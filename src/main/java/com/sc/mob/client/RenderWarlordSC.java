package com.sc.mob.client;

import com.sc.mob.EntityWarlordSC;
import com.sc.mob.IMobActionSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;

/**
 * PACKAGE M (docs/plan-substrate-tech-f4.md §7.2, rule ПС-9): the camp's Warlord - a dark crimson undersuit (textures/entity/
 * warlord.png) under its crimson Quantum and the Exo chestplate, a little larger than its Marauders (SCALE), with the crest and
 * the cape of ModelWarlordSC. The dash's cue (ACT_DASH) bends it forward like the Trooper's; the recovery is the server's sneak;
 * the Ion Cutter's aim (ACT_AIM) shows its ion-blue sight (RenderSubstrateMobSC.aimLaser).
 */
@SideOnly(Side.CLIENT)
public class RenderWarlordSC extends RenderSubstrateMobSC {

    public static final float SCALE = 1.06F, CUE_LEAN = 22F;

    public RenderWarlordSC() {
        super(new ModelWarlordSC(), 0.55F, EntityWarlordSC.NAME);
    }

    @Override
    protected float scale(EntityLivingBase e) {
        return SCALE;
    }

    @Override
    protected float lean(EntityLivingBase e, int action, float partial) {
        return action == IMobActionSC.ACT_DASH ? CUE_LEAN : 0F;
    }

    @Override
    protected int aimLaser(EntityLiving e) {
        return EntityWarlordSC.CUTTER_COLOR;
    }

    @Override
    protected double aimRange(EntityLiving e) {
        return EntityWarlordSC.CUTTER_RANGE;
    }
}
