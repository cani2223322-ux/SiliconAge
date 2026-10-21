package com.sc.block;

import java.util.List;

import com.sc.energy.Tier;
import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityWirelessSC;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/** Wireless blocks as items: one per tier (the translator: XV), with range and loss in the tooltip. */
public class ItemBlockWirelessSC extends ItemBlock {

    public ItemBlockWirelessSC(Block block) {
        super(block);
        setHasSubtypes(true);
    }

    private int kind() {
        return ((BlockWirelessSC) field_150939_a).getKind();
    }

    @Override
    public int getMetadata(int damage) {
        return damage;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        if (kind() == TileEntityWirelessSC.QUANTUM) {
            return super.getUnlocalizedName();
        }
        return super.getUnlocalizedName() + "." + BlockWirelessSC.tierFor(stack.getItemDamage()).name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        if (kind() == TileEntityWirelessSC.QUANTUM) {
            list.add("§7" + Lang.tr("sc.wl.tooltip.quantum", TileEntityWirelessSC.QUANTUM_RATE, TileEntityWirelessSC.quantumUpkeep()));
            addCharge(stack, list);
            PickaxeOnlySC.tooltip(list);
            com.sc.util.TooltipSC.more(list, null, Lang.tr("sc.wl.tooltip.quantum2"));
            return;
        }
        Tier t = BlockWirelessSC.tierFor(stack.getItemDamage());
        int range = TileEntityWirelessSC.range(t);
        list.add("§7" + Lang.tr("sc.wl.tooltip.rate", t.getVoltage()));
        list.add("§7" + (range == Integer.MAX_VALUE ? Lang.tr("sc.wl.tooltip.rangeall")
                : Lang.tr("sc.wl.tooltip.range", range)) + ", " + Lang.tr("sc.wl.tooltip.loss", TileEntityWirelessSC.blocksPerPercent(t)));
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey("Partner")) {
            list.add("§b" + Lang.tr("sc.wl.tooltip.linked"));
        }
        addCharge(stack, list);
        PickaxeOnlySC.tooltip(list);
        com.sc.util.TooltipSC.more(list, null, Lang.tr("sc.wl.tooltip.card"));
    }

    /** The charge kept in the item (TileEntityWirelessSC.writeToItem), like a storage block's. */
    @SuppressWarnings("unchecked")
    private static void addCharge(ItemStack stack, List list) {
        int e = stack.hasTagCompound() ? stack.getTagCompound().getInteger("EnergySC") : 0;
        if (e > 0) {
            list.add("§7" + Lang.tr("sc.wl.tooltip.charge", fmt(e)));
        }
    }

    /** 1 234 567 with spaces (as ItemBatterySC.fmt). */
    private static String fmt(long v) {
        String s = Long.toString(v);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) {
                b.append(' ');
            }
            b.append(s.charAt(i));
        }
        return b.toString();
    }
}
