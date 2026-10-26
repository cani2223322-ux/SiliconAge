package com.sc.hazard;

import java.util.Random;

import com.sc.mob.MobEntitiesSC;
import com.sc.settle.ReputationSC;
import com.sc.settle.SettlementSizeSC;

/**
 * PACKAGE S (docs/plan-substrate-tech-f4.md §6.1, rules ПН-1..ПН-8; proposal §9 «Налёт на поселение»): a raid's numbers as pure
 * decisions (the self-test asks them; RaidSC runs by them). Signatures and the numbers' meaning fixed (the coordinator's); open
 * numbers TODO(balance).
 * <ul>
 * <li>when: at night (NIGHT_FROM..NIGHT_TO of the day's ticks), a settlement whose core is loaded, a defender near - a player
 * not in creative of «Знакомый» or above (ReputationSC.ACQUAINTANCE_FROM) within reach(size) of the core (64 at a Post .. 96 at
 * a City); one roll a night a settlement, CHANCE_PCT, never within COOLDOWN_DAYS of its last raid;</li>
 * <li>three waves (wave): Nano - Marauders and Linemen; Quantum - Troopers and a Jammer; then either an elite with a Jammer, or
 * a column of Hollow Shells with two repair drones; each comes from SPAWN_OUT_MIN..SPAWN_OUT_MAX past the dome's edge on the
 * gate's side (under the dome they can't come - IMob is pushed out: the fight is at the gate and the dock); the next wave when
 * this one is down, or after WAVE_TICKS anyway;</li>
 * <li>won (the third wave down): every defender within reach + 32 gets REP[size] «Доверия» (ReputationSC.SRC_RAID), CHIPS[size]
 * Credit Chips and the Elder's chest (CHEST_ROLLS of the ruins' loot); lost (RAID_TICKS over, or no defender near for
 * ABANDON_TICKS): the settlement's trade is closed for CLOSED_TICKS;</li>
 * <li>nothing is simulated without a player: no raid starts without a defender, a raid nobody defends just ends lost.</li>
 * </ul>
 */
public final class RaidRulesSC {

    /** ПН-1: the chance a night (%), the days between two raids on one settlement, the waves. TODO(balance): 10% */
    public static final int CHANCE_PCT = 10, COOLDOWN_DAYS = 5, WAVES = 3;
    /** ПН-1: the night's window in the day's ticks (getWorldTime % 24000). */
    public static final int NIGHT_FROM = 13000, NIGHT_TO = 23000;
    /** ПН-1: a defender within this of the core, by SettlementSizeSC ordinal (Post, Settlement, City). TODO(balance) */
    public static final int[] REACH = {64, 80, 96};
    /** ПН-2: a wave appears this far past the dome's edge, on the gate's side; the next wave after this at the latest. */
    public static final int SPAWN_OUT_MIN = 24, SPAWN_OUT_MAX = 40, WAVE_TICKS = 2400;
    /** ПН-4: lost after this long in all, or with no defender near for ABANDON_TICKS. TODO(balance) */
    public static final int RAID_TICKS = 12000, ABANDON_TICKS = 1200;
    /** ПН-3: the rewards by size - «Доверие» (30..50), Credit Chips (6..12), the Elder chest's rolls. TODO(balance) */
    public static final int[] REP = {30, 40, 50}, CHIPS = {6, 9, 12}, CHEST_ROLLS = {2, 3, 4};
    /** ПН-4: a lost raid closes the settlement's trade this long (a game day). */
    public static final long CLOSED_TICKS = 24000L;
    /** ПН-2: the third wave is the elite one (an elite with a Jammer) with this chance, else the Shells' column. */
    public static final float ELITE_WAVE_CHANCE = 0.5F;
    /** ПД-3 (docs/plan-ideas-2026-10-26.md): a heavy night's waves - the fourth one the Exo Executioner, a Commander Trooper, a Jammer. */
    public static final int WAVES_HEAVY = 4;

    private static final long SALT = 0x2A1D5L;

    private RaidRulesSC() {
    }

    /** ПН-1: the defenders' reach of a settlement of SettlementSizeSC ordinal `size`. */
    public static int reach(int size) {
        return REACH[row(size, REACH.length)];
    }

    /**
     * The tables' row of SettlementSizeSC ordinal `size` (2026-10-26, ПД-3 / ПД-7): SettlementSizeSC.tableIndex - a Substrate size its
     * own, a relay post the Post's, anything else the last (as the old clamp), within `length`.
     */
    static int row(int size, int length) {
        int i = size < 0 ? 0 : size >= SettlementSizeSC.values().length ? length - 1 : SettlementSizeSC.of(size).tableIndex();   // out of range: clamped
        return Math.max(0, Math.min(length - 1, i));
    }

