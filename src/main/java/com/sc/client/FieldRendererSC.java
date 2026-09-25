package com.sc.client;

import java.util.List;

import org.lwjgl.opengl.GL11;

import com.sc.energy.FieldMode;
import com.sc.tileentity.FieldShapeSC;
import com.sc.tileentity.TileEntityFieldGeneratorSC;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

/**
 * Draws an active Field Generator cluster's shield as a faint, slowly pulsing cyan shell with
 * brighter edges, in the shape of its mode (FieldShapeSC): bubbles around every node, one box,
 * or a prism wall. Only the master renders it; the shape data arrives with the master's
 * description packet. Additive blending, no depth writes, so it never hides what's inside.
 */
public class FieldRendererSC extends TileEntitySpecialRenderer {

    /** The shell's colour, set per field (TileEntityFieldGeneratorSC.COLORS) before each draw. */
    private static float R = 0.35F, G = 0.9F, B = 1.0F;

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        TileEntityFieldGeneratorSC field = (TileEntityFieldGeneratorSC) te;
        List<int[]> nodes = field.getNodePositions();
        if (!field.isMaster() || !field.isActive() || nodes.isEmpty() || !field.has(TileEntityFieldGeneratorSC.F_SHOW)) {
            return;                                             // the owner can hide the shell
        }
        float[] c = TileEntityFieldGeneratorSC.COLORS[field.getColor()];
        R = c[0];
        G = c[1];
        B = c[2];
        float time = te.getWorldObj() == null ? 0 : te.getWorldObj().getTotalWorldTime() + partialTicks;
        float alpha = 0.09F + 0.04F * (float) Math.sin(time * 0.08F);

