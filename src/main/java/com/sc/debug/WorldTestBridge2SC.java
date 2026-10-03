package com.sc.debug;

import com.mojang.authlib.GameProfile;
import com.sc.block.BlockBridgeSC;
import com.sc.bridge.BridgeConsentSC;
import com.sc.bridge.BridgeFarSC;
import com.sc.bridge.BridgeItemDataSC;
import com.sc.bridge.BridgeMathSC;
import com.sc.bridge.BridgeMsgSC;
import com.sc.bridge.BridgeStructureSC;
import com.sc.init.ModBlocks;
import com.sc.init.ModItems;
import com.sc.item.ItemBridgeRemoteSC;
import com.sc.tileentity.TileEntityBridgeCapacitorSC;
import com.sc.tileentity.TileEntityBridgeControllerSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

/**
 * A live check of the Ground Bridge, stage 2 (-Dsc.worldtest=true): a calibrated 5x5 ring with an owner and a friend;
 * a stranger's remote is refused, the owner's binds; «Дистанционный режим» takes a chunk ticket; the remote's
 * «Домой» from a fake player 300 blocks away opens a vortex next to him (facing his way) and in the ring and costs
 * the remote 1 M EU; ДР4 «Забрать друга» first asks the friend (nothing opens, the signal is paid), a stranger
 * can't answer for him, his «Принять» opens a vortex next to the friend and in the ring. Everything is removed afterwards.
 */
public class WorldTestBridge2SC {

    private static final int X = 330, Y = 200, Z = 330, PX = X + 300, PZ = Z, FZ = Z + 24;
    private int ticks;
    private FakePlayer owner, friend, stranger;
    private ItemStack remote;

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
            say(false, "bridge stage 2 world test threw " + t);
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

    private FakePlayer fake(WorldServer w, String name, int x, int z) {
        FakePlayer p = FakePlayerFactory.get(w, new GameProfile(java.util.UUID.nameUUIDFromBytes(name.getBytes()), name));
        p.setLocationAndAngles(x + 0.5, Y + 1, z + 0.5, 0F, 0F);       // facing south (+Z)
        TileEntityBridgeControllerSC.TEST_PLAYERS.put(name.toLowerCase(java.util.Locale.ROOT), p);
        return p;
    }

