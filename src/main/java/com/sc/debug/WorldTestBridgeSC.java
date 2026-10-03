package com.sc.debug;

import com.sc.block.BlockBridgeSC;
import com.sc.bridge.BridgeMathSC;
import com.sc.bridge.BridgeMsgSC;
import com.sc.bridge.BridgeStructureSC;
import com.sc.init.ModBlocks;
import com.sc.tileentity.TileEntityBridgeCapacitorSC;
import com.sc.tileentity.TileEntityBridgeControllerSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

/**
 * A live check of the Ground Bridge (stage 1, -Dsc.worldtest=true): a 5x5 ring of gravity coils along X on
 * its controller, two capacitors, an energy port, a gas port and the Mass Compensator, built in the air; the
 * tanks and capacitors filled. It refuses to open before the calibration, calibrates (Kr 500 + 100 M EU),
 * the place check inside a block finds the free place just above it, then opens to a stone pad 200 blocks
 * away: the vortex stands at both ends, exactly the cost is taken, a pig walked into the ring comes out at
 * the pad, the hold eats helium; after the 30 s the vortex is gone from both ends and the hot ring refuses
 * a new opening. Everything is removed afterwards.
 */
public class WorldTestBridgeSC {

    private static final int X = 330, Y = 200, Z = 90, TX = 530, TY = 201, TZ = 90;
    private int ticks;
    private long capBefore;
    private int[] tanksBefore = new int[6];
    private int pigId = -1;
    private int heAtOpen;

