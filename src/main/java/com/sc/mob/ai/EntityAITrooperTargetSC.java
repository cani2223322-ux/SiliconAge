package com.sc.mob.ai;

import com.sc.mob.EntityTrooperSC;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAITarget;
import net.minecraft.entity.player.EntityPlayer;

/**
 * PACKAGE M (docs/plan-substrate-tech-f3.md §4.2): the Trooper goes for the player in the weakest armour - of the players it
 * may attack (vanilla's isSuitableTarget: alive, not in creative, in sight) within its follow range, the one with the
 * fewest armour points (getTotalArmorValue: the mod's armour counts as it shows), the nearer of two equal. Looked at again
 * every RETARGET_TICKS while it fights: a friend in Nano who comes into view becomes the target («друга в Нано нужно
 * прикрывать»). A target task (mutex 1) above vanilla's HurtByTarget, which answers anyone else who hurts it.
 */
public class EntityAITrooperTargetSC extends EntityAITarget {

    private int wait;
    private EntityLivingBase pick;

    public EntityAITrooperTargetSC(EntityTrooperSC mob) {
        super(mob, true);
        setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        if (--wait > 0) {
            return false;
        }
        wait = EntityTrooperSC.RETARGET_TICKS / 2 + taskOwner.getRNG().nextInt(EntityTrooperSC.RETARGET_TICKS / 2 + 1);
        pick = weakest();
        return pick != null;
    }

    @Override
    public void startExecuting() {
        taskOwner.setAttackTarget(pick);
        wait = EntityTrooperSC.RETARGET_TICKS;
        super.startExecuting();
    }

    @Override
    public boolean continueExecuting() {
        if (--wait <= 0) {
            wait = EntityTrooperSC.RETARGET_TICKS;
            EntityLivingBase w = weakest();
            if (w != null && w != taskOwner.getAttackTarget()) {
                taskOwner.setAttackTarget(w);
            }
        }
        return super.continueExecuting();
    }

    @Override
    public void resetTask() {
        pick = null;
        super.resetTask();
    }

    /** The visible player it may attack with the fewest armour points, null none. */
    private EntityLivingBase weakest() {
        double range = getTargetDistance();
        EntityPlayer best = null;
        int bestArmour = Integer.MAX_VALUE;
        double bestDist = Double.MAX_VALUE;
        for (Object o : taskOwner.worldObj.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            double d = taskOwner.getDistanceSqToEntity(p);
            if (d > range * range || !isSuitableTarget(p, false)) {
                continue;
            }
            int armour = p.getTotalArmorValue();
            if (armour < bestArmour || armour == bestArmour && d < bestDist) {
                best = p;
                bestArmour = armour;
                bestDist = d;
            }
        }
        return best;
    }
}
