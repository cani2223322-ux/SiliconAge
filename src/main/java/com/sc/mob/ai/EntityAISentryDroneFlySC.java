package com.sc.mob.ai;

import com.sc.mob.EntitySentryDroneSC;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;

/**
 * PACKAGE X (docs/plan-substrate-tech-f3.md §4.7): the feral sentry drone's flight - no path finding, its own steering and
 * vanilla's collisions, never past its leash (its home's middle +- leash() across, +- HOME_DY up and down: a wanted point
 * outside is pulled back to the edge). With a target it hangs HOVER_DIST from it on its own side, HOVER_UP over its eyes;
 * without, it patrols: a new point of its home every few seconds, one whose straight way is clear (vanilla's ghast tests the
 * same), slowly. Movement only (mutex 1): the look and the beam are EntityAISentryDroneBeamSC's.
 */
public class EntityAISentryDroneFlySC extends EntityAIBase {

    /** How fast its velocity turns to the wanted one (a share a tick); the patrol's pace (of FLY_SPEED). */
    private static final double STEER = 0.1D, PATROL = 0.5D;

    private final EntitySentryDroneSC drone;
    private double wx, wy, wz;
    private int patrolIn;

    public EntityAISentryDroneFlySC(EntitySentryDroneSC drone) {
        this.drone = drone;
        setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        return drone.isEntityAlive();
    }

    @Override
    public boolean continueExecuting() {
        return drone.isEntityAlive();
    }

    @Override
    public void updateTask() {
        if (!drone.hasOwnHome()) {
            return;                                             // its first tick: the home comes with onLivingUpdate
        }
        EntityLivingBase t = drone.getAttackTarget();
        double tx, ty, tz, speed = EntitySentryDroneSC.FLY_SPEED;
        if (t != null && t.isEntityAlive()) {
            double dx = drone.posX - t.posX, dz = drone.posZ - t.posZ, d = MathHelper.sqrt_double(dx * dx + dz * dz);
            if (d < 0.1D) {
                dx = 1D;
                dz = 0D;
                d = 1D;
            }
            tx = t.posX + dx / d * EntitySentryDroneSC.HOVER_DIST;
            tz = t.posZ + dz / d * EntitySentryDroneSC.HOVER_DIST;
            ty = t.posY + t.getEyeHeight() + EntitySentryDroneSC.HOVER_UP;
        } else {
            double dx = wx - drone.posX, dy = wy - drone.posY, dz = wz - drone.posZ;
            if (--patrolIn <= 0 || dx * dx + dy * dy + dz * dz < 1D) {
                pickPatrol();
            }
            tx = wx;
            ty = wy;
            tz = wz;
            speed *= PATROL;
        }
        // the leash: across within leash() of the home's middle, up and down within HOME_DY
        double hx = drone.homeX() + 0.5D, hz = drone.homeZ() + 0.5D, lx = tx - hx, lz = tz - hz, l = MathHelper.sqrt_double(lx * lx + lz * lz);
        double leash = drone.leash() - 1D;
        if (l > leash) {
            tx = hx + lx / l * leash;
            tz = hz + lz / l * leash;
        }
        ty = Math.max(drone.homeY() - EntitySentryDroneSC.HOME_DY, Math.min(drone.homeY() + EntitySentryDroneSC.HOME_DY, ty));
        steer(tx, ty, tz, speed);
    }

    /** A new patrol point in its home, its straight way clear; up to four tries a time. */
    private void pickPatrol() {
        patrolIn = 60 + drone.getRNG().nextInt(80);
        int r = Math.max(2, drone.leash() - EntitySentryDroneSC.HOME_MARGIN);
        for (int t = 0; t < 4; t++) {
            double x = drone.homeX() + 0.5D + (drone.getRNG().nextDouble() * 2D - 1D) * r;
            double z = drone.homeZ() + 0.5D + (drone.getRNG().nextDouble() * 2D - 1D) * r;
            double y = drone.homeY() + 0.5D + (drone.getRNG().nextDouble() * 2D - 1D) * 2D;
            if (clearWay(x, y, z)) {
                wx = x;
                wy = y;
                wz = z;
                return;
            }
        }
        wx = drone.posX;                                        // nowhere to go: hang where it is
        wy = drone.posY;
        wz = drone.posZ;
    }

    /** Vanilla's ghast's test: its box swept along the straight line to (x, y, z) meets no block. */
    private boolean clearWay(double x, double y, double z) {
        double dx = (x - drone.posX), dy = (y - drone.posY), dz = (z - drone.posZ);
        double d = MathHelper.sqrt_double(dx * dx + dy * dy + dz * dz);
        if (d < 1.0E-3D) {
            return true;
        }
        dx /= d;
        dy /= d;
        dz /= d;
        AxisAlignedBB box = drone.boundingBox.copy();
        for (int i = 1; i < d; i++) {
            box.offset(dx, dy, dz);
            if (!drone.worldObj.getCollidingBoundingBoxes(drone, box).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Turns its velocity towards (x, y, z) at up to `speed`; it slows as it arrives; over what it bumps into. */
    private void steer(double x, double y, double z, double speed) {
        double dx = x - drone.posX, dy = y - drone.posY, dz = z - drone.posZ;
        double d = MathHelper.sqrt_double(dx * dx + dy * dy + dz * dz);
        double s = d < 2D ? speed * d / 2D : speed;
        double vx = d > 1.0E-3D ? dx / d * s : 0D, vy = d > 1.0E-3D ? dy / d * s : 0D, vz = d > 1.0E-3D ? dz / d * s : 0D;
        drone.motionX += (vx - drone.motionX) * STEER;
        drone.motionY += (vy - drone.motionY) * STEER;
        drone.motionZ += (vz - drone.motionZ) * STEER;
        if (drone.isCollidedHorizontally) {
            drone.motionY += 0.04D;
        }
        if (drone.getAttackTarget() == null && drone.motionX * drone.motionX + drone.motionZ * drone.motionZ > 1.0E-4D) {
            drone.rotationYaw = drone.renderYawOffset = (float) (Math.atan2(drone.motionZ, drone.motionX) * 180D / Math.PI) - 90F;
        }
    }
}
