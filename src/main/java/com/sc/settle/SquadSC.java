package com.sc.settle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import com.sc.dim.SubstrateConfigSC;
import com.sc.mob.MobDropsSC;
import com.sc.mob.MobEventsSC;
import com.sc.util.PlayerMsgSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.event.ClickEvent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.EntityInteractEvent;

/**
 * ПД-2 (docs/plan-ideas-2026-10-26.md, «Звено для двоих», ★ A): «звено» - two..MAX players who fight together in the Substrate. Made by
 * a Credit Chip's right click on another player (an invitation; he answers in the chat - [Принять] / [Отклонить], CommandSquadSC) or,
 * without asking, by standing in one vanilla scoreboard team. What it changes (rule dimension.squads):
 * <ul>
 * <li>a contract's kill (EconomyEventsSC.onDeath -> contractCredits): the killer (MobEventsSC.killerOf, as before) and every mate of his
 * online with SHARE of the mob's damage count it; the kill's «Доверие» stays the killer's;</li>
 * <li>a mate's death mark is a bridge target (TileEntityBridgeControllerSC.si14Targets: TravelRulesSC.T_MATE_DEATH) and a Dispatcher's
 * rescue flight's target (FlightTargetsSC / FlightServerSC: RIDE_RESCUE for a mate's mark - one rescue a death, the mate's own
 * EmbassyTicketSC.rescuedAt).</li>
 * </ul>
 * Kept in the settlements' registry, per player (PlayerRec.extra EXTRA {s: the squad's id (long), t: the world time joined, n: his last
 * name - the bridge's list names an offline mate by it}): a LAN friend's own record; a squad is everyone with the same id. The
 * invitations are memory only (by the invited one's UUID, INVITE_TICKS long; logging out forgets his and those to him). Server only; a
 * FakePlayer is never in one, never invites and is never invited.
 * <p>
 * Fixed (the coordinator's): the class, INSTANCE (SCMod registers it on Forge's bus and FML's), EXTRA / SHARE / MAX, the signatures of
 * contractCredits / mates / matesOf / clearAll. The bodies are ПД-2's.
 */
public final class SquadSC {

    public static final SquadSC INSTANCE = new SquadSC();
    /** PlayerRec.extra key and its fields: the squad's id, when he joined, his last name. */
    public static final String EXTRA = "Q", ID = "s", JOINED = "t", NAME = "n";
    /** ПД-2: a mate counts a contract's kill with at least this share of the damage. TODO(balance) */
    public static final float SHARE = 0.10F;
    /** ПД-2: players in one squad at most. TODO(balance) */
    public static final int MAX = 4;
    /** ПД-2: an invitation holds this long (a minute). TODO(balance) */
    public static final int INVITE_TICKS = 1200;

    /** The invitations by the invited one's UUID. */
    private static final Map<UUID, Invite> INVITES = new HashMap<UUID, Invite>();
    private static final Random RAND = new Random();

    private static final class Invite {
        final UUID from;
        final String fromName;
        final long until;

        Invite(UUID from, String fromName, long until) {
            this.from = from;
            this.fromName = fromName;
            this.until = until;
        }
    }

    private SquadSC() {
    }

    // ================================================================== pure

    /**
     * Pure: who counts a contract's kill - `killer` first (null: nobody, as before), then every one of `mates` whose share in `shares` is at
     * least `share` (never the killer twice).
     */
    public static List<UUID> credited(Map<UUID, Float> shares, UUID killer, Set<UUID> mates, float share) {
        if (killer == null) {
            return Collections.emptyList();
        }
        List<UUID> out = new ArrayList<UUID>();
        out.add(killer);
        if (shares == null || mates == null) {
            return out;
        }
        for (Map.Entry<UUID, Float> e : shares.entrySet()) {
            UUID u = e.getKey();
            if (u != null && !u.equals(killer) && mates.contains(u) && e.getValue() != null && e.getValue().floatValue() + 1.0E-4F >= share) {
                out.add(u);
            }
        }
        return out;
    }

