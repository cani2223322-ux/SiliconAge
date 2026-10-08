package com.sc.handler;

import com.sc.item.ArmorLogicSC;
import com.sc.item.ItemArmorSC;
import com.sc.util.ArmorFeature;
import com.sc.util.ArmorGasSC;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

/**
 * Client -> server messages for the suits: switch one function of a worn piece on or off, step the
 * power mode, dash, take the chips out. The server checks everything against what the player actually wears.
 */
public final class ArmorNetSC {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("SiliconAgeArmor");

    public static final byte TOGGLE = 0, POWER_MODE = 1, DASH = 2, ANNIHILATE = 3;
    /** The energy blade in hand: switch one of its functions, or fire one of its key functions. */
    public static final byte BLADE_TOGGLE = 4, BLADE_SWEEP = 5, BLADE_WAVE = 6, BLADE_LUNGE = 7;
    /** Every chip out of the worn chestplate, back into the inventory. */
    public static final byte REMOVE_CHIPS = 8;
    /** The drill in hand: switch one of its functions, or fire its laser. */
    public static final byte DRILL_TOGGLE = 9, DRILL_LASER = 10;
    /** The light colour of every worn piece of the mod (feature = the colour index). */
    public static final byte GLOW_COLOR = 11;
    /** Life support: pour the gas container in the player's inventory slot `feature` into the worn suit (K screen). */
    public static final byte GAS_FILL = 12;
    /**
     * Life support: pour every suitable gas container of the main inventory into the worn suit - the
     * server walks the inventory itself (same rules as GAS_FILL, slot by slot) and answers with ONE chat line.
     */
    public static final byte GAS_FILL_ALL = 13;
    /** Singular leggings: the phase dash (ArmorLogicSC.phaseDash). */
    public static final byte PHASE_DASH = 14;
    /** The Singular key functions of stage 2b (SingularPowersSC.key); 20 is the air jump (ArmorLogicSC.AIR_JUMP_ACTION). */
    public static final byte GRAV_PRESS = 15, GRAV_GRAB = 16, TIME_SLOW = 17, BLACK_HOLE = 18, GRAV_DOME = 19, SINGULARITY = 21;
    /**
     * Singular levels (Р2): the free first branch choice of the worn chestplate, feature = level x 10 +
     * choice (31 / 32 at level 3, 51 / 52 at level 5); the server checks the level and that nothing is chosen.
     */
    public static final byte BRANCH = 22;
    /**
     * М4, the Singular function profiles (SingularProfiles, kept in the worn Singular chestplate):
     * pick profile `feature` (0..2; an empty one takes the current switches), save the current switches
     * into profile `feature` (-1: the active one), step to the next profile (the key).
     */
    public static final byte PROFILE_SELECT = 23, PROFILE_SAVE = 24, PROFILE_NEXT = 25;
    /**
     * The Singular tools (docs/plan-singular-tools.md): fire key function `feature` (a BladeFeature / DrillFeature
     * ordinal, isAction) of the blade / drill in hand - the old SWEEP / WAVE / LUNGE / LASER ids end there too;
     * one step of the Singular blade's form / the Singular drill's mode (feature = -1 / +1, Shift + wheel);
     * the free branch choice of the Singular tool in hand (value: the blade, else the drill; feature = the branch).
     */
    public static final byte BLADE_ACTION = 26, BLADE_FORM = 27, DRILL_ACTION = 28, DRILL_MODE = 29, TOOL_BRANCH = 30;

    /**
     * Pure: a BLADE_FORM message's signed steps - one from the wheel, several from the K menu's form buttons (С-1: sent
     * as one message, the server walks them in one go; one step a message fell to the server's wheel gap), at most a lap.
     */
    public static int formDelta(int feature) {
        int max = com.sc.util.BladeForm.values().length;
        return Math.max(-max, Math.min(max, feature));
    }

    /** Pure: one wheel / form step's direction, -1 or +1 (0 stays 0). */
    public static int step(int delta) {
        return delta < 0 ? -1 : delta > 0 ? 1 : 0;
    }

