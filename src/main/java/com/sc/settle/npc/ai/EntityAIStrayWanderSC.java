package com.sc.settle.npc.ai;

import com.sc.settle.ISettlementMemberSC;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIWander;

/**
 * PACKAGE N: vanilla's wandering - only for a stray settlement creature (settlement -1: a spawn egg's): a bound one has
 * its day (EntityAITunerDaySC) or its duty (EntityAIWardenPatrolSC) instead.
 */
public class EntityAIStrayWanderSC extends EntityAIWander {

    private final EntityCreature mob;

    public EntityAIStrayWanderSC(EntityCreature mob, double speed) {
        super(mob, speed);
        this.mob = mob;
    }

    @Override
    public boolean shouldExecute() {
        return ((ISettlementMemberSC) mob).settlementId() < 0 && super.shouldExecute();
    }
}
