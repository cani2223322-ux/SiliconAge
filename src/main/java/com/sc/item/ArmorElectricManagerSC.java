package com.sc.item;

import ic2.api.item.ElectricItem;
import ic2.api.item.IElectricItemManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

/**
 * IC2's view of the charge of the suits and the energy blades (only loaded with IC2 - see
 * ItemArmorSC / ItemBladeSC.getManager). Keeps the mod's own "ChargeSC" NBT instead of IC2's
 * "charge", so the charge is the same with or without IC2. Follows IC2's rules: a charger below
 * the item's tier gives nothing, one call moves at most the transfer limit, and the items never
 * give energy away.
 */
public class ArmorElectricManagerSC implements IElectricItemManager {

    /** The suit piece or blade as IC2 sees it, or null for anything else (or a stack of several). */
    private static ic2.api.item.ISpecialElectricItem item(ItemStack stack) {
        return stack != null && stack.stackSize == 1
                && (stack.getItem() instanceof ItemArmorSC || stack.getItem() instanceof ItemBladeSC || stack.getItem() instanceof ItemDrillSC)
                ? (ic2.api.item.ISpecialElectricItem) stack.getItem() : null;
    }

    private static int stored(ItemStack stack) {
        return stack.getItem() instanceof ItemBladeSC ? ItemBladeSC.chargeOf(stack)
                : stack.getItem() instanceof ItemDrillSC ? ItemDrillSC.chargeOf(stack) : ItemArmorSC.chargeOf(stack);
    }

    private static int capacity(ItemStack stack) {
        return stack.getItem() instanceof ItemBladeSC ? ItemBladeSC.capacityOf(stack)
                : stack.getItem() instanceof ItemDrillSC ? ItemDrillSC.capacityOf(stack) : ItemArmorSC.capacityOf(stack);
    }

    private static void add(ItemStack stack, int eu) {
        if (stack.getItem() instanceof ItemDrillSC) {
            ItemDrillSC.charge(stack, eu);
        } else if (stack.getItem() instanceof ItemBladeSC) {
            ItemBladeSC.charge(stack, eu);
        } else {
            ItemArmorSC.charge(stack, eu);
        }
    }

    private static void take(ItemStack stack, int eu) {
        if (stack.getItem() instanceof ItemDrillSC) {
            ItemDrillSC.discharge(stack, eu);
        } else if (stack.getItem() instanceof ItemBladeSC) {
            ItemBladeSC.discharge(stack, eu);
        } else {
            ItemArmorSC.discharge(stack, eu);
        }
    }

    @Override
    public double charge(ItemStack stack, double amount, int tier, boolean ignoreTransferLimit, boolean simulate) {
        ic2.api.item.ISpecialElectricItem item = item(stack);
        if (item == null || amount <= 0 || tier < item.getTier(stack)) {
            return 0;
        }
        double limit = ignoreTransferLimit ? amount : Math.min(amount, item.getTransferLimit(stack));
        int room = capacity(stack) - stored(stack);
        int moved = (int) Math.max(0, Math.min(room, Math.floor(limit)));
        if (!simulate && moved > 0) {
            add(stack, moved);
        }
        return moved;
    }

    @Override
    public double discharge(ItemStack stack, double amount, int tier, boolean ignoreTransferLimit, boolean externally, boolean simulate) {
        ic2.api.item.ISpecialElectricItem item = item(stack);
        if (item == null || amount <= 0 || externally) {
            return 0;
        }
        double limit = ignoreTransferLimit ? amount : Math.min(amount, item.getTransferLimit(stack));
        int moved = (int) Math.max(0, Math.min(stored(stack), Math.ceil(limit)));
        if (!simulate && moved > 0) {
            take(stack, moved);
        }
        return moved;
    }

    @Override
    public double getCharge(ItemStack stack) {
        return item(stack) == null ? 0 : stored(stack);
    }

    @Override
    public boolean canUse(ItemStack stack, double amount) {
        return item(stack) != null && stored(stack) >= amount;
    }

    @Override
    public boolean use(ItemStack stack, double amount, EntityLivingBase entity) {
        if (!canUse(stack, amount)) {
            return false;
        }
        take(stack, (int) Math.ceil(amount));
        return true;
    }

    @Override
    public void chargeFromArmor(ItemStack stack, EntityLivingBase entity) {
        // not ElectricItem.manager: it dispatches back to this manager - endless recursion
        if (ElectricItem.rawManager != null && ElectricItem.rawManager != this) {
            ElectricItem.rawManager.chargeFromArmor(stack, entity);
        }
    }

    @Override
    public String getToolTip(ItemStack stack) {
        return item(stack) == null ? "" : stored(stack) + " / " + capacity(stack) + " EU";
    }
}
