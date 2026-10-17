package com.sc.mob.client;

import org.lwjgl.opengl.GL11;

import com.sc.mob.EntityJammerSC;
import com.sc.mob.IMobActionSC;
import com.sc.mob.MobArmorSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;

/**
 * PACKAGE M (docs/plan-substrate-tech-f3.md §4.3, rule ПС-9): the Renegade Jammer - its night-blue undersuit
 * (textures/entity/jammer.png) under the crimson Quantum, the antenna horns of ModelJammerSC. Their tips glow full-bright:
 * a calm blue breathing; aiming its emitter (ACT_AIM) or charging a ПЭ-1 (ACT_EMP_POINT, ACT_EMP_PULSE) - a fast blink, the
 * Pulse's in violet; the armour «down» (MobArmorSC.FLAG_DOWN) - out. The screen's bubble is package A's (MobArmorClientSC).
 */
@SideOnly(Side.CLIENT)
public class RenderJammerSC extends RenderSubstrateMobSC {

    /** The tips: calm / a Pulse charging. */
    public static final int TIP = 0x6A9CFF, TIP_PULSE = 0xB070FF;

    private final ModelJammerSC model;

    public RenderJammerSC() {
        this(new ModelJammerSC());
    }

    private RenderJammerSC(ModelJammerSC model) {
        super(model, 0.5F, EntityJammerSC.NAME);
        this.model = model;
    }

    /** After the model, the armour and the held item: the antenna tips. */
    @Override
    protected void renderEquippedItems(EntityLiving e, float partial) {
        super.renderEquippedItems(e, partial);
        if (e.deathTime > 0 || MobArmorSC.clientFlag(e, MobArmorSC.FLAG_DOWN)) {
            return;
        }
        int a = ((IMobActionSC) e).mobAction();
        int rgb = a == IMobActionSC.ACT_EMP_PULSE ? TIP_PULSE : TIP;
        float k;
        if (a == IMobActionSC.ACT_AIM || a == IMobActionSC.ACT_EMP_POINT || a == IMobActionSC.ACT_EMP_PULSE) {
            k = (e.ticksExisted / 2) % 2 == 0 ? 1F : 0.3F;
        } else {
            k = 0.55F + 0.25F * (float) Math.sin((e.ticksExisted + partial) * 0.12F);
        }
        GL11.glPushMatrix();
        model.bipedHead.postRender(0.0625F);
        lamp(model.tipLeft, rgb, k);
        lamp(model.tipRight, rgb, k);
        GL11.glPopMatrix();
    }

    /** The emitter's violet sight while it aims. */
    @Override
    protected int aimLaser(EntityLiving e) {
        return EntityJammerSC.EMITTER_COLOR;
    }

    @Override
    protected double aimRange(EntityLiving e) {
        return EntityJammerSC.EMITTER_RANGE;
    }

    @Override
    protected float lean(EntityLivingBase e, int action, float partial) {
        return action == IMobActionSC.ACT_EMP_PULSE ? -6F : 0F;   // braced back while the Pulse hums
    }
}
