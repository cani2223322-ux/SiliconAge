package com.sc.settle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import com.sc.dim.SubstrateBlocksSC;
import com.sc.dim.SubstrateConfigSC;
import com.sc.dim.SubstrateSC;
import com.sc.dim.SubstrateWildBlocksSC;
import com.sc.init.ModItems;
import com.sc.mob.ItemCombatLootSC;
import com.sc.mob.MobItemsSC;
import com.sc.ruin.ItemRuinFinderSC;
import com.sc.settle.npc.EntityGathererSC;
import com.sc.settle.npc.NpcNamesSC;
import com.sc.util.Material;
import com.sc.util.PlayerMsgSC;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * ПД-5 (docs/plan-ideas-2026-10-26.md, «Ожившие поселения», ★ A, part 1): the Gatherers' day. Every CHECK_TICKS of the Substrate's world
 * tick (rule dimension.gatherers, by day - SettlementSC's clock, WORK_FROM..WORK_UNTIL): a Settlement or a City whose core is loaded and
 * with a real player (not creative, not its «Враг») within NEAR of the core sends PER_SIZE[size] Gatherers (settle.npc.EntityGathererSC)
 * out of its gate to a druse or a fumarole within REACH of the dome (found once a day, kept in Settlement.extra EXTRA); they gather
 * GATHER_TICKS and come home; a hostile on one - the call for help (a chat line with the compass direction and the distance to every
 * player within NEAR) and the guards' alarm; the Gatherer saved (alive and home, its attacker dead) - +RESCUE_REP «Доверия»
 * (ReputationSC.SRC_RESCUE) and a gift to the players who were near, once a game day a settlement (EXTRA's RESCUE_DAY). No player near -
 * they come home at once (nothing is simulated). Players never hurt them (EntityGathererSC's attackEntityFrom). SCMod registers INSTANCE
 * on FML's bus (the world tick) and Forge's (LivingAttackEvent) - the coordinator's.
 * <p>
 * The outings are memory only (OUTINGS, by id - the Gatherer's NBT): a restart forgets them and their Gatherers leave on their first tick.
 * The rescue (МБ-1 А): an attacked one (ATTACKED) all of whose attackers were killed (none just gone), SAFE_TICKS after, still alive -
 * the real players who hurt the attackers (not in creative, not «Враг») get the rescue (once a game day a settlement) or a thank-you.
 * The attacks: a Lineman / a Marauder goes for a Gatherer within MOB_REACH (EntityAITargetGathererSC), and once an outing AMBUSH_PCT
 * of an ambush at the spot.
 * <p>
 * Fixed (the coordinator's): the class, INSTANCE, EXTRA and its fields, the numbers' names, clearAll. The rest - ПД-5's.
 */
public final class GathererSC {

    public static final GathererSC INSTANCE = new GathererSC();
    /** Settlement.extra key and its fields: today's spot {x, y, z}, the day it was found, the last rescue's day. */
    public static final String EXTRA = "F", SPOT = "spot", SPOT_DAY = "sd", RESCUE_DAY = "rd";
    /** A player this close to the core (the proposal's 96); the spots within this of the dome's edge; how often it looks. TODO(balance) */
    public static final int NEAR = 96, REACH = 64, CHECK_TICKS = 200;
    /** Gatherers out at once by SettlementSizeSC ordinal (Post none, Settlement 1, City 2). */
    public static final int[] PER_SIZE = {0, 1, 2};
    /** The work's window (the day's ticks), the gathering's length, the rescue's «Доверие». TODO(balance) */
    public static final int WORK_FROM = 1000, WORK_UNTIL = 10000, GATHER_TICKS = 600, RESCUE_REP = 10;
    /** ПД-5: the spot's search - the columns tried, the ring's start past the dome's edge, the blocks below the top looked at. */
    public static final int SPOT_TRIES = 200, SPOT_FROM = 8, SPOT_DEPTH = 3;
    /** ПД-5: no player within NEAR this long - the Gatherer goes home at once (leaves); the call for help at most this often. */
    public static final int ALONE_TICKS = 400, CALL_TICKS = 200;
    /** ПД-5: the rescue - the attackers dead or this far for SAFE_TICKS. */
    public static final int SAFE_REACH = 24, SAFE_TICKS = 100;
    /** ПД-5: a hostile this near makes it run home. */
    public static final int DANGER = 12;
    /**
     * МБ-1 А: a Lineman / a Marauder goes for a Gatherer out this near (after a player: EntityAITargetGathererSC); once an outing (at the
     * spot) AMBUSH_PCT of an ambush - 1-2 of them AMBUSH_FROM..AMBUSH_TO from the spot, on the Gatherer at once. TODO(balance)
     */
    public static final int MOB_REACH = 20, AMBUSH_PCT = 25, AMBUSH_FROM = 10, AMBUSH_TO = 14;

    private static final Random RAND = new Random();
    private static final Map<Long, Outing> OUTINGS = new HashMap<Long, Outing>();

    /** One Gatherer out: its settlement, its spot, the attack it's under. Server memory. */
    public static final class Outing {
        public final long id;
        public final int settlement, dim;
        public final int[] spot, gate;
        /** The spot's kind: true - a druse, false - a fumarole. */
        public final boolean druse;
        UUID gatherer;
        long aloneSince = -1L, lastCall = -1000L, safeSince = -1L;
        final List<Integer> attackers = new ArrayList<Integer>();
        /** МБ-1 А: the ambush rolled (once an outing); the attackers killed, one gone without being killed (no rescue then); the real
         * players who hurt an attacker (the rescue's reward is theirs). */
        boolean ambushRolled, escaped;
        int killed;
        final List<UUID> helpers = new ArrayList<UUID>();

        Outing(long id, int settlement, int dim, int[] spot, int[] gate, boolean druse) {
            this.id = id;
            this.settlement = settlement;
            this.dim = dim;
            this.spot = spot;
            this.gate = gate;
            this.druse = druse;
        }
    }

    private GathererSC() {
    }

    // ================================================================== pure

    /** Pure: Gatherers out at once from a settlement of SettlementSizeSC ordinal `size`. */
    public static int perSize(int size) {
        return size >= 0 && size < PER_SIZE.length ? PER_SIZE[size] : 0;
    }

    /** Pure: world time `worldTime` is in the work's window. */
    public static boolean workTime(long worldTime) {
        long t = (worldTime % 24000L + 24000L) % 24000L;
        return t >= WORK_FROM && t < WORK_UNTIL;
    }

    /** Pure: a rescue may be rewarded on game day `day` - the last one (`rescueDay`, -1 none) was another day. */
    public static boolean rescueDue(long rescueDay, long day) {
        return rescueDay != day;
    }

    // ================================================================== the Gatherer asks

    /** The Gatherer's outing (its NBT id), null - a stale one (a restart, its outing over): it leaves. */
    public static Outing claim(EntityGathererSC g, long id) {
        synchronized (OUTINGS) {
            Outing o = OUTINGS.get(Long.valueOf(id));
            if (o == null || o.gatherer != null && !o.gatherer.equals(g.getUniqueID())) {
                return null;
            }
            o.gatherer = g.getUniqueID();
            return o;
        }
    }

    /** The Gatherer is home (or gone): its outing is over. */
    public static void done(long id) {
        synchronized (OUTINGS) {
            OUTINGS.remove(Long.valueOf(id));
        }
    }

    /** Server stopped: the outings in memory (their Gatherers leave on their next load - EntityGathererSC.NBT). */
    public static void clearAll() {
        synchronized (OUTINGS) {
            OUTINGS.clear();
        }
    }

    // ================================================================== the tick

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side != Side.SERVER || !(e.world instanceof WorldServer) || !SubstrateSC.is(e.world)
                || e.world.getTotalWorldTime() % CHECK_TICKS != 0L) {
            return;
        }
        WorldServer w = (WorldServer) e.world;
        watch(w);
        if (!SubstrateConfigSC.gatherers || w.difficultySetting == EnumDifficulty.PEACEFUL || !workTime(w.getWorldTime()) || w.playerEntities.isEmpty()) {
            return;
        }
        SettlementRegistrySC r = SettlementRegistrySC.get(w);
        if (r == null) {
            return;
        }
        for (SettlementRegistrySC.Settlement s : new ArrayList<SettlementRegistrySC.Settlement>(r.all())) {
            if (s.dim == w.provider.dimensionId && perSize(s.size) > 0 && !SettlementSC.isEmbassy(s) && !SettlementSC.isRelay(s)
                    && w.blockExists(s.x, s.y, s.z) && playerNear(w, s, s.x + 0.5D, s.z + 0.5D, NEAR) && out(s.id) < perSize(s.size)) {
                send(w, r, s);
            }
        }
    }

    /** The outings of settlement `id` running. */
    private static int out(int id) {
        int n = 0;
        synchronized (OUTINGS) {
            for (Outing o : OUTINGS.values()) {
                n += o.settlement == id ? 1 : 0;
            }
        }
        return n;
    }

    /** A real player not in creative, not «Враг» of `s`, within `reach` across of (x, z). */
    private static boolean playerNear(World w, SettlementRegistrySC.Settlement s, double x, double z, double reach) {
        long now = w.getTotalWorldTime();
        for (Object o : w.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (!PlayerMsgSC.isReal(p) || p.capabilities.isCreativeMode || !p.isEntityAlive()) {
                continue;
            }
            SettlementRegistrySC.LocalRec l = s == null ? null : s.localIfAny(p.getUniqueID());
            double dx = p.posX - x, dz = p.posZ - z;
            if (!(l != null && l.enemy(now)) && dx * dx + dz * dz <= reach * reach) {
                return true;
            }
        }
        return false;
    }

    /** One Gatherer out of `s`'s gate to today's spot (none today - nobody goes). */
    private static EntityGathererSC send(WorldServer w, SettlementRegistrySC r, SettlementRegistrySC.Settlement s) {
        int[] spot = daySpot(w, r, s);
        int[] gate = s.point(SettlementRegistrySC.P_GATE);
        if (spot == null || gate == null || gate.length != 3 || !w.blockExists(gate[0], gate[1], gate[2])) {
            return null;
        }
        long id;
        do {
            id = RAND.nextLong();
        } while (id == 0L);
        boolean druse = w.getBlock(spot[0], spot[1], spot[2]) == SubstrateBlocksSC.druse;
        Outing o = new Outing(id, s.id, s.dim, spot, gate, druse);
        EntityGathererSC g = new EntityGathererSC(w);
        g.join(s, id);
        g.setLocationAndAngles(gate[0] + 0.5D, gate[1], gate[2] + 0.5D, RAND.nextFloat() * 360F, 0F);
        synchronized (OUTINGS) {
            OUTINGS.put(Long.valueOf(id), o);
        }
        if (!w.spawnEntityInWorld(g)) {
            done(id);
            return null;
        }
        o.gatherer = g.getUniqueID();
        return g;
    }

    /**
     * Today's spot of `s` (found once a day, kept in its record): SPOT_TRIES random loaded columns in the ring radius + SPOT_FROM ..
     * radius + REACH - the top solid block and SPOT_DEPTH below it a druse or a fumarole; the nearest one. Null - none today.
     */
    static int[] daySpot(World w, SettlementRegistrySC r, SettlementRegistrySC.Settlement s) {
        long day = SettlementSC.day(w);
        NBTTagCompound f = s.extra.getCompoundTag(EXTRA);
        if (f.hasKey(SPOT_DAY) && f.getLong(SPOT_DAY) == day) {
            int[] p = f.getIntArray(SPOT);
            return p.length == 3 && w.blockExists(p[0], p[1], p[2]) && spotBlock(w, p[0], p[1], p[2]) ? p : null;   // never loads a chunk
        }
        int[] best = null;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < SPOT_TRIES; i++) {
            double a = RAND.nextDouble() * Math.PI * 2D, d = s.radius + SPOT_FROM + RAND.nextDouble() * (REACH - SPOT_FROM);
            int x = MathHelper.floor_double(s.x + 0.5D + Math.cos(a) * d), z = MathHelper.floor_double(s.z + 0.5D + Math.sin(a) * d);
            if (!w.blockExists(x, 64, z)) {
                continue;                                       // never loads a chunk
            }
            int top = w.getTopSolidOrLiquidBlock(x, z);
            for (int y = top; y >= top - SPOT_DEPTH && y > 0; y--) {
                if (spotBlock(w, x, y, z)) {
                    double dd = s.distSq(x + 0.5D, z + 0.5D);
                    if (dd < bestD) {
                        bestD = dd;
                        best = new int[]{x, y, z};
                    }
                    break;
                }
            }
        }
        f.setLong(SPOT_DAY, day);
        if (best != null) {
            f.setIntArray(SPOT, best);
        } else {
            f.removeTag(SPOT);
        }
        s.extra.setTag(EXTRA, f);
        r.markDirty();
        return best;
    }

    /** A druse (any) or a fumarole (the vent's meta 0) at (x, y, z). */
    static boolean spotBlock(World w, int x, int y, int z) {
        Block b = w.getBlock(x, y, z);
        return b != null && (b == SubstrateBlocksSC.druse || b == SubstrateWildBlocksSC.vent && w.getBlockMetadata(x, y, z) == 0);
    }

    /** Every outing of `w`: nobody near - its Gatherer leaves; the rescue's watch. */
    private static void watch(WorldServer w) {
        List<Outing> l;
        synchronized (OUTINGS) {
            l = new ArrayList<Outing>(OUTINGS.values());
        }
        if (l.isEmpty()) {
            return;
        }
        SettlementRegistrySC r = SettlementRegistrySC.get(w);
        long now = w.getTotalWorldTime();
        for (Outing o : l) {
            if (o.dim != w.provider.dimensionId) {
                continue;
            }
            EntityGathererSC g = find(w, o);
            SettlementRegistrySC.Settlement s = r == null ? null : r.byId(o.settlement);
            if (g == null || s == null) {
                if (g == null && (o.aloneSince < 0L ? (o.aloneSince = now) : o.aloneSince) <= now - ALONE_TICKS) {
                    done(o.id);                                 // its chunk unloaded long enough: the outing is over (it leaves when loaded)
                }
                continue;
            }
            if (!playerNear(w, s, g.posX, g.posZ, NEAR)) {
                if (o.aloneSince < 0L) {
                    o.aloneSince = now;
                } else if (now - o.aloneSince >= ALONE_TICKS) {
                    g.leave();
                    done(o.id);
                    continue;
                }
            } else {
                o.aloneSince = -1L;
            }
            if (!o.ambushRolled && g.gatherState() == EntityGathererSC.GATHERING) {
                o.ambushRolled = true;
                if (w.difficultySetting != EnumDifficulty.PEACEFUL && RAND.nextInt(100) < AMBUSH_PCT) {
                    ambush(w, s, o, g);
                }
            }
            rescueWatch(w, r, s, o, g, now);
        }
    }

    /**
     * МБ-1 А: the ambush at the spot - 1-2 Linemen / Marauders AMBUSH_FROM..AMBUSH_TO from the spot (the far side from the dome, loaded
     * columns only, not over the dome's edge), on the Gatherer at once (its attackers: the call for help).
     */
    private static void ambush(WorldServer w, SettlementRegistrySC.Settlement s, Outing o, EntityGathererSC g) {
        int n = 1 + RAND.nextInt(2);
        double away = Math.atan2(o.spot[2] + 0.5D - (s.z + 0.5D), o.spot[0] + 0.5D - (s.x + 0.5D));
        double keep = s.radius + 4D;
        for (int tries = 0; n > 0 && tries < 12; tries++) {
            double a = away + (RAND.nextDouble() - 0.5D) * Math.PI * 0.7D, d = AMBUSH_FROM + RAND.nextDouble() * (AMBUSH_TO - AMBUSH_FROM);
            int x = MathHelper.floor_double(o.spot[0] + 0.5D + Math.cos(a) * d), z = MathHelper.floor_double(o.spot[2] + 0.5D + Math.sin(a) * d);
            if (!w.blockExists(x, 64, z) || s.distSq(x + 0.5D, z + 0.5D) <= keep * keep) {
                continue;                                       // never loads a chunk; never inside the dome
            }
            int y = w.getTopSolidOrLiquidBlock(x, z);
            if (y <= 0) {
                continue;
            }
            int kind = RAND.nextBoolean() ? com.sc.mob.MobEntitiesSC.ID_LINEMAN : com.sc.mob.MobEntitiesSC.ID_MARAUDER;
            net.minecraft.entity.EntityLiving e = com.sc.mob.SpawnDirectorSC.spawnAt(w, kind, x + 0.5D, y, z + 0.5D, true, false);
            if (e == null) {
                continue;
            }
            e.setAttackTarget(g);
            attacked(g, o, e);
            n--;
        }
    }

    /** The loaded Gatherer of `o`, or null. */
    private static EntityGathererSC find(WorldServer w, Outing o) {
        if (o.gatherer == null) {
            return null;
        }
        for (Object e : w.loadedEntityList) {
            if (e instanceof EntityGathererSC && !((Entity) e).isDead && o.gatherer.equals(((Entity) e).getUniqueID())) {
                return (EntityGathererSC) e;
            }
        }
        return null;
    }

    // ================================================================== the attack, the rescue

    /** Forge: a hostile hits a Gatherer - ATTACKED, its attacker remembered, the call for help (at most every CALL_TICKS). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onAttack(LivingAttackEvent e) {
        if (!(e.entityLiving instanceof EntityGathererSC) || e.entityLiving.worldObj.isRemote || e.source == null) {
            return;
        }
        Entity by = e.source.getEntity();
        if (!(by instanceof IMob) || !(by instanceof EntityLivingBase)) {
            return;
        }
        EntityGathererSC g = (EntityGathererSC) e.entityLiving;
        Outing o = outing(g);
        if (o == null) {
            return;
        }
        attacked(g, o, (EntityLivingBase) by);
    }

    /** `g` of outing `o` is under attack by `by` (the world test calls it too). */
    public static void attacked(EntityGathererSC g, Outing o, EntityLivingBase by) {
        g.setGatherState(EntityGathererSC.ATTACKED);
        synchronized (OUTINGS) {
            if (!o.attackers.contains(Integer.valueOf(by.getEntityId()))) {
                o.attackers.add(Integer.valueOf(by.getEntityId()));
            }
        }
        o.safeSince = -1L;
        long now = g.worldObj.getTotalWorldTime();
        if (now - o.lastCall < CALL_TICKS) {
            return;
        }
        o.lastCall = now;
        IChatComponent name = NpcNamesSC.name(g.nameCode());
        for (Object p : g.worldObj.playerEntities) {
            EntityPlayer pl = (EntityPlayer) p;
            double dx = g.posX - pl.posX, dz = g.posZ - pl.posZ;
            if (PlayerMsgSC.isReal(pl) && dx * dx + dz * dz <= (double) NEAR * NEAR) {
                PlayerMsgSC.tell(pl, EnumChatFormatting.RED, "sc.gatherer.call", name,
                        new ChatComponentTranslation("sc.finder.dir." + ItemRuinFinderSC.DIRS[ItemRuinFinderSC.direction((int) dx, (int) dz)]),
                        String.valueOf((int) Math.round(Math.sqrt(dx * dx + dz * dz))));
            }
        }
    }

    /**
     * ATTACKED: МБ-1 А - saved only when every attacker was killed (onDeath counts them) and SAFE_TICKS passed since the last; one gone
     * without being killed (despawned, unloaded) - no rescue: the Gatherer just goes home. (SAFE_REACH - the old "far enough" - no more.)
     */
    private static void rescueWatch(WorldServer w, SettlementRegistrySC r, SettlementRegistrySC.Settlement s, Outing o, EntityGathererSC g, long now) {
        if (g.gatherState() != EntityGathererSC.ATTACKED) {
            return;
        }
        boolean left;
        synchronized (OUTINGS) {
            for (Iterator<Integer> it = o.attackers.iterator(); it.hasNext(); ) {
                Entity a = w.getEntityByID(it.next().intValue());
                if (a instanceof EntityLivingBase && !a.isDead && ((EntityLivingBase) a).getHealth() <= 0F) {
                    it.remove();                                // dying (its death event missed): killed
                    o.killed++;
                } else if (a == null || a.isDead || !(a instanceof EntityLivingBase)) {
                    it.remove();                                // gone, not killed
                    o.escaped = true;
                }
            }
            left = !o.attackers.isEmpty();
        }
        if (left) {
            o.safeSince = -1L;
            return;
        }
        if (o.escaped || o.killed <= 0) {
            synchronized (OUTINGS) {
                o.escaped = false;
                o.killed = 0;
                o.helpers.clear();
            }
            o.safeSince = -1L;
            g.setGatherState(EntityGathererSC.HOME);          // not a rescue: home all the same
            return;
        }
        if (o.safeSince < 0L) {
            o.safeSince = now;
            return;
        }
        if (now - o.safeSince < SAFE_TICKS) {
            return;
        }
        saved(w, r, s, o, g);
    }

    /**
     * The rescue: МБ-1 А - the real players who hurt its attackers (Outing.helpers), not in creative, not its settlement's «Враг», in
     * this world - the reward once a game day a settlement, else a thank-you.
     */
    static void saved(World w, SettlementRegistrySC r, SettlementRegistrySC.Settlement s, Outing o, EntityGathererSC g) {
        long day = SettlementSC.day(w);
        long now = w.getTotalWorldTime();
        NBTTagCompound f = s.extra.getCompoundTag(EXTRA);
        boolean due = rescueDue(f.hasKey(RESCUE_DAY) ? f.getLong(RESCUE_DAY) : -1L, day);
        IChatComponent name = NpcNamesSC.name(g.nameCode());
        boolean anyone = false;
        List<UUID> helpers;
        synchronized (OUTINGS) {
            helpers = new ArrayList<UUID>(o.helpers);
        }
        for (Object p : w.playerEntities) {
            EntityPlayer pl = (EntityPlayer) p;
            if (!PlayerMsgSC.isReal(pl) || pl.capabilities.isCreativeMode || !helpers.contains(pl.getUniqueID())) {
                continue;
            }
            SettlementRegistrySC.LocalRec l = s.localIfAny(pl.getUniqueID());
            if (l != null && l.enemy(now)) {
                continue;
            }
            anyone = true;
            if (due) {
                int got = ReputationSC.give(r.player(pl.getUniqueID()), RESCUE_REP, ReputationSC.SRC_RESCUE, day);
                ItemStack gift = gift(RAND);
                IChatComponent what = new net.minecraft.util.ChatComponentText(gift == null ? "-" : gift.stackSize + " x " + gift.getDisplayName());
                if (gift != null) {
                    pl.inventory.addItemStackToInventory(gift);
                    if (gift.stackSize > 0) {
                        pl.dropPlayerItemWithRandomChoice(gift, false);
                    }
                }
                PlayerMsgSC.tell(pl, EnumChatFormatting.GREEN, "sc.gatherer.saved", name, ReputationSC.signed(got), what);
            } else {
                PlayerMsgSC.tell(pl, EnumChatFormatting.GRAY, "sc.gatherer.savedToday", name);
            }
        }
        if (due && anyone) {
            f.setLong(RESCUE_DAY, day);
            s.extra.setTag(EXTRA, f);
            r.markDirty();
        }
        synchronized (OUTINGS) {
            o.attackers.clear();
            o.helpers.clear();
            o.killed = 0;
            o.escaped = false;
        }
        o.safeSince = -1L;
        g.setGatherState(EntityGathererSC.HOME);              // shaken: home now
    }

    /**
     * The rescue's gift (TODO(balance)): one of 4 copper ingots, 2 tantalum dust, a liquid helium cell, a Natural Wafer; null before init.
     * Server (named in the line by the server's language - the stack's own name).
     */
    public static ItemStack gift(Random r) {
        switch (r.nextInt(4)) {
            case 0:
                return ModItems.ingot == null ? null : copy(ModItems.ingot.stackOf(Material.COPPER), 4);
            case 1:
                return ModItems.dust == null ? null : copy(ModItems.dust.stackOf(Material.TANTALUM), 2);
            case 2:
                return ModItems.liquidHeCell == null ? null : new ItemStack(ModItems.liquidHeCell);
            default:
                return MobItemsSC.loot(ItemCombatLootSC.LootKind.NATURAL_WAFER, 1);
        }
    }

    private static ItemStack copy(ItemStack s, int n) {
        if (s == null) {
            return null;
        }
        ItemStack c = s.copy();
        c.stackSize = n;
        return c;
    }

    /** Forge: a Gatherer killed - the line to the players near; its outing over. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDeath(LivingDeathEvent e) {
        if (e.entityLiving == null || e.entityLiving.worldObj.isRemote) {
            return;
        }
        if (!(e.entityLiving instanceof EntityGathererSC)) {
            Outing o = attackedBy(e.entityLiving);              // МБ-1 А: an attacker killed
            if (o != null) {
                synchronized (OUTINGS) {
                    o.attackers.remove(Integer.valueOf(e.entityLiving.getEntityId()));
                    o.killed++;
                    helper(o, e.source == null ? null : e.source.getEntity());
                }
            }
            return;
        }
        EntityGathererSC g = (EntityGathererSC) e.entityLiving;
        IChatComponent name = NpcNamesSC.name(g.nameCode());
        for (Object p : g.worldObj.playerEntities) {
            EntityPlayer pl = (EntityPlayer) p;
            if (PlayerMsgSC.isReal(pl) && pl.getDistanceSqToEntity(g) <= (double) NEAR * NEAR) {
                PlayerMsgSC.tell(pl, EnumChatFormatting.DARK_RED, "sc.gatherer.died", name);
            }
        }
        done(g.outingId());
    }

    /** Forge, МБ-1 А: a real player hurts a Gatherer's attacker - one of the rescuers (the reward is theirs). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onHurt(LivingHurtEvent e) {
        if (e.isCanceled() || e.entityLiving == null || e.entityLiving.worldObj.isRemote || e.entityLiving instanceof EntityGathererSC
                || e.source == null || !PlayerMsgSC.isReal(e.source.getEntity())) {
            return;
        }
        Outing o = attackedBy(e.entityLiving);
        if (o != null) {
            synchronized (OUTINGS) {
                helper(o, e.source.getEntity());
            }
        }
    }

    /** The outing whose attackers hold `e` (by its entity id, the same world), or null. */
    private static Outing attackedBy(EntityLivingBase e) {
        Integer id = Integer.valueOf(e.getEntityId());
        int dim = e.worldObj.provider.dimensionId;
        synchronized (OUTINGS) {
            for (Outing o : OUTINGS.values()) {
                if (o.dim == dim && o.attackers.contains(id)) {
                    return o;
                }
            }
        }
        return null;
    }

    /** `by` (a real player) one of `o`'s rescuers. Under OUTINGS. */
    private static void helper(Outing o, Entity by) {
        if (PlayerMsgSC.isReal(by) && !o.helpers.contains(by.getUniqueID())) {
            o.helpers.add(by.getUniqueID());
        }
    }

    /** The outing of a loaded Gatherer, or null. */
    public static Outing outing(EntityGathererSC g) {
        synchronized (OUTINGS) {
            return OUTINGS.get(Long.valueOf(g.outingId()));
        }
    }

    /** Any Gatherer out in dimension `dim` (EntityAITargetGathererSC's cheap check before its search). */
    public static boolean anyOuting(int dim) {
        synchronized (OUTINGS) {
            for (Outing o : OUTINGS.values()) {
                if (o.dim == dim) {
                    return true;
                }
            }
        }
        return false;
    }
}
