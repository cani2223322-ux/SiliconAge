package com.sc.settle.gen;

import com.sc.block.BlockShowcaseSC;
import com.sc.dim.SubstrateBlocksSC;
import com.sc.settle.SettlementBlocksSC;
import com.sc.tileentity.TileEntitySettlementCoreSC;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE S (docs/plan-substrate-tech-f2.md §4.3 п. 1-2): «Сердце» and the guard square - "SCSi14SetHeart", the
 * 15 x 15 round the core. Built in the settlement's frame (a towards the gate, p across; u = p + 7, v = 7 - a):
 * <ul>
 * <li>the square: Settlement Brick, a copper trace round its edge with glowing nodes at the corners and the middles,
 * traces from the cap out to the edge (a circuit board seen from above);</li>
 * <li>the Heart: the core (floor + 1) on a tungsten pedestal set in the floor, under a 7 x 7 cap of lead glass on steel
 * posts, eight Tokamak Coils round it (four of them two high) - all of it the trim's (ComponentSettlementSC's palette) -,
 * nodes in the cap's floor. The core gets its settlement at once
 * (TileEntitySettlementCoreSC.setup: id, radius, floor) - its field stands from its first tick;</li>
 * <li>the Clone Capsule (two showcases one over the other, a titanium cap, steel posts with lamps) facing P_CLONE; the
 * armour station facing the Heart with its lamp posts (P_SQUARE in front of it); 2..4 charge pads in the floor
 * (P_PAD*).</li>
 * </ul>
 */
public class ComponentSetHeartSC extends ComponentSettlementSC {

    private static final int H = SettlementLayoutSC.SQUARE_HALF, CAP = SettlementLayoutSC.CAP_HALF;

    private int radius, pads;

    public ComponentSetHeartSC() {
    }

    public ComponentSetHeartSC(int id, SettlementLayoutSC.Plan p, SettlementLayoutSC.Lot lot) {
        super(id, p.floorY, lot, -1, SettlementLayoutSC.HEART_H);
        radius = p.radius;
        pads = SettlementLayoutSC.PADS[Math.min(SettlementLayoutSC.PADS.length - 1, p.site.size.ordinal())];
    }

    @Override
    protected void func_143012_a(NBTTagCompound nbt) {
        super.func_143012_a(nbt);
        nbt.setInteger("Radius", radius);
        nbt.setInteger("Pads", pads);
    }

    @Override
    protected void func_143011_b(NBTTagCompound nbt) {
        super.func_143011_b(nbt);
        radius = Math.max(1, nbt.getInteger("Radius"));
        pads = Math.max(0, Math.min(SettlementLayoutSC.PAD_AT.length, nbt.getInteger("Pads")));
    }

    /** A block at (a, floor + dy, p) of the settlement's frame. */
    private boolean at(World w, StructureBoundingBox win, int a, int dy, int p, net.minecraft.block.Block b, int meta) {
        return set(w, win, p + H, dy, H - a, b, meta);
    }

    private void at(World w, StructureBoundingBox win, int a, int dy, int p, net.minecraft.item.ItemStack stack) {
        set(w, win, p + H, dy, H - a, stack);
    }

    /** A showcase at (a, dy, p) whose front looks along (da, dp) of the settlement's frame. */
    private void machine(World w, StructureBoundingBox win, int a, int dy, int p, int meta, int da, int dp) {
        showcase(w, win, p + H, dy, H - a, meta, dp, -da);
    }

    @Override
    protected void build(World w, StructureBoundingBox win) {
        net.minecraft.block.Block rock = rock();
        // the square's floor
        for (int a = -H; a <= H; a++) {
            for (int p = -H; p <= H; p++) {
                int meta = BRICK;
                boolean edge = Math.abs(a) == H || Math.abs(p) == H;
                if (edge) {
                    meta = (a == 0 || p == 0 || Math.abs(a) == Math.abs(p)) ? SubstrateBlocksSC.TRACE_NODE : SubstrateBlocksSC.COPPER_TRACE;
                } else if ((a == 0 || p == 0) && Math.max(Math.abs(a), Math.abs(p)) > CAP + 1) {
                    meta = SubstrateBlocksSC.COPPER_TRACE;          // the traces from the cap out to the edge
                } else if (Math.abs(a) == 1 && Math.abs(p) == 1) {
                    meta = SubstrateBlocksSC.TRACE_NODE;            // the lights in the cap's floor
                }
                at(w, win, a, 0, p, rock, meta);
            }
        }
        // the pads in the floor
        for (int i = 0; i < pads; i++) {
            int[] q = SettlementLayoutSC.PAD_AT[i];
            machine(w, win, q[0], 0, q[1], BlockShowcaseSC.CHARGE_PAD, 1, 0);
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
        // the pedestal (set in the floor) and the core on it (floor + 1): its settlement at once
        at(w, win, 0, 0, 0, metal(com.sc.util.Material.TUNGSTEN));
        if (SettlementBlocksSC.core != null && at(w, win, 0, 1, 0, SettlementBlocksSC.core, 0)) {
            TileEntity te = w.getTileEntity(wx(H, H), floorY + 1, wz(H, H));
            if (te instanceof TileEntitySettlementCoreSC) {
                ((TileEntitySettlementCoreSC) te).setup(settlementId, radius, floorY);
            }
        }
        // the Clone Capsule, facing P_CLONE
        int[] cap = SettlementLayoutSC.CAPSULE;
        machine(w, win, cap[0], 1, cap[1], BlockShowcaseSC.CLONE_CAPSULE, 0, -1);
        machine(w, win, cap[0], 2, cap[1], BlockShowcaseSC.CLONE_CAPSULE, 0, -1);
        at(w, win, cap[0], 3, cap[1], titanium());
        for (int s = -1; s <= 1; s += 2) {
            at(w, win, cap[0] + s, 1, cap[1], steel());
            at(w, win, cap[0] + s, 2, cap[1], steel());
            at(w, win, cap[0] + s, 3, cap[1], rock, SubstrateBlocksSC.TRACE_NODE);
        }
        // the armour station, facing the Heart, its lamp posts
        int[] st = SettlementLayoutSC.STATION;
        machine(w, win, st[0], 1, st[1], BlockShowcaseSC.ARMOUR_STATION, 1, 0);
        for (int s = -1; s <= 1; s += 2) {
            at(w, win, st[0], 1, st[1] + s, steel());
            at(w, win, st[0], 2, st[1] + s, rock, SubstrateBlocksSC.TRACE_NODE);
        }
    }
}
