package com.sc.settle.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import com.sc.embassy.EmbassyAirRulesSC;
import com.sc.embassy.EmbassyAirServerSC;
import com.sc.embassy.EmbassyAirfieldSC;
import com.sc.inventory.GuiHoloSC;
import com.sc.inventory.TextFitSC;
import com.sc.manual.Lang;
import com.sc.settle.ReputationSC;
import com.sc.settle.npc.NpcNamesSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.IChatComponent;

/**
 * PACKAGE T (docs/plan-embassy-airfield-core.md §8, rule ЛП-17): the Dispatcher's «Лётное поле» tab inside GuiTradeSC - what TradeClientSC
 * last heard of AIR_STATE / AIR_RESULT for this Dispatcher (EmbassyAirServerSC writes them; only at an embassy WITH a field - without one
 * no state ever comes and the screen has no such tab). Nothing is decided here. Client only.
 * <ul>
 * <li><b>the list</b> (the left screen, scrolled by the wheel): «Лётное поле „Тантал-1“»; the lore - without the add-on «Поле
 * законсервировано, бортов с Пробоя не было» and «Связи с бортами нет со времён Пробоя», with it «Поле открыто для машин зелёного мира»;
 * not admitted (with the add-on) - the air defence's warning and how to be admitted («Знакомый», reached arriving on the ground); the ATIS
 * line and the add-on's rows (IChatComponent JSON, translated here - getFormattedText);</li>
 * <li><b>the card</b> (the right screen): the ICAO code large, «ВПП 36/18» or «Вертодром „Н“», the air defence's admission, the 7700 pass
 * (free / running - counted down here / used today), the «Лётная книжка», the column's armour gases price - each with its tooltip;</li>
 * <li>the «Обслуживание» button (GuiTradeSC's, shown when the state says act) sends AIR_DO {act: service}.</li>
 * </ul>
 * The state is asked for again (AIR_REFRESH, the caller sends it when tick says so) once if none came, every POLL ticks while the tab is
 * shown (the add-on's rows, the ATIS), and when a running pass runs out.
 */
@SideOnly(Side.CLIENT)
final class AirTabSC {

    /** The tab asks for the state this often while it is shown (ticks). */
    static final int POLL = 100;
    /** How long to wait for the first state before asking once. */
    private static final int ASK_AFTER = 20;
    private static final int LINE_H = 9;

    private final int tuner;
    /** A state came for this Dispatcher (there is a field: the tab shows). */
    boolean has;
    /** The hook's button («Обслуживание») is shown. */
    boolean act;
    private int kind, nameCode, lore, level, pass, passTicks, adRadius, adFire, warnSecs, passSecs, admitLevel, bookChips, bookTrust;
    private int gasPrice, gasVolume, gasLeft, gasUnit, gasCap, gasCode, gasLevel;
    private boolean adm, book;
    private String icao = "", rl = "", rh = "", atis;
    private final List<String> rows = new ArrayList<String>();
    private int seenState = -1, seenResult = -1, waited, poll, scroll;
    /** Э8: the list's wrapped lines and their colours for the last state and width (null: wrap again). */
    private List<String> wrapped;
    private List<Integer> wrappedColours;
    private int wrappedWidth;
    /** The last AIR_RESULT's text and its kind (good / refused). */
    String resultText = "";
    boolean resultOk;

    AirTabSC(int tuner) {
        this.tuner = tuner;
    }

    /** The newest AIR_STATE / AIR_RESULT for this Dispatcher, if any came since the last look. @return true: an answer came (resultText) */
    boolean pickUp() {
        int seq = TradeClientSC.airStateSeq();
        if (seq != seenState) {
            seenState = seq;
            NBTTagCompound s = TradeClientSC.airState();
            if (s != null && s.getInteger("tuner") == tuner) {
                parse(s);
            }
        }
        seq = TradeClientSC.airResultSeq();
        if (seq != seenResult) {
            seenResult = seq;
            NBTTagCompound r = TradeClientSC.airResult();
            if (r != null && r.getInteger("tuner") == tuner && answer(r)) {
                return true;
            }
        }
        return false;
    }

