package com.sc.mob.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

/**
 * PACKAGE M (docs/plan-substrate-tech-f4.md §7.2): the camp's Warlord's biped (64 x 32, textures/entity/warlord.png) with what
 * marks the leader - a stiff crimson crest along the top of the helmet (above the worn Quantum helmet, which inflates the head
 * by 1: its top is at -9) on the hat layer's UV (32, 0), and a tattered crimson cape off its shoulders behind the Exo chestplate
 * (which inflates the body by 1: the cape hangs from z 3.2) on (46, 0), swinging back as it walks. The vanilla hat layer is off.
 */
@SideOnly(Side.CLIENT)
public class ModelWarlordSC extends ModelBiped {

    final ModelRenderer crest, cape;

    public ModelWarlordSC() {
        super(0F, 0F, 64, 32);
        bipedHeadwear.showModel = false;
        crest = new ModelRenderer(this, 32, 0);
        crest.setTextureSize(64, 32);
        crest.addBox(-0.5F, -12F, -3F, 1, 3, 6);
        bipedHead.addChild(crest);
        cape = new ModelRenderer(this, 46, 0);
        cape.setTextureSize(64, 32);
        cape.addBox(-4F, 0F, 0F, 8, 12, 1);
        cape.setRotationPoint(0F, 0.5F, 3.2F);
        bipedBody.addChild(cape);
    }

    @Override
    public void setRotationAngles(float swing, float amount, float age, float yaw, float pitch, float scale, Entity e) {
        super.setRotationAngles(swing, amount, age, yaw, pitch, scale, e);
        cape.rotateAngleX = 0.06F + Math.abs(MathHelper.cos(swing * 0.6662F)) * 0.6F * amount + 0.02F * MathHelper.sin(age * 0.08F);
    }
}