    /**
     * Pure: the single BLADE_FORM steps (signed: + forward) that take the blade from `from` to `to` through the
     * forms open at `level` / `chosen` (creative: all), the shorter way round; 0 when it's there or `to` is closed.
     */
    public static int formSteps(com.sc.util.BladeForm from, com.sc.util.BladeForm to, int level, int chosen, boolean creative) {
        if (from == null || to == null || from == to || !to.open(level, chosen, creative)) {
            return 0;
        }
        int n = com.sc.util.BladeForm.values().length;
        int fwd = 0, back = 0;
        com.sc.util.BladeForm f = from;
        for (int i = 1; i <= n && fwd == 0; i++) {
            f = com.sc.util.BladeForm.cycle(f, 1, level, chosen, creative);
            if (f == to) {
                fwd = i;
            }
        }
        f = from;
        for (int i = 1; i <= n && back == 0; i++) {
            f = com.sc.util.BladeForm.cycle(f, -1, level, chosen, creative);
            if (f == to) {
                back = i;
            }
        }
        if (fwd == 0 || back == 0) {
            return fwd != 0 ? fwd : -back;
        }
        return fwd <= back ? fwd : -back;
    }

    /** BRANCH's feature byte for a level (3 / 5) and a choice (1 / 2). */
    public static int branchFeature(int level, int choice) {
        return level * 10 + choice;
    }

    /** The action byte of a stage 2b key function, -1 for any other function. */
    public static byte actionOf(ArmorFeature f) {
        if (f == null) {
            return -1;
        }
        switch (f) {
            case GRAV_PRESS: return GRAV_PRESS;
            case GRAV_GRAB: return GRAV_GRAB;
            case TIME_SLOW: return TIME_SLOW;
            case BLACK_HOLE: return BLACK_HOLE;
            case GRAV_DOME: return GRAV_DOME;
            case SINGULARITY: return SINGULARITY;
            default: return -1;
        }
    }

    /** The stage 2b key function of an action byte, or null. */
    public static ArmorFeature featureOfAction(byte action) {
        for (ArmorFeature f : ArmorFeature.values()) {
            if (actionOf(f) == action && action >= 0) {
                return f;
            }
        }
        return null;
    }
    /** GAS_FILL_ALL: at most this many single pours per press, and the press at most once per this many ticks. */
    private static final int FILL_ALL_MAX = 64, FILL_ALL_COOLDOWN = 10;
    private static final String FILL_ALL_TIME_TAG = "ScGasFillAllT";

    private ArmorNetSC() {
    }

    public static void init() {
        CHANNEL.registerMessage(Handler.class, Message.class, 0, Side.SERVER);
        CHANNEL.registerMessage(CooldownHandler.class, CooldownMessage.class, 1, Side.CLIENT);
        CHANNEL.registerMessage(ScanHandler.class, ScanMessage.class, 2, Side.CLIENT);
        CHANNEL.registerMessage(ThreatHandler.class, ThreatMessage.class, 3, Side.CLIENT);
        CHANNEL.registerMessage(AnalyzeHandler.class, AnalyzeMessage.class, 4, Side.CLIENT);
        CHANNEL.registerMessage(LevelHandler.class, LevelMessage.class, 5, Side.CLIENT);
    }

    // ------------------------------------------------------------------ server -> client: the Singular helmet's senses (stage 2b)
    // The handlers only store what came in SingularSenseData (common code) - no client classes, safe on a server.

    /** Most entries a sense message is read with (a broken packet can't make the client allocate much). */
    private static final int SENSE_MAX = 1024;

    /** Ш1: the scanner's pulse - blocks (x, y, z, kind) and mobs (entity id, hostile). */
    public static class ScanMessage implements IMessage {
        public int[] blocks = new int[0], mobs = new int[0];

        public ScanMessage() {
        }

