package com.sc.settle.caravan;

import java.util.ArrayList;
import java.util.List;

import com.sc.dim.SubstrateConfigSC;
import com.sc.dim.SubstrateSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.world.WorldEvent;

/**
 * PACKAGE K (docs/plan-substrate-tech-f5.md §8, rules ПН-12..ПН-16): the caravans (event 7) - every number and rule in CaravanRulesSC.
 * Registered by the coordinator on FML's bus (the world tick) and Forge's (LivingDeathEvent: the master's, the ambushers'; K may add).
 * The Substrate's world tick (END, every CaravanRulesSC.CHECK_TICKS): the rule dimension.caravans on, a caravan day, the window, none
 * under way, none yet today (SettlementRegistrySC.extra EXTRA.day) - the first settlement with a defender near gets one (the master and
 * the guards made out of the dome on the neighbour's side, walking to the gate; ambushRoll - the ambush half-way); its life: the
 * ambushers' deaths, the master's, the arrival, the timeout (CaravanRulesSC); the reward to the defenders (ReputationSC.give with
 * SRC_CARAVAN, the rare deal's record - grantDeal); sc.caravan.* lines to the players near (the arrival, the ambush, beaten off, lost).
 * <p>
 * Fixed (the coordinator's): the class, INSTANCE, clearAll, the rare deal's record (dealOpen / grantDeal / useDeal - the trade of
 * package P asks dealOpen and calls useDeal when the deal is made). K's: the rest. Server only.
 * <p>
 * Package K's: one caravan in the world at a time (run - CaravanRunSC, in memory only: a restart forgets it, as a raid). The check
 * (every CHECK_TICKS; never under -Dsc.worldtest): the rule, a caravan day (caravanDay of SettlementSC.day), its window, none under way, the world's
 * EXTRA.day not today; the registry's first settlement of this world whose core is loaded with a real player (not a FakePlayer, not in
 * creative, alive) within PLAYER_REACH of the core gets it - its start found (CaravanRunSC.begin: loaded room on its plateau), EXTRA.day
 * becomes today (no room - the next check tries again; a day with no player - none, nothing owed). Its id - the world's total time at
 * the start (never met again after a restart: a stale scCaravan). The run's step every RUN_TICKS. Forge's bus: LivingDeathEvent (the
 * master killed - the run's step at once), EntityJoinWorldEvent (an ambusher loaded again - back into its ambush, or an ordinary mob:
 * its NBT and persistence off), WorldEvent.Unload (the Substrate unloaded under a caravan - over, quietly). The caravan's creatures ask
 * claim on their first tick (a stale one leaves). The world test's helpers: startNow, ambushNow, stepNow, stopNow and the questions.
 */
public final class CaravanSC {

    public static final CaravanSC INSTANCE = new CaravanSC();
    /** How often a running caravan takes its step (the ambush, its end; the walk is the master's own AI). */
    public static final int RUN_TICKS = 20;
    /**
     * -Dsc.worldtest=true: no caravan of its own (the world tests count a settlement's creatures by its id - a caravan's three would
     * spoil Ф2's and Ф3's counts); WorldTestFinaleSC makes its one by startNow.
     */
    private static final boolean WORLD_TEST = Boolean.getBoolean("sc.worldtest");

    /** The caravan under way (server, memory only), null none. */
    private static CaravanRunSC run;
    /** How the last caravan ended (CaravanRunSC.END_*), and the players its last beaten-off ambush rewarded - the world test's. */
    private static int lastEnd = CaravanRunSC.END_NONE, lastRewarded;

    private CaravanSC() {
    }

    /** Server stopped: the caravans under way forgotten. K's body. */
    public static void clearAll() {
        run = null;
        lastEnd = CaravanRunSC.END_NONE;
        lastRewarded = 0;
    }

    // ------------------------------------------------------------------ the creatures' questions (server)

