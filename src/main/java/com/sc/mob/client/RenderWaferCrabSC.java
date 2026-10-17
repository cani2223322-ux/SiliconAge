package com.sc.mob.client;

import java.awt.Color;

import org.lwjgl.opengl.GL11;

import com.sc.Reference;
import com.sc.mob.EntityWaferCrabSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

/**
 * PACKAGE N (docs/plan-substrate-tech-f4.md §7.10, §7.11): the Wafer Crab - ModelWaferCrabSC, its wafer drawn after the body with
 * the texture under a slowly shifting rainbow tint (each crab at its own hue: the «переливчатая пластина»), lit as the world is.
 */
@SideOnly(Side.CLIENT)
public class RenderWaferCrabSC extends RenderLiving {

    /** The tint's saturation; hue turns a tick. */
    private static final float SATURATION = 0.4F, HUE_SPEED = 0.006F;
    private static final ResourceLocation SKIN = new ResourceLocation(Reference.ASSETS, "textures/entity/" + EntityWaferCrabSC.NAME + ".png");

    private final ModelWaferCrabSC model;

    public RenderWaferCrabSC() {
        this(new ModelWaferCrabSC());
    }

    private RenderWaferCrabSC(ModelWaferCrabSC model) {
        super(model, 0.35F);
        this.model = model;
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity e) {
        return SKIN;
    }

    /** The wafer (after the body, in its space), tinted. */
    @Override
    protected void renderEquippedItems(EntityLivingBase e, float pt) {
        super.renderEquippedItems(e, pt);
        float hue = ((e.ticksExisted + pt) * HUE_SPEED + e.getEntityId() * 0.137F) % 1F;
        int rgb = Color.HSBtoRGB(hue, SATURATION, 1F);
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_CURRENT_BIT);
        model.body.postRender(0.0625F);
        bindTexture(SKIN);
        GL11.glColor4f(NoiseClientSC.r(rgb), NoiseClientSC.g(rgb), NoiseClientSC.b(rgb), 1F);
        model.wafer.render(0.0625F);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }
}
