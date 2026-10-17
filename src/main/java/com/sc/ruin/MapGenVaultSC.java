package com.sc.ruin;

import java.util.ArrayList;
import java.util.List;

import com.sc.dim.ChunkProviderSubstrateSC;
import com.sc.dim.SubstrateTerrainSC;

import cpw.mods.fml.common.FMLLog;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureStart;

/**
 * PACKAGE C (docs/plan-substrate-tech-f4.md §8.5, rules ПХ-1..ПХ-6): the Vaults under the Dead Nodes - a vanilla structure, a
 * start at the crossing's chunk of every crossing with a Vault (VaultLayoutSC.hasVault). Empty where the Node's ground was
 * explored before (no half Vault). Built in populate after the Nodes (its sealed hatch replaces the plaza's middle): the bunker
 * of vault plating (ruinSC vaultPlating; a sealed bunker - you dig in), lamps, a chest or two (RuinLootSC: a Sealed Crumb on
 * chance), and its Singular Sentinel placed once (born in Si-14, persistent, its home the bunker + 24 - RareRegistrySC.vaultKey).
 * MapGenStructureIO: START_NAME, the pieces PIECE_PREFIX + name (the base "SCSi14VltPart").
 * <p>
 * Fixed (the coordinator's): START_NAME, PIECE_PREFIX (MapGenStructureIO names - append only), covers(), the start class and its
 * NBT keys, the calls from ChunkProviderSubstrateSC (provideChunk / recreateStructures make the starts, populate builds them).
 * The rest is package C's.
 * <p>
 * A Vault's start: the bunker (ComponentVaultBunkerSC, "SCSi14VltBunker") and the sealed shaft up to the plaza
 * (ComponentVaultShaftSC, "SCSi14VltShaft"); the explored check is the Dead Node's own (its whole zone, +-RuinLayoutSC.NODE_HALF),
 * made in the same provideChunk as the Node's start - so a Node is built with its Vault or both are empty. A Node of an older
 * world (its plaza built before Ф4) gets no Vault.
 */
public class MapGenVaultSC extends MapGenStructure {

    public static final String START_NAME = "SCSi14Vault";
    public static final String PIECE_PREFIX = "SCSi14Vlt";
    /** A Vault's own seed's salt (its chests, its layout). */
    private static final long SALT = 0x7A017B11L;

    @Override
    public String func_143025_a() {
        return START_NAME;
    }

    @Override
    protected boolean canSpawnStructureAtCoords(int cx, int cz) {
        return worldObj != null && RuinSC.generates() && vaultAtChunk(worldObj.getSeed(), cx, cz) != null;
    }

    /** The crossing {i, j} whose Vault's start chunk is (cx, cz) - a crossing's chunk with a Vault - or null (pure). */
    public static int[] vaultAtChunk(long seed, int cx, int cz) {
        int[] n = RuinLayoutSC.nodeAtChunk(cx, cz);
        return n != null && VaultLayoutSC.hasVault(seed, n[0], n[1]) ? n : null;
    }

    @Override
    protected StructureStart getStructureStart(int cx, int cz) {
        int[] n = vaultAtChunk(worldObj.getSeed(), cx, cz);
        if (n == null) {
            return new StructureVaultStartSC(cx, cz);
        }
        int x = n[0] * SubstrateTerrainSC.GRID, z = n[1] * SubstrateTerrainSC.GRID, r = RuinLayoutSC.NODE_HALF;
        StructureVaultStartSC st;
        if (StructureRuinStartSC.explored(worldObj, x - r, z - r, x + r, z + r)) {   // the Node's own check: no Vault under an old plaza
            FMLLog.info("[Silicon Age] Si-14 Substrate: no Vault under the Dead Node at %d %d - its ground was explored before (world of an older version)", x, z);
            st = new StructureVaultStartSC(cx, cz);
        } else {
            st = new StructureVaultStartSC(cx, cz, pieces(worldObj.getSeed(), n[0], n[1]));
        }
        st.vaultI = n[0];
        st.vaultJ = n[1];
        return st;
    }

