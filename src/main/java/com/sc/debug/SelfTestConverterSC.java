package com.sc.debug;

import com.sc.energy.ForeignEnergySC;
import com.sc.energy.ForeignEnergySC.Kind;
import com.sc.energy.Tier;
import com.sc.init.ModBlocks;
import com.sc.init.ModItems;
import com.sc.item.ItemConverterModuleSC;
import com.sc.machine.UpgradeType;
import com.sc.tileentity.TileEntityEnergyConverterSC;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Self-checks of the Energy Converter (run from SelfTestSC): the rate / loss arithmetic, the pair
 * switch's refund (and its refusal), the balance, throughput and buffers with modules, which pairs a
 * converter offers with and without the other mods, the faces' settings through NBT, the item NBT, and
 * that the recipes exist exactly when some other energy does.
 */
final class SelfTestConverterSC {

    private SelfTestConverterSC() {
    }

    private static void check(boolean ok, String what) {
        SelfTestSC.check(ok, "converter: " + what);
    }

    static void run() {
        math();
        refund();
        balance();
        modules();
        availability();
        sidesNbt();
        itemNbt();
        conversionTick();
        rfWhole();
        firstNetJoin();
        mekPullCounted();
        unknownSide();
        switchCache();
        converterNeighbour();
        recipes();
        texts();
    }

    /** RF is whole: a fractional room isn't filled for free, a fractional rest isn't given away and lost. */
    private static void rfWhole() {
        boolean was = ForeignEnergySC.testAllPresent;
        ForeignEnergySC.testAllPresent = true;
        try {
            TileEntityEnergyConverterSC te = TileEntityEnergyConverterSC.create();
            te.refreshForTest();
            te.setPairForTest(Kind.RF.ordinal());
            double cap = te.foreignCapacity();
            net.minecraftforge.common.util.ForgeDirection north = net.minecraftforge.common.util.ForgeDirection.NORTH;
            te.setForeignForTest(cap - 0.6);
            int r0 = te.receiveEnergy(north, 100, false);
            boolean noFree = r0 == 0 && Math.abs(te.getForeign() - (cap - 0.6)) < 1e-6;
            te.setForeignForTest(cap - 5.6);
            int r5 = te.receiveEnergy(north, 100, false);
            boolean whole = r5 == 5 && Math.abs(te.getForeign() - (cap - 0.6)) < 1e-6;
            te.setMode(north.ordinal(), TileEntityEnergyConverterSC.MODE_OUT);
            te.setBuf(north.ordinal(), TileEntityEnergyConverterSC.BUF_X);
            te.recomputeKinds();
            te.setForeignForTest(10.5);
            int sim = te.extractEnergy(north, 100, true);
            int e = te.extractEnergy(north, 100, false);
            boolean out = sim == 10 && e == 10 && Math.abs(te.getForeign() - 0.5) < 1e-9;
            check(noFree && whole && out, "RF in / out whole: 0.6 RF of room takes " + r0 + ", 5.6 takes " + r5
                    + ", 10.5 RF in the buffer gives " + e + " and keeps " + te.getForeign());
        } finally {
            ForeignEnergySC.testAllPresent = was;
        }
    }

    /** A Mekanism cable pulling J (setEnergy lower) is booked as this tick's output. */
    private static void mekPullCounted() {
        boolean was = ForeignEnergySC.testAllPresent;
        ForeignEnergySC.testAllPresent = true;
        try {
            TileEntityEnergyConverterSC te = TileEntityEnergyConverterSC.create();
            te.setInventorySlotContents(TileEntityEnergyConverterSC.FIRST_MODULE, ModItems.converterModule.stackOf(ItemConverterModuleSC.Kind.CARD_MEKANISM));
            te.refreshForTest();
            te.setPairForTest(Kind.J.ordinal());
            te.setMode(3, TileEntityEnergyConverterSC.MODE_OUT);
            te.setBuf(3, TileEntityEnergyConverterSC.BUF_X);
            te.recomputeKinds();
            te.setForeignForTest(1000);
            te.nextTickForTest();
            te.setEnergy(400);
            check(near(te.xOutTickForTest(), 600) && near(te.getForeign(), 400),
                    "a Mekanism cable pulling 600 J through setEnergy is booked as output: " + te.xOutTickForTest() + " J this tick");
        } finally {
            ForeignEnergySC.testAllPresent = was;
        }
    }

