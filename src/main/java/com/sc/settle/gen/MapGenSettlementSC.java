package com.sc.settle.gen;

import java.util.ArrayList;
import java.util.List;

import com.sc.settle.RosterSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.settle.npc.NpcNamesSC;

import cpw.mods.fml.common.FMLLog;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureStart;

/**
 * PACKAGE S (docs/plan-substrate-tech-f2.md §4.4, rules ПП-1, ПП-2, ПК-13): the settlements as a vanilla structure.
 * ChunkProviderSubstrateSC calls func_151539_a in provideChunk (and with null blocks in recreateStructures),
 * generateStructuresInChunk at the END of populate (after the ores, traces and druses - so the settlement lies over
 * them) and answers func_147416_a("SCSi14Settlement") with func_151545_a.
 * <ul>
 * <li>canSpawnStructureAtCoords(cx, cz): SettlementSC.generates() and SettlementLayoutSC.siteAtChunk(seed, cx, cz) != null;</li>
 * <li>getStructureStart: the plan; if any chunk of its footprint already exists on disk (a Ф1 world explored before
 * the update - AnvilChunkLoader.chunkExists through ((ChunkProviderServer) worldObj.getChunkProvider()).currentChunkLoader)
 * or is loaded - an EMPTY start (isSizeableStructure false, a 1 x 1 box: vanilla saves and intersects every start's
 * box), so no half-built settlement ever stands in old land; otherwise the start with its pieces and the registry
 * record (SettlementRegistrySC.add - its id past every start's of DIM-1414/data, reserveIds -, or the record already
 * at that centre - atCentre - when a start is made again: after DIM-1414/data was lost it is kept as it is, the
 * creatures still about are adopted; on new ground - DIM-1414 deleted - its roster starts afresh, renew) with the
 * slots and points of the plan. In a new world a start is always made before
 * any chunk of its footprint: MapGenBase looks 8 chunks round every chunk it makes, the footprint reaches 5;</li>
 * <li>pieces build only inside the populate window they are given, by the pure terrain heights (SubstrateTerrainSC) -
 * nothing is read outside the window, nothing cascades; every addComponentParts returns true.</li>
 * </ul>
 * MapGenStructureIO names (ПК-13, unique prefix SCSi14): the start START_NAME; the pieces "SCSi14Set" + their name.
 */
public class MapGenSettlementSC extends MapGenStructure {

    /** The structure's name (vanilla's MapGenStructureData in DIM-1414/data, the start's MapGenStructureIO name). */
    public static final String START_NAME = "SCSi14Settlement";
    /** The pieces' MapGenStructureIO names start with this. */
    public static final String PIECE_PREFIX = "SCSi14Set";

    @Override
    public String func_143025_a() {
        return START_NAME;
    }

    @Override
    protected boolean canSpawnStructureAtCoords(int cx, int cz) {
        return worldObj != null && SettlementSC.generates() && SettlementLayoutSC.siteAtChunk(worldObj.getSeed(), cx, cz) != null;
    }

    @Override
    protected StructureStart getStructureStart(int cx, int cz) {
        SettlementLayoutSC.Site site = SettlementLayoutSC.siteAtChunk(worldObj.getSeed(), cx, cz);
        if (site == null) {
            return new StructureSettlementStartSC(cx, cz);
        }
        SettlementLayoutSC.Plan p = SettlementLayoutSC.plan(worldObj.getSeed(), site);
        SettlementRegistrySC reg = SettlementRegistrySC.get(worldObj);
        if (reg == null) {
            return new StructureSettlementStartSC(cx, cz);
        }
        int dim = worldObj.provider.dimensionId;
        long now = worldObj.getTotalWorldTime();
        SettlementRegistrySC.Settlement rec = reg.atCentre(dim, p.coreX, p.coreZ);
        boolean explored = explored(p);
        if (rec == null && explored) {                          // ПП-2: old land - no settlement here
            FMLLog.info("[Silicon Age] Si-14 Substrate: no settlement at %d %d - its ground was explored before (world of 0.2.1 or older)",
                    p.coreX, p.coreZ);
            return new StructureSettlementStartSC(cx, cz);
        }
        if (rec == null) {
            reg.reserveIds(maxStartId());                       // the registry's file lost, DIM-1414/data kept: no id twice
            rec = reg.add(record(p, dim, now));
        } else if (!explored) {
            // §3: DIM-1414 deleted, the registry kept - the land is new, so none of the record's creatures is anywhere
            renew(rec, p, now);
            reg.reindex(rec);
            FMLLog.info("[Silicon Age] Si-14 Substrate: settlement %d at %d %d is built again on new ground - its roster starts afresh",
                    rec.id, p.coreX, p.coreZ);
        }
        return new StructureSettlementStartSC(cx, cz, rec.id, pieces(rec.id, p));
    }

