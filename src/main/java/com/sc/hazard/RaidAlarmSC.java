package com.sc.hazard;

import java.util.UUID;

import com.sc.dim.SubstrateConfigSC;
import com.sc.settle.ReputationSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.settle.trade.TradeServerSC;
import com.sc.settle.trade.TradeTableSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.util.FakePlayer;

/**
 * ПД-3 (docs/plan-ideas-2026-10-26.md, «Тревога по вызову», ★ A): a raid called for - the Elder's / the Captain's service (TradeOfferSC
 * .OUT_ALARM): «Тревога» from «Знакомый» (one a player every ALARM_DAYS), «Тяжёлая ночь» from «Свой» (every HEAVY_DAYS: a fourth wave -
 * the Exo Executioner and a Commander Trooper -, the reward x HEAVY_MUL). The DEPOSIT is paid as the deal; the raid begins the first
 * night the buyer defends that settlement (RaidSC.checks - no roll, no cooldown of the settlement's); won - the deposit back with the
 * usual reward (to the buyer by UUID even if he died in it or stepped off: returnDeposit, owed until his respawn / login); lost - the deposit burns
 * and the buyer's own cooldown of the kind grows a day (ПС-2: the settlement's trade stays open - a called raid closes it for nobody).
 * After the fight a summary line: the waves,
 * the time, how many raiders each defender killed. Kept: the settlement's pending call (Settlement.extra EXTRA {by: UUID, k: KIND_*, d:
 * the day it was called}) and the player's last days (PlayerRec.extra EXTRA {a: alarm day, h: heavy day}).
 * <p>
 * «Тяжёлая ночь» is sold only where a Captain serves (SettlementSizeSC.captain: a City always, a Settlement by its seed) - R_CAPTAIN;
 * never at an embassy or a relay post (R_SIZE). While a call waits, the same deal's line at that Elder takes it back (cancel: the
 * deposit back, the player's day kept - no calling twice in a row by it).
 * <p>
 * ПС-1: the call waits TERM_DAYS days at most (from its day DAY): then it is taken off, the deposit back to the buyer (now, or owed till
 * his login - OWED_EXPIRED) with sc.alarm.expired. ПС-3: a begun call stays at the settlement marked RUN («the raid is on») until its raid
 * ends (RaidRunSC.endCall); a RUN mark without a raid in memory (the server stopped mid-raid) - the call waits again, its deposit in it,
 * its term from that day (maintain). ПС-4: a called raid called off (no room at the gate, Peaceful) - its own line sc.raid.deposit.void.
 * The saves' format: old calls / owed deposits read as before; RUN, OWED_VOID, OWED_EXPIRED are new keys beside the old ones.
 * <p>
 * Fixed (the coordinator's): the class, the kinds, the keys, the numbers' names, the signatures. Bodies and TODO(balance) - ПД-3's.
 */
public final class RaidAlarmSC {

    public static final int KIND_ALARM = 1, KIND_HEAVY = 2;
    /** Settlement.extra / PlayerRec.extra key, and the fields. */
    public static final String EXTRA = "A", BY = "by", KIND = "k", DAY = "d", LAST_ALARM = "a", LAST_HEAVY = "h";
    /** ПД-3 (the review's fix, appended): PlayerRec.extra EXTRA - the deposits owed to a buyer not online and alive at his raid's win. */
    public static final String OWED = "o";
    /** ПС-3: Settlement.extra EXTRA - the call's raid has begun (kept till it ends). ПС-4 / ПС-1: PlayerRec.extra EXTRA - the deposits owed of
     *  called raids called off, of calls expired (each paid with its own line). */
    public static final String RUN = "run", OWED_VOID = "ov", OWED_EXPIRED = "ox";
    /** ПС-1: the days a call waits for its night at most. TODO(balance) */
    public static final int TERM_DAYS = 3;
    /** returnDeposit's reasons: a raid won, called off, a call expired. */
    public static final int BACK_WON = 0, BACK_VOID = 1, BACK_EXPIRED = 2;
    /** The deposit (Credit Chips); the days between two calls of a player; the heavy night's reward multiplier. TODO(balance) */
    public static final int DEPOSIT = 8, ALARM_DAYS = 2, HEAVY_DAYS = 3;
    public static final float HEAVY_MUL = 1.5F;
    /** The levels: «Тревога» - «Знакомый», «Тяжёлая ночь» - «Свой». */
    public static final int ALARM_LEVEL = ReputationSC.ACQUAINTANCE, HEAVY_LEVEL = ReputationSC.OWN;
    /** order()'s answers. ПД-3 (appended): R_CAPTAIN - a heavy night where no Captain serves. */
    public static final int OK = 0, R_OFF = 1, R_LEVEL = 2, R_COOLDOWN = 3, R_PENDING = 4, R_RUNNING = 5, R_SIZE = 6, R_CAPTAIN = 7;
    /** lang sc.alarm.refuse.<key> by answer. */
    public static final String[] REFUSE_KEYS = {"", "off", "level", "cooldown", "pending", "running", "size", "captain"};

