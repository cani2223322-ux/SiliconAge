package com.sc.manual;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.sc.energy.GeneratorType;
import com.sc.energy.Tier;
import com.sc.init.ModBlocks;
import com.sc.init.ModItems;
import com.sc.item.ItemQuarryModuleSC;
import com.sc.machine.MachineRecipe;
import com.sc.machine.MachineType;
import com.sc.machine.RecipeRegistry;
import com.sc.machine.UpgradeType;
import com.sc.tileentity.TileEntityQuarrySC;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

/**
 * К6: the reference articles - the mod's keys, compatibility, the owner and multiplayer, the
 * fluids and gases (made / used straight from the recipes and the generators), every module in
 * one place; and two machine-chapter articles: defects and the Quality module, the Exo
 * Drilling Rig (its ore table from ExoOreTableSC).
 */
final class BookReferenceSC {

    private BookReferenceSC() {
    }

    static void reference(List<BookEntry> list) {
        BookChapter c = BookChapter.REFERENCE;
        list.add(keys(c));
        list.add(compat(c));
        list.add(multiplayer(c));
        list.add(fluids(c));
        list.add(modules(c));
    }

    // ------------------------------------------------------------------ the keys

    private static BookEntry keys(BookChapter c) {
        BookEntry e = new BookEntry("keys", c, new ItemStack(ModItems.manual), Lang.tr("sc.book.keys.title"));
        e.add(BookEl.title(Lang.tr("sc.book.keys.title"))).add(BookEl.para(Lang.tr("sc.book.keys.intro")));
        BookEl t = BookEl.table(Lang.tr("sc.book.keys.t.key"), Lang.tr("sc.book.keys.t.what"));
        // the defaults of ArmorClientSC / BookKeySC (P, H and J only when nothing else has them)
        t.row(null, "K", Lang.tr("key.sc.armor"));
        t.row(null, "R", Lang.tr("key.sc.dash"));
        t.row(null, "G", Lang.tr("key.sc.book"));
        t.row(null, "P *", Lang.tr("key.sc.profile"));
        t.row(null, "H *", Lang.tr("key.sc.bridgeHome"));
        t.row(null, "J *", Lang.tr("key.sc.bridgeLast"));
        t.row(null, "-", Lang.tr("key.sc.bridgeMark"));
        e.add(t).add(BookEl.dim(Lang.tr("sc.book.keys.free")));
        e.addAll(BookContent.paras("sc.book.keys.fn"));
        e.add(BookEl.head(Lang.tr("sc.book.keys.mousehead"))).addAll(BookContent.paras("sc.book.keys.mouse"));
        e.add(BookEl.head(Lang.tr("sc.book.keys.neihead"))).addAll(BookContent.paras("sc.book.keys.nei"));
        e.add(BookEl.link("controls", Lang.tr("sc.manual.intro.controlshead"))).add(BookEl.link("armorfn", Lang.tr("sc.manual.armor.fnhead")))
                .add(BookEl.link("bridge.armour", Lang.tr("sc.book.bridge_armour.title")));
        return e;
    }

    // ------------------------------------------------------------------ compatibility

