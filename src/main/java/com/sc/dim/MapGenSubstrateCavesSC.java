package com.sc.dim;

import java.util.Random;

import com.sc.ruin.MapGenCampSC;
import com.sc.ruin.MapGenVaultSC;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.MapGenBase;

/**
 * PACKAGE W (docs/plan-substrate-tech.md §3.3, rule ПМ-8): the Substrate's own carver - vanilla's MapGenCaves and
 * MapGenRavine cut only stone and the biome's top, so here they'd cut nothing. Two things:
 * <ul>
 * <li>caves: vanilla's worm tunnels (the same walk as MapGenCaves), only rarer (a cave system in 1 of CAVE_CHANCE
 * origin chunks, not 1 of 7) and starting below the plateaus; no lava lakes at the bottom;</li>
 * <li>«Сквозные колодцы» (vias): straight shafts VIA_MIN..VIA_MAX across from the surface down to VIA_BOTTOM, about one
 * in VIA_CHANCE chunks, never in a canyon belt and never near the world's spawn column.</li>
 * </ul>
 * Only SubstrateBlocksSC.rock is cut (all its metas: the turf and the crust too); bedrock (Y 0) never, and a cave
 * step that would open onto water (a pond) is skipped. The ores aren't in the array yet - they come in populate.
 * <p>
 * Called from ChunkProviderSubstrateSC.provideChunk as func_151539_a(provider, world, cx, cz, blocks): MapGenBase
 * walks the origin chunks within `range` and seeds `rand` for each, so every chunk cuts its share of the same caves.
 * <p>
 * Ф2 (package S): no via inside a built settlement's box either (MapGenSettlementSC.covers - the provider lets the
 * settlements make their starts before it calls the carver). Its tunnels stay: the settlement's ground closes what
 * opens under its floor.
 * <p>
 * Ф3 (package X, rule ПУ-5): no via through a lithograph's hall or its entrance either (RuinLayoutSC.lithoNear - pure, so it
 * holds where the lithograph's start comes out empty too); a cave tunnel may still reach the hall - its walls close it.
 * <p>
 * Ф4 (package W, docs/plan-substrate-tech-f4.md §3.2): an origin chunk in a Ridges cell's inner square starts a cave system
 * RIDGE_CAVE_MUL times as often: Ф1's draw is kept (its systems and their heights - the same tunnels everywhere, so Ф1's land,
 * the band and the canyons round a Ridges cell, keeps its caves), the extra ones start as high as RIDGE_CAVE_START_MAX (the
 * ridges are hollow too - the diode moss grows there) and cut only the columns of a Ridges cell's inner square (ridgeCol[] - new
 * land: no tunnel ends at an old chunk's wall). Before a chunk is cut its columns are masked (keep[]): nothing - caves or vias -
 * is cut in a Rift cell's void (the islands stay whole, the pillar too), in a built Vault's bunker box or a built camp's box
 * (MapGenVaultSC / MapGenCampSC.covers - the provider makes their starts before it calls the carver; an empty start - explored
 * ground, no building - masks nothing, Ф1's caves go on there). Cell kinds: SubstrateCellsSC.cellKind (the server's kept
 * decision; the chunk's own was made by the provider just before).
 */
public class MapGenSubstrateCavesSC extends MapGenBase {

    /** A cave system in 1 of this many origin chunks (vanilla: 7). */
    public static final int CAVE_CHANCE = 14;
    /** A via in 1 of this many chunks; its diameter; the block row it stops above. */
    public static final int VIA_CHANCE = 64, VIA_MIN = 3, VIA_MAX = 5, VIA_BOTTOM = SubstrateTerrainSC.BASE_TOP;
    /** No via within this many blocks (either axis) of the spawn column (the provider's spawn point). */
    public static final int SPAWN_CLEAR = 32;

    /** Caves start below this (the plateaus are 64..74): a tunnel that climbs may still come out on a terrace. */
    private static final int CAVE_START_MAX = 56;
    /** Ф4: a Ridges origin chunk - cave systems this many times as often, starting up to this height. TODO(balance) */
    public static final int RIDGE_CAVE_MUL = 2, RIDGE_CAVE_START_MAX = 104;
    /** Ф4: the bunkers' and the camps' boxes are kept this much wider. */
    private static final int BOX_MARGIN = 1;