        public ScanMessage(int[] blocks, int[] mobs) {
            this.blocks = blocks;
            this.mobs = mobs;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            int n = Math.min(SENSE_MAX, buf.readUnsignedShort());
            blocks = new int[n * 4];
            for (int i = 0; i < n; i++) {
                blocks[i * 4] = buf.readInt();
                blocks[i * 4 + 1] = buf.readUnsignedByte();
                blocks[i * 4 + 2] = buf.readInt();
                blocks[i * 4 + 3] = buf.readByte();
            }
            int m = Math.min(SENSE_MAX, buf.readUnsignedShort());
            mobs = new int[m * 2];
            for (int i = 0; i < m; i++) {
                mobs[i * 2] = buf.readInt();
                mobs[i * 2 + 1] = buf.readByte();
            }
        }

        @Override
        public void toBytes(ByteBuf buf) {
            int n = Math.min(SENSE_MAX, blocks.length / 4);
            buf.writeShort(n);
            for (int i = 0; i < n; i++) {
                buf.writeInt(blocks[i * 4]);
                buf.writeByte(blocks[i * 4 + 1]);
                buf.writeInt(blocks[i * 4 + 2]);
                buf.writeByte(blocks[i * 4 + 3]);
            }
            int m = Math.min(SENSE_MAX, mobs.length / 2);
            buf.writeShort(m);
            for (int i = 0; i < m; i++) {
                buf.writeInt(mobs[i * 2]);
                buf.writeByte(mobs[i * 2 + 1]);
            }
        }
    }

    public static class ScanHandler implements IMessageHandler<ScanMessage, IMessage> {
        @Override
        public IMessage onMessage(ScanMessage msg, MessageContext ctx) {
            com.sc.util.SingularSenseData.setScan(msg.blocks, msg.mobs);
            return null;
        }
    }

    /** Ш2: the entity ids of the mobs whose target is the player. */
    public static class ThreatMessage implements IMessage {
        public int[] ids = new int[0];

        public ThreatMessage() {
        }

        public ThreatMessage(int[] ids) {
            this.ids = ids;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            int n = Math.min(SENSE_MAX, buf.readUnsignedShort());
            ids = new int[n];
            for (int i = 0; i < n; i++) {
                ids[i] = buf.readInt();
            }
        }

        @Override
        public void toBytes(ByteBuf buf) {
            int n = Math.min(SENSE_MAX, ids.length);
            buf.writeShort(n);
            for (int i = 0; i < n; i++) {
                buf.writeInt(ids[i]);
            }
        }
    }

    public static class ThreatHandler implements IMessageHandler<ThreatMessage, IMessage> {
        @Override
        public IMessage onMessage(ThreatMessage msg, MessageContext ctx) {
            com.sc.util.SingularSenseData.setThreats(msg.ids);
            return null;
        }
    }

    /** Ш5: the analyzer's table of the mob / machine looked at (kind NONE: nothing). */
    public static class AnalyzeMessage implements IMessage {
        public com.sc.util.SingularSenseData.Analysis a = new com.sc.util.SingularSenseData.Analysis();

        public AnalyzeMessage() {
        }

        public AnalyzeMessage(com.sc.util.SingularSenseData.Analysis a) {
            this.a = a;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            a = new com.sc.util.SingularSenseData.Analysis();
            a.kind = buf.readByte();
            if (a.kind == com.sc.util.SingularSenseData.Analysis.MOB) {
                a.entityId = buf.readInt();
                a.health = buf.readFloat();
                a.maxHealth = buf.readFloat();
                a.armor = buf.readShort();
                a.attack = buf.readFloat();
                a.flags = buf.readInt();
            } else if (a.kind == com.sc.util.SingularSenseData.Analysis.MACHINE) {
                a.x = buf.readInt();
                a.y = buf.readUnsignedByte();
                a.z = buf.readInt();
                a.stored = buf.readInt();
                a.capacity = buf.readInt();
                a.output = buf.readInt();
                a.status = buf.readByte();
                a.progress = buf.readByte();
                a.powerOn = buf.readBoolean();
            }
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeByte(a.kind);
            if (a.kind == com.sc.util.SingularSenseData.Analysis.MOB) {
                buf.writeInt(a.entityId);
                buf.writeFloat(a.health);
                buf.writeFloat(a.maxHealth);
                buf.writeShort(a.armor);
                buf.writeFloat(a.attack);
                buf.writeInt(a.flags);
            } else if (a.kind == com.sc.util.SingularSenseData.Analysis.MACHINE) {
                buf.writeInt(a.x);
                buf.writeByte(a.y);
                buf.writeInt(a.z);
                buf.writeInt(a.stored);
                buf.writeInt(a.capacity);
                buf.writeInt(a.output);
                buf.writeByte(a.status);
                buf.writeByte(a.progress);
                buf.writeBoolean(a.powerOn);
            }
        }
    }

