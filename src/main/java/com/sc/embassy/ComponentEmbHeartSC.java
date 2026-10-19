package com.sc.embassy;

import com.sc.block.BlockShowcaseSC;
import com.sc.dim.SubstrateBlocksSC;
import com.sc.settle.SettlementBlocksSC;
import com.sc.settle.gen.SettlementLayoutSC;
import com.sc.tileentity.TileEntitySettlementCoreSC;
import com.sc.util.Material;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE S (docs/plan-substrate-tech-f6.md §6.2, rule ПЕ-7): «Сердце» and the guard square of an embassy - "SCOwEmbHeart", the
 * 21 x 21 round the core (EmbassyLayoutSC.SQUARE_HALF), in the embassy's frame:
 * <ul>
 * <li>the square: Settlement Brick, a copper trace round its edge with glowing nodes at the corners and the middles, eight traces
 * out from the cap to the edge (a circuit board seen from above);</li>
 * <li>the Heart: the core (floor + 1) on a tungsten pedestal set in the floor, under a 7 x 7 cap of lead glass on steel posts
 * (CAP_HALF), Tokamak Coils round it - the trim's, unbreakable; the core gets its embassy (TileEntitySettlementCoreSC.setup: id,
 * radius, floor) only when its tile isn't that embassy's already - the «Достройка» never sets it up twice;</li>
 * <li>the Clone Capsule (two showcases one over the other, a titanium cap, steel posts with lamps) behind P_CLONE, facing it;</li>
 * <li>«Зал посла» behind P_HALL - the Ambassador's dais of quartz under a titanium canopy on steel posts with a lead glass skylight,
 * a turquoise runner (wool carpet) to where he stands, the Tuners' turquoise banner (wool) between lead glass panes behind him;</li>
 * <li>the armour station behind P_SQUARE facing the Heart (the Captain stands before it), its lamp posts; the four charge pads set in
 * the floor at P_PAD0..3; the plaza P_PLAZA open before the Heart (the Commandant).</li>
 * </ul>
 * Not one log or plank; the metal, the coils and the lead glass are the trim (ComponentSettlementSC's palette).
 */
public class ComponentEmbHeartSC extends ComponentEmbFrameSC {

    private static final int H = EmbassyLayoutSC.SQUARE_HALF, CAP = EmbassyLayoutSC.CAP_HALF;
    /** The capsule behind P_CLONE, the armour station behind P_SQUARE (the core's fixed points, one step further out). */
    private static final int[] CAPSULE = {EmbassyLayoutSC.AT_CLONE[0], EmbassyLayoutSC.AT_CLONE[1] + 1},
            STATION = {EmbassyLayoutSC.AT_SQUARE[0] - 1, EmbassyLayoutSC.AT_SQUARE[1]};
    /** The Ambassador's hall {a0, p0, a1, p1}: P_HALL at its front's middle, its back wall at p0. */
    private static final int[] HALL = {-3, EmbassyLayoutSC.AT_HALL[1] - 3, 3, EmbassyLayoutSC.AT_HALL[1] + 1};
    /** Wool / carpet meta: cyan - the Tuners' turquoise. */
    private static final int CYAN = 9;

    public ComponentEmbHeartSC() {
    }

    public ComponentEmbHeartSC(int id, EmbassyLayoutSC.Plan p, SettlementLayoutSC.Lot lot) {
        super(id, p, lot, -1, EmbassyQuartersSC.HEART_H);
    }

    @Override
    protected void build(World w, StructureBoundingBox win) {
        Block rock = rock();
        // the square's floor; the hall's dais
        for (int a = -H; a <= H; a++) {
            for (int p = -H; p <= H; p++) {
                if (a == 0 && p == 0 || pad(a, p)) {
                    continue;                                       // the pedestal's and the pads' own cells (set once a pass)
                }
                if (EmbassyQuartersSC.inside(HALL, a, p)) {
                    at(w, win, a, 0, p, Blocks.quartz_block, 0);
                    continue;
                }
                int meta = BRICK, m = Math.max(Math.abs(a), Math.abs(p));
                if (m == H) {
                    meta = a == 0 || p == 0 || Math.abs(a) == Math.abs(p) ? SubstrateBlocksSC.TRACE_NODE : SubstrateBlocksSC.COPPER_TRACE;
                } else if ((a == 0 || p == 0 || Math.abs(a) == Math.abs(p)) && m > CAP + 1) {
                    meta = SubstrateBlocksSC.COPPER_TRACE;          // the traces from the cap out to the edge
                } else if (Math.abs(a) == 1 && Math.abs(p) == 1) {
                    meta = SubstrateBlocksSC.TRACE_NODE;            // the lights in the cap's floor
                }
                at(w, win, a, 0, p, rock, meta);
            }
        }
        // the pads in the floor
        for (int i = 0; i < EmbassyLayoutSC.PADS; i++) {
            int[] q = EmbassyLayoutSC.PAD_AT[i];
            machine1(w, win, q[0], 0, q[1], BlockShowcaseSC.CHARGE_PAD, 1, 0);
        }
        // the cap: lead glass on steel posts, a steel rim round its glass roof
        for (int a = -CAP; a <= CAP; a++) {
            for (int p = -CAP; p <= CAP; p++) {
                boolean wall = Math.abs(a) == CAP || Math.abs(p) == CAP, post = Math.abs(a) == CAP && Math.abs(p) == CAP;
                if (post) {
                    for (int dy = 1; dy <= 5; dy++) {
                        at(w, win, a, dy, p, steel());
                    }
                } else if (wall) {
                    for (int dy = 1; dy <= 4; dy++) {
                        at(w, win, a, dy, p, leadGlass(), 0);
                    }
                    at(w, win, a, 5, p, steel());
                } else {
                    at(w, win, a, 5, p, leadGlass(), 0);
                }
            }
        }
        // the coils round the pedestal
        for (int a = -2; a <= 2; a += 2) {
            for (int p = -2; p <= 2; p += 2) {
                if (a != 0 || p != 0) {
                    at(w, win, a, 1, p, coil());
                    if (a != 0 && p != 0) {
                        at(w, win, a, 2, p, coil());
                    }
                }
            }
        }
        // the pedestal (set in the floor) and the core on it (floor + 1): its embassy - once
        at(w, win, 0, 0, 0, metal(Material.TUNGSTEN));
        if (SettlementBlocksSC.core != null && at(w, win, 0, 1, 0, SettlementBlocksSC.core, 0)) {
            TileEntity te = w.getTileEntity(fx(0, 0), floorY + 1, fz(0, 0));
            if (te instanceof TileEntitySettlementCoreSC && ((TileEntitySettlementCoreSC) te).settlementId() != settlementId) {
                ((TileEntitySettlementCoreSC) te).setup(settlementId, radius, floorY);
            }
        }
        // the Clone Capsule, facing P_CLONE (towards the core)
        machine1(w, win, CAPSULE[0], 1, CAPSULE[1], BlockShowcaseSC.CLONE_CAPSULE, 0, -1);
        machine1(w, win, CAPSULE[0], 2, CAPSULE[1], BlockShowcaseSC.CLONE_CAPSULE, 0, -1);
        at(w, win, CAPSULE[0], 3, CAPSULE[1], titanium());
        for (int s = -1; s <= 1; s += 2) {
            at(w, win, CAPSULE[0] + s, 1, CAPSULE[1], steel());
            at(w, win, CAPSULE[0] + s, 2, CAPSULE[1], steel());
            at(w, win, CAPSULE[0] + s, 3, CAPSULE[1], rock, SubstrateBlocksSC.TRACE_NODE);
        }
        // the armour station, facing the Heart, its lamp posts
        machine1(w, win, STATION[0], 1, STATION[1], BlockShowcaseSC.ARMOUR_STATION, 1, 0);
        for (int s = -1; s <= 1; s += 2) {
            at(w, win, STATION[0], 1, STATION[1] + s, steel());
            at(w, win, STATION[0], 2, STATION[1] + s, rock, SubstrateBlocksSC.TRACE_NODE);
        }
        hall(w, win, rock);
    }

    /** (a, p) holds a charge pad. */
    private static boolean pad(int a, int p) {
        for (int i = 0; i < EmbassyLayoutSC.PADS; i++) {
            if (EmbassyLayoutSC.PAD_AT[i][0] == a && EmbassyLayoutSC.PAD_AT[i][1] == p) {
                return true;
            }
        }
        return false;
    }

    /** «Зал посла»: the dais (the floor's pass), the runner, the banner wall behind, the posts and the canopy. */
    private void hall(World w, StructureBoundingBox win, Block rock) {
        int a0 = HALL[0], a1 = HALL[2], back = HALL[1], front = HALL[3];
        for (int p = back + 1; p <= front; p++) {
            at(w, win, 0, 1, p, Blocks.carpet, CYAN);              // the runner up to where the Ambassador stands and past him
        }
        for (int a = a0; a <= a1; a++) {
            int m = Math.abs(a);
            for (int dy = 1; dy <= 4; dy++) {                      // the back wall
                if (m == 3) {
                    at(w, win, a, dy, back, steel());
                } else if (m == 2) {
                    at(w, win, a, dy, back, leadGlass(), 0);
                } else if (dy == 1) {
                    at(w, win, a, dy, back, Blocks.quartz_block, 2);   // a pillar's foot under the banner
                } else if (dy == 4) {
                    at(w, win, a, dy, back, m == 0 ? rock : Blocks.quartz_block, m == 0 ? SubstrateBlocksSC.TRACE_NODE : 1);
                } else {
                    at(w, win, a, dy, back, Blocks.wool, CYAN);     // the banner
                }
            }
            for (int p = back; p <= front; p++) {                  // the canopy: titanium, a lead glass skylight
                boolean sky = m <= 1 && p > back && p < front;
                if (sky) {
                    at(w, win, a, 5, p, leadGlass(), 0);
                } else {
                    at(w, win, a, 5, p, titanium());
                }
            }
        }
        for (int s = -1; s <= 1; s += 2) {                         // the front posts
            for (int dy = 1; dy <= 4; dy++) {
                at(w, win, s * 3, dy, front, steel());
            }
        }
    }
}
