package com.sc.settle.caravan;

import java.util.ArrayList;
import java.util.List;

import com.sc.dim.SubstrateCellsSC;
import com.sc.dim.SubstrateTerrainSC;
import com.sc.settle.gen.SettlementLayoutSC;

/**
 * PACKAGE K (docs/plan-substrate-tech-f5.md §8.1, §8.2; rules ПН-13, ПН-16): a caravan's ground as pure maths - no World, so the run
 * (CaravanRunSC) and a self-test ask the same thing. A settlement stands at its cell's middle +-16 (SettlementLayoutSC.JITTER), so its
 * cell's plateau - the cell less the canyon belts (SubstrateTerrainSC.CANYON_BELT) - is a square round it reaching 87 blocks from the
 * core at the least: the caravan never leaves it (lore: the Tuners cross the Scribe lines by their docks' short punctures, not on foot).
 * <ul>
 * <li>the start (spawnSpots): APPROACH_FROM..APPROACH_TO past the dome's edge towards its source (the nearest neighbour, else the dock's
 * side), on the plateau of the core's own cell; the source's way first, then turning ANGLE_STEP degrees either side up to the far side;
 * a City's dome is big - the way that fits;</li>
 * <li>the route (route): a waypoint every STEP blocks - in from the start to the ring RING_OUT past the dome's edge (outside the belt
 * where the dome's own guards hunt; pulled in where the plateau ends, never closer than RING_MIN), round the ring the shorter way to the
 * street's axis (the gate's cardinal way - SettlementLayoutSC.DIR_*), then along the axis through the gate to the guard post's depth.
 * The plateau is convex (a square), so a straight leg between two of its points never leaves it.</li>
 * </ul>
 * Points are block columns {x, z}; heights are the run's (the loaded chunk's top, else the terrace's).
 */
public final class CaravanRunPathSC {

    /** ПН-13: a waypoint every this many blocks (the walk's leg; the path search never looks farther). */
    public static final int STEP = 12;
    /** The ring round the dome: this far past its edge, pulled in where the plateau ends - never closer than RING_MIN. TODO(balance) */
    public static final int RING_OUT = 30, RING_MIN = 6;
    /** The start's candidates: every SPAWN_STEP blocks of APPROACH_FROM..APPROACH_TO, every ANGLE_STEP degrees from the source's way. */
    public static final int SPAWN_STEP = 4, ANGLE_STEP = 15;
    /** The ring pulled in by this much at a time where it leaves the plateau. */
    private static final int PULL = 2;

    private CaravanRunPathSC() {
    }

    /** The column (x, z) is on the plateau of cell (cellX, cellZ): in that cell, out of every canyon belt. */
    public static boolean plateau(int cellX, int cellZ, int x, int z) {
        return SubstrateCellsSC.cellOf(x) == cellX && SubstrateCellsSC.cellOf(z) == cellZ && !SubstrateTerrainSC.inCanyonBelt(x, z);
    }

    /**
     * ПН-13: the start's candidate columns {x, z} for a dome of `radius` round the core column (coreX, coreZ), its source lying at
     * `angle` (radians, atan2(dz, dx) from the core) - on the core cell's plateau; the source's way first, the far way last, the far
     * distance first at each way. The run takes the first one that is loaded and has room.
     */
    public static List<int[]> spawnSpots(int coreX, int coreZ, int radius, double angle) {
        List<int[]> out = new ArrayList<int[]>();
        int cellX = SubstrateCellsSC.cellOf(coreX), cellZ = SubstrateCellsSC.cellOf(coreZ);
        double cx = coreX + 0.5D, cz = coreZ + 0.5D;
        for (int k = 0; k * ANGLE_STEP <= 180; k++) {
            for (int sign = 1; sign >= -1; sign -= 2) {
                if (k == 0 && sign < 0 || k * ANGLE_STEP == 180 && sign < 0) {
                    continue;                                   // the source's way and the far way once
                }
                double a = angle + Math.toRadians(sign * k * ANGLE_STEP);
                for (int d = CaravanRulesSC.APPROACH_TO; d >= CaravanRulesSC.APPROACH_FROM; d -= SPAWN_STEP) {
                    double r = radius + d;
                    int x = floor(cx + Math.cos(a) * r), z = floor(cz + Math.sin(a) * r);
                    if (plateau(cellX, cellZ, x, z)) {
                        out.add(new int[]{x, z});
                    }
                }
            }
        }
        return out;
    }

