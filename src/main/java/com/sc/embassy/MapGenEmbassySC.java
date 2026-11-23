package com.sc.embassy;

import java.util.ArrayList;
import java.util.List;

import com.sc.settle.RosterSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;

import cpw.mods.fml.common.FMLLog;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.MapGenStructureData;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureStart;

/**
 * PACKAGE G (docs/plan-substrate-tech-f6.md §5.3, rules ПЕ-1, ПЕ-4, ПЕ-8, ПК-27): the embassies as a vanilla structure of the Overworld
 * - a copy of MapGenSettlementSC's ways under the names EmbassySC.START_NAME "SCOwEmbassy" / "SCOwEmb*" (the data in the Overworld's
 * data/SCOwEmbassy.dat). range = RANGE (26 chunks since ЛП-2 of the audit 2026-11-04 - the long funnels; a footprint reaches 9 - EmbassyLayoutSC.REACH_MAX - so a start is always made
 * before the first chunk of its footprint). One instance a server's Overworld: EmbassySC.mapGen(world).
 * <ul>
 * <li>EmbassyWorldGenSC calls func_151539_a(provider, world, cx, cz, null) first thing in every Overworld chunk's
 * ReplaceBiomeBlocks - before its blocks are levelled, whoever made the chunk (a player walking, a bridge, a rocket, /tp) -, and
 * generateStructuresInChunk from its populate (IWorldGenerator, weight 1000);</li>
 * <li>canSpawnStructureAtCoords(cx, cz): SettlementSC.embassiesGenerate() and EmbassySiteSC.siteAtChunk(world, cx, cz) != null;</li>
 * <li>getStructureStart: the plan (EmbassyLayoutSC.plan of the site and its floorY); its ground explored (EmbassySiteSC.explored: a
 * footprint chunk loaded or on disk) or a player's field zone reaching it (EmbassySiteSC.zoneNear) and no record at its centre - an
 * EMPTY start (ПЕ-4: no half-built city in old land, none over a base); else the
 * start with its pieces (EmbassyPiecesSC.pieces after the ground piece ComponentEmbGroundSC) and the record
 * (EmbassyLayoutSC.record - SettlementRegistrySC.add after reserveIds of every embassy start's id, or the record already at that
 * centre: atCentre), the new record's beams told (EmbassyBeamSC.recordMade).</li>
 * </ul>
 * Fixed (the coordinator's): the class, RANGE, the names, registerStructures, ensureLoaded, startAt, covers. The bodies of
 * canSpawnStructureAtCoords and getStructureStart are package G's, and builtIn (the levelling's look-up of the starts over a chunk).
 * A record found at the centre while the footprint is new land (the Overworld's region files deleted, the save's data/ kept) has its
 * roster started afresh (renew - as MapGenSettlementSC's).
 * <p>
 * «Лётное поле посольства» (docs/plan-embassy-airfield-core.md §4.1, the coordinator's): a NEW record's airfield is decided in
 * getStructureStart (EmbassyAirfieldSC.decideFor - embassy.airfield and the land), its plan carries it (the pieces of the field come
 * with EmbassyPiecesSC.pieces, the ground piece keeps it as "Air"), the record keeps it as "X"; a record already there keeps its own
 * (renew keeps "X"). RANGE: the field's funnels reach FUNNEL_REACH (380, ЛП-2) along the street - within RANGE chunks of the core's.
 */
public class MapGenEmbassySC extends MapGenStructure {

    /**
     * Chunks round each chunk made that are asked for a start: a footprint reaches 9, an ILS end's long approach funnel (ЛП-2 of the audit
     * 2026-11-04: EmbassyAirfieldSC.FUNNEL_REACH, 380 along the street) 24 - the start is made before the first chunk of its funnels
     * (was 10; canSpawnStructureAtCoords asks a region's candidates' chunks first, cheaply, for the six times as many chunks).
     */
    public static final int RANGE = (EmbassyAirfieldSC.FUNNEL_REACH + EmbassyAirfieldSC.FUNNEL_HALF) / 16 + 2;

    /** canSpawnStructureAtCoords' look-up: the last region asked (world seed, grid, rx, rz) and its candidates' chunks. */
    private long candSeed;
    private int candGrid, candRx = Integer.MIN_VALUE, candRz = Integer.MIN_VALUE;
    private final int[] candChunks = new int[EmbassySiteSC.CANDIDATES * 2];