    public static class AnalyzeHandler implements IMessageHandler<AnalyzeMessage, IMessage> {
        @Override
        public IMessage onMessage(AnalyzeMessage msg, MessageContext ctx) {
            com.sc.util.SingularSenseData.setAnalysis(msg.a);
            return null;
        }
    }

    /**
     * Server -> client: a function's cooldown ends at world tick `end` (0: none); feature -1 clears them
     * all (login). The client keeps it in SingularCooldowns (no client classes here: safe on a server).
     */
    public static class CooldownMessage implements IMessage {
        public int feature;
        public long end;

        public CooldownMessage() {
        }

        public CooldownMessage(int feature, long end) {
            this.feature = feature;
            this.end = end;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            feature = buf.readByte();
            end = buf.readLong();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeByte(feature);
            buf.writeLong(end);
        }
    }

    /**
     * Server -> client: the Singular level task counters ((target-2) x 3 + task, SingularLevel.taskValues)
     * and how many biomes / dimensions were explored; kept in SingularLevel for the tooltip and the K menu.
     */
    public static class LevelMessage implements IMessage {
        public int[] tasks = new int[0];
        public int biomes, dims;

        public LevelMessage() {
        }

        public LevelMessage(int[] tasks, int biomes, int dims) {
            this.tasks = tasks;
            this.biomes = biomes;
            this.dims = dims;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            int n = Math.max(0, Math.min(64, buf.readUnsignedByte()));
            tasks = new int[n];
            for (int i = 0; i < n; i++) {
                tasks[i] = buf.readInt();
            }
            biomes = buf.readInt();
            dims = buf.readInt();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            int n = Math.min(64, tasks.length);
            buf.writeByte(n);
            for (int i = 0; i < n; i++) {
                buf.writeInt(tasks[i]);
            }
            buf.writeInt(biomes);
            buf.writeInt(dims);
        }
    }

    public static class LevelHandler implements IMessageHandler<LevelMessage, IMessage> {
        @Override
        public IMessage onMessage(LevelMessage msg, MessageContext ctx) {
            com.sc.util.SingularLevel.clientSet(msg.tasks, msg.biomes, msg.dims);
            return null;
        }
    }

    public static class CooldownHandler implements IMessageHandler<CooldownMessage, IMessage> {
        @Override
        public IMessage onMessage(CooldownMessage msg, MessageContext ctx) {
            com.sc.util.SingularCooldowns.clientSet(msg.feature, msg.end);
            return null;
        }
    }

    public static class Message implements IMessage {
        public byte action;
        public byte feature;
        /** TOGGLE: the state wanted (not "flip" - two quick clicks would fight the server's reply). */
        public boolean value;

        public Message() {
        }

        public Message(byte action, int feature) {
            this(action, feature, false);
        }

        public Message(byte action, int feature, boolean value) {
            this.action = action;
            this.feature = (byte) feature;
            this.value = value;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            action = buf.readByte();
            feature = buf.readByte();
            value = buf.readBoolean();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeByte(action);
            buf.writeByte(feature);
            buf.writeBoolean(value);
        }
    }

