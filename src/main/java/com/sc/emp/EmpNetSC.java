package com.sc.emp;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * ПЭ-1 messages, on the armour channel (ArmorNetSC.CHANNEL, discriminators 7..10 - 0..6 are ArmorNetSC's own):
 * 7 ActionMessage client -> server (K menu, keys, the wheel), 8 StateMessage (a player's EmpStateSC),
 * 9 AimMessage (the shooter's target / siphon readout), 10 FxMessage (beams, the stream, sparks) server -> client.
 * The client handlers only store what came (common code, no client classes - safe on a dedicated server);
 * package C / D read it from here and from EmpSC.clientState.
 */
public final class EmpNetSC {

    public static final int DISC_ACTION = 7, DISC_STATE = 8, DISC_AIM = 9, DISC_FX = 10;

    /**
     * ActionMessage actions (a, b: the arguments). The server checks everything against the gun in hand
     * (EmpActionsSC.handle) - SELECT_MODE / CYCLE_MODE skip modes switched off in the K menu.
     */
    public static final byte SELECT_MODE = 0,      // a = EmpMode ordinal
            CYCLE_MODE = 1,                        // a = -1 / +1 (Shift + wheel)
            TOGGLE_MODE = 2,                       // a = EmpMode ordinal, b = 1 on / 0 off
            SET_ZONE = 3,                          // a = EmpMathSC.ZONE_*
            SET_RECEIVER_ORDER = 4,                // a = the order as three base-4 digits: r0 + 4 r1 + 16 r2 (EmpMathSC.RECV_*)
            TOGGLE_RECEIVER = 5,                   // a = EmpMathSC.RECV_*, b = 1 on / 0 off
            SET_LIMIT = 6,                         // a = 0 (until the receiver is full) or 1..100 (until my armour has a%)
            SET_WARN = 7,                          // b = 1 on / 0 off: the overheat warning 2 s before
            MODULE_INSTALL = 8,                    // a = EmpModule ordinal, b = main inventory slot holding it
            MODULE_REMOVE = 9,                     // a = EmpModule ordinal
            CANCEL_CHARGE = 10,                    // the charge-up dropped, no EU spent (its key; Shift + right-click is the item's own)
            EMERGENCY_REBOOT = 11;                 // the target's reboot (K menu button / key), needs no gun

    private EmpNetSC() {
    }

    public static SimpleNetworkWrapper channel() {
        return com.sc.handler.ArmorNetSC.CHANNEL;
    }

    /** Called by ArmorNetSC.init() after its own messages. */
    public static void register(SimpleNetworkWrapper ch) {
        ch.registerMessage(ActionHandler.class, ActionMessage.class, DISC_ACTION, Side.SERVER);
        ch.registerMessage(StateHandler.class, StateMessage.class, DISC_STATE, Side.CLIENT);
        ch.registerMessage(AimHandler.class, AimMessage.class, DISC_AIM, Side.CLIENT);
        ch.registerMessage(FxHandler.class, FxMessage.class, DISC_FX, Side.CLIENT);
    }

    /** Client: sends an action. */
    public static void send(byte action, int a, int b) {
        channel().sendToServer(new ActionMessage(action, a, b));
    }

    // ------------------------------------------------------------------ 7: client -> server

    public static class ActionMessage implements IMessage {
        public byte action;
        public int a, b;

        public ActionMessage() {
        }

        public ActionMessage(byte action, int a, int b) {
            this.action = action;
            this.a = a;
            this.b = b;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            action = buf.readByte();
            a = buf.readInt();
            b = buf.readInt();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeByte(action);
            buf.writeInt(a);
            buf.writeInt(b);
        }
    }

    /** Queued like ArmorNetSC's (BladeSingularSC.queueTask: capped per player, run on the next server tick). */
    public static class ActionHandler implements IMessageHandler<ActionMessage, IMessage> {
        @Override
        public IMessage onMessage(ActionMessage msg, MessageContext ctx) {
            final EntityPlayerMP p = ctx.getServerHandler().playerEntity;
            if (p == null || p.isDead || p.getHealth() <= 0) {
                return null;
            }
            final byte action = msg.action;
            final int a = msg.a, b = msg.b;
            com.sc.item.BladeSingularSC.queueTask(p, new Runnable() {
                @Override
                public void run() {
                    EmpActionsSC.handle(p, action, a, b);
                }
            });
            return null;
        }
    }

    // ------------------------------------------------------------------ 8: a player's state

    public static class StateMessage implements IMessage {
        public int entityId;
        public EmpStateSC state;

        public StateMessage() {
        }

        public StateMessage(int entityId, EmpStateSC state) {
            this.entityId = entityId;
            this.state = state;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            entityId = buf.readInt();
            state = EmpStateSC.read(ByteBufUtils.readTag(buf));
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(entityId);
            ByteBufUtils.writeTag(buf, state.write());
        }
    }

    public static class StateHandler implements IMessageHandler<StateMessage, IMessage> {
        @Override
        public IMessage onMessage(StateMessage msg, MessageContext ctx) {
            EmpSC.clientSet(msg.entityId, msg.state);
            return null;
        }
    }

    // ------------------------------------------------------------------ 9: the shooter's readout

    /** Refusal reasons (AimMessage.reason; EmpRulesSC.refusal's codes): the K menu's «Нельзя: ...». */
    public static final int OK = 0, NO_PVP = 1, NO_CLAIM = 2, NO_OWN = 3, NO_DISABLED = 4, NO_PLAYERS = 5;

    /**
     * Server -> the shooter, every EmpMathSC.AIM_SCAN_TICKS while a ПЭ-1 is in hand (package A sends it): what is in the
     * crosshair within range and the siphon's state. targetId 0: nothing aimed at.
     */
    public static class AimMessage implements IMessage {
        public int targetId;
        /** OK or a NO_* reason. */
        public int reason;
        /** ArmorSuit ordinal of the target's chestplate (or of any worn piece), -1 none / a mob. */
        public int suit = -1;
        /** Bits 0..3: suppressed slots; bits 4..7: immune slots. */
        public int slots;
        /** The longest suppression / immunity left, ticks. */
        public int suppressedTicks, immuneTicks;
        /** The target's charge, % - only with the shooter's Sensor chip, else -1. */
        public int chargePct = -1;
        /** Siphon: the receiver the energy would go to (EmpMathSC.RECV_*), -1 «Некуда принимать»; its free room, EU. */
        public int receiver = -1;
        public long receiverRoom;
        /** Siphon: the stream runs; EU received by this stream so far. */
        public boolean streaming;
        public long received;

        @Override
        public void fromBytes(ByteBuf buf) {
            targetId = buf.readInt();
            reason = buf.readByte();
            suit = buf.readByte();
            slots = buf.readUnsignedByte();
            suppressedTicks = buf.readInt();
            immuneTicks = buf.readInt();
            chargePct = buf.readByte();
            receiver = buf.readByte();
            receiverRoom = buf.readLong();
            streaming = buf.readBoolean();
            received = buf.readLong();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(targetId);
            buf.writeByte(reason);
            buf.writeByte(suit);
            buf.writeByte(slots);
            buf.writeInt(suppressedTicks);
            buf.writeInt(immuneTicks);
            buf.writeByte(chargePct);
            buf.writeByte(receiver);
            buf.writeLong(receiverRoom);
            buf.writeBoolean(streaming);
            buf.writeLong(received);
        }
    }

    /** Client: the last readout and when it came (System.currentTimeMillis; older than ~1 s = stale). */
    public static volatile AimMessage lastAim;
    public static volatile long lastAimAt;

    public static class AimHandler implements IMessageHandler<AimMessage, IMessage> {
        @Override
        public IMessage onMessage(AimMessage msg, MessageContext ctx) {
            lastAim = msg;
            lastAimAt = System.currentTimeMillis();
            return null;
        }
    }

    // ------------------------------------------------------------------ 10: effects

    /** FxMessage kinds. */
    public static final int FX_POINT = 0, FX_PULSE = 1, FX_STREAM_ON = 2, FX_STREAM_OFF = 3, FX_ABSORBED = 4, FX_SUPPRESSED = 5,
            FX_REFLECTED = 6;

    /**
     * Server -> everyone within EmpSC.SYNC_RANGE: a Point / Pulse beam from the shooter to (x, y, z) (the hit, the miss's end
     * or the EMP screen's border), the stream between shooter and target switched on / off, sparks on a suppressed target,
     * a reflected Pulse.
     */
    public static class FxMessage implements IMessage {
        public int kind, shooterId, targetId;
        public float x, y, z;

        public FxMessage() {
        }

        public FxMessage(int kind, int shooterId, int targetId, double x, double y, double z) {
            this.kind = kind;
            this.shooterId = shooterId;
            this.targetId = targetId;
            this.x = (float) x;
            this.y = (float) y;
            this.z = (float) z;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            kind = buf.readByte();
            shooterId = buf.readInt();
            targetId = buf.readInt();
            x = buf.readFloat();
            y = buf.readFloat();
            z = buf.readFloat();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeByte(kind);
            buf.writeInt(shooterId);
            buf.writeInt(targetId);
            buf.writeFloat(x);
            buf.writeFloat(y);
            buf.writeFloat(z);
        }
    }

    /** Client: effects waiting for the renderer (package D drains it on the client thread); capped. */
    public static final Queue<FxMessage> FX = new ConcurrentLinkedQueue<FxMessage>();
    private static final int FX_MAX = 256;

    public static class FxHandler implements IMessageHandler<FxMessage, IMessage> {
        @Override
        public IMessage onMessage(FxMessage msg, MessageContext ctx) {
            if (FX.size() < FX_MAX) {
                FX.add(msg);
            }
            return null;
        }
    }

    /** Server: an effect to everyone around (x, y, z). */
    public static void sendFx(net.minecraft.world.World w, FxMessage fx, double x, double y, double z) {
        if (w != null && !w.isRemote) {
            channel().sendToAllAround(fx, new cpw.mods.fml.common.network.NetworkRegistry.TargetPoint(w.provider.dimensionId, x, y, z,
                    EmpSC.SYNC_RANGE));
        }
    }
}
