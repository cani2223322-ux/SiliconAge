package com.sc.dim;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import com.sc.ruin.RuinLayoutSC;
import com.sc.ruin.StructureRuinStartSC;
import com.sc.settle.gen.SettlementLayoutSC;

import cpw.mods.fml.common.FMLLog;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.WorldServer;

/**
 * Подложка Ф4 (docs/plan-substrate-tech-f4.md §3.1, rules ПМ-10..ПМ-13): which biome a plateau cell is - the Silicon Plateaus
 * of Ф1, or since Ф4 the Quartzite Ridges, the Oxide Fields or the Rift. A cell is the 256 x 256 square between the Scribe
 * lines (cell n spans n·256 .. n·256 + 255 - x >> 8); its canyon belts stay the Scribe Canyons, and a band INNER_MARGIN
 * blocks from every line stays the plateaus' ground and biome in every cell - the new terrain begins inside it, so a canyon's
 * walls, the Dead Nodes, the camps and two neighbouring cells always meet the plateau of Ф1.
 * <p>
 * The kind of a cell (pure, pureKind): never the spawn's cells (SPAWN_RING round cell (0, 0)), never a cell with a
 * settlement's site or an abandoned lithograph (their layouts stand on the plateau's terraces - SettlementLayoutSC.site,
 * RuinLayoutSC.lithoSite); else by the cell's hash: the Rift RIFT_PCT (only with the cell's middle RIFT_MIN_DIST and further
 * from the world's spawn), the Ridges RIDGES_PCT, the Oxide Fields OXIDE_PCT, the rest the plateaus. TODO(balance): the shares.
 * <p>
 * ПМ-11 «the land of Ф1..Ф3 is never torn»: on the server the kind is decided once per cell, when it is first needed (its first
 * chunk with inner columns is made, or anything asks the biome there) and kept in the Substrate's own WorldSavedData DATA
 * (DIM-1414/data/SCSi14Cells.dat, written at once - that file alone, saveCellsNow: a decision is rare, and it must be on disk
 * before the cell's chunks - else a crash before the autosave would find them «explored» and make the cell LEGACY against its
 * new ground). A cell whose pure kind is new but whose inner chunks exist on disk already (a world of Ф1..Ф3 explored there -
 * StructureRuinStartSC.explored) stays the plateaus for good: no half-ridge cell, no cliff against an old chunk. Clients (no
 * seed, no data) answer pureKind - a client only reads a chunk's own biome array anyway.
 * <p>
 * Server thread only for the world-aware calls (chunk generation, the spawn director). Shared core: only the coordinator edits
 * this file.
 */
public final class SubstrateCellsSC {

    /** A cell's side (= the Scribe grid). */
    public static final int CELL = SubstrateTerrainSC.GRID;
    /**
     * Within this many blocks of a Scribe line every cell's ground is the plateau's (the canyon belt is 24 of them): the new
     * biomes' terrain starts inside, and the band is never part of the «explored» check (ПМ-11).
     */
    public static final int INNER_MARGIN = 40;
    /** ПМ-10: the shares of the cells that may change (%): the Rift (far cells only), the Ridges, the Oxide Fields. TODO(balance) */
    public static final int RIFT_PCT = 8, RIDGES_PCT = 30, OXIDE_PCT = 25;
    /** ПМ-10: the Rift only this far (blocks) from the world's spawn point to the cell's middle («не ближе 1500 блоков от спавна»). */
    public static final int RIFT_MIN_DIST = 1500;
    /** ПМ-10: cells within this many of the spawn's cell (0, 0) stay plateaus (the spawn, the «Первый причал» at cell (1, 0)). */
    public static final int SPAWN_RING = 1;
    /** The data's name (the Substrate's perWorldStorage). */
    public static final String DATA = "SCSi14Cells";
    /** A kept decision: the low bits the kind; LEGACY - the pure kind was a new one, the cell's ground was explored before. */
    public static final int LEGACY = 0x80;

