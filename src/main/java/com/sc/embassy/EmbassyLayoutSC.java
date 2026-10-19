package com.sc.embassy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.sc.settle.NpcKindSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSizeSC;
import com.sc.settle.gen.SettlementLayoutSC;
import com.sc.settle.npc.NpcNamesSC;

/**
 * Подложка Ф6 «Посольства Наладчиков» (docs/plan-substrate-tech-f6.md §5.2, rules ПЕ-5..ПЕ-9): an embassy's shape - pure maths of its
 * site (EmbassySiteSC.Site: the core's column, radius, floor, seed), the same answer for the generator (package G: the land's
 * levelling, the ground piece, the «Достройка»), the city's pieces (package S), the flights (package R: the Vokzal, the ring, the
 * vortex), RosterSC.restore and the tests. Written by the coordinator: the geometry below is fixed (every package builds, levels and
 * boards by it), the roster, the record. The quarters (the lots and the posts) are package S's - EmbassyQuartersSC.lay.
 * <p>
 * The frame (as SettlementLayoutSC's): `a` along the main street towards the main gate (Plan.gateDir, by the seed), `p` across it
 * (a turned clockwise, seen from above), the core at (0, 0); Plan.x / z give the world's. From the middle out:
 * <ul>
 * <li>the Heart - the core on its pedestal under a 7 x 7 lead glass cap (CAP_HALF), the Clone Capsule, the Ambassador's hall; the
 * guard square 21 x 21 round it (SQUARE_HALF: the armour station, 4 charge pads); the ring of 11 x 11 workshops (WORKSHOP); the
 * supply depots by the back gate; the greenhouse (decor - not one log in the buildings); the living cells;</li>
 * <li>the wall on the dome's radius - WALL_IN (its outer face), WALL_T thick, WALL_H high, four towers TOWER_H high on its diagonals;
 * the main gate (GATE_W wide) towards the Vokzal, the back gate opposite;</li>
 * <li><b>the Vokzal</b> («Вокзал прокола») outside the main gate: a VOKZAL_HALF x 2 + 1 (25 x 25) platform whose middle - the dock's
 * beacon, where flights and bridges land - is VOKZAL_OUT (18) past the dome's edge on the main street; its protected zone DOCK_ZONE
 * (13) across; the Great Ring («Большое кольцо», Ø 13: RING_R) stands upright across the street RING_BACK (6) further out, its middle
 * RING_DY (3) over the floor (its foot sunk into the platform), the vortex (5 x 5: VORTEX_HALF) in its middle reaching down to the
 * floor - a passenger walks into it; the Dispatchers' office at P_OFFICE;</li>
 * <li>the level ground - the disc of levelRadius = radius + LEVEL_OUT (31: the Vokzal's far corners fit) on the floor's height, then
 * BLEND (8) blocks back to the land's own height; the footprint is that disc's box (EmbassySiteSC.area, at most REACH_MAX).</li>
 * </ul>
 */
public final class EmbassyLayoutSC {

    /** The wall: its outer face this far in from the dome's edge; thickness, height; the towers' height; the gates' width. */
    public static final int WALL_IN = 3, WALL_T = 2, WALL_H = 8, TOWER_H = 12, GATE_W = 7;
    /** The Vokzal: its middle (the beacon) this far past the dome's edge; the platform's half (25 x 25). */
    public static final int VOKZAL_OUT = 18, VOKZAL_HALF = 12;
    /** The Great Ring: its radius (Ø 13), its plane this far past the beacon, its middle this high over the floor; the vortex's half (5 x 5). */
    public static final int RING_R = 6, RING_BACK = 6, RING_DY = 3, VORTEX_HALF = 2;
    /** ПР-7: an embassy's dock zone (SettlementRegistrySC.Settlement.dockZone) - the Vokzal's ring and office in it. */
    public static final int DOCK_ZONE = 13;
    /** The level ground: radius + LEVEL_OUT, then BLEND blocks back to the land's height. */
    public static final int LEVEL_OUT = 31, BLEND = 8;
    /** No block of an embassy is further than this from its core (96 + 31 + 8) - MapGenEmbassySC's range (10 chunks) covers it. */
    public static final int REACH_MAX = 135;
    /** ПЕ-6: the floor = clamp(FLOOR_BASE + round(FLOOR_K x the area's mean rootHeight), FLOOR_MIN, FLOOR_MAX) - plains, savanna, desert: 67. */
    public static final int FLOOR_BASE = 64, FLOOR_K = 24, FLOOR_MIN = 65, FLOOR_MAX = 72;
    /** The Heart's cap, the guard square's half (21 x 21), a workshop's side. */
    public static final int CAP_HALF = 3, SQUARE_HALF = 10, WORKSHOP = 11;

