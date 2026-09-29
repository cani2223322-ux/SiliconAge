package com.sc.item;

import java.util.List;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.manual.Lang;
import com.sc.radiation.LeadSuitSC;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.oredict.OreDictionary;

/**
 * The lead suit: helmet with a lead-glass window, jacket, trousers and boots. Only against
 * radiation (25% a piece, the full suit stops it all); against blows about as good as leather;
 * and heavy (LeadSuitSC). Mended with lead ingots on an anvil.
 */
public class ItemLeadSuitSC extends ItemArmor {

    public static final ArmorMaterial LEAD = EnumHelper.addArmorMaterial("LEAD_SC", 12, new int[]{1, 3, 2, 1}, 5);
    private static final String[] NAMES = {"leadHelmet", "leadChestplate", "leadLeggings", "leadBoots"};

    public ItemLeadSuitSC(int armorType) {
        super(LEAD, 0, armorType);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + "." + NAMES[armorType]);
        setTextureName(Reference.ASSETS + ":" + NAMES[armorType]);
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, int slot, String type) {
        return Reference.ASSETS + ":textures/models/armor/lead_layer_" + (armorType == 2 ? 2 : 1) + ".png";
    }

    @Override
    public boolean getIsRepairable(ItemStack armor, ItemStack material) {
        for (ItemStack lead : OreDictionary.getOres("ingotLead")) {
            if (OreDictionary.itemMatches(lead, material, false)) {
                return true;
            }
        }
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add("§a" + Lang.tr("sc.leadsuit.tooltip.prot", 25));
        list.add("§c" + Lang.tr("sc.leadsuit.tooltip.weight", (int) Math.round(LeadSuitSC.SPEED_PER_PART * 100)));
        com.sc.util.TooltipSC.more(list, Lang.tr("sc.leadsuit.tooltip.full"), null);
    }
}
