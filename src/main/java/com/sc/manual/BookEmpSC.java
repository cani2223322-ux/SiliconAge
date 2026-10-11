package com.sc.manual;

import java.util.List;
import java.util.Locale;

import com.sc.emp.EmpMathSC;
import com.sc.emp.EmpMode;
import com.sc.emp.EmpModule;
import com.sc.emp.EmpTooltipSC;
import com.sc.emp.ItemEmpGunSC;
import com.sc.init.ModItems;
import com.sc.machine.MachineRecipe;
import com.sc.machine.MachineType;
import com.sc.machine.RecipeRegistry;
import com.sc.util.ArmorSuit;
import com.sc.util.ChipType;
import com.sc.util.ConfigSC;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.oredict.OreDictionary;

/**
 * The handbook's ПЭ-1 articles (docs/plan-pe1.md §10) in the "Броня и оружие" chapter, right after the weapons
 * article: «ПЭ-1» (modes, the suppression table §2, heat and helium §3, the siphon §4, the gun and its modules §5,
 * ПЭ-1М, the recipes; G on the gun or a module opens it) and «Защита от ЭМИ» (§7). Every number comes from
 * EmpMathSC / ConfigSC, never from the text.
 */
final class BookEmpSC {

    private BookEmpSC() {
    }

    static void add(List<BookEntry> list, BookChapter c) {
        list.add(gun(c));
        list.add(defense(c));
    }

    private static BookEntry gun(BookChapter c) {
        ItemStack gun = new ItemStack(ModItems.empGun);
        BookEntry e = new BookEntry("emp", c, gun, Lang.tr("sc.manual.emp.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.emp.title")))
                .addAll(BookContent.paras("sc.manual.emp.intro", null, new Object[]{EmpMathSC.NO_ARMOR_SLOW_TICKS / 20}));

        // modes (§1)
        e.add(BookEl.head(Lang.tr("sc.manual.emp.modeshead")));
        for (EmpMode m : EmpMode.values()) {
            String sub;
            if (m == EmpMode.POINT) {
                sub = Lang.tr("sc.manual.emp.modeline.point", BookContent.num(EmpMathSC.EU_POINT), sec(EmpMathSC.CHARGE_POINT),
                        EmpMathSC.range(m, false), EmpMathSC.range(m, true));
            } else if (m == EmpMode.PULSE) {
                sub = Lang.tr("sc.manual.emp.modeline.pulse", BookContent.num(EmpMathSC.EU_PULSE), sec(EmpMathSC.CHARGE_PULSE),
                        sec(EmpMathSC.CHARGE_PULSE_M), EmpMathSC.PULSE_COOLDOWN / 20, EmpMathSC.range(m, false));
            } else {
                sub = Lang.tr("sc.manual.emp.modeline.siphon", BookContent.num(EmpMathSC.EU_SIPHON_START), EmpMathSC.range(m, false),
                        EmpMathSC.range(m, true), EmpTooltipSC.siphonPct(ArmorSuit.SINGULAR, false), EmpTooltipSC.siphonPct(ArmorSuit.NANO, false),
                        EmpTooltipSC.decimal(EmpMathSC.WIDE_MUL), EmpMathSC.siphonLossPct(false, false, ConfigSC.empSiphonLoss));
            }
            e.add(BookEl.item(null, Lang.tr("sc.emp.mode." + m.key()), Lang.tr("sc.emp.mode." + m.key() + ".desc") + " " + sub));
        }
        e.addAll(BookContent.paras("sc.manual.emp.modes", null, null, new Object[]{EmpMathSC.SOFT_DESCENT_TICKS / 20}));

        // the suppression table (§2): the server's multiplier included, no chip, no helium
        e.add(BookEl.head(Lang.tr("sc.manual.emp.timehead")));
        BookEl t = BookEl.table(Lang.tr("sc.manual.emp.t.suit"), Lang.tr("sc.manual.emp.t.point"), Lang.tr("sc.manual.emp.t.pointm"),
                Lang.tr("sc.manual.emp.t.pulse"));
        for (ArmorSuit suit : ArmorSuit.values()) {
            t.row(new ItemStack(ModItems.ARMOR.get(suit)[1]), Lang.tr("sc.suit." + suit.name().toLowerCase(Locale.ROOT)),
                    sec(duration(suit, EmpMode.POINT, false)), sec(duration(suit, EmpMode.POINT, true)), sec(duration(suit, EmpMode.PULSE, false)));
        }
        e.add(t);
        int[] chip = EmpMathSC.DEFENSE_DURATION_PCT;
        e.addAll(BookContent.paras("sc.manual.emp.time", new Object[]{ConfigSC.empImmunitySeconds, chip[0], chip[1], chip[2],
                pct(1F - EmpMathSC.HELIUM_DURATION_MUL)}, new Object[]{multiplier(ConfigSC.empDurationMultiplier)}));

