package com.sc.mob.client;

import com.sc.mob.EntityLeechSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

/**
 * PACKAGE N (docs/plan-substrate-tech-f4.md §7.6, §7.11): the Current Leech (64 x 32, textures/entity/leech.png) - a snake of
 * cables: a head plug with two copper prongs and SEGMENTS braided segments behind it, about 3 blocks long. Each part is placed
 * on its own every frame: crawling - a wave runs down the body, the head steady at the front; coiled round its prey
 * (EntityLeechSC.coiled) - a helix round the prey's legs, climbing COIL_RISE a turn, slowly turning, the head at the top facing
 * out. The prongs' tips (prongs) the renderer lights.
 */
@SideOnly(Side.CLIENT)
public class ModelLeechSC extends ModelBase {

    public static final int SEGMENTS = 8;
    /** A segment's length (px); the helix's radius, its rise a segment, the segments a turn. */
    private static final float SEG = 5F, COIL_R = 6.5F, COIL_RISE = 1.4F, COIL_TURN = 6F;
    private static final float GROUND = 22.5F;

    final ModelRenderer head, prongs;
    final ModelRenderer[] segs = new ModelRenderer[SEGMENTS];

    public ModelLeechSC() {
        textureWidth = 64;
        textureHeight = 32;
        head = new ModelRenderer(this, 0, 10);
        head.addBox(-2F, -2F, -3F, 4, 4, 5);
        prongs = new ModelRenderer(this, 20, 10);
        prongs.addBox(-1.5F, -1F, -5F, 1, 1, 2);
        prongs.addBox(0.5F, -1F, -5F, 1, 1, 2);
        head.addChild(prongs);
        for (int i = 0; i < SEGMENTS; i++) {
            ModelRenderer s = new ModelRenderer(this, 0, 0);
            s.addBox(-1.5F, -1.5F, -2.5F, 3, 3, 5);
            segs[i] = s;
        }
    }

    @Override
    public void render(Entity e, float limbSwing, float limbAmount, float age, float yaw, float pitch, float scale) {
        setRotationAngles(limbSwing, limbAmount, age, yaw, pitch, scale, e);
        head.render(scale);
        for (ModelRenderer s : segs) {
            s.render(scale);
        }
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbAmount, float age, float yaw, float pitch, float scale, Entity e) {
        boolean coiled = e instanceof EntityLeechSC && ((EntityLeechSC) e).coiled();
        if (coiled) {
            float spin = age * 0.08F;
            for (int i = 0; i < SEGMENTS; i++) {
                float a = spin + (float) (i * 2D * Math.PI / COIL_TURN);
                ModelRenderer s = segs[SEGMENTS - 1 - i];        // the tail at the bottom, the head at the top
                s.setRotationPoint(MathHelper.cos(a) * COIL_R, GROUND - i * COIL_RISE, MathHelper.sin(a) * COIL_R);
                s.rotateAngleY = -a;                            // along the circle
                s.rotateAngleX = 0.15F;
                s.rotateAngleZ = 0F;
            }
            float a = spin + (float) (SEGMENTS * 2D * Math.PI / COIL_TURN);
            head.setRotationPoint(MathHelper.cos(a) * COIL_R, GROUND - SEGMENTS * COIL_RISE, MathHelper.sin(a) * COIL_R);
            head.rotateAngleY = (float) (-a - Math.PI / 2D);    // facing out of the helix
            head.rotateAngleX = -0.3F;
            return;
        }
        float speed = 0.25F + Math.min(1F, limbAmount) * 0.6F;
        float phase = age * speed;
        float z = -(SEGMENTS * SEG) * 0.5F;
        head.setRotationPoint(0F, GROUND - 0.5F + MathHelper.sin(age * 0.1F) * 0.3F, z);
        head.rotateAngleY = MathHelper.sin(phase) * 0.15F;
        head.rotateAngleX = 0F;
        float lastX = 0F;
        for (int i = 0; i < SEGMENTS; i++) {
            float k = (i + 1F) / SEGMENTS;
            float x = MathHelper.sin(phase - (i + 1) * 0.8F) * 2.2F * k;
            ModelRenderer s = segs[i];
            s.setRotationPoint(x, GROUND, z + (i + 1) * SEG);
            s.rotateAngleY = (float) Math.atan2(x - lastX, SEG);   // along the wave
            s.rotateAngleX = 0F;
            s.rotateAngleZ = 0F;
            lastX = x;
        }
    }
}
