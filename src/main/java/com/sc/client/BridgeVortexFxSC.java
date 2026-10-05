package com.sc.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.lwjgl.opengl.GL11;

import com.sc.bridge.BridgeMathSC;
import com.sc.bridge.BridgeVortexMathSC;
import com.sc.tileentity.TileEntityBridgeVortexSC;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * The vortex's life on the client besides its disc (BridgeVortexRendererSC):
 * <ul>
 * <li>ВП5 particles spiralling in to the centre and sparks off the rim (fewer with the «Частицы» setting lower), from
 * the master cell's client tick;</li>
 * <li>ВП12 a quiet hum near an open vortex (every 2 s, louder nearer);</li>
 * <li>ВП7 an end that closed: its disc shrinks to a point and pops (the server sends where, BridgeNetSC.Collapse);</li>
 * <li>ВП11 coming through: a flash at the screen's edges (green Ground / blue Space) and a trail of particles.</li>
 * </ul>
 */
public final class BridgeVortexFxSC {

    private static final ConcurrentLinkedQueue<double[]> COLLAPSES_IN = new ConcurrentLinkedQueue<double[]>();
    private static final ConcurrentLinkedQueue<Integer> ARRIVALS_IN = new ConcurrentLinkedQueue<Integer>();
    /** Collapses being drawn: {x, y, z, kind, size, axis, ringless, stability, start ms, popped}. */
    private static final List<double[]> COLLAPSES = new ArrayList<double[]>();
    private static World collapseWorld;
    private static final int POP_MS = 250, ARRIVE_MS = 700, TRAIL_TICKS = 12;
    private static long arriveAt;
    private static int arriveKind, trailLeft;

    private BridgeVortexFxSC() {
    }

    public static void register() {
        Object o = new Handler();
        MinecraftForge.EVENT_BUS.register(o);
        FMLCommonHandler.instance().bus().register(o);
    }

    /** Network thread: an end closed near us. */
    public static void collapse(double x, double y, double z, int kind, int size, int axis, boolean ringless, int stability) {
        COLLAPSES_IN.add(new double[]{x, y, z, kind, size, axis, ringless ? 1 : 0, stability, 0, 0});
    }

    /** Network thread: our player came through. */
    public static void arrive(int kind) {
        ARRIVALS_IN.add(kind);
    }

    private static int[] counts() {
        int ps = Minecraft.getMinecraft().gameSettings.particleSetting;     // 0 all, 1 decreased, 2 minimal
        return ps == 0 ? new int[]{2, 3} : ps == 1 ? new int[]{1, 6} : new int[]{0, 14};
    }

    /** The master cell's client tick: particles round its disc, the hum. */
    public static void tick(TileEntityBridgeVortexSC v) {
        Minecraft mc = Minecraft.getMinecraft();
        World w = v.getWorldObj();
        if (mc.thePlayer == null || w == null) {
            return;
        }
        double cx = v.xCoord + 0.5, cy = v.yCoord + 0.5, cz = v.zCoord + 0.5;
        double d2 = mc.thePlayer.getDistanceSq(cx, cy, cz);
        long time = w.getTotalWorldTime();
        boolean space = v.getKind() == BridgeMathSC.SPACE;
        long seed = BridgeVortexRendererSC.seedOf(v.xCoord, v.yCoord, v.zCoord);
        if ((time + (seed & 63)) % 40 == 0 && d2 < 24 * 24) {                         // ВП12 the hum
            w.playSound(cx, cy, cz, "portal.portal", 0.22F, space ? 0.55F : 0.42F, false);
        }
        if (d2 > 48 * 48 || time - v.getOpenedAt() < BridgeVortexMathSC.OPEN_TICKS) {
            return;
        }
        Random r = w.rand;
        int[] c = counts();
        float[] col = BridgeVortexMathSC.colour(v.getKind(), v.getStability());
        double rad = v.getSize() * 0.5 * 1.12;
        int n = c[0] + (c[0] == 0 && time % 3 == 0 ? 1 : 0);
        for (int i = 0; i < n; i++) {                                                  // spiralling in
            float k = 0.75F + 0.25F * r.nextFloat();
            spawn(mc, new Mote(w, cx, cy, cz, v.getAxis(), rad * (1.0 + 0.25 * r.nextDouble()), r.nextDouble() * Math.PI * 2,
                    col[0] * k + 0.1F, col[1] * k + 0.1F, col[2] * k + 0.1F, false));
        }
        int sparkEvery = v.isRingless() ? c[1] : c[1] * 2;
        if (v.isUnstable()) {
            sparkEvery = Math.max(1, sparkEvery / 2);
        }
        if (r.nextInt(sparkEvery) == 0) {                                               // sparks at the rim
            spawn(mc, new Mote(w, cx, cy, cz, v.getAxis(), rad * 1.02, r.nextDouble() * Math.PI * 2, 0.9F + 0.1F * col[0],
                    0.9F + 0.1F * col[1], 0.9F + 0.1F * col[2], true));
        }
    }