    private void step(WorldServer w) {
        if (ticks == 80) {
            build(w);
            owner = fake(w, "BridgeOwner", PX, PZ);
            friend = fake(w, "BridgeFriend", PX, FZ);
            stranger = fake(w, "BridgeStranger", PX + 3, PZ - 3);
        }
        if (ticks == 86) {
            TileEntityBridgeControllerSC c = ctrl(w);
            BridgeStructureSC.Scan s = c == null ? null : c.rescan();
            c.calibrate(null);
            c.setOwner("BridgeOwner");
            c.addFriend("BridgeFriend");
            c.setBridgeName("Тест");
            boolean access = c.trusted(owner) && c.trusted(friend) && !c.trusted(stranger) && !c.allowed(stranger);
            c.setAccess(BridgeMathSC.ACCESS_PUBLIC);
            access &= c.allowed(stranger) && !c.trusted(stranger);
            c.setAccess(BridgeMathSC.ACCESS_FRIENDS);
            say(s != null && s.valid && c.isCalibrated() && access, "bridge 2: owner + friend trusted, a stranger not (public mode: may use the screen, still not trusted)");
            // binding
            ItemStack theirs = new ItemStack(ModItems.bridgeRemote, 1, ItemBridgeRemoteSC.GROUND);
            String k1 = ItemBridgeRemoteSC.bind(theirs, stranger, c);
            remote = new ItemStack(ModItems.bridgeRemote, 1, ItemBridgeRemoteSC.GROUND);
            ItemStack space = new ItemStack(ModItems.bridgeRemote, 1, ItemBridgeRemoteSC.SPACE);
            String k3 = ItemBridgeRemoteSC.bind(space, owner, c);
            String k2 = ItemBridgeRemoteSC.bind(remote, owner, c);
            int[] link = BridgeItemDataSC.remoteLink(remote);
            say("sc.bridge.remote.noaccess".equals(k1) && BridgeItemDataSC.remoteLink(theirs) == null && "sc.bridge.remote.needspace".equals(k3)
                    && "sc.bridge.remote.bound".equals(k2) && link != null && link[0] == X && link[2] == Z && BridgeItemDataSC.remoteId(remote) == c.getBridgeId()
                    && c.getBridgeId() != 0, "bridge 2: a stranger's remote is refused, a Space remote doesn't fit a Ground bridge, the owner's binds ("
                    + k1 + ", " + k3 + ", " + k2 + ")");
            // «Дистанционный режим»
            c.action(owner, TileEntityBridgeControllerSC.A_REMOTE_MODE, new int[0], "");
            BridgeItemDataSC.setCharge(remote, 3 * BridgeMathSC.REMOTE_SIGNAL_EU);
            owner.inventory.mainInventory[0] = remote;
            owner.inventory.currentItem = 0;
            NBTTagCompound out = BridgeFarSC.handle(owner, BridgeFarSC.SRC_REMOTE, 0, BridgeFarSC.F_OPEN,
                    new int[]{BridgeMathSC.MODE_HOME, 0, 0, 0, 0, 0, 0}, "");
            remote = owner.inventory.mainInventory[0];
            int ring = vortexCount(w, X - 2, X + 2, Y + 1, Y + 5, Z, Z), near = vortexCount(w, PX - 1, PX + 1, Y + 1, Y + 3, PZ + 3, PZ + 3);
            int[] a = c.getEndB();
            BridgeMsgSC m = out == null || !out.hasKey("msg") ? null : BridgeMsgSC.read(out.getCompoundTag("msg"));
            say(c.isOpen() && ring == 9 && near == 9 && BridgeItemDataSC.charge(remote) == 2 * BridgeMathSC.REMOTE_SIGNAL_EU && c.getOpenMode() == BridgeMathSC.MODE_HOME
                    && "BridgeOwner".equals(c.getOpener()) && a[1] == PX && a[3] == PZ + 3 && c.getEndA() == null,
                    "bridge 2: the remote's «Домой» 300 blocks away opens a vortex next to the player (" + near + " cells at " + a[1] + " " + a[2] + " " + a[3]
                            + ") and in the ring (" + ring + "), signal 1 M EU paid (" + BridgeItemDataSC.charge(remote) + ")" + (m == null ? "" : " - " + m.key));
        }
        if (ticks == 90) {
            TileEntityBridgeControllerSC c = ctrl(w);
            say(c != null && c.isRemoteMode() && c.hasRemoteTicket(), "bridge 2: «Дистанционный режим» keeps the controller's chunk (a ticket)");
            c.closeFrom(stranger);
            boolean stillOpen = c.isOpen();
            c.closeFrom(owner);
            int ring = vortexCount(w, X - 2, X + 2, Y + 1, Y + 5, Z, Z), near = vortexCount(w, PX - 1, PX + 1, Y + 1, Y + 3, PZ + 3, PZ + 3);
            say(stillOpen && !c.isOpen() && ring == 0 && near == 0, "bridge 2: a stranger can't close it, the owner's «Закрыть» folds both ends");
            c.setCoolForTest(0);
            // ДР4: the friend must agree first
            owner.inventory.mainInventory[0] = remote;
            NBTTagCompound out = BridgeFarSC.handle(owner, BridgeFarSC.SRC_REMOTE, 0, BridgeFarSC.F_OPEN,
                    new int[]{BridgeMathSC.MODE_FRIEND, 0, 0, 0, 0, 0, 0}, "BridgeFriend\n");
            remote = owner.inventory.mainInventory[0];
            BridgeMsgSC m = out == null || !out.hasKey("msg") ? null : BridgeMsgSC.read(out.getCompoundTag("msg"));
            int id = c.getLastConsentId();
            boolean asked = m != null && "sc.bridge.msg.consentSent".equals(m.key) && !c.isOpen() && id > 0
                    && BridgeConsentSC.server().state(id) == BridgeConsentSC.PENDING && BridgeItemDataSC.charge(remote) == BridgeMathSC.REMOTE_SIGNAL_EU;
            say(asked, "bridge 2: ДР4 «Забрать друга» asks the friend first - nothing opens, the signal is paid (" + (m == null ? "-" : m.key) + ")");
            BridgeMsgSC wrong = BridgeFarSC.answer(stranger, id, true);
            boolean notYours = wrong != null && "sc.bridge.consent.notyours".equals(wrong.key) && !c.isOpen();
            BridgeMsgSC yes = BridgeFarSC.answer(friend, id, true);
            int ring2 = vortexCount(w, X - 2, X + 2, Y + 1, Y + 5, Z, Z), nearF = vortexCount(w, PX - 1, PX + 1, Y + 1, Y + 3, FZ + 3, FZ + 3);
            say(notYours && c.isOpen() && ring2 == 9 && nearF == 9 && BridgeConsentSC.server().state(id) == BridgeConsentSC.USED,
                    "bridge 2: a stranger can't answer for the friend; the friend's «Принять» opens next to him (" + nearF + ") and in the ring (" + ring2 + ")"
                            + (yes == null ? "" : " - " + yes.key));
            c.closeFrom(owner);
        }
        if (ticks == 96) {
            TileEntityBridgeControllerSC c = ctrl(w);
            if (c != null) {
                c.setRemoteMode(false);
            }
            for (int x = X - 3; x <= X + 3; x++) {
                for (int y = Y - 1; y <= Y + 6; y++) {
                    for (int z = Z - 1; z <= Z + 1; z++) {
                        w.setBlockToAir(x, y, z);
                    }
                }
            }
            for (int x = PX - 4; x <= PX + 4; x++) {
                for (int z = PZ - 5; z <= FZ + 6; z++) {
                    for (int y = Y; y <= Y + 6; y++) {
                        w.setBlockToAir(x, y, z);
                    }
                }
            }
            TileEntityBridgeControllerSC.TEST_PLAYERS.clear();
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
        for (int x = PX - 4; x <= PX + 4; x++) {
            for (int z = PZ - 5; z <= FZ + 6; z++) {
                w.setBlock(x, Y, z, Blocks.stone);
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
