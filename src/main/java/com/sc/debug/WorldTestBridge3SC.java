package com.sc.debug;

import com.mojang.authlib.GameProfile;
import com.sc.block.BlockBridgeSC;
import com.sc.bridge.BridgeFamiliarSC;
import com.sc.bridge.BridgeMathSC;
import com.sc.bridge.BridgeMsgSC;
import com.sc.bridge.BridgeStructureSC;
import com.sc.init.ModBlocks;
import com.sc.tileentity.TileEntityBridgeCapacitorSC;
import com.sc.tileentity.TileEntityBridgeControllerSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

/**
 * A live check of the bridge, stage 3 (-Dsc.worldtest=true): a calibrated 5x5 ring with the Mass Compensator and a
 * stone pad 200 blocks away. An unfamiliar pad costs +50% and the far end scatters (within 30 blocks, onto the pad);
 * a familiar one costs -25% and the end stands exactly at the target; the ring wore 1%. Then, worn 60%, in a
 * «thunderstorm» and next to another bridge controller, the vortex is turbulent (25%): a pig walked in lands shifted
 * and the mass takes 2% more; the ring overheats in about 20 s - the portal shuts down, the ring is locked (an
 * opening is refused) and wears +10%+; the repair takes the wear away for helium and EU; a swapped coil -10%.
 */
public class WorldTestBridge3SC {

