package communication.owners;

import communication.structures.Message;
import fileman.FileHandles;

import static communication.interaction.SocketInitializer.unicastMessage;
import static communication.structures.Message.Types.I_HAVE;

public class WhoHasHandler extends Thread {

    private Message m;

    public WhoHasHandler(Message m) {
        this.m = m;
    }

    @Override
    public void run() {
        String torrentID = m.getTorrentID();
        String filename = m.getFilename();
        int pieceID = m.getPieceID();
        if (!FileHandles.doIOwn(torrentID, filename, pieceID)) return;

        Message message = new Message(I_HAVE, torrentID, filename, pieceID);
        unicastMessage(message, m.getSender(), m.getPort());
    }
}
