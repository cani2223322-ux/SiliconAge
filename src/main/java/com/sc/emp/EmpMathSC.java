package com.sc.emp;

import com.sc.util.ArmorSuit;

/**
 * ПЭ-1: every number of docs/plan-pe1.md in one place, and the pure arithmetic over them (no world,
 * no player - the self-tests call it directly). Config multipliers come in as arguments; the callers
 * pass ConfigSC.emp*. TODO(balance) marks the numbers the design doc doesn't give.
 */
public final class EmpMathSC {

    private EmpMathSC() {
    }

    // ------------------------------------------------------------------ the gun (§1, §5)

    /** Buffer, EU; the Capacity module doubles it. */
    public static final int BUFFER = 600000, BUFFER_CAPACITY = 1200000;
    /** EU per use: a Point shot, a Pulse, a siphon stream's start. */
    public static final int EU_POINT = 50000, EU_PULSE = 150000, EU_SIPHON_START = 5000;
    /** Range, blocks. */
    public static final int RANGE_POINT = 32, RANGE_PULSE = 32, RANGE_SIPHON = 16;
    /** The Focus module: Siphon +8 (§5); Point "+дальность" - TODO(balance): +16. */
    public static final int FOCUS_SIPHON_BONUS = 8, FOCUS_POINT_BONUS = 16;
    /** Charge-up, ticks: Point 1 s, Pulse 3 s (ПЭ-1М 2 s). */
    public static final int CHARGE_POINT = 20, CHARGE_PULSE = 60, CHARGE_PULSE_M = 40;
    /** Pulse cooldown, ticks (20 s). */
    public static final int PULSE_COOLDOWN = 400;
    /** The Pulse's charge-up hum is heard this far (§7 "Укрытие"). */
    public static final int PULSE_HUM_RANGE = 16;
    /** The Pulse charge-up is dropped when the locked target stays out of sight this long (ticks). TODO(balance). */
    public static final int PULSE_LOS_GRACE = 10;

    /**
     * The gun's own heat (NBT "HeatSC", 0..GUN_HEAT_MAX), shared by every mode: Point +10 a shot, Pulse +30,
     * Siphon +11 a second (full in ~9 s, §4 "8-10 с"); the Cooling module x0.7. It cools GUN_COOL_PER_SECOND
     * once GUN_COOL_DELAY ticks pass without heating: full -> 0 in 12.5 s (§5 "10-15 с"). Overheated
     * (reached GUN_HEAT_MAX): no mode works until it is back at 0. TODO(balance): the units are this mod's own.
     */
    public static final int GUN_HEAT_MAX = 100, GUN_HEAT_POINT = 10, GUN_HEAT_PULSE = 30, GUN_HEAT_SIPHON_PER_SECOND = 11,
            GUN_COOL_PER_SECOND = 8, GUN_COOL_DELAY = 20;
    public static final float COOLING_MODULE_MUL = 0.7F;
    /** "предупреждение о перегреве за 2 с" (§8): warn this many seconds of siphon heat before the limit. */
    public static final int OVERHEAT_WARN_SECONDS = 2;

    /** The gun's charge tier (IC2 chargers / storages / the charge pad): HV, like its Энергоячейка HV. TODO(balance). */
    public static final com.sc.energy.Tier CHARGE_TIER = com.sc.energy.Tier.HV;

    public static int capacity(boolean capacityModule) {
        return capacityModule ? BUFFER_CAPACITY : BUFFER;
    }

    public static int euCost(EmpMode m) {
        return m == EmpMode.POINT ? EU_POINT : m == EmpMode.PULSE ? EU_PULSE : EU_SIPHON_START;
    }

    public static int range(EmpMode m, boolean focus) {
        switch (m) {
            case POINT:
                return RANGE_POINT + (focus ? FOCUS_POINT_BONUS : 0);
            case PULSE:
                return RANGE_PULSE;
            default:
                return RANGE_SIPHON + (focus ? FOCUS_SIPHON_BONUS : 0);
        }
    }

    /** Charge-up ticks (Siphon: none - the stream starts at once). */
    public static int chargeTicks(EmpMode m, boolean upgraded) {
        return m == EmpMode.POINT ? CHARGE_POINT : m == EmpMode.PULSE ? (upgraded ? CHARGE_PULSE_M : CHARGE_PULSE) : 0;
    }

    /** Gun heat a shot (Point / Pulse) or a second of stream (Siphon) adds, with or without the Cooling module. */
    public static int gunHeat(EmpMode m, boolean cooling) {
        int h = m == EmpMode.POINT ? GUN_HEAT_POINT : m == EmpMode.PULSE ? GUN_HEAT_PULSE : GUN_HEAT_SIPHON_PER_SECOND;
        return cooling ? Math.max(1, Math.round(h * COOLING_MODULE_MUL)) : h;
    }