    /** Pure: an invitation made at world time `made` is still good at `now`. */
    public static boolean inviteOpen(long made, long now) {
        return now >= made && now - made <= INVITE_TICKS;
    }

    /** Pure: a squad of `members` may take one more. */
    public static boolean room(int members) {
        return members < MAX;
    }

    // ================================================================== the credit

    /**
     * Server (EconomyEventsSC.onDeath, a qualifying kill by `killer`): who counts it for his contracts - the killer first, then (dimension
     * .squads on) his squad mates and scoreboard team mates online with SHARE of the damage (MobEventsSC.participants). Never null; empty
     * without a killer.
     */
    public static List<EntityPlayerMP> contractCredits(EntityLivingBase mob, EntityPlayerMP killer) {
        if (killer == null) {
            return Collections.emptyList();
        }
        List<EntityPlayerMP> out = new ArrayList<EntityPlayerMP>(1);
        out.add(killer);
        if (!SubstrateConfigSC.squads || mob == null) {
            return out;
        }
        Map<UUID, EntityPlayerMP> online = new HashMap<UUID, EntityPlayerMP>();
        Set<UUID> mates = new HashSet<UUID>();
        for (EntityPlayerMP p : MobEventsSC.participants(mob, SHARE)) {
            if (p != killer && mates(killer, p) && MobDropsSC.qualifies(mob, p)) {
                mates.add(p.getUniqueID());
                online.put(p.getUniqueID(), p);
            }
        }
        if (mates.isEmpty()) {
            return out;
        }
        for (UUID u : credited(MobEventsSC.damageShares(mob), killer.getUniqueID(), mates, SHARE)) {
            EntityPlayerMP p = online.get(u);
            if (p != null && p != killer) {
                out.add(p);
            }
        }
        return out;
    }

    // ================================================================== who is whose

    /** Server: `a` and `b` are mates - one squad (EXTRA's id) or one scoreboard team; never a player with himself. */
    public static boolean mates(EntityPlayer a, EntityPlayer b) {
        if (!SubstrateConfigSC.squads || a == null || b == null || a == b || a instanceof FakePlayer || b instanceof FakePlayer
                || a.getUniqueID().equals(b.getUniqueID())) {
            return false;
        }
        if (a.getTeam() != null && a.isOnSameTeam(b)) {
            return true;
        }
        SettlementRegistrySC reg = a.worldObj == null || a.worldObj.isRemote ? null : SettlementRegistrySC.get(a.worldObj);
        if (reg == null) {
            return false;
        }
        long s = squadOf(reg.playerIfAny(a.getUniqueID()));
        return s != 0L && s == squadOf(reg.playerIfAny(b.getUniqueID()));
    }

    /** Server: the UUIDs of `player`'s squad mates (not himself; the team's aren't listed - they're asked by mates()). */
    public static List<UUID> matesOf(UUID player) {
        SettlementRegistrySC reg = SettlementRegistrySC.get();
        if (!SubstrateConfigSC.squads || reg == null || player == null) {
            return Collections.emptyList();
        }
        return membersOf(reg, squadOf(reg.playerIfAny(player)), player);
    }

    /**
     * Server: everyone `p` counts as a mate - his squad's members (online or not) and the online players of his scoreboard team - for
     * the bridge's list and the rescue flights. Never himself; empty with the rule off.
     */
    public static List<UUID> allMates(EntityPlayer p) {
        List<UUID> out = new ArrayList<UUID>();
        if (!SubstrateConfigSC.squads || p == null || p instanceof FakePlayer) {
            return out;
        }
        out.addAll(matesOf(p.getUniqueID()));
        if (p.getTeam() != null) {
            for (EntityPlayerMP o : online()) {
                if (o != p && !out.contains(o.getUniqueID()) && mates(p, o)) {
                    out.add(o.getUniqueID());
                }
            }
        }
        return out;
    }

