package com.sc.settle.trade;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.sc.SCMod;
import com.sc.emp.EmpModule;
import com.sc.handler.GuiHandlerSC;
import com.sc.init.ModItems;
import com.sc.inventory.ContainerTradeSC;
import com.sc.settle.NpcKindSC;
import com.sc.settle.ReputationSC;
import com.sc.settle.SettleNetSC;
import com.sc.settle.SettlementItemsSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.settle.npc.EntityTunerSC;
import com.sc.settle.npc.NpcNamesSC;
import com.sc.settle.travel.ItemSettlementMapSC;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE T (docs/plan-substrate-tech-f2.md §9.1, §9.7, rules ПО-1..ПО-9): the server side of the «Терминал обмена».
 * open: a Tuner's right-click (package N calls it) - refusals first (the local «Враг»: closed, ПД-1), then
 * openGui(TRADE_GUI_ID, x = the Tuner's entity id) and the state (SettleNetSC.TRADE_STATE: the deals as this player
 * sees them - the counts after the level and the settlement's spread, what is left today, the restock timer, the
 * level; the Elder's board and the player's contracts). onMessage: SettleNetSC's TRADE_DO / CONTRACT_* from that
 * screen - only while that player's open container is a ContainerTradeSC of a live Tuner within 8 blocks; the inputs
 * are taken from the player's inventory, matched strictly (TradeOfferSC.Ingredient), the limits checked and counted
 * (SettlementRegistrySC), the output given (inventory, else dropped at the feet), «Доверие» +1 (ReputationSC.give,
 * SRC_TRADE). Several players at one Tuner at once: every container is the player's own. Never a packet to a
 * FakePlayer. The signatures are fixed; the bodies package T's.
 * <p>
 * The limits (ПО-4, ПО-5): an ordinary deal - its LocalRec.counts at this settlement today; a «глоб.» one -
 * PlayerRec.globalCounts over its period (the penance a day, the Crumb a week - with PlayerRec.crumbs too); the chips
 * a player gets from Tuners - deals, contracts, the anchor - at most TradeRulesSC.CHIPS_PER_DAY a day (PlayerRec.chips:
 * a deal that would go past it is refused with the reason, never cut short); contracts taken - CONTRACTS_PER_DAY.
 * An unbound Tuner (a spawn egg's, settlement -1) trades at the table's prices without a spread, its counters kept
 * apart in the player's record (extra "T": "uDay", "uCounts") - one shared «settlement −1».
 * <p>
 * A «Доверие» deal (the penance, a Stolen Cargo) gives its own amount (SRC_PENANCE, SRC_CARGO) and not the +1 of a
 * trade. Contracts (ПО-8) need «Знакомый» to take; DELIVER and CARGO are handed in a part at a time (the inputs only
 * when the reward fits under the chips' cap); KILL / SUPPRESS_KILL count by themselves (EconomyEventsSC).
 * <p>
 * Ф3 (docs/plan-substrate-tech-f3.md §6.8, rule ПО-13): the services - the Power Engineer's charge, the Armourer's
 * restoring - go the same way (the level, the limit, the price, «Доверие» +1), but their first side is the player's own
 * thing worked on in its slot (TradeServicesSC: the hand first, else the first that fits; none - R_NO_ITEM): the price is
 * taken, then the piece charged or the trophy replaced. Deals sharing a counter (TradeOfferSC.counter: the three charges)
 * count and limit together; an unlimited one (the restoring) isn't counted at all.
 * <p>
 * Ф4 (package C, docs/plan-substrate-tech-f4.md §9.4, §9.5, rules ПО-16, ПТ-17): a settlement that lost a raid trades nothing
 * for a day (RaidSC.tradeClosed - R_RAID, every deal; contracts stay open); the Elder's Crumb counts towards the player's three a
 * day from the world (CrumbLimitSC - R_CRUMBS when today's are taken; counted when the deal is made).
 */
public final class TradeServerSC {

    /** execute's results and TRADE_RESULT's "code": OK, or why not (lang sc.trade.result.<RESULT_KEYS[code]>). */
    public static final int OK = 0, R_UNKNOWN = 1, R_ENEMY = 2, R_LEVEL = 3, R_LIMIT = 4, R_CHIPS_CAP = 5, R_MISSING = 6,
            R_NO_SETTLEMENT = 7, R_NOT_HERE = 8, R_CONTRACTS_CAP = 9, R_TAKEN = 10, R_NOT_ON_BOARD = 11, R_NOT_DONE = 12,
            R_NO_CONTRACT = 13, R_ANCHOR = 14, R_PARTIAL = 15, R_NO_ITEM = 16, R_RAID = 17, R_CRUMBS = 18;
    /**
     * Appended only (Ф3: "noItem" - a service found nothing of the player's to work on; Ф4: "raid" - the settlement's trade closed
     * after a lost raid, "crumbs" - the player's Crumbs for today are taken).
     */
    public static final String[] RESULT_KEYS = {"ok", "unknown", "enemy", "level", "limit", "chipsCap", "missing", "noSettlement",
            "notHere", "contractsCap", "taken", "notOnBoard", "notDone", "noContract", "anchor", "partial", "noItem", "raid", "crumbs"};
    /** TRADE_STATE "anchor": nobody placed the first anchor yet / it is the viewer's, unpaid / paid to the viewer / someone else's. */
    public static final int ANCHOR_OPEN = 0, ANCHOR_YOURS = 1, ANCHOR_PAID = 2, ANCHOR_OTHER = 3;
    /** TRADE_STATE board "st": on the board, not taken / taken and open / handed in today. */
    public static final int C_FREE = 0, C_OPEN = 1, C_DONE = 2;
    /** Finished contracts kept this many days (the board shows today's handed-in ones). */
    private static final int KEEP_DONE_DAYS = 1;
    /** The player's record's own key (PlayerRec.extra): the unbound Tuners' counters. */
    private static final String EXTRA = "T";

    private TradeServerSC() {
    }

    /** A real player right-clicked `t` (not sneaking): its window, or the refusal in the chat. */
    public static void open(EntityPlayerMP p, EntityTunerSC t) {
        if (p == null || t == null || p instanceof FakePlayer || p.worldObj.isRemote || !t.isEntityAlive()) {
            return;
        }
        Ctx c = Ctx.of(p, t);
        if (c == null) {
            return;
        }
        if (c.enemy) {
            SettlementRegistrySC.LocalRec l = c.s.localIfAny(c.id);
            long minutes = l == null ? 0L : Math.max(1L, (l.enemyUntil - c.now + 1199L) / 1200L);
            ChatComponentTranslation m = new ChatComponentTranslation("sc.trade.refuse.enemy", SettlementSC.displayName(c.s), String.valueOf(minutes));
            m.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED));
            p.addChatComponentMessage(m);
            return;
        }
        p.openGui(SCMod.instance, GuiHandlerSC.TRADE_GUI_ID, p.worldObj, t.getEntityId(), 0, 0);
        if (p.openContainer instanceof ContainerTradeSC && ((ContainerTradeSC) p.openContainer).getTuner() == t) {
            SettleNetSC.send(p, SettleNetSC.TRADE_STATE, state(c));
        }
    }

    /** SettleNetSC.ToServer from `p` (the server thread). */
    public static void onMessage(EntityPlayerMP p, int kind, NBTTagCompound data) {
        if (p == null || p instanceof FakePlayer || data == null) {
            return;
        }
        EntityTunerSC t = openTuner(p);
        if (t == null) {
            return;                                     // the screen is closed, the Tuner gone or too far: nothing (a stale click)
        }
        Ctx c = Ctx.of(p, t);
        if (c == null) {
            return;
        }
        String key = data.getString("key");
        int[] r;
        switch (kind) {
            case SettleNetSC.TRADE_DO:
                r = run(c, key, Math.max(1, Math.min(TradeRulesSC.MAX_TIMES, data.getInteger("times"))));
                break;
            case SettleNetSC.CONTRACT_TAKE:
                r = new int[]{take(c, key), 0};
                break;
            case SettleNetSC.CONTRACT_TURN_IN:
                r = turnIn(c, key);
                break;
            case SettleNetSC.CONTRACT_DROP:
                r = new int[]{drop(c, key), 0};
                break;
            case SettleNetSC.TRADE_REFRESH:
                r = null;
                break;
            default:
                return;
        }
        if (r != null) {
            NBTTagCompound res = new NBTTagCompound();
            res.setInteger("kind", kind);
            res.setString("key", key);
            res.setInteger("code", r[0]);
            res.setInteger("done", r[1]);
            res.setInteger("tuner", t.getEntityId());
            SettleNetSC.send(p, SettleNetSC.TRADE_RESULT, res);
            p.openContainer.detectAndSendChanges();     // the inventory as it is now, before the new numbers
        }
        SettleNetSC.send(p, SettleNetSC.TRADE_STATE, state(Ctx.of(p, t)));
    }

    /**
     * For the world test (no window, no packets - a FakePlayer works): up to `times` deals of `key` with `t` - the services
     * too (the charge, the restoring: on the FakePlayer's held / first fitting thing).
     * @return OK if at least one was made, else why not (R_*)
     */
    public static int execute(EntityPlayer p, EntityTunerSC t, String key, int times) {
        Ctx c = p == null || t == null ? null : Ctx.of(p, t);
        if (c == null) {
            return R_NOT_HERE;
        }
        int[] r = run(c, key, Math.max(1, times));
        return r[1] > 0 ? OK : r[0];
    }

    /** For the world test: the Elder's contract actions without a window (CONTRACT_TAKE / CONTRACT_TURN_IN / CONTRACT_DROP). */
    public static int contract(EntityPlayer p, EntityTunerSC t, int kind, String key) {
        Ctx c = p == null || t == null ? null : Ctx.of(p, t);
        if (c == null) {
            return R_NOT_HERE;
        }
        if (kind == SettleNetSC.CONTRACT_TAKE) {
            return take(c, key);
        }
        if (kind == SettleNetSC.CONTRACT_DROP) {
            return drop(c, key);
        }
        return turnIn(c, key)[0];
    }

    /**
     * For the world test and the handbook's checks: how many times `p` made deal `key` at `t` in its window now (today
     * at this settlement - or at the unbound Tuners; a «глоб.» deal - its period everywhere); -1 for an unknown deal.
     */
    public static int usedNow(EntityPlayer p, EntityTunerSC t, String key) {
        Ctx c = p == null || t == null ? null : Ctx.of(p, t);
        TradeOfferSC o = TradeTableSC.byKey(key);
        return c == null || o == null ? -1 : used(c, o);
    }

    /** Server stopped: nothing is kept between sessions here (the screens die with their players). */
    public static void clearAll() {
    }

    // ================================================================== the deal

    /** The player's open trade screen's Tuner, if it is still a valid one (alive, same world, within REACH), else null. */
    private static EntityTunerSC openTuner(EntityPlayerMP p) {
        if (!(p.openContainer instanceof ContainerTradeSC)) {
            return null;
        }
        ContainerTradeSC box = (ContainerTradeSC) p.openContainer;
        EntityTunerSC t = box.getTuner();
        return box.getPlayer() == p && t != null && box.canInteractWith(p) ? t : null;
    }

    /** Up to `times` deals of `key`, stopping at the first refusal. @return {the refusal that stopped it (OK: all made), deals made} */
    private static int[] run(Ctx c, String key, int times) {
        TradeOfferSC o = TradeTableSC.byKey(key);
        int done = 0;
        int why = OK;
        while (done < times) {
            why = refusal(c, o);
            if (why == OK && o.service() && TradeServicesSC.slot(c.p, o) < 0) {
                why = R_NO_ITEM;                            // nothing (left) of the player's to charge or restore
            }
            if (why == OK && !affordable(c, o)) {
                why = R_MISSING;
            }
            if (why != OK) {
                break;
            }
            perform(c, o);
            done++;
        }
        if (done > 0) {
            c.p.inventory.markDirty();
            c.reg.markDirty();
        }
        return new int[]{done >= times ? OK : why, done};
    }

    /** Why `o` can't be made by this player here now - not counting what the inventory holds - or OK. */
    static int refusal(Ctx c, TradeOfferSC o) {
        if (o == null || o.kind != c.t.kind()) {
            return R_UNKNOWN;
        }
        if (c.enemy) {
            return R_ENEMY;
        }
        if (com.sc.hazard.RaidSC.tradeClosed(c.p.worldObj, c.s)) {
            return R_RAID;                                      // Ф4 (ПО-16): a lost raid closed this settlement's trade for a day
                                                                // («closed» is kept in getWorldTime(), not in c.now)
        }
        if (!TradeRulesSC.available(o, c.level)) {
            return R_LEVEL;
        }
        if (TradeRulesSC.remaining(o, used(c, o)) <= 0) {
            return R_LIMIT;
        }
        if (TradeTableSC.ELDER_CRUMB.equals(o.key) && com.sc.settle.CrumbLimitSC.left(c.p) <= 0) {
            return R_CRUMBS;                                    // Ф4 (ПТ-17): today's three Crumbs are taken
        }
        int out = o.sell() ? TradeRulesSC.outCount(o, c.level, c.pct(o)) : 0;
        if (o.sell() && TradeRulesSC.chipsAllowed(c.rec.chips, out) < out) {
            return R_CHIPS_CAP;
        }
        if (o.outKind == TradeOfferSC.OUT_MAP && c.s == null) {
            return R_NO_SETTLEMENT;
        }
        return OK;
    }

    /** The player's inventory holds every side of one deal of `o` at its price here (a service: its price - the thing is slot's). */
    private static boolean affordable(Ctx c, TradeOfferSC o) {
        int[] n = TradeRulesSC.playerCounts(o, c.level, c.pct(o));
        for (int k = o.service() ? 1 : 0; k < o.in.length; k++) {
            if (o.in[k].countIn(c.p.inventory.mainInventory) < n[k]) {
                return false;
            }
        }
        return true;
    }

    /**
     * One deal (refusal and affordable said yes, a service's slot found): the inputs out, the output in (a service: the
     * player's thing worked on in its slot), the counters and «Доверие».
     */
    private static void perform(Ctx c, TradeOfferSC o) {
        int[] n = TradeRulesSC.playerCounts(o, c.level, c.pct(o));
        int slot = o.service() ? TradeServicesSC.slot(c.p, o) : -1;     // before the payment: the hand's thing, else the first that fits
        for (int k = o.service() ? 1 : 0; k < o.in.length; k++) {
            o.in[k].takeFrom(c.p.inventory.mainInventory, n[k], o.in[k].any() ? c.p.inventory.currentItem : -1);
        }
        if (o.service()) {
            TradeServicesSC.apply(c.p, o, slot);
        } else if (o.outKind == TradeOfferSC.OUT_ITEM) {
            ItemStack out = o.out.display();
            if (TradeTableSC.ELDER_CRUMB.equals(o.key)) {
                com.sc.settle.CrumbLimitSC.take(c.p, 1);        // Ф4 (ПТ-17): counted as one of today's (refusal saw one left)
            }
            if (o.sell()) {
                out.stackSize = TradeRulesSC.outCount(o, c.level, c.pct(o));    // a trophy's sale: by the level (ПО-6)
                c.rec.chips += out.stackSize;
            }
            give(c.p, out);
        } else if (o.outKind == TradeOfferSC.OUT_MAP) {
            give(c.p, ItemSettlementMapSC.make(c.reg, c.s));
        } else {
            boolean penance = TradeTableSC.ELDER_PENANCE.equals(o.key);
            ReputationSC.give(c.rec, o.outAmount, penance ? ReputationSC.SRC_PENANCE : ReputationSC.SRC_CARGO, c.day);
            if (penance) {
                c.rec.penanceDay = c.day;
            }
        }
        count(c, o);
        if (o.outKind != TradeOfferSC.OUT_REPUTATION) {
            ReputationSC.give(c.rec, ReputationSC.TRADE, ReputationSC.SRC_TRADE, c.day);
        }
        c.refreshLevel();
    }

    /**
     * Times `o` was made by this player in its window (today here; a global one - its period everywhere) - by its counter
     * (the deals sharing one together); an unlimited deal: 0 (never counted).
     */
    static int used(Ctx c, TradeOfferSC o) {
        if (o.unlimited()) {
            return 0;
        }
        if (o.global) {
            int n = c.rec.globalCount(o.counter, TradeRulesSC.period(c.day, o.periodDays));
            if (TradeTableSC.ELDER_CRUMB.equals(o.key)) {
                c.rec.rollWeek(SettlementSC.week(c.p.worldObj));
                n = Math.max(n, c.rec.crumbs);
            }
            return n;
        }
        if (c.s != null) {
            SettlementRegistrySC.LocalRec l = c.s.localIfAny(c.id);
            return l == null || l.day != c.day ? 0 : l.count(o.counter);
        }
        NBTTagCompound u = c.rec.extra.getCompoundTag(EXTRA);
        return u.getLong("uDay") != c.day ? 0 : u.getCompoundTag("uCounts").getInteger(o.counter);
    }

    private static void count(Ctx c, TradeOfferSC o) {
        if (o.unlimited()) {
            return;
        }
        if (o.global) {
            c.rec.addGlobalCount(o.counter, TradeRulesSC.period(c.day, o.periodDays), 1);
            if (TradeTableSC.ELDER_CRUMB.equals(o.key)) {
                c.rec.rollWeek(SettlementSC.week(c.p.worldObj));
                c.rec.crumbs++;
            }
        } else if (c.s != null) {
            SettlementRegistrySC.LocalRec l = c.s.local(c.id);
            l.rollDay(c.day);
            l.addCount(o.counter, 1);
        } else {
            NBTTagCompound u = c.rec.extra.getCompoundTag(EXTRA);
            NBTTagCompound counts = u.getLong("uDay") == c.day ? u.getCompoundTag("uCounts") : new NBTTagCompound();
            counts.setInteger(o.counter, counts.getInteger(o.counter) + 1);
            u.setLong("uDay", c.day);
            u.setTag("uCounts", counts);
            c.rec.extra.setTag(EXTRA, u);
        }
    }

    /** Into the inventory; what doesn't fit lies at the player's feet (no pickup delay). */
    static void give(EntityPlayer p, ItemStack s) {
        if (s == null || s.stackSize <= 0) {
            return;
        }
        if (p.inventory.addItemStackToInventory(s) && s.stackSize <= 0) {
            return;
        }
        if (s.stackSize > 0) {
            EntityItem e = new EntityItem(p.worldObj, p.posX, p.posY + 0.5D, p.posZ, s.copy());
            e.delayBeforeCanPickup = 0;
            p.worldObj.spawnEntityInWorld(e);
            s.stackSize = 0;
        }
    }

    // ================================================================== contracts (the Elder)

    private static int take(Ctx c, String key) {
        int why = contractGate(c);
        if (why != OK) {
            return why;
        }
        ContractTableSC.Contract k = ContractTableSC.byKey(key);
        if (k == null || k.goal == ContractTableSC.ANCHOR) {
            return R_UNKNOWN;
        }
        if (c.level < ReputationSC.ACQUAINTANCE) {
            return R_LEVEL;
        }
        if (!ContractTableSC.board(c.id, c.day).contains(key)) {
            return R_NOT_ON_BOARD;
        }
        for (SettlementRegistrySC.ContractRec r : c.rec.contracts) {
            if (r.key.equals(key) && (!r.done || r.takenDay == c.day)) {
                return R_TAKEN;
            }
        }
        if (c.rec.contractsTaken >= TradeRulesSC.CONTRACTS_PER_DAY) {
            return R_CONTRACTS_CAP;
        }
        SettlementRegistrySC.ContractRec r = new SettlementRegistrySC.ContractRec();
        r.key = key;
        r.target = k.target;
        r.settlement = c.s == null ? -1 : c.s.id;
        r.takenDay = c.day;
        c.rec.contracts.add(r);
        c.rec.contractsTaken++;
        c.reg.markDirty();
        return OK;
    }

    /** @return {result, things handed in} */
    private static int[] turnIn(Ctx c, String key) {
        int why = contractGate(c);
        if (why != OK) {
            return new int[]{why, 0};
        }
        if (ContractTableSC.ANCHOR_KEY.equals(key)) {
            return new int[]{anchor(c), 0};
        }
        SettlementRegistrySC.ContractRec r = openContract(c, key);
        ContractTableSC.Contract k = r == null ? null : ContractTableSC.byKey(r.key);
        if (k == null) {
            return new int[]{R_NO_CONTRACT, 0};
        }
        int handed = 0;
        TradeOfferSC.Ingredient in = k.handIn();
        if (in != null) {
            int need = Math.max(0, r.target - r.progress);
            handed = Math.min(need, in.countIn(c.p.inventory.mainInventory));
            if (handed <= 0 && need > 0) {
                return new int[]{R_MISSING, 0};
            }
            if (r.progress + handed < r.target) {               // a part: taken, counted, no reward yet
                in.takeFrom(c.p.inventory.mainInventory, handed);
                r.progress += handed;
                c.p.inventory.markDirty();
                c.reg.markDirty();
                return new int[]{R_PARTIAL, handed};
            }
        } else if (r.progress < r.target) {
            return new int[]{R_NOT_DONE, 0};
        }
        if (TradeRulesSC.chipsAllowed(c.rec.chips, k.chips) < k.chips) {
            return new int[]{R_CHIPS_CAP, 0};                   // nothing taken: come back after dawn
        }
        if (in != null && handed > 0) {
            in.takeFrom(c.p.inventory.mainInventory, handed);
            r.progress += handed;
        }
        r.done = true;
        reward(c, k);
        c.rec.pruneContracts(c.day, KEEP_DONE_DAYS);
        c.p.inventory.markDirty();
        c.reg.markDirty();
        return new int[]{OK, handed};
    }

    /** The one-time anchor contract (§7): the world's first anchor in the Substrate was the viewer's and isn't paid yet. */
    private static int anchor(Ctx c) {
        String by = c.reg.anchorBy == null ? "" : c.reg.anchorBy;
        if (by.isEmpty()) {
            return R_NOT_DONE;
        }
        if (c.reg.anchorPaid || !by.equals(c.id.toString())) {
            return R_ANCHOR;
        }
        ContractTableSC.Contract k = ContractTableSC.ANCHOR_CONTRACT;
        if (TradeRulesSC.chipsAllowed(c.rec.chips, k.chips) < k.chips) {
            return R_CHIPS_CAP;
        }
        c.reg.anchorPaid = true;
        give(c.p, SettlementItemsSC.chips(k.chips));
        c.rec.chips += k.chips;
        ReputationSC.give(c.rec, k.reputation, ReputationSC.SRC_ANCHOR, c.day);
        c.p.inventory.markDirty();
        c.reg.markDirty();
        return OK;
    }

    private static int drop(Ctx c, String key) {
        int why = contractGate(c);
        if (why != OK) {
            return why;
        }
        for (Iterator<SettlementRegistrySC.ContractRec> it = c.rec.contracts.iterator(); it.hasNext(); ) {
            SettlementRegistrySC.ContractRec r = it.next();
            if (!r.done && r.key.equals(key)) {
                it.remove();
                c.reg.markDirty();
                return OK;
            }
        }
        return R_NO_CONTRACT;
    }

    /** Contracts are the Elder's, and not for a local «Враг». */
    private static int contractGate(Ctx c) {
        if (c.t.kind() != NpcKindSC.ELDER) {
            return R_UNKNOWN;
        }
        return c.enemy ? R_ENEMY : OK;
    }

    private static SettlementRegistrySC.ContractRec openContract(Ctx c, String key) {
        for (SettlementRegistrySC.ContractRec r : c.rec.contracts) {
            if (!r.done && r.key.equals(key)) {
                return r;
            }
        }
        return null;
    }

    /** A finished contract's pay: the chips (counted into the day's), «Доверие», maybe a ПЭ-1 module. */
    private static void reward(Ctx c, ContractTableSC.Contract k) {
        if (k.chips > 0) {
            give(c.p, SettlementItemsSC.chips(k.chips));
            c.rec.chips += k.chips;
        }
        ReputationSC.give(c.rec, k.reputation, ReputationSC.SRC_CONTRACT, c.day);
        if (k.modulePct > 0 && ModItems.empModule != null && c.p.worldObj.rand.nextInt(100) < k.modulePct) {
            EmpModule[] all = EmpModule.values();
            give(c.p, ModItems.empModule.stackOf(all[c.p.worldObj.rand.nextInt(all.length)]));
            c.p.addChatComponentMessage(new ChatComponentTranslation("sc.contract.module")
                    .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.AQUA)));
        }
        c.refreshLevel();
    }

    // ================================================================== the screen's state

    /** TRADE_STATE for the player of `c` (see TradeClientSC for the reading side). */
    private static NBTTagCompound state(Ctx c) {
        NBTTagCompound n = new NBTTagCompound();
        if (c == null) {
            return n;
        }
        n.setInteger("tuner", c.t.getEntityId());
        n.setByte("kind", (byte) c.t.kind().ordinal());
        n.setInteger("sid", c.s == null ? -1 : c.s.id);
        n.setInteger("sname", c.s == null ? 0 : NpcNamesSC.code(c.s.nameElement, c.s.nameNumber));
        n.setInteger("rep", c.rec.reputation);
        n.setByte("level", (byte) c.level);
        n.setBoolean("enemy", c.enemy);
        n.setInteger("chips", c.rec.chips);
        NBTTagList offers = new NBTTagList();
        for (TradeOfferSC o : TradeTableSC.offers(c.t.kind())) {
            NBTTagCompound e = new NBTTagCompound();
            e.setString("k", o.key);
            e.setIntArray("n", TradeRulesSC.playerCounts(o, c.level, c.pct(o)));
            e.setInteger("o", TradeRulesSC.outCount(o, c.level, c.pct(o)));
            e.setInteger("left", TradeRulesSC.remaining(o, used(c, o)));
            e.setByte("why", (byte) refusal(c, o));
            offers.appendTag(e);
        }
        n.setTag("offers", offers);
        if (c.t.kind() == NpcKindSC.ELDER) {
            NBTTagList board = new NBTTagList();
            List<String> keys = ContractTableSC.board(c.id, c.day);
            for (String key : keys) {
                NBTTagCompound e = new NBTTagCompound();
                e.setString("k", key);
                int st = C_FREE, progress = 0;
                for (SettlementRegistrySC.ContractRec r : c.rec.contracts) {
                    if (r.key.equals(key) && !r.done) {
                        st = C_OPEN;
                        progress = r.progress;
                    } else if (r.key.equals(key) && r.takenDay == c.day && st == C_FREE) {
                        st = C_DONE;
                        progress = r.target;
                    }
                }
                e.setByte("st", (byte) st);
                e.setInteger("p", progress);
                board.appendTag(e);
            }
            n.setTag("board", board);
            NBTTagList open = new NBTTagList();
            for (SettlementRegistrySC.ContractRec r : c.rec.contracts) {
                if (!r.done && !keys.contains(r.key)) {
                    NBTTagCompound e = new NBTTagCompound();
                    e.setString("k", r.key);
                    e.setByte("st", (byte) C_OPEN);
                    e.setInteger("p", r.progress);
                    open.appendTag(e);
                }
            }
            n.setTag("open", open);
            n.setInteger("taken", c.rec.contractsTaken);
            EconomyEventsSC.anchorTaken(c.p.worldObj, c.reg, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);   // a 0.2.1 world's anchor
            String by = c.reg.anchorBy == null ? "" : c.reg.anchorBy;
            int anchor = by.isEmpty() ? ANCHOR_OPEN : !by.equals(c.id.toString()) ? ANCHOR_OTHER : c.reg.anchorPaid ? ANCHOR_PAID : ANCHOR_YOURS;
            n.setByte("anchor", (byte) anchor);
        }
        return n;
    }

    // ================================================================== who, where, when

    /** One player at one Tuner now: the registry's records, the day, the level here. */
    static final class Ctx {
        final EntityPlayer p;
        final EntityTunerSC t;
        final UUID id;
        final SettlementRegistrySC reg;
        /** The Tuner's settlement, null for an unbound one (or a record gone). */
        final SettlementRegistrySC.Settlement s;
        final SettlementRegistrySC.PlayerRec rec;
        final long day, now;
        final boolean enemy;
        int level;

        private Ctx(EntityPlayer p, EntityTunerSC t, SettlementRegistrySC reg) {
            this.p = p;
            this.t = t;
            this.id = p.getUniqueID();
            this.reg = reg;
            this.s = t.settlementId() >= 0 ? reg.byId(t.settlementId()) : null;
            this.rec = reg.player(id);
            this.day = SettlementSC.day(p.worldObj);
            this.now = p.worldObj.getTotalWorldTime();
            if (rec.rollDay(day)) {
                reg.markDirty();
            }
            SettlementRegistrySC.LocalRec l = s == null ? null : s.localIfAny(id);
            this.enemy = l != null && l.enemy(now);
            refreshLevel();
        }

        /** Null on a client or without a registry. */
        static Ctx of(EntityPlayer p, EntityTunerSC t) {
            SettlementRegistrySC reg = p.worldObj.isRemote ? null : SettlementRegistrySC.get(p.worldObj);
            return reg == null ? null : new Ctx(p, t, reg);
        }

        /** After «Доверие» changed (a deal made may lift the player to the next level mid-run). */
        void refreshLevel() {
            level = ReputationSC.localLevel(rec.reputation, enemy);
        }

        /** The settlement's spread for `o` (0 for an unbound Tuner). */
        int pct(TradeOfferSC o) {
            return s == null ? 0 : TradeRulesSC.seedPct(s.seed, o.key);
        }
    }
}
