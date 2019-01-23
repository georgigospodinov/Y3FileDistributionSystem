package communication.discovery;

import communication.structures.Message;
import communication.structures.SeederStatus;
import fileman.FileHandles;

import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.PriorityQueue;
import java.util.Set;

import static communication.interaction.SocketInitializer.*;
import static communication.structures.Message.Types.LIST_REQUEST;
import static util.Logger.log;

/**
 * Created when a LIST_RESPONSE is received to build up the list.
 *
 * @version 1.3
 */
public class ListResponseHandler extends Thread {

    public static final int PERIOD = 100;
    public static final int MAX_PERIODS = 3;

    private static final LinkedHashMap<InetAddress, ListResponseHandler> RESPONSE_HANDLERS = new LinkedHashMap<>();

    static {
        new Thread(ListResponseHandler::runner).start();
    }

    /** Periodically runs the threads. */
    private static void runner() {
        while (isRunning()) {
            Set<InetAddress> responders = RESPONSE_HANDLERS.keySet();
            for (InetAddress responder : responders) {
                ListResponseHandler uploader = RESPONSE_HANDLERS.get(responder);
                State s = uploader.getState();
                if (s == State.NEW) uploader.start();
                else if (s == State.TERMINATED) {
                    RESPONSE_HANDLERS.put(responder, new ListResponseHandler(uploader));
                    RESPONSE_HANDLERS.get(responder).start();
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

    public static void addListPiece(Message m) {
        InetAddress sender = m.getSender();
        if (!RESPONSE_HANDLERS.containsKey(sender))
            RESPONSE_HANDLERS.put(sender, new ListResponseHandler(sender, m.getTotalPieces()));

        ListResponseHandler handler = RESPONSE_HANDLERS.get(sender);
        handler.messages.add(m);
    }

    private InetAddress sender;
    private int port;
    private SeederStatus status;
    private String[] pieces = null;
    private boolean recentlyUpdated = false;
    private int periods = 0;
    private PriorityQueue<Message> messages = new PriorityQueue<>();

    private ListResponseHandler(InetAddress sender, int totalPieces) {
        this.sender = sender;
        if (sender.equals(me)) return;

        status = getSeeders().get(sender);
        if (status == null) {
            status = new SeederStatus();
            getSeeders().put(sender, status);
        }

        pieces = new String[totalPieces];
        status.beingUpdated();
    }
    private ListResponseHandler(ListResponseHandler old) {
        this.sender = old.sender;
        this.port = old.port;
        this.status = old.status;
        this.pieces = old.pieces;
        this.recentlyUpdated = old.recentlyUpdated;
        this.periods = old.periods;
        this.messages = old.messages;
    }

    private String getList() {
        StringBuilder sb = new StringBuilder();
        for (String piece : pieces)
            sb.append(piece);
        return sb.toString();
    }

    private synchronized void addNextPiece() {
        Message m = messages.poll();
        int n = m.getTotalPieces();
        if (n == 0) return;

        pieces[m.getPieceID()] = new String(m.getPieceData());
        recentlyUpdated = true;
        port = m.getPort();
        for (int i = 0; i < n; i++)
            if (pieces[i] == null) return;

        status.updated();
    }

    private int nextMissing() {
        int id = -1;
        for (int i = 0; i < pieces.length; i++)
            if (pieces[i] == null)
                id = i;
        return id;
    }

    /**
     * Sends a request if needed.
     *
     * @return true if a request was sent, false if all the pieces of the list have been received
     */
    private boolean requestNext() {
        int id = nextMissing();
        if (id == -1) return false;

        Message m = new Message(LIST_REQUEST, id);
        unicastMessage(m, sender, port);
        return true;
    }

    private void countPeriods() {
        if (recentlyUpdated) {
            recentlyUpdated = false;
            periods = 0;
        }
        else periods++;

        if (periods == MAX_PERIODS) {
            if (requestNext()) return;  // Wait for more messages.
            // List is done.
            else FileHandles.addRemoteTorrents(getList(), sender, port);
        }
        else if (periods == MAX_PERIODS * 2)
            status.outOfDate();
        else return;

        RESPONSE_HANDLERS.remove(sender);
    }

    @Override
    public void run() {
        if(messages.isEmpty()) {
            countPeriods();
            return;
        }
        while (!messages.isEmpty())
            addNextPiece();

    }
}
