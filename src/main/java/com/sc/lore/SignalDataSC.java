package com.sc.lore;

import com.sc.dim.SubstrateSC;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/**
 * Подложка Ф5 (docs/plan-substrate-tech-f5.md §7.2, rule ПЛ-5): the Lithographers' Signal of this world - WorldSavedData
 * "SCSi14Signal" in the Substrate's perWorldStorage (as the weather's): the last day rolled (never twice), the signal on now (its
 * crossing, the beam's base, its window), the cache made. Package J writes it (SignalSC); `extra` - J's own fields. Server thread only.
 * Shared core: only the coordinator edits this file.
 */
public class SignalDataSC extends WorldSavedData {

    public static final String NAME = "SCSi14Signal";

    /** The game day last rolled (-1 never); the signal on now; its crossing; the beam's base (the plaza's floor); its window (world times). */
    public long rolledDay = -1L;
    public boolean on;
    public int i, j, x, y, z;
    public long startAt, endAt;
    /** The cache and its patrol were made (once a signal). */
    public boolean cacheDone;
    public NBTTagCompound extra = new NBTTagCompound();

    public SignalDataSC(String name) {
        super(name);
    }

    public SignalDataSC() {
        this(NAME);
    }

    /** The Substrate's data (made on first use), null elsewhere / on a client. */
    public static SignalDataSC get(World w) {
        if (w == null || w.isRemote || !SubstrateSC.is(w)) {
            return null;
        }
        SignalDataSC d = (SignalDataSC) w.perWorldStorage.loadData(SignalDataSC.class, NAME);
        if (d == null) {
            d = new SignalDataSC();
            w.perWorldStorage.setData(NAME, d);
        }
        return d;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        rolledDay = nbt.hasKey("Day") ? nbt.getLong("Day") : -1L;
        on = nbt.getBoolean("On");
        int[] p = nbt.getIntArray("Pos");
        if (p.length == 5) {
            i = p[0];
            j = p[1];
            x = p[2];
            y = p[3];
            z = p[4];
        }
        startAt = nbt.getLong("Start");
        endAt = nbt.getLong("End");
        cacheDone = nbt.getBoolean("Cache");
        extra = nbt.getCompoundTag("Extra");
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        nbt.setLong("Day", rolledDay);
        nbt.setBoolean("On", on);
        nbt.setIntArray("Pos", new int[]{i, j, x, y, z});
        nbt.setLong("Start", startAt);
        nbt.setLong("End", endAt);
        nbt.setBoolean("Cache", cacheDone);
        nbt.setTag("Extra", extra);
    }
}
