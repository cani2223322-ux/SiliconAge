package com.sc.mob.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

/**
 * PACKAGE N (docs/plan-substrate-tech-f4.md §7.8, §7.11): the Arc Strider (64 x 32, textures/entity/strider.png) - a transformer on
 * a tripod: a flat hub with a core on top and two electrode prongs (the arc between their balls is the renderer's), three long
 * jointed legs a third of a turn apart, each a thigh going out and down and a shin back in to the ground. Built at half scale
 * (RenderStriderSC draws it x2: about 4 blocks). Walking - the legs lift in turn; the hub sways a little. PRONG_TIP_Y / PRONG_X:
 * where the balls are (model px), for the renderer's arc.
 */
@SideOnly(Side.CLIENT)
public class ModelStriderSC extends ModelBase {

    /** The prongs' balls: px from the middle sideways, the height in model px (24 = the ground). */
    public static final float PRONG_X = 3F, PRONG_TIP_Y = -9.5F;
    /** The legs at rest (radians): a thigh out and down, its shin back in so the foot stands on the ground about a block out. */
    private static final float THIGH_OUT = -0.9F, SHIN_IN = 1.365F;

    final ModelRenderer hub, core, prongL, prongR, ballL, ballR;
    final ModelRenderer[] roots = new ModelRenderer[3], thighs = new ModelRenderer[3], shins = new ModelRenderer[3];

    public ModelStriderSC() {
        textureWidth = 64;
        textureHeight = 32;
        hub = new ModelRenderer(this, 0, 0);
        hub.addBox(-5F, -3F, -5F, 10, 5, 10);
        hub.setRotationPoint(0F, 0F, 0F);
        core = new ModelRenderer(this, 0, 16);
        core.addBox(-2F, -3F, -2F, 4, 3, 4);
        core.setRotationPoint(0F, -3F, 0F);
        hub.addChild(core);
        prongR = prong(-PRONG_X);
        prongL = prong(PRONG_X);
        hub.addChild(prongR);
        hub.addChild(prongL);
        ballR = ball(prongR);
        ballL = ball(prongL);
        for (int i = 0; i < 3; i++) {
            ModelRenderer root = new ModelRenderer(this, 0, 0);
            root.setRotationPoint(0F, 1F, 0F);
            root.rotateAngleY = (float) (i * 2D * Math.PI / 3D);
            hub.addChild(root);
            ModelRenderer thigh = new ModelRenderer(this, 40, 0);
            thigh.addBox(-1F, 0F, -1F, 2, 14, 2);
            thigh.setRotationPoint(0F, 0F, -4F);
            root.addChild(thigh);
            ModelRenderer shin = new ModelRenderer(this, 48, 0);
            shin.addBox(-0.5F, 0F, -0.5F, 1, 16, 1);
            shin.setRotationPoint(0F, 14F, 0F);
            thigh.addChild(shin);
            roots[i] = root;
            thighs[i] = thigh;
            shins[i] = shin;
        }
    }

    private ModelRenderer prong(float x) {
        ModelRenderer p = new ModelRenderer(this, 52, 0);
        p.addBox(-0.5F, -6F, -0.5F, 1, 6, 1);
        p.setRotationPoint(x, -3F, 0F);
        return p;
    }

    private ModelRenderer ball(ModelRenderer prong) {
        ModelRenderer b = new ModelRenderer(this, 56, 0);
        b.addBox(-1F, -7.5F, -1F, 2, 2, 2);
        prong.addChild(b);
        return b;
    }

    @Override
    public void render(Entity e, float limbSwing, float limbAmount, float age, float yaw, float pitch, float scale) {
        setRotationAngles(limbSwing, limbAmount, age, yaw, pitch, scale, e);
        hub.render(scale);
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbAmount, float age, float yaw, float pitch, float scale, Entity e) {
        hub.rotateAngleX = MathHelper.sin(age * 0.06F) * 0.03F + MathHelper.cos(limbSwing * 0.3F) * 0.05F * limbAmount;
        hub.rotateAngleZ = MathHelper.cos(age * 0.05F) * 0.03F;
        for (int i = 0; i < 3; i++) {
            float step = MathHelper.cos(limbSwing * 0.4F + i * 2.1F) * limbAmount;
            float lift = Math.max(0F, step);
            thighs[i].rotateAngleX = THIGH_OUT - lift * 0.3F;
            shins[i].rotateAngleX = SHIN_IN + lift * 0.5F;
        }
    }
}
