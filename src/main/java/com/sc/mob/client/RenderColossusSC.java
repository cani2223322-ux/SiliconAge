package com.sc.mob.client;

import org.lwjgl.opengl.GL11;

import com.sc.Reference;
import com.sc.mob.EntityColossusSC;
import com.sc.mob.IMobActionSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

/**
 * PACKAGE N (docs/plan-substrate-tech-f4.md §7.7, §7.11, rule ПС-9): the Quartzite Colossus - ModelColossusSC drawn x SCALE, its eye
 * facets full-bright cyan (brighter, pulsing, while it winds up a stomp or aims a crystal), the crystal in its fist full-bright
 * white-cyan while it aims (ACT_THROW) - every client sees the throw coming.
 */
@SideOnly(Side.CLIENT)
public class RenderColossusSC extends RenderLiving {

    public static final float SCALE = 1.5F;
    /** The eyes at rest, winding up; the crystal. */
    public static final int EYES = 0x5FD8E8, EYES_ANGRY = 0xB0FFFF, CRYSTAL = 0xE8FBFF;
    private static final ResourceLocation SKIN = new ResourceLocation(Reference.ASSETS, "textures/entity/" + EntityColossusSC.NAME + ".png");

    private final ModelColossusSC model;

    public RenderColossusSC() {
        this(new ModelColossusSC());
    }

    private RenderColossusSC(ModelColossusSC model) {
        super(model, 1.2F);
        this.model = model;
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity e) {
        return SKIN;
    }

    @Override
    protected void preRenderCallback(EntityLivingBase e, float pt) {
        super.preRenderCallback(e, pt);
        GL11.glScalef(SCALE, SCALE, SCALE);
    }

    /** The eyes and the aimed crystal (after the model, in its space). */
    @Override
    protected void renderEquippedItems(EntityLivingBase e, float pt) {
        super.renderEquippedItems(e, pt);
        if (e.deathTime > 0) {
            return;
        }
        int act = ((IMobActionSC) e).mobAction();
        boolean windUp = act == IMobActionSC.ACT_STOMP || act == IMobActionSC.ACT_THROW;
        float pulse = 0.75F + 0.25F * (float) Math.sin((e.ticksExisted + pt) * 0.8F);
        GL11.glPushMatrix();
        model.body.postRender(0.0625F);
        model.head.postRender(0.0625F);
        NoiseClientSC.lamp(model.eyes, windUp ? EYES_ANGRY : EYES, windUp ? pulse : 0.85F);
        GL11.glPopMatrix();
        if (model.crystal.showModel) {
            GL11.glPushMatrix();
            model.armR.postRender(0.0625F);
            NoiseClientSC.lamp(model.crystal, CRYSTAL, pulse);
            GL11.glPopMatrix();
        }
    }
}
