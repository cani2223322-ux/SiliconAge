package com.sc.debug;

import com.sc.block.BlockBridgeSC;
import com.sc.bridge.BridgeMathSC;
import com.sc.bridge.BridgeMsgSC;
import com.sc.bridge.BridgeSoftLandSC;
import com.sc.bridge.BridgeStructureSC;
import com.sc.init.ModBlocks;
import com.sc.tileentity.TileEntityBridgeCapacitorSC;
import com.sc.tileentity.TileEntityBridgeControllerSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

/**
 * A live check of §7б (-Dsc.worldtest=true): a calibrated 5x5 Ground ring with the Mass Compensator; the target
 * point hangs 20 blocks over a stone pad. «Проверить место» says free, in the air, no void; the far end's vortex
 * opens right there in the free air; a pig walked through comes out with the soft landing - falls slowly and lands
 * on the pad unhurt - while a control pig dropped from the same height beside it does get hurt. Removed afterwards.
 */
public class WorldTestBridgeAirSC {

    private static final int X = 330, Y = 200, Z = 810, TX = X + 200, PAD_Y = 180, TY = PAD_Y + 21, TZ = Z, PAD = 8;
    private static final int T0 = 140;
    private int ticks;
    private int pigId = -1, controlId = -1;
    private double pigStartY;

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
            say(false, "bridge air-end world test threw " + t);
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
        if (ticks == T0) {
            build(w);
        }
        if (ticks == T0 + 6) {
            TileEntityBridgeControllerSC c = ctrl(w);
            BridgeStructureSC.Scan s = c.rescan();
            c.calibrate(null);
            c.setTarget(TX, TY, TZ, 0);
            c.probe(null);
            NBTTagCompound place = c.writeState(null).getCompoundTag("place");
            BridgeMsgSC pm = c.getLastMsg();
            say(s != null && s.valid && c.isCalibrated() && place.getBoolean("free") && place.getBoolean("air") && !place.getBoolean("void")
                            && pm != null && "sc.bridge.place.freeairat".equals(pm.key),
                    "bridge air: «Проверить место» 20 blocks over the ground - free " + place.getBoolean("free") + ", in the air "
                            + place.getBoolean("air") + ", void below " + place.getBoolean("void") + " (" + (pm == null ? "-" : pm.key) + ")");
            BridgeMsgSC om = c.tryOpen(null);
            int b = vortexCount(w, TX - 1, TX + 1, TY, TY + 2, TZ, TZ);
            boolean airUnder = w.isAirBlock(TX, TY - 1, TZ) && w.isAirBlock(TX, TY - 1, TZ + 1);
            say(om == null && c.isOpen() && b == 9 && airUnder, "bridge air: the far end's vortex opens in the free air (" + b + " cells at "
                    + TX + " " + TY + " " + TZ + ", nothing under it)" + (om == null ? "" : " refused: " + om.key));
            EntityPig pig = new EntityPig(w);
            pig.setLocationAndAngles(X + 0.5, Y + 2, Z + 0.5, 0F, 0F);
            w.spawnEntityInWorld(pig);
            pigId = pig.getEntityId();
            c.enter(pig, 0);
            EntityPig control = new EntityPig(w);
            control.setLocationAndAngles(TX + 4.5, TY, TZ + 1.5, 0F, 0F);
            w.spawnEntityInWorld(control);
            controlId = control.getEntityId();
            Entity moved = w.getEntityByID(pigId);
            pigStartY = moved == null ? -1 : moved.posY;
        }
        if (ticks == T0 + 46) {
            Entity pig = w.getEntityByID(pigId);
            boolean slow = pig != null && BridgeSoftLandSC.active(pig) && pig.posY < pigStartY - 1 && pig.posY > PAD_Y + 4
                    && pig.motionY >= BridgeSoftLandSC.FALL_CAP - 0.09;
            say(slow, "bridge air: the pig came out of the air end with the soft landing and falls slowly ("
                    + (pig == null ? "gone" : String.format("y %.1f -> %.1f in 2 s, motion %.2f", pigStartY, pig.posY, pig.motionY)) + ")");
        }
        if (ticks == T0 + 200) {
            Entity pe = w.getEntityByID(pigId), ce = w.getEntityByID(controlId);
            EntityPig pig = pe instanceof EntityPig ? (EntityPig) pe : null;
            EntityPig control = ce instanceof EntityPig ? (EntityPig) ce : null;
            boolean landed = pig != null && !pig.isDead && pig.getHealth() >= pig.getMaxHealth() && Math.abs(pig.posY - (PAD_Y + 1)) < 0.6
                    && !BridgeSoftLandSC.active(pig);
            boolean hurt = control == null || control.isDead || control.getHealth() < control.getMaxHealth();
            say(landed && hurt, "bridge air: the pig landed on the pad unhurt (" + (pig == null ? "gone" : String.format("y %.1f, %.0f/%.0f HP", pig.posY,
                    pig.getHealth(), pig.getMaxHealth())) + "), the control pig dropped from the same height without it is hurt ("
                    + (control == null || control.isDead ? "dead" : String.format("%.0f HP", control.getHealth())) + ")");
            if (pig != null) {
                pig.setDead();
            }
            if (control != null) {
                control.setDead();
            }
            TileEntityBridgeControllerSC c = ctrl(w);
            if (c != null && c.isOpen()) {
                c.closePortal("sc.bridge.journal.closed");
            }
        }
        if (ticks == T0 + 210) {
            for (int x = X - 3; x <= X + 3; x++) {
                for (int y = Y - 1; y <= Y + 6; y++) {
                    for (int z = Z - 1; z <= Z + 1; z++) {
                        w.setBlockToAir(x, y, z);
                    }
                }
            }
            for (int x = TX - PAD; x <= TX + PAD; x++) {
                for (int z = TZ - PAD; z <= TZ + PAD; z++) {
                    w.setBlockToAir(x, PAD_Y, z);
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
        for (int x = TX - PAD; x <= TX + PAD; x++) {
            for (int z = TZ - PAD; z <= TZ + PAD; z++) {
                w.setBlock(x, PAD_Y, z, Blocks.stone);
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
