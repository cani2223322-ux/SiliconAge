package com.sc.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;

import com.sc.bridge.BridgeFarSC;
import com.sc.bridge.BridgeMathSC;
import com.sc.bridge.BridgeMsgSC;
import com.sc.manual.Lang;
import com.sc.util.ArmorFeature;
import com.sc.util.SingularLevel;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * The K menu's «Мост» tab (docs/ground-bridge/bridge_5_ktab.png, bridge_9_ktab_coords.png): the armour's tab row
 * on top (another tab goes back to GuiArmorSC), the linked bridges (up to 3, the Armour Link Module in the
 * Singular helmet) with their state, the modes ДР1-ДР5, «Закладки · Свои координаты · История · Находки сканера»,
 * «Моё» / «Взгляд» (up to 256 blocks), «Проверить место» (krypton from the helmet), the point and your end
 * (from the armour -25%, precise), «Открыть» / «Закрыть»; right the cost need / have, the hold, the lifetime and
 * the bridge's state. 470 x 250, scaled down on a small screen.
 */
public class GuiArmorBridgeSC extends GuiBridgeFarSC {

    private static final int B_LINK = 60, B_UNLINK = 64, B_ARMTAB = 100;
    private static int lastMode = BridgeMathSC.MODE_HOME, lastTab = T_BM;
    private int selLink;
    private List<Integer> tabs = new ArrayList<Integer>();
    private long unlinkArmed;

    public GuiArmorBridgeSC() {
        super(470, 250);
        mode = lastMode;
        tab = lastTab;
    }

    @Override
    protected int src() {
        return BridgeFarSC.SRC_ARMOUR;
    }

    @Override
    protected int slot() {
        return selLink;
    }

    @Override
    public void setState(NBTTagCompound s) {
        if (s != null && s.getInteger("src") == src() && st == null) {
            selLink = s.getInteger("sel");
        }
        super.setState(s);
        if (st != null && st.hasKey("sel") && st.getInteger("sel") != selLink && st.getTagList("links", 10).tagCount() <= selLink) {
            selLink = st.getInteger("sel");
        }
    }

    @Override
    public void initGui() {
        scale();
        Keyboard.enableRepeatEvents(true);
        String ox = text(fx), oy = text(fy), oz = text(fz), of = text(ffriend);
        fx = field(14, 114, 50, ox, 9);
        fy = field(78, 114, 34, oy, 4);
        fz = field(126, 114, 50, oz, 9);
        ffriend = field(10, 197, 120, of, 16);
        btns.clear();
        tabs = GuiArmorSC.tabsFor(mc);
        int n = Math.max(1, tabs.size());
        int tw = Math.max(24, Math.min(80, (W - 8) / n - 2)), tx = W / 2 - n * (tw + 2) / 2 + 1;
        for (int t : tabs) {
            Btn b = add(B_ARMTAB + t, tx, 15, tw, 14, "");
            b.label = GuiArmorSC.tabLabel(t);
            tx += tw + 2;
        }
        for (int i = 0; i < BridgeMathSC.MAX_LINKS; i++) {
            add(B_LINK + i, 6, 42 + i * 13, 150, 12, "");
            add(B_UNLINK + i, 158, 42 + i * 13, 12, 12, "").tip = Lang.tr("sc.bridge.armour.unlink.hint");
        }
        for (int i = 1; i < BridgeMathSC.MODES; i++) {
            add(B_MODE + i, 6 + (i - 1) * 58, 84, 56, 12, "sc.bridge.gui.mode." + i);
        }
        String[] names = {"sc.bridge.far.tab.bm", "sc.bridge.far.tab.coords", "sc.bridge.far.tab.hist", "sc.bridge.far.tab.finds"};
        for (int i = 0; i < 4; i++) {
            add(B_TAB + i, 6 + i * 73, 99, 71, 11, names[i]);
        }
        add(B_ME, 182, 114, 36, 11, "sc.bridge.far.me").tip = Lang.tr("sc.bridge.far.me.hint");
        add(B_LOOK, 222, 114, 74, 11, "sc.bridge.far.look.btn").tip = Lang.tr("sc.bridge.far.look.hint", BridgeMathSC.LOOK_RANGE);
        add(B_DIM, 150, 127, 146, 11, "");
        add(B_PROBE, 6, 140, 290, 11, "").tip = Lang.tr("sc.bridge.armour.probe.hint", BridgeFarSC.ARMOUR_PROBE_KR);
        add(B_TAKE, 238, 165, 54, 10, "sc.bridge.gui.take");
        add(B_TO_BM, 6, 180, 143, 10, "sc.bridge.far.tobm").tip = Lang.tr("sc.bridge.far.tobm.hint");
        add(B_TO_COORD, 153, 180, 143, 10, "sc.bridge.far.tocoord").tip = Lang.tr("sc.bridge.far.tocoord.hint");
        add(B_TOME, 134, 196, 70, 11, "").tip = Lang.tr("sc.bridge.far.tome.hint");
        add(B_OPEN, 6, 214, 200, 14, "sc.bridge.gui.open").color = OK;
        add(B_CLOSE, 210, 214, 86, 14, "sc.bridge.gui.close");
        request();
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        lastMode = mode;
        lastTab = tab;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (!SingularLevel.isSingular(mc.thePlayer.getCurrentArmor(3))) {
            mc.displayGuiScreen(new GuiArmorSC());          // the helmet came off
        }
    }

