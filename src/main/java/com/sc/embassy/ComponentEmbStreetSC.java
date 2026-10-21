package com.sc.embassy;

import com.sc.dim.SubstrateBlocksSC;
import com.sc.settle.gen.SettlementLayoutSC;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE S (docs/plan-substrate-tech-f6.md §6.2, rule ПЕ-7): a street of an embassy - "SCOwEmbStreet", EmbassyQuartersSC.STREET_HALF
 * each side of its axis (the main street from the guard square to the main gate, the back street's two pieces either side of the
 * depots, the street on from the main gate to the Vokzal). Under the dome (the protected cylinder): Settlement Brick, a copper trace
 * down the middle, nodes along the edges every LAMP_EVERY blocks, lamp posts (steel, a node on top) at the sides every POST_EVERY;
 * outside it - nothing of value lies where nobody protects it, no copper (a trace drops Crushed Copper Ore): plain stone bricks, the
 * axis chiselled. The «Достройка» (EmbassyRepairSC.repairing) puts those back only in the Vokzal's dock zone (protected), as the
 * ground piece does: elsewhere outside the dome a player's block stays (ПС-6).
 */
public class ComponentEmbStreetSC extends ComponentEmbFrameSC {

    /** A node on each edge every this many blocks; a lamp post each side every this many. */
    public static final int LAMP_EVERY = 6, POST_EVERY = 12;

    public ComponentEmbStreetSC() {
    }

    public ComponentEmbStreetSC(int id, EmbassyLayoutSC.Plan p, SettlementLayoutSC.Lot lot) {
        super(id, p, lot, -1, EmbassyQuartersSC.STREET_H);
    }

    @Override
    protected void build(World w, StructureBoundingBox win) {
        int[] q = clip(frameBox(), win);
        if (q == null) {
            return;
        }
        Block rock = rock();
        boolean repair = EmbassyRepairSC.repairing();
        int edge = EmbassyQuartersSC.STREET_HALF;
        for (int a = q[0]; a <= q[2]; a++) {
            for (int p = q[1]; p <= q[3]; p++) {
                int m = Math.abs(p);
                if (!domed(a, p)) {                                // outside the dome: stone bricks, the axis chiselled
                    if (!repair || inDockZone(a, p)) {             // ПС-6: the «Достройка» puts back the protected cells only
                        at(w, win, a, 0, p, Blocks.stonebrick, p == 0 ? 3 : 0);
                    }
                    continue;
                }
                if (m < edge) {
                    int meta = p == 0 ? SubstrateBlocksSC.COPPER_TRACE : m == edge - 1 && a % LAMP_EVERY == 0 ? SubstrateBlocksSC.TRACE_NODE : BRICK;
                    at(w, win, a, 0, p, rock, meta);
                } else {
                    at(w, win, a, 0, p, rock, BRICK);
                    if (a % POST_EVERY == 0 && domed(a, p + (p > 0 ? 1 : -1))) {    // a lamp post, well inside the dome
                        for (int dy = 1; dy <= 3; dy++) {
                            at(w, win, a, dy, p, steel());
                        }
                        at(w, win, a, 4, p, rock, SubstrateBlocksSC.TRACE_NODE);
                    }
                }
            }
        }
    }

    /**
     * (a, p) is in the Vokzal's dock zone (EmbassyLayoutSC.DOCK_ZONE across from the beacon at (radius + VOKZAL_OUT, 0) - protected,
     * SettlementRegistrySC.Settlement.inDockZone; the frame turns, it doesn't stretch: the same distance as the ground piece's).
     */
    private boolean inDockZone(int a, int p) {
        long ba = a - (radius + EmbassyLayoutSC.VOKZAL_OUT), zone = EmbassyLayoutSC.DOCK_ZONE;
        return ba * ba + (long) p * p <= zone * zone;
    }
}