    /** Ф4: the columns of the chunk being cut that nothing may cut (index x * 16 + z); any of them. */
    private final boolean[] keep = new boolean[256];
    private boolean keepAny;
    /** Ф4: the chunk's columns in a Ridges cell's inner square (an extra system cuts only those); an extra system being cut. */
    private final boolean[] ridgeCol = new boolean[256];
    private boolean extraCut;

    /** The settlements the vias keep out of (null: none); Ф4: the camps and the Vaults nothing cuts into (null: none). */
    private final com.sc.settle.gen.MapGenSettlementSC settlements;
    private final MapGenCampSC camps;
    private final MapGenVaultSC vaults;
    /** Ф5 (the coordinator's): the Breakdown Epicentres' and the bridge wrecks' boxes nothing cuts into (null: none). */
    private com.sc.arena.MapGenArenaSC arenas;
    private com.sc.lore.MapGenWreckSC wrecks;

    public MapGenSubstrateCavesSC() {
        this(null, null, null);
    }

    public MapGenSubstrateCavesSC(com.sc.settle.gen.MapGenSettlementSC settlements, MapGenCampSC camps, MapGenVaultSC vaults) {
        this.settlements = settlements;
        this.camps = camps;
        this.vaults = vaults;
    }

    /** Ф5: the arenas and the wrecks to keep out of (ChunkProviderSubstrateSC, once). */
    public void keepOut(com.sc.arena.MapGenArenaSC arenas, com.sc.lore.MapGenWreckSC wrecks) {
        this.arenas = arenas;
        this.wrecks = wrecks;
    }

    /** Ф4: masks the chunk's columns (the class comment), then cuts as MapGenBase does. */
    @Override
    public void func_151539_a(IChunkProvider provider, World world, int cx, int cz, Block[] blocks) {
        mask(world, cx, cz);
        super.func_151539_a(provider, world, cx, cz, blocks);
    }

