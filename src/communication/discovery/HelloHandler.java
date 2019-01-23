package communication.discovery;

import communication.structures.Message;
import communication.structures.SeederStatus;

import java.net.InetAddress;
import java.util.LinkedHashMap;

import static communication.interaction.SocketInitializer.*;
import static communication.structures.Message.Types.LIST_REQUEST;

public class HelloHandler extends Thread {

    private InetAddress sender;

    public HelloHandler(InetAddress sender) {
        this.sender = sender;
    }

    @Override
    public void run() {
        if (sender.equals(me)) return;

        LinkedHashMap<InetAddress, SeederStatus> seeders = getSeeders();
        if (!seeders.containsKey(sender)) {  // Maintain status for new seeders.
            seeders.put(sender, new SeederStatus());
            return;
        }

        SeederStatus status = seeders.get(sender);
        status.resetPeriods();
        if (status.isOutOfDate())
            unicastMessage(new Message(LIST_REQUEST, 0), sender);
    }
}
