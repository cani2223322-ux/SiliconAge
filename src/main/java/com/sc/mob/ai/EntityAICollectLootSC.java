package com.sc.mob.ai;

import java.util.List;

import com.sc.mob.EntityMarauderSC;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;

/**
 * PACKAGE M (docs/plan-substrate-tech.md §5.3, rule ПС-5): a Marauder walks up to items lying within RADIUS blocks and
 * puts them into its bag (EntityMarauderSC.bag, 9 slots) - never into its hands or armour slots, whatever they are. It
 * leaves alone what's younger than MIN_AGE ticks (a player's own throw, a fresh drop) and what still belongs to someone
 * (EntityItem's owner: a trophy's 15 s, ПТ-2). Movement only (mutex 1): below the fight, above wandering.
 * <p>
 * It looks around once every SCAN_TICKS; the bag then keeps the mob from despawning and falls out whole when it dies.
 */
public class EntityAICollectLootSC extends EntityAIBase {

    public static final double RADIUS = 8D;
    public static final int MIN_AGE = 40, SCAN_TICKS = 20;
    /** Close enough to take it (squared blocks). */
    private static final double TAKE_SQ = 2.25D;
    private static final int GIVE_UP = 200;

    private final EntityMarauderSC mob;
    private final double speed;
    private EntityItem item;
    private int nextScan, walking;

    public EntityAICollectLootSC(EntityMarauderSC mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        if (mob.ticksExisted < nextScan || mob.getAttackTarget() != null) {
            return false;
        }
        nextScan = mob.ticksExisted + SCAN_TICKS;
        item = nearest();
        return item != null;
    }

    @Override
    public boolean continueExecuting() {
        return item != null && takeable(item) && walking < GIVE_UP && mob.getAttackTarget() == null;
    }

    @Override
    public void startExecuting() {
        walking = 0;
        mob.getNavigator().tryMoveToEntityLiving(item, speed);
    }

    @Override
    public void resetTask() {
        item = null;
        mob.getNavigator().clearPathEntity();
    }

    @Override
    public void updateTask() {
        walking++;
        if (mob.getDistanceSqToEntity(item) <= TAKE_SQ) {
            ItemStack left = mob.putInBag(item.getEntityItem().copy());
            if (left == null || left.stackSize <= 0) {
                item.setDead();
            } else {
                item.setEntityItemStack(left);
            }
            mob.swingItem();
            mob.playSound("random.pop", 0.2F, ((mob.getRNG().nextFloat() - mob.getRNG().nextFloat()) * 0.7F + 1F) * 2F);
            if (mob.bagFull()) {
                mob.say(EntityMarauderSC.SAY_BAG);
            }
            item = null;
            return;
        }
        if (walking % 20 == 0) {
            mob.getNavigator().tryMoveToEntityLiving(item, speed);
        }
    }

    private EntityItem nearest() {
        if (mob.bagFull()) {
            return null;
        }
        List list = mob.worldObj.getEntitiesWithinAABB(EntityItem.class, mob.boundingBox.expand(RADIUS, RADIUS / 2D, RADIUS));
        EntityItem best = null;
        double bestD = Double.MAX_VALUE;
        for (Object o : list) {
            EntityItem e = (EntityItem) o;
            if (!takeable(e) || !mob.fitsInBag(e.getEntityItem())) {
                continue;
            }
            double d = mob.getDistanceSqToEntity(e);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    private static boolean takeable(EntityItem e) {
        return !e.isDead && e.age >= MIN_AGE && e.func_145798_i() == null && e.getEntityItem() != null && e.getEntityItem().stackSize > 0;
    }
}
