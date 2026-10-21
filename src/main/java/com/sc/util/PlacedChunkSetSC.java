package com.sc.util;

/**
 * СТ-2 (audit 2026-10-20), pure: the player-placed blocks of one chunk - a set of positions inside it, each an int key
 * (x & 15) | (z & 15) << 4 | y << 8 (y 0..4095), in an open-addressing table (linear probing, deletion by shifting back) that
 * grows as it fills. Saved with its chunk as an int array (PlacedChunksSC). Not thread-safe: the server's thread only.
 */
public final class PlacedChunkSetSC {

    private static final int EMPTY = -1;

    private int[] table = newTable(16);
    private int size;

    /** The key of a block position (x and z - any world coordinates: their low 4 bits are taken); -1 for a y outside 0..4095. */
    public static int key(int x, int y, int z) {
        return y < 0 || y > 4095 ? -1 : (x & 15) | (z & 15) << 4 | y << 8;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public boolean contains(int k) {
        return k >= 0 && table[slot(k)] == k;
    }

    /** @return true when `k` was not in yet (a real change) */
    public boolean add(int k) {
        if (k < 0) {
            return false;
        }
        int i = slot(k);
        if (table[i] == k) {
            return false;
        }
        table[i] = k;
        size++;
        if (size * 2 > table.length) {
            grow();
        }
        return true;
    }

    /** @return true when `k` was in (a real change) */
    public boolean remove(int k) {
        if (k < 0) {
            return false;
        }
        int mask = table.length - 1;
        int i = slot(k);
        if (table[i] != k) {
            return false;
        }
        table[i] = EMPTY;
        size--;
        // shift the run after it back, so no key is cut off from its home slot
        for (int j = (i + 1) & mask; table[j] != EMPTY; j = (j + 1) & mask) {
            int moved = table[j];
            table[j] = EMPTY;
            table[slot(moved)] = moved;
        }
        return true;
    }

    /** The keys, in no particular order (for the chunk's NBT). */
    public int[] toArray() {
        int[] out = new int[size];
        int n = 0;
        for (int v : table) {
            if (v != EMPTY) {
                out[n++] = v;
            }
        }
        return out;
    }

    /** A set of `keys` (a chunk's saved array; bad entries - out of 0..2^20 - skipped). */
    public static PlacedChunkSetSC fromArray(int[] keys) {
        PlacedChunkSetSC s = new PlacedChunkSetSC();
        if (keys != null) {
            for (int k : keys) {
                if (k >= 0 && k < 1 << 20) {
                    s.add(k);
                }
            }
        }
        return s;
    }

    private int slot(int k) {
        int mask = table.length - 1;
        int i = mix(k) & mask;
        while (table[i] != EMPTY && table[i] != k) {
            i = (i + 1) & mask;
        }
        return i;
    }

    private static int mix(int k) {
        int h = k * 0x9E3779B9;
        return h ^ h >>> 16;
    }

    private void grow() {
        int[] old = table;
        table = newTable(old.length * 2);
        for (int v : old) {
            if (v != EMPTY) {
                table[slot(v)] = v;
            }
        }
    }

    private static int[] newTable(int n) {
        int[] t = new int[n];
        java.util.Arrays.fill(t, EMPTY);
        return t;
    }
}
