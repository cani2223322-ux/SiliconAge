package com.sc.embassy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import com.sc.settle.NpcKindSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.gen.SettlementLayoutSC;

/**
 * PACKAGE S (docs/plan-substrate-tech-f6.md §6.1, rules ПЕ-7, ПЕ-8, ПЖ-13, ПГ-21): an embassy's quarters inside its plan - the lots
 * its pieces build (EmbassyPiecesSC) and one post a roster entry (in the roster's order: Plan.posts.get(i) is roster.get(i)'s), each
 * post's home. Called by EmbassyLayoutSC.plan after the frame, the fixed points and the Vokzal are set; pure (`rnd` is the plan's own).
 * <p>
 * Fixed (the coordinator's): the class, lay's signature, the rule that every roster entry gets exactly one post, in order. Package S's
 * quarters, in the embassy's frame (a towards the main gate, p across; EmbassyLayoutSC's fixed points), from the middle out:
 * <ul>
 * <li>the Heart - one lot, the guard square 21 x 21 (SQUARE_HALF): the core under its 7 x 7 cap, the Clone Capsule behind P_CLONE, the
 * Ambassador's hall at P_HALL (the Ambassador's post), the armour station behind P_SQUARE (the Captain's), the 4 charge pads
 * (P_PAD0..3), the plaza P_PLAZA (the Commandant's);</li>
 * <li>the ring of 11 x 11 workshops round the square - one a trade of the roster (SettlementLayoutSC.workshopLine: the Power
 * Engineer's with its roof, the Salvager's warehouse) on the square's four corners and its two sides (the streets run front and
 * back); the trades' posts by workshopPosts;</li>
 * <li>the main street (STREET_HALF: 7 across) from the square to the main gate, the back street from the square through the supply
 * depots (DEPOT_*, round P_DEPOT - the Quartermasters') to the back gate; the greenhouse (the only green: leaves and flowers), the
 * living cells (SettlementLayoutSC.HOUSE, houseHomes - every settler sleeps in one, the Dispatchers at their post) in two quarters
 * (AT_CELLS: two rows facing a lane, CELL_SLOTS) and the energy fields (LOT_ENERGY - solar panels, wind masts: decor of the belt),
 * each where it wants to stand or at the nearest free spot to it, a lane round every lot, inside the ring road;</li>
 * <li>the wall (its outer face on r - WALL_IN, WALL_T thick, WALL_H high - one lot: its box is the ring's, it builds only the band,
 * wallCell - the gates' and the towers' lots stand in it - and the ring road inside it, roadCell), its four towers (TOWER x TOWER,
 * the platform under P_TOWER0..3 - the Senior Wardens'), the main and the back gate (the opening GATE_W wide; the Sentries' posts by
 * them, two more on the ring road at the sides - «обход по стене»; Sentries #1 and #2 sleep by the Heart);</li>
 * <li>outside the main gate on the level ground: the street on to the Vokzal (its 25 x 25 platform round the dock's beacon, the
 * Dispatchers' office OFFICE round P_OFFICE - both Dispatchers' -, the cryo tanks TANKS), the Great Ring's lot on a = r + 24 (nested
 * in the Vokzal's: the only lots that overlap).</li>
 * </ul>
 * Every lot stands under the dome's shell (a hemisphere from the core, half a block clear) or on the level ground, inside the area;
 * problem() checks it all (the self-test's).
 */
public final class EmbassyQuartersSC {

