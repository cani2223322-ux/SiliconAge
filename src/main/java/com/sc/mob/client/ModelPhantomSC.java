package com.sc.mob.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

/**
 * PACKAGE N (docs/plan-substrate-tech-f4.md §7.5, §7.11): the Induction Phantom (64 x 64, textures/entity/phantom.png) - a hooded
 * head with two eye slits, a narrow torso, two wisp arms and a tail of three tapering segments instead of legs, and two square
 * induction coils turning round the torso the opposite ways. About 1.6 blocks; it floats (the whole figure bobs), the tail and the
 * arms sway, the coils turn. RenderPhantomSC draws it see-through, full-bright, only to those who see it.
 */
@SideOnly(Side.CLIENT)
public class ModelPhantomSC extends ModelBase {

    final ModelRenderer body, head, armL, armR, tail1, tail2, tail3, coilA, coilB;

    public ModelPhantomSC() {
        textureWidth = 64;
        textureHeight = 64;
        body = new ModelRenderer(this, 24, 0);
        body.addBox(-4F, 0F, -2F, 8, 9, 4);
        body.setRotationPoint(0F, 3F, 0F);
        head = new ModelRenderer(this, 0, 0);
        head.addBox(-3F, -6F, -3F, 6, 6, 6);
        body.addChild(head);
        armR = new ModelRenderer(this, 48, 0);
        armR.addBox(-1F, 0F, -1F, 2, 9, 2);
        armR.setRotationPoint(-5F, 1F, 0F);
        body.addChild(armR);
        armL = new ModelRenderer(this, 48, 0);
        armL.mirror = true;
        armL.addBox(-1F, 0F, -1F, 2, 9, 2);
        armL.setRotationPoint(5F, 1F, 0F);
        body.addChild(armL);
        tail1 = new ModelRenderer(this, 0, 13);
        tail1.addBox(-3F, 0F, -1.5F, 6, 4, 3);
        tail1.setRotationPoint(0F, 9F, 0F);
        body.addChild(tail1);
        tail2 = new ModelRenderer(this, 18, 13);
        tail2.addBox(-2F, 0F, -1F, 4, 4, 2);
        tail2.setRotationPoint(0F, 4F, 0F);
        tail1.addChild(tail2);
        tail3 = new ModelRenderer(this, 30, 13);
        tail3.addBox(-1F, 0F, -0.5F, 2, 4, 1);
        tail3.setRotationPoint(0F, 4F, 0F);
        tail2.addChild(tail3);
        coilA = coil(4F);
        coilB = coil(7.5F);
        body.addChild(coilA);
        body.addChild(coilB);
    }

    /** A square coil of four thin bars round the torso, `y` down from its top. */
    private ModelRenderer coil(float y) {
        ModelRenderer c = new ModelRenderer(this, 0, 24);
        c.setRotationPoint(0F, y, 0F);
        c.setTextureOffset(0, 24).addBox(-5F, -0.5F, -5F, 10, 1, 1);
        c.setTextureOffset(0, 24).addBox(-5F, -0.5F, 4F, 10, 1, 1);
        c.setTextureOffset(0, 28).addBox(-5F, -0.5F, -4F, 1, 1, 8);
        c.setTextureOffset(0, 28).addBox(4F, -0.5F, -4F, 1, 1, 8);
        return c;
    }

    @Override
    public void render(Entity e, float limbSwing, float limbAmount, float age, float yaw, float pitch, float scale) {
        setRotationAngles(limbSwing, limbAmount, age, yaw, pitch, scale, e);
        body.render(scale);
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbAmount, float age, float yaw, float pitch, float scale, Entity e) {
        body.rotationPointY = 3F + MathHelper.sin(age * 0.08F) * 1.2F;
        body.rotateAngleX = 0.12F + MathHelper.sin(age * 0.05F) * 0.05F;   // leaning forward a little, as if pulled
        head.rotateAngleX = -0.12F;
        armR.rotateAngleX = -0.35F + MathHelper.sin(age * 0.11F) * 0.25F;
        armL.rotateAngleX = -0.35F + MathHelper.sin(age * 0.11F + 1.6F) * 0.25F;
        armR.rotateAngleZ = 0.25F + MathHelper.sin(age * 0.07F) * 0.1F;
        armL.rotateAngleZ = -0.25F - MathHelper.sin(age * 0.07F) * 0.1F;
        tail1.rotateAngleX = 0.2F + MathHelper.sin(age * 0.13F) * 0.2F;
        tail2.rotateAngleX = 0.15F + MathHelper.sin(age * 0.13F - 0.8F) * 0.25F;
        tail3.rotateAngleX = 0.1F + MathHelper.sin(age * 0.13F - 1.6F) * 0.3F;
        tail1.rotateAngleZ = MathHelper.sin(age * 0.09F) * 0.15F;
        coilA.rotateAngleY = age * 0.12F;
        coilB.rotateAngleY = -age * 0.09F;
    }
}
