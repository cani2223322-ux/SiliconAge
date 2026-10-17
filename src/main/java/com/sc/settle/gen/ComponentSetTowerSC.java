package com.sc.settle.gen;

import com.sc.dim.SubstrateBlocksSC;

import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE S (docs/plan-substrate-tech-f2.md §4.3 п. 5): a watch tower - "SCSi14SetTower", TOWER x TOWER, the Senior
 * Wardens' post (P_TOWER0, P_TOWER1). A brick shaft on titanium posts with lead glass slits; inside, a spiral of quartz
 * stairs round a titanium pillar (SettlementLayoutSC.towerSteps: one walks up it without a jump, a mob's path finder
 * takes it block by block) up to a steel platform at floor + 9 - one stands at floor + TOWER_STAND - open over the
 * last stairs, iron bars round its edge and a lamp on each corner. The arch faces the settlement's middle.
 */
public class ComponentSetTowerSC extends ComponentSettlementSC {

    /** The platform's block over the settlement's floor. */
    public static final int PLATFORM = SettlementLayoutSC.TOWER_STAND - 1;
    /** Vanilla stairs' metas by the way they climb: NORTH, EAST, SOUTH, WEST (BlockStairs: 0 east, 1 west, 2 south, 3 north). */
    private static final int[] STAIR_META = {3, 0, 2, 1};
    /** Open platform over this many stairs before the last (one walks up under it). */
    private static final int OPEN = 3;

    public ComponentSetTowerSC() {
    }

    public ComponentSetTowerSC(int id, SettlementLayoutSC.Plan p, SettlementLayoutSC.Lot lot) {
        super(id, p.floorY, lot, -1, SettlementLayoutSC.TOWER_H);
    }

    @Override
    protected void build(World w, StructureBoundingBox win) {
        int n = width(), c = n / 2;
        int[][] steps = SettlementLayoutSC.towerSteps();
        int last = steps.length - 1;
        for (int u = 0; u < n; u++) {
            for (int v = 0; v < n; v++) {
                set(w, win, u, 0, v, rock(), BRICK);
                boolean edgeU = u == 0 || u == n - 1, edgeV = v == 0 || v == n - 1, corner = edgeU && edgeV;
                if (edgeU || edgeV) {
                    for (int dy = 1; dy < PLATFORM; dy++) {
                        boolean arch = v == 0 && u == c && dy <= 2;
                        boolean slit = !corner && (edgeU ? v : u) == c && (dy == 4 || dy == 7);
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
                    for (int dy = 1; dy < PLATFORM; dy++) {
                        set(w, win, u, dy, v, titanium());               // the pillar
                    }
                }
                boolean open = false;
                for (int i = last - OPEN; i <= last; i++) {
                    open |= steps[i][0] == u && steps[i][1] == v;     // over the last stairs (the last itself is one)
                }
                if (!open) {
                    set(w, win, u, PLATFORM, v, steel());
                }
                if (corner) {
                    set(w, win, u, PLATFORM + 1, v, rock(), SubstrateBlocksSC.TRACE_NODE);
                } else if (edgeU || edgeV) {
                    set(w, win, u, PLATFORM + 1, v, Blocks.iron_bars, 0);
                }
            }
        }
        // the spiral: stair i at floor + i + 1 on a brick column, climbing towards the next (the last onto the platform)
        for (int i = 0; i <= last; i++) {
            int du = i < last ? steps[i + 1][0] - steps[i][0] : 1, dv = i < last ? steps[i + 1][1] - steps[i][1] : 0;
            int d = SettlementLayoutSC.frameDir(facing, du, dv);
            for (int dy = i == last ? i + 1 : 1; dy <= i; dy++) {
                set(w, win, steps[i][0], dy, steps[i][1], rock(), BRICK);
            }
            set(w, win, steps[i][0], i + 1, steps[i][1], Blocks.quartz_stairs, STAIR_META[d < 0 ? 0 : d]);
        }
    }
}