    /** The streets' half width: paving |p| <= 2, lamp posts at |p| = 3 (7 across - the gates' opening). */
    public static final int STREET_HALF = EmbassyLayoutSC.GATE_W / 2;
    /** A gate's lot: the opening (GATE_W) and a pylon each side across; along the street from r - GATE_IN to r - GATE_OUT. */
    public static final int GATE_HALF = EmbassyLayoutSC.GATE_W / 2 + 1, GATE_IN = 6, GATE_OUT = 2;
    /** The ring of workshops: their middles this far out from the core on the square's corners and sides (17). */
    public static final int RING_C = EmbassyLayoutSC.SQUARE_HALF + 2 + EmbassyLayoutSC.WORKSHOP / 2;
    /** The supply depots: halves along the back street and across it (11 x 21 round P_DEPOT, the street through the middle). */
    public static final int DEPOT_HALF_A = 5, DEPOT_HALF_P = 10;
    /** The greenhouse's side and height; a tower's side. */
    public static final int GREENHOUSE = 15, GREENHOUSE_H = 7, TOWER = 5;
    /** A tower's platform block over the floor (P_TOWER stands on it: EmbassyLayoutSC.TOWER_H - 1). */
    public static final int TOWER_PLATFORM = EmbassyLayoutSC.TOWER_H - 2;
    /** Lots' heights (their top face over the floor - the shell's fit): the Heart, a street (its lamp posts), a gate, the wall, the depot, the Vokzal. */
    public static final int HEART_H = 7, STREET_H = 5, GATE_H = EmbassyLayoutSC.WALL_H + 2, WALL_LOT_H = EmbassyLayoutSC.WALL_H + 1, DEPOT_H = 6,
            VOKZAL_H = 5;
    /** «Обход»: the ring road inside the wall - ROAD_W wide, its outer edge ROAD_GAP inside the wall's inner face (roadCell). */
    public static final int ROAD_W = 3, ROAD_GAP = 2;
    /** Placed lots keep a lane of one inside the ring road. */
    public static final int WALL_LANE = ROAD_GAP + ROAD_W + 1;
    /** Placed lots (the greenhouse, the cells, the energy fields) have their middle no nearer the core than this (Chebyshev: past the workshops' ring and a lane). */
    public static final int CELLS_FROM = RING_C + EmbassyLayoutSC.WORKSHOP / 2 + 3;
    /** Where the greenhouse and the two quarters of cells want to stand {a, p}. */
    public static final int[] AT_GREENHOUSE = {38, -38};
    public static final int[][] AT_CELLS = {{-36, 36}, {-36, -36}};
    /** A quarter of cells: two rows facing each other across a lane (CELL_LANE wide), three a row - their middles from the quarter's {a, p}, in fill order. */
    public static final int CELL_LANE = 3;
    public static final int[][] CELL_SLOTS = {{-4, 0}, {4, 0}, {-4, -8}, {4, -8}, {-4, 8}, {4, 8}};
    /**
     * Lot kind (appended after EmbassyLayoutSC.LOT_OFFICE, package S's): «Энергополе» - the embassy's decor of the green world's power:
     * a field of solar panels with its transformer (Lot.variant 0) or a row of wind masts over their storages (1). ENERGY_L x ENERGY_S.
     */
    public static final int LOT_ENERGY = EmbassyLayoutSC.LOT_ENERGY;
    public static final int ENERGY_L = 13, ENERGY_S = 9, ENERGY_H = 7;
    /** The energy fields {a, p, variant}: the solar field across the main street from the greenhouse, wind masts at the sides. */
    public static final int[][] AT_ENERGY = {{38, 38, 0}, {0, 58, 1}, {0, -58, 1}};
    /** The Dispatchers' office and the cryo tanks round the beacon {da0, p0, da1, p1} (da from the beacon along the street; in the dock zone). */
    public static final int[] OFFICE = {-8, -10, -4, -6}, TANKS = {-8, 6, -4, 10};
    /** The second Dispatcher's place from P_OFFICE {da, dp}. */
    public static final int[] AT_DISPATCHER2 = {-1, 0};
    /** The Quartermasters' places round P_DEPOT {da, dp} (the depot's yard and the sheds' open fronts). */
    public static final int[][] AT_QUARTERMASTER = {{0, 0}, {0, 4}, {0, -4}, {-3, 4}, {-3, -4}, {3, 4}, {3, -4}, {-3, 0}};
    /** Sentries #1 and #2 sleep by the Heart (ПГ-5 «двое у Сердца ночью»), in the frame {a, p}. */
    public static final int[][] NIGHT_AT = {{-5, -4}, {-5, 4}};

    private EmbassyQuartersSC() {
    }

    // ------------------------------------------------------------------ shared geometry (the pieces build by it)

    /** The wall's inner face (cells past it, up to r - WALL_IN, are the wall's band). */
    public static int innerFace(int radius) {
        return radius - EmbassyLayoutSC.WALL_IN - EmbassyLayoutSC.WALL_T;
    }

    /** The towers' diagonal offset - EmbassyLayoutSC.plan's for P_TOWER0..3 (problem() checks they agree). */
    public static int towerDiag(int radius) {
        return (int) Math.round((radius - EmbassyLayoutSC.WALL_IN - 1) / Math.sqrt(2D));
    }

    /** Tower i's lot {a0, p0, a1, p1}: TOWER x TOWER, its platform's cell P_TOWER one in from its outer corner. */
    public static int[] towerBox(int radius, int i) {
        int d = towerDiag(radius);
        int[][] at = {{d, -d}, {d, d}, {-d, d}, {-d, -d}};
        int ta = at[i & 3][0], tp = at[i & 3][1], sa = ta > 0 ? 1 : -1, sp = tp > 0 ? 1 : -1;
        return new int[]{Math.min(ta - 3 * sa, ta + sa), Math.min(tp - 3 * sp, tp + sp), Math.max(ta - 3 * sa, ta + sa),
                Math.max(tp - 3 * sp, tp + sp)};
    }

    /** The main (back: false) gate's lot {a0, p0, a1, p1}. */
    public static int[] gateBox(int radius, boolean main) {
        return main ? new int[]{radius - GATE_IN, -GATE_HALF, radius - GATE_OUT, GATE_HALF}
                : new int[]{-(radius - GATE_OUT), -GATE_HALF, -(radius - GATE_IN), GATE_HALF};
    }

