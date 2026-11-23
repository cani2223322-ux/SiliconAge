package com.sc.embassy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.sc.block.BlockAirfieldSC;
import com.sc.dim.SubstrateConfigSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.settle.gen.SettlementLayoutSC;

import cpw.mods.fml.common.Loader;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/**
 * «Лётное поле посольства» (docs/plan-embassy-airfield-core.md §3, docs/idea-embassy-airfield.md §3-§4, rules ЛП-1..ЛП-9): an
 * embassy's airfield as pure geometry - written whole by the coordinator (as EmbassyLayoutSC in Ф6): the generator (package G - the
 * shape's levelling, the decision, the «Достройка»'s windows), the pieces (package P), the protection and the services (package R),
 * the public API for the Vehicles add-on (com.sc.embassy.api) and the tests (package D) all ask the same numbers here.
 * <p>
 * The frame is the embassy's (EmbassyLayoutSC.Plan: `a` along the main street towards the main gate, `p` across it), with `q = p x
 * side` - towards the field (side +1 or -1 by the site's seed, sideOf; the variant's decision may take the other one). r is the dome's
 * radius (88..96). A FULL field (the user's choice of 2026-10-26: 200 x 9 beside the dome, inside today's square - REACH_MAX, the
 * structure's RANGE, explored() and the site rules unchanged):
 * <ul>
 * <li>the runway (paved 9: RWY_W_HALF either side) on q = r + AXIS (23) from a = -RWY_HALF to +RWY_HALF (200), its shoulders SHOULDER (2) each side -
 * the strip; the thresholds displaced THRESHOLD_SHIFT (20) in from each end (the landing distance 160..180: TODO(balance) - the add-on's
 * LinerSimSVA measures it); the turning pads PAD_LEN x PAD_W (21 x 12) at both ends on the dome's side; taxiway «А» (TAXI_W: 7) from the
 * +a pad to the apron - no parallel taxiway (a backtrack, the tower keeps one machine on the runway);</li>
 * <li>the apron a = r + 3 .. r + 31, q = 24 .. r + 9 (its edge 14 from the runway's axis: a parked liner's wing never over the
 * runway); «МС-1» 22 x 22 for the liner by taxiway «А», «МС-2..4» 13 x 13 along a lane; a «Колонка обслуживания» by each; the
 * monument «Последний челнок Литографов» in the lane's dead end;</li>
 * <li>the «Аэровокзал» 17 x 9 (7 high) right by the Vokzal's platform outside its dock zone (13), the «Вышка КДП» 7 x 7 (20 high, the
 * «Светомаяк» on top) at its end, the «Ангар» 27 x 21 (5 high: it stays under the 1:4 transitional surface - the proposal's 11 did
 * not; vehicles are at most 2.7 high) between the dome and the apron, its gate to the apron;</li>
 * <li>the helipad «Н» 11 x 11 on the other side of the Vokzal (every variant - the proposal's second one; one only), its column;</li>
 * <li>five «Зенитный пост Наладчиков» emplacements (AA_POSTS) round the dome and the field - the add-on's air defence fires from them;</li>
 * <li>the windsock's mast; the fence (the pieces': the field's outer edge and the apron's, the ends open);</li>
 * <li>the approach funnels (FULL): growth cleared over the 1:20 plane from each threshold in a corridor of FUNNEL_HALF (15) out to
 * a = +-FUNNEL_A (150) - the only part past the square (its start's box grows); an end with an ILS of a decision of version
 * LONG_FUNNELS on (ЛП-2 of the audit 2026-11-04) out to FUNNEL_ILS_OUT (300) past its threshold (a = +-380: MapGenEmbassySC.RANGE
 * covers it); built in later chunks only FUNNEL_DAYS after the record was made (ЛП-6).</li>
 * </ul>
 * SHORT (no FULL fits): a runway 90 x 7 on q = r + 18 from a = -45 to +45 inside today's level disc (corner 125.8 from the core at
 * r = 96), the helipad, the column, the emplacements, the windsock. HELI: the helipad, its column, the emplacements. Every element
 * stands outside the dome + 3 and on the level ground, every building under the 1:4 transitional surface from the strip and the
 * 1:20 approach surface - problem() checks it all (the self-test's).
 * <p>
 * The decision (MapGenEmbassySC.getStructureStart, the moment the start is decided - decideFor): embassy.airfield AUTO (the add-on
 * ADDON loaded), ALWAYS or NEVER; building - EmbassyAirSiteSC.choose (package G: the biome layers, no chunk loaded) says FULL on one
 * side, SHORT, HELI or NONE and which ends may have an ILS. The decision lives in the record's extra EXTRA "X" {k: kind, s: side,
 * v: VERSION, i: the ILS ends} (written by EmbassyLayoutSC.fill; a record without "X" - every embassy made before, or without the
 * add-on - has no field, never: old embassies are not touched) and in the ground piece's NBT "Air" (a lost record is made again with
 * it: RosterSC). Server and client alike for the pure part; fieldOf caches a record's field.
 */
public final class EmbassyAirfieldSC {

    /** The field's kinds (Settlement.extra "X".k - append only). */
    public static final int NONE = 0, HELI = 1, SHORT = 2, FULL = 3;
    public static final String[] KIND_KEYS = {"none", "heli", "short", "full"};
    /** The Vehicles add-on's mod id (Loader.isModLoaded - the core never names its classes). */
    public static final String ADDON = "SiliconAgeVehicles";
    /** The record's key (Settlement.extra) and its fields; the decision's version. */
    public static final String EXTRA = "X", K_KIND = "k", K_SIDE = "s", K_VERSION = "v", K_ILS = "i";
    /**
     * The decision's version a NEW record gets: 1 - the first fields (funnels to +-FUNNEL_A at both ends); 2 (LONG_FUNNELS, ЛП-2 of the
     * audit 2026-11-04) - an ILS end's funnel reaches FUNNEL_ILS_OUT past its threshold. A record keeps its own version for ever (an old
     * world's field, its pieces' boxes and its settled windows stay exactly as they were built).
     */
    public static final int VERSION = 2, LONG_FUNNELS = 2;
    /** The ILS ends (K_ILS's bits): the approach over the -a end (landing towards +a), over the +a end. */
    public static final int ILS_LOW = 1, ILS_HIGH = 2;

    // ------------------------------------------------------------------ FULL (frame numbers; r - the dome's radius)