    private RaidAlarmSC() {
    }

    // ================================================================== pure

    /** Pure: a player whose last call of `kind` was on `lastDay` (-1 never) may call again on `today`. */
    public static boolean due(int kind, long lastDay, long today) {
        return lastDay < 0L || today - lastDay >= (kind == KIND_HEAVY ? HEAVY_DAYS : ALARM_DAYS);
    }

    /** Pure: the days left until due (0 - due now). */
    public static int daysLeft(int kind, long lastDay, long today) {
        return due(kind, lastDay, today) ? 0 : (int) ((kind == KIND_HEAVY ? HEAVY_DAYS : ALARM_DAYS) - (today - lastDay));
    }

    /** Pure (ПС-1): a call made on `calledDay` is past its term on `today`. */
    public static boolean expired(long calledDay, long today) {
        return today - calledDay >= TERM_DAYS;
    }

    /** Pure: the kind a deal's key calls (TradeTableSC.ELDER_ALARM / ELDER_HEAVY_NIGHT), 0 - not an alarm. */
    public static int kindOf(String offerKey) {
        return TradeTableSC.ELDER_HEAVY_NIGHT.equals(offerKey) ? KIND_HEAVY : TradeTableSC.ELDER_ALARM.equals(offerKey) ? KIND_ALARM : 0;
    }

    /** Pure: the lang key of answer `code` (null - OK). */
    public static String refuseKey(int code) {
        return code <= OK || code >= REFUSE_KEYS.length ? null : "sc.alarm.refuse." + REFUSE_KEYS[code];
    }

    // ================================================================== the records

    /** The last day `rec` called `kind` (-1 never). */
    public static long lastDay(SettlementRegistrySC.PlayerRec rec, int kind) {
        NBTTagCompound a = rec == null ? null : rec.extra.getCompoundTag(EXTRA);
        String k = kind == KIND_HEAVY ? LAST_HEAVY : LAST_ALARM;
        return a == null || !a.hasKey(k) ? -1L : a.getLong(k);
    }

    /** The UUID of the player whose call waits at `s` (null none). */
    public static UUID pendingBy(SettlementRegistrySC.Settlement s) {
        NBTTagCompound a = s == null ? null : s.extra.getCompoundTag(EXTRA);
        return a == null || !a.hasKey(BY) ? null : SettlementRegistrySC.uuid(a.getString(BY));
    }

    /** The kind of the call waiting at `s` (0 none). */
    public static int pendingKind(SettlementRegistrySC.Settlement s) {
        return pendingBy(s) == null ? 0 : s.extra.getCompoundTag(EXTRA).getInteger(KIND);
    }

    /** `player`'s call of `kind` waits at `s` (the deal's line takes it back) - not while its raid is on (ПС-3). */
    public static boolean pendingBy(SettlementRegistrySC.Settlement s, UUID player, int kind) {
        UUID by = pendingBy(s);
        return by != null && by.equals(player) && pendingKind(s) == kind && !runningCall(s);
    }