    /** (a, p) is the wall's: in its band (past the inner face, up to r - WALL_IN) and in neither gate nor tower. */
    public static boolean wallCell(int radius, int a, int p) {
        long d2 = (long) a * a + (long) p * p;
        long in = innerFace(radius), out = radius - EmbassyLayoutSC.WALL_IN;
        if (d2 <= in * in || d2 > out * out) {
            return false;
        }
        if (inside(gateBox(radius, true), a, p) || inside(gateBox(radius, false), a, p)) {
            return false;
        }
        for (int i = 0; i < 4; i++) {
            if (inside(towerBox(radius, i), a, p)) {
                return false;
            }
        }
        return true;
    }

    /**
     * (a, p) is the ring road's («обход по стене»): ROAD_W wide, ROAD_GAP inside the wall's inner face, not where a street crosses it
     * (|p| <= STREET_HALF: the main and the back street) nor in a tower (the road goes round its inner corner).
     */
    public static boolean roadCell(int radius, int a, int p) {
        long d2 = (long) a * a + (long) p * p;
        long out = innerFace(radius) - ROAD_GAP, in = out - ROAD_W;
        if (d2 <= in * in || d2 > out * out || Math.abs(p) <= STREET_HALF) {
            return false;
        }
        for (int i = 0; i < 4; i++) {
            if (inside(towerBox(radius, i), a, p)) {
                return false;
            }
        }
        return true;
    }

    /** (a, p) lies in the rectangle {a0, p0, a1, p1}. */
    public static boolean inside(int[] q, int a, int p) {
        return a >= q[0] && a <= q[2] && p >= q[1] && p <= q[3];
    }

    /** The Great Ring's coils in its plane: (p, q) - q up from the ring's middle - is one when its distance rounds to RING_R (40 of them). */
    public static boolean ringCoil(int p, int q) {
        return Math.round(Math.sqrt((double) p * p + (double) q * q)) == EmbassyLayoutSC.RING_R;
    }

    /** The trades of a roster with a workshop, in TRADES' order and the Power Engineer last, each once (not the Ambassador, the Dispatchers, the Quartermasters). */
    public static List<NpcKindSC> workshopKinds(List<NpcKindSC> roster) {
        List<NpcKindSC> out = new ArrayList<NpcKindSC>();
        for (NpcKindSC k : EmbassyLayoutSC.TRADES) {
            if (roster.contains(k)) {
                out.add(k);
            }
        }
        if (roster.contains(NpcKindSC.POWER_ENGINEER)) {
            out.add(NpcKindSC.POWER_ENGINEER);
        }
        return out;
    }

    // ------------------------------------------------------------------ the quarters

