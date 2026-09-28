package com.sc.item;

import java.util.List;

import com.sc.Reference;
import com.sc.energy.Tier;
import com.sc.init.ModCreativeTab;
import com.sc.manual.Lang;

import cpw.mods.fml.common.Optional;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

/**
 * Portable batteries, one item, the tier in the damage: LV silicon cell, MV lithium pack, HV
 * crystal, EV quartz capacitor, QV quantum cell, XV exo core. The charge (a long - the XV one
 * holds 4 billion) is kept in "ChargeSC"; the icon shows it in five steps, the bar under it too.
 * Charged and emptied by the mod's storages and charge pads (a tier at most the block's), with
 * IC2 by its batboxes and machines as well. Sneak + right-click picks what it charges itself:
 * nothing, the worn armour, the held item, or everything carried (other batteries left alone),
 * at its own rate - a lamp on the icon while it does.
 */
@Optional.Interface(iface = "ic2.api.item.ISpecialElectricItem", modid = Reference.IC2_MODID)
public class ItemBatterySC extends Item implements ic2.api.item.ISpecialElectricItem {

    public static final String[] KEYS = {"lv", "mv", "hv", "ev", "qv", "xv"};
    public static final Tier[] TIERS = {Tier.LV, Tier.MV, Tier.HV, Tier.EV, Tier.QV, Tier.XV};
    public static final long[] CAPACITY = {40000L, 400000L, 4000000L, 40000000L, 400000000L, 4000000000L};
    public static final int[] RATE = {32, 128, 512, 2048, 8192, 32768};
    /** Modes (sneak + right-click): off, the armour, the held item, everything carried. */
    public static final int MODE_OFF = 0, MODE_ARMOR = 1, MODE_HELD = 2, MODE_ALL = 3, MODES = 4;
    private static final String CHARGE = "ChargeSC", MODE = "BatteryMode";
    /** It charges others every this many ticks, its rate times this many at a time. */
    private static final int EVERY = 10;
    private static final int STEPS = 5;

    /** Client only (IIcon doesn't exist on a dedicated server): made in registerIcons. */
    @SideOnly(Side.CLIENT)
    private IIcon[][] icons;
    @SideOnly(Side.CLIENT)
    private IIcon lamp, blank;
    private Object ic2Manager;

