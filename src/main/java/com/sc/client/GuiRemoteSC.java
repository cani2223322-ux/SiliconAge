package com.sc.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.sc.bridge.BridgeFarSC;
import com.sc.bridge.BridgeMathSC;
import com.sc.bridge.BridgeMsgSC;
import com.sc.init.ModItems;
import com.sc.item.ItemBridgeRemoteSC;
import com.sc.manual.Lang;

import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * The Bridge / Space Remote's screen (docs/ground-bridge/bridge_4_remote.png, bridge_7 / 8): 250 x 264 (smaller
 * screens - scaled): the charge, the bridge's line, the modes ДР1-ДР5, the tabs «Закладки · Свои координаты ·
 * История», «Куда» with the plan's note and the coordinator slot (the Space remote: also the key - a Singular Matter
 * cell), the cost with the remote's signal, «Открыть» / «Закрыть», the bridge's state and the last answer.
 */
public class GuiRemoteSC extends GuiBridgeFarSC {

    private static final int B_COORD = 50, B_KEY = 51;
    private static final RenderItem ITEMS = new RenderItem();
    private static int lastMode = BridgeMathSC.MODE_HOME, lastTab = T_BM;
    private boolean coordSeen;
    private String seenCoord = "";

    public GuiRemoteSC() {
        super(250, 264);
        mode = lastMode;
        tab = lastTab;
    }

    @Override
    protected int src() {
        return BridgeFarSC.SRC_REMOTE;
    }

    @Override
    public void initGui() {
        scale();
        Keyboard.enableRepeatEvents(true);
        String ox = text(fx), oy = text(fy), oz = text(fz), of = text(ffriend);
        fx = field(14, 58, 44, ox, 9);
        fy = field(72, 58, 34, oy, 4);
        fz = field(120, 58, 44, oz, 9);
        ffriend = field(10, 163, 110, of, 16);
        btns.clear();
        for (int i = 1; i < BridgeMathSC.MODES; i++) {
            add(B_MODE + i, 6 + (i - 1) * 48, 28, 46, 12, "sc.bridge.gui.mode." + i);
        }
        String[] tabs = {"sc.bridge.far.tab.bm", "sc.bridge.far.tab.coords", "sc.bridge.far.tab.hist"};
        for (int i = 0; i < 3; i++) {
            add(B_TAB + i, 6 + i * 80, 43, 78, 11, tabs[i]);
        }
        add(B_ME, 170, 58, 36, 11, "sc.bridge.far.me").tip = Lang.tr("sc.bridge.far.me.hint");
        add(B_DIM, 100, 72, 144, 11, "");
        add(B_PROBE, 6, 85, 238, 11, "").tip = Lang.tr("sc.bridge.gui.probe.hint", BridgeMathSC.PROBE_KR);
        add(B_TAKE, 186, 110, 54, 10, "sc.bridge.gui.take");
        add(B_TO_BM, 6, 125, 117, 10, "sc.bridge.far.tobm").tip = Lang.tr("sc.bridge.far.tobm.hint");
        add(B_TO_COORD, 127, 125, 117, 10, "sc.bridge.far.tocoord").tip = Lang.tr("sc.bridge.far.tocoord.hint");
        add(B_TOME, 124, 162, 64, 11, "").tip = Lang.tr("sc.bridge.far.tome.hint");
        add(B_OPEN, 6, 222, 182, 14, "sc.bridge.gui.open").color = OK;
        add(B_CLOSE, 192, 222, 52, 14, "sc.bridge.gui.close");
        request();
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        lastMode = mode;
        lastTab = tab;
    }

    private NBTTagCompound item() {
        return st != null && st.hasKey("item") ? st.getCompoundTag("item") : new NBTTagCompound();
    }

    private boolean isSpace() {
        ItemStack h = mc.thePlayer.getHeldItem();
        return ItemBridgeRemoteSC.isSpace(h) || item().getBoolean("space");
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (!ItemBridgeRemoteSC.isRemote(mc.thePlayer.getHeldItem())) {
            mc.displayGuiScreen(null);                      // the remote left the hand
            return;
        }
        NBTTagCompound c = item().getCompoundTag("coord");
        String key = c.hasKey("p") ? java.util.Arrays.toString(c.getIntArray("p")) + c.getString("n") : "";
        if (coordSeen && !key.equals(seenCoord) && c.hasKey("p")) {
            setPoint(c.getIntArray("p"), c.getString("n"));  // a coordinator just put in: its point becomes the target
            if (!BridgeMathSC.needsPoint(mode)) {
                mode = BridgeMathSC.MODE_FROM_ME;
            }
        }
        seenCoord = key;
        coordSeen = st != null;
    }

