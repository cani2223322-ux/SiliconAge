package com.sc.client;

import org.lwjgl.input.Keyboard;

import com.sc.bridge.BridgeFarSC;
import com.sc.bridge.BridgeItemDataSC;
import com.sc.bridge.BridgeNetSC;
import com.sc.item.ItemCoordinatorSC;
import com.sc.manual.Lang;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.ItemStack;

/** The Coordinator's name screen (sneak + right-click): the point it keeps and a name for it; Enter / «Готово» saves. */
public class GuiCoordinatorSC extends GuiScreen {

    private GuiTextField name;

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        ItemStack s = mc.thePlayer.getHeldItem();
        name = new GuiTextField(fontRendererObj, width / 2 - 90, height / 2 - 6, 180, 14);
        name.setMaxStringLength(32);
        name.setText(BridgeItemDataSC.pointName(s));
        name.setFocused(true);
        buttonList.clear();
        buttonList.add(new GuiButton(1, width / 2 - 90, height / 2 + 14, 88, 20, Lang.tr("sc.coordinator.gui.save")));
        buttonList.add(new GuiButton(2, width / 2 + 2, height / 2 + 14, 88, 20, Lang.tr("sc.coordinator.gui.cancel")));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void save() {
        if (ItemCoordinatorSC.isCoordinator(mc.thePlayer.getHeldItem())) {
            BridgeItemDataSC.setPointName(mc.thePlayer.getHeldItem(), name.getText());        // shown at once
            BridgeNetSC.sendFar(BridgeFarSC.SRC_COORD, 0, BridgeFarSC.F_RENAME, null, name.getText());
        }
        mc.displayGuiScreen(null);
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        if (b.id == 1) {
            save();
        } else {
            mc.displayGuiScreen(null);
        }
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            save();
            return;
        }
        if (key == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }
        name.textboxKeyTyped(c, key);
    }

    @Override
    protected void mouseClicked(int x, int y, int b) {
        super.mouseClicked(x, y, b);
        name.mouseClicked(x, y, b);
    }

    @Override
    public void updateScreen() {
        name.updateCursorCounter();
        if (!ItemCoordinatorSC.isCoordinator(mc.thePlayer.getHeldItem())) {
            mc.displayGuiScreen(null);
        }
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        drawDefaultBackground();
        ItemStack s = mc.thePlayer.getHeldItem();
        drawCenteredString(fontRendererObj, Lang.tr("item.siliconage.coordinator.name"), width / 2, height / 2 - 44, 0x8CF0A8);
        drawCenteredString(fontRendererObj, ItemCoordinatorSC.label(s), width / 2, height / 2 - 30, 0xA0A8B4);
        drawCenteredString(fontRendererObj, Lang.tr("sc.coordinator.gui.name"), width / 2, height / 2 - 18, 0xE6EAF0);
        name.drawTextBox();
        super.drawScreen(mx, my, pt);
    }
}
