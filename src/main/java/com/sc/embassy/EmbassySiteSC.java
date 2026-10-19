package com.sc.embassy;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.sc.dim.SubstrateConfigSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.settle.SettlementSizeSC;
import com.sc.tileentity.TileEntityFieldGeneratorSC;

import cpw.mods.fml.common.FMLLog;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.BiomeGenMutated;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.structure.MapGenVillage;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.DimensionManager;

/**
 * Подложка Ф6 «Посольства Наладчиков» (docs/plan-substrate-tech-f6.md §5.1, rules ПЕ-1..ПЕ-4): where the embassies stand - written
 * whole by the coordinator (as ArenaLayoutSC in Ф5): the generator (package G), the Direction Finder and the first-login line
 * (package T), the beam (package S), RosterSC.restore and the tests (package D) ask the same thing.
 * <ul>
 * <li><b>The grid</b> (ПЕ-1): a region embassy.embassyGrid x embassyGrid (2048) has an embassy with embassyChance % (pure, by the
 * world seed); its CANDIDATES points (the first one first, then the spares - pure hashes) lie within a quarter of the region of its
 * middle, so two regions' embassies are at least half a region (1024) apart. The first candidate whose land fits is the region's
 * embassy; none fits - none there. Never closer than embassySpawnMin (1500) to the world's spawn. A region whose candidate already
 * has an embassy's record or start (made) keeps it whatever the numbers, the spawn or the land rules are now (made(): no second city
 * in the region, the Finder and a lost record's restore find it).</li>
 * <li><b>The land</b> (ПЕ-3): only the Overworld, its type DEFAULT or LARGE_BIOMES, its structures on. The biomes are read through
 * the world's WorldChunkManager (the generator's own layers - no chunk is loaded or made; a vanilla mutation, id + 128, by its base
 * biome's Forge types - Forge only guesses the mutations', SANDY for all): over the area (the levelled disc and the blend band,
 * area(radius) = radius + 31 + 8 <= 135) at least GOOD_PCT % PLAINS, SAVANNA or SANDY, at most FOREST_PCT % FOREST, no OCEAN, BEACH,
 * SWAMP (a RIVER is filled in by the levelling, its ends past the band stay); inside the disc's rim (levelRadius - EDGE_IN: all of it
 * cut to the floor) at most HILLS_PCT % HILLS; from the rim to the area + BAND_NEAR (16) at most EDGE_PCT % HILLS or rootHeight >=
 * EDGE_ROOT and no heightVariation >= SHATTERED_VAR (the blend band eases only 8 blocks back to the land: hills there stand as a wall
 * round the city); within the area + BAND_NEAR no MOUNTAIN and no rootHeight >= HIGH_ROOT (the plateaus: Forge calls savanna's and
 * mesa's PLAINS / SANDY, their tops at 90..100 over a floor of at most 72; Extreme Hills M); within + BAND_MID (64) no JUNGLE,
 * MUSHROOM; within + BAND_FAR (192) no MAGICAL, no SPOOKY but vanilla's Roofed Forest (Thaumcraft's magical forests and the taint).
 * The integrator's numbers (2026-10-19): the first rules (no river or hills within + 16, 70 % open, vanilla's Roofed Forest spooky,
 * 5 candidates, 64 tries) let 0 of 9000 sampled points of six seeds fit - his let about 2 % fit, a rolled region get one about a
 * third of the time, the guaranteed one stand in each of twelve seeds. The review's (LAND_RULES 3, the same twelve-seed measure in
 * docs/plan-substrate-tech-f6.md §5.1): no rim rule at all - 22 % of the rolled regions; none of the rim's hills allowed - 3 % and
 * the guaranteed one in 6 seeds of 12; EDGE_PCT 10 - 14 %, the guaranteed one in all twelve. LAND_RULES is the rules' version
 * (a «nowhere» written with older rules is decided again). The land is read in two steps (biomeProblem): the area + BAND_NEAR first
 * (a 77 x 77 read: water, mountains, the rim and the shares fail most points there), the rings out to + BAND_FAR only for a point that
 * passes it (165 x 165 - four times the samples). No vanilla village start within the area +
 * VILLAGE_CLEAR (128: a village's pieces reach 112) - its grid (32 chunks, 8 apart, salt 10387312) and the live
 * MapGenVillage.villageSpawnBiomes; no desert / jungle temple or witch hut within the area + TEMPLE_CLEAR (32; salt 14357617). The
 * floor's height comes from the same biomes: EmbassyLayoutSC.floorY of their mean rootHeight.</li>
 * <li><b>The guaranteed one</b> (ПЕ-2): one embassy between embassyRingMin and embassyRingMax (1300..2500) of the spawn - outside
 * the strongholds' ring (640..1152 and its biome shift). A grid embassy there whose ground is not explored is it; otherwise the
 * first of GUARANTEE_TRIES pure candidates in the ring whose land fits, half a region from every grid embassy and every embassy's
 * record, no chunk of its footprint on disk or loaded, no player's field zone within ZONE_CLEAR of its area (zoneNear - a grid site
 * too: its start is made empty while a zone reaches it, MapGenEmbassySC); none - the same in FALLBACK_MIN..FALLBACK_MAX;
 * none still - one line in the log. Decided once (decideGuaranteed - the world's load, or the first ask) and written into the
 * registry's world `extra` EXTRA "E" (x, z, radius, floor, seed; g - a grid one; none - nowhere, with the numbers it was tried at and v - LAND_RULES).</li>
 * </ul>
 * The sites of a region are cached (they ask the biome layers: a few milliseconds a candidate) - for the server's world seed and
 * the config's numbers; clearCache on the server's stop. Server thread only for the world's questions (WorldChunkManager's
 * IntCache is not thread-safe); the pure ones anywhere.
 */
public final class EmbassySiteSC {

