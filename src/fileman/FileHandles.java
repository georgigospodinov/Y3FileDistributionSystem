package fileman;

import fileman.hash.HashChecker;
import fileman.torrent.Torrent;
import util.CommonlyUsed;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;

import static fileman.hash.FileHashing.TORRENT_EXTENSION;
import static fileman.torrent.GenerateTorrent.DIR;
import static util.CommonlyUsed.*;
import static util.Logger.log;

/**
 * Provides file handles for the classes of the communication package.
 *
 * @version 2.0
 */
public class FileHandles {

    /** Maps torrent IDs to their respective instance of class {@link Torrent}. */
    private static final LinkedHashMap<String, Torrent> TORRENTS = new LinkedHashMap<>();

    /** Keeps track of the torrent files and their contents available from the network. */
    private static final LinkedHashMap<String, String[]> NETWORK_TORRENTS = new LinkedHashMap<>();
    private static final LinkedHashMap<String, Integer> NETWORK_TORRENTS_SIZES = new LinkedHashMap<>();

    /** Maps torrent ID to peers who have that torrent. */
    private static final LinkedHashMap<String, LinkedHashSet<InetAddress>> TORRENT_OWNERS = new LinkedHashMap<>();

    private static void addDefault() {
        File torrentsFolder = new File(DIR);
        File[] files = torrentsFolder.listFiles();
        if (files == null) return;

        for (File torrentFile : files)
            if (torrentFile.getName().endsWith(TORRENT_EXTENSION)) {
                try {
                    Torrent t = new Torrent(torrentFile);
                    TORRENTS.put(t.getId(), t);
                }
                catch (IOException e) {
                    log(e);
                }
            }
    }

    private static Thread[] runTorrentCreationThreads() {
        Thread[] torrentCreationThreads = new Thread[TORRENTS.size()];
        Set<String> ids = TORRENTS.keySet();
        int i = 0;
        print("Checking torrents' owners.");
        for (String id : ids) {
            torrentCreationThreads[i] = new Thread(TORRENTS.get(id));
            torrentCreationThreads[i].start();
            i++;
        }

        return torrentCreationThreads;
    }

    private static boolean atLeastOneRunning(Thread[] threads) {
        for (Thread t : threads)
            if (t.getState().equals(Thread.State.RUNNABLE))
                return true;
        return false;
    }

    public static void initialise(String... torrentFiles) {
        Torrent t;
        for (String torrentFile : torrentFiles) {
            if (!torrentFile.endsWith(TORRENT_EXTENSION)) {
                log("Path: " + torrentFile + " is not a torrent file.");
                continue;
            }
            try {
                t = new Torrent(new File(torrentFile));
                TORRENTS.put(t.getId(), t);
            }
            catch (IOException e) {
                log(e);
            }
        }

        if (TORRENTS.isEmpty()) addDefault();
        Thread[] threads = runTorrentCreationThreads();
        while (atLeastOneRunning(threads)) try {
            Thread.sleep(100);
        }
        catch (InterruptedException e) {
            log(e);
        }

    }

    public static void addNewLocalTorrent(File torrentFile, String localDestination) {
        Torrent t;
        try {
            t = new Torrent(torrentFile);
            t.setLocalFolder(localDestination);
            TORRENTS.put(t.getId(), t);
            new Thread(t).start();
        }
        catch (IOException e) {
            log(e);
        }

    }

    public static String getLocalTorrents() {
        StringBuilder sb = new StringBuilder();
        TORRENTS.forEach((id, t) -> sb.append(t.toString()));
        return sb.toString();
    }

    private static int trackFiles(String[] datas, int index, String torrentID) {
        int torrentFileSize;
        try {
            torrentFileSize = Integer.parseInt(datas[index + 1]);
        }
        catch (NumberFormatException e) {
            return 1;
        }
        NETWORK_TORRENTS_SIZES.put(torrentID, torrentFileSize);
        int numberOfFiles = Integer.parseInt(datas[index + 2]);
        String[] fileNames = new String[numberOfFiles];
        for (int i = 0; i < numberOfFiles; i++)
            fileNames[i] = datas[index + i + 3];
        NETWORK_TORRENTS.put(torrentID, fileNames);
        return numberOfFiles + 2;
    }

