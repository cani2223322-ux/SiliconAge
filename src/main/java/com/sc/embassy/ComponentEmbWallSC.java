package com.sc.embassy;

import com.sc.dim.SubstrateBlocksSC;
import com.sc.settle.gen.SettlementLayoutSC;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE S (docs/plan-substrate-tech-f6.md §6.2, rules ПЕ-7, ПГ-21): an embassy's wall and the ring road inside it - "SCOwEmbWall", one
 * piece whose box is the whole ring's (±(r - WALL_IN)) but which builds only two bands (a window walks only its own columns):
 * <ul>
 * <li>the wall (EmbassyQuartersSC.wallCell: outside the inner face r - WALL_IN - WALL_T, up to r - WALL_IN, not in a gate's or a
 * tower's lot - those pieces build there), WALL_H (8) high on a brick footing set in the floor: Settlement Brick, a turquoise band
 * (stained clay) on its outer skin, a steel band (the trim) under its top course, a lamp in the top course every LAMP_ARC blocks of
 * its length;</li>
 * <li>«обход» - the ring road (roadCell: ROAD_W wide, ROAD_GAP inside the wall; the streets cross it, it goes round the towers'
 * inner corners) the Sentries walk: Settlement Brick, a copper trace down its middle, lamp posts on its inner edge every
 * POST_ARC blocks.</li>
 * </ul>
 */
public class ComponentEmbWallSC extends ComponentEmbFrameSC {

    /** A lamp in the wall's top course every this many blocks along it; a lamp post by the road every this many. */
    public static final int LAMP_ARC = 8, POST_ARC = 16;
    /** The turquoise band's course; stained clay's cyan. */
    private static final int BAND_DY = 3, CYAN = 9;

    public ComponentEmbWallSC() {
    }

    public ComponentEmbWallSC(int id, EmbassyLayoutSC.Plan p, SettlementLayoutSC.Lot lot) {
        super(id, p, lot, -1, EmbassyQuartersSC.WALL_LOT_H);
    }

    @Override
    protected void build(World w, StructureBoundingBox win) {
        int[] q = clip(frameBox(), win);
        if (q == null) {
            return;
        }
        Block rock = rock();
        int top = EmbassyLayoutSC.WALL_H, in = EmbassyQuartersSC.innerFace(radius);
        long skin = radius - EmbassyLayoutSC.WALL_IN - EmbassyLayoutSC.WALL_T / 2;      // past it: the wall's outer skin
        long roadOut = in - EmbassyQuartersSC.ROAD_GAP, roadMid = roadOut - EmbassyQuartersSC.ROAD_W / 2 - 1,
                roadIn = roadOut - EmbassyQuartersSC.ROAD_W + 1;                            // the road's middle and inner course
        for (int a = q[0]; a <= q[2]; a++) {
            for (int p = q[1]; p <= q[3]; p++) {
                long d2 = (long) a * a + (long) p * p;
                if (EmbassyQuartersSC.wallCell(radius, a, p)) {
                    int arc = arc(a, p, radius - EmbassyLayoutSC.WALL_IN);
                    at(w, win, a, 0, p, rock, BRICK);
                    for (int dy = 1; dy <= top; dy++) {
                        if (dy == top - 1) {
                            at(w, win, a, dy, p, steel());
                        } else if (dy == top && arc % LAMP_ARC == 0) {
                            at(w, win, a, dy, p, rock, SubstrateBlocksSC.TRACE_NODE);
                        } else if (dy == BAND_DY && d2 > skin * skin) {
                            at(w, win, a, dy, p, Blocks.stained_hardened_clay, CYAN);
                        } else {
                            at(w, win, a, dy, p, rock, BRICK);
                        }
                    }
                } else if (EmbassyQuartersSC.roadCell(radius, a, p)) {
                    boolean middle = d2 > roadMid * roadMid && d2 <= (roadMid + 1) * (roadMid + 1);
                    at(w, win, a, 0, p, rock, middle ? SubstrateBlocksSC.COPPER_TRACE : BRICK);
                    if (d2 <= roadIn * roadIn && arc(a, p, (int) roadIn) % POST_ARC == 0) {
                        for (int dy = 1; dy <= 3; dy++) {                  // a lamp post on the inner edge
                            at(w, win, a, dy, p, steel());
                        }
                        at(w, win, a, 4, p, rock, SubstrateBlocksSC.TRACE_NODE);
                    }
                }
            }
        }
    }

    /** The block of length along a circle of radius `r` at (a, p) - 0 at the back, round to 2 pi r. */
    private static int arc(int a, int p, int r) {
        return (int) Math.floor((Math.atan2(p, a) + Math.PI) * r);
    }
}
