package com.sc.manual;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import com.sc.block.BlockDockSC;
import com.sc.block.BlockShowcaseSC;
import com.sc.bridge.BridgeMathSC;
import com.sc.dim.EntryRulesSC;
import com.sc.dim.SubstrateBlocksSC;
import com.sc.dim.SubstrateConfigSC;
import com.sc.dim.SubstrateOreTableSC;
import com.sc.dim.SubstrateSC;
import com.sc.dim.SubstrateTerrainSC;
import com.sc.dim.WorldProviderSubstrateSC;
import com.sc.emp.EmpMathSC;
import com.sc.emp.EmpMode;
import com.sc.emp.EmpTooltipSC;
import com.sc.init.ModBlocks;
import com.sc.init.ModItems;
import com.sc.machine.MachineRecipe;
import com.sc.machine.MachineType;
import com.sc.machine.RecipeRegistry;
import com.sc.mob.EntityLinemanSC;
import com.sc.mob.EntityMarauderSC;
import com.sc.mob.MobArmorMathSC;
import com.sc.mob.MobCombatSC;
import com.sc.mob.MobEntitiesSC;
import com.sc.mob.MobItemsSC;
import com.sc.mob.SpawnRulesSC;
import com.sc.mob.TrophyTableSC;
import com.sc.settle.NpcKindSC;
import com.sc.settle.ReputationSC;
import com.sc.settle.SettlementBlocksSC;
import com.sc.settle.SettlementItemsSC;
import com.sc.settle.SettlementSC;
import com.sc.settle.SettlementSizeSC;
import com.sc.settle.ViolationRulesSC;
import com.sc.settle.gen.SettlementLayoutSC;
import com.sc.settle.npc.EntityTunerSC;
import com.sc.settle.npc.EntityWardenSC;
import com.sc.settle.npc.NpcNamesSC;
import com.sc.settle.trade.ContractTableSC;
import com.sc.settle.trade.TradeOfferSC;
import com.sc.settle.trade.TradeRulesSC;
import com.sc.settle.trade.TradeTableSC;
import com.sc.settle.travel.TravelRulesSC;
import com.sc.util.ArmorSuit;
import com.sc.util.BladeType;
import com.sc.util.ConfigSC;
import com.sc.util.OreEntry;
import com.sc.util.SiliconMaterial;
import com.sc.util.WeaponType;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/**
 * The handbook's chapter «Подложка» (BookChapter.WORLD, docs/plan-substrate-tech.md §7.2): «Подложка (Si-14)» (the
 * way in, the price, the world's rules, multiplayer), «Мир-микросхема» (the grid, biomes, layers, blocks, ores),
 * «Бронированные враги» (the reserve, the shield, ПЭ-1 on mobs), the bestiary («Обходчик», «Мародёр») and «Трофеи и
 * добыча». Every number comes from the code and the config (the server's rules when playing on one - SubstrateNetSC
 * rebuilds the book with them), never from the text. G on the Substrate's blocks, the trophies, the loot and the
 * spawn eggs opens these pages.
 * <p>
 * Ф2 «Поселения» (docs/plan-substrate-tech-f2.md §10.2) adds at the chapter's end «Поселения Наладчиков» (the sizes of
 * SettlementSizeSC, the plan, the core, what a guest may not do), «Наладчики» (NpcKindSC's professions, the day),
 * «Терминал обмена и КЧ» (TradeTableSC's deals, ReputationSC's multipliers, TradeRulesSC's limits, ContractTableSC's
 * contracts), «Доверие» (ReputationSC, ViolationRulesSC), «Стражи купола» (the ranks, MobCombatSC's caps, the
 * provocation) and «Причалы и возврат» (TravelRulesSC). G on the core, the showcases, the brick, the dock, the chip,
 * the capsule, the map, the Stolen Cargo and both eggs opens them.
 * <p>
 * The bestiary's own numbers (health, reserve, weapons, drop chances) live in package M's classes; they are read by
 * name (stat) so this chapter builds before M's constants exist - the plan's value until then, and the self-test
 * lists every one still on it (unresolved). TODO(D, after M): direct references. Package N's creatures (the Tuner's
 * day, the guards' health and reserve) are read the same way.
 */
public final class BookWorldSC {

    /** The rock's metas by name, SubstrateBlocksSC's order (tile.siliconage.substrateRock.<name>.name, sc.tooltip.substrateRock.<name>). */
    public static final String[] ROCK_NAMES = {"crystallite", "oxideViolet", "oxideBlue", "oxideGold", "oxideGreen", "copperTrace", "traceNode",
            "quartzTurf", "settlementBrick"};
    /** Ф2: the Settlement Brick is the rock's meta 8 (SubstrateBlocksSC.SETTLEMENT_BRICK, package S; docs/plan-substrate-tech-f2.md §2.2). */
    public static final int BRICK = 8;
    /** The combat loot by meta (ItemCombatLootSC.LootKind, appended only): item.siliconage.combatLoot.<name>.name, sc.tooltip.loot.<name>. */
    public static final String[] LOOT_NAMES = {"shellFirmware", "nanoScrap", "stolenCargo"};
    /** Ф2: the Stolen Cargo is meta 2 (LootKind.STOLEN_CARGO, package T). */
    public static final int LOOT_FIRMWARE = 0, LOOT_NANO_SCRAP = 1, LOOT_STOLEN_CARGO = 2;
    /** The trophy's lang key by meta (item.siliconage.trophy.<key>.name; the metas of TrophyTableSC). */
    public static final String[] TROPHY_KEYS = trophyKeys();

    /** Package M's classes the bestiary reads its numbers from. */
    private static final String LINEMAN = "com.sc.mob.EntityLinemanSC", MARAUDER = "com.sc.mob.EntityMarauderSC", DROPS = "com.sc.mob.MobDropsSC",
            BEAM = "com.sc.mob.ai.EntityAIBeamAttackSC";
    /** Ф2: package N's creatures (their numbers by name, as package M's: the plan's value until N's constants exist). */
    private static final String TUNER = "com.sc.settle.npc.EntityTunerSC", WARDEN = "com.sc.settle.npc.EntityWardenSC";
    /** The trade article's price example: what the table's 16 becomes at each level. */
    private static final int PRICE_EXAMPLE = 16;
    private static final Set<String> UNRESOLVED = new TreeSet<String>();

    private BookWorldSC() {
    }

    static void add(List<BookEntry> list) {
        BookChapter c = BookChapter.WORLD;
        list.add(substrate(c));
        list.add(terrain(c));
        list.add(armoured(c));
        list.add(lineman(c));
        list.add(marauder(c));
        list.add(trophies(c));
        // Ф2 «Поселения» (docs/plan-substrate-tech-f2.md §10.2): at the chapter's end
        list.add(settlements(c));
        list.add(tuners(c));
        list.add(trade(c));
        list.add(trust(c));
        list.add(wardens(c));
        list.add(docks(c));
    }

    /** The chapter's tile: Crystallite while the Substrate's blocks exist, else a compass. */
    static ItemStack icon() {
        ItemStack s = rock(SubstrateBlocksSC.CRYSTALLITE);
        return s != null ? s : new ItemStack(Items.compass);
    }

    // ------------------------------------------------------------------ «Подложка (Si-14)»

    private static BookEntry substrate(BookChapter c) {
        BookEntry e = new BookEntry("world.substrate", c, icon(), Lang.tr("sc.manual.world.substrate.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.substrate.title"))).addAll(BookContent.paras("sc.manual.world.about"));
        if (!SubstrateSC.active()) {
            e.add(BookEl.warn(Lang.tr("sc.manual.world.off")));
        }

        // the way in (ПВ-1, ПВ-2)
        e.add(BookEl.head(Lang.tr("sc.manual.world.gethead"), com.sc.block.BlockBridgeSC.stack(com.sc.block.BlockBridgeSC.CONTROLLER, 1)));
        e.addAll(BookContent.paras("sc.manual.world.get", new Object[]{Lang.tr(WorldProviderSubstrateSC.NAME_KEY)}));
        if (!ConfigSC.bridgeCrossDimension) {
            e.add(BookEl.warn(Lang.tr("sc.manual.world.nocross")));
        }

        // the price (ПВ-6): the Space Bridge's burst, both multipliers, the shared anchor
        float mul = ConfigSC.bridgeCostMultiplier * SubstrateConfigSC.bridgeCostMultiplier;
        e.add(BookEl.head(Lang.tr("sc.manual.world.pricehead")));
        BookEl t = BookEl.table(Lang.tr("sc.manual.world.t.jump"), "EU");
        t.row(null, Lang.tr("sc.manual.world.t.noanchor"), BookContent.num(eu(BridgeMathSC.SPACE_BURST * BridgeMathSC.NO_ANCHOR_MUL, mul)));
        t.row(com.sc.block.BlockBridgeSC.stack(com.sc.block.BlockBridgeSC.ANCHOR, 1), Lang.tr("sc.manual.world.t.anchor"),
                BookContent.num(eu(BridgeMathSC.SPACE_BURST, mul)));
        e.add(t);
        e.addAll(BookContent.paras("sc.manual.world.price", new Object[]{multiplier(SubstrateConfigSC.bridgeCostMultiplier),
                multiplier(ConfigSC.bridgeCostMultiplier)}, new Object[]{BridgeMathSC.NO_ANCHOR_MUL}));

