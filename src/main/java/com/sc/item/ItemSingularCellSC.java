package com.sc.item;

import java.util.List;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.init.ModFluids;
import com.sc.manual.Lang;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

/**
 * The Singular Matter cell (plan item 7): a tank-in-an-item for singular matter only, CAPACITY mB,
 * in the look of the liquid helium / deuterium cells (violet 0xC85AFF inside, grey when empty).
 * As an IFluidContainerItem it fills from the Matter Compressor's / the Singular Reactor's tank and
 * any other singular matter tank by a right-click (FluidHandSC), pours into the Singular Station's
 * 8th tank, a mod tank or a machine, and into the worn suit from the K menu (one cell or «Заправить
 * всё», ArmorNetSC) - giving what fits and keeping the rest. NBT "Fluid" (Forge's FluidStack tag).
 */
public class ItemSingularCellSC extends Item implements IFluidContainerItem {

    public static final int CAPACITY = 1000;
    private static final String TAG = "Fluid";

    @SideOnly(Side.CLIENT)
    private IIcon full, empty;

    public ItemSingularCellSC() {
        setMaxStackSize(1);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".singularMatterCell");
    }

    /** mB of singular matter in the cell (0: empty or not a cell). */
    public static int amountOf(ItemStack s) {
        if (s == null || !(s.getItem() instanceof ItemSingularCellSC)) {
            return 0;
        }
        FluidStack f = ((ItemSingularCellSC) s.getItem()).getFluid(s);
        return f == null ? 0 : f.amount;
    }

    /** A cell holding `mb` of singular matter (0: an empty one). */
    public static ItemStack filled(Item item, int mb) {
        ItemStack s = new ItemStack(item);
        if (mb > 0 && ModFluids.singularMatter != null && item instanceof ItemSingularCellSC) {
            ((ItemSingularCellSC) item).fill(s, new FluidStack(ModFluids.singularMatter, mb), true);
        }
        return s;
    }

    // ---- IFluidContainerItem ----

    @Override
    public FluidStack getFluid(ItemStack container) {
        if (container == null || !container.hasTagCompound() || !container.getTagCompound().hasKey(TAG)) {
            return null;
        }
        FluidStack f = FluidStack.loadFluidStackFromNBT(container.getTagCompound().getCompoundTag(TAG));
        return f == null || f.amount <= 0 ? null : f;
    }

    @Override
    public int getCapacity(ItemStack container) {
        return CAPACITY;
    }

    @Override
    public int fill(ItemStack container, FluidStack resource, boolean doFill) {
        if (container == null || resource == null || resource.amount <= 0 || ModFluids.singularMatter == null
                || resource.getFluid() != ModFluids.singularMatter || container.stackSize != 1) {
            return 0;
        }
        FluidStack in = getFluid(container);
        int have = in == null ? 0 : in.amount;
        int put = Math.max(0, Math.min(CAPACITY - have, resource.amount));
        if (doFill && put > 0) {
            set(container, have + put);
        }
        return put;
    }

    @Override
    public FluidStack drain(ItemStack container, int maxDrain, boolean doDrain) {
        FluidStack in = getFluid(container);
        if (in == null || maxDrain <= 0 || container.stackSize != 1) {
            return null;
        }
        int took = Math.min(in.amount, maxDrain);
        if (doDrain) {
            set(container, in.amount - took);
        }
        return new FluidStack(in.getFluid(), took);
    }

    private static void set(ItemStack s, int mb) {
        if (mb <= 0) {
            if (s.hasTagCompound()) {
                s.getTagCompound().removeTag(TAG);
                if (s.getTagCompound().hasNoTags()) {
                    s.setTagCompound(null);
                }
            }
            return;
        }
        if (!s.hasTagCompound()) {
            s.setTagCompound(new NBTTagCompound());
        }
        s.getTagCompound().setTag(TAG, new FluidStack(ModFluids.singularMatter, Math.min(CAPACITY, mb)).writeToNBT(new NBTTagCompound()));
    }

    // ---- the look, the tooltip ----

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        int mb = amountOf(stack);
        list.add((mb > 0 ? "§d" : "§7") + Lang.tr("sc.cell.sm.amount", mb, CAPACITY));
        com.sc.util.TooltipSC.more(list, Lang.tr("sc.cell.sm.details", CAPACITY), Lang.tr("sc.cell.sm.howto"));
    }

    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        return amountOf(stack) > 0;
    }

    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        return 1.0 - amountOf(stack) / (double) CAPACITY;
    }

    @Override
    public net.minecraft.item.EnumRarity getRarity(ItemStack stack) {
        return amountOf(stack) > 0 ? net.minecraft.item.EnumRarity.rare : net.minecraft.item.EnumRarity.uncommon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        list.add(new ItemStack(item));
        list.add(filled(item, CAPACITY));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        full = register.registerIcon(Reference.ASSETS + ":singularMatterCell");
        empty = register.registerIcon(Reference.ASSETS + ":singularMatterCellEmpty");
        itemIcon = empty;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconIndex(ItemStack stack) {
        return amountOf(stack) > 0 ? full : empty;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(ItemStack stack, int pass) {
        return getIconIndex(stack);
    }
}
