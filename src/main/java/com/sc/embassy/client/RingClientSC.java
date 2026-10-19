package com.sc.embassy.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.lwjgl.opengl.GL11;

import com.sc.Reference;
import com.sc.embassy.FlightSC;
import com.sc.tileentity.TileEntityEmbassyRingSC;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * PACKAGE R (docs/plan-substrate-tech-f6.md §7.4, rule ПЯ-3): the Great Ring on the client - the TESR of TileEntityEmbassyRingSC (the
 * vortex in the ring's middle while its phase is VORTEX: the bridge's own vortex look - client.BridgeVortexFxSC may be called, not
 * changed; the countdown's growing glow; the cooling's fading coils) and FLIGHT_FX at a Substrate dock (a column of light over the
 * beacon for the channel's 20 s, a flash where a passenger went). Registered by ClientProxy.preInit (the coordinator's line).
 * Fixed: the class, register, onFx; the bodies R's.
 * <p>
 * R's details: the TESR is RenderEmbassyRingSC (the bridge's vortex drawn the same way, its renderer left alone); FLIGHT_FX {k, x, y,
 * z, t} (FlightSC.FX_*) is queued by the network thread and taken by the client tick:
 * <ul>
 * <li>FX_CHANNEL - a camera-facing column of turquoise light («Бирюза Наладчиков») COLUMN_H high over the dock's beacon for t ticks
 * (a newer message for the same beacon renews it), fading in and out, pulsing; FX_CHARGE - the countdown's: a low glow growing to
 * full height as it ends;</li>
 * <li>FX_GONE - a burst of portal motes and sparks where a passenger went, and a white flash there for FLASH_TICKS;</li>
 * <li>all of it drawn in RenderWorldLastEvent: additive, no depth writes, depth test on, no fog, no lightmap; leaving the world (a new
 * client world, the server) drops it.</li>
 * </ul>
 * Client only.
 */
@SideOnly(Side.CLIENT)
public final class RingClientSC {

    private static final RingClientSC INSTANCE = new RingClientSC();
    private static final ResourceLocation GLOW = new ResourceLocation(Reference.ASSETS, "textures/fx/vortex_glow.png");
    /** The column's height, its core's and glow's half width (blocks); a flash's life (ticks). */
    private static final double COLUMN_H = 40D, CORE_W = 0.35D, GLOW_W = 1.6D;
    private static final int FLASH_TICKS = 12, FADE_TICKS = 10;
    private static final float TR = 0x48 / 255F, TG = 0xF0 / 255F, TB = 0xDC / 255F;

    /** The messages not taken yet (written by the network thread). */
    private static final ConcurrentLinkedQueue<NBTTagCompound> IN = new ConcurrentLinkedQueue<NBTTagCompound>();

    /** Client thread only: the columns and flashes being drawn, the world they belong to. */
    private final List<Fx> live = new ArrayList<Fx>();
    private World world;
    private final Random rand = new Random();

    /** One effect: its kind, place, ticks left and whole life. */
    private static final class Fx {
        final int kind;
        final double x, y, z;
        int left, total;

