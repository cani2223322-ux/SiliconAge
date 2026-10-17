package com.sc.settle;

import com.sc.settle.trade.ItemCreditChipSC;
import com.sc.settle.travel.ItemReturnCapsuleSC;
import com.sc.settle.travel.ItemSettlementMapSC;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Подложка Ф2 (docs/plan-substrate-tech-f2.md §2.2): the settlements' items, registered from ModItems.init (preInit,
 * after MobItemsSC). Registry names are what worlds keep - fixed here: "creditChip" (package T), "returnCapsule" and
 * "settlementMap" (package B). The Stolen Cargo is the combat loot's meta 2 (ItemCombatLootSC.LootKind.STOLEN_CARGO,
 * package T). Each has its TooltipSC tooltip and an NEI «how to get» page (package D). Shared core: only the
 * coordinator edits this file.
 */
public final class SettlementItemsSC {

    public static Item creditChip, returnCapsule, settlementMap;

    private SettlementItemsSC() {
    }

    public static void init() {
        creditChip = new ItemCreditChipSC();
        GameRegistry.registerItem(creditChip, "creditChip");
        returnCapsule = new ItemReturnCapsuleSC();
        GameRegistry.registerItem(returnCapsule, "returnCapsule");
        settlementMap = new ItemSettlementMapSC();
        GameRegistry.registerItem(settlementMap, "settlementMap");
    }

    /** `n` Credit Chips (null before init). */
    public static ItemStack chips(int n) {
        return creditChip == null ? null : new ItemStack(creditChip, n);
    }

    /** `n` Return Capsules (null before init). */
    public static ItemStack capsules(int n) {
        return returnCapsule == null ? null : new ItemStack(returnCapsule, n);
    }
}