        // heat and helium (§3)
        e.add(BookEl.head(Lang.tr("sc.manual.emp.heathead")));
        BookEl h = BookEl.table(Lang.tr("sc.manual.emp.t.mode"), Lang.tr("sc.manual.emp.t.heat"), Lang.tr("sc.manual.emp.t.boil"),
                Lang.tr("sc.manual.emp.t.total"));
        h.row(null, Lang.tr("sc.emp.mode.point"), Lang.tr("sc.manual.emp.cell.heat", EmpMathSC.HEAT_PCT_POINT),
                Lang.tr("sc.manual.emp.cell.boil", EmpMathSC.BOIL_MUL_POINT, EmpMathSC.BOIL_TICKS_POINT / 20),
                Lang.tr("sc.manual.emp.cell.total", Math.round(EmpMathSC.BOIL_LOOP_PCT_POINT)));
        h.row(null, Lang.tr("sc.emp.mode.pulse"), Lang.tr("sc.manual.emp.cell.heat", EmpMathSC.HEAT_PCT_PULSE),
                Lang.tr("sc.manual.emp.cell.boil", EmpMathSC.BOIL_MUL_PULSE, EmpMathSC.BOIL_TICKS_PULSE / 20),
                Lang.tr("sc.manual.emp.cell.total", Math.round(EmpMathSC.BOIL_LOOP_PCT_PULSE)));
        h.row(null, Lang.tr("sc.emp.mode.siphon"), Lang.tr("sc.manual.emp.cell.heatsec", EmpMathSC.HEAT_PCT_SIPHON),
                Lang.tr("sc.manual.emp.cell.boilstream", EmpMathSC.BOIL_MUL_SIPHON, EmpMathSC.BOIL_TICKS_SIPHON_AFTER / 20),
                Lang.tr("sc.manual.emp.cell.totalsec", Math.round(EmpMathSC.BOIL_LOOP_PCT_STREAM_PER_SECOND)));
        e.add(h);
        int[] chipHeat = EmpMathSC.DEFENSE_HEAT_PCT;
        e.addAll(BookContent.paras("sc.manual.emp.heat", null, new Object[]{EmpMathSC.DIMINISH_WINDOW / 20},
                new Object[]{chipHeat[0], chipHeat[1], chipHeat[2]}));

        // the siphon (§4)
        e.add(BookEl.head(Lang.tr("sc.manual.emp.siphonhead")));
        int[] chipSiphon = EmpMathSC.DEFENSE_SIPHON_PCT;
        e.addAll(BookContent.paras("sc.manual.emp.siphon", null, new Object[]{EmpTooltipSC.siphonRates(false), EmpTooltipSC.decimal(EmpMathSC.WIDE_MUL),
                chipSiphon[0], chipSiphon[1], chipSiphon[2], EmpMathSC.siphonLossPct(false, false, ConfigSC.empSiphonLoss),
                EmpMathSC.siphonLossPct(true, false, ConfigSC.empSiphonLoss)}, null, new Object[]{EmpMathSC.siphonOverheatSeconds(false),
                EmpMathSC.siphonOverheatSeconds(true), EmpMathSC.SIPHON_THROTTLED_PCT, EmpTooltipSC.decimal(EmpMathSC.GUN_HEAT_THROTTLED_COOL_TENTHS / 10D),
                EmpMathSC.GUN_HEAT_RECOVER}));

        // the gun and its modules (§5)
        e.add(BookEl.head(Lang.tr("sc.manual.emp.gunhead")));
        e.addAll(BookContent.paras("sc.manual.emp.gun", new Object[]{BookContent.num(EmpMathSC.BUFFER), BookContent.num(EmpMathSC.BUFFER_CAPACITY),
                EmpMathSC.GUN_HEAT_POINT, EmpMathSC.GUN_HEAT_PULSE, EmpMathSC.GUN_HEAT_MAX, EmpMathSC.siphonOverheatSeconds(false),
                EmpMathSC.GUN_HEAT_RECOVER, EmpMathSC.coolFullSeconds(EmpMathSC.GUN_HEAT_MAX)}, new Object[]{EmpMathSC.CHARGE_TIER.name()}));
        for (EmpModule m : EmpModule.values()) {
            ItemStack s = ModItems.empModule.stackOf(m);
            e.add(BookEl.item(s, s.getDisplayName(), Lang.tr("sc.emp.module." + m.key() + ".desc")));
        }

        // ПЭ-1М
        ItemStack gunM = new ItemStack(ModItems.empGun, 1, ItemEmpGunSC.META_UPGRADED);
        e.add(BookEl.head(Lang.tr("sc.manual.emp.upgradehead"), gunM));
        e.addAll(BookContent.paras("sc.manual.emp.upgrade", new Object[]{sec(EmpMathSC.CHARGE_PULSE_M), sec(EmpMathSC.CHARGE_PULSE),
                pct(EmpMathSC.UPGRADED_POINT_MUL - 1F)}));

