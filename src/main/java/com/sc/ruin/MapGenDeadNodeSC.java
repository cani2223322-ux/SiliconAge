package com.sc.ruin;

import java.util.ArrayList;
import java.util.List;

import com.sc.dim.SubstrateTerrainSC;

import cpw.mods.fml.common.FMLLog;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureStart;

/**
 * PACKAGE X (docs/plan-substrate-tech-f3.md §8.2, rules ПУ-1..ПУ-3): the Dead Nodes - a vanilla structure, one start at every
 * Scribe crossing (RuinLayoutSC.nodeAtChunk), driven by ChunkProviderSubstrateSC (provideChunk / recreateStructures make the
 * starts, populate builds them after the ores, traces and druses, before the settlements). As the settlements (ПП-2): a start
 * whose ground was explored before (a chunk of its box on disk already - a world of Ф1 / Ф2) is EMPTY - no half ruin.
 * MapGenStructureIO names: the start START_NAME, the pieces PIECE_PREFIX + their name ("SCSi14NodePart" is the base piece).
 * <p>
 * A Node's start: the plaza (ComponentNodePlazaSC) and the mouths with the walls' panels (ComponentNodeMouthsSC), its area
 * +-RuinLayoutSC.NODE_HALF round the crossing - 4 x 4 chunks, all within MapGenBase's reach of 8 from the start's chunk, so in
 * new land the start is always made before any of them. The start keeps its crossing (NBT NodeI / NodeJ).
 */
public class MapGenDeadNodeSC extends MapGenStructure {

    public static final String START_NAME = "SCSi14DeadNode";
    public static final String PIECE_PREFIX = "SCSi14Node";

    @Override
    public String func_143025_a() {
        return START_NAME;
    }

    @Override
    protected boolean canSpawnStructureAtCoords(int cx, int cz) {
        return worldObj != null && RuinSC.generates() && RuinLayoutSC.nodeAtChunk(cx, cz) != null;
    }

    @Override
    protected StructureStart getStructureStart(int cx, int cz) {
        int[] n = RuinLayoutSC.nodeAtChunk(cx, cz);
        if (n == null) {
            return new StructureDeadNodeStartSC(cx, cz);
        }
        int x = n[0] * SubstrateTerrainSC.GRID, z = n[1] * SubstrateTerrainSC.GRID, r = RuinLayoutSC.NODE_HALF;
        if (StructureRuinStartSC.explored(worldObj, x - r, z - r, x + r, z + r)) {   // ПУ-3: old land - no half-built ruin
            FMLLog.info("[Silicon Age] Si-14 Substrate: no Dead Node ruins at %d %d - its ground was explored before (world of 0.2.1 or older)", x, z);
            StructureDeadNodeStartSC empty = new StructureDeadNodeStartSC(cx, cz);
            empty.nodeI = n[0];
            empty.nodeJ = n[1];
            return empty;
        }
        return new StructureDeadNodeStartSC(cx, cz, n[0], n[1], pieces(worldObj.getSeed(), n[0], n[1]));
    }

    /** The pieces of the Node at crossing (i, j): the plaza, then the mouths and walls (pure). */
    public static List<StructureComponent> pieces(long worldSeed, int i, int j) {
        int x = i * SubstrateTerrainSC.GRID, z = j * SubstrateTerrainSC.GRID;
        int hx = SubstrateTerrainSC.canyonHalfWidth(worldSeed, i), hz = SubstrateTerrainSC.canyonHalfWidth(~worldSeed, j);
        int floor = SubstrateTerrainSC.nodeFloorY(worldSeed, i, j);
        long seed = RuinLayoutSC.nodeSeed(worldSeed, i, j);
        List<StructureComponent> l = new ArrayList<StructureComponent>();
        l.add(new ComponentNodePlazaSC(seed, x, z, floor, hx, hz));
        l.add(new ComponentNodeMouthsSC(seed, x, z, floor, ComponentNodePlazaSC.half(hx, hz)));
        return l;
    }

    /**
     * The block column (x, z) lies in a built Node's box widened by `margin` (traces, druses and ponds
     * keep out of it - ChunkProviderSubstrateSC asks). Server; false before the generator has seen a world.
     */
    public boolean covers(int x, int z, int margin) {
        if (worldObj == null || SubstrateTerrainSC.distanceToLine(x) > RuinLayoutSC.NODE_HALF + margin
                || SubstrateTerrainSC.distanceToLine(z) > RuinLayoutSC.NODE_HALF + margin) {
            return false;
        }
        int g = SubstrateTerrainSC.GRID >> 4;
        Object st = structureMap.get(Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(SubstrateTerrainSC.lineIndex(x) * g, SubstrateTerrainSC.lineIndex(z) * g)));
        return st instanceof StructureStart && ((StructureStart) st).isSizeableStructure()
                && ((StructureStart) st).getBoundingBox().intersectsWith(x - margin, z - margin, x + margin, z + margin);
    }

    /** The start made at chunk (cx, cz) (a crossing's chunk), or null (none made yet) - the world test asks. Server. */
    public StructureRuinStartSC startAt(int cx, int cz) {
        Object st = structureMap.get(Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(cx, cz)));
        return st instanceof StructureRuinStartSC ? (StructureRuinStartSC) st : null;
    }

    /** RuinSC.init: the start and the pieces (names fixed once used - append only). */
    public static void registerStructures() {
        MapGenStructureIO.registerStructure(StructureDeadNodeStartSC.class, START_NAME);
        MapGenStructureIO.func_143031_a(ComponentRuinSC.class, PIECE_PREFIX + "Part");
        MapGenStructureIO.func_143031_a(ComponentNodePlazaSC.class, PIECE_PREFIX + "Plaza");
        MapGenStructureIO.func_143031_a(ComponentNodeMouthsSC.class, PIECE_PREFIX + "Mouths");
    }

    /** The start class of this structure (MapGenStructureIO wants one per name); its crossing in NBT NodeI / NodeJ. */
    public static class StructureDeadNodeStartSC extends StructureRuinStartSC {
        /** The crossing (line numbers). */
        public int nodeI, nodeJ;

        public StructureDeadNodeStartSC() {
        }

        public StructureDeadNodeStartSC(int cx, int cz) {
            super(cx, cz);
        }

        public StructureDeadNodeStartSC(int cx, int cz, int i, int j, List<StructureComponent> parts) {
            super(cx, cz, parts);
            nodeI = i;
            nodeJ = j;
        }

        @Override
        public void func_143022_a(NBTTagCompound nbt) {
            super.func_143022_a(nbt);
            nbt.setInteger("NodeI", nodeI);
            nbt.setInteger("NodeJ", nodeJ);
        }

        @Override
        public void func_143017_b(NBTTagCompound nbt) {
            super.func_143017_b(nbt);
            nodeI = nbt.getInteger("NodeI");
            nodeJ = nbt.getInteger("NodeJ");
        }
    }
}