        Fx(int kind, double x, double y, double z, int ticks) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.z = z;
            this.left = this.total = Math.max(1, ticks);
        }
    }

    private RingClientSC() {
    }

    /** ClientProxy.preInit: the ring's TESR (bindTileEntitySpecialRenderer), the effects' tick. */
    public static void register() {
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityEmbassyRingSC.class, new RenderEmbassyRingSC());
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        FMLCommonHandler.instance().bus().register(INSTANCE);
    }

    /** EmbassyNetSC.FLIGHT_FX (any thread). */
    public static void onFx(NBTTagCompound data) {
        if (data != null) {
            IN.add(data);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        World w = mc.theWorld;
        if (w != world) {
            live.clear();                                       // a new world, or none: what was drawn was the old one's
            world = w;
        }
        if (w == null || mc.isGamePaused()) {
            if (w == null) {
                IN.clear();
            }
            return;
        }
        NBTTagCompound d;
        while ((d = IN.poll()) != null) {
            take(w, d);
        }
        for (Iterator<Fx> it = live.iterator(); it.hasNext(); ) {
            Fx f = it.next();
            if (--f.left <= 0) {
                it.remove();
            } else if (f.kind == FlightSC.FX_CHANNEL && f.left % 4 == 0 && mc.thePlayer != null && mc.thePlayer.getDistanceSq(f.x, f.y, f.z) < 48 * 48) {
                double h = rand.nextDouble() * 8D;              // a spark rising in the column now and then
                w.spawnParticle("fireworksSpark", f.x + (rand.nextDouble() - 0.5D) * 0.8D, f.y + h, f.z + (rand.nextDouble() - 0.5D) * 0.8D, 0D, 0.12D, 0D);
            }
        }
    }

    private void take(World w, NBTTagCompound d) {
        int kind = d.getByte("k");
        double x = d.getDouble("x"), y = d.getDouble("y"), z = d.getDouble("z");
        int t = d.getInteger("t");
        if (kind == FlightSC.FX_GONE) {
            for (int i = 0; i < 36; i++) {
                double dx = (rand.nextDouble() - 0.5D) * 2.4D, dy = (rand.nextDouble() - 0.5D) * 2.4D, dz = (rand.nextDouble() - 0.5D) * 2.4D;
                w.spawnParticle("portal", x, y, z, dx, dy, dz);
            }
            for (int i = 0; i < 10; i++) {
                w.spawnParticle("fireworksSpark", x, y, z, (rand.nextDouble() - 0.5D) * 0.3D, rand.nextDouble() * 0.25D, (rand.nextDouble() - 0.5D) * 0.3D);
            }
            live.add(new Fx(kind, x, y, z, FLASH_TICKS));
            return;
        }
        if (kind != FlightSC.FX_CHANNEL && kind != FlightSC.FX_CHARGE) {
            return;
        }
        for (Iterator<Fx> it = live.iterator(); it.hasNext(); ) {
            Fx f = it.next();
            if (f.kind != FlightSC.FX_GONE && Math.abs(f.x - x) < 0.5D && Math.abs(f.z - z) < 0.5D) {
                if (f.kind == kind) {
                    f.left = Math.max(1, t);                    // the same beacon's channel renewed (a late player's resend)
                    return;
                }
                it.remove();                                    // the countdown's glow gives way to the channel
            }
        }
        live.add(new Fx(kind, x, y, z, t));
    }

    @SubscribeEvent
    public void onRenderLast(RenderWorldLastEvent e) {
        if (live.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.theWorld != world || mc.renderViewEntity == null) {
            return;
        }
        double cx = RenderManager.renderPosX, cy = RenderManager.renderPosY, cz = RenderManager.renderPosZ;
        float tt = (float) (mc.theWorld.getTotalWorldTime() % 40L) + e.partialTicks;
        float pulse = 0.8F + 0.2F * MathHelper.sin(tt / 40F * (float) Math.PI * 2F);

        float lx = OpenGlHelper.lastBrightnessX, ly = OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_CURRENT_BIT
                | GL11.GL_FOG_BIT);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
        Tessellator tes = Tessellator.instance;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        tes.startDrawingQuads();
        for (Fx f : live) {
            if (f.kind == FlightSC.FX_GONE) {
                continue;
            }
            float life = Math.min(1F, Math.min(f.left, f.total - f.left + FADE_TICKS) / (float) FADE_TICKS);   // in and out
            double h = COLUMN_H;
            float a = 0.75F * pulse * life;
            if (f.kind == FlightSC.FX_CHARGE) {
                float k = 1F - f.left / (float) f.total;
                h = 2D + (COLUMN_H - 2D) * k * k;
                a = (0.25F + 0.45F * k) * pulse;
            }
            double dx = f.x - cx, dz = f.z - cz, flat = Math.sqrt(dx * dx + dz * dz);
            double px = flat < 1.0E-3D ? 1D : -dz / flat, pz = flat < 1.0E-3D ? 0D : dx / flat;     // across the line of sight
            strip(tes, dx, f.y - cy, dz, h, px * GLOW_W, pz * GLOW_W, TR, TG, TB, a * 0.3F);
            strip(tes, dx, f.y - cy, dz, h, px * CORE_W, pz * CORE_W, 0.85F + 0.15F * TR, 0.85F + 0.15F * TG, 0.85F + 0.15F * TB, a);
        }
        tes.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        mc.getTextureManager().bindTexture(GLOW);
        for (Fx f : live) {
            if (f.kind != FlightSC.FX_GONE) {
                continue;
            }
            float k = f.left / (float) f.total;
            flash(tes, f.x - cx, f.y - cy, f.z - cz, 0.6D + 2.2D * (1F - k), k);
        }
        GL11.glPopAttrib();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** A vertical strip from y0 up `h`, half width (wx, wz) across the line of sight: bright at the foot, gone at the top. */
    private static void strip(Tessellator tes, double x, double y0, double z, double h, double wx, double wz, float r, float g, float b, float a) {
        int seg = 6;
        for (int i = 0; i < seg; i++) {
            double ya = y0 + h * i / seg, yb = y0 + h * (i + 1) / seg;
            float aa = a * (1F - (float) i / seg), ab = a * (1F - (float) (i + 1) / seg);
            tes.setColorRGBA_F(r, g, b, aa);
            tes.addVertex(x - wx, ya, z - wz);
            tes.addVertex(x + wx, ya, z + wz);
            tes.setColorRGBA_F(r, g, b, ab);
            tes.addVertex(x + wx, yb, z + wz);
            tes.addVertex(x - wx, yb, z - wz);
        }
    }

    /** A camera-facing white flash of half size `h` at (x, y, z) (relative to the camera), `a` its strength. */
    private static void flash(Tessellator tes, double x, double y, double z, double h, float a) {
        double rx = ActiveRenderInfo.rotationX * h, rz = ActiveRenderInfo.rotationZ * h;          // the camera's right (as the particles')
        double ux = ActiveRenderInfo.rotationYZ * h, uy = ActiveRenderInfo.rotationXZ * h, uz = ActiveRenderInfo.rotationXY * h;   // its up
        tes.startDrawingQuads();
        tes.setColorRGBA_F(1F, 1F, 1F, Math.max(0F, Math.min(1F, a)));
        tes.addVertexWithUV(x - rx - ux, y - uy, z - rz - uz, 0D, 1D);
        tes.addVertexWithUV(x + rx - ux, y - uy, z + rz - uz, 1D, 1D);
        tes.addVertexWithUV(x + rx + ux, y + uy, z + rz + uz, 1D, 0D);
        tes.addVertexWithUV(x - rx + ux, y + uy, z - rz + uz, 0D, 0D);
        tes.setColorRGBA_F(TR, TG, TB, Math.max(0F, Math.min(1F, a * 0.6F)));
        double k = 1.8D;
        tes.addVertexWithUV(x - (rx + ux) * k, y - uy * k, z - (rz + uz) * k, 0D, 1D);
        tes.addVertexWithUV(x + (rx - ux) * k, y - uy * k, z + (rz - uz) * k, 1D, 1D);
        tes.addVertexWithUV(x + (rx + ux) * k, y + uy * k, z + (rz + uz) * k, 1D, 0D);
        tes.addVertexWithUV(x - (rx - ux) * k, y + uy * k, z - (rz - uz) * k, 0D, 0D);
        tes.draw();
    }
}
