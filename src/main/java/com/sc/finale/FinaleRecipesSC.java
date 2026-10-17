package com.sc.finale;

import com.sc.init.ModItems;
import com.sc.item.ItemEntangledCrystalSC;
import com.sc.ruin.ItemRelicSC;
import com.sc.ruin.RelicItemsSC;
import com.sc.util.SiliconMaterial;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.ItemStack;

/**
 * Подложка Ф5 (docs/plan-substrate-tech-f5.md §2.3): the finale's two recipes, from SCMod.registerRecipes (and the self-test's path)
 * - only this mod's items (the rule of every phase):
 * <ul>
 * <li>the Breakdown Key (relic meta 3) - four Fragments of the Breakdown Key (relic meta 1, one a slot), shapeless (proposal §9
 * «Ключ Пробоя собирают из 4 Фрагментов»);</li>
 * <li>the «Навигационная запись Si-14» - a Half of an Entangled Crystal + a Memory Chip + a Controller, shapeless (proposal §4; the
 * half's pair NBT is ignored by the shapeless match - any half will do).</li>
 * </ul>
 * Everything else of Ф5 is never crafted (the Coordinator, the Module, the schemes - trades and drops; the journals - found).
 * Shared core: only the coordinator edits this file.
 */
public final class FinaleRecipesSC {

    private FinaleRecipesSC() {
    }

    public static void init() {
        ItemStack fragment = RelicItemsSC.relic(ItemRelicSC.KEY_FRAGMENT, 1), key = RelicItemsSC.relic(ItemRelicSC.BREAKDOWN_KEY, 1);
        if (fragment != null && key != null) {
            GameRegistry.addShapelessRecipe(key, fragment, fragment, fragment, fragment);
        }
        ItemStack record = FinaleItemsSC.navRecord(1);
        if (record != null && ModItems.entangledCrystal != null && ModItems.siliconMaterial != null) {
            GameRegistry.addShapelessRecipe(record, new ItemStack(ModItems.entangledCrystal, 1, ItemEntangledCrystalSC.HALF),
                    ModItems.siliconMaterial.stackOf(SiliconMaterial.MEMORY_CHIP), ModItems.siliconMaterial.stackOf(SiliconMaterial.CONTROLLER));
        }
    }
}
