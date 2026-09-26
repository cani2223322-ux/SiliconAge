package com.sc.client;

import org.lwjgl.opengl.GL11;

import com.sc.block.BlockConduitSC;
import com.sc.inventory.GuiQuarrySC;
import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityQuarrySC;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;

/**
 * How a quarry's area looks in the world - deliberately unlike the field generator's shimmering
 * shell: only the box's edges as laser lines (dashes running round it), turning diamond beacons
 * on the top corners with the size over the first one, a see-through grid plane on the layer
 * being dug with a scan line sweeping across it, and (with a scanner) the ore found, outlined
 * through the ground. Colours, brightness and when it shows are the quarry's Area tab settings.
 */
public class QuarryRendererSC extends TileEntitySpecialRenderer {

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partial) {
        if (!(te instanceof TileEntityQuarrySC)) {
            return;
        }
        TileEntityQuarrySC q = (TileEntityQuarrySC) te;
        if (!visible(q)) {
            return;
        }
        if (q.isExo()) {
            beam(q, x, y, z, partial);
            return;
        }
        int[] a = q.area();
        if (a == null) {
            return;
        }
        float bright = q.getBrightness() * 0.25F;
        double time = (te.getWorldObj().getTotalWorldTime() + partial) / 20.0;
        GL11.glPushMatrix();
        GL11.glTranslated(x - te.xCoord, y - te.yCoord, z - te.zCoord);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDepthMask(false);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);

        double x0 = a[0], x1 = a[2] + 1, z0 = a[1], z1 = a[3] + 1, yt = a[4] + 1, yb = a[5];
        boolean dash = (q.getVflags() & TileEntityQuarrySC.V_DASH) != 0;
        boolean circle = q.getShape() == TileEntityQuarrySC.SHAPE_CIRCLE && !q.usesCard();
        int frame = q.getColorFrame();

        GL11.glLineWidth(2.5F);
        if (circle) {
            double cx = (x0 + x1) / 2, cz = (z0 + z1) / 2, r = (x1 - x0) / 2;
            ring(cx, cz, r, yt, frame, bright, dash ? time : -1);
            ring(cx, cz, r, yb, frame, bright * 0.6F, dash ? time : -1);
            for (int k = 0; k < 4; k++) {
                double ang = k * Math.PI / 2;
                edge(cx + r * Math.cos(ang), yt, cz + r * Math.sin(ang), cx + r * Math.cos(ang), yb, cz + r * Math.sin(ang), frame, bright * 0.7F, dash ? time : -1);
            }
        } else {
            double[][] c = {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}};
            for (int i = 0; i < 4; i++) {
                double[] p = c[i], n = c[(i + 1) % 4];
                edge(p[0], yt, p[1], n[0], yt, n[1], frame, bright, dash ? time : -1);
                edge(p[0], yb, p[1], n[0], yb, n[1], frame, bright * 0.6F, dash ? time : -1);
                edge(p[0], yt, p[1], p[0], yb, p[1], frame, bright * 0.7F, dash ? time : -1);
            }
        }

        // the dug layer: a grid plane with a scan line sweeping over it
        if ((q.getVflags() & TileEntityQuarrySC.V_PLANE) != 0 && q.getLayerY() >= a[5] && q.getLayerY() <= a[4]) {
            plane(x0, x1, z0, z1, q.getLayerY() + 1.02, q.getColorPlane(), bright, time);
        }

        // diamond beacons on the top corners
        double[][] corners = {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}};
        for (double[] c : corners) {
            diamond(c[0], yt + 1.2 + 0.15 * Math.sin(time * 2 + c[0]), c[1], time * 60, frame, bright);
        }

        GL11.glPopAttrib();

        // the size over the first corner, when close
        EntityPlayer p = Minecraft.getMinecraft().thePlayer;
        if (p != null && p.getDistanceSq(te.xCoord + 0.5, te.yCoord + 0.5, te.zCoord + 0.5) < 32 * 32) {
            label(x0, yt + 2.1, z0, Lang.tr("sc.quarry.label", (int) (x1 - x0), (int) (z1 - z0), (int) (yt - yb)), frame);
        }
        GL11.glPopMatrix();
    }

    /**
     * The Exo Drilling Rig: a laser beam from the rig down to bedrock - a bright core (plane
     * colour) in a softer glow, rings (frame colour) running down it while it works, a flare at
     * the bottom. It pulses while it hauls, and is dimmer when it's stopped.
     */
    private void beam(TileEntityQuarrySC q, double x, double y, double z, float partial) {
        double time = (q.getWorldObj().getTotalWorldTime() + partial) / 20.0;
        float bright = q.getBrightness() * 0.25F * (q.isRunning() ? 1F : 0.35F);
        float pulse = 0.8F + 0.2F * (float) Math.sin(time * 6);
        double top = 0, bottom = -(q.yCoord - 1);
        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y, z + 0.5);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDepthMask(false);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
        boolean glow = (q.getVflags() & TileEntityQuarrySC.V_PLANE) != 0;
        int core = q.getColorPlane(), ring = q.getColorFrame();
        // core and glow: two crossed quads each, turning slowly
        GL11.glPushMatrix();
        GL11.glRotated(time * 40 % 360, 0, 1, 0);
        crossQuads(0.12, top, bottom, 0xFFFFFF, 0.9F * bright * pulse);
        crossQuads(0.22, top, bottom, core, 0.6F * bright * pulse);
        if (glow) {
            crossQuads(0.5, top, bottom, core, 0.18F * bright);
        }
        GL11.glPopMatrix();
        // rings running down
        if ((q.getVflags() & TileEntityQuarrySC.V_DASH) != 0 && q.isRunning()) {
            GL11.glLineWidth(2F);
            double shift = (time * 6) % 3.0;
            for (double ry = top - shift; ry > bottom; ry -= 3.0) {
                ring(0, 0, 0.45, ry, ring, 0.8F * bright, -1);
            }
        }
        // the emitter ring under the rig and the flare at the bottom
        GL11.glLineWidth(3F);
        ring(0, 0, 0.55, top - 0.02, ring, bright, -1);
        for (int k = 0; k < 3; k++) {
            double r = 0.3 + ((time * 1.5 + k / 3.0) % 1.0) * 1.8;
            ring(0, 0, r, bottom + 0.05, core, (float) (bright * (1 - (r - 0.3) / 1.8)), -1);
        }
        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    private static void crossQuads(double w, double top, double bottom, int rgb, float alpha) {
        color(rgb, alpha);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3d(-w, top, 0);
        GL11.glVertex3d(w, top, 0);
        GL11.glVertex3d(w, bottom, 0);
        GL11.glVertex3d(-w, bottom, 0);
        GL11.glVertex3d(0, top, -w);
        GL11.glVertex3d(0, top, w);
        GL11.glVertex3d(0, bottom, w);
        GL11.glVertex3d(0, bottom, -w);
        GL11.glEnd();
    }

    /** Always / only with a wrench in hand / only while its screen is open / never. */
    private static boolean visible(TileEntityQuarrySC q) {
        switch (q.getShow()) {
            case TileEntityQuarrySC.SHOW_ALWAYS:
                return true;
            case TileEntityQuarrySC.SHOW_WRENCH: {
                EntityPlayer p = Minecraft.getMinecraft().thePlayer;
                return p != null && (BlockConduitSC.isWrench(p.getHeldItem()) || p.getHeldItem() != null
                        && p.getHeldItem().getItem() instanceof com.sc.item.ItemAreaCardSC);
            }
            case TileEntityQuarrySC.SHOW_MENU: {
                int[] at = GuiQuarrySC.openAt;
                return at != null && at[0] == q.xCoord && at[1] == q.yCoord && at[2] == q.zCoord;
            }
            default:
                return false;
        }
    }

    private static void color(int rgb, float alpha) {
        GL11.glColor4f((rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F, alpha);
    }

    /** A laser edge; with `time` >= 0 as dashes running along it (half a block on, half off). */
    private static void edge(double ax, double ay, double az, double bx, double by, double bz, int rgb, float alpha, double time) {
        double len = Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az));
        color(rgb, alpha);
        GL11.glBegin(GL11.GL_LINES);
        if (time < 0 || len < 0.01) {
            GL11.glVertex3d(ax, ay, az);
            GL11.glVertex3d(bx, by, bz);
        } else {
            double shift = (time * 1.5) % 1.0;
            for (double s = shift - 1; s < len; s += 1.0) {
                double s0 = Math.max(0, s), s1 = Math.min(len, s + 0.5);
                if (s1 <= s0) {
                    continue;
                }
                GL11.glVertex3d(ax + (bx - ax) * s0 / len, ay + (by - ay) * s0 / len, az + (bz - az) * s0 / len);
                GL11.glVertex3d(ax + (bx - ax) * s1 / len, ay + (by - ay) * s1 / len, az + (bz - az) * s1 / len);
            }
        }
        GL11.glEnd();
    }

    private static void ring(double cx, double cz, double r, double y, int rgb, float alpha, double time) {
        int n = Math.max(24, (int) (r * 6));
        for (int i = 0; i < n; i++) {
            double a0 = i * 2 * Math.PI / n, a1 = (i + 1) * 2 * Math.PI / n;
            boolean on = time < 0 || ((i + (int) (time * 3)) % 2 == 0);
            if (on) {
                edge(cx + r * Math.cos(a0), y, cz + r * Math.sin(a0), cx + r * Math.cos(a1), y, cz + r * Math.sin(a1), rgb, alpha, -1);
            }
        }
    }

    private static void plane(double x0, double x1, double z0, double z1, double y, int rgb, float bright, double time) {
        Tessellator t = Tessellator.instance;
        color(rgb, 0.10F * bright);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3d(x0, y, z0);
        GL11.glVertex3d(x0, y, z1);
        GL11.glVertex3d(x1, y, z1);
        GL11.glVertex3d(x1, y, z0);
        GL11.glEnd();
        double w = x1 - x0, d = z1 - z0;
        int step = Math.max(w, d) > 48 ? 4 : Math.max(w, d) > 24 ? 2 : 1;
        GL11.glLineWidth(1F);
        color(rgb, 0.25F * bright);
        GL11.glBegin(GL11.GL_LINES);
        for (double gx = x0; gx <= x1 + 0.001; gx += step) {
            GL11.glVertex3d(gx, y, z0);
            GL11.glVertex3d(gx, y, z1);
        }
        for (double gz = z0; gz <= z1 + 0.001; gz += step) {
            GL11.glVertex3d(x0, y, gz);
            GL11.glVertex3d(x1, y, gz);
        }
        GL11.glEnd();
        // the scan line
        double sx = x0 + (time * 4) % Math.max(1, w);
        GL11.glLineWidth(3F);
        color(0xFFFFFF, 0.5F * bright);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(sx, y + 0.01, z0);
        GL11.glVertex3d(sx, y + 0.01, z1);
        GL11.glEnd();
        color(rgb, 0.8F * bright);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(sx - 0.15, y + 0.01, z0);
        GL11.glVertex3d(sx - 0.15, y + 0.01, z1);
        GL11.glEnd();
        GL11.glLineWidth(2.5F);
    }

    /** A turning octahedron: see-through faces, bright edges. */
    private static void diamond(double x, double y, double z, double angle, int rgb, float bright) {
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glRotated(angle, 0, 1, 0);
        double s = 0.35, h = 0.5;
        double[][] ring = {{s, 0, 0}, {0, 0, s}, {-s, 0, 0}, {0, 0, -s}};
        color(rgb, 0.35F * bright);
        GL11.glBegin(GL11.GL_TRIANGLES);
        for (int i = 0; i < 4; i++) {
            double[] p = ring[i], n = ring[(i + 1) % 4];
            GL11.glVertex3d(0, h, 0);
            GL11.glVertex3d(p[0], 0, p[2]);
            GL11.glVertex3d(n[0], 0, n[2]);
            GL11.glVertex3d(0, -h, 0);
            GL11.glVertex3d(n[0], 0, n[2]);
            GL11.glVertex3d(p[0], 0, p[2]);
        }
        GL11.glEnd();
        color(rgb, Math.min(1F, bright + 0.2F));
        GL11.glBegin(GL11.GL_LINES);
        for (int i = 0; i < 4; i++) {
            double[] p = ring[i], n = ring[(i + 1) % 4];
            GL11.glVertex3d(p[0], 0, p[2]);
            GL11.glVertex3d(n[0], 0, n[2]);
            GL11.glVertex3d(p[0], 0, p[2]);
            GL11.glVertex3d(0, h, 0);
            GL11.glVertex3d(p[0], 0, p[2]);
            GL11.glVertex3d(0, -h, 0);
        }
        GL11.glEnd();
        GL11.glPopMatrix();
    }

    private static void cube(double ax, double ay, double az, double bx, double by, double bz, int rgb, float alpha) {
        color(rgb, alpha);
        GL11.glBegin(GL11.GL_LINES);
        double[][] v = {{ax, ay, az}, {bx, ay, az}, {bx, ay, bz}, {ax, ay, bz}, {ax, by, az}, {bx, by, az}, {bx, by, bz}, {ax, by, bz}};
        int[][] e = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] l : e) {
            GL11.glVertex3d(v[l[0]][0], v[l[0]][1], v[l[0]][2]);
            GL11.glVertex3d(v[l[1]][0], v[l[1]][1], v[l[1]][2]);
        }
        GL11.glEnd();
    }

    /** A text label facing the camera. */
    private void label(double x, double y, double z, String text, int rgb) {
        FontRenderer font = func_147498_b();
        if (font == null) {
            return;
        }
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glRotatef(-RenderManager.instance.playerViewY, 0, 1, 0);
        GL11.glRotatef(RenderManager.instance.playerViewX, 1, 0, 0);
        float s = 0.03F;
        GL11.glScalef(-s, -s, s);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        int w = font.getStringWidth(text) / 2;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(0F, 0F, 0F, 0.45F);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3d(-w - 2, -2, 0);
        GL11.glVertex3d(-w - 2, 9, 0);
        GL11.glVertex3d(w + 2, 9, 0);
        GL11.glVertex3d(w + 2, -2, 0);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        font.drawString(text, -w, 0, 0xFF000000 | rgb);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        GL11.glPopMatrix();
    }
}
