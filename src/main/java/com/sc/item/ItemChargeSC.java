package com.sc.item;

import com.sc.energy.Tier;

import net.minecraft.item.ItemStack;

/**
 * Charging any electric item from a source of a given tier - what a portable battery's modes
 * use: the mod's suits, blades, drills, weapons and wrenches (only up to the source's tier, as
 * a storage would), and with IC2 its electric items (IC2 checks the tier itself).
 */
public final class ItemChargeSC {

    private ItemChargeSC() {
    }

    /** @return EU taken, at most `max` */
    public static int charge(ItemStack s, int max, Tier tier) {
        if (s == null || max <= 0) {
            return 0;
        }
        if (s.getItem() instanceof ItemArmorSC) {
            return ((ItemArmorSC) s.getItem()).getSuit().chargeTier.ordinal() <= tier.ordinal() ? ItemArmorSC.charge(s, max) : 0;
        }
        if (s.getItem() instanceof ItemBladeSC) {
            return ItemBladeSC.typeOf(s).chargeTier.ordinal() <= tier.ordinal() ? ItemBladeSC.charge(s, max) : 0;
        }
        if (s.getItem() instanceof ItemDrillSC) {
            return ItemDrillSC.typeOf(s).chargeTier.ordinal() <= tier.ordinal() ? ItemDrillSC.charge(s, max) : 0;
        }
        if (s.getItem() instanceof ItemWeaponSC) {
            ItemWeaponSC w = (ItemWeaponSC) s.getItem();
            return w.getType().tier.ordinal() <= tier.ordinal() ? ItemWeaponSC.charge(s, w.getType(), max) : 0;
        }
        if (ItemWrenchSC.isElectric(s)) {
            return ItemWrenchSC.tierOf(s).chargeTier.ordinal() <= tier.ordinal() ? ItemWrenchSC.charge(s, max) : 0;
        }
        if (cpw.mods.fml.common.Loader.isModLoaded(com.sc.Reference.IC2_MODID)) {
            return Ic2.charge(s, max, tier.toIc2Tier());
        }
        return 0;
    }

    /** Something that gives energy away: our battery, or (with IC2) its batteries and crystals. */
    public static boolean isBattery(ItemStack s) {
        if (ItemBatterySC.isBattery(s)) {
            return true;
        }
        return s != null && cpw.mods.fml.common.Loader.isModLoaded(com.sc.Reference.IC2_MODID) && Ic2.isBattery(s);
    }

    /** Kept apart so IC2's API is only loaded when IC2 is. */
    private static final class Ic2 {
        static int charge(ItemStack s, int max, int tier) {
            if (!(s.getItem() instanceof ic2.api.item.IElectricItem) || ic2.api.item.ElectricItem.manager == null) {
                return 0;
            }
            return (int) ic2.api.item.ElectricItem.manager.charge(s, max, tier, false, false);
        }

        static boolean isBattery(ItemStack s) {
            return s.getItem() instanceof ic2.api.item.IElectricItem && ((ic2.api.item.IElectricItem) s.getItem()).canProvideEnergy(s);
        }
    }
}