    /** No face given (a wireless charger): energy only comes in when some face is an input for it (mode, buffer, filter). */
    private static void unknownSide() {
        boolean was = ForeignEnergySC.testAllPresent;
        ForeignEnergySC.testAllPresent = true;
        try {
            net.minecraftforge.common.util.ForgeDirection u = net.minecraftforge.common.util.ForgeDirection.UNKNOWN;
            TileEntityEnergyConverterSC te = TileEntityEnergyConverterSC.create();
            te.refreshForTest();
            te.setPairForTest(Kind.RF.ordinal());
            boolean allIn = te.acceptForeign(Kind.RF, u, 10, true) > 0 && te.receiveEnergy(u, 128, 10, true) == 10;
            for (int s = 0; s < 6; s++) {
                te.setMode(s, TileEntityEnergyConverterSC.MODE_OUT);
            }
            boolean allOut = te.acceptForeign(Kind.RF, u, 10, true) == 0 && te.receiveEnergy(u, 128, 10, true) == 0 && !te.acceptsFrom(u)
                    && !te.acceptsForeignFrom(u);
            te.setMode(2, TileEntityEnergyConverterSC.MODE_IN);
            te.setBuf(2, TileEntityEnergyConverterSC.BUF_EU);
            boolean euOnly = te.acceptForeign(Kind.RF, u, 10, true) == 0 && te.receiveEnergy(u, 128, 10, true) == 10;
            te.setBuf(2, TileEntityEnergyConverterSC.BUF_AUTO);
            te.setFilter(2, TileEntityEnergyConverterSC.filterBit(Kind.RF));
            boolean rfOnly = te.acceptForeign(Kind.RF, u, 10, true) > 0 && te.receiveEnergy(u, 128, 10, true) == 0;
            te.setFilter(2, 0);
            boolean none = te.acceptForeign(Kind.RF, u, 10, true) == 0 && te.receiveEnergy(u, 128, 10, true) == 0;
            check(allIn && allOut && euOnly && rfOnly && none, "no face given: all inputs take EU and RF (" + allIn + "), all outputs nothing ("
                    + allOut + "), an EU input only EU (" + euOnly + "), an RF-filtered input only RF (" + rfOnly + "), a closed filter nothing (" + none + ")");
        } finally {
            ForeignEnergySC.testAllPresent = was;
        }
    }

    /** The redstone is asked once a tick however often the nets ask; the switch and the redstone mode act at once. */
    private static void switchCache() {
        TileEntityEnergyConverterSC te = TileEntityEnergyConverterSC.create();
        te.refreshForTest();
        te.setRedstoneMode(1);
        te.nextTickForTest();
        int c0 = te.redstoneChecksForTest();
        for (int i = 0; i < 5; i++) {
            te.switchedOnForTest();
            te.demandedEnergy();
            te.receiveEnergy(net.minecraftforge.common.util.ForgeDirection.NORTH, 128, 1, true);
        }
        boolean once = te.redstoneChecksForTest() - c0 == 1;
        te.nextTickForTest();
        te.switchedOnForTest();
        te.demandedEnergy();
        boolean nextTick = te.redstoneChecksForTest() - c0 == 2;
        te.setRedstoneMode(2);
        te.switchedOnForTest();
        boolean modeChange = te.redstoneChecksForTest() - c0 == 3;
        te.setPowerOn(false);
        boolean off = !te.switchedOnForTest() && te.demandedEnergy() == 0;
        te.setPowerOn(true);
        boolean on = te.switchedOnForTest();
        te.setRedstoneMode(0);
        int c1 = te.redstoneChecksForTest();
        te.nextTickForTest();
        te.switchedOnForTest();
        boolean noSignalAsked = te.redstoneChecksForTest() == c1;
        check(once && nextTick && modeChange && off && on && noSignalAsked, "switchedOn: the redstone asked once a tick (" + once + "), again the next tick ("
                + nextTick + ") and on a mode change (" + modeChange + "), the switch at once (" + off + "/" + on + "), never with mode «always» ("
                + noSignalAsked + ")");
    }

