package communication.torrent;

import communication.structures.Message;
import fileman.FileHandles;

import java.net.InetAddress;
import java.util.LinkedHashMap;

import static communication.interaction.SocketInitializer.getTorrentRequesters;
import static communication.interaction.SocketInitializer.unicastMessage;
import static communication.structures.Message.Types.TORRENT_REQUEST;
import static util.CommonlyUsed.print;

public class TorrentRequester extends Thread {

    private String torrentID;
    private String localDestination;

    public TorrentRequester(String torrentID, String localDestination) {
        print("Requesting torrent=" + torrentID);
        this.torrentID = torrentID;
        this.localDestination = localDestination;
    }

    public String getLocalDestination() {
        return localDestination;
    }

    @Override
    public void run() {
        InetAddress owner = FileHandles.getOwnerOf(torrentID);
        if (owner == null) return;

        LinkedHashMap<InetAddress, LinkedHashMap<String, TorrentRequester>> torrentRequesters = getTorrentRequesters();
        LinkedHashMap<String, TorrentRequester> perTorrentRequest;
        if (!torrentRequesters.containsKey(owner)) {
            perTorrentRequest = new LinkedHashMap<>();
            torrentRequesters.put(owner, perTorrentRequest);
        }
        else perTorrentRequest = torrentRequesters.get(owner);
        perTorrentRequest.put(torrentID, this);


        int numberOfPieces = FileHandles.getNumberOfNetworkTorrentPieces(torrentID);
        for (int i = 0; i < numberOfPieces; i++) {
            Message m = new Message(TORRENT_REQUEST, torrentID, i);
            unicastMessage(m, owner);
        }
    }
}
