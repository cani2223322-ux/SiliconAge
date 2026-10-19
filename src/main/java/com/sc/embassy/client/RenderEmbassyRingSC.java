package com.sc.embassy.client;

import org.lwjgl.opengl.GL11;

import com.sc.Reference;
import com.sc.bridge.BridgeMathSC;
import com.sc.bridge.BridgeVortexMathSC;
import com.sc.embassy.EmbassyLayoutSC;
import com.sc.embassy.EmbassyRulesSC;
import com.sc.tileentity.TileEntityEmbassyRingSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

/**
 * PACKAGE R (docs/plan-substrate-tech-f6.md §7.4, rule ПЯ-3): the Great Ring's window drawn by its core's tile (TileEntityEmbassyRingSC
 * - the phase it heard, its ticks, how long it has been on, the ring's middle and plane):
 * <ul>
 * <li>the coils' glow - a turquoise band over the ring's coils (radius RING_R in its plane): it grows over the countdown with a light
 * running round it faster and faster, burns through the vortex, fades over the cooling;</li>
 * <li>the countdown's charge - a bright point in the ring's middle, growing;</li>
 * <li>the vortex - the Space Bridge's own look (the same textures and numbers: BridgeVortexMathSC - the opening from a point with its
 * flash, the waving rim, the spiral layers going deep with the dark core between them, the starfield, the halo), VORTEX_HALF x 2 + 1
 * (5 x 5) across; it shrinks to a point in the last CLOSE_TICKS.</li>
 * </ul>
 * No depth writes, lighting off, full bright; every GL state it touches is put back (as BridgeVortexRendererSC, which stays the
 * bridge's own). Client only.
 */
@SideOnly(Side.CLIENT)
public class RenderEmbassyRingSC extends TileEntitySpecialRenderer {

    private static final ResourceLocation SPIRAL = new ResourceLocation(Reference.ASSETS, "textures/fx/vortex_spiral.png");
    private static final ResourceLocation STARS = new ResourceLocation(Reference.ASSETS, "textures/fx/vortex_stars.png");
    private static final ResourceLocation GLOW = new ResourceLocation(Reference.ASSETS, "textures/fx/vortex_glow.png");

