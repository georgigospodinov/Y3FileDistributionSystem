package fileman.hash;

import java.io.BufferedReader;
import java.io.IOException;

import static fileman.hash.FileHashing.HASH_LINE_LENGTH;
import static fileman.hash.FileHashing.hashPart;
import static util.CommonlyUsed.getNumberOfPieces;

/**
 * Checks if the expected and calculated hash for a given piece are the same.
 *
 * @version 1.2
 */
public class HashChecker {

    /**
     * Reads the torrent, looking for inputFileName
     * and positions the reader at the first byte of the hash.
     *
     * @param torrent       a {@link BufferedReader} of the torrent to read
     * @param inputFileName the name of the input file to look for
     * @return the size of the input file
     * @throws IOException if reading or skipping bytes of the torrent fails
     */
    static long skipOtherFileHashes(BufferedReader torrent, String inputFileName) throws IOException {
        long fileSize;
        String fileName;
        do {
            fileSize = Integer.parseInt(torrent.readLine());
            fileName = torrent.readLine();

            if (fileName.equals(inputFileName)) break;

            long pieces = getNumberOfPieces(fileSize);
            torrent.skip(pieces * HASH_LINE_LENGTH);

        } while (torrent.ready());
        return fileSize;
    }

    private static String readHash(BufferedReader torrentReader, String inputFileName, int pieceID) throws IOException {
        torrentReader.readLine();  // Torrent ID
        torrentReader.readLine();  // Total FileSize
        torrentReader.readLine();  // Local path
        skipOtherFileHashes(torrentReader, inputFileName);
        torrentReader.skip(pieceID * HASH_LINE_LENGTH);
        return torrentReader.readLine();
    }

    public static boolean isCorrect(BufferedReader torrentReader, String inputFileName, int pieceIndex, byte[] piece) throws IOException {
        String hashExpected = readHash(torrentReader, inputFileName, pieceIndex);
        String hashCalculated = hashPart(piece, piece.length);
        return hashCalculated.equals(hashExpected);
    }
}
