package com.sc.bridge;

import java.util.ArrayList;
import java.util.List;

/**
 * The bridge's build check (docs/plan-ground-bridge.md §3): pure, over a View of block kinds, so the
 * self-test runs it on fake layouts.
 *
 * Coordinates are "front view" cells (u, v) round the controller at (cx, cy, cz): v = 0 is the controller's
 * row, the ring's rows are v = 1..n (n = 5 Ground, 7 Space), u = -h..h across (h = (n - 1) / 2), the ring
 * standing on its edge either along X (axis 0: the cell is (cx + u, cy + v, cz), the vortex faces Z) or
 * along Z (axis 1: (cx, cy + v, cz + u), it faces X). The Space bridge has 4 focusers on the diagonals one
 * block out of the corners: (±(h + 1), 0) and (±(h + 1), n + 1).
 *
 * Ports (energy, gas), modules and capacitors count when a chain of touching parts links them to the
 * controller or to the ring's bottom row. Stabilisers count within STAB_RANGE blocks of the ring (at most 4).
 * The vortex needs 2 free blocks on both sides of the ring's inside.
 */
public final class BridgeStructureSC {

    private BridgeStructureSC() {
    }

    /** Block kinds the check sees. */
    public static final int K_AIR = 0, K_OTHER = 1, K_COIL = 2, K_CONTROLLER = 3, K_CAPACITOR = 4, K_ENERGY_PORT = 5, K_GAS_PORT = 6,
            K_FOCUSER = 7, K_NAV = 8, K_MASS = 9, K_COOLER = 10, K_SHIELD = 11, K_STABILISER = 12, K_VORTEX = 13, K_BEACON = 14,
            K_ANCHOR = 15, K_PASSABLE = 16;

    /** Front-view cell states for the screen's schematic. */
    public static final byte C_NONE = 0, C_COIL = 1, C_COIL_MISSING = 2, C_INSIDE = 3, C_JUNK = 4, C_FOCUSER = 5, C_FOCUSER_MISSING = 6,
            C_CONTROLLER = 7, C_VORTEX = 8;

    /** Ring sizes tried (3 and 9: hooks for the small / big variants, not built yet). */
    public static final int[] SIZES = {BridgeMathSC.GROUND_RING, BridgeMathSC.SPACE_RING};
    /** Parts linked by touching (at most this many looked at). */
    public static final int MAX_PARTS = 96;
    /** Free blocks the vortex needs in front of and behind the ring. */
    public static final int SIDE_CLEAR = 2;

    public interface View {
        int kind(int x, int y, int z);
    }

    /** One thing wrong: a lang key with %s arguments and, when it has one, the block to highlight. */
    public static final class Problem {
        public final String key;
        public final String[] args;
        public final boolean hasPos;
        public final int x, y, z;

        public Problem(String key, String[] args, int[] pos) {
            this.key = key;
            this.args = args == null ? new String[0] : args;
            this.hasPos = pos != null;
            this.x = pos == null ? 0 : pos[0];
            this.y = pos == null ? 0 : pos[1];
            this.z = pos == null ? 0 : pos[2];
        }
    }

    public static final class Scan {
        public int cx, cy, cz;
        /** A ring was found (one coil at least where a ring would stand). */
        public boolean found;
        public int kind = BridgeMathSC.GROUND, size = BridgeMathSC.GROUND_RING, axis;
        public int coils, coilsNeeded, focusers, focusersNeeded, junk, sideBlocked;
        /** (size + 2)^2 cells, row 0 at the top (v = size + 1), column 0 at u = -(h + 1). */
        public byte[] cells = new byte[0];
        public final List<int[]> capacitors = new ArrayList<int[]>(), energyPorts = new ArrayList<int[]>(), gasPorts = new ArrayList<int[]>(),
                modules = new ArrayList<int[]>(), stabilisers = new ArrayList<int[]>();
        public boolean nav, mass, cooler, shield;
        public final List<Problem> problems = new ArrayList<Problem>();
        public boolean valid;
        public long signature;

        public int grid() {
            return size + 2;
        }

        public int stabCount() {
            return Math.min(BridgeMathSC.MAX_STABILISERS, stabilisers.size());
        }

        /** World position of front-view cell (u, v). */
        public int[] at(int u, int v) {
            return BridgeStructureSC.at(cx, cy, cz, axis, u, v, 0);
        }

        /** The vortex's middle cell (the inside's centre). */
        public int[] centre() {
            return at(0, (size + 1) / 2);
        }
    }

