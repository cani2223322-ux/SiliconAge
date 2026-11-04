package com.sc.mob;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import com.sc.arena.ArenaLayoutSC;
import com.sc.dim.BiomeSubstrateSC;
import com.sc.dim.SubstrateCellsSC;
import com.sc.dim.SubstrateConfigSC;
import com.sc.dim.SubstrateSC;
import com.sc.settle.NpcKindSC;
import com.sc.settle.ReputationSC;
import com.sc.settle.SettlementItemsSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.settle.SettlementSizeSC;
import com.sc.settle.npc.NpcNamesSC;
import com.sc.settle.npc.ai.NpcNavSC;
import com.sc.util.ArmorSuit;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

/**
 * ПД-4 (docs/plan-ideas-2026-10-26.md, «Розыск: именные Отступники», ★ A): the week's wanted named Renegade - its record
 * (RareRegistrySC.WANTED: ALIVE / DEAD, its extra {w: week, city: settlement id, x, z: where it roams now, sx, sz, sd: seen last and
 * when, look: WantedRulesSC.packLook, m1: the first modifier's code, suit: ArmorSuit ordinal, id: the record's id - EntityWantedSC.NBT}),
 * the weekly posting, the roam while unloaded, bringing it (and its escort) into the world near a player, the rewards at its death (each
 * with WantedRulesSC.REWARD_SHARE - MobEventsSC.participants), the Cartographer's word (TradeServerSC's OUT_WANTED_HINT -> hint).
 * SCMod registers INSTANCE on FML's bus (the Substrate's world tick) and Forge's (LivingDeathEvent) - the coordinator's.
 * <p>
 * The tick (the Substrate, END, every TICK_EVERY): a new game week with the rule dimension.wanted on and a City in the registry - a new
 * one posted (the City by the week's seed, a spot RANGE_MIN..RANGE_MAX from its core outside every dome's belt, the Rift and an arena's
 * crossing, the suit, the pair, the name - WantedRulesSC; a line to everyone in the Substrate); the last week's one, if about, leaves
 * (its entity sees another id and goes). NONE (posted, not in the world): it roams ROAM_STEP a ROAM_EVERY within the ring, «seen» there;
 * a real player (not creative) within SPAWN_NEAR of its spot with the spot's chunk loaded brings it in - EntityWantedSC and ESCORT
 * Troopers (born in Si-14, kept; ПД-4 fix: only those not killed yet this week - ESC_LEFT), ALIVE. ALIVE: its spot follows it; past RANGE_MAX it turns home; no real player within LEAVE_REACH for
 * LEAVE_TICKS - it and its escort leave (setDead, no death), NONE at its spot; not seen loaded for LEAVE_TICKS (its chunk unloaded) -
 * NONE too (a saved copy sees the record and goes when loaded). Killed - the rewards, DEAD until the next week; the escort ordinary mobs.
 * Nothing of it but its record is kept between sessions.
 */
public final class WantedSC {

    public static final WantedSC INSTANCE = new WantedSC();
    /** The tick's period (ticks). */
    public static final int TICK_EVERY = 200;
    /** The escort's entity NBT (the record's id) - a Trooper kept while the wanted one is about. */
    public static final String ESCORT_NBT = "scWantedEsc";
    /** The record's extra keys. */
    public static final String W = "w", ID = "id", CITY = "city", X = "x", Z = "z", SX = "sx", SZ = "sz", SD = "sd", LOOK = "look", M1 = "m1",
            M2 = "m2", SUIT = "suit", ESC = "esc", ROAMED = "ra", ALONE = "al", MISSED = "ms",
            // ПД-4 (the review's fix, appended): the escort's Troopers not killed yet this week (missing - all ESCORT) - each trip brings
            // only these, so its escort is no Trooper spawner on demand
            ESC_LEFT = "el";

