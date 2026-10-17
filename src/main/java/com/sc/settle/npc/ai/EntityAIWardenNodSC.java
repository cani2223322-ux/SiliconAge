package com.sc.settle.npc.ai;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.sc.settle.GuardAlertSC;
import com.sc.settle.npc.EntityWardenSC;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayer;

/**
 * PACKAGE N (rule ПГ-10): a calm guard nods to a «Партнёр» (or better) passing within RADIUS - looks him in the eyes,
 * dips its head and looks up again - once in EVERY ticks per player. Look only (mutex 2): it may nod on the move.
 */
public class EntityAIWardenNodSC extends EntityAIBase {

    public static final double RADIUS = 6D;
    public static final int EVERY = 600;
    /** It looks for someone to nod to (a line of sight) at most this often. */
    private static final int CHECK = 10;
    /** The nod: eyes until LOOK, the head down until DOWN, eyes again until DONE (ticks). */
    private static final int LOOK = 10, DOWN = 18, DONE = 30;

    private final EntityWardenSC g;
    private final Map<UUID, Long> nodded = new HashMap<UUID, Long>();
    private EntityPlayer p;
    private int ticks, nextCheck;

    public EntityAIWardenNodSC(EntityWardenSC g) {
        this.g = g;
        setMutexBits(2);
    }

    @Override
    public boolean shouldExecute() {
        if (g.ticksExisted < nextCheck || g.getAttackTarget() != null || g.alert() != GuardAlertSC.CALM || g.partners().isEmpty()) {
            return false;
        }
        nextCheck = g.ticksExisted + CHECK;
        long now = g.worldObj.getTotalWorldTime();
        p = null;
        double bd = RADIUS * RADIUS;
        for (EntityPlayer q : g.partners()) {
            Long at = nodded.get(q.getUniqueID());
            double d = g.getDistanceSqToEntity(q);
            if (d < bd && (at == null || now - at.longValue() >= EVERY || now < at.longValue()) && g.getEntitySenses().canSee(q)) {
                bd = d;
                p = q;
            }
        }
        return p != null;
    }

    @Override
    public void startExecuting() {
        ticks = 0;
        long now = g.worldObj.getTotalWorldTime();
        if (nodded.size() > 16) {
            for (Iterator<Long> it = nodded.values().iterator(); it.hasNext();) {
                if (now - it.next().longValue() >= EVERY) {
                    it.remove();
                }
            }
        }
        nodded.put(p.getUniqueID(), Long.valueOf(now));
    }

    @Override
    public boolean continueExecuting() {
        return ticks < DONE && p != null && p.isEntityAlive() && g.getAttackTarget() == null;
    }

    @Override
    public void updateTask() {
        ticks++;
        double y = p.posY + p.getEyeHeight() - (ticks > LOOK && ticks <= DOWN ? 1.6D : 0D);
        g.getLookHelper().setLookPosition(p.posX, y, p.posZ, 10F, 40F);
    }

    @Override
    public void resetTask() {
        p = null;
    }
}
