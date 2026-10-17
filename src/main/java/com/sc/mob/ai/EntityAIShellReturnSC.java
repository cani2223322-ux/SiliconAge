package com.sc.mob.ai;

import com.sc.mob.EntityHollowShellSC;

import net.minecraft.entity.ai.EntityAIBase;

/**
 * PACKAGE M (docs/plan-substrate-tech-f3.md §4.4): the patrol's leash - a Shell with a home (its Dead Node's crossing) that a
 * chase took more than LEASH blocks off (horizontally) drops its target and walks back until it is inside the zone again
 * (ZONE + a few blocks), giving up after GIVE_UP ticks (it tries again later). Above the melee (mutex 3); its target is kept
 * empty while it walks back (vanilla's home area already refuses new targets outside the leash).
 */
public class EntityAIShellReturnSC extends EntityAIBase {

    public static final int GIVE_UP = 400;
    private static final double BACK_INSIDE = EntityHollowShellSC.ZONE + 4D;

    private final EntityHollowShellSC mob;
    private final double speed;
    private int ticks;

    public EntityAIShellReturnSC(EntityHollowShellSC mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        setMutexBits(3);
    }

    private double fromHome() {
        double dx = mob.posX - (mob.homeX() + 0.5D), dz = mob.posZ - (mob.homeZ() + 0.5D);
        return Math.sqrt(dx * dx + dz * dz);
    }

    @Override
    public boolean shouldExecute() {
        return mob.hasNode() && !mob.rebooting() && fromHome() > EntityHollowShellSC.LEASH;
    }

    @Override
    public void startExecuting() {
        ticks = 0;
        mob.setAttackTarget(null);
        walk();
    }

    @Override
    public boolean continueExecuting() {
        return ticks < GIVE_UP && fromHome() > BACK_INSIDE && !mob.rebooting();
    }

    @Override
    public void resetTask() {
        mob.getNavigator().clearPathEntity();
    }

    @Override
    public void updateTask() {
        ticks++;
        mob.setAttackTarget(null);
        if (ticks % 20 == 0 && mob.getNavigator().noPath()) {
            walk();
        }
    }

    private void walk() {
        mob.getNavigator().tryMoveToXYZ(mob.homeX() + 0.5D, mob.homeY(), mob.homeZ() + 0.5D, speed);
    }
}
