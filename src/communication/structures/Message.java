package communication.structures;

import java.net.DatagramPacket;
import java.net.InetAddress;

import static communication.structures.Message.Types.*;
import static util.CommonlyUsed.PIECE_SIZE;
import static util.CommonlyUsed.print;

/**
 * Contains a parsed packet received over the network or a formatted message ready to be send.
 * Format is: "TYPE:fields:CONTENTS".
 *
 * @version 3.0
 */
public class Message implements Comparable {

    public static final int MAX_MESSAGE_LENGTH;
    private static final String SEPARATOR = "___:";
    private static final int MAX_NUMBER_OF_SEPARATORS = 4;
    private static final int MAX_IDENTIFIER_LENGTH = 100;
    private static final int MAX_NUMBER_OF_IDENTIFIERS = 3;

    static {
        Types[] allTypes = Types.values();
        int maxTypeLength = allTypes[0].toString().getBytes().length;
        for (Types type : allTypes) {
            int len = type.toString().getBytes().length;
            if (maxTypeLength < len)
                maxTypeLength = len;
        }
        MAX_MESSAGE_LENGTH = maxTypeLength +
                SEPARATOR.getBytes().length * MAX_NUMBER_OF_SEPARATORS +
                MAX_IDENTIFIER_LENGTH * MAX_NUMBER_OF_IDENTIFIERS +
                PIECE_SIZE;
        // longest message is TYPE:torrent ID:filename:piece index:piece
    }

    private InetAddress sender;
    private int port = -1;
    private Types type;
    private String torrentID;
    private String filename;
    private int pieceID;
    private int totalPieces;
    private byte[] pieceData;
    public Message(DatagramPacket packet) throws UnrecognizedFormatException {
        sender = packet.getAddress();
        port = packet.getPort();

        String message = new String(packet.getData(), 0, packet.getLength());
        String[] parts = message.split(SEPARATOR);

        try {
            type = Types.valueOf(parts[0]);
        }
        catch (IllegalArgumentException e) {
            throw new UnrecognizedFormatException("Message " + parts[0] + " not recognized.");
        }

        switch (type) {
            case HELLO:
            case TIMED_OUT:
                throwExceptionIfDifferent(parts.length, 1, message);
                break;
            case LIST_REQUEST:
                throwExceptionIfDifferent(parts.length, 2, message);
                pieceID = Integer.parseInt(parts[1]);
                break;
            case LIST_RESPONSE:
                pieceID = Integer.parseInt(parts[1]);
                totalPieces = Integer.parseInt(parts[2]);
                parseData(packet, parts);
                break;
            case TORRENT_REQUEST:
                throwExceptionIfDifferent(parts.length, 3, message);
                torrentID = parts[1];
                pieceID = Integer.parseInt(parts[2]);
                break;
            case TORRENT_FILE:
                throwExceptionIfDifferent(parts.length, 4, message);
                torrentID = parts[1];
                pieceID = Integer.parseInt(parts[2]);
                parseData(packet, parts);
                break;
            case WHO_HAS:
            case I_HAVE:
            case PIECE_REQUEST:
                throwExceptionIfDifferent(parts.length, 4, message);
                torrentID = parts[1];
                filename = parts[2];
                pieceID = Integer.parseInt(parts[3]);
                break;
            case PIECE_DATA:
                throwExceptionIfDifferent(parts.length, 5, message);
                torrentID = parts[1];
                filename = parts[2];
                pieceID = Integer.parseInt(parts[3]);
                parseData(packet, parts);
                break;
        }
    }
    public Message(String s) {
        this(Types.valueOf(s));
    }
    // Example message "TORRENT_FILE:torrentID:pieceID:pieceData"

    /* DISCOVERY constructors. */
    public Message(Types type) {
        this.type = type;
        if (type != HELLO && type != TIMED_OUT)
            print("Incorrect message constructor.");
    }

    public Message(Types type, int pieceID) {
        this.type = type;
        this.pieceID = pieceID;
        if (type != LIST_REQUEST)
            print("Incorrect message constructor.");
    }

    public Message(Types type, int pieceID, int totalPieces, String pieceData) {
        this.type = type;
        this.pieceID = pieceID;
        this.totalPieces = totalPieces;
        this.pieceData = pieceData.getBytes();

        if (type != LIST_RESPONSE)
            print("Incorrect message constructor.");
    }