    /** The runway: a = -RWY_HALF..+RWY_HALF (200), paved RWY_W_HALF either side of its axis (9), the axis r + AXIS; shoulders SHOULDER. */
    public static final int RWY_HALF = 100, RWY_W_HALF = 4, AXIS = 23, SHOULDER = 2;
    /** The thresholds this far in from the runway's ends (landing distance 2 x (RWY_HALF - THRESHOLD_SHIFT)). TODO(balance): LinerSimSVA */
    public static final int THRESHOLD_SHIFT = 20;
    /** The turning pads: PAD_LEN along a from the end, PAD_W across from q = r + PAD_IN (the dome's side). */
    public static final int PAD_LEN = 21, PAD_W = 12, PAD_IN = 7;
    /** Taxiway «А»: q = r + TAXI_IN .. + TAXI_W - 1, from a = RWY_HALF - TAXI_BACK to the apron's far edge. */
    public static final int TAXI_IN = 10, TAXI_W = 7, TAXI_BACK = 6;
    /** The apron: a = r + APRON_A0 .. r + APRON_A1, q = APRON_Q0 .. r + APRON_Q1 (its edge 14 from the axis). */
    public static final int APRON_A0 = 3, APRON_A1 = 31, APRON_Q0 = 24, APRON_Q1 = 9;
    /** A parked liner's wing: the apron keeps this far from the runway's axis (the liner's half span 8.8 + the runway's half 4.5 + a block). */
    public static final int WING_CLEAR = 14;
    /** Heights over the floor (the top block): the terminal, the tower, the beacon (on the tower's roof), the hangar, the monument, an emplacement, the windsock, a column. */
    public static final int H_TERMINAL = 7, H_TOWER = 20, BEACON_DY = 21, H_HANGAR = 5, H_MONUMENT = 6, H_AD = 2, H_WINDSOCK = 3, H_COLUMN = 1;
    /**
     * Fixed blocks the API names (the pieces put exactly these - package P; the add-on keys its virtual records by them): the tower's desk
     * (BlockShowcase2SC.TOWER_CONSOLE) at the tower's middle TOWER_DESK_DY over the floor (the cab's floor one under it); a column, an
     * emplacement's gun (AA_POST) and the windsock's mast foot one over the floor (on the paving / the platform).
     */
    public static final int TOWER_DESK_DY = 17, COLUMN_DY = 1, AA_GUN_DY = 1, WINDSOCK_DY = 1;
    /** The lights (BlockAirfieldSC LIGHT_*, flush in the paving at the floor): the edge lights and the taxiway's every LIGHT_STEP. */
    public static final int LIGHT_STEP = 6;
    /** The add-on's virtual ILS (no block): the localizer this far past the far end on the axis, the glide slope GS_OFF off the axis on the dome's side GS_IN past the threshold. TODO(balance) */
    public static final int LOC_OUT = 15, GS_OFF = 15, GS_IN = 30;
    /** The fence's height (frangible: no surface counts it). */
    public static final int H_FENCE = 1;
    /** The approach funnels: growth cleared over the 1:FUNNEL_SLOPE plane from each threshold, FUNNEL_HALF either side of the axis, out to a = +-FUNNEL_A. */
    public static final int FUNNEL_A = 150, FUNNEL_HALF = 15, FUNNEL_SLOPE = 20;
    /**
     * ЛП-2 (the audit 2026-11-04, the user's choice А; decisions of version LONG_FUNNELS on): an end WITH an ILS clears its growth over the
     * 1:FUNNEL_SLOPE plane FUNNEL_ILS_OUT past its threshold (the approach's check reaches as far - EmbassyAirSiteSC.APPROACH_OUT; a hill
     * over the plane there takes the ILS away instead: EmbassyAirSiteSC.pierces); an end without one keeps FUNNEL_A. FUNNEL_REACH - the
     * farthest a funnel reaches along a from the core (MapGenEmbassySC.covers, RANGE).
     */
    public static final int FUNNEL_ILS_OUT = 300, FUNNEL_REACH = RWY_HALF - THRESHOLD_SHIFT + FUNNEL_ILS_OUT;
    /**
     * ЛП-6 (the audit 2026-11-04, the user's choice А): a field's funnels are built in later chunks, their windows settled and the leaves
     * over them taken from a neighbour chunk only this many game days after its record was made (Settlement.madeAt); later - never (the
     * land may be the players' by then). TODO(balance)
     */
    public static final int FUNNEL_DAYS = 7;
    /** Buildings within APPROACH_HALF of the axis past a runway's end stay under the 1:FUNNEL_SLOPE plane from its threshold. */
    public static final int APPROACH_HALF = 12;
    /** The transitional surface: a building's height over the floor at most its distance from the strip / TRANSITION. */
    public static final int TRANSITION = 4;
    /** The neutral sky's approach corridors (the players' air defence holds fire): NEUTRAL_HALF either side of the axis, NEUTRAL_OUT past each threshold. */
    public static final int NEUTRAL_HALF = 20, NEUTRAL_OUT = 300;
    /** Everything but the funnels and the neutral corridors keeps this far out of the dome (the dome + 3). */
    public static final int DOME_CLEAR = 3;

    // ------------------------------------------------------------------ SHORT

    /** SHORT: a runway of 2 x SHORT_HALF (90) x (2 x SHORT_W_HALF + 1) (7) on q = r + SHORT_AXIS, no shoulders, no displaced threshold. */
    public static final int SHORT_HALF = 45, SHORT_W_HALF = 3, SHORT_AXIS = 18;

    // ------------------------------------------------------------------ every variant

    /** The helipad «Н» (11 x 11) on the side away from the field: a = r + HELI_A0 .. + 10, q = -(HELI_Q0 .. + 10). */
    public static final int HELI_A0 = 12, HELI_Q0 = 14, HELI_SIZE = 11;
    /** The protected zone keeps this margin round the helipad, an emplacement, the windsock, the SHORT runway. */
    public static final int ZONE_MARGIN = 2;
    /** The air defence emplacements («Зенитный пост Наладчиков»): how many, each a 5 x 5 platform (parapet) round its gun. */
    public static final int AA_POSTS = 5, AA_HALF = 2;
    /** The air defence circle: this far across from the core, any height (EmbassyAirRulesSC.AD_RADIUS - the add-on's; ЛП-1: the warning's - the fire is Field.inFireZone's). TODO(balance) */
    public static final int AD_RADIUS = EmbassyAirRulesSC.AD_RADIUS;

    /** The spots' kinds (AirfieldInfoSC): the liner's, a small machine's, the helipad. */
    public static final int SPOT_LINER = 0, SPOT_SMALL = 1, SPOT_HELI = 2;
    /** The spots' keys (lang sc.airfield.spot.<key>: «МС-1»..«МС-4», «Н»). */
    public static final String[] SPOT_KEYS = {"ms1", "ms2", "ms3", "ms4", "heli"};

    /**
     * ICAO (the user's choice 10): «X» + two letters of the element (its chemical symbol; a one-letter symbol takes a second letter of its
     * name: W - WO, V - VA, Y - YT) + the number's letter (1..26 - A..Z, on round again). By NpcNamesSC.ELEMENTS' order (append with it).
     */
    public static final String[] ICAO_ELEMENT = {"TA", "NB", "HF", "GE", "GA", "IN", "WO", "ZR", "TI", "CO", "NI", "PT", "PD", "IR", "OS",
            "RE", "MO", "VA", "YT", "LA", "CE", "ND", "SC", "BI", "SE", "TE", "SB", "BE", "LI", "SI"};
    /** The ICAO prefix of the embassies (closed for the players' new towers in the add-on). */
    public static final String ICAO_PREFIX = "X";

    private static final long SIDE_SALT = 0xA1F1E1DL;

    /** The record -> {its "X" compound, the record's numbers' stamp, the field (or null)}: fieldOf's cache (weak: a removed record goes). */
    private static final Map<SettlementRegistrySC.Settlement, Object[]> CACHE = new WeakHashMap<SettlementRegistrySC.Settlement, Object[]>();
    /** Bumped whenever an airfield record is made or renewed (EmbassyAirApiSC.revision - the add-on rebuilds its virtual airports). */
    private static volatile int revision = 1;

    private EmbassyAirfieldSC() {
    }

    // ================================================================== the decision

    /** embassy.airfield allows a field now: ALWAYS, or AUTO with the add-on loaded (never NEVER). Server. */
    public static boolean buildsNow() {
        int m = SubstrateConfigSC.embassyAirfield();
        return m == SubstrateConfigSC.AIRFIELD_ALWAYS || m == SubstrateConfigSC.AIRFIELD_AUTO && Loader.isModLoaded(ADDON);
    }

    /**
     * The decision for a NEW embassy at `site` ({kind, side, ils, VERSION}), or null - no field at all (the mode forbids it: no "X" is
     * written, the embassy is exactly as before). MapGenEmbassySC.getStructureStart asks it once, when it makes the record. Reads the
     * biome layers only (EmbassyAirSiteSC.choose, package G). Server.
     */
    public static int[] decideFor(World w, EmbassySiteSC.Site site) {
        if (w == null || site == null || !buildsNow()) {
            return null;
        }
        int[] c = EmbassyAirSiteSC.choose(w, site);
        if (c == null || c.length < 3 || c[0] < NONE || c[0] > FULL) {
            return new int[]{NONE, sideOf(site.seed), 0, VERSION};
        }
        return new int[]{c[0], c[1] < 0 ? -1 : 1, c[2] & (ILS_LOW | ILS_HIGH), VERSION};
    }