    /** Server: the name of player `id` - online, else the one his squad record keeps (Q.n); "?" unknown. */
    public static String nameOf(UUID id) {
        EntityPlayerMP p = PlayerMsgSC.online(id);
        if (p != null) {
            return p.getCommandSenderName();
        }
        SettlementRegistrySC reg = SettlementRegistrySC.get();
        SettlementRegistrySC.PlayerRec r = reg == null ? null : reg.playerIfAny(id);
        String n = r == null ? "" : r.extra.getCompoundTag(EXTRA).getString(NAME);
        return n.isEmpty() ? "?" : n;
    }

    /** The squad id of record `r` (0: none). */
    public static long squadOf(SettlementRegistrySC.PlayerRec r) {
        return r == null ? 0L : r.extra.getCompoundTag(EXTRA).getLong(ID);
    }

    /** The members of squad `s` of `reg` but `except` (null: all). */
    private static List<UUID> membersOf(SettlementRegistrySC reg, long s, UUID except) {
        List<UUID> out = new ArrayList<UUID>();
        if (s == 0L) {
            return out;
        }
        for (Map.Entry<UUID, SettlementRegistrySC.PlayerRec> e : reg.players().entrySet()) {
            if (!e.getKey().equals(except) && squadOf(e.getValue()) == s) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    // ================================================================== the invitation (a Credit Chip's click)

    /** Forge: a real player's right click with a Credit Chip on another real player - an invitation (never a deal; the chip stays). */
    @SubscribeEvent
    public void onInteract(EntityInteractEvent e) {
        EntityPlayer p = e.entityPlayer;
        if (p == null || !(e.target instanceof EntityPlayer) || e.target == p || SettlementItemsSC.creditChip == null) {
            return;
        }
        ItemStack held = p.getCurrentEquippedItem();
        if (held == null || held.getItem() != SettlementItemsSC.creditChip || !SubstrateConfigSC.squads) {
            return;
        }
        e.setCanceled(true);                                    // both sides: no use of the chip on a player
        if (p.worldObj.isRemote || !(p instanceof EntityPlayerMP) || p instanceof FakePlayer || !(e.target instanceof EntityPlayerMP)
                || e.target instanceof FakePlayer) {
            return;
        }
        invite((EntityPlayerMP) p, (EntityPlayerMP) e.target);
    }

    /** Server: `from` invites `to`. @return true if the invitation was sent */
    public static boolean invite(EntityPlayerMP from, EntityPlayerMP to) {
        SettlementRegistrySC reg = SettlementRegistrySC.get(from.worldObj);
        if (reg == null || !SubstrateConfigSC.squads) {
            PlayerMsgSC.tell(from, EnumChatFormatting.GRAY, "sc.squad.off");
            return false;
        }
        long s = squadOf(reg.playerIfAny(from.getUniqueID()));
        if (s != 0L && s == squadOf(reg.playerIfAny(to.getUniqueID()))) {
            return false;                                       // one squad already
        }
        if (s != 0L && !room(membersOf(reg, s, null).size())) {
            PlayerMsgSC.tell(from, EnumChatFormatting.RED, "sc.squad.full", String.valueOf(MAX));
            return false;
        }
        long now = from.worldObj.getTotalWorldTime();
        synchronized (INVITES) {
            INVITES.put(to.getUniqueID(), new Invite(from.getUniqueID(), from.getCommandSenderName(), now));
        }
        String name = from.getCommandSenderName();
        IChatComponent yes = button("sc.squad.accept", "/scsquad accept " + name, EnumChatFormatting.GREEN);
        IChatComponent no = button("sc.squad.decline", "/scsquad decline " + name, EnumChatFormatting.RED);
        to.addChatComponentMessage(new ChatComponentTranslation("sc.squad.invite.got", name, yes, no)
                .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.AQUA)));
        PlayerMsgSC.tell(from, EnumChatFormatting.DARK_AQUA, "sc.squad.invite.sent", to.getCommandSenderName());
        return true;
    }