    /** The highest settlement id of the starts made or loaded from DIM-1414/data (func_143027_a ran before this), 0 none. */
    private int maxStartId() {
        int max = 0;
        for (Object o : structureMap.values()) {
            if (o instanceof StructureSettlementStartSC) {
                max = Math.max(max, ((StructureSettlementStartSC) o).settlementId());
            }
        }
        return max;
    }

    /**
     * A record whose land is made again (DIM-1414 deleted, the global registry kept - plan §3, §15): no creature of it
     * can be anywhere, so its roster starts as a new world's - the plan's site, dock, points and slots (all NEVER, the
     * roster fills them as the chunks are built), the Clone Capsule's day gone. Its id, its name and the players' local
     * records stay (the standing outlives the land). Pure; the self-test asks it.
     */
    public static void renew(SettlementRegistrySC.Settlement rec, SettlementLayoutSC.Plan p, long now) {
        SettlementRegistrySC.Settlement plan = record(p, rec.dim, now);
        rec.y = plan.y;
        rec.floorY = plan.floorY;
        rec.size = plan.size;
        rec.radius = plan.radius;
        rec.seed = plan.seed;
        rec.first = plan.first;
        rec.hasDock = plan.hasDock;
        rec.dockX = plan.dockX;
        rec.dockY = plan.dockY;
        rec.dockZ = plan.dockZ;
        rec.version = plan.version;
        rec.madeAt = now;
        rec.points.clear();
        rec.points.putAll(plan.points);
        rec.slots.clear();
        rec.slots.addAll(plan.slots);
        NBTTagCompound c = rec.extra.getCompoundTag(RosterSC.EXTRA);
        c.removeTag(RosterSC.CLONE_DAY);
        rec.extra.setTag(RosterSC.EXTRA, c);
    }

