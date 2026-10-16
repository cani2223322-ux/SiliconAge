package com.sc.mob;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import com.sc.dim.SubstrateSC;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;

/**
 * PACKAGE M (docs/plan-substrate-tech.md §5.6): the Substrate mobs' events - the «born» flag cleared in another world
 * (EntityJoinWorldEvent), who hurt a mob how much (LivingHurtEvent: the trophy roll goes to the player with the most
 * damage, at least 25%), the drops (LivingDropsEvent -> MobDropsSC), a soul vial on our mobs refused
 * (EntityInteractEvent), the trophy's 15 s owner lock (ServerTickEvent). Registered by SCMod on both buses.
 * <p>
 * The lock's end is kept in the trophy's own NBT (LOCK_NBT; vanilla saves the owner itself): a trophy whose chunk was
 * unloaded or whose server was restarted inside the 15 s comes back as a new EntityItem, and EntityJoinWorldEvent
 * locks it again for what is left - or lifts the lock that ran out meanwhile.
 * <p>
 * The damage log is transient - kept beside the mob (weakly), never saved: a mob reloaded from its chunk starts a new
 * fight. Only real players (EntityPlayerMP, not a FakePlayer) are named in it; everything else - mobs, fire, a mob
 * grinder's FakePlayer - only adds to the total, so a tap by a player and a grinder's kill don't make a trophy.
 * Note for Ф3: a blow the mob's shield takes whole never reaches LivingHurtEvent (EntityArmoredMobSC.damageEntity), so
 * it isn't counted - no Ф1 mob has a shield.
 */
public final class MobEventsSC {

    public static final MobEventsSC INSTANCE = new MobEventsSC();

    /** EnderIO's soul vial, by name - no dependency (rule ПТ-3: a captured native would carry its flag elsewhere). */
    public static final String SOUL_VESSEL = "crazypants.enderio.item.ItemSoulVessel";
    /** In a locked trophy's Entity.getEntityData(): the world time (getTotalWorldTime) its lock ends. */
    public static final String LOCK_NBT = "scTrophyLockUntil";

    /** Who hurt which armoured mob how much (server). */
    private static final Map<EntityLivingBase, DamageLog> LOGS = new WeakHashMap<EntityLivingBase, DamageLog>();
    /** Trophies lying as their player's, and the world time each lock ends (ПТ-2). */
    private static final List<Lock> LOCKS = new ArrayList<Lock>();

    private MobEventsSC() {
    }

    private static final class DamageLog {
        final Map<UUID, Float> players = new HashMap<UUID, Float>();
        float total;
    }

    private static final class Lock {
        final EntityItem item;
        final long until;

        Lock(EntityItem item, long until) {
            this.item = item;
            this.until = until;
        }
    }

    /** Server stopped (SpawnDirectorSC.clearAll calls it): nothing of that server's entities kept. */
    public static void clearAll() {
        synchronized (LOGS) {
            LOGS.clear();
        }
        LOCKS.clear();
    }

    // ------------------------------------------------------------------ the «born in Si-14» flag (ПТ-3)