    private static BookEntry compat(BookChapter c) {
        BookEntry e = new BookEntry("compat", c, new ItemStack(ModBlocks.cableSC, 1, 0), Lang.tr("sc.book.compat.title"));
        e.add(BookEl.title(Lang.tr("sc.book.compat.title"))).add(BookEl.para(Lang.tr("sc.book.compat.intro")));
        e.add(BookEl.head("IndustrialCraft 2")).addAll(BookContent.paras("sc.book.compat.ic2", null, null, new Object[]{Tier.SV.toIc2Tier()}));
        e.add(BookEl.link("sv", Lang.tr("sc.manual.energy.svhead")));
        e.add(BookEl.head("Industrial Upgrade")).addAll(BookContent.paras("sc.book.compat.iu"));
        e.add(BookEl.link("storagemods", Lang.tr("sc.manual.energy.storagemodshead")));
        e.add(BookEl.head("Not Enough Items")).addAll(BookContent.paras("sc.book.compat.nei"));
        e.add(BookEl.head("WAILA")).addAll(BookContent.paras("sc.book.compat.waila"));
        e.add(BookEl.head("Galacticraft")).addAll(BookContent.paras("sc.book.compat.gc", new Object[]{com.sc.util.ArmorGasSC.OXYGEN_PER_SECOND}));
        // the Energy Converter: which energies this game has (the same checks the block makes)
        ItemStack conv = new ItemStack(ModBlocks.energyConverter);
        e.add(BookEl.head(Lang.tr("sc.book.compat.convhead"), conv)).addAll(BookContent.paras("sc.book.compat.conv",
                new Object[]{com.sc.block.ItemBlockEnergyConverterSC.trimRate(com.sc.energy.ForeignEnergySC.Kind.RF.perEu()),
                        com.sc.block.ItemBlockEnergyConverterSC.trimRate(com.sc.energy.ForeignEnergySC.Kind.J.perEu()),
                        com.sc.block.ItemBlockEnergyConverterSC.trimRate(com.sc.energy.ForeignEnergySC.Kind.GJ.perEu())}));
        e.add(BookEl.dim(Lang.tr("sc.book.compat.conv.now", yesNo(com.sc.energy.ForeignEnergySC.rfApi()),
                yesNo(com.sc.energy.ForeignEnergySC.mekanism()), yesNo(com.sc.energy.ForeignEnergySC.galacticraft()))));
        e.add(BookEl.link("converter", Lang.tr("sc.manual.energy.convhead")));
        e.add(BookEl.head(Lang.tr("sc.book.compat.othershead"))).addAll(BookContent.paras("sc.book.compat.others",
                new Object[]{com.sc.tileentity.TileEntityFieldGeneratorSC.RF_PER_EU}));
        return e;
    }

    // ------------------------------------------------------------------ the owner and multiplayer

    private static BookEntry multiplayer(BookChapter c) {
        BookEntry e = new BookEntry("multiplayer", c, new ItemStack(Items.name_tag), Lang.tr("sc.book.multiplayer.title"));
        e.add(BookEl.title(Lang.tr("sc.book.multiplayer.title"))).add(BookEl.para(Lang.tr("sc.book.mp.intro")));
        e.add(BookEl.head(new ItemStack(ModBlocks.fieldGeneratorSC).getDisplayName(), new ItemStack(ModBlocks.fieldGeneratorSC)))
                .addAll(BookContent.paras("sc.book.mp.field", new Object[]{com.sc.tileentity.TileEntityFieldGeneratorSC.MAX_ACCESS}));
        e.add(BookEl.link("field.access", Lang.tr("sc.manual.field.accesshead")));
        e.add(BookEl.head(Lang.tr("sc.manual.machines.quarryhead"), new ItemStack(ModBlocks.quarrySC))).addAll(BookContent.paras("sc.book.mp.quarry"));
        e.add(BookEl.head(Lang.tr("sc.manual.energy.wirelesshead"), new ItemStack(ModBlocks.wirelessTx))).addAll(BookContent.paras("sc.book.mp.wireless"));
        e.add(BookEl.head(Lang.tr("sc.book.mp.bridgehead"), com.sc.block.BlockBridgeSC.stack(com.sc.block.BlockBridgeSC.CONTROLLER, 1)))
                .addAll(BookContent.paras("sc.book.mp.bridge", new Object[]{com.sc.bridge.BridgeMathSC.MAX_FRIENDS},
                        new Object[]{com.sc.bridge.BridgeMathSC.CONSENT_RADIUS, com.sc.bridge.BridgeMathSC.CONSENT_TICKS / 20}));
        e.add(BookEl.link("bridge.remote", Lang.tr("sc.book.bridge_remote.title")));
        e.add(BookEl.head(Lang.tr("sc.book.mp.otherhead"))).addAll(BookContent.paras("sc.book.mp.other"));
        return e;
    }

