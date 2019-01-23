package fileman.torrent;

import communication.owners.FileOwnerAsker;
import communication.structures.Message;
import fileman.hash.FileHashChecker;

import java.io.*;
import java.net.InetAddress;
import java.util.*;
import java.util.function.Consumer;

import static communication.interaction.SocketInitializer.me;
import static communication.interaction.SocketInitializer.multicastMessage;
import static communication.structures.Message.Types.I_HAVE;
import static fileman.hash.FileHashing.HASH_LINE_LENGTH;
import static fileman.torrent.GenerateTorrent.getPathRelativeTo;
import static util.CommonlyUsed.*;
import static util.Logger.log;

/**
 * Tracks all the peers that have pieces of a given torrent.
 *
 * @version 1.2
 */
public class Torrent implements Runnable {

    public static final String FILEPATH_SEPARATOR = "/";
    private final BufferedReader reader;
    private final String id;
    private final long totalSize;
    private final FileOwnerAsker asker;
    /**
     * Maps each file of the torrent to a list of its pieces.
     * Each piece is represented by a set of peers that own it.
     */
    private final LinkedHashMap<File, ArrayList<LinkedHashSet<InetAddress>>> filePieces = new LinkedHashMap<>();
    private final LinkedHashMap<String, RandomAccessFile> rafs = new LinkedHashMap<>();
    private final LinkedHashMap<String, BufferedRandomFile> fReaders = new LinkedHashMap<>();
    private final LinkedHashMap<String, Long> fileSizes = new LinkedHashMap<>();
    private File torrentFile;
    private File localFolder;

    public Torrent(File torrentFile) throws IOException {
        this.torrentFile = torrentFile;
        this.reader = new BufferedReader(new FileReader(this.torrentFile));
        this.id = reader.readLine();
        this.totalSize = Long.parseLong(reader.readLine());
        this.localFolder = new File(reader.readLine());
        this.asker = new FileOwnerAsker(this);
    }

    public final String getId() {
        return id;
    }

    /**
     * Returns a {@link LinkedHashMap} of filename to number of pieces.
     *
     * @return a map (filename to number of owners)
     */
    public LinkedHashMap<String, Integer> getFilesData() {
        Set<File> files = filePieces.keySet();
        LinkedHashMap<String, Integer> fileData = new LinkedHashMap<>(files.size());
        for (File f : files)
            fileData.put(getPathRelativeTo(f, localFolder), filePieces.get(f).size());

        return fileData;
    }

    public String[] getFilenames() {
        String[] filenames = new String[filePieces.size()];
        Set<File> files = filePieces.keySet();
        int i = 0;
        for (File file : files) {
            filenames[i] = getPathRelativeTo(file, localFolder);
            i++;
        }
        return filenames;
    }

    public FileOwnerAsker getAsker() {
        return asker;
    }

    public boolean doIOwn(String filename, int pieceID) {
        File f = new File(localFolder.getAbsolutePath() + FILEPATH_SEPARATOR + filename);
        Set<InetAddress> owners = filePieces.get(f).get(pieceID);
        return owners.contains(me);
    }

    public void addOwner(String filename, int pieceID, InetAddress owner) {
        File f = new File(localFolder.getAbsolutePath() + FILEPATH_SEPARATOR + filename);
        Set<InetAddress> owners = filePieces.get(f).get(pieceID);
        owners.add(owner);
    }

    public RandomAccessFile openTorrent() throws FileNotFoundException {
        return new RandomAccessFile(torrentFile, "r");
    }

    public BufferedReader getReader() throws FileNotFoundException {
        return new BufferedReader(new FileReader(this.torrentFile));
    }

