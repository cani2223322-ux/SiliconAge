package com.sc.client;

import java.util.Random;

import org.lwjgl.opengl.GL11;

import com.sc.Reference;
import com.sc.bridge.BridgeMathSC;
import com.sc.bridge.BridgeVortexMathSC;
import com.sc.tileentity.TileEntityBridgeVortexSC;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

/**
 * The open portal as ONE disc over the whole opening (3 x 3 Ground, 5 x 5 Space), drawn by the opening's centre cell
 * (the other cells draw nothing - BlockBridgeVortexSC has no block model):
 * <ul>
 * <li>ВП1 a seamless spiral (textures/fx/vortex_spiral) turning slowly - the geometry stays, the texture turns;</li>
 * <li>ВП2 a living rim: the disc's outline waves (BridgeVortexMathSC.rimNoise) and fades out;</li>
 * <li>ВП3 depth: four spiral layers behind each other (away from the viewer), smaller, faster, dimmer, with a dark core
 * between them - a tunnel;</li>
 * <li>ВП4 an energy rim with running arcs for an end with no ring round it (a subtle one inside the ring);</li>
 * <li>ВП6 a soft halo round it; ВП7 the opening grows from a bright point (BridgeVortexFxSC draws the collapse);</li>
 * <li>ВП9 the colour by stability, flicker and dark cracks under 30% and the old shaking; ВП10 Space: blue-violet with a
 * turning starfield deep inside.</li>
 * </ul>
 * No depth writes, lighting off, full bright; every GL state it touches is put back.
 */
public class BridgeVortexRendererSC extends TileEntitySpecialRenderer {

    static final ResourceLocation SPIRAL = new ResourceLocation(Reference.ASSETS, "textures/fx/vortex_spiral.png");
    static final ResourceLocation STARS = new ResourceLocation(Reference.ASSETS, "textures/fx/vortex_stars.png");
    static final ResourceLocation GLOW = new ResourceLocation(Reference.ASSETS, "textures/fx/vortex_glow.png");