    private NBTTagList links() {
        return st == null ? new NBTTagList() : st.getTagList("links", 10);
    }

    private String block() {
        return st == null ? "" : st.getString("block");
    }

    @Override
    protected void press(int id, int button) {
        if (id >= B_ARMTAB) {
            if (id - B_ARMTAB != GuiArmorSC.BRIDGE_TAB) {
                mc.displayGuiScreen(new GuiArmorSC(id - B_ARMTAB));
            }
            return;
        }
        if (id >= B_LINK && id < B_LINK + BridgeMathSC.MAX_LINKS) {
            selLink = id - B_LINK;
            send(BridgeFarSC.F_SELECT, orderValues(), orderText());
            place = null;
            return;
        }
        if (id >= B_UNLINK && id < B_UNLINK + BridgeMathSC.MAX_LINKS) {
            long now = System.currentTimeMillis();
            if (now - unlinkArmed > 2000) {
                unlinkArmed = now;                                // a second click within 2 s
                return;
            }
            unlinkArmed = 0;
            selLink = id - B_UNLINK;
            send(BridgeFarSC.F_UNLINK, orderValues(), orderText());
            return;
        }
        super.press(id, button);
    }

    @Override
    protected void refresh() {
        boolean coords = tab == T_COORDS, friend = mode == BridgeMathSC.MODE_FRIEND;
        NBTTagCompound b = bridge();
        boolean open = b != null && b.getBoolean("open");
        boolean usable = st != null && (block().length() == 0);
        fx.setVisible(coords);
        fy.setVisible(coords);
        fz.setVisible(coords);
        ffriend.setVisible(friend);
        NBTTagList ls = links();
        for (Btn x : btns) {
            x.selected = false;
            x.enabled = true;
            x.visible = true;
            if (x.id >= B_ARMTAB) {
                x.selected = x.id - B_ARMTAB == GuiArmorSC.BRIDGE_TAB;
                x.color = x.selected ? OK : TEXT;
            } else if (x.id >= B_LINK && x.id < B_LINK + BridgeMathSC.MAX_LINKS) {
                int i = x.id - B_LINK;
                if (i < ls.tagCount()) {
                    NBTTagCompound e = ls.getCompoundTagAt(i);
                    String n = e.getString("n").length() == 0 ? Lang.tr("sc.bridge.res.noname") : e.getString("n");
                    x.label = n + " — " + Lang.tr(e.getInteger("k") == BridgeMathSC.SPACE ? "sc.bridge.far.kind.space" : "sc.bridge.far.kind.ground");
                    x.selected = i == selLink;
                    x.color = x.selected ? OK : TEXT;
                } else {
                    x.label = Lang.tr("sc.bridge.armour.empty");
                    x.enabled = false;
                }
            } else if (x.id >= B_UNLINK && x.id < B_UNLINK + BridgeMathSC.MAX_LINKS) {
                x.visible = x.id - B_UNLINK < ls.tagCount();
                x.label = "✕";
                x.selected = System.currentTimeMillis() - unlinkArmed < 2000;
                x.color = BAD;
            } else if (x.id >= B_MODE && x.id < B_MODE + BridgeMathSC.MODES) {
                x.selected = x.id - B_MODE == mode;
                x.color = x.selected ? OK : TEXT;
            } else if (x.id >= B_TAB && x.id < B_TAB + 4) {
                x.selected = x.id - B_TAB == tab;
                x.color = x.selected ? OK : TEXT;
            } else if (x.id == B_ME || x.id == B_LOOK || x.id == B_PROBE || x.id == B_TO_BM || x.id == B_TO_COORD) {
                x.visible = coords;
                x.enabled = x.id == B_ME || x.id == B_LOOK || point() != null;
                if (x.id == B_PROBE) {
                    x.label = Lang.tr("sc.bridge.armour.probe", BridgeFarSC.ARMOUR_PROBE_KR);
                    x.color = WARN;
                    x.enabled &= usable;
                }
            } else if (x.id == B_DIM) {
                x.visible = coords && space();
                x.label = dimName(dim) + " ▼";
            } else if (x.id == B_TAKE) {
                x.visible = coords && place != null && place.getIntArray("near").length == 4 && !place.getBoolean("free");
            } else if (x.id == B_TOME) {
                x.visible = friend;
                x.label = Lang.tr(toMe ? "sc.bridge.far.tome.on" : "sc.bridge.far.tome.off");
            } else if (x.id == B_OPEN) {
                Object[] pl = planLine();
                x.enabled = usable && b != null && !open && (!BridgeMathSC.needsPoint(mode) || point() != null);
                x.color = (Integer) pl[1] == BAD ? DIM : OK;
            } else if (x.id == B_CLOSE) {
                x.enabled = usable && open;
            }
        }
    }

