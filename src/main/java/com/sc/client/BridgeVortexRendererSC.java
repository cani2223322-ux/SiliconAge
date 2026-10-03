package com.sc.client;

import org.lwjgl.opengl.GL11;

import com.sc.init.ModBlocks;
import com.sc.tileentity.TileEntityBridgeVortexSC;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Facing;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

/**
 * С3 / §11: a vortex whose stability fell under 30% shakes. Its cells stop drawing as blocks
 * (BlockBridgeVortexSC.shouldSideBeRendered) and are drawn here, each frame a little off its place - every cell of
 * every shaking vortex by the same offset, so the whole swirl jitters as one; full bright, see-through.
 */
public class BridgeVortexRendererSC extends TileEntitySpecialRenderer {

    private static final float JITTER = 0.09F;

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partial) {
        if (!(te instanceof TileEntityBridgeVortexSC) || !((TileEntityBridgeVortexSC) te).isUnstable()) {
            return;
        }
        World w = te.getWorldObj();
        Block b = ModBlocks.bridgeVortex;
        long t = System.currentTimeMillis() / 45;
        java.util.Random r = new java.util.Random(t * 341873128712L);
        float ox = (r.nextFloat() - 0.5F) * 2F * JITTER, oy = (r.nextFloat() - 0.5F) * 2F * JITTER, oz = (r.nextFloat() - 0.5F) * 2F * JITTER;
        bindTexture(TextureMap.locationBlocksTexture);
        GL11.glPushMatrix();
        GL11.glTranslated(x + ox, y + oy, z + oz);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
        Tessellator tes = Tessellator.instance;
        tes.startDrawingQuads();
        tes.setColorRGBA_F(1F, 1F, 1F, 0.92F);
        for (int side = 0; side < 6; side++) {
            int nx = te.xCoord + Facing.offsetsXForSide[side], ny = te.yCoord + Facing.offsetsYForSide[side], nz = te.zCoord + Facing.offsetsZForSide[side];
            if (w.getBlock(nx, ny, nz) == b) {
                continue;
            }
            IIcon ic = b.getIcon(w, te.xCoord, te.yCoord, te.zCoord, side);
            if (ic != null) {
                face(tes, side, ic);
            }
        }
        tes.draw();
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
    }

    private static void face(Tessellator t, int side, IIcon ic) {
        double u0 = ic.getMinU(), u1 = ic.getMaxU(), v0 = ic.getMinV(), v1 = ic.getMaxV();
        switch (side) {
            case 0:
                t.addVertexWithUV(0, 0, 0, u0, v0);
                t.addVertexWithUV(1, 0, 0, u1, v0);
                t.addVertexWithUV(1, 0, 1, u1, v1);
                t.addVertexWithUV(0, 0, 1, u0, v1);
                break;
            case 1:
                t.addVertexWithUV(0, 1, 1, u0, v1);
                t.addVertexWithUV(1, 1, 1, u1, v1);
                t.addVertexWithUV(1, 1, 0, u1, v0);
                t.addVertexWithUV(0, 1, 0, u0, v0);
                break;
            case 2:
                t.addVertexWithUV(1, 1, 0, u0, v0);
                t.addVertexWithUV(1, 0, 0, u0, v1);
                t.addVertexWithUV(0, 0, 0, u1, v1);
                t.addVertexWithUV(0, 1, 0, u1, v0);
                break;
            case 3:
                t.addVertexWithUV(0, 1, 1, u0, v0);
                t.addVertexWithUV(0, 0, 1, u0, v1);
                t.addVertexWithUV(1, 0, 1, u1, v1);
                t.addVertexWithUV(1, 1, 1, u1, v0);
                break;
            case 4:
                t.addVertexWithUV(0, 1, 0, u0, v0);
                t.addVertexWithUV(0, 0, 0, u0, v1);
                t.addVertexWithUV(0, 0, 1, u1, v1);
                t.addVertexWithUV(0, 1, 1, u1, v0);
                break;
            default:
                t.addVertexWithUV(1, 1, 1, u0, v0);
                t.addVertexWithUV(1, 0, 1, u0, v1);
                t.addVertexWithUV(1, 0, 0, u1, v1);
                t.addVertexWithUV(1, 1, 0, u1, v0);
                break;
        }
    }
}
