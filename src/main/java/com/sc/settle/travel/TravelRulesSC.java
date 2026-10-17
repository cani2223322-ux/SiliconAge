package com.sc.settle.travel;

/**
 * PACKAGE B (docs/plan-substrate-tech-f2.md §8, rules ПР-1..ПР-6): the docks, the bridge's targets, the Return
 * Capsule, the Settlement Map and the death mark as pure numbers (the self-test and the handbook read them). The
 * proposal's numbers (§4 «Причалы», «Возврат»); open ones TODO(balance). Signatures fixed; package B may add.
 */
public final class TravelRulesSC {

    /** ПР-2: a bridge end within this of a dock's beacon lands on it exactly (no scatter, no scouting shift). */
    public static final int DOCK_SNAP = 8;
    /** ПР-6: a jump to the death mark scatters this much (proposal §4: «с разбросом 30 блоков»); a target within MARK_SNAP of the mark counts as it. */
    public static final int DEATH_SCATTER = 30, MARK_SNAP = 8;
    /** ПР-6: arriving this close to the death mark (in the Substrate) puts it out. TODO(balance) */
    public static final int DEATH_CLEAR = 32;
    /** ПР-4: the capsule's preparation, 10 s; a chat line every 2 s of it. */
    public static final int CAPSULE_TICKS = 200, CAPSULE_SAY = 40;
    /** ПР-5: the docks on a Settlement Map. */
    public static final int MAP_DOCKS = 3;

    /** ПР-6: how often (ticks) a player in the Substrate is asked whether they reached their death mark. */
    public static final int DEATH_CHECK = 40;
    /** ПР-5: a map is read out in the chat again only after this many ticks (a held right button repeats every 4). */
    public static final int MAP_COOLDOWN = 40;
    /** ПР-3: the bridge's list holds at most this many known docks (besides «Первый причал» and the death mark), nearest first. */
    public static final int LIST_DOCKS = 12;
    /** ПР-3: the kinds of a bridge list entry (the screens' `si14` list, "k"). Append only. */
    public static final int T_FIRST = 0, T_DOCK = 1, T_DEATH = 2;
    /** ПР-2, ПР-6: famInfo's 5th value - the target point is plain ground, a dock, the viewer's death mark. */
    public static final int AT_NONE = 0, AT_DOCK = 1, AT_MARK = 2;

    private TravelRulesSC() {
    }

    /** ПР-2, ПР-6: the scatter radius of a bridge end - 0 at a dock, at least DEATH_SCATTER at the death mark, else `radius` (the bridge's own). */
    public static int scatter(int radius, boolean atDock, boolean atDeathMark) {
        if (atDock) {
            return 0;
        }
        return atDeathMark ? Math.max(radius, DEATH_SCATTER) : radius;
    }

    /** The capsule's seconds left after `elapsed` ticks of preparation (for its chat line). */
    public static int capsuleSecondsLeft(int elapsed) {
        return Math.max(0, (CAPSULE_TICKS - elapsed + 19) / 20);
    }

    /** ПР-4: the countdown's line number `said` (0 - the one at the start) is due after `elapsed` ticks of preparation. */
    public static boolean capsuleSayDue(int elapsed, int said) {
        return elapsed < CAPSULE_TICKS && elapsed >= said * CAPSULE_SAY;
    }

    /** ПР-2: a column (dx, dz) off a dock's beacon is the dock - within DOCK_SNAP across. */
    public static boolean atDock(long dx, long dz) {
        return dx * dx + dz * dz <= (long) DOCK_SNAP * DOCK_SNAP;
    }

    /** ПР-6: a column (dx, dz) off the death mark is the mark - within MARK_SNAP across. */
    public static boolean atMark(long dx, long dz) {
        return dx * dx + dz * dz <= (long) MARK_SNAP * MARK_SNAP;
    }

    /** ПР-6: a player (dx, dy, dz) off their death mark has reached it - closer than DEATH_CLEAR. */
    public static boolean markReached(double dx, double dy, double dz) {
        return dx * dx + dy * dy + dz * dz < (double) DEATH_CLEAR * DEATH_CLEAR;
    }
}
