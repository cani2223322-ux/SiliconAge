package com.sc.embassy;

import com.sc.init.ModItems;
import com.sc.util.SiliconMaterial;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/**
 * Подложка Ф6 (docs/plan-substrate-tech-f6.md §2.3, rule ПО-26): the embassies' one recipe, from SCMod.registerRecipes (and the
 * self-test's path) - vanilla's and this mod's items only: «Пеленгатор посольства» = a compass + 2 Transistors + a Silicon Wafer,
 * shapeless (proposal §8 «Как найти»). Shared core: only the coordinator edits this file.
 */
public final class EmbassyRecipesSC {

    private EmbassyRecipesSC() {
    }

    public static void init() {
        ItemStack finder = EmbassyItemsSC.finder(1);
        if (finder != null && ModItems.siliconMaterial != null) {
            ItemStack transistor = ModItems.siliconMaterial.stackOf(SiliconMaterial.TRANSISTOR);
            GameRegistry.addShapelessRecipe(finder, new ItemStack(Items.compass), transistor, transistor,
                    ModItems.siliconMaterial.stackOf(SiliconMaterial.SI_WAFER));
        }
    }
}
