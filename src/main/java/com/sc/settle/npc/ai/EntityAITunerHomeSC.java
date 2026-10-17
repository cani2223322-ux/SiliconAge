package com.sc.settle.npc.ai;

import com.sc.settle.npc.EntityTunerSC;

import net.minecraft.entity.ai.EntityAIMoveTowardsRestriction;

/**
 * PACKAGE N (rule ПЖ-1): vanilla's EntityAIMoveTowardsRestriction - back into the dome (the home area NpcLinkSC sets:
 * the dome less 2) - except while the Tuner stands at its day's goal: a post S built near the dome's edge (or up a
 * storey: the home distance is vanilla's 3D one) must not pull it to and fro between the two tasks.
 */
public class EntityAITunerHomeSC extends EntityAIMoveTowardsRestriction {

    /** The day's goal this close counts as «there» (across). */
    private static final double AT_GOAL = 4D;

    private final EntityTunerSC t;

    public EntityAITunerHomeSC(EntityTunerSC t, double speed) {
        super(t, speed);
        this.t = t;
    }

    @Override
    public boolean shouldExecute() {
        if (t.isWithinHomeDistanceCurrentPosition()) {
            return false;
        }
        int[] g = t.dayGoal();
        return (g == null || !NpcLinkSC.near(t, g, AT_GOAL)) && super.shouldExecute();
    }
}