    /** Ф4: keep[] for chunk (cx, cz) - the Rift's void, the built Vaults' and camps' boxes; ridgeCol[] - its Ridges inner columns. */
    private void mask(World world, int cx, int cz) {
        keepAny = false;
        extraCut = false;
        if (world == null) {
            java.util.Arrays.fill(keep, false);
            java.util.Arrays.fill(ridgeCol, false);
            return;
        }
        long seed = world.getSeed();
        int kind = SubstrateCellsSC.chunkHasInner(cx, cz) ? SubstrateCellsSC.cellKind(world, cx >> 4, cz >> 4) : BiomeSubstrateSC.PLATEAUS;
        boolean rift = kind == BiomeSubstrateSC.RIFT, ridge = kind == BiomeSubstrateSC.RIDGES;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int wx = cx * 16 + x, wz = cz * 16 + z;
                boolean k = rift && (SubstrateWildTerrainSC.riftVoid(seed, wx, wz) || SubstrateWildTerrainSC.pillar(wx, wz))
                        || vaults != null && vaults.covers(wx, wz, BOX_MARGIN) || camps != null && camps.covers(wx, wz, BOX_MARGIN)
                        || arenas != null && arenas.covers(wx, wz, BOX_MARGIN) || wrecks != null && wrecks.covers(wx, wz, BOX_MARGIN);
                keep[x * 16 + z] = k;
                keepAny |= k;
                ridgeCol[x * 16 + z] = ridge && SubstrateCellsSC.inner(wx, wz);
            }
        }
    }

    /** Ф4: origin chunk (ox, oz) lies in a Ridges cell's inner square. */
    private static boolean ridges(World world, int ox, int oz) {
        return world != null && SubstrateCellsSC.chunkHasInner(ox, oz) && SubstrateCellsSC.cellKind(world, ox >> 4, oz >> 4) == BiomeSubstrateSC.RIDGES;
    }

    @Override
    protected void func_151538_a(World world, int ox, int oz, int cx, int cz, Block[] blocks) {
        via(ox, oz, cx, cz, blocks);
        int systems = rand.nextInt(rand.nextInt(rand.nextInt(15) + 1) + 1);
        int roll = rand.nextInt(CAVE_CHANCE);                    // Ф1's draw, as it was: Ф1's systems the same everywhere
        boolean extra = roll != 0 && roll % (CAVE_CHANCE / RIDGE_CAVE_MUL) == 0 && ridges(world, ox, oz);
        if (roll != 0 && !extra) {
            systems = 0;
        }
        extraCut = extra;                                        // a Ridges' own system: its inner columns only (cut)
        for (int s = 0; s < systems; s++) {
            double x = ox * 16 + rand.nextInt(16);
            double y = rand.nextInt(rand.nextInt(extra ? RIDGE_CAVE_START_MAX : CAVE_START_MAX) + 8);
            double z = oz * 16 + rand.nextInt(16);
            int tunnels = 1;
            if (rand.nextInt(4) == 0) {
                room(rand.nextLong(), cx, cz, blocks, x, y, z);
                tunnels += rand.nextInt(4);
            }
            for (int t = 0; t < tunnels; t++) {
                float yaw = rand.nextFloat() * (float) Math.PI * 2F;
                float pitch = (rand.nextFloat() - 0.5F) * 2F / 8F;
                float width = rand.nextFloat() * 2F + rand.nextFloat();
                if (rand.nextInt(10) == 0) {
                    width *= rand.nextFloat() * rand.nextFloat() * 3F + 1F;
                }
                tunnel(rand.nextLong(), cx, cz, blocks, x, y, z, width, yaw, pitch, 0, 0, 1D);
            }
        }
    }

    /** A via of origin chunk (ox, oz), its part inside chunk (cx, cz). Uses `rand` first, so the caves stay the same. */
    private void via(int ox, int oz, int cx, int cz, Block[] blocks) {
        if (rand.nextInt(VIA_CHANCE) != 0) {
            return;
        }
        int d = VIA_MIN + rand.nextInt(VIA_MAX - VIA_MIN + 1);
        int vx = ox * 16 + 4 + rand.nextInt(8), vz = oz * 16 + 4 + rand.nextInt(8);
        if (SubstrateTerrainSC.inCanyonBelt(vx, vz) || nearSpawn(vx, vz) || settlements != null && settlements.covers(vx, vz, d)
                || worldObj != null && com.sc.ruin.RuinLayoutSC.lithoNear(worldObj.getSeed(), vx, vz, d + 1) != null) {
            return;                                              // Ф3: never through a lithograph's hall (pure: its start may be empty)
        }
        double c = d % 2 == 1 ? 0.5D : 0D, r2 = d * d / 4D;      // odd: around a block's centre, even: a block's corner
        for (int x = 0; x < 16; x++) {
            double ddx = cx * 16 + x + 0.5D - (vx + c);
            for (int z = 0; z < 16; z++) {
                double ddz = cz * 16 + z + 0.5D - (vz + c);
                if (ddx * ddx + ddz * ddz > r2 || keepAny && keep[x * 16 + z]) {
                    continue;
                }
                int col = (x * 16 + z) * 256;
                for (int y = 255; y >= VIA_BOTTOM; y--) {
                    if (blocks[col + y] == SubstrateBlocksSC.rock) {
                        blocks[col + y] = null;
                    }
                }
            }
        }
    }

    /** The column is near the world's spawn (WorldProviderSubstrateSC.SPAWN_X / SPAWN_Z): no via, no pond there. */
    static boolean nearSpawn(int x, int z) {
        return Math.abs(x - WorldProviderSubstrateSC.SPAWN_X) <= SPAWN_CLEAR && Math.abs(z - WorldProviderSubstrateSC.SPAWN_Z) <= SPAWN_CLEAR;
    }

    /** A cave room: a wide, short tunnel (vanilla's func_151542_a). */
    private void room(long seed, int cx, int cz, Block[] blocks, double x, double y, double z) {
        tunnel(seed, cx, cz, blocks, x, y, z, 1F + rand.nextFloat() * 6F, 0F, 0F, -1, -1, 0.5D);
    }

    /**
     * One tunnel (vanilla's MapGenCaves.func_151541_a): walks from (x, y, z), turning slowly, cutting an ellipsoid at
     * every step that falls inside chunk (cx, cz); may fork once into two thinner ones.
     */
    private void tunnel(long seed, int cx, int cz, Block[] blocks, double x, double y, double z, float width, float yaw, float pitch,
            int step, int steps, double squash) {
        double centreX = cx * 16 + 8, centreZ = cz * 16 + 8;
        float dYaw = 0F, dPitch = 0F;
        Random random = new Random(seed);
        if (steps <= 0) {
            int most = range * 16 - 16;
            steps = most - random.nextInt(most / 4);
        }
        boolean single = false;
        if (step == -1) {
            step = steps / 2;
            single = true;
        }
        int fork = random.nextInt(steps / 2) + steps / 4;
        for (boolean steep = random.nextInt(6) == 0; step < steps; step++) {
            double rh = 1.5D + MathHelper.sin(step * (float) Math.PI / steps) * width;
            double rv = rh * squash;
            float cp = MathHelper.cos(pitch), sp = MathHelper.sin(pitch);
            x += MathHelper.cos(yaw) * cp;
            y += sp;
            z += MathHelper.sin(yaw) * cp;
            pitch *= steep ? 0.92F : 0.7F;
            pitch += dPitch * 0.1F;
            yaw += dYaw * 0.1F;
            dPitch *= 0.9F;
            dYaw *= 0.75F;
            dPitch += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 2F;
            dYaw += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 4F;
            if (!single && step == fork && width > 1F && steps > 0) {
                tunnel(random.nextLong(), cx, cz, blocks, x, y, z, random.nextFloat() * 0.5F + 0.5F, yaw - (float) Math.PI / 2F, pitch / 3F,
                        step, steps, 1D);
                tunnel(random.nextLong(), cx, cz, blocks, x, y, z, random.nextFloat() * 0.5F + 0.5F, yaw + (float) Math.PI / 2F, pitch / 3F,
                        step, steps, 1D);
                return;
            }
            if (!single && random.nextInt(4) == 0) {
                continue;
            }
            double fx = x - centreX, fz = z - centreZ, left = steps - step, reach = width + 2F + 16F;
            if (fx * fx + fz * fz - left * left > reach * reach) {
                return;                                          // can't reach this chunk any more
            }
            if (x < centreX - 16D - rh * 2D || z < centreZ - 16D - rh * 2D || x > centreX + 16D + rh * 2D || z > centreZ + 16D + rh * 2D) {
                continue;
            }
            cut(cx, cz, blocks, x, y, z, rh, rv);
            if (single) {
                break;
            }
        }
    }

    /** Cuts the ellipsoid (rh across, rv high) at (x, y, z) out of chunk (cx, cz), unless it would open onto water. */
    private void cut(int cx, int cz, Block[] blocks, double x, double y, double z, double rh, double rv) {
        int x1 = Math.max(0, MathHelper.floor_double(x - rh) - cx * 16 - 1), x2 = Math.min(16, MathHelper.floor_double(x + rh) - cx * 16 + 1);
        int y1 = Math.max(1, MathHelper.floor_double(y - rv) - 1), y2 = Math.min(248, MathHelper.floor_double(y + rv) + 1);
        int z1 = Math.max(0, MathHelper.floor_double(z - rh) - cz * 16 - 1), z2 = Math.min(16, MathHelper.floor_double(z + rh) - cz * 16 + 1);
        for (int bx = x1; bx < x2; bx++) {
            for (int bz = z1; bz < z2; bz++) {
                for (int by = y2 + 1; by >= y1 - 1; by--) {
                    if (by >= 0 && by < 256 && isWater(blocks[(bx * 16 + bz) * 256 + by])) {
                        return;
                    }
                }
            }
        }
        for (int bx = x1; bx < x2; bx++) {
            double ex = (bx + cx * 16 + 0.5D - x) / rh;
            for (int bz = z1; bz < z2; bz++) {
                double ez = (bz + cz * 16 + 0.5D - z) / rh;
                if (ex * ex + ez * ez >= 1D || keepAny && keep[bx * 16 + bz] || extraCut && !ridgeCol[bx * 16 + bz]) {
                    continue;
                }
                int col = (bx * 16 + bz) * 256;
                for (int by = y2 - 1; by >= y1; by--) {
                    double ey = (by + 0.5D - y) / rv;
                    if (ey > -0.7D && ex * ex + ey * ey + ez * ez < 1D && blocks[col + by] == SubstrateBlocksSC.rock) {
                        blocks[col + by] = null;
                    }
                }
            }
        }
    }

    private static boolean isWater(Block b) {
        return b == Blocks.water || b == Blocks.flowing_water;
    }
}