        // the world's rules (ПВ-3, ПВ-7..ПВ-10)
        e.add(BookEl.head(Lang.tr("sc.manual.world.ruleshead")));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.rule.entry"),
                Lang.tr(SubstrateConfigSC.strictEntry ? "sc.manual.world.rule.entry.on" : "sc.manual.world.rule.entry.off")));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.rule.buffer"), Lang.tr("sc.manual.world.rule.buffer.desc", Lang.tr("potion.resistance"),
                Lang.trOr("potion.potency." + EntryRulesSC.BUFFER_AMPLIFIER, String.valueOf(EntryRulesSC.BUFFER_AMPLIFIER + 1)), sec(EntryRulesSC.BUFFER_TICKS))));
        e.add(BookEl.item(new ItemStack(Items.bed), Lang.tr("sc.manual.world.rule.bed"), Lang.tr("sc.manual.world.rule.bed.desc")));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.rule.death"), Lang.tr("sc.manual.world.rule.death.desc", dimName(SubstrateSC.respawnDimension()))));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.rule.natives"),
                Lang.tr(SubstrateConfigSC.nativesCrossBridge ? "sc.manual.world.rule.natives.on" : "sc.manual.world.rule.natives.off")));

        // multiplayer (ПК-4): the ids here
        boolean on = SubstrateSC.active();
        e.add(BookEl.head(Lang.tr("sc.manual.world.mphead")));
        e.addAll(BookContent.paras("sc.manual.world.mp", null, new Object[]{on ? SubstrateSC.dimId() : SubstrateConfigSC.dimensionId(),
                on ? SubstrateSC.plateaus().biomeID : SubstrateConfigSC.biomeIdPlateaus(), on ? SubstrateSC.canyons().biomeID : SubstrateConfigSC.biomeIdCanyons()}));
        e.add(BookEl.link("world.terrain", Lang.tr("sc.manual.world.terrain.title")));
        e.add(BookEl.link("world.armoured", Lang.tr("sc.manual.world.armoured.title")));
        e.add(BookEl.link("world.trophies", Lang.tr("sc.manual.world.trophies.title")));
        e.add(BookEl.link("world.settlements", Lang.tr("sc.manual.world.settlements.title")));
        e.add(BookEl.link("bridge", Lang.tr("sc.manual.bridge.title")));
        return e;
    }

    // ------------------------------------------------------------------ «Мир-микросхема»

    private static BookEntry terrain(BookChapter c) {
        ItemStack trace = rock(SubstrateBlocksSC.COPPER_TRACE);
        BookEntry e = new BookEntry("world.terrain", c, trace != null ? trace : icon(), Lang.tr("sc.manual.world.terrain.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.terrain.title"))).addAll(BookContent.paras("sc.manual.world.terrain",
                new Object[]{SubstrateTerrainSC.GRID, SubstrateTerrainSC.CANYON_WIDTH_MIN, SubstrateTerrainSC.CANYON_WIDTH_MAX,
                        SubstrateTerrainSC.CANYON_DEPTH_MIN, SubstrateTerrainSC.CANYON_DEPTH_MAX},
                new Object[]{SubstrateTerrainSC.PLATEAU_MIN, SubstrateTerrainSC.PLATEAU_MAX, SubstrateTerrainSC.TERRACE_STEP}));

        e.add(BookEl.head(Lang.tr("sc.manual.world.biomeshead")));
        e.add(BookEl.item(rock(SubstrateBlocksSC.QUARTZ_TURF), Lang.tr("sc.dim.biome.plateaus"),
                Lang.tr("sc.manual.world.biome.plateaus", SubstrateTerrainSC.PLATEAU_MIN, SubstrateTerrainSC.PLATEAU_MAX)));
        e.add(BookEl.item(rock(SubstrateBlocksSC.OXIDE_VIOLET), Lang.tr("sc.dim.biome.canyons"),
                Lang.tr("sc.manual.world.biome.canyons", SubstrateTerrainSC.CANYON_BELT)));

        // the layers (§3.3 of docs/plan-substrate.md), from SubstrateTerrainSC
        e.add(BookEl.head(Lang.tr("sc.manual.world.layershead")));
        layer(e, new ItemStack(Blocks.bedrock), "0", "bedrock");
        layer(e, rock(SubstrateBlocksSC.CRYSTALLITE), "1-" + SubstrateTerrainSC.BASE_TOP, "base");
        layer(e, ore(OreEntry.TANTALITE), SubstrateTerrainSC.BASE_TOP + "-" + SubstrateTerrainSC.METAL_TOP, "metal");
        layer(e, rock(SubstrateBlocksSC.CRYSTALLITE), SubstrateTerrainSC.METAL_TOP + "-" + SubstrateTerrainSC.OXIDE_FROM, "crystallite");
        layer(e, rock(SubstrateBlocksSC.OXIDE_GOLD), SubstrateTerrainSC.OXIDE_FROM + "+", "oxide");

        e.add(BookEl.head(Lang.tr("sc.manual.world.blockshead")));
        for (int m = 0; m < SubstrateBlocksSC.ROCK_METAS && m < ROCK_NAMES.length; m++) {
            e.add(BookEl.item(rock(m), rockName(m), Lang.tr("sc.manual.world.block." + ROCK_NAMES[m])));
        }
        ItemStack druse = druse();
        e.add(BookEl.item(druse, druse != null ? druse.getDisplayName() : Lang.tr("tile.siliconage.crystalDruse.name"), Lang.tr("sc.manual.world.block.druse")));

        // the ores: SubstrateOreTableSC's heights and multipliers on the config's veins
        e.add(BookEl.head(Lang.tr("sc.manual.world.oreshead"), ore(OreEntry.TANTALITE)));
        e.addAll(BookContent.paras("sc.manual.world.ores"));
        BookEl t = BookEl.table(Lang.tr("sc.manual.world.t.ore"), "Y", Lang.tr("sc.manual.world.t.veins"));
        for (OreEntry ore : OreEntry.values()) {
            float mul = SubstrateOreTableSC.veinMultiplier(ore);
            t.row(ore(ore), new ItemStack(ModBlocks.oreSC, 1, ore.meta()).getDisplayName(), SubstrateOreTableSC.minY(ore) + "-" + SubstrateOreTableSC.maxY(ore),
                    Lang.tr("sc.manual.world.cell.veins", n(ConfigSC.settingsFor(ore).veinsPerChunk * mul), multiplier(mul)));
        }
        e.add(t);
        if (SubstrateBlocksSC.rock != null) {
            e.about(new ItemStack(SubstrateBlocksSC.rock, 1, OreDictionary.WILDCARD_VALUE));
        }
        if (SubstrateBlocksSC.ore != null) {
            e.about(new ItemStack(SubstrateBlocksSC.ore, 1, OreDictionary.WILDCARD_VALUE));
        }
        e.about(druse);
        e.add(BookEl.link("world.substrate", Lang.tr("sc.manual.world.substrate.title")));
        return e;
    }

    private static void layer(BookEntry e, ItemStack icon, String ys, String key) {
        e.add(BookEl.item(icon, "Y " + ys + ": " + Lang.tr("sc.manual.world.layer." + key), Lang.tr("sc.manual.world.layer." + key + ".what")));
    }

    // ------------------------------------------------------------------ «Бронированные враги»

    private static BookEntry armoured(BookChapter c) {
        BookEntry e = new BookEntry("world.armoured", c, nanoPiece(1), Lang.tr("sc.manual.world.armoured.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.armoured.title"))).addAll(BookContent.paras("sc.manual.world.armour",
                new Object[]{MobArmorMathSC.FULL_POINTS}));

        // by suit: the points of a full set, the share of a blow it stops, ПЭ-1's times and the siphon's rate (the player's rules)
        BookEl t = BookEl.table(Lang.tr("sc.manual.world.t.suit"), Lang.tr("sc.manual.world.t.points"), Lang.tr("sc.manual.world.t.stops"),
                Lang.tr("sc.manual.world.t.emp"), Lang.tr("sc.manual.world.t.siphon"));
        for (ArmorSuit s : ArmorSuit.values()) {
            int pts = suitPoints(s);
            t.row(new ItemStack(ModItems.ARMOR.get(s)[1]), Lang.tr("sc.suit." + s.name().toLowerCase(Locale.ROOT)), String.valueOf(pts),
                    pct(MobArmorMathSC.absorbShare(pts)) + "%", sec(empTicks(s, EmpMode.POINT)) + " / " + sec(empTicks(s, EmpMode.PULSE)),
                    EmpTooltipSC.siphonPct(s, false) + "%");
        }
        e.add(t);
        e.add(BookEl.dim(Lang.tr("sc.manual.world.t.note")));

        e.add(BookEl.head(Lang.tr("sc.manual.world.shieldhead")));
        e.addAll(BookContent.paras("sc.manual.world.shield", null, new Object[]{ironPoints()}, new Object[]{n(MobArmorMathSC.TRACE_RECHARGE_PER_SECOND)}));
        e.add(BookEl.head(Lang.tr("sc.manual.world.fighthead")));
        e.addAll(BookContent.paras("sc.manual.world.fight"));

        // ПЭ-1 (ПБ-10..ПБ-12): the server's two switches
        e.add(BookEl.head(Lang.tr("sc.manual.world.emphead"), new ItemStack(ModItems.empGun)));
        e.add(BookEl.para(SubstrateConfigSC.empMobTargets ? Lang.tr("sc.manual.world.emp.on", ConfigSC.empImmunitySeconds, ironPoints())
                : Lang.tr("sc.manual.world.emp.off", sec(EmpMathSC.NO_ARMOR_SLOW_TICKS))));
        e.add(BookEl.para(SubstrateConfigSC.siphonMobs ? Lang.tr("sc.manual.world.siphon.on", BookContent.num(MobArmorMathSC.MOB_SIPHON_STREAM_CAP))
                : Lang.tr("sc.manual.world.siphon.off")));
        e.add(BookEl.link("emp", Lang.tr("sc.manual.emp.title")));
        e.add(BookEl.link("world.lineman", mobName(EntityLinemanSC.NAME)));
        e.add(BookEl.link("world.marauder", mobName(EntityMarauderSC.NAME)));
        return e;
    }

    // ------------------------------------------------------------------ the bestiary

    private static BookEntry lineman(BookChapter c) {
        String name = mobName(EntityLinemanSC.NAME);
        ItemStack egg = egg(MobEntitiesSC.ID_LINEMAN);
        BookEntry e = new BookEntry("world.lineman", c, egg != null ? egg : nanoPiece(0), name);
        e.add(BookEl.title(name)).addAll(BookContent.paras("sc.manual.world.lineman"));
        float hp = stat(LINEMAN, "HP", 20F);
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.t.hp"), Lang.tr("sc.manual.world.mob.hp", n(hp), n(hp / 2F))));
        e.add(BookEl.item(nanoPiece(1), Lang.tr("sc.manual.world.t.armour"), Lang.tr("sc.manual.world.mob.armour", suitName(ArmorSuit.NANO),
                n(stat(LINEMAN, "PIECES_MIN", 1F)), n(stat(LINEMAN, "PIECES_MAX", 3F)), n(stat(LINEMAN, "RESERVE_MIN", 20F)), n(stat(LINEMAN, "RESERVE_MAX", 30F)))));
        e.add(BookEl.item(weapon(WeaponType.ION_CUTTER), Lang.tr("sc.manual.world.t.weapon"), Lang.tr("sc.manual.world.lineman.weapon",
                n(stat(LINEMAN, "FIST_MIN", 3F)), n(stat(LINEMAN, "FIST_MAX", 4F)), pct(stat(LINEMAN, "ION_CUTTER_CHANCE", 0.05F)),
                n(stat(LINEMAN, "BEAM_DAMAGE", 4F)), sec((int) stat(BEAM, "RELOAD_TICKS", 40F)))));
        e.add(BookEl.item(rock(SubstrateBlocksSC.QUARTZ_TURF), Lang.tr("sc.manual.world.t.where"), Lang.tr("sc.manual.world.lineman.where",
                SpawnRulesSC.PLATEAU_WEIGHTS[0], SpawnRulesSC.CANYON_WEIGHTS[0])));
        e.add(BookEl.item(loot(LOOT_FIRMWARE), Lang.tr("sc.manual.world.t.loot"), Lang.tr("sc.manual.world.lineman.loot", lootName(LOOT_FIRMWARE),
                ModItems.siliconMaterial.stackOf(SiliconMaterial.TRANSISTOR).getDisplayName(), n(stat(DROPS, "TRANSISTOR_MAX", 2F)))));
        e.add(BookEl.item(trophy(TrophyTableSC.armorMeta(ArmorSuit.NANO, 1)), Lang.tr("sc.manual.world.t.trophies"), Lang.tr("sc.manual.world.lineman.trophies",
                pct(trophyChance(TrophyTableSC.PIECE[0])), pct(trophyChance(TrophyTableSC.WEAPON[WeaponType.ION_CUTTER.ordinal()])))));
        e.addAll(BookContent.paras("sc.manual.world.lineman.tips", new Object[]{sec(empTicks(ArmorSuit.NANO, EmpMode.POINT)), siphonSeconds(ArmorSuit.NANO)}));
        e.add(BookEl.link("world.armoured", Lang.tr("sc.manual.world.armoured.title")));
        e.add(BookEl.link("world.trophies", Lang.tr("sc.manual.world.trophies.title")));
        e.about(egg);
        return e;
    }

    private static BookEntry marauder(BookChapter c) {
        String name = mobName(EntityMarauderSC.NAME);
        ItemStack egg = egg(MobEntitiesSC.ID_MARAUDER);
        BookEntry e = new BookEntry("world.marauder", c, egg != null ? egg : nanoPiece(1), name);
        e.add(BookEl.title(name)).addAll(BookContent.paras("sc.manual.world.marauder"));
        float hp = stat(MARAUDER, "HP", 24F);
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.t.hp"), Lang.tr("sc.manual.world.mob.hp", n(hp), n(hp / 2F))));
        e.add(BookEl.item(nanoPiece(1), Lang.tr("sc.manual.world.t.armour"), Lang.tr("sc.manual.world.mob.armour", suitName(ArmorSuit.NANO),
                n(stat(MARAUDER, "PIECES_MIN", 2F)), n(stat(MARAUDER, "PIECES_MAX", 4F)), n(stat(MARAUDER, "RESERVE_MIN", 20F)), n(stat(MARAUDER, "RESERVE_MAX", 40F)))));
        e.add(BookEl.item(blade(BladeType.NANO), Lang.tr("sc.manual.world.t.weapon"), Lang.tr("sc.manual.world.marauder.weapon",
                pct(stat(MARAUDER, "BLADE_CHANCE", 0.5F)), n(stat(MARAUDER, "BLADE_DAMAGE", 6F)), pct(stat(MARAUDER, "ION_CUTTER_CHANCE", 0.3F)),
                n(stat(MARAUDER, "BEAM_DAMAGE", 4F)), n(stat(MARAUDER, "CLUB_DAMAGE", 4F)))));
        e.add(BookEl.item(rock(SubstrateBlocksSC.OXIDE_VIOLET), Lang.tr("sc.manual.world.t.where"), Lang.tr("sc.manual.world.marauder.where",
                SpawnRulesSC.CANYON_WEIGHTS[1], SpawnRulesSC.PLATEAU_WEIGHTS[1], n(stat(MARAUDER, "GROUP_MIN", 2F)), n(stat(MARAUDER, "GROUP_MAX", 4F)))));
        e.add(BookEl.item(new ItemStack(Blocks.chest), Lang.tr("sc.manual.world.t.bag"), Lang.tr("sc.manual.world.marauder.bag", n(stat(MARAUDER, "BAG_SLOTS", 9F)))));
        e.add(BookEl.item(loot(LOOT_NANO_SCRAP), Lang.tr("sc.manual.world.t.loot"), Lang.tr("sc.manual.world.marauder.loot", lootName(LOOT_NANO_SCRAP),
                pct(stat(DROPS, "SCRAP_CHANCE", 0.4F)))));
        e.add(BookEl.item(trophy(TrophyTableSC.bladeMeta(BladeType.NANO)), Lang.tr("sc.manual.world.t.trophies"), Lang.tr("sc.manual.world.marauder.trophies",
                pct(trophyChance(TrophyTableSC.PIECE[0])), pct(trophyChance(TrophyTableSC.BLADE[0])),
                pct(trophyChance(TrophyTableSC.WEAPON[WeaponType.ION_CUTTER.ordinal()])))));
        e.addAll(BookContent.paras("sc.manual.world.marauder.tips", new Object[]{sec(empTicks(ArmorSuit.NANO, EmpMode.POINT))}));
        e.add(BookEl.link("world.armoured", Lang.tr("sc.manual.world.armoured.title")));
        e.add(BookEl.link("world.trophies", Lang.tr("sc.manual.world.trophies.title")));
        e.about(egg);
        return e;
    }

    // ------------------------------------------------------------------ «Трофеи и добыча»

    private static BookEntry trophies(BookChapter c) {
        ItemStack icon = trophy(TrophyTableSC.armorMeta(ArmorSuit.NANO, 0));
        BookEntry e = new BookEntry("world.trophies", c, icon != null ? icon : nanoPiece(0), Lang.tr("sc.manual.world.trophies.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.trophies.title"))).addAll(BookContent.paras("sc.manual.world.trophies", null,
                new Object[]{pct(stat(DROPS, "KILLER_SHARE", 0.25F)), sec((int) stat(DROPS, "OWNER_TICKS", 300F))},
                new Object[]{pct(TrophyTableSC.LOOTING_STEP), n(TrophyTableSC.LOOTING_CAP), TrophyTableSC.BAD_LUCK_N[ArmorSuit.NANO.ordinal()],
                        2 * TrophyTableSC.BAD_LUCK_N[ArmorSuit.NANO.ordinal()]}));
        if (SubstrateConfigSC.gearDropMultiplier != 1F) {
            e.add(BookEl.warn(Lang.tr("sc.manual.world.trophies.mul", n(SubstrateConfigSC.gearDropMultiplier))));
        }

        // what drops in this version (ПТ-7, ПТ-9): the chances with the server's multiplier
        String both = mobName(EntityLinemanSC.NAME) + ", " + mobName(EntityMarauderSC.NAME);
        BookEl t = BookEl.table(Lang.tr("sc.manual.world.t.trophy"), Lang.tr("sc.manual.world.t.chance"), Lang.tr("sc.manual.world.t.from"));
        t.row(trophy(TrophyTableSC.armorMeta(ArmorSuit.NANO, 1)), Lang.tr("sc.manual.world.t.nanopiece"), pct(trophyChance(TrophyTableSC.PIECE[0])) + "%", both);
        t.row(trophy(TrophyTableSC.bladeMeta(BladeType.NANO)), blade(BladeType.NANO).getDisplayName(), pct(trophyChance(TrophyTableSC.BLADE[0])) + "%",
                mobName(EntityMarauderSC.NAME));
        t.row(trophy(TrophyTableSC.weaponMeta(WeaponType.ION_CUTTER)), weapon(WeaponType.ION_CUTTER).getDisplayName(),
                pct(trophyChance(TrophyTableSC.WEAPON[WeaponType.ION_CUTTER.ordinal()])) + "%", both);
        e.add(t);

        e.add(BookEl.head(Lang.tr("sc.manual.world.trophyitemshead")));
        e.addAll(BookContent.paras("sc.manual.world.trophyitems"));

        // faction loot (ПТ-1) and its use before the settlements: the Crusher's recipes
        e.add(BookEl.head(Lang.tr("sc.manual.world.loothead")));
        e.add(BookEl.item(loot(LOOT_FIRMWARE), lootName(LOOT_FIRMWARE), Lang.tr("sc.manual.world.loot.shellFirmware")));
        e.add(BookEl.item(loot(LOOT_NANO_SCRAP), lootName(LOOT_NANO_SCRAP), Lang.tr("sc.manual.world.loot.nanoScrap",
                pct(stat(DROPS, "SCRAP_CHANCE", 0.4F)))));
        e.add(BookEl.item(new ItemStack(Blocks.chest), Lang.tr("sc.manual.world.t.bag"), Lang.tr("sc.manual.world.loot.bag")));
        e.add(BookEl.item(loot(LOOT_STOLEN_CARGO), lootName(LOOT_STOLEN_CARGO), lootLine(LOOT_STOLEN_CARGO)));   // Ф2: back to the Elders
        for (MachineRecipe r : lootRecipes()) {
            e.add(BookEl.machine(r));
        }
        e.add(BookEl.link("world.lineman", mobName(EntityLinemanSC.NAME)));
        e.add(BookEl.link("world.marauder", mobName(EntityMarauderSC.NAME)));
        e.add(BookEl.link("world.trade", Lang.tr("sc.manual.world.trade.title")));
        if (MobItemsSC.trophy != null) {
            e.about(new ItemStack(MobItemsSC.trophy, 1, OreDictionary.WILDCARD_VALUE));
        }
        if (MobItemsSC.combatLoot != null) {
            e.about(new ItemStack(MobItemsSC.combatLoot, 1, OreDictionary.WILDCARD_VALUE));
        }
        return e;
    }

    /** The machine recipes (the Crusher's first) that take the combat loot. */
    private static List<MachineRecipe> lootRecipes() {
        List<MachineRecipe> out = new ArrayList<MachineRecipe>();
        if (MobItemsSC.combatLoot == null) {
            return out;
        }
        for (MachineType type : MachineType.values()) {
            for (MachineRecipe r : RecipeRegistry.recipesFor(type)) {
                for (ItemStack in : r.inputs) {
                    if (in != null && in.getItem() == MobItemsSC.combatLoot) {
                        out.add(r);
                        break;
                    }
                }
            }
        }
        return out;
    }

    // ================================================================== Ф2 «Поселения» (docs/plan-substrate-tech-f2.md §10.2)

    // ------------------------------------------------------------------ «Поселения Наладчиков»

    private static BookEntry settlements(BookChapter c) {
        ItemStack core = core();
        BookEntry e = new BookEntry("world.settlements", c, core != null ? core : icon(), Lang.tr("sc.manual.world.settlements.title"));
        // the first settlement's middle from the world's spawn (the middle of cell 0, 0): FIRST_CELL cells away
        int first = (int) Math.round(Math.hypot(SettlementLayoutSC.FIRST_CELL_X * SettlementLayoutSC.CELL,
                SettlementLayoutSC.FIRST_CELL_Z * SettlementLayoutSC.CELL));
        e.add(BookEl.title(Lang.tr("sc.manual.world.settlements.title"))).addAll(BookContent.paras("sc.manual.world.settle.about", null,
                new Object[]{SettlementLayoutSC.REGION, SettlementLayoutSC.CHANCE_PCT, SettlementLayoutSC.CELL - 2 * SettlementLayoutSC.JITTER},
                new Object[]{first}));
        if (!SubstrateConfigSC.villages) {
            e.add(BookEl.warn(Lang.tr("sc.manual.world.settle.off")));
        }
        e.add(BookEl.dim(Lang.tr("sc.manual.world.settle.old")));

        // the sizes (ПП-3): SettlementSizeSC
        e.add(BookEl.head(Lang.tr("sc.manual.world.settle.sizeshead")));
        BookEl t = BookEl.table(Lang.tr("sc.manual.world.t.size"), Lang.tr("sc.manual.world.t.dome"), Lang.tr("sc.manual.world.t.tuners"),
                Lang.tr("sc.manual.world.t.guards"), Lang.tr("sc.manual.world.t.dock"));
        for (SettlementSizeSC s : SettlementSizeSC.values()) {
            t.row(null, Lang.tr(s.langKey()), span(s.radiusMin, s.radiusMax), span(s.tunersMin, s.tunersMax),
                    s.seniors > 0 ? Lang.tr("sc.manual.world.cell.guards", s.sentries, s.seniors) : String.valueOf(s.sentries),
                    Lang.tr(s.dock ? "sc.manual.world.cell.yes" : "sc.manual.world.cell.no"));
        }
        e.add(t);
        int[] w = SettlementLayoutSC.SIZE_WEIGHTS;
        e.add(BookEl.dim(Lang.tr("sc.manual.world.settle.sizes.note", Lang.tr(NpcKindSC.SENTRY.langKey()), Lang.tr(NpcKindSC.SENIOR_WARDEN.langKey()),
                n(w.length > 2 && w[2] > 0 ? w[1] / (float) w[2] : 0F))));

        // the plan from the middle out (ПП-4)
        e.add(BookEl.head(Lang.tr("sc.manual.world.settle.layouthead")));
        e.addAll(BookContent.paras("sc.manual.world.settle.layout"));
        part(e, core, "heart");
        part(e, showcase(BlockShowcaseSC.ARMOUR_STATION), "square");
        part(e, showcase(BlockShowcaseSC.CVD), "workshops");
        part(e, rock(BRICK), "cells");
        part(e, null, "towers");
        part(e, showcase(BlockShowcaseSC.SHOWER), "gate");
        part(e, dock(BlockDockSC.BEACON), "dock");

        // the core (ПП-6, ПП-7)
        e.add(BookEl.head(Lang.tr("sc.manual.world.settle.corehead"), core));
        e.addAll(BookContent.paras("sc.manual.world.settle.core", null, new Object[]{SettlementSC.FOUNDATION}));

        // what a guest may not do (ПП-7..ПП-11, ПЖ-2)
        e.add(BookEl.head(Lang.tr("sc.manual.world.settle.ruleshead")));
        rule(e, new ItemStack(Items.iron_pickaxe), "break");
        rule(e, new ItemStack(Blocks.chest), "use");
        rule(e, new ItemStack(Items.flint_and_steel), "fire");
        rule(e, new ItemStack(Blocks.tnt), "explode");
        rule(e, egg(MobEntitiesSC.ID_MARAUDER), "spawn", SettlementSC.SPAWN_MARGIN);
        rule(e, com.sc.block.BlockBridgeSC.stack(com.sc.block.BlockBridgeSC.CONTROLLER, 1), "bridge");
        rule(e, new ItemStack(ModBlocks.fieldGeneratorSC), "field");
        rule(e, egg(MobEntitiesSC.ID_TUNER), "tuners", SettlementSC.BELT);
        rule(e, null, "fake");
        e.addAll(BookContent.paras("sc.manual.world.settle.allowed"));
        e.add(BookEl.link("world.tuners", Lang.tr("sc.manual.world.tuners.title")));
        e.add(BookEl.link("world.trade", Lang.tr("sc.manual.world.trade.title")));
        e.add(BookEl.link("world.trust", Lang.tr("sc.manual.world.trust.title")));
        e.add(BookEl.link("world.wardens", Lang.tr("sc.manual.world.wardens.title")));
        e.add(BookEl.link("world.docks", Lang.tr("sc.manual.world.docks.title")));
        e.about(core, rock(BRICK));
        if (SettlementBlocksSC.showcase != null) {
            e.about(new ItemStack(SettlementBlocksSC.showcase, 1, OreDictionary.WILDCARD_VALUE));
        }
        if (SettlementBlocksSC.decor != null) {
            e.about(new ItemStack(SettlementBlocksSC.decor, 1, OreDictionary.WILDCARD_VALUE));
        }
        if (SettlementBlocksSC.glass != null) {
            e.about(new ItemStack(SettlementBlocksSC.glass));
        }
        return e;
    }

    /** One step of the plan: sc.manual.world.settle.part.<key> and its .desc. */
    private static void part(BookEntry e, ItemStack icon, String key) {
        e.add(BookEl.item(icon, Lang.tr("sc.manual.world.settle.part." + key), Lang.tr("sc.manual.world.settle.part." + key + ".desc")));
    }

    /** One thing a guest may not do: sc.manual.world.settle.rule.<key> and its .desc (with `args`). */
    private static void rule(BookEntry e, ItemStack icon, String key, Object... args) {
        String k = "sc.manual.world.settle.rule." + key;
        e.add(BookEl.item(icon, Lang.tr(k), args.length == 0 ? Lang.tr(k + ".desc") : Lang.tr(k + ".desc", args)));
    }

    // ------------------------------------------------------------------ «Наладчики»

    private static BookEntry tuners(BookChapter c) {
        ItemStack egg = egg(MobEntitiesSC.ID_TUNER);
        BookEntry e = new BookEntry("world.tuners", c, egg != null ? egg : icon(), Lang.tr("sc.manual.world.tuners.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.tuners.title"))).addAll(BookContent.paras("sc.manual.world.tuners", null,
                new Object[]{NpcNamesSC.ELEMENTS.length}));
        float hp = stat(TUNER, "HP", 20F);
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.t.hp"), Lang.tr("sc.manual.world.mob.hp", n(hp), n(hp / 2F))));
        e.add(BookEl.item(core(), Lang.tr("sc.manual.world.tuners.safe"), Lang.tr("sc.manual.world.tuners.safe.desc", SettlementSC.BELT)));

        // the professions of Ф2 (ПЖ-3): NpcKindSC, the deals' count from TradeTableSC
        e.add(BookEl.head(Lang.tr("sc.manual.world.tuners.kindshead")));
        for (NpcKindSC k : NpcKindSC.values()) {
            if (k.guard) {
                continue;
            }
            int deals = TradeTableSC.offers(k).size();
            String what = Lang.tr("sc.manual.world.tuners.kind." + k.key);
            e.add(BookEl.item(kindIcon(k), Lang.tr(k.langKey()), deals > 0 ? what + " " + Lang.tr("sc.manual.world.tuners.deals", deals) : what));
        }
        e.add(BookEl.dim(Lang.tr("sc.manual.world.tuners.later")));

        // the day (ПЖ-4), the world's clock: tick 0 is 6:00
        int work = (int) stat(TUNER, "WORK_UNTIL", 11500F), plaza = (int) stat(TUNER, "PLAZA_UNTIL", 13500F);
        e.add(BookEl.head(Lang.tr("sc.manual.world.tuners.dayhead")));
        e.add(BookEl.item(showcase(BlockShowcaseSC.CVD), Lang.tr("sc.manual.world.tuners.day.work"),
                Lang.tr("sc.manual.world.tuners.day.work.desc", clock(0), clock(work))));
        e.add(BookEl.item(core(), Lang.tr("sc.manual.world.tuners.day.plaza"), Lang.tr("sc.manual.world.tuners.day.plaza.desc", clock(work), clock(plaza))));
        e.add(BookEl.item(rock(BRICK), Lang.tr("sc.manual.world.tuners.day.night"), Lang.tr("sc.manual.world.tuners.day.night.desc", clock(plaza))));
        e.add(BookEl.item(egg(MobEntitiesSC.ID_MARAUDER), Lang.tr("sc.manual.world.tuners.day.flee"),
                Lang.tr("sc.manual.world.tuners.day.flee.desc", n(stat(TUNER, "FLEE_RADIUS", 12F)))));

        // talking (ПЖ-5), the Clone Capsule (ПП-13)
        e.add(BookEl.head(Lang.tr("sc.manual.world.tuners.talkhead")));
        e.addAll(BookContent.paras("sc.manual.world.tuners.talk"));
        ItemStack clone = showcase(BlockShowcaseSC.CLONE_CAPSULE);
        e.add(BookEl.item(clone, clone != null ? clone.getDisplayName() : Lang.tr("tile.siliconage.showcase.cloneCapsule.name"),
                Lang.tr("sc.manual.world.tuners.clone", SettlementSC.RESPAWN_CLEAR)));
        e.add(BookEl.item(egg, egg != null ? egg.getDisplayName() : mobName(EntityTunerSC.NAME), Lang.tr("sc.manual.world.tuners.egg")));
        e.add(BookEl.link("world.trade", Lang.tr("sc.manual.world.trade.title")));
        e.add(BookEl.link("world.trust", Lang.tr("sc.manual.world.trust.title")));
        e.add(BookEl.link("world.settlements", Lang.tr("sc.manual.world.settlements.title")));
        e.about(egg);
        return e;
    }

    // ------------------------------------------------------------------ «Терминал обмена и КЧ»

    private static BookEntry trade(BookChapter c) {
        ItemStack chip = SettlementItemsSC.chips(1);
        BookEntry e = new BookEntry("world.trade", c, chip != null ? chip : icon(), Lang.tr("sc.manual.world.trade.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.trade.title"))).addAll(BookContent.paras("sc.manual.world.trade"));
        e.add(BookEl.item(chip, chip != null ? chip.getDisplayName() : Lang.tr("item.siliconage.creditChip.name"),
                Lang.tr("sc.manual.world.trade.chip", ReputationSC.PASS_CHIPS, TradeRulesSC.CHIPS_PER_DAY)));

        // the deals of every profession (ПО-3), the table's base numbers: TradeTableSC
        e.add(BookEl.head(Lang.tr("sc.manual.world.trade.dealshead")));
        e.add(BookEl.dim(Lang.tr("sc.manual.world.trade.deals.note", TradeRulesSC.SEED_SPREAD_PCT)));
        boolean any = false;
        for (NpcKindSC k : NpcKindSC.values()) {
            List<TradeOfferSC> offers = k.guard ? new ArrayList<TradeOfferSC>() : TradeTableSC.offers(k);
            if (offers.isEmpty()) {
                continue;
            }
            any = true;
            e.add(BookEl.head(Lang.tr(k.langKey()), kindIcon(k)));
            BookEl t = BookEl.table(Lang.tr("sc.manual.world.t.give"), Lang.tr("sc.manual.world.t.get"), Lang.tr("sc.manual.world.t.level"),
                    Lang.tr("sc.manual.world.t.limit"));
            for (TradeOfferSC o : offers) {
                t.row(offerIcon(o), sides(o.in), outText(o), levelName(o.minLevel), limitText(o));
            }
            e.add(t);
        }
        if (!any) {
            e.add(BookEl.dim(Lang.tr("sc.manual.world.trade.none")));
        }

        // the level's multiplier on the player's side (ПО-6): ReputationSC
        e.add(BookEl.head(Lang.tr("sc.manual.world.trade.priceshead")));
        BookEl p = BookEl.table(Lang.tr("sc.manual.world.t.level"), Lang.tr("sc.manual.world.t.mul"), Lang.tr("sc.manual.world.t.example", PRICE_EXAMPLE),
                Lang.tr("sc.manual.world.t.can"));
        for (int lv = ReputationSC.VIOLATOR; lv <= ReputationSC.HONOURED; lv++) {
            float m = ReputationSC.priceMultiplier(lv);
            p.row(null, levelName(lv), "×" + multiplier(m), String.valueOf(Math.max(1, (int) Math.ceil(PRICE_EXAMPLE * (double) m - 1e-6))),
                    Lang.tr(ReputationSC.buys(lv) ? "sc.manual.world.cell.buysells" : "sc.manual.world.cell.sells"));
        }
        e.add(p);
        e.addAll(BookContent.paras("sc.manual.world.trade.rules", new Object[]{TradeRulesSC.SEED_SPREAD_PCT},
                new Object[]{TradeRulesSC.CHIPS_PER_DAY, ReputationSC.TRADE, ReputationSC.TRADE_DAY_CAP}, new Object[]{TradeRulesSC.BUY_OVER_SELL}));

        // the Elder's contracts (ПО-8): ContractTableSC, the one-time anchor contract (§4 «Цена»)
        e.add(BookEl.head(Lang.tr("sc.manual.world.trade.contractshead"), kindIcon(NpcKindSC.ELDER)));
        e.addAll(BookContent.paras("sc.manual.world.trade.contracts", new Object[]{ContractTableSC.BOARD, TradeRulesSC.CONTRACTS_PER_DAY}));
        BookEl ct = BookEl.table(Lang.tr("sc.manual.world.t.contract"), Lang.tr("sc.manual.world.t.goal"), Lang.tr("sc.manual.world.t.reward"));
        for (ContractTableSC.Contract k : ContractTableSC.pool()) {
            ct.row(null, Lang.tr("sc.contract." + k.key), String.valueOf(k.target), reward(k.chips, k.reputation)
                    + (k.modulePct > 0 ? " " + Lang.tr("sc.manual.world.cell.module", k.modulePct) : ""));
        }
        ct.row(com.sc.block.BlockBridgeSC.stack(com.sc.block.BlockBridgeSC.ANCHOR, 1), Lang.tr("sc.contract.anchor"), "1",
                reward(ReputationSC.ANCHOR_CHIPS, ReputationSC.ANCHOR) + " " + Lang.tr("sc.manual.world.cell.once"));
        e.add(ct);
        e.add(BookEl.item(SettlementItemsSC.chips(ReputationSC.PENANCE_CHIPS), Lang.tr("sc.manual.world.trade.penance"),
                Lang.tr("sc.manual.world.trade.penance.desc", ReputationSC.PENANCE_CHIPS, ReputationSC.PENANCE)));
        e.add(BookEl.item(loot(LOOT_STOLEN_CARGO), lootName(LOOT_STOLEN_CARGO), lootLine(LOOT_STOLEN_CARGO)));
        e.add(BookEl.link("world.trust", Lang.tr("sc.manual.world.trust.title")));
        e.add(BookEl.link("world.tuners", Lang.tr("sc.manual.world.tuners.title")));
        e.about(chip, loot(LOOT_STOLEN_CARGO));
        return e;
    }

    private static String reward(int chips, int trust) {
        return Lang.tr("sc.manual.world.cell.reward", chips, trust);
    }

    /** A deal's row icon: what comes out (the map, an item, the chips), else its first side. */
    private static ItemStack offerIcon(TradeOfferSC o) {
        if (o.outKind == TradeOfferSC.OUT_MAP && SettlementItemsSC.settlementMap != null) {
            return new ItemStack(SettlementItemsSC.settlementMap);
        }
        ItemStack s = o.outKind == TradeOfferSC.OUT_ITEM && o.out != null ? o.out.display() : null;
        if ((s == null || o.sell()) && o.in.length > 0 && o.in[0] != null) {
            s = o.in[0].display();                      // a sale: the goods it takes
        }
        return s;
    }

    /** «16 × Кремниевая пластина + 6 КЧ». */
    private static String sides(TradeOfferSC.Ingredient[] in) {
        StringBuilder sb = new StringBuilder();
        for (TradeOfferSC.Ingredient i : in) {
            if (i != null) {
                sb.append(sb.length() == 0 ? "" : " + ").append(side(i));
            }
        }
        return sb.toString();
    }

    private static String side(TradeOfferSC.Ingredient i) {
        if (i.isChips()) {
            return Lang.tr("sc.manual.world.cell.chips", i.count);
        }
        ItemStack s = i.display();
        return Lang.tr("sc.manual.world.cell.count", i.count, s != null ? s.getDisplayName() : i.ore != null ? i.ore : "?");
    }

    private static String outText(TradeOfferSC o) {
        if (o.outKind == TradeOfferSC.OUT_REPUTATION) {
            return Lang.tr("sc.manual.world.cell.trust", o.outAmount);
        }
        if (o.outKind == TradeOfferSC.OUT_MAP) {
            return SettlementItemsSC.settlementMap != null ? new ItemStack(SettlementItemsSC.settlementMap).getDisplayName()
                    : Lang.tr("item.siliconage.settlementMap.name");
        }
        return o.out == null ? "?" : side(o.out);
    }

    /** «8 в сутки», «1 в неделю, глоб.» (ПО-4, ПО-5). */
    static String limitText(TradeOfferSC o) {
        String k = o.global ? (o.periodDays >= 7 ? "sc.manual.world.cell.week.global" : "sc.manual.world.cell.day.global") : "sc.manual.world.cell.day";
        return Lang.tr(k, o.limit);
    }

    /** The chips a deal takes (0: none). */
    private static int chipsOf(TradeOfferSC o) {
        int n = 0;
        for (TradeOfferSC.Ingredient i : o.in) {
            n += i != null && i.isChips() ? i.count : 0;
        }
        return n;
    }

    /** sc.settle.rep.<level> («Чужак»). */
    public static String levelName(int level) {
        return Lang.tr("sc.settle.rep." + ReputationSC.LEVEL_KEYS[Math.max(0, Math.min(ReputationSC.LEVEL_KEYS.length - 1, level))]);
    }

    // ------------------------------------------------------------------ «Доверие»

    private static BookEntry trust(BookChapter c) {
        BookEntry e = new BookEntry("world.trust", c, kindIcon(NpcKindSC.ELDER) != null ? kindIcon(NpcKindSC.ELDER) : icon(),
                Lang.tr("sc.manual.world.trust.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.trust.title"))).addAll(BookContent.paras("sc.manual.world.trust", null,
                new Object[]{ReputationSC.MIN, ReputationSC.MAX}));

        // the levels (ПД-1): ReputationSC
        BookEl t = BookEl.table(Lang.tr("sc.manual.world.t.level"), Lang.tr("sc.manual.world.t.points"), Lang.tr("sc.manual.world.t.opens"),
                Lang.tr("sc.manual.world.t.prices"));
        for (int lv = ReputationSC.ENEMY; lv <= ReputationSC.HONOURED; lv++) {
            t.row(null, levelName(lv), levelRange(lv), Lang.tr("sc.manual.world.trust.opens." + ReputationSC.LEVEL_KEYS[lv]),
                    lv == ReputationSC.ENEMY ? "-" : "×" + multiplier(ReputationSC.priceMultiplier(lv)));
        }
        e.add(t);

        // what raises it (ПД-2, ПД-4, ПД-5)
        e.add(BookEl.head(Lang.tr("sc.manual.world.trust.uphead")));
        ItemStack chip = SettlementItemsSC.chips(1);
        gain(e, chip, "trade", ReputationSC.TRADE, ReputationSC.TRADE_DAY_CAP);
        int[] k = ReputationSC.KILL_BY_SUIT;
        gain(e, nanoPiece(1), "kill", SettlementSC.KILL_REACH, k[0], k[Math.min(1, k.length - 1)], k[Math.min(2, k.length - 1)], k[k.length - 1],
                ReputationSC.KILL_SMALL_DAY_CAP);
        gain(e, kindIcon(NpcKindSC.ELDER), "contract", ReputationSC.CONTRACT_MIN, ReputationSC.CONTRACT_MAX);
        gain(e, loot(LOOT_STOLEN_CARGO), "cargo", ReputationSC.CARGO);
        gain(e, chip, "pass", ReputationSC.PASS, ReputationSC.PASS_CHIPS);
        gain(e, SettlementItemsSC.chips(ReputationSC.PENANCE_CHIPS), "penance", ReputationSC.PENANCE_CHIPS, ReputationSC.PENANCE);
        gain(e, com.sc.block.BlockBridgeSC.stack(com.sc.block.BlockBridgeSC.ANCHOR, 1), "anchor", ReputationSC.ANCHOR, ReputationSC.ANCHOR_CHIPS);

        // what lowers it (ПД-3, ПП-10): ViolationRulesSC
        e.add(BookEl.head(Lang.tr("sc.manual.world.trust.downhead")));
        boolean rule = SubstrateConfigSC.guardsAggroOnViolations;
        BookEl v = BookEl.table(Lang.tr("sc.manual.world.t.violation"), Lang.tr("sc.manual.world.t.trust"), Lang.tr("sc.manual.world.t.guards"));
        for (int kind = 0; kind < ViolationRulesSC.KEYS.length; kind++) {
            String cost, guards;
            if (ViolationRulesSC.blockKind(kind)) {
                cost = ViolationRulesSC.penalty(kind, ViolationRulesSC.STAGE_WARN) + " / " + ViolationRulesSC.penalty(kind, ViolationRulesSC.STAGE_WATCH)
                        + " / " + ViolationRulesSC.penalty(kind, ViolationRulesSC.STAGE_LAST);
            } else if (kind == ViolationRulesSC.KILL_WARDEN) {
                cost = Lang.tr("sc.manual.world.cell.killcost", ViolationRulesSC.penalty(kind, 1), ReputationSC.KILL_WARDEN_PCT);
            } else {
                cost = String.valueOf(ViolationRulesSC.penalty(kind, 1));
            }
            if (kind == ViolationRulesSC.KILL_WARDEN) {
                guards = Lang.tr("sc.manual.world.cell.enemy", days(ReputationSC.ENEMY_TICKS));
            } else if (ViolationRulesSC.provokes(kind, ViolationRulesSC.STAGE_LAST, rule)) {
                guards = Lang.tr("sc.manual.world.cell.aggro", sec(ViolationRulesSC.aggroTicks(kind)));
            } else {
                guards = Lang.tr("sc.manual.world.cell.warn");
            }
            v.row(null, Lang.tr("sc.settle.violation." + ViolationRulesSC.KEYS[kind]), cost, guards);
        }
        e.add(v);
        e.addAll(BookContent.paras("sc.manual.world.trust.down", new Object[]{sec(ViolationRulesSC.WINDOW_TICKS),
                ViolationRulesSC.penalty(ViolationRulesSC.BREAK, ViolationRulesSC.STAGE_WATCH), ViolationRulesSC.penalty(ViolationRulesSC.BREAK, ViolationRulesSC.STAGE_LAST)},
                new Object[]{ReputationSC.ENEMY_BELOW, days(ReputationSC.ENEMY_TICKS)}));
        e.add(BookEl.para(rule ? Lang.tr("sc.manual.world.trust.aggro.on", sec(ViolationRulesSC.WINDOW_TICKS), sec(ViolationRulesSC.VIOLATION_AGGRO_TICKS))
                : Lang.tr("sc.manual.world.trust.aggro.off")));
        e.add(BookEl.link("world.wardens", Lang.tr("sc.manual.world.wardens.title")));
        e.add(BookEl.link("world.trade", Lang.tr("sc.manual.world.trade.title")));
        return e;
    }

    /** «0-99», «ниже 0», «1000+», the local «Враг» status. */
    private static String levelRange(int lv) {
        switch (lv) {
            case ReputationSC.ENEMY:
                return Lang.tr("sc.manual.world.cell.local");
            case ReputationSC.VIOLATOR:
                return Lang.tr("sc.manual.world.cell.below", ReputationSC.STRANGER_FROM);
            case ReputationSC.STRANGER:
                return span(ReputationSC.STRANGER_FROM, ReputationSC.ACQUAINTANCE_FROM - 1);
            case ReputationSC.ACQUAINTANCE:
                return span(ReputationSC.ACQUAINTANCE_FROM, ReputationSC.PARTNER_FROM - 1);
            case ReputationSC.PARTNER:
                return span(ReputationSC.PARTNER_FROM, ReputationSC.OWN_FROM - 1);
            case ReputationSC.OWN:
                return span(ReputationSC.OWN_FROM, ReputationSC.HONOURED_FROM - 1);
            default:
                return ReputationSC.HONOURED_FROM + "+";
        }
    }

    /** One way up: sc.manual.world.trust.up.<key> and its .desc (with `args`). */
    private static void gain(BookEntry e, ItemStack icon, String key, Object... args) {
        String k = "sc.manual.world.trust.up." + key;
        e.add(BookEl.item(icon, Lang.tr(k), Lang.tr(k + ".desc", args)));
    }

    // ------------------------------------------------------------------ «Стражи купола»

    private static BookEntry wardens(BookChapter c) {
        ItemStack egg = egg(MobEntitiesSC.ID_WARDEN);
        BookEntry e = new BookEntry("world.wardens", c, egg != null ? egg : nanoPiece(0), Lang.tr("sc.manual.world.wardens.title"));
        e.add(BookEl.title(Lang.tr("sc.manual.world.wardens.title"))).addAll(BookContent.paras("sc.manual.world.wardens"));

        // the ranks (ПГ-2): package N's numbers by name, the blades' and beams' caps of MobCombatSC
        BookEl t = BookEl.table(Lang.tr("sc.manual.world.t.rank"), Lang.tr("sc.manual.world.t.armour"), Lang.tr("sc.manual.world.t.reserve"),
                Lang.tr("sc.manual.world.t.hp"), Lang.tr("sc.manual.world.t.where"));
        t.row(nanoPiece(1), Lang.tr(NpcKindSC.SENTRY.langKey()), Lang.tr("sc.manual.world.cell.suit4", suitName(ArmorSuit.NANO)),
                span((int) stat(WARDEN, "SENTRY_RESERVE_MIN", 20F), (int) stat(WARDEN, "SENTRY_RESERVE_MAX", 40F)), n(stat(WARDEN, "SENTRY_HP", 40F)),
                sizesWith(false));
        t.row(new ItemStack(ModItems.ARMOR.get(ArmorSuit.QUANTUM)[1]), Lang.tr(NpcKindSC.SENIOR_WARDEN.langKey()),
                Lang.tr("sc.manual.world.cell.suit4.helium", suitName(ArmorSuit.QUANTUM)),
                span((int) stat(WARDEN, "SENIOR_RESERVE_MIN", 40F), (int) stat(WARDEN, "SENIOR_RESERVE_MAX", 70F)), n(stat(WARDEN, "SENIOR_HP", 50F)),
                sizesWith(true));
        e.add(t);
        e.add(BookEl.item(blade(BladeType.NANO), Lang.tr(NpcKindSC.SENTRY.langKey()), Lang.tr("sc.manual.world.wardens.weapon.sentry",
                blade(BladeType.NANO).getDisplayName(), MobCombatSC.BLADE[BladeType.NANO.ordinal()], weapon(WeaponType.ION_CUTTER).getDisplayName(),
                MobCombatSC.BEAM[WeaponType.ION_CUTTER.ordinal()], sec((int) stat(BEAM, "RELOAD_TICKS", 40F)))));
        e.add(BookEl.item(blade(BladeType.QUANTUM), Lang.tr(NpcKindSC.SENIOR_WARDEN.langKey()), Lang.tr("sc.manual.world.wardens.weapon.senior",
                blade(BladeType.QUANTUM).getDisplayName(), MobCombatSC.BLADE[BladeType.QUANTUM.ordinal()], weapon(WeaponType.PLASMA_RIFLE).getDisplayName(),
                MobCombatSC.BEAM[WeaponType.PLASMA_RIFLE.ordinal()], sec((int) stat(WARDEN, "SENIOR_AIM_TICKS", 20F)))));

        // their work (ПГ-3, ПГ-5, ПГ-10)
        e.add(BookEl.head(Lang.tr("sc.manual.world.wardens.workhead")));
        e.add(BookEl.item(showcase(BlockShowcaseSC.CHARGE_PAD), Lang.tr("sc.manual.world.wardens.pad"), Lang.tr("sc.manual.world.wardens.pad.desc",
                pct(stat(WARDEN, "RECHARGE_BELOW", 0.3F)), pct(stat(WARDEN, "RECHARGE_UNTIL", 0.9F)))));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.wardens.patrol"), Lang.tr("sc.manual.world.wardens.patrol.desc",
                n(stat(WARDEN, "PATROL_OUT", 16F)), n(stat(WARDEN, "HUNT_OUT", 24F)))));
        e.add(BookEl.item(null, Lang.tr("sc.manual.world.wardens.help"), Lang.tr("sc.manual.world.wardens.help.desc",
                levelName(ReputationSC.PARTNER), n(stat(WARDEN, "HELP_RADIUS", 24F)))));

        // the provocation (ПГ-6..ПГ-8): ViolationRulesSC
        e.add(BookEl.head(Lang.tr("sc.manual.world.wardens.provokehead")));
        e.addAll(BookContent.paras("sc.manual.world.wardens.provoke", new Object[]{minutes(ViolationRulesSC.AGGRO_TICKS)},
                new Object[]{sec(ViolationRulesSC.WARN_TICKS), ViolationRulesSC.WALK_AWAY},
                new Object[]{ViolationRulesSC.GUARD_REACH, ViolationRulesSC.leash(SettlementSizeSC.OUTPOST.radiusMax),
                        ViolationRulesSC.leash(SettlementSizeSC.SETTLEMENT.radiusMax),
                        span(ViolationRulesSC.leash(SettlementSizeSC.CITY.radiusMin), ViolationRulesSC.leash(SettlementSizeSC.CITY.radiusMax))},
                new Object[]{-ViolationRulesSC.penalty(ViolationRulesSC.HIT_WARDEN, 1)}));

        // death (ПГ-9) and ПЭ-1
        ItemStack clone = showcase(BlockShowcaseSC.CLONE_CAPSULE);
        e.add(BookEl.item(clone, clone != null ? clone.getDisplayName() : Lang.tr("tile.siliconage.showcase.cloneCapsule.name"),
                Lang.tr("sc.manual.world.wardens.clone", minutes(NpcKindSC.GUARD_RESPAWN_TICKS), SettlementSC.RESPAWN_CLEAR)));
        e.add(BookEl.item(new ItemStack(ModItems.empGun), Lang.tr("sc.manual.world.wardens.emp"), Lang.tr("sc.manual.world.wardens.emp.desc")));
        e.add(BookEl.item(egg, egg != null ? egg.getDisplayName() : mobName(EntityWardenSC.NAME), Lang.tr("sc.manual.world.wardens.egg")));
        e.add(BookEl.link("world.trust", Lang.tr("sc.manual.world.trust.title")));
        e.add(BookEl.link("world.armoured", Lang.tr("sc.manual.world.armoured.title")));
        e.about(egg);
        return e;
    }

    /** The sizes that have guards of a rank, by name («Пост, Посёлок, Город»). */
    private static String sizesWith(boolean seniors) {
        StringBuilder sb = new StringBuilder();
        for (SettlementSizeSC s : SettlementSizeSC.values()) {
            if (seniors ? s.seniors > 0 : s.sentries > 0) {
                sb.append(sb.length() == 0 ? "" : ", ").append(Lang.tr(s.langKey()));
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ «Причалы и возврат»

    private static BookEntry docks(BookChapter c) {
        ItemStack beacon = dock(BlockDockSC.BEACON);
        BookEntry e = new BookEntry("world.docks", c, beacon != null ? beacon : com.sc.block.BlockBridgeSC.stack(com.sc.block.BlockBridgeSC.CONTROLLER, 1),
                Lang.tr("sc.manual.world.docks.title"));
        StringBuilder withDock = new StringBuilder();
        for (SettlementSizeSC s : SettlementSizeSC.values()) {
            if (s.dock) {
                withDock.append(withDock.length() == 0 ? "" : ", ").append(Lang.tr(s.langKey()));
            }
        }
        e.add(BookEl.title(Lang.tr("sc.manual.world.docks.title"))).addAll(BookContent.paras("sc.manual.world.docks",
                new Object[]{withDock.toString()}, new Object[]{SettlementSC.DOCK_ZONE, SettlementSC.DOCK_BELOW, SettlementSC.DOCK_ABOVE}));
        e.add(BookEl.item(dock(BlockDockSC.PLATE), dockName(BlockDockSC.PLATE), Lang.tr("sc.manual.world.docks.plate")));
        e.add(BookEl.item(beacon, dockName(BlockDockSC.BEACON), Lang.tr("sc.manual.world.docks.beacon", TravelRulesSC.DOCK_SNAP, BridgeMathSC.BEACON_DISCOUNT)));

        // the bridge (ПР-2, ПР-3)
        e.add(BookEl.head(Lang.tr("sc.manual.world.docks.bridgehead"), com.sc.block.BlockBridgeSC.stack(com.sc.block.BlockBridgeSC.CONTROLLER, 1)));
        e.addAll(BookContent.paras("sc.manual.world.docks.bridge"));

        // the way back (ПР-4..ПР-6)
        e.add(BookEl.head(Lang.tr("sc.manual.world.docks.backhead")));
        ItemStack capsule = SettlementItemsSC.capsules(1);
        String capsuleSeller = sellerLine(capsule);
        e.add(BookEl.item(capsule, capsule != null ? capsule.getDisplayName() : Lang.tr("item.siliconage.returnCapsule.name"),
                Lang.tr("sc.manual.world.docks.capsule", sec(TravelRulesSC.CAPSULE_TICKS)) + (capsuleSeller == null ? "" : " " + capsuleSeller)));
        ItemStack map = SettlementItemsSC.settlementMap == null ? null : new ItemStack(SettlementItemsSC.settlementMap);
        String mapSeller = sellerLine(map);
        e.add(BookEl.item(map, map != null ? map.getDisplayName() : Lang.tr("item.siliconage.settlementMap.name"),
                Lang.tr("sc.manual.world.docks.map", TravelRulesSC.MAP_DOCKS) + (mapSeller == null ? "" : " " + mapSeller)));
        e.add(BookEl.item(new ItemStack(Items.skull, 1, 0), Lang.tr("sc.manual.world.docks.death"),
                Lang.tr("sc.manual.world.docks.death.desc", TravelRulesSC.DEATH_SCATTER, TravelRulesSC.DEATH_CLEAR)));
        e.add(BookEl.link("bridge", Lang.tr("sc.manual.bridge.title")));
        e.add(BookEl.link("world.settlements", Lang.tr("sc.manual.world.settlements.title")));
        e.add(BookEl.link("world.substrate", Lang.tr("sc.manual.world.substrate.title")));
        if (SettlementBlocksSC.dock != null) {
            e.about(new ItemStack(SettlementBlocksSC.dock, 1, OreDictionary.WILDCARD_VALUE));
        }
        e.about(capsule, map);
        return e;
    }

    // ------------------------------------------------------------------ Ф2's stacks and numbers

    private static ItemStack core() {
        return SettlementBlocksSC.core == null ? null : new ItemStack(SettlementBlocksSC.core);
    }

    private static ItemStack showcase(int meta) {
        return SettlementBlocksSC.showcase == null ? null : new ItemStack(SettlementBlocksSC.showcase, 1, meta);
    }

    private static ItemStack dock(int meta) {
        return SettlementBlocksSC.dock == null ? null : new ItemStack(SettlementBlocksSC.dock, 1, meta);
    }

    private static String dockName(int meta) {
        ItemStack s = dock(meta);
        return s != null ? s.getDisplayName() : Lang.tr("tile.siliconage.dock." + BlockDockSC.nameOf(meta) + ".name");
    }

    /** A profession's workshop machine (the Elder: the core). */
    private static ItemStack kindIcon(NpcKindSC k) {
        switch (k) {
            case ELDER:
                return core();
            case TECHNOLOGIST:
                return showcase(BlockShowcaseSC.CVD);
            case METALLURGIST:
                return showcase(BlockShowcaseSC.BLAST_FURNACE);
            case GAS_TECH:
                return showcase(BlockShowcaseSC.AIR_SEPARATOR);
            case CARTOGRAPHER:
                return showcase(BlockShowcaseSC.FOCUSER);
            default:
                return showcase(BlockShowcaseSC.ARMOUR_STATION);
        }
    }

    /** «3-5», or «24» when both ends agree. */
    private static String span(int lo, int hi) {
        return lo == hi ? String.valueOf(lo) : lo + "-" + hi;
    }

    /** A world tick of the day as the clock shows it (tick 0 is 6:00): «17:30». */
    static String clock(int tick) {
        int t = ((tick % 24000) + 24000) % 24000;
        return String.format(Locale.ROOT, "%d:%02d", (6 + t / 1000) % 24, t % 1000 * 60 / 1000);
    }

    /** Ticks as whole game days: «3». */
    private static String days(long ticks) {
        return n(ticks / (float) SettlementSC.DAY);
    }

    /** Ticks as minutes: «5». */
    private static String minutes(int ticks) {
        return n(ticks / 1200F);
    }

    // ------------------------------------------------------------------ shared with NEI's «Как получить» (ObtainHandlerSC)

    /** Who drops trophy `meta` in this version (the creative tab and NEI show only these, ПТ-7), or null: a later phase. */
    public static String trophyDroppers(int meta) {
        String both = mobName(EntityLinemanSC.NAME) + ", " + mobName(EntityMarauderSC.NAME);
        if (meta >= TrophyTableSC.armorMeta(ArmorSuit.NANO, 0) && meta <= TrophyTableSC.armorMeta(ArmorSuit.NANO, 3)) {
            return both;
        }
        if (meta == TrophyTableSC.bladeMeta(BladeType.NANO)) {
            return mobName(EntityMarauderSC.NAME);
        }
        if (meta == TrophyTableSC.weaponMeta(WeaponType.ION_CUTTER)) {
            return both;
        }
        return null;
    }

    /** Trophy `meta`'s chance of one roll as percent text, the server's multiplier included ("2", "1,5"); null outside the tables. */
    public static String trophyChancePct(int meta) {
        if (meta >= 0 && meta < TrophyTableSC.META_BLADE) {
            return pct(trophyChance(TrophyTableSC.PIECE[Math.min(TrophyTableSC.PIECE.length - 1, meta / 4)]));
        }
        if (meta >= TrophyTableSC.META_BLADE && meta < TrophyTableSC.META_WEAPON) {
            return pct(trophyChance(TrophyTableSC.BLADE[Math.min(TrophyTableSC.BLADE.length - 1, meta - TrophyTableSC.META_BLADE)]));
        }
        if (meta >= TrophyTableSC.META_WEAPON && meta < TrophyTableSC.META_WEAPON + TrophyTableSC.WEAPON.length) {
            return pct(trophyChance(TrophyTableSC.WEAPON[meta - TrophyTableSC.META_WEAPON]));
        }
        return null;
    }

    /** The real item a trophy of `meta` stands for (its armour piece, blade, weapon, ПЭ-1 / ПЭ-1М), or null. */
    public static ItemStack realOfTrophy(int meta) {
        ArmorSuit[] suits = ArmorSuit.values();
        if (meta >= 0 && meta < TrophyTableSC.META_BLADE && meta / 4 < suits.length) {
            return new ItemStack(ModItems.ARMOR.get(suits[meta / 4])[meta % 4]);
        }
        BladeType[] blades = BladeType.values();
        if (meta >= TrophyTableSC.META_BLADE && meta - TrophyTableSC.META_BLADE < blades.length && meta < TrophyTableSC.META_WEAPON) {
            return blade(blades[meta - TrophyTableSC.META_BLADE]);
        }
        WeaponType[] weapons = WeaponType.values();
        if (meta >= TrophyTableSC.META_WEAPON && meta - TrophyTableSC.META_WEAPON < weapons.length && meta < TrophyTableSC.META_PE1) {
            return weapon(weapons[meta - TrophyTableSC.META_WEAPON]);
        }
        if (meta == TrophyTableSC.META_PE1 || meta == TrophyTableSC.META_PE1M) {
            return new ItemStack(ModItems.empGun, 1, meta == TrophyTableSC.META_PE1M ? com.sc.emp.ItemEmpGunSC.META_UPGRADED : com.sc.emp.ItemEmpGunSC.META_BASIC);
        }
        return null;
    }

    /** The NEI line of loot `meta`: who drops it and how often. */
    public static String lootLine(int meta) {
        if (meta == LOOT_NANO_SCRAP) {
            return Lang.tr("sc.manual.world.loot.nanoScrap", pct(stat(DROPS, "SCRAP_CHANCE", 0.4F)));
        }
        if (meta == LOOT_STOLEN_CARGO) {
            return Lang.tr("sc.manual.world.loot.stolenCargo", pct(stat(DROPS, "CARGO_CHANCE", 0.05F)), ReputationSC.CARGO);
        }
        return Lang.tr("sc.manual.world.loot.shellFirmware");
    }

    /**
     * Ф2, NEI's «Как получить» and the docks' article: who sells `s` in the settlements (TradeTableSC) - «Картограф
     * прокола: 8 КЧ, с уровня «Чужак», 2 в сутки» -, or null if no deal gives it.
     */
    public static String sellerLine(ItemStack s) {
        TradeOfferSC o = dealFor(s);
        if (o == null) {
            return null;
        }
        return Lang.tr("sc.manual.world.trade.seller", Lang.tr(o.kind.langKey()), chipsOf(o), levelName(o.minLevel), limitText(o));
    }

    /** The deal whose output is `s` (an item of OUT_ITEM, or the map of OUT_MAP), null none. */
    static TradeOfferSC dealFor(ItemStack s) {
        if (s == null || s.getItem() == null) {
            return null;
        }
        for (TradeOfferSC o : TradeTableSC.all()) {
            if (o.outKind == TradeOfferSC.OUT_MAP ? s.getItem() == SettlementItemsSC.settlementMap
                    : o.outKind == TradeOfferSC.OUT_ITEM && o.out != null && o.out.matches(s)) {
                return o;
            }
        }
        return null;
    }

    /** What the handbook says of rock `meta` (where it lies, what it drops). */
    public static String rockLine(int meta) {
        return Lang.tr("sc.manual.world.block." + ROCK_NAMES[meta >= 0 && meta < ROCK_NAMES.length ? meta : 0]);
    }

    public static String mobName(String name) {
        return Lang.tr("entity.SiliconAge." + name + ".name");
    }

    /** A share as percent text: 0.015 -> "1.5" ("1,5" in Russian), 0.02 -> "2". */
    public static String pct(float share) {
        return BookContent.number(Math.round(share * 1000F) / 10F);
    }

    // ------------------------------------------------------------------ package M's numbers

    /**
     * Public static number `field` of package M's class `cls`, or `plan` (docs/plan-substrate.md §6, §7) while it has
     * none - the page builds before M's constants exist; the self-test prints what is still on the plan's value.
     */
    static float stat(String cls, String field, float plan) {
        try {
            Object v = Class.forName(cls, true, BookWorldSC.class.getClassLoader()).getField(field).get(null);
            if (v instanceof Number) {
                return ((Number) v).floatValue();
            }
        } catch (Throwable t) {
            // not there (yet): the plan's number
        }
        synchronized (UNRESOLVED) {
            UNRESOLVED.add(cls.substring(cls.lastIndexOf('.') + 1) + "." + field);
        }
        return plan;
    }

    /** The self-test: package M's constants the bestiary still takes from the plan (after a build of the book). */
    public static List<String> unresolved() {
        synchronized (UNRESOLVED) {
            return new ArrayList<String>(UNRESOLVED);
        }
    }

    // ------------------------------------------------------------------

    private static String[] trophyKeys() {
        String[] out = new String[TrophyTableSC.METAS];
        String[] pieces = {"Helmet", "Chestplate", "Leggings", "Boots"};
        for (ArmorSuit s : ArmorSuit.values()) {
            for (int t = 0; t < 4; t++) {
                int m = TrophyTableSC.armorMeta(s, t);
                if (m >= 0 && m < out.length) {
                    out[m] = s.textureName + pieces[t];
                }
            }
        }
        for (BladeType b : BladeType.values()) {
            int m = TrophyTableSC.bladeMeta(b);
            if (m >= 0 && m < out.length) {
                String suit = b.name().toLowerCase(Locale.ROOT);
                out[m] = "blade" + Character.toUpperCase(suit.charAt(0)) + suit.substring(1);
            }
        }
        for (WeaponType w : WeaponType.values()) {
            int m = TrophyTableSC.weaponMeta(w);
            if (m >= 0 && m < out.length) {
                out[m] = w.textureName;
            }
        }
        out[TrophyTableSC.META_PE1] = "pe1";
        out[TrophyTableSC.META_PE1M] = "pe1m";
        return out;
    }

    static ItemStack rock(int meta) {
        return SubstrateBlocksSC.rock == null ? null : new ItemStack(SubstrateBlocksSC.rock, 1, meta);
    }

    private static String rockName(int meta) {
        ItemStack s = rock(meta);
        return s != null ? s.getDisplayName() : Lang.tr("tile.siliconage.substrateRock." + ROCK_NAMES[meta] + ".name");
    }

    private static ItemStack druse() {
        return SubstrateBlocksSC.druse == null ? null : new ItemStack(SubstrateBlocksSC.druse);
    }

    /** The Substrate's ore of that kind, else the mod's ordinary one (the same meta). */
    private static ItemStack ore(OreEntry ore) {
        return new ItemStack(SubstrateBlocksSC.ore != null ? SubstrateBlocksSC.ore : ModBlocks.oreSC, 1, ore.meta());
    }

    private static ItemStack egg(int poolId) {
        return MobItemsSC.spawnEgg == null ? null : new ItemStack(MobItemsSC.spawnEgg, 1, poolId);
    }

    private static ItemStack trophy(int meta) {
        return MobItemsSC.trophy == null ? null : new ItemStack(MobItemsSC.trophy, 1, meta);
    }

    private static ItemStack loot(int meta) {
        return MobItemsSC.combatLoot == null ? null : new ItemStack(MobItemsSC.combatLoot, 1, meta);
    }

    private static String lootName(int meta) {
        ItemStack s = loot(meta);
        return s != null ? s.getDisplayName() : Lang.tr("item.siliconage.combatLoot." + LOOT_NAMES[meta] + ".name");
    }

    private static ItemStack nanoPiece(int armorType) {
        return new ItemStack(ModItems.ARMOR.get(ArmorSuit.NANO)[armorType]);
    }

    private static ItemStack blade(BladeType t) {
        return new ItemStack(ModItems.BLADES.get(t));
    }

    private static ItemStack weapon(WeaponType t) {
        return new ItemStack(ModItems.WEAPONS.get(t));
    }

    private static String suitName(ArmorSuit s) {
        return Lang.tr("sc.suit." + s.name().toLowerCase(Locale.ROOT));
    }

    /** A full set's armour points (Nano 20, Quantum 22, Exo 24, Singular 28). */
    static int suitPoints(ArmorSuit s) {
        int p = 0;
        for (int t = 0; t < 4; t++) {
            p += s.material.getDamageReductionAmount(t);
        }
        return p;
    }

    /** A full set of iron armour's points: what a mob's suppressed / emergency armour gives (15). */
    static int ironPoints() {
        int p = 0;
        for (int t = 0; t < 4; t++) {
            p += MobArmorMathSC.ironPoints(t);
        }
        return p;
    }

    private static int empTicks(ArmorSuit s, EmpMode m) {
        return EmpMathSC.durationTicks(s, m, false, 0, false, ConfigSC.empDurationMultiplier);
    }

    /** Seconds the siphon takes to drain a full reserve of that suit (a share of the full reserve a second). */
    private static String siphonSeconds(ArmorSuit s) {
        double pctPerSecond = EmpMathSC.siphonPctPerSecond(s, false, 0, ConfigSC.empSiphonRateMultiplier);
        return pctPerSecond <= 0 ? "-" : String.valueOf(Math.round(100D / pctPerSecond));
    }

    /** One roll's chance at the base of a category, with the server's gearDropMultiplier (no Looting, no bad luck). */
    private static float trophyChance(float base) {
        return TrophyTableSC.chance(base, 0, false, false, SubstrateConfigSC.gearDropMultiplier, 1);
    }

    private static long eu(long base, float mul) {
        return (long) (base * (double) mul);
    }

    private static String dimName(int id) {
        return Lang.trOr("sc.wl.dim." + id, Lang.tr("sc.wl.dim.other", id));
    }

    /** "4", "1.5" ("1,5" in Russian): one decimal at most. */
    private static String n(float v) {
        return BookContent.number(Math.round(v * 10F) / 10F);
    }

    /** "0.5" ("0,5"): a multiplier with up to two decimals. */
    private static String multiplier(float m) {
        return BookContent.number(Math.round(m * 100F) / 100F);
    }

    /** Ticks as seconds: "16", "8.8" ("8,8" in Russian). */
    private static String sec(int ticks) {
        return n(ticks / 20F);
    }
}