    /** Named points of an embassy (Settlement.points; P_PLAZA, P_SQUARE, P_CLONE, P_PAD + i, P_GATE, P_TOWER + i are the Substrate's). */
    public static final String P_RING = "ring", P_VOKZAL = "vokzal", P_OFFICE = "office", P_BACK_GATE = "backGate", P_DEPOT = "depot",
            P_HALL = "hall";
    /** The fixed points in the frame {a, p} (the plaza before the Heart, the square behind it, the capsule, the Ambassador's hall). */
    public static final int[] AT_PLAZA = {7, 0}, AT_SQUARE = {-7, 0}, AT_CLONE = {0, 6}, AT_HALL = {0, -6};
    /** The four charge pads on the guard square. */
    public static final int[][] PAD_AT = {{-8, -8}, {-8, 8}, {8, -8}, {8, 8}};
    /** Pads of an embassy (all four). */
    public static final int PADS = 4;

    /** Lot kinds of an embassy's plan (SettlementLayoutSC.Lot.kind; package S's pieces build by them). Append only. */
    public static final int LOT_HEART = 0, LOT_SQUARE = 1, LOT_WORKSHOP = 2, LOT_DEPOT = 3, LOT_GREENHOUSE = 4, LOT_HOUSE = 5, LOT_WALL = 6,
            LOT_TOWER = 7, LOT_GATE = 8, LOT_STREET = 9, LOT_VOKZAL = 10, LOT_RING = 11, LOT_OFFICE = 12, LOT_ENERGY = 13;

    /** ПЖ-13: the trades two of each (the Power Engineer one), the extras' pool (2..6 more: the size's 18..22). */
    public static final NpcKindSC[] TRADES = {NpcKindSC.TECHNOLOGIST, NpcKindSC.METALLURGIST, NpcKindSC.GAS_TECH, NpcKindSC.CARTOGRAPHER,
            NpcKindSC.SALVAGER};
    public static final NpcKindSC[] EXTRA = {NpcKindSC.QUARTERMASTER, NpcKindSC.TECHNOLOGIST, NpcKindSC.METALLURGIST, NpcKindSC.SALVAGER,
            NpcKindSC.POWER_ENGINEER};
    /** The settlers every embassy has before its extras: the Ambassador, 2 Dispatchers, 2 Quartermasters, 2 x 5 trades, the Power Engineer. */
    public static final int BASE_TUNERS = 1 + 2 + 2 + 2 * 5 + 1;

    private static final long GATE_SALT = 0x6A7EL, ROSTER_SALT = 0xE3B05L, NAME_SALT = 0xE3A3EL, PLAN_SALT = 0xE391AL;

    private EmbassyLayoutSC() {
    }

    /** An embassy's plan: the Substrate's plan (SettlementLayoutSC.Plan: core, dock, points, posts, lots, names, footprint) and its own. */
    public static final class Plan extends SettlementLayoutSC.Plan {
        public final EmbassySiteSC.Site embassy;
        /** The level ground's radius (radius + LEVEL_OUT); the back gate's direction (the main gate's opposite). */
        public int levelRadius, backGateDir;
        /** The Great Ring's middle (its plane across the main street). */
        public int ringX, ringY, ringZ;

