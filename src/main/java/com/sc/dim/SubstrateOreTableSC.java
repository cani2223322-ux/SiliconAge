package com.sc.dim;

import com.sc.util.OreEntry;

/**
 * PACKAGE W (docs/plan-substrate-tech.md §3.5): the Substrate's ores - how many veins of each against the Overworld's
 * (docs/plan-substrate.md §3.3: tantalite x2; sperrylite, baddeleyite, argyrodite, wolframite, monazite x1.5;
 * chalcopyrite, cassiterite x0.5; the rest x1, no biome bound), and where (the layers of SubstrateTerrainSC). Pure:
 * the generator, the handbook's table and the self-test read the same numbers. TODO(balance)
 * <p>
 * Where: the rare ores (the six of x1.5..x2) in the metallization layer, Y 20..40, with a DEEP_SHARE of their veins
 * lower, Y 5..20; quartzite, spodumene and ilmenite in the silicon base, Y 2..20; everything else Y 10..55 (the
 * Crystallite between the base and the crust - the canyons' floors at 24..34 show some).
 * <p>
 * Ф4 (package W, docs/plan-substrate-tech-f4.md §3.4): the Quartzite Ridges' columns get x SubstrateWildTerrainSC.RIDGE_VEIN_MUL
 * («самые богатые жилы» - veinMultiplier(ore, RIDGES)); in the Rift ores lie only in the islands (SubstrateOreGenSC moves a
 * vein's height into the island over its centre, drops it on the rim). Ф1's multipliers are unchanged.
 */
public final class SubstrateOreTableSC {

    /** The rare ores' lower veins: this share of them goes in DEEP_MIN_Y..DEEP_MAX_Y instead of minY..maxY. */
    public static final float DEEP_SHARE = 0.25F;
    public static final int DEEP_MIN_Y = 5, DEEP_MAX_Y = SubstrateTerrainSC.BASE_TOP;
    /** The base layer's ores start above the bedrock floor; the common ones end under the crust. */
    public static final int BASE_MIN_Y = 2, COMMON_MIN_Y = 10, COMMON_MAX_Y = 55;

    private SubstrateOreTableSC() {
    }

    /** Veins a chunk here against the Overworld's veinsPerChunk of that ore. */
    public static float veinMultiplier(OreEntry ore) {
        switch (ore) {
            case TANTALITE:
                return 2F;
            case SPERRYLITE:
            case BADDELEYITE:
            case ARGYRODITE:
            case WOLFRAMITE:
            case MONAZITE:
                return 1.5F;
            case CHALCOPYRITE:
            case CASSITERITE:
                return 0.5F;
            default:
                return 1F;
        }
    }

    /** Ф4: veins a chunk in a column of BiomeSubstrateSC kind `kind` against the Overworld's (x RIDGE_VEIN_MUL in the Ridges). */
    public static float veinMultiplier(OreEntry ore, int kind) {
        return veinMultiplier(ore) * (kind == BiomeSubstrateSC.RIDGES ? SubstrateWildTerrainSC.RIDGE_VEIN_MUL : 1F);
    }

    /** Ф4: the Ridges' extra veins on top of veinMultiplier (SubstrateOreGenSC's second pass). */
    public static float ridgeExtra(OreEntry ore) {
        return veinMultiplier(ore) * (SubstrateWildTerrainSC.RIDGE_VEIN_MUL - 1F);
    }

    /** The ore is one of the rare six (metallization layer, some veins deeper). */
    public static boolean rare(OreEntry ore) {
        switch (ore) {
            case TANTALITE:
            case SPERRYLITE:
            case BADDELEYITE:
            case ARGYRODITE:
            case WOLFRAMITE:
            case MONAZITE:
                return true;
            default:
                return false;
        }
    }

    /** The ore lies in the silicon base (Y 2..20). */
    public static boolean base(OreEntry ore) {
        return ore == OreEntry.QUARTZITE || ore == OreEntry.SPODUMENE || ore == OreEntry.ILMENITE;
    }

    /** The Y range most of the ore's veins go in here (a vein's centre: minY inclusive .. maxY exclusive). */
    public static int minY(OreEntry ore) {
        return rare(ore) ? SubstrateTerrainSC.BASE_TOP : base(ore) ? BASE_MIN_Y : COMMON_MIN_Y;
    }

    public static int maxY(OreEntry ore) {
        return rare(ore) ? SubstrateTerrainSC.METAL_TOP : base(ore) ? SubstrateTerrainSC.BASE_TOP : COMMON_MAX_Y;
    }

    /** The share of the ore's veins in DEEP_MIN_Y..DEEP_MAX_Y instead (0 but for the rare ones). */
    public static float deepShare(OreEntry ore) {
        return rare(ore) ? DEEP_SHARE : 0F;
    }
}
