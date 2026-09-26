package com.sc.inventory;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import com.sc.Reference;
import com.sc.conduit.ItemFilterSC;
import com.sc.manual.Lang;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/** Item filter set-up screen: example slots, the switches, the player's inventory. */
public class GuiFilterSC extends GuiContainer {

    private static final ResourceLocation SHEET = new ResourceLocation(Reference.ASSETS, "textures/gui/guiMachine.png");
    private static final int W = 176, H = 166;

    private final ContainerFilterSC container;

    public GuiFilterSC(EntityPlayer player, int heldSlot) {
        super(new ContainerFilterSC(player, heldSlot));
        this.container = (ContainerFilterSC) inventorySlots;
        xSize = W;
        ySize = H;
    }

    private boolean advanced() {
        ItemStack f = container.filter();
        return f != null && ItemFilterSC.isAdvanced(f);
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();
        int x = guiLeft + 102, y = guiTop + 18;
        buttonList.add(new GuiButton(ContainerFilterSC.BTN_BLACKLIST, x, y, 66, 14, ""));
        buttonList.add(new GuiButton(ContainerFilterSC.BTN_IGNORE_META, x, y + 15, 66, 14, ""));
        if (advanced()) {
            buttonList.add(new GuiButton(ContainerFilterSC.BTN_MATCH_NBT, x, y + 30, 66, 14, ""));
            buttonList.add(new GuiButton(ContainerFilterSC.BTN_ORE_DICT, x, y + 45, 66, 14, ""));
        }
        refreshLabels();
    }

    private void refreshLabels() {
        ItemStack f = container.filter();
        if (f == null) {
            return;
        }
        for (Object o : buttonList) {
            GuiButton b = (GuiButton) o;
            switch (b.id) {
                case ContainerFilterSC.BTN_BLACKLIST:
                    b.displayString = Lang.tr(ItemFilterSC.flag(f, ItemFilterSC.BLACKLIST) ? "sc.filter.blacklist" : "sc.filter.whitelist");
                    break;
                case ContainerFilterSC.BTN_IGNORE_META:
                    b.displayString = Lang.tr(ItemFilterSC.flag(f, ItemFilterSC.IGNORE_META) ? "sc.filter.meta.off" : "sc.filter.meta.on");
                    break;
                case ContainerFilterSC.BTN_MATCH_NBT:
                    b.displayString = Lang.tr(ItemFilterSC.flag(f, ItemFilterSC.MATCH_NBT) ? "sc.filter.nbt.on" : "sc.filter.nbt.off");
                    break;
                default:
                    b.displayString = Lang.tr(ItemFilterSC.flag(f, ItemFilterSC.ORE_DICT) ? "sc.filter.ore.on" : "sc.filter.ore.off");
            }
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (container.filter() == null) {
            mc.thePlayer.closeScreen();
            return;
        }
        refreshLabels();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        mc.playerController.sendEnchantPacket(inventorySlots.windowId, button.id);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int x0 = guiLeft, y0 = guiTop, x1 = x0 + W, y1 = y0 + H;
        drawRect(x0, y0, x1, y1, 0xFFC6C6C6);
        drawRect(x0, y0, x1 - 2, y0 + 2, 0xFFFFFFFF);
        drawRect(x0, y0, x0 + 2, y1 - 2, 0xFFFFFFFF);
        drawRect(x1 - 2, y0 + 2, x1, y1, 0xFF373737);
        drawRect(x0 + 2, y1 - 2, x1, y1, 0xFF373737);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        mc.getTextureManager().bindTexture(SHEET);
        for (int i = 0; i < container.ghostCount(); i++) {
            drawTexturedModalRect(guiLeft + ContainerFilterSC.GHOST_X - 1 + (i % 5) * 18, guiTop + ContainerFilterSC.GHOST_Y - 1 + (i / 5) * 18,
                    GuiGaugeSC.SPR_SLOT_U, GuiGaugeSC.SPR_SLOT_V, 18, 18);
        }
        for (int row = 0; row < 4; row++) {
            int y = guiTop + ContainerFilterSC.INV_Y - 1 + (row < 3 ? row * 18 : 58);
            for (int col = 0; col < 9; col++) {
                drawTexturedModalRect(guiLeft + ContainerFilterSC.INV_X - 1 + col * 18, y, GuiGaugeSC.SPR_SLOT_U, GuiGaugeSC.SPR_SLOT_V, 18, 18);
            }
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        ItemStack f = container.filter();
        fontRendererObj.drawString(f == null ? "" : f.getDisplayName(), 8, 6, 0x404040);
        fontRendererObj.drawString(Lang.tr("sc.filter.examples"), 8, 60, 0x606060);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        for (Object o : buttonList) {
            GuiButton b = (GuiButton) o;
            if (mouseX >= b.xPosition && mouseY >= b.yPosition && mouseX < b.xPosition + b.width && mouseY < b.yPosition + b.height) {
                List<String> tip = new ArrayList<String>();
                tip.add(Lang.tr("sc.filter.tip." + b.id));
                drawHoveringText(GuiGaugeSC.wrapTooltip(fontRendererObj, tip, width), mouseX, mouseY, fontRendererObj);
            }
        }
    }
}
