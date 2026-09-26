package com.sc.inventory;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.IIcon;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/**
 * The quarry's tank gauge (the Tanks tab): a bolted steel frame, a nameplate in the fluid's own
 * texture with a status lamp, ten fluid segments beside a level colour column, ticks on the
 * frame, a double-chevron pointer in a side slot and the percentage in pixel digits below. The
 * lamp, the chevron and the digits share one colour: green - holding fluid, orange - full, red -
 * empty, blue - pinned to a fluid but empty. A locked compartment shows a padlock.
 * Size: WIDTH x HEIGHT GUI pixels.
 */
@SideOnly(Side.CLIENT)
public final class GuiTankGaugeSC {

    public static final int WIDTH = 31, HEIGHT = 70;
    private static final int FW = 24, FH = 62;
    private static final int STEEL = 0xFF787E88, STEEL_HI = 0xFFBEC4CE, STEEL_SH = 0xFF464A52, BOLT = 0xFFC8CED8,
            DARK = 0xFF12141A, OFF = 0xFF242A34, SLOT = 0xFF3C4048, SLOT_TOP = 0xFF282C34;
    public static final int GREEN = 0xFF5AE66E, ORANGE = 0xFFFF9628, RED = 0xFFE63C3C, BLUE = 0xFF78A0FF, YELLOW = 0xFFF0DC46;
    private static final int[] LEVEL_COLS = {GREEN, GREEN, GREEN, GREEN, GREEN, GREEN, YELLOW, YELLOW, ORANGE, ORANGE};
    private static final String[] DIGITS = {"111101101101111", "010110010010111", "111001111100111", "111001111001111",
            "101101111001001", "111100111001111", "111100111101111", "111001010010010", "111101111101111", "111101111001111"};
    private static final String PERCENT = "101001010100101", DASH = "000000111000000";

    private GuiTankGaugeSC() {
    }

    /** The lamp / pointer / digits colour for a compartment. */
    public static int status(FluidStack st, int capacity, String pinned) {
        int amount = st == null ? 0 : st.amount;
        if (amount <= 0) {
            return pinned != null ? BLUE : RED;
        }
        return amount >= capacity ? ORANGE : GREEN;
    }

