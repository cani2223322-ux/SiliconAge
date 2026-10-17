package com.sc.mob.client;

import com.sc.mob.EntitySentinelSC;
import com.sc.mob.IMobActionSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;

/**
 * PACKAGE M (docs/plan-substrate-tech-f4.md §7.3, rule ПС-9): the Singular Sentinel - «выше двух метров»: the biped and its
 * Singular suit (the scheme H «Пустота») drawn SCALE larger (its hit box is 0.7 x 2.2), a void-black undersuit with thin white
 * seams (textures/entity/sentinel.png). Through its hum (EntitySentinelSC.STAGE_HUM) it braces back, the press itself
 * (STAGE_PRESS) bows it forward over the pinned; the ring over its shoulders and the falling shards are the entity's own
 * particles. A carrier's Ion Cutter (the rule off) shows its sight while it aims.
 */
@SideOnly(Side.CLIENT)
public class RenderSentinelSC extends RenderSubstrateMobSC {

    public static final float SCALE = 1.2F, HUM_LEAN = -8F, PRESS_LEAN = 10F;

    public RenderSentinelSC() {
        super(headless(new ModelBiped()), 0.7F, EntitySentinelSC.NAME);
    }

    private static ModelBiped headless(ModelBiped m) {
        m.bipedHeadwear.showModel = false;
        return m;
    }

    @Override
    protected float scale(EntityLivingBase e) {
        return SCALE;
    }

    @Override
    protected float lean(EntityLivingBase e, int action, float partial) {
        if (!(e instanceof EntitySentinelSC)) {
            return 0F;
        }
        int stage = ((EntitySentinelSC) e).pressStage();
        if (stage == EntitySentinelSC.STAGE_HUM) {
            return HUM_LEAN + 1.5F * (float) Math.sin((e.ticksExisted + partial) * 1.3F);
        }
        return stage == EntitySentinelSC.STAGE_PRESS ? PRESS_LEAN : 0F;
    }

    @Override
    protected int aimLaser(EntityLiving e) {
        return EntitySentinelSC.CUTTER_COLOR;
    }

    @Override
    protected double aimRange(EntityLiving e) {
        return EntitySentinelSC.CUTTER_RANGE;
    }
}
