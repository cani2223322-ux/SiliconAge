package com.sc.embassy;

import java.util.List;
import java.util.UUID;

import com.sc.dim.SubstrateConfigSC;
import com.sc.dim.SubstrateSC;
import com.sc.dim.SubstrateSignalSC;
import com.sc.item.ItemArmorSC;
import com.sc.item.ItemLeadSuitSC;
import com.sc.settle.ReputationSC;
import com.sc.settle.SettlementItemsSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.settle.npc.EntityTunerSC;
import com.sc.settle.trade.TradeRulesSC;
import com.sc.settle.trade.TradeServerSC;
import com.sc.util.ConfigSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE R (docs/plan-substrate-tech-f6.md §7.2, §7.6, rules ПЯ-1, ПЯ-4..ПЯ-14, ПЯ-16): a Dispatcher's flights for one player - the
 * screen's rows (what each ride costs him here and why one is closed), the purchase (EmbassyRulesSC.refusal again on the server's
 * data, the soft admission, the chips, the day's counters, the talon, a round trip's return half, the introductory mark, the
 * guarantee) and a talon's refund. FlightServerSC sends and reads the packets, FlightSC runs the windows; the world test comes in
 * through FlightServerSC.state / execute (no packets - a FakePlayer works there, never by a packet).
 * <ul>
 * <li>A flight is not a deal: its chips are just taken (TradeServerSC.takeChips, the main inventory) - no «Доверие», not
 * the day's 64 given out; a refund gives them back (the inventory, else at the feet; the payer offline or dead - owed: OWED in
 * his record, paid at his next look);</li>
 * <li>the day's counters are PlayerRec.globalCounts (a day's period): the passenger's COUNT_RIDES (every ride but the rescue) and
 * COUNT_OTHER, the guarantor's COUNT_GUARANTEE; a refund takes its talon's day's counts back (a ride that never went - the carry
 * failed, the friend refused it - leaves the day's limit as it was; a stale one's day is over anyway);</li>
 * <li>one talon a player (EmbassyTicketSC, R_PENDING); a round trip's talon is marked ROUND (its refund takes the return half back);
 * INTRO's mark is set when it is bought and taken back when its talon is refunded;</li>
 * <li>the guarantee (ПЯ-13): a «Партнёр» pays a friend's ride out (the friend a real player within NEAR_DISPATCHER of the
 * Dispatcher with this Dispatcher's screen open - he is there for a flight -, at least «Чужак» and not this embassy's «Враг»): the
 * friend's own counters, talon and target, the guarantor's level for the ride's level and his price x GUARANTEE_PCT; the guarantee
 * noted for its watch (FlightSC), with the friend's name. The friend may refuse the talon at that Dispatcher (decline: the chips
 * back to the guarantor, the counts and the watch gone).</li>
 * </ul>
 * Server thread only.
 */
public final class FlightRunSC {

    /** Package R's keys in EmbassyTicketSC's compound (appended): chips owed to a payer who was offline at a refund. */
    public static final String OWED = "owed";
    /** In the talon's compound: a round trip's (the refund takes its return half back). In the guarantee's: the friend's name. */
    public static final String ROUND = "o", FRIEND_NAME = "n";
    /** The friends a screen lists at most. */
    public static final int MAX_FRIENDS = 8;

    private FlightRunSC() {
    }

    /** One player as a passenger at the Dispatcher's settlement: his record, the local «Враг», his level there. */
    public static final class Who {
        public final EntityPlayer p;
        public final UUID id;
        public final SettlementRegistrySC.PlayerRec rec;
        public final boolean enemy;
        public final int level;

        Who(SettlementRegistrySC reg, SettlementRegistrySC.Settlement s, EntityPlayer p, long now) {
            this.p = p;
            this.id = p.getUniqueID();
            this.rec = reg.player(id);
            SettlementRegistrySC.LocalRec l = s == null ? null : s.localIfAny(id);
            this.enemy = l != null && l.enemy(now);
            this.level = ReputationSC.localLevel(rec.reputation, enemy);
        }
    }

