package com.sc.item;

import com.sc.Reference;
import com.sc.SCMod;
import com.sc.init.ModCreativeTab;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

/** §1 of 02_guide_book.md: the manual book - opens GuiManual (a plain GuiScreen, not vanilla's book GUI). */
public class ItemSCManual extends Item {

    private IIcon icon;

    public ItemSCManual() {
        setMaxStackSize(1);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".manual");
    }

    @Override
    public void registerIcons(IIconRegister register) {
        icon = register.registerIcon(Reference.ASSETS + ":scManual");
    }

    @Override
    public IIcon getIconFromDamage(int damage) {
        return icon;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) {
            SCMod.proxy.openManual();
        }
        return stack;
    }
}
