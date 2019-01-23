package communication.interaction;

import communication.structures.Message;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.SocketTimeoutException;

import static communication.interaction.Master.respond;
import static communication.interaction.SocketInitializer.*;
import static communication.structures.Message.MAX_MESSAGE_LENGTH;
import static util.Logger.log;

public class UnicastListener extends Thread {

    @Override
    public void run() {
        byte[] data = new byte[MAX_MESSAGE_LENGTH];
        DatagramPacket packet = new DatagramPacket(data, data.length);
        while (isRunning()) {
            try {
                uniSocket.receive(packet);
            }
            catch (SocketTimeoutException ignored) {
                continue;
            }
            catch (IOException e) {
                log(e);
            }

            try {
                Message msg = new Message(packet);
                respond(msg);
            }
            catch (Message.UnrecognizedFormatException e) {
                log(e);
            }
        }
    }
}