    /** One player at one Dispatcher now: the registry, the Dispatcher's settlement and place, the day. */
    public static final class Ctx {
        public final EntityPlayer p;
        public final EntityTunerSC t;
        public final SettlementRegistrySC reg;
        /** The Dispatcher's settlement (null: an unbound one, a record gone - it sells nothing). */
        public final SettlementRegistrySC.Settlement s;
        public final int place;
        public final Who me;
        public final long day, now;

        private Ctx(EntityPlayer p, EntityTunerSC t, SettlementRegistrySC reg) {
            this.p = p;
            this.t = t;
            this.reg = reg;
            this.s = t.settlementId() >= 0 ? reg.byId(t.settlementId()) : null;
            this.place = FlightTargetsSC.place(s);
            this.day = SettlementSC.day(p.worldObj);
            this.now = p.worldObj.getTotalWorldTime();
            this.me = new Who(reg, s, p, now);
        }

        /** Null on a client or without a registry. */
        public static Ctx of(EntityPlayer p, EntityTunerSC t) {
            SettlementRegistrySC reg = p == null || t == null || p.worldObj.isRemote ? null : SettlementRegistrySC.get(p.worldObj);
            return reg == null ? null : new Ctx(p, t, reg);
        }

        long seed() {
            return s == null ? 0L : s.seed;
        }
    }

    // ================================================================== the rules on the server's data

    /** ПЯ-1, ПЯ-16: the bridge (bridge.enabled, bridge.crossDimension) and the Substrate are on. */
    public static boolean channel() {
        return ConfigSC.bridgeEnabled && ConfigSC.bridgeCrossDimension && SubstrateSC.active();
    }

    /** ПВ-13: requireSignal is off, or the player has used his Navigation Record. */
    static boolean signal(Who w) {
        return !SubstrateConfigSC.requireSignal || SubstrateSignalSC.knows(w.p);
    }

    static long period(long day) {
        return TradeRulesSC.period(day, 1);
    }

    public static int ridesToday(SettlementRegistrySC.PlayerRec rec, long day) {
        return rec.globalCount(EmbassyRulesSC.COUNT_RIDES, period(day));
    }

    static int otherToday(SettlementRegistrySC.PlayerRec rec, long day) {
        return rec.globalCount(EmbassyRulesSC.COUNT_OTHER, period(day));
    }

    static int guaranteesToday(SettlementRegistrySC.PlayerRec rec, long day) {
        return rec.globalCount(EmbassyRulesSC.COUNT_GUARANTEE, period(day));
    }

    /**
     * The target ride `ride` has for passenger `w` now (`want` - the screen's choice of the docks / embassies; `g` - a guarantor, whose
     * known docks DOCK may go to as well; null none): a settlement id, TARGET_FIRST_PURE, TARGET_MARK, or NO_TARGET.
     */
    static int target(Ctx c, Who w, Who g, int ride, int want) {
        switch (ride) {
            case EmbassyRulesSC.RIDE_INTRO:
            case EmbassyRulesSC.RIDE_FIRST:
                return FlightTargetsSC.firstTarget(c.reg);
            case EmbassyRulesSC.RIDE_DOCK:
                for (SettlementRegistrySC.Settlement s : FlightTargetsSC.knownDocks(c.reg, w.rec, g == null ? null : g.rec)) {
                    if (s.id == want) {
                        return want;
                    }
                }
                return FlightTargetsSC.NO_TARGET;
            case EmbassyRulesSC.RIDE_RESCUE:
                return FlightTargetsSC.rescueOpen(w.rec, c.now) ? EmbassyRulesSC.TARGET_MARK : FlightTargetsSC.NO_TARGET;
            case EmbassyRulesSC.RIDE_HOME:
                return FlightTargetsSC.homeTarget(c.reg, w.rec);
            case EmbassyRulesSC.RIDE_BACK:
                return FlightTargetsSC.backTarget(c.reg, w.rec, c.now);
            case EmbassyRulesSC.RIDE_OTHER:
                for (SettlementRegistrySC.Settlement s : FlightTargetsSC.otherEmbassies(c.reg, w.rec)) {
                    if (s.id == want) {
                        return want;
                    }
                }
                return FlightTargetsSC.NO_TARGET;
            default:
                return FlightTargetsSC.NO_TARGET;
        }
    }

