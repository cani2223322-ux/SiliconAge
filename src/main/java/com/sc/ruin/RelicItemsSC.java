package com.sc.ruin;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Подложка Ф4 (docs/plan-substrate-tech-f4.md §2.2, §8.7): the camps' and the Vaults' items, registered from ModItems.init
 * (preInit, after SettlementItemsSC). Registry names are what worlds keep - fixed here: "relicSC" (ItemRelicSC: the camp's key,
 * the Fragment of the Breakdown Key, the Sealed Crumb - metas there), "ruinFinder" (ItemRuinFinderSC, the Cartographer's
 * «Пеленгатор руин»). The new combat loot (Boiled Loop .. Natural Wafer) is ItemCombatLootSC's metas 7..11. Each has its TooltipSC
 * tooltip and an NEI «how to get» page (package D). Shared core: only the coordinator edits this file.
 */
public final class RelicItemsSC {

    public static Item relic, ruinFinder;

    private RelicItemsSC() {
    }

    public static void init() {
        relic = new ItemRelicSC();
        GameRegistry.registerItem(relic, "relicSC");
        ruinFinder = new ItemRuinFinderSC();
        GameRegistry.registerItem(ruinFinder, "ruinFinder");
    }

    /** `n` relics of meta `meta` (null before init). */
    public static ItemStack relic(int meta, int n) {
        return relic == null ? null : new ItemStack(relic, n, meta);
    }
}
