package com.sc.tileentity;

import java.util.ArrayList;
import java.util.List;

import com.sc.energy.FieldMode;
import com.sc.energy.Tier;
import com.sc.energy.TileEntityEnergyBase;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

/**
 * Field Generator (§7/§16/step 9) - a cluster of these forms one shield. One node is elected
 * Master (the first one placed, or whichever survives a link) and holds the full node
 * coordinate list; every other node just points back at the master and otherwise does nothing
 * but exist as a "corner" the master's bounding box stretches to include.
 *
 * §16's Union/Box/Prism modes only differ here by EU/t cost multiplier - the design doc names
 * three shapes but never actually specifies how their PROTECTED REGIONS differ geometrically
 * (that's presumably in the "предыдущая переписка" this project doesn't have access to - see
 * 01_recipes.md's own repeated notes on that gap). All three protect the same axis-aligned
 * bounding box spanning every linked node, expanded by BUFFER blocks - a deliberate
 * simplification, not a guess at the missing geometry.
 *
 * activeFaces (§16's EU/t formula: base x activeFaces x mode_multiplier) is approximated as
 * 6 per linked node (a cluster "contributes" a virtual cube's worth of surface per node) -
 * another TODO-by-analogy number, since the doc doesn't define what "face" means for an
 * arbitrary node graph either.
 */
public class TileEntityFieldGeneratorSC extends TileEntityEnergyBase implements ISidedInventory {

    public static final int BUFFER_BLOCKS = 2;
    // §16: node cap by tier - Field Generator is fixed HV per its own recipe (§7: Tungsten Cable).
    public static final int MAX_NODES = 8;
    public static final int BASE_EU_PER_FACE = 2;
    // TODO(design doc): no link range is specified - 16 blocks per axis keeps a cluster to a
    // base-sized area (and stops a 10k-block "cluster" whose bounding box is scanned every tick).
    public static final int MAX_LINK_DISTANCE = 16;

    /**
     * Upgrade slots on the master's screen, as in a machine: energy storage upgrades (+10 000 EU of
     * buffer each) and transformer upgrades (one tier higher voltage each - HV -> EV).
     */
    public static final int UPGRADE_SLOTS = 4;
    private final ItemStack[] upgrades = new ItemStack[UPGRADE_SLOTS];

    private FieldMode mode = FieldMode.UNION;
    private boolean master = true;
    private final List<int[]> nodePositions = new ArrayList<int[]>(); // master only, includes self
    private int[] masterPos; // non-master only

    public TileEntityFieldGeneratorSC() {
        super(Tier.HV);
    }

    private boolean active;
    /** How far the field reaches past the nodes, in blocks (FieldShapeSC) - set on the master's screen. */
    private int range = FieldShapeSC.DEFAULT_RANGE;

    // ---- the master's settings - saved, and sent to clients with the block (renderer, screen) ----

    /** Switches, bits of "Flags". */
    public static final int F_NO_SPAWN = 1, F_NO_ENDER = 2, F_PRIVATE = 4, F_PUSH_PLAYERS = 8, F_DAMAGE = 16,
            F_WARN = 32, F_CHARGE = 64, F_HEAL = 128, F_SHOW = 256;
    /** A new field (and one from before the switches): mobs pushed and hurt, warnings on, shell shown. */
    public static final int DEFAULT_FLAGS = F_DAMAGE | F_WARN | F_SHOW;
    /** EU per tick each switched-on protection adds to the upkeep (TODO: not in the design doc). */
    public static final int NO_SPAWN_EU = 8, NO_ENDER_EU = 4, PRIVATE_EU = 16, PUSH_PLAYERS_EU = 8;
    /** Wireless charging: EU a second per player, from the master's buffer 1:1. Healing: EU per point healed. */
    public static final int CHARGE_PER_SECOND = 10240, HEAL_COST = 400;
    public static final int REDSTONE_ALWAYS = 0, REDSTONE_ON = 1, REDSTONE_OFF = 2;
    public static final int FILTER_HOSTILE = 0, FILTER_NEUTRAL = 1, FILTER_ALL = 2;
    public static final int MAX_ACCESS = 16;
    /** Shell colours: cyan, green, red, violet, gold. */
    public static final float[][] COLORS = {{0.35F, 0.9F, 1F}, {0.35F, 1F, 0.45F}, {1F, 0.3F, 0.3F}, {0.75F, 0.45F, 1F}, {1F, 0.8F, 0.3F}};

    private int flags = DEFAULT_FLAGS;
    private int redstone = REDSTONE_ALWAYS, filter = FILTER_HOSTILE, color;
    private String owner = "";
    private final List<String> access = new ArrayList<String>();
    /** Switched off by its redstone setting right now (shown on the screen). */
    private boolean redstoneOff;
    private boolean lowWarned;
    private long lastDownWarning = -10000;

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    public void toggle(int flag) {
        flags ^= flag;
        changed();
    }

    public int getRedstone() {
        return redstone;
    }

    public void cycleRedstone() {
        redstone = (redstone + 1) % 3;
        changed();
    }

    public int getFilter() {
        return filter;
    }

    public void cycleFilter() {
        filter = (filter + 1) % 3;
        changed();
    }

    public int getColor() {
        return color;
    }

    public void cycleColor() {
        color = (color + 1) % COLORS.length;
        changed();
    }

    public boolean isRedstoneOff() {
        return redstoneOff;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String name) {
        owner = name == null ? "" : name;
        changed();
    }

    public List<String> getAccess() {
        return access;
    }

