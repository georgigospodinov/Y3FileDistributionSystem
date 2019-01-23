package communication.interaction;

import communication.discovery.HelloHandler;
import communication.discovery.ListPropagation;
import communication.discovery.ListRequestHandler;
import communication.discovery.ListResponseHandler;
import communication.owners.WhoHasHandler;
import communication.sharing.FileDownloader;
import communication.sharing.FileUploader;
import communication.structures.Message;
import communication.torrent.TorrentFileHandler;
import communication.torrent.TorrentRequestHandler;
import communication.torrent.TorrentRequester;
import fileman.FileHandles;

import java.net.InetAddress;
import java.util.LinkedHashMap;

import static communication.interaction.SocketInitializer.*;
import static communication.structures.Message.Types.TIMED_OUT;
import static fileman.torrent.GenerateTorrent.DIR;
import static util.CommonlyUsed.print;

/**
 * Master thread which provides message sending and receiving.
 * Creates and starts handler threads for all the communication.
 *
 * @version 3.0
 */
public class Master {

    public static void initialize(String groupName, int port) {
        SocketInitializer.initialize(groupName, port);
    }

    private static void torrentFileHandle(Message m) {
        InetAddress sender = m.getSender();
        if (!torrentFileHandlers.containsKey(sender))
            torrentFileHandlers.put(sender, new LinkedHashMap<>());
        LinkedHashMap<String, TorrentFileHandler> perTorrent = torrentFileHandlers.get(sender);

        String torrentID = m.getTorrentID();
        if (!perTorrent.containsKey(torrentID)) {
            perTorrent.put(torrentID, new TorrentFileHandler(m, DIR));
            perTorrent.get(torrentID).start();
        }
        TorrentFileHandler handler = perTorrent.get(torrentID);

        handler.addPiece(m);
        if (handler.isDone()) {
            // Remove torrent requester that initiated this transfer.
            LinkedHashMap<String, TorrentRequester> sendersTorrentRequesters = torrentRequesters.get(sender);
            TorrentRequester tr = sendersTorrentRequesters.remove(torrentID);
            FileHandles.addNewLocalTorrent(handler.getTorrentFile(), tr.getLocalDestination());
            if (sendersTorrentRequesters.isEmpty()) torrentRequesters.remove(sender);

            if (perTorrent.isEmpty()) torrentFileHandlers.remove(sender);

            new ListPropagation().start();
        }

    }

    static void respond(Message m) {
        if (m.getSender().equals(me)) return;

        switch (m.getType()) {
            case HELLO:
                new HelloHandler(m.getSender()).start();
                break;
            case LIST_REQUEST:
                new ListRequestHandler(m).start();
                break;
            case LIST_RESPONSE:
                ListResponseHandler.addListPiece(m);
                break;
            case TORRENT_REQUEST:
                new TorrentRequestHandler(m).start();
                break;
            case TORRENT_FILE:
                torrentFileHandle(m);
                break;
            case WHO_HAS:
                new WhoHasHandler(m).start();
                break;
            case I_HAVE:
                FileHandles.addOwner(m.getTorrentID(), m.getFilename(), m.getPieceID(), m.getSender());
                break;
            case PIECE_REQUEST:
                FileUploader.enqueueRequest(m);
                break;
            case PIECE_DATA:
                FileDownloader.addPiece(m);
                break;
        }
    }

    public static void run() {
        unicastListener = new UnicastListener();
        unicastListener.start();
        keepAlive.start();
        new ListPropagation().start();
        commandParser.start();
        while (running) {
            Message received = receiveMessage();
            if (received == null || received.getType() == TIMED_OUT) continue;
            respond(received);
        }

        cleanUp();
    }
}
