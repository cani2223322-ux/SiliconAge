package com.sc.mob.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;

/**
 * PACKAGE M (docs/plan-substrate-tech-f3.md §4.3): the Jammer's biped (64 x 32, textures/entity/jammer.png) with its antenna
 * «horns» - two thin masts from the temples up through the helmet (long enough to stand above the worn Quantum helmet,
 * which inflates the head by 1), slightly splayed, on the free UV corner (56, 16); their tips are drawn full-bright by the
 * renderer (tipLeft / tipRight, children of nothing - rendered after bipedHead.postRender). The vanilla hat layer is off
 * (the armour's helmet covers it).
 */
@SideOnly(Side.CLIENT)
public class ModelJammerSC extends ModelBiped {

    final ModelRenderer hornLeft, hornRight, tipLeft, tipRight;

    public ModelJammerSC() {
        super(0F, 0F, 64, 32);
        bipedHeadwear.showModel = false;
        hornRight = horn(-3.5F, -0.18F);
        hornLeft = horn(3.5F, 0.18F);
        hornLeft.mirror = true;
        tipRight = tip(-3.5F, -0.18F);
        tipLeft = tip(3.5F, 0.18F);
    }

    private ModelRenderer horn(float x, float splay) {
        ModelRenderer r = new ModelRenderer(this, 56, 16);
        r.setTextureSize(64, 32);
        r.addBox(-0.5F, -7F, -0.5F, 1, 7, 1);
        r.setRotationPoint(x, -7F, 1F);
        r.rotateAngleZ = splay;
        bipedHead.addChild(r);
        return r;
    }

    private ModelRenderer tip(float x, float splay) {
        ModelRenderer r = new ModelRenderer(this, 56, 24);
        r.setTextureSize(64, 32);
        r.addBox(-1F, -8.5F, -1F, 2, 2, 2);
        r.setRotationPoint(x, -7F, 1F);
        r.rotateAngleZ = splay;
        return r;
    }
}