    /** One tick of the screen (`shown`: the tab is on it). @return true: ask the server for the state again (AIR_REFRESH) */
    boolean tick(boolean shown) {
        boolean ask = !has && ++waited == ASK_AFTER;        // lost (or none: no field - the server says nothing, asked only once)
        if (passTicks > 0 && --passTicks == 0) {
            ask = true;                                     // the pass ran out: «used today»
        }
        if (has && shown && ++poll >= POLL) {
            ask = true;                                     // the tower's rows and the ATIS move on
        }
        if (ask) {
            poll = 0;
        }
        return ask;
    }

    private void parse(NBTTagCompound s) {
        wrapped = null;                                     // Э8: the list's lines are wrapped again for the new state
        kind = s.getByte("k");
        nameCode = s.getInteger("n");
        icao = s.getString("icao");
        rl = s.getString("rl");
        rh = s.getString("rh");
        lore = s.getByte("lore");
        adm = s.getBoolean("adm");
        level = s.getByte("lvl");
        pass = s.getByte("pass");
        passTicks = pass == EmbassyAirServerSC.PASS_RUNNING ? Math.max(1, s.getInteger("pt")) : 0;
        book = s.getBoolean("book");
        NBTTagCompound g = s.getCompoundTag("gas");
        gasPrice = g.getInteger("p");
        gasVolume = g.hasKey("v") ? g.getInteger("v") : g.getInteger("u");     // ЛП-4: the price is of this volume (the whole left today)
        gasLeft = g.getInteger("l");
        gasUnit = g.getInteger("u");
        gasCap = g.getInteger("d");
        gasCode = g.getByte("c");
        gasLevel = g.hasKey("m") ? g.getByte("m") : ReputationSC.ACQUAINTANCE;
        adRadius = s.getInteger("ad");
        adFire = s.hasKey("af") ? s.getInteger("af") : adRadius;          // ЛП-1: the square the guns fire over
        warnSecs = s.getInteger("ws");
        passSecs = s.getInteger("ps");
        admitLevel = s.hasKey("al") ? s.getByte("al") : ReputationSC.ACQUAINTANCE;
        bookChips = s.getInteger("bc");
        bookTrust = s.getInteger("bt");
        rows.clear();
        NBTTagList l = s.getTagList("rows", 8);
        for (int i = 0; i < l.tagCount(); i++) {
            String t = formatted(l.getStringTagAt(i));
            if (t != null) {
                rows.add(t);
            }
        }
        atis = s.hasKey("atis") ? formatted(s.getString("atis")) : null;
        act = s.getBoolean("act");
        has = true;
    }

    /** An AIR_RESULT's text (args: strings). @return false: nothing to say */
    private boolean answer(NBTTagCompound r) {
        String key = r.getString("key");
        if (key.isEmpty()) {
            return false;
        }
        NBTTagList a = r.getTagList("args", 8);
        Object[] args = new Object[a.tagCount()];
        for (int i = 0; i < args.length; i++) {
            args[i] = a.getStringTagAt(i);
        }
        resultText = args.length == 0 ? Lang.tr(key) : Lang.tr(key, args);
        resultOk = r.getBoolean("ok");
        return true;
    }

