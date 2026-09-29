package com.sc.debug;

import com.sc.energy.GeneratorStatus;
import com.sc.energy.GeneratorType;
import com.sc.energy.Tier;
import com.sc.init.ModBlocks;
import com.sc.init.ModFluids;
import com.sc.init.ModItems;
import com.sc.tileentity.TileEntityEnergyStorageSC;
import com.sc.tileentity.TileEntityGeneratorSC;
import com.sc.tileentity.TileEntityTankSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

/**
 * A live check of the big tokamak (-Dsc.worldtest=true): two full 7x7x3 builds with port tanks
 * and an IV storage. A: lit in the big mode, x4 output into the storage, helium and hydrogen
 * drawn; then a coil taken and stability at 5% - argon puts it out softly. B: no argon, stability
 * at zero - a breakdown: coils thrown, a radiation burst. The builds are removed afterwards.
 */
public class WorldTestTokamakSC {

    private int ticks;
    private static final int AX = 70, BX = 90, Y = 200, Z = 70;

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
        if (ticks == 60) {
            build(w, AX, true);
            build(w, BX, false);
        }
        if (ticks == 100) {
            gen(w, AX).setIgnitionForTest();
            gen(w, BX).setIgnitionForTest();
        }
        if (ticks == 220) {
            TileEntityGeneratorSC a = gen(w, AX), b = gen(w, BX);
            TileEntityEnergyStorageSC s = (TileEntityEnergyStorageSC) w.getTileEntity(AX, Y, Z + 3);
            int he = tank(w, AX - 3, Z).getTank().getFluidAmount(), h2 = tank(w, AX + 3, Z).getTank().getFluidAmount();
            boolean ok = a.isBigRunning() && a.isBigReady() && a.ratedOutput() == 65536 && a.getLastOutput() > 0 && s.getEnergyStored() > 0
                    && he < 8000 && h2 < 8000 && a.outputTier() == Tier.XV && b.isBigRunning();
            System.out.println("[SC-WORLDTEST] " + (ok ? "PASS" : "FAIL") + " big tokamak: running " + a.isBigRunning() + ", rated "
                    + a.ratedOutput() + ", made " + a.getLastOutput() + " EU/t, storage " + s.getEnergyStored() + ", He left " + he
                    + ", H2 left " + h2 + ", stability " + a.getStability() + ", status " + a.getStatus());
            w.setBlockToAir(AX + 2, Y, Z + 2);                       // a coil taken while it runs
            a.setStabilityForTest(5F);
            w.setBlockToAir(BX + 2, Y, Z + 2);
            b.setStabilityForTest(0.5F);
        }
        if (ticks == 270) {
            TileEntityGeneratorSC a = gen(w, AX), b = gen(w, BX);
            int ar = tank(w, AX - 3, Z - 1).getTank().getFluidAmount();
            int coilsB = 0;
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    coilsB += w.getBlock(BX + dx, Y, Z + dz) == ModBlocks.tokamakCoil ? 1 : 0;
                }
            }
            boolean soft = !a.isIgnited() && a.getBigEvent() == TileEntityGeneratorSC.EVENT_SOFT && ar == 0;
            boolean broke = !b.isIgnited() && b.getBigEvent() == TileEntityGeneratorSC.EVENT_BROKE && coilsB <= 20 && b.getBurstTicks() > 0
                    && b.radiationLevel() > 20F && b.radiationRadiusNow() == TileEntityGeneratorSC.BURST_RADIUS;
            System.out.println("[SC-WORLDTEST] " + (soft ? "PASS" : "FAIL") + " big tokamak soft stop: lit " + a.isIgnited() + ", status "
                    + a.getStatus() + ", event " + a.getBigEvent() + ", argon left " + ar);
            System.out.println("[SC-WORLDTEST] " + (broke ? "PASS" : "FAIL") + " big tokamak breakdown: lit " + b.isIgnited() + ", status "
                    + b.getStatus() + ", event " + b.getBigEvent() + ", coils left " + coilsB + ", burst " + b.getBurstTicks() + ", radiation " + b.radiationLevel()
                    + " to " + b.radiationRadiusNow());
            clear(w, AX);
            clear(w, BX);
        }
    }

    private static TileEntityGeneratorSC gen(World w, int x) {
        return (TileEntityGeneratorSC) w.getTileEntity(x, Y, Z);
    }

    private static TileEntityTankSC tank(World w, int x, int z) {
        return (TileEntityTankSC) w.getTileEntity(x, Y, z);
    }

    private static void fill(World w, int x, int z, net.minecraftforge.fluids.Fluid f, int mb) {
        w.setBlock(x, Y, z, ModBlocks.tankSC, 0, 3);
        tank(w, x, z).fill(ForgeDirection.UNKNOWN, new FluidStack(f, mb), true);
    }

    /** The 7x7x3: lead cap and floor, 24 coils, lead walls with port tanks and an IV storage. */
    private static void build(World w, int x0, boolean argon) {
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
        ItemStack st = ModBlocks.generatorStack(GeneratorType.TOKAMAK, 1);
        w.setBlock(x0, Y, Z, net.minecraft.block.Block.getBlockFromItem(st.getItem()), st.getItemDamage(), 3);
        TileEntityGeneratorSC g = gen(w, x0);
        g.setGeneratorType(GeneratorType.TOKAMAK);
        g.setInventorySlotContents(TileEntityGeneratorSC.SLOT_BLANKET, new ItemStack(ModItems.component("liBlanketModule")));
        g.setInventorySlotContents(TileEntityGeneratorSC.SLOT_FUEL, new ItemStack(ModItems.deuteriumCell, 8));
        fill(w, x0 - 3, Z, ModFluids.liquidHelium, 8000);
        fill(w, x0 + 3, Z, ModFluids.hydrogen, 8000);
        fill(w, x0, Z - 3, ModFluids.deuterium, 8000);
        if (argon) {
            fill(w, x0 - 3, Z - 1, ModFluids.argon, 1000);
        }
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