    /** The overheat warning is due: less than OVERHEAT_WARN_SECONDS of siphon heat left. */
    public static boolean overheatWarn(int heat, boolean cooling) {
        return heat < GUN_HEAT_MAX && GUN_HEAT_MAX - heat <= OVERHEAT_WARN_SECONDS * gunHeat(EmpMode.SIPHON, cooling);
    }

    /** Seconds until an overheated gun is back at 0. */
    public static int coolSeconds(int heat) {
        return (heat + GUN_COOL_PER_SECOND - 1) / GUN_COOL_PER_SECOND;
    }

    // ------------------------------------------------------------------ suppression (§2)

    /** Seconds by suit (NANO, QUANTUM, EXO, SINGULAR): {Point (a piece), Pulse (the whole suit)}. */
    private static final int[][] DURATION_S = {{16, 14}, {13, 11}, {11, 8}, {8, 6}};
    /** ПЭ-1М: Point lasts x1.25. */
    public static final float UPGRADED_POINT_MUL = 1.25F;
    /** The target's Defense chip I/II/III: suppression -10/20/30%. */
    public static final int[] DEFENSE_DURATION_PCT = {10, 20, 30};
    /** Helium in the target's loop: suppression -20%. */
    public static final float HELIUM_DURATION_MUL = 0.8F;
    /** Immunity after a suppression ends, seconds (config immunitySeconds). */
    public static final int IMMUNITY_SECONDS = 20;
    /** A target without the mod's armour (or that zone empty): Slowness (amplifier 0) this long. */
    public static final int NO_ARMOR_SLOW_TICKS = 40;
    /** The shortest suppression however many modifiers stack. */
    public static final int MIN_DURATION_TICKS = 20;
    /** Soft descent of a suppressed flyer (§1: "плавный спуск 5 с") - the existing ArmorLogicSC.SOFT_DESCENT_TICKS. */
    public static final int SOFT_DESCENT_TICKS = 100;

    /** The base seconds of the table (no modifiers). Null suit: 0. */
    public static int baseSeconds(ArmorSuit suit, EmpMode m) {
        if (suit == null || m == EmpMode.SIPHON) {
            return 0;
        }
        return DURATION_S[Math.min(DURATION_S.length - 1, suit.ordinal())][m == EmpMode.POINT ? 0 : 1];
    }

    /**
     * Suppression of a piece of `suit`, ticks: the table x ПЭ-1М (Point) x Defense chip x helium x config,
     * at least MIN_DURATION_TICKS. 0 for Siphon / no suit.
     */
    public static int durationTicks(ArmorSuit suit, EmpMode m, boolean upgraded, int defenseTier, boolean heliumInLoop, float configMul) {
        int base = baseSeconds(suit, m) * 20;
        if (base <= 0) {
            return 0;
        }
        double t = base;
        if (upgraded && m == EmpMode.POINT) {
            t *= UPGRADED_POINT_MUL;
        }
        t *= 1D - pct(DEFENSE_DURATION_PCT, defenseTier) / 100D;
        if (heliumInLoop) {
            t *= HELIUM_DURATION_MUL;
        }
        t *= Math.max(0F, configMul);
        return Math.max(MIN_DURATION_TICKS, (int) Math.round(t));
    }

    public static int immunityTicks(int configSeconds) {
        return Math.max(0, configSeconds) * 20;
    }

    // ------------------------------------------------------------------ Point zones (§1)

    /** "Куда бить Точкой" (gun NBT "EmpZone"): by the hit (default), always helmet / chestplate / legs. */
    public static final int ZONE_AUTO = 0, ZONE_HEAD = 1, ZONE_BODY = 2, ZONE_LEGS = 3, ZONE_COUNT = 4;
    /** Hit height as a share of the target's height: at or above HEAD_FROM - the head, at or above BODY_FROM - the body. */
    public static final double HEAD_FROM = 0.72, BODY_FROM = 0.40;

    /** The zone a hit at `heightShare` (0 feet .. 1 top of the hitbox) lands in. */
    public static int zoneOf(double heightShare) {
        return heightShare >= HEAD_FROM ? ZONE_HEAD : heightShare >= BODY_FROM ? ZONE_BODY : ZONE_LEGS;
    }

    /** Armour slots (ItemArmor.armorType: 0 helmet .. 3 boots) the Point hits: head -> {0}, body -> {1}, legs -> {2, 3}. */
    public static int[] pointPieces(int zoneSetting, double heightShare) {
        int z = zoneSetting > ZONE_AUTO && zoneSetting < ZONE_COUNT ? zoneSetting : zoneOf(heightShare);
        return z == ZONE_HEAD ? new int[]{0} : z == ZONE_BODY ? new int[]{1} : new int[]{2, 3};
    }

