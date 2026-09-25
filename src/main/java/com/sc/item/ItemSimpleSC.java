package com.sc.item;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

/** A single-icon, no-subtype item - for one-off materials that don't fit an existing metadata group (e.g. Coke, §17.2). */
public class ItemSimpleSC extends net.minecraft.item.Item {

    private final String textureName;
    private IIcon icon;

    public ItemSimpleSC(String unlocalizedName, String textureName) {
        this.textureName = textureName;
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + "." + unlocalizedName);
    }

    @Override
    public void registerIcons(IIconRegister register) {
        icon = register.registerIcon(Reference.ASSETS + ":" + textureName);
    }

    @Override
    public IIcon getIconFromDamage(int damage) {
        return "nb3SnIngot".equals(textureName) ? IngotLookSC.icon() : icon;
    }

    /** The Nb3Sn ingot shares the vanilla-ingot look of every other ingot (IngotLookSC). */
    @Override
    public int getColorFromItemStack(net.minecraft.item.ItemStack stack, int pass) {
        return "nb3SnIngot".equals(textureName) ? IngotLookSC.NB3SN_TINT : 0xFFFFFF;
    }
}
