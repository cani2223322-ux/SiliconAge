package com.sc.mob;

import com.sc.dim.SubstrateSC;

import net.minecraft.entity.Entity;

/**
 * The «рождён в Si-14» flag of one entity (docs/plan-substrate-tech.md §5, rules ПТ-1..ПТ-3): only the Substrate's
 * own SpawnDirectorSC sets it. A mob gives trophies, faction drops, reputation and contract counts only with the
 * flag, dying in the Substrate, killed by a real player (MobDropsSC). Spawn eggs, other mods' spawners and mob
 * grinders never set it; it is cleared when the entity enters any other world (MobEventsSC, EntityJoinWorldEvent:
 * a mob that went through a bridge is just a mob there) and when another mod captures it (a soul vial).
 * <p>
 * Kept in the entity's Forge data, so it is saved with the entity and copied with it (EntityList +
 * copyDataFrom, as BridgeTeleportSC does) - the join event then clears the copy's.
 */
public final class BornInSubstrateSC {

    /** The key in Entity.getEntityData(). */
    public static final String NBT = "scBornSi14";

    private BornInSubstrateSC() {
    }

    /** Server: SpawnDirectorSC spawned `e` in the Substrate. */
    public static void mark(Entity e) {
        if (e != null && e.worldObj != null && !e.worldObj.isRemote && SubstrateSC.is(e.worldObj)) {
            e.getEntityData().setBoolean(NBT, true);
        }
    }

    public static boolean isBorn(Entity e) {
        return e != null && e.getEntityData().getBoolean(NBT);
    }

    public static void clear(Entity e) {
        if (e != null) {
            e.getEntityData().removeTag(NBT);
        }
    }
}
