package com.sc.mob.ai;

import com.sc.mob.EntitySentinelSC;
import com.sc.mob.IMobActionSC;
import com.sc.util.SoundsSC;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayer;

/**
 * PACKAGE M (docs/plan-substrate-tech-f4.md §7.3, rules ПС-9, ПС-33): the Sentinel's gravity press - its hum. Once in
 * PRESS_EVERY ticks, a player target within PRESS_RADIUS + 2 and in sight: it stands PRESS_HUM_TICKS (3 s) still with a low hum
 * (IMobActionSC.ACT_PRESS - every client draws the dark particles falling round it; the sound every second, lower and lower),
 * then the press itself begins (EntitySentinelSC.startPress: PRESS_TICKS of every player within PRESS_RADIUS pinned to the
 * ground - the entity's own tick, so it may walk and strike meanwhile). The hum can't be broken but by a suppressed chestplate
 * (a ПЭ-1 Point into the chest - no press). Movement and look its own while it hums (mutex 3), above the melee.
 */
public class EntityAISentinelPressSC extends EntityAIBase {

    private final EntitySentinelSC mob;
    private int ticks, readyAt;
    private boolean running;

    public EntityAISentinelPressSC(EntitySentinelSC mob) {
        this.mob = mob;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        if (mob.ticksExisted < readyAt || mob.pressing() || mob.mobArmor().suppressed(1)) {
            return false;
        }
        EntityLivingBase t = mob.getAttackTarget();
        double r = EntitySentinelSC.PRESS_RADIUS + 2D;
        return t instanceof EntityPlayer && t.isEntityAlive() && mob.getDistanceSqToEntity(t) <= r * r && mob.getEntitySenses().canSee(t);
    }

    @Override
    public void startExecuting() {
        ticks = 0;
        running = true;
        readyAt = mob.ticksExisted + EntitySentinelSC.PRESS_EVERY;
        mob.getNavigator().clearPathEntity();
        mob.setMobAction(IMobActionSC.ACT_PRESS);
        mob.setPressStage(EntitySentinelSC.STAGE_HUM);
        hum();
    }

    @Override
    public boolean continueExecuting() {
        return running && mob.isEntityAlive();
    }

    @Override
    public boolean isInterruptible() {
        return false;
    }

    @Override
    public void resetTask() {
        if (mob.mobAction() == IMobActionSC.ACT_PRESS) {
            mob.setMobAction(IMobActionSC.ACT_NONE);
        }
        if (running && !mob.pressing()) {
            mob.setPressStage(EntitySentinelSC.STAGE_NONE);         // broken off before the press
        }
        running = false;
    }

    @Override
    public void updateTask() {
        if (!running) {
            return;
        }
        if (mob.mobArmor().suppressed(1)) {
            running = false;                                       // the chestplate went dark: no press
            SoundsSC.play(mob.worldObj, mob.posX, mob.posY + 1.2D, mob.posZ, "power.off", 0.9F, 0.7F);
            return;
        }
        mob.getNavigator().clearPathEntity();
        EntityLivingBase t = mob.getAttackTarget();
        if (t != null) {
            mob.getLookHelper().setLookPositionWithEntity(t, 30F, 30F);
        }
        if (++ticks % 20 == 0 && ticks < EntitySentinelSC.PRESS_HUM_TICKS) {
            hum();
        }
        if (ticks >= EntitySentinelSC.PRESS_HUM_TICKS) {
            mob.startPress();
            running = false;
        }
    }

    /** The low hum, heard EntitySentinelSC.HUM_RANGE off, a little lower each second. */
    private void hum() {
        SoundsSC.play(mob.worldObj, mob.posX, mob.posY + 1D, mob.posZ, "emp.hum", EntitySentinelSC.HUM_RANGE / 16F, 0.5F - ticks / 400F);
    }
}
