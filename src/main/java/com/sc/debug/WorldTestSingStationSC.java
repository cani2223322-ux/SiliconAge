package com.sc.debug;

import com.sc.init.ModBlocks;
import com.sc.init.ModItems;
import com.sc.tileentity.SingularProcessSC;
import com.sc.tileentity.TileEntitySingularStationSC;
import com.sc.util.ArmorGasSC.Gas;
import com.sc.util.ArmorSuit;
import com.sc.util.SingularLevel;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

/**
 * A live check of the Singular Service Station (-Dsc.worldtest=true): station A with four
 * gravitational stabilisers round it and a ready Singular helmet (level 1, points full) is
 * modernised to level 2 - speed x2 from the stabilisers, the slot locked, a pause while the buffer
 * is empty (the progress holds, nothing lost), then to the end: the helmet is level 2 with 0 points,
 * the tanks paid exactly the quarter row. Station B starts the same, and is broken (getDrops):
 * the process is cancelled first and half the drawn EU is in the dropped item. Charging switched off
 * leaves the helmet's charge alone. The blocks are removed afterwards.
 */
public class WorldTestSingStationSC {

    private static final int AX = 240, BX = 250, Y = 200, Z = 90;
    private int ticks;
    private double progressAtPause;

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
            int[][] stab = {{2, 0}, {-2, 0}, {0, 2}, {0, -3}};
            for (int[] s : stab) {
                w.setBlock(AX + s[0], Y, Z + s[1], ModBlocks.gravStabiliser);
            }
            build(w, AX);
            build(w, BX);
        }
        if (ticks == 60) {
            TileEntitySingularStationSC a = st(w, AX), b = st(w, BX);
            String sa = a == null ? "no tile" : a.startModerniseFor(new int[]{1, 0, 0, 0}, "");
            String sb = b == null ? "no tile" : b.startModerniseFor(new int[]{1, 0, 0, 0}, "");
            boolean ok = sa == null && sb == null && a.getProcess() != null && a.isLocked(0) && !a.isLocked(1)
                    && a.getProcess().cost[0] == 12500000L && a.getProcess().cost[1] == 25 && a.getProcess().cost[2] == 500;
            System.out.println("[SC-WORLDTEST] " + (ok ? "PASS" : "FAIL") + " singular station: modernisation 1 -> 2 starts (" + sa + "/" + sb
                    + "), the helmet slot locked, a quarter row: " + (a == null || a.getProcess() == null ? "-" : a.getProcess().cost[0] + " EU"));
        }
        if (ticks == 100) {
            TileEntitySingularStationSC a = st(w, AX);
            SingularProcessSC p = a == null ? null : a.getProcess();
            boolean ok = p != null && a.getStabilisers() == 4 && Math.abs(a.speed() - 2.0) < 1e-9 && p.progress > 0.05 && p.progress < 0.1
                    && a.isWorking() && a.getEnergyStored() < 13000000;
            System.out.println("[SC-WORLDTEST] " + (ok ? "PASS" : "FAIL") + " singular station runs with 4 stabilisers: speed x" + (a == null ? 0 : a.speed())
                    + ", progress " + (p == null ? -1 : p.progress) + ", EU left " + (a == null ? 0 : a.getEnergyStored()));
            if (a != null && p != null) {
                progressAtPause = p.progress;
                a.setEnergyStoredClient(0);                                // ПР4: no energy - it must wait
            }
        }
        if (ticks == 120) {
            TileEntitySingularStationSC a = st(w, AX);
            SingularProcessSC p = a == null ? null : a.getProcess();
            boolean ok = p != null && Math.abs(p.progress - progressAtPause) < 0.002 && (a.getShortMask() & 1) != 0;
            System.out.println("[SC-WORLDTEST] " + (ok ? "PASS" : "FAIL") + " singular station pauses without energy: progress " + progressAtPause + " -> "
                    + (p == null ? -1 : p.progress) + ", short mask " + (a == null ? -1 : a.getShortMask()));
            if (a != null) {
                a.setEnergyStoredClient(13000000);
            }
        }
        if (ticks == 121) {
            TileEntitySingularStationSC b = st(w, BX);
            long drawn = b == null || b.getProcess() == null ? -1 : b.getProcess().drawn[0];
            int before = b == null ? 0 : b.getEnergyStored();
            java.util.ArrayList<ItemStack> drops = ModBlocks.singularStation.getDrops(w, BX, Y, Z, 0, 0);
            NBTTagCompound tag = drops.isEmpty() || drops.get(0) == null ? null : drops.get(0).getTagCompound();
            int inItem = tag == null ? -1 : tag.getInteger("EnergySC");
            boolean ok = b != null && b.getProcess() == null && drawn > 0 && inItem == before + (int) (drawn / 2) && !b.isLocked(0);
            System.out.println("[SC-WORLDTEST] " + (ok ? "PASS" : "FAIL") + " singular station broken mid-process: cancelled first, half of "
                    + drawn + " EU back - the item holds " + inItem + " (buffer was " + before + ")");
            w.setBlockToAir(BX, Y, Z);
        }
        if (ticks == 740) {
            TileEntitySingularStationSC a = st(w, AX);
            ItemStack helm = a == null ? null : a.getStackInSlot(0);
            boolean ok = a != null && a.getProcess() == null && a.completedCount() == 1 && SingularLevel.levelOf(helm) == 2
                    && SingularLevel.points(helm) == 0 && a.tankAmount(Gas.SINGULAR_MATTER) == 75 && a.tankAmount(Gas.HELIUM) == 500
                    && a.tankAmount(Gas.DEUTERIUM) == 375 && !a.isLocked(0) && com.sc.item.ItemArmorSC.chargeOf(helm) == 0;
            System.out.println("[SC-WORLDTEST] " + (ok ? "PASS" : "FAIL") + " singular station modernisation done: helmet level "
                    + SingularLevel.levelOf(helm) + ", points " + SingularLevel.points(helm) + ", SM / He / D left "
                    + (a == null ? "-" : a.tankAmount(Gas.SINGULAR_MATTER) + " / " + a.tankAmount(Gas.HELIUM) + " / " + a.tankAmount(Gas.DEUTERIUM))
                    + ", charge (charging off) " + (helm == null ? -1 : com.sc.item.ItemArmorSC.chargeOf(helm)) + ", finished " + (a == null ? 0 : a.completedCount()));
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    w.setBlockToAir(AX + dx, Y, Z + dz);
                }
            }
        }
    }

    private static TileEntitySingularStationSC st(World w, int x) {
        net.minecraft.tileentity.TileEntity te = w.getTileEntity(x, Y, Z);
        return te instanceof TileEntitySingularStationSC ? (TileEntitySingularStationSC) te : null;
    }

    /** A switched-on station with a full buffer, the gases and a ready Singular helmet; charging off. */
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
            s.toggleFillGases();                                       // the tanks pay the modernisation only
        }
        s.setEnergyStoredClient(13000000);
        s.fillTank(Gas.SINGULAR_MATTER, 100, true);
        s.fillTank(Gas.HELIUM, 1000, true);
        s.fillTank(Gas.DEUTERIUM, 500, true);
        ItemStack helm = new ItemStack(ModItems.ARMOR.get(ArmorSuit.SINGULAR)[0]);
        SingularLevel.setLevel(helm, 1);
        SingularLevel.setPoints(helm, SingularLevel.threshold(1));
        s.setInventorySlotContents(0, helm);
    }
}
