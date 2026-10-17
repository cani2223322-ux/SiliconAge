package com.sc.arena;

import java.util.ArrayList;
import java.util.List;

import com.sc.dim.ChunkProviderSubstrateSC;
import com.sc.dim.SubstrateTerrainSC;
import com.sc.ruin.StructureRuinStartSC;

import cpw.mods.fml.common.FMLLog;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureStart;

/**
 * PACKAGE A (docs/plan-substrate-tech-f5.md §4.2, rules ПУ-8..ПУ-11): the Breakdown Epicentres - a vanilla structure, one start at
 * each region's arena crossing (ArenaLayoutSC.arenaAtChunk), driven by ChunkProviderSubstrateSC (the coordinator's lines):
 * provideChunk makes the starts (after the Dead Nodes' - their saved starts are loaded by then), then carve() cuts the pit into the
 * chunk's arrays (only where a start with pieces covers the chunk), populate builds the pieces after the Vaults. As every ruin
 * (ПУ-3): a start whose ground was explored before (a chunk of its box on disk already - a world of 0.2.x), or whose crossing has a
 * Dead Node start with pieces already (made in an earlier session: its chunks not generated yet), is EMPTY - no half arena, no
 * arena over a Node; either way the start writes its ArenaDataSC record (EMPTY / READY) before any of its chunks.
 * MapGenStructureIO names: the start START_NAME, the pieces PIECE_PREFIX + their name. The start keeps its crossing (ArenaI / ArenaJ).
 * <p>
 * Fixed (the coordinator's): the class, START_NAME / PIECE_PREFIX, the start class and its NBT, covers / carve / startAt and their
 * contracts, registerStructures (A appends its pieces).
 * <p>
 * A's:
 * <ul>
 * <li>the start: the record first (ArenaDataSC.make - a record made before decides, so a lost structure file never turns a built
 * arena empty or an empty one built), then explored / a Node there - empty, else the pieces (ComponentArenaPitSC: the columns,
 * the pedestals, the Console with its setup, the lamps);</li>
 * <li>carve(): inside RIM_R everything over the floor is air up to the sky; the floor's disk (PIT_R) the floor's plating at floorY
 * with the Breakdown scars in wall plating (ComponentArenaSC.scar); the ring out to RIM_R wall plating from the floor to floorY +
 * WALL_H, open where a canyon comes in (its mouth: the floor's plating goes on at floorY - the canyons' floors run evenly from
 * crossing to crossing, so at the rim they are within a block of it and walk in level); out of the ring the rock over the wall's
 * top cut back in a funnel of FUNNEL_RISE-high steps a block wide, rising faster where it must to meet the land at the carved
 * area's edge (funnelFrom: no cliff along the border of the chunks no start covers; no overhang over the pit, the sky open);
 * under the floor the rock as it was. No light-giving block and no Blocks.stone goes into the arrays.</li>
 * </ul>
 */
public class MapGenArenaSC extends MapGenStructure {

    public static final String START_NAME = "SCSi14Arena";
    public static final String PIECE_PREFIX = "SCSi14Arn";
    /** ПУ-9: the funnel over the wall ring - every block further out the cut is this much higher. TODO(balance) */
    public static final int FUNNEL_RISE = 2;

    @Override
    public String func_143025_a() {
        return START_NAME;
    }

    /** ПУ-8: the world is on and (cx, cz) is a region's arena crossing's corner chunk. */
    @Override
    protected boolean canSpawnStructureAtCoords(int cx, int cz) {
        return worldObj != null && ArenaSC.generates() && ArenaLayoutSC.arenaAtChunk(worldObj.getSeed(), cx, cz) != null;
    }

