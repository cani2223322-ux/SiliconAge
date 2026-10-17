package com.sc.mob.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

/**
 * PACKAGE N (docs/plan-substrate-tech-f4.md §7.10, §7.11): the Wafer Crab (64 x 32, textures/entity/waferCrab.png) - a small grey
 * crab, two eye stalks, two little claws, six legs, and on its back a square silicon wafer with its etched dies (wafer - the
 * renderer draws it on its own with a shifting iridescent tint). Walking - the legs scuttle, the wafer rocks a little.
 */
@SideOnly(Side.CLIENT)
public class ModelWaferCrabSC extends ModelBase {

    private static final float BODY_Y = 20.5F;
    private static final float[] LEG_Z = {-2F, 0.5F, 3F};

    final ModelRenderer body, wafer, clawL, clawR;
    final ModelRenderer[] legs = new ModelRenderer[6];

    public ModelWaferCrabSC() {
        textureWidth = 64;
        textureHeight = 32;
        body = new ModelRenderer(this, 0, 0);
        body.addBox(-4F, -2F, -3F, 8, 3, 7);
        body.setRotationPoint(0F, BODY_Y, 0F);
        wafer = new ModelRenderer(this, 0, 10);
        wafer.addBox(-5F, -1F, -5F, 10, 1, 10);
        wafer.setRotationPoint(0F, -2F, 0.5F);
        body.addChild(wafer);
        ModelRenderer eyes = new ModelRenderer(this, 50, 0);
        eyes.addBox(-2F, -4F, -3F, 1, 2, 1);
        eyes.addBox(1F, -4F, -3F, 1, 2, 1);
        body.addChild(eyes);
        clawR = new ModelRenderer(this, 40, 0);
        clawR.addBox(-1F, -1F, -3F, 2, 2, 3);
        clawR.setRotationPoint(-3F, 0F, -3F);
        body.addChild(clawR);
        clawL = new ModelRenderer(this, 40, 0);
        clawL.mirror = true;
        clawL.addBox(-1F, -1F, -3F, 2, 2, 3);
        clawL.setRotationPoint(3F, 0F, -3F);
        body.addChild(clawL);
        for (int i = 0; i < 6; i++) {
            boolean right = i < 3;
            ModelRenderer leg = new ModelRenderer(this, 30, 0);
            leg.mirror = !right;
            leg.addBox(right ? -4F : 0F, -0.5F, -0.5F, 4, 1, 1);
            leg.setRotationPoint(right ? -4F : 4F, 0.5F, LEG_Z[i % 3]);
            body.addChild(leg);
            legs[i] = leg;
        }
    }

    @Override
    public void render(Entity e, float limbSwing, float limbAmount, float age, float yaw, float pitch, float scale) {
        setRotationAngles(limbSwing, limbAmount, age, yaw, pitch, scale, e);
        wafer.showModel = false;                                // drawn by the renderer, tinted
        body.render(scale);
        wafer.showModel = true;
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbAmount, float age, float yaw, float pitch, float scale, Entity e) {
        body.rotationPointY = BODY_Y + MathHelper.sin(limbSwing * 1.2F) * 0.3F * limbAmount;
        wafer.rotateAngleX = MathHelper.sin(limbSwing * 1.2F) * 0.06F * limbAmount;
        wafer.rotateAngleZ = MathHelper.cos(age * 0.07F) * 0.03F;
        for (int i = 0; i < 6; i++) {
            boolean right = i < 3;
            float side = right ? -1F : 1F;                      // round Z: a right leg goes down by a negative turn
            float phase = limbSwing * 1.5F + (i % 2 == 0 ? 0F : (float) Math.PI);
            legs[i].rotateAngleZ = side * (0.55F + Math.max(0F, MathHelper.sin(phase)) * 0.4F * limbAmount);
            legs[i].rotateAngleY = (i % 3 - 1) * 0.5F * -side + MathHelper.cos(phase) * 0.4F * limbAmount;
        }
        float nip = MathHelper.sin(age * 0.2F) * 0.1F;
        clawR.rotateAngleY = 0.3F + nip;
        clawL.rotateAngleY = -0.3F - nip;
    }
}