    /** ПС-3: the call at `s` has begun and its raid runs now (a RUN mark left by a stopped server is not - the call waits again). */
    public static boolean runningCall(SettlementRegistrySC.Settlement s) {
        return pendingBy(s) != null && s.extra.getCompoundTag(EXTRA).getBoolean(RUN) && RaidSC.running(s.id);
    }

    /** ПС-3 (RaidSC.called): the call at `s` has begun - kept, marked RUN, till its raid ends. */
    static void markRunning(SettlementRegistrySC.Settlement s) {
        if (pendingBy(s) != null) {
            NBTTagCompound a = s.extra.getCompoundTag(EXTRA);
            a.setBoolean(RUN, true);
            s.extra.setTag(EXTRA, a);
        }
    }

    /**
     * Server (RaidSC's tick, refusal): the call at `s` looked after - a RUN mark with no raid in memory (the server stopped mid-raid, ПС-3):
     * the call waits again, its term from `today`; a waiting call past its term (ПС-1): taken off, the deposit back to its buyer
     * (sc.alarm.expired, now or at his login). @return the call was taken off
     */
    public static boolean maintain(SettlementRegistrySC reg, SettlementRegistrySC.Settlement s, long today) {
        UUID by = pendingBy(s);
        if (reg == null || by == null) {
            return false;
        }
        NBTTagCompound a = s.extra.getCompoundTag(EXTRA);
        if (a.getBoolean(RUN)) {
            if (RaidSC.running(s.id)) {
                return false;                                   // its raid is on: no term while it runs
            }
            a.removeTag(RUN);
            a.setLong(DAY, today);
            s.extra.setTag(EXTRA, a);
            reg.markDirty();
            return false;
        }
        if (!expired(a.getLong(DAY), today)) {
            return false;
        }
        clear(s);
        reg.markDirty();
        returnDeposit(reg, by, BACK_EXPIRED);
        return true;
    }

    /** The call at `s` is gone (begun, or taken back). */
    public static void clear(SettlementRegistrySC.Settlement s) {
        if (s != null) {
            s.extra.removeTag(EXTRA);
        }
    }

    // ================================================================== the deal

    /**
     * Server (TradeServerSC, before the payment): why `p` can't call a raid of `kind` on `s` now - R_OFF (the rules dimension.raidAlarms /
     * raids off), R_SIZE (an embassy, a relay post, an unbound Elder), R_CAPTAIN (a heavy night where no Captain serves), R_LEVEL, R_RUNNING
     * (a raid runs there), R_PENDING (a call waits there), R_COOLDOWN (his last call of the kind too recent) - or OK.
     */
    public static int refusal(EntityPlayer p, SettlementRegistrySC.Settlement s, int kind) {
        if (!SubstrateConfigSC.raidAlarms || !SubstrateConfigSC.raids) {
            return R_OFF;
        }
        if (p == null || s == null || !s.sizeKind().substrate() || SettlementSC.isEmbassy(s) || SettlementSC.isRelay(s) || !SettlementSC.inSubstrate(s)) {
            return R_SIZE;
        }
        if (kind == KIND_HEAVY && !s.sizeKind().captain(s.seed)) {
            return R_CAPTAIN;
        }
        SettlementRegistrySC reg = p.worldObj == null || p.worldObj.isRemote ? null : SettlementRegistrySC.get(p.worldObj);
        if (reg == null) {
            return R_SIZE;
        }
        SettlementRegistrySC.PlayerRec rec = reg.player(p.getUniqueID());
        SettlementRegistrySC.LocalRec l = s.localIfAny(p.getUniqueID());
        int level = ReputationSC.localLevel(rec.reputation, l != null && l.enemy(p.worldObj.getTotalWorldTime()));
        if (level < (kind == KIND_HEAVY ? HEAVY_LEVEL : ALARM_LEVEL)) {
            return R_LEVEL;
        }
        if (RaidSC.running(s.id)) {
            return R_RUNNING;
        }
        maintain(reg, s, SettlementSC.day(p.worldObj));     // ПС-1 / ПС-3: an expired call off, a stopped raid's call waiting again
        if (pendingBy(s) != null) {
            return R_PENDING;
        }
        return due(kind, lastDay(rec, kind), SettlementSC.day(p.worldObj)) ? OK : R_COOLDOWN;
    }

