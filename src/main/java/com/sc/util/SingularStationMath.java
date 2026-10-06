package com.sc.util;

/**
 * The Singular Service Station's numbers (docs/plan-singular-armor.md §7) - pure, no world, so the
 * self-test checks them directly.
 *
 * Resources, in this order everywhere: EU, singular matter, liquid helium, deuterium, krypton (R_*).
 * The plan's table is the cost of the WHOLE SET (4 pieces) going N -> N+1; one piece pays a quarter.
 *
 * ПР3 (modernisation): k pieces at once pay  sum over the pieces of row(level) / 4, and when all four
 * go at once (k == 4) x 0.8 - "the set is 20% cheaper than piece by piece". For k pieces of one level
 * that is table x k/4 x (k == 4 ? 0.8 : 1.0). Ф8 resonance (a running Singular reactor within 16
 * blocks when the process starts): EU x 0.9. Every amount is rounded up.
 * Time: the longest row among the pieces (1 / 3 / 5 / 10 min), divided by the speed:
 * (1 + 0.25 x stabilisers (up to 4)) x (resonance ? 1.3 : 1).
 *
 * Ф4 transfer: 50% of the per-piece rows the donor has climbed (levels 1 .. donor-1), time half
 * their sum. Ф5 sync: each lagging piece pays the per-piece row of every level it lacks (no ПР3);
 * time the longest lagging piece's sum. Ф3 branch change: BRANCH_SM mB of singular matter.
 * Cancelling returns half of what was drawn (refund).
 *
 * Б-1 conversion: an Exo piece -> a Singular piece of level 1, per piece CONVERT (EU, SM, He, D)
 * and CONVERT_MATERIALS (items, taken whole at the start and given back whole on «Отменить»);
 * several pieces at once - summed, the longest time. No set discount, no resonance EU discount
 * (stabilisers and resonance only speed it up).
 */
public final class SingularStationMath {

    public static final int R_EU = 0, R_SM = 1, R_HE = 2, R_D = 3, R_KR = 4, RESOURCES = 5;
    /** The gas of each resource (null: EU). */
    public static final ArmorGasSC.Gas[] GAS = {null, ArmorGasSC.Gas.SINGULAR_MATTER, ArmorGasSC.Gas.HELIUM,
            ArmorGasSC.Gas.DEUTERIUM, ArmorGasSC.Gas.KRYPTON};

    /** The plan's table, the whole set: [from level 1..4][EU, SM, He, D, Kr]. */
    private static final long[][] TABLE = {
        {50000000L, 100, 2000, 500, 0},
        {200000000L, 250, 4000, 1000, 500},
        {1000000000L, 500, 8000, 2000, 1000},
        {4000000000L, 1000, 16000, 4000, 2000},
    };
    /** Minutes per transition from level 1..4. */
    private static final int[] MINUTES = {1, 3, 5, 10};
    public static final int TICKS_PER_MINUTE = 1200;
    /** ПР3: all four pieces at once pay this share (percent). */
    public static final int SET_PERCENT = 80;
    /** Ф8: resonance - EU share (percent) and speed bonus (percent). */
    public static final int RESONANCE_EU_PERCENT = 90, RESONANCE_SPEED_PERCENT = 30;
    /** Stabilisers: +25% speed each, at most 4 counted. */
    public static final int STABILISER_PERCENT = 25, MAX_STABILISERS = 4;
    /** Ф3: a branch change costs this much singular matter, mB. */
    public static final int BRANCH_SM = 100;
    /** Ф4: the transfer pays this share (percent) of the rows. */
    public static final int TRANSFER_PERCENT = 50;
    /** The Singular core (ItemBatterySC damage) - the 4 -> 5 catalyst. */
    public static final int CORE_META = 6;
    /** The Exo core (ItemBatterySC damage) - the Exo leggings' conversion material. */
    public static final int EXO_CORE_META = 5;

    // ------------------------------------------------------------------ Б-1 conversion Exo -> Singular

