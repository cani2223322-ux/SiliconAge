package com.sc.settle.caravan.ai;

import com.sc.settle.caravan.CaravanRunSC;
import com.sc.settle.caravan.CaravanSC;
import com.sc.settle.caravan.EntityCaravanGuardSC;
import com.sc.settle.caravan.EntityCaravanMasterSC;
import com.sc.settle.npc.EntityWardenSC;
import com.sc.settle.npc.ai.NpcNavSC;

import net.minecraft.entity.ai.EntityAIBase;

/**
 * PACKAGE K (docs/plan-substrate-tech-f5.md §8.2, rule ПН-13): a caravan guard keeps by its master - its own side of it (SIDE blocks
 * across its way, BACK behind), walking up when farther than START from that spot, stopping within STOP; at a chase's speed
 * (EntityWardenSC.CHASE: the master walks slower). The path search only for the few blocks to the spot (re-set every REPATH ticks);
 * left far behind - NpcNavSC's legs. Below the attack tasks (they take a target's chase; the target task keeps that within reach of
 * the master), movement only (mutex 1).
 */
public class EntityAICaravanEscortSC extends EntityAIBase {

    /** ПН-13: within KEEP of the master; its spot SIDE across and BACK behind; walking from START to STOP of the spot. */
    public static final double KEEP = 6D, SIDE = 2D, BACK = 1D, START = 3D, STOP = 1.5D;
    /** The path re-set this often (ticks). */
    private static final int REPATH = 10;

    private final EntityCaravanGuardSC g;
    private int repath;

    public EntityAICaravanEscortSC(EntityCaravanGuardSC g) {
        this.g = g;
        setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        EntityCaravanMasterSC m = master();
        return m != null && g.getAttackTarget() == null && dist2(m) > START * START;
    }

    @Override
    public boolean continueExecuting() {
        EntityCaravanMasterSC m = master();
        return m != null && g.getAttackTarget() == null && dist2(m) > STOP * STOP;
    }

    @Override
    public void startExecuting() {
        repath = 0;
    }

    @Override
    public void resetTask() {
        g.getNavigator().clearPathEntity();
    }

    @Override
    public void updateTask() {
        EntityCaravanMasterSC m = master();
        if (m == null || --repath > 0) {
            return;
        }
        repath = REPATH;
        double[] s = spot(m);
        double dx = s[0] - g.posX, dz = s[1] - g.posZ;
        if (dx * dx + dz * dz <= NpcNavSC.DIRECT * NpcNavSC.DIRECT && g.getNavigator().tryMoveToXYZ(s[0], m.posY, s[1], EntityWardenSC.CHASE)) {
            return;
        }
        NpcNavSC.walkTo(g, s[0], m.posY, s[1], EntityWardenSC.CHASE);
    }

    private EntityCaravanMasterSC master() {
        CaravanRunSC run = CaravanSC.runOf(g);
        return run == null ? null : run.master();
    }

    /** Its spot: SIDE across the master's facing on its own side (as CaravanRunSC.begin stands them), BACK behind it. */
    private double[] spot(EntityCaravanMasterSC m) {
        double yaw = Math.toRadians(m.renderYawOffset);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);         // the master's forward (Minecraft's yaw: 0 = south)
        double sx = Math.cos(yaw) * g.side(), sz = Math.sin(yaw) * g.side();     // across it
        return new double[]{m.posX + sx * SIDE - fx * BACK, m.posZ + sz * SIDE - fz * BACK};
    }

    private double dist2(EntityCaravanMasterSC m) {
        double[] s = spot(m);
        double dx = s[0] - g.posX, dz = s[1] - g.posZ;
        return dx * dx + dz * dz;
    }
}