    private static void trackOwners(String torrentID, InetAddress owner) {
        if (!TORRENT_OWNERS.containsKey(torrentID))
            TORRENT_OWNERS.put(torrentID, new LinkedHashSet<>());
        TORRENT_OWNERS.get(torrentID).add(owner);
    }

    public static void addRemoteTorrents(String torrents, InetAddress owner, int port) {
        String[] datas = torrents.split(NEW_LINE);
        int n = datas.length;
        if (n == 1) return;
        for (int i = 0; i < n; i++) {
            String torrentID = datas[i];
            if (TORRENTS.containsKey(torrentID))  // Forces an update on who owns what pieces of the torrent.
            {
                Torrent t = TORRENTS.get(torrentID);
                t.ask(owner, port);
            }
            trackOwners(torrentID, owner);
            i += trackFiles(datas, i, torrentID);
        }
    }

    public static void removeOwner(InetAddress owner) {
        LinkedHashSet<String> torrentIDsToRemove = new LinkedHashSet<>();
        TORRENT_OWNERS.forEach((torrentID, set) -> {
            set.remove(owner);
            if (set.isEmpty())
                torrentIDsToRemove.add(torrentID);
        });
        torrentIDsToRemove.forEach(torrentID -> {
            TORRENT_OWNERS.remove(torrentID);
            NETWORK_TORRENTS.remove(torrentID);
        });
    }

    public static InetAddress getOwnerOf(String torrentID) {
        Set<InetAddress> owners = TORRENT_OWNERS.get(torrentID);
        if (owners == null) return null;
        int size = owners.size();
        int item = new Random().nextInt(size);
        int i = 0;
        for (InetAddress owner : owners)
            if (i == item)
                return owner;
            else i++;
        return null;
    }

    public static int getNumberOfNetworkTorrentPieces(String torrentID) {
        return (int) CommonlyUsed.getNumberOfPieces(NETWORK_TORRENTS_SIZES.get(torrentID));
    }

    private static byte[] readPiece(RandomAccessFile reader, int pieceID) throws IOException {
        reader.seek(pieceID * PIECE_SIZE);
        byte[] data = new byte[PIECE_SIZE];
        int actuallyRead = reader.read(data);
        if (actuallyRead == PIECE_SIZE)
            return data;
        byte[] readData = new byte[actuallyRead];
        System.arraycopy(data, 0, readData, 0, actuallyRead);
        return readData;
    }

    public static byte[] getTorrentPiece(String torrentID, int pieceID) {
        Torrent torrent = TORRENTS.get(torrentID);
        if (torrent == null) return null;

        try {
            RandomAccessFile reader = torrent.openTorrent();
            return readPiece(reader, pieceID);
        }
        catch (IOException e) {
            log(e);
        }

        return null;
    }

    public static byte[] getFilePiece(String torrentID, String filename, int pieceID) {
        Torrent t = TORRENTS.get(torrentID);
        if (t == null) return null;
        return t.getFilePiece(filename, pieceID);
    }

    public static void setFilePiece(String torrentID, String filename, Integer pieceID, byte[] pieceData) {
        Torrent t = TORRENTS.get(torrentID);
        if (t == null) return;
        t.setFilePiece(filename, pieceID, pieceData);
    }

    public static void writeAndCloseFile(String torrentID, String filename) {
        Torrent t = TORRENTS.get(torrentID);
        if (t == null) return;
        t.writeAndCloseFile(filename);
    }

    public static long getNetworkTorrentSize(String torrentID) {
        return NETWORK_TORRENTS_SIZES.get(torrentID);
    }

    public static String getAllNetworkTorrents() {
        StringBuilder sb = new StringBuilder();
        NETWORK_TORRENTS.forEach((torrent, files) -> sb.append(torrent).append(NEW_LINE));
        return sb.toString();
    }

    public static boolean doIOwn(String torrentID, String filename, int pieceID) {
        Torrent t = TORRENTS.get(torrentID);
        return t != null && t.doIOwn(filename, pieceID);
    }

    public static void addOwner(String torrentID, String filename, int pieceID, InetAddress owner) {
        Torrent t = TORRENTS.get(torrentID);
        if (t == null) return;
        t.addOwner(filename, pieceID, owner);
    }

