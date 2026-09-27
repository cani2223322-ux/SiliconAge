package com.sc.block;

import java.util.List;

import com.sc.energy.Tier;
import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityEnergyStorageSC;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/** Energy storage item: one variant per tier, carrying its stored charge in NBT ("EnergySC"). */
public class ItemBlockEnergyStorageSC extends ItemBlock {

    public ItemBlockEnergyStorageSC(Block block) {
        super(block);
        setHasSubtypes(true);
    }

    @Override
    public int getMetadata(int damage) {
        return damage;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "." + BlockEnergyStorageSC.tierFor(stack.getItemDamage()).name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        Tier tier = BlockEnergyStorageSC.tierFor(stack.getItemDamage());
        int stored = stack.hasTagCompound() ? stack.getTagCompound().getInteger("EnergySC") : 0;
        list.add(Lang.tr("sc.storage.tooltip.charge", String.valueOf(stored), String.valueOf(TileEntityEnergyStorageSC.capacityOf(tier))));
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey("UpgradesSC")) {
            list.add(Lang.tr("sc.storage.tooltip.upgrades"));
        }
        if (com.sc.util.TooltipSC.shift()) {
            com.sc.util.TooltipSC.wrap(list, Lang.tr("sc.storage.tooltip.io", tier.getVoltage()), "\u00a77");
            if (field_150939_a instanceof BlockChargePadSC) {
                com.sc.util.TooltipSC.wrap(list, Lang.tr("sc.pad.tooltip", tier.name(), tier.getVoltage()), "\u00a77");
            }
        } else {
            com.sc.util.TooltipSC.hintShift(list);
        }
    }
}