    /**
     * Why passenger `w` can't have ride `ride` to `target` here at `level` (the ride's level - his own, or a guarantor's), or OK -
     * EmbassyRulesSC.refusal on the server's data; INTRO / FIRST with no «Первый причал» anywhere: R_CHANNEL.
     */
    static int code(Ctx c, Who w, int ride, int target, int level) {
        boolean first = ride == EmbassyRulesSC.RIDE_INTRO || ride == EmbassyRulesSC.RIDE_FIRST;
        boolean has = target != FlightTargetsSC.NO_TARGET;
        int code = EmbassyRulesSC.refusal(ride, channel(), level, ridesToday(w.rec, c.day), otherToday(w.rec, c.day),
                EmbassyTicketSC.introUsed(w.rec), has || first, signal(w), FlightTargetsSC.sells(c.s, ride), EmbassyTicketSC.talon(w.rec) != null);
        return code == EmbassyRulesSC.OK && first && !has ? EmbassyRulesSC.R_CHANNEL : code;
    }

    /** What ride `ride` costs at `level` here (one way or the round trip; BACK - paid with its ticket: 0). */
    static int price(Ctx c, int ride, boolean round, int level) {
        if (ride == EmbassyRulesSC.RIDE_BACK) {
            return 0;
        }
        return round ? EmbassyRulesSC.roundTripPrice(ride, level, c.seed()) : EmbassyRulesSC.price(ride, level, c.seed());
    }

    /** ПЯ-14: {a piece of this mod's armour worn, a Return Capsule carried}. */
    public static boolean[] kit(EntityPlayer p) {
        boolean armour = false, capsule = false;
        for (ItemStack s : p.inventory.armorInventory) {
            if (s != null && (s.getItem() instanceof ItemArmorSC || s.getItem() instanceof ItemLeadSuitSC)) {
                armour = true;
            }
        }
        for (ItemStack s : p.inventory.mainInventory) {
            if (s != null && s.stackSize > 0 && SettlementItemsSC.returnCapsule != null && s.getItem() == SettlementItemsSC.returnCapsule) {
                capsule = true;
            }
        }
        return new boolean[]{armour, capsule};
    }

    // ================================================================== the screen's state

