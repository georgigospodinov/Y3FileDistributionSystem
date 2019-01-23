package communication.interaction;

import communication.discovery.KeepAlive;
import communication.structures.Message;
import communication.structures.SeederStatus;
import communication.torrent.TorrentFileHandler;
import communication.torrent.TorrentRequester;

import java.io.IOException;
import java.net.*;
import java.util.LinkedHashMap;

import static communication.structures.Message.MAX_MESSAGE_LENGTH;
import static communication.structures.Message.Types.TIMED_OUT;
import static util.CommonlyUsed.print;
import static util.Logger.log;

public class SocketInitializer {

    public static InetAddress me;
    static DatagramSocket uniSocket;
    static KeepAlive keepAlive = new KeepAlive();
    static CommandParser commandParser = new CommandParser();
    static UnicastListener unicastListener;
    static boolean running = true;
    static LinkedHashMap<InetAddress, LinkedHashMap<String, TorrentFileHandler>> torrentFileHandlers = new LinkedHashMap<>();
    static LinkedHashMap<InetAddress, LinkedHashMap<String, TorrentRequester>> torrentRequesters = new LinkedHashMap<>();
    private static InetAddress groupAddress;
    private static MulticastSocket groupSocket;
    private static int port;
    private static LinkedHashMap<InetAddress, SeederStatus> seeders = new LinkedHashMap<>();

    static {
        try {
            me = InetAddress.getLocalHost();
        }
        catch (UnknownHostException e) {
            log(e);
            me = null;
            System.err.println("CRITICAL: Failed to get localhost's address!");
        }
    }

    static void initialize(String groupName, int port) {
        SocketInitializer.port = port;
        try {
            groupAddress = InetAddress.getByName(groupName);
            groupSocket = new MulticastSocket(port);
            print("Joined");
            groupSocket.joinGroup(groupAddress);
            groupSocket.setSoTimeout(500);
            uniSocket = new DatagramSocket();
            uniSocket.setSoTimeout(500);
        }
        catch (IOException e) {
            log(e);
        }
    }

    public static boolean isRunning() {
        return running;
    }

    public static LinkedHashMap<InetAddress, SeederStatus> getSeeders() {
        return seeders;
    }

    public static LinkedHashMap<InetAddress, LinkedHashMap<String, TorrentFileHandler>> getTorrentFileHandlers() {
        return torrentFileHandlers;
    }

    public static LinkedHashMap<InetAddress, LinkedHashMap<String, TorrentRequester>> getTorrentRequesters() {
        return torrentRequesters;
    }

    private static void sendPacket(DatagramSocket socket, Message message, InetAddress host, int port) {
        byte[] data = message.asBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length, host, port);
        try {
            socket.send(packet);
        }
        catch (IOException e) {
            log(e);
        }
        catch (NullPointerException e) {
            print(e.getMessage());
        }
    }

    public static void multicastMessage(Message message) {
        sendPacket(groupSocket, message, groupAddress, port);
    }

    public static void unicastMessage(Message message, InetAddress receiver) {
        unicastMessage(message, receiver, port);
    }

    public static void unicastMessage(Message message, InetAddress receiver, int port) {
        sendPacket(uniSocket, message, receiver, port);
    }

    static Message receiveMessage() {
        byte[] data = new byte[MAX_MESSAGE_LENGTH];
        DatagramPacket packet = new DatagramPacket(data, data.length);
        try {
            groupSocket.receive(packet);
        }
        catch (SocketTimeoutException e) {
            return new Message(TIMED_OUT);
        }
        catch (IOException e) {
            log(e);
        }

        Message message;
        try {
            message = new Message(packet);
        }
        catch (Message.UnrecognizedFormatException e) {
            log(e);
            return null;
        }

        return message;
    }

    static void cleanUp() {

        print("Cleaning up!");
        running = false;
        try {
            keepAlive.join();
        }
        catch (InterruptedException e) {
            log(e);
        }

        try {
            commandParser.join();
        }
        catch (InterruptedException e) {
            log(e);
        }

        try {
            unicastListener.join();
        }
        catch (InterruptedException e) {
            log(e);
        }

        print("Leaving");
        try {
            groupSocket.leaveGroup(groupAddress);
        }
        catch (IOException e) {
            log(e);
        }
        groupSocket.close();
        uniSocket.close();
    }
}
