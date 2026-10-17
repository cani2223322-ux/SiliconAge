package com.sc.settle.gen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.sc.block.BlockShowcase2SC;
import com.sc.block.BlockShowcaseSC;
import com.sc.dim.SubstrateTerrainSC;
import com.sc.settle.NpcKindSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSizeSC;
import com.sc.settle.npc.NpcNamesSC;

/**
 * PACKAGE S (docs/plan-substrate-tech-f2.md §4.1-§4.3, rules ПП-1..ПП-4): where the settlements stand and how they
 * are laid out - pure maths of the world seed (no World: the structure start, the self-test and the handbook ask the
 * same thing).
 * <ul>
 * <li>One region = REGION blocks (32 x 32 chunks) = 2 x 2 plateau cells (CELL = the Scribe grid, 256). A region has a
 * settlement with CHANCE_PCT %, in one of its cells (never the spawn's cell 0, 0), at the cell's middle +-JITTER - so
 * it always sits on the plateau, its dome, belt and dock clear of the canyon belts, and two are at least
 * CELL - 2 x JITTER apart. Region (0, 0) always has one: «Первый причал», a SETTLEMENT in cell (FIRST_CELL_X,
 * FIRST_CELL_Z) - some 256 blocks from the world's spawn (128, 128).</li>
 * <li>Its size by SIZE_WEIGHTS (outpost / settlement / city: a city to some 3..4 settlements); its radius by the
 * size's own seed (SettlementSizeSC.radius); its floor - the median of the terraces under its dome (less cut and fill
 * than the middle's own terrace).</li>
 * <li>The plan, from the middle out (proposal §5 «Планировка»): the Heart (the core on its pedestal under a 7 x 7 cap of
 * lead glass), the guard square 15 x 15 (armour station, 2..4 charge pads, the Clone Capsule), the ring of workshops
 * (one per Tuner profession of the roster), the living cells, 2 watch towers, the gate (the decontamination shower,
 * the guard post) and - Settlement and City - the dock 7 x 7 outside the gate, wholly outside the dome.</li>
 * <li>Ф3 (docs/plan-substrate-tech-f3.md §6.2, §6.3; rules ПЖ-8, ПЖ-9, ПГ-11, ПГ-12): the roster of record version 2 -
 * the Power Engineer, the Armourer and the Salvager among the Tuners, the Captain (by SettlementSizeSC.captain) and the
 * City's Commandant after the Senior Wardens; the Power Engineer's workshop (its roof - the wind generator and the
 * solar panels - a block higher: WORKSHOP_ROOF_H) and the Salvager's warehouse join the ring (a Settlement's and a
 * City's six workshops: the square's four corners, then its sides - RING_SIDES); the Armourer has none - he works at
 * the guard square's armour station (AT_ARMOURER), the Captain stands at P_SQUARE, the Commandant at P_PLAZA. A
 * workshop's showcases are workshopLine's codes (the first block's metas, SHOWCASE2 + the second's).</li>
 * </ul>
 * The plan is worked out in the settlement's own frame: `a` along the main street towards the gate (gateDir), `p`
 * across it; the lots are placed in order (the gate, the workshops, the towers, the cells), each at the nearest free
 * spot to where it wants to stand that fits under the dome's shell (a hemisphere from the floor: a building's far top
 * corner keeps a block clear of it) and keeps a block's lane to its neighbours. Then everything goes into world
 * coordinates: the lots (Lot - what the pieces build), the points, the posts.
 * <p>
 * The signatures and the data classes' fields are fixed (packages C, N, D read them; S may add fields, never remove);
 * the bodies are package S's.
 */
public final class SettlementLayoutSC {

    /** A region a side, and the plateau cell (= SubstrateTerrainSC.GRID). */
    public static final int REGION = 512, CELL = 256;
    /** A region has a settlement with this chance, %; its middle lies within this of its cell's middle. TODO(balance) */
    public static final int CHANCE_PCT = 55, JITTER = 16;
    /** Outpost / Settlement / City weights (a city to some 3..4 settlements). TODO(balance) */
    public static final int[] SIZE_WEIGHTS = {48, 40, 12};
    /** «Первый причал»: region (0, 0), this cell. */
    public static final int FIRST_CELL_X = 1, FIRST_CELL_Z = 0;

    /** The guard square's half size (15 x 15) round the core; the Heart's lead glass cap's (7 x 7). */
    public static final int SQUARE_HALF = 7, CAP_HALF = 3;
    /** The dock's beacon this far past the dome's edge on the gate's axis; the plate's half size (7 x 7). */
    public static final int DOCK_OUT = 8, DOCK_HALF = 3;
    /** The ramp round the level ground: this many blocks, at most a block of height per block; more is a wall. TODO(design) */
    public static final int RAMP = 4;
    /** No block of a footprint is further than this from the core (a city's dock and its ramp): see footprintChunks. */
    public static final int REACH_MAX = 80;
    /** Lot sizes: workshops (a City's are bigger), living cells, towers, the gate (across x along the street). */
    public static final int WORKSHOP = 9, WORKSHOP_CITY = 11, HOUSE = 5, TOWER = 5, GATE_W = 7, GATE_D = 5;
    /** The lots' heights: their top face this many blocks over the floor (the dome fit). */
    public static final int WORKSHOP_H = 6, HOUSE_H = 5, TOWER_H = 11, GATE_H = 6, HEART_H = 7;
    /** Ф3: the Power Engineer's workshop - the wind generator and the solar panels stand on its roof (one block over WORKSHOP_H). */
    public static final int WORKSHOP_ROOF_H = 7;
    /** A tower's platform: one stands this many blocks over the floor (P_TOWER*). */
    public static final int TOWER_STAND = 10;
    /** Charge pads on the guard square: Outpost / Settlement / City. */
    public static final int[] PADS = {2, 3, 4};
    /** Homes in a living cell: two as a rule, three when the cells don't all fit. */
    public static final int HOMES = 2, HOMES_MAX = 3;

    /** Lot kinds (Lot.kind). */
    public static final int LOT_HEART = 0, LOT_STREET = 1, LOT_GATE = 2, LOT_TOWER = 3, LOT_WORKSHOP = 4, LOT_HOUSE = 5, LOT_DOCK = 6;
    /** Living cells' looks (Lot.variant of LOT_HOUSE): brick, oxide crust, metal. */
    public static final int HOUSE_VARIANTS = 3;
    /** World directions (Lot.facing, Plan.gateDir): north (-Z), east (+X), south (+Z), west (-X). */
    public static final int NORTH = 0, EAST = 1, SOUTH = 2, WEST = 3;
    public static final int[] DIR_X = {0, 1, 0, -1}, DIR_Z = {-1, 0, 1, 0};

