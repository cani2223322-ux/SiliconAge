package com.sc.settle.caravan.ai;

import java.util.List;

import com.sc.settle.caravan.CaravanRunSC;
import com.sc.settle.caravan.CaravanSC;
import com.sc.settle.caravan.EntityCaravanGuardSC;
import com.sc.settle.caravan.EntityCaravanMasterSC;
import com.sc.settle.npc.EntityTunerSC;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;

/**
 * PACKAGE K (docs/plan-substrate-tech-f5.md §8.2, rules ПН-13, ПН-14): the caravan master's walk - its caravan's route (CaravanRunSC:
 * a waypoint every CaravanRunPathSC.STEP blocks on the plateau, to the gate) by the move helper alone: setMoveTo each tick towards the
 * next waypoint, passed within REACH; no path search on the way (hundreds of blocks), the terraces by its stepHeight. Stuck - moved less
 * than STUCK_MOVE in STUCK_TICKS: the path search for that one leg (the waypoint is STEP away - within its follow range) for at most
 * ASSIST_TICKS; that failing too, the waypoint is skipped.
 * <p>
 * While its caravan is ambushed (CaravanRunSC.holds) or a hostile is within the Tuner's FLEE_RADIUS (danger): it stands behind its
 * guards - HIDE_BEHIND past their middle on the far side from the threat (none alive: HIDE_AWAY from the threat), a short path set again
 * every REPATH ticks, facing the threat. Movement only (mutex 1): its look is free for watching players.
 */
public class EntityAICaravanWalkSC extends EntityAIBase {

    /** A waypoint this near (across) is passed. */
    public static final double REACH = 1.5D;
    /** The hiding spot: this far past the guards' middle from the threat; with no guard, this far from the threat. */
    public static final double HIDE_BEHIND = 3D, HIDE_AWAY = 8D;
    /** Stuck: less than STUCK_MOVE blocks in STUCK_TICKS - the path search's leg for ASSIST_TICKS at most. */
    public static final int STUCK_TICKS = 40, ASSIST_TICKS = 80;
    public static final double STUCK_MOVE = 0.5D;
    /** The hiding path set again this often (ticks). */
    private static final int REPATH = 10;

    private final EntityCaravanMasterSC m;
    private double lastX, lastZ;
    private int clock, assist, repath;
    private boolean hiding;

    public EntityAICaravanWalkSC(EntityCaravanMasterSC m) {
        this.m = m;
        setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        return CaravanSC.runOf(m) != null;
    }

    @Override
    public boolean continueExecuting() {
        return shouldExecute();
    }

    @Override
    public void startExecuting() {
        lastX = m.posX;
        lastZ = m.posZ;
        clock = assist = repath = 0;
        hiding = false;
    }

    @Override
    public void resetTask() {
        m.getNavigator().clearPathEntity();
        hiding = false;
        assist = 0;
    }

    @Override
    public void updateTask() {
        CaravanRunSC run = CaravanSC.runOf(m);
        if (run == null) {
            return;
        }
        EntityLivingBase threat = run.holds() ? run.threat() : null;
        if (threat == null) {
            threat = m.danger();
        }
        if (threat != null || run.holds()) {
            hide(run, threat);
            return;
        }
        if (hiding) {                                           // the danger over: back on the route
            hiding = false;
            m.getNavigator().clearPathEntity();
        }
        int[] p = run.waypoint(m.worldObj);
        if (p != null && dist2(p) <= REACH * REACH) {
            run.passed();
            p = run.waypoint(m.worldObj);
            clock = 0;
            assist = 0;
            m.getNavigator().clearPathEntity();
        }
        if (p == null) {
            return;                                             // the route's end: the run takes it in at its next step
        }
        if (assist > 0) {
            assist--;
            if (!m.getNavigator().noPath()) {
                return;                                         // the leg's path walks it
            }
            assist = 0;
        }
        m.getMoveHelper().setMoveTo(p[0] + 0.5D, p[1], p[2] + 0.5D, EntityCaravanMasterSC.CARAVAN_WALK);
        if (++clock >= STUCK_TICKS) {
            double dx = m.posX - lastX, dz = m.posZ - lastZ;
            if (dx * dx + dz * dz < STUCK_MOVE * STUCK_MOVE) {
                if (m.getNavigator().tryMoveToXYZ(p[0] + 0.5D, p[1], p[2] + 0.5D, EntityCaravanMasterSC.CARAVAN_WALK)) {
                    assist = ASSIST_TICKS;
                } else {
                    run.passed();                               // no way round to it: the next one
                }
            }
            lastX = m.posX;
            lastZ = m.posZ;
            clock = 0;
        }
    }

    /** ПН-13: behind its guards, facing the threat (a short path, set again every REPATH ticks). */
    private void hide(CaravanRunSC run, EntityLivingBase threat) {
        hiding = true;
        assist = 0;
        clock = 0;
        lastX = m.posX;
        lastZ = m.posZ;
        if (threat == null) {
            m.getNavigator().clearPathEntity();                 // ambushed, nobody in sight: stand
            return;
        }
        m.getLookHelper().setLookPositionWithEntity(threat, 30F, 30F);
        if (--repath > 0) {
            return;
        }
        repath = REPATH;
        List<EntityCaravanGuardSC> gs = run.guards();
        double x, z;
        if (gs.isEmpty()) {
            double dx = m.posX - threat.posX, dz = m.posZ - threat.posZ, d = Math.max(0.1D, Math.sqrt(dx * dx + dz * dz));
            x = m.posX + dx / d * HIDE_AWAY;
            z = m.posZ + dz / d * HIDE_AWAY;
        } else {
            double gx = 0D, gz = 0D;
            for (EntityCaravanGuardSC g : gs) {
                gx += g.posX;
                gz += g.posZ;
            }
            gx /= gs.size();
            gz /= gs.size();
            double dx = gx - threat.posX, dz = gz - threat.posZ, d = Math.max(0.1D, Math.sqrt(dx * dx + dz * dz));
            x = gx + dx / d * HIDE_BEHIND;
            z = gz + dz / d * HIDE_BEHIND;
        }
        double ox = x - m.posX, oz = z - m.posZ;
        if (ox * ox + oz * oz > 1.5D * 1.5D) {
            m.getNavigator().tryMoveToXYZ(x, m.posY, z, EntityTunerSC.RUN);
        }
    }

    private double dist2(int[] p) {
        double dx = p[0] + 0.5D - m.posX, dz = p[2] + 0.5D - m.posZ;
        return dx * dx + dz * dz;
    }
}
