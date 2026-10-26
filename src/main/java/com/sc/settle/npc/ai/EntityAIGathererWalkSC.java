package com.sc.settle.npc.ai;

import com.sc.settle.GathererSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.npc.EntityGathererSC;
import com.sc.settle.npc.EntityTunerSC;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.world.WorldServer;

/**
 * ПД-5 (docs/plan-ideas-2026-10-26.md, «Ожившие поселения», ★ A): the Gatherer's walk - like the caravan master's (EntityAICaravanWalkSC):
 * the move helper towards its target each tick, no long path search (the spot is within GathererSC.REACH of the dome), the terraces by
 * its stepHeight; stuck (less than STUCK_MOVE in STUCK_TICKS) - the path search for ASSIST_TICKS. OUT: to the spot; there GATHER_TICKS
 * gathering (the arm swinging, the crystal's sparks); HOME: back to the gate - under the dome (its edge - 2) it goes in (leave). A hostile
 * within GathererSC.DANGER (EntityTunerSC.danger) or ATTACKED - it runs home at the Tuner's RUN. Movement only (mutex 1).
 */
public class EntityAIGathererWalkSC extends EntityAIBase {

    /** The spot / the gate this near (across) is reached. */
    public static final double REACH = 2.0D;
    public static final int STUCK_TICKS = 40, ASSIST_TICKS = 80;
    public static final double STUCK_MOVE = 0.5D;

    private final EntityGathererSC g;
    private double lastX, lastZ;
    private int clock, assist;

    public EntityAIGathererWalkSC(EntityGathererSC g) {
        this.g = g;
        setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        return !g.worldObj.isRemote && GathererSC.outing(g) != null;
    }

    @Override
    public boolean continueExecuting() {
        return shouldExecute();
    }

    @Override
    public void startExecuting() {
        lastX = g.posX;
        lastZ = g.posZ;
        clock = assist = 0;
    }

    @Override
    public void resetTask() {
        g.getNavigator().clearPathEntity();
        assist = 0;
    }

    @Override
    public void updateTask() {
        GathererSC.Outing o = GathererSC.outing(g);
        if (o == null) {
            return;
        }
        EntityLivingBase threat = g.danger();
        int st = g.gatherState();
        if (threat != null && st != EntityGathererSC.ATTACKED && st != EntityGathererSC.HOME) {
            g.setGatherState(EntityGathererSC.HOME);            // a hostile near: home
            st = EntityGathererSC.HOME;
        }
        boolean home = st == EntityGathererSC.HOME || st == EntityGathererSC.ATTACKED;
        if (home && underDome(o)) {
            g.leave();                                          // in through the gate: into the dome
            return;
        }
        int[] to = home ? o.gate : o.spot;
        double dx = to[0] + 0.5D - g.posX, dz = to[2] + 0.5D - g.posZ;
        if (!home && dx * dx + dz * dz <= REACH * REACH) {
            gather(o);
            return;
        }
        if (assist > 0) {
            assist--;
            if (!g.getNavigator().noPath()) {
                return;
            }
            assist = 0;
        }
        double speed = home && threat != null || st == EntityGathererSC.ATTACKED ? EntityTunerSC.RUN : EntityTunerSC.WALK;
        g.getMoveHelper().setMoveTo(to[0] + 0.5D, to[1], to[2] + 0.5D, speed);
        if (++clock >= STUCK_TICKS) {
            double mx = g.posX - lastX, mz = g.posZ - lastZ;
            if (mx * mx + mz * mz < STUCK_MOVE * STUCK_MOVE && g.getNavigator().tryMoveToXYZ(to[0] + 0.5D, to[1], to[2] + 0.5D, speed)) {
                assist = ASSIST_TICKS;
            }
            lastX = g.posX;
            lastZ = g.posZ;
            clock = 0;
        }
    }

    /** At the spot: GATHER_TICKS of work (the arm, the sparks), then home. */
    private void gather(GathererSC.Outing o) {
        g.getNavigator().clearPathEntity();
        g.setGatherState(EntityGathererSC.GATHERING);
        g.getLookHelper().setLookPosition(o.spot[0] + 0.5D, o.spot[1] + 0.5D, o.spot[2] + 0.5D, 30F, 30F);
        if (++g.gathered % 20 == 0) {
            g.swingItem();
            if (g.worldObj instanceof WorldServer) {
                ((WorldServer) g.worldObj).func_147487_a(o.druse ? "instantSpell" : "smoke", o.spot[0] + 0.5D, o.spot[1] + 1.0D,
                        o.spot[2] + 0.5D, 6, 0.3D, 0.3D, 0.3D, 0.02D);
            }
        }
        if (g.gathered >= GathererSC.GATHER_TICKS) {
            g.setGatherState(EntityGathererSC.HOME);
        }
    }

    /** Under its dome (the edge - 2). */
    private boolean underDome(GathererSC.Outing o) {
        SettlementRegistrySC.Settlement s = g.link().get();
        if (s == null) {
            return false;
        }
        double r = Math.max(2, s.radius - 2);
        return s.distSq(g.posX, g.posZ) <= r * r;
    }
}