    public static class Handler implements IMessageHandler<Message, IMessage> {
        @Override
        public IMessage onMessage(Message msg, MessageContext ctx) {
            final EntityPlayerMP p = ctx.getServerHandler().playerEntity;
            if (p == null || p.isDead || p.getHealth() <= 0) {
                return null;                               // on the death screen: nothing to act with
            }
            switch (msg.action) {
                case TOGGLE: case POWER_MODE: case DASH: case ANNIHILATE: case REMOVE_CHIPS: case GLOW_COLOR: case GAS_FILL:
                case GAS_FILL_ALL: case com.sc.item.ArmorLogicSC.AIR_JUMP_ACTION: case PHASE_DASH: case BRANCH:
                case PROFILE_SELECT: case PROFILE_SAVE: case PROFILE_NEXT: {
                    final byte action = msg.action, feature = msg.feature;
                    final boolean value = msg.value;
                    com.sc.item.BladeSingularSC.queueTask(p, new Runnable() {    // the world, entities, NBT: on the server thread
                        @Override
                        public void run() {
                            armorAction(p, action, feature, value);
                        }
                    });
                    break;
                }
                case BLADE_TOGGLE: {
                    final com.sc.util.BladeFeature f = com.sc.util.BladeFeature.of(msg.feature);
                    final boolean on = msg.value;
                    if (f != null) {
                        com.sc.item.BladeSingularSC.queueTask(p, new Runnable() {    // the tool's NBT: on the server thread
                            @Override
                            public void run() {
                                ItemStack blade = com.sc.item.BladeLogicSC.held(p);
                                if (blade != null) {
                                    com.sc.item.BladeLogicSC.toggle(p, blade, f, on);
                                }
                            }
                        });
                    }
                    break;
                }
                case BLADE_SWEEP:                           // the old ids: the same generic path as BLADE_ACTION
                    bladeAction(p, com.sc.util.BladeFeature.SWEEP);
                    break;
                case BLADE_WAVE:
                    bladeAction(p, com.sc.util.BladeFeature.WAVE);
                    break;
                case BLADE_LUNGE:
                    bladeAction(p, com.sc.util.BladeFeature.LUNGE);
                    break;
                case BLADE_ACTION:
                    bladeAction(p, com.sc.util.BladeFeature.of(msg.feature));
                    break;
                case BLADE_FORM:
                    if (com.sc.util.ToolLevelSC.isBlade(com.sc.item.BladeLogicSC.held(p)) && formDelta(msg.feature) != 0) {
                        com.sc.item.BladeLogicSC.cycleForm(p, formDelta(msg.feature));
                    }
                    break;
                case DRILL_ACTION:
                    drillAction(p, com.sc.util.DrillFeature.of(msg.feature));
                    break;
                case DRILL_MODE:
                    if (com.sc.util.ToolLevelSC.isDrill(com.sc.item.DrillLogicSC.held(p)) && step(msg.feature) != 0) {
                        final int delta = step(msg.feature);
                        com.sc.item.BladeSingularSC.queueTask(p, new Runnable() {    // the world / NBT: on the server thread
                            @Override
                            public void run() {
                                com.sc.item.DrillLogicSC.cycleMode(p, delta);
                            }
                        });
                    }
                    break;
                case TOOL_BRANCH: {                         // the free first choice; only the station changes it later
                    final boolean blade = msg.value;
                    final int branch = msg.feature;
                    com.sc.item.BladeSingularSC.queueTask(p, new Runnable() {
                        @Override
                        public void run() {
                            toolBranch(p, blade, branch);
                        }
                    });
                    break;
                }
                case DRILL_TOGGLE: {
                    final com.sc.util.DrillFeature f = com.sc.util.DrillFeature.of(msg.feature);
                    final boolean on = msg.value;
                    if (f != null) {
                        com.sc.item.BladeSingularSC.queueTask(p, new Runnable() {
                            @Override
                            public void run() {
                                ItemStack drill = com.sc.item.DrillLogicSC.held(p);
                                if (drill != null) {
                                    com.sc.item.DrillLogicSC.toggle(p, drill, f, on);
                                }
                            }
                        });
                    }
                    break;
                }
                case DRILL_LASER:                           // the old id: the same generic path as DRILL_ACTION
                    drillAction(p, com.sc.util.DrillFeature.LASER);
                    break;
                case GRAV_PRESS: case GRAV_GRAB: case TIME_SLOW: case BLACK_HOLE: case GRAV_DOME: case SINGULARITY:
                    com.sc.item.SingularPowersSC.key(p, featureOfAction(msg.action));   // queued: run on the server thread
                    break;
                default:
                    break;
            }
            return null;
        }
    }

