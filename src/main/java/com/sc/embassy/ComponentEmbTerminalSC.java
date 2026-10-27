package com.sc.embassy;

import com.sc.block.BlockSettlementDecorSC;
import com.sc.block.BlockShowcase2SC;
import com.sc.settle.SettlementBlocksSC;
import com.sc.settle.gen.SettlementLayoutSC;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE P (docs/plan-embassy-airfield-core.md §4.3): "SCOwEmbTerminal" - the «Аэровокзал» 17 x 9, 7 high (Field.terminal: open through - the Vokzal's platform on one side, the apron on the other; benches
 * of slabs, the flights' board as decor) and the «Вышка КДП» 7 x 7, 20 high (Field.tower: a stair inside, the glazed cab on top, the
 * desk BlockShowcase2SC.TOWER_CONSOLE at exactly Field.towerDesk(), the «Светомаяк» AIRFIELD_BEACON at exactly Field.beaconBlock() on
 * the roof). Nothing over H_TERMINAL / BEACON_DY (the 1:4 transitional surface - EmbassyAirfieldSC.problem).
 * <p>
 * Fixed (the coordinator's): the class, its name, the constructors, the box's height (dy -1 .. BEACON_DY + 1). The body of buildField is package
 * P's (the stub builds nothing): only the window, ComponentEmbAirSC's helpers (put - flag 2, light by hand), idempotent; vanilla's
 * blocks, the trim and the mod's airfield blocks only - nothing of value, not one log or plank.
 * <p>
 * P's: the hall - a quartz floor (the trim's), chiseled guides across it between its two doors (3 wide, 3 high, in the middle of both
 * long sides: the Vokzal's platform - the apron, where the apron's fence has its gate) and along to the tower's door; long sides of
 * lead glass over a stone brick sill between steel mullions, stone brick ends, titanium corners, a steel band and a flat steel roof
 * (H_TERMINAL) with three lit nodes; slab benches either side of the way through; the flights' board - three Dispatcher's Desks set in
 * the far end wall, their screens into the hall. The tower - a stone brick shaft on titanium corners with lead glass slits, its door
 * towards the hall's (one stone brick walk between them, and beside the tower); inside, a stair of stone brick stairs round a lit
 * stone brick core, one lap of 16 (STEPS, then the landing's own stair on the cab's floor - one walks up without a jump, the two
 * stairs under the floor have their headroom cut out of it); the cab - a steel floor at TOWER_DESK_DY - 1, lead glass all round
 * between titanium corners, the desk in its middle looking at the runway (its screens towards the controller's side), a steel roof
 * with lit nodes on its corners (H_TOWER), the beacon on it.
 */
public class ComponentEmbTerminalSC extends ComponentEmbAirSC {

    /** The hall's doors: half their width, their height; the board's desks (v, over the hall's floor) in the far end wall. */
    private static final int DOOR_HALF = 1, DOOR_H = 3, BOARD_DY = 3;
    /** The tower's stair, one lap of the ring round the core (u along a, v along q from the tower's corner): stair i is i + 1 over the floor. */
    private static final int[][] STEPS = {{1, 4}, {1, 5}, {2, 5}, {3, 5}, {4, 5}, {5, 5}, {5, 4}, {5, 3}, {5, 2}, {5, 1}, {4, 1}, {3, 1},
            {2, 1}, {1, 1}, {1, 2}};
    /** The landing inside the door: open up the shaft; its stair is the last, on the cab's floor (climbing onto it towards STEPS[0]). */
    private static final int[] LANDING = {1, 3};
    /** The tower's door (u, v) - towards the hall's tower door. */
    private static final int[] DOOR = {0, 3};
    /** The core's lamps {u, v, dy} (lit nodes up the stairwell). */
    private static final int[][] LAMPS = {{2, 3, 3}, {3, 4, 7}, {4, 3, 11}, {3, 2, 15}};

    public ComponentEmbTerminalSC() {
    }

    public ComponentEmbTerminalSC(int id, EmbassyLayoutSC.Plan p, SettlementLayoutSC.Lot lot) {
        super(id, p, lot, -1, EmbassyAirfieldSC.BEACON_DY + 1);
    }

    @Override
    protected void buildField(World w, StructureBoundingBox win, EmbassyAirfieldSC.Field f) {
        StructureBoundingBox in = ComponentEmbAirKitSC.inside(win, boundingBox);
        if (in == null || f.terminal == null || f.tower == null || !trimReady()) {
            return;
        }
        int[] c = clipq(EmbassyAirfieldSC.rect(f.terminal[0], f.terminal[1], f.tower[2], f.terminal[3]), in);
        if (c != null) {                                        // the walk between the hall and the tower, and beside the tower
            for (int a = c[0]; a <= c[2]; a++) {
                for (int q = c[1]; q <= c[3]; q++) {
                    if (!EmbassyAirfieldSC.in(f.terminal, a, q) && !EmbassyAirfieldSC.in(f.tower, a, q)) {
                        atq(w, in, a, 0, q, Blocks.stonebrick, 0);
                    }
                }
            }
        }
        hall(w, in, f);
        tower(w, in, f);
    }

    // ------------------------------------------------------------------ «Аэровокзал»

    private void hall(World w, StructureBoundingBox in, EmbassyAirfieldSC.Field f) {
        int[] t = f.terminal;
        int[] c = clipq(t, in);
        if (c == null) {
            return;
        }
        Block decor = SettlementBlocksSC.decor;
        int len = t[2] - t[0], wid = t[3] - t[1], mid = len / 2, towerV = (f.tower[1] + f.tower[3]) / 2 - t[1];
        int top = EmbassyAirfieldSC.H_TERMINAL;
        for (int a = c[0]; a <= c[2]; a++) {
            for (int q = c[1]; q <= c[3]; q++) {
                int u = a - t[0], v = q - t[1];
                boolean endU = u == 0 || u == len, endV = v == 0 || v == wid;
                boolean guide = u == mid || v == towerV && u >= mid;
                atq(w, in, a, 0, q, decor, guide ? BlockSettlementDecorSC.QUARTZ_CHISELED : BlockSettlementDecorSC.QUARTZ);
                for (int dy = 1; dy < top; dy++) {
                    if (endU && endV) {
                        atq(w, in, a, dy, q, decor, BlockSettlementDecorSC.TITANIUM);
                    } else if (endV) {                          // a long side: the doors, mullions, glass over a sill
                        if (Math.abs(u - mid) <= DOOR_HALF && dy <= DOOR_H) {
                            airq(w, in, a, dy, q);
                        } else if (u == 4 || u == len - 4 || dy == top - 1) {
                            atq(w, in, a, dy, q, decor, BlockSettlementDecorSC.STEEL);
                        } else if (dy == 1) {
                            atq(w, in, a, dy, q, Blocks.stonebrick, 0);
                        } else {
                            atq(w, in, a, dy, q, SettlementBlocksSC.glass, 0);
                        }
                    } else if (endU) {                          // an end: the tower's door / the flights' board
                        if (u == len && v == towerV && dy <= 2) {
                            airq(w, in, a, dy, q);
                        } else if (u == 0 && dy == BOARD_DY && Math.abs(v - wid / 2) <= 1) {
                            machineq(w, in, a, dy, q, BlockShowcase2SC.DISPATCH_CONSOLE, 1, 0);
                        } else if (u == len && dy >= 3 && dy <= 4 && v >= wid - 3 && v <= wid - 2) {
                            atq(w, in, a, dy, q, SettlementBlocksSC.glass, 0);
                        } else if (dy == top - 1) {
                            atq(w, in, a, dy, q, decor, BlockSettlementDecorSC.STEEL);
                        } else {
                            atq(w, in, a, dy, q, Blocks.stonebrick, 0);
                        }
                    } else if (dy == 1 && (v == 2 || v == wid - 2) && (u >= 2 && u <= 5 || u >= len - 5 && u <= len - 2)) {
                        atq(w, in, a, dy, q, Blocks.stone_slab, 0);    // the benches either side of the way through
                    } else {
                        airq(w, in, a, dy, q);
                    }
                }
                boolean lamp = v == wid / 2 && (u == 3 || u == mid || u == len - 3);
                atq(w, in, a, top, q, decor, lamp ? BlockSettlementDecorSC.NODE : BlockSettlementDecorSC.STEEL);
            }
        }
    }

    // ------------------------------------------------------------------ «Вышка КДП»

    private void tower(World w, StructureBoundingBox in, EmbassyAirfieldSC.Field f) {
        int[] t = f.tower;
        int[] c = clipq(t, in);
        if (c == null) {
            return;
        }
        Block decor = SettlementBlocksSC.decor;
        int n = t[2] - t[0], half = n / 2, floor = EmbassyAirfieldSC.TOWER_DESK_DY - 1, roof = EmbassyAirfieldSC.H_TOWER;
        for (int a = c[0]; a <= c[2]; a++) {
            for (int q = c[1]; q <= c[3]; q++) {
                int u = a - t[0], v = q - t[1];
                boolean edgeU = u == 0 || u == n, edgeV = v == 0 || v == n, corner = edgeU && edgeV, edge = edgeU || edgeV;
                boolean core = !edge && u >= 2 && u <= n - 2 && v >= 2 && v <= n - 2;
                int step = step(u, v);
                atq(w, in, a, 0, q, Blocks.stonebrick, 0);
                for (int dy = 1; dy < floor; dy++) {           // the shaft
                    if (edge) {
                        if (u == DOOR[0] && v == DOOR[1] && dy <= 2) {
                            airq(w, in, a, dy, q);
                        } else if (corner) {
                            atq(w, in, a, dy, q, decor, BlockSettlementDecorSC.TITANIUM);
                        } else if ((edgeU ? v : u) == half && dy % 4 == 1 && dy > 1) {
                            atq(w, in, a, dy, q, SettlementBlocksSC.glass, 0);
                        } else {
                            atq(w, in, a, dy, q, Blocks.stonebrick, 0);
                        }
                    } else if (core) {
                        atq(w, in, a, dy, q, lamp(u, v, dy) ? decor : Blocks.stonebrick, lamp(u, v, dy) ? BlockSettlementDecorSC.NODE : 0);
                    } else if (step >= 0 && dy == step + 1) {
                        atq(w, in, a, dy, q, Blocks.stone_brick_stairs, ComponentEmbAirKitSC.stair(climb(step)));
                    } else if (step >= 0 && step < STEPS.length && dy <= step) {
                        atq(w, in, a, dy, q, Blocks.stonebrick, 0);     // the column under a stair
                    } else {
                        airq(w, in, a, dy, q);                          // the headroom; the landing's shaft
                    }
                }
                // the cab's floor: the landing's stair on its level, the last two stairs' headroom cut out of it
                if (step == STEPS.length) {
                    atq(w, in, a, floor, q, Blocks.stone_brick_stairs, ComponentEmbAirKitSC.stair(climb(step)));
                } else if (step >= STEPS.length - 2) {
                    airq(w, in, a, floor, q);
                } else {
                    atq(w, in, a, floor, q, decor, BlockSettlementDecorSC.STEEL);
                }
                for (int dy = floor + 1; dy < roof; dy++) {    // the glazed cab, the desk in its middle
                    if (corner) {
                        atq(w, in, a, dy, q, decor, BlockSettlementDecorSC.TITANIUM);
                    } else if (edge) {
                        atq(w, in, a, dy, q, SettlementBlocksSC.glass, 0);
                    } else if (u == half && v == half && dy == EmbassyAirfieldSC.TOWER_DESK_DY) {
                        machineq(w, in, a, dy, q, BlockShowcase2SC.TOWER_CONSOLE, 0, -1);
                    } else {
                        airq(w, in, a, dy, q);
                    }
                }
                atq(w, in, a, roof, q, decor, corner ? BlockSettlementDecorSC.NODE : BlockSettlementDecorSC.STEEL);
                if (u == half && v == half) {
                    machineq(w, in, a, EmbassyAirfieldSC.BEACON_DY, q, BlockShowcase2SC.AIRFIELD_BEACON, 0, 0);
                }
            }
        }
    }

    /** The stair on (u, v): its index in STEPS, STEPS.length - the landing's (on the cab's floor), -1 none. */
    private static int step(int u, int v) {
        for (int i = 0; i < STEPS.length; i++) {
            if (STEPS[i][0] == u && STEPS[i][1] == v) {
                return i;
            }
        }
        return u == LANDING[0] && v == LANDING[1] ? STEPS.length : -1;
    }

    /** The world way (NORTH..WEST) stair `i` climbs: towards the next one - the last of STEPS towards the landing, the landing's onto the floor. */
    private int climb(int i) {
        int[] from = i < STEPS.length ? STEPS[i] : LANDING, to = i < STEPS.length - 1 ? STEPS[i + 1] : i == STEPS.length - 1 ? LANDING : STEPS[0];
        int d = dirOfq(to[0] - from[0], to[1] - from[1]);
        return d < 0 ? 0 : d;
    }

    /** A lamp of the core at (u, v, dy). */
    private static boolean lamp(int u, int v, int dy) {
        for (int[] l : LAMPS) {
            if (l[0] == u && l[1] == v && l[2] == dy) {
                return true;
            }
        }
        return false;
    }
}