    public boolean isOwner(EntityPlayer p) {
        return !owner.isEmpty() && owner.equalsIgnoreCase(p.getCommandSenderName());
    }

    /** The owner, anyone on the access list - and everyone while the field has no owner. */
    public boolean allowed(EntityPlayer p) {
        return owner.isEmpty() || isOwner(p) || access.contains(p.getCommandSenderName().toLowerCase(java.util.Locale.ROOT));
    }

    public boolean addAccess(String name) {
        String n = name == null ? "" : name.trim().toLowerCase(java.util.Locale.ROOT);
        if (n.isEmpty() || n.length() > 16 || access.contains(n) || access.size() >= MAX_ACCESS || n.equalsIgnoreCase(owner)) {
            return false;
        }
        access.add(n);
        changed();
        return true;
    }

    public boolean removeAccess(String name) {
        boolean removed = access.remove(name == null ? "" : name.toLowerCase(java.util.Locale.ROOT));
        if (removed) {
            changed();
        }
        return removed;
    }

    /** What the field pushes out (and keeps from spawning): by the filter; never villagers, golems or pets. */
    public boolean targets(Entity e) {
        if (!(e instanceof EntityLiving) || e instanceof net.minecraft.entity.INpc
                || e instanceof net.minecraft.entity.monster.EntityGolem) {
            return false;
        }
        if (e instanceof net.minecraft.entity.IEntityOwnable) {
            String tamer = ((net.minecraft.entity.IEntityOwnable) e).func_152113_b();
            if (tamer != null && !tamer.isEmpty()) {
                return false;                       // someone's pet
            }
        }
        if (e instanceof IMob) {
            return true;
        }
        if (filter >= FILTER_NEUTRAL && (e instanceof net.minecraft.entity.passive.EntityAmbientCreature
                || e instanceof net.minecraft.entity.passive.EntityWaterMob || e instanceof net.minecraft.entity.passive.EntityWolf)) {
            return true;
        }
        return filter == FILTER_ALL && !(e instanceof net.minecraft.entity.passive.EntityAnimal);
    }

    /** A new master (unlink) takes the old one's settings along with its shape. */
    private void copySettings(TileEntityFieldGeneratorSC from) {
        flags = from.flags;
        redstone = from.redstone;
        filter = from.filter;
        color = from.color;
        owner = from.owner;
        access.clear();
        access.addAll(from.access);
    }

    /** The first active field in the world with that switch on that covers the point, or null. */
    public static TileEntityFieldGeneratorSC fieldWith(World world, int flag, double x, double y, double z) {
        for (TileEntityFieldGeneratorSC f : activeFieldsIn(world)) {
            if (f.has(flag) && f.fieldContains(x, y, z)) {
                return f;
            }
        }
        return null;
    }

    public FieldMode getMode() {
        return mode;
    }

    public int getRange() {
        return range;
    }

    /** Buttons on the master's screen (ContainerFieldGeneratorSC.enchantItem). */
    public void adjustRange(int delta) {
        int next = FieldShapeSC.clampRange(range + delta);
        if (next != range) {
            range = next;
            changed();
        }
    }

    public void cycleMode() {
        mode = mode.next();
        changed();
    }

    public boolean isMaster() {
        return master;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return worldObj != null && worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
                && player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64;
    }

    public int getNodeCount() {
        if (worldObj != null && worldObj.isRemote) {
            return clientNodeCount;
        }
        return master ? nodePositions.size() : 1;
    }

    /** EU per tick for each block of range (TODO: not in the design doc). */
    public static final int RANGE_EU_PER_BLOCK = 2;

    /**
     * §16's EU/t formula, base x activeFaces x mode multiplier, plus a charge for the reach:
     * (BASE x 6 x nodes + RANGE_EU_PER_BLOCK x range) x multiplier. Also shown in the GUI.
     */
    public int upkeepPerTick() {
        return upkeepFor(getNodeCount(), range, mode) + extrasPerTick();
    }

    /** What the switched-on protections add to the upkeep (charging and healing are paid as used). */
    public int extrasPerTick() {
        return (has(F_NO_SPAWN) ? NO_SPAWN_EU : 0) + (has(F_NO_ENDER) ? NO_ENDER_EU : 0)
                + (has(F_PRIVATE) ? PRIVATE_EU : 0) + (has(F_PRIVATE) && has(F_PUSH_PLAYERS) ? PUSH_PLAYERS_EU : 0);
    }

    public static int upkeepFor(int nodes, int range, FieldMode mode) {
        return (int) Math.round((BASE_EU_PER_FACE * 6 * nodes + RANGE_EU_PER_BLOCK * range) * mode.costMultiplier);
    }

    // ---- client copies for the GUI, written by ContainerFieldGeneratorSC (nothing else syncs
    // mode/nodes/active - the screen showed UNION / 1 node / inactive whatever the real state) ----

    private int clientNodeCount = 1;

    public void setClientState(int modeOrdinal, int nodeCount, boolean isActive, int fieldRange) {
        FieldMode[] modes = FieldMode.values();
        mode = modes[Math.max(0, Math.min(modes.length - 1, modeOrdinal))];
        clientNodeCount = nodeCount;
        active = isActive;
        range = FieldShapeSC.clampRange(fieldRange);
    }

    @Override
    public void validate() {
        super.validate();
        if (master && nodePositions.isEmpty()) {
            nodePositions.add(new int[]{xCoord, yCoord, zCoord});
        }
    }

