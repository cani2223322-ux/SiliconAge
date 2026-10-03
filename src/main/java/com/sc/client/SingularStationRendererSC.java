package com.sc.client;

import org.lwjgl.opengl.GL11;

import com.sc.tileentity.TileEntitySingularStationSC;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;

/**
 * The Singular Service Station's look (В1): the armour station's gas windows on its sides, and over
 * the pedestal two tilted rings that turn slowly (fast and glowing violet while a process runs);
 * while it works, a slowly spinning translucent hologram of the pieces in the slots (their item
 * icons, stacked helmet to boots) and beams from each counted stabiliser's orb to the rings. Flat
 * quads and lines only - cheap.
 */
public class SingularStationRendererSC extends ArmorStationRendererSC {

    /** Ring centre above the block's origin, the two radii, their thickness. */
    private static final double CY = 1.65, R1 = 0.62, R2 = 0.48, THICK = 0.05;
    private static final int SEGMENTS = 40;

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        super.renderTileEntityAt(te, x, y, z, partialTicks);           // the windows
        TileEntitySingularStationSC st = (TileEntitySingularStationSC) te;
        boolean work = st.isWorking();
        long t = te.getWorldObj() == null ? 0 : te.getWorldObj().getTotalWorldTime();
        float time = t + partialTicks;
        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y + CY, z + 0.5);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        float lx = OpenGlHelper.lastBrightnessX, ly = OpenGlHelper.lastBrightnessY;
        if (work) {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);            // glowing
        } else {
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
        GL11.glDepthMask(!work);
        float speed = work ? 6F : 0.8F;
        ring(time * speed, 18F, R1, work, 1F);
        ring(-time * speed * 1.4F, -24F, R2, work, 0.85F);
        if (work) {
            beams(st, time);
        }
        GL11.glDepthMask(true);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        if (work) {
            hologram(st, time);
        }
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        GL11.glPopMatrix();
    }

    /** A flat ring (an annulus of SEGMENTS quads) turned `spin` degrees round Y and tilted `tilt` round X. */
    private static void ring(float spin, float tilt, double r, boolean work, float alpha) {
        GL11.glPushMatrix();
        GL11.glRotatef(spin % 360F, 0F, 1F, 0F);
        GL11.glRotatef(tilt, 1F, 0F, 0F);
        Tessellator tes = Tessellator.instance;
        tes.startDrawing(GL11.GL_QUAD_STRIP);
        if (work) {
            tes.setColorRGBA(190, 110, 255, (int) (220 * alpha));
        } else {
            tes.setColorRGBA(110, 92, 132, (int) (200 * alpha));
        }
        for (int i = 0; i <= SEGMENTS; i++) {
            double a = i * Math.PI * 2 / SEGMENTS, c = Math.cos(a), s = Math.sin(a);
            tes.addVertex(c * (r - THICK), 0, s * (r - THICK));
            tes.addVertex(c * (r + THICK), 0, s * (r + THICK));
        }
        tes.draw();
        if (work) {                                               // a soft halo round the lit ring
            tes.startDrawing(GL11.GL_QUAD_STRIP);
            tes.setColorRGBA(190, 110, 255, 60);
            for (int i = 0; i <= SEGMENTS; i++) {
                double a = i * Math.PI * 2 / SEGMENTS, c = Math.cos(a), s = Math.sin(a);
                tes.addVertex(c * (r - THICK * 3), 0, s * (r - THICK * 3));
                tes.addVertex(c * (r + THICK * 3), 0, s * (r + THICK * 3));
            }
            tes.draw();
        }
        GL11.glPopMatrix();
    }

    /** Lines from every counted stabiliser's orb to the rings' centre (they flicker a little). */
    private static void beams(TileEntitySingularStationSC st, float time) {
        byte[] off = st.stabiliserOffsets();
        if (off.length < 3) {
            return;
        }
        GL11.glLineWidth(2.5F);
        Tessellator tes = Tessellator.instance;
        tes.startDrawing(GL11.GL_LINES);
        for (int i = 0; i + 2 < off.length; i += 3) {
            int a = 150 + (int) (80 * Math.abs(Math.sin(time * 0.3 + i)));
            tes.setColorRGBA(200, 130, 255, a);
            tes.addVertex(off[i], off[i + 1] + 0.9 - CY, off[i + 2]);
            tes.setColorRGBA(230, 190, 255, a);
            tes.addVertex(0, 0, 0);
        }
        tes.draw();
        GL11.glLineWidth(1F);
    }

    /** The pieces in the slots as translucent violet icons, stacked, turning slowly. */
    private static void hologram(TileEntitySingularStationSC st, float time) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.locationItemsTexture);
        GL11.glPushMatrix();
        GL11.glRotatef(time * 1.5F % 360F, 0F, 1F, 0F);
        Tessellator tes = Tessellator.instance;
        double size = 0.34;
        for (int i = 0; i < 4; i++) {
            ItemStack s = st.holoPiece(i);
            if (s == null) {
                continue;
            }
            IIcon icon = s.getItem().getIcon(s, 0);
            if (icon == null) {
                continue;
            }
            double cy = 0.42 - i * 0.28;                       // helmet on top, boots at the bottom
            tes.startDrawingQuads();
            tes.setColorRGBA(210, 160, 255, 150);
            tes.addVertexWithUV(-size / 2, cy - size / 2, 0, icon.getMinU(), icon.getMaxV());
            tes.addVertexWithUV(size / 2, cy - size / 2, 0, icon.getMaxU(), icon.getMaxV());
            tes.addVertexWithUV(size / 2, cy + size / 2, 0, icon.getMaxU(), icon.getMinV());
            tes.addVertexWithUV(-size / 2, cy + size / 2, 0, icon.getMinU(), icon.getMinV());
            tes.draw();
        }
        GL11.glPopMatrix();
    }
}
