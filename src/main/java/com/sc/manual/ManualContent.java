package com.sc.manual;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.sc.energy.CableType;
import com.sc.energy.FieldMode;
import com.sc.energy.GeneratorType;
import com.sc.energy.Tier;
import com.sc.init.ModBlocks;
import com.sc.init.ModItems;
import com.sc.machine.MachineRecipe;
import com.sc.machine.MachineType;
import com.sc.machine.RecipeRegistry;
import com.sc.tileentity.TileEntityFieldGeneratorSC;
import com.sc.tileentity.TileEntityGeneratorSC;
import com.sc.tileentity.TileEntityMachineSC;
import com.sc.util.ArmorSuit;
import com.sc.util.ChipType;
import com.sc.util.ConfigSC;
import com.sc.util.CorrosiveFluids;
import com.sc.util.OreEntry;
import com.sc.util.PipeType;
import com.sc.util.WeaponType;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

/**
 * The handbook's pages (02_guide_book.md §2), generated from the mod's own registries and
 * constants - recipes, machine/generator stats, cable and pipe ratings, ore generation (as
 * configured, not the defaults), armour and weapon numbers - so a page can never disagree with
 * what the game actually does (§3 "автообновление"). Every word of prose is a .lang key
 * (en_US + ru_RU); item, block and fluid names come from the game's own localisation.
 *
 * Lines may carry Minecraft formatting codes: TITLE / HEAD for headings, DIM for secondary
 * text. GuiManual wraps them with listFormattedStringToWidth, which keeps the codes.
 *
 * TODO(scope, design doc §1/§3): no JSON lore pipeline and no ManualAPI for third-party pages;
 * no clickable graph for the silicon chain (it's a numbered list with recipes).
 */
public final class ManualContent {

    static final String TITLE = "\u00a71\u00a7l";
    static final String HEAD = "\u00a71";
    static final String DIM = "\u00a78";
    static final String WARN = "\u00a74";
    private static final String R = "\u00a7r";

    private ManualContent() {
    }

    public static List<String> linesFor(ManualTab tab, String searchQuery) {
        switch (tab) {
            case INTRO: return intro();
            case ORES: return ores();
            case SILICON_CHAIN: return siliconChain();
            case MACHINES: return machines();
            case ENERGY: return energy();
            case ARMOR_WEAPONS: return armorWeapons();
            case FIELD_GENERATOR: return fieldGenerator();
            case RECIPES: return recipes(searchQuery);
            case SAFETY: return safety();
            default: return new ArrayList<String>();
        }
    }

    // ------------------------------------------------------------------ 1. introduction

