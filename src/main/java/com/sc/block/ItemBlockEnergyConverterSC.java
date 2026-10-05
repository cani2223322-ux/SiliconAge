package com.sc.block;

import java.util.List;

import com.sc.energy.ForeignEnergySC;
import com.sc.manual.Lang;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** The Energy Converter's item: what it keeps (both buffers, the modules), what it does under Shift, how to set it up under Ctrl. */
public class ItemBlockEnergyConverterSC extends ItemBlock {

    public ItemBlockEnergyConverterSC(Block block) {
        super(block);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add("§7" + Lang.tr("sc.conv.tooltip.summary"));
        if (!ForeignEnergySC.anyPresent()) {
            list.add("§c" + Lang.tr("sc.conv.tooltip.noforeign"));
        }
        NBTTagCompound t = stack.getTagCompound();
        if (t != null) {
            int eu = t.getInteger("EnergySC");
            double x = t.getDouble("ForeignSC");
            ForeignEnergySC.Kind k = t.hasKey("ConvPair") ? ForeignEnergySC.Kind.byOrdinal(t.getInteger("ConvPair")) : null;
            if (eu > 0 || x > 0) {
                list.add(Lang.tr("sc.conv.tooltip.stored", String.valueOf(eu),
                        k == null ? "-" : String.valueOf(Math.round(x)) + " " + k.unit));
            }
            if (t.hasKey("UpgradesSC")) {
                list.add(Lang.tr("sc.storage.tooltip.upgrades"));
            }
        }
        PickaxeOnlySC.tooltip(list);
        StringBuilder rates = new StringBuilder();
        for (ForeignEnergySC.Kind kind : ForeignEnergySC.Kind.values()) {
            rates.append(rates.length() == 0 ? "" : ", ").append(trimRate(kind.perEu())).append(' ').append(kind.unit);
        }
        com.sc.util.TooltipSC.more(list,
                Lang.tr("sc.conv.tooltip.details", rates.toString(), ForeignEnergySC.lossPercent(0),
                        ForeignEnergySC.BASE_TIER.name(), ForeignEnergySC.BASE_TIER.getVoltage()),
                Lang.tr("sc.conv.tooltip.howto"));
    }

    /** 4 -> "4", 6.557 -> "6,56". */
    public static String trimRate(double r) {
        if (Math.abs(r - Math.round(r)) < 1e-6) {
            return String.valueOf(Math.round(r));
        }
        return String.format(java.util.Locale.ROOT, "%.2f", r).replace('.', ',');
    }
}
