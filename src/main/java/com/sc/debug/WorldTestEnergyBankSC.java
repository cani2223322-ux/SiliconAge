package com.sc.debug;

import com.sc.block.BlockEnergyBankSC;
import com.sc.energy.EnergyBankMathSC;
import com.sc.energy.Tier;
import com.sc.init.IdeaBlocksSC;
import com.sc.tileentity.TileEntityEnergyBankPortSC;
import com.sc.tileentity.TileEntityEnergyBankSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

/**
 * ОМ-7 (docs/plan-ideas-2026-10-26.md, -Dsc.worldtest=true; the Overworld, X = 4000 + 100 x 15, Y 200, Z 0): a 3 x 3 x 3 bank - casing,
 * the controller on the west face, an LV port IN on the east face, an LV port OUT on top (its buffer full: nothing leaves), a 4-billion
 * cell inside. Formed (side 3, the capacity, 1 in / 1 out); EU put in the IN port's buffer reach the bank; the comparator > 0; the cell
 * broken - not formed, the energy kept, the input stands. Removed afterwards.
 */
public class WorldTestEnergyBankSC {

    private static final int X = 5500, Y = 200, Z = 0;
    private int ticks;
    private long kept;

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        ticks++;
        World w = DimensionManager.getWorld(0);
        if (w == null || IdeaBlocksSC.energyBank == null) {
            return;
        }
        if (ticks == 60) {
            build(w);
        }
        if (ticks == 70) {
            port(w, X + 2, Y + 1, Z + 1).setEnergyStoredClient(Tier.LV.getBuffer());
            port(w, X + 1, Y + 2, Z + 1).setEnergyStoredClient(Tier.LV.getBuffer());     // OUT full: nothing leaves the bank
        }
        if (ticks == 130) {
            TileEntityEnergyBankSC b = bank(w);
            long cap = EnergyBankMathSC.scaledCapacity(1, 0, com.sc.util.ConfigSC.storageCapacity);
            boolean formed = b.formed() && b.size() == 3 && b.capacity() == cap && b.cellsSmall() == 1 && b.portsIn() == 1 && b.portsOut() == 1;
            boolean in = b.energy() == Tier.LV.getBuffer() && b.comparatorLevel() > 0 && port(w, X + 2, Y + 1, Z + 1).getEnergyStored() == 0;
            System.out.println("[SC-WORLDTEST] " + (formed && in ? "PASS" : "FAIL") + " ОМ-7 Energy Bank 3x3x3: formed " + b.formed() + ", side "
                    + b.size() + ", capacity " + b.capacity() + " (" + cap + "), cells " + b.cellsSmall() + ", ports in " + b.portsIn() + " / out "
                    + b.portsOut() + "; energy in " + b.energy() + ", comparator " + b.comparatorLevel());
            kept = b.energy();
            w.setBlockToAir(X + 1, Y + 1, Z + 1);                                     // the cell broken
        }
        if (ticks == 160) {
            port(w, X + 2, Y + 1, Z + 1).setEnergyStoredClient(Tier.LV.getBuffer());
        }
        if (ticks == 200) {
            TileEntityEnergyBankSC b = bank(w);
            boolean ok = !b.formed() && b.energy() == kept && kept > 0;
            boolean stands = port(w, X + 2, Y + 1, Z + 1).getEnergyStored() == Tier.LV.getBuffer();
            System.out.println("[SC-WORLDTEST] " + (ok && stands ? "PASS" : "FAIL") + " ОМ-7 the cell broken: formed " + b.formed() + ", energy "
                    + b.energy() + " (was " + kept + "), the reason " + b.badReason() + ", the input stands " + stands);
            for (int dy = 0; dy < 3; dy++) {
                for (int dz = 0; dz < 3; dz++) {
                    for (int dx = 0; dx < 3; dx++) {
                        w.setBlockToAir(X + dx, Y + dy, Z + dz);
                    }
                }
            }
        }
    }

    private static TileEntityEnergyBankSC bank(World w) {
        return (TileEntityEnergyBankSC) w.getTileEntity(X, Y + 1, Z + 1);
    }

    private static TileEntityEnergyBankPortSC port(World w, int x, int y, int z) {
        return (TileEntityEnergyBankPortSC) w.getTileEntity(x, y, z);
    }

    private static void build(World w) {
        for (int dy = 0; dy < 3; dy++) {
            for (int dz = 0; dz < 3; dz++) {
                for (int dx = 0; dx < 3; dx++) {
                    w.setBlock(X + dx, Y + dy, Z + dz, IdeaBlocksSC.energyBank, BlockEnergyBankSC.CASING, 3);
                }
            }
        }
        w.setBlock(X + 1, Y + 1, Z + 1, IdeaBlocksSC.energyBank, BlockEnergyBankSC.CELL_4, 3);
        w.setBlock(X, Y + 1, Z + 1, IdeaBlocksSC.energyBank, BlockEnergyBankSC.CONTROLLER, 3);
        w.setBlock(X + 2, Y + 1, Z + 1, IdeaBlocksSC.energyBank, BlockEnergyBankSC.PORT_FIRST + Tier.LV.ordinal(), 3);
        w.setBlock(X + 1, Y + 2, Z + 1, IdeaBlocksSC.energyBank, BlockEnergyBankSC.PORT_FIRST + Tier.LV.ordinal(), 3);
        port(w, X + 1, Y + 2, Z + 1).toggleOut();
    }
}
