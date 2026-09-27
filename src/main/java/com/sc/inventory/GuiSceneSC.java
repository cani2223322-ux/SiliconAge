package com.sc.inventory;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.Gui;

/**
 * Animated process scenes for the large machine screen's picture window, drawn from rectangles.
 * The Crusher: a hopper grid on top, two toothed jaws that close and open while it works, an ore
 * lump dropping between them, chips falling and a tray below filling with dust as the recipe
 * goes on. Still (and without chips) while the machine waits.
 */
@SideOnly(Side.CLIENT)
public final class GuiSceneSC {

    private GuiSceneSC() {
    }

    /** The picture window's frame and dark fill. */
    public static void frame(int x, int y, int w, int h) {
        rect(x, y, w, h, 0xFF1E3444);
        rect(x + 1, y + 1, w - 2, h - 2, 0xFF0A1218);
    }

    /** @param t world time in ticks (with the partial tick), @param progress 0..1 of the current recipe */
    public static void crusher(int x, int y, int w, int h, float t, boolean running, float progress) {
        frame(x, y, w, h);
        for (int k = 0; k + 3 < w - 4; k += 5) {
            rect(x + 2 + k, y + 3, 3, 1, 0xFF2A3A48);                      // the hopper grid
        }
        int open = running ? (int) Math.round(1.5 + 1.5 * Math.sin(t * 0.45)) : 2;
        int cx = x + w / 2;
        for (int side = 0; side < 2; side++) {                              // the jaws: a funnel, narrowing downwards
            for (int j = 0; j < 20; j++) {
                int jw = 10 + j / 2;
                int x0 = side == 0 ? cx - 27 - open : cx + 27 + open - jw;
                rect(x0, y + 7 + j, jw, 1, j % 4 != 0 ? 0xFF6A707A : 0xFF8A909A);
                if (j % 4 == 1) {
                    int tx = side == 0 ? x0 + jw : x0 - 2;                   // teeth on the inner edge
                    rect(tx, y + 7 + j, 2, 2, 0xFFB8BEC8);
                }
            }
        }
        if (running) {
            int fall = (int) (t * 0.8F) % 18;                               // an ore lump dropping into the jaws
            int oy = y + 5 + fall;
            rect(cx - 3, oy, 6, 5, 0xFF7A7E88);
            rect(cx - 2, oy + 1, 2, 2, 0xFFB07050);
            rect(cx + 1, oy + 2, 1, 1, 0xFFB07050);
            for (int i = 0; i < 6; i++) {                                   // chips under the jaws
                int cy = y + 28 + (int) ((t * 0.9F + i * 3.7F) % 8);
                int chx = cx - 5 + (i * 7 % 11);
                rect(chx, cy, 1, 1, 0xFFD8C8A8);
            }
        }
        int trayW = w - 60, tx0 = x + 30;                                   // the tray, filling with dust
        rect(tx0 - 2, y + h - 3, trayW + 4, 1, 0xFF3A2E24);
        int dust = Math.max(1, Math.round(3 * Math.max(0F, Math.min(1F, progress))));
        rect(tx0, y + h - 3 - dust, trayW, dust, 0xFF9A8A7A);
        rect(tx0, y + h - 3 - dust, trayW, 1, 0xFFB8A898);
    }

    private static void rect(int x, int y, int w, int h, int c) {
        if (w > 0 && h > 0) {
            Gui.drawRect(x, y, x + w, y + h, c);
        }
    }
}
