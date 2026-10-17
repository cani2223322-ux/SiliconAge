package com.sc.finale;

import com.sc.util.SingularScheme;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.FakePlayer;

/**
 * Подложка Ф5 (docs/plan-substrate-tech-f5.md §9; proposal §10 «Модуль Литографов», «Косметика»): the cosmetics' and the
 * Lithographers' Module's numbers and their small decisions - the coordinator's, read by the field generator, the Singular
 * station (package P), the trades, the handbook and the self-test (package D).
 * <ul>
 * <li>the Lithographers' Module (ПО-20): in an upgrade slot of a player's Field Generator its upkeep is UPKEEP_SHARE of what it
 * was (rounded up - a working field never costs 0); one is enough, a second does nothing;</li>
 * <li>the dome preset «Бирюза Наладчиков» (TileEntityFieldGeneratorSC.PRESETS[24]) - TUNER_DOME_RGB;</li>
 * <li>the unlockable Singular schemes (ПТ-25): L «Литограф» (PAINT_LITHOGRAPHER - the Breakdown Warden's 25%), M «Наладчик»
 * (PAINT_TUNER - the Elder's deal); a player's unlocked ones are a mask (SingularScheme.unlockBit) in his persisted NBT
 * (SCHEMES_NBT - by UUID, survives death; a LAN friend has his own). The station's ◄ ► offers a locked one only to who unlocked
 * it (SingularStationMath.cycle with the mask).</li>
 * </ul>
 * Shared core: only the coordinator edits this file.
 */
public final class FinaleRulesSC {

    /** ПО-20: the upkeep with the Module (x0.5). TODO(balance) */
    public static final float UPKEEP_SHARE = 0.5F;
    /** §9.3: «Бирюза Наладчиков» - the Tuners' domes as they glow at night (brighter than the plain turquoise preset 11, 0x3CD2C8). */
    public static final int TUNER_DOME_RGB = 0x48F0DC;
    /** The paint schemes' item metas (ItemPaintSchemeSC, fixed - append only): «Литограф», «Наладчик». */
    public static final int PAINT_LITHOGRAPHER = 0, PAINT_TUNER = 1;
    public static final SingularScheme[] PAINT_SCHEMES = {SingularScheme.L, SingularScheme.M};
    public static final String[] PAINT_NAMES = {"lithographer", "tuner"};
    /** The player's persisted NBT: the unlocked schemes' mask (int). */
    public static final String SCHEMES_NBT = "scSchemes";

    private FinaleRulesSC() {
    }

    /** ПО-20 (pure): the upkeep `upkeep` a tick with the Module in or not (rounded up; 0 stays 0). */
    public static int upkeep(int upkeep, boolean module) {
        if (!module || upkeep <= 0) {
            return upkeep;
        }
        return Math.max(1, (int) Math.ceil(upkeep * (double) UPKEEP_SHARE));
    }

    /** The scheme a paint item of `meta` unlocks (null: an unknown meta). */
    public static SingularScheme schemeOf(int meta) {
        return meta >= 0 && meta < PAINT_SCHEMES.length ? PAINT_SCHEMES[meta] : null;
    }

    /** Server: the player's unlocked schemes' mask (0 for a FakePlayer / null). */
    public static int unlockedSchemes(EntityPlayer p) {
        if (p == null || p instanceof FakePlayer) {
            return 0;
        }
        return p.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).getInteger(SCHEMES_NBT);
    }

    /** Server (ItemPaintSchemeSC): `scheme` unlocked for the player. @return it was new (false: already, or not unlockable) */
    public static boolean unlock(EntityPlayer p, SingularScheme scheme) {
        if (p == null || p instanceof FakePlayer || scheme == null || !scheme.unlockable() || p.worldObj == null || p.worldObj.isRemote) {
            return false;
        }
        NBTTagCompound data = p.getEntityData();
        NBTTagCompound persisted = data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            data.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
        }
        int was = persisted.getInteger(SCHEMES_NBT);
        persisted.setInteger(SCHEMES_NBT, was | scheme.unlockBit());
        return (was & scheme.unlockBit()) == 0;
    }

    /** The scheme is open to a player of mask `unlocked` (pure). */
    public static boolean open(SingularScheme scheme, int unlocked) {
        return scheme != null && (!scheme.unlockable() || (unlocked & scheme.unlockBit()) != 0);
    }
}
