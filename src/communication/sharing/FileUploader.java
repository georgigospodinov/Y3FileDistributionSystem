package communication.sharing;

import communication.structures.Message;
import fileman.FileHandles;

import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.PriorityQueue;
import java.util.Set;

import static communication.discovery.ListResponseHandler.MAX_PERIODS;
import static communication.discovery.ListResponseHandler.PERIOD;
import static communication.interaction.SocketInitializer.isRunning;
import static communication.interaction.SocketInitializer.unicastMessage;
import static communication.structures.Message.Types.PIECE_DATA;
import static fileman.torrent.BufferedRandomFile.MAX_BUFFERS;
import static util.CommonlyUsed.print;
import static util.Logger.log;

public class FileUploader extends Thread {

    private static final LinkedHashMap<InetAddress, FileUploader> RESPONDERS = new LinkedHashMap<>();

    static {
        new Thread(FileUploader::runner).start();
    }

    /** Periodically runs the threads. */
    private static void runner() {
        while (isRunning()) {
            Set<InetAddress> requesters = RESPONDERS.keySet();
            for (InetAddress requester : requesters) {
                FileUploader uploader = RESPONDERS.get(requester);
                State s = uploader.getState();
                if (s == State.NEW) uploader.start();
                else if (s == State.TERMINATED) {
                    RESPONDERS.put(requester, new FileUploader(uploader));
                    RESPONDERS.get(requester).start();
                }
            }
            try {
                Thread.sleep(PERIOD);
            }
            catch (InterruptedException e) {
                log(e);
            }
        }
    }

    public static void enqueueRequest(Message m) {
        InetAddress sender = m.getSender();
        if (!RESPONDERS.containsKey(sender)) {
            print("LACKING KEY=====================", sender);
            RESPONDERS.put(sender, new FileUploader(sender));
        }

        FileUploader uploader = RESPONDERS.get(sender);
        uploader.requests.add(m);
    }

    private int periods = 0;
    private boolean recentlyResponded = false;
    private InetAddress requester;
    private PriorityQueue<Message> requests = new PriorityQueue<>();
    private FileUploader(InetAddress requester) {
        this.requester = requester;
    }
    private FileUploader(FileUploader old) {
        this.recentlyResponded = old.recentlyResponded;
        this.periods = old.periods;
        this.requester = old.requester;
        this.requests = old.requests;
    }

    private void uploadNextPiece() {
        Message message;
        try {
            message = requests.poll();
        }
        catch (Exception e) {
            log(e);
            return;
        }
        String torrentID = message.getTorrentID();
        String filename = message.getFilename();
        int pieceID = message.getPieceID();
        byte[] pieceData = FileHandles.getFilePiece(torrentID, filename, pieceID);
        if (pieceData == null) {
            print("data is null");
            return;
        }

        Message m = new Message(PIECE_DATA, torrentID, filename, pieceID, pieceData);
        unicastMessage(m, message.getSender(), message.getPort());
        recentlyResponded = true;
    }

    private void countPeriods() {
        if (recentlyResponded) {
            periods = 0;
            recentlyResponded = false;
        }
        else periods++;
        if (periods < MAX_PERIODS) return;
        RESPONDERS.remove(requester);
    }

    @Override
    public void run() {
        if (requests.isEmpty()) {
            countPeriods();
            return;
        }
        int responses = 0;
        while (!requests.isEmpty()) {
            uploadNextPiece();
            if (++responses == MAX_BUFFERS) break;
        }
    }
}
