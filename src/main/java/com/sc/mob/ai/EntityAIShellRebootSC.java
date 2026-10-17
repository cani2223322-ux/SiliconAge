package com.sc.mob.ai;

import com.sc.mob.EntityHollowShellSC;

import net.minecraft.entity.ai.EntityAIBase;

/**
 * PACKAGE M (docs/plan-substrate-tech-f3.md §4.4, rule ПБ-21): a Hollow Shell «reboots» - while EntityHollowShellSC.rebooting()
 * it stands where it is and does nothing else: every bit (mutex 7: walking, looking, swimming) and the top priority, so
 * no other task of its runs; attackEntityAsMob refuses the blow too. The telegraph (IMobActionSC.ACT_REBOOT) and its end
 * are the Shell's.
 */
public class EntityAIShellRebootSC extends EntityAIBase {

    private final EntityHollowShellSC mob;

    public EntityAIShellRebootSC(EntityHollowShellSC mob) {
        this.mob = mob;
        setMutexBits(7);
    }

    @Override
    public boolean shouldExecute() {
        return mob.rebooting();
    }

    @Override
    public boolean isInterruptible() {
        return false;
    }

    @Override
    public void startExecuting() {
        mob.getNavigator().clearPathEntity();
    }

    @Override
    public void updateTask() {
        mob.getNavigator().clearPathEntity();
        mob.motionX *= 0.3D;
        mob.motionZ *= 0.3D;
        double yaw = Math.toRadians(mob.renderYawOffset);            // its head hangs: it looks at the ground before its feet
        mob.getLookHelper().setLookPosition(mob.posX - Math.sin(yaw) * 1.5D, mob.posY, mob.posZ + Math.cos(yaw) * 1.5D, 10F, 40F);
    }
}