    /** An «Авто» output beside another converter sends the other energy only when that converter's face takes it in. */
    private static void converterNeighbour() {
        boolean was = ForeignEnergySC.testAllPresent;
        ForeignEnergySC.testAllPresent = true;
        try {
            net.minecraftforge.common.util.ForgeDirection n = net.minecraftforge.common.util.ForgeDirection.NORTH;
            TileEntityEnergyConverterSC b = TileEntityEnergyConverterSC.create();
            b.refreshForTest();
            b.setPairForTest(Kind.RF.ordinal());
            boolean in = TileEntityEnergyConverterSC.converterTakes(b, Kind.RF, n);
            boolean otherKind = !TileEntityEnergyConverterSC.converterTakes(b, Kind.J, n);
            b.setBuf(n.ordinal(), TileEntityEnergyConverterSC.BUF_EU);
            boolean euBuf = !TileEntityEnergyConverterSC.converterTakes(b, Kind.RF, n);
            b.setBuf(n.ordinal(), TileEntityEnergyConverterSC.BUF_AUTO);
            b.setFilter(n.ordinal(), TileEntityEnergyConverterSC.F_EU);
            boolean filtered = !TileEntityEnergyConverterSC.converterTakes(b, Kind.RF, n);
            b.setFilter(n.ordinal(), TileEntityEnergyConverterSC.F_ALL);
            b.setMode(n.ordinal(), TileEntityEnergyConverterSC.MODE_OUT);
            boolean out = !TileEntityEnergyConverterSC.converterTakes(b, Kind.RF, n);
            check(in && otherKind && euBuf && filtered && out, "a converter neighbour takes RF through an «Авто» input (" + in + "), not another energy ("
                    + otherKind + "), not through an EU-buffer face (" + euBuf + "), an EU-filtered face (" + filtered + ") or an output (" + out + ")");
        } finally {
            ForeignEnergySC.testAllPresent = was;
        }
    }

    /** An EU output face known on the first look: the energy net is told again (it was joined with no output faces). */
    private static void firstNetJoin() {
        TileEntityEnergyConverterSC plain = TileEntityEnergyConverterSC.create();
        plain.recomputeKinds();
        boolean quiet = !plain.netRefreshDueForTest();
        TileEntityEnergyConverterSC te = TileEntityEnergyConverterSC.create();
        te.setMode(3, TileEntityEnergyConverterSC.MODE_OUT);
        te.setBuf(3, TileEntityEnergyConverterSC.BUF_EU);
        te.recomputeKinds();
        check(quiet && te.netRefreshDueForTest() && te.outputFaces().length == 1,
                "first face look: all inputs - the net left alone, an EU output - the net told again");
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-6 * Math.max(1, Math.abs(b));
    }

    /** 1 EU = 4 RF = 10 J at 5 % loss; the Efficiency modules 5 -> 3 -> 1 (a third doesn't count). */
    private static void math() {
        boolean rates = near(Kind.RF.perEu(), 4) && near(Kind.J.perEu(), 10) && Kind.GJ.perEu() > 0.5;
        boolean conv = near(ForeignEnergySC.euToX(1000, 4, 5), 3800) && near(ForeignEnergySC.xToEu(4000, 4, 5), 950)
                && near(ForeignEnergySC.euToX(100, 10, 5), 950) && near(ForeignEnergySC.xToEu(1000, 10, 0), 100);
        boolean loss = ForeignEnergySC.lossPercent(0) == 5 && ForeignEnergySC.lossPercent(1) == 3 && ForeignEnergySC.lossPercent(2) == 1
                && ForeignEnergySC.lossPercent(3) == 1;
        // a round trip loses twice: 1000 EU -> 3800 RF -> 3610 EU
        boolean trip = near(ForeignEnergySC.xToEu(ForeignEnergySC.euToX(1000, 4, 5), 4, 5), 902.5);
        check(rates && conv && loss && trip, "rates 1 EU = " + Kind.RF.perEu() + " RF = " + Kind.J.perEu() + " J = " + Kind.GJ.perEu()
                + " gJ, 1000 EU -> " + ForeignEnergySC.euToX(1000, 4, 5) + " RF, loss 5/3/1 % with 0/1/2 Efficiency modules, round trip "
                + ForeignEnergySC.xToEu(ForeignEnergySC.euToX(1000, 4, 5), 4, 5) + " EU");
    }

