package com.sc.client;

import org.lwjgl.opengl.GL11;

import com.sc.tileentity.TileEntityArmorStationSC;
import com.sc.util.ArmorGasSC.Gas;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * The Armour Service Station's "live windows": on the two sides beside its front, eight narrow
 * windows (drawn into armorStationSide.png) each show its inner tank's level in the gas's colour.
 * The levels (0..15) come with the description packet, only when they change (at most once a
 * second) - the renderer just draws a few flat quads, no textures.
 */
public class ArmorStationRendererSC extends TileEntitySpecialRenderer {

    /** Texture pixels of armorStationSide.png (32 x 32): window k spans x WIN_X + k * WIN_STEP .. + WIN_W, y WIN_TOP .. WIN_BOTTOM. */
    private static final int TEX = 32, WIN_X = 4, WIN_STEP = 3, WIN_W = 2, WIN_TOP = 7, WIN_BOTTOM = 25;
    /** Just outside the face, against z-fighting. */
    private static final double OUT = 0.002;

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        TileEntityArmorStationSC st = (TileEntityArmorStationSC) te;
        boolean any = false;
        for (Gas g : Gas.values()) {
            any |= st.windowLevel(g) > 0;
        }
        if (!any) {
            return;
        }
        ForgeDirection front = st.getFacing();
        ForgeDirection[] sides = {front.getRotation(ForgeDirection.UP), front.getRotation(ForgeDirection.DOWN)};
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        for (ForgeDirection n : sides) {
            int light = te.getWorldObj().getLightBrightnessForSkyBlocks(te.xCoord + n.offsetX, te.yCoord, te.zCoord + n.offsetZ, 5);
            t.setBrightness(light);
            for (Gas g : Gas.values()) {
                int lv = st.windowLevel(g);
                if (lv <= 0) {
                    continue;
                }
                double u0 = (WIN_X + g.ordinal() * WIN_STEP) / (double) TEX, u1 = u0 + WIN_W / (double) TEX;
                double v0 = (TEX - WIN_BOTTOM) / (double) TEX;
                double v1 = v0 + (WIN_BOTTOM - WIN_TOP) / (double) TEX * lv / TileEntityArmorStationSC.WINDOW_LEVELS;
                t.setColorOpaque_I(g.color);
                quad(t, n, u0, v0, u1, v1);
                double top = Math.max(v0, v1 - 1.0 / TEX);                 // a brighter surface line
                t.setColorOpaque_I(lighter(g.color));
                quad(t, n, u0, top, u1, v1);
            }
        }
        t.draw();
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        GL11.glPopMatrix();
    }

    /** A rectangle on the face with outward normal n: u left to right, v bottom to top, as seen from outside. */
    private static void quad(Tessellator t, ForgeDirection n, double u0, double v0, double u1, double v1) {
        double rx = n.offsetZ, rz = -n.offsetX;                            // the face's "right" seen from outside
        double cx = 0.5 + n.offsetX * (0.5 + OUT), cz = 0.5 + n.offsetZ * (0.5 + OUT);
        t.addVertex(cx + rx * (u0 - 0.5), v0, cz + rz * (u0 - 0.5));
        t.addVertex(cx + rx * (u0 - 0.5), v1, cz + rz * (u0 - 0.5));
        t.addVertex(cx + rx * (u1 - 0.5), v1, cz + rz * (u1 - 0.5));
        t.addVertex(cx + rx * (u1 - 0.5), v0, cz + rz * (u1 - 0.5));
    }

    private static int lighter(int c) {
        int r = Math.min(255, ((c >> 16) & 255) + 70), g = Math.min(255, ((c >> 8) & 255) + 70), b = Math.min(255, (c & 255) + 70);
        return r << 16 | g << 8 | b;
    }
}
