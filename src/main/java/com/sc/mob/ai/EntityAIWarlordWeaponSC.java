package com.sc.mob.ai;

import com.sc.mob.EntityWarlordSC;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;

/**
 * PACKAGE M (docs/plan-substrate-tech-f4.md §7.2, rule ПС-32): the camp's Warlord changes what it holds by the distance to its
 * target - the Ion Cutter once the target is further than SWITCH_RANGE blocks and in sight (EntityAIBeamAttackSC then takes
 * over: it holds a gun), the Quantum Blade again closer than BLADE_FROM (the melee and the dash take over). Looked at every
 * CHECK_TICKS, a change at most once in SWAP_TICKS (each change is an equipment packet to every watcher). No movement, no look
 * (mutex 0): it runs beside the fighting tasks.
 */
public class EntityAIWarlordWeaponSC extends EntityAIBase {

    private static final int CHECK_TICKS = 10, SWAP_TICKS = 30;

    private final EntityWarlordSC mob;
    private int nextCheck, nextSwap;

    public EntityAIWarlordWeaponSC(EntityWarlordSC mob) {
        this.mob = mob;
        setMutexBits(0);
    }

    @Override
    public boolean shouldExecute() {
        if (mob.ticksExisted < nextCheck) {
            return false;
        }
        nextCheck = mob.ticksExisted + CHECK_TICKS;
        EntityLivingBase t = mob.getAttackTarget();
        boolean cutter;
        if (t == null || !t.isEntityAlive()) {
            cutter = false;                                       // nothing to shoot at: the blade
        } else {
            double d = mob.getDistanceToEntity(t);
            if (d > EntityWarlordSC.SWITCH_RANGE && mob.getEntitySenses().canSee(t)) {
                cutter = true;
            } else if (d < EntityWarlordSC.BLADE_FROM) {
                cutter = false;
            } else {
                return false;                                     // in between: keep what it holds
            }
        }
        if (cutter == mob.holdsCutter() || mob.ticksExisted < nextSwap || mob.dashing()) {
            return false;
        }
        mob.wield(cutter);
        nextSwap = mob.ticksExisted + SWAP_TICKS;
        return false;                                             // a one-tick decision: nothing runs on
    }
}
