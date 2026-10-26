package com.sc.arena;

/**
 * ПД-6 (docs/plan-ideas-2026-10-26.md, «Протоколы Пробоя», ★ A): the protocols' numbers and pure decisions - the coordinator's names and
 * signatures, ПД-6's bodies and TODO(balance) numbers. A protocol is a bit of a mask (bit(p) = 1 << p); the arena's record keeps the
 * mask in its extra (ArenaDataSC.Arena.extra EXTRA {m: mask}) from the insertion to the Warden's fall (spent with the Key) or the reset
 * (kept with the Key).
 * <ul>
 * <li>DEAF_COILS «Глухие катушки» - a ПЭ-1 Point no longer darkens a Containment Coil (only its reserve shot away breaks it);</li>
 * <li>SHORT_FUSE «Короткий фитиль» - «Перегрузка»'s time to finish SHORT_FUSE_TICKS (35 s) instead of WardenRulesSC.FINISH_TICKS (60 s);</li>
 * <li>SWARM «Нашествие» - the Hollow Shell waves x SWARM_MUL (the wave's size and its cap);</li>
 * <li>ECLIPSE_SHADOW «Тень Затмения» - at SHADOW_AT of its health a copy of «Затмение» comes down into the pit (no loot of its own);</li>
 * <li>JAMMING «Помехи» - every JAMMING_EVERY ticks an EMP pulse over the whole pit (the storm's pulse: a piece of every player's armour
 * under it suppressed; the 20 s immunity holds).</li>
 * </ul>
 * Each protocol: +CRUMBS_EACH Singularity Crumbs to every looter and +TROPHY_BONUS_EACH to the trophies' chance; all MAX at once - the
 * Singular scheme «Пробой» (SingularScheme.N) unlocked for good for every looter. FORBIDDEN pairs never go in together (allowed()).
 */
public final class BreachProtocolsSC {

    public static final int DEAF_COILS = 0, SHORT_FUSE = 1, SWARM = 2, ECLIPSE_SHADOW = 3, JAMMING = 4;
    /** Metas of ItemBreachProtocolSC (append only). */
    public static final int COUNT = 5;
    /** The lang / texture keys by meta: sc.protocol.<key>, items/breachProtocol_<key>. */
    public static final String[] KEYS = {"deafCoils", "shortFuse", "swarm", "eclipseShadow", "jamming"};
    /** At most this many in one fight. */
    public static final int MAX = 3;
    /** The arena record's extra key, and its field (the mask). */
    public static final String EXTRA = "P", MASK = "m";
    /** Each protocol: Crumbs to every looter, the trophies' chance. TODO(balance) */
    public static final int CRUMBS_EACH = 2;
    public static final float TROPHY_BONUS_EACH = 0.25F;
    /** SHORT_FUSE: 35 s to finish; SWARM: x1.5 Shells; ECLIPSE_SHADOW: at half its health; JAMMING: a pulse every 25 s. TODO(balance) */
    public static final int SHORT_FUSE_TICKS = 700, JAMMING_EVERY = 500;
    public static final float SWARM_MUL = 1.5F, SHADOW_AT = 0.5F;
    /** The pairs refused together (a death no one escapes in the reckoning - ПД-6's self-test). TODO(balance) */
    public static final int[][] FORBIDDEN = {{DEAF_COILS, JAMMING}, {SHORT_FUSE, ECLIPSE_SHADOW}};
    /**
     * ПД-6: the reckoning of a mask's danger - each protocol's own (DEAF 3, SHORT 2, SWARM 2, SHADOW 3, JAMMING 3), the pairs' extra
     * (a FORBIDDEN pair +FORBIDDEN_DANGER: never within DANGER_MAX; the shells and the deaf coils, the shells and the short fuse, the
     * shadow and the jamming +1), at most DANGER_MAX for a fight that can be won. TODO(balance)
     */
    public static final int[] DANGER = {3, 2, 2, 3, 3};
    public static final int DANGER_MAX = 9, FORBIDDEN_DANGER = 6;
    public static final int[][] PAIR_DANGER = {{DEAF_COILS, SWARM, 1}, {SHORT_FUSE, SWARM, 1}, {ECLIPSE_SHADOW, JAMMING, 1}};

    private BreachProtocolsSC() {
    }

    /** The key of meta `meta` (an unknown one: the first). */
    public static String keyOf(int meta) {
        return KEYS[meta >= 0 && meta < KEYS.length ? meta : 0];
    }

    /** The bit of protocol `p` in a mask (0 for an unknown one). */
    public static int bit(int p) {
        return p >= 0 && p < COUNT ? 1 << p : 0;
    }

    /** How many protocols a mask holds. */
    public static int count(int mask) {
        return Integer.bitCount(mask & ((1 << COUNT) - 1));
    }

    /** Pure: the mask may go into the Console - at most MAX, no FORBIDDEN pair, its danger within DANGER_MAX. */
    public static boolean allowed(int mask) {
        if (count(mask) > MAX || (mask & ~((1 << COUNT) - 1)) != 0) {
            return false;
        }
        return forbiddenPair(mask) == null && danger(mask) <= DANGER_MAX;
    }

    /** Pure: the first FORBIDDEN pair the mask holds ({a, b}), or null. */
    public static int[] forbiddenPair(int mask) {
        for (int[] pair : FORBIDDEN) {
            if ((mask & bit(pair[0])) != 0 && (mask & bit(pair[1])) != 0) {
                return pair;
            }
        }
        return null;
    }

    /** ПД-6 (pure): the mask's danger - the protocols' own and the pairs' extra (FORBIDDEN_DANGER for a forbidden pair). */
    public static int danger(int mask) {
        int d = 0;
        for (int p = 0; p < COUNT; p++) {
            if ((mask & bit(p)) != 0) {
                d += DANGER[p];
            }
        }
        for (int[] pair : FORBIDDEN) {
            if ((mask & bit(pair[0])) != 0 && (mask & bit(pair[1])) != 0) {
                d += FORBIDDEN_DANGER;
            }
        }
        for (int[] pair : PAIR_DANGER) {
            if ((mask & bit(pair[0])) != 0 && (mask & bit(pair[1])) != 0) {
                d += pair[2];
            }
        }
        return d;
    }

    /** ПД-6 (pure): the protocol `p` is in the mask. */
    public static boolean has(int mask, int p) {
        return (mask & bit(p)) != 0;
    }

    /** Pure: the extra Crumbs a looter gets for the mask. */
    public static int crumbs(int mask) {
        return CRUMBS_EACH * count(mask);
    }

    /** Pure: the trophies' chance multiplier for the mask (1 + 0.25 a protocol). */
    public static float trophyMul(int mask) {
        return 1F + TROPHY_BONUS_EACH * count(mask);
    }

    /** Pure: the mask unlocks the scheme «Пробой» (MAX protocols at once). */
    public static boolean scheme(int mask) {
        return count(mask) >= MAX;
    }
}
