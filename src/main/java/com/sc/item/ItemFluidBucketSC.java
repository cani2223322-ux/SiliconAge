package com.sc.item;

import java.util.ArrayList;
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
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/**
 * A bucket of one of the mod's fluids (1000 mB). None of them has a block in the world, so it
 * doesn't pour out onto the ground - it goes into machines, generators and tanks (right-click,
 * FluidHandSC) and comes back out of them into an empty vanilla bucket. Registered with the
 * FluidContainerRegistry, which also makes NEI's R / U on it list where the fluid is made and used.
 *
 * Metadata is the index into FLUIDS - append only. A fluid another mod already has a bucket for
 * (its own registration came first) is skipped and not listed.
 */
public class ItemFluidBucketSC extends Item {

    /** Every fluid ModFluids registers, in a fixed order: the metadata. Append only. */
    public static final String[] FLUIDS = {"hcl", "sihcl3", "hydrogen", "oxygen", "nitrogen", "argon", "krypton",
            "fluorine", "photoresist", "developer", "hf", "ph3", "bcl3", "ash3", "naoh", "chlorine", "steam",
            "crudeoil", "diesel", "liquidhelium", "deuterium", "ticl4", "heavywater"};

    /** The metadata of the buckets that got registered (see registerContainers()). */
    private static final List<Integer> REGISTERED = new ArrayList<Integer>();

    @SideOnly(Side.CLIENT)
    private IIcon bucket, fluid;

    public ItemFluidBucketSC() {
        setHasSubtypes(true);
        setMaxStackSize(1);
        setContainerItem(Items.bucket);
        setUnlocalizedName(Reference.ASSETS + ".fluidBucket");
        setCreativeTab(ModCreativeTab.TAB);
    }

    private static boolean done;

    /** Registers a bucket for each fluid (init, once every mod's fluids exist; the self-test calls it early). */
    public static void registerContainers(Item item) {
        if (done) {
            return;
        }
        done = true;
        for (int i = 0; i < FLUIDS.length; i++) {
            Fluid f = FluidRegistry.getFluid(FLUIDS[i]);
            if (f == null || FluidContainerRegistry.fillFluidContainer(new FluidStack(f, 1000), new ItemStack(Items.bucket)) != null) {
                continue;       // unknown, or another mod's bucket already holds it
            }
            if (FluidContainerRegistry.registerFluidContainer(new FluidStack(f, FluidContainerRegistry.BUCKET_VOLUME),
                    new ItemStack(item, 1, i), new ItemStack(Items.bucket))) {
                REGISTERED.add(i);
            }
        }
    }

    public static List<Integer> registered() {
        return REGISTERED;
    }

    /** The fluid a bucket holds, or null. */
    public static Fluid fluidOf(ItemStack stack) {
        int i = stack == null ? -1 : stack.getItemDamage();
        return i >= 0 && i < FLUIDS.length ? FluidRegistry.getFluid(FLUIDS[i]) : null;
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        Fluid f = fluidOf(stack);
        return f == null ? super.getItemStackDisplayName(stack)
                : Lang.tr("sc.bucket.name", f.getLocalizedName(new FluidStack(f, 1000)));
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        com.sc.util.TooltipSC.more(list, null, Lang.tr("sc.bucket.tooltip"));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (int i : REGISTERED) {
            list.add(new ItemStack(item, 1, i));
        }
    }

    // ---- the look: the vanilla empty bucket, and the fluid inside tinted its colour ----

    @Override
    @SideOnly(Side.CLIENT)
    public boolean requiresMultipleRenderPasses() {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        bucket = register.registerIcon("bucket_empty");
        fluid = register.registerIcon(Reference.ASSETS + ":bucketFluid");
        itemIcon = bucket;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamageForRenderPass(int meta, int pass) {
        return pass == 0 ? bucket : fluid;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getColorFromItemStack(ItemStack stack, int pass) {
        if (pass == 0) {
            return 0xFFFFFF;
        }
        Fluid f = fluidOf(stack);
        if (f == null) {
            return 0xFFFFFF;
        }
        Integer c = ModFluids.COLORS.get(f.getName());
        return (c != null ? c : f.getColor()) & 0xFFFFFF;
    }
}
