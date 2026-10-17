package com.sc.mob.client;

import com.sc.mob.IMobActionSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

/**
 * PACKAGE N (docs/plan-substrate-tech-f4.md §7.7, §7.11): the Quartzite Colossus (128 x 64, textures/entity/colossus.png) - a
 * hunched giant of quartzite blocks: two pillar legs, a broad torso, a small head sunk between the shoulders with two cyan eye
 * facets (eyes, lit by the renderer), long arms ending in heavy fists, crystal spikes growing from the shoulders and the back.
 * Built at 2/3 scale (RenderColossusSC draws it x1.5: about 3.4 blocks). Its poses by the telegraph (IMobActionSC): the stomp
 * (ACT_STOMP) - both arms raised over its head, leaning back; the throw (ACT_THROW) - the right arm drawn back over the
 * shoulder, a crystal (crystal, lit by the renderer) in its fist. Walking - the legs swing, the arms a little against them.
 */
@SideOnly(Side.CLIENT)
public class ModelColossusSC extends ModelBase {

    final ModelRenderer body, head, eyes, armL, armR, fistL, fistR, crystal, legL, legR, spikeL, spikeR, spikeBack;

    public ModelColossusSC() {
        textureWidth = 128;
        textureHeight = 64;
        body = new ModelRenderer(this, 0, 0);
        body.addBox(-8F, 0F, -5F, 16, 14, 10);
        body.setRotationPoint(0F, -2F, 0F);
        head = new ModelRenderer(this, 52, 0);
        head.addBox(-4F, -6F, -4F, 8, 6, 7);
        head.setRotationPoint(0F, 1F, -3F);
        body.addChild(head);
        eyes = new ModelRenderer(this, 122, 0);
        eyes.addBox(-3F, -4F, -4.3F, 2, 1, 1);
        eyes.addBox(1F, -4F, -4.3F, 2, 1, 1);
        head.addChild(eyes);
        spikeL = spike(82, 0, 4, 8, 6F, 0F, 1F, 0.35F);
        spikeR = spike(82, 0, 4, 8, -6F, 0F, 1F, -0.35F);
        spikeBack = new ModelRenderer(this, 98, 0);
        spikeBack.addBox(-1.5F, -10F, -1.5F, 3, 10, 3);
        spikeBack.setRotationPoint(0F, 1F, 4F);
        spikeBack.rotateAngleX = -0.45F;
        body.addChild(spikeL);
        body.addChild(spikeR);
        body.addChild(spikeBack);
        armR = new ModelRenderer(this, 24, 24);
        armR.addBox(-3F, -2F, -3F, 6, 18, 6);
        armR.setRotationPoint(-11F, 0F, 0F);
        fistR = new ModelRenderer(this, 0, 48);
        fistR.addBox(-3.5F, 16F, -3.5F, 7, 5, 7);
        armR.addChild(fistR);
        crystal = new ModelRenderer(this, 110, 0);
        crystal.addBox(-1.5F, 19F, -1.5F, 3, 5, 3);
        armR.addChild(crystal);
        armL = new ModelRenderer(this, 24, 24);
        armL.mirror = true;
        armL.addBox(-3F, -2F, -3F, 6, 18, 6);
        armL.setRotationPoint(11F, 0F, 0F);
        fistL = new ModelRenderer(this, 0, 48);
        fistL.mirror = true;
        fistL.addBox(-3.5F, 16F, -3.5F, 7, 5, 7);
        armL.addChild(fistL);
        legR = new ModelRenderer(this, 0, 24);
        legR.addBox(-3F, 0F, -3F, 6, 12, 6);
        legR.setRotationPoint(-4.5F, 12F, 0F);
        legL = new ModelRenderer(this, 0, 24);
        legL.mirror = true;
        legL.addBox(-3F, 0F, -3F, 6, 12, 6);
        legL.setRotationPoint(4.5F, 12F, 0F);
    }

    /** A crystal spike growing from a shoulder (tilted outwards by `tilt` round Z). */
    private ModelRenderer spike(int u, int v, int w, int h, float x, float y, float z, float tilt) {
        ModelRenderer s = new ModelRenderer(this, u, v);
        s.addBox(-w / 2F, -h, -w / 2F, w, h, w);
        s.setRotationPoint(x, y, z);
        s.rotateAngleZ = tilt;
        s.rotateAngleX = -0.15F;
        return s;
    }

    @Override
    public void render(Entity e, float limbSwing, float limbAmount, float age, float yaw, float pitch, float scale) {
        setRotationAngles(limbSwing, limbAmount, age, yaw, pitch, scale, e);
        body.render(scale);
        armL.render(scale);
        armR.render(scale);
        legL.render(scale);
        legR.render(scale);
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbAmount, float age, float yaw, float pitch, float scale, Entity e) {
        int act = e instanceof IMobActionSC ? ((IMobActionSC) e).mobAction() : IMobActionSC.ACT_NONE;
        float swing = MathHelper.cos(limbSwing * 0.45F) * 0.7F * limbAmount;
        legR.rotateAngleX = swing;
        legL.rotateAngleX = -swing;
        head.rotateAngleY = yaw / (180F / (float) Math.PI) * 0.5F;
        head.rotateAngleX = pitch / (180F / (float) Math.PI) * 0.5F;
        float breathe = MathHelper.sin(age * 0.05F) * 0.03F;
        body.rotateAngleX = 0.18F + breathe;                    // hunched
        armR.rotateAngleX = -swing * 0.5F + breathe;
        armL.rotateAngleX = swing * 0.5F + breathe;
        armR.rotateAngleZ = 0.08F;
        armL.rotateAngleZ = -0.08F;
        crystal.showModel = false;
        if (act == IMobActionSC.ACT_STOMP) {
            float shake = MathHelper.sin(age * 1.3F) * 0.05F;
            armR.rotateAngleX = armL.rotateAngleX = -2.9F + shake; // both fists over its head
            armR.rotateAngleZ = -0.25F;
            armL.rotateAngleZ = 0.25F;
            body.rotateAngleX = -0.1F;
        } else if (act == IMobActionSC.ACT_THROW) {
            armR.rotateAngleX = -2.6F;                          // drawn back over the shoulder
            armR.rotateAngleZ = 0.2F;
            armL.rotateAngleX = -0.6F;                          // the other one aims
            crystal.showModel = true;
        }
    }
}
