package com.sc.dim;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.sc.block.BlockCrystalDruseSC;
import com.sc.block.BlockSingularGlassSC;
import com.sc.block.BlockSubstrateFloraSC;
import com.sc.ruin.VaultLayoutSC;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

/**
 * PACKAGE W (docs/plan-substrate-tech-f4.md §3.3-§3.4, rules ПМ-15..ПМ-18, ПА-4..ПА-7): what populate adds after Ф1's ores,
 * traces and druses, inside the same window (chunk + 8 .. + 23 - nothing is loaded, nothing cascades; the glowing blocks by
 * setBlock and ChunkProviderSubstrateSC.glow, few of them):
 * <ul>
 * <li>the flora (SubstrateWildBlocksSC.flora): Wireweed on the plateaus' turf and the Ridges (WIREWEED_TRIES a chunk), the diode
 * moss (light 3) on the canyons' floors and in the Ridges' caves (at most MOSS_MAX a chunk);</li>
 * <li>the monocrystal geodes (rock meta MONOCRYSTAL) in the silicon base, Y GEODE_Y_MIN..GEODE_Y_MAX, one in GEODE_CHANCE chunks,
 * radius GEODE_R_MIN..GEODE_R_MAX, all inside the window;</li>
 * <li>the vents (SubstrateWildBlocksSC.vent): helium fumaroles on the Oxide Fields (one in FUMAROLE_CHANCE chunks), cracked RTGs
 * there (one in RTG_CHANCE), Breakdown cracks on the Ridges', the Oxide Fields' and the canyons' floors (one in CRACK_CHANCE),
 * ion geysers on the Rift's islands (GEYSERS_MIN..MAX an island);</li>
 * <li>the Rift's islands' tops (oxide, a druse now and then).</li>
 * </ul>
 * Signature and numbers fixed (the coordinator's; TODO(balance)); the body is W's.
 * <p>
 * How: a Random of its own from the seed and the chunk (the provider's popRand goes on to the ruins and the settlements exactly
 * as in Ф1..Ф3 - their details in plain plateau land don't change); every column's kind from SubstrateCellsSC.biomeKind (the
 * cells are decided by now - the window's four chunks exist). The pillar's core (meta PILLAR_CORE, light 12) is laid by the
 * window holding the cell's middle column; a geyser and an island's druse - by the window holding their column (their places are
 * pure: SubstrateWildTerrainSC's islands and their hashes), so each is laid once. A surface feature sits on the column's top
 * (World.getHeightValue - the window's chunks are loaded) and needs air over it; nothing goes into a settlement, a Dead Node's
 * zone, a lithograph's hut or a camp (ChunkProviderSubstrateSC.covered), a geode never near a Vault's bunker.
 */
public final class SubstrateWildGenSC {

    /** ПМ-15: the flora's tries / the moss at most, a chunk. */
    public static final int WIREWEED_TRIES = 6, MOSS_MAX = 4;
    /** ПМ-16: the geodes. */
    public static final int GEODE_CHANCE = 12, GEODE_Y_MIN = 5, GEODE_Y_MAX = 18, GEODE_R_MIN = 3, GEODE_R_MAX = 4;
    /** ПА-4..ПА-7: the vents - 1 in N chunks of their biome (the cracks: of every biome but the plateaus), geysers an island. */
    public static final int FUMAROLE_CHANCE = 3, RTG_CHANCE = 8, CRACK_CHANCE = 40, GEYSERS_MIN = 1, GEYSERS_MAX = 2;

