package com.sc.tileentity;

import com.sc.util.SingularStationMath;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * A running process of the Singular Service Station (modernisation, level transfer, sync or the
 * Б-1 conversion of Exo pieces): what
 * it costs, what has been drawn so far, how far it is, which slots it locked and who started it.
 * Saved with the station (NBT "SingProc"), so it survives a reload. The resources are drawn as the
 * progress grows (ПР4): progress = min over the resources of drawn / cost, so a missing resource
 * just stops it - nothing is lost.
 */
public class SingularProcessSC {

    public static final int KIND_MODERNISE = 0, KIND_TRANSFER = 1, KIND_SYNC = 2, KIND_CONVERT = 3;

    public int kind;
    /** Locked station slots: bits 0..3 the armour slots, bit 4 the donor (TileEntitySingularStationSC.DONOR_BIT). */
    public int mask;
    /** 0..1. */
    public double progress;
    /** Ticks at speed 1. */
    public int baseTicks;
    public final long[] cost = new long[SingularStationMath.RESOURCES], drawn = new long[SingularStationMath.RESOURCES];
    /** EU the consumed Singular core brought (counted in drawn[R_EU] too). */
    public long catalystEu;
    /** The pieces' levels when it started (slot 0..3; the donor's at 4) - checked again at the end. */
    public final int[] levels = new int[5];
    /** Sync: the level the lagging pieces go to. */
    public int target;
    /** Who pressed the button (name: effects / chat at the end). */
    public String starter = "";
    /** Resonance counted when it started (the EU discount). */
    public boolean resonance;
    /** Б-1: the materials taken out of the slots at the start (cores with their charge) - given back whole on «Отменить». */
    public final java.util.List<ItemStack> items = new java.util.ArrayList<ItemStack>();

    public boolean locks(int slot) {
        return slot >= 0 && slot < 31 && (mask & (1 << slot)) != 0;
    }

    public void writeToNBT(NBTTagCompound t) {
        t.setInteger("Kind", kind);
        t.setInteger("Mask", mask);
        t.setDouble("Progress", progress);
        t.setInteger("Base", baseTicks);
        for (int i = 0; i < SingularStationMath.RESOURCES; i++) {
            t.setLong("Cost" + i, cost[i]);
            t.setLong("Drawn" + i, drawn[i]);
        }
        t.setLong("Catalyst", catalystEu);
        t.setIntArray("Levels", levels);
        t.setInteger("Target", target);
        t.setString("Starter", starter == null ? "" : starter);
        t.setBoolean("Res", resonance);
        if (!items.isEmpty()) {
            NBTTagList list = new NBTTagList();
            for (ItemStack s : items) {
                if (s != null) {
                    list.appendTag(s.writeToNBT(new NBTTagCompound()));
                }
            }
            t.setTag("Items", list);
        }
    }

    public static SingularProcessSC readFromNBT(NBTTagCompound t) {
        if (t == null || !t.hasKey("Kind")) {
            return null;
        }
        SingularProcessSC p = new SingularProcessSC();
        p.kind = Math.max(KIND_MODERNISE, Math.min(KIND_CONVERT, t.getInteger("Kind")));
        p.mask = t.getInteger("Mask");
        p.progress = Math.max(0.0, Math.min(1.0, t.getDouble("Progress")));
        p.baseTicks = Math.max(1, t.getInteger("Base"));
        for (int i = 0; i < SingularStationMath.RESOURCES; i++) {
            p.cost[i] = Math.max(0L, t.getLong("Cost" + i));
            p.drawn[i] = Math.max(0L, Math.min(p.cost[i], t.getLong("Drawn" + i)));
        }
        p.catalystEu = Math.max(0L, Math.min(p.drawn[SingularStationMath.R_EU], t.getLong("Catalyst")));
        int[] lv = t.getIntArray("Levels");
        for (int i = 0; i < p.levels.length && i < lv.length; i++) {
            p.levels[i] = lv[i];
        }
        p.target = t.getInteger("Target");
        p.starter = t.getString("Starter");
        p.resonance = t.getBoolean("Res");
        NBTTagList list = t.getTagList("Items", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            ItemStack s = ItemStack.loadItemStackFromNBT(list.getCompoundTagAt(i));
            if (s != null) {
                p.items.add(s);
            }
        }
        return p;
    }
}
