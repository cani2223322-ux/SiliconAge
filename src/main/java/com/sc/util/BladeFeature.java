package com.sc.util;

/**
 * Functions of the energy blades (ItemBladeSC), switched on / off and bound to keys in the armour
 * screen (K, the "Blade" tab) just like the suits' ArmorFeature. A function comes with its blade
 * tier and every tier above it. Ordinals are the bits of the blade's "FnToggled" NBT - only ever append.
 *
 * TODO(not in the design doc): the whole list and all the numbers are this mod's own.
 */
public enum BladeFeature {

    BLADE(BladeType.NANO, 1, false),          // the blade itself: on = full damage, EU per hit and per second
    CUTTING_EDGE(BladeType.NANO, 0, true),    // cobweb, leaves, wool, vines, grass cut at once, like shears
    ENERGY_BLOCK(BladeType.NANO, 1, true),    // blocking: damage halved once more, paid in EU
    ARMOR_PIERCE(BladeType.QUANTUM, 0, true), // hits ignore the target's armour
    DEFLECT(BladeType.QUANTUM, 1, true),      // blocking: arrows and fireballs turned back
    SWEEP(BladeType.QUANTUM, 5, true),        // on its key: every mob in an arc of 3 blocks in front
    EXECUTE(BladeType.EXO, 0, true),          // a hit mob left under a fifth of its health dies
    WAVE(BladeType.EXO, 10, true),            // on its key: a flying cut 16 blocks long through everything
    LUNGE(BladeType.EXO, 6, true),            // on its key: a leap 6 blocks forward hitting all on the way
    LOOTING(BladeType.NANO, 0, true);         // a real Looting enchantment on the blade: III / IV / V by tier (appended)

    public final BladeType minType;
    /** Heat per second while on (BLADE), per use (keys), per block / projectile (block, deflect). */
    public final int heat;
    public final boolean onByDefault;

    public static final int HIT_HEAT = 1;
    public static final int CUT_COST = 50, BLOCK_EU_PER_POINT = 100, DEFLECT_COST = 100;
    public static final int SWEEP_COST = 5000, WAVE_COST = 20000, LUNGE_COST = 10000;
    public static final double SWEEP_RANGE = 3, WAVE_RANGE = 16, LUNGE_RANGE = 6, DEFLECT_RANGE = 3;
    public static final float EXECUTE_SHARE = 0.2F;
    /** Quantum blade + full Quantum suit: EU a hostile mob killed by a normal hit gives back to the chestplate (less than the hit costs). */
    public static final int KILL_REFUND = 400;
    /** Ticks between two uses of a key function. */
    public static final int ACTION_COOLDOWN = 10;

    BladeFeature(BladeType minType, int heat, boolean onByDefault) {
        this.minType = minType;
        this.heat = heat;
        this.onByDefault = onByDefault;
    }

    /** Fired by its key (the switch is a safety catch) instead of switched on and off. */
    public boolean isAction() {
        return this == SWEEP || this == WAVE || this == LUNGE;
    }

    public boolean availableIn(BladeType type) {
        return type != null && type.ordinal() >= minType.ordinal();
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static BladeFeature of(int ordinal) {
        BladeFeature[] v = values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal] : null;
    }
}
