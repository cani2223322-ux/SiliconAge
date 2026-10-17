package com.sc.settle.trade;

import com.sc.settle.ReputationSC;

/**
 * PACKAGE T (docs/plan-substrate-tech-f2.md §9.5, rules ПО-4..ПО-7): the deals' prices and limits as pure numbers
 * (proposal §7 «Лимиты», «Чтобы торговля не обесценила крафт»). The self-test and the handbook call them; the
 * signatures are fixed, the bodies package T's.
 * <p>
 * The player's side of a deal is what the level and the settlement scale (ПО-6, the coordinator's §15 «Цена»): the
 * goods a player sells, the chips a player pays - never the Tuner's side, never the extra goods of a chips deal (the
 * one wafer of «6 КЧ + 1 пластина»), never a «Доверие» deal (the penance is 16 chips for everyone - ПД-4 - and a
 * Stolen Cargo is one crate).
 */
public final class TradeRulesSC {

    /** ПО-5, per player UUID over every settlement: chips given out a day, contracts taken a day, Singularity Crumbs a week. */
    public static final int CHIPS_PER_DAY = 64, CONTRACTS_PER_DAY = 3, CRUMBS_PER_WEEK = 1;
    /** ПО-3: a settlement's prices differ by up to this, % (its seed and the deal's key). */
    public static final int SEED_SPREAD_PCT = 10;
    /** ПО-7: what a Tuner sells costs at least this many times what it pays for the same worth. TODO(balance) */
    public static final int BUY_OVER_SELL = 2;
    /** The most deals one press of «Обменять» with Shift asks for (the server stops earlier at a limit or a lack). */
    public static final int MAX_TIMES = 64;

    private TradeRulesSC() {
    }

    /** ПО-3: the settlement's spread for one deal, -SEED_SPREAD_PCT..+SEED_SPREAD_PCT (pure). */
    public static int seedPct(long settlementSeed, String key) {
        long h = settlementSeed ^ 0x5DEECE66DL;
        if (key != null) {
            for (int i = 0; i < key.length(); i++) {
                h = h * 31L + key.charAt(i);
            }
        }
        // a 64-bit finaliser (MurmurHash3's): neighbouring seeds and keys land far apart
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        int span = 2 * SEED_SPREAD_PCT + 1;
        return (int) ((h % span + span) % span) - SEED_SPREAD_PCT;
    }

    /**
     * ПО-6: the count of the player's side of a deal (`base` of the table) at `level` in a settlement of `seed`: base x the
     * level's multiplier x (100 + seedPct) / 100, rounded up, at least 1.
     */
    public static int playerSide(int base, int level, long seed, String key) {
        return playerSide(base, level, seedPct(seed, key));
    }

    /**
     * The same with the spread given (`pct`; 0 - an unbound Tuner from an egg: the table's prices). Whole numbers only (the
     * multipliers are hundredths), so 16 at «Чужак» is 24 and at «Почётный» 14 on every machine.
     */
    public static int playerSide(int base, int level, int pct) {
        long hundredths = Math.round(ReputationSC.priceMultiplier(level) * 100F);
        long num = (long) Math.max(1, base) * hundredths * (100 + pct);
        long out = (num + 9999L) / 10000L;
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, out));
    }

    /** ПО-6: the level and the spread scale this side of `o` (see the class comment). */
    public static boolean scales(TradeOfferSC o, TradeOfferSC.Ingredient i) {
        if (o == null || i == null || o.outKind == TradeOfferSC.OUT_REPUTATION) {
            return false;
        }
        return !o.buy() || i.isChips();
    }

    /** What the player gives for one deal of `o` at `level` with spread `pct`: the counts of `o.in`, in order. */
    public static int[] playerCounts(TradeOfferSC o, int level, int pct) {
        int[] n = new int[o.in.length];
        for (int k = 0; k < o.in.length; k++) {
            TradeOfferSC.Ingredient i = o.in[k];
            n[k] = i == null ? 0 : scales(o, i) ? playerSide(i.count, level, pct) : i.count;
        }
        return n;
    }

    /**
     * ПО-3, ПД-1: the deal is open to a player of `level` - its own level, and by the level: paying chips for goods needs
     * ReputationSC.buys (a «Нарушитель» only sells); a «Доверие» deal (the penance, a Stolen Cargo back) and a sale need
     * ReputationSC.sells - a «Нарушитель» must be able to make amends. A local «Враг»: nothing.
     */
    public static boolean available(TradeOfferSC o, int level) {
        if (o == null || level < o.minLevel) {
            return false;
        }
        boolean goodsForChips = o.buy() && o.outKind != TradeOfferSC.OUT_REPUTATION;
        return goodsForChips ? ReputationSC.buys(level) : ReputationSC.sells(level);
    }

    /** ПО-4, ПО-5: how many more times today (a global deal: this period) - the limit less what is used. */
    public static int remaining(TradeOfferSC o, int used) {
        return o == null ? 0 : Math.max(0, o.limit - used);
    }

    /** ПО-5: of `want` chips, how many may still be given out today with `given` already. */
    public static int chipsAllowed(int given, int want) {
        return Math.max(0, Math.min(want, CHIPS_PER_DAY - given));
    }

    /** A global deal's period index on game day `day`. */
    public static long period(long day, int periodDays) {
        return day / Math.max(1, periodDays);
    }
}
