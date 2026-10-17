package com.sc.dim;

import java.util.Random;

import com.sc.util.ConfigSC;
import com.sc.util.OreEntry;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenMinable;

/**
 * PACKAGE W (docs/plan-substrate-tech.md §3.5): the ore veins of a Substrate chunk, from ChunkProviderSubstrateSC.populate.
 * All 16 ores of the mod, no biome bound: veins a chunk = the ore's veinsPerChunk (ore section of the config, as in
 * the Overworld) x SubstrateOreTableSC.veinMultiplier, the fraction by chance; the vein size is the config's; the
 * heights are SubstrateOreTableSC's layers.
 * <p>
 * The veins are SubstrateBlocksSC.ore in SubstrateBlocksSC.rock: WorldGenMinable asks the block it would replace
 * (isReplaceableOreGen) and the rock says yes only for Crystallite, so the crust, the turf and the traces stay. OreGenSC
 * and ConfigSC.oreDimensions are not involved (they place oreSC in minecraft:stone, of which there is none here).
 * <p>
 * Small veins: vanilla's WorldGenMinable places nothing at all for a size of 2 and 0.4 blocks on average for 3 (its
 * ellipsoids are too thin to hold a block's centre) - and tantalite, baddeleyite and sperrylite are size 2 by default.
 * Below SMALL_VEIN a vein is a short random walk of 1..size blocks instead (on average (size + 1) / 2: 1.5 for 2, 2 for
 * 3 - as much as WorldGenMinable gives at 4, so the yield still grows with the size). TODO(balance)
 * <p>
 * Ф4 (package W, docs/plan-substrate-tech-f4.md §3.4): a vein whose centre column is the Rift's (its biome kind) goes only into
 * an island - at a height inside the island over that column (SubstrateWildTerrainSC.islandAt), else it is dropped (the rim and
 * the void hold none); the draws are the same, so a vein outside the Rift is the one of Ф1. A window holding a column of the
 * Ridges gets SubstrateOreTableSC.ridgeExtra more veins of every ore (x RIDGE_VEIN_MUL in all), each kept only where its centre is
 * the Ridges' - drawn from a Random of their own, so the rest of populate (traces, druses, the ruins) draws as before.
 */
public final class SubstrateOreGenSC {

    /** Vein sizes below this are placed by smallVein, not WorldGenMinable. */
    public static final int SMALL_VEIN = 4;

    private SubstrateOreGenSC() {
    }

    /**
     * Chunk (x0 / 16, z0 / 16): vein centres at x0 + 0..15 (both placers shift them by 8), so a vein stays inside the
     * chunks populate may touch.
     */
    static void generate(World world, Random rand, int x0, int z0) {
        if (SubstrateBlocksSC.ore == null || SubstrateBlocksSC.rock == null) {
            return;
        }
        veins(world, rand, x0, z0, false);
        if (ridgesIn(world, x0 + 8, z0 + 8)) {
            veins(world, new Random(SubstrateTerrainSC.mix(world.getSeed() ^ RIDGE_SALT, x0, z0)), x0, z0, true);
        }
    }

    /** Ф4: the salt of the Ridges' extra veins' Random. */
    private static final long RIDGE_SALT = 0x81D6E0BEL;

    /** Ф4: the window from (wx0, wz0), 16 x 16, holds a Ridges column (its corners and middle asked - the cells are 256 wide). */
    private static boolean ridgesIn(World world, int wx0, int wz0) {
        int[] o = {0, 15, 8};
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (SubstrateCellsSC.biomeKind(world, wx0 + o[i], wz0 + o[j]) == BiomeSubstrateSC.RIDGES) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Every ore's veins in the window: veinsPerChunk x the multiplier (`extra`: the Ridges' share on top - their centres only
     * in the Ridges' columns). A centre in the Rift: into the island over it, or none (the class comment).
     */
    private static void veins(World world, Random rand, int x0, int z0, boolean extra) {
        long seed = world.getSeed();
        for (OreEntry ore : OreEntry.values()) {
            ConfigSC.OreGenSettings s = ConfigSC.settingsFor(ore);
            float want = s.veinsPerChunk * (extra ? SubstrateOreTableSC.ridgeExtra(ore) : SubstrateOreTableSC.veinMultiplier(ore));
            int veins = (int) want;
            if (rand.nextFloat() < want - veins) {
                veins++;
            }
            if (veins <= 0 || s.veinSize <= 0) {
                continue;
            }
            WorldGenMinable gen = s.veinSize < SMALL_VEIN ? null
                    : new WorldGenMinable(SubstrateBlocksSC.ore, ore.meta(), s.veinSize, SubstrateBlocksSC.rock);
            float deep = SubstrateOreTableSC.deepShare(ore);
            for (int i = 0; i < veins; i++) {
                boolean low = deep > 0F && rand.nextFloat() < deep;
                int lo = low ? SubstrateOreTableSC.DEEP_MIN_Y : SubstrateOreTableSC.minY(ore);
                int hi = low ? SubstrateOreTableSC.DEEP_MAX_Y : SubstrateOreTableSC.maxY(ore);
                if (hi <= lo) {
                    continue;
                }
                int x = x0 + rand.nextInt(16), y = lo + rand.nextInt(hi - lo), z = z0 + rand.nextInt(16);
                int kind = SubstrateCellsSC.biomeKind(world, x + 8, z + 8);
                if (extra && kind != BiomeSubstrateSC.RIDGES) {
                    continue;
                }
                if (kind == BiomeSubstrateSC.RIFT) {                 // only into an island (the rim and the void: none)
                    int[] is = SubstrateWildTerrainSC.islandAt(seed, x + 8, z + 8);
                    if (is == null || !SubstrateWildTerrainSC.riftVoid(seed, x + 8, z + 8) || is[1] - is[0] < 2) {
                        continue;
                    }
                    y = is[0] + 1 + rand.nextInt(Math.max(1, is[1] - is[0] - 1));
                }
                if (gen != null) {
                    gen.generate(world, rand, x, y, z);
                } else {
                    smallVein(world, rand, SubstrateBlocksSC.ore, ore.meta(), s.veinSize, SubstrateBlocksSC.rock, x, y, z);
                }
            }
        }
    }

    /**
     * A vein of 1..size blocks of `ore`:`meta` from (x + 8, y, z + 8) - as WorldGenMinable, shifted into the populate
     * window - each next block a random neighbour of the last; only where the block there says isReplaceableOreGen for
     * `target`. Public: OreGenSC places the small veins of the Overworld with it too.
     */
    public static void smallVein(World world, Random rand, Block ore, int meta, int size, Block target, int x, int y, int z) {
        int n = 1 + rand.nextInt(Math.max(1, size));
        int px = x + 8, py = y, pz = z + 8;
        for (int i = 0; i < n; i++) {
            if (py > 0 && py < 255 && world.getBlock(px, py, pz).isReplaceableOreGen(world, px, py, pz, target)) {
                world.setBlock(px, py, pz, ore, meta, 2);
            }
            int d = rand.nextInt(6);
            px += d == 0 ? 1 : d == 1 ? -1 : 0;
            py += d == 2 ? 1 : d == 3 ? -1 : 0;
            pz += d == 4 ? 1 : d == 5 ? -1 : 0;
        }
    }
}