    /** World position of front-view cell (u, v), `d` blocks out along the ring's normal. */
    public static int[] at(int cx, int cy, int cz, int axis, int u, int v, int d) {
        return axis == 0 ? new int[]{cx + u, cy + v, cz + d} : new int[]{cx + d, cy + v, cz + u};
    }

    public static boolean isRing(int size, int u, int v) {
        int h = (size - 1) / 2;
        return v >= 1 && v <= size && Math.abs(u) <= h && (Math.abs(u) == h || v == 1 || v == size);
    }

    public static boolean isInside(int size, int u, int v) {
        int h = (size - 1) / 2;
        return Math.abs(u) < h && v > 1 && v < size;
    }

    public static boolean isFocuser(int size, int u, int v) {
        int h = (size - 1) / 2;
        return Math.abs(u) == h + 1 && (v == 0 || v == size + 1);
    }

    public static int ringCells(int size) {
        return 4 * (size - 1);
    }

    private static boolean isPart(int k) {
        return k == K_CAPACITOR || k == K_ENERGY_PORT || k == K_GAS_PORT || k == K_NAV || k == K_MASS || k == K_COOLER || k == K_SHIELD;
    }

    private static String s(int v) {
        return String.valueOf(v);
    }

    /** The full check round the controller at cx, cy, cz. */
    public static Scan scan(View w, int cx, int cy, int cz) {
        // 1. the ring: the candidate (size, axis) with the most of its coils in place
        int bestSize = BridgeMathSC.GROUND_RING, bestAxis = 0, bestCoils = -1;
        double bestShare = -1;
        for (int size : SIZES) {
            for (int axis = 0; axis < 2; axis++) {
                int h = (size - 1) / 2, n = 0;
                for (int v = 1; v <= size; v++) {
                    for (int u = -h; u <= h; u++) {
                        if (isRing(size, u, v)) {
                            int[] p = at(cx, cy, cz, axis, u, v, 0);
                            if (w.kind(p[0], p[1], p[2]) == K_COIL) {
                                n++;
                            }
                        }
                    }
                }
                double share = n / (double) ringCells(size);
                if (n > 0 && (share > bestShare + 1e-9 || Math.abs(share - bestShare) < 1e-9 && size > bestSize)) {
                    bestShare = share;
                    bestSize = size;
                    bestAxis = axis;
                    bestCoils = n;
                }
            }
        }
        Scan r = new Scan();
        r.cx = cx;
        r.cy = cy;
        r.cz = cz;
        r.found = bestCoils > 0;
        r.size = bestSize;
        r.axis = bestAxis;
        r.kind = bestSize >= BridgeMathSC.SPACE_RING ? BridgeMathSC.SPACE : BridgeMathSC.GROUND;
        int n = r.size, h = (n - 1) / 2, g = n + 2;
        r.cells = new byte[g * g];
        r.coilsNeeded = ringCells(n);
        r.focusersNeeded = r.kind == BridgeMathSC.SPACE ? 4 : 0;
        if (!r.found) {
            r.problems.add(new Problem("sc.bridge.problem.noring", null, null));
        }
        List<Problem> coilProblems = new ArrayList<Problem>(), otherProblems = new ArrayList<Problem>();
        for (int v = n + 1; v >= 0; v--) {
            for (int u = -h - 1; u <= h + 1; u++) {
                int[] p = at(cx, cy, cz, r.axis, u, v, 0);
                int idx = (n + 1 - v) * g + (u + h + 1);
                int k = w.kind(p[0], p[1], p[2]);
                if (v == 0 && u == 0) {
                    r.cells[idx] = C_CONTROLLER;
                } else if (isRing(n, u, v)) {
                    if (k == K_COIL) {
                        r.coils++;
                        r.cells[idx] = C_COIL;
                    } else {
                        r.cells[idx] = C_COIL_MISSING;
                        coilProblems.add(new Problem("sc.bridge.problem.coil", new String[]{s(n + 1 - v), s(u + h + 1)}, p));
                    }
                } else if (isInside(n, u, v)) {
                    if (k == K_AIR) {
                        r.cells[idx] = C_INSIDE;
                    } else if (k == K_VORTEX) {
                        r.cells[idx] = C_VORTEX;
                    } else {
                        r.cells[idx] = C_JUNK;
                        r.junk++;
                        otherProblems.add(new Problem("sc.bridge.problem.junk", new String[]{s(p[0]), s(p[1]), s(p[2])}, p));
                    }
                } else if (r.kind == BridgeMathSC.SPACE && isFocuser(n, u, v)) {
                    if (k == K_FOCUSER) {
                        r.focusers++;
                        r.cells[idx] = C_FOCUSER;
                    } else {
                        r.cells[idx] = C_FOCUSER_MISSING;
                        otherProblems.add(new Problem("sc.bridge.problem.focuser", new String[]{s(p[0]), s(p[1]), s(p[2])}, p));
                    }
                }
            }
        }
        // 2. room for the vortex on both sides of the inside
        for (int v = 2; v < n; v++) {
            for (int u = -h + 1; u < h; u++) {
                for (int d = -SIDE_CLEAR; d <= SIDE_CLEAR; d++) {
                    if (d == 0) {
                        continue;
                    }
                    int[] p = at(cx, cy, cz, r.axis, u, v, d);
                    int k = w.kind(p[0], p[1], p[2]);
                    if (k != K_AIR && k != K_PASSABLE && k != K_VORTEX) {
                        if (r.sideBlocked == 0) {
                            otherProblems.add(new Problem("sc.bridge.problem.side", new String[]{s(p[0]), s(p[1]), s(p[2])}, p));
                        }
                        r.sideBlocked++;
                    }
                }
            }
        }
        // 3. ports, modules, capacitors: a chain of touching parts from the controller or the ring's bottom row
        List<int[]> queue = new ArrayList<int[]>();
        java.util.Set<Long> seen = new java.util.HashSet<Long>();
        queue.add(new int[]{cx, cy, cz});
        seen.add(key(cx, cy, cz));
        for (int u = -h; u <= h; u++) {
            int[] p = at(cx, cy, cz, r.axis, u, 1, 0);
            if (w.kind(p[0], p[1], p[2]) == K_COIL) {
                queue.add(p);
                seen.add(key(p[0], p[1], p[2]));
            }
        }
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        int parts = 0;
        for (int qi = 0; qi < queue.size() && parts < MAX_PARTS; qi++) {
            int[] q = queue.get(qi);
            for (int[] dd : dirs) {
                int x = q[0] + dd[0], y = q[1] + dd[1], z = q[2] + dd[2];
                if (!seen.add(key(x, y, z))) {
                    continue;
                }
                int k = w.kind(x, y, z);
                if (!isPart(k)) {
                    continue;
                }
                int[] p = {x, y, z, k};
                parts++;
                queue.add(p);
                switch (k) {
                    case K_CAPACITOR: r.capacitors.add(p); break;
                    case K_ENERGY_PORT: r.energyPorts.add(p); break;
                    case K_GAS_PORT: r.gasPorts.add(p); break;
                    default:
                        r.modules.add(p);
                        r.nav |= k == K_NAV;
                        r.mass |= k == K_MASS;
                        r.cooler |= k == K_COOLER;
                        r.shield |= k == K_SHIELD;
                }
            }
        }
        if (r.energyPorts.isEmpty()) {
            otherProblems.add(new Problem("sc.bridge.problem.noenergyport", null, null));
        }
        if (r.gasPorts.isEmpty()) {
            otherProblems.add(new Problem("sc.bridge.problem.nogasport", null, null));
        }
        // 4. stabilisers near the ring
        int range = BridgeMathSC.STAB_RANGE;
        for (int v = 1 - range; v <= n + range; v++) {
            for (int u = -h - range; u <= h + range; u++) {
                for (int d = -range; d <= range; d++) {
                    int[] p = at(cx, cy, cz, r.axis, u, v, d);
                    if (w.kind(p[0], p[1], p[2]) == K_STABILISER) {
                        r.stabilisers.add(p);
                    }
                }
            }
        }
        if (r.found) {
            r.problems.addAll(coilProblems);
        }
        r.problems.addAll(otherProblems);
        r.valid = r.found && r.problems.isEmpty();
        r.signature = signature(r);
        return r;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /**
     * The ring's signature for the calibration (С1): its kind, size, orientation, where it stands and every
     * coil / focuser cell. A different ring (moved, turned, rebuilt bigger) - another signature; 0 never.
     */
    public static long signature(Scan r) {
        long h = 1125899906842597L;
        h = 31 * h + r.kind;
        h = 31 * h + r.size;
        h = 31 * h + r.axis;
        h = 31 * h + r.cx;
        h = 31 * h + r.cy;
        h = 31 * h + r.cz;
        for (int i = 0; i < r.cells.length; i++) {
            h = 31 * h + (r.cells[i] == C_COIL || r.cells[i] == C_FOCUSER ? 1 : 0);
        }
        return h == 0 ? 1 : h;
    }
}
