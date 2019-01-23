package communication.interaction;

import communication.sharing.FileDownloader;
import communication.torrent.TorrentRequester;
import fileman.FileHandles;
import fileman.torrent.GenerateTorrent;

import java.util.Scanner;

import static util.CommonlyUsed.print;
import static util.Logger.log;

/**
 * Represents a thread that simply waits for user input and sends it over the network.
 *
 * @version 2.5
 */
public class CommandParser extends Thread {

    private void parse(String line) {
        if (line.equals("")) return;
        String[] parts = line.split(" ");
        switch (parts[0].toLowerCase()) {
            case "list":
                print(FileHandles.getAllNetworkTorrents());
                break;
            case "torrent_request":
                new TorrentRequester(parts[1], parts.length == 3 ? parts[2] : "output_files").start();
                break;
            case "download":
                if (parts.length == 3)
                    FileDownloader.createNewDownloader(parts[1], parts[2]);
                else if (parts.length == 2)
                    FileDownloader.createNewDownloader(parts[1]);
                break;
            case "local_torrents":
                print(FileHandles.getLocalTorrents());
                break;
            case "print":
                FileHandles.printMap(parts[1]);
                break;
            case "printt":
                FileHandles.printTorrent(parts[1]);
                break;
            case "printtf":
                if (parts.length == 3)
                    print(FileHandles.getFileMeta(parts[1], parts[2]));
                else print(FileHandles.getFileMeta(parts[1]));
                break;
            case "gen":
                GenerateTorrent.runNew(parts);
                break;
            default:
                print("Command not recognized.");
        }
    }

    @Override
    public void run() {
        try {
            Thread.sleep(500);
        }
        catch (InterruptedException e) {
            log(e);
        }

        Scanner s = new Scanner(System.in);
        for (String line = s.nextLine(); !line.equalsIgnoreCase("END"); line = s.nextLine()) {
            try {
                parse(line);
            }
            catch (Exception e) {
                print("Could not parse command. Please, check parameters.");
                log(e);
            }
        }
        SocketInitializer.running = false;
    }
}
