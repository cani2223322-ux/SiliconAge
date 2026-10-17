package com.sc.dim;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.FakePlayer;

/**
 * Подложка Ф5 (docs/plan-substrate-tech-f5.md §2.1, §10.3, rule ПВ-13; proposal §4 «Открытие мира»): the rule requireSignal - the
 * Substrate shows in a Space Bridge's list, and a bridge opens an end there, only for a player who has used a «Навигационная запись
 * Si-14» (ItemNavRecordSC, package P). Per player: a flag in the player's persisted NBT (EntityPlayer.PERSISTED_NBT_TAG survives
 * death; the player's own file - by UUID, so a LAN friend has his own). The server decides what each viewer sees
 * (TileEntityBridgeControllerSC.writeState filters the list it sends, plan() refuses) - a client needs no copy of the flag: every
 * screen's list comes from the server, per viewer. With the rule off (★) nothing changes: the world is everyone's, as in Ф1..Ф4.
 * <p>
 * A bridge standing in the Substrate itself always lists and reaches it (whoever is there got in somehow); a FakePlayer (a mod's
 * machine) never has the record. Shared core: only the coordinator edits this file.
 */
public final class SubstrateSignalSC {

    /** The persisted NBT flag: this player has used a Navigation Record (boolean). */
    public static final String NBT_KNOWN = "scSi14Nav";

    private SubstrateSignalSC() {
    }

    /** ПВ-13 (pure): the Substrate is open to a viewer - the rule off, or the record used, or the bridge itself in the Substrate. */
    public static boolean visible(boolean requireSignal, boolean hasRecord, boolean bridgeInSubstrate) {
        return !requireSignal || hasRecord || bridgeInSubstrate;
    }

    /** Server: the player has used a Navigation Record (a FakePlayer, null: never). */
    public static boolean knows(EntityPlayer p) {
        if (p == null || p instanceof FakePlayer) {
            return false;
        }
        return p.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).getBoolean(NBT_KNOWN);
    }

    /** Server (ItemNavRecordSC): the record used - the Substrate is open to this player from now on. @return it was new */
    public static boolean grant(EntityPlayer p) {
        if (p == null || p instanceof FakePlayer || p.worldObj == null || p.worldObj.isRemote) {
            return false;
        }
        NBTTagCompound data = p.getEntityData();
        NBTTagCompound persisted = data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            data.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
        }
        boolean was = persisted.getBoolean(NBT_KNOWN);
        persisted.setBoolean(NBT_KNOWN, true);
        return !was;
    }

    /** Server: the Substrate is open to `viewer` at a bridge in dimension `bridgeDim` (null viewer: as a player without the record). */
    public static boolean openTo(EntityPlayer viewer, int bridgeDim) {
        return visible(SubstrateConfigSC.requireSignal, knows(viewer), SubstrateSC.isDim(bridgeDim));
    }

    /**
     * ПВ-13, server: a bridge in `bridgeDim` planning an end in `toDim` for `p` - refused (the Substrate not open to the player).
     * Only an end in the Substrate is ever refused here.
     */
    public static boolean refuses(EntityPlayer p, int toDim, int bridgeDim) {
        return SubstrateSC.isDim(toDim) && !openTo(p, bridgeDim);
    }

    /**
     * ПВ-13, server: the bridge's dimension list (compounds with "id") for `viewer` - the Substrate's entry taken out when it isn't
     * open to them; the list itself is left alone (a copy is returned only when something goes).
     */
    public static NBTTagList filterDims(NBTTagList dims, EntityPlayer viewer, int bridgeDim) {
        if (dims == null || openTo(viewer, bridgeDim)) {
            return dims;
        }
        NBTTagList out = new NBTTagList();
        for (int i = 0; i < dims.tagCount(); i++) {
            NBTTagCompound d = dims.getCompoundTagAt(i);
            if (!SubstrateSC.isDim(d.getInteger("id"))) {
                out.appendTag(d.copy());
            }
        }
        return out;
    }
}
