package com.sc.embassy.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.lwjgl.opengl.GL11;

import com.sc.embassy.EmbassyBeamSC;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * PACKAGE S (docs/plan-substrate-tech-f6.md §6.4, rule ПЕ-12): the embassies' beams on the client - a turquoise («Бирюза Наладчиков»
 * 0x48F0DC) vertical column from each embassy's ring to the sky, drawn in RenderWorldLastEvent while this client is in the Overworld,
 * seen from EmbassyBeamSC.BEAM_VIEW blocks (past the render distance: no fog on it, its width grows with the distance so it stays a
 * line on the horizon; additive blending, depth test on - hills hide it): lore.client.SignalClientSC's way (ПЛ-8 was the Substrate's
 * only - this is the Overworld's own class, the Signal's is not touched). The list comes by EmbassyNetSC.BEAMS (any thread: queued,
 * applied on the client's tick); leaving the Overworld or the server drops it. Registered by ClientProxy.preInit (the coordinator's
 * line). Fixed: the class, register, onBeams; the bodies S's:
 * <ul>
 * <li>the messages are queued (the network thread) and applied on the client thread's tick once this client's world is the Overworld
 * (a message may come before the world it is for: it waits - the server sends a fresh list on every arrival); {list: [{x, y, z}]}
 * replaces the beams. A new client world (another dimension, the menu, another server) drops them, so does the disconnection;</li>
 * <li>a beam: two camera-facing strips (turned about the vertical) in SEGMENTS pieces from the ring's middle to BEAM_TOP - a wide faint
 * glow and a narrow core in the turquoise, bright at the foot, fading out over the top quarter, breathing slowly (PULSE_TICKS).
 * Past the far clipping plane nothing is drawn, so every corner further than REACH_K x the render distance is pulled towards the
 * camera along its own line of sight (on the screen it stands where it should, the terrain drawn in front of it still hides it);
 * its width is HALF_WIDTH + GROW per block of distance - at 1 km a few pixels;</li>
 * <li>no texture, no lightmap, no fog, no depth writes; the GL state pushed and popped round all the beams (one draw).</li>
 * </ul>
 * «Лётное поле посольства» (package P, docs/plan-embassy-airfield-core.md §2.6, rule ЛП-20): the same message's lights: [{x, y, z}] -
 * the airfields' «Светомаяк» blocks (EmbassyBeamSC.lights) - flash at night as a rotating aerodrome beacon: white, then green half a
 * turn later (FLASH_TICKS a turn, each flash FLASH_HALF either side of its peak; each beacon's turn shifted by its place - two
 * embassies never blink together), a dim steady glow between, from dusk (the sun's brightness under NIGHT_FROM, full NIGHT_FADE
 * lower - a thunderstorm's dark day lights them a little too) to dawn, seen from BEAM_VIEW: a round camera-facing glow (ActiveRenderInfo's
 * axes, as the particles' - a pilot over it sees it round too) just over the lamp, a wide faint halo and a bright core, their size
 * growing with the distance and pulled in as the beam's corners past the far plane; in the beams' one draw, the same GL state.
 */
@SideOnly(Side.CLIENT)
public final class EmbassyBeamClientSC {

    private static final EmbassyBeamClientSC INSTANCE = new EmbassyBeamClientSC();

    /** The Overworld's dimension; the beam's top. */
    private static final int OVERWORLD = 0, BEAM_TOP = 256;
    /** The core's half width near (blocks) and its growth a block of distance; the glow's width and strength over the core's. */
    private static final double HALF_WIDTH = 0.4D, GROW = 0.0024D, GLOW_WIDTH = 3.0D;
    private static final float GLOW_ALPHA = 0.25F;
    /** The core's alpha near and how much of it is gone at BEAM_VIEW; the breath's period (ticks) and its low point. */
    private static final float CORE_ALPHA = 0.8F, FAR_DIM = 0.3F, PULSE_LOW = 0.8F;
    private static final int PULSE_TICKS = 100, SEGMENTS = 12;
    /** Colours: the core - «Бирюза Наладчиков» 0x48F0DC; the glow - a deeper turquoise. */
    private static final float CORE_R = 0x48 / 255F, CORE_G = 0xF0 / 255F, CORE_B = 0xDC / 255F, GLOW_R = 0.12F, GLOW_G = 0.62F, GLOW_B = 0.58F;
    /** Corners past this many render distances (or MIN_REACH blocks) are pulled in along their line of sight. */
    private static final double REACH_K = 1.5D, MIN_REACH = 48D;
    /** The top share that fades out. */
    private static final float FADE_FROM = 0.75F;
    /** At most this many messages wait (a few at most in practice). */
    private static final int PENDING_MAX = 8;

    /** ЛП-20, the beacon: the glow's middle over the lamp block's foot; its core's radius near and its growth a block of distance; the halo's size and strength. */
    private static final double LIGHT_DY = 1.05D, LIGHT_SIZE = 0.8D, LIGHT_GROW = 0.005D, HALO = 3.0D;
    private static final float HALO_ALPHA = 0.35F, STEADY = 0.18F;
    /** A turn of the beacon (ticks: white at its start, green half a turn on); a flash lasts FLASH_HALF either side of its peak. */
    private static final int FLASH_TICKS = 40;
    private static final float FLASH_HALF = 4F;
    /** The sun's brightness (World.getSunBrightness: 1 noon .. 0.2 night) it starts to show under, and how much lower it is full. */
    private static final float NIGHT_FROM = 0.65F, NIGHT_FADE = 0.25F;
    /** Colours: the white flash, the green one. */
    private static final float WHITE_R = 1.0F, WHITE_G = 0.98F, WHITE_B = 0.9F, GREEN_R = 0.3F, GREEN_G = 1.0F, GREEN_B = 0.5F;
    /** The round glow in this many fan pieces (their rim's cos / sin). */
    private static final int FAN = 16;
    private static final double[] FAN_COS = new double[FAN + 1], FAN_SIN = new double[FAN + 1];

    static {
        for (int i = 0; i <= FAN; i++) {
            FAN_COS[i] = Math.cos(i * Math.PI * 2D / FAN);
            FAN_SIN[i] = Math.sin(i * Math.PI * 2D / FAN);
        }
    }

    /** The messages not applied yet (written by the network thread). */
    private static final ConcurrentLinkedQueue<NBTTagCompound> PENDING = new ConcurrentLinkedQueue<NBTTagCompound>();
    /** Left the server (the network thread): the next client tick drops the beams. */
    private static volatile boolean resetAsked;

    /** Client thread only: the beams {x, y, z} (each ring's middle), the beacons (each lamp block), the world they are for. */
    private final List<int[]> beams = new ArrayList<int[]>();
    private final List<int[]> lights = new ArrayList<int[]>();
    private World seen;

    private EmbassyBeamClientSC() {
    }

    /** ClientProxy.preInit: the beam's renderer (Forge's bus), the tick and the disconnection (FML's). */
    public static void register() {
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        FMLCommonHandler.instance().bus().register(INSTANCE);
    }

    /** EmbassyNetSC.BEAMS (any thread). */
    public static void onBeams(NBTTagCompound data) {
        if (data == null) {
            return;
        }
        PENDING.add(data);
        while (PENDING.size() > PENDING_MAX) {
            PENDING.poll();                                     // the oldest go: only the newest list matters
        }
    }

    // ------------------------------------------------------------------ the state

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent e) {
        PENDING.clear();
        resetAsked = true;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        if (resetAsked) {
            resetAsked = false;
            beams.clear();
            lights.clear();
        }
        World w = Minecraft.getMinecraft().theWorld;
        if (w != seen) {
            beams.clear();                              // another world (a dimension, the menu, another server): its own list comes
            lights.clear();
            seen = w;
        }
        if (w == null || w.provider == null || w.provider.dimensionId != OVERWORLD) {
            return;                                     // a message for the Overworld waits for it
        }
        NBTTagCompound m;
        while ((m = PENDING.poll()) != null) {
            read(m.getTagList("list", 10), beams);
            read(m.getTagList("lights", 10), lights);   // (none from an older server: no beacons)
        }
    }

    /** A message's list [{x, y, z}] into `out` (replaced). */
    private static void read(NBTTagList l, List<int[]> out) {
        out.clear();
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound t = l.getCompoundTagAt(i);
            out.add(new int[]{t.getInteger("x"), t.getInteger("y"), t.getInteger("z")});
        }
    }

    // ------------------------------------------------------------------ the beams

    @SubscribeEvent
    public void onRenderLast(RenderWorldLastEvent e) {
        if (beams.isEmpty() && lights.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        World w = mc.theWorld;
        if (w == null || w != seen || mc.renderViewEntity == null || w.provider == null || w.provider.dimensionId != OVERWORLD) {
            return;
        }
        double cx = RenderManager.renderPosX, cy = RenderManager.renderPosY, cz = RenderManager.renderPosZ;
        double reach = Math.max(MIN_REACH, mc.gameSettings.renderDistanceChunks * 16D * REACH_K);
        float t = (float) (w.getTotalWorldTime() % PULSE_TICKS) + e.partialTicks;
        float pulse = PULSE_LOW + (1F - PULSE_LOW) * (0.5F + 0.5F * MathHelper.sin(t / PULSE_TICKS * (float) Math.PI * 2F));
        boolean any = false;
        Tessellator tes = Tessellator.instance;
        boolean lightmap = false;
        for (int[] b : beams) {
            double bx = b[0] + 0.5D, bz = b[2] + 0.5D, dx = bx - cx, dz = bz - cz;
            double flat = Math.sqrt(dx * dx + dz * dz);
            if (flat > EmbassyBeamSC.BEAM_VIEW || b[1] >= BEAM_TOP) {
                continue;
            }
            if (!any) {                                 // the GL state once, for all the beams in one draw
                any = true;
                lightmap = begin(tes);
            }
            double px = flat < 1.0E-3D ? 1D : -dz / flat, pz = flat < 1.0E-3D ? 0D : dx / flat;     // across the line of sight
            double half = HALF_WIDTH + flat * GROW;
            float alpha = CORE_ALPHA * pulse * (1F - FAR_DIM * (float) (flat / EmbassyBeamSC.BEAM_VIEW));
            strip(tes, dx, b[1] - cy, dz, BEAM_TOP - cy, px * half * GLOW_WIDTH, pz * half * GLOW_WIDTH, reach, GLOW_R, GLOW_G, GLOW_B,
                    alpha * GLOW_ALPHA / CORE_ALPHA);
            strip(tes, dx, b[1] - cy, dz, BEAM_TOP - cy, px * half, pz * half, reach, CORE_R, CORE_G, CORE_B, alpha);
        }
        float night = lights.isEmpty() ? 0F : MathHelper.clamp_float((NIGHT_FROM - w.getSunBrightness(e.partialTicks)) / NIGHT_FADE, 0F, 1F);
        if (night > 0F) {                               // ЛП-20: the beacons
            float turn = (float) (w.getTotalWorldTime() % FLASH_TICKS) + e.partialTicks;
            for (int[] b : lights) {
                double dx = b[0] + 0.5D - cx, dy = b[1] + LIGHT_DY - cy, dz = b[2] + 0.5D - cz;
                double flat = Math.sqrt(dx * dx + dz * dz);
                if (flat > EmbassyBeamSC.BEAM_VIEW) {
                    continue;
                }
                if (!any) {
                    any = true;
                    lightmap = begin(tes);
                }
                float phase = turn + ((b[0] * 31 + b[2] * 17) & 0x7FFFFFFF) % FLASH_TICKS;
                float white = flash(phase), green = flash(phase + FLASH_TICKS / 2);
                float a = night * (1F - FAR_DIM * (float) (flat / EmbassyBeamSC.BEAM_VIEW));
                double size = LIGHT_SIZE + flat * LIGHT_GROW, d = Math.sqrt(flat * flat + dy * dy);
                if (d > reach) {                        // past the far plane: pulled in along its line of sight, its size with it
                    double k = reach / d;
                    dx *= k;
                    dy *= k;
                    dz *= k;
                    size *= k;
                }
                glow(tes, dx, dy, dz, size * HALO, WHITE_R, WHITE_G, WHITE_B, a * STEADY * HALO_ALPHA);
                glow(tes, dx, dy, dz, size, WHITE_R, WHITE_G, WHITE_B, a * STEADY);
                if (white > 0F) {
                    glow(tes, dx, dy, dz, size * HALO, WHITE_R, WHITE_G, WHITE_B, a * white * HALO_ALPHA);
                    glow(tes, dx, dy, dz, size, WHITE_R, WHITE_G, WHITE_B, a * white);
                }
                if (green > 0F) {
                    glow(tes, dx, dy, dz, size * HALO, GREEN_R, GREEN_G, GREEN_B, a * green * HALO_ALPHA);
                    glow(tes, dx, dy, dz, size, GREEN_R, GREEN_G, GREEN_B, a * green);
                }
            }
        }
        if (!any) {
            return;
        }
        tes.draw();
        GL11.glPopAttrib();
        if (lightmap) {                                 // the lightmap's unit as it was (the pop may not reach that unit)
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        }
    }

    /** The GL state once for everything this frame draws (one draw): no texture, no lightmap, no fog, additive, no depth writes. @return the lightmap was on */
    private static boolean begin(Tessellator tes) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LIGHTING_BIT
                | GL11.GL_CURRENT_BIT | GL11.GL_FOG_BIT);
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        boolean lightmap = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        tes.startDrawingQuads();
        return lightmap;
    }

    /** A flash's strength (0..1) `phase` ticks into the beacon's turn: a peak at the turn's start, FLASH_HALF wide either side. */
    private static float flash(float phase) {
        float x = phase % FLASH_TICKS, d = Math.min(x, FLASH_TICKS - x);
        return d >= FLASH_HALF ? 0F : 1F - d / FLASH_HALF;
    }

    /**
     * A round glow facing the camera (ActiveRenderInfo's right and up axes - the particles' own), camera-relative middle (x, y, z),
     * `radius`; colour (r, g, b) at `a` in the middle, fading to nothing at the rim - FAN quads, each a slice (the middle twice).
     */
    private static void glow(Tessellator tes, double x, double y, double z, double radius, float r, float g, float b, float a) {
        double rx = ActiveRenderInfo.rotationX * radius, rz = ActiveRenderInfo.rotationZ * radius;
        double ux = ActiveRenderInfo.rotationYZ * radius, uy = ActiveRenderInfo.rotationXZ * radius, uz = ActiveRenderInfo.rotationXY * radius;
        for (int i = 0; i < FAN; i++) {
            tes.setColorRGBA_F(r, g, b, a);
            tes.addVertex(x, y, z);
            tes.setColorRGBA_F(r, g, b, 0F);
            tes.addVertex(x + rx * FAN_COS[i] + ux * FAN_SIN[i], y + uy * FAN_SIN[i], z + rz * FAN_COS[i] + uz * FAN_SIN[i]);
            tes.addVertex(x + rx * FAN_COS[i + 1] + ux * FAN_SIN[i + 1], y + uy * FAN_SIN[i + 1], z + rz * FAN_COS[i + 1] + uz * FAN_SIN[i + 1]);
            tes.setColorRGBA_F(r, g, b, a);
            tes.addVertex(x, y, z);
        }
    }

    /**
     * One vertical strip, camera-relative: its foot's middle (x, y0, z), its top's height, half its width across the line of sight
     * (ox, oz); bright at the foot, fading over the top share.
     */
    private static void strip(Tessellator tes, double x, double y0, double z, double y1, double ox, double oz, double reach, float r, float g,
            float b, float a) {
        for (int k = 0; k < SEGMENTS; k++) {
            float f0 = k / (float) SEGMENTS, f1 = (k + 1) / (float) SEGMENTS;
            double ya = y0 + (y1 - y0) * f0, yb = y0 + (y1 - y0) * f1;
            float a0 = a * fade(f0), a1 = a * fade(f1);
            vertex(tes, x - ox, ya, z - oz, reach, r, g, b, a0);
            vertex(tes, x + ox, ya, z + oz, reach, r, g, b, a0);
            vertex(tes, x + ox, yb, z + oz, reach, r, g, b, a1);
            vertex(tes, x - ox, yb, z - oz, reach, r, g, b, a1);
        }
    }

    /** The alpha's share at `f` of the height (0 the foot, 1 the top). */
    private static float fade(float f) {
        float body = 1F - 0.35F * Math.min(f, FADE_FROM);
        return f <= FADE_FROM ? body : body * (1F - f) / (1F - FADE_FROM);
    }

    /** A corner (camera-relative) - pulled in along its line of sight past `reach`. */
    private static void vertex(Tessellator tes, double x, double y, double z, double reach, float r, float g, float b, float a) {
        double d = Math.sqrt(x * x + y * y + z * z);
        if (d > reach) {
            double k = reach / d;
            x *= k;
            y *= k;
            z *= k;
        }
        tes.setColorRGBA_F(r, g, b, a);
        tes.addVertex(x, y, z);
    }
}
