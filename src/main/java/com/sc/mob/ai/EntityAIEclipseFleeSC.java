package com.sc.mob.ai;

import com.sc.mob.EntityEclipseSC;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;

/**
 * PACKAGE M (docs/plan-substrate-tech-f4.md §7.4, rule ПС-34: «при HP ниже 25% уходит и восстанавливается»): while «Затмение»
 * flees (EntityEclipseSC.fleeing - it decides when it starts and ends) it runs from the nearest player within FLEE_DISTANCE,
 * a new way every REPATH_TICKS (vanilla's RandomPositionGenerator away from the player); with nobody that near it stands and
 * recovers (the entity's own tick heals it). The blink (EntityAIEclipseBlinkSC) helps it away. Movement its own (mutex 3), above
 * everything but swimming.
 */
public class EntityAIEclipseFleeSC extends EntityAIBase {

    private static final int REPATH_TICKS = 20;

    private final EntityEclipseSC mob;
    private int repath;

    public EntityAIEclipseFleeSC(EntityEclipseSC mob) {
        this.mob = mob;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        return mob.fleeing();
    }

    @Override
    public void startExecuting() {
        repath = 0;
        mob.setAttackTarget(null);
    }

    @Override
    public boolean continueExecuting() {
        return mob.fleeing() && mob.isEntityAlive();
    }

    @Override
    public void resetTask() {
        mob.getNavigator().clearPathEntity();
    }

    @Override
    public void updateTask() {
        if (--repath > 0) {
            return;
        }
        repath = REPATH_TICKS;
        EntityPlayer p = mob.worldObj.getClosestVulnerablePlayerToEntity(mob, EntityEclipseSC.FLEE_DISTANCE);
        if (p == null) {
            mob.getNavigator().clearPathEntity();               // nobody near: it stands and recovers
            return;
        }
        Vec3 away = RandomPositionGenerator.findRandomTargetBlockAwayFrom(mob, 16, 7, Vec3.createVectorHelper(p.posX, p.posY, p.posZ));
        if (away != null) {
            mob.getNavigator().tryMoveToXYZ(away.xCoord, away.yCoord, away.zCoord, EntityEclipseSC.FLEE_SPEED);
        }
    }
}
