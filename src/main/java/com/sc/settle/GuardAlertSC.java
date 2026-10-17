package com.sc.settle;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE C (docs/plan-substrate-tech-f2.md §5.5, rules ПГ-6..ПГ-8): who a settlement's guards are after - transient,
 * server only, emptied when the server stops (SCMod.serverStopped). SettlementProtectSC.violation provokes / asks to
 * watch; the guards' AI (package N, EntityWardenSC) asks target / warned / watched every few ticks. Per settlement,
 * per player UUID: the moment of the provocation, where the player stood (ПГ-6: walked WALK_AWAY away within the
 * WARN_TICKS - no attack), until when. A player in the «Враг» status of the settlement (the registry's LocalRec) is a
 * target on sight without the warning (ПГ-8). Creative players are never targets; only the guards within GUARD_REACH
 * of the offender act; past the leash from the core (ViolationRulesSC.leash) the offender is forgotten (the guards go
 * back to their posts).
 * <p>
 * A warning is settled when it runs out - by tick() (SettlementProtectSC's server tick, every TICK_EVERY) or by the
 * first guard asking, whichever comes first: the player still within WALK_AWAY of where they stood - the guards
 * attack for the provocation's ticks; farther - nothing. tick() also forgets the players who left the leash, the
 * settlement's world (another dimension, logged out) or died, and the grudges that ran out. Two players at one
 * settlement have a grudge each (LAN).
 * <p>
 * The signatures are fixed (package N calls them); the bodies are package C's.
 */
public final class GuardAlertSC {

    /** What a guard shows (EntityWardenSC's DataWatcher): calm, watching (the glow blinks red), warning (blade drawn, alarm), attacking. */
    public static final int CALM = 0, WATCHING = 1, WARNING = 2, ATTACKING = 3;
    /** tick() runs this often (ticks). */
    public static final int TICK_EVERY = 10;

    /** Settlement id -> player UUID -> what the guards hold against that player. Server thread only. */
    private static final Map<Integer, Map<UUID, Grudge>> GRUDGES = new HashMap<Integer, Map<UUID, Grudge>>();

    private GuardAlertSC() {
    }

    /** One player at one settlement. */
    private static final class Grudge {
        /** The 2 s warning began (0: none pending); the aggro it leads to; where the player stood then. */
        long warnFrom;
        int pending;
        double wx, wy, wz;
        /** Attacking until (world time; 0 / past: not). */
        long attackUntil;
        /** A guard watches the player until; which one (entity id, -1: the first guard asking takes it). */
        long watchUntil;
        int watcher = -1;
        /** The last blow at a guard that cost «Доверие» (ViolationRulesSC.chargesAgain). */
        long chargedAt;

        boolean warning(long now) {
            return warnFrom > 0L && now >= warnFrom && now < warnFrom + ViolationRulesSC.WARN_TICKS;
        }

        /** Nothing left to keep: no warning, no attack, no watch, the blow's charge window over. */
        boolean idle(long now) {
            return warnFrom <= 0L && attackUntil <= now && watchUntil <= now
                    && (chargedAt <= 0L || ViolationRulesSC.chargesAgain(chargedAt, now));
        }
    }

    // ------------------------------------------------------------------ what SettlementProtectSC tells

    /** The guards of settlement `id` go after `p` for `ticks` (after ViolationRulesSC.WARN_TICKS of warning). Server. */
    public static void provoke(int id, EntityPlayer p, int ticks, long now) {
        if (!real(p) || p.capabilities.isCreativeMode || ticks <= 0) {
            return;
        }
        Grudge g = grudge(id, p.getUniqueID());
        if (g.attackUntil > now) {
            g.attackUntil = Math.max(g.attackUntil, now + ticks);        // already after them: a new blow keeps it going
            return;
        }
        if (g.warning(now)) {
            g.pending = Math.max(g.pending, ticks);                      // inside the warning: it leads to the longer one
            return;
        }
        g.warnFrom = Math.max(1L, now);
        g.pending = ticks;
        g.wx = p.posX;
        g.wy = p.posY;
        g.wz = p.posZ;
    }

    /** ПГ (stage 2 of a block violation): the nearest guard of settlement `id` walks up to `p` and watches for WINDOW_TICKS. */
    public static void watch(int id, EntityPlayer p, long now) {
        if (!real(p) || p.capabilities.isCreativeMode) {
            return;
        }
        Grudge g = grudge(id, p.getUniqueID());
        g.watchUntil = Math.max(g.watchUntil, now + ViolationRulesSC.WINDOW_TICKS);
        Entity w = g.watcher < 0 ? null : p.worldObj.getEntityByID(g.watcher);
        if (w == null || !w.isEntityAlive()) {
            EntityLivingBase guard = nearestGuard(p.worldObj, id, p.posX, p.posY, p.posZ);
            g.watcher = guard == null ? -1 : guard.getEntityId();        // none loaded: the first guard asking takes it
        }
    }

    // ------------------------------------------------------------------ what the guards ask (package N)

    /** The player guard `guard` of settlement `id` attacks now (provoked past the warning, or a local «Враг» in sight), or null. */
    public static EntityPlayer target(int id, EntityLiving guard, long now) {
        SettlementRegistrySC.Settlement s = settlement(guard, id);
        if (s == null) {
            return null;
        }
        EntityPlayer best = null;
        double bestD = (double) ViolationRulesSC.GUARD_REACH * ViolationRulesSC.GUARD_REACH;
        Map<UUID, Grudge> m = GRUDGES.get(Integer.valueOf(id));
        if (m != null) {
            for (Map.Entry<UUID, Grudge> e : m.entrySet()) {
                EntityPlayer p = guard.worldObj.func_152378_a(e.getKey());
                Grudge g = e.getValue();
                settle(g, p, now);
                if (g.attackUntil > now && targetable(p, guard, s)) {
                    double d = guard.getDistanceSqToEntity(p);
                    if (d <= bestD) {
                        best = p;
                        bestD = d;
                    }
                }
            }
        }
        if (s.locals.isEmpty()) {
            return best;
        }
        // ПГ-8: a local «Враг» - on sight, no warning
        for (Object o : guard.worldObj.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (p == best || !targetable(p, guard, s)) {
                continue;
            }
            SettlementRegistrySC.LocalRec l = s.localIfAny(p.getUniqueID());
            if (l != null && l.enemy(now)) {
                double d = guard.getDistanceSqToEntity(p);
                if (d < bestD && guard.canEntityBeSeen(p)) {
                    best = p;
                    bestD = d;
                }
            }
        }
        return best;
    }

    /** The provoked player still inside the 2 s warning for guard `guard`, or null (the guard draws and sounds the alarm). */
    public static EntityPlayer warned(int id, EntityLiving guard, long now) {
        Map<UUID, Grudge> m = GRUDGES.get(Integer.valueOf(id));
        if (m == null || guard == null) {
            return null;
        }
        double reach = (double) ViolationRulesSC.GUARD_REACH * ViolationRulesSC.GUARD_REACH;
        EntityPlayer best = null;
        for (Map.Entry<UUID, Grudge> e : m.entrySet()) {
            if (!e.getValue().warning(now)) {
                continue;
            }
            EntityPlayer p = guard.worldObj.func_152378_a(e.getKey());
            if (p != null && p.isEntityAlive() && !p.capabilities.isCreativeMode) {
                double d = guard.getDistanceSqToEntity(p);
                if (d <= reach) {
                    best = p;
                    reach = d;
                }
            }
        }
        return best;
    }

    /** The player guard `guard` should walk up to and watch, or null. */
    public static EntityPlayer watched(int id, EntityLiving guard, long now) {
        Map<UUID, Grudge> m = GRUDGES.get(Integer.valueOf(id));
        if (m == null || guard == null) {
            return null;
        }
        double reach = (double) ViolationRulesSC.GUARD_REACH * ViolationRulesSC.GUARD_REACH;
        for (Map.Entry<UUID, Grudge> e : m.entrySet()) {
            Grudge g = e.getValue();
            if (g.watchUntil <= now) {
                continue;
            }
            if (g.watcher != guard.getEntityId()) {
                Entity w = g.watcher < 0 ? null : guard.worldObj.getEntityByID(g.watcher);
                if (w != null && w.isEntityAlive()) {
                    continue;                                   // another guard has it
                }
            }
            EntityPlayer p = guard.worldObj.func_152378_a(e.getKey());
            if (p != null && p.isEntityAlive() && !p.capabilities.isCreativeMode && guard.getDistanceSqToEntity(p) <= reach) {
                g.watcher = guard.getEntityId();                // the watcher gone (unloaded, dead): this one takes over
                return p;
            }
        }
        return null;
    }

    /** CALM / WATCHING / WARNING / ATTACKING for this guard now. */
    public static int alert(int id, EntityLiving guard, long now) {
        if (target(id, guard, now) != null) {
            return ATTACKING;
        }
        if (warned(id, guard, now) != null) {
            return WARNING;
        }
        return watched(id, guard, now) != null ? WATCHING : CALM;
    }

    /** When `p`'s blow at a guard of `id` was last charged (ViolationRulesSC.chargesAgain), 0 never. */
    public static long chargedAt(int id, EntityPlayer p) {
        if (p == null) {
            return 0L;
        }
        Map<UUID, Grudge> m = GRUDGES.get(Integer.valueOf(id));
        Grudge g = m == null ? null : m.get(p.getUniqueID());
        return g == null ? 0L : g.chargedAt;
    }

    public static void noteCharged(int id, EntityPlayer p, long now) {
        if (p != null) {
            grudge(id, p.getUniqueID()).chargedAt = Math.max(1L, now);
        }
    }

    /** The guards of `id` forget `p` (left the leash, logged out, died). */
    public static void forget(int id, EntityPlayer p) {
        if (p == null) {
            return;
        }
        Map<UUID, Grudge> m = GRUDGES.get(Integer.valueOf(id));
        if (m != null) {
            m.remove(p.getUniqueID());
            if (m.isEmpty()) {
                GRUDGES.remove(Integer.valueOf(id));
            }
        }
    }

    /** Every settlement forgets the player (logged out, died: SettlementProtectSC). */
    public static void forgetAll(UUID player) {
        for (Iterator<Map<UUID, Grudge>> it = GRUDGES.values().iterator(); it.hasNext(); ) {
            Map<UUID, Grudge> m = it.next();
            m.remove(player);
            if (m.isEmpty()) {
                it.remove();
            }
        }
    }

    /** The player is in a warning or under attack at settlement `id` (the tests; package N may ask too). */
    public static boolean provoked(int id, UUID player, long now) {
        Map<UUID, Grudge> m = GRUDGES.get(Integer.valueOf(id));
        Grudge g = m == null ? null : m.get(player);
        return g != null && (g.warning(now) || g.attackUntil > now);
    }

    /** Server stopped. */
    public static void clearAll() {
        GRUDGES.clear();
    }

    /**
     * SettlementProtectSC's server tick, every TICK_EVERY: the warnings that ran out are settled; the players who left
     * the leash, the settlement's world or the server, or died, are forgotten; the grudges that ran out go.
     */
    public static void tick(long now) {
        if (GRUDGES.isEmpty()) {
            return;
        }
        SettlementRegistrySC r = SettlementRegistrySC.get();
        for (Iterator<Map.Entry<Integer, Map<UUID, Grudge>>> it = GRUDGES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Map<UUID, Grudge>> e = it.next();
            SettlementRegistrySC.Settlement s = r == null ? null : r.byId(e.getKey().intValue());
            World w = s == null ? null : DimensionManager.getWorld(s.dim);
            if (w == null) {
                it.remove();                                    // no record, or its world unloaded: nobody to chase
                continue;
            }
            double leash = ViolationRulesSC.leash(s.radius);
            for (Iterator<Map.Entry<UUID, Grudge>> gi = e.getValue().entrySet().iterator(); gi.hasNext(); ) {
                Map.Entry<UUID, Grudge> ge = gi.next();
                Grudge g = ge.getValue();
                EntityPlayer p = w.func_152378_a(ge.getKey());
                if (p == null || !p.isEntityAlive() || s.distSq(p.posX, p.posZ) > leash * leash) {
                    gi.remove();                                // gone, dead or past the leash: forgotten
                    continue;
                }
                settle(g, p, now);
                if (g.idle(now)) {
                    gi.remove();
                }
            }
            if (e.getValue().isEmpty()) {
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /** ПГ-6: a warning that ran out - the player stayed within WALK_AWAY: the attack starts; walked away: nothing. */
    private static void settle(Grudge g, EntityPlayer p, long now) {
        if (g.warnFrom > now) {
            g.warnFrom = 0L;                                    // the clock went back (/time): no warning left
            g.pending = 0;
            return;
        }
        if (g.warnFrom <= 0L || now < g.warnFrom + ViolationRulesSC.WARN_TICKS) {
            return;
        }
        double away = ViolationRulesSC.WALK_AWAY;
        if (p != null && p.isEntityAlive() && p.getDistanceSq(g.wx, g.wy, g.wz) < away * away) {
            g.attackUntil = Math.max(g.attackUntil, g.warnFrom + ViolationRulesSC.WARN_TICKS + g.pending);
        }
        g.warnFrom = 0L;
        g.pending = 0;
    }

    /** A guard may go for `p`: a real survival player, alive, in the guard's world, within GUARD_REACH of it and the leash of the core. */
    private static boolean targetable(EntityPlayer p, EntityLiving guard, SettlementRegistrySC.Settlement s) {
        if (!real(p) || !p.isEntityAlive() || p.capabilities.isCreativeMode || p.worldObj != guard.worldObj) {
            return false;
        }
        double reach = ViolationRulesSC.GUARD_REACH, leash = ViolationRulesSC.leash(s.radius);
        return guard.getDistanceSqToEntity(p) <= reach * reach && s.distSq(p.posX, p.posZ) <= leash * leash;
    }

    private static boolean real(EntityPlayer p) {
        return p instanceof EntityPlayerMP && !(p instanceof FakePlayer) && p.worldObj != null && !p.worldObj.isRemote;
    }

    private static SettlementRegistrySC.Settlement settlement(EntityLiving guard, int id) {
        if (guard == null || guard.worldObj == null || guard.worldObj.isRemote || id < 0) {
            return null;
        }
        SettlementRegistrySC r = SettlementRegistrySC.get(guard.worldObj);
        SettlementRegistrySC.Settlement s = r == null ? null : r.byId(id);
        return s != null && s.dim == guard.worldObj.provider.dimensionId ? s : null;
    }

    private static Grudge grudge(int id, UUID player) {
        Integer k = Integer.valueOf(id);
        Map<UUID, Grudge> m = GRUDGES.get(k);
        if (m == null) {
            m = new HashMap<UUID, Grudge>();
            GRUDGES.put(k, m);
        }
        Grudge g = m.get(player);
        if (g == null) {
            g = new Grudge();
            m.put(player, g);
        }
        return g;
    }

    /**
     * The nearest living, loaded guard of settlement `id` within GUARD_REACH of (x, y, z), or null - the watcher, and the
     * name the warnings speak with (SettlementProtectSC.speaker). Chunks not loaded are skipped (nothing loads).
     */
    public static EntityLivingBase nearestGuard(World w, int id, double x, double y, double z) {
        if (w == null || id < 0) {
            return null;
        }
        double r = ViolationRulesSC.GUARD_REACH;
        List<?> l = w.getEntitiesWithinAABB(EntityLivingBase.class, AxisAlignedBB.getBoundingBox(x - r, y - r / 2, z - r, x + r, y + r / 2, z + r));
        EntityLivingBase best = null;
        double bestD = r * r;
        for (Object o : l) {
            if (o instanceof ISettlementGuardSC && ((ISettlementGuardSC) o).settlementId() == id && ((EntityLivingBase) o).isEntityAlive()) {
                EntityLivingBase g = (EntityLivingBase) o;
                double d = g.getDistanceSq(x, y, z);
                if (d <= bestD) {
                    best = g;
                    bestD = d;
                }
            }
        }
        return best;
    }
}
