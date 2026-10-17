package com.sc.mob.client;

import com.sc.mob.EntityContainmentCoilSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

/**
 * PACKAGE B (docs/plan-substrate-tech-f5.md §5.2, §5.8): a Containment Coil (64 x 32, textures/entity/containmentCoil.png, drawn 2 x 2
 * - tools/gen_finale_boss_tex.py), 24 model pixels tall, drawn x RenderContainmentCoilSC.SCALE (about 2 blocks): a dark plated
 * base, a steel post with ceramic insulator rings, three copper coil rings round a glass core (core - lit blue by the renderer
 * while it is LIT), an emitter cap on top (where the beam to the Warden leaves it). LIT - the rings turn slowly, each its own way;
 * DARK - they stand; BROKEN - the top ring has slipped askew, the cap tilted.
 */
@SideOnly(Side.CLIENT)
public class ModelContainmentCoilSC extends ModelBase {

    /** The rings' heights (model pixels, from the top). */
    private static final float[] RING_Y = {10F, 7F, 4F};

    final ModelRenderer base, post, core, cap;
    final ModelRenderer[] rings = new ModelRenderer[RING_Y.length];

    public ModelContainmentCoilSC() {
        textureWidth = 64;
        textureHeight = 32;
        base = new ModelRenderer(this, 0, 0);
        base.addBox(-5F, 22F, -5F, 10, 2, 10);
        post = new ModelRenderer(this, 40, 0);
        post.addBox(-2F, 12F, -2F, 4, 10, 4);
        core = new ModelRenderer(this, 32, 12);
        core.addBox(-1F, 2F, -1F, 2, 10, 2);
        cap = new ModelRenderer(this, 40, 14);
        cap.addBox(-2F, 0F, -2F, 4, 2, 4);
        cap.setRotationPoint(0F, 0F, 0F);
        for (int i = 0; i < rings.length; i++) {
            ModelRenderer r = new ModelRenderer(this, 0, 12);
            r.addBox(-4F, -0.5F, -4F, 8, 1, 8);
            r.setRotationPoint(0F, RING_Y[i], 0F);
            rings[i] = r;
        }
    }

    @Override
    public void render(Entity e, float limbSwing, float limbAmount, float age, float yaw, float pitch, float scale) {
        setRotationAngles(limbSwing, limbAmount, age, yaw, pitch, scale, e);
        base.render(scale);
        post.render(scale);
        core.render(scale);
        cap.render(scale);
        for (ModelRenderer r : rings) {
            r.render(scale);
        }
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbAmount, float age, float yaw, float pitch, float scale, Entity e) {
        int state = e instanceof EntityContainmentCoilSC ? ((EntityContainmentCoilSC) e).coilState() : EntityContainmentCoilSC.LIT;
        boolean lit = state == EntityContainmentCoilSC.LIT;
        for (int i = 0; i < rings.length; i++) {
            rings[i].rotateAngleY = lit ? age * (0.05F + 0.02F * i) * (i % 2 == 0 ? 1F : -1F) : i * 0.4F;
            rings[i].rotateAngleX = rings[i].rotateAngleZ = 0F;
            rings[i].rotationPointY = RING_Y[i] + (lit ? MathHelper.sin(age * 0.1F + i * 2F) * 0.25F : 0F);
        }
        cap.rotateAngleX = cap.rotateAngleZ = 0F;
        if (state == EntityContainmentCoilSC.BROKEN) {
            rings[2].rotateAngleX = 0.45F;                      // the top ring slipped askew
            rings[2].rotationPointY = 5.5F;
            rings[1].rotateAngleZ = -0.2F;
            cap.rotateAngleZ = 0.35F;
        }
    }
}