    /** Points of the Heart's square in the settlement's frame (a towards the gate, p across): the plaza, the square, the Clone Capsule. */
    public static final int[] AT_PLAZA = {5, 0}, AT_SQUARE = {-5, 0}, AT_CLONE = {0, 4}, AT_ELDER = {4, -1};
    /** The Clone Capsule, the armour station (with its two lamp posts) - not walkable. */
    public static final int[] CAPSULE = {0, 5}, STATION = {-6, 0};
    /** The charge pads on the square (the back two first), in the settlement's frame. */
    public static final int[][] PAD_AT = {{-5, -5}, {-5, 5}, {5, -5}, {5, 5}};
    /** Two Sentries spend the night by the Heart, here. */
    public static final int[][] NIGHT_AT = {{-4, -2}, {-4, 2}};
    /**
     * Ф3 (ПЖ-8): the Armourers' places by the armour station's front (the station at STATION, P_SQUARE - the Captain's -
     * before it): the first beside P_SQUARE, a City's others on its other side and a step out.
     */
    public static final int[][] AT_ARMOURER = {{-5, 1}, {-5, -1}, {-4, 2}, {-4, -2}};

    /** Ф3 (ПЖ-9): the seven trading professions - one of each in a Settlement, two of each in a City, in this order in the roster. */
    public static final NpcKindSC[] TRADES = {NpcKindSC.TECHNOLOGIST, NpcKindSC.METALLURGIST, NpcKindSC.GAS_TECH, NpcKindSC.CARTOGRAPHER,
            NpcKindSC.POWER_ENGINEER, NpcKindSC.ARMOURER, NpcKindSC.SALVAGER};
    /** ПЖ-9: an Outpost's Cartographer has 1..3 different ones of these with him; a Settlement's 0..4 more Tuners are any of SETTLEMENT_EXTRA. */
    public static final NpcKindSC[] OUTPOST_EXTRA = {NpcKindSC.TECHNOLOGIST, NpcKindSC.METALLURGIST, NpcKindSC.GAS_TECH, NpcKindSC.SALVAGER},
            SETTLEMENT_EXTRA = {NpcKindSC.TECHNOLOGIST, NpcKindSC.METALLURGIST, NpcKindSC.GAS_TECH, NpcKindSC.POWER_ENGINEER};
    /** Ф3: a workshopLine code from here on is a BlockShowcase2SC meta (code - SHOWCASE2); below it - a BlockShowcaseSC meta. */
    public static final int SHOWCASE2 = 16;
    /**
     * Ф3: a Settlement's and a City's workshops past the square's four corners stand at its sides (in the settlement's
     * frame, a multiple of the ring's offset c): left, right, behind - never in front (the street).
     */
    public static final int[][] RING_SIDES = {{0, -1}, {0, 1}, {-1, 0}};

    private static final long SITE_SALT = 0x5E771EL, ROSTER_SALT = 0x2057E2L, NAME_SALT = 0x4A3E5L, PLAN_SALT = 0x91A4L;
    /** The square's fixtures (no post on them), in the settlement's frame. */
    private static final int[][] SQUARE_BLOCKED = {{-6, 0}, {-6, -1}, {-6, 1}, {0, 5}, {-1, 5}, {1, 5}};

    private SettlementLayoutSC() {
    }

    /** Where a region's settlement stands (pure). */
    public static final class Site {
        /** The core's column. */
        public final int x, z;
        public final SettlementSizeSC size;
        public final int radius;
        /** «Первый причал». */
        public final boolean first;
        /** The settlement's own seed (prices, names, roster). */
        public final long seed;

        public Site(int x, int z, SettlementSizeSC size, int radius, boolean first, long seed) {
            this.x = x;
            this.z = z;
            this.size = size;
            this.radius = radius;
            this.first = first;
            this.seed = seed;
        }
    }

    /** One creature's places in a plan: its kind, where it works / stands guard, where it sleeps. */
    public static final class Post {
        public final NpcKindSC kind;
        public final int x, y, z, homeX, homeY, homeZ;
        /** Its name's code (NpcNamesSC; none twice in a settlement, nor the settlement's own). */
        public int name;

