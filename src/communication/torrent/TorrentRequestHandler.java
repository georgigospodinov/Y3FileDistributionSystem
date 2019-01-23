package communication.torrent;

import communication.structures.Message;
import fileman.FileHandles;

import java.net.InetAddress;

import static communication.interaction.SocketInitializer.unicastMessage;
import static communication.structures.Message.Types.TORRENT_FILE;

public class TorrentRequestHandler extends Thread {

    private InetAddress requester;
    private int port;
    private String torrentID;
    private int pieceID;

    public TorrentRequestHandler(Message m) {
        requester = m.getSender();
        port = m.getPort();
        torrentID = m.getTorrentID();
        pieceID = m.getPieceID();
    }

    @Override
    public void run() {
        byte[] piece = FileHandles.getTorrentPiece(torrentID, pieceID);
        Message m = new Message(TORRENT_FILE, torrentID, pieceID, piece);
        unicastMessage(m, requester, port);
    }
}
