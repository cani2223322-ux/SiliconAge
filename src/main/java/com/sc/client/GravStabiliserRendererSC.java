package com.sc.client;

import org.lwjgl.opengl.GL11;

import com.sc.tileentity.TileEntityGravStabiliserSC;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

/**
 * The Gravitational Stabiliser's orb: a small cube floating over the pillar - dim grey-violet, and
 * bright, bobbing, with a halo while a Singular station near it works.
 */
public class GravStabiliserRendererSC extends TileEntitySpecialRenderer {

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        TileEntityGravStabiliserSC s = (TileEntityGravStabiliserSC) te;
        boolean glow = s.glowing();
        float time = (te.getWorldObj() == null ? 0 : te.getWorldObj().getTotalWorldTime()) + partialTicks;
        double bob = glow ? Math.sin(time * 0.15) * 0.03 : 0;
        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y + 0.88 + bob, z + 0.5);
        GL11.glRotatef(glow ? time * 4F % 360F : 45F, 0F, 1F, 0F);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        float lx = OpenGlHelper.lastBrightnessX, ly = OpenGlHelper.lastBrightnessY;
        if (glow) {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
        }
        cube(0.085, glow ? 0xBE6EFF : 0x5C4C70, 255);
        if (glow) {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GL11.glDepthMask(false);
            cube(0.15, 0xBE6EFF, 70);
            GL11.glDepthMask(true);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_BLEND);
        }
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        GL11.glPopMatrix();
    }

    /** A cube of half-size h round the origin, shaded per face. */
    private static void cube(double h, int rgb, int alpha) {
        Tessellator t = Tessellator.instance;
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        t.startDrawingQuads();
        float[] shade = {1F, 0.6F, 0.85F, 0.85F, 0.75F, 0.75F};
        double[][][] faces = {
            {{-h, h, -h}, {-h, h, h}, {h, h, h}, {h, h, -h}},
            {{-h, -h, -h}, {h, -h, -h}, {h, -h, h}, {-h, -h, h}},
            {{-h, -h, -h}, {-h, h, -h}, {h, h, -h}, {h, -h, -h}},
            {{-h, -h, h}, {h, -h, h}, {h, h, h}, {-h, h, h}},
            {{-h, -h, -h}, {-h, -h, h}, {-h, h, h}, {-h, h, -h}},
            {{h, -h, -h}, {h, h, -h}, {h, h, h}, {h, -h, h}},
        };
        for (int f = 0; f < 6; f++) {
            t.setColorRGBA((int) (r * shade[f]), (int) (g * shade[f]), (int) (b * shade[f]), alpha);
            for (double[] v : faces[f]) {
                t.addVertex(v[0], v[1], v[2]);
            }
        }
        t.draw();
    }
}
