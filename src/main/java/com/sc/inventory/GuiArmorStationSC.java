package com.sc.inventory;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityArmorStationSC;
import com.sc.util.ArmorGasSC.Gas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;

/**
 * The Armour Service Station's screen (GuiBigSC's layout): the four armour slots down the left of
 * the holo screen beside a suit outline, a row per gas (a check box - fill it or not, its name, a
 * bar of what the pieces in the slots and the suits on top hold, the numbers), "filled / still to
 * go", the status; under the screen the "Fill gases" and "Helium only" switches; the power switch
 * and the energy gauge on the right.
 */
public class GuiArmorStationSC extends GuiContainer {

    /** The gas rows start right of the slots and the suit outline (x 35..55). */
    private static final int ROW_X = 58, ROW_Y = 25, ROW_STEP = 9, NAME_X = ROW_X + 10, NAME_W = 40, BAR_X = 111, BAR_W = 45,
            NUM_X = 160, NUM_W = GuiBigSC.SCREEN_RIGHT - 160, SUM_Y = ROW_Y + 7 * ROW_STEP + 2, STATUS_Y = SUM_Y + 11,
            BTN_Y = 122, BTN_H = 14;

    private final TileEntityArmorStationSC te;
    private GuiPowerSC power;

    public GuiArmorStationSC(InventoryPlayer inv, TileEntityArmorStationSC te) {
        super(new ContainerArmorStationSC(inv, te));
        this.te = te;
        xSize = GuiBigSC.W;
        ySize = GuiBigSC.H;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        super.initGui();
        buttonList.clear();
        power = new GuiPowerSC(te, ContainerArmorStationSC.BTN_POWER, ContainerArmorStationSC.BTN_REDSTONE);
        power.addButtons(buttonList, guiLeft, guiTop);
        for (Gas g : Gas.values()) {
            buttonList.add(new CheckButton(ContainerArmorStationSC.BTN_GAS + g.ordinal(), guiLeft + ROW_X, guiTop + rowY(g), g));
        }
        buttonList.add(new HoloButton(ContainerArmorStationSC.BTN_FILL, guiLeft + 8, guiTop + BTN_Y, 100, BTN_H));
        buttonList.add(new HoloButton(ContainerArmorStationSC.BTN_HELIUM, guiLeft + 112, guiTop + BTN_Y, 96, BTN_H));
    }