        // server rules, recipes, the way to the protections
        e.addAll(BookContent.paras("sc.manual.emp.rules"));
        recipes(e, gun, gunM);
        e.add(BookEl.link("empdefense", Lang.tr("sc.manual.emp.defense.title")));
        e.about(new ItemStack(ModItems.empGun, 1, OreDictionary.WILDCARD_VALUE), new ItemStack(ModItems.empModule, 1, OreDictionary.WILDCARD_VALUE));
        return e;
    }

    /** The crafting of the gun and the modules under one heading, then ПЭ-1М's Upgrade Station recipe. */
    private static void recipes(BookEntry e, ItemStack gun, ItemStack gunM) {
        List<IRecipe> found = new java.util.ArrayList<IRecipe>(BookContent.craftingFor(gun));
        for (EmpModule m : EmpModule.values()) {
            found.addAll(BookContent.craftingFor(ModItems.empModule.stackOf(m)));
        }
        if (!found.isEmpty()) {
            e.add(BookEl.head(Lang.tr("sc.book.craft")));
            for (IRecipe r : found) {
                e.add(BookEl.craft(r));
            }
        }
        for (MachineRecipe r : RecipeRegistry.recipesFor(MachineType.UPGRADE_STATION_HV)) {
            for (ItemStack out : r.outputs) {
                if (out != null && out.getItem() == gunM.getItem() && out.getItemDamage() == gunM.getItemDamage()) {
                    e.add(BookEl.machine(r));
                    return;
                }
            }
        }
    }

    private static BookEntry defense(BookChapter c) {
        ItemStack chip = ModItems.armorChip.stackOf(ChipType.DEFENSE, 1);
        BookEntry e = new BookEntry("empdefense", c, chip, Lang.tr("sc.manual.emp.defense.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.emp.defense.title"))).addAll(BookContent.paras("sc.manual.emp.defense.intro"));
        int[] dur = EmpMathSC.DEFENSE_DURATION_PCT, sip = EmpMathSC.DEFENSE_SIPHON_PCT, heat = EmpMathSC.DEFENSE_HEAT_PCT;
        e.add(BookEl.item(chip, Lang.tr("sc.manual.emp.def.chipname"), Lang.tr("sc.manual.emp.def.chip", dur[0], dur[1], dur[2],
                sip[0], sip[1], sip[2], heat[0], heat[1], heat[2])));
        e.add(BookEl.item(null, Lang.tr("sc.manual.emp.def.rebootname"), Lang.tr("sc.manual.emp.def.reboot", EmpMathSC.REBOOT_ARGON_MB,
                BookContent.mb(), pct(EmpMathSC.REBOOT_CHARGE_SHARE), EmpMathSC.REBOOT_COOLDOWN / 20)));
        e.add(BookEl.item(null, Lang.tr("sc.manual.emp.def.heliumname"), Lang.tr("sc.manual.emp.def.helium", pct(1F - EmpMathSC.HELIUM_DURATION_MUL))));
        e.add(BookEl.item(null, Lang.tr("sc.manual.emp.def.screenname"), Lang.tr("sc.manual.emp.def.screen", BookContent.num(EmpMathSC.SCREEN_EU_POINT),
                BookContent.num(EmpMathSC.SCREEN_EU_PULSE), BookContent.num(EmpMathSC.SCREEN_EU_SIPHON_PER_SECOND))));
        e.add(BookEl.item(null, Lang.tr("sc.manual.emp.def.guardname"), Lang.tr("sc.manual.emp.def.guard", EmpMathSC.GUARD_REFLECT_PCT)));
        e.add(BookEl.item(null, Lang.tr("sc.manual.emp.def.covername"), Lang.tr("sc.manual.emp.def.cover", EmpMathSC.PULSE_HUM_RANGE)));
        e.addAll(BookContent.paras("sc.manual.emp.defense"));
        e.add(BookEl.link("emp", Lang.tr("sc.manual.emp.title")));
        return e;
    }

    // ------------------------------------------------------------------

    private static int duration(ArmorSuit suit, EmpMode m, boolean upgraded) {
        return EmpMathSC.durationTicks(suit, m, upgraded, 0, false, ConfigSC.empDurationMultiplier);
    }

    /** Ticks as seconds: "16", "8.8" ("8,8" in Russian). */
    private static String sec(int ticks) {
        if (ticks % 20 == 0) {
            return String.valueOf(ticks / 20);
        }
        String s = String.format(Locale.ROOT, "%.1f", ticks / 20F);
        return "ru".equals(Lang.trOr("sc.book.lang", "en")) ? s.replace('.', ',') : s;
    }

    /** A share as whole percent: 0.2 -> 20. */
    private static int pct(float share) {
        return Math.round(share * 100F);
    }

    /** "1", "1.5" ("1,5" in Russian). */
    private static String multiplier(float m) {
        return sec(Math.round(m * 20F));
    }
}
