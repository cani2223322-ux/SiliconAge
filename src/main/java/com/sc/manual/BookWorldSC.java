package com.sc.manual;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

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
import com.sc.mob.MobEntitiesSC;
import com.sc.mob.MobItemsSC;
import com.sc.mob.SpawnRulesSC;
import com.sc.mob.TrophyTableSC;
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
 * The bestiary's own numbers (health, reserve, weapons, drop chances) live in package M's classes; they are read by
 * name (stat) so this chapter builds before M's constants exist - the plan's value until then, and the self-test
 * lists every one still on it (unresolved). TODO(D, after M): direct references.
 */
public final class BookWorldSC {

    /** The rock's metas by name, SubstrateBlocksSC's order (tile.siliconage.substrateRock.<name>.name, sc.tooltip.substrateRock.<name>). */
    public static final String[] ROCK_NAMES = {"crystallite", "oxideViolet", "oxideBlue", "oxideGold", "oxideGreen", "copperTrace", "traceNode",
            "quartzTurf"};
    /** The combat loot by meta (ItemCombatLootSC.LootKind, appended only): item.siliconage.combatLoot.<name>.name, sc.tooltip.loot.<name>. */
    public static final String[] LOOT_NAMES = {"shellFirmware", "nanoScrap"};
    public static final int LOOT_FIRMWARE = 0, LOOT_NANO_SCRAP = 1;
    /** The trophy's lang key by meta (item.siliconage.trophy.<key>.name; the metas of TrophyTableSC). */
    public static final String[] TROPHY_KEYS = trophyKeys();

    /** Package M's classes the bestiary reads its numbers from. */
    private static final String LINEMAN = "com.sc.mob.EntityLinemanSC", MARAUDER = "com.sc.mob.EntityMarauderSC", DROPS = "com.sc.mob.MobDropsSC",
            BEAM = "com.sc.mob.ai.EntityAIBeamAttackSC";
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
        for (MachineRecipe r : lootRecipes()) {
            e.add(BookEl.machine(r));
        }
        e.add(BookEl.link("world.lineman", mobName(EntityLinemanSC.NAME)));
        e.add(BookEl.link("world.marauder", mobName(EntityMarauderSC.NAME)));
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
        return Lang.tr("sc.manual.world.loot.shellFirmware");
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
