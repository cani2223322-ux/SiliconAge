package com.sc.manual;

import java.util.List;

import com.sc.arena.ArenaBlocksSC;
import com.sc.arena.ArenaLayoutSC;
import com.sc.arena.ArenaSC;
import com.sc.arena.ArenaWorldSC;
import com.sc.arena.ItemArenaCoordinatorSC;
import com.sc.dim.SubstrateConfigSC;
import com.sc.dim.SubstrateTerrainSC;
import com.sc.emp.EmpModule;
import com.sc.emp.EmpMobGunSC;
import com.sc.finale.FinaleItemsSC;
import com.sc.finale.FinaleRulesSC;
import com.sc.init.ModBlocks;
import com.sc.init.ModItems;
import com.sc.lore.JournalSC;
import com.sc.lore.SignalRulesSC;
import com.sc.lore.SignalSC;
import com.sc.lore.WreckLayoutSC;
import com.sc.lore.WreckLootSC;
import com.sc.mob.EntityBreakdownWardenSC;
import com.sc.mob.EntityContainmentCoilSC;
import com.sc.mob.EntityHollowShellSC;
import com.sc.mob.EntityMarauderSC;
import com.sc.mob.EntityTrooperSC;
import com.sc.mob.MobDropsSC;
import com.sc.mob.MobEntitiesSC;
import com.sc.mob.TrophyTableSC;
import com.sc.mob.WardenRulesSC;
import com.sc.ruin.ItemRelicSC;
import com.sc.ruin.RelicItemsSC;
import com.sc.settle.CrumbLimitSC;
import com.sc.settle.NpcKindSC;
import com.sc.settle.SettlementItemsSC;
import com.sc.settle.caravan.CaravanRulesSC;
import com.sc.settle.caravan.CaravanRunPathSC;
import com.sc.settle.caravan.EntityCaravanGuardSC;
import com.sc.settle.caravan.EntityCaravanMasterSC;
import com.sc.util.ArmorSuit;
import com.sc.util.BladeType;
import com.sc.util.ConfigSC;
import com.sc.util.SingularLevel;
import com.sc.util.SingularScheme;
import com.sc.util.SiliconMaterial;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/**
 * Подложка Ф5 «Финал» (docs/plan-substrate-tech-f5.md §11.4, package D): the thirteen articles BookWorldSC.add puts at the end of the
 * WORLD chapter (BookWorldSC.F5_ARTICLES, in this order) - «Литографы» (the lore of proposal §1 / §10: who they were, «Пробой», the
 * three forces, the bridges, the journals as the source - no spoiler of №12), «Эпицентр Пробоя» (ArenaLayoutSC: where, the pit, the
 * pedestals and columns; the Coordinator, the Key, the Console, Peaceful, the reset - ArenaWorldSC, WardenRulesSC), «Смотритель
 * Пробоя» (WardenRulesSC: the health by players, the three phases with every number, the Containment Coil's own bestiary lines),
 * «Добыча Смотрителя» (the share, the cooldown, the guaranteed, the scheme, the trophies, the Journal №12), «Журналы Литографа»
 * (JournalSC's pools and chances), «Обломки моста Литографов» (WreckLayoutSC, package J's chest by name), «Сигнал Литографов»
 * (SignalRulesSC), «Караваны» (CaravanRulesSC), «Схемы окраски и купол» (SingularScheme L / M, FinaleRulesSC, preset 24), «Модуль
 * Литографов» (FinaleRulesSC.upkeep), «Навигационная запись Si-14» (requireSignal, the recipe), «Сингулярный прогресс в Подложке»
 * (SingularLevel's fourth tasks, C_KILLS) and «Небо Подложки» (customSky, ShadersModCore).
 * <p>
 * Every number from the code: the core's classes and the packages' own (WreckLootSC, SignalSC, CaravanRunPathSC, EntityCaravanMasterSC,
 * ItemArenaCoordinatorSC, ArenaWorldSC) directly. G on the arena blocks, the Coordinator, the Key, the Matrix, the twelve journals, the
 * two schemes, the Module, the Record, the Burnt Coil and the boss's egg opens these pages. Plain data: safe on a server (no client
 * class touched).
 */
final class BookFinaleSC {

    /** The HP table's rows: the players counted. */
    private static final int HP_ROWS = 4;
    /** The Module's table: upkeeps (EU/t) without it. */
    private static final int[] UPKEEPS = {1, 7, 40, 100, 1000};

    private BookFinaleSC() {
    }

    static void add(List<BookEntry> list, BookChapter c) {
        list.add(lithographers(c));
        list.add(arena(c));
        list.add(warden(c));
        list.add(wardenLoot(c));
        list.add(journals(c));
        list.add(wrecks(c));
        list.add(signal(c));
        list.add(caravans(c));
        list.add(cosmetics(c));
        list.add(lithoModule(c));
        list.add(navRecord(c));
        list.add(progress(c));
        list.add(sky(c));
    }

    // ------------------------------------------------------------------ «Литографы» (ПЛ-1)

    private static BookEntry lithographers(BookChapter c) {
        ItemStack metal = BookWorldSC.ruin(com.sc.block.BlockRuinSC.RUIN_METAL);
        BookEntry e = new BookEntry("world.lithographers", c, metal != null ? metal : BookWorldSC.icon(), Lang.tr("sc.manual.world.lithographers.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.lithographers.title"))).addAll(BookContent.paras("sc.manual.world.lithographers"));

        // the three forces that stayed (proposal §1)
        e.add(BookEl.head(Lang.tr("sc.manual.world.lithographers.forceshead")));
        e.add(BookEl.item(BookWorldSC.egg(MobEntitiesSC.ID_TUNER), Lang.tr("sc.manual.world.lithographers.tuners"),
                Lang.tr("sc.manual.world.lithographers.tuners.desc")));
        e.add(BookEl.item(BookWorldSC.egg(MobEntitiesSC.ID_HOLLOW_SHELL), Lang.tr("sc.manual.world.lithographers.shells"),
                Lang.tr("sc.manual.world.lithographers.shells.desc", BookWorldSC.mobName(EntityHollowShellSC.NAME))));
        e.add(BookEl.item(BookWorldSC.egg(MobEntitiesSC.ID_MARAUDER), Lang.tr("sc.manual.world.lithographers.renegades"),
                Lang.tr("sc.manual.world.lithographers.renegades.desc")));

