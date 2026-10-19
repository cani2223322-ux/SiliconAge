package com.sc.settle;

import com.sc.util.ArmorSuit;

/**
 * PACKAGE C (docs/plan-substrate-tech-f2.md §7, rules ПД-1..ПД-6): «Доверие» as pure numbers - the levels, the price
 * multipliers, what raises and lowers it, the daily caps. Global per player UUID (the Cartographers «carry the news»),
 * plus a local «Враг» status at one settlement (SettlementRegistrySC.LocalRec.enemyUntil). The proposal's numbers
 * (§5 «Репутация «Доверие»»); open ones TODO(balance). Written by the coordinator from the table; package C may tune
 * the numbers and add helpers, the signatures stay (the self-test and packages T, N, B call them).
 * <p>
 * Ф6 (docs/plan-substrate-tech-f6.md §2.6, rule ПД-7, the coordinator's): «Доверие» earned at an embassy in the Overworld - its deals,
 * contracts, the Pass, the penance at the Ambassador - never lifts a player past EMBASSY_CEILING (599: the top of «Партнёр»); «Свой»
 * and «Почётный» come from deeds in the Substrate only. give(..., ceiling) holds it; ceiling(s) says which a record has. A player
 * already over it keeps what he has (a capped gain is 0, never a loss); penalties have no ceiling.
 */
public final class ReputationSC {

    /** «Доверие» is kept within these. TODO(balance) */
    public static final int MIN = -1000, MAX = 5000;

    /** Levels (ПД-1). ENEMY is only ever local (localLevel). Lang sc.settle.rep.<LEVEL_KEYS[level]>. */
    public static final int ENEMY = 0, VIOLATOR = 1, STRANGER = 2, ACQUAINTANCE = 3, PARTNER = 4, OWN = 5, HONOURED = 6;
    public static final String[] LEVEL_KEYS = {"enemy", "violator", "stranger", "acquaintance", "partner", "own", "honoured"};
    /** Where STRANGER .. HONOURED start (below 0: VIOLATOR). */
    public static final int STRANGER_FROM = 0, ACQUAINTANCE_FROM = 100, PARTNER_FROM = 300, OWN_FROM = 600, HONOURED_FROM = 1000;
    /** ПГ-8: a penalty taken at a settlement that leaves «Доверие» below this makes the player its «Враг». */
    public static final int ENEMY_BELOW = -50;
    /** ПД-1: the «Враг» status lasts 3 game days. */
    public static final long ENEMY_TICKS = 3L * SettlementSC.DAY;
    /** ПО-6: what the player's side of a deal is multiplied by, by level (ENEMY: no trade at all). */
    public static final float[] PRICE = {0F, 2.0F, 1.5F, 1.25F, 1.0F, 0.9F, 0.85F};

    /** ПД-2: a deal +1, at most +20 a day from deals. */
    public static final int TRADE = 1, TRADE_DAY_CAP = 20;
    /** ПД-2: a hostile born in Si-14 killed near a settlement, by its armour (Nano, Quantum, Exo, Singular); the small ones (Nano, Quantum) at most +20 a day. TODO(balance) */
    public static final int[] KILL_BY_SUIT = {1, 3, 8, 25};
    public static final int KILL_SMALL_DAY_CAP = 20;
    /** ПД-2: a contract +10..+40 (the contract's own), a Stolen Cargo back +15. */
    public static final int CONTRACT_MIN = 10, CONTRACT_MAX = 40, CARGO = 15;
    /** ПД-5: «Пропуск Наладчиков» - the first visit: +20 and 8 Credit Chips. */
    public static final int PASS = 20, PASS_CHIPS = 8;
    /** ПД-4: penance - 16 chips to an Elder give +50, once a game day. */
    public static final int PENANCE = 50, PENANCE_CHIPS = 16;
    /** §4 «Цена»: the world's first Interdimensional Anchor in the Substrate - 20 chips and +50 to who placed it. */
    public static final int ANCHOR = 50, ANCHOR_CHIPS = 20;

    /** ПД-3: a violation -5 (the second within 60 s), -10 (the third and on); an attempt on a Tuner -10; a blow at a guard -40; a guard killed -60 and -10% of what is left. */
    public static final int VIOLATION = -5, VIOLATION_LAST = -10, HIT_TUNER = -10, HIT_WARDEN = -40, KILL_WARDEN = -60, KILL_WARDEN_PCT = 10;

    /** Where a change comes from (the caps go by it). */
    public static final int SRC_TRADE = 0, SRC_KILL_SMALL = 1, SRC_KILL_BIG = 2, SRC_CONTRACT = 3, SRC_CARGO = 4, SRC_PASS = 5,
            SRC_PENANCE = 6, SRC_ANCHOR = 7, SRC_PENALTY = 8;
    /** Ф4 (docs/plan-substrate-tech-f4.md §6.1, ПН-3, the coordinator's): a raid beaten off (RaidRulesSC.REP: +30..+50, no day cap). */
    public static final int SRC_RAID = 9;
    /** Ф5 (docs/plan-substrate-tech-f5.md §8, ПН-15, the coordinator's): a caravan's ambush beaten off (CaravanRulesSC.REWARD_REP, no day cap). */
    public static final int SRC_CARAVAN = 10;
    /** Ф6 (ПД-7): what an embassy's «Доверие» can lift a player to at most - the top of «Партнёр» (OWN_FROM - 1). */
    public static final int EMBASSY_CEILING = OWN_FROM - 1;
    /** Ф6: no ceiling (a Substrate settlement's). */
    public static final int NO_CEILING = Integer.MAX_VALUE;