    /** ПД-3: the waves of a raid of kind `kind` (RaidRunSC.KIND_*: 0 ordinary, 1 an alarm, 2 a heavy night). */
    public static int waves(int kind) {
        return kind == RaidAlarmSC.KIND_HEAVY ? WAVES_HEAVY : WAVES;
    }

    /** ПД-3 (pure): `v` scaled by `mul`, rounded up (a heavy night's reward: 45/60/75, 9/14/18, 3/5/6). */
    public static int scaled(int v, float mul) {
        return (int) Math.ceil(v * (double) mul - 1.0E-6D);
    }

    /** ПД-3: the Elder chest's rolls of a won raid by size. */
    public static int chestRolls(int size) {
        return CHEST_ROLLS[row(size, CHEST_ROLLS.length)];
    }

    /** ПН-1: world time `worldTime` is in the night's window. */
    public static boolean night(long worldTime) {
        long t = (worldTime % StormRulesSC.DAY + StormRulesSC.DAY) % StormRulesSC.DAY;
        return t >= NIGHT_FROM && t < NIGHT_TO;
    }

    /** ПН-1: a player of standing `reputation` defends (not a creative one - the caller checks). */
    public static boolean defender(int reputation) {
        return reputation >= ReputationSC.ACQUAINTANCE_FROM;
    }

    /**
     * ПН-1: settlement may roll for a raid tonight (`today` - SettlementSC.day): the rule on, the night, not rolled tonight, its
     * last raid COOLDOWN_DAYS or more ago (lastRaidDay < 0: never), a defender near.
     */
    public static boolean mayRoll(boolean rule, boolean night, long today, long lastRollDay, long lastRaidDay, boolean defenderNear) {
        return rule && night && defenderNear && lastRollDay != today && (lastRaidDay < 0L || today - lastRaidDay >= COOLDOWN_DAYS);
    }

    /** ПН-1: the roll of settlement `id` on night `day` (by the world's seed: the same however often it's asked). */
    public static boolean rolls(long seed, int id, long day) {
        return StormRulesSC.pick(StormRulesSC.mix(seed ^ SALT, id, day), 100) < CHANCE_PCT;
    }

    /**
     * ПН-2: wave `n` (0..WAVES-1) at a settlement of size `size` - {pool id, count} pairs (MobEntitiesSC ids); the third wave's
     * elite is the first armoured one of it (RaidSC applies EliteSC). TODO(balance): the counts.
     */
    public static int[][] wave(int n, int size, Random r) {
        int s = row(size, SettlementSizeSC.SUBSTRATE_SIZES);   // 2026-10-26: by tableIndex (a relay post - the Post's)
        if (n >= WAVES_HEAVY - 1) {                           // ПД-3: a heavy night's fourth - the Trooper first (RaidRunSC: the Commander)
            return new int[][]{{MobEntitiesSC.ID_TROOPER, 1}, {MobEntitiesSC.ID_EXECUTIONER, 1}, {MobEntitiesSC.ID_JAMMER, 1}};
        }
        if (n <= 0) {
            return new int[][]{{MobEntitiesSC.ID_MARAUDER, 2 + s}, {MobEntitiesSC.ID_LINEMAN, 2}};
        }
        if (n == 1) {
            return new int[][]{{MobEntitiesSC.ID_TROOPER, 1 + s}, {MobEntitiesSC.ID_JAMMER, 1}};
        }
        if (r.nextFloat() < ELITE_WAVE_CHANCE) {
            return new int[][]{{MobEntitiesSC.ID_TROOPER, 1}, {MobEntitiesSC.ID_JAMMER, 1}, {MobEntitiesSC.ID_MARAUDER, 1 + s}};
        }
        return new int[][]{{MobEntitiesSC.ID_HOLLOW_SHELL, 2 + s}, {MobEntitiesSC.ID_REPAIR_DRONE, 2}};
    }

    /** ПН-2: wave `n` brings an elite (the first armoured one of it). */
    public static boolean eliteWave(int n, int[][] wave) {
        return n == WAVES - 1 && wave.length > 0 && wave[0][0] == MobEntitiesSC.ID_TROOPER;
    }

    /** ПН-3: the «Доверие» of a won raid by size. */
    public static int rep(int size) {
        return REP[row(size, REP.length)];
    }

    /** ПН-3: the Credit Chips of a won raid by size. */
    public static int chips(int size) {
        return CHIPS[row(size, CHIPS.length)];
    }
}
