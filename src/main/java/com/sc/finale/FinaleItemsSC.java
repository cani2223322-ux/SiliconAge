package com.sc.finale;

import com.sc.arena.ItemArenaCoordinatorSC;
import com.sc.lore.ItemJournalSC;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Подложка Ф5 (docs/plan-substrate-tech-f5.md §2.2): the finale's items, registered from ModItems.init (preInit, after RelicItemsSC).
 * Registry names are what worlds keep - fixed here:
 * <ul>
 * <li>"arenaCoordinator" - ItemArenaCoordinatorSC (package A): «Координатор арены», points to the nearest Breakdown Epicentre;</li>
 * <li>"journalSC" - ItemJournalSC (package J): the twelve Lithographer's Journals, meta = number - 1 (JournalSC);</li>
 * <li>"paintScheme" - ItemPaintSchemeSC (package P): «Схема окраски» «Литограф» / «Наладчик» (FinaleRulesSC.PAINT_*);</li>
 * <li>"lithoModule" - ItemLithoModuleSC (package P): «Модуль Литографов», a Field Generator's upgrade slot - upkeep x0.5;</li>
 * <li>"navRecord" - ItemNavRecordSC (package P): «Навигационная запись Si-14» - opens the Substrate to its user under requireSignal.</li>
 * </ul>
 * The Breakdown Key and the Breakdown Matrix are the relics' metas 3 and 4 (ItemRelicSC). Each has its TooltipSC tooltip and an
 * NEI «how to get» page (package D). Shared core: only the coordinator edits this file.
 */
public final class FinaleItemsSC {

    public static Item arenaCoordinator, journal, paintScheme, lithoModule, navRecord;

    private FinaleItemsSC() {
    }

    public static void init() {
        arenaCoordinator = new ItemArenaCoordinatorSC();
        GameRegistry.registerItem(arenaCoordinator, "arenaCoordinator");
        journal = new ItemJournalSC();
        GameRegistry.registerItem(journal, "journalSC");
        paintScheme = new ItemPaintSchemeSC();
        GameRegistry.registerItem(paintScheme, "paintScheme");
        lithoModule = new ItemLithoModuleSC();
        GameRegistry.registerItem(lithoModule, "lithoModule");
        navRecord = new ItemNavRecordSC();
        GameRegistry.registerItem(navRecord, "navRecord");
    }

    /** `n` Arena Coordinators (null before init). */
    public static ItemStack arenaCoordinator(int n) {
        return arenaCoordinator == null ? null : new ItemStack(arenaCoordinator, n, 0);
    }

    /** Journal №`number` (1..12; null before init or for another number). */
    public static ItemStack journal(int number) {
        return journal == null || number < 1 || number > com.sc.lore.JournalSC.COUNT ? null
                : new ItemStack(journal, 1, com.sc.lore.JournalSC.metaOf(number));
    }

    /** The paint scheme of meta `meta` (FinaleRulesSC.PAINT_*; null before init). */
    public static ItemStack paintScheme(int meta, int n) {
        return paintScheme == null ? null : new ItemStack(paintScheme, n, meta);
    }

    public static ItemStack lithoModule(int n) {
        return lithoModule == null ? null : new ItemStack(lithoModule, n, 0);
    }

    public static ItemStack navRecord(int n) {
        return navRecord == null ? null : new ItemStack(navRecord, n, 0);
    }

    /** `s` is a Lithographers' Module (the field generator's upgrade slot asks). */
    public static boolean isLithoModule(ItemStack s) {
        return s != null && lithoModule != null && s.getItem() == lithoModule;
    }
}