    /** Conversion of one Exo piece (helmet, chestplate, leggings, boots) to a Singular piece of level 1: [EU, SM, He, D, Kr]. */
    private static final long[][] CONVERT = {
        {50000000L, 100, 2000, 0, 0},
        {150000000L, 250, 4000, 1000, 0},
        {100000000L, 150, 2000, 0, 0},
        {50000000L, 100, 2000, 0, 0},
    };
    /** Minutes per piece's conversion; several pieces at once take the longest. */
    private static final int[] CONVERT_MINUTES = {2, 4, 3, 2};
    /** The conversion's materials (M_* kinds). */
    public static final int M_CAPSULE = 0, M_LENS = 1, M_NB3SN = 2, M_FUSION = 3, M_HAFNIUM = 4, M_EXO_CORE = 5, M_SING_CORE = 6, MATERIALS = 7;
    /** Per piece type: how many of each material kind (capsules, focus lens, Nb3Sn plates, fusion core, hafnium ingots, Exo core, Singular core). */
    private static final int[][] CONVERT_MATERIALS = {
        {2, 1, 2, 0, 0, 0, 0},
        {4, 0, 2, 1, 0, 0, 1},
        {3, 0, 2, 0, 0, 1, 0},
        {2, 0, 2, 0, 2, 0, 0},
    };

    /** Б-1: the cost of converting the Exo pieces of `mask` (bit per armour type) at once - the pieces' rows summed. */
    public static long[] convertCost(int mask) {
        long[] out = new long[RESOURCES];
        for (int t = 0; t < 4; t++) {
            if ((mask & 1 << t) != 0) {
                for (int i = 0; i < RESOURCES; i++) {
                    out[i] += CONVERT[t][i];
                }
            }
        }
        return out;
    }

    /** Ticks at speed 1: the longest piece. */
    public static int convertTicks(int mask) {
        int m = 0;
        for (int t = 0; t < 4; t++) {
            if ((mask & 1 << t) != 0) {
                m = Math.max(m, CONVERT_MINUTES[t]);
            }
        }
        return m * TICKS_PER_MINUTE;
    }

