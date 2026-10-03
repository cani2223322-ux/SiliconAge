package com.sc.client;

import org.lwjgl.opengl.GL11;

import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityBridgeControllerSC;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

/**
 * §11: while the portal is open a hologram of its target floats over the ring - «→ x y z» (and the dimension when
 * it is another one), turned to the viewer, see-through, slowly flickering.
 */
public class BridgeHoloRendererSC extends TileEntitySpecialRenderer {

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partial) {
        if (!(te instanceof TileEntityBridgeControllerSC)) {
            return;
        }
        int[] h = ((TileEntityBridgeControllerSC) te).getHolo();
        if (h == null) {
            return;
        }
        FontRenderer fr = func_147498_b();
        if (fr == null) {
            return;
        }
        int here = te.getWorldObj().provider.dimensionId;
        String s = Lang.tr(h[3] == here ? "sc.bridge.holo" : "sc.bridge.holo.dim", h[0], h[1], h[2], h[3]);
        float f = 0.025F;
        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y + h[4] + 1.6, z + 0.5);
        GL11.glNormal3f(0F, 1F, 0F);
        GL11.glRotatef(-RenderManager.instance.playerViewY, 0F, 1F, 0F);
        GL11.glRotatef(RenderManager.instance.playerViewX, 1F, 0F, 0F);
        GL11.glScalef(-f, -f, f);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
        int w = fr.getStringWidth(s) / 2;
        Tessellator t = Tessellator.instance;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        t.startDrawingQuads();
        t.setColorRGBA_F(0.1F, 0.6F, 0.4F, 0.25F);
        t.addVertex(-w - 2, -2, 0);
        t.addVertex(-w - 2, 9, 0);
        t.addVertex(w + 2, 9, 0);
        t.addVertex(w + 2, -2, 0);
        t.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        int a = 150 + (int) (60 * Math.sin(System.currentTimeMillis() / 180.0));
        fr.drawString(s, -w, 0, (a << 24) | 0x8CFFC8);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        GL11.glPopMatrix();
    }
}
