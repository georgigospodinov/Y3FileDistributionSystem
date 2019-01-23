package fileman.torrent;

import fileman.FileHandles;
import fileman.hash.FileHashing;

import java.io.*;
import java.security.NoSuchAlgorithmException;

import static fileman.hash.FileHashing.TORRENT_EXTENSION;
import static fileman.torrent.Torrent.FILEPATH_SEPARATOR;
import static util.CommonlyUsed.NEW_LINE;
import static util.CommonlyUsed.print;
import static util.Logger.log;

/**
 * Generates a torrent file for the given files.
 *
 * @version 2.2
 */
public class GenerateTorrent implements Runnable {

    public static final String DIR = "torrents/";

    private final String torrentId;
    private final FileOutputStream output;
    private final String[] inputFilesNames;
    private File commonParent;

    private GenerateTorrent(String torrentId, String... inputFilesNames) throws FileNotFoundException, NoSuchAlgorithmException {
        this.torrentId = torrentId;
        this.output = new FileOutputStream(DIR + torrentId + TORRENT_EXTENSION);
        this.inputFilesNames = inputFilesNames;
    }

    static String getPathRelativeTo(File file, File parent) {
        return file.getAbsolutePath().substring(parent.getAbsolutePath().length() + 1);
    }

    public static void runNew(String[] parts) {
        if (parts.length < 3) return;

        String torrentID = parts[1];
        String[] filenames = new String[parts.length - 2];
        System.arraycopy(parts, 2, filenames, 0, filenames.length);
        GenerateTorrent gt;
        try {
            gt = new GenerateTorrent(torrentID, filenames);
        }
        catch (FileNotFoundException | NoSuchAlgorithmException e) {
            print("Torrent " + torrentID + "generation failed.", e.getMessage());
            log(e);
            return;
        }
        new Thread(gt).start();
    }

    private void writeFileHash(File file) {
        try {
            FileInputStream input = new FileInputStream(file);
            FileHashing hashing = new FileHashing(input, getPathRelativeTo(file, commonParent), output);
            hashing.writeFileHash();
            input.close();
        }
        catch (NoSuchAlgorithmException | IOException e) {
            log(e);
        }
    }

    private void recursiveFileHash(File file) {
        File[] contents = file.listFiles();
        if (contents == null) writeFileHash(file);
        else for (File f : contents) recursiveFileHash(f);
    }

    private long totalFileSize(File file) {
        long size = file.length();
        File[] subFiles = file.listFiles();
        if (subFiles != null)
            for (File subFile : subFiles)
                size += totalFileSize(subFile);
        return size;
    }

    /**
     * Adds the torrent's id, the total size of all files, and the local parent path
     * as the first three lines in the torrent.
     *
     * @param totalSize the total size computed by the caller.
     */
    private void addTorrentMeta(long totalSize) {
        String meta = torrentId + NEW_LINE + totalSize + NEW_LINE + commonParent.getAbsolutePath() + NEW_LINE;
        try {
            output.write(meta.getBytes());
        }
        catch (IOException e) {
            log(e);
        }
    }

    private boolean sameCharAt(File[] inputFiles, int position) {
        char c = inputFiles[0].getAbsolutePath().charAt(position);
        for (int i = 1; i < inputFiles.length; i++) {
            if (inputFiles[i].getAbsolutePath().charAt(position) != c)
                return false;
        }

        return true;
    }

    private void determineCommonParent(File[] inputFiles) {
        int shortest = Integer.MAX_VALUE;
        for (File inputFile : inputFiles)
            if (inputFile.getAbsolutePath().length() < shortest)
                shortest = inputFile.getAbsolutePath().length();

        String path = inputFiles[0].getAbsolutePath();
        int positionOfLastSeparator = 0;
        for (int i = 0; i < shortest; i++) {
            if (!sameCharAt(inputFiles, i)) break;
            if (path.charAt(i) == FILEPATH_SEPARATOR.charAt(0))
                positionOfLastSeparator = i;
        }

        String commonPath = path.substring(0, positionOfLastSeparator+1);
        commonParent = new File(commonPath);
    }

    @Override
    public void run() {

        File[] inputFiles = new File[inputFilesNames.length];
        long totalSize = 0;
        for (int i = 0; i < inputFilesNames.length; i++) {
            inputFiles[i] = new File(inputFilesNames[i]);
            totalSize += totalFileSize(inputFiles[i]);
        }

        determineCommonParent(inputFiles);
        print(commonParent.getAbsolutePath());

        print("Writing torrent meta...");
        addTorrentMeta(totalSize);
        print("Writing hashes...");
        for (File f : inputFiles) recursiveFileHash(f);
        print("Torrent generated successfully!");
        FileHandles.addNewLocalTorrent(new File(DIR + torrentId + TORRENT_EXTENSION), commonParent.getAbsolutePath());

    }
}