    /** FLIGHT_STATE for the player of `c` (the reading side - GuiTradeSC's flights tab). */
    public static NBTTagCompound state(Ctx c) {
        NBTTagCompound n = new NBTTagCompound();
        n.setInteger("tuner", c.t.getEntityId());
        n.setByte("place", (byte) c.place);
        n.setInteger("sid", c.s == null ? -1 : c.s.id);
        n.setByte("level", (byte) c.me.level);
        n.setInteger("left", Math.max(0, EmbassyRulesSC.FLIGHTS_PER_DAY - ridesToday(c.me.rec, c.day)));
        n.setInteger("max", EmbassyRulesSC.FLIGHTS_PER_DAY);
        boolean guarantor = c.place == FlightTargetsSC.PLACE_EMBASSY && c.me.level >= EmbassyRulesSC.GUARANTOR_LEVEL;
        n.setBoolean("guarantor", guarantor);
        n.setInteger("guarLeft", Math.max(0, EmbassyRulesSC.GUARANTEE_PER_DAY - guaranteesToday(c.me.rec, c.day)));
        long seed = c.p.worldObj.getSeed();
        NBTTagList rides = new NBTTagList();
        if (c.s != null) {
            for (Integer r : FlightTargetsSC.rides(c.s)) {
                int ride = r.intValue();
                for (int target : targets(c, ride)) {
                    rides.appendTag(row(c, ride, target, seed));
                }
            }
        }
        n.setTag("rides", rides);
        int back = FlightTargetsSC.backTarget(c.reg, c.me.rec, c.now);
        if (back >= 0) {
            NBTTagCompound b = new NBTTagCompound();
            int[] nm = FlightTargetsSC.name(c.reg, EmbassyRulesSC.RIDE_BACK, back, seed);
            b.setInteger("e", back);
            b.setInteger("n", nm[0]);
            b.setInteger("d", (int) Math.max(1L, (EmbassyTicketSC.backUntil(c.me.rec) - c.now + SettlementSC.DAY - 1) / SettlementSC.DAY));
            n.setTag("back", b);
        }
        EmbassyTicketSC.Talon k = EmbassyTicketSC.talon(c.me.rec);
        if (k != null) {
            NBTTagCompound t = new NBTTagCompound();
            int[] nm = FlightTargetsSC.name(c.reg, k.ride, k.target, seed);
            t.setInteger("r", k.ride);
            t.setInteger("t", k.target);
            t.setInteger("n", nm[0]);
            t.setBoolean("e", nm[1] != 0);
            t.setBoolean("here", c.s != null && k.settlement == c.s.id);
            t.setBoolean("g", k.payer.length() > 0 && !k.payer.equals(c.me.id.toString()));     // a guarantor's: it may be refused
            SettlementRegistrySC.Settlement at = c.reg.byId(k.settlement);
            t.setInteger("sn", at == null ? 0 : com.sc.settle.npc.NpcNamesSC.code(at.nameElement, at.nameNumber));
            t.setBoolean("se", SettlementSC.isEmbassy(at));
            n.setTag("talon", t);
        }
        int id = c.s == null ? -1 : c.s.id;
        n.setByte("phase", (byte) FlightSC.phase(id));
        n.setInteger("ticks", FlightSC.ticksLeft(id));
        n.setInteger("pass", FlightSC.passengers(id));
        n.setTag("friends", c.place == FlightTargetsSC.PLACE_EMBASSY ? friends(c) : new NBTTagList());
        return n;
    }

    /** The targets of one ride's rows (a closed ride keeps one row: NO_TARGET - the screen says why). */
    private static int[] targets(Ctx c, int ride) {
        List<SettlementRegistrySC.Settlement> l = null;
        if (ride == EmbassyRulesSC.RIDE_DOCK) {
            l = FlightTargetsSC.knownDocks(c.reg, c.me.rec, null);
        } else if (ride == EmbassyRulesSC.RIDE_OTHER) {
            l = FlightTargetsSC.otherEmbassies(c.reg, c.me.rec);
        }
        if (l == null) {
            return new int[]{target(c, c.me, null, ride, FlightTargetsSC.NO_TARGET)};
        }
        if (l.isEmpty()) {
            return new int[]{FlightTargetsSC.NO_TARGET};
        }
        int[] out = new int[l.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = l.get(i).id;
        }
        return out;
    }

    /** One row: {r: ride, t: target, n: name code, e: an embassy's, x, z, p: price, o: round trip's (0 none), g: guarantee's (0 none), c: code}. */
    private static NBTTagCompound row(Ctx c, int ride, int target, long seed) {
        NBTTagCompound e = new NBTTagCompound();
        int[] nm = FlightTargetsSC.name(c.reg, ride, target, seed);
        e.setByte("r", (byte) ride);
        e.setInteger("t", target);
        e.setInteger("n", nm[0]);
        e.setBoolean("e", nm[1] != 0);
        int[] xz = coords(c, target, seed);
        if (xz != null) {
            e.setInteger("x", xz[0]);
            e.setInteger("z", xz[1]);
            e.setBoolean("xz", true);
        }
        int level = c.me.level;
        e.setInteger("p", price(c, ride, false, level));
        e.setInteger("o", EmbassyRulesSC.roundTrip(ride) ? price(c, ride, true, level) : 0);
        e.setInteger("g", EmbassyRulesSC.outbound(ride) && level >= EmbassyRulesSC.GUARANTOR_LEVEL
                ? EmbassyRulesSC.guaranteePrice(ride, level, c.seed()) : 0);
        e.setByte("c", (byte) code(c, c.me, ride, target, level));
        return e;
    }

