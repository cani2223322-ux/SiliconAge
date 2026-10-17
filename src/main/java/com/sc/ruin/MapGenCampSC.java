package com.sc.ruin;

import java.util.ArrayList;
import java.util.List;

import cpw.mods.fml.common.FMLLog;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureStart;

/**
 * PACKAGE C (docs/plan-substrate-tech-f4.md §8.2, rules ПЗ-1..ПЗ-7): the Renegades' camps - a vanilla structure, a start at the
 * chunk of every camp's middle (CampLayoutSC.siteAtChunk). As the other ruins (ПУ-3): a start whose box touches ground explored
 * before (StructureRuinStartSC.explored) is EMPTY - no half camp. The pieces build only in the populate window, by the pure
 * heights (SubstrateTerrainSC.canyonFloorY): the floor levelled, the tarps (ruinSC campTarp), the Stolen Field Generator in the
 * middle (CampBlocksSC.stolenField - setup(FIELD_RADIUS, campKey) right after setBlock), the stolen solar panels and the stolen
 * charge pad, the camp's safe (CampBlocksSC.camp CAMP_SAFE); the residents (CampLayoutSC.Site: the Marauders, the Warlord, the
 * Executioner) once, in the window holding the middle, born in Si-14, persistent, their home the camp's box; the Warlord into
 * RareRegistrySC (campKey). MapGenStructureIO: START_NAME, the pieces PIECE_PREFIX + name (the base "SCSi14CmpPart").
 * <p>
 * Fixed (the coordinator's): START_NAME, PIECE_PREFIX (MapGenStructureIO names - append only), covers(), the start class and its
 * NBT keys, the calls from ChunkProviderSubstrateSC (provideChunk / recreateStructures make the starts, populate builds them).
 * The rest is package C's.
 * <p>
 * A camp's start: one piece, the whole camp (ComponentCampGroundSC, "SCSi14CmpGround") - its box 45 x 17 round the middle, 3 x 3
 * or 4 x 2 chunks, all within MapGenBase's reach of 8 from the start's chunk, so in new land the start is always made before any
 * of them. A world of Ф1..Ф3 whose canyon there was walked already gets no camp at that spot (an empty start, a line in the log).
 */
public class MapGenCampSC extends MapGenStructure {

    public static final String START_NAME = "SCSi14Camp";
    public static final String PIECE_PREFIX = "SCSi14Cmp";

    @Override
    public String func_143025_a() {
        return START_NAME;
    }

    @Override
    protected boolean canSpawnStructureAtCoords(int cx, int cz) {
        return worldObj != null && RuinSC.generates() && CampLayoutSC.siteAtChunk(worldObj.getSeed(), cx, cz) != null;
    }

    @Override
    protected StructureStart getStructureStart(int cx, int cz) {
        CampLayoutSC.Site s = CampLayoutSC.siteAtChunk(worldObj.getSeed(), cx, cz);
        if (s == null) {
            return new StructureCampStartSC(cx, cz);
        }
        int[] b = s.box();
        StructureCampStartSC st;
        if (StructureRuinStartSC.explored(worldObj, b[0], b[1], b[2], b[3])) {   // ПУ-3: old land - no half-built camp
            FMLLog.info("[Silicon Age] Si-14 Substrate: no Renegades' camp at %d %d - its ground was explored before (world of an older version)", s.x, s.z);
            st = new StructureCampStartSC(cx, cz);
        } else {
            st = new StructureCampStartSC(cx, cz, pieces(worldObj.getSeed(), s));
        }
        st.axis = s.axis;
        st.line = s.line;
        st.seg = s.seg;
        return st;
    }

    /** The pieces of camp `s`: the whole camp on the canyon's floor (pure). */
    public static List<StructureComponent> pieces(long worldSeed, CampLayoutSC.Site s) {
        List<StructureComponent> l = new ArrayList<StructureComponent>();
        l.add(new ComponentCampGroundSC(s, ComponentCampSC.halfWidth(worldSeed, s)));
        return l;
    }

    /**
     * The block column (x, z) lies in a built camp's box widened by `margin` (the generator keeps its traces, druses and ponds,
     * the carver its vias, out of it). Server; false before the generator has seen a world.
     */
    public boolean covers(int x, int z, int margin) {
        if (worldObj == null) {
            return false;
        }
        CampLayoutSC.Site s = CampLayoutSC.siteNear(worldObj.getSeed(), x, z, margin);   // a start exists only for a real site
        if (s == null) {
            return false;
        }
        Object st = structureMap.get(Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(s.x >> 4, s.z >> 4)));
        return st instanceof StructureStart && ((StructureStart) st).isSizeableStructure()
                && ((StructureStart) st).getBoundingBox().intersectsWith(x - margin, z - margin, x + margin, z + margin);
    }

    /** The start made at chunk (cx, cz) (a camp's middle chunk), or null (none made yet) - the world test asks. Server. */
    public StructureRuinStartSC startAt(int cx, int cz) {
        Object st = structureMap.get(Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(cx, cz)));
        return st instanceof StructureRuinStartSC ? (StructureRuinStartSC) st : null;
    }

    /** The built camp's piece of the start at chunk (cx, cz), or null (none, an empty start) - the world test and RuinWorldSC ask. */
    public ComponentCampSC campAt(int cx, int cz) {
        StructureRuinStartSC st = startAt(cx, cz);
        if (st == null) {
            return null;
        }
        for (Object o : st.getComponents()) {
            if (o instanceof ComponentCampSC) {
                return (ComponentCampSC) o;
            }
        }
        return null;
    }

    /** RuinSC.init (both sides, always): the start and the pieces (names fixed once used - append only). */
    public static void registerStructures() {
        MapGenStructureIO.registerStructure(StructureCampStartSC.class, START_NAME);
        MapGenStructureIO.func_143031_a(ComponentCampSC.class, PIECE_PREFIX + "Part");
        MapGenStructureIO.func_143031_a(ComponentCampGroundSC.class, PIECE_PREFIX + "Ground");
    }

    /** The start class of this structure (MapGenStructureIO wants one per name); its canyon segment in NBT CampAxis / CampLine / CampSeg. */
    public static class StructureCampStartSC extends StructureRuinStartSC {
        /** The camp's canyon segment (CampLayoutSC.Site: axis, line, seg). */
        public int axis, line, seg;

        public StructureCampStartSC() {
        }

        public StructureCampStartSC(int cx, int cz) {
            super(cx, cz);
        }

        public StructureCampStartSC(int cx, int cz, List<StructureComponent> parts) {
            super(cx, cz, parts);
        }

        @Override
        public void func_143022_a(NBTTagCompound nbt) {
            super.func_143022_a(nbt);
            nbt.setInteger("CampAxis", axis);
            nbt.setInteger("CampLine", line);
            nbt.setInteger("CampSeg", seg);
        }

        @Override
        public void func_143017_b(NBTTagCompound nbt) {
            super.func_143017_b(nbt);
            axis = nbt.getInteger("CampAxis");
            line = nbt.getInteger("CampLine");
            seg = nbt.getInteger("CampSeg");
        }
    }
}
