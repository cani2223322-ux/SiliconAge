package com.sc.embassy;

import com.sc.dim.SubstrateBlocksSC;
import com.sc.settle.gen.SettlementLayoutSC;

import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE S (docs/plan-substrate-tech-f6.md §6.2, rules ПЕ-7, ПГ-21): one of an embassy's four watch towers on its wall's diagonals -
 * "SCOwEmbTower", TOWER x TOWER (EmbassyQuartersSC.towerBox: the platform's cell P_TOWER one in from its outer corner, the arch on the
 * side towards the core), EmbassyLayoutSC.TOWER_H (12) high: the Senior Wardens' posts. A Settlement Brick shaft on titanium posts
 * with lead glass slits; inside, a spiral of quartz stairs round a titanium pillar (STEPS: one walks up it without a jump, a mob's
 * path finder takes it block by block; the two top stairs climb over the first two) up to a steel platform at
 * EmbassyQuartersSC.TOWER_PLATFORM (one stands at floor + TOWER_H - 1 - P_TOWER), open over the last stairs, iron bars round its edge
 * and a lamp on each corner - its top face TOWER_H over the floor, under the dome's shell for every radius (EmbassyQuartersSC.problem).
 */
public class ComponentEmbTowerSC extends ComponentEmbassySC {

    /**
     * The spiral in the lot's frame (the arch at (2, 0)): stair i stands i + 1 over the floor and climbs towards stair i + 1; the last,
     * on the platform's level, onto the platform (+v).
     */
    public static final int[][] STEPS = {{2, 1}, {3, 1}, {3, 2}, {3, 3}, {2, 3}, {1, 3}, {1, 2}, {1, 1}, {2, 1}, {3, 1}};
    /** Vanilla stairs' metas by the way they climb: NORTH, EAST, SOUTH, WEST (BlockStairs: 0 east, 1 west, 2 south, 3 north). */
    private static final int[] STAIR_META = {3, 0, 2, 1};
    /** The stairs from here on stand over the spiral's first ones: no brick column under them (the first ones' headroom). */
    private static final int OVER = 8;
    /** The platform is open over the stairs from this one on (one walks up under it). */
    private static final int OPEN_FROM = 7;

    public ComponentEmbTowerSC() {
    }

    public ComponentEmbTowerSC(int id, EmbassyLayoutSC.Plan p, SettlementLayoutSC.Lot lot) {
        super(id, p.floorY, lot, -1, EmbassyLayoutSC.TOWER_H);
    }

    @Override
    protected void build(World w, StructureBoundingBox win) {
        int n = width(), c = n / 2, plat = EmbassyQuartersSC.TOWER_PLATFORM, last = STEPS.length - 1;
        for (int u = 0; u < n; u++) {
            for (int v = 0; v < n; v++) {
                set(w, win, u, 0, v, rock(), BRICK);
                boolean edgeU = u == 0 || u == n - 1, edgeV = v == 0 || v == n - 1, corner = edgeU && edgeV;
                int step = stepAt(u, v);
                if (edgeU || edgeV) {
                    for (int dy = 1; dy < plat; dy++) {
                        boolean arch = v == 0 && u == c && dy <= 2;
                        boolean slit = !corner && (edgeU ? v : u) == c && (dy == 4 || dy == 8);
                        if (arch) {
                            clear(w, win, u, dy, v);
                        } else if (corner) {
                            set(w, win, u, dy, v, titanium());
                        } else if (slit) {
                            set(w, win, u, dy, v, leadGlass(), 0);
                        } else {
                            set(w, win, u, dy, v, rock(), BRICK);
                        }
                    }
                } else if (u == c && v == c) {
                    for (int dy = 1; dy < plat; dy++) {
                        set(w, win, u, dy, v, titanium());               // the pillar
                    }
                } else if (step >= 0) {                                  // a stair's cell: its column, the stairs, air between
                    int upper = upperStep(u, v);
                    for (int dy = 1; dy < plat; dy++) {
                        if (dy == step + 1 || upper >= 0 && dy == upper + 1) {
                            set(w, win, u, dy, v, Blocks.quartz_stairs, STAIR_META[climb(dy == step + 1 ? step : upper)]);
                        } else if (dy <= step) {
                            set(w, win, u, dy, v, rock(), BRICK);          // the column under the lower stair
                        } else {
                            clear(w, win, u, dy, v);                      // the headroom
                        }
                    }
                }
                // the platform (the last stair is on its level), the parapet, the lamps
                int top = upperStep(u, v) == last ? last : -1;
                if (top >= 0) {
                    set(w, win, u, plat, v, Blocks.quartz_stairs, STAIR_META[climb(last)]);
                } else if (!openOver(u, v)) {
                    set(w, win, u, plat, v, steel());
                } else {
                    clear(w, win, u, plat, v);
                }
                if (corner) {
                    set(w, win, u, plat + 1, v, rock(), SubstrateBlocksSC.TRACE_NODE);
                } else if (edgeU || edgeV) {
                    set(w, win, u, plat + 1, v, Blocks.iron_bars, 0);
                }
            }
        }
    }

    /** The spiral's lower stair on (u, v) (the first of its cell), or -1. */
    private static int stepAt(int u, int v) {
        for (int i = 0; i < STEPS.length; i++) {
            if (STEPS[i][0] == u && STEPS[i][1] == v) {
                return i;
            }
        }
        return -1;
    }

    /** The spiral's upper stair on (u, v) - one over another (from OVER on), or -1. */
    private static int upperStep(int u, int v) {
        for (int i = STEPS.length - 1; i >= OVER; i--) {
            if (STEPS[i][0] == u && STEPS[i][1] == v) {
                return i;
            }
        }
        return -1;
    }

    /** The platform is open over (u, v): the stairs from OPEN_FROM on below it (the very last is on its level). */
    private static boolean openOver(int u, int v) {
        for (int i = OPEN_FROM; i < STEPS.length; i++) {
            if (STEPS[i][0] == u && STEPS[i][1] == v) {
                return true;
            }
        }
        return false;
    }

    /** The world way (NORTH..WEST) stair i climbs: towards the next one; the last onto the platform (+v). */
    private int climb(int i) {
        int du = i < STEPS.length - 1 ? STEPS[i + 1][0] - STEPS[i][0] : 0, dv = i < STEPS.length - 1 ? STEPS[i + 1][1] - STEPS[i][1] : 1;
        int d = SettlementLayoutSC.frameDir(facing, du, dv);
        return d < 0 ? 0 : d;
    }
}