    @Override
    protected void refresh() {
        boolean coords = tab == T_COORDS, friend = mode == BridgeMathSC.MODE_FRIEND;
        boolean open = bridge() != null && bridge().getBoolean("open");
        fx.setVisible(coords);
        fy.setVisible(coords);
        fz.setVisible(coords);
        ffriend.setVisible(friend);
        for (Btn b : btns) {
            b.selected = false;
            b.enabled = true;
            if (b.id >= B_MODE && b.id < B_MODE + BridgeMathSC.MODES) {
                b.selected = b.id - B_MODE == mode;
                b.color = b.selected ? OK : TEXT;
            } else if (b.id >= B_TAB && b.id < B_TAB + 4) {
                b.selected = b.id - B_TAB == tab;
                b.color = b.selected ? OK : TEXT;
            } else if (b.id == B_ME || b.id == B_PROBE || b.id == B_TO_BM || b.id == B_TO_COORD) {
                b.visible = coords;
                b.enabled = b.id == B_ME || point() != null;
                if (b.id == B_PROBE) {
                    b.label = Lang.tr("sc.bridge.far.probe", BridgeMathSC.PROBE_KR);
                    b.color = WARN;
                }
            } else if (b.id == B_DIM) {
                b.visible = coords && space();
                b.label = dimName(dim) + " ▼";
            } else if (b.id == B_TAKE) {
                b.visible = coords && place != null && place.getIntArray("near").length == 4 && !place.getBoolean("free");
            } else if (b.id == B_TOME) {
                b.visible = friend;
                b.label = Lang.tr(toMe ? "sc.bridge.far.tome.on" : "sc.bridge.far.tome.off");
            } else if (b.id == B_OPEN) {
                Object[] pl = planLine();
                b.enabled = bridge() != null && !open && (BridgeMathSC.needsPoint(mode) ? point() != null : true);
                b.label = Lang.tr("sc.bridge.gui.open") + (b.enabled && (Integer) pl[1] == BAD ? " (" + Lang.tr("sc.bridge.far.cant") + ")" : "");
                b.color = (Integer) pl[1] == BAD ? DIM : OK;
            } else if (b.id == B_CLOSE) {
                b.enabled = open;
            }
        }
    }

    @Override
    protected void clickedElsewhere(int x, int y, int button) {
        if (x >= 220 && x < 242 && y >= 140 && y < 162) {
            if (button == 1 && item().getCompoundTag("coord").hasKey("p")) {
                NBTTagCompound c = item().getCompoundTag("coord");
                setPoint(c.getIntArray("p"), c.getString("n"));
            } else {
                send(BridgeFarSC.F_COORD_SLOT, null, null);
            }
            return;
        }
        if (isSpace() && x >= 196 && x < 218 && y >= 140 && y < 162) {
            send(BridgeFarSC.F_KEY_SLOT, null, null);
            return;
        }
        if (tab != T_COORDS) {
            clickList(tab, 6, 58, 238, 8, x, y);
        }
    }

