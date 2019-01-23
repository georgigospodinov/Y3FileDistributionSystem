package communication.discovery;

import communication.structures.Message;
import fileman.FileHandles;

import static communication.interaction.SocketInitializer.multicastMessage;
import static communication.structures.Message.Types.LIST_RESPONSE;
import static util.CommonlyUsed.*;

public class ListPropagation extends Thread {
    @Override
    public void run() {
        String wholeList = FileHandles.getLocalTorrents();
        int totalPieces = (int) getNumberOfPieces(wholeList.length());
        if (totalPieces == 0) {
            print("no local torrents");
            return;
        }

        print("Propagating local torrent list.");

        String piece;
        Message message;
        for (int i = 0; i < totalPieces - 1; i++) {
            piece = wholeList.substring(i * PIECE_SIZE, (i + 1) * PIECE_SIZE);
            message = new Message(LIST_RESPONSE, i, totalPieces, piece);
            multicastMessage(message);
        }

        // Last piece might be shorter.
        piece = wholeList.substring((totalPieces - 1) * PIECE_SIZE);
        message = new Message(LIST_RESPONSE, totalPieces - 1, totalPieces, piece);
        multicastMessage(message);
    }
}
