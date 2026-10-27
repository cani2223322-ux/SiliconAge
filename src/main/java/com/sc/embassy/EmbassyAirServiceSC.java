package com.sc.embassy;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.sc.embassy.api.AirfieldInfoSC;
import com.sc.embassy.api.EmbassyAirApiSC;
import com.sc.embassy.api.IEmbassyAirHookSC;
import com.sc.settle.ReputationSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.settle.trade.TradeServerSC;
import com.sc.util.ArmorGasSC;

import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE R (docs/plan-embassy-airfield-core.md §7, rules ЛП-10..ЛП-12, ЛП-16): the airfield's services on the server - the «Колонка
 * обслуживания», the payments the Vehicles add-on asks for (EmbassyAirApiSC.quote / pay), the «Лётная книжка». Every number and pure
 * decision is EmbassyAirRulesSC's (the coordinator's); here: the player, the chips, the records. Server only; a FakePlayer is never served.
 * <ul>
 * <li><b>useColumn</b> (BlockShowcase2SC.onBlockActivated, server): the column's embassy (SettlementSC.airfieldAt of its column, the column
 * one of its Field.columnBlock); not sneaking and the add-on's hook there - hook.openService (true: done); else the core's armour gases:
 * the first click quotes (chat: what the worn armour lacks of COLUMN_GASES - ArmorGasSC.suitCapacity - suitAmount -, within PER_DAY's
 * 16 000 mB today (LocalRec.counts COUNT_PREFIX + "armourGas", in mB), the price - EmbassyAirRulesSC.price - or the refusal), a second
 * click within COLUMN_CONFIRM_TICKS pays (TradeServerSC.takeChips) and fills (ArmorGasSC.suitFill), counts the mB, marks the registry
 * dirty; the pending quote is per UUID (two players at once: each his own), cleared by clearAll / a log-out;</li>
 * <li><b>quote / pay</b> (the add-on's services, by EmbassyAirRulesSC's index): refusal (no field: PAY_NO_FIELD), then the chips (PAY_CHIPS:
 * not enough in the main inventory); pay takes them, counts `amount` in LocalRec.counts COUNT_PREFIX + the key and marks the registry
 * dirty; `free` (parking with a talon - EmbassyAirApiSC.hasTalon -, the day's first tow, the first chart: FREE_*) - 0 chips. No «Доверие»
 * for a service (not a deal), no item sold;</li>
 * <li><b>firstLanding</b> (the «Лётная книжка», the add-on calls it): once an embassy a player (PlayerRec.extra LOGBOOK "X" {f: int[] ids}) -
 * LOGBOOK_CHIPS within today's 64 (TradeRulesSC.chipsAllowed, PlayerRec.chips) given (TradeServerSC.giveChips) and LOGBOOK_TRUST
 * «Доверия» (ReputationSC.give(..., SRC_AIR, day, ReputationSC.ceiling(s))), a chat line (sc.airfield.logbook.first: the embassy, the
 * chips, the «Доверие» given; .capped - the day's 64 already given: no chips); later ones: false, nothing.</li>
 * </ul>
 * Fixed (the coordinator's): the class, the signatures. The bodies are package R's.
 * <p>
 * Package R's details: a held right click repeats every 4 ticks - a click within HOLD_TICKS of the last one is no click (holding the
 * button never confirms a quote: release it and click again); the second click confirms only the same column, the same embassy and
 * the same price (the amounts are measured again then - a tank that took less gives the difference back); the core's free first chart
 * is counted for good per player per embassy (LocalRec.extra "X" CHARTS - beside the 7700 ledger's d / u, which EmbassyAirDefenceSC
 * keeps), the free tow per game day (LocalRec.counts "air.tow").
 */
public final class EmbassyAirServiceSC {

    /** A held right click repeats every 4 ticks: a click within this many ticks of the last one is ignored (never a confirmation). */
    public static final int HOLD_TICKS = 6;
    /** LocalRec.extra "X" (EmbassyAirRulesSC.LEDGER): the approach charts paid for at this embassy, ever (the first FREE_CHARTS are free). */
    public static final String CHARTS = "c";

    /** The column's refusals of its own (beside EmbassyAirRulesSC.PAY_*): no tank of a column's gas worn; every such tank full. */
    private static final int NO_TANKS = -1, FULL = -2;

    /** Player -> his last click on a column and his pending quote. Server thread (forget: a log-out, the server stopped). */
    private static final Map<UUID, Column> COLUMNS = new HashMap<UUID, Column>();

    private EmbassyAirServiceSC() {
    }

    /** One player at the columns: the last click (a held one repeats), the quote waiting for its second click. */
    private static final class Column {
        /** World time of the last click (0: none). */
        long click;
        /** World time of the pending quote (-1: none). */
        long at = -1L;
        /** The quoted column (world block, dimension), its embassy, the chips quoted. */
        int dim, x, y, z, id, price;
    }

    /** The armour's gases a column offers `p` now: what each gas gets, the price, or why not. */
    private static final class GasQuote {
        /** EmbassyAirRulesSC.PAY_OK, a PAY_* refusal, NO_TANKS or FULL. */
        int why = EmbassyAirRulesSC.PAY_OK;
        int level;
        /** mB of each of COLUMN_GASES to fill; the chips; the day's cap trimmed it. */
        final int[] fill = new int[EmbassyAirRulesSC.COLUMN_GASES.length];
        int price;
        boolean capped;
    }

    // ================================================================== the column (ЛП-11)

    /** A «Колонка обслуживания» at (x, y, z) right-clicked by `p` (the server; the block answers true itself). */
    public static void useColumn(World w, int x, int y, int z, EntityPlayer p) {
        if (w == null || w.isRemote || !real(p) || p.worldObj != w) {
            return;
        }
        EntityPlayerMP mp = (EntityPlayerMP) p;
        long now = w.getTotalWorldTime();
        Column c = column(mp.getUniqueID());
        if (c.click > 0L && now >= c.click && now - c.click < HOLD_TICKS) {
            c.click = now;                                      // the button held: one click
            return;
        }
        c.click = Math.max(1L, now);
        SettlementRegistrySC.Settlement s = SettlementSC.airfieldAt(w, x + 0.5D, z + 0.5D);
        EmbassyAirfieldSC.Field f = s == null ? null : EmbassyAirfieldSC.fieldOf(s);
        int i = f == null ? -1 : columnIndex(f, x, y, z);
        if (i < 0) {
            c.at = -1L;                                         // a column put anywhere else (a creative copy): it serves nothing
            tell(mp, EnumChatFormatting.GRAY, "sc.airfield.column.offline");
            return;
        }
        IEmbassyAirHookSC h = EmbassyAirApiSC.hook();
        if (!mp.isSneaking() && h != null && openService(h, mp, s, i)) {
            c.at = -1L;                                         // the add-on's window: its services, its payments
            return;
        }
        armourGas(mp, s, c, x, y, z, now, h != null);
    }

    /** The add-on's service window for column `i` of `s`: true - opened (a throw is logged: the core serves the click). */
    private static boolean openService(IEmbassyAirHookSC h, EntityPlayerMP p, SettlementRegistrySC.Settlement s, int i) {
        AirfieldInfoSC info = EmbassyAirApiSC.airfield(p.worldObj, s.id);
        if (info == null) {
            return false;
        }
        try {
            return h.openService(p, info, i);
        } catch (Throwable t) {
            FMLLog.warning("[Silicon Age] Embassy airfields: the hook's openService threw %s - the core's column serves the click", t);
            return false;
        }
    }

    /** The column's index in `f.columns` whose block is (x, y, z), -1 none. */
    private static int columnIndex(EmbassyAirfieldSC.Field f, int x, int y, int z) {
        for (int i = 0; f.columns != null && i < f.columns.length; i++) {
            int[] b = f.columnBlock(i);
            if (b[0] == x && b[1] == y && b[2] == z) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The core's armour gases at column (x, y, z) of `s`: the first click quotes (or refuses), a second one within COLUMN_CONFIRM_TICKS
     * (the same column, embassy and price) pays and fills. `sneak` - the add-on is there: the second click must sneak too.
     */
    private static void armourGas(EntityPlayerMP p, SettlementRegistrySC.Settlement s, Column c, int x, int y, int z, long now, boolean sneak) {
        World w = p.worldObj;
        SettlementRegistrySC r = SettlementRegistrySC.get(w);
        if (r == null) {
            return;
        }
        GasQuote q = gasQuote(p, s);
        if (q.why != EmbassyAirRulesSC.PAY_OK) {
            c.at = -1L;
            refuse(p, s, q);
            return;
        }
        int dim = w.provider.dimensionId;
        boolean confirm = c.at >= 0L && now >= c.at && now - c.at <= EmbassyAirRulesSC.COLUMN_CONFIRM_TICKS && c.dim == dim
                && c.x == x && c.y == y && c.z == z && c.id == s.id && c.price == q.price;
        if (!confirm) {
            c.at = -1L;
            tell(p, EnumChatFormatting.AQUA, "sc.airfield.column.quote", parts(q.fill), String.valueOf(q.price));
            if (q.capped) {
                tell(p, EnumChatFormatting.GRAY, "sc.airfield.column.capped",
                        String.valueOf(EmbassyAirRulesSC.PER_DAY[EmbassyAirRulesSC.S_ARMOUR_GAS]));
            }
            int held = TradeServerSC.chipsHeld(p);
            if (held < q.price) {
                tell(p, EnumChatFormatting.GOLD, "sc.airfield.column.chips", String.valueOf(q.price), String.valueOf(held));
                return;
            }
            c.at = now;
            c.dim = dim;
            c.x = x;
            c.y = y;
            c.z = z;
            c.id = s.id;
            c.price = q.price;
            tell(p, EnumChatFormatting.GRAY, sneak ? "sc.airfield.column.againSneak" : "sc.airfield.column.again",
                    String.valueOf(EmbassyAirRulesSC.COLUMN_CONFIRM_TICKS / 20));
            return;
        }
        c.at = -1L;
        if (!TradeServerSC.takeChips(p, q.price)) {
            tell(p, EnumChatFormatting.GOLD, "sc.airfield.column.chips", String.valueOf(q.price), String.valueOf(TradeServerSC.chipsHeld(p)));
            return;
        }
        int[] put = new int[q.fill.length];
        int total = 0;
        for (int i = 0; i < put.length; i++) {
            put[i] = q.fill[i] <= 0 ? 0 : ArmorGasSC.suitFill(p, EmbassyAirRulesSC.COLUMN_GASES[i], q.fill[i], false);
            total += put[i];
        }
        int owed = total <= 0 ? 0 : Math.min(q.price, EmbassyAirRulesSC.price(EmbassyAirRulesSC.S_ARMOUR_GAS, q.level, s.seed, total, false));
        if (owed < q.price) {
            TradeServerSC.giveChips(p, q.price - owed);         // a tank took less than measured: the difference back
        }
        p.inventory.markDirty();
        if (total <= 0) {
            tell(p, EnumChatFormatting.GRAY, "sc.airfield.column.full");
            return;
        }
        SettlementRegistrySC.LocalRec l = s.local(p.getUniqueID());
        l.rollDay(SettlementSC.day(w));
        l.addCount(countKey(EmbassyAirRulesSC.S_ARMOUR_GAS), total);
        r.markDirty();
        tell(p, EnumChatFormatting.GREEN, "sc.airfield.column.paid", parts(put), String.valueOf(owed));
    }

    /**
     * What a column offers `p` at `s` now (nothing changes): refusal's order - «Враг», the level -, then no tank of the column's gases
     * worn, every such tank full, the day's PER_DAY used up; else each gas's lack (ArmorGasSC.suitCapacity - suitAmount, what the tanks
     * really take), in COLUMN_GASES' order within what is left today, and its price. The chips are the caller's.
     */
    private static GasQuote gasQuote(EntityPlayer p, SettlementRegistrySC.Settlement s) {
        GasQuote q = new GasQuote();
        int svc = EmbassyAirRulesSC.S_ARMOUR_GAS;
        World w = p.worldObj;
        SettlementRegistrySC.LocalRec l = s.localIfAny(p.getUniqueID());
        boolean enemy = l != null && l.enemy(w.getTotalWorldTime());
        q.level = level(p, s, enemy);
        int why = EmbassyAirRulesSC.refusal(svc, q.level, enemy, 0L, 0L);
        if (why != EmbassyAirRulesSC.PAY_OK) {
            q.why = why;
            return q;
        }
        ArmorGasSC.Gas[] gases = EmbassyAirRulesSC.COLUMN_GASES;
        int[] need = new int[gases.length];
        long cap = 0L, lack = 0L;
        for (int i = 0; i < gases.length; i++) {
            int c = ArmorGasSC.suitCapacity(p, gases[i]);
            int room = Math.max(0, c - ArmorGasSC.suitAmount(p, gases[i]));
            need[i] = room <= 0 ? 0 : Math.max(0, Math.min(room, ArmorGasSC.suitFill(p, gases[i], room, true)));
            cap += Math.max(0, c);
            lack += need[i];
        }
        if (cap <= 0L) {
            q.why = NO_TANKS;
            return q;
        }
        if (lack <= 0L) {
            q.why = FULL;
            return q;
        }
        long used = usedToday(l, SettlementSC.day(w), svc);
        long left = EmbassyAirRulesSC.PER_DAY[svc] > 0 ? EmbassyAirRulesSC.PER_DAY[svc] - used : Long.MAX_VALUE;
        if (EmbassyAirRulesSC.PER_SERVICE[svc] > 0) {
            left = Math.min(left, EmbassyAirRulesSC.PER_SERVICE[svc]);
        }
        if (left <= 0L) {
            q.why = EmbassyAirRulesSC.PAY_LIMIT;
            return q;
        }
        long amount = Math.min(lack, left);
        q.capped = amount < lack;
        long rest = amount;
        for (int i = 0; i < gases.length; i++) {
            q.fill[i] = (int) Math.min(need[i], rest);
            rest -= q.fill[i];
        }
        why = EmbassyAirRulesSC.refusal(svc, q.level, enemy, amount, used);
        if (why != EmbassyAirRulesSC.PAY_OK) {
            q.why = why;
            return q;
        }
        q.price = EmbassyAirRulesSC.price(svc, q.level, s.seed, amount, false);
        return q;
    }

    /** The column's refusal line of `q` to `p`. */
    private static void refuse(EntityPlayer p, SettlementRegistrySC.Settlement s, GasQuote q) {
        int svc = EmbassyAirRulesSC.S_ARMOUR_GAS;
        switch (q.why) {
            case EmbassyAirRulesSC.PAY_ENEMY:
                tell(p, EnumChatFormatting.RED, "sc.airfield.column.enemy", SettlementSC.displayName(s));
                break;
            case EmbassyAirRulesSC.PAY_LEVEL:
                tell(p, EnumChatFormatting.GOLD, "sc.airfield.column.level",
                        new ChatComponentTranslation(ReputationSC.levelKey(EmbassyAirRulesSC.MIN_LEVEL[svc])),
                        new ChatComponentTranslation(ReputationSC.levelKey(q.level)));
                break;
            case EmbassyAirRulesSC.PAY_LIMIT:
                tell(p, EnumChatFormatting.GOLD, "sc.airfield.column.limit", String.valueOf(EmbassyAirRulesSC.PER_DAY[svc]));
                break;
            case NO_TANKS:
                tell(p, EnumChatFormatting.GRAY, "sc.airfield.column.noArmour", names());
                break;
            case FULL:
                tell(p, EnumChatFormatting.GRAY, "sc.airfield.column.full");
                break;
            default:
                tell(p, EnumChatFormatting.GRAY, "sc.airfield.column.offline");
                break;
        }
    }

    /** „Водород 3000 мБ, Кислород 1000 мБ“ - the gases of `mb` (COLUMN_GASES' order) that have any. */
    private static IChatComponent parts(int[] mb) {
        IChatComponent out = new ChatComponentText("");
        boolean first = true;
        for (int i = 0; i < mb.length; i++) {
            if (mb[i] <= 0) {
                continue;
            }
            if (!first) {
                out.appendSibling(new ChatComponentText(", "));
            }
            first = false;
            out.appendSibling(new ChatComponentTranslation("sc.airfield.column.part", gasName(EmbassyAirRulesSC.COLUMN_GASES[i]),
                    String.valueOf(mb[i])));
        }
        return out;
    }

    /** „Водород, Кислород“ - every gas a column fills. */
    private static IChatComponent names() {
        IChatComponent out = new ChatComponentText("");
        ArmorGasSC.Gas[] gases = EmbassyAirRulesSC.COLUMN_GASES;
        for (int i = 0; i < gases.length; i++) {
            if (i > 0) {
                out.appendSibling(new ChatComponentText(", "));
            }
            out.appendSibling(gasName(gases[i]));
        }
        return out;
    }

    private static IChatComponent gasName(ArmorGasSC.Gas g) {
        return new ChatComponentTranslation("sc.gas." + g.key());
    }

    // ================================================================== the add-on's payments (ЛП-12)

    /**
     * The chips `amount` (the service's measure) of service `service` (EmbassyAirRulesSC.S_*) costs `p` at embassy `s` (>= 0), or the
     * refusal as -code (EmbassyAirRulesSC.PAY_*, never PAY_OK). Nothing changes. Server.
     */
    public static int quote(EntityPlayer p, SettlementRegistrySC.Settlement s, int service, long amount, boolean free) {
        int[] c = charge(p, s, service, amount, free);
        return c[0] != EmbassyAirRulesSC.PAY_OK ? -c[0] : c[1];
    }

    /** Pays for `amount` of `service` at `s` (quote, then the chips taken and the day's count): EmbassyAirRulesSC.PAY_* (PAY_OK: paid). Server. */
    public static int pay(EntityPlayer p, SettlementRegistrySC.Settlement s, int service, long amount, boolean free) {
        int[] c = charge(p, s, service, amount, free);
        if (c[0] != EmbassyAirRulesSC.PAY_OK) {
            return c[0];
        }
        SettlementRegistrySC r = SettlementRegistrySC.get(p.worldObj);
        if (r == null) {
            return EmbassyAirRulesSC.PAY_NO_FIELD;
        }
        if (!TradeServerSC.takeChips(p, c[1])) {
            return EmbassyAirRulesSC.PAY_CHIPS;
        }
        int n = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, amount));
        SettlementRegistrySC.LocalRec l = s.local(p.getUniqueID());
        l.rollDay(SettlementSC.day(p.worldObj));
        l.addCount(countKey(service), n);
        if (service == EmbassyAirRulesSC.S_CHART && n > 0) {
            NBTTagCompound x = l.extra.getCompoundTag(EmbassyAirRulesSC.LEDGER);
            x.setInteger(CHARTS, x.getInteger(CHARTS) + n);     // the free first chart: once an embassy, for good
            l.extra.setTag(EmbassyAirRulesSC.LEDGER, x);
        }
        r.markDirty();
        if (c[1] > 0) {
            p.inventory.markDirty();
        }
        return EmbassyAirRulesSC.PAY_OK;
    }

    /**
     * {code, chips} of `amount` of `service` for `p` at `s`: PAY_NO_FIELD (not a real player on the server, another world, no field),
     * PAY_UNKNOWN, then EmbassyAirRulesSC.refusal (by the day's count), then the price - 0 when `free`, parking while hasTalon, the tows
     * within the day's FREE_TOWS, the charts within FREE_CHARTS ever at this embassy -, then PAY_CHIPS (fewer held). Nothing changes.
     */
    private static int[] charge(EntityPlayer p, SettlementRegistrySC.Settlement s, int service, long amount, boolean free) {
        if (!real(p) || s == null || p.worldObj.provider.dimensionId != s.dim || EmbassyAirfieldSC.fieldOf(s) == null
                || SettlementRegistrySC.get(p.worldObj) == null) {
            return new int[]{EmbassyAirRulesSC.PAY_NO_FIELD, 0};
        }
        if (service < 0 || service >= EmbassyAirRulesSC.SERVICE_KEYS.length || amount < 0L) {
            return new int[]{EmbassyAirRulesSC.PAY_UNKNOWN, 0};
        }
        World w = p.worldObj;
        SettlementRegistrySC.LocalRec l = s.localIfAny(p.getUniqueID());
        boolean enemy = l != null && l.enemy(w.getTotalWorldTime());
        int level = level(p, s, enemy);
        long used = usedToday(l, SettlementSC.day(w), service);
        int why = EmbassyAirRulesSC.refusal(service, level, enemy, amount, used);
        if (why != EmbassyAirRulesSC.PAY_OK) {
            return new int[]{why, 0};
        }
        long charged = amount;
        if (free || service == EmbassyAirRulesSC.S_PARK && EmbassyAirApiSC.hasTalon(p, s.id)) {
            charged = 0L;
        } else if (service == EmbassyAirRulesSC.S_TOW) {
            charged = Math.max(0L, amount - Math.max(0L, EmbassyAirRulesSC.FREE_TOWS - used));
        } else if (service == EmbassyAirRulesSC.S_CHART) {
            charged = Math.max(0L, amount - Math.max(0L, EmbassyAirRulesSC.FREE_CHARTS - (long) charts(l)));
        }
        int price = EmbassyAirRulesSC.price(service, level, s.seed, charged, false);
        if (TradeServerSC.chipsHeld(p) < price) {
            return new int[]{EmbassyAirRulesSC.PAY_CHIPS, price};
        }
        return new int[]{EmbassyAirRulesSC.PAY_OK, price};
    }

    // ================================================================== the «Лётная книжка» (ЛП-16)

    /** The «Лётная книжка»: `p`'s first landing at `s` rewarded (true) - once an embassy a player; never to its «Враг» (not written). Server. */
    public static boolean firstLanding(EntityPlayer p, SettlementRegistrySC.Settlement s) {
        if (!real(p) || s == null || p.worldObj.provider.dimensionId != s.dim || EmbassyAirfieldSC.fieldOf(s) == null) {
            return false;
        }
        World w = p.worldObj;
        SettlementRegistrySC r = SettlementRegistrySC.get(w);
        if (r == null) {
            return false;
        }
        SettlementRegistrySC.LocalRec local = s.localIfAny(p.getUniqueID());
        if (local != null && local.enemy(w.getTotalWorldTime())) {
            return false;                                       // the review's: its «Враг» - no reward, nothing written (as PAY_ENEMY)
        }
        SettlementRegistrySC.PlayerRec rec = r.player(p.getUniqueID());
        if (landed(rec, s.id)) {
            return false;
        }
        NBTTagCompound x = rec.extra.getCompoundTag(EmbassyAirRulesSC.LOGBOOK);
        int[] f = x.getIntArray(EmbassyAirRulesSC.LANDED);
        int[] g = Arrays.copyOf(f, f.length + 1);
        g[f.length] = s.id;
        x.setIntArray(EmbassyAirRulesSC.LANDED, g);
        rec.extra.setTag(EmbassyAirRulesSC.LOGBOOK, x);
        long day = SettlementSC.day(w);
        rec.rollDay(day);                                       // today's chips (the 64 of every settlement together)
        int n = EmbassyAirRulesSC.logbookChips(rec.chips);
        if (n > 0) {
            TradeServerSC.giveChips(p, n);
            rec.chips += n;
        }
        int got = ReputationSC.give(rec, EmbassyAirRulesSC.LOGBOOK_TRUST, ReputationSC.SRC_AIR, day, ReputationSC.ceiling(s));
        r.markDirty();
        if (n > 0) {
            tell(p, EnumChatFormatting.AQUA, "sc.airfield.logbook.first", SettlementSC.displayName(s), String.valueOf(n), ReputationSC.signed(got));
        } else {
            tell(p, EnumChatFormatting.AQUA, "sc.airfield.logbook.capped", SettlementSC.displayName(s), ReputationSC.signed(got));
        }
        return true;
    }

    /** `rec` has landed at embassy `id` already (its «Лётная книжка»). Pure. */
    public static boolean landed(SettlementRegistrySC.PlayerRec rec, int id) {
        if (rec == null) {
            return false;
        }
        int[] f = rec.extra.getCompoundTag(EmbassyAirRulesSC.LOGBOOK).getIntArray(EmbassyAirRulesSC.LANDED);
        for (int i : f) {
            if (i == id) {
                return true;
            }
        }
        return false;
    }

    // ================================================================== for the Dispatcher's tab (package T) and the tests

    /** `p`'s level at embassy `s` (its «Враг» wins; never met the Tuners: «Чужак»). Server. */
    public static int level(EntityPlayer p, SettlementRegistrySC.Settlement s) {
        if (p == null || s == null || p.worldObj == null || p.worldObj.isRemote) {
            return ReputationSC.STRANGER;
        }
        SettlementRegistrySC.LocalRec l = s.localIfAny(p.getUniqueID());
        return level(p, s, l != null && l.enemy(p.worldObj.getTotalWorldTime()));
    }

    /** What of `service` (EmbassyAirRulesSC.S_*, in its measure) `p` has had at `s` today (0: none, another day's count). Server. */
    public static long usedToday(EntityPlayer p, SettlementRegistrySC.Settlement s, int service) {
        return p == null || s == null || p.worldObj == null ? 0L : usedToday(s.localIfAny(p.getUniqueID()), SettlementSC.day(p.worldObj), service);
    }

    /** The armour's gases (mB) `p` may still have from the columns of `s` today (0 .. PER_DAY). Server. */
    public static int armourGasLeft(EntityPlayer p, SettlementRegistrySC.Settlement s) {
        int perDay = EmbassyAirRulesSC.PER_DAY[EmbassyAirRulesSC.S_ARMOUR_GAS];
        return (int) Math.max(0L, perDay - usedToday(p, s, EmbassyAirRulesSC.S_ARMOUR_GAS));
    }

    /** The chips `mb` of the armour's gases cost `p` at `s` (his level, its spread; no refusal checked). Server. */
    public static int armourGasPrice(EntityPlayer p, SettlementRegistrySC.Settlement s, int mb) {
        return s == null ? 0 : EmbassyAirRulesSC.price(EmbassyAirRulesSC.S_ARMOUR_GAS, level(p, s), s.seed, mb, false);
    }

    // ================================================================== memory

    /** Server stopped / a player logged out (null: all): the pending column quotes go. */
    public static void forget(UUID player) {
        synchronized (COLUMNS) {
            if (player == null) {
                COLUMNS.clear();
            } else {
                COLUMNS.remove(player);
            }
        }
    }

    /** Server stopped. */
    public static void clearAll() {
        forget(null);
    }

    // ================================================================== inside

    private static Column column(UUID id) {
        synchronized (COLUMNS) {
            Column c = COLUMNS.get(id);
            if (c == null) {
                c = new Column();
                COLUMNS.put(id, c);
            }
            return c;
        }
    }

    /** A real, connected player on the server (a FakePlayer pays nothing, gets nothing, receives no line). */
    private static boolean real(EntityPlayer p) {
        return p instanceof EntityPlayerMP && !(p instanceof FakePlayer) && p.worldObj != null && !p.worldObj.isRemote
                && ((EntityPlayerMP) p).playerNetServerHandler != null;
    }

    private static int level(EntityPlayer p, SettlementRegistrySC.Settlement s, boolean enemy) {
        SettlementRegistrySC r = SettlementRegistrySC.get(p.worldObj);
        SettlementRegistrySC.PlayerRec pr = r == null ? null : r.playerIfAny(p.getUniqueID());
        return pr == null ? (enemy ? ReputationSC.ENEMY : ReputationSC.STRANGER) : ReputationSC.localLevel(pr.reputation, enemy);
    }

    private static long usedToday(SettlementRegistrySC.LocalRec l, long today, int service) {
        return l == null || l.day != today || service < 0 || service >= EmbassyAirRulesSC.SERVICE_KEYS.length ? 0L : l.count(countKey(service));
    }

    /** The approach charts paid for at this embassy, ever. */
    private static int charts(SettlementRegistrySC.LocalRec l) {
        return l == null ? 0 : l.extra.getCompoundTag(EmbassyAirRulesSC.LEDGER).getInteger(CHARTS);
    }

    /** LocalRec.counts' key of `service`: "air.<key>". */
    private static String countKey(int service) {
        return EmbassyAirRulesSC.COUNT_PREFIX + EmbassyAirRulesSC.SERVICE_KEYS[service];
    }

    private static void tell(EntityPlayer p, EnumChatFormatting colour, String key, Object... args) {
        if (!real(p)) {
            return;
        }
        ChatComponentTranslation m = new ChatComponentTranslation(key, args);
        m.setChatStyle(new ChatStyle().setColor(colour));
        p.addChatComponentMessage(m);
    }
}
