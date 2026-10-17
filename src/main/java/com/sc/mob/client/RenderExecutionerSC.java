package com.sc.mob.client;

import com.sc.mob.EntityExecutionerSC;
import com.sc.mob.IMobActionSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.EntityLivingBase;

/**
 * PACKAGE M (docs/plan-substrate-tech-f3.md §4.5, rule ПС-9): the Exo Executioner - «массивный»: the biped and its Exo drawn
 * SCALE larger (the hit box stays vanilla's 0.6 x 1.8), its gunmetal undersuit and the frosted helium tank of
 * ModelExecutionerSC. The wind-up of «Рассечение» (IMobActionSC.ACT_WINDUP) rocks it back, the blade's weight behind it,
 * further each tick of the second; the red glints over the arc are the entity's own (client side).
 */
@SideOnly(Side.CLIENT)
public class RenderExecutionerSC extends RenderSubstrateMobSC {

    public static final float SCALE = 1.12F, WINDUP_LEAN = -14F;

    public RenderExecutionerSC() {
        super(new ModelExecutionerSC(), 0.6F, EntityExecutionerSC.NAME);
    }

    @Override
    protected float scale(EntityLivingBase e) {
        return SCALE;
    }

    @Override
    protected float lean(EntityLivingBase e, int action, float partial) {
        return action == IMobActionSC.ACT_WINDUP ? WINDUP_LEAN : 0F;
    }
}