    private static final int SEG = 48;
    private static final float JITTER = 0.09F, INNER = 0.72F;
    /** The spiral layers, front to back: turning speed (rad/s), texture zoom, alpha. */
    private static final float[] SPEED = {0.45F, 0.7F, 1.0F, 1.45F}, ZOOM = {1.0F, 1.18F, 1.4F, 1.7F}, ALPHA = {0.95F, 0.6F, 0.45F, 0.35F};
    /** The step between the layers' depths (blocks). */
    private static final float DEPTH = 0.035F;

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partial) {
        if (!(te instanceof TileEntityBridgeVortexSC) || te.getWorldObj() == null) {
            return;
        }
        TileEntityBridgeVortexSC v = (TileEntityBridgeVortexSC) te;
        if (!v.isMaster()) {
            return;
        }
        float age = v.getOpenedAt() <= 0 ? 1000F : te.getWorldObj().getTotalWorldTime() - v.getOpenedAt() + partial;
        draw(x + 0.5, y + 0.5, z + 0.5, v.getAxis(), v.getSize(), v.getKind(), v.getStability(), v.isRingless(),
                BridgeVortexMathSC.openScale(age), BridgeVortexMathSC.openFlash(age), seedOf(te.xCoord, te.yCoord, te.zCoord));
    }

    static long seedOf(int x, int y, int z) {
        return (x * 73856093L) ^ (y * 19349663L) ^ (z * 83492791L);
    }

    /**
     * Draws a vortex whose centre is at x y z (relative to the camera). `scale`: its size 0-1 (opening / collapsing);
     * `flash`: a white glow at the centre 0-1 (the birth / the pop).
     */
    static void draw(double x, double y, double z, int axis, int n, int kind, int stab, boolean ringless, float scale, float flash, long seed) {
        long ms = System.currentTimeMillis();
        double t = (ms % 3600000L) / 1000.0;
        if (stab < BridgeVortexMathSC.STAB_BAD) {                // С3: the whole swirl jitters as one
            Random r = new Random((ms / 45) * 341873128712L);
            x += (r.nextFloat() - 0.5F) * 2F * JITTER;
            y += (r.nextFloat() - 0.5F) * 2F * JITTER;
            z += (r.nextFloat() - 0.5F) * 2F * JITTER;
        }
        float[] col = BridgeVortexMathSC.colour(kind, stab);
        float br = BridgeVortexMathSC.flicker(stab, ms) * (0.9F + 0.1F * (float) Math.sin(t * 2.2));   // ВП5 a gentle pulse
        double rad = n * 0.5 * 1.12 * scale;
        // which side of the disc the camera is on (local +Z is the disc's normal): the deeper layers go away from it
        double camZ = axis == 0 ? -z : -x;
        float side = camZ >= 0 ? 1F : -1F;

        float lx = OpenGlHelper.lastBrightnessX, ly = OpenGlHelper.lastBrightnessY;
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_LINE_BIT
                | GL11.GL_CURRENT_BIT);
        GL11.glTranslated(x, y, z);
        if (axis != 0) {
            GL11.glRotatef(90F, 0F, 1F, 0F);                     // local +Z (the normal) along world +X
        }
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDepthMask(false);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
        Minecraft mc = Minecraft.getMinecraft();
        Tessellator tes = Tessellator.instance;

        if (rad > 0.01) {
            boolean space = kind == BridgeMathSC.SPACE;
            // the body: a dark disc so the swirl reads on a bright sky
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            disc(tes, rad, -side * DEPTH * 4.2F, 0, 1, col[0] * 0.14F, col[1] * 0.14F, col[2] * 0.18F, 0.82F, t, seed);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            if (space) {                                         // ВП10 the starfield deep inside
                mc.getTextureManager().bindTexture(STARS);
                disc(tes, rad * 0.95, -side * DEPTH * 4F, t * 0.12, 1.1F, 1F, 1F, 1F, 0.9F * br, t, seed);
            }
            mc.getTextureManager().bindTexture(SPIRAL);
            for (int i = ALPHA.length - 1; i >= 0; i--) {
                if (i == 1) {                                    // the dark core between the deep layers and the front ones
                    mc.getTextureManager().bindTexture(GLOW);
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    quad(tes, rad * 0.62, -side * DEPTH * 1.5F, 0F, 0F, 0.02F, space ? 0.8F : 0.72F);
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                    mc.getTextureManager().bindTexture(SPIRAL);
                }
                float k = (1F - 0.16F * i) * br;
                disc(tes, rad * (1 - 0.08 * i), -side * DEPTH * i, -t * SPEED[i], ZOOM[i], col[0] * k, col[1] * k, col[2] * k, ALPHA[i], t, seed);
            }
            // ВП6 the halo
            mc.getTextureManager().bindTexture(GLOW);
            quad(tes, rad * 1.8, side * 0.005F, col[0], col[1], col[2], 0.28F * br);
            // ВП4 the rim: a glowing band, arcs running on it
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            rim(tes, rad, side, col, ringless, br, t, ms, seed);
            if (stab < BridgeVortexMathSC.STAB_BAD) {             // ВП9 cracks
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                cracks(tes, rad, side, ms, seed, BridgeVortexMathSC.flicker(stab, ms));
            }
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }
        if (flash > 0.01F) {                                    // ВП7 the bright point it grows from / pops into
            mc.getTextureManager().bindTexture(GLOW);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            double fr = n * 0.5 * (0.35 + 1.1 * flash);
            quad(tes, fr, side * 0.02F, 1F, 1F, 1F, Math.min(1F, flash * 1.2F));
            quad(tes, fr * 0.45, side * 0.025F, 1F, 1F, 1F, Math.min(1F, flash * 1.5F));
        }

        GL11.glPopAttrib();
        GL11.glPopMatrix();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** The disc's radius at angle `th`: the waving rim. */
    private static double rimAt(double rad, double th, double t, long seed) {
        return rad * (1 + BridgeVortexMathSC.RIM_WAVE * BridgeVortexMathSC.rimNoise(th, t, seed));
    }

    /**
     * One layer: a fan to INNER of the waving rim at full alpha, then a band fading to nothing at the rim. The texture
     * is laid flat and turned by `rot`, zoomed by `zoom` (never past its inscribed circle).
     */
    private static void disc(Tessellator tes, double rad, float depth, double rot, float zoom, float r, float g, float b, float a, double t,
            long seed) {
        double ref = rad * (1 + BridgeVortexMathSC.RIM_WAVE) * 2 * zoom, cr = Math.cos(rot), sr = Math.sin(rot);
        tes.startDrawing(GL11.GL_TRIANGLES);
        double prevTh = 0, prevR = rimAt(rad, 0, t, seed);
        for (int i = 1; i <= SEG; i++) {
            double th = Math.PI * 2 * i / SEG, rr = rimAt(rad, th, t, seed);
            double ix0 = Math.cos(prevTh) * prevR * INNER, iy0 = Math.sin(prevTh) * prevR * INNER;
            double ix1 = Math.cos(th) * rr * INNER, iy1 = Math.sin(th) * rr * INNER;
            double ox0 = Math.cos(prevTh) * prevR, oy0 = Math.sin(prevTh) * prevR, ox1 = Math.cos(th) * rr, oy1 = Math.sin(th) * rr;
            vert(tes, 0, 0, depth, r, g, b, a, cr, sr, ref);
            vert(tes, ix0, iy0, depth, r, g, b, a, cr, sr, ref);
            vert(tes, ix1, iy1, depth, r, g, b, a, cr, sr, ref);
            vert(tes, ix0, iy0, depth, r, g, b, a, cr, sr, ref);
            vert(tes, ox0, oy0, depth, r, g, b, 0F, cr, sr, ref);
            vert(tes, ox1, oy1, depth, r, g, b, 0F, cr, sr, ref);
            vert(tes, ix0, iy0, depth, r, g, b, a, cr, sr, ref);
            vert(tes, ox1, oy1, depth, r, g, b, 0F, cr, sr, ref);
            vert(tes, ix1, iy1, depth, r, g, b, a, cr, sr, ref);
            prevTh = th;
            prevR = rr;
        }
        tes.draw();
    }

    private static void vert(Tessellator tes, double px, double py, double pz, float r, float g, float b, float a, double cr, double sr, double ref) {
        tes.setColorRGBA_F(r, g, b, Math.max(0F, Math.min(1F, a)));
        tes.addVertexWithUV(px, py, pz, 0.5 + (px * cr - py * sr) / ref, 0.5 + (px * sr + py * cr) / ref);
    }

    /** A flat textured square of half-size `h` in the disc's plane (the halo, the core, the flash). */
    private static void quad(Tessellator tes, double h, float depth, float r, float g, float b, float a) {
        tes.startDrawingQuads();
        tes.setColorRGBA_F(r, g, b, Math.max(0F, Math.min(1F, a)));
        tes.addVertexWithUV(-h, -h, depth, 0, 1);
        tes.addVertexWithUV(h, -h, depth, 1, 1);
        tes.addVertexWithUV(h, h, depth, 1, 0);
        tes.addVertexWithUV(-h, h, depth, 0, 0);
        tes.draw();
    }

    /** ВП4: a band glowing on the rim and lightning arcs running along it (4 for an end with no ring, 1 inside the ring). */
    private static void rim(Tessellator tes, double rad, float side, float[] col, boolean ringless, float br, double t, long ms, long seed) {
        float a = (ringless ? 0.75F : 0.3F) * br;
        float r = col[0] + (1 - col[0]) * 0.35F, g = col[1] + (1 - col[1]) * 0.35F, b = col[2] + (1 - col[2]) * 0.35F;
        float d = side * 0.01F;
        double[] f = {0.9, 1.0, 1.13};
        tes.startDrawing(GL11.GL_TRIANGLES);
        double prevTh = 0, prevR = rimAt(rad, 0, t, seed);
        for (int i = 1; i <= SEG; i++) {
            double th = Math.PI * 2 * i / SEG, rr = rimAt(rad, th, t, seed);
            for (int s = 0; s < 2; s++) {
                float a0 = s == 0 ? 0F : a, a1 = s == 0 ? a : 0F;
                double k0 = f[s], k1 = f[s + 1];
                bandVert(tes, prevTh, prevR * k0, d, r, g, b, a0);
                bandVert(tes, prevTh, prevR * k1, d, r, g, b, a1);
                bandVert(tes, th, rr * k1, d, r, g, b, a1);
                bandVert(tes, prevTh, prevR * k0, d, r, g, b, a0);
                bandVert(tes, th, rr * k1, d, r, g, b, a1);
                bandVert(tes, th, rr * k0, d, r, g, b, a0);
            }
            prevTh = th;
            prevR = rr;
        }
        tes.draw();
        int arcs = ringless ? 4 : 1;
        GL11.glLineWidth(ringless ? 2.2F : 1.4F);
        tes.startDrawing(GL11.GL_LINES);
        for (int k = 0; k < arcs; k++) {
            Random rr = new Random(seed * 31 + (ms / 90) * 7919 + k * 104729);
            double start = rr.nextDouble() * Math.PI * 2, len = 0.5 + 0.6 * rr.nextDouble();
            int segs = 7;
            double pxp = 0, pyp = 0;
            for (int j = 0; j <= segs; j++) {
                double th = start + len * j / segs;
                double rj = rimAt(rad, th, t, seed) * (j == 0 || j == segs ? 1.0 : 1.02 + (rr.nextDouble() - 0.5) * 0.14);
                double px = Math.cos(th) * rj, py = Math.sin(th) * rj;
                if (j > 0) {
                    tes.setColorRGBA_F(0.85F + 0.15F * r, 0.85F + 0.15F * g, 0.85F + 0.15F * b, (ringless ? 0.95F : 0.5F) * br);
                    tes.addVertex(pxp, pyp, d * 1.5);
                    tes.addVertex(px, py, d * 1.5);
                }
                pxp = px;
                pyp = py;
            }
        }
        tes.draw();
    }

    private static void bandVert(Tessellator tes, double th, double r0, float d, float r, float g, float b, float a) {
        tes.setColorRGBA_F(r, g, b, a);
        tes.addVertex(Math.cos(th) * r0, Math.sin(th) * r0, d);
    }

    /** ВП9: dark jagged cracks over the disc (they change every 0.7 s). */
    private static void cracks(Tessellator tes, double rad, float side, long ms, long seed, float fl) {
        Random r = new Random(seed ^ ((ms / 700) * 0x5DEECE66DL));
        GL11.glLineWidth(2.5F);
        tes.startDrawing(GL11.GL_LINES);
        tes.setColorRGBA_F(0.06F, 0.02F, 0F, 0.8F * fl);
        float d = side * 0.02F;
        for (int k = 0; k < 4; k++) {
            double ang = r.nextDouble() * Math.PI * 2, dist = rad * (0.15 + 0.45 * r.nextDouble());
            double px = Math.cos(ang) * dist, py = Math.sin(ang) * dist, dir = ang;
            for (int j = 0; j < 6; j++) {
                dir += (r.nextDouble() - 0.5) * 1.2;
                double nx = px + Math.cos(dir) * rad * 0.12, ny = py + Math.sin(dir) * rad * 0.12;
                if (nx * nx + ny * ny > rad * rad * 0.8) {
                    break;
                }
                tes.addVertex(px, py, d);
                tes.addVertex(nx, ny, d);
                px = nx;
                py = ny;
            }
        }
        tes.draw();
    }
}
