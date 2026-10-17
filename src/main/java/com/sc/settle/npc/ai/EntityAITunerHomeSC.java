package com.sc.settle.npc.ai;

import com.sc.hazard.EmpStormSC;
import com.sc.settle.npc.EntityTunerSC;

import net.minecraft.entity.ai.EntityAIMoveTowardsRestriction;

/**
 * PACKAGE N (rule ПЖ-1): vanilla's EntityAIMoveTowardsRestriction - back into the dome (the home area NpcLinkSC sets:
 * the dome less 2) - except while the Tuner stands at its day's goal: a post S built near the dome's edge (or up a
 * storey: the home distance is vanilla's 3D one) must not pull it to and fro between the two tasks.
 * <p>
 * Ф4, package S (docs/plan-substrate-tech-f4.md §5.7, ПФ-4): in an EMP storm's warning and the storm itself the Tuner goes to
 * its home (the slot's) and sits there till the storm is over - the task then can't be interrupted, so the day's task (a
 * higher priority) can't take it back to its machine; it lets go at once for an enemy near (the flight to the core), a
 * customer at its terminal and water (the swim). A stray, or a slot without a home: the old behaviour.
 */
public class EntityAITunerHomeSC extends EntityAIMoveTowardsRestriction {

    /** The day's goal this close counts as «there» (across). */
    private static final double AT_GOAL = 4D;
    /** Ф4: the storm's walk home - a lost path set again this often; given up after FAILS tries that brought it no nearer. */
    private static final int REPATH = 20, FAILS = 6, STUCK = 200;

    private final EntityTunerSC t;
    /** Ф4: the home it goes to / sits in for the storm (null - the old task). */
    private int[] stormHome;
    private int ticks, fails, stuckUntil;
    private double lastDist;

    public EntityAITunerHomeSC(EntityTunerSC t, double speed) {
        super(t, speed);
        this.t = t;
    }

    @Override
    public boolean shouldExecute() {
        stormHome = stormHome();
        if (stormHome != null) {
            return t.ticksExisted >= stuckUntil || NpcLinkSC.near(t, stormHome, EntityAITunerDaySC.REACH_HOME + 1D);
        }
        if (t.isWithinHomeDistanceCurrentPosition()) {
            return false;
        }
        int[] g = t.dayGoal();
        return (g == null || !NpcLinkSC.near(t, g, AT_GOAL)) && super.shouldExecute();
    }

    @Override
    public void startExecuting() {
        if (stormHome == null) {
            super.startExecuting();
            return;
        }
        ticks = 0;
        fails = 0;
        lastDist = NpcLinkSC.distSq(t, stormHome);
        if (!NpcLinkSC.near(t, stormHome, EntityAITunerDaySC.REACH_HOME)) {
            NpcNavSC.walkTo(t, stormHome, EntityTunerSC.WALK);
        }
    }

    @Override
    public boolean continueExecuting() {
        if (stormHome == null) {
            return super.continueExecuting();
        }
        return fails < FAILS && stormHome() != null;
    }

    @Override
    public void updateTask() {
        if (stormHome == null) {
            return;
        }
        ticks++;
        if (NpcLinkSC.near(t, stormHome, EntityAITunerDaySC.REACH_HOME)) {
            if (!t.getNavigator().noPath()) {
                t.getNavigator().clearPathEntity();         // home: it sits it out
            }
            fails = 0;
            return;
        }
        if (ticks % REPATH == 0 && t.getNavigator().noPath()) {
            double d = NpcLinkSC.distSq(t, stormHome);
            if (!NpcNavSC.walkTo(t, stormHome, EntityTunerSC.WALK) || d > lastDist - 1D) {
                fails++;
            }
            lastDist = Math.min(lastDist, d);
        }
    }

    @Override
    public void resetTask() {
        if (stormHome != null) {
            t.getNavigator().clearPathEntity();
            if (fails >= FAILS) {
                stuckUntil = t.ticksExisted + STUCK;        // no way home from here: the other tasks have it a while
            }
        }
        stormHome = null;
    }

    /** Ф4: while it goes / sits home for a storm nothing of a higher priority but the yields of stormHome() takes it away. */
    @Override
    public boolean isInterruptible() {
        return stormHome == null;
    }

    /**
     * Ф4, ПФ-4: the home to be in now - the storm's warning or the storm in its world, its slot's home known, and nothing to let
     * go for (an enemy near, a customer, water); null otherwise.
     */
    private int[] stormHome() {
        if (t.worldObj == null || t.worldObj.isRemote || t.danger() != null || t.customer() != null || t.isInWater() || t.handleLavaMovement()) {
            return null;
        }
        if (!EmpStormSC.warning(t.worldObj) && !EmpStormSC.active(t.worldObj)) {
            return null;
        }
        return NpcLinkSC.home(t.link().slot());
    }
}
