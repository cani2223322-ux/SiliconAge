package com.sc.item;

import java.util.List;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.machine.UpgradeType;
import com.sc.manual.Lang;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

/** Machine upgrades (UpgradeType), one metadata per kind - they go into a machine's upgrade slots. */
public class ItemUpgradeSC extends Item {

    private IIcon[] icons;

    public ItemUpgradeSC() {
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".upgrade");
    }

    public static UpgradeType typeOf(ItemStack stack) {
        return UpgradeType.byMeta(stack.getItemDamage());
    }

    public ItemStack stackOf(UpgradeType type) {
        return new ItemStack(this, 1, type.ordinal());
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "." + typeOf(stack).name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(Lang.tr("sc.upgrade.tooltip." + typeOf(stack).name().toLowerCase(java.util.Locale.ROOT)));
        list.add(Lang.tr(typeOf(stack).fieldOnly() ? "sc.upgrade.tooltip.slot.field" : "sc.upgrade.tooltip.slot"));
    }

    @Override
    public void registerIcons(IIconRegister register) {
        UpgradeType[] types = UpgradeType.values();
        icons = new IIcon[types.length];
        for (UpgradeType type : types) {
            icons[type.ordinal()] = register.registerIcon(Reference.ASSETS + ":" + type.textureName);
        }
    }

    @Override
    public IIcon getIconFromDamage(int meta) {
        return icons == null ? null : icons[UpgradeType.byMeta(meta).ordinal()];
    }

    @Override
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (UpgradeType type : UpgradeType.values()) {
            list.add(new ItemStack(item, 1, type.ordinal()));
        }
    }
}
