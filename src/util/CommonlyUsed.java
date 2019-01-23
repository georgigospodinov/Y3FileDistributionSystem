package util;

import java.util.Collection;

public class CommonlyUsed {

    public static final String NEW_LINE = "\n";
    public static final int PIECE_SIZE = 60 * 1024;  // 60 KB

    /* print wrapper */
    public static void print(Object... objects) {
        for (Object o : objects)
            print(o, 0);
    }

    private static void printTabs(int numberOfTabs) {
        for (int i = 0; i < numberOfTabs; i++)
            System.out.print("\t");
    }

    private static void printArray(Object[] arr, int nestingLevel) {
        printTabs(nestingLevel - 1);
        print("[");

        for (Object o : arr)
            print(o, nestingLevel);

        printTabs(nestingLevel - 1);
        print("]");
    }

    private static void print(Object o, int nestingLevel) {
        if (o instanceof int[]) {
            int[] x = ((int[]) o);
            Object[] objs = new Object[x.length];
            for (int i = 0; i < x.length; i++) objs[i] = x[i];
            printArray(objs, nestingLevel + 1);
        }
        else if (o instanceof double[]) {
            double[] x = ((double[]) o);
            Object[] objs = new Object[x.length];
            for (int i = 0; i < x.length; i++) objs[i] = x[i];
            printArray(objs, nestingLevel + 1);
        }
        else if (o instanceof char[]) {
            char[] x = ((char[]) o);
            Object[] objs = new Object[x.length];
            for (int i = 0; i < x.length; i++) objs[i] = x[i];
            printArray(objs, nestingLevel + 1);
        }
        else if (o instanceof float[]) {
            float[] x = ((float[]) o);
            Object[] objs = new Object[x.length];
            for (int i = 0; i < x.length; i++) objs[i] = x[i];
            printArray(objs, nestingLevel + 1);
        }
        else if (o instanceof boolean[]) {
            boolean[] x = ((boolean[]) o);
            Object[] objs = new Object[x.length];
            for (int i = 0; i < x.length; i++) objs[i] = x[i];
            printArray(objs, nestingLevel + 1);
        }
        else if (o instanceof byte[]) {
            byte[] x = ((byte[]) o);
            Object[] objs = new Object[x.length];
            for (int i = 0; i < x.length; i++) objs[i] = x[i];
            printArray(objs, nestingLevel + 1);
        }
        else if (o instanceof Object[])
            printArray(((Object[]) o), nestingLevel + 1);
        else if (o instanceof Collection)
            printArray(((Collection) o).toArray(), nestingLevel + 1);
        else {
            printTabs(nestingLevel);
            System.out.println(String.valueOf(o));
        }
    }

    /* Frequent calculations. */
    public static long getNumberOfPieces(long size) {
        long pieces = size / PIECE_SIZE;
        return pieces * PIECE_SIZE < size ? pieces + 1 : pieces;
    }

    public static String getPiece(String text, int pieceID) {
        long pieces = getNumberOfPieces(text.length());
        if (pieces <= pieceID || 0 > pieceID) return "";
        if (pieceID == pieces - 1)
            return text.substring(pieceID * PIECE_SIZE);
        else return text.substring(pieceID * PIECE_SIZE, (pieceID + 1) * PIECE_SIZE);
    }

}
