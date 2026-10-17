package com.sc.hazard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.sc.dim.SubstrateConfigSC;
import com.sc.dim.SubstrateSC;
import com.sc.mob.ISubstrateNativeSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.WorldEvent;

/**
 * PACKAGE S (docs/plan-substrate-tech-f4.md §6, rules ПН-1..ПН-8): the Renegades' raids on the settlements. Registered by SCMod
 * on FML's bus (the Substrate's world tick: every RaidSC.CHECK_TICKS the settlements whose core is loaded; the running raids
 * every RUN_TICKS) and Forge's (EntityJoinWorldEvent: a raider of a raid that is over - a restart, a chunk unloaded meanwhile -
 * is an ordinary mob again; WorldEvent.Unload: the Substrate unloaded under a raid - it is lost). Per settlement in its registry record's extra "R" (EXTRA): the last night rolled, the last raid's
 * day, the trade closed until (world time); the running raid itself only in memory (RaidRunSC; a restart ends it - nothing is
 * simulated). The raiders carry NBT RAIDER_NBT = the settlement's id in getEntityData (they stay while it runs: func_110163_bv)
 * and are «born in Si-14» (SpawnDirectorSC.spawnAt).
 * <ul>
 * <li>when (ПН-1, RaidRulesSC.mayRoll): the rule dimension.raids, not Peaceful (its mobs remove themselves: no fight, no
 * reward - a raid running is called off), the night, the core loaded, no raid running there, a defender
 * near - a real player not in creative, «Доверие» from «Знакомый» (RaidRulesSC.defender), not «Враг» of this settlement, within
 * RaidRulesSC.reach of the core; one roll a night (rolled), RaidRulesSC.rolls, COOLDOWN_DAYS after the last raid (last);</li>
 * <li>the run, the waves, the reward and the loss - RaidRunSC.</li>
 * </ul>
 * Two players at one settlement both defend (the reward to each); one player at two settlements in a night - a roll at each.
 */
public final class RaidSC {

    public static final RaidSC INSTANCE = new RaidSC();
    /** The registry record's extra key (Settlement.extra) and its fields: last night rolled, last raid's day, trade closed until. */
    public static final String EXTRA = "R", ROLLED = "rolled", LAST = "last", CLOSED = "closed";
    /** The raiders' entity NBT key (the settlement's id). */
    public static final String RAIDER_NBT = "scRaid";
    /** How often the settlements are looked at for a raid to begin (ticks). */
    public static final int CHECK_TICKS = 100;
    /** How often a running raid takes its step (the waves, the raiders' orders, the end). */
    public static final int RUN_TICKS = 20;

    /** The running raids by settlement id (server, memory only). */
    private static final Map<Integer, RaidRunSC> RUNS = new HashMap<Integer, RaidRunSC>();

    private RaidSC() {
    }

    /**
     * ПН-4: the settlement's trade is closed after a lost raid (TradeServerSC refuses with sc.trade.refuse.raid - package C).
     * `worldTime` - the world's getWorldTime() (the clock «closed» is kept in).
     */
    public static boolean tradeClosed(SettlementRegistrySC.Settlement s, long worldTime) {
        return s != null && s.extra.getCompoundTag(EXTRA).getLong(CLOSED) > worldTime;
    }

    /** The same with world `w`'s clock (server). */
    public static boolean tradeClosed(World w, SettlementRegistrySC.Settlement s) {
        return w != null && tradeClosed(s, w.getWorldTime());
    }

    /** A raid on settlement `id` runs now. */
    public static boolean running(int id) {
        return RUNS.containsKey(Integer.valueOf(id));
    }

    /** The world test: a raid on `s` now, `by` its defender (the roll and the night skipped). @return it began */
    public static boolean startNow(WorldServer w, SettlementRegistrySC.Settlement s, EntityPlayer by) {
        SettlementRegistrySC r = w == null || !SubstrateSC.is(w) ? null : SettlementRegistrySC.get(w);
        if (r == null || s == null || s.dim != w.provider.dimensionId || running(s.id) || !w.blockExists(s.x, s.y, s.z) || peaceful(w)) {
            return false;
        }
        return begin(w, r, s, by) != null;
    }

