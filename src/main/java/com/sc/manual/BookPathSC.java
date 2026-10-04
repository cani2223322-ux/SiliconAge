package com.sc.manual;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.sc.energy.GeneratorType;
import com.sc.energy.Tier;
import com.sc.init.ModBlocks;
import com.sc.init.ModItems;
import com.sc.machine.MachineType;
import com.sc.util.ArmorSuit;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/**
 * К5: the "Path" chapter - the routes through the mod as ticked steps (the first-steps ticks:
 * a step's item in the inventory ticks it, BookProgressSC): the energy tiers LV..SV, the
 * reactors, the suits, radiation before the first reactor, what to do after the controller.
 * Each step names what it takes (from the enums) and links to its article.
 */
final class BookPathSC {

    static final GeneratorType[] REACTORS = {GeneratorType.FUSION_REACTOR, GeneratorType.TOKAMAK, GeneratorType.EXO_REACTOR,
            GeneratorType.TOKAMAK_XV, GeneratorType.SINGULAR_REACTOR};
    /** After the controller: a step id, its article. */
    static final String[][] AFTER = {{"chip", "chips"}, {"station", "machine.upgrade_station_mv"}, {"armorstation", "armorstation"},
            {"quarry", "quarry"}, {"field", "field"}, {"wireless", "wireless"}};
    /** The tiers' articles. */
    private static final String[] TIER_LINK = {"start", "cables", "machines", "gen.fusion_reactor", "gen.plasma_reactor", "gen.tokamak",
            "gen.tokamak_xv", "sv"};
    private static final String[] SUIT_LINK = {"suits", "armorgases", "armorstation", "singularstation"};

    private static String[] ids;
    /** The articles built so far (this chapter is built last - its links name the other articles). */
    private static List<BookEntry> built = new ArrayList<BookEntry>();

    private BookPathSC() {
    }

    /** Every step of the routes (ticked like the first steps). */
    static synchronized String[] stepIds() {
        if (ids == null) {
            List<String> l = new ArrayList<String>();
            for (Tier t : Tier.values()) {
                l.add("path.tier." + t.name().toLowerCase(Locale.ROOT));
            }
            for (GeneratorType g : REACTORS) {
                l.add("path.r." + g.name().toLowerCase(Locale.ROOT));
            }
            for (ArmorSuit s : ArmorSuit.values()) {
                l.add("path.s." + s.name().toLowerCase(Locale.ROOT));
            }
            for (String[] a : AFTER) {
                l.add("path.a." + a[0]);
            }
            ids = l.toArray(new String[l.size()]);
        }
        return ids;
    }

    /** What ticks step i: any of these in the inventory. */
    static ItemStack[] stepItems(int i) {
        int tiers = Tier.values().length, suits = ArmorSuit.values().length;
        if (i < tiers) {
            Tier t = Tier.values()[i];
            List<ItemStack> l = new ArrayList<ItemStack>();
            l.add(new ItemStack(ModBlocks.energyStorageSC, 1, t.ordinal()));
            for (GeneratorType g : GeneratorType.values()) {
                if (g.tier == t && g != GeneratorType.CREATIVE) {
                    l.add(ModBlocks.generatorStack(g, 1));
                }
            }
            for (MachineType m : MachineType.values()) {
                if (m.tier == t) {
                    l.add(BookContent.machineStack(m));
                }
            }
            return l.toArray(new ItemStack[l.size()]);
        }
        i -= tiers;
        if (i < REACTORS.length) {
            return new ItemStack[]{ModBlocks.generatorStack(REACTORS[i], 1)};
        }
        i -= REACTORS.length;
        if (i < suits) {
            com.sc.item.ItemArmorSC[] p = ModItems.ARMOR.get(ArmorSuit.values()[i]);
            ItemStack[] out = new ItemStack[p.length];
            for (int k = 0; k < p.length; k++) {
                out[k] = new ItemStack(p[k], 1, OreDictionary.WILDCARD_VALUE);
            }
            return out;
        }
        i -= suits;
        switch (i) {
            case 0: return new ItemStack[]{new ItemStack(ModItems.armorChip, 1, OreDictionary.WILDCARD_VALUE)};
            case 1: return new ItemStack[]{BookContent.machineStack(MachineType.UPGRADE_STATION_MV), BookContent.machineStack(MachineType.UPGRADE_STATION_HV),
                    BookContent.machineStack(MachineType.UPGRADE_STATION_EV)};
            case 2: return new ItemStack[]{new ItemStack(ModBlocks.armorStation)};
            case 3: return new ItemStack[]{new ItemStack(ModBlocks.quarrySC, 1, OreDictionary.WILDCARD_VALUE)};
            case 4: return new ItemStack[]{new ItemStack(ModBlocks.fieldGeneratorSC, 1, OreDictionary.WILDCARD_VALUE)};
            default: return new ItemStack[]{new ItemStack(ModBlocks.wirelessTx, 1, OreDictionary.WILDCARD_VALUE)};
        }
    }