    public MapGenEmbassySC() {
        range = RANGE;
    }

    @Override
    public String func_143025_a() {
        return EmbassySC.START_NAME;
    }

    @Override
    protected boolean canSpawnStructureAtCoords(int cx, int cz) {
        return worldObj != null && SettlementSC.embassiesGenerate() && mayHold(worldObj, cx, cz) && EmbassySiteSC.siteAtChunk(worldObj, cx, cz) != null;
    }

    /**
     * Chunk (cx, cz) can hold an embassy's core at all (siteAtChunk's own first steps, cheaply): it is a candidate's chunk of its region,
     * or the guaranteed embassy's. Server thread.
     */
    private boolean mayHold(World w, int cx, int cz) {
        if (!EmbassySiteSC.worldFits(w)) {
            return false;
        }
        long seed = w.getSeed();
        int grid = EmbassySiteSC.grid(), rx = EmbassySiteSC.region(cx << 4, grid), rz = EmbassySiteSC.region(cz << 4, grid);
        if (seed != candSeed || grid != candGrid || rx != candRx || rz != candRz) {
            int[][] c = EmbassySiteSC.candidates(seed, rx, rz, grid);
            for (int i = 0; i < EmbassySiteSC.CANDIDATES; i++) {
                candChunks[2 * i] = i < c.length ? c[i][0] >> 4 : Integer.MIN_VALUE;
                candChunks[2 * i + 1] = i < c.length ? c[i][1] >> 4 : Integer.MIN_VALUE;
            }
            candSeed = seed;
            candGrid = grid;
            candRx = rx;
            candRz = rz;
        }
        for (int i = 0; i < EmbassySiteSC.CANDIDATES; i++) {
            if (candChunks[2 * i] == cx && candChunks[2 * i + 1] == cz) {
                return true;
            }
        }
        EmbassySiteSC.Site g = EmbassySiteSC.guaranteed(w);
        return g != null && g.chunkX() == cx && g.chunkZ() == cz;
    }

    @Override
    protected StructureStart getStructureStart(int cx, int cz) {
        EmbassySiteSC.Site site = worldObj == null ? null : EmbassySiteSC.siteAtChunk(worldObj, cx, cz);
        SettlementRegistrySC reg = site == null ? null : SettlementRegistrySC.get(worldObj);
        if (reg == null || !(worldObj instanceof WorldServer)) {
            return new StructureEmbassyStartSC(cx, cz);
        }
        WorldServer w = (WorldServer) worldObj;
        long seed = w.getSeed(), now = w.getTotalWorldTime();
        SettlementRegistrySC.Settlement rec = reg.atCentre(EmbassySiteSC.DIM, site.x, site.z);
        EmbassyLayoutSC.Plan p;
        boolean explored = EmbassySiteSC.explored(w, site);
        if (rec == null && explored) {                          // ПЕ-4: old land - no embassy here
            FMLLog.info("[Silicon Age] Tuners' Embassies: no embassy at %d %d - its ground was explored before (old land)", site.x, site.z);
            return new StructureEmbassyStartSC(cx, cz);
        }
        if (rec == null && EmbassySiteSC.zoneNear(w, site)) {   // a player's field reaches its land: never a city over his base
            FMLLog.info("[Silicon Age] Tuners' Embassies: no embassy at %d %d - a player's field zone reaches its land", site.x, site.z);
            return new StructureEmbassyStartSC(cx, cz);
        }
        if (rec == null) {
            // «Лётное поле» (docs/plan-embassy-airfield-core.md §4.1, the coordinator's): the airfield is decided here, once, for a new
            // embassy only - embassy.airfield (AUTO: the add-on loaded now) and the land (package G's EmbassyAirSiteSC.choose, the biome
            // layers); null: no field, no "X" - the embassy is exactly as before. A record's decision is its own (below): never changed.
            int[] air = EmbassyAirfieldSC.decideFor(w, site);
            p = EmbassyLayoutSC.plan(seed, site, site.floorY, air);
            reg.reserveIds(maxStartId());                       // the registry's file lost, data/SCOwEmbassy.dat kept: no id twice
            rec = reg.add(EmbassyLayoutSC.record(p, EmbassySiteSC.DIM, now));
            FMLLog.info("[Silicon Age] Tuners' Embassies: embassy %d at %d %d (dome %d, floor %d%s) - its start is made%s", rec.id, p.coreX,
                    p.coreZ, p.radius, p.floorY, site.guaranteed ? ", the guaranteed one" : "", air == null ? ""
                    : ", airfield " + EmbassyAirfieldSC.KIND_KEYS[Math.max(0, Math.min(EmbassyAirfieldSC.FULL, air[0]))] + " (side " + air[1] + ")");
            EmbassyBeamSC.recordMade(w, rec);
            if (p.field != null) {
                EmbassyAirfieldSC.changed(w);
            }
        } else {
            p = EmbassyLayoutSC.plan(seed, site, site.floorY, EmbassyAirfieldSC.decisionOf(rec));
            if (rec.floorY != p.floorY || rec.radius != p.radius || rec.seed != site.seed) {
                p = EmbassyLayoutSC.planOf(rec, seed);          // the record's own numbers (a lost record its core made again)
            }
            if (!explored) {                                    // the Overworld's land made again, the registry kept
                renew(rec, p, now);
                reg.reindex(rec);
                reg.markDirty();
                FMLLog.info("[Silicon Age] Tuners' Embassies: embassy %d at %d %d is built again on new ground - its roster starts afresh",
                        rec.id, p.coreX, p.coreZ);
                if (p.field != null) {
                    EmbassyAirfieldSC.changed(w);
                }
            }
        }
        List<StructureComponent> parts = new ArrayList<StructureComponent>();
        parts.add(new ComponentEmbGroundSC(rec.id, p));         // the ground first: the buildings of a window come after it
        parts.addAll(EmbassyPiecesSC.pieces(rec.id, p));
        return new StructureEmbassyStartSC(cx, cz, rec.id, parts);
    }

