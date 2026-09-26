package com.sc.client;

import java.util.List;

import org.lwjgl.opengl.GL11;

import com.sc.energy.FieldMode;
import com.sc.tileentity.FieldShapeSC;
import com.sc.tileentity.TileEntityFieldGeneratorSC;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

/**
 * Draws a Field Generator cluster's shield in the shape of its zone (FieldShapeSC round
 * TileEntityFieldGeneratorSC.zoneNodes: nodes / cluster centre / a set point, plus the offset,
 * with its own height): a faint shell in the shell colour (pulsing, in waves running up, or
 * still), an outline in the outline colour (with the shell, always, only with a wrench in hand,
 * or never; solid or running dashes), flowing beams from the master to every node, and the Zone
 * tab's preview - the shape not yet applied, as a white dashed wireframe. Brightness scales it
 * all. Only the master renders; the settings arrive with its description packet. Additive
 * blending, no depth writes, so it never hides what's inside.
 */
public class FieldRendererSC extends TileEntitySpecialRenderer {

    /** The Zone tab's pending shape (client), drawn by its master until applied or cancelled; null: none. */
    public static Preview preview;

    public static final class Preview {
        public int x, y, z;
        public FieldMode mode;
        public List<int[]> nodes;
        public int range, height;
    }

    private static float R = 0.35F, G = 0.9F, B = 1.0F, A = 0.1F, time;
    private static int anim;
    private static boolean dash;

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        TileEntityFieldGeneratorSC field = (TileEntityFieldGeneratorSC) te;
        List<int[]> nodes = field.getNodePositions();
        if (!field.isMaster() || nodes.isEmpty()) {
            return;
        }
        boolean active = field.isActive();
        boolean shell = active && field.has(TileEntityFieldGeneratorSC.F_SHOW);
        boolean lines = active && outlineShown(field, shell);
        boolean beams = active && field.has(TileEntityFieldGeneratorSC.F_BEAMS) && nodes.size() > 1;
        Preview p = preview;
        boolean pre = p != null && p.x == te.xCoord && p.y == te.yCoord && p.z == te.zCoord && !p.nodes.isEmpty();
        if (!shell && !lines && !beams && !pre) {
            return;
        }
        time = te.getWorldObj() == null ? 0 : te.getWorldObj().getTotalWorldTime() + partialTicks;
        anim = field.getAnim();
        float bright = field.getBrightness() / 100F;
        float pulse = anim == TileEntityFieldGeneratorSC.ANIM_PULSE ? 0.09F + 0.04F * (float) Math.sin(time * 0.08F) : 0.11F;