    // ------------------------------------------------------------------ the fluids and gases

    private static BookEntry fluids(BookChapter c) {
        String[] names = com.sc.item.ItemFluidBucketSC.FLUIDS;
        BookEntry e = new BookEntry("fluidlist", c, new ItemStack(ModItems.fluidBucket, 1, 0), Lang.tr("sc.book.fluidlist.title"));
        e.add(BookEl.title(Lang.tr("sc.book.fluidlist.title"))).add(BookEl.para(Lang.tr("sc.book.fluidlist.intro", names.length)));
        for (int i = 0; i < names.length; i++) {
            String n = names[i];
            String line = Lang.tr("sc.book.fluid.d." + n);
            if (com.sc.util.CorrosiveFluids.all().contains(n)) {
                line += " " + Lang.tr("sc.book.fluid.corrosive");
            }
            e.add(BookEl.item(new ItemStack(ModItems.fluidBucket, 1, i), BookContent.fluidName(n), line));
            String made = BookMaterialsSC.join(fluidMakers(n)), used = BookMaterialsSC.join(fluidUsers(n));
            if (made != null) {
                e.add(BookEl.dim(Lang.tr("sc.book.mat.made", made)));
            }
            if (used != null) {
                e.add(BookEl.dim(Lang.tr("sc.book.fluid.used", used)));
            }
        }
        e.add(BookEl.link("fluids", Lang.tr("sc.manual.energy.fluidshead"))).add(BookEl.link("corrosive", Lang.tr("sc.manual.safety.corrosivehead")))
                .add(BookEl.link("armorgases", Lang.tr("sc.manual.armor.gaseshead")));
        return e;
    }

    private static boolean is(FluidStack f, String name) {
        return f != null && f.getFluid() != null && name.equals(f.getFluid().getName());
    }

    private static Set<String> fluidMakers(String name) {
        Set<String> by = new LinkedHashSet<String>();
        for (MachineType type : MachineType.values()) {
            for (MachineRecipe r : RecipeRegistry.recipesFor(type)) {
                if (is(r.fluidOutputA, name) || is(r.fluidOutputB, name)) {
                    by.add(type.localizedName());
                }
            }
        }
        if ("singularmatter".equals(name)) {
            by.add(GeneratorType.SINGULAR_REACTOR.localizedName());
        }
        return by;
    }

    private static Set<String> fluidUsers(String name) {
        Set<String> by = new LinkedHashSet<String>();
        for (MachineType type : MachineType.values()) {
            for (MachineRecipe r : RecipeRegistry.recipesFor(type)) {
                if (is(r.fluidInputA, name) || is(r.fluidInputB, name)) {
                    by.add(type.localizedName());
                }
            }
        }
        for (GeneratorType g : GeneratorType.values()) {
            if (g != GeneratorType.CREATIVE && (name.equals(g.fuelFluidName) || name.equals(g.fuel2FluidName))) {
                by.add(g.localizedName());
            }
        }
        if (Arrays.asList(com.sc.tileentity.TileEntityGeneratorSC.PORT_FLUIDS).contains(name)) {
            by.add(GeneratorType.TOKAMAK_XV.localizedName());
        }
        if (Arrays.asList(com.sc.tileentity.SingularReactorSC.GASES).contains(name)) {
            by.add(GeneratorType.SINGULAR_REACTOR.localizedName());
        }
        for (com.sc.util.ArmorGasSC.Gas g : com.sc.util.ArmorGasSC.Gas.values()) {
            if (g.fluid.equals(name)) {
                by.add(Lang.tr("sc.book.fluid.armor"));
            }
        }
        for (com.sc.util.ArmorGasSC.Gas g : com.sc.bridge.BridgeMathSC.GASES) {
            if (g.fluid.equals(name)) {
                by.add(Lang.tr("sc.book.fluid.bridge"));
            }
        }
        return by;
    }

    // ------------------------------------------------------------------ every module

