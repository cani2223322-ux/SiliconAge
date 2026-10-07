package com.sc.util;

/**
 * Functions of the energy blades (ItemBladeSC), switched on / off and bound to keys in the armour
 * screen (K, the "Blade" tab) just like the suits' ArmorFeature. A function comes with its blade
 * tier and every tier above it. Ordinals are the bits of the blade's "FnToggled" NBT - only ever append.
 *
 * TODO(not in the design doc): the whole list and all the numbers are this mod's own.
 * The Singular blade's own functions (minType SINGULAR, appended) open at the blade's level (singLevel) and
 * may take a gas from the worn armour (gas / gasMb / gasPerSecond) - see BladeLogicSC.unlocked and BladeSingularSC.
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
    LOOTING(BladeType.NANO, 0, true),         // a real Looting enchantment on the blade: III / IV / V by tier (appended)

    // ---- the Singular blade (docs/plan-singular-tools.md §2.2; appended): blade level, heat, default, gas, mB, per second, cooldown
    GRAV_PULL(1, 6, true, ArmorGasSC.Gas.SINGULAR_MATTER, 5, false, 60),       // key: the target looked at (12) is pulled in and hit
    CASCADE(1, 0, true, null, 0, false, 0),                                    // hits in a row on one target +10% each, up to +50%
    SHEATH(1, 0, true, null, 0, false, 0),                                     // switched off on the hotbar: +10% speed, slow charge from the chestplate
    RIFT_STEP(2, 8, true, ArmorGasSC.Gas.SINGULAR_MATTER, 10, false, 160),     // key: behind the target looked at (16), a blow x1.5
    PERFECT_PARRY(2, 2, true, ArmorGasSC.Gas.ARGON, 20, false, 0),             // a hit within 0.3 s of raising the block: none, slows round, next x2
    HUNTER_SENSE(2, 0, false, ArmorGasSC.Gas.KRYPTON, 2, true, 0),             // hostile mobs within 32 outlined through walls
    CHARGED_STRIKE(3, 4, true, ArmorGasSC.Gas.ARGON, 15, false, 0),            // a block held 1.5 s charges the next hit: x3, knockback 10
    EVENT_HORIZON(3, 1, true, ArmorGasSC.Gas.HELIUM, 10, true, 0),             // blocking: projectiles swallowed, each charges the next wave
    GRAV_SHIELD(3, 2, true, ArmorGasSC.Gas.HELIUM, 15, true, 0),               // blocking: mobs and projectiles pushed out of 3 blocks
    CHAIN_CUT(4, 1, true, ArmorGasSC.Gas.SINGULAR_MATTER, 5, false, 0),        // a hit jumps on to 3 more targets within 6, -25% each
    RIPOSTE(4, 0, true, null, 0, false, 0),                                    // a block that took over 10 damage: the next hit adds half of it
    GRAV_TETHER(4, 5, true, ArmorGasSC.Gas.KRYPTON, 10, false, 240),           // key: the target can't get further than 6 blocks for 5 s
    FORM_ATTACK(1, 0, true, null, 0, false, 0);                                // key: the form's special attack (formAttack* below)

    public final BladeType minType;
    /** Heat per second while on (BLADE), per use (keys), per block / projectile (block, deflect). */
    public final int heat;
    public final boolean onByDefault;
    /** Singular functions: the blade level they open at, the branch they need (0: none), their gas and cooldown. */
    private final int singLevel, branch, gasMb, cooldownTicks;
    private final ArmorGasSC.Gas gas;
    private final boolean gasPerSecond;

    public static final int HIT_HEAT = 1;
    public static final int CUT_COST = 50, BLOCK_EU_PER_POINT = 100, DEFLECT_COST = 100;
    public static final int SWEEP_COST = 5000, WAVE_COST = 20000, LUNGE_COST = 10000;
    public static final double SWEEP_RANGE = 3, WAVE_RANGE = 16, LUNGE_RANGE = 6, DEFLECT_RANGE = 3;
    public static final float EXECUTE_SHARE = 0.2F;
    /** Quantum blade + full Quantum suit: EU a hostile mob killed by a normal hit gives back to the chestplate (less than the hit costs). */
    public static final int KILL_REFUND = 400;
    /** Ticks between two uses of a key function. */
    public static final int ACTION_COOLDOWN = 10;

    // ---- the Singular blade's numbers (docs/plan-singular-tools.md §2; TODO(balance): drafts like the plan's)
    public static final double GRAV_PULL_RANGE = 12, RIFT_RANGE = 16, TETHER_RANGE = 16, TETHER_LEASH = 6, HUNTER_RANGE = 32;
    /** EU of the Singular key functions (their own, not discounted like the Exo-era ones). */
    public static final int GRAV_PULL_COST = 8000, RIFT_COST = 12000, TETHER_COST = 8000;
    public static final float RIFT_MUL = 1.5F;
    /** Duelist, level 5: this many rift steps in a row before the cooldown starts, within RIFT_CHAIN_TICKS of the first. */
    public static final int RIFT_CHARGES = 3, RIFT_CHAIN_TICKS = 200;
    public static final int TETHER_TICKS = 100;
    /** Cascade: +10% a hit in a row on one target, up to +50% (Duelist, level 3: +80%); forgotten after 3 s. */
    public static final float CASCADE_STEP = 0.1F, CASCADE_CAP = 0.5F, CASCADE_CAP_DUELIST = 0.8F;
    public static final int CASCADE_RESET = 60;
    /** Sheath: movement speed share while the blade is off on the hotbar; EU a second from the worn chestplate. */
    public static final double SHEATH_SPEED = 0.1;
    public static final int SHEATH_CHARGE = 1000;
    /** Perfect parry: ticks after raising the block; slowness round (radius, ticks, amplifier); the next hit's multiplier. */
    public static final int PARRY_WINDOW = 6, PARRY_SLOW_TICKS = 40, PARRY_SLOW_AMP = 3;
    public static final double PARRY_SLOW_RADIUS = 4;
    public static final float PARRY_MUL = 2F;
    /** Charged strike: the block held this long charges the next hit - x3 and thrown about 10 blocks. */
    public static final int CHARGE_TICKS = 30;
    public static final float CHARGED_MUL = 3F;
    public static final double CHARGED_PUSH = 1.4, CHARGED_UP = 0.5;
    /** A bonus for the next hit (parry, charge, riposte) waits this long after it was earned (after the block). */
    public static final int BONUS_KEEP = 100;
    /** Event horizon: at most this many projectiles stored, each +25% to the next wave. */
    public static final int HORIZON_MAX = 8;
    public static final float HORIZON_PER_CHARGE = 0.25F;
    /** Gravity shield: radius; Guardian, level 3: the first GUARDIAN_FREE_TICKS of every block cost no helium. */
    public static final double GRAV_SHIELD_RADIUS = 3;
    public static final int GUARDIAN_FREE_TICKS = 60;
    /** Chain cut: targets, reach from the last one, damage share kept per jump. */
    public static final int CHAIN_TARGETS = 3;
    public static final double CHAIN_RANGE = 6;
    public static final float CHAIN_KEEP = 0.75F;
    /** Riposte: damage a block must take, the share the next hit gives back. */
    public static final float RIPOSTE_MIN = 10F, RIPOSTE_SHARE = 0.5F;
    /** Riposte: at most this much is added (10 hearts), however long the block. */
    public static final float RIPOSTE_MAX = 20F;
    /** Destroyer, level 3: area damage x1.25. */
    public static final float DESTROYER_AREA_MUL = 1.25F;
    /** Guardian, level 5 «Последний шанс»: 1 health instead of death, once in 5 minutes, singular matter (cooldown key in the blade). */
    public static final int LAST_CHANCE_COOLDOWN = 6000, LAST_CHANCE_SM = 50;
    public static final String LAST_CHANCE_KEY = "last_chance";

    // ---- forms (BladeForm): the hit and the special attack (FORM_ATTACK)
    public static final float SCYTHE_MUL = 0.8F, SPEAR_MUL = 1.2F, WHIP_MUL = 0.7F, SHIELD_MUL = 0.5F, SINGULAR_MUL = 2F;
    /** The client's own reach for hitting mobs (survival); the spear reaches 3 further, the whip 7 in all. */
    public static final double VANILLA_REACH = 3, SPEAR_REACH = 6, WHIP_REACH = 7, SCYTHE_RADIUS = 3;
    /** Singular form: singular matter a hit (none left: the hit is a sword's). */
    public static final int SINGULAR_SM_PER_HIT = 1;
    public static final double HARVEST_RADIUS = 4, PIERCE_RANGE = 8, GRAB_RANGE = 16, RAM_RANGE = 5,
            COLLAPSE_RADIUS = 8, COLLAPSE_RADIUS_DESTROYER = 12, COLLAPSE_AT = 6;
    public static final int COLLAPSE_TICKS = 40;
    public static final float PIERCE_MUL = 1.2F, RAM_MUL = 0.5F, COLLAPSE_MUL = 2F;

    BladeFeature(BladeType minType, int heat, boolean onByDefault) {
        this.minType = minType;
        this.heat = heat;
        this.onByDefault = onByDefault;
        this.singLevel = 1;
        this.branch = 0;
        this.gas = null;
        this.gasMb = 0;
        this.gasPerSecond = false;
        this.cooldownTicks = 0;
    }

    /** A Singular function: on the Singular blade only, open at its level. */
    BladeFeature(int singLevel, int heat, boolean onByDefault, ArmorGasSC.Gas gas, int gasMb, boolean gasPerSecond, int cooldownTicks) {
        this.minType = BladeType.SINGULAR;
        this.heat = heat;
        this.onByDefault = onByDefault;
        this.singLevel = singLevel;
        this.branch = 0;
        this.gas = gas;
        this.gasMb = gasMb;
        this.gasPerSecond = gasPerSecond;
        this.cooldownTicks = cooldownTicks;
    }

    /** Fired by its key (the switch is a safety catch) instead of switched on and off. */
    public boolean isAction() {
        return this == SWEEP || this == WAVE || this == LUNGE || this == GRAV_PULL || this == RIFT_STEP || this == GRAV_TETHER
                || this == FORM_ATTACK;
    }

    /** One of the Singular blade's own functions (not an Exo-era one). */
    public boolean isSingular() {
        return minType == BladeType.SINGULAR;
    }

    /** The Singular blade level it opens at (the older functions: 1). */
    public int singLevel() {
        return singLevel;
    }

    /** The ToolLevelSC.BLADE_* branch it needs, 0 for none. */
    public int branch() {
        return branch;
    }

    /** Its gas (from the worn armour), null for none. FORM_ATTACK: by form - formAttackGas. */
    public ArmorGasSC.Gas gas() {
        return gas;
    }

    /** mB per use, or per second when gasPerSecond(). */
    public int gasMb() {
        return gasMb;
    }

    public boolean gasPerSecond() {
        return gasPerSecond;
    }

    /** Base cooldown of a key function (ticks, before the full set's -25%), 0 = none. FORM_ATTACK: by form - formAttackCooldown. */
    public int cooldownTicks() {
        return cooldownTicks;
    }

    /** Pure: open on the Singular blade at `level` with branch `chosen` (creative: every branch). */
    public boolean openAt(int level, int chosen, boolean creative) {
        return level >= singLevel && (branch == 0 || creative || chosen == branch);
    }

    // ------------------------------------------------------------------ pure helpers (forms, cascade, chain)

    /** The form's damage multiplier for a normal hit (SINGULAR only with singular matter - else the sword's 1). */
    public static float formMul(BladeForm form) {
        if (form == null) {
            return 1F;
        }
        switch (form) {
            case SCYTHE: return SCYTHE_MUL;
            case SPEAR: return SPEAR_MUL;
            case WHIP: return WHIP_MUL;
            case SHIELD: return SHIELD_MUL;
            case SINGULAR: return SINGULAR_MUL;
            default: return 1F;
        }
    }

    /** How far the form hits (0: the client's own reach). */
    public static double formReach(BladeForm form) {
        return form == BladeForm.SPEAR ? SPEAR_REACH : form == BladeForm.WHIP ? WHIP_REACH : 0;
    }

    /** Cascade multiplier of a hit after `stacks` hits in a row on the same target. */
    public static float cascadeMul(int stacks, boolean duelist) {
        float cap = duelist ? CASCADE_CAP_DUELIST : CASCADE_CAP;
        return 1F + Math.min(cap, Math.max(0, stacks) * CASCADE_STEP);
    }

    /** Damage share of the chain cut's `jump`-th extra target (1..CHAIN_TARGETS): 0.75, 0.56, 0.42. */
    public static float chainMul(int jump) {
        return (float) Math.pow(CHAIN_KEEP, Math.max(0, jump));
    }

    /** The wave's multiplier with `charges` stored by the event horizon. */
    public static float horizonMul(int charges) {
        return 1F + Math.max(0, Math.min(HORIZON_MAX, charges)) * HORIZON_PER_CHARGE;
    }

    /** FORM_ATTACK's base cooldown in that form (the sword's wave: only the usual half second between keys). */
    public static int formAttackCooldown(BladeForm form) {
        switch (form == null ? BladeForm.SWORD : form) {
            case SCYTHE: return 80;
            case SPEAR: return 100;
            case WHIP: return 60;
            case SHIELD: return 120;
            case SINGULAR: return 600;
            default: return 0;
        }
    }

    /** FORM_ATTACK's EU in that form (SWORD: the wave's - Exo-era, so the legacy discount applies). */
    public static int formAttackEu(BladeForm form) {
        switch (form == null ? BladeForm.SWORD : form) {
            case SCYTHE: return 15000;
            case SPEAR: return 15000;
            case WHIP: return 8000;
            case SHIELD: return 15000;
            case SINGULAR: return 40000;
            default: return WAVE_COST;
        }
    }

    /** FORM_ATTACK's heat in that form. */
    public static int formAttackHeat(BladeForm form) {
        switch (form == null ? BladeForm.SWORD : form) {
            case SCYTHE: return 6;
            case SPEAR: return 8;
            case WHIP: return 4;
            case SHIELD: return 6;
            case SINGULAR: return 30;
            default: return WAVE.heat;
        }
    }

    /** FORM_ATTACK's gas in that form: the collapse takes singular matter, the rest none. */
    public static ArmorGasSC.Gas formAttackGas(BladeForm form) {
        return form == BladeForm.SINGULAR ? ArmorGasSC.Gas.SINGULAR_MATTER : null;
    }

    public static int formAttackGasMb(BladeForm form) {
        return form == BladeForm.SINGULAR ? 50 : 0;
    }

    /** Lang key of the form's special attack: sc.bladeform.<form>.attack (+ ".desc"). */
    public static String formAttackKey(BladeForm form) {
        return (form == null ? BladeForm.SWORD : form).langKey() + ".attack";
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
