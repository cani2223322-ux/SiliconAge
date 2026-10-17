package com.sc.settle.npc.ai;

import com.sc.settle.npc.EntityWardenSC;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;

/**
 * PACKAGE S (docs/plan-substrate-tech-f3.md §6.5, rule ПГ-12): the City Commandant never leaves his plaza. Over every
 * other movement (priority 1, mutex 3, the leash's return beside it): farther than EntityWardenSC.PLAZA_STAY from P_PLAZA
 * (a chase, a watched player, a push) he walks back until BACK_IN of it; with a target out of his blade's reach from the
 * plaza (a Shell at the dome's edge - his ПЭ-1's, a player who backed off) he stays where he is and faces it. A target
 * that comes within his reach of the plaza is the ordinary attack task's - he cuts it down there. Only the Commandant's.
 */
public class EntityAICommandantPostSC extends EntityAIBase {

    /** Back from past PLAZA_STAY: until this near the plaza. */
    public static final double BACK_IN = 3D;
    /** A target farther than PLAZA_STAY + this from the plaza is out of his blade's reach. */
    public static final double BLADE_OUT = 2.5D;
    private static final int REPATH = 20;

    private final EntityWardenSC g;
    private boolean returning;
    private int ticks;

    public EntityAICommandantPostSC(EntityWardenSC g) {
        this.g = g;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        if (!g.holdsPlaza() || g.returning()) {
            return false;
        }
        int[] p = g.plaza();
        if (p == null) {
            return false;
        }
        returning = !EntityWardenSC.nearPlaza(g, p, 0D);
        return returning || farTarget(p);
    }

    @Override
    public boolean continueExecuting() {
        if (!g.holdsPlaza() || g.returning()) {
            return false;
        }
        int[] p = g.plaza();
        if (p == null) {
            return false;
        }
        if (returning && NpcLinkSC.distSq(g, p) > BACK_IN * BACK_IN) {
            return true;
        }
        returning = !EntityWardenSC.nearPlaza(g, p, 0D);
        return returning || farTarget(p);
    }

    @Override
    public void startExecuting() {
        ticks = 0;
        int[] p = g.plaza();
        if (returning && p != null) {
            NpcNavSC.walkTo(g, p, EntityWardenSC.CHASE);
        } else {
            g.getNavigator().clearPathEntity();
        }
    }

    @Override
    public void updateTask() {
        int[] p = g.plaza();
        if (returning) {
            if (p != null && ++ticks % REPATH == 0 && g.getNavigator().noPath()) {
                NpcNavSC.walkTo(g, p, EntityWardenSC.CHASE);
            }
            return;
        }
        g.getNavigator().clearPathEntity();
        EntityLivingBase t = g.getAttackTarget();
        if (t != null) {
            g.getLookHelper().setLookPositionWithEntity(t, 30F, 30F);
        }
    }

    @Override
    public void resetTask() {
        g.getNavigator().clearPathEntity();
        returning = false;
    }

    /** His target stands out of his blade's reach from the plaza. */
    private boolean farTarget(int[] p) {
        EntityLivingBase t = g.getAttackTarget();
        return t != null && t.isEntityAlive() && !EntityWardenSC.nearPlaza(t, p, BLADE_OUT);
    }
}