    /** The quarters of plan `p` for `roster` (see the class comment). */
    public static void lay(EmbassyLayoutSC.Plan p, List<NpcKindSC> roster, Random rnd) {
        int r = p.radius, stand = p.floorY + 1, in = innerFace(r), sq = EmbassyLayoutSC.SQUARE_HALF, sh = STREET_HALF;
        int beacon = r + EmbassyLayoutSC.VOKZAL_OUT, vh = EmbassyLayoutSC.VOKZAL_HALF, ringA = beacon + EmbassyLayoutSC.RING_BACK;
        int depotA = framed(p, EmbassyLayoutSC.P_DEPOT, -(in - 12), 0)[0];
        Placer f = new Placer(r);

        // ---- the fixed ground: the square, the streets, the gates, the depots, the towers, the Sentries' posts by the wall
        int[] heart = {-sq, -sq, sq, sq};
        int[] street = {sq + 1, -sh, in - 2, sh};
        int[] gate = gateBox(r, true), backGate = gateBox(r, false);
        int[] outer = {r - 1, -sh, beacon - vh - 1, sh};
        int[] backOut = {-(in - 2), -sh, depotA - DEPOT_HALF_A - 1, sh};
        int[] depot = {depotA - DEPOT_HALF_A, -DEPOT_HALF_P, depotA + DEPOT_HALF_A, DEPOT_HALF_P};
        int[] backIn = {depotA + DEPOT_HALF_A + 1, -sh, -sq - 1, sh};
        int[] vokzal = {beacon - vh, -vh, beacon + vh, vh};
        int[] ring = {ringA, -EmbassyLayoutSC.RING_R, ringA, EmbassyLayoutSC.RING_R};
        int[][] towers = new int[4][];
        for (int i = 0; i < 4; i++) {
            towers[i] = towerBox(r, i);
        }
        int[][] sentryAt = sentryPosts(p);
        int[][] fixed = {heart, street, gate, backGate, backOut, depot, backIn};
        for (int[] q : fixed) {
            f.taken.add(q);
        }
        for (int[] q : towers) {
            f.taken.add(q);
        }
        for (int[] s : sentryAt) {
            f.taken.add(new int[]{s[0], s[1], s[0], s[1]});
        }

        // ---- the workshops (the square's corners, then its sides), the greenhouse, the living cells
        List<NpcKindSC> trades = workshopKinds(roster);
        int ws = EmbassyLayoutSC.WORKSHOP, c = RING_C;
        int[][] want = {{-c, -c}, {-c, c}, {c, -c}, {c, c}, {0, -c}, {0, c}};
        int[][] workshop = new int[trades.size()][];
        for (int i = 0; i < trades.size(); i++) {
            workshop[i] = f.place(want[i % want.length][0], want[i % want.length][1], ws, ws,
                    SettlementLayoutSC.workshopHeight(trades.get(i)), 0);
        }
        int[] greenhouse = f.place(AT_GREENHOUSE[0], AT_GREENHOUSE[1], GREENHOUSE, GREENHOUSE, GREENHOUSE_H, CELLS_FROM);
        int sleepers = 0;
        for (NpcKindSC k : roster) {
            sleepers += !k.guard && k != NpcKindSC.DISPATCHER ? 1 : 0;
        }
        int cells = (sleepers + SettlementLayoutSC.HOMES - 1) / SettlementLayoutSC.HOMES;
        List<int[]> house = new ArrayList<int[]>();
        List<Integer> houseFacing = new ArrayList<Integer>();
        int hs = SettlementLayoutSC.HOUSE, hh2 = hs / 2;
        for (int i = 0; i < cells; i++) {                      // the quarters in turn, slot by slot; a slot taken - the nearest free spot
            int[] at = AT_CELLS[i % AT_CELLS.length];
            int slot = i / AT_CELLS.length;
            int[] h = null;
            int face = -1;
            if (slot < CELL_SLOTS.length) {
                int ca = at[0] + CELL_SLOTS[slot][0], cp = at[1] + CELL_SLOTS[slot][1];
                int[] q = {ca - hh2, cp - hh2, ca - hh2 + hs - 1, cp - hh2 + hs - 1};
                if (f.fits(q, SettlementLayoutSC.HOUSE_H) && f.free(q)) {
                    f.taken.add(q);
                    h = q;
                    face = frameDir(p.gateDir, CELL_SLOTS[slot][0] < 0 ? 1 : -1, 0);     // the two rows face their lane
                }
            }
            if (h == null) {
                h = f.place(at[0], at[1], hs, hs, SettlementLayoutSC.HOUSE_H, CELLS_FROM);
                face = h == null ? -1 : facingToCentre(p.gateDir, h);
            }
            if (h != null) {
                house.add(h);
                houseFacing.add(Integer.valueOf(face));
            }
        }
        List<int[]> energy = new ArrayList<int[]>();
        List<Integer> energyKind = new ArrayList<Integer>();
        for (int[] e : AT_ENERGY) {
            int[] q = f.place(e[0], e[1], ENERGY_L, ENERGY_S, ENERGY_H, CELLS_FROM);
            if (q != null) {
                energy.add(q);
                energyKind.add(Integer.valueOf(e[2]));
            }
        }
        int homesEach = house.size() * SettlementLayoutSC.HOMES >= sleepers ? SettlementLayoutSC.HOMES : SettlementLayoutSC.HOMES_MAX;

        // ---- the lots, in build order (the Ring after the Vokzal it stands on)
        int g = p.gateDir, back = p.backGateDir;
        p.lots.add(lot(p, EmbassyLayoutSC.LOT_HEART, heart, g, 0, HEART_H));
        p.lots.add(lot(p, EmbassyLayoutSC.LOT_STREET, street, g, 0, STREET_H));
        p.lots.add(lot(p, EmbassyLayoutSC.LOT_STREET, backIn, back, 0, STREET_H));
        p.lots.add(lot(p, EmbassyLayoutSC.LOT_STREET, backOut, back, 0, STREET_H));
        p.lots.add(lot(p, EmbassyLayoutSC.LOT_STREET, outer, g, 0, STREET_H));
        p.lots.add(lot(p, EmbassyLayoutSC.LOT_GATE, gate, g, 0, GATE_H));
        p.lots.add(lot(p, EmbassyLayoutSC.LOT_GATE, backGate, back, 1, GATE_H));
        int out = r - EmbassyLayoutSC.WALL_IN;
        p.lots.add(lot(p, EmbassyLayoutSC.LOT_WALL, new int[]{-out, -out, out, out}, g, 0, WALL_LOT_H));
        for (int i = 0; i < 4; i++) {
            int sa = towers[i][0] + towers[i][2] > 0 ? 1 : -1;             // the arch faces the core along a
            p.lots.add(lot(p, EmbassyLayoutSC.LOT_TOWER, towers[i], frameDir(g, -sa, 0), i, EmbassyLayoutSC.TOWER_H));
        }
        SettlementLayoutSC.Lot[] wLots = new SettlementLayoutSC.Lot[trades.size()];
        for (int i = 0; i < trades.size(); i++) {
            if (workshop[i] != null) {
                wLots[i] = lot(p, EmbassyLayoutSC.LOT_WORKSHOP, workshop[i], facingToCentre(g, workshop[i]), trades.get(i).ordinal(),
                        SettlementLayoutSC.workshopHeight(trades.get(i)));
                p.lots.add(wLots[i]);
            }
        }
        p.lots.add(lot(p, EmbassyLayoutSC.LOT_DEPOT, depot, back, 0, DEPOT_H));
        if (greenhouse != null) {
            p.lots.add(lot(p, EmbassyLayoutSC.LOT_GREENHOUSE, greenhouse, facingToCentre(g, greenhouse), 0, GREENHOUSE_H));
        }
        SettlementLayoutSC.Lot[] hLots = new SettlementLayoutSC.Lot[house.size()];
        for (int i = 0; i < house.size(); i++) {
            hLots[i] = lot(p, EmbassyLayoutSC.LOT_HOUSE, house.get(i), houseFacing.get(i).intValue(), rnd.nextInt(SettlementLayoutSC.HOUSE_VARIANTS),
                    SettlementLayoutSC.HOUSE_H);
            p.lots.add(hLots[i]);
        }
        for (int i = 0; i < energy.size(); i++) {
            int[] q = energy.get(i);                            // the entrance towards the core across p: ENERGY_L along its entrance
            p.lots.add(lot(p, LOT_ENERGY, q, frameDir(g, 0, q[1] + q[3] > 0 ? -1 : 1), energyKind.get(i).intValue(), ENERGY_H));
        }
        p.lots.add(lot(p, EmbassyLayoutSC.LOT_VOKZAL, vokzal, g, 0, VOKZAL_H));
        p.lots.add(lot(p, EmbassyLayoutSC.LOT_RING, ring, g, 0, EmbassyLayoutSC.RING_DY + EmbassyLayoutSC.RING_R + 1));

        // ---- the posts, one a roster entry in its order, and the homes
        int[] office = framed(p, EmbassyLayoutSC.P_OFFICE, beacon - 6, -8);
        int[] used = new int[trades.size()];
        int home = 0, sentries = 0, seniors = 0, quartermasters = 0, dispatchers = 0;
        for (NpcKindSC k : roster) {
            int[] at, sleep = null;
            int y = stand;
            if (k == NpcKindSC.ELDER) {                         // ПЖ-14: the Ambassador - his hall
                at = framed(p, EmbassyLayoutSC.P_HALL, EmbassyLayoutSC.AT_HALL[0], EmbassyLayoutSC.AT_HALL[1]);
            } else if (k == NpcKindSC.DISPATCHER) {             // the office on the Vokzal: their home is round it (NpcLinkSC.setHome)
                at = dispatchers++ == 0 ? office : new int[]{office[0] + AT_DISPATCHER2[0], office[1] + AT_DISPATCHER2[1]};
                sleep = at;
            } else if (k == NpcKindSC.QUARTERMASTER) {          // ПЖ-12: the supply depots
                int[] q = AT_QUARTERMASTER[quartermasters++ % AT_QUARTERMASTER.length];
                at = new int[]{depotA + q[0], q[1]};
            } else if (k == NpcKindSC.CAPTAIN) {                // ПГ-21: the guard square, before the armour station
                at = framed(p, SettlementRegistrySC.P_SQUARE, EmbassyLayoutSC.AT_SQUARE[0], EmbassyLayoutSC.AT_SQUARE[1]);
            } else if (k == NpcKindSC.COMMANDANT) {             // the plaza
                at = framed(p, SettlementRegistrySC.P_PLAZA, EmbassyLayoutSC.AT_PLAZA[0], EmbassyLayoutSC.AT_PLAZA[1]);
            } else if (k == NpcKindSC.SENTRY) {                 // the gates, the wall; #1 and #2 sleep by the Heart
                at = sentryAt[sentries % sentryAt.length];
                if (sentries >= 1 && sentries <= NIGHT_AT.length) {
                    sleep = NIGHT_AT[sentries - 1];
                }
                sentries++;
            } else if (k == NpcKindSC.SENIOR_WARDEN) {          // the towers' platforms
                int[] t = p.pointOf(SettlementRegistrySC.P_TOWER + (seniors++ % 4));
                at = t == null ? new int[]{0, 0} : p.frame(t[0], t[2]);
                y = t == null ? stand : t[1];
            } else {                                            // a trade: at its workshop's machines
                int w = trades.indexOf(k);
                SettlementLayoutSC.Lot l = w < 0 ? null : wLots[w];
                if (l != null) {
                    int[][] posts = SettlementLayoutSC.workshopPosts(k, ws);
                    int[] q = posts[used[w]++ % posts.length];
                    at = p.frame(l.wx(q[0], q[1]), l.wz(q[0], q[1]));
                } else {                                        // no room for its workshop (never in practice): by the plaza
                    at = new int[]{EmbassyLayoutSC.AT_PLAZA[0], 2 + home % 5};
                }
            }
            if (!k.guard && k != NpcKindSC.DISPATCHER) {        // every settler sleeps in a living cell
                int cell = home / homesEach;
                if (cell < hLots.length) {
                    int[] hh = SettlementLayoutSC.houseHomes()[home % homesEach];
                    sleep = p.frame(hLots[cell].wx(hh[0], hh[1]), hLots[cell].wz(hh[0], hh[1]));
                }
                home++;
            }
            int sy = sleep == null ? y : stand;
            if (sleep == null) {
                sleep = at;
            }
            p.posts.add(new SettlementLayoutSC.Post(k, p.x(at[0], at[1]), y, p.z(at[0], at[1]), p.x(sleep[0], sleep[1]), sy,
                    p.z(sleep[0], sleep[1])));
        }
    }

