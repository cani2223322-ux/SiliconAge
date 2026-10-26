package com.sc.ruin;

import java.util.Random;

import com.sc.block.BlockRuinSC;
import com.sc.dim.SubstrateBlocksSC;

import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * ПД-8 (docs/plan-ideas-2026-10-26.md, «Тоннели под плато», ★ A): the junction of a cell's two tunnels - "SCSi14TunJunction" - a hall
 * JUNCTION x JUNCTION, ROOM_H high over a ruin-metal floor at the cell's floorY, walled and roofed with Copper Trace, open where the two
 * tunnels come in; a column of Trace Nodes in its middle (the lamp) and a chest of RuinLootSC.tunnelChest at a corner. Built after the
 * segments in every window (its air takes their walls inside it). Only inside the populate window.
 */
public class ComponentTunnelJunctionSC extends ComponentRuinSC {

    /** The hall's inside height. */
    public static final int ROOM_H = 5;

    protected int jx, jz, floorY;

    public ComponentTunnelJunctionSC() {
    }

    public ComponentTunnelJunctionSC(long worldSeed, int cellX, int cellZ, int jx, int jz) {
        super(TunnelLayoutSC.hash(worldSeed, cellX, cellZ, 21), box(jx, jz, TunnelLayoutSC.floorY(worldSeed, cellX, cellZ)));
        this.jx = jx;
        this.jz = jz;
        this.floorY = TunnelLayoutSC.floorY(worldSeed, cellX, cellZ);
    }

    static StructureBoundingBox box(int jx, int jz, int floor) {
        int r = TunnelLayoutSC.JUNCTION / 2 + 1;
        return new StructureBoundingBox(jx - r, floor, jz - r, jx + r, floor + ROOM_H + 1, jz + r);
    }

    @Override
    protected void func_143012_a(NBTTagCompound nbt) {
        super.func_143012_a(nbt);
        nbt.setIntArray("TunJ", new int[]{jx, jz, floorY});
    }

    @Override
    protected void func_143011_b(NBTTagCompound nbt) {
        super.func_143011_b(nbt);
        int[] a = nbt.getIntArray("TunJ");
        if (a.length == 3) {
            jx = a[0];
            jz = a[1];
            floorY = a[2];
        }
    }

    @Override
    protected void build(World w, StructureBoundingBox win) {
        int r = TunnelLayoutSC.JUNCTION / 2 + 1, h = TunnelLayoutSC.HALF;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int x = jx + dx, z = jz + dz;
                boolean edge = Math.abs(dx) == r || Math.abs(dz) == r;
                boolean door = edge && (Math.abs(dz) <= h && Math.abs(dx) == r || Math.abs(dx) <= h && Math.abs(dz) == r);
                ruin(w, win, x, floorY, z, BlockRuinSC.RUIN_METAL);
                for (int y = floorY + 1; y <= floorY + ROOM_H; y++) {
                    if (edge && !(door && y <= floorY + TunnelLayoutSC.HEIGHT)) {
                        rock(w, win, x, y, z, SubstrateBlocksSC.COPPER_TRACE);
                    } else if (dx == 0 && dz == 0) {
                        rock(w, win, x, y, z, SubstrateBlocksSC.TRACE_NODE);     // the lamp column
                    } else {
                        air(w, win, x, y, z);
                    }
                }
                rock(w, win, x, floorY + ROOM_H + 1, z, (dx + dz) % 4 == 0 && !edge ? SubstrateBlocksSC.TRACE_NODE : SubstrateBlocksSC.COPPER_TRACE);
            }
        }
        int cx = jx + (r - 1), cz = jz + (r - 1);
        if (set(w, win, cx, floorY + 1, cz, Blocks.chest, 2)) {
            TileEntity te = w.getTileEntity(cx, floorY + 1, cz);
            if (te instanceof IInventory) {
                RuinLootSC.fillTunnel((IInventory) te, new Random(seed));
            }
        }
    }
}
