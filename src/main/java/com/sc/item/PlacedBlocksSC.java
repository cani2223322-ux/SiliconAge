package com.sc.item;

import com.sc.util.PlacedRecordSC;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/**
 * Blocks placed by players in one dimension (the world's per-dimension storage, "SiliconAgePlaced"): the Singular
 * drill's black hole gives no singularity crumbs for them (docs/plan-singular-tools.md §3.2 - no farming).
 * DrillEventsSC adds on BlockEvent.PlaceEvent and forgets on BlockEvent.BreakEvent. Bounded: the newest
 * PlacedRecordSC.DEFAULT_MAX positions per dimension are kept, older ones count as natural again.
 * Not tracked: blocks moved by pistons, falling sand / gravel landing elsewhere, blocks set by other mods without
 * a PlaceEvent (machines, world editors), anything placed before this record existed.
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

    public static boolean placed(World w, int x, int y, int z) {
        PlacedBlocksSC m = get(w);
        return m != null && m.record.contains(x, y, z);
    }

    public static void mark(World w, int x, int y, int z) {
        PlacedBlocksSC m = get(w);
        if (m != null && y >= 0 && y < 4096) {
            m.record.add(x, y, z);
            m.markDirty();
        }
    }

    public static void forget(World w, int x, int y, int z) {
        PlacedBlocksSC m = get(w);
        if (m != null && m.record.remove(x, y, z)) {
            m.markDirty();
        }
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
