package com.sc.mob;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * PACKAGE M (docs/plan-substrate-tech.md §5.7): the Substrate mobs' items, registered from ModItems.init (preInit,
 * the coordinator's call). Registry names are what worlds keep - fixed here: "trophySC" (meta: §5.7 table),
 * "combatLoot" (meta = LootKind ordinal, appended only), "spawnEggSC" (meta = the pool id of MobEntitiesSC).
 * Each has its TooltipSC tooltip (a summary, Shift - the details, Ctrl - how to get / use it).
 */
public final class MobItemsSC {

    public static Item trophy, combatLoot, spawnEgg;

    private MobItemsSC() {
    }

    public static void init() {
        trophy = new ItemTrophySC();
        GameRegistry.registerItem(trophy, "trophySC");
        combatLoot = new ItemCombatLootSC();
        GameRegistry.registerItem(combatLoot, "combatLoot");
        spawnEgg = new ItemSpawnEggSC();
        GameRegistry.registerItem(spawnEgg, "spawnEggSC");
    }

    /** A trophy of meta `meta` (TrophyTableSC), or null before init. */
    public static ItemStack trophyStack(int meta) {
        return trophy == null ? null : new ItemStack(trophy, 1, meta);
    }

    /** `count` of a combat loot kind, or null before init. */
    public static ItemStack loot(ItemCombatLootSC.LootKind kind, int count) {
        return combatLoot == null ? null : new ItemStack(combatLoot, count, kind.ordinal());
    }
}
