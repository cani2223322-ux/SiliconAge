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
    /** The battery slot under the energy gauge (the master's). */
    public static final int SLOT_BATTERY = UPGRADE_SLOTS;
    private final ItemStack[] upgrades = new ItemStack[UPGRADE_SLOTS];
    private ItemStack battery;

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
            F_WARN = 32, F_CHARGE = 64, F_HEAL = 128, F_SHOW = 256, F_CHARGE_FX = 512,
            F_BEAMS = 1024, F_HUM = 2048, F_DASH = 4096,
            /** Wireless charging leaves IC2 batteries / energy crystals in the inventory alone. */
            F_SKIP_BATTERIES = 8192,
            /** With a charge booster in: every item being charged gets the full rate, not a share of it. */
            F_CHARGE_EACH = 16384,
            /** Rain shield: while it rains over the field, upkeep +25% (+50% in a storm); no rain inside, no snow or ice, lightning taken. */
            F_RAIN = 32768,
            /** Radiation shield: players inside take no radiation; RadiationSC.FIELD_EU_PER_LEVEL a second for each level stopped. */
            F_RADIATION = 65536;
    /** The rain shield's extra upkeep, % of the field's, in rain and in a thunderstorm; and what a lightning bolt costs. */
    public static final int RAIN_PCT = 25, THUNDER_PCT = 50, LIGHTNING_COST = 2000;
    /** A new field (and one from before the switches): mobs pushed and hurt, warnings on, shell and charging sparks shown. */
    public static final int DEFAULT_FLAGS = F_DAMAGE | F_WARN | F_SHOW | F_CHARGE_FX;
    /**
     * What charges first: armour; the held item; everything at once (the rate shared among what isn't
     * full, a finished item's share going to the rest); the armour alone; the emptiest first; the
     * nearly full first; only what's worn and held.
     */
    public static final int CHARGE_ARMOR_FIRST = 0, CHARGE_HELD_FIRST = 1, CHARGE_EVEN = 2, CHARGE_ARMOR_ONLY = 3,
            CHARGE_LOWEST_FIRST = 4, CHARGE_FULLEST_FIRST = 5, CHARGE_WORN_ONLY = 6, CHARGE_MODES = 7;
    /** Charging only tops up what's below this percent: 10..100 in steps of 10 (100: everything not full). */
    public static final int CHARGE_BELOW_STEP = 10, CHARGE_BELOW_MIN = 10;
    /** The charging reserve: 0..90% of the buffer, in steps of 10. */
    public static final int RESERVE_STEP = 10, RESERVE_MAX = 90;
    /** RF per EU when charging other mods' RF items (Thermal Expansion's rate). */
    public static final int RF_PER_EU = 4;
    /** EU per tick each switched-on protection adds to the upkeep (TODO: not in the design doc). */
    public static final int NO_SPAWN_EU = 8, NO_ENDER_EU = 4, PRIVATE_EU = 16, PUSH_PLAYERS_EU = 8;
    /** Wireless charging: EU a second per player, from the master's buffer 1:1. Healing: EU per point healed. */
    public static final int CHARGE_PER_SECOND = 10240, HEAL_COST = 400;
    public static final int REDSTONE_ALWAYS = 0, REDSTONE_ON = 1, REDSTONE_OFF = 2;
    /**
     * Targets: hostile mobs, or every creature but players (strangers are the Access tab's "push
     * strangers"). 2 was "all but animals and villagers" - now inside NEUTRAL, and loads as it.
     */
    public static final int FILTER_HOSTILE = 0, FILTER_NEUTRAL = 1, FILTER_ALL = 2;

    /** An old save's filter to 0 or 1. */
    private static int loadFilter(int f) {
        return f >= FILTER_NEUTRAL ? FILTER_NEUTRAL : FILTER_HOSTILE;
    }
    public static final int MAX_ACCESS = 16;
    /** The old five shell colours (cyan, green, red, violet, gold) - only to read fields saved before RGB colours. */
    public static final float[][] COLORS = {{0.35F, 0.9F, 1F}, {0.35F, 1F, 0.45F}, {1F, 0.3F, 0.3F}, {0.75F, 0.45F, 1F}, {1F, 0.8F, 0.3F}};

    // ---- the Zone tab ----

    /** Where the shape is built round: every node, the cluster's middle, or a point set by a player. */
    public static final int ANCHOR_NODES = 0, ANCHOR_CENTRE = 1, ANCHOR_POINT = 2, ANCHORS = 3;
    /** The zone's offset from its anchors, per axis, in blocks. */
    public static final int MAX_OFFSET = 32;
    /** When the outline is drawn: with the shell, always, with a wrench in hand, never. */
    public static final int OUTLINE_WITH_SHELL = 0, OUTLINE_ALWAYS = 1, OUTLINE_WRENCH = 2, OUTLINE_NEVER = 3, OUTLINES = 4;
    public static final int ANIM_PULSE = 0, ANIM_WAVES = 1, ANIM_STATIC = 2, ANIMS = 3;
    public static final int BRIGHT_STEP = 25;
    /** The three colours the screen sets: the shell, the outline (and node beams), sparks and flashes. */
    public static final int RGB_SHELL = 0, RGB_OUTLINE = 1, RGB_FLASH = 2, RGB_TARGETS = 3;
    public static final int DEFAULT_RGB = 0x59E6FF;
    /** The screen's eight ready colours: cyan, green, red, violet, gold, white, pink, orange. */
    /** Ready colours, named sc.fieldzone.preset.N (the first eight are the old ones - cycleColor went through them). */
    public static final int[] PRESETS = {0x59E6FF, 0x59FF73, 0xFF4D4D, 0xBF73FF, 0xFFCC4D, 0xFFFFFF, 0xFF78BE, 0xFF8C1E, 0xFFF04D, 0xB4FF3C, 0x2EE6A0, 0x3CD2C8, 0x78B4FF, 0x3C64FF, 0x7850FF, 0xE63CE6, 0xE6286E, 0xFF7F66, 0xFFA028, 0xE6C88C, 0xA06432, 0xA0A0A0, 0x505A64, 0xC8F0FF};

    private int flags = DEFAULT_FLAGS;
    private int redstone = REDSTONE_OFF, filter = FILTER_HOSTILE;      // a new one: works without a signal, a signal stops it
    /** 0: the same as the range. */
    private int height;
    private int offX, offY, offZ, anchor = ANCHOR_NODES;
    private int[] anchorPoint;
    private int outline = OUTLINE_WITH_SHELL, anim = ANIM_PULSE, brightness = 100;
    private final int[] rgb = {DEFAULT_RGB, DEFAULT_RGB, DEFAULT_RGB};
    /** zoneNodes(), rebuilt after any change (changed(), a load). */
    private List<int[]> zoneCache;
    private int chargeMode = CHARGE_ARMOR_FIRST, chargeReserve, chargeBelow = 100;
    /** Last second's charging: EU given out and players served (the screen shows them). */
    private int chargedLastSecond, playersLastSecond;
    private String owner = "";
    private final List<String> access = new ArrayList<String>();
    /** Switched off by its redstone setting right now (shown on the screen). */
    private boolean redstoneOff;
    /** The rain shield is up: raining over the field, switched on and paid for (clients hide the rain inside). */
    private boolean rainShield;
    /** The last power-on / new zone was refused: it would overlap a stranger's field (the screen says so). */
    private boolean foreignNear;
    /** Snow and ice: columns of the zone seen clear, so snow or ice there now is the weather's, not the player's. */
    private java.util.BitSet clearCols, seenCols, waterCols;
    /** Each column's precipitation height when it was seen clear: another height now is a player's build. */
    private int[] topCols;
    private AxisAlignedBB snowBox;
    private int snowSweep;
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

    /** The field keeps its own redstone setting; the power switch's button (GuiPowerSC) shows it. */
    @Override
    public int getRedstoneMode() {
        return redstone;
    }

    /** The power switch (master): off, the field is down and takes no energy. */
    @Override
    public void cycleBatteryMode() {
        super.cycleBatteryMode();
        changed();
    }

    public void togglePower() {
        if (!powerOn && refuseForeign(true)) {
            return;                                 // a stranger's field in the way: stays off
        }
        powerOn = !powerOn;
        com.sc.util.SoundsSC.powerClick(this, powerOn);
        changed();
    }

    public void cycleRedstone() {
        redstone = (redstone + 1) % 3;
        changed();
    }

    public int getFilter() {
        return filter;
    }

    public void cycleFilter() {
        filter = filter == FILTER_HOSTILE ? FILTER_NEUTRAL : FILTER_HOSTILE;
        changed();
    }

    public int getRgb(int target) {
        return rgb[Math.max(0, Math.min(RGB_TARGETS - 1, target))];
    }

    public float[] rgbF(int target) {
        int c = getRgb(target);
        return new float[]{((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F};
    }

    public void setRgb(int target, int value) {
        if (target >= 0 && target < RGB_TARGETS && rgb[target] != (value & 0xFFFFFF)) {
            rgb[target] = value & 0xFFFFFF;
            changed();
        }
    }

    public int getHeight() {
        return height;
    }

    /** 0: x, 1: y, 2: z. */
    public int getOffset(int axis) {
        return axis == 0 ? offX : axis == 1 ? offY : offZ;
    }

    public int getAnchor() {
        return anchor;
    }

    public int[] getAnchorPoint() {
        return anchorPoint;
    }

    public int getOutline() {
        return outline;
    }

    public void cycleOutline() {
        outline = (outline + 1) % OUTLINES;
        changed();
    }

    public int getAnim() {
        return anim;
    }

    public void cycleAnim() {
        anim = (anim + 1) % ANIMS;
        changed();
    }

    /** Shell and outline brightness, 25..100 %. */
    public int getBrightness() {
        return brightness;
    }

    public void cycleBrightness() {
        brightness = brightness >= 100 ? BRIGHT_STEP : brightness + BRIGHT_STEP;
        changed();
    }

    /**
     * The Zone tab's whole shape at once (FieldNetSC.ZONE), clamped. A set point must lie within
     * MAX_LINK_DISTANCE of the master on every axis; @return false (and the anchor left as it was) if not.
     */
    public boolean setZone(int newRange, int newHeight, int ox, int oy, int oz, int newAnchor, int modeOrdinal, int px, int py, int pz) {
        boolean ok = true;
        int[] before = saveShape();
        int[] point = anchorPoint;
        range = FieldShapeSC.clampRange(newRange);
        height = newHeight <= 0 ? 0 : FieldShapeSC.clampRange(newHeight);
        offX = clampOffset(ox);
        offY = clampOffset(oy);
        offZ = clampOffset(oz);
        FieldMode[] modes = FieldMode.values();
        mode = modes[Math.max(0, Math.min(modes.length - 1, modeOrdinal))];
        int a = Math.max(0, Math.min(ANCHORS - 1, newAnchor));
        if (a == ANCHOR_POINT) {
            if (Math.abs(px - xCoord) <= MAX_LINK_DISTANCE && Math.abs(py - yCoord) <= MAX_LINK_DISTANCE
                    && Math.abs(pz - zCoord) <= MAX_LINK_DISTANCE) {
                anchorPoint = new int[]{px, py, pz};
                anchor = a;
            } else {
                ok = false;
            }
        } else {
            anchor = a;
        }
        keepShape(before, point);                   // over a stranger's field: the old zone stays (they're told)
        changed();
        return ok;
    }

    private static int clampOffset(int v) {
        return Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, v));
    }

    // ---- no field over a stranger's: checked on power-on and on a new zone (fields already up stay up) ----

    public boolean isForeignNear() {
        return foreignNear;
    }

    /** Shape, range, height, offsets and anchor (the anchor point apart) - to put back a refused zone. */
    private int[] saveShape() {
        return new int[]{mode.ordinal(), range, height, offX, offY, offZ, anchor};
    }

    private void restoreShape(int[] s, int[] point) {
        mode = FieldMode.values()[s[0]];
        range = s[1];
        height = s[2];
        offX = s[3];
        offY = s[4];
        offZ = s[5];
        anchor = s[6];
        anchorPoint = point;
        zoneCache = null;
    }

    /** After a change of shape: back to the saved one if the new zone overlaps a stranger's field. @return true if kept */
    private boolean keepShape(int[] before, int[] point) {
        zoneCache = null;
        if (refuseForeign(true)) {
            restoreShape(before, point);
            changed();
            return false;
        }
        return true;
    }

    /**
     * A loaded, up field of someone else (another owner whose access list doesn't have this field's
     * owner) that this zone would overlap, or null. Server side.
     */
    public TileEntityFieldGeneratorSC foreignOverlap() {
        return foreignOverlap(null);
    }

    /** foreignOverlap() leaving `ignore` out (link: the cluster that joins). */
    private TileEntityFieldGeneratorSC foreignOverlap(TileEntityFieldGeneratorSC ignore) {
        if (worldObj == null || worldObj.isRemote || !master) {
            return null;
        }
        for (TileEntityFieldGeneratorSC f : activeFieldsIn(worldObj)) {
            if (f == this || f == ignore || f.owner.isEmpty() || f.allowedName(owner)) {
                continue;
            }
            if (FieldShapeSC.overlaps(mode, zoneNodes(), range, height, f.mode, f.zoneNodes(), f.range, f.height)) {
                return f;
            }
        }
        return null;
    }

    /**
     * Sets the "stranger's field near" status from foreignOverlap(); with `tell`, the players with
     * this field's screen open hear why. @return true if there is such a field (the caller refuses)
     */
    private boolean refuseForeign(boolean tell) {
        if (worldObj == null || worldObj.isRemote) {
            return false;                           // the server decides (a client copy keeps the synced status)
        }
        TileEntityFieldGeneratorSC f = foreignOverlap();
        boolean was = foreignNear;
        foreignNear = f != null;
        if (f != null && tell) {
            for (Object o : worldObj.playerEntities) {
                EntityPlayer p = (EntityPlayer) o;
                if (p.openContainer instanceof com.sc.inventory.ContainerFieldGeneratorSC
                        && ((com.sc.inventory.ContainerFieldGeneratorSC) p.openContainer).getField() == this) {
                    p.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation("sc.field.foreign", f.getOwner()));
                }
            }
        }
        if (was != foreignNear) {
            changed();
        }
        return f != null;
    }

    /**
     * Just placed (BlockFieldGeneratorSC.onBlockPlacedBy): a zone over a stranger's field is placed
     * switched off. @return the stranger's field's owner, or null if all is well
     */
    public String placedNearForeign() {
        TileEntityFieldGeneratorSC f = foreignOverlap();
        foreignNear = f != null;
        if (f != null) {
            powerOn = false;
        }
        changed();
        return f == null ? null : f.getOwner();
    }

    /** The points the shape is built round: the anchors, shifted by the offset. */
    public List<int[]> zoneNodes() {
        if (zoneCache == null) {
            zoneCache = zoneNodesFor(nodePositions, anchor, anchorPoint, offX, offY, offZ);
        }
        return zoneCache;
    }

    /** zoneNodes() for any settings (the screen's preview uses it too). */
    public static List<int[]> zoneNodesFor(List<int[]> nodes, int anchor, int[] point, int ox, int oy, int oz) {
        List<int[]> base = new ArrayList<int[]>();
        if (anchor == ANCHOR_CENTRE && !nodes.isEmpty()) {
            double x = 0, y = 0, z = 0;
            for (int[] n : nodes) {
                x += n[0];
                y += n[1];
                z += n[2];
            }
            base.add(new int[]{(int) Math.round(x / nodes.size()), (int) Math.round(y / nodes.size()), (int) Math.round(z / nodes.size())});
        } else if (anchor == ANCHOR_POINT && point != null) {
            base.add(point.clone());
        } else {
            for (int[] n : nodes) {
                base.add(n.clone());
            }
        }
        for (int[] p : base) {
            p[0] += ox;
            p[1] += oy;
            p[2] += oz;
        }
        return base;
    }

    public AxisAlignedBB zoneBounds() {
        return FieldShapeSC.bounds(mode, zoneNodes(), range, height);
    }

    public int getChargeMode() {
        return chargeMode;
    }

    public void cycleChargeMode() {
        chargeMode = (chargeMode + 1) % CHARGE_MODES;
        changed();
    }

    /** Percent of the buffer charging never goes below. */
    public int getChargeReserve() {
        return chargeReserve;
    }

    public void adjustChargeReserve(int delta) {
        chargeReserve = Math.max(0, Math.min(RESERVE_MAX, chargeReserve + delta));
        changed();
    }

    /** Items charge only while below this percent. */
    public int getChargeBelow() {
        return chargeBelow;
    }

    public void adjustChargeBelow(int delta) {
        chargeBelow = Math.max(CHARGE_BELOW_MIN, Math.min(100, chargeBelow + delta));
        changed();
    }

    /** "Full rate for each item" is on and has a charge booster to work with. */
    public boolean chargesEachAtFullRate() {
        return has(F_CHARGE_EACH) && upgradeCount(com.sc.machine.UpgradeType.CHARGE_BOOSTER) > 0;
    }

    /** EU a second one player can get: the base, x2 per charge booster (up to 4). */
    public int chargeRate() {
        return CHARGE_PER_SECOND << Math.min(com.sc.machine.UpgradeType.MAX_CHARGE_BOOSTERS,
                upgradeCount(com.sc.machine.UpgradeType.CHARGE_BOOSTER));
    }

    public int getChargedLastSecond() {
        return chargedLastSecond;
    }

    public int getPlayersLastSecond() {
        return playersLastSecond;
    }

    /** Client: last second's charging numbers from the screen's sync. */
    public void setChargeStatsClient(int eu, int players) {
        chargedLastSecond = eu;
        playersLastSecond = players;
    }

    /** The shell colour to the next ready colour (all three colours follow it). */
    public void cycleColor() {
        int next = 0;
        for (int i = 0; i < PRESETS.length; i++) {
            if (PRESETS[i] == rgb[RGB_SHELL]) {
                next = (i + 1) % PRESETS.length;
            }
        }
        for (int i = 0; i < RGB_TARGETS; i++) {
            rgb[i] = PRESETS[next];
        }
        changed();
    }

    public boolean isRedstoneOff() {
        return redstoneOff;
    }

    public boolean isRainShield() {
        return rainShield;
    }

    /**
     * Rain (or snow) is falling somewhere on the zone - from the world's weather, not the client's
     * faded copy. Five spots are asked: the zone's middle and the middles of its four sides (a
     * field reaching from a desert into plains gets wet too); any wet biome among them counts.
     */
    public boolean rainOverField() {
        if (worldObj == null || !worldObj.getWorldInfo().isRaining()) {
            return false;
        }
        int cx = xCoord, cz = zCoord, x0 = xCoord, x1 = xCoord, z0 = zCoord, z1 = zCoord;
        if (master && !nodePositions.isEmpty()) {
            AxisAlignedBB b = zoneBounds();
            x0 = net.minecraft.util.MathHelper.floor_double(b.minX);
            x1 = net.minecraft.util.MathHelper.floor_double(b.maxX);
            z0 = net.minecraft.util.MathHelper.floor_double(b.minZ);
            z1 = net.minecraft.util.MathHelper.floor_double(b.maxZ);
            cx = (x0 + x1) >> 1;
            cz = (z0 + z1) >> 1;
        }
        int[][] spots = {{cx, cz}, {x0, cz}, {x1, cz}, {cx, z0}, {cx, z1}};
        for (int[] s : spots) {
            net.minecraft.world.biome.BiomeGenBase b = worldObj.getBiomeGenForCoords(s[0], s[1]);
            if (b != null && (b.canSpawnLightningBolt() || b.getEnableSnow())) {
                return true;
            }
        }
        return false;
    }

    /** What the rain shield adds to the upkeep right now (0 when it's off or dry). */
    public int rainExtraPerTick() {
        if (!has(F_RAIN) || !rainOverField()) {
            return 0;
        }
        return upkeepPerTick() * (worldObj.getWorldInfo().isThundering() ? THUNDER_PCT : RAIN_PCT) / 100;
    }

    /**
     * Stops radiation round a point for a second: the first up field with its radiation shield on
     * round it that can pay takes the EU. false: no such field, or none could pay.
     */
    public static boolean payRadiationAt(World world, EntityPlayer p, double x, double y, double z, int eu) {
        for (TileEntityFieldGeneratorSC f : activeFieldsIn(world)) {
            if (f.has(F_RADIATION) && f.allowed(p) && f.fieldContains(x, y, z) && f.payRadiation(eu)) {
                return true;
            }
        }
        return false;
    }

    /** Pays for stopping radiation round a player this second; false (nothing taken) when the buffer can't. */
    public boolean payRadiation(int eu) {
        if (eu <= 0) {
            return true;
        }
        if (getEnergyStored() < eu) {
            return false;
        }
        removeEnergy(eu);
        markDirty();
        return true;
    }

    /** A shielded field over this point, or null (lightning, snow placed by players). */
    public static TileEntityFieldGeneratorSC rainShieldAt(World world, double x, double y, double z) {
        for (TileEntityFieldGeneratorSC f : activeFieldsIn(world)) {
            if (f.rainShield && (f.fieldContains(x, y, z) || f.fieldContains(x, y - 1, z) || f.fieldContains(x, y + 1, z))) {
                return f;
            }
        }
        return null;
    }

    /** A player put snow or ice in this column: it's theirs, the shield leaves it. */
    public void playerPlacedAt(int x, int z) {
        int i = snowColumn(x, z);
        if (i >= 0 && clearCols != null) {
            clearCols.clear(i);
            seenCols.set(i);
        }
    }

    private int snowColumn(int x, int z) {
        if (snowBox == null) {
            return -1;
        }
        int x0 = net.minecraft.util.MathHelper.floor_double(snowBox.minX), z0 = net.minecraft.util.MathHelper.floor_double(snowBox.minZ);
        int w = net.minecraft.util.MathHelper.floor_double(snowBox.maxX) - x0 + 1, d = net.minecraft.util.MathHelper.floor_double(snowBox.maxZ) - z0 + 1;
        return x < x0 || z < z0 || x >= x0 + w || z >= z0 + d ? -1 : (z - z0) * w + (x - x0);
    }

    /**
     * No new snow or ice under the shield: the zone's columns are swept, 64 a tick; a column seen
     * clear that has snow on top (or ice on its water) now got it from the weather, and loses it.
     * Snow and ice that were there before - or that a player put down - stay.
     */
    private void keepSnowOff() {
        AxisAlignedBB box = zoneBounds();
        int x0 = net.minecraft.util.MathHelper.floor_double(box.minX), z0 = net.minecraft.util.MathHelper.floor_double(box.minZ);
        int w = net.minecraft.util.MathHelper.floor_double(box.maxX) - x0 + 1, d = net.minecraft.util.MathHelper.floor_double(box.maxZ) - z0 + 1;
        if (w <= 0 || d <= 0 || (long) w * d > (1 << 18)) {
            return;
        }
        if (snowBox == null || !snowBox.toString().equals(box.toString())) {
            snowBox = box;
            clearCols = new java.util.BitSet(w * d);
            seenCols = new java.util.BitSet(w * d);
            waterCols = new java.util.BitSet(w * d);
            topCols = new int[w * d];
            snowSweep = 0;
        }
        for (int k = 0; k < Math.min(64, w * d); k++) {
            int i = snowSweep;
            snowSweep = (snowSweep + 1) % (w * d);
            int x = x0 + i % w, z = z0 + i / w;
            if (!worldObj.blockExists(x, 64, z)) {
                continue;
            }
            int y = worldObj.getPrecipitationHeight(x, z);
            boolean snow = worldObj.getBlock(x, y, z) == net.minecraft.init.Blocks.snow_layer;
            boolean ice = worldObj.getBlock(x, y - 1, z) == net.minecraft.init.Blocks.ice;
            if (!fieldContains(x + 0.5, y + 0.5, z + 0.5) && !fieldContains(x + 0.5, y - 0.5, z + 0.5)) {
                continue;
            }
            if (!snow && !ice) {
                clearCols.set(i);
                topCols[i] = y;
                waterCols.set(i, worldObj.getBlock(x, y - 1, z).getMaterial() == net.minecraft.block.material.Material.water);
            } else if (!seenCols.get(i) || !clearCols.get(i)) {
                // there before we looked, or put there by a player: left alone
            } else if (topCols[i] != y) {
                clearCols.clear(i);                        // the column was built on: whatever is on top is the player's
            } else if (snow) {
                worldObj.setBlockToAir(x, y, z);
            } else if (waterCols.get(i)) {
                worldObj.setBlock(x, y - 1, z, net.minecraft.init.Blocks.water);   // only water that froze turns back
            } else {
                clearCols.clear(i);
            }
            seenCols.set(i);
        }
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

    @Override
    public boolean canItemCharge(EntityPlayer p) {
        if (!allowed(p)) {
            p.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation("sc.field.private", getOwner()));
            return false;
        }
        return super.canItemCharge(p);
    }

    /** Sneak-click charging keeps what wireless charging keeps: two seconds of upkeep and the set reserve. */
    @Override
    public int extractForItemCharging(int max) {
        if (!master) {
            return super.extractForItemCharging(max);     // a member's buffer is dead weight (only the master pays upkeep)
        }
        int spare = Math.min(getEnergyStored() - upkeepPerTick() * 40,
                getEnergyStored() - (int) ((long) getMaxEnergyStored() * chargeReserve / 100));
        return super.extractForItemCharging(Math.min(max, spare));
    }

    /** The owner, anyone on the access list - and everyone while the field has no owner. */
    public boolean allowed(EntityPlayer p) {
        return owner.isEmpty() || isOwner(p) || access.contains(p.getCommandSenderName().toLowerCase(java.util.Locale.ROOT));
    }

    /** allowed() by name - for a quarry working in the field on its owner's behalf. */
    public boolean allowedName(String name) {
        String n = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        return owner.isEmpty() || owner.equalsIgnoreCase(n) || access.contains(n);
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

    /**
     * What creature the field pushes out (and keeps from spawning): hostile ones only, or every
     * creature - animals, villagers and golems too. The pets of the owner and of the access list stay;
     * anyone else's pet is a creature like any other. Players are handled by pushesPlayer().
     */
    public boolean targets(Entity e) {
        if (!(e instanceof EntityLiving)) {
            return false;
        }
        if (e instanceof net.minecraft.entity.IEntityOwnable) {
            String tamer = ((net.minecraft.entity.IEntityOwnable) e).func_152113_b();
            // 1.7.10 keeps the tamer's UUID here, not the name - look the name up (the access list holds names)
            if (tamer != null && !tamer.isEmpty() && (allowedName(tamer) || allowedName(tamerName(tamer)))) {
                return false;                       // our own side's pet
            }
        }
        if (filter == FILTER_HOSTILE) {
            return e instanceof IMob;
        }
        return true;
    }

    /** A pet owner's name from the server's profile cache by the UUID the pet keeps, or null. */
    private static String tamerName(String uuid) {
        net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
        if (server == null) {
            return null;
        }
        try {
            com.mojang.authlib.GameProfile g = server.func_152358_ax().func_152652_a(java.util.UUID.fromString(uuid));
            return g == null ? null : g.getName();
        } catch (IllegalArgumentException e) {
            return null;                            // an old save's name, not a UUID
        }
    }

    /** A player the field pushes out: a stranger to a private field set to push them (the Access tab). */
    public boolean pushesPlayer(EntityPlayer p) {
        return !allowed(p) && !p.capabilities.isCreativeMode && has(F_PRIVATE) && has(F_PUSH_PLAYERS);
    }

    /** A new master (unlink) takes the old one's settings along with its shape. */
    private void copySettings(TileEntityFieldGeneratorSC from) {
        flags = from.flags;
        redstone = from.redstone;
        filter = from.filter;
        copyZone(from);
        chargeMode = from.chargeMode;
        chargeReserve = from.chargeReserve;
        chargeBelow = from.chargeBelow;
        owner = from.owner;
        access.clear();
        access.addAll(from.access);
        powerOn = from.powerOn;                     // a switched-off field stays off
        batteryMode = from.batteryMode;
    }

    private void copyZone(TileEntityFieldGeneratorSC from) {
        height = from.height;
        offX = from.offX;
        offY = from.offY;
        offZ = from.offZ;
        anchor = from.anchor;
        anchorPoint = from.anchorPoint == null ? null : from.anchorPoint.clone();
        outline = from.outline;
        anim = from.anim;
        brightness = from.brightness;
        System.arraycopy(from.rgb, 0, rgb, 0, RGB_TARGETS);
        zoneCache = null;
    }

    private void writeZone(NBTTagCompound nbt) {
        nbt.setInteger("Height", height);
        nbt.setInteger("OffX", offX);
        nbt.setInteger("OffY", offY);
        nbt.setInteger("OffZ", offZ);
        nbt.setInteger("Anchor", anchor);
        if (anchorPoint != null) {
            nbt.setIntArray("AnchorPoint", anchorPoint);
        }
        nbt.setInteger("Outline", outline);
        nbt.setInteger("Anim", anim);
        nbt.setInteger("Bright", brightness);
        nbt.setIntArray("RGB", rgb.clone());
    }

    /** Zone and looks; a field saved before them keeps its old look (its shell colour for all three). */
    private void readZone(NBTTagCompound nbt) {
        height = nbt.getInteger("Height") <= 0 ? 0 : FieldShapeSC.clampRange(nbt.getInteger("Height"));
        offX = clampOffset(nbt.getInteger("OffX"));
        offY = clampOffset(nbt.getInteger("OffY"));
        offZ = clampOffset(nbt.getInteger("OffZ"));
        anchor = Math.max(0, Math.min(ANCHORS - 1, nbt.getInteger("Anchor")));
        int[] ap = nbt.getIntArray("AnchorPoint");
        anchorPoint = ap.length == 3 ? ap : null;
        outline = Math.max(0, Math.min(OUTLINES - 1, nbt.getInteger("Outline")));
        anim = Math.max(0, Math.min(ANIMS - 1, nbt.getInteger("Anim")));
        int b = nbt.hasKey("Bright") ? nbt.getInteger("Bright") : 100;
        brightness = Math.max(BRIGHT_STEP, Math.min(100, b / BRIGHT_STEP * BRIGHT_STEP));
        int[] c = nbt.getIntArray("RGB");
        if (c.length == RGB_TARGETS) {
            for (int i = 0; i < RGB_TARGETS; i++) {
                rgb[i] = c[i] & 0xFFFFFF;
            }
        } else if (nbt.hasKey("Color")) {
            float[] old = COLORS[Math.max(0, Math.min(COLORS.length - 1, nbt.getInteger("Color")))];
            int v = (int) (old[0] * 255) << 16 | (int) (old[1] * 255) << 8 | (int) (old[2] * 255);
            for (int i = 0; i < RGB_TARGETS; i++) {
                rgb[i] = v;
            }
        }
        zoneCache = null;
    }

    /** The Quantum Wrench's copy: shape, range and switches - not the owner, not the access list. */
    public void exportSettings(NBTTagCompound nbt) {
        nbt.setInteger("Mode", mode.ordinal());
        nbt.setInteger("Range", range);
        nbt.setInteger("Flags", flags);
        nbt.setInteger("Redstone", redstone);
        nbt.setInteger("Filter", filter);
        nbt.setInteger("ChargeMode", chargeMode);
        nbt.setInteger("ChargeReserve", chargeReserve);
        nbt.setInteger("ChargeBelow", chargeBelow);
        writeZone(nbt);
    }

    /** The Quantum Wrench's paste (the caller checked allowed()); a zone over a stranger's field isn't taken. */
    public void importSettings(NBTTagCompound nbt) {
        int[] before = saveShape();
        int[] point = anchorPoint;
        applySettings(nbt);
        keepShape(before, point);
        changed();
    }

    private void applySettings(NBTTagCompound nbt) {
        mode = FieldMode.values()[Math.min(FieldMode.values().length - 1, Math.max(0, nbt.getInteger("Mode")))];
        range = FieldShapeSC.clampRange(nbt.getInteger("Range"));
        flags = nbt.getInteger("Flags");
        if (!nbt.hasKey("ChargeMode")) {
            flags |= F_CHARGE_FX;                  // copied before the charging settings: sparks stay on
        }
        redstone = Math.max(0, Math.min(2, nbt.getInteger("Redstone")));
        filter = loadFilter(nbt.getInteger("Filter"));
        chargeMode = Math.max(0, Math.min(CHARGE_MODES - 1, nbt.getInteger("ChargeMode")));
        chargeReserve = Math.max(0, Math.min(RESERVE_MAX, nbt.getInteger("ChargeReserve")));
        chargeBelow = nbt.hasKey("ChargeBelow") ? Math.max(CHARGE_BELOW_MIN, Math.min(100, nbt.getInteger("ChargeBelow"))) : 100;
        readZone(nbt);
        if (anchor == ANCHOR_POINT && (anchorPoint == null || Math.abs(anchorPoint[0] - xCoord) > MAX_LINK_DISTANCE
                || Math.abs(anchorPoint[1] - yCoord) > MAX_LINK_DISTANCE || Math.abs(anchorPoint[2] - zCoord) > MAX_LINK_DISTANCE)) {
            anchor = ANCHOR_NODES;                 // another field's point doesn't carry over
            anchorPoint = null;
        }
        changed();
    }

    // ---- the item keeps the charge and the settings (broken / dismantled) ----

    /**
     * The charge (up to the bare buffer: the upgrades drop as items, so what they held has nowhere
     * to go) and - a master's - the settings, the power switch, the battery mode and the access
     * list. Not the cluster's links: placed again it starts alone. Not the upgrades or the battery
     * (they drop from breakBlock - never both).
     */
    public NBTTagCompound writeToItem() {
        NBTTagCompound nbt = new NBTTagCompound();
        int e = Math.min(getEnergyStored(), getTier().getBuffer());
        if (e > 0) {
            nbt.setInteger("EnergySC", e);
        }
        if (master) {
            NBTTagCompound s = new NBTTagCompound();
            exportSettings(s);
            s.setBoolean("PowerOff", !powerOn);
            s.setInteger("BatteryMode", batteryMode);
            NBTTagList names = new NBTTagList();
            for (String n : access) {
                names.appendTag(new net.minecraft.nbt.NBTTagString(n));
            }
            s.setTag("Access", names);
            nbt.setTag("FieldSC", s);
        }
        return nbt;
    }

    /** Placed from an item with writeToItem()'s data (the block then makes the placer the owner). */
    public void readFromItem(NBTTagCompound nbt) {
        restoreEnergy(Math.min(nbt.getInteger("EnergySC"), getTier().getBuffer()));
        if (nbt.hasKey("FieldSC")) {
            NBTTagCompound s = nbt.getCompoundTag("FieldSC");
            applySettings(s);
            powerOn = !s.getBoolean("PowerOff");
            batteryMode = Math.max(0, Math.min(com.sc.item.BatteryFeedSC.MODES - 1, s.getInteger("BatteryMode")));
            access.clear();
            NBTTagList names = s.getTagList("Access", 8);
            for (int i = 0; i < names.tagCount() && access.size() < MAX_ACCESS; i++) {
                String n = names.getStringTagAt(i).toLowerCase(java.util.Locale.ROOT);
                if (!n.isEmpty() && !access.contains(n)) {
                    access.add(n);
                }
            }
        }
        changed();
    }

    /** The new owner from placing: off their own access list (the owner never is on it). */
    public void setPlacer(String name) {
        setOwner(name);
        access.remove(name == null ? "" : name.toLowerCase(java.util.Locale.ROOT));
        changed();
    }

    /**
     * A private field keeps strangers' hands off what lives or hangs inside it: hitting
     * (AttackEntityEvent - item frames and paintings break that way too) or right-clicking
     * (EntityInteractEvent) an animal or villager, a golem, a cart or boat, a frame or a painting.
     * Players and hostile mobs aren't covered. Server side; the stranger is told (as with blocks).
     * @return true if the event must be cancelled
     */
    public static boolean guardsEntity(EntityPlayer p, Entity target) {
        if (p == null || target == null || target.worldObj == null || target.worldObj.isRemote) {
            return false;
        }
        if (!(target instanceof net.minecraft.entity.EntityHanging || target instanceof net.minecraft.entity.EntityAgeable
                || target instanceof net.minecraft.entity.monster.EntityGolem || target instanceof net.minecraft.entity.item.EntityMinecart
                || target instanceof net.minecraft.entity.item.EntityBoat)) {
            return false;
        }
        return com.sc.ShieldEventHandler.privateFor(target.worldObj, p, net.minecraft.util.MathHelper.floor_double(target.posX),
                net.minecraft.util.MathHelper.floor_double(target.posY + target.height / 2), net.minecraft.util.MathHelper.floor_double(target.posZ));
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
            int[] before = saveShape();
            range = next;
            keepShape(before, anchorPoint);
            changed();
        }
    }

    public void cycleMode() {
        int[] before = saveShape();
        mode = mode.next();
        keepShape(before, anchorPoint);
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
        return upkeepFor(getNodeCount(), range, height, mode) + com.sc.util.ConfigSC.scale(extrasPerTick(), com.sc.util.ConfigSC.fieldUpkeep, 0);
    }

    /** What the switched-on protections add to the upkeep (charging and healing are paid as used). */
    public int extrasPerTick() {
        return (has(F_NO_SPAWN) ? NO_SPAWN_EU : 0) + (has(F_NO_ENDER) ? NO_ENDER_EU : 0)
                + (has(F_PRIVATE) ? PRIVATE_EU : 0) + (has(F_PRIVATE) && has(F_PUSH_PLAYERS) ? PUSH_PLAYERS_EU : 0);
    }

    public static int upkeepFor(int nodes, int range, FieldMode mode) {
        return upkeepFor(nodes, range, 0, mode);
    }

    /** With a height of its own the reach costs by (2 x range + height) / 3 - the same as before when height = range. */
    public static int upkeepFor(int nodes, int range, int height, FieldMode mode) {
        double reach = (2.0 * range + FieldShapeSC.heightOf(range, height)) / 3.0;
        return (int) Math.round((BASE_EU_PER_FACE * 6 * nodes + RANGE_EU_PER_BLOCK * reach) * mode.costMultiplier
                * com.sc.util.ConfigSC.fieldUpkeep);
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
        zoneCache = null;
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
        // the bigger cluster's zone mustn't reach over a stranger's field (as a power-on / a new zone)
        List<int[]> had = new ArrayList<int[]>(masterTe.nodePositions);
        for (int[] pos : incoming) {
            if (!containsPos(masterTe.nodePositions, pos)) {
                masterTe.nodePositions.add(pos);
            }
        }
        masterTe.zoneCache = null;
        TileEntityFieldGeneratorSC stranger = masterTe.foreignOverlap(joiningMaster);
        masterTe.nodePositions.clear();
        masterTe.nodePositions.addAll(had);
        masterTe.zoneCache = null;
        if (stranger != null) {
            if (player != null) {
                player.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation("sc.field.foreign", stranger.getOwner()));
            }
            return LinkResult.FOREIGN;
        }
        int[] masterCoord = {masterTe.xCoord, masterTe.yCoord, masterTe.zCoord};
        for (int[] pos : incoming) {
            if (!containsPos(masterTe.nodePositions, pos)) {
                masterTe.nodePositions.add(pos);
            }
            TileEntityFieldGeneratorSC member = fieldGeneratorAt(world, pos);
            if (member != null && member != masterTe) {  // a stale list naming the master itself: it stays master
                member.master = false;
                member.masterPos = masterCoord.clone();
                member.nodePositions.clear();
                member.active = false;
                member.copySettings(masterTe);          // 6: an orphan later re-elected keeps the cluster's owner, not its own old one
                // A member's own buffer is dead weight (only the master pays upkeep) - hand it over, taken
                // out first: its storage upgrades held it, and moving them clamps the member's buffer.
                int carried = member.removeEnergy(member.getEnergyStored());
                member.handUpgradesTo(masterTe);        // a member has no screen - its upgrades move to the master
                int room = masterTe.getMaxEnergyStored() - masterTe.getEnergyStored();
                int given = Math.max(0, Math.min(room, carried));
                masterTe.addEnergy(given);
                member.addEnergy(carried - given);      // what the master can't take stays in the node (was lost)
                member.changed();
            }
        }
        masterTe.changed();
        return LinkResult.LINKED;
    }

    public enum LinkResult { LINKED, INVALID, TOO_FAR, SAME_CLUSTER, NODE_CAP, UNLOADED, NO_ACCESS, FOREIGN }

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
            return powerOn ? super.demandedEnergy() : 0;
        }
        TileEntityFieldGeneratorSC m = loadedMaster();
        return m == null ? 0 : m.demandedEnergy();
    }

    @Override
    public int receiveEnergy(net.minecraftforge.common.util.ForgeDirection from, int voltage, int amount, boolean simulate) {
        if (master) {
            return powerOn ? super.receiveEnergy(from, voltage, amount, simulate) : 0;
        }
        TileEntityFieldGeneratorSC m = loadedMaster();
        return m == null ? 0 : m.receiveEnergy(from, voltage, amount, simulate);
    }

    /** Energy written into the dropped item by getDrops, this tick: breaking a master hands the cluster only the rest. */
    private int energyDropped;
    private long energyDroppedAt = Long.MIN_VALUE;

    public void markEnergyDropped(int eu) {
        energyDropped = Math.max(0, eu);
        energyDroppedAt = worldObj != null ? worldObj.getTotalWorldTime() : Long.MIN_VALUE;
    }

    private int droppedNow() {
        return worldObj != null && energyDroppedAt == worldObj.getTotalWorldTime() ? energyDropped : 0;
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
        int give = Math.max(0, self.getEnergyStored() - self.droppedNow());           // what didn't go into the item
        newMaster.addEnergy(self.removeEnergy(Math.min(room, give)));   // and its charge
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
        if (worldObj != null && worldObj.isRemote) {
            if (master && active && has(F_HUM)) {
                hum();
            }
            return;
        }
        if (worldObj == null || !master) {
            return; // only the master ticks upkeep/protection for the whole cluster
        }
        if (worldObj.getTotalWorldTime() % 100 == 0) {
            pruneNodes();
        }
        if (feedFromBattery(battery) > 0) {
            markDirty();
        }
        boolean powered = worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord);
        boolean rsOff = redstone == REDSTONE_ON ? !powered : redstone == REDSTONE_OFF && powered;
        if (rsOff != redstoneOff) {
            redstoneOff = rsOff;
            changed();
        }
        rsOff |= !powerOn;                           // switched off: down as with redstone
        int upkeep = upkeepPerTick();
        boolean wasActive = active;
        // down, it restarts only with a second's upkeep in hand - else a trickle of power flicked it on and off
        // (never more than the buffer holds - a big field under a high fieldUpkeep could never come up again)
        if (!rsOff && getEnergyStored() >= (active ? upkeep : (int) Math.min(upkeep * 20L, getMaxEnergyStored()))) {
            removeEnergy(upkeep);
            active = true;
            if (!wasActive) {
                foreignNear = false;                // up again: an old refusal is past (changed() below sends it)
            }
            // the rain shield: paid on top, and the first thing to go when the energy runs short
                boolean wet = has(F_RAIN) && rainOverField();
            int rainEu = wet ? upkeep * (worldObj.getWorldInfo().isThundering() ? THUNDER_PCT : RAIN_PCT) / 100 : 0;
            // back on only with a second's worth in hand - else a trickle flicked it every few ticks
            boolean shield = rainEu > 0 && getEnergyStored() >= (rainShield ? rainEu : (int) Math.min(rainEu * 20L, getMaxEnergyStored()));
            if (shield) {
                removeEnergy(rainEu);
            }
            if (shield || has(F_RAIN) && !wet) {
                keepSnowOff();                      // water freezes in dry cold weather too
            }
            if (shield != rainShield) {
                rainShield = shield;
                changed();
            }
            // A big field spans hundreds of chunks - scan it every 4th tick instead of every tick
            // (projectiles cover ~3 blocks a tick, well inside even the smallest such field).
            if (range <= 32 || worldObj.getTotalWorldTime() % 4 == 0) {
                protectRegion();
            }
            if (worldObj.getTotalWorldTime() % 20 == 0) {
                chargedLastSecond = 0;
                playersLastSecond = 0;
                if (has(F_CHARGE) || has(F_HEAL)) {
                    serveAllies();
                }
            }
            ACTIVE.add(this);
        } else {
            active = false;
            ACTIVE.remove(this);
            chargedLastSecond = 0;
            playersLastSecond = 0;
            if (rainShield) {
                rainShield = false;
                changed();
            }
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

    /** Client: a low hum from each node a player stands near (World.playSound is a no-op on the server). */
    private void hum() {
        long t = worldObj.getTotalWorldTime() + (xCoord * 7 + zCoord * 13 & 63);
        if (t % 70 != 0) {
            return;
        }
        for (int[] n : nodePositions) {
            if (worldObj.getClosestPlayer(n[0] + 0.5, n[1] + 0.5, n[2] + 0.5, 20) != null) {
                worldObj.playSound(n[0] + 0.5, n[1] + 0.5, n[2] + 0.5, "portal.portal", 0.07F,
                        0.45F + worldObj.rand.nextFloat() * 0.05F, false);
            }
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
        AxisAlignedBB box = zoneBounds();
        for (Object o : worldObj.getEntitiesWithinAABB(EntityPlayer.class, box)) {
            EntityPlayer p = (EntityPlayer) o;
            if (!allowed(p) || p.isDead || !fieldContains(p.posX, p.posY + 1, p.posZ)) {
                continue;
            }
            // charging keeps two seconds of upkeep and the set reserve in the buffer
            int spare = Math.min(getEnergyStored() - upkeepPerTick() * 40,
                    getEnergyStored() - (int) ((long) getMaxEnergyStored() * chargeReserve / 100));
            if (has(F_CHARGE) && spare > 0) {
                int used = chargePlayer(p, spare);
                if (used > 0) {
                    chargedLastSecond += used;
                    playersLastSecond++;
                    if (has(F_CHARGE_FX)) {
                        sparks(p);
                    }
                }
            }
            if (has(F_HEAL) && p.getHealth() < p.getMaxHealth() && getEnergyStored() - upkeepPerTick() * 40 >= HEAL_COST
                    && pay(HEAL_COST)) {
                p.heal(1.0F);
            }
        }
    }

    /**
     * One player's items, as the charge mode says, out of `spare` EU: a player's rate a second in all
     * - or, with "full rate for each item" and a booster, that rate for every item being charged.
     * Items at or above the threshold, and batteries when they're left out, are skipped.
     * @return EU used
     */
    private int chargePlayer(EntityPlayer p, int spare) {
        ItemStack held = p.isUsingItem() ? null : p.getCurrentEquippedItem();   // a blocking blade keeps its block
        List<ItemStack> order = new ArrayList<ItemStack>();
        if (chargeMode == CHARGE_HELD_FIRST && held != null) {
            order.add(held);
        }
        for (ItemStack s : p.inventory.armorInventory) {
            if (s != null) {
                order.add(s);
            }
        }
        if (chargeMode != CHARGE_ARMOR_ONLY && chargeMode != CHARGE_HELD_FIRST && held != null) {
            order.add(held);
        }
        if (chargeMode != CHARGE_ARMOR_ONLY && chargeMode != CHARGE_WORN_ONLY) {
            for (ItemStack s : p.inventory.mainInventory) {
                if (s != null && s != held && s != p.getCurrentEquippedItem()) {
                    order.add(s);
                }
            }
        }
        final java.util.Map<ItemStack, Float> level = new java.util.IdentityHashMap<ItemStack, Float>();
        List<ItemStack> todo = new ArrayList<ItemStack>();
        for (ItemStack s : order) {
            float f = chargeLevel(s);
            if (f * 100 >= chargeBelow || has(F_SKIP_BATTERIES) && isBattery(s)) {
                continue;
            }
            level.put(s, f);
            todo.add(s);
        }
        if (todo.isEmpty()) {
            return 0;
        }
        if (chargeMode == CHARGE_LOWEST_FIRST || chargeMode == CHARGE_FULLEST_FIRST) {
            final int sign = chargeMode == CHARGE_LOWEST_FIRST ? 1 : -1;
            java.util.Collections.sort(todo, new java.util.Comparator<ItemStack>() {
                @Override
                public int compare(ItemStack a, ItemStack b) {
                    return sign * Float.compare(level.get(a), level.get(b));
                }
            });
        }
        boolean each = chargesEachAtFullRate();
        int rate = chargeRate();
        int budget = (int) Math.min(spare, each ? (long) rate * todo.size() : rate);
        int used = 0;
        if (chargeMode == CHARGE_EVEN) {
            // everything at once: equal shares of what's left among the items still taking it
            List<ItemStack> active = new ArrayList<ItemStack>(todo);
            java.util.Map<ItemStack, Integer> got = new java.util.IdentityHashMap<ItemStack, Integer>();
            for (int round = 0; round < 4 && !active.isEmpty() && used < budget; round++) {
                int share = Math.max(1, (budget - used) / active.size());
                for (java.util.Iterator<ItemStack> it = active.iterator(); it.hasNext() && used < budget; ) {
                    ItemStack s = it.next();
                    int already = got.containsKey(s) ? got.get(s) : 0;
                    int want = Math.min(share, budget - used);
                    if (each) {
                        want = Math.min(want, rate - already);
                    }
                    int took = want > 0 ? chargeInto(p, s, want) : 0;
                    used += took;
                    got.put(s, already + took);
                    if (took < want || each && already + took >= rate) {
                        it.remove();                // full, not chargeable, or at its own rate
                    }
                }
            }
            return used;
        }
        for (ItemStack s : todo) {                        // one after another, in order
            if (used >= budget) {
                break;
            }
            used += chargeInto(p, s, each ? Math.min(rate, budget - used) : budget - used);
        }
        return used;
    }

    /** How charged an item is, 0..1, from its durability bar (the mod's gear, IC2's and RF items show theirs so); 0 if none. */
    private static float chargeLevel(ItemStack s) {
        if (s == null || s.getItem() == null || !s.getItem().showDurabilityBar(s)) {
            return 0F;
        }
        return (float) Math.max(0, Math.min(1, 1 - s.getItem().getDurabilityForDisplay(s)));
    }

    /** An IC2 battery or energy crystal: an electric item that can give its energy away. */
    private static boolean isBattery(ItemStack s) {
        return com.sc.item.ItemChargeSC.isBattery(s);
    }

    /** Sparks in the field's colour from the nearest node to the player being charged. */
    private void sparks(EntityPlayer p) {
        if (!(worldObj instanceof net.minecraft.world.WorldServer) || nodePositions.isEmpty()) {
            return;
        }
        int[] from = null;
        double best = Double.MAX_VALUE;
        for (int[] n : nodePositions) {
            double d = p.getDistanceSq(n[0] + 0.5, n[1] + 0.5, n[2] + 0.5);
            if (d < best) {
                best = d;
                from = n;
            }
        }
        float[] c = rgbF(RGB_FLASH);
        net.minecraft.world.WorldServer ws = (net.minecraft.world.WorldServer) worldObj;
        double sx = from[0] + 0.5, sy = from[1] + 1.1, sz = from[2] + 0.5;
        double tx = p.posX, ty = p.boundingBox.minY + p.height * 0.55, tz = p.posZ;
        int points = Math.max(4, Math.min(24, (int) Math.sqrt(best)));
        for (int i = 0; i <= points; i++) {
            double k = (double) i / points;
            double wob = Math.sin(k * Math.PI) * 0.35 * (worldObj.rand.nextDouble() - 0.5);
            // reddust with no count: the "velocity" is its colour
            ws.func_147487_a("reddust", sx + (tx - sx) * k + wob, sy + (ty - sy) * k + wob, sz + (tz - sz) * k + wob,
                    0, Math.max(0.01F, c[0]), c[1], c[2], 1.0);
        }
        ws.func_147487_a("magicCrit", tx, ty, tz, 6, 0.3, 0.5, 0.3, 0.05);
    }

    /** Charges one electric item from the buffer: the mod's own, IC2's (EU) or other mods' RF items. @return EU used */
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
        } else if (com.sc.item.ItemWrenchSC.isElectric(s)) {
            took = com.sc.item.ItemWrenchSC.charge(s, max);
        } else if (com.sc.item.ItemBridgeRemoteSC.isRemote(s)) {
            took = com.sc.item.ItemBridgeRemoteSC.charge(s, max);
        } else if (com.sc.item.ItemBatterySC.isBattery(s)) {
            took = com.sc.item.ItemBatterySC.charge(s, max);
        } else if (cpw.mods.fml.common.Loader.isModLoaded(com.sc.Reference.IC2_MODID) && Ic2Charge.is(s)) {
            took = Ic2Charge.charge(s, max);
        } else {
            took = RfCharge.charge(s, max);
        }
        took = Math.max(0, Math.min(max, took));
        removeEnergy(took);
        return took;
    }

    /** Kept apart so IC2's API is only loaded when IC2 is (any tier: the field is the one limiting the rate). */
    private static final class Ic2Charge {
        static boolean is(ItemStack s) {
            return s.getItem() instanceof ic2.api.item.IElectricItem && ic2.api.item.ElectricItem.manager != null;
        }

        static int charge(ItemStack s, int max) {
            return (int) ic2.api.item.ElectricItem.manager.charge(s, max, Integer.MAX_VALUE, true, false);
        }

        static boolean isBattery(ItemStack s) {
            return s != null && s.getItem() instanceof ic2.api.item.IElectricItem && ((ic2.api.item.IElectricItem) s.getItem()).canProvideEnergy(s);
        }
    }

    /**
     * Redstone Flux items (Thermal Expansion, EnderIO, Draconic Evolution...) through CoFH's
     * IEnergyContainerItem, found by reflection - nothing to link against, nothing breaks without it.
     */
    private static final class RfCharge {
        private static boolean looked;
        private static Class<?> type;
        private static java.lang.reflect.Method receive;

        static int charge(ItemStack s, int maxEu) {
            if (!looked) {
                looked = true;
                try {
                    type = Class.forName("cofh.api.energy.IEnergyContainerItem");
                    receive = type.getMethod("receiveEnergy", ItemStack.class, int.class, boolean.class);
                } catch (Throwable t) {
                    type = null;
                }
            }
            if (type == null || !type.isInstance(s.getItem())) {
                return 0;
            }
            try {
                int maxRf = (int) Math.min(Integer.MAX_VALUE, (long) maxEu * RF_PER_EU);
                int rf = (Integer) receive.invoke(s.getItem(), s, maxRf, false);
                return (rf + RF_PER_EU - 1) / RF_PER_EU;
            } catch (Throwable t) {
                return 0;
            }
        }
    }

    /** A burst of the shell's colour where it stopped something (a projectile, an explosion). */
    public void flash(double x, double y, double z) {
        if (!has(F_SHOW) || !(worldObj instanceof net.minecraft.world.WorldServer)) {
            return;
        }
        float[] c = rgbF(RGB_FLASH);
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
        return FieldShapeSC.contains(mode, zoneNodes(), range, height, x, y, z);
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
        AxisAlignedBB box = zoneBounds();
        // Hostile mobs inside the shape are shoved out and hurt - every IMob, not just EntityMob
        // (ghasts, slimes and magma cubes are IMob without being EntityMob and used to be ignored).
        List<Entity> living = worldObj.getEntitiesWithinAABB(EntityLiving.class, box);
        if (has(F_PRIVATE) && has(F_PUSH_PLAYERS)) {
            for (Object o : worldObj.getEntitiesWithinAABB(EntityPlayer.class, box)) {
                EntityPlayer p = (EntityPlayer) o;
                if (pushesPlayer(p)) {
                    living.add(p);                  // strangers out (not hurt)
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
        for (int[] n : zoneNodes()) {
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

    public static boolean isFieldUpgrade(ItemStack s) {
        if (s == null || !(s.getItem() instanceof com.sc.item.ItemUpgradeSC)) {
            return false;
        }
        com.sc.machine.UpgradeType t = com.sc.item.ItemUpgradeSC.typeOf(s);
        return t == com.sc.machine.UpgradeType.ENERGY_STORAGE || t == com.sc.machine.UpgradeType.TRANSFORMER
                || t == com.sc.machine.UpgradeType.UNIVERSAL_TRANSFORMER || t == com.sc.machine.UpgradeType.CHARGE_BOOSTER;
    }

    /**
     * Each transformer upgrade takes one tier higher voltage without exploding (HV -> EV, like a
     * machine's). A linked node takes whatever its master takes - it only passes energy on.
     */
    @Override
    public boolean acceptsAnyVoltage() {
        if (!master) {
            TileEntityFieldGeneratorSC m = loadedMaster();
            if (m != null) {
                return m.acceptsAnyVoltage();
            }
        }
        return upgradeCount(com.sc.machine.UpgradeType.UNIVERSAL_TRANSFORMER) > 0;
    }

    @Override
    public Tier inputTier() {
        if (!master) {
            TileEntityFieldGeneratorSC m = loadedMaster();
            if (m != null) {
                return m.inputTier();
            }
        }
        Tier[] tiers = Tier.values();
        if (upgradeCount(com.sc.machine.UpgradeType.UNIVERSAL_TRANSFORMER) > 0) {
            return tiers[tiers.length - 1];             // any voltage
        }
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
        if (battery != null) {                      // the battery too: a node has no screen to take it from
            if (to.battery == null) {
                to.battery = battery;
            } else if (worldObj != null) {
                worldObj.spawnEntityInWorld(new net.minecraft.entity.item.EntityItem(worldObj, xCoord + 0.5, yCoord + 1.2, zCoord + 0.5, battery));
            }
            battery = null;
        }
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

    /** Breaking the block: its upgrades (and battery) drop (BlockFieldGeneratorSC.breakBlock). */
    public void dropUpgrades() {
        if (battery != null && worldObj != null) {
            worldObj.spawnEntityInWorld(new net.minecraft.entity.item.EntityItem(worldObj, xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, battery));
        }
        battery = null;
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            if (upgrades[i] != null && worldObj != null) {
                worldObj.spawnEntityInWorld(new net.minecraft.entity.item.EntityItem(worldObj, xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, upgrades[i]));
            }
            upgrades[i] = null;
        }
    }

    @Override
    public int getSizeInventory() {
        return UPGRADE_SLOTS + 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot == SLOT_BATTERY) {
            return battery;
        }
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
            if (slot == SLOT_BATTERY) {
                battery = null;
            } else {
                upgrades[slot] = null;
            }
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
        if (slot == SLOT_BATTERY) {
            battery = stack;
            markDirty();
            return;
        }
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
        return slot == SLOT_BATTERY ? com.sc.item.BatteryFeedSC.accepts(stack) : isFieldUpgrade(stack);
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
        zoneCache = null;
        markDirty();
        if (worldObj != null && !worldObj.isRemote) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    /**
     * What every client near the block gets: the shape, looks, switches and state - not the access
     * list, the upgrades or the battery ("Desc": readFromNBT leaves the client's copies alone). The
     * screen's container syncs the slots itself and sends the access list to those on it (accessPacket).
     */
    @Override
    public net.minecraft.network.Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        writeToNBT(nbt);
        nbt.removeTag("Access");
        nbt.removeTag("Upgrades");
        nbt.removeTag("Battery");
        nbt.setBoolean("Desc", true);
        return new net.minecraft.network.play.server.S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, nbt);
    }

    /** The access list for one player's screen (ContainerFieldGeneratorSC): the list itself, or an empty one for a stranger. */
    public net.minecraft.network.Packet accessPacket(boolean show) {
        NBTTagCompound nbt = new NBTTagCompound();
        NBTTagList names = new NBTTagList();
        if (show) {
            for (String n : access) {
                names.appendTag(new net.minecraft.nbt.NBTTagString(n));
            }
        }
        nbt.setTag("Access", names);
        nbt.setBoolean("AccessOnly", true);
        return new net.minecraft.network.play.server.S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, nbt);
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.S35PacketUpdateTileEntity pkt) {
        NBTTagCompound nbt = pkt.func_148857_g();
        if (nbt.getBoolean("AccessOnly")) {
            readAccess(nbt);
            return;
        }
        readFromNBT(nbt);
    }

    private void readAccess(NBTTagCompound nbt) {
        access.clear();
        NBTTagList names = nbt.getTagList("Access", 8);
        for (int i = 0; i < names.tagCount(); i++) {
            access.add(names.getStringTagAt(i));
        }
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
        if (!master || nodePositions.isEmpty()) {
            return super.getRenderBoundingBox();
        }
        AxisAlignedBB z = zoneBounds(), n = FieldShapeSC.bounds(FieldMode.BOX, nodePositions, 1);   // the beams run between the nodes
        return AxisAlignedBB.getBoundingBox(Math.min(z.minX, n.minX), Math.min(z.minY, n.minY), Math.min(z.minZ, n.minZ),
                Math.max(z.maxX, n.maxX), Math.max(z.maxY, n.maxY), Math.max(z.maxZ, n.maxZ));
    }

    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public double getMaxRenderDistanceSquared() {
        double d = 128 + range + MAX_OFFSET;         // the shell is visible from as far as it reaches
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
        if (nbt.hasKey("Flags") && !nbt.hasKey("ChargeMode")) {
            flags |= F_CHARGE_FX;                  // saved before the charging settings: sparks on
        }
        chargeMode = Math.max(0, Math.min(CHARGE_MODES - 1, nbt.getInteger("ChargeMode")));
        chargeReserve = Math.max(0, Math.min(RESERVE_MAX, nbt.getInteger("ChargeReserve")));
        chargeBelow = nbt.hasKey("ChargeBelow") ? Math.max(CHARGE_BELOW_MIN, Math.min(100, nbt.getInteger("ChargeBelow"))) : 100;
        redstone = nbt.hasKey("Redstone") ? Math.max(0, Math.min(2, nbt.getInteger("Redstone"))) : REDSTONE_OFF;
        filter = loadFilter(nbt.getInteger("Filter"));
        readZone(nbt);
        owner = nbt.getString("Owner");
        redstoneOff = nbt.getBoolean("RedstoneOff");
        rainShield = nbt.getBoolean("RainShield");
        foreignNear = nbt.getBoolean("ForeignNear");
        if (nbt.getBoolean("Desc")) {
            return;                                 // a client's copy: the slots and the list come from the screen
        }
        battery = nbt.hasKey("Battery") ? ItemStack.loadItemStackFromNBT(nbt.getCompoundTag("Battery")) : null;
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
        readAccess(nbt);
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
        nbt.setInteger("ChargeMode", chargeMode);
        nbt.setInteger("ChargeReserve", chargeReserve);
        nbt.setInteger("ChargeBelow", chargeBelow);
        nbt.setInteger("Redstone", redstone);
        nbt.setInteger("Filter", filter);
        writeZone(nbt);
        nbt.setString("Owner", owner);
        nbt.setBoolean("RedstoneOff", redstoneOff);
        nbt.setBoolean("RainShield", rainShield);
        nbt.setBoolean("ForeignNear", foreignNear);
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
        if (battery != null) {
            nbt.setTag("Battery", battery.writeToNBT(new NBTTagCompound()));
        }
    }
}
