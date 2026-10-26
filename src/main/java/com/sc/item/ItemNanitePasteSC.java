package com.sc.item;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * ОМ-2 (docs/plan-ideas-2026-10-26.md, «Добыча Подложки в домашних машинах», ★ A): «Нанитовая паста» - made of the nanite particles'
 * Nanite Dust in the Chemical Reactor; in a crafting grid with a worn tool of the mod (a photomask, a sputter target, a Diamond Wire /
 * Blade, a drill head - PasteRepairRecipeSC) it gives back part of its durability, never cheaper than a new tool. A plain component
 * (ModItems.COMPONENTS "nanitePaste", registered by IdeaItemsSC) with its own TooltipSC tooltip.
 * <p>
 * Fixed (the coordinator's): the class, the name / texture "nanitePaste". ОМ-2's: the tooltip, the texture, the recipes, NEI.
 */
public class ItemNanitePasteSC extends ItemSimpleSC {

    public static final String NAME = "nanitePaste";

    public ItemNanitePasteSC() {
        super(NAME, NAME);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        com.sc.util.TooltipSC.wrap(list, com.sc.manual.Lang.tr("sc.tooltip.nanitePaste"), "\u00a77");
        com.sc.util.TooltipSC.more(list, com.sc.manual.Lang.tr("sc.tooltip.nanitePaste.details",
                Math.round(com.sc.init.PasteRepairRecipeSC.REPAIR_SHARE * 100F), com.sc.init.PasteRepairRecipeSC.MAX_REPAIRS),
                com.sc.manual.Lang.tr("sc.tooltip.nanitePaste.howto"));
    }
}
