package com.sc.dim;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.sc.block.BlockCrystalDruseSC;
import com.sc.block.BlockSubstrateRockSC;
import com.sc.settle.gen.MapGenSettlementSC;

import net.minecraft.block.Block;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

/**
 * PACKAGE W (docs/plan-substrate-tech.md §3.3): the Substrate's terrain.
 * <p>
 * Fixed for everyone (rules ПМ-6..ПМ-9): the terrain is filled into Block[] / byte[] arrays and handed to the Chunk
 * constructor (GalaxySpace has an ASM hook on World.setBlock - an event and a light update per block); no
 * Blocks.stone anywhere (other mods' ores replace only minecraft:stone - Crystallite keeps them all out); the caves
 * and «vias» are cut by W's own carver (vanilla's MapGenCaves / MapGenRavine cut only stone and the biome's top);
 * getPossibleCreatures is empty (only SpawnDirectorSC spawns here, so other mods' addSpawn by biome type never
 * reaches the world); populate never calls GameRegistry.generateWorld (ChunkProviderServer does that itself).
 * <p>
 * provideChunk, per column (heights from SubstrateTerrainSC): Y 0 bedrock; Crystallite up to the top; on a plateau
 * the top block is Quartz Turf with 2..6 blocks of Oxide Crust under it (never below Y 60), coloured in bands by Y;
 * the first WALL blocks of a canyon's wall are banded oxide from the lowest canyon floor up (the striped walls); a
 * canyon's floor is bare Crystallite. Then the carver (caves, vias), then a rare turquoise pond on a terrace, wholly
 * inside the chunk and only where the terrace is flat and solid around it. The pond is minecraft:flowing_water sources
 * (meta 0), not minecraft:water: ExtraBees' water hives (an IWorldGenerator that doesn't look at the dimension) take a
 * Blocks.water block over rock - the pond's floor; a closed pond's flowing source turns still at its first random
 * tick and has nowhere to run.
 * <p>
 * populate, inside the window chunk + 8 .. + 24 (the four chunks that exist then - nothing new is loaded): the ore
 * veins (SubstrateOreGenSC), the Copper Traces, a druse now and then. The traces are straight segments of TRACE_MIN..
 * TRACE_MAX blocks, each owned by one «origin» chunk and worked out from the seed alone; every window lays the part
 * of every nearby segment that falls into it, so a segment longer than a window is still whole and nothing cascades.
 * A segment that starts in a canyon belt runs along the canyon's wall at a fixed height (mostly), otherwise over the
 * surface; every NODE_EVERY-th block is a glowing node. The glowing blocks (nodes, druses) go in by setBlock only, and
 * there are few of them. setBlock's light update does nothing unless every chunk 17 blocks round is loaded - at the
 * edge of the generated land the chunk two over isn't yet - and vanilla's later passes (Chunk.func_150811_f,
 * enqueueRelightChecks) ask the meta-less Block.getLightValue(), 0 for the rock: there glow() spreads the block light
 * by hand through the loaded chunks (a node's 6 never leaves the window's four).
 * <p>
 * Ф2 (package S, docs/plan-substrate-tech-f2.md §4.4): the settlements are a vanilla structure (MapGenSettlementSC) -
 * provideChunk lets it make its starts first (before the carver: a via is never cut into a settlement), recreateStructures
 * does the same for a chunk loaded from disk, populate builds them LAST (they lie over the ores, traces and druses);
 * a trace, a druse and a pond never go inside a built settlement's box (MapGenSettlementSC.covers).
 * <p>
 * Ф3 (package X, docs/plan-substrate-tech-f3.md §8.2): the same for the ruins - no trace, druse or pond in a built Dead Node's
 * zone or under a lithograph's entrance hut (ruinCovers: MapGenDeadNodeSC / MapGenLithographSC.covers).
 */
public class ChunkProviderSubstrateSC implements IChunkProvider {