    @Override
    protected void clickedElsewhere(int x, int y, int button) {
        if (tab != T_COORDS) {
            clickList(tab, 6, 114, 290, 7, x, y);
        }
    }

    @Override
    protected void drawContent() {
        rect(0, 0, W, H, 0xC0101418);
        rect(1, 1, W - 2, H - 2, 0xFF46505A);
        rect(2, 2, W - 4, H - 4, 0xFF1A1F26);
        centered(Lang.tr("sc.armorgui.title"), 0, 4, W, 0xFFFFFF);
        String bl = block();
        NBTTagList ls = links();
        text(Lang.tr("sc.bridge.armour.links"), 6, 33, LABEL);
        if (st != null && ("sc.bridge.armour.nomodule".equals(bl) || "sc.bridge.armour.nohelmet".equals(bl))) {
            frame(176, 42, 120, 38, PANEL, 0xFFC84A40);
            List<?> l = fontRendererObj.listFormattedStringToWidth(Lang.tr(bl), (int) (112 / 0.75F));
            for (int i = 0; i < l.size() && i < 4; i++) {
                small((String) l.get(i), 180, 45 + i * 8, 112, BAD);
            }
        }
        for (int i = 0; i < ls.tagCount() && i < BridgeMathSC.MAX_LINKS; i++) {
            NBTTagCompound e = ls.getCompoundTagAt(i);
            int reach = e.getInteger("reach");
            String s;
            int c;
            if (reach != BridgeFarSC.R_OK) {
                s = Lang.tr("sc.bridge.far.short." + (reach == BridgeFarSC.R_LOST ? "lost" : reach == BridgeFarSC.R_ACCESS ? "access" : "unreachable"));
                c = BAD;
            } else if (e.getBoolean("open")) {
                s = Lang.tr("sc.bridge.far.st.open");
                c = BLUE;
            } else if (e.getBoolean("ready")) {
                long d = e.getLong("dist");
                s = Lang.tr("sc.bridge.far.st.ready") + (d >= 0 ? " · " + Lang.tr("sc.bridge.far.blocks", g(d)) : " · " + Lang.tr("sc.bridge.far.otherdim"));
                c = OK;
            } else if (e.getInteger("cool") > 0) {
                s = Lang.tr("sc.bridge.gui.cool.left", (e.getInteger("cool") + 19) / 20);
                c = WARN;
            } else {
                long max = Math.max(1, e.getLong("capMax"));
                s = Lang.tr("sc.bridge.armour.charging", e.getLong("cap") * 100 / max);
                c = LABEL;
            }
            small(s, 174, 45 + i * 13, 128, c);
        }
        if (st != null && "sc.bridge.armour.level".equals(bl)) {
            small(Lang.tr("sc.bridge.armour.level", SingularLevel.requiredLevel(ArmorFeature.BRIDGE_LINK)), 6, 76, 296, WARN);
        } else if (st != null && "sc.bridge.armour.off".equals(bl)) {
            small(Lang.tr("sc.bridge.armour.off"), 6, 76, 296, WARN);
        }
        // the tab
        if (tab == T_COORDS) {
            text("X", 6, 116, LABEL);
            text("Y", 70, 116, LABEL);
            text("Z", 118, 116, LABEL);
            if (space()) {
                small(Lang.tr("sc.bridge.gui.dim"), 6, 129, 140, LABEL);
            } else {
                small(Lang.tr("sc.bridge.gui.yauto"), 6, 129, 290, DIM);
            }
            drawPlace(6, 153, 290, 24);
        } else {
            if (tab == T_FINDS) {
                int lv = st == null ? 0 : st.getInteger("level");
                smallRight(Lang.tr(lv >= SingularLevel.BRIDGE_FINDS_LEVEL ? "sc.bridge.armour.finds.precise" : "sc.bridge.armour.finds.scatter",
                        SingularLevel.BRIDGE_FINDS_LEVEL), 296, 178, lv >= SingularLevel.BRIDGE_FINDS_LEVEL ? OK : DIM);
            }
            drawList(tab, 6, 114, 290, 7);
        }
        // the point and your end
        if (mode == BridgeMathSC.MODE_FRIEND) {
            small(whereLine(), 6, 190, 296, TEXT);
            if (text(ffriend).length() == 0 && !ffriend.isFocused()) {
                small(Lang.tr("sc.bridge.far.friendname"), 13, 199, 110, DIM);
            }
        } else {
            fit(whereLine(), 6, 191, 296, TEXT);
            Object[] pl = planLine();
            small((String) pl[0] + ((Integer) pl[1] == OK ? "  " + Lang.tr("sc.bridge.armour.precise", BridgeMathSC.ARMOUR_DISCOUNT) : ""), 6, 203, 296,
                    (Integer) pl[1]);
        }
        small(Lang.tr("sc.bridge.armour.keys", keyName(ArmorClientSC.KEY_BRIDGE_HOME), keyName(ArmorClientSC.KEY_BRIDGE_LAST),
                keyName(ArmorClientSC.KEY_BRIDGE_LAST)), 6, 232, 296, DIM);
        if (lastMsg != null && System.currentTimeMillis() - lastMsgAt < 15000) {
            small(lastMsg.text(), 6, 241, 460, WARN);
        } else {
            small(Lang.tr("sc.bridge.armour.levelhint", SingularLevel.requiredLevel(ArmorFeature.BRIDGE_LINK), st == null ? 0 : st.getInteger("level"),
                    SingularLevel.BRIDGE_FINDS_LEVEL), 6, 241, 460, DIM);
        }
        drawRight();
    }

