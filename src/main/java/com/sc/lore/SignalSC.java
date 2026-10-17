package com.sc.lore;

import java.util.ArrayList;
import java.util.List;

import com.sc.dim.SubstrateSC;
import com.sc.dim.SubstrateTerrainSC;
import com.sc.finale.FinaleNetSC;
import com.sc.ruin.ItemRuinFinderSC;
import com.sc.settle.SettlementSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE J (docs/plan-substrate-tech-f5.md §7, rules ПЛ-5..ПЛ-8): the Lithographers' Signal (event 5). Registered by the coordinator
 * on FML's bus (the world tick, logins, dimension changes, respawns) and Forge's (A chunk's load - the cache's place, if J needs it).
 * <ul>
 * <li>the Substrate's world tick (END, every SignalRulesSC.CHECK_TICKS): a new game day (SettlementSC.day) with a player in the Substrate
 * - the roll (SignalRulesSC.rolls), the day kept; at its start - the crossing (crossingFor a random real player there), the beam's
 * base (the crossing's floor, SubstrateTerrainSC.nodeFloorY + 1), SIGNAL_STATE {on: true, x, y, z, t} to everyone in the Substrate,
 * sc.signal.start (the direction from each player: the Pelengator's eight points); at endAt - SIGNAL_STATE {on: false}, sc.signal.end
 * to the players near;</li>
 * <li>while on: a real player within CACHE_REACH of the crossing (its chunk loaded) - the cache once (a vanilla chest at the plaza's
 * middle floor + 1, or the nearest air above it - never into a player's build: only replacing air; SignalRulesSC's contents, a
 * Journal by JournalSC.roll(SRC_SIGNAL)) and the patrol (SpawnDirectorSC.spawnAt ID_HOLLOW_SHELL round it, born in Si-14, its home the
 * plaza, ACT nothing special), sc.signal.cache to him;</li>
 * <li>a player arriving in the Substrate, logging in there, respawning there while it is on - SIGNAL_STATE to him (playerUpdate).</li>
 * </ul>
 * Fixed (the coordinator's): the class, INSTANCE, clearAll, playerUpdate. The bodies J's. Server only (the beam is SignalClientSC's).
 * <p>
 * J's details:
 * <ul>
 * <li>the day: its first look with a real player in the Substrate rolls it (SignalDataSC.rolledDay - never twice, whoever looks, the
 * server restarted or not); a day that rolls keeps its start due (extra DUE = the day) until the look that passes startOf - then
 * the signal begins if that look comes within START_GRACE of it (a player was there at its moment), else it is gone unseen. A new day
 * drops an old day's due one. Nothing runs while the Substrate is unloaded (no player - nothing simulated);</li>
 * <li>the window: by the world's total time (the Overworld's clock: /time set and a night slept don't shorten or stretch it) -
 * startAt = the look that began it, endAt = startAt + DURATION_TICKS;</li>
 * <li>the crossing's cache: the first real player (any game mode) within CACHE_REACH (3D) of the beam's base, the plaza loaded -
 * SignalCacheSC.make; no room yet - the next look tries again;</li>
 * <li>the clients: SIGNAL_STATE {on, x, y, z, t - ticks left} to the whole Substrate at the start and the end, to one player at his
 * login, arrival or respawn (playerUpdate - anywhere else {on: false}: he left it). The client drops the beam itself on leaving the
 * server or the Substrate and when t runs out.</li>
 * </ul>
 * Test helpers (the world test, D): startNow, endNow, cacheNow.
 */
public final class SignalSC {

    public static final SignalSC INSTANCE = new SignalSC();
    /** ПЛ-5: a start is begun this late at most (two looks): a later first look means nobody was there at its moment. */
    public static final int START_GRACE = 2 * SignalRulesSC.CHECK_TICKS;
    /** ПЛ-8: the end's line goes to the players this near the beam (blocks, flat). */
    public static final int END_TELL = 128;
    /** SignalDataSC.extra: the day whose signal rolled and has not begun yet (absent - none due). */
    static final String DUE = "Due";

    private SignalSC() {
    }

    /** Server stopped: nothing of that server kept - the Signal's state is SignalDataSC's, saved with the world. */
    public static void clearAll() {
        // nothing in memory
    }

    /** The signal's state to one player (arrived, logged in, respawned in the Substrate); elsewhere - off. */
    public static void playerUpdate(EntityPlayerMP p) {
        if (!real(p)) {
            return;
        }
        World w = p.worldObj;
        SignalDataSC d = SubstrateSC.is(w) ? SignalDataSC.get(w) : null;
        long now = w == null ? 0L : w.getTotalWorldTime();
        FinaleNetSC.send(p, FinaleNetSC.SIGNAL_STATE, d != null && d.on && now < d.endAt ? state(d, now) : off());
    }

    /** The Signal is on in world `w` now (the self-test / world test / the handbook's line). */
    public static boolean active(World w) {
        SignalDataSC d = SignalDataSC.get(w);
        return d != null && d.on;
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side != Side.SERVER || !(e.world instanceof WorldServer) || !SubstrateSC.is(e.world)
                || e.world.getTotalWorldTime() % SignalRulesSC.CHECK_TICKS != 0L) {
            return;
        }
        check((WorldServer) e.world);
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.player instanceof EntityPlayerMP) {
            playerUpdate((EntityPlayerMP) e.player);
        }
    }

    /** Came into the Substrate (the bridge, a jump, a command) - the beam if it burns; went out of it - off. */
    @SubscribeEvent
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.player instanceof EntityPlayerMP) {
            playerUpdate((EntityPlayerMP) e.player);
        }
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.player instanceof EntityPlayerMP) {
            playerUpdate((EntityPlayerMP) e.player);
        }
    }

    // ------------------------------------------------------------------ the day's look

    /** One look (every CHECK_TICKS): the end, the cache - or the day's roll and the start. */
    static void check(WorldServer w) {
        SignalDataSC d = SignalDataSC.get(w);
        if (d == null) {
            return;
        }
        long now = w.getTotalWorldTime();
        if (d.on) {
            if (now >= d.endAt || now < d.startAt) {
                end(w, d);                                      // its time is up (or the data is from another clock)
            } else if (!d.cacheDone) {
                EntityPlayer p = reacher(w, d);
                if (p != null) {
                    SignalCacheSC.make(w, d, p);
                }
            }
            return;
        }
        List<EntityPlayerMP> players = players(w);
        if (players.isEmpty()) {
            return;                                             // ПЛ-5: nothing without a player
        }
        long seed = w.getSeed(), day = SettlementSC.day(w);
        if (d.rolledDay != day) {
            d.rolledDay = day;
            if (SignalRulesSC.rolls(seed, day)) {
                d.extra.setLong(DUE, day);
            } else {
                d.extra.removeTag(DUE);
            }
            d.markDirty();
        }
        if (!d.extra.hasKey(DUE) || d.extra.getLong(DUE) != day) {
            return;
        }
        long into = Math.max(0L, w.getWorldTime()) % SettlementSC.DAY;
        int at = SignalRulesSC.startOf(seed, day);
        if (into < at) {
            return;
        }
        d.extra.removeTag(DUE);
        d.markDirty();
        if (into - at <= START_GRACE) {
            EntityPlayerMP p = players.get(w.rand.nextInt(players.size()));
            int[] c = SignalRulesSC.crossingFor(seed, day, MathHelper.floor_double(p.posX), MathHelper.floor_double(p.posZ));
            if (c != null) {
                begin(w, d, c[0], c[1], SignalRulesSC.DURATION_TICKS);
            }
        }
    }

    /** ПЛ-6, ПЛ-8: the Node of crossing (i, j) wakes for `ticks`: the data, the beam to every client, the line to every player. */
    static void begin(WorldServer w, SignalDataSC d, int i, int j, int ticks) {
        long now = w.getTotalWorldTime();
        d.on = true;
        d.i = i;
        d.j = j;
        d.x = i * SubstrateTerrainSC.GRID;
        d.z = j * SubstrateTerrainSC.GRID;
        d.y = SubstrateTerrainSC.nodeFloorY(w.getSeed(), i, j) + 1;
        d.startAt = now;
        d.endAt = now + Math.max(1, ticks);
        d.cacheDone = false;
        d.markDirty();
        FinaleNetSC.sendToWorld(w, FinaleNetSC.SIGNAL_STATE, state(d, now));
        for (EntityPlayerMP p : players(w)) {
            int dx = d.x - MathHelper.floor_double(p.posX), dz = d.z - MathHelper.floor_double(p.posZ);
            tell(p, EnumChatFormatting.LIGHT_PURPLE, "sc.signal.start",
                    new ChatComponentTranslation("sc.finder.dir." + ItemRuinFinderSC.DIRS[ItemRuinFinderSC.direction(dx, dz)]),
                    String.valueOf((int) Math.round(Math.sqrt(dx * (double) dx + dz * (double) dz))));
        }
    }

    /** ПЛ-8: the beam goes out - off to every client, the line to the players near it. */
    static void end(WorldServer w, SignalDataSC d) {
        d.on = false;
        d.markDirty();
        FinaleNetSC.sendToWorld(w, FinaleNetSC.SIGNAL_STATE, off());
        for (EntityPlayerMP p : players(w)) {
            double dx = p.posX - (d.x + 0.5D), dz = p.posZ - (d.z + 0.5D);
            if (dx * dx + dz * dz <= END_TELL * (double) END_TELL) {
                tell(p, EnumChatFormatting.GRAY, "sc.signal.end");
            }
        }
    }

    /** The first real player within CACHE_REACH of the beam's base, its chunk loaded; null - none. */
    private static EntityPlayer reacher(WorldServer w, SignalDataSC d) {
        if (!w.blockExists(d.x, d.y, d.z)) {
            return null;
        }
        for (EntityPlayerMP p : players(w)) {
            if (p.isEntityAlive() && SignalCacheSC.near(p, d, SignalRulesSC.CACHE_REACH)) {
                return p;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ the world test's helpers

    /** The world test (D): the Signal on now at crossing (i, j) for `ticks` - as a real start (SIGNAL_STATE, the lines). @return it began */
    public static boolean startNow(WorldServer w, int i, int j, int ticks) {
        SignalDataSC d = SignalDataSC.get(w);
        if (d == null) {
            return false;
        }
        begin(w, d, i, j, ticks);
        return true;
    }

    /** The world test: the Signal off now, as at its end (nothing if it is off). */
    public static void endNow(WorldServer w) {
        SignalDataSC d = SignalDataSC.get(w);
        if (d != null && d.on) {
            end(w, d);
        }
    }

    /**
     * The world test: the cache for `p` now, as a real player coming near would make it (`p` may be the test's FakePlayer - it is never
     * chased nor told); null - off, made already, the plaza not loaded or no room.
     */
    public static SignalCacheSC.Result cacheNow(WorldServer w, EntityPlayer p) {
        return SignalCacheSC.make(w, SignalDataSC.get(w), p);
    }

    // ------------------------------------------------------------------ helpers

    /** SIGNAL_STATE on: {on, x, y, z, t - ticks left}. */
    static NBTTagCompound state(SignalDataSC d, long now) {
        NBTTagCompound t = new NBTTagCompound();
        t.setBoolean("on", true);
        t.setInteger("x", d.x);
        t.setInteger("y", d.y);
        t.setInteger("z", d.z);
        t.setInteger("t", (int) Math.max(0L, Math.min(Integer.MAX_VALUE, d.endAt - now)));
        return t;
    }

    static NBTTagCompound off() {
        NBTTagCompound t = new NBTTagCompound();
        t.setBoolean("on", false);
        return t;
    }

    /** A real, connected player (never a FakePlayer). */
    static boolean real(Object o) {
        return o instanceof EntityPlayerMP && !(o instanceof FakePlayer) && ((EntityPlayerMP) o).playerNetServerHandler != null;
    }

    /** The real players of `w`. */
    static List<EntityPlayerMP> players(World w) {
        List<EntityPlayerMP> out = new ArrayList<EntityPlayerMP>();
        for (Object o : w.playerEntities) {
            if (real(o)) {
                out.add((EntityPlayerMP) o);
            }
        }
        return out;
    }

    /** A chat line `key` in `colour` (its arguments inherit it) - to a real player only. */
    static void tell(EntityPlayer p, EnumChatFormatting colour, String key, Object... args) {
        if (!real(p)) {
            return;
        }
        IChatComponent c = new ChatComponentTranslation(key, args);
        c.setChatStyle(new ChatStyle().setColor(colour));
        p.addChatComponentMessage(c);
    }
}