    /** A wall's banded blocks from the canyon inwards. */
    public static final int WALL = 2;
    /** A pond in 1 of this many plateau chunks: radius 2..3, 1..2 deep. */
    public static final int POND_CHANCE = 10;
    /** A trace segment in 1 of this many origin chunks; its length; a node every this many blocks. */
    public static final int TRACE_CHANCE = 2, TRACE_MIN = 8, TRACE_MAX = 40, NODE_EVERY = 12;
    /** A segment starting in a canyon belt runs along the wall in WALL_SHARE - 1 of WALL_SHARE cases, 2..17 over the floor. */
    public static final int WALL_SHARE = 3, WALL_RISE_MIN = 2, WALL_RISE = 16;
    /** A druse in 1 of this many chunks: a crystal DRUSE_MIN..DRUSE_MAX high and up to 2 lower ones beside it. */
    public static final int DRUSE_CHANCE = 6, DRUSE_MIN = 3, DRUSE_MAX = 9;

    /** Origin chunks of trace segments this far around a window (a wall segment can lie up to 31 blocks off its start). */
    private static final int TRACE_REACH = 3;
    private static final long TRACE_SALT = 0x7ACE5L;
    private static final int[] DX = {1, -1, 0, 0}, DZ = {0, 0, 1, -1};
    /** The six faces (glow). */
    private static final int[] FX = {1, -1, 0, 0, 0, 0}, FY = {0, 0, 1, -1, 0, 0}, FZ = {0, 0, 0, 0, 1, -1};

    protected final World world;
    protected final long seed;
    private final Random rand = new Random(), popRand = new Random();
    /** Ф2: the settlements (made before the carver - it keeps its vias out of them). */
    private final MapGenSettlementSC settlements = new MapGenSettlementSC();
    private final MapGenSubstrateCavesSC caves = new MapGenSubstrateCavesSC(settlements);
    /** Ф3 (package X, docs/plan-substrate-tech-f3.md §8): the Dead Nodes at the crossings, the abandoned lithographs under the plateaus. */
    private final com.sc.ruin.MapGenDeadNodeSC nodes = new com.sc.ruin.MapGenDeadNodeSC();
    private final com.sc.ruin.MapGenLithographSC lithographs = new com.sc.ruin.MapGenLithographSC();

    public ChunkProviderSubstrateSC(World world, long seed) {
        this.world = world;
        this.seed = seed;
    }

    /** Ф2: the settlements' structure (the world test asks it for a site's start). */
    public MapGenSettlementSC settlements() {
        return settlements;
    }

    /** Ф3: the Dead Nodes' structure (the world test, SpawnDirectorSC's patrols ask it). */
    public com.sc.ruin.MapGenDeadNodeSC nodes() {
        return nodes;
    }

    /** Ф3: the lithographs' structure. */
    public com.sc.ruin.MapGenLithographSC lithographs() {
        return lithographs;
    }

    @Override
    public boolean chunkExists(int x, int z) {
        return true;
    }

