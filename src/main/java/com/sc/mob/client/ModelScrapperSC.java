package com.sc.mob.client;

import com.sc.mob.EntityScrapperSC;
import com.sc.mob.IMobActionSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

/**
 * PACKAGE N (docs/plan-substrate-tech-f4.md §7.9, §7.11): the Scrapper Crab (64 x 64, textures/entity/scrapper.png) - a broad flat
 * shell of riveted scrap on six jointed legs, two claws in front, two eye stalks with amber lamps (eyes, lit by the renderer), and
 * the hopper on its back - its weak spot - with the swallowed junk (pile) showing in it, higher the fuller the bag
 * (EntityScrapperSC.bagStacks). Walking - the legs scuttle in two sets of three; the claws snap on its swing; stunned
 * (ACT_STUNNED) the shell sinks, the legs splay and the claws hang.
 */
@SideOnly(Side.CLIENT)
public class ModelScrapperSC extends ModelBase {

    private static final float BODY_Y = 13F;
    /** The legs: their places along the shell (z, px) and the angles out of it. */
    private static final float[] LEG_Z = {-4F, 0.5F, 5F};

    final ModelRenderer body, hopper, pile, eyes, clawL, clawR, pincerL, pincerR;
    final ModelRenderer[] legs = new ModelRenderer[6], shins = new ModelRenderer[6];

    public ModelScrapperSC() {
        textureWidth = 64;
        textureHeight = 64;
        body = new ModelRenderer(this, 0, 0);
        body.addBox(-8F, -4F, -7F, 16, 7, 14);
        body.setRotationPoint(0F, BODY_Y, 0F);
        hopper = new ModelRenderer(this, 0, 21);
        hopper.addBox(-5F, -6F, -4F, 10, 6, 9);
        hopper.setRotationPoint(0F, -4F, 2F);
        body.addChild(hopper);
        pile = new ModelRenderer(this, 0, 36);
        pile.addBox(-4F, -1F, -3F, 8, 1, 7);
        pile.setRotationPoint(0F, -1F, 0.5F);
        hopper.addChild(pile);
        ModelRenderer stalks = new ModelRenderer(this, 58, 21);
        stalks.addBox(-3.5F, -7F, -6.5F, 1, 3, 1);
        stalks.addBox(2.5F, -7F, -6.5F, 1, 3, 1);
        body.addChild(stalks);
        eyes = new ModelRenderer(this, 54, 29);
        eyes.addBox(-4F, -9F, -7F, 2, 2, 2);
        eyes.addBox(2F, -9F, -7F, 2, 2, 2);
        body.addChild(eyes);
        clawR = claw(-6F);
        clawL = claw(6F);
        pincerR = pincer(clawR);
        pincerL = pincer(clawL);
        for (int i = 0; i < 6; i++) {
            boolean right = i < 3;
            ModelRenderer leg = new ModelRenderer(this, 38, 21);
            leg.mirror = !right;
            leg.addBox(right ? -8F : 0F, -1F, -1F, 8, 2, 2);
            leg.setRotationPoint(right ? -7F : 7F, 1F, LEG_Z[i % 3]);
            body.addChild(leg);
            ModelRenderer shin = new ModelRenderer(this, 38, 25);
            shin.mirror = !right;
            shin.addBox(right ? -6F : 0F, -1F, -1F, 6, 2, 2);
            shin.setRotationPoint(right ? -8F : 8F, 0F, 0F);
            leg.addChild(shin);
            legs[i] = leg;
            shins[i] = shin;
        }
    }

    private ModelRenderer claw(float x) {
        ModelRenderer c = new ModelRenderer(this, 38, 29);
        c.addBox(-1F, -1F, -6F, 2, 2, 6);
        c.setRotationPoint(x, 0F, -6F);
        body.addChild(c);
        return c;
    }

    private ModelRenderer pincer(ModelRenderer claw) {
        ModelRenderer p = new ModelRenderer(this, 30, 37);
        p.addBox(-1.5F, -1.5F, -4F, 3, 3, 4);
        p.setRotationPoint(0F, 0F, -6F);
        claw.addChild(p);
        return p;
    }

    @Override
    public void render(Entity e, float limbSwing, float limbAmount, float age, float yaw, float pitch, float scale) {
        setRotationAngles(limbSwing, limbAmount, age, yaw, pitch, scale, e);
        body.render(scale);
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbAmount, float age, float yaw, float pitch, float scale, Entity e) {
        boolean stunned = e instanceof IMobActionSC && ((IMobActionSC) e).mobAction() == IMobActionSC.ACT_STUNNED;
        int stacks = e instanceof EntityScrapperSC ? ((EntityScrapperSC) e).bagStacks() : 0;
        float fill = Math.min(1F, stacks / (float) EntityScrapperSC.BAG_SLOTS);
        pile.showModel = stacks > 0;
        pile.rotationPointY = -1F - 4F * fill;
        body.rotationPointY = BODY_Y + (stunned ? 3F : MathHelper.sin(age * 0.1F) * 0.2F);
        body.rotateAngleX = 0F;
        for (int i = 0; i < 6; i++) {
            boolean right = i < 3;
            float side = right ? -1F : 1F;                  // round Z: a right leg goes down by a negative turn
            float phase = limbSwing * 0.9F + ((i + (right ? 0 : 1)) % 2 == 0 ? 0F : (float) Math.PI);
            float lift = stunned ? 0F : Math.max(0F, MathHelper.sin(phase)) * 0.5F * limbAmount;
            legs[i].rotateAngleZ = side * (stunned ? 0.05F : 0.45F + lift);       // out and a little down
            legs[i].rotateAngleY = (i % 3 - 1) * 0.45F * -side + (stunned ? 0F : MathHelper.cos(phase) * 0.35F * limbAmount);
            shins[i].rotateAngleZ = side * (stunned ? 0.3F : 0.9F);
        }
        float snap = MathHelper.sin(Math.min(1F, onGround) * (float) Math.PI);   // ModelBase.onGround: the swing's progress
        clawR.rotateAngleX = clawL.rotateAngleX = stunned ? 0.6F : -0.15F + MathHelper.sin(age * 0.07F) * 0.05F - snap * 0.5F;
        clawR.rotateAngleY = 0.25F - snap * 0.3F;
        clawL.rotateAngleY = -0.25F + snap * 0.3F;
        pincerR.rotateAngleY = -0.3F + snap * 0.4F;
        pincerL.rotateAngleY = 0.3F - snap * 0.4F;
    }
}
