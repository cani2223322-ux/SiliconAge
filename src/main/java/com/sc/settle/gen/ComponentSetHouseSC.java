package com.sc.settle.gen;

import com.sc.dim.SubstrateBlocksSC;
import com.sc.util.Material;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE S (docs/plan-substrate-tech-f2.md §4.3 п. 4): a living cell - "SCSi14SetHouse", HOUSE x HOUSE, two (three)
 * homes inside (SettlementLayoutSC.houseHomes). No logs, no doors (proposal §5): an arch one wide and two high, lead
 * glass windows on three sides, a flat roof at floor + 4 with a lamp in its middle. Three looks (Lot.variant): brick
 * on steel under a steel roof; oxide crust (its colour by the lot's place) on titanium under brick; zirconium on brick
 * under titanium.
 */
public class ComponentSetHouseSC extends ComponentSettlementSC {

    public ComponentSetHouseSC() {
    }

    public ComponentSetHouseSC(int id, SettlementLayoutSC.Plan p, SettlementLayoutSC.Lot lot) {
        super(id, p.floorY, lot, -1, SettlementLayoutSC.HOUSE_H);
    }

    @Override
    protected void build(World w, StructureBoundingBox win) {
        int n = width(), c = n / 2;
        int look = Math.abs(variant) % SettlementLayoutSC.HOUSE_VARIANTS;
        int crust = SubstrateBlocksSC.OXIDE_VIOLET + ((boundingBox.minX * 31 + boundingBox.minZ * 17) & 3);
        ItemStack post, roof, wallMetal = null;
        int wallMeta = BRICK;
        if (look == 0) {
            post = roof = steel();
        } else if (look == 1) {
            post = titanium();
            roof = null;
            wallMeta = crust;
        } else {
            post = null;
            roof = titanium();
            wallMetal = metal(Material.ZIRCONIUM);
        }
        for (int u = 0; u < n; u++) {
            for (int v = 0; v < n; v++) {
                set(w, win, u, 0, v, rock(), BRICK);
                if (u == c && v == c) {
                    set(w, win, u, 4, v, rock(), SubstrateBlocksSC.TRACE_NODE);
                } else if (roof != null) {
                    set(w, win, u, 4, v, roof);
                } else {
                    set(w, win, u, 4, v, rock(), BRICK);
                }
                boolean edgeU = u == 0 || u == n - 1, edgeV = v == 0 || v == n - 1;
                if (!edgeU && !edgeV) {
                    continue;
                }
                boolean corner = edgeU && edgeV, arch = v == 0 && u == c;
                boolean window = !corner && !arch && ((edgeU && v == c) || (v == n - 1 && u == c));
                for (int dy = 1; dy <= 3; dy++) {
                    if (arch && dy <= 2) {
                        clear(w, win, u, dy, v);
                    } else if (window && dy == 2) {
                        set(w, win, u, dy, v, leadGlass(), 0);
                    } else if (corner && post != null) {
                        set(w, win, u, dy, v, post);
                    } else if (corner || wallMetal == null) {
                        set(w, win, u, dy, v, rock(), corner ? BRICK : wallMeta);
                    } else {
                        set(w, win, u, dy, v, wallMetal);
                    }
                }
            }
        }
    }
}
