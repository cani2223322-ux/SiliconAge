package com.sc.dim;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * PACKAGE W (docs/plan-substrate-tech-f4.md §4.1, rules ПА-1..ПА-3; proposal §3 «Фоновая радиация»): the Substrate's background
 * radiation - one hook in RadiationSC.levelAt (the coordinator's line), so everything the radiation already has works on it
 * unchanged: the lead suit, heavy water in the leggings, the radiation shield of Quantum / Exo armour, a field with F_RADIATION
 * (a settlement's dome has it - TileEntitySettlementCoreSC.FLAGS), the decontamination shower, the dosimeter; its damage goes
 * through the Exo shield (exoStops lets radiation pass).
 * <ul>
 * <li>by the biome of the player's column (the chunk's own array): the Oxide Fields OXIDE, the Rift RIFT, elsewhere 0;</li>
 * <li>on top, as ordinary RadiationSC sources (report, once a second, blocks in the way count): a cracked RTG RTG_LEVEL within
 * RTG_RADIUS (so up to 2 near one - TileEntitySubstrateVentSC), an Overheated Shell 2 within 4 (package M);</li>
 * <li>the Rift's puncture pillar PILLAR_LEVEL within PILLAR_RADIUS (up to 3 at it) - counted here (pillarShare: the pillar has
 * no tile to report, and nothing stands between it and the void round it).</li>
 * </ul>
 * In the Rift during the Singular Emission (EmpStormSC.emission, ПФ-10) the background (the pillar's share too) is
 * StormRulesSC.EMISSION_RADIATION_MUL times as strong. Numbers fixed (the proposal's: «в Окисных полях 1–2 (у РИТЭГов), в
 * Разломе 2–3»). Server; called once a second a player.
 */
public final class SubstrateRadiationSC {

    /** ПА-1: the background by biome. */
    public static final float OXIDE = 1F, RIFT = 2F;
    /** ПА-2: the sources on top - a cracked RTG, the pillar. */
    public static final float RTG_LEVEL = 1F, PILLAR_LEVEL = 1F;
    public static final int RTG_RADIUS = 12, PILLAR_RADIUS = 32;

    private SubstrateRadiationSC() {
    }

    /** ПА-1 (pure): the background of a column of BiomeSubstrateSC kind `kind`. */
    public static float backgroundLevel(int kind) {
        return kind == BiomeSubstrateSC.OXIDE ? OXIDE : kind == BiomeSubstrateSC.RIFT ? RIFT : 0F;
    }

    /**
     * ПА-2 (pure): the puncture pillar's share at a point of a Rift cell - PILLAR_LEVEL at its axis, falling evenly to 0 at
     * PILLAR_RADIUS (RadiationSC.atDistance; above PILLAR_TOP the distance to its top counts). Nothing stands in the way in the
     * void, so no block cuts it (W counts it here rather than as a reported source - the pillar has no tile).
     */
    public static float pillarShare(double x, double y, double z) {
        int cx = (MathHelper.floor_double(x) >> 8) * SubstrateCellsSC.CELL + SubstrateCellsSC.CELL / 2;
        int cz = (MathHelper.floor_double(z) >> 8) * SubstrateCellsSC.CELL + SubstrateCellsSC.CELL / 2;
        double dx = x - (cx + 0.5D), dz = z - (cz + 0.5D), dy = Math.max(0D, y - SubstrateWildTerrainSC.PILLAR_TOP);
        return com.sc.radiation.RadiationSC.atDistance(PILLAR_LEVEL, PILLAR_RADIUS, Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    /** RadiationSC.levelAt's hook: the background where the player stands (0 outside the Substrate, on a client). */
    public static float background(EntityPlayer p) {
        if (p == null || p.worldObj == null || p.worldObj.isRemote || !SubstrateSC.is(p.worldObj)) {
            return 0F;
        }
        BiomeGenBase b = p.worldObj.getBiomeGenForCoords(MathHelper.floor_double(p.posX), MathHelper.floor_double(p.posZ));
        int kind = b instanceof BiomeSubstrateSC ? ((BiomeSubstrateSC) b).kind : BiomeSubstrateSC.PLATEAUS;
        float level = backgroundLevel(kind);
        if (kind == BiomeSubstrateSC.RIFT) {
            level += pillarShare(p.posX, p.posY + 1.0D, p.posZ);            // ПА-2: up to 3 at the pillar
            if (com.sc.hazard.EmpStormSC.emission(p.worldObj)) {
                level *= com.sc.hazard.StormRulesSC.EMISSION_RADIATION_MUL;     // ПФ-10: the Singular Emission
            }
        }
        return level;
    }
}
