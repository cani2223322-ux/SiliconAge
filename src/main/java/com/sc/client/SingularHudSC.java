package com.sc.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.lwjgl.opengl.GL11;

import com.sc.item.ItemArmorSC;
import com.sc.manual.Lang;
import com.sc.util.ArmorFeature;
import com.sc.util.ArmorGasSC;
import com.sc.util.ArmorGasSC.Gas;
import com.sc.util.SingularCooldowns;
import com.sc.util.SingularHud;
import com.sc.util.SingularLevel;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

/**
 * М3: the Singular suit's cooldown icons (SingularHud decides what's shown): above the hotbar by
 * default, or at the top / right / left, or off (ArmorKeyBindsSC.hudPos, the K menu's Level tab).
 * Each icon: the function's code, a dark sweep for the time left, the time / «готов» / «нет СМ», its
 * short name under it (in a row). Also К1's boost and weakness, Н4's slowing and К2's resonance.
 * Client only.
 */
public class SingularHudSC {

    private static final int BOX = 24, GAP = 4;
    private static final int PURPLE = 0xC080FF, DIM_PURPLE = 0x7A5A99, GREEN = 0x60FF60, RED = 0xFF5050, ORANGE = 0xFFA040, CYAN = 0x60E0FF;

    private static final class Icon {
        String code, text, name;
        int codeColor = PURPLE, textColor = 0xFFFFFF, edge = 0xFF4A3A5A;
        float sweep;
    }