    /**
     * The caravan creature `e` of caravan `id` (its scCaravan) belongs to the caravan under way - its master or one of its guards (a
     * new instance after its chunk slept is taken). False: it leaves (a restart, the caravan over meanwhile, 0 - none).
     */
    public static boolean claim(EntityLiving e, long id) {
        CaravanRunSC r = run;
        return r != null && id != 0L && r.id == id && e.worldObj != null && e.worldObj.provider.dimensionId == r.dim && r.claim(e);
    }

    /** The caravan `e` is the master or a guard of (the instance it knows), null none - the caravan's AI asks. */
    public static CaravanRunSC runOf(Entity e) {
        CaravanRunSC r = run;
        return r != null && e != null && !e.worldObj.isRemote && r.member(e) ? r : null;
    }

    /** The caravan under way, null none. */
    public static CaravanRunSC current() {
        return run;
    }

    /** ПН-15: the rare deal «Модуль ПЭ-1: Широкий канал» is open to `p` today (a beaten-off ambush within RARE_DAYS, not used yet). */
    public static boolean dealOpen(EntityPlayer p) {
        NBTTagCompound c = deal(p, false);
        return c != null && c.hasKey(CaravanRulesSC.DEAL_UNTIL)
                && CaravanRulesSC.dealOpen(c.getLong(CaravanRulesSC.DEAL_UNTIL), c.getBoolean(CaravanRulesSC.DEAL_USED), SettlementSC.day(p.worldObj));
    }

    /** ПН-15, K (the ambush beaten off): the rare deal opened to `p` for RARE_DAYS from today (a fresh one, unused). */
    public static void grantDeal(EntityPlayer p) {
        NBTTagCompound c = deal(p, true);
        if (c != null) {
            c.setLong(CaravanRulesSC.DEAL_UNTIL, SettlementSC.day(p.worldObj) + CaravanRulesSC.RARE_DAYS);
            c.setBoolean(CaravanRulesSC.DEAL_USED, false);
            dirty(p);
        }
    }

    /** ПН-15, P (the trade made): the rare deal used up. */
    public static void useDeal(EntityPlayer p) {
        NBTTagCompound c = deal(p, false);
        if (c != null) {
            c.setBoolean(CaravanRulesSC.DEAL_USED, true);
            dirty(p);
        }
    }

    /** The player's caravan record (PlayerRec.extra EXTRA; made when `make`), null - a client, a FakePlayer, no registry. */
    private static NBTTagCompound deal(EntityPlayer p, boolean make) {
        if (p == null || p.worldObj == null || p.worldObj.isRemote || p instanceof FakePlayer) {
            return null;
        }
        SettlementRegistrySC r = SettlementSC.registry(p.worldObj);
        SettlementRegistrySC.PlayerRec rec = r == null ? null : make ? r.player(p.getUniqueID()) : r.playerIfAny(p.getUniqueID());
        if (rec == null) {
            return null;
        }
        if (!rec.extra.hasKey(CaravanRulesSC.EXTRA)) {
            if (!make) {
                return null;
            }
            rec.extra.setTag(CaravanRulesSC.EXTRA, new NBTTagCompound());
        }
        return rec.extra.getCompoundTag(CaravanRulesSC.EXTRA);
    }

    private static void dirty(EntityPlayer p) {
        SettlementRegistrySC r = SettlementSC.registry(p.worldObj);
        if (r != null) {
            r.markDirty();
        }
    }

