package com.sc.util;

/**
 * Functions of the drills (ItemDrillSC), switched on / off and bound to keys in the armour screen
 * (K, the "Drill" tab) like the suits' and blades' functions. A function comes with its drill tier
 * and every tier above it (the Exo drill has all of them). Ordinals are the bits of the drill's
 * "FnToggled" NBT - only ever append.
 *
 * TODO(not in the design doc): the whole list and all the numbers are this mod's own.
 */
public enum DrillFeature {

    ECO(DrillType.NANO, false),         // half the EU per block, half the speed
    MAGNET(DrillType.NANO, true),       // what it digs goes straight into the inventory
    TORCH(DrillType.NANO, true),        // right-click places a torch from the inventory
    AREA_3X3(DrillType.QUANTUM, false), // a 3x3 face at once
    SILK(DrillType.QUANTUM, false),     // silk touch (fortune is skipped while it's on)
    FORTUNE(DrillType.QUANTUM, true),   // fortune III (Exo: V)
    VEIN(DrillType.QUANTUM, false),     // one ore block takes the whole vein (up to VEIN_MAX)
    AREA_5X5(DrillType.EXO, false),     // a 5x5 face at once (wins over 3x3)
    TUNNEL(DrillType.EXO, false),       // 3x3 and TUNNEL_DEPTH blocks deep
    AUTOSMELT(DrillType.EXO, false),    // drops come out smelted (furnace recipes)
    LASER(DrillType.EXO, true),         // on its key: a beam cuts LASER_RANGE blocks ahead
    LINK(DrillType.EXO, false);         // drops go to the linked chest (sneak + right-click on it to link)

    public final DrillType minType;
    public final boolean onByDefault;

    public static final int BLOCK_HEAT = 1, LASER_HEAT = 5;
    public static final int VEIN_MAX = 32, TUNNEL_DEPTH = 5, LASER_RANGE = 8;
    /** Ticks between two laser shots. */
    public static final int LASER_COOLDOWN = 10;

    DrillFeature(DrillType minType, boolean onByDefault) {
        this.minType = minType;
        this.onByDefault = onByDefault;
    }

    /** Fired by its key (the switch is a safety catch) instead of switched on and off. */
    public boolean isAction() {
        return this == LASER;
    }

    public boolean availableIn(DrillType type) {
        return type != null && type.ordinal() >= minType.ordinal();
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static DrillFeature of(int ordinal) {
        DrillFeature[] v = values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal] : null;
    }
}
