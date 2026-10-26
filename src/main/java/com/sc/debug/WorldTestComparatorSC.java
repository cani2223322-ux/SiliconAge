package com.sc.debug;

import com.sc.energy.GeneratorType;
import com.sc.energy.Tier;
import com.sc.init.IdeaBlocksSC;
import com.sc.init.ModBlocks;
import com.sc.init.ModFluids;
import com.sc.init.ModItems;
import com.sc.tileentity.TileEntityDataPortSC;
import com.sc.tileentity.TileEntityEnergyStorageSC;
import com.sc.tileentity.TileEntityGeneratorSC;
import com.sc.tileentity.TileEntityTankSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

/**
 * ОМ-5 (docs/plan-ideas-2026-10-26.md, -Dsc.worldtest=true; the Overworld, X = 4000 + 100 x 13, Y 200, Z 0): two Tokamak XV builds
 * (WorldTestTokamakSC's) with a Data Port in place of a lead wall block. A (STOP): the structure stays whole with the port, the port is
 * bound, the reactor lights, the comparator at the port reads its stability (> 0); a redstone block put by the port - a soft stop by
 * the port (EVENT_PORT). B (PERMIT, unpowered): it doesn't light, the screen's «no permit» flag set. Removed afterwards.
 */
public class WorldTestComparatorSC {

    private static final int AX = 5300, BX = 5320, Y = 200, Z = 0;
    private int ticks;

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        ticks++;
        World w = DimensionManager.getWorld(0);
        if (w == null || IdeaBlocksSC.dataPort == null) {
            return;
        }
        if (ticks == 60) {
            build(w, AX);
            build(w, BX);
            ((TileEntityDataPortSC) w.getTileEntity(BX + 3, Y, Z + 2)).toggleMode();       // B: PERMIT, no signal
        }
        if (ticks == 70) {
            for (int x : new int[]{AX, BX}) {
                ((TileEntityEnergyStorageSC) w.getTileEntity(x, Y, Z + 3)).setEnergyStoredClient(5000000);
                gen(w, x).setIgnitionForTest();
            }
        }
        if (ticks == 220) {
            TileEntityGeneratorSC a = gen(w, AX), b = gen(w, BX);
            TileEntityDataPortSC pa = port(w, AX), pb = port(w, BX);
            boolean lit = a.isBigReady() && a.isIgnited() && pa.boundAt() != null && a.hasPort(AX + 3, Y, Z + 2)
                    && pa.comparatorLevel() > 0 && pa.comparatorLevel() == a.comparatorLevel();
            boolean refused = b.isBigReady() && !b.isIgnited() && b.isPortBlocked() && pb.boundAt() != null && pb.comparatorLevel() == 0;
            System.out.println("[SC-WORLDTEST] " + (lit ? "PASS" : "FAIL") + " ОМ-5 Data Port in a Tokamak XV's wall: ready " + a.isBigReady()
                    + ", lit " + a.isIgnited() + ", bound " + (pa.boundAt() != null) + ", the port's comparator " + pa.comparatorLevel()
                    + " (the reactor's " + a.comparatorLevel() + ")");
            System.out.println("[SC-WORLDTEST] " + (refused ? "PASS" : "FAIL") + " ОМ-5 PERMIT unpowered: ready " + b.isBigReady() + ", lit "
                    + b.isIgnited() + ", no permit " + b.isPortBlocked() + ", status " + b.getStatus());
            w.setBlock(AX + 4, Y, Z + 2, Blocks.redstone_block);                       // the pulse
        }
        if (ticks == 260) {
            TileEntityGeneratorSC a = gen(w, AX);
            boolean stopped = !a.isIgnited() && a.getBigEvent() == TileEntityGeneratorSC.EVENT_PORT;
            System.out.println("[SC-WORLDTEST] " + (stopped ? "PASS" : "FAIL") + " ОМ-5 a pulse into the Data Port: lit " + a.isIgnited()
                    + ", event " + a.getBigEvent() + ", status " + a.getStatus());
            w.setBlockToAir(AX + 4, Y, Z + 2);
            clear(w, AX);
            clear(w, BX);
        }
    }

    private static TileEntityGeneratorSC gen(World w, int x) {
        return (TileEntityGeneratorSC) w.getTileEntity(x, Y, Z);
    }

    private static TileEntityDataPortSC port(World w, int x) {
        return (TileEntityDataPortSC) w.getTileEntity(x + 3, Y, Z + 2);
    }

    private static void fill(World w, int x, int z, net.minecraftforge.fluids.Fluid f, int mb) {
        w.setBlock(x, Y, z, ModBlocks.tankSC, 0, 3);
        ((TileEntityTankSC) w.getTileEntity(x, Y, z)).fill(ForgeDirection.UNKNOWN, new FluidStack(f, mb), true);
    }

    /** WorldTestTokamakSC's 7x7x3, a Data Port at (x0 + 3, Z + 2) in the wall. */
    private static void build(World w, int x0) {
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                w.setBlock(x0 + dx, Y - 1, Z + dz, ModBlocks.leadBlock);
                w.setBlock(x0 + dx, Y + 1, Z + dz, ModBlocks.leadBlock);
                w.setBlockToAir(x0 + dx, Y, Z + dz);
                if (Math.abs(dx) == 3 || Math.abs(dz) == 3) {
                    w.setBlock(x0 + dx, Y, Z + dz, ModBlocks.leadBlock);
                } else if (dx != 0 || dz != 0) {
                    w.setBlock(x0 + dx, Y, Z + dz, ModBlocks.tokamakCoil);
                }
            }
        }
        w.setBlock(x0 + 3, Y, Z + 2, IdeaBlocksSC.dataPort, 0, 3);
        ItemStack st = ModBlocks.generatorStack(GeneratorType.TOKAMAK_XV, 1);
        w.setBlock(x0, Y, Z, net.minecraft.block.Block.getBlockFromItem(st.getItem()), st.getItemDamage(), 3);
        TileEntityGeneratorSC g = gen(w, x0);
        g.setGeneratorType(GeneratorType.TOKAMAK_XV);
        g.setInventorySlotContents(TileEntityGeneratorSC.SLOT_BLANKET, new ItemStack(ModItems.component("liBlanketModule")));
        g.setInventorySlotContents(TileEntityGeneratorSC.SLOT_FUEL, new ItemStack(ModItems.deuteriumCell, 8));
        fill(w, x0 - 3, Z, ModFluids.liquidHelium, 8000);
        fill(w, x0 + 3, Z, ModFluids.hydrogen, 8000);
        fill(w, x0, Z - 3, ModFluids.deuterium, 8000);
        fill(w, x0 - 3, Z - 1, ModFluids.argon, 1000);
        w.setBlock(x0, Y, Z + 3, ModBlocks.energyStorageSC, Tier.IV.ordinal(), 3);
    }

    private static void clear(World w, int x0) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dx = -3; dx <= 3; dx++) {
                    w.setBlockToAir(x0 + dx, Y + dy, Z + dz);
                }
            }
        }
    }
}
