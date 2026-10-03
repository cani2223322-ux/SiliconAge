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
        return cost(kind, distances, null, beacon, anchor, stabilisers);
    }

    /**
     * The cost with a discount per projected end (percent off that end's share - the armour's own end -25%,
     * §8 «броня - маяк на вашем конце»); Space has no distance share: the discount goes off its krypton (the aim).
     */
    public static Cost cost(int kind, long[] distances, int[] endPct, boolean beacon, boolean anchor, int stabilisers) {
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
            int best = 0;
            for (int i = 0; endPct != null && i < endPct.length; i++) {
                best = Math.max(best, Math.max(0, Math.min(100, endPct[i])));
            }
            c.kr = c.kr * (100 - best) / 100;
            return c;
        }
        long eu = GROUND_BURST, sm = 50, kr = 20;
        for (int i = 0; i < distances.length; i++) {
            long d = Math.max(0, distances[i]);
            int keep = 100 - (endPct != null && i < endPct.length ? Math.max(0, Math.min(100, endPct[i])) : 0);
            eu += GROUND_PER_1000 * d / 1000 * keep / 100;
            sm += 10 * d / 1000 * keep / 100;
            kr += 5 * d / 1000 * keep / 100;
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

    // ------------------------------------------------------------------ stage 2: modes, remotes, armour (§7, §8, §10)

    /** The modes ДР0-ДР5: from the base, home, from me to a point, from the base remotely, fetch a friend, to me. */
    public static final int MODE_BASE = 0, MODE_HOME = 1, MODE_FROM_ME = 2, MODE_REMOTE = 3, MODE_FRIEND = 4, MODE_TO_ME = 5, MODES = 6;
    /** What an end is: the ring, a point (coordinates), next to a player (a projection). */
    public static final int END_RING = 0, END_POINT = 1, END_NEAR = 2;
    /** Where the commands come from. */
    public static final int SRC_CONTROLLER = 0, SRC_REMOTE = 1, SRC_ARMOUR = 2;
    /** A remote's signal: EU taken from its own charge per command (С9); the remotes' charge. */
    public static final long REMOTE_SIGNAL_EU = 1000000L, REMOTE_CAPACITY = 10000000L, SPACE_REMOTE_CAPACITY = 20000000L;
    /** «Дистанционный режим»: EU a tick while the controller keeps its own chunk loaded. */
    public static final int REMOTE_MODE_EU = 500;
    /** From the armour your end is this much cheaper (and precise). */
    public static final int ARMOUR_DISCOUNT = 25;
    /** A projected end stands this many blocks in front of its player. */
    public static final int PROJECTION_AHEAD = 3;
    /** Another player within this many blocks of an end must agree (§10); the request lives CONSENT_TICKS. */
    public static final int CONSENT_RADIUS = 8, CONSENT_TICKS = 600;
    /** Bridges one helmet links; remembered targets; scanner finds kept; «Взгляд» reach; friends a bridge keeps. */
    public static final int MAX_LINKS = 3, HISTORY = 5, FINDS = 16, LOOK_RANGE = 256, MAX_FRIENDS = 16;
    /** Access: owner and friends, or public (anyone at the controller; remotes and armour stay owner / friends). */
    public static final int ACCESS_FRIENDS = 0, ACCESS_PUBLIC = 1;

    /**
     * The ends of a mode: {end A, end B} (END_*) - end B is never the ring. Friend: the ring and next to the friend,
     * or (toMe) next to the friend and next to you; friendEnd() says which one is the friend's.
     */
    public static int[] modeEnds(int mode, boolean toMe) {
        switch (mode) {
            case MODE_HOME:
            case MODE_TO_ME:
                return new int[]{END_RING, END_NEAR};
            case MODE_FROM_ME:
                return new int[]{END_NEAR, END_POINT};
            case MODE_FRIEND:
                return new int[]{toMe ? END_NEAR : END_RING, END_NEAR};
            default:
                return new int[]{END_RING, END_POINT};
        }
    }

    /** ДР4: the index of the friend's end (0 with «к вам», else 1); -1 for the other modes. */
    public static int friendEnd(int mode, boolean toMe) {
        return mode != MODE_FRIEND ? -1 : toMe ? 0 : 1;
    }

    /** The mode needs a target point (ДР0, ДР2, ДР3). */
    public static boolean needsPoint(int mode) {
        return mode == MODE_BASE || mode == MODE_FROM_ME || mode == MODE_REMOTE;
    }

    /** How many projected ends (not at the ring) a mode has. */
    public static int projections(int mode, boolean toMe) {
        int n = 0;
        for (int e : modeEnds(mode, toMe)) {
            n += e == END_RING ? 0 : 1;
        }
        return n;
    }

    /**
     * Where a projected end stands in front of a player: {x, y, z, axis} - PROJECTION_AHEAD blocks along the
     * way they face (yaw: 0 south +Z, 90 west -X, 180 north -Z, 270 east +X), the vortex across that way
     * (axis 0: along X), its lowest cell at the player's feet.
     */
    public static int[] projectionSpot(double px, double py, double pz, float yaw) {
        int f = (int) Math.floor(yaw * 4.0F / 360.0F + 0.5D) & 3;
        int dx = f == 1 ? -1 : f == 3 ? 1 : 0, dz = f == 0 ? 1 : f == 2 ? -1 : 0;
        int x = (int) Math.floor(px) + dx * PROJECTION_AHEAD, z = (int) Math.floor(pz) + dz * PROJECTION_AHEAD;
        int y = (int) Math.floor(py + 0.001);
        return new int[]{x, y, z, dz != 0 ? 0 : 1};
    }

    /**
     * «Взгляд»: the first solid cell along a look from an eye (step 0.25 block, at most `range` blocks) -
     * {x, y + 1, z} (the cell over it), or null. Pure: `solid` says what is solid.
     */
    public static int[] lookTarget(double ex, double ey, double ez, double lx, double ly, double lz, int range, Solid solid) {
        double len = Math.sqrt(lx * lx + ly * ly + lz * lz);
        if (len < 1e-6) {
            return null;
        }
        lx /= len;
        ly /= len;
        lz /= len;
        for (double t = 0.5; t <= range; t += 0.25) {
            int x = (int) Math.floor(ex + lx * t), y = (int) Math.floor(ey + ly * t), z = (int) Math.floor(ez + lz * t);
            if (y < 0) {
                return null;
            }
            if (solid.at(x, y, z)) {
                return new int[]{x, y + 1, z};
            }
        }
        return null;
    }

    /** What is solid for lookTarget. */
    public interface Solid {
        boolean at(int x, int y, int z);
    }

    /** The remote's charge after a command: the signal paid, or -1 when it isn't there. */
    public static long remoteAfterSignal(long charge, boolean creative) {
        if (creative) {
            return charge;
        }
        return charge >= REMOTE_SIGNAL_EU ? charge - REMOTE_SIGNAL_EU : -1;
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
