package com.sc.mob.ai;

import com.sc.mob.EntityScrapperSC;
import com.sc.settle.npc.ai.NpcNavSC;

import net.minecraft.entity.ai.EntityAIBase;

/**
 * PACKAGE N (docs/plan-substrate-tech-f4.md §7.9, rule ПС-39): a Scrapper on an errand (ScrapperSC: its home the place of a death)
 * walks there - a path every REPATH ticks (one that will not come waits for the next; past its follow range - the path search's -
 * by NpcNavSC's legs: it is sent from up to ScrapperSC.SPAWN_MAX off) - until it stands within ARRIVED blocks of
 * it; there EntityAIScrapperCollectSC finds the things. The errand ends only by ScrapperSC (nothing tagged left near). Movement
 * (mutex 1), below collecting.
 */
public class EntityAIScrapperErrandSC extends EntityAIBase {

    private static final int REPATH = 20;
    private static final double ARRIVED = 3D;

    private final EntityScrapperSC mob;
    private final double speed;
    private int repath;

    public EntityAIScrapperErrandSC(EntityScrapperSC mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        return mob.onErrand() && mob.hasOwnHome() && mob.getAttackTarget() == null && !mob.stunned() && far();
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
        mob.getNavigator().clearPathEntity();
    }

    @Override
    public void updateTask() {
        if (--repath <= 0) {
            repath = REPATH;
            NpcNavSC.walkTo(mob, mob.homeX() + 0.5D, mob.homeY(), mob.homeZ() + 0.5D, speed);
        }
    }

    private boolean far() {
        return mob.getDistanceSq(mob.homeX() + 0.5D, mob.homeY(), mob.homeZ() + 0.5D) > ARRIVED * ARRIVED;
    }
}