    /** The target's coordinates for the screen: a dock's beacon, the death mark, the pure plan's beacon; null none. */
    private static int[] coords(Ctx c, int target, long seed) {
        if (target == EmbassyRulesSC.TARGET_MARK) {
            return new int[]{c.me.rec.deathX, c.me.rec.deathZ};
        }
        if (target == EmbassyRulesSC.TARGET_FIRST_PURE) {
            com.sc.settle.gen.SettlementLayoutSC.Plan p = FlightTargetsSC.firstPure(seed);
            return p == null ? null : new int[]{p.dockX, p.dockZ};
        }
        SettlementRegistrySC.Settlement s = target >= 0 ? c.reg.byId(target) : null;
        return s == null ? null : new int[]{s.dockX, s.dockZ};
    }

    /**
     * ПЯ-13: the real players within NEAR_DISPATCHER of the Dispatcher but the viewer, with this Dispatcher's screen open (FlightServerSC
     * .atScreen: he came for a flight - nobody's day is booked from afar) - {n: name, u: UUID, l: level here}.
     */
    private static NBTTagList friends(Ctx c) {
        NBTTagList l = new NBTTagList();
        for (Object o : c.t.worldObj.playerEntities) {
            if (!(o instanceof EntityPlayerMP) || o instanceof FakePlayer || o == c.p || l.tagCount() >= MAX_FRIENDS) {
                continue;
            }
            EntityPlayerMP f = (EntityPlayerMP) o;
            if (!f.isEntityAlive() || f.getDistanceToEntity(c.t) > EmbassyRulesSC.NEAR_DISPATCHER || !FlightServerSC.atScreen(f, c.t)) {
                continue;
            }
            Who w = new Who(c.reg, c.s, f, c.now);
            NBTTagCompound e = new NBTTagCompound();
            e.setString("n", f.getCommandSenderName());
            e.setString("u", w.id.toString());
            e.setByte("l", (byte) w.level);
            l.appendTag(e);
        }
        return l;
    }

    // ================================================================== the purchase