    private static BookEntry modules(BookChapter c) {
        BookEntry e = new BookEntry("modules", c, ModItems.upgrade.stackOf(UpgradeType.OVERCLOCKER), Lang.tr("sc.book.modules.title"));
        e.add(BookEl.title(Lang.tr("sc.book.modules.title"))).add(BookEl.para(Lang.tr("sc.book.modules.intro")));
        e.add(BookEl.head(Lang.tr("sc.book.modules.machines")));
        for (UpgradeType t : UpgradeType.values()) {
            ItemStack s = ModItems.upgrade.stackOf(t);
            e.add(BookEl.item(s, s.getDisplayName(), Lang.tr("sc.book.modules.where", upgradeWhere(t, s)) + " "
                    + Lang.tr("sc.upgrade.tooltip." + t.name().toLowerCase(Locale.ROOT))));
        }
        e.add(BookEl.link("upgrades", Lang.tr("sc.manual.machines.upgradeshead")));
        e.add(BookEl.head(Lang.tr("sc.book.quarry.modules")));
        for (ItemQuarryModuleSC.Kind k : ItemQuarryModuleSC.Kind.values()) {
            ItemStack s = ModItems.quarryModule.stackOf(k);
            e.add(BookEl.item(s, s.getDisplayName(), quarryModuleLine(k)));
        }
        e.add(BookEl.link("quarry.modules", Lang.tr("sc.book.quarry.modules")));
        e.add(BookEl.head(Lang.tr("sc.manual.bridge.modhead") + " - " + Lang.tr("sc.book.bridge.title")));
        for (int i = com.sc.block.BlockBridgeSC.NAV; i < com.sc.block.BlockBridgeSC.parts(); i++) {
            ItemStack s = com.sc.block.BlockBridgeSC.stack(i, 1);
            e.add(BookEl.item(s, s.getDisplayName(), BookContent.bridgeModuleLine(i)));
        }
        e.add(BookEl.link("bridge.places", Lang.tr("sc.book.bridge_places.title")));
        e.add(BookEl.head(Lang.tr("sc.book.modules.conv"), new ItemStack(ModBlocks.energyConverter)));
        e.add(BookEl.dim(Lang.tr("sc.book.modules.conv.upgrades")));
        for (com.sc.item.ItemConverterModuleSC.Kind k : com.sc.item.ItemConverterModuleSC.Kind.values()) {
            ItemStack s = ModItems.converterModule.stackOf(k);
            String line = Lang.tr("sc.conv.module.details." + k.key());
            if (k.max > 1) {
                line += " " + Lang.tr("sc.conv.module.max", k.max);
            }
            e.add(BookEl.item(s, s.getDisplayName(), line));
        }
        e.add(BookEl.link("converter", Lang.tr("sc.manual.energy.convhead")));
        e.add(BookEl.head(Lang.tr("sc.book.modules.other")));
        ItemStack[] other = {new ItemStack(ModItems.fieldLinkModule), new ItemStack(ModItems.bridgeLinkModule), new ItemStack(ModItems.tubeSpeedUpgrade),
                new ItemStack(ModItems.itemFilter), new ItemStack(ModItems.oreLens), new ItemStack(ModItems.areaCard), new ItemStack(ModItems.oreScanner),
                ModItems.armorChip.stackOf(com.sc.util.ChipType.values()[0], 1)};
        String[] keys = {"fieldlink", "bridgelink", "tubespeed", "filter", "orelens", "areacard", "orescanner", "chip"};
        for (int i = 0; i < other.length; i++) {
            e.add(BookEl.item(other[i], other[i].getDisplayName(), Lang.tr("sc.book.modules.o." + keys[i])));
        }
        e.add(BookEl.link("chips", Lang.tr("sc.manual.armor.chipshead")));
        return e;
    }

