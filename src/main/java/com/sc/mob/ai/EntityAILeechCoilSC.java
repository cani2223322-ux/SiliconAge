package com.sc.mob.ai;

import com.sc.mob.EntityLeechSC;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;

/**
 * PACKAGE N (docs/plan-substrate-tech-f4.md §7.6, rule ПС-36): the Current Leech crawls up to its target (a path re-asked every
 * REPATH ticks) and, at its feet (EntityLeechSC.COIL_REACH across, COIL_DY up or down) with its cooldown over, coils round it
 * (EntityLeechSC.coil - the coil itself runs in the leech's own tick). On cooldown it keeps at the prey's heels. Movement and
 * look (mutex 3); nothing while stunned or coiled.
 */
public class EntityAILeechCoilSC extends EntityAIBase {

    private static final int REPATH = 10;

    private final EntityLeechSC leech;
    private int repath;

    public EntityAILeechCoilSC(EntityLeechSC leech) {
        this.leech = leech;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        EntityLivingBase t = leech.getAttackTarget();
        return t != null && t.isEntityAlive() && !leech.coiled() && !leech.stunned();
    }

    @Override
    public boolean continueExecuting() {
        return shouldExecute();
    }

    @Override
    public void startExecuting() {
        repath = 0;
    }

    @Override
    public void resetTask() {
        leech.getNavigator().clearPathEntity();
    }

    @Override
    public void updateTask() {
        EntityLivingBase t = leech.getAttackTarget();
        if (t == null) {
            return;
        }
        leech.getLookHelper().setLookPositionWithEntity(t, 30F, 30F);
        double dx = t.posX - leech.posX, dz = t.posZ - leech.posZ, dy = t.posY - leech.posY;
        if (dx * dx + dz * dz <= EntityLeechSC.COIL_REACH * EntityLeechSC.COIL_REACH && Math.abs(dy) <= EntityLeechSC.COIL_DY && leech.mayCoil()) {
            leech.coil(t);
            return;
        }
        if (--repath <= 0) {
            repath = REPATH;
            leech.getNavigator().tryMoveToEntityLiving(t, 1.0D);
        }
    }
}