    /** Server thread (queued by the handler): the suit's own actions - they touch the world, entities and NBT. */
    private static void armorAction(EntityPlayerMP p, byte action, byte feature, boolean value) {
        switch (action) {
            case TOGGLE: {
                ArmorFeature f = ArmorFeature.of(feature);
                ItemStack piece = f == null ? null : ArmorLogicSC.piece(p, f.piece);
                if (piece != null && f.availableIn(ArmorLogicSC.suitOf(piece), f.piece)
                        && (!value || com.sc.util.SingularLevel.unlocked(p, f, piece))) {    // a locked function can't be switched on
                    ItemArmorSC.setEnabled(piece, f, value);
                    p.inventoryContainer.detectAndSendChanges();
                }
                break;
            }
            case POWER_MODE: {                        // feature: the mode wanted (0..2), anything else - the next one
                ItemStack chest = ArmorLogicSC.piece(p, 1);
                if (chest != null) {
                    int want = feature >= 0 && feature <= 2 ? feature : (ItemArmorSC.powerMode(chest) + 1) % 3;
                    ItemArmorSC.setPowerMode(chest, want);
                    p.inventoryContainer.detectAndSendChanges();
                }
                break;
            }
            case DASH:
                ArmorLogicSC.dash(p);
                break;
            case ANNIHILATE:
                ArmorLogicSC.annihilate(p);
                break;
            case REMOVE_CHIPS:
                com.sc.item.ItemArmorChipSC.removeAll(p);
                break;
            case GLOW_COLOR: {
                for (int i = 0; i < 4; i++) {
                    ItemStack s = ArmorLogicSC.piece(p, i);
                    if (s != null) {
                        ItemArmorSC.setGlowColor(s, feature);
                    }
                }
                p.inventoryContainer.detectAndSendChanges();
                break;
            }
            case GAS_FILL:
                fillSuit(p, feature);
                break;
            case GAS_FILL_ALL:
                fillSuitAll(p);
                break;
            case com.sc.item.ArmorLogicSC.AIR_JUMP_ACTION:   // hydrogen: a second jump in mid-air
                com.sc.item.ArmorLogicSC.airJump(p);
                break;
            case PHASE_DASH:
                com.sc.item.ArmorLogicSC.phaseDash(p);
                break;
            case BRANCH:                                // Р2: the free first choice; only the station changes it later
                if (com.sc.util.SingularLevel.chooseFree(p, feature / 10, feature % 10)) {
                    p.inventoryContainer.detectAndSendChanges();
                    p.addChatComponentMessage(new ChatComponentTranslation("sc.armor.sing.branch.chosen",
                            new ChatComponentTranslation("sc.armorfn." + com.sc.util.SingularLevel.branchFeature(feature / 10, feature % 10)
                                    .name().toLowerCase(java.util.Locale.ROOT))));
                }
                break;
            case PROFILE_SELECT:
                com.sc.util.SingularProfiles.serverAction(p, 0, feature);
                break;
            case PROFILE_SAVE:
                com.sc.util.SingularProfiles.serverAction(p, 1, feature);
                break;
            case PROFILE_NEXT:
                com.sc.util.SingularProfiles.serverAction(p, 2, -1);
                break;
            default:
                break;
        }
    }

    /** A blade key function: a blade in hand that has it, a key function, open at the blade's level / branch. */
    private static void bladeAction(EntityPlayerMP p, com.sc.util.BladeFeature f) {
        ItemStack blade = com.sc.item.BladeLogicSC.held(p);
        if (f != null && f.isAction() && blade != null && f.availableIn(com.sc.item.ItemBladeSC.typeOf(blade))
                && com.sc.item.BladeLogicSC.unlocked(p, blade, f)) {
            com.sc.item.BladeLogicSC.action(p, f);
        }
    }