    /**
     * Called by ItemFieldLinkModule: merges the cluster of the node at `to` into the cluster of
     * the node at `from` (whole clusters, through their masters). All-or-nothing: either every
     * incoming node fits under MAX_NODES or nothing changes.
     *
     * The old version iterated the joining master's own node list while clearing it (a
     * ConcurrentModificationException - linking two fresh generators, i.e. two masters, always
     * crashed), and a node that was already a member of another cluster got added to the new
     * one without leaving the old, so it sat in two clusters at once.
     */
    public static LinkResult link(World world, int[] from, int[] to) {
        return link(world, from, to, null);
    }

    /** As link(world, from, to), checked for the player doing it: they must be allowed on both clusters. */
    public static LinkResult link(World world, int[] from, int[] to, EntityPlayer player) {
        if (!world.blockExists(from[0], from[1], from[2]) || !world.blockExists(to[0], to[1], to[2])) {
            return LinkResult.UNLOADED;         // never force-load a chunk just to link
        }
        TileEntityFieldGeneratorSC a = fieldGeneratorAt(world, from);
        TileEntityFieldGeneratorSC b = fieldGeneratorAt(world, to);
        if (a == null || b == null || a == b) {
            return LinkResult.INVALID;
        }
        if (Math.abs(from[0] - to[0]) > MAX_LINK_DISTANCE || Math.abs(from[1] - to[1]) > MAX_LINK_DISTANCE
                || Math.abs(from[2] - to[2]) > MAX_LINK_DISTANCE) {
            return LinkResult.TOO_FAR;
        }
        TileEntityFieldGeneratorSC masterTe = a.resolveMaster();
        TileEntityFieldGeneratorSC joiningMaster = b.resolveMaster();
        if (masterTe == null || joiningMaster == null) {
            return LinkResult.UNLOADED;
        }
        if (masterTe == joiningMaster) {
            return LinkResult.SAME_CLUSTER;
        }
        if (player != null && (!masterTe.allowed(player) || !joiningMaster.allowed(player))) {
            return LinkResult.NO_ACCESS;
        }
        List<int[]> incoming = new ArrayList<int[]>(joiningMaster.nodePositions);
        if (masterTe.nodePositions.size() + incoming.size() > MAX_NODES) {
            return LinkResult.NODE_CAP;
        }
        for (int[] pos : incoming) {            // all-or-nothing: every joining node must be reachable
            if (!world.blockExists(pos[0], pos[1], pos[2])) {
                return LinkResult.UNLOADED;
            }
        }
        int[] masterCoord = {masterTe.xCoord, masterTe.yCoord, masterTe.zCoord};
        for (int[] pos : incoming) {
            if (!containsPos(masterTe.nodePositions, pos)) {
                masterTe.nodePositions.add(pos);
            }
            TileEntityFieldGeneratorSC member = fieldGeneratorAt(world, pos);
            if (member != null) {
                member.master = false;
                member.masterPos = masterCoord.clone();
                member.nodePositions.clear();
                member.active = false;
                member.copySettings(masterTe);          // 6: an orphan later re-elected keeps the cluster's owner, not its own old one
                member.handUpgradesTo(masterTe);        // a member has no screen - its upgrades move to the master
                // A member's own buffer is dead weight (only the master pays upkeep) - hand it over.
                // Only what fits: addEnergy caps at the buffer, so moving everything threw the rest away.
                int room = masterTe.getMaxEnergyStored() - masterTe.getEnergyStored();
                masterTe.addEnergy(member.removeEnergy(Math.min(room, member.getEnergyStored())));
                member.changed();
            }
        }
        masterTe.changed();
        return LinkResult.LINKED;
    }

    public enum LinkResult { LINKED, INVALID, TOO_FAR, SAME_CLUSTER, NODE_CAP, UNLOADED, NO_ACCESS }

    /**
     * This node's cluster master; a member whose master is gone becomes its own master again.
     * Null if the master's chunk isn't loaded (it isn't force-loaded just to look).
     */
    private TileEntityFieldGeneratorSC resolveMaster() {
        if (master) {
            return this;
        }
        if (masterPos != null && !worldObj.blockExists(masterPos[0], masterPos[1], masterPos[2])) {
            // Master's chunk isn't loaded - don't force-load it to look, and don't break away from a
            // cluster that is only out of reach: the caller has to give up (LinkResult.UNLOADED).
            return null;
        }
        TileEntityFieldGeneratorSC m = masterPos == null ? null : fieldGeneratorAt(worldObj, masterPos);
        if (m != null && m.master && containsPos(m.nodePositions, new int[]{xCoord, yCoord, zCoord})) {
            return m;
        }
        master = true;
        masterPos = null;
        nodePositions.clear();
        nodePositions.add(new int[]{xCoord, yCoord, zCoord});
        changed();
        return this;
    }

    /** Master for energy forwarding - only if its chunk is loaded (never force-load it). */
    private TileEntityFieldGeneratorSC loadedMaster() {
        if (master || masterPos == null || worldObj == null
                || !worldObj.blockExists(masterPos[0], masterPos[1], masterPos[2])) {
            return null;
        }
        TileEntityFieldGeneratorSC m = fieldGeneratorAt(worldObj, masterPos);
        return m != null && m.master ? m : null;
    }

    /**
     * A linked node passes whatever energy reaches it straight to its master. It used to keep
     * its own 51200 EU buffer, which nothing ever spent (only the master pays upkeep): a cable
     * run to any corner node filled that dead buffer and the field never came on.
     */
    @Override
    public int demandedEnergy() {
        if (master) {
            return super.demandedEnergy();
        }
        TileEntityFieldGeneratorSC m = loadedMaster();
        return m == null ? 0 : m.demandedEnergy();
    }