    /** The side the field tries first, by the site's seed: +1 or -1 (pure). */
    public static int sideOf(long siteSeed) {
        return EmbassySiteSC.pick(EmbassySiteSC.mix(siteSeed ^ SIDE_SALT, 0, 0), 2) == 0 ? 1 : -1;
    }

    // ================================================================== the record

    /** Record `s`'s decision {kind, side, ils, version}, or null (no "X": no field - an old embassy, or made without the add-on). */
    public static int[] decisionOf(SettlementRegistrySC.Settlement s) {
        if (s == null || !s.extra.hasKey(EXTRA, 10)) {
            return null;
        }
        NBTTagCompound x = s.extra.getCompoundTag(EXTRA);
        return new int[]{x.getInteger(K_KIND), x.getInteger(K_SIDE) < 0 ? -1 : 1, x.getInteger(K_ILS), Math.max(1, x.getInteger(K_VERSION))};
    }

    /** Writes decision `d` ({kind, side, ils, version}) into record `s` (null: nothing - the key stays as it is). EmbassyLayoutSC.fill. */
    public static void writeDecision(SettlementRegistrySC.Settlement s, int[] d) {
        if (s == null || d == null || d.length < 4) {
            return;
        }
        NBTTagCompound x = new NBTTagCompound();
        x.setInteger(K_KIND, d[0]);
        x.setInteger(K_SIDE, d[1] < 0 ? -1 : 1);
        x.setInteger(K_VERSION, d[3]);
        x.setInteger(K_ILS, d[2]);
        s.extra.setTag(EXTRA, x);
    }

    /** The decision kept in the built start whose core is the column (x, z) (its ground piece's NBT "Air"), or null. Server. */
    public static int[] decisionOfStart(World w, int x, int z) {
        MapGenEmbassySC m = EmbassySC.mapGen(w);
        StructureEmbassyStartSC st = m == null ? null : m.startAt(w, x >> 4, z >> 4);
        ComponentEmbGroundSC g = st == null || !st.isSizeableStructure() ? null : ComponentEmbGroundSC.of(st);
        return g == null || g.coreX() != x || g.coreZ() != z ? null : g.airDecision();
    }

    /**
     * Record `s`'s airfield (null: none - not an embassy, no "X", or NONE). Cached by the record (its "X" compound and its numbers: a
     * renewed record's new floor or radius gives a new one). Any thread that owns the record (the server's; the tests').
     */
    public static synchronized Field fieldOf(SettlementRegistrySC.Settlement s) {
        if (s == null || !SettlementSC.isEmbassy(s) || !s.extra.hasKey(EXTRA, 10)) {
            return null;
        }
        NBTTagCompound x = s.extra.getCompoundTag(EXTRA);
        long stamp = stamp(s);
        Object[] c = CACHE.get(s);
        if (c != null && c[0] == x && ((Long) c[1]).longValue() == stamp) {
            return (Field) c[2];
        }
        int[] d = decisionOf(s);
        Field f = Field.of(s.x, s.z, s.radius, s.floorY, EmbassyLayoutSC.gateDir(s.seed), d[0], d[1], d[2], d[3]);
        CACHE.put(s, new Object[]{x, Long.valueOf(stamp), f});
        return f;
    }

    private static long stamp(SettlementRegistrySC.Settlement s) {
        return EmbassySiteSC.mix(s.seed, ((long) s.x << 32) ^ (s.z & 0xFFFFFFFFL), ((long) s.radius << 16) ^ s.floorY);
    }

    /** {minX, minZ, maxX, maxZ} of record `s`'s protected airfield (Settlement.reach / overlapping), null - none. */
    public static int[] zoneBox(SettlementRegistrySC.Settlement s) {
        Field f = fieldOf(s);
        return f == null ? null : f.zoneBox();
    }

    /** The revision of the airfields (EmbassyAirApiSC.revision): changes whenever one is made or renewed. */
    public static int revision() {
        return revision;
    }

    /**
     * An airfield record was made or renewed in `w` (MapGenEmbassySC; a lost record made again with its field - RosterSC.restore): the
     * revision moves on and the add-on's hook hears it (EmbassyAirApiSC.fireChanged - its virtual airports rebuilt). Server.
     */
    public static void changed(World w) {
        revision++;
        com.sc.embassy.api.EmbassyAirApiSC.fireChanged(w, revision);
    }

    /** Server stopped: the cache goes (the revision keeps counting - a client of the add-on never sees an old number again). */
    public static synchronized void clearAll() {
        CACHE.clear();
        index = null;
        revision++;
    }

    // ================================================================== the airfields of a world (the API's hot questions)

    /** Index's slack past a field's reach (a renewed record's radius or floor moves its field a few blocks). */
    private static final int REACH_SLACK = 16;
    /** The last index made (one registry - the Overworld's - asks: the others make their own anew). */
    private static volatile Index index;

    /**
     * The records of one registry with an airfield - EmbassyAirApiSC.neutralAt / adZoneAt / airfields ask it for every contact of every
     * launcher and every machine near an embassy (the review's: no walk of every record, no list made a question): each record with its
     * reach (Field.reach + REACH_SLACK, across from the core) - a far one costs two compares. Made again when the revision moves or the
     * registry's count changes (index). Immutable; server thread.
     */
    public static final class Index {
        private final SettlementRegistrySC reg;
        private final int rev, count;
        private final SettlementRegistrySC.Settlement[] recs;
        private final int[] reach;

        private Index(SettlementRegistrySC reg, int rev, int count, List<SettlementRegistrySC.Settlement> recs, List<Integer> reach) {
            this.reg = reg;
            this.rev = rev;
            this.count = count;
            this.recs = recs.toArray(new SettlementRegistrySC.Settlement[recs.size()]);
            this.reach = new int[reach.size()];
            for (int i = 0; i < this.reach.length; i++) {
                this.reach[i] = reach.get(i).intValue();
            }
        }

        /** How many records with a field. */
        public int size() {
            return recs.length;
        }

        /** Record `i` (fieldOf gives its field - null if it was taken back since). */
        public SettlementRegistrySC.Settlement get(int i) {
            return recs[i];
        }

        /** Record `i` is of dimension `dim` and its field's reach holds the column (x, z): only then ask its field. */
        public boolean near(int i, int dim, double x, double z) {
            SettlementRegistrySC.Settlement s = recs[i];
            return s.dim == dim && Math.abs(x - (s.x + 0.5D)) <= reach[i] && Math.abs(z - (s.z + 0.5D)) <= reach[i];
        }
    }

    /** Registry `r`'s index of its airfield records (made again when the revision or r's count moved). Server thread. */
    public static Index index(SettlementRegistrySC r) {
        Index ix = index;
        int rev = revision, n = r == null ? 0 : r.count();
        if (ix != null && ix.reg == r && ix.rev == rev && ix.count == n) {
            return ix;
        }
        List<SettlementRegistrySC.Settlement> recs = new ArrayList<SettlementRegistrySC.Settlement>();
        List<Integer> reach = new ArrayList<Integer>();
        if (r != null) {
            for (SettlementRegistrySC.Settlement s : r.all()) {
                Field f = fieldOf(s);
                if (f != null) {
                    recs.add(s);
                    reach.add(Integer.valueOf(f.reach() + REACH_SLACK));
                }
            }
        }
        ix = new Index(r, rev, n, recs, reach);
        index = ix;
        return ix;
    }

    /**
     * A player's base holds the column (x, z) of `w`: a switched-on player's field zone (TileEntityFieldGeneratorSC.playerZoneNear with no
     * reach: its box round the master and its anchors, grown by its range - a settlement's core is no player's). The review's (an embassy
     * made later beside a base, a base in an approach corridor): the embassy's air defence never fires over it (EmbassyAirApiSC.adZoneAt,
     * holdFire) and its neutral sky never holds that base's own air defence there (neutralAt). Server; false on a client.
     */
    public static boolean playerBaseAt(World w, double x, double z) {
        return w != null && !w.isRemote && w.provider != null && com.sc.tileentity.TileEntityFieldGeneratorSC.playerZoneNear(w,
                w.provider.dimensionId, (int) Math.floor(x), (int) Math.floor(z), 0);
    }