    /** The hook's JSON as this client's text (null: broken, empty). */
    private static String formatted(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            IChatComponent c = IChatComponent.Serializer.func_150699_a(json);
            String t = c == null ? null : c.getFormattedText();
            return t == null || t.isEmpty() ? null : t;
        } catch (RuntimeException e) {
            return null;                                    // a line this client can't read: left out
        }
    }

    void scroll(int wheel) {
        scroll = Math.max(0, scroll + (wheel > 0 ? -1 : 1));    // drawList clamps it to the lines there are
    }

    // ================================================================== drawing (foreground: relative to the screen's corner ox, oy)

    /** One line of the list: its text and colour. */
    private static final class Entry {
        final String text;
        final int colour;

        Entry(String text, int colour) {
            this.text = text;
            this.colour = colour;
        }
    }

    private List<Entry> entries() {
        List<Entry> l = new ArrayList<Entry>();
        l.add(new Entry(Lang.tr("sc.airfield.tab.title", NpcNamesSC.name(nameCode).getUnformattedText()), GuiHoloSC.VALUE));
        if (lore == 0) {
            l.add(new Entry(Lang.tr("sc.airfield.tab.lore.closed"), GuiHoloSC.LABEL));
            l.add(new Entry(Lang.tr("sc.airfield.tab.lore.noLink"), GuiHoloSC.IDLE));
        } else {
            l.add(new Entry(Lang.tr("sc.airfield.tab.lore.open"), GuiHoloSC.OK));
            if (!adm) {                                     // the add-on's guns are real: say it before anything else
                l.add(new Entry(Lang.tr("sc.airfield.tab.ad.no.tip", adRadius, warnSecs, adFire), GuiHoloSC.BAD));
                l.add(new Entry(Lang.tr("sc.airfield.tab.ad.how", levelName(admitLevel)), GuiHoloSC.WARN));
            }
        }
        if (atis != null) {
            l.add(new Entry(Lang.tr("sc.airfield.tab.atis", atis), GuiHoloSC.LABEL));
        }
        for (String r : rows) {
            l.add(new Entry(r, GuiHoloSC.VALUE));
        }
        return l;
    }

    /** The left screen at (x, y) of w x h: the lines wrapped, scrolled; a scrollbar when they don't fit. */
    @SuppressWarnings("unchecked")
    void drawList(FontRenderer f, int x, int y, int w, int h, int ox, int oy) {
        int tx = x + 6, tw = w - 16, top = y + 4, fit = Math.max(1, (h - 8) / LINE_H);
        if (wrapped == null || wrappedWidth != tw) {        // Э8: built and wrapped once per state, not every frame
            wrapped = new ArrayList<String>();
            wrappedColours = new ArrayList<Integer>();
            wrappedWidth = tw;
            for (Entry e : entries()) {
                for (String s : (List<String>) f.listFormattedStringToWidth(e.text, tw)) {
                    wrapped.add(s);
                    wrappedColours.add(Integer.valueOf(e.colour));
                }
            }
        }
        List<String> lines = wrapped;
        List<Integer> colours = wrappedColours;
        scroll = Math.max(0, Math.min(scroll, lines.size() - fit));
        for (int i = 0; i < fit && scroll + i < lines.size(); i++) {
            f.drawString(lines.get(scroll + i), tx, top + i * LINE_H, colours.get(scroll + i).intValue());
        }
        if (lines.size() > fit) {
            int bx = x + w - 6, by = y + 3, bh = h - 6;
            Gui.drawRect(bx, by, bx + 4, by + bh, 0xFF0A1420);
            int knob = Math.max(8, bh * fit / lines.size());
            int ky = by + (bh - knob) * scroll / Math.max(1, lines.size() - fit);
            Gui.drawRect(bx, ky, bx + 4, ky + knob, GuiHoloSC.CYAN_MID);
            Gui.drawRect(bx + 1, ky + 1, bx + 3, ky + knob - 1, GuiHoloSC.CYAN);
        }
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** The right screen from (x, y), w wide, down to `bottom`: the ICAO large and the player's lines, each with its tooltip. */
    void drawCard(FontRenderer f, int x, int y, int w, int bottom, int ox, int oy) {
        if (!icao.isEmpty()) {
            int iw = f.getStringWidth(icao) * 2;
            GL11.glPushMatrix();
            GL11.glTranslatef(x + Math.max(0, (w - iw) / 2), y, 0F);
            GL11.glScalef(2F, 2F, 1F);
            f.drawString(icao, 0, 0, GuiHoloSC.VALUE);
            GL11.glPopMatrix();
            TextFitSC.hover(ox + x, oy + y, w, 16, Lang.tr("sc.airfield.tab.icao.tip", icao));
            y += 19;
        }
        boolean heli = kind == EmbassyAirfieldSC.HELI || rl.isEmpty();
        List<String> tip = new ArrayList<String>();
        tip.add(Lang.tr("sc.airfield.kind." + EmbassyAirfieldSC.KIND_KEYS[Math.max(0, Math.min(EmbassyAirfieldSC.KIND_KEYS.length - 1, kind))]));
        if (!heli) {
            tip.add(Lang.tr("sc.airfield.tab.heli.tip"));
        }
        y = line(f, heli ? Lang.tr("sc.airfield.tab.heli") : Lang.tr("sc.airfield.tab.runway", rl, rh), GuiHoloSC.LABEL, tip, x, y, w, bottom, ox, oy);
        // ЛП-13: the air defence's admission and what admits
        tip = new ArrayList<String>();
        if (lore == 0) {
            tip.add(Lang.tr("sc.airfield.tab.lore.noLink"));    // no add-on's hook: the emplacements are silent
        }
        if (adm) {
            tip.add(Lang.tr("sc.airfield.tab.ad.yes.tip", levelName(admitLevel)));
        } else {
            tip.add(Lang.tr("sc.airfield.tab.ad.no.tip", adRadius, warnSecs, adFire));
            tip.add(Lang.tr("sc.airfield.tab.ad.how", levelName(admitLevel)));
            tip.add(Lang.tr("sc.airfield.tab.ad.cover"));
            tip.add(Lang.tr("sc.airfield.tab.ad.never"));
        }
        y = line(f, Lang.tr(adm ? "sc.airfield.tab.ad.yes" : "sc.airfield.tab.ad.no"), adm ? GuiHoloSC.OK : lore == 0 ? GuiHoloSC.IDLE : GuiHoloSC.BAD,
                tip, x, y, w, bottom, ox, oy);
        // ЛП-15: the 7700 pass (counted down here; the server's state corrects it)
        tip = new ArrayList<String>();
        tip.add(Lang.tr("sc.airfield.tab.pass.tip", passSecs));
        if (adm) {
            tip.add(Lang.tr("sc.airfield.tab.pass.admitted"));
        }
        String p;
        int pc;
        if (pass == EmbassyAirServerSC.PASS_RUNNING) {
            p = Lang.tr("sc.airfield.tab.pass.running", (passTicks + 19) / 20);
            pc = GuiHoloSC.OK;
        } else if (pass == EmbassyAirServerSC.PASS_USED) {
            p = Lang.tr("sc.airfield.tab.pass.used");
            pc = GuiHoloSC.IDLE;
        } else {
            p = Lang.tr("sc.airfield.tab.pass.free");
            pc = adm ? GuiHoloSC.IDLE : GuiHoloSC.LABEL;
        }
        y = line(f, p, pc, tip, x, y, w, bottom, ox, oy);
        // ЛП-16: the «Лётная книжка»
        tip = new ArrayList<String>();
        tip.add(book ? Lang.tr("sc.airfield.tab.book.yes.tip") : Lang.tr("sc.airfield.tab.book.no.tip", bookChips, bookTrust));
        y = line(f, book ? Lang.tr("sc.airfield.tab.book.yes") : Lang.tr("sc.airfield.tab.book.no", bookChips), book ? GuiHoloSC.OK : GuiHoloSC.LABEL,
                tip, x, y, w, bottom, ox, oy);
        // §7.2: the column's armour gases for this player
        tip = new ArrayList<String>();
        // ЛП-4 (the audit 2026-11-04): the price of the whole volume left today - the column rounds once over the whole fill
        tip.add(Lang.tr("sc.airfield.tab.gas.tip", gasVolume, gasPrice, gasUnit));
        boolean open = gasCode == EmbassyAirRulesSC.PAY_OK;
        if (gasCode == EmbassyAirRulesSC.PAY_LIMIT) {
            tip.add(Lang.tr("sc.airfield.tab.gas.limit", gasCap));
        } else if (gasCode == EmbassyAirRulesSC.PAY_ENEMY) {
            tip.add(Lang.tr("sc.airfield.tab.gas.enemy"));      // its «Враг»: not a matter of the level
        } else if (!open) {
            tip.add(Lang.tr("sc.airfield.tab.gas.level", levelName(gasLevel)));
        }
        line(f, open ? Lang.tr("sc.airfield.tab.gas", gasVolume, gasPrice) : Lang.tr("sc.airfield.tab.gas.closed"), open ? GuiHoloSC.VALUE : GuiHoloSC.IDLE,
                tip, x, y, w, bottom, ox, oy);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** One card line if there is room, its tooltip over it. @return the next y */
    private static int line(FontRenderer f, String text, int colour, List<String> tip, int x, int y, int w, int bottom, int ox, int oy) {
        if (y + 8 > bottom) {
            return y;
        }
        TextFitSC.draw(f, text, x, y, w, colour, ox, oy);
        if (f.getStringWidth(text) * TextFitSC.MIN_SCALE > w) {
            tip.add(0, text);                                   // cut: the tooltip says it whole first
        }
        TextFitSC.hover(ox + x, oy + y, w, 9, tip);
        return y + 9;
    }

    private static String levelName(int level) {
        return Lang.tr(ReputationSC.levelKey(level));
    }
}