    @Override
    public int receiveEnergy(net.minecraftforge.common.util.ForgeDirection from, int voltage, int amount, boolean simulate) {
        if (master) {
            return super.receiveEnergy(from, voltage, amount, simulate);
        }
        TileEntityFieldGeneratorSC m = loadedMaster();
        return m == null ? 0 : m.receiveEnergy(from, voltage, amount, simulate);
    }

    /**
     * Called by BlockFieldGeneratorSC.breakBlock before the TE is removed - without this, breaking
     * the master leaves every surviving member's masterPos pointing at a now-empty block forever
     * (members never tick, so nothing would ever notice or re-elect a new master).
     */
    public static void unlink(World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityFieldGeneratorSC)) {
            return;
        }
        TileEntityFieldGeneratorSC self = (TileEntityFieldGeneratorSC) te;
        if (!self.master) {
            // An unloaded master isn't loaded just for this - it drops the dead node itself
            // (pruneNodes) once it runs again.
            TileEntityFieldGeneratorSC master = self.masterPos == null
                    || !world.blockExists(self.masterPos[0], self.masterPos[1], self.masterPos[2])
                    ? null : fieldGeneratorAt(world, self.masterPos);
            int idx = master == null ? -1 : indexOf(master.nodePositions, x, y, z);
            if (idx >= 0) {
                master.nodePositions.remove(idx);
                master.changed();
            }
            return;
        }
        List<int[]> remaining = new ArrayList<int[]>(self.nodePositions);
        int selfIdx = indexOf(remaining, x, y, z);
        if (selfIdx >= 0) {
            remaining.remove(selfIdx);
        }
        if (remaining.isEmpty()) {
            return;
        }
        // The first surviving node whose chunk is loaded takes over (none is force-loaded).
        int[] newMasterPos = null;
        TileEntityFieldGeneratorSC newMaster = null;
        for (int[] pos : remaining) {
            if (world.blockExists(pos[0], pos[1], pos[2])) {
                newMaster = fieldGeneratorAt(world, pos);
                if (newMaster != null) {
                    newMasterPos = pos;
                    break;
                }
            }
        }
        if (newMaster == null) {
            return; // none reachable - they become their own masters when next linked (resolveMaster)
        }
        newMaster.master = true;
        newMaster.mode = self.mode;               // the cluster keeps its shape
        newMaster.range = self.range;
        newMaster.copySettings(self);             // and its switches, owner and access list
        int room = newMaster.getMaxEnergyStored() - newMaster.getEnergyStored();
        newMaster.addEnergy(self.removeEnergy(Math.min(room, self.getEnergyStored())));   // and its charge
        newMaster.masterPos = null;
        newMaster.nodePositions.clear();
        newMaster.nodePositions.addAll(remaining);
        newMaster.changed();
        for (int[] pos : remaining) {
            if (pos == newMasterPos || !world.blockExists(pos[0], pos[1], pos[2])) {
                continue;               // an unloaded member is adopted later by pruneNodes
            }
            TileEntityFieldGeneratorSC member = fieldGeneratorAt(world, pos);
            if (member != null) {
                member.master = false;
                member.masterPos = new int[]{newMasterPos[0], newMasterPos[1], newMasterPos[2]};
                member.nodePositions.clear();
                member.changed();
            }
        }
    }

    private static int indexOf(List<int[]> list, int x, int y, int z) {
        for (int i = 0; i < list.size(); i++) {
            int[] p = list.get(i);
            if (p[0] == x && p[1] == y && p[2] == z) {
                return i;
            }
        }
        return -1;
    }

    private static boolean containsPos(List<int[]> list, int[] pos) {
        for (int[] p : list) {
            if (p[0] == pos[0] && p[1] == pos[1] && p[2] == pos[2]) {
                return true;
            }
        }
        return false;
    }

    private static TileEntityFieldGeneratorSC fieldGeneratorAt(World world, int[] pos) {
        TileEntity te = world.getTileEntity(pos[0], pos[1], pos[2]);
        return te instanceof TileEntityFieldGeneratorSC ? (TileEntityFieldGeneratorSC) te : null;
    }

    @Override
    public boolean canUpdate() {
        return true;
    }

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote || !master) {
            return; // only the master ticks upkeep/protection for the whole cluster
        }
        if (worldObj.getTotalWorldTime() % 100 == 0) {
            pruneNodes();
        }
        boolean powered = worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord);
        boolean rsOff = redstone == REDSTONE_ON ? !powered : redstone == REDSTONE_OFF && powered;
        if (rsOff != redstoneOff) {
            redstoneOff = rsOff;
            changed();
        }
        int upkeep = upkeepPerTick();
        boolean wasActive = active;
        // down, it restarts only with a second's upkeep in hand - else a trickle of power flicked it on and off
        if (!rsOff && getEnergyStored() >= (active ? upkeep : upkeep * 20)) {
            removeEnergy(upkeep);
            active = true;
            // A big field spans hundreds of chunks - scan it every 4th tick instead of every tick
            // (projectiles cover ~3 blocks a tick, well inside even the smallest such field).
            if (range <= 32 || worldObj.getTotalWorldTime() % 4 == 0) {
                protectRegion();
            }
            if (worldObj.getTotalWorldTime() % 20 == 0 && (has(F_CHARGE) || has(F_HEAL))) {
                serveAllies();
            }
            ACTIVE.add(this);
        } else {
            active = false;
            ACTIVE.remove(this);
        }
        if (active != wasActive) {
            changed();          // turn the visible shield on/off for nearby clients
            long now = worldObj.getTotalWorldTime();
            if (!active && !rsOff && now - lastDownWarning > 600) {
                lastDownWarning = now;
                warn("sc.field.warn.down");
            }
        }
        if (getEnergyStored() < getMaxEnergyStored() / 10) {
            if (active && !lowWarned) {
                lowWarned = true;
                warn("sc.field.warn.low");
            }
        } else if (getEnergyStored() > getMaxEnergyStored() / 5) {
            lowWarned = false;
        }
    }

    /** A chat line to the owner, if the warnings are on and the owner is online. */
    private void warn(String key) {
        if (!has(F_WARN) || owner.isEmpty()) {
            return;
        }
        net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
        EntityPlayer p = server == null ? null : server.getConfigurationManager().func_152612_a(owner);
        if (p != null) {
            p.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation(key,
                    String.valueOf(xCoord), String.valueOf(yCoord), String.valueOf(zCoord)));
        }
    }

    /** Once a second: the owner and the access list inside the field are charged and healed (if switched on). */
    private void serveAllies() {
        AxisAlignedBB box = FieldShapeSC.bounds(mode, nodePositions, range);
        for (Object o : worldObj.getEntitiesWithinAABB(EntityPlayer.class, box)) {
            EntityPlayer p = (EntityPlayer) o;
            if (!allowed(p) || p.isDead || !fieldContains(p.posX, p.posY + 1, p.posZ)) {
                continue;
            }
            int spare = getEnergyStored() - upkeepPerTick() * 40;
            if (has(F_CHARGE) && spare > 0) {
                int budget = Math.min(CHARGE_PER_SECOND, spare);
                for (int i = 0; i < p.inventory.armorInventory.length && budget > 0; i++) {
                    budget -= chargeInto(p, p.inventory.armorInventory[i], budget);
                }
                for (int i = 0; i < p.inventory.mainInventory.length && budget > 0; i++) {
                    ItemStack s = p.inventory.mainInventory[i];
                    if (!(p.isUsingItem() && s == p.getCurrentEquippedItem())) {       // a blocking blade keeps its block
                        budget -= chargeInto(p, s, budget);
                    }
                }
            }
            if (has(F_HEAL) && p.getHealth() < p.getMaxHealth() && getEnergyStored() - upkeepPerTick() * 40 >= HEAL_COST
                    && pay(HEAL_COST)) {
                p.heal(1.0F);
            }
        }
    }

    /** Charges one of the mod's electric items from the buffer. @return EU used */
    private int chargeInto(EntityPlayer p, ItemStack s, int max) {
        if (s == null || max <= 0) {
            return 0;
        }
        int took;
        if (s.getItem() instanceof com.sc.item.ItemArmorSC) {
            took = com.sc.item.ItemArmorSC.charge(s, max);
        } else if (s.getItem() instanceof com.sc.item.ItemBladeSC) {
            took = com.sc.item.ItemBladeSC.charge(s, max);
        } else if (s.getItem() instanceof com.sc.item.ItemDrillSC) {
            took = com.sc.item.DrillLogicSC.digging(p, s) ? 0 : com.sc.item.ItemDrillSC.charge(s, max);
        } else if (s.getItem() instanceof com.sc.item.ItemWeaponSC) {
            took = com.sc.item.ItemWeaponSC.charge(s, ((com.sc.item.ItemWeaponSC) s.getItem()).getType(), max);
        } else {
            return 0;
        }
        removeEnergy(took);
        return took;
    }

    /** A burst of the shell's colour where it stopped something (a projectile, an explosion). */
    public void flash(double x, double y, double z) {
        if (!has(F_SHOW) || !(worldObj instanceof net.minecraft.world.WorldServer)) {
            return;
        }
        float[] c = COLORS[color];
        net.minecraft.world.WorldServer ws = (net.minecraft.world.WorldServer) worldObj;
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            // count 0: the offsets are the particle's motion - for redstone dust, its colour
            ws.func_147487_a("reddust", x + Math.cos(a) * 0.6, y + Math.sin(a) * 0.6, z + Math.sin(a * 2) * 0.3,
                    0, Math.max(0.01F, c[0]), c[1], c[2], 1.0);
        }
    }

    /**
     * Keeps the node list true to the world for changes made while chunks were unloaded (unlink
     * never force-loads them): a recorded node that is gone, or now belongs to another cluster,
     * is dropped; a member still pointing at a vanished master is adopted. Unloaded nodes are
     * left as they are.
     */
    private void pruneNodes() {
        boolean dirty = false;
        for (int i = nodePositions.size() - 1; i >= 0; i--) {
            int[] pos = nodePositions.get(i);
            if ((pos[0] == xCoord && pos[1] == yCoord && pos[2] == zCoord)
                    || !worldObj.blockExists(pos[0], pos[1], pos[2])) {
                continue;
            }
            TileEntityFieldGeneratorSC node = fieldGeneratorAt(worldObj, pos);
            if (node == null || node.master) {
                nodePositions.remove(i);
                dirty = true;
                continue;
            }
            if (node.masterPos != null && node.masterPos[0] == xCoord && node.masterPos[1] == yCoord
                    && node.masterPos[2] == zCoord) {
                continue;
            }
            int[] other = node.masterPos;
            TileEntityFieldGeneratorSC otherMaster = other == null || !worldObj.blockExists(other[0], other[1], other[2])
                    ? null : fieldGeneratorAt(worldObj, other);
            if (other != null && (otherMaster == null ? !worldObj.blockExists(other[0], other[1], other[2])
                    : otherMaster.master && containsPos(otherMaster.nodePositions, pos))) {
                nodePositions.remove(i);        // it belongs to another (possibly unloaded) cluster
                dirty = true;
            } else {
                node.masterPos = new int[]{xCoord, yCoord, zCoord};
                node.changed();
            }
        }
        if (dirty) {
            changed();
        }
    }

    // ---- what the field does while active ----

    /** EU per projectile turned back and per explosion kept off the blocks inside (TODO: not in the design doc). */
    public static final int DEFLECT_COST = 50;
    public static final int EXPLOSION_COST = 2000;

    /** Masters whose field is up, per server - ExplosionEvent handling looks them up here. */
    private static final java.util.Set<TileEntityFieldGeneratorSC> ACTIVE =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<TileEntityFieldGeneratorSC, Boolean>());

    public static java.util.List<TileEntityFieldGeneratorSC> activeFieldsIn(World world) {
        java.util.List<TileEntityFieldGeneratorSC> out = new ArrayList<TileEntityFieldGeneratorSC>();
        for (TileEntityFieldGeneratorSC te : ACTIVE) {
            if (te.worldObj == world && !te.isInvalid() && te.active && te.master) {
                out.add(te);
            }
        }
        return out;
    }

    public boolean fieldContains(double x, double y, double z) {
        return FieldShapeSC.contains(mode, nodePositions, range, x, y, z);
    }

    /** Spends EU for a protective action; false (and nothing spent) if the buffer can't cover it. */
    public boolean pay(int eu) {
        if (getEnergyStored() < eu) {
            return false;
        }
        removeEnergy(eu);
        return true;
    }

    private void protectRegion() {
        AxisAlignedBB box = FieldShapeSC.bounds(mode, nodePositions, range);
        // Hostile mobs inside the shape are shoved out and hurt - every IMob, not just EntityMob
        // (ghasts, slimes and magma cubes are IMob without being EntityMob and used to be ignored).
        List<Entity> living = worldObj.getEntitiesWithinAABB(EntityLiving.class, box);
        if (has(F_PRIVATE) && has(F_PUSH_PLAYERS)) {
            for (Object o : worldObj.getEntitiesWithinAABB(EntityPlayer.class, box)) {
                EntityPlayer p = (EntityPlayer) o;
                if (!allowed(p) && !p.capabilities.isCreativeMode) {
                    living.add(p);                  // strangers out of a private field (not hurt)
                }
            }
        }
        for (Entity entity : living) {
            boolean stranger = entity instanceof EntityPlayer;
            if (!(stranger || targets(entity)) || !fieldContains(entity.posX, entity.posY + entity.height / 2, entity.posZ)) {
                continue;
            }
            double[] from = pushOrigin(entity, box);
            double dx = entity.posX - from[0];
            double dz = entity.posZ - from[1];
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < 0.001) {
                dx = 1;
                dist = 1;
            }
            entity.motionX += (dx / dist) * 0.5;
            entity.motionZ += (dz / dist) * 0.5;
            entity.velocityChanged = true;
            if (!stranger && has(F_DAMAGE)) {
                entity.attackEntityFrom(net.minecraft.util.DamageSource.generic, 1.0F);
            }
        }
        // Projectiles from anything but a player are turned back at the shield.
        List<Entity> all = worldObj.getEntitiesWithinAABB(Entity.class, box);
        for (Entity e : all) {
            if (!isHostileProjectile(e) || e.getEntityData().getBoolean("scDeflected")
                    || !fieldContains(e.posX, e.posY, e.posZ) || !pay(DEFLECT_COST)) {
                continue;
            }
            e.motionX = -e.motionX;
            e.motionY = -e.motionY;
            e.motionZ = -e.motionZ;
            if (e instanceof net.minecraft.entity.projectile.EntityFireball) {
                net.minecraft.entity.projectile.EntityFireball f = (net.minecraft.entity.projectile.EntityFireball) e;
                f.accelerationX = -f.accelerationX;
                f.accelerationY = -f.accelerationY;
                f.accelerationZ = -f.accelerationZ;
            }
            e.getEntityData().setBoolean("scDeflected", true);   // once - no ping-pong inside the field
            e.velocityChanged = true;
            worldObj.playSoundEffect(e.posX, e.posY, e.posZ, "random.fizz", 0.6F, 1.6F);
            flash(e.posX, e.posY, e.posZ);
        }
    }

    /** Push away from the nearest node for bubbles, from the middle of the field otherwise. */
    private double[] pushOrigin(Entity entity, AxisAlignedBB box) {
        if (!mode.perNode()) {
            return new double[]{(box.minX + box.maxX) / 2.0, (box.minZ + box.maxZ) / 2.0};
        }
        double[] best = null;
        double bestD = Double.MAX_VALUE;
        for (int[] n : nodePositions) {
            double d = entity.getDistanceSq(n[0] + 0.5, n[1] + 0.5, n[2] + 0.5);
            if (d < bestD) {
                bestD = d;
                best = new double[]{n[0] + 0.5, n[2] + 0.5};
            }
        }
        return best;
    }

    private static boolean isHostileProjectile(Entity e) {
        Entity owner;
        if (e instanceof net.minecraft.entity.projectile.EntityArrow) {
            owner = ((net.minecraft.entity.projectile.EntityArrow) e).shootingEntity;
        } else if (e instanceof net.minecraft.entity.projectile.EntityFireball) {
            owner = ((net.minecraft.entity.projectile.EntityFireball) e).shootingEntity;
        } else if (e instanceof net.minecraft.entity.projectile.EntityThrowable) {
            owner = ((net.minecraft.entity.projectile.EntityThrowable) e).getThrower();
        } else {
            return false;
        }
        // Everything not fired by a player, as the manual says: mobs' shots, dispensers, and projectiles
        // with no shooter at all (arrows don't save theirs, so any arrow reloaded from a save). A
        // player's own reloaded arrow is turned back too - it can't be told apart any more.
        if (owner instanceof EntityPlayer) {
            return false;
        }
        // Arrows stuck in a block have no motion to turn around.
        return e.motionX * e.motionX + e.motionY * e.motionY + e.motionZ * e.motionZ > 0.01;
    }

    // ---- upgrade slots (energy storage, transformer): player-only, no pipes or hoppers ----

    /** How many upgrades of a kind count (the machines' cap, UpgradeType.MAX_EFFECTIVE). */
    public int upgradeCount(com.sc.machine.UpgradeType type) {
        int n = 0;
        for (ItemStack s : upgrades) {
            if (s != null && s.getItem() instanceof com.sc.item.ItemUpgradeSC && com.sc.item.ItemUpgradeSC.typeOf(s) == type) {
                n += s.stackSize;
            }
        }
        return Math.min(n, com.sc.machine.UpgradeType.MAX_EFFECTIVE);
    }

    public int storageUpgrades() {
        return upgradeCount(com.sc.machine.UpgradeType.ENERGY_STORAGE);
    }

    private static boolean isFieldUpgrade(ItemStack s) {
        if (s == null || !(s.getItem() instanceof com.sc.item.ItemUpgradeSC)) {
            return false;
        }
        com.sc.machine.UpgradeType t = com.sc.item.ItemUpgradeSC.typeOf(s);
        return t == com.sc.machine.UpgradeType.ENERGY_STORAGE || t == com.sc.machine.UpgradeType.TRANSFORMER;
    }

    /**
     * Each transformer upgrade takes one tier higher voltage without exploding (HV -> EV, like a
     * machine's). A linked node takes whatever its master takes - it only passes energy on.
     */
    @Override
    public Tier inputTier() {
        if (!master) {
            TileEntityFieldGeneratorSC m = loadedMaster();
            if (m != null) {
                return m.inputTier();
            }
        }
        Tier[] tiers = Tier.values();
        return tiers[Math.min(tiers.length - 1, getTier().ordinal() + upgradeCount(com.sc.machine.UpgradeType.TRANSFORMER))];
    }

    /** HV buffer + 10 000 EU per energy storage upgrade. */
    @Override
    public int getMaxEnergyStored() {
        return super.getMaxEnergyStored() + storageUpgrades() * com.sc.machine.UpgradeType.STORAGE_PER_UPGRADE;
    }

    /** Taking upgrades out shrinks the buffer - what no longer fits is lost, as in a machine. */
    private void clampEnergy() {
        int over = getEnergyStored() - getMaxEnergyStored();
        if (over > 0 && worldObj != null && !worldObj.isRemote) {
            removeEnergy(over);
        }
    }

    /** A node joining a cluster: its upgrades into the master's free room, the rest dropped where it stands. */
    private void handUpgradesTo(TileEntityFieldGeneratorSC to) {
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            ItemStack s = upgrades[i];
            if (s == null) {
                continue;
            }
            for (int j = 0; j < UPGRADE_SLOTS && s.stackSize > 0; j++) {
                ItemStack t = to.upgrades[j];
                if (t == null) {
                    to.upgrades[j] = s.copy();
                    s.stackSize = 0;
                } else if (t.isItemEqual(s) && ItemStack.areItemStackTagsEqual(t, s)) {
                    int move = Math.min(s.stackSize, t.getMaxStackSize() - t.stackSize);
                    t.stackSize += move;
                    s.stackSize -= move;
                }
            }
            if (s.stackSize > 0 && worldObj != null) {
                worldObj.spawnEntityInWorld(new net.minecraft.entity.item.EntityItem(worldObj, xCoord + 0.5, yCoord + 1.2, zCoord + 0.5, s));
            }
            upgrades[i] = null;
        }
        markDirty();
        to.markDirty();
    }

    /** Breaking the block: its upgrades drop (BlockFieldGeneratorSC.breakBlock). */
    public void dropUpgrades() {
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            if (upgrades[i] != null && worldObj != null) {
                worldObj.spawnEntityInWorld(new net.minecraft.entity.item.EntityItem(worldObj, xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, upgrades[i]));
            }
            upgrades[i] = null;
        }
    }

    @Override
    public int getSizeInventory() {
        return UPGRADE_SLOTS;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot >= 0 && slot < UPGRADE_SLOTS ? upgrades[slot] : null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int count) {
        ItemStack s = getStackInSlot(slot);
        if (s == null) {
            return null;
        }
        ItemStack out;
        if (s.stackSize <= count) {
            out = s;
            upgrades[slot] = null;
        } else {
            out = s.splitStack(count);
        }
        markDirty();
        return out;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return null;                            // the slots are the block's own, not a crafting grid
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot < 0 || slot >= UPGRADE_SLOTS) {
            return;
        }
        if (stack != null && stack.stackSize > getInventoryStackLimit()) {
            stack.stackSize = getInventoryStackLimit();
        }
        upgrades[slot] = stack;
        markDirty();
    }

    @Override
    public void markDirty() {
        clampEnergy();
        super.markDirty();
    }

    @Override
    public String getInventoryName() {
        return "container.siliconage.fieldGenerator";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public void openInventory() {
    }

    @Override
    public void closeInventory() {
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return isFieldUpgrade(stack);
    }

    private static final int[] NO_SLOTS = new int[0];

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        return NO_SLOTS;
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack stack, int side) {
        return false;
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack stack, int side) {
        return false;
    }

    // ---- client sync: the shield is drawn from the master's nodes, mode and active flag ----

    private void changed() {
        markDirty();
        if (worldObj != null && !worldObj.isRemote) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    @Override
    public net.minecraft.network.Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        writeToNBT(nbt);
        return new net.minecraft.network.play.server.S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, nbt);
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.S35PacketUpdateTileEntity pkt) {
        readFromNBT(pkt.func_148857_g());
    }

    /** Client copy of the node list, for the renderer. */
    public List<int[]> getNodePositions() {
        return nodePositions;
    }

    @Override
    public void invalidate() {
        if (worldObj == null || !worldObj.isRemote) {   // the set is server-side only (single-player shares the JVM)
            ACTIVE.remove(this);
        }
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        if (worldObj == null || !worldObj.isRemote) {
            ACTIVE.remove(this);
        }
        super.onChunkUnload();
    }

    /** The shield is far larger than the block - don't cull it when the block itself is off-screen. */
    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        return master && !nodePositions.isEmpty() ? FieldShapeSC.bounds(mode, nodePositions, range) : super.getRenderBoundingBox();
    }

    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public double getMaxRenderDistanceSquared() {
        double d = 128 + range;         // the shell is visible from as far as it reaches
        return d * d;
    }

    /** Drawn in the translucent pass, after solid terrain. */
    @Override
    public boolean shouldRenderInPass(int pass) {
        return pass == 1;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        mode = FieldMode.values()[Math.min(FieldMode.values().length - 1, Math.max(0, nbt.getInteger("Mode")))];
        range = nbt.hasKey("Range") ? FieldShapeSC.clampRange(nbt.getInteger("Range")) : FieldShapeSC.DEFAULT_RANGE;
        master = nbt.getBoolean("Master");
        active = nbt.getBoolean("Active");
        nodePositions.clear();
        NBTTagList list = nbt.getTagList("Nodes", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound n = list.getCompoundTagAt(i);
            nodePositions.add(new int[]{n.getInteger("X"), n.getInteger("Y"), n.getInteger("Z")});
        }
        masterPos = nbt.hasKey("MasterX")
                ? new int[]{nbt.getInteger("MasterX"), nbt.getInteger("MasterY"), nbt.getInteger("MasterZ")} : null;
        flags = nbt.hasKey("Flags") ? nbt.getInteger("Flags") : DEFAULT_FLAGS;
        redstone = Math.max(0, Math.min(2, nbt.getInteger("Redstone")));
        filter = Math.max(0, Math.min(2, nbt.getInteger("Filter")));
        color = Math.max(0, Math.min(COLORS.length - 1, nbt.getInteger("Color")));
        owner = nbt.getString("Owner");
        redstoneOff = nbt.getBoolean("RedstoneOff");
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            upgrades[i] = null;
        }
        NBTTagList ups = nbt.getTagList("Upgrades", 10);
        for (int i = 0; i < ups.tagCount(); i++) {
            NBTTagCompound u = ups.getCompoundTagAt(i);
            int slot = u.getByte("Slot");
            if (slot >= 0 && slot < UPGRADE_SLOTS) {
                upgrades[slot] = ItemStack.loadItemStackFromNBT(u);
            }
        }
        access.clear();
        NBTTagList names = nbt.getTagList("Access", 8);
        for (int i = 0; i < names.tagCount(); i++) {
            access.add(names.getStringTagAt(i));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger("Mode", mode.ordinal());
        nbt.setInteger("Range", range);
        nbt.setBoolean("Master", master);
        nbt.setBoolean("Active", active);
        NBTTagList list = new NBTTagList();
        for (int[] p : nodePositions) {
            NBTTagCompound n = new NBTTagCompound();
            n.setInteger("X", p[0]);
            n.setInteger("Y", p[1]);
            n.setInteger("Z", p[2]);
            list.appendTag(n);
        }
        nbt.setTag("Nodes", list);
        if (masterPos != null) {
            nbt.setInteger("MasterX", masterPos[0]);
            nbt.setInteger("MasterY", masterPos[1]);
            nbt.setInteger("MasterZ", masterPos[2]);
        }
        nbt.setInteger("Flags", flags);
        nbt.setInteger("Redstone", redstone);
        nbt.setInteger("Filter", filter);
        nbt.setInteger("Color", color);
        nbt.setString("Owner", owner);
        nbt.setBoolean("RedstoneOff", redstoneOff);
        NBTTagList names = new NBTTagList();
        for (String n : access) {
            names.appendTag(new net.minecraft.nbt.NBTTagString(n));
        }
        nbt.setTag("Access", names);
        NBTTagList ups = new NBTTagList();
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            if (upgrades[i] != null) {
                NBTTagCompound u = new NBTTagCompound();
                u.setByte("Slot", (byte) i);
                upgrades[i].writeToNBT(u);
                ups.appendTag(u);
            }
        }
        nbt.setTag("Upgrades", ups);
    }
}
