package com.sc.client;

import com.sc.bridge.BridgeHudDataSC;
import com.sc.bridge.BridgeMathSC;
import com.sc.bridge.BridgeMsgSC;
import com.sc.manual.Lang;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * The bridge's HUD line (docs/ground-bridge/bridge_6_world.png): while a portal you opened is open - top centre
 * «Мост «Имя»: портал открыт · NN с · стабильность NN%» and a bar of the time left. A key's answer (the armour's
 * «Домой» / «Открыть по последней цели» / «Запомнить точку» with no screen open) shows above the hotbar.
 */
public class BridgeHudSC {

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new BridgeHudSC());
    }

    /** A FarState came in: with no screen open, its message goes above the hotbar. */
    public static void heard(NBTTagCompound state) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen == null && state != null && state.hasKey("msg") && mc.ingameGUI != null) {
            mc.ingameGUI.func_110326_a(BridgeMsgSC.read(state.getCompoundTag("msg")).text(), false);
        }
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post e) {
        if (e.type != RenderGameOverlayEvent.ElementType.TEXT || !BridgeHudDataSC.shown()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.hideGUI) {
            return;
        }
        ScaledResolution r = e.resolution;
        String n = BridgeHudDataSC.name.length() == 0 ? Lang.tr("sc.bridge.res.noname") : BridgeHudDataSC.name;
        String line = Lang.tr("sc.bridge.hud.line", n, (BridgeHudDataSC.left + 19) / 20, BridgeHudDataSC.stability);
        int w = mc.fontRenderer.getStringWidth(line), x = (r.getScaledWidth() - w) / 2, y = 4;
        boolean space = BridgeHudDataSC.kind == BridgeMathSC.SPACE;
        int color = BridgeHudDataSC.stability < 30 ? 0xFF6A5A : space ? 0x9CC4FF : 0x7CF0A0;
        mc.fontRenderer.drawStringWithShadow(line, x, y, color);
        int bw = 120, bx = (r.getScaledWidth() - bw) / 2, by = y + 11;
        float f = BridgeHudDataSC.total <= 0 ? 0F : Math.max(0F, Math.min(1F, BridgeHudDataSC.left / (float) BridgeHudDataSC.total));
        Gui.drawRect(bx - 1, by - 1, bx + bw + 1, by + 4, 0xC0000000);
        Gui.drawRect(bx, by, bx + Math.round(bw * f), by + 3, space ? 0xFF6AA8FF : 0xFF3CCB6E);
    }
}