    public long getFileSize(String filename) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(torrentFile));
        reader.readLine();  // torrentID
        reader.readLine();  // total size
        reader.readLine();  // local path
        long fileSize;
        String filePath;
        do {
            fileSize = Long.parseLong(reader.readLine());
            filePath = reader.readLine();

            if (filePath.equals(filename))
                return fileSize;
            else skipHashesAndGetPieces(fileSize, reader);
        } while (reader.ready());

        return -1;
    }

    public int[] getPiecesByRarity(String filename) {
        File f = new File(localFolder.getAbsolutePath() + FILEPATH_SEPARATOR + filename);
        ArrayList<LinkedHashSet<InetAddress>> piecesOwners = filePieces.get(f);
        return PairMergeSorter.mergeSort(piecesOwners);
    }

    public void forEachFile(Consumer<String> action) {
        if (action == null) throw new NullPointerException();
        Set<File> files = filePieces.keySet();
        for (File file : files)
            action.accept(getPathRelativeTo(file, localFolder));
    }

    private File getFileOf(String filename) {
        File file = new File(localFolder.getAbsolutePath() + FILEPATH_SEPARATOR + filename);
        if (!filePieces.containsKey(file))
            throw new NullPointerException("file " + file.getAbsolutePath() + " not in torrent");
        return file;
    }

    public synchronized byte[] getFilePiece(String filename, int pieceID) {
        if (!fReaders.containsKey(filename))
            try {
                fReaders.put(filename, new BufferedRandomFile(getFileOf(filename), fileSizes.get(filename)));
            }
            catch (Exception e) {
                log(e);
            }
        return fReaders.get(filename).getPiece(pieceID);
    }

    public void setFilePiece(String filename, Integer pieceID, byte[] pieceData) {
        if (!fReaders.containsKey(filename))
            try {
                fReaders.put(filename, new BufferedRandomFile(getFileOf(filename), fileSizes.get(filename)));
            }
            catch (Exception e) {
                log(e);
            }

        fReaders.get(filename).setPiece(pieceID, pieceData);
    }

    public void writeAndCloseFile(String filename) {
        BufferedRandomFile reader = fReaders.get(filename);
        if (reader == null) return;
        reader.writeAndClose();
    }

    public RandomAccessFile openFileW(String filename) {
        if (!rafs.containsKey(filename)) {
            File file = getFileOf(filename);
            try {
                if (!file.exists()) {
                    file.getParentFile().mkdirs();
                    file.createNewFile();
                }
                rafs.put(filename, new RandomAccessFile(getFileOf(filename), "rw"));
            }
            catch (IOException e) {
                log(e);
            }
        }
        return rafs.get(filename);
    }

    public ArrayList<LinkedHashSet<InetAddress>> getSetsOfOwners(String filename) {
        File file = new File(localFolder.getAbsolutePath() + FILEPATH_SEPARATOR + filename);
        return filePieces.get(file);
    }

    public int getNumberOfFiles() {
        return filePieces.size();
    }

    private File getLocalFolder() {
        return localFolder;
    }

    public void setLocalFolder(String directory) {
        this.localFolder = new File(directory);
        File temp = new File(torrentFile.getAbsolutePath() + ".tmp");
        BufferedWriter writer;
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(torrentFile));
            writer = new BufferedWriter(new FileWriter(temp));
            writer.write(reader.readLine() + NEW_LINE);  // ID
            writer.write(reader.readLine() + NEW_LINE);  // size
            reader.readLine(); // skip current path
            writer.write(localFolder.getAbsolutePath() + NEW_LINE);
            while (reader.ready())
                writer.write(reader.readLine() + NEW_LINE);
            writer.close();
            reader.close();
        }
        catch (IOException e) {
            log(e);
            return;
        }

        torrentFile.delete();
        temp.renameTo(torrentFile);
        // the torrentFile instance now points to the one created by temp

    }

    private int skipHashesAndGetPieces(long fileSize, BufferedReader reader) throws IOException {
        int pieces = (int) getNumberOfPieces(fileSize);
        long skipped = reader.skip(pieces * HASH_LINE_LENGTH);
        return pieces;
    }

    private void createFileEntry(String filePath, int pieces) {
        ArrayList<LinkedHashSet<InetAddress>> piecesOwners = new ArrayList<>(pieces);
        for (int i = 0; i < pieces; i++)
            piecesOwners.add(new LinkedHashSet<>());
        File f = new File(localFolder.getAbsolutePath() + FILEPATH_SEPARATOR + filePath);
        filePieces.put(f, piecesOwners);
    }

    private void initialiseFileEntries() {
        long fileSize;
        String filePath;
        int pieces;
        try {
            do {
                String fileSizeStr = reader.readLine();
                fileSize = Long.parseLong(fileSizeStr);
                filePath = reader.readLine();
                fileSizes.put(filePath, fileSize);
                pieces = skipHashesAndGetPieces(fileSize, reader);
                createFileEntry(filePath, pieces);
            } while (reader.ready());
        }
        catch (IOException e) {
            log(e);
        }
    }

    /**
     * Starts FileHashChecker threads to determine which file pieces are present on this host.
     *
     * @return a {@link LinkedList} of the created threads.
     */
    private LinkedList<FileHashChecker> runCheckers() {
        if (!localFolder.exists()) return null;
        LinkedList<FileHashChecker> checkers = new LinkedList<>();
        Set<File> files = filePieces.keySet();
        for (File f : files) {
            if (!f.exists()) continue;
            FileHashChecker checker;
            try {
                checker = new FileHashChecker(torrentFile, getPathRelativeTo(f, localFolder));
                checker.start();
                checkers.addLast(checker);
            }
            catch (FileNotFoundException e) {
                // The file is not present here.
            }
            catch (IOException e) {
                log(e);
            }
        }
        return checkers;
    }

    private boolean atLeastOneRunning(LinkedList<FileHashChecker> checkers) {
        for (FileHashChecker checker : checkers)
            if (checker.getState().equals(Thread.State.RUNNABLE))
                return true;

        return false;
    }

    private void fillCheckersResults(LinkedList<FileHashChecker> checkers) {
        for (FileHashChecker checker : checkers) {
            try {
                checker.join();
            }
            catch (InterruptedException e) {
                log(e);
            }
            LinkedHashSet<Integer> piecesOwned = checker.getPiecesOwned();
            File checkerFile = new File(localFolder.getAbsolutePath() + FILEPATH_SEPARATOR + checker.getInputFileName());
            ArrayList<LinkedHashSet<InetAddress>> owners = filePieces.get(checkerFile);
            for (Integer pieceOwned : piecesOwned) {
                LinkedHashSet<InetAddress> ownersOfPiece = owners.get(pieceOwned);
                Message message = new Message(I_HAVE, id, checker.getInputFileName(), pieceOwned);
                multicastMessage(message);
                ownersOfPiece.add(me);
            }
        }
    }

    @Override
    public void run() {
        initialiseFileEntries();
        LinkedList<FileHashChecker> checkers = runCheckers();
        if (checkers == null) return;
        while (atLeastOneRunning(checkers)) try {
            Thread.sleep(100);
        }
        catch (InterruptedException e) {
            log(e);
        }
        fillCheckersResults(checkers);
        asker.start();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Torrent)) return false;
        Torrent other = (Torrent) obj;
        return this.id.equals(other.id);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(id).append(NEW_LINE);
        sb.append(torrentFile.length()).append(NEW_LINE);
        sb.append(filePieces.size()).append(NEW_LINE);
        Set<File> files = filePieces.keySet();
        for (File file : files)
            sb.append(getPathRelativeTo(file, localFolder)).append(NEW_LINE);
        return sb.toString();
    }

    public void printFilePiecesOwners() {
        filePieces.forEach((file, owners) -> {
            print(getPathRelativeTo(file, this.getLocalFolder()), owners);
        });
    }

    public void ask(InetAddress owner, int port) {
        filePieces.forEach((file, listOfSets) -> {
            for(LinkedHashSet<InetAddress> set : listOfSets)
                if (!set.contains(owner)) {  // If at least one set does not contain the owner, ask him.
                    new FileOwnerAsker(this, owner, port).start();
                    return;
                }
        });
    }
}