    /** ПУ-9, ПУ-11: the record first, then explored / a Node there - empty, else the pieces. */
    @Override
    protected StructureStart getStructureStart(int cx, int cz) {
        long seed = worldObj.getSeed();
        int[] c = ArenaLayoutSC.arenaAtChunk(seed, cx, cz);
        if (c == null) {
            return new StructureArenaStartSC(cx, cz);
        }
        int i = c[0], j = c[1], x = ArenaLayoutSC.centre(i), z = ArenaLayoutSC.centre(j), r = ArenaLayoutSC.HALF;
        int floorY = ArenaLayoutSC.floorY(seed, i, j);
        ArenaDataSC data = ArenaDataSC.get(worldObj);
        ArenaDataSC.Arena old = data == null ? null : data.arenaAt(i, j);
        boolean built;
        if (old != null) {
            built = old.state != ArenaDataSC.EMPTY;                 // decided before (the structure's own file lost): kept
        } else if (StructureRuinStartSC.explored(worldObj, x - r, z - r, x + r, z + r)) {   // ПУ-3: old land - no half-built arena
            FMLLog.info("[Silicon Age] Si-14 Substrate: no Breakdown Epicentre at %d %d - its ground was explored before (world of 0.2.1 or older)", x, z);
            built = false;
        } else if (nodeAt(i, j)) {
            FMLLog.info("[Silicon Age] Si-14 Substrate: no Breakdown Epicentre at %d %d - the Dead Node made there earlier stays", x, z);
            built = false;
        } else {
            built = true;
        }
        if (data != null) {
            data.make(i, j, floorY, built);                         // ПУ-11: the record before any of its chunks
        }
        if (!built || data == null) {
            StructureArenaStartSC empty = new StructureArenaStartSC(cx, cz);
            empty.arenaI = i;
            empty.arenaJ = j;
            return empty;
        }
        return new StructureArenaStartSC(cx, cz, i, j, pieces(seed, i, j));
    }

    /** A Dead Node's start with pieces stands at crossing (i, j) already (made in an earlier session, before Ф5). */
    private boolean nodeAt(int i, int j) {
        ChunkProviderSubstrateSC gen = ChunkProviderSubstrateSC.of(worldObj);
        int g = SubstrateTerrainSC.GRID >> 4;
        StructureRuinStartSC st = gen == null ? null : gen.nodes().startAt(i * g, j * g);
        return st != null && st.isSizeableStructure();
    }

    /** The pieces of the arena at crossing (i, j) (pure). */
    public static List<StructureComponent> pieces(long worldSeed, int i, int j) {
        List<StructureComponent> l = new ArrayList<StructureComponent>();
        l.add(new ComponentArenaPitSC(ComponentArenaSC.seedOf(worldSeed, i, j), i, j, ArenaLayoutSC.floorY(worldSeed, i, j)));
        return l;
    }

