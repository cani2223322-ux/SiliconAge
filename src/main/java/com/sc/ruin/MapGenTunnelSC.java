package com.sc.ruin;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureStart;

/**
 * ПД-8 (docs/plan-ideas-2026-10-26.md, «Тоннели под плато», ★ A): the copper busbar tunnels - a vanilla structure, one start at a plateau
 * cell's middle chunk (TunnelLayoutSC.startChunk), driven by ChunkProviderSubstrateSC (the coordinator's wiring: provideChunk /
 * recreateStructures make the starts after the wrecks', before the carver; populate builds them after the wrecks, before the
 * settlements). As every ruin: a start whose box was explored before is EMPTY. MapGenStructureIO names: the start START_NAME, the pieces
 * PIECE_PREFIX + their name. Rule dimension.tunnels: no new starts while it is off (the ones made stay).
 * <p>
 * Fixed (the coordinator's): the class, the names, the start class, the hooks. ПД-8's: canSpawnStructureAtCoords, getStructureStart's
 * pieces, the pieces' classes (registered in registerStructures), covers().
 */
public class MapGenTunnelSC extends MapGenStructure {

    public static final String START_NAME = "SCSi14Tunnel";
    public static final String PIECE_PREFIX = "SCSi14Tun";

    @Override
    public String func_143025_a() {
        return START_NAME;
    }

    @Override
    protected boolean canSpawnStructureAtCoords(int cx, int cz) {
        int[] c = TunnelLayoutSC.cellAtChunk(cx, cz);           // ПД-8: a cell's middle chunk with a tunnel, the rule on, the world on
        return c != null && worldObj != null && RuinSC.generates() && com.sc.dim.SubstrateConfigSC.tunnels
                && (TunnelLayoutSC.has(worldObj.getSeed(), c[0], c[1], true) || TunnelLayoutSC.has(worldObj.getSeed(), c[0], c[1], false));
    }

    @Override
    protected StructureStart getStructureStart(int cx, int cz) {
        int[] c = TunnelLayoutSC.cellAtChunk(cx, cz);
        if (c == null) {
            return new StructureTunnelStartSC(cx, cz);
        }
        List<StructureComponent> parts = pieces(worldObj.getSeed(), c[0], c[1]);
        StructureBoundingBox all = null;
        for (StructureComponent p : parts) {
            if (all == null) {
                all = new StructureBoundingBox(p.getBoundingBox());
            } else {
                all.expandTo(p.getBoundingBox());
            }
        }
        StructureTunnelStartSC st;
        if (all == null || StructureRuinStartSC.explored(worldObj, all.minX, all.minZ, all.maxX, all.maxZ)) {
            st = new StructureTunnelStartSC(cx, cz);           // «только на ещё не открытой земле»: an empty start
        } else {
            st = new StructureTunnelStartSC(cx, cz, c[0], c[1], parts);
        }
        st.cellX = c[0];
        st.cellZ = c[1];
        return st;
    }

    /** ПД-8 (pure): the pieces of cell (cellX, cellZ) - each tunnel's segments (at most SEGMENT long), then the junction of both. */
    public static List<StructureComponent> pieces(long seed, int cellX, int cellZ) {
        List<StructureComponent> l = new ArrayList<StructureComponent>();
        for (boolean alongX : new boolean[]{true, false}) {
            if (!TunnelLayoutSC.has(seed, cellX, cellZ, alongX)) {
                continue;
            }
            int[] e = TunnelLayoutSC.ends(seed, cellX, cellZ, alongX);
            for (int t = e[0]; t <= e[1]; t += TunnelLayoutSC.SEGMENT) {
                l.add(new ComponentTunnelSC(seed, cellX, cellZ, alongX, t, Math.min(e[1], t + TunnelLayoutSC.SEGMENT - 1)));
            }
        }
        int[] j = TunnelLayoutSC.junction(seed, cellX, cellZ);
        if (j != null) {
            l.add(new ComponentTunnelJunctionSC(seed, cellX, cellZ, j[0], j[1]));
        }
        return l;
    }

    /** The column (x, z) lies in a built tunnel's box widened by `margin` (the carver keeps out - ПД-8). Server. */
    public boolean covers(int x, int z, int margin) {
        if (worldObj == null) {
            return false;
        }
        int[] s = TunnelLayoutSC.startChunk(x >> 8, z >> 8);
        Object st = structureMap.get(Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(s[0], s[1])));
        if (!(st instanceof StructureStart) || !((StructureStart) st).isSizeableStructure()) {
            return false;
        }
        for (Object o : ((StructureStart) st).getComponents()) {
            if (((StructureComponent) o).getBoundingBox().intersectsWith(x - margin, z - margin, x + margin, z + margin)) {
                return true;
            }
        }
        return false;
    }

    /** The start made at chunk (cx, cz), or null (none made yet) - the world test asks. Server. */
    public StructureRuinStartSC startAt(int cx, int cz) {
        Object st = structureMap.get(Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(cx, cz)));
        return st instanceof StructureRuinStartSC ? (StructureRuinStartSC) st : null;
    }

    /** RuinSC.init: the start and the pieces (names fixed once used - append only). */
    public static void registerStructures() {
        MapGenStructureIO.registerStructure(StructureTunnelStartSC.class, START_NAME);
        MapGenStructureIO.func_143031_a(ComponentTunnelSC.class, PIECE_PREFIX + "Seg");
        MapGenStructureIO.func_143031_a(ComponentTunnelJunctionSC.class, PIECE_PREFIX + "Junction");
    }

    /** The start class of this structure (MapGenStructureIO wants one per name); its cell in NBT CellX / CellZ. */
    public static class StructureTunnelStartSC extends StructureRuinStartSC {
        public int cellX, cellZ;

        public StructureTunnelStartSC() {
        }

        public StructureTunnelStartSC(int cx, int cz) {
            super(cx, cz);
        }

        public StructureTunnelStartSC(int cx, int cz, int cellX, int cellZ, List<StructureComponent> parts) {
            super(cx, cz, parts);
            this.cellX = cellX;
            this.cellZ = cellZ;
        }

        @Override
        public void func_143022_a(NBTTagCompound nbt) {
            super.func_143022_a(nbt);
            nbt.setInteger("CellX", cellX);
            nbt.setInteger("CellZ", cellZ);
        }

        @Override
        public void func_143017_b(NBTTagCompound nbt) {
            super.func_143017_b(nbt);
            cellX = nbt.getInteger("CellX");
            cellZ = nbt.getInteger("CellZ");
        }
    }
}
