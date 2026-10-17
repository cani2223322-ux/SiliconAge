package com.sc.mob.client;

import java.util.List;

import com.sc.manual.Lang;
import com.sc.mob.EliteModSC;
import com.sc.mob.EntityArmoredMobSC;
import com.sc.mob.EntityColossusSC;
import com.sc.mob.EntityScrapperSC;
import com.sc.mob.EntityStriderSC;
import com.sc.mob.IDisguisedSC;
import com.sc.mob.MobAnalysisSC;
import com.sc.util.ArmorSuit;
import com.sc.util.SingularSenseData.Analysis;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.Entity;

/**
 * PACKAGE A (docs/plan-substrate-tech-f3.md §7.2, rule ПИ-2): the analyzer's lines for a Substrate mob (SingularClientSC's
 * table, right of the crosshair) from the Ф3 flags MobAnalysisSC packed - «Броня: Квант, запас 60%», «Уязвим к ПЭ-1»,
 * «Аварийный режим», «Щит», «Элита: Командир», «ЭМИ-экран»; a disguised mimic's first line becomes «???». Client only.
 * The elite's modifier is read from the mob's DataWatcher (the flags only say «elite»); everything else from the flags.
 * Ф4 (docs/plan-substrate-tech-f4.md §7.7-§7.9, package N): what the creature shows of itself - the Colossus's facets
 * («стрелы отскакивают, лучи вдвое слабее»), the Arc Strider's collapsed arc, the Scrapper's bag («Мешок: 12 из 27»); the
 * reactive keys and «ПЭ-1 здесь бесполезен» come with the flags as before.
 */
@SideOnly(Side.CLIENT)
public final class MobAnalysisClientSC {

    private MobAnalysisClientSC() {
    }

    /** `lines` as SingularClientSC built them for `e` (null: not in the client's world); add / change by `flags`. */
    public static void lines(List<String> lines, Entity e, int flags) {
        if (e instanceof IDisguisedSC && ((IDisguisedSC) e).disguised()) {
            lines.clear();                              // ПИ-3: a machine that answers nothing - no name, no health, no weakness
            lines.add("§f???");
            lines.add("§8" + Lang.tr("sc.analyzer.mob.unknown"));
            return;
        }
        ArmorSuit suit = MobAnalysisSC.suitOf(flags);
        if (suit != null) {
            String name = Lang.tr("sc.analyzer.mob.suit." + suit.textureName);
            int pct = MobAnalysisSC.reserveOf(flags);
            lines.add("§b" + (pct < 0 ? Lang.tr("sc.analyzer.mob.armor.suit", name) : Lang.tr("sc.analyzer.mob.armor", name, pct)));
        }
        if ((flags & Analysis.WEAK_EMP) != 0) {
            lines.add("§e" + Lang.tr("sc.analyzer.mob.emp"));
        }
        if ((flags & Analysis.DOWN) != 0) {
            lines.add("§c" + Lang.tr("sc.analyzer.mob.down"));
        }
        if ((flags & Analysis.EMERGENCY) != 0) {
            lines.add("§6" + Lang.tr("sc.analyzer.mob.emergency"));
        }
        if ((flags & Analysis.SHIELD) != 0) {
            lines.add("§3" + Lang.tr("sc.analyzer.mob.shield"));
        }
        if ((flags & Analysis.SCREEN) != 0) {
            lines.add("§9" + Lang.tr("sc.analyzer.mob.screen"));
        }
        if ((flags & Analysis.ELITE) != 0) {
            EliteModSC m = e instanceof EntityArmoredMobSC ? ((EntityArmoredMobSC) e).eliteMod() : null;
            lines.add("§6" + (m != null ? Lang.tr("sc.analyzer.mob.elite", Lang.tr(m.langKey())) : Lang.tr("sc.analyzer.mob.elite.kind")));
        }
        if ((flags & Analysis.EMP_REACTS) != 0) {
            String k = MobAnalysisSC.reactiveKey(e);
            lines.add("§e" + Lang.tr(k != null ? "sc.analyzer.mob.reactive." + k : "sc.analyzer.mob.reactive"));
        }
        if ((flags & Analysis.EMP_USELESS) != 0) {
            lines.add("§8" + Lang.tr("sc.analyzer.mob.useless"));
        }
        noise(lines, e);
    }

    /** Ф4: what the «noise» shows of itself: the Colossus's facets, a collapsed arc (its telegraph), the Scrapper's bag (DW_BAG). */
    private static void noise(List<String> lines, Entity e) {
        if (e instanceof EntityColossusSC) {
            lines.add("§7" + Lang.tr("sc.analyzer.mob.facets"));
        } else if (e instanceof EntityStriderSC && ((EntityStriderSC) e).collapsed()) {
            lines.add("§b" + Lang.tr("sc.analyzer.mob.collapsed"));
        } else if (e instanceof EntityScrapperSC) {
            lines.add("§6" + Lang.tr("sc.analyzer.mob.bag", ((EntityScrapperSC) e).bagStacks(), EntityScrapperSC.BAG_SLOTS));
        }
    }
}