        GL11.glPushMatrix();
        // The translucent pass keeps alpha test on (> 0.1), which threw away the faint faces for
        // most of each pulse, and runs with its own depth-mask/blend setup that the next
        // translucent tile entity expects back - so save everything and restore it afterwards.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LIGHTING_BIT
                | GL11.GL_LINE_BIT);
        GL11.glTranslated(x - te.xCoord, y - te.yCoord, z - te.zCoord);   // draw in world coordinates
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDepthMask(false);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);

        List<int[]> zone = field.zoneNodes();
        if (shell) {
            colour(field.rgbF(TileEntityFieldGeneratorSC.RGB_SHELL), pulse * bright);
            shape(field.getMode(), zone, field.getRange(), field.getHeight(), true, false);
        }
        if (lines) {
            colour(field.rgbF(TileEntityFieldGeneratorSC.RGB_OUTLINE), Math.min(1F, pulse * 4) * bright);
            dash = field.has(TileEntityFieldGeneratorSC.F_DASH);
            shape(field.getMode(), zone, field.getRange(), field.getHeight(), false, true);
        }
        if (beams) {
            colour(field.rgbF(TileEntityFieldGeneratorSC.RGB_OUTLINE), Math.min(1F, 0.8F * bright + 0.2F));
            dash = true;
            anim = TileEntityFieldGeneratorSC.ANIM_STATIC;
            GL11.glLineWidth(2F);
            Tessellator t = Tessellator.instance;
            t.startDrawing(GL11.GL_LINES);
            t.setColorRGBA_F(R, G, B, A);
            double mx = te.xCoord + 0.5, my = te.yCoord + 0.8, mz = te.zCoord + 0.5;
            for (int[] n : nodes) {
                if (n[0] != te.xCoord || n[1] != te.yCoord || n[2] != te.zCoord) {
                    seg(t, mx, my, mz, n[0] + 0.5, n[1] + 0.8, n[2] + 0.5);
                }
            }
            t.draw();
            GL11.glLineWidth(1F);
        }
        if (pre) {
            R = G = B = 1F;
            A = 0.85F;
            dash = true;
            anim = TileEntityFieldGeneratorSC.ANIM_STATIC;
            GL11.glLineWidth(2F);
            shape(p.mode, p.nodes, p.range, p.height, false, true);
            wire(p.mode, p.nodes, p.range, p.height);
            GL11.glLineWidth(1F);
        }

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    private static boolean outlineShown(TileEntityFieldGeneratorSC field, boolean shell) {
        switch (field.getOutline()) {
            case TileEntityFieldGeneratorSC.OUTLINE_ALWAYS:
                return true;
            case TileEntityFieldGeneratorSC.OUTLINE_WRENCH: {
                ItemStack held = Minecraft.getMinecraft().thePlayer == null ? null : Minecraft.getMinecraft().thePlayer.getCurrentEquippedItem();
                return held != null && held.getItem() instanceof com.sc.item.ItemWrenchSC;
            }
            case TileEntityFieldGeneratorSC.OUTLINE_NEVER:
                return false;
            default:
                return shell;
        }
    }

    private static void colour(float[] c, float alpha) {
        R = c[0];
        G = c[1];
        B = c[2];
        A = alpha;
    }

    /** The shape's faces or its outline (the edges / rims / equators). */
    private static void shape(FieldMode mode, List<int[]> nodes, int range, int height, boolean faces, boolean lines) {
        double h = FieldShapeSC.heightOf(range, height);
        if (mode == FieldMode.UNION) {
            for (int[] n : nodes) {
                sphere(n[0] + 0.5, n[1] + 0.5, n[2] + 0.5, range, h, faces, lines);
            }
        } else if (mode == FieldMode.DOME) {
            for (int[] n : nodes) {
                dome(n[0] + 0.5, n[1], n[2] + 0.5, range, h, faces, lines);
            }
        } else if (mode == FieldMode.CYLINDER) {
            for (int[] n : nodes) {
                cylinder(n[0] + 0.5, n[1] + 0.5, n[2] + 0.5, range, h, faces, lines);
            }
        } else if (mode == FieldMode.PRISM && FieldShapeSC.hull(nodes).size() >= 3) {
            prism(FieldShapeSC.hull(nodes), FieldShapeSC.bounds(mode, nodes, range, height), range, faces, lines);
        } else {
            box(FieldShapeSC.bounds(mode, nodes, range, height), faces, lines);
        }
    }

    /** The preview's extra lines: meridians and uprights, so a bare outline still reads as a shape. */
    private static void wire(FieldMode mode, List<int[]> nodes, int range, int height) {
        if (mode != FieldMode.UNION && mode != FieldMode.DOME && mode != FieldMode.CYLINDER) {
            return;
        }
        double h = FieldShapeSC.heightOf(range, height);
        Tessellator t = Tessellator.instance;
        t.startDrawing(GL11.GL_LINES);
        t.setColorRGBA_F(R, G, B, A);
        for (int[] n : nodes) {
            double cx = n[0] + 0.5, cz = n[2] + 0.5;
            for (int m = 0; m < 4; m++) {
                double lon = Math.PI / 2 * m;
                if (mode == FieldMode.CYLINDER) {
                    double cy = n[1] + 0.5;
                    seg(t, cx + range * Math.cos(lon), cy - h, cz + range * Math.sin(lon), cx + range * Math.cos(lon), cy + h,
                            cz + range * Math.sin(lon));
                    continue;
                }
                double cy = mode == FieldMode.DOME ? n[1] : n[1] + 0.5;
                int steps = 12;
                double from = mode == FieldMode.DOME ? 0 : -Math.PI / 2;
                for (int i = 0; i < steps; i++) {
                    double a0 = from + (Math.PI / 2 - from) * i / steps, a1 = from + (Math.PI / 2 - from) * (i + 1) / steps;
                    seg(t, cx + range * Math.cos(a0) * Math.cos(lon), cy + h * Math.sin(a0), cz + range * Math.cos(a0) * Math.sin(lon),
                            cx + range * Math.cos(a1) * Math.cos(lon), cy + h * Math.sin(a1), cz + range * Math.cos(a1) * Math.sin(lon));
                }
            }
        }
        t.draw();
    }

    // ------------------------------------------------------------------ vertices

    /** A face vertex: waves make the shell brighter in bands running up. */
    private static void fv(Tessellator t, double x, double y, double z) {
        if (anim == TileEntityFieldGeneratorSC.ANIM_WAVES) {
            float k = 0.5F + 0.5F * (float) Math.sin(y * 0.35 - time * 0.12);
            t.setColorRGBA_F(R, G, B, A * (0.45F + 1.1F * k));
        }
        t.addVertex(x, y, z);
    }

    /** One outline segment - solid, or cut into running dashes. */
    private static void seg(Tessellator t, double x0, double y0, double z0, double x1, double y1, double z1) {
        if (!dash) {
            t.addVertex(x0, y0, z0);
            t.addVertex(x1, y1, z1);
            return;
        }
        double len = Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (z1 - z0) * (z1 - z0));
        double piece = 0.6, phase = time * 0.08;
        int n = Math.max(1, (int) Math.ceil(len / piece));
        for (int i = 0; i < n; i++) {
            if ((((int) Math.floor(i - phase)) & 1) != 0) {
                continue;
            }
            double s0 = (double) i / n, s1 = (double) (i + 1) / n;
            t.addVertex(x0 + (x1 - x0) * s0, y0 + (y1 - y0) * s0, z0 + (z1 - z0) * s0);
            t.addVertex(x0 + (x1 - x0) * s1, y0 + (y1 - y0) * s1, z0 + (z1 - z0) * s1);
        }
    }

    private static void startFaces(Tessellator t) {
        t.startDrawingQuads();
        t.setColorRGBA_F(R, G, B, A);
    }

    private static void startLines(Tessellator t) {
        t.startDrawing(GL11.GL_LINES);
        t.setColorRGBA_F(R, G, B, A);
    }

    // ------------------------------------------------------------------ shapes

    private static void box(AxisAlignedBB b, boolean faces, boolean lines) {
        Tessellator t = Tessellator.instance;
        if (faces) {
            startFaces(t);
            for (double yy : new double[]{b.minY, b.maxY}) {
                fv(t, b.minX, yy, b.minZ); fv(t, b.maxX, yy, b.minZ); fv(t, b.maxX, yy, b.maxZ); fv(t, b.minX, yy, b.maxZ);
            }
            wall(t, b.minX, b.minZ, b.maxX, b.minZ, b.minY, b.maxY);
            wall(t, b.maxX, b.minZ, b.maxX, b.maxZ, b.minY, b.maxY);
            wall(t, b.maxX, b.maxZ, b.minX, b.maxZ, b.minY, b.maxY);
            wall(t, b.minX, b.maxZ, b.minX, b.minZ, b.minY, b.maxY);
            t.draw();
        }
        if (lines) {
            startLines(t);
            double[] xs = {b.minX, b.maxX}, ys = {b.minY, b.maxY}, zs = {b.minZ, b.maxZ};
            for (double yy : ys) {
                for (double zz : zs) {
                    seg(t, b.minX, yy, zz, b.maxX, yy, zz);
                }
                for (double xx : xs) {
                    seg(t, xx, yy, b.minZ, xx, yy, b.maxZ);
                }
            }
            for (double xx : xs) {
                for (double zz : zs) {
                    seg(t, xx, b.minY, zz, xx, b.maxY, zz);
                }
            }
            t.draw();
        }
    }

    /** A wall quad; with waves the upper and lower edges take their own brightness. */
    private static void wall(Tessellator t, double x0, double z0, double x1, double z1, double y0, double y1) {
        fv(t, x0, y0, z0);
        fv(t, x1, y0, z1);
        fv(t, x1, y1, z1);
        fv(t, x0, y1, z0);
    }

    /** A sphere of radius r - an ellipsoid when the height h differs - with a brighter equator. */
    private static void sphere(double cx, double cy, double cz, double r, double h, boolean faces, boolean lines) {
        int lat = 10, lon = 18;
        Tessellator t = Tessellator.instance;
        if (faces) {
            startFaces(t);
            for (int i = 0; i < lat; i++) {
                double a0 = Math.PI * i / lat - Math.PI / 2, a1 = Math.PI * (i + 1) / lat - Math.PI / 2;
                for (int j = 0; j < lon; j++) {
                    double b0 = 2 * Math.PI * j / lon, b1 = 2 * Math.PI * (j + 1) / lon;
                    vertex(t, cx, cy, cz, r, h, a0, b0);
                    vertex(t, cx, cy, cz, r, h, a0, b1);
                    vertex(t, cx, cy, cz, r, h, a1, b1);
                    vertex(t, cx, cy, cz, r, h, a1, b0);
                }
            }
            t.draw();
        }
        if (lines) {
            ring(t, cx, cy, cz, r, lon);
        }
    }

    /** The upper half of a sphere (ellipsoid) standing on the node's floor, with a brighter rim at the ground. */
    private static void dome(double cx, double cy, double cz, double r, double h, boolean faces, boolean lines) {
        int lat = 6, lon = 18;
        Tessellator t = Tessellator.instance;
        if (faces) {
            startFaces(t);
            for (int i = 0; i < lat; i++) {
                double a0 = Math.PI / 2 * i / lat, a1 = Math.PI / 2 * (i + 1) / lat;
                for (int j = 0; j < lon; j++) {
                    double b0 = 2 * Math.PI * j / lon, b1 = 2 * Math.PI * (j + 1) / lon;
                    vertex(t, cx, cy, cz, r, h, a0, b0);
                    vertex(t, cx, cy, cz, r, h, a0, b1);
                    vertex(t, cx, cy, cz, r, h, a1, b1);
                    vertex(t, cx, cy, cz, r, h, a1, b0);
                }
            }
            t.draw();
        }
        if (lines) {
            ring(t, cx, cy, cz, r, lon);
        }
    }

    /** An upright cylinder round the node (radius r, h up and down), bright rims top and bottom. */
    private static void cylinder(double cx, double cy, double cz, double r, double h, boolean faces, boolean lines) {
        int lon = 20;
        Tessellator t = Tessellator.instance;
        if (faces) {
            startFaces(t);
            for (int j = 0; j < lon; j++) {
                double b0 = 2 * Math.PI * j / lon, b1 = 2 * Math.PI * (j + 1) / lon;
                wall(t, cx + r * Math.cos(b0), cz + r * Math.sin(b0), cx + r * Math.cos(b1), cz + r * Math.sin(b1), cy - h, cy + h);
            }
            t.draw();
        }
        if (lines) {
            ring(t, cx, cy - h, cz, r, lon);
            ring(t, cx, cy + h, cz, r, lon);
        }
    }

    /** A horizontal circle of `lon` segments. */
    private static void ring(Tessellator t, double cx, double cy, double cz, double r, int lon) {
        startLines(t);
        for (int j = 0; j < lon; j++) {
            double b0 = 2 * Math.PI * j / lon, b1 = 2 * Math.PI * (j + 1) / lon;
            seg(t, cx + r * Math.cos(b0), cy, cz + r * Math.sin(b0), cx + r * Math.cos(b1), cy, cz + r * Math.sin(b1));
        }
        t.draw();
    }

    /** Unit normal pointing out of a counter-clockwise polygon for edge a -> b. */
    private static double[] outwardNormal(double[] a, double[] b) {
        double dx = b[0] - a[0], dz = b[1] - a[1];
        double len = Math.max(1e-9, Math.hypot(dx, dz));
        return new double[]{dz / len, -dx / len};
    }

    private static void vertex(Tessellator t, double cx, double cy, double cz, double r, double h, double lat, double lon) {
        fv(t, cx + r * Math.cos(lat) * Math.cos(lon), cy + h * Math.sin(lat), cz + r * Math.cos(lat) * Math.sin(lon));
    }

    /** Walls along the (margin-grown) hull outline, from the field's floor to its top. */
    private static void prism(List<double[]> hull, AxisAlignedBB bounds, int range, boolean faces, boolean lines) {
        // Each edge moved out by the margin along its normal, corners where neighbours meet: the
        // drawn wall sits exactly on the protected boundary (edges + BUFFER). Pushing corners
        // out from the centroid drew it ~1.4 blocks in on a square.
        int n = hull.size();
        double[][] grown = new double[n][];
        for (int i = 0; i < n; i++) {
            double[] prev = hull.get((i + n - 1) % n), cur = hull.get(i), next = hull.get((i + 1) % n);
            double[] n1 = outwardNormal(prev, cur), n2 = outwardNormal(cur, next);
            double bx = n1[0] + n2[0], bz = n1[1] + n2[1];
            double dot = n1[0] * n2[0] + n1[1] * n2[1];
            double scale = range / (1 + dot);                    // miter: |bisector| * range / cos(half angle)
            grown[i] = new double[]{cur[0] + bx * scale, cur[1] + bz * scale};
        }
        Tessellator t = Tessellator.instance;
        if (faces) {
            startFaces(t);
            for (int i = 0; i < n; i++) {
                double[] a = grown[i], b = grown[(i + 1) % n];
                wall(t, a[0], a[1], b[0], b[1], bounds.minY, bounds.maxY);
            }
            t.draw();
        }
        if (lines) {
            startLines(t);
            for (int i = 0; i < n; i++) {
                double[] a = grown[i], b = grown[(i + 1) % n];
                seg(t, a[0], bounds.maxY, a[1], b[0], bounds.maxY, b[1]);
                seg(t, a[0], bounds.minY, a[1], b[0], bounds.minY, b[1]);
                seg(t, a[0], bounds.minY, a[1], a[0], bounds.maxY, a[1]);
            }
            t.draw();
        }
    }
}
