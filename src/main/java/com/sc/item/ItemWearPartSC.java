package com.sc.item;

import java.util.List;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.manual.Lang;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * A part that wears out in a generator: the Wind Turbine's rotor, the RTG's radioisotope
 * capsule. Its damage is the steps (seconds) used up; the bar and the tooltip show what's left.
 */
public class ItemWearPartSC extends Item {

    private final String name;

    public ItemWearPartSC(String name, int lifeSeconds) {
        this.name = name;
        setMaxStackSize(1);
        setMaxDamage(lifeSeconds);
        setNoRepair();
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + "." + name);
        setTextureName(Reference.ASSETS + ":" + name);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        int left = stack.getMaxDamage() - stack.getItemDamage();
        list.add(Lang.tr("sc.wear.left", left * 100 / Math.max(1, stack.getMaxDamage()), left / 3600, left / 60 % 60));
        list.add("§7" + Lang.tr("sc.wear." + name));
    }
}
