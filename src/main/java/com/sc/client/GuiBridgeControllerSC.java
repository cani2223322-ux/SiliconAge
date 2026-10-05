package com.sc.client;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.sc.bridge.BridgeMathSC;
import com.sc.bridge.BridgeMsgSC;
import com.sc.bridge.BridgeNetSC;
import com.sc.bridge.BridgeStructureSC;
import com.sc.inventory.GuiEnergyGaugeSC;
import com.sc.inventory.GuiGaugeSC;
import com.sc.inventory.GuiTankGaugeSC;
import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityBridgeControllerSC;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

/**
 * The Bridge Controller's screen (docs/ground-bridge/bridge_3_controller.png): 420 x 300 - left the build's
 * schematic (the ring seen from the front, what's missing in red, the problem block also outlined in the
 * world for a few seconds), «Проверить», «Калибровка», the ring's heat / wear / cooling and the power switch;
 * centre the target: the modes (stage 1: «С базы» only), X / Y (empty - «авто») / Z and, for the Space
 * bridge, the dimension, «Проверить место» with its answer and «Взять его», the bookmarks (add, rename,
 * delete), the cost need / have, «Открыть» / «Закрыть»; right the capacitors (the machines' energy gauge), the
 * six tanks (the machines' gauges, × pours one out), stability; under it the journal and the access
 * (owner only in stage 1). A screen smaller than 420 x 300 GUI pixels gets the same screen scaled down.
 *
 * It holds no container: the state comes from the server (BridgeNetSC) twice a second while it's open.
 */
public class GuiBridgeControllerSC extends GuiScreen {

    public static final int W = 420, H = 300;
    private static final int BG = 0xFF161C1A, PANEL = 0xFF101513, EDGE = 0xFF3C5A48, EDGE_HI = 0xFF5A8A6A, TEXT = 0xE6EEE8,
            LABEL = 0xA8B4AC, DIM = 0x6E7A72, OK = 0x5AE66E, BAD = 0xFF6A5A, WARN = 0xFFE14D, BLUE = 0x8CB4FF;
    private static final int B_CHECK = 1, B_CALIB = 2, B_POWER = 3, B_PROBE = 4, B_TAKE = 5, B_BM_ADD = 6, B_BM_REN = 7, B_BM_DEL = 8,
            B_OPEN = 9, B_CLOSE = 10, B_DIM = 11, B_MODE = 20, B_CLEAR = 30, B_ACCESS = 40;
    /** Stage 2: friends, «Дистанционный режим», «Привязать шлем», the name, the coordinators in the inventory. */
    private static final int B_FRIEND_ADD = 41, B_REMOTE = 42, B_BIND = 43, B_RENAME = 44, B_FROM_COORD = 45, B_TO_COORD = 46;
    /** Stage 3: «Ремонт» (С2). */
    private static final int B_REPAIR = 47;
    private static final int MODES = 6;
    private static final int BM_ROWS = 4, BM_Y = 131, BM_ROW_H = 9;

    private final int cx, cy, cz;
    private NBTTagCompound st;
    private float k = 1F;
    private int left, top, vmx, vmy;
    private final List<Btn> btns = new ArrayList<Btn>();
    private GuiTextField fx, fy, fz, fname, ffriend, fbridge;
    private int seenRev = -1, armedFriend = -1;
    private long armedFriendAt;
    private boolean renamingBridge;
    /** The friends' chips as last drawn: {x, y, w, index}. */
    private final List<int[]> chips = new ArrayList<int[]>();
    private int selBm = -1, bmScroll, renaming = -1;
    private int dim, reqTimer, sendTimer;
    private String lastSent = "";
    private boolean filled;
    private int armedClear = -1;
    private long armedAt;

    public GuiBridgeControllerSC(int x, int y, int z) {
        this.cx = x;
        this.cy = y;
        this.cz = z;
    }

    public boolean isFor(int x, int y, int z) {
        return x == cx && y == cy && z == cz;
    }

    public void setState(NBTTagCompound s) {
        st = s;
        if (filled && s.getInteger("targetRev") != seenRev && seenRev >= 0) {
            filled = false;                                   // the server took a coordinator's point: show it
        }
        seenRev = s.getInteger("targetRev");
        if (!filled && s.getBoolean("targetSet")) {
            int[] t = s.getIntArray("target");
            if (t.length == 4) {
                fx.setText(String.valueOf(t[0]));
                fy.setText(t[1] == TileEntityBridgeControllerSC.AUTO_Y ? "" : String.valueOf(t[1]));
                fz.setText(String.valueOf(t[2]));
                dim = t[3];
                lastSent = targetKey();
            }
        }
        if (!filled) {
            if (!s.getBoolean("targetSet")) {
                dim = s.getInteger("dimHere");
            }
            filled = true;
        }
        if (s.hasKey("hl")) {
            BridgeHighlightSC.set(s.getIntArray("hl"), s.getInteger("hlTicks"));
        }
    }

    // ------------------------------------------------------------------ set-up

    @Override
    public void initGui() {
        k = Math.min(1F, Math.min(width / (float) W, height / (float) H));
        left = Math.round((width - W * k) / 2F);
        top = Math.round((height - H * k) / 2F);
        Keyboard.enableRepeatEvents(true);
        String ox = fx == null ? "" : fx.getText(), oy = fy == null ? "" : fy.getText(), oz = fz == null ? "" : fz.getText();
        fx = field(118, 59, 48, ox);
        fy = field(178, 59, 36, oy);
        fz = field(226, 59, 48, oz);
        fname = field(110, BM_Y, 190, "");
        fname.setMaxStringLength(32);
        fname.setVisible(false);
        String of = ffriend == null ? "" : ffriend.getText();
        ffriend = field(206, 256, 92, of);
        ffriend.setMaxStringLength(16);
        fbridge = field(8, 4, 150, "");
        fbridge.setMaxStringLength(32);
        fbridge.setVisible(false);
        btns.clear();
        btns.add(new Btn(B_CHECK, 8, 162, 44, 12, "sc.bridge.gui.check"));
        btns.add(new Btn(B_CALIB, 56, 162, 44, 12, "sc.bridge.gui.calibrate"));
        btns.add(new Btn(B_POWER, 8, 216, 92, 12, ""));
        btns.add(new Btn(B_REPAIR, 8, 230, 92, 11, "sc.bridge.gui.repair"));
        for (int i = 0; i < MODES; i++) {
            btns.add(new Btn(B_MODE + i, 110 + (i % 3) * 64, 33 + (i / 3) * 13, 62, 11, "sc.bridge.gui.mode." + i));
        }
        btns.add(new Btn(B_DIM, 150, 73, 80, 11, ""));
        btns.add(new Btn(B_FROM_COORD, 232, 73, 34, 11, "sc.bridge.gui.fromcoord"));
        btns.add(new Btn(B_TO_COORD, 268, 73, 34, 11, "sc.bridge.gui.tocoord"));
        btns.add(new Btn(B_PROBE, 110, 86, 124, 12, "sc.bridge.gui.probe"));
        btns.add(new Btn(B_TAKE, 238, 86, 64, 12, "sc.bridge.gui.take"));
        btns.add(new Btn(B_BM_ADD, 254, 120, 14, 10, ""));
        btns.add(new Btn(B_BM_REN, 270, 120, 14, 10, ""));
        btns.add(new Btn(B_BM_DEL, 286, 120, 14, 10, ""));
        btns.add(new Btn(B_OPEN, 110, 228, 94, 10, "sc.bridge.gui.open"));
        btns.add(new Btn(B_CLOSE, 208, 228, 94, 10, "sc.bridge.gui.close"));
        for (int i = 0; i < BridgeMathSC.GASES.length; i++) {
            btns.add(new Btn(B_CLEAR + i, 312 + i * 17 + 2, 104, 7, 7, ""));
        }
        btns.add(new Btn(B_FRIEND_ADD, 300, 256, 12, 11, ""));
        btns.add(new Btn(B_ACCESS, 316, 256, 96, 11, ""));
        btns.add(new Btn(B_REMOTE, 206, 286, 102, 11, ""));
        btns.add(new Btn(B_BIND, 312, 286, 100, 11, "sc.bridge.gui.bindhelmet"));
        btns.add(new Btn(B_RENAME, 270, 4, 12, 10, ""));
        request();
    }

