package com.sc.embassy;

import com.sc.block.BlockShowcaseSC;
import com.sc.dim.SubstrateBlocksSC;
import com.sc.settle.gen.SettlementLayoutSC;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE S (docs/plan-substrate-tech-f6.md §6.2, rule ПЕ-7): an embassy's gate in its wall - "SCOwEmbGate", the main one towards the
 * Vokzal (Lot.variant 0, on +a) or the back one (1, on -a): EmbassyQuartersSC.gateBox - the opening GATE_W (7) wide and 5 high through
 * the wall's band, a pylon each side (steel posts, Settlement Brick, lamps) up past the wall's top, the lintel over the opening
 * (a steel band with lamps on both faces, brick over it), two decontamination showers in the passage's floor (as a settlement's
 * «Санпропускник»: one walks over them), porches of brick inside and out. The Sentries' posts (P_GATE, P_BACK_GATE and the other
 * side) are just inside it, on the street.
 */
public class ComponentEmbGateSC extends ComponentEmbFrameSC {

    /** Stained clay's meta: cyan - the Tuners' turquoise. */
    private static final int CYAN = 9;

    public ComponentEmbGateSC() {
    }

    public ComponentEmbGateSC(int id, EmbassyLayoutSC.Plan p, SettlementLayoutSC.Lot lot) {
        super(id, p, lot, -1, EmbassyQuartersSC.GATE_H);
    }

    @Override
    protected void build(World w, StructureBoundingBox win) {
        Block rock = rock();
        int s = variant == 0 ? 1 : -1, ph = EmbassyQuartersSC.GATE_HALF, open = ph - 1, top = EmbassyLayoutSC.WALL_H;
        int depth = EmbassyQuartersSC.GATE_IN - EmbassyQuartersSC.GATE_OUT;     // ga 0 (the inner porch) .. depth (the outer one)
        for (int ga = 0; ga <= depth; ga++) {
            int a = s * (radius - EmbassyQuartersSC.GATE_IN + ga);
            boolean porch = ga == 0 || ga == depth, face = ga == 1 || ga == depth - 1;
            for (int p = -ph; p <= ph; p++) {
                int m = Math.abs(p);
                if (!porch && m == 0 && face) {
                    machine1(w, win, a, 0, p, BlockShowcaseSC.SHOWER, 0, 0);
                } else {
                    at(w, win, a, 0, p, rock, porch && m == 0 ? SubstrateBlocksSC.TRACE_NODE : BRICK);
                }
                if (porch) {
                    continue;
                }
                if (m == ph) {                                     // a pylon: steel at its faces, brick between, lamps on top
                    for (int dy = 1; dy <= top; dy++) {
                        if (face) {
                            at(w, win, a, dy, p, steel());
                        } else {
                            at(w, win, a, dy, p, rock, dy == top - 3 ? SubstrateBlocksSC.TRACE_NODE : BRICK);
                        }
                    }
                    at(w, win, a, top + 1, p, rock, face ? SubstrateBlocksSC.TRACE_NODE : BRICK);
                    continue;
                }
                for (int dy = 1; dy <= 5; dy++) {                  // the opening, 5 high
                    air(w, win, a, dy, p);
                }
                if (face && m == open - 1) {                       // the lintel: a lamp each side on both faces
                    at(w, win, a, 6, p, rock, SubstrateBlocksSC.TRACE_NODE);
                } else {
                    at(w, win, a, 6, p, steel());
                }
                for (int dy = 7; dy <= top; dy++) {
                    if (dy == top && m == 0 && !face) {
                        at(w, win, a, dy, p, Blocks.stained_hardened_clay, CYAN);     // the embassy's turquoise sign over the passage
                    } else {
                        at(w, win, a, dy, p, rock, BRICK);
                    }
                }
            }
        }
    }
}
