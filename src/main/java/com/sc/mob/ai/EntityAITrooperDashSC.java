package com.sc.mob.ai;

import com.sc.mob.EntityTrooperSC;
import com.sc.mob.IMobActionSC;
import com.sc.mob.MobCombatSC;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;

/**
 * PACKAGE M (docs/plan-substrate-tech-f3.md §4.2, rules ПС-9, ПС-10): the Trooper's dash - only with the blade in hand, the
 * target DASH_FROM..DASH_TO blocks off, in sight and about level with it, no piece of its armour suppressed (a ПЭ-1 Point
 * buys 13 s without a dash). Three steps, movement and look its own (mutex 3, above the melee):
 * <ol>
 * <li>the cue - DASH_CUE_TICKS standing, turned to the target, IMobActionSC.ACT_DASH (the red sparks every client draws, a
 * servo's whine): the moment to step aside;</li>
 * <li>the dash - straight at where the target stood when the cue ended, DASH_LENGTH at DASH_SPEED a tick (a wall stops it);
 * ending next to the target it strikes once (its own blade blow, attackEntityAsMob);</li>
 * <li>the recovery - RECOVER_TICKS crouched and still, facing the dash's way (ACT_RECOVER): a blow from behind counts x1.5
 * (EntityTrooperSC.attackEntityFrom).</li>
 * </ol>
 * A suppression during the cue breaks it off; the next dash comes DASH_COOLDOWN after this one began.
 */
public class EntityAITrooperDashSC extends EntityAIBase {

    /** Blocks a tick of the dash (6 in 4 ticks); ticks it may last at most; reach of the blow at its end (squared). */
    public static final double DASH_SPEED = 1.5D, STRIKE_SQ = 6.25D;
    private static final int DASH_MAX_TICKS = 6;
    private static final int CUE = 0, DASH = 1, RECOVER = 2, DONE = 3;

    private final EntityTrooperSC mob;
    private EntityLivingBase target;
    private int phase, ticks, readyAt;
    private double dirX, dirZ, fromX, fromZ;
    private float yaw;

    public EntityAITrooperDashSC(EntityTrooperSC mob) {
        this.mob = mob;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        if (mob.ticksExisted < readyAt || !mob.onGround || MobCombatSC.blade(mob) == null || mob.mobArmor().anySuppressed()) {
            return false;
        }
        EntityLivingBase t = mob.getAttackTarget();
        if (t == null || !t.isEntityAlive() || Math.abs(t.posY - mob.posY) > 2.5D) {
            return false;
        }
        double d = mob.getDistanceToEntity(t);
        if (d < EntityTrooperSC.DASH_FROM || d > EntityTrooperSC.DASH_TO || !mob.getEntitySenses().canSee(t)) {
            return false;
        }
        target = t;
        return true;
    }

    @Override
    public void startExecuting() {
        phase = CUE;
        ticks = 0;
        readyAt = mob.ticksExisted + EntityTrooperSC.DASH_COOLDOWN;
        mob.getNavigator().clearPathEntity();
        mob.setMobAction(IMobActionSC.ACT_DASH);
        mob.playSound("mob.irongolem.throw", 1F, 1.6F);              // the servos wind up
    }

    @Override
    public boolean continueExecuting() {
        return phase != DONE && mob.isEntityAlive();
    }

    @Override
    public boolean isInterruptible() {
        return phase == CUE;                                         // once it goes, it goes (and recovers)
    }

    @Override
    public void resetTask() {
        int a = mob.mobAction();
        if (a == IMobActionSC.ACT_DASH || a == IMobActionSC.ACT_RECOVER) {
            mob.setMobAction(IMobActionSC.ACT_NONE);
        }
        mob.setSneaking(false);
        mob.getNavigator().clearPathEntity();
        target = null;
        phase = DONE;
    }

    @Override
    public void updateTask() {
        switch (phase) {
            case CUE:
                cue();
                break;
            case DASH:
                dash();
                break;
            case RECOVER:
                face(yaw);
                mob.getNavigator().clearPathEntity();
                mob.motionX *= 0.5D;
                mob.motionZ *= 0.5D;
                if (++ticks >= EntityTrooperSC.RECOVER_TICKS) {
                    phase = DONE;
                }
                break;
            default:
                break;
        }
    }

    private void cue() {
        if (target == null || !target.isEntityAlive() || mob.mobArmor().anySuppressed()) {
            phase = DONE;                                            // the target went, or a ПЭ-1 got it first
            return;
        }
        mob.getNavigator().clearPathEntity();
        face(MobCombatSC.yawTo(mob.posX, mob.posZ, target.posX, target.posZ));
        if (++ticks < EntityTrooperSC.DASH_CUE_TICKS) {
            return;
        }
        double dx = target.posX - mob.posX, dz = target.posZ - mob.posZ, len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-3D) {
            phase = DONE;
            return;
        }
        dirX = dx / len;
        dirZ = dz / len;
        fromX = mob.posX;
        fromZ = mob.posZ;
        yaw = MobCombatSC.yawTo(mob.posX, mob.posZ, target.posX, target.posZ);
        mob.setDashYaw(yaw);
        phase = DASH;
        ticks = 0;
        mob.playSound("mob.irongolem.throw", 1.2F, 0.8F);
    }

    private void dash() {
        double gone = Math.sqrt((mob.posX - fromX) * (mob.posX - fromX) + (mob.posZ - fromZ) * (mob.posZ - fromZ));
        double left = EntityTrooperSC.DASH_LENGTH - gone;
        boolean arrived = target != null && target.isEntityAlive() && mob.getDistanceSqToEntity(target) <= 2.6D;
        if (left <= 0.05D || arrived || ticks >= DASH_MAX_TICKS || ticks > 0 && mob.isCollidedHorizontally) {
            mob.motionX = 0D;
            mob.motionZ = 0D;
            if (target != null && target.isEntityAlive() && mob.getDistanceSqToEntity(target) <= STRIKE_SQ) {
                mob.swingItem();
                mob.attackEntityAsMob(target);
            }
            phase = RECOVER;
            ticks = 0;
            mob.setMobAction(IMobActionSC.ACT_RECOVER);
            mob.setSneaking(true);
            return;
        }
        double step = Math.min(DASH_SPEED, left);
        mob.motionX = dirX * step;
        mob.motionZ = dirZ * step;
        face(yaw);
        ticks++;
    }

    /** Body, head and look all one way (the look helper would turn the head back to the target). */
    private void face(float y) {
        mob.rotationYaw = y;
        mob.rotationYawHead = y;
        mob.renderYawOffset = y;
    }
}