    // ================================================================== names

    /** The ICAO the embassy proposes for itself (ICAO_ELEMENT): «Тантал-1» - XTAA; the add-on makes it unique (its TowerMath.unique). Pure. */
    public static String icao(int element, int number) {
        String e = element >= 0 && element < ICAO_ELEMENT.length ? ICAO_ELEMENT[element] : "ZZ";
        int n = Math.max(1, number);
        return ICAO_PREFIX + e + (char) ('A' + (n - 1) % 26);
    }

    /**
     * ЛП-5 (the audit 2026-11-04, the user's choice А): `proposed` made unique among `taken` the add-on's way (its
     * EmbassyAirportMath.uniqueIcao): free - itself; else the next free of its last two letters counted on (AA, AB .. ZZ, round again);
     * "" - none free, not four letters. Pure.
     */
    public static String uniqueIcao(String proposed, List<String> taken) {
        if (proposed == null || proposed.length() != 4) {
            return "";
        }
        String p = proposed.toUpperCase();
        if (free(p, taken)) {
            return p;
        }
        int start = (p.charAt(2) - 'A') * 26 + (p.charAt(3) - 'A');
        for (int i = 1; i < 26 * 26; i++) {
            int k = ((start + i) % (26 * 26) + 26 * 26) % (26 * 26);
            String c = p.substring(0, 2) + (char) ('A' + k / 26) + (char) ('A' + k % 26);
            if (free(c, taken)) {
                return c;
            }
        }
        return "";
    }