    private static final int SEG = 48;
    private static final float INNER = 0.72F;
    /** The spiral layers, front to back: turning speed (rad/s), texture zoom, alpha (the bridge's Space vortex's). */
    private static final float[] SPEED = {0.45F, 0.7F, 1.0F, 1.45F}, ZOOM = {1.0F, 1.18F, 1.4F, 1.7F}, ALPHA = {0.95F, 0.6F, 0.45F, 0.35F};
    private static final float DEPTH = 0.035F;
    /** The vortex's cells across (EmbassyLayoutSC.VORTEX_HALF x 2 + 1) and its shrinking at the end (ticks). */
    private static final int CELLS = EmbassyLayoutSC.VORTEX_HALF * 2 + 1, CLOSE_TICKS = 8;
    /** «Бирюза Наладчиков» (0x48F0DC). */
    private static final float TR = 0x48 / 255F, TG = 0xF0 / 255F, TB = 0xDC / 255F;
    /** The coils' band: half its width; its brightest alpha. */
    private static final double BAND = 0.55D;
    private static final float BAND_ALPHA = 0.6F;

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partial) {
        if (!(te instanceof TileEntityEmbassyRingSC) || te.getWorldObj() == null) {
            return;
        }
        TileEntityEmbassyRingSC ring = (TileEntityEmbassyRingSC) te;
        int phase = ring.phase();
        if (phase == EmbassyRulesSC.PHASE_IDLE) {
            return;
        }
        int len = Math.max(1, TileEntityEmbassyRingSC.length(phase));
        float age = Math.min(len, ring.age() + partial), k = age / len;
        float glow = phase == EmbassyRulesSC.PHASE_COUNTDOWN ? k * k * (3F - 2F * k) : phase == EmbassyRulesSC.PHASE_VORTEX ? 1F : (1F - k) * (1F - k);
        double cx = x + ring.ringDx() + 0.5D, cy = y + ring.ringDy() + 0.5D, cz = z + ring.ringDz() + 0.5D;
        long seed = (te.xCoord * 73856093L) ^ (te.yCoord * 19349663L) ^ (te.zCoord * 83492791L);
        long ms = System.currentTimeMillis();
        double t = (ms % 3600000L) / 1000.0D;
        int axis = ring.axis();

        float lx = OpenGlHelper.lastBrightnessX, ly = OpenGlHelper.lastBrightnessY;
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_LINE_BIT
                | GL11.GL_CURRENT_BIT);
        GL11.glTranslated(cx, cy, cz);
        if (axis == TileEntityEmbassyRingSC.AXIS_Z) {
            GL11.glRotatef(90F, 0F, 1F, 0F);                     // local +Z (the plane's normal) along world +X
        }
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDepthMask(false);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
        Tessellator tes = Tessellator.instance;
        // which side of the ring the camera is on (local +Z is the normal): the vortex's deeper layers go away from it
        double camZ = axis == TileEntityEmbassyRingSC.AXIS_Z ? -cx : -cz;
        float side = camZ >= 0 ? 1F : -1F;

        coils(tes, glow, phase, k, t);
        if (phase == EmbassyRulesSC.PHASE_COUNTDOWN) {
            Minecraft.getMinecraft().getTextureManager().bindTexture(GLOW);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            double h = 0.3D + 1.6D * k + 0.15D * Math.sin(t * 9D);
            quad(tes, h, side * 0.01F, TR, TG, TB, 0.35F + 0.5F * k);
            quad(tes, h * 0.4D, side * 0.015F, 1F, 1F, 1F, 0.3F + 0.6F * k);
        } else if (phase == EmbassyRulesSC.PHASE_VORTEX) {
            float left = ring.ticksLeft() - partial;
            float scale = BridgeVortexMathSC.openScale(age) * Math.max(0F, Math.min(1F, left / CLOSE_TICKS));
            float flash = Math.max(BridgeVortexMathSC.openFlash(age), left < CLOSE_TICKS ? 1F - Math.max(0F, left) / CLOSE_TICKS : 0F);
            vortex(tes, side, scale, flash, t, seed);
        }

        GL11.glPopAttrib();
        GL11.glPopMatrix();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** The coils' glow: a band over the ring (radius RING_R), a light running round it (faster as the countdown ends). */
    private static void coils(Tessellator tes, float glow, int phase, float k, double t) {
        if (glow <= 0.01F) {
            return;
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        double r = EmbassyLayoutSC.RING_R, speed = phase == EmbassyRulesSC.PHASE_COUNTDOWN ? 2D + 10D * k : phase == EmbassyRulesSC.PHASE_VORTEX ? 4D : 1D;
        float pulse = 0.85F + 0.15F * (float) Math.sin(t * 3.1D);
        tes.startDrawing(GL11.GL_TRIANGLES);
        double prev = 0D;
        for (int i = 1; i <= SEG; i++) {
            double th = Math.PI * 2D * i / SEG;
            float b0 = glow * pulse * runner(prev, t, speed), b1 = glow * pulse * runner(th, t, speed);
            for (int s = 0; s < 2; s++) {                       // the band: bright on the coils, fading out both ways
                double i0 = s == 0 ? r - BAND : r, i1 = s == 0 ? r : r + BAND;
                float a0 = s == 0 ? 0F : 1F, a1 = s == 0 ? 1F : 0F;
                vertex(tes, prev, i0, TR, TG, TB, BAND_ALPHA * b0 * a0);
                vertex(tes, prev, i1, TR, TG, TB, BAND_ALPHA * b0 * a1);
                vertex(tes, th, i1, TR, TG, TB, BAND_ALPHA * b1 * a1);
                vertex(tes, prev, i0, TR, TG, TB, BAND_ALPHA * b0 * a0);
                vertex(tes, th, i1, TR, TG, TB, BAND_ALPHA * b1 * a1);
                vertex(tes, th, i0, TR, TG, TB, BAND_ALPHA * b1 * a0);
            }
            prev = th;
        }
        tes.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /** The running light's brightness at angle `th`: 0.45 everywhere, up to 1 at the four runners. */
    private static float runner(double th, double t, double speed) {
        double v = Math.cos(4D * th - t * speed);
        return 0.45F + 0.55F * (float) Math.pow(Math.max(0D, v), 6D);
    }

    private static void vertex(Tessellator tes, double th, double rad, float r, float g, float b, float a) {
        tes.setColorRGBA_F(r, g, b, Math.max(0F, Math.min(1F, a)));
        tes.addVertex(Math.cos(th) * rad, Math.sin(th) * rad, 0D);
    }

    /** The Space Bridge's vortex (its colours, layers and rim), `scale` of its size, `flash` - the white point it grows from. */
    private static void vortex(Tessellator tes, float side, float scale, float flash, double t, long seed) {
        Minecraft mc = Minecraft.getMinecraft();
        float[] col = BridgeVortexMathSC.colour(BridgeMathSC.SPACE, 100);
        float br = 0.9F + 0.1F * (float) Math.sin(t * 2.2D);
        double rad = CELLS * 0.5D * 1.12D * scale;
        if (rad > 0.01D) {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            disc(tes, rad, -side * DEPTH * 4.2F, 0, 1, col[0] * 0.14F, col[1] * 0.14F, col[2] * 0.18F, 0.82F, t, seed);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            mc.getTextureManager().bindTexture(STARS);
            disc(tes, rad * 0.95D, -side * DEPTH * 4F, t * 0.12D, 1.1F, 1F, 1F, 1F, 0.9F * br, t, seed);
            mc.getTextureManager().bindTexture(SPIRAL);
            for (int i = ALPHA.length - 1; i >= 0; i--) {
                if (i == 1) {                                    // the dark core between the deep layers and the front ones
                    mc.getTextureManager().bindTexture(GLOW);
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    quad(tes, rad * 0.62D, -side * DEPTH * 1.5F, 0F, 0F, 0.02F, 0.8F);
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                    mc.getTextureManager().bindTexture(SPIRAL);
                }
                float kk = (1F - 0.16F * i) * br;
                disc(tes, rad * (1D - 0.08D * i), -side * DEPTH * i, -t * SPEED[i], ZOOM[i], col[0] * kk, col[1] * kk, col[2] * kk, ALPHA[i], t, seed);
            }
            mc.getTextureManager().bindTexture(GLOW);              // the halo, a little turquoise: the Tuners' ring round it
            quad(tes, rad * 1.8D, side * 0.005F, (col[0] + TR) * 0.5F, (col[1] + TG) * 0.5F, (col[2] + TB) * 0.5F, 0.28F * br);
        }
        if (flash > 0.01F) {
            mc.getTextureManager().bindTexture(GLOW);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            double fr = CELLS * 0.5D * (0.35D + 1.1D * flash);
            quad(tes, fr, side * 0.02F, 1F, 1F, 1F, Math.min(1F, flash * 1.2F));
            quad(tes, fr * 0.45D, side * 0.025F, 1F, 1F, 1F, Math.min(1F, flash * 1.5F));
        }
    }

    /** The disc's radius at angle `th`: the waving rim. */
    private static double rimAt(double rad, double th, double t, long seed) {
        return rad * (1D + BridgeVortexMathSC.RIM_WAVE * BridgeVortexMathSC.rimNoise(th, t, seed));
    }

    /** One layer: a fan to INNER of the waving rim at full alpha, then a band fading to nothing at the rim; the texture turned by `rot`. */
    private static void disc(Tessellator tes, double rad, float depth, double rot, float zoom, float r, float g, float b, float a, double t,
                             long seed) {
        double ref = rad * (1D + BridgeVortexMathSC.RIM_WAVE) * 2D * zoom, cr = Math.cos(rot), sr = Math.sin(rot);
        tes.startDrawing(GL11.GL_TRIANGLES);
        double prevTh = 0D, prevR = rimAt(rad, 0D, t, seed);
        for (int i = 1; i <= SEG; i++) {
            double th = Math.PI * 2D * i / SEG, rr = rimAt(rad, th, t, seed);
            double ix0 = Math.cos(prevTh) * prevR * INNER, iy0 = Math.sin(prevTh) * prevR * INNER;
            double ix1 = Math.cos(th) * rr * INNER, iy1 = Math.sin(th) * rr * INNER;
            double ox0 = Math.cos(prevTh) * prevR, oy0 = Math.sin(prevTh) * prevR, ox1 = Math.cos(th) * rr, oy1 = Math.sin(th) * rr;
            uv(tes, 0D, 0D, depth, r, g, b, a, cr, sr, ref);
            uv(tes, ix0, iy0, depth, r, g, b, a, cr, sr, ref);
            uv(tes, ix1, iy1, depth, r, g, b, a, cr, sr, ref);
            uv(tes, ix0, iy0, depth, r, g, b, a, cr, sr, ref);
            uv(tes, ox0, oy0, depth, r, g, b, 0F, cr, sr, ref);
            uv(tes, ox1, oy1, depth, r, g, b, 0F, cr, sr, ref);
            uv(tes, ix0, iy0, depth, r, g, b, a, cr, sr, ref);
            uv(tes, ox1, oy1, depth, r, g, b, 0F, cr, sr, ref);
            uv(tes, ix1, iy1, depth, r, g, b, a, cr, sr, ref);
            prevTh = th;
            prevR = rr;
        }
        tes.draw();
    }

    private static void uv(Tessellator tes, double px, double py, double pz, float r, float g, float b, float a, double cr, double sr, double ref) {
        tes.setColorRGBA_F(r, g, b, Math.max(0F, Math.min(1F, a)));
        tes.addVertexWithUV(px, py, pz, 0.5D + (px * cr - py * sr) / ref, 0.5D + (px * sr + py * cr) / ref);
    }

    /** A flat textured square of half-size `h` in the ring's plane (the halo, the core, the flash, the charge). */
    private static void quad(Tessellator tes, double h, float depth, float r, float g, float b, float a) {
        tes.startDrawingQuads();
        tes.setColorRGBA_F(r, g, b, Math.max(0F, Math.min(1F, a)));
        tes.addVertexWithUV(-h, -h, depth, 0D, 1D);
        tes.addVertexWithUV(h, -h, depth, 1D, 1D);
        tes.addVertexWithUV(h, h, depth, 1D, 0D);
        tes.addVertexWithUV(-h, h, depth, 0D, 0D);
        tes.draw();
    }
}