    /** The pair switch: the old buffer comes back as EU with the loss; no room - refused, nothing changes. */
    private static void refund() {
        boolean statics = ForeignEnergySC.refund(400000, 4, 5, 1000000) == 95000 && ForeignEnergySC.refund(400000, 4, 5, 94999) == -1
                && ForeignEnergySC.refund(0, 10, 5, 0) == 0;
        boolean was = ForeignEnergySC.testAllPresent;
        ForeignEnergySC.testAllPresent = true;
        try {
            TileEntityEnergyConverterSC te = TileEntityEnergyConverterSC.create();
            te.setInventorySlotContents(TileEntityEnergyConverterSC.FIRST_MODULE, ModItems.converterModule.stackOf(ItemConverterModuleSC.Kind.CARD_MEKANISM));
            te.refreshForTest();
            te.setPairForTest(Kind.RF.ordinal());
            te.setForeignForTest(400000);                                   // 400 000 RF -> 95 000 EU
            int r = te.switchPair(Kind.J.ordinal());
            boolean ok = r == TileEntityEnergyConverterSC.SW_OK && te.getEnergyStored() == 95000 && te.pairKind() == Kind.J && te.getForeign() == 0;
            // full EU buffer: refused, the J stays
            te.addEnergyForTest(te.getMaxEnergyStored());
            te.setForeignForTest(1000000);                                   // 1 000 000 J -> 95 000 EU, no room
            int r2 = te.switchPair(Kind.RF.ordinal());
            boolean refused = r2 == TileEntityEnergyConverterSC.SW_NO_ROOM && te.pairKind() == Kind.J && te.getForeign() == 1000000;
            // gJ without its card: not offered
            int r3 = te.switchPair(Kind.GJ.ordinal());
            check(statics && ok && refused && r3 == TileEntityEnergyConverterSC.SW_UNAVAILABLE,
                    "pair switch RF -> J gives back 95 000 EU for 400 000 RF (" + r + ", " + te.getEnergyStored() + " EU), refused with a full EU buffer ("
                            + r2 + "), gJ without its card refused (" + r3 + ")");
        } finally {
            ForeignEnergySC.testAllPresent = was;
        }
    }

    /** «Баланс»: towards equally full buffers, within the throughput, nothing inside the dead band. */
    private static void balance() {
        long euCap = 1000000;
        double xCap = 4000000;
        long a = ForeignEnergySC.balanceStep(800000, euCap, 0, xCap, 4, 5, 10000000);
        // after it: (800000 - a) / 1e6 vs a * 3.8 / 4e6 - equal
        double fe = (800000 - a) / 1e6, fx = a * 3.8 / 4e6;
        boolean eq = a > 0 && Math.abs(fe - fx) < 0.001;
        long b = ForeignEnergySC.balanceStep(800000, euCap, 0, xCap, 4, 5, 512);       // the throughput caps it
        long c = ForeignEnergySC.balanceStep(100000, euCap, 3000000, xCap, 4, 5, 10000000);   // X fuller: EU made
        double fe2 = (100000 - c) / 1e6, fx2 = (3000000 + c / 0.95 * 4) / 4e6;              // c < 0: -c EU made of -c/0.95 EU worth
        boolean eq2 = c < 0 && Math.abs(fe2 - fx2) < 0.002;
        long d = ForeignEnergySC.balanceStep(500000, euCap, 2000000 + 40000, xCap, 4, 5, 10000000);   // 50 % vs 51 %: dead band
        check(eq && b == 512 && eq2 && d == 0, "balance: 80 % EU / empty RF moves " + a + " EU (both " + Math.round(fe * 1000) / 10.0
                + " %), capped to the throughput (" + b + "), the other way makes " + (-c) + " EU, 50 / 51 % left alone (" + d + ")");
    }

