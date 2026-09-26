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
            fieldShapes();
            fluidDrops();
            energyStorage();
            electricArmor();
            transformers();
            energySplit();
            conduitBundles();
            machineSidesAndUpgrades();
            tubeFilters();
            portableTanks();
            armorFunctions();
            bladeFunctions();
            chargePad();
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

    /** Every handbook tab builds, has content, and no untranslated key or "%" leaks through. */
    private static void handbookPagesBuild() {
        StringBuilder bad = new StringBuilder();
        int total = 0;
        for (com.sc.manual.ManualTab tab : com.sc.manual.ManualTab.values()) {
            List<String> lines = com.sc.manual.ManualContent.linesFor(tab, tab == com.sc.manual.ManualTab.RECIPES ? "" : null);
            total += lines.size();
            if (lines.size() < 5) {
                bad.append(tab).append(" too short; ");
            }
            for (String l : lines) {
                if (l.contains("sc.manual.") || l.contains("sc.biome.") || l.contains("sc.suit.") || l.contains("%d") || l.contains("%s")) {
                    bad.append(tab).append(": ").append(l).append("; ");
                }
            }
            if (Boolean.getBoolean("sc.selftest.print")) {
                for (String l : lines) {
                    System.out.println("[SC-PAGE] " + tab + " | " + l.replaceAll("\u00a7.", ""));
                }
            }
        }
        List<String> search = com.sc.manual.ManualContent.linesFor(com.sc.manual.ManualTab.RECIPES, "wafer");
        check(search.size() > 5, "handbook search 'wafer' finds recipes (" + search.size() + " lines)");
        check(bad.length() == 0, "handbook: all 9 tabs build, " + total + " lines, no raw keys" + (bad.length() == 0 ? "" : " -> " + bad));
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
        check(t.getLowTier() == com.sc.energy.Tier.QV && t.getHighTier() == com.sc.energy.Tier.XV, "top transformer is QV-XV");
        com.sc.tileentity.TileEntityEnergyStorageSC xv = new com.sc.tileentity.TileEntityEnergyStorageSC();
        xv.setStorageTier(com.sc.energy.Tier.XV);
        com.sc.tileentity.TileEntityEnergyStorageSC qv = new com.sc.tileentity.TileEntityEnergyStorageSC();
        qv.setStorageTier(com.sc.energy.Tier.QV);
        check(xv.getMaxEnergyStored() == 2000000000 && xv.outputTier().getVoltage() == 32768
                        && qv.getMaxEnergyStored() == 1000000000 && qv.outputTier().getVoltage() == 16384
                        && com.sc.energy.CableType.EXO.tier == com.sc.energy.Tier.XV
                        && com.sc.energy.Tier.QV.toIc2Tier() == 6 && com.sc.energy.Tier.IV.toIc2Tier() == 5
                        && com.sc.block.BlockTransformerSC.VARIANTS == 6,
                "tiers above EV: XV storage 2 000 000 000 EU / 32768 EU/t, QV half of it, cables, transformers, IC2 tiers");
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
            everySide &= m.getAccessibleSlotsFromSide(side).length == TileEntityMachineSC.INPUT_SLOTS + TileEntityMachineSC.OUTPUT_SLOTS
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
        TileEntityMachineSC u = new TileEntityMachineSC();
        u.setMachineType(MachineType.CRUSHER);
        u.setInventorySlotContents(TileEntityMachineSC.FIRST_UPGRADE_SLOT, ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.UNIVERSAL_TRANSFORMER));
        check(u.inputTier() == com.sc.energy.Tier.XV && u.receiveEnergy(net.minecraftforge.common.util.ForgeDirection.UNKNOWN, 32768, 100, true) == 100,
                "universal transformer upgrade: an LV machine takes any voltage, XV included");
        boolean hidden = true;
        for (int slot : c.getAccessibleSlotsFromSide(1)) {
            hidden &= slot < TileEntityMachineSC.FIRST_UPGRADE_SLOT;
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
        check(nano == 6 && quantum == 13 && exo == 20 && com.sc.util.ArmorFeature.ANNIHILATION.isAction()
                        && !com.sc.util.ArmorFeature.ANNIHILATION.availableIn(com.sc.util.ArmorSuit.QUANTUM, 1),
                "suit functions: Nano 6, Quantum 13 (flight from Quantum), Exo all 20 incl. the annihilation pulse, regeneration, explosion proofing (" + nano + "/" + quantum + "/" + exo + ")");

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
        ev &= f.inputTier() == com.sc.energy.Tier.XV;
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
}
