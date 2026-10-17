package com.sc.mob.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

/**
 * PACKAGE M (docs/plan-substrate-tech-f3.md §4.4): the Repair Drone (64 x 32, textures/entity/repairDrone.png) - an olive
 * hull 6 x 3 x 6 with a yellow work light underneath, four short arms with spinning rotors, an antenna, the repair nozzle
 * (lens) at the front that the renderer draws full-bright while it repairs. About 0.5 blocks across; it bobs a little.
 */
@SideOnly(Side.CLIENT)
public class ModelRepairDroneSC extends ModelBase {

    final ModelRenderer hull, belly, antenna, nozzle;
    final ModelRenderer[] arms = new ModelRenderer[4], rotors = new ModelRenderer[4];

    public ModelRepairDroneSC() {
        textureWidth = 64;
        textureHeight = 32;
        hull = new ModelRenderer(this, 0, 0);
        hull.addBox(-3F, -3F, -3F, 6, 3, 6);
        hull.setRotationPoint(0F, 18F, 0F);
        belly = new ModelRenderer(this, 0, 9);
        belly.addBox(-2F, 0F, -2F, 4, 1, 4);
        hull.addChild(belly);
        antenna = new ModelRenderer(this, 24, 0);
        antenna.addBox(1F, -6F, 1F, 1, 3, 1);
        hull.addChild(antenna);
        nozzle = new ModelRenderer(this, 28, 0);
        nozzle.addBox(-1F, -2F, -4.2F, 2, 2, 1);
        hull.addChild(nozzle);
        for (int i = 0; i < 4; i++) {
            ModelRenderer arm = new ModelRenderer(this, 0, 14);
            arm.addBox(0F, -0.5F, -0.5F, 4, 1, 1);
            arm.rotateAngleY = (float) (Math.PI / 4D + i * Math.PI / 2D);
            arm.setRotationPoint(0F, -2F, 0F);
            hull.addChild(arm);
            arms[i] = arm;
            ModelRenderer rotor = new ModelRenderer(this, 16, 14);
            rotor.addBox(-2F, -0.5F, -0.5F, 4, 1, 1);
            rotor.setRotationPoint(4F, -1F, 0F);
            arm.addChild(rotor);
            rotors[i] = rotor;
        }
    }

    @Override
    public void render(Entity e, float limbSwing, float limbAmount, float age, float yaw, float pitch, float scale) {
        setRotationAngles(limbSwing, limbAmount, age, yaw, pitch, scale, e);
        hull.render(scale);
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbAmount, float age, float yaw, float pitch, float scale, Entity e) {
        hull.rotationPointY = 18F + MathHelper.sin(age * 0.15F) * 0.6F;
        hull.rotateAngleX = MathHelper.sin(age * 0.07F) * 0.06F;
        for (int i = 0; i < 4; i++) {
            rotors[i].rotateAngleY = age * 1.9F + i;                  // blurred by speed
        }
    }
}