    /** The six Sentries' posts {a, p}: by the main gate (P_GATE first), by the back gate, by each gate's other side, inside the wall at the sides. */
    static int[][] sentryPosts(EmbassyLayoutSC.Plan p) {
        int in = innerFace(p.radius);
        int[] gate = framed(p, SettlementRegistrySC.P_GATE, in - 2, 2), back = framed(p, EmbassyLayoutSC.P_BACK_GATE, -(in - 2), -2);
        return new int[][]{gate, back, {gate[0], -gate[1]}, {back[0], -back[1]}, {0, in - 2}, {0, -(in - 2)}};
    }

    /** A named point of the plan in its frame {a, p} (its fallback where the plan has none). */
    private static int[] framed(EmbassyLayoutSC.Plan p, String name, int a, int q) {
        int[] at = p.pointOf(name);
        return at == null ? new int[]{a, q} : p.frame(at[0], at[2]);
    }

    /** A lot of the frame's rectangle {a0, p0, a1, p1} in world coordinates, its entrance facing `facing` (NORTH..WEST). */
    static SettlementLayoutSC.Lot lot(EmbassyLayoutSC.Plan p, int kind, int[] q, int facing, int variant, int height) {
        int xa = p.x(q[0], q[1]), xb = p.x(q[2], q[3]), za = p.z(q[0], q[1]), zb = p.z(q[2], q[3]);
        return new SettlementLayoutSC.Lot(kind, Math.min(xa, xb), Math.min(za, zb), Math.max(xa, xb), Math.max(za, zb), facing, variant, height);
    }

