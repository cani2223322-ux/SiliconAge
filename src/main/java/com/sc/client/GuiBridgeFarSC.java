package com.sc.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.sc.bridge.BridgeFarSC;
import com.sc.bridge.BridgeMathSC;
import com.sc.bridge.BridgeMsgSC;
import com.sc.bridge.BridgeNetSC;
import com.sc.inventory.GuiGaugeSC;
import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityBridgeControllerSC;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * What the Bridge Remote's screen (GuiRemoteSC) and the armour's «Мост» tab (GuiArmorBridgeSC) share
 * (docs/ground-bridge/bridge_4/5/7/8/9): a W x H canvas scaled down on a small screen, the bridge screens' look,
 * the mode (ДР1-ДР5), the target - X / Y («авто») / Z and the dimension, filled from the bookmarks, the history,
 * the scanner's finds, the coordinator or «Моё» / «Взгляд» - «Проверить место» with its answer and «Взять его»,
 * «В закладки» / «В координатор», the friend's name and «к вам» (ДР4), «Открыть» / «Закрыть»; the server's
 * answer (BridgeFarSC) comes every second and after every command.
 */
public abstract class GuiBridgeFarSC extends GuiScreen {

    protected static final int BG = 0xFF1A1C22, PANEL = 0xFF101318, EDGE = 0xFF464C5A, EDGE_HI = 0xFF3C6A4C, TEXT = 0xE6EAF0,
            LABEL = 0xA0A8B4, DIM = 0x6E7480, OK = 0x5AE66E, BAD = 0xFF6A5A, WARN = 0xFFE14D, BLUE = 0x8CB4FF;
    protected static final int B_MODE = 10, B_TAB = 20, B_ME = 30, B_LOOK = 31, B_DIM = 32, B_PROBE = 33, B_TAKE = 34, B_TO_BM = 35,
            B_TO_COORD = 36, B_OPEN = 37, B_CLOSE = 38, B_TOME = 39;
    protected static final int T_BM = 0, T_COORDS = 1, T_HIST = 2, T_FINDS = 3;

    protected final int W, H;
    protected float k = 1F;
    protected int left, top, vmx, vmy;
    protected final List<Btn> btns = new ArrayList<Btn>();
    protected GuiTextField fx, fy, fz, ffriend;
    protected NBTTagCompound st;
    protected NBTTagCompound place;
    protected int mode = BridgeMathSC.MODE_HOME, tab = T_BM, dim, listScroll, sel = -1;
    protected boolean toMe;
    protected String targetName = "";
    private int reqTimer, changed = -1;
    private String lastKey = "";

    protected GuiBridgeFarSC(int w, int h) {
        W = w;
        H = h;
    }

    /** BridgeFarSC.SRC_REMOTE / SRC_ARMOUR. */
    protected abstract int src();

    /** The armour's selected link (the remote: 0). */
    protected int slot() {
        return 0;
    }

    /** The answer from the server (ClientProxy.bridgeFarState). */
    public void setState(NBTTagCompound s) {
        if (s == null || s.getInteger("src") != src()) {
            return;
        }
        st = s;
        if (s.hasKey("place")) {
            place = s.getCompoundTag("place");
        }
        if (s.hasKey("msg")) {
            lastMsg = BridgeMsgSC.read(s.getCompoundTag("msg"));
            lastMsgAt = System.currentTimeMillis();
        }
        if (!dimSet && bridge() != null) {
            int[] pos = bridge().getIntArray("pos");
            dim = mc.thePlayer != null ? mc.thePlayer.dimension : pos.length == 4 ? pos[3] : 0;
            dimSet = true;
        }
    }

    private boolean dimSet;
    protected BridgeMsgSC lastMsg;
    protected long lastMsgAt;

    protected NBTTagCompound bridge() {
        return st != null && st.hasKey("bridge") ? st.getCompoundTag("bridge") : null;
    }

    /** С5 / С7 for the order's target point: «знакомое место: -25%» / «незнакомое: +50%, разброс до 30 бл.»; "" - no point. */
    protected String famLine() {
        NBTTagCompound b = bridge();
        return b == null || !b.hasKey("fam") ? "" : GuiBridgeControllerSC.famText(b.getIntArray("fam"));
    }

    /** The colour of famLine: familiar - green, unfamiliar or scouting - yellow. */
    protected int famColor() {
        NBTTagCompound b = bridge();
        int[] f = b == null ? new int[0] : b.getIntArray("fam");
        return f.length < 4 ? DIM : f[0] == 1 && f[3] == 0 ? OK : WARN;
    }

