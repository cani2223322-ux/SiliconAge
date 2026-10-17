package com.sc.mob.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.lwjgl.opengl.GL11;

import com.sc.arena.ArenaLayoutSC;
import com.sc.mob.EntityBreakdownWardenSC;
import com.sc.mob.EntityContainmentCoilSC;
import com.sc.mob.WardenRulesSC;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * PACKAGE B (docs/plan-substrate-tech-f5.md §5.7): the Breakdown Warden's client side - registered by ClientProxy.preInit (the
 * coordinator's line): the renderers of EntityBreakdownWardenSC (RenderBreakdownWardenSC + ModelBreakdownWardenSC, the boss bar -
 * BossStatus.setBossStatus in doRender, as vanilla's Wither) and of EntityContainmentCoilSC (RenderContainmentCoilSC, its beam to the
 * Warden's chest), the highlight's white glow, the overheat's white-hot chestplate, the telegraphs' particles; and onFx - the server's
 * one-off effects (FinaleNetSC.BOSS_FX: {k: FX_*, x, y, z} - the «сброс ядра»'s flash over the arena, the «Импульс глушения»'s wave,
 * a coil going dark). Textures - tools/gen_finale_boss_tex.py (B's). Client only; any thread for onFx (keep it to volatile fields /
 * a queue the client tick drains).
 * <p>
 * Package B: onFx only queues (FX, capped); the client tick (FML, END) drains it on the client thread - particles, and the expanding
 * rings (a flat band on the ground with a fading wall over it) drawn in RenderWorldLastEvent: FX_SILENCE out to SILENCE_RADIUS
 * (violet), FX_CORE_RESET out to the arena's rim (white-gold, the sky flashing for whoever stands near). A new world (or none) drops
 * everything. The «Частицы» setting thins the particles; the rings are a few quads each.
 */
@SideOnly(Side.CLIENT)
public final class BossClientSC {

    /**
     * FinaleNetSC.BOSS_FX kinds (fixed, append only): the core reset's blast, the silencing pulse's wave, a coil dark / broken / lit.
     * The server's code sends the same numbers from WardenFightSC.FX_* (it never names this client class).
     */
    public static final int FX_CORE_RESET = 1, FX_SILENCE = 2, FX_COIL_DARK = 3, FX_COIL_BROKEN = 4, FX_COIL_LIT = 5;
    /** The rings: their colours, life (ticks), the bands' width and the wall's height; the sky's flash within FLASH_RANGE. */
    public static final int SILENCE_RGB = 0xA06EFF, RESET_RGB = 0xFFF0C8, SILENCE_TICKS = 14, RESET_TICKS = 22, FLASH_RANGE = 64;
    private static final double BAND = 0.6D, WALL = 2.2D;
    private static final int SEGMENTS = 48, FX_MAX = 64;

    /** BOSS_FX messages waiting for the client thread. */
    private static final Queue<NBTTagCompound> FX = new ConcurrentLinkedQueue<NBTTagCompound>();
    /** The rings under way (client thread only): {x, y, z, radius, born tick, life, rgb}. */
    private static final List<double[]> RINGS = new ArrayList<double[]>();
    private static final Random RAND = new Random();
    private static World lastWorld;
    private static long tick;

    private BossClientSC() {
    }

    /** ClientProxy.preInit. B's body. */
    public static void register() {
        RenderingRegistry.registerEntityRenderingHandler(EntityBreakdownWardenSC.class, new RenderBreakdownWardenSC());
        RenderingRegistry.registerEntityRenderingHandler(EntityContainmentCoilSC.class, new RenderContainmentCoilSC());
        Events ev = new Events();
        FMLCommonHandler.instance().bus().register(ev);
        MinecraftForge.EVENT_BUS.register(ev);
    }

    /** A BOSS_FX message (any thread). B's body. */
    public static void onFx(NBTTagCompound data) {
        if (data != null && FX.size() < FX_MAX) {
            FX.add(data);
        }
    }

