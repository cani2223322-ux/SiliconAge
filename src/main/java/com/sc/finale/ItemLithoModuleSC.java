package com.sc.finale;

import java.util.List;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.manual.Lang;
import com.sc.util.TooltipSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * PACKAGE P (docs/plan-substrate-tech-f5.md §9.4, rule ПО-20; proposal §7 «Старейшина | Матрица Пробоя (с босса) + 48 КЧ | Модуль
 * Литографов: −50% содержания своего Генератора поля | Свой»): «Модуль Литографов» - goes into an upgrade slot of the player's own
 * Field Generator (TileEntityFieldGeneratorSC.isFieldUpgrade - the coordinator's line), the upkeep x FinaleRulesSC.UPKEEP_SHARE
 * (upkeepPerTick, the rain shield's extra with it); one is enough. Never crafted, only the Elder's (ПО-7: only sold). Stack 1, epic.
 * <p>
 * Fixed (the coordinator's): the class, the registry name "lithoModule", the stack, the slot and the upkeep. P's: the icon
 * (textures/items/lithoModule.png), the tooltip (sc.tooltip.lithoModule + .details with the share + .howto), the field screen's
 * upkeep line (GuiFieldGeneratorSC shows the halved upkeep with it - upkeepFor alone doesn't know the module).
 */
public class ItemLithoModuleSC extends Item {

    public ItemLithoModuleSC() {
        setMaxStackSize(1);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".lithoModule");
        setTextureName(Reference.ASSETS + ":lithoModule");
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.epic;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        TooltipSC.wrap(list, Lang.tr("sc.tooltip.lithoModule"), "§7");
        TooltipSC.more(list, Lang.tr("sc.tooltip.lithoModule.details", Math.round((1F - FinaleRulesSC.UPKEEP_SHARE) * 100)),
                Lang.tr("sc.tooltip.lithoModule.howto"));
    }
}
