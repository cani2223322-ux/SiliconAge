package com.sc.dim;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

/**
 * PACKAGE W (docs/plan-substrate-tech.md §3): the Substrate's world provider.
 * <p>
 * Fixed for the other packages (rules ПМ-1..ПМ-4, ПК-2):
 * <ul>
 * <li>extends WorldProvider, NOT WorldProviderSurface (Galacticraft's TickHandlerClient gives those its
 * SkyProviderOverworld, GalaxySpace calls them breathable and changes their sky) and implements no Galacticraft
 * interface (IGalacticraftWorldProvider would make arrows fly at 0.005 gravity and NPE GC's sky code);</li>
 * <li>a light constructor: DimensionManager.createProviderFor (the bridge's dimension list) makes one with no world
 * - nothing here may touch worldObj before registerWorld;</li>
 * <li>getDimensionName() is the Latin «Si-14 Substrate» (logs, JourneyMap); the bridge shows nameKey() translated;</li>
 * <li>canRespawnHere() false: death sends you to dimension.respawnDimension; beds are refused by SubstrateEntrySC
 * (package E) before vanilla's bed would explode;</li>
 * <li>no own weather: 1.7.10's DerivedWorldInfo copies the Overworld's rain, so updateWeather keeps the strengths at 0
 * (GC / IC2 solar panels here never «see» the Overworld's rain).</li>
 * </ul>
 * W's part (docs/plan-substrate.md §3.4): the colours, all @SideOnly(CLIENT) - by day a pale teal-grey sky (≈ #A8C9C6
 * times the sun's height, as vanilla), at night towards a dark blue (≈ #0B1430) instead of black; a whitish fog; long
 * copper-violet twilights (a wider window than vanilla's); stars 1.5 times as bright; clouds at Y 200. No own sky
 * renderer (ShadersModCore), the sun and the moon as vanilla. The horizon (the dark lower half of the sky) and the
 * void fog are moved below the canyons' floors, so a canyon isn't gloomy. The spawn point: the middle of the first
 * plateau (SPAWN_X, the terrain's top + 1, SPAWN_Z) - DerivedWorldInfo would give the Overworld's coordinates.
 * <p>
 * Ф4 (docs/plan-substrate-tech-f4.md §5.4, ПФ-4; the coordinator's lines): an EMP storm turns the sky and the fog violet - the
 * colours lean to SKY_STORM / FOG_STORM by StormStateSC.skyFactor (growing over the 30 s warning, fading after the storm). The
 * Rift's own violet-black fog is package W's (SubstrateClientSC, EntityViewRenderEvent).
 */
public class WorldProviderSubstrateSC extends WorldProvider implements INamedDimensionSC {

    public static final String NAME = "Si-14 Substrate";
    public static final String NAME_KEY = "sc.dim.substrate.name";
    /** The world's spawn column: the middle of the plateau between lines 0 and 1 (no via, no pond near it). */
    public static final int SPAWN_X = SubstrateTerrainSC.GRID / 2, SPAWN_Z = SubstrateTerrainSC.GRID / 2;

    /** Day sky, night sky, day fog, night fog (0..1 RGB). */
    private static final float[] SKY_DAY = {0xA8 / 255F, 0xC9 / 255F, 0xC6 / 255F}, SKY_NIGHT = {0x0B / 255F, 0x14 / 255F, 0x30 / 255F};
    private static final float[] FOG_DAY = {0xC9 / 255F, 0xDE / 255F, 0xDC / 255F}, FOG_NIGHT = {0.035F, 0.06F, 0.15F};
    /** Ф4: the storm's sky and fog (a deep violet; W may retune). */
    private static final float[] SKY_STORM = {0x5A / 255F, 0x2A / 255F, 0x8C / 255F}, FOG_STORM = {0x4A / 255F, 0x2A / 255F, 0x6A / 255F};
    /** Twilight while |cos(the sun's angle)| is under this (vanilla: 0.4). */
    private static final float DUSK = 0.55F;
    private static final float STARS = 1.5F, CLOUDS = 200F;
    /** Ф4: how far the storm's colour takes the sky over at its height (the rest stays day / night). */
    private static final float STORM_SHARE = 0.7F;
    /** The horizon and the void fog: below the lowest canyon floor (vanilla: 63 and darkening under Y 32). */
    private static final double HORIZON = 16D, VOID_FOG = 1D / 16D;

    private final float[] dusk = new float[4];
    /** The spawn point, worked out once (server: pure terrain maths, no chunk loaded). */
    private ChunkCoordinates spawn;

    @Override
    protected void registerWorldChunkManager() {
        worldChunkMgr = new WorldChunkManagerSubstrateSC(worldObj);
    }

    @Override
    public IChunkProvider createChunkGenerator() {
        return new ChunkProviderSubstrateSC(worldObj, worldObj.getSeed());
    }

    @Override
    public String getDimensionName() {
        return NAME;
    }

    @Override
    public String nameKey() {
        return NAME_KEY;
    }

    @Override
    public boolean canRespawnHere() {
        return false;
    }

    @Override
    public int getRespawnDimension(EntityPlayerMP player) {
        return SubstrateSC.respawnDimension();
    }

    /** The sky is open: the sun, the moon, day and night as in the Overworld (the vanilla 20-minute day, ПМ-5). */
    @Override
    public boolean isSurfaceWorld() {
        return true;
    }