    private ReputationSC() {
    }

    /** ПД-1: the level of `rep` points (never ENEMY: that one is local). */
    public static int level(int rep) {
        if (rep < STRANGER_FROM) {
            return VIOLATOR;
        }
        if (rep < ACQUAINTANCE_FROM) {
            return STRANGER;
        }
        if (rep < PARTNER_FROM) {
            return ACQUAINTANCE;
        }
        if (rep < OWN_FROM) {
            return PARTNER;
        }
        return rep < HONOURED_FROM ? OWN : HONOURED;
    }

    /** The level at one settlement: its «Враг» status wins. */
    public static int localLevel(int rep, boolean enemy) {
        return enemy ? ENEMY : level(rep);
    }

    /** The level's lang key, sc.settle.rep.<LEVEL_KEYS[level]> (a bad level: STRANGER's). */
    public static String levelKey(int level) {
        return "sc.settle.rep." + LEVEL_KEYS[level >= 0 && level < LEVEL_KEYS.length ? level : STRANGER];
    }

    /** «Доверие» as the chat shows a change: "+20", "-5", "0". */
    public static String signed(int n) {
        return n > 0 ? "+" + n : String.valueOf(n);
    }

    /** ПО-6: the multiplier of the player's side of a deal at `level` (ENEMY: 0 - closed). */
    public static float priceMultiplier(int level) {
        return PRICE[level >= 0 && level < PRICE.length ? level : STRANGER];
    }

    /** ПД-1: the player may buy from Tuners (a VIOLATOR only sells to them, an ENEMY does nothing). */
    public static boolean buys(int level) {
        return level >= STRANGER;
    }

    /** The player may sell to Tuners. */
    public static boolean sells(int level) {
        return level >= VIOLATOR;
    }

    public static int clamp(int rep) {
        return Math.max(MIN, Math.min(MAX, rep));
    }

    /** Of `add` (> 0), what still fits under a daily `cap` with `already` given today. */
    public static int capped(int already, int add, int cap) {
        return add <= 0 ? add : Math.max(0, Math.min(add, cap - already));
    }

    /** ПД-2: «Доверие» for a kill of a hostile in `suit`'s armour (null: none - the smallest). */
    public static int killReward(ArmorSuit suit) {
        int i = suit == null ? 0 : suit.ordinal();
        return KILL_BY_SUIT[Math.max(0, Math.min(KILL_BY_SUIT.length - 1, i))];
    }

    /** The «small ones» whose kills share the daily cap (Nano, Quantum). */
    public static boolean smallKill(ArmorSuit suit) {
        return suit == null || suit == ArmorSuit.NANO || suit == ArmorSuit.QUANTUM;
    }

    /**
     * Applies a change of `amount` from `source` to the player's record on game day `day`: the day's counters roll, the
     * caps of SRC_TRADE and SRC_KILL_SMALL hold, the result is clamped. Pure but for `p`. @return what was applied
     */
    public static int give(SettlementRegistrySC.PlayerRec p, int amount, int source, long day) {
        p.rollDay(day);
        int a = amount;
        if (source == SRC_TRADE) {
            a = capped(p.repTrades, amount, TRADE_DAY_CAP);
            p.repTrades += Math.max(0, a);
        } else if (source == SRC_KILL_SMALL) {
            a = capped(p.repKills, amount, KILL_SMALL_DAY_CAP);
            p.repKills += Math.max(0, a);
        }
        int before = p.reputation;
        p.reputation = clamp(before + a);
        return p.reputation - before;
    }

    /**
     * Ф6 (ПД-7): give() under a ceiling - a gain stops at `ceiling` (a player already at or over it gains 0, never loses), a loss is
     * applied as it is. The day's caps hold as in give(). Pure but for `p`. @return what was applied
     */
    public static int give(SettlementRegistrySC.PlayerRec p, int amount, int source, long day, int ceiling) {
        if (amount <= 0 || ceiling >= MAX) {
            return give(p, amount, source, day);
        }
        int room = Math.max(0, ceiling - p.reputation);
        if (room <= 0) {
            p.rollDay(day);                                     // the day's counters still roll (nothing is counted: nothing given)
            return 0;
        }
        return give(p, Math.min(amount, room), source, day);
    }

    /** Ф6 (ПД-7): the ceiling of «Доверие» earned at settlement `s` - an embassy's EMBASSY_CEILING, else NO_CEILING. */
    public static int ceiling(SettlementRegistrySC.Settlement s) {
        return SettlementSC.isEmbassy(s) ? EMBASSY_CEILING : NO_CEILING;
    }

    /** ПД-3: «Доверие» after killing a guard: -60, then -10% (rounded up) of what is left if it is still positive. */
    public static int afterWardenKill(int rep) {
        int r = rep + KILL_WARDEN;
        if (r > 0) {
            r -= (r * KILL_WARDEN_PCT + 99) / 100;
        }
        return clamp(r);
    }
}
