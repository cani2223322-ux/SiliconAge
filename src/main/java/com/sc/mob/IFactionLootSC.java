package com.sc.mob;

import java.util.List;
import java.util.Random;

import net.minecraft.item.ItemStack;

/**
 * PACKAGE M (docs/plan-substrate-tech.md §5.7): a Substrate mob's faction drops - Shell Firmware, scrap, later the
 * stolen cargo and dusts. MobDropsSC asks for them only under rule ПТ-1 (born in Si-14, died in the Substrate, a real
 * player's kill); the numbers live as constants in the mob's class (the handbook's bestiary prints them).
 */
public interface IFactionLootSC {

    /** Adds this kill's faction drops to `out` (server). */
    void factionLoot(List<ItemStack> out, Random rand);
}