    /** The world direction (NORTH..WEST) of a step (da, dp) of the frame whose main gate lies `gateDir`. */
    public static int frameDir(int gateDir, int da, int dp) {
        int g = gateDir & 3;
        return SettlementLayoutSC.dirOf(da * SettlementLayoutSC.DIR_X[g] - dp * SettlementLayoutSC.DIR_Z[g],
                da * SettlementLayoutSC.DIR_Z[g] + dp * SettlementLayoutSC.DIR_X[g]);
    }

    /** A placed lot's entrance faces the core along its longer offset (a corner one: the square behind it, the street before it). */
    static int facingToCentre(int gateDir, int[] q) {
        int ca = q[0] + q[2], cp = q[1] + q[3];
        int da = 0, dp = 0;
        if (Math.abs(ca) > Math.abs(cp) || Math.abs(ca) == Math.abs(cp) && ca < 0) {
            da = ca > 0 ? -1 : 1;
        } else {
            dp = cp > 0 ? -1 : 1;
        }
        return frameDir(gateDir, da, dp);
    }

    // ------------------------------------------------------------------ the self-test's check

    /**
     * What breaks the quarters' rules in plan `p` of `roster` (null: nothing) - the self-test's check (docs/plan-substrate-tech-f6.md
     * §10.1 п. 10): a post a roster entry in its order; every settler's post but the Dispatchers' and every home inside the dome - 2;
     * the Dispatchers in the dock zone; the guards inside the wall's inner face or on a tower's platform (P_TOWER*); the towers where
     * the plan's P_TOWER says; no two lots overlap (the wall: no lot but a gate or a tower in its band; the Ring: inside the Vokzal);
     * every lot inside the area, under the dome's shell (its top half a block under it) or on the level ground; one Ring lot, on
     * a = r + VOKZAL_OUT + RING_BACK. Pure.
     */
    public static String problem(EmbassyLayoutSC.Plan p, List<NpcKindSC> roster) {
        int r = p.radius, in = innerFace(r), out = r - EmbassyLayoutSC.WALL_IN;
        if (p.posts.size() != roster.size()) {
            return p.posts.size() + " posts for " + roster.size() + " in the roster";
        }
        for (int i = 0; i < 4; i++) {
            int[] t = p.pointOf(SettlementRegistrySC.P_TOWER + i);
            int[] b = towerBox(r, i);
            int[] f = t == null ? null : p.frame(t[0], t[2]);
            if (f == null || 2 * f[0] - b[0] - b[2] != 2 * (f[0] > 0 ? 1 : -1) || 2 * f[1] - b[1] - b[3] != 2 * (f[1] > 0 ? 1 : -1)) {
                return "tower " + i + " isn't one in from P_TOWER" + i;
            }
        }
        long dome2 = (long) (r - 2) * (r - 2), zone2 = (long) EmbassyLayoutSC.DOCK_ZONE * EmbassyLayoutSC.DOCK_ZONE;
        for (int i = 0; i < roster.size(); i++) {
            SettlementLayoutSC.Post q = p.posts.get(i);
            NpcKindSC k = roster.get(i);
            if (q.kind != k) {
                return "post " + i + " is a " + q.kind + ", the roster's a " + k;
            }
            long dx = q.x - p.coreX, dz = q.z - p.coreZ, hx = q.homeX - p.coreX, hz = q.homeZ - p.coreZ;
            if (k == NpcKindSC.DISPATCHER) {
                long ox = q.x - p.dockX, oz = q.z - p.dockZ;
                if (ox * ox + oz * oz > zone2) {
                    return "a Dispatcher outside the dock zone";
                }
            } else if (!k.guard) {
                if (dx * dx + dz * dz > dome2 || hx * hx + hz * hz > dome2) {
                    return "a " + k + "'s post or home outside the dome - 2";
                }
            } else {
                boolean tower = false;
                for (int t = 0; t < 4; t++) {
                    int[] at = p.pointOf(SettlementRegistrySC.P_TOWER + t);
                    tower |= at != null && at[0] == q.x && at[1] == q.y && at[2] == q.z;
                }
                if (!tower && (dx * dx + dz * dz > (long) in * in || hx * hx + hz * hz > (long) in * in)) {
                    return "a " + k + "'s post outside the wall";
                }
            }
        }
        List<SettlementLayoutSC.Lot> lots = p.lots;
        int[][] rect = new int[lots.size()][];
        int vokzal = -1, rings = 0;
        for (int i = 0; i < lots.size(); i++) {
            SettlementLayoutSC.Lot l = lots.get(i);
            int[] fa = p.frame(l.x0, l.z0), fb = p.frame(l.x1, l.z1);
            rect[i] = new int[]{Math.min(fa[0], fb[0]), Math.min(fa[1], fb[1]), Math.max(fa[0], fb[0]), Math.max(fa[1], fb[1])};
            if (l.kind == EmbassyLayoutSC.LOT_VOKZAL) {
                vokzal = i;
            }
        }
        for (int i = 0; i < lots.size(); i++) {
            SettlementLayoutSC.Lot l = lots.get(i);
            int[] q = rect[i];
            if (l.kind == EmbassyLayoutSC.LOT_WALL) {           // the band: its outer edge under the shell, the box its own
                double e = out + 0.5D, h = l.height - 1, lim = r - 0.5D;
                if (q[0] != -out || q[1] != -out || q[2] != out || q[3] != out || e * e + h * h > lim * lim) {
                    return "the wall's lot isn't its band's box under the shell";
                }
                continue;
            }
            String fit = fits(p, q, l.height);
            if (fit != null) {
                return "lot " + i + " (kind " + l.kind + "): " + fit;
            }
            if (l.kind == EmbassyLayoutSC.LOT_RING) {
                rings++;
                int ringA = r + EmbassyLayoutSC.VOKZAL_OUT + EmbassyLayoutSC.RING_BACK;
                if (q[0] != ringA || q[2] != ringA || vokzal < 0 || !inside(rect[vokzal], q[0], q[1]) || !inside(rect[vokzal], q[2], q[3])) {
                    return "the Ring's lot isn't on a = r + 24 on the Vokzal";
                }
            }
            if (l.kind != EmbassyLayoutSC.LOT_GATE && l.kind != EmbassyLayoutSC.LOT_TOWER) {
                long road = in - ROAD_GAP - ROAD_W;                  // the streets cross the ring road, nothing else stands on it
                long from = l.kind == EmbassyLayoutSC.LOT_STREET ? in : road;
                for (int a = q[0]; a <= q[2]; a++) {
                    for (int b = q[1]; b <= q[3]; b++) {
                        long d2 = (long) a * a + (long) b * b;
                        if (d2 > from * from && d2 <= (long) out * out) {
                            return "lot " + i + " (kind " + l.kind + ") in the wall's or the road's band at " + a + " " + b;
                        }
                    }
                }
            }
            for (int j = i + 1; j < lots.size(); j++) {
                SettlementLayoutSC.Lot m = lots.get(j);
                boolean nested = l.kind == EmbassyLayoutSC.LOT_VOKZAL && m.kind == EmbassyLayoutSC.LOT_RING
                        || m.kind == EmbassyLayoutSC.LOT_VOKZAL && l.kind == EmbassyLayoutSC.LOT_RING;
                if (m.kind != EmbassyLayoutSC.LOT_WALL && !nested && overlaps(q, rect[j], 0)) {
                    return "lots " + i + " (kind " + l.kind + ") and " + j + " (kind " + m.kind + ") overlap";
                }
            }
        }
        return rings == 1 ? null : rings + " Ring lots";
    }