    private static final long SALT = 0xCE115F04L;
    /** The pure kinds asked lately (the chunk manager asks a column at a time): seed, then cell key -> kind. */
    private static final Map<Long, Integer> PURE = new HashMap<Long, Integer>();
    private static long pureSeed;
    private static final int PURE_MAX = 16384;

    private SubstrateCellsSC() {
    }

    // ------------------------------------------------------------------ pure

    /** The cell of a block coordinate. */
    public static int cellOf(int coord) {
        return coord >> 8;
    }

    /** One long for a cell (the data's and the cache's key). */
    public static long key(int cellX, int cellZ) {
        return (long) cellX << 32 | cellZ & 0xFFFFFFFFL;
    }

    /** The column lies inside its cell's inner square (further than INNER_MARGIN from every Scribe line). */
    public static boolean inner(int x, int z) {
        return SubstrateTerrainSC.distanceToLine(x) > INNER_MARGIN && SubstrateTerrainSC.distanceToLine(z) > INNER_MARGIN;
    }

    /** Chunk (cx, cz) holds a column of its cell's inner square (the others need no cell kind - their ground is Ф1's). */
    public static boolean chunkHasInner(int cx, int cz) {
        int ox = (cx & 15) << 4, oz = (cz & 15) << 4, lo = INNER_MARGIN + 1, hi = CELL - INNER_MARGIN - 1;
        return ox + 15 >= lo && ox <= hi && oz + 15 >= lo && oz <= hi;
    }

    /** The column's biome kind in a cell of kind `cellKind`: the canyon belt the canyons', the margin band the plateaus', else the cell's. */
    public static int columnKind(int cellKind, int x, int z) {
        if (SubstrateTerrainSC.inCanyonBelt(x, z)) {
            return BiomeSubstrateSC.CANYONS;
        }
        return inner(x, z) ? cellKind : BiomeSubstrateSC.PLATEAUS;
    }

    /** ПМ-10: the cell's kind by the seed alone (PLATEAUS, RIDGES, OXIDE or RIFT). Pure (cached). */
    public static int pureKind(long seed, int cellX, int cellZ) {
        Long k = Long.valueOf(key(cellX, cellZ));
        synchronized (PURE) {
            if (pureSeed != seed || PURE.size() > PURE_MAX) {
                PURE.clear();
                pureSeed = seed;
            }
            Integer got = PURE.get(k);
            if (got != null) {
                return got.intValue();
            }
        }
        int kind = computeKind(seed, cellX, cellZ);
        synchronized (PURE) {
            if (pureSeed == seed) {
                PURE.put(k, Integer.valueOf(kind));
            }
        }
        return kind;
    }

    private static int computeKind(long seed, int cellX, int cellZ) {
        if (Math.abs(cellX) <= SPAWN_RING && Math.abs(cellZ) <= SPAWN_RING) {
            return BiomeSubstrateSC.PLATEAUS;                       // the spawn and the first dock
        }
        SettlementLayoutSC.Site s = SettlementLayoutSC.site(seed, cellX >> 1, cellZ >> 1);      // a region = 2 x 2 cells
        if (s != null && s.x >> 8 == cellX && s.z >> 8 == cellZ) {
            return BiomeSubstrateSC.PLATEAUS;                       // a settlement stands on this cell's terraces
        }
        if (RuinLayoutSC.lithoSite(seed, cellX, cellZ) != null) {
            return BiomeSubstrateSC.PLATEAUS;                       // so does a lithograph's hut (its hall under them)
        }
        long h = SubstrateTerrainSC.mix(seed ^ SALT, cellX, cellZ);
        int roll = SubstrateTerrainSC.pick(h, 100);
        double mx = cellX * CELL + CELL / 2 - WorldProviderSubstrateSC.SPAWN_X, mz = cellZ * CELL + CELL / 2 - WorldProviderSubstrateSC.SPAWN_Z;
        boolean far = mx * mx + mz * mz >= (double) RIFT_MIN_DIST * RIFT_MIN_DIST;
        if (far && roll < RIFT_PCT) {
            return BiomeSubstrateSC.RIFT;
        }
        roll = SubstrateTerrainSC.pick(SubstrateTerrainSC.mix(h, 1, 0), 100);        // near cells: the Rift's share goes to the others
        if (roll < RIDGES_PCT) {
            return BiomeSubstrateSC.RIDGES;
        }
        return roll < RIDGES_PCT + OXIDE_PCT ? BiomeSubstrateSC.OXIDE : BiomeSubstrateSC.PLATEAUS;
    }

