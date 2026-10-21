package com.sc.emp.client;

import java.util.Locale;

import org.lwjgl.opengl.GL11;

import com.sc.client.GasUiSC;
import com.sc.emp.EmpMathSC;
import com.sc.emp.EmpMode;
import com.sc.emp.EmpModule;
import com.sc.emp.EmpNetSC;
import com.sc.emp.EmpSC;
import com.sc.emp.ItemEmpGunSC;
import com.sc.item.ArmorLogicSC;
import com.sc.manual.Lang;
import com.sc.util.ArmorFeature;
import com.sc.util.ArmorGasSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * ПЭ-1 on the HUD (shown with or without the helmet's HUD).
 * <ul>
 * <li>The shooter, the gun in hand: the charge-up bar above the hotbar in the mode's colour (Siphon with no receiver:
 * «Некуда принимать» there instead); under the crosshair «ПЭ-1 перегрет: N с» / the blinking 2-second warning; while the stream
 * runs, the target's charge bar (only with a Sensor chip - the server sends -1 without) and «Принято: N EU».</li>
 * <li>The target, above the crosshair: «Броня подавлена: шлем 6 с» per suppressed piece (or the whole suit in one line),
 * «×3 расход: 14 с» when the helmet's gas bars don't show it (ArmorClientSC draws it there otherwise), «ЭМИ-наведение»
 * blinking (only with a Sensor chip) and «Энергия утекает!», each with an arrow round the crosshair pointing at the
 * shooter.</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class EmpHudSC {

    public static final EmpHudSC INSTANCE = new EmpHudSC();

    private static final int COL_SUPPRESSED = 0x6ED7FF, COL_BOIL = 0xFFA040, COL_AIMED = 0xFF5050, COL_DRAIN = 0x82EC64,
            COL_WARN = 0xFF5050, COL_HOT = 0xFFA040, COL_TEXT = 0xB0B0B0;
    /** The charge-up bar: width, and how far above the screen's bottom (just over the armour / hearts rows). */
    private static final int BAR_W = 82, BAR_FROM_BOTTOM = 54;
    /** Arrows round the crosshair: radius, pixels. */
    private static final float ARROW_R = 22F;
    /** lastAim older than this is stale. */
    private static final long AIM_FRESH_MS = 1000L;

    private EmpHudSC() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(INSTANCE);
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || mc.thePlayer == null || mc.currentScreen != null
                || mc.gameSettings.showDebugInfo) {
            return;
        }
        EntityPlayer p = mc.thePlayer;
        int w = event.resolution.getScaledWidth(), h = event.resolution.getScaledHeight();
        ItemStack gun = ItemEmpGunSC.held(p);
        if (gun != null) {
            shooter(mc, p, gun, w, h);
        }
        target(mc, p, w, h);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    // ------------------------------------------------------------------ the shooter

    private static void shooter(Minecraft mc, EntityPlayer p, ItemStack gun, int w, int h) {
        FontRenderer fr = mc.fontRenderer;
        EmpMode mode = ItemEmpGunSC.modeOf(gun);
        boolean using = p.isUsingItem() && ItemEmpGunSC.isGun(p.getItemInUse());
        int cx = w / 2;
        // the charge-up bar, filling in the mode's colour; its name on the left
        int need = EmpMathSC.chargeTicks(mode, ItemEmpGunSC.upgraded(gun));
        if (using && need > 0) {
            float f = Math.min(1F, p.getItemInUseDuration() / (float) need);
            int x0 = cx - BAR_W / 2, y0 = h - BAR_FROM_BOTTOM;
            Gui.drawRect(x0 - 1, y0 - 1, x0 + BAR_W + 1, y0 + 4, 0xA0000000);
            Gui.drawRect(x0, y0, x0 + (int) (BAR_W * f), y0 + 3, 0xFF000000 | (f >= 1F ? 0xFFFFFF : mode.rgb()));
            String label = Lang.tr("sc.emp.hud.charge", Lang.tr("sc.emp.mode." + mode.key()));
            fr.drawStringWithShadow(label, x0 - 4 - fr.getStringWidth(label), y0 - 3, mode.rgb());
        }
        // Siphon with nowhere to put the energy: in the charge-up bar's place, above the hotbar
        EmpNetSC.AimMessage aim = freshAim();
        if (mode == EmpMode.SIPHON && aim != null && aim.receiver < 0 && !aim.streaming) {
            centred(fr, Lang.tr("sc.emp.hud.noreceiver"), cx, h - BAR_FROM_BOTTOM - 4, COL_WARN);
        }
        // the gun's heat under the crosshair
        int y = h / 2 + 12;
        boolean cooling = ItemEmpGunSC.hasModule(gun, EmpModule.COOLING);
        int heat = ItemEmpGunSC.heatOf(gun);
        boolean streamHeat = using && mode == EmpMode.SIPHON && aim != null && aim.streaming && aim.heat >= 0;
        if (streamHeat) {
            heat = aim.heat;                                     // the server keeps the stream's heat until it ends (rule 0.4.1): its readout
        }
        if (streamHeat && aim.throttled) {
            y = centred(fr, Lang.tr("sc.emp.hud.throttled"), cx, y, GasUiSC.blinkOff() ? 0xB06020 : COL_WARN);   // soft overheat (2026-10-11)
        } else if (!streamHeat && ItemEmpGunSC.overheated(gun)) {
            y = centred(fr, Lang.tr("sc.emp.hud.overheat", EmpMathSC.coolSeconds(heat)), cx, y, COL_WARN);
        } else if (ItemEmpGunSC.warnOn(gun) && EmpMathSC.overheatWarn(heat, cooling, mode)) {   // ОР-3: by the mode, as the chat's
            y = centred(fr, Lang.tr(mode == EmpMode.SIPHON ? "sc.emp.hud.overheat.warn" : "sc.emp.hud.overheat.next"), cx, y,
                    GasUiSC.blinkOff() ? 0xB06020 : COL_HOT);
        }
        // the stream: the target's charge (Sensor chip) and what came in
        if (aim != null && aim.streaming) {
            if (aim.chargePct >= 0) {
                String l = Lang.tr("sc.emp.hud.target", aim.chargePct);
                int bw = 50, total = fr.getStringWidth(l) + 4 + bw;
                int x0 = cx - total / 2;
                fr.drawStringWithShadow(l, x0, y, COL_TEXT);
                int bx = x0 + fr.getStringWidth(l) + 4, by = y + 3;
                Gui.drawRect(bx - 1, by - 1, bx + bw + 1, by + 3, 0xA0000000);
                Gui.drawRect(bx, by, bx + bw * Math.max(0, Math.min(100, aim.chargePct)) / 100, by + 2, 0xFF000000 | EmpMode.SIPHON.rgb());
                y += 10;
            }
            y = centred(fr, Lang.tr("sc.emp.hud.received", eu(aim.received)), cx, y, EmpMode.SIPHON.rgb());
            if (aim.vented > 0) {
                y = centred(fr, Lang.tr("sc.emp.hud.vented", eu(aim.vented)), cx, y, 0xFF9A3C);
            }
        }
    }

    private static EmpNetSC.AimMessage freshAim() {
        EmpNetSC.AimMessage a = EmpNetSC.lastAim;
        return a != null && System.currentTimeMillis() - EmpNetSC.lastAimAt < AIM_FRESH_MS ? a : null;
    }

    // ------------------------------------------------------------------ the target

    private static void target(Minecraft mc, EntityPlayer p, int w, int h) {
        FontRenderer fr = mc.fontRenderer;
        int cx = w / 2, cy = h / 2;
        int y = cy - 34;                                         // lines go up from just above the crosshair
        // suppression: the whole suit in one line, else each piece
        if (EmpSC.anySuppressed(p)) {
            int worn = 0, sup = 0, longest = 0;
            for (int i = 0; i < 4; i++) {
                if (EmpSC.modPiece(p, i) != null) {
                    worn++;
                }
                if (EmpSC.isSuppressed(p, i)) {
                    sup++;
                    longest = Math.max(longest, EmpSC.suppressedTicks(p, i));
                }
            }
            if (sup >= 2 && sup >= worn) {
                y = up(fr, Lang.tr("sc.emp.hud.suppressed.all", seconds(longest)), cx, y, COL_SUPPRESSED);
            } else {
                for (int i = 3; i >= 0; i--) {
                    if (EmpSC.isSuppressed(p, i)) {
                        String piece = Lang.tr("sc.armorhud.piece." + i).toLowerCase(Locale.ROOT);
                        y = up(fr, Lang.tr("sc.emp.hud.suppressed", piece, seconds(EmpSC.suppressedTicks(p, i))), cx, y, COL_SUPPRESSED);
                    }
                }
            }
        }
        // the helium boil, unless the helmet's gas bars show it next to the helium bar
        int mul = EmpSC.boilMultiplier(p);
        boolean gasBars = ArmorLogicSC.active(p, ArmorFeature.HUD) && ArmorGasSC.suitCapacity(p, ArmorGasSC.Gas.HELIUM) > 0;
        if (mul > 1 && !gasBars && ArmorGasSC.suitCapacity(p, ArmorGasSC.Gas.HELIUM) > 0) {
            y = up(fr, Lang.tr("sc.emp.hud.boil", mul, seconds(EmpSC.boilTicks(p))), cx, y, COL_BOIL);
        }
        boolean blink = GasUiSC.blinkOff();
        if (EmpSC.drained(p)) {
            y = up(fr, Lang.tr("sc.emp.hud.drain"), cx, y, blink ? 0x4E8E3C : COL_DRAIN);
            arrow(mc, p, EmpSC.drainedBy(p), cx, cy, COL_DRAIN);
        }
        if (EmpSC.aimedAt(p) && EmpSC.hasSensorChip(p)) {
            if (!blink) {
                up(fr, Lang.tr("sc.emp.hud.aimed"), cx, y, COL_AIMED);
            }
            arrow(mc, p, EmpSC.aimedBy(p), cx, cy, blink ? 0x802828 : COL_AIMED);
        }
    }

    // ------------------------------------------------------------------ drawing helpers

    /** A centred line at y. @return the y under it */
    private static int centred(FontRenderer fr, String s, int cx, int y, int rgb) {
        fr.drawStringWithShadow(s, cx - fr.getStringWidth(s) / 2, y, rgb);
        return y + 10;
    }

    /** A centred line at y. @return the y of the line above it */
    private static int up(FontRenderer fr, String s, int cx, int y, int rgb) {
        fr.drawStringWithShadow(s, cx - fr.getStringWidth(s) / 2, y, rgb);
        return y - 10;
    }

    /** A small triangle round the crosshair pointing the way to entity `id` (nothing when it isn't loaded here). */
    private static void arrow(Minecraft mc, EntityPlayer p, int id, int cx, int cy, int rgb) {
        Entity from = mc.theWorld == null ? null : mc.theWorld.getEntityByID(id);
        if (from == null || from == p) {
            return;
        }
        double dx = from.posX - p.posX, dz = from.posZ - p.posZ;
        double yawTo = Math.toDegrees(Math.atan2(-dx, dz));          // the yaw facing it (0 = +z, 90 = -x)
        double rel = Math.toRadians(MathHelper.wrapAngleTo180_double(yawTo - p.rotationYaw));   // + : to our right
        float sx = (float) Math.sin(rel), sy = (float) -Math.cos(rel);  // screen: up is ahead
        float px = -sy, py = sx;                                         // across the arrow
        float bx = cx + sx * ARROW_R, by = cy + sy * ARROW_R, tip = 7F, half = 4F;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        Tessellator t = Tessellator.instance;
        t.startDrawing(GL11.GL_TRIANGLES);
        t.setColorRGBA_I(rgb, 230);
        t.addVertex(bx + sx * tip, by + sy * tip, 0);
        t.addVertex(bx - px * half, by - py * half, 0);
        t.addVertex(bx + px * half, by + py * half, 0);
        t.draw();
        GL11.glPopAttrib();
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** Ticks -> whole seconds, rounded up. */
    private static int seconds(int ticks) {
        return (ticks + 19) / 20;
    }

    /** 1 234 567 with spaces. */
    private static String eu(long v) {
        return String.format(Locale.ROOT, "%,d", v).replace(',', ' ');
    }
}
