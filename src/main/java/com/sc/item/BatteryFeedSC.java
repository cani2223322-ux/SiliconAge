package com.sc.item;

import net.minecraft.item.ItemStack;

/**
 * The battery slot of the machines, quarries and field generator: what goes in it (the mod's
 * portable batteries and, with IC2, its batteries and crystals - things that give energy away,
 * not tools), how much it gives and holds. Any tier is fine: the energy comes out of the item at
 * most at the block's input voltage, so nothing can blow up.
 */
public final class BatteryFeedSC {

    /** The slot's modes: only while the buffer is under half (the grid first), or always keep it full. */
    public static final int MODE_RESERVE = 0, MODE_ALWAYS = 1, MODES = 2;

    private BatteryFeedSC() {
    }

    public static boolean accepts(ItemStack s) {
        if (s == null) {
            return false;
        }
        if (ItemBatterySC.isBattery(s)) {
            return true;
        }
        return cpw.mods.fml.common.Loader.isModLoaded(com.sc.Reference.IC2_MODID) && Ic2.isBattery(s);
    }

    /** Takes up to `max` EU out of the battery. @return EU got */
    public static int discharge(ItemStack s, int max) {
        if (s == null || max <= 0) {
            return 0;
        }
        if (ItemBatterySC.isBattery(s)) {
            return ItemBatterySC.discharge(s, max);
        }
        return cpw.mods.fml.common.Loader.isModLoaded(com.sc.Reference.IC2_MODID) ? Ic2.discharge(s, max) : 0;
    }

    /** Puts up to `max` EU in (a generator charging it). @return EU taken */
    public static int charge(ItemStack s, int max) {
        if (s == null || max <= 0) {
            return 0;
        }
        if (ItemBatterySC.isBattery(s)) {
            return ItemBatterySC.charge(s, max);
        }
        return cpw.mods.fml.common.Loader.isModLoaded(com.sc.Reference.IC2_MODID) ? Ic2.charge(s, max) : 0;
    }

    public static long chargeOf(ItemStack s) {
        if (s == null) {
            return 0;
        }
        if (ItemBatterySC.isBattery(s)) {
            return ItemBatterySC.chargeOf(s);
        }
        return cpw.mods.fml.common.Loader.isModLoaded(com.sc.Reference.IC2_MODID) ? Ic2.charge(s) : 0;
    }

    public static long capacityOf(ItemStack s) {
        if (s == null) {
            return 0;
        }
        if (ItemBatterySC.isBattery(s)) {
            return ItemBatterySC.capacityOf(s);
        }
        return cpw.mods.fml.common.Loader.isModLoaded(com.sc.Reference.IC2_MODID) ? Ic2.capacity(s) : 0;
    }

    /** What it gives a tick at most. */
    public static int rateOf(ItemStack s) {
        if (s == null) {
            return 0;
        }
        if (ItemBatterySC.isBattery(s)) {
            return ItemBatterySC.rateOf(s);
        }
        return cpw.mods.fml.common.Loader.isModLoaded(com.sc.Reference.IC2_MODID) ? Ic2.rate(s) : 0;
    }

    /** Kept apart so IC2's API is only loaded when IC2 is. */
    private static final class Ic2 {
        static boolean isBattery(ItemStack s) {
            return s.getItem() instanceof ic2.api.item.IElectricItem && ((ic2.api.item.IElectricItem) s.getItem()).canProvideEnergy(s);
        }

        static int discharge(ItemStack s, int max) {
            if (!isBattery(s) || ic2.api.item.ElectricItem.manager == null) {
                return 0;
            }
            return (int) ic2.api.item.ElectricItem.manager.discharge(s, max, Integer.MAX_VALUE, false, true, false);
        }

        static int charge(ItemStack s, int max) {
            if (!isBattery(s) || ic2.api.item.ElectricItem.manager == null) {
                return 0;
            }
            return (int) ic2.api.item.ElectricItem.manager.charge(s, max, Integer.MAX_VALUE, false, false);
        }

        static long charge(ItemStack s) {
            return isBattery(s) && ic2.api.item.ElectricItem.manager != null ? (long) ic2.api.item.ElectricItem.manager.getCharge(s) : 0;
        }

        static long capacity(ItemStack s) {
            return isBattery(s) ? (long) ((ic2.api.item.IElectricItem) s.getItem()).getMaxCharge(s) : 0;
        }

        static int rate(ItemStack s) {
            return isBattery(s) ? (int) ((ic2.api.item.IElectricItem) s.getItem()).getTransferLimit(s) : 0;
        }
    }
}