    @Override
    public Chunk provideChunk(int cx, int cz) {
        rand.setSeed(cx * 341873128712L + cz * 132897987541L ^ seed);
        Block[] blocks = new Block[65536];
        byte[] metas = new byte[65536];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                column(cx * 16 + x, cz * 16 + z, (x * 16 + z) * 256, blocks, metas);   // Chunk(World, Block[], byte[], x, z): index (x * 16 + z) * 256 + y
            }
        }
        settlements.func_151539_a(this, world, cx, cz, blocks);     // the starts within 8 chunks (no block is touched)
        nodes.func_151539_a(this, world, cx, cz, blocks);           // Ф3: the ruins' starts, the same way
        lithographs.func_151539_a(this, world, cx, cz, blocks);
        caves.func_151539_a(this, world, cx, cz, blocks);
        pond(cx, cz, blocks, metas);
        Chunk chunk = new Chunk(world, blocks, metas, cx, cz);
        byte[] biomes = chunk.getBiomeArray();
        BiomeGenBase plateaus = SubstrateSC.plateaus(), canyons = SubstrateSC.canyons();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                BiomeGenBase b = SubstrateTerrainSC.biomeKind(cx * 16 + x, cz * 16 + z) == BiomeSubstrateSC.CANYONS ? canyons : plateaus;
                biomes[z << 4 | x] = (byte) (b == null ? 0 : b.biomeID);
            }
        }
        chunk.generateSkylightMap();
        return chunk;
    }

    /** One column of the terrain (world x, z; `col` = the column's first index in the arrays). */
    private void column(int wx, int wz, int col, Block[] blocks, byte[] metas) {
        Block rock = SubstrateBlocksSC.rock;
        blocks[col] = Blocks.bedrock;
        int floor = SubstrateTerrainSC.canyonFloorY(seed, wx, wz);
        if (floor >= 0) {                                        // a canyon (or a crossing): bare Crystallite
            for (int y = 1; y <= floor; y++) {
                blocks[col + y] = rock;
            }
            return;
        }
        int top = SubstrateTerrainSC.terraceY(seed, wx, wz);
        int crustFrom = Math.max(SubstrateTerrainSC.OXIDE_FROM, top - SubstrateTerrainSC.crustDepth(seed, wx, wz));
        boolean wall = SubstrateTerrainSC.canyonEdgeDistance(seed, wx, wz) <= WALL;
        for (int y = 1; y <= top; y++) {
            int meta;
            if (y == top) {
                meta = SubstrateBlocksSC.QUARTZ_TURF;
            } else if (y >= crustFrom || wall && y >= SubstrateTerrainSC.CANYON_FLOOR_MIN) {
                meta = SubstrateBlocksSC.OXIDE_VIOLET + SubstrateTerrainSC.oxideBand(y);
            } else {
                meta = SubstrateBlocksSC.CRYSTALLITE;
            }
            blocks[col + y] = rock;
            metas[col + y] = (byte) meta;
        }
    }

    /**
     * A rare pond: an ellipse of water flush with the terrace, wholly inside the chunk with a ring of 1 around it; only
     * where every column of its box has the same top and is solid to below the pond's bottom (no spill, no leak into a
     * cave).
     */
    private void pond(int cx, int cz, Block[] blocks, byte[] metas) {
        if (rand.nextInt(POND_CHANCE) != 0) {
            return;
        }
        int rx = 2 + rand.nextInt(2), rz = 2 + rand.nextInt(2), depth = 1 + rand.nextInt(2);
        int px = rx + 1 + rand.nextInt(14 - 2 * rx), pz = rz + 1 + rand.nextInt(14 - 2 * rz);
        int wx = cx * 16 + px, wz = cz * 16 + pz;
        if (SubstrateTerrainSC.inCanyonBelt(wx, wz) || MapGenSubstrateCavesSC.nearSpawn(wx, wz) || settlements.covers(wx, wz, Math.max(rx, rz) + 1)
                || ruinCovers(wx, wz, Math.max(rx, rz) + 1)) {
            return;
        }
        int level = -1;
        for (int x = px - rx - 1; x <= px + rx + 1; x++) {
            for (int z = pz - rz - 1; z <= pz + rz + 1; z++) {
                int col = (x * 16 + z) * 256, t = topY(blocks, col);
                if (level < 0) {
                    level = t;
                }
                if (t != level || t - depth - 1 < 1) {
                    return;
                }
                for (int k = 1; k <= depth + 1; k++) {
                    if (blocks[col + t - k] == null) {
                        return;
                    }
                }
            }
        }
        for (int x = px - rx; x <= px + rx; x++) {
            for (int z = pz - rz; z <= pz + rz; z++) {
                double ex = (x - px) / (double) rx, ez = (z - pz) / (double) rz;
                if (ex * ex + ez * ez > 1D) {
                    continue;
                }
                int col = (x * 16 + z) * 256;
                for (int k = 0; k < depth; k++) {
                    blocks[col + level - k] = Blocks.flowing_water;   // a source; not Blocks.water - ExtraBees' hives (above)
                    metas[col + level - k] = 0;
                }
            }
        }
    }

    /** The highest block of a column in the arrays, -1 for none. */
    private static int topY(Block[] blocks, int col) {
        for (int y = 255; y >= 0; y--) {
            if (blocks[col + y] != null) {
                return y;
            }
        }
        return -1;
    }

    @Override
    public Chunk loadChunk(int x, int z) {
        return provideChunk(x, z);
    }

    @Override
    public void populate(IChunkProvider provider, int cx, int cz) {
        if (SubstrateBlocksSC.rock == null) {
            return;
        }
        popRand.setSeed(seed);
        long a = popRand.nextLong() / 2L * 2L + 1L, b = popRand.nextLong() / 2L * 2L + 1L;
        popRand.setSeed(cx * a + cz * b ^ seed);
        SubstrateOreGenSC.generate(world, popRand, cx * 16, cz * 16);
        townNear = settlements.covers(cx * 16 + 16, cz * 16 + 16, 9);      // the window (+8..+23) and a block round it
        ruinNear = ruinCovers(cx * 16 + 16, cz * 16 + 16, 9);             // Ф3: a Dead Node's zone, a lithograph's hut
        for (int ox = cx - TRACE_REACH; ox <= cx + TRACE_REACH; ox++) {
            for (int oz = cz - TRACE_REACH; oz <= cz + TRACE_REACH; oz++) {
                trace(ox, oz, cx * 16 + 8, cz * 16 + 8);
            }
        }
        druse(cx * 16 + 8, cz * 16 + 8);
        nodes.generateStructuresInChunk(world, popRand, cx, cz);          // Ф3: the ruins over the land (they never meet a settlement)
        lithographs.generateStructuresInChunk(world, popRand, cx, cz);
        settlements.generateStructuresInChunk(world, popRand, cx, cz);    // last: the settlements lie over everything
    }

    /** The window being populated meets a settlement: its traces and druses ask covers() block by block. */
    private boolean townNear;
    /** Ф3 (package X): the window meets a built ruin's box - its traces and druses ask ruinCovers() block by block. */
    private boolean ruinNear;

    /**
     * Ф3 (package X, docs/plan-substrate-tech-f3.md §8.2): the column (x, z), `margin` round it, lies in a built Dead Node's zone
     * or under a lithograph's entrance hut - no trace, druse or pond there (the ruins are built after them and over them).
     */
    private boolean ruinCovers(int x, int z, int margin) {
        return nodes.covers(x, z, margin) || lithographs.covers(x, z, margin);
    }

    /** The part of origin chunk (ox, oz)'s trace segment (if it has one) inside the window from (wx0, wz0), 16 x 16. */
    private void trace(int ox, int oz, int wx0, int wz0) {
        Random r = new Random(SubstrateTerrainSC.mix(seed ^ TRACE_SALT, ox, oz));
        if (r.nextInt(TRACE_CHANCE) != 0) {
            return;
        }
        int len = TRACE_MIN + r.nextInt(TRACE_MAX - TRACE_MIN + 1), phase = r.nextInt(NODE_EVERY);
        int sx = ox * 16 + r.nextInt(16), sz = oz * 16 + r.nextInt(16);
        boolean alongX = r.nextBoolean(), onWall = r.nextInt(WALL_SHARE) != 0;
        int side = r.nextBoolean() ? 1 : -1, rise = WALL_RISE_MIN + r.nextInt(WALL_RISE);
        int bx = SubstrateTerrainSC.distanceToLine(sx), bz = SubstrateTerrainSC.distanceToLine(sz);
        if (onWall && (bx <= SubstrateTerrainSC.CANYON_BELT || bz <= SubstrateTerrainSC.CANYON_BELT)) {
            if (bx <= SubstrateTerrainSC.CANYON_BELT) {          // the wall of the canyon along Z at x = line·GRID, facing it
                int line = SubstrateTerrainSC.lineIndex(sx), lx = line * SubstrateTerrainSC.GRID;
                int x = lx + side * (SubstrateTerrainSC.canyonHalfWidth(seed, line) + 1);
                int y = SubstrateTerrainSC.canyonFloorY(seed, lx, sz) + rise;
                for (int k = 0; k < len; k++) {
                    if (inWindow(x, sz + k, wx0, wz0)) {
                        wallTrace(x, y, sz + k, -side, 0, (k + phase) % NODE_EVERY == 0);
                    }
                }
            } else {                                             // the wall of the canyon along X at z = line·GRID
                int line = SubstrateTerrainSC.lineIndex(sz), lz = line * SubstrateTerrainSC.GRID;
                int z = lz + side * (SubstrateTerrainSC.canyonHalfWidth(~seed, line) + 1);
                int y = SubstrateTerrainSC.canyonFloorY(seed, sx, lz) + rise;
                for (int k = 0; k < len; k++) {
                    if (inWindow(sx + k, z, wx0, wz0)) {
                        wallTrace(sx + k, y, z, 0, -side, (k + phase) % NODE_EVERY == 0);
                    }
                }
            }
            return;
        }
        for (int k = 0; k < len; k++) {
            int x = alongX ? sx + k : sx, z = alongX ? sz : sz + k;
            if (inWindow(x, z, wx0, wz0)) {
                surfaceTrace(x, z, (k + phase) % NODE_EVERY == 0);
            }
        }
    }

    private static boolean inWindow(int x, int z, int wx0, int wz0) {
        return x >= wx0 && x < wx0 + 16 && z >= wz0 && z < wz0 + 16;
    }

    /** A trace block in a wall: only into the rock of the wall's face (the canyon's air at x + ix, z + iz). */
    private void wallTrace(int x, int y, int z, int ix, int iz, boolean node) {
        if (ruinNear && ruinCovers(x, z, 0)) {
            return;                                              // Ф3: a Dead Node's walls carry its panels
        }
        if (y > 0 && y < 255 && world.getBlock(x, y, z) == SubstrateBlocksSC.rock && world.isAirBlock(x + ix, y, z + iz)) {
            world.setBlock(x, y, z, SubstrateBlocksSC.rock, node ? SubstrateBlocksSC.TRACE_NODE : SubstrateBlocksSC.COPPER_TRACE, 2);
            if (node) {
                glow(x, y, z, BlockSubstrateRockSC.NODE_LIGHT);
            }
        }
    }

    /** A trace block as the column's top block (turf, crust or a canyon's floor; never water, never in a settlement). */
    private void surfaceTrace(int x, int z, boolean node) {
        if (townNear && settlements.covers(x, z, 0) || ruinNear && ruinCovers(x, z, 0)) {
            return;
        }
        int y = world.getHeightValue(x, z) - 1;
        if (y > 0 && world.getBlock(x, y, z) == SubstrateBlocksSC.rock) {
            world.setBlock(x, y, z, SubstrateBlocksSC.rock, node ? SubstrateBlocksSC.TRACE_NODE : SubstrateBlocksSC.COPPER_TRACE, 2);
            if (node) {
                glow(x, y, z, BlockSubstrateRockSC.NODE_LIGHT);
            }
        }
    }

    /** Now and then a druse in the window, on a plateau: one tall crystal, up to two short ones beside it. */
    private void druse(int wx0, int wz0) {
        if (SubstrateBlocksSC.druse == null || popRand.nextInt(DRUSE_CHANCE) != 0) {
            return;
        }
        int x = wx0 + 1 + popRand.nextInt(14), z = wz0 + 1 + popRand.nextInt(14);
        int h = DRUSE_MIN + popRand.nextInt(DRUSE_MAX - DRUSE_MIN + 1);
        if (!crystal(x, z, h)) {
            return;
        }
        int more = popRand.nextInt(3);
        for (int i = 0; i < more; i++) {
            int d = popRand.nextInt(4);
            crystal(x + DX[d], z + DZ[d], 1 + popRand.nextInt(Math.max(1, h / 2)));
        }
    }

    /**
     * A crystal `h` high on the column's top rock (a plateau's, not a canyon's floor, not in a settlement), the last block
     * its tip; false where it doesn't fit.
     */
    private boolean crystal(int x, int z, int h) {
        int y = world.getHeightValue(x, z);
        if (y <= 1 || y + h >= 255 || world.getBlock(x, y - 1, z) != SubstrateBlocksSC.rock || SubstrateTerrainSC.canyonFloorY(seed, x, z) >= 0
                || townNear && settlements.covers(x, z, 1) || ruinNear && ruinCovers(x, z, 1)) {
            return false;
        }
        for (int k = 0; k < h; k++) {
            if (!world.isAirBlock(x, y + k, z)) {
                return false;
            }
        }
        for (int k = 0; k < h; k++) {
            world.setBlock(x, y + k, z, SubstrateBlocksSC.druse, k == h - 1 ? BlockCrystalDruseSC.META_TIP : BlockCrystalDruseSC.META_BODY, 2);
            glow(x, y + k, z, BlockCrystalDruseSC.LIGHT);
        }
        return true;
    }

    /**
     * The block light of a glowing block just set at (x, y, z), where setBlock's own update gave up (the class comment):
     * spread from it through the loaded chunks - each step loses max(1, the block's opacity), as World.computeLightValue
     * does; it only ever brightens. Light already as bright is never walked again, so a druse's stack costs little more
     * than one crystal.
     */
    private void glow(int x, int y, int z, int light) {
        glow(world, x, y, z, light);
    }

    /** glow() for any world being populated - the settlements' pieces light their lamps and showcases with it (Ф2). */
    public static void glow(World world, int x, int y, int z, int light) {
        if (light <= 0 || world.doChunksNearChunkExist(x, y, z, 17)) {
            return;                                              // setBlock worked it out
        }
        List<int[]> open = new ArrayList<int[]>();
        open.add(new int[]{x, y, z, light});
        for (int i = 0; i < open.size(); i++) {
            int[] p = open.get(i);
            if (world.getSavedLightValue(EnumSkyBlock.Block, p[0], p[1], p[2]) >= p[3]) {
                continue;
            }
            world.setLightValue(EnumSkyBlock.Block, p[0], p[1], p[2], p[3]);
            for (int d = 0; d < 6; d++) {
                int nx = p[0] + FX[d], ny = p[1] + FY[d], nz = p[2] + FZ[d];
                if (ny < 0 || ny > 255 || !world.blockExists(nx, ny, nz)) {
                    continue;                                    // never loads a chunk
                }
                int left = p[3] - Math.max(1, world.getBlock(nx, ny, nz).getLightOpacity(world, nx, ny, nz));
                if (left > 0 && world.getSavedLightValue(EnumSkyBlock.Block, nx, ny, nz) < left) {
                    open.add(new int[]{nx, ny, nz, left});
                }
            }
        }
    }

    @Override
    public boolean saveChunks(boolean all, IProgressUpdate progress) {
        return true;
    }

    @Override
    public boolean unloadQueuedChunks() {
        return false;
    }

    @Override
    public boolean canSave() {
        return true;
    }

    @Override
    public String makeString() {
        return "SubstrateLevelSource";
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List getPossibleCreatures(EnumCreatureType type, int x, int y, int z) {
        return new ArrayList();
    }

    /** The nearest settlement for "SCSi14Settlement" (vanilla's structure finder); Ф3: a Dead Node, a lithograph by their names. */
    @Override
    public ChunkPosition func_147416_a(World w, String structure, int x, int y, int z) {
        if (com.sc.ruin.MapGenDeadNodeSC.START_NAME.equals(structure)) {
            return nodes.func_151545_a(w, x, y, z);
        }
        if (com.sc.ruin.MapGenLithographSC.START_NAME.equals(structure)) {
            return lithographs.func_151545_a(w, x, y, z);
        }
        return MapGenSettlementSC.START_NAME.equals(structure) ? settlements.func_151545_a(w, x, y, z) : null;
    }

    @Override
    public int getLoadedChunkCount() {
        return 0;
    }

    /** A chunk loaded from disk: the settlements' starts round it (in land of a Ф1 world they come out empty - ПП-2). */
    @Override
    public void recreateStructures(int x, int z) {
        settlements.func_151539_a(this, world, x, z, null);
        nodes.func_151539_a(this, world, x, z, null);
        lithographs.func_151539_a(this, world, x, z, null);
    }

    @Override
    public void saveExtraData() {
    }
}
