package com.sc.hazard;

import java.util.Random;

import com.sc.emp.EmpMathSC;
import com.sc.mob.TrophyTableSC;

/**
 * PACKAGE S (docs/plan-substrate-tech-f4.md §5.2, rules ПФ-1..ПФ-8; proposal §9 «ЭМИ-буря»): the storm's numbers as pure
 * decisions - the self-test asks them without a world, EmpStormSC runs by them. Signatures and the meaning of the numbers fixed
 * (the coordinator's); S may refine a body, the numbers stay the proposal's (open ones TODO(balance)).
 * <ul>
 * <li>a game day (SettlementSC.day) has a storm with CHANCE_PCT, by the world's seed and the day (rolls): a restart, a second
 * player, a reload never roll again;</li>
 * <li>it starts at startOf (world time) - inside the day, after DAY_MARGIN, ending before the next dawn - and lasts durationOf
 * (MIN_TICKS..MAX_TICKS, 3..5 min); WARN_TICKS (30 s) before it the warning (the domes' siren, the violet sky);</li>
 * <li>a pulse every PULSE_MIN..PULSE_MAX (20..40 s): every player and armoured mob under the open sky within REACH of a player
 * gets one random worn piece of the mod's armour suppressed for SUPPRESS_MIN..SUPPRESS_MAX (3..4 s; immunity respected), the
 * helium loop boiling BOIL_MUL times for BOIL_TICKS; a Hollow Shell reboots SHELL_REBOOT_TICKS (ПБ-21);</li>
 * <li>cover: COVER_BLOCKS solid blocks over the head (looked for up to COVER_SCAN above it), or a field with F_EMP_SCREEN
 * round it (it pays EmpMathSC.SCREEN_EU_PULSE, a settlement's core always);</li>
 * <li>during the storm: the mod's own solar panels give 0, the swarms come SWARM_MUL times as often, the Arc Striders come,
 * the trophies x TROPHY_MUL (ПТ-4: with an elite at most x2.5).</li>
 * </ul>
 */
public final class StormRulesSC {

    /** ПФ-1: a storm on this share of the game days (%); its length; the warning before it. TODO(balance): 20% */
    public static final int CHANCE_PCT = 20, MIN_TICKS = 3600, MAX_TICKS = 6000, WARN_TICKS = 600;
    /** A storm never starts before dawn + DAY_MARGIN (ticks) and always ends before the next dawn. */
    public static final int DAY_MARGIN = 1000;
    /** ПФ-2: the pulses' spacing; the suppression of one piece; the helium boil. TODO(balance): the boil */
    public static final int PULSE_MIN = 400, PULSE_MAX = 800, SUPPRESS_MIN = 60, SUPPRESS_MAX = 80;
    public static final int BOIL_MUL = EmpMathSC.BOIL_MUL_POINT, BOIL_TICKS = 200;
    /** ПФ-2: mobs further than this from every player feel no pulse (nothing to see there). */
    public static final int REACH = 128;
    /** ПФ-3: the cover - solid blocks over the head, looked for this far up. */
    public static final int COVER_BLOCKS = 3, COVER_SCAN = 48;
    /** ПБ-21: a Hollow Shell under a pulse reboots this long (as after a Pulse of the ПЭ-1). */
    public static final int SHELL_REBOOT_TICKS = 60;
    /** ПФ-6: the swarms' weight during a storm, times this; ПФ-7: the trophies (TrophyTableSC.STORM_MUL). */
    public static final int SWARM_MUL = 3;
    public static final float TROPHY_MUL = TrophyTableSC.STORM_MUL;
    /** One game day (= SettlementSC.DAY). */
    public static final long DAY = 24000L;
    /**
     * ПФ-9 (§5.5, proposal §3 «Кремнезёмная мгла — густой туман на 2–3 мин, видимость около 24 блоков. Вражеские стрелки слепнут
     * так же, как игрок»): a haze on HAZE_CHANCE_PCT of the days, HAZE_MIN..MAX ticks, never over the day's storm (haze window -
     * the day's other half); the visibility HAZE_SIGHT for the players' fog and every mob's beam / ПЭ-1 aim. TODO(balance): the chance
     */
    public static final int HAZE_CHANCE_PCT = 25, HAZE_MIN = 2400, HAZE_MAX = 3600, HAZE_SIGHT = 24;
    /**
     * ПФ-10 (§5.6, proposal §9 «Сингулярный выброс. Раз в игровую неделю в Разломе на 10 мин: радиация ×3, шанс Крупиц ×2
     * (суточный лимит остаётся), Затмение появляется в 3 раза чаще»): once a game week (SettlementSC.week), EMISSION_TICKS long.
     */
    public static final int EMISSION_TICKS = 12000;
    public static final float EMISSION_RADIATION_MUL = 3F, EMISSION_CRUMB_MUL = 2F, EMISSION_ECLIPSE_MUL = 3F;
    /** A game week in days (= SettlementSC.WEEK_DAYS). */
    public static final int WEEK_DAYS = 7;