    private static boolean free(String c, List<String> taken) {
        for (String o : taken) {
            if (o != null && o.equalsIgnoreCase(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * ЛП-5: the ICAO code record `s` shows (the Dispatcher's tab, /scembassy airfield) - the add-on's own order: the FULL fields (those with
     * a tower) of the embassies' world by id first, each its proposed code made unique among the ones before (an embassy of a higher
     * number takes the next free letter); then the rest by id the same way. Computed, never saved; the players' own towers' codes are the
     * add-on's (a rare clash with one of them is still its to settle). A record without a field - its proposed code. Server.
     */
    public static String icaoShown(SettlementRegistrySC r, SettlementRegistrySC.Settlement s) {
        String own = s == null ? "" : icao(s.nameElement, s.nameNumber);
        Field mine = s == null ? null : fieldOf(s);
        if (r == null || mine == null) {
            return own;
        }
        List<SettlementRegistrySC.Settlement> recs = new ArrayList<SettlementRegistrySC.Settlement>();
        for (SettlementRegistrySC.Settlement e : r.embassies()) {
            if (e.dim == s.dim && fieldOf(e) != null) {
                recs.add(e);
            }
        }
        java.util.Collections.sort(recs, new java.util.Comparator<SettlementRegistrySC.Settlement>() {
            @Override
            public int compare(SettlementRegistrySC.Settlement a, SettlementRegistrySC.Settlement b) {
                return a.id < b.id ? -1 : a.id > b.id ? 1 : 0;
            }
        });
        List<String> taken = new ArrayList<String>();
        for (int pass = 0; pass < 2; pass++) {
            for (SettlementRegistrySC.Settlement e : recs) {
                if ((fieldOf(e).kind == FULL) != (pass == 0)) {
                    continue;
                }
                String c = uniqueIcao(icao(e.nameElement, e.nameNumber), taken);
                if (e == s) {
                    return c.length() == 4 ? c : own;
                }
                if (c.length() == 4) {
                    taken.add(c);
                }
            }
        }
        return own;
    }

    /**
     * ЛП-6: the funnels of embassy `settlementId` in `w` may still be built, settled and their overhanging leaves taken - within
     * FUNNEL_DAYS game days (24000 ticks each) of its record's making (Settlement.madeAt, world time). No registry
     * or no such record (a test world) - yes. Server.
     */
    public static boolean funnelsOpen(World w, int settlementId) {
        SettlementRegistrySC r = w == null || w.isRemote ? null : SettlementRegistrySC.get(w);
        SettlementRegistrySC.Settlement s = r == null ? null : r.byId(settlementId);
        return s == null || funnelsOpen(s.madeAt, w.getTotalWorldTime());
    }

    /**
     * ЛП-6: a field made at world time `madeAt` still builds its funnels at world time `now` (a new world's first embassy is made at
     * time 0; an old record's unknown 0 is long past in its world). Pure.
     */
    public static boolean funnelsOpen(long madeAt, long now) {
        return now >= madeAt && now - madeAt <= FUNNEL_DAYS * 24000L;
    }

    /** The true heading (degrees, 90..360) of a landing along +a (`plusA`) or -a in a frame whose main gate lies `gateDir` (NORTH 0 .. WEST 3). */
    public static int heading(int gateDir, boolean plusA) {
        int d = ((gateDir & 3) + (plusA ? 0 : 2)) & 3;     // NORTH 360, EAST 90, SOUTH 180, WEST 270
        return d == 0 ? 360 : d * 90;
    }

    /** The runway's number for a landing heading (degrees): its tens, two digits («36», «09», «18», «27»). Pure. */
    public static String number(int heading) {
        int n = Math.round(heading / 10F);
        n = n <= 0 ? 36 : n > 36 ? n - 36 : n;
        return n < 10 ? "0" + n : String.valueOf(n);
    }

    // ================================================================== the field

    /**
     * One embassy's airfield - immutable, pure (made by Field.of from the record's numbers and its decision). Rectangles are {a0, q0,
     * a1, q1} in the frame (q towards the field), inclusive; points {a, q}; null where the kind has none. x / z turn a frame cell into
     * the world's column, frame the other way.
     */
    public static final class Field {
        public final int kind, side, version, ils;
        public final int coreX, coreZ, radius, floorY, gateDir;
        /** The runway's axis (q), its ends (a: the -a end, the +a end) and thresholds (a: where the landing over that end touches down). */
        public final int axis, endLow, endHigh, thresholdLow, thresholdHigh;
        /** The paved runway; the strip (the runway and its shoulders - the transitional surface's start). */
        public final int[] runway, strip;
        /** FULL: the turning pads (the -a end's, the +a end's), taxiway «А», the apron, the terminal, the tower, the hangar, the monument. */
        public final int[] padLow, padHigh, taxi, apron, terminal, tower, hangar, monument;
        /** The helipad «Н» (every kind). */
        public final int[] helipad;
        /** The stands: FULL «МС-1..4» (rectangles) - the helipad is spots' last: SPOT_KEYS by index. */
        public final int[][] spots;
        /** The spots' kinds (SPOT_LINER, SPOT_SMALL, SPOT_HELI), by index of spots. */
        public final int[] spotKinds;
        /** The service columns {a, q}: one a spot, in spots' order (the helipad's last). */
        public final int[][] columns;
        /** The air defence emplacements' posts {a, q} (the gun block stands on floor + H_AD). */
        public final int[][] aaPosts;
        /** The windsock's mast {a, q} (null: HELI). */
        public final int[] windsock;
        /**
         * The posts on the field {a, q}: the 5th and 6th Sentries' (by the apron / the helipad; by the windsock - in its zone, WING_CLEAR and
         * more off the runway's axis: no wing over a guard), the second Gas Engineer's (by the columns) - FULL, SHORT; null - HELI.
         */
        public final int[][] posts;
        /** FULL: the approach funnels (growth cleared) - the -a end's, the +a end's; empty otherwise. */
        public final int[][] funnels;
        /** The neutral sky's approach corridors (the players' air defence holds fire over them, any height). */
        public final int[][] approaches;
        /** The level ground past the disc (FULL: the runway's rectangle, the apron's); empty otherwise. */
        public final int[][] level;
        /** The protected zone (no block broken or set, no blast, no mob spawn): the field's rectangles. */
        public final int[][] zone;

        private Field(int coreX, int coreZ, int radius, int floorY, int gateDir, int kind, int side, int ils, int version) {
            this.coreX = coreX;
            this.coreZ = coreZ;
            this.radius = radius;
            this.floorY = floorY;
            this.gateDir = gateDir & 3;
            this.kind = kind;
            this.side = side < 0 ? -1 : 1;
            this.ils = kind == FULL ? ils & (ILS_LOW | ILS_HIGH) : 0;
            this.version = version;
            int r = radius;
            List<int[]> zoneL = new ArrayList<int[]>(), levelL = new ArrayList<int[]>(), funnelL = new ArrayList<int[]>(), apprL = new ArrayList<int[]>();
            helipad = rect(r + HELI_A0, -(HELI_Q0 + HELI_SIZE - 1), r + HELI_A0 + HELI_SIZE - 1, -HELI_Q0);
            int[] heliColumn = {r + HELI_A0 + HELI_SIZE / 2, -(HELI_Q0 + HELI_SIZE)};
            aaPosts = new int[][]{{r / 2, -(r + 14)}, {-(r / 2), -(r + 14)}, {-(r + 14), -6}, {-(r / 4), r + 4}, {r / 4, r + 4}};
            zoneL.add(grow(helipad, ZONE_MARGIN));
            for (int[] a : aaPosts) {
                zoneL.add(rect(a[0] - AA_HALF - 1, a[1] - AA_HALF - 1, a[0] + AA_HALF + 1, a[1] + AA_HALF + 1));
            }
            if (kind == FULL) {
                axis = r + AXIS;
                endLow = -RWY_HALF;
                endHigh = RWY_HALF;
                thresholdLow = -RWY_HALF + THRESHOLD_SHIFT;
                thresholdHigh = RWY_HALF - THRESHOLD_SHIFT;
                runway = rect(-RWY_HALF, axis - RWY_W_HALF, RWY_HALF, axis + RWY_W_HALF);
                strip = rect(-RWY_HALF, axis - RWY_W_HALF - SHOULDER, RWY_HALF, axis + RWY_W_HALF + SHOULDER);
                padLow = rect(-RWY_HALF, r + PAD_IN, -RWY_HALF + PAD_LEN - 1, r + PAD_IN + PAD_W - 1);
                padHigh = rect(RWY_HALF - PAD_LEN + 1, r + PAD_IN, RWY_HALF, r + PAD_IN + PAD_W - 1);
                taxi = rect(RWY_HALF - TAXI_BACK, r + TAXI_IN, r + APRON_A1, r + TAXI_IN + TAXI_W - 1);
                apron = rect(r + APRON_A0, APRON_Q0, r + APRON_A1, r + APRON_Q1);
                terminal = rect(r + 6, 14, r + 22, 22);
                tower = rect(r + 24, 14, r + 30, 20);
                hangar = rect(r - 24, r - 23, r + 2, r - 3);
                monument = rect(r + 4, 24, r + 9, 29);
                spots = new int[][]{rect(r + 10, r - 12, r + 31, r + 9), rect(r + 19, r - 27, r + 31, r - 15), rect(r + 19, r - 42, r + 31, r - 30),
                        rect(r + 19, r - 57, r + 31, r - 45), helipad};
                spotKinds = new int[]{SPOT_LINER, SPOT_SMALL, SPOT_SMALL, SPOT_SMALL, SPOT_HELI};
                columns = new int[][]{{r + 31, r - 13}, {r + 31, r - 28}, {r + 31, r - 43}, {r + 31, r - 58}, heliColumn};
                windsock = new int[]{-(RWY_HALF - 30), r + 3};
                posts = new int[][]{{r + 3, 24}, {windsock[0], windsock[1] + 1}, {r + 30, r - 14}};     // the far one by the windsock
                int lv = r + EmbassyLayoutSC.LEVEL_OUT;
                levelL.add(rect(-lv, r + PAD_IN, lv, lv));                   // the runway's rectangle (its pads reach in to r + 7)
                levelL.add(rect(r - 32, 14, lv, r + 15));                    // the apron's (the hangar, the terminal, the tower in it)
                zoneL.addAll(levelL);
                // ЛП-2: an ILS end of a LONG_FUNNELS decision clears FUNNEL_ILS_OUT past its threshold, any other end out to FUNNEL_A
                boolean longs = version >= LONG_FUNNELS;
                int outLow = longs && (this.ils & ILS_LOW) != 0 ? thresholdLow - FUNNEL_ILS_OUT : -FUNNEL_A;
                int outHigh = longs && (this.ils & ILS_HIGH) != 0 ? thresholdHigh + FUNNEL_ILS_OUT : FUNNEL_A;
                funnelL.add(rect(outLow, axis - FUNNEL_HALF, endLow - 1, axis + FUNNEL_HALF));
                funnelL.add(rect(endHigh + 1, axis - FUNNEL_HALF, outHigh, axis + FUNNEL_HALF));
            } else if (kind == SHORT) {
                axis = r + SHORT_AXIS;
                endLow = -SHORT_HALF;
                endHigh = SHORT_HALF;
                thresholdLow = endLow;
                thresholdHigh = endHigh;
                runway = rect(-SHORT_HALF, axis - SHORT_W_HALF, SHORT_HALF, axis + SHORT_W_HALF);
                strip = runway;
                padLow = padHigh = taxi = apron = terminal = tower = hangar = monument = null;
                spots = new int[][]{helipad};
                spotKinds = new int[]{SPOT_HELI};
                columns = new int[][]{heliColumn};
                windsock = new int[]{-30, r + 3};
                posts = new int[][]{{r + HELI_A0 - 2, -(HELI_Q0 + HELI_SIZE / 2)}, {windsock[0], windsock[1] + 1},
                        {heliColumn[0] + 1, heliColumn[1] - 1}};        // the far one: by the windsock, in its zone, WING_CLEAR off the axis
                zoneL.add(grow(runway, ZONE_MARGIN));
            } else {
                axis = endLow = endHigh = thresholdLow = thresholdHigh = 0;
                runway = strip = padLow = padHigh = taxi = apron = terminal = tower = hangar = monument = null;
                spots = new int[][]{helipad};
                spotKinds = new int[]{SPOT_HELI};
                columns = new int[][]{heliColumn};
                windsock = null;
                posts = null;
            }
            if (windsock != null) {
                zoneL.add(rect(windsock[0] - 1, windsock[1] - 1, windsock[0] + 1, windsock[1] + 1));
            }
            if (runway != null) {
                apprL.add(rect(thresholdLow - NEUTRAL_OUT, axis - NEUTRAL_HALF, thresholdLow, axis + NEUTRAL_HALF));
                apprL.add(rect(thresholdHigh, axis - NEUTRAL_HALF, thresholdHigh + NEUTRAL_OUT, axis + NEUTRAL_HALF));
            }
            zone = zoneL.toArray(new int[zoneL.size()][]);
            level = levelL.toArray(new int[levelL.size()][]);
            funnels = funnelL.toArray(new int[funnelL.size()][]);
            approaches = apprL.toArray(new int[apprL.size()][]);
        }

        /** A field (null: NONE or a bad kind) of an embassy whose core is (coreX, coreZ), dome `radius`, floor `floorY`, main gate `gateDir`. Pure. */
        public static Field of(int coreX, int coreZ, int radius, int floorY, int gateDir, int kind, int side, int ils, int version) {
            return kind < HELI || kind > FULL ? null : new Field(coreX, coreZ, radius, floorY, gateDir, kind, side, ils, version);
        }

        /** The field of plan `p` with decision `d` ({kind, side, ils, version}; null or NONE - null). Pure. */
        public static Field of(EmbassyLayoutSC.Plan p, int[] d) {
            return d == null || d.length < 4 ? null : of(p.coreX, p.coreZ, p.radius, p.floorY, p.gateDir, d[0], d[1], d[2], d[3]);
        }

        /** The numbers the pieces keep in their NBT ("EmbAir": kind, side, ils, version - the frame is ComponentEmbFrameSC's). */
        public int[] airInts() {
            return new int[]{kind, side, ils, version};
        }

        // -------------------------------------------------------------- the frame

        /** The world X / Z of frame cell (a, q). */
        public int x(int a, int q) {
            return coreX + a * SettlementLayoutSC.DIR_X[gateDir] - q * side * SettlementLayoutSC.DIR_Z[gateDir];
        }

        public int z(int a, int q) {
            return coreZ + a * SettlementLayoutSC.DIR_Z[gateDir] + q * side * SettlementLayoutSC.DIR_X[gateDir];
        }

        /** World column (x, z)'s frame cell {a, q}. */
        public int[] frame(int x, int z) {
            int dx = x - coreX, dz = z - coreZ, ax = SettlementLayoutSC.DIR_X[gateDir], az = SettlementLayoutSC.DIR_Z[gateDir];
            return new int[]{dx * ax + dz * az, (-dx * az + dz * ax) * side};
        }

        /** Frame rectangle `q` as the world's {minX, minZ, maxX, maxZ}. */
        public int[] world(int[] q) {
            int xa = x(q[0], q[1]), xb = x(q[2], q[3]), za = z(q[0], q[1]), zb = z(q[2], q[3]);
            return new int[]{Math.min(xa, xb), Math.min(za, zb), Math.max(xa, xb), Math.max(za, zb)};
        }

        /** The world direction (SettlementLayoutSC.NORTH..WEST) of a frame step (da, dq). */
        public int dirOf(int da, int dq) {
            return EmbassyQuartersSC.frameDir(gateDir, da, dq * side);
        }

        // -------------------------------------------------------------- the shape and the zones (world columns)

        /**
         * ЛП-2: how far past the level ground the column (x, z) lies - the disc (radius + LEVEL_OUT) and the level rectangles (FULL), the
         * nearest of them (0 or less: on it); EmbassyLayoutSC.targetY eases BLEND back to the land's height from it. Pure.
         */
        public double levelPast(int x, int z) {
            int dx = x - coreX, dz = z - coreZ;
            double best = EmbassyLayoutSC.levelPast(dx, dz, radius + EmbassyLayoutSC.LEVEL_OUT);
            if (level.length > 0) {
                int[] f = frame(x, z);
                for (int[] q : level) {
                    best = Math.min(best, past(q, f[0], f[1]));
                }
            }
            return best;
        }

        /** The column is on the level ground (the disc or a level rectangle). */
        public boolean onShape(int x, int z) {
            return levelPast(x, z) <= 0D;
        }

        /** ЛП-6: the column is in the protected zone (any height from the floor - SettlementSC.FOUNDATION; SettlementSC.airfieldZoneAt). */
        public boolean inZone(int x, int z) {
            int[] f = frame(x, z);
            return inAny(zone, f[0], f[1]);
        }

        /**
         * ЛП-8: the column is under the neutral sky (any height): the level disc, the field's zone, the approach corridors (NEUTRAL_HALF of
         * the axis, NEUTRAL_OUT past each threshold) - the players' air defence holds fire there (the add-on's TileEntityLauncherSVA).
         */
        public boolean neutral(int x, int z) {
            long dx = x - coreX, dz = z - coreZ, lv = radius + EmbassyLayoutSC.LEVEL_OUT;
            if (dx * dx + dz * dz <= lv * lv) {
                return true;
            }
            int[] f = frame(x, z);
            return inAny(zone, f[0], f[1]) || inAny(approaches, f[0], f[1]);
        }

        /** ЛП-9: the column is in the embassy's air defence zone (AD_RADIUS across from the core, any height). */
        public boolean inAdZone(double x, double z) {
            double dx = x - (coreX + 0.5D), dz = z - (coreZ + 0.5D);
            return dx * dx + dz * dz <= (double) AD_RADIUS * AD_RADIUS;
        }

        /**
         * ЛП-1 (the audit 2026-11-04, the user's choice А): the half side of the square the guns fire over, across from the core
         * (Chebyshev: the dome, the level disc and its blend band - the embassy's own checked land - + AD_FIRE_MARGIN; about 145).
         */
        public int fireHalf() {
            return radius + EmbassyLayoutSC.LEVEL_OUT + EmbassyLayoutSC.BLEND + EmbassyAirRulesSC.AD_FIRE_MARGIN;
        }

        /**
         * ЛП-1: the embassy's guns may fire over the column (any height) - inside the AD circle (inAdZone) AND over the embassy's square
         * (fireHalf) or an approach corridor (approaches); the rest of the circle only warns (EmbassyAirApiSC.adWarnZoneAt). Pure.
         */
        public boolean inFireZone(double x, double z) {
            if (!inAdZone(x, z)) {
                return false;
            }
            int bx = (int) Math.floor(x), bz = (int) Math.floor(z), h = fireHalf();
            if (Math.abs(bx - coreX) <= h && Math.abs(bz - coreZ) <= h) {
                return true;
            }
            int[] f = frame(bx, bz);
            return inAny(approaches, f[0], f[1]);
        }

        /**
         * How far across (Chebyshev, from the core) any question of this field reaches: the air defence's circle, the level disc, the zone,
         * the funnels and the neutral corridors (the frame's turns keep the distance). Index's. Pure.
         */
        public int reach() {
            int m = Math.max(AD_RADIUS, radius + EmbassyLayoutSC.LEVEL_OUT) + 1;
            int[][][] all = {zone, approaches, funnels, level};
            for (int[][] rs : all) {
                for (int[] q : rs) {
                    m = Math.max(m, farCheb(q) + 1);
                }
            }
            return m;
        }

        /** {minX, minZ, maxX, maxZ}: the protected zone's box (the registry's index - Settlement.reach; the players' fields - overlapping's first look). */
        public int[] zoneBox() {
            int[] b = null;
            for (int[] q : zone) {
                int[] w = world(q);
                b = b == null ? w : new int[]{Math.min(b[0], w[0]), Math.min(b[1], w[1]), Math.max(b[2], w[2]), Math.max(b[3], w[3])};
            }
            return b;
        }

        /** The world block of emplacement `i`'s gun (BlockShowcase2SC.AA_POST at aaPosts[i], floor + AA_GUN_DY) {x, y, z} - the add-on fires from it. */
        public int[] aaBlock(int i) {
            int[] a = aaPosts[i];
            return new int[]{x(a[0], a[1]), floorY + AA_GUN_DY, z(a[0], a[1])};
        }

        /** The world block of the tower's beacon {x, y, z} (null: no tower). */
        public int[] beaconBlock() {
            return tower == null ? null : new int[]{x((tower[0] + tower[2]) / 2, (tower[1] + tower[3]) / 2), floorY + BEACON_DY,
                    z((tower[0] + tower[2]) / 2, (tower[1] + tower[3]) / 2)};
        }

        /** The tower's desk (BlockShowcase2SC.TOWER_CONSOLE) {x, y, z} - the add-on's tower key (null: no tower - SHORT, HELI). */
        public int[] towerDesk() {
            return tower == null ? null : new int[]{x((tower[0] + tower[2]) / 2, (tower[1] + tower[3]) / 2), floorY + TOWER_DESK_DY,
                    z((tower[0] + tower[2]) / 2, (tower[1] + tower[3]) / 2)};
        }

        /** Service column `i`'s block (BlockShowcase2SC.SERVICE_COLUMN) {x, y, z}, in columns' order. */
        public int[] columnBlock(int i) {
            int[] c = columns[i];
            return new int[]{x(c[0], c[1]), floorY + COLUMN_DY, z(c[0], c[1])};
        }

        /** The windsock mast's foot {x, y, z} (null: none - HELI). */
        public int[] windsockBlock() {
            return windsock == null ? null : new int[]{x(windsock[0], windsock[1]), floorY + WINDSOCK_DY, z(windsock[0], windsock[1])};
        }

        /**
         * The threshold lights' row (a) of a landing over the -a end (`low`) or the +a end: FULL - the displaced threshold; SHORT - one in
         * from the end (the end row is the red end lights'). Integer.MIN_VALUE: no runway.
         */
        public int thresholdRow(boolean low) {
            if (runway == null) {
                return Integer.MIN_VALUE;
            }
            return low ? Math.max(thresholdLow, endLow + 1) : Math.min(thresholdHigh, endHigh - 1);
        }

        /**
         * The fixed places {a, q} of the lights of BlockAirfieldSC meta `meta`, flush in the paving at the floor (package P puts exactly
         * these; the API gives them to the add-on, the world test checks them): LIGHT_END - the paved end rows across; LIGHT_THRESHOLD - the
         * threshold rows across (thresholdRow); LIGHT_EDGE - both paved edge rows (the axis +- the paved half) every LIGHT_STEP from the
         * -a end, off the end and threshold rows; LIGHT_TAXI - both edges of taxiway «А» every LIGHT_STEP (FULL). Empty: none. Pure.
         */
        public List<int[]> lights(int meta) {
            List<int[]> out = new ArrayList<int[]>();
            if (runway == null) {
                return out;
            }
            int lo = runway[1], hi = runway[3], tl = thresholdRow(true), th = thresholdRow(false);
            if (meta == BlockAirfieldSC.LIGHT_END || meta == BlockAirfieldSC.LIGHT_THRESHOLD) {
                boolean end = meta == BlockAirfieldSC.LIGHT_END;
                for (int q = lo; q <= hi; q++) {
                    out.add(new int[]{end ? endLow : tl, q});
                    out.add(new int[]{end ? endHigh : th, q});
                }
            } else if (meta == BlockAirfieldSC.LIGHT_EDGE) {
                for (int a = endLow + LIGHT_STEP; a < endHigh; a += LIGHT_STEP) {
                    if (a != tl && a != th) {
                        out.add(new int[]{a, lo});
                        out.add(new int[]{a, hi});
                    }
                }
            } else if (meta == BlockAirfieldSC.LIGHT_TAXI && taxi != null) {
                for (int a = taxi[0]; a <= taxi[2]; a += LIGHT_STEP) {
                    out.add(new int[]{a, taxi[1]});
                    out.add(new int[]{a, taxi[3]});
                }
            }
            return out;
        }

        /**
         * The add-on's virtual localizer {a, q} of a landing over the -a end (`low`: towards +a - it stands LOC_OUT past the +a end) or the
         * +a end, on the axis; null - no runway. Only FULL has an ILS (and only at an end whose ils bit is set: AirfieldInfoSC.ils).
         */
        public int[] localizer(boolean low) {
            return runway == null ? null : new int[]{low ? endHigh + LOC_OUT : endLow - LOC_OUT, axis};
        }

        /** The add-on's virtual glide slope {a, q}: GS_IN past that landing's threshold, GS_OFF off the axis on the dome's side; null - no runway. */
        public int[] glideSlope(boolean low) {
            return runway == null ? null : new int[]{low ? thresholdLow + GS_IN : thresholdHigh - GS_IN, axis - GS_OFF};
        }

        /** Frame cell (a, q) as the world block {x, y, z} at floor + dy. */
        public int[] block(int a, int q, int dy) {
            return new int[]{x(a, q), floorY + dy, z(a, q)};
        }

        // -------------------------------------------------------------- the pieces' lots

        /**
         * The airfield's lots, in build order (EmbassyPiecesSC.pieces builds them after the city's): the runway (with its shoulders, pads,
         * taxiway, lights, windsock and the runway's fence - FULL, SHORT), the apron (FULL), the terminal and the tower (FULL), the hangar
         * (FULL), the helipad, the funnels (FULL; variant 0 the -a end's, 1 the +a end's), the emplacements (variant: their index).
         * Lot.height is the element's height over the floor. Pure.
         */
        public List<SettlementLayoutSC.Lot> lots() {
            List<SettlementLayoutSC.Lot> out = new ArrayList<SettlementLayoutSC.Lot>();
            int toDome = dirOf(0, -1);
            if (runway != null) {
                int[] box = kind == FULL ? rect(-(radius + EmbassyLayoutSC.LEVEL_OUT), windsock[1] - 1, radius + EmbassyLayoutSC.LEVEL_OUT,
                        radius + EmbassyLayoutSC.LEVEL_OUT) : grow(rect(runway[0], windsock[1] - 1, runway[2], runway[3]), ZONE_MARGIN);
                out.add(lot(EmbassyLayoutSC.LOT_RUNWAY, box, toDome, kind, H_WINDSOCK));
            }
            if (kind == FULL) {
                out.add(lot(EmbassyLayoutSC.LOT_APRON, apron, toDome, 0, H_MONUMENT));
                out.add(lot(EmbassyLayoutSC.LOT_TERMINAL, rect(terminal[0], terminal[1], tower[2], terminal[3]), dirOf(0, -1), 0, BEACON_DY));
                out.add(lot(EmbassyLayoutSC.LOT_HANGAR, hangar, dirOf(1, 0), 0, H_HANGAR));
            }
            int[] c = columns[columns.length - 1];
            out.add(lot(EmbassyLayoutSC.LOT_HELIPAD, rect(helipad[0], c[1], helipad[2], helipad[3]), dirOf(-1, 0), 0, H_COLUMN));
            for (int i = 0; i < funnels.length; i++) {
                out.add(lot(EmbassyLayoutSC.LOT_FUNNEL, funnels[i], i == 0 ? dirOf(-1, 0) : dirOf(1, 0), i, 0));
            }
            for (int i = 0; i < aaPosts.length; i++) {
                int[] a = aaPosts[i];
                out.add(lot(EmbassyLayoutSC.LOT_AIR_DEFENCE, rect(a[0] - AA_HALF, a[1] - AA_HALF, a[0] + AA_HALF, a[1] + AA_HALF),
                        dirOf(-Integer.signum(a[0]), 0), i, H_AD));
            }
            return out;
        }

        private SettlementLayoutSC.Lot lot(int kind, int[] q, int facing, int variant, int height) {
            int[] w = world(q);
            return new SettlementLayoutSC.Lot(kind, w[0], w[1], w[2], w[3], facing, variant, height);
        }
    }

    // ================================================================== rectangles (frame {a0, q0, a1, q1}, inclusive)

    /** A rectangle from two corners. */
    public static int[] rect(int a0, int q0, int a1, int q1) {
        return new int[]{Math.min(a0, a1), Math.min(q0, q1), Math.max(a0, a1), Math.max(q0, q1)};
    }

    /** `q` grown by `m` each way. */
    public static int[] grow(int[] q, int m) {
        return new int[]{q[0] - m, q[1] - m, q[2] + m, q[3] + m};
    }

    /** Cell (a, q) is in rectangle `r`. */
    public static boolean in(int[] r, int a, int q) {
        return r != null && a >= r[0] && a <= r[2] && q >= r[1] && q <= r[3];
    }

    static boolean inAny(int[][] rs, int a, int q) {
        for (int[] r : rs) {
            if (in(r, a, q)) {
                return true;
            }
        }
        return false;
    }

    /** How far cell (a, q) lies outside rectangle `r` (0 inside) - the blend band's distance. */
    public static double past(int[] r, int a, int q) {
        int da = Math.max(0, Math.max(r[0] - a, a - r[2])), dq = Math.max(0, Math.max(r[1] - q, q - r[3]));
        return Math.sqrt((double) da * da + (double) dq * dq);
    }

    /** The distance between two rectangles' nearest cells (0: they touch or overlap). */
    public static double gap(int[] r, int[] t) {
        int da = Math.max(0, Math.max(t[0] - r[2], r[0] - t[2])), dq = Math.max(0, Math.max(t[1] - r[3], r[1] - t[3]));
        return Math.sqrt((double) da * da + (double) dq * dq);
    }

    /** The squared distance from the core of rectangle `r`'s nearest cell. */
    static long nearSq(int[] r) {
        long a = Math.max(r[0], Math.min(0, r[2])), q = Math.max(r[1], Math.min(0, r[3]));
        return a * a + q * q;
    }

    /** The farthest cell of `r` from the core, Chebyshev (the square's check). */
    static int farCheb(int[] r) {
        return Math.max(Math.max(Math.abs(r[0]), Math.abs(r[2])), Math.max(Math.abs(r[1]), Math.abs(r[3])));
    }

    // ================================================================== the self-test's check

    /**
     * What breaks the field's rules in plan `p` (null: nothing - and for a plan without a field) - the self-test's check (§10.1 п. 3):
     * every element (but the funnels and the neutral corridors) outside the dome + DOME_CLEAR, inside the square (+-(r + 39)), on the
     * level ground; no element on the Vokzal's platform or in its dock zone, none on a lot of the city (but the wall's box, which is
     * the dome's square); the apron and every stand at least WING_CLEAR from the runway's axis; every building (the terminal, the tower
     * with its beacon, the hangar, the monument, an emplacement, the windsock, a column) under the 1:TRANSITION transitional surface from
     * the strip and - within APPROACH_HALF of the axis past an end - under the 1:FUNNEL_SLOPE plane from its threshold; the lots' boxes
     * are the elements'; the guards' posts on the field at least WING_CLEAR off the runway's axis. Pure.
     */
    public static String problem(EmbassyLayoutSC.Plan p) {
        Field f = p == null ? null : p.field;
        if (f == null) {
            return null;
        }
        int r = f.radius, sq = r + EmbassyLayoutSC.LEVEL_OUT + EmbassyLayoutSC.BLEND;
        long dome2 = (long) (r + DOME_CLEAR) * (r + DOME_CLEAR);
        List<Object[]> parts = elements(f);
        int beacon = r + EmbassyLayoutSC.VOKZAL_OUT, vh = EmbassyLayoutSC.VOKZAL_HALF;
        int[] vokzal = rect(beacon - vh, -vh * f.side, beacon + vh, vh * f.side);
        for (Object[] e : parts) {
            String name = (String) e[0];
            int[] q = (int[]) e[1];
            if (nearSq(q) <= dome2) {
                return name + " inside the dome + " + DOME_CLEAR;
            }
            if (farCheb(q) > sq) {
                return name + " outside the square +-" + sq;
            }
            int[][] corners = {{q[0], q[1]}, {q[0], q[3]}, {q[2], q[1]}, {q[2], q[3]}};
            for (int[] c : corners) {
                if (f.levelPast(f.x(c[0], c[1]), f.z(c[0], c[1])) > 0D) {
                    return name + " off the level ground at " + c[0] + " " + c[1];
                }
            }
            if (gap(q, vokzal) <= 0D) {
                return name + " on the Vokzal's platform";
            }
            double ba = Math.max(0, Math.max(q[0] - beacon, beacon - q[2])), bq = Math.max(0, Math.max(q[1], -q[3]));
            if (ba * ba + bq * bq <= (double) EmbassyLayoutSC.DOCK_ZONE * EmbassyLayoutSC.DOCK_ZONE) {
                return name + " in the Vokzal's dock zone";
            }
            int h = ((Integer) e[2]).intValue();
            if (h > 0 && f.strip != null) {
                double d = gap(q, f.strip);
                if (h * TRANSITION > d) {
                    return name + " (" + h + " high) through the 1:" + TRANSITION + " transitional surface (" + (int) d + " from the strip)";
                }
                double off = Math.max(0, Math.max(q[1] - f.axis, f.axis - q[3]));
                // past an end: its cells nearest to that end's threshold are the strictest (the plane rises outwards)
                int pastLow = q[0] < f.endLow ? f.thresholdLow - Math.min(q[2], f.endLow - 1) : Integer.MAX_VALUE;
                int pastHigh = q[2] > f.endHigh ? Math.max(q[0], f.endHigh + 1) - f.thresholdHigh : Integer.MAX_VALUE;
                if (off <= APPROACH_HALF && (long) h * FUNNEL_SLOPE > Math.min(pastLow, pastHigh)) {
                    return name + " (" + h + " high) through the 1:" + FUNNEL_SLOPE + " approach surface";
                }
            }
        }
        if (f.apron != null && f.apron[3] > f.axis - WING_CLEAR) {
            return "the apron within " + WING_CLEAR + " of the runway's axis";
        }
        for (int i = 0; i < f.spots.length; i++) {
            if (f.runway != null && f.spots[i][3] > f.axis - WING_CLEAR) {
                return SPOT_KEYS[Math.min(i, SPOT_KEYS.length - 1)] + " within " + WING_CLEAR + " of the runway's axis";
            }
        }
        for (int i = 0; f.posts != null && f.runway != null && i < f.posts.length; i++) {
            if (Math.abs(f.posts[i][1] - f.axis) < WING_CLEAR) {
                return "post " + i + " within " + WING_CLEAR + " of the runway's axis (a wing over the guard)";
            }
        }
        for (SettlementLayoutSC.Lot l : p.lots) {
            if (l.kind == EmbassyLayoutSC.LOT_WALL) {
                continue;                                       // the wall's lot is the dome's square: the band itself is inside the dome
            }
            int[] la = f.frame(l.x0, l.z0), lb = f.frame(l.x1, l.z1);
            int[] city = rect(la[0], la[1], lb[0], lb[1]);
            for (Object[] e : parts) {
                if (gap((int[]) e[1], city) <= 0D) {
                    return e[0] + " on the city's lot of kind " + l.kind;
                }
            }
        }
        return null;
    }

    /** problem()'s elements: {name, rectangle, height over the floor (0: paving)}. */
    static List<Object[]> elements(Field f) {
        List<Object[]> out = new ArrayList<Object[]>();
        add(out, "the runway", f.strip, 0);
        add(out, "the -a pad", f.padLow, 0);
        add(out, "the +a pad", f.padHigh, 0);
        add(out, "taxiway A", f.taxi, 0);
        add(out, "the apron", f.apron, 0);
        add(out, "the terminal", f.terminal, H_TERMINAL);
        add(out, "the tower", f.tower, BEACON_DY);
        add(out, "the hangar", f.hangar, H_HANGAR);
        add(out, "the monument", f.monument, H_MONUMENT);
        add(out, "the helipad", f.helipad, 0);
        for (int i = 0; i < f.spots.length; i++) {
            add(out, SPOT_KEYS[Math.min(i, SPOT_KEYS.length - 1)], f.spots[i], 0);
        }
        for (int i = 0; i < f.columns.length; i++) {
            add(out, "column " + i, rect(f.columns[i][0], f.columns[i][1], f.columns[i][0], f.columns[i][1]), H_COLUMN);
        }
        for (int i = 0; i < f.aaPosts.length; i++) {
            int[] a = f.aaPosts[i];
            add(out, "emplacement " + i, rect(a[0] - AA_HALF, a[1] - AA_HALF, a[0] + AA_HALF, a[1] + AA_HALF), H_AD);
        }
        if (f.windsock != null) {
            add(out, "the windsock", rect(f.windsock[0], f.windsock[1], f.windsock[0], f.windsock[1]), H_WINDSOCK);
        }
        if (f.posts != null) {
            for (int i = 0; i < f.posts.length; i++) {
                add(out, "post " + i, rect(f.posts[i][0], f.posts[i][1], f.posts[i][0], f.posts[i][1]), 0);
            }
        }
        return out;
    }

    private static void add(List<Object[]> out, String name, int[] q, int h) {
        if (q != null) {
            out.add(new Object[]{name, q, Integer.valueOf(h)});
        }
    }
}
