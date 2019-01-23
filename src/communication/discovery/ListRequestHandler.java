package communication.discovery;

import communication.structures.Message;
import fileman.FileHandles;

import java.net.InetAddress;

import static communication.interaction.SocketInitializer.unicastMessage;
import static communication.structures.Message.Types.LIST_RESPONSE;
import static util.CommonlyUsed.getNumberOfPieces;
import static util.CommonlyUsed.getPiece;

public class ListRequestHandler extends Thread {

    private InetAddress requester;
    private int port;
    private int pieceID;

    public ListRequestHandler(Message m) {
        this.requester = m.getSender();
        this.port = m.getPort();
        this.pieceID = m.getPieceID();
    }

    @Override
    public void run() {
        String wholeList = FileHandles.getLocalTorrents();
        int totalPieces = (int) getNumberOfPieces(wholeList.length());
        String piece = getPiece(wholeList, pieceID);
        Message m = new Message(LIST_RESPONSE, pieceID, totalPieces, piece);
        unicastMessage(m, requester, port);
    }
}
