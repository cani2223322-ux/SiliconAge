package com.sc.embassy;

import java.util.List;

import com.sc.embassy.api.AirfieldInfoSC;
import com.sc.embassy.api.EmbassyAirApiSC;
import com.sc.embassy.api.IEmbassyAirHookSC;
import com.sc.inventory.ContainerTradeSC;
import com.sc.settle.NpcKindSC;
import com.sc.settle.SettleNetSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.settle.npc.EntityTunerSC;
import com.sc.settle.npc.NpcNamesSC;

import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE T (docs/plan-embassy-airfield-core.md §8, rule ЛП-17): the server side of the Dispatcher's «Лётное поле» tab (GuiTradeSC; only
 * at a Dispatcher of an embassy that HAS an airfield - none: no tab, no message). TradeServerSC.open calls sendState after the flights'
 * state (the coordinator's hook); SettleNetSC routes AIR_DO / AIR_REFRESH here (onMessage checks the sender's open screen at that
 * Dispatcher itself, as FlightServerSC.openDispatcher).
 * <p>
 * SettleNetSC.AIR_STATE {tuner: the entity id, id: the embassy, k: kind, icao, rl / rh: the runway numbers, lore: 0 - without the add-on's
 * hook (no add-on, or one that registers none - 0.1.0; «Поле законсервировано, бортов с Пробоя не было», «Связи с бортами нет со времён
 * Пробоя») / 1 - with it («Поле открыто для машин
 * зелёного мира»), adm: the player admitted by the air defence, lvl: his level, pass: his 7700 pass state (0 none today, 1 running, 2 used),
 * book: his «Лётная книжка» here (landed), gas: the column's armour gases price a 4 000 mB and the mB left today, rows: the hook's lines
 * as IChatComponent JSON (IChatComponent.Serializer, at most EmbassyAirApiSC.TAB_ROWS), atis: the hook's ATIS line JSON (or none), act:
 * the hook's action button shown}; AIR_RESULT {tuner, ok, key: a lang key, args} after an AIR_DO. AIR_DO {act} - the hook's
 * tabAction(p, field, act); AIR_REFRESH {} - the state again. Never sent to a FakePlayer (SettleNetSC.send).
 * <p>
 * Fixed (the coordinator's): the class, the signatures, the message kinds. The bodies are package T's.
 * <p>
 * T's details: AIR_STATE also carries n (the embassy's name code - NpcNamesSC.code: the tab's «Лётное поле „Тантал-1“»), pt (a running
 * pass's ticks left - the screen counts them down and asks again at 0), gas {p: chips a UNIT, l: mB left today, u: the UNIT, d: the day's
 * cap, c: EmbassyAirRulesSC.refusal of a single mB now - PAY_LEVEL: the column serves from «Знакомый», PAY_LIMIT: the day's cap reached,
 * m: the level it serves from}, and the numbers its lines quote (ad: the zone's radius, ws: the warning's seconds, ps: a pass's seconds,
 * al: the level that admits, bc / bt: the logbook's chips and «Доверие»). rows / atis are the hook's for a real player only (the world
 * test's FakePlayer gets none);
 * a throw of the hook is caught and logged (at most once a minute) and the tab shows the rest. Nothing here writes a record: the 7700
 * ledger and the logbook are only read (the pass is the add-on's to ask for, the logbook is package R's). AIR_DO acts only
 * TAB_ACTION_SERVICE (the tab's one button); when the hook's window replaced the trade screen nothing more is sent, else AIR_RESULT
 * (sc.airfield.tab.result.<sent | noLink | unknown | failed>, no args) and the state again.
 */
public final class EmbassyAirServerSC {

    /** AIR_RESULT's keys (lang). */
    public static final String R_SENT = "sc.airfield.tab.result.sent", R_NO_LINK = "sc.airfield.tab.result.noLink",
            R_UNKNOWN = "sc.airfield.tab.result.unknown", R_FAILED = "sc.airfield.tab.result.failed";
    /** AIR_STATE "pass": none given today, one running now, today's used. */
    public static final int PASS_NONE = 0, PASS_RUNNING = 1, PASS_USED = 2;
    /** A line of the hook longer than this (its JSON) is left out - the packet stays small. */
    private static final int MAX_JSON = 4096;
    /** A failing hook is logged at most this often (ms). */
    private static final long WARN_GAP = 60000L;

    private static volatile long lastWarn;

    private EmbassyAirServerSC() {
    }

    /** SettleNetSC.ToServer's AIR_DO / AIR_REFRESH from `p` (the server thread). */
    public static void onMessage(EntityPlayerMP p, int kind, NBTTagCompound data) {
        if (p == null || p instanceof FakePlayer || data == null || p.worldObj == null || p.worldObj.isRemote) {
            return;
        }
        EntityTunerSC t = openDispatcher(p);
        if (t == null) {
            return;                                     // the screen is closed, the Dispatcher gone or too far: nothing (a stale click)
        }
        if (kind == SettleNetSC.AIR_DO) {
            AirfieldInfoSC f = field(p, t);
            if (f == null) {
                return;                                 // no field here: no tab (a stale or forged click)
            }
            String act = data.getString("act");
            IEmbassyAirHookSC h = EmbassyAirApiSC.hook();
            boolean ok = false;
            String key;
            if (h == null) {
                key = R_NO_LINK;                        // no add-on: no tower on the air since the Breakdown
            } else if (!EmbassyAirApiSC.TAB_ACTION_SERVICE.equals(act)) {
                key = R_UNKNOWN;                        // the tab has the one button
            } else {
                try {
                    h.tabAction(p, f, act);
                    ok = true;
                    key = R_SENT;
                } catch (Throwable e) {
                    warn("tabAction", e);
                    key = R_FAILED;
                }
            }
            if (openDispatcher(p) != t) {
                return;                                 // the add-on's window replaced the trade screen: no tab to tell
            }
            NBTTagCompound res = new NBTTagCompound();
            res.setInteger("tuner", t.getEntityId());
            res.setBoolean("ok", ok);
            res.setString("key", key);
            res.setTag("args", new NBTTagList());
            SettleNetSC.send(p, SettleNetSC.AIR_RESULT, res);
        } else if (kind != SettleNetSC.AIR_REFRESH) {
            return;
        }
        sendState(p, t);
    }

    /** The «Лётное поле» tab's state for `p` at Dispatcher `t` (SettleNetSC.AIR_STATE; nothing for an embassy without a field). */
    public static void sendState(EntityPlayerMP p, EntityTunerSC t) {
        if (p == null || p instanceof FakePlayer || t == null || t.kind() != NpcKindSC.DISPATCHER) {
            return;
        }
        NBTTagCompound n = state(p, t);
        if (n != null) {
            SettleNetSC.send(p, SettleNetSC.AIR_STATE, n);
        }
    }

    /** AIR_STATE's compound for `p` at Dispatcher `t` without sending it (the world test; null - none: no field, a client). */
    public static NBTTagCompound state(EntityPlayer p, EntityTunerSC t) {
        SettlementRegistrySC.Settlement s = embassy(p, t);
        AirfieldInfoSC f = s == null ? null : EmbassyAirApiSC.airfield(p.worldObj, s.id);
        SettlementRegistrySC reg = f == null ? null : SettlementRegistrySC.get(p.worldObj);
        if (reg == null) {
            return null;
        }
        long now = p.worldObj.getTotalWorldTime(), today = SettlementSC.day(p.worldObj);
        SettlementRegistrySC.PlayerRec rec = reg.playerIfAny(p.getUniqueID());
        SettlementRegistrySC.LocalRec l = s.localIfAny(p.getUniqueID());
        boolean enemy = l != null && l.enemy(now);
        int level = EmbassyAirApiSC.levelOf(p, s.id);
        NBTTagCompound n = new NBTTagCompound();
        n.setInteger("tuner", t.getEntityId());
        n.setInteger("id", s.id);
        n.setInteger("n", NpcNamesSC.code(s.nameElement, s.nameNumber));
        n.setByte("k", (byte) f.kind);
        n.setString("icao", f.icao == null ? "" : f.icao);
        n.setString("rl", f.numberLow == null ? "" : f.numberLow);
        n.setString("rh", f.numberHigh == null ? "" : f.numberHigh);
        n.setByte("lore", (byte) (EmbassyAirApiSC.hook() != null ? 1 : 0));    // the review's: its hook (add-on 0.1.0 registers none)
        n.setBoolean("adm", EmbassyAirApiSC.admitted(p, s.id));
        n.setByte("lvl", (byte) level);
        // ЛП-15: the 7700 ledger (LocalRec.extra "X" {d, u}) - only read: the pass is the add-on's to ask for
        NBTTagCompound x = l == null ? new NBTTagCompound() : l.extra.getCompoundTag(EmbassyAirRulesSC.LEDGER);
        long until = x.getLong(EmbassyAirRulesSC.PASS_UNTIL);
        long last = x.hasKey(EmbassyAirRulesSC.PASS_DAY) ? x.getLong(EmbassyAirRulesSC.PASS_DAY) : -1L;
        int pass = until > now ? PASS_RUNNING : EmbassyAirRulesSC.passAvailable(last, today) ? PASS_NONE : PASS_USED;
        n.setByte("pass", (byte) pass);
        n.setInteger("pt", pass == PASS_RUNNING ? (int) Math.min(Integer.MAX_VALUE, until - now) : 0);
        n.setBoolean("book", EmbassyAirServiceSC.landed(rec, s.id));
        n.setTag("gas", gas(s, l, level, enemy, today));
        n.setInteger("ad", EmbassyAirRulesSC.AD_RADIUS);
        n.setInteger("ws", EmbassyAirRulesSC.WARN_SECONDS);
        n.setInteger("ps", EmbassyAirRulesSC.PASS_SECONDS);
        n.setByte("al", (byte) EmbassyAirRulesSC.ADMIT_LEVEL);
        n.setInteger("bc", EmbassyAirRulesSC.LOGBOOK_CHIPS);
        n.setInteger("bt", EmbassyAirRulesSC.LOGBOOK_TRUST);
        IEmbassyAirHookSC h = EmbassyAirApiSC.hook();
        n.setTag("rows", rows(h, p, f));
        String atis = atis(h, p, f);
        if (atis != null) {
            n.setString("atis", atis);
        }
        n.setBoolean("act", h != null);
        return n;
    }

    /** The core's column (§7.2): a UNIT of the armour's gases priced for this player, the mB left today, why it would refuse now. */
    private static NBTTagCompound gas(SettlementRegistrySC.Settlement s, SettlementRegistrySC.LocalRec l, int level, boolean enemy, long today) {
        int sv = EmbassyAirRulesSC.S_ARMOUR_GAS, unit = EmbassyAirRulesSC.UNIT[sv], cap = EmbassyAirRulesSC.PER_DAY[sv];
        int used = l == null || l.day != today ? 0 : l.count(EmbassyAirRulesSC.COUNT_PREFIX + EmbassyAirRulesSC.SERVICE_KEYS[sv]);
        NBTTagCompound g = new NBTTagCompound();
        g.setInteger("p", EmbassyAirRulesSC.price(sv, level, s.seed, unit, false));
        g.setInteger("l", cap > 0 ? Math.max(0, cap - used) : -1);
        g.setInteger("u", unit);
        g.setInteger("d", cap);
        g.setByte("c", (byte) EmbassyAirRulesSC.refusal(sv, level, enemy, 1, used));
        g.setByte("m", (byte) EmbassyAirRulesSC.MIN_LEVEL[sv]);
        return g;
    }

    /** The hook's rows for `p` as JSON (at most TAB_ROWS; a real player only; none on a throw). */
    private static NBTTagList rows(IEmbassyAirHookSC h, EntityPlayer p, AirfieldInfoSC f) {
        NBTTagList out = new NBTTagList();
        if (h == null || !(p instanceof EntityPlayerMP) || p instanceof FakePlayer) {
            return out;
        }
        try {
            List<IChatComponent> l = h.serviceRows((EntityPlayerMP) p, f);
            for (int i = 0; l != null && i < l.size() && out.tagCount() < EmbassyAirApiSC.TAB_ROWS; i++) {
                String j = json(l.get(i));
                if (j != null) {
                    out.appendTag(new NBTTagString(j));
                }
            }
        } catch (Throwable e) {
            warn("serviceRows", e);
            return new NBTTagList();
        }
        return out;
    }

    /** The hook's ATIS line as JSON (null: none, a throw; a real player only). */
    private static String atis(IEmbassyAirHookSC h, EntityPlayer p, AirfieldInfoSC f) {
        if (h == null || !(p instanceof EntityPlayerMP) || p instanceof FakePlayer) {
            return null;
        }
        try {
            return json(h.atisLine(p.worldObj, f));
        } catch (Throwable e) {
            warn("atisLine", e);
            return null;
        }
    }

    /** `c` as IChatComponent.Serializer's JSON (null: none, too long). */
    private static String json(IChatComponent c) {
        if (c == null) {
            return null;
        }
        String j = IChatComponent.Serializer.func_150696_a(c);
        return j == null || j.isEmpty() || j.length() > MAX_JSON ? null : j;
    }

    private static void warn(String what, Throwable e) {
        long ms = System.currentTimeMillis();
        if (ms - lastWarn >= WARN_GAP) {
            lastWarn = ms;
            FMLLog.warning("[Silicon Age] Embassy airfields: the hook's %s threw %s (the Dispatcher's tab shows the rest)", what, e);
        }
    }

    /** Dispatcher `t`'s embassy for `p` on the server (null: a client, not a Dispatcher, unbound, not an embassy). */
    private static SettlementRegistrySC.Settlement embassy(EntityPlayer p, EntityTunerSC t) {
        if (p == null || t == null || p.worldObj == null || p.worldObj.isRemote || t.kind() != NpcKindSC.DISPATCHER || t.settlementId() < 0) {
            return null;
        }
        SettlementRegistrySC reg = SettlementRegistrySC.get(p.worldObj);
        SettlementRegistrySC.Settlement s = reg == null ? null : reg.byId(t.settlementId());
        return s != null && SettlementSC.isEmbassy(s) ? s : null;
    }

    /** The airfield of Dispatcher `t`'s embassy (null: none). */
    private static AirfieldInfoSC field(EntityPlayer p, EntityTunerSC t) {
        SettlementRegistrySC.Settlement s = embassy(p, t);
        return s == null ? null : EmbassyAirApiSC.airfield(p.worldObj, s.id);
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
}
