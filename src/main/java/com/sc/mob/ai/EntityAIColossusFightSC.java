package com.sc.mob.ai;

import com.sc.mob.EntityColossusSC;
import com.sc.mob.IMobActionSC;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;

/**
 * PACKAGE N (docs/plan-substrate-tech-f4.md §7.7, rules ПС-4, ПС-9, ПС-37): the Quartzite Colossus's fight. Its target within
 * EntityColossusSC.STOMP_RADIUS (and STOMP_DY of its feet), the stomp ready - it stops and winds up STOMP_WINDUP ticks
 * (IMobActionSC.ACT_STOMP: its arms go up - every client sees it), then EntityColossusSC.stomp; the target THROW_MIN..THROW_RANGE
 * off and in sight (checked every SIGHT_CHECK ticks), the throw ready - it aims THROW_AIM ticks (ACT_THROW: a crystal glows in its
 * raised hand), then throwCrystal if the line is still clear and the target still within reach (stepping behind cover in time
 * dodges it). Else it walks up (a path every REPATH ticks). A wind-up runs to its end whatever the target does. Movement and look
 * (mutex 3).
 */
public class EntityAIColossusFightSC extends EntityAIBase {

    private static final int REPATH = 10, SIGHT_CHECK = 10;
    private static final int NONE = 0, STOMP = 1, THROW = 2;

    private final EntityColossusSC colossus;
    private int windingUp = NONE, windup, stompCd, throwCd, repath, sightIn;
    private boolean seen;

    public EntityAIColossusFightSC(EntityColossusSC colossus) {
        this.colossus = colossus;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        EntityLivingBase t = colossus.getAttackTarget();
        return t != null && t.isEntityAlive();
    }

    @Override
    public boolean continueExecuting() {
        return windingUp != NONE || shouldExecute();
    }

    @Override
    public void startExecuting() {
        repath = 0;
        sightIn = 0;
    }

    @Override
    public void resetTask() {
        windingUp = NONE;
        windup = 0;
        if (colossus.mobAction() == IMobActionSC.ACT_STOMP || colossus.mobAction() == IMobActionSC.ACT_THROW) {
            colossus.setMobAction(IMobActionSC.ACT_NONE);
        }
        colossus.getNavigator().clearPathEntity();
    }

    @Override
    public void updateTask() {
        if (stompCd > 0) {
            stompCd--;
        }
        if (throwCd > 0) {
            throwCd--;
        }
        EntityLivingBase t = colossus.getAttackTarget();
        if (windingUp != NONE) {
            windUp(t);
            return;
        }
        if (t == null) {
            return;
        }
        colossus.getLookHelper().setLookPositionWithEntity(t, 20F, 20F);
        if (--sightIn <= 0) {
            seen = colossus.getEntitySenses().canSee(t);
            sightIn = SIGHT_CHECK;
        }
        double dx = t.posX - colossus.posX, dz = t.posZ - colossus.posZ, d = Math.sqrt(dx * dx + dz * dz);
        double dy = Math.abs(t.posY - colossus.posY);
        if (d <= EntityColossusSC.STOMP_RADIUS - 0.5D && dy <= EntityColossusSC.STOMP_DY && stompCd <= 0) {
            start(STOMP);
            return;
        }
        if (d >= EntityColossusSC.THROW_MIN && d <= EntityColossusSC.THROW_RANGE && seen && throwCd <= 0) {
            start(THROW);
            return;
        }
        if (--repath <= 0) {
            repath = REPATH;
            if (d > 2.5D) {
                colossus.getNavigator().tryMoveToEntityLiving(t, 1.0D);
            } else {
                colossus.getNavigator().clearPathEntity();
            }
        }
    }

    private void start(int kind) {
        windingUp = kind;
        windup = 0;
        colossus.getNavigator().clearPathEntity();
        colossus.setMobAction(kind == STOMP ? IMobActionSC.ACT_STOMP : IMobActionSC.ACT_THROW);
        colossus.windUpSound(kind == STOMP);
    }

    /** A tick of the wind-up: standing, facing the target; at its end the blow. */
    private void windUp(EntityLivingBase t) {
        colossus.getNavigator().clearPathEntity();
        if (t != null) {
            colossus.getLookHelper().setLookPositionWithEntity(t, 30F, 30F);
            float yaw = (float) (Math.atan2(t.posZ - colossus.posZ, t.posX - colossus.posX) * 180D / Math.PI) - 90F;
            colossus.rotationYaw = colossus.renderYawOffset = yaw;
        }
        if (++windup < (windingUp == STOMP ? EntityColossusSC.STOMP_WINDUP : EntityColossusSC.THROW_AIM)) {
            return;
        }
        if (windingUp == STOMP) {
            colossus.stomp();
            stompCd = EntityColossusSC.STOMP_COOLDOWN;
        } else {
            if (t != null && t.isEntityAlive() && colossus.getDistanceToEntity(t) <= EntityColossusSC.THROW_RANGE + 1D && colossus.canEntityBeSeen(t)) {
                colossus.throwCrystal(t);
            }
            throwCd = EntityColossusSC.THROW_COOLDOWN;
        }
        windingUp = NONE;
        windup = 0;
        colossus.setMobAction(IMobActionSC.ACT_NONE);
    }
}