    private static final Random RAND = new Random();
    /** The loaded wanted one (server; a weak reference - the world holds it). */
    private static WeakReference<EntityWantedSC> loaded = new WeakReference<EntityWantedSC>(null);

    private WantedSC() {
    }

    // ================================================================== asked from outside

    /** Server: there is a wanted Renegade alive this week in `w` (the Substrate) - posted (roaming or in the world), not killed. */
    public static boolean active(World w) {
        RareRegistrySC.Rec r = rec(w);
        return r != null && SubstrateConfigSC.wanted && r.extra.hasKey(W) && r.extra.getLong(W) == SettlementSC.week(w)
                && r.state != RareRegistrySC.DEAD;
    }

    /**
     * Server (the Cartographer's service, paid): the line for `p` - where it was seen last (within WantedRulesSC.HINT_SPREAD), how long ago,
     * its name; or that there is none this week. @return said
     */
    public static boolean hint(EntityPlayer p) {
        if (p == null || p.worldObj == null || p.worldObj.isRemote) {
            return false;
        }
        World w = substrate(p.worldObj);
        RareRegistrySC.Rec r = rec(w);
        if (r == null || !active(w)) {
            tell(p, EnumChatFormatting.GRAY, "sc.wanted.none");
            return false;
        }
        int s = WantedRulesSC.HINT_SPREAD;
        int x = r.extra.getInteger(SX) + RAND.nextInt(2 * s + 1) - s, z = r.extra.getInteger(SZ) + RAND.nextInt(2 * s + 1) - s;
        tell(p, EnumChatFormatting.GOLD, "sc.wanted.hint", fullName(r), String.valueOf(x), String.valueOf(z), String.valueOf(s),
                String.valueOf(WantedRulesSC.minutesAgo(r.extra.getLong(SD), w.getTotalWorldTime())));
        return true;
    }

    /** Server (a Captain's Shift + right click): this week's wanted one, or that nobody is wanted. */
    public static IChatComponent captainLine(World any) {
        World w = substrate(any);
        RareRegistrySC.Rec r = rec(w);
        if (r == null || !active(w)) {
            return new ChatComponentTranslation("sc.wanted.quiet");
        }
        return new ChatComponentTranslation("sc.wanted.captain", fullName(r));
    }

    /** «Хром-7 „Резчик“» of record `r` (each client in its own language). */
    public static IChatComponent fullName(RareRegistrySC.Rec r) {
        int look = r.extra.getInteger(LOOK);
        return fullName(WantedRulesSC.nameOf(look), WantedRulesSC.aliasOf(look));
    }

    public static IChatComponent fullName(int name, int alias) {
        return new ChatComponentTranslation("sc.wanted.fullName", new ChatComponentTranslation("sc.wanted.name." + name),
                new ChatComponentTranslation("sc.wanted.alias." + alias));
    }

    /** EntityWantedSC's own check: `e` is the one its record holds now (this week's id, ALIVE, its UUID). Server. */
    static boolean current(EntityWantedSC e) {
        RareRegistrySC.Rec r = rec(e.worldObj);
        if (!SubstrateConfigSC.wanted || r == null || r.state != RareRegistrySC.ALIVE || e.wantedId() == 0L
                || r.extra.getLong(ID) != e.wantedId() || !e.getUniqueID().equals(r.uuid())) {
            return false;                                       // the rule off (the config) - it leaves too
        }
        e.setLook(r.extra.getInteger(LOOK));                    // the DataWatcher is not saved: the look again after a load
        loaded = new WeakReference<EntityWantedSC>(e);
        return true;
    }

    /** It leaves the world (no death, no drops) - and its escort with it. Server. */
    static void leave(EntityWantedSC e) {
        if (loaded.get() == e) {
            loaded = new WeakReference<EntityWantedSC>(null);
        }
        sparks(e);
        e.setDead();
    }

    /** Server stopped: the loaded wanted's cache, its escort's. */
    public static void clearAll() {
        loaded = new WeakReference<EntityWantedSC>(null);
    }

