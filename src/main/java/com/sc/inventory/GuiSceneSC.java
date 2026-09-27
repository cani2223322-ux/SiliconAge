package com.sc.inventory;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.Gui;

/**
 * Animated process scenes for the large machine screen's picture window, drawn from rectangles.
 * The Crusher: a hopper grid on top, two toothed jaws that close and open while it works, an ore
 * lump dropping between them, chips falling and a tray below filling with dust as the recipe
 * goes on. Still (and without chips) while the machine waits.
 * The Ore Washer: a steel tub whose water level is the machine's water tank, nozzles spraying,
 * ore bobbing on the water, bubbles rising, silt on the bottom, level marks on the side.
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

    /** @param level the water tank's fill, 0..1 - the tub's water line */
    public static void washer(int x, int y, int w, int h, float t, boolean running, float level) {
        frame(x, y, w, h);
        int tx0 = x + 6, tx1 = x + w - 6, ty0 = y + 12, ty1 = y + h - 4;
        rect(tx0 - 2, ty0, 2, ty1 - ty0 + 2, 0xFF8A909A);                   // the tub
        rect(tx1, ty0, 2, ty1 - ty0 + 2, 0xFF6A707A);
        rect(tx0 - 2, ty1, tx1 - tx0 + 4, 2, 0xFF5A606A);
        level = Math.max(0F, Math.min(1F, level));
        int wl = ty1 - Math.round((ty1 - ty0) * level);
        if (wl < ty1) {                                                     // water, gently rippling rows
            for (int yy = wl; yy < ty1; yy++) {
                int shade = ((yy - wl) + (int) (t * 0.3F)) % 5 == 0 ? 0xFF3C78E6 : (yy - wl) > (ty1 - wl) / 2 ? 0xFF2452B8 : 0xFF2E64D0;
                rect(tx0, yy, tx1 - tx0, 1, shade);
            }
            rect(tx0, wl, tx1 - tx0, 1, 0xFFA8D8FF);
        }
        rect(tx0, ty1 - 2, tx1 - tx0, 2, 0xFF4A3A2A);                       // silt
        for (int k = 0; k < 3; k++) {                                       // nozzles and drops
            int nx = tx0 + 6 + k * (tx1 - tx0 - 12) / 2;
            rect(nx - 2, y + 3, 5, 3, 0xFF8A909A);
            rect(nx - 1, y + 6, 3, 1, 0xFF6A707A);
            if (running) {
                for (int d = 0; d < 3; d++) {
                    int dy = y + 8 + (int) ((t * 1.3F + d * 4 + k * 2) % 10);
                    if (dy < wl) {
                        rect(nx, dy, 1, 2, 0xFF7AB8FF);
                    }
                }
            }
        }
        if (running && wl < ty1 - 3) {
            float[][] lumps = {{0.3F, 0F}, {0.55F, 1.7F}, {0.75F, 3.1F}};
            for (float[] lp : lumps) {                                      // ore bobbing on the water
                int lx = tx0 + (int) ((tx1 - tx0) * lp[0]) - 3;
                int ly = Math.max(ty0 + 2, wl - 2 + (int) Math.round(Math.sin(t * 0.3F + lp[1])));
                rect(lx, ly, 7, 5, 0xFF7A6E62);
                rect(lx + 1, ly + 1, 2, 2, 0xFFB07050);
            }
            for (int b = 0; b < 6; b++) {                                   // bubbles
                int bx = tx0 + 4 + (b * 17) % Math.max(1, tx1 - tx0 - 8);
                int by = ty1 - 3 - (int) ((t * 0.9F + b * 5) % Math.max(1, ty1 - wl - 3));
                if (by > wl) {
                    rect(bx, by, 1, 1, 0xFFE0F4FF);
                }
            }
        }
        for (int k = 0; k <= 4; k++) {                                      // level marks
            int my = ty0 + Math.round((ty1 - ty0) * k / 4F);
            rect(tx1 + 2, my, 2, 1, k % 2 == 0 ? 0xFFF0C450 : 0xFF3C4048);
        }
    }

    /** The Blast Furnace: brick walls, a glowing mouth (brighter the hotter), flames while it works, a crucible, shimmer. */
    public static void furnace(int x, int y, int w, int h, float t, boolean running, float heat) {
        frame(x, y, w, h);
        int bx0 = x + 5, bx1 = x + w - 5, by0 = y + 3, by1 = y + h - 3;
        for (int yy = by0, row = 0; yy + 3 <= by1; yy += 4, row++) {         // bricks
            int off = row % 2 == 0 ? 0 : 4;
            for (int xx = bx0 - off; xx < bx1; xx += 8) {
                int a = Math.max(bx0, xx), e = Math.min(bx1, xx + 7);
                rect(a, yy, e - a, 3, 0xFF6A3A2A);
                rect(a, yy, e - a, 1, 0xFF8A4A34);
            }
        }
        int mx0 = bx0 + 9, mx1 = bx1 - 9, my0 = by0 + 9, my1 = by1 - 2;       // the mouth
        rect(mx0 - 1, my0 - 1, mx1 - mx0 + 2, my1 - my0 + 1, 0xFF2A1810);
        int[] glow = {0xFF3A1206, 0xFF7A2A08, 0xFFC04A10, 0xFFFF8C1E, 0xFFFFD050};
        float bright = 0.35F + 0.65F * Math.max(0F, Math.min(1F, heat));
        for (int yy = my0; yy < my1; yy++) {
            float k = (float) (yy - my0) / Math.max(1, my1 - my0);
            rect(mx0, yy, mx1 - mx0, 1, glow[Math.min(4, (int) (k * 5 * bright))]);
        }
        if (running) {
            for (int i = 0; i < 6; i++) {                                     // flames
                int fx = mx0 + 2 + i * (mx1 - mx0 - 4) / 5;
                int fh = 3 + (int) Math.round(2.5 + 2.5 * Math.sin(t * 0.7F + i * 1.3F));
                rect(fx, my1 - fh, 2, fh, 0xFFFFB040);
                rect(fx, my1 - fh, 2, 1, 0xFFFFF0A0);
            }
            for (int i = 0; i < 4; i++) {                                     // shimmer over the mouth
                rect(mx0 + 3 + i * 6, by0 + 1 + (int) ((t * 0.5F + i) % 4), 1, 1, 0xFFFFD890);
            }
        }
        int cx = (mx0 + mx1) / 2;                                              // the crucible
        rect(cx - 7, my0 + 3, 14, 6, 0xFF5A606A);
        rect(cx - 6, my0 + 3, 12, 2, heat > 0.05F ? 0xFFFFC060 : 0xFF6A5A4A);
        rect(cx - 6, my0 + 5, 12, 1, heat > 0.05F ? 0xFFFF8C1E : 0xFF4A3A2A);
    }

    /**
     * The Czochralski puller: a crucible of melt over heater coils, the seed rod pulling a boule up out
     * of it (longer as the run goes on, striped as it turns), argon coming down.
     */
    public static void puller(int x, int y, int w, int h, float t, boolean running, float progress, float heat) {
        frame(x, y, w, h);
        progress = Math.max(0F, Math.min(1F, progress));
        boolean hot = heat > 0.05F || running;
        int cx = x + w / 2;
        rect(x + 5, y + 3, w - 10, 2, 0xFF6A707A);                            // the pull head
        rect(x + 5, y + 3, w - 10, 1, 0xFF9AA0AA);
        int cy0 = y + h - 14, cy1 = y + h - 5;                                 // the crucible
        rect(cx - 16, cy0, 32, cy1 - cy0, 0xFF8A909A);
        rect(cx - 15, cy0, 30, cy1 - cy0 - 1, 0xFF2A1810);
        int[] melt = hot ? new int[]{0xFFFFD050, 0xFFFF9A30, 0xFFC04A10} : new int[]{0xFF8A8A90, 0xFF6A6A72, 0xFF4A4A52};
        for (int i = 0; i < melt.length; i++) {
            rect(cx - 15, cy0 + 1 + i * 2, 30, 2, melt[i]);
        }
        for (int i = 0; i < 5; i++) {                                          // heater coils
            rect(cx - 18 + i * 8, cy1 + 1, 5, 2, hot ? 0xFFFF5A3C : 0xFF5A2A20);
        }
        int rodTop = y + 5, room = cy0 - (y + 9);
        int length = Math.round(room * progress), top = cy0 - length;
        rect(cx - 1, rodTop, 2, top - rodTop, 0xFFB0B8C4);                    // the seed rod
        if (length > 0) {                                                      // the boule
            int turn = running ? (int) (t * 0.8F) : 0;
            for (int yy = top; yy < cy0; yy++) {
                float k = (float) (yy - top) / Math.max(1, cy0 - top);
                int half = k < 0.9F ? 2 + Math.round(6 * Math.min(1F, k * 3)) : 2 + Math.round(6 * (1 - (k - 0.9F) * 5));
                rect(cx - half, yy, 2 * half, 1, (yy + turn) % 4 == 0 ? 0xFF8A94A8 : 0xFF6A7488);
                rect(cx - half, yy, 1, 1, 0xFFB8C4D8);
            }
            if (hot) {
                rect(cx - 6, cy0 - 1, 12, 1, 0xFFFFE08A);                      // the glowing meniscus
            }
        }
        if (running) {
            for (int i = 0; i < 6; i++) {                                      // argon flowing down
                int ax = x + 5 + (i * 7) % Math.max(1, w - 10);
                int ay = y + 6 + (int) ((t * 0.6F + i * 5) % Math.max(1, cy0 - y - 8));
                rect(ax, ay, 1, 1, 0xFF8AB0E8);
            }
        }
    }

    /**
     * The wire saw: two grooved pulleys, a web of diamond wires running between them and going down
     * through the ingot on its holder (cuts behind it), water jets on the cut, sparks where it bites.
     */
    public static void wireSaw(int x, int y, int w, int h, float t, boolean running, float progress) {
        frame(x, y, w, h);
        progress = Math.max(0F, Math.min(1F, progress));
        int ix0 = x + w / 2 - 16, ix1 = x + w / 2 + 16, top = y + 14, bot = y + h - 8;
        int wy = top - 2 + Math.round((bot - top + 2) * progress);
        int px0 = x + 6, px1 = x + w - 14, spin = running ? (int) t % 2 : 0;
        for (int px : new int[]{px0, px1}) {                                   // the pulleys
            rect(px, wy - 5, 8, 10, 0xFF6A707A);
            rect(px + 1, wy - 4, 6, 8, 0xFF3A4048);
            for (int k = 0; k < 4; k++) {
                rect(px + 1, wy - 4 + k * 2 + spin, 6, 1, 0xFF9AA0AA);
            }
        }
        rect(ix0, top, ix1 - ix0, bot - top, 0xFF6A7488);                     // the ingot
        rect(ix0, top, ix1 - ix0, 1, 0xFFB8C4D8);
        for (int i = 1; i < 8; i++) {                                          // the cuts so far
            rect(ix0 + i * (ix1 - ix0) / 8, top, 1, wy - top, 0xFF0A1218);
        }
        rect(ix0 - 3, bot, ix1 - ix0 + 6, 3, 0xFF8A909A);                     // the holder
        for (int k = 0; k < 3; k++) {                                          // the wire web
            rect(px0 + 8, wy - 2 + k * 2, px1 - px0 - 8, 1, (k + spin) % 2 == 1 ? 0xFFD8E8F8 : 0xFFA8C8E8);
        }
        for (int i = 0; i < 2; i++) {                                          // the water nozzles
            rect(ix0 - 6 + i * (ix1 - ix0 + 10), y + 4, 3, 3, 0xFF5A8AC8);
        }
        if (running) {
            for (int i = 0; i < 8; i++) {                                      // sparks where it cuts
                rect(ix0 + 2 + (i * 5 + (int) (t * 2)) % (ix1 - ix0 - 4), wy - 1, 1, 1, 0xFFFFFFFF);
            }
            for (int i = 0; i < 2; i++) {                                      // water jets
                int jx = ix0 - 5 + i * (ix1 - ix0 + 10);
                for (int k = 0; k < 5; k++) {
                    rect(jx, y + 7 + (int) ((t * 1.5F + k * 4) % Math.max(1, wy - y - 7)), 1, 1, 0xFF6AB4F0);
                }
            }
        }
    }

    /**
     * The oxidation furnace: a quartz tube inside glowing heater coils, a boat of wafers standing in it
     * (n of them, up to 6), oxygen flowing through when gas; the wafers take their colour with the
     * progress - blue-violet oxide, or a warm anneal.
     */
    public static void tubeFurnace(int x, int y, int w, int h, float t, boolean running, float progress, int n, boolean gas, boolean oxide) {
        frame(x, y, w, h);
        progress = Math.max(0F, Math.min(1F, progress));
        n = Math.max(1, Math.min(6, n));
        int ty0 = y + h / 2 - 9, ty1 = y + h / 2 + 9, tx0 = x + 8, tx1 = x + w - 6;
        rect(tx0 + 8, ty0 - 4, tx1 - tx0 - 16, ty1 - ty0 + 8, 0xFF3A2418);     // the furnace body
        for (int i = tx0 + 10; i < tx1 - 10; i += 4) {                         // the coils
            int c = !running ? 0xFF5A2A20 : (i / 4 + (int) (t * 0.3F)) % 3 == 0 ? 0xFFFFA040 : 0xFFFF6A2A;
            rect(i, ty0 - 3, 2, ty1 - ty0 + 6, c);
        }
        rect(tx0, ty0, tx1 - tx0, ty1 - ty0, 0xFF9AB8D0);                      // the quartz tube
        rect(tx0 + 1, ty0 + 1, tx1 - tx0 - 2, ty1 - ty0 - 2, 0xFF1A2A36);
        rect(tx0 + 1, ty0 + 1, tx1 - tx0 - 2, 1, 0xFFCFE4F4);
        rect(tx0 - 4, ty0 + 6, 5, 6, 0xFF6A707A);                              // the gas inlet
        int bx0 = x + w / 2 - n * 3 - 2;
        rect(bx0, ty1 - 4, n * 6 + 3, 2, 0xFFDDE8F0);                          // the boat
        int col = mix(0xFF8A94A8, oxide ? 0xFF6A5AE0 : 0xFFE0A860, progress);
        int hi = mix(0xFFC8D0DC, oxide ? 0xFFA8A0FF : 0xFFFFD8A0, progress);
        for (int i = 0; i < n; i++) {
            rect(bx0 + 2 + i * 6, ty0 + 3, 3, ty1 - ty0 - 7, col);
            rect(bx0 + 2 + i * 6, ty0 + 3, 1, ty1 - ty0 - 7, hi);
        }
        if (running && gas) {
            for (int i = 0; i < 7; i++) {                                      // oxygen flowing through
                int d = (int) ((t * 1.3F + i * 9) % (tx1 - tx0 - 4));
                rect(tx0 + 2 + d, ty0 + 3 + (i * 5) % (ty1 - ty0 - 6), 2, 1, 0xFF9AD8FF);
            }
        }
    }

    /** The Photoresist Coater's stages by progress: drop, spin-up, spreading, drying (0..3). */
    public static int coatStage(float progress) {
        return progress < 0.1F ? 0 : progress < 0.3F ? 1 : progress < 0.85F ? 2 : 3;
    }

    /** How much of the wafer the resist film covers at this progress, 0..1. */
    public static float coatCover(float progress) {
        return Math.max(0F, Math.min(1F, (progress - 0.1F) / 0.75F));
    }

    /**
     * The spin coater from above: the catch cup, the (oxidised) wafer turning on its chuck, the resist
     * film spreading out from the centre, droplets spun off its edge, the nozzle dropping resist.
     */
    public static void spinCoater(int x, int y, int w, int h, float t, boolean running, float progress, int resist) {
        frame(x, y, w, h);
        if (resist == 0) {
            resist = 0xFFE07A30;
        }
        int cx = x + w / 2, cy = y + h / 2 + 2, r = Math.min(w, h) / 2 - 5;
        disc(cx, cy, r + 3, 0xFF3A4048);                                       // the catch cup
        disc(cx, cy, r + 2, 0xFF12181E);
        disc(cx, cy, r, 0xFF8A94A8);                                           // the wafer
        disc(cx, cy, r - 1, 0xFF6A5AE0);
        int stage = running ? coatStage(progress) : -1;
        int rr = Math.round((r - 1) * (running ? coatCover(progress) : 0F));
        if (stage == 0 || stage == 1) {
            disc(cx, cy, 2, resist);                                           // the puddle before it spreads
        }
        if (rr > 0) {
            disc(cx, cy, rr, stage == 3 ? mix(resist, 0xFF404040, 0.25F) : resist);
        }
        float a = running && stage > 0 ? t * (stage == 1 ? 0.4F : 0.9F) : 0F;
        for (int k = 0; k < r - 2; k++) {                                      // the turning mark
            rect(cx + (int) (k * Math.cos(a)), cy + (int) (k * Math.sin(a)), 1, 1, k % 3 != 0 ? 0xFFD8E8F8 : 0xFF0A1218);
        }
        if (stage == 2) {
            for (int i = 0; i < 10; i++) {                                     // spun-off droplets
                double b = a * 1.3 + i * 0.63;
                float d = r + 1 + (t * 2 + i * 3) % 3;
                rect(cx + (int) (d * Math.cos(b)), cy + (int) (d * Math.sin(b)), 1, 1, resist);
            }
        }
        rect(cx - 1, y + 3, 3, 6, 0xFF8A909A);                                 // the nozzle
        if (stage == 0) {
            rect(cx, y + 9, 1, 2 + (int) t % 3, resist);
        }
    }

    private static void disc(int cx, int cy, int r, int c) {
        for (int yy = -r; yy <= r; yy++) {
            int half = (int) Math.sqrt(Math.max(0, r * r - yy * yy));
            rect(cx - half, cy + yy, 2 * half + 1, 1, c);
        }
    }

    private static final int UV = 0xFFB070FF, RESIST = 0xFFE07A30;

    /**
     * The stepper's column from the side: the UV lamp, the photomask, the lenses narrowing the beam,
     * the wafer on its stage stepping under it from field to field.
     */
    public static void stepperColumn(int x, int y, int w, int h, float t, boolean running, float progress) {
        frame(x, y, w, h);
        int cx = x + w / 2;
        rect(cx - 10, y + 3, 20, 5, 0xFF3A4048);                                // the lamp
        rect(cx - 8, y + 5, 16, 2, !running ? 0xFF4A3A5A : (int) t % 4 != 0 ? UV : 0xFFD8B0FF);
        rect(cx - 12, y + 11, 24, 2, 0xFF9AB8D0);                              // the mask
        for (int i = -10; i <= 10; i += 3) {
            rect(cx + i, y + 11, 1, 2, 0xFF1A2A36);
        }
        for (int k = 0; k < 3; k++) {                                          // the lenses
            rect(cx - 9 + k * 2, y + 16 + k * 6, 18 - k * 4, 2, 0xFF6A8AA8);
        }
        if (running) {                                                         // the beam
            for (int yy = y + 8; yy < y + h - 12; yy++) {
                float k = (float) (yy - y - 8) / (h - 20);
                int half = (int) (8 - 7 * k);
                rect(cx - half, yy, 2 * half, 1, yy % 2 == 0 ? 0x40B070FF : 0x30B070FF);
            }
        }
        int span = Math.max(4, w - 20);
        int sx = cx - span / 2 + (int) (span * ((progress * 6) % 1F));        // the stage steps
        rect(x + 3, y + h - 7, w - 6, 3, 0xFF6A707A);
        rect(sx - 10, y + h - 10, 20, 3, 0xFF3A4048);
        rect(sx - 9, y + h - 11, 18, 1, RESIST);
        if (running) {
            rect(cx - 1, y + h - 12, 3, 1, 0xFFFFFFFF);
        }
    }

    /** How many dies the die map of radius r shows. */
    public static int dieCount(int r) {
        int n = 0;
        for (int j = -r; j < r; j += 5) {
            for (int i = -r; i < r; i += 5) {
                if ((i + 2.5) * (i + 2.5) + (j + 2.5) * (j + 2.5) <= (r - 2) * (r - 2)) {
                    n++;
                }
            }
        }
        return n;
    }

    /** The wafer from above as a grid of dies: exposed ones violet, the current one flashing white. */
    public static void dieMap(int cx, int cy, int r, int done, float t, boolean running) {
        disc(cx, cy, r + 1, 0xFF8A94A8);
        disc(cx, cy, r, RESIST);
        int idx = 0;
        for (int j = -r; j < r; j += 5) {
            for (int i = -r; i < r; i += 5) {
                if ((i + 2.5) * (i + 2.5) + (j + 2.5) * (j + 2.5) > (r - 2) * (r - 2)) {
                    continue;
                }
                int c = idx < done ? UV : idx == done && running && (int) t % 10 < 5 ? 0xFFFFFFFF : 0xFFB05A20;
                rect(cx + i + 1, cy + j + 1, 4, 4, c);
                idx++;
            }
        }
    }

    /**
     * The etching bath: two baths side by side (the developer, rimmed cyan; the acid, rimmed orange),
     * a carriage on a rail over them dipping the wafer in the first for the first half of the run,
     * in the second for the rest; bubbles in the bath in use.
     */
    public static void etchBaths(int x, int y, int w, int h, float t, boolean running, float progress, int colA, int colB) {
        frame(x, y, w, h);
        progress = Math.max(0F, Math.min(1F, progress));
        int bw = (w - 12) / 2, by = y + h - 22;
        int[] cols = {colA != 0 ? colA : 0xFF8AC8A0, colB != 0 ? colB : 0xFFA0D8B0};
        int[] rims = {0xFF4AA8C8, 0xFFE08A30};
        for (int i = 0; i < 2; i++) {
            int bx = x + 4 + i * (bw + 4);
            rect(bx - 1, by - 1, bw + 2, 19, rims[i]);
            rect(bx, by - 1, bw, 18, 0xFF12181E);
            int top = by + 17 - 11;
            rect(bx, top, bw, by + 17 - top, cols[i]);
            rect(bx, top, bw, 1, mix(cols[i], 0xFFFFFFFF, 0.35F));
        }
        rect(x + 3, y + 5, w - 6, 2, 0xFF6A707A);                              // the rail
        int stage = progress < 0.5F ? 0 : 1;
        float k = running ? (progress - stage * 0.5F) / 0.5F : 0F;
        int cx = x + 4 + bw / 2 + stage * (bw + 4);
        int dip = (int) (10 * Math.sin(Math.PI * Math.min(1F, k * 1.2F)));
        rect(cx - 4, y + 4, 8, 4, 0xFF8A909A);                                 // the carriage
        rect(cx, y + 8, 1, 6 + dip, 0xFFB0B8C4);
        rect(cx - 1, y + 14 + dip, 2, 12, 0xFF8A94A8);                         // the wafer, edge on
        rect(cx + 1, y + 14 + dip, 1, 12, stage == 0 ? 0xFFB070FF : 0xFF6A5AE0);
        if (running) {
            for (int i = 0; i < 5; i++) {                                      // bubbles in the bath in use
                int bx = x + 6 + stage * (bw + 4) + (i * 5 + (int) t) % Math.max(1, bw - 4);
                int bby = y + h - 6 - (int) ((t * 0.7F + i * 3) % 10);
                rect(bx, bby, 1, 1, 0xFFE8F8FF);
            }
        }
    }

    /**
     * The ion implanter's beam line from above: the ion source, the analysing magnet bending the
     * beam up and over, the wafer at the end swept by it and taking colour with the progress.
     */
    public static void beamLine(int x, int y, int w, int h, float t, boolean running, float progress) {
        frame(x, y, w, h);
        progress = Math.max(0F, Math.min(1F, progress));
        int beam = 0xFF6AE0FF, shift = (int) (t * 2);
        int sx = x + 4, sy = y + h - 12;
        rect(sx, sy - 3, 9, 8, 0xFF6A707A);                                    // the source
        rect(sx + 2, sy - 1, 5, 4, running ? 0xFFFFB040 : 0xFF5A4020);
        int mx = x + w / 2 - 6, my = y + h - 16;
        rect(mx, my, 14, 12, 0xFF3A4A8A);                                      // the magnet
        rect(mx + 2, my + 2, 10, 8, 0xFF1A2A5A);
        int cx = x + w - 9, cy = y + 12;
        if (running) {
            for (int i = sx + 9; i < mx + 7; i++) {                            // in, bent up, over to the wafer
                if ((i + shift) % 3 != 0) {
                    rect(i, sy + 1, 1, 1, beam);
                }
            }
            for (int j = y + 12; j < my + 7; j++) {
                if ((j + shift) % 3 != 0) {
                    rect(mx + 7, j, 1, 1, beam);
                }
            }
            for (int i = mx + 7; i < cx - 5; i++) {
                if ((i + shift) % 3 != 0) {
                    rect(i, cy, 1, 1, beam);
                }
            }
        }
        rect(cx - 3, cy - 7, 6, 14, 0xFF8A94A8);                               // the wafer, swept
        rect(cx - 2, cy - 6, 4, 12, mix(0xFF6A5AE0, 0xFF3AA0FF, progress));
        if (running) {
            rect(cx - 3, cy + (int) (5 * Math.sin(t * 0.4F)), 6, 1, 0xFFFFFFFF);
        }
    }

    /**
     * The sputtering chamber from the side: the metal target on top (worn into a groove), the violet
     * argon plasma under it, metal atoms flying down onto the wafer on its stage, the film growing.
     */
    public static void sputterChamber(int x, int y, int w, int h, float t, boolean running, float progress, int metal) {
        frame(x, y, w, h);
        progress = Math.max(0F, Math.min(1F, progress));
        int cx = x + w / 2;
        rect(cx - 20, y + 4, 40, 3, 0xFF6A707A);                               // the target
        rect(cx - 18, y + 7, 36, 3, metal);
        rect(cx - 16, y + 10, 32, 1, mix(metal, 0xFF000000, 0.4F));
        if (running) {
            int[] plasma = {0xFF3A1A5A, 0xFF6A2A9A, 0xFF9A4ADA};
            for (int k = 0; k < 6; k++) {                                      // the plasma
                rect(cx - 16 + k, y + 12 + k, 32 - 2 * k, 1, (k + (int) t) % 4 != 0 ? plasma[k % 3] : 0xFFB070FF);
            }
        }
        int wy = y + h - 9;
        rect(cx - 18, wy + 3, 36, 3, 0xFF8A4A2A);                              // the stage
        rect(cx - 16, wy, 32, 3, 0xFF8A94A8);                                  // the wafer
        int film = Math.max(1, (int) (3 * progress));
        if (running || progress > 0F) {
            rect(cx - 16, wy - film + 1, 32, film, metal);
        }
        if (running) {
            int fall = Math.max(1, wy - y - 20);
            for (int i = 0; i < 9; i++) {                                      // atoms flying down
                rect(cx - 14 + (i * 7) % 28, y + 19 + (int) ((t * 1.2F + i * 4) % fall), 1, 1, mix(metal, 0xFFFFFFFF, 0.3F));
            }
        }
    }

    /**
     * The dicing saw from above: the metallized wafer on its tape frame, the diamond blade (turning)
     * running along the cut lines - four across, then four down - the lines done staying cut, water
     * spraying where it cuts.
     */
    public static void dicingSaw(int x, int y, int w, int h, float t, boolean running, float progress) {
        frame(x, y, w, h);
        progress = Math.max(0F, Math.min(1F, progress));
        int cx = x + w / 2 - 8, cy = y + h / 2, r = Math.min(18, h / 2 - 3);
        disc(cx, cy, r + 3, 0xFF3A4048);                                       // the tape frame
        disc(cx, cy, r + 2, 0xFF12181E);
        disc(cx, cy, r, 0xFF8A94A8);                                           // the wafer
        disc(cx, cy, r - 1, 0xFFD8844A);
        int[] lines = {-9, -3, 3, 9};
        int done = running ? (int) (8 * progress) : 0;
        for (int i = 0; i < 4; i++) {
            int half = (int) Math.sqrt(Math.max(0, (r - 1) * (r - 1) - lines[i] * lines[i]));
            if (i < done) {
                rect(cx + lines[i], cy - half, 1, 2 * half, 0xFF0A1218);
            }
            if (i + 4 < done) {
                rect(cx - half, cy + lines[i], 2 * half, 1, 0xFF0A1218);
            }
        }
        int bx = done < 4 ? cx + lines[Math.min(done, 3)] : cx + 22, by = y + 6;
        disc(bx, by, 5, 0xFF6A707A);                                           // the blade
        disc(bx, by, 4, 0xFFB8C4D8);
        disc(bx, by, 1, 0xFF3A4048);
        float a = running ? t * 0.8F : 0F;
        for (int k = 0; k < 4; k++) {
            double ang = a + k * Math.PI / 2;
            for (int d = 2; d < 4; d++) {
                rect(bx + (int) (d * Math.cos(ang)), by + (int) (d * Math.sin(ang)), 1, 1, 0xFF8A94A8);
            }
        }
        if (running) {
            rect(bx, by + 5, 1, cy - by - 5, 0xFF6AB4F0);                      // the water on the cut
            for (int k = 0; k < 4; k++) {
                rect(bx - 3 + k * 2, by + 6 + (int) ((t * 1.3F + k * 5) % 10), 1, 1, 0xFF9AD8FF);
            }
        }
    }

    /** The Packager's stage by progress: 0 die attach, 1 wire bonding, 2 moulding, 3 done. */
    public static int packStage(float progress) {
        return progress < 0.3F ? 0 : progress < 0.65F ? 1 : progress < 0.95F ? 2 : 3;
    }

    /**
     * The packager: a pick-and-place head on its gantry over a lead frame; the dies go down on the
     * pad, gold wires bond them to the pins, the black body is moulded over - by the stage.
     * @param pins 3, 16 or 40 (the lead frame); dies 1, 2 or 4
     */
    public static void packager(int x, int y, int w, int h, float t, boolean running, float progress, int pins, int dies) {
        frame(x, y, w, h);
        progress = Math.max(0F, Math.min(1F, progress));
        int stage = running ? packStage(progress) : -1;
        rect(x + 3, y + 4, w - 6, 2, 0xFF6A707A);                              // the gantry
        int cx = x + w / 2, cy = y + h / 2 + 6;
        int hx = cx + (running ? (int) (10 * Math.sin(t * 0.15F)) : 0);
        int down = running && stage == 0 ? (int) (4 * Math.abs(Math.sin(t * 0.3F))) : 0;
        rect(hx - 1, y + 6, 3, 8 + down, 0xFF8A909A);                          // the head
        rect(hx - 3, y + 14 + down, 7, 2, 0xFF6A707A);
        int perSide = Math.max(1, pins / 2);
        int pitch = perSide > 10 ? 2 : 3;
        int bw = Math.max(12, perSide * pitch + 4), x0 = cx - bw / 2;
        for (int i = 0; i < perSide; i++) {                                    // the lead frame
            rect(x0 + 2 + i * pitch, cy - 10, 1, 5, 0xFFB0B8C4);
            rect(x0 + 2 + i * pitch, cy + 5, 1, 5, 0xFFB0B8C4);
        }
        if (stage >= 2) {                                                      // moulded
            float k = stage == 2 ? (progress - 0.65F) / 0.3F : 1F;
            rect(x0, cy - 5, bw, 10, 0xFF6A707A);
            rect(x0, cy - 5, Math.max(1, (int) (bw * Math.min(1F, k))), 10, 0xFF22262E);
            if (stage == 3) {
                rect(x0 + 1, cy - 4, 2, 2, 0xFF4A505A);                        // the pin-1 dot
            }
            return;
        }
        rect(x0, cy - 5, bw, 10, 0xFF6A707A);                                  // the pad
        float k = stage == 0 ? progress / 0.3F : 1F;
        int shown = stage < 0 ? 0 : Math.max(0, Math.min(dies, (int) Math.ceil(dies * k)));
        for (int d = 0; d < shown; d++) {                                      // the dies placed
            rect(cx - dies * 3 + d * 6 + 1, cy - 2, 4, 4, 0xFFD8844A);
        }
        if (stage == 1) {
            float kb = (progress - 0.3F) / 0.35F;
            int wires = (int) (perSide * Math.min(1F, kb));
            for (int i = 0; i < wires; i++) {                                  // gold wires bonded
                rect(x0 + 2 + i * pitch, cy - 5, 1, 3, 0xFFE8C850);
                rect(x0 + 2 + i * pitch, cy + 2, 1, 3, 0xFFE8C850);
            }
            rect(hx + (int) (3 * Math.sin(t)), cy - 6, 1, 1, 0xFFFFFFFF);      // the bonding spark
        }
    }

    /**
     * The centrifuge's rotor from above: four tubes on their arms, spinning while it runs, the
     * sample in them taking the main product's colour as it separates.
     */
    public static void centrifuge(int x, int y, int w, int h, float t, boolean running, float progress, int colour) {
        frame(x, y, w, h);
        int cx = x + w / 2, cy = y + h / 2, r = Math.min(w, h) / 2 - 4;
        disc(cx, cy, r + 2, 0xFF3A4048);
        disc(cx, cy, r + 1, 0xFF12181E);
        double a0 = running ? t * 0.9 : 0.4;
        int sample = mix(0xFF8A7A6A, colour != 0 ? colour : 0xFFB08A6A, running ? progress : 0F);
        for (int k = 0; k < 4; k++) {
            double a = a0 + k * Math.PI / 2;
            for (int d = 3; d < r - 5; d++) {                                  // the arm
                rect(cx + (int) (d * Math.cos(a)), cy + (int) (d * Math.sin(a)), 1, 1, 0xFF6A707A);
            }
            int tx = cx + (int) ((r - 4) * Math.cos(a)), ty = cy + (int) ((r - 4) * Math.sin(a));
            disc(tx, ty, 3, 0xFF9AB8D0);                                       // the tube
            disc(tx, ty, 2, sample);
        }
        disc(cx, cy, 3, 0xFF8A909A);
        if (running) {
            for (int i = 0; i < 6; i++) {                                      // motion blur
                double a = a0 + i * 1.05 + 0.4;
                rect(cx + (int) ((r - 1) * Math.cos(a)), cy + (int) ((r - 1) * Math.sin(a)), 1, 1, 0xFF3A5A7A);
            }
        }
    }

    /**
     * The electrolysis cell: brine behind glass, a membrane down the middle, the anode (left) and
     * the cathode (right) on leads from the top with the current running along them, each side
     * bubbling its own gas.
     * @param colA the anode side's gas, @param colB the cathode side's (0: the defaults)
     */
    public static void electrolysisCell(int x, int y, int w, int h, float t, boolean running, int colA, int colB) {
        frame(x, y, w, h);
        int bx0 = x + 4, bx1 = x + w - 4, by0 = y + 12, by1 = y + h - 4;
        rect(bx0 - 1, by0 - 1, bx1 - bx0 + 2, by1 - by0 + 2, 0xFF9AB8D0);    // the glass
        rect(bx0, by0, bx1 - bx0, by1 - by0, 0xFF2A4A6A);                     // the brine
        rect(bx0, by0, bx1 - bx0, 1, 0xFF5A8AB0);
        int mx = (bx0 + bx1) / 2;
        for (int yy = by0; yy < by1; yy += 2) {
            rect(mx, yy, 1, 1, 0xFF8A94A8);                                    // the membrane
        }
        int ax = bx0 + (mx - bx0) / 2, cx = mx + (bx1 - mx) / 2;
        rect(ax - 1, y + 5, 3, by1 - y - 8, 0xFF6A707A);                       // the anode
        rect(cx - 1, y + 5, 3, by1 - y - 8, 0xFFB08A6A);                       // the cathode
        rect(ax - 1, y + 3, cx - ax + 3, 1, 0xFFE8C850);                       // the leads
        if (!running) {
            return;
        }
        int a = colA != 0 ? colA : 0xFFB8D84A, b = colB != 0 ? colB : 0xFFF0F8FF;
        int span = Math.max(1, by1 - by0 - 4);
        for (int i = 0; i < 6; i++) {
            int yy = by1 - 3 - (int) ((t * 0.8F + i * 4) % span);
            rect(ax - 4 + (i % 3) * 3, yy, 1, 1, a);
            rect(cx - 3 + (i % 3) * 3, yy, 1, 1, b);
        }
        rect(ax + (int) (t * 2) % Math.max(1, cx - ax), y + 3, 2, 1, 0xFFFFFFFF);   // the current
    }

    /**
     * The air separator's cryogenic column: the intake fan bottom left blowing air in, the column
     * with its trays frosting over from the bottom with the progress, the draw-offs (oxygen low,
     * argon in the middle), nitrogen vented at the top.
     */
    public static void airColumn(int x, int y, int w, int h, float t, boolean running, float progress) {
        frame(x, y, w, h);
        progress = Math.max(0F, Math.min(1F, progress));
        int fx = x + 3, fy = y + h - 12;
        rect(fx, fy, 10, 9, 0xFF6A707A);                                       // the fan
        rect(fx + 1, fy + 1, 8, 7, 0xFF1A2430);
        double a = running ? t * 1.2 : 0;
        for (int k = 0; k < 3; k++) {
            double aa = a + k * 2.09;
            rect(fx + 5 + (int) (3 * Math.cos(aa)), fy + 4 + (int) (3 * Math.sin(aa)), 1, 1, 0xFFB0B8C4);
        }
        int c0 = x + w / 2 - 4, c1 = x + w / 2 + 6;
        rect(c0 - 1, y + 4, c1 - c0 + 2, h - 8, 0xFF9AB8D0);                  // the column
        rect(c0, y + 5, c1 - c0, h - 10, 0xFF1A2A36);
        int frost = running ? (int) ((h - 10) * progress) : 0;
        for (int yy = y + h - 5 - frost; yy < y + h - 5; yy++) {
            rect(c0, yy, c1 - c0, 1, yy % 3 != 0 ? 0xFF4A7A9A : 0xFF8AC8E8);
        }
        for (int i = 1; i < 6; i++) {
            rect(c0, y + 5 + i * (h - 10) / 6, c1 - c0, 1, 0xFF6A8AA8);        // the trays
        }
        rect(fx + 10, fy + 4, c0 - fx - 11, 2, 0xFF6A707A);                   // the air pipe
        rect(c1 + 1, y + h - 14, 4, 2, 0xFF8AB0E8);                            // oxygen, low
        rect(c1 + 1, y + h / 2, 4, 2, 0xFFB070FF);                             // argon, the middle
        if (running) {
            int run = Math.max(1, c0 - fx - 11);
            for (int i = 0; i < 3; i++) {
                rect(fx + 10 + (int) ((t * 1.5F + i * 5) % run), fy + 4, 1, 1, 0xFFE8F8FF);
            }
            for (int i = 0; i < 3; i++) {                                      // nitrogen vented
                rect(x + w / 2 - 1 + i * 2, y + 2, 1, 1 + (int) (t + i) % 2, 0xFFD8E8F8);
            }
        }
    }

    private static final int[] FRACTIONS = {0xFFE8E8C8, 0xFFE8C850, 0xFFD8A040, 0xFF8A5A2A};

    /**
     * The refinery's fractionating tower: the furnace under it, vapour rising, four fraction bands
     * (gas, petrol, diesel, residue) with their draw-offs, the one being made glowing.
     */
    public static void refineryTower(int x, int y, int w, int h, float t, boolean running, int hot) {
        frame(x, y, w, h);
        int c0 = x + w / 2 - 8, c1 = x + w / 2 + 6;
        rect(c0 - 1, y + 3, c1 - c0 + 2, h - 14, 0xFF8A909A);
        rect(c0, y + 4, c1 - c0, h - 16, 0xFF1A2430);
        int band = (h - 16) / 4;
        for (int i = 0; i < 4; i++) {
            int yy = y + 4 + i * band;
            rect(c0, yy, c1 - c0, band, mix(0xFF1A2430, FRACTIONS[i], 0.35F));
            rect(c0, yy, c1 - c0, 1, 0xFF6A707A);
            rect(c1 + 1, yy + band / 2, 5, 2, i == hot ? FRACTIONS[i] : mix(FRACTIONS[i], 0xFF0A1218, 0.6F));
        }
        rect(c0 - 3, y + h - 11, c1 - c0 + 6, 8, 0xFF3A2418);                 // the furnace
        if (running) {
            for (int i = 0; i < 4; i++) {
                int fh = 2 + (int) (2 + 2 * Math.sin(t * 0.7F + i));
                rect(c0 - 1 + i * 4, y + h - 3 - fh, 2, fh, 0xFFFFB040);
            }
            int rise = Math.max(1, h - 18);
            for (int i = 0; i < 6; i++) {                                      // vapour rising
                rect(c0 + 2 + (i * 3) % 10, y + h - 13 - (int) ((t * 1.1F + i * 6) % rise), 1, 1, 0xFFE8E0D0);
            }
        }
    }

    /**
     * The rolling mill from the side: the ingot coming in from the left, two rolls squeezing it
     * into a strip that grows with the progress, the press over it stamping with the mold.
     */
    public static void rollingMill(int x, int y, int w, int h, float t, boolean running, float progress, int metal) {
        frame(x, y, w, h);
        progress = Math.max(0F, Math.min(1F, progress));
        if (metal == 0) {
            metal = 0xFFD8844A;
        }
        int ly = y + h / 2 + 2;
        rect(x + 3, ly + 3, w - 6, 2, 0xFF3A4048);                             // the table
        int rx = x + 26;
        for (int s = -1; s <= 1; s += 2) {                                     // the rolls
            int cy = ly + (s < 0 ? -7 : 9);
            disc(rx, cy, 6, 0xFF6A707A);
            disc(rx, cy, 5, 0xFF9AA0AA);
            disc(rx, cy, 1, 0xFF3A4048);
            double a = running ? s * t * 0.6 : 0;
            for (int k = 0; k < 3; k++) {
                double aa = a + k * 2.09;
                rect(rx + (int) (3 * Math.cos(aa)), cy + (int) (3 * Math.sin(aa)), 1, 1, 0xFF4A505A);
            }
        }
        rect(x + 4, ly - 3, rx - 10 - x, 6, metal);                            // the ingot
        rect(x + 4, ly - 3, rx - 10 - x, 1, mix(metal, 0xFFFFFFFF, 0.4F));
        int sx = rx + 6, len = running || progress > 0F ? (int) ((x + w - 8 - sx) * progress) : 0;
        if (len > 0) {
            rect(sx, ly + 1, len, 2, metal);                                   // the strip
            rect(sx, ly + 1, len, 1, mix(metal, 0xFFFFFFFF, 0.4F));
        }
        int px = x + w - 20;
        rect(px - 1, y + 3, 14, 3, 0xFF6A707A);                                // the press
        rect(px + 5, y + 6, 2, 5, 0xFF8A909A);
        int down = running ? (int) (4 * Math.max(0, Math.sin(t * 0.25F))) : 0;
        rect(px, y + 11 + down, 12, 4, 0xFF8A5AA0);                            // the mold
        if (running && len > 0) {
            for (int i = 0; i < 3; i++) {
                rect(sx + (int) ((t * 1.3F + i * 6) % len), ly + 4, 1, 1, 0xFFFFD8A0);
            }
        }
    }

    /**
     * The upgrade station's bench: the piece (a helmet shape, or a chip) on a pedestal under a
     * ring of light, an arm fitting the parts, the piece shifting from the old tier's colour to
     * the new one with the progress, sparks at the join.
     */
    public static void upgradeBench(int x, int y, int w, int h, float t, boolean running, float progress, int from, int to, boolean chip) {
        frame(x, y, w, h);
        progress = Math.max(0F, Math.min(1F, progress));
        int cx = x + w / 2, cy = y + h - 9;
        rect(cx - 14, cy + 2, 28, 4, 0xFF6A707A);                              // the pedestal
        rect(cx - 12, cy + 1, 24, 1, 0xFF9AA0AA);
        for (int i = 0; i < 9; i++) {                                          // the ring of light
            double a = t * 0.2 + i * 0.7;
            rect(cx + (int) (15 * Math.cos(a)), y + 10 + (int) (4 * Math.sin(a)), 2, 1, running ? 0xFF6AE0FF : 0xFF2A4A5A);
        }
        int c = mix(from, to, running ? progress : 0F);
        if (chip) {                                                            // a chip: a package with pins
            for (int i = 0; i < 5; i++) {
                rect(cx - 9 + i * 4, cy - 17, 1, 3, 0xFFB0B8C4);
                rect(cx - 9 + i * 4, cy - 2, 1, 3, 0xFFB0B8C4);
            }
            rect(cx - 11, cy - 14, 22, 12, c);
            rect(cx - 8, cy - 11, 6, 6, mix(c, 0xFFFFFFFF, 0.35F));
        } else {                                                               // a helmet
            rect(cx - 8, cy - 16, 16, 4, c);
            rect(cx - 12, cy - 12, 24, 12, c);
            rect(cx - 8, cy - 8, 16, 4, 0xFF0A1218);
            rect(cx - 6, cy - 8, 12, 2, 0xFF8AE8FF);
        }
        rect(x + 6, y + 4, 3, 16, 0xFF6A707A);                                 // the arm
        rect(x + 6, y + 18, 18 + (running ? (int) (6 * Math.sin(t * 0.3F)) : 0), 3, 0xFF8A909A);
        if (running) {
            for (int i = 0; i < 3; i++) {
                rect(cx - 10 + (int) (4 * Math.sin(t + i)), cy - 10 + i * 2, 1, 1, 0xFFFFE08A);
            }
        }
    }

    /**
     * The kiln: a brick wall with an arched chamber glowing more as the firing goes on, the piece
     * on a shelf taking its fired colour, embers under it.
     */
    public static void kiln(int x, int y, int w, int h, float t, boolean running, float progress, int from, int to) {
        frame(x, y, w, h);
        progress = Math.max(0F, Math.min(1F, progress));
        int bx0 = x + 5, bx1 = x + w - 5, by0 = y + 3, by1 = y + h - 3;
        for (int yy = by0, row = 0; yy + 3 <= by1; yy += 4, row++) {          // bricks
            int off = row % 2 == 0 ? 0 : 4;
            for (int xx = bx0 - off; xx < bx1; xx += 8) {
                int a = Math.max(bx0, xx), b = Math.min(bx1, xx + 7);
                rect(a, yy, b - a, 3, 0xFF7A3E2A);
                rect(a, yy, b - a, 1, 0xFF9A5238);
            }
        }
        int cx = (bx0 + bx1) / 2, cw = (bx1 - bx0) / 2 - 6, top = by0 + 6, bot = by1 - 3;
        float heat = running ? 0.3F + 0.7F * progress : 0.15F;
        for (int yy = top; yy < bot; yy++) {                                   // the arched chamber
            float k = (float) (yy - top) / Math.max(1, bot - top);
            int half = (int) (cw * Math.sqrt(Math.min(1F, k * 2.5F)));
            rect(cx - half, yy, 2 * half, 1, mix(0xFF2A1208, 0xFFE0701E, heat * k));
        }
        rect(cx - cw + 2, bot - 6, 2 * cw - 4, 2, 0xFF8A909A);                // the shelf
        rect(cx - 5, bot - 12, 10, 6, mix(from, to, running ? progress : 0F)); // the piece
        if (running) {
            for (int i = 0; i < 6; i++) {
                int fx = cx - cw + 4 + i * (2 * cw - 8) / 5;
                int fh = 1 + (int) (1.5 + 1.5 * Math.sin(t * 0.8F + i));
                rect(fx, bot - fh, 2, fh, 0xFFFFB040);
            }
        }
    }

    /** The firing curve: heat up, hold, cool down, the part done bright, a marker at the progress. */
    public static void firingCurve(int x, int y, int w, int h, float progress) {
        frame(x, y, w, h);
        progress = Math.max(0F, Math.min(1F, progress));
        for (int i = 0; i < w - 4; i++) {
            float k = (float) i / (w - 5);
            float v = k < 0.3F ? k / 0.3F : k < 0.75F ? 1F : Math.max(0F, 1 - (k - 0.75F) / 0.25F * 0.7F);
            rect(x + 2 + i, y + h - 3 - (int) ((h - 6) * v), 1, 1, i < (w - 4) * progress ? 0xFFFF8C1E : 0xFF5A3A1A);
        }
        rect(x + 2 + (int) ((w - 4) * progress), y + 2, 1, h - 4, 0xFFFFFFFF);
    }

    /** The firing's stage: 0 heating up, 1 holding, 2 cooling. */
    public static int firingStage(float progress) {
        return progress < 0.3F ? 0 : progress < 0.75F ? 1 : 2;
    }

    /** A capsule: a cap, glass, filled to `level` (0..1) in colour `c`. */
    public static void capsule(int x, int y, int w, int h, float level, int c) {
        rect(x + 2, y, w - 4, 2, 0xFF8A909A);
        rect(x, y + 2, w, h - 2, 0xFF9AB8D0);
        rect(x + 1, y + 3, w - 2, h - 4, 0xFF12181E);
        int fill = (int) ((h - 4) * Math.max(0F, Math.min(1F, level)));
        if (fill > 0) {
            rect(x + 1, y + h - 1 - fill, w - 2, fill, c);
            rect(x + 1, y + h - 1 - fill, w - 2, 1, mix(c, 0xFFFFFFFF, 0.4F));
        }
    }

    /** The cell filler: a nozzle over a capsule on a little conveyor, the fluid running down into it as it fills. */
    public static void cellFiller(int x, int y, int w, int h, float t, boolean running, float progress, int c) {
        frame(x, y, w, h);
        if (c == 0) {
            c = 0xFF9AD8FF;
        }
        int cx = x + w / 2;
        rect(x + 3, y + h - 7, w - 6, 3, 0xFF6A707A);                          // the conveyor
        for (int i = x + 5; i < x + w - 6; i += 5) {
            rect(i + (running ? (int) t % 5 : 0), y + h - 6, 1, 1, 0xFF3A4048);
        }
        rect(cx - 6, y + 3, 12, 5, 0xFF6A707A);                                // the nozzle
        rect(cx - 1, y + 8, 3, 5, 0xFF8A909A);
        capsule(cx - 6, y + 16, 12, h - 24, running ? progress : 0F, c);
        if (running) {
            rect(cx, y + 13, 1, 3, c);
            for (int i = 0; i < 2; i++) {
                rect(cx - 1 + i * 2, y + 14 + (int) (t * 2 + i) % 3, 1, 1, mix(c, 0xFFFFFFFF, 0.5F));
            }
        }
    }

    /**
     * The boiler: the water drum bubbling over a firebox (coal lumps, or a diesel burner) with its
     * flames, steam leaving by the pipe on top.
     */
    public static void boiler(int x, int y, int w, int h, float t, boolean running, boolean coal) {
        frame(x, y, w, h);
        int cx = x + w / 2, dw = w - 8, d0 = cx - dw / 2;
        rect(d0, y + 10, dw, h - 30, 0xFF8A909A);                              // the drum
        rect(d0 + 1, y + 11, dw - 2, h - 32, 0xFF1A2A3A);
        int lv = y + 11 + (int) ((h - 32) * 0.35F);
        rect(d0 + 1, lv, dw - 2, y + h - 21 - lv, 0xFF3A6AB0);                 // the water
        rect(cx - 2, y + 3, 4, 8, 0xFF8A909A);                                 // the steam pipe
        int fy = y + h - 19;
        rect(d0, fy, dw, 16, 0xFF3A2418);                                      // the firebox
        rect(d0 + 2, fy + 2, dw - 4, 12, 0xFF1A0A06);
        if (coal) {
            for (int i = 0; i < 4; i++) {
                rect(d0 + 3 + i * (dw - 6) / 4, fy + 10, 3, 3, 0xFF2A2A2E);
            }
        } else {
            rect(cx - 6, fy + 11, 12, 2, 0xFF6A707A);                          // the burner
        }
        if (!running) {
            return;
        }
        int rise = Math.max(1, y + h - 23 - lv);
        for (int i = 0; i < 6; i++) {                                          // bubbles
            rect(d0 + 3 + (i * 7) % Math.max(1, dw - 6), y + h - 23 - (int) ((t * 0.9F + i * 3) % rise), 1, 1, 0xFFB8D8F8);
        }
        for (int i = 0; i < 3; i++) {                                          // steam out
            rect(cx - 1 + i, y + 2, 1, 1 + (int) (t + i) % 2, 0xFFE0E8F0);
        }
        for (int i = 0; i < 4; i++) {                                          // flames
            int fh = 3 + (int) (2 + 2 * Math.sin(t * 0.8F + i));
            rect(d0 + 4 + i * (dw - 8) / 4, fy + 10 - fh, 2, fh, 0xFFFFB040);
        }
    }

    /** A little pressure dial: ticks round the top, the last ones red, the needle at `v` (0..1). */
    public static void dial(int cx, int cy, int r, float v) {
        disc(cx, cy, r + 1, 0xFF8A909A);
        disc(cx, cy, r, 0xFF12181E);
        for (int i = 0; i < 9; i++) {
            double a = Math.PI * (0.75 + i * 1.5 / 8);
            rect(cx + (int) ((r - 2) * Math.cos(a)), cy + (int) ((r - 2) * Math.sin(a)), 1, 1, i > 6 ? 0xFFE63C3C : 0xFFB0B8C4);
        }
        double a = Math.PI * (0.75 + 1.5 * Math.max(0F, Math.min(1F, v)));
        for (int d = 0; d < r - 2; d++) {
            rect(cx + (int) (d * Math.cos(a)), cy + (int) (d * Math.sin(a)), 1, 1, 0xFFFF8C1E);
        }
    }

    /**
     * A one-cylinder engine: the piston going up and down in its finned cylinder, the spark and the
     * burst at the top of the stroke, the con-rod turning the flywheel, smoke out of the exhaust.
     */
    public static void engine(int x, int y, int w, int h, float t, boolean running) {
        frame(x, y, w, h);
        int cx = x + w / 3 + 2, cy0 = y + 10, cy1 = y + h / 2 + 8;
        rect(cx - 9, cy0, 18, cy1 - cy0, 0xFF8A909A);                          // the cylinder
        rect(cx - 7, cy0 + 2, 14, cy1 - cy0 - 2, 0xFF1A2430);
        for (int i = 0; i < 4; i++) {                                          // its fins
            rect(cx - 11, cy0 + 3 + i * 5, 2, 3, 0xFF6A707A);
            rect(cx + 9, cy0 + 3 + i * 5, 2, 3, 0xFF6A707A);
        }
        double a = running ? t * 0.5 : 0;
        int py = cy0 + 5 + (int) (6 * (1 - Math.cos(a)));
        if (running && Math.cos(a) > 0.8) {
            rect(cx - 6, cy0 + 2, 12, py - cy0 - 2, 0xFFFFB040);              // the burst
            rect(cx - 1, cy0 + 1, 2, 2, 0xFFFFFFFF);                           // the spark
        }
        rect(cx - 7, py, 14, 5, 0xFFB0B8C4);                                   // the piston
        rect(cx - 7, py, 14, 1, 0xFFD8E0E8);
        int fx = cx, fy = y + h - 11, fr = 8;
        disc(fx, fy, fr, 0xFF6A707A);                                          // the flywheel
        disc(fx, fy, fr - 2, 0xFF12181E);
        disc(fx, fy, 2, 0xFF8A909A);
        int px = fx + (int) ((fr - 3) * Math.sin(a)), pyy = fy - (int) ((fr - 3) * Math.cos(a));
        for (int k = 0; k < 10; k++) {                                         // the con-rod
            rect(cx + (px - cx) * k / 9, py + 5 + (pyy - py - 5) * k / 9, 2, 1, 0xFFB0B8C4);
        }
        int ex = cx + 11;
        rect(ex, cy0 + 4, 12, 3, 0xFF6A707A);                                  // the exhaust
        rect(ex + 10, cy0 - 4, 3, 10, 0xFF6A707A);
        if (running) {
            for (int i = 0; i < 4; i++) {
                float d = (t * 0.7F + i * 4) % 14;
                rect(ex + 10 + (int) (d / 3), cy0 - 5 - (int) (d / 2), 2, 2, mix(0xFF8A8A92, 0xFF0A1218, d / 14F));
            }
        }
    }

    /**
     * The sky over a solar panel: a day or night gradient (greyed in the rain), stars by night, the
     * sun or the moon on its arc at `arc` (0 rising .. 1 setting), rain streaks, the panel's cells
     * on their stand, light rays hitting them on a clear day. `covered`: something over the panel.
     */
    public static void sky(int x, int y, int w, int h, float t, boolean day, boolean rain, float arc, boolean covered) {
        rect(x, y, w, h, 0xFF1E3444);
        int top = day ? 0xFF3A7AC8 : 0xFF0A1024, bot = day ? 0xFF9AC8F0 : 0xFF1A2A4A;
        if (rain) {
            top = mix(top, 0xFF5A6070, 0.6F);
            bot = mix(bot, 0xFF7A8090, 0.6F);
        }
        for (int yy = y + 1; yy < y + h - 1; yy++) {
            rect(x + 1, yy, w - 2, 1, mix(top, bot, (float) (yy - y) / h));
        }
        if (!day) {
            for (int i = 0; i < 12; i++) {
                rect(x + 3 + (i * 17) % Math.max(1, w - 6), y + 3 + (i * 7) % Math.max(1, h / 2), 1, 1, 0xFFE0E8FF);
            }
        }
        arc = Math.max(0F, Math.min(1F, arc));
        int cx = x + w / 2 + (int) ((w / 2 - 8) * Math.cos(Math.PI * (1 - arc)));
        int cy = y + h / 2 + 2 - (int) ((h / 2 - 6) * Math.sin(Math.PI * arc));
        if (day) {
            disc(cx, cy, 5, 0xFFFFE070);
            disc(cx, cy, 3, 0xFFFFF4B0);
        } else {
            disc(cx, cy, 4, 0xFFD8E0F0);
            disc(cx + 2, cy - 1, 3, top);
        }
        int px0 = x + 6, px1 = x + w - 6, py = y + h - 12;
        if (covered) {                                                         // a roof over the panel
            rect(x + 1, py - 8, w - 2, 4, 0xFF5A4A3A);
        }
        for (int i = px0; i + 5 <= px1; i += 6) {                              // the panel
            rect(i, py, 5, 4, 0xFF2A3A8A);
            rect(i, py, 5, 1, 0xFF4A6AC8);
        }
        rect(px0 - 1, py + 4, px1 - px0 + 2, 1, 0xFF8A909A);
        rect(x + w / 2 - 1, py + 5, 2, y + h - py - 6, 0xFF6A707A);
        if (day && !rain && !covered) {
            for (int i = 0; i < 4; i++) {
                float k = (t * 0.05F + i * 0.25F) % 1F;
                int tx = px0 + 6 + i * (px1 - px0 - 12) / 3;
                rect((int) (cx + (tx - cx) * k), (int) (cy + (py - cy) * k), 1, 1, 0xFFFFF0A0);
            }
        }
        if (rain) {
            for (int i = 0; i < 14; i++) {
                rect(x + 3 + (i * 11 + (int) (t * 2)) % Math.max(1, w - 6), y + 2 + (int) ((t * 3 + i * 7) % Math.max(1, h - 4)), 1, 3, 0xFF8AB0E8);
            }
        }
    }

    /**
     * A steam turbine from the side: steam in by the left pipe, the casing with its blade rows
     * (longer down the flow), the blades flickering as they turn, the shaft out to the right.
     */
    public static void turbineSide(int x, int y, int w, int h, float t, boolean running) {
        frame(x, y, w, h);
        int cy = y + h / 2;
        rect(x + 2, cy - 3, 9, 6, 0xFF6A707A);                                 // steam in
        if (running) {
            for (int i = 0; i < 3; i++) {
                rect(x + 2 + (int) ((t * 1.5F + i * 3) % 9), cy - 1, 2, 2, 0xFFE0E8F0);
            }
        }
        int c0 = x + 11, c1 = x + w - 16;
        rect(c0, cy - 14, c1 - c0, 28, 0xFF8A909A);                            // the casing
        rect(c0 + 1, cy - 13, c1 - c0 - 2, 26, 0xFF1A2430);
        for (int k = 0; k < 5; k++) {                                          // blade rows
            int bx = c0 + 4 + k * (c1 - c0 - 8) / 4, bh = 6 + k * 2;
            for (int j = -bh; j <= bh; j += 2) {
                boolean on = running && (j + (int) (t * 2) + k) % 4 == 0;
                rect(bx, cy + j, 2, 1, on ? 0xFFD8E0E8 : 0xFF8A94A8);
            }
        }
        rect(c0, cy - 1, x + w - 3 - c0, 2, 0xFFB0B8C4);                        // the shaft
    }

    /** A turbine rotor end-on: curved blades turning while it runs, the hub in the middle. */
    public static void rotorFront(int cx, int cy, int r, float t, boolean running) {
        disc(cx, cy, r + 2, 0xFF8A909A);
        disc(cx, cy, r + 1, 0xFF12181E);
        double a0 = running ? t * 0.6 : 0;
        for (int k = 0; k < 10; k++) {
            double a = a0 + k * Math.PI / 5;
            for (int d = 3; d < r; d++) {
                rect(cx + (int) (d * Math.cos(a + d * 0.04)), cy + (int) (d * Math.sin(a + d * 0.04)), 1, 1, 0xFFB0B8C4);
            }
        }
        disc(cx, cy, 3, 0xFF6A707A);
    }

    /** A colour between a and b (k 0..1), opaque. */
    public static int mix(int a, int b, float k) {
        k = Math.max(0F, Math.min(1F, k));
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * k);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * k);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * k);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** A thermometer beside the furnace: bulb, fill in the zone colour, marks at 0 / 50 / resume / 100. */
    public static void thermometer(int x, int y, int h, float heat, float resume) {
        heat = Math.max(0F, Math.min(1F, heat));
        rect(x + 2, y, 7, h - 8, 0xFF8A909A);
        rect(x + 3, y + 1, 5, h - 9, 0xFF101418);
        rect(x, y + h - 10, 11, 10, 0xFF8A909A);
        rect(x + 1, y + h - 9, 9, 8, heat > 0F ? 0xFFE63C3C : 0xFF3A2020);
        rect(x + 2, y + h - 8, 2, 2, 0xFFFF9090);
        int col = heat < resume ? 0xFF5AE66E : heat < 0.9F ? 0xFFFF8C1E : 0xFFE63C3C;
        int top = y + 1 + Math.round((h - 10) * (1 - heat));
        rect(x + 4, top, 3, y + h - 9 - top, col);
        float[] marks = {0F, 0.5F, resume, 1F};
        int[] cols = {0xFF3C4048, 0xFF3C4048, 0xFFF0C450, 0xFFE63C3C};
        for (int i = 0; i < marks.length; i++) {
            rect(x + 9, y + 1 + Math.round((h - 10) * (1 - marks[i])), 3, 1, cols[i]);
        }
    }

    /** The heat as 20 segments in zone colours (green to resume, orange, red), marks at resume and 100. */
    public static void heatBar(int x, int y, int w, int h, float heat, float resume) {
        rect(x - 1, y - 1, w + 2, h + 2, 0xFF04080C);
        int n = 20;
        for (int i = 0; i < n; i++) {
            int a = x + i * w / n, b = x + (i + 1) * w / n - 1;
            float v = (i + 0.5F) / n;
            int col = v < resume ? 0xFF5AE66E : v < 0.9F ? 0xFFFF8C1E : 0xFFE63C3C;
            boolean on = v <= heat;
            rect(a, y, b - a, h, on ? col : 0xFF16222E);
            rect(a, y, b - a, 1, on ? (col | 0xFF505050) : 0xFF20303E);
        }
        rect(x + Math.round(w * resume), y - 3, 1, h + 5, 0xFFF0C450);
        rect(x + w - 1, y - 3, 1, h + 5, 0xFFE63C3C);
    }

    /**
     * The Chemical Reactor's flask: a round-bottom flask fed from both sides at the top (in the
     * two input fluids' colours), draining at the bottom, the mix in the product's colour, bubbling
     * while it works; the mix rises with the recipe's progress.
     */
    public static void flask(int x, int y, int w, int h, float t, boolean running, int colA, int colB, int colOut, float progress) {
        frame(x, y, w, h);
        int cx = x + w / 2, r = Math.min(w / 2 - 3, 11), cy = y + h - r - 5;
        int mix = colOut != 0 ? colOut : 0xFF7ABCA8;
        float level = running ? 0.35F + 0.4F * Math.max(0F, Math.min(1F, progress)) : 0.3F;
        int wl = cy + r - Math.round(2 * r * level);
        for (int yy = cy - r; yy <= cy + r; yy++) {
            for (int xx = cx - r; xx <= cx + r; xx++) {
                double d = Math.hypot(xx + 0.5 - cx, yy + 0.5 - cy);
                if (d > r) {
                    continue;
                }
                if (d >= r - 1) {
                    rect(xx, yy, 1, 1, 0xFFB0C8D8);                            // the glass
                } else if (yy >= wl) {
                    rect(xx, yy, 1, 1, (xx + yy) % 3 != 0 ? mix : lighter(mix));
                }
            }
        }
        rect(cx - 3, y + 7, 6, cy - r - y - 6, 0xFFB0C8D8);                 // the neck
        rect(cx - 2, y + 7, 4, cy - r - y - 6, 0xFF0A1218);
        rect(x + 3, y + 5, cx - 3 - x - 3, 2, colA != 0 ? colA : 0xFF3A4450);  // the feeds
        rect(cx + 3, y + 5, x + w - 3 - cx - 3, 2, colB != 0 ? colB : 0xFF3A4450);
        rect(cx - 3, y + 5, 6, 2, 0xFF8A909A);
        rect(cx - 1, cy + r, 2, y + h - 2 - cy - r, mix);                    // the drain
        if (running) {
            for (int i = 0; i < 5; i++) {                                     // bubbles
                int bx = cx - r + 3 + (i * 5) % Math.max(1, 2 * r - 4);
                int by = cy + r - 2 - (int) ((t * 0.8F + i * 3) % Math.max(1, cy + r - wl - 1));
                if (by > wl) {
                    rect(bx, by, 1, 1, 0xFFE8FFF8);
                }
            }
            int drop = y + 8 + (int) ((t * 1.2F) % Math.max(1, cy - r - y - 7));    // a drop running down the neck
            rect(cx - 1, drop, 2, 2, mix);
        }
    }

    /**
     * The CVD Chamber: a Siemens bell jar - a glass dome over two glowing silicon rods and their
     * bridge on a base plate; the rods thicken as silicon deposits (with the recipe's progress),
     * gas swirls inside while it works, the two gas inlets in their fluids' colours.
     */
    public static void bell(int x, int y, int w, int h, float t, boolean running, float progress, int colA, int colB) {
        frame(x, y, w, h);
        int cx = x + w / 2, base = y + h - 8;
        rect(x + 4, base, w - 8, 3, 0xFF6A707A);                               // the base plate
        rect(x + 4, base, w - 8, 1, 0xFF9AA0AA);
        int rw = (w - 16) / 2, rh = h - 20;
        for (int yy = base - rh; yy < base; yy++) {                           // the dome
            double k = (double) (yy - (base - rh)) / rh;
            int half = (int) (rw * Math.sqrt(Math.max(0.0, 1 - (1 - k) * (1 - k))));
            rect(cx - half - 1, yy, 1, 1, 0xFF9AB8D0);
            rect(cx + half, yy, 1, 1, 0xFF9AB8D0);
            if (half > 0) {
                rect(cx - half, yy, 2 * half, 1, 0xFF0E1C28);
            }
        }
        rect(cx - 2, base - rh - 1, 4, 1, 0xFF9AB8D0);
        int thick = 1 + Math.round(3 * Math.max(0F, Math.min(1F, progress)));
        int glow = running ? 0xFFFF9A40 : 0xFF8A5A30, hot = running ? 0xFFFFD8A0 : 0xFFA07850;
        for (int rx0 : new int[]{cx - 9, cx + 6}) {                           // the rods
            rect(rx0 - (thick - 1) / 2, base - rh + 8, thick + 1, rh - 8, glow);
            rect(rx0 - (thick - 1) / 2, base - rh + 8, 1, rh - 8, hot);
        }
        rect(cx - 9, base - rh + 7, 16, thick, glow);                          // the bridge
        if (running) {
            for (int i = 0; i < 8; i++) {                                     // swirling gas
                double a = t * 0.3F + i * 0.8;
                int px = cx + (int) ((rw - 4) * Math.cos(a) * 0.8);
                int py = base - 6 - (int) ((rh - 10) * ((i * 0.13 + t * 0.02) % 1.0));
                rect(px, py, 1, 1, i % 2 == 0 ? (colA != 0 ? colA : 0xFFB8D8A0) : (colB != 0 ? colB : 0xFFD8E8F8));
            }
        }
        rect(x + 3, base + 3, 8, 2, colA != 0 ? colA : 0xFF3A4450);           // the gas inlets
        rect(x + w - 11, base + 3, 8, 2, colB != 0 ? colB : 0xFF3A4450);
    }

    private static int lighter(int c) {
        int r = Math.min(255, ((c >> 16) & 255) + 50), g = Math.min(255, ((c >> 8) & 255) + 50), b = Math.min(255, (c & 255) + 50);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static void rect(int x, int y, int w, int h, int c) {
        if (w > 0 && h > 0) {
            Gui.drawRect(x, y, x + w, y + h, c);
        }
    }
}
