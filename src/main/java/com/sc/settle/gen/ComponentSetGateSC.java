package com.sc.settle.gen;

import com.sc.block.BlockShowcaseSC;
import com.sc.dim.SubstrateBlocksSC;

import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE S (docs/plan-substrate-tech-f2.md §4.3 п. 6): the gate, «Санпропускник» - "SCSi14SetGate", GATE_W across
 * the street x GATE_D along it, as far out on the street as fits under the dome's shell. Two brick blocks on steel
 * posts with lead glass windows, the passage between them 3 wide and 3 high under a steel ceiling, lamps on both
 * faces; two decontamination showers (showcases) in the passage's floor - one walks over them. The guard post
 * (P_GATE) is just inside. Its frame faces out of the dome: u across, v from the outer face in.
 */
public class ComponentSetGateSC extends ComponentSettlementSC {

    public ComponentSetGateSC() {
    }

    public ComponentSetGateSC(int id, SettlementLayoutSC.Plan p, SettlementLayoutSC.Lot lot) {
        super(id, p.floorY, lot, -1, SettlementLayoutSC.GATE_H);
    }

    @Override
    protected void build(World w, StructureBoundingBox win) {
        int wd = width(), dp = depth(), mid = wd / 2;
        for (int u = 0; u < wd; u++) {
            for (int v = 0; v < dp; v++) {
                boolean side = Math.abs(u - mid) >= 2, post = (u == 0 || u == wd - 1) && (v == 0 || v == dp - 1);
                if (u == mid && (v == 1 || v == dp - 2)) {
                    showcase(w, win, u, 0, v, BlockShowcaseSC.SHOWER, 0, 0);
                } else {
                    set(w, win, u, 0, v, rock(), BRICK);
                }
                if (post) {
                    for (int dy = 1; dy <= 5; dy++) {
                        set(w, win, u, dy, v, steel());
                    }
                } else if (side) {
                    boolean window = (u == 0 || u == wd - 1) && v == dp / 2;
                    for (int dy = 1; dy <= 4; dy++) {
                        if (window && (dy == 2 || dy == 3)) {
                            set(w, win, u, dy, v, leadGlass(), 0);
                        } else {
                            set(w, win, u, dy, v, rock(), BRICK);
                        }
                    }
                    set(w, win, u, 5, v, steel());
                } else {
                    for (int dy = 1; dy <= 3; dy++) {
                        clear(w, win, u, dy, v);
                    }
                    boolean lamp = u != mid && (v == 0 || v == dp - 1);
                    if (lamp) {
                        set(w, win, u, 4, v, rock(), SubstrateBlocksSC.TRACE_NODE);
                    } else {
                        set(w, win, u, 4, v, steel());
                    }
                    set(w, win, u, 5, v, steel());
                }
            }
        }
    }
}
