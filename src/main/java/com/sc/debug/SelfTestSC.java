package com.sc.debug;

import java.lang.reflect.Method;
import java.util.List;

import com.sc.init.ModFluids;
import com.sc.init.ModItems;
import com.sc.machine.MachineRecipe;
import com.sc.machine.MachineType;
import com.sc.machine.RecipeRegistry;
import com.sc.tileentity.TileEntityConduitBundleSC;
import com.sc.tileentity.TileEntityMachineSC;
import com.sc.util.Material;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/**
 * Developer self-check of the world-independent machine logic, run from SCMod.preInit only
 * when the JVM is started with -Dsc.selftest=true (never in normal play). Prints one
 * "[SC-TEST] PASS/FAIL ..." line per check plus a summary to the log.
 */
public final class SelfTestSC {

    private static int passed;
    private static int failed;

    private SelfTestSC() {
    }

    public static void run() {
        passed = 0;
        failed = 0;
        try {
            everyRecipeSelectsItself();
            kilnPrefersHeatResistantRubber();
            fluidsMatchInEitherTank();
            machinesRefuseForeignFluids();
            outputCheckDoesNotDoubleBookSlots();
            guiSyncCarriesFullInts();
            handbookPagesBuild();
            bookBuilds();
            bookTexts();
            fieldShapes();
            fluidDrops();
            energyStorage();
            storageModules();
            electricArmor();
            transformers();
            tierSV();
            tokamakSvOutput();
            singularReactor();
            matterCompressor();
            armorStation();
            energySplit();
            conduitBundles();
            machineSidesAndUpgrades();
            tubeFilters();
            portableTanks();
            armorFunctions();
            armorGases();
            singularArmor();
            singularFunctions();
            singularFunctions2b();
            singularLevels();
            singularStation();
            singularStage5();
            singularCrafts();
            auditFixes20261006();
            fullCheck20261006();
            fullFixes20261006();
            invUtilForeign();
            singToolsNetview();
            singToolsBooksearch();
            singToolsFormicons();
            singToolsFixes();
            singToolsPolish();
            singToolsUi();
            singToolsStation();
            singToolsDrill();
            singToolsBlade();
            singToolsStage1();
            bladeFunctions();
            chargePad();
            batteries();
            singularCableAndCore();
            batterySlot();
            generatorBattery();
            audit2Fixes();
            sounds();
            wireless();
            radiation();
            balanceConfig();
            smelters();
            vanillaOres();
            fixes20261007();
            loneIngredientRecipes();
            updateCheck();
            metalBlocks();
            electrolysisAndHeavyWater();
            drills();
            fieldExtras();
            bridge();
            bridge2();
            bridge3();
            bridgeRecipes();
            bridgeVortexLook();
            SelfTestConverterSC.run();
        } catch (Throwable t) {
            fail("exception: " + t);
            t.printStackTrace();
        }
        System.out.println("[SC-TEST] SUMMARY passed=" + passed + " failed=" + failed);
    }

    static void check(boolean ok, String what) {
        if (ok) {
            passed++;
            System.out.println("[SC-TEST] PASS " + what);
        } else {
            fail(what);
        }
    }

    private static void fail(String what) {
        failed++;
        System.out.println("[SC-TEST] FAIL " + what);
    }

    /** Fed exactly its own inputs, every recipe must be the one chosen (or one with identical inputs). */
    private static void everyRecipeSelectsItself() {
        int total = 0;
        int bad = 0;
        for (MachineType type : MachineType.values()) {
            for (MachineRecipe r : RecipeRegistry.recipesFor(type)) {
                total++;
                ItemStack[] slots = new ItemStack[TileEntityMachineSC.INPUT_SLOTS];
                for (int i = 0; i < r.inputs.length && i < slots.length; i++) {
                    slots[i] = r.inputs[i].copy();
                }
                MachineRecipe got = RecipeRegistry.findMatch(type, slots, copy(r.fluidInputA), copy(r.fluidInputB));
                if (got != r && (got == null || !sameInputs(got, r))) {
                    bad++;
                    System.out.println("[SC-TEST]   shadowed: " + type + " " + describe(r) + " -> " + (got == null ? "null" : describe(got)));
                }
            }
        }
        check(bad == 0, "every recipe selects itself (" + total + " recipes, " + bad + " shadowed)");
    }

    private static void kilnPrefersHeatResistantRubber() {
        ItemStack[] slots = {new ItemStack(ModItems.rubber, 16), copyN(ModItems.dust.stackOf(Material.ALUMINIUM), 64), null};
        MachineRecipe got = RecipeRegistry.findMatch(MachineType.KILN, slots, null, null);
        check(got != null && got.outputs[0].getItem() == ModItems.rubberHeatResist,
                "Kiln with rubber x16 + alumina x64 makes heat-resistant rubber");
    }

    private static void fluidsMatchInEitherTank() {
        FluidStack h2 = new FluidStack(ModFluids.hydrogen, 1000);
        FluidStack sihcl3 = new FluidStack(ModFluids.sihcl3, 1000);
        ItemStack[] none = new ItemStack[TileEntityMachineSC.INPUT_SLOTS];
        check(RecipeRegistry.findMatch(MachineType.CVD_CHAMBER, none, sihcl3, h2) != null, "CVD matches SiHCl3 in A + H2 in B");
        check(RecipeRegistry.findMatch(MachineType.CVD_CHAMBER, none, h2, sihcl3) != null, "CVD matches H2 in A + SiHCl3 in B (swapped)");
        check(RecipeRegistry.findMatch(MachineType.CVD_CHAMBER, none, h2, null) == null, "CVD does not run on H2 alone");
    }

    private static void machinesRefuseForeignFluids() {
        check(!RecipeRegistry.isValidFluidInput(MachineType.CRUSHER, FluidRegistry.WATER), "Crusher refuses water");
        check(!RecipeRegistry.isValidFluidInput(MachineType.CVD_CHAMBER, FluidRegistry.WATER), "CVD refuses water");
        check(RecipeRegistry.isValidFluidInput(MachineType.CVD_CHAMBER, ModFluids.hydrogen), "CVD accepts hydrogen");
        check(!RecipeRegistry.usesTank(MachineType.CRUSHER, 0), "Crusher input tank shown unused");
        check(RecipeRegistry.usesTank(MachineType.CVD_CHAMBER, 1), "CVD second input tank shown used");
        check(RecipeRegistry.usesTank(MachineType.CHLOR_ALKALI_ELECTROLYZER, 3), "Chlor-Alkali has a 2nd output fluid (goes out the top)");
        check(!RecipeRegistry.usesTank(MachineType.CHEM_REACTOR, 3), "Chem Reactor keeps a single fluid output face");
    }

    private static void outputCheckDoesNotDoubleBookSlots() throws Exception {
        TileEntityMachineSC te = new TileEntityMachineSC();
        int out = TileEntityMachineSC.INPUT_SLOTS;
        te.setInventorySlotContents(out, copyN(ModItems.dust.stackOf(Material.ZINC), 5));
        te.setInventorySlotContents(out + 1, copyN(ModItems.dustTiny.stackOf(Material.GALLIUM), 64));
        Method canInsertAll = TileEntityMachineSC.class.getDeclaredMethod("canInsertAll", ItemStack[].class);
        canInsertAll.setAccessible(true);
        ItemStack zn = ModItems.dust.stackOf(Material.ZINC);
        ItemStack ga = ModItems.dustTiny.stackOf(Material.GALLIUM);
        ItemStack in = ModItems.dustTiny.stackOf(Material.INDIUM);
        check(!(Boolean) canInsertAll.invoke(te, (Object) new ItemStack[]{zn, ga, in}),
                "Zn + Ga + In do NOT fit into [Zn 5, Ga 64, empty]");
        check((Boolean) canInsertAll.invoke(te, (Object) new ItemStack[]{zn, in}),
                "Zn + In fit into [Zn 5, Ga 64, empty]");
    }

    private static void guiSyncCarriesFullInts() throws Exception {
        Class<?> cls = Class.forName("com.sc.inventory.IntSyncSC");
        java.lang.reflect.Constructor<?> ctor = cls.getDeclaredConstructor(int.class);
        ctor.setAccessible(true);
        Object sync = ctor.newInstance(1);
        Method receive = cls.getDeclaredMethod("receive", int.class, int.class);
        Method value = cls.getDeclaredMethod("value", int.class);
        receive.setAccessible(true);
        value.setAccessible(true);
        boolean ok = true;
        for (int v : new int[]{0, 32767, 32768, 51200, 204800, 1000000, 65535, 65536}) {
            // What S31PacketWindowProperty does to each half on the wire: truncate to a short.
            receive.invoke(sync, 0, (int) (short) (v & 0xFFFF));
            receive.invoke(sync, 1, (int) (short) (v >>> 16));
            int got = (Integer) value.invoke(sync, 0);
            if (got != v) {
                ok = false;
                System.out.println("[SC-TEST]   sync " + v + " -> " + got);
            }
        }
        check(ok, "GUI sync round-trips values above 32767");
    }

    /** Every handbook chapter builds with enough text, and no untranslated key or "%" leaks through (the old tab check, on the illustrated book). */
    private static void handbookPagesBuild() {
        StringBuilder bad = new StringBuilder();
        int total = 0, hits = 0;
        for (com.sc.manual.BookChapter ch : com.sc.manual.BookChapter.values()) {
            StringBuilder text = new StringBuilder();
            for (com.sc.manual.BookEntry e : com.sc.manual.BookContent.chapter(ch)) {
                text.append(e.searchText());
                if (ch != com.sc.manual.BookChapter.RECIPES && e.searchText().contains("wafer")) {
                    hits++;
                }
            }
            int recipeCards = 0;
            if (ch == com.sc.manual.BookChapter.RECIPES) {
                for (com.sc.manual.BookEl el : com.sc.manual.BookContent.recipes("wafer", 40)) {
                    el.collectText(text);
                    recipeCards++;
                }
            }
            String[] lines = text.toString().split("\n");
            total += lines.length;
            if (ch == com.sc.manual.BookChapter.RECIPES ? recipeCards < 3 : lines.length < 5) {   // recipes are cards, not lines
                bad.append(ch).append(" too short; ");
            }
            for (String l : lines) {
                if (l.contains("sc.manual.") || l.contains("sc.book.") || l.contains("sc.biome.") || l.contains("sc.suit.") || l.contains("%d") || l.contains("%s")) {
                    bad.append(ch).append(": ").append(l).append("; ");
                }
            }
            if (Boolean.getBoolean("sc.selftest.print")) {
                for (String l : lines) {
                    System.out.println("[SC-PAGE] " + ch + " | " + l);
                }
            }
        }
        check(hits > 0, "handbook search 'wafer' finds articles (" + hits + ")");
        check(bad.length() == 0, "handbook: all " + com.sc.manual.BookChapter.values().length + " chapters build, " + total + " lines, no raw keys"
                + (bad.length() == 0 ? "" : " -> " + bad));
    }

    /** The illustrated handbook: every chapter has articles, no raw key leaks, items find their page, the recipe search works. */
    private static void bookBuilds() {
        StringBuilder bad = new StringBuilder();
        java.util.List<com.sc.manual.BookEntry> all = com.sc.manual.BookContent.all();
        for (com.sc.manual.BookChapter ch : com.sc.manual.BookChapter.values()) {
            if (com.sc.manual.BookContent.chapter(ch).isEmpty()) {
                bad.append(ch).append(" empty; ");
            }
            if (ch.title().startsWith("sc.")) {
                bad.append(ch).append(" title; ");
            }
        }
        java.util.Set<String> ids = new java.util.HashSet<String>();
        for (com.sc.manual.BookEntry e : all) {
            if (!ids.add(e.id)) {
                bad.append("dup ").append(e.id).append("; ");
            }
            if (e.els.isEmpty() || e.title.startsWith("sc.")) {
                bad.append(e.id).append(" empty/untitled; ");
            }
            String t = e.searchText();
            if (t.contains("sc.book.") || t.contains("sc.manual.") || t.contains("%d") || t.contains("%s")) {
                bad.append(e.id).append(" raw key; ");
            }
            for (com.sc.manual.BookEl el : e.els) {
                if (el.kind == com.sc.manual.BookEl.Kind.LINK && com.sc.manual.BookContent.byId(el.target) == null) {
                    bad.append(e.id).append(" -> ").append(el.target).append("; ");
                }
            }
        }
        com.sc.manual.BookEntry crusher = com.sc.manual.BookContent.entryFor(com.sc.manual.BookContent.machineStack(com.sc.machine.MachineType.CRUSHER));
        com.sc.manual.BookEntry xv = com.sc.manual.BookContent.entryFor(com.sc.init.ModBlocks.generatorStack(com.sc.energy.GeneratorType.TOKAMAK_XV, 1));
        com.sc.manual.BookEntry wafer = com.sc.manual.BookContent.entryFor(ModItems.siliconMaterial.stackOf(com.sc.util.SiliconMaterial.SI_WAFER));
        boolean found = crusher != null && crusher.id.equals("machine.crusher") && xv != null && wafer != null && wafer.id.equals("silicon");
        int recipes = com.sc.manual.BookContent.recipes("wafer", 40).size();
        com.sc.manual.BookContent.invalidate();
        check(bad.length() == 0 && found && recipes > 3 && all.size() > 80,
                "book: " + all.size() + " articles in " + com.sc.manual.BookChapter.values().length + " chapters, items find their page " + found
                        + ", 'wafer' -> " + recipes + (bad.length() == 0 ? "" : " -> " + bad));
    }

    /** Technical blocks / items with no page of their own: placed by the mod itself, or a lookup stand-in for NEI. */
    private static final String[] BOOK_TECHNICAL = {"bridgeVortex", "lightSC", "fluidDrop"};
    /**
     * Items and blocks that have no article (G finds nothing) yet - the next handbook task covers
     * them (components reference, new articles). Printed as INFO; shrink it as articles appear.
     */
    private static final String[] BOOK_NOT_YET = {};

    /**
     * К13: the handbook's texts - (a) every sc.manual.* / sc.book.* key in both en_US and ru_RU,
     * (b) no Cyrillic and no «» in the English ones, (c) the bridge article's keys, (d) every item and
     * block of the mod is on some article's `about` list, or on BOOK_TECHNICAL / BOOK_NOT_YET.
     */
    private static void bookTexts() {
        java.util.Map<String, String> en = langMap("en_US"), ru = langMap("ru_RU");
        // (a) the same book keys in both languages
        StringBuilder onlyOne = new StringBuilder();
        int bookKeys = 0;
        for (String[] pair : new String[][]{{"en_US", "ru_RU"}, {"ru_RU", "en_US"}}) {
            java.util.Map<String, String> from = pair[0].equals("en_US") ? en : ru, to = pair[0].equals("en_US") ? ru : en;
            for (String k : from.keySet()) {
                if (isBookKey(k)) {
                    bookKeys += pair[0].equals("en_US") ? 1 : 0;
                    if (!to.containsKey(k)) {
                        onlyOne.append(' ').append(pair[1]).append(" lacks ").append(k);
                    }
                }
            }
        }
        check(bookKeys > 600 && onlyOne.length() == 0, "book texts (a): " + bookKeys + " sc.manual.* / sc.book.* keys, each in en_US and ru_RU" + onlyOne);
        // (b) English is English
        StringBuilder cyr = new StringBuilder();
        for (java.util.Map.Entry<String, String> e : en.entrySet()) {
            if (isBookKey(e.getKey()) && (e.getValue().matches(".*[\\u0400-\\u04FF].*") || e.getValue().contains("«") || e.getValue().contains("»"))) {
                cyr.append(' ').append(e.getKey());
            }
        }
        check(cyr.length() == 0, "book texts (b): no Cyrillic letters and no «» quotes in the en_US handbook texts" + cyr);
        // (c) the bridge article: its headings and paragraphs in both languages
        String[] bridge = {"title", "about.1", "about.2", "groundhead", "front", "ground.1", "ground.4", "spacehead", "space.1", "space.2", "calibhead",
                "calib", "reshead", "t.ground", "t.space", "t.burst", "t.hold", "t.gases", "t.life", "tanks", "openhead", "open.1", "open.2", "cool",
                "coordhead", "coord.1", "coord.3", "modhead", "mod.1", "remotehead", "remote.1", "remote.3", "modeshead", "modes.1", "modes.2",
                "accesshead", "access.1", "access.2", "armourhead", "armour.1", "armour.3", "wearhead", "wear.1", "wear.2", "stabhead", "stab.1",
                "stab.4", "heathead", "heat.1", "heat.2", "famhead", "fam.1", "fam.3", "lookhead", "look.1"};
        StringBuilder noBridge = new StringBuilder();
        for (String k : bridge) {
            for (java.util.Map<String, String> m : new java.util.Map[]{en, ru}) {
                if (!m.containsKey("sc.manual.bridge." + k)) {
                    noBridge.append(' ').append(m == en ? "en_US:" : "ru_RU:").append(k);
                }
            }
        }
        java.util.List<com.sc.manual.BookEntry> bridgeChapter = com.sc.manual.BookContent.chapter(com.sc.manual.BookChapter.BRIDGE);
        int bridgeEls = 0;
        for (com.sc.manual.BookEntry e : bridgeChapter) {
            bridgeEls += e.els.size();
        }
        check(noBridge.length() == 0 && bridgeChapter.size() == 6 && bridgeEls > 60, "book texts (c): the bridge chapter - " + bridge.length
                + " keys in both languages, " + bridgeChapter.size() + " articles (6), " + bridgeEls + " elements" + noBridge);
        // (d) every item / block of the mod has an article (G opens it), or is listed as technical / not yet covered
        java.util.Set<String> covered = new java.util.HashSet<String>();
        for (com.sc.manual.BookEntry e : com.sc.manual.BookContent.all()) {
            for (ItemStack s : e.about) {
                covered.add(String.valueOf(net.minecraft.item.Item.itemRegistry.getNameForObject(s.getItem())));
            }
        }
        java.util.Set<String> technical = new java.util.HashSet<String>(java.util.Arrays.asList(BOOK_TECHNICAL));
        java.util.Set<String> notYet = new java.util.HashSet<String>(java.util.Arrays.asList(BOOK_NOT_YET));
        String prefix = com.sc.Reference.MODID + ":";
        java.util.List<String> uncovered = new java.util.ArrayList<String>(), waiting = new java.util.ArrayList<String>(),
                nowCovered = new java.util.ArrayList<String>();
        int mod = 0;
        for (Object o : net.minecraft.item.Item.itemRegistry.getKeys()) {
            String name = String.valueOf(o);
            if (!name.startsWith(prefix)) {
                continue;
            }
            mod++;
            String id = name.substring(prefix.length());
            if (covered.contains(name)) {
                if (notYet.contains(id)) {
                    nowCovered.add(id);
                }
            } else if (notYet.contains(id)) {
                waiting.add(id);
            } else if (!technical.contains(id)) {
                uncovered.add(id);
            }
        }
        java.util.Collections.sort(uncovered);
        java.util.Collections.sort(waiting);
        com.sc.manual.BookContent.invalidate();
        System.out.println("[SC-TEST] INFO book: not yet covered by an article (" + waiting.size() + "): " + waiting);
        if (!nowCovered.isEmpty()) {
            System.out.println("[SC-TEST] INFO book: covered now - drop from BOOK_NOT_YET: " + nowCovered);
        }
        int withArticle = 0;
        for (String c : covered) {
            withArticle += c.startsWith(prefix) ? 1 : 0;
        }
        check(mod > 50 && uncovered.isEmpty(), "book texts (d): " + mod + " items / blocks of the mod - " + withArticle
                + " have an article, " + waiting.size() + " on the not-yet list, " + technical.size() + " technical"
                + (uncovered.isEmpty() ? "" : "; with no article and on no list: " + uncovered));
        bookPart2();
    }

    /**
     * Handbook part 2: 14 chapters; the new articles (the Path, the Bridge chapter, the Components and Reference
     * chapters, the split articles) build, have no raw keys; the pictures are in the jar; every item of the
     * Components chapter opens an article with G; the quarry and the Exo rig have their own pages.
     */
    private static void bookPart2() {
        String[] ids = {"path", "path.energy", "path.reactors", "path.suits", "path.radiation", "path.after", "bridge", "bridge.res", "bridge.remote",
                "bridge.armour", "bridge.wear", "bridge.places", "materials", "mat.electronics", "mat.structure", "mat.power", "mat.ore", "mat.tools",
                "mat.cells", "keys", "compat", "multiplayer", "fluidlist", "modules", "defects", "quarry.exo", "quarry.modules", "bundles", "storagemods",
                "gen.singular_reactor.fuel", "gen.singular_reactor.safety", "singulararmor.schemes", "singulararmor.fn"};
        StringBuilder bad = new StringBuilder();
        for (String id : ids) {
            com.sc.manual.BookEntry e = com.sc.manual.BookContent.byId(id);
            if (e == null || e.els.size() < 3) {
                bad.append(' ').append(id).append(e == null ? " missing" : " empty");
            } else if (e.searchText().contains("sc.book.") || e.searchText().contains("sc.manual.") || e.searchText().contains("%s")
                    || e.searchText().contains("%d")) {
                bad.append(' ').append(id).append(" raw");
            }
        }
        int images = 0;
        StringBuilder noImage = new StringBuilder();
        for (com.sc.manual.BookEntry e : com.sc.manual.BookContent.all()) {
            for (com.sc.manual.BookEl el : e.els) {
                if (el.kind == com.sc.manual.BookEl.Kind.IMAGE) {
                    images++;
                    if (SelfTestSC.class.getResource("/assets/siliconage/textures/gui/book/" + el.image + ".png") == null) {
                        noImage.append(' ').append(el.image);
                    }
                }
            }
        }
        StringBuilder noG = new StringBuilder();
        for (com.sc.manual.BookEntry e : com.sc.manual.BookContent.chapter(com.sc.manual.BookChapter.MATERIALS)) {
            for (ItemStack s : e.about) {
                ItemStack probe = s.getItemDamage() == net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE ? new ItemStack(s.getItem(), 1, 0) : s;
                if (com.sc.manual.BookContent.entryFor(probe) == null) {
                    noG.append(' ').append(net.minecraft.item.Item.itemRegistry.getNameForObject(s.getItem()));
                }
            }
        }
        com.sc.manual.BookEntry rig = com.sc.manual.BookContent.entryFor(new ItemStack(com.sc.init.ModBlocks.quarrySC, 1, 4));
        com.sc.manual.BookEntry lv = com.sc.manual.BookContent.entryFor(new ItemStack(com.sc.init.ModBlocks.quarrySC, 1, 0));
        boolean quarries = rig != null && rig.id.equals("quarry.exo") && lv != null && lv.id.equals("quarry");
        int chapters = com.sc.manual.BookChapter.values().length;
        com.sc.manual.BookContent.invalidate();
        check(chapters == 14 && bad.length() == 0 && images >= 9 && noImage.length() == 0 && noG.length() == 0 && quarries,
                "book part 2: " + chapters + " chapters (14), " + ids.length + " new / split articles build with no raw keys, " + images
                        + " pictures (>= 9) all in the jar, every component's G finds its page, the quarry / Exo rig pages ("
                        + quarries + ")" + bad + (noImage.length() == 0 ? "" : "; no picture:" + noImage) + (noG.length() == 0 ? "" : "; no G:" + noG));
    }

    private static boolean isBookKey(String k) {
        return k.startsWith("sc.manual.") || k.startsWith("sc.book.");
    }

    /** A .lang file of the jar as key -> value (the first one wins, as in the game). */
    static java.util.Map<String, String> langMap(String lang) {
        java.util.Map<String, String> map = new java.util.HashMap<String, String>();
        java.io.InputStream in = SelfTestSC.class.getResourceAsStream("/assets/siliconage/lang/" + lang + ".lang");
        if (in == null) {
            return map;
        }
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(in, "UTF-8"));
            for (String line; (line = r.readLine()) != null; ) {
                int eq = line.indexOf('=');
                if (eq > 0 && !line.startsWith("#") && !map.containsKey(line.substring(0, eq))) {
                    map.put(line.substring(0, eq), line.substring(eq + 1));
                }
            }
            r.close();
        } catch (java.io.IOException e) {
            // an empty map: every key missing
        }
        return map;
    }

    /** Field Generator shapes: a square of 4 nodes 10 blocks apart, at y 64. */
    private static void fieldShapes() {
        java.util.List<int[]> nodes = new java.util.ArrayList<int[]>();
        nodes.add(new int[]{0, 64, 0});
        nodes.add(new int[]{10, 64, 0});
        nodes.add(new int[]{10, 64, 10});
        nodes.add(new int[]{0, 64, 10});
        com.sc.energy.FieldMode U = com.sc.energy.FieldMode.UNION, B = com.sc.energy.FieldMode.BOX, P = com.sc.energy.FieldMode.PRISM;
        check(com.sc.tileentity.FieldShapeSC.contains(U, nodes, 6, 2.5, 65, 2.5), "union r6: point near a node is inside");
        check(!com.sc.tileentity.FieldShapeSC.contains(U, nodes, 6, 5.5, 64.5, 5.5), "union r6: centre of a 10-block square is outside");
        check(com.sc.tileentity.FieldShapeSC.contains(U, nodes, 8, 5.5, 64.5, 5.5), "union r8: centre is inside");
        check(com.sc.tileentity.FieldShapeSC.contains(B, nodes, 2, 5.5, 64.5, 5.5), "box r2: centre is inside");
        check(!com.sc.tileentity.FieldShapeSC.contains(B, nodes, 2, 5.5, 80, 5.5), "box r2: 15 blocks above is outside");
        check(com.sc.tileentity.FieldShapeSC.contains(B, nodes, 256, 250.5, 64.5, 5.5), "box r256: 240 blocks out is inside");
        check(!com.sc.tileentity.FieldShapeSC.contains(B, nodes, 256, 280.5, 64.5, 5.5), "box r256: 270 blocks out is outside");
        check(com.sc.tileentity.FieldShapeSC.contains(P, nodes, 2, 5.5, 200, 5.5), "prism: full world height above the centre is inside");
        check(!com.sc.tileentity.FieldShapeSC.contains(P, nodes, 2, 20.5, 64.5, 5.5), "prism r2: 10 blocks outside the wall is outside");
        check(com.sc.tileentity.FieldShapeSC.clampRange(1000) == 256 && com.sc.tileentity.FieldShapeSC.clampRange(0) == 1, "range clamps to 1..256");
        check(com.sc.tileentity.FieldShapeSC.hull(nodes).size() == 4, "prism: hull of a square has 4 corners");
        // the Zone tab: height, anchors, offset
        java.util.List<int[]> one = new java.util.ArrayList<int[]>();
        one.add(new int[]{0, 64, 0});
        check(com.sc.tileentity.FieldShapeSC.contains(U, one, 8, 3, 0.5, 66.5, 0.5)
                        && !com.sc.tileentity.FieldShapeSC.contains(U, one, 8, 3, 0.5, 68, 0.5)
                        && com.sc.tileentity.FieldShapeSC.contains(U, one, 8, 3, 7.5, 64.5, 0.5),
                "zone: height 3 squashes the sphere (7 across in, 2 up in, 3.5 up out)");
        check(com.sc.tileentity.FieldShapeSC.contains(P, nodes, 2, 4, 5.5, 67, 5.5)
                && !com.sc.tileentity.FieldShapeSC.contains(P, nodes, 2, 4, 5.5, 200, 5.5), "zone: a prism with a height stops above it");
        java.util.List<int[]> mid = com.sc.tileentity.TileEntityFieldGeneratorSC.zoneNodesFor(nodes,
                com.sc.tileentity.TileEntityFieldGeneratorSC.ANCHOR_CENTRE, null, 0, 3, 0);
        check(mid.size() == 1 && mid.get(0)[0] == 5 && mid.get(0)[1] == 67 && mid.get(0)[2] == 5,
                "zone: the cluster centre of the square, 3 up, is (5, 67, 5)");
        java.util.List<int[]> moved = com.sc.tileentity.TileEntityFieldGeneratorSC.zoneNodesFor(nodes,
                com.sc.tileentity.TileEntityFieldGeneratorSC.ANCHOR_NODES, null, 4, 0, -2);
        check(moved.size() == 4 && moved.get(1)[0] == 14 && moved.get(1)[2] == -2 && nodes.get(1)[0] == 10,
                "zone: the offset moves the anchors, not the nodes");
        long v = com.sc.tileentity.FieldShapeSC.volume(B, one, 2, 0);
        check(v >= 60 && v <= 68, "zone: a box of radius 2 (4 x 4 x 4) holds ~64 blocks (" + v + ")");
        check(com.sc.tileentity.TileEntityFieldGeneratorSC.upkeepFor(1, 8, 8, U) == com.sc.tileentity.TileEntityFieldGeneratorSC.upkeepFor(1, 8, U),
                "zone: the upkeep with height = radius is the old one");
    }

    /** NEI fluid stand-ins: every recipe/generator fluid has a drop, and drop <-> fluid round-trips. */
    private static void fluidDrops() {
        List<String> names = com.sc.item.ItemFluidDropSC.names();
        boolean ok = names.size() > 10;
        for (String n : names) {
            net.minecraftforge.fluids.Fluid f = FluidRegistry.getFluid(n);
            ItemStack drop = com.sc.item.ItemFluidDropSC.stackOf(f);
            net.minecraftforge.fluids.FluidStack back = com.sc.item.ItemFluidDropSC.fluidOf(drop);
            if (f == null || drop == null || back == null || back.getFluid() != f) {
                ok = false;
                System.out.println("[SC-TEST]   drop round-trip failed for " + n);
            }
        }
        check(ok, "NEI fluid drops: " + names.size() + " fluids, all round-trip (" + names + ")");
        check(names.contains("ticl4") && names.contains("steam") && names.contains("water"), "drops cover TiCl4, steam, water");
        // buckets: an empty bucket fills from HCl into ours, ours drains back to 1000 mB and an empty bucket
        net.minecraftforge.fluids.FluidStack hcl = new net.minecraftforge.fluids.FluidStack(FluidRegistry.getFluid("hcl"), 1000);
        ItemStack full = net.minecraftforge.fluids.FluidContainerRegistry.fillFluidContainer(hcl, new ItemStack(net.minecraft.init.Items.bucket));
        net.minecraftforge.fluids.FluidStack in = net.minecraftforge.fluids.FluidContainerRegistry.getFluidForFilledItem(full);
        ItemStack empty = net.minecraftforge.fluids.FluidContainerRegistry.drainFluidContainer(full);
        check(full != null && full.getItem() == ModItems.fluidBucket && in != null && in.amount == 1000 && in.getFluid() == hcl.getFluid()
                        && empty != null && empty.getItem() == net.minecraft.init.Items.bucket
                        && com.sc.item.ItemFluidBucketSC.registered().size() >= 15,
                "fluid buckets: HCl fills an empty bucket, drains back; " + com.sc.item.ItemFluidBucketSC.registered().size()
                        + " of " + com.sc.item.ItemFluidBucketSC.FLUIDS.length + " registered");
    }

    /** Energy storage: capacity per tier, energy only in on the sides, out at the tier voltage. */
    private static void energyStorage() {
        com.sc.tileentity.TileEntityEnergyStorageSC te = new com.sc.tileentity.TileEntityEnergyStorageSC();
        te.setStorageTier(com.sc.energy.Tier.HV);
        te.setFacing(net.minecraftforge.common.util.ForgeDirection.NORTH);
        check(te.getMaxEnergyStored() == 4000000, "HV storage holds 4,000,000 EU");
        int front = te.receiveEnergy(net.minecraftforge.common.util.ForgeDirection.NORTH, 512, 500, false);
        int side = te.receiveEnergy(net.minecraftforge.common.util.ForgeDirection.EAST, 512, 500, false);
        check(front == 0 && side == 500, "storage refuses energy on its output face, takes it on a side (" + front + "/" + side + ")");
        te.setStoredFromItem(3000000);
        check(te.getEnergyStored() == 3000500 && te.offerableEnergy() == 512, "storage keeps item charge beyond the tier buffer and offers 512 EU/t");
        check(te.outputFaces().length == 1 && te.outputFaces()[0] == net.minecraftforge.common.util.ForgeDirection.NORTH, "storage outputs only through its front");
        // upgrades: transformer a tier up, +25% capacity each, overdrive +1 packet each; the comparator
        com.sc.tileentity.TileEntityEnergyStorageSC lv = new com.sc.tileentity.TileEntityEnergyStorageSC();
        int emptyLevel = lv.comparatorLevel();
        ItemStack two = ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.ENERGY_STORAGE);
        two.stackSize = 2;
        lv.setInventorySlotContents(com.sc.tileentity.TileEntityEnergyStorageSC.FIRST_UPGRADE_SLOT, two);
        lv.setInventorySlotContents(com.sc.tileentity.TileEntityEnergyStorageSC.FIRST_UPGRADE_SLOT + 1,
                ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.TRANSFORMER));
        lv.setInventorySlotContents(com.sc.tileentity.TileEntityEnergyStorageSC.FIRST_UPGRADE_SLOT + 2,
                ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.OVERDRIVE));
        lv.setStoredFromItem(30000);
        check(lv.getMaxEnergyStored() == 60000 && lv.outputTier() == com.sc.energy.Tier.MV && lv.packetsPerTick() == (com.sc.tileentity.TileEntityEnergyStorageSC.overdriveWorks() ? 2 : 1)
                        && emptyLevel == 0 && lv.comparatorLevel() == 1 + 14 * 30000 / 60000
                        && lv.isItemValidForSlot(com.sc.tileentity.TileEntityEnergyStorageSC.FIRST_UPGRADE_SLOT + 3,
                                ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.OVERDRIVE))
                                == com.sc.tileentity.TileEntityEnergyStorageSC.overdriveWorks()   // IC2 without IU: no overdrive
                        && !lv.isItemValidForSlot(com.sc.tileentity.TileEntityEnergyStorageSC.FIRST_UPGRADE_SLOT + 3,
                                ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.OVERCLOCKER)),
                "storage upgrades: 2x capacity +50% = 60000, transformer LV->MV, overdrive 2 packets, comparator " + lv.comparatorLevel());
        ItemStack boots = new ItemStack(com.sc.init.ModItems.ARMOR.get(com.sc.util.ArmorSuit.NANO)[3]);
        com.sc.item.ItemArmorSC.setCharge(boots, 1000);
        int got = lv.dischargeItem(boots, 32);
        check(got == 32 && com.sc.item.ItemArmorSC.chargeOf(boots) == 968
                        && com.sc.tileentity.TileEntityEnergyStorageSC.isDischargeable(boots),
                "storage discharge slot takes 32 EU a tick out of a charged suit piece (" + got + ")");
        // charge slots by tier, each charging on its own
        com.sc.tileentity.TileEntityEnergyStorageSC hv = new com.sc.tileentity.TileEntityEnergyStorageSC();
        hv.setStorageTier(com.sc.energy.Tier.HV);
        com.sc.tileentity.TileEntityEnergyStorageSC xv = new com.sc.tileentity.TileEntityEnergyStorageSC();
        xv.setStorageTier(com.sc.energy.Tier.XV);
        ItemStack b1 = new ItemStack(com.sc.init.ModItems.ARMOR.get(com.sc.util.ArmorSuit.NANO)[3]);
        ItemStack b2 = new ItemStack(com.sc.init.ModItems.ARMOR.get(com.sc.util.ArmorSuit.NANO)[2]);
        boolean thirdRefused = !hv.isItemValidForSlot(com.sc.tileentity.TileEntityEnergyStorageSC.chargeSlotIndex(2), b1.copy());
        hv.setInventorySlotContents(com.sc.tileentity.TileEntityEnergyStorageSC.chargeSlotIndex(0), b1);
        hv.setInventorySlotContents(com.sc.tileentity.TileEntityEnergyStorageSC.chargeSlotIndex(1), b2);
        hv.setStoredFromItem(10000);
        int before = hv.getEnergyStored();
        hv.chargeRoundForTest();
        check(new com.sc.tileentity.TileEntityEnergyStorageSC().chargeSlots() == 1 && hv.chargeSlots() == 2 && xv.chargeSlots() == 4
                        && thirdRefused && before - hv.getEnergyStored() == 1024
                        && com.sc.item.ItemArmorSC.chargeOf(b1) == 512 && com.sc.item.ItemArmorSC.chargeOf(b2) == 512,
                "charge slots by tier: LV 1, HV 2, XV 4; HV charges two pieces at 512 EU/t each (" + (before - hv.getEnergyStored()) + ")");
    }

    /**
     * The storage-only modules: Output Splitter (extra output faces set with the wrench, each its own
     * stream, 1% upkeep) and Adaptive Transformer (up to the output neighbour's limit, at most +2).
     */
    private static void storageModules() {
        com.sc.machine.UpgradeType[] types = com.sc.machine.UpgradeType.values();
        com.sc.machine.UpgradeType split = com.sc.machine.UpgradeType.OUTPUT_SPLITTER, adapt = com.sc.machine.UpgradeType.ADAPTIVE_TRANSFORMER;
        int up = com.sc.tileentity.TileEntityEnergyStorageSC.FIRST_UPGRADE_SLOT;
        check(split.ordinal() == types.length - 2 && adapt.ordinal() == types.length - 1
                        && com.sc.machine.UpgradeType.RAD_SHIELDING.ordinal() == types.length - 3
                        && split.storageOnly() && adapt.storageOnly() && !split.forGenerators() && !adapt.forGenerators()
                        && !split.fieldOnly() && !adapt.generatorOnly()
                        && !new com.sc.tileentity.TileEntityChargePadSC().isItemValidForSlot(up, ModItems.upgrade.stackOf(adapt)),
                "storage modules appended last (meta " + split.ordinal() + ", " + adapt.ordinal() + "), storages only, not in a charge pad");
        net.minecraftforge.common.util.ForgeDirection N = net.minecraftforge.common.util.ForgeDirection.NORTH,
                E = net.minecraftforge.common.util.ForgeDirection.EAST, W = net.minecraftforge.common.util.ForgeDirection.WEST,
                S = net.minecraftforge.common.util.ForgeDirection.SOUTH;
        // A: Output Splitter
        com.sc.tileentity.TileEntityEnergyStorageSC hv = new com.sc.tileentity.TileEntityEnergyStorageSC();
        hv.setStorageTier(com.sc.energy.Tier.HV);
        hv.setFacing(N);
        com.sc.tileentity.TileEntityEnergyStorageSC mv = new com.sc.tileentity.TileEntityEnergyStorageSC();
        mv.setStorageTier(com.sc.energy.Tier.MV);
        ItemStack two = ModItems.upgrade.stackOf(split);
        two.stackSize = 2;
        if (com.sc.tileentity.TileEntityEnergyStorageSC.overdriveWorks()) {
            boolean takes = hv.isItemValidForSlot(up, two) && !mv.isItemValidForSlot(up, two);
            hv.setInventorySlotContents(up, two);
            int a = hv.toggleExtraOutput(E), b = hv.toggleExtraOutput(W), c = hv.toggleExtraOutput(S), m = hv.toggleExtraOutput(N);
            int front = hv.receiveEnergy(E, 512, 100, false), side = hv.receiveEnergy(S, 512, 100, false);
            check(takes && a == com.sc.tileentity.TileEntityEnergyStorageSC.OUT_ADDED && b == com.sc.tileentity.TileEntityEnergyStorageSC.OUT_ADDED
                            && c == com.sc.tileentity.TileEntityEnergyStorageSC.OUT_FULL && m == com.sc.tileentity.TileEntityEnergyStorageSC.OUT_MAIN
                            && hv.outputFaces().length == 3 && hv.isOutputFace(E) && hv.isOutputFace(W) && !hv.isOutputFace(S)
                            && front == 0 && side == 100
                            && hv.packetsPerFace() == 1 && hv.packetsPerTick() == 3 && hv.outputTier().getVoltage() * hv.packetsPerTick() == 1536,
                    "splitter: HV storage with 2 takes 3 output faces, the 4th refused, MV refuses it; out 3 x 512 = "
                            + hv.outputTier().getVoltage() * hv.packetsPerTick() + " EU/t");
            hv.setInventorySlotContents(up + 1, ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.OVERDRIVE));
            int withOverdrive = hv.outputTier().getVoltage() * hv.packetsPerTick();
            hv.setStoredFromItem(100000);
            int e0 = hv.getEnergyStored();
            hv.sentOutForTest(E, 1000);
            int e1 = hv.getEnergyStored();
            hv.sentOutForTest(N, 1000);
            int e2 = hv.getEnergyStored();
            check(withOverdrive == 3 * 512 * 2 && e0 - e1 == 10 && e1 == e2,
                    "splitter + overdrive: 3 faces x 512 x 2 = " + withOverdrive + "; upkeep 1% on an extra face (" + (e0 - e1) + "), none on the front");
            net.minecraft.nbt.NBTTagCompound nbt = new net.minecraft.nbt.NBTTagCompound();
            hv.writeToNBT(nbt);
            com.sc.tileentity.TileEntityEnergyStorageSC copy = new com.sc.tileentity.TileEntityEnergyStorageSC();
            copy.readFromNBT(nbt);
            hv.decrStackSize(up, 1);                                // one splitter out: the face set last goes
            net.minecraft.nbt.NBTTagCompound old = new net.minecraft.nbt.NBTTagCompound();
            copy.writeToNBT(old);
            old.removeTag("OutFaces");
            old.removeTag("OutOrder");
            com.sc.tileentity.TileEntityEnergyStorageSC legacy = new com.sc.tileentity.TileEntityEnergyStorageSC();
            legacy.readFromNBT(old);
            check(copy.outputFaces().length == 3 && copy.isOutputFace(E) && copy.isOutputFace(W)
                            && hv.outputFaces().length == 2 && hv.isOutputFace(E) && !hv.isOutputFace(W)
                            && legacy.outputFaces().length == 1 && legacy.outputFaces()[0] == N,
                    "splitter: outputs survive NBT, a module out drops the last face set, old saves have the front only");
            copy.getStackInSlot(up).stackSize = 1;                  // part of the stack out past decrStackSize (a slot changed in place)
            copy.markDirty();
            check(copy.outputSplitters() == 1 && copy.outputFaces().length == 2 && copy.isOutputFace(E) && !copy.isOutputFace(W),
                    "splitter: a stack changed in place is caught by markDirty (" + copy.outputFaces().length + " outputs)");
        } else {
            check(!hv.isItemValidForSlot(up, two) && hv.outputSplitters() == 0,
                    "splitter: under IC2 without Industrial Upgrade a storage doesn't take it");
        }
        // B: Adaptive Transformer
        com.sc.tileentity.TileEntityEnergyStorageSC ad = new com.sc.tileentity.TileEntityEnergyStorageSC();
        ad.setStorageTier(com.sc.energy.Tier.HV);
        ad.setFacing(N);
        boolean takesAdaptive = ad.isItemValidForSlot(up, ModItems.upgrade.stackOf(adapt)) && mv.isItemValidForSlot(up, ModItems.upgrade.stackOf(adapt));
        ad.setInventorySlotContents(up, ModItems.upgrade.stackOf(adapt));
        ad.applyAdaptiveLimit(null, com.sc.tileentity.TileEntityEnergyStorageSC.ADAPT_NONE);
        com.sc.energy.Tier alone = ad.outputTier();
        // the mod's cable: no more than the weakest consumer on its network; no network known - no raise
        com.sc.energy.Tier HV = com.sc.energy.Tier.HV, EV = com.sc.energy.Tier.EV, LV = com.sc.energy.Tier.LV, XV = com.sc.energy.Tier.XV;
        com.sc.tileentity.TileEntityEnergyStorageSC.Limit fast = com.sc.tileentity.TileEntityEnergyStorageSC.cableLimit(XV, EV),
                thin = com.sc.tileentity.TileEntityEnergyStorageSC.cableLimit(LV, EV),
                empty = com.sc.tileentity.TileEntityEnergyStorageSC.cableLimit(XV, null);
        TileEntityConduitBundleSC loose = new TileEntityConduitBundleSC();
        loose.addPart(com.sc.conduit.ConduitKind.CABLE, com.sc.energy.CableType.SUPERCONDUCTOR.ordinal());
        com.sc.tileentity.TileEntityEnergyStorageSC.Limit off = com.sc.tileentity.TileEntityEnergyStorageSC.neighbourLimit(loose, N, ad);
        boolean byCable = fast.tier == EV && fast.why == com.sc.tileentity.TileEntityEnergyStorageSC.ADAPT_CONSUMER
                && thin.tier == LV && thin.why == com.sc.tileentity.TileEntityEnergyStorageSC.ADAPT_CABLE
                && empty.tier == null && off != null && off.tier == null;
        ad.applyAdaptiveLimit(fast.tier, fast.why);
        byCable &= ad.outputTier() == EV;
        ad.applyAdaptiveLimit(off == null ? null : off.tier, off == null ? 0 : off.why);
        byCable &= ad.outputTier() == HV;
        // a consumer of the mod's beside the front: its input tier
        com.sc.tileentity.TileEntityEnergyStorageSC sink = new com.sc.tileentity.TileEntityEnergyStorageSC();
        sink.setStorageTier(EV);
        sink.setFacing(N);                                          // takes on its south face, which touches our north
        com.sc.tileentity.TileEntityEnergyStorageSC.Limit sl = com.sc.tileentity.TileEntityEnergyStorageSC.neighbourLimit(sink, N, ad);
        ad.applyAdaptiveLimit(sl == null ? null : sl.tier, sl == null ? 0 : sl.why);
        boolean byConsumer = sl != null && sl.why == com.sc.tileentity.TileEntityEnergyStorageSC.ADAPT_CONSUMER && ad.outputTier() == EV;
        // IC2: behind a cable nothing can be seen - no raise; a sink its tier, none given (0) - no raise
        com.sc.tileentity.TileEntityEnergyStorageSC.Limit ic2Cable = com.sc.tileentity.TileEntityEnergyStorageSC.ic2Limit(true, false, 0, true),
                ic2Zero = com.sc.tileentity.TileEntityEnergyStorageSC.ic2Limit(false, true, 0, true),
                ic2Ev = com.sc.tileentity.TileEntityEnergyStorageSC.ic2Limit(false, true, 4, true);
        boolean byIc2 = ic2Cable.tier == null && ic2Zero.tier == null && ic2Ev.tier == EV
                && com.sc.tileentity.TileEntityEnergyStorageSC.ic2Limit(false, false, 0, false) == null;
        // with a plain Transformer: max(base with transformers, adaptive); the adaptive never passes the neighbour
        ad.setInventorySlotContents(up + 1, ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.TRANSFORMER));
        ad.applyAdaptiveLimit(LV, com.sc.tileentity.TileEntityEnergyStorageSC.ADAPT_CABLE);
        boolean withTransformer = ad.outputTier() == EV && ad.baseOutputTier() == EV;
        check(takesAdaptive && alone == HV && byCable && byConsumer && byIc2 && withTransformer,
                "adaptive: HV alone stays HV; mod cable min(cable, weakest consumer), no network - no raise; EV consumer -> EV;"
                        + " IC2 cable / sink tier 0 - no raise; + Transformer over an LV cable -> EV");
    }

    /** Electric armor: no protection while empty, EU paid per absorbed damage, charged by the storage slot. */
    private static void electricArmor() {
        com.sc.item.ItemArmorSC chest = com.sc.init.ModItems.ARMOR.get(com.sc.util.ArmorSuit.NANO)[1];
        ItemStack stack = new ItemStack(chest);
        net.minecraft.util.DamageSource hit = net.minecraft.util.DamageSource.cactus;   // "generic" bypasses armor
        net.minecraftforge.common.ISpecialArmor.ArmorProperties empty = chest.getProperties(null, stack, hit, 4, 2);
        check(empty.AbsorbRatio == 0 && chest.getArmorDisplay(null, stack, 2) == 0 && !chest.isDamageable(),
                "empty armor gives no protection and has no durability");
        com.sc.item.ItemArmorSC.setCharge(stack, 10000);
        net.minecraftforge.common.ISpecialArmor.ArmorProperties full = chest.getProperties(null, stack, hit, 4, 2);
        check(Math.abs(full.AbsorbRatio - 8 / 25.0) < 1e-9 && full.AbsorbMax == 25 * 10000 / 500 && chest.getArmorDisplay(null, stack, 2) == 8,
                "charged Nano chestplate absorbs 32% up to its charge (" + full.AbsorbRatio + ", " + full.AbsorbMax + ")");
        chest.damageArmor(null, stack, hit, 3, 2);
        check(com.sc.item.ItemArmorSC.chargeOf(stack) == 10000 - 3 * 500, "absorbing 3 damage costs 1500 EU");

        com.sc.tileentity.TileEntityEnergyStorageSC te = new com.sc.tileentity.TileEntityEnergyStorageSC();
        te.setStorageTier(com.sc.energy.Tier.MV);
        check(te.isItemValidForSlot(0, stack), "energy storage slot accepts armor");
        if (cpw.mods.fml.common.Loader.isModLoaded(com.sc.Reference.IC2_MODID)) {
            check(Ic2ArmorTest.charge(chest, stack), "IC2 charger: MV fills 128 EU per call, LV gives nothing, no discharge out");
        }
    }

    /** Transformer MV-HV: which faces take which voltage, and which way it sends energy out. */
    private static void transformers() {
        net.minecraftforge.common.util.ForgeDirection n = net.minecraftforge.common.util.ForgeDirection.NORTH;
        net.minecraftforge.common.util.ForgeDirection e = net.minecraftforge.common.util.ForgeDirection.EAST;
        com.sc.energy.Tier mv = com.sc.energy.Tier.MV, hv = com.sc.energy.Tier.HV;
        com.sc.tileentity.TileEntityTransformerSC t = new com.sc.tileentity.TileEntityTransformerSC();
        t.setLowTier(mv);
        t.setFacing(n);
        int front = t.receiveEnergy(n, 512, 512, false);
        int side = t.receiveEnergy(e, 128, 100, false);
        check(front == 512 && side == 0 && t.inputTier() == hv && t.outputTier() == mv
                        && t.outputFaces().length == 5 && !t.isOutputFace(n) && t.offerableEnergy() == 128,
                "step-down: HV in on the front, out one MV packet (128 EU/t) on the other 5 faces (" + front + "/" + side + ")");
        t.setStepUp(true);
        int sideUp = t.receiveEnergy(e, 128, 100, true);
        int frontUp = t.receiveEnergy(n, 128, 100, true);
        int overvolt = t.receiveEnergy(e, 512, 100, true);
        check(sideUp == 100 && frontUp == 0 && overvolt == 0 && t.outputTier() == hv
                        && t.outputFaces().length == 1 && t.outputFaces()[0] == n && t.offerableEnergy() == 512
                        && t.getMaxEnergyStored() == 1024,
                "step-up: MV in on the sides only (HV there refused), one HV packet (512 EU/t) out of the front, buffer 8 MV packets");
        t.setLowTier(com.sc.energy.Tier.XV);
        check(t.getLowTier() == com.sc.energy.Tier.XV && t.getHighTier() == com.sc.energy.Tier.SV, "XV-SV transformer: XV low, SV high");
        t.setLowTier(com.sc.energy.Tier.SV);
        check(t.getLowTier() == com.sc.energy.Tier.XV && t.getHighTier() == com.sc.energy.Tier.SV, "top transformer is XV-SV (SV as low clamps to it)");
        com.sc.tileentity.TileEntityEnergyStorageSC xv = new com.sc.tileentity.TileEntityEnergyStorageSC();
        xv.setStorageTier(com.sc.energy.Tier.XV);
        com.sc.tileentity.TileEntityEnergyStorageSC qv = new com.sc.tileentity.TileEntityEnergyStorageSC();
        qv.setStorageTier(com.sc.energy.Tier.QV);
        check(xv.getMaxEnergyStored() == 2000000000 && xv.outputTier().getVoltage() == 32768
                        && qv.getMaxEnergyStored() == 1000000000 && qv.outputTier().getVoltage() == 16384
                        && com.sc.energy.CableType.EXO.tier == com.sc.energy.Tier.XV
                        && com.sc.energy.Tier.QV.toIc2Tier() == 6 && com.sc.energy.Tier.IV.toIc2Tier() == 5
                        && com.sc.block.BlockTransformerSC.VARIANTS == 7,
                "tiers above EV: XV storage 2 000 000 000 EU / 32768 EU/t, QV half of it, cables, transformers, IC2 tiers");
    }

    /** SV (Singular): the top tier - order, IC2 numbers, the net's "any voltage", hidden content, overvoltage, ints. */
    @SuppressWarnings("unchecked")
    private static void tierSV() {
        com.sc.energy.Tier SV = com.sc.energy.Tier.SV, XV = com.sc.energy.Tier.XV, QV = com.sc.energy.Tier.QV;
        com.sc.energy.Tier[] all = com.sc.energy.Tier.values();
        check(all[all.length - 1] == SV && SV.ordinal() == 7 && com.sc.energy.Tier.max() == SV
                        && SV.getVoltage() == 131072 && SV.getBuffer() == 13107200,
                "SV is the last tier (ordinal 7), Tier.max(), 131072 EU/t, buffer 13 107 200");
        check(SV.toIc2Tier() == 7 && XV.toIc2Tier() == 6 && QV.toIc2Tier() == 6
                        && com.sc.energy.Tier.fromIc2Tier(7) == SV && com.sc.energy.Tier.fromIc2Tier(6) == XV
                        && com.sc.energy.Tier.fromIc2Tier(13) == SV && com.sc.energy.Tier.fromIc2Tier(5) == com.sc.energy.Tier.IV,
                "IC2 tiers: SV 7, QV / XV 6; back: 7+ SV, 6 XV, 5 IV");
        check(com.sc.energy.Tier.byOrdinal(99) == SV && com.sc.energy.Tier.byOrdinal(-1) == com.sc.energy.Tier.LV
                        && XV.up() == SV && SV.up() == SV,
                "byOrdinal clamps, up() stops at SV");
        // overvoltage: an SV packet is one tier over an XV cable / consumer (it burns / blows), two over QV
        check(XV.excessTiersOf(SV) == 1 && QV.excessTiersOf(SV) == 2 && SV.excessTiersOf(XV) == 0
                        && com.sc.energy.CableType.EXO.tier.excessTiersOf(SV) == 1,
                "SV over XV is one tier of overvoltage, over QV two, nothing the other way");
        // int headroom: SV voltage x the most packets x faces, the biggest capacities
        long worst = (long) SV.getVoltage() * (1 + com.sc.tileentity.TileEntityEnergyStorageSC.MAX_EXTRA_PACKETS) * 3;
        check(worst < Integer.MAX_VALUE && com.sc.tileentity.TileEntityEnergyStorageSC.CAPACITY.length == all.length
                        && com.sc.tileentity.TileEntityEnergyStorageSC.capacityOf(SV) >= com.sc.tileentity.TileEntityEnergyStorageSC.capacityOf(XV)
                        && com.sc.tileentity.TileEntityEnergyStorageSC.chargeSlotsFor(SV) == com.sc.tileentity.TileEntityEnergyStorageSC.MAX_CHARGE_SLOTS,
                "SV fits ints: voltage x packets x 3 faces = " + worst + ", a capacity per tier, SV storage >= XV, 4 charge slots");
        com.sc.tileentity.TileEntityEnergyStorageSC svs = new com.sc.tileentity.TileEntityEnergyStorageSC();
        svs.setStorageTier(SV);
        boolean svStore = svs.getMaxEnergyStored() > 0 && svs.outputTier() == SV && svs.adaptiveCeiling() == SV;
        for (int n = 1; n <= com.sc.machine.UpgradeType.MAX_EFFECTIVE; n++) {
            ItemStack caps = ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.ENERGY_STORAGE);
            caps.stackSize = n;
            svs.setInventorySlotContents(com.sc.tileentity.TileEntityEnergyStorageSC.FIRST_UPGRADE_SLOT, caps);
            svStore &= svs.getMaxEnergyStored() > 0;              // clamped, never wraps negative
        }
        check(svStore, "SV storage: SV out, adaptive ceiling SV, capacity upgrades clamp to int (" + svs.getMaxEnergyStored() + ")");
        // the wireless link of SV: no worse than XV
        check(com.sc.tileentity.TileEntityWirelessSC.range(SV) == Integer.MAX_VALUE
                        && com.sc.tileentity.TileEntityWirelessSC.blocksPerPercent(SV) >= com.sc.tileentity.TileEntityWirelessSC.blocksPerPercent(XV),
                "wireless SV: the whole dimension, losing no more than XV");
        // a Transformer upgrade lifts an XV storage no higher than outputRaiseCeiling (XV until SV content is in)
        com.sc.tileentity.TileEntityEnergyStorageSC xvs = new com.sc.tileentity.TileEntityEnergyStorageSC();
        xvs.setStorageTier(XV);
        xvs.setInventorySlotContents(com.sc.tileentity.TileEntityEnergyStorageSC.FIRST_UPGRADE_SLOT,
                ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.TRANSFORMER));
        check(xvs.baseOutputTier() == com.sc.energy.Tier.outputRaiseCeiling() && SV.raisedOutput(2) == SV
                        && QV.raisedOutput(5) == com.sc.energy.Tier.outputRaiseCeiling(),
                "Transformer upgrade: XV storage goes no higher than " + com.sc.energy.Tier.outputRaiseCeiling() + " (SV content ready: "
                        + com.sc.energy.Tier.SV_CONTENT_READY + ")");
        // SV blocks stay out of the creative tab / NEI until Tier.SV_CONTENT_READY
        List<ItemStack> st = new java.util.ArrayList<ItemStack>(), pad = new java.util.ArrayList<ItemStack>(),
                tr = new java.util.ArrayList<ItemStack>(), tx = new java.util.ArrayList<ItemStack>();
        com.sc.init.ModBlocks.energyStorageSC.getSubBlocks(net.minecraft.item.Item.getItemFromBlock(com.sc.init.ModBlocks.energyStorageSC), null, st);
        com.sc.init.ModBlocks.chargePadSC.getSubBlocks(net.minecraft.item.Item.getItemFromBlock(com.sc.init.ModBlocks.chargePadSC), null, pad);
        com.sc.init.ModBlocks.transformerSC.getSubBlocks(net.minecraft.item.Item.getItemFromBlock(com.sc.init.ModBlocks.transformerSC), null, tr);
        com.sc.init.ModBlocks.wirelessTx.getSubBlocks(net.minecraft.item.Item.getItemFromBlock(com.sc.init.ModBlocks.wirelessTx), null, tx);
        int shown = com.sc.energy.Tier.SV_CONTENT_READY ? all.length : all.length - 1;
        check(st.size() == shown && pad.size() == shown && tr.size() == shown - 1 && tx.size() == shown,
                "SV variants in creative only when SV_CONTENT_READY (" + com.sc.energy.Tier.SV_CONTENT_READY + "): storage " + st.size()
                        + ", pad " + pad.size() + ", transformers " + tr.size() + ", Tx " + tx.size());
    }

    /**
     * The Tokamak XV goes SV only into what takes SV (TileEntityGeneratorSC.tokamakOutputFor, on
     * the same neighbour limits as the Adaptive Transformer): XV with nothing beside it, beside an
     * XV cable / XV machine / IC2 cable / a cable with no network; SV beside a SV cable with SV
     * behind it or an IC2 sink of tier 7. A tile with no world stays XV, 2 packets of 32 768.
     */
    private static void tokamakSvOutput() {
        com.sc.energy.Tier SV = com.sc.energy.Tier.SV, XV = com.sc.energy.Tier.XV;
        com.sc.tileentity.TileEntityEnergyStorageSC.Limit svCable = com.sc.tileentity.TileEntityEnergyStorageSC.cableLimit(SV, SV),
                svCableXvMachine = com.sc.tileentity.TileEntityEnergyStorageSC.cableLimit(SV, XV),
                xvCable = com.sc.tileentity.TileEntityEnergyStorageSC.cableLimit(XV, SV),
                noNet = com.sc.tileentity.TileEntityEnergyStorageSC.cableLimit(SV, null),
                ic2Cable = com.sc.tileentity.TileEntityEnergyStorageSC.ic2Limit(true, false, 0, true),
                ic2Sv = com.sc.tileentity.TileEntityEnergyStorageSC.ic2Limit(false, true, 7, true),
                ic2Xv = com.sc.tileentity.TileEntityEnergyStorageSC.ic2Limit(false, true, 6, true);
        check(out() == XV && out((com.sc.tileentity.TileEntityEnergyStorageSC.Limit) null) == XV,
                "Tokamak XV: nothing beside it -> XV");
        check(out(xvCable) == XV && out(svCableXvMachine) == XV && out(noNet) == XV && out(ic2Cable) == XV && out(ic2Xv) == XV,
                "Tokamak XV: XV cable, SV cable with an XV machine on it, no network, IC2 cable, IC2 tier-6 sink -> XV");
        check(out(svCable) == SV && out(ic2Sv) == SV && out(svCable, null, ic2Sv) == SV,
                "Tokamak XV: SV cable with SV behind it, IC2 tier-7 sink -> SV");
        check(out(svCable, xvCable) == XV && out(ic2Sv, ic2Cable) == XV,
                "Tokamak XV: one weaker neighbour among SV ones -> XV");
        com.sc.tileentity.TileEntityGeneratorSC tok = new com.sc.tileentity.TileEntityGeneratorSC();
        tok.setGeneratorType(com.sc.energy.GeneratorType.TOKAMAK_XV);
        check(!tok.isSvOutput() && tok.outputTier() == XV && tok.packetsPerTick() == 2,
                "Tokamak XV without a world: XV, 2 packets of 32768 (" + tok.outputTier() + ", " + tok.packetsPerTick() + ")");
        int[] sync = tok.bigSync();
        sync[0] |= 128 << 24;                                    // the server says: SV
        tok.setBigClient(sync);
        check(tok.isSvOutput() && tok.outputTier() == SV && tok.packetsPerTick() == 1,
                "Tokamak XV at SV (synced flag): one packet of 131072 carries 65536 (" + tok.outputTier() + ", " + tok.packetsPerTick() + ")");
    }

    /**
     * The Singular Reactor's logic without a world: the type (last, SV, 700 million to light), the
     * output by mass and the 40-70% window, Auto holding 55%, the fixed feeds steering the mass,
     * evaporation under 5%, containment without helium, the screen's sync.
     */
    private static void singularReactor() {
        com.sc.energy.GeneratorType[] types = com.sc.energy.GeneratorType.values();
        com.sc.energy.GeneratorType sr = com.sc.energy.GeneratorType.SINGULAR_REACTOR;
        check(types[types.length - 1] == sr && sr.tier == com.sc.energy.Tier.SV && sr.euPerTick == 131072
                        && sr.ignitionThreshold() == 700000000L && sr.kind == com.sc.energy.GeneratorType.Kind.SINGULAR
                        && com.sc.init.ModBlocks.generatorTypeOf(com.sc.init.ModBlocks.generatorStack(sr, 1)) == sr,
                "singular reactor: the last generator type, SV 131072 EU/t, lit by 700 000 000 EU, its own block metadata");
        Class<com.sc.tileentity.SingularReactorSC> S = com.sc.tileentity.SingularReactorSC.class;
        boolean power = com.sc.tileentity.SingularReactorSC.outputFor(0.10) == 196608 && com.sc.tileentity.SingularReactorSC.outputFor(0.30) == 157286
                && com.sc.tileentity.SingularReactorSC.outputFor(0.55) == 131072 && com.sc.tileentity.SingularReactorSC.outputFor(0.85) == 78643
                && com.sc.tileentity.SingularReactorSC.MAX_OUTPUT == 196608;
        check(power && S != null, "singular reactor: output by mass - <20% 196608, 20-40% 157286, 40-70% 131072, >70% 78643 EU/t (x1.5 at most)");
        boolean window = com.sc.tileentity.SingularReactorSC.powerFactor(0.40) == 1.0 && com.sc.tileentity.SingularReactorSC.powerFactor(0.70) == 1.0
                && com.sc.tileentity.SingularReactorSC.powerFactor(0.399) == 1.2 && com.sc.tileentity.SingularReactorSC.powerFactor(0.701) == 0.6
                && com.sc.tileentity.SingularReactorSC.powerFactor(0.199) == 1.5;
        check(window, "singular reactor: the window 40-70% (both ends) is x1.0, just below x1.2, just above x0.6, under 20% x1.5");
        check(com.sc.tileentity.SingularReactorSC.HE_START == 8000
                        && com.sc.tileentity.SingularReactorSC.HE_START > com.sc.tileentity.SingularReactorSC.COMPRESS_TICKS * com.sc.tileentity.SingularReactorSC.HE_PER_TICK,
                "singular reactor: lighting needs 8000 mB of helium - more than the whole compression takes ("
                        + Math.round(com.sc.tileentity.SingularReactorSC.COMPRESS_TICKS * com.sc.tileentity.SingularReactorSC.HE_PER_TICK) + " mB)");
        double lo = simulateSingular(0.45, true, 1, 72000), hi = simulateSingular(0.68, true, 1, 72000);
        check(Math.abs(lo - 0.55) < 0.01 && Math.abs(hi - 0.55) < 0.01,
                "singular reactor: Auto holds the mass near 55% (from 45% -> " + Math.round(lo * 1000) / 10.0 + "%, from 68% -> "
                        + Math.round(hi * 1000) / 10.0 + "% in an hour)");
        double eco = simulateSingular(0.55, false, com.sc.tileentity.SingularReactorSC.MODE_ECO, 12000),
                force = simulateSingular(0.55, false, com.sc.tileentity.SingularReactorSC.MODE_FORCE, 12000);
        boolean ticks = Math.round(com.sc.tileentity.SingularReactorSC.CAPSULE_MASS / com.sc.tileentity.SingularReactorSC.feedRate(0, false, 0.5)) == 12000
                && Math.round(com.sc.tileentity.SingularReactorSC.CAPSULE_MASS / com.sc.tileentity.SingularReactorSC.feedRate(1, false, 0.5)) == 9000
                && Math.round(com.sc.tileentity.SingularReactorSC.CAPSULE_MASS / com.sc.tileentity.SingularReactorSC.feedRate(2, false, 0.5)) == 6000
                && Math.round(com.sc.tileentity.SingularReactorSC.CAPSULE_MASS / com.sc.tileentity.SingularReactorSC.evaporation(0.55)) == 9000;
        check(eco < 0.55 && force > 0.55 && ticks && com.sc.tileentity.SingularReactorSC.feedRate(1, true, 0.99) == 0,
                "singular reactor: a capsule lasts 10 / 7.5 / 5 min (Economy / Normal / Overdrive), Economy lets the mass fall ("
                        + Math.round(eco * 1000) / 10.0 + "%), Overdrive raises it (" + Math.round(force * 1000) / 10.0 + "%); nothing fed near full");
        double m = 0.55;
        int t = 0;
        while (m >= com.sc.tileentity.SingularReactorSC.EVAP_MASS && t < 200000) {
            m -= com.sc.tileentity.SingularReactorSC.evaporation(m);
            t++;
        }
        check(t > 20000 && t < 30000 && com.sc.tileentity.SingularReactorSC.evaporation(0.1) > com.sc.tileentity.SingularReactorSC.evaporation(0.5),
                "singular reactor: no capsules - the mass falls under 5% (evaporation, an accident) in " + t / 1200 + " min; lighter evaporates faster");
        float c = 100F;
        int secs = 0, warn40 = -1, argon = -1;
        while (c > 0F && secs < 100) {
            c = Math.max(0F, c + com.sc.tileentity.SingularReactorSC.containmentDelta(true, false, true, 0.55));
            secs++;
            if (warn40 < 0 && c < 40F) {
                warn40 = secs;
            }
            if (argon < 0 && c < com.sc.tileentity.SingularReactorSC.CONT_ARGON) {
                argon = secs;
            }
        }
        check(com.sc.tileentity.SingularReactorSC.containmentDelta(false, false, true, 0.55) > 0F && warn40 > 10 && argon > warn40 && secs == 25
                        && com.sc.tileentity.SingularReactorSC.containmentDelta(false, false, true, 0.1) < 0F,
                "singular reactor: without helium containment falls (40% after " + warn40 + " s, argon after " + argon + " s, ejection at " + secs
                        + " s); in the norm it recovers; a light hole loses it");
        com.sc.tileentity.TileEntityGeneratorSC g = new com.sc.tileentity.TileEntityGeneratorSC();
        g.setGeneratorType(sr);
        com.sc.tileentity.SingularReactorSC s = g.getSingular();
        int[] sync = g.bigSync();
        sync[1] = 423456;                                         // the server says: 42.3456%
        sync[2] = 7700;
        com.sc.tileentity.TileEntityGeneratorSC client = new com.sc.tileentity.TileEntityGeneratorSC();
        client.setGeneratorType(sr);
        client.setBigClient(sync);
        net.minecraft.item.Item cap = ModItems.component("matterCapsule");
        boolean capsuleOk = cap == null || g.isItemValidForSlot(com.sc.tileentity.TileEntityGeneratorSC.SLOT_FUEL, new ItemStack(cap));
        check(s != null && !s.canLight() && !g.isEnergySink() && !g.isEnergySource() && g.outputTier() == com.sc.energy.Tier.SV
                        && !com.sc.tileentity.TileEntityGeneratorSC.hasUpgradeSlots(sr) && com.sc.tileentity.TileEntityGeneratorSC.usesSlot(sr, 0)
                        && !com.sc.tileentity.TileEntityGeneratorSC.usesSlot(sr, 1) && capsuleOk
                        && !g.isItemValidForSlot(com.sc.tileentity.TileEntityGeneratorSC.SLOT_FUEL, new ItemStack(ModItems.deuteriumCell))
                        && Math.abs(client.getSingular().getMass() - 0.423456) < 1e-6 && client.getSingular().getContainment() == 77F
                        && sync.length == com.sc.tileentity.TileEntityGeneratorSC.SYNC_SIZE
                        && com.sc.tileentity.TileEntityGeneratorSC.radiationBase(sr) == 9F && com.sc.tileentity.TileEntityGeneratorSC.radiationRadius(sr) == 20,
                "singular reactor tile: no world - can't light, no sink (the charge comes from the port storages), SV, no upgrade slots, "
                        + "capsules only (" + (cap == null ? "capsule not registered yet" : "capsule fits") + "), the screen's sync, radiation 9 to 20 blocks");
        int roles = 0;
        for (int dy = -2; dy <= 2; dy++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dx = -3; dx <= 3; dx++) {
                    roles += com.sc.tileentity.SingularReactorSC.cellRole(dx, dy, dz) == 2 ? 1 : 0;
                }
            }
        }
        com.sc.manual.BookEntry be = com.sc.manual.BookContent.entryFor(com.sc.init.ModBlocks.generatorStack(sr, 1));
        com.sc.manual.BookEntry coilEntry = com.sc.manual.BookContent.entryFor(new ItemStack(com.sc.init.ModBlocks.gravityCoil));
        check(roles == 16 && com.sc.tileentity.SingularReactorSC.wallIndex(3, 3) == 23 && com.sc.tileentity.SingularReactorSC.ringIndex(1, 1) == 7
                        && be != null && coilEntry == be,
                "singular reactor build: 16 coil places (2 rings of 8), 24 wall cells a level, the handbook finds the reactor and its coil");
    }

    /** The mass after `ticks` of feeding (an endless supply of capsules) and evaporation. */
    private static double simulateSingular(double mass, boolean auto, int mode, int ticks) {
        double m = mass;
        for (int i = 0; i < ticks; i++) {
            m = Math.min(1.0, m + com.sc.tileentity.SingularReactorSC.feedRate(mode, auto, m));
            m -= com.sc.tileentity.SingularReactorSC.evaporation(m);
        }
        return m;
    }

    private static com.sc.energy.Tier out(com.sc.tileentity.TileEntityEnergyStorageSC.Limit... limits) {
        return com.sc.tileentity.TileEntityGeneratorSC.tokamakOutputFor(java.util.Arrays.asList(limits));
    }

    /** Cable network arithmetic: even shares, line loss, the rating cap, drawing from several generators. */
    private static void energySplit() {
        int[] even = com.sc.energy.EnergySplitSC.split(100, new int[]{30, 100, 100}, new int[]{0, 0, 0});
        check(even[0] == 30 && even[1] == 35 && even[2] == 35, "network shares evenly: 100 EU -> 30 (all it needs) / 35 / 35");
        int[] lossy = com.sc.energy.EnergySplitSC.split(100, new int[]{100, 100}, new int[]{0, 10});
        check(lossy[0] == 50 && lossy[1] == 40, "a consumer 10 blocks away gets its share minus the line loss (50 / 40)");
        int[] far = com.sc.energy.EnergySplitSC.split(20, new int[]{100, 100}, new int[]{0, 15});
        check(far[0] == 20 && far[1] == 0, "a share that wouldn't cover the line loss goes to the others");
        int[] odd = com.sc.energy.EnergySplitSC.split(10, new int[]{100, 100, 100}, new int[]{0, 0, 0});
        check(odd[0] + odd[1] + odd[2] == 10, "no EU lost to rounding (10 over 3 consumers)");
        int[] many = com.sc.energy.EnergySplitSC.split(32, new int[]{100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100},
                new int[]{3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3});
        int got = 0;
        for (int v : many) {
            got += v;
        }
        check(got >= 15, "32 EU over 11 machines 3 blocks away: fewer machines, less loss (" + got + " EU delivered)");
        int[] crowdDemand = new int[40];
        java.util.Arrays.fill(crowdDemand, 100);
        int[] crowd = com.sc.energy.EnergySplitSC.split(32, crowdDemand, new int[40]);
        int crowdSum = 0;
        for (int v : crowd) {
            crowdSum += v;
        }
        check(crowdSum == 32, "32 EU over 40 machines: nothing lost when there's less than 1 EU each (" + crowdSum + ")");
        int[] lone = com.sc.energy.EnergySplitSC.split(100, new int[]{500}, new int[]{60});
        check(lone[0] == 40, "a lone machine 60 blocks away still gets what gets through (40 of 100)");
        int[] taken = com.sc.energy.EnergySplitSC.draw(90, new int[]{100, 50});
        check(taken[0] == 60 && taken[1] == 30, "generators give in proportion to their offer (60 / 30)");
    }

    /** Bundle slots, side modes, parts and saving. */
    private static void conduitBundles() {
        com.sc.conduit.ConduitKind[] kinds = com.sc.conduit.ConduitKind.values();
        boolean apart = true;
        for (int i = 0; i < kinds.length; i++) {
            apart &= kinds[i].lo() >= 0 && kinds[i].hi() <= 1;
            for (int j = i + 1; j < kinds.length; j++) {
                apart &= Math.abs(kinds[i].offset - kinds[j].offset) >= 2 * com.sc.conduit.ConduitKind.HALF_WIDTH - 1e-6;
            }
        }
        check(apart, "cable, pipe and tube lanes don't overlap and stay inside the block");

        com.sc.conduit.ConduitMode n = com.sc.conduit.ConduitMode.NORMAL;
        com.sc.conduit.ConduitKind cable = com.sc.conduit.ConduitKind.CABLE, pipe = com.sc.conduit.ConduitKind.PIPE,
                tube = com.sc.conduit.ConduitKind.TUBE;
        com.sc.conduit.ConduitMode E = com.sc.conduit.ConduitMode.EXTRACT, I = com.sc.conduit.ConduitMode.INSERT,
                O = com.sc.conduit.ConduitMode.OFF, B = com.sc.conduit.ConduitMode.BOTH;
        boolean cycles = n.next(cable, true) == E && E.next(cable, true) == I && I.next(cable, true) == O && O.next(cable, true) == n
                && n.next(pipe, true) == E && E.next(pipe, true) == B && B.next(pipe, true) == O && O.next(pipe, true) == n
                && n.step(tube, true, -1) == O && I.next(tube, true) == E
                && n.next(tube, false) == O && O.next(tube, false) == n
                && n.extracts(cable) && n.inserts(cable) && !I.extracts(cable) && !E.inserts(cable)
                && !n.extracts(pipe) && n.inserts(pipe) && B.extracts(tube) && B.inserts(tube) && !O.inserts(tube);
        check(cycles, "Ender IO modes: cable in-out/extract/insert/off, pipe and tube insert/extract/in-out/off, links on/off");
        check(com.sc.conduit.RedstoneMode.WITH_SIGNAL.allows(true) && !com.sc.conduit.RedstoneMode.WITH_SIGNAL.allows(false)
                        && com.sc.conduit.RedstoneMode.WITHOUT_SIGNAL.allows(false) && !com.sc.conduit.RedstoneMode.NEVER.allows(true)
                        && com.sc.conduit.RedstoneMode.ALWAYS.allows(false) && com.sc.conduit.RedstoneMode.NEVER.step(1) == com.sc.conduit.RedstoneMode.ALWAYS,
                "redstone control: always / with signal / without / never");

        TileEntityConduitBundleSC b = new TileEntityConduitBundleSC();
        boolean added = b.addPart(cable, com.sc.energy.CableType.TUNGSTEN.ordinal()) && b.addPart(pipe, com.sc.util.PipeType.PTFE.ordinal())
                && b.addPart(tube, 0) && !b.addPart(cable, 0);
        check(added && b.getCable() == com.sc.energy.CableType.TUNGSTEN && b.getPipe() == com.sc.util.PipeType.PTFE && b.hasTube(),
                "a bundle takes one cable, one pipe and one tube, and no second cable");
        b.setMode(pipe, net.minecraftforge.common.util.ForgeDirection.EAST, com.sc.conduit.ConduitMode.EXTRACT);
        net.minecraftforge.common.util.ForgeDirection w = net.minecraftforge.common.util.ForgeDirection.WEST;
        b.setRedstoneMode(tube, w, com.sc.conduit.RedstoneMode.WITHOUT_SIGNAL);
        b.setExtractColor(tube, w, 14);
        b.setInsertColor(tube, w, 17);
        b.setPriority(tube, w, 250);
        b.setRoundRobin(tube, w, false);
        b.fill(net.minecraftforge.common.util.ForgeDirection.UNKNOWN, new FluidStack(FluidRegistry.WATER, 200), true);
        net.minecraft.nbt.NBTTagCompound nbt = new net.minecraft.nbt.NBTTagCompound();
        b.writeToNBT(nbt);
        TileEntityConduitBundleSC c = new TileEntityConduitBundleSC();
        c.readFromNBT(nbt);
        check(c.getCable() == com.sc.energy.CableType.TUNGSTEN && c.getPipe() == com.sc.util.PipeType.PTFE && c.hasTube()
                        && c.mode(pipe, net.minecraftforge.common.util.ForgeDirection.EAST) == com.sc.conduit.ConduitMode.EXTRACT
                        && c.getFluid() != null && c.getFluid().amount == 200
                        && c.redstoneMode(tube, w) == com.sc.conduit.RedstoneMode.WITHOUT_SIGNAL && c.extractColor(tube, w) == 14
                        && c.insertColor(tube, w) == 1 && c.priority(tube, w) == 99 && !c.roundRobin(tube, w)
                        && c.insertColor(tube, net.minecraftforge.common.util.ForgeDirection.UP) == TileEntityConduitBundleSC.DEFAULT_COLOR
                        && c.roundRobin(tube, net.minecraftforge.common.util.ForgeDirection.UP),
                "bundle saves parts, modes, fluid and connector options (redstone, channels, priority capped at 99, round robin)");
        ItemStack drop = c.removePart(cable);
        check(drop != null && drop.getItem() == net.minecraft.item.Item.getItemFromBlock(com.sc.init.ModBlocks.cableSC)
                        && drop.getItemDamage() == com.sc.energy.CableType.TUNGSTEN.ordinal() && c.getCable() == null && !c.isEmpty(),
                "breaking the cable out gives back the tungsten cable item, the rest stays");
    }

    /** Machines work from every side; upgrades change time, energy, voltage and buffer the IC2 way. */
    private static void machineSidesAndUpgrades() {
        TileEntityMachineSC m = new TileEntityMachineSC();
        m.setMachineType(MachineType.CVD_CHAMBER);
        boolean everySide = true;
        for (int side = 0; side < 6; side++) {
            everySide &= m.getAccessibleSlotsFromSide(side).length == TileEntityMachineSC.INPUT_SLOTS + TileEntityMachineSC.OUTPUT_SLOTS + 1   // + the battery
                    && m.canExtractItem(TileEntityMachineSC.INPUT_SLOTS, null, side)
                    && !m.canExtractItem(0, null, side)
                    && m.canFill(net.minecraftforge.common.util.ForgeDirection.getOrientation(side), ModFluids.hydrogen)
                    && m.canDrain(net.minecraftforge.common.util.ForgeDirection.getOrientation(side), null);
        }
        int filled = m.fill(net.minecraftforge.common.util.ForgeDirection.SOUTH, new FluidStack(ModFluids.hydrogen, 1000), true);
        check(everySide && filled == 1000, "machine: every side takes ingredients and gives products (the old output-only front too)");

        TileEntityMachineSC c = new TileEntityMachineSC();
        c.setMachineType(MachineType.CRUSHER);
        MachineRecipe r = RecipeRegistry.recipesFor(MachineType.CRUSHER).get(0);
        int baseTicks = c.effectiveTicks(r), baseEu = c.effectiveEuPerTick(), baseMax = c.getMaxEnergyStored();
        com.sc.energy.Tier baseIn = c.inputTier();
        ItemStack oc = ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.OVERCLOCKER);
        oc.stackSize = 2;
        c.setInventorySlotContents(TileEntityMachineSC.FIRST_UPGRADE_SLOT, oc);
        c.setInventorySlotContents(TileEntityMachineSC.FIRST_UPGRADE_SLOT + 1, ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.TRANSFORMER));
        c.setInventorySlotContents(TileEntityMachineSC.FIRST_UPGRADE_SLOT + 2, ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.ENERGY_STORAGE));
        check(baseTicks == r.ticks && c.effectiveTicks(r) == Math.max(1, (int) Math.round(r.ticks * 0.49))
                        && c.effectiveEuPerTick() == (int) Math.ceil(MachineType.CRUSHER.euPerTick * 2.56)
                        && c.inputTier().ordinal() == baseIn.ordinal() + 1 && c.getMaxEnergyStored() == baseMax + 10000
                        && baseEu == MachineType.CRUSHER.euPerTick,
                "upgrades: 2 overclockers x0.49 time / x2.56 energy, transformer +1 tier, storage +10000 EU ("
                        + baseTicks + "->" + c.effectiveTicks(r) + " t, " + baseEu + "->" + c.effectiveEuPerTick() + " EU/t)");
        // Tank Extension: +8000 mB a tank each (4 count), taking them out pours nothing; Clear costs 1 EU / 10 mB
        TileEntityMachineSC tk = new TileEntityMachineSC();
        tk.setMachineType(MachineType.CVD_CHAMBER);
        int cap0 = tk.getTank(0).getCapacity();
        ItemStack ext = ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.TANK_EXTENSION);
        ext.stackSize = 5;
        tk.setInventorySlotContents(TileEntityMachineSC.FIRST_UPGRADE_SLOT, ext);
        int cap4 = tk.getTank(0).getCapacity();
        int in = tk.fill(net.minecraftforge.common.util.ForgeDirection.SOUTH, new FluidStack(ModFluids.hydrogen, 30000), true);
        tk.setInventorySlotContents(TileEntityMachineSC.FIRST_UPGRADE_SLOT, null);
        boolean kept = tk.getTank(0).getFluidAmount() + tk.getTank(1).getFluidAmount() == in && tk.getTank(0).getCapacity() == cap0;
        int full = tk.getTank(0).getFluidAmount() > 0 ? 0 : 1;
        int cost = tk.clearCost(full);
        boolean refused = !tk.clearTank(full);
        tk.setEnergyStoredClient(cost);                     // a buffer holding exactly the price
        int e0 = tk.getEnergyStored();
        boolean paid = e0 >= cost && tk.clearTank(full) && tk.getTank(full).getFluidAmount() == 0 && e0 - tk.getEnergyStored() == cost;
        check(cap0 == 4000 && cap4 == 36000 && in == 30000 && kept && cost == 3000 && refused && paid,
                "tank extension: 4000 -> 36000 mB (5 count as 4), 30000 mB kept after taking them out, clear 3000 EU paid only in full ("
                        + cap0 + "/" + cap4 + "/" + in + "/" + cost + ")");
        TileEntityMachineSC fm = new TileEntityMachineSC();
        fm.setMachineType(MachineType.CRUSHER);
        boolean southFirst = fm.getFacing() == net.minecraftforge.common.util.ForgeDirection.SOUTH;
        fm.setFacing(net.minecraftforge.common.util.ForgeDirection.EAST);
        fm.setFacing(net.minecraftforge.common.util.ForgeDirection.UP);         // ignored: horizontal only
        net.minecraft.nbt.NBTTagCompound fn = new net.minecraft.nbt.NBTTagCompound();
        fm.writeToNBT(fn);
        TileEntityMachineSC fm2 = new TileEntityMachineSC();
        fm2.readFromNBT(fn);
        net.minecraft.nbt.NBTTagCompound old = new net.minecraft.nbt.NBTTagCompound();
        fm.writeToNBT(old);
        old.removeTag("Facing");
        TileEntityMachineSC fm3 = new TileEntityMachineSC();
        fm3.readFromNBT(old);
        check(southFirst && fm2.getFacing() == net.minecraftforge.common.util.ForgeDirection.EAST
                        && fm3.getFacing() == net.minecraftforge.common.util.ForgeDirection.SOUTH,
                "machine facing: horizontal only, saved, old machines stay facing south");
        ItemStack we = new ItemStack(ModItems.WRENCHES.get(1)), wq = new ItemStack(ModItems.WRENCHES.get(2));
        com.sc.tileentity.TileEntityEnergyStorageSC lvs = new com.sc.tileentity.TileEntityEnergyStorageSC();
        lvs.setStorageTier(com.sc.energy.Tier.LV);
        check(!com.sc.item.ItemWrenchSC.isElectric(new ItemStack(ModItems.WRENCHES.get(0)))
                        && com.sc.item.ItemWrenchSC.charge(we, 50000) == 10000 && com.sc.item.ItemWrenchSC.chargeOf(we) == 10000
                        && com.sc.block.BlockConduitSC.isWrench(wq) && lvs.isItemValidForSlot(0, we) && !lvs.isItemValidForSlot(0, wq)
                        && com.sc.item.ItemWrenchSC.Tier.QUANTUM.modes() == 3,
                "wrenches: basic has no battery, electric 10000 EU charges on LV, quantum only from HV, all count as wrenches");
        check(!com.sc.energy.ExplosionLogic.burnCableIfOvervolted(null, com.sc.energy.Tier.XV, com.sc.energy.Tier.QV),
                "cable burn-out: a cable at or above the source's tier stays (the burn itself needs a world)");
        // generators: the two blocks, upgrades, ignition thresholds
        boolean roundTrip = true;
        for (com.sc.energy.GeneratorType gt : com.sc.energy.GeneratorType.values()) {
            roundTrip &= com.sc.init.ModBlocks.generatorTypeOf(com.sc.init.ModBlocks.generatorStack(gt, 1)) == gt;
        }
        com.sc.tileentity.TileEntityGeneratorSC gen = new com.sc.tileentity.TileEntityGeneratorSC();
        gen.setGeneratorType(com.sc.energy.GeneratorType.COMBUSTION);
        ItemStack od = ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.OVERDRIVE);
        gen.setInventorySlotContents(com.sc.tileentity.TileEntityGeneratorSC.FIRST_UPGRADE_SLOT, od);
        gen.setInventorySlotContents(com.sc.tileentity.TileEntityGeneratorSC.FIRST_UPGRADE_SLOT + 1, ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.TRANSFORMER));
        boolean upg = gen.ratedOutput() == 48 && gen.outputTier() == com.sc.energy.Tier.MV && gen.packetsPerTick() == 1
                && gen.isItemValidForSlot(com.sc.tileentity.TileEntityGeneratorSC.FIRST_UPGRADE_SLOT + 2, ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.ECONOMIZER))
                && !gen.isItemValidForSlot(com.sc.tileentity.TileEntityGeneratorSC.FIRST_UPGRADE_SLOT + 2, ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.OVERCLOCKER));
        com.sc.tileentity.TileEntityGeneratorSC odLv = new com.sc.tileentity.TileEntityGeneratorSC();
        odLv.setGeneratorType(com.sc.energy.GeneratorType.COMBUSTION);
        odLv.setInventorySlotContents(com.sc.tileentity.TileEntityGeneratorSC.FIRST_UPGRADE_SLOT, od.copy());
        odLv.setInventorySlotContents(com.sc.tileentity.TileEntityGeneratorSC.FIRST_UPGRADE_SLOT + 1, od.copy());
        check(com.sc.tileentity.TileEntityEnergyStorageSC.overdriveWorks()
                ? odLv.ratedOutput() == 72 && odLv.outputTier() == com.sc.energy.Tier.LV && odLv.packetsPerTick() == 3
                    && odLv.sendMultibleEnergyPackets() && odLv.getMultibleEnergyPacketAmount() == 3 && !odLv.overdriveCapped()
                : odLv.ratedOutput() == 32 && odLv.packetsPerTick() == 1 && odLv.overdriveCapped() && odLv.effectiveOverdrive() == 0
                    && gen.ratedOutput() == 48 && !gen.overdriveCapped(),
                "overdrive: 3 packets of 32 (own net / IU); under IC2 without IU cut to one packet, a Transformer lets it work");
        {   // Г-1: running heat stays under the limit (4 Overdrive + lead casing)
            com.sc.tileentity.TileEntityGeneratorSC hot = new com.sc.tileentity.TileEntityGeneratorSC();
            hot.setGeneratorType(com.sc.energy.GeneratorType.FUSION_REACTOR);
            ItemStack od4 = ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.OVERDRIVE);
            od4.stackSize = 4;
            hot.setInventorySlotContents(com.sc.tileentity.TileEntityGeneratorSC.FIRST_UPGRADE_SLOT, od4);
            hot.setInventorySlotContents(com.sc.tileentity.TileEntityGeneratorSC.FIRST_UPGRADE_SLOT + 1, ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.RAD_SHIELDING));
            int lim = com.sc.tileentity.TileEntityGeneratorSC.HEAT_LIMIT - com.sc.tileentity.TileEntityGeneratorSC.RUNNING_HEAT_MARGIN;
            check(hot.runningHeat(600) <= lim && hot.runningHeat(500) <= lim
                    && (!com.sc.tileentity.TileEntityEnergyStorageSC.overdriveWorks() || hot.runningHeat(600) == lim),
                    "Г-1: reactor running heat capped at limit-50 (" + hot.runningHeat(600) + "): overheat only with a full buffer");
            // Г-2: unlit reactor in ignition mode takes any voltage, no "will explode" warning
            com.sc.tileentity.TileEntityGeneratorSC ign = new com.sc.tileentity.TileEntityGeneratorSC();
            ign.setGeneratorType(com.sc.energy.GeneratorType.FUSION_REACTOR);
            check(ign.isEnergySink() && ign.acceptsAnyVoltage() && !ign.lineTooStrong() && !gen.acceptsAnyVoltage(),
                    "Г-2: unlit reactor (ignition sink) accepts any voltage; an ordinary generator doesn't");
            // Г-5: item keeps the real buffer and tank sizes and every tank
            com.sc.tileentity.TileEntityGeneratorSC tkG = new com.sc.tileentity.TileEntityGeneratorSC();
            tkG.setGeneratorType(com.sc.energy.GeneratorType.COMBUSTION);
            ItemStack extG = ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.TANK_EXTENSION);
            extG.stackSize = 2;
            tkG.setInventorySlotContents(com.sc.tileentity.TileEntityGeneratorSC.FIRST_UPGRADE_SLOT, extG);
            tkG.setInventorySlotContents(com.sc.tileentity.TileEntityGeneratorSC.FIRST_UPGRADE_SLOT + 1, ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.ENERGY_STORAGE));
            net.minecraft.nbt.NBTTagCompound tin = new net.minecraft.nbt.NBTTagCompound();
            tin.setInteger("EnergySC", 100);
            tin.setTag("FuelTank2", new FluidStack(FluidRegistry.WATER, 5000).writeToNBT(new net.minecraft.nbt.NBTTagCompound()));
            tkG.readFromItem(tin);
            net.minecraft.nbt.NBTTagCompound tout = tkG.writeToItem();
            check(tout != null && tout.hasKey("FuelTank2") && tkG.tankCapacity() > com.sc.tileentity.TileEntityGeneratorSC.TANK_CAPACITY
                    && tout.getInteger(com.sc.tileentity.TileEntityGeneratorSC.ITEM_TANK_KEY) == tkG.tankCapacity()
                    && tkG.getMaxEnergyStored() > com.sc.tileentity.TileEntityGeneratorSC.baseBuffer(com.sc.energy.GeneratorType.COMBUSTION)
                    && tout.getInteger(com.sc.tileentity.TileEntityGeneratorSC.ITEM_BUFFER_KEY) == tkG.getMaxEnergyStored(),
                    "Г-5: generator item keeps its tank size with extensions, the 2nd tank and the buffer with storage upgrades");
        }
        com.sc.tileentity.TileEntityGeneratorSC sol = new com.sc.tileentity.TileEntityGeneratorSC();
        sol.setGeneratorType(com.sc.energy.GeneratorType.SOLAR_EXO);
        sol.setInventorySlotContents(com.sc.tileentity.TileEntityGeneratorSC.FIRST_UPGRADE_SLOT, od.copy());
        boolean passive = sol.ratedOutput() == 16384 && sol.outputTier() == com.sc.energy.Tier.XV;   // overdrive: fuel generators only
        TileEntityMachineSC mo = new TileEntityMachineSC();
        mo.setMachineType(MachineType.CRUSHER);
        boolean machineRefuses = !mo.isItemValidForSlot(TileEntityMachineSC.FIRST_UPGRADE_SLOT, od);
        com.sc.tileentity.TileEntityGeneratorSC exo = new com.sc.tileentity.TileEntityGeneratorSC();
        exo.setGeneratorType(com.sc.energy.GeneratorType.EXO_REACTOR);
        boolean ign = exo.isEnergySink() && !exo.isEnergySource() && exo.demandedEnergy() == 100000000
                && com.sc.energy.GeneratorType.COMBUSTION.euPerMb("fuel") > 0 && com.sc.energy.GeneratorType.COMBUSTION.euPerMb("water") == 0;
        check(roundTrip && upg && passive && machineRefuses && ign,
                "generators: 21 types over two blocks, overdrive x1.5 + transformer tier, passive ones ignore overdrive, machines refuse it, exo ignition 100M, combustion fuels");
        // quarry: tier sizes, radius modules, costs, area card, upgrade slots
        com.sc.tileentity.TileEntityQuarrySC qr = new com.sc.tileentity.TileEntityQuarrySC();
        qr.setQuarryTier(com.sc.energy.Tier.HV);
        int[] qa = qr.area();
        boolean qsize = qa != null && qa[2] - qa[0] + 1 == 32 && qr.maxSize() == 32;
        ItemStack rad = ModItems.quarryModule.stackOf(com.sc.item.ItemQuarryModuleSC.Kind.RADIUS);
        rad.stackSize = 9;
        qr.setInventorySlotContents(com.sc.tileentity.TileEntityQuarrySC.FIRST_UPGRADE, rad);
        boolean qrad = qr.maxSize() == 32 + 8 * 4;
        int plain = qr.costFor(1.5F);
        qr.setInventorySlotContents(com.sc.tileentity.TileEntityQuarrySC.FIRST_UPGRADE + 1, ModItems.quarryModule.stackOf(com.sc.item.ItemQuarryModuleSC.Kind.SPEED));
        boolean qcost = plain == 53 && qr.costFor(1.5F) == (int) Math.ceil(52.5 * 1.6);
        qr.setInventorySlotContents(com.sc.tileentity.TileEntityQuarrySC.SLOT_HEAD, new ItemStack(ModItems.DRILL_HEADS.get(2)));
        boolean qspeed = Math.abs(qr.blocksPerSecond() - 4 * 2 * 1.4) < 1e-6;
        ItemStack cardSt = new ItemStack(ModItems.areaCard);
        cardSt.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
        cardSt.getTagCompound().setIntArray("A", new int[]{10, 60, 10});
        cardSt.getTagCompound().setIntArray("B", new int[]{-5, 20, 3});
        int[] ca = com.sc.item.ItemAreaCardSC.area(cardSt);
        boolean qcard = ca[0] == -5 && ca[3] == 10 && ca[1] == 20 && ca[4] == 60;
        boolean qslots = qr.isItemValidForSlot(com.sc.tileentity.TileEntityQuarrySC.FIRST_UPGRADE + 2, ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.TRANSFORMER))
                && !qr.isItemValidForSlot(com.sc.tileentity.TileEntityQuarrySC.FIRST_UPGRADE + 2, ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.OVERCLOCKER))
                && !qr.isItemValidForSlot(0, new ItemStack(ModItems.oreScanner));
        check(qsize && qrad && qcost && qspeed && qcard && qslots,
                "quarry: HV 32x32, radius +8 x4 max, cost 53 EU / x1.6 with speed, diamond head 4 x2 x1.4 blocks/s, area card box, slots");
        // quarry module slots by tier, the Exo Drilling Rig, the old slot layout
        com.sc.tileentity.TileEntityQuarrySC qlv = new com.sc.tileentity.TileEntityQuarrySC();
        qlv.setQuarryTier(com.sc.energy.Tier.LV);
        ItemStack spd = ModItems.quarryModule.stackOf(com.sc.item.ItemQuarryModuleSC.Kind.SPEED);
        int fu = com.sc.tileentity.TileEntityQuarrySC.FIRST_UPGRADE;
        boolean slotsLv = qlv.isItemValidForSlot(fu + 5, spd) && !qlv.isItemValidForSlot(fu + 6, spd);
        com.sc.tileentity.TileEntityQuarrySC qev = new com.sc.tileentity.TileEntityQuarrySC();
        qev.setQuarryTier(com.sc.energy.Tier.EV);
        boolean slotsEv = qev.isItemValidForSlot(fu + 17, spd) && qev.unlockedUpgrades() == 18;
        com.sc.tileentity.TileEntityQuarrySC rig = new com.sc.tileentity.TileEntityQuarrySC();
        rig.setQuarryTier(com.sc.energy.Tier.XV);
        ItemStack lens = new ItemStack(ModItems.oreLens, 1, com.sc.util.OreEntry.SPERRYLITE.meta());
        boolean rigSlots = rig.isExo() && rig.unlockedUpgrades() == 18 && rig.area() == null
                && !rig.isItemValidForSlot(fu, ModItems.quarryModule.stackOf(com.sc.item.ItemQuarryModuleSC.Kind.PUMP))
                && !rig.isItemValidForSlot(com.sc.tileentity.TileEntityQuarrySC.SLOT_HEAD, new ItemStack(ModItems.DRILL_HEADS.get(0)))
                && rig.isItemValidForSlot(com.sc.tileentity.TileEntityQuarrySC.FIRST_LENS, lens)
                && !qev.isItemValidForSlot(com.sc.tileentity.TileEntityQuarrySC.FIRST_LENS, lens);
        com.sc.machine.ExoOreTableSC.Entry sp = null;
        for (com.sc.machine.ExoOreTableSC.Entry e : com.sc.machine.ExoOreTableSC.entries()) {
            if (e.lens == com.sc.util.OreEntry.SPERRYLITE.meta()) {
                sp = e;
            }
        }
        int plainW = rig.weightOf(sp);
        rig.setInventorySlotContents(com.sc.tileentity.TileEntityQuarrySC.FIRST_LENS, lens);
        boolean rigLens = sp != null && rig.weightOf(sp) == plainW * 5 && rig.haulCost() == 200000
                && rig.getMaxEnergyStored() == com.sc.tileentity.TileEntityQuarrySC.EXO_BUFFER;
        net.minecraft.nbt.NBTTagCompound oldSave = new net.minecraft.nbt.NBTTagCompound();
        qev.writeToNBT(oldSave);
        oldSave.removeTag("Layout");
        net.minecraft.nbt.NBTTagList ol = new net.minecraft.nbt.NBTTagList();
        net.minecraft.nbt.NBTTagCompound hd = new net.minecraft.nbt.NBTTagCompound();
        hd.setByte("Slot", (byte) 35);
        new ItemStack(ModItems.DRILL_HEADS.get(1)).writeToNBT(hd);
        ol.appendTag(hd);
        oldSave.setTag("Slots", ol);
        com.sc.tileentity.TileEntityQuarrySC moved = new com.sc.tileentity.TileEntityQuarrySC();
        moved.readFromNBT(oldSave);
        boolean migrate = moved.getStackInSlot(com.sc.tileentity.TileEntityQuarrySC.SLOT_HEAD) != null
                && moved.getStackInSlot(35) == null;
        check(slotsLv && slotsEv && rigSlots && rigLens && migrate,
                "quarry slots LV 6 / EV 18; Exo rig: no area, no pump / head, lenses x5, 200k EU a haul, 20M buffer; old saves' head moves to its new slot");
        // the second batch of quarry modules: tiers and scopes, trash, gentle, economy, resonator, stabilizer, old saves' switches
        com.sc.item.ItemQuarryModuleSC qm = ModItems.quarryModule;
        boolean mTier = !qlv.isItemValidForSlot(fu, qm.stackOf(com.sc.item.ItemQuarryModuleSC.Kind.CENTRIFUGE))
                && qev.isItemValidForSlot(fu, qm.stackOf(com.sc.item.ItemQuarryModuleSC.Kind.CENTRIFUGE))
                && !qlv.isItemValidForSlot(fu, qm.stackOf(com.sc.item.ItemQuarryModuleSC.Kind.RESONATOR))
                && !rig.isItemValidForSlot(fu, qm.stackOf(com.sc.item.ItemQuarryModuleSC.Kind.VEIN))
                && rig.isItemValidForSlot(fu, qm.stackOf(com.sc.item.ItemQuarryModuleSC.Kind.DEEP_SCAN))
                && qlv.isItemValidForSlot(fu, qm.stackOf(com.sc.item.ItemQuarryModuleSC.Kind.TRASH));
        boolean mTrash = com.sc.tileentity.TileEntityQuarrySC.trash(new ItemStack(net.minecraft.init.Blocks.cobblestone))
                && !com.sc.tileentity.TileEntityQuarrySC.trash(new ItemStack(net.minecraft.init.Blocks.diamond_ore))
                && com.sc.tileentity.TileEntityQuarrySC.built(net.minecraft.init.Blocks.planks)
                && !com.sc.tileentity.TileEntityQuarrySC.built(net.minecraft.init.Blocks.stone);
        com.sc.tileentity.TileEntityQuarrySC qe = new com.sc.tileentity.TileEntityQuarrySC();
        qe.setQuarryTier(com.sc.energy.Tier.EV);
        qe.setInventorySlotContents(com.sc.tileentity.TileEntityQuarrySC.SLOT_HEAD, new ItemStack(ModItems.DRILL_HEADS.get(0)));
        double bps0 = qe.blocksPerSecond();
        int cost0 = qe.costFor(1.5F);
        qe.setInventorySlotContents(fu, qm.stackOf(com.sc.item.ItemQuarryModuleSC.Kind.ECONOMY));
        boolean mEco = Math.abs(qe.blocksPerSecond() - bps0 * 0.8) < 1e-6 && qe.costFor(1.5F) < cost0
                && (qe.getFlags() & com.sc.tileentity.TileEntityQuarrySC.F_REPAIR) == 0;
        qe.setInventorySlotContents(fu + 1, qm.stackOf(com.sc.item.ItemQuarryModuleSC.Kind.DOUBLE));
        mEco &= Math.abs(qe.blocksPerSecond() - bps0 * 1.6) < 1e-6;
        com.sc.tileentity.TileEntityQuarrySC rig2 = new com.sc.tileentity.TileEntityQuarrySC();
        rig2.setQuarryTier(com.sc.energy.Tier.XV);
        int L = com.sc.tileentity.TileEntityQuarrySC.FIRST_LENS;
        boolean closed = !rig2.isItemValidForSlot(L + 4, lens) && rig2.unlockedLenses() == 4;
        ItemStack res = qm.stackOf(com.sc.item.ItemQuarryModuleSC.Kind.RESONATOR);
        res.stackSize = 2;
        rig2.setInventorySlotContents(fu, res);
        boolean opened = rig2.unlockedLenses() == 6 && rig2.isItemValidForSlot(L + 5, lens) && !rig2.isItemValidForSlot(L + 6, lens);
        rig2.setInventorySlotContents(fu + 1, qm.stackOf(com.sc.item.ItemQuarryModuleSC.Kind.STABILIZER));
        rig2.setInventorySlotContents(L + 5, lens);
        boolean stab = rig2.lensBoost() == 6 && rig2.haulCost() == 300000 && sp != null && rig2.weightOf(sp) == plainW * 7;
        net.minecraft.nbt.NBTTagCompound old18 = new net.minecraft.nbt.NBTTagCompound();
        qe.writeToNBT(old18);
        int[] st = old18.getIntArray("Settings");
        int[] st18 = java.util.Arrays.copyOf(st, 18);
        st18[7] = com.sc.tileentity.TileEntityQuarrySC.F_SPEED;
        old18.setIntArray("Settings", st18);
        com.sc.tileentity.TileEntityQuarrySC qm18 = new com.sc.tileentity.TileEntityQuarrySC();
        qm18.readFromNBT(old18);
        int f18 = qm18.getFlags();
        boolean migr = (f18 & com.sc.tileentity.TileEntityQuarrySC.F_TRASH) != 0 && (f18 & com.sc.tileentity.TileEntityQuarrySC.F_DEEP_SCAN) != 0
                && (f18 & com.sc.tileentity.TileEntityQuarrySC.F_REPAIR) == 0 && (f18 & com.sc.tileentity.TileEntityQuarrySC.F_FORTUNE) == 0;
        check(mTier && mTrash && mEco && closed && opened && stab && migr,
                "quarry modules 2: tier / quarry / rig rules, trash and gentle lists, economy x0.8 and twin x2, resonator opens lenses, stabilizer x7 at 300k, old saves get the new switches (repair off)");
        // field generator wireless charging: the booster goes into fields only and doubles the rate; mode and reserve are kept
        ItemStack booster = ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.CHARGE_BOOSTER);
        com.sc.tileentity.TileEntityFieldGeneratorSC cf = new com.sc.tileentity.TileEntityFieldGeneratorSC();
        TileEntityMachineSC cm = new TileEntityMachineSC();
        cm.setMachineType(MachineType.CRUSHER);
        boolean bSlots = cf.isItemValidForSlot(0, booster) && !cm.isItemValidForSlot(TileEntityMachineSC.FIRST_UPGRADE_SLOT, booster);
        int rate0 = cf.chargeRate();
        ItemStack b3 = booster.copy();
        b3.stackSize = 6;
        cf.setInventorySlotContents(0, b3);
        boolean bRate = rate0 == com.sc.tileentity.TileEntityFieldGeneratorSC.CHARGE_PER_SECOND && cf.chargeRate() == rate0 * 16;
        cf.cycleChargeMode();
        cf.adjustChargeReserve(30);
        cf.adjustChargeReserve(500);
        net.minecraft.nbt.NBTTagCompound cfn = new net.minecraft.nbt.NBTTagCompound();
        cf.writeToNBT(cfn);
        com.sc.tileentity.TileEntityFieldGeneratorSC cf2 = new com.sc.tileentity.TileEntityFieldGeneratorSC();
        cf2.readFromNBT(cfn);
        boolean bKeep = cf2.getChargeMode() == com.sc.tileentity.TileEntityFieldGeneratorSC.CHARGE_HELD_FIRST
                && cf2.getChargeReserve() == com.sc.tileentity.TileEntityFieldGeneratorSC.RESERVE_MAX
                && cf2.has(com.sc.tileentity.TileEntityFieldGeneratorSC.F_CHARGE_FX);
        cfn.removeTag("ChargeMode");
        cfn.setInteger("Flags", com.sc.tileentity.TileEntityFieldGeneratorSC.F_CHARGE);
        com.sc.tileentity.TileEntityFieldGeneratorSC cf3 = new com.sc.tileentity.TileEntityFieldGeneratorSC();
        cf3.readFromNBT(cfn);
        boolean bOld = cf3.has(com.sc.tileentity.TileEntityFieldGeneratorSC.F_CHARGE_FX) && cf3.getChargeMode() == 0;
        check(bSlots && bRate && bKeep && bOld,
                "field charging: booster in fields only, x2 each up to x16, mode / reserve (max 90%) saved, old fields get sparks on");
        // the pump's compartments: open by tier, sized by tank modules, each given out only on its side; old saves' tank moves in
        com.sc.tileentity.TileEntityQuarrySC tq = new com.sc.tileentity.TileEntityQuarrySC();
        tq.setQuarryTier(com.sc.energy.Tier.HV);
        boolean tOpen = tq.unlockedTanks() == 3 && tq.tankCapacity() == com.sc.tileentity.TileEntityQuarrySC.TANK_BASE;
        ItemStack tm = ModItems.quarryModule.stackOf(com.sc.item.ItemQuarryModuleSC.Kind.TANK);
        tm.stackSize = 6;
        tq.setInventorySlotContents(com.sc.tileentity.TileEntityQuarrySC.FIRST_UPGRADE, tm);
        boolean tCap = tq.tankCapacity() == com.sc.tileentity.TileEntityQuarrySC.TANK_BASE + 4 * com.sc.tileentity.TileEntityQuarrySC.TANK_PER_MODULE
                && tq.waterCapacity() == com.sc.tileentity.TileEntityQuarrySC.WATER_BASE + 4 * com.sc.tileentity.TileEntityQuarrySC.TANK_PER_MODULE;
        net.minecraft.nbt.NBTTagCompound tn = new net.minecraft.nbt.NBTTagCompound();
        tq.writeToNBT(tn);
        tn.removeTag("Tanks");
        tn.setTag("Pumped", new FluidStack(FluidRegistry.LAVA, 5000).writeToNBT(new net.minecraft.nbt.NBTTagCompound()));
        tn.setIntArray("TankSides", new int[]{net.minecraftforge.common.util.ForgeDirection.DOWN.ordinal(), -1, -1, -1});
        com.sc.tileentity.TileEntityQuarrySC tq2 = new com.sc.tileentity.TileEntityQuarrySC();
        tq2.readFromNBT(tn);
        boolean tMig = tq2.getTank(0).getFluidAmount() == 5000 && tq2.getTank(0).getFluid().getFluid() == FluidRegistry.LAVA;
        boolean tSide = tq2.drain(net.minecraftforge.common.util.ForgeDirection.UP, 1000, false) == null
                && tq2.drain(net.minecraftforge.common.util.ForgeDirection.DOWN, 1000, false) != null
                && !tq2.canDrain(net.minecraftforge.common.util.ForgeDirection.NORTH, null);
        boolean tFilter = tq2.fluidWanted(FluidRegistry.WATER);
        check(tOpen && tCap && tMig && tSide && tFilter,
                "quarry pump tank: HV 3 of 4 compartments, tank modules +32000 each (up to 4), old 'Pumped' becomes compartment 1, output side kept");
        // the Tanks tab: a pinned tank takes only its fluid (first, even empty), clearing costs 1 EU per 10 mB, pins and auto are kept
        boolean tPins = false;
        try {
            net.minecraft.nbt.NBTTagCompound pn = new net.minecraft.nbt.NBTTagCompound();
            tq.writeToNBT(pn);
            net.minecraft.nbt.NBTTagList tl = new net.minecraft.nbt.NBTTagList();
            net.minecraft.nbt.NBTTagCompound w2 = new FluidStack(FluidRegistry.WATER, 5000).writeToNBT(new net.minecraft.nbt.NBTTagCompound());
            w2.setByte("Tank", (byte) 2);
            tl.appendTag(w2);
            pn.setTag("Tanks", tl);
            net.minecraft.nbt.NBTTagList pins = new net.minecraft.nbt.NBTTagList();
            for (String n : new String[]{"lava", "", "", ""}) {
                pins.appendTag(new net.minecraft.nbt.NBTTagString(n));
            }
            pn.setTag("TankPinned", pins);
            pn.setByteArray("TankAuto", new byte[]{0, 1, 0, 0});
            com.sc.tileentity.TileEntityQuarrySC tp = new com.sc.tileentity.TileEntityQuarrySC();
            tp.readFromNBT(pn);
            java.lang.reflect.Method cfor = com.sc.tileentity.TileEntityQuarrySC.class.getDeclaredMethod("compartmentFor", FluidStack.class);
            cfor.setAccessible(true);
            int water = (Integer) cfor.invoke(tp, new FluidStack(FluidRegistry.WATER, 1000));
            int lava = (Integer) cfor.invoke(tp, new FluidStack(FluidRegistry.LAVA, 1000));
            net.minecraftforge.fluids.Fluid other = null;
            for (Object o : FluidRegistry.getRegisteredFluids().values()) {
                net.minecraftforge.fluids.Fluid f = (net.minecraftforge.fluids.Fluid) o;
                if (f != FluidRegistry.WATER && f != FluidRegistry.LAVA) {
                    other = f;
                    break;
                }
            }
            int oth = other == null ? 1 : (Integer) cfor.invoke(tp, new FluidStack(other, 1000));
            tPins = water == 2 && lava == 0 && oth == 1 && tp.clearCost(2) == 500 && tp.clearCost(0) == 0
                    && "lava".equals(tp.getTankPinned(0)) && tp.getTankAuto(1) && !tp.getTankAuto(0);
        } catch (Exception ex) {
            tPins = false;
        }
        check(tPins, "quarry tanks: a pinned tank takes only its fluid, others go to a free tank, clearing 1 EU / 10 mB, pins + auto saved");
        // the fluid vein module: quarry only, off without it, its reach by tier (and the setting) - saved
        com.sc.tileentity.TileEntityQuarrySC fv = new com.sc.tileentity.TileEntityQuarrySC();
        fv.setQuarryTier(com.sc.energy.Tier.MV);
        boolean fvOff = !fv.fluidVeinActive();
        fv.setInventorySlotContents(com.sc.tileentity.TileEntityQuarrySC.FIRST_UPGRADE,
                ModItems.quarryModule.stackOf(com.sc.item.ItemQuarryModuleSC.Kind.FLUID_VEIN));
        com.sc.tileentity.TileEntityQuarrySC fvRig = new com.sc.tileentity.TileEntityQuarrySC();
        fvRig.setQuarryTier(com.sc.energy.Tier.XV);
        check(fvOff && fv.fluidVeinActive() && fv.fluidVeinReach() == 16
                        && !fvRig.isItemValidForSlot(com.sc.tileentity.TileEntityQuarrySC.FIRST_UPGRADE,
                        ModItems.quarryModule.stackOf(com.sc.item.ItemQuarryModuleSC.Kind.FLUID_VEIN)),
                "fluid vein module: on with the module, MV reach 16, not for the drilling rig");
        // the quarry's pump knows water and lava by their flowing blocks too, and other fluids by their own blocks
        net.minecraft.block.Block modFluidBlock = null;
        for (Object o : FluidRegistry.getRegisteredFluids().values()) {
            net.minecraftforge.fluids.Fluid f = (net.minecraftforge.fluids.Fluid) o;
            if (f.getBlock() instanceof net.minecraftforge.fluids.IFluidBlock && f.getBlock() != null) {
                modFluidBlock = f.getBlock();
                break;
            }
        }
        check(com.sc.tileentity.TileEntityQuarrySC.fluidOf(net.minecraft.init.Blocks.flowing_water) == FluidRegistry.WATER
                        && com.sc.tileentity.TileEntityQuarrySC.fluidOf(net.minecraft.init.Blocks.water) == FluidRegistry.WATER
                        && com.sc.tileentity.TileEntityQuarrySC.fluidOf(net.minecraft.init.Blocks.flowing_lava) == FluidRegistry.LAVA
                        && com.sc.tileentity.TileEntityQuarrySC.fluidOf(net.minecraft.init.Blocks.stone) == null
                        && (modFluidBlock == null || com.sc.tileentity.TileEntityQuarrySC.fluidOf(modFluidBlock)
                                == ((net.minecraftforge.fluids.IFluidBlock) modFluidBlock).getFluid()),
                "quarry pump: flowing water / lava count, other fluids by their own block");
        // a filter as another filter's example keeps no list of its own (no filter-in-filter growth)
        ItemStack fa = new ItemStack(ModItems.itemFilter, 1, 1), fb = new ItemStack(ModItems.itemFilter, 1, 1);
        com.sc.conduit.ItemFilterSC.setEntry(fb, 0, new ItemStack(ModItems.oreScanner));
        com.sc.conduit.ItemFilterSC.setEntry(fa, 0, fb);
        ItemStack inA = com.sc.conduit.ItemFilterSC.entries(fa)[0];
        check(inA != null && com.sc.conduit.ItemFilterSC.isFilter(inA) && !inA.hasTagCompound(),
                "a filter put into a filter keeps no NBT");
        TileEntityMachineSC u = new TileEntityMachineSC();
        u.setMachineType(MachineType.CRUSHER);
        u.setInventorySlotContents(TileEntityMachineSC.FIRST_UPGRADE_SLOT, ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.UNIVERSAL_TRANSFORMER));
        check(u.inputTier() == com.sc.energy.Tier.max() && u.receiveEnergy(net.minecraftforge.common.util.ForgeDirection.UNKNOWN, 32768, 100, true) == 100
                        && u.receiveEnergy(net.minecraftforge.common.util.ForgeDirection.UNKNOWN, 131072, 100, true) == 100,
                "universal transformer upgrade: an LV machine takes any voltage, XV and SV included");
        net.minecraft.nbt.NBTTagCompound ut = u.upgradesForItem();
        TileEntityMachineSC u2 = new TileEntityMachineSC();
        u2.setMachineType(MachineType.CRUSHER);
        u2.loadUpgradesFromItem(ut);
        check(u.acceptsAnyVoltage() && !new TileEntityMachineSC().acceptsAnyVoltage() && u.upgradesInItem() && u2.acceptsAnyVoltage()
                        && TileEntityMachineSC.upgradesOf(ut)[0] != null && new TileEntityMachineSC().upgradesForItem() == null,
                "universal transformer: no voltage limit for IC2 nets; a machine's upgrades go with its item and come back on placement");
        TileEntityMachineSC off = new TileEntityMachineSC();
        off.setMachineType(MachineType.CRUSHER);
        off.setPowerOn(false);
        off.setRedstoneMode(2);
        net.minecraft.nbt.NBTTagCompound offNbt = new net.minecraft.nbt.NBTTagCompound();
        off.writeToNBT(offNbt);
        TileEntityMachineSC back = new TileEntityMachineSC();
        back.readFromNBT(offNbt);
        TileEntityMachineSC legacy = new TileEntityMachineSC();
        legacy.readFromNBT(new net.minecraft.nbt.NBTTagCompound());
        check(off.demandedEnergy() == 0 && off.receiveEnergy(net.minecraftforge.common.util.ForgeDirection.UNKNOWN, 32768, 100, false) == 0
                        && !back.isPowerOn() && back.getRedstoneMode() == 2 && legacy.isPowerOn() && legacy.getRedstoneMode() == 0,
                "power switch: off takes no energy (an XV packet doesn't blow an LV machine up), saved with the redstone mode; old saves stay on");
        com.sc.tileentity.TileEntityEnergyStorageSC sw = new com.sc.tileentity.TileEntityEnergyStorageSC();
        sw.setStorageTier(com.sc.energy.Tier.LV);
        sw.setStoredFromItem(1000);
        boolean swOn = sw.offerableEnergy() > 0 && sw.demandedEnergy() > 0;
        sw.setPowerOn(false);
        check(swOn && sw.offerableEnergy() == 0 && sw.demandedEnergy() == 0
                        && sw.receiveEnergy(net.minecraftforge.common.util.ForgeDirection.UNKNOWN, 32768, 100, false) == 0,
                "power switch: a storage switched off neither gives nor takes energy (no overvolting either)");
        boolean hidden = true;
        for (int slot : c.getAccessibleSlotsFromSide(1)) {
            hidden &= slot < TileEntityMachineSC.FIRST_UPGRADE_SLOT || slot == TileEntityMachineSC.SLOT_BATTERY;
        }
        check(hidden && c.isItemValidForSlot(TileEntityMachineSC.FIRST_UPGRADE_SLOT + 3, oc)
                        && !c.isItemValidForSlot(TileEntityMachineSC.FIRST_UPGRADE_SLOT + 3, new ItemStack(net.minecraft.init.Items.iron_ingot)),
                "upgrade slots take only upgrades and are out of reach of pipes and hoppers");
    }

    /** Ender IO style item filters and the tube connector slots. */
    private static void tubeFilters() {
        ItemStack iron = new ItemStack(net.minecraft.init.Items.iron_ingot), gold = new ItemStack(net.minecraft.init.Items.gold_ingot);
        ItemStack f = new ItemStack(ModItems.itemFilter, 1, 0);
        boolean empty = !com.sc.conduit.ItemFilterSC.passes(f, iron) && com.sc.conduit.ItemFilterSC.passes(null, iron);
        com.sc.conduit.ItemFilterSC.setEntry(f, 0, iron);
        boolean white = com.sc.conduit.ItemFilterSC.passes(f, iron) && !com.sc.conduit.ItemFilterSC.passes(f, gold);
        com.sc.conduit.ItemFilterSC.setFlag(f, com.sc.conduit.ItemFilterSC.BLACKLIST, true);
        boolean black = !com.sc.conduit.ItemFilterSC.passes(f, iron) && com.sc.conduit.ItemFilterSC.passes(f, gold);
        check(empty && white && black && com.sc.conduit.ItemFilterSC.slots(f) == 5,
                "item filter: empty whitelist blocks, whitelist / blacklist of iron, 5 examples; no filter lets all through");

        ItemStack wool = new ItemStack(net.minecraft.init.Blocks.wool, 1, 3), redWool = new ItemStack(net.minecraft.init.Blocks.wool, 1, 14);
        ItemStack m = new ItemStack(ModItems.itemFilter, 1, 0);
        com.sc.conduit.ItemFilterSC.setEntry(m, 2, wool);
        boolean meta = !com.sc.conduit.ItemFilterSC.passes(m, redWool);
        com.sc.conduit.ItemFilterSC.setFlag(m, com.sc.conduit.ItemFilterSC.IGNORE_META, true);
        meta &= com.sc.conduit.ItemFilterSC.passes(m, redWool);
        ItemStack adv = new ItemStack(ModItems.itemFilter, 1, 1);
        com.sc.conduit.ItemFilterSC.setEntry(adv, 9, new ItemStack(net.minecraft.init.Blocks.log, 1, 0));
        boolean ore = !com.sc.conduit.ItemFilterSC.passes(adv, new ItemStack(net.minecraft.init.Blocks.log2, 1, 1));
        com.sc.conduit.ItemFilterSC.setFlag(adv, com.sc.conduit.ItemFilterSC.ORE_DICT, true);
        ore &= com.sc.conduit.ItemFilterSC.passes(adv, new ItemStack(net.minecraft.init.Blocks.log2, 1, 1));
        check(meta && ore && com.sc.conduit.ItemFilterSC.slots(adv) == 10,
                "filter switches: ignore meta (any wool), advanced ore dictionary (oak log lets dark oak through), 10 examples");

        TileEntityConduitBundleSC b = new TileEntityConduitBundleSC();
        com.sc.conduit.ConnectorInventorySC inv = b.getConnectorItems();
        net.minecraftforge.common.util.ForgeDirection e = net.minecraftforge.common.util.ForgeDirection.EAST;
        int outSlot = com.sc.conduit.ConnectorInventorySC.index(e, com.sc.conduit.ConnectorInventorySC.OUT_FILTER);
        int speedSlot = com.sc.conduit.ConnectorInventorySC.index(e, com.sc.conduit.ConnectorInventorySC.SPEED);
        ItemStack speed = new ItemStack(ModItems.tubeSpeedUpgrade, 3);
        boolean valid = inv.isItemValidForSlot(outSlot, f) && !inv.isItemValidForSlot(outSlot, speed)
                && inv.isItemValidForSlot(speedSlot, speed) && !inv.isItemValidForSlot(speedSlot, f);
        inv.setInventorySlotContents(outSlot, f);
        inv.setInventorySlotContents(speedSlot, speed);
        b.addPart(com.sc.conduit.ConduitKind.TUBE, 0);
        net.minecraft.nbt.NBTTagCompound nbt = new net.minecraft.nbt.NBTTagCompound();
        b.writeToNBT(nbt);
        TileEntityConduitBundleSC c = new TileEntityConduitBundleSC();
        c.readFromNBT(nbt);
        check(valid && c.getConnectorItems().speed(e) == 3
                        && com.sc.conduit.ItemFilterSC.isFilter(c.getConnectorItems().get(e, com.sc.conduit.ConnectorInventorySC.OUT_FILTER))
                        && c.allPartStacks().size() == 1 && c.getConnectorItems().contents().size() == 2,
                "tube connector slots: filters and speed upgrades only in their slots, saved (they spill as themselves, never copies)");
    }

    /** Portable tanks: capacity per tier, any side, contents kept through the item and a tier upgrade. */
    private static void portableTanks() {
        com.sc.tileentity.TileEntityTankSC t = new com.sc.tileentity.TileEntityTankSC();
        t.setTier(1);
        net.minecraftforge.common.util.ForgeDirection up = net.minecraftforge.common.util.ForgeDirection.UP;
        int in = t.fill(up, new FluidStack(FluidRegistry.WATER, 40000), true);
        int wrong = t.fill(net.minecraftforge.common.util.ForgeDirection.WEST, new FluidStack(FluidRegistry.LAVA, 100), true);
        FluidStack out = t.drain(net.minecraftforge.common.util.ForgeDirection.DOWN, 2000, true);
        check(in == 32000 && wrong == 0 && out != null && out.amount == 2000 && t.comparatorLevel() == 1 + 30000 * 14 / 32000,
                "titanium tank: 32 000 mB of one fluid, in and out on any side, comparator level");
        t.setAutoOutput(true);
        ItemStack item = com.sc.block.BlockTankSC.itemOf(t, com.sc.init.ModBlocks.tankSC, 1);
        FluidStack carried = com.sc.block.ItemBlockTankSC.contents(item);
        com.sc.tileentity.TileEntityTankSC placed = new com.sc.tileentity.TileEntityTankSC();
        placed.setTier(2);
        placed.setContents(carried);
        check(carried != null && carried.amount == 30000 && item.getTagCompound().getBoolean("AutoOutput")
                        && placed.getTank().getFluidAmount() == 30000 && placed.getTank().getCapacity() == 128000
                        && item.getItem().getItemStackLimit(item) == 1,
                "tank item carries 30 000 mB and auto-output; a filled tank doesn't stack; tungsten tank holds 128 000");
    }

    /** Suit functions: which piece and tier has which, each switched on / off on its own, the power mode. */
    private static void armorFunctions() {
        int nano = 0, quantum = 0, exo = 0;
        for (com.sc.util.ArmorFeature f : com.sc.util.ArmorFeature.values()) {
            for (int type = 0; type < 4; type++) {
                nano += f.availableIn(com.sc.util.ArmorSuit.NANO, type) ? 1 : 0;
                quantum += f.availableIn(com.sc.util.ArmorSuit.QUANTUM, type) ? 1 : 0;
                exo += f.availableIn(com.sc.util.ArmorSuit.EXO, type) ? 1 : 0;
            }
        }
        check(nano == 8 && quantum == 17 && exo == 25 && com.sc.util.ArmorFeature.ANNIHILATION.isAction()
                        && !com.sc.util.ArmorFeature.ANNIHILATION.availableIn(com.sc.util.ArmorSuit.QUANTUM, 1)
                        && com.sc.util.ArmorFeature.AIR.availableIn(com.sc.util.ArmorSuit.NANO, 0)
                        && !com.sc.util.ArmorFeature.FUSION_CELL.availableIn(com.sc.util.ArmorSuit.QUANTUM, 1),
                "suit functions: Nano 8 (oxygen breathing from Nano), Quantum 17 (+engine boost, searchlight), Exo all 25 incl. the fusion cell (" + nano + "/" + quantum + "/" + exo + ")");

        ItemStack helmet = new ItemStack(ModItems.ARMOR.get(com.sc.util.ArmorSuit.EXO)[0]);
        ItemStack nanoHelmet = new ItemStack(ModItems.ARMOR.get(com.sc.util.ArmorSuit.NANO)[0]);
        boolean defaults = com.sc.item.ItemArmorSC.isEnabled(helmet, com.sc.util.ArmorFeature.NIGHT_VISION)
                && !com.sc.item.ItemArmorSC.isEnabled(helmet, com.sc.util.ArmorFeature.THERMAL)
                && !com.sc.item.ItemArmorSC.isEnabled(nanoHelmet, com.sc.util.ArmorFeature.SOLAR)
                && !com.sc.item.ItemArmorSC.isEnabled(helmet, com.sc.util.ArmorFeature.FLIGHT);
        com.sc.item.ItemArmorSC.setEnabled(helmet, com.sc.util.ArmorFeature.NIGHT_VISION, false);
        com.sc.item.ItemArmorSC.setEnabled(helmet, com.sc.util.ArmorFeature.THERMAL, true);
        boolean flipped = !com.sc.item.ItemArmorSC.isEnabled(helmet, com.sc.util.ArmorFeature.NIGHT_VISION)
                && com.sc.item.ItemArmorSC.isEnabled(helmet, com.sc.util.ArmorFeature.THERMAL)
                && com.sc.item.ItemArmorSC.isEnabled(helmet, com.sc.util.ArmorFeature.SOLAR);
        ItemStack chest = new ItemStack(ModItems.ARMOR.get(com.sc.util.ArmorSuit.QUANTUM)[1]);
        boolean mode = com.sc.item.ItemArmorSC.powerMode(chest) == 1;
        com.sc.item.ItemArmorSC.setPowerMode(chest, 2);
        mode &= com.sc.item.ItemArmorSC.powerMode(chest) == 2;
        com.sc.item.ItemArmorSC.setCharge(chest, 100);
        boolean pay = !com.sc.item.ItemArmorSC.pay(chest, 150) && com.sc.item.ItemArmorSC.chargeOf(chest) == 100
                && com.sc.item.ItemArmorSC.pay(chest, 60) && com.sc.item.ItemArmorSC.chargeOf(chest) == 40;
        check(defaults && flipped && mode && pay,
                "functions: defaults (thermal off), each switched on its own, only in pieces that have them; power mode; all-or-nothing payment");
    }

    /**
     * The Singular suit, stage 1 (docs/plan-singular-armor.md): its numbers, every Exo function, the
     * tanks (singular matter too), helium and radiators, 20% less gas than Exo, the colour schemes
     * and their textures, the station's 8th tank and old stations' gas switches.
     */
    private static void singularArmor() {
        com.sc.util.ArmorSuit sg = com.sc.util.ArmorSuit.SINGULAR, exo = com.sc.util.ArmorSuit.EXO;
        com.sc.util.ArmorSuit[] suits = com.sc.util.ArmorSuit.values();
        com.sc.item.ItemArmorSC[] p = ModItems.ARMOR.get(sg);
        check(suits[suits.length - 1] == sg && sg.ordinal() == 3 && p != null && p.length == 4
                        && sg.material.getDamageReductionAmount(0) == 5 && sg.material.getDamageReductionAmount(1) == 10
                        && sg.material.getDamageReductionAmount(2) == 8 && sg.material.getDamageReductionAmount(3) == 5
                        && sg.maxCharge == 64000000 && sg.chargeTier == com.sc.energy.Tier.SV && sg.heatCapacity == 1000
                        && sg.heatDissipation == 2 && exo.heatCapacity == 400 && com.sc.util.ArmorSuit.NANO.heatCapacity == 100
                        && sg.material.getDurability(1) > exo.material.getDurability(1) && sg.material.getEnchantability() > exo.material.getEnchantability()
                        && p[1].getSuit() == sg && p[2].armorType == 2
                        && ItemStackName(p[0]).equals("item.siliconage.armor.singular.helmet")
                        && com.sc.item.ItemArmorSC.capacityOf(new ItemStack(p[1])) == com.sc.util.ConfigSC.scale(64000000, com.sc.util.ConfigSC.armorCapacity, 1)
                        && p[0].getTier(new ItemStack(p[0])) == com.sc.energy.Tier.SV.toIc2Tier(),
                "Singular suit: appended 4th, 5/10/8/5, 64M EU at SV, heat 1000 / 2 a second, four pieces registered");
        int exoFns = 0, sgFns = 0;
        boolean inherits = true;
        for (com.sc.util.ArmorFeature f : com.sc.util.ArmorFeature.values()) {
            for (int t = 0; t < 4; t++) {
                exoFns += f.availableIn(exo, t) ? 1 : 0;
                sgFns += f.availableIn(sg, t) ? 1 : 0;
                inherits &= !f.availableIn(exo, t) || f.availableIn(sg, t);
            }
        }
        ItemStack[] w = gasSuit(sg, true, true, true, true);
        for (ItemStack s : w) {
            com.sc.item.ItemArmorSC.setCharge(s, 1000);
        }
        boolean strict = com.sc.item.ArmorLogicSC.strict(sg) && com.sc.item.ArmorLogicSC.emergency(w)
                && ((com.sc.item.ItemArmorSC) w[1].getItem()).protectionIn(w) == 6
                && com.sc.item.ArmorLogicSC.fullSetOf(w) == sg && com.sc.util.ArmorSuit.exoClass(sg) && sg.atLeast(exo) && !exo.atLeast(sg)
                && com.sc.util.ArmorFeature.regenHeal(sg) == 3F;
        fillSuit(w, 50);
        strict &= !com.sc.item.ArmorLogicSC.emergency(w) && com.sc.item.ArmorLogicSC.worksIn(w, com.sc.util.ArmorFeature.FLIGHT)
                && com.sc.item.ArmorLogicSC.worksIn(w, com.sc.util.ArmorFeature.ANNIHILATION)
                && com.sc.item.ArmorLogicSC.worksIn(w, com.sc.util.ArmorFeature.NIGHT_VISION)
                && ((com.sc.item.ItemArmorSC) w[1].getItem()).protectionIn(w) == 10;
        check(inherits && exoFns == 25 && sgFns == exoFns + SINGULAR_OWN && strict,
                "Singular suit: every Exo function (" + sgFns + "), strict gas rules, emergency mode without helium (iron plating)");
        // tanks (plan §4), singular matter only in the Singular chestplate
        com.sc.util.ArmorGasSC.Gas he = com.sc.util.ArmorGasSC.Gas.HELIUM, o2 = com.sc.util.ArmorGasSC.Gas.OXYGEN,
                h2 = com.sc.util.ArmorGasSC.Gas.HYDROGEN, ar = com.sc.util.ArmorGasSC.Gas.ARGON, kr = com.sc.util.ArmorGasSC.Gas.KRYPTON,
                d2o = com.sc.util.ArmorGasSC.Gas.HEAVY_WATER, d = com.sc.util.ArmorGasSC.Gas.DEUTERIUM,
                sm = com.sc.util.ArmorGasSC.Gas.SINGULAR_MATTER;
        int[][] want = {   // [gas][piece]
            {4000, 24000, 4000, 4000}, {12000, 0, 0, 0}, {0, 6000, 0, 8000}, {0, 4000, 0, 0},
            {4000, 0, 0, 0}, {0, 0, 12000, 0}, {0, 8000, 0, 0}, {0, 1000, 0, 0}};
        boolean tanks = com.sc.util.ArmorGasSC.Gas.values().length == 8 && sm.ordinal() == 7 && sm.color == 0xC85AFF
                && "singularmatter".equals(sm.fluid);
        for (com.sc.util.ArmorGasSC.Gas g : com.sc.util.ArmorGasSC.Gas.values()) {
            for (int t = 0; t < 4; t++) {
                tanks &= com.sc.util.ArmorGasSC.baseCapacity(new ItemStack(p[t]), g) == want[g.ordinal()][t];
            }
        }
        for (com.sc.util.ArmorSuit other : new com.sc.util.ArmorSuit[]{com.sc.util.ArmorSuit.NANO, com.sc.util.ArmorSuit.QUANTUM, exo}) {
            for (int t = 0; t < 4; t++) {
                tanks &= com.sc.util.ArmorGasSC.baseCapacity(new ItemStack(ModItems.ARMOR.get(other)[t]), sm) == 0;
            }
        }
        ItemStack[] full = gasSuit(sg, true, true, true, true);
        fillSuit(full, 100);
        tanks &= com.sc.util.ArmorGasSC.capacityOf(full, he) == 36000 && com.sc.util.ArmorGasSC.capacityOf(full, h2) == 14000
                && com.sc.util.ArmorGasSC.amountOf(full, sm) == 1000 && com.sc.util.ArmorGasSC.levelBonusPercent(full[1]) == 0
                && com.sc.util.ArmorGasSC.capacity(full[1], d) == 8000 && com.sc.util.ArmorGasSC.capacity(full[2], d2o) == 12000;
        net.minecraftforge.fluids.Fluid smFluid = FluidRegistry.getFluid("singularmatter");
        tanks &= smFluid != null && sm.fluidOf() == smFluid && com.sc.util.ArmorGasSC.Gas.of(smFluid) == sm
                && java.util.Arrays.asList(com.sc.item.ItemFluidBucketSC.FLUIDS).indexOf("singularmatter") >= 0
                && com.sc.init.ModFluids.COLORS.containsKey("singularmatter");
        check(tanks, "Singular tanks: He 24000 + 3 x 4000, O2 12000, H2 6000 + 8000, Ar 4000, Kr 4000, D2O 12000, D 8000, "
                + "singular matter 1000 (chestplate only, its own fluid and bucket); level hook 0%");
        // helium: 25 heat a mB, radiators +20% (full set x1.6), pump 32
        ItemStack[] exoFull = gasSuit(exo, true, true, true, true);
        fillSuit(exoFull, 100);
        int heBefore = com.sc.util.ArmorGasSC.amountOf(full, he);
        int removed = com.sc.util.ArmorGasSC.heliumCool(full, 1000, false);
        float frac = full[1].getTagCompound().getFloat("GasFrac_helium");
        float used = heBefore - com.sc.util.ArmorGasSC.amountOf(full, he) + frac;
        boolean cool = com.sc.util.ArmorGasSC.heliumHeatPerMb(sg) == 25F && com.sc.util.ArmorGasSC.heliumHeatPerMb(exo) == 20F
                && com.sc.util.ArmorGasSC.radiatorBonus(sg) == 0.20F && com.sc.util.ArmorGasSC.radiatorBonus(exo) == 0.15F
                && Math.abs(com.sc.util.ArmorGasSC.coolingFactorOf(full) - 1.6F) < 1e-4
                && Math.abs(com.sc.util.ArmorGasSC.coolingFactorOf(exoFull) - 1.45F) < 1e-4
                && removed == (int) (com.sc.util.ArmorGasSC.HELIUM_PUMP[sg.ordinal()] * 1.6F) && removed == 51
                && Math.abs(used - removed / (25F * 1.6F)) < 0.01F;
        check(cool, "Singular helium: 1 mB per 25 heat (Exo 20), radiators +20% (x1.6 full set, Exo x1.45), pump " + removed + "/s for "
                + used + " mB");
        // gas -20% for the Exo functions (K10): ten dashes' hydrogen, a minute of krypton
        ItemStack[] sgGas = gasSuit(sg, true, true, true, true), exoGas = gasSuit(exo, true, true, true, true);
        fillSuit(sgGas, 100);
        fillSuit(exoGas, 100);
        int sgH2 = com.sc.util.ArmorGasSC.amountOf(sgGas, h2), exoH2 = com.sc.util.ArmorGasSC.amountOf(exoGas, h2);
        int sgKr = com.sc.util.ArmorGasSC.amountOf(sgGas, kr), exoKr = com.sc.util.ArmorGasSC.amountOf(exoGas, kr);
        boolean dashes = true;
        for (int i = 0; i < 10; i++) {
            dashes &= com.sc.util.ArmorGasSC.drainExactUse(sgGas, h2, com.sc.util.ArmorGasSC.H2_DASH)
                    && com.sc.util.ArmorGasSC.drainExactUse(exoGas, h2, com.sc.util.ArmorGasSC.H2_DASH);
        }
        for (int i = 0; i < 600; i++) {
            com.sc.util.ArmorGasSC.drainFractionUse(sgGas, kr, 1F / 6F);
            com.sc.util.ArmorGasSC.drainFractionUse(exoGas, kr, 1F / 6F);
        }
        int sgUsed = sgH2 - com.sc.util.ArmorGasSC.amountOf(sgGas, h2), exoUsed = exoH2 - com.sc.util.ArmorGasSC.amountOf(exoGas, h2);
        int sgKrUsed = sgKr - com.sc.util.ArmorGasSC.amountOf(sgGas, kr), exoKrUsed = exoKr - com.sc.util.ArmorGasSC.amountOf(exoGas, kr);
        check(dashes && exoUsed == 500 && sgUsed == 400 && Math.abs(exoKrUsed - 100) <= 1 && Math.abs(sgKrUsed - 80) <= 1
                        && com.sc.util.ArmorGasSC.gasUseMul(sgGas[1]) == 0.8F && com.sc.util.ArmorGasSC.gasUseMul(exoGas[1]) == 1F,
                "Singular gas -20%: 10 dashes " + sgUsed + " mB of hydrogen (Exo " + exoUsed + "), krypton " + sgKrUsed + " (Exo " + exoKrUsed + ")");
        // colour schemes: default A, set / get, an unknown value falls back to A; 11 schemes
        com.sc.util.SingularScheme[] all = com.sc.util.SingularScheme.values();
        ItemStack piece = new ItemStack(p[1]);
        boolean schemes = all.length == 11 && com.sc.util.SingularScheme.DEFAULT == com.sc.util.SingularScheme.A
                && com.sc.util.SingularScheme.of(piece) == com.sc.util.SingularScheme.A
                && com.sc.item.ItemArmorSC.textureBase(piece).equals("singular_a")
                && com.sc.item.ItemArmorSC.textureBase(new ItemStack(ModItems.ARMOR.get(exo)[1])).equals("exo")
                && sg.hasSchemes() && !exo.hasSchemes();
        com.sc.util.SingularScheme.setScheme(piece, com.sc.util.SingularScheme.D);
        schemes &= com.sc.util.SingularScheme.of(piece) == com.sc.util.SingularScheme.D && piece.getTagCompound().getInteger("SingScheme") == 3
                && com.sc.item.ItemArmorSC.textureBase(piece).equals("singular_d");
        piece.getTagCompound().setInteger("SingScheme", 99);
        schemes &= com.sc.util.SingularScheme.of(piece) == com.sc.util.SingularScheme.A && com.sc.util.SingularScheme.of(-1) == com.sc.util.SingularScheme.A
                && com.sc.util.SingularScheme.K.ordinal() == 10 && com.sc.util.SingularScheme.A.accent == 0xBE6EFF;
        com.sc.util.SingularScheme.setScheme(null, com.sc.util.SingularScheme.B);
        java.util.List<ItemStack> sub = new java.util.ArrayList<ItemStack>();
        p[0].getSubItems(p[0], null, sub);
        schemes &= sub.size() == 2 + all.length - 1;
        // every scheme's textures are there: 4 icons, 2 layers and their glow layers (8 frames, white, red)
        StringBuilder missing = new StringBuilder();
        String[] names = {"Helmet", "Chestplate", "Leggings", "Boots"};
        for (com.sc.util.SingularScheme s : all) {
            java.util.List<String> files = new java.util.ArrayList<String>();
            for (String n : names) {
                files.add("items/armorSingular" + n + "_" + s.key() + ".png");
            }
            for (int l = 1; l <= 2; l++) {
                String b = "models/armor/singular_" + s.key();
                files.add(b + "_layer_" + l + ".png");
                files.add(b + "_gloww_" + l + ".png");
                files.add(b + "_glowred_" + l + ".png");
                for (int f = 0; f < com.sc.client.ModelArmorGlowSC.FRAMES; f++) {
                    files.add(b + "_glow_" + l + "_" + f + ".png");
                }
            }
            for (String f : files) {
                if (SelfTestSC.class.getResource("/assets/siliconage/textures/" + f) == null) {
                    missing.append(' ').append(f);
                }
            }
        }
        for (String f : new String[]{"blocks/fluids/singularmatter_still.png", "blocks/fluids/singularmatter_flow.png"}) {
            if (SelfTestSC.class.getResource("/assets/siliconage/textures/" + f) == null) {
                missing.append(' ').append(f);
            }
        }
        check(schemes && missing.length() == 0, "Singular colour schemes: " + all.length + ", default A, set / get by NBT, unknown -> A, "
                + "creative tab has each; textures of every scheme" + (missing.length() > 0 ? " MISSING:" + missing : ""));
        // the station: an 8th tank, and an old station's gas switches turn the new gas on
        net.minecraftforge.common.util.ForgeDirection any = net.minecraftforge.common.util.ForgeDirection.UNKNOWN;
        com.sc.tileentity.TileEntityArmorStationSC st = new com.sc.tileentity.TileEntityArmorStationSC();
        boolean station = st.getTankInfo(any).length == 8 && smFluid != null
                && st.fill(any, new FluidStack(smFluid, 500), true) == 500 && st.tankAmount(sm) == 500
                && com.sc.tileentity.TileEntityArmorStationSC.ALL_GASES == 255
                && com.sc.tileentity.TileEntityArmorStationSC.migrateMask(127, 7) == 255
                && com.sc.tileentity.TileEntityArmorStationSC.migrateMask(5, 7) == (5 | 128)
                && com.sc.tileentity.TileEntityArmorStationSC.migrateMask(5, 8) == 5;
        net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
        st.writeToNBT(tag);
        station &= tag.getInteger("GasMaskN") == 8 && tag.getCompoundTag(com.sc.tileentity.TileEntityArmorStationSC.TANKS_KEY).getInteger(sm.key()) == 500;
        tag.setInteger("GasMask", 1 | 4);
        tag.removeTag("GasMaskN");                                   // saved before singular matter existed
        com.sc.tileentity.TileEntityArmorStationSC old = new com.sc.tileentity.TileEntityArmorStationSC();
        old.readFromNBT(tag);
        station &= old.getGasMask() == (1 | 4 | 128) && old.gasEnabled(sm) && !old.gasEnabled(o2) && old.tankAmount(sm) == 500;
        ItemStack[] slotsSuit = gasSuit(sg, false, true, false, false);
        old.setInventorySlotContents(1, slotsSuit[1]);
        station &= old.putGas(sm, 300, null) == 300 && com.sc.util.ArmorGasSC.amount(slotsSuit[1], sm) == 300;
        check(station, "station: 8 tanks (singular matter fills its own), saved with the gas count; an old station's switches turn the new gas on");
    }

    private static String ItemStackName(net.minecraft.item.Item item) {
        return item.getUnlocalizedName();
    }

    private static ItemStack[] gasSuit(com.sc.util.ArmorSuit suit, boolean helmet, boolean chest, boolean legs, boolean boots) {
        com.sc.item.ItemArmorSC[] a = ModItems.ARMOR.get(suit);
        boolean[] on = {helmet, chest, legs, boots};
        ItemStack[] w = new ItemStack[4];
        for (int t = 0; t < 4; t++) {
            w[t] = on[t] ? new ItemStack(a[t]) : null;
        }
        return w;
    }

    /** The Singular suit's own functions: stage 2a Н1, Н7, П3, П7, Б3, Б4, Б1, К9, Ш8, Н11, Н2, П1; stage 2b 10 more. */
    private static final int SINGULAR_OWN = 23;            // 22 + the bridge link (stage 2 of the bridge)

    /**
     * The Singular functions, stage 2a (docs/plan-singular-armor.md §3-§5): appended after the 25 old
     * ones, Singular only, the plan's pieces / levels / gases; the switch bits past 32 in a second
     * int (old pieces keep theirs); the level hook; the cooldown API; the phase dash's path on a mock
     * world; the plan's gas numbers without the K10 discount; О2.
     */
    private static void singularFunctions() {
        com.sc.util.ArmorFeature[] v = com.sc.util.ArmorFeature.values();
        com.sc.util.ArmorSuit sg = com.sc.util.ArmorSuit.SINGULAR, exo = com.sc.util.ArmorSuit.EXO;
        String[] order = {"GRAV_FLIGHT", "MAGNET", "GRAV_ANCHOR", "STABILIZER", "ANTIGRAV", "VOID_RESCUE", "GRAV_STRIKE",
                "WITHER_VOID", "CLEAR_SIGHT", "HEAT_VENT", "EVENT_HORIZON", "PHASE_DASH"};
        int[] pieces = {1, 1, 2, 2, 3, 3, 3, 1, 0, 1, 1, 2};
        int[] levels = {1, 1, 1, 1, 1, 1, 2, 1, 1, 3, 2, 2};
        com.sc.util.ArmorGasSC.Gas he = com.sc.util.ArmorGasSC.Gas.HELIUM, h2 = com.sc.util.ArmorGasSC.Gas.HYDROGEN,
                ar = com.sc.util.ArmorGasSC.Gas.ARGON, kr = com.sc.util.ArmorGasSC.Gas.KRYPTON, o2 = com.sc.util.ArmorGasSC.Gas.OXYGEN,
                d2o = com.sc.util.ArmorGasSC.Gas.HEAVY_WATER;
        com.sc.util.ArmorGasSC.Gas[] gases = {he, he, d2o, ar, h2, he, h2, o2, kr, ar, he, h2};
        float[] use = {1F, 1F, 1F, 0.5F, 0.5F, 500F, 1F, 2F, 1F, 200F, 5F, 50F};
        boolean layout = v.length == 25 + SINGULAR_OWN && v.length <= 64;
        String bad = "";
        for (int i = 0; i < order.length; i++) {
            com.sc.util.ArmorFeature f = com.sc.util.ArmorFeature.valueOf(order[i]);
            boolean ok = f.ordinal() == 25 + i && f.minSuit == sg && f.piece == pieces[i] && f.availableIn(sg, pieces[i])
                    && !f.availableIn(exo, pieces[i]) && com.sc.util.SingularLevel.requiredLevel(f) == levels[i]
                    && f.gas() == gases[i] && Math.abs(f.gasUse() - use[i]) < 1e-6 && f.isAction() == (f == com.sc.util.ArmorFeature.PHASE_DASH);
            if (!ok) {
                bad += " " + order[i];
            }
            layout &= ok;
        }
        layout &= com.sc.util.ArmorFeature.GRAV_FLIGHT.euPerSecond == 2000 && com.sc.util.ArmorFeature.MAGNET.euPerSecond == 20
                && com.sc.util.ArmorFeature.CLEAR_SIGHT.euPerSecond == 40 && com.sc.util.ArmorFeature.ANTIGRAV.euPerSecond == 100
                && com.sc.util.ArmorFeature.MAGNET.gasUseKind() == com.sc.util.ArmorFeature.USE_MINUTE
                && com.sc.util.ArmorFeature.GRAV_FLIGHT.gasUseKind() == com.sc.util.ArmorFeature.USE_SECOND
                && com.sc.util.ArmorFeature.GRAV_STRIKE.gasPerPoint() && com.sc.util.ArmorFeature.PHASE_DASH.gasMin() == 50
                && com.sc.util.ArmorFeature.HEAT_VENT.gasMin() == 200 && com.sc.util.ArmorFeature.WITHER_VOID.needsFullSet()
                && com.sc.util.ArmorFeature.GRAV_FLIGHT.offOnLowCharge() && com.sc.util.ArmorFeature.EVENT_HORIZON.offOnLowCharge()
                && com.sc.util.ArmorFeature.GRAV_ANCHOR.offOnLowCharge() && !com.sc.util.ArmorFeature.MAGNET.offOnLowCharge()
                && com.sc.util.SingularLevel.requiredLevel(com.sc.util.ArmorFeature.FLIGHT) == 1;
        check(layout, "Singular functions: 12 appended (25..36, stage 2a), Singular only, the plan's pieces, levels, gases and rates" + bad);

        // the switch bits: an old piece keeps its "FnToggled", bits 32+ go to "FnToggled2"
        ItemStack chest = new ItemStack(ModItems.ARMOR.get(sg)[1]), helmet = new ItemStack(ModItems.ARMOR.get(sg)[0]);
        helmet.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
        helmet.getTagCompound().setInteger("FnToggled", (1 << com.sc.util.ArmorFeature.THERMAL.ordinal())
                | (1 << com.sc.util.ArmorFeature.NIGHT_VISION.ordinal()));            // as saved before stage 2a
        boolean bits = com.sc.item.ItemArmorSC.isEnabled(helmet, com.sc.util.ArmorFeature.THERMAL)
                && !com.sc.item.ItemArmorSC.isEnabled(helmet, com.sc.util.ArmorFeature.NIGHT_VISION)
                && !com.sc.item.ItemArmorSC.isEnabled(helmet, com.sc.util.ArmorFeature.CLEAR_SIGHT);
        com.sc.item.ItemArmorSC.setEnabled(helmet, com.sc.util.ArmorFeature.CLEAR_SIGHT, true);         // ordinal 33
        bits &= com.sc.item.ItemArmorSC.isEnabled(helmet, com.sc.util.ArmorFeature.CLEAR_SIGHT)
                && helmet.getTagCompound().getInteger("FnToggled2") == 1 << (33 - 32)
                && helmet.getTagCompound().getInteger("FnToggled") == ((1 << 6) | 1)
                && com.sc.item.ItemArmorSC.isEnabled(helmet, com.sc.util.ArmorFeature.THERMAL);
        com.sc.item.ItemArmorSC.setEnabled(helmet, com.sc.util.ArmorFeature.CLEAR_SIGHT, false);
        bits &= !helmet.getTagCompound().hasKey("FnToggled2") && !com.sc.item.ItemArmorSC.isEnabled(helmet, com.sc.util.ArmorFeature.CLEAR_SIGHT);
        com.sc.item.ItemArmorSC.setEnabled(chest, com.sc.util.ArmorFeature.GRAV_STRIKE, false);         // ordinal 31: the sign bit
        ItemStack boots = new ItemStack(ModItems.ARMOR.get(sg)[3]);
        com.sc.item.ItemArmorSC.setEnabled(boots, com.sc.util.ArmorFeature.GRAV_STRIKE, false);
        bits &= !com.sc.item.ItemArmorSC.isEnabled(boots, com.sc.util.ArmorFeature.GRAV_STRIKE)
                && boots.getTagCompound().getInteger("FnToggled") == Integer.MIN_VALUE
                && com.sc.item.ItemArmorSC.isEnabled(boots, com.sc.util.ArmorFeature.ANTIGRAV)
                && com.sc.item.ItemArmorSC.isEnabled(chest, com.sc.util.ArmorFeature.EVENT_HORIZON)
                && !com.sc.item.ItemArmorSC.isEnabled(chest, com.sc.util.ArmorFeature.MAGNET)
                && !com.sc.item.ItemArmorSC.isEnabled(new ItemStack(ModItems.ARMOR.get(exo)[1]), com.sc.util.ArmorFeature.GRAV_FLIGHT)
                && com.sc.item.ItemArmorSC.toggled(null, 40) == false;
        check(bits, "Singular functions: switch bits 0..31 in FnToggled as before (old pieces keep theirs), 32+ in FnToggled2, bit 31 works");

        // the level hook: default 1, clamped 1..5, creative counts as 5
        ItemStack legs = new ItemStack(ModItems.ARMOR.get(sg)[2]);
        boolean levels2 = com.sc.util.SingularLevel.levelOf(legs) == 1 && com.sc.util.SingularLevel.levelOf(null) == 1
                && !com.sc.util.SingularLevel.unlocked(com.sc.util.ArmorFeature.PHASE_DASH, 1, false)
                && com.sc.util.SingularLevel.unlocked(com.sc.util.ArmorFeature.PHASE_DASH, 1, true)
                && com.sc.util.SingularLevel.unlocked(com.sc.util.ArmorFeature.GRAV_ANCHOR, 1, false)
                && com.sc.util.SingularLevel.unlocked(com.sc.util.ArmorFeature.DASH, 1, false);
        com.sc.util.SingularLevel.setLevel(legs, 2);
        levels2 &= com.sc.util.SingularLevel.levelOf(legs) == 2 && com.sc.util.SingularLevel.unlocked(null, com.sc.util.ArmorFeature.PHASE_DASH, legs)
                && !com.sc.util.SingularLevel.unlocked(com.sc.util.ArmorFeature.HEAT_VENT, 2, false);
        legs.getTagCompound().setInteger("SingLevel", 9);
        levels2 &= com.sc.util.SingularLevel.levelOf(legs) == 5;
        legs.getTagCompound().setInteger("SingLevel", -3);
        levels2 &= com.sc.util.SingularLevel.levelOf(legs) == 1;
        ItemStack[] w = gasSuit(sg, true, true, true, true);
        for (ItemStack s : w) {
            com.sc.item.ItemArmorSC.setCharge(s, com.sc.item.ItemArmorSC.capacityOf(s));
        }
        fillSuit(w, 50);
        levels2 &= com.sc.item.ArmorLogicSC.worksIn(w, com.sc.util.ArmorFeature.GRAV_FLIGHT)
                && com.sc.item.ArmorLogicSC.worksIn(w, com.sc.util.ArmorFeature.PHASE_DASH)          // the gases allow it (the level: active())
                && com.sc.item.ArmorLogicSC.missingGas(w, com.sc.util.ArmorFeature.HEAT_VENT) == null;
        com.sc.util.ArmorGasSC.setAmount(w[1], ar, 150);                                              // under a vent's 200 mB of argon
        levels2 &= com.sc.item.ArmorLogicSC.missingGas(w, com.sc.util.ArmorFeature.HEAT_VENT) == ar
                && com.sc.item.ArmorLogicSC.missingGas(w, com.sc.util.ArmorFeature.STABILIZER) == null;
        fillSuit(w, 0);
        levels2 &= com.sc.item.ArmorLogicSC.emergency(w) && !com.sc.item.ArmorLogicSC.gasAllows(w, com.sc.util.ArmorFeature.GRAV_FLIGHT)
                && com.sc.item.ArmorLogicSC.flightCutByGas(w);                                       // no helium: Н1 cut with the soft descent
        check(levels2, "Singular levels: NBT SingLevel default 1, clamped 1..5, the plan's level per function, creative = 5; "
                + "H1 / P1 / H11 by their gases, emergency cuts H1");

        // cooldowns: the pure part and the client's copy
        com.sc.util.SingularCooldowns.clientSet(-1, 0);
        boolean cool = com.sc.util.SingularCooldowns.left(1000, 940) == 60 && com.sc.util.SingularCooldowns.left(1000, 1000) == 0
                && com.sc.util.SingularCooldowns.left(0, 5) == 0 && com.sc.util.SingularCooldowns.clientEnd(35) == 0;
        com.sc.util.SingularCooldowns.clientSet(com.sc.util.ArmorFeature.PHASE_DASH.ordinal(), 12345L);
        cool &= com.sc.util.SingularCooldowns.clientEnd(com.sc.util.ArmorFeature.PHASE_DASH.ordinal()) == 12345L;
        com.sc.util.SingularCooldowns.clientSet(-1, 0);
        cool &= com.sc.util.SingularCooldowns.clientEnd(com.sc.util.ArmorFeature.PHASE_DASH.ordinal()) == 0
                && com.sc.util.ArmorFeature.PHASE_DASH_COOLDOWN == 60 && com.sc.util.ArmorFeature.VOID_RESCUE_COOLDOWN == 6000
                && com.sc.util.ArmorFeature.HEAT_VENT_COOLDOWN == 1200 && !com.sc.item.ArmorLogicSC.timeSlowActive(null);
        check(cool, "Singular cooldowns: ticks left, the client's copy set / cleared; P1 3 s, B4 5 min, H11 1 min; C3 hook off");

        // the phase dash's path: a wall at x = 5 (a body 0.6 wide), open space, a thin wall with room behind it
        com.sc.item.ArmorLogicSC.SpaceCheck wall = new com.sc.item.ArmorLogicSC.SpaceCheck() {
            @Override
            public boolean free(double x, double y, double z) {
                return x + 0.3 < 5.0 || x - 0.3 >= 5.5;            // the wall is x 5..5.5; free beyond it
            }
        };
        com.sc.item.ArmorLogicSC.SpaceCheck open = new com.sc.item.ArmorLogicSC.SpaceCheck() {
            @Override
            public boolean free(double x, double y, double z) {
                return y >= 0;
            }
        };
        double toWall = com.sc.item.ArmorLogicSC.phaseDistance(wall, 0.5, 64, 0.5, 1, 0, 0, 16);
        double far = com.sc.item.ArmorLogicSC.phaseDistance(open, 0.5, 64, 0.5, 3, 0, 4, 16);
        double down = com.sc.item.ArmorLogicSC.phaseDistance(open, 0.5, 2, 0.5, 0, -1, 0, 16);
        double none = com.sc.item.ArmorLogicSC.phaseDistance(wall, 4.6, 64, 0.5, 1, 0, 0, 16);
        double still = com.sc.item.ArmorLogicSC.phaseDistance(open, 0.5, 64, 0.5, 0, 0, 0, 16);
        check(Math.abs(toWall - 4.0) < 1e-9 && Math.abs(far - 16.0) < 1e-9 && Math.abs(down - 2.0) < 1e-9 && none == 0 && still == 0,
                "phase dash path: stops before a wall (" + toWall + ", never behind it), 16 blocks in the open (" + far + "), "
                        + "the ground (" + down + "), blocked (" + none + ")");

        // the plan's gas numbers, without the -20% of the Exo legacy; О2; the magnet's grace; the vent
        ItemStack[] g = gasSuit(sg, true, true, true, true);
        fillSuit(g, 100);
        int he0 = com.sc.util.ArmorGasSC.amountOf(g, he), ar0 = com.sc.util.ArmorGasSC.amountOf(g, ar), h20 = com.sc.util.ArmorGasSC.amountOf(g, h2);
        for (int i = 0; i < 60; i++) {
            com.sc.util.ArmorGasSC.drainFraction(g, he, com.sc.util.ArmorFeature.SING_HE_FLIGHT_PER_SECOND);     // a minute of Н1
            com.sc.util.ArmorGasSC.drainFraction(g, ar, com.sc.util.ArmorFeature.SING_AR_STABILIZER_PER_SECOND); // a minute in a web
        }
        com.sc.util.ArmorGasSC.drainExact(g, h2, com.sc.util.ArmorFeature.SING_H2_PHASE);
        boolean nums = he0 - com.sc.util.ArmorGasSC.amountOf(g, he) == 60 && ar0 - com.sc.util.ArmorGasSC.amountOf(g, ar) == 30
                && h20 - com.sc.util.ArmorGasSC.amountOf(g, h2) == 50
                && com.sc.item.ArmorLogicSC.lowCharge(6399999, 64000000) && !com.sc.item.ArmorLogicSC.lowCharge(6400000, 64000000)
                && !com.sc.item.ArmorLogicSC.lowCharge(0, 0)
                && com.sc.item.ArmorLogicSC.ventedHeat(1000) == 500 && com.sc.item.ArmorLogicSC.ventedHeat(999) == 499
                && !com.sc.item.ArmorLogicSC.magnetPulls("a", "a", 5) && com.sc.item.ArmorLogicSC.magnetPulls("a", "a", 20)
                && com.sc.item.ArmorLogicSC.magnetPulls("a", "b", 0) && com.sc.item.ArmorLogicSC.magnetPulls("a", "", 0)
                && com.sc.util.ArmorFeature.HORIZON_SHARE == 0.3F && com.sc.util.ArmorFeature.SING_HE_PER_PROJECTILE == 5
                && com.sc.util.ArmorFeature.SING_H2_AIR_JUMP == 10 && com.sc.util.ArmorFeature.ANTIGRAV_AIR_JUMP_EU == 5000
                && com.sc.util.ArmorFeature.PHASE_DASH_EU == 50000 && com.sc.util.ArmorFeature.PHASE_DASH_RANGE == 16.0
                && com.sc.util.ArmorFeature.SING_HE_VOID_RESCUE == 500 && com.sc.util.ArmorFeature.VOID_RESCUE_CHARGE == 0.10F
                && com.sc.util.ArmorFeature.GRAV_FLIGHT_SPEED_MUL == 3F && com.sc.util.ArmorFeature.MAGNET_RADIUS == 8.0;
        check(nums, "Singular gas: H1 flight 60 mB of helium a minute, P7 30 mB of argon, P1 50 mB of hydrogen (no -20%); "
                + "O2 under 10% charge; magnet leaves a just-thrown item; vent halves the heat (used " + (he0 - com.sc.util.ArmorGasSC.amountOf(g, he))
                + "/" + (ar0 - com.sc.util.ArmorGasSC.amountOf(g, ar)) + "/" + (h20 - com.sc.util.ArmorGasSC.amountOf(g, h2)) + ")");

        // every new function named and described in both languages, the new chat / screen lines too
        String missing = "";
        for (String lang : new String[]{"en_US", "ru_RU"}) {
            java.util.Set<String> keys = langKeys(lang);
            java.util.List<String> want = new java.util.ArrayList<String>();
            for (String n : order) {
                String k = "sc.armorfn." + n.toLowerCase(java.util.Locale.ROOT);
                want.add(k);
                want.add(k + ".desc");
            }
            java.util.Collections.addAll(want, "sc.armor.cooldown", "sc.armor.voidrescue", "sc.armor.voidrescue.spawn", "sc.armor.voidrescue.cant",
                    "sc.armor.heatvent", "sc.armor.phase.cant", "sc.armor.phase.blocked", "sc.armorkey.locked", "sc.armorgui.row.locked",
                    "sc.armorgui.row.replaced", "sc.armorgui.tip.locked", "sc.armorgui.tip.cooldown", "sc.tooltip.armor.singular.level",
                    "sc.tooltip.armor.singular.at", "sc.manual.singular.fnhead", "sc.manual.singular.fnline");
            for (String k : want) {
                if (!keys.contains(k)) {
                    missing += " " + lang + ":" + k;
                }
            }
        }
        check(missing.isEmpty(), "Singular functions: names, descriptions, chat and screen texts in en_US and ru_RU" + missing);
    }

    /**
     * Stage 2b (docs/plan-singular-armor.md §3-§5): Н10, Н8, Н4, Н3, Н17, К1, К2, Ш1, Ш2, Ш5 appended
     * (37..46) with the plan's pieces / levels / gases; the branch hook (Р2); К1's multipliers; the
     * black hole's damage; the scanner's cap; the analyzer's weakness flags; the resonance sources on
     * a field generator without a world; the new messages written and read back; the texts.
     */
    private static void singularFunctions2b() {
        com.sc.util.ArmorSuit sg = com.sc.util.ArmorSuit.SINGULAR, exo = com.sc.util.ArmorSuit.EXO;
        com.sc.util.ArmorGasSC.Gas he = com.sc.util.ArmorGasSC.Gas.HELIUM, kr = com.sc.util.ArmorGasSC.Gas.KRYPTON,
                sm = com.sc.util.ArmorGasSC.Gas.SINGULAR_MATTER;
        String[] order = {"GRAV_PRESS", "GRAV_GRAB", "TIME_SLOW", "BLACK_HOLE", "GRAV_DOME", "SINGULARITY", "RESONANCE",
                "GRAV_SCANNER", "THREAT_SENSE", "ANALYZER"};
        int[] pieces = {1, 1, 1, 1, 1, 1, 1, 0, 0, 0};
        int[] levels = {3, 3, 4, 5, 5, 5, 4, 3, 3, 2};
        com.sc.util.ArmorGasSC.Gas[] gases = {he, he, he, he, he, sm, null, kr, kr, kr};
        float[] use = {100F, 50F, 500F, 1000F, 300F, 200F, 0F, 2F, 0.5F, 1F};
        boolean[] action = {true, true, true, true, true, true, false, false, false, false};
        int[] eu = {0, 0, 0, 0, 0, 0, 0, 200, 20, 0};
        int[] heat = {60, 0, 200, 300, 0, 100, 0, 1, 0, 0};
        com.sc.util.ArmorFeature[] v = com.sc.util.ArmorFeature.values();
        boolean layout = v.length == 25 + SINGULAR_OWN && v.length <= 64;
        String bad = "";
        for (int i = 0; i < order.length; i++) {
            com.sc.util.ArmorFeature f = com.sc.util.ArmorFeature.valueOf(order[i]);
            boolean ok = f.ordinal() == 37 + i && f.minSuit == sg && f.piece == pieces[i] && f.availableIn(sg, pieces[i])
                    && !f.availableIn(exo, pieces[i]) && com.sc.util.SingularLevel.requiredLevel(f) == levels[i]
                    && f.gas() == gases[i] && Math.abs(f.gasUse() - use[i]) < 1e-6 && f.isAction() == action[i]
                    && f.euPerSecond == eu[i] && f.heat == heat[i]
                    && f.needsFullSet() == (f == com.sc.util.ArmorFeature.SINGULARITY || f == com.sc.util.ArmorFeature.RESONANCE)
                    && (com.sc.handler.ArmorNetSC.actionOf(f) >= 0) == action[i];
            if (!ok) {
                bad += " " + order[i];
            }
            layout &= ok;
        }
        layout &= com.sc.util.ArmorFeature.GRAV_SCANNER.gasUseKind() == com.sc.util.ArmorFeature.USE_MINUTE
                && com.sc.util.ArmorFeature.THREAT_SENSE.gasUseKind() == com.sc.util.ArmorFeature.USE_MINUTE
                && com.sc.util.ArmorFeature.ANALYZER.gasUseKind() == com.sc.util.ArmorFeature.USE_ONCE
                && com.sc.util.ArmorFeature.BLACK_HOLE.gasUseKind() == com.sc.util.ArmorFeature.USE_ONCE
                && !com.sc.util.ArmorFeature.GRAV_SCANNER.onByDefault && !com.sc.util.ArmorFeature.THREAT_SENSE.onByDefault
                && com.sc.util.ArmorFeature.ANALYZER.onByDefault && com.sc.util.ArmorFeature.RESONANCE.onByDefault;
        check(layout, "Singular functions stage 2b: 10 appended (37..46), Singular only, the plan's pieces, levels, gases, keys" + bad);

        // the plan's numbers (§4: costs, cooldowns, reaches)
        boolean nums = com.sc.util.ArmorFeature.SING_HE_PRESS == 100 && com.sc.util.ArmorFeature.SING_D_PRESS == 50
                && com.sc.util.ArmorFeature.PRESS_EU == 200000 && com.sc.util.ArmorFeature.PRESS_COOLDOWN == 600
                && com.sc.util.ArmorFeature.PRESS_TICKS == 100 && com.sc.util.ArmorFeature.PRESS_RADIUS == 6.0
                && com.sc.util.ArmorFeature.SING_HE_GRAB == 50 && com.sc.util.ArmorFeature.GRAB_EU == 100000
                && com.sc.util.ArmorFeature.GRAB_COOLDOWN == 200 && com.sc.util.ArmorFeature.GRAB_TICKS == 120 && com.sc.util.ArmorFeature.GRAB_RANGE == 8.0
                && com.sc.util.ArmorFeature.SING_KR_SLOW == 100 && com.sc.util.ArmorFeature.SING_HE_SLOW == 500 && com.sc.util.ArmorFeature.SING_SM_SLOW == 50
                && com.sc.util.ArmorFeature.SLOW_CHARGE == 0.10F && com.sc.util.ArmorFeature.SLOW_COOLDOWN == 3600 && com.sc.util.ArmorFeature.SLOW_TICKS == 120
                && com.sc.util.ArmorFeature.SLOW_RADIUS == 16.0 && com.sc.util.ArmorFeature.SLOW_FACTOR == 0.2F && com.sc.util.ArmorFeature.AGGRO_RADIUS == 48.0
                && com.sc.util.ArmorFeature.SING_D_HOLE == 500 && com.sc.util.ArmorFeature.SING_HE_HOLE == 1000 && com.sc.util.ArmorFeature.SING_SM_HOLE == 100
                && com.sc.util.ArmorFeature.HOLE_CHARGE == 0.25F && com.sc.util.ArmorFeature.HOLE_COOLDOWN == 2400 && com.sc.util.ArmorFeature.HOLE_TICKS == 200
                && com.sc.util.ArmorFeature.HOLE_RANGE == 24.0 && com.sc.util.ArmorFeature.HOLE_RADIUS == 10.0 && com.sc.util.ArmorFeature.HOLE_COLLAPSE_RADIUS == 4.0
                && com.sc.util.ArmorFeature.SING_HE_DOME == 300 && com.sc.util.ArmorFeature.SING_D_DOME == 200 && com.sc.util.ArmorFeature.DOME_CHARGE == 0.10F
                && com.sc.util.ArmorFeature.DOME_COOLDOWN == 2400 && com.sc.util.ArmorFeature.DOME_TICKS == 160 && com.sc.util.ArmorFeature.DOME_RADIUS == 5.0
                && com.sc.util.ArmorFeature.SING_SM_BOOST == 200 && com.sc.util.ArmorFeature.SING_D_BOOST == 1000 && com.sc.util.ArmorFeature.BOOST_CHARGE == 0.20F
                && com.sc.util.ArmorFeature.BOOST_COOLDOWN == 12000 && com.sc.util.ArmorFeature.BOOST_TICKS == 300 && com.sc.util.ArmorFeature.WEAK_TICKS == 1200
                && com.sc.util.ArmorFeature.RESONANCE_RADIUS == 16 && com.sc.util.ArmorFeature.RESONANCE_EU == 20000 && com.sc.util.ArmorFeature.RESONANCE_COOL == 10
                && com.sc.util.ArmorFeature.RES_HE_PER_SECOND == 2F && com.sc.util.ArmorFeature.RES_D_PER_SECOND == 0.5F
                && com.sc.util.ArmorFeature.SING_KR_PER_PULSE == 10 && com.sc.util.ArmorFeature.SCANNER_RADIUS == 32 && com.sc.util.ArmorFeature.SCANNER_EVERY == 100
                && com.sc.util.ArmorFeature.SCANNER_MAX_BLOCKS == 256 && com.sc.util.ArmorFeature.SCANNER_MAX_MOBS == 64
                && com.sc.util.ArmorFeature.ANALYZE_EU == 1000 && com.sc.util.ArmorFeature.ANALYZE_MOB_RANGE == 16.0 && com.sc.util.ArmorFeature.ANALYZE_BLOCK_RANGE == 8.0;
        check(nums, "Singular stage 2b numbers: press, grab, time slowing, black hole, dome, Singularity, resonance, scanner, analyzer as in the plan");

        // the branches (Р2): nothing chosen on a new chestplate - both sides locked (stage 3)
        ItemStack chest = new ItemStack(ModItems.ARMOR.get(sg)[1]);
        boolean branch = com.sc.util.SingularLevel.branchChoice(chest, 3) == com.sc.util.SingularLevel.BRANCH_NONE
                && com.sc.util.SingularLevel.branchChoice(chest, 5) == com.sc.util.SingularLevel.BRANCH_NONE
                && com.sc.util.SingularLevel.branchOf(com.sc.util.ArmorFeature.GRAV_PRESS) == com.sc.util.SingularLevel.BRANCH_A
                && com.sc.util.SingularLevel.branchOf(com.sc.util.ArmorFeature.GRAV_GRAB) == com.sc.util.SingularLevel.BRANCH_B
                && com.sc.util.SingularLevel.branchOf(com.sc.util.ArmorFeature.BLACK_HOLE) == com.sc.util.SingularLevel.BRANCH_A
                && com.sc.util.SingularLevel.branchOf(com.sc.util.ArmorFeature.GRAV_DOME) == com.sc.util.SingularLevel.BRANCH_B
                && com.sc.util.SingularLevel.branchOf(com.sc.util.ArmorFeature.TIME_SLOW) == com.sc.util.SingularLevel.BRANCH_BOTH
                && com.sc.util.SingularLevel.branchAllows(1, 1) && !com.sc.util.SingularLevel.branchAllows(1, 2)
                && !com.sc.util.SingularLevel.branchAllows(2, 0) && com.sc.util.SingularLevel.branchAllows(0, 2);
        for (com.sc.util.ArmorFeature f : com.sc.util.ArmorFeature.values()) {
            branch &= com.sc.util.SingularLevel.branchAllowed(f, chest) == (com.sc.util.SingularLevel.branchOf(f) == com.sc.util.SingularLevel.BRANCH_BOTH);
        }
        check(branch, "Singular branches (P2): press / grab at level 3, black hole / dome at level 5, both locked until chosen");

        // К1: the multipliers, the shield share, the black hole's damage
        boolean boost = com.sc.item.SingularPowersSC.boostAt(100, 400, 1600) == 2F && com.sc.item.SingularPowersSC.boostAt(400, 400, 1600) == 0.5F
                && com.sc.item.SingularPowersSC.boostAt(1599, 400, 1600) == 0.5F && com.sc.item.SingularPowersSC.boostAt(1600, 400, 1600) == 1F
                && com.sc.item.SingularPowersSC.boostAt(5, 0, 0) == 1F
                && com.sc.item.SingularPowersSC.rangeMul(2F) == 1.5F && com.sc.item.SingularPowersSC.rangeMul(0.5F) == 0.75F
                && com.sc.item.SingularPowersSC.rangeMul(1F) == 1F
                && com.sc.item.SingularPowersSC.costMulFor(2F) == 2F && com.sc.item.SingularPowersSC.costMulFor(0.5F) == 1F
                && com.sc.item.SingularPowersSC.costMulFor(1F) == 1F && com.sc.item.SingularPowersSC.singularBoost(null) == 1F
                && Math.abs(com.sc.item.ArmorLogicSC.horizonShare(2F) - 0.6F) < 1e-6 && Math.abs(com.sc.item.ArmorLogicSC.horizonShare(1F) - 0.3F) < 1e-6
                && Math.abs(com.sc.item.ArmorLogicSC.horizonShare(0.5F) - 0.15F) < 1e-6
                && !com.sc.item.SingularPowersSC.timeSlowActive(null) && com.sc.item.SingularPowersSC.fieldCount() == 0
                && Math.abs(com.sc.item.SingularPowersSC.holeDamage(0, 10, 0, 200) - 3F) < 1e-5
                && Math.abs(com.sc.item.SingularPowersSC.holeDamage(0, 10, 200, 200) - 9F) < 1e-5
                && com.sc.item.SingularPowersSC.holeDamage(5, 10, 100, 200) == 0F && com.sc.item.SingularPowersSC.holeDamage(7, 10, 100, 200) == 0F
                && com.sc.item.SingularPowersSC.holeDamage(4.9, 10, 0, 200) == 1F
                && com.sc.item.SingularPowersSC.holeDamage(2, 10, 100, 200) > com.sc.item.SingularPowersSC.holeDamage(4, 10, 100, 200);
        check(boost, "Singularity mode: x2 for 15 s, x0.5 for 60 s, then x1; reach x1.5 / x0.75; use x2 only boosted; "
                + "horizon 60% / 30% / 15%; black hole damage grows toward the centre and with time");

        // Ш1: chests and spawners first, then the nearest ores; never more than the cap
        java.util.List<int[]> found = new java.util.ArrayList<int[]>();
        for (int i = 0; i < 300; i++) {
            found.add(new int[]{i, 10, 0, com.sc.util.SingularSenseData.ORE, 1000 - i});
        }
        found.add(new int[]{0, 20, 0, com.sc.util.SingularSenseData.CHEST, 5000});
        found.add(new int[]{0, 21, 0, com.sc.util.SingularSenseData.SPAWNER, 4000});
        java.util.List<int[]> shown = com.sc.item.SingularSensesSC.capScan(found, 256);
        boolean cap = shown.size() == 256 && shown.get(0)[3] == com.sc.util.SingularSenseData.SPAWNER
                && shown.get(1)[3] == com.sc.util.SingularSenseData.CHEST && shown.get(2)[4] == 701 && shown.get(255)[4] == 954
                && com.sc.item.SingularSensesSC.capScan(found.subList(0, 10), 256).size() == 10 && found.size() == 302;
        cap &= com.sc.item.SingularSensesSC.oreKind(net.minecraft.init.Blocks.diamond_ore) == com.sc.util.SingularSenseData.ORE
                && com.sc.item.SingularSensesSC.oreKind(net.minecraft.init.Blocks.lit_redstone_ore) == com.sc.util.SingularSenseData.ORE
                && com.sc.item.SingularSensesSC.oreKind(com.sc.init.ModBlocks.oreSC) == com.sc.util.SingularSenseData.MOD_ORE
                && com.sc.item.SingularSensesSC.oreKind(net.minecraft.init.Blocks.stone) < 0;
        check(cap, "gravity scanner: capped at 256, chests and spawners first, then the nearest ores; vanilla and the mod's ores known");

        // Ш5: weaknesses; К2: a field generator as a source (no world needed)
        int all = com.sc.item.SingularSensesSC.weaknessFlags(true, true, true, true, true, true);
        boolean senses = com.sc.item.SingularSensesSC.weaknessFlags(false, false, false, false, false, false) == 0
                && com.sc.item.SingularSensesSC.weaknessFlags(true, false, false, false, true, false)
                == (com.sc.util.SingularSenseData.Analysis.WEAK_WATER | com.sc.util.SingularSenseData.Analysis.FIRE_IMMUNE)
                && all == 63 && com.sc.util.SingularSenseData.fresh(1000, 1500, 1000) && !com.sc.util.SingularSenseData.fresh(1000, 2501, 1000)
                && !com.sc.util.SingularSenseData.fresh(0, 10, 1000) && !com.sc.util.SingularSenseData.fresh(2000, 1000, 1000);
        com.sc.tileentity.TileEntityFieldGeneratorSC field = new com.sc.tileentity.TileEntityFieldGeneratorSC();
        field.setPowerOn(false);
        field.setEnergyStoredClient(5000);
        int off = com.sc.item.SingularSensesSC.resonanceKind(field);
        field.setPowerOn(true);
        int on = com.sc.item.SingularSensesSC.resonanceKind(field);
        field.setEnergyStoredClient(0);
        int empty = com.sc.item.SingularSensesSC.resonanceKind(field);
        senses &= off == 0 && on == 1 && empty == 0 && com.sc.item.SingularSensesSC.resonanceKind(null) == 0;
        check(senses, "analyzer weakness flags; resonance: a powered, switched-on field generator counts (" + off + "/" + on + "/" + empty + ")");

        // the new messages: action bytes, and written / read back
        boolean net = com.sc.handler.ArmorNetSC.featureOfAction(com.sc.handler.ArmorNetSC.BLACK_HOLE) == com.sc.util.ArmorFeature.BLACK_HOLE
                && com.sc.handler.ArmorNetSC.featureOfAction(com.sc.handler.ArmorNetSC.PHASE_DASH) == null
                && com.sc.handler.ArmorNetSC.featureOfAction((byte) 20) == null
                && com.sc.handler.ArmorNetSC.actionOf(com.sc.util.ArmorFeature.PHASE_DASH) == -1;
        java.util.Set<Byte> bytes = new java.util.HashSet<Byte>();
        for (com.sc.util.ArmorFeature f : com.sc.util.ArmorFeature.values()) {
            byte a = com.sc.handler.ArmorNetSC.actionOf(f);
            if (a >= 0) {
                net &= bytes.add(a) && a >= 15 && a != 20 && com.sc.handler.ArmorNetSC.featureOfAction(a) == f;
            }
        }
        io.netty.buffer.ByteBuf buf = io.netty.buffer.Unpooled.buffer();
        new com.sc.handler.ArmorNetSC.ScanMessage(new int[]{-5, 12, 300, 1, 7, 255, -9, 3}, new int[]{42, 1, 43, 0}).toBytes(buf);
        com.sc.handler.ArmorNetSC.ScanMessage scan = new com.sc.handler.ArmorNetSC.ScanMessage();
        scan.fromBytes(buf);
        net &= java.util.Arrays.equals(scan.blocks, new int[]{-5, 12, 300, 1, 7, 255, -9, 3}) && java.util.Arrays.equals(scan.mobs, new int[]{42, 1, 43, 0});
        buf.clear();
        new com.sc.handler.ArmorNetSC.ThreatMessage(new int[]{7, 8, 9}).toBytes(buf);
        com.sc.handler.ArmorNetSC.ThreatMessage threat = new com.sc.handler.ArmorNetSC.ThreatMessage();
        threat.fromBytes(buf);
        net &= java.util.Arrays.equals(threat.ids, new int[]{7, 8, 9});
        com.sc.util.SingularSenseData.Analysis a = new com.sc.util.SingularSenseData.Analysis();
        a.kind = com.sc.util.SingularSenseData.Analysis.MACHINE;
        a.x = -100;
        a.y = 70;
        a.z = 2000;
        a.stored = 123456;
        a.capacity = 1000000;
        a.status = 1;
        a.progress = 45;
        a.output = -1;
        a.powerOn = false;
        buf.clear();
        new com.sc.handler.ArmorNetSC.AnalyzeMessage(a).toBytes(buf);
        com.sc.handler.ArmorNetSC.AnalyzeMessage back = new com.sc.handler.ArmorNetSC.AnalyzeMessage();
        back.fromBytes(buf);
        net &= back.a.kind == a.kind && back.a.x == -100 && back.a.y == 70 && back.a.z == 2000 && back.a.stored == 123456
                && back.a.capacity == 1000000 && back.a.status == 1 && back.a.progress == 45 && back.a.output == -1 && !back.a.powerOn;
        a = new com.sc.util.SingularSenseData.Analysis();
        a.kind = com.sc.util.SingularSenseData.Analysis.MOB;
        a.entityId = 99;
        a.health = 12.5F;
        a.maxHealth = 20F;
        a.armor = 2;
        a.attack = 3F;
        a.flags = 5;
        buf.clear();
        new com.sc.handler.ArmorNetSC.AnalyzeMessage(a).toBytes(buf);
        back.fromBytes(buf);
        net &= back.a.kind == a.kind && back.a.entityId == 99 && back.a.health == 12.5F && back.a.maxHealth == 20F && back.a.armor == 2
                && back.a.attack == 3F && back.a.flags == 5;
        check(net, "Singular stage 2b network: key action bytes 15..21 (not 14 / 20), scan / threat / analyzer messages read back as written");

        // texts: every new function and line in both languages
        String missing = "";
        for (String lang : new String[]{"en_US", "ru_RU"}) {
            java.util.Set<String> keys = langKeys(lang);
            java.util.List<String> want = new java.util.ArrayList<String>();
            for (String n : order) {
                String k = "sc.armorfn." + n.toLowerCase(java.util.Locale.ROOT);
                want.add(k);
                want.add(k + ".desc");
            }
            java.util.Collections.addAll(want, "sc.armor.sing.unavailable", "sc.armor.sing.unavailable.set", "sc.armor.sing.branch",
                    "sc.armor.sing.nogas", "sc.armor.sing.noeu", "sc.armor.sing.nocharge", "sc.armor.press", "sc.armor.grab",
                    "sc.armor.grab.none", "sc.armor.timeslow", "sc.armor.blackhole", "sc.armor.dome", "sc.armor.singularity",
                    "sc.armor.singularity.weak", "sc.armor.singularity.end", "sc.armor.resonance", "sc.armor.resonance.lost",
                    "sc.armor.analyzer.cant", "sc.analyzer.health", "sc.analyzer.armor", "sc.analyzer.attack", "sc.analyzer.weak.water",
                    "sc.analyzer.weak.heat", "sc.analyzer.undead", "sc.analyzer.arthropod", "sc.analyzer.fireimmune", "sc.analyzer.explodes",
                    "sc.analyzer.energy", "sc.analyzer.off", "sc.analyzer.status", "sc.analyzer.progress", "sc.analyzer.output",
                    "sc.manual.singular.fn.3", "sc.manual.singular.fn.4", "sc.gas.singular_matter", "sc.gas.deuterium", "sc.gas.krypton");
            for (String k : want) {
                if (!keys.contains(k)) {
                    missing += " " + lang + ":" + k;
                }
            }
        }
        check(missing.isEmpty(), "Singular stage 2b: names, descriptions, chat, analyzer and handbook texts in en_US and ru_RU" + missing);
    }

    /**
     * Stage 3, the Singular levels (docs/plan-singular-armor.md §6): thresholds, points (capped, every
     * source's rate), the kill cap, the tasks on a fake persisted NBT, applyLevelUp, the branches, the
     * bonuses (tanks / EU / protection, Р4 sync), the pending gas / SM counters, the network, the texts.
     */
    private static void singularLevels() {
        com.sc.util.ArmorSuit sg = com.sc.util.ArmorSuit.SINGULAR;
        // thresholds and the tasks' needs
        boolean thr = com.sc.util.SingularLevel.threshold(1) == 2000 && com.sc.util.SingularLevel.threshold(2) == 8000
                && com.sc.util.SingularLevel.threshold(3) == 25000 && com.sc.util.SingularLevel.threshold(4) == 60000
                && com.sc.util.SingularLevel.threshold(5) == 0 && com.sc.util.SingularLevel.threshold(0) == 0
                && com.sc.util.SingularLevel.taskNeed(2, 0) == 5000 && com.sc.util.SingularLevel.taskNeed(2, 1) == 300
                && com.sc.util.SingularLevel.taskNeed(2, 2) == 2000 && com.sc.util.SingularLevel.taskNeed(3, 0) == 100
                && com.sc.util.SingularLevel.taskNeed(3, 1) == 600 && com.sc.util.SingularLevel.taskNeed(3, 2) == 50
                && com.sc.util.SingularLevel.taskNeed(4, 0) == 1 && com.sc.util.SingularLevel.taskNeed(4, 1) == 300
                && com.sc.util.SingularLevel.taskNeed(4, 2) == 20 && com.sc.util.SingularLevel.taskNeed(5, 0) == 1
                && com.sc.util.SingularLevel.taskNeed(5, 1) == 30 && com.sc.util.SingularLevel.taskNeed(5, 2) == 5000
                && com.sc.util.SingularLevel.taskNeed(6, 0) == 0 && com.sc.util.SingularLevel.taskNeed(2, 3) == 0;
        check(thr, "Singular levels: thresholds 2000 / 8000 / 25000 / 60000, none at 5; the 12 tasks' needs as in the plan");

        // points: per piece, capped at the threshold, Singular only; the sources' rates
        ItemStack legs = new ItemStack(ModItems.ARMOR.get(sg)[2]);
        ItemStack exoLegs = new ItemStack(ModItems.ARMOR.get(com.sc.util.ArmorSuit.EXO)[2]);
        boolean pts = com.sc.util.SingularLevel.points(legs) == 0 && com.sc.util.SingularLevel.addPoints(legs, 1500) == 1500
                && com.sc.util.SingularLevel.addPoints(legs, 1500) == 500 && com.sc.util.SingularLevel.points(legs) == 2000
                && com.sc.util.SingularLevel.pointsFull(legs) && com.sc.util.SingularLevel.addPoints(legs, 10) == 0
                && com.sc.util.SingularLevel.addPoints(exoLegs, 10) == 0 && com.sc.util.SingularLevel.points(exoLegs) == 0
                && com.sc.util.SingularLevel.capPoints(5, 999) == 0 && com.sc.util.SingularLevel.capPoints(2, -5) == 0
                && com.sc.util.SingularLevel.capPoints(2, 9000) == 8000
                && com.sc.util.SingularLevel.wholePoints(29.9, 10) == 2 && com.sc.util.SingularLevel.wholePoints(30, 10) == 3
                && com.sc.util.SingularLevel.wholePoints(-1, 10) == 0
                && com.sc.util.SingularLevel.GAS_MB_PER_POINT == 10 && com.sc.util.SingularLevel.FLIGHT_BLOCKS_PER_POINT == 10
                && com.sc.util.SingularLevel.BIOME_POINTS == 50 && com.sc.util.SingularLevel.DIMENSION_POINTS == 200
                && com.sc.util.SingularLevel.keyPoints(com.sc.util.ArmorFeature.PHASE_DASH) == 2
                && com.sc.util.SingularLevel.keyPoints(com.sc.util.ArmorFeature.GRAV_PRESS) == 10
                && com.sc.util.SingularLevel.keyPoints(com.sc.util.ArmorFeature.GRAV_GRAB) == 5
                && com.sc.util.SingularLevel.keyPoints(com.sc.util.ArmorFeature.TIME_SLOW) == 15
                && com.sc.util.SingularLevel.keyPoints(com.sc.util.ArmorFeature.BLACK_HOLE) == 20
                && com.sc.util.SingularLevel.keyPoints(com.sc.util.ArmorFeature.GRAV_DOME) == 15
                && com.sc.util.SingularLevel.keyPoints(com.sc.util.ArmorFeature.SINGULARITY) == 20
                && com.sc.util.SingularLevel.keyPoints(com.sc.util.ArmorFeature.FLIGHT) == 0;
        net.minecraft.nbt.NBTTagCompound list = new net.minecraft.nbt.NBTTagCompound();
        pts &= com.sc.item.SingularProgressSC.addOnce(list, "b", 7, 3) && !com.sc.item.SingularProgressSC.addOnce(list, "b", 7, 3)
                && com.sc.item.SingularProgressSC.addOnce(list, "b", 8, 3) && com.sc.item.SingularProgressSC.addOnce(list, "b", 9, 3)
                && !com.sc.item.SingularProgressSC.addOnce(list, "b", 10, 3) && list.getIntArray("b").length == 3;
        check(pts, "Singular points: per piece in SingPts, capped at the threshold (ready), none for other suits; "
                + "10 mB / 10 blocks a point, keys 2-20, biome 50, dimension 200 once each");

        // the kill cap: 200 a minute from mobs, bosses always 500
        int used = 0, kills = 0;
        for (int i = 0; i < 60; i++) {
            int got = com.sc.util.SingularLevel.killPoints(false, used);
            used += got;
            kills += got > 0 ? 1 : 0;
        }
        check(used == 200 && kills == 40 && com.sc.util.SingularLevel.killPoints(false, 198) == 2
                && com.sc.util.SingularLevel.killPoints(true, 200) == 500 && com.sc.util.SingularLevel.killPoints(false, 0) == 5,
                "Singular kill points: 5 a mob up to 200 a minute (" + used + " from 60 kills), a boss 500 past the cap");

        // the tasks on a fake persisted NBT
        net.minecraft.nbt.NBTTagCompound per = new net.minecraft.nbt.NBTTagCompound();
        net.minecraft.nbt.NBTTagCompound store = new net.minecraft.nbt.NBTTagCompound();
        per.setTag(com.sc.util.SingularLevel.STORE, store);
        boolean tasks = !com.sc.util.SingularLevel.taskDoneIn(per, 2) && !com.sc.util.SingularLevel.taskDoneIn(per, 5);
        store.setFloat(com.sc.util.SingularLevel.C_H2ABS, 299.5F);
        store.setDouble(com.sc.util.SingularLevel.C_FLY, 4999.9);
        store.setInteger(com.sc.util.SingularLevel.C_GAS, 1999);
        tasks &= !com.sc.util.SingularLevel.taskDoneIn(per, 2) && com.sc.util.SingularLevel.taskValue(per, 2, 0) == 4999;
        store.setInteger(com.sc.util.SingularLevel.C_GAS, 2000);
        tasks &= com.sc.util.SingularLevel.taskDoneIn(per, 2) && !com.sc.util.SingularLevel.taskDoneIn(per, 3);
        store.setInteger(com.sc.util.SingularLevel.C_DASHES, 50);
        tasks &= com.sc.util.SingularLevel.taskDoneIn(per, 3);
        net.minecraft.nbt.NBTTagCompound spawners = new net.minecraft.nbt.NBTTagCompound();
        for (int i = 0; i < 19; i++) {
            spawners.setBoolean("0:" + i + ":64:0", true);
        }
        per.setTag(com.sc.util.SingularLevel.SPAWNERS, spawners);
        per.setInteger(com.sc.util.SingularLevel.RES_REACTOR_SEC, 299);
        tasks &= !com.sc.util.SingularLevel.taskDoneIn(per, 4) && com.sc.util.SingularLevel.taskValue(per, 4, 2) == 19;
        spawners.setBoolean("0:99:64:0", true);
        tasks &= com.sc.util.SingularLevel.taskDoneIn(per, 4);
        per.setInteger(com.sc.util.SingularLevel.HOLE_KILLS, 29);
        store.setInteger(com.sc.util.SingularLevel.C_SM, 4999);
        tasks &= !com.sc.util.SingularLevel.taskDoneIn(per, 5);
        store.setInteger(com.sc.util.SingularLevel.C_DRAGON, 1);
        tasks &= com.sc.util.SingularLevel.taskDoneIn(per, 5) && com.sc.util.SingularLevel.taskValues(per).length == 12
                && com.sc.util.SingularLevel.taskValues(per)[3 * 3 + 0] == 1 && com.sc.util.SingularLevel.taskValues(per)[2 * 3 + 1] == 299;
        com.sc.util.SingularLevel.clientSet(com.sc.util.SingularLevel.taskValues(per), 4, 2);
        check(tasks, "Singular tasks: one of three per level, read from the persisted NBT (scSingLv + the stage 2b counters), "
                + "under the need - not done");

        // applyLevelUp: +1, points to 0, Singular only, 5 stays 5
        ItemStack boots = new ItemStack(ModItems.ARMOR.get(sg)[3]);
        com.sc.util.SingularLevel.addPoints(boots, 2000);
        boolean up = com.sc.util.SingularLevel.applyLevelUp(boots) == 2 && com.sc.util.SingularLevel.levelOf(boots) == 2
                && com.sc.util.SingularLevel.points(boots) == 0 && !com.sc.util.SingularLevel.pointsFull(boots)
                && com.sc.util.SingularLevel.applyLevelUp(exoLegs) == 1 && com.sc.util.SingularLevel.levelOf(exoLegs) == 1;
        com.sc.util.SingularLevel.setLevel(boots, 5);
        up &= com.sc.util.SingularLevel.applyLevelUp(boots) == 5 && com.sc.util.SingularLevel.addPoints(boots, 100) == 0
                && !com.sc.util.SingularLevel.readyToUpgrade(null, boots) && !com.sc.util.SingularLevel.readyToUpgrade(null, legs);
        check(up, "Singular applyLevelUp: level + 1 and the points back to 0, only Singular, level 5 is the top (no points)");

        // the branches: locked until chosen, the free choice only at the level, set / clear, the pairs
        ItemStack chest = new ItemStack(ModItems.ARMOR.get(sg)[1]);
        com.sc.util.ArmorFeature press = com.sc.util.ArmorFeature.GRAV_PRESS, grab = com.sc.util.ArmorFeature.GRAV_GRAB,
                hole = com.sc.util.ArmorFeature.BLACK_HOLE, dome = com.sc.util.ArmorFeature.GRAV_DOME;
        boolean br = !com.sc.util.SingularLevel.branchAllowed(press, chest) && !com.sc.util.SingularLevel.branchAllowed(grab, chest)
                && com.sc.util.SingularLevel.branchAllowed(com.sc.util.ArmorFeature.TIME_SLOW, chest)
                && !com.sc.util.SingularLevel.branchPending(chest, 3);
        com.sc.util.SingularLevel.setLevel(chest, 3);
        br &= com.sc.util.SingularLevel.branchPending(chest, 3) && !com.sc.util.SingularLevel.branchPending(chest, 5)
                && com.sc.util.SingularLevel.setBranch(chest, 3, 1) && com.sc.util.SingularLevel.branchAllowed(press, chest)
                && !com.sc.util.SingularLevel.branchAllowed(grab, chest) && !com.sc.util.SingularLevel.branchPending(chest, 3)
                && !com.sc.util.SingularLevel.branchAllowed(hole, chest) && !com.sc.util.SingularLevel.branchAllowed(dome, chest);
        com.sc.util.SingularLevel.setLevel(chest, 5);
        br &= com.sc.util.SingularLevel.branchPending(chest, 5) && com.sc.util.SingularLevel.setBranch(chest, 5, 2)
                && com.sc.util.SingularLevel.branchAllowed(dome, chest) && !com.sc.util.SingularLevel.branchAllowed(hole, chest)
                && com.sc.util.SingularLevel.branchChoice(chest, 3) == 1 && com.sc.util.SingularLevel.branchChoice(chest, 5) == 2
                && !com.sc.util.SingularLevel.setBranch(chest, 4, 1) && !com.sc.util.SingularLevel.setBranch(chest, 3, 3)
                && com.sc.util.SingularLevel.setBranch(chest, 3, 0) && com.sc.util.SingularLevel.branchChoice(chest, 3) == 0
                && com.sc.util.SingularLevel.branchFeature(3, 1) == press && com.sc.util.SingularLevel.branchFeature(3, 2) == grab
                && com.sc.util.SingularLevel.branchFeature(5, 1) == hole && com.sc.util.SingularLevel.branchFeature(5, 2) == dome
                && com.sc.util.SingularLevel.branchFeature(4, 1) == null && !com.sc.util.SingularLevel.branchAllowed(null, press, chest)
                && !com.sc.util.SingularLevel.branchPending(exoLegs, 3);
        check(br, "Singular branches: both locked until chosen, the choice offered at level 3 / 5, press|grab and hole|dome exclusive, "
                + "only levels 3 / 5 and choices 0..2");

        // the bonuses: tanks +10% / protection +5% / EU -5% a level, Р4 +10% with all four at one level 2+
        ItemStack[] w = gasSuit(sg, true, true, true, true);
        com.sc.util.ArmorGasSC.Gas he = com.sc.util.ArmorGasSC.Gas.HELIUM;
        boolean bonus = com.sc.util.SingularLevel.bonusPercent(1, false, 10) == 0 && com.sc.util.SingularLevel.bonusPercent(1, true, 10) == 0
                && com.sc.util.SingularLevel.bonusPercent(5, false, 10) == 40 && com.sc.util.SingularLevel.bonusPercent(5, true, 10) == 50
                && com.sc.util.SingularLevel.bonusPercent(3, false, 5) == 10 && com.sc.util.SingularLevel.syncedLevel(w) == 0
                && com.sc.util.ArmorGasSC.levelBonusPercent(w[1]) == 0 && com.sc.util.ArmorGasSC.capacity(w[1], he) == 24000
                && com.sc.util.SingularLevel.euMul(w[1]) == 1F && com.sc.util.SingularLevel.protectionPercent(w) == 0;
        com.sc.util.ArmorGasSC.setAmount(w[1], he, 24000);
        for (ItemStack s : w) {
            com.sc.util.SingularLevel.setLevel(s, 3);
        }
        bonus &= com.sc.util.ArmorGasSC.levelBonusPercent(w[1]) == 20 && com.sc.util.ArmorGasSC.capacity(w[1], he) == 28800
                && com.sc.util.ArmorGasSC.amount(w[1], he) == 24000 && com.sc.util.SingularLevel.syncedLevel(w) == 3
                && Math.abs(com.sc.util.SingularLevel.euMul(w[1]) - 0.9F) < 1e-6 && com.sc.util.SingularLevel.protectionPercent(w) == 10;
        bonus &= com.sc.util.SingularLevel.updateSync(w) && !com.sc.util.SingularLevel.updateSync(w)
                && com.sc.util.ArmorGasSC.levelBonusPercent(w[1]) == 30 && com.sc.util.ArmorGasSC.capacity(w[1], he) == 31200
                && Math.abs(com.sc.util.SingularLevel.euMul(w[1]) - 0.8F) < 1e-6 && com.sc.util.SingularLevel.protectionPercent(w) == 20;
        com.sc.util.SingularLevel.setLevel(w[0], 4);
        bonus &= com.sc.util.SingularLevel.syncedLevel(w) == 0 && com.sc.util.SingularLevel.updateSync(w)
                && com.sc.util.ArmorGasSC.levelBonusPercent(w[1]) == 20 && com.sc.util.ArmorGasSC.levelBonusPercent(w[0]) == 30
                && com.sc.util.SingularLevel.tankBonusPercent(exoLegs) == 0 && com.sc.util.SingularLevel.euMul(exoLegs) == 1F;
        ItemStack[] three = gasSuit(sg, true, true, true, false);
        for (ItemStack s : three) {
            com.sc.util.SingularLevel.setLevel(s, 2);
        }
        bonus &= com.sc.util.SingularLevel.syncedLevel(three) == 0 && com.sc.util.SingularLevel.protectionPercent(three) == 3;
        check(bonus, "Singular bonuses: level 3 tanks +20% (He 24000 -> 28800, nothing lost), EU x0.9, protection 10%; "
                + "all four at level 3: +30% / x0.8 / 20%; out of sync again when one piece differs; other suits none");

        // what the suit spends / the SM poured in is noted on the pieces for the level logic
        ItemStack[] g = gasSuit(sg, true, true, true, true);
        com.sc.util.ArmorGasSC.setAmount(g[1], he, 1000);
        com.sc.util.ArmorGasSC.drainOf(g, he, 30, true);
        boolean pend = com.sc.util.ArmorGasSC.takePending(g, com.sc.util.ArmorGasSC.SPENT_PENDING) == 0;
        com.sc.util.ArmorGasSC.drainOf(g, he, 30, false);
        com.sc.util.ArmorGasSC.drainExact(g, he, 25);
        pend &= com.sc.util.ArmorGasSC.takePending(g, com.sc.util.ArmorGasSC.SPENT_PENDING) == 55
                && com.sc.util.ArmorGasSC.takePending(g, com.sc.util.ArmorGasSC.SPENT_PENDING) == 0;
        com.sc.util.ArmorGasSC.fillOf(g, com.sc.util.ArmorGasSC.Gas.SINGULAR_MATTER, 400, false);
        com.sc.util.ArmorGasSC.fillOf(g, he, 400, false);
        pend &= com.sc.util.ArmorGasSC.takePending(g, com.sc.util.ArmorGasSC.SM_PENDING) == 400;
        ItemStack[] exo = gasSuit(com.sc.util.ArmorSuit.EXO, true, true, true, true);
        com.sc.util.ArmorGasSC.setAmount(exo[1], he, 1000);
        com.sc.util.ArmorGasSC.drainOf(exo, he, 30, false);
        pend &= com.sc.util.ArmorGasSC.takePending(exo, com.sc.util.ArmorGasSC.SPENT_PENDING) == 0;
        check(pend, "Singular levels: gas spent (not simulated) and SM poured noted on the Singular pieces, taken once; nothing for Exo");

        // network: the branch byte and the level message
        io.netty.buffer.ByteBuf buf = io.netty.buffer.Unpooled.buffer();
        int[] vals = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 1234567};
        new com.sc.handler.ArmorNetSC.LevelMessage(vals, 17, 3).toBytes(buf);
        com.sc.handler.ArmorNetSC.LevelMessage back = new com.sc.handler.ArmorNetSC.LevelMessage();
        back.fromBytes(buf);
        boolean net = java.util.Arrays.equals(back.tasks, vals) && back.biomes == 17 && back.dims == 3
                && com.sc.handler.ArmorNetSC.BRANCH == 22 && com.sc.handler.ArmorNetSC.featureOfAction(com.sc.handler.ArmorNetSC.BRANCH) == null
                && com.sc.handler.ArmorNetSC.branchFeature(5, 2) == 52 && com.sc.handler.ArmorNetSC.BRANCH != com.sc.item.ArmorLogicSC.AIR_JUMP_ACTION;
        check(net, "Singular levels network: BRANCH action 22 (level x 10 + choice), the task counters message reads back as written");

        // texts
        String missing = "";
        for (String lang : new String[]{"en_US", "ru_RU"}) {
            java.util.Set<String> keys = langKeys(lang);
            java.util.List<String> want = new java.util.ArrayList<String>();
            java.util.Collections.addAll(want, "sc.armor.sing.levelup", "sc.armor.sing.branch.choose", "sc.armor.sing.branch.chosen",
                    "sc.armorgui.branch.pick", "sc.armorgui.row.branch", "sc.armorgui.tip.branch", "sc.tooltip.armor.singular.points",
                    "sc.tooltip.armor.singular.points.ready", "sc.tooltip.armor.singular.tasks", "sc.tooltip.armor.singular.tasks.done",
                    "sc.tooltip.armor.singular.opens", "sc.tooltip.armor.singular.max", "sc.tooltip.armor.singular.branch",
                    "sc.tooltip.armor.singular.branch.pick", "sc.tooltip.armor.singular.branch.later", "sc.tooltip.armor.singular.bonus",
                    "sc.tooltip.armor.singular.sync");
            for (int t = 2; t <= 5; t++) {
                for (int i = 0; i < 3; i++) {
                    want.add("sc.tooltip.armor.singular.task." + t + "." + i);
                }
            }
            for (String k : want) {
                if (!keys.contains(k)) {
                    missing += " " + lang + ":" + k;
                }
            }
        }
        check(missing.isEmpty(), "Singular levels: chat, K menu and tooltip texts in en_US and ru_RU" + missing);
    }

    /**
     * Stage 5 (docs/plan-singular-armor.md §6 "Отображение"): М4 the function profiles (store, apply with
     * locked functions left off, an empty one taking the current switches, cycling), М3 the cooldown
     * HUD's pure rules (what a function needs, which icons show), the new message ids / bytes and the texts.
     */
    /** The Singular armour's crafts: recipes, the Б-1 conversion, the compressor's liquid mode, the reactor's by-product, the SM cell. */
    private static void singularCrafts() {
        net.minecraft.item.Item stationItem = net.minecraft.item.Item.getItemFromBlock(com.sc.init.ModBlocks.singularStation);
        // СС1 / ГС1 / the cell: recipes there, with the right parts
        java.util.List<net.minecraft.item.crafting.IRecipe> stR = com.sc.manual.BookContent.craftingFor(new ItemStack(com.sc.init.ModBlocks.singularStation));
        java.util.List<net.minecraft.item.crafting.IRecipe> gsR = com.sc.manual.BookContent.craftingFor(new ItemStack(com.sc.init.ModBlocks.gravStabiliser));
        java.util.List<net.minecraft.item.crafting.IRecipe> cellR = com.sc.manual.BookContent.craftingFor(new ItemStack(ModItems.singularCell));
        boolean stHasParts = !stR.isEmpty() && stR.get(0) instanceof com.sc.init.ChargeCarryRecipeSC
                && inputsHave(stR.get(0), new ItemStack(com.sc.init.ModBlocks.armorStation))
                && inputsHave(stR.get(0), new ItemStack(ModItems.battery, 1, 5)) && inputsHave(stR.get(0), new ItemStack(com.sc.init.ModBlocks.gravityCoil));
        boolean gsHasParts = !gsR.isEmpty() && inputsHave(gsR.get(0), new ItemStack(ModItems.component("matterCapsule")))
                && inputsHave(gsR.get(0), new ItemStack(com.sc.init.ModBlocks.gravityCoil));
        // the Exo core's charge goes into the station's buffer
        net.minecraft.inventory.InventoryCrafting grid = new net.minecraft.inventory.InventoryCrafting(new net.minecraft.inventory.Container() {
            @Override
            public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer p) {
                return true;
            }
        }, 3, 3);
        ItemStack coil = new ItemStack(com.sc.init.ModBlocks.gravityCoil), cab = new ItemStack(com.sc.init.ModBlocks.cableSC, 1, com.sc.energy.CableType.SINGULAR.ordinal()),
                hf = ModItems.ingot.stackOf(com.sc.util.Material.HAFNIUM), core5 = new ItemStack(ModItems.battery, 1, 5);
        com.sc.item.ItemBatterySC.setCharge(core5, 1000000L);
        ItemStack[] pattern = {coil, core5, coil, cab, new ItemStack(com.sc.init.ModBlocks.armorStation), cab, hf, new ItemStack(ModItems.component("fusionCore")), hf};
        for (int i = 0; i < 9; i++) {
            grid.setInventorySlotContents(i, pattern[i].copy());
        }
        ItemStack made = !stR.isEmpty() && stR.get(0).matches(grid, null) ? stR.get(0).getCraftingResult(grid) : null;
        boolean carried = made != null && made.getItem() == stationItem && made.hasTagCompound() && made.getTagCompound().getInteger("EnergySC") == 1000000;
        check(stHasParts && gsHasParts && !cellR.isEmpty() && carried, "Singular crafts: the station (Armour Station + Exo core + gravity coils + Singular cable),"
                + " the stabiliser (capsule + gravity coil), the SM cell have recipes; the Exo core's charge goes into the station item (" + stHasParts + "/"
                + gsHasParts + "/" + !cellR.isEmpty() + "/" + carried + ")");

        // Б-1 cost math
        long[] one = com.sc.util.SingularStationMath.convertCost(1), set = com.sc.util.SingularStationMath.convertCost(15);
        boolean math = java.util.Arrays.equals(one, new long[]{50000000L, 100, 2000, 0, 0})
                && java.util.Arrays.equals(com.sc.util.SingularStationMath.convertCost(2), new long[]{150000000L, 250, 4000, 1000, 0})
                && java.util.Arrays.equals(set, new long[]{350000000L, 600, 10000, 1000, 0})
                && com.sc.util.SingularStationMath.convertTicks(15) == 4800 && com.sc.util.SingularStationMath.convertTicks(9) == 2400
                && com.sc.util.SingularStationMath.convertTicks(4) == 3600
                && java.util.Arrays.equals(com.sc.util.SingularStationMath.convertMaterials(15), new int[]{11, 1, 8, 1, 2, 1, 1})
                && java.util.Arrays.equals(com.sc.util.SingularStationMath.convertMaterials(1), new int[]{2, 1, 2, 0, 0, 0, 0});
        check(math, "Singular conversion cost: helmet 50 M EU / SM 100 / He 2000, chest 150 M / 250 / 4000 / D 1000, the set summed "
                + java.util.Arrays.toString(set) + ", time the longest piece (4 min), materials summed");

        // Б-1 in a station: start takes the materials, cancel gives them back whole; the new piece keeps charge / gases / chips
        com.sc.tileentity.TileEntitySingularStationSC st = new com.sc.tileentity.TileEntitySingularStationSC();
        int M = com.sc.tileentity.TileEntitySingularStationSC.MATERIAL_SLOT;
        ItemStack exoHelm = new ItemStack(ModItems.ARMOR.get(com.sc.util.ArmorSuit.EXO)[0]);
        com.sc.item.ItemArmorSC.setCharge(exoHelm, 12345);
        int heCapExo = com.sc.util.ArmorGasSC.capacity(exoHelm, com.sc.util.ArmorGasSC.Gas.HELIUM);
        com.sc.util.ArmorGasSC.setAmount(exoHelm, com.sc.util.ArmorGasSC.Gas.HELIUM, 300);
        com.sc.item.ItemArmorSC.chipsTag(exoHelm).setInteger("NIGHT_VISION", 2);
        String noExo = st.startConvertFor("");
        st.setInventorySlotContents(0, exoHelm.copy());
        st.setInventorySlotContents(M, new ItemStack(ModItems.component("matterCapsule"), 3));
        st.setInventorySlotContents(M + 1, new ItemStack(ModItems.component("focusLens")));
        String noMat = st.startConvertFor("");
        st.setInventorySlotContents(M + 2, new ItemStack(ModItems.component("nb3SnPlate"), 2));
        boolean valid = st.isItemValidForSlot(M + 3, hf) && st.isItemValidForSlot(M + 3, new ItemStack(ModItems.battery, 1, 5))
                && !st.isItemValidForSlot(M + 3, new ItemStack(net.minecraft.init.Blocks.stone));
        String started = st.startConvertFor("");
        com.sc.tileentity.SingularProcessSC p = st.getProcess();
        boolean took = started == null && p != null && p.kind == com.sc.tileentity.SingularProcessSC.KIND_CONVERT && p.mask == 1 && st.isLocked(0)
                && p.cost[0] == 50000000L && p.baseTicks == 2400 && st.getStackInSlot(M) != null && st.getStackInSlot(M).stackSize == 1
                && st.getStackInSlot(M + 1) == null && st.getStackInSlot(M + 2) == null && p.items.size() == 3;
        net.minecraft.nbt.NBTTagCompound pn = new net.minecraft.nbt.NBTTagCompound();
        if (p != null) {
            p.writeToNBT(pn);
        }
        com.sc.tileentity.SingularProcessSC p2 = com.sc.tileentity.SingularProcessSC.readFromNBT(pn);
        boolean procNbt = p2 != null && p2.kind == com.sc.tileentity.SingularProcessSC.KIND_CONVERT && p2.items.size() == 3 && p2.cost[2] == 2000;
        st.cancelProcess();
        int caps = 0, lens = 0, plates = 0;
        for (int i = 0; i < com.sc.tileentity.TileEntitySingularStationSC.MATERIAL_SLOTS; i++) {
            ItemStack s = st.getStackInSlot(M + i);
            int k = com.sc.tileentity.TileEntitySingularStationSC.materialKind(s);
            caps += k == com.sc.util.SingularStationMath.M_CAPSULE ? s.stackSize : 0;
            lens += k == com.sc.util.SingularStationMath.M_LENS ? s.stackSize : 0;
            plates += k == com.sc.util.SingularStationMath.M_NB3SN ? s.stackSize : 0;
        }
        boolean back = st.getProcess() == null && caps == 3 && lens == 1 && plates == 2 && !st.isLocked(0)
                && com.sc.tileentity.TileEntitySingularStationSC.isExo(st.getStackInSlot(0));
        ItemStack sing = com.sc.tileentity.TileEntitySingularStationSC.convertPiece(exoHelm);
        boolean piece = com.sc.util.SingularLevel.isSingular(sing) && ((com.sc.item.ItemArmorSC) sing.getItem()).armorType == 0
                && com.sc.util.SingularLevel.levelOf(sing) == 1 && com.sc.util.SingularLevel.points(sing) == 0
                && com.sc.util.SingularScheme.of(sing) == com.sc.util.SingularScheme.A && com.sc.item.ItemArmorSC.chargeOf(sing) == 12345
                && com.sc.util.ArmorGasSC.amount(sing, com.sc.util.ArmorGasSC.Gas.HELIUM) == Math.min(300, heCapExo)
                && sing.getTagCompound().getCompoundTag("ChipsSC").getInteger("NIGHT_VISION") == 2;
        // the chest: its Singular core's charge pays the EU first; cancelled - the core comes back with all of it
        com.sc.tileentity.TileEntitySingularStationSC sc = new com.sc.tileentity.TileEntitySingularStationSC();
        sc.setInventorySlotContents(1, new ItemStack(ModItems.ARMOR.get(com.sc.util.ArmorSuit.EXO)[1]));
        ItemStack core6 = new ItemStack(ModItems.battery, 1, 6);
        com.sc.item.ItemBatterySC.setCharge(core6, 100000000L);
        sc.setInventorySlotContents(com.sc.tileentity.TileEntitySingularStationSC.CATALYST_SLOT, core6);
        sc.setInventorySlotContents(M, new ItemStack(ModItems.component("matterCapsule"), 4));
        sc.setInventorySlotContents(M + 1, new ItemStack(ModItems.component("fusionCore")));
        sc.setInventorySlotContents(M + 2, new ItemStack(ModItems.component("nb3SnPlate"), 2));
        String chestStart = sc.startConvertFor("");
        boolean coreEu = chestStart == null && sc.getProcess() != null && sc.getProcess().drawn[0] == 100000000L && sc.getProcess().catalystEu == 100000000L
                && sc.getStackInSlot(com.sc.tileentity.TileEntitySingularStationSC.CATALYST_SLOT) == null;
        sc.cancelProcess();
        ItemStack coreBack = sc.getStackInSlot(com.sc.tileentity.TileEntitySingularStationSC.CATALYST_SLOT);
        coreEu &= coreBack != null && com.sc.item.ItemBatterySC.chargeOf(coreBack) == 100000000L && sc.getEnergyStored() == 0;
        check("sc.singStation.err.noexo".equals(noExo) && "sc.singStation.err.nomaterials".equals(noMat) && valid && took && procNbt && back && piece && coreEu,
                "Singular conversion: refused without Exo / materials, starts taking the materials whole (slot locked), the process with its items survives NBT,"
                        + " «Отменить» gives them back whole, the new piece is level 1 / scheme A with the charge, helium and chips; the chest's core pays the EU"
                        + " and comes back whole (" + noExo + "/" + noMat + "/" + valid + "/" + took + "/" + procNbt + "/" + back + "/" + piece + "/" + coreEu + ")");

        // Б-1, six material slots: a whole Exo set in one process; one kind spread over several slots; the two new slots in NBT
        final int TS = com.sc.tileentity.TileEntitySingularStationSC.MATERIAL_SLOT;
        final int CAT = com.sc.tileentity.TileEntitySingularStationSC.CATALYST_SLOT;
        com.sc.tileentity.TileEntitySingularStationSC fs = new com.sc.tileentity.TileEntitySingularStationSC();
        for (int t = 0; t < 4; t++) {
            fs.setInventorySlotContents(t, new ItemStack(ModItems.ARMOR.get(com.sc.util.ArmorSuit.EXO)[t]));
        }
        fs.setInventorySlotContents(CAT, new ItemStack(ModItems.battery, 1, 6));
        fs.setInventorySlotContents(TS, new ItemStack(ModItems.component("matterCapsule"), 13));   // capsules stack to 16
        fs.setInventorySlotContents(TS + 1, new ItemStack(ModItems.component("focusLens")));
        fs.setInventorySlotContents(TS + 2, new ItemStack(ModItems.component("nb3SnPlate"), 64));
        fs.setInventorySlotContents(TS + 3, new ItemStack(ModItems.component("fusionCore")));
        fs.setInventorySlotContents(TS + 4, new ItemStack(ModItems.battery, 1, 5));
        ItemStack hf1 = hf.copy();
        hf1.stackSize = 1;
        fs.setInventorySlotContents(TS + 5, hf1);
        String fsShort = fs.startConvertFor("");
        ItemStack hf2 = hf.copy();
        hf2.stackSize = 2;
        fs.setInventorySlotContents(TS + 5, hf2.copy());
        int[] fsHave = fs.materialsHave();
        String fsStart = fs.startConvertFor("");
        com.sc.tileentity.SingularProcessSC fp = fs.getProcess();
        boolean fullSet = com.sc.tileentity.TileEntitySingularStationSC.MATERIAL_SLOTS == 6 && fs.getSizeInventory() == 17 && TS == 10 && CAT == 9
                && fs.isItemValidForSlot(TS + 5, hf) && fs.isItemValidForSlot(TS + 4, new ItemStack(ModItems.component("focusLens")))
                && fs.getAccessibleSlotsFromSide(1).length == 5
                && "sc.singStation.err.nomaterials".equals(fsShort) && java.util.Arrays.equals(fsHave, new int[]{13, 1, 64, 1, 2, 1, 1})
                && fsStart == null && fp != null && fp.mask == 15 && fp.items.size() == 7 && fp.cost[0] == 350000000L
                && fs.getStackInSlot(TS) != null && fs.getStackInSlot(TS).stackSize == 2 && fs.getStackInSlot(TS + 2) != null
                && fs.getStackInSlot(TS + 2).stackSize == 56 && fs.getStackInSlot(TS + 1) == null && fs.getStackInSlot(TS + 3) == null
                && fs.getStackInSlot(TS + 4) == null && fs.getStackInSlot(TS + 5) == null && fs.getStackInSlot(CAT) == null;
        fs.cancelProcess();
        int[] fsBack = fs.materialsHave();
        fullSet &= fs.getProcess() == null && java.util.Arrays.equals(fsBack, new int[]{13, 1, 64, 1, 2, 1, 1})
                && com.sc.tileentity.TileEntitySingularStationSC.isCore(fs.getStackInSlot(CAT));
        // helmet + boots: the capsules in slots 10 and 14, the plates in 11 and 15
        com.sc.tileentity.TileEntitySingularStationSC sp = new com.sc.tileentity.TileEntitySingularStationSC();
        sp.setInventorySlotContents(0, new ItemStack(ModItems.ARMOR.get(com.sc.util.ArmorSuit.EXO)[0]));
        sp.setInventorySlotContents(3, new ItemStack(ModItems.ARMOR.get(com.sc.util.ArmorSuit.EXO)[3]));
        sp.setInventorySlotContents(TS, new ItemStack(ModItems.component("matterCapsule"), 3));
        sp.setInventorySlotContents(TS + 1, new ItemStack(ModItems.component("nb3SnPlate"), 3));
        sp.setInventorySlotContents(TS + 2, new ItemStack(ModItems.component("focusLens")));
        sp.setInventorySlotContents(TS + 3, hf2.copy());
        sp.setInventorySlotContents(TS + 4, new ItemStack(ModItems.component("matterCapsule"), 1));
        sp.setInventorySlotContents(TS + 5, new ItemStack(ModItems.component("nb3SnPlate"), 1));
        int[] spHave = sp.materialsHave();
        String spStart = sp.startConvertFor("");
        boolean spread = spHave[com.sc.util.SingularStationMath.M_CAPSULE] == 4 && spHave[com.sc.util.SingularStationMath.M_NB3SN] == 4
                && spStart == null && sp.getProcess() != null && sp.getProcess().mask == 9;
        for (int i = 0; i < 6; i++) {
            spread &= sp.getStackInSlot(TS + i) == null;
        }
        sp.cancelProcess();
        int[] spBack = sp.materialsHave();
        spread &= spBack[com.sc.util.SingularStationMath.M_CAPSULE] == 4 && spBack[com.sc.util.SingularStationMath.M_NB3SN] == 4
                && spBack[com.sc.util.SingularStationMath.M_LENS] == 1 && spBack[com.sc.util.SingularStationMath.M_HAFNIUM] == 2;
        // NBT: slots 14 / 15 round-trip; an old save (SingItems Slot 0..5 = slots 8..13) loads as it was, 14 / 15 empty
        com.sc.tileentity.TileEntitySingularStationSC ns = new com.sc.tileentity.TileEntitySingularStationSC();
        ns.setInventorySlotContents(TS + 4, hf2.copy());
        ns.setInventorySlotContents(TS + 5, new ItemStack(ModItems.component("nb3SnPlate"), 7));
        ns.setInventorySlotContents(TS, new ItemStack(ModItems.component("matterCapsule"), 5));
        net.minecraft.nbt.NBTTagCompound nsTag = new net.minecraft.nbt.NBTTagCompound();
        ns.writeToNBT(nsTag);
        com.sc.tileentity.TileEntitySingularStationSC ns2 = new com.sc.tileentity.TileEntitySingularStationSC();
        ns2.readFromNBT(nsTag);
        boolean nbt6 = ns2.getStackInSlot(TS + 4) != null && com.sc.tileentity.TileEntitySingularStationSC.materialKind(ns2.getStackInSlot(TS + 4))
                == com.sc.util.SingularStationMath.M_HAFNIUM && ns2.getStackInSlot(TS + 4).stackSize == 2
                && ns2.getStackInSlot(TS + 5) != null && ns2.getStackInSlot(TS + 5).stackSize == 7
                && ns2.getStackInSlot(TS) != null && ns2.getStackInSlot(TS).stackSize == 5 && ns2.getStackInSlot(TS + 1) == null;
        net.minecraft.nbt.NBTTagCompound oldTag = new net.minecraft.nbt.NBTTagCompound();
        new com.sc.tileentity.TileEntitySingularStationSC().writeToNBT(oldTag);
        net.minecraft.nbt.NBTTagList oldList = new net.minecraft.nbt.NBTTagList();
        ItemStack[] oldItems = {new ItemStack(ModItems.ARMOR.get(com.sc.util.ArmorSuit.SINGULAR)[2]), new ItemStack(ModItems.battery, 1, 6),
                new ItemStack(ModItems.component("matterCapsule"), 4), new ItemStack(ModItems.component("focusLens")),
                new ItemStack(ModItems.component("nb3SnPlate"), 2), new ItemStack(ModItems.component("fusionCore"))};
        for (int i = 0; i < oldItems.length; i++) {
            net.minecraft.nbt.NBTTagCompound t = oldItems[i].writeToNBT(new net.minecraft.nbt.NBTTagCompound());
            t.setByte("Slot", (byte) i);
            oldList.appendTag(t);
        }
        oldTag.setTag("SingItems", oldList);
        ns2.readFromNBT(oldTag);                              // over a station that had 14 / 15 filled: they go empty
        boolean oldNbt = com.sc.util.SingularLevel.isSingular(ns2.getStackInSlot(com.sc.tileentity.TileEntitySingularStationSC.DONOR_SLOT))
                && com.sc.tileentity.TileEntitySingularStationSC.isCore(ns2.getStackInSlot(CAT))
                && ns2.getStackInSlot(TS) != null && ns2.getStackInSlot(TS).stackSize == 4
                && com.sc.tileentity.TileEntitySingularStationSC.materialKind(ns2.getStackInSlot(TS + 1)) == com.sc.util.SingularStationMath.M_LENS
                && ns2.getStackInSlot(TS + 2) != null && ns2.getStackInSlot(TS + 2).stackSize == 2
                && com.sc.tileentity.TileEntitySingularStationSC.materialKind(ns2.getStackInSlot(TS + 3)) == com.sc.util.SingularStationMath.M_FUSION
                && ns2.getStackInSlot(TS + 4) == null && ns2.getStackInSlot(TS + 5) == null;
        check(fullSet && spread && nbt6 && oldNbt, "Singular conversion, 6 material slots: a whole Exo set in one process (11 capsules / 8 plates out of"
                + " 13 / 64, the core from the catalyst slot, 1 hafnium short refused), a kind spread over two slots counts and is taken,"
                + " slots 14 / 15 survive NBT, an old 4-slot save loads (" + fsShort + "/" + fsStart + "/" + fullSet + "/" + spread + "/" + nbt6 + "/" + oldNbt + ")");

        // СМ1: the compressor's liquid mode - 100 mB per capsule's worth, no capsule; the mode in NBT; a full tank waits
        com.sc.tileentity.TileEntityMachineSC c = new com.sc.tileentity.TileEntityMachineSC();
        c.setMachineType(com.sc.machine.MachineType.MATTER_COMPRESSOR);
        c.setPowerOn(true);
        c.setMatterLiquid(true);
        c.loadEnergyFromItem(c.getMaxEnergyStored());
        c.setMatterForTest(2 * com.sc.tileentity.TileEntityMachineSC.MATTER_PER_CAPSULE);
        for (int i = 0; i < 2 * c.compressTicks(); i++) {
            c.loadEnergyFromItem(c.getMaxEnergyStored());
            c.compressorTickForTest();
        }
        net.minecraftforge.fluids.FluidStack inTank = c.getTank(2).getFluid();
        boolean liquid = inTank != null && inTank.getFluid() == com.sc.init.ModFluids.singularMatter && inTank.amount == 200 && c.getMatter() == 0
                && c.getStackInSlot(com.sc.tileentity.TileEntityMachineSC.INPUT_SLOTS) == null
                && com.sc.tileentity.TileEntityMachineSC.liquidFor(1152) == 200 && com.sc.tileentity.TileEntityMachineSC.liquidFor(575) == 0
                && c.getTank(2).getCapacity() == com.sc.tileentity.TileEntityMachineSC.SM_TANK;
        net.minecraft.nbt.NBTTagCompound cn = new net.minecraft.nbt.NBTTagCompound();
        c.writeToNBT(cn);
        com.sc.tileentity.TileEntityMachineSC c2 = new com.sc.tileentity.TileEntityMachineSC();
        c2.readFromNBT(cn);
        net.minecraft.nbt.NBTTagCompound oldN = (net.minecraft.nbt.NBTTagCompound) cn.copy();
        oldN.removeTag("MatterLiquid");
        com.sc.tileentity.TileEntityMachineSC c3 = new com.sc.tileentity.TileEntityMachineSC();
        c3.readFromNBT(oldN);
        boolean modeNbt = c2.isMatterLiquid() && c2.getTank(2).getFluidAmount() == 200 && !c3.isMatterLiquid();
        net.minecraftforge.fluids.FluidStack out = c.drain(net.minecraftforge.common.util.ForgeDirection.UNKNOWN, 150, true);
        boolean drained = out != null && out.amount == 150 && c.getTank(2).getFluidAmount() == 50;
        c.getTank(2).fill(new net.minecraftforge.fluids.FluidStack(com.sc.init.ModFluids.singularMatter, com.sc.tileentity.TileEntityMachineSC.SM_TANK), true);
        c.setMatterForTest(com.sc.tileentity.TileEntityMachineSC.MATTER_PER_CAPSULE);
        c.compressorTickForTest();
        boolean full = c.getStatus() == com.sc.machine.MachineStatus.OUTPUT_FULL && c.getMatter() == com.sc.tileentity.TileEntityMachineSC.MATTER_PER_CAPSULE;
        check(liquid && modeNbt && drained && full, "Matter compressor «жидкая материя»: 2 capsules' mass -> 200 mB SM, no capsule; the mode saved (old ones: capsules);"
                + " pipes drain it; a full tank waits (" + liquid + "/" + modeNbt + "/" + drained + "/" + full + ")");

        // СМ2: the reactor's by-product - 1 mB/s up to 4000, drained at its block, kept in NBT
        boolean rate = com.sc.tileentity.SingularReactorSC.byProductAfter(0, 60) == 60 && com.sc.tileentity.SingularReactorSC.byProductAfter(3990, 60) == 4000
                && com.sc.tileentity.SingularReactorSC.SM_PER_SECOND == 1 && com.sc.tileentity.SingularReactorSC.SM_TANK == 4000;
        com.sc.tileentity.TileEntityGeneratorSC g = new com.sc.tileentity.TileEntityGeneratorSC();
        g.setGeneratorType(com.sc.energy.GeneratorType.SINGULAR_REACTOR);
        g.getSingular().setSmForTest(250);
        net.minecraftforge.fluids.FluidStack gd = g.drain(net.minecraftforge.common.util.ForgeDirection.UNKNOWN, 100, true);
        boolean gDrain = gd != null && gd.amount == 100 && gd.getFluid() == com.sc.init.ModFluids.singularMatter
                && g.canDrain(net.minecraftforge.common.util.ForgeDirection.UNKNOWN, com.sc.init.ModFluids.singularMatter) && g.getSingular().getSmStored() == 150;
        net.minecraft.nbt.NBTTagCompound gn = new net.minecraft.nbt.NBTTagCompound();
        g.writeToNBT(gn);
        com.sc.tileentity.TileEntityGeneratorSC g2 = new com.sc.tileentity.TileEntityGeneratorSC();
        g2.readFromNBT(gn);
        boolean gNbt = g2.singular() && g2.getSingular().getSmStored() == 150;
        check(rate && gDrain && gNbt, "Singular reactor by-product: 1 mB/s up to 4000 mB, drained as singular matter at the block, kept in NBT ("
                + rate + "/" + gDrain + "/" + gNbt + ")");

        // the SM cell: fills to 1000 with singular matter only, drains, its fluid is the suit's 8th gas
        ItemStack cell = new ItemStack(ModItems.singularCell);
        com.sc.item.ItemSingularCellSC ci = ModItems.singularCell;
        int f1 = ci.fill(cell, new net.minecraftforge.fluids.FluidStack(com.sc.init.ModFluids.singularMatter, 600), true);
        int f2 = ci.fill(cell, new net.minecraftforge.fluids.FluidStack(com.sc.init.ModFluids.singularMatter, 600), true);
        int f3 = ci.fill(cell, new net.minecraftforge.fluids.FluidStack(com.sc.init.ModFluids.hydrogen, 100), true);
        net.minecraftforge.fluids.FluidStack d1 = ci.drain(cell, 300, true);
        boolean cellOk = f1 == 600 && f2 == 400 && f3 == 0 && d1 != null && d1.amount == 300 && com.sc.item.ItemSingularCellSC.amountOf(cell) == 700
                && com.sc.util.ArmorGasSC.Gas.of(com.sc.init.ModFluids.singularMatter) == com.sc.util.ArmorGasSC.Gas.SINGULAR_MATTER
                && com.sc.util.FluidHandSC.isContainer(cell);
        ci.drain(cell, 1000, true);
        cellOk &= com.sc.item.ItemSingularCellSC.amountOf(cell) == 0 && !cell.hasTagCompound();
        // into the Singular Station's 8th tank, as the cell's right-click pours it
        com.sc.tileentity.TileEntitySingularStationSC sst = new com.sc.tileentity.TileEntitySingularStationSC();
        int intoStation = sst.fill(net.minecraftforge.common.util.ForgeDirection.UNKNOWN,
                new net.minecraftforge.fluids.FluidStack(com.sc.init.ModFluids.singularMatter, 1000), true);
        cellOk &= intoStation == 1000 && sst.tankAmount(com.sc.util.ArmorGasSC.Gas.SINGULAR_MATTER) == 1000;
        check(cellOk, "Singular Matter cell: fills to 1000 mB of singular matter only, drains, empties clean; the suit and the station take its fluid");

        String missing = "";
        for (String lang : new String[]{"en_US", "ru_RU"}) {
            java.util.Set<String> keys = langKeys(lang);
            for (String k : new String[]{"item.siliconage.singularMatterCell.name", "sc.singStation.btn.convert", "sc.singStation.head.convert",
                    "sc.singStation.proc.3", "sc.waila.singStation.proc.3", "sc.singStation.err.noexo", "sc.singStation.err.nomaterials",
                    "sc.singStation.done.convert", "sc.singStation.materials", "sc.gui.comp.mode.liquid", "sc.gui.comp.mode.capsule",
                    "sc.waila.sm", "sc.waila.comp.liquid", "sc.cell.sm.amount", "sc.nei.conv.title", "sc.manual.singstation.conv.1",
                    "sc.manual.generator.sing.byproduct", "sc.manual.comp.liquid", "sc.manual.singular.get.1", "sc.tooltip.armor.exo.convert"}) {
                if (!keys.contains(k)) {
                    missing += lang + ":" + k + " ";
                }
            }
        }
        check(missing.isEmpty(), "Singular crafts: lang keys in both languages" + (missing.isEmpty() ? "" : " - missing " + missing));
    }

    /** Whether a shaped (ore) recipe's inputs hold this exact item (item + damage). */
    private static boolean inputsHave(net.minecraft.item.crafting.IRecipe r, ItemStack want) {
        Object[] in = r instanceof net.minecraftforge.oredict.ShapedOreRecipe ? ((net.minecraftforge.oredict.ShapedOreRecipe) r).getInput() : new Object[0];
        for (Object o : in) {
            java.util.List<?> options = o instanceof ItemStack ? java.util.Collections.singletonList(o) : o instanceof java.util.List ? (java.util.List<?>) o
                    : java.util.Collections.emptyList();
            for (Object x : options) {
                if (x instanceof ItemStack && ((ItemStack) x).getItem() == want.getItem() && ((ItemStack) x).getItemDamage() == want.getItemDamage()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void singularStage5() {
        com.sc.util.ArmorSuit sg = com.sc.util.ArmorSuit.SINGULAR;
        com.sc.util.ArmorFeature dash = com.sc.util.ArmorFeature.PHASE_DASH, press = com.sc.util.ArmorFeature.GRAV_PRESS,
                grab = com.sc.util.ArmorFeature.GRAV_GRAB, magnet = com.sc.util.ArmorFeature.MAGNET, flight = com.sc.util.ArmorFeature.GRAV_FLIGHT,
                sight = com.sc.util.ArmorFeature.CLEAR_SIGHT, anchor = com.sc.util.ArmorFeature.GRAV_ANCHOR;
        // a set: helmet 1, chestplate 3 with the press side of level 3, leggings 1 (П1 locked), boots 2
        ItemStack[] w = gasSuit(sg, true, true, true, true);
        com.sc.util.SingularLevel.setLevel(w[1], 3);
        com.sc.util.SingularLevel.setBranch(w[1], 3, com.sc.util.SingularLevel.BRANCH_A);
        com.sc.util.SingularLevel.setLevel(w[3], 2);
        boolean prof = com.sc.util.SingularProfiles.active(w[1]) == -1 && !com.sc.util.SingularProfiles.has(w[1], 0)
                && com.sc.util.SingularProfiles.holder(w) == w[1];
        // profile 0 "Combat": magnet on, flight off, sight on, the press on, the grab on, the dash on, the anchor off
        com.sc.item.ItemArmorSC.setEnabled(w[1], magnet, true);
        com.sc.item.ItemArmorSC.setEnabled(w[1], flight, false);
        com.sc.item.ItemArmorSC.setEnabled(w[0], sight, true);
        com.sc.item.ItemArmorSC.setEnabled(w[1], press, true);
        com.sc.item.ItemArmorSC.setEnabled(w[1], grab, true);
        com.sc.item.ItemArmorSC.setEnabled(w[2], dash, true);
        com.sc.item.ItemArmorSC.setEnabled(w[2], anchor, false);
        prof &= com.sc.util.SingularProfiles.select(w, 0, false) == com.sc.util.SingularProfiles.CAPTURED   // empty: takes the current switches
                && com.sc.util.SingularProfiles.active(w[1]) == 0 && com.sc.util.SingularProfiles.has(w[1], 0);
        // everything the other way round, then profile 0 again
        com.sc.item.ItemArmorSC.setEnabled(w[1], magnet, false);
        com.sc.item.ItemArmorSC.setEnabled(w[1], flight, true);
        com.sc.item.ItemArmorSC.setEnabled(w[0], sight, false);
        com.sc.item.ItemArmorSC.setEnabled(w[1], press, false);
        com.sc.item.ItemArmorSC.setEnabled(w[1], grab, false);
        com.sc.item.ItemArmorSC.setEnabled(w[2], dash, false);
        com.sc.item.ItemArmorSC.setEnabled(w[2], anchor, true);
        int[] r = com.sc.util.SingularProfiles.apply(w, 0, false);
        prof &= r != null && com.sc.item.ItemArmorSC.isEnabled(w[1], magnet) && !com.sc.item.ItemArmorSC.isEnabled(w[1], flight)
                && com.sc.item.ItemArmorSC.isEnabled(w[0], sight) && com.sc.item.ItemArmorSC.isEnabled(w[1], press)
                && !com.sc.item.ItemArmorSC.isEnabled(w[2], anchor)
                && !com.sc.item.ItemArmorSC.isEnabled(w[1], grab)          // Р2: the grab side isn't chosen - not switched on
                && !com.sc.item.ItemArmorSC.isEnabled(w[2], dash)          // П1 opens at level 2 - leggings at 1: not switched on
                && r[1] == 2 && r[0] >= 5;
        // creative: the locked ones too
        r = com.sc.util.SingularProfiles.apply(w, 0, true);
        prof &= r != null && r[0] == 2 && com.sc.item.ItemArmorSC.isEnabled(w[1], grab) && com.sc.item.ItemArmorSC.isEnabled(w[2], dash);
        // a stored profile picked again: applied (not overwritten); save current into it; a piece not stored is left alone
        com.sc.item.ItemArmorSC.setEnabled(w[1], magnet, false);
        prof &= com.sc.util.SingularProfiles.select(w, 0, false) == com.sc.util.SingularProfiles.APPLIED
                && com.sc.item.ItemArmorSC.isEnabled(w[1], magnet);
        com.sc.item.ItemArmorSC.setEnabled(w[1], magnet, false);
        prof &= com.sc.util.SingularProfiles.capture(w, 0);
        com.sc.item.ItemArmorSC.setEnabled(w[1], magnet, true);
        com.sc.util.SingularProfiles.apply(w, 0, false);
        prof &= !com.sc.item.ItemArmorSC.isEnabled(w[1], magnet);
        // the profile survives the chestplate's NBT round trip
        ItemStack copy = ItemStack.loadItemStackFromNBT(w[1].writeToNBT(new net.minecraft.nbt.NBTTagCompound()));
        prof &= com.sc.util.SingularProfiles.active(copy) == 0 && com.sc.util.SingularProfiles.has(copy, 0) && !com.sc.util.SingularProfiles.has(copy, 1);
        // no Singular chestplate: no profiles
        ItemStack[] noChest = gasSuit(sg, true, false, true, true);
        ItemStack[] exo = gasSuit(com.sc.util.ArmorSuit.EXO, true, true, true, true);
        prof &= com.sc.util.SingularProfiles.holder(noChest) == null && com.sc.util.SingularProfiles.select(noChest, 0, false) == com.sc.util.SingularProfiles.NONE
                && com.sc.util.SingularProfiles.holder(exo) == null && !com.sc.util.SingularProfiles.capture(exo, 1)
                && com.sc.util.SingularProfiles.apply(w, 2, false) == null && com.sc.util.SingularProfiles.select(w, 3, false) == com.sc.util.SingularProfiles.NONE;
        // the pure bits: a function's state from the two switch fields, ordinals past 32 in the second
        prof &= com.sc.util.SingularProfiles.wantsOn(0, 0, sight) == sight.onByDefault && sight.ordinal() >= 32
                && com.sc.util.SingularProfiles.wantsOn(0, 1 << (sight.ordinal() & 31), sight) != sight.onByDefault
                && com.sc.util.SingularProfiles.wantsOn(1 << (sight.ordinal() & 31), 0, sight) == sight.onByDefault
                && com.sc.util.SingularProfiles.wantsOn(1 << com.sc.util.ArmorFeature.HUD.ordinal(), 0, com.sc.util.ArmorFeature.HUD) != com.sc.util.ArmorFeature.HUD.onByDefault;
        check(prof, "Singular profiles (M4): an empty profile takes the current switches; applied back exactly, a function locked by level / branch never"
                + " switched on (creative: all); save current; kept in the chestplate's NBT; none without a Singular chestplate");

        // cycling: none -> 0 -> 1 -> 2 -> 0
        boolean cyc = com.sc.util.SingularProfiles.next(-1) == 0 && com.sc.util.SingularProfiles.next(0) == 1
                && com.sc.util.SingularProfiles.next(1) == 2 && com.sc.util.SingularProfiles.next(2) == 0 && com.sc.util.SingularProfiles.COUNT == 3;
        ItemStack[] w2 = gasSuit(sg, true, true, true, true);
        int a = com.sc.util.SingularProfiles.active(w2[1]);
        for (int i = 0; i < 4; i++) {
            a = com.sc.util.SingularProfiles.next(a);
            com.sc.util.SingularProfiles.select(w2, a, false);
        }
        cyc &= com.sc.util.SingularProfiles.active(w2[1]) == 0 && com.sc.util.SingularProfiles.has(w2[1], 0)
                && com.sc.util.SingularProfiles.has(w2[1], 1) && com.sc.util.SingularProfiles.has(w2[1], 2);
        com.sc.util.SingularProfiles.setActive(w2[1], -1);
        cyc &= com.sc.util.SingularProfiles.active(w2[1]) == -1;
        check(cyc, "Singular profiles (M4): the key steps Combat -> Mining -> Flight -> Combat, each empty one filled on the way");

        // М3: what a function needs, who has it, the icon states, the list
        long now = 100000L;
        com.sc.util.ArmorFeature slow = com.sc.util.ArmorFeature.TIME_SLOW, hole = com.sc.util.ArmorFeature.BLACK_HOLE,
                boost = com.sc.util.ArmorFeature.SINGULARITY, rescue = com.sc.util.ArmorFeature.VOID_RESCUE;
        boolean hud = com.sc.util.SingularHud.FEATURES.length == 9 && com.sc.util.SingularHud.automatic(rescue)
                && com.sc.util.SingularHud.automatic(com.sc.util.ArmorFeature.HEAT_VENT) && !com.sc.util.SingularHud.automatic(dash);
        for (com.sc.util.ArmorFeature f : com.sc.util.SingularHud.FEATURES) {
            hud &= com.sc.util.SingularHud.gases(f).length == com.sc.util.SingularHud.amounts(f).length && com.sc.util.SingularHud.gases(f).length > 0
                    && com.sc.util.SingularHud.cooldownTicks(f) > 1 && java.util.Arrays.asList(com.sc.util.SingularHud.gases(f)).contains(f.gas());
        }
        ItemStack[] dry = gasSuit(sg, true, true, true, true);
        ItemStack[] full = gasSuit(sg, true, true, true, true);
        fillSuit(full, 100);
        hud &= com.sc.util.SingularHud.missingGas(dry, slow, 1F) == com.sc.util.ArmorGasSC.Gas.KRYPTON
                && com.sc.util.SingularHud.missingGas(full, slow, 1F) == null && com.sc.util.SingularHud.missingGas(full, hole, 2F) == null
                && com.sc.util.SingularHud.missingGas(dry, rescue, 1F) == com.sc.util.ArmorGasSC.Gas.HELIUM;
        com.sc.util.ArmorGasSC.setAmount(full[1], com.sc.util.ArmorGasSC.Gas.SINGULAR_MATTER, 60);   // enough for Н4 (50), not twice (К1 boost)
        hud &= com.sc.util.SingularHud.missingGas(full, slow, 1F) == null
                && com.sc.util.SingularHud.missingGas(full, slow, 2F) == com.sc.util.ArmorGasSC.Gas.SINGULAR_MATTER;
        // who has what: levels and branches (w: chestplate 3 + press, leggings 1, boots 2)
        hud &= com.sc.util.SingularHud.has(w, press, false) && !com.sc.util.SingularHud.has(w, grab, false) && !com.sc.util.SingularHud.has(w, dash, false)
                && !com.sc.util.SingularHud.has(w, slow, false) && com.sc.util.SingularHud.has(w, rescue, false)
                && com.sc.util.SingularHud.has(w, com.sc.util.ArmorFeature.HEAT_VENT, false)
                && com.sc.util.SingularHud.has(w, boost, true) && com.sc.util.SingularHud.has(w, dash, true)
                && !com.sc.util.SingularHud.has(noChest, boost, true) && !com.sc.util.SingularHud.has(exo, dash, true);
        // states
        int H = com.sc.util.SingularHud.HIDDEN;
        hud &= com.sc.util.SingularHud.stateOf(dash, false, now + 50, now, false, true, 0, 0) == H
                && com.sc.util.SingularHud.stateOf(dash, true, now + 50, now, true, true, 0, 0) == com.sc.util.SingularHud.COOLING
                && com.sc.util.SingularHud.stateOf(dash, true, now - 10, now, false, true, 0, 0) == com.sc.util.SingularHud.READY
                && com.sc.util.SingularHud.stateOf(dash, true, now - 10, now, true, false, 0, 0) == com.sc.util.SingularHud.NOGAS
                && com.sc.util.SingularHud.stateOf(dash, true, now - com.sc.util.SingularHud.READY_TICKS, now, false, true, 0, 0) == H
                && com.sc.util.SingularHud.stateOf(dash, true, 0, now, false, true, 0, 0) == H
                && com.sc.util.SingularHud.stateOf(dash, true, 0, now, true, true, 0, 0) == com.sc.util.SingularHud.NOGAS
                && com.sc.util.SingularHud.stateOf(dash, true, 0, now, true, false, 0, 0) == H
                && com.sc.util.SingularHud.stateOf(boost, true, now + 9000, now, false, true, now + 100, now + 1300) == com.sc.util.SingularHud.BOOST
                && com.sc.util.SingularHud.stateOf(boost, true, now + 9000, now, false, true, now - 1, now + 1300) == com.sc.util.SingularHud.WEAK
                && com.sc.util.SingularHud.stateOf(boost, true, now + 9000, now, false, true, now - 1, now - 1) == com.sc.util.SingularHud.COOLING
                && com.sc.util.SingularHud.stateOf(slow, true, now + 3000, now, false, true, now + 40, 0) == com.sc.util.SingularHud.RUNNING
                && com.sc.util.SingularHud.stateOf(dash, true, now + 50, now, false, true, now + 40, 0) == com.sc.util.SingularHud.COOLING;
        java.util.List<Integer> shown = com.sc.util.SingularHud.shown(new int[]{H, com.sc.util.SingularHud.COOLING, H, com.sc.util.SingularHud.NOGAS,
                com.sc.util.SingularHud.READY});
        hud &= shown.size() == 3 && shown.get(0) == 1 && shown.get(1) == 3 && shown.get(2) == 4 && com.sc.util.SingularHud.shown(null).isEmpty();
        hud &= Math.abs(com.sc.util.SingularHud.sweep(now + 30, now, 60) - 0.5F) < 1e-4 && com.sc.util.SingularHud.sweep(now, now, 60) == 0F
                && com.sc.util.SingularHud.sweep(now + 999, now, 60) == 1F
                && "18s".equals(com.sc.util.SingularHud.time(18 * 20 - 5, "%ss")) && "2:33".equals(com.sc.util.SingularHud.time(153 * 20, "%ss"))
                && "1:00".equals(com.sc.util.SingularHud.time(1200, "%ss")) && "0s".equals(com.sc.util.SingularHud.time(-4, "%ss"));
        check(hud, "Singular cooldown HUD (M3): gases per use (x2 boosted) from the synced tanks, only functions the pieces have,"
                + " states cooling / ready / no gas / boost / weak / running, the icon list, the sweep and the time");

        // network: pseudo ids past every function, the profile bytes, the messages read back
        io.netty.buffer.ByteBuf buf = io.netty.buffer.Unpooled.buffer();
        new com.sc.handler.ArmorNetSC.CooldownMessage(com.sc.util.SingularCooldowns.P_SLOW, 123456789012L).toBytes(buf);
        com.sc.handler.ArmorNetSC.CooldownMessage cm = new com.sc.handler.ArmorNetSC.CooldownMessage();
        cm.fromBytes(buf);
        buf = io.netty.buffer.Unpooled.buffer();
        new com.sc.handler.ArmorNetSC.Message(com.sc.handler.ArmorNetSC.PROFILE_SAVE, -1).toBytes(buf);
        com.sc.handler.ArmorNetSC.Message mm = new com.sc.handler.ArmorNetSC.Message();
        mm.fromBytes(buf);
        java.util.Set<Byte> bytes = new java.util.HashSet<Byte>();
        for (byte b : new byte[]{com.sc.handler.ArmorNetSC.TOGGLE, com.sc.handler.ArmorNetSC.POWER_MODE, com.sc.handler.ArmorNetSC.DASH,
                com.sc.handler.ArmorNetSC.ANNIHILATE, com.sc.handler.ArmorNetSC.BLADE_TOGGLE, com.sc.handler.ArmorNetSC.BLADE_SWEEP,
                com.sc.handler.ArmorNetSC.BLADE_WAVE, com.sc.handler.ArmorNetSC.BLADE_LUNGE, com.sc.handler.ArmorNetSC.REMOVE_CHIPS,
                com.sc.handler.ArmorNetSC.DRILL_TOGGLE, com.sc.handler.ArmorNetSC.DRILL_LASER, com.sc.handler.ArmorNetSC.GLOW_COLOR,
                com.sc.handler.ArmorNetSC.GAS_FILL, com.sc.handler.ArmorNetSC.GAS_FILL_ALL, com.sc.handler.ArmorNetSC.PHASE_DASH,
                com.sc.handler.ArmorNetSC.GRAV_PRESS, com.sc.handler.ArmorNetSC.GRAV_GRAB, com.sc.handler.ArmorNetSC.TIME_SLOW,
                com.sc.handler.ArmorNetSC.BLACK_HOLE, com.sc.handler.ArmorNetSC.GRAV_DOME, com.sc.item.ArmorLogicSC.AIR_JUMP_ACTION,
                com.sc.handler.ArmorNetSC.SINGULARITY, com.sc.handler.ArmorNetSC.BRANCH, com.sc.handler.ArmorNetSC.PROFILE_SELECT,
                com.sc.handler.ArmorNetSC.PROFILE_SAVE, com.sc.handler.ArmorNetSC.PROFILE_NEXT}) {
            bytes.add(b);
        }
        boolean net = cm.feature == com.sc.util.SingularCooldowns.P_SLOW && cm.end == 123456789012L
                && mm.action == com.sc.handler.ArmorNetSC.PROFILE_SAVE && mm.feature == -1
                && com.sc.handler.ArmorNetSC.PROFILE_SELECT == 23 && com.sc.handler.ArmorNetSC.PROFILE_SAVE == 24
                && com.sc.handler.ArmorNetSC.PROFILE_NEXT == 25 && bytes.size() == 26
                && com.sc.handler.ArmorNetSC.featureOfAction(com.sc.handler.ArmorNetSC.PROFILE_NEXT) == null
                && com.sc.util.ArmorFeature.values().length <= com.sc.util.SingularCooldowns.P_FIRST
                && com.sc.util.SingularCooldowns.P_RES < 64 && com.sc.item.SingularPowersSC.STATE_KEYS.length == 3;
        // the client's copy: a pseudo id kept apart from the functions, cleared with them
        com.sc.util.SingularCooldowns.clientSet(com.sc.util.SingularCooldowns.P_BOOST, 777L);
        com.sc.util.SingularCooldowns.clientSet(com.sc.util.SingularCooldowns.P_RES, 1L);
        net &= com.sc.util.SingularCooldowns.clientEnd(com.sc.util.SingularCooldowns.P_BOOST) == 777L
                && com.sc.util.SingularCooldowns.clientEnd(com.sc.util.SingularCooldowns.P_RES) == 1L
                && com.sc.util.SingularCooldowns.clientEnd(com.sc.util.ArmorFeature.SINGULARITY.ordinal()) != 777L;
        com.sc.util.SingularCooldowns.clientSet(-1, 0L);
        net &= com.sc.util.SingularCooldowns.clientEnd(com.sc.util.SingularCooldowns.P_BOOST) == 0L
                && com.sc.util.SingularCooldowns.clientEnd(com.sc.util.SingularCooldowns.P_RES) == 0L;
        check(net, "Singular stage 5 network: PROFILE_SELECT / SAVE / NEXT = 23 / 24 / 25 (no clash), the HUD state ids 60-63 past every function,"
                + " the messages read back as written");

        // texts
        String missing = "";
        for (String lang : new String[]{"en_US", "ru_RU"}) {
            java.util.Set<String> keys = langKeys(lang);
            java.util.List<String> want = new java.util.ArrayList<String>();
            java.util.Collections.addAll(want, "key.sc.profile", "sc.levelgui.tab", "sc.levelgui.pieces", "sc.levelgui.sync.yes", "sc.levelgui.sync.no",
                    "sc.levelgui.sync.notall", "sc.levelgui.sync.one", "sc.levelgui.tasks", "sc.levelgui.ready", "sc.levelgui.needpoints",
                    "sc.levelgui.needtask", "sc.levelgui.cost.ready", "sc.levelgui.cost.next", "sc.levelgui.branches", "sc.levelgui.profiles",
                    "sc.levelgui.profile.save", "sc.levelgui.profile.key", "sc.levelgui.profile.nokey", "sc.levelgui.hud", "sc.levelgui.bonus.head",
                    "sc.levelgui.page.next", "sc.levelgui.page.back", "sc.armor.sing.profile.on", "sc.armor.sing.profile.captured",
                    "sc.armor.sing.profile.saved", "sc.armor.sing.profile.locked", "sc.armor.sing.profile.nochest", "sc.armorhud.sing.level",
                    "sc.armorhud.sing.profile", "sc.armorhud.sing.ready", "sc.armorhud.sing.ready2", "sc.singhud.sec", "sc.singhud.ready",
                    "sc.singhud.nogas", "sc.singhud.boost", "sc.singhud.weak", "sc.singhud.res", "sc.manual.singular.levelhead",
                    "sc.manual.singular.level.1", "sc.manual.singular.level.2", "sc.manual.singular.level.3");
            for (int i = 0; i < com.sc.util.SingularProfiles.COUNT; i++) {
                want.add("sc.armor.sing.profile.name." + i);
            }
            for (String pos : new String[]{"off", "hotbar", "top", "right", "left"}) {   // ArmorKeyBindsSC.HUD_POS (client)
                want.add("sc.levelgui.hud." + pos);
            }
            for (int t = 2; t <= 5; t++) {
                for (int i = 0; i < 3; i++) {
                    want.add("sc.levelgui.task." + t + "." + i);
                }
            }
            for (com.sc.util.ArmorFeature f : com.sc.util.SingularHud.FEATURES) {
                want.add("sc.singhud.code." + f.name().toLowerCase(java.util.Locale.ROOT));
                want.add("sc.singhud.name." + f.name().toLowerCase(java.util.Locale.ROOT));
            }
            for (int bl : new int[]{3, 5}) {
                for (int c = 1; c <= 2; c++) {
                    want.add("sc.levelgui.branch.short." + com.sc.util.SingularLevel.branchFeature(bl, c).name().toLowerCase(java.util.Locale.ROOT));
                }
            }
            for (String k : want) {
                if (!keys.contains(k)) {
                    missing += " " + lang + ":" + k;
                }
            }
        }
        check(missing.isEmpty(), "Singular stage 5: Level tab, profiles, HUD and handbook texts in en_US and ru_RU" + missing);
    }

    /**
     * Stage 4, the Singular Service Station (docs/plan-singular-armor.md §7): the cost formula (ПР3,
     * resonance), the speed multipliers, the draw / pause / refund math, the process NBT round trip,
     * the transfer / sync / branch costs, the scheme cycling, the station itself without a world
     * (start, lock, cancel by the 50% rule, the catalyst, the branch change, NBT), the texts.
     */
    private static void singularStation() {
        long[] set3 = com.sc.util.SingularStationMath.moderniseCost(new int[]{3, 3, 3, 3}, false);
        long[] one3 = com.sc.util.SingularStationMath.moderniseCost(new int[]{0, 3, 0, 0}, false);
        long[] two1 = com.sc.util.SingularStationMath.moderniseCost(new int[]{1, 1, 0, 0}, false);
        long[] res3 = com.sc.util.SingularStationMath.moderniseCost(new int[]{3, 3, 3, 3}, true);
        long[] mixed = com.sc.util.SingularStationMath.moderniseCost(new int[]{1, 2, 0, 0}, false);
        long[] four4 = com.sc.util.SingularStationMath.moderniseCost(new int[]{4, 4, 4, 4}, false);
        boolean cost = java.util.Arrays.equals(set3, new long[]{800000000L, 400, 6400, 1600, 800})
                && java.util.Arrays.equals(one3, new long[]{250000000L, 125, 2000, 500, 250})
                && java.util.Arrays.equals(two1, new long[]{25000000L, 50, 1000, 250, 0})
                && res3[0] == 720000000L && res3[1] == 400
                && java.util.Arrays.equals(mixed, new long[]{62500000L, 88, 1500, 375, 125})
                && four4[0] == 3200000000L && four4[1] == 800
                && com.sc.util.SingularStationMath.moderniseTicks(new int[]{1, 2, 0, 0}) == 3600
                && com.sc.util.SingularStationMath.moderniseTicks(new int[]{4, 0, 0, 0}) == 12000
                && com.sc.util.SingularStationMath.needsCatalyst(new int[]{0, 4, 0, 0}) && !com.sc.util.SingularStationMath.needsCatalyst(new int[]{3, 3, 3, 3})
                && com.sc.util.SingularStationMath.pieces(new int[]{0, 3, 5, 1}) == 2;
        check(cost, "Singular station cost: the set of 4 x0.8 (3->4: 800 M EU, SM 400), one piece a quarter, resonance EU x0.9, mixed levels summed "
                + java.util.Arrays.toString(set3) + " " + java.util.Arrays.toString(mixed));

        boolean speed = Math.abs(com.sc.util.SingularStationMath.speed(0, false) - 1.0) < 1e-9
                && Math.abs(com.sc.util.SingularStationMath.speed(3, false) - 1.75) < 1e-9
                && Math.abs(com.sc.util.SingularStationMath.speed(4, true) - 2.6) < 1e-9
                && Math.abs(com.sc.util.SingularStationMath.speed(9, false) - 2.0) < 1e-9
                && Math.abs(com.sc.util.SingularStationMath.speed(0, true) - 1.3) < 1e-9
                && com.sc.util.SingularStationMath.duration(12000, 2.6) == 4616 && com.sc.util.SingularStationMath.duration(1200, 2.0) == 600
                && com.sc.util.SingularStationMath.duration(1200, 1.0) == 1200;
        check(speed, "Singular station speed: +25% a stabiliser (4 at most), resonance x1.3; 10 min with 4 + resonance = 4616 ticks");

        long[] c = {100, 10, 0, 7, 0};
        long[] need = com.sc.util.SingularStationMath.needFor(c, new long[5], 0.5);
        double prog = com.sc.util.SingularStationMath.progressOf(c, new long[]{50, 2, 0, 4, 0}, 0.5);
        double full = com.sc.util.SingularStationMath.progressOf(c, new long[]{100, 10, 0, 7, 0}, 1.0);
        double none = com.sc.util.SingularStationMath.progressOf(new long[5], new long[5], 0.3);
        boolean math = java.util.Arrays.equals(need, new long[]{50, 5, 0, 4, 0})
                && Math.abs(prog - 0.2) < 1e-9 && full == 1.0 && Math.abs(none - 0.3) < 1e-9
                && java.util.Arrays.equals(com.sc.util.SingularStationMath.needFor(c, new long[]{60, 10, 0, 7, 0}, 0.5), new long[5])
                && com.sc.util.SingularStationMath.refund(7) == 3 && com.sc.util.SingularStationMath.refund(-5) == 0
                && com.sc.util.SingularStationMath.refund(4000000000L) == 2000000000L;
        check(math, "Singular station process math: draw what progress p needs, a short resource holds the progress (0.2), refund half");

        com.sc.tileentity.SingularProcessSC p = new com.sc.tileentity.SingularProcessSC();
        p.kind = com.sc.tileentity.SingularProcessSC.KIND_TRANSFER;
        p.mask = 0x13;
        p.progress = 0.4375;
        p.baseTicks = 6000;
        p.cost[0] = 3200000000L;
        p.drawn[0] = 2900000000L;
        p.cost[2] = 16000;
        p.drawn[2] = 999;
        p.catalystEu = 1500000000L;
        p.levels[4] = 4;
        p.target = 2;
        p.starter = "Steve";
        p.resonance = true;
        net.minecraft.nbt.NBTTagCompound pt = new net.minecraft.nbt.NBTTagCompound();
        p.writeToNBT(pt);
        com.sc.tileentity.SingularProcessSC q = com.sc.tileentity.SingularProcessSC.readFromNBT(pt);
        boolean nbt = q != null && q.kind == p.kind && q.mask == 0x13 && q.progress == 0.4375 && q.baseTicks == 6000
                && q.cost[0] == 3200000000L && q.drawn[0] == 2900000000L && q.drawn[2] == 999 && q.catalystEu == 1500000000L
                && q.levels[4] == 4 && q.target == 2 && "Steve".equals(q.starter) && q.resonance && q.locks(0) && q.locks(4) && !q.locks(2)
                && com.sc.tileentity.SingularProcessSC.readFromNBT(new net.minecraft.nbt.NBTTagCompound()) == null;
        check(nbt, "Singular station process NBT round trip (longs past 2^31, mask, levels, starter)");

        long[] tr3 = com.sc.util.SingularStationMath.transferCost(3);
        long[] sync = com.sc.util.SingularStationMath.syncCost(new int[]{3, 1, 0, 2});
        boolean more = java.util.Arrays.equals(tr3, new long[]{31250000L, 44, 750, 188, 63})
                && com.sc.util.SingularStationMath.transferTicks(3) == 2400
                && java.util.Arrays.equals(com.sc.util.SingularStationMath.transferCost(1), new long[5])
                && java.util.Arrays.equals(sync, new long[]{112500000L, 150, 2500, 625, 250})
                && com.sc.util.SingularStationMath.syncTicks(new int[]{3, 1, 0, 2}) == 4800
                && java.util.Arrays.equals(com.sc.util.SingularStationMath.syncCost(new int[]{2, 2, 0, 2}), new long[5])
                && com.sc.util.SingularStationMath.BRANCH_SM == 100
                && com.sc.item.ItemBatterySC.TIERS[com.sc.util.SingularStationMath.CORE_META] == com.sc.energy.Tier.SV;
        check(more, "Singular station transfer (50% of the rows / 4), sync (per-piece rows, no set discount), branch 100 mB, the core is the SV battery "
                + java.util.Arrays.toString(tr3) + " " + java.util.Arrays.toString(sync));

        com.sc.util.SingularScheme sa = com.sc.util.SingularScheme.A, sk = com.sc.util.SingularScheme.K;
        boolean cyc = com.sc.util.SingularStationMath.cycle(sa, 1) == com.sc.util.SingularScheme.B && com.sc.util.SingularStationMath.cycle(sa, -1) == sk
                && com.sc.util.SingularStationMath.cycle(sk, 1) == sa && com.sc.util.SingularStationMath.cycle(null, 1) == com.sc.util.SingularScheme.B;
        check(cyc, "Singular station scheme cycling wraps (A < K, K > A)");

        // the station itself, no world
        com.sc.item.ItemArmorSC[] sg = ModItems.ARMOR.get(com.sc.util.ArmorSuit.SINGULAR);
        com.sc.tileentity.TileEntitySingularStationSC st = new com.sc.tileentity.TileEntitySingularStationSC();
        ItemStack chest = new ItemStack(sg[1]), helm = new ItemStack(sg[0]), legs = new ItemStack(sg[2]);
        com.sc.util.SingularLevel.setLevel(chest, 3);
        com.sc.util.SingularLevel.setLevel(helm, 1);
        st.setInventorySlotContents(1, chest);
        st.setInventorySlotContents(0, helm);
        st.setInventorySlotContents(2, legs);
        com.sc.util.ArmorGasSC.Gas smG = com.sc.util.ArmorGasSC.Gas.SINGULAR_MATTER;
        boolean base = st.getTier() == com.sc.energy.Tier.SV && st.tankCapacity(smG) == com.sc.tileentity.TileEntitySingularStationSC.SM_TANK
                && st.tankCapacity(com.sc.util.ArmorGasSC.Gas.HELIUM) == com.sc.tileentity.TileEntityArmorStationSC.TANK_CAPACITY
                && st.getSizeInventory() == com.sc.tileentity.TileEntitySingularStationSC.SING_SLOTS
                && st.isItemValidForSlot(com.sc.tileentity.TileEntitySingularStationSC.DONOR_SLOT, new ItemStack(sg[3]))
                && !st.isItemValidForSlot(com.sc.tileentity.TileEntitySingularStationSC.DONOR_SLOT, new ItemStack(ModItems.ARMOR.get(com.sc.util.ArmorSuit.EXO)[3]))
                && st.isItemValidForSlot(com.sc.tileentity.TileEntitySingularStationSC.CATALYST_SLOT, new ItemStack(ModItems.battery, 1, 6))
                && !st.isItemValidForSlot(com.sc.tileentity.TileEntitySingularStationSC.CATALYST_SLOT, new ItemStack(ModItems.battery, 1, 5));
        st.fillTank(smG, 4500, true);
        boolean smCap = st.tankAmount(smG) == 4000;
        String e1 = st.changeBranch(3, com.sc.util.SingularLevel.BRANCH_B);
        String e2 = st.changeBranch(3, com.sc.util.SingularLevel.BRANCH_B);
        String e3 = st.changeBranch(5, com.sc.util.SingularLevel.BRANCH_A);
        boolean branch = e1 == null && com.sc.util.SingularLevel.branchChoice(chest, 3) == com.sc.util.SingularLevel.BRANCH_B
                && st.tankAmount(smG) == 3900 && "sc.singStation.err.samebranch".equals(e2) && "sc.singStation.err.branchlevel".equals(e3);
        check(base && smCap && branch, "Singular station: SV, SM tank 4000 mB, donor / catalyst slots take only their items, branch change for 100 mB SM ("
                + e1 + "/" + e2 + "/" + e3 + ")");

        boolean scheme = st.cycleScheme(1) && com.sc.util.SingularScheme.of(chest) == com.sc.util.SingularScheme.B
                && com.sc.util.SingularScheme.of(helm) == com.sc.util.SingularScheme.B && st.cycleScheme(-1)
                && com.sc.util.SingularScheme.of(legs) == sa;
        check(scheme, "Singular station scheme buttons: every Singular piece in the slots takes the next / previous scheme");

        // a modernisation started by hand (readiness is the player's), some of it drawn, then cancelled: half back, slots unlocked
        st.setScanForTest(4, false);
        String start = st.startModerniseFor(new int[]{1, 3, 0, 0}, "tester");
        com.sc.tileentity.SingularProcessSC run = st.getProcess();
        boolean started = start == null && run != null && run.locks(0) && run.locks(1) && !run.locks(2)
                && st.isLocked(0) && !st.isLocked(2) && !st.isItemValidForSlot(1, new ItemStack(sg[1])) && !st.canExtractItem(1, chest, 0)
                && run.cost[0] == (50000000L + 1000000000L) / 4 && run.baseTicks == 5 * 1200
                && "sc.singStation.err.busy".equals(st.startSync(null));
        run.drawn[0] = 2000001;
        run.drawn[2] = 301;
        st.cancelProcess();
        boolean cancel = st.getProcess() == null && st.getEnergyStored() == 1000000 && st.tankAmount(com.sc.util.ArmorGasSC.Gas.HELIUM) == 150 && !st.isLocked(0);
        check(started && cancel, "Singular station: start locks the ready pieces' slots, cancel gives half of the drawn EU / gas back and unlocks ("
                + start + ", EU " + st.getEnergyStored() + ")");

        // 4 -> 5 needs the core; it is consumed, its charge counts, a cancel gives back a core with half of it
        ItemStack boots = new ItemStack(sg[3]);
        com.sc.util.SingularLevel.setLevel(boots, 4);
        st.setInventorySlotContents(3, boots);
        String noCore = st.startModerniseFor(new int[]{0, 0, 0, 4}, "tester");
        ItemStack core = new ItemStack(ModItems.battery, 1, 6);
        com.sc.item.ItemBatterySC.setCharge(core, 600000000L);
        st.setInventorySlotContents(com.sc.tileentity.TileEntitySingularStationSC.CATALYST_SLOT, core);
        String withCore = st.startModerniseFor(new int[]{0, 0, 0, 4}, "tester");
        com.sc.tileentity.SingularProcessSC cat = st.getProcess();
        boolean catOk = "sc.singStation.err.nocatalyst".equals(noCore) && withCore == null && cat != null
                && cat.catalystEu == 600000000L && cat.drawn[0] == 600000000L && cat.cost[0] == 1000000000L
                && st.getStackInSlot(com.sc.tileentity.TileEntitySingularStationSC.CATALYST_SLOT) == null
                && st.isLocked(com.sc.tileentity.TileEntitySingularStationSC.CATALYST_SLOT);
        // the station saves the process and reads it back
        net.minecraft.nbt.NBTTagCompound saved = new net.minecraft.nbt.NBTTagCompound();
        st.writeToNBT(saved);
        com.sc.tileentity.TileEntitySingularStationSC back = new com.sc.tileentity.TileEntitySingularStationSC();
        back.readFromNBT(saved);
        boolean reload = back.getProcess() != null && back.getProcess().catalystEu == 600000000L && back.getProcess().locks(3)
                && back.getStackInSlot(1) != null && back.tankAmount(smG) == 3900 && back.getTier() == com.sc.energy.Tier.SV;
        st.cancelProcess();
        ItemStack coreBack = st.getStackInSlot(com.sc.tileentity.TileEntitySingularStationSC.CATALYST_SLOT);
        catOk &= coreBack != null && com.sc.tileentity.TileEntitySingularStationSC.isCore(coreBack)
                && com.sc.item.ItemBatterySC.chargeOf(coreBack) == 300000000L;
        check(catOk && reload, "Singular station catalyst: 4 -> 5 without a core refused, the core consumed and its charge counted, the process saved "
                + "and read back, a cancel returns a core with half (" + noCore + ")");

        // Ф4 transfer on two pieces, Ф4 / Ф5 starts
        ItemStack donor = new ItemStack(sg[1]), fresh = new ItemStack(sg[1]);
        com.sc.util.SingularLevel.setLevel(donor, 4);
        com.sc.util.SingularLevel.setPoints(donor, 1234);
        com.sc.util.SingularLevel.setBranch(donor, 3, com.sc.util.SingularLevel.BRANCH_A);
        com.sc.tileentity.TileEntitySingularStationSC.transferLevel(donor, fresh);
        boolean tr = com.sc.util.SingularLevel.levelOf(fresh) == 4 && com.sc.util.SingularLevel.points(fresh) == 1234
                && com.sc.util.SingularLevel.branchChoice(fresh, 3) == com.sc.util.SingularLevel.BRANCH_A
                && com.sc.util.SingularLevel.levelOf(donor) == 1 && com.sc.util.SingularLevel.points(donor) == 0
                && com.sc.util.SingularLevel.branchChoice(donor, 3) == com.sc.util.SingularLevel.BRANCH_NONE;
        com.sc.tileentity.TileEntitySingularStationSC t2 = new com.sc.tileentity.TileEntitySingularStationSC();
        ItemStack lv1 = new ItemStack(sg[1]), old = new ItemStack(sg[1]);
        com.sc.util.SingularLevel.setLevel(old, 3);
        t2.setInventorySlotContents(1, lv1);
        t2.setInventorySlotContents(com.sc.tileentity.TileEntitySingularStationSC.DONOR_SLOT, old);
        String trStart = t2.startTransfer(null);
        boolean trProc = trStart == null && t2.getProcess().kind == com.sc.tileentity.SingularProcessSC.KIND_TRANSFER
                && t2.getProcess().locks(1) && t2.isLocked(com.sc.tileentity.TileEntitySingularStationSC.DONOR_SLOT)
                && t2.getProcess().cost[0] == 31250000L;
        t2.cancelProcess();
        ItemStack h3 = new ItemStack(sg[0]);
        com.sc.util.SingularLevel.setLevel(h3, 3);
        t2.setInventorySlotContents(0, h3);
        String syncStart = t2.startSync(null);
        boolean syncProc = syncStart == null && t2.getProcess().kind == com.sc.tileentity.SingularProcessSC.KIND_SYNC
                && t2.getProcess().target == 3 && t2.getProcess().locks(1) && !t2.getProcess().locks(0)
                && t2.getProcess().cost[0] == (50000000L + 200000000L) / 4;
        check(tr && trProc && syncProc, "Singular station: transfer moves level / points / branches and resets the donor; transfer and sync start ("
                + trStart + "/" + syncStart + ")");

        String missing = "";
        for (String lang : new String[]{"en_US", "ru_RU"}) {
            java.util.Set<String> keys = langKeys(lang);
            java.util.List<String> want = new java.util.ArrayList<String>();
            java.util.Collections.addAll(want, "tile.siliconage.singularStation.name", "tile.siliconage.gravStabiliser.name",
                    "sc.singStation.tooltip", "sc.singStation.details", "sc.singStation.details2", "sc.singStation.howto",
                    "sc.gravStabiliser.tooltip", "sc.gravStabiliser.details", "sc.gravStabiliser.howto",
                    "sc.singStation.err.busy", "sc.singStation.err.noready", "sc.singStation.err.nocatalyst", "sc.singStation.err.nosync",
                    "sc.singStation.err.notransfer", "sc.singStation.err.nochest", "sc.singStation.err.branchlevel", "sc.singStation.err.samebranch",
                    "sc.singStation.err.nosm", "sc.singStation.done.sync", "sc.singStation.done.transfer", "sc.singStation.proc.0",
                    "sc.singStation.proc.1", "sc.singStation.proc.2", "sc.waila.singStation.proc.0", "sc.waila.singStation.proc.1",
                    "sc.waila.singStation.proc.2", "sc.waila.singStation.idle", "sc.waila.singStation.paused", "sc.waila.singStation.speed",
                    "sc.waila.gravStabiliser.none", "sc.waila.gravStabiliser.linked", "sc.waila.gravStabiliser.work",
                    "sc.manual.singstation.1", "sc.manual.singstation.row", "sc.manual.singstation.stab", "sc.singStation.time.min",
                    "sc.singStation.time.sec", "sc.singStation.unit.m");
            for (String k : want) {
                if (!keys.contains(k)) {
                    missing += " " + lang + ":" + k;
                }
            }
        }
        check(missing.isEmpty(), "Singular station: names, tooltips, chat, WAILA and handbook texts in en_US and ru_RU" + missing);
    }

    /** The keys of one of the mod's lang files. */
    private static java.util.Set<String> langKeys(String lang) {
        java.util.Set<String> keys = new java.util.HashSet<String>();
        java.io.InputStream in = SelfTestSC.class.getResourceAsStream("/assets/siliconage/lang/" + lang + ".lang");
        if (in == null) {
            return keys;
        }
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(in, "UTF-8"));
            for (String line; (line = r.readLine()) != null; ) {
                int eq = line.indexOf('=');
                if (eq > 0) {
                    keys.add(line.substring(0, eq));
                }
            }
            r.close();
        } catch (java.io.IOException e) {
            // an empty set: every key missing
        }
        return keys;
    }

    /** Fills every tank of the set to `pct` percent. */
    private static void fillSuit(ItemStack[] w, int pct) {
        for (ItemStack s : w) {
            if (s == null) {
                continue;
            }
            for (com.sc.util.ArmorGasSC.Gas g : com.sc.util.ArmorGasSC.Gas.values()) {
                com.sc.util.ArmorGasSC.setAmount(s, g, com.sc.util.ArmorGasSC.capacity(s, g) * pct / 100);
            }
        }
    }

    /**
     * The strict rules: Nano runs its functions on EU (oxygen only to breathe); Quantum / Exo need each
     * function's own gas (ArmorFeature.gas()), and without helium go into emergency mode - everything
     * off but the HUD, the plating only as good as iron; the old-world hydrogen start, once.
     */
    private static void armorGasRules() {
        com.sc.util.ArmorGasSC.Gas h2 = com.sc.util.ArmorGasSC.Gas.HYDROGEN, he = com.sc.util.ArmorGasSC.Gas.HELIUM,
                kr = com.sc.util.ArmorGasSC.Gas.KRYPTON;
        // the table: one place, the gases the rules name
        boolean table = com.sc.util.ArmorFeature.FLIGHT.gas() == h2 && com.sc.util.ArmorFeature.DASH.gas() == h2
                && com.sc.util.ArmorFeature.SHIELD.gas() == he && com.sc.util.ArmorFeature.CHARGER.gas() == he
                && com.sc.util.ArmorFeature.CLEANSE.gas() == com.sc.util.ArmorGasSC.Gas.OXYGEN
                && com.sc.util.ArmorFeature.THERMAL.gas() == kr && com.sc.util.ArmorFeature.WATER_WALK.gas() == com.sc.util.ArmorGasSC.Gas.ARGON
                && com.sc.util.ArmorFeature.RAD_SHIELD.gas() == com.sc.util.ArmorGasSC.Gas.HEAVY_WATER
                && com.sc.util.ArmorFeature.HUD.gas() == null && com.sc.util.ArmorFeature.STEP_ASSIST.gas() == null
                && com.sc.util.ArmorFeature.SOLAR.gas() == null && com.sc.util.ArmorFeature.SET_AURA.gas() == null
                && com.sc.util.ArmorFeature.JUMP.gasUse() == 0.2F && com.sc.util.ArmorFeature.FLIGHT.gasUse() == 1F
                && com.sc.util.ArmorFeature.NIGHT_VISION.gasUseKind() == com.sc.util.ArmorFeature.USE_MINUTE;
        check(table, "strict rules: the function -> gas table (hydrogen moves, helium works, krypton sees, argon fire, heavy water radiation)");

        // Nano: can't fly at all; its functions work with empty tanks (breathing still needs oxygen)
        ItemStack[] nano = gasSuit(com.sc.util.ArmorSuit.NANO, true, true, true, true);
        boolean nanoOk = !com.sc.util.ArmorFeature.FLIGHT.availableIn(com.sc.util.ArmorSuit.NANO, 1)
                && com.sc.item.ArmorLogicSC.worksIn(nano, com.sc.util.ArmorFeature.NIGHT_VISION)
                && com.sc.item.ArmorLogicSC.worksIn(nano, com.sc.util.ArmorFeature.CHARGER)
                && com.sc.item.ArmorLogicSC.worksIn(nano, com.sc.util.ArmorFeature.FALL_DAMPING)
                && com.sc.item.ArmorLogicSC.worksIn(nano, com.sc.util.ArmorFeature.STEP_ASSIST)
                && !com.sc.item.ArmorLogicSC.emergency(nano)
                && !com.sc.item.ArmorLogicSC.worksIn(nano, com.sc.util.ArmorFeature.AIR)
                && com.sc.item.ArmorLogicSC.missingGas(nano, com.sc.util.ArmorFeature.AIR) == com.sc.util.ArmorGasSC.Gas.OXYGEN;
        check(nanoOk, "strict rules: Nano can't fly; its functions run without gases (no emergency mode), breathing needs oxygen");

        // Quantum: helium in the loop, no hydrogen - no flight; hydrogen in - flight
        ItemStack[] q = gasSuit(com.sc.util.ArmorSuit.QUANTUM, true, true, true, true);
        fillSuit(q, 50);
        com.sc.util.ArmorGasSC.setAmount(q[1], h2, 0);
        com.sc.util.ArmorGasSC.setAmount(q[3], h2, 0);
        boolean dry = !com.sc.item.ArmorLogicSC.emergency(q) && !com.sc.item.ArmorLogicSC.worksIn(q, com.sc.util.ArmorFeature.FLIGHT)
                && com.sc.item.ArmorLogicSC.missingGas(q, com.sc.util.ArmorFeature.FLIGHT) == h2
                && !com.sc.item.ArmorLogicSC.worksIn(q, com.sc.util.ArmorFeature.JUMP)
                && com.sc.item.ArmorLogicSC.worksIn(q, com.sc.util.ArmorFeature.NIGHT_VISION)
                && com.sc.item.ArmorLogicSC.worksIn(q, com.sc.util.ArmorFeature.STEP_ASSIST);
        com.sc.util.ArmorGasSC.setAmount(q[3], h2, 1);
        boolean wet = com.sc.item.ArmorLogicSC.worksIn(q, com.sc.util.ArmorFeature.FLIGHT)
                && com.sc.item.ArmorLogicSC.worksIn(q, com.sc.util.ArmorFeature.SPEED);       // the leggings use the suit's hydrogen
        // fractions add up: five 0.2 mB jumps are one whole mB
        com.sc.util.ArmorGasSC.setAmount(q[3], h2, 100);
        com.sc.util.ArmorGasSC.setAmount(q[1], h2, 0);
        for (int i = 0; i < 5; i++) {
            com.sc.util.ArmorGasSC.drainFraction(q, h2, com.sc.util.ArmorFeature.JUMP.gasUse());
        }
        int afterJumps = com.sc.util.ArmorGasSC.amountOf(q, h2);
        check(dry && wet && afterJumps == 99, "strict rules: Quantum without hydrogen - no flight / jump (night vision on krypton still works); "
                + "with hydrogen - flight; 5 jumps = 1 mB (" + dry + "/" + wet + "/" + afterJumps + ")");

        // no helium: emergency mode - all off but the HUD, iron-grade plating; helium back - all back
        for (ItemStack s : q) {
            com.sc.util.ArmorGasSC.setAmount(s, he, 0);
        }
        com.sc.item.ItemArmorSC qChest = (com.sc.item.ItemArmorSC) q[1].getItem();
        boolean em = com.sc.item.ArmorLogicSC.emergency(q)
                && !com.sc.item.ArmorLogicSC.worksIn(q, com.sc.util.ArmorFeature.FLIGHT)
                && !com.sc.item.ArmorLogicSC.worksIn(q, com.sc.util.ArmorFeature.NIGHT_VISION)
                && !com.sc.item.ArmorLogicSC.worksIn(q, com.sc.util.ArmorFeature.STEP_ASSIST)
                && com.sc.item.ArmorLogicSC.worksIn(q, com.sc.util.ArmorFeature.HUD)
                && qChest.protectionIn(q) == 6 && ((com.sc.item.ItemArmorSC) q[0].getItem()).protectionIn(q) == 2
                && ((com.sc.item.ItemArmorSC) q[2].getItem()).protectionIn(q) == 5 && ((com.sc.item.ItemArmorSC) q[3].getItem()).protectionIn(q) == 2;
        ItemStack[] noChest = {q[0], null, q[2], q[3]};
        em &= com.sc.item.ArmorLogicSC.emergency(noChest);                                  // no chestplate: no loop - emergency
        com.sc.util.ArmorGasSC.setAmount(q[1], he, 3000);
        boolean back = !com.sc.item.ArmorLogicSC.emergency(q) && com.sc.item.ArmorLogicSC.worksIn(q, com.sc.util.ArmorFeature.FLIGHT)
                && qChest.protectionIn(q) == com.sc.util.ArmorSuit.QUANTUM.material.getDamageReductionAmount(1);
        check(em && back, "strict rules: no helium (or no chestplate) - emergency mode: only the HUD, armour 2/6/5/2 like iron; helium back - all back ("
                + em + "/" + back + ")");

        // Exo: no krypton - no night vision
        ItemStack[] e = gasSuit(com.sc.util.ArmorSuit.EXO, true, true, true, true);
        fillSuit(e, 50);
        boolean nvOn = com.sc.item.ArmorLogicSC.worksIn(e, com.sc.util.ArmorFeature.NIGHT_VISION);
        com.sc.util.ArmorGasSC.setAmount(e[0], kr, 0);
        boolean nvOff = !com.sc.item.ArmorLogicSC.worksIn(e, com.sc.util.ArmorFeature.NIGHT_VISION)
                && com.sc.item.ArmorLogicSC.missingGas(e, com.sc.util.ArmorFeature.NIGHT_VISION) == kr
                && com.sc.item.ArmorLogicSC.worksIn(e, com.sc.util.ArmorFeature.SOLAR);
        check(nvOn && nvOff, "strict rules: Exo without krypton - night vision off (the solar film needs no gas)");

        // old worlds: a quarter of hydrogen once (Quantum chestplate 4000 + boots 2000 -> 1500), Nano none
        ItemStack[] old = gasSuit(com.sc.util.ArmorSuit.QUANTUM, true, true, true, true);
        int first = com.sc.util.ArmorGasSC.giveHydrogenStarter(old);
        int h2After = com.sc.util.ArmorGasSC.amountOf(old, h2);
        int second = com.sc.util.ArmorGasSC.giveHydrogenStarter(old);
        ItemStack[] oldNano = gasSuit(com.sc.util.ArmorSuit.NANO, true, true, true, true);
        check(first == 2 && h2After == 1500 && second == 0 && com.sc.util.ArmorGasSC.amountOf(old, h2) == 1500
                        && com.sc.util.ArmorGasSC.giveHydrogenStarter(oldNano) == 0,
                "strict rules: old-world hydrogen start 25% once (" + first + "/" + h2After + "/" + second + ")");
    }

    /**
     * The strict rules, fixes С-1..С-5: the dash needs a whole dash of hydrogen (client and server
     * agree); the soft landing costs 1 mB a damage point absorbed; the "hydrogen low" line; no set
     * bonus in emergency mode; the flight cut by the gases (soft descent) told from one switched off.
     */
    private static void armorStrictFixes() {
        com.sc.util.ArmorGasSC.Gas h2 = com.sc.util.ArmorGasSC.Gas.HYDROGEN, he = com.sc.util.ArmorGasSC.Gas.HELIUM;
        com.sc.util.ArmorFeature dash = com.sc.util.ArmorFeature.DASH;
        // С-4: the dash - under H2_DASH it's missing hydrogen (the K screen shows it off), at H2_DASH it works
        ItemStack[] e = gasSuit(com.sc.util.ArmorSuit.EXO, true, true, true, true);
        fillSuit(e, 50);
        com.sc.util.ArmorGasSC.setAmount(e[1], h2, 0);
        com.sc.util.ArmorGasSC.setAmount(e[3], h2, com.sc.util.ArmorGasSC.H2_DASH - 1);
        boolean shortDash = !com.sc.item.ArmorLogicSC.worksIn(e, dash) && com.sc.item.ArmorLogicSC.missingGas(e, dash) == h2
                && com.sc.item.ArmorLogicSC.worksIn(e, com.sc.util.ArmorFeature.SPEED);          // other hydrogen functions still on
        com.sc.util.ArmorGasSC.setAmount(e[3], h2, com.sc.util.ArmorGasSC.H2_DASH);
        boolean fullDash = com.sc.item.ArmorLogicSC.worksIn(e, dash) && com.sc.item.ArmorLogicSC.missingGas(e, dash) == null;
        check(shortDash && fullDash, "strict rules: the dash needs " + com.sc.util.ArmorGasSC.H2_DASH + " mB of hydrogen to show on ("
                + shortDash + "/" + fullDash + ")");

        // С-5: the soft landing - 1 mB a damage point absorbed, at least 1, none for nothing absorbed
        boolean landing = com.sc.util.ArmorGasSC.fallDampingGas(0) == 0 && com.sc.util.ArmorGasSC.fallDampingGas(1) == 1
                && com.sc.util.ArmorGasSC.fallDampingGas(4) == 4 && com.sc.util.ArmorGasSC.fallDampingGas(20) == 20
                && com.sc.util.ArmorFeature.FALL_DAMPING.gasUse() == 1F && com.sc.util.ArmorFeature.FALL_DAMPING.gasPerPoint()
                && !dash.gasPerPoint();
        check(landing, "strict rules: the soft landing costs 1 mB of hydrogen per damage point absorbed (min 1)");

        // С-1: "hydrogen low" under 30 s of flight (boosted 2 mB/s: under 60 mB; plain 1 mB/s: under 30 mB)
        boolean low = com.sc.util.ArmorGasSC.hydrogenLow(59, com.sc.util.ArmorGasSC.H2_FLIGHT_PER_SECOND)
                && !com.sc.util.ArmorGasSC.hydrogenLow(60, com.sc.util.ArmorGasSC.H2_FLIGHT_PER_SECOND)
                && com.sc.util.ArmorGasSC.hydrogenLow(29, com.sc.util.ArmorGasSC.H2_FLIGHT_BASE_PER_SECOND)
                && !com.sc.util.ArmorGasSC.hydrogenLow(30, com.sc.util.ArmorGasSC.H2_FLIGHT_BASE_PER_SECOND)
                && !com.sc.util.ArmorGasSC.hydrogenLow(0, 0F);
        check(low, "strict rules: the 'hydrogen low' warning comes under 30 s of flight");

        // С-1: the flight cut by the gases (soft descent) - not when it's switched off, not with hydrogen in
        ItemStack[] q = gasSuit(com.sc.util.ArmorSuit.QUANTUM, true, true, true, true);
        fillSuit(q, 50);
        boolean flying = !com.sc.item.ArmorLogicSC.flightCutByGas(q);
        com.sc.util.ArmorGasSC.setAmount(q[1], h2, 0);
        com.sc.util.ArmorGasSC.setAmount(q[3], h2, 0);
        boolean noH2 = com.sc.item.ArmorLogicSC.flightCutByGas(q);
        com.sc.item.ItemArmorSC.setEnabled(q[1], com.sc.util.ArmorFeature.FLIGHT, false);
        boolean switchedOff = !com.sc.item.ArmorLogicSC.flightCutByGas(q);
        com.sc.item.ItemArmorSC.setEnabled(q[1], com.sc.util.ArmorFeature.FLIGHT, true);
        com.sc.util.ArmorGasSC.setAmount(q[3], h2, 100);
        for (ItemStack s : q) {
            com.sc.util.ArmorGasSC.setAmount(s, he, 0);
        }
        boolean noHe = com.sc.item.ArmorLogicSC.flightCutByGas(q);                            // emergency mode cuts it too
        check(flying && noH2 && switchedOff && noHe, "strict rules: the flight cut by the gases (no hydrogen / no helium) gets the soft descent, "
                + "switched off - not (" + flying + "/" + noH2 + "/" + switchedOff + "/" + noHe + ")");

        // С-2: no set bonus in emergency mode (a full charged Quantum set: bonus with helium, none without)
        ItemStack[] b = gasSuit(com.sc.util.ArmorSuit.QUANTUM, true, true, true, true);
        fillSuit(b, 50);
        for (ItemStack s : b) {
            com.sc.item.ItemArmorSC.setCharge(s, 1000);
        }
        boolean withHe = com.sc.item.ArmorLogicSC.fullSetOf(b) == com.sc.util.ArmorSuit.QUANTUM
                && com.sc.item.ArmorLogicSC.bonusSetOf(b) == com.sc.util.ArmorSuit.QUANTUM;
        for (ItemStack s : b) {
            com.sc.util.ArmorGasSC.setAmount(s, he, 0);
        }
        boolean withoutHe = com.sc.item.ArmorLogicSC.fullSetOf(b) == com.sc.util.ArmorSuit.QUANTUM
                && com.sc.item.ArmorLogicSC.bonusSetOf(b) == null;
        ItemStack[] n = gasSuit(com.sc.util.ArmorSuit.NANO, true, true, true, true);
        for (ItemStack s : n) {
            com.sc.item.ItemArmorSC.setCharge(s, 1000);
        }
        boolean nano = com.sc.item.ArmorLogicSC.bonusSetOf(n) == com.sc.util.ArmorSuit.NANO;          // Nano: no emergency mode
        check(withHe && withoutHe && nano, "strict rules: emergency mode switches the set bonuses off (" + withHe + "/" + withoutHe + "/" + nano + ")");
    }

    /** Life support (docs/plan-armor-gases.md): tanks, the hybrid helium loop, the hard rules, the Cryo Tank chip, the old-world start. */
    private static void armorGases() {
        com.sc.util.ArmorGasSC.Gas he = com.sc.util.ArmorGasSC.Gas.HELIUM, o2 = com.sc.util.ArmorGasSC.Gas.OXYGEN,
                d = com.sc.util.ArmorGasSC.Gas.DEUTERIUM;
        com.sc.util.ArmorSuit nano = com.sc.util.ArmorSuit.NANO, quantum = com.sc.util.ArmorSuit.QUANTUM, exo = com.sc.util.ArmorSuit.EXO;
        ItemStack[] qFull = gasSuit(quantum, true, true, true, true), eFull = gasSuit(exo, true, true, true, true);
        ItemStack[] nFull = gasSuit(nano, true, true, true, true);
        check(com.sc.util.ArmorGasSC.capacityOf(qFull, he) == 9000 && com.sc.util.ArmorGasSC.capacityOf(eFull, he) == 18000
                        && com.sc.util.ArmorGasSC.capacity(qFull[1], he) == 6000 && com.sc.util.ArmorGasSC.capacityOf(nFull, he) == 2000
                        && com.sc.util.ArmorGasSC.capacityOf(nFull, o2) == 2000 && com.sc.util.ArmorGasSC.capacityOf(eFull, d) == 4000
                        && com.sc.util.ArmorGasSC.capacityOf(qFull, d) == 0,
                "gas tanks: helium Quantum 9000 / Exo 18000 (chest 6000 + radiators), Nano 2000 helium + 2000 oxygen, deuterium Exo only");

        // hybrid: radiators without a chestplate hold helium but cool nothing
        ItemStack[] noChest = gasSuit(quantum, true, false, true, true);
        for (ItemStack s : noChest) {
            if (s != null) {
                com.sc.util.ArmorGasSC.setAmount(s, he, 1000);
            }
        }
        check(com.sc.util.ArmorGasSC.amountOf(noChest, he) == 0 && com.sc.util.ArmorGasSC.coolingFactorOf(noChest) == 0F
                        && com.sc.util.ArmorGasSC.heliumCool(noChest, 100, false) == 0
                        && com.sc.util.ArmorGasSC.amount(noChest[0], he) == 1000,
                "helium without a chestplate: no loop - no cooling, the radiators keep their helium");
        check(com.sc.util.ArmorGasSC.fillOf(noChest, he, 100, true) == 0 && com.sc.util.ArmorGasSC.fillOf(noChest, o2, 100, true) == 100,
                "helium without a chestplate: the suit takes none in (oxygen still goes into the helmet)");

        // radiators: +15% each; the pump's rate grows with them
        ItemStack[] chestOnly = gasSuit(quantum, false, true, false, false);
        com.sc.util.ArmorGasSC.setAmount(chestOnly[1], he, 6000);
        com.sc.util.ArmorGasSC.setAmount(qFull[1], he, 6000);
        int alone = com.sc.util.ArmorGasSC.heliumCool(chestOnly, 100, false);
        int full = com.sc.util.ArmorGasSC.heliumCool(qFull, 100, false);
        check(Math.abs(com.sc.util.ArmorGasSC.coolingFactorOf(qFull) - 1.45F) < 1e-4 && com.sc.util.ArmorGasSC.coolingFactorOf(chestOnly) == 1F
                        && alone == 8 && full == 11 && com.sc.util.ArmorGasSC.heliumCool(chestOnly, 0, false) == 0,
                "helium cooling: Quantum pump 8 heat/s alone, x1.45 with three radiators (" + alone + "/" + full + ")");
        // the helium used: 1 mB per 20 heat (x cooling factor) - 400 heat from the chestplate alone costs 20 mB
        ItemStack[] use = gasSuit(exo, false, true, false, false);
        com.sc.util.ArmorGasSC.setAmount(use[1], he, 1000);
        int took = 0;
        for (int i = 0; i < 25; i++) {
            took += com.sc.util.ArmorGasSC.heliumCool(use, 16, false);
        }
        int left = com.sc.util.ArmorGasSC.amount(use[1], he);
        com.sc.item.ItemArmorSC.chipsTag(use[1]).setInteger(com.sc.util.ChipType.RECUPERATOR.name(), 1);
        for (int i = 0; i < 25; i++) {
            com.sc.util.ArmorGasSC.heliumCool(use, 16, false);
        }
        int leftRec = com.sc.util.ArmorGasSC.amount(use[1], he);
        check(took == 400 && Math.abs(left - 980) <= 1 && Math.abs(leftRec - (left - 14)) <= 1,
                "helium use: 400 heat = 20 mB; the Recuperator gives 30% back (" + took + ", " + left + ", " + leftRec + ")");

        // hard rules: the Exo shield / annihilation need helium; breathing needs oxygen
        for (ItemStack s : eFull) {
            com.sc.util.ArmorGasSC.setAmount(s, he, 0);
        }
        boolean shieldDry = !com.sc.item.ArmorLogicSC.heliumReady(eFull);
        int readyMin = com.sc.item.ArmorLogicSC.heliumReadyMin(com.sc.util.ArmorGasSC.capacityOf(eFull, he));
        com.sc.util.ArmorGasSC.setAmount(eFull[1], he, readyMin - 1);
        shieldDry &= !com.sc.item.ArmorLogicSC.heliumReady(eFull);
        com.sc.util.ArmorGasSC.setAmount(eFull[1], he, readyMin);
        boolean shieldWet = com.sc.item.ArmorLogicSC.heliumReady(eFull);
        check(readyMin == 180 && com.sc.item.ArmorLogicSC.heliumReadyMin(50) == 1,
                "helium ready: 1% of the loop (Exo 18000 -> 180 mB), never under 1 mB (" + readyMin + ")");
        ItemStack[] head = gasSuit(nano, true, false, false, false);
        boolean noAir = !com.sc.item.ArmorLogicSC.breathe(head);
        com.sc.util.ArmorGasSC.setAmount(head[0], o2, 2);
        boolean air = com.sc.item.ArmorLogicSC.breathe(head) && com.sc.util.ArmorGasSC.amount(head[0], o2) == 1;
        com.sc.item.ItemArmorSC.setEnabled(head[0], com.sc.util.ArmorFeature.AIR, false);
        boolean off = !com.sc.item.ArmorLogicSC.breathe(head) && com.sc.util.ArmorGasSC.amount(head[0], o2) == 1;
        check(shieldDry && shieldWet && noAir && air && off,
                "hard rules: no helium - no Exo shield / pulse; no oxygen (or breathing off) - no breath, 1 mB a breath");

        // fusion cell: deuterium and helium both, 2 mB of deuterium a second
        ItemStack[] cell = gasSuit(exo, false, true, false, false);
        com.sc.util.ArmorGasSC.setAmount(cell[1], d, 100);
        boolean dryCell = !com.sc.item.ArmorLogicSC.fusionStep(cell) && com.sc.util.ArmorGasSC.amount(cell[1], d) == 100;
        com.sc.util.ArmorGasSC.setAmount(cell[1], he, 1000);                 // over the 1% the loop needs (120 of 12000)
        boolean wetCell = com.sc.item.ArmorLogicSC.fusionStep(cell) && com.sc.util.ArmorGasSC.amount(cell[1], d) == 98;
        check(dryCell && wetCell, "fusion cell: off without helium, 2 mB deuterium a second with it");

        // the Cryo Tank chip: +50% tanks; without it (or the chestplate) the extra is out of reach, not erased
        ItemStack[] tank = gasSuit(quantum, true, true, false, false);
        com.sc.item.ItemArmorSC.chipsTag(tank[1]).setInteger(com.sc.util.ChipType.CRYO_TANK.name(), 1);
        com.sc.util.ArmorGasSC.applyCapacityBonus(tank);
        int bigger = com.sc.util.ArmorGasSC.capacity(tank[0], o2);
        com.sc.util.ArmorGasSC.setAmount(tank[0], o2, bigger);
        tank[1].getTagCompound().removeTag("ChipsSC");
        com.sc.util.ArmorGasSC.applyCapacityBonus(tank);
        boolean shrunk = com.sc.util.ArmorGasSC.capacity(tank[0], o2) == 4000 && com.sc.util.ArmorGasSC.amount(tank[0], o2) == 4000
                && tank[0].getTagCompound().getInteger("Gas_oxygen") == 6000
                && com.sc.util.ArmorGasSC.fill(tank[0], o2, 100, true) == 0 && com.sc.util.ArmorGasSC.drain(tank[0], o2, 99999, true) == 4000;
        com.sc.item.ItemArmorSC.chipsTag(tank[1]).setInteger(com.sc.util.ChipType.CRYO_TANK.name(), 1);
        com.sc.util.ArmorGasSC.applyCapacityBonus(tank);
        boolean back = com.sc.util.ArmorGasSC.amount(tank[0], o2) == 6000;
        // the chestplate off: the helmet's bonus goes, its gas stays in the NBT
        ItemStack[] noChestNow = {tank[0], null, null, null};
        com.sc.util.ArmorGasSC.applyCapacityBonus(noChestNow);
        boolean chestOff = com.sc.util.ArmorGasSC.amount(tank[0], o2) == 4000 && tank[0].getTagCompound().getInteger("Gas_oxygen") == 6000;
        // used while shrunk: what it writes back is at most the tank - the hidden extra can't be drawn on
        com.sc.util.ArmorGasSC.drain(tank[0], o2, 1, false);
        boolean noDupe = com.sc.util.ArmorGasSC.amount(tank[0], o2) == 3999 && tank[0].getTagCompound().getInteger("Gas_oxygen") == 3999;
        check(bigger == 6000 && shrunk && back && chestOff && noDupe,
                "Cryo Tank chip: helmet oxygen 4000 -> 6000; without the chip / chestplate 4000 shown, 6000 kept, back with the chip; no extra drawn (" + shrunk + "/" + back + "/" + chestOff + "/" + noDupe + ")");

        // the searchlight: moves on a 2-block shift at once, a 1-block one only after 8 ticks
        check(!com.sc.item.ArmorLogicSC.lightShouldMove(0, 64, 0, 0, 64, 0, 100)
                        && com.sc.item.ArmorLogicSC.lightShouldMove(0, 64, 0, 2, 64, 0, 0)
                        && !com.sc.item.ArmorLogicSC.lightShouldMove(0, 64, 0, 1, 65, 0, 4)
                        && com.sc.item.ArmorLogicSC.lightShouldMove(0, 64, 0, 1, 65, 0, 8)
                        && com.sc.block.BlockLightSC.LIGHT == 13,
                "searchlight: moves at 2 blocks, or a small shift after 8 ticks; light 13");

        // old worlds: a quarter of helium and oxygen, once per piece
        ItemStack[] old = gasSuit(quantum, true, true, true, true);
        int first = com.sc.util.ArmorGasSC.giveStarter(old);
        int heAfter = com.sc.util.ArmorGasSC.amountOf(old, he), o2After = com.sc.util.ArmorGasSC.amountOf(old, o2);
        int second = com.sc.util.ArmorGasSC.giveStarter(old);
        check(first == 4 && heAfter == 2250 && o2After == 1000 && second == 0
                        && com.sc.util.ArmorGasSC.amountOf(old, he) == 2250,
                "old-world start: 25% helium (2250 of 9000) and oxygen (1000 of 4000), only once (" + first + "/" + heAfter + "/" + o2After + "/" + second + ")");

        armorGasRules();
        armorStrictFixes();

        // chips: the new ones are life-support chips, metadata past the old 15
        check(com.sc.util.ChipType.CRYO_LOOP.isGasChip() && !com.sc.util.ChipType.UTILITY.isGasChip()
                        && com.sc.item.ItemArmorChipSC.typeAt(com.sc.item.ItemArmorChipSC.metaFor(com.sc.util.ChipType.RECUPERATOR, 3)) == com.sc.util.ChipType.RECUPERATOR
                        && com.sc.item.ItemArmorChipSC.gasChipValue(com.sc.util.ChipType.CRYO_TANK, 1) == 50,
                "life-support chips: appended after Utility, tiers kept, Cryo Tank I = +50%");
    }

    /** Energy blades: functions per tier, defaults and switches, the blade's own heat with its cool-down to half. */
    private static void bladeFunctions() {
        int[] count = new int[com.sc.util.BladeType.values().length];
        for (com.sc.util.BladeFeature f : com.sc.util.BladeFeature.values()) {
            for (com.sc.util.BladeType t : com.sc.util.BladeType.values()) {
                count[t.ordinal()] += f.availableIn(t) ? 1 : 0;
            }
        }
        check(count[0] == 4 && count[1] == 7 && count[2] == 10 && com.sc.util.BladeFeature.WAVE.isAction()
                        && !com.sc.util.BladeFeature.EXECUTE.availableIn(com.sc.util.BladeType.QUANTUM),
                "blade functions: Nano 4, Quantum 7, Exo 10 incl. looting (" + count[0] + "/" + count[1] + "/" + count[2] + ")");

        ItemStack blade = new ItemStack(ModItems.BLADES.get(com.sc.util.BladeType.NANO));
        boolean defaults = !com.sc.item.ItemBladeSC.isEnabled(blade, com.sc.util.BladeFeature.BLADE)
                && com.sc.item.ItemBladeSC.isEnabled(blade, com.sc.util.BladeFeature.CUTTING_EDGE)
                && !com.sc.item.ItemBladeSC.isEnabled(blade, com.sc.util.BladeFeature.ARMOR_PIERCE)   // not on a Nano blade
                && !com.sc.item.ItemBladeSC.isLit(blade);
        com.sc.item.ItemBladeSC.setEnabled(blade, com.sc.util.BladeFeature.BLADE, true);
        com.sc.item.ItemBladeSC.setEnabled(blade, com.sc.util.BladeFeature.CUTTING_EDGE, false);
        boolean flipped = com.sc.item.ItemBladeSC.isLit(blade)
                && !com.sc.item.ItemBladeSC.isEnabled(blade, com.sc.util.BladeFeature.CUTTING_EDGE);
        com.sc.item.ItemBladeSC.addHeat(blade, 100);                 // Nano: capacity 100
        boolean hot = com.sc.item.ItemBladeSC.overheated(blade) && !com.sc.item.ItemBladeSC.isLit(blade);
        com.sc.item.ItemBladeSC.addHeat(blade, -40);                 // 60%: still dark
        hot &= com.sc.item.ItemBladeSC.overheated(blade);
        com.sc.item.ItemBladeSC.addHeat(blade, -10);                 // 50%: lit again
        hot &= !com.sc.item.ItemBladeSC.overheated(blade) && com.sc.item.ItemBladeSC.isLit(blade)
                && com.sc.item.ItemBladeSC.heatPercent(blade) == 50;
        com.sc.item.ItemBladeSC.setCharge(blade, 150000);
        boolean charge = com.sc.item.ItemBladeSC.chargeOf(blade) == 100000
                && com.sc.item.ItemBladeSC.discharge(blade, 30000) == 30000 && com.sc.item.ItemBladeSC.charge(blade, 50000) == 30000;
        check(defaults && flipped && hot && charge,
                "blades: blade off / functions on by default, each switched on its own, overheat until cooled to half, charge capped");
    }

    /** Field generator: dome / cylinder geometry, default switches, the upkeep of switched-on protections, targets. */
    private static void fieldExtras() {
        java.util.List<int[]> one = new java.util.ArrayList<int[]>();
        one.add(new int[]{0, 64, 0});
        boolean dome = com.sc.tileentity.FieldShapeSC.contains(com.sc.energy.FieldMode.DOME, one, 8, 0.5, 70, 0.5)
                && !com.sc.tileentity.FieldShapeSC.contains(com.sc.energy.FieldMode.DOME, one, 8, 0.5, 60, 0.5);
        boolean cyl = com.sc.tileentity.FieldShapeSC.contains(com.sc.energy.FieldMode.CYLINDER, one, 8, 7.5, 71, 0.5)
                && !com.sc.tileentity.FieldShapeSC.contains(com.sc.energy.FieldMode.UNION, one, 8, 7.5, 71, 0.5)
                && !com.sc.tileentity.FieldShapeSC.contains(com.sc.energy.FieldMode.CYLINDER, one, 8, 0.5, 74, 0.5);
        com.sc.tileentity.TileEntityFieldGeneratorSC f = new com.sc.tileentity.TileEntityFieldGeneratorSC();
        boolean defaults = f.has(com.sc.tileentity.TileEntityFieldGeneratorSC.F_SHOW) && f.has(com.sc.tileentity.TileEntityFieldGeneratorSC.F_DAMAGE)
                && !f.has(com.sc.tileentity.TileEntityFieldGeneratorSC.F_PRIVATE) && f.extrasPerTick() == 0;
        f.toggle(com.sc.tileentity.TileEntityFieldGeneratorSC.F_NO_SPAWN);
        f.toggle(com.sc.tileentity.TileEntityFieldGeneratorSC.F_PRIVATE);
        boolean extras = f.extrasPerTick() == com.sc.tileentity.TileEntityFieldGeneratorSC.NO_SPAWN_EU + com.sc.tileentity.TileEntityFieldGeneratorSC.PRIVATE_EU;
        boolean access = f.addAccess("Steve") && !f.addAccess("steve") && f.getAccess().size() == 1 && f.removeAccess("STEVE");
        int base = f.getMaxEnergyStored();
        com.sc.item.ItemUpgradeSC up = (com.sc.item.ItemUpgradeSC) com.sc.init.ModItems.upgrade;
        ItemStack two = up.stackOf(com.sc.machine.UpgradeType.ENERGY_STORAGE);
        two.stackSize = 2;
        f.setInventorySlotContents(0, two);
        boolean plus = f.getMaxEnergyStored() == base + 2 * com.sc.machine.UpgradeType.STORAGE_PER_UPGRADE;
        ItemStack many = up.stackOf(com.sc.machine.UpgradeType.ENERGY_STORAGE);
        many.stackSize = 40;
        f.setInventorySlotContents(1, many);
        boolean capped = f.getMaxEnergyStored() == base + com.sc.machine.UpgradeType.MAX_EFFECTIVE * com.sc.machine.UpgradeType.STORAGE_PER_UPGRADE;
        boolean hv = f.inputTier() == com.sc.energy.Tier.HV;
        f.setInventorySlotContents(2, up.stackOf(com.sc.machine.UpgradeType.TRANSFORMER));
        boolean ev = hv && f.inputTier() == com.sc.energy.Tier.EV;
        f.setInventorySlotContents(3, up.stackOf(com.sc.machine.UpgradeType.UNIVERSAL_TRANSFORMER));
        ev &= f.inputTier() == com.sc.energy.Tier.max();
        f.setInventorySlotContents(3, null);
        boolean only = !f.isItemValidForSlot(3, up.stackOf(com.sc.machine.UpgradeType.OVERCLOCKER))
                && f.isItemValidForSlot(3, two) && f.getAccessibleSlotsFromSide(1).length == 0;
        check(dome && cyl && defaults && extras && access && plus && capped && ev && only,
                "field generator: dome / cylinder shapes, default switches, protections' upkeep, access list, storage upgrades (+10k each, 16 max), transformer HV -> EV, universal -> any, nothing else");
    }

    /** Drills: each tier has everything the one below has, defaults, fortune III / V, small batteries, pad tier rule. */
    private static void drills() {
        int[] count = new int[com.sc.util.DrillType.values().length];
        boolean inherits = true;
        for (com.sc.util.DrillFeature f : com.sc.util.DrillFeature.values()) {
            for (com.sc.util.DrillType t : com.sc.util.DrillType.values()) {
                count[t.ordinal()] += f.availableIn(t) ? 1 : 0;
            }
            inherits &= !f.availableIn(com.sc.util.DrillType.QUANTUM) || f.availableIn(com.sc.util.DrillType.EXO);
        }
        ItemStack exo = new ItemStack(ModItems.DRILLS.get(com.sc.util.DrillType.EXO));
        ItemStack nano = new ItemStack(ModItems.DRILLS.get(com.sc.util.DrillType.NANO));
        boolean defaults = com.sc.item.ItemDrillSC.isEnabled(exo, com.sc.util.DrillFeature.FORTUNE)
                && !com.sc.item.ItemDrillSC.isEnabled(exo, com.sc.util.DrillFeature.AREA_3X3)
                && !com.sc.item.ItemDrillSC.isEnabled(nano, com.sc.util.DrillFeature.FORTUNE)       // not on the Nano drill
                && com.sc.item.ItemDrillSC.isEnabled(nano, com.sc.util.DrillFeature.MAGNET);
        com.sc.item.ItemDrillSC.setEnabled(exo, com.sc.util.DrillFeature.AREA_5X5, true);
        defaults &= com.sc.item.ItemDrillSC.isEnabled(exo, com.sc.util.DrillFeature.AREA_5X5);
        com.sc.item.ItemDrillSC.setCharge(nano, 999999);
        com.sc.tileentity.TileEntityChargePadSC pad = new com.sc.tileentity.TileEntityChargePadSC();
        pad.setStorageTier(com.sc.energy.Tier.MV);
        ItemStack quantum = new ItemStack(ModItems.DRILLS.get(com.sc.util.DrillType.QUANTUM));
        boolean charge = com.sc.item.ItemDrillSC.chargeOf(nano) == 10000 && pad.chargeItem(quantum, 500) == 0
                && pad.chargeItem(new ItemStack(ModItems.DRILLS.get(com.sc.util.DrillType.NANO)), 500) == 500;
        check(count[0] == 3 && count[1] == 7 && count[2] == 12 && inherits && defaults && charge
                        && com.sc.util.DrillType.EXO.fortune == 5 && com.sc.util.DrillType.QUANTUM.fortune == 3,
                "drills: Nano 3 / Quantum 7 / Exo all 12 functions (" + count[0] + "/" + count[1] + "/" + count[2]
                        + "), fortune III / V, battery capped, MV pad charges the Nano drill only");
    }

    /** Charge pad: items of its tier or lower only (Nano from MV, Quantum from HV), blades and weapons too. */
    private static void chargePad() {
        com.sc.tileentity.TileEntityChargePadSC pad = new com.sc.tileentity.TileEntityChargePadSC();
        pad.setStorageTier(com.sc.energy.Tier.MV);
        ItemStack nano = new ItemStack(ModItems.ARMOR.get(com.sc.util.ArmorSuit.NANO)[1]);
        ItemStack quantum = new ItemStack(ModItems.ARMOR.get(com.sc.util.ArmorSuit.QUANTUM)[1]);
        ItemStack nanoBlade = new ItemStack(ModItems.BLADES.get(com.sc.util.BladeType.NANO));
        ItemStack exoBlade = new ItemStack(ModItems.BLADES.get(com.sc.util.BladeType.EXO));
        ItemStack ionCutter = new ItemStack(ModItems.WEAPONS.get(com.sc.util.WeaponType.ION_CUTTER));
        boolean ok = pad.chargeItem(nano, 640) == 640 && com.sc.item.ItemArmorSC.chargeOf(nano) == 640
                && pad.chargeItem(quantum, 640) == 0
                && pad.chargeItem(nanoBlade, 640) == 640 && pad.chargeItem(exoBlade, 640) == 0
                && pad.chargeItem(ionCutter, 640) == 640
                && pad.chargeItem(new ItemStack(net.minecraft.init.Items.iron_sword), 640) == 0;
        check(ok, "charge pad MV: Nano armour / blade and LV-MV weapons charge, Quantum / Exo and plain items don't");
    }

    /** Wireless energy: range and loss by tier, one sending tick over 57 blocks, the crystal's halves. */
    private static void wireless() {
        com.sc.tileentity.TileEntityWirelessSC tx = new com.sc.tileentity.TileEntityWirelessSC();
        com.sc.tileentity.TileEntityWirelessSC rx = new com.sc.tileentity.TileEntityWirelessSC();
        tx.setup(com.sc.tileentity.TileEntityWirelessSC.TRANSMITTER, com.sc.energy.Tier.HV);
        rx.setup(com.sc.tileentity.TileEntityWirelessSC.RECEIVER, com.sc.energy.Tier.HV);
        tx.setEnergyStoredClient(10000);
        int took = tx.sendForTest(rx, 57);
        boolean ok = com.sc.tileentity.TileEntityWirelessSC.range(com.sc.energy.Tier.HV) == 64
                && com.sc.tileentity.TileEntityWirelessSC.range(com.sc.energy.Tier.XV) == Integer.MAX_VALUE
                && com.sc.tileentity.TileEntityWirelessSC.lossPct(57, com.sc.energy.Tier.HV) == 7
                && com.sc.tileentity.TileEntityWirelessSC.lossPct(100000, com.sc.energy.Tier.XV) == 50
                && took == 512 && rx.getEnergyStored() == 476 && tx.getEnergyStored() == 10000 - 512
                && tx.isEnergySink() && !tx.isEnergySource() && rx.isEnergySource() && !rx.isEnergySink();
        check(ok, "wireless: HV reaches 64, 7% lost over 57 blocks (512 sent, 476 arrive), roles right (took " + took + ")");
        ItemStack a1 = com.sc.item.ItemEntangledCrystalSC.half(ModItems.entangledCrystal, 12345L, 1);
        ItemStack a2 = com.sc.item.ItemEntangledCrystalSC.half(ModItems.entangledCrystal, 12345L, 2);
        com.sc.item.ItemEntangledCrystalSC.wear(a1, 1000);
        com.sc.tileentity.TileEntityWirelessSC q = new com.sc.tileentity.TileEntityWirelessSC();
        q.setup(com.sc.tileentity.TileEntityWirelessSC.QUANTUM, com.sc.energy.Tier.XV);
        ok = com.sc.item.ItemEntangledCrystalSC.pairOf(a1) == 12345L && com.sc.item.ItemEntangledCrystalSC.pairOf(a2) == 12345L
                && com.sc.item.ItemEntangledCrystalSC.halfOf(a1) == 1 && com.sc.item.ItemEntangledCrystalSC.halfOf(a2) == 2
                && com.sc.item.ItemEntangledCrystalSC.lifeOf(a1) == com.sc.item.ItemEntangledCrystalSC.LIFE_MAX - 1000
                && q.isItemValidForSlot(com.sc.tileentity.TileEntityWirelessSC.SLOT_CRYSTAL, a1)
                && !q.isItemValidForSlot(com.sc.tileentity.TileEntityWirelessSC.SLOT_CRYSTAL, new ItemStack(ModItems.entangledCrystal))
                && q.getTier() == com.sc.energy.Tier.XV && q.isEnergySink();
        check(ok, "quantum pair: halves share the pair, wear, only a half fits the slot, the translator is XV and gives by default");
        com.sc.tileentity.TileEntityWirelessSC off = new com.sc.tileentity.TileEntityWirelessSC();
        off.setup(com.sc.tileentity.TileEntityWirelessSC.TRANSMITTER, com.sc.energy.Tier.HV);
        int wantOn = off.demandedEnergy();
        off.setPowerOn(false);
        com.sc.tileentity.TileEntityWirelessSC rxOff = new com.sc.tileentity.TileEntityWirelessSC();
        rxOff.setup(com.sc.tileentity.TileEntityWirelessSC.RECEIVER, com.sc.energy.Tier.HV);
        rxOff.setEnergyStoredClient(1000);
        int giveOn = rxOff.offerableEnergy();
        rxOff.setPowerOn(false);
        ok = wantOn > 0 && off.demandedEnergy() == 0 && off.receiveEnergy(net.minecraftforge.common.util.ForgeDirection.UNKNOWN, 512, 512, true) == 0
                && giveOn > 0 && rxOff.offerableEnergy() == 0;
        check(ok, "wireless: switched off, a transmitter takes nothing from the grid and a receiver gives nothing");
    }

    /** Radiation: what blocks let through, sources, the lead casing, the suits' shield, the field's switch, the shower. */
    private static void radiation() {
        boolean ok = com.sc.radiation.RadiationSC.blockPasses(com.sc.init.ModBlocks.leadBlock) == 0.02F
                && com.sc.radiation.RadiationSC.blockPasses(com.sc.init.ModBlocks.leadGlass) == 0.05F
                && com.sc.radiation.RadiationSC.blockPasses(net.minecraft.init.Blocks.stone) == 0.7F
                && com.sc.radiation.RadiationSC.blockPasses(net.minecraft.init.Blocks.water) == 0.6F
                && com.sc.radiation.RadiationSC.blockPasses(net.minecraft.init.Blocks.air) == 1F
                && com.sc.radiation.RadiationSC.blockPasses(net.minecraft.init.Blocks.glass) == 1F
                && Math.abs(com.sc.radiation.RadiationSC.atDistance(6F, 12, 6) - 3F) < 1e-4
                && com.sc.radiation.RadiationSC.atDistance(10F, 16, 16) == 0F
                && "4.5".equals(com.sc.radiation.RadiationSC.fmt(4.5F)) && "0.0".equals(com.sc.radiation.RadiationSC.fmt(0F));
        check(ok, "radiation: lead 2%, lead glass 5%, water 60%, stone 70%, glass and air all; linear fall to the edge; 4.5 printed");
        com.sc.energy.GeneratorType[] hot = {com.sc.energy.GeneratorType.RTG, com.sc.energy.GeneratorType.FUSION_REACTOR,
                com.sc.energy.GeneratorType.TOKAMAK, com.sc.energy.GeneratorType.PLASMA_REACTOR, com.sc.energy.GeneratorType.EXO_REACTOR,
                com.sc.energy.GeneratorType.TOKAMAK_XV};
        float[] levels = {1F, 4F, 6F, 8F, 10F, 12F};
        int[] reach = {4, 8, 12, 14, 16, 20};
        ok = true;
        for (int i = 0; i < hot.length; i++) {
            ok &= com.sc.tileentity.TileEntityGeneratorSC.radiationBase(hot[i]) == levels[i]
                    && com.sc.tileentity.TileEntityGeneratorSC.radiationRadius(hot[i]) == reach[i];
        }
        ok &= com.sc.tileentity.TileEntityGeneratorSC.radiationBase(com.sc.energy.GeneratorType.SOLAR_SI) == 0F
                && com.sc.tileentity.TileEntityGeneratorSC.radiationBase(com.sc.energy.GeneratorType.FUEL_CELL) == 0F;
        ItemStack casing = ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.RAD_SHIELDING);
        com.sc.tileentity.TileEntityGeneratorSC rtg = new com.sc.tileentity.TileEntityGeneratorSC();
        rtg.setGeneratorType(com.sc.energy.GeneratorType.RTG);
        rtg.setInventorySlotContents(com.sc.tileentity.TileEntityGeneratorSC.SLOT_FUEL, new ItemStack(ModItems.isotopeCapsule));
        rtg.setInventorySlotContents(com.sc.tileentity.TileEntityGeneratorSC.SLOT_BLANKET, new ItemStack(ModItems.isotopeCapsule));
        float open = rtg.radiationLevel();
        boolean fits = rtg.isItemValidForSlot(com.sc.tileentity.TileEntityGeneratorSC.FIRST_UPGRADE_SLOT, casing);
        rtg.setInventorySlotContents(com.sc.tileentity.TileEntityGeneratorSC.FIRST_UPGRADE_SLOT, casing);
        com.sc.tileentity.TileEntityGeneratorSC solar = new com.sc.tileentity.TileEntityGeneratorSC();
        solar.setGeneratorType(com.sc.energy.GeneratorType.SOLAR_SI);
        com.sc.tileentity.TileEntityGeneratorSC exo = new com.sc.tileentity.TileEntityGeneratorSC();
        exo.setGeneratorType(com.sc.energy.GeneratorType.EXO_REACTOR);
        int exoRated = exo.ratedOutput();
        exo.setInventorySlotContents(com.sc.tileentity.TileEntityGeneratorSC.FIRST_UPGRADE_SLOT, casing.copy());
        ok &= open == 2F && fits && rtg.isShielded() && rtg.radiationLevel() == 0F
                && !solar.isItemValidForSlot(com.sc.tileentity.TileEntityGeneratorSC.FIRST_UPGRADE_SLOT, casing)
                && exo.ratedOutput() == (int) Math.round(exoRated * 0.9)
                && com.sc.machine.UpgradeType.RAD_SHIELDING.forGenerators() && com.sc.machine.UpgradeType.RAD_SHIELDING.generatorOnly();
        check(ok, "radiation sources: RTG 1 a capsule (2 here) to 4 bl., reactors 4/6/8/10 to 8-16 bl.; the lead casing fits only them,"
                + " stops it and takes 10% of the output (open " + open + ")");
        ok = com.sc.util.ArmorFeature.RAD_SHIELD.availableIn(com.sc.util.ArmorSuit.QUANTUM, 1)
                && com.sc.util.ArmorFeature.RAD_SHIELD.availableIn(com.sc.util.ArmorSuit.EXO, 1)
                && !com.sc.util.ArmorFeature.RAD_SHIELD.availableIn(com.sc.util.ArmorSuit.NANO, 1)
                && com.sc.util.ArmorFeature.RAD_SHIELD.ordinal() < 31
                && com.sc.util.ArmorFeature.RAD_QUANTUM_PCT * 10 / 100F * com.sc.util.ArmorFeature.RAD_QUANTUM_EU == 150F
                && com.sc.util.ArmorFeature.RAD_EXO_PCT * 10 / 100F * com.sc.util.ArmorFeature.RAD_EXO_EU == 250F
                && com.sc.util.ArmorFeature.RAD_QUANTUM_PCT * 10 / 100F * com.sc.util.ArmorFeature.RAD_HEAT_PER_LEVEL < com.sc.util.ArmorSuit.QUANTUM.heatDissipation
                        + com.sc.util.ArmorGasSC.HELIUM_PUMP[com.sc.util.ArmorSuit.QUANTUM.ordinal()]   // passive + the helium loop
                && com.sc.tileentity.TileEntityFieldGeneratorSC.F_RADIATION == 1 << 16
                && com.sc.inventory.ContainerFieldGeneratorSC.BTN_FLAG_BASE + com.sc.inventory.ContainerFieldGeneratorSC.FLAG_COUNT
                        <= com.sc.inventory.ContainerFieldGeneratorSC.BTN_RESERVE_PLUS;
        check(ok, "radiation shield: Quantum / Exo chestplate only, 150 / 250 EU/s at level 10, Quantum's heat under its cooling (with helium);"
                + " the field's switch is bit 16 and its button id is free");
        com.sc.tileentity.TileEntityShowerSC shower = new com.sc.tileentity.TileEntityShowerSC();
        int water = shower.fill(net.minecraftforge.common.util.ForgeDirection.UP,
                new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, 10000), true);
        int lava = shower.fill(net.minecraftforge.common.util.ForgeDirection.UP,
                new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.LAVA, 1000), true);
        ok = water == com.sc.tileentity.TileEntityShowerSC.TANK && lava == 0 && shower.getTier() == com.sc.energy.Tier.MV
                && shower.isEnergySink() && shower.drain(net.minecraftforge.common.util.ForgeDirection.UP, 1000, true) == null
                && shower.isItemValidForSlot(0, new ItemStack(ModItems.battery)) && !shower.isItemValidForSlot(0, new ItemStack(ModItems.dosimeter));
        check(ok, "shower: MV, takes only water (8000 mB), nothing drains out, a battery fits its slot (water " + water + ")");
        shower.setEnergyStoredClient(5000);
        net.minecraft.nbt.NBTTagCompound kept = shower.writeToItem();
        com.sc.tileentity.TileEntityShowerSC placed = new com.sc.tileentity.TileEntityShowerSC();
        placed.readFromItem(kept);
        ok = placed.getTank().getFluidAmount() == com.sc.tileentity.TileEntityShowerSC.TANK && placed.getEnergyStored() == 5000;
        check(ok, "shower: the item keeps its water and energy (" + placed.getTank().getFluidAmount() + " mB, " + placed.getEnergyStored() + " EU)");
    }

    /** Water electrolysis (H2 + O2) and deuterium through heavy water: the right recipe wins, no energy loop with the fuel cell. */
    private static void electrolysisAndHeavyWater() {
        com.sc.machine.MachineType el = com.sc.machine.MachineType.CHLOR_ALKALI_ELECTROLYZER, chem = com.sc.machine.MachineType.CHEM_REACTOR;
        net.minecraftforge.fluids.FluidStack water = new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, 4000);
        ItemStack halite = new ItemStack(com.sc.init.ModBlocks.oreSC, 1, com.sc.util.OreEntry.HALITE.meta());
        net.minecraftforge.fluids.FluidStack lye = new net.minecraftforge.fluids.FluidStack(com.sc.init.ModFluids.naoh, 100);
        net.minecraftforge.fluids.FluidStack h2 = new net.minecraftforge.fluids.FluidStack(com.sc.init.ModFluids.hydrogen, 100);
        com.sc.machine.MachineRecipe w = com.sc.machine.RecipeRegistry.findMatch(el, new ItemStack[3], water, lye);
        com.sc.machine.MachineRecipe plain = com.sc.machine.RecipeRegistry.findMatch(el, new ItemStack[3], water, null);
        com.sc.machine.MachineRecipe chemPlain = com.sc.machine.RecipeRegistry.findMatch(chem, new ItemStack[3], water, null);
        com.sc.machine.MachineRecipe brine = com.sc.machine.RecipeRegistry.findMatch(el, new ItemStack[]{halite, null, null}, water, null);
        com.sc.machine.MachineRecipe hw = com.sc.machine.RecipeRegistry.findMatch(chem, new ItemStack[3], water, h2);
        com.sc.machine.MachineRecipe d = com.sc.machine.RecipeRegistry.findMatch(el, new ItemStack[3],
                new net.minecraftforge.fluids.FluidStack(com.sc.init.ModFluids.heavyWater, 1000), null);
        long cost = (long) el.euPerTick * (w == null ? 0 : w.ticks);
        long back = w == null ? 0 : (long) w.fluidOutputA.amount / 4 * com.sc.energy.GeneratorType.FUEL_CELL.euPerTick;
        boolean ok = w != null && w.fluidOutputA.getFluid() == com.sc.init.ModFluids.hydrogen && w.fluidOutputB.getFluid() == com.sc.init.ModFluids.oxygen
                && brine != null && brine.fluidOutputA.getFluid() == com.sc.init.ModFluids.naoh
                && hw != null && hw.fluidOutputA.getFluid() == com.sc.init.ModFluids.heavyWater
                && d != null && d.fluidOutputA.getFluid() == com.sc.init.ModFluids.deuterium
                && cost > back && com.sc.item.ItemFluidDropSC.names().contains("heavywater") && plain == null && chemPlain == null;
        check(ok, "electrolysis: water + lye -> H2 + O2, plain water starts nothing (" + cost + " EU, a fuel cell gets " + back + " back), brine still NaOH + Cl2,"
                + " water -> heavy water -> deuterium + O2");
    }

    /**
     * Blocks of metal: 22 (lead has its own), each its metal's block in the ore dictionary, unused metas clamped.
     * The recipes are made in postInit (they depend on other mods' blocks) - the world test checks them.
     */
    private static void metalBlocks() {
        int ok = 0;
        for (com.sc.util.Material m : com.sc.block.BlockMetalSC.METALS) {
            ItemStack b = com.sc.block.BlockMetalSC.stackOf(m, 1);
            for (int id : net.minecraftforge.oredict.OreDictionary.getOreIDs(b)) {
                ok += net.minecraftforge.oredict.OreDictionary.getOreName(id).equals("block" + m.oreDictName) ? 1 : 0;
            }
        }
        boolean clamp = com.sc.init.ModBlocks.metalBlock2.damageDropped(9) == 0 && com.sc.init.ModBlocks.metalBlock2.count() == 6;
        check(ok == 22 && com.sc.block.BlockMetalSC.stackOf(com.sc.util.Material.LEAD, 1) == null && clamp,
                "metal blocks: 22 in the ore dictionary as their blocks, lead apart, unused metas clamped (" + ok + ")");
    }

    /** The electric / induction furnace: furnace recipes, one / two streams, speed with heat. */
    /** «Есть обновление»: version order by number and version.json parsing (no network here). */
    private static void updateCheck() {
        com.sc.util.UpdateCheckSC.Info i = com.sc.util.UpdateCheckSC.parse("{\"version\":\"0.1.11-beta\",\"channel\":\"beta\","
                + "\"url\":\"https://example/r\",\"page\":\"https://example/c\",\"changes\":{\"ru\":[\"а\",\"б\",\"в\",\"г\"],\"en\":[\"a\"]}}");
        check(com.sc.util.UpdateCheckSC.compare("0.1.10-beta", "0.1.9") > 0 && com.sc.util.UpdateCheckSC.compare("v0.1.9", "0.1.9-beta") == 0
                        && com.sc.util.UpdateCheckSC.compare("0.1.9", "0.2.0") < 0 && com.sc.util.UpdateCheckSC.compare("x", "0.0.0") == 0
                        && i != null && "0.1.11-beta".equals(i.version) && i.ru.length == 3 && i.en.length == 1 && "https://example/r".equals(i.url)
                        && com.sc.util.UpdateCheckSC.parse("not json") == null && com.sc.util.UpdateCheckSC.parse("{\"x\":1}") == null
                        && com.sc.util.UpdateCheckSC.message(i).getUnformattedText().contains("[Silicon Age]"),
                "update check: versions compared by number (0.1.10 > 0.1.9), version.json parsed, bad files ignored");
    }

    /** No crafting recipe of the mod is one shared ingredient alone (a copper ingot alone was other mods' nuggets). */
    private static void loneIngredientRecipes() {
        StringBuilder bad = new StringBuilder();
        for (Object o : net.minecraft.item.crafting.CraftingManager.getInstance().getRecipeList()) {
            String s = RecipeConflictsSC.loneShared((net.minecraft.item.crafting.IRecipe) o);
            if (s != null) {
                bad.append(bad.length() > 0 ? "; " : "").append(s);
            }
        }
        check(bad.length() == 0, "crafting: no recipe of one shared ingredient alone" + (bad.length() > 0 ? " - " + bad : ""));
        // a pack that unifies lead (UniDict) crafts another mod's block: the reactor shells go by "blockLead"
        // every block of metal is pressed from 9 ingots in the Rolling Machine (UniDict leaves machine recipes alone);
        // a mold in the slot keeps its own recipe
        boolean pressed = true;
        for (Material m : com.sc.block.BlockMetalSC.METALS) {
            ItemStack nine = ModItems.ingot.stackOf(m);
            nine.stackSize = 9;
            MachineRecipe r = RecipeRegistry.findMatch(MachineType.ROLLING_MACHINE, new ItemStack[]{nine}, null, null);
            pressed &= r != null && r.outputs[0].isItemEqual(com.sc.block.BlockMetalSC.stackOf(m, 1))
                    && com.sc.block.BlockMetalSC.isBlockOf(m.oreDictName, net.minecraft.block.Block.getBlockFromItem(r.outputs[0].getItem()),
                    r.outputs[0].getItemDamage());
        }
        ItemStack lead9 = ModItems.ingot.stackOf(Material.LEAD), al9 = ModItems.ingot.stackOf(Material.ALUMINIUM);
        lead9.stackSize = 9;
        al9.stackSize = 9;
        MachineRecipe leadR = RecipeRegistry.findMatch(MachineType.ROLLING_MACHINE, new ItemStack[]{lead9}, null, null);
        MachineRecipe foil = RecipeRegistry.findMatch(MachineType.ROLLING_MACHINE,
                new ItemStack[]{al9, new ItemStack(ModItems.TOOLS.get(com.sc.util.SCToolType.MOLD_PLATE))}, null, null);
        check(pressed && leadR != null && leadR.outputs[0].getItem() == net.minecraft.item.Item.getItemFromBlock(com.sc.init.ModBlocks.leadBlock)
                        && foil != null && foil.outputs[0].getItem() == ModItems.alFoil,
                "blocks of metal: pressed from 9 ingots in the Rolling Machine, a mold still wins");
        check(com.sc.radiation.RadiationSC.isLeadBlock(com.sc.init.ModBlocks.leadBlock, 0)
                        && !com.sc.radiation.RadiationSC.isLeadBlock(net.minecraft.init.Blocks.iron_block, 0),
                "crafting: any block of lead (\"blockLead\") counts as lead - ours and other mods'");
    }

    /** Fixes after the post-0.1.8 bug check (Б, К, С, Р). */
    private static void fixes20261007() {
        // Б-1: a crumb of stone costs more SM in the black hole than its clot gives back
        float smPerStoneCrumb = (float) com.sc.item.ItemSingularCrumbSC.CRUMB_BLOCKS * com.sc.item.ItemSingularCrumbSC.STONE_DIV
                / com.sc.util.DrillFeature.HOLE_BLOCKS_PER_MB;
        float smPerCrumbBack = Math.max((float) com.sc.item.ItemSingularClotSC.SM_PER_CLOT / 9F, com.sc.util.SingularStationMath.CRUMB_SM);
        int[] st = com.sc.util.DrillZoneSC.accrue(5, 20, com.sc.item.ItemSingularCrumbSC.STONE_DIV);   // 25 stone: 3 units, 1 kept
        check(smPerStoneCrumb > smPerCrumbBack && st[0] == 3 && st[1] == 1
                        && com.sc.item.DrillLogicSC.twoPart(net.minecraft.init.Blocks.piston_head)
                        && com.sc.item.DrillLogicSC.twoPart(net.minecraft.init.Blocks.piston_extension)
                        && com.sc.item.DrillLogicSC.twoPart(net.minecraft.init.Blocks.sticky_piston)
                        && !com.sc.item.DrillLogicSC.twoPart(net.minecraft.init.Blocks.stone),
                "fixes 2026-10-07: stone crumbs cost more SM than they give back; pistons left by the black hole");
        // С-1: the K menu's form buttons - several steps in one message, at most a lap
        int forms = com.sc.util.BladeForm.values().length;
        check(com.sc.handler.ArmorNetSC.formDelta(3) == 3 && com.sc.handler.ArmorNetSC.formDelta(-2) == -2
                        && com.sc.handler.ArmorNetSC.formDelta(100) == forms && com.sc.handler.ArmorNetSC.formDelta(-100) == -forms,
                "fixes 2026-10-07: blade form message carries the K menu's steps");
        // Р-1 / Р-2: diamond dust isn't compressor mass; the Crusher takes diamonds and quartz only by hand
        TileEntityMachineSC crusher = new TileEntityMachineSC();
        crusher.setMachineType(MachineType.CRUSHER);
        ItemStack dia = new ItemStack(net.minecraft.init.Items.diamond), qz = new ItemStack(net.minecraft.init.Items.quartz, 2);
        check(TileEntityMachineSC.matterMass(ModItems.dust.stackOf(Material.DIAMOND)) == 0
                        && TileEntityMachineSC.matterMass(ModItems.dust.stackOf(Material.IRON)) > 0
                        && !crusher.canInsertItem(0, dia, 1) && !crusher.canInsertItem(0, qz, 1)
                        && crusher.isItemValidForSlot(0, dia) && crusher.isItemValidForSlot(0, qz)
                        && crusher.canInsertItem(0, new ItemStack(net.minecraft.init.Blocks.iron_ore), 1),
                "fixes 2026-10-07: diamond dust kept out of the compressor, Crusher takes diamonds / quartz by hand only");
    }

    /** Vanilla ores in the mod's machines: iron/gold pipeline, gem ores, quartz, diamond dust, steel from dust. */
    private static void vanillaOres() {
        net.minecraft.item.crafting.FurnaceRecipes fr = net.minecraft.item.crafting.FurnaceRecipes.smelting();
        FluidStack water = new FluidStack(FluidRegistry.WATER, 1000);
        boolean chain = true;
        for (Material m : new Material[]{Material.IRON, Material.GOLD}) {
            ItemStack ore = new ItemStack(m == Material.IRON ? net.minecraft.init.Blocks.iron_ore : net.minecraft.init.Blocks.gold_ore);
            net.minecraft.item.Item ingot = m == Material.IRON ? net.minecraft.init.Items.iron_ingot : net.minecraft.init.Items.gold_ingot;
            MachineRecipe crush = RecipeRegistry.findMatch(MachineType.CRUSHER, new ItemStack[]{ore}, null, null);
            MachineRecipe wash = RecipeRegistry.findMatch(MachineType.ORE_WASHER, new ItemStack[]{ModItems.crushedOre.stackOf(m)}, water, null);
            MachineRecipe spin = RecipeRegistry.findMatch(MachineType.CENTRIFUGE, new ItemStack[]{ModItems.purifiedCrushedOre.stackOf(m)}, null, null);
            chain &= crush != null && crush.outputs[0].isItemEqual(ModItems.crushedOre.stackOf(m)) && crush.outputs[0].stackSize == 2
                    && wash != null && wash.outputs[0].isItemEqual(ModItems.purifiedCrushedOre.stackOf(m))
                    && spin != null && spin.outputs[0].isItemEqual(ModItems.dust.stackOf(m))
                    && (m == Material.IRON ? spin.byproducts.length == 0
                        : spin.byproducts.length == 1 && spin.byproducts[0].isItemEqual(ModItems.dustTiny.stackOf(Material.SILVER))
                          && Math.abs(spin.byproductChances[0] - 0.10f) < 1e-6)
                    && fr.getSmeltingResult(ModItems.dust.stackOf(m)) != null && fr.getSmeltingResult(ModItems.dust.stackOf(m)).getItem() == ingot
                    && fr.getSmeltingResult(ModItems.crushedOre.stackOf(m)).getItem() == ingot
                    && fr.getSmeltingResult(ModItems.purifiedCrushedOre.stackOf(m)).getItem() == ingot
                    && ModItems.ingot.stackOf(m) == null;
        }
        check(chain, "vanilla ores: iron/gold ore -> 2 crushed -> purified -> dust (+10% tiny silver for gold) -> vanilla ingot");

        boolean gems = true;
        net.minecraft.block.Block[] ores = {net.minecraft.init.Blocks.coal_ore, net.minecraft.init.Blocks.redstone_ore, net.minecraft.init.Blocks.lapis_ore,
                net.minecraft.init.Blocks.quartz_ore, net.minecraft.init.Blocks.diamond_ore, net.minecraft.init.Blocks.emerald_ore};
        int[] counts = {2, 6, 8, 2, 1, 1};
        for (int i = 0; i < ores.length; i++) {
            MachineRecipe r = RecipeRegistry.findMatch(MachineType.CRUSHER, new ItemStack[]{new ItemStack(ores[i])}, null, null);
            gems &= r != null && r.outputs[0].stackSize == counts[i] && r.byproducts.length == 1 && r.byproductChances[0] > 0f;
        }
        MachineRecipe lapis = RecipeRegistry.findMatch(MachineType.CRUSHER, new ItemStack[]{new ItemStack(net.minecraft.init.Blocks.lapis_ore)}, null, null);
        gems &= lapis != null && lapis.outputs[0].getItem() == net.minecraft.init.Items.dye && lapis.outputs[0].getItemDamage() == 4;
        check(gems, "vanilla ores: coal/redstone/lapis/quartz/diamond/emerald ore crushed to their drops with a bonus roll");

        MachineRecipe quartz1 = RecipeRegistry.findMatch(MachineType.CRUSHER, new ItemStack[]{new ItemStack(net.minecraft.init.Items.quartz, 1)}, null, null);
        MachineRecipe quartz2 = RecipeRegistry.findMatch(MachineType.CRUSHER, new ItemStack[]{new ItemStack(net.minecraft.init.Items.quartz, 2)}, null, null);
        MachineRecipe grit = RecipeRegistry.findMatch(MachineType.CRUSHER, new ItemStack[]{new ItemStack(net.minecraft.init.Items.diamond)}, null, null);
        MachineRecipe steel = RecipeRegistry.findMatch(MachineType.BLAST_FURNACE,
                new ItemStack[]{ModItems.dust.stackOf(Material.IRON), ModItems.dust.stackOf(Material.CARBON)}, null, null);
        int wires = com.sc.manual.BookContent.craftingFor(new ItemStack(ModItems.TOOLS.get(com.sc.util.SCToolType.DIAMOND_WIRE))).size();
        int blades = com.sc.manual.BookContent.craftingFor(new ItemStack(ModItems.TOOLS.get(com.sc.util.SCToolType.DIAMOND_BLADE))).size();
        check(quartz1 == null && quartz2 != null && quartz2.outputs[0].getItem() == ModItems.siliconMaterial
                        && grit != null && grit.outputs[0].isItemEqual(ModItems.dust.stackOf(Material.DIAMOND))
                        && steel != null && steel.outputs[0].isItemEqual(ModItems.ingot.stackOf(Material.STEEL))
                        && wires >= 2 && blades >= 2,
                "vanilla ores: 2 nether quartz -> silica sand, diamond -> dust (wire/blade recipes), iron dust + carbon -> steel");

        // Append-only metadata: the new materials sit after Magnesium in every item kind.
        check(ModItems.crushedOre.metaOf(Material.MAGNESIUM) == Material.byKind(com.sc.item.MaterialItemKind.CRUSHED_ORE).length - 3
                        && ModItems.dust.metaOf(Material.MAGNESIUM) == Material.byKind(com.sc.item.MaterialItemKind.DUST).length - 4
                        && ModItems.dust.metaOf(Material.DIAMOND) == Material.byKind(com.sc.item.MaterialItemKind.DUST).length - 1
                        && ModItems.ingot.metaOf(Material.MAGNESIUM) == Material.byKind(com.sc.item.MaterialItemKind.INGOT).length - 1,
                "vanilla ores: iron/gold/diamond items appended, older metadata unchanged");
    }

    private static void smelters() {
        com.sc.tileentity.TileEntityMachineSC e = new com.sc.tileentity.TileEntityMachineSC();
        e.setMachineType(com.sc.machine.MachineType.ELECTRIC_FURNACE);
        com.sc.tileentity.TileEntityMachineSC ind = new com.sc.tileentity.TileEntityMachineSC();
        ind.setMachineType(com.sc.machine.MachineType.INDUCTION_FURNACE);
        ItemStack ore = new ItemStack(net.minecraft.init.Blocks.iron_ore), dia = new ItemStack(net.minecraft.init.Items.diamond);
        boolean ok = e.isItemValidForSlot(0, ore) && !e.isItemValidForSlot(1, ore) && !e.isItemValidForSlot(0, dia)
                && ind.isItemValidForSlot(0, ore) && ind.isItemValidForSlot(1, ore) && !ind.isItemValidForSlot(2, ore)
                && com.sc.machine.RecipeRegistry.isValidInput(com.sc.machine.MachineType.ELECTRIC_FURNACE, new ItemStack(net.minecraft.init.Blocks.sand))
                && e.smeltTicks() == com.sc.tileentity.TileEntityMachineSC.SMELT_TICKS && ind.smeltSpeed() == 1.0
                && e.getTier() == com.sc.energy.Tier.LV && ind.getTier() == com.sc.energy.Tier.MV
                && com.sc.machine.MachineType.ELECTRIC_FURNACE.ordinal() < 32 && com.sc.machine.MachineType.INDUCTION_FURNACE.ordinal() < 32
                && com.sc.machine.MachineType.ELECTRIC_FURNACE.smeltStreams() == 1 && com.sc.machine.MachineType.INDUCTION_FURNACE.smeltStreams() == 2;
        check(ok, "smelters: take what a furnace smelts (iron ore, sand; no diamond), 1 / 2 streams, 100 ticks cold, LV / MV, fit the 2nd machine block");
    }

    /** The config's balance multipliers reach what they name (set for the test, then put back). */
    private static void balanceConfig() {
        float ms = com.sc.util.ConfigSC.machineSpeed, st = com.sc.util.ConfigSC.storageCapacity, bc = com.sc.util.ConfigSC.batteryCapacity,
                ac = com.sc.util.ConfigSC.armorCapacity, wr = com.sc.util.ConfigSC.wirelessRange, wl = com.sc.util.ConfigSC.wirelessLoss,
                qu = com.sc.util.ConfigSC.quantumUpkeep, fu = com.sc.util.ConfigSC.fieldUpkeep;
        int base = com.sc.tileentity.TileEntityEnergyStorageSC.capacityOf(com.sc.energy.Tier.LV);
        long bat = com.sc.item.ItemBatterySC.capacity(0);
        int field = com.sc.tileentity.TileEntityFieldGeneratorSC.upkeepFor(1, 8, com.sc.energy.FieldMode.UNION);
        try {
            com.sc.util.ConfigSC.storageCapacity = 2F;
            com.sc.util.ConfigSC.batteryCapacity = 0.5F;
            com.sc.util.ConfigSC.armorCapacity = 3F;
            com.sc.util.ConfigSC.wirelessRange = 2F;
            com.sc.util.ConfigSC.wirelessLoss = 0F;
            com.sc.util.ConfigSC.quantumUpkeep = 0.5F;
            com.sc.util.ConfigSC.fieldUpkeep = 2F;
            ItemStack chest = null;
            for (Object o : net.minecraft.item.Item.itemRegistry) {
                if (o instanceof com.sc.item.ItemArmorSC && ((com.sc.item.ItemArmorSC) o).getSuit() == com.sc.util.ArmorSuit.NANO) {
                    chest = new ItemStack((net.minecraft.item.Item) o);
                    break;
                }
            }
            boolean ok = com.sc.tileentity.TileEntityEnergyStorageSC.capacityOf(com.sc.energy.Tier.LV) == base * 2
                    && com.sc.item.ItemBatterySC.capacity(0) == bat / 2
                    && chest != null && com.sc.item.ItemArmorSC.capacityOf(chest) == com.sc.util.ArmorSuit.NANO.maxCharge * 3
                    && com.sc.tileentity.TileEntityWirelessSC.range(com.sc.energy.Tier.HV) == 128
                    && com.sc.tileentity.TileEntityWirelessSC.range(com.sc.energy.Tier.XV) == Integer.MAX_VALUE
                    && com.sc.tileentity.TileEntityWirelessSC.lossPct(500, com.sc.energy.Tier.LV) == 0
                    && com.sc.tileentity.TileEntityWirelessSC.quantumUpkeep() == 1024
                    && Math.abs(com.sc.tileentity.TileEntityFieldGeneratorSC.upkeepFor(1, 8, com.sc.energy.FieldMode.UNION) - field * 2) <= 1;
            check(ok, "config balance: storage x2, battery x0.5, armour x3, wireless range x2 (XV unlimited), no loss, quantum upkeep x0.5, field x2");
        } finally {
            com.sc.util.ConfigSC.machineSpeed = ms;
            com.sc.util.ConfigSC.storageCapacity = st;
            com.sc.util.ConfigSC.batteryCapacity = bc;
            com.sc.util.ConfigSC.armorCapacity = ac;
            com.sc.util.ConfigSC.wirelessRange = wr;
            com.sc.util.ConfigSC.wirelessLoss = wl;
            com.sc.util.ConfigSC.quantumUpkeep = qu;
            com.sc.util.ConfigSC.fieldUpkeep = fu;
        }
    }

    /** Every sound the code plays is in sounds.json, and every file there is a real Ogg in the jar. */
    private static void sounds() {
        String json = "";
        try {
            java.io.InputStream in = SelfTestSC.class.getResourceAsStream("/assets/siliconage/sounds.json");
            java.util.Scanner sc = new java.util.Scanner(in, "UTF-8").useDelimiter("\\A");
            json = sc.hasNext() ? sc.next() : "";
            sc.close();
        } catch (Exception e) {
            json = "";
        }
        java.util.List<String> names = new java.util.ArrayList<String>();
        for (com.sc.machine.MachineType t : com.sc.machine.MachineType.values()) {
            names.add(com.sc.util.SoundsSC.of(t).name);
        }
        for (com.sc.energy.GeneratorType t : com.sc.energy.GeneratorType.values()) {
            if (com.sc.util.SoundsSC.of(t) != null) {
                names.add(com.sc.util.SoundsSC.of(t).name);
            }
        }
        java.util.Collections.addAll(names, "quarry.drill", "quarry.beam", "power.on", "power.off", "field.zap", "battery.mode", "rad.click");
        String missing = "";
        for (String n : names) {
            if (!json.contains("\"" + n + "\"")) {
                missing += " " + n;
            }
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"sounds\": \\[\"([^\"]+)\"").matcher(json);
        int files = 0;
        while (m.find()) {
            files++;
            java.io.InputStream f = SelfTestSC.class.getResourceAsStream("/assets/siliconage/sounds/" + m.group(1) + ".ogg");
            byte[] head = new byte[4];
            try {
                if (f == null || f.read(head) != 4 || !"OggS".equals(new String(head, "US-ASCII"))) {
                    missing += " file:" + m.group(1);
                }
                if (f != null) {
                    f.close();
                }
            } catch (java.io.IOException e) {
                missing += " file:" + m.group(1);
            }
        }
        check(!json.isEmpty() && files >= 19 && missing.isEmpty(), "sounds: every sound played is in sounds.json, every file an Ogg (" + files + ")"
                + (missing.isEmpty() ? "" : " missing:" + missing));
    }

    /** Fixes of the second bug audit: chat texts only %s (1.7.10's chat throws on %d), comparator, storage automation. */
    private static void audit2Fixes() {
        String[] families = {"sc.areacard.", "sc.cable.warn", "sc.field.zone.toofar", "sc.quarry.warn.", "sc.wrench.missing",
                "sc.wrench.nocharge", "sc.chat.", "sc.field.warn."};
        boolean chatOk = true;
        String bad = "";
        for (String lang : new String[]{"en_US", "ru_RU"}) {
            java.io.InputStream in = SelfTestSC.class.getResourceAsStream("/assets/siliconage/lang/" + lang + ".lang");
            if (in == null) {
                chatOk = false;
                bad = lang + " missing";
                continue;
            }
            try {
                java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(in, "UTF-8"));
                for (String line; (line = r.readLine()) != null; ) {
                    int eq = line.indexOf('=');
                    if (eq <= 0) {
                        continue;
                    }
                    String key = line.substring(0, eq), val = line.substring(eq + 1);
                    for (String f : families) {
                        if (key.startsWith(f) && val.matches(".*%(\\d+\\$)?[a-rt-zA-Z].*")) {
                            chatOk = false;
                            bad = key;
                        }
                    }
                }
                r.close();
            } catch (java.io.IOException e) {
                chatOk = false;
            }
        }
        check(chatOk, "chat texts use %s only (1.7.10's chat throws on %d)" + (bad.isEmpty() ? "" : ": " + bad));
        com.sc.tileentity.TileEntityEnergyStorageSC st = new com.sc.tileentity.TileEntityEnergyStorageSC();
        st.setStorageTier(com.sc.energy.Tier.LV);
        st.setEnergyStoredClient(st.getMaxEnergyStored() * 2);             // as after an Energy Storage upgrade came out
        boolean upgradesHidden = true;
        for (int slot : st.getAccessibleSlotsFromSide(0)) {
            upgradesHidden &= slot < com.sc.tileentity.TileEntityEnergyStorageSC.FIRST_UPGRADE_SLOT
                    || slot >= com.sc.tileentity.TileEntityEnergyStorageSC.FIRST_EXTRA_CHARGE;
        }
        check(st.comparatorLevel() == 15 && upgradesHidden
                        && !st.canExtractItem(com.sc.tileentity.TileEntityEnergyStorageSC.FIRST_UPGRADE_SLOT, null, 0),
                "storage: comparator at most 15, hoppers never reach the upgrade slots");
    }

    private static void generatorBattery() {
        com.sc.tileentity.TileEntityGeneratorSC g = new com.sc.tileentity.TileEntityGeneratorSC();
        g.setGeneratorType(com.sc.energy.GeneratorType.COMBUSTION);
        int slot = com.sc.tileentity.TileEntityGeneratorSC.SLOT_BATTERY;
        ItemStack b = new ItemStack(ModItems.battery, 1, 1);
        g.setInventorySlotContents(slot, b);
        int cap = g.getMaxEnergyStored(), out = g.outputTier().getVoltage();
        g.setEnergyStoredClient(cap);
        int surplus = g.batteryRoundForTest();                     // buffer full: the surplus goes in
        g.setEnergyStoredClient(cap / 2 - 10);
        int low = g.batteryRoundForTest();                         // under half: the grid first
        g.cycleBatteryMode();
        int always = g.batteryRoundForTest();
        com.sc.item.ItemBatterySC.setCharge(b, com.sc.item.ItemBatterySC.capacityOf(b));
        boolean full = !g.batteryCharges(b) && g.canExtractItem(slot, b, 0);
        check(surplus == Math.min(out, 128) && low == 0 && always == Math.min(out, 128) && full,
                "generator battery slot: surplus charges over half only, always charges anyway, a full battery comes out");
    }

    private static void batterySlot() {
        com.sc.tileentity.TileEntityMachineSC m = new com.sc.tileentity.TileEntityMachineSC();
        int slot = com.sc.tileentity.TileEntityMachineSC.SLOT_BATTERY;
        ItemStack b = new ItemStack(ModItems.battery, 1, 1);
        com.sc.item.ItemBatterySC.setCharge(b, 1000);
        boolean valid = m.isItemValidForSlot(slot, b) && !m.isItemValidForSlot(slot, new ItemStack(net.minecraft.init.Items.iron_pickaxe))
                && m.canInsertItem(slot, b, 2) && !m.canExtractItem(slot, b, 0);
        m.setInventorySlotContents(slot, b);
        int cap = m.getMaxEnergyStored();
        int first = m.batteryRoundForTest();                        // LV machine, empty buffer: 32 EU/t from an MV pack
        m.setEnergyStoredClient(cap / 2 + 10);
        int reserve = m.batteryRoundForTest();                      // over half: the reserve waits
        m.cycleBatteryMode();
        int always = m.batteryRoundForTest();
        m.setPowerOn(false);
        int off = m.batteryRoundForTest();
        check(valid && first == 32 && reserve == 0 && always == 32 && off == 0
                        && com.sc.item.ItemBatterySC.chargeOf(b) == 1000 - 64,
                "battery slot: batteries only, fed at the input voltage; reserve waits over half, always tops up, off takes nothing");
    }

    private static void batteries() {
        com.sc.tileentity.TileEntityChargePadSC pad = new com.sc.tileentity.TileEntityChargePadSC();
        pad.setStorageTier(com.sc.energy.Tier.MV);
        ItemStack lv = new ItemStack(ModItems.battery, 1, 0), mv = new ItemStack(ModItems.battery, 1, 1);
        ItemStack hv = new ItemStack(ModItems.battery, 1, 2), xv = new ItemStack(ModItems.battery, 1, 5);
        boolean ok = pad.chargeItem(lv, 1000) == 32 && com.sc.item.ItemBatterySC.chargeOf(lv) == 32
                && pad.chargeItem(mv, 1000) == 128 && pad.chargeItem(hv, 1000) == 0
                && pad.dischargeItem(mv, 1000) == 128 && com.sc.item.ItemBatterySC.chargeOf(mv) == 0;
        check(ok, "batteries: an MV pad charges LV at 32 and MV at 128, not HV; empties them at their rate");
        com.sc.item.ItemBatterySC.setCharge(xv, 4000000000L);
        com.sc.item.ItemBatterySC.setCharge(lv, 1L << 40);
        ok = com.sc.item.ItemBatterySC.chargeOf(xv) == 4000000000L && com.sc.item.ItemBatterySC.discharge(xv, 100000) == 32768
                && com.sc.item.ItemBatterySC.chargeOf(xv) == 4000000000L - 32768 && com.sc.item.ItemBatterySC.chargeOf(lv) == 40000;
        check(ok, "batteries: the XV core holds 4 billion (a long), gives 32768 a call; charge capped at capacity");
        ItemStack nano = new ItemStack(ModItems.ARMOR.get(com.sc.util.ArmorSuit.NANO)[1]);
        ItemStack quantum = new ItemStack(ModItems.ARMOR.get(com.sc.util.ArmorSuit.QUANTUM)[1]);
        ok = com.sc.item.ItemChargeSC.charge(nano, 500, com.sc.energy.Tier.HV) > 0
                && com.sc.item.ItemChargeSC.charge(quantum, 500, com.sc.energy.Tier.LV) == 0
                && com.sc.item.ItemChargeSC.isBattery(hv) && !com.sc.item.ItemChargeSC.isBattery(nano);
        check(ok, "batteries: charge worn suits up to their own tier; a battery counts as a battery");
    }

    /** SV content: the Singular cable (last CableType, SV) and the Singular core battery (meta 6). */
    private static void singularCableAndCore() {
        com.sc.energy.CableType[] cables = com.sc.energy.CableType.values();
        com.sc.energy.CableType sing = com.sc.energy.CableType.SINGULAR;
        check(cables[cables.length - 1] == sing && sing.ordinal() == 8 && com.sc.energy.CableType.EXO.ordinal() == 7
                        && sing.tier == com.sc.energy.Tier.SV && sing.maxAmps == 4 && sing.lossPerBlock == 0 && sing.insulated
                        && sing.maxThroughput() == 524288 && "wireSingularSV".equals(sing.oreDictName),
                "Singular cable: last CableType (meta 8, Exo stays 7), SV, 4 A = 524288 EU/t, no loss, insulated");
        TileEntityConduitBundleSC xvBundle = new TileEntityConduitBundleSC();
        xvBundle.addPart(com.sc.conduit.ConduitKind.CABLE, com.sc.energy.CableType.EXO.ordinal());
        TileEntityConduitBundleSC svBundle = new TileEntityConduitBundleSC();
        boolean added = svBundle.addPart(com.sc.conduit.ConduitKind.CABLE, sing.ordinal())
                && svBundle.addPart(com.sc.conduit.ConduitKind.PIPE, com.sc.util.PipeType.PTFE.ordinal())
                && svBundle.addPart(com.sc.conduit.ConduitKind.TUBE, 0);
        net.minecraft.nbt.NBTTagCompound nbt = new net.minecraft.nbt.NBTTagCompound();
        svBundle.writeToNBT(nbt);
        TileEntityConduitBundleSC back = new TileEntityConduitBundleSC();
        back.readFromNBT(nbt);
        check(added && svBundle.getCable() == sing && back.getCable() == sing && back.getPipe() == com.sc.util.PipeType.PTFE && back.hasTube()
                        && com.sc.energy.ExplosionLogic.burnCableIfOvervolted(xvBundle, com.sc.energy.CableType.EXO.tier, com.sc.energy.Tier.SV)
                        && !com.sc.energy.ExplosionLogic.burnCableIfOvervolted(svBundle, sing.tier, com.sc.energy.Tier.SV),
                "Singular cable: in a bundle with a pipe and a tube (NBT round trip); an SV packet burns an Exo cable, not a Singular one");

        com.sc.energy.Tier[] old = {com.sc.energy.Tier.LV, com.sc.energy.Tier.MV, com.sc.energy.Tier.HV,
                com.sc.energy.Tier.EV, com.sc.energy.Tier.QV, com.sc.energy.Tier.XV};
        String[] oldKeys = {"lv", "mv", "hv", "ev", "qv", "xv"};
        boolean kept = com.sc.item.ItemBatterySC.TIERS.length == 7 && com.sc.item.ItemBatterySC.KEYS.length == 7
                && com.sc.item.ItemBatterySC.CAPACITY.length == 7 && com.sc.item.ItemBatterySC.RATE.length == 7;
        for (int m = 0; m < old.length; m++) {
            ItemStack s = new ItemStack(ModItems.battery, 1, m);
            kept &= com.sc.item.ItemBatterySC.tierOf(s) == old[m] && s.getUnlocalizedName().endsWith(".battery." + oldKeys[m]);
        }
        check(kept, "batteries: meta 0..5 keep LV..XV (SV appended as meta 6)");
        ItemStack sv = new ItemStack(ModItems.battery, 1, 6);
        com.sc.item.ItemBatterySC.setCharge(sv, Long.MAX_VALUE);
        boolean ok = com.sc.item.ItemBatterySC.tierOf(sv) == com.sc.energy.Tier.SV && sv.getUnlocalizedName().endsWith(".battery.sv")
                && com.sc.item.ItemBatterySC.CAPACITY[6] == 16000000000L && com.sc.item.ItemBatterySC.RATE[6] == 131072
                && com.sc.item.ItemBatterySC.capacityOf(sv) == com.sc.item.ItemBatterySC.capacity(6)
                && com.sc.item.ItemBatterySC.chargeOf(sv) == com.sc.item.ItemBatterySC.capacityOf(sv)
                && com.sc.item.ItemBatterySC.charge(sv, Integer.MAX_VALUE) == 0
                && com.sc.item.ItemBatterySC.discharge(sv, Integer.MAX_VALUE) == 131072
                && com.sc.item.ItemBatterySC.chargeOf(sv) == com.sc.item.ItemBatterySC.capacityOf(sv) - 131072
                && ModItems.battery.getTier(sv) == 7;
        com.sc.item.ItemBatterySC.setCharge(sv, 16000000000L);
        ok &= com.sc.item.ItemBatterySC.chargeOf(sv) == Math.min(16000000000L, com.sc.item.ItemBatterySC.capacityOf(sv));
        check(ok, "Singular core: SV, 16 000 000 000 EU (a long, capped, no overflow), 131072 EU/t a call, IC2 tier 7");
        com.sc.tileentity.TileEntityChargePadSC svPad = new com.sc.tileentity.TileEntityChargePadSC();
        svPad.setStorageTier(com.sc.energy.Tier.SV);
        com.sc.tileentity.TileEntityChargePadSC xvPad = new com.sc.tileentity.TileEntityChargePadSC();
        xvPad.setStorageTier(com.sc.energy.Tier.XV);
        ItemStack empty = new ItemStack(ModItems.battery, 1, 6), exo = new ItemStack(ModItems.battery, 1, 5);
        check(xvPad.chargeItem(empty, 1000000) == 0 && svPad.chargeItem(empty, 1000000) == 131072
                        && svPad.chargeItem(exo, 1000000) == 32768 && svPad.dischargeItem(empty, 1000000) == 131072,
                "Singular core: an SV pad charges it at 131072 (and an Exo core at 32768), an XV pad refuses it");
    }

    /** Kept in its own class so IC2's API is only loaded when IC2 is. */
    private static final class Ic2ArmorTest {
        static boolean charge(com.sc.item.ItemArmorSC chest, ItemStack stack) {
            ic2.api.item.IElectricItemManager m = chest.getManager(stack);
            int before = com.sc.item.ItemArmorSC.chargeOf(stack);
            double lv = m.charge(stack, 1000, 1, false, false);
            double mv = m.charge(stack, 1000, 2, false, false);
            double out = m.discharge(stack, 1000, 4, true, true, false);
            return lv == 0 && mv == 128 && out == 0 && com.sc.item.ItemArmorSC.chargeOf(stack) == before + 128;
        }
    }

    // ---- helpers ----

    private static FluidStack copy(FluidStack f) {
        return f == null ? null : f.copy();
    }

    private static ItemStack copyN(ItemStack s, int n) {
        ItemStack c = s.copy();
        c.stackSize = n;
        return c;
    }

    private static boolean sameInputs(MachineRecipe a, MachineRecipe b) {
        return describeInputs(a).equals(describeInputs(b));
    }

    private static String describeInputs(MachineRecipe r) {
        StringBuilder sb = new StringBuilder();
        for (ItemStack s : r.inputs) {
            sb.append(s.getItem()).append('@').append(s.getItemDamage()).append('x').append(s.stackSize).append(' ');
        }
        sb.append(r.fluidInputA == null ? "-" : r.fluidInputA.getFluid().getName() + r.fluidInputA.amount).append(' ');
        sb.append(r.fluidInputB == null ? "-" : r.fluidInputB.getFluid().getName() + r.fluidInputB.amount);
        return sb.toString();
    }

    private static String describe(MachineRecipe r) {
        List<ItemStack> outs = java.util.Arrays.asList(r.outputs);
        return "[" + describeInputs(r) + "] => " + outs;
    }

    /** Matter Compressor: stone block 9, lead block 36, NBT items / capsules refused, a capsule at the threshold, last MachineType. */
    private static void matterCompressor() {
        com.sc.machine.MachineType[] all = com.sc.machine.MachineType.values();
        com.sc.tileentity.TileEntityMachineSC c = new com.sc.tileentity.TileEntityMachineSC();
        c.setMachineType(com.sc.machine.MachineType.MATTER_COMPRESSOR);
        ItemStack stone = new ItemStack(net.minecraft.init.Blocks.stone);
        java.util.List<ItemStack> leads = net.minecraftforge.oredict.OreDictionary.getOres("blockLead");
        ItemStack lead = leads.isEmpty() ? null : leads.get(0).copy();
        ItemStack tagged = new ItemStack(net.minecraft.init.Items.iron_ingot);
        tagged.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
        ItemStack capsule = com.sc.tileentity.TileEntityMachineSC.capsuleStack();
        boolean mass = com.sc.tileentity.TileEntityMachineSC.matterMass(stone) == 9
                && lead != null && com.sc.tileentity.TileEntityMachineSC.matterMass(lead) == 36
                && com.sc.tileentity.TileEntityMachineSC.matterMass(new ItemStack(net.minecraft.init.Items.stick)) == 1
                && com.sc.tileentity.TileEntityMachineSC.matterMass(tagged) == 0 && !c.isItemValidForSlot(0, tagged)
                && capsule != null && com.sc.tileentity.TileEntityMachineSC.matterMass(capsule) == 0 && !c.isItemValidForSlot(0, capsule)
                && c.isItemValidForSlot(0, stone) && !c.isItemValidForSlot(3, stone);
        c.setPowerOn(true);
        c.loadEnergyFromItem(c.getMaxEnergyStored());
        c.setMatterForTest(com.sc.tileentity.TileEntityMachineSC.MATTER_PER_CAPSULE - 1);   // one short: nothing happens
        c.compressorTickForTest();
        boolean idle = c.getStatus() == com.sc.machine.MachineStatus.IDLE && c.getProgressTicks() == 0;
        c.setMatterForTest(com.sc.tileentity.TileEntityMachineSC.MATTER_PER_CAPSULE);
        int ticks = c.compressTicks();
        for (int i = 0; i < ticks; i++) {
            c.compressorTickForTest();
        }
        ItemStack out = c.getStackInSlot(com.sc.tileentity.TileEntityMachineSC.INPUT_SLOTS);
        boolean made = out != null && capsule != null && out.getItem() == capsule.getItem() && out.stackSize == 1 && c.getMatter() == 0;
        c.loadEnergyFromItem(c.getMaxEnergyStored());
        c.setInventorySlotContents(0, new ItemStack(net.minecraft.init.Blocks.stone, 64));   // swallowed 8 a tick
        c.compressorTickForTest();
        boolean absorb = c.getMatter() == 72 && c.getStackInSlot(0) != null && c.getStackInSlot(0).stackSize == 56;
        // nothing valuable burnt by accident: fluid buckets, the mod's machines, nether stars; nothing swallowed without power
        ItemStack machine = new ItemStack(com.sc.init.ModBlocks.machineSC, 1, 0);
        boolean refused = com.sc.tileentity.TileEntityMachineSC.matterMass(new ItemStack(net.minecraft.init.Items.water_bucket)) == 0
                && com.sc.tileentity.TileEntityMachineSC.matterMass(machine) == 0 && !c.isItemValidForSlot(0, machine)
                && com.sc.tileentity.TileEntityMachineSC.matterMass(new ItemStack(net.minecraft.init.Items.nether_star)) == 0
                && com.sc.tileentity.TileEntityMachineSC.matterMass(new ItemStack(net.minecraft.init.Items.diamond)) == 0;
        com.sc.tileentity.TileEntityMachineSC dark = new com.sc.tileentity.TileEntityMachineSC();
        dark.setMachineType(com.sc.machine.MachineType.MATTER_COMPRESSOR);
        dark.setPowerOn(true);
        dark.setInventorySlotContents(0, new ItemStack(net.minecraft.init.Blocks.stone, 64));
        dark.compressorTickForTest();
        boolean unpowered = dark.getMatter() == 0 && dark.getStackInSlot(0) != null && dark.getStackInSlot(0).stackSize == 64;
        boolean last = all[all.length - 1] == com.sc.machine.MachineType.MATTER_COMPRESSOR
                && com.sc.machine.MachineType.MATTER_COMPRESSOR.ordinal() < 32 && com.sc.machine.MachineType.MATTER_COMPRESSOR.tier == com.sc.energy.Tier.IV;
        check(mass && idle && made && absorb && refused && unpowered && last, "matter compressor: stone 9, lead block 36, NBT / capsule refused, capsule at "
                + com.sc.tileentity.TileEntityMachineSC.MATTER_PER_CAPSULE + "; water bucket, the mod's machine, nether star, diamond refused; nothing swallowed"
                + " without power (mass " + mass + ", idle " + idle + ", made " + made + ", absorb " + absorb + ", refused " + refused
                + ", unpowered " + unpowered + ", last " + last + ")");
    }

    /** The Armour Service Station: sharing gas over the pieces (pure), and filling a Quantum set in its slots. */
    private static void armorStation() {
        int[] s = com.sc.tileentity.TileEntityArmorStationSC.split(7000, new int[]{6000, 1000, 1000, 0});
        check(s[0] == 6000 && s[1] == 1000 && s[2] == 0 && s[3] == 0, "station: gas goes to the chestplate first, then on in order");
        s = com.sc.tileentity.TileEntityArmorStationSC.split(500, new int[]{0, 300, 1000, 0});
        check(s[0] == 0 && s[1] == 300 && s[2] == 200 && s[3] == 0, "station: full tanks skipped, the rest takes what's left");
        s = com.sc.tileentity.TileEntityArmorStationSC.split(99999, new int[]{10, 20, 0, 5});
        check(s[0] + s[1] + s[2] + s[3] == 35, "station: never more than the room");
        com.sc.item.ItemArmorSC[] q = ModItems.ARMOR.get(com.sc.util.ArmorSuit.QUANTUM);
        check(!com.sc.tileentity.TileEntityArmorStationSC.fits(com.sc.util.ArmorGasSC.HELMET, new ItemStack(q[com.sc.util.ArmorGasSC.CHEST])), "station: a chestplate doesn't fit the helmet slot");
        check(com.sc.tileentity.TileEntityArmorStationSC.fits(com.sc.util.ArmorGasSC.CHEST, new ItemStack(q[com.sc.util.ArmorGasSC.CHEST])), "station: a chestplate fits its slot");
        com.sc.tileentity.TileEntityArmorStationSC st = new com.sc.tileentity.TileEntityArmorStationSC();
        for (int t = 0; t < 4; t++) {
            st.setInventorySlotContents(t, new ItemStack(q[t]));
        }
        java.util.List<net.minecraft.entity.player.EntityPlayer> nobody = new java.util.ArrayList<net.minecraft.entity.player.EntityPlayer>();
        com.sc.util.ArmorGasSC.Gas he = com.sc.util.ArmorGasSC.Gas.HELIUM;
        int room = 0;
        for (int t = 0; t < 4; t++) {
            room += com.sc.util.ArmorGasSC.capacity(st.getStackInSlot(t), he);
        }
        check(room > 0 && st.need(he, nobody) == room, "station: need = the empty helium tanks of the set (" + room + " mB)");
        int chestCap = com.sc.util.ArmorGasSC.capacity(st.getStackInSlot(com.sc.util.ArmorGasSC.CHEST), he);
        int put = st.putGas(he, chestCap + 1, nobody);
        check(put == chestCap + 1 && com.sc.util.ArmorGasSC.amount(st.getStackInSlot(com.sc.util.ArmorGasSC.CHEST), he) == chestCap
                && com.sc.util.ArmorGasSC.amount(st.getStackInSlot(com.sc.util.ArmorGasSC.HELMET), he) == 1, "station: the chestplate's loop fills first, then the helmet");
        put = st.putGas(he, room * 2, nobody);
        check(put == room - chestCap - 1 && st.need(he, nobody) == 0, "station: tops up to full and no further");
        check(st.putGas(com.sc.util.ArmorGasSC.Gas.DEUTERIUM, 1000, nobody) == 0, "station: no deuterium tank in Quantum - nothing goes in");
        check(st.tierAllows(new ItemStack(ModItems.ARMOR.get(com.sc.util.ArmorSuit.EXO)[1])), "station: charges Exo too (at MV rate)");
        // pipes: GAS_PER_TICK a tick per gas, however many calls
        int cap = com.sc.tileentity.TileEntityArmorStationSC.GAS_PER_TICK;
        com.sc.tileentity.TileEntityArmorStationSC pipe = new com.sc.tileentity.TileEntityArmorStationSC();
        boolean fresh = pipe.pipeLeft(he, 100L) == cap;
        pipe.pipeUsed(he, 100L, 60);
        pipe.pipeUsed(he, 100L, cap - 60);
        boolean spent = pipe.pipeLeft(he, 100L) == 0 && pipe.pipeLeft(com.sc.util.ArmorGasSC.Gas.OXYGEN, 100L) == cap;
        boolean next = pipe.pipeLeft(he, 101L) == cap;
        check(fresh && spent && next, "station: pipes push at most " + cap + " mB of a gas a tick, other gases apart, new tick - new limit");
        armorStationModules();
        armorStationTanks();
    }

    /**
     * The station's seven inner tanks: each takes only its own gas, Tank Extensions add room (and
     * taking them out loses nothing), the armour is filled out of the tank, x pours it out for EU,
     * the tanks ride in the item (no dupe), and an older station loads with empty tanks.
     */
    private static void armorStationTanks() {
        net.minecraftforge.common.util.ForgeDirection any = net.minecraftforge.common.util.ForgeDirection.UNKNOWN;
        com.sc.util.ArmorGasSC.Gas he = com.sc.util.ArmorGasSC.Gas.HELIUM, o2 = com.sc.util.ArmorGasSC.Gas.OXYGEN;
        int cap = com.sc.tileentity.TileEntityArmorStationSC.TANK_CAPACITY, ext = com.sc.machine.UpgradeType.TANK_PER_UPGRADE;
        int up = com.sc.tileentity.TileEntityArmorStationSC.FIRST_UPGRADE_SLOT;
        if (he.fluidOf() == null || o2.fluidOf() == null) {
            check(false, "station tanks: the helium / oxygen fluids aren't registered");
            return;
        }
        // only its own gas; pipes can't drain; seven tanks
        com.sc.tileentity.TileEntityArmorStationSC s = new com.sc.tileentity.TileEntityArmorStationSC();
        boolean own = s.fill(any, new FluidStack(o2.fluidOf(), 1000), true) == 1000 && s.tankAmount(o2) == 1000 && s.tankAmount(he) == 0
                && s.fill(any, new FluidStack(FluidRegistry.WATER, 1000), true) == 0 && !s.canFill(any, FluidRegistry.WATER)
                && s.drain(any, 1000, true) == null && s.drain(any, new FluidStack(o2.fluidOf(), 1000), true) == null
                && s.tankAmount(o2) == 1000 && s.getTankInfo(any).length == com.sc.util.ArmorGasSC.Gas.values().length;
        check(own, "station tanks: oxygen goes into the oxygen tank only, water is refused, pipes can't drain; 7 tanks");
        // Tank Extension: +8000 each, up to 4; out again - nothing lost, nothing taken in
        boolean full = s.fillTank(he, 100000, true) == cap;
        s.setInventorySlotContents(up, ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.TANK_EXTENSION));
        boolean one = s.tankCapacity() == cap + ext && s.fillTank(he, 100000, true) == ext && s.tankAmount(he) == cap + ext;
        ItemStack six = ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.TANK_EXTENSION);
        six.stackSize = 6;
        s.setInventorySlotContents(up, six);
        boolean four = s.tankCapacity() == cap + com.sc.machine.UpgradeType.MAX_TANK_UPGRADES * ext;
        s.setInventorySlotContents(up, null);
        boolean kept = s.tankCapacity() == cap && s.getTank(he).getFluidAmount() == cap + ext && s.fillTank(he, 1, true) == 0
                && s.fill(any, new FluidStack(he.fluidOf(), 1), true) == 0;
        check(full && one && four && kept, "station tanks: " + cap + " mB, +" + ext + " per Tank Extension (counted up to "
                + com.sc.machine.UpgradeType.MAX_TANK_UPGRADES + "); taken out - nothing lost, the tank takes nothing until used down");
        // the armour is filled out of the tank, the pumps paid
        com.sc.item.ItemArmorSC[] q = ModItems.ARMOR.get(com.sc.util.ArmorSuit.QUANTUM);
        com.sc.tileentity.TileEntityArmorStationSC f = new com.sc.tileentity.TileEntityArmorStationSC();
        f.setInventorySlotContents(com.sc.util.ArmorGasSC.CHEST, new ItemStack(q[com.sc.util.ArmorGasSC.CHEST]));
        java.util.List<net.minecraft.entity.player.EntityPlayer> nobody = new java.util.ArrayList<net.minecraft.entity.player.EntityPlayer>();
        boolean dry = f.fillFromTank(he, 1000, nobody) == 0;                       // an empty tank: nothing
        f.fillTank(he, 5000, true);
        boolean poor = f.fillFromTank(he, 1000, nobody) == 0 && f.tankAmount(he) == 5000;   // no energy: nothing
        f.setEnergyStoredClient(10000);
        int moved = f.fillFromTank(he, 1000, nobody);
        int inChest = com.sc.util.ArmorGasSC.amount(f.getStackInSlot(com.sc.util.ArmorGasSC.CHEST), he);
        check(dry && poor && moved == 1000 && inChest == 1000 && f.tankAmount(he) == 4000 && f.getEnergyStored() == 10000 - f.gasCost(1000),
                "station tanks: the armour is filled out of the tank (5000 -> " + f.tankAmount(he) + " mB, chestplate " + inChest
                        + " mB), the pumps paid; an empty tank or no energy - nothing moves");
        // x: pour out for EU, only if it pays it all
        com.sc.tileentity.TileEntityArmorStationSC c = new com.sc.tileentity.TileEntityArmorStationSC();
        c.fillTank(he, 1234, true);
        int cost = c.clearCost(he);
        c.setEnergyStoredClient(cost - 1);
        boolean refused = !c.clearTank(he) && c.tankAmount(he) == 1234 && c.getEnergyStored() == cost - 1;
        c.setEnergyStoredClient(1000);
        boolean poured = c.clearTank(he) && c.tankAmount(he) == 0 && c.getEnergyStored() == 1000 - cost && !c.clearTank(he);
        check(cost == (1234 + com.sc.machine.UpgradeType.CLEAR_MB_PER_EU - 1) / com.sc.machine.UpgradeType.CLEAR_MB_PER_EU && refused && poured,
                "station tanks: x pours a tank out for " + cost + " EU (1 EU / " + com.sc.machine.UpgradeType.CLEAR_MB_PER_EU
                        + " mB) - only when the buffer pays it all");
        // the item: the tanks go in, come back, and aren't left behind too
        com.sc.tileentity.TileEntityArmorStationSC a = new com.sc.tileentity.TileEntityArmorStationSC();
        a.fillTank(he, 3000, true);
        a.fillTank(o2, 500, true);
        net.minecraft.nbt.NBTTagCompound item = a.writeToItem();
        a.takeLooseContents();
        com.sc.tileentity.TileEntityArmorStationSC b = new com.sc.tileentity.TileEntityArmorStationSC();
        b.readFromItem(item);
        check(item.hasKey(com.sc.tileentity.TileEntityArmorStationSC.ITEM_TANKS_KEY) && a.tanksInItem() && a.tankAmount(he) == 0
                        && a.tankAmount(o2) == 0 && b.tankAmount(he) == 3000 && b.tankAmount(o2) == 500
                        && !new com.sc.tileentity.TileEntityArmorStationSC().writeToItem().hasKey(com.sc.tileentity.TileEntityArmorStationSC.ITEM_TANKS_KEY),
                "station tanks: they ride in the item and come back on placement; the broken station keeps no copy (no dupe); empty tanks - no tag");
        // saving; an older station (no tanks in its NBT) loads with empty tanks
        net.minecraft.nbt.NBTTagCompound saved = new net.minecraft.nbt.NBTTagCompound();
        b.writeToNBT(saved);
        com.sc.tileentity.TileEntityArmorStationSC back = new com.sc.tileentity.TileEntityArmorStationSC();
        back.readFromNBT(saved);
        boolean roundTrip = back.tankAmount(he) == 3000 && back.tankAmount(o2) == 500;
        saved.removeTag(com.sc.tileentity.TileEntityArmorStationSC.TANKS_KEY);
        com.sc.tileentity.TileEntityArmorStationSC old = new com.sc.tileentity.TileEntityArmorStationSC();
        old.fillTank(he, 999, true);
        old.readFromNBT(saved);
        boolean empty = true;
        for (com.sc.util.ArmorGasSC.Gas g : com.sc.util.ArmorGasSC.Gas.values()) {
            empty &= old.tankAmount(g) == 0;
        }
        check(roundTrip && empty, "station tanks: saved with the world; an older station without tanks loads with empty ones");
    }

    /** The Armour Service Station's module slots: what goes in, what each module does, the modules in the item (no dupe). */
    private static void armorStationModules() {
        int up = com.sc.tileentity.TileEntityArmorStationSC.FIRST_UPGRADE_SLOT;
        com.sc.tileentity.TileEntityArmorStationSC m = new com.sc.tileentity.TileEntityArmorStationSC();
        ItemStack oc = ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.OVERCLOCKER);
        check(m.isItemValidForSlot(up, oc) && !m.isItemValidForSlot(up, ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.EJECTOR))
                        && m.isItemValidForSlot(up, ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.TANK_EXTENSION))
                        && !m.isItemValidForSlot(0, oc) && !m.isItemValidForSlot(up, new ItemStack(ModItems.ARMOR.get(com.sc.util.ArmorSuit.QUANTUM)[0])),
                "station modules: Overclocker and Tank Extension go in, Ejector doesn't; no module in an armour slot, no armour in a module slot");
        boolean old = m.inputTier() == com.sc.energy.Tier.MV && !m.acceptsAnyVoltage() && m.getMaxEnergyStored() == com.sc.energy.Tier.MV.getBuffer()
                && m.gasPerTick() == com.sc.tileentity.TileEntityArmorStationSC.GAS_PER_TICK && m.gasCost(20) == 1 && m.gasCost(21) == 2
                && m.chargePerRound() == com.sc.energy.Tier.MV.getVoltage() * com.sc.tileentity.TileEntityArmorStationSC.EVERY;
        check(old, "station modules: none in - MV, MV buffer, 100 mB a tick, 1 EU per 20 mB, MV charging, as before");
        m.setInventorySlotContents(up, ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.TRANSFORMER));
        check(m.inputTier() == com.sc.energy.Tier.HV && m.chargePerRound() == com.sc.energy.Tier.HV.getVoltage() * com.sc.tileentity.TileEntityArmorStationSC.EVERY,
                "station modules: one Transformer - input HV, charging at HV's rate");
        ItemStack many = ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.TRANSFORMER);
        many.stackSize = 20;
        m.setInventorySlotContents(up, many);
        check(m.inputTier() == com.sc.energy.Tier.max(), "station modules: Transformers never lift the input past the top tier");
        m.setInventorySlotContents(up, null);
        m.setInventorySlotContents(up + 1, ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.UNIVERSAL_TRANSFORMER));
        check(m.acceptsAnyVoltage() && m.inputTier() == com.sc.energy.Tier.max()
                        && m.receiveEnergy(net.minecraftforge.common.util.ForgeDirection.UNKNOWN, 131072, 100, true) == 100,
                "station modules: Universal Transformer - any voltage");
        m.setInventorySlotContents(up + 2, ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.ENERGY_STORAGE));
        check(m.getMaxEnergyStored() == com.sc.energy.Tier.MV.getBuffer() + com.sc.machine.UpgradeType.STORAGE_PER_UPGRADE,
                "station modules: Energy Storage - buffer +" + com.sc.machine.UpgradeType.STORAGE_PER_UPGRADE + " EU");
        ItemStack ocs = ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.OVERCLOCKER);
        ocs.stackSize = 6;
        m.setInventorySlotContents(up + 3, ocs);
        int g4 = m.gasPerTick();
        check(m.upgradeCount(com.sc.machine.UpgradeType.OVERCLOCKER) == com.sc.tileentity.TileEntityArmorStationSC.MAX_OVERCLOCKERS
                        && g4 == (int) Math.round(100 / Math.pow(0.7, 4)) && m.gasCost(1000) > 50 && m.affordableGas(m.gasCost(1000)) >= 1000,
                "station modules: Overclockers count up to 4 - gas " + g4 + " mB a tick, pumping dearer (" + m.gasCost(1000) + " EU per 1000 mB)");
        // the item: the modules go in, come back, and don't also drop loose
        m.setInventorySlotContents(0, new ItemStack(ModItems.ARMOR.get(com.sc.util.ArmorSuit.QUANTUM)[0]));
        net.minecraft.nbt.NBTTagCompound item = m.writeToItem();
        ItemStack[] kept = com.sc.tileentity.TileEntityArmorStationSC.upgradesOf(item.getCompoundTag(com.sc.tileentity.TileEntityArmorStationSC.ITEM_UPGRADES_KEY));
        java.util.List<ItemStack> loose = m.takeLooseContents();
        boolean noModuleLoose = true;
        for (ItemStack l : loose) {
            noModuleLoose &= !(l.getItem() instanceof com.sc.item.ItemUpgradeSC);
        }
        com.sc.tileentity.TileEntityArmorStationSC back = new com.sc.tileentity.TileEntityArmorStationSC();
        back.readFromItem(item);
        check(m.upgradesInItem() && kept[1] != null && kept[2] != null && kept[3] != null && kept[3].stackSize == 6 && kept[0] == null
                        && loose.size() == 1 && noModuleLoose && m.getStackInSlot(0) == null
                        && back.acceptsAnyVoltage() && back.upgradeCount(com.sc.machine.UpgradeType.ENERGY_STORAGE) == 1
                        && back.upgradeCount(com.sc.machine.UpgradeType.OVERCLOCKER) == 4 && back.getStackInSlot(0) == null,
                "station modules: they ride in the item and come back on placement; breaking then drops only the armour (no dupe)");
        com.sc.tileentity.TileEntityArmorStationSC bare = new com.sc.tileentity.TileEntityArmorStationSC();
        bare.setInventorySlotContents(up, ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.OVERCLOCKER));
        java.util.List<ItemStack> all = bare.takeLooseContents();
        check(!bare.upgradesInItem() && all.size() == 1 && all.get(0).getItem() instanceof com.sc.item.ItemUpgradeSC
                        && !new com.sc.tileentity.TileEntityArmorStationSC().writeToItem().hasKey(com.sc.tileentity.TileEntityArmorStationSC.ITEM_UPGRADES_KEY),
                "station modules: not put into an item - they drop loose; an empty station's item has no module list");
        net.minecraft.nbt.NBTTagCompound saved = new net.minecraft.nbt.NBTTagCompound();
        back.writeToNBT(saved);
        com.sc.tileentity.TileEntityArmorStationSC loaded = new com.sc.tileentity.TileEntityArmorStationSC();
        loaded.readFromNBT(saved);
        check(loaded.acceptsAnyVoltage() && loaded.upgradeCount(com.sc.machine.UpgradeType.OVERCLOCKER) == 4,
                "station modules: saved with the world");
    }

    // ------------------------------------------------------------------ the Ground / Space Bridge (stage 2)

    /**
     * Stage 2 (docs/plan-ground-bridge.md §7, §8, §10): the remote's charge and binding data, the coordinator's NBT,
     * the projected ends' cost (the armour's -25%), the modes' ends, the spot in front of a player, the consent
     * state machine, the friends and the order's NBT, the helmet's link NBT and the level gate, «Взгляд».
     */
    private static void bridge2() {
        // the remote: charge, tier, signal
        ItemStack r = new ItemStack(ModItems.bridgeRemote, 1, com.sc.item.ItemBridgeRemoteSC.GROUND);
        ItemStack sr = new ItemStack(ModItems.bridgeRemote, 1, com.sc.item.ItemBridgeRemoteSC.SPACE);
        int took = com.sc.item.ItemBridgeRemoteSC.charge(r, 4000000);
        int more = com.sc.item.ItemBridgeRemoteSC.charge(r, 999999999);
        boolean hv = com.sc.item.ItemChargeSC.charge(new ItemStack(ModItems.bridgeRemote), 100, com.sc.energy.Tier.HV) == 0
                && com.sc.item.ItemChargeSC.charge(new ItemStack(ModItems.bridgeRemote), 100, com.sc.energy.Tier.IV) == 100;
        boolean spaceSv = com.sc.item.ItemChargeSC.charge(sr, 100, com.sc.energy.Tier.XV) == 0 && com.sc.item.ItemChargeSC.charge(sr, 100, com.sc.energy.Tier.SV) == 100;
        check(took == 4000000 && more == 6000000 && com.sc.item.ItemBridgeRemoteSC.chargeOf(r) == com.sc.bridge.BridgeMathSC.REMOTE_CAPACITY && hv && spaceSv
                && com.sc.item.ItemBridgeRemoteSC.capacityOf(sr) == 20000000L && com.sc.tileentity.TileEntityEnergyStorageSC.isChargeable(r)
                && com.sc.bridge.BridgeMathSC.remoteAfterSignal(3000000L, false) == 2000000L && com.sc.bridge.BridgeMathSC.remoteAfterSignal(999999L, false) == -1
                && com.sc.bridge.BridgeMathSC.remoteAfterSignal(5L, true) == 5L,
                "bridge remote: 10 M EU (Space 20 M), charged from IV (Space SV), 1 M EU a signal, creative free");
        com.sc.bridge.BridgeItemDataSC.bindRemote(r, 10, 64, -20, 0, 777L, "База", com.sc.bridge.BridgeMathSC.GROUND);
        ItemStack cell = com.sc.item.ItemSingularCellSC.filled(ModItems.singularCell, 500);
        com.sc.bridge.BridgeItemDataSC.setInside(sr, "Key", cell);
        int[] l = com.sc.bridge.BridgeItemDataSC.remoteLink(r);
        check(l != null && l[0] == 10 && l[2] == -20 && com.sc.bridge.BridgeItemDataSC.remoteId(r) == 777L && "База".equals(com.sc.bridge.BridgeItemDataSC.remoteBridgeName(r))
                && com.sc.item.ItemBridgeRemoteSC.hasKey(sr) && !com.sc.item.ItemBridgeRemoteSC.hasKey(r)
                && com.sc.bridge.BridgeItemDataSC.remoteLink(new ItemStack(ModItems.bridgeRemote)) == null,
                "bridge remote NBT: the bound controller, its id and name; the Space remote's key (a cell with matter)");
        // the coordinator
        ItemStack c1 = new ItemStack(ModItems.coordinator), c2 = new ItemStack(ModItems.coordinator);
        boolean empty = com.sc.bridge.BridgeItemDataSC.point(c1) == null;
        com.sc.bridge.BridgeItemDataSC.setPoint(c1, 12480, 68, -7315, -1, true);
        com.sc.bridge.BridgeItemDataSC.setPointName(c1, "  Шахта алмазов  ");
        com.sc.bridge.BridgeItemDataSC.copyPoint(c1, c2);
        ItemStack back = ItemStack.loadItemStackFromNBT(c2.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));
        int[] p = com.sc.bridge.BridgeItemDataSC.point(back);
        check(empty && p != null && p[0] == 12480 && p[1] == 68 && p[2] == -7315 && p[3] == -1 && com.sc.bridge.BridgeItemDataSC.safe(back)
                && "Шахта алмазов".equals(com.sc.bridge.BridgeItemDataSC.pointName(back)),
                "coordinator NBT: x y z, dimension, a trimmed name, «безопасно»; a copy into another coordinator");
        // the remote's history: the newest first, the same point moves up, at most 5
        net.minecraft.nbt.NBTTagCompound h = new net.minecraft.nbt.NBTTagCompound();
        for (int i = 0; i < 7; i++) {
            com.sc.bridge.BridgeItemDataSC.pushHistory(h, new int[]{i, 64, 0, 0}, "t" + i);
        }
        com.sc.bridge.BridgeItemDataSC.pushHistory(h, new int[]{4, 64, 0, 0}, "again");
        net.minecraft.nbt.NBTTagList hl = h.getTagList("Hist", 10);
        check(hl.tagCount() == com.sc.bridge.BridgeMathSC.HISTORY && "again".equals(hl.getCompoundTagAt(0).getString("n"))
                && hl.getCompoundTagAt(1).getIntArray("p")[0] == 6 && hl.getCompoundTagAt(4).getIntArray("p")[0] == 2,
                "bridge history: 5 targets, newest first, a repeated point moves to the top");
        // projected ends: the cost, the armour's -25%, the modes' ends
        com.sc.bridge.BridgeMathSC.Cost two = com.sc.bridge.BridgeMathSC.cost(com.sc.bridge.BridgeMathSC.GROUND, new long[]{1000, 3000}, new int[]{25, 0}, false, false, 0);
        com.sc.bridge.BridgeMathSC.Cost one = com.sc.bridge.BridgeMathSC.cost(com.sc.bridge.BridgeMathSC.GROUND, new long[]{4000}, null, false, false, 0);
        com.sc.bridge.BridgeMathSC.Cost sp = com.sc.bridge.BridgeMathSC.cost(com.sc.bridge.BridgeMathSC.SPACE, new long[]{0}, new int[]{25}, false, false, 0);
        check(two.eu == 275000000L && two.sm == 87 && two.kr == 38 && one.eu == 280000000L && one.sm == 90 && one.kr == 40 && sp.kr == 150 && sp.eu == 4000000000L,
                "bridge projections: each end pays its distance; from the armour your end -25% (Ground 275 M EU, SM 87, Kr 38; Space Kr 150)");
        boolean ends = java.util.Arrays.equals(com.sc.bridge.BridgeMathSC.modeEnds(1, false), new int[]{0, 2})
                && java.util.Arrays.equals(com.sc.bridge.BridgeMathSC.modeEnds(2, false), new int[]{2, 1})
                && java.util.Arrays.equals(com.sc.bridge.BridgeMathSC.modeEnds(3, false), new int[]{0, 1})
                && java.util.Arrays.equals(com.sc.bridge.BridgeMathSC.modeEnds(4, false), new int[]{0, 2})
                && java.util.Arrays.equals(com.sc.bridge.BridgeMathSC.modeEnds(4, true), new int[]{2, 2})
                && java.util.Arrays.equals(com.sc.bridge.BridgeMathSC.modeEnds(5, false), new int[]{0, 2})
                && com.sc.bridge.BridgeMathSC.friendEnd(4, false) == 1 && com.sc.bridge.BridgeMathSC.friendEnd(4, true) == 0
                && com.sc.bridge.BridgeMathSC.friendEnd(1, false) == -1 && com.sc.bridge.BridgeMathSC.projections(2, false) == 2
                && com.sc.bridge.BridgeMathSC.projections(4, true) == 2 && com.sc.bridge.BridgeMathSC.projections(0, false) == 1
                && com.sc.bridge.BridgeMathSC.needsPoint(2) && !com.sc.bridge.BridgeMathSC.needsPoint(1) && !com.sc.bridge.BridgeMathSC.needsPoint(4);
        int[] s0 = com.sc.bridge.BridgeMathSC.projectionSpot(10.3, 64.0, -5.7, 0F), s1 = com.sc.bridge.BridgeMathSC.projectionSpot(10.3, 64.0, -5.7, 90F),
                s2 = com.sc.bridge.BridgeMathSC.projectionSpot(10.3, 64.0, -5.7, 180F), s3 = com.sc.bridge.BridgeMathSC.projectionSpot(10.3, 64.0, -5.7, -90F);
        check(ends && java.util.Arrays.equals(s0, new int[]{10, 64, -3, 0}) && java.util.Arrays.equals(s1, new int[]{7, 64, -6, 1})
                && java.util.Arrays.equals(s2, new int[]{10, 64, -9, 0}) && java.util.Arrays.equals(s3, new int[]{13, 64, -6, 1}),
                "bridge modes ДР1-ДР5: the ends (ring / point / next to a player), the friend's end; a projection 3 blocks the way a player faces");
        // consent (§10): the state machine
        com.sc.bridge.BridgeConsentSC cs = new com.sc.bridge.BridgeConsentSC(600);
        net.minecraft.nbt.NBTTagCompound order = new net.minecraft.nbt.NBTTagCompound();
        order.setInteger("m", 4);
        com.sc.bridge.BridgeConsentSC.Request q = cs.ask("Alice", "Bob", 0, order);
        int stranger = cs.answer(q.id, "Eve", true, 10);
        int yes = cs.answer(q.id, "bob", true, 20);
        int again = cs.answer(q.id, "Bob", false, 30);
        boolean used = cs.use(q.id), usedTwice = cs.use(q.id);
        com.sc.bridge.BridgeConsentSC.Request late = cs.ask("Alice", "Bob", 1000, order);
        int expired = cs.answer(late.id, "Bob", true, 1601);
        com.sc.bridge.BridgeConsentSC.Request d1 = cs.ask("Alice", "Carl", 2000, order), d2 = cs.ask("Alice", "Carl", 2001, order);
        int no = cs.answer(d2.id, "Carl", false, 2002);
        check(stranger == com.sc.bridge.BridgeConsentSC.NOT_YOURS && yes == com.sc.bridge.BridgeConsentSC.ACCEPTED && again == com.sc.bridge.BridgeConsentSC.CLOSED
                && used && !usedTwice && cs.state(q.id) == com.sc.bridge.BridgeConsentSC.USED && expired == com.sc.bridge.BridgeConsentSC.CLOSED
                && cs.state(late.id) == com.sc.bridge.BridgeConsentSC.EXPIRED && cs.state(d1.id) == com.sc.bridge.BridgeConsentSC.EXPIRED
                && no == com.sc.bridge.BridgeConsentSC.DECLINED && !cs.use(d2.id) && cs.answer(9999, "Bob", true, 2003) == com.sc.bridge.BridgeConsentSC.NOT_FOUND
                && cs.get(q.id).order.getInteger("m") == 4,
                "bridge consent: only the asked player answers, once; accepted is used once; 30 s then expired; a new request replaces the old");
        // access: friends, public, the order's NBT
        com.sc.tileentity.TileEntityBridgeControllerSC a = new com.sc.tileentity.TileEntityBridgeControllerSC();
        a.setOwner("Owner");
        boolean fr = a.addFriend("Pal") && !a.addFriend("pal") && !a.addFriend("Owner") && !a.addFriend("bad name") && !a.addFriend("x")
                && a.addFriend("Friend_2") && a.isFriend("PAL") && !a.isFriend("Eve");
        a.setAccess(com.sc.bridge.BridgeMathSC.ACCESS_PUBLIC);
        a.setBridgeName("База");
        a.setRemoteMode(true);
        long id = a.ensureBridgeId();
        net.minecraft.nbt.NBTTagCompound t = new net.minecraft.nbt.NBTTagCompound();
        a.writeToNBT(t);
        com.sc.tileentity.TileEntityBridgeControllerSC b = new com.sc.tileentity.TileEntityBridgeControllerSC();
        b.readFromNBT(t);
        a.removeFriend(0);
        check(fr && b.getFriends().size() == 2 && b.isFriend("pal") && b.getAccess() == com.sc.bridge.BridgeMathSC.ACCESS_PUBLIC && "База".equals(b.getBridgeName())
                && b.isRemoteMode() && id != 0 && b.getBridgeId() == id && a.getFriends().size() == 1 && b.trusted(null) && b.allowed(null),
                "bridge access: friends (no owner, no doubles, plausible names), public mode, the name, «Дистанционный режим», the id kept in NBT");
        com.sc.tileentity.TileEntityBridgeControllerSC.Order o = com.sc.bridge.BridgeFarSC.order(new int[]{0, 1, 5, -1, 7, -1, 1}, "Pal\nШахта", 2);
        o.consented.add("Pal");
        com.sc.tileentity.TileEntityBridgeControllerSC.Order o2 = com.sc.tileentity.TileEntityBridgeControllerSC.Order.read(o.write());
        check(o.mode == com.sc.bridge.BridgeMathSC.MODE_REMOTE && o2.mode == o.mode && o2.hasPoint && o2.px == 5 && o2.py == -1 && o2.pz == 7 && o2.pdim == -1
                && o2.toMe && "Pal".equals(o2.friend) && "Шахта".equals(o2.pointName) && o2.source == com.sc.bridge.BridgeMathSC.SRC_ARMOUR
                && o2.agreed("pal") && !o2.agreed("Eve") && com.sc.bridge.BridgeFarSC.order(new int[]{1}, "", 1) == null,
                "bridge order NBT: mode («С базы» from afar is «удалённо»), point, «к вам», the friend, the name, who agreed");
        // the helmet's link and the level gate
        ItemStack helmet = new ItemStack(ModItems.ARMOR.get(com.sc.util.ArmorSuit.SINGULAR)[0]);
        boolean noMod = !com.sc.bridge.BridgeItemDataSC.hasModule(helmet) && com.sc.bridge.BridgeItemDataSC.addLink(helmet, 0, 64, 0, 0, 1L, "A", 0) == -1;
        com.sc.bridge.BridgeItemDataSC.installModule(helmet);
        int i0 = com.sc.bridge.BridgeItemDataSC.addLink(helmet, 0, 64, 0, 0, 1L, "A", 0);
        int i1 = com.sc.bridge.BridgeItemDataSC.addLink(helmet, 100, 64, 0, 0, 2L, "B", 0);
        int i2 = com.sc.bridge.BridgeItemDataSC.addLink(helmet, 0, 64, 900, -1, 3L, "C", 1);
        int i3 = com.sc.bridge.BridgeItemDataSC.addLink(helmet, 5, 5, 5, 0, 4L, "D", 0);
        int again2 = com.sc.bridge.BridgeItemDataSC.addLink(helmet, 100, 64, 0, 0, 2L, "B2", 0);
        com.sc.bridge.BridgeItemDataSC.select(helmet, 2);
        ItemStack hb = ItemStack.loadItemStackFromNBT(helmet.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));
        boolean links = noMod && i0 == 0 && i1 == 1 && i2 == 2 && i3 == -1 && again2 == 1 && com.sc.bridge.BridgeItemDataSC.links(hb).tagCount() == 3
                && "B2".equals(com.sc.bridge.BridgeItemDataSC.links(hb).getCompoundTagAt(1).getString("n")) && com.sc.bridge.BridgeItemDataSC.selected(hb) == 2;
        com.sc.bridge.BridgeItemDataSC.removeLink(hb, 0);
        links &= com.sc.bridge.BridgeItemDataSC.links(hb).tagCount() == 2 && com.sc.bridge.BridgeItemDataSC.selected(hb) == 1
                && com.sc.bridge.BridgeItemDataSC.links(hb).getCompoundTagAt(1).getLong("id") == 3L;
        com.sc.bridge.BridgeItemDataSC.clearLinks(hb);
        links &= com.sc.bridge.BridgeItemDataSC.hasModule(hb) && com.sc.bridge.BridgeItemDataSC.links(hb).tagCount() == 0;
        com.sc.util.ArmorFeature f = com.sc.util.ArmorFeature.BRIDGE_LINK;
        boolean gate = f.ordinal() == 47 && f.piece == 0 && f.availableIn(com.sc.util.ArmorSuit.SINGULAR, 0) && !f.availableIn(com.sc.util.ArmorSuit.EXO, 0)
                && com.sc.util.SingularLevel.requiredLevel(f) == 3 && !com.sc.util.SingularLevel.unlocked(f, 2, false)
                && com.sc.util.SingularLevel.unlocked(f, 3, false) && com.sc.util.SingularLevel.unlocked(f, 1, true) && f.gas() == null && f.onByDefault
                && com.sc.util.SingularLevel.BRIDGE_FINDS_LEVEL == 5 && !f.isAction();
        check(links && gate, "armour bridge link: the module merged in, up to 3 bridges (the same id renamed, not added), select / unlink / clear kept in NBT;"
                + " the function on the Singular helmet from level 3 (creative always)");
        // «Взгляд»
        com.sc.bridge.BridgeMathSC.Solid floor = new com.sc.bridge.BridgeMathSC.Solid() {
            @Override
            public boolean at(int x, int y, int z) {
                return y < 64 || x == 10 || x == 300;
            }
        };
        int[] down = com.sc.bridge.BridgeMathSC.lookTarget(0.5, 70.5, 0.5, 0, -1, 0, 256, floor);
        int[] wall = com.sc.bridge.BridgeMathSC.lookTarget(0.5, 64.5, 0.5, 2, 0, 0, 256, floor);
        int[] far = com.sc.bridge.BridgeMathSC.lookTarget(20.5, 64.5, 0.5, 1, 0, 0, 256, floor);
        int[] sky = com.sc.bridge.BridgeMathSC.lookTarget(20.5, 64.5, 0.5, 0, 1, 0, 256, floor);
        check(java.util.Arrays.equals(down, new int[]{0, 64, 0}) && java.util.Arrays.equals(wall, new int[]{10, 65, 0}) && far == null && sky == null
                && com.sc.bridge.BridgeMathSC.lookTarget(0, 0, 0, 0, 0, 0, 10, floor) == null,
                "bridge «Взгляд»: the first solid block along the look within 256 blocks (the cell over it), nothing beyond or in the sky");
        // the messages
        io.netty.buffer.ByteBuf buf = io.netty.buffer.Unpooled.buffer();
        new com.sc.bridge.BridgeNetSC.Far(1, 2, com.sc.bridge.BridgeFarSC.F_OPEN, new int[]{4, 0, -5, 70, 9, -1, 1}, "Pal\nДом").toBytes(buf);
        com.sc.bridge.BridgeNetSC.Far fm = new com.sc.bridge.BridgeNetSC.Far();
        fm.fromBytes(buf);
        buf.clear();
        new com.sc.bridge.BridgeNetSC.Hud(true, "База", 340, 600, 92, 1, 77, 2, "gas.helium").toBytes(buf);
        com.sc.bridge.BridgeNetSC.Hud hm = new com.sc.bridge.BridgeNetSC.Hud();
        hm.fromBytes(buf);
        check(fm.src == 1 && fm.slot == 2 && fm.action == com.sc.bridge.BridgeFarSC.F_OPEN && java.util.Arrays.equals(fm.values, new int[]{4, 0, -5, 70, 9, -1, 1})
                && "Pal\nДом".equals(fm.text) && hm.open && "База".equals(hm.name) && hm.left == 340 && hm.total == 600 && hm.stability == 92 && hm.kind == 1 && hm.heat == 77 && hm.warn == 2 && "gas.helium".equals(hm.shortWhat)
                && com.sc.inventory.ContainerSingularStationSC.TABS == 6 && com.sc.inventory.ContainerSingularStationSC.TAB_LINK == 5,
                "bridge messages: a remote / armour command and the HUD line through bytes; the Singular Station's «Связь» tab");
    }

    // ------------------------------------------------------------------ the Ground / Space Bridge (stage 3)

    /**
     * Stage 3 (docs/plan-ground-bridge.md §9): the stability formula and its factors, wear per opening and the repair's
     * price, the mass of a pass, the familiar-chunk memory (LRU, NBT), the scatter rules and offsets, the turbulent
     * arrival's standing spot, interference on fake layouts, the heat / overheat machine and the coils' colour.
     */
    private static void bridge3() {
        // С3 stability
        com.sc.bridge.BridgeMathSC.Stab full = com.sc.bridge.BridgeMathSC.stability(4, 0, 0, false, false, false, 0);
        com.sc.bridge.BridgeMathSC.Stab mix = com.sc.bridge.BridgeMathSC.stability(2, 40, 20, false, true, true, 15);
        com.sc.bridge.BridgeMathSC.Stab zero = com.sc.bridge.BridgeMathSC.stability(0, 100, 400, false, true, true, 100);
        check(full.total == 100 && mix.missing == 10 && mix.wear == 10 && mix.mass == 4 && mix.interference == 20 && mix.storm == 15
                && mix.argon == 15 && mix.total == 26 && mix.parts().length == 7 && mix.parts()[6] == 26 && zero.total == 0
                && com.sc.bridge.BridgeMathSC.argonStep(0, true) == 15 && com.sc.bridge.BridgeMathSC.argonStep(15, false) == 10
                && com.sc.bridge.BridgeMathSC.argonStep(3, false) == 0,
                "bridge stability: 100 - stabilisers - wear - mass - interference - storm - argon (26% for a mixed case), clamped; argon deficit +15 / -5 a second");
        // С2 wear and repair
        check(com.sc.bridge.BridgeMathSC.wearPerOpen(100, false, 0, 0) == 1 && com.sc.bridge.BridgeMathSC.wearPerOpen(2500, false, 0, 0) == 2
                && com.sc.bridge.BridgeMathSC.wearPerOpen(0, true, 120, 0) == 3 && com.sc.bridge.BridgeMathSC.wearPerOpen(0, false, 0, 65) == 2
                && com.sc.bridge.BridgeMathSC.wearPerOpen(99999, true, 999, 100) == 3
                && com.sc.bridge.BridgeMathSC.wearStab(20) == 0 && com.sc.bridge.BridgeMathSC.wearStab(30) == 5 && com.sc.bridge.BridgeMathSC.wearStab(100) == 40
                && com.sc.bridge.BridgeMathSC.repairHe(25) == 1000 && com.sc.bridge.BridgeMathSC.repairEu(25) == 50000000L
                && com.sc.bridge.BridgeMathSC.repairEu(0) == 0,
                "bridge wear: 1-3% an opening (far / another dimension, heavy or hot), free up to 20% then -1% stability per 2%; repair 40 mB He + 2 M EU per 1%");
        // С4 mass
        check(com.sc.bridge.BridgeMathSC.massEu(com.sc.bridge.BridgeMathSC.MASS_PLAYER, false) == 1000000L
                && com.sc.bridge.BridgeMathSC.massEu(com.sc.bridge.BridgeMathSC.MASS_MOB, true) == 1000000L
                && com.sc.bridge.BridgeMathSC.massEu(com.sc.bridge.BridgeMathSC.MASS_ITEM, false) == 100000L
                && com.sc.bridge.BridgeMathSC.massEu(com.sc.bridge.BridgeMathSC.MASS_CART, true) == 2000000L
                && com.sc.bridge.BridgeMathSC.massStab(com.sc.bridge.BridgeMathSC.MASS_PLAYER, false) == 2
                && com.sc.bridge.BridgeMathSC.massStab(com.sc.bridge.BridgeMathSC.MASS_PLAYER, true) == 1
                && com.sc.bridge.BridgeMathSC.massStab(com.sc.bridge.BridgeMathSC.MASS_CART + com.sc.bridge.BridgeMathSC.MASS_MOB, false) == 12,
                "bridge mass: player 1 / mob 2 / item 0.1 / minecart 4 units, 1 M EU and 2% stability a unit, halved by the compensator");
        // С5 familiar chunks: the LRU store and the saved data
        com.sc.bridge.BridgeFamiliarSC.Store lru = new com.sc.bridge.BridgeFamiliarSC.Store(3);
        boolean n1 = lru.mark(0, 1, 1), n2 = lru.mark(0, -2, 5), n3 = lru.mark(-1, 1, 1), again = lru.mark(0, 1, 1);
        boolean hadA = lru.has(0, 1, 1);
        lru.mark(0, 9, 9);                                           // the 4th: the one looked at longest ago (0, -2, 5) goes
        boolean lruOk = n1 && n2 && n3 && !again && hadA && lru.size() == 3 && !lru.has(0, -2, 5) && lru.has(0, 1, 1) && lru.has(-1, 1, 1)
                && lru.has(0, 9, 9) && !lru.has(1, 1, 1) && lru.scout(7) && !lru.scout(7) && lru.scouted(7) && !lru.scouted(0);
        com.sc.bridge.BridgeFamiliarSC fs = new com.sc.bridge.BridgeFamiliarSC("t");
        fs.markBlock("Alice", -1, -100, 50, 1);
        fs.scout("Alice", 7);
        net.minecraft.nbt.NBTTagCompound ft = new net.minecraft.nbt.NBTTagCompound();
        fs.writeToNBT(ft);
        com.sc.bridge.BridgeFamiliarSC fs2 = new com.sc.bridge.BridgeFamiliarSC("t");
        fs2.readFromNBT(ft);
        boolean famOk = fs2.familiar("alice", -1, -100, 50) && fs2.familiar("ALICE", -1, -100 + 16, 50 - 16) && !fs2.familiar("Alice", -1, -100 + 48, 50)
                && !fs2.familiar("Alice", 0, -100, 50) && !fs2.familiar("Bob", -1, -100, 50) && fs2.of("Alice").size() == 9 && fs2.scouted("alice", 7)
                && !fs2.scouted("Alice", 1) && new com.sc.bridge.BridgeFamiliarSC.Store(com.sc.bridge.BridgeMathSC.FAMILIAR_CAP).cap() == 4096;
        com.sc.bridge.BridgeFamiliarSC.Store big = new com.sc.bridge.BridgeFamiliarSC.Store(com.sc.bridge.BridgeMathSC.FAMILIAR_CAP);
        for (int i = 0; i < 5000; i++) {
            big.mark(0, i, -i);
        }
        check(lruOk && famOk && big.size() == 4096 && !big.has(0, 0, 0) && big.has(0, 4999, -4999) && big.has(0, 904, -904) && !big.has(0, 903, -903),
                "bridge familiar places: per player (any case), 3x3 chunks round a scanner, kept in NBT, scouted dimensions; at most 4096 - the oldest forgotten");
        // С5 / С7 scatter rules
        boolean rules = com.sc.bridge.BridgeMathSC.scatterRadius(true, false, false, false, false) == 0
                && com.sc.bridge.BridgeMathSC.scatterRadius(false, false, false, false, false) == 30
                && com.sc.bridge.BridgeMathSC.scatterRadius(false, false, true, false, false) == 10
                && com.sc.bridge.BridgeMathSC.scatterRadius(false, true, false, false, false) == 0
                && com.sc.bridge.BridgeMathSC.scatterRadius(false, false, false, true, false) == 0
                && com.sc.bridge.BridgeMathSC.scatterRadius(true, false, true, false, true) == 100
                && com.sc.bridge.BridgeMathSC.scatterRadius(false, false, true, true, true) == 0;
        java.util.Random rnd = new java.util.Random(42);
        boolean inside = true, moved = false;
        for (int i = 0; i < 300; i++) {
            int[] o = com.sc.bridge.BridgeMathSC.scatterOffset(rnd, 30);
            inside &= o[0] * o[0] + o[1] * o[1] <= 900;
            moved |= o[0] != 0 || o[1] != 0;
        }
        int[] none = com.sc.bridge.BridgeMathSC.scatterOffset(rnd, 0);
        com.sc.bridge.BridgeMathSC.Cost famC = com.sc.bridge.BridgeMathSC.adjust(com.sc.bridge.BridgeMathSC.cost(0, new long[]{0}, false, false, 0), -25, false);
        com.sc.bridge.BridgeMathSC.Cost unC = com.sc.bridge.BridgeMathSC.adjust(com.sc.bridge.BridgeMathSC.cost(0, new long[]{0}, false, false, 0), 50, true);
        com.sc.bridge.BridgeMathSC.Cost spC = com.sc.bridge.BridgeMathSC.adjust(com.sc.bridge.BridgeMathSC.cost(1, new long[]{0}, false, false, 0), 0, false);
        check(rules && inside && moved && none[0] == 0 && none[1] == 0 && famC.eu == 150000000L && famC.sm == 37 && famC.kr == 15 && famC.famPct == -25
                && unC.eu == 240000000L && unC.sm == 75 && unC.resonance && spC.eu == 4000000000L,
                "bridge scatter: familiar / beacon / level-5 find precise, unfamiliar 30 (Navigation Computer 10), scouting 100; offsets in the circle; "
                        + "familiar -25%, unfamiliar +50%, resonance -20% EU; Space without an anchor stays x2 (4 G)");
        // С3 turbulence: where a shaken arrival stands
        FakeCells fc = new FakeCells();
        int[] open = com.sc.bridge.BridgeSpaceSC.standSpot(fc, 0, 64, 0, 4, 6);
        fc.put(0, 64, 0, com.sc.bridge.BridgeSpaceSC.SOLID).put(0, 65, 0, com.sc.bridge.BridgeSpaceSC.SOLID);
        int[] up = com.sc.bridge.BridgeSpaceSC.standSpot(fc, 0, 64, 0, 0, 6);
        FakeCells lava = new FakeCells();
        lava.ground = 0;
        int[] noneSpot = com.sc.bridge.BridgeSpaceSC.standSpot(lava, 0, 64, 0, 2, 3);
        check(java.util.Arrays.equals(open, new int[]{0, 64, 0}) && java.util.Arrays.equals(up, new int[]{0, 66, 0}) && noneSpot == null,
                "bridge turbulence: a shaken arrival stands on the nearest floor with two free cells (none over the void)");
        // §7б: the turbulence / scatter takes any free place - the air too (one comes out with a soft landing)
        FakeCells sky = new FakeCells();
        sky.ground = 0;
        int[] inAir = com.sc.bridge.BridgeSpaceSC.freeSpot(sky, 0, 64, 0, 4, 6);
        sky.put(0, 64, 0, com.sc.bridge.BridgeSpaceSC.SOLID);
        int[] past = com.sc.bridge.BridgeSpaceSC.freeSpot(sky, 0, 64, 0, 4, 6);
        FakeCells overLava = new FakeCells();
        overLava.put(0, 63, 0, com.sc.bridge.BridgeSpaceSC.LAVA);
        int[] noLava = com.sc.bridge.BridgeSpaceSC.freeSpot(overLava, 0, 64, 0, 4, 6);
        check(java.util.Arrays.equals(inAir, new int[]{0, 64, 0}) && past != null && !(past[0] == 0 && past[1] == 64 && past[2] == 0)
                        && past[1] == 65 && noLava != null && !(noLava[0] == 0 && noLava[1] == 64 && noLava[2] == 0),
                "bridge turbulence (§7б): a shaken arrival may come out in the air - the nearest two free cells, not into a block, not over lava");
        // §7б soft landing: the countdown (pure)
        int sl = com.sc.bridge.BridgeSoftLandSC.MAX_TICKS, airTicks = 0;
        while (sl > 0 && airTicks < 10000) {
            sl = com.sc.bridge.BridgeSoftLandSC.next(sl, false);
            airTicks++;
        }
        int g = com.sc.bridge.BridgeSoftLandSC.MAX_TICKS;
        boolean grace = com.sc.bridge.BridgeSoftLandSC.next(g, true) == g - 1
                && com.sc.bridge.BridgeSoftLandSC.next(g - com.sc.bridge.BridgeSoftLandSC.GRACE, true) == 0
                && com.sc.bridge.BridgeSoftLandSC.next(300, true) == 0 && com.sc.bridge.BridgeSoftLandSC.next(300, false) == 299
                && com.sc.bridge.BridgeSoftLandSC.next(0, false) == 0 && com.sc.bridge.BridgeSoftLandSC.next(-5, true) == 0;
        boolean capOk = com.sc.bridge.BridgeSoftLandSC.cap(-0.8) == com.sc.bridge.BridgeSoftLandSC.FALL_CAP
                && com.sc.bridge.BridgeSoftLandSC.cap(-0.1) == -0.1 && com.sc.bridge.BridgeSoftLandSC.cap(0.42) == 0.42
                && com.sc.bridge.BridgeSoftLandSC.FALL_CAP == -0.15;
        boolean need = com.sc.bridge.BridgeSoftLandSC.needed(com.sc.bridge.BridgeSpaceSC.AIR) && com.sc.bridge.BridgeSoftLandSC.needed(com.sc.bridge.BridgeSpaceSC.PASS)
                && !com.sc.bridge.BridgeSoftLandSC.needed(com.sc.bridge.BridgeSpaceSC.SOLID) && !com.sc.bridge.BridgeSoftLandSC.needed(com.sc.bridge.BridgeSpaceSC.WATER);
        check(airTicks == 600 && grace && capOk && need,
                "bridge soft landing: lasts 30 s in the air at most (" + airTicks + " ticks), ends on touching the ground (not in the first "
                        + com.sc.bridge.BridgeSoftLandSC.GRACE + " ticks - stale onGround), caps the fall at 0.15 b/t, only over air / grass");
        // С6 interference on fake layouts
        java.util.List<int[]> others = new java.util.ArrayList<int[]>();
        others.add(new int[]{0, 64, 0});
        others.add(new int[]{30, 64, 30});
        others.add(new int[]{70, 64, 0});
        others.add(new int[]{0, 64, -64});
        java.util.List<int[]> alone = new java.util.ArrayList<int[]>();
        alone.add(new int[]{0, 64, 0});
        check(com.sc.bridge.BridgeMathSC.interferers(0, 64, 0, others, 64) == 2 && com.sc.bridge.BridgeMathSC.interferers(0, 64, 0, alone, 64) == 0
                && com.sc.bridge.BridgeMathSC.interferers(70, 64, 0, others, 64) == 1,
                "bridge interference: other controllers within 64 blocks counted (itself not)");
        // С12 heat and overheat
        int heat = 0, secs = 0;
        while (heat < com.sc.bridge.BridgeMathSC.HEAT_MAX && secs < 1000) {
            heat = com.sc.bridge.BridgeMathSC.heatStep(heat, 50, 25, false);
            secs++;
        }
        int calm = 0;
        for (int i = 0; i < 60; i++) {
            calm = com.sc.bridge.BridgeMathSC.heatStep(calm, 0, 90, false);
        }
        int dry = com.sc.bridge.BridgeMathSC.heatStep(0, 0, 90, true);
        check(secs == 20 && heat == com.sc.bridge.BridgeMathSC.HEAT_MAX && calm == 600 && dry == 60
                && com.sc.bridge.BridgeMathSC.coolingHeat(1000, 1200, 2400) == 500 && com.sc.bridge.BridgeMathSC.coolingHeat(800, 0, 1200) == 0
                && com.sc.bridge.BridgeMathSC.coilMeta(true, 0) == 1 && com.sc.bridge.BridgeMathSC.coilMeta(true, 500) == 2
                && com.sc.bridge.BridgeMathSC.coilMeta(false, 800) == 3 && com.sc.bridge.BridgeMathSC.coilMeta(false, 100) == 0
                && com.sc.bridge.BridgeMathSC.OVERHEAT_LOCK_S == 120,
                "bridge heat: 1%/s (60 s - 60%), wear 50% + a shaking vortex overheat in 20 s, no helium +5%/s; cools with the ring; coils blue / orange / red");
        // the controller keeps stage 3 in NBT
        com.sc.tileentity.TileEntityBridgeControllerSC a = new com.sc.tileentity.TileEntityBridgeControllerSC();
        a.setWearForTest(37);
        a.setHeatForTest(420);
        net.minecraft.nbt.NBTTagCompound t = new net.minecraft.nbt.NBTTagCompound();
        a.writeToNBT(t);
        com.sc.tileentity.TileEntityBridgeControllerSC b = new com.sc.tileentity.TileEntityBridgeControllerSC();
        b.readFromNBT(t);
        check(b.getWear() == 37 && b.getHeat() == 420 && !b.isOverheatLocked() && t.getIntArray("Stage3").length == 10,
                "bridge stage 3 NBT: wear, heat, the overheat lock and the opening's stats saved");
    }

    // ------------------------------------------------------------------ the Ground / Space Bridge (stage 1)

    /** A fake world of bridge block kinds (everything else air). */
    private static final class FakeBridge implements com.sc.bridge.BridgeStructureSC.View {
        final java.util.Map<Long, Integer> m = new java.util.HashMap<Long, Integer>();

        static long k(int x, int y, int z) {
            return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
        }

        FakeBridge put(int x, int y, int z, int kind) {
            m.put(k(x, y, z), kind);
            return this;
        }

        @Override
        public int kind(int x, int y, int z) {
            Integer v = m.get(k(x, y, z));
            return v == null ? com.sc.bridge.BridgeStructureSC.K_AIR : v;
        }

        /** A ring of `size` along `axis` on the controller at 0 64 0, the controller, an energy and a gas port, a capacitor. */
        static FakeBridge ring(int size, int axis) {
            FakeBridge f = new FakeBridge();
            int h = (size - 1) / 2;
            for (int v = 1; v <= size; v++) {
                for (int u = -h; u <= h; u++) {
                    if (com.sc.bridge.BridgeStructureSC.isRing(size, u, v)) {
                        int[] p = com.sc.bridge.BridgeStructureSC.at(0, 64, 0, axis, u, v, 0);
                        f.put(p[0], p[1], p[2], com.sc.bridge.BridgeStructureSC.K_COIL);
                    }
                }
            }
            f.put(0, 64, 0, com.sc.bridge.BridgeStructureSC.K_CONTROLLER);
            int[] e = com.sc.bridge.BridgeStructureSC.at(0, 64, 0, axis, 1, 0, 0), g = com.sc.bridge.BridgeStructureSC.at(0, 64, 0, axis, 2, 0, 0),
                    c = com.sc.bridge.BridgeStructureSC.at(0, 64, 0, axis, -1, 0, 0);
            f.put(e[0], e[1], e[2], com.sc.bridge.BridgeStructureSC.K_ENERGY_PORT);
            f.put(g[0], g[1], g[2], com.sc.bridge.BridgeStructureSC.K_GAS_PORT);
            f.put(c[0], c[1], c[2], com.sc.bridge.BridgeStructureSC.K_CAPACITOR);
            if (size == 7) {
                for (int[] uv : new int[][]{{-4, 0}, {4, 0}, {-4, 8}, {4, 8}}) {
                    int[] p = com.sc.bridge.BridgeStructureSC.at(0, 64, 0, axis, uv[0], uv[1], 0);
                    f.put(p[0], p[1], p[2], com.sc.bridge.BridgeStructureSC.K_FOCUSER);
                }
            }
            return f;
        }
    }

    /** A fake world of place-check cells: stone below y 64, air above, with exceptions. */
    private static final class FakeCells implements com.sc.bridge.BridgeSpaceSC.Cells {
        final java.util.Map<Long, Integer> m = new java.util.HashMap<Long, Integer>();
        final java.util.Map<Long, Integer> tops = new java.util.HashMap<Long, Integer>();
        int ground = 64;

        FakeCells put(int x, int y, int z, int c) {
            m.put(FakeBridge.k(x, y, z), c);
            long col = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
            Integer t = tops.get(col);
            if (c != com.sc.bridge.BridgeSpaceSC.AIR && (t == null || y > t)) {
                tops.put(col, y);
            }
            return this;
        }

        @Override
        public int cell(int x, int y, int z) {
            Integer v = m.get(FakeBridge.k(x, y, z));
            return v != null ? v : y < ground ? com.sc.bridge.BridgeSpaceSC.SOLID : com.sc.bridge.BridgeSpaceSC.AIR;
        }

        @Override
        public int top(int x, int z) {
            Integer t = tops.get(((long) x << 32) ^ (z & 0xFFFFFFFFL));
            return t == null ? ground - 1 : Math.max(ground - 1, t);
        }

        @Override
        public int height() {
            return 256;
        }
    }

    private static void bridge() {
        // the build check
        FakeBridge f = FakeBridge.ring(5, 0);
        f.put(-1, 63, 0, com.sc.bridge.BridgeStructureSC.K_NAV);                // a module under the capacitor (the chain)
        f.put(-3, 66, 0, com.sc.bridge.BridgeStructureSC.K_STABILISER);         // beside the ring: counts
        f.put(9, 64, 0, com.sc.bridge.BridgeStructureSC.K_STABILISER);          // too far: doesn't
        f.put(6, 64, 0, com.sc.bridge.BridgeStructureSC.K_CAPACITOR);           // not touching anything: doesn't
        com.sc.bridge.BridgeStructureSC.Scan s = com.sc.bridge.BridgeStructureSC.scan(f, 0, 64, 0);
        check(s.valid && s.kind == com.sc.bridge.BridgeMathSC.GROUND && s.axis == 0 && s.size == 5 && s.coils == 16 && s.coilsNeeded == 16,
                "bridge: a 5x5 ring along X is a valid Ground bridge (" + s.problems.size() + " problems)");
        check(s.capacitors.size() == 1 && s.energyPorts.size() == 1 && s.gasPorts.size() == 1 && s.nav && !s.mass && s.stabCount() == 1,
                "bridge: parts by the chain of contacts - 1 capacitor, the ports, the nav module; 1 stabiliser in range");
        com.sc.bridge.BridgeStructureSC.Scan z = com.sc.bridge.BridgeStructureSC.scan(FakeBridge.ring(5, 1), 0, 64, 0);
        check(z.valid && z.axis == 1 && s.signature != z.signature && s.signature != 0,
                "bridge: the ring along Z is valid too, with another calibration signature");
        com.sc.bridge.BridgeStructureSC.Scan again = com.sc.bridge.BridgeStructureSC.scan(f, 0, 64, 0);
        check(again.signature == s.signature, "bridge: the same ring - the same signature (calibration kept)");
        FakeBridge miss = FakeBridge.ring(5, 0).put(2, 67, 0, com.sc.bridge.BridgeStructureSC.K_AIR);
        com.sc.bridge.BridgeStructureSC.Scan ms = com.sc.bridge.BridgeStructureSC.scan(miss, 0, 64, 0);
        com.sc.bridge.BridgeStructureSC.Problem p0 = ms.problems.isEmpty() ? null : ms.problems.get(0);
        check(!ms.valid && ms.coils == 15 && p0 != null && "sc.bridge.problem.coil".equals(p0.key) && p0.hasPos && p0.x == 2 && p0.y == 67 && p0.z == 0
                && ms.cells[3 * 7 + 5] == com.sc.bridge.BridgeStructureSC.C_COIL_MISSING && ms.signature != s.signature,
                "bridge: a missing coil is reported with its block (highlight) and red on the schematic");
        com.sc.bridge.BridgeStructureSC.Scan junk = com.sc.bridge.BridgeStructureSC.scan(FakeBridge.ring(5, 0).put(0, 66, 0, com.sc.bridge.BridgeStructureSC.K_OTHER), 0, 64, 0);
        com.sc.bridge.BridgeStructureSC.Scan side = com.sc.bridge.BridgeStructureSC.scan(FakeBridge.ring(5, 0).put(0, 66, 2, com.sc.bridge.BridgeStructureSC.K_OTHER), 0, 64, 0);
        check(!junk.valid && junk.junk == 1 && !side.valid && side.sideBlocked == 1 && "sc.bridge.problem.side".equals(side.problems.get(0).key),
                "bridge: a block inside the ring / in the 2 blocks in front of it is a problem");
        FakeBridge noPort = FakeBridge.ring(5, 0).put(1, 64, 0, com.sc.bridge.BridgeStructureSC.K_AIR).put(5, 64, 0, com.sc.bridge.BridgeStructureSC.K_ENERGY_PORT);
        com.sc.bridge.BridgeStructureSC.Scan np = com.sc.bridge.BridgeStructureSC.scan(noPort, 0, 64, 0);
        boolean noEp = false;
        for (com.sc.bridge.BridgeStructureSC.Problem p : np.problems) {
            noEp |= "sc.bridge.problem.noenergyport".equals(p.key);
        }
        check(!np.valid && noEp, "bridge: an energy port not touching the chain doesn't count");
        com.sc.bridge.BridgeStructureSC.Scan sp = com.sc.bridge.BridgeStructureSC.scan(FakeBridge.ring(7, 0), 0, 64, 0);
        com.sc.bridge.BridgeStructureSC.Scan spf = com.sc.bridge.BridgeStructureSC.scan(FakeBridge.ring(7, 0).put(4, 72, 0, com.sc.bridge.BridgeStructureSC.K_AIR), 0, 64, 0);
        check(sp.valid && sp.kind == com.sc.bridge.BridgeMathSC.SPACE && sp.coils == 24 && sp.focusers == 4 && !spf.valid && spf.focusers == 3
                && "sc.bridge.problem.focuser".equals(spf.problems.get(0).key) && sp.signature != s.signature,
                "bridge: a 7x7 ring with 4 focusers is a Space bridge; a missing focuser is a problem");
        com.sc.bridge.BridgeStructureSC.Scan none = com.sc.bridge.BridgeStructureSC.scan(new FakeBridge(), 0, 64, 0);
        check(!none.found && !none.valid && "sc.bridge.problem.noring".equals(none.problems.get(0).key), "bridge: no coils - no ring");
        // the cost (§4-§5)
        com.sc.bridge.BridgeMathSC.Cost g = com.sc.bridge.BridgeMathSC.cost(com.sc.bridge.BridgeMathSC.GROUND, new long[]{1000}, false, false, 0);
        com.sc.bridge.BridgeMathSC.Cost gb = com.sc.bridge.BridgeMathSC.cost(com.sc.bridge.BridgeMathSC.GROUND, new long[]{1000}, true, false, 4);
        com.sc.bridge.BridgeMathSC.Cost two = com.sc.bridge.BridgeMathSC.cost(com.sc.bridge.BridgeMathSC.GROUND, new long[]{1000, 3000}, false, false, 0);
        check(g.eu == 220000000L && g.sm == 60 && g.kr == 25 && g.d == 100 && g.ar == 50 && g.holdEu == 50000 && g.heSec == 10 && g.arSec == 2
                && g.d2oSec == 0 && g.lifeTicks == 600, "bridge cost, Ground 1000 blocks: 220 M EU, SM 60, Kr 25, D 100, Ar 50; hold 50 000 EU/t; 30 s");
        check(gb.eu == 132000000L && gb.sm == 36 && gb.kr == 15 && gb.lifeTicks == 1200 && two.eu == 280000000L && two.kr == 40,
                "bridge cost: a beacon -40%, 4 stabilisers - 60 s; two projected ends pay both distances");
        com.sc.bridge.BridgeMathSC.Cost sn = com.sc.bridge.BridgeMathSC.cost(com.sc.bridge.BridgeMathSC.SPACE, new long[]{50000}, false, false, 2);
        com.sc.bridge.BridgeMathSC.Cost sa = com.sc.bridge.BridgeMathSC.cost(com.sc.bridge.BridgeMathSC.SPACE, new long[]{50000}, false, true, 4);
        check(sn.eu == 4000000000L && sn.sm == 500 && sn.kr == 200 && sn.d == 1000 && sn.ar == 300 && sn.d2oSec == 5 && sn.holdEu == 200000
                && sn.lifeTicks == 600 && sa.eu == 2000000000L && sa.sm == 200 && sa.kr == 0 && sa.lifeTicks == 800,
                "bridge cost, Space: 4 G EU without an anchor (2 G with), SM 500 / 200, Kr 200 / 0, D2O 5/s; 20 s (+25% a stabiliser)");
        // capacitors
        long[] cap = new long[2];
        long in = com.sc.bridge.BridgeMathSC.charge(cap, 700000000L);
        boolean over = com.sc.bridge.BridgeMathSC.charge(cap, 600000000L) == 300000000L;
        boolean d1 = com.sc.bridge.BridgeMathSC.drain(cap, 600000000L);
        boolean d2 = com.sc.bridge.BridgeMathSC.drain(cap, 500000000L);
        check(in == 700000000L && over && d1 && cap[0] == 400000000L && cap[1] == 0 && !d2 && com.sc.bridge.BridgeMathSC.total(cap) == 400000000L,
                "bridge capacitors: 500 M each, charged first to last, drained last to first, all-or-nothing");
        check(com.sc.bridge.BridgeMathSC.tankCapacity(com.sc.bridge.BridgeMathSC.HE, 1) == 32000
                && com.sc.bridge.BridgeMathSC.tankCapacity(com.sc.bridge.BridgeMathSC.HE, 3) == 64000
                && com.sc.bridge.BridgeMathSC.tankCapacity(com.sc.bridge.BridgeMathSC.KR, 0) == 8000,
                "bridge tanks: He 32 000, +50% for each gas port beyond the first");
        check(com.sc.bridge.BridgeMathSC.stabilityStep(100, 100, true) == 85 && com.sc.bridge.BridgeMathSC.stabilityStep(50, 90, false) == 55
                && com.sc.bridge.BridgeMathSC.baseStability(2) == 90 && com.sc.bridge.BridgeMathSC.shortEu(1040000000L, "M", "G").equals("1,04 G")
                && com.sc.bridge.BridgeMathSC.group(-3880).equals("-3 880"), "bridge stability steps and the screen's numbers");
        // the place check (§7а) on a mock world
        FakeCells c = new FakeCells();
        com.sc.bridge.BridgeSpaceSC.Result free = com.sc.bridge.BridgeSpaceSC.check(c, 0, 64, 0, 3, 0);
        com.sc.bridge.BridgeSpaceSC.Result small = com.sc.bridge.BridgeSpaceSC.check(new FakeCells().put(1, 66, 1, com.sc.bridge.BridgeSpaceSC.SOLID), 0, 64, 0, 3, 0);
        com.sc.bridge.BridgeSpaceSC.Result lava = com.sc.bridge.BridgeSpaceSC.check(new FakeCells().put(1, 63, 0, com.sc.bridge.BridgeSpaceSC.LAVA), 0, 64, 0, 3, 0);
        com.sc.bridge.BridgeSpaceSC.Result water = com.sc.bridge.BridgeSpaceSC.check(new FakeCells().put(0, 64, 1, com.sc.bridge.BridgeSpaceSC.WATER), 0, 64, 0, 3, 0);
        com.sc.bridge.BridgeSpaceSC.Result inside = com.sc.bridge.BridgeSpaceSC.check(c, 0, 60, 0, 3, 0);
        com.sc.bridge.BridgeSpaceSC.Result air = com.sc.bridge.BridgeSpaceSC.check(c, 0, 70, 0, 3, 0);
        com.sc.bridge.BridgeSpaceSC.Result voidR = com.sc.bridge.BridgeSpaceSC.check(c, 0, 0, 0, 3, 0);
        com.sc.bridge.BridgeSpaceSC.Result high = com.sc.bridge.BridgeSpaceSC.check(c, 0, 254, 0, 3, 0);
        com.sc.bridge.BridgeSpaceSC.Result top = com.sc.bridge.BridgeSpaceSC.check(c, 0, 253, 0, 3, 0);
        com.sc.bridge.BridgeSpaceSC.Result inLava = com.sc.bridge.BridgeSpaceSC.check(new FakeCells().put(0, 75, 1, com.sc.bridge.BridgeSpaceSC.LAVA), 0, 74, 0, 3, 0);
        com.sc.bridge.BridgeSpaceSC.Result airWater = com.sc.bridge.BridgeSpaceSC.check(new FakeCells().put(-1, 76, 0, com.sc.bridge.BridgeSpaceSC.WATER), 0, 74, 0, 3, 0);
        com.sc.bridge.BridgeSpaceSC.Result airPart = com.sc.bridge.BridgeSpaceSC.check(new FakeCells().put(1, 76, 1, com.sc.bridge.BridgeSpaceSC.SOLID), 0, 74, 0, 3, 0);
        com.sc.bridge.BridgeSpaceSC.Result space = com.sc.bridge.BridgeSpaceSC.check(c, 0, 90, 0, 5, 1);
        FakeCells voidW = new FakeCells();
        voidW.ground = 0;
        com.sc.bridge.BridgeSpaceSC.Result overVoid = com.sc.bridge.BridgeSpaceSC.check(voidW, 0, 100, 0, 3, 0);
        check(free.free && free.onGround && !free.inAir && !free.voidBelow && !small.free && "sc.bridge.place.small".equals(small.reason)
                && "sc.bridge.place.overlava".equals(lava.reason) && "sc.bridge.place.water".equals(water.reason) && "sc.bridge.place.inside".equals(inside.reason)
                && air.free && air.inAir && !air.onGround && !air.voidBelow && "sc.bridge.place.low".equals(voidR.reason)
                && "sc.bridge.place.high".equals(high.reason) && top.free && "sc.bridge.place.lava".equals(inLava.reason)
                && "sc.bridge.place.water".equals(airWater.reason) && "sc.bridge.place.small".equals(airPart.reason) && space.free && space.inAir
                && overVoid.free && overVoid.inAir && overVoid.voidBelow,
                "bridge place check (§7б): free on the ground (onGround) and in the air (inAir, no floor needed); a block, lava, water in the volume, "
                        + "y < 1, the top over the ceiling refused; 5x5x2 in the air free; over the Void free with the void warning");
        FakeCells tower = new FakeCells().put(0, 80, 0, com.sc.bridge.BridgeSpaceSC.SOLID);
        int ay = com.sc.bridge.BridgeSpaceSC.autoY(tower, 0, 0, 3, 0, com.sc.bridge.BridgeSpaceSC.AUTO_DEPTH);
        com.sc.bridge.BridgeSpaceSC.Result auto = com.sc.bridge.BridgeSpaceSC.probe(c, 5, Integer.MIN_VALUE, 5, 3, 0, 16);
        check(ay == 64 && auto.free && auto.y == 64, "bridge «Y авто»: the topmost free floor (a lone block on top is no room) - y " + ay);
        FakeCells voidCol = new FakeCells();
        voidCol.ground = 0;
        com.sc.bridge.BridgeSpaceSC.Result autoVoid = com.sc.bridge.BridgeSpaceSC.probe(voidCol, 0, Integer.MIN_VALUE, 0, 3, 0, 4);
        check(com.sc.bridge.BridgeSpaceSC.autoY(voidCol, 0, 0, 3, 0, com.sc.bridge.BridgeSpaceSC.AUTO_DEPTH) == -1 && !autoVoid.free
                        && com.sc.bridge.BridgeSpaceSC.autoY(c, 0, 0, 3, 0, com.sc.bridge.BridgeSpaceSC.AUTO_DEPTH) == 64,
                "bridge «Y авто» (§7б) still wants a surface: none over the Void, the ground's top otherwise");
        // §7б: the nearest free place in the air - a floating rock 7x7x7: the same height beside it, not over it
        FakeCells rock = new FakeCells();
        rock.ground = 0;
        for (int x = -3; x <= 3; x++) {
            for (int y = 100; y <= 106; y++) {
                for (int rz = -3; rz <= 3; rz++) {
                    rock.put(x, y, rz, com.sc.bridge.BridgeSpaceSC.SOLID);
                }
            }
        }
        com.sc.bridge.BridgeSpaceSC.Result nearAir = com.sc.bridge.BridgeSpaceSC.probe(rock, 0, 100, 0, 3, 0, 16);
        com.sc.bridge.BridgeSpaceSC.Result nearAirOk = nearAir.hasNearest ? com.sc.bridge.BridgeSpaceSC.check(rock, nearAir.nx, nearAir.ny, nearAir.nz, 3, 0) : null;
        FakeCells slab = new FakeCells();
        slab.ground = 0;
        for (int x = -4; x <= 4; x++) {
            for (int sz = -4; sz <= 4; sz++) {
                slab.put(x, 50, sz, com.sc.bridge.BridgeSpaceSC.SOLID);
            }
        }
        com.sc.bridge.BridgeSpaceSC.Result inSlab = com.sc.bridge.BridgeSpaceSC.probe(slab, 0, 50, 0, 3, 0, 16);
        check(!nearAir.free && nearAirOk != null && nearAirOk.free && nearAirOk.inAir && nearAir.ny == 100 && nearAir.nDist <= 5
                        && !inSlab.free && inSlab.hasNearest && inSlab.ny == 51 && inSlab.nx == 0 && inSlab.nz == 0 && inSlab.nDist == 1,
                "bridge nearest free place (§7б): in the air at the same height beside a floating rock (" + nearAir.nx + " " + nearAir.ny + " " + nearAir.nz
                        + ", " + nearAir.nDist + " blocks); inside a thin slab - one up, onto it (" + inSlab.nx + " " + inSlab.ny + " " + inSlab.nz + ")");
        FakeCells wall = new FakeCells();
        for (int x = -3; x <= 3; x++) {
            for (int y = 64; y <= 70; y++) {
                wall.put(x, y, 0, com.sc.bridge.BridgeSpaceSC.SOLID);
            }
        }
        com.sc.bridge.BridgeSpaceSC.Result near = com.sc.bridge.BridgeSpaceSC.probe(wall, 0, 64, 0, 3, 0, 16);
        com.sc.bridge.BridgeSpaceSC.Result nearOk = near.hasNearest ? com.sc.bridge.BridgeSpaceSC.check(wall, near.nx, near.ny, near.nz, 3, 0) : null;
        check(!near.free && near.hasNearest && nearOk != null && nearOk.free && near.nDist <= 2,
                "bridge: inside a wall - the nearest free place found (" + near.nx + " " + near.ny + " " + near.nz + ", " + near.nDist + " blocks)");
        // NBT round-trips
        com.sc.tileentity.TileEntityBridgeControllerSC a = new com.sc.tileentity.TileEntityBridgeControllerSC();
        a.setOwner("Tester");
        a.setTarget(1240, com.sc.tileentity.TileEntityBridgeControllerSC.AUTO_Y, -3880, 0);
        a.putTankForTest(com.sc.bridge.BridgeMathSC.SM, 4200);
        a.putTankForTest(com.sc.bridge.BridgeMathSC.HE, 777);
        a.action(null, com.sc.tileentity.TileEntityBridgeControllerSC.A_BM_ADD, new int[]{1, 2, 3, 0}, "Дом");
        a.action(null, com.sc.tileentity.TileEntityBridgeControllerSC.A_BM_ADD, new int[]{10, 20, 30, -1}, "Крепость");
        a.action(null, com.sc.tileentity.TileEntityBridgeControllerSC.A_BM_RENAME, new int[]{1}, "Крепость Нижнего");
        net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
        a.writeToNBT(tag);
        com.sc.tileentity.TileEntityBridgeControllerSC b = new com.sc.tileentity.TileEntityBridgeControllerSC();
        b.readFromNBT(tag);
        int[] bt = b.getTarget();
        check("Tester".equals(b.getOwner()) && bt[0] == 1240 && bt[1] == com.sc.tileentity.TileEntityBridgeControllerSC.AUTO_Y && bt[2] == -3880
                && b.tankAmount(com.sc.bridge.BridgeMathSC.SM) == 4200 && b.tankAmount(com.sc.bridge.BridgeMathSC.HE) == 777
                && b.getBookmarks().size() == 2 && "Крепость Нижнего".equals(b.getBookmarks().get(1).getString("n")) && !b.isOpen(),
                "bridge controller NBT: owner, target («авто»), tanks, bookmarks kept");
        net.minecraft.nbt.NBTTagCompound item = a.writeToItem();
        com.sc.tileentity.TileEntityBridgeControllerSC fromItem = new com.sc.tileentity.TileEntityBridgeControllerSC();
        fromItem.readFromItem(item);
        check(fromItem.tankAmount(com.sc.bridge.BridgeMathSC.SM) == 4200 && fromItem.getBookmarks().size() == 2 && "".equals(fromItem.getOwner()),
                "bridge controller item: keeps the tanks and bookmarks (not the owner)");
        com.sc.tileentity.TileEntityBridgeCapacitorSC ca = new com.sc.tileentity.TileEntityBridgeCapacitorSC();
        ca.setEnergy(123456789L);
        ca.link(new int[]{4, 5, 6});
        net.minecraft.nbt.NBTTagCompound ct = new net.minecraft.nbt.NBTTagCompound();
        ca.writeToNBT(ct);
        com.sc.tileentity.TileEntityBridgeCapacitorSC cb = new com.sc.tileentity.TileEntityBridgeCapacitorSC();
        cb.readFromNBT(ct);
        com.sc.tileentity.TileEntityBridgeCapacitorSC cc = new com.sc.tileentity.TileEntityBridgeCapacitorSC();
        cc.setEnergy(com.sc.bridge.BridgeMathSC.CAPACITOR_EU * 3);
        check(cb.getEnergy() == 123456789L && cb.controllerPos() != null && cb.controllerPos()[1] == 5 && cc.getEnergy() == com.sc.bridge.BridgeMathSC.CAPACITOR_EU,
                "bridge capacitor NBT: charge and link kept; never over 500 M");
        com.sc.bridge.BridgeMsgSC msg = new com.sc.bridge.BridgeMsgSC("sc.bridge.refuse.missing").part("sc.bridge.need.cap", "1 200").part("sc.bridge.need.gas", "@gas.krypton", 40, 2);
        com.sc.bridge.BridgeMsgSC back = com.sc.bridge.BridgeMsgSC.read(msg.write());
        check(back.key.equals(msg.key) && back.parts.size() == 2 && back.parts.get(1).args.length == 3 && "@gas.krypton".equals(back.parts.get(1).args[0]),
                "bridge messages: key, arguments and parts through NBT (journal, screen)");
        com.sc.bridge.BridgeMarksSC marks = new com.sc.bridge.BridgeMarksSC(com.sc.bridge.BridgeMarksSC.NAME);
        marks.add(com.sc.bridge.BridgeMarksSC.BEACON, 0, 100, 64, 100);
        marks.add(com.sc.bridge.BridgeMarksSC.ANCHOR, -1, 0, 64, 0);
        net.minecraft.nbt.NBTTagCompound mt = new net.minecraft.nbt.NBTTagCompound();
        marks.writeToNBT(mt);
        com.sc.bridge.BridgeMarksSC m2 = new com.sc.bridge.BridgeMarksSC(com.sc.bridge.BridgeMarksSC.NAME);
        m2.readFromNBT(mt);
        check(m2.count() == 2 && m2.near(com.sc.bridge.BridgeMarksSC.BEACON, 0, 104, 64, 104, 8) && !m2.near(com.sc.bridge.BridgeMarksSC.BEACON, 0, 120, 64, 100, 8)
                && m2.near(com.sc.bridge.BridgeMarksSC.BEACON, 0, 105, Integer.MIN_VALUE, 100, 8) && m2.any(com.sc.bridge.BridgeMarksSC.ANCHOR, -1)
                && !m2.any(com.sc.bridge.BridgeMarksSC.ANCHOR, 1), "bridge beacons / anchors: kept, found near a target or in a dimension");
        check(com.sc.block.BlockBridgeSC.parts() == 11 && com.sc.block.BlockBridgeSC.kindOf(com.sc.block.BlockBridgeSC.FOCUSER)
                == com.sc.bridge.BridgeStructureSC.K_FOCUSER, "bridge blocks: 11 parts in metadata order");
    }
    /** The first registered crafting recipe matching a 3x3 grid of `rows` (chars mapped by `map`), or null; result in out[0]. */
    private static net.minecraft.item.crafting.IRecipe craft3(ItemStack[] out, String[] rows, Object... map) {
        java.util.Map<Character, ItemStack> m = new java.util.HashMap<Character, ItemStack>();
        for (int i = 0; i + 1 < map.length; i += 2) {
            m.put((Character) map[i], (ItemStack) map[i + 1]);
        }
        net.minecraft.inventory.InventoryCrafting grid = new net.minecraft.inventory.InventoryCrafting(new net.minecraft.inventory.Container() {
            @Override
            public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer p) {
                return true;
            }
        }, 3, 3);
        for (int r = 0; r < 3 && r < rows.length; r++) {
            for (int c = 0; c < 3 && c < rows[r].length(); c++) {
                ItemStack st = m.get(rows[r].charAt(c));
                grid.setInventorySlotContents(r * 3 + c, st == null ? null : st.copy());
            }
        }
        out[0] = null;
        for (Object o : net.minecraft.item.crafting.CraftingManager.getInstance().getRecipeList()) {
            net.minecraft.item.crafting.IRecipe rec = (net.minecraft.item.crafting.IRecipe) o;
            boolean hit;
            try {
                hit = rec.matches(grid, null);
            } catch (Throwable t) {
                hit = false;
            }
            if (hit) {
                out[0] = rec.getCraftingResult(grid);
                return rec;
            }
        }
        return null;
    }

    private static boolean made(ItemStack got, net.minecraft.item.Item item, int meta, int count) {
        return got != null && got.getItem() == item && got.getItemDamage() == meta && got.stackSize == count;
    }

    /** The bridge's crafts (approved): each part and item, charge carried, the coordinator copy, the second gravity-coil recipe, the book. */
    /** ВП: the vortex's look in numbers - the colour by stability, the open / close sizes, the rim's wave. */
    private static void bridgeVortexLook() {
        float[] g = com.sc.bridge.BridgeVortexMathSC.colour(com.sc.bridge.BridgeMathSC.GROUND, 80);
        float[] sp = com.sc.bridge.BridgeVortexMathSC.colour(com.sc.bridge.BridgeMathSC.SPACE, 80);
        float[] g60 = com.sc.bridge.BridgeVortexMathSC.colour(com.sc.bridge.BridgeMathSC.GROUND, 60);
        float[] g45 = com.sc.bridge.BridgeVortexMathSC.colour(com.sc.bridge.BridgeMathSC.GROUND, 45);
        float[] g30 = com.sc.bridge.BridgeVortexMathSC.colour(com.sc.bridge.BridgeMathSC.GROUND, 30);
        float[] g20 = com.sc.bridge.BridgeVortexMathSC.colour(com.sc.bridge.BridgeMathSC.GROUND, 20);
        float[] s20 = com.sc.bridge.BridgeVortexMathSC.colour(com.sc.bridge.BridgeMathSC.SPACE, 20);
        check(g[1] > g[0] && g[1] > g[2], "vortex: a stable Ground vortex is green");
        check(sp[2] > sp[1] && sp[0] > sp[1], "vortex: a stable Space vortex is blue-violet");
        check(java.util.Arrays.equals(g, g60), "vortex: at 60% still the kind's own colour");
        check(g45[0] > g60[0] && g45[2] < g60[2], "vortex: under 60% it turns towards yellow");
        check(g30[0] > g45[0] && g30[0] >= 0.99F && g30[1] > 0.8F, "vortex: at 30% fully yellow");
        check(g20[0] >= 0.99F && g20[1] < 0.6F && g20[2] < 0.2F && java.util.Arrays.equals(g20, s20), "vortex: under 30% orange (either kind)");
        check(com.sc.bridge.BridgeVortexMathSC.flicker(80, 12345) == 1F, "vortex: no flicker while stable");
        boolean varies = false, inRange = true;
        for (long ms = 0; ms < 3000; ms += 60) {
            float f = com.sc.bridge.BridgeVortexMathSC.flicker(20, ms);
            inRange &= f >= 0.6F && f <= 1F;
            varies |= Math.abs(f - com.sc.bridge.BridgeVortexMathSC.flicker(20, 0)) > 0.05F;
        }
        check(inRange && varies, "vortex: under 30% it flickers within 0.6-1");
        boolean grows = true;
        float prev = -1;
        for (int t = 0; t <= com.sc.bridge.BridgeVortexMathSC.OPEN_TICKS; t++) {
            float s = com.sc.bridge.BridgeVortexMathSC.openScale(t);
            grows &= s > prev && s >= 0F && s <= 1F;
            prev = s;
        }
        check(com.sc.bridge.BridgeVortexMathSC.openScale(0) == 0F && grows && com.sc.bridge.BridgeVortexMathSC.openScale(100) == 1F,
                "vortex: opening grows from a point to full size in 0.5 s");
        check(com.sc.bridge.BridgeVortexMathSC.openFlash(0) == 1F && com.sc.bridge.BridgeVortexMathSC.openFlash(10) == 0F,
                "vortex: the opening flash fades over the growth");
        check(com.sc.bridge.BridgeVortexMathSC.closeScale(0) == 1F && com.sc.bridge.BridgeVortexMathSC.closeScale(200) < 1F
                && com.sc.bridge.BridgeVortexMathSC.closeScale(200) > 0F && com.sc.bridge.BridgeVortexMathSC.closeScale(400) == 0F,
                "vortex: collapsing shrinks to a point in 0.4 s");
        boolean same = true, bounded = true, periodic = true, differs = false;
        for (int i = 0; i < 64; i++) {
            double th = i * 0.3, t = i * 0.17;
            float a = com.sc.bridge.BridgeVortexMathSC.rimNoise(th, t, 42L);
            same &= a == com.sc.bridge.BridgeVortexMathSC.rimNoise(th, t, 42L);
            bounded &= a >= -1F && a <= 1F;
            periodic &= Math.abs(a - com.sc.bridge.BridgeVortexMathSC.rimNoise(th + Math.PI * 2, t, 42L)) < 1e-4F;
            differs |= Math.abs(a - com.sc.bridge.BridgeVortexMathSC.rimNoise(th, t, 987654321L)) > 1e-3F;
        }
        check(same && bounded, "vortex: the rim's wave is deterministic and within -1..1");
        check(periodic, "vortex: the rim closes on itself (no seam at 360 degrees)");
        check(differs, "vortex: two vortices wave differently");
    }

    private static void bridgeRecipes() {
        ItemStack X = ModItems.siliconMaterial.stackOf(com.sc.util.SiliconMaterial.CONTROLLER), P = ModItems.siliconMaterial.stackOf(com.sc.util.SiliconMaterial.MEMORY_CHIP),
                T = new ItemStack(ModItems.component("tiPlate")), K = new ItemStack(ModItems.component("tiCasing")), G = ModItems.ingot.stackOf(Material.HAFNIUM),
                E = new ItemStack(com.sc.init.ModBlocks.cableSC, 1, com.sc.energy.CableType.EXO.ordinal()),
                S = new ItemStack(com.sc.init.ModBlocks.cableSC, 1, com.sc.energy.CableType.SINGULAR.ordinal()),
                N = new ItemStack(ModItems.component("nb3SnPlate")), M = new ItemStack(ModItems.component("matterCapsule")),
                D = new ItemStack(ModItems.component("sensor")), O = new ItemStack(net.minecraft.init.Items.ender_eye),
                Z = new ItemStack(net.minecraft.init.Items.nether_star), B = new ItemStack(net.minecraft.init.Blocks.obsidian),
                cmp = new ItemStack(net.minecraft.init.Items.compass), coil = new ItemStack(com.sc.init.ModBlocks.gravityCoil);
        net.minecraft.item.Item br = net.minecraft.item.Item.getItemFromBlock(com.sc.init.ModBlocks.bridge);
        ItemStack[] o = new ItemStack[1];
        String bad = "";
        craft3(o, new String[]{"XPX", "EOE", "GKG"}, 'X', X, 'P', P, 'E', E, 'O', O, 'G', G, 'K', K);
        bad += made(o[0], br, com.sc.block.BlockBridgeSC.CONTROLLER, 1) ? "" : " controller";
        craft3(o, new String[]{"TET", "EXE", "TET"}, 'T', T, 'E', E, 'X', X);
        bad += made(o[0], br, com.sc.block.BlockBridgeSC.ENERGY_PORT, 1) ? "" : " energyPort";
        craft3(o, new String[]{"TPT", "PBP", "TPT"}, 'T', T, 'P', new ItemStack(com.sc.init.ModBlocks.pipeSC, 1, com.sc.util.PipeType.TITANIUM.ordinal()),
                'B', new ItemStack(com.sc.init.ModBlocks.tankSC, 1, 0));
        bad += made(o[0], br, com.sc.block.BlockBridgeSC.GAS_PORT, 1) ? "" : " gasPort";
        craft3(o, new String[]{"NZN", "SMS", "NZN"}, 'N', N, 'Z', Z, 'S', S, 'M', M);
        bad += made(o[0], br, com.sc.block.BlockBridgeSC.FOCUSER, 2) ? "" : " focuser";
        craft3(o, new String[]{"PCP", "DXD", "TTT"}, 'P', P, 'C', cmp, 'D', D, 'X', X, 'T', T);
        bad += made(o[0], br, com.sc.block.BlockBridgeSC.NAV, 1) ? "" : " nav";
        craft3(o, new String[]{"NGN", "GCG", "NGN"}, 'N', N, 'G', G, 'C', coil);
        bad += made(o[0], br, com.sc.block.BlockBridgeSC.MASS, 1) ? "" : " mass";
        craft3(o, new String[]{"CLC", "LHL", "CLC"}, 'C', new ItemStack(ModItems.component("copperCoil")), 'L', new ItemStack(ModItems.component("ptfeSheet")),
                'H', new ItemStack(ModItems.component("heLoopModule")));
        bad += made(o[0], br, com.sc.block.BlockBridgeSC.COOLER, 1) ? "" : " cooler";
        craft3(o, new String[]{"BFB", "DXD", "BBB"}, 'B', B, 'F', new ItemStack(ModItems.component("focusLens")), 'D', D, 'X', X);
        bad += made(o[0], br, com.sc.block.BlockBridgeSC.SHIELD, 1) ? "" : " shield";
        craft3(o, new String[]{"LOL", "EXE", "TTT"}, 'L', new ItemStack(net.minecraft.init.Items.glowstone_dust), 'O', O, 'E', E, 'X', X, 'T', T);
        bad += made(o[0], br, com.sc.block.BlockBridgeSC.BEACON, 1) ? "" : " beacon";
        craft3(o, new String[]{"BOB", "SMS", "BGB"}, 'B', B, 'O', O, 'S', S, 'M', M, 'G', G);
        bad += made(o[0], br, com.sc.block.BlockBridgeSC.ANCHOR, 1) ? "" : " anchor";
        craft3(o, new String[]{"TRT", "GCG", "TRT"}, 'T', T, 'R', new ItemStack(net.minecraft.init.Items.redstone),
                'G', new ItemStack(net.minecraft.init.Blocks.glass_pane), 'C', cmp);
        bad += made(o[0], ModItems.coordinator, 0, 1) && com.sc.bridge.BridgeItemDataSC.point(o[0]) == null ? "" : " coordinator";
        craft3(o, new String[]{"GOG", "PXP", "GSG"}, 'G', G, 'O', O, 'P', P, 'X', X, 'S', S);
        bad += made(o[0], ModItems.bridgeLinkModule, 0, 1) ? "" : " linkModule";
        // G2: the gravity coil with Exo cable (1), the original with Singular cable still there (2)
        ItemStack tc = new ItemStack(com.sc.init.ModBlocks.tokamakCoil), fc = new ItemStack(ModItems.component("fusionCore"));
        craft3(o, new String[]{"HSH", "KFK", "HSH"}, 'H', G, 'S', E, 'K', tc, 'F', fc);
        bad += made(o[0], coil.getItem(), 0, 1) ? "" : " coilExo";
        craft3(o, new String[]{"HSH", "KFK", "HSH"}, 'H', G, 'S', S, 'K', tc, 'F', fc);
        bad += made(o[0], coil.getItem(), 0, 2) ? "" : " coilSingular";
        check(bad.isEmpty(), "bridge recipes: 10 parts, coordinator, link module and both gravity-coil recipes registered with the right result" + bad);

        // the capacitor round a QV cell, the remotes round an EV cell / a Bridge Remote and an XV core: charge carried (capped)
        ItemStack qv = new ItemStack(ModItems.battery, 1, 4), ev = new ItemStack(ModItems.battery, 1, 3), xv = new ItemStack(ModItems.battery, 1, 5),
                tan = new ItemStack(ModItems.component("tantalumCapacitor"));
        com.sc.item.ItemBatterySC.setCharge(qv, 123456789L);
        craft3(o, new String[]{"GCG", "CQC", "GCG"}, 'G', G, 'C', tan, 'Q', qv);
        boolean capOk = made(o[0], br, com.sc.block.BlockBridgeSC.CAPACITOR, 1) && o[0].hasTagCompound() && o[0].getTagCompound().getLong("BridgeEU") == 123456789L;
        craft3(o, new String[]{"GCG", "CQC", "GCG"}, 'G', G, 'C', tan, 'Q', new ItemStack(ModItems.battery, 1, 4));
        capOk &= made(o[0], br, com.sc.block.BlockBridgeSC.CAPACITOR, 1) && (!o[0].hasTagCompound() || o[0].getTagCompound().getLong("BridgeEU") == 0);
        ItemStack L = new ItemStack(ModItems.component("polymerPlate")), I = new ItemStack(ModItems.component("quartzEmitter"));
        com.sc.item.ItemBatterySC.setCharge(ev, 25000000L);
        craft3(o, new String[]{"LIL", "TXT", "TVT"}, 'L', L, 'I', I, 'T', T, 'X', X, 'V', ev);
        boolean remOk = made(o[0], ModItems.bridgeRemote, com.sc.item.ItemBridgeRemoteSC.GROUND, 1)
                && com.sc.item.ItemBridgeRemoteSC.chargeOf(o[0]) == com.sc.bridge.BridgeMathSC.REMOTE_CAPACITY;
        com.sc.item.ItemBatterySC.setCharge(ev, 3000000L);
        craft3(o, new String[]{"LIL", "TXT", "TVT"}, 'L', L, 'I', I, 'T', T, 'X', X, 'V', ev);
        remOk &= made(o[0], ModItems.bridgeRemote, com.sc.item.ItemBridgeRemoteSC.GROUND, 1) && com.sc.item.ItemBridgeRemoteSC.chargeOf(o[0]) == 3000000L;
        ItemStack ground = new ItemStack(ModItems.bridgeRemote, 1, com.sc.item.ItemBridgeRemoteSC.GROUND);
        com.sc.bridge.BridgeItemDataSC.setCharge(ground, 4000000L);
        com.sc.bridge.BridgeItemDataSC.tag(ground).setIntArray("Br", new int[]{10, 64, -20, 0});
        com.sc.item.ItemBatterySC.setCharge(xv, 5000000L);
        craft3(o, new String[]{"SOS", "MRM", "SYS"}, 'S', S, 'O', O, 'M', M, 'R', ground, 'Y', xv);
        boolean spaceOk = made(o[0], ModItems.bridgeRemote, com.sc.item.ItemBridgeRemoteSC.SPACE, 1) && com.sc.item.ItemBridgeRemoteSC.chargeOf(o[0]) == 9000000L
                && java.util.Arrays.equals(com.sc.bridge.BridgeItemDataSC.remoteLink(o[0]), new int[]{10, 64, -20, 0});
        com.sc.item.ItemBatterySC.setCharge(xv, 3000000000L);
        craft3(o, new String[]{"SOS", "MRM", "SYS"}, 'S', S, 'O', O, 'M', M, 'R', ground, 'Y', xv);
        spaceOk &= made(o[0], ModItems.bridgeRemote, com.sc.item.ItemBridgeRemoteSC.SPACE, 1)
                && com.sc.item.ItemBridgeRemoteSC.chargeOf(o[0]) == com.sc.bridge.BridgeMathSC.SPACE_REMOTE_CAPACITY;
        check(capOk && remOk && spaceOk, "bridge recipes: the QV cell's charge goes into the capacitor, the EV cell's into the Bridge Remote (capped 10 M),"
                + " the Bridge Remote's binding and charge + the XV core's into the Space Remote (capped 20 M) (" + capOk + "/" + remOk + "/" + spaceOk + ")");

        // the coordinator copy: filled + empty -> the point copied, the filled one stays in the grid; two empty / two filled - no
        ItemStack filled = new ItemStack(ModItems.coordinator), empty = new ItemStack(ModItems.coordinator);
        com.sc.bridge.BridgeItemDataSC.setPoint(filled, 100, 70, -300, 0, true);
        com.sc.bridge.BridgeItemDataSC.setPointName(filled, "Home");
        net.minecraft.item.crafting.IRecipe cr = craft3(o, new String[]{"F E", "", ""}, 'F', filled, 'E', empty);
        boolean copy = cr instanceof com.sc.init.CoordinatorCopyRecipeSC && o[0] != null && o[0].getItem() == ModItems.coordinator
                && java.util.Arrays.equals(com.sc.bridge.BridgeItemDataSC.point(o[0]), new int[]{100, 70, -300, 0})
                && "Home".equals(com.sc.bridge.BridgeItemDataSC.pointName(o[0])) && com.sc.bridge.BridgeItemDataSC.safe(o[0]);
        boolean stays = ModItems.coordinator.hasContainerItem(filled) && !ModItems.coordinator.doesContainerItemLeaveCraftingGrid(filled)
                && java.util.Arrays.equals(com.sc.bridge.BridgeItemDataSC.point(ModItems.coordinator.getContainerItem(filled)), new int[]{100, 70, -300, 0})
                && !ModItems.coordinator.hasContainerItem(empty);
        boolean twoEmpty = craft3(o, new String[]{"EE", "", ""}, 'E', empty) == null;
        boolean twoFilled = craft3(o, new String[]{"FF", "", ""}, 'F', filled) == null;
        boolean extra = craft3(o, new String[]{"FEO", "", ""}, 'F', filled, 'E', empty, 'O', O) == null;
        check(copy && stays && twoEmpty && twoFilled && extra, "coordinator copy: filled + empty -> the point, name and safe flag copied, the filled one stays in the grid;"
                + " two empty / two filled / anything extra - no recipe (" + copy + "/" + stays + "/" + twoEmpty + "/" + twoFilled + "/" + extra + ")");

        // the book: the bridge chapter has the recipe cards, and the G key opens the article about each part and item
        com.sc.manual.BookContent.invalidate();
        int cards = 0;
        for (com.sc.manual.BookEntry e : com.sc.manual.BookContent.chapter(com.sc.manual.BookChapter.BRIDGE)) {
            for (com.sc.manual.BookEl el : e.els) {
                if (el.kind == com.sc.manual.BookEl.Kind.CRAFT) {
                    cards++;
                }
            }
        }
        boolean keys = bridgePage(new ItemStack(ModItems.bridgeRemote, 1, 0), "bridge.remote") && bridgePage(new ItemStack(ModItems.bridgeRemote, 1, 1), "bridge.remote")
                && bridgePage(new ItemStack(ModItems.coordinator), "bridge.remote") && bridgePage(new ItemStack(ModItems.bridgeLinkModule), "bridge.armour")
                && bridgePage(com.sc.block.BlockBridgeSC.stack(com.sc.block.BlockBridgeSC.ANCHOR, 1), "bridge.places");
        for (int i = 0; i < com.sc.block.BlockBridgeSC.parts(); i++) {
            com.sc.manual.BookEntry e = com.sc.manual.BookContent.entryFor(com.sc.block.BlockBridgeSC.stack(i, 1));
            keys &= e != null && e.chapter == com.sc.manual.BookChapter.BRIDGE;
        }
        String about2 = com.sc.manual.Lang.tr("sc.manual.bridge.about.2");
        check(cards >= 18 && keys && !about2.contains("No recipes yet"), "bridge book: " + cards + " recipe cards in the chapter (>= 18), G opens the right"
                + " article for every part, the remotes, the coordinator and the link module (" + keys + "), no 'no recipes yet'");
    }

    private static boolean bridgePage(ItemStack s, String id) {
        com.sc.manual.BookEntry e = com.sc.manual.BookContent.entryFor(s);
        return e != null && e.id.equals(id);
    }

    /** The fixes after the 2026-10-05 bug check (docs/todo-недоработки.md: СБ, М, МК). */
    private static void auditFixes20261006() {
        singularAudit20261005();
        check(testCellTopUp(), "МК-1: a part-filled Singular Matter cell tops up from a tank that won't take it");
        check(testBookIndex(), "МК-2: the handbook's recipe index gives the same lines as the full scan");
        check(testStationCarry(), "МК-3: the Singular Station's recipe carries the Armour Station's EU and gas tanks");
        bridgeChecks20261005();
    }

    /** Проверка 2026-10-05 СБ-1, СБ-2, СБ-4, СБ-6. */
    private static void singularAudit20261005() {
        net.minecraft.item.Item[] sg = ModItems.ARMOR.get(com.sc.util.ArmorSuit.SINGULAR);
        com.sc.util.ArmorGasSC.Gas he = com.sc.util.ArmorGasSC.Gas.HELIUM;
        ItemStack donor = new ItemStack(sg[1]), fresh = new ItemStack(sg[1]);
        com.sc.util.SingularLevel.setLevel(donor, 4);
        int cap4 = com.sc.util.ArmorGasSC.capacity(donor, he);
        com.sc.util.ArmorGasSC.setAmount(donor, he, cap4);
        int[] over = com.sc.tileentity.TileEntitySingularStationSC.transferLevel(donor, fresh);
        int cap1 = com.sc.util.ArmorGasSC.capacity(donor, he);
        com.sc.tileentity.TileEntitySingularStationSC st = new com.sc.tileentity.TileEntitySingularStationSC();
        int lost = st.pourIntoTanks(over);
        boolean sb1 = cap4 > cap1 && over[he.ordinal()] == cap4 - cap1
                && com.sc.util.ArmorGasSC.amount(donor, he) == cap1
                && donor.getTagCompound().getInteger(com.sc.util.ArmorGasSC.nbtKey(he)) == cap1
                && st.tankAmount(he) + lost == cap4 - cap1;
        check(sb1, "СБ-1: donor surplus gas " + (cap4 - cap1) + " mB poured into the station (lost " + lost + ")");
        ItemStack[] set = new ItemStack[4];
        for (int t = 0; t < 4; t++) {
            set[t] = new ItemStack(sg[t]);
            com.sc.util.SingularLevel.setLevel(set[t], 2);
        }
        com.sc.util.SingularLevel.updateSync(set);
        boolean wasSynced = com.sc.util.SingularLevel.synced(set[0]);
        com.sc.tileentity.TileEntitySingularStationSC st2 = new com.sc.tileentity.TileEntitySingularStationSC();
        st2.setInventorySlotContents(0, set[0]);
        st2.setInventorySlotContents(com.sc.tileentity.TileEntitySingularStationSC.DONOR_SLOT, set[1]);
        boolean sb2 = wasSynced && !com.sc.util.SingularLevel.synced(set[0]) && !com.sc.util.SingularLevel.synced(set[1])
                && com.sc.util.SingularLevel.clearSync(set[2]) && !com.sc.util.SingularLevel.clearSync(set[2]);
        check(sb2, "СБ-2: the sync flag is cleared off a piece put in the station / not worn");
        com.sc.tileentity.TileEntitySingularStationSC st3 = new com.sc.tileentity.TileEntitySingularStationSC();
        ItemStack boots = new ItemStack(sg[3]);
        com.sc.util.SingularLevel.setLevel(boots, 4);
        st3.setInventorySlotContents(3, boots);
        ItemStack core = new ItemStack(ModItems.battery, 1, com.sc.util.SingularStationMath.CORE_META);
        long full = com.sc.item.ItemBatterySC.capacityOf(core);
        com.sc.item.ItemBatterySC.setCharge(core, full);
        st3.setInventorySlotContents(com.sc.tileentity.TileEntitySingularStationSC.CATALYST_SLOT, core);
        int buf0 = st3.getEnergyStored(), buf0Max = st3.getMaxEnergyStored();
        String r = st3.startModerniseFor(new int[]{0, 0, 0, 4}, "tester");
        long cost = st3.getProcess() == null ? -1 : st3.getProcess().cost[0];
        ItemStack kept = st3.getStackInSlot(com.sc.tileentity.TileEntitySingularStationSC.CATALYST_SLOT);
        boolean sb4;
        if (r == null && cost >= 0) {
            long counted = Math.min(full, cost);
            long put = Math.min(full - counted, Math.max(0L, (long) buf0Max - buf0));
            sb4 = kept == null && st3.getProcess().catalystEu == counted && st3.getProcess().drawn[0] == counted
                    && st3.getEnergyStored() == buf0 + put;
            int bufStart = st3.getEnergyStored();
            st3.cancelProcess();
            ItemStack after = st3.getStackInSlot(com.sc.tileentity.TileEntitySingularStationSC.CATALYST_SLOT);
            sb4 &= st3.getProcess() == null && com.sc.tileentity.TileEntitySingularStationSC.isCore(after) && after != core
                    && com.sc.item.ItemBatterySC.chargeOf(after) == com.sc.util.SingularStationMath.refund(counted)
                    && st3.getEnergyStored() == bufStart;
        } else {
            sb4 = false;
        }
        check(sb4, "Н-1 В2: the core is consumed at the start (" + cost + " of " + full + " counted), the rest goes into the station's buffer,"
                + " cancel returns a core with 50% of the counted charge (" + r + ")");
        ItemStack synced = new ItemStack(sg[0]);
        synced.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
        synced.getTagCompound().setBoolean(com.sc.util.SingularLevel.SYNC, true);
        new com.sc.tileentity.TileEntityArmorStationSC().setInventorySlotContents(0, synced);
        check(!synced.getTagCompound().hasKey(com.sc.util.SingularLevel.SYNC), "Н-2: the sync flag is cleared off a piece put in the regular Armour Service Station");
        boolean sb6;
        try {
            net.minecraft.entity.projectile.EntityArrow a = new net.minecraft.entity.projectile.EntityArrow(null);
            boolean flying = !com.sc.item.SingularPowersSC.stuckInGround(a);
            boolean set6 = com.sc.item.SingularPowersSC.setStuckForTest(a, true);
            sb6 = flying && set6 && com.sc.item.SingularPowersSC.stuckInGround(a)
                    && !com.sc.item.SingularPowersSC.stuckInGround(null);
        } catch (Throwable t) {
            sb6 = false;
        }
        check(sb6, "СБ-6: an arrow stuck in the ground is seen (inGround via reflection)");
    }

    private static boolean testCellTopUp() {          // МК-1
        if (com.sc.init.ModFluids.singularMatter == null || ModItems.singularCell == null) return true;
        final net.minecraftforge.fluids.FluidTank src = new net.minecraftforge.fluids.FluidTank(
                new net.minecraftforge.fluids.FluidStack(com.sc.init.ModFluids.singularMatter, 700), 4000);
        net.minecraftforge.fluids.IFluidHandler h = new net.minecraftforge.fluids.IFluidHandler() {
            public int fill(net.minecraftforge.common.util.ForgeDirection d, net.minecraftforge.fluids.FluidStack r, boolean doIt) { return 0; }
            public net.minecraftforge.fluids.FluidStack drain(net.minecraftforge.common.util.ForgeDirection d, net.minecraftforge.fluids.FluidStack r, boolean doIt) {
                return r != null && r.isFluidEqual(src.getFluid()) ? src.drain(r.amount, doIt) : null; }
            public net.minecraftforge.fluids.FluidStack drain(net.minecraftforge.common.util.ForgeDirection d, int max, boolean doIt) { return src.drain(max, doIt); }
            public boolean canFill(net.minecraftforge.common.util.ForgeDirection d, net.minecraftforge.fluids.Fluid f) { return false; }
            public boolean canDrain(net.minecraftforge.common.util.ForgeDirection d, net.minecraftforge.fluids.Fluid f) { return true; }
            public net.minecraftforge.fluids.FluidTankInfo[] getTankInfo(net.minecraftforge.common.util.ForgeDirection d) { return new net.minecraftforge.fluids.FluidTankInfo[]{src.getInfo()}; }
        };
        ItemStack cell = com.sc.item.ItemSingularCellSC.filled(ModItems.singularCell, 400);
        net.minecraftforge.fluids.IFluidContainerItem it = (net.minecraftforge.fluids.IFluidContainerItem) cell.getItem();
        net.minecraftforge.fluids.FluidStack got = com.sc.util.FluidHandSC.topUp(h, it, cell, it.getFluid(cell), false);
        boolean ok = got != null && got.amount == 600 && com.sc.item.ItemSingularCellSC.amountOf(cell) == 1000 && src.getFluidAmount() == 100;
        ok &= com.sc.util.FluidHandSC.topUp(h, it, cell, it.getFluid(cell), false) == null && src.getFluidAmount() == 100;
        ItemStack c2 = com.sc.item.ItemSingularCellSC.filled(ModItems.singularCell, 500);
        got = com.sc.util.FluidHandSC.topUp(h, it, c2, it.getFluid(c2), false);
        ok &= got != null && got.amount == 100 && com.sc.item.ItemSingularCellSC.amountOf(c2) == 600 && src.getFluidAmount() == 0;
        return ok;
    }

    private static boolean testBookIndex() {          // МК-2
        String diff = com.sc.manual.BookContent.indexMatchesScan();
        if (diff != null) System.out.println("[SC-SELFTEST] book index differs: " + diff);
        return diff == null;
    }

    private static boolean testStationCarry() {       // МК-3
        com.sc.init.ChargeCarryRecipeSC rec = null;
        for (Object o : net.minecraft.item.crafting.CraftingManager.getInstance().getRecipeList())
            if (o instanceof com.sc.init.ChargeCarryRecipeSC && ((com.sc.init.ChargeCarryRecipeSC) o).getRecipeOutput().getItem()
                    == net.minecraft.item.Item.getItemFromBlock(com.sc.init.ModBlocks.singularStation)) rec = (com.sc.init.ChargeCarryRecipeSC) o;
        if (rec == null) return false;
        net.minecraft.inventory.InventoryCrafting grid = new net.minecraft.inventory.InventoryCrafting(new net.minecraft.inventory.Container() {
            public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer p) { return true; } }, 3, 3);
        ItemStack st = new ItemStack(com.sc.init.ModBlocks.armorStation);
        net.minecraft.nbt.NBTTagCompound t = new net.minecraft.nbt.NBTTagCompound(), tanks = new net.minecraft.nbt.NBTTagCompound();
        t.setInteger("EnergySC", 1234);
        tanks.setInteger(com.sc.util.ArmorGasSC.Gas.values()[0].key(), 500);
        t.setTag(com.sc.tileentity.TileEntityArmorStationSC.ITEM_TANKS_KEY, tanks);
        st.setTagCompound(t);
        grid.setInventorySlotContents(4, st);
        ItemStack out = rec.getCraftingResult(grid);
        return out != null && out.hasTagCompound() && out.getTagCompound().getInteger("EnergySC") == 1234
                && out.getTagCompound().getCompoundTag(com.sc.tileentity.TileEntityArmorStationSC.ITEM_TANKS_KEY)
                       .getInteger(com.sc.util.ArmorGasSC.Gas.values()[0].key()) == 500;
    }

    private static void bridgeChecks20261005() {
        com.sc.bridge.BridgeSpaceSC.Cells g = com.sc.bridge.BridgeSpaceSC.guarded(new FakeCells(), new com.sc.bridge.BridgeSpaceSC.Zones() {
            @Override
            public boolean ring(int x, int y, int z) {
                return x >= -1 && x <= 1 && y >= 64 && y <= 66 && z >= -2 && z <= 2;
            }
        });
        com.sc.bridge.BridgeSpaceSC.Result in = com.sc.bridge.BridgeSpaceSC.check(g, 0, 64, 0, 3, 0);
        com.sc.bridge.BridgeSpaceSC.Result away = com.sc.bridge.BridgeSpaceSC.check(g, 10, 64, 0, 3, 0);
        com.sc.bridge.BridgeSpaceSC.Result near = com.sc.bridge.BridgeSpaceSC.probe(g, 0, 64, 0, 3, 0, 16);
        boolean clear = true;
        if (near.hasNearest) {
            for (int u = -1; u <= 1; u++) for (int v = 0; v < 3; v++) for (int d = 0; d < 2; d++)
                if (g.cell(near.nx + u, near.ny + v, near.nz + d) == com.sc.bridge.BridgeSpaceSC.RING) clear = false;
        }
        check(!in.free && "sc.bridge.place.ring".equals(in.reason) && away.free && near.hasNearest && clear
                && com.sc.bridge.BridgeSpaceSC.guarded(new FakeCells(), null) != null,
                "bridge М-6: another ring's room is taken for an end (" + in.reason + "), the nearest free place is outside it");
        com.sc.tileentity.TileEntityBridgeControllerSC a = new com.sc.tileentity.TileEntityBridgeControllerSC();
        check(a.ownerless() && a.allowed(null) && a.trusted(null) && a.ownerlessRefusal(null) == null
                && com.sc.tileentity.TileEntityBridgeControllerSC.openThrottle(null) == null,
                "bridge М-7 / М-3: an ownerless controller, the server itself is never refused or throttled");
    }

    /** The full bug check of 2026-10-06: machines (compressor tank extensions) and generators (item round trip). */
    private static void fullCheck20261006() {
        {   // МШ: компрессор принимает расширение бака, полный бак переживает слом; печь отказывает
            TileEntityMachineSC c = new TileEntityMachineSC();
            c.setMachineType(MachineType.MATTER_COMPRESSOR);
            ItemStack ext = ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.TANK_EXTENSION);
            ext.stackSize = 4;
            boolean takes = c.isItemValidForSlot(TileEntityMachineSC.FIRST_UPGRADE_SLOT, ext);
            c.setInventorySlotContents(TileEntityMachineSC.FIRST_UPGRADE_SLOT, ext);
            int cap = c.getTank(2).getCapacity();
            c.getTank(2).fill(new FluidStack(ModFluids.singularMatter, cap), true);
            FluidStack[] back = TileEntityMachineSC.tankFluidsOf(c.tanksForItem());
            TileEntityMachineSC f = new TileEntityMachineSC();
            f.setMachineType(MachineType.ELECTRIC_FURNACE);
            check(takes && cap == TileEntityMachineSC.SM_TANK + 4 * com.sc.machine.UpgradeType.TANK_PER_UPGRADE
                    && back[2] != null && back[2].amount == cap
                    && !f.isItemValidForSlot(TileEntityMachineSC.FIRST_UPGRADE_SLOT, ext),
                    "compressor: tank extensions fit, a full 40000 mB SM tank survives the item; furnace refuses them");
        }
        generatorItemRoundTrip();
    }

    /** Generator item: buffer, fuel and ignition survive break/place; ignition clamped; Singular by-product kept. */
    private static void generatorItemRoundTrip() {
        com.sc.energy.GeneratorType exo = com.sc.energy.GeneratorType.EXO_REACTOR;
        com.sc.tileentity.TileEntityGeneratorSC a = new com.sc.tileentity.TileEntityGeneratorSC();
        a.setGeneratorType(exo);
        a.setEnergyStoredClient(5000);
        a.getFuelTank().fill(new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.getFluid("liquidhelium"), 1500), true);
        a.receiveEnergy(net.minecraftforge.common.util.ForgeDirection.UNKNOWN, 32768, 12345, false);
        net.minecraft.nbt.NBTTagCompound tag = a.writeToItem();
        com.sc.tileentity.TileEntityGeneratorSC b = new com.sc.tileentity.TileEntityGeneratorSC();
        b.setGeneratorType(exo);
        b.readFromItem(tag);
        boolean ok = tag != null && b.getEnergyStored() == 5000 && b.getFuelTank().getFluidAmount() == 1500
                && b.getIgnitionEU() == 12345 && !b.isIgnited();
        tag.setLong("IgnitionEU", Long.MAX_VALUE);
        com.sc.tileentity.TileEntityGeneratorSC c = new com.sc.tileentity.TileEntityGeneratorSC();
        c.setGeneratorType(exo);
        c.readFromItem(tag);
        ok &= c.getIgnitionEU() == exo.ignitionThreshold();
        com.sc.tileentity.TileEntityGeneratorSC s1 = new com.sc.tileentity.TileEntityGeneratorSC();
        s1.setGeneratorType(com.sc.energy.GeneratorType.SINGULAR_REACTOR);
        s1.getSingular().setSmForTest(1234);
        com.sc.tileentity.TileEntityGeneratorSC s2 = new com.sc.tileentity.TileEntityGeneratorSC();
        s2.setGeneratorType(com.sc.energy.GeneratorType.SINGULAR_REACTOR);
        s2.readFromItem(s1.writeToItem());
        ok &= s2.getSingular().getSmStored() == 1234;
        com.sc.tileentity.TileEntityGeneratorSC cr = new com.sc.tileentity.TileEntityGeneratorSC();
        cr.setGeneratorType(com.sc.energy.GeneratorType.CREATIVE);
        cr.setEnergyStoredClient(1000);
        ok &= cr.writeToItem() == null;
        check(ok, "generator item: buffer, fuel, ignition (clamped) and the Singular by-product survive break/place");
    }

    /** The fixes after the full bug check of 2026-10-06 (docs/todo-недоработки.md, «Полная проверка 2026-10-06»). */
    private static void fullFixes20261006() {
        // МШ-1: фильтр имён руд для глубокого сканирования
        check(com.sc.machine.ExoOreTableSC.isOreName("oreCopper") && !com.sc.machine.ExoOreTableSC.isOreName("oreberryIron")
                && !com.sc.machine.ExoOreTableSC.isOreName("ore") && !com.sc.machine.ExoOreTableSC.isOreName("orecopper")
                && !com.sc.machine.ExoOreTableSC.isOreName("ingotCopper") && !com.sc.machine.ExoOreTableSC.isOreName(null),
                "МШ-1: deep scan takes only ore[A-Z]... names");
        boolean onlyBlocks = true;
        for (com.sc.machine.ExoOreTableSC.Entry e : com.sc.machine.ExoOreTableSC.foreign()) {
            onlyBlocks &= e.ore.getItem() instanceof net.minecraft.item.ItemBlock;
        }
        check(onlyBlocks, "МШ-1: deep scan foreign ores are blocks only");
        // МШ-2: список измерений генерации
        check(com.sc.worldgen.OreGenSC.generatesIn(0, new int[]{0}) && !com.sc.worldgen.OreGenSC.generatesIn(-1, new int[]{0})
                && com.sc.worldgen.OreGenSC.generatesIn(7, new int[]{0, 7})
                && com.sc.worldgen.OreGenSC.generatesIn(0, null) && !com.sc.worldgen.OreGenSC.generatesIn(1, null)
                && com.sc.util.ConfigSC.oreDimensions != null && com.sc.util.ConfigSC.oreDimensions.length > 0,
                "МШ-2: ore generation dimensions from the config (default 0)");
        // БР-5: Oxygen Regen only in Exo-class suits, other chips everywhere
        check(!com.sc.item.ItemArmorChipSC.worksIn(com.sc.util.ChipType.OXYGEN_REGEN, com.sc.util.ArmorSuit.NANO)
                && !com.sc.item.ItemArmorChipSC.worksIn(com.sc.util.ChipType.OXYGEN_REGEN, com.sc.util.ArmorSuit.QUANTUM)
                && com.sc.item.ItemArmorChipSC.worksIn(com.sc.util.ChipType.OXYGEN_REGEN, com.sc.util.ArmorSuit.EXO)
                && com.sc.item.ItemArmorChipSC.worksIn(com.sc.util.ChipType.OXYGEN_REGEN, com.sc.util.ArmorSuit.SINGULAR)
                && com.sc.item.ItemArmorChipSC.worksIn(com.sc.util.ChipType.CRYO_LOOP, com.sc.util.ArmorSuit.NANO)
                && com.sc.item.ItemArmorChipSC.worksIn(com.sc.util.ChipType.SENSOR, com.sc.util.ArmorSuit.NANO)
                && !com.sc.item.ItemArmorChipSC.worksIn(com.sc.util.ChipType.SENSOR, null),
                "БР-5: a chip goes only into a chestplate where it works");
        {   // БР-4: a piece out of use cools by heatDissipation a second; chips back on at <=50%
            com.sc.util.ArmorSuit s = com.sc.util.ArmorSuit.EXO;
            net.minecraft.item.ItemStack c = new net.minecraft.item.ItemStack(ModItems.ARMOR.get(s)[1]);
            c.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
            c.getTagCompound().setInteger("HeatSC", s.heatCapacity / 2 + s.heatDissipation);
            c.getTagCompound().setBoolean("ChipsOffSC", true);
            boolean ch = com.sc.item.ItemArmorSC.coolOneSecond(c);
            check(ch && c.getTagCompound().getInteger("HeatSC") == s.heatCapacity / 2 && !c.getTagCompound().getBoolean("ChipsOffSC")
                    && com.sc.tileentity.TileEntityArmorStationSC.COOL_EVERY == 20 && !com.sc.item.ItemArmorSC.coolOneSecond(null),
                    "БР-4: armour in a station slot cools, chips come back at 50% heat");
        }
        check(com.sc.ShieldEventHandler.privateFieldAgainst(null, null, 0, 0, 0) == null, "БР-1: the station access helper is null-safe");
        {   // К1/К2: block dearer than the whole buffer takes the full buffer; own neighbours never dug
            com.sc.tileentity.TileEntityQuarrySC qk = new com.sc.tileentity.TileEntityQuarrySC();
            qk.setQuarryTier(com.sc.energy.Tier.LV);
            int qkMax = qk.getMaxEnergyStored();
            qk.setEnergyStoredClient(qkMax);
            boolean k1full = qk.payable(qkMax + 1000) == qkMax;
            qk.setEnergyStoredClient(qkMax - 1);
            boolean k1wait = qk.payable(qkMax + 1000) == -1;
            qk.setEnergyStoredClient(100);
            boolean k1norm = qk.payable(50) == 50 && qk.payable(150) == -1;
            boolean k2 = qk.touchesMe(1, 0, 0) && qk.touchesMe(0, -1, 0) && !qk.touchesMe(1, 1, 0) && !qk.touchesMe(0, 0, 0) && !qk.touchesMe(2, 0, 0);
            check(k1full && k1wait && k1norm && k2, "К1/К2: an over-buffer block takes the full buffer; 6 own neighbours never dug");
            int[] g1 = com.sc.tileentity.TileEntityFieldGeneratorSC.gridAxis(0, 10, 4);
            int[] g2 = com.sc.tileentity.TileEntityFieldGeneratorSC.gridAxis(5, 5, 4);
            int[] g3 = com.sc.tileentity.TileEntityFieldGeneratorSC.gridAxis(0, 8, 4);
            check(java.util.Arrays.equals(g1, new int[]{0, 4, 8, 10}) && java.util.Arrays.equals(g2, new int[]{5})
                    && java.util.Arrays.equals(g3, new int[]{0, 4, 8}) && com.sc.tileentity.TileEntityFieldGeneratorSC.CLAIM_MAX_POINTS >= 64,
                    "П1: private-zone claim check grid every 4 blocks, edges in, capped");
        }
        {   // Э-4: foreign inventories in item tubes
            net.minecraft.inventory.InventoryBasic stingy = new net.minecraft.inventory.InventoryBasic("t", false, 1) {
                @Override
                public ItemStack decrStackSize(int s, int n) {
                    return super.decrStackSize(s, Math.min(1, n));
                }
            };
            net.minecraft.inventory.InventoryBasic dst = new net.minecraft.inventory.InventoryBasic("d", false, 1);
            stingy.setInventorySlotContents(0, new ItemStack(net.minecraft.init.Items.iron_ingot, 10));
            int moved = TileEntityConduitBundleSC.transfer(stingy, 0, net.minecraftforge.common.util.ForgeDirection.NORTH, stingy.getStackInSlot(0),
                    dst, net.minecraftforge.common.util.ForgeDirection.SOUTH, 4, null);
            boolean less = moved == 1 && stingy.getStackInSlot(0).stackSize == 9 && dst.getStackInSlot(0).stackSize == 1;
            net.minecraft.inventory.InventoryBasic liar = new net.minecraft.inventory.InventoryBasic("t", false, 1) {
                @Override
                public ItemStack decrStackSize(int s, int n) {
                    return null;
                }
            };
            net.minecraft.inventory.InventoryBasic dst2 = new net.minecraft.inventory.InventoryBasic("d", false, 1);
            liar.setInventorySlotContents(0, new ItemStack(net.minecraft.init.Items.iron_ingot, 10));
            int m2 = TileEntityConduitBundleSC.transfer(liar, 0, net.minecraftforge.common.util.ForgeDirection.NORTH, liar.getStackInSlot(0),
                    dst2, net.minecraftforge.common.util.ForgeDirection.SOUTH, 4, null);
            boolean none = m2 == -1 && liar.getStackInSlot(0).stackSize == 10 && dst2.getStackInSlot(0) == null;
            net.minecraft.inventory.InventoryBasic greedy = new net.minecraft.inventory.InventoryBasic("t", false, 1) {
                @Override
                public ItemStack decrStackSize(int s, int n) {
                    return super.decrStackSize(s, 8);
                }
            };
            net.minecraft.inventory.InventoryBasic small = new net.minecraft.inventory.InventoryBasic("d", false, 1) {
                @Override
                public int getInventoryStackLimit() {
                    return 4;
                }
            };
            greedy.setInventorySlotContents(0, new ItemStack(net.minecraft.init.Items.iron_ingot, 10));
            int m3 = TileEntityConduitBundleSC.transfer(greedy, 0, net.minecraftforge.common.util.ForgeDirection.NORTH, greedy.getStackInSlot(0),
                    small, net.minecraftforge.common.util.ForgeDirection.SOUTH, 4, null);
            boolean more = m3 == 4 && small.getStackInSlot(0).stackSize == 4 && greedy.getStackInSlot(0).stackSize == 6;
            class NullSided extends net.minecraft.inventory.InventoryBasic implements net.minecraft.inventory.ISidedInventory {
                NullSided() {
                    super("s", false, 2);
                }

                public int[] getAccessibleSlotsFromSide(int side) {
                    return null;
                }

                public boolean canInsertItem(int slot, ItemStack st, int side) {
                    return true;
                }

                public boolean canExtractItem(int slot, ItemStack st, int side) {
                    return true;
                }
            }
            boolean nullSlots;
            try {
                nullSlots = TileEntityConduitBundleSC.insert(new NullSided(), net.minecraftforge.common.util.ForgeDirection.SOUTH,
                        new ItemStack(net.minecraft.init.Items.iron_ingot, 5), false) == 5;
            } catch (RuntimeException e) {
                nullSlots = false;
            }
            check(less && none && more && nullSlots, "Э-4: tubes with a foreign inventory that gives less / null / more, null slot arrays");
        }
    }

    /** Э-7: the shared inventory helper (machines, quarry, drill) doesn't trust other mods' inventories. */
    private static void invUtilForeign() {
        net.minecraftforge.common.util.ForgeDirection n = net.minecraftforge.common.util.ForgeDirection.NORTH;
        net.minecraftforge.common.util.ForgeDirection so = net.minecraftforge.common.util.ForgeDirection.SOUTH;
        net.minecraft.inventory.InventoryBasic stingy = new net.minecraft.inventory.InventoryBasic("t", false, 1) {
            @Override
            public ItemStack decrStackSize(int sl, int k) {
                return super.decrStackSize(sl, Math.min(1, k));
            }
        };
        net.minecraft.inventory.InventoryBasic d1 = new net.minecraft.inventory.InventoryBasic("d", false, 1);
        stingy.setInventorySlotContents(0, new ItemStack(net.minecraft.init.Items.iron_ingot, 10));
        int m1 = com.sc.util.InvUtilSC.move(stingy, 0, n, d1, so, 4, null, 0, 0, 0);
        boolean less = m1 == 1 && stingy.getStackInSlot(0).stackSize == 9 && d1.getStackInSlot(0).stackSize == 1;
        net.minecraft.inventory.InventoryBasic liar = new net.minecraft.inventory.InventoryBasic("t", false, 1) {
            @Override
            public ItemStack decrStackSize(int sl, int k) {
                return null;
            }
        };
        net.minecraft.inventory.InventoryBasic d2 = new net.minecraft.inventory.InventoryBasic("d", false, 1);
        liar.setInventorySlotContents(0, new ItemStack(net.minecraft.init.Items.iron_ingot, 10));
        int m2 = com.sc.util.InvUtilSC.move(liar, 0, n, d2, so, 4, null, 0, 0, 0);
        boolean none = m2 == 0 && liar.getStackInSlot(0).stackSize == 10 && d2.getStackInSlot(0) == null;
        net.minecraft.inventory.InventoryBasic greedy = new net.minecraft.inventory.InventoryBasic("t", false, 1) {
            @Override
            public ItemStack decrStackSize(int sl, int k) {
                return super.decrStackSize(sl, 8);
            }
        };
        net.minecraft.inventory.InventoryBasic small = new net.minecraft.inventory.InventoryBasic("d", false, 1) {
            @Override
            public int getInventoryStackLimit() {
                return 4;
            }
        };
        greedy.setInventorySlotContents(0, new ItemStack(net.minecraft.init.Items.iron_ingot, 10));
        int m3 = com.sc.util.InvUtilSC.move(greedy, 0, n, small, so, 4, null, 0, 0, 0);
        boolean more = m3 == 4 && small.getStackInSlot(0).stackSize == 4 && greedy.getStackInSlot(0).stackSize == 6;
        class NullSlots extends net.minecraft.inventory.InventoryBasic implements net.minecraft.inventory.ISidedInventory {
            NullSlots() {
                super("s", false, 2);
            }

            public int[] getAccessibleSlotsFromSide(int side) {
                return null;
            }

            public boolean canInsertItem(int sl, ItemStack st, int side) {
                return true;
            }

            public boolean canExtractItem(int sl, ItemStack st, int side) {
                return true;
            }
        }
        boolean nullOk;
        try {
            nullOk = com.sc.util.InvUtilSC.insert(new NullSlots(), so, new ItemStack(net.minecraft.init.Items.iron_ingot, 5)) == 5
                    && com.sc.util.InvUtilSC.slots(new NullSlots(), so).length == 0;
        } catch (RuntimeException e) {
            nullOk = false;
        }
        net.minecraft.inventory.InventoryBasic sim = new net.minecraft.inventory.InventoryBasic("d", false, 1);
        boolean simOk = com.sc.util.InvUtilSC.insert(sim, so, new ItemStack(net.minecraft.init.Items.iron_ingot, 3), true) == 0
                && sim.getStackInSlot(0) == null;
        check(less && none && more && nullOk && simOk,
                "Э-7: machines/quarry/drill inventory helper: a foreign inventory giving less / null / more, null slot arrays, simulate");
    }

    /** Singular blade & drill, stage1 (docs/plan-singular-tools.md). */
    private static void singToolsStage1() {
        // ---- Singular blade & drill, stage 1 (docs/plan-singular-tools.md) ----
        {
            com.sc.util.BladeType st1Sb = com.sc.util.BladeType.SINGULAR;
            com.sc.util.DrillType st1Sd = com.sc.util.DrillType.SINGULAR;
            check(st1Sb.ordinal() == 3 && st1Sd.ordinal() == 3 && st1Sb.suit == com.sc.util.ArmorSuit.SINGULAR && st1Sd.suit == com.sc.util.ArmorSuit.SINGULAR
                            && st1Sb.chargeTier == com.sc.energy.Tier.SV && st1Sd.chargeTier == com.sc.energy.Tier.SV
                            && st1Sb.maxCharge == 16000000 && st1Sd.maxCharge == 1000000 && st1Sb.heatCapacity == 800 && st1Sb.heatDissipation == 16
                            && st1Sd.heatCapacity == 800 && st1Sd.heatDissipation == 16 && st1Sd.fortune == 5 && st1Sb.looting == 6,
                    "singular tools: appended, SV, 16M / 1M EU, heat 800 / 16");
            check(st1Sb.exoClass() && st1Sd.exoClass() && com.sc.util.BladeType.EXO.exoClass() && !com.sc.util.BladeType.QUANTUM.exoClass()
                            && !com.sc.util.DrillType.NANO.exoClass(),
                    "singular tools count as Exo class (Exo rules)");
            boolean st1AllOld = true;
            for (com.sc.util.BladeFeature st1F : com.sc.util.BladeFeature.values()) {
                st1AllOld &= st1F.availableIn(st1Sb);
            }
            for (com.sc.util.DrillFeature st1F : com.sc.util.DrillFeature.values()) {
                st1AllOld &= st1F.availableIn(st1Sd);
            }
            check(st1AllOld, "singular tools have every Nano / Quantum / Exo function");
            check(ModItems.BLADES.get(st1Sb) != null && ModItems.DRILLS.get(st1Sd) != null && ModItems.singularCrumb != null && ModItems.singularClot != null,
                    "singular blade / drill / crumb / clot registered");

            ItemStack st1Blade = new ItemStack(ModItems.BLADES.get(st1Sb));
            ItemStack st1Drill = new ItemStack(ModItems.DRILLS.get(st1Sd));
            ItemStack st1ExoBlade = new ItemStack(ModItems.BLADES.get(com.sc.util.BladeType.EXO));
            check(com.sc.util.ToolLevelSC.isBlade(st1Blade) && com.sc.util.ToolLevelSC.isDrill(st1Drill) && !com.sc.util.ToolLevelSC.isSingularTool(st1ExoBlade)
                            && com.sc.util.ToolLevelSC.levelOf(st1Blade) == 1 && com.sc.util.ToolLevelSC.points(st1Blade) == 0,
                    "ToolLevelSC: kinds, fresh tool level 1 / 0 points");

            // thresholds: blade / drill tables, 0 at 5
            check(com.sc.util.ToolLevelSC.threshold(true, 1) == 3000 && com.sc.util.ToolLevelSC.threshold(true, 4) == 60000
                            && com.sc.util.ToolLevelSC.threshold(false, 1) == 2000 && com.sc.util.ToolLevelSC.threshold(false, 4) == 50000
                            && com.sc.util.ToolLevelSC.threshold(true, 5) == 0 && com.sc.util.ToolLevelSC.threshold(false, 0) == 0
                            && com.sc.util.ToolLevelSC.threshold(st1Blade) == 3000 && com.sc.util.ToolLevelSC.threshold(st1Drill) == 2000
                            && com.sc.util.ToolLevelSC.threshold(st1ExoBlade) == 0,
                    "ToolLevelSC thresholds (blade 3000..60000, drill 2000..50000, none at 5)");

            // points: capped at the threshold, not on other tools
            int st1A1 = com.sc.util.ToolLevelSC.addPoints(st1Drill, 1500);
            int st1A2 = com.sc.util.ToolLevelSC.addPoints(st1Drill, 1000);
            int st1A3 = com.sc.util.ToolLevelSC.addPoints(st1Drill, 10);
            check(st1A1 == 1500 && st1A2 == 500 && st1A3 == 0 && com.sc.util.ToolLevelSC.points(st1Drill) == 2000 && com.sc.util.ToolLevelSC.pointsFull(st1Drill)
                            && com.sc.util.ToolLevelSC.readyToUpgrade(st1Drill) && com.sc.util.ToolLevelSC.addPoints(st1ExoBlade, 50) == 0
                            && com.sc.util.ToolLevelSC.addPoints(st1Drill, -5) == 0,
                    "ToolLevelSC.addPoints caps at the threshold (" + st1A1 + "/" + st1A2 + "/" + st1A3 + ")");

            // level-up: +1, points back to 0, stops at 5
            int st1Lv = com.sc.util.ToolLevelSC.applyLevelUp(st1Drill);
            boolean st1LvOk = st1Lv == 2 && com.sc.util.ToolLevelSC.points(st1Drill) == 0 && !com.sc.util.ToolLevelSC.readyToUpgrade(st1Drill);
            com.sc.util.ToolLevelSC.setLevel(st1Drill, 5);
            com.sc.util.ToolLevelSC.addPoints(st1Drill, 100);
            st1LvOk &= com.sc.util.ToolLevelSC.applyLevelUp(st1Drill) == 5 && com.sc.util.ToolLevelSC.points(st1Drill) == 0
                    && !com.sc.util.ToolLevelSC.pointsFull(st1Drill) && com.sc.util.ToolLevelSC.applyLevelUp(st1ExoBlade) == 1;
            com.sc.util.ToolLevelSC.setLevel(st1Drill, 9);
            st1LvOk &= com.sc.util.ToolLevelSC.levelOf(st1Drill) == 5;
            check(st1LvOk, "ToolLevelSC.applyLevelUp: +1, points reset, capped at 5");

            // branches: blade 1..3, drill 1..2, chosen at 3, free choice only while pending
            ItemStack st1B2 = new ItemStack(ModItems.BLADES.get(st1Sb));
            ItemStack st1D2 = new ItemStack(ModItems.DRILLS.get(st1Sd));
            boolean st1Br = com.sc.util.ToolLevelSC.branchCount(st1B2) == 3 && com.sc.util.ToolLevelSC.branchCount(st1D2) == 2
                    && com.sc.util.ToolLevelSC.branchCount(st1ExoBlade) == 0
                    && com.sc.util.ToolLevelSC.setBranch(st1B2, com.sc.util.ToolLevelSC.BLADE_GUARDIAN)
                    && !com.sc.util.ToolLevelSC.setBranch(st1B2, 4) && !com.sc.util.ToolLevelSC.setBranch(st1D2, 3)
                    && com.sc.util.ToolLevelSC.setBranch(st1D2, com.sc.util.ToolLevelSC.DRILL_PROSPECTOR)
                    && !com.sc.util.ToolLevelSC.setBranch(st1ExoBlade, 1)
                    && com.sc.util.ToolLevelSC.branchOf(st1B2) == 3 && com.sc.util.ToolLevelSC.branchOf(st1D2) == 2;
            st1Br &= com.sc.util.ToolLevelSC.setBranch(st1B2, com.sc.util.ToolLevelSC.BRANCH_NONE) && com.sc.util.ToolLevelSC.branchOf(st1B2) == 0
                    && !com.sc.util.ToolLevelSC.branchPending(st1B2);
            st1B2.getTagCompound().setInteger(com.sc.util.ToolLevelSC.BRANCH, 7);           // garbage reads as none
            st1Br &= com.sc.util.ToolLevelSC.branchOf(st1B2) == com.sc.util.ToolLevelSC.BRANCH_NONE;
            com.sc.util.ToolLevelSC.setLevel(st1B2, 3);
            st1Br &= com.sc.util.ToolLevelSC.branchPending(st1B2);
            st1Br &= com.sc.util.ToolLevelSC.hasBranch(3, false, 2, 2, 3) && !com.sc.util.ToolLevelSC.hasBranch(3, false, 2, 1, 3)
                    && !com.sc.util.ToolLevelSC.hasBranch(4, false, 2, 2, 5) && com.sc.util.ToolLevelSC.hasBranch(5, true, 0, 1, 5);
            check(st1Br, "ToolLevelSC branches: blade 1..3, drill 1..2, validated, pending at level 3");

            // forms: levels and the Guardian's shield
            com.sc.util.BladeForm[] st1Forms = com.sc.util.BladeForm.values();
            check(st1Forms.length == 6 && com.sc.util.BladeForm.SWORD.level == 1 && com.sc.util.BladeForm.SCYTHE.level == 1
                            && com.sc.util.BladeForm.SPEAR.level == 1 && com.sc.util.BladeForm.WHIP.level == 2
                            && com.sc.util.BladeForm.SHIELD.level == 3 && com.sc.util.BladeForm.SINGULAR.level == 5
                            && com.sc.util.BladeForm.SHIELD.branch == com.sc.util.ToolLevelSC.BLADE_GUARDIAN
                            && com.sc.util.BladeForm.SWORD.branch == 0 && com.sc.util.BladeForm.WHIP.key().equals("whip")
                            && com.sc.item.ItemBladeSC.formOf(st1Blade) == com.sc.util.BladeForm.SWORD
                            && com.sc.item.ItemBladeSC.formOf(null) == com.sc.util.BladeForm.SWORD,
                    "BladeForm: six forms, levels 1/1/1/2/3/5, the shield is the Guardian's, default sword");
            com.sc.item.ItemBladeSC.setForm(st1Blade, com.sc.util.BladeForm.SPEAR);
            boolean st1Fm = com.sc.item.ItemBladeSC.formOf(st1Blade) == com.sc.util.BladeForm.SPEAR;
            st1Fm &= com.sc.util.BladeForm.cycle(com.sc.util.BladeForm.SPEAR, 1, 1, 0, false) == com.sc.util.BladeForm.SWORD   // whip shut at 1
                    && com.sc.util.BladeForm.cycle(com.sc.util.BladeForm.SPEAR, 1, 3, 0, false) == com.sc.util.BladeForm.WHIP
                    && com.sc.util.BladeForm.cycle(com.sc.util.BladeForm.WHIP, 1, 3, 1, false) == com.sc.util.BladeForm.SWORD // no shield: Destroyer
                    && com.sc.util.BladeForm.cycle(com.sc.util.BladeForm.WHIP, 1, 3, 3, false) == com.sc.util.BladeForm.SHIELD
                    && com.sc.util.BladeForm.cycle(com.sc.util.BladeForm.SWORD, -1, 5, 0, true) == com.sc.util.BladeForm.SINGULAR
                    && com.sc.util.BladeForm.of(99) == com.sc.util.BladeForm.SWORD;
            check(st1Fm, "BladeForm: setForm / formOf, cycling skips the shut forms");

            // drill read helpers
            ItemStack st1D3 = new ItemStack(ModItems.DRILLS.get(st1Sd));
            boolean st1Dh = com.sc.item.ItemDrillSC.blackHoleSize(st1D3) == 0 && com.sc.item.ItemDrillSC.tunnelDepth(st1D3) == 1;
            st1D3.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
            st1D3.getTagCompound().setInteger("SingHole", 9);
            st1D3.getTagCompound().setInteger("SingHoleDepth", 3);
            st1Dh &= com.sc.item.ItemDrillSC.blackHoleSize(st1D3) == 9 && com.sc.item.ItemDrillSC.tunnelDepth(st1D3) == 3;
            st1D3.getTagCompound().setInteger("SingHole", 7);
            st1Dh &= com.sc.item.ItemDrillSC.blackHoleSize(st1D3) == 0;
            check(st1Dh, "ItemDrillSC.blackHoleSize / tunnelDepth read the NBT (0/5/9/12, 1/3)");

            // cooldowns in the tool (pure: an explicit world tick)
            ItemStack st1C = new ItemStack(ModItems.BLADES.get(st1Sb));
            com.sc.util.ToolLevelSC.setCooldownEnd(st1C, "rift", 1000L);
            boolean st1Cd = com.sc.util.ToolLevelSC.cooldownEnd(st1C, "rift") == 1000L && com.sc.util.ToolLevelSC.cooldownLeft(st1C, "rift", 940L) == 60
                    && com.sc.util.ToolLevelSC.cooldownLeft(st1C, "rift", 1000L) == 0 && com.sc.util.ToolLevelSC.cooldownLeft(st1C, "other", 0L) == 0;
            com.sc.util.ToolLevelSC.setCooldownEnd(st1C, "rift", 0L);
            st1Cd &= com.sc.util.ToolLevelSC.cooldownEnd(st1C, "rift") == 0L && !st1C.getTagCompound().hasKey(com.sc.util.ToolLevelSC.COOLDOWNS);
            st1Cd &= com.sc.util.ToolLevelSC.cooldownTicks(100, true) == 75 && com.sc.util.ToolLevelSC.cooldownTicks(100, false) == 100
                    && com.sc.util.ToolLevelSC.cooldownTicks(1, true) == 1 && com.sc.util.ToolLevelSC.cooldownTicks(0, true) == 0
                    && com.sc.util.ToolLevelSC.cooldownTicks((net.minecraft.entity.player.EntityPlayer) null, 40) == 40;
            check(st1Cd, "ToolLevelSC cooldowns: end tick in the tool, ticks left, -25% with the full set");

            // Exo legacy: old functions x0.8 EU on the Singular tools only
            check(com.sc.util.ToolLevelSC.legacyCost(1000) == 800 && com.sc.util.ToolLevelSC.legacyCost(5) == 4
                            && com.sc.util.ToolLevelSC.legacyCost(st1Blade, 1000) == 800 && com.sc.util.ToolLevelSC.legacyCost(st1ExoBlade, 1000) == 1000
                            && com.sc.item.BladeLogicSC.legacy(st1Blade, com.sc.util.BladeFeature.WAVE_COST)
                                    == 16000
                            && com.sc.item.DrillLogicSC.legacy(st1Drill, 300) == 240 && com.sc.item.DrillLogicSC.legacy(null, 300) == 300
                            && com.sc.util.ToolLevelSC.LEGACY_MUL == 0.8F,
                    "LEGACY_MUL: Exo-era functions cost 80% EU on the Singular tools");

            // scheme: kept in the tool, default A
            ItemStack st1S = new ItemStack(ModItems.DRILLS.get(st1Sd));
            boolean st1Sc = com.sc.util.ToolLevelSC.schemeOf(st1S) == com.sc.util.SingularScheme.A;
            com.sc.util.ToolLevelSC.setScheme(st1S, com.sc.util.SingularScheme.G);
            st1Sc &= com.sc.util.ToolLevelSC.schemeOf(st1S) == com.sc.util.SingularScheme.G
                    && com.sc.util.ToolLevelSC.syncScheme(null, st1S) == false;
            check(st1Sc, "ToolLevelSC scheme: default A, set / read");

            // gases: the per-tick remainder
            float[] st1Sp = com.sc.util.ToolGasSC.split(0.7F, 0.5F);
            check(st1Sp[0] == 1F && Math.abs(st1Sp[1] - 0.2F) < 1e-4 && com.sc.util.ToolGasSC.split(0F, 0.25F)[0] == 0F,
                    "ToolGasSC.split keeps the part of a mB");

            // crumbs and the clot
            check(com.sc.item.ItemSingularCrumbSC.crumbsToFeed(64, 15, 10) == 2 && com.sc.item.ItemSingularCrumbSC.crumbsToFeed(64, 0, 10) == 0
                            && com.sc.item.ItemSingularCrumbSC.crumbsToFeed(3, 100, 10) == 3 && com.sc.item.ItemSingularCrumbSC.crumbsToFeed(0, 100, 10) == 0
                            && com.sc.item.ItemSingularCrumbSC.CRUMB_POINTS == 10 && com.sc.item.ItemSingularCrumbSC.CRUMB_BLOCKS == 64
                            && com.sc.item.ItemSingularCrumbSC.ORE_MUL == 4,
                    "singularity crumbs: only what fits is fed (10 points each)");
            ItemStack st1Crumb = new ItemStack(ModItems.singularCrumb), st1Clot = new ItemStack(ModItems.singularClot);
            ItemStack st1Made = com.sc.init.ModRecipesCrafting.craft(st1Crumb, st1Crumb, st1Crumb, st1Crumb, st1Crumb, st1Crumb, st1Crumb, st1Crumb, st1Crumb);
            check(st1Made != null && st1Made.getItem() == ModItems.singularClot && st1Made.stackSize == 1
                            && com.sc.item.ItemSingularClotSC.isClot(st1Clot) && !com.sc.item.ItemSingularClotSC.isClot(st1Crumb)
                            && com.sc.tileentity.TileEntityMachineSC.matterMass(st1Clot) == 0
                            && com.sc.tileentity.TileEntityMachineSC.matterMass(st1Crumb) == 0
                            && com.sc.item.ItemSingularClotSC.SM_PER_CLOT == 100,
                    "9 crumbs -> clot; neither is plain compressor mass (clot: 100 mB in the liquid mode)");
        }
    }

    /** Singular blade & drill, blade (docs/plan-singular-tools.md). */
    private static void singToolsBlade() {
        // ---- Singular blade (stage 2, blade agent): metadata, forms, cascade, chain, gating, cooldowns
        {
            boolean sbMeta = true;
            String sbBad = "";
            for (com.sc.util.BladeFeature sbF : com.sc.util.BladeFeature.values()) {
                boolean ok;
                if (sbF.isSingular()) {
                    ok = sbF.singLevel() >= 1 && sbF.singLevel() <= 5 && sbF.branch() == 0
                            && (sbF.gas() == null) == (sbF.gasMb() == 0) && (!sbF.gasPerSecond() || sbF.gas() != null)
                            && (sbF.cooldownTicks() == 0 || sbF.isAction()) && sbF.availableIn(com.sc.util.BladeType.SINGULAR)
                            && !sbF.availableIn(com.sc.util.BladeType.EXO);
                } else {
                    ok = sbF.singLevel() == 1 && sbF.branch() == 0 && sbF.gas() == null && sbF.gasMb() == 0 && !sbF.gasPerSecond()
                            && sbF.cooldownTicks() == 0;
                }
                if (!ok) {
                    sbMeta = false;
                    sbBad += sbF.key() + " ";
                }
            }
            check(sbMeta && com.sc.util.BladeFeature.values().length <= 31, "singular blade: feature metadata consistent " + sbBad);
            check(com.sc.util.BladeFeature.GRAV_PULL.ordinal() == 10 && com.sc.util.BladeFeature.FORM_ATTACK.ordinal() == 22
                            && com.sc.util.BladeFeature.LOOTING.ordinal() == 9,
                    "singular blade: features appended after LOOTING (ordinals saved)");
            check(com.sc.util.BladeFeature.GRAV_PULL.isAction() && com.sc.util.BladeFeature.RIFT_STEP.isAction()
                            && com.sc.util.BladeFeature.GRAV_TETHER.isAction() && com.sc.util.BladeFeature.FORM_ATTACK.isAction()
                            && com.sc.util.BladeFeature.WAVE.isAction() && !com.sc.util.BladeFeature.CASCADE.isAction()
                            && !com.sc.util.BladeFeature.HUNTER_SENSE.isAction() && !com.sc.util.BladeFeature.CHARGED_STRIKE.isAction()
                            && !com.sc.util.BladeFeature.HUNTER_SENSE.onByDefault,
                    "singular blade: keys vs switches, hunter's sense off by default");
            check(com.sc.util.BladeFeature.GRAV_PULL.gas() == com.sc.util.ArmorGasSC.Gas.SINGULAR_MATTER && com.sc.util.BladeFeature.GRAV_PULL.gasMb() == 5
                            && com.sc.util.BladeFeature.RIFT_STEP.gasMb() == 10 && com.sc.util.BladeFeature.PERFECT_PARRY.gas() == com.sc.util.ArmorGasSC.Gas.ARGON
                            && com.sc.util.BladeFeature.HUNTER_SENSE.gasPerSecond() && com.sc.util.BladeFeature.HUNTER_SENSE.gas() == com.sc.util.ArmorGasSC.Gas.KRYPTON
                            && com.sc.util.BladeFeature.EVENT_HORIZON.gasMb() == 10 && com.sc.util.BladeFeature.GRAV_SHIELD.gasMb() == 15
                            && com.sc.util.BladeFeature.GRAV_TETHER.gasMb() == 10 && com.sc.util.BladeFeature.CHAIN_CUT.singLevel() == 4
                            && com.sc.util.BladeFeature.CHARGED_STRIKE.singLevel() == 3 && com.sc.util.BladeFeature.RIFT_STEP.singLevel() == 2,
                    "singular blade: gases and levels as in the plan");

            // forms
            check(Math.abs(com.sc.util.BladeFeature.formMul(com.sc.util.BladeForm.SWORD) - 1F) < 1e-6
                            && Math.abs(com.sc.util.BladeFeature.formMul(com.sc.util.BladeForm.SCYTHE) - 0.8F) < 1e-6
                            && Math.abs(com.sc.util.BladeFeature.formMul(com.sc.util.BladeForm.SPEAR) - 1.2F) < 1e-6
                            && Math.abs(com.sc.util.BladeFeature.formMul(com.sc.util.BladeForm.WHIP) - 0.7F) < 1e-6
                            && Math.abs(com.sc.util.BladeFeature.formMul(com.sc.util.BladeForm.SHIELD) - 0.5F) < 1e-6
                            && Math.abs(com.sc.util.BladeFeature.formMul(com.sc.util.BladeForm.SINGULAR) - 2F) < 1e-6
                            && com.sc.util.BladeFeature.formReach(com.sc.util.BladeForm.SPEAR) == 6 && com.sc.util.BladeFeature.formReach(com.sc.util.BladeForm.WHIP) == 7
                            && com.sc.util.BladeFeature.formReach(com.sc.util.BladeForm.SWORD) == 0,
                    "singular blade: form multipliers and reach");
            check(com.sc.util.BladeFeature.formAttackCooldown(com.sc.util.BladeForm.SWORD) == 0
                            && com.sc.util.BladeFeature.formAttackCooldown(com.sc.util.BladeForm.SINGULAR) == 600
                            && com.sc.util.BladeFeature.formAttackGas(com.sc.util.BladeForm.SINGULAR) == com.sc.util.ArmorGasSC.Gas.SINGULAR_MATTER
                            && com.sc.util.BladeFeature.formAttackGasMb(com.sc.util.BladeForm.SINGULAR) == 50
                            && com.sc.util.BladeFeature.formAttackGas(com.sc.util.BladeForm.WHIP) == null
                            && com.sc.util.BladeFeature.formAttackEu(com.sc.util.BladeForm.SWORD) == com.sc.util.BladeFeature.WAVE_COST
                            && "sc.bladeform.scythe.attack".equals(com.sc.util.BladeFeature.formAttackKey(com.sc.util.BladeForm.SCYTHE)),
                    "singular blade: form attacks - cooldown, gas, EU, lang key");
            check(com.sc.util.BladeForm.cycle(com.sc.util.BladeForm.SWORD, 1, 1, 0, false) == com.sc.util.BladeForm.SCYTHE
                            && com.sc.util.BladeForm.cycle(com.sc.util.BladeForm.SPEAR, 1, 2, 0, false) == com.sc.util.BladeForm.WHIP
                            && com.sc.util.BladeForm.cycle(com.sc.util.BladeForm.WHIP, 1, 4, com.sc.util.ToolLevelSC.BLADE_DESTROYER, false) == com.sc.util.BladeForm.SWORD
                            && com.sc.util.BladeForm.cycle(com.sc.util.BladeForm.WHIP, 1, 3, com.sc.util.ToolLevelSC.BLADE_GUARDIAN, false) == com.sc.util.BladeForm.SHIELD
                            && com.sc.util.BladeForm.cycle(com.sc.util.BladeForm.SWORD, -1, 5, 0, false) == com.sc.util.BladeForm.SINGULAR,
                    "singular blade: one open form per wheel step (shield only for the Guardian)");

            // cascade, chain, horizon
            check(Math.abs(com.sc.util.BladeFeature.cascadeMul(0, false) - 1F) < 1e-5 && Math.abs(com.sc.util.BladeFeature.cascadeMul(3, false) - 1.3F) < 1e-5
                            && Math.abs(com.sc.util.BladeFeature.cascadeMul(9, false) - 1.5F) < 1e-5
                            && Math.abs(com.sc.util.BladeFeature.cascadeMul(9, true) - 1.8F) < 1e-5
                            && Math.abs(com.sc.util.BladeFeature.cascadeMul(-2, true) - 1F) < 1e-5,
                    "singular blade: cascade +10% a hit, cap 50% (Duelist 80%)");
            check(Math.abs(com.sc.util.BladeFeature.chainMul(1) - 0.75F) < 1e-5 && Math.abs(com.sc.util.BladeFeature.chainMul(3) - 0.421875F) < 1e-5
                            && Math.abs(com.sc.util.BladeFeature.horizonMul(0) - 1F) < 1e-5 && Math.abs(com.sc.util.BladeFeature.horizonMul(4) - 2F) < 1e-5
                            && Math.abs(com.sc.util.BladeFeature.horizonMul(99) - 3F) < 1e-5,
                    "singular blade: chain -25% a jump, horizon +25% a projectile (max 8)");

            // gating: openAt and unlocked() on a real blade
            check(com.sc.util.BladeFeature.RIFT_STEP.openAt(2, 0, false) && !com.sc.util.BladeFeature.RIFT_STEP.openAt(1, 0, false)
                            && com.sc.util.BladeFeature.WAVE.openAt(1, 0, false) && !com.sc.util.BladeFeature.GRAV_TETHER.openAt(3, 0, true),
                    "singular blade: functions open at their level");
            net.minecraft.item.ItemStack sbBlade = new net.minecraft.item.ItemStack(com.sc.init.ModItems.BLADES.get(com.sc.util.BladeType.SINGULAR));
            net.minecraft.item.ItemStack sbExo = new net.minecraft.item.ItemStack(com.sc.init.ModItems.BLADES.get(com.sc.util.BladeType.EXO));
            boolean sbGate = com.sc.item.BladeLogicSC.unlocked(null, sbBlade, com.sc.util.BladeFeature.GRAV_PULL)
                    && com.sc.item.BladeLogicSC.unlocked(null, sbBlade, com.sc.util.BladeFeature.WAVE)
                    && !com.sc.item.BladeLogicSC.unlocked(null, sbBlade, com.sc.util.BladeFeature.RIFT_STEP)
                    && !com.sc.item.BladeLogicSC.unlocked(null, sbBlade, com.sc.util.BladeFeature.CHAIN_CUT)
                    && com.sc.item.BladeLogicSC.unlocked(null, sbExo, com.sc.util.BladeFeature.WAVE)
                    && !com.sc.item.BladeLogicSC.unlocked(null, sbExo, com.sc.util.BladeFeature.GRAV_PULL);
            com.sc.util.ToolLevelSC.setLevel(sbBlade, 4);
            sbGate &= com.sc.item.BladeLogicSC.unlocked(null, sbBlade, com.sc.util.BladeFeature.CHAIN_CUT)
                    && com.sc.item.BladeLogicSC.unlocked(null, sbBlade, com.sc.util.BladeFeature.GRAV_TETHER);
            check(sbGate, "singular blade: unlocked() by level; old blades keep their own functions");
            check(!com.sc.item.ItemBladeSC.isEnabled(sbBlade, com.sc.util.BladeFeature.HUNTER_SENSE)
                            && com.sc.item.ItemBladeSC.isEnabled(sbBlade, com.sc.util.BladeFeature.CASCADE)
                            && !com.sc.item.ItemBladeSC.isEnabled(sbExo, com.sc.util.BladeFeature.CASCADE),
                    "singular blade: new switches' defaults, none on the Exo blade");

            // branch gating (pure) and the set bonus on cooldowns
            check(com.sc.util.ToolLevelSC.hasBranch(3, false, com.sc.util.ToolLevelSC.BLADE_DUELIST, com.sc.util.ToolLevelSC.BLADE_DUELIST, 3)
                            && !com.sc.util.ToolLevelSC.hasBranch(4, false, com.sc.util.ToolLevelSC.BLADE_DUELIST, com.sc.util.ToolLevelSC.BLADE_DUELIST, 5)
                            && !com.sc.util.ToolLevelSC.hasBranch(5, false, com.sc.util.ToolLevelSC.BLADE_GUARDIAN, com.sc.util.ToolLevelSC.BLADE_DESTROYER, 5)
                            && com.sc.util.ToolLevelSC.hasBranch(1, true, 0, com.sc.util.ToolLevelSC.BLADE_DESTROYER, 1),
                    "singular blade: branch perks at 3 / 5");
            check(com.sc.util.ToolLevelSC.cooldownTicks(com.sc.util.BladeFeature.RIFT_STEP.cooldownTicks(), true) == 120
                            && com.sc.util.ToolLevelSC.cooldownTicks(com.sc.util.BladeFeature.RIFT_STEP.cooldownTicks(), false) == 160
                            && com.sc.util.ToolLevelSC.cooldownTicks(com.sc.util.BladeFeature.formAttackCooldown(com.sc.util.BladeForm.SINGULAR), true) == 450
                            && com.sc.util.ToolLevelSC.cooldownTicks(com.sc.util.BladeFeature.CASCADE.cooldownTicks(), true) == 0,
                    "singular blade: cooldowns -25% with the full Singular suit");
            com.sc.util.ToolLevelSC.setCooldown(sbBlade, com.sc.util.BladeFeature.GRAV_PULL.key(), null, 60);   // no world: ignored
            com.sc.util.ToolLevelSC.setCooldownEnd(sbBlade, com.sc.util.BladeFeature.GRAV_PULL.key(), 1000L);
            check(com.sc.util.ToolLevelSC.cooldownLeft(sbBlade, "grav_pull", 940L) == 60 && com.sc.util.ToolLevelSC.cooldownLeft(sbBlade, "grav_pull", 1000L) == 0,
                    "singular blade: cooldown key = the feature's key()");
        }
    }

    /** Singular blade & drill, drill (docs/plan-singular-tools.md). */
    private static void singToolsDrill() {
        // ---- Singular drill, stage 2 (docs/plan-singular-tools.md §3): zones, sizes, crumbs, points, costs, metadata, placed record ----
        {
            // zone geometry: odd sizes centred; 12x12 shifted half a block toward the player; depth into the wall
            int[] sdZ5 = com.sc.util.DrillZoneSC.zone(10, 64, 20, 1, 5, 1, 10.5, 70, 20.5);        // floor (up face), 5x5
            check(sdZ5[0] == 8 && sdZ5[3] == 12 && sdZ5[2] == 18 && sdZ5[5] == 22 && sdZ5[1] == 64 && sdZ5[4] == 64
                    && com.sc.util.DrillZoneSC.volume(sdZ5) == 25, "drill zone: 5x5 on a floor centred on the block");
            int[] sdZ12a = com.sc.util.DrillZoneSC.zone(10, 64, 20, 1, 12, 1, 5.0, 70, 30.0);      // player at -x, +z
            int[] sdZ12b = com.sc.util.DrillZoneSC.zone(10, 64, 20, 1, 12, 1, 15.0, 70, 10.0);     // player at +x, -z
            check(sdZ12a[0] == 4 && sdZ12a[3] == 15 && sdZ12a[2] == 15 && sdZ12a[5] == 26
                            && sdZ12b[0] == 5 && sdZ12b[3] == 16 && sdZ12b[2] == 14 && sdZ12b[5] == 25
                            && com.sc.util.DrillZoneSC.volume(sdZ12a) == 144,
                    "drill zone: 12x12 has 12 blocks a side, its centre shifted half a block toward the player");
            int[] sdZt = com.sc.util.DrillZoneSC.zone(0, 64, 0, 3, 9, 3, 0.5, 65.6, 5.0);          // south wall (+z face), tunnel 3
            int[] sdZd = com.sc.util.DrillZoneSC.zone(0, 64, 0, 0, 5, 3, 0.5, 60.0, 0.5);          // ceiling (down face), 3 deep up
            check(sdZt[2] == -2 && sdZt[5] == 0 && sdZt[1] == 60 && sdZt[4] == 68 && sdZt[0] == -4 && sdZt[3] == 4
                            && com.sc.util.DrillZoneSC.volume(sdZt) == 243 && sdZd[1] == 64 && sdZd[4] == 66
                            && com.sc.util.DrillZoneSC.volume(com.sc.util.DrillZoneSC.zone(0, 64, 0, 5, 12, 3, 9, 70, 9)) == 432,
                    "drill zone: the tunnel goes 3 deep away from the clicked face; 12x12x3 = 432 blocks");
            check(com.sc.util.DrillZoneSC.onShell(sdZ5, 8, 64, 20) && !com.sc.util.DrillZoneSC.onShell(sdZt, 0, 64, -1)
                    && com.sc.util.DrillZoneSC.inside(sdZ5, 12, 64, 22) && !com.sc.util.DrillZoneSC.inside(sdZ5, 13, 64, 22), "drill zone: shell / inside");

            // black hole sizes gated by the level, the Shift+wheel cycle (with the tunnel depth), one step a call
            check(com.sc.util.DrillZoneSC.maxHole(1) == 5 && com.sc.util.DrillZoneSC.maxHole(2) == 5 && com.sc.util.DrillZoneSC.maxHole(3) == 9
                            && com.sc.util.DrillZoneSC.maxHole(4) == 9 && com.sc.util.DrillZoneSC.maxHole(5) == 12
                            && com.sc.util.DrillZoneSC.effectiveHole(12, 3) == 9 && com.sc.util.DrillZoneSC.effectiveHole(9, 1) == 5
                            && com.sc.util.DrillZoneSC.effectiveHole(0, 5) == 0 && com.sc.util.DrillZoneSC.effectiveHole(12, 5) == 12,
                    "black hole: 5 at lv 1-2, 9 at 3-4, 12 at 5; a bigger stored size works as the largest open one");
            int[] sdM = com.sc.util.DrillZoneSC.nextMode(0, 1, 1, 1);
            int[] sdM2 = com.sc.util.DrillZoneSC.nextMode(sdM[0], sdM[1], 1, 1);
            int[] sdM3 = com.sc.util.DrillZoneSC.nextMode(sdM2[0], sdM2[1], 1, 1);
            int[] sdMb = com.sc.util.DrillZoneSC.nextMode(0, 1, -1, 5);
            int[] sdM12 = com.sc.util.DrillZoneSC.nextMode(9, 3, 1, 5);
            check(sdM[0] == 5 && sdM[1] == 1 && sdM2[0] == 5 && sdM2[1] == 3 && sdM3[0] == 0 && sdM3[1] == 1
                            && sdMb[0] == 12 && sdMb[1] == 3 && sdM12[0] == 12 && sdM12[1] == 1
                            && com.sc.util.DrillZoneSC.nextHole(0, 1, 3) == 5 && com.sc.util.DrillZoneSC.nextHole(9, 1, 3) == 0
                            && com.sc.util.DrillZoneSC.nextHole(9, 1, 5) == 12,
                    "black hole cycle: off -> 5 -> 5 tunnel -> off at lv 1; back from off -> 12 tunnel at lv 5; 9 tunnel -> 12");
            ItemStack sdDrill = new ItemStack(ModItems.DRILLS.get(com.sc.util.DrillType.SINGULAR));
            com.sc.item.ItemDrillSC.setEnabled(sdDrill, com.sc.util.DrillFeature.BLACK_HOLE, true);
            boolean sdHoleOn = com.sc.item.ItemDrillSC.blackHoleSize(sdDrill) == 5 && com.sc.item.ItemDrillSC.isEnabled(sdDrill, com.sc.util.DrillFeature.BLACK_HOLE);
            com.sc.item.ItemDrillSC.setBlackHoleSize(sdDrill, 7);
            boolean sdBad = com.sc.item.ItemDrillSC.blackHoleSize(sdDrill) == 0 && !com.sc.item.ItemDrillSC.isEnabled(sdDrill, com.sc.util.DrillFeature.BLACK_HOLE);
            com.sc.item.ItemDrillSC.setTunnelDepth(sdDrill, 3);
            check(sdHoleOn && sdBad && com.sc.item.ItemDrillSC.tunnelDepth(sdDrill) == 3, "black hole switch = the SingHole size; invalid sizes are off; depth 3 kept");

            // crumbs: 1 per 64 natural blocks, ores x4; points: 1 per 16 blocks
            int[] sdC = com.sc.util.DrillZoneSC.accrue(60, com.sc.util.DrillZoneSC.crumbUnits(10, 2, com.sc.item.ItemSingularCrumbSC.ORE_MUL),
                    com.sc.item.ItemSingularCrumbSC.CRUMB_BLOCKS);                                  // 8 + 2*4 = 16 units on 60
            int[] sdC432 = com.sc.util.DrillZoneSC.accrue(0, com.sc.util.DrillZoneSC.crumbUnits(432, 0, 4), 64);
            int[] sdP = com.sc.util.DrillZoneSC.accrue(15, 33, com.sc.util.DrillFeature.BLOCKS_PER_POINT);
            check(com.sc.util.DrillZoneSC.crumbUnits(10, 2, 4) == 16 && sdC[0] == 1 && sdC[1] == 12 && sdC432[0] == 6 && sdC432[1] == 48
                            && sdP[0] == 3 && sdP[1] == 0 && com.sc.util.DrillZoneSC.accrue(0, 15, 16)[0] == 0,
                    "crumbs: 64 natural blocks each, an ore counts 4; points: one per 16 blocks, the rest carried");
            com.sc.item.ItemDrillSC.setCrumbCounter(sdDrill, 23);
            com.sc.item.ItemDrillSC.setDigCounter(sdDrill, 9);
            check(com.sc.item.ItemDrillSC.crumbProgress(sdDrill) == 23 && com.sc.item.ItemDrillSC.digCounter(sdDrill) == 9,
                    "drill NBT: crumb progress and dig counter");

            // costs: EU x0.5 a block (400 -> 200), SM 1 mB per 25 blocks, heat 1.5 a block
            check(com.sc.util.DrillZoneSC.holeEu(com.sc.util.DrillType.SINGULAR.euPerBlock, com.sc.util.DrillFeature.HOLE_EU_MUL) == 200
                            && Math.abs(com.sc.util.DrillZoneSC.holeGas(432, com.sc.util.DrillFeature.HOLE_BLOCKS_PER_MB) - 17.28F) < 1e-3
                            && com.sc.util.DrillZoneSC.holeGas(0, 25) == 0F
                            && com.sc.util.DrillZoneSC.holeHeat(25, com.sc.util.DrillFeature.HOLE_HEAT_PER_BLOCK) == 38
                            && com.sc.util.DrillZoneSC.holeHeat(432, 1.5F) == 648 && com.sc.util.ToolGasSC.split(0.72F, 0.48F)[0] == 1F,
                    "black hole costs: 200 EU a block, 17.28 mB SM for 432 blocks (fraction carried), heat 1.5 a block");
            check(com.sc.util.DrillZoneSC.funnelRadius(1, false) == 0 && com.sc.util.DrillZoneSC.funnelRadius(2, false) == 3
                            && com.sc.util.DrillZoneSC.funnelRadius(4, false) == 4 && com.sc.util.DrillZoneSC.funnelRadius(5, true) == 5,
                    "gravitational funnel: 7x7 at lv 2, 9x9 at lv 4, 11x11 for the Miner at lv 5");

            // feature metadata consistency
            boolean sdMeta = true;
            int sdSing = 0;
            for (com.sc.util.DrillFeature sdF : com.sc.util.DrillFeature.values()) {
                if (sdF.singular()) {
                    sdSing++;
                    sdMeta &= sdF.singLevel() >= 1 && sdF.singLevel() <= 5 && (sdF.gas() == null) == (sdF.gasMb() == 0)
                            && sdF.availableIn(com.sc.util.DrillType.SINGULAR) && !sdF.availableIn(com.sc.util.DrillType.EXO);
                } else {
                    sdMeta &= sdF.singLevel() == 1 && sdF.gas() == null && sdF.gasMb() == 0 && sdF.cooldownTicks() == 0 && sdF.branch() == 0;
                }
                sdMeta &= !sdF.gasPerSecond() && sdF.ordinal() < 31;                                   // FnToggled bits
            }
            check(sdMeta && sdSing == 6 && com.sc.util.DrillFeature.GRAV_FUNNEL.singLevel() == 2 && com.sc.util.DrillFeature.REPLACE.singLevel() == 2
                            && com.sc.util.DrillFeature.PHASE_DIG.singLevel() == 3 && com.sc.util.DrillFeature.PHASE_DIG.isAction()
                            && com.sc.util.DrillFeature.LASER.isAction() && !com.sc.util.DrillFeature.BLACK_HOLE.isAction()
                            && com.sc.util.DrillFeature.GRAV_FUNNEL.gas() == com.sc.util.ArmorGasSC.Gas.ARGON
                            && com.sc.util.DrillFeature.BLACK_HOLE.gas() == com.sc.util.ArmorGasSC.Gas.SINGULAR_MATTER
                            && com.sc.util.DrillFeature.BLACK_HOLE.cooldownTicks() == 20 && com.sc.util.DrillFeature.LINK.ordinal() == 11,
                    "drill features: 6 Singular ones appended after LINK, levels / gases / cooldowns as the plan");
            check(com.sc.util.ToolLevelSC.cooldownTicks(20, true) == 15 && com.sc.util.ToolLevelSC.cooldownTicks(20, false) == 20,
                    "black hole 12x12 cooldown: 1 s, 0.75 s with the full Singular suit");

            // the placed-block record: bounded FIFO, remove, re-add, positions round-trip
            com.sc.util.PlacedRecordSC sdR = new com.sc.util.PlacedRecordSC(1000);
            for (int i = 0; i < 1500; i++) {
                sdR.add(i, 64, -i);
            }
            boolean sdBound = sdR.size() == 1000 && !sdR.contains(0, 64, 0) && !sdR.contains(499, 64, -499) && sdR.contains(500, 64, -500)
                    && sdR.contains(1499, 64, -1499);
            boolean sdRem = sdR.remove(700, 64, -700) && !sdR.contains(700, 64, -700) && !sdR.remove(700, 64, -700) && sdR.size() == 999;
            sdR.add(700, 64, -700);                                     // re-added: now the newest
            for (int i = 1500; i < 1700; i++) {
                sdR.add(i, 64, -i);
            }
            boolean sdReAdd = sdR.contains(700, 64, -700) && !sdR.contains(699, 64, -699) && sdR.contains(701, 64, -701) && sdR.size() == 1000;
            long[] sdAll = sdR.toArray();
            com.sc.util.PlacedRecordSC sdR2 = new com.sc.util.PlacedRecordSC(100);
            for (int i = 0; i < 5000; i++) {                             // place / break loops: stale ring entries compacted
                sdR2.add(-5, 10, 7);
                sdR2.remove(-5, 10, 7);
            }
            sdR2.add(1, 2, 3);
            sdR2.add(-5, 10, 7);
            boolean sdLoop = sdR2.size() == 2 && sdR2.toArray().length == 2 && sdR2.toArray()[1] == com.sc.util.DrillZoneSC.pack(-5, 10, 7);
            int[] sdU = com.sc.util.DrillZoneSC.unpack(com.sc.util.DrillZoneSC.pack(-30000000, 255, 29999999));
            check(sdBound && sdRem && sdReAdd && sdLoop && sdAll.length == 1000 && sdAll[sdAll.length - 1] == com.sc.util.DrillZoneSC.pack(1699, 64, -1699)
                            && sdU[0] == -30000000 && sdU[1] == 255 && sdU[2] == 29999999,
                    "placed-block record: newest 1000 kept, remove / re-add, oldest first, packed positions round-trip");
        }
    }

    /** Singular blade & drill, station (docs/plan-singular-tools.md). */
    private static void singToolsStation() {
        // ---- Singular station: the tool slot (docs/plan-singular-tools.md §4, station agent) ----
        {
            final int TOOL = com.sc.tileentity.TileEntitySingularStationSC.TOOL_SLOT;
            final int MAT = com.sc.tileentity.TileEntitySingularStationSC.MATERIAL_SLOT;
            final int B = com.sc.util.SingularStationMath.TOOL_BLADE, D = com.sc.util.SingularStationMath.TOOL_DRILL;
            // costs and times (black-box numbers)
            long[] cb = com.sc.util.SingularStationMath.toolConvertCost(B), cd = com.sc.util.SingularStationMath.toolConvertCost(D);
            int[] mb = com.sc.util.SingularStationMath.toolConvertMaterials(B), md = com.sc.util.SingularStationMath.toolConvertMaterials(D);
            check(cb[0] == 400000000L && cb[1] == 1000 && cb[2] == 4000 && cb[3] == 0 && cb[4] == 0
                    && cd[0] == 200000000L && cd[1] == 600 && cd[2] == 2000
                    && com.sc.util.SingularStationMath.toolConvertTicks(B) == 3 * 1200 && com.sc.util.SingularStationMath.toolConvertTicks(D) == 2 * 1200
                    && mb[com.sc.util.SingularStationMath.M_SING_CORE] == 1 && mb[com.sc.util.SingularStationMath.M_NB3SN] == 4
                    && md[com.sc.util.SingularStationMath.M_SING_CORE] == 1 && md[com.sc.util.SingularStationMath.M_NB3SN] == 2,
                    "Singular station tool conversion: blade 400M EU / 1000 SM / 4000 He / core + 4 Nb3Sn / 3 min, drill 200M / 600 / 2000 / core + 2 / 2 min");
            long[] both = com.sc.util.SingularStationMath.convertCost(1, B);
            check(both[0] == 450000000L && both[1] == 1100 && com.sc.util.SingularStationMath.convertTicks(1, B) == 3 * 1200
                    && com.sc.util.SingularStationMath.convertMaterials(1, D)[com.sc.util.SingularStationMath.M_NB3SN] == 4
                    && com.sc.util.SingularStationMath.convertCost(1, com.sc.util.SingularStationMath.TOOL_NONE)[0] == 50000000L,
                    "Singular station: helmet + blade convert together (costs summed, the longest time, materials summed)");
            // modernisation: blade 50%, drill 25% of the set's row; resonance EU x 0.9; the core for 4 -> 5
            long[] t1 = com.sc.util.SingularStationMath.toolModerniseCost(B, 1, false), t4 = com.sc.util.SingularStationMath.toolModerniseCost(D, 4, true);
            check(t1[0] == 25000000L && t1[1] == 50 && t1[2] == 1000 && t1[3] == 250
                    && t4[0] == 900000000L && t4[1] == 250 && t4[4] == 500
                    && com.sc.util.SingularStationMath.toolModerniseTicks(D, 4) == 10 * 1200
                    && com.sc.util.SingularStationMath.toolModerniseCost(B, 5, false)[0] == 0,
                    "Singular station tool modernisation: blade 1->2 = half the set's row, drill 4->5 = a quarter (resonance -10% EU), 10 min");
            // crumbs: 10 mB SM each, at most 50% of the drill's SM
            long drillSm = com.sc.util.SingularStationMath.toolModerniseCost(D, 2, false)[1];   // 250 / 4 = 63 mB
            int maxCrumbs = com.sc.util.SingularStationMath.crumbsUsable(drillSm, 1000);
            long[] withCrumbs = com.sc.util.SingularStationMath.moderniseCost(new int[4], D, 2, maxCrumbs, false);
            long[] bladeCrumbs = com.sc.util.SingularStationMath.moderniseCost(new int[4], B, 2, 50, false);
            check(drillSm == 63 && maxCrumbs == 3 && withCrumbs[1] == 63 - 30 && withCrumbs[1] * 2 >= drillSm
                    && com.sc.util.SingularStationMath.crumbsUsable(drillSm, 1) == 1
                    && bladeCrumbs[1] == com.sc.util.SingularStationMath.toolModerniseCost(B, 2, false)[1],
                    "Singular station: crumbs pay <= 50% of the drill's SM (10 mB each), never the blade's: " + withCrumbs[1]);
            // slot validity, hoppers
            com.sc.tileentity.TileEntitySingularStationSC ts = new com.sc.tileentity.TileEntitySingularStationSC();
            ItemStack exoBlade = new ItemStack(com.sc.init.ModItems.BLADES.get(com.sc.util.BladeType.EXO));
            ItemStack exoDrill = new ItemStack(com.sc.init.ModItems.DRILLS.get(com.sc.util.DrillType.EXO));
            ItemStack sDrill = new ItemStack(com.sc.init.ModItems.DRILLS.get(com.sc.util.DrillType.SINGULAR));
            ItemStack nanoBlade = new ItemStack(com.sc.init.ModItems.BLADES.get(com.sc.util.BladeType.NANO));
            int[] pipes = ts.getAccessibleSlotsFromSide(1);
            check(ts.getSizeInventory() == 17 && TOOL == 16 && com.sc.tileentity.TileEntitySingularStationSC.MATERIAL_END == 16
                    && ts.isItemValidForSlot(TOOL, exoBlade) && ts.isItemValidForSlot(TOOL, exoDrill) && ts.isItemValidForSlot(TOOL, sDrill)
                    && !ts.isItemValidForSlot(TOOL, nanoBlade) && !ts.isItemValidForSlot(TOOL, new ItemStack(com.sc.init.ModItems.battery, 1, 6))
                    && !ts.isItemValidForSlot(MAT, exoBlade) && ts.isItemValidForSlot(MAT, new ItemStack(com.sc.init.ModItems.singularCrumb, 5))
                    && pipes.length == 5 && pipes[4] == TOOL && ts.canInsertItem(TOOL, exoBlade, 1) && !ts.canInsertItem(TOOL, nanoBlade, 1),
                    "Singular station: the tool slot (16) takes Exo / Singular blades and drills only, hoppers reach it, crumbs go to the material slots");
            // the conversion keeps charge and switches
            com.sc.item.ItemBladeSC.setCharge(exoBlade, 3000000);
            com.sc.item.ItemBladeSC.setEnabled(exoBlade, com.sc.util.BladeFeature.EXECUTE, false);
            com.sc.item.ItemBladeSC.setEnabled(exoBlade, com.sc.util.BladeFeature.LOOTING, true);
            exoBlade.setStackDisplayName("Kusanagi");
            ItemStack sb = com.sc.tileentity.TileEntitySingularStationSC.convertTool(exoBlade, com.sc.util.SingularScheme.values()[2]);
            com.sc.item.ItemDrillSC.setCharge(exoDrill, 100000);
            ItemStack sd = com.sc.tileentity.TileEntitySingularStationSC.convertTool(exoDrill);
            check(com.sc.util.ToolLevelSC.isBlade(sb) && com.sc.util.ToolLevelSC.levelOf(sb) == 1 && com.sc.util.ToolLevelSC.points(sb) == 0
                    && com.sc.item.ItemBladeSC.chargeOf(sb) == 3000000 && !com.sc.item.ItemBladeSC.isEnabled(sb, com.sc.util.BladeFeature.EXECUTE)
                    && com.sc.item.ItemBladeSC.isEnabled(sb, com.sc.util.BladeFeature.LOOTING)
                    && net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(net.minecraft.enchantment.Enchantment.looting.effectId, sb)
                        == com.sc.util.BladeType.SINGULAR.looting
                    && "Kusanagi".equals(sb.getDisplayName()) && com.sc.util.ToolLevelSC.schemeOf(sb) == com.sc.util.SingularScheme.values()[2]
                    && com.sc.util.ToolLevelSC.isDrill(sd) && com.sc.item.ItemDrillSC.chargeOf(sd) == 100000
                    && com.sc.util.ToolLevelSC.schemeOf(sd) == com.sc.util.SingularScheme.DEFAULT
                    && com.sc.tileentity.TileEntitySingularStationSC.convertTool(sd) == sd,
                    "Singular station convertTool: level 1, charge, switches, name, Looting re-synced, scheme; drill charge kept");
            // a station converting an Exo blade (no world): materials taken, the tool slot locked, cancel gives them back
            ts.setInventorySlotContents(TOOL, exoBlade.copy());
            ts.setInventorySlotContents(com.sc.tileentity.TileEntitySingularStationSC.CATALYST_SLOT, new ItemStack(com.sc.init.ModItems.battery, 1, 6));
            ts.setInventorySlotContents(MAT, new ItemStack(com.sc.init.ModItems.component("nb3SnPlate"), 3));
            String short1 = ts.startConvertFor("");
            ts.setInventorySlotContents(MAT, new ItemStack(com.sc.init.ModItems.component("nb3SnPlate"), 4));
            String ok1 = ts.startConvertFor("");
            com.sc.tileentity.SingularProcessSC tp = ts.getProcess();
            boolean conv = "sc.singStation.err.nomaterials".equals(short1) && ok1 == null && tp != null
                    && tp.mask == 1 << com.sc.tileentity.TileEntitySingularStationSC.TOOL_BIT && tp.cost[0] == 400000000L && tp.items.size() == 2
                    && ts.isLocked(TOOL) && !ts.isItemValidForSlot(TOOL, exoDrill) && !ts.canExtractItem(TOOL, exoBlade, 0)
                    && ts.getStackInSlot(MAT) == null;
            ts.cancelProcess();
            check(conv && ts.getProcess() == null && !ts.isLocked(TOOL) && ts.getStackInSlot(MAT) != null && ts.getStackInSlot(MAT).stackSize == 4
                    && com.sc.tileentity.TileEntitySingularStationSC.isCore(ts.getStackInSlot(com.sc.tileentity.TileEntitySingularStationSC.CATALYST_SLOT))
                    && com.sc.tileentity.TileEntitySingularStationSC.isExoTool(ts.getStackInSlot(TOOL)),
                    "Singular station: an Exo blade's conversion starts (materials whole, the slot locked) and «Отменить» gives them back");
            // the drill's modernisation with crumbs: they leave the slots, come back whole on cancel
            com.sc.tileentity.TileEntitySingularStationSC tm = new com.sc.tileentity.TileEntitySingularStationSC();
            ItemStack ready = new ItemStack(com.sc.init.ModItems.DRILLS.get(com.sc.util.DrillType.SINGULAR));
            com.sc.util.ToolLevelSC.setLevel(ready, 2);
            com.sc.util.ToolLevelSC.setPoints(ready, com.sc.util.ToolLevelSC.threshold(ready));
            tm.setInventorySlotContents(TOOL, ready);
            tm.setInventorySlotContents(MAT + 1, new ItemStack(com.sc.init.ModItems.singularCrumb, 10));
            String off = tm.startModerniseFor(new int[4], "");          // С-3: «Инструмент тоже» off - nothing to raise
            boolean offOk = "sc.singStation.err.noready".equals(off) && tm.getProcess() == null && tm.crumbsHave() == 10
                    && tm.toolModerniseLevel() == 0 && tm.toolReadyLevel() == 2;
            tm.toggleModTool();
            net.minecraft.nbt.NBTTagCompound mtNbt = new net.minecraft.nbt.NBTTagCompound();
            tm.writeToNBT(mtNbt);
            check(offOk && mtNbt.getBoolean("ModTool") && tm.toolModerniseLevel() == 2,
                    "Singular station: a ready tool joins the modernisation only with «Инструмент тоже» (saved)");
            String ms = tm.startModerniseFor(new int[4], "");
            com.sc.tileentity.SingularProcessSC mp = tm.getProcess();
            boolean mod = ms == null && mp != null && mp.kind == com.sc.tileentity.SingularProcessSC.KIND_MODERNISE
                    && mp.levels[com.sc.tileentity.TileEntitySingularStationSC.TOOL_BIT] == 2 && mp.cost[1] == 33 && mp.items.size() == 1
                    && tm.getStackInSlot(MAT + 1) != null && tm.getStackInSlot(MAT + 1).stackSize == 7 && tm.isLocked(TOOL);
            tm.cancelProcess();
            check(mod && tm.crumbsHave() == 10 && tm.getProcess() == null,
                    "Singular station: a ready Singular drill modernises alone, 3 crumbs pay 30 of 63 mB SM, back whole on cancel");
            // the tool's branch re-choice for BRANCH_SM, the scheme cycles onto the tool
            com.sc.tileentity.TileEntitySingularStationSC tb = new com.sc.tileentity.TileEntitySingularStationSC();
            ItemStack lv3 = new ItemStack(com.sc.init.ModItems.BLADES.get(com.sc.util.BladeType.SINGULAR));
            com.sc.util.ToolLevelSC.setLevel(lv3, 3);
            com.sc.util.ToolLevelSC.setBranch(lv3, com.sc.util.ToolLevelSC.BLADE_DUELIST);
            tb.setInventorySlotContents(TOOL, lv3);
            tb.fillTank(com.sc.util.ArmorGasSC.Gas.SINGULAR_MATTER, 150, true);
            String b1 = tb.changeToolBranch(com.sc.util.ToolLevelSC.BLADE_DUELIST);
            String b2 = tb.changeToolBranch(com.sc.util.ToolLevelSC.BLADE_GUARDIAN);
            String b3 = tb.changeToolBranch(com.sc.util.ToolLevelSC.BLADE_DESTROYER);
            com.sc.util.SingularScheme before = tb.shownScheme();
            boolean cyc = tb.cycleScheme(1);
            check("sc.singStation.err.samebranch".equals(b1) && b2 == null && "sc.singStation.err.nosm".equals(b3)
                    && com.sc.util.ToolLevelSC.branchOf(lv3) == com.sc.util.ToolLevelSC.BLADE_GUARDIAN
                    && tb.tankAmount(com.sc.util.ArmorGasSC.Gas.SINGULAR_MATTER) == 150 - com.sc.util.SingularStationMath.BRANCH_SM
                    && before == com.sc.util.SingularScheme.DEFAULT && cyc
                    && com.sc.util.ToolLevelSC.schemeOf(lv3) == com.sc.util.SingularStationMath.cycle(before, 1),
                    "Singular station: the tool's branch re-choice costs 100 mB SM, the scheme arrows recolour the tool");
            // an older station's NBT (no tool slot entry) loads with the slot empty; a saved tool comes back
            net.minecraft.nbt.NBTTagCompound saved = new net.minecraft.nbt.NBTTagCompound();
            tb.writeToNBT(saved);
            com.sc.tileentity.TileEntitySingularStationSC tl = new com.sc.tileentity.TileEntitySingularStationSC();
            tl.readFromNBT(saved);
            net.minecraft.nbt.NBTTagCompound old = (net.minecraft.nbt.NBTTagCompound) saved.copy();
            old.setTag("SingItems", new net.minecraft.nbt.NBTTagList());
            com.sc.tileentity.TileEntitySingularStationSC to = new com.sc.tileentity.TileEntitySingularStationSC();
            to.readFromNBT(old);
            check(com.sc.util.ToolLevelSC.isBlade(tl.getTool()) && com.sc.util.ToolLevelSC.levelOf(tl.getTool()) == 3 && to.getTool() == null,
                    "Singular station NBT: the tool slot is saved (SingItems Slot 8), an older save loads with it empty");
        }
    }

    /** Singular blade & drill, ui (docs/plan-singular-tools.md). */
    private static void singToolsUi() {
        // ---- Singular blade & drill, stage 2 UI: messages, form steps, tool HUD (docs/plan-singular-tools.md §4) ----
        {
            // the new action ids: distinct from every older one and from the air jump
            byte[] ui2Ids = {com.sc.handler.ArmorNetSC.BLADE_ACTION, com.sc.handler.ArmorNetSC.BLADE_FORM, com.sc.handler.ArmorNetSC.DRILL_ACTION,
                com.sc.handler.ArmorNetSC.DRILL_MODE, com.sc.handler.ArmorNetSC.TOOL_BRANCH};
            boolean ui2Distinct = true;
            for (int i = 0; i < ui2Ids.length; i++) {
                ui2Distinct &= ui2Ids[i] > com.sc.handler.ArmorNetSC.PROFILE_NEXT && ui2Ids[i] != com.sc.item.ArmorLogicSC.AIR_JUMP_ACTION
                        && com.sc.handler.ArmorNetSC.featureOfAction(ui2Ids[i]) == null;
                for (int j = i + 1; j < ui2Ids.length; j++) {
                    ui2Distinct &= ui2Ids[i] != ui2Ids[j];
                }
            }
            check(ui2Distinct, "tools UI: BLADE_ACTION / BLADE_FORM / DRILL_ACTION / DRILL_MODE / TOOL_BRANCH ids distinct and new");

            // encode / decode: a negative step and the branch flag survive the wire
            io.netty.buffer.ByteBuf ui2Buf = io.netty.buffer.Unpooled.buffer();
            new com.sc.handler.ArmorNetSC.Message(com.sc.handler.ArmorNetSC.BLADE_FORM, -1).toBytes(ui2Buf);
            com.sc.handler.ArmorNetSC.Message ui2M = new com.sc.handler.ArmorNetSC.Message();
            ui2M.fromBytes(ui2Buf);
            io.netty.buffer.ByteBuf ui2Buf2 = io.netty.buffer.Unpooled.buffer();
            new com.sc.handler.ArmorNetSC.Message(com.sc.handler.ArmorNetSC.TOOL_BRANCH, com.sc.util.ToolLevelSC.BLADE_GUARDIAN, true).toBytes(ui2Buf2);
            com.sc.handler.ArmorNetSC.Message ui2M2 = new com.sc.handler.ArmorNetSC.Message();
            ui2M2.fromBytes(ui2Buf2);
            check(ui2M.action == com.sc.handler.ArmorNetSC.BLADE_FORM && com.sc.handler.ArmorNetSC.step(ui2M.feature) == -1
                            && ui2M2.action == com.sc.handler.ArmorNetSC.TOOL_BRANCH && ui2M2.feature == 3 && ui2M2.value,
                    "tools UI: form step -1 and the tool branch message encode / decode");
            check(com.sc.handler.ArmorNetSC.step(5) == 1 && com.sc.handler.ArmorNetSC.step(-7) == -1 && com.sc.handler.ArmorNetSC.step(0) == 0,
                    "tools UI: a wheel step is one form at most");

            // the form row: steps from one form to another through the open ones, the shorter way round
            com.sc.util.BladeForm ui2Sw = com.sc.util.BladeForm.SWORD, ui2Sc = com.sc.util.BladeForm.SCYTHE, ui2Sp = com.sc.util.BladeForm.SPEAR,
                    ui2Wh = com.sc.util.BladeForm.WHIP, ui2Sh = com.sc.util.BladeForm.SHIELD, ui2Si = com.sc.util.BladeForm.SINGULAR;
            check(com.sc.handler.ArmorNetSC.formSteps(ui2Sw, ui2Sc, 1, 0, false) == 1
                            && com.sc.handler.ArmorNetSC.formSteps(ui2Sw, ui2Sp, 1, 0, false) == -1       // level 1: sword, scythe, spear - spear is one back
                            && com.sc.handler.ArmorNetSC.formSteps(ui2Sp, ui2Sc, 1, 0, false) == -1
                            && com.sc.handler.ArmorNetSC.formSteps(ui2Sw, ui2Sw, 1, 0, false) == 0,
                    "tools UI: form steps at level 1");
            check(com.sc.handler.ArmorNetSC.formSteps(ui2Sw, ui2Wh, 1, 0, false) == 0                         // closed: nothing sent
                            && com.sc.handler.ArmorNetSC.formSteps(ui2Sw, ui2Sh, 3, com.sc.util.ToolLevelSC.BLADE_DUELIST, false) == 0
                            && com.sc.handler.ArmorNetSC.formSteps(ui2Sw, ui2Sh, 3, com.sc.util.ToolLevelSC.BLADE_GUARDIAN, false) == -1   // sword..shield: 4 forward, 1 back
                            && com.sc.handler.ArmorNetSC.formSteps(ui2Sw, ui2Wh, 2, 0, false) == -1
                            && com.sc.handler.ArmorNetSC.formSteps(ui2Sc, ui2Si, 5, 0, true) == -2,
                    "tools UI: form steps skip closed forms (level, branch), creative opens all");
            // every step the client sends lands where it meant to (the server's cycle, step by step)
            boolean ui2Lands = true;
            for (com.sc.util.BladeForm ui2From : com.sc.util.BladeForm.values()) {
                for (com.sc.util.BladeForm ui2To : com.sc.util.BladeForm.values()) {
                    for (int ui2Lv = 1; ui2Lv <= 5; ui2Lv++) {
                        int ui2N = com.sc.handler.ArmorNetSC.formSteps(ui2From, ui2To, ui2Lv, com.sc.util.ToolLevelSC.BLADE_GUARDIAN, false);
                        if (ui2N == 0) {
                            continue;
                        }
                        com.sc.util.BladeForm ui2At = ui2From;
                        for (int k = 0; k < Math.abs(ui2N); k++) {
                            ui2At = com.sc.util.BladeForm.cycle(ui2At, ui2N > 0 ? 1 : -1, ui2Lv, com.sc.util.ToolLevelSC.BLADE_GUARDIAN, false);
                        }
                        ui2Lands &= ui2At == ui2To;
                    }
                }
            }
            check(ui2Lands, "tools UI: a form click's steps always land on the form clicked");

            // the tool HUD: icon states from the tool's own cooldown
            check(com.sc.util.SingularHud.toolState(true, 200, 100, false, true) == com.sc.util.SingularHud.COOLING
                            && com.sc.util.SingularHud.toolState(true, 90, 100, false, true) == com.sc.util.SingularHud.READY
                            && com.sc.util.SingularHud.toolState(true, 0, 100, true, true) == com.sc.util.SingularHud.NOGAS
                            && com.sc.util.SingularHud.toolState(true, 0, 100, true, false) == com.sc.util.SingularHud.HIDDEN
                            && com.sc.util.SingularHud.toolState(false, 200, 100, false, true) == com.sc.util.SingularHud.HIDDEN
                            && com.sc.util.SingularHud.toolState(true, 10, 100, false, true) == com.sc.util.SingularHud.HIDDEN,
                    "tools UI: tool cooldown icon states");
            check(com.sc.util.SingularHud.crumbShare(0, 64) == 0F && com.sc.util.SingularHud.crumbShare(32, 64) == 0.5F
                            && com.sc.util.SingularHud.crumbShare(100, 64) == 1F && com.sc.util.SingularHud.crumbShare(5, 0) == 0F,
                    "tools UI: the crumb bar's share");

            // the K menu's id ranges hold the grown enums (BLADE_BASE 500..599, DRILL_BASE 700..899)
            check(com.sc.util.BladeFeature.values().length <= 100 && com.sc.util.DrillFeature.values().length <= 200
                            && com.sc.util.BladeForm.values().length <= 20,
                    "tools UI: blade / drill features and blade forms fit the K menu's button id ranges");
        }
    }

    /** Singular blade & drill, polish (docs/plan-singular-tools.md). */
    private static void singToolsPolish() {
        {
            // Singular tools, stage 3 (polish): the handbook's articles and G on the new items
            com.sc.manual.BookEntry pbBlade = com.sc.manual.BookContent.byId("singularblade");
            com.sc.manual.BookEntry pbDrill = com.sc.manual.BookContent.byId("singulardrill");
            com.sc.manual.BookEntry pbCrumb = com.sc.manual.BookContent.byId("singularcrumb");
            ItemStack pbSb = new ItemStack(com.sc.init.ModItems.BLADES.get(com.sc.util.BladeType.SINGULAR));
            ItemStack pbSd = new ItemStack(com.sc.init.ModItems.DRILLS.get(com.sc.util.DrillType.SINGULAR));
            ItemStack pbEb = new ItemStack(com.sc.init.ModItems.BLADES.get(com.sc.util.BladeType.EXO));
            com.sc.manual.BookEntry pbOfClot = com.sc.manual.BookContent.entryFor(new ItemStack(com.sc.init.ModItems.singularClot));
            com.sc.manual.BookEntry pbOfCrumb = com.sc.manual.BookContent.entryFor(new ItemStack(com.sc.init.ModItems.singularCrumb));
            com.sc.manual.BookEntry pbOfSb = com.sc.manual.BookContent.entryFor(pbSb);
            com.sc.manual.BookEntry pbOfSd = com.sc.manual.BookContent.entryFor(pbSd);
            com.sc.manual.BookEntry pbOfEb = com.sc.manual.BookContent.entryFor(pbEb);
            check(pbBlade != null && pbDrill != null && pbCrumb != null && pbBlade.els.size() > 20 && pbDrill.els.size() > 20 && pbCrumb.els.size() > 5
                    && pbOfClot == pbCrumb && pbOfCrumb == pbCrumb && pbOfSb == pbBlade && pbOfSd == pbDrill
                    && pbOfEb != null && "blades".equals(pbOfEb.id)
                    && pbBlade.chapter == com.sc.manual.BookChapter.ARMOR && pbCrumb.chapter == com.sc.manual.BookChapter.ARMOR,
            "Singular tools polish: handbook articles - blade, drill, crumbs / clot (G opens them), the Exo blade still on «blades»");
            // the station article has the tool slot: both conversion cards link the tools' articles
            com.sc.manual.BookEntry pbSt = com.sc.manual.BookContent.byId("singularstation");
            StringBuilder pbText = new StringBuilder();
            for (com.sc.manual.BookEl el : pbSt.els) {
        el.collectText(pbText);
            }
            check(pbSt != null && pbText.indexOf(pbSb.getDisplayName()) >= 0 && pbText.indexOf(pbSd.getDisplayName()) >= 0,
            "Singular tools polish: the Singular station article names the Singular blade and drill (tool slot)");
            // the form attack's metadata the K menu / HUD show (sword: no cooldown, collapse: SM)
            check(com.sc.util.BladeFeature.formAttackCooldown(com.sc.util.BladeForm.SWORD) == 0
                    && com.sc.util.BladeFeature.formAttackGas(com.sc.util.BladeForm.SINGULAR) == com.sc.util.ArmorGasSC.Gas.SINGULAR_MATTER
                    && com.sc.util.BladeFeature.formAttackGasMb(com.sc.util.BladeForm.SINGULAR) > 0
                    && !com.sc.util.DrillFeature.BLACK_HOLE.isAction()
                    && com.sc.util.DrillZoneSC.effectiveHole(12, 3) == 9 && com.sc.util.DrillZoneSC.effectiveHole(12, 5) == 12,
            "Singular tools polish: form attack by form, the black hole a mode, its size gated by the level");
            com.sc.manual.BookContent.invalidate();
        }
    }

    /** Singular blade & drill, fixes (docs/plan-singular-tools.md). */
    private static void singToolsFixes() {
        // ---- Singular tools, review fixes: crumb-eligible blocks, the black hole's SM up front, the wheel's rate limit ----
        {
            // crumbs only for natural stone / ground / ore - nothing that regrows or forms from lava + water
            check(com.sc.item.DrillLogicSC.crumbBlock(net.minecraft.init.Blocks.stone, 0)
                            && com.sc.item.DrillLogicSC.crumbBlock(net.minecraft.init.Blocks.dirt, 0)
                            && com.sc.item.DrillLogicSC.crumbBlock(net.minecraft.init.Blocks.grass, 0)
                            && com.sc.item.DrillLogicSC.crumbBlock(net.minecraft.init.Blocks.sand, 0)
                            && com.sc.item.DrillLogicSC.crumbBlock(net.minecraft.init.Blocks.gravel, 0)
                            && com.sc.item.DrillLogicSC.crumbBlock(net.minecraft.init.Blocks.clay, 0)
                            && com.sc.item.DrillLogicSC.crumbBlock(net.minecraft.init.Blocks.netherrack, 0)
                            && com.sc.item.DrillLogicSC.crumbBlock(net.minecraft.init.Blocks.end_stone, 0)
                            && com.sc.item.DrillLogicSC.crumbBlock(net.minecraft.init.Blocks.iron_ore, 0)
                            && com.sc.item.DrillLogicSC.crumbBlock(net.minecraft.init.Blocks.lit_redstone_ore, 0),
                    "drill crumbs: stone, dirt, grass, sand, gravel, clay, netherrack, end stone and ores count");
            check(!com.sc.item.DrillLogicSC.crumbBlock(net.minecraft.init.Blocks.cobblestone, 0)
                            && !com.sc.item.DrillLogicSC.crumbBlock(net.minecraft.init.Blocks.mossy_cobblestone, 0)
                            && !com.sc.item.DrillLogicSC.crumbBlock(net.minecraft.init.Blocks.stonebrick, 0)
                            && !com.sc.item.DrillLogicSC.crumbBlock(net.minecraft.init.Blocks.obsidian, 0)
                            && !com.sc.item.DrillLogicSC.crumbBlock(net.minecraft.init.Blocks.log, 0)
                            && !com.sc.item.DrillLogicSC.crumbBlock(net.minecraft.init.Blocks.leaves, 0)
                            && !com.sc.item.DrillLogicSC.crumbBlock(net.minecraft.init.Blocks.planks, 0)
                            && !com.sc.item.DrillLogicSC.crumbBlock(net.minecraft.init.Blocks.pumpkin, 0)
                            && !com.sc.item.DrillLogicSC.crumbBlock(net.minecraft.init.Blocks.melon_block, 0)
                            && !com.sc.item.DrillLogicSC.crumbBlock(net.minecraft.init.Blocks.reeds, 0)
                            && !com.sc.item.DrillLogicSC.crumbBlock(net.minecraft.init.Blocks.cactus, 0)
                            && !com.sc.item.DrillLogicSC.crumbBlock(net.minecraft.init.Blocks.tallgrass, 1)
                            && !com.sc.item.DrillLogicSC.crumbBlock(net.minecraft.init.Blocks.snow, 0)
                            && !com.sc.item.DrillLogicSC.crumbBlock(net.minecraft.init.Blocks.snow_layer, 0)
                            && !com.sc.item.DrillLogicSC.crumbBlock(net.minecraft.init.Blocks.ice, 0)
                            && !com.sc.item.DrillLogicSC.crumbBlock(net.minecraft.init.Blocks.wool, 0)
                            && !com.sc.item.DrillLogicSC.crumbBlock(net.minecraft.init.Blocks.glass, 0)
                            && !com.sc.item.DrillLogicSC.crumbBlock(net.minecraft.init.Blocks.air, 0)
                            && !com.sc.item.DrillLogicSC.crumbBlock(null, 0),
                    "drill crumbs: no cobblestone / stone bricks / obsidian, logs, leaves, planks, gourds, cane, cactus, plants, snow, ice, wool, glass");
            // the black hole needs the whole zone's SM before it starts: 12x12x3 = 432 blocks at 25 a mB -> 18 mB
            check(com.sc.util.DrillZoneSC.holeGasNeed(432, 25) == 18 && com.sc.util.DrillZoneSC.holeGasNeed(25, 25) == 1
                            && com.sc.util.DrillZoneSC.holeGasNeed(26, 25) == 2 && com.sc.util.DrillZoneSC.holeGasNeed(0, 25) == 0
                            && com.sc.util.DrillZoneSC.holeGasNeed(144, 0) == 0,
                    "black hole: SM needed up front = the zone's volume / blocks per mB, rounded up");
            // the zone's real cost never exceeds what was checked: whole mB taken <= holeGasNeed for any carried fraction < 1
            float[] sfSplit = com.sc.util.ToolGasSC.split(0.99F, com.sc.util.DrillZoneSC.holeGas(432, 25));
            check((int) sfSplit[0] <= com.sc.util.DrillZoneSC.holeGasNeed(432, 25),
                    "black hole: the carried fraction never makes it take more SM than was checked");
            // doors, beds, tall plants are left by the black hole
            check(com.sc.item.DrillLogicSC.twoPart(net.minecraft.init.Blocks.wooden_door)
                            && com.sc.item.DrillLogicSC.twoPart(net.minecraft.init.Blocks.iron_door)
                            && com.sc.item.DrillLogicSC.twoPart(net.minecraft.init.Blocks.bed)
                            && com.sc.item.DrillLogicSC.twoPart(net.minecraft.init.Blocks.double_plant)
                            && !com.sc.item.DrillLogicSC.twoPart(net.minecraft.init.Blocks.stone),
                    "black hole: doors, beds and tall plants are skipped");
            // Shift + wheel: at least WHEEL_GAP ticks between two steps (time going back - a new world - lets it through)
            check(com.sc.item.BladeSingularSC.gapOk(100, 0, false, 2) && !com.sc.item.BladeSingularSC.gapOk(101, 100, true, 2)
                            && com.sc.item.BladeSingularSC.gapOk(102, 100, true, 2) && com.sc.item.BladeSingularSC.gapOk(5, 100, true, 2)
                            && !com.sc.item.BladeSingularSC.gapOk(100, 100, true, 2),
                    "tool wheel: a form / mode step at most once per WHEEL_GAP ticks");
        }
    }

    /** Singular blade & drill, formicons (docs/plan-singular-tools.md). */
    private static void singToolsFormicons() {
        // ---- Singular blade: an icon per form x scheme x lit, every name distinct
        {
            java.util.Set<String> fiNames = new java.util.HashSet<String>();
            boolean fiOk = true;
            int fiCount = 0;
            for (com.sc.util.BladeForm fiF : com.sc.util.BladeForm.values()) {
                for (com.sc.util.SingularScheme fiS : com.sc.util.SingularScheme.values()) {
                    for (int fiL = 0; fiL < 2; fiL++) {
                        String fiN = com.sc.item.ItemBladeSC.formIconName(fiF, fiS, fiL == 1);
                        fiOk &= fiN.startsWith(fiL == 1 ? "bladeSingularOn_" : "bladeSingular_")
                                && fiN.endsWith("_" + fiF.key() + "_" + fiS.key());
                        fiNames.add(fiN);
                        fiCount++;
                    }
                }
            }
            check(fiOk, "blade form icons: names follow bladeSingular[On]_<form>_<scheme>");
            check(fiNames.size() == fiCount, "blade form icons: " + fiCount + " names, all distinct (got " + fiNames.size() + ")");
            check(com.sc.item.ItemBladeSC.formIconName(null, null, false).equals("bladeSingular_sword_a"),
                    "blade form icons: null form / scheme fall back to sword / A");
        }
    }

    /** Singular blade & drill, booksearch (docs/plan-singular-tools.md). */
    private static void singToolsBooksearch() {
        // ---- Handbook search and bookmarks (BookSearchSC: pure helpers, no client classes) ----
        {
            // normalisation: §-codes out, whitespace collapsed, lower case, ё = е, length kept by fold
            check("елка ель еж".equals(com.sc.manual.BookSearchSC.norm("  §aЁлка \n ЕЛЬ\t §lЁж ")),
                    "book search: norm strips §-codes, collapses spaces, folds case and ё (got '" + com.sc.manual.BookSearchSC.norm("  §aЁлка \n ЕЛЬ\t §lЁж ") + "')");
            check(com.sc.manual.BookSearchSC.fold("ЁёAБ").equals("ееaб") && com.sc.manual.BookSearchSC.fold("ЁёAБ").length() == 4
                            && com.sc.manual.BookSearchSC.plain("abc§").equals("abc") && com.sc.manual.BookSearchSC.plain(null).isEmpty(),
                    "book search: fold keeps the length; a trailing § and null are safe");
            check(com.sc.manual.BookSearchSC.count("кабель кабелькабель", "кабель") == 3 && com.sc.manual.BookSearchSC.count("ааа", "аа") == 1
                            && com.sc.manual.BookSearchSC.count("x", "") == 0,
                    "book search: count is non-overlapping");

            // ranking: title start, then title, then body by the number of matches, then book order
            java.util.List<com.sc.manual.BookSearchSC.Doc> bsDocs = new java.util.ArrayList<com.sc.manual.BookSearchSC.Doc>();
            bsDocs.add(new com.sc.manual.BookSearchSC.Doc("d0", "Медный кабель", "Энергия", new String[]{"Кабель из меди."}, 0));
            bsDocs.add(new com.sc.manual.BookSearchSC.Doc("d1", "Генератор", "Генераторы", new String[]{"кабель кабель §lкабель", null, "ещё кабель"}, 1));
            bsDocs.add(new com.sc.manual.BookSearchSC.Doc("d2", "Провода: кабель", "Энергия", new String[]{"кабель"}, 2));
            bsDocs.add(new com.sc.manual.BookSearchSC.Doc("d3", "Печь", "Машины", new String[]{"Ничего"}, 3));
            bsDocs.add(new com.sc.manual.BookSearchSC.Doc("d4", "§eКабель-канал", "Энергия", new String[]{"Короб"}, 4));
            int[] bsTotal = new int[1];
            java.util.List<com.sc.manual.BookSearchSC.Hit> bsHits = com.sc.manual.BookSearchSC.search(bsDocs, "КАБЕЛЬ", 50, bsTotal);
            StringBuilder bsOrder = new StringBuilder();
            for (com.sc.manual.BookSearchSC.Hit h : bsHits) {
                bsOrder.append(h.doc.id).append(' ');
            }
            check("d4 d0 d2 d1 ".equals(bsOrder.toString()) && bsTotal[0] == 4,
                    "book search: ranked title-start, title (book order on a tie), then body by count (got " + bsOrder + ", total " + bsTotal[0] + ")");
            check(bsHits.size() == 4 && bsHits.get(0).rank == 0 && bsHits.get(1).rank == 1 && bsHits.get(3).rank == 2 && bsHits.get(3).count == 4,
                    "book search: rank classes and match counts");
            java.util.List<com.sc.manual.BookSearchSC.Hit> bsTop = com.sc.manual.BookSearchSC.search(bsDocs, "кабель", 2, bsTotal);
            check(bsTop.size() == 2 && bsTotal[0] == 4 && com.sc.manual.BookSearchSC.search(bsDocs, "к", 50, null).isEmpty(),
                    "book search: the limit cuts the list, the total stays; one letter finds nothing");
            com.sc.manual.BookSearchSC.Hit bsYo = com.sc.manual.BookSearchSC.match(bsDocs.get(1), com.sc.manual.BookSearchSC.norm("ЕЩЕ"));
            check(bsYo != null && bsYo.el == 2 && com.sc.manual.BookSearchSC.fold(bsDocs.get(1).body.substring(bsYo.at, bsYo.at + bsYo.len)).equals("еще"),
                    "book search: «еще» finds «ещё», in element 2, the offsets point into the shown body");
            com.sc.manual.BookSearchSC.Hit bsWords = com.sc.manual.BookSearchSC.match(bsDocs.get(1), com.sc.manual.BookSearchSC.norm("генератор ещё"));
            check(bsWords != null && bsWords.rank == 3 && com.sc.manual.BookSearchSC.match(bsDocs.get(3), "печь кабель") == null,
                    "book search: words apart match only when every word is there");
            com.sc.manual.BookSearchSC.Hit bsShort = com.sc.manual.BookSearchSC.match(bsDocs.get(1), com.sc.manual.BookSearchSC.norm("генератор и ещё"));
            check(bsShort != null && com.sc.manual.BookSearchSC.match(bsDocs.get(1), com.sc.manual.BookSearchSC.norm("е щ")) == null,
                    "book search: one-letter words apart don't count (Кн-4)");
            check(bsDocs.get(1).body.indexOf('§') < 0 && bsDocs.get(4).title.equals("Кабель-канал"),
                    "book search: no §-codes in the shown title and body");

            // snippets: around the match, cut at spaces, "..." where cut, bounds safe
            String bsText = "один два три четыре пять шесть семь восемь девять десять";
            com.sc.manual.BookSearchSC.Snip bsS = com.sc.manual.BookSearchSC.snippet(bsText, bsText.indexOf("пять"), 4, 8, 8);
            check(bsS.text.startsWith("...") && bsS.text.endsWith("...") && bsS.text.substring(bsS.hl, bsS.hl + bsS.hlLen).equals("пять")
                            && bsS.text.length() < bsText.length(),
                    "book search: a snippet around the match (got '" + bsS.text + "')");
            com.sc.manual.BookSearchSC.Snip bsHead = com.sc.manual.BookSearchSC.snippet(bsText, 0, 4, 8, 8);
            com.sc.manual.BookSearchSC.Snip bsTail = com.sc.manual.BookSearchSC.snippet(bsText, bsText.length() - 6, 6, 8, 8);
            com.sc.manual.BookSearchSC.Snip bsNone = com.sc.manual.BookSearchSC.snippet(bsText, -1, 3, 8, 8);
            com.sc.manual.BookSearchSC.Snip bsFar = com.sc.manual.BookSearchSC.snippet("abc", 1, 10, 5, 5);
            check(!bsHead.text.startsWith("...") && bsHead.hl == 0 && bsHead.text.substring(0, 4).equals("один")
                            && !bsTail.text.endsWith("...") && bsTail.text.substring(bsTail.hl).equals("десять")
                            && bsNone.hl == 0 && bsNone.hlLen == 0 && bsNone.text.startsWith("один")
                            && bsFar.text.equals("abc") && bsFar.hl == 1 && bsFar.hlLen == 2
                            && com.sc.manual.BookSearchSC.snippet("", 0, 0, 5, 5).text.isEmpty()
                            && com.sc.manual.BookSearchSC.snippet(bsText, 999, 2, 5, 5).hlLen == 0,
                    "book search: snippet bounds - text start / end, no match, a match past the end, empty text");

            // bookmarks: add / remove / limit / the file's lines round trip
            java.util.List<String> bsMarks = new java.util.ArrayList<String>();
            boolean bsOk = com.sc.manual.BookSearchSC.addMark(bsMarks, "keys", 64) && !com.sc.manual.BookSearchSC.addMark(bsMarks, "keys", 64)
                    && !com.sc.manual.BookSearchSC.addMark(bsMarks, " ", 64) && !com.sc.manual.BookSearchSC.addMark(bsMarks, "a\nb", 64);
            for (int i = 0; bsMarks.size() < com.sc.manual.BookSearchSC.MAX_MARKS; i++) {
                bsOk &= com.sc.manual.BookSearchSC.addMark(bsMarks, "m" + i, com.sc.manual.BookSearchSC.MAX_MARKS);
            }
            bsOk &= !com.sc.manual.BookSearchSC.addMark(bsMarks, "extra", com.sc.manual.BookSearchSC.MAX_MARKS) && bsMarks.size() == 64;
            bsOk &= com.sc.manual.BookSearchSC.removeMark(bsMarks, "m5") && !com.sc.manual.BookSearchSC.removeMark(bsMarks, "m5")
                    && com.sc.manual.BookSearchSC.addMark(bsMarks, "extra", 64) && "extra".equals(bsMarks.get(63)) && "keys".equals(bsMarks.get(0));
            check(bsOk, "bookmarks: no duplicates or blank ids, at most 64, removal frees a place, order kept");
            java.util.List<String> bsLines = new java.util.ArrayList<String>(com.sc.manual.BookSearchSC.marksToLines(bsMarks));
            bsLines.add(0, "# header");
            bsLines.add("done=sp:w|copper");
            bsLines.add("bm=keys");
            bsLines.add("bm=");
            check(com.sc.manual.BookSearchSC.marksFromLines(bsLines, 64).equals(bsMarks)
                            && com.sc.manual.BookSearchSC.marksFromLines(bsLines, 3).size() == 3,
                    "bookmarks: the file's lines round trip (other lines, duplicates and blanks skipped, capped)");

            // the real book: indexed lazily, rebuilt with the book, finds an article by its title, no §-codes
            java.util.List<com.sc.manual.BookSearchSC.Doc> bsIdx = com.sc.manual.BookSearchSC.index();
            com.sc.manual.BookEntry bsKeys = com.sc.manual.BookContent.byId("keys");
            boolean bsFound = false, bsClean = true;
            for (com.sc.manual.BookSearchSC.Hit h : com.sc.manual.BookSearchSC.searchBook(bsKeys.title, null)) {
                bsFound |= h.doc.id.equals("keys") && h.rank == 0;
            }
            for (com.sc.manual.BookSearchSC.Doc d : bsIdx) {
                bsClean &= d.body.indexOf('§') < 0 && d.title.indexOf('§') < 0 && !d.id.equals("recipes");
            }
            check(bsIdx.size() > 50 && bsIdx == com.sc.manual.BookSearchSC.index() && bsFound && bsClean,
                    "book search: the book's index (" + bsIdx.size() + " articles) finds «keys» by its title, no §-codes, no Recipes");
            boolean bsHead2 = false;
            for (com.sc.manual.BookEl el : bsKeys.els) {
                bsHead2 |= el.kind == com.sc.manual.BookEl.Kind.HEAD && com.sc.manual.Lang.tr("sc.book.keys.bookhead").equals(el.text);
            }
            check(bsHead2, "book search: the keys article has the search and bookmarks section");
            com.sc.manual.BookContent.invalidate();
            check(com.sc.manual.BookSearchSC.index() != bsIdx, "book search: the index is built again with the book");
        }
    }

    /** Singular blade & drill, netview (docs/plan-singular-tools.md). */
    private static void singToolsNetview() {
        // ---- network overview (NetViewScanSC): positions, bounded walk, flow estimate, load colours, clicks, snapshot round trip
        {
            long nvP = com.sc.util.NetViewScanSC.pack(-123456, 255, 987654);
            check(com.sc.util.NetViewScanSC.unpackX(nvP) == -123456 && com.sc.util.NetViewScanSC.unpackY(nvP) == 255
                            && com.sc.util.NetViewScanSC.unpackZ(nvP) == 987654
                            && com.sc.util.NetViewScanSC.unpackZ(com.sc.util.NetViewScanSC.pack(5, 0, -7)) == -7,
                    "netview: position pack / unpack (negative coordinates)");

            final int nvLen = 50;
            com.sc.util.NetViewScanSC.Graph nvLine = new com.sc.util.NetViewScanSC.Graph() {
                public void links(long c, java.util.List<Long> out) {
                    int x = com.sc.util.NetViewScanSC.unpackX(c);
                    if (x > 0) {
                        out.add(com.sc.util.NetViewScanSC.pack(x - 1, 64, 0));
                    }
                    if (x < nvLen - 1) {
                        out.add(com.sc.util.NetViewScanSC.pack(x + 1, 64, 0));
                    }
                }

                public void endpoints(long c, java.util.List<Long> out) {
                    int x = com.sc.util.NetViewScanSC.unpackX(c);
                    if (x % 5 == 0) {
                        out.add(com.sc.util.NetViewScanSC.pack(x, 65, 0));
                    }
                }
            };
            long nvStart = com.sc.util.NetViewScanSC.pack(25, 64, 0);
            com.sc.util.NetViewScanSC.Scan nvAll = com.sc.util.NetViewScanSC.scan(nvLine, nvStart, 4096, 512);
            check(nvAll.cables.size() == 50 && nvAll.endpoints.size() == 10 && !nvAll.truncated
                            && nvAll.cables.get(0) == nvStart && nvAll.links.get(0).length == 2,
                    "netview: walk finds the whole line (50 cables, 10 blocks)");
            com.sc.util.NetViewScanSC.Scan nvCut = com.sc.util.NetViewScanSC.scan(nvLine, nvStart, 20, 512);
            check(nvCut.cables.size() == 20 && nvCut.truncated, "netview: cable bound cuts the walk and flags it");
            com.sc.util.NetViewScanSC.Scan nvCutE = com.sc.util.NetViewScanSC.scan(nvLine, nvStart, 4096, 3);
            check(nvCutE.endpoints.size() == 3 && nvCutE.truncated && nvCutE.cables.size() == 50,
                    "netview: endpoint bound cuts the blocks, not the cables");

            // source at cable 0, consumer at cable 3, 1 EU loss a block
            com.sc.util.NetViewScanSC.Graph nvFour = new com.sc.util.NetViewScanSC.Graph() {
                public void links(long c, java.util.List<Long> out) {
                    int x = com.sc.util.NetViewScanSC.unpackX(c);
                    if (x > 0) {
                        out.add(com.sc.util.NetViewScanSC.pack(x - 1, 64, 0));
                    }
                    if (x < 3) {
                        out.add(com.sc.util.NetViewScanSC.pack(x + 1, 64, 0));
                    }
                }

                public void endpoints(long c, java.util.List<Long> out) {
                    int x = com.sc.util.NetViewScanSC.unpackX(c);
                    if (x == 0 || x == 3) {
                        out.add(com.sc.util.NetViewScanSC.pack(x, 65, 0));
                    }
                }
            };
            com.sc.util.NetViewScanSC.Scan nvS4 = com.sc.util.NetViewScanSC.scan(nvFour, com.sc.util.NetViewScanSC.pack(0, 64, 0), 4096, 512);
            int[] nvRole = {com.sc.util.NetViewScanSC.R_OUT, com.sc.util.NetViewScanSC.R_IN};
            com.sc.util.NetViewScanSC.Flow nvF = com.sc.util.NetViewScanSC.estimate(nvS4, nvRole, new int[]{100, 0}, new int[]{0, 50}, 1, 128);
            boolean nvPath = true;
            for (int nvI = 0; nvI < 4; nvI++) {
                nvPath &= nvF.cableFlow[nvI] == 54;
            }
            check(nvS4.endpoints.size() == 2 && nvF.taken[1] == 50 && nvF.loss == 4 && nvF.given[0] == 54 && nvPath,
                    "netview: estimate - 50 EU/t delivered, 4 lost over 4 cables, 54 on every cable of the path");
            com.sc.util.NetViewScanSC.Flow nvF2 = com.sc.util.NetViewScanSC.estimate(nvS4, nvRole, new int[]{100, 0}, new int[]{0, 50}, 1, 30);
            check(nvF2.given[0] == 30 && nvF2.taken[1] == 26 && nvF2.cableFlow[3] == 30,
                    "netview: estimate - the cable rating caps the flow");

            check(com.sc.util.NetViewScanSC.level(49, 100, 0) == com.sc.util.NetViewScanSC.L_LOW
                            && com.sc.util.NetViewScanSC.level(50, 100, 0) == com.sc.util.NetViewScanSC.L_MID
                            && com.sc.util.NetViewScanSC.level(89, 100, 0) == com.sc.util.NetViewScanSC.L_MID
                            && com.sc.util.NetViewScanSC.level(90, 100, 0) == com.sc.util.NetViewScanSC.L_HIGH
                            && com.sc.util.NetViewScanSC.level(100, 100, 0) == com.sc.util.NetViewScanSC.L_HIGH
                            && com.sc.util.NetViewScanSC.level(101, 100, 0) == com.sc.util.NetViewScanSC.L_OVER
                            && com.sc.util.NetViewScanSC.level(0, 100, com.sc.util.NetViewScanSC.C_BOTTLENECK) == com.sc.util.NetViewScanSC.L_HIGH
                            && com.sc.util.NetViewScanSC.level(0, 100, com.sc.util.NetViewScanSC.C_OVERVOLT) == com.sc.util.NetViewScanSC.L_OVER,
                    "netview: load colours (green < 50%, yellow < 90%, red >= 90% / bottleneck, pulsing past the rating / overvolt)");

            com.sc.util.NetViewScanSC.Clicks nvC = new com.sc.util.NetViewScanSC.Clicks();
            Object nvWho = new Object(), nvOther = new Object();
            int nvC1 = nvC.click(nvWho, 1000), nvC2 = nvC.click(nvWho, 1004), nvC3 = nvC.click(nvWho, 1010);
            int nvC4 = nvC.click(nvWho, 1019), nvC5 = nvC.click(nvWho, 1040), nvC6 = nvC.click(nvOther, 1004);
            check(nvC1 == com.sc.util.NetViewScanSC.CLICK_SCAN && nvC2 == com.sc.util.NetViewScanSC.CLICK_IGNORE
                            && nvC3 == com.sc.util.NetViewScanSC.CLICK_IGNORE && nvC4 == com.sc.util.NetViewScanSC.CLICK_PING
                            && nvC5 == com.sc.util.NetViewScanSC.CLICK_SCAN && nvC6 == com.sc.util.NetViewScanSC.CLICK_SCAN,
                    "netview: held button clicks once, one scan a second per player, others independent");

            com.sc.util.NetViewScanSC.Snapshot nvSn = new com.sc.util.NetViewScanSC.Snapshot();
            nvSn.dim = -1;
            nvSn.ox = -1000;
            nvSn.oy = 70;
            nvSn.oz = 2000;
            nvSn.flags = com.sc.util.NetViewScanSC.S_TRUNCATED | com.sc.util.NetViewScanSC.S_MEASURED;
            nvSn.cables(2);
            nvSn.cx[0] = -1000; nvSn.cy[0] = 70; nvSn.cz[0] = 2000;
            nvSn.cx[1] = -4095; nvSn.cy[1] = 71; nvSn.cz[1] = 2001;
            nvSn.ctype[1] = 4;
            nvSn.cflags[1] = (byte) com.sc.util.NetViewScanSC.C_OVERVOLT;
            nvSn.cflow[1] = 123456;
            nvSn.endpoints(1);
            nvSn.ex[0] = -999; nvSn.ey[0] = 70; nvSn.ez[0] = 2000;
            nvSn.ekind[0] = (byte) com.sc.util.NetViewScanSC.K_STORAGE;
            nvSn.etier[0] = 3;
            nvSn.erole[0] = 3;
            nvSn.erate[0] = 2048;
            nvSn.epct[0] = -1;
            nvSn.gen = 5000000000L;
            nvSn.cons = 7;
            nvSn.loss = 3;
            nvSn.stored = 1L << 40;
            nvSn.capacity = (1L << 41) + 1;
            nvSn.maxSourceTier = -1;
            nvSn.bottlenecks = 2;
            nvSn.hidden = 9;
            io.netty.buffer.ByteBuf nvBuf = io.netty.buffer.Unpooled.buffer();
            com.sc.util.NetViewScanSC.encode(nvBuf, nvSn);
            com.sc.util.NetViewScanSC.Snapshot nvBack = com.sc.util.NetViewScanSC.decode(nvBuf);
            check(nvBack.dim == -1 && nvBack.ox == -1000 && nvBack.oy == 70 && nvBack.oz == 2000 && nvBack.flags == nvSn.flags
                            && nvBack.n == 2 && nvBack.cx[1] == -4095 && nvBack.cy[1] == 71 && nvBack.cz[1] == 2001
                            && nvBack.ctype[1] == 4 && nvBack.cflags[1] == com.sc.util.NetViewScanSC.C_OVERVOLT && nvBack.cflow[1] == 123456
                            && nvBack.m == 1 && nvBack.ex[0] == -999 && nvBack.ekind[0] == com.sc.util.NetViewScanSC.K_STORAGE
                            && nvBack.etier[0] == 3 && nvBack.erole[0] == 3 && nvBack.erate[0] == 2048 && nvBack.epct[0] == -1
                            && nvBack.gen == 5000000000L && nvBack.cons == 7 && nvBack.loss == 3 && nvBack.stored == 1L << 40
                            && nvBack.capacity == (1L << 41) + 1 && nvBack.maxSourceTier == -1 && nvBack.bottlenecks == 2
                            && nvBack.hidden == 9 && nvBuf.readableBytes() == 0 && nvBack.hasCable(-4095, 71, 2001),
                    "netview: snapshot encode / decode round trip");
            com.sc.util.NetViewScanSC.Snapshot nvPing = new com.sc.util.NetViewScanSC.Snapshot();
            nvPing.flags = com.sc.util.NetViewScanSC.S_PING;
            nvPing.ox = 5;
            io.netty.buffer.ByteBuf nvBuf2 = io.netty.buffer.Unpooled.buffer();
            com.sc.util.NetViewScanSC.encode(nvBuf2, nvPing);
            com.sc.util.NetViewScanSC.Snapshot nvPingBack = com.sc.util.NetViewScanSC.decode(nvBuf2);
            check(nvPingBack.flags == com.sc.util.NetViewScanSC.S_PING && nvPingBack.ox == 5 && nvPingBack.n == 0
                            && nvPingBack.m == 0 && nvBuf2.readableBytes() == 0,
                    "netview: ping (toggle-only click) round trip");
            check("1234".equals(com.sc.util.NetViewScanSC.compact(1234)) && "45.7k".equals(com.sc.util.NetViewScanSC.compact(45678))
                            && "3.2M".equals(com.sc.util.NetViewScanSC.compact(3200000)) && "12.0M".equals(com.sc.util.NetViewScanSC.compact(12000000)),
                    "netview: compact EU numbers");
        }
    }
}
