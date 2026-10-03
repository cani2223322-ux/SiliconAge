package com.sc.debug;

import com.sc.init.ModBlocks;
import com.sc.init.ModItems;
import com.sc.item.ItemArmorSC;
import com.sc.tileentity.SingularProcessSC;
import com.sc.tileentity.TileEntitySingularStationSC;
import com.sc.util.ArmorGasSC;
import com.sc.util.ArmorGasSC.Gas;
import com.sc.util.ArmorSuit;
import com.sc.util.SingularLevel;
import com.sc.util.SingularScheme;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

/**
 * A live check of the Singular Station's Б-1 conversion (-Dsc.worldtest=true): station A gets a
 * charged Exo helmet with helium and the helmet's materials, starts «Преобразовать» (the materials
 * leave the slots, the slot locks), is fast-forwarded to 97% (the rest is drawn as usual) and ends
 * with a Singular helmet of level 1, scheme A, the same charge and helium, the tanks paying the rest
 * of the row. Station B starts the same and is cancelled: the materials come back whole, the helmet
 * stays Exo. Station C gets a whole Exo set and its materials in the six material slots (the Singular
 * core in the catalyst slot) and converts all four pieces in one process. The blocks are removed afterwards.
 */
public class WorldTestSingConvertSC {

    private static final int AX = 270, BX = 280, CX = 290, Y = 200, Z = 90, SM = 300, HE = 4000, CHARGE = 5000;
    private int ticks;
    private int heInHelm = -1;
    private long smLeftToDraw = -1, heLeftToDraw = -1;

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        ticks++;
        World w = DimensionManager.getWorld(0);
        if (w == null) {
            return;
        }
        if (ticks == 50) {
            build(w, AX);
            build(w, BX);
            buildSet(w, CX);
        }
        if (ticks == 60) {
            TileEntitySingularStationSC a = st(w, AX), b = st(w, BX);
            heInHelm = a == null ? -1 : ArmorGasSC.amount(a.getStackInSlot(0), Gas.HELIUM);
            String sa = a == null ? "no tile" : a.startConvertFor("");
            String sb = b == null ? "no tile" : b.startConvertFor("");
            SingularProcessSC p = a == null ? null : a.getProcess();
            boolean empty = a != null && a.getStackInSlot(TileEntitySingularStationSC.MATERIAL_SLOT) == null
                    && a.getStackInSlot(TileEntitySingularStationSC.MATERIAL_SLOT + 1) == null
                    && a.getStackInSlot(TileEntitySingularStationSC.MATERIAL_SLOT + 2) == null;
            boolean ok = sa == null && sb == null && p != null && p.kind == SingularProcessSC.KIND_CONVERT && a.isLocked(0) && empty
                    && p.cost[0] == 50000000L && p.cost[1] == 100 && p.cost[2] == 2000 && p.items.size() == 3;
            System.out.println("[SC-WORLDTEST] " + (ok ? "PASS" : "FAIL") + " singular conversion starts (" + sa + "/" + sb
                    + "): the materials taken, the helmet slot locked, cost " + (p == null ? "-" : p.cost[0] + " EU / SM " + p.cost[1] + " / He " + p.cost[2]));
            TileEntitySingularStationSC c = st(w, CX);
            String sc = c == null ? "no tile" : c.startConvertFor("");
            SingularProcessSC pc = c == null ? null : c.getProcess();
            boolean cEmpty = c != null && c.getStackInSlot(TileEntitySingularStationSC.CATALYST_SLOT) == null;
            for (int i = 0; c != null && i < TileEntitySingularStationSC.MATERIAL_SLOTS; i++) {
                ItemStack m = c.getStackInSlot(TileEntitySingularStationSC.MATERIAL_SLOT + i);
                cEmpty &= i == 0 ? m != null && m.stackSize == 5 : i == 2 ? m != null && m.stackSize == 4 : m == null;
            }
            boolean cOk = sc == null && pc != null && pc.mask == 15 && pc.items.size() == 7 && pc.cost[0] == 350000000L && cEmpty;
            System.out.println("[SC-WORLDTEST] " + (cOk ? "PASS" : "FAIL") + " singular conversion of a whole Exo set starts in one process (" + sc
                    + "): 6 material slots + the core, mask " + (pc == null ? "-" : pc.mask) + ", items " + (pc == null ? "-" : pc.items.size()));
            if (pc != null) {
                c.fastForwardForTest(0.97);
            }
            if (p != null) {
                a.fastForwardForTest(0.97);
                smLeftToDraw = p.cost[1] - p.drawn[1];
                heLeftToDraw = p.cost[2] - p.drawn[2];
            }
        }
        if (ticks == 70) {
            TileEntitySingularStationSC b = st(w, BX);
            if (b != null) {
                b.cancelProcess();
            }
            int caps = count(b, com.sc.util.SingularStationMath.M_CAPSULE), lens = count(b, com.sc.util.SingularStationMath.M_LENS),
                    plates = count(b, com.sc.util.SingularStationMath.M_NB3SN);
            boolean ok = b != null && b.getProcess() == null && caps == 2 && lens == 1 && plates == 2 && !b.isLocked(0)
                    && TileEntitySingularStationSC.isExo(b.getStackInSlot(0));
            System.out.println("[SC-WORLDTEST] " + (ok ? "PASS" : "FAIL") + " singular conversion cancelled: materials back whole (capsules " + caps
                    + ", lens " + lens + ", plates " + plates + "), the helmet still Exo");
            w.setBlockToAir(BX, Y, Z);
        }
        if (ticks == 300) {
            TileEntitySingularStationSC a = st(w, AX);
            ItemStack h = a == null ? null : a.getStackInSlot(0);
            boolean ok = a != null && a.getProcess() == null && a.completedCount() == 1 && SingularLevel.isSingular(h)
                    && ((ItemArmorSC) h.getItem()).armorType == 0 && SingularLevel.levelOf(h) == 1 && SingularScheme.of(h) == SingularScheme.A
                    && ItemArmorSC.chargeOf(h) == CHARGE && ArmorGasSC.amount(h, Gas.HELIUM) == heInHelm && !a.isLocked(0)
                    && a.tankAmount(Gas.SINGULAR_MATTER) == SM - smLeftToDraw && a.tankAmount(Gas.HELIUM) == HE - heLeftToDraw;
            System.out.println("[SC-WORLDTEST] " + (ok ? "PASS" : "FAIL") + " singular conversion done: " + (h == null ? "-" : h.getDisplayName())
                    + ", level " + SingularLevel.levelOf(h) + ", charge " + (h == null ? -1 : ItemArmorSC.chargeOf(h)) + ", helium "
                    + (h == null ? -1 : ArmorGasSC.amount(h, Gas.HELIUM)) + "/" + heInHelm + ", SM / He left "
                    + (a == null ? "-" : a.tankAmount(Gas.SINGULAR_MATTER) + " / " + a.tankAmount(Gas.HELIUM)) + ", finished " + (a == null ? 0 : a.completedCount()));
            w.setBlockToAir(AX, Y, Z);
            TileEntitySingularStationSC c = st(w, CX);
            boolean cOk = c != null && c.getProcess() == null && c.completedCount() == 1;
            StringBuilder lv = new StringBuilder();
            for (int t = 0; t < 4; t++) {
                ItemStack s = c == null ? null : c.getStackInSlot(t);
                cOk &= SingularLevel.isSingular(s) && ((ItemArmorSC) s.getItem()).armorType == t && SingularLevel.levelOf(s) == 1;
                lv.append(SingularLevel.isSingular(s) ? "S" + SingularLevel.levelOf(s) : s == null ? "-" : "X");
            }
            System.out.println("[SC-WORLDTEST] " + (cOk ? "PASS" : "FAIL") + " singular conversion of a whole Exo set done in one process: " + lv
                    + ", finished " + (c == null ? 0 : c.completedCount()));
            w.setBlockToAir(CX, Y, Z);
        }
    }

    private static int count(TileEntitySingularStationSC s, int kind) {
        int n = 0;
        for (int i = 0; s != null && i < TileEntitySingularStationSC.MATERIAL_SLOTS; i++) {
            ItemStack m = s.getStackInSlot(TileEntitySingularStationSC.MATERIAL_SLOT + i);
            n += TileEntitySingularStationSC.materialKind(m) == kind ? m.stackSize : 0;
        }
        return n;
    }

    private static TileEntitySingularStationSC st(World w, int x) {
        net.minecraft.tileentity.TileEntity te = w.getTileEntity(x, Y, Z);
        return te instanceof TileEntitySingularStationSC ? (TileEntitySingularStationSC) te : null;
    }

    /** Station C: a whole Exo set, a Singular core in the catalyst slot, the six kinds in the six material slots (with spare capsules and plates). */
    private static void buildSet(World w, int x) {
        w.setBlock(x, Y, Z, ModBlocks.singularStation);
        TileEntitySingularStationSC s = st(w, x);
        if (s == null) {
            return;
        }
        s.setPowerOn(true);
        if (s.isChargeOn()) {
            s.toggleCharge();
        }
        if (s.isFillGases()) {
            s.toggleFillGases();
        }
        s.setEnergyStoredClient(13000000);
        s.fillTank(Gas.SINGULAR_MATTER, 1000, true);
        s.fillTank(Gas.HELIUM, HE, true);
        s.fillTank(Gas.DEUTERIUM, 1000, true);
        for (int t = 0; t < 4; t++) {
            s.setInventorySlotContents(t, new ItemStack(ModItems.ARMOR.get(ArmorSuit.EXO)[t]));
        }
        int m = TileEntitySingularStationSC.MATERIAL_SLOT;
        ItemStack hf = ModItems.ingot.stackOf(com.sc.util.Material.HAFNIUM);
        hf.stackSize = 2;
        s.setInventorySlotContents(TileEntitySingularStationSC.CATALYST_SLOT, new ItemStack(ModItems.battery, 1, 6));
        s.setInventorySlotContents(m, new ItemStack(ModItems.component("matterCapsule"), 16));
        s.setInventorySlotContents(m + 1, new ItemStack(ModItems.component("focusLens")));
        s.setInventorySlotContents(m + 2, new ItemStack(ModItems.component("nb3SnPlate"), 12));
        s.setInventorySlotContents(m + 3, new ItemStack(ModItems.component("fusionCore")));
        s.setInventorySlotContents(m + 4, new ItemStack(ModItems.battery, 1, 5));
        s.setInventorySlotContents(m + 5, hf);
    }

    /** A switched-on station (charging and filling off) with a full buffer, SM and helium, a charged Exo helmet and its materials. */
    private static void build(World w, int x) {
        w.setBlock(x, Y, Z, ModBlocks.singularStation);
        TileEntitySingularStationSC s = st(w, x);
        if (s == null) {
            return;
        }
        s.setPowerOn(true);
        if (s.isChargeOn()) {
            s.toggleCharge();
        }
        if (s.isFillGases()) {
            s.toggleFillGases();
        }
        s.setEnergyStoredClient(13000000);
        s.fillTank(Gas.SINGULAR_MATTER, SM, true);
        s.fillTank(Gas.HELIUM, HE, true);
        ItemStack helm = new ItemStack(ModItems.ARMOR.get(ArmorSuit.EXO)[0]);
        ItemArmorSC.setCharge(helm, CHARGE);
        ArmorGasSC.setAmount(helm, Gas.HELIUM, 250);
        s.setInventorySlotContents(0, helm);
        s.setInventorySlotContents(TileEntitySingularStationSC.MATERIAL_SLOT, new ItemStack(ModItems.component("matterCapsule"), 2));
        s.setInventorySlotContents(TileEntitySingularStationSC.MATERIAL_SLOT + 1, new ItemStack(ModItems.component("focusLens")));
        s.setInventorySlotContents(TileEntitySingularStationSC.MATERIAL_SLOT + 2, new ItemStack(ModItems.component("nb3SnPlate"), 2));
    }
}
