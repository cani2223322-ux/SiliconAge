package com.sc.embassy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import com.sc.bridge.BridgeMathSC;
import com.sc.bridge.BridgeNetSC;
import com.sc.bridge.BridgeSoftLandSC;
import com.sc.bridge.BridgeTeleportSC;
import com.sc.dim.SafeSpotSC;
import com.sc.dim.SubstrateEntrySC;
import com.sc.dim.SubstrateSC;
import com.sc.hazard.BridgeEchoSC;
import com.sc.inventory.ContainerTradeSC;
import com.sc.settle.NpcKindSC;
import com.sc.settle.ReputationSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.settle.npc.EntityTunerSC;
import com.sc.settle.npc.NpcNamesSC;
import com.sc.tileentity.TileEntityEmbassyRingSC;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE R (docs/plan-substrate-tech-f6.md §7.3..§7.6, rules ПЯ-3, ПЯ-6..ПЯ-8, ПЯ-15, ПЯ-16): the flights' windows and the moves.
 * Registered by SCMod on FML's bus (ServerTickEvent: the windows; PlayerTickEvent: the stale talons' refunds, the guarantee's watch,
 * every TALON_CHECK ticks; PlayerLoggedOutEvent). Server thread only; the windows are transient (a restart forgets a running one -
 * its talons stay and fly in the next window).
 * <ul>
 * <li>a window a settlement (its id): the countdown, the vortex, the cooling of EmbassyRulesSC; up to PASSENGERS; at an embassy its
 * boarding zone is the ring's vortex (EmbassyLayoutSC.inVortex), at a Substrate dock the plate (EmbassyRulesSC.BOARD_DOCK round the
 * beacon); the ring's tile shows the phase (TileEntityEmbassyRingSC.setPhase), the Substrate dock's channel is the server's portal
 * particles (both LAN players see them) and EmbassyNetSC.FLIGHT_FX;</li>
 * <li>a passenger with a talon of this settlement who steps into the zone during the vortex - only a real player, not riding,
 * nobody riding him (pets and mounts stay): carry() - the target's point (a dock's beacon + 1, the death mark scattered by
 * RESCUE_SCATTER, an embassy's Vokzal), SafeSpotSC.find in the target world (it loads the chunk - «Первый причал» not made yet is
 * made by it), BridgeTeleportSC.teleport (the strictEntry grant comes with it), the bridge's soft landing (BridgeSoftLandSC.arrived:
 * started when he comes out in the air), BridgeEchoSC.crossed(..., portalShield = true); into the Substrate SubstrateEntrySC.NBT_FROM
 * is set to the departure embassy's Vokzal (the Return Capsule goes there), EmbassyTicketSC.setFrom; the talon goes;</li>
 * <li>a talon of an earlier day goes back to its payer (EmbassyTicketSC.Talon.stale - chips into the inventory, else at the feet);</li>
 * <li>ПЯ-13: a guarantor whose friend became that embassy's «Враг» within its days - GUARANTEE_PENALTY, once.</li>
 * </ul>
 * Fixed (the coordinator's): the class, INSTANCE, TALON_CHECK, the entries below. Bodies R's.
 * <p>
 * R's details:
 * <ul>
 * <li>a window opens with its first paid passenger (FlightRunSC.buy) or one flying by his talon («Лететь»): board(); a passenger who
 * missed the vortex keeps his talon for the next window that day; one with a talon of this settlement who steps in unbooked goes
 * too while there is room; nothing runs without a passenger - no window, no tick;</li>
 * <li>the ring's tile is found under the ring's point (P_RING) once a window and told every SYNC_TICKS and at each change (its chunk
 * not loaded - nothing); the open Dispatcher screens of the settlement get a new FLIGHT_STATE at each change;</li>
 * <li>FLIGHT_FX kinds: FX_CHANNEL - a Substrate dock's column of light (t: ticks; re-sent every FX_RESEND for players who came
 * late), FX_CHARGE - its countdown's glow, FX_GONE - the flash where a passenger went (both places);</li>
 * <li>the soft admission's warnings (FlightRunSC.buy) are kept here too: {buyer: the time, the ride, its target, the passenger}.</li>
 * </ul>
 */
public final class FlightSC {

