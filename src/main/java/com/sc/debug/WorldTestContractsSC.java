package com.sc.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.sc.dim.SubstrateSC;
import com.sc.settle.NpcKindSC;
import com.sc.settle.ReputationSC;
import com.sc.settle.SettleNetSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.settle.SettlementSizeSC;
import com.sc.settle.npc.EntityTunerSC;
import com.sc.settle.trade.ContractLocalSC;
import com.sc.settle.trade.ContractTableSC;
import com.sc.settle.trade.TradeRulesSC;
import com.sc.settle.trade.TradeServerSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

/**
 * ПД-1 (ИГ-2 А of the audit 2026-11-04; docs/plan-ideas-2026-10-26.md, the Substrate's region k = 1: X 21024, Z 20000), -Dsc.worldtest=true:
 * the Elder's board on the server, no window, no packet (TradeServerSC.contract - a StationPlayer: a real EntityPlayerMP, never spawned).
 * Two Substrate settlement records of its own (never generated: no chunk is loaded) with an Elder's slot each, two Tuners bound to them
 * (never spawned). «Срочная депеша»: taken before noon (after it - R_URGENT_TIME, then the record as a take makes it), handed in at the
 * wrong Elder - R_ELSEWHERE, at its destination - OK and paid (after sunset - R_DISPATCH_LATE, lost); an order of the board: a part -
 * R_PARTIAL, the rest over the day's chips - R_CHIPS_CAP (nothing taken), then OK and paid; the day's limit of contracts taken -
 * R_CONTRACTS_CAP. SKIP while the Substrate is off. Its records are taken out again (as RELAY - the only kind SettlementRegistrySC.remove
 * takes), the player's contracts and things cleared. One line each, "[SC-WORLDTEST] PASS / FAIL / SKIP / INFO contracts: ...".
 */
public final class WorldTestContractsSC {

