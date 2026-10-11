package com.sc.util;

import net.minecraft.entity.Entity;

/**
 * "Every n ticks" for a player and the items it carries - by the world's clock, not Entity.ticksExisted.
 * <p>
 * A player riding an entity (seen 2026-10-11 in the Vehicles add-on's plane, with Galacticraft's player class in the
 * pack) has ticksExisted counted twice a tick while the player tick event and the item updates run once - so they see
 * only odd or only even values, and "ticksExisted % 20 == 0" may never fire: the suit's night vision was not renewed
 * and went out after 13 s, the heat / gas / charge second stopped. The world time moves exactly once a tick; adding the
 * entity id spreads the players over the ticks the way ticksExisted did.
 */
public final class TickClock {

    private TickClock() {
    }

    /** A per-entity tick count that moves once a world tick. */
    public static long ticks(Entity e) {
        if (e.worldObj == null) {
            return e.ticksExisted;
        }
        return e.worldObj.getTotalWorldTime() + (e.getEntityId() & 0xFFFF);
    }

    /** ticks(e) % n (n > 0), the drop-in for "e.ticksExisted % n". */
    public static int mod(Entity e, int n) {
        return (int) (ticks(e) % n);
    }

    /**
     * True only the first time it is asked for this entity and key in a world tick: a handler that may be reached
     * twice a tick (two tick events for one player) runs once.
     */
    public static boolean firstThisTick(Entity e, String key) {
        if (e.worldObj == null) {
            return true;
        }
        long now = e.worldObj.getTotalWorldTime();
        net.minecraft.nbt.NBTTagCompound d = e.getEntityData();
        if (d.hasKey(key) && d.getLong(key) == now) {
            return false;
        }
        d.setLong(key, now);
        return true;
    }
}