    private GuiTextField field(int x, int y, int w, String text) {
        GuiTextField f = new GuiTextField(fontRendererObj, x, y, w, 12);
        f.setMaxStringLength(9);
        f.setText(text);
        return f;
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void request() {
        BridgeNetSC.send(cx, cy, cz, BridgeNetSC.A_REQUEST, null, null);
    }

    private void act(int a, int[] v, String s) {
        BridgeNetSC.send(cx, cy, cz, a, v, s);
    }

    @Override
    public void updateScreen() {
        ffriend.updateCursorCounter();
        fbridge.updateCursorCounter();
        fx.updateCursorCounter();
        fy.updateCursorCounter();
        fz.updateCursorCounter();
        fname.updateCursorCounter();
        if (++reqTimer >= 10) {
            reqTimer = 0;
            request();
        }
        if (++sendTimer >= 5) {
            sendTimer = 0;
            int[] t = target();
            String key = targetKey();
            if (t != null && !key.equals(lastSent)) {
                lastSent = key;
                act(TileEntityBridgeControllerSC.A_TARGET, t, null);
            }
        }
        if (mc.thePlayer != null && mc.thePlayer.getDistanceSq(cx + 0.5, cy + 0.5, cz + 0.5) > BridgeNetSC.REACH * BridgeNetSC.REACH) {
            mc.displayGuiScreen(null);
        }
    }

    // ------------------------------------------------------------------ the state

    private boolean has() {
        return st != null;
    }

    private int[] ints(String key, int n) {
        int[] a = st == null ? new int[0] : st.getIntArray(key);
        return a.length >= n ? a : new int[n];
    }

    private boolean space() {
        return has() && st.getInteger("kind") == BridgeMathSC.SPACE;
    }

    private boolean allowed() {
        return has() && st.getBoolean("allowed");
    }

    /** The typed target, or null when X / Z aren't numbers. */
    private int[] target() {
        try {
            int x = Integer.parseInt(fx.getText().trim().replace(" ", ""));
            int z = Integer.parseInt(fz.getText().trim().replace(" ", ""));
            String ys = fy.getText().trim();
            int y = ys.length() == 0 || ys.equalsIgnoreCase("auto") || ys.equalsIgnoreCase(Lang.tr("sc.bridge.gui.auto"))
                    ? TileEntityBridgeControllerSC.AUTO_Y : Integer.parseInt(ys);
            return new int[]{x, y, z, dim};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String targetKey() {
        int[] t = target();
        return t == null ? "" : t[0] + "," + t[1] + "," + t[2] + "," + t[3];
    }

    // ------------------------------------------------------------------ input

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        int x = vx(mx), y = vy(my);
        fx.mouseClicked(x, y, button);
        fy.mouseClicked(x, y, button);
        fz.mouseClicked(x, y, button);
        ffriend.mouseClicked(x, y, button);
        if (renamingBridge) {
            fbridge.mouseClicked(x, y, button);
            if (!fbridge.isFocused()) {
                renamingBridge = false;
                fbridge.setVisible(false);
            }
        }
        for (int[] c : chips) {
            if (x >= c[0] && x < c[0] + c[2] && y >= c[1] && y < c[1] + 8 && has() && st.getBoolean("isOwner")) {
                long now = System.currentTimeMillis();
                if (armedFriend == c[3] && now - armedFriendAt < 2000) {
                    act(TileEntityBridgeControllerSC.A_FRIEND_DEL, new int[]{c[3]}, null);
                    armedFriend = -1;
                } else {
                    armedFriend = c[3];
                    armedFriendAt = now;
                }
                return;
            }
        }
        if (renaming >= 0) {
            fname.mouseClicked(x, y, button);
            if (!fname.isFocused()) {
                renaming = -1;
                fname.setVisible(false);
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
        // the bookmark rows
        NBTTagList bm = bookmarks();
        for (int r = 0; r < BM_ROWS; r++) {
            int i = bmScroll + r;
            if (i < bm.tagCount() && x >= 110 && x < 302 && y >= BM_Y + r * BM_ROW_H && y < BM_Y + (r + 1) * BM_ROW_H) {
                selBm = i;
                int[] p = bm.getCompoundTagAt(i).getIntArray("p");
                if (p.length == 4) {
                    fx.setText(String.valueOf(p[0]));
                    fy.setText(p[1] == TileEntityBridgeControllerSC.AUTO_Y ? "" : String.valueOf(p[1]));
                    fz.setText(String.valueOf(p[2]));
                    if (space()) {
                        dim = p[3];
                    }
                }
                return;
            }
        }
    }

    private void press(int id, int button) {
        int[] t = target();
        switch (id) {
            case B_CHECK: act(TileEntityBridgeControllerSC.A_CHECK, null, null); return;
            case B_CALIB: act(TileEntityBridgeControllerSC.A_CALIBRATE, null, null); return;
            case B_REPAIR: act(TileEntityBridgeControllerSC.A_REPAIR, null, null); return;
            case B_POWER: act(TileEntityBridgeControllerSC.A_POWER, null, null); return;
            case B_PROBE:
                if (t != null) {
                    act(TileEntityBridgeControllerSC.A_PROBE, t, null);
                    lastSent = targetKey();
                }
                return;
            case B_TAKE: {
                int[] n = has() && st.hasKey("place") ? st.getCompoundTag("place").getIntArray("near") : new int[0];
                if (n.length == 4) {
                    fx.setText(String.valueOf(n[0]));
                    fy.setText(String.valueOf(n[1]));
                    fz.setText(String.valueOf(n[2]));
                }
                return;
            }
            case B_BM_ADD:
                if (t != null) {
                    act(TileEntityBridgeControllerSC.A_BM_ADD, t, Lang.tr("sc.bridge.gui.bm.default", bookmarks().tagCount() + 1));
                }
                return;
            case B_BM_REN:
                if (selBm >= 0 && selBm < bookmarks().tagCount()) {
                    renaming = selBm;
                    fname.setText(bookmarks().getCompoundTagAt(selBm).getString("n"));
                    fname.yPosition = BM_Y + (selBm - bmScroll) * BM_ROW_H - 2;
                    fname.setVisible(true);
                    fname.setFocused(true);
                }
                return;
            case B_BM_DEL:
                if (selBm >= 0 && selBm < bookmarks().tagCount()) {
                    act(TileEntityBridgeControllerSC.A_BM_DELETE, new int[]{selBm}, null);
                    selBm = -1;
                }
                return;
            case B_OPEN:
                if (t != null) {
                    act(TileEntityBridgeControllerSC.A_OPEN, t, null);
                    lastSent = targetKey();
                }
                return;
            case B_CLOSE: act(TileEntityBridgeControllerSC.A_CLOSE, null, null); return;
            case B_FRIEND_ADD:
                if (ffriend.getText().trim().length() > 0) {
                    act(TileEntityBridgeControllerSC.A_FRIEND_ADD, null, ffriend.getText().trim());
                    ffriend.setText("");
                }
                return;
            case B_ACCESS: act(TileEntityBridgeControllerSC.A_ACCESS, null, null); return;
            case B_REMOTE: act(TileEntityBridgeControllerSC.A_REMOTE_MODE, null, null); return;
            case B_BIND: act(TileEntityBridgeControllerSC.A_BIND_HELMET, null, null); return;
            case B_RENAME:
                renamingBridge = true;
                fbridge.setText(has() ? st.getString("name") : "");
                fbridge.setVisible(true);
                fbridge.setFocused(true);
                return;
            case B_FROM_COORD: act(TileEntityBridgeControllerSC.A_FROM_COORD, null, null); return;
            case B_TO_COORD:
                if (button == 1) {
                    act(TileEntityBridgeControllerSC.A_COPY_COORD, null, null);
                } else if (t != null) {
                    int bm = selBm;
                    String n = bm >= 0 && bm < bookmarks().tagCount() ? bookmarks().getCompoundTagAt(bm).getString("n") : "";
                    act(TileEntityBridgeControllerSC.A_TO_COORD, t, n);
                }
                return;
            case B_DIM: {
                NBTTagList d = has() ? st.getTagList("dims", 10) : new NBTTagList();
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
                return;
            }
            default:
                break;
        }
        if (id >= B_CLEAR && id < B_CLEAR + BridgeMathSC.GASES.length) {
            int i = id - B_CLEAR;
            long now = System.currentTimeMillis();
            if (armedClear == i && now - armedAt < 2000) {
                act(TileEntityBridgeControllerSC.A_CLEAR, new int[]{i}, null);
                armedClear = -1;
            } else {
                armedClear = i;
                armedAt = now;
            }
        }
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (renamingBridge) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                act(TileEntityBridgeControllerSC.A_NAME, null, fbridge.getText());
                renamingBridge = false;
                fbridge.setVisible(false);
            } else if (key == Keyboard.KEY_ESCAPE) {
                renamingBridge = false;
                fbridge.setVisible(false);
            } else {
                fbridge.textboxKeyTyped(c, key);
            }
            return;
        }
        if (ffriend.isFocused()) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                press(B_FRIEND_ADD, 0);
                return;
            }
            if (key != Keyboard.KEY_ESCAPE) {
                ffriend.textboxKeyTyped(c, key);
                return;
            }
        }
        if (renaming >= 0) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                act(TileEntityBridgeControllerSC.A_BM_RENAME, new int[]{renaming}, fname.getText());
                renaming = -1;
                fname.setVisible(false);
            } else if (key == Keyboard.KEY_ESCAPE) {
                renaming = -1;
                fname.setVisible(false);
            } else {
                fname.textboxKeyTyped(c, key);
            }
            return;
        }
        boolean typed = false;
        for (GuiTextField f : new GuiTextField[]{fx, fy, fz}) {
            if (f.isFocused()) {
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
                }
                typed = true;
            }
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
            int max = Math.max(0, bookmarks().tagCount() - BM_ROWS);
            bmScroll = Math.max(0, Math.min(max, bmScroll + (wheel > 0 ? -1 : 1)));
        }
    }

    private int vx(int mx) {
        return (int) Math.floor((mx - left) / k);
    }

    private int vy(int my) {
        return (int) Math.floor((my - top) / k);
    }

    private NBTTagList bookmarks() {
        return has() ? st.getTagList("bookmarks", 10) : new NBTTagList();
    }

    // ------------------------------------------------------------------ drawing helpers

    private static void rect(int x, int y, int w, int h, int c) {
        if (w > 0 && h > 0) {
            Gui.drawRect(x, y, x + w, y + h, c);
        }
    }

    private static void frame(int x, int y, int w, int h, int fill, int edge) {
        rect(x, y, w, h, edge);
        rect(x + 1, y + 1, w - 2, h - 2, fill);
    }

    private void text(String s, int x, int y, int color) {
        fontRendererObj.drawString(s, x, y, color);
    }

    /** Shrinks (to 75%) or cuts a text to maxW. */
    private void fit(String s, int x, int y, int maxW, int color) {
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

    /** Small (75%) text cut to maxW. */
    private void small(String s, int x, int y, int maxW, int color) {
        float f = 0.75F;
        String shown = s;
        if (fontRendererObj.getStringWidth(s) * f > maxW) {
            shown = fontRendererObj.trimStringToWidth(s, (int) (maxW / f) - fontRendererObj.getStringWidth("...")) + "...";
        }
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0F);
        GL11.glScalef(f, f, 1F);
        text(shown, 0, 0, color);
        GL11.glPopMatrix();
    }

    private void smallRight(String s, int right, int y, int color) {
        int w = (int) Math.ceil(fontRendererObj.getStringWidth(s) * 0.75F);
        small(s, right - w, y, 400, color);
    }

    private void centered(String s, int x, int y, int w, int color) {
        int sw = fontRendererObj.getStringWidth(s);
        if (sw > w) {
            fit(s, x, y, w, color);
        } else {
            text(s, x + (w - sw) / 2, y, color);
        }
    }

    private static void bar(int x, int y, int w, int h, float f, int color) {
        rect(x, y, w, h, 0xFF4A504C);
        rect(x + 1, y + 1, w - 2, h - 2, 0xFF0A0C0B);
        rect(x + 1, y + 1, Math.round((w - 2) * Math.max(0F, Math.min(1F, f))), h - 2, color);
    }

    private String eu(long v) {
        return BridgeMathSC.shortEu(v, Lang.tr("sc.bridge.unit.m"), Lang.tr("sc.bridge.unit.g"));
    }

    private static String g(long v) {
        return BridgeMathSC.group(v);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void drawScreen(int mouseX, int mouseY, float partial) {
        drawDefaultBackground();
        vmx = vx(mouseX);
        vmy = vy(mouseY);
        refreshButtons();
        GL11.glPushMatrix();
        GL11.glTranslatef(left, top, 0F);
        GL11.glScalef(k, k, 1F);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        drawBack();
        if (has()) {
            drawLeft();
            drawCentre();
            drawRight();
            drawBottom();
        } else {
            centered(Lang.tr("sc.bridge.gui.loading"), 0, H / 2 - 4, W, LABEL);
        }
        for (Btn b : btns) {
            b.draw();
        }
        fx.drawTextBox();
        fy.drawTextBox();
        fz.drawTextBox();
        if (fy.getText().length() == 0 && !fy.isFocused()) {
            text(Lang.tr("sc.bridge.gui.auto"), 182, 61, OK);
        }
        if (renaming >= 0) {
            fname.drawTextBox();
        }
        if (has()) {
            ffriend.drawTextBox();
            if (ffriend.getText().length() == 0 && !ffriend.isFocused()) {
                small(Lang.tr("sc.bridge.gui.friend.hint"), 209, 258, 88, DIM);
            }
        }
        if (renamingBridge) {
            rect(6, 3, 260, 13, BG);
            fbridge.drawTextBox();
        }
        List<String> tip = has() ? tip() : null;
        GL11.glPopMatrix();
        if (tip != null && !tip.isEmpty()) {
            drawHoveringText(GuiGaugeSC.wrapTooltip(fontRendererObj, tip, width), mouseX, mouseY, fontRendererObj);
        }
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    private void refreshButtons() {
        boolean a = allowed(), open = has() && st.getBoolean("open");
        for (Btn b : btns) {
            b.enabled = a;
            b.visible = true;
            b.selected = false;
            if (b.id == B_CHECK) {
                b.enabled = has();
            } else if (b.id == B_POWER) {
                boolean on = has() && st.getBoolean("power");
                b.label = Lang.tr(on ? "sc.bridge.gui.power.on" : "sc.bridge.gui.power.off");
                b.color = on ? OK : BAD;
            } else if (b.id >= B_MODE && b.id < B_MODE + MODES) {
                b.enabled = false;
                b.selected = b.id == B_MODE;                    // the controller opens «С базы»; ДР1-ДР5 - remotes and the armour
                b.color = b.selected ? OK : TEXT;
            } else if (b.id == B_DIM) {
                b.visible = space();
                b.label = dimName(dim) + " ▼";
            } else if (b.id == B_TAKE) {
                b.enabled = a && has() && st.hasKey("place") && st.getCompoundTag("place").getIntArray("near").length == 4;
            } else if (b.id == B_BM_REN || b.id == B_BM_DEL) {
                b.enabled = a && selBm >= 0 && selBm < bookmarks().tagCount();
                b.label = b.id == B_BM_REN ? "✎" : "✕";
            } else if (b.id == B_BM_ADD) {
                b.enabled = a && bookmarks().tagCount() < (has() ? st.getInteger("bmMax") : 8) && target() != null;
                b.label = "+";
            } else if (b.id == B_OPEN) {
                b.enabled = a && !open && target() != null;
                b.color = OK;
            } else if (b.id == B_CLOSE) {
                b.enabled = a && open;
            } else if (b.id >= B_CLEAR && b.id < B_CLEAR + BridgeMathSC.GASES.length) {
                int i = b.id - B_CLEAR;
                b.enabled = a && ints("tanks", BridgeMathSC.GASES.length)[i] > 0;
                b.clear = true;
                b.selected = armedClear == i && System.currentTimeMillis() - armedAt < 2000;
            } else if (b.id == B_ACCESS) {
                boolean pub = has() && st.getInteger("access") == BridgeMathSC.ACCESS_PUBLIC;
                b.enabled = has() && st.getBoolean("isOwner");
                b.label = Lang.tr(pub ? "sc.bridge.gui.access.public" : "sc.bridge.gui.access.friends");
                b.color = pub ? WARN : OK;
            } else if (b.id == B_FRIEND_ADD) {
                b.enabled = has() && st.getBoolean("isOwner") && ffriend.getText().trim().length() > 1;
                b.label = "+";
            } else if (b.id == B_REMOTE) {
                boolean on = has() && st.getBoolean("remoteMode");
                b.enabled = has() && st.getBoolean("trusted");
                b.label = Lang.tr(on ? "sc.bridge.gui.remote.on" : "sc.bridge.gui.remote.off");
                b.color = on ? BLUE : TEXT;
            } else if (b.id == B_BIND) {
                b.enabled = has() && st.getBoolean("trusted");
            } else if (b.id == B_RENAME) {
                b.enabled = has() && st.getBoolean("isOwner");
                b.label = "✎";
            } else if (b.id == B_FROM_COORD) {
                b.enabled = a && has() && st.getInteger("coords") > 0;
            } else if (b.id == B_TO_COORD) {
                b.enabled = a && has() && st.getInteger("coords") > 0 && target() != null;
            } else if (b.id == B_PROBE) {
                b.enabled = a && target() != null;
                b.color = WARN;
            } else if (b.id == B_CALIB) {
                b.color = has() && st.getBoolean("calibrated") ? TEXT : WARN;
            } else if (b.id == B_REPAIR) {
                int wear = has() ? st.getInteger("wear") : 0;
                b.enabled = a && !open && wear > 0;
                b.label = wear > 0 ? Lang.tr("sc.bridge.gui.repair.n", wear) : Lang.tr("sc.bridge.gui.repair");
                b.color = wear > BridgeMathSC.WEAR_FREE ? WARN : TEXT;
            }
        }
    }

    private String dimName(int id) {
        NBTTagList d = has() ? st.getTagList("dims", 10) : new NBTTagList();
        for (int i = 0; i < d.tagCount(); i++) {
            if (d.getCompoundTagAt(i).getInteger("id") == id) {
                return d.getCompoundTagAt(i).getString("n");
            }
        }
        return "DIM " + id;
    }

    private void drawBack() {
        rect(0, 0, W, H, 0xFF060807);
        rect(1, 1, W - 2, H - 2, EDGE);
        rect(2, 2, W - 4, H - 4, BG);
        boolean sp = space();
        String title = !has() || !st.getBoolean("found") ? Lang.tr("sc.bridge.gui.title")
                : Lang.tr(sp ? "sc.bridge.gui.title.space" : "sc.bridge.gui.title.ground");
        if (has() && st.getString("name").length() > 0) {
            title = title + " «" + st.getString("name") + "»";
        }
        fit(title, 8, 6, 258, sp ? BLUE : OK);
        if (has() && st.getString("owner").length() > 0) {
            String o = Lang.tr("sc.bridge.gui.owner", st.getString("owner"));
            smallRight(o, W - 8, 7, LABEL);
        }
        rect(4, 16, W - 8, 1, EDGE);
        frame(106, 19, 200, 222, PANEL, EDGE_HI);
        rect(4, 243, W - 8, 1, EDGE);
    }

    private void drawLeft() {
        text(Lang.tr("sc.bridge.gui.build"), 8, 21, LABEL);
        frame(8, 30, 92, 92, 0xFF0A0D0C, EDGE);
        byte[] cells = st.getByteArray("cells");
        int size = st.getInteger("size"), g = size + 2, axis = st.getInteger("axis");
        if (cells.length == g * g && g > 2) {
            int cs = Math.min(12, 86 / g), gap = 1, total = g * cs, ox = 8 + (92 - total) / 2, oy = 30 + (92 - total) / 2;
            int[] hl = st.hasKey("hl") ? st.getIntArray("hl") : null;
            boolean blink = (System.currentTimeMillis() / 300) % 2 == 0, open = st.getBoolean("open"), sp = space();
            int h = (size - 1) / 2;
            for (int r = 0; r < g; r++) {
                for (int c = 0; c < g; c++) {
                    int col;
                    switch (cells[r * g + c]) {
                        case BridgeStructureSC.C_COIL: col = open ? 0xFF9A7AE0 : 0xFF5A5470; break;
                        case BridgeStructureSC.C_COIL_MISSING: col = 0xFFC83C3C; break;
                        case BridgeStructureSC.C_INSIDE: col = 0xFF141E18; break;
                        case BridgeStructureSC.C_JUNK: col = 0xFFD07030; break;
                        case BridgeStructureSC.C_FOCUSER: col = 0xFFC050E0; break;
                        case BridgeStructureSC.C_FOCUSER_MISSING: col = 0xFF8A2A2A; break;
                        case BridgeStructureSC.C_CONTROLLER: col = 0xFF3CC86E; break;
                        case BridgeStructureSC.C_VORTEX: col = sp ? 0xFF6AA8FF : 0xFF2ECC6A; break;
                        default: col = 0;
                    }
                    if (col == 0) {
                        continue;
                    }
                    int px = ox + c * cs, py = oy + r * cs;
                    if (hl != null && hl.length == 3) {
                        int[] w = BridgeStructureSC.at(cx, cy, cz, axis, c - h - 1, g - 1 - r, 0);
                        if (w[0] == hl[0] && w[1] == hl[1] && w[2] == hl[2] && blink) {
                            col = 0xFFFFFFFF;
                        }
                    }
                    rect(px, py, cs - gap, cs - gap, col);
                }
            }
            // the vortex inside (one block of green / blue) when open
            if (open) {
                int in = (size - 2) * cs;
                int ix = ox + 2 * cs, iy = oy + 2 * cs;
                rect(ix, iy, in - gap, in - gap, (sp ? 0x406AA8FF : 0x402ECC6A));
            }
        }
        int[] n = ints("counts", 9);
        int y = 126;
        small(Lang.tr("sc.bridge.gui.coils", n[0], n[1]) + (space() ? "  " + Lang.tr("sc.bridge.gui.focusers", n[2], n[3]) : ""), 8, y, 92,
                n[0] == n[1] && n[2] == n[3] ? OK : LABEL);
        NBTTagList pr = st.getTagList("problems", 10);
        for (int i = 0; i < 2 && i < pr.tagCount(); i++) {
            small(BridgeMsgSC.read(pr.getCompoundTagAt(i)).text(), 8, y + 8 + i * 8, 92, BAD);
        }
        if (pr.tagCount() > 2) {
            small(Lang.tr("sc.bridge.gui.more", pr.tagCount() > 6 ? st.getInteger("problemCount") - 2 : pr.tagCount() - 2), 8, y + 24, 92, BAD);
        } else if (pr.tagCount() == 0 && st.getBoolean("valid")) {
            small(Lang.tr("sc.bridge.gui.buildok"), 8, y + 8, 92, OK);
        }
        boolean cal = st.getBoolean("calibrated");
        small(Lang.tr(cal ? "sc.bridge.gui.calib.ok" : "sc.bridge.gui.calib.need"), 8, 153, 92, cal ? OK : WARN);
        text(Lang.tr("sc.bridge.gui.ring"), 8, 180, LABEL);
        int[] t = ints("time", 6);
        boolean open = st.getBoolean("open");
        int heatPct = st.getInteger("heat") / 10, wear = st.getInteger("wear");
        float heat = heatPct / 100F;
        small(Lang.tr("sc.bridge.gui.heat"), 8, 190, 30, LABEL);
        bar(36, 189, 46, 6, heat, heatPct >= 75 ? 0xFFE04830 : heatPct >= 40 ? 0xFFF0A040 : 0xFF6AA8FF);
        smallRight(heatPct + "%", 100, 189, heatPct * 10 >= BridgeMathSC.HEAT_WARN ? BAD : TEXT);
        small(Lang.tr("sc.bridge.gui.wear"), 8, 199, 30, LABEL);
        bar(36, 198, 46, 6, 1F - wear / 100F, wear > BridgeMathSC.WEAR_FREE ? 0xFFF0A040 : 0xFF5AE66E);
        smallRight(wear + "%", 100, 198, wear > BridgeMathSC.WEAR_FREE ? WARN : TEXT);
        boolean oh = st.getBoolean("overheat");
        String cool = open ? Lang.tr("sc.bridge.gui.cool.open") : oh ? Lang.tr("sc.bridge.gui.cool.overheat", (t[2] + 19) / 20)
                : t[2] > 0 ? Lang.tr("sc.bridge.gui.cool.left", (t[2] + 19) / 20) : Lang.tr("sc.bridge.gui.cool.ready");
        small(cool, 8, 207, 92, open ? BLUE : oh ? BAD : t[2] > 0 ? WARN : OK);
    }

    private void drawCentre() {
        centered(Lang.tr("sc.bridge.gui.target"), 106, 23, 200, TEXT);
        text("X", 110, 61, LABEL);
        text("Y", 170, 61, LABEL);
        text("Z", 218, 61, LABEL);
        if (space()) {
            small(Lang.tr("sc.bridge.gui.dim"), 110, 75, 38, LABEL);
        } else {
            small(Lang.tr("sc.bridge.gui.yauto"), 110, 75, 120, DIM);
        }
        // the place check's answer
        if (st.hasKey("place")) {
            NBTTagCompound p = st.getCompoundTag("place");
            int[] at = p.getIntArray("at");
            String[] args = BridgeMsgSC.read(p.getCompoundTag("args")).args;
            Object[] a = new Object[args.length];
            System.arraycopy(args, 0, a, 0, args.length);
            if (p.getBoolean("free") && at.length == 4) {
                fit(Lang.tr(p.getBoolean("air") ? "sc.bridge.gui.place.freeair" : "sc.bridge.gui.place.free", at[0], at[1], at[2]), 110, 101, 192, OK);
                if (p.getBoolean("void")) {
                    small(Lang.tr("sc.bridge.gui.place.void", g(p.getLong("dist"))), 110, 111, 192, WARN);
                } else {
                    small(Lang.tr("sc.bridge.gui.place.freeinfo", p.getInteger("w"), p.getInteger("w"), g(p.getLong("dist")))
                            + (p.getBoolean("beacon") ? "  " + Lang.tr("sc.bridge.gui.beacon", BridgeMathSC.BEACON_DISCOUNT) : ""), 110, 111, 192, LABEL);
                }
            } else {
                String why = a.length == 0 ? Lang.tr(p.getString("reason")) : Lang.tr(p.getString("reason"), a);
                fit(Lang.tr("sc.bridge.gui.place.blocked", why), 110, 101, 192, BAD);
                int[] n = p.getIntArray("near");
                if (n.length == 4) {
                    small(Lang.tr("sc.bridge.gui.place.near", n[0], n[1], n[2], n[3]), 110, 111, 192, WARN);
                } else {
                    small(Lang.tr("sc.bridge.gui.place.nonear"), 110, 111, 192, DIM);
                }
            }
        } else {
            small(Lang.tr("sc.bridge.gui.place.hint"), 110, 104, 192, DIM);
        }
        // the bookmarks
        NBTTagList bm = bookmarks();
        text(Lang.tr("sc.bridge.gui.bookmarks", bm.tagCount(), st.getInteger("bmMax")), 110, 121, LABEL);
        for (int r = 0; r < BM_ROWS; r++) {
            int i = bmScroll + r, y = BM_Y + r * BM_ROW_H;
            rect(110, y, 192, BM_ROW_H - 1, i == selBm ? 0xFF22402C : (r % 2 == 0 ? 0xFF141A17 : 0xFF111613));
            if (i >= bm.tagCount()) {
                if (bm.tagCount() == 0 && r == 0) {
                    small(Lang.tr("sc.bridge.gui.bm.none"), 113, y + 2, 186, DIM);
                }
                continue;
            }
            if (i == renaming) {
                continue;
            }
            NBTTagCompound b = bm.getCompoundTagAt(i);
            int[] p = b.getIntArray("p");
            small(b.getString("n"), 113, y + 2, 90, TEXT);
            if (p.length == 4) {
                String pos = p[0] + " " + (p[1] == TileEntityBridgeControllerSC.AUTO_Y ? Lang.tr("sc.bridge.gui.auto") : String.valueOf(p[1])) + " " + p[2];
                if (space() && p[3] != st.getInteger("dimHere")) {
                    pos += " · " + dimName(p[3]);
                }
                smallRight(pos, 299, y + 2, LABEL);
            }
        }
        if (bm.tagCount() > BM_ROWS) {
            smallRight((bmScroll + 1) + "-" + Math.min(bm.tagCount(), bmScroll + BM_ROWS) + "/" + bm.tagCount(), 250, 122, DIM);
        }
        // the target summary and the cost
        int[] c = ints("cost", 11);
        long dist = st.getLong("dist");
        String sum = space() ? Lang.tr(c[10] != 0 ? "sc.bridge.gui.sum.anchor" : "sc.bridge.gui.sum.noanchor", dimName(ints("target", 4)[3]))
                : Lang.tr("sc.bridge.gui.sum.ground", g(dist)) + (c[9] != 0 ? "  " + Lang.tr("sc.bridge.gui.beacon", BridgeMathSC.BEACON_DISCOUNT) : "");
        String fam = famText(ints("fam", 4));
        if (fam.length() > 0) {
            sum += "  " + fam;
        }
        small(sum, 110, 170, 192, ints("fam", 4)[0] == 0 ? WARN : c[9] != 0 || c[10] != 0 ? OK : LABEL);
        text(Lang.tr("sc.bridge.gui.cost"), 110, 178, LABEL);
        long capEu = st.getLong("capEu"), costEu = st.getLong("costEu");
        int[] tk = ints("tanks", BridgeMathSC.GASES.length);
        costRow(0, Lang.tr("sc.bridge.res.eu"), eu(costEu) + " / " + eu(capEu) + " EU", capEu >= costEu);
        int[] idx = {BridgeMathSC.SM, BridgeMathSC.D, BridgeMathSC.KR, BridgeMathSC.AR};
        int[] need = {c[0], c[1], c[2], c[3]};
        for (int i = 0; i < 4; i++) {
            costRow(i + 1, Lang.tr(BridgeMsgSC.RES + "gas." + BridgeMathSC.GASES[idx[i]].key()), g(need[i]) + " / " + g(tk[idx[i]]) + " " + Lang.tr("sc.bridge.unit.mb"),
                    tk[idx[i]] >= need[i]);
        }
        small(Lang.tr(space() ? "sc.bridge.gui.hold.space" : "sc.bridge.gui.hold.ground", g(c[4]), c[5], c[6], c[7], c[8] / 20), 110, 221, 192, DIM);
    }

    private void costRow(int r, String name, String value, boolean ok) {
        int y = 187 + r * 7;
        rect(111, y, 4, 4, ok ? 0xFF5AE66E : 0xFFE05040);
        small(name, 118, y, 80, TEXT);
        smallRight(value, 301, y, ok ? TEXT : BAD);
    }

    private void drawRight() {
        int[] n = ints("counts", 9);
        text(Lang.tr("sc.bridge.gui.capacitors", n[4]), 312, 21, LABEL);
        long capEu = st.getLong("capEu"), capMax = st.getLong("capMax");
        GuiEnergyGaugeSC.draw(312, 30, 22, 60, capMax <= 0 ? 0F : capEu / (float) capMax);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        fit(eu(capEu), 338, 33, 74, TEXT);
        small("/ " + eu(capMax) + " EU", 338, 44, 74, LABEL);
        long rate = st.getLong("rate");
        small(rate > 0 ? Lang.tr("sc.bridge.gui.rate", eu(rate)) : Lang.tr("sc.bridge.gui.norate"), 338, 54, 74, rate > 0 ? OK : DIM);
        small(Lang.tr("sc.bridge.gui.ports", n[5], eu(st.getLong("portEu"))), 338, 64, 74, LABEL);
        small(Lang.tr("sc.bridge.gui.gasports", n[6]), 338, 73, 74, LABEL);
        text(Lang.tr("sc.bridge.gui.tanks"), 312, 95, LABEL);
        int[] tk = ints("tanks", BridgeMathSC.GASES.length), caps = ints("tankCaps", BridgeMathSC.GASES.length);
        for (int i = 0; i < BridgeMathSC.GASES.length; i++) {
            int x = 312 + i * 17;
            Fluid f = BridgeMathSC.GASES[i].fluidOf();
            GuiTankGaugeSC.drawCompact(mc, x, 112, 12, 50, f == null || tk[i] <= 0 ? null : new FluidStack(f, tk[i]), caps[i], false);
            GL11.glColor4f(1F, 1F, 1F, 1F);
            String s = GasUiSC.shortName(BridgeMathSC.GASES[i]);
            int w = (int) (fontRendererObj.getStringWidth(s) * 0.75F);
            small(s, x + 6 - w / 2, 165, 20, BridgeMathSC.GASES[i].color);
        }
        text(Lang.tr("sc.bridge.gui.stability"), 312, 176, LABEL);
        int[] t = ints("time", 6);
        boolean open = st.getBoolean("open");
        int stab = open ? t[4] : ints("stab", 7)[6];
        boolean shake = open && st.getBoolean("turb") && (System.currentTimeMillis() / 90) % 2 == 0;
        bar(312, 186 + (shake ? 1 : 0), 80, 6, stab / 100F, stab >= 70 ? 0xFF5AE66E : stab >= BridgeMathSC.TURBULENCE ? 0xFFF0A040 : 0xFFE04830);
        smallRight(stab + "%", 412, 186, stab < BridgeMathSC.TURBULENCE ? BAD : TEXT);
        int mods = st.getInteger("modules");
        StringBuilder m = new StringBuilder();
        String[] mk = {"nav", "mass", "cooler", "shield"};
        for (int i = 0; i < 4; i++) {
            if ((mods & 1 << i) != 0) {
                m.append(m.length() > 0 ? ", " : "").append(Lang.tr("sc.bridge.gui.mod." + mk[i]));
            }
        }
        small(Lang.tr("sc.bridge.gui.stabs", n[7], BridgeMathSC.MAX_STABILISERS), 312, 195, 100, LABEL);
        small(Lang.tr("sc.bridge.gui.modules", m.length() == 0 ? Lang.tr("sc.bridge.gui.none") : m.toString()), 312, 202, 100, LABEL);
        int[] env = ints("env", 3);
        StringBuilder e = new StringBuilder();
        if (env[2] != 0) {
            e.append(Lang.tr("sc.bridge.gui.env.res", BridgeMathSC.RESONANCE_PCT));
        }
        if (env[1] != 0) {
            e.append(e.length() > 0 ? " · " : "").append(Lang.tr("sc.bridge.gui.env.storm", BridgeMathSC.STORM_STAB));
        }
        if (env[0] != 0) {
            e.append(e.length() > 0 ? " · " : "").append(Lang.tr("sc.bridge.gui.env.interf", BridgeMathSC.INTERFERENCE_STAB));
        }
        small(e.length() == 0 ? Lang.tr("sc.bridge.gui.env.none") : e.toString(), 312, 209, 100, env[0] != 0 || env[1] != 0 ? WARN : env[2] != 0 ? OK : DIM);
        if (open) {
            small(Lang.tr("sc.bridge.gui.openleft", (t[0] + 19) / 20), 312, 217, 100, BLUE);
            int[] b = ints("endB", 4);
            int[] sc = ints("scatter", 2);
            small(Lang.tr("sc.bridge.gui.endb", b[1], b[2], b[3]) + (sc[1] > 0 ? " " + Lang.tr("sc.bridge.gui.shifted", sc[1]) : ""), 312, 224, 100, LABEL);
            if (t[5] >= 0) {
                small(Lang.tr("sc.bridge.gui.short", (t[5] + 19) / 20), 312, 231, 100, BAD);
            } else if (st.getBoolean("turb")) {
                small(Lang.tr("sc.bridge.gui.turb", BridgeMathSC.TURB_SHIFT), 312, 231, 100, BAD);
            }
        } else if (st.getBoolean("overheat")) {
            small(Lang.tr("sc.bridge.gui.overheat", (t[2] + 19) / 20), 312, 217, 100, BAD);
        }
    }

    private void drawBottom() {
        text(Lang.tr("sc.bridge.gui.journal"), 8, 247, LABEL);
        if (st.hasKey("last")) {
            small(BridgeMsgSC.read(st.getCompoundTag("last")).text(), 52, 248, 148, WARN);
        }
        NBTTagList j = st.getTagList("journal", 10);
        SimpleDateFormat f = new SimpleDateFormat("HH:mm");
        int lines = 6;
        for (int i = 0; i < lines && i < j.tagCount(); i++) {
            NBTTagCompound e = j.getCompoundTagAt(j.tagCount() - 1 - i);
            String who = e.getString("p");
            String line = f.format(new Date(e.getLong("t"))) + " " + (who.length() > 0 ? who + " — " : "")
                    + BridgeMsgSC.read(e.getCompoundTag("m")).text();
            small(line, 8, 257 + i * 7, 192, e.getBoolean("bad") ? BAD : TEXT);
        }
        if (j.tagCount() == 0) {
            small(Lang.tr("sc.bridge.gui.journal.empty"), 8, 257, 192, DIM);
        }
        rect(203, 245, 1, 53, EDGE);
        text(Lang.tr("sc.bridge.gui.access2"), 206, 247, LABEL);
        int[] lk = ints("links", 2);
        smallRight(Lang.tr("sc.bridge.gui.linkedn", lk[0], lk[1]), 412, 248, lk[0] + lk[1] > 0 ? OK : DIM);
        chips.clear();
        NBTTagList fr = st.getTagList("friends", 8);
        int cx2 = 206, cy2 = 269;
        if (fr.tagCount() == 0) {
            small(Lang.tr("sc.bridge.gui.friends.none"), 206, 270, 206, DIM);
        }
        for (int i = 0; i < fr.tagCount(); i++) {
            String n = fr.getStringTagAt(i);
            int w = (int) Math.ceil(fontRendererObj.getStringWidth(n + " ×") * 0.75F) + 4;
            if (cx2 + w > 412) {
                cx2 = 206;
                cy2 += 9;
                if (cy2 > 277) {
                    small("...", 404, 278, 8, DIM);
                    break;
                }
            }
            boolean armed = armedFriend == i && System.currentTimeMillis() - armedFriendAt < 2000;
            rect(cx2, cy2, w, 8, armed ? 0xFF6A2A2A : 0xFF22302A);
            small(n + " ×", cx2 + 2, cy2 + 1, w - 2, armed ? BAD : TEXT);
            chips.add(new int[]{cx2, cy2, w, i});
            cx2 += w + 2;
        }
    }

    /** С5 / С7: «знакомое место: -25%» / «незнакомое: +50%, разброс до 30 бл.» / «разведка: разброс до 100 бл.»; "" for the server. */
    static String famText(int[] f) {
        if (f == null || f.length < 4 || f[0] < 0) {
            return "";
        }
        if (f[3] != 0) {
            return Lang.tr("sc.bridge.fam.scout", f[2]);
        }
        if (f[0] != 0) {
            return Lang.tr(f[2] > 0 ? "sc.bridge.fam.knownScatter" : "sc.bridge.fam.known", -BridgeMathSC.FAMILIAR_PCT, f[2]);
        }
        return Lang.tr(f[2] > 0 ? "sc.bridge.fam.unknown" : "sc.bridge.fam.unknownPrecise", BridgeMathSC.UNFAMILIAR_PCT, f[2]);
    }

    // ------------------------------------------------------------------ tooltips

    private boolean over(int x, int y, int w, int h) {
        return vmx >= x && vmy >= y && vmx < x + w && vmy < y + h;
    }

    private List<String> tip() {
        List<String> t = new ArrayList<String>();
        for (Btn b : btns) {
            if (b.visible && b.over(vmx, vmy)) {
                if (b.id >= B_MODE && b.id < B_MODE + MODES) {
                    t.add(Lang.tr("sc.bridge.gui.mode." + (b.id - B_MODE)));
                    t.add("§7" + Lang.tr("sc.bridge.gui.mode." + (b.id - B_MODE) + ".hint"));
                    if (b.id != B_MODE) {
                        t.add("§6" + Lang.tr("sc.bridge.gui.farmode"));
                    }
                } else if (b.id >= B_CLEAR && b.id < B_CLEAR + BridgeMathSC.GASES.length) {
                    t.add(Lang.tr("sc.bridge.gui.clear"));
                    t.add("§7" + Lang.tr("sc.bridge.gui.clear.hint"));
                } else if (b.id == B_REPAIR) {
                    int wear = st.getInteger("wear");
                    t.add(Lang.tr("sc.bridge.gui.repair.hint", wear));
                    t.add("§7" + Lang.tr("sc.bridge.gui.repair.cost", g(st.getInteger("repairHe")), eu(st.getLong("repairEu"))));
                    t.add("§7" + Lang.tr("sc.bridge.gui.repair.hint2", BridgeMathSC.REPAIR_HE_PER_WEAR, eu(BridgeMathSC.REPAIR_EU_PER_WEAR)));
                    if (st.getBoolean("open")) {
                        t.add("§c" + Lang.tr("sc.bridge.refuse.open"));
                    }
                } else if (b.id == B_CALIB) {
                    t.add(Lang.tr("sc.bridge.gui.calibrate"));
                    t.add("§7" + Lang.tr("sc.bridge.gui.calibrate.hint", BridgeMathSC.CALIB_KR, eu(BridgeMathSC.CALIB_EU)));
                } else if (b.id == B_ACCESS) {
                    t.add(Lang.tr("sc.bridge.gui.access.hint"));
                    t.add("§7" + Lang.tr("sc.bridge.gui.access.hint2"));
                } else if (b.id == B_FRIEND_ADD) {
                    t.add(Lang.tr("sc.bridge.gui.friend.add"));
                    t.add("§7" + Lang.tr("sc.bridge.gui.friend.add2", BridgeMathSC.MAX_FRIENDS));
                } else if (b.id == B_REMOTE) {
                    t.add(Lang.tr("sc.bridge.gui.remote.hint"));
                    t.add("§7" + Lang.tr("sc.bridge.gui.remote.hint2", g(BridgeMathSC.REMOTE_MODE_EU)));
                } else if (b.id == B_BIND) {
                    t.add(Lang.tr("sc.bridge.gui.bindhelmet.hint", BridgeMathSC.MAX_LINKS));
                    t.add("§7" + Lang.tr("sc.bridge.gui.bindremote.hint"));
                } else if (b.id == B_RENAME) {
                    t.add(Lang.tr("sc.bridge.gui.rename"));
                } else if (b.id == B_FROM_COORD) {
                    t.add(Lang.tr("sc.bridge.gui.fromcoord.hint"));
                    t.add("§7" + Lang.tr("sc.bridge.gui.coords.have", has() ? st.getInteger("coords") : 0));
                } else if (b.id == B_TO_COORD) {
                    t.add(Lang.tr("sc.bridge.gui.tocoord.hint"));
                    t.add("§7" + Lang.tr("sc.bridge.gui.copycoord.hint"));
                } else if (b.id == B_DIM) {
                    t.add(Lang.tr("sc.bridge.gui.dim.hint"));
                } else if (b.id == B_BM_ADD || b.id == B_BM_REN || b.id == B_BM_DEL) {
                    t.add(Lang.tr(b.id == B_BM_ADD ? "sc.bridge.gui.bm.add" : b.id == B_BM_REN ? "sc.bridge.gui.bm.rename" : "sc.bridge.gui.bm.delete"));
                } else if (b.id == B_PROBE) {
                    t.add(Lang.tr("sc.bridge.gui.probe.hint", BridgeMathSC.PROBE_KR));
                } else if (b.id == B_POWER) {
                    t.add(Lang.tr("sc.bridge.gui.power.hint"));
                } else if (b.id == B_CHECK) {
                    t.add(Lang.tr("sc.bridge.gui.check.hint"));
                } else if (b.id == B_OPEN) {
                    t.add(Lang.tr("sc.bridge.gui.open.hint"));
                }
                if (!b.enabled && !allowed() && b.id != B_ACCESS && b.id != B_RENAME && b.id != B_FRIEND_ADD && !(b.id >= B_MODE && b.id < B_MODE + MODES)) {
                    t.add("§c" + Lang.tr("sc.bridge.gui.notowner", st.getString("owner")));
                }
                return t;
            }
        }
        int[] tk = ints("tanks", BridgeMathSC.GASES.length), caps = ints("tankCaps", BridgeMathSC.GASES.length);
        for (int i = 0; i < BridgeMathSC.GASES.length; i++) {
            if (over(312 + i * 17, 112, 12, 60)) {
                t.add(Lang.tr(BridgeMsgSC.RES + "gas." + BridgeMathSC.GASES[i].key()));
                t.add(g(tk[i]) + " / " + g(caps[i]) + " " + Lang.tr("sc.bridge.unit.mb"));
                t.add("§7" + Lang.tr("sc.bridge.gui.tank.hint"));
                return t;
            }
        }
        if (over(312, 30, 22, 60)) {
            t.add(Lang.tr("sc.bridge.gui.capacitors", ints("counts", 9)[4]));
            t.add(g(st.getLong("capEu")) + " / " + g(st.getLong("capMax")) + " EU");
            t.add("§7" + Lang.tr("sc.bridge.gui.cap.hint", eu(BridgeMathSC.CAPACITOR_EU)));
            return t;
        }
        if (over(312, 176, 100, 40)) {
            int[] sp = ints("stab", 7);
            boolean open = st.getBoolean("open");
            t.add(Lang.tr("sc.bridge.gui.stability") + ": " + (open ? ints("time", 6)[4] : sp[6]) + "%");
            t.add("§7" + Lang.tr("sc.bridge.gui.stab.base"));
            String[] keys = {"missing", "wear", "mass", "interf", "storm", "argon"};
            for (int i = 0; i < keys.length; i++) {
                t.add((sp[i] > 0 ? "§c" : "§8") + Lang.tr("sc.bridge.gui.stab." + keys[i], sp[i]));
            }
            int[] env = ints("env", 3);
            if (env[2] != 0) {
                t.add("§a" + Lang.tr("sc.bridge.gui.stab.res", BridgeMathSC.RESONANCE_PCT));
            }
            t.add("§7" + Lang.tr("sc.bridge.gui.stab.hint", BridgeMathSC.STAB_MISSING_PERCENT, BridgeMathSC.STAB_FOLD));
            t.add("§7" + Lang.tr("sc.bridge.gui.stab.hint2", BridgeMathSC.TURBULENCE, BridgeMathSC.TURB_SHIFT));
            return t;
        }
        if (over(206, 268, 206, 18) && !chips.isEmpty()) {
            t.add(Lang.tr("sc.bridge.gui.friends.hint"));
            return t;
        }
        if (over(8, 189, 92, 8)) {
            t.add(Lang.tr("sc.bridge.gui.heat") + ": " + st.getInteger("heat") / 10 + "%");
            t.add("§7" + Lang.tr("sc.bridge.gui.heat.hint", BridgeMathSC.COOL_S, BridgeMathSC.COOL_S / BridgeMathSC.COOLER_SPEED));
            t.add("§7" + Lang.tr("sc.bridge.gui.heat.hint2", BridgeMathSC.OVERHEAT_LOCK_S / 60, BridgeMathSC.OVERHEAT_WEAR));
            return t;
        }
        if (over(8, 197, 92, 8)) {
            t.add(Lang.tr("sc.bridge.gui.wear") + ": " + st.getInteger("wear") + "%");
            t.add("§7" + Lang.tr("sc.bridge.gui.wear.hint", BridgeMathSC.WEAR_FREE));
            t.add("§7" + Lang.tr("sc.bridge.gui.wear.hint2"));
            return t;
        }
        if (over(110, 168, 192, 8)) {
            t.add(Lang.tr("sc.bridge.gui.fam.hint"));
            t.add("§7" + Lang.tr("sc.bridge.gui.fam.hint2", -BridgeMathSC.FAMILIAR_PCT, BridgeMathSC.UNFAMILIAR_PCT, BridgeMathSC.SCATTER_UNFAMILIAR,
                    BridgeMathSC.SCATTER_NAV));
            return t;
        }
        if (over(8, 30, 92, 92)) {
            t.add(Lang.tr("sc.bridge.gui.scheme.hint"));
            return t;
        }
        if (over(110, 186, 192, 35)) {
            t.add(Lang.tr("sc.bridge.gui.cost.hint"));
            return t;
        }
        return null;
    }

    // ------------------------------------------------------------------ buttons

    private class Btn {
        final int id, x, y, w, h;
        String label;
        int color = TEXT;
        boolean enabled = true, visible = true, selected, clear;

        Btn(int id, int x, int y, int w, int h, String key) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.label = key.length() == 0 ? "" : Lang.tr(key);
        }

        boolean over(int mx, int my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }

        void draw() {
            if (!visible) {
                return;
            }
            boolean hover = enabled && over(vmx, vmy);
            if (clear) {
                rect(x, y, 7, 7, 0xFF1E0A0A);
                rect(x + 1, y + 1, 5, 5, !enabled ? 0xFF2A2E36 : selected ? 0xFFE04040 : hover ? 0xFF9A2E2E : 0xFF5A1E1E);
                int c = enabled ? 0xFFFF9A9A : 0xFF5A606A;
                for (int i = 0; i < 3; i++) {
                    rect(x + 2 + i, y + 2 + i, 1, 1, c);
                    rect(x + 4 - i, y + 2 + i, 1, 1, c);
                }
                return;
            }
            rect(x, y, w, h, selected ? EDGE_HI | 0xFF000000 : hover ? 0xFFB8C4BC : 0xFF0A0C0B);
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
}