    private static void say(boolean ok, String what) {
        System.out.println("[SC-WORLDTEST] " + (ok ? "PASS" : "FAIL") + " " + what);
    }

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
        try {
            step(w);
        } catch (Throwable t) {
            say(false, "bridge world test threw " + t);
            t.printStackTrace();
            ticks = 100000;
        }
    }

    private static TileEntityBridgeControllerSC ctrl(World w) {
        TileEntity te = w.getTileEntity(X, Y, Z);
        return te instanceof TileEntityBridgeControllerSC ? (TileEntityBridgeControllerSC) te : null;
    }

    private static int vortexCount(World w, int x0, int x1, int y0, int y1, int z0, int z1) {
        int n = 0;
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    if (w.getBlock(x, y, z) == ModBlocks.bridgeVortex) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    private void step(World w) {
        if (ticks == 60) {
            build(w);
        }
        if (ticks == 66) {
            TileEntityBridgeControllerSC c = ctrl(w);
            BridgeStructureSC.Scan s = c == null ? null : c.rescan();
            boolean ok = s != null && s.valid && s.kind == BridgeMathSC.GROUND && s.axis == 0 && s.coils == 16 && s.capacitors.size() == 2
                    && s.energyPorts.size() == 1 && s.gasPorts.size() == 1 && s.mass && c.capacitorEnergy() == 2 * BridgeMathSC.CAPACITOR_EU;
            say(ok, "bridge: the 5x5 Ground ring along X is valid - coils " + (s == null ? -1 : s.coils) + ", capacitors "
                    + (s == null ? -1 : s.capacitors.size()) + ", problems " + (s == null ? -1 : s.problems.size())
                    + (s != null && !s.problems.isEmpty() ? " (" + s.problems.get(0).key + ")" : "") + ", EU " + (c == null ? -1 : c.capacitorEnergy()));
            c.setTarget(TX, TY, TZ, 0);
            BridgeMsgSC m = c.tryOpen(null);
            say(m != null && "sc.bridge.refuse.calib".equals(m.key) && !c.isOpen(), "bridge refuses to open before the calibration: "
                    + (m == null ? "opened!" : m.key));
            int kr = c.tankAmount(BridgeMathSC.KR);
            long eu = c.capacitorEnergy();
            BridgeMsgSC cal = c.calibrate(null);
            say(c.isCalibrated() && c.tankAmount(BridgeMathSC.KR) == kr - BridgeMathSC.CALIB_KR && c.capacitorEnergy() == eu - BridgeMathSC.CALIB_EU,
                    "bridge calibration: Kr " + kr + " -> " + c.tankAmount(BridgeMathSC.KR) + ", EU " + eu + " -> " + c.capacitorEnergy() + " (" + cal.key + ")");
            c.setTarget(TX, TY - 1, TZ, 0);                       // inside the stone pad
            c.probe(null);
            BridgeMsgSC pm = c.getLastMsg();
            net.minecraft.nbt.NBTTagCompound st = c.writeState(null);
            int[] near = st.getCompoundTag("place").getIntArray("near");
            boolean pok = !st.getCompoundTag("place").getBoolean("free") && near.length == 4 && near[0] == TX && near[1] == TY && near[2] == TZ
                    && near[3] == 1 && c.tankAmount(BridgeMathSC.KR) == kr - BridgeMathSC.CALIB_KR - BridgeMathSC.PROBE_KR;
            say(pok, "bridge place check inside the pad: blocked (" + (pm == null ? "-" : pm.parts.isEmpty() ? pm.key : pm.parts.get(0).key)
                    + "), nearest free " + (near.length == 4 ? near[0] + " " + near[1] + " " + near[2] + " in " + near[3] : "none"));
            c.setTarget(TX, TY, TZ, 0);
            BridgeMathSC.Cost cost = c.previewCost();
            capBefore = c.capacitorEnergy();
            for (int i = 0; i < 6; i++) {
                tanksBefore[i] = c.tankAmount(i);
            }
            BridgeMsgSC om = c.tryOpen(null);
            int a = vortexCount(w, X - 2, X + 2, Y + 1, Y + 5, Z, Z), b = vortexCount(w, TX - 1, TX + 1, TY, TY + 2, TZ, TZ);
            boolean paid = c.capacitorEnergy() == capBefore - cost.eu && c.tankAmount(BridgeMathSC.SM) == tanksBefore[BridgeMathSC.SM] - cost.sm
                    && c.tankAmount(BridgeMathSC.D) == tanksBefore[BridgeMathSC.D] - cost.d && c.tankAmount(BridgeMathSC.KR) == tanksBefore[BridgeMathSC.KR] - cost.kr
                    && c.tankAmount(BridgeMathSC.AR) == tanksBefore[BridgeMathSC.AR] - cost.ar;
            boolean costOk = cost.eu == BridgeMathSC.GROUND_BURST + BridgeMathSC.GROUND_PER_1000 * 200 / 1000 && cost.sm == 52 && cost.kr == 21;
            say(om == null && c.isOpen() && a == 9 && b == 9 && paid && costOk, "bridge opens 200 blocks away at once: vortex cells " + a + " at the ring, " + b
                    + " at the target, burst " + cost.eu + " EU (capacitors " + capBefore + " -> " + c.capacitorEnergy() + "), SM " + cost.sm + ", Kr " + cost.kr
                    + (om == null ? "" : " refused: " + om.key));
            heAtOpen = c.tankAmount(BridgeMathSC.HE);
            EntityPig pig = new EntityPig(w);
            pig.setLocationAndAngles(X + 0.5, Y + 2, Z + 0.5, 0F, 0F);
            w.spawnEntityInWorld(pig);
            pigId = pig.getEntityId();
        }
        if (ticks == 100) {
            net.minecraft.entity.Entity pig = w.getEntityByID(pigId);
            boolean ok = pig != null && Math.abs(pig.posX - (TX + 0.5)) < 3 && Math.abs(pig.posZ - (TZ + 1.5)) < 3 && Math.abs(pig.posY - TY) < 1.5;
            say(ok, "bridge: a pig walked into the ring comes out at the far end (Mass Compensator): "
                    + (pig == null ? "gone" : String.format("%.1f %.1f %.1f", pig.posX, pig.posY, pig.posZ)));
            TileEntityBridgeControllerSC c = ctrl(w);
            say(c != null && c.isOpen() && c.tankAmount(BridgeMathSC.HE) < heAtOpen && c.tankAmount(BridgeMathSC.HE) >= heAtOpen - 30,
                    "bridge hold eats helium (10 mB/s): " + heAtOpen + " -> " + (c == null ? -1 : c.tankAmount(BridgeMathSC.HE)));
            if (pig != null) {
                pig.setDead();
            }
        }
        if (ticks == 690) {
            TileEntityBridgeControllerSC c = ctrl(w);
            int a = vortexCount(w, X - 2, X + 2, Y + 1, Y + 5, Z, Z), b = vortexCount(w, TX - 1, TX + 1, TY, TY + 2, TZ, TZ);
            boolean ok = c != null && !c.isOpen() && a == 0 && b == 0 && c.getCoolTicks() > 0 && c.getCoolTicks() <= BridgeMathSC.coolTicks();
            say(ok, "bridge folds after its 30 s: vortex cells left " + a + " / " + b + ", cooling " + (c == null ? -1 : c.getCoolTicks()) + " ticks");
            BridgeMsgSC m = c == null ? null : c.tryOpen(null);
            say(m != null && "sc.bridge.refuse.cooling".equals(m.key) && !c.isOpen(), "bridge: the hot ring refuses a new opening ("
                    + (m == null ? "opened!" : m.key + " " + (m.args.length > 0 ? m.args[0] + " s" : "")) + ")");
        }
        if (ticks == 700) {
            for (int x = X - 3; x <= X + 3; x++) {
                for (int y = Y - 1; y <= Y + 6; y++) {
                    for (int z = Z - 1; z <= Z + 1; z++) {
                        w.setBlockToAir(x, y, z);
                    }
                }
            }
            for (int x = TX - 2; x <= TX + 2; x++) {
                for (int z = TZ - 2; z <= TZ + 2; z++) {
                    w.setBlockToAir(x, TY - 1, z);
                }
            }
        }
    }

    private static void build(World w) {
        for (int u = -2; u <= 2; u++) {
            for (int v = 1; v <= 5; v++) {
                if (BridgeStructureSC.isRing(5, u, v)) {
                    w.setBlock(X + u, Y + v, Z, ModBlocks.gravityCoil);
                }
            }
        }
        w.setBlock(X, Y, Z, ModBlocks.bridge, BlockBridgeSC.CONTROLLER, 3);
        w.setBlock(X - 1, Y, Z, ModBlocks.bridge, BlockBridgeSC.CAPACITOR, 3);
        w.setBlock(X - 2, Y, Z, ModBlocks.bridge, BlockBridgeSC.CAPACITOR, 3);
        w.setBlock(X + 1, Y, Z, ModBlocks.bridge, BlockBridgeSC.ENERGY_PORT, 3);
        w.setBlock(X + 2, Y, Z, ModBlocks.bridge, BlockBridgeSC.GAS_PORT, 3);
        w.setBlock(X, Y - 1, Z, ModBlocks.bridge, BlockBridgeSC.MASS, 3);
        for (int x = TX - 2; x <= TX + 2; x++) {
            for (int z = TZ - 2; z <= TZ + 2; z++) {
                w.setBlock(x, TY - 1, z, Blocks.stone);
            }
        }
        TileEntityBridgeControllerSC c = ctrl(w);
        if (c == null) {
            return;
        }
        c.setPowerOn(true);
        int[] fill = {20000, 10000, 4000, 8000, 8000, 8000};
        for (int i = 0; i < fill.length; i++) {
            c.putTankForTest(i, fill[i]);
        }
        for (int x = X - 2; x <= X - 1; x++) {
            TileEntity te = w.getTileEntity(x, Y, Z);
            if (te instanceof TileEntityBridgeCapacitorSC) {
                ((TileEntityBridgeCapacitorSC) te).setEnergy(BridgeMathSC.CAPACITOR_EU);
            }
        }
    }
}
