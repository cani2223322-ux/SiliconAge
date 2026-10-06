package com.sc.util;

/**
 * Pure geometry and arithmetic of the drills' zones and the Singular drill's counters (docs/plan-singular-tools.md §3),
 * shared by the server (DrillLogicSC) and the client frame (DrillHoleRendererSC) so both see the same blocks.
 * A zone is a box {minX, minY, minZ, maxX, maxY, maxZ} (inclusive): `size` x `size` on the clicked face's plane,
 * `depth` blocks into the wall. Odd sizes are centred on the clicked block; an even size (the 12x12 black hole)
 * can't be, so its centre is shifted half a block toward the player on each axis of the face.
 */
public final class DrillZoneSC {

    /** Black hole sizes in order (0 = off). */
    public static final int[] HOLE_SIZES = {0, 5, 9, 12};
    /** The tunnel depth of the black hole (the other one is 1). */
    public static final int HOLE_TUNNEL = 3;

    private DrillZoneSC() {
    }

    /** The outward normal {nx, ny, nz} of a block face (0 down, 1 up, 2 north -z, 3 south +z, 4 west -x, 5 east +x). */
    public static int[] normal(int side) {
        switch (side) {
            case 0: return new int[]{0, -1, 0};
            case 1: return new int[]{0, 1, 0};
            case 2: return new int[]{0, 0, -1};
            case 3: return new int[]{0, 0, 1};
            case 4: return new int[]{-1, 0, 0};
            default: return new int[]{1, 0, 0};
        }
    }

    /**
     * The offsets [lo, hi] of a zone `size` wide around the block at `c` on one axis: odd - centred; even - one more
     * block on the side of `viewer` (the player's coordinate on that axis): centre shifted half a block toward him.
     */
    public static int[] span(int c, int size, double viewer) {
        if (size <= 1) {
            return new int[]{c, c};
        }
        int half = size / 2;
        if ((size & 1) == 1) {
            return new int[]{c - half, c + half};
        }
        return viewer < c + 0.5 ? new int[]{c - half, c + half - 1} : new int[]{c - half + 1, c + half};
    }

    /** The zone's box for a click on (x, y, z) at `side`, seen from (px, py, pz) (eyes). */
    public static int[] zone(int x, int y, int z, int side, int size, int depth, double px, double py, double pz) {
        int[] n = normal(side);
        int[] sx = n[0] != 0 ? new int[]{x, x} : span(x, size, px);
        int[] sy = n[1] != 0 ? new int[]{y, y} : span(y, size, py);
        int[] sz = n[2] != 0 ? new int[]{z, z} : span(z, size, pz);
        int d = Math.max(1, depth) - 1;                      // into the wall: against the normal
        int[] box = {sx[0], sy[0], sz[0], sx[1], sy[1], sz[1]};
        for (int a = 0; a < 3; a++) {
            if (n[a] < 0) {
                box[3 + a] += d;
            } else if (n[a] > 0) {
                box[a] -= d;
            }
        }
        return box;
    }

    /** Blocks in a box. */
    public static int volume(int[] box) {
        return (box[3] - box[0] + 1) * (box[4] - box[1] + 1) * (box[5] - box[2] + 1);
    }

    public static boolean inside(int[] box, int x, int y, int z) {
        return x >= box[0] && x <= box[3] && y >= box[1] && y <= box[4] && z >= box[2] && z <= box[5];
    }

    /** On the box's outer shell (its neighbours outside need telling when it goes). */
    public static boolean onShell(int[] box, int x, int y, int z) {
        return x == box[0] || x == box[3] || y == box[1] || y == box[4] || z == box[2] || z == box[5];
    }

    // ------------------------------------------------------------------ black hole sizes

    /** The largest black hole a drill of `level` opens: 5 (1-2), 9 (3-4), 12 (5). */
    public static int maxHole(int level) {
        return level >= 5 ? 12 : level >= 3 ? 9 : 5;
    }

    public static boolean holeOpen(int size, int level) {
        return size == 0 || (size == 5 || size == 9 || size == 12) && size <= maxHole(level);
    }

    /** The stored size as it works at `level`: the largest open size not above it (0 stays 0). */
    public static int effectiveHole(int stored, int level) {
        if (stored <= 0) {
            return 0;
        }
        int best = 0;
        for (int s : HOLE_SIZES) {
            if (s > 0 && s <= stored && holeOpen(s, level)) {
                best = s;
            }
        }
        return best == 0 ? 5 : best;
    }

