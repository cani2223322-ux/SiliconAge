package com.sc.lore.client;

import java.util.concurrent.ConcurrentLinkedQueue;

import org.lwjgl.opengl.GL11;

import com.sc.dim.SubstrateSC;
import com.sc.lore.SignalRulesSC;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * PACKAGE J (docs/plan-substrate-tech-f5.md §7.3, rule ПЛ-8): the Lithographers' Signal's beam on the client - a vertical column of
 * pale violet-white light from the Node's floor to SignalRulesSC.BEAM_TOP at (x, z), drawn in RenderWorldLastEvent while the signal is
 * on and this client is in the Substrate, seen from SignalRulesSC.BEAM_VIEW blocks (past the render distance: no fog on it, its
 * width grows with the distance so it stays a line on the horizon; additive blending, depth test on - hills hide it), a slow pulse.
 * Registered by ClientProxy.preInit (the coordinator's line). onState - FinaleNetSC.SIGNAL_STATE (any thread: volatile fields);
 * the state goes on leaving the server or the Substrate (FMLNetworkEvent.ClientDisconnectionFromServerEvent, the world's change).
 * Fixed: the class, register, onState; the bodies J's. Client only.
 * <p>
 * J's details:
 * <ul>
 * <li>the messages are queued (the network thread) and applied on the client thread's next tick once a world is there (at a login
 * the message may come before it): {on, x, y, z, t} - the beam until the world's total time + t; {on: false} - none. Leaving the
 * Substrate (a new client world that isn't it) or the server drops it, and so does its time running out (a lost «off» never leaves
 * a beam burning);</li>
 * <li>the beam: one camera-facing strip (turned about the vertical) in SEGMENTS pieces - a wide faint violet glow and a narrow
 * violet-white core, bright at the foot, fading out over its top quarter, the whole pulsing slowly (PULSE_TICKS). Past the far
 * clipping plane nothing is drawn, so every corner further than REACH_K x the render distance is pulled towards the camera along its
 * own line of sight: on the screen it stands where it should (a straight line stays one), the terrain drawn in front of it still hides
 * it. Its width is HALF_WIDTH + GROW per block of distance: at 1 km a few pixels wide;</li>
 * <li>no texture, no lightmap, no fog, no depth writes; the GL state is pushed and popped round it. One beam a client.</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class SignalClientSC {

    private static final SignalClientSC INSTANCE = new SignalClientSC();

    /** The core's half width near (blocks) and its growth a block of distance; the glow's width and strength over the core's. */
    private static final double HALF_WIDTH = 0.35D, GROW = 0.0022D, GLOW_WIDTH = 3.2D;
    private static final float GLOW_ALPHA = 0.22F;
    /** The core's alpha near and how much of it is gone at BEAM_VIEW; the pulse's period (ticks) and its low point. */
    private static final float CORE_ALPHA = 0.85F, FAR_DIM = 0.3F, PULSE_LOW = 0.65F;
    private static final int PULSE_TICKS = 60, SEGMENTS = 12;
    /** Colours: the core (pale violet-white), the glow (violet). */
    private static final float CORE_R = 0.94F, CORE_G = 0.88F, CORE_B = 1.0F, GLOW_R = 0.62F, GLOW_G = 0.42F, GLOW_B = 1.0F;
    /** Corners past this many render distances (or MIN_REACH blocks) are pulled in along their line of sight. */
    private static final double REACH_K = 1.5D, MIN_REACH = 48D;
    /** The top share that fades out. */
    private static final float FADE_FROM = 0.75F;

    /** The messages not applied yet (written by the network thread). */
    private static final ConcurrentLinkedQueue<NBTTagCompound> PENDING = new ConcurrentLinkedQueue<NBTTagCompound>();
    /** Left the server (the network thread): the next client tick drops the beam. */
    private static volatile boolean resetAsked;

    /** Client thread only: the beam, its base, its end (the world's total time); the world last seen was the Substrate. */
    private boolean on, wasSubstrate;
    private int x, y, z;
    private long endAt;

    private SignalClientSC() {
    }

    /** ClientProxy.preInit: the beam (Forge's bus), the tick and the disconnection (FML's). */
    public static void register() {
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        FMLCommonHandler.instance().bus().register(INSTANCE);
    }

    /** A SIGNAL_STATE message {on, x, y, z, t} (any thread: queued for the client thread). */
    public static void onState(NBTTagCompound data) {
        if (data != null) {
            PENDING.add(data);
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
            on = false;
        }
        World w = Minecraft.getMinecraft().theWorld;
        boolean sub = w != null && SubstrateSC.is(w);
        if (!sub && wasSubstrate) {
            on = false;                                 // out of the Substrate (another world, the menu)
        }
        wasSubstrate = sub;
        if (w == null) {
            return;
        }
        long now = w.getTotalWorldTime();
        NBTTagCompound m;
        while ((m = PENDING.poll()) != null) {          // after the reset: a message for the world coming next stays
            on = m.getBoolean("on");
            if (on) {
                x = m.getInteger("x");
                y = m.getInteger("y");
                z = m.getInteger("z");
                endAt = now + Math.max(0, m.getInteger("t"));
            }
        }
        if (on && now >= endAt) {
            on = false;                                 // its time ran out (the server's «off» lost or late)
        }
    }

    // ------------------------------------------------------------------ the beam

    @SubscribeEvent
    public void onRenderLast(RenderWorldLastEvent e) {
        if (!on) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        World w = mc.theWorld;
        if (w == null || mc.renderViewEntity == null || !SubstrateSC.is(w) || w.getTotalWorldTime() >= endAt) {
            return;
        }
        double cx = RenderManager.renderPosX, cy = RenderManager.renderPosY, cz = RenderManager.renderPosZ;
        double bx = x + 0.5D, bz = z + 0.5D, dx = bx - cx, dz = bz - cz;
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat > SignalRulesSC.BEAM_VIEW) {
            return;
        }
        double reach = Math.max(MIN_REACH, mc.gameSettings.renderDistanceChunks * 16D * REACH_K);
        double px = flat < 1.0E-3D ? 1D : -dz / flat, pz = flat < 1.0E-3D ? 0D : dx / flat;     // across the line of sight
        double half = HALF_WIDTH + flat * GROW;
        float t = (float) (w.getTotalWorldTime() % PULSE_TICKS) + e.partialTicks;
        float pulse = PULSE_LOW + (1F - PULSE_LOW) * (0.5F + 0.5F * MathHelper.sin(t / PULSE_TICKS * (float) Math.PI * 2F));
        float alpha = CORE_ALPHA * pulse * (1F - FAR_DIM * (float) (flat / SignalRulesSC.BEAM_VIEW));

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_CURRENT_BIT
                | GL11.GL_FOG_BIT);
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
        Tessellator tes = Tessellator.instance;
        tes.startDrawingQuads();
        strip(tes, bx - cx, y - cy, bz - cz, SignalRulesSC.BEAM_TOP - cy, px * half * GLOW_WIDTH, pz * half * GLOW_WIDTH, reach,
                GLOW_R, GLOW_G, GLOW_B, alpha * GLOW_ALPHA / CORE_ALPHA);
        strip(tes, bx - cx, y - cy, bz - cz, SignalRulesSC.BEAM_TOP - cy, px * half, pz * half, reach, CORE_R, CORE_G, CORE_B, alpha);
        tes.draw();
        GL11.glPopAttrib();
        if (lightmap) {                                 // the lightmap's unit as it was (the pop may not reach that unit)
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
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
