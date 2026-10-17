package com.sc.mob.ai;

import com.sc.mob.EntityWarlordSC;
import com.sc.mob.IMobActionSC;
import com.sc.mob.MobCombatSC;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;

/**
 * PACKAGE M (docs/plan-substrate-tech-f4.md §7.2, rules ПС-9, ПС-32: «как Штурмовик»): the Warlord's dash - the Trooper's
 * (EntityAITrooperDashSC) with the Warlord's numbers: only with the blade in hand, the target DASH_FROM..DASH_TO blocks off, in
 * sight and about level with it, no piece of its armour suppressed. The cue (DASH_CUE_TICKS standing, IMobActionSC.ACT_DASH,
 * the red sparks every client draws), the dash (DASH_LENGTH straight at where the target stood, a blow at its end), the
 * recovery (RECOVER_TICKS crouched, ACT_RECOVER: a blow from behind x BACKSTAB_MUL - EntityWarlordSC.attackEntityFrom).
 * Movement and look its own (mutex 3), above the melee; the next one DASH_COOLDOWN after this one began.
 */
public class EntityAIWarlordDashSC extends EntityAIBase {

    /** Blocks a tick of the dash; ticks it may last at most; the blow's reach at its end (squared). */
    private static final double DASH_SPEED = EntityAITrooperDashSC.DASH_SPEED, STRIKE_SQ = EntityAITrooperDashSC.STRIKE_SQ;
    private static final int DASH_MAX_TICKS = 6;
    private static final int CUE = 0, DASH = 1, RECOVER = 2, DONE = 3;

    private final EntityWarlordSC mob;
    private EntityLivingBase target;
    private int phase = DONE, ticks, readyAt;
    private double dirX, dirZ, fromX, fromZ;
    private float yaw;

    public EntityAIWarlordDashSC(EntityWarlordSC mob) {
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
        if (d < EntityWarlordSC.DASH_FROM || d > EntityWarlordSC.DASH_TO || !mob.getEntitySenses().canSee(t)) {
            return false;
        }
        target = t;
        return true;
    }

    @Override
    public void startExecuting() {
        phase = CUE;
        ticks = 0;
        readyAt = mob.ticksExisted + EntityWarlordSC.DASH_COOLDOWN;
        mob.getNavigator().clearPathEntity();
        mob.setMobAction(IMobActionSC.ACT_DASH);
        mob.playSound("mob.irongolem.throw", 1F, 1.4F);
    }

    @Override
    public boolean continueExecuting() {
        return phase != DONE && mob.isEntityAlive();
    }

    @Override
    public boolean isInterruptible() {
        return phase == CUE;
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

    /** It is in a dash or its recovery now (the weapon stays in hand meanwhile). */
    public boolean running() {
        return phase == DASH || phase == RECOVER;
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
                if (++ticks >= EntityWarlordSC.RECOVER_TICKS) {
                    phase = DONE;
                }
                break;
            default:
                break;
        }
    }

    private void cue() {
        if (target == null || !target.isEntityAlive() || mob.mobArmor().anySuppressed() || MobCombatSC.blade(mob) == null) {
            phase = DONE;
            return;
        }
        mob.getNavigator().clearPathEntity();
        face(MobCombatSC.yawTo(mob.posX, mob.posZ, target.posX, target.posZ));
        if (++ticks < EntityWarlordSC.DASH_CUE_TICKS) {
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
        mob.playSound("mob.irongolem.throw", 1.2F, 0.7F);
    }

    private void dash() {
        double gone = Math.sqrt((mob.posX - fromX) * (mob.posX - fromX) + (mob.posZ - fromZ) * (mob.posZ - fromZ));
        double left = EntityWarlordSC.DASH_LENGTH - gone;
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

    private void face(float y) {
        mob.rotationYaw = y;
        mob.rotationYawHead = y;
        mob.renderYawOffset = y;
    }
}
