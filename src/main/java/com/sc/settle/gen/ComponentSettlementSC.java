package com.sc.settle.gen;

import java.util.Random;

import com.sc.block.BlockSettlementDecorSC;
import com.sc.dim.ChunkProviderSubstrateSC;
import com.sc.dim.SubstrateBlocksSC;
import com.sc.settle.SettlementBlocksSC;
import com.sc.tileentity.TileEntityShowcaseSC;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;

/**
 * PACKAGE S (docs/plan-substrate-tech-f2.md §4.5): a piece of a settlement, built only inside the populate window
 * it is handed by the pure terrain heights. Every piece class has its own MapGenStructureIO name "SCSi14Set..."
 * (MapGenSettlementSC.registerStructures); this base one is registered as "SCSi14SetPart" and builds nothing. A public
 * constructor without arguments (MapGenStructureIO). addComponentParts always returns true (false drops the piece for
 * good). The core's piece (ComponentSetHeartSC) places the core block and calls TileEntitySettlementCoreSC.setup(id,
 * radius, floorY) right after; no piece spawns a creature (the core does, package C).
 * <p>
 * A piece stands on one lot of the plan (SettlementLayoutSC.Lot): its box is the lot's, its frame the lot's - u across
 * the entrance, v from the entrance inwards, dy over the floor. Every block goes in through put(): only inside the
 * window (nothing outside it is read or set, so nothing cascades), with flag 2 (no neighbour updates while the land
 * is made), and a block that gives light is lit by hand where setBlock's own update gave up
 * (ChunkProviderSubstrateSC.glow - as Ф1's trace nodes and druses). No Blocks.stone anywhere.
 */
public class ComponentSettlementSC extends StructureComponent {

    /** The registry's id of its settlement. */
    protected int settlementId = -1;
    /** The settlement's floor (its top solid block); the lot's entrance side (SettlementLayoutSC.NORTH..WEST); its variant. */
    protected int floorY, facing, variant;

    public ComponentSettlementSC() {
    }

    /** A piece on `lot`, from `dyMin` to `dyMax` over the floor. */
    protected ComponentSettlementSC(int id, int floorY, SettlementLayoutSC.Lot lot, int dyMin, int dyMax) {
        super(0);
        this.settlementId = id;
        this.floorY = floorY;
        this.facing = lot.facing;
        this.variant = lot.variant;
        this.coordBaseMode = lot.facing;
        this.boundingBox = new StructureBoundingBox(lot.x0, Math.max(1, floorY + dyMin), lot.z0, lot.x1, Math.min(255, floorY + dyMax), lot.z1);
    }

    @Override
    protected void func_143012_a(NBTTagCompound nbt) {
        nbt.setInteger("SettlementId", settlementId);
        nbt.setInteger("Floor", floorY);
        nbt.setInteger("Facing", facing);
        nbt.setInteger("Variant", variant);
    }

    @Override
    protected void func_143011_b(NBTTagCompound nbt) {
        settlementId = nbt.getInteger("SettlementId");
        floorY = nbt.getInteger("Floor");
        facing = nbt.getInteger("Facing") & 3;
        variant = nbt.getInteger("Variant");
    }

    @Override
    public boolean addComponentParts(World world, Random rand, StructureBoundingBox window) {
        if (SubstrateBlocksSC.rock != null) {
            build(world, window);
        }
        return true;                                    // never false: that would drop the piece for good
    }

    /** The piece's part inside `window` (the subclasses'). */
    protected void build(World world, StructureBoundingBox window) {
    }

    // ------------------------------------------------------------------ the lot's frame

    protected int width() {
        return facing == SettlementLayoutSC.NORTH || facing == SettlementLayoutSC.SOUTH ? boundingBox.getXSize() : boundingBox.getZSize();
    }

    protected int depth() {
        return facing == SettlementLayoutSC.NORTH || facing == SettlementLayoutSC.SOUTH ? boundingBox.getZSize() : boundingBox.getXSize();
    }

    protected int wx(int u, int v) {
        return SettlementLayoutSC.frameX(boundingBox.minX, boundingBox.maxX, facing, u, v);
    }

    protected int wz(int u, int v) {
        return SettlementLayoutSC.frameZ(boundingBox.minZ, boundingBox.maxZ, facing, u, v);
    }

    /** A block at (u, floor + dy, v) of the lot's frame. @return placed (inside the window) */
    protected boolean set(World w, StructureBoundingBox win, int u, int dy, int v, Block b, int meta) {
        return put(w, win, wx(u, v), floorY + dy, wz(u, v), b, meta);
    }

