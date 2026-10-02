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
    AIR(0, ArmorSuit.NANO, 40, 1, true),            // breathing on the helmet's oxygen: under water, inside a block, in space (Galacticraft)
    CLEANSE(0, ArmorSuit.QUANTUM, 0, 0, true),      // poison, wither, hunger, nausea, blindness removed (EU per effect)
    ORE_SCANNER(0, ArmorSuit.QUANTUM, 60, 2, false), // this mod's ores (Exo: every ore) outlined through the ground
    SOLAR(0, ArmorSuit.EXO, 0, 0, true),            // open sky charges the whole suit, at night at half rate
    THERMAL(0, ArmorSuit.EXO, 60, 2, false),        // living things outlined through walls
    // chestplate
    CHARGER(1, ArmorSuit.NANO, 0, 1, true),         // charges the mod's weapons in the inventory
    SHIELD(1, ArmorSuit.QUANTUM, 0, 1, true),       // turns arrows and fireballs back (EU per projectile)
    FLIGHT(1, ArmorSuit.QUANTUM, 120, 3, true),     // EU per second while actually flying; Quantum at half the speed
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
    EXPLOSION_PROOF(1, ArmorSuit.EXO, 0, 2, true),  // full Exo set only: explosions neither hurt nor throw (EU per explosion)
    SET_AURA(1, ArmorSuit.NANO, 0, 0, true),        // full set of one suit: sparks of its light colour around the wearer (looks only)
    RAD_SHIELD(1, ArmorSuit.QUANTUM, 0, 0, true),   // radiation stopped (Quantum 75%, Exo 100%): EU and heat only while irradiated
    // life support (docs/plan-armor-gases.md), appended: these run on their gas, never on EU alone
    BOOSTER(1, ArmorSuit.QUANTUM, 0, 1, true),      // hydrogen: flight x2 speed, a longer dash, an air jump, a soft landing
    SEARCHLIGHT(0, ArmorSuit.QUANTUM, 0, 0, false), // krypton: a moving light 12 blocks ahead
    FUSION_CELL(1, ArmorSuit.EXO, 0, 6, false);     // deuterium + helium: 256 EU/t into the suit, heats hard

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
    /**
     * Radiation shield: the share of the radiation it stops (Quantum / Exo; Economy mode 25 points
     * less), the EU a second for each level it stops (x the power mode), the heat a second for each
     * level stopped - Quantum at level 10 stops 7.5: 150 EU/s and 3 heat/s, under its 4/s cooling
     * alone but over it with flight on; Exo stops 10: 250 EU/s, 4 heat/s of its 8/s.
     */
    public static final int RAD_QUANTUM_PCT = 75, RAD_EXO_PCT = 100, RAD_ECO_PCT_LESS = 25;
    public static final int RAD_QUANTUM_EU = 20, RAD_EXO_EU = 25;
    public static final float RAD_HEAT_PER_LEVEL = 0.4F;

    /** Runs on its gas: needs no EU in its piece to be active (the gas is checked where it's spent). */
    public boolean gasPowered() {
        return this == BOOSTER || this == SEARCHLIGHT || this == FUSION_CELL;
    }

    // ------------------------------------------------------------------ the gas each function runs on (one table for the logic, the K screen and the handbook)

    /** How gasUse() is counted. */
    public static final char USE_SECOND = 's', USE_MINUTE = 'm', USE_ONCE = 'u', USE_COOLING = 'h', USE_RADIATION = 'r';

    /**
     * The gas this function needs in the worn suit (Quantum / Exo; a Nano suit only ever needs oxygen
     * to breathe) - without it the function doesn't switch on; null: runs on EU alone. Helium means
     * the loop at work (ArmorLogicSC.heliumReady). The boost / searchlight / fusion cell listed too.
     */
    public ArmorGasSC.Gas gas() {
        switch (this) {
            case FLIGHT: case DASH: case JUMP: case SPEED: case FALL_DAMPING: case BOOSTER:
                return ArmorGasSC.Gas.HYDROGEN;
            case SHIELD: case ANNIHILATION: case EXPLOSION_PROOF: case REGENERATION: case CHARGER:
                return ArmorGasSC.Gas.HELIUM;
            case AIR: case CLEANSE:
                return ArmorGasSC.Gas.OXYGEN;
            case NIGHT_VISION: case ORE_SCANNER: case THERMAL: case SEARCHLIGHT:
                return ArmorGasSC.Gas.KRYPTON;
            case FIRE_PROOF: case WATER_WALK:
                return ArmorGasSC.Gas.ARGON;
            case RAD_SHIELD:
                return ArmorGasSC.Gas.HEAVY_WATER;
            case FUSION_CELL:
                return ArmorGasSC.Gas.DEUTERIUM;
            default:
                return null;
        }
    }

    /** How the gas is spent while it works: USE_SECOND / USE_MINUTE (mB a second / minute), USE_ONCE (mB a use), USE_COOLING, USE_RADIATION. */
    public char gasUseKind() {
        switch (this) {
            case DASH: case JUMP: case FALL_DAMPING: case CLEANSE:
                return USE_ONCE;
            case NIGHT_VISION: case ORE_SCANNER: case THERMAL: case SEARCHLIGHT:
                return USE_MINUTE;
            case SHIELD: case ANNIHILATION: case EXPLOSION_PROOF: case REGENERATION: case CHARGER:
                return USE_COOLING;
            case RAD_SHIELD:
                return USE_RADIATION;
            default:
                return USE_SECOND;
        }
    }

    /** mB spent (per gasUseKind; 0 for the cooling / radiation kinds, spent by the loop / RadiationSC). */
    public float gasUse() {
        switch (this) {
            case FLIGHT: return ArmorGasSC.H2_FLIGHT_BASE_PER_SECOND;
            case BOOSTER: return ArmorGasSC.H2_FLIGHT_PER_SECOND;
            case DASH: return ArmorGasSC.H2_DASH;
            case JUMP: return ArmorGasSC.H2_JUMP;
            case SPEED: return ArmorGasSC.H2_SPEED_PER_SECOND;
            case FALL_DAMPING: return ArmorGasSC.H2_FALL_DAMPING_PER_POINT;   // per damage point absorbed (gasPerPoint)
            case AIR: return ArmorGasSC.OXYGEN_PER_SECOND;
            case CLEANSE: return ArmorGasSC.O2_CLEANSE;
            case NIGHT_VISION: case ORE_SCANNER: case THERMAL: case SEARCHLIGHT: return ArmorGasSC.KRYPTON_PER_MIN;
            case FIRE_PROOF: return ArmorGasSC.ARGON_FIRE_PROOF_PER_SECOND;
            case WATER_WALK: return ArmorGasSC.ARGON_WATER_WALK_PER_SECOND;
            case FUSION_CELL: return ArmorGasSC.FUSION_D_PER_TICK * 20;
            default: return 0F;
        }
    }

    /**
     * A USE_ONCE function whose gasUse() is per damage point it absorbs, not per use (the soft
     * landing: ArmorGasSC.fallDampingGas) - the texts say "mB per damage point" for it.
     */
    public boolean gasPerPoint() {
        return this == FALL_DAMPING;
    }

    /** The least of its gas the function needs in the suit to switch on (the dash: a whole dash's worth). */
    public int gasMin() {
        return this == DASH ? ArmorGasSC.H2_DASH : 1;
    }

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
