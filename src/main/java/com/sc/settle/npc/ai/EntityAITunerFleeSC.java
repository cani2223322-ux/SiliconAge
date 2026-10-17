package com.sc.settle.npc.ai;

import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.npc.EntityTunerSC;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.util.Vec3;

/**
 * PACKAGE N (rule ПЖ-4): a hostile (IMob) within EntityTunerSC.FLEE_RADIUS - the Tuner runs to its core (the Heart under
 * the guards' eyes) and stays within CORE_SAFE of it while the danger lasts; a stray (no dome) runs away from the mob
 * instead. Movement and look are its own (mutex 3), above everything but swimming: it doesn't stop to trade.
 */
public class EntityAITunerFleeSC extends EntityAIBase {

    /** Gives up a run after this long (stuck), ticks; how often it sets the path again. */
    private static final int GIVE_UP = 400, REPATH = 10;

    private final EntityTunerSC t;
    private int[] goal;
    private int ticks;

    public EntityAITunerFleeSC(EntityTunerSC t) {
        this.t = t;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        EntityLivingBase d = t.danger();
        if (d == null) {
            return false;
        }
        goal = goal(d);
        return goal != null;
    }

    /** The core (bound) - null when it is already there; else a spot away from the mob. */
    private int[] goal(EntityLivingBase d) {
        SettlementRegistrySC.Settlement s = t.link().get();
        if (s != null) {
            int[] c = NpcLinkSC.core(s);
            return NpcLinkSC.near(t, c, EntityTunerSC.CORE_SAFE) ? null : c;
        }
        Vec3 v = RandomPositionGenerator.findRandomTargetBlockAwayFrom(t, 12, 6, Vec3.createVectorHelper(d.posX, d.posY, d.posZ));
        return v == null ? null : new int[]{(int) Math.floor(v.xCoord), (int) Math.floor(v.yCoord), (int) Math.floor(v.zCoord)};
    }

    @Override
    public void startExecuting() {
        ticks = 0;
        t.setSprinting(true);
        NpcNavSC.walkTo(t, goal, EntityTunerSC.RUN);
    }

    @Override
    public boolean continueExecuting() {
        return ticks < GIVE_UP && t.danger() != null && !NpcLinkSC.near(t, goal, t.isBound() ? EntityTunerSC.CORE_SAFE : 1.5D);
    }

    @Override
    public void updateTask() {
        ticks++;
        if (ticks % REPATH == 0 && t.getNavigator().noPath()) {
            EntityLivingBase d = t.danger();
            if (!t.isBound() && d != null) {
                int[] g = goal(d);
                if (g != null) {
                    goal = g;
                }
            }
            NpcNavSC.walkTo(t, goal, EntityTunerSC.RUN);
        }
    }

    @Override
    public void resetTask() {
        t.setSprinting(false);
        t.getNavigator().clearPathEntity();
        goal = null;
    }
}