    /** A drill key function: the same checks - queued, run on the server thread (it digs). */
    private static void drillAction(final EntityPlayerMP p, final com.sc.util.DrillFeature f) {
        if (f == null || !f.isAction()) {
            return;
        }
        com.sc.item.BladeSingularSC.queueTask(p, new Runnable() {
            @Override
            public void run() {
                ItemStack drill = com.sc.item.DrillLogicSC.held(p);
                if (drill != null && f.availableIn(com.sc.item.ItemDrillSC.typeOf(drill)) && com.sc.item.DrillLogicSC.unlocked(p, drill, f)) {
                    com.sc.item.DrillLogicSC.action(p, f);
                }
            }
        });
    }

    /** Server thread: the free first branch choice of the Singular tool in hand (`blade`: the blade, else the drill). */
    private static void toolBranch(EntityPlayerMP p, boolean blade, int branch) {
        ItemStack tool = blade ? com.sc.item.BladeLogicSC.held(p) : com.sc.item.DrillLogicSC.held(p);
        boolean kind = blade ? com.sc.util.ToolLevelSC.isBlade(tool) : com.sc.util.ToolLevelSC.isDrill(tool);
        if (kind && com.sc.util.ToolLevelSC.chooseFree(p, tool, branch)) {
            p.inventoryContainer.detectAndSendChanges();
            p.addChatComponentMessage(new ChatComponentTranslation("sc.toolgui.branch.chosen",
                    new ChatComponentTranslation(tool.getItem().getUnlocalizedName(tool) + ".name"),
                    new ChatComponentTranslation(com.sc.util.ToolLevelSC.branchLangKey(tool, branch))));
        }
    }

    /**
     * Pours the gas container in main-inventory slot `slot` into the worn suit's tanks. A container
     * that only empties whole (a bucket, a cell from the FluidContainerRegistry) goes in only if all of
     * it fits - otherwise nothing is spent; a tank-in-an-item (IFluidContainerItem) gives what fits and
     * keeps the rest. The empty container comes back to the inventory (or drops at the feet).
     * Creative players keep their containers full, as with a machine.
     */
    public static void fillSuit(EntityPlayerMP p, int slot) {
        if (pour(p, slot, null)) {
            p.worldObj.playSoundAtEntity(p, "random.fizz", 0.3F, 1.6F);
            p.inventory.markDirty();
            p.inventoryContainer.detectAndSendChanges();
        }
    }

    /**
     * "Fill everything": every slot of the main inventory in order, each poured exactly as a single
     * GAS_FILL would (a stack of whole-only containers - one by one while whole ones fit). No chat line
     * per container: one summary at the end ("Suit filled: Helium 2000 mB, Hydrogen 1000 mB" / nothing to fill).
     */
    public static void fillSuitAll(EntityPlayerMP p) {
        long now = p.worldObj.getTotalWorldTime();
        net.minecraft.nbt.NBTTagCompound data = p.getEntityData();
        long last = data.getLong(FILL_ALL_TIME_TAG);
        if (last > 0 && now >= last && now - last < FILL_ALL_COOLDOWN) {
            return;                                             // a flood of presses: one per half a second
        }
        data.setLong(FILL_ALL_TIME_TAG, now);
        int[] got = new int[ArmorGasSC.Gas.values().length];
        int pours = 0;
        for (int slot = 0; slot < p.inventory.mainInventory.length && pours < FILL_ALL_MAX; slot++) {
            while (pours < FILL_ALL_MAX && pour(p, slot, got)) {   // until the slot has nothing more that fits
                pours++;
            }
        }
        if (pours > 0) {
            p.worldObj.playSoundAtEntity(p, "random.fizz", 0.3F, 1.6F);
            p.inventory.markDirty();
            p.inventoryContainer.detectAndSendChanges();
        }
        ChatComponentText list = new ChatComponentText("");
        boolean any = false;
        for (ArmorGasSC.Gas g : ArmorGasSC.Gas.values()) {
            if (got[g.ordinal()] <= 0) {
                continue;
            }
            if (any) {
                list.appendText(", ");
            }
            list.appendSibling(new ChatComponentTranslation("sc.chat.gas.fillall.part", new ChatComponentTranslation("sc.gas." + g.key()),
                    String.valueOf(got[g.ordinal()])));
            any = true;
        }
        p.addChatComponentMessage(any ? new ChatComponentTranslation("sc.chat.gas.fillall", list)
                : new ChatComponentTranslation("sc.chat.gas.fillall.none"));
    }

