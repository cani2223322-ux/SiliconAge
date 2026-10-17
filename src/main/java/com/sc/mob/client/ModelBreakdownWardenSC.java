package com.sc.mob.client;

import com.sc.mob.IMobActionSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

/**
 * PACKAGE B (docs/plan-substrate-tech-f5.md §5.1; proposal §9 «Торс — огромный Сингулярный нагрудник-реактор с кольцами тёмных частиц.
 * Четыре руки, в двух — Сингулярный клинок и ПЭ-1М. «Юбка» из кабелей»): the Breakdown Warden (128 x 64, textures/entity/
 * breakdownWarden.png, drawn 2 x 2 - tools/gen_finale_boss_tex.py), built 32 model pixels tall like a biped and drawn x
 * RenderBreakdownWardenSC.SCALE (3.5 blocks):
 * <ul>
 * <li>a small helmet (head, its visor slit lit by the renderer) over a huge chestplate-reactor (body: 14 x 12 x 8, the reactor's
 * core on its front - core, lit; a heat sink on its back), two pauldrons;</li>
 * <li>four arms - the upper pair from the shoulders (armUR holds the Singular Blade, armUL the ПЭ-1М: RenderBreakdownWardenSC draws
 * them in the hands), the lower pair from under the pauldrons with clawed hands;</li>
 * <li>a skirt of cables (CABLES strands round the hips, each with a plug at its end, swaying as it walks) over two short legs with
 * heavy boots (feet).</li>
 * </ul>
 * The poses by the telegraph (IMobActionSC, every client sees it): the silencing hum (ACT_SILENCE) - all four arms spread, the
 * torso back; the press (ACT_PRESS) - the arms pushing down before it, bowed; the core's wind-up (ACT_CORE_RESET) - the upper arms
 * raised high, the lower ones spread, trembling; the overheat (ACT_OVERHEAT) - hunched, the arms hanging, shaking; the call of a
 * wave (ACT_SUMMON) - the upper arms up; a ПЭ-1 Point charging (ACT_EMP_POINT) - the gun arm aimed. The blade arm swings with the
 * blow (vanilla's swing progress). The parts the renderer lights or glows are package-private fields.
 */
@SideOnly(Side.CLIENT)
public class ModelBreakdownWardenSC extends ModelBase {

    /** The cable strands of the skirt. */
    public static final int CABLES = 10;

    final ModelRenderer head, visor, body, core, sink, padR, padL, armUR, armUL, handUR, handUL, armLR, armLL, hips, legR, legL, footR, footL;
    final ModelRenderer[] cables = new ModelRenderer[CABLES];
    private final float[] cablePhase = new float[CABLES];

