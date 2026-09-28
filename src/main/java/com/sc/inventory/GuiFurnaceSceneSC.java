package com.sc.inventory;

import net.minecraft.client.gui.Gui;

/**
 * The smelters' scenes (GuiMachineSC): the electric furnace's firebrick chamber with its heating
 * coils and the glowing piece; the induction furnace's two crucibles in copper coils, the field's
 * rings and the melt; the heat bar and an experience orb.
 */
public final class GuiFurnaceSceneSC {

    private GuiFurnaceSceneSC() {
    }

    private static void rect(int x, int y, int w, int h, int c) {
        Gui.drawRect(x, y, x + w, y + h, c);
    }

    /** Coil glow by heat 0..1: dark red -> orange -> yellow-white. */
    public static int heatColour(float h) {
        h = Math.max(0F, Math.min(1F, h));
        int r, g, b;
        if (h < 0.5F) {
            float k = h / 0.5F;
            r = 90 + (int) (165 * k);
            g = 30 + (int) (110 * k);
            b = 20;
        } else {
            float k = (h - 0.5F) / 0.5F;
            r = 255;
            g = 140 + (int) (100 * k);
            b = 20 + (int) (200 * k);
        }
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static void frame(int x, int y, int w, int h) {
        rect(x, y, w, h, 0xFF1E3444);
        rect(x + 1, y + 1, w - 2, h - 2, 0xFF0A1218);
    }

    /** The electric furnace: coils glow while it works, the piece in the middle glows with them. */
    public static void chamber(int x, int y, int w, int h, float t, boolean working) {
        frame(x, y, w, h);
        rect(x + 3, y + 3, w - 6, h - 6, 0xFF2A1A16);
        for (int k = 0; k < w - 6; k += 6) {
            rect(x + 3 + k, y + 3, 1, h - 6, 0xFF3A2620);
        }
        float heat = working ? 0.85F : 0.1F;
        for (int k = 7; k < w - 9; k += 4) {
            float flick = working ? 0.08F * (float) Math.sin(t * 0.7F + k * 0.5F) : 0F;
            int c = heatColour(heat + flick);
            rect(x + k, y + 6, 3, 2, c);
            rect(x + k, y + h - 8, 3, 2, c);
        }
        for (int k = 0; k < 3; k++) {
            rect(x + 5, y + 11 + k * 7, 2, 3, heatColour(heat));
            rect(x + w - 7, y + 11 + k * 7, 2, 3, heatColour(heat));
        }
        int cx = x + w / 2 - 6, cy = y + h / 2 - 5;
        if (working) {
            rect(cx - 3, cy - 3, 18, 16, 0x40FFA040);
            for (int i = 0; i < 5; i++) {                                    // rising shimmer
                int yy = y + h - 10 - (int) ((t * 1.3F + i * 5) % (h - 18));
                rect(x + 16 + i * (w - 32) / 4, yy, 1, 2, 0x80FFB060);
            }
        }
        rect(cx, cy + 2, 12, 6, working ? heatColour(0.8F) : 0xFF8A909A);    // the piece
        rect(cx + 1, cy, 10, 2, working ? heatColour(0.95F) : 0xFFB8BEC8);
    }

    /** The induction furnace: two crucibles; `busy` says which stream smelts; the field's rings while it works. */
    public static void crucibles(int x, int y, int w, int h, float t, float heat, boolean[] busy) {
        frame(x, y, w, h);
        for (int c = 0; c < 2; c++) {
            int cx = x + 14 + c * (w / 2), by = y + 5;
            boolean on = busy != null && c < busy.length && busy[c];
            for (int k = 0; k < 7; k++) {                                     // coil turns
                rect(cx - 3, by + 2 + k * 4, 26, 2, k % 2 == 0 ? 0xFFD08040 : 0xFFA05A28);
            }
            rect(cx + 2, by, 16, h - 10, 0xFF3A3E46);                        // the crucible
            rect(cx + 3, by + 1, 14, h - 12, heat > 0.05F ? heatColour(heat * 0.9F) : 0xFF2A2E36);
            rect(cx + 3, by + 1, 14, 3, 0xFF1A1E24);
            if (on) {
                rect(cx + 5, by + h - 23, 10, 10, heatColour(Math.min(1F, heat + 0.15F)));
                rect(cx + 6, by + h - 23, 8, 1, 0xFFFFF0C0);
                for (int r = 0; r < 3; r++) {                                  // the field
                    float f = (t * 0.15F + r / 3F) % 1F;
                    int rr = 13 + (int) (9 * f), a = (int) (200 * (1 - f));
                    for (int q = 0; q < 360; q += 15) {
                        int px = cx + 10 + (int) (rr * Math.cos(Math.toRadians(q)));
                        int py = by + (h - 10) / 2 + (int) (rr * 0.5 * Math.sin(Math.toRadians(q)));
                        if (px > x + 1 && px < x + w - 2 && py > y + 1 && py < y + h - 2) {
                            rect(px, py, 1, 1, (a << 24) | 0x6EE6FF);
                        }
                    }
                }
            }
        }
    }

    /** The induction heat bar, coloured along its length; marks at a third and two thirds. */
    public static void heatBar(int x, int y, int w, int h, float heat) {
        rect(x, y, w, h, 0xFF1E3444);
        rect(x + 1, y + 1, w - 2, h - 2, 0xFF0A1218);
        int n = (int) ((w - 2) * Math.max(0F, Math.min(1F, heat)));
        for (int k = 0; k < n; k++) {
            rect(x + 1 + k, y + 1, 1, h - 2, heatColour(k / (float) (w - 2)));
        }
        rect(x + w / 3, y, 1, h, 0xFF4A5666);
        rect(x + 2 * w / 3, y, 1, h, 0xFF4A5666);
    }

    /** A small green experience orb. */
    public static void orb(int x, int y) {
        rect(x - 2, y - 3, 5, 7, 0xFF5AB010);
        rect(x - 3, y - 2, 7, 5, 0xFF5AB010);
        rect(x - 1, y - 2, 3, 5, 0xFFB8FF40);
        rect(x - 2, y - 1, 5, 3, 0xFFB8FF40);
        rect(x - 1, y - 1, 1, 1, 0xFFFFFFA0);
    }
}