    /** The column's biome kind by the seed alone (clients, the self-test). */
    public static int biomeKindPure(long seed, int x, int z) {
        if (SubstrateTerrainSC.inCanyonBelt(x, z) || !inner(x, z)) {
            return columnKind(BiomeSubstrateSC.PLATEAUS, x, z);    // no cell kind needed (cheap)
        }
        return pureKind(seed, cellOf(x), cellOf(z));
    }

    // ------------------------------------------------------------------ the world (server: decided once, kept)

    /**
     * The cell's kind in world `w`: the server's kept decision (made now if none - ПМ-11); a client, a world that isn't the
     * Substrate or has no storage: pureKind.
     */
    public static int cellKind(World w, int cellX, int cellZ) {
        long seed = w == null ? 0L : w.getSeed();
        if (w == null || w.isRemote || !(w instanceof WorldServer) || w.perWorldStorage == null) {
            return pureKind(seed, cellX, cellZ);
        }
        int pure = pureKind(seed, cellX, cellZ);
        if (pure == BiomeSubstrateSC.PLATEAUS) {
            return pure;                                           // nothing to decide: Ф1's plateau stays a plateau
        }
        Data d = data(w);
        Long k = Long.valueOf(key(cellX, cellZ));
        Integer kept = d.kinds.get(k);
        if (kept != null) {
            return kept.intValue() & ~LEGACY;
        }
        int x0 = cellX * CELL + INNER_MARGIN + 1, z0 = cellZ * CELL + INNER_MARGIN + 1;
        int x1 = cellX * CELL + CELL - INNER_MARGIN - 1, z1 = cellZ * CELL + CELL - INNER_MARGIN - 1;     // the inner square (inner())
        int decided = pure;
        if (StructureRuinStartSC.explored(w, x0, z0, x1, z1)) {
            decided = BiomeSubstrateSC.PLATEAUS | LEGACY;
            FMLLog.info("[Silicon Age] Si-14 Substrate: cell %d %d stays the Silicon Plateaus (it would be the %s) - its ground was explored"
                    + " before (world of 0.2.1 or older)", cellX, cellZ, BiomeSubstrateSC.NAMES[pure]);
        }
        d.kinds.put(k, Integer.valueOf(decided));
        d.markDirty();
        saveCellsNow((WorldServer) w, d);   // on disk before the cell's chunks are (they are written as they unload): a crash keeps it
        return decided & ~LEGACY;
    }

    /**
     * ПМ-5: writes only DIM-1414/data/SCSi14Cells.dat, now, as MapStorage.saveData does (a "data" tag in a gzip'd NBT) - not
     * all the dimension's data in the middle of chunk generation. Through a .tmp file, so a crash mid-write leaves the old file.
     * Written - no longer dirty; failed - stays dirty (the game's next save writes it) and a line in the log.
     */
    private static void saveCellsNow(WorldServer w, Data d) {
        try {
            File dir = new File(w.getChunkSaveLocation(), "data");     // the dimension's folder (perWorldStorage writes there too)
            if (!dir.isDirectory() && !dir.mkdirs()) {
                throw new IOException("cannot create " + dir);
            }
            File file = new File(dir, DATA + ".dat"), tmp = new File(dir, DATA + ".dat.tmp");
            NBTTagCompound inner = new NBTTagCompound();
            d.writeToNBT(inner);
            NBTTagCompound root = new NBTTagCompound();
            root.setTag("data", inner);
            write(root, tmp);
            if (!tmp.renameTo(file)) {                              // Windows: no rename over an existing file
                if (file.exists() && !file.delete() || !tmp.renameTo(file)) {
                    write(root, file);                              // can't replace it: straight into the file itself
                    tmp.delete();
                }
            }
            d.setDirty(false);
        } catch (Exception e) {
            FMLLog.warning("[Silicon Age] Si-14 Substrate: could not write %s.dat now (%s) - it is written with the world's next save",
                    DATA, e);
        }
    }

