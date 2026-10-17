package com.sc.mob.ai;

import com.sc.mob.EntityMimicSC;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;

/**
 * PACKAGE X (docs/plan-substrate-tech-f3.md §4.8): an awake mimic machine hunts - walks to its target (EntityMimicSC.SPEED,
 * vanilla's path finding, a new path every REPATH ticks) and «chews» it (EntityMimicSC.attackEntityAsMob: CHEW_DAMAGE) every
 * CHEW_EVERY ticks while it is within reach. Asleep or stunned no task runs at all (EntityMimicSC.updateAITasks). Movement
 * and look (mutex 3), as vanilla's EntityAIAttackOnCollide.
 */
public class EntityAIMimicChewSC extends EntityAIBase {

    public static final int REPATH = 10;

    private final EntityMimicSC mimic;
    private EntityLivingBase target;
    private int repath, chew;

    public EntityAIMimicChewSC(EntityMimicSC mimic) {
        this.mimic = mimic;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        EntityLivingBase t = mimic.getAttackTarget();
        if (t == null || !t.isEntityAlive() || mimic.dormant() || mimic.stunned()) {
            return false;
        }
        target = t;
        return true;
    }

    @Override
    public boolean continueExecuting() {
        return shouldExecute();
    }

    @Override
    public void startExecuting() {
        repath = 0;
    }

    @Override
    public void resetTask() {
        target = null;
        mimic.getNavigator().clearPathEntity();
    }

    @Override
    public void updateTask() {
        if (target == null) {
            return;
        }
        mimic.getLookHelper().setLookPositionWithEntity(target, 30F, 30F);
        if (--repath <= 0) {
            repath = REPATH + mimic.getRNG().nextInt(5);
            mimic.getNavigator().tryMoveToEntityLiving(target, 1.0D);
        }
        if (chew > 0) {
            chew--;
        }
        double reach = mimic.width * 2F * mimic.width * 2F + target.width;
        if (chew <= 0 && mimic.getDistanceSq(target.posX, target.boundingBox.minY, target.posZ) <= reach) {
            chew = EntityMimicSC.CHEW_EVERY;
            mimic.swingItem();
            mimic.attackEntityAsMob(target);
        }
    }
}