    /** The street's cardinal way (SettlementLayoutSC.NORTH..WEST) nearest to the direction (dx, dz) from the core; (0, 0) - EAST. */
    public static int axis(double dx, double dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx < 0D ? SettlementLayoutSC.WEST : SettlementLayoutSC.EAST;
        }
        return dz < 0D ? SettlementLayoutSC.NORTH : SettlementLayoutSC.SOUTH;
    }

    /**
     * ПН-13: the waypoints {x, z} from the start (spawnX, spawnZ) - not in the list - to the street's last point: `along` blocks from
     * the core on the cardinal `axis` (the guard post's depth; the gate is passed on the way). In to the ring, round it the shorter
     * way, then in along the axis. Every leg cut into steps of at most STEP.
     */
    public static int[][] route(int coreX, int coreZ, int radius, int spawnX, int spawnZ, int axis, int along) {
        int cellX = SubstrateCellsSC.cellOf(coreX), cellZ = SubstrateCellsSC.cellOf(coreZ);
        double cx = coreX + 0.5D, cz = coreZ + 0.5D;
        int ax = SettlementLayoutSC.DIR_X[axis & 3], az = SettlementLayoutSC.DIR_Z[axis & 3];
        double from = Math.atan2(spawnZ + 0.5D - cz, spawnX + 0.5D - cx), to = Math.atan2(az, ax);
        double turn = to - from;
        while (turn > Math.PI) {
            turn -= Math.PI * 2D;
        }
        while (turn <= -Math.PI) {
            turn += Math.PI * 2D;
        }
        List<int[]> out = new ArrayList<int[]>();
        int[] at = {spawnX, spawnZ};
        int[] ring = ringPoint(cellX, cellZ, cx, cz, radius, from);
        at = leg(out, at, ring);
        int n = (int) Math.ceil(Math.abs(turn) * (radius + RING_OUT) / STEP);
        for (int i = 1; i <= n; i++) {
            int[] p = ringPoint(cellX, cellZ, cx, cz, radius, from + turn * i / n);
            at = leg(out, at, p);
        }
        int[] end = {coreX + ax * along, coreZ + az * along};
        leg(out, at, end);
        return out.toArray(new int[out.size()][]);
    }

    /** Where the ring crosses the way `angle` from the core: RING_OUT past the edge, pulled in while off the plateau (RING_MIN at the least). */
    public static int[] ringPoint(int cellX, int cellZ, double cx, double cz, int radius, double angle) {
        int[] p = null;
        for (int r = radius + RING_OUT; r >= radius + RING_MIN; r -= PULL) {
            p = new int[]{floor(cx + Math.cos(angle) * r), floor(cz + Math.sin(angle) * r)};
            if (plateau(cellX, cellZ, p[0], p[1])) {
                return p;
            }
        }
        return p;
    }

    /** The waypoint the ambush waits by: the route's half-way one (index; 0 for a route of one point). */
    public static int half(int[][] route) {
        return route.length / 2;
    }

    /** The leg from `a` to `b` in steps of at most STEP (b included, a not; nothing when they are the same column). @return b */
    private static int[] leg(List<int[]> out, int[] a, int[] b) {
        double dx = b[0] - a[0], dz = b[1] - a[1], len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1D) {
            return a;
        }
        int n = (int) Math.ceil(len / STEP);
        for (int i = 1; i <= n; i++) {
            int[] p = i == n ? new int[]{b[0], b[1]} : new int[]{(int) Math.round(a[0] + dx * i / n), (int) Math.round(a[1] + dz * i / n)};
            int[] last = out.isEmpty() ? null : out.get(out.size() - 1);
            if (last == null || last[0] != p[0] || last[1] != p[1]) {
                out.add(p);
            }
        }
        return b;
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