    /** ПМ-4: never the Overworld's rain or thunder here. */
    @Override
    public void updateWeather() {
        if (worldObj != null) {
            worldObj.prevRainingStrength = 0F;
            worldObj.rainingStrength = 0F;
            worldObj.prevThunderingStrength = 0F;
            worldObj.thunderingStrength = 0F;
        }
    }

    @Override
    public void calculateInitialWeather() {
        updateWeather();
    }

    @Override
    public boolean canDoLightning(Chunk chunk) {
        return false;
    }

    @Override
    public boolean canDoRainSnowIce(Chunk chunk) {
        return false;
    }

    @Override
    public boolean canBlockFreeze(int x, int y, int z, boolean byWater) {
        return false;
    }

    @Override
    public boolean canSnowAt(int x, int y, int z, boolean checkLight) {
        return false;
    }

    // ------------------------------------------------------------------ the spawn point

    /**
     * The middle of the first plateau, on its top (the client doesn't know the seed: there the column's highest
     * possible terrace - nothing but a compass looks at it).
     */
    @Override
    public ChunkCoordinates getSpawnPoint() {
        if (worldObj == null || worldObj.isRemote) {
            return new ChunkCoordinates(SPAWN_X, SubstrateTerrainSC.PLATEAU_MAX + 1, SPAWN_Z);
        }
        if (spawn == null) {
            spawn = new ChunkCoordinates(SPAWN_X, SubstrateTerrainSC.surfaceY(worldObj.getSeed(), SPAWN_X, SPAWN_Z) + 1, SPAWN_Z);
        }
        return new ChunkCoordinates(spawn);
    }

    @Override
    public ChunkCoordinates getRandomizedSpawnPoint() {
        return getSpawnPoint();
    }

    /** A player may start on a column topped by the turf or bare Crystallite (not water, a trace or a druse). */
    @Override
    public boolean canCoordinateBeSpawn(int x, int z) {
        Chunk c = worldObj.getChunkFromBlockCoords(x, z);
        int y = c.getHeightValue(x & 15, z & 15) - 1;
        if (y < 1) {
            return false;
        }
        Block b = c.getBlock(x & 15, y, z & 15);
        int m = c.getBlockMetadata(x & 15, y, z & 15);
        return b == SubstrateBlocksSC.rock && (m == SubstrateBlocksSC.QUARTZ_TURF || m == SubstrateBlocksSC.CRYSTALLITE);
    }

    @Override
    public int getAverageGroundLevel() {
        return SubstrateTerrainSC.PLATEAU_MIN;
    }

    /** The sky's dark lower half starts below the canyons' floors (24..34), not at 63. */
    @Override
    public double getHorizon() {
        return HORIZON;
    }

    // ------------------------------------------------------------------ colours (client)

    /** How much day it is at `angle` (vanilla's sky factor): 1 by day, 0 at night. */
    private static float daylight(float angle) {
        float f = MathHelper.cos(angle * (float) Math.PI * 2F) * 2F + 0.5F;
        return f < 0F ? 0F : f > 1F ? 1F : f;
    }

    private static Vec3 mix(float[] night, float[] day, float t) {
        return Vec3.createVectorHelper(night[0] + (day[0] - night[0]) * t, night[1] + (day[1] - night[1]) * t, night[2] + (day[2] - night[2]) * t);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Vec3 getSkyColor(Entity camera, float partialTicks) {
        return storm(mix(SKY_NIGHT, SKY_DAY, daylight(worldObj.getCelestialAngle(partialTicks))), SKY_STORM, partialTicks);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Vec3 getFogColor(float angle, float partialTicks) {
        return storm(mix(FOG_NIGHT, FOG_DAY, daylight(angle)), FOG_STORM, partialTicks);
    }

    /** Ф4: `c` leaned towards the storm's colour by the storm's share now (0: unchanged). */
    @SideOnly(Side.CLIENT)
    private Vec3 storm(Vec3 c, float[] to, float partialTicks) {
        float t = com.sc.hazard.StormStateSC.skyFactor(worldObj, partialTicks) * STORM_SHARE;
        if (t <= 0F) {
            return c;
        }
        return Vec3.createVectorHelper(c.xCoord + (to[0] - c.xCoord) * t, c.yCoord + (to[1] - c.yCoord) * t, c.zCoord + (to[2] - c.zCoord) * t);
    }

    /** Copper towards the day, violet towards the night; the alpha as vanilla's, over a wider window. */
    @Override
    @SideOnly(Side.CLIENT)
    public float[] calcSunriseSunsetColors(float angle, float partialTicks) {
        float c = MathHelper.cos(angle * (float) Math.PI * 2F);
        if (c < -DUSK || c > DUSK) {
            return null;
        }
        float f = c / DUSK * 0.5F + 0.5F;
        float a = 1F - (1F - MathHelper.sin(f * (float) Math.PI)) * 0.99F;
        dusk[0] = 0.72F + 0.16F * f;
        dusk[1] = 0.30F + 0.24F * f * f;
        dusk[2] = 0.66F - 0.22F * f;
        dusk[3] = a * a;
        return dusk;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public float getStarBrightness(float partialTicks) {
        return Math.min(1F, worldObj.getStarBrightnessBody(partialTicks) * STARS);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public float getCloudHeight() {
        return CLOUDS;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean isSkyColored() {
        return true;
    }

    /** The void fog darkens only under Y 16 (vanilla: under 32 - the canyons' floors are at 24..34). */
    @Override
    @SideOnly(Side.CLIENT)
    public double getVoidFogYFactor() {
        return VOID_FOG;
    }
}