        public Plan(EmbassySiteSC.Site site) {
            super(new SettlementLayoutSC.Site(site.x, site.z, SettlementSizeSC.EMBASSY, site.radius, false, site.seed));
            this.embassy = site;
        }

        /** The frame's world X / Z of (a, p). */
        public int x(int a, int p) {
            return coreX + a * SettlementLayoutSC.DIR_X[gateDir] - p * SettlementLayoutSC.DIR_Z[gateDir];
        }

        public int z(int a, int p) {
            return coreZ + a * SettlementLayoutSC.DIR_Z[gateDir] + p * SettlementLayoutSC.DIR_X[gateDir];
        }

        /** (x, z)'s frame coordinates {a, p} (the inverse of x / z). */
        public int[] frame(int x, int z) {
            int dx = x - coreX, dz = z - coreZ, ax = SettlementLayoutSC.DIR_X[gateDir], az = SettlementLayoutSC.DIR_Z[gateDir];
            return new int[]{dx * ax + dz * az, -dx * az + dz * ax};
        }

        /** A point of the frame at `y`. */
        public void framePoint(String name, int a, int p, int y) {
            point(name, x(a, p), y, z(a, p));
        }
    }

    // ------------------------------------------------------------------ pure numbers

    /** ПЕ-6: the floor of an area whose biomes' mean rootHeight is `meanRoot`. */
    public static int floorY(float meanRoot) {
        int y = FLOOR_BASE + Math.round(FLOOR_K * meanRoot);
        return Math.max(FLOOR_MIN, Math.min(FLOOR_MAX, y));
    }

    /** The main gate's direction by the site's seed (SettlementLayoutSC.NORTH..WEST). */
    public static int gateDir(long siteSeed) {
        return EmbassySiteSC.pick(EmbassySiteSC.mix(siteSeed ^ GATE_SALT, 0, 0), 4);
    }

    /** ПЕ-6: how far past the level ground the column (dx, dz) off the core lies (0 or less: on it). */
    public static double levelPast(int dx, int dz, int levelRadius) {
        return Math.sqrt((double) dx * dx + (double) dz * dz) - levelRadius;
    }

    /**
     * ПЕ-6: the column's new top (its top solid block) - on the level ground the floor, over the BLEND band eased back to the land's
     * own `natural` height (smoothstep), past it `natural` untouched. Pure (package G's ReplaceBiomeBlocks and the tests).
     */
    public static int targetY(int natural, int floorY, double past) {
        if (past <= 0D) {
            return floorY;
        }
        if (past >= BLEND) {
            return natural;
        }
        double t = past / BLEND, s = t * t * (3D - 2D * t);
        return (int) Math.round(floorY + (natural - floorY) * s);
    }

    /** The column (x, z) is on the Vokzal's platform of plan `p`. */
    public static boolean onVokzal(Plan p, int x, int z) {
        int[] f = p.frame(x, z);
        int a = f[0] - (p.radius + VOKZAL_OUT);
        return Math.abs(a) <= VOKZAL_HALF && Math.abs(f[1]) <= VOKZAL_HALF;
    }

    /**
     * ПЯ-15: (x, y, z) is in the vortex of plan `p`'s ring - the ring's plane (one block either side), within VORTEX_HALF of its middle
     * across, from the floor up to the ring's middle + VORTEX_HALF: where a passenger steps in. Pure.
     */
    public static boolean inVortex(Plan p, double x, double y, double z) {
        int[] f = p.frame((int) Math.floor(x), (int) Math.floor(z));
        int ringA = p.radius + VOKZAL_OUT + RING_BACK;
        return Math.abs(f[0] - ringA) <= 1 && Math.abs(f[1]) <= VORTEX_HALF && y >= p.floorY + 1 && y <= p.floorY + RING_DY + VORTEX_HALF + 1;
    }