    /**
     * FLIGHT_DO: buys ride `ride` (to `want` - the docks' and the embassies' rows; the others' targets are the server's own) for the
     * player of `c`, or - `friend` not null - for that friend (the guarantee); `round` - the round trip; `confirm` - the soft
     * admission's second click. @return {code, chips (the price - charged when OK), no armour 0/1, no capsule 0/1}
     */
    public static int[] buy(Ctx c, int ride, int want, boolean round, EntityPlayer friend, boolean confirm) {
        if (c.s == null || !EmbassyRulesSC.valid(ride) || !FlightTargetsSC.sells(c.s, ride)) {
            return res(EmbassyRulesSC.R_NOT_HERE, 0);
        }
        Who w = c.me;
        boolean guarantee = friend != null;
        if (guarantee) {
            w = new Who(c.reg, c.s, friend, c.now);
            if (!EmbassyRulesSC.outbound(ride) || round || w.id.equals(c.me.id)) {
                return res(EmbassyRulesSC.R_FRIEND, 0);
            }
            if (c.me.level < EmbassyRulesSC.GUARANTOR_LEVEL) {
                return res(EmbassyRulesSC.R_LEVEL, 0);
            }
            if (guaranteesToday(c.me.rec, c.day) >= EmbassyRulesSC.GUARANTEE_PER_DAY) {
                return res(EmbassyRulesSC.R_LIMIT, 0);
            }
            if (w.enemy || !EmbassyRulesSC.guaranteeOpen(c.me.level, w.level)) {
                return res(EmbassyRulesSC.R_FRIEND, 0);
            }
        }
        if (round && !EmbassyRulesSC.roundTrip(ride)) {
            return res(EmbassyRulesSC.R_NOT_HERE, 0);
        }
        int target = target(c, w, guarantee ? c.me : null, ride, want);
        int level = guarantee ? c.me.level : w.level;           // a guarantee: the ride's level is the guarantor's
        int code = code(c, w, ride, target, level);
        if (code != EmbassyRulesSC.OK) {
            return res(code, 0);
        }
        if (!FlightSC.joinable(c.s.id, w.id)) {
            return res(EmbassyRulesSC.R_BUSY, 0);                // the ring cools, or the window is full: nothing taken
        }
        int price = guarantee ? EmbassyRulesSC.guaranteePrice(ride, c.me.level, c.seed()) : price(c, ride, round, level);
        boolean[] kit = kit(w.p);
        if (EmbassyRulesSC.outbound(ride) && EmbassyRulesSC.softWarning(kit[0], kit[1])
                && !(confirm && FlightSC.confirmed(c.me.id, ride, target, w.id, c.now))) {
            FlightSC.warn(c.me.id, ride, target, w.id, c.now);   // ПЯ-14: «Там стреляют, Странник» - the same button again within 5 s
            return new int[]{EmbassyRulesSC.R_CONFIRM, price, kit[0] ? 0 : 1, kit[1] ? 0 : 1};
        }
        if (chips(c.p) < price) {
            return res(EmbassyRulesSC.R_CHIPS, price);
        }
        takeChips(c.p, price);
        long period = period(c.day);
        if (EmbassyRulesSC.counted(ride)) {
            w.rec.addGlobalCount(EmbassyRulesSC.COUNT_RIDES, period, 1);
        }
        if (ride == EmbassyRulesSC.RIDE_OTHER) {
            w.rec.addGlobalCount(EmbassyRulesSC.COUNT_OTHER, period, 1);
        }
        if (guarantee) {
            c.me.rec.addGlobalCount(EmbassyRulesSC.COUNT_GUARANTEE, period, 1);
            EmbassyTicketSC.setGuarantee(c.me.rec, w.id.toString(), c.s.id, c.now);
            NBTTagCompound g = EmbassyTicketSC.guarantee(c.me.rec);
            if (g != null) {
                g.setString(FRIEND_NAME, w.p.getCommandSenderName());
            }
        }
        EmbassyTicketSC.setTalon(w.rec, new EmbassyTicketSC.Talon(ride, target, c.s.id, price, c.day, guarantee ? c.me.id.toString() : ""));
        if (round) {
            EmbassyTicketSC.of(w.rec).getCompoundTag(EmbassyTicketSC.TALON).setBoolean(ROUND, true);
            EmbassyTicketSC.setBack(w.rec, c.s.id, c.now + EmbassyRulesSC.BACK_TICKS);
        }
        if (ride == EmbassyRulesSC.RIDE_INTRO) {
            EmbassyTicketSC.useIntro(w.rec);
        }
        FlightSC.unwarn(c.me.id);
        c.reg.markDirty();
        c.p.inventory.markDirty();
        FlightSC.board(w.p, c.s);                               // joins the window, or starts it (joinable said yes)
        if (guarantee) {
            tell(w.p, EnumChatFormatting.DARK_AQUA, "sc.flight.guarantee.given", c.p.getCommandSenderName());
        }
        return res(EmbassyRulesSC.OK, price);
    }

