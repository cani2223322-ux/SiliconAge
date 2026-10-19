package com.sc.embassy;

import java.util.UUID;

import com.sc.inventory.ContainerTradeSC;
import com.sc.settle.NpcKindSC;
import com.sc.settle.SettleNetSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.npc.EntityTunerSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE R (docs/plan-substrate-tech-f6.md §7.2, rules ПЯ-1..ПЯ-14): the server side of a Dispatcher's «Рейсы» tab (GuiTradeSC, GUI
 * 21 - the trade screen of a DISPATCHER Tuner). sendState: TradeServerSC.open calls it after the trade state (the coordinator's hook),
 * FlightServerSC sends SettleNetSC.FLIGHT_STATE again after every FLIGHT_DO / FLIGHT_REFRESH; onMessage: SettleNetSC's FLIGHT_DO
 * {ride, target, round, friend, confirm} / FLIGHT_REFRESH {} from that screen - only while the sender's open container is a
 * ContainerTradeSC of a live Dispatcher within 8 blocks (as TradeServerSC's), never a FakePlayer.
 * <ul>
 * <li>FLIGHT_STATE {tuner, place: 0 embassy / 1 Substrate dock, rides: [{ride, target, name, x, z, price, round, code}], left: flights
 * today, back: {embassy name, days left}, talon: {ride, target}, phase, ticks, friends: [{name, uuid, level}]} - every ride this
 * Dispatcher sells, with EmbassyRulesSC.refusal's code for this player (the screen greys the refused ones and says why);</li>
 * <li>FLIGHT_DO: the ride checked again (refusal; the chips counted from the inventory - TradeServerSC's chips helper), the soft
 * admission (EmbassyRulesSC.softWarning: warn, a second FLIGHT_DO with confirm within CONFIRM_TICKS), the chips taken (they count
 * towards nothing - a flight is not a deal: no «Доверие», not the day's 64 given out), the day's counters added (COUNT_RIDES,
 * COUNT_OTHER, COUNT_GUARANTEE), the talon written (EmbassyTicketSC), a round trip's return half (setBack), the window joined or
 * started (FlightSC.board); FLIGHT_RESULT {code, ride, chips} back.</li>
 * </ul>
 * Fixed (the coordinator's): the class, the two entries' signatures. Bodies R's.
 * <p>
 * R's details: the keys exactly - FLIGHT_STATE as FlightRunSC.state writes it ({tuner, place, sid, level, left, max, guarantor,
 * guarLeft, rides: [{r, t, n: name code, e: an embassy's, xz / x / z, p: price, o: round trip's, g: guarantee's, c: code}], back {e,
 * n, d}, talon {r, t, n, e, here, sn, se, g: a guarantor paid it}, phase, ticks, pass, friends [{n, u, l}]}); FLIGHT_DO {ride, target,
 * round, friend: the friend's UUID or "", confirm, go: «Лететь» by the player's own talon, decline: «Отказаться» from a talon a
 * guarantor paid}; FLIGHT_RESULT {tuner, kind: FLIGHT_DO, code, ride, chips, go, decline, round, friend: his name, noArmour,
 * noCapsule, place}. The chips are FlightRunSC's own helper (the main inventory, as the deals'). A friend is one with this very
 * Dispatcher's screen open (atScreen): a guarantee is never put on a player who only stands near. The world test comes in through
 * state / execute (no packets, no screen - a FakePlayer works there).
 */
public final class FlightServerSC {

    private FlightServerSC() {
    }

    /** SettleNetSC.ToServer FLIGHT_DO / FLIGHT_REFRESH from `p` (the server thread). */
    public static void onMessage(EntityPlayerMP p, int kind, NBTTagCompound data) {
        if (p == null || p instanceof FakePlayer || data == null || p.worldObj == null || p.worldObj.isRemote) {
            return;
        }
        EntityTunerSC t = openDispatcher(p);
        if (t == null) {
            return;                                     // the screen is closed, the Dispatcher gone or too far: nothing (a stale click)
        }
        if (kind == SettleNetSC.FLIGHT_DO) {
            FlightRunSC.Ctx c = FlightRunSC.Ctx.of(p, t);
            if (c == null) {
                return;
            }
            String fid = data.getString("friend");
            EntityPlayerMP friend = fid.isEmpty() ? null : friendNear(p, t, SettlementRegistrySC.uuid(fid));
            int ride = data.getInteger("ride");
            boolean go = data.getBoolean("go"), round = data.getBoolean("round"), decline = data.getBoolean("decline");
            EmbassyTicketSC.Talon had = decline ? EmbassyTicketSC.talon(c.me.rec) : null;
            int[] r;
            if (go) {
                r = FlightRunSC.go(c);
            } else if (decline) {
                r = FlightRunSC.decline(c);
            } else if (!fid.isEmpty() && friend == null) {
                r = new int[]{EmbassyRulesSC.R_FRIEND, 0, 0, 0};    // he walked off, logged out
            } else {
                r = FlightRunSC.buy(c, ride, data.getInteger("target"), round, friend, data.getBoolean("confirm"));
            }
            NBTTagCompound res = new NBTTagCompound();
            res.setInteger("tuner", t.getEntityId());
            res.setInteger("kind", kind);
            res.setInteger("code", r[0]);
            res.setInteger("ride", go || decline ? -1 : ride);
            res.setInteger("chips", r[1]);
            res.setBoolean("go", go);
            res.setBoolean("decline", decline);
            res.setBoolean("round", round);
            res.setString("friend", friend == null ? "" : friend.getCommandSenderName());
            res.setBoolean("noArmour", r[2] != 0);
            res.setBoolean("noCapsule", r[3] != 0);
            res.setByte("place", (byte) c.place);
            SettleNetSC.send(p, SettleNetSC.FLIGHT_RESULT, res);
            p.openContainer.detectAndSendChanges();     // the inventory as it is now (the chips gone), before the new numbers
            if (friend != null && r[0] == EmbassyRulesSC.OK) {
                EntityTunerSC ft = openDispatcher(friend);
                if (ft != null) {
                    sendState(friend, ft);              // his own screen shows his talon
                }
            }
            EntityPlayerMP payer = had != null && r[0] == EmbassyRulesSC.OK ? FlightRunSC.online(SettlementRegistrySC.uuid(had.payer)) : null;
            EntityTunerSC pt = payer == null ? null : openDispatcher(payer);
            if (pt != null) {
                payer.openContainer.detectAndSendChanges();
                sendState(payer, pt);                   // the guarantor's screen: his chips and his day's guarantee back
            }
        } else if (kind != SettleNetSC.FLIGHT_REFRESH) {
            return;
        }
        sendState(p, t);
    }

    /** The «Рейсы» tab's state for `p` at Dispatcher `t` (SettleNetSC.FLIGHT_STATE). */
    public static void sendState(EntityPlayerMP p, EntityTunerSC t) {
        if (p == null || p instanceof FakePlayer || t == null || t.kind() != NpcKindSC.DISPATCHER) {
            return;
        }
        NBTTagCompound n = state(p, t);
        if (n != null) {
            SettleNetSC.send(p, SettleNetSC.FLIGHT_STATE, n);
        }
    }

    /** FLIGHT_STATE's compound for `p` at Dispatcher `t` without sending it (the world test; null on a client). */
    public static NBTTagCompound state(EntityPlayer p, EntityTunerSC t) {
        FlightRunSC.Ctx c = p == null || t == null ? null : FlightRunSC.Ctx.of(p, t);
        return c == null ? null : FlightRunSC.state(c);
    }

    /**
     * For the world test (no window screen, no packets - a FakePlayer works): FLIGHT_DO `d` ({ride, target, round, confirm, go}) of
     * `p` at Dispatcher `t`, for `friend` (the guarantee; null - himself). @return {code, chips, no armour, no capsule}
     */
    public static int[] execute(EntityPlayer p, EntityTunerSC t, NBTTagCompound d, EntityPlayer friend) {
        FlightRunSC.Ctx c = p == null || t == null || d == null ? null : FlightRunSC.Ctx.of(p, t);
        if (c == null) {
            return new int[]{EmbassyRulesSC.R_NOT_HERE, 0, 0, 0};
        }
        if (d.getBoolean("go")) {
            return FlightRunSC.go(c);
        }
        if (d.getBoolean("decline")) {
            return FlightRunSC.decline(c);
        }
        return FlightRunSC.buy(c, d.getInteger("ride"), d.getInteger("target"), d.getBoolean("round"), friend, d.getBoolean("confirm"));
    }

    /** The player's open trade screen's Dispatcher, if it is still a valid one (alive, same world, within REACH), else null. */
    private static EntityTunerSC openDispatcher(EntityPlayerMP p) {
        if (!(p.openContainer instanceof ContainerTradeSC)) {
            return null;
        }
        ContainerTradeSC box = (ContainerTradeSC) p.openContainer;
        EntityTunerSC t = box.getTuner();
        return box.getPlayer() == p && t != null && t.kind() == NpcKindSC.DISPATCHER && box.canInteractWith(p) ? t : null;
    }

    /** ПЯ-13: `f` has Dispatcher `t`'s screen open - he is there for a flight (a guarantee is put only on such a friend). */
    static boolean atScreen(EntityPlayerMP f, EntityTunerSC t) {
        return f != null && t != null && openDispatcher(f) == t;
    }

    /** ПЯ-13: the real player `id` (not `p`) within NEAR_DISPATCHER of Dispatcher `t`, alive, at its screen (atScreen), or null. */
    private static EntityPlayerMP friendNear(EntityPlayerMP p, EntityTunerSC t, UUID id) {
        if (id == null || id.equals(p.getUniqueID())) {
            return null;
        }
        for (Object o : t.worldObj.playerEntities) {
            if (o instanceof EntityPlayerMP && !(o instanceof FakePlayer) && id.equals(((EntityPlayerMP) o).getUniqueID())) {
                EntityPlayerMP f = (EntityPlayerMP) o;
                return f.isEntityAlive() && f.getDistanceToEntity(t) <= EmbassyRulesSC.NEAR_DISPATCHER && atScreen(f, t) ? f : null;
            }
        }
        return null;
    }
}
