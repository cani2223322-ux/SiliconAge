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
    LINK(DrillType.EXO, false),         // drops go to the linked chest (sneak + right-click on it to link)
    // ---- Singular (docs/plan-singular-tools.md §3, appended): the drill's level opens them (DrillLogicSC.unlocked) ----
    GRAV_FUNNEL(DrillType.SINGULAR, false, 2, ArmorGasSC.Gas.ARGON, 1, 0),           // the area face 7x7 (lv 4: 9x9; Miner lv 5: 11x11), Ar 1 mB a block
    CROSS_LINK(DrillType.SINGULAR, false, 1, ArmorGasSC.Gas.SINGULAR_MATTER, 1, 0),  // the linked chest in any dimension (loaded), SM 1 mB a stack
    DRAIN(DrillType.SINGULAR, false, 1, null, 0, 0),                                   // water / lava in and around the dug zone removed
    REPLACE(DrillType.SINGULAR, false, 2, null, 0, 0),                                 // the block in the hotbar slot right of the drill goes where one was dug
    PHASE_DIG(DrillType.SINGULAR, true, 3, ArmorGasSC.Gas.SINGULAR_MATTER, 2, 10),   // on its key: the ore looked at through up to 8 blocks of rock
    BLACK_HOLE(DrillType.SINGULAR, false, 1, ArmorGasSC.Gas.SINGULAR_MATTER, 1, 20); // the mode (NBT "SingHole"): the zone destroyed, SM 1 mB per 25 blocks, 12x12: 1 s

    public final DrillType minType;
    public final boolean onByDefault;
    /** Singular functions: the level of the Singular drill that opens it, its gas (null: none), mB per use / block / stack, base cooldown. */
    private final int singLevel, gasMb, cooldownTicks;
    private final ArmorGasSC.Gas gas;

    public static final int BLOCK_HEAT = 1, LASER_HEAT = 5;
    public static final int VEIN_MAX = 32, TUNNEL_DEPTH = 5, LASER_RANGE = 8;
    /** Ticks between two laser shots. */
    public static final int LASER_COOLDOWN = 10;

    // ---- Singular numbers (docs/plan-singular-tools.md §3; TODO(balance)) ----
    /** Gravitational funnel: the face's half-size at level 2 / 4 / Miner level 5 (7x7, 9x9, 11x11). */
    public static final int FUNNEL_RADIUS = 3, FUNNEL_RADIUS_4 = 4, FUNNEL_RADIUS_MINER = 5;
    /** Phase dig: reach through rock (Prospector level 3: PHASE_RANGE_PROSPECTOR). */
    public static final int PHASE_RANGE = 8, PHASE_RANGE_PROSPECTOR = 12;
    /** Prospector level 5: the vein's size. */
    public static final int VEIN_MAX_PROSPECTOR = 256;
    /** Black hole: blocks per mB of singular matter; EU per block = euPerBlock x HOLE_EU_MUL; heat per block. */
    public static final int HOLE_BLOCKS_PER_MB = 25;
    public static final float HOLE_EU_MUL = 0.5F, HOLE_HEAT_PER_BLOCK = 1.5F;
    /** Drain: liquid blocks one dig may remove at most. */
    public static final int DRAIN_MAX = 512;
    /** Level points: one per this many blocks dug by the Singular drill. */
    public static final int BLOCKS_PER_POINT = 16;
    /** Miner level 3: dig speed x this. */
    public static final float MINER_SPEED = 1.25F;

    DrillFeature(DrillType minType, boolean onByDefault) {
        this(minType, onByDefault, 1, null, 0, 0);
    }

    DrillFeature(DrillType minType, boolean onByDefault, int singLevel, ArmorGasSC.Gas gas, int gasMb, int cooldownTicks) {
        this.minType = minType;
        this.onByDefault = onByDefault;
        this.singLevel = singLevel;
        this.gas = gas;
        this.gasMb = gasMb;
        this.cooldownTicks = cooldownTicks;
    }

    /** Fired by its key (the switch is a safety catch) instead of switched on and off. */
    public boolean isAction() {
        return this == LASER || this == PHASE_DIG;
    }

    /** One of the Singular drill's own functions (needs its level; its gas comes from the worn armour). */
    public boolean singular() {
        return minType == DrillType.SINGULAR;
    }

    /** The Singular drill's level that opens it (older functions: 1). */
    public int singLevel() {
        return singLevel;
    }

    /** The ToolLevelSC branch it needs (none of the drill's functions: the branches only improve them). */
    public int branch() {
        return ToolLevelSC.BRANCH_NONE;
    }

    /** Its gas from the worn armour, or null. */
    public ArmorGasSC.Gas gas() {
        return gas;
    }

    /** mB per use - the funnel: per block beyond the first, the cross link: per stack, the black hole: per HOLE_BLOCKS_PER_MB blocks. */
    public int gasMb() {
        return gasMb;
    }

    public boolean gasPerSecond() {
        return false;
    }

    /** Base cooldown (ticks) of a key function; the black hole's applies to the 12x12 zone only. Cooldown key = key(). */
    public int cooldownTicks() {
        return cooldownTicks;
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