    private static BookEl step(int i, String text) {
        return BookEl.check(stepIds()[i], stepItems(i)[0], text);
    }

    static void path(List<BookEntry> list) {
        built = list;
        BookChapter c = BookChapter.PATH;
        String[] arts = {"path.energy", "path.reactors", "path.suits", "path.radiation", "path.after"};
        BookEntry home = new BookEntry("path", c, new ItemStack(Items.compass), Lang.tr("sc.book.path.title"));
        home.add(BookEl.title(Lang.tr("sc.book.path.title"))).addAll(BookContent.paras("sc.book.path.intro"));
        home.add(BookEl.link("start", Lang.tr("sc.manual.intro.starthead")));
        for (String a : arts) {
            home.add(BookEl.link(a, Lang.tr("sc.book." + a.replace('.', '_'))));
        }
        home.add(BookEl.dim(Lang.tr("sc.book.steps.hint")));
        list.add(home);

        // the energy tiers
        int n = 0;
        BookEntry en = start(c, "path.energy", new ItemStack(ModBlocks.energyStorageSC, 1, Tier.EV.ordinal()));
        for (Tier t : Tier.values()) {
            String k = t.name().toLowerCase(Locale.ROOT);
            en.add(step(n, Lang.tr("sc.book.path.tier", t.name(), BookContent.num(t.getVoltage()))))
                    .add(BookEl.para(Lang.tr("sc.book.path.d." + k)));
            String what = tierContents(t);
            if (what != null) {
                en.add(BookEl.dim(what));
            }
            en.add(BookEl.link(TIER_LINK[t.ordinal()], linkTitle(TIER_LINK[t.ordinal()])));
            n++;
        }
        en.add(BookEl.gap()).add(BookEl.link("tiers", Lang.tr("sc.manual.intro.tiers")));
        list.add(en);

        // the reactors
        BookEntry re = start(c, "path.reactors", ModBlocks.generatorStack(GeneratorType.TOKAMAK, 1));
        re.add(BookEl.para(Lang.tr("sc.book.path.reactors.intro")));
        for (GeneratorType g : REACTORS) {
            String k = g.name().toLowerCase(Locale.ROOT);
            re.add(step(n, g.localizedName())).add(BookEl.para(Lang.tr("sc.book.path.d." + k)))
                    .add(BookEl.dim(Lang.tr("sc.book.path.rline", g == GeneratorType.TOKAMAK_XV ? g.tier.name() + " / " + Tier.SV.name() : g.tier.name(),
                            BookContent.num(g.euPerTick), BookContent.fuel(g))))
                    .add(BookEl.link("gen." + k, g.localizedName()));
            n++;
        }
        re.add(BookEl.gap()).add(BookEl.link("path.radiation", Lang.tr("sc.book.path_radiation")));
        list.add(re);

        // the suits
        BookEntry su = start(c, "path.suits", new ItemStack(ModItems.ARMOR.get(ArmorSuit.EXO)[1]));
        su.add(BookEl.para(Lang.tr("sc.book.path.suits.intro")));
        for (ArmorSuit s : ArmorSuit.values()) {
            String k = s.name().toLowerCase(Locale.ROOT);
            su.add(step(n, Lang.tr("sc.suit." + k))).add(BookEl.para(Lang.tr("sc.book.path.d.suit." + k)))
                    .add(BookEl.dim(Lang.tr("sc.book.path.sline", s.chargeTier.name(), BookContent.num(s.maxCharge))))
                    .add(BookEl.link(SUIT_LINK[s.ordinal()], linkTitle(SUIT_LINK[s.ordinal()])));
            n++;
        }
        su.add(BookEl.gap()).add(BookEl.link("chips", Lang.tr("sc.manual.armor.chipshead"))).add(BookEl.link("armorfn", Lang.tr("sc.manual.armor.fnhead")));
        list.add(su);

        // radiation before the first reactor
        BookEntry ra = start(c, "path.radiation", new ItemStack(ModItems.dosimeter));
        ra.addAll(BookContent.paras("sc.book.path.rad"));
        List<ItemStack> gear = new ArrayList<ItemStack>();
        gear.add(new ItemStack(ModItems.dosimeter));
        for (com.sc.item.ItemLeadSuitSC p : ModItems.leadSuit) {
            gear.add(new ItemStack(p));
        }
        gear.add(new ItemStack(ModBlocks.leadBlock));
        gear.add(new ItemStack(ModBlocks.leadGlass));
        gear.add(ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.RAD_SHIELDING));
        gear.add(new ItemStack(ModItems.radioprotector));
        gear.add(new ItemStack(ModBlocks.shower));
        ra.add(BookEl.items(gear));
        for (String l : new String[]{"radiation", "radprotect", "hazmat", "dose", "radcure", "gen.rtg"}) {
            ra.add(BookEl.link(l, linkTitle(l)));
        }
        list.add(ra);

