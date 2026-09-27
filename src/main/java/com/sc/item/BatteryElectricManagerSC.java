package com.sc.item;

import ic2.api.item.ElectricItem;
import ic2.api.item.IElectricItemManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

/**
 * IC2's view of the portable batteries (only loaded with IC2 - see ItemBatterySC.getManager).
 * The charge stays the mod's own "ChargeSC"; IC2's rules: a charger below the battery's tier
 * gives nothing, a call moves at most the transfer limit - and, being a battery, it gives its
 * energy to IC2 machines and chargers too.
 */
public class BatteryElectricManagerSC implements IElectricItemManager {

    private static boolean ok(ItemStack s) {
        return s != null && s.stackSize == 1 && ItemBatterySC.isBattery(s);
    }

    @Override
    public double charge(ItemStack stack, double amount, int tier, boolean ignoreTransferLimit, boolean simulate) {
        if (!ok(stack) || amount <= 0 || tier < ItemBatterySC.tierOf(stack).toIc2Tier()) {
            return 0;
        }
        double limit = ignoreTransferLimit ? amount : Math.min(amount, ItemBatterySC.rateOf(stack));
        long room = ItemBatterySC.capacityOf(stack) - ItemBatterySC.chargeOf(stack);
        long moved = (long) Math.max(0, Math.min(room, Math.floor(limit)));
        if (!simulate && moved > 0) {
            ItemBatterySC.setCharge(stack, ItemBatterySC.chargeOf(stack) + moved);
        }
        return moved;
    }

    @Override
    public double discharge(ItemStack stack, double amount, int tier, boolean ignoreTransferLimit, boolean externally, boolean simulate) {
        if (!ok(stack) || amount <= 0 || tier < ItemBatterySC.tierOf(stack).toIc2Tier() && !ignoreTransferLimit) {
            return 0;
        }
        double limit = ignoreTransferLimit ? amount : Math.min(amount, ItemBatterySC.rateOf(stack));
        long moved = (long) Math.max(0, Math.min(ItemBatterySC.chargeOf(stack), Math.ceil(limit)));
        if (!simulate && moved > 0) {
            ItemBatterySC.setCharge(stack, ItemBatterySC.chargeOf(stack) - moved);
        }
        return moved;
    }

    @Override
    public double getCharge(ItemStack stack) {
        return ok(stack) ? ItemBatterySC.chargeOf(stack) : 0;
    }

    @Override
    public boolean canUse(ItemStack stack, double amount) {
        return ok(stack) && ItemBatterySC.chargeOf(stack) >= amount;
    }

    @Override
    public boolean use(ItemStack stack, double amount, EntityLivingBase entity) {
        if (!canUse(stack, amount)) {
            return false;
        }
        ItemBatterySC.setCharge(stack, ItemBatterySC.chargeOf(stack) - (long) Math.ceil(amount));
        return true;
    }

    @Override
    public void chargeFromArmor(ItemStack stack, EntityLivingBase entity) {
        if (ElectricItem.rawManager != null && ElectricItem.rawManager != this) {
            ElectricItem.rawManager.chargeFromArmor(stack, entity);
        }
    }

    @Override
    public String getToolTip(ItemStack stack) {
        return ok(stack) ? ItemBatterySC.fmt(ItemBatterySC.chargeOf(stack)) + " / " + ItemBatterySC.fmt(ItemBatterySC.capacityOf(stack)) + " EU" : "";
    }
}
