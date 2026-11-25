package com.sc.mob.ai;

import java.util.List;

import com.sc.settle.GathererSC;
import com.sc.settle.npc.EntityGathererSC;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAITarget;

/**
 * МБ-1 А (docs/audit-2026-11-04-nedorabotki.md): a hostile's target after the player - a Gatherer out on its outing (GathererSC.outing)
 * within `reach` across (GathererSC.MOB_REACH), seen. Only when the mob has no target; asked about once in 10 ticks. Target mutex 1,
 * as the vanilla target tasks (a player found by the higher task takes over).
 */
public class EntityAITargetGathererSC extends EntityAITarget {

    private final double reach;
    private EntityGathererSC target;

    public EntityAITargetGathererSC(EntityCreature owner, double reach) {
        super(owner, true);
        this.reach = reach;
        setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        if (taskOwner.worldObj.isRemote || taskOwner.getAttackTarget() != null || taskOwner.getRNG().nextInt(10) != 0
                || !GathererSC.anyOuting(taskOwner.worldObj.provider.dimensionId)) {
            return false;
        }
        List<?> l = taskOwner.worldObj.getEntitiesWithinAABB(EntityGathererSC.class, taskOwner.boundingBox.expand(reach, 4D, reach));
        EntityGathererSC best = null;
        double bestD = reach * reach;
        for (Object o : l) {
            EntityGathererSC g = (EntityGathererSC) o;
            double dx = g.posX - taskOwner.posX, dz = g.posZ - taskOwner.posZ, d = dx * dx + dz * dz;
            if (d <= bestD && GathererSC.outing(g) != null && isSuitableTarget(g, false)) {
                best = g;
                bestD = d;
            }
        }
        target = best;
        return best != null;
    }

    @Override
    public void startExecuting() {
        taskOwner.setAttackTarget(target);
        super.startExecuting();
    }
}