    /** The moss's tries a chunk; a Ridges' cave floor is looked for from a random height down this many blocks, not under CAVE_MIN_Y. */
    private static final int MOSS_TRIES = 10, CAVE_MIN_Y = 12, CAVE_SCAN = 10;
    /** An island's druse: on 1 in ISLAND_DRUSE islands, ISLAND_DRUSE_MIN..MAX high. TODO(balance) */
    private static final int ISLAND_DRUSE = 3, ISLAND_DRUSE_MIN = 2, ISLAND_DRUSE_MAX = 6;
    private static final long SALT = 0x5C1F4A11DL, GEYSER_SALT = 0x6E75E4L, DRUSE_SALT = 0xD805EL;
    private static final int[] DX = {1, -1, 0, 0}, DZ = {0, 0, 1, -1};

    private SubstrateWildGenSC() {
    }

    /** ChunkProviderSubstrateSC.populate (the core's call), after the druses and before the structures. */
    public static void populate(World world, Random rand, int cx, int cz) {
        if (world == null || world.isRemote || SubstrateBlocksSC.rock == null || SubstrateWildBlocksSC.vent == null) {
            return;
        }
        Window w = new Window(world, cx, cz);
        w.rift();
        w.flora();
        w.geode();
        w.vents();
    }

    /** One populate window: chunk (cx, cz) + 8 .. + 23 on both axes. */
    private static final class Window {
        final World world;
        final long seed;
        final int x0, z0;
        final Random r;
        final ChunkProviderSubstrateSC gen;

        Window(World world, int cx, int cz) {
            this.world = world;
            this.seed = world.getSeed();
            this.x0 = cx * 16 + 8;
            this.z0 = cz * 16 + 8;
            this.r = new Random(SubstrateTerrainSC.mix(seed ^ SALT, cx, cz));
            this.gen = ChunkProviderSubstrateSC.of(world);
        }

        boolean in(int x, int z) {
            return x >= x0 && x < x0 + 16 && z >= z0 && z < z0 + 16;
        }

        int kind(int x, int z) {
            return SubstrateCellsSC.biomeKind(world, x, z);
        }

        /** A settlement, a ruin or a camp stands (or will be built) here. */
        boolean covered(int x, int z, int margin) {
            return gen != null && gen.covered(x, z, margin);
        }

        /** The window's own column - x0 + 0..15. */
        int rx() {
            return x0 + r.nextInt(16);
        }

        int rz() {
            return z0 + r.nextInt(16);
        }

        // -------------------------------------------------------------- the Rift: the pillar's core, geysers, island druses

        void rift() {
            for (int cellX = x0 >> 8; cellX <= x0 + 15 >> 8; cellX++) {
                for (int cellZ = z0 >> 8; cellZ <= z0 + 15 >> 8; cellZ++) {
                    if (SubstrateCellsSC.pureKind(seed, cellX, cellZ) == BiomeSubstrateSC.RIFT && meetsInner(cellX, cellZ)
                            && SubstrateCellsSC.cellKind(world, cellX, cellZ) == BiomeSubstrateSC.RIFT) {
                        riftCell(cellX, cellZ);
                    }
                }
            }
        }

        /** The window meets the cell's inner square (only then has the cell's kind been decided - its chunks are here). */
        boolean meetsInner(int cellX, int cellZ) {
            int lo = SubstrateCellsSC.INNER_MARGIN + 1, hi = SubstrateCellsSC.CELL - SubstrateCellsSC.INNER_MARGIN - 1;
            int bx = cellX * SubstrateCellsSC.CELL, bz = cellZ * SubstrateCellsSC.CELL;
            return x0 + 15 >= bx + lo && x0 <= bx + hi && z0 + 15 >= bz + lo && z0 <= bz + hi;
        }

