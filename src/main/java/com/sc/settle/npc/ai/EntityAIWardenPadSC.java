package com.sc.settle.npc.ai;

import java.util.List;

import com.sc.mob.MobArmorSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.npc.EntityWardenSC;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.WorldServer;

/**
 * PACKAGE N (rule ПГ-3, «смена караула»): a guard with nothing to fight whose reserve is below
 * EntityWardenSC.RECHARGE_BELOW walks to the nearest free charge pad of the guard square (P_PAD + "0".."3"; all taken - the
 * nearest one: it waits its turn beside it) and stands on it, EntityWardenSC.padUnits into the reserve every PAD_EVERY ticks
 * (MobArmorSC.recharge - nothing while a ПЭ-1 Siphon draws it) until RECHARGE_UNTIL. A spark runs from the pad into the
 * suit each time - the players see who is «down». A fight or the leash takes it off the pad. Mutex 3. Ф3: the City
 * Commandant only uses the pads within EntityWardenSC.PLAZA_STAY of his plaza (a City's two front ones).
 */
public class EntityAIWardenPadSC extends EntityAIBase {

    /** Pads per settlement (the plan's 2..4). */
    public static final int PADS = 4;
    /** On the pad: this close across. */
    private static final double ON_PAD = 1.0D;
    /** It gives up walking after this long (and tries again later); how often a lost path is set again. */
    private static final int GIVE_UP = 1200, REPATH = 20, RETRY = 200;

    private final EntityWardenSC g;
    private final MobArmorSC armor;
    private int[] pad;
    private int walking, charging, nextTry;

    public EntityAIWardenPadSC(EntityWardenSC g) {
        this.g = g;
        this.armor = g.mobArmor();
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        if (g.ticksExisted < nextTry || g.getAttackTarget() != null || g.returning()
                || !EntityWardenSC.needsPad(armor.reserve, armor.reserveMax)) {
            return false;
        }
        SettlementRegistrySC.Settlement s = g.link().get();
        pad = s == null ? null : pad(s);
        if (pad == null) {
            nextTry = g.ticksExisted + RETRY;
        }
        return pad != null;
    }

    @Override
    public void startExecuting() {
        walking = 0;
        charging = 0;
        NpcNavSC.walkTo(g, pad, EntityWardenSC.PATROL);
    }

    @Override
    public boolean continueExecuting() {
        if (g.getAttackTarget() != null || g.returning() || EntityWardenSC.padDone(armor.reserve, armor.reserveMax)) {
            return false;
        }
        if (walking > GIVE_UP) {
            nextTry = g.ticksExisted + RETRY;
            return false;
        }
        return true;
    }

    @Override
    public void updateTask() {
        if (!NpcLinkSC.near(g, pad, ON_PAD)) {
            if (++walking % REPATH == 0 && g.getNavigator().noPath()) {
                NpcNavSC.walkTo(g, pad, EntityWardenSC.PATROL);
            }
            return;
        }
        g.getNavigator().clearPathEntity();
        if (++charging % EntityWardenSC.PAD_EVERY == 0) {
            armor.recharge(EntityWardenSC.padUnits(armor.reserveMax));     // ПГ-17: a share of the full reserve
            if (g.worldObj instanceof WorldServer) {            // a turquoise spark off the pad into the suit
                WorldServer ws = (WorldServer) g.worldObj;
                for (int i = 0; i < 4; i++) {
                    ws.func_147487_a("reddust", pad[0] + 0.5D, pad[1] + 0.2D + i * 0.4D, pad[2] + 0.5D, 0, 0.24D, 0.82D, 0.78D, 1.0D);
                }
            }
        }
    }

    @Override
    public void resetTask() {
        g.getNavigator().clearPathEntity();
        pad = null;
    }

    /** The nearest pad no other guard stands on; all taken - the nearest. Null: the plan has no pads. */
    private int[] pad(SettlementRegistrySC.Settlement s) {
        int[] best = null, bestFree = null;
        double bd = Double.MAX_VALUE, bdFree = Double.MAX_VALUE;
        int[] plaza = g.holdsPlaza() ? g.plaza() : null;
        for (int i = 0; i < PADS; i++) {
            int[] p = NpcLinkSC.point(s, SettlementRegistrySC.P_PAD + i);
            if (p == null || plaza != null && sq(p[0] - plaza[0]) + sq(p[2] - plaza[2]) > sq(EntityWardenSC.PLAZA_STAY)) {
                continue;                                       // Ф3: the Commandant only uses the pads by his plaza
            }
            double d = NpcLinkSC.distSq(g, p);
            if (d < bd) {
                bd = d;
                best = p;
            }
            if (d < bdFree && free(p)) {
                bdFree = d;
                bestFree = p;
            }
        }
        return bestFree != null ? bestFree : best;
    }

    private static double sq(double d) {
        return d * d;
    }

    private boolean free(int[] p) {
        if (!g.worldObj.blockExists(p[0], p[1], p[2])) {
            return true;
        }
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(p[0] - 0.5D, p[1] - 1D, p[2] - 0.5D, p[0] + 1.5D, p[1] + 2D, p[2] + 1.5D);
        List<?> l = g.worldObj.getEntitiesWithinAABB(EntityWardenSC.class, box);
        for (Object o : l) {
            if (o != g) {
                return false;
            }
        }
        return true;
    }
}
