package communication.sharing;

import communication.structures.Message;
import fileman.FileHandles;
import fileman.torrent.BufferedRandomFile;

import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static communication.discovery.ListResponseHandler.MAX_PERIODS;
import static communication.discovery.ListResponseHandler.PERIOD;
import static communication.interaction.SocketInitializer.isRunning;
import static communication.interaction.SocketInitializer.unicastMessage;
import static communication.structures.Message.Types.PIECE_REQUEST;
import static util.CommonlyUsed.print;
import static util.Logger.log;

public class FileDownloader extends Thread {

    /** Maps torrentID to filename to downloading thread. */
    private static final Map<String, Map<String, FileDownloader>> TORRENT_MAP = new LinkedHashMap<>();

    static {
        new Thread(FileDownloader::runner).start();
    }

    private static void runEntries(String torrentID, Map<String, FileDownloader> map) {
        Set<String> filenames = map.keySet();
        for (String filename : filenames) {
            FileDownloader downloader = map.get(filename);
            State s = downloader.getState();
            if (s == State.NEW) downloader.start();
            else if (s == State.TERMINATED) {
                map.put(filename, new FileDownloader(downloader));
                map.get(filename).start();
            }
        }
    }

    /** Periodically runs the threads. */
    private static void runner() {
        while (isRunning()) {
            TORRENT_MAP.forEach(FileDownloader::runEntries);
            try {
                Thread.sleep(PERIOD);
            }
            catch (InterruptedException e) {
                log(e);
            }
        }
    }

    public static void createNewDownloader(String torrentID) {
        print("Starting new download for torrent=" + torrentID);
        String[] filenames = FileHandles.getFilenamesIn(torrentID);
        if (filenames == null) {
            TORRENT_MAP.remove(torrentID);
            return;
        }
        for (String filename : filenames)
            createNewDownloader(torrentID, filename);
    }

    public static void createNewDownloader(String torrentID, String filename) {
        print("Starting new download for torrent=" + torrentID + ", file=" + filename);
        if (!TORRENT_MAP.containsKey(torrentID))
            TORRENT_MAP.put(torrentID, new LinkedHashMap<>());
        Map<String, FileDownloader> fileMap = TORRENT_MAP.get(torrentID);

        if (fileMap.containsKey(filename)) return;

        FileDownloader downloader = new FileDownloader(torrentID, filename);
        fileMap.put(filename, downloader);
    }

    public static void addPiece(Message m) {
        Map<String, FileDownloader> fileMap = TORRENT_MAP.get(m.getTorrentID());
        if (fileMap == null) return;

        FileDownloader downloader = fileMap.get(m.getFilename());
        if (downloader == null) return;

        downloader.orderedPieces.put(m.getPieceID(), m.getPieceData());
    }

    private String torrentID;
    private String filename;
    private RandomAccessFile writer;
    private boolean[] acquiredPieces;
    private boolean recentlyUpdated = false;
    private int[] pieces;
    private TreeMap<Integer, byte[]> orderedPieces = new TreeMap<>();
    private int periods = 0;
    private FileDownloader(String torrentID, String filename) {
        this.torrentID = torrentID;
        this.filename = filename;
        writer = FileHandles.getRandomWriter(torrentID, filename);
        acquiredPieces = new boolean[FileHandles.getNumberOfPieces(torrentID, filename)];
        pieces = FileHandles.getPiecesByRarity(torrentID, filename);
    }
    public FileDownloader(FileDownloader old) {
        this.torrentID = old.torrentID;
        this.filename = old.filename;
        this.writer = old.writer;
        this.acquiredPieces = old.acquiredPieces;
        this.recentlyUpdated = old.recentlyUpdated;
        this.pieces = old.pieces;
        this.orderedPieces = old.orderedPieces;
        this.periods = old.periods;
    }

    private void writeNextPiece() {
        Map.Entry first = orderedPieces.pollFirstEntry();
        if (first == null) return;
        Integer pieceID = (Integer) first.getKey();
        if (acquiredPieces[pieceID]) return;  // Ignore repeated packets.

        byte[] pieceData = (byte[]) first.getValue();
        if (!FileHandles.check(torrentID, filename, pieceID, pieceData)) {
            print("hashes not matching ", filename, pieceID);
            return;
        }

        FileHandles.setFilePiece(torrentID, filename, pieceID, pieceData);
        recentlyUpdated = true;
        acquiredPieces[pieceID] = true;
    }

    private boolean haveAll() {
        for (boolean acquiredPiece : acquiredPieces)
            if (!acquiredPiece)
                return false;

        print("Successfully downloaded torrent " + torrentID + ",   file " + filename + ".");
        return true;
    }

    private void countPeriods() {
        if (recentlyUpdated) {
            periods = 0;
            recentlyUpdated = false;
        }
        else periods++;

        if (periods < MAX_PERIODS) return;

        print("Giving up on " + torrentID + "   " + filename);
        cleanUp();
    }

    /**
     * Sends a request for each missing piece.
     * Starts with the piece that has the least amount of owners.
     */
    private void requestMissing() {
        int queriesMade = 0;
        for (Integer id : pieces) {
            if (acquiredPieces[id]) continue;  // skip acquired pieces
            InetAddress owner = FileHandles.getOwnerOf(torrentID, filename, id);
            if (owner == null) continue;
            Message m = new Message(PIECE_REQUEST, torrentID, filename, id);
            unicastMessage(m, owner);
            if (++queriesMade == BufferedRandomFile.MAX_BUFFERS)
                break;
        }
    }

    private void cleanUp() {
        FileHandles.writeAndCloseFile(torrentID, filename);
        Map<String, FileDownloader> fileMap = TORRENT_MAP.get(torrentID);
        fileMap.remove(filename);
        if (fileMap.isEmpty())
            TORRENT_MAP.remove(torrentID);
    }

    @Override
    public void run() {
        if (orderedPieces.isEmpty()) {
            requestMissing();
            countPeriods();
            return;
        }
        else while (!orderedPieces.isEmpty())
            writeNextPiece();

        if (haveAll()) cleanUp();
    }

}
