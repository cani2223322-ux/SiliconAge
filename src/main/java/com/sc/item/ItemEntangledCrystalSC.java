package com.sc.item;

import java.util.List;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
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
import net.minecraft.world.World;

/**
 * The entangled crystal (damage 0) and its halves (damage 1). Sneak + right-click breaks a whole
 * crystal into two halves sharing one pair number; a half in a quantum translator links it with
 * the translator holding the other half, anywhere. A half wears while its link is up ("Life").
 */
public class ItemEntangledCrystalSC extends Item {

    public static final int WHOLE = 0, HALF = 1;
    /** A half's life: 100 000, one step every WEAR_EVERY ticks of an up link - about 1% an hour. */
    public static final int LIFE_MAX = 100000;
    private static final String PAIR = "PairSC", HALF_NO = "HalfSC", LIFE = "LifeSC";

    @SideOnly(Side.CLIENT)
    private IIcon whole, half1, half2;

    public ItemEntangledCrystalSC() {
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".entangledCrystal");
    }

    public static long pairOf(ItemStack s) {
        return s != null && s.getItem() instanceof ItemEntangledCrystalSC && s.getItemDamage() == HALF && s.hasTagCompound()
                ? s.getTagCompound().getLong(PAIR) : 0L;
    }

    public static int halfOf(ItemStack s) {
        return pairOf(s) == 0 ? 0 : s.getTagCompound().getInteger(HALF_NO);
    }

    public static int lifeOf(ItemStack s) {
        return pairOf(s) == 0 ? 0 : s.getTagCompound().getInteger(LIFE);
    }

    public static void wear(ItemStack s, int n) {
        if (pairOf(s) != 0) {
            s.getTagCompound().setInteger(LIFE, Math.max(0, lifeOf(s) - n));
        }
    }

    /** "4F2A": what the tooltip and the screen call the pair. */
    public static String pairName(long pair) {
        return String.format("%04X", (int) (pair & 0xFFFF));
    }

    public static ItemStack half(Item item, long pair, int no) {
        ItemStack h = new ItemStack(item, 1, HALF);
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setLong(PAIR, pair);
        nbt.setInteger(HALF_NO, no);
        nbt.setInteger(LIFE, LIFE_MAX);
        h.setTagCompound(nbt);
        return h;
    }

    @Override
    public int getItemStackLimit(ItemStack stack) {
        return stack.getItemDamage() == HALF ? 1 : 16;
    }

    /** Sneak + right-click a whole crystal: two halves of one new pair. */
    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (stack.getItemDamage() != WHOLE || !player.isSneaking()) {
            return stack;
        }
        if (!world.isRemote) {
            long pair = (world.rand.nextLong() & Long.MAX_VALUE) | 1L;
            ItemStack a = half(this, pair, 1), b = half(this, pair, 2);
            stack.stackSize--;
            for (ItemStack h : new ItemStack[]{a, b}) {
                if (!player.inventory.addItemStackToInventory(h)) {
                    player.dropPlayerItemWithRandomChoice(h, false);
                }
            }
            world.playSoundAtEntity(player, Reference.ASSETS + ":battery.mode", 0.6F * com.sc.util.ConfigSC.soundVolume, 0.7F);
            player.inventoryContainer.detectAndSendChanges();
        }
        return stack;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return "item." + Reference.ASSETS + (stack.getItemDamage() == HALF ? ".entangledHalf" : ".entangledCrystal");
    }

    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        return pairOf(stack) != 0;
    }

    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        return 1.0 - (double) lifeOf(stack) / LIFE_MAX;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        list.add(new ItemStack(item, 1, WHOLE));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        long pair = pairOf(stack);
        if (pair == 0) {
            list.add("§7" + Lang.tr("sc.crystal.tooltip.whole"));
            list.add("§7" + Lang.tr("sc.crystal.tooltip.whole2"));
            return;
        }
        list.add("§d" + Lang.tr("sc.crystal.tooltip.pair", pairName(pair), halfOf(stack)));
        int pct = (int) Math.ceil(lifeOf(stack) * 100.0 / LIFE_MAX);
        list.add((pct > 20 ? "§a" : pct > 0 ? "§e" : "§c") + Lang.tr("sc.crystal.tooltip.life", pct));
        list.add("§7" + Lang.tr("sc.crystal.tooltip.half"));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        whole = register.registerIcon(Reference.ASSETS + ":entangledCrystal");
        half1 = register.registerIcon(Reference.ASSETS + ":entangledHalf1");
        half2 = register.registerIcon(Reference.ASSETS + ":entangledHalf2");
        itemIcon = whole;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(ItemStack stack, int pass) {
        return stack.getItemDamage() != HALF ? whole : halfOf(stack) == 2 ? half2 : half1;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconIndex(ItemStack stack) {
        return getIcon(stack, 0);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int damage) {
        return damage == HALF ? half1 : whole;
    }
}
