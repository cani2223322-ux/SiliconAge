package com.sc.settle.caravan;

/**
 * Подложка Ф5 (docs/plan-substrate-tech-f5.md §8, rules ПН-12..ПН-16; proposal §9 событие 7 «Караван (по желанию). Раз в 2 дня торговец
 * с двумя стражами идёт между поселениями, шанс засады 40%. Защитил — +20 репутации и редкая сделка (Модуль ПЭ-1: Широкий канал)»):
 * the caravans' numbers and pure decisions - written by the coordinator; package K may retune the TODO(balance) numbers, not the
 * contract.
 * <ul>
 * <li>one caravan every EVERY_DAYS game days in the world (a caravan day: day % EVERY_DAYS == 0), in its daytime window
 * (WINDOW_FROM..WINDOW_TO ticks into the day): it comes to a settlement with its core loaded and a real player not in creative within
 * PLAYER_REACH of the core - the first such settlement a check finds (nothing simulated without a player: a day with none - no
 * caravan, nothing owed);</li>
 * <li>it «comes from» the neighbour settlement nearest that one (the registry; none within NEIGHBOUR_MAX - from the dock's side): it
 * appears APPROACH_FROM..APPROACH_TO blocks out of the dome's edge on that side, on the plateau of the same cell (never across a
 * canyon - the Scribe lines are their «roads' end», lore: the caravans cross them by the docks' short punctures), and walks to the
 * gate: a master (EntityCaravanMasterSC - a Tuner) and GUARDS guards (EntityCaravanGuardSC - Sentries);</li>
 * <li>AMBUSH_PCT of the caravans are ambushed half-way (ambushRoll by the day): AMBUSHERS + one a player beyond the first Renegades
 * (Marauders, a Trooper from the second on - SpawnDirectorSC.spawnAt, born in Si-14) AMBUSH_DIST off its path;</li>
 * <li>the ambush beaten off (every ambusher dead, the master alive): each real player within DEFENDER_REACH of the master then -
 * REWARD_REP «Доверие» (ReputationSC.SRC_CARAVAN, no day cap) and the rare deal (the Armourer's «Модуль ПЭ-1: Широкий канал», open
 * to him RARE_DAYS game days, once - PlayerRec.extra EXTRA); the master killed - the caravan lost, nothing;</li>
 * <li>the caravan arrives (within ARRIVE_REACH of the gate) or TIMEOUT_TICKS passes - it goes into the dome (the entities leave the
 * world with the Tuners' sparks); a restart forgets a caravan under way (in memory only - as a raid).</li>
 * </ul>
 */
public final class CaravanRulesSC {

    /** ПН-12: a caravan every 2 game days, in the daytime; the check's period; the player's reach of the core. TODO(balance) */
    public static final int EVERY_DAYS = 2, WINDOW_FROM = 1000, WINDOW_TO = 9000, CHECK_TICKS = 200, PLAYER_REACH = 96;
    /** ПН-13: its start out of the dome's edge, the neighbour looked for, the guards. TODO(balance) */
    public static final int APPROACH_FROM = 40, APPROACH_TO = 64, NEIGHBOUR_MAX = 1024, GUARDS = 2;
    /** ПН-14: the ambush - its chance, its size, how far off the path. TODO(balance) */
    public static final int AMBUSH_PCT = 40, AMBUSHERS = 3, AMBUSH_DIST = 16;
    /** ПН-15: the reward - «Доверие», the defenders' reach, the rare deal's days. */
    public static final int REWARD_REP = 20, DEFENDER_REACH = 48, RARE_DAYS = 3;
    /** ПН-16: the arrival's reach of the gate, the caravan's longest life (5 min). TODO(balance) */
    public static final int ARRIVE_REACH = 6, TIMEOUT_TICKS = 6000;
    /** The registry's records: the world's (SettlementRegistrySC.extra EXTRA {day: the last caravan's day}), the player's (PlayerRec.extra EXTRA {deal: the rare deal's last day, used}). */
    public static final String EXTRA = "V", DAY = "day", DEAL_UNTIL = "deal", DEAL_USED = "used";

    private static final long SALT = 0xCA7A7A11L;

    private CaravanRulesSC() {
    }

    /** ПН-12 (pure): game day `day` is a caravan day. */
    public static boolean caravanDay(long day) {
        return day >= 0 && day % EVERY_DAYS == 0;
    }

    /** ПН-12 (pure): `dayTime` (0..23999) lies in the caravans' window. */
    public static boolean inWindow(long dayTime) {
        long t = ((dayTime % 24000L) + 24000L) % 24000L;
        return t >= WINDOW_FROM && t < WINDOW_TO;
    }

    /** ПН-14 (pure): the caravan of day `day` in a world of `seed` is ambushed. */
    public static boolean ambushRoll(long seed, long day) {
        long h = seed ^ SALT ^ (day * 0x9E3779B97F4A7C15L);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (int) ((h >>> 1) % 100) < AMBUSH_PCT;
    }

    /** ПН-14 (pure): the ambushers for `players` players. */
    public static int ambushSize(int players) {
        return AMBUSHERS + Math.max(0, players - 1);
    }

    /** ПН-15 (pure): the ambush is beaten off. */
    public static boolean defended(boolean masterAlive, int ambushersLeft) {
        return masterAlive && ambushersLeft <= 0;
    }

    /** ПН-15 (pure): the rare deal is open on `day` to a record of `until` (the last day) / `used`. */
    public static boolean dealOpen(long until, boolean used, long day) {
        return !used && day <= until;
    }
}