    /** The world test: the raiders of the running raid on `id` still alive and loaded, -1 none running. */
    public static int raidersLeft(int id) {
        RaidRunSC run = RUNS.get(Integer.valueOf(id));
        return run == null ? -1 : run.left();
    }

    /** The world test: the wave the running raid on `id` is at (0..WAVES-1), -1 none running. */
    public static int wave(int id) {
        RaidRunSC run = RUNS.get(Integer.valueOf(id));
        return run == null ? -1 : run.wave();
    }

    /** The world test: the running raid on `id` takes its step now (as its tick would). @return it is over */
    public static boolean stepNow(WorldServer w, int id) {
        RaidRunSC run = RUNS.get(Integer.valueOf(id));
        if (run == null || w == null) {
            return true;
        }
        if (run.step(w)) {
            RUNS.remove(Integer.valueOf(id));
            return true;
        }
        return false;
    }

    /** Server stopped: the running raids (their raiders stay as ordinary mobs - the next load's join event frees them). */
    public static void clearAll() {
        RUNS.clear();
    }

    // ------------------------------------------------------------------ the tick

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side != Side.SERVER || !(e.world instanceof WorldServer) || !SubstrateSC.is(e.world)) {
            return;
        }
        WorldServer w = (WorldServer) e.world;
        long total = w.getTotalWorldTime();
        if (!RUNS.isEmpty() && total % RUN_TICKS == 0L) {
            int dim = w.provider.dimensionId;
            for (Iterator<RaidRunSC> it = new ArrayList<RaidRunSC>(RUNS.values()).iterator(); it.hasNext(); ) {
                RaidRunSC run = it.next();
                if (run.dim == dim && run.step(w)) {
                    RUNS.remove(Integer.valueOf(run.id));
                }
            }
        }
        if (total % CHECK_TICKS == 0L) {
            checks(w);
        }
    }

    /** ПН-1: every settlement of `w` whose core is loaded - tonight's roll, once, when a defender is near. */
    private static void checks(WorldServer w) {
        if (!SubstrateConfigSC.raids || w.playerEntities.isEmpty() || !RaidRulesSC.night(w.getWorldTime()) || peaceful(w)) {
            return;
        }
        SettlementRegistrySC r = SettlementRegistrySC.get(w);
        if (r == null) {
            return;
        }
        int dim = w.provider.dimensionId;
        long today = SettlementSC.day(w);
        for (SettlementRegistrySC.Settlement s : new ArrayList<SettlementRegistrySC.Settlement>(r.all())) {
            if (s.dim != dim || running(s.id) || !w.blockExists(s.x, s.y, s.z)) {
                continue;
            }
            NBTTagCompound x = s.extra.getCompoundTag(EXTRA);
            long rolled = x.hasKey(ROLLED) ? x.getLong(ROLLED) : -1L, last = x.hasKey(LAST) ? x.getLong(LAST) : -1L;
            if (rolled == today) {
                continue;
            }
            EntityPlayer def = firstDefender(w, r, s);
            if (!RaidRulesSC.mayRoll(SubstrateConfigSC.raids, true, today, rolled, last, def != null)) {
                continue;
            }
            x.setLong(ROLLED, today);
            s.extra.setTag(EXTRA, x);
            r.markDirty();
            if (RaidRulesSC.rolls(w.getSeed(), s.id, today)) {
                begin(w, r, s, def);
            }
        }
    }

    /** A raid on `s` begins: the day remembered (ПН-1's cooldown - kept even if the server stops mid-raid), the first wave. */
    private static RaidRunSC begin(WorldServer w, SettlementRegistrySC r, SettlementRegistrySC.Settlement s, EntityPlayer by) {
        NBTTagCompound x = s.extra.getCompoundTag(EXTRA);
        x.setLong(LAST, SettlementSC.day(w));
        s.extra.setTag(EXTRA, x);
        r.markDirty();
        RaidRunSC run = new RaidRunSC(w, s, by);
        RUNS.put(Integer.valueOf(s.id), run);
        run.begin(w, r, s);
        return run;
    }

    /** The first defender of `s` in `w`, null none. */
    private static EntityPlayer firstDefender(World w, SettlementRegistrySC r, SettlementRegistrySC.Settlement s) {
        int reach = RaidRulesSC.reach(s.size);
        for (Object o : w.playerEntities) {
            if (defends(w, r, s, (EntityPlayer) o, reach)) {
                return (EntityPlayer) o;
            }
        }
        return null;
    }

    /**
     * ПН-1: `p` defends `s` - a real player of `w`, alive, not in creative, within `reach` of the core (across), «Доверие» from
     * «Знакомый», not «Враг» here.
     */
    static boolean defends(World w, SettlementRegistrySC r, SettlementRegistrySC.Settlement s, EntityPlayer p, int reach) {
        if (!real(p) || p.worldObj != w || !p.isEntityAlive() || p.capabilities.isCreativeMode
                || s.distSq(p.posX, p.posZ) > (double) reach * reach) {
            return false;
        }
        UUID u = p.getUniqueID();
        SettlementRegistrySC.PlayerRec pr = r.playerIfAny(u);
        SettlementRegistrySC.LocalRec l = s.localIfAny(u);
        return pr != null && RaidRulesSC.defender(pr.reputation) && !(l != null && l.enemy(w.getTotalWorldTime()));
    }

    /** Peaceful: the raiders (EntityMob) would remove themselves on their first tick - no raid. */
    static boolean peaceful(World w) {
        return w.difficultySetting == EnumDifficulty.PEACEFUL;
    }

    /** A real player on the server (a FakePlayer gets no packets and defends nothing - but the world test's own). */
    static boolean real(EntityPlayer p) {
        return p instanceof EntityPlayerMP && !(p instanceof FakePlayer);
    }

    /**
     * The Substrate unloaded while the server runs (nobody left there): its raids are abandoned - lost (ПН-4, ПН-5). The server
     * stopping unloads it too: then they just end (clearAll), nothing is recorded.
     */
    @SubscribeEvent
    public void onUnload(WorldEvent.Unload e) {
        if (RUNS.isEmpty() || e.world == null || e.world.isRemote || !(e.world instanceof WorldServer)) {
            return;
        }
        MinecraftServer srv = MinecraftServer.getServer();
        if (srv == null || !srv.isServerRunning()) {
            return;
        }
        int dim = e.world.provider.dimensionId;
        for (RaidRunSC run : new ArrayList<RaidRunSC>(RUNS.values())) {
            if (run.dim == dim) {
                RUNS.remove(Integer.valueOf(run.id));
                run.abandon((WorldServer) e.world);
            }
        }
    }

    // ------------------------------------------------------------------ the raiders

    /** A raider loaded again: of a raid still running - back into it; else (a restart, the raid over meanwhile) an ordinary mob. */
    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent e) {
        if (e.world.isRemote || !(e.entity instanceof ISubstrateNativeSC) || !(e.entity instanceof EntityLiving)) {
            return;
        }
        NBTTagCompound d = e.entity.getEntityData();
        if (!d.hasKey(RAIDER_NBT)) {
            return;
        }
        RaidRunSC run = RUNS.get(Integer.valueOf(d.getInteger(RAIDER_NBT)));
        if (run == null || !run.adopt((EntityLiving) e.entity)) {
            release((EntityLiving) e.entity);
        }
    }

    /** A raider of settlement `id`: the NBT, and it stays while the raid runs. */
    static void enlist(EntityLiving e, int id) {
        e.getEntityData().setInteger(RAIDER_NBT, id);
        e.func_110163_bv();
    }

    /** No raider any more: the NBT off, the vanilla despawn again (EntityLiving.persistenceRequired). */
    static void release(EntityLiving e) {
        e.getEntityData().removeTag(RAIDER_NBT);
        try {
            ReflectionHelper.setPrivateValue(EntityLiving.class, e, Boolean.FALSE, "persistenceRequired", "field_82179_bU");
        } catch (RuntimeException ex) {
            // another mapping: it stays (a Marauder with a full bag never despawns anyway)
        }
    }
}
