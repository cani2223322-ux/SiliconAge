package com.sc.inventory;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;

/**
 * Text that always fits its place, for every screen of the mod: a string wider than its room is
 * first drawn smaller (down to 75%), and if even that doesn't fit, cut with "..." - the full text
 * then shows as a tooltip when the mouse is over it (drawHover, called after the screen). Also
 * the buttons that do the same (Button), icon tabs (Tab) and the "?" help mark (help).
 * Hover areas are kept per frame in screen coordinates: beginFrame() at the start of a screen's
 * drawScreen, drawHover() at its end.
 */
@SideOnly(Side.CLIENT)
public final class TextFitSC {

    public static final float MIN_SCALE = 0.75F;
    private static final List<int[]> AREAS = new ArrayList<int[]>();
    private static final List<List<String>> TEXTS = new ArrayList<List<String>>();
    private static final RenderItem ITEMS = new RenderItem();

    private TextFitSC() {
    }

    public static void beginFrame() {
        AREAS.clear();
        TEXTS.clear();
    }

    /** A hover area (screen coordinates) with its tooltip lines. */
    public static void hover(int x, int y, int w, int h, List<String> lines) {
        AREAS.add(new int[]{x, y, w, h});
        TEXTS.add(lines);
    }

    public static void hover(int x, int y, int w, int h, String text) {
        List<String> l = new ArrayList<String>();
        l.add(text);
        hover(x, y, w, h, l);
    }

    /** The tooltip under the mouse, or null. */
    public static List<String> hoverAt(int mouseX, int mouseY) {
        for (int i = AREAS.size() - 1; i >= 0; i--) {
            int[] a = AREAS.get(i);
            if (mouseX >= a[0] && mouseX < a[0] + a[2] && mouseY >= a[1] && mouseY < a[1] + a[3]) {
                return TEXTS.get(i);
            }
        }
        return null;
    }

    /**
     * Draws `text` at (x, y) in at most `maxW` pixels. (ox, oy) is where (0, 0) of the current
     * drawing is on the screen (guiLeft / guiTop inside a GuiContainer's foreground layer, 0 / 0
     * for screen coordinates) - for the hover area of a cut string. @return the width drawn
     */
    public static int draw(FontRenderer font, String text, int x, int y, int maxW, int color, boolean shadow, int ox, int oy) {
        int w = font.getStringWidth(text);
        if (w <= maxW || maxW <= 0) {
            font.drawString(text, x, y, color, shadow);
            return w;
        }
        float scale = Math.max(MIN_SCALE, (float) maxW / w);
        String shown = text;
        boolean cut = false;
        if (w * scale > maxW) {
            int room = (int) (maxW / scale) - font.getStringWidth("...");
            shown = font.trimStringToWidth(text, Math.max(0, room)) + "...";
            cut = true;
        }
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y + (1 - scale) * font.FONT_HEIGHT / 2F, 0);
        GL11.glScalef(scale, scale, 1F);
        font.drawString(shown, 0, 0, color, shadow);
        GL11.glPopMatrix();
        if (cut) {
            hover(x + ox, y + oy, maxW, font.FONT_HEIGHT, text);
        }
        return Math.min(maxW, (int) (font.getStringWidth(shown) * scale));
    }

    public static int draw(FontRenderer font, String text, int x, int y, int maxW, int color, int ox, int oy) {
        return draw(font, text, x, y, maxW, color, false, ox, oy);
    }

    /** Centred in [x, x + w). */
    public static void drawCentered(FontRenderer font, String text, int x, int y, int w, int color, boolean shadow, int ox, int oy) {
        int tw = font.getStringWidth(text);
        if (tw <= w) {
            font.drawString(text, x + (w - tw) / 2, y, color, shadow);
            return;
        }
        float scale = Math.max(MIN_SCALE, (float) w / tw);
        int shownW = (int) Math.min(w, tw * scale);
        draw(font, text, x + (w - shownW) / 2, y, w, color, shadow, ox, oy);
    }

    /** The "?" mark: a small round badge; its text shows (wrapped) when the mouse is over it. */
    public static void help(FontRenderer font, int x, int y, String text, int ox, int oy) {
        net.minecraft.client.gui.Gui.drawRect(x, y + 1, x + 9, y + 8, 0xFF2A62A8);
        net.minecraft.client.gui.Gui.drawRect(x + 1, y, x + 8, y + 9, 0xFF2A62A8);
        font.drawString("?", x + 2, y + 1, 0xFFFFFF);
        hover(x + ox, y + oy, 9, 9, text);
    }

    /** A vanilla button whose caption fits: smaller, or cut with its full text as a tooltip. */
    public static class Button extends GuiButton {

        public Button(int id, int x, int y, int w, int h, String text) {
            super(id, x, y, w, h, text);
        }

        @Override
        public void drawButton(Minecraft mc, int mx, int my) {
            if (!visible) {
                return;
            }
            String text = displayString;
            displayString = "";
            super.drawButton(mc, mx, my);
            displayString = text;
            int color = !enabled ? 0xA0A0A0 : field_146123_n ? 0xFFFFA0 : 0xE0E0E0;
            drawCentered(mc.fontRenderer, text, xPosition + 3, yPosition + (height - 8) / 2, width - 6, color, true, 0, 0);
        }
    }

    /**
     * A tab: an item icon and, when there's room, its short name (smaller if need be); the full
     * name is its tooltip whenever it isn't all written out.
     */
    public static class Tab extends GuiButton {

        private final ItemStack icon;
        private final String full;

        public Tab(int id, int x, int y, int w, int h, ItemStack icon, String shortName, String fullName) {
            super(id, x, y, w, h, shortName);
            this.icon = icon;
            this.full = fullName;
        }

        @Override
        public void drawButton(Minecraft mc, int mx, int my) {
            if (!visible) {
                return;
            }
            String text = displayString;
            displayString = "";
            super.drawButton(mc, mx, my);
            displayString = text;
            FontRenderer font = mc.fontRenderer;
            int room = width - 4 - (icon != null ? 17 : 0);
            int tw = font.getStringWidth(text);
            boolean withText = !text.isEmpty() && tw * TextFitSC.MIN_SCALE <= room;
            int contentW = (icon != null ? 16 : 0) + (withText ? (icon != null ? 1 : 0) + (int) Math.min(room, tw) : 0);
            int x = xPosition + (width - contentW) / 2;
            if (icon != null) {
                RenderHelper.enableGUIStandardItemLighting();
                GL11.glEnable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
                ITEMS.renderItemAndEffectIntoGUI(font, mc.getTextureManager(), icon, x, yPosition + (height - 16) / 2);
                RenderHelper.disableStandardItemLighting();
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glColor4f(1F, 1F, 1F, 1F);
                x += 17;
            }
            if (withText) {
                int color = !enabled ? 0xFFFFA0 : field_146123_n ? 0xFFFFA0 : 0xE0E0E0;
                draw(font, text, x, yPosition + (height - 8) / 2, room, color, true, 0, 0);
            }
            if (!withText || tw > room || !text.equals(full)) {
                hover(xPosition, yPosition, width, height, full);
            }
        }
    }
}
