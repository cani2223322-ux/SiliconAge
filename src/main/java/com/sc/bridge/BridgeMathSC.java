package com.sc.bridge;

import com.sc.util.ArmorGasSC.Gas;

/**
 * The Ground / Space Bridge's numbers (docs/plan-ground-bridge.md §4-§6, §3.3, §7а) - pure, the self-test
 * checks them. Every figure the controller, the screen, the book and WAILA show comes from here.
 *
 * Stage 2/3 hooks (kept as parameters / constants now so the data does not change shape later):
 * the projected ends of the modes ДР1-ДР5 (projections), familiar places (С5, familiarPercent), wear (С2),
 * mass (С4), interference (С6), scouting (С7).
 */
public final class BridgeMathSC {

    private BridgeMathSC() {
    }

    /** The bridge kinds (ring sizes): small 3x3 and big 9x9 are hooks only (plan §3.1 / §3.2 "по желанию"). */
    public static final int GROUND = 0, SPACE = 1;
    /** The ring's outer size and the vortex inside it. */
    public static final int GROUND_RING = 5, SPACE_RING = 7;

    public static int ringSize(int kind) {
        return kind == SPACE ? SPACE_RING : GROUND_RING;
    }

    /** The vortex is the ring's inside: 3x3 / 5x5. */
    public static int vortexSize(int kind) {
        return ringSize(kind) - 2;
    }

    // ------------------------------------------------------------------ the tanks (§5)

    /** The bridge's shared tanks, in the screen's order. */
    public static final Gas[] GASES = {Gas.HELIUM, Gas.ARGON, Gas.KRYPTON, Gas.DEUTERIUM, Gas.HEAVY_WATER, Gas.SINGULAR_MATTER};
    public static final int HE = 0, AR = 1, KR = 2, D = 3, D2O = 4, SM = 5;
    public static final int[] BASE_TANK = {32000, 16000, 8000, 16000, 16000, 16000};
    /** Each gas port beyond the first: +50% to every tank. */
    public static final int EXTRA_PORT_PERCENT = 50;

    /** Tank `i` with `gasPorts` gas ports (none counts as one: the tanks keep their base size). */
    public static int tankCapacity(int i, int gasPorts) {
        int extra = Math.max(0, gasPorts - 1);
        return (int) Math.min(Integer.MAX_VALUE, (long) BASE_TANK[i] * (100 + EXTRA_PORT_PERCENT * extra) / 100);
    }