    /* TORRENT constructors. */
    public Message(Types type, String torrentID, int pieceID) {
        this.type = type;
        this.torrentID = torrentID;
        this.pieceID = pieceID;
        if (type != TORRENT_REQUEST)
            print("Incorrect message constructor.");
    }

    public Message(Types type, String torrentID, int pieceID, byte[] pieceData) {
        this.type = type;
        this.torrentID = torrentID;
        this.pieceID = pieceID;
        this.pieceData = pieceData;
        if (type != TORRENT_FILE)
            print("Incorrect message constructor.");
    }

    /* Piece exchange constructors. */
    public Message(Types type, String torrentID, String filename, int pieceID) {
        this.type = type;
        this.torrentID = torrentID;
        this.filename = filename;
        this.pieceID = pieceID;
        if (type != WHO_HAS && type != I_HAVE && type != PIECE_REQUEST)
            print("Incorrect message constructor.");
    }

    public Message(Types type, String torrentID, String filename, int pieceID, byte[] pieceData) {
        this.type = type;
        this.torrentID = torrentID;
        this.filename = filename;
        this.pieceID = pieceID;
        this.pieceData = pieceData;
        if (type != PIECE_DATA)
            print("Incorrect message constructor.");
    }

    private static void throwExceptionIfDifferent(int received, int expected, String message) throws UnrecognizedFormatException {
        if (received != expected)
            throw new UnrecognizedFormatException("Expected " + expected + " parts, got " + received + " in " + message + ".");
    }

    private void parseData(DatagramPacket packet, String[] parts) {
        StringBuilder header = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++)
            header.append(parts[i]).append(SEPARATOR);

        pieceData = new byte[packet.getLength() - header.length()];
        System.arraycopy(packet.getData(), header.length(), pieceData, 0, pieceData.length);
    }

    public InetAddress getSender() {
        return sender;
    }

    public int getPort() {
        return port;
    }

    public Types getType() {
        return type;
    }

    public String getTorrentID() {
        return torrentID;
    }

    public String getFilename() {
        return filename;
    }

    public int getPieceID() {
        return pieceID;
    }

    public int getTotalPieces() {
        return totalPieces;
    }

    public byte[] getPieceData() {
        return pieceData;
    }

    public byte[] asBytes() {
        String s = SEPARATOR;
        String header = "";
        boolean hasPayload = false;
        switch (type) {
            case HELLO:
            case TIMED_OUT:
                header = type.toString();
                break;
            case LIST_REQUEST:
                header = type + s + pieceID;
                break;
            case LIST_RESPONSE:
                header = type + s + pieceID + s + totalPieces + s;
                hasPayload = true;
                break;
            case TORRENT_REQUEST:
                header = type + s + torrentID + s + pieceID;
                break;
            case TORRENT_FILE:
                header = type + s + torrentID + s + pieceID + s;
                hasPayload = true;
                break;
            case WHO_HAS:
            case I_HAVE:
            case PIECE_REQUEST:
                header = type + s + torrentID + s + filename + s + pieceID;
                break;
            case PIECE_DATA:
                header = type + s + torrentID + s + filename + s + pieceID + s;
                hasPayload = true;
                break;
        }

        byte[] headerBytes = header.getBytes();
        if (!hasPayload) return headerBytes;
        byte[] msg = new byte[headerBytes.length + pieceData.length];
        System.arraycopy(headerBytes, 0, msg, 0, headerBytes.length);
        System.arraycopy(pieceData, 0, msg, headerBytes.length, pieceData.length);
        return msg;
    }

    @Override
    public String toString() {
        return "{" + type + " " + torrentID + " " + filename + " " + pieceID + "}";
    }

    // Allows creating PriorityQueues of Messages, ordered by message type and pieceID
    @Override
    public int compareTo(Object o) {
        if (!(o instanceof Message))
            throw new IllegalArgumentException("Message " + this.toString() + " cannot be compared to " + String.valueOf(o));

        Message other = (Message) o;
        int v = this.type.compareTo(other.type);
        return v != 0 ? v : Integer.compare(this.pieceID, other.pieceID);
    }

    public enum Types {
        HELLO, TIMED_OUT,  // For keep alive.
        LIST_REQUEST, LIST_RESPONSE,  // For lists of torrent IDs.
        TORRENT_REQUEST, TORRENT_FILE, // For .torrent files.
        WHO_HAS, I_HAVE,  // For ownership.
        PIECE_REQUEST, PIECE_DATA  // For pieces.
    }

    public static class UnrecognizedFormatException extends Exception {
        UnrecognizedFormatException(String message) {
            super(message);
        }
    }

}