    /**
     * The coordinator's call in provideChunk (after the starts, before the carver): cut the pit of a built arena into chunk (cx, cz)'s
     * arrays (index (x * 16 + z) * 256 + y, as ChunkProviderSubstrateSC's). Nothing when no start with pieces covers the chunk.
     */
    public void carve(int cx, int cz, Block[] blocks, byte[] metas) {
        Block plate = ArenaBlocksSC.arena;
        if (worldObj == null || plate == null || blocks == null || metas == null) {
            return;
        }
        int x0 = cx << 4, z0 = cz << 4;
        int i = SubstrateTerrainSC.lineIndex(x0 + 8), j = SubstrateTerrainSC.lineIndex(z0 + 8), g = SubstrateTerrainSC.GRID >> 4;
        Object o = structureMap.get(Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(i * g, j * g)));
        if (!(o instanceof StructureArenaStartSC) || !((StructureStart) o).isSizeableStructure()
                || !((StructureStart) o).getBoundingBox().intersectsWith(x0, z0, x0 + 15, z0 + 15)) {
            return;
        }
        long seed = worldObj.getSeed();
        long scars = ComponentArenaSC.seedOf(seed, i, j);
        int mx = ArenaLayoutSC.centre(i), mz = ArenaLayoutSC.centre(j), floorY = ArenaLayoutSC.floorY(seed, i, j);
        // the carved area: every chunk the start's box touches (its last column each way from the middle - the funnel's edge)
        StructureBoundingBox bb = ((StructureStart) o).getBoundingBox();
        int loX = mx - (bb.minX >> 4 << 4), hiX = (bb.maxX >> 4 << 4) + 15 - mx;
        int loZ = mz - (bb.minZ >> 4 << 4), hiZ = (bb.maxZ >> 4 << 4) + 15 - mz;
        int hx = SubstrateTerrainSC.canyonHalfWidth(seed, i), hz = SubstrateTerrainSC.canyonHalfWidth(~seed, j);
        int pit2 = ArenaLayoutSC.PIT_R * ArenaLayoutSC.PIT_R, rim2 = ArenaLayoutSC.RIM_R * ArenaLayoutSC.RIM_R;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int dx = x0 + x - mx, dz = z0 + z - mz, d2 = dx * dx + dz * dz, col = (x * 16 + z) * 256;
                if (d2 > rim2) {                                // the funnel: out of the ring, the rock over the wall's top stepped back
                    clear(blocks, metas, col, funnelFrom(blocks, col, dx, dz, floorY, loX, hiX, loZ, hiZ));
                    continue;
                }
                boolean mouth = Math.abs(dx) <= hx || Math.abs(dz) <= hz;
                boolean wall = d2 > pit2 && !mouth;
                int top = wall ? floorY + ArenaLayoutSC.WALL_H : floorY;
                for (int y = floorY; y <= top; y++) {
                    blocks[col + y] = plate;
                    metas[col + y] = (byte) (wall || d2 <= pit2 && ComponentArenaSC.scar(scars, dx, dz) ? ArenaBlocksSC.WALL : ArenaBlocksSC.FLOOR);
                }
                clear(blocks, metas, col, top + 1);
            }
        }
    }

    /**
     * ПУ-9: where the funnel's cut begins in column `col` at (dx, dz) out of the ring - FUNNEL_RISE a block out from the wall's top,
     * and never under the line from there to the column's own ground at the carved area's edge along the ray from the middle (lo /
     * hi - the area's last columns each way): the cut meets the land at the chunks no start covers - no cliff along their border,
     * and steeper where the area is narrow (by the canyons' lines). @return the first Y cleared
     */
    private static int funnelFrom(Block[] blocks, int col, int dx, int dz, int floorY, int loX, int hiX, int loZ, int hiZ) {
        double d = Math.sqrt(dx * (double) dx + dz * (double) dz);
        int base = floorY + ArenaLayoutSC.WALL_H + 1;
        int stepped = base + ((int) Math.ceil(d) - ArenaLayoutSC.RIM_R - 1) * FUNNEL_RISE;
        double k = Math.max(dx >= 0 ? dx / (double) Math.max(1, hiX) : -dx / (double) Math.max(1, loX),
                dz >= 0 ? dz / (double) Math.max(1, hiZ) : -dz / (double) Math.max(1, loZ));
        double edge = k > 0D ? d / k : d;                       // the ray's length to the area's edge
        int top = 0;
        for (int y = 255; y > 0; y--) {
            if (blocks[col + y] != null) {
                top = y;                                        // the column's own ground (the terrain as made)
                break;
            }
        }
        if (top + 1 <= base || edge <= ArenaLayoutSC.RIM_R) {
            return stepped;
        }
        double f = Math.min(1D, Math.max(0D, (d - ArenaLayoutSC.RIM_R) / (edge - ArenaLayoutSC.RIM_R)));
        return Math.max(stepped, base + (int) Math.round((top + 1 - base) * f));
    }

    /** Air from `from` up to the top of the column `col` (never Y 0, the bedrock). */
    private static void clear(Block[] blocks, byte[] metas, int col, int from) {
        for (int y = Math.max(1, from); y < 256; y++) {
            blocks[col + y] = null;
            metas[col + y] = 0;
        }
    }

    /**
     * The block column (x, z) lies in a built arena's box widened by `margin` (traces, druses, ponds, vias keep out). Server; false
     * before the generator has seen a world.
     */
    public boolean covers(int x, int z, int margin) {
        if (worldObj == null || SubstrateTerrainSC.distanceToLine(x) > ArenaLayoutSC.HALF + margin
                || SubstrateTerrainSC.distanceToLine(z) > ArenaLayoutSC.HALF + margin) {
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

    /** ArenaSC.init: the start and the pieces (names fixed once used - append only). */
    public static void registerStructures() {
        MapGenStructureIO.registerStructure(StructureArenaStartSC.class, START_NAME);
        MapGenStructureIO.func_143031_a(ComponentArenaSC.class, PIECE_PREFIX + "Part");
        MapGenStructureIO.func_143031_a(ComponentArenaPitSC.class, PIECE_PREFIX + "Pit");
    }

    /** The start class of this structure (MapGenStructureIO wants one per name); its crossing in NBT ArenaI / ArenaJ. */
    public static class StructureArenaStartSC extends StructureRuinStartSC {
        /** The crossing (line numbers). */
        public int arenaI, arenaJ;

        public StructureArenaStartSC() {
        }

        public StructureArenaStartSC(int cx, int cz) {
            super(cx, cz);
        }

        public StructureArenaStartSC(int cx, int cz, int i, int j, List<StructureComponent> parts) {
            super(cx, cz, parts);
            arenaI = i;
            arenaJ = j;
        }

        @Override
        public void func_143022_a(NBTTagCompound nbt) {
            super.func_143022_a(nbt);
            nbt.setInteger("ArenaI", arenaI);
            nbt.setInteger("ArenaJ", arenaJ);
        }

        @Override
        public void func_143017_b(NBTTagCompound nbt) {
            super.func_143017_b(nbt);
            arenaI = nbt.getInteger("ArenaI");
            arenaJ = nbt.getInteger("ArenaJ");
        }
    }
}