    protected boolean space() {
        NBTTagCompound b = bridge();
        return b != null && b.getInteger("kind") == BridgeMathSC.SPACE;
    }

    // ------------------------------------------------------------------ set-up

    protected void scale() {
        k = Math.min(1F, Math.min(width / (float) W, height / (float) H));
        left = Math.round((width - W * k) / 2F);
        top = Math.round((height - H * k) / 2F);
    }

    protected GuiTextField field(int x, int y, int w, String text, int max) {
        GuiTextField f = new GuiTextField(fontRendererObj, x, y, w, 11);
        f.setMaxStringLength(max);
        f.setText(text == null ? "" : text);
        return f;
    }

    protected String text(GuiTextField f) {
        return f == null ? "" : f.getText();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ------------------------------------------------------------------ the order

    /** The typed point, or null when X / Z aren't numbers. */
    protected int[] point() {
        try {
            int x = Integer.parseInt(text(fx).trim().replace(" ", ""));
            int z = Integer.parseInt(text(fz).trim().replace(" ", ""));
            String ys = text(fy).trim();
            int y = ys.length() == 0 || ys.equalsIgnoreCase("auto") || ys.equalsIgnoreCase(Lang.tr("sc.bridge.gui.auto"))
                    ? TileEntityBridgeControllerSC.AUTO_Y : Integer.parseInt(ys);
            return new int[]{x, y, z, space() ? dim : mc.thePlayer.dimension};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected int[] orderValues() {
        int[] p = point();
        boolean has = p != null && BridgeMathSC.needsPoint(mode);
        return new int[]{mode, has ? 1 : 0, p == null ? 0 : p[0], p == null ? 0 : p[1], p == null ? 0 : p[2], p == null ? 0 : p[3], toMe ? 1 : 0,
                fromFind ? 1 : 0};
    }

    /** The target is a scanner find (cleared by any other choice or typing). */
    protected boolean fromFind;

    protected String orderText() {
        return text(ffriend).trim() + "\n" + targetName;
    }

    protected void send(int action, int[] values, String text) {
        BridgeNetSC.sendFar(src(), slot(), action, values, text);
    }

    protected void sendOrder(int action) {
        send(action, orderValues(), orderText());
    }

    protected void request() {
        sendOrder(BridgeFarSC.F_STATE);
    }

    /** Something of the order changed: ask again soon. */
    protected void touched() {
        changed = 4;
    }

    protected void setPoint(int[] p, String name) {
        if (p == null || p.length < 3) {
            return;
        }
        fx.setText(String.valueOf(p[0]));
        fy.setText(p[1] == TileEntityBridgeControllerSC.AUTO_Y ? "" : String.valueOf(p[1]));
        fz.setText(String.valueOf(p[2]));
        if (p.length >= 4) {
            dim = p[3];
        }
        targetName = name == null ? "" : name;
        fromFind = false;
        place = null;
        touched();
    }

    @Override
    public void updateScreen() {
        for (GuiTextField f : new GuiTextField[]{fx, fy, fz, ffriend}) {
            if (f != null) {
                f.updateCursorCounter();
            }
        }
        String key = mode + "," + toMe + "," + text(fx) + "," + text(fy) + "," + text(fz) + "," + dim + "," + text(ffriend);
        if (!key.equals(lastKey)) {
            lastKey = key;
            changed = Math.max(changed, 4);
        }
        if (changed > 0 && --changed == 0) {
            request();
            reqTimer = 0;
        } else if (++reqTimer >= 20) {
            reqTimer = 0;
            request();
        }
    }

    // ------------------------------------------------------------------ input

    protected int vx(int mx) {
        return (int) Math.floor((mx - left) / k);
    }

    protected int vy(int my) {
        return (int) Math.floor((my - top) / k);
    }

    protected boolean over(int x, int y, int w, int h) {
        return vmx >= x && vmy >= y && vmx < x + w && vmy < y + h;
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        int x = vx(mx), y = vy(my);
        for (GuiTextField f : new GuiTextField[]{fx, fy, fz, ffriend}) {
            if (f != null && f.getVisible()) {
                f.mouseClicked(x, y, button);
            }
        }
        for (Btn b : btns) {
            if (b.visible && b.enabled && b.over(x, y)) {
                mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.func_147674_a(
                        new net.minecraft.util.ResourceLocation("gui.button.press"), 1.0F));
                press(b.id, button);
                return;
            }
        }
        clickedElsewhere(x, y, button);
    }

    /** A click on no button (the lists, the slots). */
    protected void clickedElsewhere(int x, int y, int button) {
    }

    protected void press(int id, int button) {
        if (id >= B_MODE && id < B_MODE + BridgeMathSC.MODES) {
            mode = id - B_MODE;
            touched();
            return;
        }
        if (id >= B_TAB && id < B_TAB + 4) {
            tab = id - B_TAB;
            listScroll = 0;
            sel = -1;
            return;
        }
        int[] p = point();
        switch (id) {
            case B_ME:
                setPoint(new int[]{(int) Math.floor(mc.thePlayer.posX), (int) Math.floor(mc.thePlayer.boundingBox.minY + 0.001),
                        (int) Math.floor(mc.thePlayer.posZ), mc.thePlayer.dimension}, Lang.tr("sc.bridge.far.here"));
                return;
            case B_LOOK: {
                final net.minecraft.world.World w = mc.theWorld;
                net.minecraft.util.Vec3 look = mc.thePlayer.getLookVec();
                int[] t = BridgeMathSC.lookTarget(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight() - mc.thePlayer.yOffset,
                        mc.thePlayer.posZ, look.xCoord, look.yCoord, look.zCoord, BridgeMathSC.LOOK_RANGE, new BridgeMathSC.Solid() {
                            @Override
                            public boolean at(int x, int y, int z) {
                                return y < w.getHeight() && w.getBlock(x, y, z).getMaterial().blocksMovement();
                            }
                        });
                if (t == null) {
                    lastMsg = new BridgeMsgSC("sc.bridge.far.nolook", BridgeMathSC.LOOK_RANGE);
                    lastMsgAt = System.currentTimeMillis();
                } else {
                    setPoint(new int[]{t[0], t[1], t[2], mc.thePlayer.dimension}, Lang.tr("sc.bridge.far.look"));
                }
                return;
            }
            case B_DIM: {
                NBTTagCompound b = bridge();
                NBTTagList d = b == null ? new NBTTagList() : b.getTagList("dims", 10);
                if (d.tagCount() == 0) {
                    return;
                }
                int at = 0;
                for (int i = 0; i < d.tagCount(); i++) {
                    if (d.getCompoundTagAt(i).getInteger("id") == dim) {
                        at = i;
                    }
                }
                at = (at + (button == 1 ? d.tagCount() - 1 : 1)) % d.tagCount();
                dim = d.getCompoundTagAt(at).getInteger("id");
                touched();
                return;
            }
            case B_PROBE:
                if (p != null) {
                    send(BridgeFarSC.F_PROBE, p, null);
                }
                return;
            case B_TAKE: {
                int[] n = place != null ? place.getIntArray("near") : new int[0];
                if (n.length == 4) {
                    int[] at = place.getIntArray("at");
                    setPoint(new int[]{n[0], n[1], n[2], at.length == 4 ? at[3] : dim}, targetName);
                }
                return;
            }
            case B_TO_BM:
                if (p != null) {
                    String n = targetName.length() > 0 ? targetName : Lang.tr("sc.bridge.gui.bm.default", bookmarks().tagCount() + 1);
                    send(BridgeFarSC.F_BM_ADD, p, n);
                }
                return;
            case B_TO_COORD:
                if (p != null) {
                    send(BridgeFarSC.F_TO_COORD, p, "\n" + targetName);
                }
                return;
            case B_OPEN:
                sendOrder(BridgeFarSC.F_OPEN);
                return;
            case B_CLOSE:
                send(BridgeFarSC.F_CLOSE, null, null);
                return;
            case B_TOME:
                toMe = !toMe;
                touched();
                return;
            default:
                break;
        }
    }

    @Override
    protected void keyTyped(char c, int key) {
        boolean typed = false;
        GuiTextField[] nums = {fx, fy, fz};
        for (GuiTextField f : nums) {
            if (f != null && f.isFocused()) {
                if (key == Keyboard.KEY_TAB) {
                    f.setFocused(false);
                    (f == fx ? fy : f == fy ? fz : fx).setFocused(true);
                    return;
                }
                if (key == Keyboard.KEY_ESCAPE) {
                    break;
                }
                if (Character.isDigit(c) || c == '-' || key == Keyboard.KEY_BACK || key == Keyboard.KEY_DELETE || key == Keyboard.KEY_LEFT
                        || key == Keyboard.KEY_RIGHT || key == Keyboard.KEY_HOME || key == Keyboard.KEY_END || GuiScreen.isCtrlKeyDown()) {
                    f.textboxKeyTyped(c, key);
                    targetName = "";
                    fromFind = false;
                    place = null;
                }
                typed = true;
            }
        }
        if (ffriend != null && ffriend.isFocused() && key != Keyboard.KEY_ESCAPE) {
            ffriend.textboxKeyTyped(c, key);
            typed = true;
        }
        if (typed && key != Keyboard.KEY_ESCAPE) {
            return;
        }
        if (key == Keyboard.KEY_ESCAPE || key == mc.gameSettings.keyBindInventory.getKeyCode()) {
            mc.displayGuiScreen(null);
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            listScroll = Math.max(0, listScroll + (wheel > 0 ? -1 : 1));
        }
    }

    // ------------------------------------------------------------------ the lists

    protected NBTTagList bookmarks() {
        NBTTagCompound b = bridge();
        return b == null ? new NBTTagList() : b.getTagList("bookmarks", 10);
    }

    protected NBTTagList history() {
        if (st == null) {
            return new NBTTagList();
        }
        return st.hasKey("item") ? st.getCompoundTag("item").getTagList("hist", 10) : st.getTagList("hist", 10);
    }

    protected NBTTagList finds() {
        return st == null ? new NBTTagList() : st.getTagList("finds", 10);
    }

    protected NBTTagList listOf(int t) {
        return t == T_BM ? bookmarks() : t == T_HIST ? history() : finds();
    }

    /** A row's name: a bookmark's / history's name, a find's kind. */
    protected String rowName(int t, NBTTagCompound e) {
        if (t == T_FINDS) {
            return Lang.tr("sc.bridge.far.find." + Math.max(0, Math.min(3, e.getInteger("k"))));
        }
        String n = e.getString("n");
        return n.length() == 0 ? Lang.tr("sc.bridge.far.noname") : n;
    }

    protected String rowPos(NBTTagCompound e) {
        int[] p = e.getIntArray("p");
        if (p.length < 3) {
            return "";
        }
        String s = BridgeMathSC.group(p[0]) + " " + (p[1] == TileEntityBridgeControllerSC.AUTO_Y ? Lang.tr("sc.bridge.gui.auto") : p[1]) + " "
                + BridgeMathSC.group(p[2]);
        if (p.length == 4 && mc.thePlayer != null && p[3] != mc.thePlayer.dimension) {
            s += " · " + dimName(p[3]);
        }
        return s;
    }

    protected String dimName(int id) {
        NBTTagCompound b = bridge();
        NBTTagList d = b == null ? new NBTTagList() : b.getTagList("dims", 10);
        for (int i = 0; i < d.tagCount(); i++) {
            if (d.getCompoundTagAt(i).getInteger("id") == id) {
                return d.getCompoundTagAt(i).getString("n");
            }
        }
        return "DIM " + id;
    }

    /** Draws a list tab in (x, y, w) with `rows` rows. */
    protected void drawList(int t, int x, int y, int w, int rows) {
        NBTTagList l = listOf(t);
        listScroll = Math.max(0, Math.min(Math.max(0, l.tagCount() - rows), listScroll));
        for (int r = 0; r < rows; r++) {
            int i = listScroll + r, ry = y + r * 9;
            rect(x, ry, w, 8, i == sel ? 0xFF22402C : (r % 2 == 0 ? 0xFF15181E : 0xFF12151A));
            if (i >= l.tagCount()) {
                if (l.tagCount() == 0 && r == 0) {
                    small(Lang.tr(t == T_BM ? "sc.bridge.gui.bm.none" : t == T_HIST ? "sc.bridge.far.nohist" : "sc.bridge.far.nofinds"), x + 3, ry + 2,
                            w - 6, DIM);
                }
                continue;
            }
            NBTTagCompound e = l.getCompoundTagAt(i);
            small(rowName(t, e), x + 3, ry + 2, w / 2 - 4, TEXT);
            String pos = rowPos(e);
            if (t == T_FINDS && mc.thePlayer != null) {
                int[] p = e.getIntArray("p");
                if (p.length == 4 && p[3] == mc.thePlayer.dimension) {
                    pos += " · " + Lang.tr("sc.bridge.far.blocks", BridgeMathSC.group(BridgeMathSC.distance(p[0], p[1], p[2],
                            (int) mc.thePlayer.posX, (int) mc.thePlayer.posY, (int) mc.thePlayer.posZ)));
                }
            }
            smallRight(pos, x + w - 3, ry + 2, LABEL);
        }
        if (l.tagCount() > rows) {
            smallRight((listScroll + 1) + "-" + Math.min(l.tagCount(), listScroll + rows) + "/" + l.tagCount(), x + w, y - 8, DIM);
        }
    }

    /** A click in a list tab. @return whether it hit a row */
    protected boolean clickList(int t, int x, int y, int w, int rows, int mx, int my) {
        NBTTagList l = listOf(t);
        for (int r = 0; r < rows; r++) {
            int i = listScroll + r;
            if (i < l.tagCount() && mx >= x && mx < x + w && my >= y + r * 9 && my < y + r * 9 + 9) {
                sel = i;
                NBTTagCompound e = l.getCompoundTagAt(i);
                setPoint(e.getIntArray("p"), t == T_FINDS ? rowName(t, e) : e.getString("n"));
                fromFind = t == T_FINDS;
                if (BridgeMathSC.needsPoint(mode) == false) {
                    mode = BridgeMathSC.MODE_FROM_ME;
                }
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ the place check's answer

    protected void drawPlace(int x, int y, int w, int h) {
        if (place == null) {
            frame(x, y, w, h, PANEL, EDGE);
            small(Lang.tr("sc.bridge.gui.place.hint"), x + 4, y + 4, w - 8, DIM);
            return;
        }
        boolean free = place.getBoolean("free");
        frame(x, y, w, h, PANEL, free ? 0xFF3CC86E : 0xFFC84A40);
        int[] at = place.getIntArray("at");
        String[] args = BridgeMsgSC.read(place.getCompoundTag("args")).args;
        Object[] a = new Object[args.length];
        System.arraycopy(args, 0, a, 0, args.length);
        if (free && at.length == 4) {
            fit(Lang.tr(place.getBoolean("air") ? "sc.bridge.gui.place.freeair" : "sc.bridge.gui.place.free", at[0], at[1], at[2]), x + 4, y + 3, w - 8, OK);
            if (place.getBoolean("void")) {
                small(Lang.tr("sc.bridge.gui.place.void", g(place.getLong("dist"))), x + 4, y + 13, w - 8, WARN);
            } else {
                small(Lang.tr("sc.bridge.gui.place.freeinfo", place.getInteger("w"), place.getInteger("w"), g(place.getLong("dist")))
                        + (place.getBoolean("beacon") ? "  " + Lang.tr("sc.bridge.gui.beacon", BridgeMathSC.BEACON_DISCOUNT) : ""), x + 4, y + 13, w - 8, LABEL);
            }
        } else {
            String why = a.length == 0 ? Lang.tr(place.getString("reason")) : Lang.tr(place.getString("reason"), a);
            fit(Lang.tr("sc.bridge.gui.place.blocked", why), x + 4, y + 3, w - 8, BAD);
            int[] n = place.getIntArray("near");
            small(n.length == 4 ? Lang.tr("sc.bridge.gui.place.near", n[0], n[1], n[2], n[3]) : Lang.tr("sc.bridge.gui.place.nonear"), x + 4, y + 13,
                    w - 66, n.length == 4 ? WARN : DIM);
        }
    }

    // ------------------------------------------------------------------ the order's summary from the server's plan

    /** "Куда": what the mode does with the target (one line). */
    protected String whereLine() {
        NBTTagCompound b = bridge();
        String bn = b == null ? "?" : b.getString("name").length() == 0 ? Lang.tr("sc.bridge.res.noname") : b.getString("name");
        int[] p = point();
        String pt = p == null ? Lang.tr("sc.bridge.far.nopoint") : (targetName.length() > 0 ? targetName + " · " : "")
                + BridgeMathSC.group(p[0]) + " " + (p[1] == TileEntityBridgeControllerSC.AUTO_Y ? Lang.tr("sc.bridge.gui.auto") : p[1]) + " "
                + BridgeMathSC.group(p[2]);
        switch (mode) {
            case BridgeMathSC.MODE_HOME: return Lang.tr("sc.bridge.far.where.1", bn);
            case BridgeMathSC.MODE_FROM_ME: return Lang.tr("sc.bridge.far.where.2", pt);
            case BridgeMathSC.MODE_FRIEND: return Lang.tr(toMe ? "sc.bridge.far.where.4me" : "sc.bridge.far.where.4", text(ffriend).trim().length() == 0
                    ? "?" : text(ffriend).trim(), bn);
            case BridgeMathSC.MODE_TO_ME: return Lang.tr("sc.bridge.far.where.5", bn);
            default: return Lang.tr("sc.bridge.far.where.3", bn, pt);
        }
    }

    /** The plan's note: why not / whose consent / what is missing / where your end stands. @return {text, colour} */
    protected Object[] planLine() {
        NBTTagCompound b = bridge();
        if (st == null) {
            return new Object[]{Lang.tr("sc.bridge.gui.loading"), DIM};
        }
        int reach = st.getInteger("reach");
        if (b == null || reach != BridgeFarSC.R_OK) {
            String k = reach == BridgeFarSC.R_UNBOUND ? "unbound" : reach == BridgeFarSC.R_LOST ? "lost" : reach == BridgeFarSC.R_ACCESS ? "access" : "unreachable";
            return new Object[]{Lang.tr("sc.bridge.far." + k), BAD};
        }
        if (b.hasKey("refuse")) {
            return new Object[]{BridgeMsgSC.read(b.getCompoundTag("refuse")).text(), BAD};
        }
        if (b.hasKey("consent")) {
            return new Object[]{Lang.tr("sc.bridge.far.needconsent", b.getString("consent")), WARN};
        }
        String me = mc.thePlayer.getCommandSenderName();
        int[] e = me.equalsIgnoreCase(b.getString("endAp")) ? b.getIntArray("endA") : me.equalsIgnoreCase(b.getString("endBpl")) ? b.getIntArray("endBp") : null;
        String end = e != null && e.length == 5 ? Lang.tr("sc.bridge.far.yourend", e[2], e[3], e[4]) : "";
        if (b.hasKey("missing")) {
            return new Object[]{BridgeMsgSC.read(b.getCompoundTag("missing")).text(), WARN};
        }
        if (!b.getBoolean("calibrated") || !b.getBoolean("power") || !b.getBoolean("valid")) {
            return new Object[]{Lang.tr(!b.getBoolean("valid") ? "sc.bridge.far.notbuilt" : !b.getBoolean("power") ? "sc.bridge.far.off" : "sc.bridge.far.nocalib"), BAD};
        }
        int[] t = b.getIntArray("time");
        if (b.getBoolean("open")) {
            return new Object[]{Lang.tr("sc.bridge.far.isopen", t.length > 0 ? (t[0] + 19) / 20 : 0), BLUE};
        }
        if (t.length > 2 && t[2] > 0) {
            return new Object[]{Lang.tr("sc.bridge.far.cooling", (t[2] + 19) / 20), WARN};
        }
        return new Object[]{end.length() > 0 ? end : Lang.tr("sc.bridge.far.ready"), OK};
    }

    /** "Мост: конденсаторы 820 млн / 1 млрд" and "баки в норме · кольцо остыло · стабильность 92%". */
    protected String[] stateLines() {
        NBTTagCompound b = bridge();
        if (b == null) {
            return new String[]{"", ""};
        }
        String l1 = Lang.tr("sc.bridge.far.caps", eu(b.getLong("capEu")), eu(b.getLong("capMax")));
        int[] tk = ints(b, "tanks", BridgeMathSC.GASES.length), caps = ints(b, "tankCaps", BridgeMathSC.GASES.length);
        int low = 0;
        for (int i = 0; i < tk.length; i++) {
            low += caps[i] > 0 && tk[i] < caps[i] / 10 && i != BridgeMathSC.D2O ? 1 : 0;
        }
        int[] t = ints(b, "time", 6);
        String ring = b.getBoolean("open") ? Lang.tr("sc.bridge.gui.cool.open") : t[2] > 0 ? Lang.tr("sc.bridge.gui.cool.left", (t[2] + 19) / 20)
                : Lang.tr("sc.bridge.far.ringready");
        int stab = b.getBoolean("open") ? t[4] : b.getInteger("baseStab");
        String l2 = Lang.tr(low == 0 ? "sc.bridge.far.tanksok" : "sc.bridge.far.tankslow", low) + " · " + ring + " · "
                + Lang.tr("sc.bridge.far.stab", stab) + " · " + Lang.tr("sc.bridge.armour.wear", b.getInteger("wear"));
        if (b.getBoolean("overheat")) {
            l2 = Lang.tr(low == 0 ? "sc.bridge.far.tanksok" : "sc.bridge.far.tankslow", low) + " · "
                    + Lang.tr("sc.bridge.gui.overheat", t.length > 2 ? (t[2] + 19) / 20 : 0);
        }
        return new String[]{l1, l2};
    }

    protected static int[] ints(NBTTagCompound t, String key, int n) {
        int[] a = t == null ? new int[0] : t.getIntArray(key);
        return a.length >= n ? a : new int[n];
    }

    // ------------------------------------------------------------------ drawing helpers

    protected static void rect(int x, int y, int w, int h, int c) {
        if (w > 0 && h > 0) {
            Gui.drawRect(x, y, x + w, y + h, c);
        }
    }

    protected static void frame(int x, int y, int w, int h, int fill, int edge) {
        rect(x, y, w, h, edge);
        rect(x + 1, y + 1, w - 2, h - 2, fill);
    }

    protected void text(String s, int x, int y, int color) {
        fontRendererObj.drawString(s, x, y, color);
    }

    protected void fit(String s, int x, int y, int maxW, int color) {
        int w = fontRendererObj.getStringWidth(s);
        if (w <= maxW) {
            text(s, x, y, color);
            return;
        }
        float f = Math.max(0.75F, maxW / (float) w);
        String shown = s;
        if (w * f > maxW) {
            shown = fontRendererObj.trimStringToWidth(s, (int) (maxW / f) - fontRendererObj.getStringWidth("...")) + "...";
        }
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y + (1F - f) * 4F, 0F);
        GL11.glScalef(f, f, 1F);
        text(shown, 0, 0, color);
        GL11.glPopMatrix();
    }

    protected void small(String s, int x, int y, int maxW, int color) {
        float f = 0.75F;
        String shown = s;
        if (fontRendererObj.getStringWidth(s) * f > maxW) {
            shown = fontRendererObj.trimStringToWidth(s, Math.max(0, (int) (maxW / f) - fontRendererObj.getStringWidth("..."))) + "...";
        }
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0F);
        GL11.glScalef(f, f, 1F);
        text(shown, 0, 0, color);
        GL11.glPopMatrix();
    }

    protected void smallRight(String s, int right, int y, int color) {
        int w = (int) Math.ceil(fontRendererObj.getStringWidth(s) * 0.75F);
        small(s, right - w, y, 400, color);
    }

    protected void centered(String s, int x, int y, int w, int color) {
        int sw = fontRendererObj.getStringWidth(s);
        if (sw > w) {
            fit(s, x, y, w, color);
        } else {
            text(s, x + (w - sw) / 2, y, color);
        }
    }

    protected String eu(long v) {
        return BridgeMathSC.shortEu(v, Lang.tr("sc.bridge.unit.m"), Lang.tr("sc.bridge.unit.g"));
    }

    protected static String g(long v) {
        return BridgeMathSC.group(v);
    }

    /** One cost row: a square, the name, need / have. */
    protected void costRow(int x, int y, int w, String name, String value, boolean ok) {
        rect(x, y, 4, 4, ok ? 0xFF5AE66E : 0xFFE05040);
        small(name, x + 7, y, w / 2, TEXT);
        smallRight(value, x + w, y, ok ? TEXT : BAD);
    }

    /** The cost rows of the plan (energy, SM, Kr, D, Ar - `rows` of them). @return the y after them */
    protected int drawCost(int x, int y, int w, int rows) {
        NBTTagCompound b = bridge();
        if (b == null || !b.hasKey("cost")) {
            small(Lang.tr("sc.bridge.far.nocost"), x, y, w, DIM);
            return y + 8;
        }
        int[] c = ints(b, "cost", 11), tk = ints(b, "tanks", BridgeMathSC.GASES.length);
        long capEu = b.getLong("capEu"), costEu = b.getLong("costEu");
        costRow(x, y, w, Lang.tr("sc.bridge.res.eu"), eu(costEu) + " / " + eu(capEu) + " EU", capEu >= costEu);
        int[] idx = {BridgeMathSC.SM, BridgeMathSC.KR, BridgeMathSC.D, BridgeMathSC.AR};
        int[] need = {c[0], c[2], c[1], c[3]};
        for (int i = 0; i < rows - 1 && i < 4; i++) {
            costRow(x, y + (i + 1) * 8, w, Lang.tr(BridgeMsgSC.RES + "gas." + BridgeMathSC.GASES[idx[i]].key()),
                    g(need[i]) + " / " + g(tk[idx[i]]) + " " + Lang.tr("sc.bridge.unit.mb"), tk[idx[i]] >= need[i]);
        }
        return y + rows * 8;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void drawScreen(int mouseX, int mouseY, float partial) {
        drawDefaultBackground();
        vmx = vx(mouseX);
        vmy = vy(mouseY);
        refresh();
        GL11.glPushMatrix();
        GL11.glTranslatef(left, top, 0F);
        GL11.glScalef(k, k, 1F);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        drawContent();
        for (Btn b : btns) {
            b.draw();
        }
        for (GuiTextField f : new GuiTextField[]{fx, fy, fz, ffriend}) {
            if (f != null && f.getVisible()) {
                f.drawTextBox();
            }
        }
        if (fy != null && fy.getVisible() && fy.getText().length() == 0 && !fy.isFocused()) {
            text(Lang.tr("sc.bridge.gui.auto"), fy.xPosition + 3, fy.yPosition + 2, OK);
        }
        drawOver();
        List<String> tip = tip();
        GL11.glPopMatrix();
        if (tip != null && !tip.isEmpty()) {
            drawHoveringText(GuiGaugeSC.wrapTooltip(fontRendererObj, tip, width), mouseX, mouseY, fontRendererObj);
        }
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** Labels, enabled and visible states, every frame. */
    protected abstract void refresh();

    protected abstract void drawContent();

    /** Items over everything (the slots). */
    protected void drawOver() {
    }

    protected List<String> tip() {
        for (Btn b : btns) {
            if (b.visible && b.over(vmx, vmy)) {
                List<String> t = new ArrayList<String>();
                if (b.id >= B_MODE && b.id < B_MODE + BridgeMathSC.MODES) {
                    t.add(Lang.tr("sc.bridge.gui.mode." + (b.id - B_MODE)));
                    t.add("§7" + Lang.tr("sc.bridge.gui.mode." + (b.id - B_MODE) + ".hint"));
                } else if (b.tip != null) {
                    for (String line : b.tip.split("\n")) {
                        t.add(t.isEmpty() ? line : "§7" + line);
                    }
                } else {
                    return null;
                }
                return t;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ buttons

    protected class Btn {
        final int id;
        int x, y, w, h;
        String label = "", tip;
        int color = TEXT;
        boolean enabled = true, visible = true, selected;

        Btn(int id, int x, int y, int w, int h, String key) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.label = key == null || key.length() == 0 ? "" : Lang.tr(key);
        }

        boolean over(int mx, int my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }

        void draw() {
            if (!visible) {
                return;
            }
            boolean hover = enabled && over(vmx, vmy);
            rect(x, y, w, h, selected ? 0xFF5A8A6A : hover ? 0xFFB8C4BC : 0xFF0A0C0B);
            rect(x + 1, y + 1, w - 2, h - 2, selected ? 0xFF2E3A33 : !enabled ? 0xFF2E3230 : hover ? 0xFF7E8682 : 0xFF5E6462);
            if (enabled && !selected) {
                rect(x + 1, y + 1, w - 2, 1, 0xFF8A928E);
            }
            int c = !enabled && !selected ? 0x7A807C : color & 0xFFFFFF;
            int sw = fontRendererObj.getStringWidth(label);
            if (h < 11 || sw > w - 4) {
                float f = Math.min(0.75F, (w - 4) / (float) Math.max(1, sw));
                f = Math.max(0.5F, f);
                GL11.glPushMatrix();
                GL11.glTranslatef(x + (w - sw * f) / 2F, y + (h - 8 * f) / 2F + 0.5F, 0F);
                GL11.glScalef(f, f, 1F);
                text(label, 0, 0, c);
                GL11.glPopMatrix();
            } else {
                text(label, x + (w - sw) / 2, y + (h - 8) / 2 + 1, c);
            }
        }
    }

    protected Btn add(int id, int x, int y, int w, int h, String key) {
        Btn b = new Btn(id, x, y, w, h, key);
        btns.add(b);
        return b;
    }

    protected Btn find(int id) {
        for (Btn b : btns) {
            if (b.id == id) {
                return b;
            }
        }
        return null;
    }
}
