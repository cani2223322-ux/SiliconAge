package com.sc.client;

import org.lwjgl.opengl.GL11;

import com.sc.bridge.BridgeHudDataSC;
import com.sc.bridge.BridgeMathSC;
import com.sc.bridge.BridgeMsgSC;
import com.sc.manual.Lang;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * The bridge's HUD line (docs/ground-bridge/bridge_6_world.png): while a portal you opened is open - top centre
 * «Мост «Имя»: портал открыт · NN с · стабильность NN%» (green / yellow / red by the stability, С3), the ring's heat
 * when it is warm (С12) and a bar of the time left; a shortage's «схлопывание через N с» under it (С11). A key's
 * answer (the armour's «Домой» / «Открыть по последней цели» / «Запомнить точку» with no screen open) shows above
 * the hotbar. §11: the birth of a singularity near you - an implosion of particles and a quick white flash.
 */
public class BridgeHudSC {

    /** The flash of a birth nearby: when it started (system time). */
    private static long flashAt;
    private static final int FLASH_MS = 350, FLASH_RANGE = 24;

    public static void register() {
        BridgeHudSC h = new BridgeHudSC();
        MinecraftForge.EVENT_BUS.register(h);
        FMLCommonHandler.instance().bus().register(h);
    }

    /** A FarState came in: with no screen open, its message goes above the hotbar. */
    public static void heard(NBTTagCompound state) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen == null && state != null && state.hasKey("msg") && mc.ingameGUI != null) {
            mc.ingameGUI.func_110326_a(BridgeMsgSC.read(state.getCompoundTag("msg")).text(), false);
        }
    }

    /** §11: the births heard are played on the client's thread. */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        double[] b;
        while ((b = BridgeHudDataSC.BIRTHS.poll()) != null) {
            if (mc.theWorld != null && mc.thePlayer != null) {
                birth(mc.theWorld, b[0], b[1], b[2], (int) b[3]);
                if (mc.thePlayer.getDistanceSq(b[0], b[1], b[2]) < FLASH_RANGE * FLASH_RANGE) {
                    flashAt = System.currentTimeMillis();
                }
            }
        }
    }

    /** An implosion: particles fly in to the centre from a shell round it, a spark burst at the centre. */
    private static void birth(World w, double x, double y, double z, int kind) {
        java.util.Random r = w.rand;
        boolean space = kind == BridgeMathSC.SPACE;
        double rad = space ? 3.5 : 2.5;
        for (int i = 0; i < 120; i++) {
            double th = r.nextDouble() * Math.PI * 2, ph = Math.acos(2 * r.nextDouble() - 1);
            double dx = Math.sin(ph) * Math.cos(th) * rad, dy = Math.cos(ph) * rad, dz = Math.sin(ph) * Math.sin(th) * rad;
            w.spawnParticle("portal", x, y, z, dx, dy, dz);      // the portal particle starts at x + motion and flies to x
        }
        for (int i = 0; i < 24; i++) {
            w.spawnParticle("fireworksSpark", x, y, z, (r.nextDouble() - 0.5) * 0.5, (r.nextDouble() - 0.5) * 0.5, (r.nextDouble() - 0.5) * 0.5);
        }
        w.spawnParticle("hugeexplosion", x, y, z, 0, 0, 0);
        w.playSound(x, y, z, "random.explode", 0.4F, space ? 1.6F : 1.9F, false);
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post e) {
        if (e.type != RenderGameOverlayEvent.ElementType.TEXT) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution r = e.resolution;
        long since = System.currentTimeMillis() - flashAt;
        if (since >= 0 && since < FLASH_MS) {
            int a = (int) (170 * (1F - since / (float) FLASH_MS));
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            Gui.drawRect(0, 0, r.getScaledWidth(), r.getScaledHeight(), (a << 24) | 0xFFFFFF);
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
        if (!BridgeHudDataSC.shown() || mc.gameSettings.hideGUI) {
            return;
        }
        String n = BridgeHudDataSC.name.length() == 0 ? Lang.tr("sc.bridge.res.noname") : BridgeHudDataSC.name;
        String line = Lang.tr("sc.bridge.hud.line", n, (BridgeHudDataSC.left + 19) / 20, BridgeHudDataSC.stability);
        if (BridgeHudDataSC.heat >= 40) {
            line += " · " + Lang.tr("sc.bridge.hud.heat", BridgeHudDataSC.heat);
        }
        int w = mc.fontRenderer.getStringWidth(line), x = (r.getScaledWidth() - w) / 2, y = 4;
        boolean space = BridgeHudDataSC.kind == BridgeMathSC.SPACE;
        int st = BridgeHudDataSC.stability;
        int color = st < BridgeMathSC.TURBULENCE ? 0xFF6A5A : st < 70 ? 0xFFE14D : space ? 0x9CC4FF : 0x7CF0A0;
        mc.fontRenderer.drawStringWithShadow(line, x, y, color);
        int bw = 120, bx = (r.getScaledWidth() - bw) / 2, by = y + 11;
        float f = BridgeHudDataSC.total <= 0 ? 0F : Math.max(0F, Math.min(1F, BridgeHudDataSC.left / (float) BridgeHudDataSC.total));
        Gui.drawRect(bx - 1, by - 1, bx + bw + 1, by + 4, 0xC0000000);
        Gui.drawRect(bx, by, bx + Math.round(bw * f), by + 3, space ? 0xFF6AA8FF : 0xFF3CCB6E);
        String warn = null;
        if (BridgeHudDataSC.warn >= 0) {
            String what = BridgeHudDataSC.shortWhat.length() == 0 ? "?" : Lang.tr(BridgeMsgSC.RES + BridgeHudDataSC.shortWhat);
            warn = Lang.tr("sc.bridge.hud.warn", BridgeHudDataSC.warn, what);
        } else if (BridgeHudDataSC.heat * 10 >= BridgeMathSC.HEAT_WARN) {
            warn = Lang.tr("sc.bridge.hud.hot");
        } else if (st < BridgeMathSC.TURBULENCE) {
            warn = Lang.tr("sc.bridge.hud.turb");
        }
        if (warn != null && (System.currentTimeMillis() / 400) % 3 != 0) {
            int ww = mc.fontRenderer.getStringWidth(warn);
            mc.fontRenderer.drawStringWithShadow(warn, (r.getScaledWidth() - ww) / 2, by + 7, 0xFF6A5A);
        }
    }
}
