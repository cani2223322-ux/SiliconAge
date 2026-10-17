package com.sc.settle.caravan.ai;

import java.util.ArrayList;
import java.util.List;

import com.sc.mob.MobCombatSC;
import com.sc.settle.GuardAlertSC;
import com.sc.settle.ISettlementMemberSC;
import com.sc.settle.caravan.CaravanRunSC;
import com.sc.settle.caravan.CaravanSC;
import com.sc.settle.caravan.EntityCaravanGuardSC;
import com.sc.settle.caravan.EntityCaravanMasterSC;
import com.sc.settle.npc.EntityWardenSC;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE K (docs/plan-substrate-tech-f5.md §8.2, rules ПН-13, ПН-14): a caravan guard's target, decided every EntityWardenSC.THINK
 * ticks (each guard on its own tick) - the only task of its targetTasks (the dome's brain, its leash and patrol, are a dome's). Within
 * FIGHT_REACH of its master, in this order:
 * <ol>
 * <li>the caravan's target settlement's offender (GuardAlertSC.target - provoked past the warning, or its local «Враг» in sight: the
 * guard is that settlement's, ПГ-8) - and a player only then or as below;</li>
 * <li>who hits it, then who hits its master (a player too, while he keeps hitting: a fair fight - not in creative, not a FakePlayer);</li>
 * <li>its current target while it is still within FIGHT_REACH + KEEP_OUT of the master and seen within UNSEEN thinks;</li>
 * <li>the nearest of the caravan's ambush (CaravanRunSC.ambushers), then the nearest seen hostile (IMob, not a settlement's creature).
 * With a blade it leaves creepers alone (a gun takes them), as every guard.</li>
 * </ol>
 * The alert shown (EntityWardenSC.showAlert: the lamp, the blade drawn) - ATTACKING while it has a target, else CALM. No master (the
 * caravan over, its chunk asleep): nothing.
 */
public class EntityAICaravanGuardTargetSC extends EntityAIBase {

    /** ПН-13: it fights what comes within FIGHT_REACH of its master, keeps a target to KEEP_OUT past it, drops one unseen for UNSEEN thinks. */
    public static final int FIGHT_REACH = 16, KEEP_OUT = 6, UNSEEN = 6;
    /** How many of the nearest hostiles it checks for a line of sight. */
    private static final int SIGHT_CHECKS = 3;

    private final EntityCaravanGuardSC g;
    private int unseen;

    public EntityAICaravanGuardTargetSC(EntityCaravanGuardSC g) {
        this.g = g;
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
        if ((g.ticksExisted + g.getEntityId()) % EntityWardenSC.THINK == 0) {
            think();
        }
    }

    private void think() {
        CaravanRunSC run = CaravanSC.runOf(g);
        EntityCaravanMasterSC m = run == null ? null : run.master();
        if (m == null) {
            target(null);
            g.showAlert(GuardAlertSC.CALM);
            return;
        }
        EntityLivingBase t = null;
        if (g.settlementId() >= 0) {
            EntityPlayer off = GuardAlertSC.target(g.settlementId(), g, g.worldObj.getTotalWorldTime());
            if (fair(off, m, FIGHT_REACH + KEEP_OUT)) {
                t = off;
            }
        }
        if (t == null && fair(g.getAITarget(), m, FIGHT_REACH + KEEP_OUT)) {
            t = g.getAITarget();
        }
        if (t == null && fair(m.getAITarget(), m, FIGHT_REACH + KEEP_OUT)) {
            t = m.getAITarget();
        }
        EntityLivingBase cur = g.getAttackTarget();
        if (t == null && keep(cur, m)) {
            t = cur;
        }
        if (t == null) {
            t = nearestSeen(near(run.ambushers(), m), false);
        }
        if (t == null) {
            t = hunt(m);
        }
        target(t);
        g.showAlert(t != null ? GuardAlertSC.ATTACKING : GuardAlertSC.CALM);
    }

    private void target(EntityLivingBase t) {
        if (t != g.getAttackTarget()) {
            g.setAttackTarget(t);
            unseen = 0;
        }
    }

    /** `e` may be fought: alive, no settlement's creature, near the master; a player - a real one, not in creative. */
    private boolean fair(EntityLivingBase e, EntityCaravanMasterSC m, double reach) {
        if (e == null || !e.isEntityAlive() || e == g || e instanceof ISettlementMemberSC || e.worldObj != g.worldObj
                || m.getDistanceSqToEntity(e) > reach * reach) {
            return false;
        }
        if (e instanceof EntityPlayer) {
            EntityPlayer p = (EntityPlayer) e;
            return p instanceof EntityPlayerMP && !(p instanceof FakePlayer) && !p.capabilities.isCreativeMode;
        }
        return true;
    }

    /** The current target stays: a fair one within FIGHT_REACH + KEEP_OUT of the master (a player only by the steps above), seen lately. */
    private boolean keep(EntityLivingBase cur, EntityCaravanMasterSC m) {
        if (cur instanceof EntityPlayer || !fair(cur, m, FIGHT_REACH + KEEP_OUT)) {
            return false;
        }
        unseen = g.getEntitySenses().canSee(cur) ? 0 : unseen + 1;
        return unseen <= UNSEEN;
    }

    /** Of `l`, those within FIGHT_REACH + KEEP_OUT of the master that it may fight (the creeper rule). */
    private List<EntityLivingBase> near(List<EntityLiving> l, EntityCaravanMasterSC m) {
        List<EntityLivingBase> out = new ArrayList<EntityLivingBase>();
        for (EntityLiving e : l) {
            if (fair(e, m, FIGHT_REACH + KEEP_OUT) && takes(e)) {
                out.add(e);
            }
        }
        return out;
    }

    /** ПН-13: the nearest seen hostile within FIGHT_REACH of the master, or null. */
    private EntityLivingBase hunt(EntityCaravanMasterSC m) {
        double r = FIGHT_REACH;
        List<?> l = g.worldObj.getEntitiesWithinAABB(IMob.class, m.boundingBox.expand(r, 8D, r));
        List<EntityLivingBase> found = new ArrayList<EntityLivingBase>();
        for (Object o : l) {
            if (o instanceof EntityLivingBase && !(o instanceof EntityPlayer) && fair((EntityLivingBase) o, m, r) && takes((EntityLivingBase) o)) {
                found.add((EntityLivingBase) o);
            }
        }
        return nearestSeen(found, true);
    }

    /** A blade leaves creepers alone (a gun takes them). */
    private boolean takes(EntityLivingBase e) {
        return !(e instanceof EntityCreeper) || MobCombatSC.gun(g) != null;
    }

    /** The nearest of `found` it sees, checking at most SIGHT_CHECKS of the nearest; `mustSee` false - the nearest one anyway. */
    private EntityLivingBase nearestSeen(List<EntityLivingBase> found, boolean mustSee) {
        EntityLivingBase first = nearest(found);
        for (int i = 0; i < SIGHT_CHECKS && !found.isEmpty(); i++) {
            EntityLivingBase best = nearest(found);
            if (g.getEntitySenses().canSee(best)) {
                return best;
            }
            found.remove(best);
        }
        return mustSee ? null : first;
    }

    private EntityLivingBase nearest(List<EntityLivingBase> l) {
        EntityLivingBase best = null;
        double bd = Double.MAX_VALUE;
        for (EntityLivingBase e : l) {
            double d = g.getDistanceSqToEntity(e);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }
}