        // what is left of them, and where to read it
        e.add(BookEl.head(Lang.tr("sc.manual.world.lithographers.lefthead")));
        e.add(BookEl.item(metal, Lang.tr("sc.manual.world.ruins.title"), Lang.tr("sc.manual.world.lithographers.left.ruins")));
        e.add(BookEl.item(BookWorldSC.ruin(BookWorldSC.RUIN_BURNT_COIL), Lang.tr("sc.manual.world.wrecks.title"), Lang.tr("sc.manual.world.lithographers.left.wrecks")));
        e.add(BookEl.item(ArenaBlocksSC.stack(ArenaBlocksSC.CONSOLE, 1), Lang.tr("sc.manual.world.arena.title"), Lang.tr("sc.manual.world.lithographers.left.arena")));
        e.add(BookEl.item(FinaleItemsSC.journal(1), Lang.tr("sc.manual.world.journals.title"), Lang.tr("sc.manual.world.lithographers.left.journals",
                JournalSC.COUNT)));
        e.add(BookEl.dim(Lang.tr("sc.manual.world.lithographers.note")));
        e.add(BookEl.link("world.journals", Lang.tr("sc.manual.world.journals.title")));
        e.add(BookEl.link("world.wrecks", Lang.tr("sc.manual.world.wrecks.title")));
        e.add(BookEl.link("world.signal", Lang.tr("sc.manual.world.signal.title")));
        e.add(BookEl.link("world.arena", Lang.tr("sc.manual.world.arena.title")));
        e.add(BookEl.link("world.ruins", Lang.tr("sc.manual.world.ruins.title")));
        return e;
    }

    // ------------------------------------------------------------------ «Эпицентр Пробоя» (ПУ-8..ПУ-12, ПС-56)

    private static BookEntry arena(BookChapter c) {
        ItemStack console = ArenaBlocksSC.stack(ArenaBlocksSC.CONSOLE, 1), key = RelicItemsSC.relic(ItemRelicSC.BREAKDOWN_KEY, 1),
                fragment = RelicItemsSC.relic(ItemRelicSC.KEY_FRAGMENT, 1), coordinator = FinaleItemsSC.arenaCoordinator(1);
        BookEntry e = new BookEntry("world.arena", c, console != null ? console : key != null ? key : BookWorldSC.icon(), Lang.tr("sc.manual.world.arena.title"));
        int region = SubstrateTerrainSC.GRID * ArenaLayoutSC.REGION, spawnFar = (int) (Math.floor(Math.sqrt(2D) * ArenaLayoutSC.SPAWN_REGION_FROM
                * SubstrateTerrainSC.GRID / 100D) * 100D), apart = (ArenaLayoutSC.REGION - ArenaLayoutSC.SPAN + 1) * SubstrateTerrainSC.GRID;
        e.add(BookEl.title(Lang.tr("sc.manual.world.arena.title"))).addAll(BookContent.paras("sc.manual.world.arena",
                new Object[]{region, region, ArenaLayoutSC.MARGIN, ArenaLayoutSC.MARGIN + ArenaLayoutSC.SPAN - 1, apart},
                new Object[]{ArenaLayoutSC.SPAWN_REGION_FROM, ArenaLayoutSC.MARGIN + ArenaLayoutSC.SPAN - 1, spawnFar},
                new Object[]{ArenaLayoutSC.PIT_R, ArenaLayoutSC.RIM_R, ArenaLayoutSC.WALL_H},
                new Object[]{ArenaLayoutSC.COILS, ArenaLayoutSC.COIL_R, ArenaLayoutSC.COLUMNS, ArenaLayoutSC.COLUMN_H, ArenaLayoutSC.COLUMN_R, ArenaLayoutSC.LAMPS}));
        if (!ArenaSC.generates()) {
            e.add(BookEl.warn(Lang.tr("sc.manual.world.arena.off")));
        }
        e.add(BookEl.dim(Lang.tr("sc.manual.world.arena.old")));

        // finding it (ПУ-12): the Coordinator - the Cartographer's deal, an elite's 2%
        e.add(BookEl.head(Lang.tr("sc.manual.world.arena.findhead"), coordinator));
        String seller = BookWorldSC.sellerLine(coordinator);
        e.add(BookEl.item(coordinator, coordinator != null ? coordinator.getDisplayName() : Lang.tr("item.siliconage.arenaCoordinator.name"),
                Lang.tr("sc.manual.world.arena.coordinator", ItemArenaCoordinatorSC.SEARCH_REGIONS * 2 + 1, ItemArenaCoordinatorSC.SEARCH_REGIONS * 2 + 1) + " "
                        + (seller != null ? seller : Lang.tr("sc.manual.world.arena.coordinator.seller", Lang.tr(NpcKindSC.CARTOGRAPHER.langKey()))) + " "
                        + Lang.tr("sc.manual.world.arena.coordinator.elite", BookWorldSC.pct(MobDropsSC.ARENA_COORDINATOR_CHANCE))));

        // the Breakdown Key (ПУ-11): four Fragments, shapeless
        e.add(BookEl.head(Lang.tr("sc.manual.world.arena.keyhead"), key));
        e.add(BookEl.item(fragment, BookWorldSC.relicName(ItemRelicSC.KEY_FRAGMENT), BookWorldSC.relicLine(ItemRelicSC.KEY_FRAGMENT)));
        e.add(BookEl.item(key, BookWorldSC.relicName(ItemRelicSC.BREAKDOWN_KEY), BookWorldSC.relicLine(ItemRelicSC.BREAKDOWN_KEY)));
        if (key != null) {
            BookContent.crafts(e, key);
        }

        // the Console (ПУ-11): Peaceful, the key, the countdown, a fight on
        e.add(BookEl.head(Lang.tr("sc.manual.world.arena.consolehead"), console));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.arena.peaceful"), Lang.tr("sc.manual.world.arena.peaceful.desc")));
        e.add(BookEl.item(key, Lang.tr("sc.manual.world.arena.insert"), Lang.tr("sc.manual.world.arena.insert.desc", BookWorldSC.sec(ArenaWorldSC.COUNTDOWN_TICKS),
                WardenRulesSC.SCALE_RADIUS)));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.arena.busy"), Lang.tr("sc.manual.world.arena.busy.desc")));

        // the reset (ПС-56): all away 5 min - the key stays in; a Warden gone without a death - the same
        e.add(BookEl.head(Lang.tr("sc.manual.world.arena.resethead")));
        e.addAll(BookContent.paras("sc.manual.world.arena.reset", new Object[]{WardenRulesSC.SCALE_RADIUS, BookWorldSC.minutes(WardenRulesSC.RESET_AWAY_TICKS)}));

        // the blocks (ПУ-10): unbreakable, hidden in NEI, AE2's Spatial IO leaves them
        e.add(BookEl.head(Lang.tr("sc.manual.world.arena.blockshead")));
        for (int m = 0; m < ArenaBlocksSC.NAMES.length; m++) {
            e.add(BookEl.item(ArenaBlocksSC.stack(m, 1), arenaName(m), Lang.tr("sc.manual.world.arena.block." + ArenaBlocksSC.NAMES[m])));
        }
        e.add(BookEl.dim(Lang.tr("sc.manual.world.arena.blocks.note")));
        e.add(BookEl.link("world.warden", BookWorldSC.mobName(EntityBreakdownWardenSC.NAME)));
        e.add(BookEl.link("world.wardenLoot", Lang.tr("sc.manual.world.wardenLoot.title")));
        e.add(BookEl.link("world.vaults", Lang.tr("sc.manual.world.vaults.title")));
        e.add(BookEl.link("world.ruins", Lang.tr("sc.manual.world.ruins.title")));
        for (int m = 0; m < ArenaBlocksSC.NAMES.length; m++) {
            e.about(ArenaBlocksSC.stack(m, 1));
        }
        e.about(coordinator, key);
        return e;
    }

    /** An arena block's name (tile.siliconage.arena.<name>.name). */
    static String arenaName(int meta) {
        ItemStack s = ArenaBlocksSC.stack(meta, 1);
        return s != null ? s.getDisplayName() : Lang.tr("tile.siliconage.arena." + ArenaBlocksSC.nameOf(meta) + ".name");
    }

    // ------------------------------------------------------------------ «Смотритель Пробоя» and its coils (ПС-50..ПС-58)

    private static BookEntry warden(BookChapter c) {
        String name = BookWorldSC.mobName(EntityBreakdownWardenSC.NAME), coil = BookWorldSC.mobName(EntityContainmentCoilSC.NAME);
        ItemStack egg = BookWorldSC.egg(MobEntitiesSC.ID_BREAKDOWN_WARDEN), chest = new ItemStack(ModItems.ARMOR.get(ArmorSuit.SINGULAR)[1]),
                gun = new ItemStack(ModItems.empGun, 1, com.sc.emp.ItemEmpGunSC.META_UPGRADED), pedestal = ArenaBlocksSC.stack(ArenaBlocksSC.PEDESTAL, 1),
                column = ArenaBlocksSC.stack(ArenaBlocksSC.COLUMN, 1);
        BookEntry e = new BookEntry("world.warden", c, egg != null ? egg : chest, name);
        e.add(BookEl.title(name)).addAll(BookContent.paras("sc.manual.world.warden", new Object[]{name, n(WardenRulesSC.HEIGHT)},
                new Object[]{n((float) WardenRulesSC.BASE_HP), BookWorldSC.pct((float) WardenRulesSC.PER_PLAYER), WardenRulesSC.SCALE_RADIUS}));

        // the health by the players counted (ПС-50): WardenRulesSC.maxHealth
        BookEl t = BookEl.table(Lang.tr("sc.manual.world.t.players"), Lang.tr("sc.manual.world.t.hp"));
        for (int p = 1; p <= HP_ROWS; p++) {
            t.row(null, String.valueOf(p), n(WardenRulesSC.maxHealth(p)));
        }
        e.add(t);
        e.add(BookEl.dim(Lang.tr("sc.manual.world.warden.hp.note")));
        e.add(BookEl.item(chest, Lang.tr("sc.manual.world.t.armour"), Lang.tr("sc.manual.world.warden.armour", BookWorldSC.suitName(ArmorSuit.SINGULAR),
                n(WardenRulesSC.RESERVE_MIN), n(WardenRulesSC.RESERVE_MAX), n(WardenRulesSC.REGEN_PER_SECOND), BookWorldSC.sec(WardenRulesSC.REGEN_DELAY_TICKS))));
        e.add(BookEl.item(BookWorldSC.blade(BladeType.SINGULAR), Lang.tr("sc.manual.world.t.weapon"), Lang.tr("sc.manual.world.warden.weapon",
                BookWorldSC.blade(BladeType.SINGULAR).getDisplayName(), n(WardenRulesSC.BLADE_DAMAGE))));
        e.add(BookEl.item(gun, gun.getDisplayName(), EmpMobGunSC.allowed() ? Lang.tr("sc.manual.world.warden.pe1m.on")
                : Lang.tr("sc.manual.world.warden.pe1m.off")));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.warden.rules"), Lang.tr("sc.manual.world.warden.rules.desc", WardenRulesSC.LEASH)));

        // phase 1 «Удержание» (ПС-52): the coils, the window, the waves
        e.add(BookEl.head(Lang.tr("sc.manual.world.warden.p1head", BookWorldSC.pct(WardenRulesSC.P2_AT)), pedestal));
        e.add(BookEl.item(pedestal, Lang.tr("sc.manual.world.warden.coils"), Lang.tr("sc.manual.world.warden.coils.desc", WardenRulesSC.COILS, coil,
                BookWorldSC.sec(WardenRulesSC.COIL_OFF_TICKS), n(WardenRulesSC.COIL_RESERVE))));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.warden.window"), Lang.tr("sc.manual.world.warden.window.desc", BookWorldSC.sec(WardenRulesSC.WINDOW_TICKS),
                BookWorldSC.ironPoints())));
        e.add(BookEl.item(BookWorldSC.egg(MobEntitiesSC.ID_HOLLOW_SHELL), Lang.tr("sc.manual.world.warden.waves"), Lang.tr("sc.manual.world.warden.waves.desc",
                BookWorldSC.sec(WardenRulesSC.WAVE_EVERY), WardenRulesSC.WAVE_SHELLS, BookWorldSC.mobName(EntityHollowShellSC.NAME), WardenRulesSC.WAVE_MAX)));

        // phase 2 «Пробой» (ПС-53): the highlight, the press, «Импульс глушения», the cracks, the swarms
        e.add(BookEl.head(Lang.tr("sc.manual.world.warden.p2head", BookWorldSC.pct(WardenRulesSC.P2_AT), BookWorldSC.pct(WardenRulesSC.P3_AT)), gun));
        e.add(BookEl.item(chest, Lang.tr("sc.manual.world.warden.highlight"), Lang.tr("sc.manual.world.warden.highlight.desc",
                BookWorldSC.sec(WardenRulesSC.HIGHLIGHT_EVERY), BookWorldSC.sec(WardenRulesSC.HIGHLIGHT_TICKS), n(WardenRulesSC.HIGHLIGHT_MUL),
                n(WardenRulesSC.HIGHLIGHT_RESERVE), BookWorldSC.sec(WardenRulesSC.SET_TICKS))));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.sentinel.press"), Lang.tr("sc.manual.world.sentinel.press.desc", BookWorldSC.sec(WardenRulesSC.PRESS_EVERY),
                BookWorldSC.sec(WardenRulesSC.PRESS_HUM), WardenRulesSC.PRESS_RADIUS, BookWorldSC.sec(WardenRulesSC.PRESS_TICKS))));
        e.add(BookEl.item(gun, Lang.tr("sc.manual.world.warden.silence"), EmpMobGunSC.allowed() ? Lang.tr("sc.manual.world.warden.silence.desc",
                BookWorldSC.sec(WardenRulesSC.SILENCE_EVERY), BookWorldSC.sec(WardenRulesSC.SILENCE_HUM), WardenRulesSC.SILENCE_RADIUS, ConfigSC.empImmunitySeconds)
                : Lang.tr("sc.manual.world.warden.silence.off")));
        e.add(BookEl.item(BookWorldSC.wild(com.sc.dim.SubstrateWildBlocksSC.vent, com.sc.dim.SubstrateWildBlocksSC.ARC_CRACK), Lang.tr("sc.manual.world.warden.cracks"),
                Lang.tr("sc.manual.world.warden.cracks.desc", BookWorldSC.sec(WardenRulesSC.CRACK_EVERY), WardenRulesSC.CRACKS_MAX,
                        BookWorldSC.sec(WardenRulesSC.SWARM_EVERY))));

        // phase 3 «Перегрузка» (ПС-54): no shield, the heat, the core reset
        e.add(BookEl.head(Lang.tr("sc.manual.world.warden.p3head", BookWorldSC.pct(WardenRulesSC.P3_AT)), column));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.warden.overheat"), Lang.tr("sc.manual.world.warden.overheat.desc",
                BookWorldSC.sec(WardenRulesSC.OVERHEAT_EVERY), BookWorldSC.sec(WardenRulesSC.OVERHEAT_TICKS))));
        e.add(BookEl.item(column, Lang.tr("sc.manual.world.warden.reset"), Lang.tr("sc.manual.world.warden.reset.desc", BookWorldSC.sec(WardenRulesSC.FINISH_TICKS),
                BookWorldSC.sec(WardenRulesSC.CORE_RESET_WARN), n(WardenRulesSC.CORE_RESET_DAMAGE), WardenRulesSC.CORE_RESET_RADIUS)));

        // the Containment Coil (ПС-58): its own lines of the bestiary
        e.add(BookEl.head(coil, pedestal));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.t.what"), Lang.tr("sc.manual.world.warden.coil.what")));
        e.add(BookEl.item(gun, Lang.tr("sc.manual.world.warden.coil.states"), Lang.tr("sc.manual.world.warden.coil.states.desc",
                BookWorldSC.sec(WardenRulesSC.COIL_OFF_TICKS), n(WardenRulesSC.COIL_RESERVE))));
        e.addAll(BookContent.paras("sc.manual.world.warden.tips"));
        e.add(BookEl.link("world.arena", Lang.tr("sc.manual.world.arena.title")));
        e.add(BookEl.link("world.wardenLoot", Lang.tr("sc.manual.world.wardenLoot.title")));
        e.add(BookEl.link("world.mobemp", Lang.tr("sc.manual.world.mobemp.title")));
        e.add(BookEl.link("world.elites", Lang.tr("sc.manual.world.elites.title")));
        e.about(egg);
        return e;
    }

    // ------------------------------------------------------------------ «Добыча Смотрителя» (ПТ-21..ПТ-24)

    private static BookEntry wardenLoot(BookChapter c) {
        ItemStack matrix = RelicItemsSC.relic(ItemRelicSC.BREAKDOWN_MATRIX, 1), paint = FinaleItemsSC.paintScheme(FinaleRulesSC.PAINT_LITHOGRAPHER, 1);
        String name = BookWorldSC.mobName(EntityBreakdownWardenSC.NAME);
        BookEntry e = new BookEntry("world.wardenLoot", c, matrix != null ? matrix : new ItemStack(ModItems.singularCrumb), Lang.tr("sc.manual.world.wardenLoot.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.wardenLoot.title"))).addAll(BookContent.paras("sc.manual.world.wardenLoot",
                new Object[]{name, BookWorldSC.pct(WardenRulesSC.LOOT_SHARE), WardenRulesSC.LOOT_COOLDOWN_DAYS}, null));

        // everybody's own (ПТ-22, ПТ-23)
        e.add(BookEl.head(Lang.tr("sc.manual.world.wardenLoot.eachhead")));
        e.add(BookEl.item(matrix, BookWorldSC.relicName(ItemRelicSC.BREAKDOWN_MATRIX), Lang.tr("sc.manual.world.wardenLoot.matrix", WardenRulesSC.MATRIX,
                Lang.tr(NpcKindSC.ELDER.langKey()))));
        e.add(BookEl.item(new ItemStack(ModItems.singularCrumb), new ItemStack(ModItems.singularCrumb).getDisplayName(), Lang.tr("sc.manual.world.wardenLoot.crumbs",
                span(WardenRulesSC.CRUMBS_MIN, WardenRulesSC.CRUMBS_MAX), CrumbLimitSC.DAY_CAP)));
        e.add(BookEl.item(ModItems.singularClot == null ? null : new ItemStack(ModItems.singularClot), BookWorldSC.clotName(),
                Lang.tr("sc.manual.world.wardenLoot.count", span(WardenRulesSC.CLOTS_MIN, WardenRulesSC.CLOTS_MAX))));
        e.add(BookEl.item(BookWorldSC.loot(BookWorldSC.LOOT_SINGULAR_SCRAP), BookWorldSC.lootName(BookWorldSC.LOOT_SINGULAR_SCRAP),
                Lang.tr("sc.manual.world.wardenLoot.count", span(WardenRulesSC.SCRAP_MIN, WardenRulesSC.SCRAP_MAX))));
        e.add(BookEl.item(paint, paintName(FinaleRulesSC.PAINT_LITHOGRAPHER), Lang.tr("sc.manual.world.wardenLoot.paint", BookWorldSC.pct(WardenRulesSC.PAINT_CHANCE))));
        e.add(BookEl.item(BookWorldSC.trophy(TrophyTableSC.META_PE1M), Lang.tr("sc.manual.world.t.trophies"), Lang.tr("sc.manual.world.wardenLoot.trophies",
                BookWorldSC.wardenTrophyPct(WardenRulesSC.TROPHY_CHEST), BookWorldSC.wardenTrophyPct(WardenRulesSC.TROPHY_BLADE),
                BookWorldSC.wardenTrophyPct(WardenRulesSC.TROPHY_PE1M))));
        if (!EmpMobGunSC.allowed()) {
            e.add(BookEl.dim(Lang.tr("sc.manual.world.wardenLoot.nope1m")));
        }
        e.add(BookEl.item(FinaleItemsSC.journal(WardenRulesSC.FIRST_KILL_JOURNAL), Lang.tr("sc.manual.world.wardenLoot.first"),
                Lang.tr("sc.manual.world.wardenLoot.first.desc", WardenRulesSC.FIRST_KILL_JOURNAL)));
        e.add(BookEl.item(new ItemStack(ModItems.ARMOR.get(ArmorSuit.SINGULAR)[1]), Lang.tr("sc.manual.world.wardenLoot.progress"),
                Lang.tr("sc.manual.world.wardenLoot.progress.desc", WardenRulesSC.PROGRESS_POINTS, Lang.tr("sc.levelgui.task.5.3"))));

        // the delivery (ПТ-21): offline, dead, elsewhere - it waits
        e.add(BookEl.head(Lang.tr("sc.manual.world.wardenLoot.deliveryhead")));
        e.addAll(BookContent.paras("sc.manual.world.wardenLoot.delivery"));
        e.add(BookEl.dim(Lang.tr("sc.manual.world.wardenLoot.egg")));
        e.add(BookEl.link("world.warden", name));
        e.add(BookEl.link("world.lithoModule", Lang.tr("sc.manual.world.lithoModule.title")));
        e.add(BookEl.link("world.cosmetics", Lang.tr("sc.manual.world.cosmetics.title")));
        e.add(BookEl.link("world.trophies", Lang.tr("sc.manual.world.trophies.title")));
        e.add(BookEl.link("world.crumbs", Lang.tr("sc.manual.world.crumbs.title")));
        e.about(matrix);
        return e;
    }

    // ------------------------------------------------------------------ «Журналы Литографа» (ПЛ-1, ПЛ-2)

    private static BookEntry journals(BookChapter c) {
        ItemStack first = FinaleItemsSC.journal(1);
        BookEntry e = new BookEntry("world.journals", c, first != null ? first : new ItemStack(Items.book), Lang.tr("sc.manual.world.journals.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.journals.title"))).addAll(BookContent.paras("sc.manual.world.journals",
                new Object[]{JournalSC.COUNT}, null));

        // where each number is found: JournalSC's pools and chances
        BookEl t = BookEl.table(Lang.tr("sc.manual.world.t.where"), Lang.tr("sc.manual.world.t.numbers"), Lang.tr("sc.manual.world.t.chance"));
        ItemStack[] icons = {BookWorldSC.ruin(com.sc.block.BlockRuinSC.MACHINE_HUSK), BookWorldSC.ruin(com.sc.block.BlockRuinSC.BURNT_PANEL),
                BookWorldSC.ruin(BookWorldSC.RUIN_VAULT_PLATING), BookWorldSC.egg(MobEntitiesSC.ID_SENTINEL), BookWorldSC.ruin(BookWorldSC.RUIN_BURNT_COIL),
                new ItemStack(Blocks.beacon), BookWorldSC.egg(MobEntitiesSC.ID_BREAKDOWN_WARDEN)};
        for (int src = 0; src < JournalSC.POOLS.length; src++) {
            t.row(src < icons.length ? icons[src] : null, BookWorldSC.journalSource(src), BookWorldSC.journalNumbers(src), BookWorldSC.journalChance(src));
        }
        e.add(t);
        e.addAll(BookContent.paras("sc.manual.world.journals.read"));
        e.add(BookEl.link("world.lithographers", Lang.tr("sc.manual.world.lithographers.title")));
        e.add(BookEl.link("world.ruins", Lang.tr("sc.manual.world.ruins.title")));
        e.add(BookEl.link("world.vaults", Lang.tr("sc.manual.world.vaults.title")));
        e.add(BookEl.link("world.wrecks", Lang.tr("sc.manual.world.wrecks.title")));
        e.add(BookEl.link("world.signal", Lang.tr("sc.manual.world.signal.title")));
        e.add(BookEl.link("world.wardenLoot", Lang.tr("sc.manual.world.wardenLoot.title")));
        for (int n = 1; n <= JournalSC.COUNT; n++) {
            e.about(FinaleItemsSC.journal(n));
        }
        return e;
    }

    // ------------------------------------------------------------------ «Обломки моста Литографов» (ПЛ-3, ПЛ-4)

    private static BookEntry wrecks(BookChapter c) {
        ItemStack coil = BookWorldSC.ruin(BookWorldSC.RUIN_BURNT_COIL);
        BookEntry e = new BookEntry("world.wrecks", c, coil != null ? coil : BookWorldSC.icon(), Lang.tr("sc.manual.world.wrecks.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.wrecks.title"))).addAll(BookContent.paras("sc.manual.world.wrecks",
                new Object[]{WreckLayoutSC.CHANCE_PCT, WreckLayoutSC.OFFSET},
                new Object[]{WreckLayoutSC.RING_MIN, WreckLayoutSC.RING_MAX, WreckLayoutSC.TILT_MAX}));
        if (!ArenaSC.generates()) {
            e.add(BookEl.warn(Lang.tr("sc.manual.world.arena.off")));
        }
        e.add(BookEl.dim(Lang.tr("sc.manual.world.wrecks.old")));
        e.add(BookEl.item(coil, coil != null ? coil.getDisplayName() : Lang.tr("tile.siliconage.ruin." + BookWorldSC.RUIN_F5_NAME + ".name"),
                BookWorldSC.ruinLine(BookWorldSC.RUIN_BURNT_COIL)));
        e.add(BookEl.item(new ItemStack(Blocks.chest), Lang.tr("sc.manual.world.ruins.chests"), Lang.tr("sc.manual.world.wrecks.loot",
                BookWorldSC.journalNumbers(JournalSC.SRC_WRECK), BookWorldSC.lootName(BookWorldSC.LOOT_NANO_SCRAP),
                span(WreckLootSC.SCRAP_MIN, WreckLootSC.SCRAP_MAX), BookWorldSC.lootName(BookWorldSC.LOOT_FIRMWARE),
                span(WreckLootSC.FIRMWARE_MIN, WreckLootSC.FIRMWARE_MAX), BookWorldSC.pct(WreckLootSC.COMPONENT_CHANCE))));
        e.add(BookEl.link("world.lithographers", Lang.tr("sc.manual.world.lithographers.title")));
        e.add(BookEl.link("world.journals", Lang.tr("sc.manual.world.journals.title")));
        e.add(BookEl.link("world.ruins", Lang.tr("sc.manual.world.ruins.title")));
        e.about(coil);
        return e;
    }

    // ------------------------------------------------------------------ «Сигнал Литографов» (ПЛ-5..ПЛ-8)

    private static BookEntry signal(BookChapter c) {
        BookEntry e = new BookEntry("world.signal", c, new ItemStack(Blocks.beacon), Lang.tr("sc.manual.world.signal.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.signal.title"))).addAll(BookContent.paras("sc.manual.world.signal",
                new Object[]{SignalRulesSC.CHANCE_PCT, BookWorldSC.clock(SignalRulesSC.START_MIN), BookWorldSC.clock(SignalRulesSC.START_MAX),
                        BookWorldSC.minutes(SignalRulesSC.DURATION_TICKS)},
                new Object[]{SignalRulesSC.DIST_MIN, SignalRulesSC.DIST_MAX, SignalRulesSC.BEAM_TOP, SignalRulesSC.BEAM_VIEW},
                new Object[]{SignalSC.END_TELL}));

        // the cache and its patrol (ПЛ-7)
        e.add(BookEl.head(Lang.tr("sc.manual.world.signal.cachehead"), new ItemStack(Blocks.chest)));
        e.add(BookEl.item(new ItemStack(Blocks.chest), Lang.tr("sc.manual.world.signal.cache"), Lang.tr("sc.manual.world.signal.cache.desc",
                SignalRulesSC.CACHE_REACH, span(SignalRulesSC.CHIPS_MIN, SignalRulesSC.CHIPS_MAX), material(SiliconMaterial.MEMORY_CHIP),
                span(SignalRulesSC.CONTROLLERS_MIN, SignalRulesSC.CONTROLLERS_MAX), material(SiliconMaterial.CONTROLLER),
                BookWorldSC.relicName(ItemRelicSC.SEALED_CRUMB), CrumbLimitSC.DAY_CAP, BookWorldSC.journalNumbers(JournalSC.SRC_SIGNAL))));
        e.add(BookEl.item(BookWorldSC.egg(MobEntitiesSC.ID_HOLLOW_SHELL), Lang.tr("sc.manual.world.signal.patrol"), Lang.tr("sc.manual.world.signal.patrol.desc",
                SignalRulesSC.PATROL_SHELLS, BookWorldSC.mobName(EntityHollowShellSC.NAME))));
        e.addAll(BookContent.paras("sc.manual.world.signal.tips"));
        e.add(BookEl.link("world.lithographers", Lang.tr("sc.manual.world.lithographers.title")));
        e.add(BookEl.link("world.journals", Lang.tr("sc.manual.world.journals.title")));
        e.add(BookEl.link("world.ruins", Lang.tr("sc.manual.world.ruins.title")));
        e.add(BookEl.link("world.crumbs", Lang.tr("sc.manual.world.crumbs.title")));
        return e;
    }

    // ------------------------------------------------------------------ «Караваны» (ПН-12..ПН-16)

    private static BookEntry caravans(BookChapter c) {
        ItemStack tuner = BookWorldSC.egg(MobEntitiesSC.ID_TUNER), guard = BookWorldSC.egg(MobEntitiesSC.ID_WARDEN),
                wide = ModItems.empModule == null ? null : ModItems.empModule.stackOf(EmpModule.WIDE);
        BookEntry e = new BookEntry("world.caravans", c, tuner != null ? tuner : BookWorldSC.icon(), Lang.tr("sc.manual.world.caravans.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.caravans.title"))).addAll(BookContent.paras("sc.manual.world.caravans",
                new Object[]{CaravanRulesSC.EVERY_DAYS, BookWorldSC.clock(CaravanRulesSC.WINDOW_FROM), BookWorldSC.clock(CaravanRulesSC.WINDOW_TO),
                        CaravanRulesSC.PLAYER_REACH},
                new Object[]{CaravanRulesSC.APPROACH_FROM, CaravanRulesSC.APPROACH_TO, CaravanRulesSC.NEIGHBOUR_MAX},
                new Object[]{CaravanRunPathSC.RING_OUT, CaravanRunPathSC.STEP}));
        if (!SubstrateConfigSC.caravans) {
            e.add(BookEl.warn(Lang.tr("sc.manual.world.caravans.off")));
        }
        e.add(BookEl.item(tuner, BookWorldSC.mobName(EntityCaravanMasterSC.NAME), Lang.tr("sc.manual.world.caravans.master",
                n((float) EntityCaravanMasterSC.MASTER_HP))));
        e.add(BookEl.item(guard, BookWorldSC.mobName(EntityCaravanGuardSC.NAME), Lang.tr("sc.manual.world.caravans.guard", CaravanRulesSC.GUARDS)));

        // the ambush (ПН-14) and the reward (ПН-15)
        e.add(BookEl.head(Lang.tr("sc.manual.world.caravans.ambushhead", CaravanRulesSC.AMBUSH_PCT), BookWorldSC.egg(MobEntitiesSC.ID_MARAUDER)));
        e.add(BookEl.item(BookWorldSC.egg(MobEntitiesSC.ID_MARAUDER), Lang.tr("sc.manual.world.caravans.ambush"), Lang.tr("sc.manual.world.caravans.ambush.desc",
                CaravanRulesSC.AMBUSHERS, BookWorldSC.mobName(EntityMarauderSC.NAME), BookWorldSC.mobName(EntityTrooperSC.NAME), CaravanRulesSC.AMBUSH_DIST)));
        String deal = BookWorldSC.sellerLine(wide);
        e.add(BookEl.item(SettlementItemsSC.chips(1), Lang.tr("sc.manual.world.caravans.reward"), Lang.tr("sc.manual.world.caravans.reward.desc",
                CaravanRulesSC.DEFENDER_REACH, CaravanRulesSC.REWARD_REP)));
        e.add(BookEl.item(wide, wide != null ? wide.getDisplayName() : Lang.tr("item.siliconage.empModule.wide.name"), Lang.tr("sc.manual.world.caravans.deal",
                Lang.tr(NpcKindSC.ARMOURER.langKey()), CaravanRulesSC.RARE_DAYS) + (deal != null ? " " + deal : "")));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.caravans.lost"), Lang.tr("sc.manual.world.caravans.lost.desc")));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.caravans.end"), Lang.tr("sc.manual.world.caravans.end.desc", CaravanRulesSC.ARRIVE_REACH,
                BookWorldSC.minutes(CaravanRulesSC.TIMEOUT_TICKS))));
        e.addAll(BookContent.paras("sc.manual.world.caravans.tips"));
        e.add(BookEl.link("world.settlements", Lang.tr("sc.manual.world.settlements.title")));
        e.add(BookEl.link("world.trust", Lang.tr("sc.manual.world.trust.title")));
        e.add(BookEl.link("world.trade", Lang.tr("sc.manual.world.trade.title")));
        e.add(BookEl.link("world.wardens", Lang.tr("sc.manual.world.wardens.title")));
        return e;
    }

    // ------------------------------------------------------------------ «Схемы окраски и купол» (ПТ-25, §9.3)

    private static BookEntry cosmetics(BookChapter c) {
        ItemStack l = FinaleItemsSC.paintScheme(FinaleRulesSC.PAINT_LITHOGRAPHER, 1), m = FinaleItemsSC.paintScheme(FinaleRulesSC.PAINT_TUNER, 1);
        BookEntry e = new BookEntry("world.cosmetics", c, l != null ? l : new ItemStack(ModItems.ARMOR.get(ArmorSuit.SINGULAR)[0]),
                Lang.tr("sc.manual.world.cosmetics.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.cosmetics.title"))).addAll(BookContent.paras("sc.manual.world.cosmetics",
                new Object[]{SingularScheme.values().length - 2}));

        // the two schemes (L, M) and where they come from
        e.add(BookEl.head(Lang.tr("sc.manual.world.cosmetics.schemeshead"), new ItemStack(ModItems.ARMOR.get(ArmorSuit.SINGULAR)[1])));
        e.add(BookEl.item(l, paintName(FinaleRulesSC.PAINT_LITHOGRAPHER), Lang.tr("sc.manual.world.cosmetics.l", Lang.tr(SingularScheme.L.langKey()),
                BookWorldSC.mobName(EntityBreakdownWardenSC.NAME), BookWorldSC.pct(WardenRulesSC.PAINT_CHANCE))));
        String elder = BookWorldSC.sellerLine(m);
        e.add(BookEl.item(m, paintName(FinaleRulesSC.PAINT_TUNER), Lang.tr("sc.manual.world.cosmetics.m", Lang.tr(SingularScheme.M.langKey())) + " "
                + (elder != null ? elder : Lang.tr("sc.manual.world.cosmetics.m.seller", Lang.tr(NpcKindSC.ELDER.langKey())))));
        e.addAll(BookContent.paras("sc.manual.world.cosmetics.use"));

        // the dome's preset «Бирюза Наладчиков» (PRESETS[24]) - everybody's
        int preset = com.sc.tileentity.TileEntityFieldGeneratorSC.PRESETS.length - 1;
        e.add(BookEl.head(Lang.tr("sc.manual.world.cosmetics.presethead"), new ItemStack(ModBlocks.fieldGeneratorSC)));
        e.add(BookEl.item(new ItemStack(ModBlocks.fieldGeneratorSC), Lang.tr("sc.fieldzone.preset." + preset), Lang.tr("sc.manual.world.cosmetics.preset",
                String.format(java.util.Locale.ROOT, "#%06X", FinaleRulesSC.TUNER_DOME_RGB), preset + 1)));
        e.add(BookEl.link("world.wardenLoot", Lang.tr("sc.manual.world.wardenLoot.title")));
        e.add(BookEl.link("world.trade", Lang.tr("sc.manual.world.trade.title")));
        e.add(BookEl.link("singulararmor.schemes", Lang.tr("sc.manual.singular.schemeshead")));
        e.about(l, m);
        return e;
    }

    /** A paint scheme's name (item.siliconage.paintScheme.<name>.name). */
    static String paintName(int meta) {
        ItemStack s = FinaleItemsSC.paintScheme(meta, 1);
        return s != null ? s.getDisplayName() : Lang.tr("item.siliconage.paintScheme." + FinaleRulesSC.PAINT_NAMES[Math.max(0,
                Math.min(FinaleRulesSC.PAINT_NAMES.length - 1, meta))] + ".name");
    }

    // ------------------------------------------------------------------ «Модуль Литографов» (ПО-20)

    private static BookEntry lithoModule(BookChapter c) {
        ItemStack module = FinaleItemsSC.lithoModule(1);
        BookEntry e = new BookEntry("world.lithoModule", c, module != null ? module : new ItemStack(ModBlocks.fieldGeneratorSC), Lang.tr("sc.manual.world.lithoModule.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.lithoModule.title"))).addAll(BookContent.paras("sc.manual.world.lithoModule",
                new Object[]{BookWorldSC.pct(1F - FinaleRulesSC.UPKEEP_SHARE)}, null));
        BookEl t = BookEl.table(Lang.tr("sc.manual.world.t.upkeep"), Lang.tr("sc.manual.world.t.withModule"));
        for (int u : UPKEEPS) {
            t.row(null, BookContent.num(u), BookContent.num(FinaleRulesSC.upkeep(u, true)));
        }
        e.add(t);
        String elder = BookWorldSC.sellerLine(module);
        e.add(BookEl.item(RelicItemsSC.relic(ItemRelicSC.BREAKDOWN_MATRIX, 1), Lang.tr("sc.manual.world.t.how"), elder != null ? elder
                : Lang.tr("sc.manual.world.lithoModule.seller", Lang.tr(NpcKindSC.ELDER.langKey()), BookWorldSC.relicName(ItemRelicSC.BREAKDOWN_MATRIX))));
        e.add(BookEl.link("world.wardenLoot", Lang.tr("sc.manual.world.wardenLoot.title")));
        e.add(BookEl.link("world.trade", Lang.tr("sc.manual.world.trade.title")));
        e.add(BookEl.link("field.modes", Lang.tr("sc.manual.field.modes")));
        e.about(module);
        return e;
    }

    // ------------------------------------------------------------------ «Навигационная запись Si-14» (ПВ-13)

    private static BookEntry navRecord(BookChapter c) {
        ItemStack record = FinaleItemsSC.navRecord(1);
        BookEntry e = new BookEntry("world.navrecord", c, record != null ? record : new ItemStack(Items.map), Lang.tr("sc.manual.world.navrecord.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.navrecord.title"))).addAll(BookContent.paras("sc.manual.world.navrecord"));
        e.add(BookEl.item(record, Lang.tr("sc.manual.world.rule.signal"), Lang.tr(SubstrateConfigSC.requireSignal ? "sc.manual.world.rule.signal.on"
                : "sc.manual.world.rule.signal.off")));
        if (record != null) {
            BookContent.crafts(e, record);
        }
        e.addAll(BookContent.paras("sc.manual.world.navrecord.use"));
        e.add(BookEl.link("world.substrate", Lang.tr("sc.manual.world.substrate.title")));
        e.add(BookEl.link("bridge", Lang.tr("sc.manual.bridge.title")));
        e.about(record);
        return e;
    }

    // ------------------------------------------------------------------ «Сингулярный прогресс в Подложке» (ПК-23)

    private static BookEntry progress(BookChapter c) {
        ItemStack chest = new ItemStack(ModItems.ARMOR.get(ArmorSuit.SINGULAR)[1]);
        BookEntry e = new BookEntry("world.progress", c, chest, Lang.tr("sc.manual.world.progress.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.progress.title"))).addAll(BookContent.paras("sc.manual.world.progress",
                new Object[]{Lang.tr("sc.levelgui.task.3.0"), SingularLevel.taskNeed(3, 0)}));
        e.add(BookEl.item(new ItemStack(ModItems.empGun), Lang.tr("sc.levelgui.task.3.3"), Lang.tr("sc.manual.world.progress.emp", SingularLevel.taskNeed(3, 3))));
        e.add(BookEl.item(BookWorldSC.egg(MobEntitiesSC.ID_BREAKDOWN_WARDEN), Lang.tr("sc.levelgui.task.5.3"), Lang.tr("sc.manual.world.progress.warden",
                SingularLevel.taskNeed(5, 3), WardenRulesSC.PROGRESS_POINTS)));
        e.add(BookEl.dim(Lang.tr("sc.manual.world.progress.note")));
        e.add(BookEl.link("singulararmor", Lang.tr("sc.manual.singular.head")));
        e.add(BookEl.link("world.warden", BookWorldSC.mobName(EntityBreakdownWardenSC.NAME)));
        e.add(BookEl.link("world.mobemp", Lang.tr("sc.manual.world.mobemp.title")));
        return e;
    }

    // ------------------------------------------------------------------ «Небо Подложки» (ПМ-19, ПК-22)

    private static BookEntry sky(BookChapter c) {
        BookEntry e = new BookEntry("world.sky", c, new ItemStack(Items.clock), Lang.tr("sc.manual.world.sky.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.sky.title"))).addAll(BookContent.paras("sc.manual.world.sky"));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.sky.option"), Lang.tr(SubstrateConfigSC.customSky() ? "sc.manual.world.rule.sky.on"
                : "sc.manual.world.rule.sky.off")));
        e.add(BookEl.dim(Lang.tr("sc.manual.world.sky.shaders")));
        e.add(BookEl.link("world.substrate", Lang.tr("sc.manual.world.substrate.title")));
        e.add(BookEl.link("world.storm", Lang.tr("sc.manual.world.storm.title")));
        return e;
    }

    // ------------------------------------------------------------------

    private static String material(SiliconMaterial m) {
        return ModItems.siliconMaterial != null ? ModItems.siliconMaterial.stackOf(m).getDisplayName() : m.name();
    }

    /** «4-8», or «1» when both ends agree. */
    private static String span(int lo, int hi) {
        return lo == hi ? String.valueOf(lo) : lo + "-" + hi;
    }

    private static String n(float v) {
        return BookContent.number(Math.round(v * 10F) / 10F);
    }
}
