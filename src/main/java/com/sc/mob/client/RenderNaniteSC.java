package com.sc.mob.client;

import org.lwjgl.opengl.GL11;

import com.sc.Reference;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;

/**
 * PACKAGE X (docs/plan-substrate-tech-f3.md §8.7): a nanite particle (EntityNaniteSC) - a glowing point with a tail. A camera-facing sprite of
 * textures/entity/nanite.png (a soft green-white dot), drawn additive and full-bright (it is light, not a body), flickering at
 * its own pace; the tail - TAIL smaller, fainter sprites back along the way it came (its last tick's move). No shadow, no
 * model, nothing else: a swarm of twelve costs twelve small quads and their tails.
 */
@SideOnly(Side.CLIENT)
public class RenderNaniteSC extends Render {

    private static final ResourceLocation TEX = new ResourceLocation(Reference.ASSETS, "textures/entity/nanite.png");
    /** The sprite's half size (blocks), the tail's sprites, how far back they reach (of the last move). */
    private static final float SIZE = 0.22F;
    private static final int TAIL = 4;
    private static final double TAIL_STEP = 1.2D;

    public RenderNaniteSC() {
        shadowSize = 0F;
    }

    @Override
    public void doRender(Entity e, double x, double y, double z, float yaw, float pt) {
        double mx = e.posX - e.prevPosX, my = e.posY - e.prevPosY, mz = e.posZ - e.prevPosZ;
        float flicker = 0.75F + 0.25F * MathHelper.sin((e.ticksExisted + pt) * 0.9F + e.getEntityId());
        float lastX = OpenGlHelper.lastBrightnessX, lastY = OpenGlHelper.lastBrightnessY;
        bindEntityTexture(e);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x, y + e.height * 0.5D, z);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);   // additive: light
            GL11.glDepthMask(false);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
            for (int k = TAIL; k >= 0; k--) {
                GL11.glPushMatrix();
                GL11.glTranslated(-mx * k * TAIL_STEP, -my * k * TAIL_STEP, -mz * k * TAIL_STEP);
                sprite(SIZE * (1F - k * 0.17F), flicker * (1F - k * 0.21F));
                GL11.glPopMatrix();
            }
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    /** One camera-facing quad `s` from its middle, `alpha` bright (as vanilla's snowball faces the camera). */
    private void sprite(float s, float alpha) {
        GL11.glPushMatrix();
        GL11.glRotatef(180F - renderManager.playerViewY, 0F, 1F, 0F);
        GL11.glRotatef(-renderManager.playerViewX, 1F, 0F, 0F);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA_F(1F, 1F, 1F, alpha);
        t.addVertexWithUV(-s, -s, 0D, 0D, 1D);
        t.addVertexWithUV(s, -s, 0D, 1D, 1D);
        t.addVertexWithUV(s, s, 0D, 1D, 0D);
        t.addVertexWithUV(-s, s, 0D, 0D, 0D);
        t.draw();
        GL11.glPopMatrix();
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity e) {
        return TEX;
    }
}