    /** ПЕ-1: the points a region tries, the first first. */
    public static final int CANDIDATES = 16;
    /** ПЕ-3: the bands past the area and the shares over it (HILLS_PCT inside the rim). TODO(balance) */
    public static final int BAND_NEAR = 16, BAND_MID = 64, BAND_FAR = 192, GOOD_PCT = 40, FOREST_PCT = 60, HILLS_PCT = 25;
    /**
     * ПЕ-3: the rim - from this far inside the levelled disc's edge out to the area + BAND_NEAR: at most EDGE_PCT % of it HILLS or of a
     * rootHeight of EDGE_ROOT, none of a heightVariation of SHATTERED_VAR (shattered savanna); none of HIGH_ROOT within the area +
     * BAND_NEAR. TODO(balance)
     */
    public static final int EDGE_IN = 4, EDGE_PCT = 10;
    public static final float EDGE_ROOT = 0.3F, HIGH_ROOT = 0.8F, SHATTERED_VAR = 0.6F;
    /** ПЕ-3: the land rules' version - written with a «nowhere» decision (a different one decides again). */
    public static final int LAND_RULES = 3;
    /** ПЕ-3: a vanilla village's start, a temple no nearer than the area + this. */
    public static final int VILLAGE_CLEAR = 128, TEMPLE_CLEAR = 32;
    /** ПЕ-2: the guaranteed one's tries in each ring, the fallback ring, a player's field zone no nearer than its area + this. */
    public static final int GUARANTEE_TRIES = 128, FALLBACK_MIN = 2500, FALLBACK_MAX = 4000, ZONE_CLEAR = 32;
    /** Vanilla's grids (MapGenVillage, MapGenScatteredFeature: their defaults - checked in Forge 1614's bytecode). */
    public static final int VILLAGE_SPACING = 32, VILLAGE_SEPARATION = 8, TEMPLE_SPACING = 32, TEMPLE_SEPARATION = 8;
    public static final long VILLAGE_SALT = 10387312L, TEMPLE_SALT = 14357617L;
    /** The registry's world extra key of the guaranteed embassy's decision (the coordinator's). */
    public static final String EXTRA = "E";
    /** The Overworld's id (SettlementSC.EMBASSY_DIM). */
    public static final int DIM = SettlementSC.EMBASSY_DIM;

    private static final long SITE_SALT = 0xE3BA55EL, CAND_SALT = 0xCA4D1DL, ROLL_SALT = 0x4011L, GUAR_SALT = 0x6A2A7EEL;
    private static final BiomeDictionary.Type[] GOOD = {BiomeDictionary.Type.PLAINS, BiomeDictionary.Type.SAVANNA, BiomeDictionary.Type.SANDY};
    private static final BiomeDictionary.Type[] NOT_IN = {BiomeDictionary.Type.OCEAN, BiomeDictionary.Type.BEACH, BiomeDictionary.Type.SWAMP};
    private static final BiomeDictionary.Type[] NOT_NEAR = {BiomeDictionary.Type.MOUNTAIN};
    private static final BiomeDictionary.Type[] NOT_MID = {BiomeDictionary.Type.JUNGLE, BiomeDictionary.Type.MUSHROOM};

    /** One embassy's site: its core's column, dome, floor and own seed (its names, prices, roster). */
    public static final class Site {
        public final int x, z, radius, floorY;
        public final long seed;
        /** The guaranteed first embassy (ПЕ-2) - a grid one chosen for it is marked too. */
        public final boolean guaranteed;
        /** Read from an embassy's record or start (made()): it stands, whatever the maths of today says. */
        public final boolean made;

        public Site(int x, int z, int radius, int floorY, long seed, boolean guaranteed) {
            this(x, z, radius, floorY, seed, guaranteed, false);
        }

        public Site(int x, int z, int radius, int floorY, long seed, boolean guaranteed, boolean made) {
            this.x = x;
            this.z = z;
            this.radius = radius;
            this.floorY = floorY;
            this.seed = seed;
            this.guaranteed = guaranteed;
            this.made = made;
        }

        /** The area (the levelled disc and the blend band): area(radius). */
        public int area() {
            return EmbassySiteSC.area(radius);
        }

        public double distSq(double px, double pz) {
            double dx = px - (x + 0.5D), dz = pz - (z + 0.5D);
            return dx * dx + dz * dz;
        }

        /** The chunk of the core's column holds the start (MapGenEmbassySC). */
        public int chunkX() {
            return x >> 4;
        }

        public int chunkZ() {
            return z >> 4;
        }

        Site guaranteedCopy() {
            return new Site(x, z, radius, floorY, seed, true, made);
        }

        @Override
        public String toString() {
            return x + " " + z + " r" + radius + " floor " + floorY + (guaranteed ? " (guaranteed)" : "");
        }
    }

    /** A region's site (or NONE) for the cached world seed and numbers. */
    private static final Map<Long, Site> CACHE = new HashMap<Long, Site>();
    private static final Site NONE = new Site(0, 0, 0, 0, 0L, false);
    private static long cacheSeed;
    private static int cacheGrid, cacheChance, cacheSpawnMin, cacheSpawnX, cacheSpawnZ;
    /** The guaranteed one as read from the registry (guaranteedRead), or decided; deciding - its search runs (no recursion). */
    private static Site guaranteedSite;
    private static boolean guaranteedRead, deciding;

    private EmbassySiteSC() {
    }

    // ------------------------------------------------------------------ pure

    /** The grid in use (embassy.embassyGrid, at least 1024). */
    public static int grid() {
        return Math.max(1024, SubstrateConfigSC.embassyGrid);
    }

    /** The region of a block coordinate (floor division). */
    public static int region(int coord, int grid) {
        return coord >= 0 ? coord / grid : (coord + 1) / grid - 1;
    }

