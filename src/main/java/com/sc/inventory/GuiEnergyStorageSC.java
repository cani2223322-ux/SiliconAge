package com.sc.inventory;

import java.util.ArrayList;
import java.util.List;

import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityEnergyStorageSC;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;

/**
 * Energy storage (and charge pad) screen, the large holo-screen layout (GuiBigSC, 248 x 232):
 * the charging slot, stored / capacity, the net flow per tick (averaged over a second: +
 * charging, - discharging) and the output voltage on the screen, a wide segmented charge bar
 * along its bottom in the charge colour, the tall energy gauge right of it, the player's
 * inventory below.
 */
public class GuiEnergyStorageSC extends GuiContainer {

    private static final int TEXT_X = 42, BAR_X = 14, BAR_Y = 98, BAR_W = 186, BAR_H = 9;

    private final TileEntityEnergyStorageSC storage;

    public GuiEnergyStorageSC(InventoryPlayer playerInv, TileEntityEnergyStorageSC storage) {
        super(new ContainerEnergyStorageSC(playerInv, storage));
        this.storage = storage;
        xSize = GuiBigSC.W;
        ySize = GuiBigSC.H;
    }

    private GuiPowerSC power;

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();
        power = new GuiPowerSC(storage, ContainerEnergyStorageSC.BTN_POWER, ContainerEnergyStorageSC.BTN_REDSTONE);
        power.addButtons(buttonList, guiLeft, guiTop);
    }

    @Override
    protected void actionPerformed(net.minecraft.client.gui.GuiButton button) {
        if (power.allowClick(button)) {
            mc.playerController.sendEnchantPacket(inventorySlots.windowId, button.id);
        }
    }

    private float fraction() {
        return (float) storage.getEnergyStored() / Math.max(1, storage.getMaxEnergyStored());
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int x = guiLeft, y = guiTop;
        GuiBigSC.window(x, y, false, 0);
        GuiHoloSC.screen(x + GuiBigSC.SCREEN_X, y + GuiBigSC.SCREEN_Y, GuiBigSC.SCREEN_W, GuiBigSC.SCREEN_H);
        GuiHoloSC.slot(x + ContainerEnergyStorageSC.SLOT_X, y + ContainerEnergyStorageSC.SLOT_Y, storage.getStackInSlot(0) != null);
        GuiHoloSC.bar(x + BAR_X, y + BAR_Y, BAR_W, BAR_H, fraction(), 24, GuiEnergyGaugeSC.colour(fraction()));
        GuiEnergyGaugeSC.draw(x + GuiBigSC.GAUGE_X, y + GuiPowerSC.GAUGE_Y, GuiBigSC.GAUGE_W, GuiPowerSC.GAUGE_H, fraction());
        GuiHoloSC.glint(x + GuiBigSC.SCREEN_X, y + GuiBigSC.SCREEN_Y, GuiBigSC.SCREEN_W, GuiBigSC.SCREEN_H);
        org.lwjgl.opengl.GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = Lang.tr("tile.siliconage." + (storage instanceof com.sc.tileentity.TileEntityChargePadSC ? "chargePad." : "energyStorage.")
                + storage.getTier().name().toLowerCase(java.util.Locale.ROOT) + ".name");
        fit(title, 8, 5, GuiBigSC.titleRoom(fontRendererObj, storage.getTier()), GuiGaugeSC.TITLE_COLOR);
        GuiGaugeSC.drawTierBadge(fontRendererObj, storage.getTier(), GuiBigSC.W - 6, 3);
        GuiBigSC.labels(fontRendererObj, null, Lang.tr("container.inventory"));
        int room = GuiBigSC.SCREEN_RIGHT - TEXT_X;
        fit(Lang.tr("sc.gui.holo.storage"), TEXT_X, 25, room, GuiHoloSC.CYAN & 0xFFFFFF);
        fit(Lang.tr("sc.storage.gui.stored", String.valueOf(storage.getEnergyStored())), TEXT_X, 40, room, GuiHoloSC.VALUE);
        fit(Lang.tr("sc.storage.gui.capacity", String.valueOf(storage.getMaxEnergyStored()), Math.round(fraction() * 100)),
                TEXT_X, 51, room, GuiHoloSC.LABEL);
        int flow = storage.getFlowPerTick();
        fit(Lang.tr("sc.storage.gui.flow", (flow > 0 ? "+" : "") + flow), TEXT_X, 66, room,
                flow > 0 ? GuiHoloSC.OK : flow < 0 ? GuiHoloSC.BAD : GuiHoloSC.IDLE);
        fit(Lang.tr("sc.storage.gui.output", storage.getTier().getVoltage()), TEXT_X, 77, room, GuiHoloSC.LABEL);
        String pct = Math.round(fraction() * 100) + "%";
        fontRendererObj.drawString(pct, BAR_X + BAR_W - fontRendererObj.getStringWidth(pct), BAR_Y - 10, GuiEnergyGaugeSC.colour(fraction()) & 0xFFFFFF);
        power.drawGaugeOff(fontRendererObj);
        power.drawWarning(fontRendererObj, TEXT_X, 52, GuiBigSC.SCREEN_RIGHT - TEXT_X, guiLeft, guiTop);
    }

    /** A string that fits its room (smaller, or cut with the full text as a tooltip) - foreground coordinates. */
    private void fit(String text, int x, int y, int maxW, int color) {
        TextFitSC.draw(fontRendererObj, text, x, y, maxW, color, guiLeft, guiTop);
    }

    private List<String> tooltipAt(int mx, int my) {
        List<String> powerTip = power.tooltip(mx, my);
        if (powerTip != null) {
            return powerTip;
        }
        List<String> lines = new ArrayList<String>();
        if (GuiGaugeSC.isOver(TEXT_X, 76, GuiBigSC.SCREEN_RIGHT - TEXT_X, 10, mx, my)) {
            lines.add(Lang.tr("sc.storage.tooltip.io", storage.getTier().getVoltage()));
            return lines;
        }
        if (GuiGaugeSC.isOver(GuiBigSC.GAUGE_X, GuiPowerSC.GAUGE_Y, GuiBigSC.GAUGE_W, GuiPowerSC.GAUGE_H, mx, my)
                || GuiGaugeSC.isOver(BAR_X - 1, BAR_Y - 1, BAR_W + 2, BAR_H + 2, mx, my)) {
            lines.add(Lang.tr("sc.gui.energy"));
            lines.add(storage.getEnergyStored() + " / " + storage.getMaxEnergyStored() + " EU");
            return lines;
        }
        if (storage.getStackInSlot(0) == null
                && GuiGaugeSC.isOver(ContainerEnergyStorageSC.SLOT_X, ContainerEnergyStorageSC.SLOT_Y, 16, 16, mx, my)) {
            lines.add(Lang.tr("sc.storage.gui.slot", storage.getTier().name()));
            return lines;
        }
        return null;
    }

    /** Tooltips in screen space - see GuiMachineSC.drawScreen. */
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        TextFitSC.beginFrame();
        super.drawScreen(mouseX, mouseY, partialTicks);
        List<String> tooltip = tooltipAt(mouseX - guiLeft, mouseY - guiTop);
        if (tooltip == null) {
            tooltip = TextFitSC.hoverAt(mouseX, mouseY);
        }
        if (tooltip != null) {
            drawHoveringText(GuiGaugeSC.wrapTooltip(fontRendererObj, tooltip, width), mouseX, mouseY, fontRendererObj);
        }
    }
}