    /**
     * A native entering any other world (a bridge with nativesCrossBridge, another mod's teleport) is just a mob there.
     * A trophy coming into a world - dropped, or loaded again - gets its lock back (relock).
     */
    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent e) {
        if (e.entity instanceof EntityItem && !e.world.isRemote) {
            relock((EntityItem) e.entity, e.world.getTotalWorldTime());
            return;
        }
        if (e.entity instanceof ISubstrateNativeSC && !e.world.isRemote && !SubstrateSC.is(e.world) && BornInSubstrateSC.isBorn(e.entity)) {
            BornInSubstrateSC.clear(e.entity);
        }
    }

    /** EnderIO's soul vial can't take a native (it would carry the mob - and its flag - to a spawner anywhere). */
    @SubscribeEvent
    public void onInteract(EntityInteractEvent e) {
        if (!(e.target instanceof ISubstrateNativeSC) || e.entityPlayer == null) {
            return;
        }
        ItemStack held = e.entityPlayer.getCurrentEquippedItem();
        if (held != null && held.getItem() != null && SOUL_VESSEL.equals(held.getItem().getClass().getName())) {
            e.setCanceled(true);
            if (!e.entityPlayer.worldObj.isRemote) {
                e.entityPlayer.addChatComponentMessage(new ChatComponentTranslation("sc.mob.soulvessel"));
            }
        }
    }

    // ------------------------------------------------------------------ who fought it (ПТ-2)

    /** Last, after every mod changed the amount: the blow as it lands (before the armour - that is the fight too). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onHurt(LivingHurtEvent e) {
        if (!(e.entityLiving instanceof EntityArmoredMobSC) || e.entityLiving.worldObj.isRemote || !(e.ammount > 0F)) {
            return;
        }
        EntityArmoredMobSC mob = (EntityArmoredMobSC) e.entityLiving;
        // an overkill blow counts only what there was to take: 1000 from a player doesn't outweigh the whole fight
        float amount = Math.min(e.ammount, mob.getHealth() + Math.max(0F, mob.mobArmor().reserve));
        Entity src = e.source.getEntity();
        if (src == null && e.source.getSourceOfDamage() instanceof EntityArrow) {
            src = ((EntityArrow) e.source.getSourceOfDamage()).shootingEntity;
        }
        synchronized (LOGS) {
            DamageLog log = LOGS.get(mob);
            if (log == null) {
                log = new DamageLog();
                LOGS.put(mob, log);
            }
            log.total += amount;
            if (src instanceof EntityPlayerMP && !(src instanceof FakePlayer)) {
                UUID id = ((EntityPlayer) src).getUniqueID();
                Float was = log.players.get(id);
                log.players.put(id, (was == null ? 0F : was.floatValue()) + amount);
            }
        }
    }

    /**
     * The trophy's player (ПТ-2): the real player who dealt the most of the damage the mob took, at least KILLER_SHARE of
     * it, still online. Null - no one (experience only).
     */
    public static EntityPlayerMP killerOf(EntityLivingBase mob) {
        UUID best = null;
        float bestDamage = 0F, total;
        synchronized (LOGS) {
            DamageLog log = LOGS.get(mob);
            if (log == null || log.total <= 0F) {
                return null;
            }
            total = log.total;
            for (Map.Entry<UUID, Float> en : log.players.entrySet()) {
                if (en.getValue().floatValue() > bestDamage) {
                    bestDamage = en.getValue().floatValue();
                    best = en.getKey();
                }
            }
        }
        if (best == null || bestDamage + 1.0E-4F < MobDropsSC.KILLER_SHARE * total) {
            return null;
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return null;
        }
        for (Object o : server.getConfigurationManager().playerEntityList) {
            if (o instanceof EntityPlayerMP && !(o instanceof FakePlayer) && best.equals(((EntityPlayerMP) o).getUniqueID())) {
                return (EntityPlayerMP) o;
            }
        }
        return null;
    }

    /** The mob's fight is over (its drops are decided). */
    public static void forget(EntityLivingBase mob) {
        synchronized (LOGS) {
            LOGS.remove(mob);
        }
    }

    // ------------------------------------------------------------------ drops, the trophy's lock

    @SubscribeEvent
    public void onDrops(LivingDropsEvent e) {
        MobDropsSC.onDrops(e);
    }

    /** A trophy lies as its player's until world time `until` (MobDropsSC; its owner is set already). Public for the world test. */
    public static void lockTrophy(EntityItem item, long until) {
        item.getEntityData().setLong(LOCK_NBT, until);
        lock(item, until);
    }

    /**
     * Server: `item` comes into a world. Only one with an owner and LOCK_NBT is a trophy under lock: locked again for
     * the rest of its time, or - run out while it lay in a saved chunk - lifted at once. Public for the world test.
     */
    public static void relock(EntityItem item, long now) {
        if (item.func_145798_i() == null || !item.getEntityData().hasKey(LOCK_NBT)) {
            return;
        }
        long until = item.getEntityData().getLong(LOCK_NBT);
        if (now >= until) {
            unlock(item);
        } else {
            lock(item, until);
        }
    }

    private static void lock(EntityItem item, long until) {
        for (Lock l : LOCKS) {
            if (l.item == item) {
                return;                                     // dropped: lockTrophy, then its own join
            }
        }
        LOCKS.add(new Lock(item, until));
    }

    private static void unlock(EntityItem item) {
        item.func_145797_a(null);
        item.getEntityData().removeTag(LOCK_NBT);
    }

    /**
     * FML bus: the locks that ran out are lifted - then anyone (a friend, a Marauder) may take the trophy. A lock on an
     * EntityItem whose chunk went (not dead, just out of the world) is lifted on that stale copy - harmless; the copy
     * loaded later has its own (relock).
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || LOCKS.isEmpty()) {
            return;
        }
        for (Iterator<Lock> it = LOCKS.iterator(); it.hasNext(); ) {
            Lock l = it.next();
            if (l.item.isDead || l.item.worldObj == null) {
                it.remove();
            } else if (l.item.worldObj.getTotalWorldTime() >= l.until) {
                unlock(l.item);
                it.remove();
            }
        }
    }

    /** The trophy locks waiting (the world test). */
    public static int locks() {
        return LOCKS.size();
    }
}