    /** Throughput and buffers with the modules: MV 128; 2 Transformers + 1 Amplifier EV x2 = 4096; Universal SV; 4 storage x256. */
    private static void modules() {
        TileEntityEnergyConverterSC te = TileEntityEnergyConverterSC.create();
        te.refreshForTest();
        boolean base = te.throughput() == 128 && te.euTier() == Tier.MV && te.packets() == 1 && te.getMaxEnergyStored() == 1000000;
        ItemStack tr = ModItems.upgrade.stackOf(UpgradeType.TRANSFORMER);
        tr.stackSize = 2;
        te.setInventorySlotContents(TileEntityEnergyConverterSC.FIRST_MODULE, tr);
        te.setInventorySlotContents(TileEntityEnergyConverterSC.FIRST_MODULE + 1, ModItems.converterModule.stackOf(ItemConverterModuleSC.Kind.AMPLIFIER));
        boolean ev = te.euTier() == Tier.EV && te.packets() == 2 && te.throughput() == 4096 && te.outputTier() == Tier.EV && te.inputTier() == Tier.EV;
        ItemStack amps = ModItems.converterModule.stackOf(ItemConverterModuleSC.Kind.AMPLIFIER);
        amps.stackSize = 4;
        te.setInventorySlotContents(TileEntityEnergyConverterSC.FIRST_MODULE + 1, amps);
        boolean x8 = te.packets() == 8 && te.throughput() == 2048 * 8;
        te.setInventorySlotContents(TileEntityEnergyConverterSC.FIRST_MODULE + 2, ModItems.upgrade.stackOf(UpgradeType.UNIVERSAL_TRANSFORMER));
        boolean sv = te.workTier() == Tier.SV && te.throughput() == Tier.SV.getVoltage() * 8 && te.acceptsAnyVoltage()
                && te.inputTier() == Tier.max() && te.outputTier() == Tier.EV;
        ItemStack trs = ModItems.upgrade.stackOf(UpgradeType.TRANSFORMER);
        trs.stackSize = 4;
        te.setInventorySlotContents(TileEntityEnergyConverterSC.FIRST_MODULE, trs);
        te.setInventorySlotContents(TileEntityEnergyConverterSC.FIRST_MODULE + 2, null);
        boolean qv = te.euTier() == ForeignEnergySC.euTier(4) && ForeignEnergySC.euTier(9) == ForeignEnergySC.euTier(4);
        int[] caps = new int[6];
        for (int n = 0; n <= 5; n++) {
            ItemStack st = n == 0 ? null : ModItems.upgrade.stackOf(UpgradeType.ENERGY_STORAGE);
            if (st != null) {
                st.stackSize = n;
            }
            te.setInventorySlotContents(TileEntityEnergyConverterSC.FIRST_MODULE + 3, st);
            caps[n] = te.getMaxEnergyStored();
        }
        boolean buffers = caps[0] == 1000000 && caps[1] == 4000000 && caps[2] == 16000000 && caps[4] == 256000000 && caps[5] == 256000000;
        te.setInventorySlotContents(TileEntityEnergyConverterSC.FIRST_MODULE + 3, null);
        ItemStack eff = ModItems.converterModule.stackOf(ItemConverterModuleSC.Kind.EFFICIENCY);
        eff.stackSize = 2;
        te.setInventorySlotContents(TileEntityEnergyConverterSC.FIRST_MODULE + 4, eff);
        boolean loss = te.lossPercent() == 1;
        boolean slots = TileEntityEnergyConverterSC.isModule(tr) && TileEntityEnergyConverterSC.isModule(eff)
                && !TileEntityEnergyConverterSC.isModule(ModItems.upgrade.stackOf(UpgradeType.OVERCLOCKER))
                && TileEntityEnergyConverterSC.moduleLimit(amps) == 3 && TileEntityEnergyConverterSC.moduleLimit(trs) == 4;
        check(base && ev && x8 && sv && qv && buffers && loss && slots, "modules: MV 128 EU/t, 2 Transformers + Amplifier = EV x2 = 4096 (" + ev
                + "), 4 Amplifiers count 3 = x8 (" + x8 + "), Universal = SV " + te.throughput() + " any voltage, EU out at the Transformers' tier ("
                + sv + "), 4 Transformers = " + te.euTier() + ", buffers " + caps[0] + " / " + caps[1] + " / " + caps[4] + " / " + caps[5]
                + ", 2 Efficiency = " + te.lossPercent() + " %, the slots' rules (" + slots + ")");
    }

