package com.sc.mob.client;

import org.lwjgl.opengl.GL11;

import com.sc.Reference;
import com.sc.mob.EntityScrapperSC;
import com.sc.mob.IMobActionSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

/**
 * PACKAGE N (docs/plan-substrate-tech-f4.md §7.9, §7.11, rule ПС-9): the Scrapper Crab - ModelScrapperSC (the junk in its hopper by
 * its DataWatcher's bag count), its eye lamps full-bright amber - flickering out while a Point has it stunned (ACT_STUNNED).
 */
@SideOnly(Side.CLIENT)
public class RenderScrapperSC extends RenderLiving {

    /** The eye lamps; stunned. */
    public static final int EYES = 0xFFB040, EYES_STUNNED = 0x402810;
    private static final ResourceLocation SKIN = new ResourceLocation(Reference.ASSETS, "textures/entity/" + EntityScrapperSC.NAME + ".png");

    private final ModelScrapperSC model;

    public RenderScrapperSC() {
        this(new ModelScrapperSC());
    }

    private RenderScrapperSC(ModelScrapperSC model) {
        super(model, 0.8F);
        this.model = model;
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity e) {
        return SKIN;
    }

    /** The eyes (after the model, in the shell's space). */
    @Override
    protected void renderEquippedItems(EntityLivingBase e, float pt) {
        super.renderEquippedItems(e, pt);
        if (e.deathTime > 0) {
            return;
        }
        boolean stunned = ((IMobActionSC) e).mobAction() == IMobActionSC.ACT_STUNNED;
        float t = e.ticksExisted + pt;
        boolean flick = stunned && Math.sin(t * 2.3F) > 0.6D;
        GL11.glPushMatrix();
        model.body.postRender(0.0625F);
        NoiseClientSC.lamp(model.eyes, stunned && !flick ? EYES_STUNNED : EYES, stunned ? 0.7F : 0.9F + 0.1F * (float) Math.sin(t * 0.3F));
        GL11.glPopMatrix();
    }
}