    public ModelBreakdownWardenSC() {
        textureWidth = 128;
        textureHeight = 64;
        body = new ModelRenderer(this, 0, 12);
        body.addBox(-7F, 0F, -4F, 14, 12, 8);
        body.setRotationPoint(0F, -2F, 0F);
        core = new ModelRenderer(this, 44, 12);
        core.addBox(-2F, 3F, -5F, 4, 4, 1);
        body.addChild(core);
        sink = new ModelRenderer(this, 0, 44);
        sink.addBox(-3F, 1F, 4F, 6, 8, 3);
        body.addChild(sink);
        padR = new ModelRenderer(this, 56, 0);
        padR.addBox(-11F, -1F, -3F, 5, 3, 6);
        body.addChild(padR);
        padL = new ModelRenderer(this, 56, 0);
        padL.mirror = true;
        padL.addBox(6F, -1F, -3F, 5, 3, 6);
        body.addChild(padL);
        head = new ModelRenderer(this, 0, 0);
        head.addBox(-3F, -6F, -3F, 6, 6, 6);
        head.setRotationPoint(0F, -2F, -0.5F);
        visor = new ModelRenderer(this, 24, 0);
        visor.addBox(-2F, -4F, -3.4F, 4, 1, 1);
        head.addChild(visor);
        armUR = new ModelRenderer(this, 80, 0);
        armUR.addBox(-2F, -1F, -1.5F, 3, 12, 3);
        armUR.setRotationPoint(-9F, 0F, 0F);
        handUR = new ModelRenderer(this, 92, 0);
        handUR.addBox(-2.5F, 10F, -2F, 4, 3, 4);
        armUR.addChild(handUR);
        armUL = new ModelRenderer(this, 80, 0);
        armUL.mirror = true;
        armUL.addBox(-1F, -1F, -1.5F, 3, 12, 3);
        armUL.setRotationPoint(9F, 0F, 0F);
        handUL = new ModelRenderer(this, 92, 0);
        handUL.mirror = true;
        handUL.addBox(-1.5F, 10F, -2F, 4, 3, 4);
        armUL.addChild(handUL);
        armLR = lowerArm(-7F, false);
        armLL = lowerArm(7F, true);
        hips = new ModelRenderer(this, 0, 32);
        hips.addBox(-5F, 0F, -3F, 10, 3, 6);
        hips.setRotationPoint(0F, 10F, 0F);
        legR = new ModelRenderer(this, 32, 32);
        legR.addBox(-2F, 0F, -2F, 4, 11, 4);
        legR.setRotationPoint(-2.5F, 13F, 0F);
        footR = new ModelRenderer(this, 48, 32);
        footR.addBox(-2.5F, 9F, -3F, 5, 2, 5);
        legR.addChild(footR);
        legL = new ModelRenderer(this, 32, 32);
        legL.mirror = true;
        legL.addBox(-2F, 0F, -2F, 4, 11, 4);
        legL.setRotationPoint(2.5F, 13F, 0F);
        footL = new ModelRenderer(this, 48, 32);
        footL.mirror = true;
        footL.addBox(-2.5F, 9F, -3F, 5, 2, 5);
        legL.addChild(footL);
        for (int i = 0; i < CABLES; i++) {                     // the skirt: strands round an ellipse under the hips
            double a = Math.PI * 2D * i / CABLES + 0.2D;
            ModelRenderer c = new ModelRenderer(this, 68, 32);
            c.addBox(-0.5F, 0F, -0.5F, 1, 9, 1);
            ModelRenderer plug = new ModelRenderer(this, 72, 32);
            plug.addBox(-1F, 9F, -1F, 2, 1, 2);
            c.addChild(plug);
            c.setRotationPoint((float) (Math.cos(a) * 5.6D), 12F, (float) (Math.sin(a) * 3.6D));
            cables[i] = c;
            cablePhase[i] = i * 1.7F;
        }
    }

    private ModelRenderer lowerArm(float x, boolean mirror) {
        ModelRenderer a = new ModelRenderer(this, 80, 16);
        a.mirror = mirror;
        a.addBox(-1F, -1F, -1F, 2, 10, 2);
        a.setRotationPoint(x, 5F, 0F);
        ModelRenderer claw = new ModelRenderer(this, 88, 16);
        claw.mirror = mirror;
        claw.addBox(-1.5F, 8.5F, -1.5F, 3, 2, 3);
        a.addChild(claw);
        return a;
    }