    /** The highest record id of the embassy starts made or read in (vanilla's reading ran before getStructureStart), 0 none. */
    private int maxStartId() {
        int max = 0;
        for (Object o : structureMap.values()) {
            if (o instanceof StructureEmbassyStartSC) {
                max = Math.max(max, ((StructureEmbassyStartSC) o).settlementId());
            }
        }
        return max;
    }

    /**
     * A record whose land is made again (no creature of it can be anywhere): its roster starts as a new embassy's - the plan's site,
     * Vokzal, points and named slots (NEVER); the Clone Capsule's day and the «Достройка»'s pass go; its id, its name and the players'
     * local records stay. Pure.
     */
    private static void renew(SettlementRegistrySC.Settlement rec, EmbassyLayoutSC.Plan p, long now) {
        SettlementRegistrySC.Settlement plan = EmbassyLayoutSC.record(p, rec.dim, now);
        rec.y = plan.y;
        rec.floorY = plan.floorY;
        rec.radius = plan.radius;
        rec.version = plan.version;
        rec.madeAt = now;
        EmbassyLayoutSC.fill(rec, p);
        rec.slots.clear();
        rec.slots.addAll(plan.slots);
        NBTTagCompound c = rec.extra.getCompoundTag(RosterSC.EXTRA);
        c.removeTag(RosterSC.CLONE_DAY);
        rec.extra.setTag(RosterSC.EXTRA, c);
        rec.extra.removeTag(EmbassyRepairSC.EXTRA);
    }

    /**
     * The built embassy starts (made and not empty) whose box meets the columns [minX..maxX] x [minZ..maxZ] - the levelling of a
     * chunk's arrays asks it (EmbassyWorldGenSC). Server.
     */
    public List<StructureEmbassyStartSC> builtIn(World w, int minX, int minZ, int maxX, int maxZ) {
        ensureLoaded(w);
        List<StructureEmbassyStartSC> out = new ArrayList<StructureEmbassyStartSC>();
        for (Object o : structureMap.values()) {
            if (o instanceof StructureEmbassyStartSC && ((StructureEmbassyStartSC) o).isSizeableStructure()
                    && ((StructureEmbassyStartSC) o).getBoundingBox().intersectsWith(minX, minZ, maxX, maxZ)) {
                out.add((StructureEmbassyStartSC) o);
            }
        }
        return out;
    }

    /** Whether this world's saved starts were read in (ensureLoaded). */
    private World loadedFor;

