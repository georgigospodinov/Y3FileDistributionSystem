package fileman.hash;

import java.io.*;
import java.util.LinkedHashSet;

import static fileman.hash.FileHashing.hashPart;
import static fileman.hash.HashChecker.skipOtherFileHashes;
import static fileman.torrent.Torrent.FILEPATH_SEPARATOR;
import static util.CommonlyUsed.*;
import static util.Logger.log;

/**
 * Checks all the hashes of the pieces of the given file.
 *
 * @version 1.0
 */
public class FileHashChecker extends Thread {

    private final String inputFileName;
    private final FileInputStream input;
    private final BufferedReader torrent;
    private final LinkedHashSet<Integer> piecesOwned = new LinkedHashSet<>();

    public FileHashChecker(File torrent, String inputFileName) throws IOException {
        this.inputFileName = inputFileName;
        this.torrent = new BufferedReader(new FileReader(torrent));
        this.torrent.readLine();  // Torrent ID
        this.torrent.readLine();  // Total FileSize
        File local = new File(this.torrent.readLine());
        if (!local.exists()) throw new FileNotFoundException("Not local!");
        this.input = new FileInputStream(local.getAbsolutePath() + FILEPATH_SEPARATOR + inputFileName);
    }

    public String getInputFileName() {
        return inputFileName;
    }

    public LinkedHashSet<Integer> getPiecesOwned() {
        return piecesOwned;
    }

    private long findFileHash() {
        try {
            return getNumberOfPieces(skipOtherFileHashes(torrent, inputFileName));
        }
        catch (IOException e) {
            log(e);
            return -1;
        }
    }

    private boolean checkNextPiece(int i) {
        byte[] piece = new byte[PIECE_SIZE];
        int actuallyRead;
        try {
            actuallyRead = input.read(piece, 0, piece.length);
        }
        catch (IOException e) {
            log(e);
            return false;
        }

        String hashExpected;
        try {
            hashExpected = torrent.readLine();
        }
        catch (IOException e) {
            log(e);
            return false;
        }

        String hashCalculated;
        try {
            hashCalculated = hashPart(piece, actuallyRead);
        }
        catch (Exception e) {
            log(e);
            log(this.inputFileName);
            hashCalculated = "---";
        }


        if (hashCalculated.equals(hashExpected)) return true;
        else {
            print(inputFileName, i, "calculated:" + hashCalculated, "expected:  " + hashExpected, "");
            return false;
        }
    }

    @Override
    public void run() {
        long pieces = findFileHash();
        if (pieces == -1) return;

        for (int i = 0; i < pieces; i++) {
            if (!checkNextPiece(i)) {
                continue;
            }
            piecesOwned.add(i);
        }
    }
}