    private static void spawn(Minecraft mc, EntityFX fx) {
        if (mc.effectRenderer != null) {
            mc.effectRenderer.addEffect(fx);
        }
    }

    /** A spiralling mote (to the centre, in the disc's plane) or a spark (off the rim, fading). */
    static final class Mote extends EntityFX {
        private final double cx, cy, cz;
        private final int axis;
        private final boolean spark;
        private double ang, rad, out;
        private final double spin;

        Mote(World w, double cx, double cy, double cz, int axis, double rad, double ang, float r, float g, float b, boolean spark) {
            super(w, cx, cy, cz, 0, 0, 0);
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.axis = axis;
            this.rad = rad;
            this.ang = ang;
            this.spark = spark;
            this.spin = spark ? 0.02 : 0.09 + rand.nextDouble() * 0.06;
            this.out = spark ? (rand.nextDouble() - 0.5) * 0.3 : (rand.nextDouble() - 0.5) * 0.25;
            setRBGColorF(Math.min(1F, r), Math.min(1F, g), Math.min(1F, b));
            particleMaxAge = spark ? 8 + rand.nextInt(6) : 26 + rand.nextInt(14);
            particleScale = spark ? 0.5F + rand.nextFloat() * 0.3F : 0.6F + rand.nextFloat() * 0.5F;
            noClip = true;
            setParticleTextureIndex(spark ? 160 : rand.nextInt(8));
            place();
            prevPosX = posX;
            prevPosY = posY;
            prevPosZ = posZ;
        }

        private void place() {
            double a = Math.cos(ang) * rad, b = Math.sin(ang) * rad;
            if (axis == 0) {
                setPosition(cx + a, cy + b, cz + out);
            } else {
                setPosition(cx + out, cy + b, cz - a);
            }
        }

        @Override
        public void onUpdate() {
            prevPosX = posX;
            prevPosY = posY;
            prevPosZ = posZ;
            if (particleAge++ >= particleMaxAge) {
                setDead();
                return;
            }
            float f = particleAge / (float) particleMaxAge;
            if (spark) {
                rad *= 1.035;
                out *= 1.1;
                ang += spin;
                setParticleTextureIndex(160 + Math.min(7, (int) (f * 8)));
                particleAlpha = 1F - f;
            } else {
                ang += spin * (1.0 + 1.5 * f);                    // faster nearer the centre
                rad *= 0.93;
                out *= 0.9;
                particleAlpha = Math.min(1F, 3F * (1F - f));
            }
            place();
        }

        @Override
        public int getBrightnessForRender(float partial) {
            return 0xF000F0;
        }

        @Override
        public float getBrightness(float partial) {
            return 1F;
        }
    }

    /** The event handler (Forge bus: drawing; FML bus: the client tick). */
    public static final class Handler {

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft mc = Minecraft.getMinecraft();
            World w = mc.theWorld;
            if (w != collapseWorld) {
                COLLAPSES.clear();
                collapseWorld = w;
            }
            double[] c;
            while ((c = COLLAPSES_IN.poll()) != null) {
                if (w != null) {
                    c[8] = System.currentTimeMillis();
                    COLLAPSES.add(c);
                }
            }
            Integer k;
            while ((k = ARRIVALS_IN.poll()) != null) {
                arriveAt = System.currentTimeMillis();
                arriveKind = k;
                trailLeft = TRAIL_TICKS;
            }
            if (w == null || mc.thePlayer == null) {
                return;
            }
            long now = System.currentTimeMillis();
            for (Iterator<double[]> it = COLLAPSES.iterator(); it.hasNext();) {
                double[] o = it.next();
                long ms = now - (long) o[8];
                if (o[9] == 0 && ms >= BridgeVortexMathSC.CLOSE_MS) {      // the pop
                    o[9] = 1;
                    w.playSound(o[0], o[1], o[2], "random.pop", 1.0F, 0.5F, false);
                    w.playSound(o[0], o[1], o[2], "fireworks.blast", 0.5F, 1.6F, false);
                    float[] col = BridgeVortexMathSC.colour((int) o[3], (int) o[7]);
                    int burst = counts()[0] == 0 ? 6 : 16 * counts()[0];
                    for (int i = 0; i < burst; i++) {
                        spawn(mc, new Mote(w, o[0], o[1], o[2], (int) o[5], 0.1, w.rand.nextDouble() * Math.PI * 2, col[0], col[1], col[2], true));
                    }
                }
                if (ms > BridgeVortexMathSC.CLOSE_MS + POP_MS) {
                    it.remove();
                }
            }
            if (trailLeft > 0) {                                           // ВП11 the trail round the player who came through
                trailLeft--;
                EntityLivingBase p = mc.thePlayer;
                float[] col = BridgeVortexMathSC.kindColour(arriveKind);
                int n = counts()[0] == 0 ? 1 : 3 * counts()[0];
                for (int i = 0; i < n; i++) {
                    double a = w.rand.nextDouble() * Math.PI * 2, h = w.rand.nextDouble() * 1.8;
                    Mote m = new Mote(w, p.posX, p.posY - p.yOffset + h, p.posZ, w.rand.nextInt(2), 0.9, a, col[0], col[1], col[2], false);
                    spawn(mc, m);
                }
            }
        }