    private static int rowY(Gas g) {
        return ROW_Y + g.ordinal() * ROW_STEP;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (power.allowClick(button)) {
            mc.playerController.sendEnchantPacket(inventorySlots.windowId, button.id);
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int x = guiLeft, y = guiTop;
        GuiBigSC.window(x, y, false, 0);
        GuiHoloSC.screen(x + GuiBigSC.SCREEN_X, y + GuiBigSC.SCREEN_Y, GuiBigSC.SCREEN_W, GuiBigSC.SCREEN_H);
        suitOutline(x + ContainerArmorStationSC.PIECE_X + 19, y + ContainerArmorStationSC.PIECE_Y);
        for (int i = 0; i < TileEntityArmorStationSC.SLOTS; i++) {
            GuiHoloSC.slot(x + ContainerArmorStationSC.PIECE_X, y + ContainerArmorStationSC.PIECE_Y + i * ContainerArmorStationSC.PIECE_STEP,
                    te.getStackInSlot(i) != null && te.isActive());
        }
        for (Gas g : Gas.values()) {
            int cap = te.shownCapacity(g);
            float frac = cap <= 0 ? 0F : (float) te.shownAmount(g) / cap;
            int col = 0xFF000000 | g.color;
            if (cap <= 0 || !te.gasEnabled(g) || !te.isFillGases()) {
                col = 0xFF3A4654;                              // no tank here / not filled: a dim bar
            }
            GuiHoloSC.bar(x + BAR_X, y + rowY(g) + 2, BAR_W, 4, frac, 13, col);
        }
        GuiEnergyGaugeSC.draw(x + GuiBigSC.GAUGE_X, y + GuiPowerSC.GAUGE_Y, GuiBigSC.GAUGE_W, GuiPowerSC.GAUGE_H,
                (float) te.getEnergyStored() / Math.max(1, te.getMaxEnergyStored()));
        GuiHoloSC.glint(x + GuiBigSC.SCREEN_X, y + GuiBigSC.SCREEN_Y, GuiBigSC.SCREEN_W, GuiBigSC.SCREEN_H);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** A thin suit outline beside the slots: helmet, torso with the gas loop, legs, boots - lit while it works. */
    private void suitOutline(int x, int y) {
        int c = te.isActive() ? GuiHoloSC.CYAN : GuiHoloSC.CYAN_DIM;
        rect(x + 6, y + 3, 8, 9, c);                              // helmet
        rect(x + 8, y + 6, 4, 3, 0xFF0A1218);                     // visor
        rect(x + 4, y + 22, 12, 17, c);                           // torso
        rect(x + 6, y + 25, 8, 2, 0xFF0A1218);                    // the helium loop
        rect(x + 6, y + 31, 8, 2, 0xFF0A1218);
        rect(x + 1, y + 23, 2, 13, c);                            // arms
        rect(x + 17, y + 23, 2, 13, c);
        rect(x + 5, y + 46, 4, 16, c);                            // legs
        rect(x + 11, y + 46, 4, 16, c);
        rect(x + 4, y + 70, 5, 4, c);                             // boots
        rect(x + 11, y + 70, 5, 4, c);
    }

    private static void rect(int x, int y, int w, int h, int c) {
        drawRect(x, y, x + w, y + h, c);
    }

    private void fit(String text, int x, int y, int maxW, int color) {
        TextFitSC.draw(fontRendererObj, text, x, y, maxW, color, guiLeft, guiTop);
    }

    static String gasName(Gas g) {
        return Lang.tr("sc.armorStation.gas." + g.key());
    }

    private int statusColor(int st) {
        switch (st) {
            case TileEntityArmorStationSC.ST_WORKING: return GuiHoloSC.OK;
            case TileEntityArmorStationSC.ST_FULL:
            case TileEntityArmorStationSC.ST_IDLE:
            case TileEntityArmorStationSC.ST_OFF: return GuiHoloSC.IDLE;
            default: return GuiHoloSC.WARN;
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fit(Lang.tr("tile.siliconage.armorStation.name"), 8, 5, GuiBigSC.titleRoom(fontRendererObj, te.getTier()), GuiGaugeSC.TITLE_COLOR);
        GuiGaugeSC.drawTierBadge(fontRendererObj, te.getTier(), GuiBigSC.W - 6, 3);
        fontRendererObj.drawString(Lang.tr("container.inventory"), GuiBigSC.INV_X, GuiBigSC.INV_Y - 10, 0x404040);
        long have = 0, room = 0;
        for (Gas g : Gas.values()) {
            int a = te.shownAmount(g), cap = te.shownCapacity(g);
            boolean on = te.isFillGases() && te.gasEnabled(g);
            int nameCol = cap <= 0 ? 0x4A5A6A : on ? GuiHoloSC.VALUE : GuiHoloSC.IDLE;
            fit(gasName(g), NAME_X, rowY(g), NAME_W, nameCol);
            fit(cap <= 0 ? "-" : a + "/" + cap, NUM_X, rowY(g), NUM_W, cap <= 0 ? 0x4A5A6A : GuiHoloSC.LABEL);
            if (on) {
                have += a;
                room += Math.max(0, cap - a);
            }
        }
        fit(Lang.tr("sc.armorStation.sum", String.valueOf(have), String.valueOf(room)), ROW_X, SUM_Y, GuiBigSC.SCREEN_RIGHT - ROW_X,
                room > 0 ? GuiHoloSC.LABEL : GuiHoloSC.OK);
        int st = te.getStatus();
        String status = Lang.tr("sc.armorStation.status." + st);
        if (te.getPlayers() > 0) {
            status += " | " + Lang.tr("sc.armorStation.onpad", te.getPlayers());
        }
        fit(status, ROW_X, STATUS_Y, GuiBigSC.SCREEN_RIGHT - ROW_X, statusColor(st));
        power.drawGaugeOff(fontRendererObj);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        TextFitSC.beginFrame();
        super.drawScreen(mouseX, mouseY, partialTicks);
        int mx = mouseX - guiLeft, my = mouseY - guiTop;
        List<String> tip = power.tooltip(mx, my);
        if (tip == null && GuiGaugeSC.isOver(GuiBigSC.GAUGE_X, GuiPowerSC.GAUGE_Y, GuiBigSC.GAUGE_W, GuiPowerSC.GAUGE_H, mx, my)) {
            tip = new ArrayList<String>();
            tip.add(Lang.tr("sc.gui.energy"));
            tip.add(te.getEnergyStored() + " / " + te.getMaxEnergyStored() + " EU");
        }
        if (tip == null) {
            for (Gas g : Gas.values()) {
                if (GuiGaugeSC.isOver(ROW_X, rowY(g), GuiBigSC.SCREEN_RIGHT - ROW_X, ROW_STEP, mx, my)) {
                    tip = new ArrayList<String>();
                    tip.add(gasName(g));
                    tip.add(te.shownAmount(g) + " / " + te.shownCapacity(g) + " mB");
                    tip.add(Lang.tr("sc.armorStation.gasuse." + g.key()));
                    tip.add(Lang.tr(te.gasEnabled(g) ? "sc.armorStation.gas.on" : "sc.armorStation.gas.off"));
                    break;
                }
            }
        }
        if (tip == null && GuiGaugeSC.isOver(8, BTN_Y, 100, BTN_H, mx, my)) {
            tip = new ArrayList<String>();
            tip.add(Lang.tr("sc.armorStation.fill.hint"));
        }
        if (tip == null && GuiGaugeSC.isOver(112, BTN_Y, 96, BTN_H, mx, my)) {
            tip = new ArrayList<String>();
            tip.add(Lang.tr("sc.armorStation.helium.hint"));
        }
        if (tip == null) {
            tip = TextFitSC.hoverAt(mouseX, mouseY);
        }
        if (tip != null) {
            drawHoveringText(GuiGaugeSC.wrapTooltip(fontRendererObj, tip, width), mouseX, mouseY, fontRendererObj);
        }
    }

    /** A gas's check box on the screen. */
    private class CheckButton extends GuiButton {
        private final Gas gas;

        CheckButton(int id, int x, int y, Gas gas) {
            super(id, x, y, 7, 7, "");
            this.gas = gas;
        }

        @Override
        public void drawButton(Minecraft mc, int mx, int my) {
            if (!visible) {
                return;
            }
            boolean over = mx >= xPosition && my >= yPosition && mx < xPosition + width && my < yPosition + height;
            rect(xPosition, yPosition, 7, 7, over ? GuiHoloSC.CYAN : GuiHoloSC.CYAN_MID);
            rect(xPosition + 1, yPosition + 1, 5, 5, 0xFF0A1218);
            if (te.gasEnabled(gas)) {
                rect(xPosition + 2, yPosition + 2, 3, 3, te.isFillGases() ? 0xFF000000 | gas.color : 0xFF4A5A6A);
            }
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
    }

    /** A flat holo switch: its label says what it is and whether it's on. */
    private class HoloButton extends GuiButton {
        HoloButton(int id, int x, int y, int w, int h) {
            super(id, x, y, w, h, "");
        }

        private boolean lit() {
            return id == ContainerArmorStationSC.BTN_FILL ? te.isFillGases() : te.getGasMask() == 1 << Gas.HELIUM.ordinal();
        }

        private String label() {
            if (id == ContainerArmorStationSC.BTN_FILL) {
                return Lang.tr("sc.armorStation.fill") + ": " + Lang.tr(te.isFillGases() ? "sc.armorStation.on" : "sc.armorStation.off");
            }
            return Lang.tr("sc.armorStation.helium");
        }

        @Override
        public void drawButton(Minecraft mc, int mx, int my) {
            if (!visible) {
                return;
            }
            boolean over = mx >= xPosition && my >= yPosition && mx < xPosition + width && my < yPosition + height;
            boolean lit = lit();
            rect(xPosition, yPosition, width, height, over ? GuiHoloSC.CYAN : lit ? GuiHoloSC.CYAN_MID : 0xFF343C48);
            rect(xPosition + 1, yPosition + 1, width - 2, height - 2, lit ? 0xFF0E2A36 : 0xFF141C26);
            TextFitSC.drawCentered(mc.fontRenderer, label(), xPosition + 2, yPosition + (height - 8) / 2, width - 4,
                    lit ? GuiHoloSC.VALUE : GuiHoloSC.IDLE, false, 0, 0);
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
    }
}
