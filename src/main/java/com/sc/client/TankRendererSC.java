package com.sc.client;

import org.lwjgl.opengl.GL11;

import com.sc.tileentity.TileEntityTankSC;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

/**
 * The fluid inside a portable tank, seen through its glass: a liquid fills from the bottom, a gas
 * hangs from the top and is see-through - its height is how full the tank is. Lit by the tank's
 * own light (a glowing fluid glows).
 */
public class TankRendererSC extends TileEntitySpecialRenderer {

    private static final float IN = 0.0625F + 0.01F;     // just inside the frame
    private static final float LO = 0.0625F, HI = 1 - 0.0625F;

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        TileEntityTankSC tank = (TileEntityTankSC) te;
        FluidStack stack = tank.getTank().getFluid();
        if (stack == null || stack.amount <= 0 || stack.getFluid() == null) {
            return;
        }
        Fluid fluid = stack.getFluid();
        IIcon icon = fluid.getStillIcon() != null ? fluid.getStillIcon() : fluid.getIcon();
        if (icon == null) {
            return;
        }
        float fill = Math.min(1F, (float) stack.amount / tank.getTank().getCapacity());
        boolean gas = fluid.isGaseous(stack);
        float h = (HI - LO) * fill;
        float y0 = gas ? HI - h : LO, y1 = gas ? HI : LO + h;
        int color = fluid.getColor(stack);

        bindTexture(TextureMap.locationBlocksTexture);
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        int light = te.getWorldObj().getLightBrightnessForSkyBlocks(te.xCoord, te.yCoord, te.zCoord, fluid.getLuminosity(stack));
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light % 65536, light / 65536);
        GL11.glColor4f(((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F, (color & 255) / 255F, gas ? 0.55F : 0.95F);

        float a = IN, b = 1 - IN;
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        // faces with the texture scaled to their size, counter-clockwise from outside
        quad(t, icon, a, y1, a, a, y1, b, b, y1, b, b, y1, a, b - a, b - a);   // top
        quad(t, icon, a, y0, b, a, y0, a, b, y0, a, b, y0, b, b - a, b - a);   // bottom
        quad(t, icon, a, y0, a, a, y1, a, b, y1, a, b, y0, a, b - a, y1 - y0); // north
        quad(t, icon, b, y0, b, b, y1, b, a, y1, b, a, y0, b, b - a, y1 - y0); // south
        quad(t, icon, a, y0, b, a, y1, b, a, y1, a, a, y0, a, b - a, y1 - y0); // west
        quad(t, icon, b, y0, a, b, y1, a, b, y1, b, b, y0, b, b - a, y1 - y0); // east
        t.draw();

        GL11.glColor4f(1F, 1F, 1F, 1F);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
    }

    private static void quad(Tessellator t, IIcon icon, float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3, float w, float h) {
        double u0 = icon.getMinU(), u1 = icon.getInterpolatedU(w * 16), v0 = icon.getMinV(), v1 = icon.getInterpolatedV(h * 16);
        t.addVertexWithUV(x0, y0, z0, u0, v1);
        t.addVertexWithUV(x1, y1, z1, u0, v0);
        t.addVertexWithUV(x2, y2, z2, u1, v0);
        t.addVertexWithUV(x3, y3, z3, u1, v1);
    }
}