        public Post(NpcKindSC kind, int x, int y, int z, int homeX, int homeY, int homeZ) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.z = z;
            this.homeX = homeX;
            this.homeY = homeY;
            this.homeZ = homeZ;
        }
    }

    /**
     * One building plot of a plan, in world coordinates: [x0..x1] x [z0..z1] (inclusive), its entrance facing `facing`
     * (NORTH..WEST). The pieces build in the lot's own frame: u across (0..width-1), v from the entrance (0) inwards.
     * The Heart, the street, the gate and the dock face the gate's way: their v runs from the gate side back.
     */
    public static final class Lot {
        public final int kind, x0, z0, x1, z1, facing;
        /** A workshop: its NpcKindSC ordinal; a living cell: its look (0..HOUSE_VARIANTS-1); a tower: its index. */
        public final int variant;
        /** Its top face over the floor. */
        public final int height;

        public Lot(int kind, int x0, int z0, int x1, int z1, int facing, int variant, int height) {
            this.kind = kind;
            this.x0 = x0;
            this.z0 = z0;
            this.x1 = x1;
            this.z1 = z1;
            this.facing = facing;
            this.variant = variant;
            this.height = height;
        }

        /** Across the entrance (u). */
        public int width() {
            return facing == NORTH || facing == SOUTH ? x1 - x0 + 1 : z1 - z0 + 1;
        }

        /** From the entrance inwards (v). */
        public int depth() {
            return facing == NORTH || facing == SOUTH ? z1 - z0 + 1 : x1 - x0 + 1;
        }

        public int wx(int u, int v) {
            return frameX(x0, x1, facing, u, v);
        }

        public int wz(int u, int v) {
            return frameZ(z0, z1, facing, u, v);
        }
    }

    /** A site's plan (pure): the core, the floor, the named points (SettlementRegistrySC.P_*), the dock, the roster's posts. */
    public static final class Plan {
        public final Site site;
        public int coreX, coreY, coreZ, floorY;
        public boolean dock;
        /** The dock's beacon (the middle of its 7 x 7). */
        public int dockX, dockY, dockZ;
        /** SettlementRegistrySC.P_* -> {x, y, z}: the plaza, the guard square, the Clone Capsule, the pads, the gate, the towers. */
        public final List<String> pointNames = new ArrayList<String>();
        public final List<int[]> points = new ArrayList<int[]>();
        /** Villagers first (the Elder at 0), then the guards. */
        public final List<Post> posts = new ArrayList<Post>();
        /** {minX, minZ, maxX, maxZ}: every block the structure may set (the dome and the dock, with the ramp round them). */
        public int[] footprint = new int[4];
        /** The dome's radius (= site.radius); the way the gate (and the dock past it) lies from the core (NORTH..WEST). */
        public int radius, gateDir;
        /** The settlement's name (NpcNamesSC element index and number). */
        public int nameElement, nameNumber;
        /** What the pieces build, in build order after the ground: the Heart, the street, the gate, workshops, towers, cells, the dock. */
        public final List<Lot> lots = new ArrayList<Lot>();

        public Plan(Site site) {
            this.site = site;
        }

        public void point(String name, int x, int y, int z) {
            pointNames.add(name);
            points.add(new int[]{x, y, z});
        }

        /** A point by name, or null. */
        public int[] pointOf(String name) {
            int i = pointNames.indexOf(name);
            return i < 0 ? null : points.get(i);
        }

        /** The block column (x, z) is under the dome: its middle within the radius of the core's (as SettlementRegistrySC.contains). */
        public boolean insideDome(int x, int z) {
            long dx = x - coreX, dz = z - coreZ;
            return dx * dx + dz * dz <= (long) radius * radius;
        }
    }

    /** The region (REGION blocks) of a block coordinate. */
    public static int regionOf(int coord) {
        return coord >> 9;
    }

    /** Region (rx, rz)'s settlement, or null (none there). Region (0, 0): always «Первый причал». Cheap: asked 289 times a chunk. */
    public static Site site(long worldSeed, int rx, int rz) {
        long h = mix(worldSeed ^ SITE_SALT, rx, rz);
        boolean first = rx == 0 && rz == 0;
        int cellX, cellZ;
        if (first) {
            cellX = FIRST_CELL_X;
            cellZ = FIRST_CELL_Z;
        } else {
            if (pick(h, 100) >= CHANCE_PCT) {
                return null;
            }
            int c = pick(mix(h, 1, 0), 4);
            cellX = rx * 2 + (c & 1);
            cellZ = rz * 2 + (c >> 1);
            if (cellX == 0 && cellZ == 0) {
                return null;                                   // the spawn's cell (only region 0, 0 has it: never here)
            }
        }
        int x = cellX * CELL + CELL / 2 + pick(mix(h, 2, 0), 2 * JITTER + 1) - JITTER;
        int z = cellZ * CELL + CELL / 2 + pick(mix(h, 3, 0), 2 * JITTER + 1) - JITTER;
        long seed = mix(h, 4, 0);
        SettlementSizeSC size = first ? SettlementSizeSC.SETTLEMENT : sizeOf(pick(mix(h, 5, 0), 100));
        return new Site(x, z, size, size.radius(seed), first, seed);
    }

    /** The size of a 0..99 roll by SIZE_WEIGHTS (they sum to 100). */
    private static SettlementSizeSC sizeOf(int roll) {
        SettlementSizeSC[] all = SettlementSizeSC.values();
        int acc = 0;
        for (int i = 0; i < SIZE_WEIGHTS.length && i < all.length; i++) {
            acc += SIZE_WEIGHTS[i];
            if (roll < acc) {
                return all[i];
            }
        }
        return SettlementSizeSC.OUTPOST;
    }

    /** The site whose core column lies in chunk (cx, cz), or null - MapGenSettlementSC.canSpawnStructureAtCoords. */
    public static Site siteAtChunk(long worldSeed, int cx, int cz) {
        Site s = site(worldSeed, regionOf(cx << 4), regionOf(cz << 4));
        return s != null && s.x >> 4 == cx && s.z >> 4 == cz ? s : null;
    }

    /** The site whose footprint may hold column (x, z) - its region's, if any (a footprint never leaves its plateau cell). */
    public static Site siteNear(long worldSeed, int x, int z) {
        Site s = site(worldSeed, regionOf(x), regionOf(z));
        return s != null && Math.abs(x - s.x) <= REACH_MAX && Math.abs(z - s.z) <= REACH_MAX ? s : null;
    }

    /** The settlement's floor: the median of the terraces under the dome (sampled every 4 blocks) - its top solid block. */
    public static int floorY(long worldSeed, Site s) {
        int n = s.radius / 4;
        int[] ys = new int[(2 * n + 1) * (2 * n + 1)];
        int k = 0;
        for (int i = -n; i <= n; i++) {
            for (int j = -n; j <= n; j++) {
                if (i * i + j * j <= n * n) {
                    ys[k++] = SubstrateTerrainSC.terraceY(worldSeed, s.x + i * 4, s.z + j * 4);
                }
            }
        }
        int[] got = Arrays.copyOf(ys, k);
        Arrays.sort(got);
        return got[k / 2];
    }

    /**
     * The roster of a site - record version 2 (Ф3, ПЖ-9, ПГ-11, ПГ-12; §5 «Размеры»): the villagers first - the Elder at
     * 0, then the trading professions in TRADES order -, then the guards: the Sentries, the Senior Wardens, the Captain
     * (SettlementSizeSC.captain by the site's seed), the Commandant (the City). The settlers:
     * <ul>
     * <li>Outpost (3..5): the Elder, a Cartographer and 1..3 different ones of OUTPOST_EXTRA;</li>
     * <li>Settlement (8..12): the Elder, one of each of the seven TRADES and 0..4 more, any of SETTLEMENT_EXTRA;</li>
     * <li>City (15..20): the Elder, two of each of the seven and the rest evenly (0..5 of them, none twice).</li>
     * </ul>
     * Pure: the plan, the self-test (rosterProblem) and RosterSC.restore ask the same.
     */
    public static List<NpcKindSC> roster(Site s) {
        Random r = new Random(s.seed ^ ROSTER_SALT);
        SettlementSizeSC z = s.size;
        int total = z.tunersMin + r.nextInt(z.tunersMax - z.tunersMin + 1);
        int[] count = new int[NpcKindSC.values().length];
        if (z == SettlementSizeSC.OUTPOST) {
            count[NpcKindSC.CARTOGRAPHER.ordinal()] = 1;       // the Cartographer + 1..3 of the four, all different
            List<NpcKindSC> extra = new ArrayList<NpcKindSC>(Arrays.asList(OUTPOST_EXTRA));
            Collections.shuffle(extra, r);
            for (int i = 0; i < total - 2 && i < extra.size(); i++) {
                count[extra.get(i).ordinal()]++;
            }
        } else if (z == SettlementSizeSC.SETTLEMENT) {
            for (NpcKindSC k : TRADES) {                       // one of each + 0..4 more, any of the four
                count[k.ordinal()] = 1;
            }
            for (int i = 0; i < total - 1 - TRADES.length; i++) {
                count[SETTLEMENT_EXTRA[r.nextInt(SETTLEMENT_EXTRA.length)].ordinal()]++;
            }
        } else {
            for (NpcKindSC k : TRADES) {                       // two of each + the rest evenly
                count[k.ordinal()] = 2;
            }
            List<NpcKindSC> order = new ArrayList<NpcKindSC>(Arrays.asList(TRADES));
            Collections.shuffle(order, r);
            for (int i = 0; i < total - 1 - 2 * TRADES.length; i++) {
                count[order.get(i % order.size()).ordinal()]++;
            }
        }
        List<NpcKindSC> out = new ArrayList<NpcKindSC>();
        out.add(NpcKindSC.ELDER);
        for (NpcKindSC k : TRADES) {
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
        if (z.captain(s.seed)) {
            out.add(NpcKindSC.CAPTAIN);
        }
        if (z.commandant) {
            out.add(NpcKindSC.COMMANDANT);
        }
        return out;
    }

    /**
     * What breaks §6.2's roster rules in `roster` for a site of `size` and `seed` (null: nothing) - the self-test's check of
     * roster() (and of a record's kinds). The order: the Elder (one, first), the settlers, the Sentries, the Senior Wardens,
     * the Captain, the Commandant; the counts by size as roster() says.
     */
    public static String rosterProblem(SettlementSizeSC size, long seed, List<NpcKindSC> roster) {
        int[] n = new int[NpcKindSC.values().length];
        int tuners = 0, last = -1;
        NpcKindSC[] guardOrder = {NpcKindSC.SENTRY, NpcKindSC.SENIOR_WARDEN, NpcKindSC.CAPTAIN, NpcKindSC.COMMANDANT};
        for (int i = 0; i < roster.size(); i++) {
            NpcKindSC k = roster.get(i);
            n[k.ordinal()]++;
            int rank = k.guard ? Arrays.asList(guardOrder).indexOf(k) : -1;
            if (k.guard && rank < 0) {
                return "a guard of kind " + k;
            }
            if (rank < last) {
                return (k.guard ? "a " + k + " after a " + guardOrder[last] : "a settler after the guards") + " (" + roster + ")";
            }
            last = Math.max(last, rank);
            if (!k.guard) {
                tuners++;
                if (k == NpcKindSC.ELDER && i != 0) {
                    return "the Elder isn't first (" + roster + ")";
                }
            }
        }
        if (roster.isEmpty() || roster.get(0) != NpcKindSC.ELDER || n[NpcKindSC.ELDER.ordinal()] != 1) {
            return "the Elder isn't the one and first (" + roster + ")";
        }
        if (tuners < size.tunersMin || tuners > size.tunersMax || n[NpcKindSC.SENTRY.ordinal()] != size.sentries
                || n[NpcKindSC.SENIOR_WARDEN.ordinal()] != size.seniors || n[NpcKindSC.CAPTAIN.ordinal()] != (size.captain(seed) ? 1 : 0)
                || n[NpcKindSC.COMMANDANT.ordinal()] != (size.commandant ? 1 : 0)) {
            return size + ": " + tuners + " settlers, guards " + n[NpcKindSC.SENTRY.ordinal()] + " / " + n[NpcKindSC.SENIOR_WARDEN.ordinal()] + " / "
                    + n[NpcKindSC.CAPTAIN.ordinal()] + " / " + n[NpcKindSC.COMMANDANT.ordinal()];
        }
        StringBuilder counts = new StringBuilder();
        for (NpcKindSC k : TRADES) {
            counts.append(' ').append(k.key).append(' ').append(n[k.ordinal()]);
        }
        if (size == SettlementSizeSC.OUTPOST) {
            int extra = 0;
            for (NpcKindSC k : TRADES) {
                boolean allowed = k == NpcKindSC.CARTOGRAPHER || Arrays.asList(OUTPOST_EXTRA).contains(k);
                if (!allowed && n[k.ordinal()] > 0 || n[k.ordinal()] > 1) {
                    return size + ":" + counts;
                }
                extra += k == NpcKindSC.CARTOGRAPHER ? 0 : n[k.ordinal()];
            }
            return n[NpcKindSC.CARTOGRAPHER.ordinal()] != 1 || extra < 1 || extra > 3 ? size + ":" + counts : null;
        }
        int need = size == SettlementSizeSC.CITY ? 2 : 1;
        for (NpcKindSC k : TRADES) {
            int more = n[k.ordinal()] - need;
            if (more < 0 || size == SettlementSizeSC.CITY && more > 1 || size == SettlementSizeSC.SETTLEMENT && more > 0
                    && !Arrays.asList(SETTLEMENT_EXTRA).contains(k)) {
                return size + ":" + counts;
            }
        }
        return null;
    }

    /** The professions with a workshop of a roster, in its order, each once (ПЖ-8: not the Elder - the Heart - nor the Armourer - the square). */
    public static List<NpcKindSC> workshopKinds(List<NpcKindSC> roster) {
        List<NpcKindSC> out = new ArrayList<NpcKindSC>();
        for (NpcKindSC k : roster) {
            if (!k.guard && k != NpcKindSC.ELDER && k != NpcKindSC.ARMOURER && !out.contains(k)) {
                out.add(k);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ workshops (shared with ComponentSetWorkshopSC)

    /** A workshop's side (WORKSHOP; a City's WORKSHOP_CITY). */
    public static int workshopSize(SettlementSizeSC size) {
        return size == SettlementSizeSC.CITY ? WORKSHOP_CITY : WORKSHOP;
    }

    /** A profession's workshop height over the floor for the dome fit: WORKSHOP_H; the Power Engineer's, with its roof's machines, WORKSHOP_ROOF_H. */
    public static int workshopHeight(NpcKindSC kind) {
        return kind == NpcKindSC.POWER_ENGINEER ? WORKSHOP_ROOF_H : WORKSHOP_H;
    }

    /** The machine spots of a w x w workshop in its frame: {u, v, du, dv} - the machine, and the way it faces (its tender stands there). */
    public static int[][] workshopSpots(int w) {
        int c = w / 2, back = w - 2;
        List<int[]> l = new ArrayList<int[]>();
        l.add(new int[]{c, back, 0, -1});
        l.add(new int[]{c - 2, back, 0, -1});
        l.add(new int[]{c + 2, back, 0, -1});
        for (int v = w - 4; v >= 3; v -= 2) {
            l.add(new int[]{1, v, 1, 0});
            l.add(new int[]{w - 2, v, -1, 0});
        }
        return l.toArray(new int[l.size()][]);
    }

    /**
     * The showcases of a profession's workshop, spot by spot (workshopSpots): a code below SHOWCASE2 is a BlockShowcaseSC
     * meta, from SHOWCASE2 on a BlockShowcase2SC one (code - SHOWCASE2). The Cartographer's one stands in the middle. Ф3:
     * the Power Engineer - two energy storages round a transformer at the back, two charge pads set in the floor at the
     * sides (the Armourer has no workshop); the Salvager's warehouse - the scrap pile between two racks at the back, crates
     * at the sides (ComponentSetWorkshopSC stacks the warehouse's racks and crates two high and heaps the pile).
     */
    public static int[] workshopLine(NpcKindSC kind) {
        switch (kind) {
            case TECHNOLOGIST:
                return new int[]{BlockShowcaseSC.CVD, BlockShowcaseSC.CZOCHRALSKI, BlockShowcaseSC.STEPPER, BlockShowcaseSC.ETCH_BATH,
                        BlockShowcaseSC.ION_IMPLANTER};
            case METALLURGIST:
                return new int[]{BlockShowcaseSC.BLAST_FURNACE, BlockShowcaseSC.CRUSHER, BlockShowcaseSC.ORE_WASHER};
            case GAS_TECH:
                return new int[]{BlockShowcaseSC.AIR_SEPARATOR, BlockShowcaseSC.CENTRIFUGE, BlockShowcaseSC.GAS_TANK, BlockShowcaseSC.GAS_TANK,
                        BlockShowcaseSC.GAS_TANK};
            case CARTOGRAPHER:
                return new int[]{BlockShowcaseSC.FOCUSER};
            case POWER_ENGINEER:
                return new int[]{SHOWCASE2 + BlockShowcase2SC.TRANSFORMER, SHOWCASE2 + BlockShowcase2SC.ENERGY_STORAGE,
                        SHOWCASE2 + BlockShowcase2SC.ENERGY_STORAGE, BlockShowcaseSC.CHARGE_PAD, BlockShowcaseSC.CHARGE_PAD};
            case SALVAGER:
                return new int[]{SHOWCASE2 + BlockShowcase2SC.SCRAP_PILE, SHOWCASE2 + BlockShowcase2SC.RACK, SHOWCASE2 + BlockShowcase2SC.RACK,
                        SHOWCASE2 + BlockShowcase2SC.CRATE, SHOWCASE2 + BlockShowcase2SC.CRATE};
            default:
                return new int[0];
        }
    }

    /**
     * The first block's showcases of a profession's workshop (BlockShowcaseSC metas, in workshopLine's order): the whole
     * line for Ф2's professions, the charge pads of the Power Engineer's, none of the Salvager's (the rest is BlockShowcase2SC).
     */
    public static int[] workshopMachines(NpcKindSC kind) {
        int[] line = workshopLine(kind);
        int n = 0;
        for (int c : line) {
            n += c < SHOWCASE2 ? 1 : 0;
        }
        int[] out = new int[n];
        n = 0;
        for (int c : line) {
            if (c < SHOWCASE2) {
                out[n++] = c;
            }
        }
        return out;
    }

    /** Where a profession's Tuners stand in their w x w workshop, in its frame: {u, v}, the first first (more Tuners: round again). */
    public static int[][] workshopPosts(NpcKindSC kind, int w) {
        List<int[]> l = new ArrayList<int[]>();
        if (kind == NpcKindSC.CARTOGRAPHER) {                  // round the focuser in the middle
            int c = w / 2;
            l.add(new int[]{c, c - 1});
            l.add(new int[]{c - 1, c});
            l.add(new int[]{c + 1, c});
            l.add(new int[]{c, c + 1});
            l.add(new int[]{c - 1, c - 1});
            return l.toArray(new int[l.size()][]);
        }
        int[][] spots = workshopSpots(w);
        int n = Math.min(spots.length, workshopLine(kind).length);
        for (int k = 1; k <= 2; k++) {                         // at each machine, then a step further from it
            for (int i = 0; i < n; i++) {
                l.add(new int[]{spots[i][0] + spots[i][2] * k, spots[i][1] + spots[i][3] * k});
            }
        }
        return l.toArray(new int[l.size()][]);
    }

    /** The homes of a living cell (HOUSE x HOUSE) in its frame: {u, v} - two, and a third when cells are short. */
    public static int[][] houseHomes() {
        return new int[][]{{1, 2}, {3, 2}, {2, 3}};
    }

    /**
     * A tower's spiral stair in its frame (TOWER x TOWER, the arch at (2, 0)): stair i stands i + 1 over the floor and
     * climbs towards stair i + 1; the last one, on the platform's level over the first, climbs onto the platform
     * (+u). The platform is open over the three before it (headroom).
     */
    public static int[][] towerSteps() {
        return new int[][]{{2, 1}, {3, 1}, {3, 2}, {3, 3}, {2, 3}, {1, 3}, {1, 2}, {1, 1}, {2, 1}};
    }

    /** Where one stands on a tower's platform, in its frame (TOWER_STAND over the floor). */
    public static int[] towerStand() {
        return new int[]{3, 3};
    }

    // ------------------------------------------------------------------ the plan

    /** The plan of a site (pure): every post inside the dome, the dock outside it. A few milliseconds - once per structure start. */
    public static Plan plan(long worldSeed, Site s) {
        Plan p = new Plan(s);
        Random rnd = new Random(s.seed ^ PLAN_SALT);
        int floor = floorY(worldSeed, s);
        p.floorY = floor;
        p.coreX = s.x;
        p.coreY = floor + 1;                                   // on its pedestal (a tungsten block set in the floor)
        p.coreZ = s.z;
        p.radius = s.radius;
        p.gateDir = s.first ? WEST : rnd.nextInt(4);           // «Первый причал» opens towards the world's spawn
        p.dock = s.size.dock;
        int r = s.radius;
        Frame f = new Frame(s.x, s.z, p.gateDir, r);
        List<NpcKindSC> roster = roster(s);
        List<NpcKindSC> trades = workshopKinds(roster);
        int tuners = 0;
        for (NpcKindSC k : roster) {
            if (!k.guard) {
                tuners++;
            }
        }
        // the reserved ground: the square, the street from it through the gate to the dome's edge
        f.reserved.add(new int[]{-SQUARE_HALF, -SQUARE_HALF, SQUARE_HALF, SQUARE_HALF});
        f.reserved.add(new int[]{SQUARE_HALF + 1, -2, r + 1, 2});
        // the gate: as far out on the street as fits under the shell
        int g = r;
        while (g > SQUARE_HALF + 3 && !f.fits(g, -GATE_W / 2, g + GATE_D - 1, GATE_W / 2, GATE_H)) {
            g--;
        }
        int[] gate = {g, -GATE_W / 2, g + GATE_D - 1, GATE_W / 2};
        f.taken.add(gate);
        // the workshops: one per profession (not the Armourer's), round the square (a Settlement's and a City's on its
        // corners, Ф3's two more at its sides)
        int ws = workshopSize(s.size), c = SQUARE_HALF + 2 + ws / 2;
        int[][] want;
        if (r >= SettlementSizeSC.SETTLEMENT.radiusMin) {
            want = new int[4 + RING_SIDES.length][];
            int[][] corners = {{-c, -c}, {-c, c}, {c, -c}, {c, c}};
            System.arraycopy(corners, 0, want, 0, 4);
            for (int i = 0; i < RING_SIDES.length; i++) {
                want[4 + i] = new int[]{RING_SIDES[i][0] * c, RING_SIDES[i][1] * c};
            }
        } else {
            want = new int[][]{{-c, 0}, {0, -c}, {0, c}, {c - 1, c + 1}};
        }
        int[][] workshop = new int[trades.size()][];
        for (int i = 0; i < trades.size(); i++) {
            workshop[i] = f.place(want[i % want.length][0], want[i % want.length][1], ws, ws, workshopHeight(trades.get(i)), false);
        }
        // the towers, flanking the gate on the inside
        int[][] tower = new int[2][];
        for (int i = 0; i < 2; i++) {
            tower[i] = f.place(g + 1, (i == 0 ? -1 : 1) * (GATE_W / 2 + 4), TOWER, TOWER, TOWER_H, false);
        }
        // the living cells, nearest the middle first
        int cells = (tuners + HOMES - 1) / HOMES;
        List<int[]> house = new ArrayList<int[]>();
        for (int i = 0; i < cells; i++) {
            int[] h = f.place(0, 0, HOUSE, HOUSE, HOUSE_H, true);
            if (h == null) {
                break;
            }
            house.add(h);
        }
        int homesEach = house.size() * HOMES >= tuners ? HOMES : HOMES_MAX;

        // ---- the lots, in build order
        int streetEnd = p.dock ? r + DOCK_OUT - DOCK_HALF - 1 : r + 2;
        p.lots.add(f.lot(LOT_HEART, new int[]{-SQUARE_HALF, -SQUARE_HALF, SQUARE_HALF, SQUARE_HALF}, 0, 0, HEART_H));
        p.lots.add(f.lot(LOT_STREET, new int[]{SQUARE_HALF + 1, -1, streetEnd, 1}, 0, 0, 1));
        p.lots.add(f.lot(LOT_GATE, gate, 0, 0, GATE_H));
        Lot[] wLots = new Lot[trades.size()];
        for (int i = 0; i < trades.size(); i++) {
            if (workshop[i] != null) {
                wLots[i] = f.lot(LOT_WORKSHOP, workshop[i], f.facingToCentre(workshop[i]), trades.get(i).ordinal(), workshopHeight(trades.get(i)));
                p.lots.add(wLots[i]);
            }
        }
        Lot[] tLots = new Lot[2];
        for (int i = 0; i < 2; i++) {
            if (tower[i] != null) {
                tLots[i] = f.lot(LOT_TOWER, tower[i], f.facingToCentre(tower[i]), i, TOWER_H);
                p.lots.add(tLots[i]);
            }
        }
        Lot[] hLots = new Lot[house.size()];
        for (int i = 0; i < house.size(); i++) {
            hLots[i] = f.lot(LOT_HOUSE, house.get(i), f.facingToCentre(house.get(i)), rnd.nextInt(HOUSE_VARIANTS), HOUSE_H);
            p.lots.add(hLots[i]);
        }
        if (p.dock) {
            p.lots.add(f.lot(LOT_DOCK, new int[]{r + DOCK_OUT - DOCK_HALF, -DOCK_HALF, r + DOCK_OUT + DOCK_HALF, DOCK_HALF}, 0, 0, 1));
            p.dockX = f.x(r + DOCK_OUT, 0);
            p.dockY = floor;
            p.dockZ = f.z(r + DOCK_OUT, 0);
        }

        // ---- the points
        int stand = floor + 1;
        f.point(p, SettlementRegistrySC.P_PLAZA, AT_PLAZA[0], AT_PLAZA[1], stand);
        f.point(p, SettlementRegistrySC.P_SQUARE, AT_SQUARE[0], AT_SQUARE[1], stand);
        f.point(p, SettlementRegistrySC.P_CLONE, AT_CLONE[0], AT_CLONE[1], stand);
        int pads = PADS[Math.min(PADS.length - 1, s.size.ordinal())];
        for (int i = 0; i < pads; i++) {
            f.point(p, SettlementRegistrySC.P_PAD + i, PAD_AT[i][0], PAD_AT[i][1], stand);
        }
        f.point(p, SettlementRegistrySC.P_GATE, g - 1, 2, stand);
        int[] ts = towerStand();
        for (int i = 0; i < 2; i++) {
            if (tLots[i] != null) {
                p.point(SettlementRegistrySC.P_TOWER + i, tLots[i].wx(ts[0], ts[1]), floor + TOWER_STAND, tLots[i].wz(ts[0], ts[1]));
            } else {                                           // no room for the tower: by the gate
                int[] q = f.freeSpot(g - 2, (i == 0 ? -1 : 1) * 3);
                f.point(p, SettlementRegistrySC.P_TOWER + i, q[0], q[1], stand);
            }
        }

        // ---- the posts
        int[] used = new int[trades.size()];
        int home = 0, sentries = 0, seniors = 0, armourers = 0, byHeart = 0;
        for (NpcKindSC k : roster) {
            int[] at;
            int[] sleep = null;
            if (k == NpcKindSC.CAPTAIN) {                      // ПГ-11: the guard square, before the armour station
                at = p.pointOf(SettlementRegistrySC.P_SQUARE);
            } else if (k == NpcKindSC.COMMANDANT) {            // ПГ-12: the plaza, he never leaves it
                at = p.pointOf(SettlementRegistrySC.P_PLAZA);
            } else if (k == NpcKindSC.SENTRY) {
                if (sentries == 0) {
                    at = p.pointOf(SettlementRegistrySC.P_GATE);
                } else if (sentries == 1) {
                    at = p.pointOf(SettlementRegistrySC.P_SQUARE);
                } else {                                       // the rest walk the dome's edge
                    int[][] edge = {{0, r - 4}, {-(r - 4), 0}, {0, -(r - 4)}};
                    int[] q = f.freeSpot(edge[(sentries - 2) % edge.length][0], edge[(sentries - 2) % edge.length][1]);
                    at = new int[]{f.x(q[0], q[1]), stand, f.z(q[0], q[1])};
                }
                if (sentries < NIGHT_AT.length) {              // two sleep by the Heart
                    int[] n = NIGHT_AT[sentries];
                    sleep = new int[]{f.x(n[0], n[1]), stand, f.z(n[0], n[1])};
                }
                sentries++;
            } else if (k == NpcKindSC.SENIOR_WARDEN) {
                if (seniors < 2) {
                    at = p.pointOf(SettlementRegistrySC.P_TOWER + seniors);
                } else {                                       // the third: the gate's other side
                    int[] q = f.freeSpot(g - 1, -2);
                    at = new int[]{f.x(q[0], q[1]), stand, f.z(q[0], q[1])};
                }
                seniors++;
            } else {
                if (k == NpcKindSC.ELDER) {
                    at = new int[]{f.x(AT_ELDER[0], AT_ELDER[1]), stand, f.z(AT_ELDER[0], AT_ELDER[1])};
                } else if (k == NpcKindSC.ARMOURER) {          // ПЖ-8: no workshop - at the guard square's armour station
                    int[] q = AT_ARMOURER[armourers++ % AT_ARMOURER.length];
                    at = new int[]{f.x(q[0], q[1]), stand, f.z(q[0], q[1])};
                } else {
                    int w = trades.indexOf(k);
                    Lot l = wLots[w];
                    if (l != null) {
                        int[][] posts = workshopPosts(k, ws);
                        int[] q = posts[used[w]++ % posts.length];
                        at = new int[]{l.wx(q[0], q[1]), stand, l.wz(q[0], q[1])};
                    } else {                                   // no room for its workshop: by the square
                        int[] q = f.freeSpot(3, -5 + 2 * byHeart++);
                        at = new int[]{f.x(q[0], q[1]), stand, f.z(q[0], q[1])};
                    }
                }
                int cell = home / homesEach;
                if (cell < hLots.length) {
                    int[] hh = houseHomes()[home % homesEach];
                    sleep = new int[]{hLots[cell].wx(hh[0], hh[1]), stand, hLots[cell].wz(hh[0], hh[1])};
                } else {                                       // no cell for it: by the Heart
                    int[] q = f.freeSpot(-4, home % 2 == 0 ? -5 : 5);
                    sleep = new int[]{f.x(q[0], q[1]), stand, f.z(q[0], q[1])};
                }
                home++;
            }
            if (sleep == null) {
                sleep = at;
            }
            p.posts.add(new Post(k, at[0], at[1], at[2], sleep[0], sleep[1], sleep[2]));
        }
        names(p);
        p.footprint = f.footprint(p.dock);
        return p;
    }

    /** The settlement's name and its creatures' (NpcNamesSC), from its seed, none twice. */
    private static void names(Plan p) {
        Random r = new Random(p.site.seed ^ NAME_SALT);
        Set<Integer> taken = new HashSet<Integer>();
        int own = NpcNamesSC.random(r);
        taken.add(Integer.valueOf(own));
        p.nameElement = NpcNamesSC.element(own);
        p.nameNumber = NpcNamesSC.number(own);
        for (Post post : p.posts) {
            int code;
            do {
                code = NpcNamesSC.random(r);
            } while (!taken.add(Integer.valueOf(code)));
            post.name = code;
        }
    }

    /** {minCX, minCZ, maxCX, maxCZ}: the chunks of the plan's footprint (ПП-2: none may exist on disk yet). */
    public static int[] footprintChunks(Plan p) {
        return new int[]{p.footprint[0] >> 4, p.footprint[1] >> 4, p.footprint[2] >> 4, p.footprint[3] >> 4};
    }

    // ------------------------------------------------------------------ the ground's shape (shared with ComponentSetGroundSC)

    /**
     * How far past the level ground the column at (a, p) of the settlement's frame lies: 0 or less on it (the dome's
     * disc, the pad from the gate out past the dock), up to RAMP on the ramp round it.
     */
    public static double levelDistance(int a, int p, int radius, boolean dock) {
        double disc = Math.sqrt((double) a * a + (double) p * p) - radius;
        int padEnd = dock ? radius + DOCK_OUT + DOCK_HALF + 1 : radius + 3, padHalf = dock ? DOCK_HALF + 1 : 2;
        int pad = Math.max(Math.max(radius - 1 - a, a - padEnd), Math.max(-padHalf - p, p - padHalf));
        return Math.min(disc, pad);
    }

    // ------------------------------------------------------------------ frames

    /** The world X of (u, v) in the frame of a box [x0..x1] whose entrance faces `facing` (u across, v inwards). */
    public static int frameX(int x0, int x1, int facing, int u, int v) {
        switch (facing) {
            case EAST:
                return x1 - v;
            case SOUTH:
                return x1 - u;
            case WEST:
                return x0 + v;
            default:
                return x0 + u;
        }
    }

    /** The world Z of (u, v) in the frame of a box [z0..z1] whose entrance faces `facing`. */
    public static int frameZ(int z0, int z1, int facing, int u, int v) {
        switch (facing) {
            case EAST:
                return z0 + u;
            case SOUTH:
                return z1 - v;
            case WEST:
                return z1 - u;
            default:
                return z0 + v;
        }
    }

    /** The world direction (NORTH..WEST) of a step (du, dv) in a frame facing `facing`; -1 for none. */
    public static int frameDir(int facing, int du, int dv) {
        switch (facing) {
            case EAST:
                return dirOf(-dv, du);
            case SOUTH:
                return dirOf(-du, -dv);
            case WEST:
                return dirOf(dv, -du);
            default:
                return dirOf(du, dv);
        }
    }

    /** NORTH..WEST of a straight step (dx, dz), -1 none. */
    public static int dirOf(int dx, int dz) {
        if (dx != 0 && dz != 0 || dx == 0 && dz == 0) {
            return -1;
        }
        return dz < 0 ? NORTH : dx > 0 ? EAST : dz > 0 ? SOUTH : WEST;
    }

    /**
     * The settlement's own frame while it is planned: `a` along the street towards the gate, `p` across it (p's unit is
     * a's turned clockwise, seen from above), the core at (0, 0). Rectangles are {a0, p0, a1, p1}, inclusive.
     */
    private static final class Frame {
        final int cx, cz, dir, ax, az, px, pz, r;
        /** Ground nothing stands on (the square, the street) and the lots placed so far. */
        final List<int[]> reserved = new ArrayList<int[]>(), taken = new ArrayList<int[]>();

        Frame(int cx, int cz, int dir, int r) {
            this.cx = cx;
            this.cz = cz;
            this.dir = dir;
            this.r = r;
            ax = DIR_X[dir];
            az = DIR_Z[dir];
            px = -az;
            pz = ax;
        }

        int x(int a, int p) {
            return cx + a * ax + p * px;
        }

        int z(int a, int p) {
            return cz + a * az + p * pz;
        }

        void point(Plan plan, String name, int a, int p, int y) {
            plan.point(name, x(a, p), y, z(a, p));
        }

        /** The rectangle stands under the shell: its far corner a block and a half in from the dome's foot, its top a block under the shell. */
        boolean fits(int a0, int p0, int a1, int p1, int height) {
            double da = Math.max(Math.abs(a0), Math.abs(a1)) + 0.5D, dp = Math.max(Math.abs(p0), Math.abs(p1)) + 0.5D;
            double d2 = da * da + dp * dp, edge = r - 1.5D, shell = r - 1D;
            return d2 <= edge * edge && d2 + (double) height * height <= shell * shell;
        }

        boolean free(int[] q) {
            for (int[] t : reserved) {
                if (overlaps(q, t, 1)) {
                    return false;
                }
            }
            for (int[] t : taken) {
                if (overlaps(q, t, 1)) {
                    return false;
                }
            }
            return true;
        }

        static boolean overlaps(int[] q, int[] t, int gap) {
            return q[0] <= t[2] + gap && t[0] <= q[2] + gap && q[1] <= t[3] + gap && t[1] <= q[3] + gap;
        }

        /**
         * A w (along a) x d (along p) lot as near (wa, wp) as it can stand: free, under the shell; null if nowhere.
         * `outer`: a living cell - never inside the workshops' ring (the square and its lane).
         */
        int[] place(int wa, int wp, int w, int d, int height, boolean outer) {
            for (int[] o : offsets()) {
                int ca = wa + o[0], cp = wp + o[1];
                if (Math.abs(ca) > r || Math.abs(cp) > r || outer && Math.max(Math.abs(ca), Math.abs(cp)) <= SQUARE_HALF + 2) {
                    continue;
                }
                int[] q = {ca - w / 2, cp - d / 2, ca - w / 2 + w - 1, cp - d / 2 + d - 1};
                if (fits(q[0], q[1], q[2], q[3], height) && free(q)) {
                    taken.add(q);
                    return q;
                }
            }
            return null;
        }

        /** The entrance of a lot faces the core along its longer offset (a corner lot: the square if behind it, the street if in front). */
        int facingToCentre(int[] q) {
            int ca = q[0] + q[2], cp = q[1] + q[3];                // twice the middle: the sign and the order are all it needs
            int da = 0, dp = 0;
            if (Math.abs(ca) > Math.abs(cp) || Math.abs(ca) == Math.abs(cp) && ca < 0) {
                da = ca > 0 ? -1 : 1;
            } else {
                dp = cp > 0 ? -1 : 1;
            }
            return dirOf(da * ax + dp * px, da * az + dp * pz);
        }

        /** A lot in world coordinates; the Heart, the street, the gate and the dock face the gate's way, the rest `facing`. */
        Lot lot(int kind, int[] q, int facing, int variant, int height) {
            int xa = x(q[0], q[1]), xb = x(q[2], q[3]), za = z(q[0], q[1]), zb = z(q[2], q[3]);
            int face = kind == LOT_HEART || kind == LOT_STREET || kind == LOT_GATE || kind == LOT_DOCK ? dir : facing;
            return new Lot(kind, Math.min(xa, xb), Math.min(za, zb), Math.max(xa, xb), Math.max(za, zb), face, variant, height);
        }

        /** A walkable cell near (a, p): under the dome (2 in from its foot), not in a lot, the cap or a fixture of the square. */
        int[] freeSpot(int a, int p) {
            int lim = (r - 2) * (r - 2);
            for (int[] o : offsets()) {
                int qa = a + o[0], qp = p + o[1];
                if (qa * qa + qp * qp > lim || Math.abs(qa) <= CAP_HALF && Math.abs(qp) <= CAP_HALF) {
                    continue;
                }
                boolean ok = true;
                int[] cell = {qa, qp, qa, qp};
                for (int i = 0; ok && i < taken.size(); i++) {
                    ok = !overlaps(cell, taken.get(i), 0);
                }
                for (int i = 0; ok && i < SQUARE_BLOCKED.length; i++) {
                    ok = SQUARE_BLOCKED[i][0] != qa || SQUARE_BLOCKED[i][1] != qp;
                }
                if (ok) {
                    return new int[]{qa, qp};
                }
            }
            return new int[]{a, p};
        }

        /** {minX, minZ, maxX, maxZ}: the dome's disc and the pad out past the dock, each with its ramp. */
        int[] footprint(boolean dock) {
            int e = r + RAMP;
            int padEnd = (dock ? r + DOCK_OUT + DOCK_HALF + 1 : r + 3) + RAMP, padHalf = (dock ? DOCK_HALF + 1 : 2) + RAMP;
            int[][] q = {{-e, -e, e, e}, {r - 1 - RAMP, -padHalf, padEnd, padHalf}};
            int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (int[] t : q) {
                int xa = x(t[0], t[1]), xb = x(t[2], t[3]), za = z(t[0], t[1]), zb = z(t[2], t[3]);
                minX = Math.min(minX, Math.min(xa, xb));
                maxX = Math.max(maxX, Math.max(xa, xb));
                minZ = Math.min(minZ, Math.min(za, zb));
                maxZ = Math.max(maxZ, Math.max(za, zb));
            }
            return new int[]{minX, minZ, maxX, maxZ};
        }
    }

    /** Offsets within REACH_MAX, nearest first (ties by angle) - the lots' search order. Built once. */
    private static int[][] offsets;

    private static synchronized int[][] offsets() {
        if (offsets == null) {
            int n = REACH_MAX;
            List<int[]> l = new ArrayList<int[]>();
            for (int a = -n; a <= n; a++) {
                for (int p = -n; p <= n; p++) {
                    if (a * a + p * p <= n * n) {
                        l.add(new int[]{a, p, a * a + p * p, (int) Math.round(StrictMath.atan2(p, a) * 1000D)});   // the same on every JVM
                    }
                }
            }
            Collections.sort(l, new Comparator<int[]>() {
                @Override
                public int compare(int[] x, int[] y) {
                    return x[2] != y[2] ? (x[2] < y[2] ? -1 : 1) : (x[3] < y[3] ? -1 : x[3] > y[3] ? 1 : 0);
                }
            });
            offsets = l.toArray(new int[l.size()][]);
        }
        return offsets;
    }

    // ------------------------------------------------------------------ hashing (SubstrateTerrainSC's own is package-private)

    /** A well-mixed hash of the seed and two numbers (SplitMix64's finaliser). */
    static long mix(long seed, long a, long b) {
        long h = seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h;
    }

    /** 0..n-1 from a hash. */
    static int pick(long h, int n) {
        return (int) ((h >>> 1) % n);
    }
}