    /** ПП-2: a chunk of the plan's footprint is on disk already, or loaded. */
    private boolean explored(SettlementLayoutSC.Plan p) {
        IChunkProvider cp = worldObj.getChunkProvider();
        ChunkProviderServer server = cp instanceof ChunkProviderServer ? (ChunkProviderServer) cp : null;
        IChunkLoader loader = server == null ? null : server.currentChunkLoader;
        int[] c = SettlementLayoutSC.footprintChunks(p);
        for (int x = c[0]; x <= c[2]; x++) {
            for (int z = c[1]; z <= c[3]; z++) {
                if (server != null && server.chunkExists(x, z)
                        || loader instanceof AnvilChunkLoader && ((AnvilChunkLoader) loader).chunkExists(worldObj, x, z)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The registry's record of a plan (rule ПП-5): the site, the dome, the dock, the points, the slots (NEVER). */
    static SettlementRegistrySC.Settlement record(SettlementLayoutSC.Plan p, int dim, long now) {
        SettlementRegistrySC.Settlement s = new SettlementRegistrySC.Settlement();
        s.dim = dim;
        s.x = p.coreX;
        s.y = p.coreY;
        s.z = p.coreZ;
        s.floorY = p.floorY;
        s.size = p.site.size.ordinal();
        s.radius = p.radius;
        s.seed = p.site.seed;
        s.nameElement = p.nameElement;
        s.nameNumber = p.nameNumber;
        s.first = p.site.first;
        s.hasDock = p.dock;
        s.dockX = p.dockX;
        s.dockY = p.dockY;
        s.dockZ = p.dockZ;
        s.version = SettlementRegistrySC.VERSION;
        s.madeAt = now;
        for (int i = 0; i < p.pointNames.size(); i++) {
            s.points.put(p.pointNames.get(i), p.points.get(i).clone());
        }
        for (SettlementLayoutSC.Post q : p.posts) {
            SettlementRegistrySC.Slot sl = new SettlementRegistrySC.Slot();
            sl.kind = q.kind.ordinal();
            sl.nameElement = NpcNamesSC.element(q.name);
            sl.nameNumber = NpcNamesSC.number(q.name);
            sl.postX = q.x;
            sl.postY = q.y;
            sl.postZ = q.z;
            sl.homeX = q.homeX;
            sl.homeY = q.homeY;
            sl.homeZ = q.homeZ;
            sl.state = SettlementRegistrySC.NEVER;
            s.slots.add(sl);
        }
        return s;
    }

    /** The pieces of a plan: the ground first (the buildings of a window come after it), then the lots in the plan's order. */
    static List<StructureComponent> pieces(int id, SettlementLayoutSC.Plan p) {
        List<StructureComponent> l = new ArrayList<StructureComponent>();
        l.add(new ComponentSetGroundSC(id, p));
        for (SettlementLayoutSC.Lot lot : p.lots) {
            switch (lot.kind) {
                case SettlementLayoutSC.LOT_HEART:
                    l.add(new ComponentSetHeartSC(id, p, lot));
                    break;
                case SettlementLayoutSC.LOT_STREET:
                    l.add(new ComponentSetStreetSC(id, p, lot));
                    break;
                case SettlementLayoutSC.LOT_GATE:
                    l.add(new ComponentSetGateSC(id, p, lot));
                    break;
                case SettlementLayoutSC.LOT_TOWER:
                    l.add(new ComponentSetTowerSC(id, p, lot));
                    break;
                case SettlementLayoutSC.LOT_WORKSHOP:
                    l.add(new ComponentSetWorkshopSC(id, p, lot));
                    break;
                case SettlementLayoutSC.LOT_HOUSE:
                    l.add(new ComponentSetHouseSC(id, p, lot));
                    break;
                case SettlementLayoutSC.LOT_DOCK:
                    l.add(new ComponentSetDockSC(id, p, lot));
                    break;
                default:
                    break;
            }
        }
        return l;
    }

    /**
     * A built settlement's box (its start is made and not empty) holds column (x, z), `margin` round it - the chunk
     * generator keeps its traces, druses, ponds and vias out of it. Only the column's own region can hold one (a
     * footprint never leaves its plateau cell). Server; false before the generator has seen a world.
     */
    public boolean covers(int x, int z, int margin) {
        if (worldObj == null) {
            return false;
        }
        SettlementLayoutSC.Site s = SettlementLayoutSC.siteNear(worldObj.getSeed(), x, z);
        if (s == null) {
            return false;
        }
        Object st = structureMap.get(Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(s.x >> 4, s.z >> 4)));
        return st instanceof StructureStart && ((StructureStart) st).isSizeableStructure()
                && ((StructureStart) st).getBoundingBox().intersectsWith(x - margin, z - margin, x + margin, z + margin);
    }

    /**
     * The start made for the site whose core column is in chunk (cx, cz), or null (none made yet) - the world test asks
     * it (an empty one: isSizeableStructure false, settlementId -1). Server.
     */
    public StructureSettlementStartSC startAt(int cx, int cz) {
        Object st = structureMap.get(Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(cx, cz)));
        return st instanceof StructureSettlementStartSC ? (StructureSettlementStartSC) st : null;
    }

    /** SettlementSC.init (both sides, always): the start's and the pieces' names - a saved world loads its starts by them. */
    public static void registerStructures() {
        MapGenStructureIO.registerStructure(StructureSettlementStartSC.class, START_NAME);
        MapGenStructureIO.func_143031_a(ComponentSettlementSC.class, PIECE_PREFIX + "Part");
        MapGenStructureIO.func_143031_a(ComponentSetGroundSC.class, PIECE_PREFIX + "Ground");
        MapGenStructureIO.func_143031_a(ComponentSetHeartSC.class, PIECE_PREFIX + "Heart");
        MapGenStructureIO.func_143031_a(ComponentSetStreetSC.class, PIECE_PREFIX + "Street");
        MapGenStructureIO.func_143031_a(ComponentSetGateSC.class, PIECE_PREFIX + "Gate");
        MapGenStructureIO.func_143031_a(ComponentSetTowerSC.class, PIECE_PREFIX + "Tower");
        MapGenStructureIO.func_143031_a(ComponentSetWorkshopSC.class, PIECE_PREFIX + "Workshop");
        MapGenStructureIO.func_143031_a(ComponentSetHouseSC.class, PIECE_PREFIX + "House");
        MapGenStructureIO.func_143031_a(ComponentSetDockSC.class, PIECE_PREFIX + "Dock");
    }
}
