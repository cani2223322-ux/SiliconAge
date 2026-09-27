package com.sc.inventory;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.FontRenderer;

/**
 * The large screen shared by machines, generators and energy storages (248 x 232): a steel panel
 * with a dark title bar, one big holo screen (GuiHoloSC) with a tall energy gauge right of it,
 * a row of four upgrade slots inside the window, and the player's inventory centred below.
 * Containers place their slots at these coordinates (plain int constants - safe to use from the
 * server side, nothing here is loaded there).
 */
public final class GuiBigSC {

    public static final int W = 248, H = 232;
    /** The holo screen and the energy gauge right of it. */
    public static final int SCREEN_X = 7, SCREEN_Y = 21, SCREEN_W = 202, SCREEN_H = 94;
    public static final int GAUGE_X = 214, GAUGE_Y = 21, GAUGE_W = 26, GAUGE_H = 94;
    /** The screen's inner right edge: text and tanks end here. */
    public static final int SCREEN_RIGHT = SCREEN_X + SCREEN_W - 3;
    /** Upgrade slots: a row inside the window (item coordinates of the first), the label left of it. */
    public static final int UPG_X = 49, UPG_Y = 121, UPG_LABEL_X = 8, UPG_TEXT_X = 124;
    /** The text beside the upgrade row ends before the gauge column (the battery slot sits there). */
    public static final int UPG_TEXT_W = GAUGE_X - 4 - UPG_TEXT_X;
    /** The player's inventory, centred (item coordinates). */
    public static final int INV_X = 44, INV_Y = 155, HOTBAR_Y = 213;
    private static final int SEPARATOR_Y = 141;

    private static final int POCKET_DARK = 0xFF343C48, POCKET_FILL = 0xFF707A88, POCKET_LIGHT = 0xFFECF1F7,
            TITLE_BAR = 0xFF2E3642, PANEL = 0xFFB9C1CC;

    private GuiBigSC() {
    }

    /** The panel, its title bar, the divider above the inventory and the inventory's pockets. */
    public static void window(int x, int y, boolean upgrades, int upgradeSlots) {
        rect(x, y, W, H, 0xFF1E2024);
        rect(x + 1, y + 1, W - 2, H - 2, PANEL);
        rect(x + 1, y + 1, W - 3, 1, 0xFFECF0F6);
        rect(x + 1, y + 1, 1, H - 3, 0xFFE2E8F0);
        rect(x + 2, y + H - 2, W - 3, 1, 0xFF6E747E);
        rect(x + W - 2, y + 2, 1, H - 3, 0xFF767C86);
        rect(x + 3, y + 3, W - 6, 1, 0xFF6E747E);                 // the title bar, inset
        rect(x + 3, y + 3, 1, 13, 0xFF6E747E);
        rect(x + 4, y + 4, W - 8, 11, TITLE_BAR);
        rect(x + 4, y + 15, W - 8, 1, 0xFFF0F2F6);
        rect(x + 7, y + SEPARATOR_Y, GAUGE_X - 10, 1, 0xFF5A606A);
        rect(x + 7, y + SEPARATOR_Y + 1, GAUGE_X - 10, 1, 0xFFF0F2F6);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                pocket(x + INV_X + c * 18, y + INV_Y + r * 18);
            }
        }
        for (int c = 0; c < 9; c++) {
            pocket(x + INV_X + c * 18, y + HOTBAR_Y);
        }
        if (upgrades) {
            for (int i = 0; i < upgradeSlots; i++) {
                pocket(x + UPG_X + i * 18, y + UPG_Y);
            }
        }
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** A steel slot pocket round the 16x16 item area at (x, y). */
    public static void pocket(int x, int y) {
        rect(x - 1, y - 1, 18, 18, POCKET_DARK);
        rect(x, y, 17, 17, POCKET_LIGHT);
        rect(x, y, 16, 16, POCKET_FILL);
    }

    /** The upgrade row's caption and the inventory's, in foreground coordinates. */
    public static void labels(FontRenderer font, String upgrades, String inventory) {
        if (upgrades != null) {
            font.drawString(upgrades, UPG_LABEL_X, UPG_Y + 4, 0x404040);
        }
        font.drawString(inventory, INV_X, INV_Y - 10, 0x404040);
    }

    /** Room for the title: up to the tier plate at the right of the title bar. */
    public static int titleRoom(FontRenderer font, com.sc.energy.Tier tier) {
        return W - 6 - (font.getStringWidth(tier.name()) + 4) - 4 - 8;
    }

    public static boolean overUpgradeSlot(int mx, int my, int slots) {
        for (int i = 0; i < slots; i++) {
            if (GuiGaugeSC.isOver(UPG_X - 1 + i * 18, UPG_Y - 1, 18, 18, mx, my)) {
                return true;
            }
        }
        return false;
    }

    /** A tank's small Clear button (7 x 7, a cross) above its gauge's side slot. */
    public static class ClearButton extends net.minecraft.client.gui.GuiButton {
        public ClearButton(int id) {
            super(id, 0, 0, 7, 7, "");
            visible = false;
        }

        @Override
        public void drawButton(net.minecraft.client.Minecraft mc, int mx, int my) {
            if (!visible) {
                return;
            }
            boolean over = mx >= xPosition && my >= yPosition && mx < xPosition + width && my < yPosition + height;
            rect(xPosition, yPosition, 7, 7, 0xFF1E0A0A);
            rect(xPosition + 1, yPosition + 1, 5, 5, !enabled ? 0xFF2A2E36 : over ? 0xFF9A2E2E : 0xFF5A1E1E);
            int c = enabled ? 0xFFFF9A9A : 0xFF5A606A;
            for (int i = 0; i < 3; i++) {
                rect(xPosition + 2 + i, yPosition + 2 + i, 1, 1, c);
                rect(xPosition + 4 - i, yPosition + 2 + i, 1, 1, c);
            }
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }

        public boolean over(int mx, int my) {
            return visible && mx >= xPosition && my >= yPosition && mx < xPosition + width && my < yPosition + height;
        }
    }

    /** The Clear button's tooltip: what it pours out and costs, or why it can't. */
    public static java.util.List<String> clearTip(int amount, int cost, int have) {
        java.util.List<String> lines = new java.util.ArrayList<String>();
        lines.add(com.sc.manual.Lang.tr("sc.gui.tank.clear"));
        if (amount <= 0) {
            lines.add("\u00a77" + com.sc.manual.Lang.tr("sc.gui.tank.clear.empty"));
        } else {
            lines.add(com.sc.manual.Lang.tr("sc.gui.tank.clear.cost", amount, cost, have));
            if (have < cost) {
                lines.add("\u00a7c" + com.sc.manual.Lang.tr("sc.gui.tank.clear.nopower"));
            }
        }
        return lines;
    }

    private static void rect(int x, int y, int w, int h, int c) {
        if (w > 0 && h > 0) {
            Gui.drawRect(x, y, x + w, y + h, c);
        }
    }
}