    @Override
    protected void drawContent() {
        rect(0, 0, W, H, 0xFF060708);
        rect(1, 1, W - 2, H - 2, EDGE);
        rect(2, 2, W - 4, H - 4, BG);
        boolean space = isSpace();
        fit(Lang.tr(space ? "item.siliconage.spaceRemote.name" : "item.siliconage.bridgeRemote.name"), 6, 5, 150, space ? BLUE : OK);
        NBTTagCompound it = item();
        long max = Math.max(1, it.getLong("max"));
        smallRight(Lang.tr("sc.bridge.far.charge", it.getLong("charge") * 100 / max), W - 6, 6, it.getLong("charge") >= BridgeMathSC.REMOTE_SIGNAL_EU ? LABEL : BAD);
        // the bridge's line
        NBTTagCompound b = bridge();
        String line;
        int lc;
        if (st == null) {
            line = Lang.tr("sc.bridge.gui.loading");
            lc = DIM;
        } else if (b == null) {
            int reach = st.getInteger("reach");
            line = Lang.tr("sc.bridge.far." + (reach == BridgeFarSC.R_UNBOUND ? "unbound" : reach == BridgeFarSC.R_LOST ? "lost"
                    : reach == BridgeFarSC.R_ACCESS ? "access" : "unreachable"));
            lc = BAD;
        } else {
            String n = b.getString("name").length() == 0 ? Lang.tr("sc.bridge.res.noname") : b.getString("name");
            long d = b.getLong("dist");
            Object[] pl = planLine();
            String ready = b.getBoolean("open") ? Lang.tr("sc.bridge.far.st.open") : (Integer) pl[1] == OK ? Lang.tr("sc.bridge.far.st.ready")
                    : Lang.tr("sc.bridge.far.st.notready");
            line = Lang.tr("sc.bridge.far.bridgeline", n, d >= 0 ? Lang.tr("sc.bridge.far.blocks", g(d)) : Lang.tr("sc.bridge.far.otherdim"), ready);
            lc = (Integer) pl[1] == OK ? OK : (Integer) pl[1] == BAD ? BAD : WARN;
        }
        fit(line, 6, 17, W - 12, lc);
        // the tab
        if (tab == T_COORDS) {
            text("X", 6, 60, LABEL);
            text("Y", 64, 60, LABEL);
            text("Z", 112, 60, LABEL);
            if (space()) {
                small(Lang.tr("sc.bridge.gui.dim"), 6, 74, 90, LABEL);
            } else {
                small(Lang.tr("sc.bridge.gui.yauto"), 6, 74, 238, DIM);
            }
            drawPlace(6, 98, 238, 24);
        } else {
            drawList(tab, 6, 58, 238, 8);
        }
        // «Куда»
        text(Lang.tr("sc.bridge.far.where"), 6, 140, LABEL);
        frame(6, 149, space ? 186 : 210, 27, PANEL, EDGE);
        fit(whereLine(), 10, 152, (space ? 186 : 210) - 8, TEXT);
        Object[] pl = planLine();
        if (mode == BridgeMathSC.MODE_FRIEND) {
            if (text(ffriend).length() == 0 && !ffriend.isFocused()) {
                small(Lang.tr("sc.bridge.far.friendname"), 13, 165, 100, DIM);
            }
        } else {
            small((String) pl[0], 10, 165, (space ? 186 : 210) - 8, (Integer) pl[1]);
        }
        // the slots
        slotFrame(220, 140, it.hasKey("coord"));
        small(Lang.tr("sc.bridge.far.coordslot"), 216, 165, 32, DIM);
        if (space) {
            slotFrame(196, 140, it.getBoolean("key"));
            small(Lang.tr("sc.bridge.far.keyslot"), 194, 165, 24, it.getBoolean("key") ? DIM : BAD);
        }
        // the cost
        int proj = b == null ? BridgeMathSC.projections(mode, toMe) : b.getInteger("proj");
        text(Lang.tr("sc.bridge.far.cost", proj), 6, 179, LABEL);
        int y = drawCost(10, 189, 232, 3);
        costRow(10, y, 232, Lang.tr("sc.bridge.far.signal"), eu(BridgeMathSC.REMOTE_SIGNAL_EU) + " EU", it.getLong("charge") >= BridgeMathSC.REMOTE_SIGNAL_EU);
        // the bridge's state and the last answer
        String[] s = stateLines();
        small(s[0], 6, 240, W - 12, LABEL);
        small(s[1], 6, 248, W - 12, LABEL);
        if (lastMsg != null && System.currentTimeMillis() - lastMsgAt < 15000) {
            small(lastMsg.text(), 6, 256, W - 12, WARN);
        } else {
            small(Lang.tr(mode == BridgeMathSC.MODE_HOME || mode == BridgeMathSC.MODE_TO_ME ? "sc.bridge.far.hint.near" : "sc.bridge.far.hint.point"), 6, 256,
                    W - 12, DIM);
        }
    }

    private void slotFrame(int x, int y, boolean full) {
        frame(x, y, 22, 22, 0xFF0C0D12, full ? 0xFFB08A30 : 0xFF6A6A70);
    }

    @Override
    protected void drawOver() {
        NBTTagCompound it = item();
        GL11.glPushMatrix();
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        if (it.hasKey("coord")) {
            ITEMS.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), new ItemStack(ModItems.coordinator), 223, 143);
        }
        if (isSpace() && it.getBoolean("key")) {
            ITEMS.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), new ItemStack(ModItems.singularCell), 199, 143);
        }
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
    }

    @Override
    protected List<String> tip() {
        List<String> t = super.tip();
        if (t != null) {
            return t;
        }
        t = new ArrayList<String>();
        NBTTagCompound it = item();
        if (over(220, 140, 22, 22)) {
            t.add(Lang.tr("sc.bridge.far.coordslot.title"));
            NBTTagCompound c = it.getCompoundTag("coord");
            if (it.hasKey("coord")) {
                int[] p = c.getIntArray("p");
                t.add("§b" + (p.length == 4 ? (c.getString("n").length() > 0 ? c.getString("n") + " · " : "") + p[0] + " " + p[1] + " " + p[2]
                        : Lang.tr("sc.coordinator.empty")));
            }
            t.add("§7" + Lang.tr("sc.bridge.far.coordslot.hint"));
            return t;
        }
        if (isSpace() && over(196, 140, 22, 22)) {
            t.add(Lang.tr("sc.bridge.far.keyslot.title"));
            t.add("§7" + Lang.tr("sc.bridge.far.keyslot.hint"));
            return t;
        }
        if (over(6, 189, 238, 32)) {
            t.add(Lang.tr("sc.bridge.far.cost.hint", eu(BridgeMathSC.REMOTE_SIGNAL_EU)));
            return t;
        }
        if (over(6, 4, W - 12, 10)) {
            t.add(Lang.tr("sc.bridge.far.charge.hint", eu(it.getLong("charge")), eu(it.getLong("max")), eu(BridgeMathSC.REMOTE_SIGNAL_EU)));
            return t;
        }
        return null;
    }
}
