package com.sc.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sc.Reference;
import com.sc.energy.GeneratorType;
import com.sc.init.ModFluids;
import com.sc.machine.MachineRecipe;
import com.sc.machine.MachineType;
import com.sc.machine.RecipeRegistry;
import com.sc.manual.Lang;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/**
 * A lookup-only stand-in for a fluid in NEI. NEI for 1.7.10 has no fluid stacks, so every
 * recipe whose product or ingredient is a fluid (HCl, chlorine, TiCl4, steam...) could only be
 * found on its machine's page. One variant per fluid any machine recipe or generator uses: R on
 * it lists the recipes that make the fluid, U the ones that consume it.
 *
 * Not obtainable in survival and hidden from the creative tabs - it only shows in NEI's item
 * list (NEI asks getSubItems with a null tab). Metadata is the index into the alphabetical list
 * of those fluid names, so it's stable as long as the recipe set is.
 */
public class ItemFluidDropSC extends Item {

    private static List<String> names;

    public ItemFluidDropSC() {
        setHasSubtypes(true);
        setMaxStackSize(1);
        setUnlocalizedName(Reference.ASSETS + ".fluidDrop");
    }

    /** Every fluid a machine recipe or generator uses, alphabetically (built once recipes exist). */
    public static List<String> names() {
        if (names == null || names.isEmpty()) {
            java.util.Set<String> set = new java.util.TreeSet<String>();
            for (MachineType type : MachineType.values()) {
                for (MachineRecipe r : RecipeRegistry.recipesFor(type)) {
                    for (FluidStack f : new FluidStack[]{r.fluidInputA, r.fluidInputB, r.fluidOutputA, r.fluidOutputB}) {
                        if (f != null && f.getFluid() != null) {
                            set.add(f.getFluid().getName());
                        }
                    }
                }
            }
            for (GeneratorType type : GeneratorType.values()) {
                if (type.fuelFluidName != null) {
                    set.add(type.fuelFluidName);
                }
            }
            List<String> list = new ArrayList<String>(set);
            names = set.isEmpty() ? list : Collections.unmodifiableList(list);
        }
        return names;
    }

    /** The drop standing for `fluid`, or null if no recipe uses it. */
    public static ItemStack stackOf(Fluid fluid) {
        if (fluid == null || com.sc.init.ModItems.fluidDrop == null) {
            return null;
        }
        int i = names().indexOf(fluid.getName());
        return i < 0 ? null : new ItemStack(com.sc.init.ModItems.fluidDrop, 1, i);
    }

    /** The fluid a drop stands for (1000 mB), or null if `stack` isn't a drop. */
    public static FluidStack fluidOf(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemFluidDropSC)) {
            return null;
        }
        List<String> list = names();
        int i = stack.getItemDamage();
        Fluid fluid = i >= 0 && i < list.size() ? FluidRegistry.getFluid(list.get(i)) : null;
        return fluid == null ? null : new FluidStack(fluid, 1000);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        FluidStack fluid = fluidOf(stack);
        return fluid == null ? super.getItemStackDisplayName(stack)
                : Lang.tr("sc.fluiddrop.name", fluid.getFluid().getLocalizedName(fluid));
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(Lang.tr("sc.fluiddrop.tooltip"));
    }

    /** Only NEI (null tab) lists the drops; creative tabs never show them. */
    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        if (tab != null) {
            return;
        }
        for (int i = 0; i < names().size(); i++) {
            list.add(new ItemStack(item, 1, i));
        }
    }

    /** Drawn with the fluid's own (block-atlas) texture. */
    @Override
    @SideOnly(Side.CLIENT)
    public int getSpriteNumber() {
        return 0;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        // no icon of its own - see getIconFromDamage
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int meta) {
        List<String> list = names();
        Fluid fluid = meta >= 0 && meta < list.size() ? FluidRegistry.getFluid(list.get(meta)) : null;
        IIcon icon = fluid == null ? null : fluid.getIcon();
        return icon != null ? icon : Blocks.water.getIcon(0, 0);
    }

    /** Other mods' fluid textures are grey and tinted by their colour; ours carry their colour already. */
    @Override
    @SideOnly(Side.CLIENT)
    public int getColorFromItemStack(ItemStack stack, int pass) {
        FluidStack fluid = fluidOf(stack);
        if (fluid == null || ModFluids.OWNED.contains(fluid.getFluid())) {
            return 0xFFFFFF;
        }
        return fluid.getFluid().getColor(fluid);
    }
}
