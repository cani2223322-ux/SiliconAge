package com.sc.settle;

/**
 * PACKAGE C (docs/plan-substrate-tech-f2.md §5.4, rules ПП-10, ПГ-6, ПГ-7): what a violation in a settlement costs and
 * when the guards answer it, as pure numbers (proposal §6 «Правила провокации»). The hook itself is
 * SettlementProtectSC.violation - called only from the concrete handlers (a break, a place, a right-click, a blow at a
 * creature), never from TileEntityFieldGeneratorSC.refused / ShieldEventHandler.privateFor (the bridge's screen, the
 * wrench and the drill ask those too: a player in the Overworld aiming the bridge near a dome must not «violate»).
 * Written by the coordinator from the table; package C may tune numbers, the signatures stay.
 */
public final class ViolationRulesSC {

    /**
     * Kinds (lang sc.settle.violation.<KEYS[kind]>). The first four are «block» violations - the field's refusals. POINT_WARDEN
     * (2026-10-18, ПГ-20): any ПЭ-1 use at a guard - a Point, a Pulse, a Siphon stream (EntityWardenSC.empHit / empStreamSecond).
     */
    public static final int BREAK = 0, PLACE = 1, USE = 2, FIRE = 3, HIT_TUNER = 4, HIT_WARDEN = 5, POINT_WARDEN = 6, KILL_WARDEN = 7;
    public static final String[] KEYS = {"break", "place", "use", "fire", "hitTuner", "hitWarden", "pointWarden", "killWarden"};
    /** The escalation counts block violations within 60 s. */
    public static final int WINDOW_TICKS = 1200;
    /** ПГ-6: 2 s of warning before the first blow (the guard draws, the alarm sounds); walked 12+ blocks away by then - no attack. */
    public static final int WARN_TICKS = 40, WALK_AWAY = 12;
    /** ПГ-6: a provocation - the guards attack the offender for 5 minutes; ПГ-7: the third violation, 60 s (only with guardsAggroOnViolations). */
    public static final int AGGRO_TICKS = 6000, VIOLATION_AGGRO_TICKS = 1200;
    /** ПГ-6: the guards keep within 48 blocks of the core (past it they turn back and forget), and only those within 96 of the offender act. */
    public static final int LEASH = 48, GUARD_REACH = 96;
    /** One chat line a second at most per player. */
    public static final int SAY_TICKS = 20;
    /**
     * Block violations closer than this to the last one counted are refused but not counted again: a held right-click
     * repeats every 4 ticks, a held place too - one slip must not run up the whole escalation at once. A blow at a
     * Tuner is charged at most this often too. TODO(balance)
     */
    public static final int REPEAT_TICKS = 20;
    /** Stages of the block violations within the window: 1 - a warning, 2 - a guard comes to watch (-5), 3 - the last warning (-10). */
    public static final int STAGE_WARN = 1, STAGE_WATCH = 2, STAGE_LAST = 3;

    private ViolationRulesSC() {
    }

    /** A block violation (the field's refusal), not a blow at a creature. */
    public static boolean blockKind(int kind) {
        return kind >= BREAK && kind <= FIRE;
    }

    /** The stage of the `count`-th block violation within the window (1, 2, then 3 and on). */
    public static int stage(int count) {
        return Math.max(STAGE_WARN, Math.min(STAGE_LAST, count));
    }

    /** ПД-3: the «Доверие» it costs (0 or less). KILL_WARDEN: its fixed part (ReputationSC.afterWardenKill does the rest). */
    public static int penalty(int kind, int stage) {
        switch (kind) {
            case BREAK:
            case PLACE:
            case USE:
            case FIRE:
                return stage <= STAGE_WARN ? 0 : stage == STAGE_WATCH ? ReputationSC.VIOLATION : ReputationSC.VIOLATION_LAST;
            case HIT_TUNER:
                return ReputationSC.HIT_TUNER;
            case HIT_WARDEN:
            case POINT_WARDEN:
                return ReputationSC.HIT_WARDEN;
            case KILL_WARDEN:
                return ReputationSC.KILL_WARDEN;
            default:
                return 0;
        }
    }

    /** ПГ-6, ПГ-7: the guards attack after it - a blow (tried) at a creature always, a third block violation only with the rule on. */
    public static boolean provokes(int kind, int stage, boolean aggroOnViolations) {
        return kind >= HIT_TUNER || aggroOnViolations && blockKind(kind) && stage >= STAGE_LAST;
    }

    /** How long the guards keep after the offender. */
    public static int aggroTicks(int kind) {
        return blockKind(kind) ? VIOLATION_AGGRO_TICKS : AGGRO_TICKS;
    }

    /**
     * ПГ-6: the leash of a dome of radius `domeRadius`, from its core across: LEASH, but never short of the belt's edge -
     * a City's dome (56..64) is wider than 48 itself, and its guards patrol its edge and 16 past it. Outpost: 48.
     */
    public static int leash(int domeRadius) {
        return Math.max(LEASH, domeRadius + SettlementSC.BELT);
    }

    /** The stage a block violation at `now` gets, with the two last counted at `last` and `before` (0: none) - a sliding 60 s. */
    public static int stageAt(long now, long last, long before) {
        int n = 1;
        if (last > 0L && now >= last && now - last < WINDOW_TICKS) {
            n++;
            if (before > 0L && now >= before && now - before < WINDOW_TICKS) {
                n++;
            }
        }
        return stage(n);
    }

    /** A block violation at `now` repeats the one counted at `last` (closer than REPEAT_TICKS): refused, not counted. */
    public static boolean repeats(long now, long last) {
        return last > 0L && now >= last && now - last < REPEAT_TICKS;
    }

    /**
     * A blow at a guard costs its -40 once per provocation: further blows while the guards are already after the player
     * (since `chargedAt`) cost nothing more - a fight would otherwise sink any standing in seconds. TODO(balance)
     */
    public static boolean chargesAgain(long chargedAt, long now) {
        return chargedAt <= 0L || now - chargedAt >= AGGRO_TICKS || now < chargedAt;
    }
}
