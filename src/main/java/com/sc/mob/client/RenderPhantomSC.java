package com.sc.mob.client;

import org.lwjgl.opengl.GL11;

import com.sc.Reference;
import com.sc.mob.EntityPhantomSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;

/**
 * PACKAGE N (docs/plan-substrate-tech-f4.md §7.5, §7.11, rule ПС-35): the Induction Phantom - ModelPhantomSC, see-through and
 * full-bright, only to a player who sees it (NoiseClientSC.seesPhantoms: the helmet's night vision, the Singular helmet's Ш1 /
 * Ш2): pale blue-white at SEEN_ALPHA, flickering, drawn over its own back faces (no depth writes - it is light in the wires, not
 * a body); to anyone else a faint additive shimmer at SHIMMER_ALPHA that comes and goes - a heat haze where the air hums. No
 * shadow, no name tag, no vanilla living render (its isInvisible() is always true: vanilla would draw nothing); its own matrix
 * as RendererLivingEntity's (the body's yaw, the model 24 px over its feet). Dying it fades out over its death ticks.
 */
@SideOnly(Side.CLIENT)
public class RenderPhantomSC extends Render {

    /** Its colour; how strong to one who sees it, the shimmer to anyone else. */
    public static final int COLOUR = 0xBFE8FF;
    public static final float SEEN_ALPHA = 0.7F, SHIMMER_ALPHA = 0.07F;
    private static final ResourceLocation SKIN = new ResourceLocation(Reference.ASSETS, "textures/entity/" + EntityPhantomSC.NAME + ".png");

    private final ModelPhantomSC model = new ModelPhantomSC();

    public RenderPhantomSC() {
        shadowSize = 0F;
    }

    @Override
    public void doRender(Entity e, double x, double y, double z, float yaw, float pt) {
        EntityLivingBase p = (EntityLivingBase) e;
        float age = p.ticksExisted + pt;
        boolean seen = NoiseClientSC.seesPhantoms();
        float flicker = 0.8F + 0.2F * MathHelper.sin(age * 0.7F + p.getEntityId());
        float alpha;
        if (seen) {
            alpha = SEEN_ALPHA * flicker;
        } else {
            float wave = MathHelper.sin(age * 0.05F + p.getEntityId() * 0.37F);   // the shimmer comes and goes
            alpha = SHIMMER_ALPHA * Math.max(0F, wave) * flicker;
        }
        if (p.deathTime > 0) {
            alpha *= Math.max(0F, 1F - p.deathTime / 20F);
        }
        if (alpha <= 0.004F) {
            return;
        }
        float body = p.prevRenderYawOffset + MathHelper.wrapAngleTo180_float(p.renderYawOffset - p.prevRenderYawOffset) * pt;
        bindEntityTexture(e);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_CURRENT_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x, y, z);
            GL11.glRotatef(180F - body, 0F, 1F, 0F);
            GL11.glScalef(-1F, -1F, 1F);
            GL11.glTranslatef(0F, -1.5F, 0F);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            if (seen) {
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            } else {
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);     // additive: a haze, not a figure
            }
            GL11.glDepthMask(false);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.003921569F);
            NoiseClientSC.brightOn();
            GL11.glColor4f(NoiseClientSC.r(COLOUR), NoiseClientSC.g(COLOUR), NoiseClientSC.b(COLOUR), alpha);
            model.render(e, 0F, 0F, age, 0F, 0F, 0.0625F);
            NoiseClientSC.brightOff();
        } finally {
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
            GL11.glColor4f(1F, 1F, 1F, 1F);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity e) {
        return SKIN;
    }
}