    // ------------------------------------------------------------------ the roster (ПЖ-13)

    /**
     * ПЖ-13: an embassy's roster - the settlers first: the Ambassador (ELDER) at 0, the two Dispatchers, the Quartermasters (two and the
     * extras'), then two of each of TRADES, the Power Engineer, the other extras (2..6 of EXTRA by the seed - the size's 18..22 in all);
     * then the guards: 6 Sentries, 4 Senior Wardens, the Captain, the Commandant. Pure.
     */
    public static List<NpcKindSC> roster(EmbassySiteSC.Site s) {
        Random r = new Random(s.seed ^ ROSTER_SALT);
        SettlementSizeSC z = SettlementSizeSC.EMBASSY;
        int total = z.tunersMin + r.nextInt(z.tunersMax - z.tunersMin + 1);
        int[] count = new int[NpcKindSC.values().length];
        count[NpcKindSC.DISPATCHER.ordinal()] = 2;
        count[NpcKindSC.QUARTERMASTER.ordinal()] = 2;
        for (NpcKindSC k : TRADES) {
            count[k.ordinal()] = 2;
        }
        count[NpcKindSC.POWER_ENGINEER.ordinal()] = 1;
        for (int i = 0; i < total - BASE_TUNERS; i++) {
            count[EXTRA[r.nextInt(EXTRA.length)].ordinal()]++;
        }
        List<NpcKindSC> out = new ArrayList<NpcKindSC>();
        out.add(NpcKindSC.ELDER);
        NpcKindSC[] order = {NpcKindSC.DISPATCHER, NpcKindSC.QUARTERMASTER, NpcKindSC.TECHNOLOGIST, NpcKindSC.METALLURGIST, NpcKindSC.GAS_TECH,
                NpcKindSC.CARTOGRAPHER, NpcKindSC.SALVAGER, NpcKindSC.POWER_ENGINEER};
        for (NpcKindSC k : order) {
            for (int i = 0; i < count[k.ordinal()]; i++) {
                out.add(k);
            }
        }
        for (int i = 0; i < z.sentries; i++) {
            out.add(NpcKindSC.SENTRY);
        }
        for (int i = 0; i < z.seniors; i++) {
            out.add(NpcKindSC.SENIOR_WARDEN);
        }
        out.add(NpcKindSC.CAPTAIN);
        out.add(NpcKindSC.COMMANDANT);
        return out;
    }

    /** What breaks roster()'s rules in `roster` (null: nothing) - the self-test's check. */
    public static String rosterProblem(List<NpcKindSC> roster) {
        if (roster.isEmpty() || roster.get(0) != NpcKindSC.ELDER) {
            return "the Ambassador isn't first (" + roster + ")";
        }
        int[] n = new int[NpcKindSC.values().length];
        int tuners = 0;
        boolean guards = false;
        for (NpcKindSC k : roster) {
            n[k.ordinal()]++;
            if (k.guard) {
                guards = true;
            } else if (guards) {
                return "a settler after the guards (" + roster + ")";
            } else {
                tuners++;
            }
        }
        SettlementSizeSC z = SettlementSizeSC.EMBASSY;
        if (tuners < z.tunersMin || tuners > z.tunersMax || n[NpcKindSC.ELDER.ordinal()] != 1 || n[NpcKindSC.DISPATCHER.ordinal()] != 2
                || n[NpcKindSC.QUARTERMASTER.ordinal()] < 2 || n[NpcKindSC.POWER_ENGINEER.ordinal()] < 1 || n[NpcKindSC.ARMOURER.ordinal()] != 0) {
            return tuners + " settlers, " + n[NpcKindSC.DISPATCHER.ordinal()] + " Dispatchers, " + n[NpcKindSC.QUARTERMASTER.ordinal()] + " Quartermasters";
        }
        for (NpcKindSC k : TRADES) {
            if (n[k.ordinal()] < 2) {
                return "fewer than two of " + k.key;
            }
        }
        if (n[NpcKindSC.SENTRY.ordinal()] != z.sentries || n[NpcKindSC.SENIOR_WARDEN.ordinal()] != z.seniors || n[NpcKindSC.CAPTAIN.ordinal()] != 1
                || n[NpcKindSC.COMMANDANT.ordinal()] != 1) {
            return "guards " + n[NpcKindSC.SENTRY.ordinal()] + " / " + n[NpcKindSC.SENIOR_WARDEN.ordinal()] + " / " + n[NpcKindSC.CAPTAIN.ordinal()]
                    + " / " + n[NpcKindSC.COMMANDANT.ordinal()];
        }
        return null;
    }

