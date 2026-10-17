package com.sc.settle.npc.ai;

import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.npc.EntityWardenSC;

import net.minecraft.entity.ai.EntityAIBase;

/**
 * PACKAGE N (rule ПГ-5): a guard's duty when there is nothing else to do - EntityWardenSC.role by the clock: at the
 * gate, at its post (a Senior: a tower), at the Heart at night - it walks there and stands; on patrol - round the
 * dome from waypoint to waypoint, each STEP_ARC blocks on along the ring at a random distance from the dome's edge
 * to PATROL_OUT past it (Sentries of even slots clockwise, odd ones the other way), a short stop at each. A waypoint
 * whose column isn't loaded is skipped (never a chunk loaded for it). Movement only (mutex 1): the look tasks watch
 * players meanwhile.
 */
public class EntityAIWardenPatrolSC extends EntityAIBase {

    /** Along the ring between two waypoints (blocks of arc). */
    public static final double STEP_ARC = 14D;
    /** At a post: this close counts as there. At a waypoint. */
    private static final double AT_POST = 1.5D, AT_WAYPOINT = 2.5D;
    /** A leg gives up after this long or this many failed paths; how often a lost path is set again; the stop at a waypoint. */
    private static final int GIVE_UP = 500, FAILS = 4, REPATH = 20, STOP = 40, STOP_RANDOM = 60;
    /** A post it can't get nearer to: it waits this long before it tries again (it stands where it got). */
    private static final int POST_RETRY = 200;

    private final EntityWardenSC g;
    private int[] goal;
    private double angle = Double.NaN;
    private int ticks, fails, wait, postWait, lookFor;
    private double postDist = Double.MAX_VALUE, lookYaw;

    public EntityAIWardenPatrolSC(EntityWardenSC g) {
        this.g = g;
        setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        return g.getAttackTarget() == null && !g.returning() && g.link().get() != null;
    }

    @Override
    public boolean continueExecuting() {
        return shouldExecute();
    }

    @Override
    public void startExecuting() {
        goal = null;
        wait = 0;
    }

    @Override
    public void resetTask() {
        g.getNavigator().clearPathEntity();
        goal = null;
    }

    @Override
    public void updateTask() {
        SettlementRegistrySC.Settlement s = g.link().get();
        if (s == null) {
            return;
        }
        int role = EntityWardenSC.role(s, g.slot(), NpcLinkSC.night(g.worldObj.getWorldTime()));
        int[] post = EntityWardenSC.dutyPoint(s, g.slot(), role);
        if (post != null) {
            stand(post);
            return;
        }
        if (goal != null && (NpcLinkSC.near(g, goal, AT_WAYPOINT) || ticks > GIVE_UP || fails >= FAILS)) {
            goal = null;
            wait = STOP + g.getRNG().nextInt(STOP_RANDOM);
            g.getNavigator().clearPathEntity();
        }
        if (goal == null) {
            if (wait > 0) {
                wait--;
                lookAround();
                return;
            }
            goal = waypoint(s);
            ticks = 0;
            fails = 0;
            if (goal == null) {
                wait = STOP;
                return;
            }
            NpcNavSC.walkTo(g, goal, EntityWardenSC.PATROL);
            return;
        }
        if (++ticks % REPATH == 0 && g.getNavigator().noPath() && !NpcNavSC.walkTo(g, goal, EntityWardenSC.PATROL)) {
            fails++;
        }
    }

    /** At a post: walk there, then stand and look round. */
    private void stand(int[] post) {
        goal = null;
        if (NpcLinkSC.near(g, post, AT_POST)) {
            postDist = Double.MAX_VALUE;
            if (!g.getNavigator().noPath()) {
                g.getNavigator().clearPathEntity();
            }
            lookAround();
            return;
        }
        if (postWait > 0) {
            postWait--;
            return;
        }
        if (++ticks % REPATH == 1 && g.getNavigator().noPath()) {
            double d = NpcLinkSC.distSq(g, post);
            if (d > postDist - 1D) {
                postWait = POST_RETRY;                          // no nearer than last time: as close as it gets
                postDist = Double.MAX_VALUE;
                return;
            }
            postDist = d;
            NpcNavSC.walkTo(g, post, EntityWardenSC.PATROL);
        }
    }

    /** Standing: it looks one way, then another (a player near: the look tasks, updated after this one, win). */
    private void lookAround() {
        if (--lookFor <= 0) {
            lookFor = 40 + g.getRNG().nextInt(80);
            lookYaw = g.getRNG().nextDouble() * Math.PI * 2D;
        }
        g.getLookHelper().setLookPosition(g.posX + Math.cos(lookYaw) * 8D, g.posY + g.getEyeHeight(), g.posZ + Math.sin(lookYaw) * 8D,
                10F, (float) g.getVerticalFaceSpeed());
    }

    /** The next waypoint on the ring, or null (its columns not loaded). */
    private int[] waypoint(SettlementRegistrySC.Settlement s) {
        double cx = s.x + 0.5D, cz = s.z + 0.5D;
        if (Double.isNaN(angle)) {
            angle = Math.atan2(g.posZ - cz, g.posX - cx);
        }
        int dir = g.slot() % 2 == 0 ? 1 : -1;
        for (int i = 0; i < 3; i++) {
            double r = s.radius + 2 + g.getRNG().nextInt(Math.max(1, EntityWardenSC.PATROL_OUT - 1));
            angle += dir * STEP_ARC / r;
            int x = (int) Math.floor(cx + Math.cos(angle) * r), z = (int) Math.floor(cz + Math.sin(angle) * r);
            int y = NpcNavSC.groundY(g.worldObj, x, z);
            if (y > 0) {
                return new int[]{x, y, z};
            }
        }
        return null;
    }
}
