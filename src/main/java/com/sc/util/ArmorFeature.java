package com.sc.util;

/**
 * Functions built into the suits, each switched on or off on its own (the armour settings screen,
 * GuiArmorSC). A function belongs to one piece (ItemArmor.armorType: 0 helmet, 1 chestplate,
 * 2 leggings, 3 boots) and comes with that suit tier and every tier above it. While it works it
 * costs EU from its own piece and heats the suit (chestplate heat, §16).
 * Ordinals are the bits of the piece's "FnToggled" NBT (switched away from the default) - only ever append.
 *
 * TODO(not in the design doc): the whole list and all the numbers are this mod's own.
 */
public enum ArmorFeature {

    // helmet
    NIGHT_VISION(0, ArmorSuit.NANO, 40, 1, true),
    HUD(0, ArmorSuit.NANO, 0, 0, true),
    AIR(0, ArmorSuit.QUANTUM, 40, 1, true),         // breathing under water
    CLEANSE(0, ArmorSuit.QUANTUM, 0, 0, true),      // poison, wither, hunger, nausea, blindness removed (EU per effect)
    ORE_SCANNER(0, ArmorSuit.QUANTUM, 60, 2, false), // this mod's ores (Exo: every ore) outlined through the ground
    SOLAR(0, ArmorSuit.EXO, 0, 0, true),            // open sky charges the whole suit, at night at half rate
    THERMAL(0, ArmorSuit.EXO, 60, 2, false),        // living things outlined through walls
    // chestplate
    CHARGER(1, ArmorSuit.NANO, 0, 1, true),         // charges the mod's weapons in the inventory
    SHIELD(1, ArmorSuit.QUANTUM, 0, 1, true),       // turns arrows and fireballs back (EU per projectile)
    FLIGHT(1, ArmorSuit.EXO, 120, 3, true),         // EU per second while actually flying
    FIRE_PROOF(1, ArmorSuit.EXO, 40, 1, true),      // fire and lava don't hurt (EU per second while burning)
    // leggings
    STEP_ASSIST(2, ArmorSuit.NANO, 0, 0, true),
    SPEED(2, ArmorSuit.QUANTUM, 40, 1, true),       // EU per second while sprinting
    DASH(2, ArmorSuit.EXO, 0, 2, true),             // a burst forward on the dash key (EU per dash)
    // boots
    FALL_DAMPING(3, ArmorSuit.NANO, 0, 0, true),    // Nano takes half the fall, Quantum a quarter, Exo none (EU per point)
    JUMP(3, ArmorSuit.QUANTUM, 20, 1, true),
    WATER_WALK(3, ArmorSuit.EXO, 40, 1, true),      // walking on water and lava (EU per second while on it)
    // chestplate, added later (ordinals are saved - appended)
    ANNIHILATION(1, ArmorSuit.EXO, 0, 40, true),    // on its key only: every hostile mob within 7 blocks dies; 95% of the suit's energy
    REGENERATION(1, ArmorSuit.NANO, 200, 2, false), // combat mode only: heals every second; the whole suit costs 3x combat meanwhile
    EXPLOSION_PROOF(1, ArmorSuit.EXO, 0, 2, true);  // full Exo set only: explosions neither hurt nor throw (EU per explosion)

    public final int piece;
    public final ArmorSuit minSuit;
    /** EU per second while the function is doing something (0: priced per use instead). */
    public final int euPerSecond;
    /** Heat per second added to the suit while it works. */
    public final int heat;
    /** On in a fresh piece (the see-through-walls ones start off). */
    public final boolean onByDefault;

    public static final int DASH_COST = 2000, SHIELD_COST = 60, CLEANSE_COST = 400, FALL_COST_PER_POINT = 400;
    /**
     * EU a sunlit Exo helmet gives the suit per second (half of it at night; a Power chip adds a
     * quarter per tier). Sized to the Exo suit: 4 x 4M EU from empty in about two Minecraft days
     * (10 min day x 8000 + 10 min night x 4000 = 7.2M a day) - well over what the functions use
     * (flight 120 EU/s), well under a charger (EV MFSU ~40 000 EU/s, the suit in under 7 minutes).
     */
    public static final int SOLAR_PER_SECOND = 8000;
    /** Chestplate EU spent on one food point by the Exo set (it never needs to eat). */
    public static final int FOOD_POINT_COST = 1500;
    /** Annihilation pulse: reach, the share of every worn piece's charge it burns, the charge each of the four pieces needs for it. */
    public static final double ANNIHILATION_RADIUS = 7.0;
    public static final double ANNIHILATION_DRAIN = 0.95, ANNIHILATION_MIN_CHARGE = 0.95;
    /** Annihilation pulse: what a boss (dragon, wither) takes instead of dying on the spot - a heavy but normal blow. */
    public static final float ANNIHILATION_BOSS_DAMAGE = 100F;
    /** Regeneration (combat mode): the suit's energy use multiplied while it's on; HP healed per second by Nano / Quantum / Exo. */
    public static final float REGEN_COST_MUL = 3F;
    public static final float[] REGEN_HEAL = {0.5F, 1F, 2F};
    /**
     * Full Exo set: with the energy shield on nothing gets through (but the void) - each point of
     * damage stopped costs this much from the chestplate (x power mode / regeneration); explosion
     * proofing costs a flat amount per explosion.
     */
    public static final int EXO_SHIELD_EU_PER_POINT = 1000, EXPLOSION_PROOF_COST = 5000;

    /** Fired by its key (once, then off again) instead of switched on and off. */
    public boolean isAction() {
        return this == DASH || this == ANNIHILATION;
    }

    ArmorFeature(int piece, ArmorSuit minSuit, int euPerSecond, int heat, boolean onByDefault) {
        this.piece = piece;
        this.minSuit = minSuit;
        this.euPerSecond = euPerSecond;
        this.heat = heat;
        this.onByDefault = onByDefault;
    }

    /** Whether a piece of that suit and type has this function. */
    public boolean availableIn(ArmorSuit suit, int armorType) {
        return armorType == piece && suit.ordinal() >= minSuit.ordinal();
    }

    public static ArmorFeature of(int ordinal) {
        ArmorFeature[] v = values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal] : null;
    }
}
