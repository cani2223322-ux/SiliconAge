package com.sc.dim.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.IRenderHandler;

/**
 * PACKAGE P (docs/plan-substrate-tech-f5.md §10.1, rule ПМ-19; proposal §3 «Небо и свет»): the Substrate's own sky, drawn instead of
 * RenderGlobal.renderSky when this client's dimension.customSky is on and ShadersModCore isn't there (SubstrateSkySC.renderer - the
 * only way in). The same steps and GL states as vanilla's surface sky, with its own bodies:
 * <ul>
 * <li>the dome and the dark below the horizon - vanilla's two planes, the colour the provider's (WorldProviderSubstrateSC.getSkyColor:
 * the pale teal day, the deep blue night, the EMP storm's violet), asked once a game tick (a frame asks nothing new of it);</li>
 * <li>the twilight - vanilla's fan with the provider's calcSunriseSunsetColors (its copper and violet, its wider window);</li>
 * <li>a dim ring low over the horizon («аккреционный диск») - fixed to the ground, as a planet's ring seen from its surface: a band
 * 2..10° up, tilted a little, copper along its bright line and violet at its edges, in clumps; brighter at night;</li>
 * <li>a small sharp white sun (a third of vanilla's) with a faint halo, and vanilla's moon with its eight phases;</li>
 * <li>the stars as a circuit: pads joined by short straight traces along the sky's parallels and meridians (a grid of 4°, a 45°
 * bend now and then), vias at the corners, and dim plain stars between - by a fixed seed (STAR_SEED): the same every night, in
 * every world and on every client.</li>
 * </ul>
 * Everything static is built once into display lists and two small textures made here (no resource files): again after a resource
 * reload (onResourceManagerReload - the lists and the textures rebuilt on the next frame). A frame allocates nothing of its own: the
 * lists are called, the twilight's fan and the under-horizon box go through the Tessellator's own buffer as vanilla's do. Client only.
 */
@SideOnly(Side.CLIENT)
public final class SubstrateSkyRenderSC extends IRenderHandler implements IResourceManagerReloadListener {

    /** The stars' fixed seed: one circuit for every night, world and client. */
    public static final long STAR_SEED = 0x51140E14L;
    private static final ResourceLocation MOON = new ResourceLocation("textures/environment/moon_phases.png");

    /** The display lists: offsets from `base` (the moon's eight phases at the end). */
    private static final int L_DOME = 0, L_DARK = 1, L_RING = 2, L_SUN = 3, L_STARS = 4, L_TRACES = 5, L_PADS = 6, L_MOON = 7,
            LISTS = L_MOON + 8;
    /** The celestial sphere's radius (vanilla's). */
    private static final double R = 100D;
    /** The sun's quad (half-size; vanilla's 30) and its texture: the disc's radius and the halo in texels of SUN_TEX. */
    private static final double SUN_HALF = 12D;
    private static final int SUN_TEX = 32;
    private static final float SUN_DISC = 4.6F;
    /** The moon's quad (half-size, vanilla's). */
    private static final double MOON_HALF = 20D;

    /** The ring: its middle's height over the horizon, its tilt and half-height (degrees), segments, texture repeats round it. */
    private static final double RING_ELEV = 5.5D, RING_TILT = 2.5D, RING_HALF = 3.6D;
    private static final int RING_SEGMENTS = 96, RING_REPEAT = 3, RING_W = 128, RING_H = 16;
    /** The ring's strength by day and at night (x the texture's alpha, additive). */
    private static final float RING_DAY = 0.22F, RING_NIGHT = 0.62F;