    /** ПЕ-1: region (rx, rz) has an embassy at all - chancePct % of the regions (pure). */
    public static boolean rolls(long worldSeed, int rx, int rz, int chancePct) {
        return pick(mix(worldSeed ^ ROLL_SALT, rx, rz), 100) < Math.max(0, Math.min(100, chancePct));
    }

    /** ПЕ-1: the CANDIDATES points {x, z} of region (rx, rz) - within grid / 4 of its middle, the first one first (pure). */
    public static int[][] candidates(long worldSeed, int rx, int rz, int grid) {
        int j = grid / 4, mx = rx * grid + grid / 2, mz = rz * grid + grid / 2;
        int[][] out = new int[CANDIDATES][];
        for (int i = 0; i < CANDIDATES; i++) {
            long h = mix(worldSeed ^ CAND_SALT, mix(rx, rz, i), i);
            out[i] = new int[]{mx + pick(h, 2 * j + 1) - j, mz + pick(mix(h, 1, 0), 2 * j + 1) - j};
        }
        return out;
    }

    /** An embassy's own seed at (x, z) (pure). */
    public static long siteSeed(long worldSeed, int x, int z) {
        return mix(worldSeed ^ SITE_SALT, x, z);
    }

    /** ПЕ-5: the dome's radius, 88..96 by the site's seed (SettlementSizeSC.EMBASSY.radius). */
    public static int radius(long siteSeed) {
        return SettlementSizeSC.EMBASSY.radius(siteSeed);
    }

    /** The area round the core the land is read and levelled in: radius + EmbassyLayoutSC.LEVEL_OUT + BLEND (<= 135). */
    public static int area(int radius) {
        return radius + EmbassyLayoutSC.LEVEL_OUT + EmbassyLayoutSC.BLEND;
    }

    /** ПЕ-1: (x, z) is at least `min` from the spawn (sx, sz). */
    public static boolean farFromSpawn(long x, long z, long sx, long sz, int min) {
        long dx = x - sx, dz = z - sz;
        return dx * dx + dz * dz >= (long) min * min;
    }

    /** ПЕ-3: the chunk {cx, cz} vanilla's village grid cell (vx, vz) tries (MapGenVillage.canSpawnStructureAtCoords; pure). */
    public static int[] villageStart(long worldSeed, int vx, int vz) {
        return gridStart(worldSeed, vx, vz, VILLAGE_SPACING, VILLAGE_SEPARATION, VILLAGE_SALT);
    }

    /** ПЕ-3: the chunk a temple cell (tx, tz) tries (MapGenScatteredFeature; pure). */
    public static int[] templeStart(long worldSeed, int tx, int tz) {
        return gridStart(worldSeed, tx, tz, TEMPLE_SPACING, TEMPLE_SEPARATION, TEMPLE_SALT);
    }

    /** World.setRandomSeed's formula on a Random of our own (the world's own is never touched). */
    private static int[] gridStart(long worldSeed, int cx, int cz, int spacing, int separation, long salt) {
        Random r = new Random((long) cx * 341873128712L + (long) cz * 132897987541L + worldSeed + salt);
        int x = cx * spacing + r.nextInt(spacing - separation);
        int z = cz * spacing + r.nextInt(spacing - separation);
        return new int[]{x, z};
    }

    /** The cell of a chunk coordinate in a vanilla grid of `spacing` chunks (its own negative rule). */
    public static int gridCell(int chunk, int spacing) {
        return (chunk < 0 ? chunk - (spacing - 1) : chunk) / spacing;
    }

    /** ПЕ-2: GUARANTEE_TRIES points {x, z} between `min` and `max` of the spawn (sx, sz), in order (pure). */
    public static int[][] guaranteeCandidates(long worldSeed, int sx, int sz, int min, int max) {
        int[][] out = new int[GUARANTEE_TRIES][];
        for (int i = 0; i < GUARANTEE_TRIES; i++) {
            long h = mix(worldSeed ^ GUAR_SALT, min, i);
            double a = pick(h, 3600) * Math.PI / 1800D;
            int d = min + pick(mix(h, 1, 0), Math.max(1, max - min + 1));
            out[i] = new int[]{sx + (int) Math.round(Math.cos(a) * d), sz + (int) Math.round(Math.sin(a) * d)};
        }
        return out;
    }

    // ------------------------------------------------------------------ the land (the world's biome layers - no chunk loaded)

    /**
     * ПЕ-3: the world may have embassies - a server's Overworld, DEFAULT or LARGE_BIOMES, its structures on (a client's world has no
     * seed to read the land by: false).
     */
    public static boolean worldFits(World w) {
        if (w == null || w.isRemote || w.provider == null || w.provider.dimensionId != DIM || w.getWorldInfo() == null) {
            return false;
        }
        WorldType t = w.getWorldInfo().getTerrainType();
        return (t == WorldType.DEFAULT || t == WorldType.LARGE_BIOMES) && w.getWorldInfo().isMapFeaturesEnabled();
    }

