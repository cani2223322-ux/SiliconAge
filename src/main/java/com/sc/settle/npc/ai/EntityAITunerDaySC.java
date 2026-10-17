package com.sc.settle.npc.ai;

import java.util.Random;

import com.sc.settle.SettlementBlocksSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.npc.EntityTunerSC;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.world.World;

/**
 * PACKAGE N (rule ПЖ-4): a Tuner's day - EntityTunerSC.dayGoal: its machine by day, the plaza at dusk, home at night.
 * Away from the goal it walks there (NpcNavSC: a City is too wide for one path search). At the goal, now and then:
 * by day it tends its machine (the nearest showcase block round the post: faces it, a swing of the arm now and then),
 * on the plaza it turns to the Heart, or it strolls a few steps round the goal (by day and on the plaza; at night it
 * stays put). Between those - COOLDOWN ticks of nothing, when the vanilla look tasks have it watch players and look
 * round. Movement and look are its own while it runs (mutex 3). While an enemy is near it rests (it stays by the core).
 */
public class EntityAITunerDaySC extends EntityAIBase {

    /** Within this of the goal (across) it is there: by day / at dusk, at night (a house's cell is small). */
    public static final double REACH = 2D, REACH_HOME = 1.2D;
    /** How far it strolls from the goal: by day, on the plaza (at night: never). */
    public static final int STROLL_WORK = 2, STROLL_PLAZA = 4;
    /** The machine is looked for this far round the post (across) and from 1 below to 2 above. */
    public static final int MACHINE_REACH = 3;
    private static final int MODE_GO = 0, MODE_STROLL = 1, MODE_TEND = 2;
    /** A walk gives up after this long; a stroll after; how often a lost path is set again; idle between turns. */
    private static final int GIVE_UP = 600, STROLL_MAX = 200, REPATH = 20, COOLDOWN = 60, COOLDOWN_RANDOM = 140;
    /** A goal it couldn't get closer to in FAILS tries: left alone this long (it stands where it got). */
    private static final int FAILS = 5, STUCK = 400;

    private final EntityTunerSC t;
    private int mode;
    private int[] goal, target;
    /** The machine of the last post (cached by the post once found: the house may be built after the Tuner came). */
    private int[] machine, machineOf;
    private int ticks, tendFor, idleUntil, stuckUntil, fails;
    /** Squared distance to the goal at the last re-path: no nearer since - a failed try. */
    private double lastDist;

    public EntityAITunerDaySC(EntityTunerSC t) {
        this.t = t;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        goal = t.danger() == null ? t.dayGoal() : null;     // an enemy near: it stays by the core (EntityAITunerFleeSC)
        if (goal == null) {
            return false;
        }
        int phase = NpcLinkSC.phase(t.worldObj.getWorldTime());
        if (!NpcLinkSC.near(t, goal, phase == NpcLinkSC.PHASE_HOME ? REACH_HOME : REACH + 1D)) {
            if (t.ticksExisted < stuckUntil) {
                return false;
            }
            mode = MODE_GO;
            target = goal;
            return true;
        }
        if (t.ticksExisted < idleUntil) {
            return false;
        }
        Random r = t.getRNG();
        idleUntil = t.ticksExisted + COOLDOWN + r.nextInt(COOLDOWN_RANDOM);
        int stroll = phase == NpcLinkSC.PHASE_WORK ? STROLL_WORK : phase == NpcLinkSC.PHASE_PLAZA ? STROLL_PLAZA : 0;
        if (stroll > 0 && r.nextInt(3) == 0) {
            target = new int[]{goal[0] + r.nextInt(2 * stroll + 1) - stroll, goal[1], goal[2] + r.nextInt(2 * stroll + 1) - stroll};
            mode = MODE_STROLL;
            return t.getNavigator().tryMoveToXYZ(target[0] + 0.5D, target[1], target[2] + 0.5D, EntityTunerSC.WALK);
        }
        target = phase == NpcLinkSC.PHASE_WORK ? machineNear(goal) : phase == NpcLinkSC.PHASE_PLAZA ? heart() : null;
        if (target == null) {
            return false;
        }
        mode = MODE_TEND;
        tendFor = 40 + r.nextInt(80);
        return true;
    }