    // ================================================================== the tick

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side != Side.SERVER || !(e.world instanceof WorldServer) || !SubstrateSC.is(e.world)
                || e.world.getTotalWorldTime() % TICK_EVERY != 0L) {
            return;
        }
        tick((WorldServer) e.world);
    }

    private static void tick(WorldServer w) {
        RareRegistrySC reg = RareRegistrySC.get(w);
        if (reg == null || !SubstrateConfigSC.wanted) {
            return;
        }
        long week = SettlementSC.week(w);
        RareRegistrySC.Rec r = reg.recIfAny(RareRegistrySC.WANTED);
        if (r == null || !r.extra.hasKey(W) || r.extra.getLong(W) != week) {
            post(w, reg, week);
            return;
        }
        long now = w.getTotalWorldTime();
        if (r.state == RareRegistrySC.NONE) {
            roam(w, reg, r, now);
            EntityPlayer near = bringer(w, r.extra.getInteger(X), r.extra.getInteger(Z));
            if (near != null) {
                spawn(w, reg, r, now);
            }
        } else if (r.state == RareRegistrySC.ALIVE) {
            follow(w, reg, r, now);
        }
    }

    /** A new week's wanted one - a City (by the week's seed), the spot, the suit, the pair, the name; the line to the Substrate. */
    private static boolean post(WorldServer w, RareRegistrySC reg, long week) {
        SettlementRegistrySC sr = SettlementRegistrySC.get(w);
        List<SettlementRegistrySC.Settlement> cities = new ArrayList<SettlementRegistrySC.Settlement>();
        if (sr != null) {
            for (SettlementRegistrySC.Settlement s : sr.all()) {
                if (s.dim == w.provider.dimensionId && s.size == SettlementSizeSC.CITY.ordinal()) {
                    cities.add(s);
                }
            }
        }
        if (cities.isEmpty()) {
            return false;                                       // no City yet: nobody posts (asked again next tick)
        }
        Collections.sort(cities, new Comparator<SettlementRegistrySC.Settlement>() {
            @Override
            public int compare(SettlementRegistrySC.Settlement a, SettlementRegistrySC.Settlement b) {
                return a.id - b.id;
            }
        });
        long seed = w.getSeed();
        SettlementRegistrySC.Settlement city = cities.get(WantedRulesSC.city(seed, week, cities.size()));
        int[] at = null;
        for (int k = 0; k < WantedRulesSC.SPOT_TRIES && at == null; k++) {
            int[] s = WantedRulesSC.spot(seed, week, city.x, city.z, k);
            if (allowed(w, sr, s[0], s[1])) {
                at = s;
            }
        }
        if (at == null) {
            at = WantedRulesSC.spot(seed, week, city.x, city.z, 0);
        }
        EntityWantedSC old = loaded.get();
        if (old != null && !old.isDead) {
            leave(old);                                         // last week's one goes (its escort is let go by the join event)
        }
        RareRegistrySC.Rec r = reg.rec(RareRegistrySC.WANTED);
        r.state = RareRegistrySC.NONE;
        r.uuidMost = r.uuidLeast = 0L;
        NBTTagCompound x = new NBTTagCompound();
        long id;
        do {
            id = RAND.nextLong() ^ week;
        } while (id == 0L);
        int[] pair = WantedRulesSC.pair(seed, week);
        int name = WantedRulesSC.name(seed, week), alias = WantedRulesSC.alias(seed, week);
        x.setLong(W, week);
        x.setLong(ID, id);
        x.setInteger(CITY, city.id);
        x.setInteger(X, at[0]);
        x.setInteger(Z, at[1]);
        x.setInteger(SX, at[0]);
        x.setInteger(SZ, at[1]);
        x.setLong(SD, w.getTotalWorldTime());
        x.setInteger(M1, EliteModSC.values()[pair[0]].code());
        x.setInteger(M2, EliteModSC.values()[pair[1]].code());
        x.setInteger(LOOK, WantedRulesSC.packLook(name, alias, EliteModSC.values()[pair[1]].code()));
        x.setInteger(SUIT, (WantedRulesSC.quantum(seed, week) ? ArmorSuit.QUANTUM : ArmorSuit.EXO).ordinal());
        x.setLong(ROAMED, w.getTotalWorldTime());
        r.extra = x;
        reg.markDirty();
        int far = (int) Math.round(Math.sqrt(city.distSq(at[0] + 0.5D, at[1] + 0.5D)));
        for (Object o : w.playerEntities) {
            if (real(o)) {
                tell((EntityPlayer) o, EnumChatFormatting.GOLD, "sc.wanted.posted", captainName(city), fullName(r), String.valueOf(far),
                        NpcNamesSC.name(NpcNamesSC.code(city.nameElement, city.nameNumber)));
            }
        }
        return true;
    }

    /** The City's Captain's name (its roster's CAPTAIN slot), «?» none. */
    private static IChatComponent captainName(SettlementRegistrySC.Settlement s) {
        for (Integer i : s.slotsOf(NpcKindSC.CAPTAIN)) {
            return NpcNamesSC.name(s.slots.get(i.intValue()).nameCode());
        }
        return new ChatComponentText("?");
    }

    /** A spot it may stand on: not in a dome's belt, not in the Rift, not on an arena's crossing. */
    static boolean allowed(World w, SettlementRegistrySC sr, int x, int z) {
        if (sr != null && sr.within(w.provider.dimensionId, x + 0.5D, z + 0.5D, com.sc.settle.SettlementSC.BELT) != null) {
            return false;
        }
        if (SubstrateCellsSC.pureKind(w.getSeed(), SubstrateCellsSC.cellOf(x), SubstrateCellsSC.cellOf(z)) == BiomeSubstrateSC.RIFT) {
            return false;
        }
        return ArenaLayoutSC.arenaNear(w.getSeed(), x, z, 16) == null;
    }

    /** NONE: a step of ROAM_STEP a ROAM_EVERY in a random way within the ring and the bans; «seen» there. */
    private static void roam(WorldServer w, RareRegistrySC reg, RareRegistrySC.Rec r, long now) {
        if (now - r.extra.getLong(ROAMED) < WantedRulesSC.ROAM_EVERY) {
            return;
        }
        SettlementRegistrySC sr = SettlementRegistrySC.get(w);
        SettlementRegistrySC.Settlement city = sr == null ? null : sr.byId(r.extra.getInteger(CITY));
        int x = r.extra.getInteger(X), z = r.extra.getInteger(Z);
        for (int k = 0; k < 8 && city != null; k++) {
            double a = RAND.nextDouble() * Math.PI * 2D;
            int nx = x + (int) Math.round(Math.cos(a) * WantedRulesSC.ROAM_STEP), nz = z + (int) Math.round(Math.sin(a) * WantedRulesSC.ROAM_STEP);
            if (WantedRulesSC.inRing(nx, nz, city.x, city.z) && allowed(w, sr, nx, nz)) {
                x = nx;
                z = nz;
                break;
            }
        }
        r.extra.setInteger(X, x);
        r.extra.setInteger(Z, z);
        seen(r, x, z, now);
        r.extra.setLong(ROAMED, now);
        reg.markDirty();
    }

    private static void seen(RareRegistrySC.Rec r, int x, int z, long now) {
        r.extra.setInteger(SX, x);
        r.extra.setInteger(SZ, z);
        r.extra.setLong(SD, now);
    }

    /** A real player (not creative) within SPAWN_NEAR of (x, z) with the spot's chunk loaded, or null. */
    private static EntityPlayer bringer(WorldServer w, int x, int z) {
        if (!w.blockExists(x, 64, z)) {
            return null;
        }
        double n = WantedRulesSC.SPAWN_NEAR;
        for (Object o : w.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            double dx = p.posX - x, dz = p.posZ - z;
            if (real(p) && !p.capabilities.isCreativeMode && p.isEntityAlive() && dx * dx + dz * dz <= n * n) {
                return p;
            }
        }
        return null;
    }

    /** Brings it (and its escort) into the world at its spot - its suit, its pair, its look; ALIVE. @return it came */
    private static EntityWantedSC spawn(WorldServer w, RareRegistrySC reg, RareRegistrySC.Rec r, long now) {
        int x = r.extra.getInteger(X), z = r.extra.getInteger(Z);
        int y = NpcNavSC.groundY(w, x, z);
        if (y <= 0) {
            return null;
        }
        EntityWantedSC e = new EntityWantedSC(w);
        e.setLocationAndAngles(x + 0.5D, y, z + 0.5D, RAND.nextFloat() * 360F, 0F);
        if (!w.getCollidingBoundingBoxes(e, e.boundingBox).isEmpty() || w.isAnyLiquid(e.boundingBox)) {
            return null;
        }
        e.onSpawnWithEgg(null);
        ArmorSuit[] suits = ArmorSuit.values();
        int si = r.extra.getInteger(SUIT);
        e.dress(si >= 0 && si < suits.length ? suits[si] : ArmorSuit.QUANTUM, w.rand);
        e.setWantedId(r.extra.getLong(ID));
        e.setLook(r.extra.getInteger(LOOK));
        EliteSC.apply(e, EliteModSC.of(r.extra.getInteger(M1)));
        EliteSC.applySecond(e, EliteModSC.of(r.extra.getInteger(M2)));
        e.func_110163_bv();
        BornInSubstrateSC.mark(e);
        if (!w.spawnEntityInWorld(e)) {
            return null;
        }
        reg.alive(RareRegistrySC.WANTED, e, now);
        loaded = new WeakReference<EntityWantedSC>(e);
        NBTTagList esc = new NBTTagList();
        int left = escortLeft(r);
        for (int i = 0; i < left; i++) {
            double a = Math.PI * 2D * i / WantedRulesSC.ESCORT;
            int ex = MathHelper.floor_double(x + 0.5D + Math.cos(a) * 3D), ez = MathHelper.floor_double(z + 0.5D + Math.sin(a) * 3D);
            int ey = NpcNavSC.groundY(w, ex, ez);
            EntityLiving t = ey <= 0 ? null : SpawnDirectorSC.spawnAt(w, MobEntitiesSC.ID_TROOPER, ex + 0.5D, ey, ez + 0.5D, false, false);
            if (t != null) {
                t.getEntityData().setLong(ESCORT_NBT, r.extra.getLong(ID));
                t.func_110163_bv();
                esc.appendTag(new NBTTagString(t.getUniqueID().toString()));
            }
        }
        r.extra.setTag(ESC, esc);
        r.extra.setLong(ALONE, now);
        r.extra.setLong(MISSED, now);
        seen(r, x, z, now);
        reg.markDirty();
        return e;
    }

    /** ALIVE: the spot follows it; the leash; nobody near - it goes; not seen loaded long enough - NONE (its saved copy goes later). */
    private static void follow(WorldServer w, RareRegistrySC reg, RareRegistrySC.Rec r, long now) {
        EntityWantedSC e = loaded.get();
        if (e == null || e.isDead || e.worldObj != w) {
            e = find(w, r);
        }
        if (e == null) {
            if (now - r.extra.getLong(MISSED) >= WantedRulesSC.LEAVE_TICKS) {
                escortLeaves(w, r);                             // the review's fix: its loaded escort goes with it (the rest when loaded)
                backToRecord(reg, r);                           // its chunk unloaded: it roams on its record again
            }
            return;
        }
        r.extra.setLong(MISSED, now);
        int x = MathHelper.floor_double(e.posX), z = MathHelper.floor_double(e.posZ);
        r.extra.setInteger(X, x);
        r.extra.setInteger(Z, z);
        seen(r, x, z, now);
        reg.seen(RareRegistrySC.WANTED, e, now);
        SettlementRegistrySC sr = SettlementRegistrySC.get(w);
        SettlementRegistrySC.Settlement city = sr == null ? null : sr.byId(r.extra.getInteger(CITY));
        if (city != null && city.distSq(e.posX, e.posZ) > (double) WantedRulesSC.RANGE_MAX * WantedRulesSC.RANGE_MAX && e.getAttackTarget() == null) {
            double dx = city.x - e.posX, dz = city.z - e.posZ, len = Math.max(1D, Math.sqrt(dx * dx + dz * dz));
            NpcNavSC.walkTo(e, e.posX + dx / len * 24D, e.posY, e.posZ + dz / len * 24D, 1.0D);    // the leash: back towards home
        }
        boolean someone = false;
        double reach = WantedRulesSC.LEAVE_REACH;
        for (Object o : w.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (real(p) && p.getDistanceSqToEntity(e) <= reach * reach) {
                someone = true;
                break;
            }
        }
        if (someone) {
            r.extra.setLong(ALONE, now);
        } else if (now - r.extra.getLong(ALONE) >= WantedRulesSC.LEAVE_TICKS) {
            leaveWithEscort(w, e, r);
            backToRecord(reg, r);
            return;
        }
        reg.markDirty();
    }

    /** The loaded entity of record `r` in `w` (by its UUID), or null. */
    private static EntityWantedSC find(WorldServer w, RareRegistrySC.Rec r) {
        UUID u = r.uuid();
        for (Object o : w.loadedEntityList) {
            if (o instanceof EntityWantedSC && !((Entity) o).isDead && ((Entity) o).getUniqueID().equals(u)) {
                loaded = new WeakReference<EntityWantedSC>((EntityWantedSC) o);
                return (EntityWantedSC) o;
            }
        }
        return null;
    }

    private static void backToRecord(RareRegistrySC reg, RareRegistrySC.Rec r) {
        r.state = RareRegistrySC.NONE;
        r.uuidMost = r.uuidLeast = 0L;
        r.extra.removeTag(ESC);
        reg.markDirty();
    }

    /** It and its loaded escort leave the world (no death). */
    private static void leaveWithEscort(WorldServer w, EntityWantedSC e, RareRegistrySC.Rec r) {
        escortLeaves(w, r);
        leave(e);
    }

    /** Its loaded escort leaves the world (no death; an unloaded one when loaded - onJoin). */
    private static void escortLeaves(WorldServer w, RareRegistrySC.Rec r) {
        List<String> esc = escort(r);
        if (esc.isEmpty()) {
            return;
        }
        for (Object o : new ArrayList<Object>(w.loadedEntityList)) {
            if (o instanceof EntityLiving && esc.contains(((Entity) o).getUniqueID().toString())) {
                sparks((Entity) o);
                ((Entity) o).setDead();
            }
        }
    }

    /** The escort's Troopers not killed yet this week (ESC_LEFT; missing - all WantedRulesSC.ESCORT). */
    static int escortLeft(RareRegistrySC.Rec r) {
        return r == null || !r.extra.hasKey(ESC_LEFT) ? WantedRulesSC.ESCORT
                : Math.max(0, Math.min(WantedRulesSC.ESCORT, r.extra.getInteger(ESC_LEFT)));
    }

    private static List<String> escort(RareRegistrySC.Rec r) {
        List<String> out = new ArrayList<String>();
        NBTTagList l = r.extra.getTagList(ESC, 8);
        for (int i = 0; i < l.tagCount(); i++) {
            out.add(l.getStringTagAt(i));
        }
        return out;
    }

    // ================================================================== the escort loaded again, the death

    /**
     * A Trooper of an escort loaded again: of the wanted one about now - it stays; of this week's one that left (its chunk unloaded, it
     * went with it, or it is about again with a new escort) - it leaves too (setDead: no death, no drop - it was never let go); of a killed
     * one or another week's - an ordinary mob again.
     */
    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent e) {
        if (e.world.isRemote || !(e.entity instanceof EntityLiving) || !e.entity.getEntityData().hasKey(ESCORT_NBT)) {
            return;
        }
        RareRegistrySC.Rec r = rec(e.world);
        long id = e.entity.getEntityData().getLong(ESCORT_NBT);
        boolean mine = r != null && r.extra.getLong(ID) == id;
        if (mine && SubstrateConfigSC.wanted && r.state == RareRegistrySC.ALIVE && escort(r).contains(e.entity.getUniqueID().toString())) {
            return;
        }
        if (mine && r.state != RareRegistrySC.DEAD) {
            e.entity.setDead();
            return;
        }
        release((EntityLiving) e.entity);
    }

    /** Forge: an escort Trooper of this week's one killed - one fewer comes with it from now on (ESC_LEFT). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEscortDeath(LivingDeathEvent e) {
        if (!(e.entityLiving instanceof EntityLiving) || e.entityLiving.worldObj == null || e.entityLiving.worldObj.isRemote
                || !e.entityLiving.getEntityData().hasKey(ESCORT_NBT)) {
            return;
        }
        RareRegistrySC reg = RareRegistrySC.get(e.entityLiving.worldObj);
        RareRegistrySC.Rec r = reg == null ? null : reg.recIfAny(RareRegistrySC.WANTED);
        if (r == null || r.extra.getLong(ID) != e.entityLiving.getEntityData().getLong(ESCORT_NBT)
                || !escort(r).contains(e.entityLiving.getUniqueID().toString())) {
            return;
        }
        r.extra.setInteger(ESC_LEFT, Math.max(0, escortLeft(r) - 1));
        reg.markDirty();
    }

    /** The escort's tag off, the vanilla despawn again. */
    static void release(EntityLiving e) {
        e.getEntityData().removeTag(ESCORT_NBT);
        try {
            ReflectionHelper.setPrivateValue(EntityLiving.class, e, Boolean.FALSE, "persistenceRequired", "field_82179_bU");
        } catch (RuntimeException ex) {
            // another mapping: it stays
        }
    }

    /**
     * Forge (LOWEST, a death not cancelled): the wanted one killed - each real player with REWARD_SHARE of the damage, born in Si-14
     * (MobDropsSC.qualifies), gets REWARD_CHIPS (outside the day's 64), REWARD_REP «Доверия» (SRC_WANTED) and a Wanted Token; a line to
     * the Substrate; DEAD until the next week; the escort let go.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDeath(LivingDeathEvent e) {
        if (!(e.entityLiving instanceof EntityWantedSC) || e.entityLiving.worldObj.isRemote) {
            return;
        }
        EntityWantedSC mob = (EntityWantedSC) e.entityLiving;
        World w = mob.worldObj;
        RareRegistrySC reg = RareRegistrySC.get(w);
        RareRegistrySC.Rec r = reg == null ? null : reg.recIfAny(RareRegistrySC.WANTED);
        if (r == null || r.extra.getLong(ID) != mob.wantedId() || !SubstrateConfigSC.wanted) {
            return;
        }
        SettlementRegistrySC sr = SettlementRegistrySC.get(w);
        long day = SettlementSC.day(w);
        List<String> names = new ArrayList<String>();
        for (EntityPlayerMP p : MobEventsSC.participants(mob, WantedRulesSC.REWARD_SHARE)) {
            if (!MobDropsSC.qualifies(mob, p) || sr == null) {
                continue;
            }
            int got = ReputationSC.give(sr.player(p.getUniqueID()), WantedRulesSC.REWARD_REP, ReputationSC.SRC_WANTED, day);
            give(p, SettlementItemsSC.chips(WantedRulesSC.REWARD_CHIPS));
            give(p, com.sc.init.IdeaItemsSC.wantedToken(1));
            p.inventoryContainer.detectAndSendChanges();
            tell(p, EnumChatFormatting.GREEN, "sc.wanted.reward", fullName(r), String.valueOf(WantedRulesSC.REWARD_CHIPS), ReputationSC.signed(got));
            names.add(p.getCommandSenderName());
        }
        if (sr != null) {
            sr.markDirty();
        }
        reg.died(RareRegistrySC.WANTED, w.getTotalWorldTime());
        List<String> esc = escort(r);
        for (Object o : w.loadedEntityList) {
            if (o instanceof EntityLiving && esc.contains(((Entity) o).getUniqueID().toString())) {
                release((EntityLiving) o);
            }
        }
        r.extra.removeTag(ESC);
        reg.markDirty();
        loaded = new WeakReference<EntityWantedSC>(null);
        StringBuilder who = new StringBuilder();
        for (String n : names) {
            who.append(who.length() == 0 ? "" : ", ").append(n);
        }
        for (Object o : w.playerEntities) {
            if (real(o)) {
                tell((EntityPlayer) o, EnumChatFormatting.GOLD, "sc.wanted.down", fullName(r), who.length() == 0 ? "-" : who.toString());
            }
        }
    }

    // ================================================================== the world test's hooks

    /** The world test: post this week's one now (a City must exist). @return posted */
    public static boolean postNow(WorldServer w) {
        RareRegistrySC reg = RareRegistrySC.get(w);
        return reg != null && post(w, reg, SettlementSC.week(w));
    }

    /** The world test: bring the posted one into the world now at its spot (no player asked). @return the entity, or null */
    public static EntityWantedSC spawnNow(WorldServer w) {
        RareRegistrySC reg = RareRegistrySC.get(w);
        RareRegistrySC.Rec r = reg == null ? null : reg.recIfAny(RareRegistrySC.WANTED);
        return r == null || r.state != RareRegistrySC.NONE ? null : spawn(w, reg, r, w.getTotalWorldTime());
    }

    // ================================================================== helpers

    private static RareRegistrySC.Rec rec(World w) {
        RareRegistrySC reg = w == null || w.isRemote ? null : RareRegistrySC.get(w);
        return reg == null ? null : reg.recIfAny(RareRegistrySC.WANTED);
    }

    /** The Substrate's world (the record is kept there) from any server world; `w` itself if none. */
    private static World substrate(World w) {
        if (w == null || SubstrateSC.is(w)) {
            return w;
        }
        net.minecraft.server.MinecraftServer srv = net.minecraft.server.MinecraftServer.getServer();
        World s = srv == null || !SubstrateSC.active() ? null : srv.worldServerForDimension(SubstrateSC.dimId());
        return s != null ? s : w;
    }

    private static boolean real(Object o) {
        return o instanceof EntityPlayerMP && !(o instanceof FakePlayer);
    }

    private static void tell(EntityPlayer p, EnumChatFormatting colour, String key, Object... args) {
        if (real(p)) {
            p.addChatComponentMessage(new ChatComponentTranslation(key, args).setChatStyle(new ChatStyle().setColor(colour)));
        }
    }

    private static void give(EntityPlayer p, ItemStack s) {
        if (s == null || s.stackSize <= 0) {
            return;
        }
        p.inventory.addItemStackToInventory(s);
        if (s.stackSize > 0) {
            p.dropPlayerItemWithRandomChoice(s, false);
        }
    }

    private static void sparks(Entity e) {
        if (e.worldObj instanceof WorldServer) {
            ((WorldServer) e.worldObj).func_147487_a("fireworksSpark", e.posX, e.posY + e.height * 0.5D, e.posZ, 16, 0.3D, 0.5D, 0.3D, 0.06D);
        }
    }
}
