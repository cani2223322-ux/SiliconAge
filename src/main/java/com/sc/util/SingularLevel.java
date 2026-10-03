package com.sc.util;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * The Singular suit's levels (docs/plan-singular-armor.md §6), the simple hook of stage 2a: each
 * piece keeps its own level in NBT "SingLevel" (1..5, a piece without it is level 1); a Singular
 * function opens at the level in the plan's §3 table (requiredLevel). A player in creative mode
 * counts as level 5 - everything can be tried before the points / tasks of stage 3 exist.
 */
public final class SingularLevel {

    public static final String NBT = "SingLevel";
    public static final int MIN = 1, MAX = 5;

    private SingularLevel() {
    }

    /** The piece's level, 1..5 (no tag / not set: 1). */
    public static int levelOf(ItemStack piece) {
        if (piece == null || !piece.hasTagCompound() || !piece.getTagCompound().hasKey(NBT)) {
            return MIN;
        }
        return clamp(piece.getTagCompound().getInteger(NBT));
    }

    public static void setLevel(ItemStack piece, int level) {
        if (piece == null) {
            return;
        }
        if (!piece.hasTagCompound()) {
            piece.setTagCompound(new NBTTagCompound());
        }
        piece.getTagCompound().setInteger(NBT, clamp(level));
    }

    public static int clamp(int level) {
        return Math.max(MIN, Math.min(MAX, level));
    }

    /** The level a function opens at: the plan's §3 column; 1 for every function from before the Singular suit. */
    public static int requiredLevel(ArmorFeature f) {
        switch (f) {
            case GRAV_STRIKE: case EVENT_HORIZON: case PHASE_DASH:
                return 2;
            case HEAT_VENT:
                return 3;
            default:
                return 1;
        }
    }

    /** The level that counts for this player and piece: creative mode - always MAX. */
    public static int effectiveLevel(EntityPlayer p, ItemStack piece) {
        return p != null && p.capabilities.isCreativeMode ? MAX : levelOf(piece);
    }

    /** Pure: a function is open with a piece of `level` (creative: true). */
    public static boolean unlocked(ArmorFeature f, int level, boolean creative) {
        return creative || level >= requiredLevel(f);
    }

    /** The function is open in this worn piece (its own piece; not worn: by the level-1 rule). */
    public static boolean unlocked(EntityPlayer p, ArmorFeature f, ItemStack piece) {
        return unlocked(f, levelOf(piece), p != null && p.capabilities.isCreativeMode);
    }
}