    @Override
    public void render(Entity e, float limbSwing, float limbAmount, float age, float yaw, float pitch, float scale) {
        setRotationAngles(limbSwing, limbAmount, age, yaw, pitch, scale, e);
        body.render(scale);
        head.render(scale);
        armUR.render(scale);
        armUL.render(scale);
        armLR.render(scale);
        armLL.render(scale);
        hips.render(scale);
        legR.render(scale);
        legL.render(scale);
        for (ModelRenderer c : cables) {
            c.render(scale);
        }
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbAmount, float age, float yaw, float pitch, float scale, Entity e) {
        int act = e instanceof IMobActionSC ? ((IMobActionSC) e).mobAction() : IMobActionSC.ACT_NONE;
        float swing = MathHelper.cos(limbSwing * 0.6662F) * 1.1F * limbAmount;
        float breathe = MathHelper.sin(age * 0.06F) * 0.04F;
        head.rotateAngleY = yaw / (180F / (float) Math.PI);
        head.rotateAngleX = pitch / (180F / (float) Math.PI);
        legR.rotateAngleX = swing;
        legL.rotateAngleX = -swing;
        body.rotateAngleX = breathe;
        // at rest: the upper arms a little out, the lower pair forward, swaying against the walk
        armUR.rotateAngleX = -swing * 0.4F + breathe;
        armUL.rotateAngleX = swing * 0.4F + breathe;
        armUR.rotateAngleZ = 0.22F;
        armUL.rotateAngleZ = -0.22F;
        armUR.rotateAngleY = armUL.rotateAngleY = 0F;
        armLR.rotateAngleX = -0.45F + swing * 0.3F - breathe;
        armLL.rotateAngleX = -0.45F - swing * 0.3F - breathe;
        armLR.rotateAngleZ = 0.32F;
        armLL.rotateAngleZ = -0.32F;
        if (onGround > 0F) {                                    // vanilla's swing progress: the blade arm chops down and back
            float s = MathHelper.sin(MathHelper.sqrt_float(onGround) * (float) Math.PI);
            armUR.rotateAngleX -= s * 1.7F + MathHelper.sin(onGround * (float) Math.PI) * 0.5F;
            armUR.rotateAngleY = -0.35F * s;
        }
        float shake = MathHelper.sin(age * 1.9F) * 0.06F;
        switch (act) {
            case IMobActionSC.ACT_EMP_POINT:
            case IMobActionSC.ACT_EMP_PULSE:
                armUL.rotateAngleX = -1.45F + head.rotateAngleX;   // the ПЭ-1М aimed along the look
                armUL.rotateAngleY = head.rotateAngleY * 0.6F;
                armUL.rotateAngleZ = 0F;
                break;
            case IMobActionSC.ACT_SILENCE:
                armUR.rotateAngleZ = 1.4F + shake;
                armUL.rotateAngleZ = -1.4F - shake;
                armLR.rotateAngleZ = 1.0F - shake;
                armLL.rotateAngleZ = -1.0F + shake;
                armUR.rotateAngleX = armUL.rotateAngleX = -0.3F;
                armLR.rotateAngleX = armLL.rotateAngleX = 0F;
                body.rotateAngleX = -0.16F;
                break;
            case IMobActionSC.ACT_PRESS:
                armUR.rotateAngleX = armUL.rotateAngleX = -0.9F;
                armLR.rotateAngleX = armLL.rotateAngleX = -0.7F;
                armUR.rotateAngleZ = armLR.rotateAngleZ = 0.1F;
                armUL.rotateAngleZ = armLL.rotateAngleZ = -0.1F;
                body.rotateAngleX = 0.25F;
                break;
            case IMobActionSC.ACT_CORE_RESET:
                armUR.rotateAngleX = armUL.rotateAngleX = -2.8F + shake;
                armUR.rotateAngleZ = 0.35F;
                armUL.rotateAngleZ = -0.35F;
                armLR.rotateAngleZ = 1.2F + shake;
                armLL.rotateAngleZ = -1.2F - shake;
                body.rotateAngleX = -0.12F + shake * 0.5F;
                break;
            case IMobActionSC.ACT_OVERHEAT:
                armUR.rotateAngleX = armUL.rotateAngleX = 0.1F + shake;
                armLR.rotateAngleX = armLL.rotateAngleX = 0.2F - shake;
                armUR.rotateAngleZ = 0.08F;
                armUL.rotateAngleZ = -0.08F;
                body.rotateAngleX = 0.35F + shake * 0.5F;
                break;
            case IMobActionSC.ACT_SUMMON:
                armUR.rotateAngleX = armUL.rotateAngleX = -2.9F;
                armUR.rotateAngleZ = 0.5F;
                armUL.rotateAngleZ = -0.5F;
                break;
            default:
                break;
        }
        head.rotationPointZ = -0.5F - body.rotateAngleX * 6F;   // the head follows a bow of the torso
        head.rotationPointY = -2F + Math.abs(body.rotateAngleX) * 1.5F;
        armUR.rotationPointZ = armUL.rotationPointZ = -body.rotateAngleX * 4F;
        armLR.rotationPointZ = armLL.rotationPointZ = -body.rotateAngleX * 9F;
        for (int i = 0; i < CABLES; i++) {                      // the skirt sways with the walk, a little all the time
            float ph = cablePhase[i];
            cables[i].rotateAngleX = MathHelper.sin(age * 0.08F + ph) * 0.06F + swing * 0.25F * (cables[i].rotationPointZ < 0F ? 1F : -1F);
            cables[i].rotateAngleZ = MathHelper.cos(age * 0.07F + ph) * 0.06F - cables[i].rotationPointX * 0.03F;
        }
    }
}
