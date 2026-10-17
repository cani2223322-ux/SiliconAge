package com.sc.dim.client;

import java.lang.ref.WeakReference;

import org.lwjgl.opengl.GL11;

import com.sc.dim.BiomeSubstrateSC;
import com.sc.dim.SubstrateSC;
import com.sc.hazard.StormStateSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * PACKAGE W (docs/plan-substrate-tech.md §3.6): the Substrate's client side - fog events if the provider's colours
 * aren't enough, block renderers. Called from ClientProxy.preInit.
 * <p>
 * Ф1: nothing to register. The sky, fog, twilight and star colours are WorldProviderSubstrateSC's (EntityRenderer
 * takes the fog from getFogColor and blends in the sky colour itself); the druse draws as vanilla's crossed quads.
 * <p>
 * Ф4 (docs/plan-substrate-tech-f4.md §3.5, rule ПМ-17): the fogs - one handler a Forge event, package W's only (package S draws
 * the storm's overlays, not the fog; the storm's violet sky stays the provider's):
 * <ul>
 * <li>FogColors: in the Rift a violet-black fog (≈ #1A0A2A, RIFT_FOG) - by the Rift's share round the camera (the biome of the
 * loaded chunks' own arrays at the camera and SAMPLE blocks each way, eased over time, so the rim fades in); during the Singular
 * Emission (StormStateSC.clientEmission) it reddens, slowly pulsing; the Silica Haze (StormStateSC.hazeFactor) - a whitish fog
 * lit as the day is (dim at night);</li>
 * <li>RenderFogEvent (after vanilla's linear fog in open air - water, lava and blindness keep theirs): the distances - in the
 * Rift the fog closes to RIFT_SIGHT_LOW at its floor, RIFT_SIGHT_HIGH up at the islands and over them; in the haze to HAZE_SIGHT
 * (the proposal's «видимость около 24 блоков»); the nearer of the two wins. FogDensity is not used: cancelling it would also
 * replace vanilla's fog under water.</li>
 * </ul>
 * Only in the Substrate (SubstrateSC.is - the provider); the haze and the emission are the server's word (HazardNetSC), the Rift
 * the chunk's. The render thread only.
 */
@SideOnly(Side.CLIENT)
public final class SubstrateClientSC {

    /** The Rift's fog, the Emission's, the haze's (0..1 RGB). */
    public static final float[] RIFT_FOG = {0x1A / 255F, 0x0A / 255F, 0x2A / 255F}, EMISSION_FOG = {0x5C / 255F, 0x12 / 255F, 0x20 / 255F},
            HAZE_FOG = {0xD4 / 255F, 0xDF / 255F, 0xDC / 255F};
    /** How far one sees: in the haze; in the Rift at its floor and up at the islands (blocks), between those heights evenly. */
    public static final float HAZE_SIGHT = 24F, RIFT_SIGHT_LOW = 28F, RIFT_SIGHT_HIGH = 112F;
    public static final int RIFT_LOW_Y = 8, RIFT_HIGH_Y = 110;
    /** The fog's start as a share of its end: the haze is thick at once, the Rift's opens a little. */
    private static final float HAZE_START = 0.1F, RIFT_START = 0.25F;
    /** How much of the fog's colour the Rift / the haze take over at their fullest. */
    private static final float RIFT_SHARE = 0.9F, HAZE_SHARE = 0.85F, HAZE_NIGHT = 0.15F;
    /** The Rift's share is looked for this far each way of the camera; it eases by this much a tick. */
    private static final int SAMPLE = 12;
    private static final float EASE = 0.06F;

    /**
     * The eased Rift share round the camera, the world and the time it was worked out for. The world weakly: one left from the
     * Substrate (a disconnect - no FogColors in the menu) is no WorldClient kept with all its chunks until the next one's fog.
     */
    private static float rift;
    private static final WeakReference<World> NO_WORLD = new WeakReference<World>(null);
    private static WeakReference<World> riftWorld = NO_WORLD;
    private static double riftAt;

    private SubstrateClientSC() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new SubstrateClientSC());
    }

    @SubscribeEvent
    public void onFogColors(EntityViewRenderEvent.FogColors e) {
        World w = Minecraft.getMinecraft().theWorld;
        if (!SubstrateSC.is(w)) {
            riftWorld = NO_WORLD;                                 // keep no other world's client alive
            return;
        }
        if (e.entity == null || e.block != null && e.block.getMaterial().isLiquid()) {
            return;
        }
        float partial = (float) e.renderPartialTicks;
        ease(w, e.entity, partial);
        float r = e.red, g = e.green, b = e.blue;
        if (rift > 0.001F) {
            float[] c = RIFT_FOG;
            if (StormStateSC.clientEmission()) {                  // ПФ-10: the Rift reddens, slowly pulsing
                float t = 0.7F + 0.3F * MathHelper.sin((w.getTotalWorldTime() + partial) / 30F);
                c = new float[]{lerp(RIFT_FOG[0], EMISSION_FOG[0], t), lerp(RIFT_FOG[1], EMISSION_FOG[1], t), lerp(RIFT_FOG[2], EMISSION_FOG[2], t)};
            }
            float k = rift * RIFT_SHARE;
            r = lerp(r, c[0], k);
            g = lerp(g, c[1], k);
            b = lerp(b, c[2], k);
        }
        float haze = StormStateSC.hazeFactor(w, partial);
        if (haze > 0.001F) {
            float day = Math.max(HAZE_NIGHT, w.getSunBrightness(partial)), k = haze * HAZE_SHARE;
            r = lerp(r, HAZE_FOG[0] * day, k);
            g = lerp(g, HAZE_FOG[1] * day, k);
            b = lerp(b, HAZE_FOG[2] * day, k);
        }
        e.red = r;
        e.green = g;
        e.blue = b;
    }

    @SubscribeEvent
    public void onRenderFog(EntityViewRenderEvent.RenderFogEvent e) {
        World w = Minecraft.getMinecraft().theWorld;
        if (!SubstrateSC.is(w) || e.entity == null) {
            return;
        }
        float far = e.farPlaneDistance, end = far, start = e.fogMode < 0 ? 0F : far * 0.75F;
        boolean changed = false;
        if (rift > 0.001F && w == riftWorld.get()) {
            float t = clamp01((float) (e.entity.posY - RIFT_LOW_Y) / (RIFT_HIGH_Y - RIFT_LOW_Y));
            float sight = RIFT_SIGHT_LOW + (RIFT_SIGHT_HIGH - RIFT_SIGHT_LOW) * t;
            if (sight < far) {
                end = lerp(far, sight, rift);
                start = Math.min(start, lerp(start, sight * RIFT_START, rift));
                changed = true;
            }
        }
        float haze = StormStateSC.hazeFactor(w, (float) e.renderPartialTicks);
        if (haze > 0.001F && HAZE_SIGHT < far) {
            float hEnd = lerp(far, HAZE_SIGHT, haze);
            if (hEnd < end) {
                end = hEnd;
                start = Math.min(start, lerp(e.fogMode < 0 ? 0F : far * 0.75F, HAZE_SIGHT * HAZE_START, haze));
                changed = true;
            }
        }
        if (changed) {
            GL11.glFogf(GL11.GL_FOG_START, Math.min(start, end * 0.95F));
            GL11.glFogf(GL11.GL_FOG_END, end);
        }
    }

    /** The Rift's share round the camera (loaded chunks only), eased over the world's ticks. */
    private static void ease(World w, Entity camera, float partial) {
        int x = MathHelper.floor_double(camera.posX), z = MathHelper.floor_double(camera.posZ);
        int n = 0;
        int[] dx = {0, SAMPLE, -SAMPLE, 0, 0}, dz = {0, 0, 0, SAMPLE, -SAMPLE};
        for (int i = 0; i < dx.length; i++) {
            n += isRift(w, x + dx[i], z + dz[i]) ? 1 : 0;
        }
        float target = n / (float) dx.length;
        double now = w.getTotalWorldTime() + partial;
        if (w != riftWorld.get()) {
            riftWorld = new WeakReference<World>(w);
            rift = target;                                        // a new world (a dimension change): no fade from the last
        } else {
            double dt = Math.max(0D, Math.min(20D, now - riftAt));
            rift += (target - rift) * (float) Math.min(1D, dt * EASE);
        }
        riftAt = now;
    }

    /** The column's biome is the Rift's (the chunk's own array; an unloaded chunk: no). */
    private static boolean isRift(World w, int x, int z) {
        if (!w.blockExists(x, 0, z)) {
            return false;
        }
        BiomeGenBase b = w.getBiomeGenForCoords(x, z);
        return b instanceof BiomeSubstrateSC && ((BiomeSubstrateSC) b).kind == BiomeSubstrateSC.RIFT;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp01(float v) {
        return v < 0F ? 0F : v > 1F ? 1F : v;
    }
}
