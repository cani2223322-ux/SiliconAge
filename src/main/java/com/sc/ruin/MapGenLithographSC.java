package com.sc.ruin;

import java.util.ArrayList;
import java.util.List;

import com.sc.dim.SubstrateTerrainSC;

import cpw.mods.fml.common.FMLLog;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureStart;

/**
 * PACKAGE X (docs/plan-substrate-tech-f3.md §8.4, rules ПУ-4..ПУ-6): the abandoned lithographs - a vanilla structure, one
 * start per RuinLayoutSC.lithoSite, its hall underground (25 x 25 x 9) with the entrance hut on the surface; built in the
 * populate window only. Its pieces put the loot chests (§8.6) and - in their own window, once - the residents: 2..4 mimic
 * machines among the husks, 2..3 feral sentry drones (marked «born in Si-14», persistent, never made again). An explored
 * ground: an EMPTY start (ПП-2). MapGenStructureIO names: START_NAME, pieces PIECE_PREFIX + their name ("SCSi14LitPart" is
 * the base piece).
 * <p>
 * The start's chunk is the hall's middle column's; its area is the hall's box (the hut and the shaft stand inside it) - 2 x 2
 * or 3 x 3 chunks. The start keeps its cell (NBT LithoCX / LithoCZ). Pieces: the hall (ComponentLitHallSC, with the
 * residents), then the entrance (ComponentLitEntranceSC: the hut and the shaft's ladder through the hall's ceiling).
 */
public class MapGenLithographSC extends MapGenStructure {

    public static final String START_NAME = "SCSi14Litho";
    public static final String PIECE_PREFIX = "SCSi14Lit";
    /** ПУ-6: a hall's residents (ComponentLitHallSC's - here for the handbook). */
    public static final int MIMICS_MIN = ComponentLitHallSC.MIMICS_MIN, MIMICS_MAX = ComponentLitHallSC.MIMICS_MAX,
            DRONES_MIN = ComponentLitHallSC.DRONES_MIN, DRONES_MAX = ComponentLitHallSC.DRONES_MAX;
    /** A piece reaching this high stands on the surface (the entrance): traces, druses and ponds keep off it, not off the hall. */
    private static final int SURFACE_Y = SubstrateTerrainSC.PLATEAU_MIN - 8;

    @Override
    public String func_143025_a() {
        return START_NAME;
    }

    @Override
    protected boolean canSpawnStructureAtCoords(int cx, int cz) {
        return worldObj != null && RuinSC.generates() && RuinLayoutSC.lithoAtChunk(worldObj.getSeed(), cx, cz) != null;
    }

    @Override
    protected StructureStart getStructureStart(int cx, int cz) {
        RuinLayoutSC.LithoSite s = RuinLayoutSC.lithoAtChunk(worldObj.getSeed(), cx, cz);
        if (s == null) {
            return new StructureLithographStartSC(cx, cz);
        }
        int r = RuinLayoutSC.HALL_HALF;
        if (StructureRuinStartSC.explored(worldObj, s.x - r, s.z - r, s.x + r, s.z + r)) {   // ПУ-3: old land - none here
            FMLLog.info("[Silicon Age] Si-14 Substrate: no lithograph at %d %d - its ground was explored before (world of 0.2.1 or older)", s.x, s.z);
            StructureLithographStartSC empty = new StructureLithographStartSC(cx, cz);
            empty.cellX = s.cellX;
            empty.cellZ = s.cellZ;
            return empty;
        }
        return new StructureLithographStartSC(cx, cz, s.cellX, s.cellZ, pieces(worldObj.getSeed(), s));
    }

    /** The pieces of a lithograph: the hall first, then its entrance (pure). */
    public static List<StructureComponent> pieces(long worldSeed, RuinLayoutSC.LithoSite s) {
        List<StructureComponent> l = new ArrayList<StructureComponent>();
        l.add(new ComponentLitHallSC(s));
        l.add(new ComponentLitEntranceSC(s, worldSeed));
        return l;
    }

    /**
     * The column (x, z) lies in a built lithograph's surface part (its entrance hut) widened by `margin`: no trace, druse or
     * pond on it - ChunkProviderSubstrateSC asks. The hall is deep under the plateau: what lies over it stays. Server.
     */
    public boolean covers(int x, int z, int margin) {
        if (worldObj == null) {
            return false;
        }
        int[] m = RuinLayoutSC.middleNear(worldObj.getSeed(), x, z, margin);   // a start exists only for a real site
        if (m == null) {
            return false;
        }
        Object st = structureMap.get(Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(m[0] >> 4, m[1] >> 4)));
        if (!(st instanceof StructureStart) || !((StructureStart) st).isSizeableStructure()) {
            return false;
        }
        for (Object o : ((StructureStart) st).getComponents()) {
            StructureBoundingBox b = ((StructureComponent) o).getBoundingBox();
            if (b.maxY >= SURFACE_Y && b.intersectsWith(x - margin, z - margin, x + margin, z + margin)) {
                return true;
            }
        }
        return false;
    }

    /** The start made at chunk (cx, cz) (a hall's middle chunk), or null (none made yet) - the world test asks. Server. */
    public StructureRuinStartSC startAt(int cx, int cz) {
        Object st = structureMap.get(Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(cx, cz)));
        return st instanceof StructureRuinStartSC ? (StructureRuinStartSC) st : null;
    }

    /** RuinSC.init: the start and the pieces (names fixed once used - append only). */
    public static void registerStructures() {
        MapGenStructureIO.registerStructure(StructureLithographStartSC.class, START_NAME);
        MapGenStructureIO.func_143031_a(ComponentRuinSC.Lit.class, PIECE_PREFIX + "Part");
        MapGenStructureIO.func_143031_a(ComponentLitHallSC.class, PIECE_PREFIX + "Hall");
        MapGenStructureIO.func_143031_a(ComponentLitEntranceSC.class, PIECE_PREFIX + "Entrance");
    }

    /** The start class of this structure; its cell in NBT LithoCX / LithoCZ. */
    public static class StructureLithographStartSC extends StructureRuinStartSC {
        /** The plateau cell. */
        public int cellX, cellZ;

        public StructureLithographStartSC() {
        }

        public StructureLithographStartSC(int cx, int cz) {
            super(cx, cz);
        }

        public StructureLithographStartSC(int cx, int cz, int cellX, int cellZ, List<StructureComponent> parts) {
            super(cx, cz, parts);
            this.cellX = cellX;
            this.cellZ = cellZ;
        }

        @Override
        public void func_143022_a(NBTTagCompound nbt) {
            super.func_143022_a(nbt);
            nbt.setInteger("LithoCX", cellX);
            nbt.setInteger("LithoCZ", cellZ);
        }

        @Override
        public void func_143017_b(NBTTagCompound nbt) {
            super.func_143017_b(nbt);
            cellX = nbt.getInteger("LithoCX");
            cellZ = nbt.getInteger("LithoCZ");
        }
    }
}