    public static RandomAccessFile getRandomWriter(String torrentID, String filename) {
        Torrent t = TORRENTS.get(torrentID);
        if (t == null) return null;
        return t.openFileW(filename);
    }

    public static int getNumberOfPieces(String torrentID, String filename) {
        Torrent t = TORRENTS.get(torrentID);
        try {
            return (int) CommonlyUsed.getNumberOfPieces(t.getFileSize(filename));
        }
        catch (IOException e) {
            log(e);
            return -1;
        }
    }

    /**
     * Returns a random owner of the requested piece.
     *
     * @param torrentID the id of the torrent that contains the file
     * @param filename  the name of the file
     * @param pieceID   the id of the piece to get an owner of
     * @return a random owner of the requested piece or null if no such is found
     */
    public static InetAddress getOwnerOf(String torrentID, String filename, int pieceID) {
        Torrent t = TORRENTS.get(torrentID);
        Set<InetAddress> owners = t.getSetsOfOwners(filename).get(pieceID);
        int n = owners.size();
        int item;
        try {
            item = new Random().nextInt(n);
        }
        catch (Exception e) {
            log(e);
            return null;
        }
        int i = 0;
        for (InetAddress owner : owners) {
            if (i == item)
                return owner;
            else i++;
        }
        return null;
    }

    /** {@link Torrent#getPiecesByRarity(String)} */
    public static int[] getPiecesByRarity(String torrentID, String filename) {
        return TORRENTS.get(torrentID).getPiecesByRarity(filename);
    }

    public static void printMap(String mapName) {
        switch (mapName) {
            case "torrents":
                TORRENTS.forEach(CommonlyUsed::print);
                break;
            case "net_torrents":
                NETWORK_TORRENTS.forEach(CommonlyUsed::print);
                break;
            case "net_torrent_sizes":
                NETWORK_TORRENTS_SIZES.forEach((id, size) -> print(id + " " + size));
                break;
            case "torrent_owners":
                TORRENT_OWNERS.forEach(CommonlyUsed::print);
                break;
            case "file_owners":
                TORRENTS.forEach((id, t) -> {
                    print(id);
                    t.forEachFile((filename) -> {
                        print(filename);
                        t.getSetsOfOwners(filename).forEach(set -> print(set.size()));
                    });
                });
                break;
            case "peers":
                LinkedHashSet<InetAddress> peers = new LinkedHashSet<>();
                TORRENT_OWNERS.forEach((torrentID, set) -> peers.addAll(set));
                peers.forEach(CommonlyUsed::print);
                break;
            default:
                print("unrecognized");
        }
    }

    public static void printTorrent(String torrentID) {
        Torrent t = TORRENTS.get(torrentID);
        if (t == null) return;
        t.printFilePiecesOwners();
    }

    private static String formatFileMeta(Torrent t, String filename) {
        long size = 0;
        try {
            size = t.getFileSize(filename);
        }
        catch (IOException e) {
            log(e);
        }
        int pieces = (int) CommonlyUsed.getNumberOfPieces(size);
        return filename + ": " + size + " bytes in  " + pieces + " pieces" + NEW_LINE;
    }

    public static String getFileMeta(String torrentID) {
        Torrent t = TORRENTS.get(torrentID);
        if (t == null) return null;
        StringBuilder sb = new StringBuilder();
        t.forEachFile((filename) -> sb.append(formatFileMeta(t, filename)));
        return sb.toString();
    }

    public static String getFileMeta(String torrentID, String filename) {
        Torrent t = TORRENTS.get(torrentID);
        if (t == null) return null;
        return formatFileMeta(t, filename);
    }

    public static String[] getFilenamesIn(String torrentID) {
        Torrent t = TORRENTS.get(torrentID);
        if (t == null) return null;
        return t.getFilenames();
    }

    public static boolean check(String torrentID, String filename, int pieceID, byte[] pieceData) {
        Torrent t = TORRENTS.get(torrentID);
        if (t == null) return false;

        try {
            return HashChecker.isCorrect(t.getReader(), filename, pieceID, pieceData);
        }
        catch (IOException e) {
            log(e);
            return true;
        }
    }
}