    /** Which pairs the converter offers: RF with the RF API; J / gJ only with their mod AND their card (none here without the mods). */
    private static void availability() {
        TileEntityEnergyConverterSC te = TileEntityEnergyConverterSC.create();
        te.setInventorySlotContents(TileEntityEnergyConverterSC.FIRST_MODULE, ModItems.converterModule.stackOf(ItemConverterModuleSC.Kind.CARD_MEKANISM));
        te.setInventorySlotContents(TileEntityEnergyConverterSC.FIRST_MODULE + 1, ModItems.converterModule.stackOf(ItemConverterModuleSC.Kind.CARD_GALACTICRAFT));
        te.refreshForTest();
        boolean rf = te.pairAvailable(Kind.RF) == ForeignEnergySC.rfApi();
        boolean j = te.pairAvailable(Kind.J) == ForeignEnergySC.mekanism();
        boolean gj = te.pairAvailable(Kind.GJ) == ForeignEnergySC.galacticraft();
        boolean inert = ItemConverterModuleSC.Kind.CARD_MEKANISM.inert() == !ForeignEnergySC.mekanism()
                && ItemConverterModuleSC.Kind.CARD_GALACTICRAFT.inert() == !ForeignEnergySC.galacticraft();
        boolean firstPair = ForeignEnergySC.rfApi() ? te.pairKind() == Kind.RF : (te.pairKind() == null) == !ForeignEnergySC.anyPresent();
        TileEntityEnergyConverterSC bare = TileEntityEnergyConverterSC.create();
        bare.refreshForTest();
        boolean noCard = !bare.pairAvailable(Kind.J) && !bare.pairAvailable(Kind.GJ);
        boolean tileClass = (bare.speaksRf() == ForeignEnergySC.rfApi());
        String mods = "RF API " + ForeignEnergySC.rfApi() + ", Mekanism " + ForeignEnergySC.mekanism() + ", Galacticraft " + ForeignEnergySC.galacticraft();
        if (!ForeignEnergySC.anyPresent()) {
            System.out.println("[SC-TEST] INFO converter: no other energy in this game - the converter works as an EU buffer only");
        }
        check(rf && j && gj && inert && firstPair && noCard && tileClass, "pairs offered (" + mods + "): RF " + te.pairAvailable(Kind.RF) + ", J "
                + te.pairAvailable(Kind.J) + ", gJ " + te.pairAvailable(Kind.GJ) + " - the cards inert without their mod (" + inert + "), first pair "
                + te.pairKind() + ", no card - no J / gJ (" + noCard + "), the tile speaks RF " + bare.speaksRf());
    }