    public static void draw(Minecraft mc, int x, int y, FluidStack st, int capacity, String pinned, boolean locked) {
        Fluid shown = st != null && st.amount > 0 ? st.getFluid() : pinned != null ? FluidRegistry.getFluid(pinned) : null;
        float level = st == null || capacity <= 0 ? 0F : Math.min(1F, (float) st.amount / capacity);
        int col = status(st, capacity, pinned);
        // the frame and its bolts
        rect(x, y, FW, FH, STEEL);
        rect(x, y, FW, 1, STEEL_HI);
        rect(x, y, 1, FH, STEEL_HI);
        rect(x + FW - 1, y, 1, FH, STEEL_SH);
        rect(x, y + FH - 1, FW, 1, STEEL_SH);
        int[][] bolts = {{2, 2}, {FW - 4, 2}, {2, FH - 4}, {FW - 4, FH - 4}};
        for (int[] b : bolts) {
            rect(x + b[0], y + b[1], 2, 2, STEEL_SH);
            rect(x + b[0], y + b[1], 1, 1, BOLT);
        }
        // the nameplate and the lamp
        rect(x + 3, y + 2, FW - 11, 6, 0xFF282C34);
        rect(x + FW - 7, y + 3, 3, 3, 0xFF141414);
        rect(x + 3, y + FH + 1, FW - 4, 7, DARK);
        rect(x + 4, y + 10, FW - 8, FH - 15, DARK);
        if (locked) {
            rect(x + FW - 6, y + 4, 1, 1, 0xFF3C3C3C);
            padlock(x + FW / 2, y + FH / 2 + 2);
            text(DASH + DASH + DASH, 3, x + 5, y + FH + 2, 0xFF787E88);
            return;
        }
        rect(x + FW - 6, y + 4, 2, 2, col);
        // the side slot
        rect(x + FW, y + 10, 6, FH - 15, SLOT);
        rect(x + FW, y + 10, 6, 1, SLOT_TOP);
        // segments and the level column
        int top = y + 11, bottom = y + FH - 6;
        float seg = (bottom - top) / 10F;
        float filled = level * 10F;
        for (int i = 0; i < 10; i++) {
            int b = Math.round(bottom - i * seg), t = Math.round(bottom - (i + 1) * seg) + 1;
            rect(x + 5, t, 9, b - t, OFF);
            if (st != null && st.amount > 0 && i < filled) {
                float part = Math.min(1F, filled - i);
                int h = Math.max(1, Math.round((b - t) * part));
                fluid(mc, st.getFluid(), st, x + 5, b - h, 9, h);
            } else if (st == null || st.amount <= 0) {
                if (shown != null) {
                    stripes(shown, x + 5, t, 9, b - t);
                }
            }
            rect(x + 15, t, 3, b - t, st != null && st.amount > 0 && i < filled ? LEVEL_COLS[i] : OFF);
        }
        // ticks on the frame
        for (int i = 0; i <= 10; i++) {
            int ty = Math.round(bottom - (bottom - top) * i / 10F);
            rect(x + FW - 3, ty, 2, 1, i % 5 == 0 ? 0xFFD9A834 : 0xFF282C34);
        }
        // the nameplate: the fluid's texture (or the pinned one's)
        if (shown != null) {
            fluid(mc, shown, st != null && st.amount > 0 ? st : new FluidStack(shown, 1000), x + 4, y + 3, FW - 13, 4);
        }
        // the chevron
        if (st != null && st.amount > 0 || pinned != null) {
            int py = Math.round(bottom - (bottom - top) * level);
            for (int k = 0; k <= 3; k += 3) {
                rect(x + FW + k, py, 1, 1, col);
                rect(x + FW + k + 1, py - 1, 1, 1, col);
                rect(x + FW + k + 2, py - 2, 1, 1, col);
                rect(x + FW + k + 1, py + 1, 1, 1, col);
                rect(x + FW + k + 2, py + 2, 1, 1, col);
            }
        }
        // the percentage
        int pct = Math.round(level * 100);
        String digits = String.valueOf(pct);
        int w = digits.length() * 4 + 3;
        int dx = x + FW / 2 - w / 2;
        for (int i = 0; i < digits.length(); i++) {
            text(DIGITS[digits.charAt(i) - '0'], 1, dx + i * 4, y + FH + 2, col);
        }
        text(PERCENT, 1, dx + digits.length() * 4, y + FH + 2, col);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /**
     * The same gauge squeezed into a machine's tank well (11 px wide): the bolted steel frame, the
     * status lamp on top, fluid segments beside the level colour column and gold / dark ticks on the
     * frame's right edge; the percentage moves to the tooltip. `unused`: a tank this machine never
     * fills - hatched, lamp off. Leaves the blocks atlas bound when it drew a fluid.
     */
    public static void drawCompact(Minecraft mc, int x, int y, int w, int h, FluidStack st, int capacity, boolean unused) {
        boolean has = st != null && st.amount > 0 && st.getFluid() != null;
        float level = !has || capacity <= 0 ? 0F : Math.min(1F, (float) st.amount / capacity);
        int col = status(st, capacity, null);
        GuiEnergyGaugeSC.gradient(x, y, w, h, 0xFFAAB0BA, 0xFF747A84);
        rect(x + 1, y + 1, w - 2, 1, 0xFFD0D6E0);
        rect(x + 1, y + 1, 1, h - 2, 0xFFC4CAD4);
        rect(x + 1, y + h - 2, w - 2, 1, 0xFF565A62);
        rect(x + w - 2, y + 1, 1, h - 2, 0xFF5C6068);
        rect(x, y, w, 1, 0xFF1E2024);
        rect(x, y + h - 1, w, 1, 0xFF1E2024);
        rect(x, y, 1, h, 0xFF1E2024);
        rect(x + w - 1, y, 1, h, 0xFF1E2024);
        // the lamp, with a little glow on the steel round it
        int lx = x + (w - 4) / 2, ly = y + 2;
        if (!unused) {
            rect(lx - 1, ly - 1, 6, 6, (col & 0xFFFFFF) | 0x48000000);
        }
        rect(lx, ly, 4, 4, 0xFF121214);
        rect(lx + 1, ly + 1, 2, 2, unused ? 0xFF3C3C3C : col);
        if (!unused) {
            rect(lx + 1, ly + 1, 1, 1, 0xFFFFFFFF & (col | 0xFF808080));
        }
        // the well: recessed, dark
        int sx = x + 2, sy = y + 8, sw = w - 4, sh = h - 10;
        rect(sx, sy, sw, sh, DARK);
        rect(sx, sy, sw, 1, 0xFF3A3E46);
        rect(sx, sy, 1, sh, 0xFF3A3E46);
        rect(sx, sy + sh - 1, sw, 1, 0xFFDCE2EA);
        rect(sx + sw - 1, sy, 1, sh, 0xFFC8CED8);
        int left = sx + 1, fw = sw - 4, colX = sx + sw - 2;
        int top = sy + 1, bottom = sy + sh - 1;
        if (unused) {
            for (int j = top; j < bottom; j++) {
                for (int i = left; i < sx + sw - 1; i++) {
                    if ((i + j) % 3 == 0) {
                        rect(i, j, 1, 1, 0xFF30343C);
                    }
                }
            }
            GL11.glColor4f(1F, 1F, 1F, 1F);
            return;
        }
        int n = Math.max(3, (bottom - top + 1) / 4);
        float seg = (bottom - top) / (float) n;
        float filled = level * n;
        for (int i = 0; i < n; i++) {
            int b = Math.round(bottom - i * seg), t = Math.round(bottom - (i + 1) * seg) + 1;
            boolean on = has && i < filled;
            rect(left, t, fw, b - t, OFF);
            if (on) {
                float part = Math.min(1F, filled - i);
                int hh = Math.max(1, Math.round((b - t) * part));
                fluid(mc, st.getFluid(), st, left, b - hh, fw, hh);
            }
            int li = Math.min(LEVEL_COLS.length - 1, i * LEVEL_COLS.length / n);
            rect(colX, t, 1, b - t, on ? LEVEL_COLS[li] : OFF);
        }
        // ticks on the frame's right edge: gold at 0 / 50 / 100 %
        for (int i = 0; i <= 10; i++) {
            int ty = Math.round(bottom - 1 - (bottom - 1 - top) * i / 10F);
            rect(x + w - 2, ty, 1, 1, i % 5 == 0 ? 0xFFF0C450 : 0xFF3C4048);
        }
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** "73%" in the lamp's colour, for the tooltips of the compact gauges. */
    public static String percentLine(FluidStack st, int capacity) {
        int pct = st == null || capacity <= 0 ? 0 : Math.min(100, Math.round(st.amount * 100F / capacity));
        int col = status(st, capacity, null);
        String code = col == GREEN ? "§a" : col == ORANGE ? "§6" : "§c";
        return code + pct + "%";
    }

    private static void rect(int x, int y, int w, int h, int c) {
        if (w > 0 && h > 0) {
            Gui.drawRect(x, y, x + w, y + h, c);
        }
    }

    /** 3x5 pixel glyphs; `glyphs` of them one after another (each 15 characters of 0 / 1). */
    private static void text(String bits, int glyphs, int x, int y, int c) {
        for (int g = 0; g < glyphs; g++) {
            for (int j = 0; j < 5; j++) {
                for (int i = 0; i < 3; i++) {
                    if (bits.charAt(g * 15 + j * 3 + i) == '1') {
                        rect(x + g * 4 + i, y + j, 1, 1, c);
                    }
                }
            }
        }
    }

    private static void padlock(int cx, int cy) {
        rect(cx - 2, cy - 5, 5, 1, 0xFFB8C0CB);
        rect(cx - 2, cy - 5, 1, 4, 0xFFB8C0CB);
        rect(cx + 2, cy - 5, 1, 4, 0xFFB8C0CB);
        rect(cx - 3, cy - 1, 7, 5, 0xFFD9A834);
        rect(cx, cy + 1, 1, 2, 0xFF3A2A08);
    }

    /** An empty compartment pinned to a fluid: its colour in faint diagonal stripes. */
    private static void stripes(Fluid f, int x, int y, int w, int h) {
        int c = 0x70000000 | (colourOf(f) & 0xFFFFFF);
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                if ((x + i + y + j) % 4 == 0) {
                    rect(x + i, y + j, 1, 1, c);
                }
            }
        }
    }

