package com.sc.mob.ai;

import com.sc.dim.SubstrateBlocksSC;
import com.sc.mob.IArmoredMobSC;
import com.sc.mob.MobArmorMathSC;
import com.sc.mob.MobArmorSC;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

/**
 * PACKAGE M (docs/plan-substrate-tech.md §5.2, rule ПБ-7): an armoured mob whose reserve is gone («села») looks for a
 * Copper Trace within SEARCH_RADIUS blocks (SubstrateBlocksSC.isTrace, with room to stand on it), walks there, crouches
 * and takes TRACE_RECHARGE_PER_SECOND units a second until the reserve is full. Movement and look are its own (mutex 3),
 * above the attack: a «down» Lineman / Marauder runs to the trace instead of fighting - the moment to catch it, the
 * plateaus' lesson.
 * <p>
 * The search reads the chunks' arrays directly (no chunk is loaded for it), at most once every SEARCH_COOLDOWN ticks
 * of a mob that found nothing (a mob from an egg in the Overworld never finds one and just fights).
 */
public class EntityAIRechargeAtTraceSC extends EntityAIBase {

    public static final int SEARCH_RADIUS = 16, SEARCH_HEIGHT = 8, SEARCH_COOLDOWN = 100;
    /** Blocks (squared) from the standing spot that count as «at the trace». */
    private static final double REACH_SQ = 2.25D;
    /** Ticks it tries to get there before it gives up (and searches again later). */
    private static final int GIVE_UP = 400;

    private final EntityCreature mob;
    private final MobArmorSC armor;
    private final double speed;
    private int tx, ty, tz;
    private int nextSearch, walking, charging;

    public EntityAIRechargeAtTraceSC(EntityCreature mob, double speed) {
        this.mob = mob;
        this.armor = ((IArmoredMobSC) mob).mobArmor();
        this.speed = speed;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        if (!armor.down() || armor.reserveMax <= 0F || mob.ticksExisted < nextSearch) {
            return false;
        }
        if (!find()) {
            nextSearch = mob.ticksExisted + SEARCH_COOLDOWN + mob.getRNG().nextInt(20);
            return false;
        }
        return true;
    }

    @Override
    public void startExecuting() {
        walking = 0;
        charging = 0;
        mob.getNavigator().tryMoveToXYZ(tx + 0.5D, ty + 1D, tz + 0.5D, speed);
    }

    @Override
    public boolean continueExecuting() {
        if (armor.reserve >= armor.reserveMax || walking > GIVE_UP || mob.isDead) {
            return false;
        }
        // the trace may be mined meanwhile: look again once a second
        return mob.ticksExisted % 20 != 0 || isTrace(mob.worldObj, tx, ty, tz);
    }

    @Override
    public void resetTask() {
        mob.setSneaking(false);
        mob.getNavigator().clearPathEntity();
        nextSearch = mob.ticksExisted + 20;
    }

    @Override
    public void updateTask() {
        double dx = tx + 0.5D - mob.posX, dy = ty + 1D - mob.posY, dz = tz + 0.5D - mob.posZ;
        boolean there = dx * dx + dz * dz <= REACH_SQ && Math.abs(dy) < 1.5D;
        mob.getLookHelper().setLookPosition(tx + 0.5D, ty + 0.5D, tz + 0.5D, 10F, mob.getVerticalFaceSpeed());
        if (!there) {
            mob.setSneaking(false);
            walking++;
            if (walking % 20 == 0 && mob.getNavigator().noPath()) {
                mob.getNavigator().tryMoveToXYZ(tx + 0.5D, ty + 1D, tz + 0.5D, speed);
            }
            return;
        }
        mob.getNavigator().clearPathEntity();
        mob.setSneaking(true);
        if (++charging % 20 == 0) {
            armor.recharge(MobArmorMathSC.TRACE_RECHARGE_PER_SECOND);
            if (mob.worldObj instanceof WorldServer) {                   // a spark off the trace into the suit
                ((WorldServer) mob.worldObj).func_147487_a("reddust", tx + 0.5D, ty + 1.1D, tz + 0.5D, 0, 1.0D, 0.55D, 0.2D, 1.0D);
            }
        }
    }

    /** The nearest trace with two free blocks above it within reach of the mob's position. */
    private boolean find() {
        World w = mob.worldObj;
        if (SubstrateBlocksSC.rock == null) {
            return false;
        }
        int mx = MathHelper.floor_double(mob.posX), my = MathHelper.floor_double(mob.posY), mz = MathHelper.floor_double(mob.posZ);
        int yLo = Math.max(1, my - SEARCH_HEIGHT), yHi = Math.min(w.getHeight() - 3, my + SEARCH_HEIGHT);
        double best = Double.MAX_VALUE;
        boolean found = false;
        for (int x = mx - SEARCH_RADIUS; x <= mx + SEARCH_RADIUS; x++) {
            for (int z = mz - SEARCH_RADIUS; z <= mz + SEARCH_RADIUS; z++) {
                if (!w.getChunkProvider().chunkExists(x >> 4, z >> 4)) {
                    continue;
                }
                Chunk c = w.getChunkFromChunkCoords(x >> 4, z >> 4);
                for (int y = yLo; y <= yHi; y++) {
                    if (c.getBlock(x & 15, y, z & 15) != SubstrateBlocksSC.rock
                            || !SubstrateBlocksSC.isTrace(SubstrateBlocksSC.rock, c.getBlockMetadata(x & 15, y, z & 15))
                            || !free(c.getBlock(x & 15, y + 1, z & 15)) || !free(c.getBlock(x & 15, y + 2, z & 15))) {
                        continue;
                    }
                    double d = (x - mx) * (x - mx) + (y - my) * (y - my) + (z - mz) * (z - mz);
                    if (d < best) {
                        best = d;
                        tx = x;
                        ty = y;
                        tz = z;
                        found = true;
                    }
                }
            }
        }
        return found;
    }

    private static boolean free(Block b) {
        return b != null && !b.getMaterial().blocksMovement() && !b.getMaterial().isLiquid();
    }

    private static boolean isTrace(World w, int x, int y, int z) {
        return w.blockExists(x, y, z) && SubstrateBlocksSC.isTrace(w.getBlock(x, y, z), w.getBlockMetadata(x, y, z));
    }
}
