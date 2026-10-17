package com.sc.settle.gen;

import com.sc.block.BlockDockSC;
import com.sc.settle.SettlementBlocksSC;

import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE S (docs/plan-substrate-tech-f2.md §4.3 п. 6, rule ПР-1): «Причал» - "SCSi14SetDock", the 7 x 7 landing of a
 * Settlement / City on the gate's axis, its beacon DOCK_OUT past the dome's edge (wholly outside the dome, inside the
 * belt). The plate (dockSC:0) and the beacon (dockSC:1, package B) lie flush with the floor; the ground piece has
 * levelled the pad round it and cleared the air over it (the bridge's 5 x 5 vortex needs 8 free blocks at least).
 */
public class ComponentSetDockSC extends ComponentSettlementSC {

    public ComponentSetDockSC() {
    }

    public ComponentSetDockSC(int id, SettlementLayoutSC.Plan p, SettlementLayoutSC.Lot lot) {
        super(id, p.floorY, lot, -1, 1);
    }

    @Override
    protected void build(World w, StructureBoundingBox win) {
        if (SettlementBlocksSC.dock == null) {
            return;
        }
        int n = width(), c = n / 2;
        for (int u = 0; u < n; u++) {
            for (int v = 0; v < n; v++) {
                set(w, win, u, 0, v, SettlementBlocksSC.dock, u == c && v == c ? BlockDockSC.BEACON : BlockDockSC.PLATE);
            }
        }
    }
}
