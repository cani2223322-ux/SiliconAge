package com.sc.client;

import org.lwjgl.opengl.GL11;

import com.sc.init.ModItems;
import com.sc.item.ArmorLogicSC;
import com.sc.manual.Lang;
import com.sc.radiation.RadiationSC;
import com.sc.radiation.RadiationStateSC;
import com.sc.util.ArmorFeature;
import com.sc.util.ArmorGasSC;
import com.sc.util.SingularLevel;
import com.sc.util.ToolLevelSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

/**
 * Radiation on screen, top right: with a dosimeter in the hotbar (or the suit's HUD on) the
 * radiation, the dose and the protection; otherwise only a warning while radiation gets through
 * or a dose is left. The dosimeter crackles faster the stronger the radiation.
 */
public class RadiationClientSC {

    public static void register() {
        RadiationClientSC instance = new RadiationClientSC();
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(instance);   // ClientTickEvent
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(instance);       // overlay
    }

    /** A dosimeter in the hotbar. */
    public static boolean hasDosimeter(EntityPlayer p) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.inventory.mainInventory[i];
            if (s != null && s.getItem() == ModItems.dosimeter) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase == TickEvent.Phase.START && mc.thePlayer != null) {
            noSprint(mc);
            return;
        }
        if (event.phase != TickEvent.Phase.END || mc.thePlayer == null || mc.theWorld == null || mc.isGamePaused()
                || !RadiationStateSC.fresh()) {
            return;
        }
        float level = RadiationStateSC.level;
        if (level <= 0.05F || !hasDosimeter(mc.thePlayer)) {
            return;
        }
        float chance = Math.min(0.75F, 0.03F + level * 0.06F);
        if (mc.theWorld.rand.nextFloat() < chance) {
            EntityPlayer p = mc.thePlayer;
            mc.theWorld.playSound(p.posX, p.posY, p.posZ, "siliconage:rad.click",
                    0.35F, 0.85F + mc.theWorld.rand.nextFloat() * 0.4F, false);
        }
    }

    /**
     * The full lead suit: no running. The client sets sprinting again each tick while the key is
     * held (or after a double tap) before it moves - so the key is let go and the double-tap
     * timer cleared before the player's update, not after.
     */
    private static void noSprint(Minecraft mc) {
        EntityPlayer p = mc.thePlayer;
        if (com.sc.radiation.LeadSuitSC.parts(p) < com.sc.radiation.LeadSuitSC.FULL || p.capabilities.isFlying) {
            return;
        }
        net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        p.setSprinting(false);
        try {
            cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(net.minecraft.client.entity.EntityPlayerSP.class,
                    mc.thePlayer, 0, "sprintToggleTimer", "field_71156_d");
        } catch (RuntimeException e) {
            // another mapping: the key is let go all the same
        }
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || mc.thePlayer == null || mc.currentScreen != null
                || mc.gameSettings.showDebugInfo || !RadiationStateSC.fresh()) {
            return;
        }
        EntityPlayer p = mc.thePlayer;
        ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int right = res.getScaledWidth() - 4, y = topY(p);
        float level = RadiationStateSC.level, dose = RadiationStateSC.dose, through = RadiationStateSC.through();
        int prot = RadiationStateSC.protection, flags = RadiationStateSC.flags;
        boolean blink = (p.ticksExisted / 10) % 2 == 0;
        if (hasDosimeter(p) || ArmorLogicSC.active(p, ArmorFeature.HUD)) {
            y = line(mc, Lang.tr("sc.radhud.level", RadiationSC.fmt(level)), right, y, levelColor(level));
            y = line(mc, Lang.tr("sc.radhud.dose", RadiationSC.fmt(dose)), right, y, doseColor(dose));
            if (level > 0.05F) {
                String by = (flags & RadiationSC.F_FIELD) != 0 ? Lang.tr("sc.radhud.by.field")
                        : (flags & RadiationSC.F_ARMOR) != 0 ? Lang.tr("sc.radhud.by.armor")
                        : (flags & RadiationSC.F_LEAD) != 0 ? Lang.tr("sc.radhud.by.lead") : "";
                y = line(mc, Lang.tr("sc.radhud.prot", prot) + (by.isEmpty() ? "" : " " + by), right, y,
                        prot >= 100 ? 0x60FF60 : prot > 0 ? 0xFFD040 : 0xFF5050);
            }
            if ((flags & RadiationSC.F_ARMOR_FAIL) != 0 && blink) {
                y = line(mc, Lang.tr("sc.radhud.armorfail"), right, y, 0xFF4040);
            }
        } else if (through > 0.05F || dose >= 1F) {
            int col = through > 0.05F ? (blink ? 0xFF4040 : 0xFFA040) : doseColor(dose);
            y = line(mc, Lang.tr(through > 0.05F ? "sc.radhud.warn" : "sc.radhud.dosehint", RadiationSC.fmt(dose)), right, y, col);
        }
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** The height of SingularHudSC.drawDrillPanel (five lines: 4 + 13 + 4 * 10 + 3 + 2). */
    private static final int DRILL_PANEL_H = 62;

    /**
     * Where the radiation lines start, under what the earlier overlays put in the top right corner: the
     * Singular drill's panel (SingularHudSC, unless the icons are on the right) and the suit HUD's
     * «готово к модернизации» two lines (ArmorClientSC).
     */
    private static int topY(EntityPlayer p) {
        int y = 4;
        if (ToolLevelSC.isDrill(p.getCurrentEquippedItem()) && ArmorKeyBindsSC.hudPos() != ArmorKeyBindsSC.HUD_RIGHT) {
            y += DRILL_PANEL_H + 3;
        }
        if (ArmorLogicSC.active(p, ArmorFeature.HUD)) {
            for (int t = 0; t < 4; t++) {
                if (SingularLevel.readyToUpgrade(p, ArmorGasSC.worn(p, t))) {
                    y += 22;
                    break;
                }
            }
        }
        return y;
    }

    private static int line(Minecraft mc, String text, int right, int y, int color) {
        mc.fontRenderer.drawStringWithShadow(text, right - mc.fontRenderer.getStringWidth(text), y, color);
        return y + 10;
    }

    public static int levelColor(float level) {
        return level <= 0.05F ? 0x60FF60 : level < 2F ? 0xFFE060 : level < 6F ? 0xFFA040 : 0xFF4040;
    }

    public static int doseColor(float dose) {
        return dose < 1F ? 0x60FF60 : dose < RadiationSC.STAGE_NAUSEA ? 0xFFE060 : dose < RadiationSC.STAGE_WEAK ? 0xFFA040 : 0xFF4040;
    }
}