    /** A lot {a0, p0, a1, p1} of top face `height`: inside the area; all under the shell (inside the dome) or all on the level ground. */
    private static String fits(EmbassyLayoutSC.Plan p, int[] q, int height) {
        int r = p.radius;
        int ca = Math.max(Math.abs(q[0]), Math.abs(q[2])), cp = Math.max(Math.abs(q[1]), Math.abs(q[3]));
        double fa = ca + 0.5D, fp = cp + 0.5D, far = Math.sqrt(fa * fa + fp * fp);
        if (Math.sqrt((double) ca * ca + (double) cp * cp) > EmbassySiteSC.area(r)) {
            return "outside the area";
        }
        if (far <= r) {                                         // under the dome: its far top corner half a block under the shell
            double h = Math.max(0, height - 1), lim = r - 0.5D;
            return far * far + h * h <= lim * lim ? null : "through the dome's shell";
        }
        int[][] corners = {{q[0], q[1]}, {q[0], q[3]}, {q[2], q[1]}, {q[2], q[3]}};
        for (int[] c : corners) {
            if (EmbassyLayoutSC.levelPast(c[0], c[1], p.levelRadius) > 0D) {
                return "off the level ground";
            }
        }
        return null;
    }

    static boolean overlaps(int[] q, int[] t, int gap) {
        return q[0] <= t[2] + gap && t[0] <= q[2] + gap && q[1] <= t[3] + gap && t[1] <= q[3] + gap;
    }