        GL11.glPushMatrix();
        // The translucent pass keeps alpha test on (> 0.1), which threw away the faint faces for
        // most of each pulse, and runs with its own depth-mask/blend setup that the next
        // translucent tile entity expects back - so save everything and restore it afterwards.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glTranslated(x - te.xCoord, y - te.yCoord, z - te.zCoord);   // draw in world coordinates
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDepthMask(false);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);

        FieldMode mode = field.getMode();
        if (mode == FieldMode.UNION) {
            for (int[] n : nodes) {
                sphere(n[0] + 0.5, n[1] + 0.5, n[2] + 0.5, field.getRange(), alpha);
            }
        } else if (mode == FieldMode.DOME) {
            for (int[] n : nodes) {
                dome(n[0] + 0.5, n[1], n[2] + 0.5, field.getRange(), alpha);
            }
        } else if (mode == FieldMode.CYLINDER) {
            for (int[] n : nodes) {
                cylinder(n[0] + 0.5, n[1] + 0.5, n[2] + 0.5, field.getRange(), alpha);
            }
        } else if (mode == FieldMode.PRISM && FieldShapeSC.hull(nodes).size() >= 3) {
            prism(FieldShapeSC.hull(nodes), FieldShapeSC.bounds(mode, nodes, field.getRange()), field.getRange(), alpha);
        } else {
            box(FieldShapeSC.bounds(mode, nodes, field.getRange()), alpha);
        }

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    private static void box(AxisAlignedBB b, float alpha) {
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA_F(R, G, B, alpha);
        double[][] faces = {
                {b.minX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ}, {b.minX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ},
        };
        // bottom and top
        for (double[] f : faces) {
            t.addVertex(f[0], f[1], f[2]); t.addVertex(f[3], f[1], f[2]); t.addVertex(f[3], f[1], f[5]); t.addVertex(f[0], f[1], f[5]);
        }
        // four walls
        wall(t, b.minX, b.minZ, b.maxX, b.minZ, b.minY, b.maxY);
        wall(t, b.maxX, b.minZ, b.maxX, b.maxZ, b.minY, b.maxY);
        wall(t, b.maxX, b.maxZ, b.minX, b.maxZ, b.minY, b.maxY);
        wall(t, b.minX, b.maxZ, b.minX, b.minZ, b.minY, b.maxY);
        t.draw();

        t.startDrawing(GL11.GL_LINES);
        t.setColorRGBA_F(R, G, B, Math.min(1F, alpha * 4));
        double[] xs = {b.minX, b.maxX}, ys = {b.minY, b.maxY}, zs = {b.minZ, b.maxZ};
        for (double yy : ys) {
            for (double zz : zs) {
                t.addVertex(b.minX, yy, zz); t.addVertex(b.maxX, yy, zz);
            }
            for (double xx : xs) {
                t.addVertex(xx, yy, b.minZ); t.addVertex(xx, yy, b.maxZ);
            }
        }
        for (double xx : xs) {
            for (double zz : zs) {
                t.addVertex(xx, b.minY, zz); t.addVertex(xx, b.maxY, zz);
            }
        }
        t.draw();
    }

    private static void wall(Tessellator t, double x0, double z0, double x1, double z1, double y0, double y1) {
        t.addVertex(x0, y0, z0);
        t.addVertex(x1, y0, z1);
        t.addVertex(x1, y1, z1);
        t.addVertex(x0, y1, z0);
    }

    private static void sphere(double cx, double cy, double cz, double r, float alpha) {
        int lat = 10, lon = 18;
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA_F(R, G, B, alpha);
        for (int i = 0; i < lat; i++) {
            double a0 = Math.PI * i / lat - Math.PI / 2, a1 = Math.PI * (i + 1) / lat - Math.PI / 2;
            for (int j = 0; j < lon; j++) {
                double b0 = 2 * Math.PI * j / lon, b1 = 2 * Math.PI * (j + 1) / lon;
                vertex(t, cx, cy, cz, r, a0, b0);
                vertex(t, cx, cy, cz, r, a0, b1);
                vertex(t, cx, cy, cz, r, a1, b1);
                vertex(t, cx, cy, cz, r, a1, b0);
            }
        }
        t.draw();
        t.startDrawing(GL11.GL_LINES);                          // the equator glows a little brighter
        t.setColorRGBA_F(R, G, B, Math.min(1F, alpha * 4));
        for (int j = 0; j < lon; j++) {
            vertex(t, cx, cy, cz, r, 0, 2 * Math.PI * j / lon);
            vertex(t, cx, cy, cz, r, 0, 2 * Math.PI * (j + 1) / lon);
        }
        t.draw();
    }

    /** The upper half of a sphere standing on the node's floor, with a brighter rim at the ground. */
    private static void dome(double cx, double cy, double cz, double r, float alpha) {
        int lat = 6, lon = 18;
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA_F(R, G, B, alpha);
        for (int i = 0; i < lat; i++) {
            double a0 = Math.PI / 2 * i / lat, a1 = Math.PI / 2 * (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                double b0 = 2 * Math.PI * j / lon, b1 = 2 * Math.PI * (j + 1) / lon;
                vertex(t, cx, cy, cz, r, a0, b0);
                vertex(t, cx, cy, cz, r, a0, b1);
                vertex(t, cx, cy, cz, r, a1, b1);
                vertex(t, cx, cy, cz, r, a1, b0);
            }
        }
        t.draw();
        t.startDrawing(GL11.GL_LINES);
        t.setColorRGBA_F(R, G, B, Math.min(1F, alpha * 4));
        for (int j = 0; j < lon; j++) {
            vertex(t, cx, cy, cz, r, 0, 2 * Math.PI * j / lon);
            vertex(t, cx, cy, cz, r, 0, 2 * Math.PI * (j + 1) / lon);
        }
        t.draw();
    }

    /** An upright cylinder round the node (radius and half-height r), bright rims top and bottom. */
    private static void cylinder(double cx, double cy, double cz, double r, float alpha) {
        int lon = 20;
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA_F(R, G, B, alpha);
        for (int j = 0; j < lon; j++) {
            double b0 = 2 * Math.PI * j / lon, b1 = 2 * Math.PI * (j + 1) / lon;
            double x0 = cx + r * Math.cos(b0), z0 = cz + r * Math.sin(b0), x1 = cx + r * Math.cos(b1), z1 = cz + r * Math.sin(b1);
            wall(t, x0, z0, x1, z1, cy - r, cy + r);
        }
        t.draw();
        t.startDrawing(GL11.GL_LINES);
        t.setColorRGBA_F(R, G, B, Math.min(1F, alpha * 4));
        for (int j = 0; j < lon; j++) {
            double b0 = 2 * Math.PI * j / lon, b1 = 2 * Math.PI * (j + 1) / lon;
            for (double yy : new double[]{cy - r, cy + r}) {
                t.addVertex(cx + r * Math.cos(b0), yy, cz + r * Math.sin(b0));
                t.addVertex(cx + r * Math.cos(b1), yy, cz + r * Math.sin(b1));
            }
        }
        t.draw();
    }

    /** Unit normal pointing out of a counter-clockwise polygon for edge a -> b. */
    private static double[] outwardNormal(double[] a, double[] b) {
        double dx = b[0] - a[0], dz = b[1] - a[1];
        double len = Math.max(1e-9, Math.hypot(dx, dz));
        return new double[]{dz / len, -dx / len};
    }

    private static void vertex(Tessellator t, double cx, double cy, double cz, double r, double lat, double lon) {
        t.addVertex(cx + r * Math.cos(lat) * Math.cos(lon), cy + r * Math.sin(lat), cz + r * Math.cos(lat) * Math.sin(lon));
    }

    /** Walls along the (margin-grown) hull outline, from the field's floor to its top. */
    private static void prism(List<double[]> hull, AxisAlignedBB bounds, int range, float alpha) {
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
        t.startDrawingQuads();
        t.setColorRGBA_F(R, G, B, alpha);
        for (int i = 0; i < n; i++) {
            double[] a = grown[i], b = grown[(i + 1) % n];
            wall(t, a[0], a[1], b[0], b[1], bounds.minY, bounds.maxY);
        }
        t.draw();
        t.startDrawing(GL11.GL_LINES);
        t.setColorRGBA_F(R, G, B, Math.min(1F, alpha * 4));
        for (int i = 0; i < n; i++) {
            double[] a = grown[i], b = grown[(i + 1) % n];
            t.addVertex(a[0], bounds.maxY, a[1]); t.addVertex(b[0], bounds.maxY, b[1]);
            t.addVertex(a[0], bounds.minY, a[1]); t.addVertex(b[0], bounds.minY, b[1]);
            t.addVertex(a[0], bounds.minY, a[1]); t.addVertex(a[0], bounds.maxY, a[1]);
        }
        t.draw();
    }
}