    private static final int X = 330, Y = 200, Z = 570, TX = X + 200, TY = 201, TZ = Z, PAD = 32, ZB = Z + 20;
    private int ticks;
    private FakePlayer walker;
    private int pigId = -1;
    private int stabAtPig;

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
            step((WorldServer) w);
        } catch (Throwable t) {
            say(false, "bridge stage 3 world test threw " + t);
            t.printStackTrace();
            ticks = 100000;
        }
    }

    private static TileEntityBridgeControllerSC ctrl(World w) {
        TileEntity te = w.getTileEntity(X, Y, Z);
        return te instanceof TileEntityBridgeControllerSC ? (TileEntityBridgeControllerSC) te : null;
    }

    private static TileEntityBridgeControllerSC.Order toPad() {
        TileEntityBridgeControllerSC.Order o = new TileEntityBridgeControllerSC.Order();
        o.hasPoint = true;
        o.px = TX;
        o.py = TY;
        o.pz = TZ;
        return o;
    }

    private void step(WorldServer w) {
        if (ticks == 100) {
            build(w);
            walker = FakePlayerFactory.get(w, new GameProfile(java.util.UUID.nameUUIDFromBytes("Bridge3Walker".getBytes()), "Bridge3Walker"));
            walker.setLocationAndAngles(X + 0.5, Y + 1, Z + 3.5, 0F, 0F);
            BridgeFamiliarSC.get(w).forget("Bridge3Walker");      // a previous run's memory (world/data) must not make the pad familiar
        }
        if (ticks == 106) {
            TileEntityBridgeControllerSC c = ctrl(w);
            BridgeStructureSC.Scan s = c.rescan();
            c.calibrate(null);
            // С5: an unfamiliar target - +50%, the far end scatters up to 30 blocks onto a free place
            TileEntityBridgeControllerSC.Plan pl = c.plan(walker, toPad());
            long base = BridgeMathSC.GROUND_BURST + BridgeMathSC.GROUND_PER_1000 * 200 / 1000;
            boolean unfamiliar = pl.refuse == null && pl.fam[0] == 0 && pl.fam[1] == BridgeMathSC.UNFAMILIAR_PCT && pl.fam[2] == BridgeMathSC.SCATTER_UNFAMILIAR
                    && pl.cost.eu == base * 150 / 100;
            long cap = c.capacitorEnergy();
            BridgeMsgSC m = c.openOrder(walker, toPad());
            int[] b = c.getEndB();
            long off = BridgeMathSC.distance(TX, 0, TZ, b[1], 0, b[3]);
            boolean onPad = Math.abs(b[1] - TX) <= PAD - 1 && Math.abs(b[3] - TZ) <= PAD - 1 && b[2] == TY;
            say(s != null && s.valid && c.isCalibrated() && unfamiliar && m == null && c.isOpen() && off <= BridgeMathSC.SCATTER_UNFAMILIAR + 1 && onPad
                    && c.capacitorEnergy() == cap - pl.cost.eu && c.getOpenScatter()[0] == BridgeMathSC.SCATTER_UNFAMILIAR,
                    "bridge 3: an unfamiliar target costs +50% (" + (pl.cost == null ? -1 : pl.cost.eu) + " EU) and its end scatters " + off
                            + " blocks onto the pad (" + b[1] + " " + b[2] + " " + b[3] + ")" + (m == null ? "" : " refused: " + m.key));
            int wearBefore = c.getWear();
            c.closePortal("sc.bridge.journal.closed");
            say(!c.isOpen() && c.getWear() == wearBefore + 1, "bridge 3: a near, light, cool opening wears the ring 1% (" + wearBefore + " -> " + c.getWear() + ")");
        }
        if (ticks == 110) {
            TileEntityBridgeControllerSC c = ctrl(w);
            c.setCoolForTest(0);
            BridgeFamiliarSC.get(w).markBlock("Bridge3Walker", 0, TX, TZ, 0);
            TileEntityBridgeControllerSC.Plan pl = c.plan(walker, toPad());
            long base = BridgeMathSC.GROUND_BURST + BridgeMathSC.GROUND_PER_1000 * 200 / 1000;
            boolean familiar = pl.refuse == null && pl.fam[0] == 1 && pl.fam[2] == 0 && pl.cost.eu == base * 75 / 100;
            // С3 / С6: worn 60%, a «thunderstorm», another bridge 20 blocks away: 100 - 20 (no stabilisers) - 20 - 15 - 20 = 25%
            c.setWearForTest(60);
            c.setStormForTest(true);
            BridgeMsgSC m = c.openOrder(walker, toPad());
            int[] b = c.getEndB();
            say(familiar && m == null && c.isOpen() && b[1] == TX && b[2] == TY && b[3] == TZ && c.getStability() == 25,
                    "bridge 3: a familiar target costs -25% (" + pl.cost.eu + " EU) and opens exactly at it (" + b[1] + " " + b[2] + " " + b[3]
                            + "); wear 60% + storm + another bridge near: stability " + c.getStability() + "%" + (m == null ? "" : " refused: " + m.key));
        }
        if (ticks == 135) {
            TileEntityBridgeControllerSC c = ctrl(w);
            com.sc.bridge.BridgeMathSC.Stab sb = c.stabNow();
            boolean factors = sb.interference == BridgeMathSC.INTERFERENCE_STAB && sb.storm == BridgeMathSC.STORM_STAB && sb.wear == 20 && sb.missing == 20;
            stabAtPig = c.getStability();
            EntityPig pig = new EntityPig(w);
            pig.setLocationAndAngles(X + 0.5, Y + 2, Z + 0.5, 0F, 0F);
            w.spawnEntityInWorld(pig);
            pigId = pig.getEntityId();
            c.enter(pig, 0);
            say(c.isOpen() && c.isTurbulent() && factors && c.getLastShift() != null,
                    "bridge 3: under 30% the vortex is turbulent (interference -" + sb.interference + ", storm -" + sb.storm + ", wear -" + sb.wear
                            + ") and a pig's arrival is shaken off to " + (c.getLastShift() == null ? "-" : c.getLastShift()[0] + " " + c.getLastShift()[1] + " "
                            + c.getLastShift()[2]));
        }
        if (ticks == 160) {
            TileEntityBridgeControllerSC c = ctrl(w);
            Entity pig = w.getEntityByID(pigId);
            double ex = TX + 0.5, ez = TZ + 1.5;
            double d = pig == null ? -1 : Math.sqrt((pig.posX - ex) * (pig.posX - ex) + (pig.posZ - ez) * (pig.posZ - ez));
            say(pig != null && d >= 0 && d <= BridgeMathSC.TURB_SHIFT + 6 && Math.abs(pig.posY - TY) < 1.6 && c.getStability() == stabAtPig - 2,
                    "bridge 3: the shaken pig stands on the pad " + String.format("%.1f", d) + " blocks from the planned exit; its mass took 2% ("
                            + stabAtPig + " -> " + c.getStability() + ")");
            if (pig != null) {
                pig.setDead();
            }
        }
        if (ticks == 620) {
            // С12: wear 60 + a shaking vortex: 5.2%/s - the ring overheats in about 20 s, long before the 30 s
            TileEntityBridgeControllerSC c = ctrl(w);
            int wear = c.getWear();
            BridgeMsgSC m = c.tryOpen(walker);
            say(!c.isOpen() && c.isOverheatLocked() && c.getCoolTicks() > 0 && c.getCoolTicks() <= BridgeMathSC.OVERHEAT_LOCK_S * 20
                    && c.getCoolTicks() > BridgeMathSC.coolTicks() && wear == 72 && m != null && "sc.bridge.refuse.overheat".equals(m.key)
                    && c.getHeat() > 0,
                    "bridge 3: the ring overheats - emergency shutdown, locked " + (c.getCoolTicks() + 19) / 20 + " s (refused: " + (m == null ? "opened!" : m.key)
                            + "), wear 60 -> " + wear + " (+2 for a hot opening, +10 overheat), heat " + c.getHeat() / 10 + "% cooling");
            // С2: the repair
            c.setCoolForTest(0);
            int he = c.tankAmount(BridgeMathSC.HE);
            long eu = c.capacitorEnergy();
            BridgeMsgSC r = c.repair(null);
            say(c.getWear() == 0 && c.tankAmount(BridgeMathSC.HE) == he - BridgeMathSC.repairHe(72) && c.capacitorEnergy() == eu - BridgeMathSC.repairEu(72)
                    && r != null && "sc.bridge.journal.repaired".equals(r.key),
                    "bridge 3: «Ремонт» takes the wear 72% -> " + c.getWear() + "% for " + (he - c.tankAmount(BridgeMathSC.HE)) + " mB He and "
                            + (eu - c.capacitorEnergy()) + " EU");
            // М-4: a swapped coil keeps the wear (only «Ремонт» takes it off), the calibration is gone
            c.setWearForTest(30);
            w.setBlockToAir(X - 2, Y + 3, Z);
            say(c.getWear() == 30 && !c.isCalibrated(), "bridge 3: a coil taken out keeps the wear (" + c.getWear() + "%) and loses the calibration");
            // М-7: the ring is ownerless here - anyone opens, but not the settings; a fake player never claims it
            String nm = c.getBridgeName();
            c.action(walker, TileEntityBridgeControllerSC.A_NAME, new int[0], "Hijack");
            say(c.ownerless() && c.allowed(walker) && !c.trusted(walker) && !c.isOwner(walker) && !c.claim(walker) && nm.equals(c.getBridgeName()),
                    "bridge 3: an ownerless controller - opening for all, its name / settings refused, a fake player doesn't claim it");
            // М-7: a controller placed by automation (a FakePlayer) stays ownerless - a real player can claim it
            w.setBlock(X + 20, Y, Z, com.sc.init.ModBlocks.bridge, com.sc.block.BlockBridgeSC.CONTROLLER, 3);
            com.sc.init.ModBlocks.bridge.onBlockPlacedBy(w, X + 20, Y, Z, walker,
                    new net.minecraft.item.ItemStack(com.sc.init.ModBlocks.bridge, 1, com.sc.block.BlockBridgeSC.CONTROLLER));
            net.minecraft.tileentity.TileEntity fte = w.getTileEntity(X + 20, Y, Z);
            boolean fakeOwnerless = fte instanceof TileEntityBridgeControllerSC && ((TileEntityBridgeControllerSC) fte).ownerless()
                    && !((TileEntityBridgeControllerSC) fte).claim(walker);
            say(fakeOwnerless, "bridge 3: a controller placed by a fake player has no owner (claimable by a real player)");
            w.setBlockToAir(X + 20, Y, Z);
        }
        if (ticks == 630) {
            BridgeFamiliarSC.get(w).forget("Bridge3Walker");
            for (int x = X - 3; x <= X + 3; x++) {
                for (int y = Y - 1; y <= Y + 6; y++) {
                    for (int z = Z - 1; z <= Z + 1; z++) {
                        w.setBlockToAir(x, y, z);
                    }
                }
            }
            w.setBlockToAir(X, Y, ZB);
            for (int x = TX - PAD; x <= TX + PAD; x++) {
                for (int z = TZ - PAD; z <= TZ + PAD; z++) {
                    for (int y = TY - 1; y <= TY + 3; y++) {
                        if (!w.isAirBlock(x, y, z)) {
                            w.setBlockToAir(x, y, z);
                        }
                    }
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
        w.setBlock(X, Y, ZB, ModBlocks.bridge, BlockBridgeSC.CONTROLLER, 3);            // another bridge 20 blocks away (С6)
        for (int x = TX - PAD; x <= TX + PAD; x++) {
            for (int z = TZ - PAD; z <= TZ + PAD; z++) {
                w.setBlock(x, TY - 1, z, Blocks.stone, 0, 2);
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