    // ------------------------------------------------------------------ placing

    /** The placed lots' search: the nearest free spot to where a lot wants to stand - inside the wall's lane, under the shell, a lane round it. */
    private static final class Placer {
        final int r;
        final long lim2;
        /** The fixed ground and the lots placed so far, {a0, p0, a1, p1}. */
        final List<int[]> taken = new ArrayList<int[]>();

        Placer(int r) {
            this.r = r;
            long lim = innerFace(r) - WALL_LANE;
            lim2 = lim * lim;
        }

        boolean fits(int[] q, int height) {
            long fa = Math.max(Math.abs(q[0]), Math.abs(q[2])), fp = Math.max(Math.abs(q[1]), Math.abs(q[3]));
            double ea = fa + 0.5D, ep = fp + 0.5D, h = Math.max(0, height - 1), lim = r - 0.5D;
            return fa * fa + fp * fp <= lim2 && ea * ea + ep * ep + h * h <= lim * lim;
        }

        boolean free(int[] q) {
            for (int[] t : taken) {
                if (overlaps(q, t, 1)) {
                    return false;
                }
            }
            return true;
        }

        /**
         * A w (along a) x d (along p) lot as near (wa, wp) as it stands - free, fitting, its middle no nearer the core than `from`
         * (Chebyshev); null if nowhere.
         */
        int[] place(int wa, int wp, int w, int d, int height, int from) {
            for (int[] o : offsets()) {
                int ca = wa + o[0], cp = wp + o[1];
                if (Math.max(Math.abs(ca), Math.abs(cp)) < from) {
                    continue;
                }
                int[] q = {ca - w / 2, cp - d / 2, ca - w / 2 + w - 1, cp - d / 2 + d - 1};
                if (fits(q, height) && free(q)) {
                    taken.add(q);
                    return q;
                }
            }
            return null;
        }
    }

    /** Offsets within OFFSETS_REACH, nearest first (ties by angle - the same on every JVM). Built once. */
    private static int[][] offsets;
    private static final int OFFSETS_REACH = 96;

    private static synchronized int[][] offsets() {
        if (offsets == null) {
            int n = OFFSETS_REACH;
            List<int[]> l = new ArrayList<int[]>();
            for (int a = -n; a <= n; a++) {
                for (int p = -n; p <= n; p++) {
                    if (a * a + p * p <= n * n) {
                        l.add(new int[]{a, p, a * a + p * p, (int) Math.round(StrictMath.atan2(p, a) * 1000D)});
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
}