    public static void register() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new SingularHudSC());
    }

    private static String key(ArmorFeature f) {
        return f.name().toLowerCase(Locale.ROOT);
    }

    /** The icons to draw now, in order. */
    static List<Icon> icons(EntityPlayer p) {
        List<Icon> out = new ArrayList<Icon>();
        ItemStack[] worn = ArmorGasSC.wornSet(p);
        boolean creative = p.capabilities.isCreativeMode;
        long now = p.worldObj.getTotalWorldTime();
        long boostEnd = SingularCooldowns.clientEnd(SingularCooldowns.P_BOOST), weakEnd = SingularCooldowns.clientEnd(SingularCooldowns.P_WEAK),
                slowEnd = SingularCooldowns.clientEnd(SingularCooldowns.P_SLOW);
        float mul = boostEnd > now ? 2F : 1F;
        String sec = Lang.tr("sc.singhud.sec");
        for (ArmorFeature f : SingularHud.FEATURES) {
            if (!SingularHud.has(worn, f, creative)) {
                continue;
            }
            long end = SingularCooldowns.endOf(p, f);
            Gas miss = SingularHud.missingGas(worn, f, mul);
            boolean wanted = ItemArmorSC.isEnabled(worn[f.piece], f) && (SingularHud.automatic(f) || ArmorKeyBindsSC.get(f) != null);
            long active = f == ArmorFeature.SINGULARITY ? boostEnd : f == ArmorFeature.TIME_SLOW ? slowEnd : 0L;
            int st = SingularHud.stateOf(f, true, end, now, miss != null, wanted, active, weakEnd);
            if (st == SingularHud.HIDDEN) {
                continue;
            }
            Icon ic = new Icon();
            ic.code = Lang.tr("sc.singhud.code." + key(f));
            ic.name = Lang.tr("sc.singhud.name." + key(f));
            switch (st) {
                case SingularHud.COOLING:
                    ic.text = SingularHud.time(SingularCooldowns.left(end, now), sec);
                    ic.sweep = SingularHud.sweep(end, now, SingularHud.cooldownTicks(f));
                    ic.codeColor = DIM_PURPLE;
                    break;
                case SingularHud.READY:
                    ic.text = Lang.tr("sc.singhud.ready");
                    ic.textColor = GREEN;
                    ic.edge = 0xFF000000 | PURPLE;
                    break;
                case SingularHud.NOGAS:
                    ic.text = Lang.tr("sc.singhud.nogas", GasUiSC.shortName(miss));
                    ic.textColor = RED;
                    ic.codeColor = DIM_PURPLE;
                    ic.edge = 0xFF803030;
                    break;
                case SingularHud.BOOST:
                    ic.text = Lang.tr("sc.singhud.boost", SingularHud.time(SingularCooldowns.left(boostEnd, now), sec));
                    ic.textColor = GREEN;
                    ic.sweep = SingularHud.sweep(boostEnd, now, ArmorFeature.BOOST_TICKS);
                    ic.edge = 0xFF000000 | GREEN;
                    break;
                case SingularHud.WEAK:
                    ic.text = Lang.tr("sc.singhud.weak", SingularHud.time(SingularCooldowns.left(weakEnd, now), sec));
                    ic.textColor = ORANGE;
                    ic.sweep = SingularHud.sweep(weakEnd, now, ArmorFeature.WEAK_TICKS);
                    ic.edge = 0xFF000000 | ORANGE;
                    break;
                default:                                    // RUNNING: Н4's slowing
                    ic.text = SingularHud.time(SingularCooldowns.left(slowEnd, now), sec);
                    ic.textColor = CYAN;
                    ic.sweep = SingularHud.sweep(slowEnd, now, ArmorFeature.SLOW_TICKS);
                    ic.edge = 0xFF000000 | CYAN;
                    break;
            }
            out.add(ic);
        }
        if (SingularCooldowns.clientEnd(SingularCooldowns.P_RES) != 0 && SingularHud.has(worn, ArmorFeature.RESONANCE, creative)) {
            Icon ic = new Icon();                           // К2 works: no time, just "on"
            ic.code = Lang.tr("sc.singhud.code.resonance");
            ic.name = Lang.tr("sc.singhud.name.resonance");
            ic.text = Lang.tr("sc.singhud.res");
            ic.textColor = CYAN;
            ic.edge = 0xFF000000 | CYAN;
            out.add(ic);
        }
        return out;
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || mc.thePlayer == null || mc.theWorld == null || mc.gameSettings.showDebugInfo
                || (mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.GuiChat))) {
            return;
        }
        int pos = ArmorKeyBindsSC.hudPos();
        if (pos == ArmorKeyBindsSC.HUD_OFF || !SingularLevel.wearsSingular(mc.thePlayer)) {
            return;
        }
        List<Icon> icons = icons(mc.thePlayer);
        if (icons.isEmpty()) {
            return;
        }
        int w = event.resolution.getScaledWidth(), h = event.resolution.getScaledHeight();
        int n = icons.size();
        boolean row = pos == ArmorKeyBindsSC.HUD_HOTBAR || pos == ArmorKeyBindsSC.HUD_TOP;
        int x, y;
        if (row) {
            int total = n * BOX + (n - 1) * GAP;
            x = (w - total) / 2;
            if (pos == ArmorKeyBindsSC.HUD_TOP) {
                y = 4 + (net.minecraft.entity.boss.BossStatus.bossName != null && net.minecraft.entity.boss.BossStatus.statusBarTime > 0 ? 18 : 0);
            } else {                                        // over the hearts / armour / food rows, whatever they take
                int rows = Math.max(net.minecraftforge.client.GuiIngameForge.left_height, net.minecraftforge.client.GuiIngameForge.right_height);
                y = h - rows - 4 - 9 - BOX;
            }
        } else {
            int total = n * (BOX + 3) - 3;
            x = pos == ArmorKeyBindsSC.HUD_RIGHT ? w - BOX - 4 : 4;
            y = Math.max(4, (h - total) / 2);
        }
        FontRenderer fr = mc.fontRenderer;
        for (Icon ic : icons) {
            drawIcon(fr, ic, x, y, row);
            if (row) {
                x += BOX + GAP;
            } else {
                y += BOX + 3;
            }
        }
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    private static void drawIcon(FontRenderer fr, Icon ic, int x, int y, boolean name) {
        Gui.drawRect(x, y, x + BOX, y + BOX, ic.edge);
        Gui.drawRect(x + 1, y + 1, x + BOX - 1, y + BOX - 1, 0xD8140F1E);
        if (ic.sweep > 0F) {                                // the dark part shrinks as the time runs out
            int sh = Math.max(1, Math.round((BOX - 2) * ic.sweep));
            Gui.drawRect(x + 1, y + 1, x + BOX - 1, y + 1 + sh, 0x90000000);
        }
        int cx = x + BOX / 2;
        text(fr, ic.code, cx, y + 3, BOX - 2, ic.codeColor);
        text(fr, ic.text, cx, y + BOX - 10, BOX - 2, ic.textColor);
        if (name && ic.name != null) {
            text(fr, ic.name, cx, y + BOX + 1, BOX + GAP, 0xE0E0E0);
        }
    }

    /** Centred at cx, scaled down (to half at most) to fit maxW. */
    private static void text(FontRenderer fr, String s, int cx, int y, int maxW, int color) {
        if (s == null || s.isEmpty()) {
            return;
        }
        int sw = fr.getStringWidth(s);
        float sc = sw > maxW ? Math.max(0.5F, maxW / (float) sw) : 1F;
        GL11.glPushMatrix();
        GL11.glTranslatef(cx, y + (1F - sc) * 4F, 0F);
        GL11.glScalef(sc, sc, 1F);
        fr.drawStringWithShadow(s, -sw / 2, 0, color);
        GL11.glPopMatrix();
    }
}
