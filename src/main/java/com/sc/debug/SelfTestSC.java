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
            metalBlocks();
            electrolysisAndHeavyWater();
            drills();
            fieldExtras();
        } catch (Throwable t) {
            fail("exception: " + t);
            t.printStackTrace();
        }
        System.out.println("[SC-TEST] SUMMARY passed=" + passed + " failed=" + failed);
    }

    private static void check(boolean ok, String what) {
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
        check(odLv.ratedOutput() == 72 && odLv.outputTier() == com.sc.energy.Tier.LV && odLv.packetsPerTick() == 3
                        && odLv.sendMultibleEnergyPackets() && odLv.getMultibleEnergyPacketAmount() == 3,
                "overdrive: 72 EU/t at LV goes out as 3 packets of 32 a tick, not 1 and a stalled buffer");
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
    private static final int SINGULAR_OWN = 22;

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
        int[] count = new int[3];
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
        int[] count = new int[3];
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
}