    public static final FlightSC INSTANCE = new FlightSC();
    /** A player's talons and guarantee are looked at this often (ticks). */
    public static final int TALON_CHECK = 100;
    /** SubstrateEntrySC.NBT_FROM's "via" of a flight out (the Return Capsule goes back to the Vokzal). */
    public static final String VIA_EMBASSY = "embassy";
    /** EmbassyNetSC.FLIGHT_FX "k" (RingClientSC.onFx); the players this near get them. */
    public static final int FX_CHANNEL = 1, FX_GONE = 2, FX_CHARGE = 3, FX_REACH = 64;
    /** The ring's tile is told the phase this often besides its changes; the dock's column is re-sent this often (ticks). */
    private static final int SYNC_TICKS = 10, FX_RESEND = 40;
    /** During the vortex the players this near the boarding point are looked at (the zones are smaller). */
    private static final int SCAN_REACH = 24;
    /** The ring's tile is looked for this far down from the ring's middle (and one up): the core under it, its foot sunk. */
    private static final int RING_SEARCH = EmbassyLayoutSC.RING_R + EmbassyLayoutSC.RING_DY + 2;
    /** A chat line about the same thing (mounted, no talon, full) comes again after this (ticks). */
    private static final int SAY_TICKS = 80;

    private static final Map<Integer, Window> WINDOWS = new HashMap<Integer, Window>();
    private static final Map<UUID, Warning> WARNED = new HashMap<UUID, Warning>();
    private static final Map<String, Long> SAID = new HashMap<String, Long>();
    /** The embassies' plans (the vortex's frame), made once a server. */
    private static final Map<Integer, EmbassyLayoutSC.Plan> PLANS = new HashMap<Integer, EmbassyLayoutSC.Plan>();
    private static final Random RAND = new Random();

    /** One settlement's window: when its countdown began (world total time), who is booked in it. */
    private static final class Window {
        final int id, dim;
        final boolean embassy;
        long start;
        final List<UUID> passengers = new ArrayList<UUID>();
        int lastPhase = EmbassyRulesSC.PHASE_IDLE;
        /** The ring's tile (an embassy's), found once. */
        int[] ring;

        Window(int id, int dim, boolean embassy, long start) {
            this.id = id;
            this.dim = dim;
            this.embassy = embassy;
            this.start = start;
        }
    }

    /** A soft admission's warning given: when, for which ride, target and passenger. */
    private static final class Warning {
        final long at;
        final int ride, target;
        final UUID passenger;

        Warning(long at, int ride, int target, UUID passenger) {
            this.at = at;
            this.ride = ride;
            this.target = target;
            this.passenger = passenger;
        }
    }

    private FlightSC() {
    }

