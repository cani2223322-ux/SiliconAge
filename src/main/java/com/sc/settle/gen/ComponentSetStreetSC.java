package com.sc.settle.gen;

import com.sc.dim.SubstrateBlocksSC;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE S (docs/plan-substrate-tech-f2.md §4.3): the main street - "SCSi14SetStreet", three blocks wide from the
 * guard square through the gate to the dome's edge and on to the dock (or a few blocks past the edge at an Outpost).
 * Under the dome Settlement Brick with glowing nodes along both edges; outside it Crystallite (nothing of the
 * settlement's own lies outside its protection but the dock). Its frame faces the gate: u = p + 1, v from its far end.
 */
public class ComponentSetStreetSC extends ComponentSettlementSC {

    /** A node on each edge every this many blocks. */
    public static final int LAMP_EVERY = 6;

    private int radius;

    public ComponentSetStreetSC() {
    }

    public ComponentSetStreetSC(int id, SettlementLayoutSC.Plan p, SettlementLayoutSC.Lot lot) {
        super(id, p.floorY, lot, -1, 1);
        radius = p.radius;
    }

    @Override
    protected void func_143012_a(NBTTagCompound nbt) {
        super.func_143012_a(nbt);
        nbt.setInteger("Radius", radius);
    }

    @Override
    protected void func_143011_b(NBTTagCompound nbt) {
        super.func_143011_b(nbt);
        radius = Math.max(1, nbt.getInteger("Radius"));
    }

    @Override
    protected void build(World w, StructureBoundingBox win) {
        int len = depth(), end = SettlementLayoutSC.SQUARE_HALF + len;     // the far end's a (v = 0 there)
        for (int v = 0; v < len; v++) {
            int a = end - v;
            boolean inside = a <= radius - 1;
            for (int u = 0; u < 3; u++) {
                int meta = !inside ? SubstrateBlocksSC.CRYSTALLITE
                        : u != 1 && a % LAMP_EVERY == 0 ? SubstrateBlocksSC.TRACE_NODE : BRICK;
                set(w, win, u, 0, v, rock(), meta);
            }
        }
    }
}
