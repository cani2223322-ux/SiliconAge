package com.sc.inventory;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import com.sc.energy.TileEntityEnergyBase;
import com.sc.item.BatteryFeedSC;
import com.sc.manual.Lang;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.item.ItemStack;

/**
 * The battery slot under a screen's energy gauge, drawn: three arrows between it and the gauge
 * (green while energy moves - up from the battery on a consumer, down into it on a generator),
 * the slot in a cyan frame (a battery's outline when empty), the battery's charge as a strip
 * beside it, and under it the mode button - the gauge keeps its full height. Positions are
 * screen-local: x the gauge column's left, top the gauge's bottom edge; the slot's item sits at
 * (x + 3, top + 4), SlotBatterySC's place.
 */
public final class GuiBatterySlotSC {

    public static final int COLUMN_W = 26;
    private final TileEntityEnergyBase te;
    private final int modeId, x, top;
    /** A generator's slot: the output charges the battery (arrows down, "surplus / always"). */
    private final boolean charging;

    public GuiBatterySlotSC(TileEntityEnergyBase te, int modeId, int x, int top) {
        this(te, modeId, x, top, false);
    }

    public GuiBatterySlotSC(TileEntityEnergyBase te, int modeId, int x, int top, boolean charging) {
        this.te = te;
        this.modeId = modeId;
        this.x = x;
        this.top = top;
        this.charging = charging;
    }

    /** Where the slot's item goes (for the container). */
    public static int itemX(int columnX) {
        return SlotBatterySC.itemX(columnX);
    }

    public static int itemY(int gaugeBottom) {
        return SlotBatterySC.itemY(gaugeBottom);
    }

    /** A generator's texts live under sc.gui.battery.gen.*. */
    private String key(String k) {
        return charging ? k.replace("sc.gui.battery.", "sc.gui.battery.gen.") : k;
    }

    @SuppressWarnings("unchecked")
    public void addButton(List buttons, int guiLeft, int guiTop) {
        buttons.add(new ModeButton(guiLeft + x, guiTop + top + 22));
    }

    /** Background: arrows, the slot's frame, the charge strip. */
    public void draw(int guiLeft, int guiTop, ItemStack battery) {
        int gx = guiLeft + x, gy = guiTop + top;
        boolean moving = charging ? te.batteryCharges(battery) : te.batteryFeeds(battery);
        for (int k = 0; k < 3; k++) {
            int c = moving ? 0xFF5AE66E : 0xFF3A4450;
            int ax = gx + 6 + k * 6;
            if (charging) {                                      // down: the gauge into the battery
                rect(ax, gy + 1, 3, 1, c);
                rect(ax + 1, gy + 2, 1, 1, c);
            } else {                                             // up: the battery into the gauge
                rect(ax, gy + 2, 3, 1, c);
                rect(ax + 1, gy + 1, 1, 1, c);
            }
        }
        int sx = gx + 2, sy = gy + 3;
        rect(sx, sy, 18, 18, battery != null ? GuiHoloSC.CYAN : GuiHoloSC.CYAN_MID);
        rect(sx + 1, sy + 1, 16, 16, 0xFF0E1A26);
        if (battery == null) {                                   // a battery's outline: put one here
            int c = 0xFF1E3444;
            rect(sx + 7, sy + 3, 4, 2, c);
            rect(sx + 5, sy + 5, 8, 10, c);
            rect(sx + 6, sy + 6, 6, 8, 0xFF0E1A26);
        }
        rect(gx + 21, sy, 4, 18, 0xFF04080C);
        long cap = BatteryFeedSC.capacityOf(battery);
        if (battery != null && cap > 0) {
            float f = (float) BatteryFeedSC.chargeOf(battery) / cap;
            int n = Math.round(16 * f);
            int c = f > 0.5F ? 0xFF5AE66E : f > 0.2F ? 0xFFF0C040 : 0xFFE63C3C;
            rect(gx + 22, sy + 1 + 16 - n, 2, n, c);
        }
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** Tooltip over the mode button or the empty slot (screen-local mouse), else null. */
    public List<String> tooltip(int mx, int my, ItemStack battery) {
        List<String> lines = new ArrayList<String>();
        if (GuiGaugeSC.isOver(x, top + 22, COLUMN_W, 9, mx, my)) {
            int mode = te.getBatteryMode();
            lines.add(Lang.tr(key("sc.gui.battery.mode"), Lang.tr(key("sc.gui.battery.mode." + mode))));
            lines.add("§7" + Lang.tr(key("sc.gui.battery.mode." + mode + ".desc")));
            if (battery != null) {
                int rate = Math.min(BatteryFeedSC.rateOf(battery), charging ? te.outputTier().getVoltage() : te.inputTier().getVoltage());
                lines.add(Lang.tr(key("sc.gui.battery.rate"), rate));
                long left = charging ? BatteryFeedSC.capacityOf(battery) - BatteryFeedSC.chargeOf(battery) : BatteryFeedSC.chargeOf(battery);
                long ticks = rate <= 0 ? 0 : left / rate;
                long min = ticks / 20 / 60;
                lines.add("§a" + Lang.tr(key("sc.gui.battery.lasts"), min / 60, min % 60));
            }
            return lines;
        }
        if (battery == null && GuiGaugeSC.isOver(x + 2, top + 3, 18, 18, mx, my)) {
            lines.add(Lang.tr("sc.gui.battery.slot"));
            lines.add("§7" + Lang.tr(key("sc.gui.battery.slot.desc")));
            return lines;
        }
        return null;
    }

    private static void rect(int x, int y, int w, int h, int c) {
        Gui.drawRect(x, y, x + w, y + h, c);
    }

    /** The mode button: a lamp (amber reserve / blue surplus, green always) and the mode's short name. */
    private class ModeButton extends GuiButton {
        ModeButton(int bx, int by) {
            super(modeId, bx, by, COLUMN_W, 9, "");
        }

        @Override
        public void drawButton(Minecraft mc, int mx, int my) {
            if (!visible) {
                return;
            }
            boolean over = mx >= xPosition && my >= yPosition && mx < xPosition + width && my < yPosition + height;
            rect(xPosition, yPosition, width, height, over && enabled ? GuiHoloSC.CYAN : 0xFF2A6A8A);
            rect(xPosition + 1, yPosition + 1, width - 2, height - 2, 0xFF0E3A50);
            int mode = te.getBatteryMode();
            rect(xPosition + 2, yPosition + 3, 3, 3, mode == BatteryFeedSC.MODE_ALWAYS ? 0xFF5AE66E : charging ? 0xFF6EB4FF : 0xFFF0C040);
            String t = Lang.tr(key("sc.gui.battery.mode.short." + mode));
            float k = Math.min(0.5F, 18F / Math.max(1, mc.fontRenderer.getStringWidth(t)));
            GL11.glPushMatrix();
            GL11.glTranslatef(xPosition + 7, yPosition + (height - 8 * k) / 2F, 0F);
            GL11.glScalef(k, k, 1F);
            mc.fontRenderer.drawString(t, 0, 0, enabled ? 0xE6F0FA : 0x6A8098);
            GL11.glPopMatrix();
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
    }
}