    private static String keyName(net.minecraft.client.settings.KeyBinding k) {
        return k == null || k.getKeyCode() == 0 ? "-" : Keyboard.getKeyName(k.getKeyCode());
    }

    private void drawRight() {
        int x = 304, w = 160;
        frame(x - 4, 33, w + 8, 205, 0xFF111518, 0xFF3C5A48);
        NBTTagCompound b = bridge();
        int proj = b == null ? BridgeMathSC.projections(mode, toMe) : b.getInteger("proj");
        text(Lang.tr("sc.bridge.far.cost", proj), x, 37, TEXT);
        int y = drawCost(x, 48, w, 5);
        if (b != null && b.hasKey("cost")) {
            int[] c = ints(b, "cost", 11);
            small(Lang.tr(space() ? "sc.bridge.gui.hold.space" : "sc.bridge.gui.hold.ground", g(c[4]), c[5], c[6], c[7], c[8] / 20), x, y + 2, w, DIM);
            String fam = famLine();
            if (b.getInteger("pct") > 0) {
                small(Lang.tr("sc.bridge.armour.discount", b.getInteger("pct")) + (fam.length() > 0 ? " · " + fam : ""), x, y + 11, w, OK);
            } else if (fam.length() > 0) {
                small(fam, x, y + 11, w, famColor());
            }
        }
        String n = b == null ? "" : b.getString("name").length() == 0 ? Lang.tr("sc.bridge.res.noname") : b.getString("name");
        text(Lang.tr("sc.bridge.armour.state", n), x, 104, TEXT);
        if (b == null) {
            Object[] pl = planLine();
            List<?> l = fontRendererObj.listFormattedStringToWidth((String) pl[0], (int) (w / 0.75F));
            for (int i = 0; i < l.size() && i < 4; i++) {
                small((String) l.get(i), x, 116 + i * 8, w, (Integer) pl[1]);
            }
            return;
        }
        int[] t = ints(b, "time", 6);
        row(x, 116, w, Lang.tr("sc.bridge.armour.capsrow"), eu(b.getLong("capEu")) + " / " + eu(b.getLong("capMax")), TEXT);
        int[] tk = ints(b, "tanks", BridgeMathSC.GASES.length);
        row(x, 125, w, Lang.tr("sc.bridge.res.gas.singular_matter"), g(tk[BridgeMathSC.SM]) + " " + Lang.tr("sc.bridge.unit.mb"), TEXT);
        row(x, 134, w, Lang.tr("sc.bridge.res.gas.helium"), g(tk[BridgeMathSC.HE]) + " " + Lang.tr("sc.bridge.unit.mb"), TEXT);
        row(x, 143, w, Lang.tr("sc.bridge.res.gas.krypton"), g(tk[BridgeMathSC.KR]) + " " + Lang.tr("sc.bridge.unit.mb"), TEXT);
        String ring = b.getBoolean("open") ? Lang.tr("sc.bridge.gui.cool.open") : t[2] > 0 ? Lang.tr("sc.bridge.gui.cool.left", (t[2] + 19) / 20)
                : Lang.tr("sc.bridge.far.ringready");
        row(x, 152, w, Lang.tr("sc.bridge.gui.ring"), ring + " · " + Lang.tr("sc.bridge.armour.wear", b.getInteger("wear")), t[2] > 0 ? WARN : OK);
        int stab = b.getBoolean("open") ? t[4] : b.getInteger("baseStab");
        row(x, 161, w, Lang.tr("sc.bridge.gui.stability"), stab + "%", stab >= 70 ? OK : stab >= 30 ? WARN : BAD);
        NBTTagList bm = bookmarks();
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < bm.tagCount() && i < 6; i++) {
            names.append(i > 0 ? ", " : "").append(bm.getCompoundTagAt(i).getString("n"));
        }
        List<?> l = fontRendererObj.listFormattedStringToWidth(Lang.tr("sc.bridge.armour.bms", bm.tagCount(), names.toString()), (int) (w / 0.75F));
        for (int i = 0; i < l.size() && i < 3; i++) {
            small((String) l.get(i), x, 174 + i * 8, w, LABEL);
        }
        small(Lang.tr("sc.bridge.armour.coords", st.getInteger("coords")), x, 202, w, DIM);
        small(Lang.tr("sc.bridge.armour.kr", g(st.getInteger("kr"))), x, 211, w, DIM);
        if (b.getBoolean("remoteMode")) {
            small(Lang.tr("sc.bridge.armour.remotemode"), x, 220, w, BLUE);
        }
    }

    private void row(int x, int y, int w, String name, String value, int color) {
        small(name, x, y, w / 2, LABEL);
        smallRight(value, x + w, y, color);
    }

    @Override
    protected List<String> tip() {
        List<String> t = super.tip();
        if (t != null) {
            return t;
        }
        for (Btn b : btns) {
            if (b.visible && b.over(vmx, vmy) && b.id >= B_LINK && b.id < B_LINK + BridgeMathSC.MAX_LINKS) {
                t = new ArrayList<String>();
                t.add(Lang.tr("sc.bridge.armour.link.hint"));
                return t;
            }
        }
        if (over(300, 33, 168, 60)) {
            t = new ArrayList<String>();
            t.add(Lang.tr("sc.bridge.far.cost.armour", BridgeMathSC.ARMOUR_DISCOUNT));
            return t;
        }
        if (over(6, 228, 296, 20)) {
            t = new ArrayList<String>();
            t.add(Lang.tr("sc.bridge.armour.keys.hint"));
            return t;
        }
        return null;
    }

}
