package com.sc.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.lwjgl.opengl.GL11;

import com.sc.item.BladeLogicSC;
import com.sc.item.DrillLogicSC;
import com.sc.item.ItemArmorSC;
import com.sc.item.ItemBladeSC;
import com.sc.item.ItemDrillSC;
import com.sc.manual.Lang;
import com.sc.util.ArmorFeature;
import com.sc.util.ArmorGasSC;
import com.sc.util.ArmorGasSC.Gas;
import com.sc.util.BladeFeature;
import com.sc.util.BladeType;
import com.sc.util.DrillFeature;
import com.sc.util.DrillType;
import com.sc.util.SingularCooldowns;
import com.sc.util.SingularHud;
import com.sc.util.SingularLevel;
import com.sc.util.ToolGasSC;
import com.sc.util.ToolLevelSC;

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
 * The Singular tools (docs/plan-singular-tools.md §4): the blade's form over the hearts / food rows, its and the
 * drill's key function cooldowns next to the armour's (same icons, the tool's NBT cooldowns), and a panel at the
 * top with the drill's mode, the crumb in progress, SM in the armour and the drill's heat.
 * Client only.
 */
public class SingularHudSC {

    private static final int BOX = 24, GAP = 4;
    private static final int PURPLE = 0xC080FF, DIM_PURPLE = 0x7A5A99, GREEN = 0x60FF60, RED = 0xFF5050, ORANGE = 0xFFA040, CYAN = 0x60E0FF;
    /** The height of drawDrillPanel's panel (five lines: 4 + 13 + 4 * 10 + 3 + 2); the overlays under it start lower. */
    public static final int DRILL_PANEL_H = 62;
    /** BridgeHudSC's top centre block while a portal is open: its line, the bar and room for its blinking warning. */
    private static final int BRIDGE_TOP_H = 28;

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

    // ------------------------------------------------------------------ the Singular tools (docs/plan-singular-tools.md §4)

    /** A short key caption for a tool icon: "R", "^V", "M4"; unbound - the name's first two letters. */
    private static String keyCode(Enum<?> f, String name) {
        int[] b = f == null ? null : ArmorKeyBindsSC.get(f);
        if (b == null || b[0] == 0) {
            return name.length() > 2 ? name.substring(0, 2) : name;
        }
        String k = b[0] < 0 ? "M" + (b[0] - ArmorKeyBindsSC.MOUSE_BASE + 1) : org.lwjgl.input.Keyboard.getKeyName(b[0]);
        if (k == null) {
            k = "?";
        }
        if (k.length() > 3) {
            k = k.substring(0, 3);
        }
        return (b[1] != 0 ? "^" : "") + k;
    }

    /**
     * One tool function's icon, or null while hidden (the same states and look as the armour's). `f` the key's
     * function (null: none - «Последний шанс»), `wanted` its missing gas is worth showing (on with a key / a mode on).
     */
    private static Icon toolIcon(EntityPlayer p, ItemStack tool, Enum<?> f, String name, boolean has, boolean wanted, int baseCd,
            Gas gas, int gasMb, String key) {
        if (baseCd <= 0) {
            return null;
        }
        long now = p.worldObj.getTotalWorldTime();
        long end = ToolLevelSC.cooldownEnd(tool, key);
        boolean gasShort = gas != null && !ToolGasSC.has(p, gas, gasMb);
        int st = SingularHud.toolState(has, end, now, gasShort, wanted);
        if (st == SingularHud.HIDDEN) {
            return null;
        }
        Icon ic = new Icon();
        ic.code = keyCode(f, name);
        ic.name = name;
        if (st == SingularHud.COOLING) {
            ic.text = SingularHud.time(SingularCooldowns.left(end, now), Lang.tr("sc.singhud.sec"));
            ic.sweep = SingularHud.sweep(end, now, ToolLevelSC.cooldownTicks(p, baseCd));
            ic.codeColor = DIM_PURPLE;
        } else if (st == SingularHud.READY) {
            ic.text = Lang.tr("sc.singhud.ready");
            ic.textColor = GREEN;
            ic.edge = 0xFF000000 | PURPLE;
        } else {
            ic.text = Lang.tr("sc.singhud.nogas", GasUiSC.shortName(gas));
            ic.textColor = RED;
            ic.codeColor = DIM_PURPLE;
            ic.edge = 0xFF803030;
        }
        return ic;
    }

    /** A key function switched on with a key bound: its missing gas is worth an icon. */
    private static boolean keyed(boolean on, Enum<?> f) {
        return on && ArmorKeyBindsSC.get(f) != null;
    }

    /**
     * The icons of the Singular blade / drill in hand: its key functions with a cooldown. The form attack goes by
     * the form that counts (its own name, cooldown and gas; the sword's wave has none), the Guardian's «Последний
     * шанс» (level 5) by its own cooldown; the drill's black hole (a mode, not a key) while its zone has one (12x12).
     */
    static List<Icon> toolIcons(EntityPlayer p) {
        List<Icon> out = new ArrayList<Icon>();
        ItemStack held = p.getCurrentEquippedItem();
        if (ToolLevelSC.isBlade(held)) {
            com.sc.util.BladeForm form = com.sc.item.BladeSingularSC.effectiveForm(p, held);
            for (BladeFeature f : BladeFeature.values()) {
                if (!f.isAction() || !f.availableIn(BladeType.SINGULAR)) {
                    continue;
                }
                boolean fa = f == BladeFeature.FORM_ATTACK;
                Icon ic = toolIcon(p, held, f, Lang.tr(fa ? BladeFeature.formAttackKey(form) : "sc.bladefn." + f.key()),
                        BladeLogicSC.unlocked(p, held, f), keyed(ItemBladeSC.isEnabled(held, f), f),
                        fa ? BladeFeature.formAttackCooldown(form) : f.cooldownTicks(), fa ? BladeFeature.formAttackGas(form) : f.gas(),
                        fa ? BladeFeature.formAttackGasMb(form) : f.gasMb(), f.key());
                if (ic != null) {
                    out.add(ic);
                }
            }
            if (ToolLevelSC.hasBranch(p, held, ToolLevelSC.BLADE_GUARDIAN, ToolLevelSC.BRANCH_PERK_LEVEL)) {
                Icon ic = toolIcon(p, held, null, Lang.tr("sc.toolhud.lastchance"), true, true, BladeFeature.LAST_CHANCE_COOLDOWN,
                        Gas.SINGULAR_MATTER, BladeFeature.LAST_CHANCE_SM, BladeFeature.LAST_CHANCE_KEY);
                if (ic != null) {
                    out.add(ic);
                }
            }
        } else if (ToolLevelSC.isDrill(held)) {
            for (DrillFeature f : DrillFeature.values()) {
                if (!f.isAction() || !f.availableIn(DrillType.SINGULAR)) {
                    continue;
                }
                Icon ic = toolIcon(p, held, f, Lang.tr("sc.drillfn." + f.key()), DrillLogicSC.unlocked(p, held, f),
                        keyed(ItemDrillSC.isEnabled(held, f), f), f.cooldownTicks(), f.gas(), f.gasMb(), f.key());
                if (ic != null) {
                    out.add(ic);
                }
            }
            DrillFeature hole = DrillFeature.BLACK_HOLE;
            int size = DrillLogicSC.holeSize(p, held);
            int cd = size > 0 ? DrillLogicSC.holeCooldown(p, held, size) : 0;
            // a cooldown left from a 12x12 dig stays shown even after the mode changed
            if (cd <= 0 && ToolLevelSC.cooldownEnd(held, hole.key()) > p.worldObj.getTotalWorldTime()) {
                cd = hole.cooldownTicks();
            }
            Icon ic = toolIcon(p, held, hole, Lang.tr("sc.drillfn." + hole.key()), DrillLogicSC.unlocked(p, held, hole), size > 0,
                    cd, hole.gas(), hole.gasMb(), hole.key());
            if (ic != null) {
                out.add(ic);
            }
        }
        return out;
    }

    /**
     * The black hole as it works now: the size the drill's level opens (a stored 12 on a level-3 drill digs 9x9)
     * and the depth; «Обычный режим» while off. Client-safe (NBT + creative).
     */
    public static String holeLine(EntityPlayer p, ItemStack drill) {
        int size = DrillLogicSC.holeSize(p, drill);
        if (size <= 0) {
            return Lang.tr("sc.toolgui.hole.off");
        }
        int depth = ItemDrillSC.tunnelDepth(drill);
        return depth > 1 ? Lang.tr("sc.toolgui.hole.tunnel", size, size, depth) : Lang.tr("sc.toolgui.hole.size", size, size);
    }

    /** Above the hearts / food rows: the Singular blade's form. @return the height it took (0: none) */
    private static int drawForm(FontRenderer fr, EntityPlayer p, int w, int h) {
        ItemStack held = p.getCurrentEquippedItem();
        if (!ToolLevelSC.isBlade(held)) {
            return 0;
        }
        int rows = Math.max(net.minecraftforge.client.GuiIngameForge.left_height, net.minecraftforge.client.GuiIngameForge.right_height);
        com.sc.util.BladeForm form = com.sc.item.BladeSingularSC.effectiveForm(p, held);
        String s = Lang.tr("sc.toolhud.form", Lang.tr(form.langKey()), Lang.tr(BladeFeature.formAttackKey(form)));
        fr.drawStringWithShadow(s, (w - fr.getStringWidth(s)) / 2, h - rows - 11, PURPLE);
        return 11;
    }

    /** Top right (top left while the icons are on the right): the Singular drill's mode, the crumb, SM in the armour, heat. */
    private static void drawDrillPanel(FontRenderer fr, EntityPlayer p, int w, int pos) {
        ItemStack held = p.getCurrentEquippedItem();
        if (!ToolLevelSC.isDrill(held)) {
            return;
        }
        List<String> lines = new ArrayList<String>();
        List<Integer> colors = new ArrayList<Integer>();
        lines.add(Lang.tr("sc.toolhud.drill.head"));
        colors.add(0xFFFFFF);
        lines.add(holeLine(p, held));
        colors.add(PURPLE);
        int per = com.sc.item.ItemSingularCrumbSC.CRUMB_BLOCKS;
        int prog = Math.max(0, Math.min(per, ItemDrillSC.crumbProgress(held)));
        lines.add(Lang.tr("sc.toolhud.drill.crumb", prog, per));
        colors.add(0xE0E0E0);
        int sm = ArmorGasSC.suitAmount(p, Gas.SINGULAR_MATTER);
        lines.add(Lang.tr("sc.toolhud.drill.sm", sm));
        colors.add(sm > 0 ? 0xE0E0E0 : RED);
        int heat = ItemDrillSC.heatPercent(held);
        boolean hot = ItemDrillSC.overheated(held);
        lines.add(Lang.tr(hot ? "sc.drillhud.overheat" : "sc.toolhud.drill.heat", heat));
        colors.add(hot || heat > 80 ? RED : heat > 0 ? ORANGE : 0xA0A0A0);
        int tw = 0;
        for (String s : lines) {
            tw = Math.max(tw, fr.getStringWidth(s));
        }
        int pw = tw + 10, ph = DRILL_PANEL_H;                // 4 + 13 + (lines - 1) * 10 + 3 + 2, five lines
        int x = pos == ArmorKeyBindsSC.HUD_RIGHT ? 4 : w - pw - 4, y = 4;
        Gui.drawRect(x, y, x + pw, y + ph, 0xFF000000 | PURPLE);
        Gui.drawRect(x + 1, y + 1, x + pw - 1, y + ph - 1, 0xD8140F1E);
        int ly = y + 4;
        for (int i = 0; i < lines.size(); i++) {
            fr.drawStringWithShadow(lines.get(i), x + (pw - fr.getStringWidth(lines.get(i))) / 2, ly, colors.get(i));
            ly += i == 0 ? 13 : 10;                       // a gap under the heading
            if (i == 2) {                                 // the crumb's bar under its line
                int share = Math.round((pw - 12) * SingularHud.crumbShare(prog, per));
                Gui.drawRect(x + 6, ly - 1, x + pw - 6, ly, 0xFF3A2A4A);
                if (share > 0) {
                    Gui.drawRect(x + 6, ly - 1, x + 6 + share, ly, 0xFF000000 | PURPLE);
                }
                ly += 2;
            }
        }
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || mc.thePlayer == null || mc.theWorld == null || mc.gameSettings.showDebugInfo
                || (mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.GuiChat))) {
            return;
        }
        int pos = ArmorKeyBindsSC.hudPos();
        int w = event.resolution.getScaledWidth(), h = event.resolution.getScaledHeight();
        FontRenderer fr = mc.fontRenderer;
        int formH = drawForm(fr, mc.thePlayer, w, h);          // the tool's own lines: whatever the icons' place
        drawDrillPanel(fr, mc.thePlayer, w, pos);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        if (pos == ArmorKeyBindsSC.HUD_OFF) {
            return;
        }
        List<Icon> icons = SingularLevel.wearsSingular(mc.thePlayer) ? icons(mc.thePlayer) : new ArrayList<Icon>();
        icons.addAll(toolIcons(mc.thePlayer));               // next to the armour's, the same look
        if (icons.isEmpty()) {
            return;
        }
        int n = icons.size();
        boolean row = pos == ArmorKeyBindsSC.HUD_HOTBAR || pos == ArmorKeyBindsSC.HUD_TOP;
        int x, y;
        if (row) {
            int total = n * BOX + (n - 1) * GAP;
            x = (w - total) / 2;
            if (pos == ArmorKeyBindsSC.HUD_TOP) {
                y = 4 + (net.minecraft.entity.boss.BossStatus.bossName != null && net.minecraft.entity.boss.BossStatus.statusBarTime > 0 ? 18 : 0)
                        + (com.sc.bridge.BridgeHudDataSC.shown() ? BRIDGE_TOP_H : 0);   // under the bridge's portal line
            } else {                                        // over the hearts / armour / food rows, whatever they take
                int rows = Math.max(net.minecraftforge.client.GuiIngameForge.left_height, net.minecraftforge.client.GuiIngameForge.right_height);
                y = h - rows - 4 - 9 - BOX - formH;
            }
        } else {
            int total = n * (BOX + 3) - 3;
            x = pos == ArmorKeyBindsSC.HUD_RIGHT ? w - BOX - 4 : 4;
            y = Math.max(4, (h - total) / 2);
        }
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