    /** "machines, generators, storages, the field generator, the stations" - where an upgrade goes (the slots' own checks). */
    private static String upgradeWhere(UpgradeType t, ItemStack s) {
        List<String> w = new ArrayList<String>();
        if (!t.generatorOnly() && !t.fieldOnly() && !t.storageOnly()) {
            w.add(Lang.tr("sc.book.modules.w.machines"));
        }
        if (t.forGenerators()) {
            w.add(Lang.tr("sc.book.modules.w.generators"));
        }
        if (com.sc.tileentity.TileEntityEnergyStorageSC.acceptsUpgrade(s)) {
            w.add(Lang.tr("sc.book.modules.w.storages"));
        }
        if (com.sc.tileentity.TileEntityFieldGeneratorSC.isFieldUpgrade(s)) {
            w.add(Lang.tr("sc.book.modules.w.field"));
        }
        if (com.sc.tileentity.TileEntityArmorStationSC.acceptsModule(s)) {
            w.add(Lang.tr("sc.book.modules.w.stations"));
        }
        if (com.sc.tileentity.TileEntityEnergyConverterSC.isModule(s)) {
            w.add(Lang.tr("sc.book.modules.w.converter"));
        }
        StringBuilder sb = new StringBuilder();
        for (String x : w) {
            sb.append(sb.length() > 0 ? ", " : "").append(x);
        }
        return sb.toString();
    }

    /** A quarry module's line: its tooltip, where it goes (the quarry / the rig, from which tier), how many count. */
    static String quarryModuleLine(ItemQuarryModuleSC.Kind k) {
        StringBuilder sb = new StringBuilder(Lang.tr("sc.quarrymodule.tooltip." + k.name().toLowerCase(Locale.ROOT)));
        if (k.scope == ItemQuarryModuleSC.EXO) {
            sentence(sb, Lang.tr("sc.quarrymodule.tooltip.exoonly"));
        } else if (k.scope == ItemQuarryModuleSC.QUARRY) {
            sentence(sb, Lang.tr("sc.quarrymodule.tooltip.quarryonly"));
        }
        if (k.minTier > 0) {
            sentence(sb, Lang.tr("sc.quarrymodule.tooltip.tier", Tier.values()[k.minTier].name()));
        }
        if (k.max > 1) {
            sentence(sb, Lang.tr("sc.book.qmod.max", k.max));
        }
        return sb.toString();
    }

    private static String yesNo(boolean b) {
        return Lang.tr(b ? "sc.book.compat.conv.yes" : "sc.book.compat.conv.no");
    }

    /** One more sentence: after a full stop just a space. */
    private static void sentence(StringBuilder sb, String s) {
        sb.append(sb.length() > 0 && sb.charAt(sb.length() - 1) == '.' ? " " : ". ").append(s);
    }

    // ------------------------------------------------------------------ machines: defects, the Exo rig

    /** Defects: the chance per machine (the highest of its recipes), what a defect gives, overheating, the Quality module. */
    static BookEntry defects(BookChapter c) {
        ItemStack q = ModItems.upgrade.stackOf(UpgradeType.QUALITY);
        ItemStack scrap = ModItems.dust.stackOf(com.sc.util.Material.SCRAP);
        BookEntry e = new BookEntry("defects", c, q, Lang.tr("sc.book.defects.title"));
        e.add(BookEl.title(Lang.tr("sc.book.defects.title"))).add(BookEl.items(BookContent.listOf(q, scrap)))
                .addAll(BookContent.paras("sc.book.defects", null, new Object[]{com.sc.tileentity.TileEntityMachineSC.getHeatCapacity()}, null,
                        new Object[]{UpgradeType.MAX_EFFECTIVE}));
        BookEl t = BookEl.table(Lang.tr("sc.book.defects.t.machine"), Lang.tr("sc.book.defects.t.chance"));
        for (MachineType type : MachineType.values()) {
            float max = 0;
            for (MachineRecipe r : RecipeRegistry.recipesFor(type)) {
                max = Math.max(max, r.defectChance);
            }
            if (max > 0) {
                t.row(BookContent.machineStack(type), type.localizedName(), Lang.tr("sc.book.defects.upto", BookContent.percent(max)));
            }
        }
        e.add(BookEl.head(Lang.tr("sc.book.defects.tablehead"))).add(t);
        for (MachineRecipe r : RecipeRegistry.recipesFor(MachineType.CRUSHER)) {
            if (r.inputs.length == 1 && BookMaterialsSC.same(scrap, r.inputs[0], false)) {
                e.add(BookEl.head(Lang.tr("sc.book.defects.recyclehead"))).add(BookEl.machine(r));
                break;
            }
        }
        BookContent.crafting(e, q);
        e.add(BookEl.link("upgrades", Lang.tr("sc.manual.machines.upgradeshead"))).add(BookEl.link("heat", Lang.tr("sc.manual.machines.heathead")));
        return e;
    }