    /** The faces' modes, buffers, filters and the other settings survive writeToNBT / readFromNBT. */
    private static void sidesNbt() {
        TileEntityEnergyConverterSC a = TileEntityEnergyConverterSC.create();
        a.setMode(0, TileEntityEnergyConverterSC.MODE_OFF);
        a.setMode(1, TileEntityEnergyConverterSC.MODE_OUT);
        a.setBuf(1, TileEntityEnergyConverterSC.BUF_X);
        a.setMode(4, TileEntityEnergyConverterSC.MODE_OUT);
        a.setBuf(4, TileEntityEnergyConverterSC.BUF_EU);
        a.setFilter(5, TileEntityEnergyConverterSC.F_EU);
        a.setDirection(TileEntityEnergyConverterSC.DIR_BALANCE);
        a.cyclePriority();
        a.cycleComparator();
        a.setPairForTest(Kind.RF.ordinal());
        a.setForeignForTest(12345.5);
        a.setInventorySlotContents(TileEntityEnergyConverterSC.FIRST_MODULE + 2, ModItems.converterModule.stackOf(ItemConverterModuleSC.Kind.AMPLIFIER));
        NBTTagCompound nbt = new NBTTagCompound();
        a.writeToNBT(nbt);
        TileEntityEnergyConverterSC b = TileEntityEnergyConverterSC.create();
        b.readFromNBT(nbt);
        boolean ok = true;
        for (int s = 0; s < 6; s++) {
            ok &= a.getMode(s) == b.getMode(s) && a.getBuf(s) == b.getBuf(s) && a.getFilter(s) == b.getFilter(s);
        }
        ok &= b.getMode(0) == TileEntityEnergyConverterSC.MODE_OFF && b.getBuf(1) == TileEntityEnergyConverterSC.BUF_X && b.getFilter(5) == 1
                && b.getFilter(2) == TileEntityEnergyConverterSC.F_ALL && b.getDirection() == TileEntityEnergyConverterSC.DIR_BALANCE
                && b.getOutPriority() == TileEntityEnergyConverterSC.PRIO_X && b.getComparatorOf() == TileEntityEnergyConverterSC.COMP_X
                && b.pairKind() == Kind.RF && b.getForeign() == 12345.5 && b.packets() == 2
                && TileEntityEnergyConverterSC.TILE_ID.equals(nbt.getString("id"));
        // the filter round on a face: both -> EU -> RF -> none -> both
        TileEntityEnergyConverterSC f = TileEntityEnergyConverterSC.create();
        f.setPairForTest(Kind.RF.ordinal());
        int rfBit = TileEntityEnergyConverterSC.filterBit(Kind.RF);
        StringBuilder round = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            f.cycleFilter(3);
            int v = f.getFilter(3);
            round.append(((v & 1) != 0 ? "EU" : "") + ((v & rfBit) != 0 ? "RF" : "") + "|");
        }
        boolean filterRound = round.toString().equals("EU|RF||EURF|");
        // the faces: an input (Авто) takes EU and RF, an EU-only one no RF, an output face takes nothing in
        boolean faces = a.acceptsFrom(net.minecraftforge.common.util.ForgeDirection.SOUTH) && a.acceptsForeignFrom(net.minecraftforge.common.util.ForgeDirection.SOUTH)
                && !a.acceptsFrom(net.minecraftforge.common.util.ForgeDirection.UP) && !a.acceptsFrom(net.minecraftforge.common.util.ForgeDirection.DOWN)
                && a.acceptsFrom(net.minecraftforge.common.util.ForgeDirection.EAST) && !a.acceptsForeignFrom(net.minecraftforge.common.util.ForgeDirection.EAST)
                && !a.canConnectEnergy(net.minecraftforge.common.util.ForgeDirection.DOWN) && a.canConnectEnergy(net.minecraftforge.common.util.ForgeDirection.UP);
        check(ok && filterRound && faces, "faces' settings through NBT (" + ok + "), the filter round " + round + ", what each face takes (" + faces + ")");
    }

    /** The dropped block's item keeps both buffers, the pair, the settings and the modules; placed again, all of it is back. */
    private static void itemNbt() {
        TileEntityEnergyConverterSC a = TileEntityEnergyConverterSC.create();
        ItemStack st = ModItems.upgrade.stackOf(UpgradeType.ENERGY_STORAGE);
        st.stackSize = 2;
        a.setInventorySlotContents(TileEntityEnergyConverterSC.FIRST_MODULE + 5, st);
        a.addEnergyForTest(9000000);                                    // more than the bare 1 000 000: the modules make the room
        a.setPairForTest(Kind.RF.ordinal());
        a.setForeignForTest(777);
        a.setMode(2, TileEntityEnergyConverterSC.MODE_OUT);
        NBTTagCompound item = a.writeToItem();
        TileEntityEnergyConverterSC b = TileEntityEnergyConverterSC.create();
        b.readFromItem(item);
        boolean ok = b.getEnergyStored() == 9000000 && b.getForeign() == 777 && b.pairKind() == Kind.RF && b.getMode(2) == TileEntityEnergyConverterSC.MODE_OUT
                && b.storageModules() == 2 && b.getMaxEnergyStored() == 16000000 && b.getStackInSlot(TileEntityEnergyConverterSC.SLOT_CHARGE) == null;
        // a storage module holding energy can't come out; the active pair's card with energy in it can't either
        boolean blocked = b.moduleRemoveBlock(TileEntityEnergyConverterSC.FIRST_MODULE + 5) == 1;
        check(ok && blocked, "the item keeps " + b.getEnergyStored() + " EU, " + b.getForeign() + " RF, the faces and 2 storage modules ("
                + b.getMaxEnergyStored() + " EU), the module holding energy stays in (" + blocked + ")");
    }

    /** A conversion tick without a world: EU -> RF at the rate minus the loss, X -> EU back, within the throughput. */
    private static void conversionTick() {
        if (!ForeignEnergySC.rfApi()) {
            check(true, "conversion tick: no RF API here - skipped (EU only)");
            return;
        }
        TileEntityEnergyConverterSC te = TileEntityEnergyConverterSC.create();
        te.refreshForTest();
        te.setPairForTest(Kind.RF.ordinal());
        te.setPowerOn(true);
        te.addEnergyForTest(100000);
        te.setDirection(TileEntityEnergyConverterSC.DIR_EU_TO_X);
        te.convert();
        boolean toX = te.getEnergyStored() == 100000 - 128 && near(te.getForeign(), 128 * 4 * 0.95);
        te.setDirection(TileEntityEnergyConverterSC.DIR_X_TO_EU);
        te.setForeignForTest(4000);
        int eu0 = te.getEnergyStored();
        te.convert();
        int made = te.getEnergyStored() - eu0;
        boolean toEu = made == 950 && te.getForeign() < 1e-6;               // 4000 RF -> 950 EU, under 128 * 0.95 = 121? no: capped
        // the throughput caps X -> EU at 128 EU worth a tick: 4000 RF would make 950, but only floor(128 * 0.95) = 121 come out
        boolean capped = made == 121 && near(te.getForeign(), 4000 - 121 * 4 / 0.95);
        check(toX && capped && !toEu, "a tick at MV: 128 EU -> " + te.totalXMade + " RF (" + toX + "), RF -> EU capped to the throughput: "
                + made + " EU for " + te.totalXUsed + " RF (" + capped + ")");
    }

    /** The recipes exist exactly when some other energy does (here: the RF API of Industrial Upgrade, when it's installed). */
    private static void recipes() {
        int found = 0;
        for (Object o : CraftingManager.getInstance().getRecipeList()) {
            ItemStack out = ((IRecipe) o).getRecipeOutput();
            if (out != null && (out.getItem() == ModItems.converterModule
                    || out.getItem() == net.minecraft.item.Item.getItemFromBlock(ModBlocks.energyConverter))) {
                found++;
            }
        }
        boolean ok = ForeignEnergySC.anyPresent() ? found == 5 : found == 0;
        check(ok, "recipes: " + found + " (5 with another energy in the game, 0 without; other energy here: " + ForeignEnergySC.anyPresent() + ")");
    }

    /** Every sc.conv.* text in both languages, the English free of Cyrillic. */
    private static void texts() {
        java.util.Map<String, String> en = SelfTestSC.langMap("en_US"), ru = SelfTestSC.langMap("ru_RU");
        StringBuilder bad = new StringBuilder();
        int n = 0;
        for (java.util.Map<String, String> m : new java.util.Map[]{en, ru}) {
            java.util.Map<String, String> other = m == en ? ru : en;
            for (String k : m.keySet()) {
                if (k.startsWith("sc.conv.") || k.startsWith("sc.waila.conv.") || k.contains("converterModule") || k.contains("energyConverter")) {
                    n += m == en ? 1 : 0;
                    if (!other.containsKey(k)) {
                        bad.append(' ').append(m == en ? "ru lacks " : "en lacks ").append(k);
                    }
                    if (m == en && m.get(k).matches(".*[\\u0400-\\u04FF].*")) {
                        bad.append(" cyrillic:").append(k);
                    }
                }
            }
        }
        String[] must = {"tile.siliconage.energyConverter.name", "item.siliconage.converterModule.card_galacticraft.name", "sc.conv.tooltip.noforeign",
                "sc.conv.msg.noroom", "sc.conv.hint.dir.2", "sc.conv.tip.modules.7"};
        for (String k : must) {
            if (!en.containsKey(k) || !ru.containsKey(k)) {
                bad.append(" missing:").append(k);
            }
        }
        check(n > 60 && bad.length() == 0, "texts: " + n + " keys in both languages" + bad);
    }
}