    private static void write(NBTTagCompound root, File f) throws IOException {
        OutputStream out = new FileOutputStream(f);
        try {
            CompressedStreamTools.writeCompressed(root, out);
        } finally {
            out.close();
        }
    }

    /** The column's biome kind in world `w` (cellKind for its cell's inner square, the canyons / plateaus in the belt and the band). */
    public static int biomeKind(World w, int x, int z) {
        if (SubstrateTerrainSC.inCanyonBelt(x, z) || !inner(x, z)) {
            return columnKind(BiomeSubstrateSC.PLATEAUS, x, z);
        }
        return cellKind(w, cellOf(x), cellOf(z));
    }

    /** The cell kept as LEGACY (its pure kind was a new one; it stays the plateaus) - the handbook's note, the world test. Server. */
    public static boolean legacy(World w, int cellX, int cellZ) {
        if (w == null || w.isRemote || w.perWorldStorage == null) {
            return false;
        }
        Integer kept = data(w).kinds.get(Long.valueOf(key(cellX, cellZ)));
        return kept != null && (kept.intValue() & LEGACY) != 0;
    }

    /** The world test: forget a cell's decision (only a test world's cells). Server. */
    public static void forget(World w, int cellX, int cellZ) {
        if (w != null && !w.isRemote && w.perWorldStorage != null) {
            Data d = data(w);
            if (d.kinds.remove(Long.valueOf(key(cellX, cellZ))) != null) {
                d.markDirty();
            }
        }
    }

    /** Server stopped: the pure cache (the next world has another seed anyway). */
    public static void clearAll() {
        synchronized (PURE) {
            PURE.clear();
        }
    }

    private static Data data(World w) {
        Data d = (Data) w.perWorldStorage.loadData(Data.class, DATA);
        if (d == null) {
            d = new Data(DATA);
            w.perWorldStorage.setData(DATA, d);
        }
        return d;
    }

    /** DIM-1414/data/SCSi14Cells.dat: the decided cells (only those whose pure kind is a new one). */
    public static final class Data extends WorldSavedData {
        final Map<Long, Integer> kinds = new HashMap<Long, Integer>();

        public Data(String name) {
            super(name);
        }

        @Override
        public void readFromNBT(NBTTagCompound nbt) {
            kinds.clear();
            NBTTagList l = nbt.getTagList("Cells", 10);
            for (int i = 0; i < l.tagCount(); i++) {
                NBTTagCompound c = l.getCompoundTagAt(i);
                kinds.put(Long.valueOf(key(c.getInteger("X"), c.getInteger("Z"))), Integer.valueOf(c.getByte("K") & 0xFF));
            }
        }

        @Override
        public void writeToNBT(NBTTagCompound nbt) {
            NBTTagList l = new NBTTagList();
            for (Map.Entry<Long, Integer> e : kinds.entrySet()) {
                NBTTagCompound c = new NBTTagCompound();
                long k = e.getKey().longValue();
                c.setInteger("X", (int) (k >> 32));
                c.setInteger("Z", (int) k);
                c.setByte("K", (byte) e.getValue().intValue());
                l.appendTag(c);
            }
            nbt.setTag("Cells", l);
        }
    }
}
