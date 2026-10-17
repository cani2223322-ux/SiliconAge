package com.sc.mob.ai;

import com.sc.mob.EntityHollowShellSC;
import com.sc.mob.EntityRepairDroneSC;
import com.sc.mob.IMobActionSC;
import com.sc.mob.MobArmorSC;
import com.sc.mob.MobCombatSC;

import net.minecraft.entity.ai.EntityAIBase;

/**
 * PACKAGE M (docs/plan-substrate-tech-f3.md §4.4): a Repair Drone's whole life - it always runs (all the bits; the drone
 * has no other task). Each tick: its Shell (EntityRepairDroneSC.shell(), which also counts an orphan's ticks), the flight to
 * a point HOVER_Y over the Shell's feet circling it at ORBIT (each drone its own phase, by entity id), steered by its motion
 * (no path finding: it flies). Every REPAIR_EVERY ticks, within REPAIR_REACH of a Shell whose reserve isn't full and that no
 * siphon is draining (MobArmorSC.streamUntil - recharge would refuse anyway), REPAIR_UNITS into the Shell's reserve and the
 * beam telegraph IMobActionSC.ACT_REPAIR (every client draws the beam from the drone to its Shell); otherwise ACT_NONE.
 */
public class EntityAIDroneRepairSC extends EntityAIBase {

    /** How hard it steers (blocks a tick squared, a tick); how fast it circles (radians a tick). */
    private static final double ACCEL = 0.08D, SPIN = 0.06D;

    private final EntityRepairDroneSC drone;

    public EntityAIDroneRepairSC(EntityRepairDroneSC drone) {
        this.drone = drone;
        setMutexBits(7);
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
    public boolean isInterruptible() {
        return false;
    }

    @Override
    public void resetTask() {
        if (drone.mobAction() == IMobActionSC.ACT_REPAIR) {
            drone.setMobAction(IMobActionSC.ACT_NONE);
        }
    }

    @Override
    public void updateTask() {
        EntityHollowShellSC shell = drone.shell();
        if (drone.isDead) {
            return;
        }
        if (shell == null) {
            drone.setMobAction(IMobActionSC.ACT_NONE);
            drone.motionY += (Math.sin(drone.ticksExisted * 0.1D) * 0.01D) - drone.motionY * 0.1D;   // hangs there, lost
            return;
        }
        double a = drone.ticksExisted * SPIN + drone.getEntityId() * 2.1D;
        steer(shell.posX + Math.cos(a) * EntityRepairDroneSC.ORBIT, shell.posY + EntityRepairDroneSC.HOVER_Y + Math.sin(a * 0.7D) * 0.15D,
                shell.posZ + Math.sin(a) * EntityRepairDroneSC.ORBIT);
        float yaw = MobCombatSC.yawTo(drone.posX, drone.posZ, shell.posX, shell.posZ);
        drone.rotationYaw = yaw;
        drone.rotationYawHead = yaw;
        drone.renderYawOffset = yaw;
        MobArmorSC armor = shell.mobArmor();
        long now = drone.worldObj.getTotalWorldTime();
        boolean reach = drone.getDistanceSqToEntity(shell) <= EntityRepairDroneSC.REPAIR_REACH * EntityRepairDroneSC.REPAIR_REACH;
        boolean needs = armor.reserveMax > 0F && armor.reserve < armor.reserveMax && armor.streamUntil <= now;
        if (!reach || !needs) {
            drone.setMobAction(IMobActionSC.ACT_NONE);
            return;
        }
        drone.setMobAction(IMobActionSC.ACT_REPAIR);
        if ((now + drone.getEntityId()) % EntityRepairDroneSC.REPAIR_EVERY == 0) {
            armor.recharge(EntityRepairDroneSC.REPAIR_UNITS);
        }
    }

    /** Accelerates towards (x, y, z), at most FLY_SPEED a tick. */
    private void steer(double x, double y, double z) {
        double dx = x - drone.posX, dy = y - drone.posY, dz = z - drone.posZ, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-3D) {
            return;
        }
        double k = Math.min(ACCEL, len * 0.25D) / len;
        drone.motionX += dx * k;
        drone.motionY += dy * k;
        drone.motionZ += dz * k;
        double v = Math.sqrt(drone.motionX * drone.motionX + drone.motionY * drone.motionY + drone.motionZ * drone.motionZ);
        if (v > EntityRepairDroneSC.FLY_SPEED) {
            double s = EntityRepairDroneSC.FLY_SPEED / v;
            drone.motionX *= s;
            drone.motionY *= s;
            drone.motionZ *= s;
        }
    }
}
