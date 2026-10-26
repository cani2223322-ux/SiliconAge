package com.sc.settle.client;

import org.lwjgl.opengl.GL11;

import com.sc.Reference;
import com.sc.settle.npc.EntityGathererSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.ResourceLocation;

/**
 * ПД-5 (docs/plan-ideas-2026-10-26.md, «Ожившие поселения», ★ A): the Gatherer - a Tuner in the Gas Technician's skin (RenderTunerSC by
 * its kind) with a sack on its back (textures/entity/gatherer_sack.png 32 x 32: a canvas sack with a strap and a crystal sticking out),
 * fuller while it gathers or carries home. Client only (AmbientLifeClientSC registers it).
 */
@SideOnly(Side.CLIENT)
public class RenderGathererSC extends RenderTunerSC {

    public static final ResourceLocation SACK = new ResourceLocation(Reference.ASSETS, "textures/entity/gatherer_sack.png");

    private final SackModel sack = new SackModel();

    @Override
    protected void renderEquippedItems(EntityLiving e, float partial) {
        super.renderEquippedItems(e, partial);
        if (!(e instanceof EntityGathererSC) || e.deathTime > 0 || !(mainModel instanceof ModelBiped)) {
            return;
        }
        int st = ((EntityGathererSC) e).gatherState();
        GL11.glPushMatrix();
        ((ModelBiped) mainModel).bipedBody.postRender(0.0625F);
        bindTexture(SACK);
        float full = st == EntityGathererSC.OUT ? 0.85F : 1.1F;
        GL11.glTranslatef(0F, 0.25F, 0.2F);
        GL11.glScalef(full, full, full);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        sack.body.render(0.0625F);
        GL11.glPopMatrix();
    }

    /** The sack's box: 6 x 7 x 3 at the back, its strap's cut on the texture. */
    static final class SackModel extends ModelBase {
        final ModelRenderer body;

        SackModel() {
            textureWidth = 32;
            textureHeight = 32;
            body = new ModelRenderer(this, 0, 0);
            body.addBox(-3F, -3F, 0F, 6, 7, 3);
        }
    }
}
