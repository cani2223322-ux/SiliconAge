package com.sc.mob.ai;

import com.sc.mob.EntityStriderSC;
import com.sc.mob.IMobActionSC;
import com.sc.util.SoundsSC;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;

/**
 * PACKAGE N (docs/plan-substrate-tech-f4.md §7.8, rules ПС-4, ПС-9, ПС-38): the Arc Strider's chain lightning. A wired target
 * (EntityStriderSC.wired) within ARC_RANGE in sight (checked every SIGHT_CHECK ticks), the cooldown over - it stops and charges
 * ARC_CHARGE ticks (IMobActionSC.ACT_ARC: the arc between its electrodes swells, the hum rises - the cue to break the line), then
 * EntityStriderSC.arc if the target is still wired, within reach and in sight at that very moment (else the charge is spent for
 * nothing: the cooldown all the same). Out of reach or sight it walks up (a path every REPATH ticks); a target that is no longer
 * wired (it took its armour off, went creative) is let go. Movement and look (mutex 3).
 */
public class EntityAIStriderArcSC extends EntityAIBase {

    private static final int REPATH = 10, SIGHT_CHECK = 10;

    private final EntityStriderSC strider;
    private int charge = -1, cooldown, repath, sightIn;
    private boolean seen;

    public EntityAIStriderArcSC(EntityStriderSC strider) {
        this.strider = strider;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        EntityLivingBase t = strider.getAttackTarget();
        if (t != null && !EntityStriderSC.wired(t)) {
            strider.setAttackTarget(null);                      // nothing for its lightning there
            return false;
        }
        return t != null && !strider.collapsed();
    }

    @Override
    public boolean continueExecuting() {
        return charge >= 0 && !strider.collapsed() || shouldExecute();
    }

    @Override
    public void startExecuting() {
        repath = 0;
        sightIn = 0;
    }

    @Override
    public void resetTask() {
        charge = -1;
        if (strider.mobAction() == IMobActionSC.ACT_ARC) {
            strider.setMobAction(IMobActionSC.ACT_NONE);
        }
        strider.getNavigator().clearPathEntity();
    }

    @Override
    public void updateTask() {
        if (cooldown > 0) {
            cooldown--;
        }
        EntityLivingBase t = strider.getAttackTarget();
        if (charge >= 0) {
            charging(t);
            return;
        }
        if (t == null) {
            return;
        }
        strider.getLookHelper().setLookPositionWithEntity(t, 30F, 30F);
        if (--sightIn <= 0) {
            seen = strider.getEntitySenses().canSee(t);
            sightIn = SIGHT_CHECK;
        }
        double r = EntityStriderSC.ARC_RANGE;
        boolean inReach = strider.getDistanceSqToEntity(t) <= r * r;
        if (inReach && seen && cooldown <= 0) {
            charge = 0;
            strider.getNavigator().clearPathEntity();
            strider.setMobAction(IMobActionSC.ACT_ARC);
            SoundsSC.play(strider.worldObj, strider.posX, strider.posY + 3D, strider.posZ, "emp.hum", 1F, 1.2F);
            return;
        }
        if (--repath <= 0) {
            repath = REPATH;
            if (!inReach || !seen || strider.getDistanceSqToEntity(t) > r * r * 0.4D) {
                strider.getNavigator().tryMoveToEntityLiving(t, 1.0D);
            } else {
                strider.getNavigator().clearPathEntity();       // close enough: it waits out the cooldown where it stands
            }
        }
    }

    /** A tick of the charge: standing, facing it; at its end the lightning if the target is still there. */
    private void charging(EntityLivingBase t) {
        if (strider.mobAction() != IMobActionSC.ACT_ARC) {
            charge = -1;                                        // a Pulse collapsed the arc meanwhile: the charge is gone
            cooldown = EntityStriderSC.ARC_COOLDOWN;
            return;
        }
        strider.getNavigator().clearPathEntity();
        if (t != null) {
            strider.getLookHelper().setLookPositionWithEntity(t, 30F, 30F);
        }
        if (++charge < EntityStriderSC.ARC_CHARGE) {
            return;
        }
        double r = EntityStriderSC.ARC_RANGE + 2D;
        if (t != null && EntityStriderSC.wired(t) && strider.getDistanceSqToEntity(t) <= r * r && strider.canEntityBeSeen(t)) {
            strider.arc(t);
        }
        charge = -1;
        cooldown = EntityStriderSC.ARC_COOLDOWN;
        strider.setMobAction(IMobActionSC.ACT_NONE);
    }
}