    /** The Exo Drilling Rig: hauls ore up from the depths - its cost, lenses, modules and the chance of each ore. */
    static BookEntry exoRig(BookChapter c) {
        ItemStack rig = new ItemStack(ModBlocks.quarrySC, 1, 4);
        BookEntry e = new BookEntry("quarry.exo", c, rig, Lang.tr("sc.book.quarry.exo"));
        e.add(BookEl.head(rig.getDisplayName(), rig)).add(BookEl.dim(Tier.XV.name() + ", " + BookContent.num(TileEntityQuarrySC.EXO_BUFFER) + " EU"));
        e.addAll(BookContent.paras("sc.book.exo", new Object[]{BookContent.num(TileEntityQuarrySC.EXO_COST), BookContent.number((float) TileEntityQuarrySC.EXO_RATE)},
                new Object[]{com.sc.machine.ExoOreTableSC.LENS_BOOST + 1, TileEntityQuarrySC.BASE_LENSES, TileEntityQuarrySC.LENSES,
                        com.sc.machine.ExoOreTableSC.LENS_BOOST},
                null, null));
        List<com.sc.machine.ExoOreTableSC.Entry> ores = com.sc.machine.ExoOreTableSC.entries();
        int total = 0;
        for (com.sc.machine.ExoOreTableSC.Entry en : ores) {
            total += en.weight;
        }
        int boost = com.sc.machine.ExoOreTableSC.LENS_BOOST;
        BookEl t = BookEl.table(Lang.tr("sc.book.exo.t.ore"), Lang.tr("sc.book.exo.t.base"), Lang.tr("sc.book.exo.t.lens1"), Lang.tr("sc.book.exo.t.lens4"));
        for (com.sc.machine.ExoOreTableSC.Entry en : ores) {
            String one = "-", four = "-";
            if (en.lens >= 0) {
                one = pct(en.weight * (1 + boost), total - en.weight + en.weight * (1 + boost));
                four = pct(en.weight * (1 + 4 * boost), total - en.weight + en.weight * (1 + 4 * boost));
            }
            t.row(en.ore, BookMaterialsSC.name(en.ore), pct(en.weight, total), one, four);
        }
        e.add(BookEl.head(Lang.tr("sc.book.exo.tablehead"))).add(t);
        List<ItemStack> lenses = new ArrayList<ItemStack>();
        for (com.sc.util.OreEntry o : com.sc.util.OreEntry.values()) {
            lenses.add(new ItemStack(ModItems.oreLens, 1, o.meta()));
        }
        e.add(BookEl.head(Lang.tr("sc.book.exo.lenshead"))).add(BookEl.items(lenses));
        BookContent.crafting(e, rig);
        BookContent.crafting(e, new ItemStack(ModItems.oreLens, 1, 0));
        e.add(BookEl.link("quarry.modules", Lang.tr("sc.book.quarry.modules"))).add(BookEl.link("quarry", Lang.tr("sc.manual.machines.quarryhead")));
        e.about(rig, new ItemStack(ModItems.oreLens, 1, OreDictionary.WILDCARD_VALUE));
        return e;
    }

    private static String pct(int part, int whole) {
        String mark = Lang.trOr("sc.num.decimal", ".");
        return (whole <= 0 ? "0" : BookContent.percent(part / (float) whole).replace(".", mark.isEmpty() ? "." : mark)) + "%";
    }
}