    /** A flat colour for a fluid: its tint, or a stand-in for water / lava (drawn from grey textures). */
    public static int colourOf(Fluid f) {
        if (f == FluidRegistry.WATER) {
            return 0xFF3F76E4;
        }
        if (f == FluidRegistry.LAVA) {
            return 0xFFFF6010;
        }
        int c = f.getColor();
        return (c & 0xFFFFFF) == 0xFFFFFF ? 0xFF9098A8 : 0xFF000000 | c;
    }

    /** The fluid's own texture tiled into the box (clipped, not stretched), tinted like the machines' tanks. */
    private static void fluid(Minecraft mc, Fluid f, FluidStack st, int x, int y, int w, int h) {
        IIcon icon = f.getIcon(st);
        if (icon == null) {
            rect(x, y, w, h, colourOf(f));
            return;
        }
        mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
        int tint = f == FluidRegistry.WATER ? 0x3F76E4
                : com.sc.init.ModFluids.OWNED.contains(f) ? 0xFFFFFF : f.getColor(st);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(((tint >> 16) & 255) / 255F, ((tint >> 8) & 255) / 255F, (tint & 255) / 255F, 1F);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        for (int yy = 0; yy < h; yy += 16) {
            for (int xx = 0; xx < w; xx += 16) {
                int tw = Math.min(16, w - xx), th = Math.min(16, h - yy);
                double u0 = icon.getMinU(), u1 = icon.getInterpolatedU(tw), v0 = icon.getMinV(), v1 = icon.getInterpolatedV(th);
                t.addVertexWithUV(x + xx, y + yy + th, 0, u0, v1);
                t.addVertexWithUV(x + xx + tw, y + yy + th, 0, u1, v1);
                t.addVertexWithUV(x + xx + tw, y + yy, 0, u1, v0);
                t.addVertexWithUV(x + xx, y + yy, 0, u0, v0);
            }
        }
        t.draw();
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }
}