    private static IChatComponent button(String key, String command, EnumChatFormatting colour) {
        ChatComponentTranslation b = new ChatComponentTranslation(key);
        b.setChatStyle(new ChatStyle().setColor(colour).setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
        return b;
    }

    /** CommandSquadSC «accept <name>»: `p` takes `fromName`'s invitation (if still good). */
    public static void accept(EntityPlayerMP p, String fromName) {
        SettlementRegistrySC reg = SettlementRegistrySC.get(p.worldObj);
        if (reg == null || !SubstrateConfigSC.squads) {
            PlayerMsgSC.tell(p, EnumChatFormatting.GRAY, "sc.squad.off");
            return;
        }
        Invite inv = take(p.getUniqueID(), fromName);
        long now = p.worldObj.getTotalWorldTime();
        if (inv == null || !inviteOpen(inv.until, now)) {
            PlayerMsgSC.tell(p, EnumChatFormatting.GRAY, "sc.squad.expired");
            return;
        }
        join(reg, inv.from, inv.fromName, p.getUniqueID(), p.getCommandSenderName(), now);
    }

    /**
     * Server (accept; the world test): `who` joins `host`'s squad - a new one for both if the host has none (id: the time ^ a random,
     * never 0); `who` leaves his old one first. @return false if the squad is full
     */
    public static boolean join(SettlementRegistrySC reg, UUID host, String hostName, UUID who, String whoName, long now) {
        SettlementRegistrySC.PlayerRec h = reg.player(host), w = reg.player(who);
        long s = squadOf(h);
        if (s != 0L && s == squadOf(w)) {
            return true;
        }
        if (s != 0L && !room(membersOf(reg, s, null).size())) {
            EntityPlayerMP p = PlayerMsgSC.online(who);
            if (p != null) {
                PlayerMsgSC.tell(p, EnumChatFormatting.RED, "sc.squad.full", String.valueOf(MAX));
            }
            return false;
        }
        if (squadOf(w) != 0L) {
            leave(reg, who, whoName);
        }
        if (s == 0L) {
            do {
                s = now ^ RAND.nextLong();
            } while (s == 0L);
            write(h, s, now, hostName);
        }
        write(w, s, now, whoName);
        reg.markDirty();
        for (EntityPlayerMP m : onlineOf(reg, s)) {
            PlayerMsgSC.tell(m, EnumChatFormatting.AQUA, "sc.squad.joined", whoName);
        }
        return true;
    }

    private static void write(SettlementRegistrySC.PlayerRec r, long s, long now, String name) {
        NBTTagCompound q = new NBTTagCompound();
        q.setLong(ID, s);
        q.setLong(JOINED, now);
        q.setString(NAME, name == null ? "" : name);
        r.extra.setTag(EXTRA, q);
    }

    /** CommandSquadSC «decline <name>». */
    public static void decline(EntityPlayerMP p, String fromName) {
        Invite inv = take(p.getUniqueID(), fromName);
        if (inv == null) {
            PlayerMsgSC.tell(p, EnumChatFormatting.GRAY, "sc.squad.expired");
            return;
        }
        EntityPlayerMP from = PlayerMsgSC.online(inv.from);
        if (from != null) {
            PlayerMsgSC.tell(from, EnumChatFormatting.GRAY, "sc.squad.declined", p.getCommandSenderName());
        }
    }

    /** The invitation to `to` from `fromName` (any case), taken out; null none. */
    private static Invite take(UUID to, String fromName) {
        synchronized (INVITES) {
            Invite i = INVITES.get(to);
            if (i == null || fromName != null && !fromName.isEmpty() && !i.fromName.equalsIgnoreCase(fromName)) {
                return null;
            }
            INVITES.remove(to);
            return i;
        }
    }

    /** CommandSquadSC «leave»: out of his squad (the last one left - it falls apart). @return false - not in one */
    public static boolean leave(SettlementRegistrySC reg, UUID who, String whoName) {
        SettlementRegistrySC.PlayerRec r = reg.playerIfAny(who);
        long s = squadOf(r);
        if (s == 0L) {
            return false;
        }
        List<EntityPlayerMP> told = onlineOf(reg, s);
        r.extra.removeTag(EXTRA);
        List<UUID> rest = membersOf(reg, s, null);
        if (rest.size() == 1) {
            SettlementRegistrySC.PlayerRec last = reg.playerIfAny(rest.get(0));
            if (last != null) {
                last.extra.removeTag(EXTRA);                    // one left alone - no squad any more
            }
        }
        reg.markDirty();
        for (EntityPlayerMP m : told) {
            PlayerMsgSC.tell(m, EnumChatFormatting.GRAY, "sc.squad.left", whoName);
        }
        return true;
    }

    /** CommandSquadSC «list»: the members - name, online or not, the distance in one world. */
    public static void list(EntityPlayerMP p) {
        SettlementRegistrySC reg = SettlementRegistrySC.get(p.worldObj);
        long s = reg == null ? 0L : squadOf(reg.playerIfAny(p.getUniqueID()));
        if (s == 0L) {
            PlayerMsgSC.tell(p, EnumChatFormatting.GRAY, SubstrateConfigSC.squads ? "sc.squad.none" : "sc.squad.off");
            return;
        }
        List<UUID> all = membersOf(reg, s, null);
        PlayerMsgSC.tell(p, EnumChatFormatting.AQUA, "sc.squad.list.head", String.valueOf(all.size()));
        for (UUID u : all) {
            EntityPlayerMP m = PlayerMsgSC.online(u);
            IChatComponent status;
            if (m == null) {
                status = new ChatComponentTranslation("sc.squad.list.offline");
            } else if (m.worldObj == p.worldObj) {
                status = new ChatComponentTranslation("sc.squad.list.far", String.valueOf((int) Math.round(m.getDistanceToEntity(p))));
            } else {
                status = new ChatComponentTranslation("sc.squad.list.far", "?");
            }
            p.addChatComponentMessage(new ChatComponentTranslation("sc.squad.list.line", nameOf(u), status)
                    .setChatStyle(new ChatStyle().setColor(m == null ? EnumChatFormatting.DARK_GRAY : EnumChatFormatting.GRAY)));
        }
    }

    // ================================================================== logging in and out

    /** FML: the name kept for the bridge's list follows a renamed player. */
    @SubscribeEvent
    public void onLoggedIn(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.player instanceof EntityPlayerMP) || e.player instanceof FakePlayer || e.player.worldObj.isRemote) {
            return;
        }
        SettlementRegistrySC reg = SettlementRegistrySC.get(e.player.worldObj);
        SettlementRegistrySC.PlayerRec r = reg == null ? null : reg.playerIfAny(e.player.getUniqueID());
        if (r != null && squadOf(r) != 0L) {
            NBTTagCompound q = r.extra.getCompoundTag(EXTRA);
            if (!e.player.getCommandSenderName().equals(q.getString(NAME))) {
                q.setString(NAME, e.player.getCommandSenderName());
                reg.markDirty();
            }
        }
    }

    /** FML: his invitations and those to him are forgotten. */
    @SubscribeEvent
    public void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.player == null) {
            return;
        }
        UUID id = e.player.getUniqueID();
        synchronized (INVITES) {
            INVITES.remove(id);
            for (Iterator<Invite> it = INVITES.values().iterator(); it.hasNext(); ) {
                if (it.next().from.equals(id)) {
                    it.remove();
                }
            }
        }
    }

    /** Server stopped: the pending invitations (memory only). */
    public static void clearAll() {
        synchronized (INVITES) {
            INVITES.clear();
        }
    }

    // ================================================================== helpers

    @SuppressWarnings("unchecked")
    private static Collection<EntityPlayerMP> online() {
        MinecraftServer srv = MinecraftServer.getServer();
        List<EntityPlayerMP> out = new ArrayList<EntityPlayerMP>();
        if (srv == null || srv.getConfigurationManager() == null) {
            return out;
        }
        for (Object o : srv.getConfigurationManager().playerEntityList) {
            if (o instanceof EntityPlayerMP && !(o instanceof FakePlayer)) {
                out.add((EntityPlayerMP) o);
            }
        }
        return out;
    }

    private static List<EntityPlayerMP> onlineOf(SettlementRegistrySC reg, long s) {
        List<EntityPlayerMP> out = new ArrayList<EntityPlayerMP>();
        for (UUID u : membersOf(reg, s, null)) {
            EntityPlayerMP p = PlayerMsgSC.online(u);
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }
}
