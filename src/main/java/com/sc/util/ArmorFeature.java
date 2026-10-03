package com.sc.util;

/**
 * Functions built into the suits, each switched on or off on its own (the armour settings screen,
 * GuiArmorSC). A function belongs to one piece (ItemArmor.armorType: 0 helmet, 1 chestplate,
 * 2 leggings, 3 boots) and comes with that suit tier and every tier above it. While it works it
 * costs EU from its own piece and heats the suit (chestplate heat, §16).
 * Ordinals are the bits of the piece's "FnToggled" NBT (switched away from the default; 32 and up in "FnToggled2") - only ever append.
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
    FUSION_CELL(1, ArmorSuit.EXO, 0, 6, false),     // deuterium + helium: 256 EU/t into the suit, heats hard
    // the Singular suit's own functions (docs/plan-singular-armor.md §3, stage 2a), appended: Singular only,
    // each opens at its level (SingularLevel.requiredLevel)
    GRAV_FLIGHT(1, ArmorSuit.SINGULAR, 2000, 3, true),   // Н1: flight x3 the Exo speed, no inertia, hovering; helium instead of hydrogen
    MAGNET(1, ArmorSuit.SINGULAR, 20, 0, false),        // Н7: items and experience within 8 blocks fly to the wearer
    GRAV_ANCHOR(2, ArmorSuit.SINGULAR, 0, 0, true),     // П3: no knockback, explosions don't push (heavy water per push)
    STABILIZER(2, ArmorSuit.SINGULAR, 0, 0, true),      // П7: cobwebs and soul sand don't slow (argon while in them)
    ANTIGRAV(3, ArmorSuit.SINGULAR, 100, 0, true),      // Б3: slow fall (sneak: fall freely) and a jump in mid-air
    VOID_RESCUE(3, ArmorSuit.SINGULAR, 0, 0, true),     // Б4: fallen into the void - back to the last safe place
    GRAV_STRIKE(3, ArmorSuit.SINGULAR, 0, 0, true),     // Б1: the fall the boots absorb hits the mobs around
    WITHER_VOID(1, ArmorSuit.SINGULAR, 0, 0, true),     // К9: full set - wither taken off, the void hurts half
    CLEAR_SIGHT(0, ArmorSuit.SINGULAR, 40, 1, false),   // Ш8: night vision that goes out in bright light (no glare)
    HEAT_VENT(1, ArmorSuit.SINGULAR, 0, 0, true),       // Н11: at 100% heat a wave throws mobs back and half the heat goes
    EVENT_HORIZON(1, ArmorSuit.SINGULAR, 0, 2, true),   // Н2: projectiles swallowed, 30% of other damage into EU (replaces the shield)
    PHASE_DASH(2, ArmorSuit.SINGULAR, 0, 10, true),     // П1: on its key - a jump through space up to 16 blocks along the look
    // stage 2b (docs/plan-singular-armor.md §3), appended; heat: per use for the key ones, per second for the rest
    GRAV_PRESS(1, ArmorSuit.SINGULAR, 0, 60, true),     // Н10: on its key - mobs within 6 blocks pinned to the ground 5 s (branch Р2, lvl 3)
    GRAV_GRAB(1, ArmorSuit.SINGULAR, 0, 0, true),       // Н8: on its key - a mob held in front of the wearer, then thrown (branch Р2, lvl 3)
    TIME_SLOW(1, ArmorSuit.SINGULAR, 0, 200, true),     // Н4: on its key - 6 s mobs and projectiles within 16 blocks at x0.2
    BLACK_HOLE(1, ArmorSuit.SINGULAR, 0, 300, true),    // Н3: on its key - 10 s a point along the look pulls mobs and items, then collapses (Р2, lvl 5)
    GRAV_DOME(1, ArmorSuit.SINGULAR, 0, 0, true),       // Н17: on its key - 8 s a dome of radius 5 keeps mobs and projectiles out (Р2, lvl 5)
    SINGULARITY(1, ArmorSuit.SINGULAR, 0, 100, true),   // К1: full set, on its key - 15 s the Singular functions x2, then 60 s weakened
    RESONANCE(1, ArmorSuit.SINGULAR, 0, 0, true),       // К2: full set - a running Singular reactor / field generator near charges, cools, refills
    GRAV_SCANNER(0, ArmorSuit.SINGULAR, 200, 1, false), // Ш1: every 5 s chests, spawners, ores and mobs within 32 blocks outlined
    THREAT_SENSE(0, ArmorSuit.SINGULAR, 20, 0, false),  // Ш2: mobs that target the wearer outlined red, arrows at the screen's edge
    ANALYZER(0, ArmorSuit.SINGULAR, 0, 0, true);        // Ш5: the mob / machine looked at - a small table on the HUD

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
    /** Regeneration (combat mode): the suit's energy use multiplied while it's on; HP healed per second by Nano / Quantum / Exo / Singular. */
    public static final float REGEN_COST_MUL = 3F;
    public static final float[] REGEN_HEAL = {0.5F, 1F, 2F, 3F};

    /** HP regeneration heals a second with a chestplate of this suit. */
    public static float regenHeal(ArmorSuit suit) {
        return suit == null ? 0F : REGEN_HEAL[Math.min(REGEN_HEAL.length - 1, suit.ordinal())];
    }
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

    // ------------------------------------------------------------------ the Singular functions' numbers (docs/plan-singular-armor.md §4, draft)

    /** Н1 flight: helium a second, the speed against the Exo flight. Н7 magnet: helium a minute, its reach. */
    public static final float SING_HE_FLIGHT_PER_SECOND = 1F, SING_HE_MAGNET_PER_MIN = 1F;
    public static final float GRAV_FLIGHT_SPEED_MUL = 3F;
    public static final double MAGNET_RADIUS = 8.0;
    /** Н7: an item the wearer threw is left alone this many ticks (so things can be dropped). */
    public static final int MAGNET_THROWN_GRACE = 20;
    /** П3 heavy water per push stopped; П7 argon a second while slowed; Б3 hydrogen a second while falling slowly. */
    public static final float SING_D2O_ANCHOR = 1F, SING_AR_STABILIZER_PER_SECOND = 0.5F, SING_H2_SLOWFALL_PER_SECOND = 0.5F;
    /** Б3: the fastest fall (motionY) while it holds the wearer; its air jump - hydrogen and EU. */
    public static final double ANTIGRAV_FALL_SPEED = -0.12;
    public static final int SING_H2_AIR_JUMP = 10, ANTIGRAV_AIR_JUMP_EU = 5000;
    /** Б4: helium, the cooldown (ticks), heat, the share of the suit's charge. */
    public static final int SING_HE_VOID_RESCUE = 500, VOID_RESCUE_COOLDOWN = 5 * 60 * 20, VOID_RESCUE_HEAT = 50;
    public static final float VOID_RESCUE_CHARGE = 0.10F;
    /** Б1: hydrogen and heat per damage point turned into the wave, its reach, the damage per point. */
    public static final float SING_H2_STRIKE_PER_POINT = 1F, STRIKE_DAMAGE_PER_POINT = 1F;
    public static final double STRIKE_RADIUS = 4.0;
    public static final int STRIKE_HEAT_PER_POINT = 1;
    /** К9: oxygen per wither effect taken off; the void's damage multiplied. */
    public static final float SING_O2_WITHER = 2F, VOID_DAMAGE_MUL = 0.5F;
    /** Ш8: krypton a minute; the light (0..15) at the eyes from which the night vision goes out. */
    public static final float SING_KR_SIGHT_PER_MIN = 1F;
    public static final int CLEAR_SIGHT_BRIGHT = 12;
    /** Н11: argon, the cooldown (ticks), the wave's reach, the share of heat thrown out. */
    public static final int SING_AR_HEAT_VENT = 200, HEAT_VENT_COOLDOWN = 60 * 20;
    public static final double HEAT_VENT_RADIUS = 5.0;
    public static final float HEAT_VENT_SHARE = 0.5F;
    /** Н2: helium per projectile swallowed (heat: EVENT_HORIZON.heat), the share of other damage turned into EU, EU and helium per point. */
    public static final int SING_HE_PER_PROJECTILE = 5, HORIZON_EU_PER_POINT = 8000;
    public static final float HORIZON_SHARE = 0.3F, SING_HE_PER_DAMAGE_POINT = 1F;
    /** П1: hydrogen, EU, the reach (blocks), the cooldown (ticks); heat: PHASE_DASH.heat. */
    public static final int SING_H2_PHASE = 50, PHASE_DASH_EU = 50000, PHASE_DASH_COOLDOWN = 3 * 20;
    public static final double PHASE_DASH_RANGE = 16.0;
    /** О2: under this share of its piece's charge flight, the event horizon and the anchor switch off. */
    public static final float SING_LOW_CHARGE = 0.10F;

    // ------------------------------------------------------------------ stage 2b (docs/plan-singular-armor.md §3-§5, draft)

    /** Н10 press: helium, deuterium, EU, the cooldown (ticks), how long the mobs are held (ticks), the reach. */
    public static final int SING_HE_PRESS = 100, SING_D_PRESS = 50, PRESS_EU = 200000, PRESS_COOLDOWN = 30 * 20, PRESS_TICKS = 5 * 20;
    public static final double PRESS_RADIUS = 6.0;
    /** Н8 grab: helium, EU, the cooldown (from the throw), the longest hold; the reach, how far in front it's held, the throw's speed. */
    public static final int SING_HE_GRAB = 50, GRAB_EU = 100000, GRAB_COOLDOWN = 10 * 20, GRAB_TICKS = 6 * 20;
    public static final double GRAB_RANGE = 8.0, GRAB_HOLD = 3.0, GRAB_THROW_SPEED = 2.2;
    /** Н4 time slowing: krypton, helium, singular matter, cooldown, how long; the suit share, the speed factor; the reach. */
    public static final int SING_KR_SLOW = 100, SING_HE_SLOW = 500, SING_SM_SLOW = 50, SLOW_COOLDOWN = 3 * 60 * 20, SLOW_TICKS = 6 * 20;
    public static final float SLOW_CHARGE = 0.10F, SLOW_FACTOR = 0.2F;
    public static final double SLOW_RADIUS = 16.0;
    /** О3: the time slowing and the black hole set the hostile mobs within this many blocks on the wearer. */
    public static final double AGGRO_RADIUS = 48.0;
    /** Н3 black hole: deuterium, helium, singular matter, cooldown, how long; the suit share, damage; the point's reach, the pull, the collapse. */
    public static final int SING_D_HOLE = 500, SING_HE_HOLE = 1000, SING_SM_HOLE = 100, HOLE_COOLDOWN = 2 * 60 * 20, HOLE_TICKS = 10 * 20;
    public static final float HOLE_CHARGE = 0.25F, HOLE_DAMAGE = 6F, HOLE_COLLAPSE_DAMAGE = 20F;
    public static final double HOLE_RANGE = 24.0, HOLE_RADIUS = 10.0, HOLE_COLLAPSE_RADIUS = 4.0;
    /** Н17 dome: helium, deuterium, cooldown, how long; the suit share; the radius. */
    public static final int SING_HE_DOME = 300, SING_D_DOME = 200, DOME_COOLDOWN = 2 * 60 * 20, DOME_TICKS = 8 * 20;
    public static final float DOME_CHARGE = 0.10F;
    public static final double DOME_RADIUS = 5.0;
    /** К1 "Singularity": singular matter, deuterium, cooldown, the boost and the weakness after it (ticks); the suit share, the multipliers. */
    public static final int SING_SM_BOOST = 200, SING_D_BOOST = 1000, BOOST_COOLDOWN = 10 * 60 * 20, BOOST_TICKS = 15 * 20, WEAK_TICKS = 60 * 20;
    public static final float BOOST_CHARGE = 0.20F, BOOST_MUL = 2F, WEAK_MUL = 0.5F;
    /** К2 resonance: the reach, EU a second into the suit, heat a second taken off, seconds between scans; helium / deuterium a second. */
    public static final int RESONANCE_RADIUS = 16, RESONANCE_EU = 20000, RESONANCE_COOL = 10, RESONANCE_RESCAN = 5;
    public static final float RES_HE_PER_SECOND = 2F, RES_D_PER_SECOND = 0.5F;
    /** Ш1 scanner: krypton a minute; a pulse's krypton, the reach, ticks between pulses, how long outlines stay, the most blocks / mobs shown. */
    public static final float SING_KR_SCANNER_PER_MIN = 2F;
    public static final int SING_KR_PER_PULSE = 10, SCANNER_RADIUS = 32, SCANNER_EVERY = 5 * 20, SCANNER_SHOW = 5 * 20,
            SCANNER_MAX_BLOCKS = 256, SCANNER_MAX_MOBS = 64;
    /** Ш2 threat sense: krypton a minute; the reach, the most mobs sent. */
    public static final float SING_KR_THREAT_PER_MIN = 0.5F;
    public static final int THREAT_RANGE = 32, THREAT_MAX = 32;
    /** Ш5 analyzer: krypton and EU per new target; the reach to a mob / to a machine. */
    public static final int SING_KR_ANALYZE = 1, ANALYZE_EU = 1000;
    public static final double ANALYZE_MOB_RANGE = 16.0, ANALYZE_BLOCK_RANGE = 8.0;

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
            case GRAV_FLIGHT: case MAGNET: case VOID_RESCUE: case EVENT_HORIZON:
                return ArmorGasSC.Gas.HELIUM;
            case GRAV_ANCHOR:
                return ArmorGasSC.Gas.HEAVY_WATER;
            case STABILIZER: case HEAT_VENT:
                return ArmorGasSC.Gas.ARGON;
            case ANTIGRAV: case GRAV_STRIKE: case PHASE_DASH:
                return ArmorGasSC.Gas.HYDROGEN;
            case WITHER_VOID:
                return ArmorGasSC.Gas.OXYGEN;
            case CLEAR_SIGHT: case GRAV_SCANNER: case THREAT_SENSE: case ANALYZER:
                return ArmorGasSC.Gas.KRYPTON;
            case GRAV_PRESS: case GRAV_GRAB: case TIME_SLOW: case BLACK_HOLE: case GRAV_DOME:
                return ArmorGasSC.Gas.HELIUM;    // the main one; the other gases they need are checked at the key (SingularPowersSC)
            case SINGULARITY:
                return ArmorGasSC.Gas.SINGULAR_MATTER;
            default:
                return null;
        }
    }

    /** How the gas is spent while it works: USE_SECOND / USE_MINUTE (mB a second / minute), USE_ONCE (mB a use), USE_COOLING, USE_RADIATION. */
    public char gasUseKind() {
        switch (this) {
            case DASH: case JUMP: case FALL_DAMPING: case CLEANSE:
            case GRAV_ANCHOR: case VOID_RESCUE: case GRAV_STRIKE: case WITHER_VOID: case HEAT_VENT: case EVENT_HORIZON: case PHASE_DASH:
            case GRAV_PRESS: case GRAV_GRAB: case TIME_SLOW: case BLACK_HOLE: case GRAV_DOME: case SINGULARITY: case ANALYZER:
                return USE_ONCE;
            case NIGHT_VISION: case ORE_SCANNER: case THERMAL: case SEARCHLIGHT: case MAGNET: case CLEAR_SIGHT:
            case GRAV_SCANNER: case THREAT_SENSE:
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
            // the Singular functions: the plan's own numbers (no K10 discount - that's for the Exo legacy)
            case GRAV_FLIGHT: return SING_HE_FLIGHT_PER_SECOND;
            case MAGNET: return SING_HE_MAGNET_PER_MIN;
            case GRAV_ANCHOR: return SING_D2O_ANCHOR;
            case STABILIZER: return SING_AR_STABILIZER_PER_SECOND;
            case ANTIGRAV: return SING_H2_SLOWFALL_PER_SECOND;
            case VOID_RESCUE: return SING_HE_VOID_RESCUE;
            case GRAV_STRIKE: return SING_H2_STRIKE_PER_POINT;
            case WITHER_VOID: return SING_O2_WITHER;
            case CLEAR_SIGHT: return SING_KR_SIGHT_PER_MIN;
            case HEAT_VENT: return SING_AR_HEAT_VENT;
            case EVENT_HORIZON: return SING_HE_PER_PROJECTILE;
            case PHASE_DASH: return SING_H2_PHASE;
            case GRAV_PRESS: return SING_HE_PRESS;
            case GRAV_GRAB: return SING_HE_GRAB;
            case TIME_SLOW: return SING_HE_SLOW;
            case BLACK_HOLE: return SING_HE_HOLE;
            case GRAV_DOME: return SING_HE_DOME;
            case SINGULARITY: return SING_SM_BOOST;
            case GRAV_SCANNER: return SING_KR_SCANNER_PER_MIN;
            case THREAT_SENSE: return SING_KR_THREAT_PER_MIN;
            case ANALYZER: return SING_KR_ANALYZE;
            default: return 0F;
        }
    }

    /**
     * A USE_ONCE function whose gasUse() is per damage point it absorbs, not per use (the soft
     * landing: ArmorGasSC.fallDampingGas) - the texts say "mB per damage point" for it.
     */
    public boolean gasPerPoint() {
        return this == FALL_DAMPING || this == GRAV_STRIKE;
    }

    /** The least of its gas the function needs in the suit to switch on (the dash: a whole dash's worth). */
    public int gasMin() {
        switch (this) {
            case DASH: return ArmorGasSC.H2_DASH;
            case PHASE_DASH: return SING_H2_PHASE;
            case HEAT_VENT: return SING_AR_HEAT_VENT;
            case VOID_RESCUE: return SING_HE_VOID_RESCUE;
            default: return 1;
        }
    }

    /** Fired by its key (once, then off again) instead of switched on and off. */
    public boolean isAction() {
        return this == DASH || this == ANNIHILATION || this == PHASE_DASH || this == GRAV_PRESS || this == GRAV_GRAB
                || this == TIME_SLOW || this == BLACK_HOLE || this == GRAV_DOME || this == SINGULARITY;
    }

    /** Switched off below SING_LOW_CHARGE of its piece's charge (plan §5, О2: flight, the event horizon, the anchor). */
    public boolean offOnLowCharge() {
        return this == GRAV_FLIGHT || this == EVENT_HORIZON || this == GRAV_ANCHOR;
    }

    /** Works only with all four Singular pieces worn (К9, К1, К2); its row and its level are the chestplate's. */
    public boolean needsFullSet() {
        return this == WITHER_VOID || this == SINGULARITY || this == RESONANCE;
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
