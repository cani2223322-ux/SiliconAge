package com.sc.mob;

import java.util.Map;
import java.util.UUID;

import net.minecraftforge.event.entity.living.LivingDropsEvent;

/**
 * Подложка Ф5 (docs/plan-substrate-tech-f5.md §5.6, rule ПТ-21): a native that decides its drops itself - the Breakdown Warden's
 * personal loot (every player with WardenRulesSC.LOOT_SHARE of the damage gets his own, at most once in 3 game days). MobDropsSC
 * hands it the LivingDropsEvent instead of the standard path (no faction drops, no single trophy roll, no bad-luck counter) - the
 * bags rule doesn't apply (it carries none). The damage log (MobEventsSC.damageShares) is taken before it's forgotten. Server.
 * Shared core: only the coordinator edits this file.
 */
public interface IOwnDropsSC {

    /**
     * The drops of this death. `shares` - every real player's share of the damage the mob took (0..1, by UUID; empty: nobody's);
     * `doMobLoot` - the gamerule (off: experience only, as MobDropsSC). `event.drops` may be added to.
     */
    void ownDrops(LivingDropsEvent event, Map<UUID, Float> shares, boolean doMobLoot);
}