    /** The materials the pieces of `mask` need, summed: count per M_* kind. */
    public static int[] convertMaterials(int mask) {
        int[] out = new int[MATERIALS];
        for (int t = 0; t < 4; t++) {
            if ((mask & 1 << t) != 0) {
                for (int i = 0; i < MATERIALS; i++) {
                    out[i] += CONVERT_MATERIALS[t][i];
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ the tool slot (docs/plan-singular-tools.md §4)

    /**
     * The tool in the station's tool slot: none, a blade, a drill. Its conversion (Exo -> Singular, level 1) and its
     * modernisation join the armour's process (one button, summed costs, the longest time, its own bit in the mask).
     *  - conversion: blade 400M EU, 1000 SM, 4000 He, 1 Singular core, 4 Nb3Sn plates, 3 min;
     *                drill 200M EU, 600 SM, 2000 He, 1 Singular core, 2 Nb3Sn plates, 2 min (materials whole, as Б-1);
     *  - modernisation N -> N+1: TOOL_SHARE percent of the whole set's row (blade 50%, drill 25% - a piece's), its
     *    minutes, the core catalyst for 4 -> 5 as the armour; resonance EU x 0.9 (the drill's share with the armour's);
     *  - the drill's modernisation: Singular crumbs in the material slots pay up to CRUMB_MAX_PERCENT of its SM,
     *    CRUMB_SM mB each (taken whole at the start, given back whole on «Отменить»);
     *  - branch re-choice: BRANCH_SM, as the chestplate.
     */
    public static final int TOOL_NONE = 0, TOOL_BLADE = 1, TOOL_DRILL = 2;
    private static final long[][] TOOL_CONVERT = {
        {400000000L, 1000, 4000, 0, 0},
        {200000000L, 600, 2000, 0, 0},
    };
    private static final int[] TOOL_CONVERT_MINUTES = {3, 2};
    private static final int[][] TOOL_CONVERT_MATERIALS = {
        {0, 0, 4, 0, 0, 0, 1},
        {0, 0, 2, 0, 0, 0, 1},
    };
    /** Modernisation: the tool pays this percent of the whole set's row (blade, drill). */
    private static final int[] TOOL_SHARE = {50, 25};
    /** A crumb pays this much SM (mB); crumbs pay at most this percent of the drill's SM. */
    public static final int CRUMB_SM = 10, CRUMB_MAX_PERCENT = 50;

    private static boolean toolKind(int tool) {
        return tool == TOOL_BLADE || tool == TOOL_DRILL;
    }

    /** The conversion of an Exo tool `tool` (TOOL_*): [EU, SM, He, D, Kr]; none: zeros. */
    public static long[] toolConvertCost(int tool) {
        long[] out = new long[RESOURCES];
        if (toolKind(tool)) {
            System.arraycopy(TOOL_CONVERT[tool - 1], 0, out, 0, RESOURCES);
        }
        return out;
    }

    public static int toolConvertTicks(int tool) {
        return toolKind(tool) ? TOOL_CONVERT_MINUTES[tool - 1] * TICKS_PER_MINUTE : 0;
    }

    public static int[] toolConvertMaterials(int tool) {
        int[] out = new int[MATERIALS];
        if (toolKind(tool)) {
            System.arraycopy(TOOL_CONVERT_MATERIALS[tool - 1], 0, out, 0, MATERIALS);
        }
        return out;
    }

    /** Б-1 with the tool: the armour pieces of `mask` and the Exo tool `tool`, summed. */
    public static long[] convertCost(int mask, int tool) {
        long[] out = convertCost(mask), t = toolConvertCost(tool);
        for (int i = 0; i < RESOURCES; i++) {
            out[i] += t[i];
        }
        return out;
    }

    public static int convertTicks(int mask, int tool) {
        return Math.max(convertTicks(mask), toolConvertTicks(tool));
    }

    public static int[] convertMaterials(int mask, int tool) {
        int[] out = convertMaterials(mask), t = toolConvertMaterials(tool);
        for (int i = 0; i < MATERIALS; i++) {
            out[i] += t[i];
        }
        return out;
    }

    /** The tool `tool` going from `level` (1..4) to the next: its share of the set's row; resonance EU x 0.9. */
    public static long[] toolModerniseCost(int tool, int level, boolean resonance) {
        long[] out = new long[RESOURCES];
        if (!toolKind(tool) || level < 1 || level > 4) {
            return out;
        }
        long[] r = row(level);
        for (int i = 0; i < RESOURCES; i++) {
            out[i] = ceilShare(r[i], TOOL_SHARE[tool - 1], 100);
        }
        if (resonance) {
            out[R_EU] = ceilShare(out[R_EU], RESONANCE_EU_PERCENT, 100);
        }
        return out;
    }

    public static int toolModerniseTicks(int tool, int level) {
        return toolKind(tool) ? minutes(level) * TICKS_PER_MINUTE : 0;
    }

    /** Crumbs the drill's modernisation takes when `have` lie in the slots and its SM part is `drillSm`: at most 50% of it. */
    public static int crumbsUsable(long drillSm, int have) {
        long max = drillSm * CRUMB_MAX_PERCENT / 100 / CRUMB_SM;
        return (int) Math.max(0L, Math.min(have, max));
    }

    /**
     * The modernisation of the armour pieces at `levels` (as moderniseCost) plus the tool `tool` at `toolLevel` (0: not
     * taking part), `crumbs` of them paying the drill's SM (crumbsUsable already applied by the caller).
     */
    public static long[] moderniseCost(int[] levels, int tool, int toolLevel, int crumbs, boolean resonance) {
        long[] out = moderniseCost(levels, resonance), t = toolModerniseCost(tool, toolLevel, resonance);
        for (int i = 0; i < RESOURCES; i++) {
            out[i] += t[i];
        }
        if (tool == TOOL_DRILL && crumbs > 0) {
            out[R_SM] = Math.max(0L, out[R_SM] - Math.min((long) crumbs * CRUMB_SM, t[R_SM] * CRUMB_MAX_PERCENT / 100));
        }
        return out;
    }

    public static int moderniseTicks(int[] levels, int tool, int toolLevel) {
        return Math.max(moderniseTicks(levels), toolModerniseTicks(tool, toolLevel));
    }

    private SingularStationMath() {
    }

    /** The whole set's row for going from `level` (1..4) to the next; zeros otherwise. */
    public static long[] row(int level) {
        long[] out = new long[RESOURCES];
        if (level >= 1 && level <= 4) {
            System.arraycopy(TABLE[level - 1], 0, out, 0, RESOURCES);
        }
        return out;
    }

    public static int minutes(int level) {
        return level >= 1 && level <= 4 ? MINUTES[level - 1] : 0;
    }

    /** ceil(a * num / den) for non-negative a (no overflow for the table's sizes). */
    public static long ceilShare(long a, long num, long den) {
        if (a <= 0 || num <= 0) {
            return 0;
        }
        java.math.BigInteger p = java.math.BigInteger.valueOf(a).multiply(java.math.BigInteger.valueOf(num));
        java.math.BigInteger[] qr = p.divideAndRemainder(java.math.BigInteger.valueOf(den));
        return qr[0].longValue() + (qr[1].signum() > 0 ? 1 : 0);
    }

    /**
     * ПР1 + ПР3: the cost of taking the pieces of `levels` (0 = no piece / not taking part; 1..4 the
     * level it leaves) up one level each, all at once. Resonance: EU x 0.9.
     */
    public static long[] moderniseCost(int[] levels, boolean resonance) {
        long[] sum = new long[RESOURCES];
        int k = 0;
        for (int lvl : levels) {
            if (lvl >= 1 && lvl <= 4) {
                k++;
                long[] r = row(lvl);
                for (int i = 0; i < RESOURCES; i++) {
                    sum[i] += r[i];
                }
            }
        }
        int pct = k >= 4 ? SET_PERCENT : 100;
        long[] out = new long[RESOURCES];
        for (int i = 0; i < RESOURCES; i++) {
            out[i] = ceilShare(sum[i], pct, 400);
        }
        if (resonance) {
            out[R_EU] = ceilShare(out[R_EU], RESONANCE_EU_PERCENT, 100);
        }
        return out;
    }

    /** Pieces taking part (a level 1..4). */
    public static int pieces(int[] levels) {
        int k = 0;
        for (int lvl : levels) {
            k += lvl >= 1 && lvl <= 4 ? 1 : 0;
        }
        return k;
    }

    /** Ticks at speed 1: the longest row among the pieces. */
    public static int moderniseTicks(int[] levels) {
        int m = 0;
        for (int lvl : levels) {
            m = Math.max(m, minutes(lvl));
        }
        return m * TICKS_PER_MINUTE;
    }

    /** Some piece goes 4 -> 5: the Singular core is needed. */
    public static boolean needsCatalyst(int[] levels) {
        for (int lvl : levels) {
            if (lvl == 4) {
                return true;
            }
        }
        return false;
    }

    /** The speed: (1 + 0.25 per stabiliser, up to 4) x 1.3 with resonance. */
    public static double speed(int stabilisers, boolean resonance) {
        int n = Math.max(0, Math.min(MAX_STABILISERS, stabilisers));
        return (1.0 + n * STABILISER_PERCENT / 100.0) * (resonance ? 1.0 + RESONANCE_SPEED_PERCENT / 100.0 : 1.0);
    }

    /** Ticks a process of `baseTicks` takes at `speed`. */
    public static int duration(int baseTicks, double speed) {
        return speed <= 0 ? Integer.MAX_VALUE : (int) Math.ceil(baseTicks / speed - 1e-9);
    }

    /** Ф4: the donor's level (2..5) moves to a level-1 piece: 50% of the per-piece rows 1 .. donor-1. */
    public static long[] transferCost(int donorLevel) {
        long[] sum = new long[RESOURCES];
        for (int l = 1; l < donorLevel && l <= 4; l++) {
            long[] r = row(l);
            for (int i = 0; i < RESOURCES; i++) {
                sum[i] += r[i];
            }
        }
        long[] out = new long[RESOURCES];
        for (int i = 0; i < RESOURCES; i++) {
            out[i] = ceilShare(sum[i], TRANSFER_PERCENT, 400);
        }
        return out;
    }

    public static int transferTicks(int donorLevel) {
        int m = 0;
        for (int l = 1; l < donorLevel && l <= 4; l++) {
            m += minutes(l);
        }
        return m * TICKS_PER_MINUTE / 2;
    }

    /** The highest level among `levels` (0 = no piece). */
    public static int top(int[] levels) {
        int t = 0;
        for (int l : levels) {
            t = Math.max(t, l);
        }
        return t;
    }

    /** Ф5: every piece below the highest pays the per-piece row of each level it lacks (no ПР3). */
    public static long[] syncCost(int[] levels) {
        int top = top(levels);
        long[] sum = new long[RESOURCES];
        for (int lvl : levels) {
            for (int l = Math.max(1, lvl); lvl > 0 && l < top; l++) {
                long[] r = row(l);
                for (int i = 0; i < RESOURCES; i++) {
                    sum[i] += r[i];
                }
            }
        }
        long[] out = new long[RESOURCES];
        for (int i = 0; i < RESOURCES; i++) {
            out[i] = ceilShare(sum[i], 1, 4);
        }
        return out;
    }

    public static int syncTicks(int[] levels) {
        int top = top(levels), m = 0;
        for (int lvl : levels) {
            int s = 0;
            for (int l = Math.max(1, lvl); lvl > 0 && l < top; l++) {
                s += minutes(l);
            }
            m = Math.max(m, s);
        }
        return m * TICKS_PER_MINUTE;
    }

    /** Cancelling: half of what was drawn comes back (rounded down). */
    public static long refund(long drawn) {
        return Math.max(0L, drawn) / 2;
    }

    /** What has to be drawn of each resource to stand at progress `p` (0..1): ceil(cost x p) - drawn, never below 0. */
    public static long[] needFor(long[] cost, long[] drawn, double p) {
        long[] out = new long[RESOURCES];
        double q = Math.max(0.0, Math.min(1.0, p));
        for (int i = 0; i < RESOURCES; i++) {
            long target = q >= 1.0 ? cost[i] : Math.min(cost[i], (long) Math.ceil(cost[i] * q - 1e-9));
            out[i] = Math.max(0L, target - drawn[i]);
        }
        return out;
    }

    /** ПР4: the progress the drawn resources pay for, at most `cap` (every resource's drawn / cost; none needed: cap). */
    public static double progressOf(long[] cost, long[] drawn, double cap) {
        double p = Math.max(0.0, Math.min(1.0, cap));
        for (int i = 0; i < RESOURCES; i++) {
            if (cost[i] > 0) {
                p = Math.min(p, drawn[i] >= cost[i] ? 1.0 : drawn[i] / (double) cost[i]);
            }
        }
        return p;
    }

    /** A colour scheme `dir` steps on (wraps round), for the station's ◄ ►. */
    public static SingularScheme cycle(SingularScheme s, int dir) {
        SingularScheme[] v = SingularScheme.values();
        int n = v.length;
        int i = ((s == null ? 0 : s.ordinal()) + dir) % n;
        return v[(i + n) % n];
    }

    /** "1,24 млрд"-style short amount for the screen: plain below 10 000, then тыс / млн / млрд. */
    public static String shortAmount(long v, String th, String mln, String bln) {
        long a = Math.abs(v);
        if (a < 10000) {
            return String.valueOf(v);
        }
        double d;
        String u;
        if (a >= 1000000000L) {
            d = v / 1e9;
            u = bln;
        } else if (a >= 1000000L) {
            d = v / 1e6;
            u = mln;
        } else {
            d = v / 1e3;
            u = th;
        }
        String num = Math.abs(d) >= 100 ? String.valueOf(Math.round(d))
                : String.format(java.util.Locale.ROOT, Math.abs(d) >= 10 ? "%.1f" : "%.2f", d).replace('.', ',');
        if (num.contains(",")) {
            while (num.endsWith("0")) {
                num = num.substring(0, num.length() - 1);
            }
            if (num.endsWith(",")) {
                num = num.substring(0, num.length() - 1);
            }
        }
        return num + " " + u;
    }
}
