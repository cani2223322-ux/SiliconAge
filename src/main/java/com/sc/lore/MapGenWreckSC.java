package com.sc.lore;

import java.util.ArrayList;
import java.util.List;

import com.sc.arena.ArenaSC;
import com.sc.ruin.StructureRuinStartSC;

import cpw.mods.fml.common.FMLLog;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureStart;

/**
 * PACKAGE J (docs/plan-substrate-tech-f5.md §6.2, rule ПЛ-3): the Lithographers' bridge wrecks - a vanilla structure, one start at
 * each wreck's middle chunk (WreckLayoutSC.siteAtChunk), driven by ChunkProviderSubstrateSC (the coordinator's lines): its starts made
 * with the others in provideChunk, built in populate after the arenas, before the settlements. As every ruin (ПУ-3): a start whose
 * ground was explored before is EMPTY. Built by the pure terrace heights (SubstrateTerrainSC.terraceY - a plateau cell is always Ф1's
 * ground), inside the populate window only, with ComponentRuinSC's rules (its own seed, flag 2, no Blocks.stone, the light by hand).
 * MapGenStructureIO names: the start START_NAME, the pieces PIECE_PREFIX + their name. The start keeps its cell (WreckCX / WreckCZ).
 * <p>
 * Fixed (the coordinator's): the class, the names, the start class and its NBT, covers / startAt, registerStructures (J appends its
 * pieces). The bodies are J's.
 * <p>
 * J: the start - one piece, the whole wreck (ComponentWreckSC, "SCSi14WrkPart"): its box HALF each way round the middle, 2 x 2 or
 * 3 x 3 chunks, all within MapGenBase's reach of 8 from the start's chunk, so in new land the start is always made before any of
 * them. A world of Ф1..Ф4 whose plateau there was walked already gets no wreck at that spot (an empty start, a line in the log).
 */
public class MapGenWreckSC extends MapGenStructure {

    public static final String START_NAME = "SCSi14Wreck";
    public static final String PIECE_PREFIX = "SCSi14Wrk";

    @Override
    public String func_143025_a() {
        return START_NAME;
    }

    /** New wrecks generate (ArenaSC.generates) and chunk (cx, cz) holds a wreck's middle (WreckLayoutSC.siteAtChunk). */
    @Override
    protected boolean canSpawnStructureAtCoords(int cx, int cz) {
        return worldObj != null && ArenaSC.generates() && WreckLayoutSC.siteAtChunk(worldObj.getSeed(), cx, cz) != null;
    }

    /** ПЛ-3: the ground of the wreck's box explored before - an empty start (its cell kept), else the wreck's piece. */
    @Override
    protected StructureStart getStructureStart(int cx, int cz) {
        WreckLayoutSC.Site s = WreckLayoutSC.siteAtChunk(worldObj.getSeed(), cx, cz);
        if (s == null) {
            return new StructureWreckStartSC(cx, cz);
        }
        int h = WreckLayoutSC.HALF;
        if (StructureRuinStartSC.explored(worldObj, s.x - h, s.z - h, s.x + h, s.z + h)) {   // ПУ-3: old land - no half-built wreck
            FMLLog.info("[Silicon Age] Si-14 Substrate: no Lithographers' bridge wreck at %d %d - its ground was explored before (world of an older version)",
                    s.x, s.z);
            StructureWreckStartSC empty = new StructureWreckStartSC(cx, cz);
            empty.cellX = s.cellX;
            empty.cellZ = s.cellZ;
            return empty;
        }
        return new StructureWreckStartSC(cx, cz, s.cellX, s.cellZ, pieces(s));
    }

    /** The pieces of wreck `s`: the whole wreck in one (pure). */
    public static List<StructureComponent> pieces(WreckLayoutSC.Site s) {
        List<StructureComponent> l = new ArrayList<StructureComponent>();
        l.add(new ComponentWreckSC(s));
        return l;
    }

    /** The block column (x, z) lies in a built wreck's box widened by `margin` (traces, druses, ponds, vias keep out). Server. */
    public boolean covers(int x, int z, int margin) {
        if (worldObj == null) {
            return false;
        }
        WreckLayoutSC.Site s = WreckLayoutSC.siteNear(worldObj.getSeed(), x, z, margin);
        if (s == null) {
            return false;
        }
        Object st = structureMap.get(Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(s.x >> 4, s.z >> 4)));
        return st instanceof StructureStart && ((StructureStart) st).isSizeableStructure();
    }

    /** The start made at chunk (cx, cz), or null (none made yet) - the world test asks. Server. */
    public StructureRuinStartSC startAt(int cx, int cz) {
        Object st = structureMap.get(Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(cx, cz)));
        return st instanceof StructureRuinStartSC ? (StructureRuinStartSC) st : null;
    }

    /** ArenaSC.init: the start and the pieces (names fixed once used - append only). J appends its pieces. */
    public static void registerStructures() {
        MapGenStructureIO.registerStructure(StructureWreckStartSC.class, START_NAME);
        MapGenStructureIO.func_143031_a(ComponentWreckSC.class, PIECE_PREFIX + "Part");      // J: the whole wreck
    }

    /** The start class of this structure; its cell in NBT WreckCX / WreckCZ. */
    public static class StructureWreckStartSC extends StructureRuinStartSC {
        public int cellX, cellZ;

        public StructureWreckStartSC() {
        }

        public StructureWreckStartSC(int cx, int cz) {
            super(cx, cz);
        }

        public StructureWreckStartSC(int cx, int cz, int cellX, int cellZ, List<StructureComponent> parts) {
            super(cx, cz, parts);
            this.cellX = cellX;
            this.cellZ = cellZ;
        }

        @Override
        public void func_143022_a(NBTTagCompound nbt) {
            super.func_143022_a(nbt);
            nbt.setInteger("WreckCX", cellX);
            nbt.setInteger("WreckCZ", cellZ);
        }

        @Override
        public void func_143017_b(NBTTagCompound nbt) {
            super.func_143017_b(nbt);
            cellX = nbt.getInteger("WreckCX");
            cellZ = nbt.getInteger("WreckCZ");
        }
    }
}
