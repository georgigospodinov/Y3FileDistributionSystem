package communication.discovery;

import communication.structures.Message;
import fileman.FileHandles;

import java.net.InetAddress;
import java.util.LinkedList;

import static communication.interaction.SocketInitializer.*;
import static communication.structures.Message.Types.HELLO;
import static util.CommonlyUsed.print;
import static util.Logger.log;

public class KeepAlive extends Thread {

    private static final int TIME_OUT = 3000;  // ms

    private LinkedList<InetAddress> seedersToRemove = new LinkedList<>();

    private void countPeriods() {
        getSeeders().forEach((id, status) -> {
            status.countPeriod();
            if (status.isDead())
                seedersToRemove.push(id);
        });

        while (!seedersToRemove.isEmpty()) {
            InetAddress seeder = seedersToRemove.pop();
            getSeeders().remove(seeder);
            getTorrentRequesters().remove(seeder);
            getTorrentFileHandlers().remove(seeder);
            FileHandles.removeOwner(seeder);
        }
    }

    @Override
    public void run() {
        while (isRunning()) try {
            multicastMessage(new Message(HELLO));
            countPeriods();
            Thread.sleep(TIME_OUT);
        }
        catch (Exception e) {
            log(e);
        }
    }
}
