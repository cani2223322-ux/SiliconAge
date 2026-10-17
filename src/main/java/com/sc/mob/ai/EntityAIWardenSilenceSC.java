package com.sc.mob.ai;

import com.sc.emp.EmpMobGunSC;
import com.sc.mob.EntityBreakdownWardenSC;
import com.sc.mob.IMobActionSC;
import com.sc.mob.WardenFightSC;
import com.sc.mob.WardenRulesSC;
import com.sc.util.SoundsSC;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;

/**
 * PACKAGE B (docs/plan-substrate-tech-f5.md §5.3, §5.4, rules ПС-9, ПС-53, ПС-55): «Импульс глушения» - from «Пробой» on, once in
 * SILENCE_EVERY (WardenFightSC.silenceIn), a real player within SILENCE_RADIUS of the Warden: it stands SILENCE_HUM (3 s) still with
 * the rising hum heard SILENCE_RADIUS off (IMobActionSC.ACT_SILENCE: every client sees the motes drawn in from the pulse's reach),
 * then the pulse itself (WardenFightSC.silence: a ПЭ-1 Pulse on every player within the radius it can see - cover saves). Only with
 * the mobs' ПЭ-1 allowed and the ПЭ-1М in its hand (ПС-55); never while the press hums or the core winds up; the hum isn't broken off
 * (the Warden's own discharge). Looks for a player once in CHECK_TICKS (§13). Movement its own while it hums (mutex 3).
 */
public class EntityAIWardenSilenceSC extends EntityAIBase {

    private static final int CHECK_TICKS = 10;

    private final EntityBreakdownWardenSC mob;
    private int ticks, nextCheck;
    private boolean running;

    public EntityAIWardenSilenceSC(EntityBreakdownWardenSC mob) {
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
        if (!f.ready() || f.phase < WardenRulesSC.PHASE_BREAKDOWN || f.silenceIn > 0 || f.silenceHum
                || f.pressStage == WardenFightSC.STAGE_HUM || f.resetLeft > 0 || !EmpMobGunSC.allowed() || !mob.holdsPe1m()
                || mob.mobAction() == IMobActionSC.ACT_EMP_POINT) {      // ПБ-17: the Point's charge-up is shown first
            return false;
        }
        double r2 = WardenRulesSC.SILENCE_RADIUS * WardenRulesSC.SILENCE_RADIUS;
        for (EntityLivingBase p : f.players(WardenRulesSC.SCALE_RADIUS)) {
            if (p.getDistanceSqToEntity(mob) <= r2) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void startExecuting() {
        WardenFightSC f = mob.fight();
        ticks = 0;
        running = true;
        f.silenceIn = WardenRulesSC.SILENCE_EVERY;
        f.silenceHum = true;
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
        mob.fight().silenceHum = false;
        running = false;
    }

    @Override
    public void updateTask() {
        if (!running) {
            return;
        }
        mob.getNavigator().clearPathEntity();
        EntityLivingBase t = mob.getAttackTarget();
        if (t != null) {
            mob.getLookHelper().setLookPositionWithEntity(t, 30F, 30F);
        }
        if (++ticks % 20 == 0 && ticks < WardenRulesSC.SILENCE_HUM) {
            hum();
        }
        if (ticks >= WardenRulesSC.SILENCE_HUM) {
            mob.fight().silenceHum = false;
            mob.fight().silence();
            running = false;
        }
    }

    /** Heard SILENCE_RADIUS off, a little higher each second. */
    private void hum() {
        SoundsSC.play(mob.worldObj, mob.posX, mob.posY + 2D, mob.posZ, "warden.silence", WardenRulesSC.SILENCE_RADIUS / 16F,
                0.6F + ticks / 120F);
    }
}