    /** A block from a stack (the trim's metal) at (u, floor + dy, v). */
    protected boolean set(World w, StructureBoundingBox win, int u, int dy, int v, ItemStack stack) {
        return stack != null && stack.getItem() != null && set(w, win, u, dy, v, Block.getBlockFromItem(stack.getItem()), stack.getItemDamage());
    }

    /** Air at (u, floor + dy, v) (only where something stands). */
    protected void clear(World w, StructureBoundingBox win, int u, int dy, int v) {
        int x = wx(u, v), y = floorY + dy, z = wz(u, v);
        if (y > 0 && y < 256 && win.isVecInside(x, y, z) && !w.isAirBlock(x, y, z)) {
            w.setBlockToAir(x, y, z);
        }
    }

    /**
     * A showcase machine of `meta` at (u, floor + dy, v), its front towards (du, dv) of the frame (0, 0: the default,
     * south) - BlockShowcaseSC, TileEntityShowcaseSC.
     */
    protected void showcase(World w, StructureBoundingBox win, int u, int dy, int v, int meta, int du, int dv) {
        if (SettlementBlocksSC.showcase == null || !set(w, win, u, dy, v, SettlementBlocksSC.showcase, meta)) {
            return;
        }
        int d = SettlementLayoutSC.frameDir(facing, du, dv);
        TileEntity te = w.getTileEntity(wx(u, v), floorY + dy, wz(u, v));
        if (d >= 0 && te instanceof TileEntityShowcaseSC) {
            ((TileEntityShowcaseSC) te).setFacing(SIDE_OF_DIR[d]);
        }
    }

    /** ForgeDirection ordinals of NORTH, EAST, SOUTH, WEST. */
    protected static final int[] SIDE_OF_DIR = {2, 5, 3, 4};

    /**
     * Sets one block if (x, y, z) is inside the window - flag 2, no neighbour updates; a block that gives light
     * (getLightValue by the world: a meta's light too) is lit by hand where setBlock gave up. The same block and meta
     * already there (Ф6's «Достройка» runs the pieces again, a piece built over another's block): nothing set - Forge
     * 1614's setBlock runs both light passes even for no change. @return placed (or already so)
     */
    public static boolean put(World w, StructureBoundingBox win, int x, int y, int z, Block b, int meta) {
        if (b == null || y < 1 || y > 255 || !win.isVecInside(x, y, z)) {
            return false;
        }
        if (w.getBlock(x, y, z) == b && w.getBlockMetadata(x, y, z) == meta) {
            return true;
        }
        w.setBlock(x, y, z, b, meta, 2);
        int light = b.getLightValue(w, x, y, z);
        if (light > 0) {
            ChunkProviderSubstrateSC.glow(w, x, y, z, light);
        }
        return true;
    }

    // ------------------------------------------------------------------ the palette (only this mod's blocks and vanilla's quartz, iron bars)
    // No valuable of the mod stands in a settlement: its metal, coils and lead glass are the trim (BlockSettlementDecorSC -
    // their looks, unbreakable, no drop), so no tool that breaks blocks past Forge's events takes them out of a dome.

    protected static Block rock() {
        return SubstrateBlocksSC.rock;
    }

    /** «Кирпич Поселения». */
    protected static final int BRICK = SubstrateBlocksSC.SETTLEMENT_BRICK;

    /** The trim that looks like a block of one of the mod's metals (SettlementBlocksSC.trim), or null. */
    protected static ItemStack metal(com.sc.util.Material m) {
        return SettlementBlocksSC.trim(m);
    }

    protected static ItemStack steel() {
        return metal(com.sc.util.Material.STEEL);
    }

    protected static ItemStack titanium() {
        return metal(com.sc.util.Material.TITANIUM);
    }

    /** The trim's lead glass (BlockSettlementDecorSC.Glass, meta 0). */
    protected static Block leadGlass() {
        return SettlementBlocksSC.glass;
    }

    /** The trim's Tokamak Coil. */
    protected static ItemStack coil() {
        return SettlementBlocksSC.decor == null ? null : new ItemStack(SettlementBlocksSC.decor, 1, BlockSettlementDecorSC.COIL);
    }

    // ------------------------------------------------------------------ helpers of the subclasses' NBT

    protected static int[] ints(NBTTagCompound nbt, String key, int n) {
        int[] a = nbt.getIntArray(key);
        return a.length == n ? a : new int[n];
    }
}
