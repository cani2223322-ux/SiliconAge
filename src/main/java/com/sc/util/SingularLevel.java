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
            case ANALYZER:
                return 2;
            case HEAT_VENT: case GRAV_PRESS: case GRAV_GRAB: case GRAV_SCANNER: case THREAT_SENSE:
                return 3;
            case TIME_SLOW: case RESONANCE:
                return 4;
            case BLACK_HOLE: case GRAV_DOME: case SINGULARITY:
                return 5;
            default:
                return 1;
        }
    }

    // ------------------------------------------------------------------ the branches (Р2): the hook for stage 3

    /** No branch / both sides of a branch allowed; the first and the second alternative of a branch. */
    public static final int BRANCH_BOTH = 0, BRANCH_A = 1, BRANCH_B = 2;

    /**
     * The branch the piece has chosen at `level` (Р2: level 3 - the press or the grab, level 5 - the
     * black hole or the dome). Stage 2b: BRANCH_BOTH always - both alternatives work; stage 3 keeps
     * the choice in the piece's NBT (changed in the Singular station for 100 mB of singular matter).
     */
    public static int branchChoice(ItemStack piece, int level) {
        return BRANCH_BOTH;
    }

    /** Which side of a branch a function is (BRANCH_A / BRANCH_B), BRANCH_BOTH when it's in no branch. */
    public static int branchOf(ArmorFeature f) {
        switch (f) {
            case GRAV_PRESS: case BLACK_HOLE:
                return BRANCH_A;
            case GRAV_GRAB: case GRAV_DOME:
                return BRANCH_B;
            default:
                return BRANCH_BOTH;
        }
    }

    /** Pure: a function of side `side` is allowed with the choice `choice`. */
    public static boolean branchAllows(int side, int choice) {
        return side == BRANCH_BOTH || choice == BRANCH_BOTH || side == choice;
    }

    /** The piece's branch choice lets the function work (stage 2b: always). */
    public static boolean branchAllowed(ArmorFeature f, ItemStack piece) {
        int side = branchOf(f);
        return side == BRANCH_BOTH || branchAllows(side, branchChoice(piece, requiredLevel(f)));
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