    /**
     * One pour from main-inventory slot `slot` (the rules above). `got` null: a single fill - every
     * outcome gets its chat line; otherwise silent, the poured mB added to got[gas ordinal].
     * @return true when gas went into the suit
     */
    private static boolean pour(EntityPlayerMP p, int slot, int[] got) {
        if (slot < 0 || slot >= p.inventory.mainInventory.length) {
            return false;
        }
        ItemStack stack = p.inventory.mainInventory[slot];
        if (stack == null || stack.stackSize <= 0) {
            return false;
        }
        boolean creative = p.capabilities.isCreativeMode;
        if (FluidContainerRegistry.isFilledContainer(stack)) {
            FluidStack in = FluidContainerRegistry.getFluidForFilledItem(stack);
            ArmorGasSC.Gas g = in == null ? null : ArmorGasSC.Gas.of(in.getFluid());
            if (g == null || in.amount <= 0) {
                return false;                                   // not one of the suit's gases
            }
            if (ArmorGasSC.suitCapacity(p, g) <= 0) {
                if (got == null) {
                    tell(p, "sc.chat.gas.notank", g, in.amount);
                }
                return false;
            }
            if (ArmorGasSC.suitFill(p, g, in.amount, true) != in.amount) {
                if (got == null) {
                    tell(p, "sc.chat.gas.nofit", g, in.amount);   // a whole-only container: nothing spent
                }
                return false;
            }
            ArmorGasSC.suitFill(p, g, in.amount, false);
            if (!creative) {
                ItemStack empty = FluidContainerRegistry.drainFluidContainer(stack);
                if (empty == null && stack.getItem().hasContainerItem(stack)) {
                    empty = stack.getItem().getContainerItem(stack);
                }
                if (stack.stackSize <= 1) {
                    p.inventory.setInventorySlotContents(slot, empty);
                } else {
                    stack.stackSize--;
                    if (empty != null && !p.inventory.addItemStackToInventory(empty)) {
                        p.dropPlayerItemWithRandomChoice(empty, false);
                    }
                }
            }
            report(got, p, g, in.amount);
        } else if (stack.getItem() instanceof IFluidContainerItem && stack.stackSize == 1) {
            IFluidContainerItem item = (IFluidContainerItem) stack.getItem();
            FluidStack carried = item.getFluid(stack);
            ArmorGasSC.Gas g = carried == null || carried.amount <= 0 ? null : ArmorGasSC.Gas.of(carried.getFluid());
            if (g == null) {
                return false;
            }
            if (ArmorGasSC.suitCapacity(p, g) <= 0) {
                if (got == null) {
                    tell(p, "sc.chat.gas.notank", g, carried.amount);
                }
                return false;
            }
            int room = ArmorGasSC.suitFill(p, g, carried.amount, true);
            if (room <= 0) {
                if (got == null) {
                    tell(p, "sc.chat.gas.full", g, 0);
                }
                return false;
            }
            FluidStack taken = item.drain(stack, room, false);      // what the container really gives
            int put = taken == null ? 0 : Math.min(room, taken.amount);
            if (put <= 0) {
                return false;
            }
            if (!creative) {
                FluidStack drained = item.drain(stack, put, true);
                put = drained == null ? 0 : Math.min(put, drained.amount);
            }
            ArmorGasSC.suitFill(p, g, put, false);
            if (put <= 0) {
                return false;                                   // the container gave nothing after all
            }
            report(got, p, g, put);
        } else {
            return false;
        }
        return true;
    }

    private static void report(int[] got, EntityPlayerMP p, ArmorGasSC.Gas g, int mb) {
        if (got == null) {
            tell(p, "sc.chat.gas.in", g, mb);
        } else {
            got[g.ordinal()] += mb;
        }
    }

    private static void tell(EntityPlayerMP p, String key, ArmorGasSC.Gas g, int mb) {
        p.addChatComponentMessage(new ChatComponentTranslation(key, mb, new ChatComponentTranslation("sc.gas." + g.key())));
    }
}