        void riftCell(int cellX, int cellZ) {
            Block glass = SubstrateWildBlocksSC.glass;
            int px = cellX * SubstrateCellsSC.CELL + SubstrateCellsSC.CELL / 2, pz = cellZ * SubstrateCellsSC.CELL + SubstrateCellsSC.CELL / 2;
            if (glass != null && in(px, pz)) {                   // the pillar's glowing core, floor to top (the cap stays glass)
                for (int y = SubstrateWildTerrainSC.RIFT_FLOOR + 1; y < SubstrateWildTerrainSC.PILLAR_TOP; y++) {
                    if (world.getBlock(px, y, pz) == glass && world.getBlockMetadata(px, y, pz) == SubstrateWildBlocksSC.GLASS) {
                        world.setBlock(px, y, pz, glass, SubstrateWildBlocksSC.PILLAR_CORE, 2);
                        ChunkProviderSubstrateSC.glow(world, px, y, pz, BlockSingularGlassSC.CORE_LIGHT);
                    }
                }
            }
            List<Long> hashes = new ArrayList<Long>();
            int[][] islands = SubstrateWildTerrainSC.islandList(seed, cellX, cellZ, hashes);
            for (int i = 0; i < islands.length; i++) {
                int[] is = islands[i];
                long h = hashes.get(i).longValue();
                if (Math.abs(is[0] - (x0 + 8)) > is[3] + 10 || Math.abs(is[1] - (z0 + 8)) > is[3] + 10) {
                    continue;                                    // nowhere near this window
                }
                int geysers = GEYSERS_MIN + SubstrateTerrainSC.pick(SubstrateTerrainSC.mix(h, GEYSER_SALT, 0), GEYSERS_MAX - GEYSERS_MIN + 1);
                for (int k = 0; k < geysers; k++) {
                    int[] at = onIsland(is, h, k);
                    if (in(at[0], at[1])) {
                        islandTop(at[0], at[1], SubstrateWildBlocksSC.vent, SubstrateWildBlocksSC.ION_GEYSER);
                    }
                }
                if (SubstrateBlocksSC.druse != null && SubstrateTerrainSC.pick(SubstrateTerrainSC.mix(h, DRUSE_SALT, 0), ISLAND_DRUSE) == 0) {
                    int[] at = onIsland(is, h, 7);
                    if (in(at[0], at[1])) {
                        islandDruse(at[0], at[1], ISLAND_DRUSE_MIN + SubstrateTerrainSC.pick(SubstrateTerrainSC.mix(h, DRUSE_SALT, 1),
                                ISLAND_DRUSE_MAX - ISLAND_DRUSE_MIN + 1));
                    }
                }
            }
        }

        /** The k-th spot on island `is` (pure, within half its radius of its middle). */
        int[] onIsland(int[] is, long h, int k) {
            double a = SubstrateTerrainSC.pick(SubstrateTerrainSC.mix(h, k, 11), 628) / 100D;
            int d = SubstrateTerrainSC.pick(SubstrateTerrainSC.mix(h, k, 12), Math.max(1, is[3] / 2));
            return new int[]{is[0] + (int) Math.round(Math.cos(a) * d), is[1] + (int) Math.round(Math.sin(a) * d)};
        }

        /** `b`:`meta` flush into an island's top at (x, z): its top rock, air over it. */
        void islandTop(int x, int z, Block b, int meta) {
            int[] span = SubstrateWildTerrainSC.islandAt(seed, x, z);
            if (span == null || span[1] >= 255) {
                return;
            }
            int y = span[1];
            if (world.getBlock(x, y, z) == SubstrateBlocksSC.rock && world.isAirBlock(x, y + 1, z)) {
                world.setBlock(x, y, z, b, meta, 2);
            }
        }

        /** A druse on an island's top: one crystal `h` high, its last block the tip (light, by glow). */
        void islandDruse(int x, int z, int h) {
            int[] span = SubstrateWildTerrainSC.islandAt(seed, x, z);
            if (span == null || span[1] + h >= 255 || world.getBlock(x, span[1], z) != SubstrateBlocksSC.rock) {
                return;
            }
            for (int k = 1; k <= h; k++) {
                if (!world.isAirBlock(x, span[1] + k, z)) {
                    return;
                }
            }
            for (int k = 1; k <= h; k++) {
                world.setBlock(x, span[1] + k, z, SubstrateBlocksSC.druse, k == h ? BlockCrystalDruseSC.META_TIP : BlockCrystalDruseSC.META_BODY, 2);
                ChunkProviderSubstrateSC.glow(world, x, span[1] + k, z, BlockCrystalDruseSC.LIGHT);
            }
        }

