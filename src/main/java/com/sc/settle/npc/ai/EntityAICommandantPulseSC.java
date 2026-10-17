package com.sc.settle.npc.ai;

import com.sc.emp.EmpMobGunSC;
import com.sc.mob.ai.EntityAIEmpShooterSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.npc.EntityWardenSC;

import net.minecraft.entity.EntityLivingBase;

/**
 * PACKAGE S (docs/plan-substrate-tech-f3.md §6.5, rules ПГ-12, ПБ-17): the City Commandant's ПЭ-1 - package A's
 * EntityAIEmpShooterSC (a Pulse only, not the upgraded one, his reach his dome's radius + EntityWardenSC.COMMANDANT_RANGE_OUT;
 * the charge-up, the hum, the sight, the rules and the shot are all A's) let run only at a target the plan allows him:
 * EmpMobGunSC.allowed(), a hostile in the mod's armour (EntityWardenSC.empWorthy) in his dome or its belt, within his reach
 * - never a player (the guards' refusal says so too), never a Tuner or a guard, nothing while the rule is off. His brain
 * picks such a target (EntityAIWardenBrainSC); EntityWardenSC puts the ПЭ-1 in his hand for it. Made by EntityWardenSC
 * once his record gives the reach (the constructor's range is fixed).
 */
public class EntityAICommandantPulseSC extends EntityAIEmpShooterSC {

    private final EntityWardenSC g;

    public EntityAICommandantPulseSC(EntityWardenSC g, double range) {
        super(g, false, true, false, range);
        this.g = g;
    }

    /** The reach it was made with (EntityWardenSC makes a new one if the dome's radius changes). */
    public double reach() {
        return range;
    }

    @Override
    public boolean shouldExecute() {
        return allowedTarget() && super.shouldExecute();
    }

    @Override
    public boolean continueExecuting() {
        return allowedTarget() && super.continueExecuting();
    }

    /** ПГ-12: his attack target may take his Pulse. */
    private boolean allowedTarget() {
        if (!g.holdsPlaza() || !EmpMobGunSC.allowed()) {
            return false;
        }
        EntityLivingBase t = g.getAttackTarget();
        SettlementRegistrySC.Settlement s = g.link().get();
        return t != null && t.isEntityAlive() && s != null && EntityWardenSC.empWorthy(t) && s.inBelt(t.posX, t.posZ)
                && g.getDistanceSqToEntity(t) <= range * range;
    }
}