    /** «Лететь»: the player's own talon of this settlement starts its window or joins it. @return {code, 0} */
    public static int[] go(Ctx c) {
        EmbassyTicketSC.Talon k = EmbassyTicketSC.talon(c.me.rec);
        if (k == null || c.s == null || k.settlement != c.s.id) {
            return res(EmbassyRulesSC.R_NOT_HERE, 0);
        }
        if (k.stale(c.day)) {
            refund(c.reg, c.me.id, c.me.rec, k, c.p, null);     // an earlier day's: back to its payer (the check every TALON_CHECK was late)
            return res(EmbassyRulesSC.R_NOT_HERE, 0);
        }
        if (!channel()) {
            return res(EmbassyRulesSC.R_OFF, 0);
        }
        return res(FlightSC.board(c.p, c.s), 0);
    }

    private static int[] res(int code, int chips) {
        return new int[]{code, chips, 0, 0};
    }

    // ================================================================== the refund

    /**
     * Talon `k` of passenger `id` (record `rec`) goes back: its chips to its payer (online and alive - now, else owed till his next
     * look: a payer on his death screen would get them into the inventory the death has just spilt), the talon gone, its day's counts
     * taken back (the passenger's rides, a guarantor's guarantee and its watch - a ride never flown), a refunded INTRO open again, a
     * round trip's return half gone. `p` - the passenger online (told `why`), or null.
     */
    public static void refund(SettlementRegistrySC reg, UUID id, SettlementRegistrySC.PlayerRec rec, EmbassyTicketSC.Talon k, EntityPlayer p,
                              String why) {
        if (reg == null || rec == null || k == null) {
            return;
        }
        boolean round = EmbassyTicketSC.of(rec).getCompoundTag(EmbassyTicketSC.TALON).getBoolean(ROUND);
        EmbassyTicketSC.setTalon(rec, null);
        if (k.ride == EmbassyRulesSC.RIDE_INTRO) {
            EmbassyTicketSC.of(rec).removeTag(EmbassyTicketSC.INTRO);    // never flown: the introductory flight is open again
        }
        if (round && EmbassyTicketSC.backEmbassy(rec) == k.settlement) {
            EmbassyTicketSC.clearBack(rec);
        }
        long period = period(k.day);
        if (EmbassyRulesSC.counted(k.ride)) {
            uncount(rec, EmbassyRulesSC.COUNT_RIDES, period);
        }
        if (k.ride == EmbassyRulesSC.RIDE_OTHER) {
            uncount(rec, EmbassyRulesSC.COUNT_OTHER, period);
        }
        UUID payer = k.payer.length() > 0 ? SettlementRegistrySC.uuid(k.payer) : id;
        if (payer == null) {
            payer = id;
        }
        if (!payer.equals(id)) {                                // a guarantee: his day's one back, no watch over a ride not flown
            SettlementRegistrySC.PlayerRec pr = reg.player(payer);
            uncount(pr, EmbassyRulesSC.COUNT_GUARANTEE, period);
            NBTTagCompound g = EmbassyTicketSC.guarantee(pr);
            if (g != null && id.toString().equals(g.getString("f")) && g.getInteger("s") == k.settlement && !EmbassyTicketSC.guaranteePaid(pr)) {
                EmbassyTicketSC.of(pr).removeTag(EmbassyTicketSC.GUARANTEE);
            }
        }
        if (k.chips > 0) {
            EntityPlayer to = payer.equals(id) && p != null ? p : online(payer);
            if (to != null && to.isEntityAlive()) {
                giveChips(to, k.chips);
                tell(to, EnumChatFormatting.DARK_AQUA, "sc.flight.refund", String.valueOf(k.chips));
            } else {
                NBTTagCompound o = EmbassyTicketSC.of(reg.player(payer));
                o.setInteger(OWED, o.getInteger(OWED) + k.chips);
            }
        }
        reg.markDirty();
        if (p != null && why != null) {
            tell(p, EnumChatFormatting.GOLD, why);
        }
    }