    /**
     * Server (TradeServerSC, the deal paid): `p` calls a raid of `kind` on `s` - OK, or why not (R_OFF the rule off, R_LEVEL, R_COOLDOWN his
     * last call too recent, R_PENDING a call waits there already, R_RUNNING a raid runs, R_SIZE an embassy / a relay post).
     */
    public static int order(EntityPlayerMP p, SettlementRegistrySC.Settlement s, int kind) {
        int why = refusal(p, s, kind);
        if (why != OK) {
            return why;
        }
        SettlementRegistrySC reg = SettlementRegistrySC.get(p.worldObj);
        long day = SettlementSC.day(p.worldObj);
        NBTTagCompound a = new NBTTagCompound();
        a.setString(BY, p.getUniqueID().toString());
        a.setInteger(KIND, kind);
        a.setLong(DAY, day);
        s.extra.setTag(EXTRA, a);
        SettlementRegistrySC.PlayerRec rec = reg.player(p.getUniqueID());
        NBTTagCompound mine = rec.extra.getCompoundTag(EXTRA);
        mine.setLong(kind == KIND_HEAVY ? LAST_HEAVY : LAST_ALARM, day);
        rec.extra.setTag(EXTRA, mine);
        reg.markDirty();
        if (!(p instanceof FakePlayer)) {
            EmpStormSC.tell(p, EnumChatFormatting.GOLD, kind == KIND_HEAVY ? "sc.alarm.orderedHeavy" : "sc.alarm.ordered",
                    String.valueOf(RaidRulesSC.reach(s.size)), SettlementSC.displayName(s));
        }
        return OK;
    }

    /**
     * Server (TradeServerSC, the same deal's line while `p`'s call of `kind` waits at `s`): the call taken back - the deposit back into
     * his inventory (outside the day's 64), his day kept. @return true if there was one to take back
     */
    public static boolean cancel(EntityPlayer p, SettlementRegistrySC.Settlement s, int kind) {
        if (p == null || !pendingBy(s, p.getUniqueID(), kind)) {
            return false;                                       // ПС-3: not while its raid is on (pendingBy)
        }
        clear(s);
        SettlementRegistrySC reg = SettlementRegistrySC.get(p.worldObj);
        if (reg != null) {
            reg.markDirty();
        }
        TradeServerSC.giveChips(p, DEPOSIT);
        if (!(p instanceof FakePlayer)) {
            EmpStormSC.tell(p, EnumChatFormatting.GRAY, "sc.alarm.cancelled", String.valueOf(DEPOSIT));
        }
        return true;
    }

    /** Server (RaidRunSC, a called raid won): returnDeposit(reg, buyer, BACK_WON). */
    public static void returnDeposit(SettlementRegistrySC reg, UUID buyer) {
        returnDeposit(reg, buyer, BACK_WON);
    }

    /**
     * Server (RaidRunSC, a called raid won or called off; maintain, a call expired): the deposit back to `buyer` wherever he is - online and
     * alive: into his inventory (outside the day's 64); dead or offline: owed (OWED / OWED_VOID / OWED_EXPIRED by `why`), paid at his next
     * respawn or login (payOwed). Only a loss burns it. `why` - BACK_* (the line: won, didn't take place, expired).
     */
    public static void returnDeposit(SettlementRegistrySC reg, UUID buyer, int why) {
        if (reg == null || buyer == null) {
            return;
        }
        EntityPlayerMP p = online(buyer);
        if (p != null && p.isEntityAlive()) {
            TradeServerSC.giveChips(p, DEPOSIT);
            p.inventoryContainer.detectAndSendChanges();
            tellBack(p, why, DEPOSIT);
            return;
        }
        SettlementRegistrySC.PlayerRec rec = reg.player(buyer);
        NBTTagCompound a = rec.extra.getCompoundTag(EXTRA);
        String k = owedKey(why);
        a.setInteger(k, a.getInteger(k) + DEPOSIT);
        rec.extra.setTag(EXTRA, a);
        reg.markDirty();
    }