    static final int START = 1200;
    private static final int X = 20000 + 1024, Z = 20000, Y = 120;
    private int ticks;
    private boolean done;

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || done) {
            return;
        }
        if (++ticks < START) {
            return;
        }
        done = true;
        try {
            run();
        } catch (Throwable t) {
            print(false, "exception " + t);
            t.printStackTrace();
        }
    }

    private void run() {
        if (!SubstrateSC.active()) {
            System.out.println("[SC-WORLDTEST] SKIP contracts: the dimension is off (" + SubstrateSC.offReason() + ")");
            return;
        }
        WorldServer w = MinecraftServer.getServer().worldServerForDimension(SubstrateSC.dimId());
        SettlementRegistrySC reg = w == null ? null : SettlementRegistrySC.get(w);
        if (w == null || reg == null) {
            print(false, "the Substrate's world or the settlements' registry not there");
            return;
        }
        SettlementRegistrySC.Settlement a = record(reg, w, X, Z, 901), b = record(reg, w, X + 400, Z, 902);
        WorldTestArmourerSC.StationPlayer p = null;
        try {
            EntityTunerSC ta = elder(w, a), tb = elder(w, b);
            long day = SettlementSC.day(w), now = w.getWorldTime();
            SettlementRegistrySC.Settlement dest = TradeServerSC.urgentDest(reg, a);
            System.out.println("[SC-WORLDTEST] INFO contracts: the records #" + a.id + " at " + a.x + " " + a.z + " and #" + b.id + " at " + b.x + " "
                    + b.z + " (the Substrate), day " + day + ", the time of day " + (now % 24000L) + ", «Срочная депеша» from #" + a.id + " to "
                    + (dest == null ? "none" : "#" + dest.id + " at " + dest.x + " " + dest.z));
            // a player whose board at A has the dispatch today (the board is the player's and the day's)
            int tags = ContractLocalSC.tags(w, a);
            String name = null;
            for (int i = 0; i < 120 && name == null; i++) {
                if (ContractTableSC.substrateBoard(profile("board-" + i).getId(), day, tags, dest != null, true).contains(ContractTableSC.URGENT_DISPATCH)) {
                    name = "board-" + i;
                }
            }
            if (dest == null || name == null) {
                print(false, "«Срочная депеша»: no destination from #" + a.id + " (" + (dest == null) + ") or no board of 120 players with it today");
                return;
            }
            p = new WorldTestArmourerSC.StationPlayer(w, profile(name));
            p.setLocationAndAngles(a.x + 0.5D, Y, a.z + 0.5D, 0F, 0F);
            SettlementRegistrySC.PlayerRec pr = fresh(reg, p, day);
            // 1. «Срочная депеша»: taken (before noon), the wrong Elder, its own
            EntityTunerSC there = dest == b ? tb : elder(w, dest);
            int take = TradeServerSC.contract(p, ta, SettleNetSC.CONTRACT_TAKE, ContractTableSC.URGENT_DISPATCH);
            boolean morning = ContractTableSC.urgentTakeable(now), night = ContractTableSC.urgentLate(ContractTableSC.urgentBy(day), now);
            if (!morning && take == TradeServerSC.R_URGENT_TIME) {
                SettlementRegistrySC.ContractRec r = new SettlementRegistrySC.ContractRec();      // as take() makes it, the time aside
                r.key = ContractTableSC.URGENT_DISPATCH;
                r.target = 1;
                r.settlement = a.id;
                r.takenDay = day;
                r.extra.setInteger(ContractTableSC.X_TO, dest.id);
                r.extra.setLong(ContractTableSC.X_BY, ContractTableSC.urgentBy(day));
                pr.contracts.add(r);
                pr.contractsTaken++;
            }
            int chipsBefore = TradeServerSC.chipsHeld(p), repBefore = pr.reputation;
            int wrong = TradeServerSC.contract(p, ta, SettleNetSC.CONTRACT_TURN_IN, ContractTableSC.URGENT_DISPATCH);
            int right = night ? TradeServerSC.R_DISPATCH_LATE
                    : TradeServerSC.contract(p, there, SettleNetSC.CONTRACT_TURN_IN, ContractTableSC.URGENT_DISPATCH);
            ContractTableSC.Contract uk = ContractTableSC.byKey(ContractTableSC.URGENT_DISPATCH);
            int paid = TradeServerSC.chipsHeld(p) - chipsBefore, rep = pr.reputation - repBefore;
            boolean urgentOk;
            String when;
            if (morning) {
                when = "before noon";
                urgentOk = take == TradeServerSC.OK && wrong == TradeServerSC.R_ELSEWHERE && right == TradeServerSC.OK && paid == uk.chips && rep > 0;
            } else if (!night) {
                when = "after noon";
                urgentOk = take == TradeServerSC.R_URGENT_TIME && wrong == TradeServerSC.R_ELSEWHERE && right == TradeServerSC.OK && paid == uk.chips && rep > 0;
            } else {
                when = "after sunset";
                urgentOk = take == TradeServerSC.R_URGENT_TIME && wrong == TradeServerSC.R_DISPATCH_LATE && open(pr, ContractTableSC.URGENT_DISPATCH) == null;
            }
            print(urgentOk, "ПД-1 «Срочная депеша» (" + when + ", time of day " + (now % 24000L) + "): taken at #" + a.id + " " + code(take) + ", handed in at #"
                    + a.id + " " + code(wrong) + " (elsewhere" + (night ? " / late" : "") + "), at its destination #" + dest.id + " " + code(right) + "; chips +" + paid
                    + " (" + uk.chips + "), «Доверие» +" + rep);
            // 2. the board's order: a part, over the day's chips, the rest
            List<String> board = ContractTableSC.substrateBoard(p.getUniqueID(), day, tags, true, true);
            String orderKey = null;
            for (String k : board) {
                ContractTableSC.Contract c = ContractTableSC.byKey(k);
                if (c != null && c.group == ContractTableSC.G_ORDER) {
                    orderKey = k;
                }
            }
            ContractTableSC.Contract ok = orderKey == null ? null : ContractTableSC.byKey(orderKey);
            if (ok == null || ok.item == null) {
                print(false, "ПД-1 the order: none on the board " + board);
            } else {
                pr.contractsTaken = 0;
                pr.chips = 0;
                int t = TradeServerSC.contract(p, ta, SettleNetSC.CONTRACT_TAKE, orderKey);
                int part = TradeServerSC.R_PARTIAL;
                if (ok.target > 1) {
                    give(p, ok.item, ok.target - 1);
                    part = TradeServerSC.contract(p, ta, SettleNetSC.CONTRACT_TURN_IN, orderKey);
                }
                SettlementRegistrySC.ContractRec or = open(pr, orderKey);
                int progress = or == null ? -1 : or.progress;
                give(p, ok.item, 1);
                pr.chips = TradeRulesSC.CHIPS_PER_DAY - ok.chips + 1;
                int capped = TradeServerSC.contract(p, ta, SettleNetSC.CONTRACT_TURN_IN, orderKey);
                int held = count(p, ok.item);
                pr.chips = 0;
                int c0 = TradeServerSC.chipsHeld(p), r0 = pr.reputation;
                int last = TradeServerSC.contract(p, ta, SettleNetSC.CONTRACT_TURN_IN, orderKey);
                int got = TradeServerSC.chipsHeld(p) - c0, gotRep = pr.reputation - r0, left = count(p, ok.item);
                boolean orderOk = t == TradeServerSC.OK && part == TradeServerSC.R_PARTIAL && (ok.target <= 1 || progress == ok.target - 1)
                        && capped == TradeServerSC.R_CHIPS_CAP && held == 1 && last == TradeServerSC.OK && got == ok.chips && gotRep > 0 && left == 0
                        && open(pr, orderKey) == null;
                print(orderOk, "ПД-1 the order " + orderKey + " (" + ok.target + " x " + ok.item.getDisplayName() + ") at #" + a.id + ": taken " + code(t)
                        + ", " + (ok.target - 1) + " of them - " + code(part) + " (progress " + progress + "), the rest past the day's " + TradeRulesSC.CHIPS_PER_DAY
                        + " chips - " + code(capped) + " (kept " + held + " - 1), then " + code(last) + ": chips +" + got + " (" + ok.chips + "), «Доверие» +"
                        + gotRep + ", left " + left + " (0)");
            }
            // 3. the day's limit of contracts taken
            pr.contracts.clear();
            pr.contractsTaken = 0;
            List<String> codes = new ArrayList<String>();
            int capAt = -1;
            for (String k : board) {
                ContractTableSC.Contract c = ContractTableSC.byKey(k);
                if (c == null || c.goal == ContractTableSC.URGENT || c.goal == ContractTableSC.BIG_ORDER) {
                    continue;                                       // the time's / the week's own gates aside
                }
                int r = TradeServerSC.contract(p, ta, SettleNetSC.CONTRACT_TAKE, k);
                codes.add(k + ":" + code(r));
                if (r == TradeServerSC.R_CONTRACTS_CAP && capAt < 0) {
                    capAt = codes.size() - 1;
                }
            }
            boolean limit = capAt == TradeRulesSC.CONTRACTS_PER_DAY && pr.contractsTaken == TradeRulesSC.CONTRACTS_PER_DAY;
            print(limit, "ПД-1 the day's limit: " + codes + " - the " + (TradeRulesSC.CONTRACTS_PER_DAY + 1) + "th refused with contractsCap ("
                    + TradeRulesSC.CONTRACTS_PER_DAY + " a day), taken " + pr.contractsTaken);
        } finally {
            if (p != null) {
                SettlementRegistrySC.PlayerRec pr = reg.playerIfAny(p.getUniqueID());
                if (pr != null) {
                    pr.contracts.clear();
                    pr.contractsTaken = 0;
                    pr.chips = 0;
                }
                p.inventory.clearInventory(null, -1);
            }
            drop(reg, a);
            drop(reg, b);
            reg.markDirty();
        }
    }

    // ------------------------------------------------------------------ helpers

    /** A Substrate Settlement's record of its own at (x, z): an Elder's slot, never a core (no chunk). */
    private static SettlementRegistrySC.Settlement record(SettlementRegistrySC reg, WorldServer w, int x, int z, int number) {
        SettlementRegistrySC.Settlement s = new SettlementRegistrySC.Settlement();
        s.dim = w.provider.dimensionId;
        s.x = x;
        s.y = Y;
        s.z = z;
        s.floorY = Y;
        s.size = SettlementSizeSC.SETTLEMENT.ordinal();
        s.radius = 40;
        s.seed = 0x5C0E7A1E00000000L ^ number;
        s.nameElement = 0;
        s.nameNumber = number;
        s.madeAt = w.getTotalWorldTime();
        SettlementRegistrySC.Slot elder = new SettlementRegistrySC.Slot();
        elder.kind = NpcKindSC.ELDER.ordinal();
        elder.postX = elder.homeX = x + 1;
        elder.postY = elder.homeY = Y;
        elder.postZ = elder.homeZ = z;
        s.slots.add(elder);
        return reg.add(s);
    }

    /** Record `s` out of the registry again (SettlementRegistrySC.remove takes a RELAY only - the size first). */
    static void drop(SettlementRegistrySC reg, SettlementRegistrySC.Settlement s) {
        if (s == null || reg.byId(s.id) != s) {
            return;
        }
        s.size = SettlementSizeSC.RELAY.ordinal();
        reg.remove(s.id);
    }

    /** A Tuner bound to the Elder's slot of `s` (never spawned). */
    private static EntityTunerSC elder(WorldServer w, SettlementRegistrySC.Settlement s) {
        List<Integer> sl = s.slotsOf(NpcKindSC.ELDER);
        EntityTunerSC t = new EntityTunerSC(w);
        t.bindTo(s, sl.isEmpty() ? 0 : sl.get(0).intValue());
        t.setLocationAndAngles(s.x + 1.5D, s.y, s.z + 0.5D, 0F, 0F);
        return t;
    }

    /** The player's record as a newcomer to the board: «Знакомый», today's counters empty, no contracts. */
    private static SettlementRegistrySC.PlayerRec fresh(SettlementRegistrySC reg, WorldTestArmourerSC.StationPlayer p, long day) {
        SettlementRegistrySC.PlayerRec pr = reg.player(p.getUniqueID());
        pr.reputation = Math.max(pr.reputation, ReputationSC.ACQUAINTANCE_FROM + 50);
        pr.rollDay(day - 1);
        pr.rollDay(day);
        pr.contracts.clear();
        pr.contractsTaken = 0;
        pr.extra.removeTag(ContractTableSC.BIG_EXTRA);
        p.inventory.clearInventory(null, -1);
        return pr;
    }

    private static SettlementRegistrySC.ContractRec open(SettlementRegistrySC.PlayerRec pr, String key) {
        for (SettlementRegistrySC.ContractRec r : pr.contracts) {
            if (!r.done && r.key.equals(key)) {
                return r;
            }
        }
        return null;
    }

    private static void give(WorldTestArmourerSC.StationPlayer p, ItemStack what, int n) {
        while (n > 0) {
            ItemStack s = what.copy();
            s.stackSize = Math.min(n, s.getMaxStackSize());
            n -= s.stackSize;
            p.inventory.addItemStackToInventory(s);
        }
    }

    private static int count(WorldTestArmourerSC.StationPlayer p, ItemStack what) {
        int n = 0;
        for (ItemStack s : p.inventory.mainInventory) {
            if (s != null && s.getItem() == what.getItem() && s.getItemDamage() == what.getItemDamage()) {
                n += s.stackSize;
            }
        }
        return n;
    }

    static String code(int r) {
        return r >= 0 && r < TradeServerSC.RESULT_KEYS.length ? TradeServerSC.RESULT_KEYS[r] : String.valueOf(r);
    }

    static GameProfile profile(String name) {
        return new GameProfile(UUID.nameUUIDFromBytes(("sc-pd1-" + name).getBytes()), "[SCTestPd1]");
    }

    private static void print(boolean ok, String what) {
        System.out.println("[SC-WORLDTEST] " + (ok ? "PASS" : "FAIL") + " contracts: " + what);
    }
}
