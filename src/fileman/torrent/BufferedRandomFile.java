package fileman.torrent;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import static java.lang.Math.min;
import static util.CommonlyUsed.PIECE_SIZE;
import static util.CommonlyUsed.getNumberOfPieces;
import static util.Logger.log;

public class BufferedRandomFile {

    public static final int MAX_BUFFERS = 1000;

    private final RandomAccessFile raf;
    private final long numberOfPieces;
    private final long fileSize;
    private final byte[][] buffer;
    private int startID = 0;
    private boolean isFresh = true;

    BufferedRandomFile(File file, long fileSize) throws IOException {
        this.raf = new RandomAccessFile(file, "rw");
        if (raf.length() < fileSize) raf.setLength(fileSize);  // For newly created files.
        this.numberOfPieces = getNumberOfPieces(fileSize);
        this.fileSize = fileSize;
        this.buffer = new byte[(int) min(numberOfPieces, MAX_BUFFERS)][];
    }

    private synchronized void readChunk(int pieceID) {
        try {  // It is likely that next call will require subsequent pieces only.
            raf.seek(pieceID * PIECE_SIZE);
        }
        catch (IOException e) {
            log(e);
        }

        startID = pieceID;
        int remainingPieces = (int) (numberOfPieces - pieceID);
        int lastPieceID = min(remainingPieces, MAX_BUFFERS) - 1;
        for (int i = 0; i < lastPieceID; i++) {
            buffer[i] = new byte[PIECE_SIZE];
            try {
                raf.read(buffer[i]);
            }
            catch (IOException e) {
                log(e);
            }
        }

        if (remainingPieces <= MAX_BUFFERS) {
            int lastPieceSize = (int) (fileSize - (numberOfPieces - 1) * PIECE_SIZE);
            buffer[lastPieceID] = new byte[lastPieceSize];
        }
        else buffer[lastPieceID] = new byte[PIECE_SIZE];
        try {
            raf.read(buffer[lastPieceID]);
        }
        catch (IOException e) {
            log(e);
        }
        isFresh = false;
    }

    byte[] getPiece(int pieceID) {
        if (pieceID >= numberOfPieces)
            throw new IndexOutOfBoundsException("Piece ID " + pieceID + " too high. File has " + numberOfPieces + " pieces.");
        if (isFresh) readChunk(pieceID);
        if (pieceID < startID || pieceID >= MAX_BUFFERS + startID)
            readChunk(pieceID);
        return buffer[pieceID - startID];
    }

    private void writeCurrentAndReposition() {
        try {
            if (raf.getFilePointer() != startID * PIECE_SIZE)
                raf.seek(startID * PIECE_SIZE);
        }
        catch (IOException e) {
            log(e);
        }

        int remainingPieces = (int) (numberOfPieces - startID);
        int lastPieceID = min(remainingPieces, MAX_BUFFERS) - 1;
        for (int i = 0; i <= lastPieceID; i++)
            try {
                // Leave empty space for every piece but the last.
                if (buffer[i] == null) {
                    if (i != lastPieceID)
                        raf.skipBytes(PIECE_SIZE);
                }
                else {  // The last piece might have data, however.
                    raf.write(buffer[i]);
                    buffer[i] = null;
                }
            }
            catch (IOException e) {
                log(e);
            }
    }

    void setPiece(int pieceID, byte[] pieceData) {
        if (pieceID >= numberOfPieces)
            throw new IndexOutOfBoundsException("Piece ID " + pieceID + " too high. File has " + numberOfPieces + " pieces.");
        if (pieceID < startID || pieceID >= MAX_BUFFERS + startID) {
            writeCurrentAndReposition();
            startID = pieceID;
        }

        buffer[pieceID - startID] = new byte[pieceData.length];
        System.arraycopy(pieceData, 0, buffer[pieceID - startID], 0, pieceData.length);
    }

    void writeAndClose() {
        writeCurrentAndReposition();
        try {
            raf.close();
        }
        catch (IOException e) {
            log(e);
        }
    }

    @Override
    public String toString() {
        return "{numberOfPieces=" + numberOfPieces +
                " fileSize=" + fileSize +
                " startID=" + startID + "}";
    }
}