    // ------------------------------------------------------------------ the plan

    /**
     * An embassy's plan at `floorY` (the site's own - EmbassySiteSC.Site.floorY - for a new one; a lost record's core's): the frame,
     * the fixed points and the Vokzal (here), the quarters - lots and one post a roster entry, in its order (EmbassyQuartersSC.lay,
     * package S), the names, the footprint. Pure; a few milliseconds - once per structure start.
     */
    public static Plan plan(long worldSeed, EmbassySiteSC.Site site, int floorY) {
        Plan p = new Plan(site);
        int r = site.radius;
        p.floorY = floorY;
        p.coreX = site.x;
        p.coreY = floorY + 1;                                  // on its pedestal, as a settlement's
        p.coreZ = site.z;
        p.radius = r;
        p.gateDir = gateDir(site.seed);
        p.backGateDir = (p.gateDir + 2) & 3;
        p.levelRadius = r + LEVEL_OUT;
        p.dock = true;
        int beacon = r + VOKZAL_OUT;
        p.dockX = p.x(beacon, 0);
        p.dockY = floorY;
        p.dockZ = p.z(beacon, 0);
        p.ringX = p.x(beacon + RING_BACK, 0);
        p.ringY = floorY + RING_DY;
        p.ringZ = p.z(beacon + RING_BACK, 0);
        int stand = floorY + 1;
        p.framePoint(SettlementRegistrySC.P_PLAZA, AT_PLAZA[0], AT_PLAZA[1], stand);
        p.framePoint(SettlementRegistrySC.P_SQUARE, AT_SQUARE[0], AT_SQUARE[1], stand);
        p.framePoint(SettlementRegistrySC.P_CLONE, AT_CLONE[0], AT_CLONE[1], stand);
        p.framePoint(P_HALL, AT_HALL[0], AT_HALL[1], stand);
        for (int i = 0; i < PADS; i++) {
            p.framePoint(SettlementRegistrySC.P_PAD + i, PAD_AT[i][0], PAD_AT[i][1], stand);
        }
        int wall = r - WALL_IN - WALL_T;                       // the wall's inner face
        p.framePoint(SettlementRegistrySC.P_GATE, wall - 2, 2, stand);
        p.framePoint(P_BACK_GATE, -(wall - 2), -2, stand);
        p.framePoint(P_DEPOT, -(wall - 12), 0, stand);
        int diag = (int) Math.round((r - WALL_IN - 1) / Math.sqrt(2D));
        int[][] towers = {{diag, -diag}, {diag, diag}, {-diag, diag}, {-diag, -diag}};
        for (int i = 0; i < towers.length; i++) {
            p.framePoint(SettlementRegistrySC.P_TOWER + i, towers[i][0], towers[i][1], floorY + TOWER_H - 1);
        }
        p.framePoint(P_VOKZAL, beacon, 0, stand);
        p.framePoint(P_RING, beacon + RING_BACK, 0, p.ringY);
        p.framePoint(P_OFFICE, beacon - 6, -8, stand);
        List<NpcKindSC> roster = roster(site);
        EmbassyQuartersSC.lay(p, roster, new Random(site.seed ^ PLAN_SALT));
        names(p);
        int e = p.levelRadius + BLEND;
        p.footprint = new int[]{p.coreX - e, p.coreZ - e, p.coreX + e, p.coreZ + e};
        return p;
    }