    private static final long SALT = 0x5702AL;

    private StormRulesSC() {
    }

    /** ПФ-1: game day `day` of the world of `seed` has a storm. */
    public static boolean rolls(long seed, long day) {
        return pick(mix(seed ^ SALT, day, 1), 100) < chancePct();
    }

    /** ПМ-6 (audit 2026-10-20): the day's storm chance with dimension.stormChanceMultiplier (CHANCE_PCT by default), at most 100. */
    public static int chancePct() {
        return Math.max(0, Math.min(100, Math.round(CHANCE_PCT * com.sc.dim.SubstrateConfigSC.stormChanceMultiplier)));
    }

    /** ПМ-6: the day's haze chance with dimension.hazeChanceMultiplier (HAZE_CHANCE_PCT by default), at most 100. */
    public static int hazeChancePct() {
        return Math.max(0, Math.min(100, Math.round(HAZE_CHANCE_PCT * com.sc.dim.SubstrateConfigSC.hazeChanceMultiplier)));
    }

    /** ПФ-1: the storm's length on that day (ticks, MIN_TICKS..MAX_TICKS). */
    public static int durationOf(long seed, long day) {
        return MIN_TICKS + pick(mix(seed ^ SALT, day, 2), MAX_TICKS - MIN_TICKS + 1);
    }

    /** ПФ-1: the world time the storm of that day starts (its warning WARN_TICKS before): dawn + DAY_MARGIN .. so that it ends by the next dawn. */
    public static long startOf(long seed, long day) {
        int room = (int) (DAY - DAY_MARGIN - WARN_TICKS - durationOf(seed, day));
        return day * DAY + DAY_MARGIN + WARN_TICKS + pick(mix(seed ^ SALT, day, 3), Math.max(1, room));
    }

    /** The phase at world time `now` of a storm starting at `start` and ending at `end` (StormStateSC.CALM / WARNING / STORM). */
    public static int phaseAt(long start, long end, long now) {
        if (now >= start && now < end) {
            return StormStateSC.STORM;
        }
        return now >= start - WARN_TICKS && now < start ? StormStateSC.WARNING : StormStateSC.CALM;
    }

    /** ПФ-9: game day `day` has a Silica Haze. */
    public static boolean hazeRolls(long seed, long day) {
        return pick(mix(seed ^ SALT, day, 11), 100) < hazeChancePct();   // ПМ-6
    }

    /**
     * ПФ-9: {start, end} world time of that day's haze, never over the day's storm (if any) or its warning: in the longer of the
     * stretches before and after it (after dawn + DAY_MARGIN, before the next dawn); {0, 0} - no room (never with these numbers).
     */
    public static long[] hazeWindow(long seed, long day) {
        int len = HAZE_MIN + pick(mix(seed ^ SALT, day, 12), HAZE_MAX - HAZE_MIN + 1);
        long dawn = day * DAY, from = dawn + DAY_MARGIN, room = DAY - DAY_MARGIN - len;
        if (rolls(seed, day)) {
            long warnAt = startOf(seed, day) - WARN_TICKS, end = startOf(seed, day) + durationOf(seed, day);
            long before = warnAt - from - len, after = dawn + DAY - end - len;
            if (after > before) {
                from = end;
                room = after;
            } else {
                room = before;
            }
        }
        if (room < 1L) {
            return new long[]{0L, 0L};
        }
        long start = from + pick(mix(seed ^ SALT, day, 13), (int) Math.min(Integer.MAX_VALUE, room));
        return new long[]{start, start + len};
    }

    /** ПФ-10: {start, end} world time of game week `week`'s Singular Emission (inside the week, ending before it ends). */
    public static long[] emissionWindow(long seed, long week) {
        long from = week * WEEK_DAYS * DAY + DAY_MARGIN, room = WEEK_DAYS * DAY - DAY_MARGIN - EMISSION_TICKS;
        long start = from + (pick(mix(seed ^ SALT, week, 21), Integer.MAX_VALUE) % Math.max(1L, room));
        return new long[]{start, start + EMISSION_TICKS};
    }

    /** ПФ-2: ticks to the next pulse. */
    public static int nextPulse(Random r) {
        return PULSE_MIN + r.nextInt(PULSE_MAX - PULSE_MIN + 1);
    }

    /** ПФ-2: a pulse's suppression of one piece (ticks). */
    public static int suppressTicks(Random r) {
        return SUPPRESS_MIN + r.nextInt(SUPPRESS_MAX - SUPPRESS_MIN + 1);
    }

    /** ПФ-3: `solidAbove` blocks over the head are cover. */
    public static boolean covered(int solidAbove) {
        return solidAbove >= COVER_BLOCKS;
    }

    /** SplitMix64's finaliser (as SubstrateTerrainSC's). */
    static long mix(long seed, long a, long b) {
        long h = seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h;
    }

    static int pick(long h, int n) {
        return (int) ((h >>> 1) % n);
    }
}