        // after the controller
        BookEntry af = start(c, "path.after", ModItems.siliconMaterial.stackOf(com.sc.util.SiliconMaterial.CONTROLLER));
        af.add(BookEl.para(Lang.tr("sc.book.path.after.intro")));
        for (String[] a : AFTER) {
            af.add(step(n, Lang.tr("sc.book.path.step." + a[0]))).add(BookEl.para(Lang.tr("sc.book.path.d.after." + a[0])))
                    .add(BookEl.link(a[1], linkTitle(a[1])));
            n++;
        }
        af.add(BookEl.gap()).add(BookEl.para(Lang.tr("sc.book.path.after.more")))
                .add(BookEl.link("path.reactors", Lang.tr("sc.book.path_reactors"))).add(BookEl.link("bridge", Lang.tr("sc.book.bridge.title")))
                .add(BookEl.link("materials", Lang.tr("sc.book.mat.title")));
        list.add(af);
    }

    private static BookEntry start(BookChapter c, String id, ItemStack icon) {
        String title = Lang.tr("sc.book." + id.replace('.', '_'));
        return new BookEntry(id, c, icon, title).add(BookEl.title(title)).add(BookEl.dim(Lang.tr("sc.book.steps.hint")));
    }

    /** "Generators: ...; machines: ..." of a tier, straight from the enums (null for none). */
    private static String tierContents(Tier t) {
        StringBuilder gens = new StringBuilder(), machines = new StringBuilder();
        for (GeneratorType g : GeneratorType.values()) {
            if (g.tier == t && g != GeneratorType.CREATIVE) {
                gens.append(gens.length() > 0 ? ", " : "").append(g.localizedName());
            }
        }
        for (MachineType m : MachineType.values()) {
            if (m.tier == t) {
                machines.append(machines.length() > 0 ? ", " : "").append(m.localizedName());
            }
        }
        String s = (gens.length() > 0 ? Lang.tr("sc.book.path.gens", gens) : "")
                + (machines.length() > 0 ? (gens.length() > 0 ? " " : "") + Lang.tr("sc.book.path.machines", machines) : "");
        return s.isEmpty() ? null : s;
    }

    /** The title of the article a step links to. */
    static String linkTitle(String id) {
        BookEntry e = null;
        for (BookEntry x : built) {
            if (x.id.equals(id)) {
                e = x;
            }
        }
        return e != null ? e.title : Lang.tr("sc.book.path.more");
    }
}
