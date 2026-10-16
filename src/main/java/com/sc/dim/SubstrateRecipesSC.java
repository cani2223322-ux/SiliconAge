package com.sc.dim;

import com.sc.init.ModItems;
import com.sc.util.SiliconMaterial;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.ItemStack;

/**
 * PACKAGE W (docs/plan-substrate-tech.md §3.4): recipes of the Substrate's blocks - vanilla and this mod's items only
 * (Crystallite smelts to Silica Sand). Called from SCMod.registerRecipes (init, and the self-test's preInit) - once.
 * <p>
 * The ore dictionary names of the Substrate's ores go in here too: after ModBlocks.registerOreDict (preInit), so oreSC
 * stays the first stack of every ore<Name>.
 */
public final class SubstrateRecipesSC {

    /** Crystallite's smelting experience (as a stone's). */
    public static final float CRYSTALLITE_XP = 0.1F;

    private SubstrateRecipesSC() {
    }

    public static void init() {
        GameRegistry.addSmelting(new ItemStack(SubstrateBlocksSC.rock, 1, SubstrateBlocksSC.CRYSTALLITE),
                ModItems.siliconMaterial.stackOf(SiliconMaterial.SILICA_SAND), CRYSTALLITE_XP);
        SubstrateBlocksSC.registerOreDict();
    }
}