    // ------------------------------------------------------------------ heat and helium (§3)

    /** Heat of a hit, % of the target suit's heatCapacity: Point 15, Pulse 35, Siphon 3 a second. */
    public static final int HEAT_PCT_POINT = 15, HEAT_PCT_PULSE = 35, HEAT_PCT_SIPHON = 3;
    /** Hits after the first within this window (ticks) heat half as much. */
    public static final int DIMINISH_WINDOW = 400;
    public static final float DIMINISH_MUL = 0.5F, IMMUNE_MUL = 0.5F, SINGULAR_HEAT_MUL = 0.5F;
    /** The target's Defense chip I/II/III: ПЭ-1 heat -10/20/30%. */
    public static final int[] DEFENSE_HEAT_PCT = {10, 20, 30};

    /**
     * Heat units one hit (Point / Pulse) or one second of stream (Siphon) gives a suit of `heatCapacity`:
     * the % x 1/2 for a repeat within DIMINISH_WINDOW x 1/2 while immune x 1/2 Singular x Defense chip x config.
     */
    public static int heat(int heatCapacity, EmpMode m, boolean repeat, boolean immune, boolean singular, int defenseTier, float configMul) {
        int pct = m == EmpMode.POINT ? HEAT_PCT_POINT : m == EmpMode.PULSE ? HEAT_PCT_PULSE : HEAT_PCT_SIPHON;
        double h = heatCapacity * pct / 100D;
        if (repeat && m != EmpMode.SIPHON) {
            h *= DIMINISH_MUL;
        }
        if (immune) {
            h *= IMMUNE_MUL;
        }
        if (singular) {
            h *= SINGULAR_HEAT_MUL;
        }
        h *= 1D - pct(DEFENSE_HEAT_PCT, defenseTier) / 100D;
        h *= Math.max(0F, configMul);
        return Math.max(0, (int) Math.round(h));
    }

    /** Boil-off multiplier shown on the target's HUD («×3 расход») and its time, ticks: Point x2 15 s, Pulse x3 20 s, Siphon x2 while the stream runs + 10 s. */
    public static final int BOIL_MUL_POINT = 2, BOIL_MUL_PULSE = 3, BOIL_MUL_SIPHON = 2;
    public static final int BOIL_TICKS_POINT = 300, BOIL_TICKS_PULSE = 400, BOIL_TICKS_SIPHON_AFTER = 200;
    /** Share of the whole helium loop one hit boils away (spread over its time): Point ~5%, Pulse ~10%; the stream ~1% a second. */
    public static final float BOIL_LOOP_PCT_POINT = 5F, BOIL_LOOP_PCT_PULSE = 10F, BOIL_LOOP_PCT_STREAM_PER_SECOND = 1F;

    public static int boilMul(EmpMode m) {
        return m == EmpMode.PULSE ? BOIL_MUL_PULSE : m == EmpMode.POINT ? BOIL_MUL_POINT : BOIL_MUL_SIPHON;
    }

    public static int boilTicks(EmpMode m) {
        return m == EmpMode.PULSE ? BOIL_TICKS_PULSE : m == EmpMode.POINT ? BOIL_TICKS_POINT : BOIL_TICKS_SIPHON_AFTER;
    }

    /**
     * Extra boil-off, % of the whole loop a second, for a boil of multiplier `mul` (x3: Pulse 10% / 20 s; x2:
     * Point 5% / 15 s - also the siphon's 10 s tail), or the running stream (1%/s). 0 for mul <= 1.
     */
    public static float boilLoopPctPerSecond(int mul, boolean stream) {
        if (stream) {
            return BOIL_LOOP_PCT_STREAM_PER_SECOND;
        }
        if (mul >= BOIL_MUL_PULSE) {
            return BOIL_LOOP_PCT_PULSE / (BOIL_TICKS_PULSE / 20F);
        }
        if (mul >= BOIL_MUL_POINT) {
            return BOIL_LOOP_PCT_POINT / (BOIL_TICKS_POINT / 20F);
        }
        return 0F;
    }

    /** mB a second the boil takes from a loop of `loopCapacityMb`: the share above, x 1/2 while immune, x config. */
    public static float boilMbPerSecond(int loopCapacityMb, int mul, boolean stream, boolean immune, float configMul) {
        float mb = loopCapacityMb * boilLoopPctPerSecond(mul, stream) / 100F;
        if (immune) {
            mb *= IMMUNE_MUL;
        }
        return Math.max(0F, mb * Math.max(0F, configMul));
    }

