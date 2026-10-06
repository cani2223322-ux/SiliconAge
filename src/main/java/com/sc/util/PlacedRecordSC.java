package com.sc.util;

/**
 * A bounded set of block positions (DrillZoneSC.pack) that remembers its insertion order: the record of blocks
 * placed by players, so the Singular drill's black hole doesn't count them for singularity crumbs (anti-farm).
 * Pure - no world; item/PlacedBlocksSC keeps one per dimension. Beyond `max` positions the oldest are forgotten
 * (such a block then counts as natural again - an accepted leak). Primitive arrays: an open-addressing hash
 * (linear probing, backward-shift deletion) of key -> insertion number, and a FIFO ring of (key, number); a ring
 * entry whose number no longer matches the hash (removed, or removed and added again) is stale and skipped.
 * Grown on demand: about 30-60 bytes a position (200k: 6-12 MB at worst).
 */
public final class PlacedRecordSC {

    public static final int DEFAULT_MAX = 200000;

    private final int max;
    // hash
    private long[] keys;
    private int[] nums;
    private boolean[] used;
    private int size;
    // FIFO ring
    private long[] ringKeys;
    private int[] ringNums;
    private int head, count;
    private int nextNum = 1;

    public PlacedRecordSC() {
        this(DEFAULT_MAX);
    }

    public PlacedRecordSC(int max) {
        this.max = Math.max(1, max);
        keys = new long[64];
        nums = new int[64];
        used = new boolean[64];
        ringKeys = new long[64];
        ringNums = new int[64];
    }

    public int size() {
        return size;
    }

    public int max() {
        return max;
    }

    private static int mix(long k) {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        k *= 0xc4ceb9fe1a85ec53L;
        k ^= k >>> 33;
        return (int) k;
    }

    private int slot(long k) {
        int mask = keys.length - 1;
        int i = mix(k) & mask;
        while (used[i] && keys[i] != k) {
            i = (i + 1) & mask;
        }
        return i;
    }

    public boolean contains(long k) {
        return used[slot(k)];
    }

    public boolean contains(int x, int y, int z) {
        return contains(DrillZoneSC.pack(x, y, z));
    }

    /** Remembers k (already there: kept with its old age). Over `max`: the oldest go. */
    public void add(long k) {
        int i = slot(k);
        if (used[i]) {
            return;
        }
        int num = nextNum++;
        if (nextNum == 0) {
            nextNum = 1;                                   // 0 never marks a live entry
        }
        used[i] = true;
        keys[i] = k;
        nums[i] = num;
        size++;
        push(k, num);
        if (size * 4 > keys.length * 3) {
            rehash(keys.length * 2);
        }
        while (size > max) {
            evictOldest();
        }
        if (count > 2 * size + 1024) {
            compact();                                     // many stale entries (place / break loops)
        }
    }

    public void add(int x, int y, int z) {
        add(DrillZoneSC.pack(x, y, z));
    }

    /** Forgets k. @return whether it was there */
    public boolean remove(long k) {
        int i = slot(k);
        if (!used[i]) {
            return false;
        }
        deleteAt(i);
        return true;
    }

    public boolean remove(int x, int y, int z) {
        return remove(DrillZoneSC.pack(x, y, z));
    }

    public void clear() {
        java.util.Arrays.fill(used, false);
        size = 0;
        head = 0;
        count = 0;
    }

    /** The live positions, oldest first. */
    public long[] toArray() {
        long[] out = new long[size];
        int n = 0;
        for (int j = 0; j < count && n < size; j++) {
            int r = (head + j) % ringKeys.length;
            if (live(ringKeys[r], ringNums[r])) {
                out[n++] = ringKeys[r];
            }
        }
        return n == out.length ? out : java.util.Arrays.copyOf(out, n);
    }

    // ------------------------------------------------------------------ internals

    private boolean live(long k, int num) {
        int i = slot(k);
        return used[i] && nums[i] == num;
    }

    private void push(long k, int num) {
        if (count == ringKeys.length) {
            int cap = ringKeys.length * 2;
            long[] nk = new long[cap];
            int[] nn = new int[cap];
            for (int j = 0; j < count; j++) {
                int r = (head + j) % ringKeys.length;
                nk[j] = ringKeys[r];
                nn[j] = ringNums[r];
            }
            ringKeys = nk;
            ringNums = nn;
            head = 0;
        }
        int r = (head + count) % ringKeys.length;
        ringKeys[r] = k;
        ringNums[r] = num;
        count++;
    }

    private void evictOldest() {
        while (count > 0) {
            long k = ringKeys[head];
            int num = ringNums[head];
            head = (head + 1) % ringKeys.length;
            count--;
            int i = slot(k);
            if (used[i] && nums[i] == num) {
                deleteAt(i);
                return;
            }
        }
    }

    private void compact() {
        long[] live = toArray();
        int[] liveNums = new int[live.length];
        for (int j = 0; j < live.length; j++) {
            liveNums[j] = nums[slot(live[j])];
        }
        int cap = 64;
        while (cap < live.length + 1) {
            cap *= 2;
        }
        ringKeys = new long[cap];
        ringNums = new int[cap];
        System.arraycopy(live, 0, ringKeys, 0, live.length);
        System.arraycopy(liveNums, 0, ringNums, 0, live.length);
        head = 0;
        count = live.length;
    }

    /** Removes the hash slot i, shifting the following run back (no tombstones). */
    private void deleteAt(int i) {
        int mask = keys.length - 1;
        used[i] = false;
        size--;
        int j = i;
        while (true) {
            j = (j + 1) & mask;
            if (!used[j]) {
                return;
            }
            int home = mix(keys[j]) & mask;
            // move j back to i when its home isn't in the cyclic range (i, j]
            boolean between = i <= j ? home > i && home <= j : home > i || home <= j;
            if (!between) {
                keys[i] = keys[j];
                nums[i] = nums[j];
                used[i] = true;
                used[j] = false;
                i = j;
            }
        }
    }

    private void rehash(int cap) {
        long[] ok = keys;
        int[] on = nums;
        boolean[] ou = used;
        keys = new long[cap];
        nums = new int[cap];
        used = new boolean[cap];
        for (int i = 0; i < ok.length; i++) {
            if (ou[i]) {
                int s = slot(ok[i]);
                used[s] = true;
                keys[s] = ok[i];
                nums[s] = on[i];
            }
        }
    }
}
