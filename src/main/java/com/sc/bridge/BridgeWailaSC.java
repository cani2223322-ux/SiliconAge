package com.sc.bridge;

import java.util.ArrayList;
import java.util.List;

import com.sc.tileentity.IBridgePartSC;
import com.sc.tileentity.TileEntityBridgeCapacitorSC;
import com.sc.tileentity.TileEntityBridgeControllerSC;
import com.sc.tileentity.TileEntityBridgeEnergyPortSC;
import com.sc.tileentity.TileEntityBridgeVortexSC;

import net.minecraft.tileentity.TileEntity;

/**
 * WAILA lines of the bridge's blocks (server side, as messages the client translates): the controller's
 * state (ready / open with seconds left / cooling / what's missing), the capacitors' charge, a part's link,
 * a vortex cell's seconds left. Kept out of WailaSC so it loads nothing of WAILA's.
 */
public final class BridgeWailaSC {

    private BridgeWailaSC() {
    }

    private static String m(long eu) {
        return BridgeMathSC.group(eu);
    }

    /** null: not a bridge block. All lines, the far end included (the server itself / tests). */
    public static List<BridgeMsgSC> lines(TileEntity te) {
        return lines(te, null);
    }

    /**
     * null: not a bridge block. К-1: an open controller's far end (coordinates) only for a viewer with access
     * (controller.allowed: owner, friends, public / ownerless; null viewer - the server itself), like the wireless.
     */
    public static List<BridgeMsgSC> lines(TileEntity te, net.minecraft.entity.player.EntityPlayer viewer) {
        List<BridgeMsgSC> l = new ArrayList<BridgeMsgSC>();
        if (te instanceof TileEntityBridgeControllerSC) {
            TileEntityBridgeControllerSC c = (TileEntityBridgeControllerSC) te;
            BridgeStructureSC.Scan s = c.getScan();
            if (s == null || !s.found) {
                l.add(new BridgeMsgSC("sc.waila.bridge.noring"));
                return l;
            }
            l.add(new BridgeMsgSC(s.kind == BridgeMathSC.SPACE ? "sc.waila.bridge.space" : "sc.waila.bridge.ground", s.coils, s.coilsNeeded));
            if (c.isOpen()) {
                int[] b = c.getEndB();
                if (viewer == null || c.allowed(viewer)) {
                    l.add(new BridgeMsgSC("sc.waila.bridge.open", (c.getLifeLeft() + 19) / 20, b[1], b[2], b[3]));
                } else {
                    l.add(new BridgeMsgSC("sc.waila.bridge.open.hidden", (c.getLifeLeft() + 19) / 20));
                }
            } else if (!s.valid) {
                l.add(new BridgeMsgSC("sc.waila.bridge.problems", s.problems.size()));
            } else if (!c.isCalibrated()) {
                l.add(new BridgeMsgSC("sc.waila.bridge.calib"));
            } else if (c.getCoolTicks() > 0) {
                l.add(new BridgeMsgSC("sc.waila.bridge.cooling", (c.getCoolTicks() + 19) / 20));
            } else {
                l.add(new BridgeMsgSC(c.isPowerOn() ? "sc.waila.bridge.ready" : "sc.waila.bridge.off"));
            }
            l.add(new BridgeMsgSC("sc.waila.bridge.caps", s.capacitors.size(), m(c.capacitorEnergy()), m(c.capacitorMax())));
            return l;
        }
        if (te instanceof TileEntityBridgeCapacitorSC) {
            l.add(new BridgeMsgSC("sc.waila.bridge.charge", m(((TileEntityBridgeCapacitorSC) te).getEnergy()), m(BridgeMathSC.CAPACITOR_EU)));
        }
        if (te instanceof TileEntityBridgeVortexSC) {
            TileEntityBridgeControllerSC c = ((TileEntityBridgeVortexSC) te).controller();
            if (c != null) {
                l.add(new BridgeMsgSC("sc.waila.bridge.vortex", (c.getLifeLeft() + 19) / 20));
            }
            return l;
        }
        if (te instanceof IBridgePartSC) {
            int[] p = ((IBridgePartSC) te).controllerPos();
            l.add(p == null ? new BridgeMsgSC("sc.waila.bridge.unlinked") : new BridgeMsgSC("sc.waila.bridge.linked", p[0], p[1], p[2]));
            if (te instanceof TileEntityBridgeEnergyPortSC && !((TileEntityBridgeEnergyPortSC) te).isPowerOn()) {
                l.add(new BridgeMsgSC("sc.waila.bridge.portoff"));
            }
            return l;
        }
        return l.isEmpty() ? null : l;
    }
}
