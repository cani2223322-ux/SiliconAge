package com.sc.item;

import java.util.List;
import java.util.Locale;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.util.SiliconMaterial;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

/**
 * All solid silicon-chain intermediates (§3) and the GaAs subsystem (§18.1) on one metadata
 * item - kept separate from ItemMaterialSC (§11.2: Silicon has its own chain).
 *
 * §13.6's Die "type" (Logic/Power/Memory, from which dopant+target combination produced it) is
 * NBT, not metadata - deferred to whichever later step first needs to actually distinguish
 * them (armor chips, §8); the Sputterer recipe (step 11) can tag the NBT once that's needed
 * without touching this class.
 */
public class ItemSiliconMaterialSC extends Item {

    private IIcon[] icons;

    public ItemSiliconMaterialSC() {
        setHasSubtypes(true);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".siliconMaterial");
    }

    public static SiliconMaterial materialAt(int meta) {
        SiliconMaterial[] values = SiliconMaterial.values();
        return values[meta >= 0 && meta < values.length ? meta : 0];
    }

    public ItemStack stackOf(SiliconMaterial material) {
        return new ItemStack(this, 1, material.ordinal());
    }

    @Override
    public void registerIcons(IIconRegister register) {
        SiliconMaterial[] values = SiliconMaterial.values();
        icons = new IIcon[values.length];
        for (SiliconMaterial material : values) {
            icons[material.ordinal()] = register.registerIcon(Reference.ASSETS + ":" + material.textureName);
        }
    }

    @Override
    public IIcon getIconFromDamage(int meta) {
        return icons != null ? icons[materialAt(meta).ordinal()] : null;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "." + materialAt(stack.getItemDamage()).name().toLowerCase(Locale.ROOT);
    }

    @Override
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (SiliconMaterial material : SiliconMaterial.values()) {
            list.add(new ItemStack(item, 1, material.ordinal()));
        }
    }
}