    private static List<String> intro() {
        List<String> lines = new ArrayList<String>();
        lines.add(TITLE + Lang.tr("sc.manual.intro.title"));
        lines.add("");
        lines.addAll(paragraph("sc.manual.intro.about"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.intro.tiers"));
        lines.addAll(paragraph("sc.manual.intro.power"));
        for (Tier t : Tier.values()) {
            lines.add(" - " + t.name() + ": " + Lang.tr("sc.manual.intro.tierline", t.getVoltage(), t.getBuffer()));
        }
        lines.add("");
        lines.addAll(paragraph("sc.manual.intro.flow"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.intro.starthead"));
        lines.addAll(paragraph("sc.manual.intro.start"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.intro.guihead"));
        lines.addAll(paragraph("sc.manual.intro.gui"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.intro.controlshead"));
        lines.addAll(paragraph("sc.manual.intro.controls"));
        lines.add("");
        lines.add(WARN + Lang.tr("sc.manual.intro.warninghead"));
        lines.addAll(paragraph("sc.manual.intro.warning"));
        return lines;
    }

    // ------------------------------------------------------------------ 2. ore atlas

    private static List<String> ores() {
        List<String> lines = new ArrayList<String>();
        lines.add(TITLE + Lang.tr("sc.manual.ores.title", OreEntry.values().length));
        lines.add("");
        lines.addAll(paragraph("sc.manual.ores.intro"));
        for (OreEntry ore : OreEntry.values()) {
            ConfigSC.OreGenSettings gen = ConfigSC.settingsFor(ore);
            ItemStack block = new ItemStack(ModBlocks.oreSC, 1, ore.meta());
            lines.add("");
            lines.add(HEAD + block.getDisplayName());
            lines.add("  " + Lang.tr("sc.manual.ores.where", gen.minY, gen.maxY, gen.veinSize, gen.veinsPerChunk));
            lines.add("  " + Lang.tr("sc.manual.ores.biomes", biomes(ore)));
            lines.add("  " + Lang.tr("sc.manual.ores.tool", Lang.tr("sc.manual.ores.tool." + ore.tool.name().toLowerCase(Locale.ROOT))));
            lines.addAll(processing(block));
        }
        return lines;
    }

    private static String biomes(OreEntry ore) {
        if (ore.biomes == null) {
            return Lang.tr("sc.manual.ores.anybiome");
        }
        StringBuilder sb = new StringBuilder();
        for (BiomeDictionary.Type type : ore.biomes) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(Lang.trOr("sc.biome." + type.name().toLowerCase(Locale.ROOT), type.name()));
        }
        return sb.toString();
    }

    /**
     * What becomes of an ore: its furnace result, and the Crusher -> Ore Washer -> Centrifuge
     * route followed step by step (each hop takes the first recipe whose single input is the
     * previous product), with by-product chances; plus any other machine that takes the raw ore.
     */
    private static List<String> processing(ItemStack ore) {
        List<String> lines = new ArrayList<String>();
        ItemStack smelted = FurnaceRecipes.smelting().getSmeltingResult(ore);
        if (smelted != null) {
            lines.add("  " + Lang.tr("sc.manual.ores.smelt", smelted.stackSize, smelted.getDisplayName()));
        }
        ItemStack current = ore;
        // Crushing, washing, centrifuging, then calcining / electrolysis for the oxide ores.
        MachineType[] route = {MachineType.CRUSHER, MachineType.ORE_WASHER, MachineType.CENTRIFUGE,
                MachineType.KILN, MachineType.CHLOR_ALKALI_ELECTROLYZER};
        StringBuilder chain = new StringBuilder();
        for (MachineType type : route) {
            MachineRecipe step = singleInputRecipe(type, current);
            // Only steps that refine the material itself - Bauxite's alumina also makes ceramic
            // packages in the Kiln, but that's a use, not the metal's route.
            if (step == null || step.outputs.length == 0 || !isMaterialItem(step.outputs[0])) {
                continue;
            }
            chain.append(chain.length() == 0 ? "" : " -> ").append(type.localizedName()).append(": ");
            chain.append(step.outputs[0].stackSize).append("x ").append(step.outputs[0].getDisplayName());
            for (int i = 0; i < step.byproducts.length; i++) {
                chain.append(" + ").append(step.byproducts[i].getDisplayName())
                        .append(" (").append(percent(step.byproductChances[i])).append("%)");
            }
            current = step.outputs[0];
        }
        if (current != ore) {
            ItemStack ingot = FurnaceRecipes.smelting().getSmeltingResult(current);
            if (ingot != null) {
                chain.append(" -> ").append(Lang.tr("sc.manual.ores.furnace")).append(": ").append(ingot.getDisplayName());
            }
        }
        if (chain.length() > 0) {
            lines.add("  " + DIM + chain);
        }
        // Where the end product goes next when it's one of several ingredients (titanium
        // concentrate -> Chem Reactor chlorination, magnesium -> the Kroll step...).
        if (current != ore && current.getItem() != ModItems.ingot) {     // ingots go everywhere - not worth listing
            for (MachineType type : MachineType.values()) {
                for (MachineRecipe r : RecipeRegistry.recipesFor(type)) {
                    if (r.inputs.length + (r.fluidInputA != null ? 1 : 0) + (r.fluidInputB != null ? 1 : 0) < 2) {
                        continue;
                    }
                    for (ItemStack in : r.inputs) {
                        if (MachineRecipe.isSameIngredient(current, in) && r.outputs.length > 0 || MachineRecipe.isSameIngredient(current, in) && r.fluidOutputA != null) {
                            String product = r.outputs.length > 0 ? r.outputs[0].getDisplayName()
                                    : r.fluidOutputA.getFluid().getLocalizedName(r.fluidOutputA);
                            lines.add("  " + DIM + Lang.tr("sc.manual.ores.next", type.localizedName() + " -> " + product));
                        }
                    }
                }
            }
        }
        for (MachineType type : MachineType.values()) {
            if (type == MachineType.CRUSHER) {
                continue;
            }
            boolean used = false;
            for (MachineRecipe r : RecipeRegistry.recipesFor(type)) {
                for (ItemStack in : r.inputs) {
                    used |= MachineRecipe.isSameIngredient(ore, in);
                }
            }
            if (used) {
                lines.add("  " + DIM + Lang.tr("sc.manual.ores.usedin", type.localizedName()));
            }
        }
        return lines;
    }

    private static boolean isMaterialItem(ItemStack stack) {
        return stack.getItem() instanceof com.sc.item.ItemMaterialSC || stack.getItem() == ModItems.siliconMaterial;
    }

    private static MachineRecipe singleInputRecipe(MachineType type, ItemStack input) {
        for (MachineRecipe r : RecipeRegistry.recipesFor(type)) {
            if (r.inputs.length == 1 && MachineRecipe.isSameIngredient(input, r.inputs[0])
                    && r.outputs.length > 0 && isMaterialItem(r.outputs[0])) {
                return r;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 3. silicon chain

    private static List<String> siliconChain() {
        List<String> lines = new ArrayList<String>();
        lines.add(TITLE + Lang.tr("sc.manual.chain.title"));
        lines.add("");
        lines.addAll(paragraph("sc.manual.chain.intro"));
        MachineType[] chain = {
                MachineType.CRUSHER, MachineType.BLAST_FURNACE, MachineType.CHEM_REACTOR, MachineType.CVD_CHAMBER,
                MachineType.CZOCHRALSKI_PULLER, MachineType.WIRE_SAW, MachineType.OXIDATION_FURNACE,
                MachineType.PHOTORESIST_COATER, MachineType.STEPPER, MachineType.ETCHING_BATH,
                MachineType.ION_IMPLANTER, MachineType.SPUTTERER, MachineType.DICING_SAW, MachineType.PACKAGER,
        };
        int step = 1;
        for (MachineType type : chain) {
            lines.add("");
            lines.add(HEAD + step + ". " + type.localizedName() + R + DIM + " (" + type.tier + ", " + type.euPerTick + " EU/t)");
            lines.add("  " + Lang.tr("sc.manual.chain.step." + type.name().toLowerCase(Locale.ROOT)));
            for (MachineRecipe recipe : RecipeRegistry.recipesFor(type)) {
                // These machines also run ore processing, steel and chemistry - only the steps
                // that move a silicon stage belong here; Recipe Search lists everything.
                if (involvesSilicon(recipe)) {
                    lines.addAll(describeRecipe(recipe, type));
                }
            }
            step++;
        }
        lines.add("");
        lines.addAll(paragraph("sc.manual.chain.outro"));
        return lines;
    }

    private static boolean involvesSilicon(MachineRecipe recipe) {
        for (ItemStack s : recipe.inputs) {
            if (s.getItem() == ModItems.siliconMaterial) {
                return true;
            }
        }
        for (ItemStack s : recipe.outputs) {
            if (s.getItem() == ModItems.siliconMaterial) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 4. machines

    private static List<String> machines() {
        List<String> lines = new ArrayList<String>();
        lines.add(TITLE + Lang.tr("sc.manual.machines.title"));
        lines.add("");
        lines.addAll(paragraph("sc.manual.machines.intro"));
        for (Tier tier : Tier.values()) {
            boolean any = false;
            for (MachineType type : MachineType.values()) {
                any |= type.tier == tier;
            }
            if (!any) {
                continue;                           // IV / QV / XV: energy blocks only, no machines
            }
            lines.add("");
            lines.add(HEAD + Lang.tr("sc.manual.machines.tierhead", tier.name()));
            for (MachineType type : MachineType.values()) {
                if (type.tier != tier) {
                    continue;
                }
                StringBuilder stats = new StringBuilder(type.euPerTick + " EU/t, "
                        + Lang.tr("sc.manual.machines.recipes", RecipeRegistry.recipesFor(type).size()));
                if (type.heatCapable) {
                    stats.append(", ").append(Lang.tr("sc.manual.machines.heat"));
                }
                if (RecipeRegistry.usesTank(type, 3)) {
                    stats.append(", ").append(Lang.tr("sc.manual.machines.topout"));
                }
                lines.add(" " + type.localizedName() + R + DIM + " - " + stats);
                lines.add("   " + Lang.tr("sc.manual.machine." + type.name().toLowerCase(Locale.ROOT)));
            }
        }
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.machines.sideshead"));
        lines.addAll(paragraph("sc.manual.machines.sides"));
        lines.addAll(paragraph("sc.manual.machines.vent"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.machines.wrenchhead"));
        lines.addAll(paragraph("sc.manual.machines.wrench"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.machines.quarryhead"));
        lines.addAll(paragraph("sc.manual.machines.quarry"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.machines.upgradeshead"));
        lines.addAll(paragraph("sc.manual.machines.upgrades"));
        for (com.sc.machine.UpgradeType type : com.sc.machine.UpgradeType.values()) {
            String name = ModItems.upgrade.stackOf(type).getDisplayName();
            lines.add(" " + name + R + DIM + " - " + Lang.tr("sc.upgrade.tooltip." + type.name().toLowerCase(Locale.ROOT)));
        }
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.machines.heathead"));
        lines.add(Lang.tr("sc.manual.machines.heatrule", TileEntityMachineSC.getHeatCapacity(), TileEntityMachineSC.HEAT_RESUME));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.machines.troublehead"));
        lines.addAll(paragraph("sc.manual.machines.trouble"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.machines.generators"));
        lines.addAll(paragraph("sc.manual.gen.rules"));
        for (GeneratorType type : GeneratorType.values()) {
            if (type == GeneratorType.CREATIVE) {
                continue;
            }
            String fuel;
            switch (type.kind) {
                case PASSIVE: fuel = Lang.tr("sc.manual.machines.passive"); break;
                case FUSION: fuel = Lang.tr("sc.manual.machines.fusioninfo", String.valueOf(type.ignitionThreshold()),
                        TileEntityGeneratorSC.CELL_BURN_TICKS / 1200); break;
                case DUAL_FLUID: fuel = type.fuelRatePerTick + " mB/t " + fluidName(type.fuelFluidName) + " + "
                        + type.fuel2RatePerTick + " mB/t " + fluidName(type.fuel2FluidName); break;
                case FLUID_FUEL:
                case EXO: fuel = type.fuelRatePerTick + " mB/t " + fluidName(type.fuelFluidName); break;
                default: fuel = Lang.tr("sc.manual.gen.kind." + type.kind.name().toLowerCase(Locale.ROOT));
            }
            lines.add(" " + type.localizedName() + R + DIM + " - " + type.tier + ", " + type.euPerTick + " EU/t, " + fuel);
            lines.add("   " + Lang.tr("sc.manual.generator." + type.name().toLowerCase(Locale.ROOT)));
        }
        return lines;
    }

    // ------------------------------------------------------------------ 5. energy & logistics

    private static List<String> energy() {
        List<String> lines = new ArrayList<String>();
        lines.add(TITLE + Lang.tr("sc.manual.energy.title"));
        lines.add("");
        lines.addAll(paragraph("sc.manual.energy.intro"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.energy.bundlehead"));
        lines.addAll(paragraph("sc.manual.energy.bundle"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.energy.cables"));
        for (CableType type : CableType.values()) {
            String name = new ItemStack(ModBlocks.cableSC, 1, type.ordinal()).getDisplayName();
            lines.add(" " + name + R + DIM + " - " + Lang.tr("sc.manual.energy.cableline", type.tier.name(),
                    type.maxAmps, type.maxThroughput(), type.lossPerBlock));
        }
        lines.addAll(paragraph("sc.manual.energy.rules"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.energy.storagehead"));
        for (Tier tier : Tier.values()) {
            String name = new ItemStack(ModBlocks.energyStorageSC, 1, tier.ordinal()).getDisplayName();
            lines.add(" " + name + R + DIM + " - " + Lang.tr("sc.manual.energy.storageline",
                    String.valueOf(com.sc.tileentity.TileEntityEnergyStorageSC.capacityOf(tier)), tier.getVoltage()));
        }
        lines.addAll(paragraph("sc.manual.energy.storage"));
        StringBuilder slots = new StringBuilder();
        for (Tier tier : Tier.values()) {
            slots.append(slots.length() == 0 ? "" : ", ").append(tier.name()).append(" ")
                    .append(com.sc.tileentity.TileEntityEnergyStorageSC.chargeSlotsFor(tier));
        }
        lines.add(Lang.tr("sc.manual.energy.chargeslots", slots.toString()));
        lines.addAll(paragraph("sc.manual.energy.storage2"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.energy.padhead"));
        lines.addAll(paragraph("sc.manual.energy.pad"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.energy.transformerhead"));
        for (int meta = 0; meta < com.sc.block.BlockTransformerSC.VARIANTS; meta++) {
            Tier low = Tier.values()[meta];
            Tier high = Tier.values()[meta + 1];
            String name = new ItemStack(ModBlocks.transformerSC, 1, meta).getDisplayName();
            lines.add(" " + name + R + DIM + " - " + Lang.tr("sc.manual.energy.transformerline",
                    high.getVoltage(), low.getVoltage(), low.getVoltage(), high.getVoltage()));
        }
        lines.addAll(paragraph("sc.manual.energy.transformer"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.energy.pipes"));
        for (PipeType type : PipeType.values()) {
            String name = new ItemStack(ModBlocks.pipeSC, 1, type.ordinal()).getDisplayName();
            lines.add(" " + name + R + DIM + " - " + type.throughput + " mB/t"
                    + (type.chemicallyResistant ? ", " + Lang.tr("sc.manual.energy.corrosionsafe") : ""));
        }
        lines.addAll(paragraph("sc.manual.energy.piperules"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.energy.tankhead"));
        for (int tier = 0; tier < com.sc.block.BlockTankSC.TIER_NAMES.length; tier++) {
            String name = new ItemStack(ModBlocks.tankSC, 1, tier).getDisplayName();
            lines.add(" " + name + R + DIM + " - " + Lang.tr("sc.manual.energy.tankline", com.sc.tileentity.TileEntityTankSC.capacityOf(tier)));
        }
        lines.addAll(paragraph("sc.manual.energy.tank"));
        lines.add("");
        lines.add(HEAD + new ItemStack(ModBlocks.tubeItemPneumatic).getDisplayName());
        lines.addAll(paragraph("sc.manual.energy.tube"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.energy.powerhead"));
        lines.addAll(paragraph("sc.manual.energy.power"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.energy.batteryhead"));
        for (int t = 0; t < com.sc.item.ItemBatterySC.KEYS.length; t++) {
            String name = new ItemStack(ModItems.battery, 1, t).getDisplayName();
            lines.add(" " + name + R + DIM + " - " + Lang.tr("sc.manual.energy.batteryline", com.sc.item.ItemBatterySC.TIERS[t].name(),
                    String.valueOf(com.sc.item.ItemBatterySC.CAPACITY[t]), com.sc.item.ItemBatterySC.RATE[t]));
        }
        lines.addAll(paragraph("sc.manual.energy.battery"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.energy.slothead"));
        lines.addAll(paragraph("sc.manual.energy.slot"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.energy.fluidshead"));
        lines.add(Lang.tr("sc.manual.energy.buckets", com.sc.item.ItemFluidBucketSC.FLUIDS.length));
        lines.addAll(paragraph("sc.manual.energy.fluids"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.energy.wirelesshead"));
        for (Tier tier : Tier.values()) {
            int range = com.sc.tileentity.TileEntityWirelessSC.range(tier);
            lines.add(" " + Lang.tr("sc.manual.energy.wirelessline", tier.name(),
                    range == Integer.MAX_VALUE ? Lang.tr("sc.manual.energy.wirelessall") : Lang.tr("sc.manual.energy.wirelessrange", range),
                    tier.getVoltage(), com.sc.tileentity.TileEntityWirelessSC.blocksPerPercent(tier)));
        }
        lines.addAll(paragraph("sc.manual.energy.wireless"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.energy.quantumhead"));
        lines.addAll(paragraph("sc.manual.energy.quantum"));
        return lines;
    }

    // ------------------------------------------------------------------ 6. armour & weapons

    private static List<String> armorWeapons() {
        List<String> lines = new ArrayList<String>();
        lines.add(TITLE + Lang.tr("sc.manual.armor.title"));
        lines.add("");
        lines.addAll(paragraph("sc.manual.armor.suits"));
        for (ArmorSuit suit : ArmorSuit.values()) {
            int points = 0;
            for (int i = 0; i < 4; i++) {
                points += suit.material.getDamageReductionAmount(i);
            }
            lines.add(" " + Lang.tr("sc.suit." + suit.name().toLowerCase(Locale.ROOT)) + R + DIM + " - "
                    + Lang.tr("sc.manual.armor.suitline", points, points * 4, suit.heatCapacity, suit.heatDissipation));
        }
        lines.addAll(paragraph("sc.manual.armor.upgrade"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.armor.chipshead"));
        lines.addAll(paragraph("sc.manual.armor.chips"));
        for (ChipType type : ChipType.values()) {
            lines.add(" " + Lang.trOr("sc.chip." + type.name().toLowerCase(Locale.ROOT), type.name()) + R + DIM + " - "
                    + Lang.tr("sc.manual.armor.chip." + type.name().toLowerCase(Locale.ROOT)));
        }
        lines.addAll(paragraph("sc.manual.armor.effects"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.armor.fnhead"));
        lines.addAll(paragraph("sc.manual.armor.fn"));
        for (com.sc.util.ArmorFeature f : com.sc.util.ArmorFeature.values()) {
            String key = "sc.armorfn." + f.name().toLowerCase(Locale.ROOT);
            lines.add(" " + Lang.tr(key) + R + DIM + " (" + Lang.tr("sc.suit." + f.minSuit.name().toLowerCase(Locale.ROOT)) + "+, "
                    + Lang.tr("sc.armorhud.piece." + f.piece) + ") - " + Lang.tr(key + ".desc"));
        }
        lines.add(HEAD + Lang.tr("sc.manual.armor.sethead"));
        for (com.sc.util.ArmorSuit suit : com.sc.util.ArmorSuit.values()) {
            lines.add(" " + Lang.tr("sc.armorgui.set." + suit.name().toLowerCase(Locale.ROOT)));
        }
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.armor.energyhead"));
        lines.addAll(paragraph("sc.manual.armor.energy"));
        for (ArmorSuit suit : ArmorSuit.values()) {
            lines.add(" " + Lang.tr("sc.suit." + suit.name().toLowerCase(Locale.ROOT)) + R + DIM + " - "
                    + Lang.tr("sc.manual.armor.energyline", suit.maxCharge, suit.euPerDamage, suit.chargeTier.name()));
        }
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.armor.weapons"));
        for (WeaponType type : WeaponType.values()) {
            String name = new ItemStack(ModItems.WEAPONS.get(type)).getDisplayName();
            lines.add(" " + name + R + DIM + " - " + Lang.tr("sc.manual.armor.weaponline", type.damagePerHit, type.shotsPerUse,
                    type.euPerShot, type.range, type.tier.getBuffer()));
        }
        lines.addAll(paragraph("sc.manual.armor.charge"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.armor.bladeshead"));
        lines.addAll(paragraph("sc.manual.armor.blades"));
        for (com.sc.util.BladeType type : com.sc.util.BladeType.values()) {
            String name = new ItemStack(ModItems.BLADES.get(type)).getDisplayName();
            lines.add(" " + name + R + DIM + " - " + Lang.tr("sc.manual.armor.bladeline", type.offDamage, type.onDamage,
                    type.euPerHit, type.idlePerSecond, type.maxCharge, type.chargeTier.name(), type.heatCapacity, type.heatDissipation));
        }
        for (com.sc.util.BladeFeature f : com.sc.util.BladeFeature.values()) {
            lines.add(" " + Lang.tr("sc.bladefn." + f.key()) + R + DIM + " ("
                    + new ItemStack(ModItems.BLADES.get(f.minType)).getDisplayName() + "+) - " + Lang.tr("sc.bladefn." + f.key() + ".desc"));
        }
        for (com.sc.util.BladeType type : com.sc.util.BladeType.values()) {
            lines.add(" " + Lang.tr("sc.bladegui.set." + type.key()));
        }
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.armor.drillshead"));
        lines.addAll(paragraph("sc.manual.armor.drills"));
        for (com.sc.util.DrillType type : com.sc.util.DrillType.values()) {
            String name = new ItemStack(ModItems.DRILLS.get(type)).getDisplayName();
            lines.add(" " + name + R + DIM + " - " + Lang.tr("sc.manual.armor.drillline", type.euPerBlock, (int) type.speed,
                    type.harvestLevel, type.fortune, type.maxCharge, type.chargeTier.name()));
        }
        for (com.sc.util.DrillFeature f : com.sc.util.DrillFeature.values()) {
            lines.add(" " + Lang.tr("sc.drillfn." + f.key()) + R + DIM + " ("
                    + new ItemStack(ModItems.DRILLS.get(f.minType)).getDisplayName() + "+) - " + Lang.tr("sc.drillfn." + f.key() + ".desc"));
        }
        for (com.sc.util.DrillType type : com.sc.util.DrillType.values()) {
            lines.add(" " + Lang.tr("sc.drillgui.set." + type.key()));
        }
        return lines;
    }

    // ------------------------------------------------------------------ 7. field generator

    private static List<String> fieldGenerator() {
        List<String> lines = new ArrayList<String>();
        lines.add(TITLE + Lang.tr("sc.manual.field.title"));
        lines.add("");
        lines.addAll(paragraph("sc.manual.field.intro"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.field.linkhead"));
        lines.addAll(paragraph("sc.manual.field.linking"));
        lines.add(Lang.tr("sc.manual.field.limits", TileEntityFieldGeneratorSC.MAX_NODES, TileEntityFieldGeneratorSC.MAX_LINK_DISTANCE));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.field.modes"));
        int def = com.sc.tileentity.FieldShapeSC.DEFAULT_RANGE, max = com.sc.tileentity.FieldShapeSC.MAX_RANGE;
        for (FieldMode mode : FieldMode.values()) {
            lines.add(" " + Lang.trOr("sc.field.mode." + mode.name().toLowerCase(Locale.ROOT), mode.name()) + R + DIM + " - "
                    + Lang.tr("sc.manual.field.modeline", String.valueOf(mode.costMultiplier),
                    TileEntityFieldGeneratorSC.upkeepFor(1, def, mode), def,
                    TileEntityFieldGeneratorSC.upkeepFor(TileEntityFieldGeneratorSC.MAX_NODES, max, mode), max));
            lines.add("   " + Lang.tr("sc.manual.field.shape." + mode.name().toLowerCase(Locale.ROOT)));
        }
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.field.rangehead"));
        lines.add(Lang.tr("sc.manual.field.range", com.sc.tileentity.FieldShapeSC.MIN_RANGE, max, def));
        lines.add(Lang.tr("sc.manual.field.formula", TileEntityFieldGeneratorSC.BASE_EU_PER_FACE,
                TileEntityFieldGeneratorSC.RANGE_EU_PER_BLOCK));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.field.protecthead"));
        lines.add(Lang.tr("sc.manual.field.protect"));
        lines.add(Lang.tr("sc.manual.field.projectiles", TileEntityFieldGeneratorSC.DEFLECT_COST));
        lines.add(Lang.tr("sc.manual.field.explosions", TileEntityFieldGeneratorSC.EXPLOSION_COST));
        lines.add(Lang.tr("sc.manual.field.visible"));
        lines.addAll(paragraph("sc.manual.field.power"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.field.funchead"));
        lines.addAll(paragraph("sc.manual.field.functions"));
        lines.addAll(paragraph("sc.manual.field.targets"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.field.charginghead"));
        for (int m = 0; m < TileEntityFieldGeneratorSC.CHARGE_MODES; m++) {
            lines.add(" - " + Lang.tr("sc.fieldgui.charge.mode." + m));
        }
        lines.addAll(paragraph("sc.manual.field.charging"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.field.rainhead"));
        lines.add(Lang.tr("sc.manual.field.raincost", TileEntityFieldGeneratorSC.RAIN_PCT, TileEntityFieldGeneratorSC.THUNDER_PCT,
                TileEntityFieldGeneratorSC.LIGHTNING_COST));
        lines.addAll(paragraph("sc.manual.field.rain"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.field.switchhead"));
        lines.addAll(paragraph("sc.manual.field.switch"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.field.accesshead"));
        lines.addAll(paragraph("sc.manual.field.access"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.field.lookhead"));
        lines.addAll(paragraph("sc.manual.field.look"));
        return lines;
    }

    // ------------------------------------------------------------------ 8. recipe search

    private static List<String> recipes(String query) {
        List<String> lines = new ArrayList<String>();
        boolean filtered = query != null && !query.isEmpty();
        lines.add(TITLE + (filtered ? Lang.tr("sc.manual.recipes.title.filtered", query) : Lang.tr("sc.manual.recipes.title")));
        lines.add(DIM + Lang.tr("sc.manual.recipes.hint"));
        int header = lines.size();
        String needle = filtered ? query.toLowerCase(Locale.ROOT) : null;
        for (MachineType type : MachineType.values()) {
            for (MachineRecipe recipe : RecipeRegistry.recipesFor(type)) {
                if (needle != null && !matchesQuery(type, recipe, needle)) {
                    continue;
                }
                lines.add("");
                lines.add(HEAD + type.localizedName());
                lines.addAll(describeRecipe(recipe, type));
            }
        }
        // Crafting-table recipes for this mod's items (the machines themselves, components...).
        for (Object o : CraftingManager.getInstance().getRecipeList()) {
            IRecipe recipe = (IRecipe) o;
            ItemStack out = recipe.getRecipeOutput();
            if (out == null || out.getItem() == null || !isModItem(out)) {
                continue;
            }
            List<String> inputs = craftingInputs(recipe);
            if (needle != null && !out.getDisplayName().toLowerCase(Locale.ROOT).contains(needle)
                    && !containsNeedle(inputs, needle)) {
                continue;
            }
            lines.add("");
            lines.add(HEAD + Lang.tr("sc.manual.recipes.crafting"));
            lines.add("  " + Lang.tr("sc.manual.recipes.in") + ": " + join(inputs));
            lines.add("  " + Lang.tr("sc.manual.recipes.out") + ": " + out.stackSize + "x " + out.getDisplayName());
        }
        if (lines.size() == header) {
            lines.add("");
            lines.add(Lang.tr("sc.manual.recipes.none"));
        }
        return lines;
    }

    private static boolean isModItem(ItemStack stack) {
        Object name = net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem());
        return name != null && name.toString().startsWith(com.sc.Reference.MODID + ":");
    }

    /** Ingredient names of a crafting recipe, counted ("4x Iron Ingot"); ore-dict slots show their first item. */
    private static List<String> craftingInputs(IRecipe recipe) {
        List<Object> raw = new ArrayList<Object>();
        if (recipe instanceof ShapedRecipes) {
            for (Object s : ((ShapedRecipes) recipe).recipeItems) {
                raw.add(s);
            }
        } else if (recipe instanceof ShapelessRecipes) {
            raw.addAll(((ShapelessRecipes) recipe).recipeItems);
        } else if (recipe instanceof ShapedOreRecipe) {
            for (Object s : ((ShapedOreRecipe) recipe).getInput()) {
                raw.add(s);
            }
        } else if (recipe instanceof ShapelessOreRecipe) {
            raw.addAll(((ShapelessOreRecipe) recipe).getInput());
        }
        List<String> names = new ArrayList<String>();
        List<Integer> counts = new ArrayList<Integer>();
        for (Object o : raw) {
            ItemStack s = o instanceof ItemStack ? (ItemStack) o
                    : o instanceof List && !((List<?>) o).isEmpty() ? (ItemStack) ((List<?>) o).get(0) : null;
            if (s == null || s.getItem() == null) {
                continue;
            }
            String n;
            try {
                n = s.getDisplayName();
            } catch (Throwable t) {
                n = String.valueOf(s.getItem());
            }
            int idx = names.indexOf(n);
            if (idx < 0) {
                names.add(n);
                counts.add(1);
            } else {
                counts.set(idx, counts.get(idx) + 1);
            }
        }
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < names.size(); i++) {
            out.add(counts.get(i) + "x " + names.get(i));
        }
        return out;
    }

    private static boolean containsNeedle(List<String> texts, String needle) {
        for (String t : texts) {
            if (t.toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(p);
        }
        return sb.toString();
    }

    /** Machine name AND every ingredient/product/by-product/fluid name. */
    private static boolean matchesQuery(MachineType type, MachineRecipe recipe, String needle) {
        if (type.localizedName().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        for (ItemStack[] group : new ItemStack[][]{recipe.inputs, recipe.outputs, recipe.byproducts}) {
            for (ItemStack stack : group) {
                if (stack.getDisplayName().toLowerCase(Locale.ROOT).contains(needle)) {
                    return true;
                }
            }
        }
        return fluidMatches(recipe.fluidInputA, needle) || fluidMatches(recipe.fluidInputB, needle)
                || fluidMatches(recipe.fluidOutputA, needle) || fluidMatches(recipe.fluidOutputB, needle);
    }

    private static boolean fluidMatches(FluidStack stack, String needle) {
        return stack != null && stack.getFluid() != null
                && stack.getFluid().getLocalizedName(stack).toLowerCase(Locale.ROOT).contains(needle);
    }

    // ------------------------------------------------------------------ 9. safety

    private static List<String> safety() {
        List<String> lines = new ArrayList<String>();
        lines.add(TITLE + Lang.tr("sc.manual.safety.title"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.safety.corrosivehead"));
        StringBuilder list = new StringBuilder();
        for (String name : CorrosiveFluids.all()) {
            if (list.length() > 0) {
                list.append(", ");
            }
            list.append(fluidName(name));
        }
        lines.add(list.toString());
        lines.addAll(paragraph("sc.manual.safety.corrosive"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.safety.explosionshead"));
        lines.addAll(paragraph("sc.manual.safety.explosions"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.safety.suithead"));
        lines.addAll(paragraph("sc.manual.safety.suit"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.safety.radiationhead"));
        lines.addAll(paragraph("sc.manual.safety.radiation"));
        lines.add(Lang.tr("sc.manual.safety.radsources"));
        for (com.sc.energy.GeneratorType type : com.sc.energy.GeneratorType.values()) {
            float base = com.sc.tileentity.TileEntityGeneratorSC.radiationBase(type);
            if (base > 0) {
                lines.add(" " + type.localizedName() + R + DIM + " - " + Lang.tr(type == com.sc.energy.GeneratorType.RTG
                                ? "sc.manual.safety.radline.rtg" : "sc.manual.safety.radline",
                        com.sc.radiation.RadiationSC.fmt(base), com.sc.tileentity.TileEntityGeneratorSC.radiationRadius(type)));
            }
        }
        lines.add(" " + Lang.tr("sc.manual.safety.radline.carried", com.sc.radiation.RadiationSC.fmt(com.sc.radiation.RadiationSC.CAPSULE_LEVEL),
                com.sc.radiation.RadiationSC.fmt(com.sc.radiation.RadiationSC.MONAZITE_STACK_LEVEL)));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.safety.dosehead"));
        lines.addAll(paragraph("sc.manual.safety.dose"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.safety.radprothead"));
        lines.addAll(paragraph("sc.manual.safety.radprot"));
        lines.add("");
        lines.add(HEAD + Lang.tr("sc.manual.safety.radcurehead"));
        lines.addAll(paragraph("sc.manual.safety.radcure"));
        return lines;
    }

    // ------------------------------------------------------------------ shared helpers

    private static List<String> describeRecipe(MachineRecipe recipe, MachineType type) {
        List<String> lines = new ArrayList<String>();
        StringBuilder in = new StringBuilder("  " + Lang.tr("sc.manual.recipes.in") + ": ");
        boolean first = true;
        for (ItemStack stack : recipe.inputs) {
            first = append(in, stack.stackSize + "x " + stack.getDisplayName(), first);
        }
        first = appendFluid(in, recipe.fluidInputA, first);
        appendFluid(in, recipe.fluidInputB, first);
        lines.add(in.toString());

        StringBuilder out = new StringBuilder("  " + Lang.tr("sc.manual.recipes.out") + ": ");
        first = true;
        for (ItemStack stack : recipe.outputs) {
            first = append(out, stack.stackSize + "x " + stack.getDisplayName(), first);
        }
        for (int i = 0; i < recipe.byproducts.length; i++) {
            first = append(out, recipe.byproducts[i].getDisplayName() + " (" + percent(recipe.byproductChances[i]) + "%)", first);
        }
        first = appendFluid(out, recipe.fluidOutputA, first);
        appendFluid(out, recipe.fluidOutputB, first);
        lines.add(out.toString());

        String time = String.format(Locale.ROOT, "%.1f", recipe.ticks / 20f);
        String meta = Lang.tr("sc.manual.recipes.meta", time, type.euPerTick, String.valueOf((long) type.euPerTick * recipe.ticks));
        if (recipe.defectChance > 0) {
            meta += ", " + Lang.tr("sc.manual.recipes.defect", percent(recipe.defectChance));
        }
        lines.add("  " + DIM + meta);
        return lines;
    }

    private static boolean append(StringBuilder sb, String part, boolean first) {
        if (!first) {
            sb.append(", ");
        }
        sb.append(part);
        return false;
    }

    private static boolean appendFluid(StringBuilder sb, FluidStack stack, boolean first) {
        if (stack == null) {
            return first;
        }
        return append(sb, stack.amount + " mB " + stack.getFluid().getLocalizedName(stack), first);
    }

    private static String fluidName(String name) {
        Fluid fluid = FluidRegistry.getFluid(name);
        return fluid == null ? name : fluid.getLocalizedName(new FluidStack(fluid, 1000));
    }

    private static String percent(float chance) {
        float pct = chance * 100f;
        return pct == Math.round(pct) ? String.valueOf(Math.round(pct)) : String.format(Locale.ROOT, "%.1f", pct);
    }

    /** A block of prose stored as key.1, key.2, ... so translators control their own line breaks. */
    private static List<String> paragraph(String baseKey) {
        List<String> lines = new ArrayList<String>();
        for (int i = 1; ; i++) {
            String text = Lang.trOr(baseKey + "." + i, null);
            if (text == null) {
                break;
            }
            lines.add(text);
        }
        return lines;
    }
}