    /** Shift + wheel: off -> 5 -> 9 -> 12 -> off (only the sizes open at `level`), `delta` < 0 goes back. */
    public static int nextHole(int current, int delta, int level) {
        int[] open = new int[HOLE_SIZES.length];
        int n = 0, at = 0;
        int cur = effectiveHole(current, level);
        for (int s : HOLE_SIZES) {
            if (holeOpen(s, level)) {
                if (s == cur) {
                    at = n;
                }
                open[n++] = s;
            }
        }
        int step = delta < 0 ? -1 : 1;
        return open[((at + step) % n + n) % n];
    }

    /**
     * Shift + wheel / the K menu's mode button, one step: off -> 5 -> 5 tunnel -> 9 -> 9 tunnel -> 12 -> 12 tunnel -> off
     * (only the sizes open at `level`; `delta` < 0 goes back). @return {size, depth}
     */
    public static int[] nextMode(int size, int depth, int delta, int level) {
        int[] sizes = new int[HOLE_SIZES.length * 2];
        int[] depths = new int[sizes.length];
        int n = 0, at = 0;
        int cur = effectiveHole(size, level);
        int curDepth = cur == 0 ? 1 : depth == HOLE_TUNNEL ? HOLE_TUNNEL : 1;
        for (int s : HOLE_SIZES) {
            if (!holeOpen(s, level)) {
                continue;
            }
            for (int d = 0; d < (s == 0 ? 1 : 2); d++) {
                int dep = d == 0 ? 1 : HOLE_TUNNEL;
                if (s == cur && dep == curDepth) {
                    at = n;
                }
                sizes[n] = s;
                depths[n++] = dep;
            }
        }
        int i = ((at + (delta < 0 ? -1 : 1)) % n + n) % n;
        return new int[]{sizes[i], depths[i]};
    }

    // ------------------------------------------------------------------ counters

    /** {whole things earned, the counter's new remainder} for `add` more units on `counter`, `per` a thing. */
    public static int[] accrue(int counter, long add, int per) {
        long sum = Math.max(0, counter) + Math.max(0, add);
        return new int[]{(int) (sum / per), (int) (sum % per)};
    }

    /** Crumb units of a dig: natural blocks count 1, natural ores `oreMul` (ores are natural blocks too). */
    public static long crumbUnits(int naturalBlocks, int naturalOres, int oreMul) {
        return (long) Math.max(0, naturalBlocks - naturalOres) + (long) Math.max(0, naturalOres) * oreMul;
    }

    /** Whole mB of singular matter `blocks` cost at `perMb` blocks a mB, the remainder carried (ToolGasSC.drainFraction does it in the tool). */
    public static float holeGas(int blocks, int perMb) {
        return blocks <= 0 ? 0F : (float) blocks / perMb;
    }

    /** Whole mB of singular matter a zone of `volume` blocks may cost at `perMb` blocks a mB (rounded up): needed before it starts. */
    public static int holeGasNeed(int volume, int perMb) {
        return volume <= 0 || perMb <= 0 ? 0 : (volume + perMb - 1) / perMb;
    }

    /** EU per block of the black hole: `euPerBlock` x `mul`, rounded up. */
    public static int holeEu(int euPerBlock, float mul) {
        return (int) Math.ceil(euPerBlock * mul - 1e-4);
    }

    /** The black hole's heat for `blocks`. */
    public static int holeHeat(int blocks, float perBlock) {
        return (int) Math.ceil(blocks * perBlock - 1e-4);
    }

    /** The gravitational funnel's half-size for a drill of `level` (0: not open yet). */
    public static int funnelRadius(int level, boolean minerPerk) {
        if (level < 2) {
            return 0;
        }
        if (minerPerk) {
            return DrillFeature.FUNNEL_RADIUS_MINER;
        }
        return level >= 4 ? DrillFeature.FUNNEL_RADIUS_4 : DrillFeature.FUNNEL_RADIUS;
    }

    /** Pack / unpack a block position into one long (x, z: 26 bits, y: 12 bits) - the placed-block record's key. */
    public static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) y & 0xFFFL) << 26 | ((long) z & 0x3FFFFFFL);
    }

    public static int[] unpack(long k) {
        int x = (int) (k >> 38);
        int y = (int) (k << 26 >> 52);
        int z = (int) (k << 38 >> 38);
        return new int[]{x, y & 0xFFF, z};
    }
}
