package com.sc.mob.client;

import com.sc.mob.EntityTrooperSC;
import com.sc.mob.IMobActionSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;

/**
 * PACKAGE M (docs/plan-substrate-tech-f3.md §4.2, rule ПС-9): the Renegade Trooper - its dark undersuit
 * (textures/entity/trooper.png) under the crimson Quantum. The dash's cue (IMobActionSC.ACT_DASH) bends it forward like a
 * runner at the start, the dash itself leans it into the run; after it (ACT_RECOVER) it crouches - the server's sneak, its
 * back open. The red sparks and the trail are the entity's own (EntityTrooperSC.onLivingUpdate, client side). The rifle's
 * aim (ACT_AIM) shows its red laser sight (RenderSubstrateMobSC.aimLaser).
 */
@SideOnly(Side.CLIENT)
public class RenderTrooperSC extends RenderSubstrateMobSC {

    /** Degrees forward through the cue and the dash (ACT_DASH). */
    public static final float CUE_LEAN = 22F;

    public RenderTrooperSC() {
        super(new ModelBiped(), 0.5F, EntityTrooperSC.NAME);
    }

    @Override
    protected float lean(EntityLivingBase e, int action, float partial) {
        return action == IMobActionSC.ACT_DASH ? CUE_LEAN : 0F;
    }

    /** The Plasma Rifle's red laser sight while it aims. */
    @Override
    protected int aimLaser(EntityLiving e) {
        return EntityTrooperSC.RIFLE_COLOR;
    }

    @Override
    protected double aimRange(EntityLiving e) {
        return EntityTrooperSC.RIFLE_RANGE;
    }
}
