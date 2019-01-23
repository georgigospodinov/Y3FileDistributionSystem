package communication.owners;

import communication.structures.Message;
import fileman.torrent.Torrent;

import java.net.InetAddress;
import java.util.Map;

import static communication.interaction.SocketInitializer.multicastMessage;
import static communication.interaction.SocketInitializer.unicastMessage;
import static communication.structures.Message.Types.WHO_HAS;
import static util.CommonlyUsed.print;
import static util.Logger.log;

/**
 * Multicasts {@link Message.Types} WHO_HAS message twice.
 * Minimizes packet loss effect.
 *
 * @version 2.0
 */
public class FileOwnerAsker extends Thread {

    private static final long REPEAT_PERIOD = 1000;//ms
    private static final int REPEATS = 2;
    private Torrent torrent;
    private Map<String, Integer> fileData;
    private InetAddress potentialOwner;
    private int port;

    public FileOwnerAsker(Torrent torrent) {
        this.torrent = torrent;
        this.potentialOwner = null;
    }

    public FileOwnerAsker(Torrent torrent, InetAddress owner, int port) {
        this.torrent = torrent;
        this.potentialOwner = owner;
        this.port = port;
    }

    private void multicastAsk() {
        fileData.forEach((filename, numberOfPieces) -> {
            for (int i = 0; i < numberOfPieces; i++) {
                Message message = new Message(WHO_HAS, torrent.getId(), filename, i);
                try {
                    Thread.sleep(1);
                }
                catch (InterruptedException e) {
                    log(e);
                }
                if (potentialOwner == null)
                    multicastMessage(message);
                else unicastMessage(message, potentialOwner, port);
            }
        });
        try {
            Thread.sleep(REPEAT_PERIOD);
        }
        catch (InterruptedException e) {
            log(e);
        }

    }

    @Override
    public void run() {
        fileData = torrent.getFilesData();
        for (int i = 0; i < REPEATS; i++)
            multicastAsk();
        print("Torrent " + torrent.getId() + "'s owners updated!");
    }
}