    /**
     * The saved starts of `w` in structureMap now - vanilla reads them only on the first func_151539_a / generateStructuresInChunk
     * (its func_143027_a is private): the «Достройка», the bans, the command and the tests may ask before. The same reading as
     * vanilla's (data/SCOwEmbassy.dat in the world's perWorldStorage, MapGenStructureIO.func_143035_a a start); vanilla's own read
     * later puts the same starts again. Server.
     */
    public void ensureLoaded(World w) {
        if (w == null || w.isRemote || loadedFor == w) {
            return;
        }
        loadedFor = w;
        if (worldObj == null) {
            worldObj = w;
        }
        MapGenStructureData d = (MapGenStructureData) w.perWorldStorage.loadData(MapGenStructureData.class, func_143025_a());
        if (d == null) {
            return;
        }
        NBTTagCompound all = d.func_143041_a();
        for (Object k : all.func_150296_c()) {
            NBTBase b = all.getTag((String) k);
            if (b == null || b.getId() != 10) {
                continue;
            }
            NBTTagCompound t = (NBTTagCompound) b;
            if (t.hasKey("ChunkX") && t.hasKey("ChunkZ")) {
                Long key = Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(t.getInteger("ChunkX"), t.getInteger("ChunkZ")));
                if (!structureMap.containsKey(key)) {
                    StructureStart st = MapGenStructureIO.func_143035_a(t, w);
                    if (st != null) {
                        structureMap.put(key, st);
                    }
                }
            }
        }
    }

    /** The start made for the embassy whose core column is in chunk (cx, cz) of `w`, or null (none made yet; an empty one: settlementId -1). */
    public StructureEmbassyStartSC startAt(World w, int cx, int cz) {
        ensureLoaded(w);
        Object st = structureMap.get(Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(cx, cz)));
        return st instanceof StructureEmbassyStartSC ? (StructureEmbassyStartSC) st : null;
    }

    /**
     * A built embassy's box (its start made and not empty) holds column (x, z), `margin` round it - the populate / decorate bans and
     * the «Достройка» ask it. Only the embassies within REACH_MAX (an airfield's funnels: FUNNEL_REACH; a piece of it must meet the column - piecesMeet) + margin of the column can (their
     * starts' chunks). Server.
     */
    public boolean covers(World w, int x, int z, int margin) {
        ensureLoaded(w);
        // the airfield's approach funnels reach EmbassyAirfieldSC.FUNNEL_REACH past the core along the street (the box grows with them)
        int reach = Math.max(EmbassyLayoutSC.REACH_MAX, EmbassyAirfieldSC.FUNNEL_REACH) + margin;
        for (Object o : structureMap.values()) {
            if (!(o instanceof StructureStart) || !((StructureStart) o).isSizeableStructure()) {
                continue;
            }
            StructureStart st = (StructureStart) o;
            int cx = (st.func_143019_e() << 4) + 8, cz = (st.func_143018_f() << 4) + 8;
            if (Math.abs(cx - x) <= reach + 16 && Math.abs(cz - z) <= reach + 16
                    && st.getBoundingBox().intersectsWith(x - margin, z - margin, x + margin, z + margin) && piecesMeet(st, x, z, margin)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Some piece of start `st` meets the columns (x, z) +- `margin` (ЛП-2 of the audit 2026-11-04: the long funnels stretch the start's
     * box to +-380 along the street - its strips beside them are not the embassy's; risk 4 of docs/plan-embassy-airfield-core.md).
     */
    private static boolean piecesMeet(StructureStart st, int x, int z, int margin) {
        for (Object c : st.getComponents()) {
            if (c instanceof StructureComponent && ((StructureComponent) c).getBoundingBox().intersectsWith(x - margin, z - margin, x + margin, z + margin)) {
                return true;
            }
        }
        return false;
    }

    /** EmbassySC.init (both sides, always): the start's and the pieces' names - a saved world loads its starts by them. */
    public static void registerStructures() {
        MapGenStructureIO.registerStructure(StructureEmbassyStartSC.class, EmbassySC.START_NAME);
        MapGenStructureIO.func_143031_a(ComponentEmbassySC.class, EmbassySC.PIECE_PREFIX + "Part");
        MapGenStructureIO.func_143031_a(ComponentEmbGroundSC.class, EmbassySC.PIECE_PREFIX + "Ground");
        EmbassyPiecesSC.register();
    }
}
