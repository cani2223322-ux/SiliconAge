package com.sc.embassy;

import com.sc.block.BlockShowcase2SC;
import com.sc.dim.SubstrateBlocksSC;
import com.sc.settle.SettlementBlocksSC;
import com.sc.settle.gen.SettlementLayoutSC;
import com.sc.tileentity.TileEntityShowcaseSC;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE S (docs/plan-substrate-tech-f6.md §6.1, §6.2, rule ПЕ-7): «Энергополе» - "SCOwEmbEnergy", a decor lot of the embassy's belt
 * (EmbassyQuartersSC.LOT_ENERGY, ENERGY_L along its entrance x ENERGY_S deep, the entrance towards the core): what the Tuners keep
 * their workshops alive with in the green world, the Power Engineer's showcases (BlockShowcase2SC - the real machines' look,
 * unbreakable, nothing to take):
 * <ul>
 * <li>variant 0 - a solar field: rows of solar panels on copper cable traces with brick walks between them, the transformer at the
 * entrance's middle, lamp posts at the corners;</li>
 * <li>variant 1 - wind masts: three steel masts on brick pads, a wind generator on each (facing the core), an energy storage at
 * each mast's foot, a brick walk along them, lamps set in the pads' corners.</li>
 * </ul>
 * Idempotent: every block of it is set once a pass.
 */
public class ComponentEmbEnergySC extends ComponentEmbassySC {

    /** A wind mast's height (the generator on top of it). */
    public static final int MAST_H = 5;

    public ComponentEmbEnergySC() {
    }

    public ComponentEmbEnergySC(int id, EmbassyLayoutSC.Plan p, SettlementLayoutSC.Lot lot) {
        super(id, p.floorY, lot, -1, EmbassyQuartersSC.ENERGY_H);
    }

    @Override
    protected void build(World w, StructureBoundingBox win) {
        if (variant == 1) {
            wind(w, win);
        } else {
            solar(w, win);
        }
    }

    /** The solar field. */
    private void solar(World w, StructureBoundingBox win) {
        int n = width(), d = depth(), c = n / 2;
        for (int u = 0; u < n; u++) {
            for (int v = 0; v < d; v++) {
                boolean edge = u == 0 || u == n - 1 || v == 0 || v == d - 1, corner = (u == 0 || u == n - 1) && (v == 0 || v == d - 1);
                boolean row = !edge && v % 2 == 1;
                set(w, win, u, 0, v, rock(), row ? SubstrateBlocksSC.COPPER_TRACE : BRICK);
                if (corner) {                                           // the lamp posts
                    set(w, win, u, 1, v, steel());
                    set(w, win, u, 2, v, steel());
                    set(w, win, u, 3, v, rock(), SubstrateBlocksSC.TRACE_NODE);
                } else if (row) {
                    showcase2(w, win, u, 1, v, BlockShowcase2SC.SOLAR_PANEL, 0, -1);
                } else if (v == 0 && u == c) {
                    showcase2(w, win, u, 1, v, BlockShowcase2SC.TRANSFORMER, 0, -1);
                }
            }
        }
    }

    /** The wind masts. */
    private void wind(World w, StructureBoundingBox win) {
        int n = width(), d = depth(), mid = d / 2;
        int[] masts = {2, n / 2, n - 3};
        for (int u = 0; u < n; u++) {
            for (int v = 0; v < d; v++) {
                int pad = -1;
                for (int m : masts) {
                    if (Math.abs(u - m) <= 1 && Math.abs(v - mid) <= 1) {
                        pad = m;
                    }
                }
                if (pad >= 0) {                                         // a mast's pad: lamps in its corners
                    boolean corner = Math.abs(u - pad) == 1 && Math.abs(v - mid) == 1;
                    set(w, win, u, 0, v, rock(), corner ? SubstrateBlocksSC.TRACE_NODE : BRICK);
                } else if (v == mid) {
                    set(w, win, u, 0, v, rock(), BRICK);                // the walk along the masts
                }
            }
        }
        for (int m : masts) {
            for (int dy = 1; dy <= MAST_H; dy++) {
                set(w, win, m, dy, mid, steel());
            }
            showcase2(w, win, m, MAST_H + 1, mid, BlockShowcase2SC.WIND_GENERATOR, 0, -1);
            showcase2(w, win, m, 1, mid - 1, BlockShowcase2SC.ENERGY_STORAGE, 0, -1);
        }
    }

    /** A BlockShowcase2SC machine of `meta` at (u, floor + dy, v), its front towards (du, dv) of the lot's frame. */
    private void showcase2(World w, StructureBoundingBox win, int u, int dy, int v, int meta, int du, int dv) {
        if (SettlementBlocksSC.showcase2 == null || !set(w, win, u, dy, v, SettlementBlocksSC.showcase2, meta)) {
            return;
        }
        int dir = SettlementLayoutSC.frameDir(facing, du, dv);
        TileEntity te = w.getTileEntity(wx(u, v), floorY + dy, wz(u, v));
        if (dir >= 0 && te instanceof TileEntityShowcaseSC) {
            ((TileEntityShowcaseSC) te).setFacing(SIDE_OF_DIR[dir]);
        }
    }
}