    /**
     * ПЕ-3: what is wrong with the biomes round (x, z) for an embassy of `radius` (null: they fit). `meanRoot` (one float, or null)
     * gets the area's mean rootHeight - the floor's height (EmbassyLayoutSC.floorY). Reads the biome layers at a quarter's
     * resolution (one sample a 4 x 4 cell), in two steps (the class comment): the near disc, then - only if it fits - the far rings.
     */
    public static String biomeProblem(WorldChunkManager m, int x, int z, int radius, float[] meanRoot) {
        int area = area(radius), near = area + BAND_NEAR;
        int[] count = new int[7];                   // {in the area, good, forest, inside the rim, hills inside it, on the rim, hills on it}
        double[] root = new double[1];
        String p = scan(m, x, z, radius, -1, near, count, root);
        if (p != null) {
            return p;
        }
        int inArea = count[0], good = count[1], forest = count[2], inner = count[3], hills = count[4], rim = count[5], rimHills = count[6];
        if (inArea == 0) {
            return "no biome read";
        }
        if (meanRoot != null && meanRoot.length > 0) {
            meanRoot[0] = (float) (root[0] / inArea);
        }
        if (good * 100 < GOOD_PCT * inArea) {
            return "plains / savanna / sand " + good * 100 / inArea + "% < " + GOOD_PCT + "%";
        }
        if (hills * 100 > HILLS_PCT * Math.max(1, inner)) {
            return "hills " + hills * 100 / Math.max(1, inner) + "% > " + HILLS_PCT + "%";
        }
        if (rimHills * 100 > EDGE_PCT * Math.max(1, rim)) {
            return "hills at the rim " + rimHills * 100 / Math.max(1, rim) + "% > " + EDGE_PCT + "%";
        }
        if (forest * 100 > FOREST_PCT * inArea) {
            return "forest " + forest * 100 / inArea + "% > " + FOREST_PCT + "%";
        }
        return scan(m, x, z, radius, near, area + BAND_FAR, null, null);   // the wide rings: jungle, mushroom, magical, spooky
    }