    // ------------------------------------------------------------------ the tick

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side != Side.SERVER || !(e.world instanceof WorldServer) || !SubstrateSC.is(e.world)) {
            return;
        }
        WorldServer w = (WorldServer) e.world;
        long total = w.getTotalWorldTime();
        if (run != null && run.dim == w.provider.dimensionId && total % RUN_TICKS == 0L) {
            step(w);
        }
        if (total % CaravanRulesSC.CHECK_TICKS == 0L) {
            check(w);
        }
    }

    /** ПН-12: a caravan day, its window, none under way, none today - the first settlement with a player near gets one. */
    private static void check(WorldServer w) {
        if (!SubstrateConfigSC.caravans || run != null || WORLD_TEST || w.playerEntities.isEmpty()) {
            return;
        }
        long today = SettlementSC.day(w);
        if (!CaravanRulesSC.caravanDay(today) || !CaravanRulesSC.inWindow(w.getWorldTime())) {
            return;
        }
        SettlementRegistrySC r = SettlementRegistrySC.get(w);
        if (r == null) {
            return;
        }
        NBTTagCompound x = r.extra.getCompoundTag(CaravanRulesSC.EXTRA);
        if (x.hasKey(CaravanRulesSC.DAY) && x.getLong(CaravanRulesSC.DAY) == today) {
            return;
        }
        int dim = w.provider.dimensionId;
        for (SettlementRegistrySC.Settlement s : new ArrayList<SettlementRegistrySC.Settlement>(r.all())) {
            if (s.dim != dim || !w.blockExists(s.x, s.y, s.z) || !playerNear(w, s)) {
                continue;
            }
            CaravanRunSC c = CaravanRunSC.begin(w, r, s, null, CaravanRulesSC.ambushRoll(w.getSeed(), today), newId(w));
            if (c != null) {
                run = c;
                lastEnd = CaravanRunSC.END_NONE;
                x.setLong(CaravanRulesSC.DAY, today);
                r.extra.setTag(CaravanRulesSC.EXTRA, x);
                r.markDirty();
                return;
            }
        }
    }

    /** ПН-12: a real player (not a FakePlayer), alive, not in creative, within PLAYER_REACH of the core (across). */
    private static boolean playerNear(WorldServer w, SettlementRegistrySC.Settlement s) {
        double reach = CaravanRulesSC.PLAYER_REACH;
        for (Object o : w.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (CaravanRunSC.real(p) && p.isEntityAlive() && !p.capabilities.isCreativeMode && s.distSq(p.posX, p.posZ) <= reach * reach) {
                return true;
            }
        }
        return false;
    }

    /** A caravan's id: the world's total time at its start (it never goes back - a stale one is never met again); never 0. */
    private static long newId(WorldServer w) {
        return Math.max(1L, w.getTotalWorldTime());
    }

    /** The running caravan's step; it ended - forgotten. */
    private static void step(WorldServer w) {
        CaravanRunSC c = run;
        int end = c.step(w);
        if (end != CaravanRunSC.END_NONE) {
            lastEnd = end;
            lastRewarded = c.rewarded();
            if (run == c) {
                run = null;
            }
        } else {
            lastRewarded = c.rewarded();
        }
    }

    // ------------------------------------------------------------------ Forge's bus

    /** ПН-15: the master killed - lost now, not at the next step. Low priority: a death another mod cancels doesn't count. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onDeath(LivingDeathEvent e) {
        if (run == null || e.entityLiving == null || e.entityLiving.worldObj == null || e.entityLiving.worldObj.isRemote
                || !(e.entityLiving instanceof EntityCaravanMasterSC) || !(e.entityLiving.worldObj instanceof WorldServer)
                || e.entityLiving.worldObj.provider.dimensionId != run.dim || !run.member(e.entityLiving) || e.entityLiving.getHealth() > 0F) {
            return;
        }
        step((WorldServer) e.entityLiving.worldObj);            // its health is gone: the step finds it lost
    }

    /**
     * An ambusher loaded again: of the caravan under way - back into its ambush; else (a restart, the caravan over) an ordinary mob.
     * The ambushers are the Substrate's natives only (Marauders, Troopers): any other entity of any world is never asked for its
     * getEntityData (made on first ask - every mob of the pack's worlds would carry an empty ForgeData into its chunk).
     */
    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent e) {
        if (e.world.isRemote || !(e.entity instanceof EntityLiving) || !(e.entity instanceof com.sc.mob.ISubstrateNativeSC)) {
            return;
        }
        NBTTagCompound d = e.entity.getEntityData();
        if (!d.hasKey(CaravanRunSC.AMBUSH_NBT)) {
            return;
        }
        CaravanRunSC c = run;
        if (c != null && c.id == d.getLong(CaravanRunSC.AMBUSH_NBT) && e.world.provider.dimensionId == c.dim) {
            c.adopt((EntityLiving) e.entity);
        } else {
            CaravanRunSC.release((EntityLiving) e.entity);
        }
    }

    /** The Substrate unloaded while the server runs (nobody left there): its caravan is over, quietly. The server stopping: clearAll. */
    @SubscribeEvent
    public void onUnload(WorldEvent.Unload e) {
        CaravanRunSC c = run;
        if (c == null || e.world == null || e.world.isRemote || e.world.provider.dimensionId != c.dim) {
            return;
        }
        MinecraftServer srv = MinecraftServer.getServer();
        if (srv == null || !srv.isServerRunning()) {
            return;
        }
        c.releaseAll();
        run = null;
        lastEnd = CaravanRunSC.END_GONE;
    }

    // ------------------------------------------------------------------ the world test (debug/WorldTestFinaleSC, package D)

    /**
     * The world test: a caravan to `s` now - the day, the window, the player and the day's record skipped (EXTRA.day not written);
     * `by` its defender (a FakePlayer counts here - it is in no player list: the ambush's size and the «Доверие»; the rare deal stays
     * the core's - none for a FakePlayer); `ambush` - one half-way. The chunks round `s` must be loaded (force them). @return it began
     */
    public static boolean startNow(WorldServer w, SettlementRegistrySC.Settlement s, EntityPlayer by, boolean ambush) {
        SettlementRegistrySC r = w == null || !SubstrateSC.is(w) ? null : SettlementRegistrySC.get(w);
        if (r == null || s == null || s.dim != w.provider.dimensionId || run != null || !w.blockExists(s.x, s.y, s.z)) {
            return false;
        }
        CaravanRunSC c = CaravanRunSC.begin(w, r, s, by, ambush, newId(w));
        if (c == null) {
            return false;
        }
        run = c;
        lastEnd = CaravanRunSC.END_NONE;
        lastRewarded = 0;
        return true;
    }

    /** The world test: the running caravan's ambush now, by its master (not waiting for the half-way). @return how many came (0 none) */
    public static int ambushNow(WorldServer w) {
        CaravanRunSC c = run;
        SettlementRegistrySC r = w == null ? null : SettlementRegistrySC.get(w);
        SettlementRegistrySC.Settlement s = c == null || r == null ? null : r.byId(c.settlementId);
        EntityCaravanMasterSC m = c == null ? null : c.master();
        if (s == null || m == null || c.holds()) {
            return 0;
        }
        return c.spawnAmbush(w, r, s, m, -1);
    }

    /** The world test: the running caravan takes its step now (as its tick would). @return it is over (or none ran) */
    public static boolean stepNow(WorldServer w) {
        if (run == null || w == null || w.provider.dimensionId != run.dim) {
            return true;
        }
        step(w);
        return run == null;
    }

    /** The world test: the running caravan ended at once, quietly (its creatures leave, the ambushers released). */
    public static void stopNow(WorldServer w) {
        CaravanRunSC c = run;
        if (c != null) {
            c.end(w);
            run = null;
            lastEnd = CaravanRunSC.END_GONE;
        }
    }

    /** A caravan is under way. */
    public static boolean running() {
        return run != null;
    }

    /** The running caravan's master (loaded, alive), or null. */
    public static EntityCaravanMasterSC master() {
        return run == null ? null : run.master();
    }

    /** The running caravan's guards (loaded, alive; empty none). */
    public static List<EntityCaravanGuardSC> guards() {
        return run == null ? new ArrayList<EntityCaravanGuardSC>() : run.guards();
    }

    /** The running caravan's ambushers (loaded, alive; empty none). */
    public static List<EntityLiving> ambushers() {
        return run == null ? new ArrayList<EntityLiving>() : run.ambushers();
    }

    /** How the last caravan ended (CaravanRunSC.END_*; END_NONE - none yet, or one runs). */
    public static int lastEnd() {
        return lastEnd;
    }

    /** The players the last beaten-off ambush rewarded (0 none). */
    public static int lastRewarded() {
        return lastRewarded;
    }
}
