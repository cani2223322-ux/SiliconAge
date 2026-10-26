package com.sc.ruin;

import java.util.Random;

import com.sc.block.BlockRuinSC;
import com.sc.dim.SubstrateBlocksSC;
import com.sc.mob.BornInSubstrateSC;
import com.sc.mob.EntityMimicSC;

import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * ПД-8 (docs/plan-ideas-2026-10-26.md, «Тоннели под плато», ★ A): one segment of a copper busbar tunnel - "SCSi14TunSeg" - at most
 * TunnelLayoutSC.SEGMENT blocks of it along its axis: the floor of ruin metal (the block one stands on at TunnelLayoutSC.floorAt - the
 * ramps near the ends), WIDTH x HEIGHT of air over it, the walls and the ceiling a block of Copper Trace (a Trace Node - the light -
 * every NODE_EVERY); every CHEST_EVERY a niche in a wall: a chest of RuinLootSC.tunnelChest or, MIMIC_PCT of them, a mimic waiting in
 * it. An end a camp is near stays shut (a Trace wall: no opening). Only inside the populate window, by the pure layout (no cascade).
 */
public class ComponentTunnelSC extends ComponentRuinSC {

    /** A Trace Node in the walls this often (blocks along). */
    public static final int NODE_EVERY = 8;

    /** The cell, the axis, the segment's run [t0..t1] along it, the line across. */
    protected int cellX, cellZ, t0, t1, across;
    protected boolean alongX;
    /** The world's seed (the pure layout asks it again in every window). */
    protected long worldSeed;

    public ComponentTunnelSC() {
    }

    public ComponentTunnelSC(long worldSeed, int cellX, int cellZ, boolean alongX, int t0, int t1) {
        super(TunnelLayoutSC.hash(worldSeed, cellX, cellZ, alongX ? 11 : 12) ^ t0, box(worldSeed, cellX, cellZ, alongX, t0, t1));
        this.worldSeed = worldSeed;
        this.cellX = cellX;
        this.cellZ = cellZ;
        this.alongX = alongX;
        this.t0 = t0;
        this.t1 = t1;
        this.across = TunnelLayoutSC.line(worldSeed, cellX, cellZ, alongX);
    }

    /** The segment's box: its run, the walls across, the lowest floor to the highest ceiling. */
    static StructureBoundingBox box(long seed, int cellX, int cellZ, boolean alongX, int t0, int t1) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, c = TunnelLayoutSC.line(seed, cellX, cellZ, alongX);
        for (int t = t0; t <= t1; t++) {
            int f = TunnelLayoutSC.floorAt(seed, cellX, cellZ, alongX, t);
            lo = Math.min(lo, f);
            hi = Math.max(hi, f + TunnelLayoutSC.HEIGHT + 1);
        }
        int w = TunnelLayoutSC.WALL;
        return alongX ? new StructureBoundingBox(t0, lo, c - w, t1, hi, c + w) : new StructureBoundingBox(c - w, lo, t0, c + w, hi, t1);
    }

    @Override
    protected void func_143012_a(NBTTagCompound nbt) {
        super.func_143012_a(nbt);
        nbt.setIntArray("Tun", new int[]{cellX, cellZ, t0, t1, across, alongX ? 1 : 0});
        nbt.setLong("TunSeed", worldSeed);
    }

    @Override
    protected void func_143011_b(NBTTagCompound nbt) {
        super.func_143011_b(nbt);
        int[] a = nbt.getIntArray("Tun");
        if (a.length == 6) {
            cellX = a[0];
            cellZ = a[1];
            t0 = a[2];
            t1 = a[3];
            across = a[4];
            alongX = a[5] != 0;
        }
        worldSeed = nbt.getLong("TunSeed");
    }

    /** The block at `t` along, `c` across (from the line), height y. */
    protected int bx(int t, int c) {
        return alongX ? t : across + c;
    }

    protected int bz(int t, int c) {
        return alongX ? across + c : t;
    }

    @Override
    protected void build(World w, StructureBoundingBox win) {
        int[] e = TunnelLayoutSC.ends(worldSeed, cellX, cellZ, alongX);
        boolean shutA = TunnelLayoutSC.endShut(worldSeed, cellX, cellZ, alongX, false), shutB = TunnelLayoutSC.endShut(worldSeed, cellX, cellZ, alongX, true);
        for (int t = t0; t <= t1; t++) {
            int f = TunnelLayoutSC.floorAt(worldSeed, cellX, cellZ, alongX, t);
            boolean shut = t == e[0] && shutA || t == e[1] && shutB;
            boolean node = (t - e[0]) % NODE_EVERY == 0;
            for (int c = -TunnelLayoutSC.WALL; c <= TunnelLayoutSC.WALL; c++) {
                int x = bx(t, c), z = bz(t, c);
                boolean wall = Math.abs(c) == TunnelLayoutSC.WALL;
                ruin(w, win, x, f, z, BlockRuinSC.RUIN_METAL);  // the floor
                for (int y = f + 1; y <= f + TunnelLayoutSC.HEIGHT; y++) {
                    if (wall || shut) {
                        rock(w, win, x, y, z, node && wall && y == f + 2 ? SubstrateBlocksSC.TRACE_NODE : SubstrateBlocksSC.COPPER_TRACE);
                    } else {
                        air(w, win, x, y, z);
                    }
                }
                rock(w, win, x, f + TunnelLayoutSC.HEIGHT + 1, z, node && c == 0 ? SubstrateBlocksSC.TRACE_NODE : SubstrateBlocksSC.COPPER_TRACE);
            }
            niche(w, win, e, t, f);
        }
    }

    /** Every CHEST_EVERY along (from the first end): a niche in one wall - a chest, or a mimic (MIMIC_PCT). */
    private void niche(World w, StructureBoundingBox win, int[] e, int t, int f) {
        int d = t - e[0];
        if (d <= 0 || t >= e[1] || d % TunnelLayoutSC.CHEST_EVERY != TunnelLayoutSC.CHEST_EVERY / 2) {
            return;
        }
        int side = roll(t, f, 0, 7) < 50 ? 1 : -1, c = side * TunnelLayoutSC.WALL;
        int x = bx(t, c), z = bz(t, c);
        if (!win.isVecInside(x, f + 1, z) || !boundingBox.isVecInside(x, f + 1, z)) {
            return;
        }
        air(w, win, x, f + 2, z);
        int facing = alongX ? (side > 0 ? 2 : 3) : (side > 0 ? 4 : 5);    // towards the aisle
        if (roll(t, f, 0, 13) < TunnelLayoutSC.MIMIC_PCT) {
            air(w, win, x, f + 1, z);
            EntityMimicSC m = new EntityMimicSC(w);
            m.setLocationAndAngles(x + 0.5D, f + 1, z + 0.5D, alongX ? (side > 0 ? 180F : 0F) : (side > 0 ? 90F : 270F), 0F);
            m.setDisguise(roll(t, f, 0, 17) < 50 ? 0 : 1);
            BornInSubstrateSC.mark(m);
            m.func_110163_bv();
            m.setHome(x, f + 1, z, 8);
            w.spawnEntityInWorld(m);
            return;
        }
        if (set(w, win, x, f + 1, z, Blocks.chest, facing)) {
            TileEntity te = w.getTileEntity(x, f + 1, z);
            if (te instanceof IInventory) {
                RuinLootSC.fillTunnel((IInventory) te, new Random(seed ^ (long) t * 31L));
            }
        }
    }
}
