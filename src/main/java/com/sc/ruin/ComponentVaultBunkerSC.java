package com.sc.ruin;

import java.util.Random;

import com.sc.block.BlockRuinSC;

import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE C (docs/plan-substrate-tech-f4.md §8.6, rules ПХ-2, ПХ-3): a Vault's sealed bunker - "SCSi14VltBunker" - under the Dead
 * Node's plaza (VaultLayoutSC: +-HALF round the crossing, walls included, its floor DEPTH under the plaza's, HEIGHT high, floor and
 * ceiling included):
 * <ul>
 * <li>the shell all of vault plating (ruinSC vaultPlating - a diamond pickaxe and patience: no door, no soft spot), four dim
 * lamps in the ceiling (vaultLamp), the room inside cleared of whatever rock, ore or cave was there;</li>
 * <li>the ladder down the middle from the sealed ceiling (the shaft's foot - ComponentVaultShaftSC) to the floor, on a plating
 * pillar; racks of machine and tank husks along the east and west walls;</li>
 * <li>RuinLootSC.VAULT_CHESTS_MIN..MAX chests by the north and south walls (RuinLootSC.fillVault);</li>
 * <li>ПХ-3, once (NBT "Sentinel"), in the populate window holding the middle - its chunks are loaded then: the Singular
 * Sentinel at a corner of the room (RuinWorldSC.sentinel: born in Si-14, persistent, its home the bunker - scHome, the leash
 * 24 past it - into RareRegistrySC under the Vault's key); the Vault's record keeps the spot for its return (ПХ-4).</li>
 * </ul>
 */
public class ComponentVaultBunkerSC extends ComponentVaultSC {

    /** The ceiling's lamps (dx, dz). */
    public static final int[][] LAMPS = {{-4, -4}, {4, -4}, {-4, 4}, {4, 4}};
    /** The racks along the east and west walls (dz), every other one with a tank husk on top. */
    public static final int[] RACK_DZ = {-5, -2, 2, 5};
    /** The chests' places (dx, dz from the middle; by the north and south walls). */
    public static final int[][] CHESTS = {{-4, -(VaultLayoutSC.HALF - 1)}, {4, VaultLayoutSC.HALF - 1}, {4, -(VaultLayoutSC.HALF - 1)},
            {-4, VaultLayoutSC.HALF - 1}};
    /** Where the Sentinel may stand (dx, dz) - the first one inside the middle's window. */
    public static final int[][] SENTINEL_AT = {{-3, 3}, {3, 3}, {-3, -3}, {3, -3}};

    private boolean sentinel;

    public ComponentVaultBunkerSC() {
    }

    public ComponentVaultBunkerSC(long seed, int i, int j, int floorY, int plazaY) {
        super(seed, i, j, floorY, plazaY, new StructureBoundingBox(i * com.sc.dim.SubstrateTerrainSC.GRID - VaultLayoutSC.HALF, floorY,
                j * com.sc.dim.SubstrateTerrainSC.GRID - VaultLayoutSC.HALF, i * com.sc.dim.SubstrateTerrainSC.GRID + VaultLayoutSC.HALF,
                floorY + VaultLayoutSC.HEIGHT - 1, j * com.sc.dim.SubstrateTerrainSC.GRID + VaultLayoutSC.HALF));
    }

    @Override
    protected void func_143012_a(NBTTagCompound nbt) {
        super.func_143012_a(nbt);
        nbt.setBoolean("Sentinel", sentinel);
    }

    @Override
    protected void func_143011_b(NBTTagCompound nbt) {
        super.func_143011_b(nbt);
        sentinel = nbt.getBoolean("Sentinel");
    }

    /** The Sentinel was placed (the world test asks). */
    public boolean sentinelMade() {
        return sentinel;
    }

    @Override
    protected void build(World w, StructureBoundingBox win) {
        int h = VaultLayoutSC.HALF, top = ceilingY();
        int x0 = Math.max(boundingBox.minX, win.minX), x1 = Math.min(boundingBox.maxX, win.maxX);
        int z0 = Math.max(boundingBox.minZ, win.minZ), z1 = Math.min(boundingBox.maxZ, win.maxZ);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int dx = x - mx, dz = z - mz;
                boolean wall = Math.abs(dx) == h || Math.abs(dz) == h;
                for (int y = floorY; y <= top; y++) {
                    if (y == top && !wall && lamp(dx, dz)) {
                        ruin(w, win, x, y, z, BlockRuinSC.VAULT_LAMP);
                    } else if (y == floorY || y == top || wall) {
                        plating(w, win, x, y, z);
                    } else {
                        air(w, win, x, y, z);
                    }
                }
            }
        }
        // the ladder down the middle on its pillar (the shaft's ring holds it above the ceiling)
        for (int y = floorY + 1; y < top; y++) {
            plating(w, win, mx, y, mz - 1);
            set(w, win, mx, y, mz, Blocks.ladder, LADDER_NORTH);
        }
        racks(w, win);
        chests(w, win);
        if (!sentinel && win.isVecInside(mx, floorY + 1, mz)) {
            sentinel = true;                                    // once, whatever happens below (the start is saved after this window)
            sentinel(w, win);
        }
    }

    private static boolean lamp(int dx, int dz) {
        for (int[] l : LAMPS) {
            if (l[0] == dx && l[1] == dz) {
                return true;
            }
        }
        return false;
    }

    /** The racks: a machine husk by the east and west walls at each RACK_DZ, a tank husk on every other one. */
    private void racks(World w, StructureBoundingBox win) {
        int in = VaultLayoutSC.HALF - 1;
        for (int side = -1; side <= 1; side += 2) {
            for (int k = 0; k < RACK_DZ.length; k++) {
                int x = mx + side * in, z = mz + RACK_DZ[k];
                ruin(w, win, x, floorY + 1, z, BlockRuinSC.MACHINE_HUSK);
                if ((k + (side > 0 ? 1 : 0)) % 2 == 0) {
                    ruin(w, win, x, floorY + 2, z, BlockRuinSC.TANK_HUSK);
                }
            }
        }
    }

    /** The chests: their count from the seed, their places by the north and south walls, facing the room. */
    private void chests(World w, StructureBoundingBox win) {
        Random r = layout(5);
        int n = RuinLootSC.VAULT_CHESTS_MIN + r.nextInt(RuinLootSC.VAULT_CHESTS_MAX - RuinLootSC.VAULT_CHESTS_MIN + 1);
        for (int c = 0; c < n && c < CHESTS.length; c++) {
            int x = mx + CHESTS[c][0], z = mz + CHESTS[c][1];
            vaultChest(w, win, x, floorY + 1, z, CHESTS[c][1] < 0 ? 3 : 2, RuinLayoutSC.mix(seed, c, 0x7A1C4EL));
        }
    }

    /** ПХ-3: the Sentinel at the first of SENTINEL_AT inside the window (one always is: the window holds the middle). */
    private void sentinel(World w, StructureBoundingBox win) {
        int[] spot = null;
        for (int[] s : SENTINEL_AT) {
            int x = mx + s[0], z = mz + s[1];
            if (win.isVecInside(x, floorY + 1, z) && win.isVecInside(x, floorY + 3, z) && w.isAirBlock(x, floorY + 1, z)
                    && w.isAirBlock(x, floorY + 2, z) && w.isAirBlock(x, floorY + 3, z)) {
                spot = new int[]{x, floorY + 1, z};
                break;
            }
        }
        int[] home = {mx, floorY + 1, mz, VaultLayoutSC.HALF};
        RuinWorldSC.vaultMade(w, key(), middle(), spot, home[3]);
        if (spot != null && w instanceof WorldServer) {
            RuinWorldSC.sentinel((WorldServer) w, key(), spot[0], spot[1], spot[2], home);
        }
    }
}