        // -------------------------------------------------------------- the flora (ПМ-15)

        void flora() {
            Block flora = SubstrateWildBlocksSC.flora;
            if (flora == null) {
                return;
            }
            for (int t = 0; t < WIREWEED_TRIES; t++) {               // Wireweed: on the turf of the plateaus and the Ridges, a tuft or two
                int x = rx(), z = rz(), k = kind(x, z);
                if (k != BiomeSubstrateSC.PLATEAUS && k != BiomeSubstrateSC.RIDGES) {
                    continue;
                }
                if (wireweed(flora, x, z) && r.nextBoolean()) {
                    int d = r.nextInt(4);
                    if (in(x + DX[d], z + DZ[d])) {
                        wireweed(flora, x + DX[d], z + DZ[d]);
                    }
                }
            }
            int moss = 0;
            for (int t = 0; t < MOSS_TRIES && moss < MOSS_MAX; t++) {    // the diode moss: canyons' floors, the Ridges' caves
                int x = rx(), z = rz(), k = kind(x, z), y = -1;
                if (k == BiomeSubstrateSC.CANYONS) {
                    int floor = SubstrateTerrainSC.canyonFloorY(seed, x, z);
                    if (floor > 0 && world.getBlock(x, floor, z) == SubstrateBlocksSC.rock && world.isAirBlock(x, floor + 1, z)) {
                        y = floor + 1;
                    }
                } else if (k == BiomeSubstrateSC.RIDGES) {
                    y = caveFloor(x, z);
                }
                if (y > 0 && y < 255 && !covered(x, z, 1)) {
                    world.setBlock(x, y, z, flora, SubstrateWildBlocksSC.DIODE_MOSS, 2);
                    ChunkProviderSubstrateSC.glow(world, x, y, z, BlockSubstrateFloraSC.MOSS_LIGHT);
                    moss++;
                }
            }
        }

        /** A Wireweed tuft on the turf topping column (x, z), air over it. */
        boolean wireweed(Block flora, int x, int z) {
            int y = world.getHeightValue(x, z);
            if (y <= 1 || y >= 255 || world.getBlock(x, y - 1, z) != SubstrateBlocksSC.rock
                    || world.getBlockMetadata(x, y - 1, z) != SubstrateBlocksSC.QUARTZ_TURF || !world.isAirBlock(x, y, z) || covered(x, z, 0)) {
                return false;
            }
            world.setBlock(x, y, z, flora, SubstrateWildBlocksSC.WIREWEED, 2);
            return true;
        }

        /** The air block over a cave's floor in column (x, z) under the sky's reach, from a random height down; -1 none. */
        int caveFloor(int x, int z) {
            int top = world.getHeightValue(x, z) - 6;
            if (top <= CAVE_MIN_Y + 1) {
                return -1;
            }
            int from = CAVE_MIN_Y + r.nextInt(top - CAVE_MIN_Y);
            for (int y = from; y > from - CAVE_SCAN && y > CAVE_MIN_Y; y--) {
                if (world.isAirBlock(x, y, z) && world.getBlock(x, y - 1, z) == SubstrateBlocksSC.rock && !world.canBlockSeeTheSky(x, y, z)) {
                    return y;
                }
            }
            return -1;
        }

        // -------------------------------------------------------------- the geodes (ПМ-16)