    public ItemBatterySC() {
        setMaxStackSize(1);
        setMaxDamage(0);
        setHasSubtypes(true);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".battery");
    }

    // ------------------------------------------------------------------ the charge

    public static int tierIndex(ItemStack s) {
        return Math.max(0, Math.min(KEYS.length - 1, s.getItemDamage()));
    }

    public static Tier tierOf(ItemStack s) {
        return TIERS[tierIndex(s)];
    }

    public static long capacityOf(ItemStack s) {
        return CAPACITY[tierIndex(s)];
    }

    public static int rateOf(ItemStack s) {
        return RATE[tierIndex(s)];
    }

    public static long chargeOf(ItemStack s) {
        return s.hasTagCompound() ? Math.max(0L, Math.min(capacityOf(s), s.getTagCompound().getLong(CHARGE))) : 0L;
    }

    public static void setCharge(ItemStack s, long eu) {
        if (!s.hasTagCompound()) {
            s.setTagCompound(new NBTTagCompound());
        }
        s.getTagCompound().setLong(CHARGE, Math.max(0L, Math.min(capacityOf(s), eu)));
    }

    /** Puts up to `max` EU in (at most its rate). @return EU taken */
    public static int charge(ItemStack s, int max) {
        long room = capacityOf(s) - chargeOf(s);
        int moved = (int) Math.max(0L, Math.min(room, Math.min(max, rateOf(s))));
        if (moved > 0) {
            setCharge(s, chargeOf(s) + moved);
        }
        return moved;
    }

    /** Takes up to `max` EU out (at most its rate). @return EU given */
    public static int discharge(ItemStack s, int max) {
        int moved = (int) Math.max(0L, Math.min(chargeOf(s), Math.min(max, rateOf(s))));
        if (moved > 0) {
            setCharge(s, chargeOf(s) - moved);
        }
        return moved;
    }

    public static int modeOf(ItemStack s) {
        return s.hasTagCompound() ? Math.max(0, Math.min(MODES - 1, s.getTagCompound().getInteger(MODE))) : MODE_OFF;
    }

    public static boolean isBattery(ItemStack s) {
        return s != null && s.getItem() instanceof ItemBatterySC;
    }

    // ------------------------------------------------------------------ the modes

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (player.isSneaking()) {
            if (!world.isRemote) {
                int mode = (modeOf(stack) + 1) % MODES;
                if (!stack.hasTagCompound()) {
                    stack.setTagCompound(new NBTTagCompound());
                }
                stack.getTagCompound().setInteger(MODE, mode);
                world.playSoundAtEntity(player, Reference.ASSETS + ":battery.mode", 0.5F * com.sc.util.ConfigSC.soundVolume,
                        mode == MODE_OFF ? 0.8F : 1.2F);
                player.addChatComponentMessage(new ChatComponentTranslation("sc.battery.modeset",
                        new ChatComponentTranslation("sc.battery.mode." + mode)));
            }
        }
        return stack;
    }

    /** In a player's inventory with a mode on: every EVERY ticks, what the mode says gets up to rate x EVERY EU. */
    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean held) {
        if (world.isRemote || !(entity instanceof EntityPlayer) || world.getTotalWorldTime() % EVERY != 0) {
            return;
        }
        int mode = modeOf(stack);
        if (mode == MODE_OFF || chargeOf(stack) <= 0) {
            return;
        }
        EntityPlayer p = (EntityPlayer) entity;
        int budget = (int) Math.min(chargeOf(stack), (long) rateOf(stack) * EVERY);
        int spent = 0;
        if (mode == MODE_ARMOR || mode == MODE_ALL) {
            for (ItemStack a : p.inventory.armorInventory) {
                spent += give(a, budget - spent, stack);
            }
        }
        if (mode == MODE_HELD || mode == MODE_ALL) {
            ItemStack hand = p.getCurrentEquippedItem();
            // not while it digs: a changed tag restarts the block's breaking on the client
            if (hand != null && !DrillLogicSC.digging(p, hand) && !p.isSwingInProgress) {
                spent += give(hand, budget - spent, stack);
            }
        }
        if (mode == MODE_ALL) {
            for (ItemStack s : p.inventory.mainInventory) {
                if (s != p.getCurrentEquippedItem()) {
                    spent += give(s, budget - spent, stack);
                }
            }
        }
        if (spent > 0) {
            setCharge(stack, chargeOf(stack) - spent);
        }
    }

    /** One item charged from this battery's budget - never a battery (nor itself). */
    private static int give(ItemStack target, int max, ItemStack self) {
        if (target == null || target == self || max <= 0 || isBattery(target) || ItemChargeSC.isBattery(target)) {
            return 0;
        }
        return Math.max(0, Math.min(max, ItemChargeSC.charge(target, max, tierOf(self))));
    }

    // ------------------------------------------------------------------ look

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return "item." + Reference.ASSETS + ".battery." + KEYS[tierIndex(stack)];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        icons = new IIcon[KEYS.length][STEPS];
        for (int t = 0; t < KEYS.length; t++) {
            for (int k = 0; k < STEPS; k++) {
                icons[t][k] = register.registerIcon(Reference.ASSETS + ":battery_" + KEYS[t] + "_" + k);
            }
        }
        lamp = register.registerIcon(Reference.ASSETS + ":battery_lamp");
        blank = register.registerIcon(Reference.ASSETS + ":battery_blank");
        itemIcon = icons[0][0];
    }

    /** Which of the five pictures: empty, a quarter, half, three quarters, full. */
    private static int step(ItemStack s) {
        long c = chargeOf(s), cap = capacityOf(s);
        if (c <= 0) {
            return 0;
        }
        double f = (double) c / cap;
        return f < 0.375 ? 1 : f < 0.625 ? 2 : f < 0.95 ? 3 : 4;
    }

    @Override
    public boolean requiresMultipleRenderPasses() {
        return true;
    }

    @Override
    public int getRenderPasses(int metadata) {
        return 2;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(ItemStack stack, int pass) {
        if (pass == 1) {
            return modeOf(stack) != MODE_OFF ? lamp : blank;
        }
        return icons[tierIndex(stack)][step(stack)];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int damage) {
        return icons[Math.max(0, Math.min(KEYS.length - 1, damage))][0];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamageForRenderPass(int damage, int pass) {
        return pass == 1 ? blank : getIconFromDamage(damage);
    }

    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        return true;
    }

    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        return 1.0 - (double) chargeOf(stack) / capacityOf(stack);
    }

    /** Creative tab and NEI: each tier empty and full. */
    @Override
    @SuppressWarnings("unchecked")
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (int t = 0; t < KEYS.length; t++) {
            list.add(new ItemStack(item, 1, t));
            ItemStack full = new ItemStack(item, 1, t);
            setCharge(full, CAPACITY[t]);
            list.add(full);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        long c = chargeOf(stack), cap = capacityOf(stack);
        list.add(Lang.tr("sc.battery.charge", fmt(c), fmt(cap), cap == 0 ? 0 : (int) (c * 100 / cap)));
        list.add("§7" + Lang.tr("sc.battery.tier", tierOf(stack).name(), RATE[tierIndex(stack)]));
        int mode = modeOf(stack);
        list.add((mode == MODE_OFF ? "§7" : "§a") + Lang.tr("sc.battery.mode", Lang.tr("sc.battery.mode." + mode)));
        list.add("§8" + Lang.tr("sc.battery.hint"));
    }

    /** 1 234 567 with spaces. */
    static String fmt(long v) {
        String s = Long.toString(v);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) {
                b.append(' ');
            }
            b.append(s.charAt(i));
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ IC2: a battery like its own

    @Override
    public boolean canProvideEnergy(ItemStack stack) {
        return true;
    }

    @Override
    public Item getChargedItem(ItemStack stack) {
        return this;
    }

    @Override
    public Item getEmptyItem(ItemStack stack) {
        return this;
    }

    @Override
    public double getMaxCharge(ItemStack stack) {
        return capacityOf(stack);
    }

    @Override
    public int getTier(ItemStack stack) {
        return tierOf(stack).toIc2Tier();
    }

    @Override
    public double getTransferLimit(ItemStack stack) {
        return rateOf(stack);
    }

    @Override
    @Optional.Method(modid = Reference.IC2_MODID)
    public ic2.api.item.IElectricItemManager getManager(ItemStack stack) {
        if (ic2Manager == null) {
            ic2Manager = new BatteryElectricManagerSC();
        }
        return (ic2.api.item.IElectricItemManager) ic2Manager;
    }
}
