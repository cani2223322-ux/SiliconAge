package com.sc.mob.ai;

import java.util.List;

import com.sc.mob.EntityScrapperSC;
import com.sc.mob.ScrapperSC;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.item.EntityItem;

/**
 * PACKAGE N (docs/plan-substrate-tech-f4.md §7.9, rule ПС-39): the Scrapper walks up to things lying within
 * EntityScrapperSC.COLLECT_RADIUS (half that up or down) and swallows them (EntityScrapperSC.swallow - into its bag, never its
 * hands or armour; a dead player's thing remembers its owner). It leaves alone a fresh throw (younger than MIN_AGE ticks - a
 * dead player's things are always old enough) and what still belongs to someone (EntityItem's owner: a trophy's 15 s, ПТ-2); a
 * full bag takes nothing. It looks round once every SCAN_TICKS; not while it fights. Movement (mutex 1): below the fight, above
 * the errand and wandering.
 */
public class EntityAIScrapperCollectSC extends EntityAIBase {

    public static final int MIN_AGE = 40, SCAN_TICKS = 20;
    /** Close enough to swallow it (squared blocks: its body is wide). */
    private static final double TAKE_SQ = 3.0D;
    private static final int GIVE_UP = 200;

    private final EntityScrapperSC mob;
    private final double speed;
    private EntityItem item;
    private int nextScan, walking;

    public EntityAIScrapperCollectSC(EntityScrapperSC mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        if (mob.ticksExisted < nextScan || mob.getAttackTarget() != null || mob.stunned()) {
            return false;
        }
        nextScan = mob.ticksExisted + SCAN_TICKS;
        item = nearest();
        return item != null;
    }

    @Override
    public boolean continueExecuting() {
        return item != null && takeable(item) && walking < GIVE_UP && mob.getAttackTarget() == null && !mob.stunned();
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
            mob.swallow(item);
            item = null;
            nextScan = 0;                                      // the next thing at once: the pile is swallowed in a row
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
        double r = EntityScrapperSC.COLLECT_RADIUS;
        List<?> list = mob.worldObj.getEntitiesWithinAABB(EntityItem.class, mob.boundingBox.expand(r, r / 2D, r));
        EntityItem best = null;
        double bestD = Double.MAX_VALUE;
        for (Object o : list) {
            EntityItem e = (EntityItem) o;
            if (!takeable(e) || !mob.fitsInBag(e.getEntityItem())) {
                continue;
            }
            double d = mob.getDistanceSqToEntity(e);
            if (d < bestD && d <= r * r) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    private static boolean takeable(EntityItem e) {
        if (e.isDead || e.getEntityItem() == null || e.getEntityItem().stackSize <= 0 || e.func_145798_i() != null) {
            return false;
        }
        return e.age >= MIN_AGE || e.getEntityData().hasKey(ScrapperSC.DROP_NBT);
    }
}
