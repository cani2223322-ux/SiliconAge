package com.sc.inventory;

import java.util.ArrayList;
import java.util.List;

import com.sc.Reference;
import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityEnergyStorageSC;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;

/**
 * Energy storage screen: stored / capacity with a wide charge bar, the net flow per tick
 * (averaged over a second: + charging, - discharging), the output voltage, and a slot that
 * charges a weapon. Reuses the generator sheet (frame, energy well, player inventory).
 */
public class GuiEnergyStorageSC extends GuiContainer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(Reference.ASSETS, "textures/gui/guiGenerator.png");
    private static final int BAR_X = 8, BAR_Y = 42, BAR_W = 136, BAR_H = 8;
    private static final int ENERGY_X = 152, ENERGY_Y = 17, ENERGY_W = 10, ENERGY_H = 44;
    private static final int TEXT_X = 8, FLOW_Y = 55, OUTPUT_Y = 67;

    private final TileEntityEnergyStorageSC storage;

    public GuiEnergyStorageSC(InventoryPlayer playerInv, TileEntityEnergyStorageSC storage) {
        super(new ContainerEnergyStorageSC(playerInv, storage));
        this.storage = storage;
        xSize = 176;
        ySize = 166;
    }

    private float fraction() {
        return (float) storage.getEnergyStored() / Math.max(1, storage.getMaxEnergyStored());
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GuiGaugeSC.bind(mc, TEXTURE);
        int x = guiLeft, y = guiTop;
        drawTexturedModalRect(x, y, 0, 0, xSize, ySize);
        drawTexturedModalRect(x + ContainerEnergyStorageSC.SLOT_X - 1, y + ContainerEnergyStorageSC.SLOT_Y - 1,
                GuiGaugeSC.SPR_STEEL_SLOT_U, GuiGaugeSC.SPR_STEEL_SLOT_V, 18, 18);
        GuiGaugeSC.drawSpriteVertical(this, x + ENERGY_X, y + ENERGY_Y, GuiGaugeSC.SPR_ENERGY_U, GuiGaugeSC.SPR_ENERGY_V,
                ENERGY_W, ENERGY_H, fraction());
        // wide charge bar: well, fill, lighter top edge
        GuiGaugeSC.drawWell(x + BAR_X, y + BAR_Y, BAR_W, BAR_H);
        int filled = (int) (BAR_W * Math.min(1F, fraction()));
        if (filled > 0) {
            Gui.drawRect(x + BAR_X, y + BAR_Y, x + BAR_X + filled, y + BAR_Y + BAR_H, 0xFFD86A10);
            Gui.drawRect(x + BAR_X, y + BAR_Y, x + BAR_X + filled, y + BAR_Y + 2, 0xFFFFB040);
        }
        org.lwjgl.opengl.GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = Lang.tr("tile.siliconage." + (storage instanceof com.sc.tileentity.TileEntityChargePadSC ? "chargePad." : "energyStorage.")
                + storage.getTier().name().toLowerCase(java.util.Locale.ROOT) + ".name");
        fontRendererObj.drawString(title, 8, 5, GuiGaugeSC.TITLE_COLOR);
        GuiGaugeSC.drawTierBadge(fontRendererObj, storage.getTier(), xSize - 6, 3);
        fontRendererObj.drawString(Lang.tr("sc.storage.gui.stored", String.valueOf(storage.getEnergyStored())), 8, 20, 0x404040);
        fontRendererObj.drawString(Lang.tr("sc.storage.gui.capacity", String.valueOf(storage.getMaxEnergyStored()),
                Math.round(fraction() * 100)), 8, 30, 0x606060);
        // Flow and output sit left of the charge slot (x 130): 8..~110 px at the longest strings
        // ("Поток: +10240 EU/t", "Output: 2048 EU/t"); which face is the output is in the tooltip.
        int flow = storage.getFlowPerTick();
        fontRendererObj.drawString(Lang.tr("sc.storage.gui.flow", (flow > 0 ? "+" : "") + flow), TEXT_X, FLOW_Y,
                flow > 0 ? 0x2E7D32 : flow < 0 ? 0xB02418 : 0x606060);
        fontRendererObj.drawString(Lang.tr("sc.storage.gui.output", storage.getTier().getVoltage()), TEXT_X, OUTPUT_Y, 0x606060);
    }

    private List<String> tooltipAt(int mx, int my) {
        List<String> lines = new ArrayList<String>();
        if (GuiGaugeSC.isOver(TEXT_X, OUTPUT_Y - 1, ContainerEnergyStorageSC.SLOT_X - 4 - TEXT_X, 10, mx, my)) {
            lines.add(Lang.tr("sc.storage.tooltip.io", storage.getTier().getVoltage()));
            return lines;
        }
        if (GuiGaugeSC.isOver(ENERGY_X, ENERGY_Y, ENERGY_W, ENERGY_H, mx, my) || GuiGaugeSC.isOver(BAR_X, BAR_Y, BAR_W, BAR_H, mx, my)) {
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
        super.drawScreen(mouseX, mouseY, partialTicks);
        List<String> tooltip = tooltipAt(mouseX - guiLeft, mouseY - guiTop);
        if (tooltip != null) {
            drawHoveringText(tooltip, mouseX, mouseY, fontRendererObj);
        }
    }
}
