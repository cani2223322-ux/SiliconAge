package com.sc.item;

import java.util.List;
import java.util.Locale;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.util.Material;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

/**
 * One class per §11.1 item kind (crushedOre / purifiedCrushedOre / dust / dustTiny / ingot),
 * each holding every applicable Material as a metadata variant - no 16-value cap here (unlike
 * BlockOreSC, §10): Item damage is a short, so one class comfortably holds all ~24 materials.
 */
public class ItemMaterialSC extends Item {

    private final MaterialItemKind kind;
    private final Material[] materials;
    private IIcon[] icons;

    public ItemMaterialSC(MaterialItemKind kind) {
        this.kind = kind;
        this.materials = Material.byKind(kind);
        setHasSubtypes(true);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + "." + kind.prefix);
    }

    public Material materialAt(int meta) {
        return meta >= 0 && meta < materials.length ? materials[meta] : materials[0];
    }

    public int metaOf(Material material) {
        for (int i = 0; i < materials.length; i++) {
            if (materials[i] == material) {
                return i;
            }
        }
        return -1;
    }

    public ItemStack stackOf(Material material) {
        int meta = metaOf(material);
        return meta < 0 ? null : new ItemStack(this, 1, meta);
    }

    @Override
    public void registerIcons(IIconRegister register) {
        icons = new IIcon[materials.length];
        for (int i = 0; i < materials.length; i++) {
            String textureName = kind.prefix + materials[i].oreDictNameFor(kind);
            icons[i] = register.registerIcon(Reference.ASSETS + ":" + textureName);
        }
    }

    @Override
    public IIcon getIconFromDamage(int meta) {
        if (kind == MaterialItemKind.INGOT) {
            return IngotLookSC.icon();          // vanilla's ingot shape, tinted below
        }
        if (usesIc2DustLook()) {
            return DustLookSC.icon(kind == MaterialItemKind.DUST_TINY);   // IC2's dust shape, tinted below
        }
        if (icons == null || meta < 0 || meta >= icons.length) {
            return icons != null ? icons[0] : null;
        }
        return icons[meta];
    }

    @Override
    public int getColorFromItemStack(ItemStack stack, int pass) {
        if (kind == MaterialItemKind.INGOT) {
            return IngotLookSC.tint(materialAt(stack.getItemDamage()));
        }
        return usesIc2DustLook() ? DustLookSC.tint(materialAt(stack.getItemDamage())) : 0xFFFFFF;
    }

    /** Dusts borrow IC2's dust icons when IC2 is installed (and its icon is really there). */
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    private boolean usesIc2DustLook() {
        return (kind == MaterialItemKind.DUST || kind == MaterialItemKind.DUST_TINY)
                && DustLookSC.available() && DustLookSC.icon(kind == MaterialItemKind.DUST_TINY) != null;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        Material material = materialAt(stack.getItemDamage());
        return super.getUnlocalizedName() + "." + material.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (int i = 0; i < materials.length; i++) {
            list.add(new ItemStack(item, 1, i));
        }
    }
}
