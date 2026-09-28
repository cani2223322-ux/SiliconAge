package com.sc.item;

import java.util.List;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.manual.Lang;
import com.sc.radiation.RadiationSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

/**
 * The dosimeter: in the hotbar it shows the radiation, the dose and the protection in the corner of
 * the screen and crackles in radiation (RadiationClientSC); right-click writes the numbers in chat.
 */
public class ItemDosimeterSC extends Item {

    public ItemDosimeterSC() {
        setMaxStackSize(1);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".dosimeter");
        setTextureName(Reference.ASSETS + ":dosimeter");
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote) {
            float level = com.sc.util.ConfigSC.radiation ? RadiationSC.levelAt(player) : 0F;
            player.addChatComponentMessage(new ChatComponentTranslation("sc.chat.rad.reading", RadiationSC.fmt(level),
                    RadiationSC.fmt(RadiationSC.doseOf(player)), String.valueOf(RadiationSC.lastProtection(player))));
        }
        return stack;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add("§7" + Lang.tr("sc.dosimeter.tooltip.1"));
        list.add("§7" + Lang.tr("sc.dosimeter.tooltip.2"));
    }
}