    // ================================================================== events

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || WINDOWS.isEmpty()) {
            return;
        }
        for (Window win : new ArrayList<Window>(WINDOWS.values())) {
            try {
                tick(win);
            } catch (RuntimeException ex) {
                WINDOWS.remove(Integer.valueOf(win.id));
                FMLLog.warning("[Silicon Age] Embassy flights: the window of settlement %d failed and is closed: %s", win.id, ex);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof EntityPlayerMP) || e.player instanceof FakePlayer || e.player.worldObj == null
                || e.player.worldObj.isRemote) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) e.player;
        if ((p.worldObj.getTotalWorldTime() + p.getEntityId()) % TALON_CHECK != 0) {
            return;
        }
        look(p);
    }

    @SubscribeEvent
    public void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.player == null) {
            return;
        }
        UUID id = e.player.getUniqueID();
        unbook(id);                                             // his talon stays: the next window that day
        WARNED.remove(id);
        String prefix = id.toString();
        for (Iterator<String> it = SAID.keySet().iterator(); it.hasNext(); ) {
            if (it.next().startsWith(prefix)) {
                it.remove();
            }
        }
    }

    // ================================================================== the windows' questions

    /** The phase of settlement `id`'s window now (EmbassyRulesSC.PHASE_*; IDLE: none). */
    public static int phase(int settlementId) {
        Window win = WINDOWS.get(Integer.valueOf(settlementId));
        long e = win == null ? -1L : elapsed(win);
        return e < 0 ? EmbassyRulesSC.PHASE_IDLE : EmbassyRulesSC.phaseAt(e);
    }

    /** Ticks left of the phase settlement `id`'s window is in (0: none). */
    public static int ticksLeft(int settlementId) {
        Window win = WINDOWS.get(Integer.valueOf(settlementId));
        long e = win == null ? -1L : elapsed(win);
        return e < 0 ? 0 : EmbassyRulesSC.phaseTicksLeft(e);
    }

    /** The passengers booked in settlement `id`'s window (0: none). */
    public static int passengers(int settlementId) {
        Window win = WINDOWS.get(Integer.valueOf(settlementId));
        return win == null || phase(settlementId) == EmbassyRulesSC.PHASE_IDLE ? 0 : win.passengers.size();
    }

    /** Player `who` may still be booked in settlement `id`'s window now (none, idle; the countdown or the vortex with room; his own). */
    public static boolean joinable(int settlementId, UUID who) {
        Window win = WINDOWS.get(Integer.valueOf(settlementId));
        int ph = phase(settlementId);
        if (win == null || ph == EmbassyRulesSC.PHASE_IDLE) {
            return true;
        }
        if (who != null && win.passengers.contains(who)) {
            return ph != EmbassyRulesSC.PHASE_COOL;
        }
        return EmbassyRulesSC.joinable(ph, win.passengers.size());
    }

    /**
     * Books `p` (a paid passenger, or one flying by his talon) into settlement `s`'s window, or starts it (ПЯ-3: the first one starts
     * the countdown). The world test may book a FakePlayer: it is never carried. @return OK, or R_BUSY (cooling, full)
     */
    public static int board(EntityPlayer p, SettlementRegistrySC.Settlement s) {
        if (p == null || s == null || p.worldObj == null || p.worldObj.isRemote) {
            return EmbassyRulesSC.R_NOT_HERE;
        }
        UUID id = p.getUniqueID();
        Integer key = Integer.valueOf(s.id);
        Window win = WINDOWS.get(key);
        int ph = phase(s.id);
        if (win == null || ph == EmbassyRulesSC.PHASE_IDLE) {
            win = new Window(s.id, s.dim, SettlementSC.isEmbassy(s), now(s.dim, p.worldObj));
            WINDOWS.put(key, win);
            ph = EmbassyRulesSC.PHASE_COUNTDOWN;
        } else if (win.passengers.contains(id) ? ph == EmbassyRulesSC.PHASE_COOL : !EmbassyRulesSC.joinable(ph, win.passengers.size())) {
            return EmbassyRulesSC.R_BUSY;
        }
        if (!win.passengers.contains(id)) {
            win.passengers.add(id);
        }
        int secs = (EmbassyRulesSC.phaseTicksLeft(Math.max(0L, elapsed(win))) + 19) / 20;
        if (ph == EmbassyRulesSC.PHASE_VORTEX) {
            FlightRunSC.tell(p, EnumChatFormatting.AQUA, win.embassy ? "sc.flight.open.ring" : "sc.flight.open.dock", String.valueOf(secs));
        } else {
            FlightRunSC.tell(p, EnumChatFormatting.DARK_AQUA, win.embassy ? "sc.flight.countdown.ring" : "sc.flight.countdown.dock",
                    String.valueOf(secs));
        }
        return EmbassyRulesSC.OK;
    }

    /**
     * ПЯ-3, ПР-8 (the world test too): (x, y, z) of world `w` is settlement `s`'s boarding zone - an embassy's ring's vortex
     * (EmbassyLayoutSC.inVortex of its plan), a Substrate dock's plate (BOARD_DOCK across from the beacon, the dock zone's height).
     */
    public static boolean boardingZone(SettlementRegistrySC.Settlement s, World w, double x, double y, double z) {
        if (s == null) {
            return false;
        }
        if (SettlementSC.isEmbassy(s)) {
            EmbassyLayoutSC.Plan plan = plan(s, w);
            return plan != null && EmbassyLayoutSC.inVortex(plan, x, y, z);
        }
        int bx = MathHelper.floor_double(x), bz = MathHelper.floor_double(z);
        return Math.abs(bx - s.dockX) <= EmbassyRulesSC.BOARD_DOCK && Math.abs(bz - s.dockZ) <= EmbassyRulesSC.BOARD_DOCK && s.inDockZone(x, y, z);
    }

    /** The world test: settlement `id`'s window is moved on by `ticks` (as if they went by). @return whether there is one */
    public static boolean age(int settlementId, long ticks) {
        Window win = WINDOWS.get(Integer.valueOf(settlementId));
        if (win != null) {
            win.start -= ticks;
        }
        return win != null;
    }

    // ================================================================== the soft admission (FlightRunSC.buy)

    static void warn(UUID buyer, int ride, int target, UUID passenger, long now) {
        WARNED.put(buyer, new Warning(now, ride, target, passenger));
    }

    /** ПЯ-14: `buyer` was warned of this ride, target and passenger within CONFIRM_TICKS of `now`. */
    static boolean confirmed(UUID buyer, int ride, int target, UUID passenger, long now) {
        Warning x = WARNED.get(buyer);
        return x != null && x.ride == ride && x.target == target && x.passenger.equals(passenger) && EmbassyRulesSC.confirmed(x.at, now);
    }

    static void unwarn(UUID buyer) {
        WARNED.remove(buyer);
    }

    // ================================================================== the window's life

    private static void tick(Window win) {
        WorldServer w = DimensionManager.getWorld(win.dim);
        SettlementRegistrySC reg = w == null ? null : SettlementRegistrySC.get(w);
        SettlementRegistrySC.Settlement s = reg == null ? null : reg.byId(win.id);
        if (w == null || s == null) {
            WINDOWS.remove(Integer.valueOf(win.id));            // the world unloaded, the record gone: the talons wait
            return;
        }
        long elapsed = w.getTotalWorldTime() - win.start;
        int phase = EmbassyRulesSC.phaseAt(elapsed), left = EmbassyRulesSC.phaseTicksLeft(elapsed);
        if (phase != win.lastPhase) {
            win.lastPhase = phase;
            changed(w, s, win, phase, left);
        } else if (elapsed % SYNC_TICKS == 0) {
            showRing(w, s, win, phase, left);
        }
        effects(w, s, win, phase, left, elapsed);
        if (phase == EmbassyRulesSC.PHASE_VORTEX) {
            boardTick(w, reg, s, win);
        } else if (phase == EmbassyRulesSC.PHASE_IDLE) {
            WINDOWS.remove(Integer.valueOf(win.id));
        }
    }

    /** A new phase: the ring, the sound, the dock's channel, the passengers told, the open screens told. */
    private static void changed(WorldServer w, SettlementRegistrySC.Settlement s, Window win, int phase, int left) {
        showRing(w, s, win, phase, left);
        double[] c = centre(s, win);
        int secs = (left + 19) / 20;
        switch (phase) {
            case EmbassyRulesSC.PHASE_COUNTDOWN:
                w.playSoundEffect(c[0], c[1], c[2], "random.orb", 1.0F, 0.5F);
                if (!win.embassy) {
                    fx(w, FX_CHARGE, s.dockX + 0.5D, s.dockY + 1D, s.dockZ + 0.5D, left);
                }
                break;
            case EmbassyRulesSC.PHASE_VORTEX:
                w.playSoundEffect(c[0], c[1], c[2], "portal.trigger", 1.6F, 1.3F);
                if (!win.embassy) {
                    fx(w, FX_CHANNEL, s.dockX + 0.5D, s.dockY + 1D, s.dockZ + 0.5D, left);
                }
                for (UUID id : win.passengers) {
                    FlightRunSC.tell(FlightRunSC.online(id), EnumChatFormatting.AQUA, win.embassy ? "sc.flight.open.ring" : "sc.flight.open.dock",
                            String.valueOf(secs));
                }
                break;
            case EmbassyRulesSC.PHASE_COOL:
                w.playSoundEffect(c[0], c[1], c[2], "random.fizz", 1.2F, 0.6F);
                for (UUID id : win.passengers) {
                    FlightRunSC.tell(FlightRunSC.online(id), EnumChatFormatting.GOLD, "sc.flight.missed");
                }
                win.passengers.clear();                          // they keep their talons: the next window today
                break;
            default:
        }
        refreshScreens(w, s.id);
    }

    /** The ring's tile shows the phase (an embassy's; its chunk loaded). */
    private static void showRing(World w, SettlementRegistrySC.Settlement s, Window win, int phase, int left) {
        if (!win.embassy) {
            return;
        }
        TileEntityEmbassyRingSC ring = ring(w, s, win);
        if (ring != null) {
            ring.setPhase(phase, left);
        }
    }

    /** The ring's core under its middle (found once a window; null - its chunk not loaded, none there). */
    private static TileEntityEmbassyRingSC ring(World w, SettlementRegistrySC.Settlement s, Window win) {
        int[] at = s.point(EmbassyLayoutSC.P_RING);
        if (at == null || !w.getChunkProvider().chunkExists(at[0] >> 4, at[2] >> 4)) {
            return null;
        }
        if (win.ring != null) {
            TileEntity te = w.getTileEntity(win.ring[0], win.ring[1], win.ring[2]);
            if (te instanceof TileEntityEmbassyRingSC) {
                return (TileEntityEmbassyRingSC) te;
            }
            win.ring = null;
        }
        for (int dy = 1; dy >= -RING_SEARCH; dy--) {
            int y = at[1] + dy;
            if (y < 1 || y > 254) {
                continue;
            }
            TileEntity te = w.getTileEntity(at[0], y, at[2]);
            if (te instanceof TileEntityEmbassyRingSC
                    && (((TileEntityEmbassyRingSC) te).embassyId() == s.id || ((TileEntityEmbassyRingSC) te).embassyId() < 0)) {
                win.ring = new int[]{at[0], y, at[2]};
                return (TileEntityEmbassyRingSC) te;
            }
        }
        return null;
    }

    /** A Substrate dock's channel: the server's portal particles over the plate (every player within 16 sees them), the column re-sent. */
    private static void effects(WorldServer w, SettlementRegistrySC.Settlement s, Window win, int phase, int left, long elapsed) {
        if (win.embassy) {
            return;                                             // the ring's tile draws the embassy's (RingClientSC's renderer)
        }
        double x = s.dockX + 0.5D, y = s.dockY + 1.2D, z = s.dockZ + 0.5D;
        if (phase == EmbassyRulesSC.PHASE_COUNTDOWN && elapsed % 5 == 0) {
            w.func_147487_a("portal", x, y, z, 6 + (int) (elapsed * 24 / EmbassyRulesSC.COUNTDOWN_TICKS), 1.4D, 0.3D, 1.4D, 0.4D);
        } else if (phase == EmbassyRulesSC.PHASE_VORTEX) {
            if (elapsed % 3 == 0) {
                w.func_147487_a("portal", x, y + 0.6D, z, 28, 1.9D, 1.2D, 1.9D, 0.8D);
            }
            if (elapsed % 10 == 0) {
                w.func_147487_a("fireworksSpark", x, y + 0.4D, z, 6, 1.6D, 0.4D, 1.6D, 0.04D);
            }
            if (elapsed % FX_RESEND == 0) {
                fx(w, FX_CHANNEL, x, s.dockY + 1D, z, left);
            }
        } else if (phase == EmbassyRulesSC.PHASE_COOL && elapsed % 20 == 0 && left > EmbassyRulesSC.COOL_TICKS / 2) {
            w.func_147487_a("smoke", x, y, z, 6, 1.5D, 0.2D, 1.5D, 0.01D);
        }
    }

    /** ПЯ-3, ПЯ-15: the vortex - who stands in the zone with a talon of this settlement goes. */
    private static void boardTick(WorldServer w, SettlementRegistrySC reg, SettlementRegistrySC.Settlement s, Window win) {
        double[] c = centre(s, win);
        List<EntityPlayerMP> in = new ArrayList<EntityPlayerMP>();
        for (Object o : w.playerEntities) {
            if (!(o instanceof EntityPlayerMP) || o instanceof FakePlayer) {
                continue;
            }
            EntityPlayerMP p = (EntityPlayerMP) o;
            if (p.isEntityAlive() && p.playerNetServerHandler != null && p.getDistanceSq(c[0], c[1], c[2]) <= SCAN_REACH * SCAN_REACH
                    && boardingZone(s, w, p.posX, p.posY, p.posZ)) {
                in.add(p);
            }
        }
        for (EntityPlayerMP p : in) {                           // apart from the walk: a carry takes the player out of playerEntities
            fly(w, reg, s, win, p);
        }
    }

    /** One player in the zone during the vortex. */
    private static void fly(WorldServer w, SettlementRegistrySC reg, SettlementRegistrySC.Settlement s, Window win, EntityPlayerMP p) {
        UUID id = p.getUniqueID();
        SettlementRegistrySC.PlayerRec rec = reg.playerIfAny(id);
        EmbassyTicketSC.Talon k = EmbassyTicketSC.talon(rec);
        if (k == null || k.settlement != s.id) {
            say(p, "sc.flight.noTalon");
            return;
        }
        if (k.stale(SettlementSC.day(w))) {
            FlightRunSC.refund(reg, id, rec, k, p, null);
            return;
        }
        if (!win.passengers.contains(id)) {
            if (win.passengers.size() >= EmbassyRulesSC.PASSENGERS) {
                say(p, "sc.flight.full");
                return;
            }
            win.passengers.add(id);                             // a talon of this place from earlier today: room left, he goes too
        }
        if (p.ridingEntity != null || p.riddenByEntity != null) {
            say(p, "sc.flight.mounted");                        // pets and mounts stay: off the saddle first
            return;
        }
        if (!FlightRunSC.channel()) {
            say(p, EmbassyRulesSC.refuseKey(EmbassyRulesSC.R_OFF));
            return;
        }
        double[] to = FlightTargetsSC.point(reg, rec, k, w.getSeed(), RAND);
        double fx = p.posX, fy = p.posY + 1D, fz = p.posZ;
        IChatComponent name = targetName(reg, rec, k, w.getSeed());
        boolean outbound = EmbassyRulesSC.outbound(k.ride);
        if (to == null || !carry(p, (int) to[0], to[1], to[2], to[3], outbound ? s.id : -1)) {
            win.passengers.remove(id);
            FlightRunSC.refund(reg, id, rec, k, p, "sc.flight.channelLost");   // ПЯ-5: «Канал не найден» - the chips go back
            return;
        }
        win.passengers.remove(id);
        EmbassyTicketSC.setTalon(rec, null);
        if (k.ride == EmbassyRulesSC.RIDE_RESCUE) {
            EmbassyTicketSC.setRescued(rec, rec.deathAt);       // ПЯ-7: one a death
        } else if (k.ride == EmbassyRulesSC.RIDE_BACK) {
            EmbassyTicketSC.clearBack(rec);                     // the return half is used
        }
        reg.markDirty();
        fx(w, FX_GONE, fx, fy, fz, 0);
        p.addChatComponentMessage(new ChatComponentTranslation("sc.flight.carried", name)
                .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.DARK_AQUA)));
    }

    /**
     * Carries `p` to (x, y, z) of dimension `dim` the flights' way (see the class comment); `embassyId` - the embassy whose Vokzal is
     * the way back (into the Substrate) or -1. @return whether he went
     */
    public static boolean carry(EntityPlayerMP p, int dim, double x, double y, double z, int embassyId) {
        if (p == null || p instanceof FakePlayer || p.worldObj == null || p.worldObj.isRemote || p.isDead || p.playerNetServerHandler == null
                || p.ridingEntity != null || p.riddenByEntity != null) {
            return false;
        }
        MinecraftServer srv = MinecraftServer.getServer();
        WorldServer to = srv == null ? null : srv.worldServerForDimension(dim);
        if (to == null) {
            return false;
        }
        int[] at = SafeSpotSC.find(to, p, MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z),
                FlightTargetsSC.SAFE_RADIUS);
        if (at == null) {
            return false;
        }
        World from = p.worldObj;
        int fromDim = from.provider.dimensionId;
        double fx = p.posX, fy = p.posY, fz = p.posZ;
        double ax = at[0] + 0.5D, ay = at[1], az = at[2] + 0.5D;
        Entity moved = BridgeTeleportSC.teleport(p, dim, ax, ay, az, p.rotationYaw);   // the strictEntry grant comes with it
        if (!(moved instanceof EntityPlayerMP)) {
            return false;
        }
        EntityPlayerMP m = (EntityPlayerMP) moved;
        BridgeSoftLandSC.arrived(m);                            // out into the air - the bridge's soft landing (only then, as the bridge's)
        from.playSoundEffect(fx, fy, fz, "mob.endermen.portal", 0.9F, 0.8F);
        m.worldObj.playSoundEffect(ax, ay, az, "mob.endermen.portal", 0.9F, 1.0F);
        BridgeNetSC.CHANNEL.sendTo(new BridgeNetSC.Arrive(BridgeMathSC.SPACE), m);     // the bridge's arrival flash
        SettlementRegistrySC reg = SettlementRegistrySC.get(m.worldObj);
        SettlementRegistrySC.Settlement e = reg == null || embassyId < 0 ? null : reg.byId(embassyId);
        if (e != null && SubstrateSC.isDim(dim) && !SubstrateSC.isDim(fromDim)) {
            // ПЯ-15: the Return Capsule goes back to the Vokzal of the embassy flown out of (over its beacon, outside the dome). The
            // arrival (SubstrateEntrySC, inside the transfer above) noted the vortex; this replaces it.
            NBTTagCompound data = m.getEntityData();
            NBTTagCompound kept = data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
            NBTTagCompound back = new NBTTagCompound();
            back.setInteger("dim", e.dim);
            back.setDouble("x", e.dockX + 0.5D);
            back.setDouble("y", e.dockY + 1D);
            back.setDouble("z", e.dockZ + 0.5D);
            back.setString("via", VIA_EMBASSY);
            kept.setTag(SubstrateEntrySC.NBT_FROM, back);
            data.setTag(EntityPlayer.PERSISTED_NBT_TAG, kept);
            EmbassyTicketSC.setFrom(reg.player(m.getUniqueID()), embassyId);
            reg.markDirty();
        }
        BridgeEchoSC.crossed(m, fromDim, dim, ax, ay, az, true);    // a dock: no squad (sheltered); the death mark: the intended risk
        return true;
    }

    /** The open Dispatcher screens of settlement `id` in world `w` get a new FLIGHT_STATE (the phase changed). */
    private static void refreshScreens(World w, int id) {
        for (Object o : new ArrayList<Object>(w.playerEntities)) {
            if (!(o instanceof EntityPlayerMP) || o instanceof FakePlayer) {
                continue;
            }
            EntityPlayerMP p = (EntityPlayerMP) o;
            if (p.openContainer instanceof ContainerTradeSC) {
                EntityTunerSC t = ((ContainerTradeSC) p.openContainer).getTuner();
                if (t != null && t.kind() == NpcKindSC.DISPATCHER && t.settlementId() == id) {
                    FlightServerSC.sendState(p, t);
                }
            }
        }
    }

    // ================================================================== a player's look (every TALON_CHECK)

    /**
     * A player's look every TALON_CHECK (the world test may call it with a FakePlayer): the chips owed paid, a stale talon refunded,
     * the stale talon of the friend he paid for while that friend is away (the friend's own look would - he may never come back), the
     * guarantee's watch. Not on the death screen (a player's tick comes there too): his inventory has just been spilt - the next look
     * after the respawn pays.
     */
    public static void look(EntityPlayer p) {
        if (p == null || p.worldObj == null || p.worldObj.isRemote || !p.isEntityAlive()) {
            return;
        }
        SettlementRegistrySC reg = SettlementRegistrySC.get(p.worldObj);
        SettlementRegistrySC.PlayerRec rec = reg == null ? null : reg.playerIfAny(p.getUniqueID());
        if (rec == null) {
            return;
        }
        FlightRunSC.payOwed(reg, rec, p);
        long today = SettlementSC.day(p.worldObj);
        EmbassyTicketSC.Talon k = EmbassyTicketSC.talon(rec);
        if (k != null && k.stale(today)) {
            unbook(p.getUniqueID());
            FlightRunSC.refund(reg, p.getUniqueID(), rec, k, p, null);     // ПЯ-6: «неиспользованный возвращается на рассвете»
        }
        NBTTagCompound g = EmbassyTicketSC.guarantee(rec);
        UUID friend = g == null ? null : SettlementRegistrySC.uuid(g.getString("f"));
        SettlementRegistrySC.PlayerRec fr = friend == null || FlightRunSC.online(friend) != null ? null : reg.playerIfAny(friend);
        EmbassyTicketSC.Talon gift = EmbassyTicketSC.talon(fr);
        if (gift != null && gift.stale(today) && p.getUniqueID().toString().equals(gift.payer)) {
            FlightRunSC.refund(reg, friend, fr, gift, null, null);          // his chips back now, the friend away
        }
        watchGuarantee(reg, rec, p);
    }

    /**
     * ПЯ-13 (the world test too): `p`'s last guarantee - the friend became that embassy's «Враг» (his status's start: its end less
     * ENEMY_TICKS) within GUARANTEE_TICKS of it - GUARANTEE_PENALTY, once. @return whether it was taken now
     */
    public static boolean watchGuarantee(SettlementRegistrySC reg, SettlementRegistrySC.PlayerRec rec, EntityPlayer p) {
        NBTTagCompound g = EmbassyTicketSC.guarantee(rec);
        if (reg == null || g == null || EmbassyTicketSC.guaranteePaid(rec)) {
            return false;
        }
        SettlementRegistrySC.Settlement s = reg.byId(g.getInteger("s"));
        UUID friend = SettlementRegistrySC.uuid(g.getString("f"));
        SettlementRegistrySC.LocalRec l = s == null || friend == null ? null : s.localIfAny(friend);
        if (l == null || l.enemyUntil <= 0L || !EmbassyRulesSC.guaranteeBroken(g.getLong("a"), l.enemyUntil - ReputationSC.ENEMY_TICKS)) {
            return false;
        }
        EmbassyTicketSC.payGuarantee(rec);
        int lost = ReputationSC.give(rec, EmbassyRulesSC.GUARANTEE_PENALTY, ReputationSC.SRC_PENALTY, SettlementSC.day(p.worldObj));
        reg.markDirty();
        String name = g.getString(FlightRunSC.FRIEND_NAME);
        FlightRunSC.tell(p, EnumChatFormatting.RED, "sc.flight.guarantee.penalty", name.isEmpty() ? "?" : name, ReputationSC.signed(lost));
        return true;
    }

    // ================================================================== helpers

    /** The world total time a window of dimension `dim` counts by (the total time is the server's own: the same in every world). */
    private static long now(int dim, World fallback) {
        World w = DimensionManager.getWorld(dim);
        return (w != null ? w : fallback).getTotalWorldTime();
    }

    /** Ticks since the window's countdown began (-1: its world isn't loaded). */
    private static long elapsed(Window win) {
        World w = DimensionManager.getWorld(win.dim);
        return w == null ? -1L : w.getTotalWorldTime() - win.start;
    }

    /** The middle of the boarding zone: the ring's middle, the dock's plate. */
    private static double[] centre(SettlementRegistrySC.Settlement s, Window win) {
        int[] r = win.embassy ? s.point(EmbassyLayoutSC.P_RING) : null;
        if (r != null) {
            return new double[]{r[0] + 0.5D, r[1] + 0.5D, r[2] + 0.5D};
        }
        return new double[]{s.dockX + 0.5D, s.dockY + 1.5D, s.dockZ + 0.5D};
    }

    /** An embassy's plan (the ring's frame), made once a server (again if the record's core or floor changed). */
    private static EmbassyLayoutSC.Plan plan(SettlementRegistrySC.Settlement s, World w) {
        Integer key = Integer.valueOf(s.id);
        EmbassyLayoutSC.Plan p = PLANS.get(key);
        if (p == null || p.coreX != s.x || p.coreZ != s.z || p.floorY != s.floorY) {
            p = EmbassyLayoutSC.planOf(s, w == null ? 0L : w.getSeed());
            PLANS.put(key, p);
        }
        return p;
    }

    /** The talon's target as the chat names it: a dome, an embassy, the death mark. */
    private static IChatComponent targetName(SettlementRegistrySC reg, SettlementRegistrySC.PlayerRec rec, EmbassyTicketSC.Talon k, long seed) {
        if (k.ride == EmbassyRulesSC.RIDE_RESCUE) {
            return new ChatComponentTranslation("sc.flight.target.mark", String.valueOf(rec.deathX), String.valueOf(rec.deathZ));
        }
        SettlementRegistrySC.Settlement s = k.target >= 0 ? reg.byId(k.target) : null;
        if ((k.ride == EmbassyRulesSC.RIDE_INTRO || k.ride == EmbassyRulesSC.RIDE_FIRST) && SubstrateSC.active()) {
            SettlementRegistrySC.Settlement f = reg.first(SubstrateSC.dimId());
            s = f != null ? f : s;
        }
        if (s != null) {
            return SettlementSC.displayName(s);
        }
        int[] n = FlightTargetsSC.name(reg, k.ride, k.target, seed);
        return new ChatComponentTranslation("sc.settle.name", NpcNamesSC.name(n[0]));
    }

    /** FLIGHT_FX to the players near (x, z) of `w`. */
    private static void fx(World w, int kind, double x, double y, double z, int ticks) {
        NBTTagCompound d = new NBTTagCompound();
        d.setByte("k", (byte) kind);
        d.setDouble("x", x);
        d.setDouble("y", y);
        d.setDouble("z", z);
        d.setInteger("t", ticks);
        EmbassyNetSC.sendNear(w, x, z, FX_REACH, EmbassyNetSC.FLIGHT_FX, d);
    }

    /** A chat line, at most once in SAY_TICKS a player and key. */
    private static void say(EntityPlayerMP p, String key) {
        String k = p.getUniqueID() + "|" + key;
        long now = p.worldObj.getTotalWorldTime();
        Long next = SAID.get(k);
        if (next != null && now < next.longValue()) {
            return;
        }
        SAID.put(k, Long.valueOf(now + SAY_TICKS));
        FlightRunSC.tell(p, EnumChatFormatting.GOLD, key);
    }

    /** `id` leaves every window's list (his talon stays; FlightRunSC.decline - it goes). */
    static void unbook(UUID id) {
        for (Window win : WINDOWS.values()) {
            win.passengers.remove(id);
        }
    }

    /** Server stopped: the windows and the warnings go. */
    public static void clearAll() {
        WINDOWS.clear();
        WARNED.clear();
        SAID.clear();
        PLANS.clear();
        FlightTargetsSC.clearAll();
    }
}
