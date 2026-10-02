package com.sc.debug;

import com.sc.energy.GeneratorType;
import com.sc.energy.Tier;
import com.sc.init.ModBlocks;
import com.sc.init.ModFluids;
import com.sc.init.ModItems;
import com.sc.tileentity.SingularReactorSC;
import com.sc.tileentity.TileEntityEnergyStorageSC;
import com.sc.tileentity.TileEntityGeneratorSC;
import com.sc.tileentity.TileEntityTankSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

/**
 * A live check of the Singular Reactor (-Dsc.worldtest=true): four full 7x7x5 builds with port
 * tanks and an SV storage. A: lit from its storage (the charge drawn, then hurried; the compression
 * shortened), runs at 131 072 EU/t into the storage on helium and deuterium, then "Stop" eats the
 * hole and a fifth of the charge comes back. B: no helium, containment at 3% and no argon - the
 * pull (an item drifts in), the flash through the walls, 2-4 coils thrown out. C: a hole of 5.1%
 * without capsules evaporates - a flash 32 wide, the coils whole. D: containment 5% with argon -
 * put out softly. The builds are removed afterwards.
 */
public class WorldTestSingularSC {

    private int ticks;
    private static final int AX = 150, BX = 170, CX = 190, DX = 210, Y = 200, Z = 70;
    private int chargeStart;
    private long storeBeforeStop;
    private double itemStartX;
    private EntityItem probe;

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
            build(w, AX, true, true);
            build(w, BX, false, false);
            build(w, CX, true, false);
            build(w, DX, true, true);
            store(w, AX).setEnergyStoredClient(1000000000);
        }
        if (ticks == 80) {
            SingularReactorSC a = sing(w, AX), b = sing(w, BX);
            boolean capsule = ModItems.component("matterCapsule") != null;
            boolean ok = a.isReady() && a.getCoilCount() == 16 && a.getWallCount() == 72 && a.getStores() == 1 && a.getPortFluid(0) == 32000
                    && a.getPortFluid(0) >= SingularReactorSC.HE_START && a.getPortFluid(1) == 60000 && a.getLabel(24 + SingularReactorSC.wallIndex(-3, 0)) == 1 && b.isReady() && !b.canLight();
            System.out.println("[SC-WORLDTEST] " + (ok ? "PASS" : "FAIL") + " singular build: ready " + a.isReady() + ", coils " + a.getCoilCount()
                    + ", walls " + a.getWallCount() + ", stores " + a.getStores() + ", He " + a.getPortFluid(0) + ", D " + a.getPortFluid(1)
                    + ", B (no helium) can light " + b.canLight());
            chargeStart = store(w, AX).getEnergyStored();
            boolean lit = a.light();
            System.out.println("[SC-WORLDTEST] " + (lit ? "PASS" : "FAIL") + " singular lighting starts (capsule registered: " + capsule
                    + ", can light " + a.canLight() + ")");
            b.setRunningForTest(0.5);
            b.setContainmentForTest(3F);
            sing(w, CX).setRunningForTest(0.051);
            SingularReactorSC d = sing(w, DX);
            d.setRunningForTest(0.5);
            d.setContainmentForTest(5F);
        }
        if (ticks == 84) {
            probe = new EntityItem(w, BX + 2.5, Y + 0.2, Z + 0.5, new ItemStack(Items.iron_ingot));
            probe.delayBeforeCanPickup = 1000;
            w.spawnEntityInWorld(probe);
            itemStartX = probe.posX;
        }
        if (ticks == 110) {
            SingularReactorSC c = sing(w, CX), d = sing(w, DX);
            TileEntityGeneratorSC cg = gen(w, CX);
            int ar = tank(w, DX, 0, -3).getTank().getFluidAmount();
            boolean evap = !c.hasHole() && c.getEvent() == SingularReactorSC.EVENT_EVAP && c.getBurstTicks() > 0 && c.isPiercing()
                    && cg.radiationRadiusNow() == SingularReactorSC.EVAP_RADIUS && cg.radiationLevel() > 20F && coils(w, CX) == 16;
            System.out.println("[SC-WORLDTEST] " + (evap ? "PASS" : "FAIL") + " singular evaporation: hole " + c.hasHole() + ", event " + c.getEvent()
                    + ", flash " + c.getBurstTicks() + " to " + cg.radiationRadiusNow() + ", level " + cg.radiationLevel() + ", coils " + coils(w, CX));
            boolean soft = !d.hasHole() && d.getEvent() == SingularReactorSC.EVENT_SOFT && ar == 0 && !gen(w, DX).isIgnited();
            System.out.println("[SC-WORLDTEST] " + (soft ? "PASS" : "FAIL") + " singular argon soft stop: hole " + d.hasHole() + ", event "
                    + d.getEvent() + ", argon left " + ar);
        }
        if (ticks == 120) {
            TileEntityGeneratorSC a = gen(w, AX);
            int drawn = chargeStart - store(w, AX).getEnergyStored();
            boolean ok = sing(w, AX).getPhase() == SingularReactorSC.PHASE_CHARGE && a.getIgnitionEU() > 0 && drawn > 0
                    && a.getIgnitionEU() <= 41L * SingularReactorSC.CHARGE_PER_TICK;
            System.out.println("[SC-WORLDTEST] " + (ok ? "PASS" : "FAIL") + " singular charge from the port storage: " + a.getIgnitionEU()
                    + " EU in 40 ticks (storage gave " + drawn + ")");
            a.setIgnitionForTest();                                       // the rest at once
        }
        if (ticks == 125) {
            sing(w, AX).shortenCompressionForTest(10);
        }
        if (ticks == 130 && probe != null) {
            double moved = itemStartX - probe.posX;
            // a server with no players stops moving entities after 1200 ticks: the pull shows in the velocity it gave
            boolean pull = sing(w, BX).getPhase() == SingularReactorSC.PHASE_PULL && (moved > 0.1 || probe.motionX < -0.01);
            System.out.println("[SC-WORLDTEST] " + (pull ? "PASS" : "FAIL") + " singular pull: phase " + sing(w, BX).getPhase()
                    + ", the item drifted " + String.format("%.2f", moved) + " blocks toward the hole, motion x " + String.format("%.3f", probe.motionX));
        }
        if (ticks == 200) {
            TileEntityGeneratorSC a = gen(w, AX);
            SingularReactorSC s = sing(w, AX);
            int he = tank(w, AX, -3, 0).getTank().getFluidAmount(), dd = tank(w, AX, 3, 0).getTank().getFluidAmount();
            storeBeforeStop = store(w, AX).getEnergyStored();
            boolean ok = s.getPhase() == SingularReactorSC.PHASE_RUN && a.isIgnited() && a.getLastOutput() == 131072
                    && a.outputTier() == Tier.SV && Math.abs(s.getMass() - 0.5) < 0.01 && he < 32000 && dd <= 10000 && s.getContainment() >= 99F;
            System.out.println("[SC-WORLDTEST] " + (ok ? "PASS" : "FAIL") + " singular running: phase " + s.getPhase() + ", made " + a.getLastOutput()
                    + " EU/t at " + a.outputTier() + ", mass " + s.getMass() + ", He left " + he + ", D left " + dd + ", containment "
                    + s.getContainment() + ", storage " + storeBeforeStop + ", status " + a.getStatus());
            net.minecraft.block.Block rb = w.getBlock(AX, Y, Z);
            float hard = rb.getBlockHardness(w, AX, Y, Z);
            boolean locked = hard < 0 && rb.getExplosionResistance(null, w, AX, Y, Z, AX, Y, Z) >= 6000000F;
            System.out.println("[SC-WORLDTEST] " + (locked ? "PASS" : "FAIL") + " singular reactor with its hole is unbreakable: hardness " + hard);
            s.stop();
        }
        if (ticks == 201) {
            sing(w, AX).shortenDrainForTest(20);
        }
        if (ticks == 230) {
            SingularReactorSC b = sing(w, BX);
            TileEntityGeneratorSC bg = gen(w, BX);
            int left = coils(w, BX);
            boolean eject = !b.hasHole() && b.getEvent() == SingularReactorSC.EVENT_EJECT && left >= 12 && left <= 14 && b.getBurstTicks() > 0
                    && b.isPiercing() && bg.radiationRadiusNow() == SingularReactorSC.EJECT_RADIUS && bg.radiationLevel() > 20F;
            System.out.println("[SC-WORLDTEST] " + (eject ? "PASS" : "FAIL") + " singular ejection: hole " + b.hasHole() + ", event " + b.getEvent()
                    + ", coils left " + left + ", flash " + b.getBurstTicks() + " to " + bg.radiationRadiusNow() + ", level " + bg.radiationLevel());
            SingularReactorSC a = sing(w, AX);
            long back = store(w, AX).getEnergyStored() - storeBeforeStop;
            boolean stop = !a.hasHole() && a.getEvent() == SingularReactorSC.EVENT_SOFT && !gen(w, AX).isIgnited()
                    && back >= Math.round(SingularReactorSC.IGNITION_EU * SingularReactorSC.RETURN_SHARE);
            System.out.println("[SC-WORLDTEST] " + (stop ? "PASS" : "FAIL") + " singular stop: hole " + a.hasHole() + ", event " + a.getEvent()
                    + ", the storage got " + back + " EU back");
            a.stop();                                                     // "Allow lighting"
            System.out.println("[SC-WORLDTEST] " + (a.getEvent() == SingularReactorSC.EVENT_NONE ? "PASS" : "FAIL") + " singular latch cleared");
        }
        if (ticks == 240) {
            for (int x : new int[]{AX, BX, CX, DX}) {
                clear(w, x);
            }
            if (probe != null) {
                probe.setDead();
            }
        }
    }

    private static TileEntityGeneratorSC gen(World w, int x) {
        return (TileEntityGeneratorSC) w.getTileEntity(x, Y, Z);
    }

    private static SingularReactorSC sing(World w, int x) {
        return gen(w, x).getSingular();
    }

    private static TileEntityTankSC tank(World w, int x0, int dx, int dz) {
        return (TileEntityTankSC) w.getTileEntity(x0 + dx, Y, Z + dz);
    }

    private static TileEntityEnergyStorageSC store(World w, int x0) {
        return (TileEntityEnergyStorageSC) w.getTileEntity(x0, Y, Z + 3);
    }

    private static int coils(World w, int x0) {
        int n = 0;
        for (int dy = -1; dy <= 1; dy += 2) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    n += w.getBlock(x0 + dx, Y + dy, Z + dz) == ModBlocks.gravityCoil ? 1 : 0;
                }
            }
        }
        return n;
    }

    private static void fill(World w, int x, int z, int tier, Fluid f, int mb) {
        w.setBlock(x, Y, z, ModBlocks.tankSC, tier, 3);
        if (mb > 0) {
            ((TileEntityTankSC) w.getTileEntity(x, Y, z)).fill(ForgeDirection.UNKNOWN, new FluidStack(f, mb), true);
        }
    }

    /** The 7x7x5: lead floor and cap, two coil rings, lead walls; ports in the middle ring; an SV storage. */
    private static void build(World w, int x0, boolean helium, boolean argon) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dx = -3; dx <= 3; dx++) {
                    int role = SingularReactorSC.cellRole(dx, dy, dz);
                    if (role == 0 || role == 1) {
                        w.setBlock(x0 + dx, Y + dy, Z + dz, ModBlocks.leadBlock);
                    } else if (role == 2) {
                        w.setBlock(x0 + dx, Y + dy, Z + dz, ModBlocks.gravityCoil);
                    } else {
                        w.setBlockToAir(x0 + dx, Y + dy, Z + dz);
                    }
                }
            }
        }
        ItemStack st = ModBlocks.generatorStack(GeneratorType.SINGULAR_REACTOR, 1);
        w.setBlock(x0, Y, Z, net.minecraft.block.Block.getBlockFromItem(st.getItem()), st.getItemDamage(), 3);
        TileEntityGeneratorSC g = gen(w, x0);
        g.setGeneratorType(GeneratorType.SINGULAR_REACTOR);
        net.minecraft.item.Item cap = ModItems.component("matterCapsule");
        if (cap != null) {
            g.setInventorySlotContents(TileEntityGeneratorSC.SLOT_FUEL, new ItemStack(cap, 4));
        }
        fill(w, x0 - 3, Z, 1, ModFluids.liquidHelium, helium ? 32000 : 0);
        fill(w, x0 + 3, Z, 2, ModFluids.deuterium, 60000);
        fill(w, x0, Z - 3, 0, ModFluids.argon, argon ? 1000 : 0);
        w.setBlock(x0, Y, Z + 3, ModBlocks.energyStorageSC, Tier.SV.ordinal(), 3);
    }

    private static void clear(World w, int x0) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dx = -3; dx <= 3; dx++) {
                    w.setBlockToAir(x0 + dx, Y + dy, Z + dz);
                }
            }
        }
    }
}
