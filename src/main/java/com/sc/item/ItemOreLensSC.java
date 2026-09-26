package com.sc.item;

import java.util.List;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.machine.ExoOreTableSC;
import com.sc.manual.Lang;
import com.sc.util.OreEntry;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** An ore lens for the Exo Drilling Rig, one metadata per mod ore (OreEntry), tinted in the ore's colour. */
public class ItemOreLensSC extends Item {

    public ItemOreLensSC() {
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".oreLens");
        setTextureName(Reference.ASSETS + ":oreLens");
    }

    public static int oreOf(ItemStack s) {
        return Math.max(0, Math.min(OreEntry.values().length - 1, s.getItemDamage()));
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        String ore = new ItemStack(com.sc.init.ModBlocks.oreSC, 1, oreOf(stack)).getDisplayName();
        return Lang.tr("item.siliconage.oreLens.name", ore);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getColorFromItemStack(ItemStack stack, int pass) {
        return ExoOreTableSC.LENS_COLORS[oreOf(stack)];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(Lang.tr("sc.orelens.tooltip", ExoOreTableSC.LENS_BOOST + 1));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (OreEntry o : OreEntry.values()) {
            list.add(new ItemStack(item, 1, o.meta()));
        }
    }
}
