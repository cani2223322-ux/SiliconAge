package com.sc.item;

import com.sc.util.PlacedRecordSC;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/**
 * Blocks placed by players in one dimension (the world's per-dimension storage, "SiliconAgePlaced"): the Singular
 * drill's black hole gives no singularity crumbs for them (docs/plan-singular-tools.md §3.2 - no farming).
 * СТ-2 (audit 2026-10-20): the OLD journal - read only now. The marks live in their chunks (PlacedChunksSC); this file is
 * never written again (never marked dirty) nor removed: when a chunk from before the change loads, its marks here move
 * into it (takeChunk) - nothing is lost. Its static placed / mark / forget pass to PlacedChunksSC.
 */
public class PlacedBlocksSC extends WorldSavedData {

    public static final String NAME = "SiliconAgePlaced";

    private final PlacedRecordSC record = new PlacedRecordSC();

    public PlacedBlocksSC(String name) {
        super(name);
    }

    /** This dimension's record (server only; null on the client). */
    public static PlacedBlocksSC get(World w) {
        if (w == null || w.isRemote || w.perWorldStorage == null) {
            return null;
        }
        PlacedBlocksSC m = (PlacedBlocksSC) w.perWorldStorage.loadData(PlacedBlocksSC.class, NAME);
        if (m == null) {
            m = new PlacedBlocksSC(NAME);
            w.perWorldStorage.setData(NAME, m);
        }
        return m;
    }

    /** СТ-2: the chunks' own marks (PlacedChunksSC). */
    @Deprecated
    public static boolean placed(World w, int x, int y, int z) {
        return PlacedChunksSC.placed(w, x, y, z);
    }

    /** СТ-2: into the chunk (PlacedChunksSC) - this journal is never written again. */
    @Deprecated
    public static void mark(World w, int x, int y, int z) {
        PlacedChunksSC.mark(w, x, y, z);
    }

    @Deprecated
    public static void forget(World w, int x, int y, int z) {
        PlacedChunksSC.forget(w, x, y, z);
    }

    /** СТ-2: the journal's marks by chunk (chunkXZ2Int -> the chunk-local keys), laid out on the first take; never saved. */
    private java.util.Map<Long, int[]> byChunk;

    /**
     * СТ-2: the old marks of chunk (cx, cz) as PlacedChunkSetSC keys - handed over once (forgotten here in memory; the file
     * keeps them, but the chunk is written as moved over, so it never asks again). null - none.
     */
    public static int[] takeChunk(World w, int cx, int cz) {
        PlacedBlocksSC m = get(w);
        if (m == null || m.record.size() == 0 && m.byChunk == null) {
            return null;
        }
        if (m.byChunk == null) {
            java.util.Map<Long, java.util.List<Integer>> lists = new java.util.HashMap<Long, java.util.List<Integer>>();
            for (long k : m.record.toArray()) {
                int[] p = com.sc.util.DrillZoneSC.unpack(k);
                Long key = Long.valueOf(net.minecraft.world.ChunkCoordIntPair.chunkXZ2Int(p[0] >> 4, p[2] >> 4));
                java.util.List<Integer> l = lists.get(key);
                if (l == null) {
                    l = new java.util.ArrayList<Integer>();
                    lists.put(key, l);
                }
                l.add(Integer.valueOf(com.sc.util.PlacedChunkSetSC.key(p[0], p[1], p[2])));
            }
            m.byChunk = new java.util.HashMap<Long, int[]>();
            for (java.util.Map.Entry<Long, java.util.List<Integer>> e : lists.entrySet()) {
                int[] a = new int[e.getValue().size()];
                for (int i = 0; i < a.length; i++) {
                    a[i] = e.getValue().get(i).intValue();
                }
                m.byChunk.put(e.getKey(), a);
            }
            m.record.clear();                               // laid out by chunk: the flat copy goes (the file stays as it is)
        }
        return m.byChunk.remove(Long.valueOf(net.minecraft.world.ChunkCoordIntPair.chunkXZ2Int(cx, cz)));
    }

    public int size() {
        return record.size();
    }

    /** Saved as one int array: (high, low) halves of each packed position, oldest first. */
    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        record.clear();
        int[] a = nbt.getIntArray("Pos");
        for (int i = 0; i + 1 < a.length; i += 2) {
            record.add((long) a[i] << 32 | a[i + 1] & 0xFFFFFFFFL);
        }
    }

    /** СТ-2: never asked (never dirty) - the old journal is read only; kept so a save written anyway would be the same. */
    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        long[] all = record.toArray();
        int[] a = new int[all.length * 2];
        for (int i = 0; i < all.length; i++) {
            a[2 * i] = (int) (all[i] >>> 32);
            a[2 * i + 1] = (int) all[i];
        }
        nbt.setTag("Pos", new NBTTagIntArray(a));
    }
}
