package com.sc.ruin;

import java.util.Random;

import com.sc.block.BlockRuinSC;
import com.sc.mob.RareRegistrySC;

import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE C (docs/plan-substrate-tech-f4.md §8.6, rules ПХ-2, ПХ-3): a piece of a Vault - "SCSi14VltPart" is this base piece (it
 * builds nothing; MapGenStructureIO wants a class per name). It keeps the Vault's place (NBT "Vault": its crossing i, j, the
 * bunker's middle column, its floor's Y, the Dead Node plaza's floor over it) and the helpers of its two pieces - the bunker
 * (ComponentVaultBunkerSC) and the sealed shaft (ComponentVaultShaftSC). As every ruin piece: only inside the populate window, by
 * the pure heights, from its own seed (ComponentRuinSC).
 */
public class ComponentVaultSC extends ComponentRuinSC {

    /** The ladder's meta: on the north side of its block, held by the block at z - 1 (BlockLadder) - the shaft's ring, the pillar. */
    protected static final int LADDER_NORTH = 3;

    /** The crossing, the bunker's middle, its floor, the plaza's floor. */
    protected int i, j, mx, mz, floorY, plazaY;

    public ComponentVaultSC() {
    }

    protected ComponentVaultSC(long seed, int i, int j, int floorY, int plazaY, StructureBoundingBox box) {
        super(seed, box);
        this.i = i;
        this.j = j;
        this.mx = i * com.sc.dim.SubstrateTerrainSC.GRID;
        this.mz = j * com.sc.dim.SubstrateTerrainSC.GRID;
        this.floorY = floorY;
        this.plazaY = plazaY;
    }

    @Override
    protected void func_143012_a(NBTTagCompound nbt) {
        super.func_143012_a(nbt);
        nbt.setIntArray("Vault", new int[]{i, j, mx, mz, floorY, plazaY});
    }

    @Override
    protected void func_143011_b(NBTTagCompound nbt) {
        super.func_143011_b(nbt);
        int[] a = nbt.getIntArray("Vault");
        if (a.length == 6) {
            i = a[0];
            j = a[1];
            mx = a[2];
            mz = a[3];
            floorY = a[4];
            plazaY = a[5];
        }
    }

    /** The Vault's registry key (RareRegistrySC.vaultKey: its Sentinel's record). */
    public String key() {
        return RareRegistrySC.vaultKey(i, j);
    }

    /** The bunker's ceiling (its top block, plating). */
    public int ceilingY() {
        return floorY + VaultLayoutSC.HEIGHT - 1;
    }

    public int floorY() {
        return floorY;
    }

    /** The bunker's middle on its floor {x, floorY + 1, z}. */
    public int[] middle() {
        return new int[]{mx, floorY + 1, mz};
    }

    /** A block of vault plating. */
    protected boolean plating(World w, StructureBoundingBox win, int x, int y, int z) {
        return ruin(w, win, x, y, z, BlockRuinSC.VAULT_PLATING);
    }

    /**
     * A Vault's loot chest (vanilla's) facing `facing`, filled by RuinLootSC.fillVault from its own seed `chestSeed`. @return placed
     */
    protected boolean vaultChest(World w, StructureBoundingBox win, int x, int y, int z, int facing, long chestSeed) {
        if (!set(w, win, x, y, z, Blocks.chest, facing)) {
            return false;
        }
        TileEntity te = w.getTileEntity(x, y, z);
        if (te instanceof IInventory) {
            RuinLootSC.fillVault((IInventory) te, new Random(chestSeed));
        }
        return true;
    }
}
