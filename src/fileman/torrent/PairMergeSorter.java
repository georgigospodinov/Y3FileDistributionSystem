package fileman.torrent;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashSet;

/**
 * Provides sorting for the Torrent class's pieces.
 *
 * @version 1.1
 */
class PairMergeSorter {

    private static Pair[] merge(Pair[] left, Pair[] right) {
        int ls = left.length, rs = right.length;
        Pair[] merged = new Pair[ls + rs];
        int li = 0, ri = 0;
        for (int i = 0; i < ls + rs; i++) {
            if (li == ls)
                merged[i] = right[ri++];
            else if (ri == rs)
                merged[i] = left[li++];
            else if (left[li].occurrences < right[ri].occurrences)
                merged[i] = left[li++];
            else
                merged[i] = right[ri++];
        }


        return merged;
    }

    private static Pair[] mergeSort(Pair[] arr) {
        int size = arr.length;
        if (size == 1) return arr;

        int ls = size / 2;
        int rs = size - ls;
        Pair[] left = new Pair[ls];
        Pair[] right = new Pair[rs];
        System.arraycopy(arr, 0, left, 0, ls);
        System.arraycopy(arr, ls, right, 0, rs);
        return merge(mergeSort(left), mergeSort(right));
    }

    static int[] mergeSort(ArrayList<LinkedHashSet<InetAddress>> sets) {
        int n = sets.size();
        Pair[] pairs = new Pair[n];
        for (int i = 0; i < n; i++)
            pairs[i] = new Pair(sets.get(i).size(), i);
        pairs = mergeSort(pairs);

        int[] indexes = new int[n];
        for (int i = 0; i < n; i++)
            indexes[i] = pairs[i].id;
        return indexes;
    }

    private static class Pair {
        int occurrences;
        int id;

        Pair(int occurrences, int id) {
            this.occurrences = occurrences;
            this.id = id;
        }

        @Override
        public String toString() {
            return "Pair{" +
                    "occurrences=" + occurrences +
                    ", id=" + id +
                    '}';
        }
    }
}
