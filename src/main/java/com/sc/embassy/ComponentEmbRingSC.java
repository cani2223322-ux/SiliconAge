package com.sc.embassy;

import com.sc.block.BlockShowcase2SC;
import com.sc.dim.SubstrateBlocksSC;
import com.sc.settle.gen.SettlementLayoutSC;
import com.sc.tileentity.TileEntityEmbassyRingSC;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE S (docs/plan-substrate-tech-f6.md §6.2, rules ПЕ-8, ПЯ-3): «Большое кольцо» - "SCOwEmbRing", standing upright across the
 * main street on the Vokzal, its plane a = r + VOKZAL_OUT + RING_BACK (the record's point "ring" is its middle, RING_DY over the
 * floor): Ø 13 of Ring Gravity Coils (BlockShowcase2SC GRAVITY_COIL - 40 segments, EmbassyQuartersSC.ringCoil), radius RING_R, its foot
 * sunk 3 into the platform. In its row: the platform's floor inside it (copper traces under the vortex, VORTEX_HALF each side),
 * «Ядро Большого кольца» (EmbassyBlocksSC.ring) under its middle - flush with the floor at (ring x, floor, ring z) - set up for its
 * embassy (TileEntityEmbassyRingSC.setup) only when its tile isn't that embassy's already (the «Достройка» runs this again); the air
 * inside the ring kept clear (the vortex 5 x 5 from the floor - EmbassyLayoutSC.inVortex - is where a passenger steps in).
 */
public class ComponentEmbRingSC extends ComponentEmbFrameSC {

    public ComponentEmbRingSC() {
    }

    public ComponentEmbRingSC(int id, EmbassyLayoutSC.Plan p, SettlementLayoutSC.Lot lot) {
        super(id, p, lot, EmbassyLayoutSC.RING_DY - EmbassyLayoutSC.RING_R, EmbassyLayoutSC.RING_DY + EmbassyLayoutSC.RING_R);
    }

    @Override
    protected void build(World w, StructureBoundingBox win) {
        Block rock = rock();
        int a = radius + EmbassyLayoutSC.VOKZAL_OUT + EmbassyLayoutSC.RING_BACK, rr = EmbassyLayoutSC.RING_R, mid = EmbassyLayoutSC.RING_DY;
        for (int p = -rr; p <= rr; p++) {
            for (int dy = mid - rr; dy <= mid + rr; dy++) {
                int q = dy - mid;
                double d = Math.sqrt((double) p * p + (double) q * q);
                if (EmbassyQuartersSC.ringCoil(p, q)) {
                    machine2(w, win, a, dy, p, BlockShowcase2SC.GRAVITY_COIL, 0, 0);
                } else if (dy == 0) {                                        // the platform's floor in the ring's row
                    if (p == 0) {
                        core(w, win, a);
                    } else {
                        at(w, win, a, 0, p, rock, Math.abs(p) <= EmbassyLayoutSC.VORTEX_HALF ? SubstrateBlocksSC.COPPER_TRACE : BRICK);
                    }
                } else if (dy > 0 && d < rr) {                               // the vortex's room inside the ring
                    air(w, win, a, dy, p);
                }
            }
        }
    }

    /** The ring's core under its middle, set up for this embassy once. */
    private void core(World w, StructureBoundingBox win, int a) {
        if (EmbassyBlocksSC.ring == null || !at(w, win, a, 0, 0, EmbassyBlocksSC.ring, 0)) {
            return;
        }
        TileEntity te = w.getTileEntity(fx(a, 0), floorY, fz(a, 0));
        if (te instanceof TileEntityEmbassyRingSC && ((TileEntityEmbassyRingSC) te).embassyId() != settlementId) {
            ((TileEntityEmbassyRingSC) te).setup(settlementId);
        }
    }
}
