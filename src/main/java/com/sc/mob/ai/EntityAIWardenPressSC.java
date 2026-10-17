package com.sc.mob.ai;

import com.sc.mob.EntityBreakdownWardenSC;
import com.sc.mob.IMobActionSC;
import com.sc.mob.WardenFightSC;
import com.sc.mob.WardenRulesSC;
import com.sc.util.SoundsSC;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayer;

/**
 * PACKAGE B (docs/plan-substrate-tech-f5.md §5.3, rules ПС-9, ПС-53): the Breakdown Warden's gravity press - as the Sentinel's
 * (EntityAISentinelPressSC): from «Пробой» on, once in PRESS_EVERY (WardenFightSC.pressIn), a player target within PRESS_RADIUS + 2
 * and in sight: it stands PRESS_HUM (3 s) still with a low hum (IMobActionSC.ACT_PRESS - WardenFightSC's telegraph by the stage
 * STAGE_HUM; every client draws the dark motes falling in), then the press itself (WardenFightSC.startPress: PRESS_TICKS of every
 * player within PRESS_RADIUS pinned down - the fight's own tick, so the Warden may walk and strike meanwhile). A suppressed
 * chestplate breaks the hum off (no press); never while the silencing pulse hums or the core winds up. Looks for a target in reach
 * once in CHECK_TICKS (§13). Movement and look its own while it hums (mutex 3), above the melee.
 */
public class EntityAIWardenPressSC extends EntityAIBase {

    private static final int CHECK_TICKS = 10;

    private final EntityBreakdownWardenSC mob;
    private int ticks, nextCheck;
    private boolean running;

    public EntityAIWardenPressSC(EntityBreakdownWardenSC mob) {
        this.mob = mob;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        if (mob.worldObj.isRemote || mob.ticksExisted < nextCheck) {
            return false;
        }
        nextCheck = mob.ticksExisted + CHECK_TICKS;
        WardenFightSC f = mob.fight();
        if (!f.ready() || f.phase < WardenRulesSC.PHASE_BREAKDOWN || f.pressIn > 0 || f.pressStage != WardenFightSC.STAGE_NONE
                || f.silenceHum || f.resetLeft > 0 || mob.mobArmor().suppressed(1)
                || mob.mobAction() == IMobActionSC.ACT_EMP_POINT) {      // ПБ-17: the Point's charge-up is shown first
            return false;
        }
        EntityLivingBase t = mob.getAttackTarget();
        double r = WardenRulesSC.PRESS_RADIUS + 2D;
        return t instanceof EntityPlayer && t.isEntityAlive() && mob.getDistanceSqToEntity(t) <= r * r && mob.getEntitySenses().canSee(t);
    }

    @Override
    public void startExecuting() {
        WardenFightSC f = mob.fight();
        ticks = 0;
        running = true;
        f.pressIn = WardenRulesSC.PRESS_EVERY;
        f.pressStage = WardenFightSC.STAGE_HUM;
        mob.getNavigator().clearPathEntity();
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
        WardenFightSC f = mob.fight();
        if (f.pressStage == WardenFightSC.STAGE_HUM) {
            f.pressStage = WardenFightSC.STAGE_NONE;              // broken off before the press
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
            SoundsSC.play(mob.worldObj, mob.posX, mob.posY + 2D, mob.posZ, "power.off", 1.2F, 0.6F);
            return;
        }
        mob.getNavigator().clearPathEntity();
        EntityLivingBase t = mob.getAttackTarget();
        if (t != null) {
            mob.getLookHelper().setLookPositionWithEntity(t, 30F, 30F);
        }
        if (++ticks % 20 == 0 && ticks < WardenRulesSC.PRESS_HUM) {
            hum();
        }
        if (ticks >= WardenRulesSC.PRESS_HUM) {
            mob.fight().startPress();
            running = false;
        }
    }

    private void hum() {
        SoundsSC.play(mob.worldObj, mob.posX, mob.posY + 1D, mob.posZ, "emp.hum", 1.5F, 0.45F - ticks / 400F);
    }
}
