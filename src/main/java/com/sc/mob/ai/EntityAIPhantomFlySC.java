package com.sc.mob.ai;

import com.sc.mob.EntityPhantomSC;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.util.MathHelper;

/**
 * PACKAGE N (docs/plan-substrate-tech-f4.md §7.5, rule ПС-35): the Induction Phantom's flight - no path, no walls (its noClip):
 * with a target it steers straight at the target's chest at FLY_SPEED and hangs there (EntityAIPhantomStrikeSC strikes); without
 * one it drifts at DRIFT_SPEED to a point a few blocks off, DRIFT_MIN_UP..DRIFT_MAX_UP over the ground of that column (the height
 * map, only where the chunk is loaded - never loads one), a new point when it is there or after DRIFT_GIVE_UP ticks. The steering
 * eases the motion towards the wanted one (a ghost's glide, no jerks); the body faces the way it goes. Movement (mutex 1).
 */
public class EntityAIPhantomFlySC extends EntityAIBase {

    /** Drift: how far off a new point is (horizontally), when it is reached (squared), when given up. */
    private static final int DRIFT_RANGE = 8, DRIFT_GIVE_UP = 120;
    private static final double REACHED_SQ = 1.0D;
    /** How close it hangs to its prey's chest; how much of the wanted motion it takes a tick. */
    private static final double HANG = 0.6D, EASE = 0.3D;

    private final EntityPhantomSC phantom;
    private double wx, wy, wz;
    private int drifting = DRIFT_GIVE_UP;

    public EntityAIPhantomFlySC(EntityPhantomSC phantom) {
        this.phantom = phantom;
        setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        return true;
    }

    @Override
    public boolean continueExecuting() {
        return true;
    }

    @Override
    public void updateTask() {
        EntityLivingBase t = phantom.getAttackTarget();
        double tx, ty, tz, speed;
        if (t != null && t.isEntityAlive()) {
            tx = t.posX;
            ty = t.posY + t.height * 0.5D - phantom.height * 0.5D;
            tz = t.posZ;
            speed = EntityPhantomSC.FLY_SPEED;
            drifting = DRIFT_GIVE_UP;                           // a fresh drift point once the hunt is over
        } else {
            if (++drifting >= DRIFT_GIVE_UP || phantom.getDistanceSq(wx, wy, wz) <= REACHED_SQ) {
                pickDrift();
            }
            tx = wx;
            ty = wy;
            tz = wz;
            speed = EntityPhantomSC.DRIFT_SPEED;
        }
        double dx = tx - phantom.posX, dy = ty - phantom.posY, dz = tz - phantom.posZ;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double vx = 0D, vy = 0D, vz = 0D;
        if (d > (t != null ? HANG : 0.2D)) {
            double k = Math.min(speed, d) / d;
            vx = dx * k;
            vy = dy * k;
            vz = dz * k;
        }
        phantom.motionX += (vx - phantom.motionX) * EASE;
        phantom.motionY += (vy - phantom.motionY) * EASE;
        phantom.motionZ += (vz - phantom.motionZ) * EASE;
        double fx = t != null ? t.posX - phantom.posX : vx, fz = t != null ? t.posZ - phantom.posZ : vz;
        if (fx * fx + fz * fz > 1.0E-4D) {
            float yaw = (float) (Math.atan2(fz, fx) * 180D / Math.PI) - 90F;
            phantom.rotationYaw = phantom.renderYawOffset = phantom.rotationYawHead = yaw;
        }
    }

    /** A new drift point near: a few blocks off, a little over the ground of its column (where that is known). */
    private void pickDrift() {
        drifting = 0;
        wx = phantom.posX + (phantom.getRNG().nextInt(2 * DRIFT_RANGE + 1) - DRIFT_RANGE);
        wz = phantom.posZ + (phantom.getRNG().nextInt(2 * DRIFT_RANGE + 1) - DRIFT_RANGE);
        int x = MathHelper.floor_double(wx), z = MathHelper.floor_double(wz);
        double ground = phantom.posY - EntityPhantomSC.DRIFT_MIN_UP;
        if (phantom.worldObj.blockExists(x, 64, z)) {
            ground = phantom.worldObj.getHeightValue(x, z);
        }
        int up = EntityPhantomSC.DRIFT_MIN_UP + phantom.getRNG().nextInt(EntityPhantomSC.DRIFT_MAX_UP - EntityPhantomSC.DRIFT_MIN_UP + 1);
        wy = Math.max(2D, Math.min(phantom.worldObj.getHeight() - 4D, ground + up));
    }
}