    /** The embassy's name and its creatures' (NpcNamesSC), from its seed, none twice. */
    private static void names(Plan p) {
        Random r = new Random(p.site.seed ^ NAME_SALT);
        Set<Integer> taken = new HashSet<Integer>();
        int own = NpcNamesSC.random(r);
        taken.add(Integer.valueOf(own));
        p.nameElement = NpcNamesSC.element(own);
        p.nameNumber = NpcNamesSC.number(own);
        for (SettlementLayoutSC.Post post : p.posts) {
            int code;
            do {
                code = NpcNamesSC.random(r);
            } while (!taken.add(Integer.valueOf(code)));
            post.name = code;
        }
    }

    /** {minCX, minCZ, maxCX, maxCZ}: the chunks of the plan's footprint. */
    public static int[] footprintChunks(Plan p) {
        return new int[]{p.footprint[0] >> 4, p.footprint[1] >> 4, p.footprint[2] >> 4, p.footprint[3] >> 4};
    }

    // ------------------------------------------------------------------ the record

    /** ПЕ-8: the registry's record of a plan - the site, the dome, the Vokzal's dock and zone, the points, the slots (NEVER). */
    public static SettlementRegistrySC.Settlement record(Plan p, int dim, long now) {
        SettlementRegistrySC.Settlement s = new SettlementRegistrySC.Settlement();
        s.dim = dim;
        s.x = p.coreX;
        s.y = p.coreY;
        s.z = p.coreZ;
        s.floorY = p.floorY;
        s.radius = p.radius;
        s.nameElement = p.nameElement;
        s.nameNumber = p.nameNumber;
        s.version = SettlementRegistrySC.VERSION;
        s.madeAt = now;
        fill(s, p);
        for (int i = 0; i < p.posts.size(); i++) {
            SettlementLayoutSC.Post q = p.posts.get(i);
            SettlementRegistrySC.Slot sl = s.slots.get(i);
            sl.nameElement = NpcNamesSC.element(q.name);
            sl.nameNumber = NpcNamesSC.number(q.name);
        }
        return s;
    }

    /**
     * The plan's size, seed, Vokzal (dock, its zone), points and slots (NEVER, unnamed) into record `s` - its id, core, radius, floor
     * and name are left as they are (RosterSC.restore names the slots itself). Pure.
     */
    public static void fill(SettlementRegistrySC.Settlement s, Plan p) {
        s.size = SettlementSizeSC.EMBASSY.ordinal();
        s.seed = p.site.seed;
        s.first = false;
        s.hasDock = true;
        s.dockX = p.dockX;
        s.dockY = p.dockY;
        s.dockZ = p.dockZ;
        s.dockZone = DOCK_ZONE;
        s.points.clear();
        for (int i = 0; i < p.pointNames.size() && i < p.points.size(); i++) {
            s.points.put(p.pointNames.get(i), p.points.get(i).clone());
        }
        s.slots.clear();
        for (SettlementLayoutSC.Post q : p.posts) {
            SettlementRegistrySC.Slot sl = new SettlementRegistrySC.Slot();
            sl.kind = q.kind.ordinal();
            sl.postX = q.x;
            sl.postY = q.y;
            sl.postZ = q.z;
            sl.homeX = q.homeX;
            sl.homeY = q.homeY;
            sl.homeZ = q.homeZ;
            sl.state = SettlementRegistrySC.NEVER;
            s.slots.add(sl);
        }
    }

    /** Record `s`'s plan made again (its site from its own core, seed and radius) - the Vokzal's and the ring's points for package R. */
    public static Plan planOf(SettlementRegistrySC.Settlement s, long worldSeed) {
        EmbassySiteSC.Site site = new EmbassySiteSC.Site(s.x, s.z, s.radius, s.floorY, s.seed, false);
        return plan(worldSeed, site, s.floorY);
    }
}