    /** The circuit's lattice: 4° cells, rows up to LAT_CELLS off the sun's path (±68°), LON_CELLS round it. */
    private static final double STEP = Math.toRadians(4D);
    private static final int LAT_CELLS = 17, LON_CELLS = 90;
    /** Traces, their steps (MIN_STEPS..MIN_STEPS + EXTRA_STEPS - 1), a turn's and a 45° bend's chance a step; plain stars. */
    private static final int TRACES = 170, MIN_STEPS = 3, EXTRA_STEPS = 7, PLAIN_STARS = 800;
    private static final float TURN = 0.28F, DIAGONAL = 0.1F;
    /** Half-widths: a trace, a pad at a trace's end, a via at a corner; plain stars' half-sizes. */
    private static final double TRACE_HALF = 0.1D, PAD_HALF = 0.34D, VIA_HALF = 0.2D, PLAIN_MIN = 0.07D, PLAIN_SPAN = 0.08D;
    /** The colours (x the star brightness): the plain stars, the traces (teal, and dimmer), the pads (a warm white). */
    private static final float[] PLAIN_RGB = {0.82F, 0.86F, 0.95F}, TRACE_RGB = {0.42F, 0.86F, 0.80F}, PAD_RGB = {1.0F, 0.96F, 0.88F};
    private static final float TRACE_ALPHA = 0.75F;
    /** Lattice steps E, N, W, S. */
    private static final int[] DI = {0, 1, 0, -1}, DJ = {1, 0, -1, 0};

    private int base;
    private DynamicTexture sunTex, ringTex;
    private boolean dirty = true;
    /** The provider's sky colour, asked once a game tick. */
    private long colourTick = Long.MIN_VALUE;
    private float skyR, skyG, skyB;

    SubstrateSkyRenderSC() {
    }

    /** A resource reload: the lists and the textures are built again on the next frame (the GL thread). */
    @Override
    public void onResourceManagerReload(IResourceManager manager) {
        dirty = true;
    }