    @Override
    public void startExecuting() {
        ticks = 0;
        fails = 0;
        if (mode == MODE_GO) {
            lastDist = NpcLinkSC.distSq(t, goal);
            NpcNavSC.walkTo(t, goal, EntityTunerSC.WALK);
        } else if (mode == MODE_TEND) {
            t.getNavigator().clearPathEntity();
        }
    }

    @Override
    public boolean continueExecuting() {
        if (t.danger() != null) {
            return false;
        }
        switch (mode) {
            case MODE_GO:
                if (ticks % 40 == 39) {                         // the day may have turned meanwhile
                    int[] g = t.dayGoal();
                    if (g == null) {
                        return false;
                    }
                    goal = g;
                }
                return ticks < GIVE_UP && fails < FAILS && !NpcLinkSC.near(t, goal, NpcLinkSC.night(t.worldObj.getWorldTime()) ? REACH_HOME : REACH);
            case MODE_STROLL:
                return ticks < STROLL_MAX && !t.getNavigator().noPath();
            default:
                return ticks < tendFor;
        }
    }

    @Override
    public void updateTask() {
        ticks++;
        if (mode == MODE_GO) {
            if (ticks % REPATH == 0 && t.getNavigator().noPath()) {
                double d = NpcLinkSC.distSq(t, goal);
                if (!NpcNavSC.walkTo(t, goal, EntityTunerSC.WALK) || d > lastDist - 1D) {
                    fails++;                                    // no path, or the last one brought it no nearer
                }
                lastDist = Math.min(lastDist, d);
            }
        } else if (mode == MODE_TEND && target != null) {
            t.getLookHelper().setLookPosition(target[0] + 0.5D, target[1] + 0.5D, target[2] + 0.5D, 10F, (float) t.getVerticalFaceSpeed());
            if (NpcLinkSC.phase(t.worldObj.getWorldTime()) == NpcLinkSC.PHASE_WORK && t.getRNG().nextInt(30) == 0) {
                t.swingItem();                                  // the hands at the machine
            }
        }
    }

    @Override
    public void resetTask() {
        t.getNavigator().clearPathEntity();
        if (mode == MODE_GO && (fails >= FAILS || ticks >= GIVE_UP)) {
            stuckUntil = t.ticksExisted + STUCK;                // stuck: let the other tasks have it a while
        }
    }

    /** The heart of the dome to turn to on the plaza (the core's block). */
    private int[] heart() {
        SettlementRegistrySC.Settlement s = t.link().get();
        return s == null ? null : new int[]{s.x, s.y, s.z};
    }

    /** The nearest showcase machine round the post (loaded blocks only), cached by the post once found; null none. */
    private int[] machineNear(int[] post) {
        if (machine != null && machineOf[0] == post[0] && machineOf[1] == post[1] && machineOf[2] == post[2]) {
            return machine;
        }
        machineOf = post.clone();
        machine = null;
        if (SettlementBlocksSC.showcase == null) {
            return null;
        }
        World w = t.worldObj;
        int best = Integer.MAX_VALUE;
        for (int x = post[0] - MACHINE_REACH; x <= post[0] + MACHINE_REACH; x++) {
            for (int z = post[2] - MACHINE_REACH; z <= post[2] + MACHINE_REACH; z++) {
                for (int y = post[1] - 1; y <= post[1] + 2; y++) {
                    if (!w.blockExists(x, y, z) || w.getBlock(x, y, z) != SettlementBlocksSC.showcase) {
                        continue;
                    }
                    int d = (x - post[0]) * (x - post[0]) + (y - post[1]) * (y - post[1]) + (z - post[2]) * (z - post[2]);
                    if (d < best) {
                        best = d;
                        machine = new int[]{x, y, z};
                    }
                }
            }
        }
        return machine;
    }
}
