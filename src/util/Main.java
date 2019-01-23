package util;

import communication.interaction.Master;
import fileman.FileHandles;

public class Main {
    public static void main(String[] args) {
        Logger.open("log.txt");

        String groupName = "224.0.0.1";
        int port = 6789;
        Master.initialize(groupName, port);
        FileHandles.initialise(args);
        Master.run();

        Logger.close();
    }
}
