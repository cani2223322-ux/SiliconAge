package com.sc.manual;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.sc.bridge.BridgeMathSC;
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
import com.sc.util.Material;
import com.sc.util.OreEntry;
import com.sc.util.PipeType;
import com.sc.util.SiliconMaterial;
import com.sc.util.WeaponType;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

/**
 * The illustrated handbook (GuiBook): chapters of articles built from the mod's registries and
 * constants - item icons, crafting grids, machine recipes, ore cards, the multiblocks layer by
 * layer, tables - so a page never disagrees with the game. Every word of prose is a .lang key
 * (the old handbook's sc.manual.* texts, plus sc.book.*). Plain data: safe on a server.
 */
public final class BookContent {

    private static List<BookEntry> cache;
    private static String cacheLang;
    private static Map<String, BookEntry> byId;
    private static Map<String, BookEntry> byStack;

    private BookContent() {
    }

    /** Every article, in chapter order (built once per language). */
    public static synchronized List<BookEntry> all() {
        // the server's options (ConfigSyncSC) change capacities and rates on the pages: built again with them
        String lang = Lang.tr("language.code") + "|" + Lang.tr("sc.book.lang") + "|" + ConfigSC.stamp();
        if (cache == null || !lang.equals(cacheLang)) {
            BookIndexSC.begin();                        // МК-2: one pass over the recipes for this build
            try {
                build(lang);
            } finally {
                BookIndexSC.end();
            }
        }
        return cache;
    }

    private static void build(String lang) {
        List<BookEntry> list = new ArrayList<BookEntry>();
        intro(list);
        ores(list);
        silicon(list);
        machines(list);
        generators(list);
        bridge(list);
        energy(list);
        armor(list);
        field(list);
        safety(list);
        BookMaterialsSC.materials(list);
        BookReferenceSC.reference(list);
        BookPathSC.path(list);                      // last: its steps link to (and name) the other articles
        list.add(new BookEntry("recipes", BookChapter.RECIPES, new ItemStack(Blocks.crafting_table), Lang.tr("sc.book.recipes.title"))
                .add(BookEl.dim(Lang.tr("sc.manual.recipes.hint"))));
        byId = new HashMap<String, BookEntry>();
        byStack = new HashMap<String, BookEntry>();
        for (BookEntry e : list) {
            byId.put(e.id, e);
            for (ItemStack s : e.about) {
                String k = key(s);
                if (!byStack.containsKey(k)) {
                    byStack.put(k, e);
                }
            }
        }
        cache = list;
        cacheLang = lang;
    }

    /**
     * Self-test (МК-2): the Components chapter's look-ups give the same lines with the recipe index
     * as by scanning every recipe. @return null when they agree, else the first item that differs
     */
    public static synchronized String indexMatchesScan() {
        List<String> scan = BookMaterialsSC.lookups();
        BookIndexSC.begin();
        try {
            List<String> indexed = BookMaterialsSC.lookups();
            for (int i = 0; i < Math.max(scan.size(), indexed.size()); i++) {
                String a = i < scan.size() ? scan.get(i) : null, b = i < indexed.size() ? indexed.get(i) : null;
                if (a == null ? b != null : !a.equals(b)) {
                    return a + " <> " + b;
                }
            }
            return null;
        } finally {
            BookIndexSC.end();
        }
    }

    /** Built again on the next use (the self-test builds it before other mods' recipes exist). */
    public static synchronized void invalidate() {
        cache = null;
    }

    public static List<BookEntry> chapter(BookChapter ch) {
        List<BookEntry> out = new ArrayList<BookEntry>();
        for (BookEntry e : all()) {
            if (e.chapter == ch) {
                out.add(e);
            }
        }
        return out;
    }

    public static BookEntry byId(String id) {
        all();
        return byId.get(id);
    }

    /** The article about an item (exact metadata first, then any), or null. */
    public static BookEntry entryFor(ItemStack s) {
        if (s == null || s.getItem() == null) {
            return null;
        }
        all();
        BookEntry e = byStack.get(key(s));
        return e != null ? e : byStack.get(Item.itemRegistry.getNameForObject(s.getItem()) + ":*");
    }

    static String key(ItemStack s) {
        String name = String.valueOf(Item.itemRegistry.getNameForObject(s.getItem()));
        return name + ":" + (s.getItemDamage() == OreDictionary.WILDCARD_VALUE ? "*" : String.valueOf(s.getItemDamage()));
    }

    public static ItemStack chapterIcon(BookChapter ch) {
        switch (ch) {
            case INTRO: return new ItemStack(ModItems.manual);
            case ORES: return new ItemStack(ModBlocks.oreSC, 1, OreEntry.CHALCOPYRITE.meta());
            case SILICON: return ModItems.siliconMaterial.stackOf(SiliconMaterial.SI_WAFER);
            case MACHINES: return machineStack(MachineType.CRUSHER);
            case GENERATORS: return ModBlocks.generatorStack(GeneratorType.TOKAMAK_XV, 1);
            case ENERGY: return new ItemStack(ModBlocks.cableSC, 1, CableType.SUPERCONDUCTOR.ordinal());
            case ARMOR: return new ItemStack(ModItems.ARMOR.get(ArmorSuit.values()[ArmorSuit.values().length - 1])[1]);
            case FIELD: return new ItemStack(ModBlocks.fieldGeneratorSC);
            case SAFETY: return new ItemStack(ModItems.dosimeter);
            case PATH: return new ItemStack(Items.compass);
            case BRIDGE: return com.sc.block.BlockBridgeSC.stack(com.sc.block.BlockBridgeSC.CONTROLLER, 1);
            case MATERIALS: return new ItemStack(ModItems.component("capacitor"));
            case REFERENCE: return new ItemStack(Blocks.bookshelf);
            default: return new ItemStack(Blocks.crafting_table);
        }
    }

    // ------------------------------------------------------------------ 1. introduction

    /** The first steps: an id, what's needed (any of), the icon. */
    public static final String[] STEPS = {"copper", "cable", "generator", "machine", "steel", "storage", "sand", "wafer", "controller"};

    public static ItemStack[] stepItems(int i) {
        switch (i) {
            case 0: return new ItemStack[]{ModItems.ingot.stackOf(Material.COPPER)};
            case 1: return new ItemStack[]{new ItemStack(ModBlocks.cableSC, 1, CableType.COPPER_BARE.ordinal())};
            case 2: return new ItemStack[]{ModBlocks.generatorStack(GeneratorType.SOLID_FUEL, 1), ModBlocks.generatorStack(GeneratorType.COMBUSTION, 1)};
            case 3: {
                List<ItemStack> lv = new ArrayList<ItemStack>();
                for (MachineType t : MachineType.values()) {
                    if (t.tier == Tier.LV) {
                        lv.add(machineStack(t));
                    }
                }
                return lv.toArray(new ItemStack[lv.size()]);
            }
            case 4: return new ItemStack[]{ModItems.ingot.stackOf(Material.STEEL)};
            case 5: return new ItemStack[]{new ItemStack(ModBlocks.energyStorageSC, 1, OreDictionary.WILDCARD_VALUE)};
            case 6: return new ItemStack[]{ModItems.siliconMaterial.stackOf(SiliconMaterial.SILICA_SAND)};
            case 7: return new ItemStack[]{ModItems.siliconMaterial.stackOf(SiliconMaterial.SI_WAFER)};
            default: return new ItemStack[]{ModItems.siliconMaterial.stackOf(SiliconMaterial.CONTROLLER)};
        }
    }

    private static void intro(List<BookEntry> list) {
        BookChapter c = BookChapter.INTRO;
        list.add(new BookEntry("intro", c, new ItemStack(ModItems.manual), Lang.tr("sc.book.intro.about"))
                .add(BookEl.title(Lang.tr("sc.manual.intro.title"))).addAll(paras("sc.manual.intro.about"))
                .add(BookEl.gap()).add(BookEl.dim(Lang.tr("sc.book.intro.how"))).add(BookEl.dim(Lang.tr("sc.book.intro.search")))
                .about(new ItemStack(ModItems.manual)));
        BookEntry start = new BookEntry("start", c, ModItems.ingot.stackOf(Material.COPPER), Lang.tr("sc.manual.intro.starthead"));
        start.add(BookEl.title(Lang.tr("sc.manual.intro.starthead"))).add(BookEl.dim(Lang.tr("sc.book.steps.hint")));
        for (int i = 0; i < STEPS.length; i++) {
            start.add(BookEl.check(STEPS[i], stepItems(i)[0], Lang.tr("sc.book.step." + STEPS[i])));
        }
        start.add(BookEl.gap()).addAll(paras("sc.manual.intro.start"));
        start.add(BookEl.head(Lang.tr("sc.book.start.nexthead"))).add(BookEl.para(Lang.tr("sc.book.start.next")))
                .add(BookEl.link("path.after", Lang.tr("sc.book.path_after"))).add(BookEl.link("path", Lang.tr("sc.book.path.title")));
        list.add(start);
        BookEl tiers = BookEl.table(Lang.tr("sc.book.t.tier"), Lang.tr("sc.book.t.voltage"), Lang.tr("sc.book.t.buffer"));
        for (Tier t : Tier.values()) {
            tiers.row(new ItemStack(ModBlocks.energyStorageSC, 1, t.ordinal()), t.name(), t.getVoltage() + " EU/t", String.valueOf(t.getBuffer()));
        }
        StringBuilder steps = new StringBuilder();          // "MV x4, HV x4, ... QV x2, XV x2, SV x4" straight from the voltages
        for (int i = 1; i < Tier.values().length; i++) {
            Tier t = Tier.values()[i];
            steps.append(i > 1 ? ", " : "").append(t.name()).append(" x").append(t.getVoltage() / Tier.values()[i - 1].getVoltage());
        }
        list.add(new BookEntry("tiers", c, new ItemStack(ModBlocks.energyStorageSC, 1, 0), Lang.tr("sc.manual.intro.tiers"))
                .add(BookEl.title(Lang.tr("sc.manual.intro.tiers"))).addAll(paras("sc.manual.intro.power", new Object[]{steps.toString()})).add(tiers)
                .addAll(paras("sc.manual.intro.flow")).add(BookEl.gap())
                .add(BookEl.warn(Lang.tr("sc.manual.intro.warninghead"))).addAll(paras("sc.manual.intro.warning")));
        list.add(new BookEntry("gui", c, machineStack(MachineType.CRUSHER), Lang.tr("sc.manual.intro.guihead"))
                .add(BookEl.title(Lang.tr("sc.manual.intro.guihead"))).addAll(paras("sc.manual.intro.gui")));
        list.add(new BookEntry("controls", c, new ItemStack(ModItems.WRENCHES.get(0)), Lang.tr("sc.manual.intro.controlshead"))
                .add(BookEl.title(Lang.tr("sc.manual.intro.controlshead")))
                .addAll(paras("sc.manual.intro.controls", new Object[]{com.sc.machine.UpgradeType.CLEAR_MB_PER_EU}))
                .add(BookEl.para(Lang.tr("sc.book.gkey"))));
    }

    // ------------------------------------------------------------------ 2. ores

    private static void ores(List<BookEntry> list) {
        BookChapter c = BookChapter.ORES;
        List<ItemStack> all = new ArrayList<ItemStack>();
        for (OreEntry ore : OreEntry.values()) {
            all.add(new ItemStack(ModBlocks.oreSC, 1, ore.meta()));
        }
        list.add(new BookEntry("ores", c, all.get(0), Lang.tr("sc.book.ores.all"))
                .add(BookEl.title(Lang.tr("sc.manual.ores.title", OreEntry.values().length))).addAll(paras("sc.manual.ores.intro"))
                .add(BookEl.items(all)));
        for (OreEntry ore : OreEntry.values()) {
            ItemStack block = new ItemStack(ModBlocks.oreSC, 1, ore.meta());
            BookEntry e = new BookEntry("ore." + ore.name().toLowerCase(Locale.ROOT), c, block, block.getDisplayName());
            e.add(BookEl.ore(ore, block)).about(block);
            processing(e, block);
            list.add(e);
        }
        List<ItemStack> blocks = new ArrayList<ItemStack>();
        for (Material m : com.sc.block.BlockMetalSC.METALS) {
            blocks.add(com.sc.block.BlockMetalSC.stackOf(m, 1));
        }
        BookEntry mb = new BookEntry("metalblocks", c, blocks.get(0), Lang.tr("sc.manual.ores.blockshead"));
        mb.add(BookEl.title(Lang.tr("sc.manual.ores.blockshead"))).addAll(paras("sc.manual.ores.blocks")).add(BookEl.items(blocks));
        mb.about(new ItemStack(ModBlocks.metalBlock, 1, OreDictionary.WILDCARD_VALUE), new ItemStack(ModBlocks.metalBlock2, 1, OreDictionary.WILDCARD_VALUE));
        list.add(mb);
    }

    /** The ore's route as a picture chain (crusher, washer, centrifuge... furnace), its by-products and uses. */
    private static void processing(BookEntry e, ItemStack ore) {
        List<ItemStack> items = new ArrayList<ItemStack>(), machines = new ArrayList<ItemStack>();
        items.add(ore);
        ItemStack current = ore;
        List<BookEl> extras = new ArrayList<BookEl>();
        MachineType[] route = {MachineType.CRUSHER, MachineType.ORE_WASHER, MachineType.CENTRIFUGE, MachineType.KILN,
                MachineType.CHLOR_ALKALI_ELECTROLYZER};
        for (MachineType type : route) {
            MachineRecipe step = singleInputRecipe(type, current);
            if (step == null || step.outputs.length == 0 || !isMaterialItem(step.outputs[0])) {
                continue;
            }
            machines.add(machineStack(type));
            items.add(step.outputs[0]);
            for (int i = 0; i < step.byproducts.length; i++) {
                extras.add(BookEl.item(step.byproducts[i], step.byproducts[i].getDisplayName(),
                        Lang.tr("sc.book.ores.byproduct", type.localizedName(), percent(step.byproductChances[i]))));
            }
            current = step.outputs[0];
        }
        ItemStack ingot = FurnaceRecipes.smelting().getSmeltingResult(current);
        if (ingot != null) {
            machines.add(new ItemStack(Blocks.furnace));
            items.add(ingot);
        }
        if (items.size() > 1) {
            e.add(BookEl.head(Lang.tr("sc.book.ores.route"))).add(BookEl.chain(items, machines));
        }
        ItemStack direct = FurnaceRecipes.smelting().getSmeltingResult(ore);
        if (direct != null && items.size() > 2) {
            e.add(BookEl.dim(Lang.tr("sc.manual.ores.smelt", direct.stackSize, direct.getDisplayName())));
        }
        if (!extras.isEmpty()) {
            e.add(BookEl.head(Lang.tr("sc.book.ores.byproducts"))).addAll(extras);
        }
        List<BookEl> uses = new ArrayList<BookEl>();
        if (current != ore && current.getItem() != ModItems.ingot) {
            for (MachineType type : MachineType.values()) {
                for (MachineRecipe r : RecipeRegistry.recipesFor(type)) {
                    if (r.inputs.length + (r.fluidInputA != null ? 1 : 0) + (r.fluidInputB != null ? 1 : 0) < 2) {
                        continue;
                    }
                    for (ItemStack in : r.inputs) {
                        if (MachineRecipe.isSameIngredient(current, in) && (r.outputs.length > 0 || r.fluidOutputA != null)) {
                            uses.add(BookEl.machine(r));
                        }
                    }
                }
            }
        }
        if (!uses.isEmpty()) {
            e.add(BookEl.head(Lang.tr("sc.book.ores.next"))).addAll(uses);
        }
    }

    private static boolean isMaterialItem(ItemStack stack) {
        return stack.getItem() instanceof com.sc.item.ItemMaterialSC || stack.getItem() == ModItems.siliconMaterial;
    }