    /** The pieces of the Vault under crossing (i, j): the bunker, then the shaft (pure). */
    public static List<StructureComponent> pieces(long worldSeed, int i, int j) {
        long seed = RuinLayoutSC.mix(RuinLayoutSC.nodeSeed(worldSeed, i, j), SALT, 0);
        int floor = VaultLayoutSC.floorY(worldSeed, i, j), plaza = SubstrateTerrainSC.nodeFloorY(worldSeed, i, j);
        List<StructureComponent> l = new ArrayList<StructureComponent>();
        l.add(new ComponentVaultBunkerSC(seed, i, j, floor, plaza));
        l.add(new ComponentVaultShaftSC(seed, i, j, floor, plaza));
        return l;
    }

    /**
     * The block column (x, z) lies in a built Vault's box widened by `margin` (the generator keeps its traces, druses and ponds,
     * the carver its vias, out of it). Server; false before the generator has seen a world.
     */
    public boolean covers(int x, int z, int margin) {
        if (worldObj == null) {
            return false;
        }
        int[] v = VaultLayoutSC.vaultNear(worldObj.getSeed(), x, z, margin);
        if (v == null) {
            return false;
        }
        int g = SubstrateTerrainSC.GRID >> 4;
        Object st = structureMap.get(Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(v[0] * g, v[1] * g)));
        return st instanceof StructureStart && ((StructureStart) st).isSizeableStructure()
                && ((StructureStart) st).getBoundingBox().intersectsWith(x - margin, z - margin, x + margin, z + margin);
    }

    /** The start made at chunk (cx, cz) (a crossing's chunk), or null (none made yet) - the world test and the pelengator ask. Server. */
    public StructureRuinStartSC startAt(int cx, int cz) {
        Object st = structureMap.get(Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(cx, cz)));
        return st instanceof StructureRuinStartSC ? (StructureRuinStartSC) st : null;
    }

    /**
     * Crossing (i, j) has a Vault being built or built (its start isn't an empty one) - the Dead Node's plaza leaves its middle
     * free for the hatch then (ComponentNodePlazaSC). Server; the start not made yet / another mod's generator: the pure answer.
     */
    public static boolean builtAt(World w, int i, int j) {
        if (w == null || !VaultLayoutSC.hasVault(w.getSeed(), i, j)) {
            return false;
        }
        ChunkProviderSubstrateSC gen = ItemRuinFinderSC.generator(w);
        int g = SubstrateTerrainSC.GRID >> 4;
        StructureRuinStartSC st = gen == null ? null : gen.vaults().startAt(i * g, j * g);
        return st == null || st.isSizeableStructure();
    }

    /** RuinSC.init (both sides, always): the start and the pieces (names fixed once used - append only). */
    public static void registerStructures() {
        MapGenStructureIO.registerStructure(StructureVaultStartSC.class, START_NAME);
        MapGenStructureIO.func_143031_a(ComponentVaultSC.class, PIECE_PREFIX + "Part");
        MapGenStructureIO.func_143031_a(ComponentVaultBunkerSC.class, PIECE_PREFIX + "Bunker");
        MapGenStructureIO.func_143031_a(ComponentVaultShaftSC.class, PIECE_PREFIX + "Shaft");
    }

    /** The start class of this structure (MapGenStructureIO wants one per name); its crossing in NBT VaultI / VaultJ. */
    public static class StructureVaultStartSC extends StructureRuinStartSC {
        /** The crossing (line numbers) the Vault lies under. */
        public int vaultI, vaultJ;

        public StructureVaultStartSC() {
        }

        public StructureVaultStartSC(int cx, int cz) {
            super(cx, cz);
        }

        public StructureVaultStartSC(int cx, int cz, List<StructureComponent> parts) {
            super(cx, cz, parts);
        }

        @Override
        public void func_143022_a(NBTTagCompound nbt) {
            super.func_143022_a(nbt);
            nbt.setInteger("VaultI", vaultI);
            nbt.setInteger("VaultJ", vaultJ);
        }

        @Override
        public void func_143017_b(NBTTagCompound nbt) {
            super.func_143017_b(nbt);
            vaultI = nbt.getInteger("VaultI");
            vaultJ = nbt.getInteger("VaultJ");
        }
    }
}