    @Override
    public void render(float pt, WorldClient world, Minecraft mc) {
        if (world == null || mc == null) {
            return;
        }
        if (dirty || base == 0) {
            build();
        }
        long now = world.getTotalWorldTime();
        if (now != colourTick) {
            Vec3 c = world.getSkyColor(mc.renderViewEntity, pt);
            skyR = (float) c.xCoord;
            skyG = (float) c.yCoord;
            skyB = (float) c.zCoord;
            colourTick = now;
        }
        float r = skyR, g = skyG, b = skyB;
        if (mc.gameSettings.anaglyph) {
            float ar = (r * 30F + g * 59F + b * 11F) / 100F, ag = (r * 30F + g * 70F) / 100F, ab = (r * 30F + b * 70F) / 100F;
            r = ar;
            g = ag;
            b = ab;
        }
        // the dome, in the fog as vanilla's (the horizon melts into the fog's colour)
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_FOG);
        GL11.glColor3f(r, g, b);
        GL11.glCallList(base + L_DOME);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        RenderHelper.disableStandardItemLighting();
        float angle = world.getCelestialAngle(pt);
        twilight(world, mc, pt, angle);
        float clear = 1F - world.getRainStrength(pt);
        // the ring - fixed to the ground, added to the sky
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.glBlendFunc(770, 1, 1, 0);
        float night = 1F - daylight(angle);
        GL11.glColor4f(1F, 1F, 1F, (RING_DAY + (RING_NIGHT - RING_DAY) * night) * clear);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, ringTex.getGlTextureId());
        GL11.glCallList(base + L_RING);
        // the turning sky: the sun, the moon, the stars (vanilla's frame)
        GL11.glPushMatrix();
        GL11.glRotatef(-90F, 0F, 1F, 0F);
        GL11.glRotatef(angle * 360F, 1F, 0F, 0F);
        GL11.glColor4f(1F, 1F, 1F, clear);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sunTex.getGlTextureId());
        GL11.glCallList(base + L_SUN);
        mc.renderEngine.bindTexture(MOON);
        GL11.glCallList(base + L_MOON + (world.getMoonPhase() & 7));
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        float s = world.getStarBrightness(pt) * clear;
        if (s > 0F) {
            GL11.glColor4f(PLAIN_RGB[0] * s, PLAIN_RGB[1] * s, PLAIN_RGB[2] * s, s);
            GL11.glCallList(base + L_STARS);
            GL11.glColor4f(TRACE_RGB[0] * s, TRACE_RGB[1] * s, TRACE_RGB[2] * s, s * TRACE_ALPHA);
            GL11.glCallList(base + L_TRACES);
            GL11.glColor4f(PAD_RGB[0] * s, PAD_RGB[1] * s, PAD_RGB[2] * s, s);
            GL11.glCallList(base + L_PADS);
        }
        GL11.glColor4f(1F, 1F, 1F, 1F);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_FOG);
        GL11.glPopMatrix();
        horizon(world, mc, pt, r, g, b);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDepthMask(true);
    }

    /** How much day it is at `angle` (the provider's and vanilla's sky factor): 1 by day, 0 at night. */
    private static float daylight(float angle) {
        float f = MathHelper.cos(angle * (float) Math.PI * 2F) * 2F + 0.5F;
        return f < 0F ? 0F : f > 1F ? 1F : f;
    }

    /** Vanilla's twilight fan, the provider's colours (its array is reused - nothing allocated). */
    private static void twilight(WorldClient world, Minecraft mc, float pt, float angle) {
        float[] dusk = world.provider.calcSunriseSunsetColors(angle, pt);
        if (dusk == null) {
            return;
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glPushMatrix();
        GL11.glRotatef(90F, 1F, 0F, 0F);
        GL11.glRotatef(MathHelper.sin(world.getCelestialAngleRadians(pt)) < 0F ? 180F : 0F, 0F, 0F, 1F);
        GL11.glRotatef(90F, 0F, 0F, 1F);
        float r = dusk[0], g = dusk[1], b = dusk[2];
        if (mc.gameSettings.anaglyph) {
            float ar = (r * 30F + g * 59F + b * 11F) / 100F, ag = (r * 30F + g * 70F) / 100F, ab = (r * 30F + b * 70F) / 100F;
            r = ar;
            g = ag;
            b = ab;
        }
        Tessellator t = Tessellator.instance;
        t.startDrawing(GL11.GL_TRIANGLE_FAN);
        t.setColorRGBA_F(r, g, b, dusk[3]);
        t.addVertex(0D, 100D, 0D);
        t.setColorRGBA_F(dusk[0], dusk[1], dusk[2], 0F);
        for (int j = 0; j <= 16; j++) {
            float a = j * (float) Math.PI * 2F / 16F;
            float sn = MathHelper.sin(a), cs = MathHelper.cos(a);
            t.addVertex(sn * 120F, cs * 120F, -cs * 40F * dusk[3]);
        }
        t.draw();
        GL11.glPopMatrix();
        GL11.glShadeModel(GL11.GL_FLAT);
    }

    /** Vanilla's dark under the horizon: the box when the eye is below it, the lower plane in the horizon's shade. */
    private void horizon(WorldClient world, Minecraft mc, float pt, float r, float g, float b) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor3f(0F, 0F, 0F);
        EntityPlayer p = mc.thePlayer;
        double eye = p == null ? world.getHorizon() + 64D : p.prevPosY + (p.posY - p.prevPosY) * pt;
        double d0 = eye - world.getHorizon();
        if (d0 < 0D) {
            GL11.glPushMatrix();
            GL11.glTranslatef(0F, 12F, 0F);
            GL11.glCallList(base + L_DARK);
            GL11.glPopMatrix();
            double s = 1D, lo = -(d0 + 65D), hi = -s;
            Tessellator t = Tessellator.instance;
            t.startDrawingQuads();
            t.setColorRGBA_I(0, 255);
            t.addVertex(-s, lo, s);
            t.addVertex(s, lo, s);
            t.addVertex(s, hi, s);
            t.addVertex(-s, hi, s);
            t.addVertex(-s, hi, -s);
            t.addVertex(s, hi, -s);
            t.addVertex(s, lo, -s);
            t.addVertex(-s, lo, -s);
            t.addVertex(s, hi, -s);
            t.addVertex(s, hi, s);
            t.addVertex(s, lo, s);
            t.addVertex(s, lo, -s);
            t.addVertex(-s, lo, -s);
            t.addVertex(-s, lo, s);
            t.addVertex(-s, hi, s);
            t.addVertex(-s, hi, -s);
            t.addVertex(-s, hi, -s);
            t.addVertex(-s, hi, s);
            t.addVertex(s, hi, s);
            t.addVertex(s, hi, -s);
            t.draw();
        }
        if (world.provider.isSkyColored()) {
            GL11.glColor3f(r * 0.2F + 0.04F, g * 0.2F + 0.04F, b * 0.6F + 0.1F);
        } else {
            GL11.glColor3f(r, g, b);
        }
        GL11.glPushMatrix();
        GL11.glTranslatef(0F, -((float) (d0 - 16D)), 0F);
        GL11.glCallList(base + L_DARK);
        GL11.glPopMatrix();
    }

    // ================================================================== built once

    /** The lists and the textures (the old ones let go first). The GL thread. */
    private void build() {
        release();
        base = GLAllocation.generateDisplayLists(LISTS);
        Tessellator t = Tessellator.instance;
        GL11.glNewList(base + L_DOME, GL11.GL_COMPILE);
        plane(t, 16D, false);
        GL11.glEndList();
        GL11.glNewList(base + L_DARK, GL11.GL_COMPILE);
        plane(t, -16D, true);
        GL11.glEndList();
        GL11.glNewList(base + L_RING, GL11.GL_COMPILE);
        ring(t);
        GL11.glEndList();
        GL11.glNewList(base + L_SUN, GL11.GL_COMPILE);
        t.startDrawingQuads();
        t.addVertexWithUV(-SUN_HALF, R, -SUN_HALF, 0D, 0D);
        t.addVertexWithUV(SUN_HALF, R, -SUN_HALF, 1D, 0D);
        t.addVertexWithUV(SUN_HALF, R, SUN_HALF, 1D, 1D);
        t.addVertexWithUV(-SUN_HALF, R, SUN_HALF, 0D, 1D);
        t.draw();
        GL11.glEndList();
        for (int k = 0; k < 8; k++) {
            float u0 = (k % 4) / 4F, v0 = (k / 4 % 2) / 2F, u1 = (k % 4 + 1) / 4F, v1 = (k / 4 % 2 + 1) / 2F;
            GL11.glNewList(base + L_MOON + k, GL11.GL_COMPILE);
            t.startDrawingQuads();
            t.addVertexWithUV(-MOON_HALF, -R, MOON_HALF, u1, v1);
            t.addVertexWithUV(MOON_HALF, -R, MOON_HALF, u0, v1);
            t.addVertexWithUV(MOON_HALF, -R, -MOON_HALF, u0, v0);
            t.addVertexWithUV(-MOON_HALF, -R, -MOON_HALF, u1, v0);
            t.draw();
            GL11.glEndList();
        }
        stars(t, base);
        sunTex = texture(SUN_TEX, SUN_TEX, sunPixels(), false);
        ringTex = texture(RING_W, RING_H, ringPixels(), true);
        dirty = false;
    }

    /** The lists and the textures freed (a rebuild; nothing to free the first time). */
    private void release() {
        if (base != 0) {
            GLAllocation.deleteDisplayLists(base);
            base = 0;
        }
        if (sunTex != null) {
            sunTex.deleteGlTexture();
            sunTex = null;
        }
        if (ringTex != null) {
            ringTex.deleteGlTexture();
            ringTex = null;
        }
    }

    /** Vanilla's sky plane at height `y` (the dome above, wound to face down; the dark below, wound to face up). */
    private static void plane(Tessellator t, double y, boolean below) {
        int cell = 64, n = 256 / cell + 2;
        t.startDrawingQuads();
        for (int j = -cell * n; j <= cell * n; j += cell) {
            for (int k = -cell * n; k <= cell * n; k += cell) {
                if (below) {
                    t.addVertex(j + cell, y, k);
                    t.addVertex(j, y, k);
                    t.addVertex(j, y, k + cell);
                    t.addVertex(j + cell, y, k + cell);
                } else {
                    t.addVertex(j, y, k);
                    t.addVertex(j + cell, y, k);
                    t.addVertex(j + cell, y, k + cell);
                    t.addVertex(j, y, k + cell);
                }
            }
        }
        t.draw();
    }

    /** The ring's band: RING_SEGMENTS quads round the horizon, its middle RING_ELEV up and tilted by RING_TILT. */
    private static void ring(Tessellator t) {
        t.startDrawingQuads();
        double[][] p = new double[4][];
        double[][] uv = new double[4][];
        for (int k = 0; k < RING_SEGMENTS; k++) {
            double a0 = k * Math.PI * 2D / RING_SEGMENTS, a1 = (k + 1) * Math.PI * 2D / RING_SEGMENTS;
            double u0 = (double) k / RING_SEGMENTS * RING_REPEAT, u1 = (double) (k + 1) / RING_SEGMENTS * RING_REPEAT;
            p[0] = horizonPoint(a0, ringMid(a0) - RING_HALF);
            p[1] = horizonPoint(a1, ringMid(a1) - RING_HALF);
            p[2] = horizonPoint(a1, ringMid(a1) + RING_HALF);
            p[3] = horizonPoint(a0, ringMid(a0) + RING_HALF);
            uv[0] = new double[]{u0, 1D};
            uv[1] = new double[]{u1, 1D};
            uv[2] = new double[]{u1, 0D};
            uv[3] = new double[]{u0, 0D};
            quad(t, p, uv);
        }
        t.draw();
    }

    /** The ring's middle at azimuth `a` (degrees over the horizon). */
    private static double ringMid(double a) {
        return RING_ELEV + RING_TILT * Math.sin(a + 0.9D);
    }

    /** A point of the sphere at azimuth `a` (radians) and `elevDeg` over the horizon. */
    private static double[] horizonPoint(double a, double elevDeg) {
        double e = Math.toRadians(elevDeg);
        return new double[]{R * Math.cos(e) * Math.cos(a), R * Math.sin(e), R * Math.cos(e) * Math.sin(a)};
    }

    /**
     * The stars: the circuit (random walks on the lattice - each step a trace, a pad where a walk starts and ends, a via where it
     * turns; a step onto a trace already there ends the walk) and the plain stars, all from STAR_SEED.
     */
    private static void stars(Tessellator t, int list) {
        Random r = new Random(STAR_SEED);
        List<double[]> traces = new ArrayList<double[]>(), pads = new ArrayList<double[]>();
        Set<Long> used = new HashSet<Long>();
        for (int n = 0; n < TRACES; n++) {
            int i = r.nextInt(2 * LAT_CELLS - 3) - (LAT_CELLS - 2), j = r.nextInt(LON_CELLS);
            int dir = r.nextInt(4), steps = MIN_STEPS + r.nextInt(EXTRA_STEPS);
            pads.add(new double[]{i, j, PAD_HALF});
            for (int k = 0; k < steps; k++) {
                if (k > 0 && r.nextFloat() < TURN) {
                    dir = (dir + (r.nextBoolean() ? 1 : 3)) & 3;
                    pads.add(new double[]{i, j, VIA_HALF});
                }
                int di = DI[dir], dj = DJ[dir];
                if (r.nextFloat() < DIAGONAL) {                 // one step aslant: a 45° bend
                    if (di == 0) {
                        di = r.nextBoolean() ? 1 : -1;
                    } else {
                        dj = r.nextBoolean() ? 1 : -1;
                    }
                }
                int ni = i + di, nj = j + dj;
                if (Math.abs(ni) > LAT_CELLS || !used.add(edge(i, j, ni, nj))) {
                    break;
                }
                traces.add(new double[]{i, j, ni, nj});
                i = ni;
                j = ((nj % LON_CELLS) + LON_CELLS) % LON_CELLS;
            }
            pads.add(new double[]{i, j, PAD_HALF});
        }
        double[][] p = new double[4][];
        // the traces: a thin quad along each step's chord (5..7 blocks at R; the arc's sag is under 0.1), a half-width past each end
        GL11.glNewList(list + L_TRACES, GL11.GL_COMPILE);
        t.startDrawingQuads();
        for (double[] s : traces) {
            double[] a = lattice(s[0], s[1]), b = lattice(s[2], s[3]);
            double[] d = unit(b[0] - a[0], b[1] - a[1], b[2] - a[2]), m = unit(a[0] + b[0], a[1] + b[1], a[2] + b[2]);
            double[] side = unit(d[1] * m[2] - d[2] * m[1], d[2] * m[0] - d[0] * m[2], d[0] * m[1] - d[1] * m[0]);
            double ax = a[0] - d[0] * TRACE_HALF, ay = a[1] - d[1] * TRACE_HALF, az = a[2] - d[2] * TRACE_HALF;
            double bx = b[0] + d[0] * TRACE_HALF, by = b[1] + d[1] * TRACE_HALF, bz = b[2] + d[2] * TRACE_HALF;
            double sx = side[0] * TRACE_HALF, sy = side[1] * TRACE_HALF, sz = side[2] * TRACE_HALF;
            p[0] = new double[]{ax + sx, ay + sy, az + sz};
            p[1] = new double[]{bx + sx, by + sy, bz + sz};
            p[2] = new double[]{bx - sx, by - sy, bz - sz};
            p[3] = new double[]{ax - sx, ay - sy, az - sz};
            quad(t, p, null);
        }
        t.draw();
        GL11.glEndList();
        // the pads and the vias: squares along the lattice's parallel and meridian there
        GL11.glNewList(list + L_PADS, GL11.GL_COMPILE);
        t.startDrawingQuads();
        for (double[] q : pads) {
            double lat = q[0] * STEP, lon = q[1] * STEP, h = q[2];
            double[] c = lattice(q[0], q[1]);
            double[] eLat = {Math.cos(lat) * h, -Math.sin(lat) * Math.cos(lon) * h, -Math.sin(lat) * Math.sin(lon) * h};
            double[] eLon = {0D, -Math.sin(lon) * h, Math.cos(lon) * h};
            p[0] = new double[]{c[0] + eLat[0] + eLon[0], c[1] + eLat[1] + eLon[1], c[2] + eLat[2] + eLon[2]};
            p[1] = new double[]{c[0] - eLat[0] + eLon[0], c[1] - eLat[1] + eLon[1], c[2] - eLat[2] + eLon[2]};
            p[2] = new double[]{c[0] - eLat[0] - eLon[0], c[1] - eLat[1] - eLon[1], c[2] - eLat[2] - eLon[2]};
            p[3] = new double[]{c[0] + eLat[0] - eLon[0], c[1] + eLat[1] - eLon[1], c[2] + eLat[2] - eLon[2]};
            quad(t, p, null);
        }
        t.draw();
        GL11.glEndList();
        // the plain stars: vanilla's way (a random direction, a small square turned at random), smaller and dimmer
        GL11.glNewList(list + L_STARS, GL11.GL_COMPILE);
        t.startDrawingQuads();
        for (int n = 0; n < PLAIN_STARS; n++) {
            double x = r.nextFloat() * 2F - 1F, y = r.nextFloat() * 2F - 1F, z = r.nextFloat() * 2F - 1F;
            double h = PLAIN_MIN + r.nextFloat() * PLAIN_SPAN, turn = r.nextDouble() * Math.PI * 2D;
            double len = x * x + y * y + z * z;
            if (len >= 1D || len <= 0.01D) {
                continue;
            }
            double[] v = unit(x, y, z);
            double[] e1 = Math.abs(v[1]) < 0.9D ? unit(v[2], 0D, -v[0]) : unit(0D, -v[2], v[1]);     // v x Y, else v x X
            double[] e2 = {v[1] * e1[2] - v[2] * e1[1], v[2] * e1[0] - v[0] * e1[2], v[0] * e1[1] - v[1] * e1[0]};
            double cs = Math.cos(turn) * h, sn = Math.sin(turn) * h;
            double[] a = {e1[0] * cs + e2[0] * sn, e1[1] * cs + e2[1] * sn, e1[2] * cs + e2[2] * sn};
            double[] b = {e2[0] * cs - e1[0] * sn, e2[1] * cs - e1[1] * sn, e2[2] * cs - e1[2] * sn};
            double cx = v[0] * R, cy = v[1] * R, cz = v[2] * R;
            p[0] = new double[]{cx + a[0] + b[0], cy + a[1] + b[1], cz + a[2] + b[2]};
            p[1] = new double[]{cx - a[0] + b[0], cy - a[1] + b[1], cz - a[2] + b[2]};
            p[2] = new double[]{cx - a[0] - b[0], cy - a[1] - b[1], cz - a[2] - b[2]};
            p[3] = new double[]{cx + a[0] - b[0], cy + a[1] - b[1], cz + a[2] - b[2]};
            quad(t, p, null);
        }
        t.draw();
        GL11.glEndList();
    }

    /** (x, y, z) made 1 long ((0, 1, 0) for a zero vector). */
    private static double[] unit(double x, double y, double z) {
        double l = Math.sqrt(x * x + y * y + z * z);
        return l < 1.0E-9D ? new double[]{0D, 1D, 0D} : new double[]{x / l, y / l, z / l};
    }

    /** A lattice edge's key, the same both ways (j taken round the circle). */
    private static long edge(int i0, int j0, int i1, int j1) {
        long a = (long) (i0 + LAT_CELLS) * LON_CELLS + (((j0 % LON_CELLS) + LON_CELLS) % LON_CELLS);
        long b = (long) (i1 + LAT_CELLS) * LON_CELLS + (((j1 % LON_CELLS) + LON_CELLS) % LON_CELLS);
        return Math.min(a, b) * 1000003L + Math.max(a, b);
    }

    /** The lattice point (lat row `i`, lon column `j`) on the sphere: the polar axis X (the sky turns round it), lat 0 the sun's path. */
    private static double[] lattice(double i, double j) {
        double lat = i * STEP, lon = j * STEP;
        return new double[]{R * Math.sin(lat), R * Math.cos(lat) * Math.cos(lon), R * Math.cos(lat) * Math.sin(lon)};
    }

    /**
     * A quad of corners `p` (in order round it) wound to face the eye at the origin (vanilla's sky culls its back faces), with
     * texture coordinates `uv` (null: none).
     */
    private static void quad(Tessellator t, double[][] p, double[][] uv) {
        double[] a = p[0], b = p[1], d = p[3];
        double ex = b[0] - a[0], ey = b[1] - a[1], ez = b[2] - a[2], fx = d[0] - a[0], fy = d[1] - a[1], fz = d[2] - a[2];
        double nx = ey * fz - ez * fy, ny = ez * fx - ex * fz, nz = ex * fy - ey * fx;
        boolean outward = nx * a[0] + ny * a[1] + nz * a[2] > 0D;
        for (int k = 0; k < 4; k++) {
            int q = outward ? (4 - k) % 4 : k;
            if (uv == null) {
                t.addVertex(p[q][0], p[q][1], p[q][2]);
            } else {
                t.addVertexWithUV(p[q][0], p[q][1], p[q][2], uv[q][0], uv[q][1]);
            }
        }
    }

    // ------------------------------------------------------------------ the two textures (ARGB, made here)

    private static DynamicTexture texture(int w, int h, int[] argb, boolean repeatU) {
        DynamicTexture d = new DynamicTexture(w, h);
        System.arraycopy(argb, 0, d.getTextureData(), 0, argb.length);
        d.updateDynamicTexture();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, d.getGlTextureId());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, repeatU ? GL11.GL_REPEAT : GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        return d;
    }

    /** The sun: a sharp white disc of SUN_DISC texels (one texel of edge) in a faint blue-white halo out to the quad's edge. */
    private static int[] sunPixels() {
        int[] px = new int[SUN_TEX * SUN_TEX];
        float c = (SUN_TEX - 1) / 2F, edge = SUN_TEX / 2F;
        for (int y = 0; y < SUN_TEX; y++) {
            for (int x = 0; x < SUN_TEX; x++) {
                float d = (float) Math.sqrt((x - c) * (x - c) + (y - c) * (y - c));
                float disc = clamp01(SUN_DISC + 0.5F - d);
                float halo = d >= edge ? 0F : 0.42F * (float) Math.pow(1F - d / edge, 2.4D);
                float a = Math.max(disc, halo);
                int rr = Math.round(255F * (0.88F + 0.12F * disc)), gg = Math.round(255F * (0.93F + 0.07F * disc));
                px[y * SUN_TEX + x] = argb(a, rr, gg, 255);
            }
        }
        return px;
    }

    /**
     * The ring's band (u round it, repeating; v from its top edge down): a soft violet glow, a brighter copper line in its lower
     * half, clumps along it; nothing at its edges.
     */
    private static int[] ringPixels() {
        int[] px = new int[RING_W * RING_H];
        for (int y = 0; y < RING_H; y++) {
            double v = (y + 0.5D) / RING_H;
            double glow = 0.55D * Math.exp(-sq((v - 0.55D) / 0.26D)), line = Math.exp(-sq((v - 0.62D) / 0.08D));
            double fade = smooth(v / 0.18D) * smooth((1D - v) / 0.18D);
            for (int x = 0; x < RING_W; x++) {
                double u = (x + 0.5D) / RING_W * Math.PI * 2D;
                double clump = 0.7D + 0.3D * (0.5D * Math.sin(3D * u + 0.7D) + 0.3D * Math.sin(7D * u + 2.1D) + 0.2D * Math.sin(17D * u + 0.3D));
                double a = Math.min(1D, (glow + 0.5D * line) * clump * fade);
                double k = Math.min(1D, line * 0.85D + 0.15D);            // copper on the line, violet off it
                int rr = (int) Math.round(255D * (0.55D + (0.88D - 0.55D) * k));
                int gg = (int) Math.round(255D * (0.36D + (0.58D - 0.36D) * k));
                int bb = (int) Math.round(255D * (0.86D + (0.34D - 0.86D) * k));
                px[y * RING_W + x] = argb((float) a, rr, gg, bb);
            }
        }
        return px;
    }

    private static int argb(float a, int r, int g, int b) {
        int ai = Math.max(0, Math.min(255, Math.round(a * 255F)));
        return ai << 24 | Math.max(0, Math.min(255, r)) << 16 | Math.max(0, Math.min(255, g)) << 8 | Math.max(0, Math.min(255, b));
    }

    private static float clamp01(float f) {
        return f < 0F ? 0F : f > 1F ? 1F : f;
    }

    private static double sq(double d) {
        return d * d;
    }

    /** 0 at 0, 1 from 1 on, smooth between. */
    private static double smooth(double t) {
        double c = t < 0D ? 0D : t > 1D ? 1D : t;
        return c * c * (3D - 2D * c);
    }
}
