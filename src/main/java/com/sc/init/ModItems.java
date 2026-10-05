package com.sc.init;

import com.sc.item.ItemArmorChipSC;
import com.sc.item.ItemArmorSC;
import com.sc.item.ItemFieldLinkModule;
import com.sc.item.ItemMaterialSC;
import com.sc.item.ItemSCManual;
import com.sc.item.ItemSiliconMaterialSC;
import com.sc.item.ItemSimpleSC;
import com.sc.item.ItemToolSC;
import com.sc.item.ItemWeaponSC;
import com.sc.item.MaterialItemKind;
import com.sc.util.ArmorSuit;
import com.sc.util.Material;
import com.sc.util.SCToolType;
import com.sc.util.WeaponType;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/**
 * Step 3 (01_recipes.md §11): registers the 5 generic material items (crushedOre /
 * purifiedCrushedOre / dust / dustTiny / ingot) and their OreDictionary entries, so other
 * mods can recognize e.g. "ingotCopper" or "dustTantalum" regardless of which mod made them.
 * No recipes are wired here - crafting/machine recipes are step 11's job (linking these items
 * together, and to the machines from steps 5-6, is out of scope for base item registration).
 */
public final class ModItems {

    public static ItemMaterialSC crushedOre;
    public static ItemMaterialSC purifiedCrushedOre;
    public static ItemMaterialSC dust;
    public static ItemMaterialSC dustTiny;
    public static ItemMaterialSC ingot;
    public static ItemSiliconMaterialSC siliconMaterial;
    public static ItemSimpleSC coke;
    public static ItemSimpleSC leadFrame3;
    public static ItemSimpleSC leadFrame16;
    public static ItemSimpleSC leadFrame40;
    public static ItemSimpleSC compound;
    public static ItemSimpleSC rubber;
    public static ItemSimpleSC rubberBlue;
    public static ItemSimpleSC rubberHeatResist;
    public static ItemSimpleSC alFoil;
    public static ItemSimpleSC liquidHeCell;
    public static ItemSimpleSC deuteriumCell;
    /** The Singular Matter cell (a tank-in-an-item, 1000 mB). */
    public static com.sc.item.ItemSingularCellSC singularCell;
    /** The Ground / Space Bridge, stage 2: the remotes (damage 0 / 1), the coordinator, the Armour Link Module. */
    public static com.sc.item.ItemBridgeRemoteSC bridgeRemote;
    public static com.sc.item.ItemCoordinatorSC coordinator;
    public static com.sc.item.ItemBridgeLinkModuleSC bridgeLinkModule;
    public static com.sc.item.ItemWearPartSC windRotor, isotopeCapsule;
    public static com.sc.item.ItemQuarryModuleSC quarryModule;
    public static final java.util.List<com.sc.item.ItemDrillHeadSC> DRILL_HEADS = new java.util.ArrayList<com.sc.item.ItemDrillHeadSC>();
    public static ItemSimpleSC oreScanner;
    public static com.sc.item.ItemAreaCardSC areaCard;
    public static com.sc.item.ItemOreLensSC oreLens;
    /** Portable batteries LV..XV (the tier in the damage). */
    public static com.sc.item.ItemBatterySC battery;
    /** Wireless energy: the link card (transmitter -> receiver), the entangled crystal and its halves (quantum pair). */
    public static com.sc.item.ItemLinkCardSC linkCard;
    public static com.sc.item.ItemEntangledCrystalSC entangledCrystal;
    /** Radiation: the lead suit (helmet, jacket, trousers, boots), the dosimeter, the radioprotector. */
    public static final com.sc.item.ItemLeadSuitSC[] leadSuit = new com.sc.item.ItemLeadSuitSC[4];
    public static com.sc.item.ItemDosimeterSC dosimeter;
    public static com.sc.item.ItemRadioprotectorSC radioprotector;
    public static final java.util.List<com.sc.item.ItemWrenchSC> WRENCHES = new java.util.ArrayList<com.sc.item.ItemWrenchSC>();
    public static final java.util.Map<SCToolType, ItemToolSC> TOOLS = new java.util.EnumMap<SCToolType, ItemToolSC>(SCToolType.class);
    public static final java.util.Map<ArmorSuit, ItemArmorSC[]> ARMOR = new java.util.EnumMap<ArmorSuit, ItemArmorSC[]>(ArmorSuit.class);
    public static ItemArmorChipSC armorChip;
    public static final java.util.Map<WeaponType, ItemWeaponSC> WEAPONS = new java.util.EnumMap<WeaponType, ItemWeaponSC>(WeaponType.class);
    public static final java.util.Map<com.sc.util.DrillType, com.sc.item.ItemDrillSC> DRILLS =
            new java.util.EnumMap<com.sc.util.DrillType, com.sc.item.ItemDrillSC>(com.sc.util.DrillType.class);
    public static final java.util.Map<com.sc.util.BladeType, com.sc.item.ItemBladeSC> BLADES =
            new java.util.EnumMap<com.sc.util.BladeType, com.sc.item.ItemBladeSC>(com.sc.util.BladeType.class);
    public static ItemFieldLinkModule fieldLinkModule;
    /** IC2-style machine upgrades (UpgradeType). */
    public static com.sc.item.ItemUpgradeSC upgrade;
    /** Pneumatic tube connector items (Ender IO style): item filters and the speed upgrade. */
    public static com.sc.item.ItemItemFilterSC itemFilter;
    public static com.sc.item.ItemTubeSpeedSC tubeSpeedUpgrade;
    public static ItemSCManual manual;
    /** NEI-only stand-ins for fluids (ItemFluidDropSC) - never obtainable in play. */
    public static com.sc.item.ItemFluidDropSC fluidDrop;
    /** Buckets of the mod's fluids (ItemFluidBucketSC) - into machines and tanks, never onto the ground. */
    public static com.sc.item.ItemFluidBucketSC fluidBucket;
    /** Step 11: every remaining §1/§2/§5-§7 component that's just a plain item with no metadata/behaviour, keyed by its own name (== texture name == unlocalized suffix). */
    public static final java.util.Map<String, ItemSimpleSC> COMPONENTS = new java.util.LinkedHashMap<String, ItemSimpleSC>();   // registration order (the creative tab lists them so)

