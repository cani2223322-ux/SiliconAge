package com.sc.block;

import java.util.List;

import com.sc.bridge.BridgeMathSC;
import com.sc.manual.Lang;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** The bridge's parts as items: what each is for (TooltipSC: Shift - details, Ctrl - how to use), what the item kept. */
public class ItemBlockBridgeSC extends ItemBlock {

    public ItemBlockBridgeSC(Block block) {
        super(block);
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    @Override
    public int getMetadata(int damage) {
        return damage;
    }

    private static String name(ItemStack stack) {
        int m = stack.getItemDamage();
        return BlockBridgeSC.NAMES[m >= 0 && m < BlockBridgeSC.NAMES.length ? m : 0];
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return "tile.siliconage.bridge." + name(stack);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        String n = name(stack);
        list.add("§7" + Lang.tr("sc.bridge." + n + ".tooltip"));
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt != null && nbt.getLong("BridgeEU") > 0) {
            list.add(Lang.tr("sc.bridge.tooltip.charge", BridgeMathSC.group(nbt.getLong("BridgeEU")), BridgeMathSC.group(BridgeMathSC.CAPACITOR_EU)));
        }
        if (nbt != null && nbt.getInteger("EnergySC") > 0) {
            list.add(Lang.tr("sc.machine.tooltip.energy", BridgeMathSC.group(nbt.getInteger("EnergySC"))));
        }
        if (nbt != null && nbt.hasKey("BridgeTanks")) {
            int[] t = nbt.getIntArray("BridgeTanks");
            for (int i = 0; i < t.length && i < BridgeMathSC.GASES.length; i++) {
                if (t[i] > 0) {
                    list.add(Lang.tr("sc.armorStation.tooltip.tank", Lang.tr("sc.armorStation.gas." + BridgeMathSC.GASES[i].key()), t[i]));
                }
            }
        }
        if (nbt != null && nbt.hasKey("Bookmarks")) {
            list.add(Lang.tr("sc.bridge.tooltip.bookmarks", nbt.getTagList("Bookmarks", 10).tagCount()));
        }
        PickaxeOnlySC.tooltip(list);
        String more = Lang.tr("sc.bridge." + n + ".more");
        if (stack.getItemDamage() == BlockBridgeSC.CAPACITOR) {
            more = Lang.tr("sc.bridge." + n + ".more", BridgeMathSC.shortEu(BridgeMathSC.CAPACITOR_EU, Lang.tr("sc.bridge.unit.m"), Lang.tr("sc.bridge.unit.g")));
        } else if (stack.getItemDamage() == BlockBridgeSC.GAS_PORT) {
            more = Lang.tr("sc.bridge." + n + ".more", BridgeMathSC.EXTRA_PORT_PERCENT);
        } else if (stack.getItemDamage() == BlockBridgeSC.BEACON) {
            more = Lang.tr("sc.bridge." + n + ".more", BridgeMathSC.BEACON_RADIUS, BridgeMathSC.BEACON_DISCOUNT);
        } else if (stack.getItemDamage() == BlockBridgeSC.COOLER) {
            more = Lang.tr("sc.bridge." + n + ".more", BridgeMathSC.COOL_S, BridgeMathSC.COOL_S / BridgeMathSC.COOLER_SPEED, BridgeMathSC.COOLER_HE_PER_S);
        }
        com.sc.util.TooltipSC.more(list, more, Lang.tr("sc.bridge." + n + ".howto"));
    }
}