    /** One of a day's counts back (never under 0; another day's stored count is left alone). */
    private static void uncount(SettlementRegistrySC.PlayerRec rec, String key, long period) {
        if (rec != null && rec.globalCount(key, period) > 0) {
            rec.addGlobalCount(key, period, -1);
        }
    }

    /**
     * ПЯ-13: the passenger of `c` refuses the talon a guarantor paid for him, at the Dispatcher that sold it: it goes back to the
     * guarantor (refund - his chips, his day's guarantee; the passenger's day as it was), the guarantor told. @return {code, chips}
     */
    public static int[] decline(Ctx c) {
        EmbassyTicketSC.Talon k = EmbassyTicketSC.talon(c.me.rec);
        if (k == null || c.s == null || k.settlement != c.s.id || k.payer.length() == 0 || k.payer.equals(c.me.id.toString())) {
            return res(EmbassyRulesSC.R_NOT_HERE, 0);
        }
        FlightSC.unbook(c.me.id);
        refund(c.reg, c.me.id, c.me.rec, k, c.p, null);
        EntityPlayerMP payer = online(SettlementRegistrySC.uuid(k.payer));
        if (payer != null) {
            tell(payer, EnumChatFormatting.GOLD, "sc.flight.guarantee.declined", c.p.getCommandSenderName());
        }
        c.p.inventory.markDirty();
        return res(EmbassyRulesSC.OK, k.chips);
    }

    /** The chips owed to `p` from a refund while he was away: into his inventory. @return whether there were any */
    public static boolean payOwed(SettlementRegistrySC reg, SettlementRegistrySC.PlayerRec rec, EntityPlayer p) {
        NBTTagCompound c = rec == null ? null : rec.extra.getCompoundTag(EmbassyTicketSC.EXTRA);
        int owed = c == null ? 0 : c.getInteger(OWED);
        if (owed <= 0) {
            return false;
        }
        c.removeTag(OWED);
        giveChips(p, owed);
        tell(p, EnumChatFormatting.DARK_AQUA, "sc.flight.refund", String.valueOf(owed));
        reg.markDirty();
        return true;
    }

    // ================================================================== chips, players, chat

    /** Credit Chips in `p`'s main inventory (TradeServerSC's helper: a flight's price is not a deal). */
    public static int chips(EntityPlayer p) {
        return TradeServerSC.chipsHeld(p);
    }

    /** Takes `n` chips (chips() said there are as many). */
    static void takeChips(EntityPlayer p, int n) {
        TradeServerSC.takeChips(p, n);
    }

    /** `n` chips into `p`'s inventory; what doesn't fit lies at his feet (no pickup delay); an open screen sees it at once. */
    public static void giveChips(EntityPlayer p, int n) {
        TradeServerSC.giveChips(p, n);
        if (p != null && n > 0 && p.openContainer != null) {
            p.openContainer.detectAndSendChanges();
        }
    }

    /** The online real player with this UUID, or null. */
    public static EntityPlayerMP online(UUID id) {
        MinecraftServer srv = MinecraftServer.getServer();
        if (srv == null || id == null || srv.getConfigurationManager() == null) {
            return null;
        }
        for (Object o : srv.getConfigurationManager().playerEntityList) {
            if (o instanceof EntityPlayerMP && !(o instanceof FakePlayer) && id.equals(((EntityPlayerMP) o).getUniqueID())) {
                return (EntityPlayerMP) o;
            }
        }
        return null;
    }

    /** A chat line to a real player (nothing for a FakePlayer). */
    static void tell(EntityPlayer p, EnumChatFormatting colour, String key, Object... args) {
        if (p == null || p instanceof FakePlayer || p.worldObj == null || p.worldObj.isRemote) {
            return;
        }
        ChatComponentTranslation m = new ChatComponentTranslation(key, args);
        m.setChatStyle(new ChatStyle().setColor(colour));
        p.addChatComponentMessage(m);
    }
}
