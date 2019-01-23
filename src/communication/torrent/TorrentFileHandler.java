package communication.torrent;

import communication.structures.Message;
import fileman.FileHandles;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.util.LinkedHashMap;

import static communication.discovery.ListResponseHandler.MAX_PERIODS;
import static communication.discovery.ListResponseHandler.PERIOD;
import static communication.interaction.SocketInitializer.*;
import static communication.structures.Message.Types.TORRENT_REQUEST;
import static fileman.hash.FileHashing.TORRENT_EXTENSION;
import static fileman.torrent.Torrent.FILEPATH_SEPARATOR;
import static util.CommonlyUsed.PIECE_SIZE;
import static util.CommonlyUsed.print;
import static util.Logger.log;

public class TorrentFileHandler extends Thread {

    private InetAddress sender;
    private int port;
    private String torrentID;
    private File torrentFile;
    private RandomAccessFile writer;
    private boolean[] piecesReceived;
    private boolean recentlyUpdated = false;
    private boolean done = false;

    public TorrentFileHandler(Message m, String localPath) {
        sender = m.getSender();
        torrentID = m.getTorrentID();

        int n = FileHandles.getNumberOfNetworkTorrentPieces(torrentID);
        piecesReceived = new boolean[n];
        for (int i = 0; i < n; i++)
            piecesReceived[i] = false;

        openFiles(localPath);
    }

    public File getTorrentFile() {
        return torrentFile;
    }

    public boolean isDone() {
        return done;
    }

    private void openFiles(String localPath) {
        torrentFile = new File(localPath + FILEPATH_SEPARATOR + torrentID + TORRENT_EXTENSION);
        try {
            torrentFile.getParentFile().mkdirs();
            torrentFile.createNewFile();
            writer = new RandomAccessFile(torrentFile, "rw");
            writer.setLength(FileHandles.getNetworkTorrentSize(torrentID));
        }
        catch (IOException e) {
            log(e);
        }
    }

    public synchronized void addPiece(Message m) {
        if (!m.getTorrentID().equals(torrentID)) return;

        int pieceID = m.getPieceID();
        try {
            if (writer.getFilePointer() != pieceID * PIECE_SIZE)
                writer.seek(pieceID * PIECE_SIZE);
            writer.write(m.getPieceData());
        }
        catch (IOException e) {
            log(e);
        }
        piecesReceived[pieceID] = true;
        recentlyUpdated = true;
        port = m.getPort();
        for (boolean pieceReceived : piecesReceived)
            if (!pieceReceived) {
                done = false;
                return;
            }

        try {
            writer.close();
        }
        catch (IOException e) {
            log(e);
        }
        print("Torrent " + torrentID + " downloaded.");
        done = true;
    }

    private void findNewOwner() {

        // Gets the TorrentRequester that initiated the request for the torrent.
        LinkedHashMap<InetAddress, LinkedHashMap<String, TorrentRequester>> torrentRequesters;
        torrentRequesters = getTorrentRequesters();
        LinkedHashMap<String, TorrentRequester> thisSendersRequesters;
        thisSendersRequesters = torrentRequesters.get(sender);
        TorrentRequester requester = thisSendersRequesters.remove(torrentID);

        // Gets the maps that lead to this TorrentFileHandler.
        LinkedHashMap<InetAddress, LinkedHashMap<String, TorrentFileHandler>> torrentFileHandlers;
        torrentFileHandlers = getTorrentFileHandlers();
        LinkedHashMap<String, TorrentFileHandler> thisSenderFileHandlers;
        thisSenderFileHandlers = torrentFileHandlers.get(sender);
        TorrentFileHandler handler = thisSenderFileHandlers.remove(torrentID);

        sender = FileHandles.getOwnerOf(torrentID);

        // Put the requester in the new sender's maps.
        LinkedHashMap<String, TorrentRequester> newSendersRequesters = new LinkedHashMap<>();
        if (!torrentRequesters.containsKey(sender))
            torrentRequesters.put(sender, newSendersRequesters);
        else newSendersRequesters = torrentRequesters.get(sender);
        newSendersRequesters.put(torrentID, requester);

        // Put the handler in the new sender's maps.
        LinkedHashMap<String, TorrentFileHandler> newSenderFileHandlers = new LinkedHashMap<>();
        if (!torrentFileHandlers.containsKey(sender))
            torrentFileHandlers.put(sender, newSenderFileHandlers);
        else newSenderFileHandlers = torrentFileHandlers.get(sender);
        newSenderFileHandlers.put(torrentID, handler);
    }

    @Override
    public void run() {
        int periodsPassed = 0;
        while (true) {
            try {
                Thread.sleep(PERIOD);
            }
            catch (InterruptedException e) {
                log(e);
            }
            if (recentlyUpdated) {
                recentlyUpdated = false;
                periodsPassed = 0;
            }
            else periodsPassed++;

            if (periodsPassed == MAX_PERIODS) {
                int id = -1;
                for (int i = 0; i < piecesReceived.length; i++)
                    if (!piecesReceived[i])
                        id = i;
                if (id == -1) break;  // Break if I have all the pieces.

                Message m = new Message(TORRENT_REQUEST, torrentID, id);
                if (getSeeders().get(sender).isDead())
                    findNewOwner();
                unicastMessage(m, sender, port);

            }
            else if (periodsPassed == MAX_PERIODS * 2) {
                try {
                    writer.close();
                }
                catch (IOException e) {
                    log(e);
                }
                torrentFile.delete();
                break;  // Give up on pieces.
            }
        }
    }
}
