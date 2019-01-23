package fileman.hash;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static util.CommonlyUsed.NEW_LINE;
import static util.CommonlyUsed.PIECE_SIZE;
import static util.Logger.log;

/**
 * Generates the hashes for a single file.
 *
 * @version 1.3
 */
public class FileHashing {

    public static final String TORRENT_EXTENSION = ".torrent";

    /** This is the length of the hash accounting for EOL delimiter. */
    public static final int HASH_LINE_LENGTH;

    /** This is the length of the hash as number of hexadecimal characters. */
    private static final int HASH_LENGTH;

    private static MessageDigest sha1 = null;

    static {
        try {
            sha1 = MessageDigest.getInstance("SHA1");
        }
        catch (NoSuchAlgorithmException e) {
            log(e);
        }
        HASH_LENGTH = sha1.getDigestLength() * 2;  // Two hexadecimal digits per byte.
        HASH_LINE_LENGTH = HASH_LENGTH + NEW_LINE.length();
    }

    private final FileInputStream input;
    private final String relativeFilePath;
    private final OutputStream output;

    public FileHashing(FileInputStream input, String relativeFilePath, OutputStream output) throws NoSuchAlgorithmException, FileNotFoundException {
        this.input = input;
        this.relativeFilePath = relativeFilePath;
        this.output = output;
    }

    /**
     * Performs the SHA-1 hashing algorithm on the given bytes
     * and returns the generated hash as a hexadecimal string.
     * This method needs to be synchronized,
     * otherwise the hashing algorithm digests data from multiple threads.
     *
     * @param bytes  the bytes to perform the hash on
     * @param amount the amount of bytes to read (starting at 0)
     * @return a hexadecimal representation of the hash
     */
    synchronized static String hashPart(byte[] bytes, int amount) {
        sha1.update(bytes, 0, amount);
        StringBuilder sb = new StringBuilder();
        byte[] hash = sha1.digest();
        for (byte b : hash) {
            // Hexadecimal, ignore sign.
            String hex = Integer.toHexString(b & 0xff);
            if (hex.length() == 1) hex = "0" + hex;  // Add leading zero.
            sb.append(hex);
        }

        return sb.toString();
    }

    /**
     * Reads the bytes from the given {@link FileInputStream} and
     * writes the hashes of the chunks consecutively.
     */
    private void hashChunks() {
        byte[] bytes = new byte[PIECE_SIZE];
        int amountRead;
        try {
            do {
                amountRead = input.read(bytes, 0, PIECE_SIZE);
                String hash = hashPart(bytes, amountRead) + NEW_LINE;
                output.write(hash.getBytes());
            }
            while (amountRead == PIECE_SIZE);
        }
        catch (IOException e) {
            log(e);
        }
    }

    /**
     * Adds the file's size and relative path to the torrent on two separate lines.
     * The path is relative to the common parent path specified in the first few lines.
     */
    private void addMeta() {
        try {
            String meta = input.available() + NEW_LINE + relativeFilePath + NEW_LINE;
            output.write(meta.getBytes());
        }
        catch (IOException e) {
            log(e);
        }
    }

    public void writeFileHash() {
        addMeta();
        hashChunks();
    }
}