    private ModItems() {
    }

    public static ItemSimpleSC component(String name) {
        return COMPONENTS.get(name);
    }

    public static void init() {
        crushedOre = registerKind(MaterialItemKind.CRUSHED_ORE, "crushedOreSC");
        purifiedCrushedOre = registerKind(MaterialItemKind.PURIFIED_CRUSHED_ORE, "purifiedCrushedOreSC");
        dust = registerKind(MaterialItemKind.DUST, "dustSC");
        dustTiny = registerKind(MaterialItemKind.DUST_TINY, "dustTinySC");
        ingot = registerKind(MaterialItemKind.INGOT, "ingotSC");

        siliconMaterial = new ItemSiliconMaterialSC();
        GameRegistry.registerItem(siliconMaterial, "siliconMaterialSC");

        coke = new ItemSimpleSC("coke", "coke");
        GameRegistry.registerItem(coke, "coke");
        OreDictionary.registerOre("coke", new ItemStack(coke));

        // §2/§14.3: Packager (§3 step 14) needs these - minimal registration now (their own
        // crafting-table recipes are step 11's job, same as everywhere else in this file).
        leadFrame3 = new ItemSimpleSC("leadFrame3", "leadFrame3");
        GameRegistry.registerItem(leadFrame3, "leadFrame3");
        leadFrame16 = new ItemSimpleSC("leadFrame16", "leadFrame16");
        GameRegistry.registerItem(leadFrame16, "leadFrame16");
        leadFrame40 = new ItemSimpleSC("leadFrame40", "leadFrame40");
        GameRegistry.registerItem(leadFrame40, "leadFrame40");
        compound = new ItemSimpleSC("compound", "compound");
        GameRegistry.registerItem(compound, "compound");

        // §14/§11.6/§1: step 6's Refinery/Kiln/Fluid Cell Filler/Rolling Machine recipes need these.
        rubber = new ItemSimpleSC("rubber", "rubber");
        GameRegistry.registerItem(rubber, "rubber");
        OreDictionary.registerOre("rubber", new ItemStack(rubber));
        OreDictionary.registerOre("itemRubber", new ItemStack(rubber)); // IC2's name - its rubber then fits SC recipes

        rubberBlue = new ItemSimpleSC("rubberBlue", "rubberBlue");
        GameRegistry.registerItem(rubberBlue, "rubberBlue");
        OreDictionary.registerOre("rubberBlue", new ItemStack(rubberBlue));

        rubberHeatResist = new ItemSimpleSC("rubberHeatResist", "rubberHeatResist");
        GameRegistry.registerItem(rubberHeatResist, "rubberHeatResist");
        OreDictionary.registerOre("rubberHeatResist", new ItemStack(rubberHeatResist));

        alFoil = new ItemSimpleSC("alFoil", "alFoil");
        GameRegistry.registerItem(alFoil, "alFoil");

        liquidHeCell = new ItemSimpleSC("liquidHeCell", "liquidHeCell");
        GameRegistry.registerItem(liquidHeCell, "liquidHeCell");

        deuteriumCell = new ItemSimpleSC("deuteriumCell", "deuteriumCell");
        GameRegistry.registerItem(deuteriumCell, "deuteriumCell");

        for (SCToolType type : SCToolType.values()) {
            ItemToolSC tool = new ItemToolSC(type);
            GameRegistry.registerItem(tool, "tool" + type.name());
            TOOLS.put(type, tool);
        }

        // §6/§16: armor - 4 pieces (armorType 0=helmet,1=chest,2=legs,3=boots) per suit.
        String[] pieceNames = {"helmet", "chestplate", "leggings", "boots"};
        for (ArmorSuit suit : ArmorSuit.values()) {
            ItemArmorSC[] pieces = new ItemArmorSC[4];
            for (int armorType = 0; armorType < 4; armorType++) {
                ItemArmorSC piece = new ItemArmorSC(suit, armorType, pieceNames[armorType]);
                GameRegistry.registerItem(piece, "armor" + suit.name() + pieceNames[armorType]);
                pieces[armorType] = piece;
            }
            ARMOR.put(suit, pieces);
        }

        armorChip = new ItemArmorChipSC();
        GameRegistry.registerItem(armorChip, "armorChipSC");

        // §7/§16: weapons.
        for (WeaponType type : WeaponType.values()) {
            ItemWeaponSC weapon = new ItemWeaponSC(type);
            GameRegistry.registerItem(weapon, "weapon" + type.name());
            WEAPONS.put(type, weapon);
        }

        // Energy blades, one per suit (appended - registry names are what worlds keep).
        for (com.sc.util.BladeType type : com.sc.util.BladeType.values()) {
            com.sc.item.ItemBladeSC blade = new com.sc.item.ItemBladeSC(type);
            GameRegistry.registerItem(blade, "blade" + type.name());
            BLADES.put(type, blade);
        }

        // Electric drills, one per suit (appended).
        for (com.sc.util.DrillType type : com.sc.util.DrillType.values()) {
            com.sc.item.ItemDrillSC drill = new com.sc.item.ItemDrillSC(type);
            GameRegistry.registerItem(drill, "drill" + type.name());
            DRILLS.put(type, drill);
        }

        // §7/§9: Field Generator cluster linking tool.
        fieldLinkModule = new ItemFieldLinkModule();
        GameRegistry.registerItem(fieldLinkModule, "fieldLinkModule");

        // Generator wear parts (appended): 6 hours of turning, a day of decay.
        windRotor = new com.sc.item.ItemWearPartSC("windRotor", 21600);
        GameRegistry.registerItem(windRotor, "windRotor");
        isotopeCapsule = new com.sc.item.ItemWearPartSC("isotopeCapsule", 86400);
        GameRegistry.registerItem(isotopeCapsule, "isotopeCapsule");

        // Quarry: modules, drill heads, the ore scanner and the area card (appended).
        quarryModule = new com.sc.item.ItemQuarryModuleSC();
        GameRegistry.registerItem(quarryModule, "quarryModule");
        for (com.sc.item.ItemDrillHeadSC.Kind k : com.sc.item.ItemDrillHeadSC.Kind.values()) {
            com.sc.item.ItemDrillHeadSC h = new com.sc.item.ItemDrillHeadSC(k);
            GameRegistry.registerItem(h, k.name);
            DRILL_HEADS.add(h);
        }
        oreScanner = new ItemSimpleSC("oreScanner", "oreScanner");
        oreScanner.setMaxStackSize(1);
        GameRegistry.registerItem(oreScanner, "oreScanner");
        areaCard = new com.sc.item.ItemAreaCardSC();
        GameRegistry.registerItem(areaCard, "areaCard");
        oreLens = new com.sc.item.ItemOreLensSC();
        GameRegistry.registerItem(oreLens, "oreLens");

        // Wrenches: basic, electric, quantum (appended).
        for (com.sc.item.ItemWrenchSC.Tier t : com.sc.item.ItemWrenchSC.Tier.values()) {
            com.sc.item.ItemWrenchSC w = new com.sc.item.ItemWrenchSC(t);
            GameRegistry.registerItem(w, t.name);
            WRENCHES.add(w);
        }

        upgrade = new com.sc.item.ItemUpgradeSC();
        GameRegistry.registerItem(upgrade, "machineUpgrade");
        itemFilter = new com.sc.item.ItemItemFilterSC();
        GameRegistry.registerItem(itemFilter, "itemFilter");
        tubeSpeedUpgrade = new com.sc.item.ItemTubeSpeedSC();
        GameRegistry.registerItem(tubeSpeedUpgrade, "tubeSpeedUpgrade");
        battery = new com.sc.item.ItemBatterySC();
        GameRegistry.registerItem(battery, "batterySC");
        linkCard = new com.sc.item.ItemLinkCardSC();
        GameRegistry.registerItem(linkCard, "linkCard");
        entangledCrystal = new com.sc.item.ItemEntangledCrystalSC();
        GameRegistry.registerItem(entangledCrystal, "entangledCrystal");
        String[] lead = {"leadHelmet", "leadChestplate", "leadLeggings", "leadBoots"};
        for (int i = 0; i < 4; i++) {
            leadSuit[i] = new com.sc.item.ItemLeadSuitSC(i);
            GameRegistry.registerItem(leadSuit[i], lead[i]);
        }
        dosimeter = new com.sc.item.ItemDosimeterSC();
        GameRegistry.registerItem(dosimeter, "dosimeter");
        radioprotector = new com.sc.item.ItemRadioprotectorSC();
        GameRegistry.registerItem(radioprotector, "radioprotector");

        // Step 10 (02_guide_book.md).
        manual = new ItemSCManual();
        GameRegistry.registerItem(manual, "scManual");
        fluidDrop = new com.sc.item.ItemFluidDropSC();
        GameRegistry.registerItem(fluidDrop, "fluidDrop");
        fluidBucket = new com.sc.item.ItemFluidBucketSC();
        GameRegistry.registerItem(fluidBucket, "fluidBucket");

        // Step 11 (§1/§2/§5-§7): the remaining plain components those recipes need - see
        // ModRecipesCrafting for what consumes each of these.
        String[] simpleComponents = {
                "ptfeSheet", "ceramicRod", "dielectric", "resistor", "capacitor", "tantalumCapacitor", "ceramicPackage",
                "combustionChamber", "alFrame", "tiFrame", "turbineBladeTungsten", "turbineBladeTitanium",
                "tiCasing", "quartzChamber", "nb3SnCoil", "liBlanketModule",
                "polymerPlate", "tiPlate", "powerCoreFrame", "wTiPlate", "nb3SnPlate", "heLoopModule", "hfo2Die",
                "lens", "sensor",
                "polymerHandle", "focusLens", "energyCellLV", "energyCellMV", "energyCellHV",
                "steelCasing", "copperCoil", "wBarrel", "quartzEmitter",
                // TODO(§13.5, added during bug-hunt): the doc's own "real targets are backing+material"
                // note was never given a distinct item - without one, Sputter Target (Copper) was
                // byte-for-byte identical in Rolling Machine input to Lead Frame x16 (both 2 Cu
                // Ingot), and Sputter Target (Tungsten) to Turbine Blade (Tungsten) (both 2 W
                // Ingot), so one recipe of each pair was unreachable no matter how RecipeRegistry
                // picks matches. This plain steel backing plate disambiguates all three targets.
                "sputterBacking",
                // §18.3: Nb + Sn alloyed in the Blast Furnace, then pressed into the
                // Coil/Plate the Fusion Reactor and Exo Suit need.
                "nb3SnIngot",
                // §5: the 56-item bulk of the Fusion Reactor, assembled on the EV Upgrade
                // Station because it cannot fit any crafting grid.
                "fusionCore",
        };
        for (String name : simpleComponents) {
            ItemSimpleSC item = new ItemSimpleSC(name, name);
            GameRegistry.registerItem(item, name);
            COMPONENTS.put(name, item);
        }
        // Singular reactor: its fuel, pressed by the Matter Compressor (appended last - registry
        // names are what worlds keep, the list order is only the creative tab's).
        ItemSimpleSC capsule = new MatterCapsule();
        GameRegistry.registerItem(capsule, "matterCapsule");
        COMPONENTS.put("matterCapsule", capsule);
        // the Singular Matter cell (appended)
        singularCell = new com.sc.item.ItemSingularCellSC();
        GameRegistry.registerItem(singularCell, "singularMatterCell");
        // the Ground / Space Bridge, stage 2 (appended)
        bridgeRemote = new com.sc.item.ItemBridgeRemoteSC();
        GameRegistry.registerItem(bridgeRemote, "bridgeRemote");
        coordinator = new com.sc.item.ItemCoordinatorSC();
        GameRegistry.registerItem(coordinator, "coordinator");
        bridgeLinkModule = new com.sc.item.ItemBridgeLinkModuleSC();
        GameRegistry.registerItem(bridgeLinkModule, "bridgeLinkModule");
        // the Energy Converter's modules: Channel Amplifier, Efficiency, the Mekanism / Galacticraft cards (appended)
        converterModule = new com.sc.item.ItemConverterModuleSC();
        GameRegistry.registerItem(converterModule, "converterModule");
    }

