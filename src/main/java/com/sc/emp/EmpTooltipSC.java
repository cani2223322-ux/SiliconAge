package com.sc.emp;

import java.util.List;
import java.util.Locale;

import com.sc.manual.Lang;
import com.sc.util.ArmorSuit;
import com.sc.util.ConfigSC;
import com.sc.util.TooltipSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * The TooltipSC tooltips of ПЭ-1 and its modules (docs/plan-pe1.md §10): the summary - charge, mode, heat, modules,
 * what it does; Shift - the modes with their prices / ranges and the suppression time by suit (EmpMathSC's table with
 * ПЭ-1М and the server's multiplier); Ctrl - controls, charging, the recipe, how a target protects itself. Every
 * number comes from EmpMathSC / ConfigSC. Client only (addInformation).
 */
public final class EmpTooltipSC {

    private EmpTooltipSC() {
    }

    public static void gun(ItemStack stack, EntityPlayer player, List list) {
        boolean upgraded = ItemEmpGunSC.upgraded(stack);
        list.add(Lang.tr("sc.tooltip.weapon.charge", ItemEmpGunSC.chargeOf(stack), ItemEmpGunSC.capacityOf(stack)));
        String mode = Lang.tr("sc.emp.mode." + ItemEmpGunSC.modeOf(stack).key());
        int heat = ItemEmpGunSC.heatOf(stack) * 100 / EmpMathSC.GUN_HEAT_MAX;
        list.add("§7" + Lang.tr(ItemEmpGunSC.overheated(stack) ? "sc.tooltip.emp.statehot" : "sc.tooltip.emp.state", mode, heat));
        list.add("§7" + modules(stack));
        if (!ConfigSC.empEnabled) {
            list.add("§c" + Lang.tr("sc.emp.refuse.4"));
        }
        switch (TooltipSC.page()) {
            case 1:
                details(stack, list, upgraded);
                TooltipSC.hintCtrl(list);
                break;
            case 2:
                TooltipSC.wrap(list, Lang.tr("sc.tooltip.emp.howto"), "§7");
                TooltipSC.wrap(list, Lang.tr("sc.tooltip.emp.charge", EmpMathSC.CHARGE_TIER.name()), "§7");
                TooltipSC.wrap(list, Lang.tr("sc.tooltip.emp.recipe"), "§8");
                if (!upgraded) {
                    TooltipSC.wrap(list, Lang.tr("sc.tooltip.emp.upgrade"), "§d");
                }
                TooltipSC.wrap(list, Lang.tr("sc.tooltip.emp.defense"), "§7");
                break;
            default:
                TooltipSC.wrap(list, Lang.tr("sc.tooltip.emp.summary"), "§7");
                TooltipSC.hintShift(list);
        }
    }

    /** Shift: each mode with its numbers (with this gun's modules), then the suppression table. */
    private static void details(ItemStack stack, List list, boolean upgraded) {
        boolean focus = ItemEmpGunSC.hasModule(stack, EmpModule.FOCUS);
        boolean wide = ItemEmpGunSC.hasModule(stack, EmpModule.WIDE);
        boolean efficient = ItemEmpGunSC.hasModule(stack, EmpModule.EFFICIENT);
        list.add(Lang.tr("sc.tooltip.emp.modes"));
        for (EmpMode m : EmpMode.values()) {
            String name = Lang.tr("sc.emp.mode." + m.key());
            String line;
            if (m == EmpMode.POINT) {
                line = Lang.tr("sc.tooltip.emp.line.point", name, num(EmpMathSC.EU_POINT), seconds(EmpMathSC.chargeTicks(m, upgraded)),
                        EmpMathSC.range(m, focus));
            } else if (m == EmpMode.PULSE) {
                line = Lang.tr("sc.tooltip.emp.line.pulse", name, num(EmpMathSC.EU_PULSE), seconds(EmpMathSC.chargeTicks(m, upgraded)),
                        EmpMathSC.PULSE_COOLDOWN / 20, EmpMathSC.range(m, focus));
            } else {
                line = Lang.tr("sc.tooltip.emp.line.siphon", name, num(EmpMathSC.EU_SIPHON_START), siphonPct(ArmorSuit.SINGULAR, wide),
                        siphonPct(ArmorSuit.NANO, wide), EmpMathSC.siphonLossPct(efficient, false, ConfigSC.empSiphonLoss), EmpMathSC.range(m, focus));
            }
            String off = ItemEmpGunSC.modeEnabled(stack, m) ? "" : " " + Lang.tr("sc.tooltip.emp.off");
            TooltipSC.wrap(list, line + off, "§7");
        }
        list.add(Lang.tr("sc.tooltip.emp.table"));
        for (ArmorSuit suit : ArmorSuit.values()) {
            list.add("§7" + Lang.tr("sc.tooltip.emp.tablerow", Lang.tr("sc.suit." + suit.name().toLowerCase(Locale.ROOT)),
                    seconds(EmpMathSC.durationTicks(suit, EmpMode.POINT, upgraded, 0, false, ConfigSC.empDurationMultiplier)),
                    seconds(EmpMathSC.durationTicks(suit, EmpMode.PULSE, upgraded, 0, false, ConfigSC.empDurationMultiplier))));
        }
        TooltipSC.wrap(list, Lang.tr("sc.tooltip.emp.immunity", ConfigSC.empImmunitySeconds), "§8");
        if (upgraded) {
            TooltipSC.wrap(list, Lang.tr("sc.tooltip.emp.upgraded", seconds(EmpMathSC.CHARGE_PULSE_M),
                    Math.round((EmpMathSC.UPGRADED_POINT_MUL - 1F) * 100)), "§d");
        }
    }

    /** "Модули: Фокус, Охлаждение" or "Модулей нет". */
    private static String modules(ItemStack stack) {
        StringBuilder sb = new StringBuilder();
        for (EmpModule m : EmpModule.values()) {
            if (ItemEmpGunSC.hasModule(stack, m)) {
                sb.append(sb.length() > 0 ? ", " : "").append(Lang.tr("sc.tooltip.emp.mod." + m.key()));
            }
        }
        return sb.length() == 0 ? Lang.tr("sc.tooltip.emp.nomodules") : Lang.tr("sc.tooltip.emp.modules", sb.toString());
    }

    public static void module(ItemStack stack, EntityPlayer player, List list) {
        EmpModule m = ItemEmpModuleSC.moduleOf(stack);
        if (m == null) {
            return;
        }
        TooltipSC.wrap(list, Lang.tr("sc.emp.module." + m.key() + ".desc"), "§7");
        String nums;
        switch (m) {
            case CAPACITY:
                nums = Lang.tr("sc.tooltip.emp.module.capacity", num(EmpMathSC.BUFFER), num(EmpMathSC.BUFFER_CAPACITY));
                break;
            case FOCUS:
                nums = Lang.tr("sc.tooltip.emp.module.focus", EmpMathSC.range(EmpMode.POINT, false), EmpMathSC.range(EmpMode.POINT, true),
                        EmpMathSC.range(EmpMode.SIPHON, false), EmpMathSC.range(EmpMode.SIPHON, true));
                break;
            case COOLING:
                nums = Lang.tr("sc.tooltip.emp.module.cooling", Math.round((1F - EmpMathSC.COOLING_MODULE_MUL) * 100));
                break;
            case EFFICIENT:
                nums = Lang.tr("sc.tooltip.emp.module.efficient", EmpMathSC.siphonLossPct(false, false, ConfigSC.empSiphonLoss),
                        EmpMathSC.siphonLossPct(true, false, ConfigSC.empSiphonLoss));
                break;
            default:
                nums = Lang.tr("sc.tooltip.emp.module.wide", decimal(EmpMathSC.WIDE_MUL), siphonRates(true));
        }
        list.add("§b" + nums);
        TooltipSC.more(list, null, Lang.tr("sc.tooltip.emp.module.howto"));
    }

    // ------------------------------------------------------------------

    /** A big number in the language's style: "50 000" in Russian, "50,000" in English (as the handbook). */
    static String num(long v) {
        String s = com.sc.bridge.BridgeMathSC.group(v);
        return "ru".equals(Lang.trOr("sc.book.lang", "en")) ? s : s.replace(' ', ',');
    }

    /** "6", "4.5" ("4,5" in Russian): a number with at most one decimal. */
    public static String decimal(double v) {
        double r = Math.round(v * 10) / 10D;
        if (r == Math.floor(r)) {
            return String.valueOf((long) r);
        }
        String s = String.format(Locale.ROOT, "%.1f", r);
        return "ru".equals(Lang.trOr("sc.book.lang", "en")) ? s.replace('.', ',') : s;
    }

    /** The siphon's speed against `suit`, % of its full charge a second (config included, no Defense chip), as decimal(). */
    public static String siphonPct(ArmorSuit suit, boolean wide) {
        return decimal(EmpMathSC.siphonPctPerSecond(suit, wide, 0, ConfigSC.empSiphonRateMultiplier));
    }

    /** «Нано 6% · Квант 5% · Экзо 4% · Сингулярная 3%» - the siphon's speed by the target's suit, a second. */
    public static String siphonRates(boolean wide) {
        StringBuilder sb = new StringBuilder();
        for (ArmorSuit suit : ArmorSuit.values()) {
            sb.append(sb.length() > 0 ? " • " : "").append(Lang.tr("sc.suit." + suit.name().toLowerCase(Locale.ROOT)))
                    .append(' ').append(siphonPct(suit, wide)).append('%');
        }
        return sb.toString();
    }

    /** Ticks as seconds: "16", "8.8" ("8,8" in Russian). */
    static String seconds(int ticks) {
        if (ticks % 20 == 0) {
            return String.valueOf(ticks / 20);
        }
        String s = String.format(Locale.ROOT, "%.1f", ticks / 20F);
        return "ru".equals(Lang.trOr("sc.book.lang", "en")) ? s.replace('.', ',') : s;
    }
}
