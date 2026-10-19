package com.sc.embassy;

import com.sc.block.BlockShowcase2SC;
import com.sc.dim.SubstrateBlocksSC;
import com.sc.settle.gen.SettlementLayoutSC;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE S (docs/plan-substrate-tech-f6.md §6.1, §6.2, rules ПЕ-7, ПЖ-12): «Склады снабжения» by the back gate - "SCOwEmbDepot", the
 * yard round P_DEPOT (EmbassyQuartersSC.DEPOT_HALF_A x DEPOT_HALF_P each side) the back street runs through: the Quartermasters'
 * (AT_QUARTERMASTER - the yard and the sheds' open fronts). Two open sheds facing the street, one each side: steel posts, Settlement
 * Brick walls with lead glass windows, a steel roof with skylights and lamps; along their back walls racks and crates two high
 * (BlockShowcase2SC RACK, CRATE - the warehouse's own), crates by the side walls, a pile of sacks (wool) in the middle - what the
 * expeditions are fitted out from. No logs, no planks, nothing to carry away.
 */
public class ComponentEmbDepotSC extends ComponentEmbFrameSC {

    /** Sacks: brown and white wool. */
    private static final int SACK = 12, SACK_LIGHT = 0;

    public ComponentEmbDepotSC() {
    }

    public ComponentEmbDepotSC(int id, EmbassyLayoutSC.Plan p, SettlementLayoutSC.Lot lot) {
        super(id, p, lot, -1, EmbassyQuartersSC.DEPOT_H);
    }

    @Override
    protected void build(World w, StructureBoundingBox win) {
        Block rock = rock();
        int[] box = frameBox();
        int mid = (box[0] + box[2]) / 2, ha = EmbassyQuartersSC.DEPOT_HALF_A, hp = EmbassyQuartersSC.DEPOT_HALF_P;
        int front = EmbassyQuartersSC.STREET_HALF + 1;            // the sheds' open fronts
        for (int ca = -ha; ca <= ha; ca++) {
            int a = mid + ca;
            for (int p = -hp; p <= hp; p++) {
                int m = Math.abs(p), s = p > 0 ? 1 : -1;
                // the floor: the street through the yard (a copper trace on its axis, a node each side), brick round it
                int meta = p == 0 ? SubstrateBlocksSC.COPPER_TRACE : m == 2 && ca == 0 ? SubstrateBlocksSC.TRACE_NODE : BRICK;
                at(w, win, a, 0, p, rock, meta);
                if (m < front) {
                    continue;
                }
                boolean side = Math.abs(ca) == ha, backWall = m == hp, post = side && (m == front || backWall);
                if (post) {
                    for (int dy = 1; dy <= 4; dy++) {
                        at(w, win, a, dy, p, steel());
                    }
                } else if (backWall || side && m > front) {               // the walls: brick, windows
                    for (int dy = 1; dy <= 4; dy++) {
                        boolean window = (dy == 2 || dy == 3) && (backWall ? ca % 3 == 0 : m == (front + hp) / 2);
                        if (window) {
                            at(w, win, a, dy, p, leadGlass(), 0);
                        } else {
                            at(w, win, a, dy, p, rock, BRICK);
                        }
                    }
                }
                // the roof: steel, skylights over the aisle, lamps
                boolean sky = m == front + 3 && Math.abs(ca) <= 1, lamp = m == front + 2 && Math.abs(ca) == 3;
                if (sky) {
                    at(w, win, a, 5, p, leadGlass(), 0);
                } else if (lamp) {
                    at(w, win, a, 5, p, rock, SubstrateBlocksSC.TRACE_NODE);
                } else {
                    at(w, win, a, 5, p, steel());
                }
                // the stock
                if (m == hp - 1 && !side) {                               // along the back: racks and crates, two high
                    int code = (ca & 1) == 0 ? BlockShowcase2SC.RACK : BlockShowcase2SC.CRATE;
                    machine2(w, win, a, 1, p, code, 0, -s);
                    machine2(w, win, a, 2, p, code, 0, -s);
                } else if (Math.abs(ca) == ha - 1 && (m == front + 2 || m == front + 4)) {    // crates by the side walls
                    machine2(w, win, a, 1, p, BlockShowcase2SC.CRATE, 0, -s);
                } else if (m == front + 3 && Math.abs(ca) <= 1) {        // the pile of sacks
                    at(w, win, a, 1, p, Blocks.wool, SACK);
                    if (ca == 0) {
                        at(w, win, a, 2, p, Blocks.wool, SACK_LIGHT);
                    }
                }
            }
        }
    }
}
