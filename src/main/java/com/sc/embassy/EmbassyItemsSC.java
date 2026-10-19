package com.sc.embassy;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Подложка Ф6 (docs/plan-substrate-tech-f6.md §2.2): the embassies' item, registered from ModItems.init (preInit, after
 * FinaleItemsSC). Registry names are what worlds keep - fixed here: "embassyFinder" - ItemEmbassyFinderSC (package T): «Пеленгатор
 * посольства», the direction and distance to the nearest embassy in the Overworld (EmbassySiteSC.nearest). Its TooltipSC tooltip and an
 * NEI «how to get» page (the recipe - EmbassyRecipesSC; package D). Shared core: only the coordinator edits this file.
 */
public final class EmbassyItemsSC {

    public static Item finder;

    private EmbassyItemsSC() {
    }

    public static void init() {
        finder = new ItemEmbassyFinderSC();
        GameRegistry.registerItem(finder, "embassyFinder");
    }

    /** `n` Embassy Direction Finders (null before init). */
    public static ItemStack finder(int n) {
        return finder == null ? null : new ItemStack(finder, n, 0);
    }
}
