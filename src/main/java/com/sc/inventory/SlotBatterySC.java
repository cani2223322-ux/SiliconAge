package com.sc.inventory;

import com.sc.item.BatteryFeedSC;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/** The battery slot under a screen's energy gauge: one battery (the mod's, or IC2's batteries and crystals). */
public class SlotBatterySC extends Slot {

    /** Where the slot's item sits: x the gauge column's left, the gauge's bottom edge (GuiBatterySlotSC draws round it). */
    public static int itemX(int columnX) {
        return columnX + 3;
    }

    public static int itemY(int gaugeBottom) {
        return gaugeBottom + 4;
    }

    public SlotBatterySC(IInventory inv, int index, int x, int y) {
        super(inv, index, x, y);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return BatteryFeedSC.accepts(stack);
    }

    @Override
    public int getSlotStackLimit() {
        return 1;
    }
}