        @SubscribeEvent
        public void onWorldRender(RenderWorldLastEvent e) {
            if (COLLAPSES.isEmpty()) {
                return;
            }
            Minecraft mc = Minecraft.getMinecraft();
            EntityLivingBase v = mc.renderViewEntity;
            if (v == null) {
                return;
            }
            double px = v.lastTickPosX + (v.posX - v.lastTickPosX) * e.partialTicks;
            double py = v.lastTickPosY + (v.posY - v.lastTickPosY) * e.partialTicks;
            double pz = v.lastTickPosZ + (v.posZ - v.lastTickPosZ) * e.partialTicks;
            long now = System.currentTimeMillis();
            boolean drew = false;
            for (double[] o : COLLAPSES) {
                double dx = o[0] - px, dy = o[1] - py, dz = o[2] - pz;
                if (dx * dx + dy * dy + dz * dz > 96 * 96) {
                    continue;
                }
                float ms = now - (long) o[8];
                float scale = BridgeVortexMathSC.closeScale(ms);
                float flash = ms < BridgeVortexMathSC.CLOSE_MS ? 0.6F * (ms / BridgeVortexMathSC.CLOSE_MS)
                        : Math.max(0F, 1F - (ms - BridgeVortexMathSC.CLOSE_MS) / POP_MS);
                BridgeVortexRendererSC.draw(dx, dy, dz, (int) o[5], (int) o[4], (int) o[3], (int) o[7], o[6] != 0, scale, flash,
                        (long) (o[0] * 31 + o[2] * 17));
                drew = true;
            }
            if (drew) {
                mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
            }
        }

        /** ВП11: the flash at the screen's edges, fading in 0.7 s. */
        @SubscribeEvent
        public void onOverlay(RenderGameOverlayEvent.Post e) {
            if (e.type != RenderGameOverlayEvent.ElementType.TEXT) {
                return;
            }
            long since = System.currentTimeMillis() - arriveAt;
            if (since < 0 || since >= ARRIVE_MS) {
                return;
            }
            float a = 0.6F * (1F - since / (float) ARRIVE_MS);
            float[] c = arriveKind == BridgeMathSC.SPACE ? new float[]{0.35F, 0.55F, 1F} : new float[]{0.25F, 1F, 0.45F};
            int w = e.resolution.getScaledWidth(), h = e.resolution.getScaledHeight();
            float bw = Math.min(w, h) * 0.28F;
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_CURRENT_BIT);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glShadeModel(GL11.GL_SMOOTH);
            Tessellator t = Tessellator.instance;
            t.startDrawingQuads();
            edge(t, 0, 0, w, 0, w, bw, 0, bw, c, a);                 // top: outer edge first
            edge(t, w, h, 0, h, 0, h - bw, w, h - bw, c, a);         // bottom
            edge(t, 0, h, 0, 0, bw, 0, bw, h, c, a);                 // left
            edge(t, w, 0, w, h, w - bw, h, w - bw, 0, c, a);         // right
            t.draw();
            GL11.glShadeModel(GL11.GL_FLAT);
            GL11.glPopAttrib();
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }

        /** A band from the screen's edge (x0 y0 - x1 y1, coloured) to inside (x2 y2 - x3 y3, clear). */
        private static void edge(Tessellator t, double x0, double y0, double x1, double y1, double x2, double y2, double x3, double y3, float[] c,
                float a) {
            t.setColorRGBA_F(c[0], c[1], c[2], a);
            t.addVertex(x0, y0, 0);
            t.addVertex(x1, y1, 0);
            t.setColorRGBA_F(c[0], c[1], c[2], 0F);
            t.addVertex(x2, y2, 0);
            t.addVertex(x3, y3, 0);
        }
    }
}