    /** Boils don't stack: the stronger multiplier wins, the same one is extended. @return {mul, untilTick} after a new boil */
    public static long[] mergeBoil(int curMul, long curUntil, int newMul, long newUntil, long now) {
        if (curUntil <= now || newMul > curMul) {
            return new long[]{newMul, newUntil};
        }
        if (newMul == curMul) {
            return new long[]{curMul, Math.max(curUntil, newUntil)};
        }
        return new long[]{curMul, curUntil};                     // a weaker boil while a stronger one runs: no change
    }

    // ------------------------------------------------------------------ siphon (§4)

    /** EU/t: the gun's EV, with the Wide channel module IV. */
    public static final int SIPHON_RATE = 2048, SIPHON_RATE_WIDE = 8192;
    /** Loss, %: base (config siphonLoss), with the Efficient siphon module, against a suppressed suit. */
    public static final int SIPHON_LOSS = 25, SIPHON_LOSS_EFFICIENT = 10, SIPHON_LOSS_SUPPRESSED = 0;
    /** The target's Defense chip I/II/III: siphon speed -20/30/40%. */
    public static final int[] DEFENSE_SIPHON_PCT = {20, 30, 40};
    /** "Пока моя броня не наберёт N%" (gun NBT "EmpLimit"): 0 = until the receiver is full. */
    public static final int LIMIT_FULL = 0;
    /** Receivers (gun NBT "EmpRecv" order, "EmpRecvOff" bits): the mod's battery in the inventory, own worn armour, the gun itself. */
    public static final int RECV_BATTERY = 0, RECV_ARMOR = 1, RECV_GUN = 2, RECV_COUNT = 3;

    public static int gunRate(boolean wide) {
        return wide ? SIPHON_RATE_WIDE : SIPHON_RATE;
    }

    /** EU/t the stream takes from the target: min(gun, receiver tier, target suit tier) x Defense chip x config. */
    public static int siphonRate(int gunRate, int receiverVoltage, int targetVoltage, int defenseTier, float configMul) {
        double r = Math.min(gunRate, Math.min(receiverVoltage, targetVoltage));
        r *= 1D - pct(DEFENSE_SIPHON_PCT, defenseTier) / 100D;
        r *= Math.max(0F, configMul);
        return Math.max(0, (int) Math.floor(r));
    }

    /** Loss % of the stream: 0 against a suppressed suit; the Efficient module scales config's base 25 -> 10. */
    public static int siphonLossPct(boolean efficient, boolean targetSuppressed, int configLoss) {
        if (targetSuppressed) {
            return SIPHON_LOSS_SUPPRESSED;
        }
        int base = Math.max(0, Math.min(100, configLoss));
        return efficient ? Math.round(base * (float) SIPHON_LOSS_EFFICIENT / SIPHON_LOSS) : base;
    }

    /** EU that reach the receiver out of `taken` at `lossPct`. */
    public static int delivered(int taken, int lossPct) {
        return (int) ((long) Math.max(0, taken) * (100 - Math.max(0, Math.min(100, lossPct))) / 100);
    }

    /** "Энергия утекает!" / the target's dimmed lights are kept this long after the last stream tick. */
    public static final int DRAIN_SHOW_TICKS = 20;
    /** The Sensor-chip warning «ЭМИ-наведение» lasts this long after the last aim scan (the scan runs every AIM_SCAN_TICKS). */
    public static final int AIMED_SHOW_TICKS = 25, AIM_SCAN_TICKS = 10;

    // ------------------------------------------------------------------ target protections (§7)

    /** Emergency reboot: argon (suits with an argon tank), share of the suit's charge, cooldown. TODO(balance): argon mB. */
    public static final int REBOOT_ARGON_MB = 100, REBOOT_COOLDOWN = 1200;
    public static final float REBOOT_CHARGE_SHARE = 0.2F;
    /** Singular «Страж»: chance (%) to turn a Pulse back. TODO(balance). */
    public static final int GUARD_REFLECT_PCT = 30;
    /** EMP screen: the field pays this for each absorbed shot / each second of absorbed stream. TODO(balance). */
    public static final int SCREEN_EU_POINT = 5000, SCREEN_EU_PULSE = 15000, SCREEN_EU_SIPHON_PER_SECOND = 500;

    public static int screenCost(EmpMode m) {
        return m == EmpMode.POINT ? SCREEN_EU_POINT : m == EmpMode.PULSE ? SCREEN_EU_PULSE : SCREEN_EU_SIPHON_PER_SECOND;
    }

    // ------------------------------------------------------------------

    /** table[tier - 1], 0 for tier 0 / out of range. */
    public static int pct(int[] table, int tier) {
        return tier >= 1 && tier <= table.length ? table[tier - 1] : 0;
    }
}
