package com.sc.settle.npc.ai;

import com.sc.settle.npc.EntityWardenSC;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayer;

/**
 * PACKAGE N (rule ПГ-6): the 2 s warning - the guard stops and faces the provoked player (the brain has drawn its blade
 * and sounded the alarm: EntityWardenSC.showAlert). No blow until package C names him a target; walked away 12+
 * blocks meanwhile - none at all. Mutex 3.
 */
public class EntityAIWardenWarnSC extends EntityAIBase {

    private final EntityWardenSC g;

    public EntityAIWardenWarnSC(EntityWardenSC g) {
        this.g = g;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        return g.getAttackTarget() == null && g.warnedPlayer() != null;
    }

    @Override
    public void startExecuting() {
        g.getNavigator().clearPathEntity();
    }

    @Override
    public void updateTask() {
        EntityPlayer p = g.warnedPlayer();
        if (p != null) {
            g.getLookHelper().setLookPositionWithEntity(p, 30F, 30F);
        }
    }
}
