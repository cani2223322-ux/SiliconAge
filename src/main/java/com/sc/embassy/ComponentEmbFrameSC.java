package com.sc.embassy;

import com.sc.settle.SettlementBlocksSC;
import com.sc.settle.gen.SettlementLayoutSC;
import com.sc.tileentity.TileEntityShowcaseSC;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE S (docs/plan-substrate-tech-f6.md §6.2): the base of the embassy's pieces that build in the embassy's own frame rather than
 * their lot's - the Heart, the streets, the gates, the wall, the depots, the Vokzal, the Ring: `a` along the main street towards the
 * main gate, `p` across it, the core at (0, 0) (EmbassyLayoutSC.Plan.x / z - kept here as the core's column, the dome's radius and
 * the main gate's way: NBT "EmbFrame"). Never registered itself (abstract): each piece is, under "SCOwEmb" + its name
 * (EmbassyPiecesSC.register). Every block goes in through ComponentSettlementSC.put - only inside the window, flag 2, light by hand -
 * so a piece is idempotent (the «Достройка» runs it again: the same block and meta change nothing).
 */
public abstract class ComponentEmbFrameSC extends ComponentEmbassySC {

    /** The core's column, the dome's radius, the main gate's way (SettlementLayoutSC.NORTH..WEST). */
    protected int coreX, coreZ, radius, gateDir;

    public ComponentEmbFrameSC() {
    }

    /** A piece on `lot` of embassy `id`'s plan `p`, from `dyMin` to `dyMax` over the floor. */
    protected ComponentEmbFrameSC(int id, EmbassyLayoutSC.Plan p, SettlementLayoutSC.Lot lot, int dyMin, int dyMax) {
        super(id, p.floorY, lot, dyMin, dyMax);
        coreX = p.coreX;
        coreZ = p.coreZ;
        radius = p.radius;
        gateDir = p.gateDir & 3;
    }

    @Override
    protected void func_143012_a(NBTTagCompound nbt) {
        super.func_143012_a(nbt);
        nbt.setIntArray("EmbFrame", new int[]{coreX, coreZ, radius, gateDir});
    }

    @Override
    protected void func_143011_b(NBTTagCompound nbt) {
        super.func_143011_b(nbt);
        int[] f = ints(nbt, "EmbFrame", 4);
        coreX = f[0];
        coreZ = f[1];
        radius = Math.max(1, f[2]);
        gateDir = f[3] & 3;
    }

    // ------------------------------------------------------------------ the frame

    /** The world X / Z of (a, p). */
    protected int fx(int a, int p) {
        return coreX + a * SettlementLayoutSC.DIR_X[gateDir] - p * SettlementLayoutSC.DIR_Z[gateDir];
    }

    protected int fz(int a, int p) {
        return coreZ + a * SettlementLayoutSC.DIR_Z[gateDir] + p * SettlementLayoutSC.DIR_X[gateDir];
    }

    /** (x, z)'s frame coordinates {a, p}. */
    protected int[] frameOf(int x, int z) {
        int dx = x - coreX, dz = z - coreZ, ax = SettlementLayoutSC.DIR_X[gateDir], az = SettlementLayoutSC.DIR_Z[gateDir];
        return new int[]{dx * ax + dz * az, -dx * az + dz * ax};
    }

    /** The piece's box in the frame {a0, p0, a1, p1}. */
    protected int[] frameBox() {
        int[] f0 = frameOf(boundingBox.minX, boundingBox.minZ), f1 = frameOf(boundingBox.maxX, boundingBox.maxZ);
        return new int[]{Math.min(f0[0], f1[0]), Math.min(f0[1], f1[1]), Math.max(f0[0], f1[0]), Math.max(f0[1], f1[1])};
    }

    /** The part of the frame's rectangle q {a0, p0, a1, p1} the window holds (null: none) - a piece walks only that. */
    protected int[] clip(int[] q, StructureBoundingBox win) {
        int[] f0 = frameOf(win.minX, win.minZ), f1 = frameOf(win.maxX, win.maxZ);
        int a0 = Math.max(q[0], Math.min(f0[0], f1[0])), a1 = Math.min(q[2], Math.max(f0[0], f1[0]));
        int p0 = Math.max(q[1], Math.min(f0[1], f1[1])), p1 = Math.min(q[3], Math.max(f0[1], f1[1]));
        return a0 > a1 || p0 > p1 ? null : new int[]{a0, p0, a1, p1};
    }

    /** The world direction (NORTH..WEST) of a step (da, dp) of the frame. */
    protected int dirOf(int da, int dp) {
        return EmbassyQuartersSC.frameDir(gateDir, da, dp);
    }

    /** (a, p) is in the protected cylinder (SettlementRegistrySC.Settlement.contains: the dome's radius across). */
    protected boolean domed(int a, int p) {
        return (long) a * a + (long) p * p <= (long) radius * radius;
    }

    // ------------------------------------------------------------------ blocks

    /** A block at (a, floor + dy, p) - a trace or quartz as the trim (ComponentEmbassySC.trim). @return placed (inside the window) */
    protected boolean at(World w, StructureBoundingBox win, int a, int dy, int p, Block b, int meta) {
        int t = trim(b, meta);
        return t < 0 ? put(w, win, fx(a, p), floorY + dy, fz(a, p), b, meta)
                : put(w, win, fx(a, p), floorY + dy, fz(a, p), com.sc.settle.SettlementBlocksSC.decor, t);
    }

    /** A block from a stack (the trim) at (a, floor + dy, p). */
    protected boolean at(World w, StructureBoundingBox win, int a, int dy, int p, ItemStack stack) {
        return stack != null && stack.getItem() != null && at(w, win, a, dy, p, Block.getBlockFromItem(stack.getItem()), stack.getItemDamage());
    }

    /** Air at (a, floor + dy, p) (only where something stands). */
    protected void air(World w, StructureBoundingBox win, int a, int dy, int p) {
        int x = fx(a, p), y = floorY + dy, z = fz(a, p);
        if (y > 0 && y < 256 && win.isVecInside(x, y, z) && !w.isAirBlock(x, y, z)) {
            w.setBlockToAir(x, y, z);
        }
    }

    /**
     * A showcase machine (`block`: SettlementBlocksSC.showcase or showcase2) of `meta` at (a, floor + dy, p), its front towards (da, dp)
     * of the frame (0, 0: as it stands) - its light tile turned (TileEntityShowcaseSC.setFacing changes only what differs).
     */
    protected void machine(World w, StructureBoundingBox win, int a, int dy, int p, Block block, int meta, int da, int dp) {
        if (block == null || !at(w, win, a, dy, p, block, meta)) {
            return;
        }
        int d = da == 0 && dp == 0 ? -1 : dirOf(da, dp);
        TileEntity te = w.getTileEntity(fx(a, p), floorY + dy, fz(a, p));
        if (d >= 0 && te instanceof TileEntityShowcaseSC) {
            ((TileEntityShowcaseSC) te).setFacing(SIDE_OF_DIR[d]);
        }
    }

    /** A BlockShowcase2SC machine (the Ф3 / Ф6 metas). */
    protected void machine2(World w, StructureBoundingBox win, int a, int dy, int p, int meta, int da, int dp) {
        machine(w, win, a, dy, p, SettlementBlocksSC.showcase2, meta, da, dp);
    }

    /** A BlockShowcaseSC machine (the first block's metas). */
    protected void machine1(World w, StructureBoundingBox win, int a, int dy, int p, int meta, int da, int dp) {
        machine(w, win, a, dy, p, SettlementBlocksSC.showcase, meta, da, dp);
    }
}