    /**
     * One read of biomeProblem: the samples more than `from` and at most `to` from (x, z) against every rule of their distance - the
     * first broken one, or null; `count` / `root` (or null) sum the area's shares and rootHeight.
     */
    private static String scan(WorldChunkManager m, int x, int z, int radius, int from, int to, int[] count, double[] root) {
        int area = area(radius), rim = radius + EmbassyLayoutSC.LEVEL_OUT - EDGE_IN;
        int q0x = (x - to) >> 2, q0z = (z - to) >> 2, n = ((2 * to) >> 2) + 2;
        BiomeGenBase[] b = m.getBiomesForGeneration(null, q0x, q0z, n, n);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                BiomeGenBase g = b[i + j * n];
                if (g == null) {
                    continue;
                }
                double dx = ((q0x + i) << 2) + 2 - (x + 0.5D), dz = ((q0z + j) << 2) + 2 - (z + 0.5D);
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= from || d > to) {
                    continue;
                }
                BiomeGenBase t = typed(g);
                if (BiomeDictionary.isBiomeOfType(t, BiomeDictionary.Type.MAGICAL)
                        || BiomeDictionary.isBiomeOfType(t, BiomeDictionary.Type.SPOOKY) && !vanillaRoofed(g)) {
                    return "magical / spooky " + g.biomeName + " within " + (int) d;
                }
                if (d <= area + BAND_MID && any(t, NOT_MID)) {
                    return "jungle / mushroom " + g.biomeName + " within " + (int) d;
                }
                if (d <= area + BAND_NEAR && (any(t, NOT_NEAR) || g.rootHeight >= HIGH_ROOT)) {
                    return "mountains / plateau " + g.biomeName + " within " + (int) d;
                }
                boolean hill = BiomeDictionary.isBiomeOfType(t, BiomeDictionary.Type.HILLS), onRim = d >= rim && d <= area + BAND_NEAR;
                if (onRim && g.heightVariation >= SHATTERED_VAR) {
                    return "shattered land at the rim " + g.biomeName + " at " + (int) d;
                }
                if (d <= area && any(t, NOT_IN)) {
                    return "water " + g.biomeName + " within " + (int) d;
                }
                if (onRim && count != null) {
                    count[5]++;
                    count[6] += hill || g.rootHeight >= EDGE_ROOT ? 1 : 0;
                }
                if (d <= area && count != null) {
                    count[0]++;
                    count[1] += any(t, GOOD) ? 1 : 0;
                    count[2] += BiomeDictionary.isBiomeOfType(t, BiomeDictionary.Type.FOREST) ? 1 : 0;
                    if (d < rim) {
                        count[3]++;
                        count[4] += hill ? 1 : 0;
                    }
                    root[0] += g.rootHeight;
                }
            }
        }
        return null;
    }

    /** The biome whose Forge types count: a vanilla mutation's (id + 128) base - Forge guesses the mutations' own (SANDY for all). */
    private static BiomeGenBase typed(BiomeGenBase g) {
        if (g instanceof BiomeGenMutated && g.biomeID >= 128) {
            BiomeGenBase base = BiomeGenBase.getBiomeGenArray()[g.biomeID - 128];
            return base != null ? base : g;
        }
        return g;
    }

    /** Vanilla's Roofed Forest (and its M): Forge calls it SPOOKY, but it is a plain dark forest - not the taint the rule keeps away. */
    private static boolean vanillaRoofed(BiomeGenBase g) {
        int id = BiomeGenBase.roofedForest.biomeID;
        return g.biomeID == id || g.biomeID == id + 128;
    }

    private static boolean any(BiomeGenBase g, BiomeDictionary.Type[] types) {
        for (BiomeDictionary.Type t : types) {
            if (BiomeDictionary.isBiomeOfType(g, t)) {
                return true;
            }
        }
        return false;
    }

    /** ПЕ-3: a vanilla village's start within `clear` of (x, z) - its grid and its live biome list. */
    public static boolean villageNear(World w, int x, int z, int clear) {
        WorldChunkManager m = w.getWorldChunkManager();
        long seed = w.getSeed();
        int c0x = gridCell((x - clear) >> 4, VILLAGE_SPACING), c1x = gridCell((x + clear) >> 4, VILLAGE_SPACING);
        int c0z = gridCell((z - clear) >> 4, VILLAGE_SPACING), c1z = gridCell((z + clear) >> 4, VILLAGE_SPACING);
        long r2 = (long) clear * clear;
        for (int vx = c0x; vx <= c1x; vx++) {
            for (int vz = c0z; vz <= c1z; vz++) {
                int[] c = villageStart(seed, vx, vz);
                long bx = (c[0] << 4) + 8, bz = (c[1] << 4) + 8, dx = bx - x, dz = bz - z;
                if (dx * dx + dz * dz <= r2 && m.areBiomesViable((int) bx, (int) bz, 0, MapGenVillage.villageSpawnBiomes)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** ПЕ-3: a desert / jungle temple or a witch hut within `clear` of (x, z) (MapGenScatteredFeature's biomes: vanilla's five). */
    public static boolean templeNear(World w, int x, int z, int clear) {
        WorldChunkManager m = w.getWorldChunkManager();
        long seed = w.getSeed();
        int c0x = gridCell((x - clear) >> 4, TEMPLE_SPACING), c1x = gridCell((x + clear) >> 4, TEMPLE_SPACING);
        int c0z = gridCell((z - clear) >> 4, TEMPLE_SPACING), c1z = gridCell((z + clear) >> 4, TEMPLE_SPACING);
        long r2 = (long) clear * clear;
        for (int tx = c0x; tx <= c1x; tx++) {
            for (int tz = c0z; tz <= c1z; tz++) {
                int[] c = templeStart(seed, tx, tz);
                long bx = (c[0] << 4) + 8, bz = (c[1] << 4) + 8, dx = bx - x, dz = bz - z;
                if (dx * dx + dz * dz > r2) {
                    continue;
                }
                BiomeGenBase g = m.getBiomeGenAt((int) bx, (int) bz);
                if (g == BiomeGenBase.desert || g == BiomeGenBase.desertHills || g == BiomeGenBase.jungle || g == BiomeGenBase.jungleHills
                        || g == BiomeGenBase.swampland) {
                    return true;
                }
            }
        }
        return false;
    }

    /** ПЕ-3: the site at (x, z) - its land fits (biomes, villages, temples) - or null. */
    public static Site fitSite(World w, int x, int z, boolean guaranteed) {
        long seed = siteSeed(w.getSeed(), x, z);
        int r = radius(seed);
        float[] root = new float[1];
        if (biomeProblem(w.getWorldChunkManager(), x, z, r, root) != null) {
            return null;
        }
        int area = area(r);
        if (villageNear(w, x, z, area + VILLAGE_CLEAR) || templeNear(w, x, z, area + TEMPLE_CLEAR)) {
            return null;
        }
        return new Site(x, z, r, EmbassyLayoutSC.floorY(root[0]), seed, guaranteed);
    }

    /** Why (x, z) doesn't fit (null: it does) - the command's and the tests' wording of fitSite. */
    public static String landProblem(World w, int x, int z) {
        int r = radius(siteSeed(w.getSeed(), x, z));
        String b = biomeProblem(w.getWorldChunkManager(), x, z, r, null);
        if (b != null) {
            return b;
        }
        int area = area(r);
        if (villageNear(w, x, z, area + VILLAGE_CLEAR)) {
            return "a village's start within " + (area + VILLAGE_CLEAR);
        }
        return templeNear(w, x, z, area + TEMPLE_CLEAR) ? "a temple within " + (area + TEMPLE_CLEAR) : null;
    }

    // ------------------------------------------------------------------ the grid's sites (cached)

    /**
     * ПЕ-1: region (rx, rz)'s embassy, or null: a candidate already made (made()), else the region rolls, then its candidates in order
     * - far enough from the spawn, the land fits, half a region from the guaranteed one (when it is not this one) - the first that
     * does. The guaranteed one standing on this very point is returned marked. Cached. Server thread.
     */
    public static Site site(World w, int rx, int rz) {
        if (!worldFits(w)) {
            return null;
        }
        checkCache(w);
        Long k = Long.valueOf(((long) rx << 32) ^ ((long) rz & 0xFFFFFFFFL));
        Site s = CACHE.get(k);
        if (s == null) {
            s = compute(w, rx, rz);
            CACHE.put(k, s == null ? NONE : s);
        }
        if (s == null || s == NONE) {
            return null;
        }
        Site g = deciding ? null : guaranteed(w);
        if (g != null && g.x == s.x && g.z == s.z) {
            return g;
        }
        if (!s.made && g != null && g.distSq(s.x + 0.5D, s.z + 0.5D) < (double) (grid() / 2) * (grid() / 2)) {
            return null;                                         // the guaranteed one is too near (a changed config): it wins
        }
        return s;
    }

    private static Site compute(World w, int rx, int rz) {
        int grid = grid();
        long seed = w.getSeed();
        int[][] cands = candidates(seed, rx, rz, grid);
        Site made = made(w, cands);
        if (made != null) {
            return made;                                        // made once: it stays the region's (another spawn, numbers, rules)
        }
        if (!rolls(seed, rx, rz, SubstrateConfigSC.embassyChance)) {
            return null;
        }
        int sx = w.getWorldInfo().getSpawnX(), sz = w.getWorldInfo().getSpawnZ();
        for (int[] c : cands) {
            if (!farFromSpawn(c[0], c[1], sx, sz, SubstrateConfigSC.embassySpawnMin)) {
                continue;
            }
            Site s = fitSite(w, c[0], c[1], false);
            if (s != null) {
                return s;
            }
        }
        return null;
    }

    /**
     * The region's candidate already made (`cands`, in order): an embassy's record at one (its own numbers), else a start made in
     * one's chunk - a built one by its ground piece (the record lost), an empty one (old land, ПЕ-4) as that candidate (it stays
     * empty: no city in the region). Null - none made. Server.
     */
    private static Site made(World w, int[][] cands) {
        SettlementRegistrySC reg = SettlementRegistrySC.get(w);
        for (int[] c : cands) {
            SettlementRegistrySC.Settlement rec = reg == null ? null : reg.atCentre(DIM, c[0], c[1]);
            if (rec != null && SettlementSC.isEmbassy(rec)) {
                return ofRecord(rec);
            }
        }
        MapGenEmbassySC gen = EmbassySC.mapGen(w);
        Site g = guaranteedRead ? guaranteedSite : null;          // read, never decided from here
        for (int[] c : gen == null ? new int[0][] : cands) {
            StructureEmbassyStartSC st = gen.startAt(w, c[0] >> 4, c[1] >> 4);
            if (st == null || g != null && g.chunkX() == c[0] >> 4 && g.chunkZ() == c[1] >> 4) {
                continue;                                       // none, or the guaranteed one's own start in that chunk
            }
            Site s = ofStart(w, st);
            if (s != null && s.x == c[0] && s.z == c[1]) {
                return s;
            }
            if (!st.isSizeableStructure()) {
                long seed = siteSeed(w.getSeed(), c[0], c[1]);
                return new Site(c[0], c[1], radius(seed), EmbassyLayoutSC.FLOOR_MIN, seed, false, true);
            }
        }
        return null;
    }

    /** An embassy record's site (its core, dome, floor and seed) - made. */
    public static Site ofRecord(SettlementRegistrySC.Settlement rec) {
        return new Site(rec.x, rec.z, rec.radius, rec.floorY, rec.seed, false, true);
    }

    /** A built start's site by its ground piece (the core, the dome, the floor; the seed of its core's column) - made; null none. */
    private static Site ofStart(World w, StructureEmbassyStartSC st) {
        ComponentEmbGroundSC g = st == null || !st.isSizeableStructure() ? null : ComponentEmbGroundSC.of(st);
        return g == null ? null : new Site(g.coreX(), g.coreZ(), g.radius(), g.floor(), siteSeed(w.getSeed(), g.coreX(), g.coreZ()), false, true);
    }

    /**
     * The built embassy start whose core is the column (x, z) - its site by the ground piece, or null: a lost record made again
     * (RosterSC.restore) when the maths of today puts no site there (siteAtCore null). Server.
     */
    public static Site siteOfStart(World w, int x, int z) {
        MapGenEmbassySC gen = worldFits(w) ? EmbassySC.mapGen(w) : null;
        Site s = gen == null ? null : ofStart(w, gen.startAt(w, x >> 4, z >> 4));
        return s != null && s.x == x && s.z == z ? s : null;
    }

    private static void checkCache(World w) {
        int sx = w.getWorldInfo().getSpawnX(), sz = w.getWorldInfo().getSpawnZ();
        if (cacheSeed != w.getSeed() || cacheGrid != grid() || cacheChance != SubstrateConfigSC.embassyChance
                || cacheSpawnMin != SubstrateConfigSC.embassySpawnMin || cacheSpawnX != sx || cacheSpawnZ != sz) {
            CACHE.clear();
            cacheSeed = w.getSeed();
            cacheGrid = grid();
            cacheChance = SubstrateConfigSC.embassyChance;
            cacheSpawnMin = SubstrateConfigSC.embassySpawnMin;
            cacheSpawnX = sx;
            cacheSpawnZ = sz;
        }
    }

    /**
     * The embassy whose core column lies in chunk (cx, cz), or null - MapGenEmbassySC.canSpawnStructureAtCoords, asked for every
     * chunk round each chunk made: a region that doesn't roll, or a chunk none of its candidates is in, answers without a biome read.
     */
    public static Site siteAtChunk(World w, int cx, int cz) {
        if (!worldFits(w)) {
            return null;
        }
        Site g = deciding ? null : guaranteed(w);
        if (g != null && g.chunkX() == cx && g.chunkZ() == cz) {
            return g;
        }
        int grid = grid(), rx = region(cx << 4, grid), rz = region(cz << 4, grid);
        if (!rolls(w.getSeed(), rx, rz, SubstrateConfigSC.embassyChance)) {
            return null;
        }
        boolean mine = false;
        for (int[] c : candidates(w.getSeed(), rx, rz, grid)) {
            mine |= c[0] >> 4 == cx && c[1] >> 4 == cz;
        }
        if (!mine) {
            return null;
        }
        Site s = site(w, rx, rz);
        return s != null && s.chunkX() == cx && s.chunkZ() == cz ? s : null;
    }

    /** The embassy (grid or guaranteed) whose core is the column (x, z), or null - a lost record made again (RosterSC.restore). */
    public static Site siteAtCore(World w, int x, int z) {
        Site s = siteAtChunk(w, x >> 4, z >> 4);
        return s != null && s.x == x && s.z == z ? s : null;
    }

    /** The embassies (grid and guaranteed) within `reach` of (x, z), nearest first. Reads the biomes of the regions round: server thread. */
    public static List<Site> near(World w, final int x, final int z, int reach) {
        List<Site> out = new ArrayList<Site>();
        if (!worldFits(w)) {
            return out;
        }
        int grid = grid();
        long r2 = (long) reach * reach;
        for (int rx = region(x - reach, grid); rx <= region(x + reach, grid); rx++) {
            for (int rz = region(z - reach, grid); rz <= region(z + reach, grid); rz++) {
                Site s = site(w, rx, rz);
                if (s != null && s.distSq(x, z) <= r2) {
                    out.add(s);
                }
            }
        }
        Site g = guaranteed(w);
        if (g != null && g.distSq(x, z) <= r2) {
            boolean had = false;
            for (Site s : out) {
                had |= s.x == g.x && s.z == g.z;
            }
            if (!had) {
                out.add(g);
            }
        }
        Collections.sort(out, new Comparator<Site>() {
            @Override
            public int compare(Site a, Site b) {
                return Double.compare(a.distSq(x, z), b.distSq(x, z));
            }
        });
        return out;
    }

    /** The nearest embassy within `reach` of (x, z), or null. */
    public static Site nearest(World w, int x, int z, int reach) {
        List<Site> l = near(w, x, z, reach);
        return l.isEmpty() ? null : l.get(0);
    }

    // ------------------------------------------------------------------ the guaranteed one (ПЕ-2)

    /**
     * The guaranteed embassy - read from the registry, decided on the first ask on a server (null: none anywhere, or no server world, or a
     * new world still looking for its spawn: the ring is measured from it, so nothing is decided before it is set).
     */
    public static Site guaranteed(World w) {
        if (deciding || !worldFits(w) || w.isRemote || w.findingSpawnPoint || w.getWorldInfo() == null || !w.getWorldInfo().isInitialized()) {
            return null;
        }
        if (!guaranteedRead) {
            guaranteedRead = true;
            guaranteedSite = read(w);
            if (guaranteedSite == null && w instanceof WorldServer && SettlementSC.embassiesGenerate()) {
                guaranteedSite = decideGuaranteed((WorldServer) w);
            }
        }
        return guaranteedSite;
    }

    /** The registry's decision, if one was made with the numbers in use (null: none yet, or «nowhere», or other numbers). */
    private static Site read(World w) {
        SettlementRegistrySC r = SettlementRegistrySC.get(w);
        NBTTagCompound e = r == null ? null : r.extra.getCompoundTag(EXTRA);
        if (e == null || !e.hasKey("x")) {
            return null;
        }
        return new Site(e.getInteger("x"), e.getInteger("z"), e.getInteger("r"), e.getInteger("f"), e.getLong("s"), true);
    }

    /**
     * ПЕ-2: chooses the guaranteed embassy now (see the class comment) and writes it into the registry; a decision already written
     * stands (only a «nowhere» made with other numbers is tried again). Server; called by the world's load (package G) or the first
     * ask. @return the site, null nowhere
     */
    public static Site decideGuaranteed(WorldServer w) {
        SettlementRegistrySC r = SettlementRegistrySC.get(w);
        if (r == null || !worldFits(w)) {
            return null;
        }
        Site had = read(w);
        if (had != null) {
            guaranteedSite = had;
            guaranteedRead = true;
            return had;
        }
        int grid = grid(), min = SubstrateConfigSC.embassyRingMin, max = SubstrateConfigSC.embassyRingMax;
        NBTTagCompound e = r.extra.getCompoundTag(EXTRA);
        if (e.getBoolean("none") && e.getInteger("grid") == grid && e.getInteger("min") == min && e.getInteger("max") == max
                && e.getInteger("chance") == SubstrateConfigSC.embassyChance && e.getInteger("v") == LAND_RULES) {
            return null;                                        // tried with these very numbers: nowhere, said in the log then
        }
        long t0 = System.currentTimeMillis();
        deciding = true;
        Site found;
        boolean onGrid = false;
        try {
            found = gridInRing(w, min, max);
            onGrid = found != null;
            if (found == null) {
                found = ring(w, min, max);
            }
            if (found == null) {
                found = ring(w, FALLBACK_MIN, FALLBACK_MAX);
            }
        } finally {
            deciding = false;
        }
        NBTTagCompound d = new NBTTagCompound();
        if (found == null) {
            d.setBoolean("none", true);
            d.setInteger("grid", grid);
            d.setInteger("min", min);
            d.setInteger("max", max);
            d.setInteger("chance", SubstrateConfigSC.embassyChance);
            d.setInteger("v", LAND_RULES);
            FMLLog.warning("[Silicon Age] Tuners' Embassies: no place for the guaranteed embassy %d..%d nor %d..%d blocks from the spawn"
                    + " (the land round it is explored, or sea, mountains and forest) - only the grid's embassies will stand", min, max,
                    FALLBACK_MIN, FALLBACK_MAX);
        } else {
            found = found.guaranteedCopy();
            d.setInteger("x", found.x);
            d.setInteger("z", found.z);
            d.setInteger("r", found.radius);
            d.setInteger("f", found.floorY);
            d.setLong("s", found.seed);
            d.setBoolean("g", onGrid);
            d.setLong("at", w.getTotalWorldTime());
            d.setInteger("v", LAND_RULES);                      // the rules it was chosen by (it stands under newer ones too)
            FMLLog.info("[Silicon Age] Tuners' Embassies: the guaranteed embassy stands at %d %d (%s, %d blocks from the spawn; chosen in %d ms)",
                    found.x, found.z, onGrid ? "the grid's" : "its own point", (int) Math.sqrt(found.distSq(w.getWorldInfo().getSpawnX(),
                            w.getWorldInfo().getSpawnZ())), System.currentTimeMillis() - t0);
        }
        r.extra.setTag(EXTRA, d);
        r.markDirty();
        guaranteedSite = found;
        guaranteedRead = true;
        return found;
    }

    /** A grid embassy between min and max of the spawn on unexplored ground (the nearest to the spawn), or null. */
    private static Site gridInRing(WorldServer w, int min, int max) {
        int sx = w.getWorldInfo().getSpawnX(), sz = w.getWorldInfo().getSpawnZ();
        Site best = null;
        for (Site s : near(w, sx, sz, max)) {
            if (!farFromSpawn(s.x, s.z, sx, sz, min) || explored(w, s) || zoneNear(w, s)) {
                continue;
            }
            if (best == null || s.distSq(sx, sz) < best.distSq(sx, sz)) {
                best = s;
            }
        }
        return best;
    }

    /** The first pure candidate between min and max of the spawn that fits (see the class comment), or null. */
    private static Site ring(WorldServer w, int min, int max) {
        int sx = w.getWorldInfo().getSpawnX(), sz = w.getWorldInfo().getSpawnZ(), half = grid() / 2;
        for (int[] c : guaranteeCandidates(w.getSeed(), sx, sz, min, max)) {
            Site s = fitSite(w, c[0], c[1], true);
            if (s == null) {
                continue;
            }
            boolean clear = true;
            for (Site g : near(w, s.x, s.z, half)) {
                clear &= g.distSq(s.x + 0.5D, s.z + 0.5D) >= (double) half * half;
            }
            SettlementRegistrySC reg = SettlementRegistrySC.get(w);
            for (SettlementRegistrySC.Settlement e : reg == null ? new ArrayList<SettlementRegistrySC.Settlement>() : reg.embassies()) {
                clear &= e.dim != DIM || e.distSq(s.x + 0.5D, s.z + 0.5D) >= (double) half * half;    // made under other numbers too
            }
            if (clear && !explored(w, s) && !zoneNear(w, s)) {
                return s;
            }
        }
        return null;
    }

    /**
     * ПЕ-2 / ПЕ-4: a player's field zone (its box grown by its range) reaches within ZONE_CLEAR of the site's area - the guaranteed
     * one is never chosen there, a grid one's start is made empty while it does (MapGenEmbassySC). Server.
     */
    public static boolean zoneNear(World w, Site s) {
        return TileEntityFieldGeneratorSC.playerZoneNear(w, DIM, s.x, s.z, s.area() + ZONE_CLEAR);
    }

    // ------------------------------------------------------------------ the ground explored (no region file is ever made by asking)

    /**
     * ПЕ-4: some chunk of the site's footprint (its area's box) is loaded or on disk - its start is made empty (old land: no
     * half-built city). A region file that isn't there is never opened (AnvilChunkLoader would create it). The loaded chunks first
     * (a hash look-up each), then each of the footprint's region files (four at most) looked for once - only a file that is there
     * has its chunks asked (the same answers as chunkOnDisk's, without a File.exists a chunk). Server.
     */
    public static boolean explored(WorldServer w, Site s) {
        int a = s.area(), c0x = (s.x - a) >> 4, c1x = (s.x + a) >> 4, c0z = (s.z - a) >> 4, c1z = (s.z + a) >> 4;
        IChunkProvider cp = w.getChunkProvider();
        ChunkProviderServer server = cp instanceof ChunkProviderServer ? (ChunkProviderServer) cp : null;
        if (server != null) {
            for (int cx = c0x; cx <= c1x; cx++) {
                for (int cz = c0z; cz <= c1z; cz++) {
                    if (server.chunkExists(cx, cz)) {
                        return true;
                    }
                }
            }
        }
        File dir = regionDir(w);
        IChunkLoader loader = server == null ? null : server.currentChunkLoader;
        if (dir == null || !(loader instanceof AnvilChunkLoader)) {
            return false;
        }
        for (int rx = c0x >> 5; rx <= c1x >> 5; rx++) {
            for (int rz = c0z >> 5; rz <= c1z >> 5; rz++) {
                if (!new File(dir, "r." + rx + "." + rz + ".mca").exists()) {
                    continue;                                   // none of its chunks was ever saved
                }
                for (int cx = Math.max(c0x, rx << 5); cx <= Math.min(c1x, (rx << 5) + 31); cx++) {
                    for (int cz = Math.max(c0z, rz << 5); cz <= Math.min(c1z, (rz << 5) + 31); cz++) {
                        if (((AnvilChunkLoader) loader).chunkExists(w, cx, cz)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /** Chunk (cx, cz) of `w` is loaded, or saved on disk (its region file there and the chunk in it). Never makes a file. */
    public static boolean chunkOnDisk(WorldServer w, int cx, int cz) {
        IChunkProvider cp = w.getChunkProvider();
        ChunkProviderServer server = cp instanceof ChunkProviderServer ? (ChunkProviderServer) cp : null;
        if (server != null && server.chunkExists(cx, cz)) {
            return true;
        }
        File dir = regionDir(w);
        if (dir == null || !new File(dir, "r." + (cx >> 5) + "." + (cz >> 5) + ".mca").exists()) {
            return false;
        }
        IChunkLoader loader = server == null ? null : server.currentChunkLoader;
        return loader instanceof AnvilChunkLoader && ((AnvilChunkLoader) loader).chunkExists(w, cx, cz);
    }

    /** The region folder of a loaded world (the save's root, or its DIM folder). */
    private static File regionDir(WorldServer w) {
        File root = w.getSaveHandler() == null ? null : w.getSaveHandler().getWorldDirectory();
        if (root == null) {
            return null;
        }
        String sub = w.provider.getSaveFolder();
        return new File(sub == null ? root : new File(root, sub), "region");
    }

    /**
     * The region file of chunk (cx, cz) of dimension `dim` exists in the current save - even for a world not loaded (package R: was
     * the Substrate's region (0, 0) explored before Ф2 - then «Первый причал» will never be built there). Server.
     */
    public static boolean regionFileExists(int dim, int cx, int cz) {
        File root = DimensionManager.getCurrentSaveRootDirectory();
        if (root == null) {
            return false;
        }
        File base = dim == 0 ? root : new File(root, "DIM" + dim);
        return new File(new File(base, "region"), "r." + (cx >> 5) + "." + (cz >> 5) + ".mca").exists();
    }

    /** Server stopped: the cached sites and the guaranteed one go (the next world is another). */
    public static void clearCache() {
        CACHE.clear();
        cacheSeed = 0L;
        cacheGrid = 0;
        guaranteedSite = null;
        guaranteedRead = false;
        deciding = false;
    }

    // ------------------------------------------------------------------ hashing

    /** A well-mixed hash of the seed and two numbers (SplitMix64's finaliser, as SettlementLayoutSC's). */
    static long mix(long seed, long a, long b) {
        long h = seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h;
    }

    /** 0..n-1 from a hash. */
    static int pick(long h, int n) {
        return (int) ((h >>> 1) % Math.max(1, n));
    }
}