    /** The PlayerRec.extra EXTRA key the deposits owed for `why` are kept in. */
    private static String owedKey(int why) {
        return why == BACK_VOID ? OWED_VOID : why == BACK_EXPIRED ? OWED_EXPIRED : OWED;
    }

    /** Pure: the line of a deposit back for `why` (BACK_*). */
    public static String backKey(int why) {
        return why == BACK_VOID ? "sc.raid.deposit.void" : why == BACK_EXPIRED ? "sc.alarm.expired" : "sc.raid.deposit.owed";
    }

    /** The line of a deposit back (sc.alarm.expired: the term's days, then the chips; the others: the chips). */
    static void tellBack(EntityPlayer p, int why, int chips) {
        if (why == BACK_EXPIRED) {
            EmpStormSC.tell(p, EnumChatFormatting.AQUA, backKey(why), String.valueOf(TERM_DAYS), String.valueOf(chips));
        } else {
            EmpStormSC.tell(p, EnumChatFormatting.AQUA, backKey(why), String.valueOf(chips));
        }
    }

    /** Server (RaidSC: a login, a respawn): the deposits owed to `p` (OWED) into his inventory. @return there were some */
    public static boolean payOwed(EntityPlayer p) {
        if (p == null || p.worldObj == null || p.worldObj.isRemote || p instanceof FakePlayer || !p.isEntityAlive()) {
            return false;
        }
        SettlementRegistrySC reg = SettlementRegistrySC.get(p.worldObj);
        SettlementRegistrySC.PlayerRec rec = reg == null ? null : reg.playerIfAny(p.getUniqueID());
        if (rec == null || !rec.extra.hasKey(EXTRA, 10)) {
            return false;
        }
        NBTTagCompound a = rec.extra.getCompoundTag(EXTRA);
        boolean paid = false;
        int[] whys = {BACK_WON, BACK_VOID, BACK_EXPIRED};
        for (int why : whys) {
            String k = owedKey(why);
            int owed = a.getInteger(k);
            if (owed <= 0) {
                continue;
            }
            a.removeTag(k);
            reg.markDirty();
            TradeServerSC.giveChips(p, owed);
            tellBack(p, why, owed);
            paid = true;
        }
        if (paid) {
            p.inventoryContainer.detectAndSendChanges();
        }
        return paid;
    }

    /**
     * ПС-2 (RaidRunSC.lose, a called raid lost): the buyer's cooldown of `kind` a day longer - his last day of it moved to a day past
     * max(it, today). No trade closed for anyone.
     */
    static void lostCooldown(SettlementRegistrySC reg, UUID buyer, int kind, long today) {
        if (reg == null || buyer == null) {
            return;
        }
        SettlementRegistrySC.PlayerRec rec = reg.player(buyer);
        NBTTagCompound mine = rec.extra.getCompoundTag(EXTRA);
        String k = kind == KIND_HEAVY ? LAST_HEAVY : LAST_ALARM;
        long last = mine.hasKey(k) ? mine.getLong(k) : today;
        mine.setLong(k, Math.max(last, today) + 1L);
        rec.extra.setTag(EXTRA, mine);
        reg.markDirty();
    }

    /** The real player `id` online (any world), or null. */
    private static EntityPlayerMP online(UUID id) {
        net.minecraft.server.MinecraftServer srv = net.minecraft.server.MinecraftServer.getServer();
        if (id == null || srv == null || srv.getConfigurationManager() == null) {
            return null;
        }
        for (Object o : srv.getConfigurationManager().playerEntityList) {
            if (o instanceof EntityPlayerMP && !(o instanceof FakePlayer) && id.equals(((EntityPlayerMP) o).getUniqueID())) {
                return (EntityPlayerMP) o;
            }
        }
        return null;
    }

    /** Server stopped. */
    public static void clearAll() {
    }
}