    public static final class Events {
        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase == TickEvent.Phase.END) {
                clientTick(Minecraft.getMinecraft());
            }
        }

        @SubscribeEvent
        public void onRenderLast(RenderWorldLastEvent e) {
            render(Minecraft.getMinecraft(), e.partialTicks);
        }
    }

    // ------------------------------------------------------------------ the tick: the messages, the rings' ends

    private static void clientTick(Minecraft mc) {
        World w = mc.theWorld;
        if (w != lastWorld) {                                   // a new world / left: nothing carries over
            lastWorld = w;
            RINGS.clear();
        }
        if (w == null || mc.thePlayer == null) {
            FX.clear();
            return;
        }
        if (mc.isGamePaused()) {
            return;
        }
        tick++;
        NBTTagCompound d;
        while ((d = FX.poll()) != null) {
            handle(mc, w, d);
        }
        for (Iterator<double[]> it = RINGS.iterator(); it.hasNext(); ) {
            double[] r = it.next();
            if (tick - r[4] > r[5]) {
                it.remove();
            }
        }
    }

    private static void handle(Minecraft mc, World w, NBTTagCompound d) {
        int k = d.getInteger("k");
        double x = d.getDouble("x"), y = d.getDouble("y"), z = d.getDouble("z");
        switch (k) {
            case FX_CORE_RESET: {
                RINGS.add(new double[]{x, y - 0.9D, z, ArenaLayoutSC.RIM_R, tick, RESET_TICKS, RESET_RGB});
                w.spawnParticle("hugeexplosion", x, y + 1D, z, 0D, 0D, 0D);
                for (int i = 0; i < scaled(mc, 60); i++) {
                    double a = RAND.nextDouble() * Math.PI * 2D, s = 0.6D + RAND.nextDouble() * 1.2D;
                    w.spawnParticle("fireworksSpark", x, y + 1D + RAND.nextDouble() * 2D, z, Math.cos(a) * s, RAND.nextDouble() * 0.3D, Math.sin(a) * s);
                }
                if (mc.thePlayer.getDistanceSq(x, y, z) <= FLASH_RANGE * FLASH_RANGE) {
                    w.lastLightningBolt = 2;                     // the sky flashes white over the pit
                }
                break;
            }
            case FX_SILENCE: {
                RINGS.add(new double[]{x, y, z, WardenRulesSC.SILENCE_RADIUS, tick, SILENCE_TICKS, SILENCE_RGB});
                for (int i = 0; i < scaled(mc, 40); i++) {               // y: its feet; the burst out of its chest
                    double a = RAND.nextDouble() * Math.PI * 2D, s = 0.8D + RAND.nextDouble() * 0.6D;
                    w.spawnParticle("witchMagic", x, y + 2.1D, z, Math.cos(a) * s, (RAND.nextDouble() - 0.5D) * 0.2D, Math.sin(a) * s);
                }
                break;
            }
            case FX_COIL_DARK:
                burst(mc, w, "fireworksSpark", x, y, z, 16, 0.25D);
                burst(mc, w, "smoke", x, y, z, 8, 0.05D);
                break;
            case FX_COIL_BROKEN:
                w.spawnParticle("largeexplode", x, y - 0.6D, z, 0D, 0D, 0D);
                burst(mc, w, "largesmoke", x, y - 0.5D, z, 14, 0.08D);
                burst(mc, w, "lava", x, y - 0.5D, z, 4, 0D);
                break;
            case FX_COIL_LIT:
                for (int i = 0; i < scaled(mc, 20); i++) {
                    double a = Math.PI * 2D * i / 20D;
                    w.spawnParticle("reddust", x + Math.cos(a) * 0.7D, y - 0.8D + RAND.nextDouble() * 1.4D, z + Math.sin(a) * 0.7D, 0.35D, 0.78D, 1D);
                }
                burst(mc, w, "fireworksSpark", x, y, z, 8, 0.15D);
                break;
            default:
                break;                                          // a kind of a newer server
        }
    }

    private static void burst(Minecraft mc, World w, String name, double x, double y, double z, int n, double speed) {
        for (int i = 0; i < scaled(mc, n); i++) {
            w.spawnParticle(name, x + (RAND.nextDouble() - 0.5D) * 0.4D, y + (RAND.nextDouble() - 0.5D) * 0.4D, z + (RAND.nextDouble() - 0.5D) * 0.4D,
                    (RAND.nextDouble() - 0.5D) * 2D * speed, RAND.nextDouble() * speed, (RAND.nextDouble() - 0.5D) * 2D * speed);
        }
    }

    /** Fewer particles with the «Частицы» setting lower. */
    private static int scaled(Minecraft mc, int n) {
        int ps = mc.gameSettings.particleSetting;               // 0 all, 1 decreased, 2 minimal
        return ps == 0 ? n : ps == 1 ? Math.max(1, n / 2) : Math.max(1, n / 5);
    }

    // ------------------------------------------------------------------ the rings

    private static void render(Minecraft mc, float pt) {
        if (RINGS.isEmpty() || mc.theWorld == null) {
            return;
        }
        Entity cam = mc.renderViewEntity != null ? mc.renderViewEntity : mc.thePlayer;
        double cx = cam.lastTickPosX + (cam.posX - cam.lastTickPosX) * pt;
        double cy = cam.lastTickPosY + (cam.posY - cam.lastTickPosY) * pt;
        double cz = cam.lastTickPosZ + (cam.posZ - cam.lastTickPosZ) * pt;
        float lastX = OpenGlHelper.lastBrightnessX, lastY = OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_CURRENT_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(-cx, -cy, -cz);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);   // additive: light
            GL11.glDepthMask(false);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
            Tessellator t = Tessellator.instance;
            t.startDrawingQuads();
            float now = tick + pt;
            for (double[] r : RINGS) {
                float f = (float) ((now - r[4]) / r[5]);
                if (f < 0F || f > 1F) {
                    continue;
                }
                double radius = 0.5D + (r[3] - 0.5D) * Math.sqrt(f);   // fast at first, slowing at its reach
                ring(t, r[0], r[1], r[2], radius, (int) r[6], 0.8F * (1F - f));
            }
            t.draw();
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
    }

    /** A flat band at (x, y, z) of `radius` and a fading wall over it. */
    private static void ring(Tessellator t, double x, double y, double z, double radius, int rgb, float alpha) {
        int a = (int) (Math.max(0F, Math.min(1F, alpha)) * 255);
        double r0 = Math.max(0D, radius - BAND / 2D), r1 = radius + BAND / 2D;
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2D * i / SEGMENTS, a1 = Math.PI * 2D * (i + 1) / SEGMENTS;
            double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            t.setColorRGBA_I(rgb, a);
            t.addVertex(x + c0 * r0, y + 0.1D, z + s0 * r0);
            t.addVertex(x + c0 * r1, y + 0.1D, z + s0 * r1);
            t.addVertex(x + c1 * r1, y + 0.1D, z + s1 * r1);
            t.addVertex(x + c1 * r0, y + 0.1D, z + s1 * r0);
            t.setColorRGBA_I(rgb, a / 2);                        // the wall: bright at the ground, gone at its top
            t.addVertex(x + c0 * radius, y + 0.1D, z + s0 * radius);
            t.addVertex(x + c1 * radius, y + 0.1D, z + s1 * radius);
            t.setColorRGBA_I(rgb, 0);
            t.addVertex(x + c1 * radius, y + WALL, z + s1 * radius);
            t.addVertex(x + c0 * radius, y + WALL, z + s0 * radius);
        }
    }
}
