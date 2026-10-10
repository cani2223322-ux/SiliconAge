package com.sc.emp;

import java.util.List;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

/**
 * ПЭ-1 modules (docs/plan-pe1.md §5), meta = EmpModule ordinal. Registry name "empModule"; unlocalized
 * item.siliconage.empModule.<key> (capacity, focus, cooling, efficient, wide); textures empModule_<key>.
 * Put into the gun from the K menu (EmpNetSC.MODULE_INSTALL), one of each kind.
 */
public class ItemEmpModuleSC extends Item {

    private final IIcon[] icons = new IIcon[EmpModule.values().length];

    public ItemEmpModuleSC() {
        setHasSubtypes(true);
        setMaxDamage(0);
        setMaxStackSize(16);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".empModule");
    }

    public static EmpModule moduleOf(ItemStack s) {
        return s != null && s.getItem() instanceof ItemEmpModuleSC ? EmpModule.of(s.getItemDamage()) : null;
    }

    public ItemStack stackOf(EmpModule m) {
        return new ItemStack(this, 1, m.ordinal());
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        EmpModule m = moduleOf(stack);
        return super.getUnlocalizedName() + "." + (m == null ? "unknown" : m.key());
    }

    @Override
    public void registerIcons(IIconRegister register) {
        for (EmpModule m : EmpModule.values()) {
            icons[m.ordinal()] = register.registerIcon(Reference.ASSETS + ":empModule_" + m.key());
        }
    }

    @Override
    public IIcon getIconFromDamage(int damage) {
        return icons[damage >= 0 && damage < icons.length ? damage : 0];
    }

    @Override
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (EmpModule m : EmpModule.values()) {
            list.add(new ItemStack(item, 1, m.ordinal()));
        }
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        EmpTooltipSC.module(stack, player, list);
    }
}