    public static com.sc.item.ItemConverterModuleSC converterModule;

    /** The Compressed Matter Capsule: a plain component with a TooltipSC tooltip. */
    public static final class MatterCapsule extends ItemSimpleSC {
        MatterCapsule() {
            super("matterCapsule", "matterCapsule");
            setMaxStackSize(16);
        }

        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        public void addInformation(ItemStack stack, net.minecraft.entity.player.EntityPlayer player, java.util.List list, boolean advanced) {
            list.add("§7" + com.sc.manual.Lang.tr("sc.tooltip.matterCapsule"));
            com.sc.util.TooltipSC.more(list, com.sc.manual.Lang.tr("sc.tooltip.matterCapsule.details",
                    com.sc.tileentity.TileEntityMachineSC.MATTER_PER_CAPSULE), com.sc.manual.Lang.tr("sc.tooltip.matterCapsule.howto"));
        }

        @Override
        public net.minecraft.item.EnumRarity getRarity(ItemStack stack) {
            return net.minecraft.item.EnumRarity.rare;
        }
    }

    private static ItemMaterialSC registerKind(MaterialItemKind kind, String registryName) {
        ItemMaterialSC item = new ItemMaterialSC(kind);
        GameRegistry.registerItem(item, registryName);
        for (Material material : Material.byKind(kind)) {
            ItemStack stack = item.stackOf(material);
            OreDictionary.registerOre(kind.prefix + material.oreDictNameFor(kind), stack);
            // IC2's own spellings, so its crushed ores and ours are interchangeable in both mods'
            // machines (OreRecipes / MachineRecipe.isSameIngredient match on these names).
            if (kind == MaterialItemKind.CRUSHED_ORE) {
                OreDictionary.registerOre("crushed" + material.oreDictName, stack);
            } else if (kind == MaterialItemKind.PURIFIED_CRUSHED_ORE) {
                OreDictionary.registerOre("crushedPurified" + material.oreDictName, stack);
            } else if (kind == MaterialItemKind.INGOT && material == Material.ALUMINIUM) {
                // Most mods spell it "Aluminum". Only the ingot is aliased: this mod's Al dust is
                // Al2O3 (§11.5), which must not pass for another mod's smeltable metal dust.
                OreDictionary.registerOre("ingotAluminum", stack);
            }
        }
        return item;
    }
}
