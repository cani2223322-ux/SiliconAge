package com.sc.ruin;

import java.util.Random;

import com.sc.block.BlockRuinSC;
import com.sc.dim.ChunkProviderSubstrateSC;
import com.sc.dim.SubstrateBlocksSC;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;

/**
 * PACKAGE X (docs/plan-substrate-tech-f3.md §8.3): a piece of a ruin, built only inside the populate window it is handed
 * (StructureComponent's helpers clip to it; nothing outside is read or set - no cascade), with flag 2, by the pure terrain
 * heights; never Blocks.stone; a light-giving block lit by hand where setBlock gave up (ChunkProviderSubstrateSC.glow).
 * addComponentParts always returns true (false drops the piece for good). A public constructor without arguments
 * (MapGenStructureIO). This base piece is "SCSi14NodePart" and builds nothing; Lit ("SCSi14LitPart") is the lithographs'.
 * <p>
 * Everything a piece builds comes from its own seed (NBT "Seed") and the block's position - never from the populate
 * Random - so every window builds its share of the same ruin, in any order. A block goes in only inside both the window and
 * the piece's box; the world is read only inside the window (one block past it at most - a chest looks at its neighbours,
 * and the window's four chunks reach a block further on every side).
 */
public class ComponentRuinSC extends StructureComponent {

    /** The ruin's own seed (its layout, its chests). */
    protected long seed;

    public ComponentRuinSC() {
    }

    /** A piece of the ruin of `seed` in `box`. */
    protected ComponentRuinSC(long seed, StructureBoundingBox box) {
        super(0);
        this.seed = seed;
        this.boundingBox = box;
    }

    @Override
    protected void func_143012_a(NBTTagCompound nbt) {
        nbt.setLong("Seed", seed);
    }

    @Override
    protected void func_143011_b(NBTTagCompound nbt) {
        seed = nbt.getLong("Seed");
    }

    @Override
    public boolean addComponentParts(World world, Random rand, StructureBoundingBox window) {
        if (SubstrateBlocksSC.rock != null && RuinBlocksSC.ruin != null) {
            build(world, window);
        }
        return true;                                    // never false: that would drop the piece for good
    }

    /** The piece's part inside `window` (the subclasses'). */
    protected void build(World w, StructureBoundingBox win) {
    }

    // ------------------------------------------------------------------ setting blocks

    /** Sets a block inside the window and the piece's box (flag 2; a light-giving one lit by hand). @return placed */
    protected boolean set(World w, StructureBoundingBox win, int x, int y, int z, Block b, int meta) {
        return boundingBox.isVecInside(x, y, z) && put(w, win, x, y, z, b, meta);
    }

    /** A ruin block (BlockRuinSC meta). */
    protected boolean ruin(World w, StructureBoundingBox win, int x, int y, int z, int meta) {
        return set(w, win, x, y, z, RuinBlocksSC.ruin, meta);
    }

    /** The Substrate's rock (SubstrateBlocksSC meta) - Crystallite, the crust's bands, a glowing Trace Node. */
    protected boolean rock(World w, StructureBoundingBox win, int x, int y, int z, int meta) {
        return set(w, win, x, y, z, SubstrateBlocksSC.rock, meta);
    }

    /** Air (only where something stands). */
    protected void air(World w, StructureBoundingBox win, int x, int y, int z) {
        if (y > 0 && y < 256 && win.isVecInside(x, y, z) && boundingBox.isVecInside(x, y, z) && !w.isAirBlock(x, y, z)) {
            w.setBlock(x, y, z, Blocks.air, 0, 2);
        }
    }

    /** The block at (x, y, z) inside the window is ground to stand on (null outside it: never read). */
    protected static boolean solid(World w, StructureBoundingBox win, int x, int y, int z) {
        if (y < 1 || y > 255 || !win.isVecInside(x, y, z)) {
            return false;
        }
        Material m = w.getBlock(x, y, z).getMaterial();
        return m.isSolid() && !m.isLiquid();
    }

    /** The block at (x, y, z) inside the window is the Substrate's own rock (a wall's face, the floor) - not air, a crystal, water. */
    protected static boolean isRock(World w, StructureBoundingBox win, int x, int y, int z) {
        return y >= 1 && y <= 255 && win.isVecInside(x, y, z) && w.getBlock(x, y, z) == SubstrateBlocksSC.rock;
    }

    /**
     * A loot chest (vanilla's) facing `facing` (2 north, 3 south, 4 west, 5 east), filled by RuinLootSC from its own seed
     * `chestSeed` (the lithograph's table or the Dead Node's). @return placed
     */
    protected boolean chest(World w, StructureBoundingBox win, int x, int y, int z, int facing, boolean litho, long chestSeed) {
        if (!set(w, win, x, y, z, Blocks.chest, facing)) {
            return false;
        }
        TileEntity te = w.getTileEntity(x, y, z);
        if (te instanceof IInventory) {
            Random r = new Random(chestSeed);
            if (litho) {
                RuinLootSC.fillLitho((IInventory) te, r);
            } else {
                RuinLootSC.fillNode((IInventory) te, r);
            }
        }
        return true;
    }

    /**
     * Sets one block if (x, y, z) is inside the window - flag 2, no neighbour updates; a block that gives light (by the
     * world: a meta's light too) is lit by hand where setBlock gave up. @return placed
     */
    public static boolean put(World w, StructureBoundingBox win, int x, int y, int z, Block b, int meta) {
        if (b == null || y < 1 || y > 255 || !win.isVecInside(x, y, z)) {
            return false;
        }
        w.setBlock(x, y, z, b, meta, 2);
        int light = b.getLightValue(w, x, y, z);
        if (light > 0) {
            ChunkProviderSubstrateSC.glow(w, x, y, z, light);
        }
        return true;
    }

    // ------------------------------------------------------------------ the ruin's own randomness

    /** 0..99 for the block (x, y, z) and `salt` of this ruin - the same in every window. */
    protected int roll(int x, int y, int z, int salt) {
        return RuinLayoutSC.pick(RuinLayoutSC.mix(seed ^ salt * 0x5DEECE66DL, x * 31L + y, z), 100);
    }

    /** A Random of this ruin and `salt` (its layout: the same list of things in every window). */
    protected Random layout(int salt) {
        return new Random(RuinLayoutSC.mix(seed, salt, 0x1A40L));
    }

    /** The meta of a fallen piece of rubble: Crystallite most, ruin metal, a burnt panel, now and then a machine's husk (BlockRuinSC / rock). */
    protected static boolean rubble(ComponentRuinSC p, World w, StructureBoundingBox win, int x, int y, int z, int r) {
        if (r < 45) {
            return p.rock(w, win, x, y, z, SubstrateBlocksSC.CRYSTALLITE);
        }
        if (r < 75) {
            return p.ruin(w, win, x, y, z, BlockRuinSC.RUIN_METAL);
        }
        return p.ruin(w, win, x, y, z, r < 93 ? BlockRuinSC.BURNT_PANEL : BlockRuinSC.MACHINE_HUSK);
    }

    /** The chest's facing towards (tx, tz) from (x, z): 2 north, 3 south, 4 west, 5 east. */
    protected static int facingTo(int x, int z, int tx, int tz) {
        int dx = tx - x, dz = tz - z;
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx > 0 ? 5 : 4;
        }
        return dz > 0 ? 3 : 2;
    }

    /** The lithographs' base piece (its own MapGenStructureIO name). */
    public static class Lit extends ComponentRuinSC {
        public Lit() {
        }

        protected Lit(long seed, StructureBoundingBox box) {
            super(seed, box);
        }
    }
}