    /** The tank index of a gas, or -1. */
    public static int tankOf(Gas g) {
        for (int i = 0; i < GASES.length; i++) {
            if (GASES[i] == g) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ energy (§4)

    /** One Singularity Capacitor. */
    public static final long CAPACITOR_EU = 500000000L;
    /** Opening: Ground base + per 1000 blocks to each projected end; Space base, without an anchor x2. */
    public static final long GROUND_BURST = 200000000L, GROUND_PER_1000 = 20000000L, SPACE_BURST = 2000000000L;
    public static final int NO_ANCHOR_MUL = 2;
    /** Holding, EU a tick. */
    public static final int GROUND_HOLD = 50000, SPACE_HOLD = 200000;
    /** Calibration (С1): Kr and EU. */
    public static final int CALIB_KR = 500;
    public static final long CALIB_EU = 100000000L;
    /** «Проверить место»: krypton a probe. */
    public static final int PROBE_KR = 1;
    /** A Receiver Beacon near the target: the price -40%. */
    public static final int BEACON_DISCOUNT = 40, BEACON_RADIUS = 8;

    // ------------------------------------------------------------------ time (§4, §6)

    /** Lifetime, seconds (+25% a stabiliser: 4 of them x2). */
    public static final int GROUND_LIFE_S = 30, SPACE_LIFE_S = 20, STAB_LIFE_PERCENT = 25, MAX_STABILISERS = 4, STAB_RANGE = 3;
    /** Cooling after a close, seconds; the Ring Cooler module makes it three times as fast for helium. */
    public static final int COOL_S = 60, COOLER_SPEED = 3, COOLER_HE_PER_S = 5;
    /** Out of a hold resource: the vortex folds after this many seconds (С11). */
    public static final int SHORT_GRACE_S = 3;
    /** Stability (simple, stage 1): -5% a missing stabiliser; argon gone -15% a second, back +5% a second; under 10% it folds. */
    public static final int STAB_MISSING_PERCENT = 5, ARGON_LOSS = 15, STAB_RECOVER = 5, STAB_FOLD = 10;
    /** A teleported entity is left alone this long (no ping-pong). */
    public static final int TELEPORT_COOLDOWN = 60;

    /** Lifetime in ticks for a kind and the stabilisers counted. */
    public static int lifeTicks(int kind, int stabilisers) {
        int base = (kind == SPACE ? SPACE_LIFE_S : GROUND_LIFE_S) * 20;
        int s = Math.max(0, Math.min(MAX_STABILISERS, stabilisers));
        return base * (100 + STAB_LIFE_PERCENT * s) / 100;
    }

    /** Cooling ticks after a close. */
    public static int coolTicks() {
        return COOL_S * 20;
    }

    /** The stability a vortex starts at / recovers to. */
    public static int baseStability(int stabilisers) {
        return 100 - STAB_MISSING_PERCENT * (MAX_STABILISERS - Math.max(0, Math.min(MAX_STABILISERS, stabilisers)));
    }

    /** One second of stability: argon short - down, else back towards the base. */
    public static int stabilityStep(int now, int base, boolean argonShort) {
        if (argonShort) {
            return Math.max(0, now - ARGON_LOSS);
        }
        return now < base ? Math.min(base, now + STAB_RECOVER) : Math.max(base, now);
    }

    // ------------------------------------------------------------------ the cost of an opening

    /** What an opening takes at once and what holding takes (§4, §5). */
    public static final class Cost {
        public long eu;
        public int sm, d, kr, ar;
        public int holdEu, heSec, arSec, d2oSec;
        public int lifeTicks;
        /** The beacon / anchor discounts that were applied (for the screen). */
        public boolean beacon, anchor;

        public int resource(int tank) {
            switch (tank) {
                case SM: return sm;
                case D: return d;
                case KR: return kr;
                case AR: return ar;
                default: return 0;
            }
        }
    }

    /**
     * The cost of opening.
     * @param kind GROUND / SPACE
     * @param distances blocks from the bridge to each projected end (ends not at the ring); ДР0 has one
     * @param beacon a Receiver Beacon near the target (Ground: -40%)
     * @param anchor an Interdimensional Anchor in the target dimension (Space)
     * @param stabilisers counted stabilisers (lifetime)
     */
    public static Cost cost(int kind, long[] distances, boolean beacon, boolean anchor, int stabilisers) {
        Cost c = new Cost();
        c.lifeTicks = lifeTicks(kind, stabilisers);
        if (kind == SPACE) {
            c.anchor = anchor;
            c.eu = anchor ? SPACE_BURST : SPACE_BURST * NO_ANCHOR_MUL;
            c.sm = anchor ? 200 : 500;
            c.d = 1000;
            c.kr = anchor ? 0 : 200;
            c.ar = 300;
            c.holdEu = SPACE_HOLD;
            c.heSec = 40;
            c.arSec = 8;
            c.d2oSec = 5;
            return c;
        }
        long eu = GROUND_BURST, sm = 50, kr = 20;
        for (long dist : distances) {
            long d = Math.max(0, dist);
            eu += GROUND_PER_1000 * d / 1000;
            sm += 10 * d / 1000;
            kr += 5 * d / 1000;
        }
        if (beacon) {
            c.beacon = true;
            eu = eu * (100 - BEACON_DISCOUNT) / 100;
            sm = sm * (100 - BEACON_DISCOUNT) / 100;
            kr = kr * (100 - BEACON_DISCOUNT) / 100;
        }
        c.eu = eu;
        c.sm = (int) Math.min(Integer.MAX_VALUE, sm);
        c.kr = (int) Math.min(Integer.MAX_VALUE, kr);
        c.d = 100;
        c.ar = 50;
        c.holdEu = GROUND_HOLD;
        c.heSec = 10;
        c.arSec = 2;
        c.d2oSec = 0;
        return c;
    }

    /** Straight-line distance in whole blocks. */
    public static long distance(int x0, int y0, int z0, int x1, int y1, int z1) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        return Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    // ------------------------------------------------------------------ capacitors

    /**
     * Spreads `eu` into capacitors (in their order), each up to CAPACITOR_EU. Changes `stored`.
     * @return what went in
     */
    public static long charge(long[] stored, long eu) {
        long left = Math.max(0, eu);
        for (int i = 0; i < stored.length && left > 0; i++) {
            long room = CAPACITOR_EU - stored[i];
            if (room > 0) {
                long put = Math.min(room, left);
                stored[i] += put;
                left -= put;
            }
        }
        return Math.max(0, eu) - left;
    }

    /**
     * Takes `eu` out of the capacitors, the last one first (charge() fills from the first: the burst
     * leaves the earliest ones full). All or nothing. @return true if taken
     */
    public static boolean drain(long[] stored, long eu) {
        if (eu <= 0) {
            return true;
        }
        if (total(stored) < eu) {
            return false;
        }
        long left = eu;
        for (int i = stored.length - 1; i >= 0 && left > 0; i--) {
            long take = Math.min(stored[i], left);
            stored[i] -= take;
            left -= take;
        }
        return true;
    }

    public static long total(long[] stored) {
        long t = 0;
        for (long s : stored) {
            t += s;
        }
        return t;
    }

    // ------------------------------------------------------------------ formatting

    /** 1 240 / 820 млн / 1,04 млрд - the screen's short numbers (units from the caller: k, M, G). */
    public static String shortEu(long eu, String m, String g) {
        if (eu >= 1000000000L) {
            long h = Math.round(eu / 10000000.0);
            return (h / 100) + "," + pad2(h % 100) + " " + g;
        }
        if (eu >= 1000000L) {
            return Math.round(eu / 1000000.0) + " " + m;
        }
        return group(eu);
    }

    private static String pad2(long v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }

    /** 12480 -> "12 480". */
    public static String group(long v) {
        String s = String.valueOf(Math.abs(v));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) {
                b.append(' ');
            }
            b.append(s.charAt(i));
        }
        return (v < 0 ? "-" : "") + b;
    }
}