        void geode() {
            if (r.nextInt(GEODE_CHANCE) != 0) {
                return;
            }
            int rad = GEODE_R_MIN + r.nextInt(GEODE_R_MAX - GEODE_R_MIN + 1), span = 16 - 2 * (rad + 1);
            int gx = x0 + rad + 1 + r.nextInt(span), gz = z0 + rad + 1 + r.nextInt(span);
            int gy = GEODE_Y_MIN + r.nextInt(GEODE_Y_MAX - GEODE_Y_MIN + 1);
            int shell = SubstrateBlocksSC.OXIDE_VIOLET + r.nextInt(4);
            if (VaultLayoutSC.vaultNear(seed, gx, gz, rad + 2) != null
                    || kind(gx, gz) == BiomeSubstrateSC.RIFT && SubstrateWildTerrainSC.riftVoid(seed, gx, gz)) {
                return;                                          // never into a Vault's bunker; the void has no rock to hold one
            }
            Block rock = SubstrateBlocksSC.rock;
            for (int dx = -rad; dx <= rad; dx++) {
                for (int dy = -rad; dy <= rad; dy++) {
                    for (int dz = -rad; dz <= rad; dz++) {
                        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        int x = gx + dx, y = gy + dy, z = gz + dz;
                        if (d > rad + 0.5D || y < 1) {
                            continue;
                        }
                        Block b = world.getBlock(x, y, z);
                        if (b != rock && b != SubstrateBlocksSC.ore) {
                            continue;                            // a cave's air, bedrock: as they are
                        }
                        if (d <= rad - 1.5D) {
                            world.setBlock(x, y, z, Blocks.air, 0, 2);
                        } else if (d <= rad - 0.5D) {
                            world.setBlock(x, y, z, rock, SubstrateBlocksSC.MONOCRYSTAL, 2);
                        } else {
                            world.setBlock(x, y, z, rock, shell, 2);
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------- the vents (ПА-4, ПА-5, ПА-7)

        void vents() {
            Block vent = SubstrateWildBlocksSC.vent;
            if (r.nextInt(FUMAROLE_CHANCE) == 0) {                       // a helium fumarole: flush in a dune (a pipe goes on top)
                int x = rx(), z = rz();
                if (kind(x, z) == BiomeSubstrateSC.OXIDE) {
                    surface(x, z, vent, SubstrateWildBlocksSC.FUMAROLE, true);
                }
            }
            if (r.nextInt(RTG_CHANCE) == 0) {                            // a cracked RTG: lying on a dune
                int x = rx(), z = rz();
                if (kind(x, z) == BiomeSubstrateSC.OXIDE) {
                    surface(x, z, vent, SubstrateWildBlocksSC.CRACKED_RTG, false);
                }
            }
            if (r.nextInt(CRACK_CHANCE) == 0) {                          // a Breakdown crack: a canyon's floor, the Ridges, the dunes
                int x = rx(), z = rz(), k = kind(x, z);
                boolean floor = k == BiomeSubstrateSC.CANYONS && SubstrateTerrainSC.canyonFloorY(seed, x, z) == world.getHeightValue(x, z) - 1;
                if (floor || k == BiomeSubstrateSC.RIDGES || k == BiomeSubstrateSC.OXIDE) {
                    surface(x, z, vent, SubstrateWildBlocksSC.ARC_CRACK, true);
                }
            }
        }

        /** `b`:`meta` into the top block of column (x, z) (`flush`) or onto it: the top a plain rock block, air over it. */
        void surface(int x, int z, Block b, int meta, boolean flush) {
            int y = world.getHeightValue(x, z) - 1;
            if (y < 1 || y >= 254 || covered(x, z, 1) || world.getBlock(x, y, z) != SubstrateBlocksSC.rock || !world.isAirBlock(x, y + 1, z)) {
                return;
            }
            int m = world.getBlockMetadata(x, y, z);
            if (m == SubstrateBlocksSC.COPPER_TRACE || m == SubstrateBlocksSC.TRACE_NODE || m == SubstrateBlocksSC.SETTLEMENT_BRICK) {
                return;                                          // a trace (a node glows) or a building stays
            }
            world.setBlock(x, flush ? y : y + 1, z, b, meta, 2);
        }
    }
}