    private static MachineRecipe singleInputRecipe(MachineType type, ItemStack input) {
        for (MachineRecipe r : RecipeRegistry.recipesFor(type)) {
            if (r.inputs.length == 1 && MachineRecipe.isSameIngredient(input, r.inputs[0]) && r.outputs.length > 0 && isMaterialItem(r.outputs[0])) {
                return r;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 3. silicon

    private static final MachineType[] CHAIN = {MachineType.CRUSHER, MachineType.BLAST_FURNACE, MachineType.CHEM_REACTOR,
            MachineType.CVD_CHAMBER, MachineType.CZOCHRALSKI_PULLER, MachineType.WIRE_SAW, MachineType.OXIDATION_FURNACE,
            MachineType.PHOTORESIST_COATER, MachineType.STEPPER, MachineType.ETCHING_BATH, MachineType.ION_IMPLANTER,
            MachineType.SPUTTERER, MachineType.DICING_SAW, MachineType.PACKAGER};

    private static void silicon(List<BookEntry> list) {
        BookChapter c = BookChapter.SILICON;
        // the whole route as one picture: each machine's first silicon step from what the last one made
        List<ItemStack> items = new ArrayList<ItemStack>(), machines = new ArrayList<ItemStack>();
        ItemStack current = new ItemStack(ModBlocks.oreSC, 1, OreEntry.QUARTZITE.meta());
        items.add(current);
        for (MachineType type : CHAIN) {
            MachineRecipe step = null;
            for (MachineRecipe r : RecipeRegistry.recipesFor(type)) {
                if (!involvesSilicon(r) || r.outputs.length == 0 || r.outputs[0].getItem() != ModItems.siliconMaterial) {
                    continue;
                }
                boolean fromCurrent = false;
                for (ItemStack in : r.inputs) {
                    fromCurrent |= MachineRecipe.isSameIngredient(current, in);
                }
                if (fromCurrent || step == null && (current.getItem() != ModItems.siliconMaterial
                        || r.outputs[0].getItemDamage() > current.getItemDamage())) {
                    step = r;
                    if (fromCurrent) {
                        break;
                    }
                }
            }
            if (step != null) {
                machines.add(machineStack(type));
                items.add(step.outputs[0]);
                current = step.outputs[0];
            }
        }
        list.add(new BookEntry("silicon", c, ModItems.siliconMaterial.stackOf(SiliconMaterial.SI_WAFER), Lang.tr("sc.book.silicon.all"))
                .add(BookEl.title(Lang.tr("sc.manual.chain.title"))).addAll(paras("sc.manual.chain.intro"))
                .add(BookEl.image("silicon", 256, 96)).add(BookEl.head(Lang.tr("sc.book.silicon.route"))).add(BookEl.chain(items, machines))
                .add(BookEl.gap()).addAll(paras("sc.manual.chain.outro")));
        int n = 1;
        for (MachineType type : CHAIN) {
            BookEntry e = new BookEntry("si." + type.name().toLowerCase(Locale.ROOT), c, machineStack(type), n + ". " + type.localizedName());
            e.add(BookEl.head(n + ". " + type.localizedName(), machineStack(type)))
                    .add(BookEl.dim(type.tier + ", " + type.euPerTick + " EU/t"))
                    .add(BookEl.para(Lang.tr("sc.manual.chain.step." + type.name().toLowerCase(Locale.ROOT))));
            for (MachineRecipe r : RecipeRegistry.recipesFor(type)) {
                if (involvesSilicon(r)) {
                    e.add(BookEl.machine(r));
                }
            }
            e.add(BookEl.link("machine." + type.name().toLowerCase(Locale.ROOT), Lang.tr("sc.book.seemachine", type.localizedName())));
            list.add(e);
            n++;
        }
        for (SiliconMaterial m : SiliconMaterial.values()) {
            byMaterial(list, m);
        }
    }

    /** Silicon materials point at the chain overview (G on a wafer opens it). */
    private static void byMaterial(List<BookEntry> list, SiliconMaterial m) {
        for (BookEntry e : list) {
            if (e.id.equals("silicon")) {
                e.about(ModItems.siliconMaterial.stackOf(m));
            }
        }
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

    public static ItemStack machineStack(MachineType type) {
        return com.sc.block.BlockMachineSC.stackOf(type, 1);
    }

    private static final int RECIPES_SHOWN = 8;

    private static void machines(List<BookEntry> list) {
        BookChapter c = BookChapter.MACHINES;
        BookEntry all = new BookEntry("machines", c, machineStack(MachineType.CRUSHER), Lang.tr("sc.book.machines.all"));
        all.add(BookEl.title(Lang.tr("sc.manual.machines.title"))).addAll(paras("sc.manual.machines.intro", new Object[]{TileEntityMachineSC.TANK_CAPACITY}));
        for (Tier tier : Tier.values()) {
            List<ItemStack> row = new ArrayList<ItemStack>();
            for (MachineType type : MachineType.values()) {
                if (type.tier == tier) {
                    row.add(machineStack(type));
                }
            }
            if (!row.isEmpty()) {
                all.add(BookEl.head(Lang.tr("sc.manual.machines.tierhead", tier.name()))).add(BookEl.items(row));
            }
        }
        list.add(all);
        for (MachineType type : MachineType.values()) {
            ItemStack st = machineStack(type);
            BookEntry e = new BookEntry("machine." + type.name().toLowerCase(Locale.ROOT), c, st, type.localizedName());
            e.about(st).add(BookEl.head(type.localizedName(), st));
            List<MachineRecipe> recipes = RecipeRegistry.recipesFor(type);
            BookEl stats = BookEl.table(Lang.tr("sc.book.t.param"), Lang.tr("sc.book.t.value"));
            stats.row(null, Lang.tr("sc.book.t.tier"), type.tier.name());
            stats.row(null, Lang.tr("sc.book.t.use"), type.euPerTick + " EU/t");
            stats.row(null, Lang.tr("sc.book.t.recipes"), type.isSmelter() ? Lang.tr("sc.manual.machines.smelts")
                    : type.isCompressor() ? Lang.tr("sc.nei.comp.any") : String.valueOf(recipes.size()));
            if (type.heatCapable) {
                stats.row(null, Lang.tr("sc.book.t.heat"), Lang.tr("sc.book.yes"));
            }
            if (RecipeRegistry.usesTank(type, 3)) {
                stats.row(null, Lang.tr("sc.book.t.topout"), Lang.tr("sc.book.yes"));
            }
            e.add(stats).add(BookEl.para(Lang.tr("sc.manual.machine." + type.name().toLowerCase(Locale.ROOT))));
            if (type.isCompressor()) {                 // what it makes: the Singular Reactor's fuel
                int perCapsule = TileEntityMachineSC.MATTER_PER_CAPSULE, block = TileEntityMachineSC.MASS_BLOCK, heavy = TileEntityMachineSC.MASS_HEAVY;
                e.add(BookEl.para(Lang.tr("sc.manual.comp.mass", block, TileEntityMachineSC.MASS_ITEM, heavy, perCapsule,
                        TileEntityMachineSC.COMPRESS_TICKS / 20, type.euPerTick, perCapsule / block, perCapsule / (block * heavy))));
                ItemStack cap = new ItemStack(ModItems.component("matterCapsule"));
                e.about(cap).add(BookEl.head(cap.getDisplayName(), cap))
                        .add(BookEl.para(Lang.tr("sc.manual.matterCapsule", perCapsule)))
                        .add(BookEl.para(Lang.tr("sc.manual.matterCapsule.2", perCapsule / block, perCapsule / (block * heavy))));
                // СМ1: the liquid mode - singular matter instead of capsules
                ItemStack cell = com.sc.item.ItemSingularCellSC.filled(ModItems.singularCell, com.sc.item.ItemSingularCellSC.CAPACITY);
                e.about(new ItemStack(ModItems.singularCell, 1, OreDictionary.WILDCARD_VALUE))
                        .add(BookEl.head(Lang.tr("sc.manual.comp.liquidhead"), cell))
                        .add(BookEl.para(Lang.tr("sc.manual.comp.liquid", com.sc.tileentity.TileEntityMachineSC.SM_PER_CAPSULE,
                                com.sc.tileentity.TileEntityMachineSC.MATTER_PER_CAPSULE, com.sc.tileentity.TileEntityMachineSC.SM_TANK)))
                        .add(BookEl.para(Lang.tr("sc.manual.comp.cell", com.sc.item.ItemSingularCellSC.CAPACITY)));
                // a singularity clot: SM_PER_CLOT mB at once in the liquid mode (the crumbs' article has the rest)
                e.add(BookEl.chain(listOf(new ItemStack(ModItems.singularClot), cell), listOf(machineStack(type))))
                        .add(BookEl.para(Lang.tr("sc.manual.comp.clot", com.sc.item.ItemSingularClotSC.SM_PER_CLOT)))
                        .add(BookEl.link("singularcrumb", Lang.tr("sc.manual.singcrumb.head")));
                crafting(e, new ItemStack(ModItems.singularCell));
            }
            if (!recipes.isEmpty()) {
                e.add(BookEl.head(Lang.tr("sc.book.recipes")));
                for (int i = 0; i < Math.min(RECIPES_SHOWN, recipes.size()); i++) {
                    e.add(BookEl.machine(recipes.get(i)));
                }
                if (recipes.size() > RECIPES_SHOWN) {
                    e.add(BookEl.dim(Lang.tr("sc.book.morerecipes", recipes.size() - RECIPES_SHOWN)));
                }
            }
            crafting(e, st);
            list.add(e);
        }
        int clear = com.sc.machine.UpgradeType.CLEAR_MB_PER_EU;
        list.add(simple("sides", c, new ItemStack(ModBlocks.pipeSC), "sc.manual.machines.sideshead", "sc.manual.machines.sides")
                .addAll(paras("sc.manual.machines.vent", new Object[]{clear})));
        list.add(simple("wrench", c, new ItemStack(ModItems.WRENCHES.get(0)), "sc.manual.machines.wrenchhead", "sc.manual.machines.wrench")
                .about(new ItemStack(ModItems.WRENCHES.get(0), 1, OreDictionary.WILDCARD_VALUE)));
        // К8: the quarry in three articles - the quarry itself, its modules, the Exo Drilling Rig
        BookEntry quarry = new BookEntry("quarry", c, new ItemStack(ModBlocks.quarrySC), Lang.tr("sc.manual.machines.quarryhead"));
        quarry.add(BookEl.title(Lang.tr("sc.manual.machines.quarryhead")));
        List<BookEl> qp = paras("sc.manual.machines.quarry");
        quarry.add(qp.get(0)).add(qp.get(2));
        for (int i = 5; i < qp.size(); i++) {                       // .6 and on (К1, К2): the digging rules, before the menu
            quarry.add(qp.get(i));
        }
        quarry.add(BookEl.head(Lang.tr("sc.book.quarry.menuhead"))).add(qp.get(1));
        quarry.add(BookEl.link("quarry.modules", Lang.tr("sc.book.quarry.modules"))).add(BookEl.link("quarry.exo", Lang.tr("sc.book.quarry.exo")));
        quarry.about(new ItemStack(ModBlocks.quarrySC, 1, OreDictionary.WILDCARD_VALUE), new ItemStack(ModItems.areaCard));
        crafting(quarry, new ItemStack(ModBlocks.quarrySC));
        list.add(quarry);
        BookEntry qm = new BookEntry("quarry.modules", c, new ItemStack(ModItems.quarryModule), Lang.tr("sc.book.quarry.modules"));
        qm.add(BookEl.title(Lang.tr("sc.book.quarry.modules"))).add(qp.get(3)).add(qp.get(4));
        for (com.sc.item.ItemQuarryModuleSC.Kind k : com.sc.item.ItemQuarryModuleSC.Kind.values()) {
            ItemStack s = ModItems.quarryModule.stackOf(k);
            qm.add(BookEl.item(s, s.getDisplayName(), BookReferenceSC.quarryModuleLine(k)));
        }
        qm.about(new ItemStack(ModItems.quarryModule, 1, OreDictionary.WILDCARD_VALUE));
        list.add(qm);
        list.add(BookReferenceSC.exoRig(c));
        list.add(BookReferenceSC.defects(c));
        int perTank = com.sc.machine.UpgradeType.TANK_PER_UPGRADE, maxTank = com.sc.machine.UpgradeType.MAX_TANK_UPGRADES;
        BookEntry up = simple("upgrades", c, ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.OVERDRIVE), "sc.manual.machines.upgradeshead")
                .addAll(paras("sc.manual.machines.upgrades", new Object[]{TileEntityMachineSC.UPGRADE_SLOTS, com.sc.machine.UpgradeType.MAX_EFFECTIVE,
                        num(perTank), maxTank, num(TileEntityMachineSC.TANK_CAPACITY), num(TileEntityMachineSC.TANK_CAPACITY + perTank * maxTank), clear}));
        for (com.sc.machine.UpgradeType type : com.sc.machine.UpgradeType.values()) {
            ItemStack s = ModItems.upgrade.stackOf(type);
            up.add(BookEl.item(s, s.getDisplayName(), Lang.tr("sc.upgrade.tooltip." + type.name().toLowerCase(Locale.ROOT))));
        }
        up.about(new ItemStack(ModItems.upgrade, 1, OreDictionary.WILDCARD_VALUE));
        list.add(up);
        list.add(simple("heat", c, machineStack(MachineType.BLAST_FURNACE), "sc.manual.machines.heathead")
                .add(BookEl.para(Lang.tr("sc.manual.machines.heatrule", TileEntityMachineSC.getHeatCapacity(), TileEntityMachineSC.HEAT_RESUME))));
        list.add(simple("trouble", c, new ItemStack(Blocks.redstone_torch), "sc.manual.machines.troublehead", "sc.manual.machines.trouble"));
        for (BookEntry e : list) {
            if (e.id.equals("wrench")) {
                for (com.sc.item.ItemWrenchSC w : ModItems.WRENCHES) {
                    e.about(new ItemStack(w, 1, OreDictionary.WILDCARD_VALUE));
                }
            }
        }
    }

    /** The crafting-table recipes that make this stack. */
    static void crafting(BookEntry e, ItemStack out) {
        List<IRecipe> found = craftingFor(out);
        if (!found.isEmpty()) {
            e.add(BookEl.head(Lang.tr("sc.book.craft")));
            for (IRecipe r : found) {
                e.add(BookEl.craft(r));
            }
        }
    }

    public static List<IRecipe> craftingFor(ItemStack out) {
        List<IRecipe> found = new ArrayList<IRecipe>();
        for (Object o : BookIndexSC.craftMaking(out)) {     // МК-2: while the book builds, only the recipes making this item
            IRecipe r = (IRecipe) o;
            ItemStack res = r.getRecipeOutput();
            if (res != null && res.getItem() == out.getItem() && (res.getItemDamage() == out.getItemDamage()
                    || out.getItemDamage() == OreDictionary.WILDCARD_VALUE) && found.size() < 4) {
                found.add(r);
            }
        }
        return found;
    }

    // ------------------------------------------------------------------ 5. generators

    private static void generators(List<BookEntry> list) {
        BookChapter c = BookChapter.GENERATORS;
        List<ItemStack> all = new ArrayList<ItemStack>();
        for (GeneratorType t : GeneratorType.values()) {
            if (t != GeneratorType.CREATIVE) {
                all.add(ModBlocks.generatorStack(t, 1));
            }
        }
        list.add(new BookEntry("generators", c, all.get(0), Lang.tr("sc.book.gen.all"))
                .add(BookEl.title(Lang.tr("sc.manual.machines.generators"))).addAll(paras("sc.manual.gen.rules")).add(BookEl.items(all)));
        for (GeneratorType type : GeneratorType.values()) {
            if (type == GeneratorType.CREATIVE) {
                continue;
            }
            ItemStack st = ModBlocks.generatorStack(type, 1);
            BookEntry e = new BookEntry("gen." + type.name().toLowerCase(Locale.ROOT), c, st, type.localizedName());
            e.about(st).add(BookEl.head(type.localizedName(), st));
            BookEl stats = BookEl.table(Lang.tr("sc.book.t.param"), Lang.tr("sc.book.t.value"));
            stats.row(null, Lang.tr("sc.book.t.tier"), type == GeneratorType.TOKAMAK_XV ? type.tier.name() + " / " + Tier.SV.name() : type.tier.name());
            stats.row(null, Lang.tr("sc.book.t.output"), type == GeneratorType.SINGULAR_REACTOR
                    ? Lang.tr("sc.manual.generator.sing.output", type.euPerTick, com.sc.tileentity.SingularReactorSC.MAX_OUTPUT) : type.euPerTick + " EU/t");
            stats.row(null, Lang.tr("sc.book.t.fuel"), fuel(type));
            float rad = TileEntityGeneratorSC.radiationBase(type);
            if (rad > 0) {
                stats.row(null, Lang.tr("sc.book.t.radiation"), com.sc.radiation.RadiationSC.fmt(rad) + " / "
                        + TileEntityGeneratorSC.radiationRadius(type) + " " + Lang.tr("sc.book.blocks"));
            }
            e.add(stats).add(BookEl.para(Lang.tr("sc.manual.generator." + type.name().toLowerCase(Locale.ROOT))))
                    .addAll(paras("sc.manual.generator." + type.name().toLowerCase(Locale.ROOT) + ".note"));  // Г-4: notes .note.1, .2...
            if (type == GeneratorType.TOKAMAK) {
                e.add(BookEl.head(Lang.tr("sc.book.build"))).add(tokamakRing());
            }
            if (type == GeneratorType.TOKAMAK_XV) {
                e.add(BookEl.head(Lang.tr("sc.manual.generator.bighead"))).add(BookEl.image("tokamakxv", 256, 128)).add(tokamakXv())
                        .add(BookEl.items(materials(tokamakXvLayers()))).addAll(paras("sc.manual.generator.big"))
                        .add(BookEl.head(Lang.tr("sc.manual.generator.bigsvhead"))).addAll(paras("sc.manual.generator.bigsv"));
                e.about(new ItemStack(ModBlocks.tokamakCoil), new ItemStack(ModBlocks.leadBlock), new ItemStack(ModBlocks.leadGlass));
            }
            if (type == GeneratorType.SINGULAR_REACTOR) {
                singular(e);
            }
            crafting(e, st);
            list.add(e);
            if (type == GeneratorType.SINGULAR_REACTOR) {            // К8: the fuel and the cycle, the accidents - their own articles
                list.add(singularFuel(c, st));
                list.add(singularSafety(c, st));
            }
        }
    }

    /**
     * The Singular Reactor's main article: what it makes, the build by layer (a picture and the
     * layers); the fuel and cycle, the accidents - singularFuel / singularSafety (sc.manual.generator.sing.*).
     */
    private static void singular(BookEntry e) {
        e.add(BookEl.head(Lang.tr("sc.manual.generator.sing.byproducthead")))      // СМ2: singular matter while it runs
                .add(BookEl.para(Lang.tr("sc.manual.generator.sing.byproduct", com.sc.tileentity.SingularReactorSC.SM_PER_SECOND,
                        com.sc.tileentity.SingularReactorSC.SM_TANK)));
        e.add(BookEl.dim(Lang.tr("sc.manual.generator.sing.smport")));      // МК-5: a port takes matter only once it holds some
        e.add(BookEl.head(Lang.tr("sc.manual.generator.sing.buildhead"))).add(BookEl.image("singular_build", 300, 112))
                .add(BookEl.dim(Lang.tr("sc.book.sing.buildimg"))).add(singularBuild())
                .add(BookEl.items(materials(singularLayers()))).addAll(paras("sc.manual.generator.sing.build", null, null,
                        new Object[]{com.sc.tileentity.SingularReactorSC.TANKS_MAX, com.sc.tileentity.SingularReactorSC.STORES_MAX}));
        e.add(BookEl.link("gen.singular_reactor.fuel", Lang.tr("sc.book.sing.fuel"))).add(BookEl.link("gen.singular_reactor.safety", Lang.tr("sc.book.sing.safety")));
        e.about(new ItemStack(ModBlocks.gravityCoil));
    }

    /** The Singular Reactor's fuel, the cycle, the mass and its window. */
    private static BookEntry singularFuel(BookChapter c, ItemStack st) {
        BookEntry e = new BookEntry("gen.singular_reactor.fuel", c, new ItemStack(ModItems.component("matterCapsule")), Lang.tr("sc.book.sing.fuel"));
        e.add(BookEl.title(Lang.tr("sc.book.sing.fuel")));
        // К14: the numbers of the fuel and the cycle straight from SingularReactorSC
        long ignition = com.sc.tileentity.SingularReactorSC.IGNITION_EU;
        int charge = com.sc.tileentity.SingularReactorSC.CHARGE_PER_TICK;
        int[] feed = com.sc.tileentity.SingularReactorSC.FEED_TICKS;
        e.add(BookEl.head(Lang.tr("sc.manual.generator.sing.fuelhead"))).addAll(paras("sc.manual.generator.sing.fuel",
                new Object[]{number((float) com.sc.tileentity.SingularReactorSC.HE_PER_TICK), num(com.sc.tileentity.SingularReactorSC.HE_START),
                        num(com.sc.tileentity.SingularReactorSC.D_IGNITION), number((float) com.sc.tileentity.SingularReactorSC.D_PER_TICK),
                        num(com.sc.tileentity.SingularReactorSC.ARGON_STOP)},
                new Object[]{Math.round(com.sc.tileentity.SingularReactorSC.CAPSULE_MASS * 100), feed[feed.length - 1] / 1200, feed[0] / 1200}));
        e.add(BookEl.head(Lang.tr("sc.manual.generator.sing.cyclehead"))).addAll(paras("sc.manual.generator.sing.cycle",
                new Object[]{num(com.sc.tileentity.SingularReactorSC.HE_START), num(com.sc.tileentity.SingularReactorSC.D_IGNITION),
                        ignition / 1000000, num(charge), Math.round(ignition / (double) charge / 20), com.sc.tileentity.SingularReactorSC.COMPRESS_TICKS / 20,
                        Math.round(com.sc.tileentity.SingularReactorSC.START_MASS * 100)},
                new Object[]{num(Tier.SV.getVoltage()), com.sc.tileentity.SingularReactorSC.DRAIN_TICKS / 20,
                        Math.round(ignition * com.sc.tileentity.SingularReactorSC.RETURN_SHARE / 1000000)}));
        e.add(BookEl.head(Lang.tr("sc.manual.generator.sing.masshead"))).addAll(paras("sc.manual.generator.sing.mass"));
        BookEl t = BookEl.table(Lang.tr("sc.manual.generator.sing.t.mass"), Lang.tr("sc.manual.generator.sing.t.power"),
                Lang.tr("sc.manual.generator.sing.t.rad"), Lang.tr("sc.manual.generator.sing.t.risk"));
        double[] at = {0.10, 0.30, 0.55, 0.85};
        String[] range = {"< 20%", "20-40%", "40-70%", "> 70%"};
        for (int i = 0; i < 4; i++) {
            t.row(null, range[i], com.sc.tileentity.SingularReactorSC.outputFor(at[i]) + " EU/t",
                    com.sc.radiation.RadiationSC.fmt(com.sc.tileentity.SingularReactorSC.radiationLevelFor(at[i])) + " / "
                            + com.sc.tileentity.SingularReactorSC.radiationRadiusFor(at[i]) + " " + Lang.tr("sc.book.blocks"),
                    Lang.tr("sc.manual.generator.sing.t.risk." + i));
        }
        e.add(t);
        e.add(BookEl.head(Lang.tr("sc.manual.generator.sing.feedhead"))).addAll(paras("sc.manual.generator.sing.feed"));
        e.add(BookEl.link("gen.singular_reactor", st.getDisplayName())).add(BookEl.link("gen.singular_reactor.safety", Lang.tr("sc.book.sing.safety")));
        return e;
    }

    /** The Singular Reactor's accidents, radiation and its screen. */
    private static BookEntry singularSafety(BookChapter c, ItemStack st) {
        BookEntry e = new BookEntry("gen.singular_reactor.safety", c, new ItemStack(ModItems.dosimeter), Lang.tr("sc.book.sing.safety"));
        e.add(BookEl.title(Lang.tr("sc.book.sing.safety")));
        e.add(BookEl.head(Lang.tr("sc.manual.generator.sing.accidenthead"))).addAll(paras("sc.manual.generator.sing.accident"));
        e.add(BookEl.head(Lang.tr("sc.manual.generator.sing.radhead"))).addAll(paras("sc.manual.generator.sing.rad"));
        e.add(BookEl.head(Lang.tr("sc.manual.generator.sing.screenhead"))).addAll(paras("sc.manual.generator.sing.screen"));
        e.add(BookEl.link("gen.singular_reactor", st.getDisplayName())).add(BookEl.link("radiation", Lang.tr("sc.manual.safety.radiationhead")));
        return e;
    }

    /** The 7x7x5, floor to cap: lead floor; coil ring and walls with ports; the reactor, walls and ports; coils again; the cap with a window. */
    public static ItemStack[][][] singularLayers() {
        ItemStack lead = new ItemStack(ModBlocks.leadBlock), glass = new ItemStack(ModBlocks.leadGlass), coil = new ItemStack(ModBlocks.gravityCoil);
        ItemStack tank = new ItemStack(ModBlocks.tankSC, 1, 2), store = new ItemStack(ModBlocks.energyStorageSC, 1, Tier.SV.ordinal());
        ItemStack core = ModBlocks.generatorStack(GeneratorType.SINGULAR_REACTOR, 1);
        ItemStack[][][] l = new ItemStack[5][7][7];
        for (int y = 0; y < 5; y++) {
            for (int z = 0; z < 7; z++) {
                for (int x = 0; x < 7; x++) {
                    int role = com.sc.tileentity.SingularReactorSC.cellRole(x - 3, y - 2, z - 3);
                    l[y][z][x] = role == 0 || role == 1 ? lead : role == 2 ? coil : role == 4 ? core : null;
                }
            }
        }
        l[4][3][3] = glass;             // a window in the cap
        l[2][3][0] = tank;              // liquid helium
        l[2][3][6] = tank;              // deuterium
        l[2][0][3] = tank;              // argon
        l[2][6][3] = store;             // the energy storage (IV+, an SV one holds the whole charge)
        l[1][3][0] = tank;              // a second helium tank, below
        return l;
    }

    private static BookEl singularBuild() {
        String[] names = new String[5];
        for (int i = 0; i < 5; i++) {
            names[i] = Lang.tr("sc.book.layer.sing." + i);
        }
        return BookEl.layers(singularLayers(), names);
    }

    static String fuel(GeneratorType type) {
        switch (type.kind) {
            case PASSIVE: return Lang.tr("sc.manual.machines.passive");
            case FUSION: return Lang.tr("sc.manual.machines.fusioninfo", String.valueOf(type.ignitionThreshold()), TileEntityGeneratorSC.CELL_BURN_TICKS / 1200);
            case DUAL_FLUID: return type.fuelRatePerTick + " " + mb() + "/t " + fluidName(type.fuelFluidName) + " + " + type.fuel2RatePerTick + " " + mb() + "/t "
                    + fluidName(type.fuel2FluidName);
            case FLUID_FUEL:
            case EXO: return type.fuelRatePerTick + " " + mb() + "/t " + fluidName(type.fuelFluidName);
            default: return Lang.tr("sc.manual.gen.kind." + type.kind.name().toLowerCase(Locale.ROOT));
        }
    }

    private static BookEl tokamakRing() {
        ItemStack coil = new ItemStack(ModBlocks.tokamakCoil), core = ModBlocks.generatorStack(GeneratorType.TOKAMAK, 1);
        ItemStack[][][] l = {{{coil, coil, coil}, {coil, core, coil}, {coil, coil, coil}}};
        return BookEl.layers(l, new String[]{Lang.tr("sc.book.layer.one")});
    }

    public static ItemStack[][][] tokamakXvLayers() {
        ItemStack lead = new ItemStack(ModBlocks.leadBlock), coil = new ItemStack(ModBlocks.tokamakCoil), tank = new ItemStack(ModBlocks.tankSC, 1, 1);
        ItemStack store = new ItemStack(ModBlocks.energyStorageSC, 1, Tier.IV.ordinal()), core = ModBlocks.generatorStack(GeneratorType.TOKAMAK_XV, 1);
        ItemStack glass = new ItemStack(ModBlocks.leadGlass);
        ItemStack[][][] l = new ItemStack[3][7][7];
        for (int z = 0; z < 7; z++) {
            for (int x = 0; x < 7; x++) {
                l[0][z][x] = lead;
                l[2][z][x] = (x == 3 && z == 3) ? glass : lead;
                boolean wall = x == 0 || x == 6 || z == 0 || z == 6;
                l[1][z][x] = wall ? lead : coil;
            }
        }
        l[1][3][3] = core;
        l[1][3][0] = tank;              // liquid helium
        l[1][3][6] = tank;              // hydrogen
        l[1][0][3] = tank;              // deuterium
        l[1][2][0] = tank;              // argon
        l[1][6][3] = store;             // the energy storage (IV+)
        return l;
    }

    private static BookEl tokamakXv() {
        return BookEl.layers(tokamakXvLayers(), new String[]{Lang.tr("sc.book.layer.floor"), Lang.tr("sc.book.layer.middle"), Lang.tr("sc.book.layer.cap")});
    }

    /** What a multiblock takes: each block once, its count as the stack size. */
    public static List<ItemStack> materials(ItemStack[][][] layers) {
        Map<String, ItemStack> count = new LinkedHashMap<String, ItemStack>();
        for (ItemStack[][] layer : layers) {
            for (ItemStack[] row : layer) {
                for (ItemStack s : row) {
                    if (s == null) {
                        continue;
                    }
                    String k = key(s);
                    ItemStack have = count.get(k);
                    if (have == null) {
                        have = s.copy();
                        have.stackSize = 0;
                        count.put(k, have);
                    }
                    have.stackSize++;
                }
            }
        }
        return new ArrayList<ItemStack>(count.values());
    }

    // ------------------------------------------------------------------ 6. energy & logistics

    private static void energy(List<BookEntry> list) {
        BookChapter c = BookChapter.ENERGY;
        list.add(simple("energy", c, new ItemStack(ModBlocks.cableSC, 1, CableType.COPPER_INSULATED.ordinal()), "sc.manual.energy.title", "sc.manual.energy.intro")
                .add(BookEl.image("energy", 256, 96)));
        BookEl cables = BookEl.table(Lang.tr("sc.book.t.cable"), Lang.tr("sc.book.t.tier"), Lang.tr("sc.book.t.amps"), "EU/t", Lang.tr("sc.book.t.loss"));
        BookEntry ce = new BookEntry("cables", c, new ItemStack(ModBlocks.cableSC, 1, CableType.SILVER.ordinal()), Lang.tr("sc.manual.energy.cables"));
        for (CableType type : CableType.values()) {
            ItemStack s = new ItemStack(ModBlocks.cableSC, 1, type.ordinal());
            cables.row(s, s.getDisplayName(), type.tier.name(), String.valueOf(type.maxAmps), String.valueOf(type.maxThroughput()), String.valueOf(type.lossPerBlock));
        }
        ce.add(BookEl.title(Lang.tr("sc.manual.energy.cables"))).add(cables).addAll(paras("sc.manual.energy.rules"))
                .addAll(paras("sc.manual.energy.cablesv")).add(BookEl.link("bundles", Lang.tr("sc.manual.energy.bundlehead")))
                .about(new ItemStack(ModBlocks.cableSC, 1, OreDictionary.WILDCARD_VALUE));
        list.add(ce);
        // К8: the conduit bundles - their own article
        list.add(new BookEntry("bundles", c, new ItemStack(ModBlocks.conduitBundle), Lang.tr("sc.manual.energy.bundlehead"))
                .add(BookEl.title(Lang.tr("sc.manual.energy.bundlehead"))).addAll(paras("sc.manual.energy.bundle"))
                .add(BookEl.link("cables", Lang.tr("sc.manual.energy.cables"))).add(BookEl.link("pipes", Lang.tr("sc.manual.energy.pipes")))
                .add(BookEl.link("tube", new ItemStack(ModBlocks.tubeItemPneumatic).getDisplayName()))
                .about(new ItemStack(ModBlocks.conduitBundle, 1, OreDictionary.WILDCARD_VALUE)));
        BookEntry st = new BookEntry("storage", c, new ItemStack(ModBlocks.energyStorageSC, 1, 2), Lang.tr("sc.manual.energy.storagehead"));
        st.add(BookEl.title(Lang.tr("sc.manual.energy.storagehead")));
        for (Tier tier : Tier.values()) {
            ItemStack s = new ItemStack(ModBlocks.energyStorageSC, 1, tier.ordinal());
            st.add(BookEl.item(s, s.getDisplayName(), Lang.tr("sc.manual.energy.storageline",
                    String.valueOf(com.sc.tileentity.TileEntityEnergyStorageSC.capacityOf(tier)), tier.getVoltage())));
        }
        StringBuilder slots = new StringBuilder();
        for (Tier tier : Tier.values()) {
            slots.append(slots.length() == 0 ? "" : ", ").append(tier.name()).append(" ").append(com.sc.tileentity.TileEntityEnergyStorageSC.chargeSlotsFor(tier));
        }
        StringBuilder highMachines = new StringBuilder();     // the machines above EV (the Matter Compressor)
        for (MachineType t : MachineType.values()) {
            if (t.tier.ordinal() > Tier.EV.ordinal()) {
                highMachines.append(highMachines.length() == 0 ? "" : ", ").append(t.localizedName()).append(" (").append(t.tier.name()).append(')');
            }
        }
        st.addAll(paras("sc.manual.energy.storage", null, null, new Object[]{num(Tier.IV.getVoltage()), num(Tier.QV.getVoltage()),
                num(Tier.XV.getVoltage()), num(Tier.SV.getVoltage()), highMachines.toString()}))
                .add(BookEl.para(Lang.tr("sc.manual.energy.chargeslots", slots.toString())))
                .addAll(paras("sc.manual.energy.storage2")).addAll(paras("sc.manual.energy.storagesv"));
        // the two storage-only modules: the Output Splitter and the Adaptive Transformer
        ItemStack splitter = ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.OUTPUT_SPLITTER);
        ItemStack adaptive = ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.ADAPTIVE_TRANSFORMER);
        st.add(BookEl.link("storagemods", Lang.tr("sc.manual.energy.storagemodshead")))
                .about(new ItemStack(ModBlocks.energyStorageSC, 1, OreDictionary.WILDCARD_VALUE));
        list.add(st);
        // К8: the storage-only modules - their own article
        list.add(new BookEntry("storagemods", c, splitter, Lang.tr("sc.manual.energy.storagemodshead"))
                .add(BookEl.title(Lang.tr("sc.manual.energy.storagemodshead")))
                .add(BookEl.item(splitter, splitter.getDisplayName(), Lang.tr("sc.manual.energy.storagemod.splitter")))
                .add(BookEl.item(adaptive, adaptive.getDisplayName(), Lang.tr("sc.manual.energy.storagemod.adaptive")))
                .addAll(paras("sc.manual.energy.storagemods")).add(BookEl.link("storage", Lang.tr("sc.manual.energy.storagehead")))
                .add(BookEl.link("modules", Lang.tr("sc.book.modules.title"))));
        list.add(converter(c));
        StringBuilder padSuits = new StringBuilder();         // "Nano Suit from MV, ... Singular Suit from SV" from ArmorSuit.chargeTier
        for (ArmorSuit suit : ArmorSuit.values()) {
            padSuits.append(padSuits.length() == 0 ? "" : ", ").append(Lang.tr("sc.book.suitfrom",
                    Lang.tr("sc.suit." + suit.name().toLowerCase(Locale.ROOT)), suit.chargeTier.name()));
        }
        list.add(simple("chargepad", c, new ItemStack(ModBlocks.chargePadSC, 1, 0), "sc.manual.energy.padhead")
                .addAll(paras("sc.manual.energy.pad", new Object[]{padSuits.toString()}))
                .about(new ItemStack(ModBlocks.chargePadSC, 1, OreDictionary.WILDCARD_VALUE)));
        BookEntry tr = new BookEntry("transformers", c, new ItemStack(ModBlocks.transformerSC, 1, 0), Lang.tr("sc.manual.energy.transformerhead"));
        tr.add(BookEl.title(Lang.tr("sc.manual.energy.transformerhead")));
        for (int meta = 0; meta < com.sc.block.BlockTransformerSC.VARIANTS; meta++) {
            Tier low = Tier.values()[meta], high = Tier.values()[meta + 1];
            ItemStack s = new ItemStack(ModBlocks.transformerSC, 1, meta);
            tr.add(BookEl.item(s, s.getDisplayName(), Lang.tr("sc.manual.energy.transformerline", high.getVoltage(), low.getVoltage(),
                    low.getVoltage(), high.getVoltage())));
        }
        tr.addAll(paras("sc.manual.energy.transformer")).addAll(paras("sc.manual.energy.transformersv"))
                .about(new ItemStack(ModBlocks.transformerSC, 1, OreDictionary.WILDCARD_VALUE));
        list.add(tr);
        list.add(svTier(c));
        BookEntry pipes = new BookEntry("pipes", c, new ItemStack(ModBlocks.pipeSC, 1, 0), Lang.tr("sc.manual.energy.pipes"));
        pipes.add(BookEl.title(Lang.tr("sc.manual.energy.pipes")));
        for (PipeType type : PipeType.values()) {
            ItemStack s = new ItemStack(ModBlocks.pipeSC, 1, type.ordinal());
            pipes.add(BookEl.item(s, s.getDisplayName(), type.throughput + " " + mb() + "/t" + (type.chemicallyResistant ? ", " + Lang.tr("sc.manual.energy.corrosionsafe") : "")));
        }
        pipes.addAll(paras("sc.manual.energy.piperules")).about(new ItemStack(ModBlocks.pipeSC, 1, OreDictionary.WILDCARD_VALUE));
        list.add(pipes);
        BookEntry tanks = new BookEntry("tanks", c, new ItemStack(ModBlocks.tankSC, 1, 0), Lang.tr("sc.manual.energy.tankhead"));
        tanks.add(BookEl.title(Lang.tr("sc.manual.energy.tankhead")));
        for (int tier = 0; tier < com.sc.block.BlockTankSC.TIER_NAMES.length; tier++) {
            ItemStack s = new ItemStack(ModBlocks.tankSC, 1, tier);
            tanks.add(BookEl.item(s, s.getDisplayName(), Lang.tr("sc.manual.energy.tankline", com.sc.tileentity.TileEntityTankSC.capacityOf(tier))));
        }
        tanks.addAll(paras("sc.manual.energy.tank")).about(new ItemStack(ModBlocks.tankSC, 1, OreDictionary.WILDCARD_VALUE));
        list.add(tanks);
        ItemStack tube = new ItemStack(ModBlocks.tubeItemPneumatic);
        list.add(new BookEntry("tube", c, tube, tube.getDisplayName()).add(BookEl.title(tube.getDisplayName())).addAll(paras("sc.manual.energy.tube"))
                .about(new ItemStack(ModBlocks.tubeItemPneumatic, 1, OreDictionary.WILDCARD_VALUE), new ItemStack(ModItems.itemFilter),
                        new ItemStack(ModItems.tubeSpeedUpgrade)));
        list.add(simple("power", c, new ItemStack(Blocks.lever), "sc.manual.energy.powerhead", "sc.manual.energy.power"));
        BookEntry bat = new BookEntry("batteries", c, new ItemStack(ModItems.battery, 1, 0), Lang.tr("sc.manual.energy.batteryhead"));
        bat.add(BookEl.title(Lang.tr("sc.manual.energy.batteryhead")));
        for (int t = 0; t < com.sc.item.ItemBatterySC.KEYS.length; t++) {
            ItemStack s = new ItemStack(ModItems.battery, 1, t);
            bat.add(BookEl.item(s, s.getDisplayName(), Lang.tr("sc.manual.energy.batteryline", com.sc.item.ItemBatterySC.TIERS[t].name(),
                    String.valueOf(com.sc.item.ItemBatterySC.capacity(t)), com.sc.item.ItemBatterySC.RATE[t])));
        }
        bat.addAll(paras("sc.manual.energy.battery")).add(BookEl.head(Lang.tr("sc.manual.energy.slothead"))).addAll(paras("sc.manual.energy.slot"))
                .about(new ItemStack(ModItems.battery, 1, OreDictionary.WILDCARD_VALUE));
        list.add(bat);
        List<ItemStack> buckets = new ArrayList<ItemStack>();
        for (int i = 0; i < com.sc.item.ItemFluidBucketSC.FLUIDS.length; i++) {
            buckets.add(new ItemStack(ModItems.fluidBucket, 1, i));
        }
        list.add(new BookEntry("fluids", c, buckets.get(0), Lang.tr("sc.manual.energy.fluidshead")).add(BookEl.title(Lang.tr("sc.manual.energy.fluidshead")))
                .add(BookEl.para(Lang.tr("sc.manual.energy.buckets", buckets.size()))).add(BookEl.items(buckets))
                .addAll(paras("sc.manual.energy.fluids", null, new Object[]{num(com.sc.machine.UpgradeType.TANK_PER_UPGRADE),
                        com.sc.machine.UpgradeType.MAX_TANK_UPGRADES, com.sc.machine.UpgradeType.CLEAR_MB_PER_EU}))
                .about(new ItemStack(ModItems.fluidBucket, 1, OreDictionary.WILDCARD_VALUE)));
        BookEl wl = BookEl.table(Lang.tr("sc.book.t.tier"), Lang.tr("sc.book.t.range"), "EU/t", Lang.tr("sc.book.t.loss"));
        for (Tier tier : Tier.values()) {
            int range = com.sc.tileentity.TileEntityWirelessSC.range(tier);
            wl.row(new ItemStack(ModBlocks.wirelessTx, 1, tier.ordinal()), tier.name(), range == Integer.MAX_VALUE ? Lang.tr("sc.manual.energy.wirelessall")
                    : String.valueOf(range), String.valueOf(tier.getVoltage()), "1% / " + com.sc.tileentity.TileEntityWirelessSC.blocksPerPercent(tier));
        }
        list.add(new BookEntry("wireless", c, new ItemStack(ModBlocks.wirelessTx, 1, 0), Lang.tr("sc.manual.energy.wirelesshead"))
                .add(BookEl.title(Lang.tr("sc.manual.energy.wirelesshead"))).add(wl).addAll(paras("sc.manual.energy.wireless"))
                .about(new ItemStack(ModBlocks.wirelessTx, 1, OreDictionary.WILDCARD_VALUE), new ItemStack(ModBlocks.wirelessRx, 1, OreDictionary.WILDCARD_VALUE),
                        new ItemStack(ModItems.linkCard)));
        list.add(simple("quantum", c, new ItemStack(ModBlocks.quantumTranslator), "sc.manual.energy.quantumhead", "sc.manual.energy.quantum")
                .about(new ItemStack(ModBlocks.quantumTranslator, 1, OreDictionary.WILDCARD_VALUE), new ItemStack(ModItems.entangledCrystal, 1, OreDictionary.WILDCARD_VALUE)));
    }

    /**
     * The Energy Converter: the pairs and their rates (the config's - ForeignEnergySC), the buffers, the
     * throughput, the faces, the modules (each with the slot's limit), the recipes, the other mods.
     */
    private static BookEntry converter(BookChapter c) {
        com.sc.energy.ForeignEnergySC.Kind[] kinds = com.sc.energy.ForeignEnergySC.Kind.values();
        ItemStack conv = new ItemStack(ModBlocks.energyConverter);
        BookEntry e = new BookEntry("converter", c, conv, Lang.tr("sc.manual.energy.convhead"));
        Tier base = com.sc.energy.ForeignEnergySC.BASE_TIER;
        int loss = com.sc.energy.ForeignEnergySC.lossPercent(0);
        e.add(BookEl.title(Lang.tr("sc.manual.energy.convhead")))
                .addAll(paras("sc.manual.energy.conv", null,
                        new Object[]{num(com.sc.energy.ForeignEnergySC.BASE_EU_BUFFER), String.valueOf(loss)},
                        new Object[]{base.name(), num(base.getVoltage())}));
        BookEl t = BookEl.table(Lang.tr("sc.manual.energy.conv.t.energy"), Lang.tr("sc.manual.energy.conv.t.rate"),
                Lang.tr("sc.manual.energy.conv.t.buffer"), Lang.tr("sc.manual.energy.conv.t.need"));
        ItemStack[] icons = {null, ModItems.converterModule.stackOf(com.sc.item.ItemConverterModuleSC.Kind.CARD_MEKANISM),
                ModItems.converterModule.stackOf(com.sc.item.ItemConverterModuleSC.Kind.CARD_GALACTICRAFT)};
        for (com.sc.energy.ForeignEnergySC.Kind k : kinds) {
            t.row(icons[k.ordinal()], k.unit, "1 EU = " + com.sc.block.ItemBlockEnergyConverterSC.trimRate(k.perEu()) + " " + k.unit,
                    num(Math.round(com.sc.energy.ForeignEnergySC.BASE_EU_BUFFER * k.perEu())) + " " + k.unit,
                    Lang.tr("sc.manual.energy.conv.need." + k.name().toLowerCase(Locale.ROOT)));
        }
        e.add(BookEl.head(Lang.tr("sc.manual.energy.conv.pairshead"))).add(t).addAll(paras("sc.manual.energy.convpair"));
        e.add(BookEl.head(Lang.tr("sc.manual.energy.conv.faceshead"))).addAll(paras("sc.manual.energy.convfaces"));
        e.add(BookEl.head(Lang.tr("sc.manual.energy.conv.modhead")));
        ItemStack transformer = ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.TRANSFORMER);
        ItemStack universal = ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.UNIVERSAL_TRANSFORMER);
        ItemStack storage = ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.ENERGY_STORAGE);
        e.add(BookEl.item(transformer, transformer.getDisplayName(), Lang.tr("sc.manual.energy.convmod.transformer",
                com.sc.energy.ForeignEnergySC.MAX_TRANSFORMERS, com.sc.energy.ForeignEnergySC.euTier(com.sc.energy.ForeignEnergySC.MAX_TRANSFORMERS).name())));
        e.add(BookEl.item(universal, universal.getDisplayName(), Lang.tr("sc.manual.energy.convmod.universal", Tier.SV.name())));
        e.add(BookEl.item(storage, storage.getDisplayName(), Lang.tr("sc.manual.energy.convmod.storage", com.sc.energy.ForeignEnergySC.MAX_STORAGE,
                num(com.sc.energy.ForeignEnergySC.euCapacity(com.sc.energy.ForeignEnergySC.MAX_STORAGE)))));
        for (com.sc.item.ItemConverterModuleSC.Kind k : com.sc.item.ItemConverterModuleSC.Kind.values()) {
            ItemStack s = ModItems.converterModule.stackOf(k);
            String line = Lang.tr("sc.conv.module.details." + k.key());
            if (k.max > 1) {
                line += " " + Lang.tr("sc.conv.module.max", k.max);
            }
            e.add(BookEl.item(s, s.getDisplayName(), line));
        }
        e.add(BookEl.head(Lang.tr("sc.manual.energy.conv.extrahead"))).addAll(paras("sc.manual.energy.convextra"));
        if (!com.sc.energy.ForeignEnergySC.anyPresent()) {
            e.add(BookEl.warn(Lang.tr("sc.conv.tooltip.noforeign")));
        }
        crafting(e, conv);
        for (com.sc.item.ItemConverterModuleSC.Kind k : com.sc.item.ItemConverterModuleSC.Kind.values()) {
            crafting(e, ModItems.converterModule.stackOf(k));
        }
        e.add(BookEl.link("compat", Lang.tr("sc.book.compat.title"))).add(BookEl.link("modules", Lang.tr("sc.book.modules.title")))
                .add(BookEl.link("storage", Lang.tr("sc.manual.energy.storagehead")));
        e.about(conv, new ItemStack(ModItems.converterModule, 1, OreDictionary.WILDCARD_VALUE));
        return e;
    }

    /** The SV tier (Singular, 131 072 EU/t): what it is, how to get it, its blocks, IC2. */
    private static BookEntry svTier(BookChapter c) {
        Tier sv = Tier.SV;
        ItemStack store = new ItemStack(ModBlocks.energyStorageSC, 1, sv.ordinal());
        ItemStack tokamak = ModBlocks.generatorStack(GeneratorType.TOKAMAK_XV, 1);
        ItemStack trans = new ItemStack(ModBlocks.transformerSC, 1, Tier.XV.ordinal());       // by its low tier: XV-SV
        ItemStack adaptive = ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.ADAPTIVE_TRANSFORMER);
        ItemStack cable = new ItemStack(ModBlocks.cableSC, 1, CableType.SINGULAR.ordinal());
        ItemStack pad = new ItemStack(ModBlocks.chargePadSC, 1, sv.ordinal());
        ItemStack tx = new ItemStack(ModBlocks.wirelessTx, 1, sv.ordinal());
        int core = java.util.Arrays.asList(com.sc.item.ItemBatterySC.TIERS).indexOf(sv);
        int range = com.sc.tileentity.TileEntityWirelessSC.range(sv);
        BookEntry e = new BookEntry("sv", c, store, Lang.tr("sc.manual.energy.svhead"));
        e.add(BookEl.title(Lang.tr("sc.manual.energy.svhead")))
                .add(BookEl.para(Lang.tr("sc.manual.energy.svlead", sv.getVoltage(), Tier.XV.getVoltage(), sv.toIc2Tier())))
                .addAll(paras("sc.manual.energy.svtext"))
                .add(BookEl.head(Lang.tr("sc.manual.energy.svgethead")))
                .add(BookEl.item(tokamak, tokamak.getDisplayName(), Lang.tr("sc.manual.energy.svget.tokamak", GeneratorType.TOKAMAK_XV.euPerTick)))
                .add(BookEl.item(ModBlocks.generatorStack(GeneratorType.SINGULAR_REACTOR, 1), GeneratorType.SINGULAR_REACTOR.localizedName(),
                        Lang.tr("sc.manual.energy.svget.singular", GeneratorType.SINGULAR_REACTOR.euPerTick,
                                com.sc.tileentity.SingularReactorSC.MAX_OUTPUT)))
                .add(BookEl.item(trans, trans.getDisplayName(), Lang.tr("sc.manual.energy.svget.transformer")))
                .add(BookEl.item(adaptive, adaptive.getDisplayName(), Lang.tr("sc.manual.energy.svget.adaptive")))
                .add(BookEl.head(Lang.tr("sc.manual.energy.svblockshead")))
                .add(BookEl.item(cable, cable.getDisplayName(), Lang.tr("sc.manual.energy.svblock.cable", CableType.SINGULAR.maxThroughput())))
                .add(BookEl.item(store, store.getDisplayName(), Lang.tr("sc.manual.energy.svblock.storage",
                        String.valueOf(com.sc.tileentity.TileEntityEnergyStorageSC.capacityOf(sv)))))
                .add(BookEl.item(pad, pad.getDisplayName(), Lang.tr("sc.manual.energy.svblock.pad")))
                .add(BookEl.item(tx, tx.getDisplayName(), Lang.tr("sc.manual.energy.svblock.wireless",
                        range == Integer.MAX_VALUE ? Lang.tr("sc.manual.energy.wirelessall") : String.valueOf(range))));
        if (core >= 0) {
            ItemStack bat = new ItemStack(ModItems.battery, 1, core);
            e.add(BookEl.item(bat, bat.getDisplayName(), Lang.tr("sc.manual.energy.svblock.battery",
                    String.valueOf(com.sc.item.ItemBatterySC.capacity(core)), com.sc.item.ItemBatterySC.RATE[core])));
        }
        e.add(BookEl.gap()).add(BookEl.warn(Lang.tr("sc.manual.energy.svcompathead"))).addAll(paras("sc.manual.energy.svcompat"))
                .about(trans);
        return e;
    }

    // ------------------------------------------------------------------ 7. armour & weapons

    private static void armor(List<BookEntry> list) {
        BookChapter c = BookChapter.ARMOR;
        BookEntry suits = new BookEntry("suits", c, new ItemStack(ModItems.ARMOR.get(ArmorSuit.values()[0])[1]), Lang.tr("sc.book.armor.suits"));
        suits.add(BookEl.title(Lang.tr("sc.manual.armor.title"))).addAll(paras("sc.manual.armor.suits"));
        for (ArmorSuit suit : ArmorSuit.values()) {
            int points = 0;
            for (int i = 0; i < 4; i++) {
                points += suit.material.getDamageReductionAmount(i);
            }
            ItemStack chest = new ItemStack(ModItems.ARMOR.get(suit)[1]);
            suits.add(BookEl.item(chest, Lang.tr("sc.suit." + suit.name().toLowerCase(Locale.ROOT)),
                    Lang.tr("sc.manual.armor.suitline", points, Math.min(100, points * 4), suit.heatCapacity, suit.heatDissipation)));
            for (com.sc.item.ItemArmorSC piece : ModItems.ARMOR.get(suit)) {
                suits.about(new ItemStack(piece, 1, OreDictionary.WILDCARD_VALUE));
            }
        }
        suits.addAll(paras("sc.manual.armor.upgrade")).add(BookEl.head(Lang.tr("sc.manual.armor.energyhead"))).addAll(paras("sc.manual.armor.energy"));
        for (ArmorSuit suit : ArmorSuit.values()) {
            suits.add(BookEl.item(null, Lang.tr("sc.suit." + suit.name().toLowerCase(Locale.ROOT)),
                    Lang.tr("sc.manual.armor.energyline", suit.maxCharge, suit.euPerDamage, suit.chargeTier.name())));
        }
        list.add(suits);
        BookEntry chips = new BookEntry("chips", c, ModItems.armorChip.stackOf(ChipType.values()[0], 1), Lang.tr("sc.manual.armor.chipshead"));
        chips.add(BookEl.title(Lang.tr("sc.manual.armor.chipshead"))).addAll(paras("sc.manual.armor.chips"));
        for (ChipType type : ChipType.values()) {
            chips.add(BookEl.item(ModItems.armorChip.stackOf(type, 1), Lang.trOr("sc.chip." + type.name().toLowerCase(Locale.ROOT), type.name()),
                    Lang.tr("sc.manual.armor.chip." + type.name().toLowerCase(Locale.ROOT))));
        }
        chips.addAll(paras("sc.manual.armor.effects")).about(new ItemStack(ModItems.armorChip, 1, OreDictionary.WILDCARD_VALUE));
        list.add(chips);
        BookEntry fn = new BookEntry("armorfn", c, new ItemStack(ModItems.ARMOR.get(ArmorSuit.values()[ArmorSuit.values().length - 1])[0]),
                Lang.tr("sc.manual.armor.fnhead"));
        fn.add(BookEl.title(Lang.tr("sc.manual.armor.fnhead"))).addAll(paras("sc.manual.armor.fn"));
        for (com.sc.util.ArmorFeature f : com.sc.util.ArmorFeature.values()) {
            String key = "sc.armorfn." + f.name().toLowerCase(Locale.ROOT);
            fn.add(BookEl.item(null, Lang.tr(key), "(" + Lang.tr("sc.suit." + f.minSuit.name().toLowerCase(Locale.ROOT)) + "+, "
                    + Lang.tr("sc.armorhud.piece." + f.piece) + ") " + Lang.tr(key + ".desc")));
        }
        fn.add(BookEl.head(Lang.tr("sc.manual.armor.sethead")));
        for (ArmorSuit suit : ArmorSuit.values()) {
            fn.add(BookEl.dim(Lang.tr("sc.armorgui.set." + suit.name().toLowerCase(Locale.ROOT))));
        }
        list.add(fn);
        armorGases(list, c);
        singularArmor(list, c);
        singularStation(list, c);
        BookEntry weapons = new BookEntry("weapons", c, new ItemStack(ModItems.WEAPONS.get(WeaponType.values()[0])), Lang.tr("sc.manual.armor.weapons"));
        weapons.add(BookEl.title(Lang.tr("sc.manual.armor.weapons")));
        for (WeaponType type : WeaponType.values()) {
            ItemStack s = new ItemStack(ModItems.WEAPONS.get(type));
            weapons.add(BookEl.item(s, s.getDisplayName(), Lang.tr("sc.manual.armor.weaponline", type.damagePerHit, type.shotsPerUse, type.euPerShot,
                    type.range, type.tier.getBuffer()))).about(new ItemStack(s.getItem(), 1, OreDictionary.WILDCARD_VALUE));
        }
        weapons.addAll(paras("sc.manual.armor.charge"));
        list.add(weapons);
        BookEntry blades = new BookEntry("blades", c, new ItemStack(ModItems.BLADES.get(com.sc.util.BladeType.values()[0])), Lang.tr("sc.manual.armor.bladeshead"));
        blades.add(BookEl.title(Lang.tr("sc.manual.armor.bladeshead"))).addAll(paras("sc.manual.armor.blades"));
        for (com.sc.util.BladeType type : com.sc.util.BladeType.values()) {
            ItemStack s = new ItemStack(ModItems.BLADES.get(type));
            blades.add(BookEl.item(s, s.getDisplayName(), Lang.tr("sc.manual.armor.bladeline", type.offDamage, type.onDamage, type.euPerHit,
                    type.idlePerSecond, type.maxCharge, type.chargeTier.name(), type.heatCapacity, type.heatDissipation)));
            if (type != com.sc.util.BladeType.SINGULAR) {         // G on the Singular blade: its own article
                blades.about(new ItemStack(s.getItem(), 1, OreDictionary.WILDCARD_VALUE));
            }
        }
        for (com.sc.util.BladeFeature f : com.sc.util.BladeFeature.values()) {
            if (f.isSingular()) {
                continue;                                         // the Singular blade's own: in its article
            }
            blades.add(BookEl.item(null, Lang.tr("sc.bladefn." + f.key()), "(" + new ItemStack(ModItems.BLADES.get(f.minType)).getDisplayName() + "+) "
                    + Lang.tr("sc.bladefn." + f.key() + ".desc")));
        }
        for (com.sc.util.BladeType type : com.sc.util.BladeType.values()) {
            blades.add(BookEl.dim(Lang.tr("sc.bladegui.set." + type.key())));
        }
        blades.add(BookEl.link("singularblade", new ItemStack(ModItems.BLADES.get(com.sc.util.BladeType.SINGULAR)).getDisplayName()));
        list.add(blades);
        singularBlade(list, c);
        BookEntry drills = new BookEntry("drills", c, new ItemStack(ModItems.DRILLS.get(com.sc.util.DrillType.values()[0])), Lang.tr("sc.manual.armor.drillshead"));
        drills.add(BookEl.title(Lang.tr("sc.manual.armor.drillshead"))).addAll(paras("sc.manual.armor.drills"));
        for (com.sc.util.DrillType type : com.sc.util.DrillType.values()) {
            ItemStack s = new ItemStack(ModItems.DRILLS.get(type));
            drills.add(BookEl.item(s, s.getDisplayName(), Lang.tr("sc.manual.armor.drillline", type.euPerBlock, (int) type.speed, type.harvestLevel,
                    type.fortune, type.maxCharge, type.chargeTier.name())));
            if (type != com.sc.util.DrillType.SINGULAR) {         // G on the Singular drill: its own article
                drills.about(new ItemStack(s.getItem(), 1, OreDictionary.WILDCARD_VALUE));
            }
        }
        for (com.sc.util.DrillFeature f : com.sc.util.DrillFeature.values()) {
            if (f.singular()) {
                continue;                                         // the Singular drill's own: in its article
            }
            drills.add(BookEl.item(null, Lang.tr("sc.drillfn." + f.key()), "(" + new ItemStack(ModItems.DRILLS.get(f.minType)).getDisplayName() + "+) "
                    + Lang.tr("sc.drillfn." + f.key() + ".desc")));
        }
        for (com.sc.util.DrillType type : com.sc.util.DrillType.values()) {
            drills.add(BookEl.dim(Lang.tr("sc.drillgui.set." + type.key())));
        }
        drills.add(BookEl.link("singulardrill", new ItemStack(ModItems.DRILLS.get(com.sc.util.DrillType.SINGULAR)).getDisplayName()));
        list.add(drills);
        singularDrill(list, c);
        singularCrumbs(list, c);
    }

    // ------------------------------------------------------------------ the Singular blade and drill (docs/plan-singular-tools.md)

    private static String shortEu(long eu) {
        return com.sc.util.SingularStationMath.shortAmount(eu, Lang.tr("sc.singStation.unit.k"), Lang.tr("sc.singStation.unit.m"),
                Lang.tr("sc.singStation.unit.b"));
    }

    /** The share (percent) of the whole set's modernisation row a Singular tool pays (TOOL_BLADE / TOOL_DRILL). */
    private static long toolShare(int tool) {
        return Math.round(100.0 * com.sc.util.SingularStationMath.toolModerniseCost(tool, 1, false)[0]
                / Math.max(1L, com.sc.util.SingularStationMath.row(1)[0]));
    }

    /** The conversion card of an Exo tool (TOOL_BLADE / TOOL_DRILL): Exo -> (the station) -> Singular, materials, EU / gases, time. */
    private static void toolConvertCard(BookEntry e, int tool) {
        boolean blade = tool == com.sc.util.SingularStationMath.TOOL_BLADE;
        ItemStack exo = blade ? new ItemStack(ModItems.BLADES.get(com.sc.util.BladeType.EXO)) : new ItemStack(ModItems.DRILLS.get(com.sc.util.DrillType.EXO));
        ItemStack sing = blade ? new ItemStack(ModItems.BLADES.get(com.sc.util.BladeType.SINGULAR))
                : new ItemStack(ModItems.DRILLS.get(com.sc.util.DrillType.SINGULAR));
        e.add(BookEl.chain(listOf(exo, sing), listOf(new ItemStack(ModBlocks.singularStation))));
        int[] need = com.sc.util.SingularStationMath.toolConvertMaterials(tool);
        List<ItemStack> mats = new ArrayList<ItemStack>();
        for (int k = 0; k < need.length; k++) {
            if (need[k] > 0) {
                mats.add(com.sc.tileentity.TileEntitySingularStationSC.materialStack(k, need[k]));
            }
        }
        e.add(BookEl.items(mats));
        long[] cost = com.sc.util.SingularStationMath.toolConvertCost(tool);
        e.add(BookEl.dim(Lang.tr("sc.manual.singstation.convrow", sing.getDisplayName(), shortEu(cost[0]), cost[1], cost[2], cost[3],
                com.sc.util.SingularStationMath.toolConvertTicks(tool) / com.sc.util.SingularStationMath.TICKS_PER_MINUTE)));
    }

    /** The modernisation rows of a Singular tool: N -> N+1 its share of the set's row, the time. */
    private static void toolModerniseRows(BookEntry e, int tool) {
        e.add(BookEl.para(Lang.tr("sc.manual.singtool.modernise", toolShare(tool))));
        for (int lvl = com.sc.util.ToolLevelSC.MIN; lvl < com.sc.util.ToolLevelSC.MAX; lvl++) {
            long[] r = com.sc.util.SingularStationMath.toolModerniseCost(tool, lvl, false);
            e.add(BookEl.dim(Lang.tr("sc.manual.singstation.row", lvl, lvl + 1, shortEu(r[0]), r[1], r[2], r[3], r[4],
                    com.sc.util.SingularStationMath.toolModerniseTicks(tool, lvl) / com.sc.util.SingularStationMath.TICKS_PER_MINUTE)));
        }
    }

    /** The level table of a Singular tool: points to the next level, what each level opens (functions, forms, zone, branch). */
    private static BookEl toolLevels(boolean blade) {
        BookEl t = BookEl.table(Lang.tr("sc.manual.singtool.t.level"), Lang.tr("sc.manual.singtool.t.points"), Lang.tr("sc.manual.singtool.t.opens"));
        for (int lv = com.sc.util.ToolLevelSC.MIN; lv <= com.sc.util.ToolLevelSC.MAX; lv++) {
            StringBuilder opens = new StringBuilder();
            if (blade) {
                for (com.sc.util.BladeForm f : com.sc.util.BladeForm.values()) {
                    if (f.level == lv) {
                        opens.append(opens.length() > 0 ? ", " : "").append(Lang.tr(f.langKey()))
                                .append(f.branch != 0 ? " (" + Lang.tr("sc.toolbranch.blade." + f.branch) + ")" : "");
                    }
                }
                for (com.sc.util.BladeFeature f : com.sc.util.BladeFeature.values()) {
                    if (f.isSingular() && f.singLevel() == lv && f != com.sc.util.BladeFeature.FORM_ATTACK) {
                        opens.append(opens.length() > 0 ? ", " : "").append(Lang.tr("sc.bladefn." + f.key()));
                    }
                }
            } else {
                for (com.sc.util.DrillFeature f : com.sc.util.DrillFeature.values()) {
                    if (f.singular() && f.singLevel() == lv) {
                        opens.append(opens.length() > 0 ? ", " : "").append(Lang.tr("sc.drillfn." + f.key()));
                    }
                }
                int h = com.sc.util.DrillZoneSC.maxHole(lv);
                if (lv == com.sc.util.ToolLevelSC.MIN || h > com.sc.util.DrillZoneSC.maxHole(lv - 1)) {
                    opens.append(opens.length() > 0 ? ", " : "").append(Lang.tr("sc.toolgui.hole.size", h, h));
                }
            }
            if (lv == com.sc.util.ToolLevelSC.BRANCH_LEVEL || lv == com.sc.util.ToolLevelSC.BRANCH_PERK_LEVEL) {
                opens.append(opens.length() > 0 ? "; " : "").append(Lang.tr(lv == com.sc.util.ToolLevelSC.BRANCH_LEVEL
                        ? "sc.manual.singtool.t.branch" : "sc.manual.singtool.t.perk"));
            }
            int need = com.sc.util.ToolLevelSC.threshold(blade, lv);
            t.row(null, String.valueOf(lv), need > 0 ? num(need) : "-", opens.length() > 0 ? opens.toString() : "-");
        }
        return t;
    }

    /** The Singular blade: what it is, how to get it, levels, forms, functions, branches, gases and the set, keys. */
    private static void singularBlade(List<BookEntry> list, BookChapter c) {
        com.sc.util.BladeType type = com.sc.util.BladeType.SINGULAR;
        ItemStack s = new ItemStack(ModItems.BLADES.get(type));
        int tool = com.sc.util.SingularStationMath.TOOL_BLADE;
        BookEntry e = new BookEntry("singularblade", c, s, s.getDisplayName());
        e.add(BookEl.title(s.getDisplayName())).add(BookEl.item(s, s.getDisplayName(), Lang.tr("sc.manual.armor.bladeline", type.offDamage,
                type.onDamage, type.euPerHit, type.idlePerSecond, type.maxCharge, type.chargeTier.name(), type.heatCapacity, type.heatDissipation)));
        e.addAll(paras("sc.manual.singblade.intro", null, new Object[]{Math.round((1F - com.sc.util.ToolLevelSC.LEGACY_MUL) * 100)}));
        // how to get it: the Exo blade converted in the Singular station
        e.add(BookEl.head(Lang.tr("sc.manual.singtool.gethead"))).addAll(paras("sc.manual.singtool.get"));
        toolConvertCard(e, tool);
        // levels: points, the table, the station's price
        e.add(BookEl.head(Lang.tr("sc.manual.singtool.levelhead"))).addAll(paras("sc.manual.singblade.level"));
        e.add(toolLevels(true));
        toolModerniseRows(e, tool);
        // forms (Shift + wheel) and their special attacks
        e.add(BookEl.head(Lang.tr("sc.manual.singblade.formshead"))).addAll(paras("sc.manual.singblade.forms"));
        for (com.sc.util.BladeForm f : com.sc.util.BladeForm.values()) {
            String name = Lang.tr("sc.toolgui.lv", Lang.tr(f.langKey()), f.level) + (f.branch != 0 ? ", " + Lang.tr("sc.toolbranch.blade." + f.branch) : "");
            String attack = com.sc.util.BladeFeature.formAttackKey(f);
            e.add(BookEl.item(null, name, Lang.tr("sc.manual.singblade.formline", Lang.tr(f.langKey() + ".desc"), Lang.tr(attack),
                    Lang.tr(attack + ".desc"))));
        }
        // its own functions, by level
        e.add(BookEl.head(Lang.tr("sc.manual.singtool.fnhead"))).addAll(paras("sc.manual.singblade.fn"));
        for (com.sc.util.BladeFeature f : com.sc.util.BladeFeature.values()) {
            if (f.isSingular()) {
                e.add(BookEl.item(null, Lang.tr("sc.bladefn." + f.key()), Lang.tr("sc.manual.singtool.fnline", f.singLevel(),
                        Lang.tr("sc.bladefn." + f.key() + ".desc"))));
            }
        }
        // branches: chosen at level 3, the same choice's perk at 5
        e.add(BookEl.head(Lang.tr("sc.manual.singtool.branchhead"))).addAll(paras("sc.manual.singtool.branch",
                new Object[]{com.sc.util.ToolLevelSC.BRANCH_LEVEL, com.sc.util.ToolLevelSC.BRANCH_PERK_LEVEL, com.sc.util.SingularStationMath.BRANCH_SM}));
        for (int b = 1; b <= com.sc.util.ToolLevelSC.branchCount(true); b++) {
            e.add(BookEl.item(null, Lang.tr("sc.toolbranch.blade." + b), Lang.tr("sc.singStation.toolbranch.blade." + b + ".hint")));
        }
        e.add(BookEl.dim(Lang.tr("sc.manual.singblade.lastchance", com.sc.util.BladeFeature.LAST_CHANCE_COOLDOWN / 1200,
                com.sc.util.BladeFeature.LAST_CHANCE_SM)));
        // gases from the worn armour, the full set
        e.add(BookEl.head(Lang.tr("sc.manual.singtool.gashead"))).addAll(paras("sc.manual.singtool.gas", null,
                new Object[]{Math.round((1F - com.sc.util.ToolLevelSC.SET_COOLDOWN_MUL) * 100)}));
        e.add(BookEl.dim(Lang.tr("sc.bladegui.set.singular")));
        e.add(BookEl.head(Lang.tr("sc.manual.singtool.keyshead"))).addAll(paras("sc.manual.singblade.keys"));
        e.add(BookEl.link("singularstation", new ItemStack(ModBlocks.singularStation).getDisplayName()))
                .add(BookEl.link("singulararmor", Lang.tr("sc.manual.singular.head"))).add(BookEl.link("blades", Lang.tr("sc.manual.armor.bladeshead")))
                .add(BookEl.link("keys", Lang.tr("sc.book.keys.title")));
        e.about(new ItemStack(s.getItem(), 1, OreDictionary.WILDCARD_VALUE));
        list.add(e);
    }

    /** The Singular drill: what it is, how to get it, levels, functions, the black hole and its crumbs, branches. */
    private static void singularDrill(List<BookEntry> list, BookChapter c) {
        com.sc.util.DrillType type = com.sc.util.DrillType.SINGULAR;
        ItemStack s = new ItemStack(ModItems.DRILLS.get(type));
        int tool = com.sc.util.SingularStationMath.TOOL_DRILL;
        BookEntry e = new BookEntry("singulardrill", c, s, s.getDisplayName());
        e.add(BookEl.title(s.getDisplayName())).add(BookEl.item(s, s.getDisplayName(), Lang.tr("sc.manual.armor.drillline", type.euPerBlock,
                (int) type.speed, type.harvestLevel, type.fortune, type.maxCharge, type.chargeTier.name())));
        e.addAll(paras("sc.manual.singdrill.intro", null, new Object[]{Math.round((1F - com.sc.util.ToolLevelSC.LEGACY_MUL) * 100)}));
        e.add(BookEl.head(Lang.tr("sc.manual.singtool.gethead"))).addAll(paras("sc.manual.singtool.get"));
        toolConvertCard(e, tool);
        e.add(BookEl.head(Lang.tr("sc.manual.singtool.levelhead"))).addAll(paras("sc.manual.singdrill.level",
                new Object[]{com.sc.util.DrillFeature.BLOCKS_PER_POINT, com.sc.item.ItemSingularCrumbSC.CRUMB_POINTS}));
        e.add(toolLevels(false));
        toolModerniseRows(e, tool);
        e.add(BookEl.dim(Lang.tr("sc.manual.singdrill.crumbpay", com.sc.util.SingularStationMath.CRUMB_SM, com.sc.util.SingularStationMath.CRUMB_MAX_PERCENT)));
        // its own functions, by level
        e.add(BookEl.head(Lang.tr("sc.manual.singtool.fnhead")));
        for (com.sc.util.DrillFeature f : com.sc.util.DrillFeature.values()) {
            if (f.singular()) {
                e.add(BookEl.item(null, Lang.tr("sc.drillfn." + f.key()), Lang.tr("sc.manual.singtool.fnline", f.singLevel(),
                        Lang.tr("sc.drillfn." + f.key() + ".desc"))));
            }
        }
        // the black hole mode: sizes, what it never touches, the cost, the crumbs
        e.add(BookEl.head(Lang.tr("sc.manual.singdrill.holehead"))).addAll(paras("sc.manual.singdrill.hole",
                new Object[]{com.sc.util.DrillZoneSC.maxHole(1), com.sc.util.DrillZoneSC.maxHole(3), com.sc.util.DrillZoneSC.maxHole(5),
                        com.sc.util.DrillZoneSC.HOLE_TUNNEL},
                null,
                new Object[]{Math.round(com.sc.util.DrillFeature.HOLE_EU_MUL * 100), com.sc.util.DrillFeature.HOLE_BLOCKS_PER_MB,
                        number(com.sc.util.DrillFeature.BLACK_HOLE.cooldownTicks() / 20F)},
                new Object[]{com.sc.item.ItemSingularCrumbSC.CRUMB_BLOCKS, com.sc.item.ItemSingularCrumbSC.ORE_MUL}));
        e.add(BookEl.link("singularcrumb", Lang.tr("sc.manual.singcrumb.head")));
        // branches
        e.add(BookEl.head(Lang.tr("sc.manual.singtool.branchhead"))).addAll(paras("sc.manual.singtool.branch",
                new Object[]{com.sc.util.ToolLevelSC.BRANCH_LEVEL, com.sc.util.ToolLevelSC.BRANCH_PERK_LEVEL, com.sc.util.SingularStationMath.BRANCH_SM}));
        for (int b = 1; b <= com.sc.util.ToolLevelSC.branchCount(false); b++) {
            e.add(BookEl.item(null, Lang.tr("sc.toolbranch.drill." + b), Lang.tr("sc.toolbranch.drill." + b + ".desc")));
        }
        e.add(BookEl.head(Lang.tr("sc.manual.singtool.gashead"))).addAll(paras("sc.manual.singtool.gas", null,
                new Object[]{Math.round((1F - com.sc.util.ToolLevelSC.SET_COOLDOWN_MUL) * 100)}));
        e.add(BookEl.dim(Lang.tr("sc.drillgui.set.singular")));
        e.add(BookEl.head(Lang.tr("sc.manual.singtool.keyshead"))).addAll(paras("sc.manual.singdrill.keys"));
        e.add(BookEl.link("singularstation", new ItemStack(ModBlocks.singularStation).getDisplayName()))
                .add(BookEl.link("singulararmor", Lang.tr("sc.manual.singular.head"))).add(BookEl.link("drills", Lang.tr("sc.manual.armor.drillshead")))
                .add(BookEl.link("keys", Lang.tr("sc.book.keys.title")));
        e.about(new ItemStack(s.getItem(), 1, OreDictionary.WILDCARD_VALUE));
        list.add(e);
    }

    /** Singularity crumbs and the clot: only from the black hole; points, the station, the compressor. */
    private static void singularCrumbs(List<BookEntry> list, BookChapter c) {
        ItemStack crumb = new ItemStack(ModItems.singularCrumb), clot = new ItemStack(ModItems.singularClot);
        BookEntry e = new BookEntry("singularcrumb", c, crumb, Lang.tr("sc.manual.singcrumb.head"));
        e.add(BookEl.title(Lang.tr("sc.manual.singcrumb.head"))).add(BookEl.items(listOf(crumb, clot)));
        e.addAll(paras("sc.manual.singcrumb", new Object[]{com.sc.item.ItemSingularCrumbSC.CRUMB_BLOCKS, com.sc.item.ItemSingularCrumbSC.ORE_MUL},
                new Object[]{com.sc.item.ItemSingularCrumbSC.CRUMB_POINTS},
                new Object[]{com.sc.util.SingularStationMath.CRUMB_SM, com.sc.util.SingularStationMath.CRUMB_MAX_PERCENT}));
        e.add(BookEl.head(clot.getDisplayName(), clot)).add(BookEl.para(Lang.tr("sc.manual.singclot", com.sc.item.ItemSingularClotSC.SM_PER_CLOT)));
        crafting(e, clot);
        ItemStack sm = com.sc.item.ItemSingularCellSC.filled(ModItems.singularCell, com.sc.item.ItemSingularClotSC.SM_PER_CLOT);
        e.add(BookEl.chain(listOf(clot, sm), listOf(machineStack(MachineType.MATTER_COMPRESSOR))));
        e.add(BookEl.link("singulardrill", new ItemStack(ModItems.DRILLS.get(com.sc.util.DrillType.SINGULAR)).getDisplayName()))
                .add(BookEl.link("machine." + MachineType.MATTER_COMPRESSOR.name().toLowerCase(Locale.ROOT), MachineType.MATTER_COMPRESSOR.localizedName()))
                .add(BookEl.link("singularstation", new ItemStack(ModBlocks.singularStation).getDisplayName()));
        e.about(new ItemStack(ModItems.singularCrumb, 1, OreDictionary.WILDCARD_VALUE), new ItemStack(ModItems.singularClot, 1, OreDictionary.WILDCARD_VALUE));
        list.add(e);
    }

    /** The Singular Service Station and the Gravitational Stabiliser (docs/plan-singular-armor.md §7). */
    private static void singularStation(List<BookEntry> list, BookChapter c) {
        ItemStack st = new ItemStack(ModBlocks.singularStation), stab = new ItemStack(ModBlocks.gravStabiliser);
        BookEntry e = new BookEntry("singularstation", c, st, st.getDisplayName());
        e.add(BookEl.title(st.getDisplayName())).add(BookEl.image("singular_station", 300, 117)).add(BookEl.items(listOf(st, stab)))
                .addAll(paras("sc.manual.singstation", new Object[]{com.sc.util.ArmorGasSC.Gas.values().length}));
        e.add(BookEl.head(Lang.tr("sc.manual.singstation.costhead")));
        e.add(BookEl.para(Lang.tr("sc.manual.singstation.cost", 100 - com.sc.util.SingularStationMath.SET_PERCENT)));
        for (int lvl = 1; lvl <= 4; lvl++) {
            long[] r = com.sc.util.SingularStationMath.row(lvl);
            e.add(BookEl.dim(Lang.tr("sc.manual.singstation.row", lvl, lvl + 1, com.sc.util.SingularStationMath.shortAmount(r[0], Lang.tr("sc.singStation.unit.k"), Lang.tr("sc.singStation.unit.m"), Lang.tr("sc.singStation.unit.b")),
                    r[1], r[2], r[3], r[4], com.sc.util.SingularStationMath.minutes(lvl))));
        }
        e.add(BookEl.para(Lang.tr("sc.manual.singstation.process")));
        e.add(BookEl.head(Lang.tr("sc.manual.singstation.morehead")));
        e.add(BookEl.para(Lang.tr("sc.manual.singstation.more", com.sc.util.SingularStationMath.BRANCH_SM,
                com.sc.util.SingularStationMath.TRANSFER_PERCENT)));
        e.add(BookEl.head(stab.getDisplayName(), stab));
        e.add(BookEl.para(Lang.tr("sc.manual.singstation.stab", com.sc.tileentity.TileEntitySingularStationSC.STAB_RADIUS,
                com.sc.util.SingularStationMath.STABILISER_PERCENT, com.sc.util.SingularStationMath.MAX_STABILISERS,
                com.sc.tileentity.TileEntitySingularStationSC.RES_RADIUS, com.sc.util.SingularStationMath.RESONANCE_SPEED_PERCENT,
                100 - com.sc.util.SingularStationMath.RESONANCE_EU_PERCENT)));
        e.add(BookEl.para(Lang.tr("sc.manual.singstation.tanks", com.sc.tileentity.TileEntityArmorStationSC.TANK_CAPACITY,
                com.sc.tileentity.TileEntitySingularStationSC.SM_TANK, com.sc.tileentity.TileEntitySingularStationSC.SM_PER_EXTENSION)));
        // Б-1: the conversion Exo -> Singular
        e.add(BookEl.head(Lang.tr("sc.manual.singstation.convhead"))).addAll(paras("sc.manual.singstation.conv"));
        convertCards(e);
        crafting(e, st);
        // the tool slot (docs/plan-singular-tools.md §4): the Exo blade / drill converted, the Singular ones modernised
        e.add(BookEl.head(Lang.tr("sc.manual.singstation.toolhead"))).addAll(paras("sc.manual.singstation.tool", null,
                new Object[]{toolShare(com.sc.util.SingularStationMath.TOOL_BLADE), toolShare(com.sc.util.SingularStationMath.TOOL_DRILL),
                        com.sc.util.SingularStationMath.CRUMB_SM, com.sc.util.SingularStationMath.CRUMB_MAX_PERCENT,
                        com.sc.util.SingularStationMath.BRANCH_SM}));
        toolConvertCard(e, com.sc.util.SingularStationMath.TOOL_BLADE);
        toolConvertCard(e, com.sc.util.SingularStationMath.TOOL_DRILL);
        e.add(BookEl.link("singularblade", new ItemStack(ModItems.BLADES.get(com.sc.util.BladeType.SINGULAR)).getDisplayName()))
                .add(BookEl.link("singulardrill", new ItemStack(ModItems.DRILLS.get(com.sc.util.DrillType.SINGULAR)).getDisplayName()));
        e.add(BookEl.dim(Lang.tr("sc.manual.singstation.carry")));      // МК-3: the Armour Station's contents go over
        crafting(e, stab);
        e.about(st, stab);
        list.add(e);
    }

    /** Б-1 in the book: per piece Exo -> (the station) -> Singular, its materials, EU / gases and time. */
    private static void convertCards(BookEntry e) {
        ItemStack station = new ItemStack(ModBlocks.singularStation);
        for (int t = 0; t < 4; t++) {
            List<ItemStack> pair = listOf(new ItemStack(ModItems.ARMOR.get(ArmorSuit.EXO)[t]), new ItemStack(ModItems.ARMOR.get(ArmorSuit.SINGULAR)[t]));
            e.add(BookEl.chain(pair, listOf(station)));
            int[] need = com.sc.util.SingularStationMath.convertMaterials(1 << t);
            List<ItemStack> mats = new ArrayList<ItemStack>();
            for (int k = 0; k < need.length; k++) {
                if (need[k] > 0) {
                    mats.add(com.sc.tileentity.TileEntitySingularStationSC.materialStack(k, need[k]));
                }
            }
            e.add(BookEl.items(mats));
            long[] c = com.sc.util.SingularStationMath.convertCost(1 << t);
            e.add(BookEl.dim(Lang.tr("sc.manual.singstation.convrow", Lang.tr("sc.armorhud.piece." + t),
                    com.sc.util.SingularStationMath.shortAmount(c[0], Lang.tr("sc.singStation.unit.k"), Lang.tr("sc.singStation.unit.m"), Lang.tr("sc.singStation.unit.b")),
                    c[1], c[2], c[3], com.sc.util.SingularStationMath.convertTicks(1 << t) / com.sc.util.SingularStationMath.TICKS_PER_MINUTE)));
        }
    }

    private static void singularArmor(List<BookEntry> list, BookChapter c) {
        ArmorSuit s = ArmorSuit.SINGULAR;
        com.sc.item.ItemArmorSC[] pieces = ModItems.ARMOR.get(s);
        BookEntry e = new BookEntry("singulararmor", c, new ItemStack(pieces[1]), Lang.tr("sc.manual.singular.head"));
        e.add(BookEl.title(Lang.tr("sc.manual.singular.head")));
        List<ItemStack> shown = new ArrayList<ItemStack>();
        for (com.sc.item.ItemArmorSC piece : pieces) {
            shown.add(new ItemStack(piece));
            e.about(new ItemStack(piece, 1, OreDictionary.WILDCARD_VALUE));
        }
        e.add(BookEl.items(shown)).addAll(paras("sc.manual.singular.intro"));
        // how to get it (Б-1): an Exo piece converted in the Singular Station
        e.add(BookEl.head(Lang.tr("sc.manual.singular.gethead"))).addAll(paras("sc.manual.singular.get"));
        e.add(BookEl.chain(listOf(new ItemStack(ModItems.ARMOR.get(ArmorSuit.EXO)[1]), new ItemStack(pieces[1])),
                listOf(new ItemStack(ModBlocks.singularStation))));
        e.add(BookEl.link("singularstation", new ItemStack(ModBlocks.singularStation).getDisplayName()));
        e.add(BookEl.head(Lang.tr("sc.manual.singular.statshead")));
        e.add(BookEl.para(Lang.tr("sc.manual.singular.stats", s.material.getDamageReductionAmount(0), s.material.getDamageReductionAmount(1),
                s.material.getDamageReductionAmount(2), s.material.getDamageReductionAmount(3), s.maxCharge, s.chargeTier.name(),
                s.chargeTier.getVoltage())));
        e.add(BookEl.para(Lang.tr("sc.manual.singular.heat", s.heatCapacity, s.heatDissipation,
                (int) com.sc.util.ArmorGasSC.HELIUM_HEAT_PER_MB_SINGULAR, (int) com.sc.util.ArmorGasSC.HELIUM_HEAT_PER_MB,
                Math.round(com.sc.util.ArmorGasSC.RADIATOR_BONUS_SINGULAR * 100), Math.round(com.sc.util.ArmorGasSC.RADIATOR_BONUS * 100))));
        e.add(BookEl.para(Lang.tr("sc.manual.singular.legacy", Math.round((1F - com.sc.util.ArmorGasSC.SINGULAR_GAS_MUL) * 100))));
        e.add(BookEl.head(Lang.tr("sc.manual.singular.tankshead")));
        for (com.sc.util.ArmorGasSC.Gas g : com.sc.util.ArmorGasSC.Gas.values()) {
            StringBuilder tanks = new StringBuilder();
            for (int t = 0; t < 4; t++) {
                int cap = com.sc.util.ArmorGasSC.baseCapacity(new ItemStack(pieces[t]), g);
                if (cap > 0) {
                    tanks.append(tanks.length() > 0 ? ", " : "").append(Lang.tr("sc.armorhud.piece." + t)).append(' ').append(cap);
                }
            }
            if (tanks.length() > 0) {
                e.add(BookEl.dim(Lang.tr("sc.armorStation.gas." + g.key()) + ": " + tanks + " " + mb()));
            }
        }
        e.add(BookEl.head(Lang.tr("sc.armorStation.gas.singular_matter"))).addAll(paras("sc.manual.singular.matter", new Object[]{
                num(com.sc.util.ArmorGasSC.baseCapacity(new ItemStack(pieces[1]), com.sc.util.ArmorGasSC.Gas.SINGULAR_MATTER)),
                TileEntityMachineSC.SM_PER_CAPSULE, com.sc.tileentity.SingularReactorSC.SM_PER_SECOND, num(com.sc.item.ItemSingularCellSC.CAPACITY),
                com.sc.util.ArmorGasSC.Gas.SINGULAR_MATTER.ordinal() + 1}));
        e.add(BookEl.items(listOf(com.sc.item.ItemSingularCellSC.filled(ModItems.singularCell, com.sc.item.ItemSingularCellSC.CAPACITY),
                machineStack(com.sc.machine.MachineType.MATTER_COMPRESSOR), ModBlocks.generatorStack(com.sc.energy.GeneratorType.SINGULAR_REACTOR, 1))));
        e.add(BookEl.link("singulararmor.fn", Lang.tr("sc.book.singfn.title"))).add(BookEl.link("singulararmor.schemes", Lang.tr("sc.manual.singular.schemeshead")))
                .add(BookEl.link("singularstation", new ItemStack(ModBlocks.singularStation).getDisplayName()));
        list.add(e);
        // К8: the colour schemes (with the picture) and the functions / levels - their own articles
        BookEntry sch = new BookEntry("singulararmor.schemes", c, new ItemStack(pieces[0]), Lang.tr("sc.manual.singular.schemeshead"));
        sch.add(BookEl.title(Lang.tr("sc.manual.singular.schemeshead"))).add(BookEl.image("singular_schemes", 300, 166))
                .addAll(paras("sc.manual.singular.schemes"));
        StringBuilder names = new StringBuilder();
        for (com.sc.util.SingularScheme sc : com.sc.util.SingularScheme.values()) {
            names.append(names.length() > 0 ? ", " : "").append(sc.name()).append(" - ").append(Lang.tr(sc.langKey()));
        }
        sch.add(BookEl.dim(names.toString())).add(BookEl.link("singulararmor", Lang.tr("sc.manual.singular.head")));
        list.add(sch);
        e = new BookEntry("singulararmor.fn", c, new ItemStack(pieces[2]), Lang.tr("sc.book.singfn.title"));
        e.add(BookEl.title(Lang.tr("sc.book.singfn.title")));
        // its own functions (stage 2a on): each with its piece and the level it opens at
        e.add(BookEl.head(Lang.tr("sc.manual.singular.fnhead"))).addAll(paras("sc.manual.singular.fn"));
        for (com.sc.util.ArmorFeature f : com.sc.util.ArmorFeature.values()) {
            if (f.minSuit != s) {
                continue;
            }
            String key = "sc.armorfn." + f.name().toLowerCase(Locale.ROOT);
            e.add(BookEl.item(new ItemStack(pieces[f.piece]), Lang.tr(key), Lang.tr("sc.manual.singular.fnline",
                    Lang.tr("sc.armorhud.piece." + f.piece), com.sc.util.SingularLevel.requiredLevel(f), Lang.tr(key + ".desc"))));
        }
        // stage 5: the K menu's Level tab, the function profiles (M4), the cooldown HUD (M3)
        e.add(BookEl.head(Lang.tr("sc.manual.singular.levelhead"))).addAll(paras("sc.manual.singular.level"));
        e.add(BookEl.gap()).add(BookEl.para(Lang.tr("sc.manual.singular.next")))
                .add(BookEl.link("singularstation", new ItemStack(ModBlocks.singularStation).getDisplayName()))
                .add(BookEl.link("singulararmor", Lang.tr("sc.manual.singular.head"))).add(BookEl.link("bridge.armour", Lang.tr("sc.book.bridge_armour.title")));
        list.add(e);
    }

    /**
     * Gases in the energy suits (ArmorGasSC): what each one gives, and the tanks per suit and piece
     * straight from ArmorGasSC; then the Armour Service Station that fills them.
     */
    private static void armorGases(List<BookEntry> list, BookChapter c) {
        BookEntry gases = new BookEntry("armorgases", c, new ItemStack(ModItems.ARMOR.get(ArmorSuit.QUANTUM)[1]), Lang.tr("sc.manual.armor.gaseshead"));
        gases.add(BookEl.title(Lang.tr("sc.manual.armor.gaseshead"))).addAll(paras("sc.manual.armor.gases"));
        for (com.sc.util.ArmorGasSC.Gas g : com.sc.util.ArmorGasSC.Gas.values()) {
            gases.add(BookEl.head(Lang.tr("sc.armorStation.gas." + g.key())));
            gases.add(BookEl.para(Lang.tr("sc.manual.armor.gas." + g.key())));
            for (ArmorSuit suit : ArmorSuit.values()) {
                StringBuilder tanks = new StringBuilder();
                for (int t = 0; t < 4; t++) {
                    int cap = com.sc.util.ArmorGasSC.baseCapacity(new ItemStack(ModItems.ARMOR.get(suit)[t]), g);
                    if (cap > 0) {
                        tanks.append(tanks.length() > 0 ? ", " : "").append(Lang.tr("sc.armorhud.piece." + t)).append(' ').append(cap);
                    }
                }
                if (tanks.length() > 0) {
                    gases.add(BookEl.dim(Lang.tr("sc.suit." + suit.name().toLowerCase(Locale.ROOT)) + ": " + tanks + " " + mb()));
                }
            }
        }
        armorGasRules(gases);
        gases.add(BookEl.head(Lang.tr("sc.manual.armor.gasfillhead"))).addAll(paras("sc.manual.armor.gasfill"));
        list.add(gases);
        ItemStack st = new ItemStack(ModBlocks.armorStation);
        BookEntry station = new BookEntry("armorstation", c, st, st.getDisplayName());
        int gasCount = com.sc.util.ArmorGasSC.Gas.values().length;
        station.add(BookEl.title(st.getDisplayName())).add(BookEl.items(listOf(st)))
                .addAll(paras("sc.manual.armor.station", null, null, null, new Object[]{gasCount}));
        station.add(BookEl.head(Lang.tr("sc.gui.big.upgrades")))
                .add(BookEl.para(Lang.tr("sc.manual.armor.station.modules", com.sc.tileentity.TileEntityArmorStationSC.MAX_OVERCLOCKERS,
                        com.sc.machine.UpgradeType.STORAGE_PER_UPGRADE)));
        station.add(BookEl.head(Lang.tr("sc.manual.armor.station.tankshead")))
                .add(BookEl.para(Lang.tr("sc.manual.armor.station.tanks", gasCount, com.sc.tileentity.TileEntityArmorStationSC.TANK_CAPACITY,
                        com.sc.machine.UpgradeType.TANK_PER_UPGRADE, com.sc.machine.UpgradeType.MAX_TANK_UPGRADES,
                        com.sc.machine.UpgradeType.CLEAR_MB_PER_EU)))
                .add(BookEl.para(Lang.tr("sc.manual.armor.station.windows", gasCount)));
        crafting(station, st);
        station.about(st);
        list.add(station);
    }

    // ------------------------------------------------------------------ 8. field generator

    private static void field(List<BookEntry> list) {
        BookChapter c = BookChapter.FIELD;
        ItemStack fg = new ItemStack(ModBlocks.fieldGeneratorSC);
        BookEntry intro = simple("field", c, fg, "sc.manual.field.title", "sc.manual.field.intro");
        intro.about(new ItemStack(ModBlocks.fieldGeneratorSC, 1, OreDictionary.WILDCARD_VALUE));
        crafting(intro, fg);
        list.add(intro);
        list.add(simple("field.link", c, new ItemStack(ModItems.fieldLinkModule), "sc.manual.field.linkhead", "sc.manual.field.linking")
                .add(BookEl.para(Lang.tr("sc.manual.field.limits", TileEntityFieldGeneratorSC.MAX_NODES, TileEntityFieldGeneratorSC.MAX_LINK_DISTANCE)))
                .about(new ItemStack(ModItems.fieldLinkModule)));
        BookEntry modes = new BookEntry("field.modes", c, fg, Lang.tr("sc.manual.field.modes"));
        modes.add(BookEl.title(Lang.tr("sc.manual.field.modes")));
        int def = com.sc.tileentity.FieldShapeSC.DEFAULT_RANGE, max = com.sc.tileentity.FieldShapeSC.MAX_RANGE;
        for (FieldMode mode : FieldMode.values()) {
            modes.add(BookEl.item(null, Lang.trOr("sc.field.mode." + mode.name().toLowerCase(Locale.ROOT), mode.name()),
                    Lang.tr("sc.manual.field.modeline", String.valueOf(mode.costMultiplier), TileEntityFieldGeneratorSC.upkeepFor(1, def, mode), def,
                            TileEntityFieldGeneratorSC.upkeepFor(TileEntityFieldGeneratorSC.MAX_NODES, max, mode), max)
                            + " " + Lang.tr("sc.manual.field.shape." + mode.name().toLowerCase(Locale.ROOT))));
        }
        modes.add(BookEl.head(Lang.tr("sc.manual.field.rangehead")))
                .add(BookEl.para(Lang.tr("sc.manual.field.range", com.sc.tileentity.FieldShapeSC.MIN_RANGE, max, def)))
                .add(BookEl.para(Lang.tr("sc.manual.field.formula", TileEntityFieldGeneratorSC.BASE_EU_PER_FACE, TileEntityFieldGeneratorSC.RANGE_EU_PER_BLOCK)));
        list.add(modes);
        list.add(new BookEntry("field.protect", c, new ItemStack(Items.arrow), Lang.tr("sc.manual.field.protecthead"))
                .add(BookEl.title(Lang.tr("sc.manual.field.protecthead"))).add(BookEl.para(Lang.tr("sc.manual.field.protect")))
                .add(BookEl.para(Lang.tr("sc.manual.field.projectiles", TileEntityFieldGeneratorSC.DEFLECT_COST)))
                .add(BookEl.para(Lang.tr("sc.manual.field.explosions", TileEntityFieldGeneratorSC.EXPLOSION_COST)))
                .add(BookEl.para(Lang.tr("sc.manual.field.visible"))).addAll(paras("sc.manual.field.power")));
        list.add(simple("field.functions", c, new ItemStack(Items.golden_apple), "sc.manual.field.funchead", "sc.manual.field.functions", "sc.manual.field.targets"));
        BookEntry ch = new BookEntry("field.charging", c, new ItemStack(ModItems.battery, 1, 0), Lang.tr("sc.manual.field.charginghead"));
        ch.add(BookEl.title(Lang.tr("sc.manual.field.charginghead")));
        for (int m = 0; m < TileEntityFieldGeneratorSC.CHARGE_MODES; m++) {
            ch.add(BookEl.item(null, Lang.tr("sc.fieldgui.charge.mode." + m), null));
        }
        ch.addAll(paras("sc.manual.field.charging"));
        list.add(ch);
        list.add(new BookEntry("field.rain", c, new ItemStack(Items.water_bucket), Lang.tr("sc.manual.field.rainhead"))
                .add(BookEl.title(Lang.tr("sc.manual.field.rainhead")))
                .add(BookEl.para(Lang.tr("sc.manual.field.raincost", TileEntityFieldGeneratorSC.RAIN_PCT, TileEntityFieldGeneratorSC.THUNDER_PCT,
                        TileEntityFieldGeneratorSC.LIGHTNING_COST))).addAll(paras("sc.manual.field.rain")));
        list.add(simple("field.switch", c, new ItemStack(Blocks.lever), "sc.manual.field.switchhead", "sc.manual.field.switch"));
        list.add(simple("field.access", c, new ItemStack(Items.name_tag), "sc.manual.field.accesshead", "sc.manual.field.access"));
        list.add(simple("field.look", c, new ItemStack(Blocks.glass), "sc.manual.field.lookhead", "sc.manual.field.look"));
    }

    // ------------------------------------------------------------------ 9. safety

    private static void safety(List<BookEntry> list) {
        BookChapter c = BookChapter.SAFETY;
        StringBuilder corrosive = new StringBuilder();
        for (String name : CorrosiveFluids.all()) {
            corrosive.append(corrosive.length() > 0 ? ", " : "").append(fluidName(name));
        }
        list.add(new BookEntry("corrosive", c, new ItemStack(ModBlocks.pipeSC, 1, 0), Lang.tr("sc.manual.safety.corrosivehead"))
                .add(BookEl.title(Lang.tr("sc.manual.safety.corrosivehead"))).add(BookEl.para(corrosive.toString())).addAll(paras("sc.manual.safety.corrosive")));
        list.add(simple("explosions", c, new ItemStack(Blocks.tnt), "sc.manual.safety.explosionshead")
                .addAll(paras("sc.manual.safety.explosions", new Object[]{(int) com.sc.energy.ExplosionLogic.EXPLOSION_BASE_POWER})));
        list.add(simple("hazmat", c, new ItemStack(ModItems.leadSuit[1]), "sc.manual.safety.suithead", "sc.manual.safety.suit"));
        BookEntry rad = new BookEntry("radiation", c, new ItemStack(ModItems.dosimeter), Lang.tr("sc.manual.safety.radiationhead"));
        rad.add(BookEl.title(Lang.tr("sc.manual.safety.radiationhead"))).add(BookEl.image("radiation", 256, 112)).addAll(paras("sc.manual.safety.radiation"))
                .add(BookEl.head(Lang.tr("sc.manual.safety.radsources")));
        for (GeneratorType type : GeneratorType.values()) {
            float base = TileEntityGeneratorSC.radiationBase(type);
            if (base > 0) {
                ItemStack s = ModBlocks.generatorStack(type, 1);
                rad.add(BookEl.item(s, type.localizedName(), Lang.tr(type == GeneratorType.RTG ? "sc.manual.safety.radline.rtg" : "sc.manual.safety.radline",
                        com.sc.radiation.RadiationSC.fmt(base), TileEntityGeneratorSC.radiationRadius(type))));
            }
        }
        rad.add(BookEl.item(new ItemStack(ModItems.isotopeCapsule), Lang.tr("sc.book.carried"), Lang.tr("sc.manual.safety.radline.carried",
                com.sc.radiation.RadiationSC.fmt(com.sc.radiation.RadiationSC.CAPSULE_LEVEL), com.sc.radiation.RadiationSC.fmt(com.sc.radiation.RadiationSC.MONAZITE_STACK_LEVEL))));
        rad.about(new ItemStack(ModItems.dosimeter));
        list.add(rad);
        list.add(simple("dose", c, new ItemStack(Items.rotten_flesh), "sc.manual.safety.dosehead", "sc.manual.safety.dose"));
        List<ItemStack> prot = new ArrayList<ItemStack>();
        prot.add(new ItemStack(ModBlocks.leadBlock));
        prot.add(new ItemStack(ModBlocks.leadGlass));
        for (com.sc.item.ItemLeadSuitSC p : ModItems.leadSuit) {
            prot.add(new ItemStack(p));
        }
        prot.add(new ItemStack(ModItems.dosimeter));
        prot.add(ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.RAD_SHIELDING));
        BookEntry protect = simple("radprotect", c, new ItemStack(ModBlocks.leadBlock), "sc.manual.safety.radprothead", "sc.manual.safety.radprot");
        protect.add(BookEl.items(prot));
        for (com.sc.item.ItemLeadSuitSC p : ModItems.leadSuit) {
            protect.about(new ItemStack(p, 1, OreDictionary.WILDCARD_VALUE));
        }
        list.add(protect);
        BookEntry cure = simple("radcure", c, new ItemStack(ModItems.radioprotector), "sc.manual.safety.radcurehead", "sc.manual.safety.radcure");
        cure.add(BookEl.items(listOf(new ItemStack(ModItems.radioprotector), new ItemStack(ModBlocks.shower))))
                .about(new ItemStack(ModItems.radioprotector), new ItemStack(ModBlocks.shower));
        list.add(cure);
    }

    // ------------------------------------------------------------------ 10. recipe search

    /** Machine and crafting-table recipes whose machine, ingredients or products match the query (at most `limit`). */
    public static List<BookEl> recipes(String query, int limit) {
        List<BookEl> out = new ArrayList<BookEl>();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.length() < 2) {
            out.add(BookEl.dim(Lang.tr("sc.book.recipes.type")));
            return out;
        }
        for (MachineType type : MachineType.values()) {
            for (MachineRecipe r : RecipeRegistry.recipesFor(type)) {
                if (out.size() < limit && matches(type, r, needle)) {
                    out.add(BookEl.machine(r));
                }
            }
        }
        for (Object o : CraftingManager.getInstance().getRecipeList()) {
            IRecipe r = (IRecipe) o;
            ItemStack res = r.getRecipeOutput();
            if (out.size() >= limit || res == null || res.getItem() == null || !isModItem(res)) {
                continue;
            }
            if (name(res).contains(needle)) {
                out.add(BookEl.craft(r));
            }
        }
        if (out.isEmpty()) {
            out.add(BookEl.dim(Lang.tr("sc.manual.recipes.none")));
        } else if (out.size() >= limit) {
            out.add(BookEl.dim(Lang.tr("sc.book.recipes.more", limit)));
        }
        return out;
    }

    private static boolean matches(MachineType type, MachineRecipe r, String needle) {
        if (type.localizedName().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        for (ItemStack[] group : new ItemStack[][]{r.inputs, r.outputs, r.byproducts}) {
            for (ItemStack s : group) {
                if (name(s).contains(needle)) {
                    return true;
                }
            }
        }
        for (FluidStack f : new FluidStack[]{r.fluidInputA, r.fluidInputB, r.fluidOutputA, r.fluidOutputB}) {
            if (f != null && f.getFluid() != null && f.getFluid().getLocalizedName(f).toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String name(ItemStack s) {
        try {
            return s.getDisplayName().toLowerCase(Locale.ROOT);
        } catch (Throwable t) {
            return "";
        }
    }

    private static boolean isModItem(ItemStack stack) {
        Object name = Item.itemRegistry.getNameForObject(stack.getItem());
        return name != null && name.toString().startsWith(com.sc.Reference.MODID + ":");
    }

    // ------------------------------------------------------------------ shared

    /** A title and the paragraphs of one or more lang blocks. */
    static BookEntry simple(String id, BookChapter c, ItemStack icon, String titleKey, String... paraKeys) {
        BookEntry e = new BookEntry(id, c, icon, Lang.tr(titleKey));
        e.add(BookEl.title(Lang.tr(titleKey)));
        for (String k : paraKeys) {
            e.addAll(paras(k));
        }
        return e;
    }

    /**
     * The strict rules of the Quantum / Exo / Singular suits: the "function - gas - use" table straight from
     * ArmorFeature.gas() / gasUse() (what the armour logic runs on), and the emergency mode.
     */
    private static void armorGasRules(BookEntry gases) {
        gases.add(BookEl.head(Lang.tr("sc.manual.armor.strict.head"))).addAll(paras("sc.manual.armor.strict"));
        BookEl t = BookEl.table(Lang.tr("sc.manual.armor.strict.t.fn"), Lang.tr("sc.manual.armor.strict.t.gas"),
                Lang.tr("sc.manual.armor.strict.t.use"));
        for (com.sc.util.ArmorGasSC.Gas g : com.sc.util.ArmorGasSC.Gas.values()) {
            for (com.sc.util.ArmorFeature f : com.sc.util.ArmorFeature.values()) {
                if (f.gas() != g) {
                    continue;
                }
                ArmorSuit from = f.minSuit.ordinal() < ArmorSuit.QUANTUM.ordinal() && f != com.sc.util.ArmorFeature.AIR ? ArmorSuit.QUANTUM : f.minSuit;
                ItemStack icon = new ItemStack(ModItems.ARMOR.get(from)[f.piece]);
                t.row(icon, Lang.tr("sc.armorfn." + f.name().toLowerCase(Locale.ROOT)),
                        Lang.trOr("sc.gas." + g.key(), g.key()), gasUseText(f));
            }
        }
        gases.add(t);
        gases.add(BookEl.head(Lang.tr("sc.manual.armor.emergency.head"))).addAll(paras("sc.manual.armor.emergency"));
    }

    /** "1 mB/s", "50 mB a use", "1 mB per damage point", "1 mB/min", "the loop's cooling", "by the radiation". */
    private static String gasUseText(com.sc.util.ArmorFeature f) {
        String n = number(f.gasUse());
        switch (f.gasUseKind()) {
            case com.sc.util.ArmorFeature.USE_MINUTE:
                return Lang.tr("sc.manual.armor.strict.min", n);
            case com.sc.util.ArmorFeature.USE_ONCE:
                return Lang.tr(f.gasPerPoint() ? "sc.manual.armor.strict.point" : "sc.manual.armor.strict.once", n);
            case com.sc.util.ArmorFeature.USE_COOLING:
                return Lang.tr("sc.manual.armor.strict.cooling");
            case com.sc.util.ArmorFeature.USE_RADIATION:
                return Lang.tr("sc.manual.armor.strict.rad");
            default:
                return Lang.tr("sc.manual.armor.strict.sec", n);
        }
    }

    /**
     * A rate for the text: whole numbers as they are, fractions with the language's decimal mark
     * ("sc.num.decimal": a comma in Russian "0,2", a dot in English "0.2"; a dot when the key is missing).
     */
    static String number(float v) {
        if (v == (int) v) {
            return String.valueOf((int) v);
        }
        String s = String.valueOf(v);
        String mark = Lang.trOr("sc.num.decimal", ".");
        return mark.isEmpty() || ".".equals(mark) ? s : s.replace(".", mark);
    }

    /** A block of prose stored as key.1, key.2, ... */
    static List<BookEl> paras(String baseKey) {
        List<BookEl> out = new ArrayList<BookEl>();
        for (int i = 1; ; i++) {
            String text = Lang.trOr(baseKey + "." + i, null);
            if (text == null) {
                break;
            }
            out.add(BookEl.para(text));
        }
        return out;
    }

    /**
     * A block of prose key.1, key.2, ... where paragraph i is formatted with args[i - 1] - the numbers
     * straight from the code constants (К14). A paragraph without args (null or past the end) is shown
     * as it is; one with args writes a literal percent as %%.
     */
    static List<BookEl> paras(String baseKey, Object[]... args) {
        List<BookEl> out = new ArrayList<BookEl>();
        for (int i = 1; ; i++) {
            String key = baseKey + "." + i;
            String text = Lang.trOr(key, null);
            if (text == null) {
                break;
            }
            out.add(BookEl.para(i <= args.length && args[i - 1] != null ? Lang.tr(key, args[i - 1]) : text));
        }
        return out;
    }

    /** Л-1: the millibucket unit in the language ("мБ" / "mB"). */
    static String mb() {
        return Lang.trOr("sc.levelgui.mb", "mB");
    }

    /** A big number in the language's style: "50 000" in Russian, "50,000" in English. */
    static String num(long v) {
        String s = BridgeMathSC.group(v);
        return "ru".equals(Lang.trOr("sc.book.lang", "en")) ? s : s.replace(' ', ',');
    }

    static List<ItemStack> listOf(ItemStack... s) {
        List<ItemStack> l = new ArrayList<ItemStack>();
        for (ItemStack x : s) {
            l.add(x);
        }
        return l;
    }

    static String fluidName(String name) {
        Fluid fluid = FluidRegistry.getFluid(name);
        return fluid == null ? name : fluid.getLocalizedName(new FluidStack(fluid, 1000));
    }

    static String percent(float chance) {
        float pct = chance * 100f;
        return pct == Math.round(pct) ? String.valueOf(Math.round(pct)) : String.format(Locale.ROOT, "%.1f", pct);
    }

    /** Ore generation as configured: min / max height, vein size, veins per chunk; biomes. */
    public static ConfigSC.OreGenSettings gen(OreEntry ore) {
        return ConfigSC.settingsFor(ore);
    }

    public static String biomes(OreEntry ore) {
        if (ore.biomes == null) {
            return Lang.tr("sc.manual.ores.anybiome");
        }
        StringBuilder sb = new StringBuilder();
        for (BiomeDictionary.Type type : ore.biomes) {
            sb.append(sb.length() > 0 ? ", " : "").append(Lang.trOr("sc.biome." + type.name().toLowerCase(Locale.ROOT), type.name()));
        }
        return sb.toString();
    }

    public static ItemStack pickaxe(OreEntry ore) {
        switch (ore.tool) {
            case STONE: return new ItemStack(Items.stone_pickaxe);
            case IRON: return new ItemStack(Items.iron_pickaxe);
            default: return new ItemStack(Items.diamond_pickaxe);
        }
    }

    // ------------------------------------------------------------------ the Ground / Space Bridge (its own chapter, К4)

    private static ItemStack bridgePart(int part) {
        return com.sc.block.BlockBridgeSC.stack(part, 1);
    }

    /** The crafting cards of these stacks, under one "Craft" heading. */
    static void crafts(BookEntry e, ItemStack... stacks) {
        boolean head = false;
        for (ItemStack s : stacks) {
            for (IRecipe r : craftingFor(s)) {
                if (!head) {
                    e.add(BookEl.head(Lang.tr("sc.book.craft")));
                    head = true;
                }
                e.add(BookEl.craft(r));
            }
        }
    }

    /** The links to the bridge chapter's other articles (their titles: sc.book.bridge_*.title). */
    private static void bridgeLinks(BookEntry e, String... ids) {
        for (String id : ids) {
            e.add(BookEl.link(id, Lang.tr("sc.book." + id.replace('.', '_') + ".title")));
        }
    }

    /**
     * The Ground and Space Bridge (docs/plan-ground-bridge.md): six articles - the build, resources and
     * opening, remotes / modes / access, the Singular armour link, wear / stability / heat, familiar
     * places and the modules. Each part's and item's G key opens the article about it.
     */
    private static void bridge(List<BookEntry> list) {
        BookChapter ch = BookChapter.BRIDGE;
        final int ctrl = com.sc.block.BlockBridgeSC.CONTROLLER;
        ItemStack ground = new ItemStack(ModItems.bridgeRemote, 1, com.sc.item.ItemBridgeRemoteSC.GROUND);
        ItemStack space = new ItemStack(ModItems.bridgeRemote, 1, com.sc.item.ItemBridgeRemoteSC.SPACE);
        ItemStack coord = new ItemStack(ModItems.coordinator), link = new ItemStack(ModItems.bridgeLinkModule);
        ItemStack coil = new ItemStack(ModBlocks.gravityCoil);
        // 1. the build
        BookEntry b = new BookEntry("bridge", ch, bridgePart(ctrl), Lang.tr("sc.book.bridge.title"));
        List<ItemStack> parts = new ArrayList<ItemStack>();
        for (int i = 0; i < com.sc.block.BlockBridgeSC.parts(); i++) {
            parts.add(bridgePart(i));
        }
        parts.add(coil);
        parts.add(new ItemStack(ModBlocks.gravStabiliser));
        parts.add(ground);
        parts.add(space);
        parts.add(coord);
        parts.add(link);
        b.add(BookEl.title(Lang.tr("sc.manual.bridge.title"))).add(BookEl.items(parts)).addAll(paras("sc.manual.bridge.about"));
        b.add(BookEl.head(Lang.tr("sc.manual.bridge.groundhead"))).add(BookEl.image("bridge_ground", 300, 194))
                .add(BookEl.layers(bridgeLayers(5), new String[]{Lang.tr("sc.manual.bridge.front")}))
                .addAll(paras("sc.manual.bridge.ground", null, null, new Object[]{com.sc.bridge.BridgeStructureSC.SIDE_CLEAR, BridgeMathSC.MAX_STABILISERS,
                        BridgeMathSC.STAB_RANGE, BridgeMathSC.STAB_LIFE_PERCENT}));
        b.add(BookEl.head(Lang.tr("sc.manual.bridge.spacehead"))).add(BookEl.image("bridge_space", 300, 239))
                .add(BookEl.layers(bridgeLayers(7), new String[]{Lang.tr("sc.manual.bridge.front")})).addAll(paras("sc.manual.bridge.space"));
        b.add(BookEl.head(Lang.tr("sc.manual.bridge.calibhead")))
                .add(BookEl.para(Lang.tr("sc.manual.bridge.calib", BridgeMathSC.CALIB_KR, BridgeMathSC.CALIB_EU / 1000000)));
        crafts(b, bridgePart(ctrl), bridgePart(com.sc.block.BlockBridgeSC.CAPACITOR), bridgePart(com.sc.block.BlockBridgeSC.ENERGY_PORT),
                bridgePart(com.sc.block.BlockBridgeSC.GAS_PORT), bridgePart(com.sc.block.BlockBridgeSC.FOCUSER), coil);
        b.add(BookEl.dim(Lang.tr("sc.book.bridge.stabcraft"))).add(BookEl.link("singularstation", new ItemStack(ModBlocks.singularStation).getDisplayName()));
        bridgeLinks(b, "bridge.res", "bridge.remote", "bridge.armour", "bridge.wear", "bridge.places");
        b.about(bridgePart(ctrl), bridgePart(com.sc.block.BlockBridgeSC.CAPACITOR), bridgePart(com.sc.block.BlockBridgeSC.ENERGY_PORT),
                bridgePart(com.sc.block.BlockBridgeSC.GAS_PORT), bridgePart(com.sc.block.BlockBridgeSC.FOCUSER));
        list.add(b);

        // 2. resources and opening, own coordinates (in the air too, the soft landing)
        BookEntry r = new BookEntry("bridge.res", ch, bridgePart(com.sc.block.BlockBridgeSC.CAPACITOR), Lang.tr("sc.book.bridge_res.title"));
        r.add(BookEl.title(Lang.tr("sc.book.bridge_res.title"))).add(BookEl.head(Lang.tr("sc.manual.bridge.reshead")));
        BridgeMathSC.Cost g = BridgeMathSC.cost(BridgeMathSC.GROUND, new long[]{0}, false, false, 0);
        BridgeMathSC.Cost s = BridgeMathSC.cost(BridgeMathSC.SPACE, new long[]{0}, false, false, 0);
        BridgeMathSC.Cost sa = BridgeMathSC.cost(BridgeMathSC.SPACE, new long[]{0}, false, true, 0);
        BookEl t = BookEl.table("", Lang.tr("sc.manual.bridge.t.ground"), Lang.tr("sc.manual.bridge.t.space"));
        String m = Lang.tr("sc.bridge.unit.m");
        t.row(null, Lang.tr("sc.manual.bridge.t.burst"), Lang.tr("sc.manual.bridge.t.burstg", g.eu / 1000000, BridgeMathSC.GROUND_PER_1000 / 1000000),
                Lang.tr("sc.manual.bridge.t.bursts", sa.eu / 1000000, s.eu / 1000000));
        t.row(null, Lang.tr("sc.bridge.res.gas.singular_matter"), Lang.tr("sc.manual.bridge.t.smg", g.sm), Lang.tr("sc.manual.bridge.t.sms", s.sm, sa.sm));
        t.row(null, Lang.tr("sc.bridge.res.gas.deuterium"), String.valueOf(g.d), String.valueOf(s.d));
        t.row(null, Lang.tr("sc.bridge.res.gas.krypton"), Lang.tr("sc.manual.bridge.t.krg", g.kr), Lang.tr("sc.manual.bridge.t.krs", s.kr, sa.kr));
        t.row(null, Lang.tr("sc.bridge.res.gas.argon"), String.valueOf(g.ar), String.valueOf(s.ar));
        t.row(null, Lang.tr("sc.manual.bridge.t.hold"), BridgeMathSC.group(g.holdEu) + " EU/t", BridgeMathSC.group(s.holdEu) + " EU/t");
        t.row(null, Lang.tr("sc.manual.bridge.t.gases"), Lang.tr("sc.manual.bridge.t.gasesg", g.heSec, g.arSec), Lang.tr("sc.manual.bridge.t.gasess", s.heSec, s.arSec, s.d2oSec));
        t.row(null, Lang.tr("sc.manual.bridge.t.life"), Lang.tr("sc.manual.bridge.t.lifev", g.lifeTicks / 20, g.lifeTicks / 10),
                Lang.tr("sc.manual.bridge.t.lifev", s.lifeTicks / 20, s.lifeTicks / 10));
        r.add(t);
        StringBuilder tanks = new StringBuilder();
        for (int i = 0; i < BridgeMathSC.GASES.length; i++) {
            tanks.append(i > 0 ? ", " : "").append(Lang.tr("sc.bridge.res.gas." + BridgeMathSC.GASES[i].key())).append(' ')
                    .append(BridgeMathSC.group(BridgeMathSC.BASE_TANK[i]));
        }
        r.add(BookEl.para(Lang.tr("sc.manual.bridge.tanks", tanks.toString(), BridgeMathSC.EXTRA_PORT_PERCENT,
                BridgeMathSC.shortEu(BridgeMathSC.CAPACITOR_EU, m, Lang.tr("sc.bridge.unit.g")))));
        List<ItemStack> gases = new ArrayList<ItemStack>();
        gases.add(com.sc.item.ItemSingularCellSC.filled(ModItems.singularCell, com.sc.item.ItemSingularCellSC.CAPACITY));
        for (String f : new String[]{"krypton", "liquidhelium", "argon", "deuterium", "heavywater"}) {
            gases.add(new ItemStack(ModItems.fluidBucket, 1, java.util.Arrays.asList(com.sc.item.ItemFluidBucketSC.FLUIDS).indexOf(f)));
        }
        r.add(BookEl.items(gases)).add(BookEl.link("fluidlist", Lang.tr("sc.book.fluidlist.title")));
        r.add(BookEl.head(Lang.tr("sc.manual.bridge.openhead"))).addAll(paras("sc.manual.bridge.open", null, new Object[]{BridgeMathSC.SHORT_GRACE_S, BridgeMathSC.STAB_FOLD}));
        r.add(BookEl.para(Lang.tr("sc.manual.bridge.cool", BridgeMathSC.COOL_S, BridgeMathSC.COOL_S / BridgeMathSC.COOLER_SPEED)));
        r.add(BookEl.head(Lang.tr("sc.manual.bridge.coordhead"))).addAll(paras("sc.manual.bridge.coord", null, new Object[]{BridgeMathSC.BEACON_DISCOUNT},
                new Object[]{com.sc.bridge.BridgeSoftLandSC.MAX_TICKS / 20}));
        bridgeLinks(r, "bridge.remote", "bridge.wear");
        list.add(r);

        // 3. remotes, the coordinator, the modes, access and consent
        BookEntry rm = new BookEntry("bridge.remote", ch, ground, Lang.tr("sc.book.bridge_remote.title"));
        rm.add(BookEl.title(Lang.tr("sc.book.bridge_remote.title"))).add(BookEl.head(Lang.tr("sc.manual.bridge.remotehead")))
                .add(BookEl.items(listOf(ground, space, coord)))
                .addAll(paras("sc.manual.bridge.remote", new Object[]{BridgeMathSC.REMOTE_SIGNAL_EU / 1000000}, new Object[]{BridgeMathSC.REMOTE_MODE_EU}));
        rm.add(BookEl.head(Lang.tr("sc.manual.bridge.modeshead"))).addAll(paras("sc.manual.bridge.modes", null, new Object[]{BridgeMathSC.PROJECTION_AHEAD}));
        rm.add(BookEl.head(Lang.tr("sc.manual.bridge.accesshead"))).addAll(paras("sc.manual.bridge.access", new Object[]{BridgeMathSC.MAX_FRIENDS},
                new Object[]{BridgeMathSC.CONSENT_RADIUS, BridgeMathSC.CONSENT_TICKS / 20}));
        crafts(rm, ground, space, coord);
        rm.add(BookEl.link("multiplayer", Lang.tr("sc.book.multiplayer.title")));
        bridgeLinks(rm, "bridge.armour");
        rm.about(ground, space, new ItemStack(ModItems.bridgeRemote, 1, OreDictionary.WILDCARD_VALUE), coord);
        list.add(rm);

        // 4. the Singular armour link
        BookEntry ar = new BookEntry("bridge.armour", ch, link, Lang.tr("sc.book.bridge_armour.title"));
        ar.add(BookEl.title(Lang.tr("sc.book.bridge_armour.title"))).add(BookEl.items(listOf(link, new ItemStack(ModItems.ARMOR.get(ArmorSuit.SINGULAR)[0]))))
                .addAll(paras("sc.manual.bridge.armour", new Object[]{BridgeMathSC.MAX_LINKS},
                        new Object[]{BridgeMathSC.LOOK_RANGE, BridgeMathSC.PROBE_KR, BridgeMathSC.ARMOUR_DISCOUNT}));
        crafts(ar, link);
        ar.add(BookEl.link("singulararmor", Lang.tr("sc.manual.singular.head"))).add(BookEl.link("keys", Lang.tr("sc.book.keys.title")));
        ar.about(link);
        list.add(ar);

        // 5. wear and repair, stability (mass, interference), heat and overheating, the look
        BookEntry w = new BookEntry("bridge.wear", ch, coil, Lang.tr("sc.book.bridge_wear.title"));
        w.add(BookEl.title(Lang.tr("sc.book.bridge_wear.title")));
        w.add(BookEl.head(Lang.tr("sc.manual.bridge.wearhead"))).addAll(paras("sc.manual.bridge.wear", new Object[]{BridgeMathSC.WEAR_FAR, BridgeMathSC.WEAR_FREE, BridgeMathSC.WEAR_STAB_DIV},
                new Object[]{BridgeMathSC.REPAIR_HE_PER_WEAR, BridgeMathSC.REPAIR_EU_PER_WEAR / 1000000, BridgeMathSC.COIL_SWAP_WEAR}));
        w.add(BookEl.head(Lang.tr("sc.manual.bridge.stabhead"))).addAll(paras("sc.manual.bridge.stab", new Object[]{BridgeMathSC.STAB_MISSING_PERCENT},
                new Object[]{BridgeMathSC.TURBULENCE, BridgeMathSC.TURB_SHIFT, BridgeMathSC.STAB_FOLD},
                new Object[]{number(BridgeMathSC.MASS_PLAYER / 10F), number(BridgeMathSC.MASS_MOB / 10F), number(BridgeMathSC.MASS_ITEM / 10F), number(BridgeMathSC.MASS_CART / 10F),
                        BridgeMathSC.MASS_EU_PER_UNIT / 1000000, BridgeMathSC.MASS_STAB_PER_UNIT, BridgeMathSC.MASS_TICKS / 20},
                new Object[]{BridgeMathSC.INTERFERENCE_RADIUS, BridgeMathSC.INTERFERENCE_STAB, BridgeMathSC.STORM_STAB, BridgeMathSC.RESONANCE_RADIUS, BridgeMathSC.RESONANCE_PCT}));
        w.add(BookEl.head(Lang.tr("sc.manual.bridge.heathead"))).addAll(paras("sc.manual.bridge.heat", new Object[]{number(BridgeMathSC.HEAT_BASE / 10F)},
                new Object[]{BridgeMathSC.OVERHEAT_LOCK_S / 60, BridgeMathSC.OVERHEAT_WEAR, BridgeMathSC.SHORT_GRACE_S}));
        w.add(BookEl.head(Lang.tr("sc.manual.bridge.lookhead"))).addAll(paras("sc.manual.bridge.look"));
        bridgeLinks(w, "bridge.places");
        list.add(w);

        // 6. familiar places, scouting, the modules (the beacon and the anchor among them)
        BookEntry p = new BookEntry("bridge.places", ch, bridgePart(com.sc.block.BlockBridgeSC.NAV), Lang.tr("sc.book.bridge_places.title"));
        p.add(BookEl.title(Lang.tr("sc.book.bridge_places.title")));
        p.add(BookEl.head(Lang.tr("sc.manual.bridge.famhead"))).addAll(paras("sc.manual.bridge.fam", new Object[]{BridgeMathSC.FAMILIAR_CAP},
                new Object[]{-BridgeMathSC.FAMILIAR_PCT, BridgeMathSC.UNFAMILIAR_PCT, BridgeMathSC.SCATTER_UNFAMILIAR, BridgeMathSC.SCATTER_NAV}, new Object[]{BridgeMathSC.SCATTER_SCOUT}));
        p.add(BookEl.head(Lang.tr("sc.manual.bridge.modhead"))).add(BookEl.dim(Lang.tr("sc.book.bridge.modrule")));
        List<ItemStack> mods = new ArrayList<ItemStack>();
        for (int i = com.sc.block.BlockBridgeSC.NAV; i < com.sc.block.BlockBridgeSC.parts(); i++) {
            ItemStack mod = bridgePart(i);
            mods.add(mod);
            p.add(BookEl.item(mod, mod.getDisplayName(), bridgeModuleLine(i)));
        }
        crafts(p, mods.toArray(new ItemStack[0]));
        bridgeLinks(p, "bridge");
        p.about(mods.toArray(new ItemStack[0]));
        list.add(p);
    }

    /** One bridge module's line (the numbers from BridgeMathSC, the controller and BridgeSpaceSC). */
    static String bridgeModuleLine(int part) {
        switch (part) {
            case com.sc.block.BlockBridgeSC.NAV:
                return Lang.tr("sc.book.bmod.navComputer", com.sc.tileentity.TileEntityBridgeControllerSC.BOOKMARKS_NAV,
                        com.sc.tileentity.TileEntityBridgeControllerSC.BOOKMARKS, com.sc.bridge.BridgeSpaceSC.NEAR_RADIUS_NAV,
                        com.sc.bridge.BridgeSpaceSC.NEAR_RADIUS, BridgeMathSC.SCATTER_NAV, BridgeMathSC.SCATTER_UNFAMILIAR);
            case com.sc.block.BlockBridgeSC.COOLER:
                return Lang.tr("sc.book.bmod.ringCooler", BridgeMathSC.COOLER_SPEED, BridgeMathSC.COOL_S / BridgeMathSC.COOLER_SPEED,
                        BridgeMathSC.COOL_S, BridgeMathSC.COOLER_HE_PER_S);
            case com.sc.block.BlockBridgeSC.BEACON:
                return Lang.tr("sc.book.bmod.receiverBeacon", BridgeMathSC.BEACON_RADIUS, BridgeMathSC.BEACON_DISCOUNT);
            case com.sc.block.BlockBridgeSC.ANCHOR:
                return Lang.tr("sc.book.bmod.dimAnchor", BridgeMathSC.NO_ANCHOR_MUL, BridgeMathSC.SCATTER_SCOUT);
            default:
                return Lang.tr("sc.book.bmod." + com.sc.block.BlockBridgeSC.NAMES[part]);
        }
    }

    /** The bridge seen from the front (one layer): the ring, the focusers (7x7), the controller row with its parts. */
    public static ItemStack[][][] bridgeLayers(int size) {
        int h = (size - 1) / 2, g = size + 2;
        ItemStack coil = new ItemStack(ModBlocks.gravityCoil);
        ItemStack[][][] l = new ItemStack[1][g][g];
        for (int r = 0; r < g; r++) {
            for (int c = 0; c < g; c++) {
                int u = c - h - 1, v = g - 1 - r;
                if (com.sc.bridge.BridgeStructureSC.isRing(size, u, v)) {
                    l[0][r][c] = coil;
                } else if (size == 7 && com.sc.bridge.BridgeStructureSC.isFocuser(size, u, v)) {
                    l[0][r][c] = com.sc.block.BlockBridgeSC.stack(com.sc.block.BlockBridgeSC.FOCUSER, 1);
                }
            }
        }
        int row = g - 1, mid = h + 1;
        l[0][row][mid] = com.sc.block.BlockBridgeSC.stack(com.sc.block.BlockBridgeSC.CONTROLLER, 1);
        l[0][row][mid - 1] = com.sc.block.BlockBridgeSC.stack(com.sc.block.BlockBridgeSC.CAPACITOR, 1);
        l[0][row][mid - 2] = com.sc.block.BlockBridgeSC.stack(com.sc.block.BlockBridgeSC.CAPACITOR, 1);
        l[0][row][mid + 1] = com.sc.block.BlockBridgeSC.stack(com.sc.block.BlockBridgeSC.ENERGY_PORT, 1);
        l[0][row][mid + 2] = com.sc.block.BlockBridgeSC.stack(com.sc.block.BlockBridgeSC.GAS_PORT, 1);
        if (size == 7) {
            l[0][row][mid + 3] = com.sc.block.BlockBridgeSC.stack(com.sc.block.BlockBridgeSC.CAPACITOR, 1);
        }
        return l;
    }
}
