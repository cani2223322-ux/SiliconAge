package com.sc.item;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.sc.init.ModItems;
import com.sc.util.ArmorGasSC;
import com.sc.util.ArmorSuit;
import com.sc.util.DrillFeature;
import com.sc.util.DrillType;
import com.sc.util.DrillZoneSC;
import com.sc.util.InvUtilSC;
import com.sc.util.ToolGasSC;
import com.sc.util.ToolLevelSC;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryLargeChest;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.oredict.OreDictionary;

/**
 * What the drills do, on the server. The drill pays per block from its own small battery, then -
 * worn energy armour - from the chestplate (any suit). Set bonuses (the drill with the full suit
 * of its own tier):
 * - Nano: a quarter less EU per block,
 * - Quantum: fortune one level higher,
 * - Exo: the area / tunnel / vein / laser blocks beyond the first cost half.
 * The Singular drill counts as Exo for every Exo rule (with the full Singular suit as its set), and its
 * Exo-era functions (the area / tunnel / vein / laser blocks beyond the first) cost ToolLevelSC.LEGACY_MUL of their EU.
 * The drill breaks blocks itself (onBlockStartBreak): area, vein, tunnel, silk touch / fortune up
 * to V, autosmelt, magnet, the linked chest. Blocks with a tile entity are left to vanilla (their
 * own drop rules) and never taken by the area modes; neither is anything unbreakable or liquid.
 *
 * The Singular drill (docs/plan-singular-tools.md §3) on top: its level opens its own functions (unlocked) - the
 * gravitational funnel (a 7x7 / 9x9 / 11x11 face, argon a block), the cross link (the linked chest in any loaded
 * dimension, SM a stack), drain (water / lava in and around the dug zone removed - the armour has no water / lava
 * tanks, so it is just gone), replace (the block in the hotbar slot right of the drill goes where one was dug), the
 * phase dig (its key: the ore looked at through rock) - and the black hole mode (Shift + wheel: cycleMode, sizes and
 * the depth 1 / 3 in one cycle; the depth alone: Shift + right-click in the air, toggleDepth; its key: the mode
 * off / on, back to the last zone, ItemDrillSC.setEnabled): everything in the zone destroyed, no drops, no XP,
 * singularity crumbs for the natural blocks instead. Its gases come from the worn armour (ToolGasSC);
 * 1 level point per BLOCKS_PER_POINT blocks it digs in any mode. Branches: Miner (lv 3: +25% speed, no 12x12
 * cooldown; lv 5: an 11x11 funnel), Prospector (lv 3: the phase dig reaches 12; lv 5: fortune VI, veins up to 256).
 * Full Singular suit: the drill's heat goes into the suit (its helium loop), cooldowns -25% (ToolLevelSC).
 * Every area dig keeps the rules: loaded chunks only, spawn protection, a BreakEvent per block (claims, private
 * fields), no tile-entity blocks, nothing unbreakable.
 */
public final class DrillLogicSC {

    private static final String LAST_LASER = "scDrillLaser", DIG_AT = "scDrillDigAt";
    /** Throttled chat warnings (ArmorLogicSC.warn: the key is the lang key). */
    private static final String WARN_LINK = "sc.drill.link.unloaded", WARN_PHASE = "sc.drill.phase.none";

    private DrillLogicSC() {
    }

    public static ItemStack held(EntityPlayer p) {
        ItemStack s = p.getCurrentEquippedItem();
        return s != null && s.getItem() instanceof ItemDrillSC ? s : null;
    }

    private static boolean fullSetOf(EntityPlayer p, DrillType type) {
        com.sc.util.ArmorSuit set = ArmorLogicSC.bonusSet(p);  // none in emergency mode (no helium)
        return set == type.suit || type.suit == com.sc.util.ArmorSuit.EXO && set == com.sc.util.ArmorSuit.SINGULAR;   // Singular keeps the Exo bonuses
    }

    /** Worn energy armour feeding the drill: the chestplate, any suit - not while it's overheated. */
    private static ItemStack feeder(EntityPlayer p) {
        ItemStack chest = ArmorLogicSC.piece(p, 1);
        return chest == null || ArmorLogicSC.overheated(p) ? null : chest;
    }

    /** EU the chestplate may give the drill: what it holds above the reserve (ConfigSC.drillArmorReserve, % of its capacity). */
    static int spare(ItemStack chest) {
        if (chest == null) {
            return 0;
        }
        long reserve = (long) ItemArmorSC.capacityOf(chest) * com.sc.util.ConfigSC.drillArmorReserve / 100;
        return (int) Math.max(0, ItemArmorSC.chargeOf(chest) - reserve);
    }

    /** EU for one block: economy halves it, the Nano set takes a quarter off (Nano drill). */
    public static int cost(EntityPlayer p, ItemStack drill, int eu) {
        DrillType t = ItemDrillSC.typeOf(drill);
        float mul = ItemDrillSC.isEnabled(drill, DrillFeature.ECO) ? 0.5F : 1F;
        if (t == DrillType.NANO && fullSetOf(p, t)) {
            mul *= 0.75F;
        }
        return (int) Math.ceil(eu * mul);
    }

    public static boolean canPay(EntityPlayer p, ItemStack drill, int eu) {
        int need = cost(p, drill, eu);
        ItemStack chest = feeder(p);
        return ItemDrillSC.chargeOf(drill) + spare(chest) >= need;
    }

    /** All or nothing: the drill's own charge first, then the worn chestplate's. */
    public static boolean pay(EntityPlayer p, ItemStack drill, int eu) {
        if (eu <= 0) {
            return true;
        }
        if (!canPay(p, drill, eu)) {
            return false;
        }
        int need = cost(p, drill, eu);
        need -= ItemDrillSC.discharge(drill, need);
        if (need > 0) {
            ItemArmorSC.discharge(feeder(p), need);
        }
        return true;
    }

    /** Creative mode: the Singular drill's own modes (the black hole) are free; the rest is vanilla's there anyway. */
    private static boolean payOrFree(EntityPlayer p, ItemStack drill, int eu) {
        return p.capabilities.isCreativeMode || pay(p, drill, eu);
    }

    /** The drill heats up - the Singular drill with the full Singular suit puts it into the suit (its helium cools it). */
    public static void heat(EntityPlayer p, ItemStack drill, int h) {
        if (h <= 0) {
            return;
        }
        if (dumpsHeat(p, drill)) {
            ArmorLogicSC.addHeat(p, h);
        } else {
            ItemDrillSC.addHeat(drill, h);
        }
    }

    /** The Singular drill with the working full Singular suit: its heat goes into the suit. */
    public static boolean dumpsHeat(EntityPlayer p, ItemStack drill) {
        return p != null && ToolLevelSC.isDrill(drill) && ArmorLogicSC.bonusSet(p) == ArmorSuit.SINGULAR;
    }

    /** The eyes' height on either side (the client player's posY already is at the eyes - as Forge's ray traces do it). */
    public static double eyeY(EntityPlayer p) {
        return p.posY + (p.worldObj.isRemote ? p.getEyeHeight() - p.getDefaultEyeHeight() : p.getEyeHeight());
    }

    /** Forge BreakSpeed (both sides): out of energy or overheated it digs like a bare hand; economy and the area modes are slower. */
    public static float breakSpeed(EntityPlayer p, Block block, int meta, float speed) {
        ItemStack drill = held(p);
        if (drill == null || !ItemDrillSC.suits(block, meta)) {
            return speed;
        }
        DrillType t = ItemDrillSC.typeOf(drill);
        if (!p.worldObj.isRemote) {
            p.getEntityData().setLong(DIG_AT, p.worldObj.getTotalWorldTime());
        }
        if (ItemDrillSC.overheated(drill) || !canPay(p, drill, t.euPerBlock)) {
            return Math.min(speed, 0.5F);
        }
        if (ItemDrillSC.isEnabled(drill, DrillFeature.ECO)) {
            speed /= 2;
        }
        if (areaOn(p, drill)) {
            speed /= 2;
        }
        if (ToolLevelSC.hasBranch(p, drill, ToolLevelSC.DRILL_MINER, ToolLevelSC.BRANCH_LEVEL)) {
            speed *= DrillFeature.MINER_SPEED;                    // Miner lv 3
        }
        return speed;
    }

    /**
     * The drill in hand is digging right now (half a second): charging / cooling it then would change
     * its NBT, and the client restarts the block's progress when the held item's tag changes.
     */
    public static boolean digging(EntityPlayer p, ItemStack stack) {
        return stack == p.getCurrentEquippedItem() && p.worldObj.getTotalWorldTime() - p.getEntityData().getLong(DIG_AT) < 10;
    }

    private static boolean areaOn(EntityPlayer p, ItemStack drill) {
        return !p.isSneaking() && (ItemDrillSC.isEnabled(drill, DrillFeature.AREA_3X3) || ItemDrillSC.isEnabled(drill, DrillFeature.AREA_5X5)
                || ItemDrillSC.isEnabled(drill, DrillFeature.TUNNEL) || funnelRadius(p, drill) > 0 || holeSize(p, drill) > 0);
    }

    public static int fortune(EntityPlayer p, ItemStack drill) {
        DrillType t = ItemDrillSC.typeOf(drill);
        if (!ItemDrillSC.isEnabled(drill, DrillFeature.FORTUNE) || ItemDrillSC.isEnabled(drill, DrillFeature.SILK)) {
            return 0;
        }
        int f = t.fortune + (t == DrillType.QUANTUM && fullSetOf(p, t) ? 1 : 0);
        if (ToolLevelSC.hasBranch(p, drill, ToolLevelSC.DRILL_PROSPECTOR, ToolLevelSC.BRANCH_PERK_LEVEL)) {
            f++;                                                   // Prospector lv 5: fortune VI
        }
        return f;
    }

    // ------------------------------------------------------------------ Singular: levels, modes (client-safe: NBT + creative)

    /** The function works at this drill's level (older functions: whenever the drill has them). Client-safe. */
    public static boolean unlocked(EntityPlayer p, ItemStack drill, DrillFeature f) {
        DrillType t = ItemDrillSC.typeOf(drill);
        if (t == null || f == null || !f.availableIn(t)) {
            return false;
        }
        if (!f.singular()) {
            return true;
        }
        return ToolLevelSC.effectiveLevel(p, drill) >= f.singLevel()
                && (f.branch() == ToolLevelSC.BRANCH_NONE || ToolLevelSC.hasBranch(p, drill, f.branch(), f.singLevel()));
    }

    /** Switched on and open at the drill's level. Client-safe. */
    public static boolean on(EntityPlayer p, ItemStack drill, DrillFeature f) {
        return ItemDrillSC.isEnabled(drill, f) && unlocked(p, drill, f);
    }

    /** The black hole's size as it works now (0: off / not the Singular drill), gated by the level. Client-safe. */
    public static int holeSize(EntityPlayer p, ItemStack drill) {
        if (!ToolLevelSC.isDrill(drill)) {
            return 0;
        }
        return DrillZoneSC.effectiveHole(ItemDrillSC.blackHoleSize(drill), ToolLevelSC.effectiveLevel(p, drill));
    }

    /** The gravitational funnel's half-size (0: off / locked). Client-safe. */
    public static int funnelRadius(EntityPlayer p, ItemStack drill) {
        if (!on(p, drill, DrillFeature.GRAV_FUNNEL)) {
            return 0;
        }
        return DrillZoneSC.funnelRadius(ToolLevelSC.effectiveLevel(p, drill),
                ToolLevelSC.hasBranch(p, drill, ToolLevelSC.DRILL_MINER, ToolLevelSC.BRANCH_PERK_LEVEL));
    }

    /** The phase dig's reach (Prospector lv 3: farther). */
    public static int phaseRange(EntityPlayer p, ItemStack drill) {
        return ToolLevelSC.hasBranch(p, drill, ToolLevelSC.DRILL_PROSPECTOR, ToolLevelSC.BRANCH_LEVEL)
                ? DrillFeature.PHASE_RANGE_PROSPECTOR : DrillFeature.PHASE_RANGE;
    }

    /** The vein's size (Prospector lv 5: bigger; diagonals count as always). */
    public static int veinMax(EntityPlayer p, ItemStack drill) {
        return ToolLevelSC.hasBranch(p, drill, ToolLevelSC.DRILL_PROSPECTOR, ToolLevelSC.BRANCH_PERK_LEVEL)
                ? DrillFeature.VEIN_MAX_PROSPECTOR : DrillFeature.VEIN_MAX;
    }

    /** The black hole's base cooldown for this size (12x12 only; Miner lv 3: none), before the set's -25%. */
    public static int holeCooldown(EntityPlayer p, ItemStack drill, int size) {
        if (size < 12 || ToolLevelSC.hasBranch(p, drill, ToolLevelSC.DRILL_MINER, ToolLevelSC.BRANCH_LEVEL)) {
            return 0;
        }
        return DrillFeature.BLACK_HOLE.cooldownTicks();
    }

    /**
     * The box {minX, minY, minZ, maxX, maxY, maxZ} a dig at (x, y, z) on `side` would take - the frame
     * DrillHoleRendererSC draws: the black hole's zone, or the area / funnel / tunnel face; null for a single block,
     * a vein, or while sneaking. Client-safe (the funnel's argon isn't checked here).
     */
    public static int[] zoneFor(EntityPlayer p, ItemStack drill, int x, int y, int z, int side) {
        if (drill == null || p.isSneaking()) {
            return null;
        }
        int hole = holeSize(p, drill);
        if (hole > 0) {
            return DrillZoneSC.zone(x, y, z, side, hole, ItemDrillSC.tunnelDepth(drill), p.posX, eyeY(p), p.posZ);
        }
        Block b = p.worldObj.getBlock(x, y, z);
        int meta = p.worldObj.getBlockMetadata(x, y, z);
        if (!ItemDrillSC.suits(b, meta) || veinApplies(p, drill, b, meta)) {
            return null;
        }
        return areaBox(p, drill, x, y, z, side, true);
    }

    private static boolean veinApplies(EntityPlayer p, ItemStack drill, Block center, int meta) {
        return ItemDrillSC.isEnabled(drill, DrillFeature.VEIN) && isOre(norm(center), meta) && center.canHarvestBlock(p, meta);
    }

    /** The area modes' box (null: one block). The funnel (when allowed) wins over 5x5 / 3x3 and is one block deep. */
    private static int[] areaBox(EntityPlayer p, ItemStack drill, int x, int y, int z, int side, boolean funnelOk) {
        int funnel = funnelOk ? funnelRadius(p, drill) : 0;
        int radius = funnel > 0 ? funnel : ItemDrillSC.isEnabled(drill, DrillFeature.AREA_5X5) ? 2
                : ItemDrillSC.isEnabled(drill, DrillFeature.AREA_3X3) || ItemDrillSC.isEnabled(drill, DrillFeature.TUNNEL) ? 1 : 0;
        int depth = funnel == 0 && ItemDrillSC.isEnabled(drill, DrillFeature.TUNNEL) ? DrillFeature.TUNNEL_DEPTH : 1;
        if (radius == 0 && depth == 1) {
            return null;
        }
        return DrillZoneSC.zone(x, y, z, side, 2 * radius + 1, depth, p.posX, eyeY(p), p.posZ);
    }

    // ------------------------------------------------------------------ digging

    /**
     * The block at x, y, z is being broken with the drill (server). @return true when the drill
     * broke it itself; false leaves it to vanilla (a tile-entity block, out of charge, overheated, creative mode
     * - except the Singular drill's black hole, which works in creative too).
     */
    public static boolean dig(EntityPlayer player, ItemStack drill, int x, int y, int z) {
        if (!(player instanceof EntityPlayerMP)) {
            return false;
        }
        EntityPlayerMP p = (EntityPlayerMP) player;
        World w = p.worldObj;
        DrillType t = ItemDrillSC.typeOf(drill);
        boolean creative = p.capabilities.isCreativeMode;
        if (ItemDrillSC.overheated(drill) || !creative && !canPay(p, drill, t.euPerBlock)) {
            return false;
        }
        int hole = p.isSneaking() ? 0 : holeSize(p, drill);
        if (hole > 0 && w.getTileEntity(x, y, z) == null) {
            Boolean done = blackHole(p, drill, x, y, z, hole);
            if (done != null) {
                return done;
            }                                                     // no singular matter: an ordinary dig
        }
        if (creative) {
            return false;
        }
        Block center = w.getBlock(x, y, z);
        int centerMeta = w.getBlockMetadata(x, y, z);
        int side = hitSide(p, x, y, z);
        boolean funnel = !p.isSneaking() && ItemDrillSC.suits(center, centerMeta) && !veinApplies(p, drill, center, centerMeta)
                && funnelRadius(p, drill) > 0;
        List<int[]> targets = targets(p, drill, x, y, z, side, center, centerMeta, funnel);
        int argon = (targets.size() - 1) * DrillFeature.GRAV_FUNNEL.gasMb();
        if (funnel && argon > 0 && !ToolGasSC.has(p, ArmorGasSC.Gas.ARGON, argon)) {
            ToolGasSC.noGasMessage(p, ArmorGasSC.Gas.ARGON, argon);
            funnel = false;
            targets = targets(p, drill, x, y, z, side, center, centerMeta, false);   // the old area mode then
        }
        boolean centerByVanilla = w.getTileEntity(x, y, z) != null;
        Harvest h = new Harvest(p, drill);
        for (int i = 0; i < targets.size(); i++) {
            int[] b = targets.get(i);
            boolean first = i == 0;
            if (first && centerByVanilla) {
                continue;
            }
            int eu = first ? t.euPerBlock : extraCost(p, drill);
            if (!h.breakOne(b[0], b[1], b[2], first, eu)) {
                break;
            }
        }
        if (funnel && h.extra > 0) {
            ToolGasSC.drainExact(p, ArmorGasSC.Gas.ARGON, h.extra * DrillFeature.GRAV_FUNNEL.gasMb());
        }
        if (on(p, drill, DrillFeature.DRAIN)) {
            drain(p, bounds(targets), null);
        }
        h.deliver(x + 0.5, y + 0.5, z + 0.5);
        return !centerByVanilla;
    }

    /** Area / tunnel / vein blocks after the first: the Exo (Singular) set halves them; the Singular drill then x LEGACY_MUL. */
    private static int extraCost(EntityPlayer p, ItemStack drill) {
        DrillType t = ItemDrillSC.typeOf(drill);
        int eu = t.exoClass() && fullSetOf(p, t) ? (t.euPerBlock + 1) / 2 : t.euPerBlock;
        return legacy(drill, eu);
    }

    /** EU of an Exo-era function on this drill: ToolLevelSC.LEGACY_MUL of it on the Singular drill. */
    public static int legacy(ItemStack drill, int eu) {
        return com.sc.util.ToolLevelSC.legacyCost(drill, eu);
    }

    /** The blocks one dig takes, the clicked one first: a vein (ore), the funnel, a tunnel, a 5x5 or 3x3 face, or just the one. */
    private static List<int[]> targets(EntityPlayer p, ItemStack drill, int x, int y, int z, int side, Block center, int meta, boolean funnel) {
        List<int[]> out = new ArrayList<int[]>();
        out.add(new int[]{x, y, z});
        if (p.isSneaking() || !ItemDrillSC.suits(center, meta)) {
            return out;                                           // sneaking, or a torch / log / grass clicked: just that one
        }
        World w = p.worldObj;
        // an ore the drill can't harvest: just that one (the vein used to go too - broken with no drops)
        if (veinApplies(p, drill, center, meta)) {
            int max = veinMax(p, drill);
            Set<Long> seen = new LinkedHashSet<Long>();
            List<int[]> queue = new ArrayList<int[]>();
            queue.add(new int[]{x, y, z});
            seen.add(key(x, y, z));
            for (int qi = 0; qi < queue.size() && out.size() < max; qi++) {
                int[] c = queue.get(qi);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            int nx = c[0] + dx, ny = c[1] + dy, nz = c[2] + dz;
                            if (!seen.add(key(nx, ny, nz)) || !w.blockExists(nx, ny, nz)
                                    || norm(w.getBlock(nx, ny, nz)) != norm(center) || w.getBlockMetadata(nx, ny, nz) != meta
                                    || w.getTileEntity(nx, ny, nz) != null) {
                                continue;
                            }
                            int[] b = {nx, ny, nz};
                            queue.add(b);
                            if (out.size() < max) {
                                out.add(b);
                            }
                        }
                    }
                }
            }
            return out;
        }
        int[] box = areaBox(p, drill, x, y, z, side, funnel);
        if (box == null) {
            return out;
        }
        for (int by = box[4]; by >= box[1]; by--) {               // top down: nothing left hanging to fall in between
            for (int bx = box[0]; bx <= box[3]; bx++) {
                for (int bz = box[2]; bz <= box[5]; bz++) {
                    if (bx == x && by == y && bz == z) {
                        continue;
                    }
                    if (areaTakes(p, bx, by, bz)) {
                        out.add(new int[]{bx, by, bz});
                    }
                }
            }
        }
        return out;
    }

    /** Clicked redstone ore lights up (a block of its own with no item) - for the vein it's the same ore. */
    private static Block norm(Block b) {
        return b == Blocks.lit_redstone_ore ? Blocks.redstone_ore : b;
    }

    private static long key(int x, int y, int z) {
        return DrillZoneSC.pack(x, y, z);
    }

    /** What the area modes may take: loaded, not air / liquid / unbreakable, no tile entity, something the drill is for. */
    private static boolean areaTakes(EntityPlayer p, int x, int y, int z) {
        World w = p.worldObj;
        if (y < 0 || y > 255 || !w.blockExists(x, y, z)) {
            return false;
        }
        Block b = w.getBlock(x, y, z);
        int meta = w.getBlockMetadata(x, y, z);
        return !b.isAir(w, x, y, z) && !b.getMaterial().isLiquid() && b.getBlockHardness(w, x, y, z) >= 0
                && w.getTileEntity(x, y, z) == null && ItemDrillSC.suits(b, meta) && b.canHarvestBlock(p, meta);
    }

    /** The face of the block the player is looking at (for the area's plane). */
    private static int hitSide(EntityPlayer p, int x, int y, int z) {
        Vec3 eyes = Vec3.createVectorHelper(p.posX, p.posY + p.getEyeHeight(), p.posZ);
        Vec3 look = p.getLookVec();
        Vec3 end = eyes.addVector(look.xCoord * 6, look.yCoord * 6, look.zCoord * 6);
        MovingObjectPosition hit = p.worldObj.rayTraceBlocks(eyes, end);
        if (hit != null && hit.blockX == x && hit.blockY == y && hit.blockZ == z) {
            return hit.sideHit;
        }
        return p.rotationPitch > 45 ? 1 : p.rotationPitch < -45 ? 0 : new int[]{2, 5, 3, 4}[MathHelper.floor_double(p.rotationYaw * 4F / 360F + 0.5) & 3];
    }

    /** An ore by the ore dictionary ("ore..."). */
    static boolean isOre(Block b, int meta) {
        Item item = Item.getItemFromBlock(b);
        if (item == null) {
            return false;
        }
        for (int id : OreDictionary.getOreIDs(new ItemStack(item, 1, meta))) {
            if (OreDictionary.getOreName(id).startsWith("ore")) {
                return true;
            }
        }
        return false;
    }

    /** The box around a list of blocks {minX, minY, minZ, maxX, maxY, maxZ}. */
    private static int[] bounds(List<int[]> blocks) {
        int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int[] b : blocks) {
            for (int a = 0; a < 3; a++) {
                box[a] = Math.min(box[a], b[a]);
                box[3 + a] = Math.max(box[3 + a], b[a]);
            }
        }
        return box;
    }

    /** One dig's harvest: breaks blocks one by one, collects the drops and XP, hands them over at the end. */
    private static final class Harvest {
        final EntityPlayerMP p;
        final ItemStack drill;
        final World w;
        final int fortune;
        final boolean silk;
        /** Replace: the hotbar slot right of the drill (-1: off). */
        final int replaceSlot;
        final List<ItemStack> drops = new ArrayList<ItemStack>();
        int xp;
        /** Blocks broken; of them, beyond the first; of them, not placed by a player (level points). */
        int broken, extra, natural;

        Harvest(EntityPlayerMP p, ItemStack drill) {
            this.p = p;
            this.drill = drill;
            this.w = p.worldObj;
            this.fortune = fortune(p, drill);
            this.silk = ItemDrillSC.isEnabled(drill, DrillFeature.SILK);
            this.replaceSlot = on(p, drill, DrillFeature.REPLACE) && p.inventory.currentItem < 8 ? p.inventory.currentItem + 1 : -1;
        }

        /** @return false when it couldn't be paid for (the dig stops there) */
        boolean breakOne(int x, int y, int z, boolean first, int eu) {
            Block block = w.getBlock(x, y, z);
            int meta = w.getBlockMetadata(x, y, z);
            if (block.isAir(w, x, y, z) || block.getBlockHardness(w, x, y, z) < 0) {
                return true;
            }
            // placed by a player? asked before this block's BreakEvent forgets it (the clicked one's: DrillEventsSC kept it)
            boolean placed = ToolLevelSC.isDrill(drill)
                    && (first ? DrillEventsSC.wasPlaced(p, x, y, z) : PlacedBlocksSC.placed(w, x, y, z));
            if (!first) {                                         // the clicked block's event vanilla has fired already
                // paid for and not spawn-protected first: the event already tells the client the block is gone,
                // and a block then left standing stayed a ghost (air) on the client
                if (!canPay(p, drill, eu)) {
                    return false;
                }
                if (!w.canMineBlock(p, x, y, z)) {
                    return true;                                  // spawn protection - skip it, go on
                }
                net.minecraftforge.event.world.BlockEvent.BreakEvent ev = net.minecraftforge.common.ForgeHooks.onBlockBreakEvent(
                        w, p.theItemInWorldManager.getGameType(), p, x, y, z);
                if (ev.isCanceled()) {
                    return true;                                  // protected (a mod's claim) - skip it, go on
                }
            }
            if (!pay(p, drill, eu)) {
                return false;
            }
            boolean canHarvest = block.canHarvestBlock(p, meta);
            ArrayList<ItemStack> got = new ArrayList<ItemStack>();
            boolean silked = false;
            if (silk && canHarvest && block.canSilkHarvest(w, p, x, y, z, meta)) {
                Item item = Item.getItemFromBlock(norm(block));   // clicked redstone ore is lit: no item of its own
                if (item != null) {
                    got.add(new ItemStack(item, 1, item.getHasSubtypes() ? block.damageDropped(meta) : 0));
                    silked = true;
                }
            }
            if (!silked && canHarvest) {
                got.addAll(block.getDrops(w, x, y, z, meta, fortune));
            }
            net.minecraftforge.event.ForgeEventFactory.fireBlockHarvesting(got, w, block, x, y, z, meta, silked ? 0 : fortune, 1F, silked, p);
            if (!silked && canHarvest) {
                xp += block.getExpDrop(w, meta, fortune);
            }
            block.onBlockHarvested(w, x, y, z, meta, p);
            if (block.removedByPlayer(w, p, x, y, z, canHarvest)) {
                block.onBlockDestroyedByPlayer(w, x, y, z, meta);
            }
            w.playAuxSFX(2001, x, y, z, Block.getIdFromBlock(block) + (meta << 12));
            for (ItemStack s : got) {
                if (s != null && s.stackSize > 0) {
                    if (ItemDrillSC.isEnabled(drill, DrillFeature.AUTOSMELT)) {
                        drops.addAll(smelted(s));
                    } else {
                        drops.add(s);
                    }
                }
            }
            broken++;
            if (!first) {
                extra++;
            }
            if (!placed) {
                natural++;
            }
            if (replaceSlot >= 0 && w.isAirBlock(x, y, z)) {
                replace(x, y, z);
            }
            heat(p, drill, DrillFeature.BLOCK_HEAT);
            return true;
        }

        /** Replace: one block from the hotbar slot right of the drill where this one was (a placement like any other). */
        private void replace(int x, int y, int z) {
            ItemStack s = p.inventory.mainInventory[replaceSlot];
            if (s == null || s.stackSize <= 0 || !(s.getItem() instanceof ItemBlock)) {
                return;
            }
            Block b = Block.getBlockFromItem(s.getItem());
            int meta = s.getItem().getMetadata(s.getItemDamage());
            if (b == null || b == Blocks.air || b.hasTileEntity(meta) || !b.canPlaceBlockAt(w, x, y, z)) {
                return;                                           // no chests / machines - only plain blocks
            }
            net.minecraftforge.common.util.BlockSnapshot snap = net.minecraftforge.common.util.BlockSnapshot.getBlockSnapshot(w, x, y, z);
            if (!w.setBlock(x, y, z, b, meta, 3)) {
                return;
            }
            net.minecraftforge.event.world.BlockEvent.PlaceEvent ev = net.minecraftforge.event.ForgeEventFactory.onPlayerBlockPlace(
                    p, snap, ForgeDirection.UNKNOWN);             // claims refuse it; DrillEventsSC records it as placed
            if (ev.isCanceled()) {
                w.setBlockToAir(x, y, z);
                return;
            }
            b.onBlockPlacedBy(w, x, y, z, p, s);
            if (!p.capabilities.isCreativeMode && --s.stackSize <= 0) {
                p.inventory.mainInventory[replaceSlot] = null;
            }
        }

        /** Drops to the linked chest (link / cross link), the inventory (magnet) or the ground at the clicked block; XP to the player. */
        void deliver(double x, double y, double z) {
            LinkTarget link = ItemDrillSC.isEnabled(drill, DrillFeature.LINK) || on(p, drill, DrillFeature.CROSS_LINK) ? linked(p, drill) : null;
            IInventory chest = link == null ? null : link.inv;
            int smMb = DrillFeature.CROSS_LINK.gasMb();
            boolean warned = false;
            for (ItemStack s : merged(drops)) {
                ItemStack left = s;
                if (chest != null) {
                    if (!link.cross) {
                        left = insert(chest, s);
                    } else if (ToolGasSC.has(p, DrillFeature.CROSS_LINK.gas(), smMb)) {
                        left = insert(chest, s);                  // another dimension: SM a stack delivered
                        if (left == null || left.stackSize < s.stackSize) {
                            ToolGasSC.drainExact(p, DrillFeature.CROSS_LINK.gas(), smMb);
                        }
                    } else if (!warned) {
                        ToolGasSC.noGasMessage(p, DrillFeature.CROSS_LINK.gas(), smMb);
                        warned = true;
                    }
                }
                if (left != null && ItemDrillSC.isEnabled(drill, DrillFeature.MAGNET)) {
                    p.inventory.addItemStackToInventory(left);            // takes what fits, shrinking the stack
                }
                if (left != null && left.stackSize > 0) {
                    EntityItem e = new EntityItem(w, x, y, z, left);
                    e.delayBeforeCanPickup = 10;
                    w.spawnEntityInWorld(e);
                }
            }
            if (chest != null) {
                chest.markDirty();
            }
            if (xp > 0) {
                w.spawnEntityInWorld(new EntityXPOrb(w, p.posX, p.posY + 0.5, p.posZ, xp));
            }
            addDug(p, drill, natural);                            // placed blocks give no points (no place-and-break farming)
            p.inventoryContainer.detectAndSendChanges();
        }
    }

    /** Equal drops joined into full stacks (a big funnel / vein would spawn hundreds of item entities). */
    private static List<ItemStack> merged(List<ItemStack> in) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        for (ItemStack s : in) {
            ItemStack rest = s.copy();
            for (ItemStack o : out) {
                if (rest.stackSize <= 0) {
                    break;
                }
                if (o.isItemEqual(rest) && ItemStack.areItemStackTagsEqual(o, rest) && o.stackSize < o.getMaxStackSize()) {
                    int move = Math.min(rest.stackSize, o.getMaxStackSize() - o.stackSize);
                    o.stackSize += move;
                    rest.stackSize -= move;
                }
            }
            while (rest.stackSize > 0) {
                ItemStack part = rest.copy();
                part.stackSize = Math.min(rest.stackSize, Math.max(1, rest.getMaxStackSize()));
                rest.stackSize -= part.stackSize;
                out.add(part);
            }
        }
        return out;
    }

    /** The furnace result for the whole drop (split into full stacks), or the drop itself. */
    private static List<ItemStack> smelted(ItemStack s) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        ItemStack r = FurnaceRecipes.smelting().getSmeltingResult(s);
        if (r == null) {
            out.add(s);
            return out;
        }
        int total = r.stackSize * s.stackSize;
        while (total > 0) {
            ItemStack part = r.copy();
            part.stackSize = Math.min(part.getMaxStackSize(), total);
            total -= part.stackSize;
            out.add(part);
        }
        return out;
    }

    /** The linked inventory and whether it is in another dimension (the cross link). */
    private static final class LinkTarget {
        final IInventory inv;
        final boolean cross;

        LinkTarget(IInventory inv, boolean cross) {
            this.inv = inv;
            this.cross = cross;
        }
    }

    /**
     * The linked chest if it's in this dimension (the cross link on: in any loaded dimension), its chunk loaded
     * (never loaded for it) and still an inventory; else null - «not loaded» in chat once in a while.
     */
    private static LinkTarget linked(EntityPlayer p, ItemStack drill) {
        int[] l = ItemDrillSC.link(drill);
        if (l == null) {
            return null;
        }
        boolean cross = l[3] != p.worldObj.provider.dimensionId;
        World lw = p.worldObj;
        if (cross) {
            if (!on(p, drill, DrillFeature.CROSS_LINK)) {
                return null;                                      // the old link: this dimension only
            }
            lw = net.minecraftforge.common.DimensionManager.getWorld(l[3]);   // null: that dimension isn't loaded
        }
        if (lw == null || !lw.blockExists(l[0], l[1], l[2])) {
            ArmorLogicSC.warn(p, WARN_LINK, 200);                // the drops go to the inventory / ground instead
            return null;
        }
        if (com.sc.ShieldEventHandler.privateFor(lw, p, l[0], l[1], l[2])) {
            return null;                    // БР-3: checked on every delivery - a chest now in someone's private field gets nothing, the drops fall
        }
        TileEntity te = lw.getTileEntity(l[0], l[1], l[2]);
        if (te instanceof TileEntityChest) {
            // a double chest: both halves, in vanilla's order (as BlockChest opens it)
            TileEntityChest c = (TileEntityChest) te;
            c.checkForAdjacentChests();
            if (c.adjacentChestXNeg != null) {
                return new LinkTarget(new InventoryLargeChest("container.chestDouble", c.adjacentChestXNeg, c), cross);
            }
            if (c.adjacentChestXPos != null) {
                return new LinkTarget(new InventoryLargeChest("container.chestDouble", c, c.adjacentChestXPos), cross);
            }
            if (c.adjacentChestZNeg != null) {
                return new LinkTarget(new InventoryLargeChest("container.chestDouble", c.adjacentChestZNeg, c), cross);
            }
            if (c.adjacentChestZPos != null) {
                return new LinkTarget(new InventoryLargeChest("container.chestDouble", c, c.adjacentChestZPos), cross);
            }
        }
        return te instanceof IInventory ? new LinkTarget((IInventory) te, cross) : null;
    }

    /**
     * Puts as much as fits into the inventory, as a hopper above it would: through its top face
     * (ISidedInventory's slots for side 1 and canInsertItem). @return what's left, or null
     */
    private static ItemStack insert(IInventory inv, ItemStack s) {
        int left = InvUtilSC.insert(inv, ForgeDirection.DOWN, s);       // moving down into it = its UP face
        if (left <= 0) {
            return null;
        }
        ItemStack rest = s.copy();
        rest.stackSize = left;
        return rest;
    }

    // ------------------------------------------------------------------ Singular: drain, black hole, phase dig, counters

    /**
     * Drain: water / lava in `box` and one block around it removed without flow updates (the zone stays dry); same
     * rules as digging (loaded, spawn protection, a BreakEvent each). The armour has no water / lava tanks: the
     * fluid is just gone. `skip` (may be null): a box already cleared. At most DRAIN_MAX blocks. @return sources removed
     */
    static int drain(EntityPlayerMP p, int[] box, int[] skip) {
        World w = p.worldObj;
        int removed = 0, sources = 0;
        for (int x = box[0] - 1; x <= box[3] + 1; x++) {
            for (int y = Math.max(0, box[1] - 1); y <= Math.min(255, box[4] + 1); y++) {
                for (int z = box[2] - 1; z <= box[5] + 1; z++) {
                    if (removed >= DrillFeature.DRAIN_MAX) {
                        return sources;
                    }
                    if (skip != null && DrillZoneSC.inside(skip, x, y, z) || !w.blockExists(x, y, z)) {
                        continue;
                    }
                    Block b = w.getBlock(x, y, z);
                    if (!b.getMaterial().isLiquid() || !w.canMineBlock(p, x, y, z)) {
                        continue;
                    }
                    int meta = w.getBlockMetadata(x, y, z);
                    if (net.minecraftforge.common.ForgeHooks.onBlockBreakEvent(w, p.theItemInWorldManager.getGameType(), p, x, y, z).isCanceled()) {
                        continue;
                    }
                    if (meta == 0 && (b instanceof BlockLiquid || b instanceof net.minecraftforge.fluids.IFluidBlock)) {
                        sources++;
                    }
                    w.setBlock(x, y, z, Blocks.air, 0, 2);
                    removed++;
                }
            }
        }
        if (removed > 0) {
            w.playSoundEffect(p.posX, p.posY, p.posZ, "random.fizz", 0.5F, 1.4F);
        }
        return sources;
    }

    /**
     * Black hole (docs/plan-singular-tools.md §3.2): everything in the zone destroyed - ores and liquids too - with
     * no drops and no XP; not touched: bedrock and the unbreakable, tile-entity blocks, spawn protection, anything
     * a BreakEvent refuses (claims, private fields), unloaded chunks. EU euPerBlock x HOLE_EU_MUL a block, SM 1 mB per
     * HOLE_BLOCKS_PER_MB blocks (the fraction kept in the drill), heat HOLE_HEAT_PER_BLOCK a block, 12x12: a cooldown.
     * Removed with flag 2 (no neighbour updates inside), then only the solid outside neighbours are told; one burst
     * of particles. Crumbs for the natural blocks.
     * @return true: handled (or the cooldown still running - nothing happens); false: no EU; null: no singular matter - dig as usual
     */
    private static Boolean blackHole(EntityPlayerMP p, ItemStack drill, int x, int y, int z, int size) {
        World w = p.worldObj;
        DrillFeature f = DrillFeature.BLACK_HOLE;
        // the clicked block's BreakEvent has fired already: whether it was placed, DrillEventsSC kept
        boolean firstPlaced = DrillEventsSC.wasPlaced(p, x, y, z);
        if (ToolLevelSC.cooldownLeft(drill, f.key(), w) > 0) {
            if (firstPlaced) {
                PlacedBlocksSC.mark(w, x, y, z);                  // it stays: so does its mark
            }
            return Boolean.TRUE;                                  // the block is sent back to the client
        }
        int[] box = DrillZoneSC.zone(x, y, z, hitSide(p, x, y, z), size, ItemDrillSC.tunnelDepth(drill), p.posX, eyeY(p), p.posZ);
        int smNeed = Math.max(f.gasMb(), DrillZoneSC.holeGasNeed(DrillZoneSC.volume(box), DrillFeature.HOLE_BLOCKS_PER_MB));
        if (!ToolGasSC.has(p, f.gas(), smNeed)) {                 // the whole zone's SM there before it starts (creative: free)
            ToolGasSC.noGasMessage(p, f.gas(), smNeed);
            return null;
        }
        DrillType t = ItemDrillSC.typeOf(drill);
        int eu = DrillZoneSC.holeEu(t.euPerBlock, DrillFeature.HOLE_EU_MUL);
        if (!p.capabilities.isCreativeMode && !canPay(p, drill, eu)) {
            return Boolean.FALSE;
        }
        net.minecraft.world.WorldSettings.GameType mode = p.theItemInWorldManager.getGameType();
        int removed = 0, dug = 0, natural = 0, ores = 0, stone = 0;
        List<int[]> shell = new ArrayList<int[]>();
        boolean empty = false;
        for (int by = Math.min(255, box[4]); by >= Math.max(0, box[1]) && !empty; by--) {      // top down
            for (int bx = box[0]; bx <= box[3] && !empty; bx++) {
                for (int bz = box[2]; bz <= box[5]; bz++) {
                    if (!w.blockExists(bx, by, bz)) {
                        continue;
                    }
                    Block b = w.getBlock(bx, by, bz);
                    if (b.isAir(w, bx, by, bz) || b.getBlockHardness(w, bx, by, bz) < 0) {
                        continue;                                 // bedrock, the unbreakable
                    }
                    int meta = w.getBlockMetadata(bx, by, bz);
                    if (b.hasTileEntity(meta) || w.getTileEntity(bx, by, bz) != null || !w.canMineBlock(p, bx, by, bz)
                            || twoPart(b)) {
                        continue;                                 // chests, machines; spawn protection; doors / beds / tall plants
                    }
                    boolean liquid = b.getMaterial().isLiquid();
                    boolean first = bx == x && by == y && bz == z;                       // its event vanilla has fired
                    boolean nat = !liquid && !(first ? firstPlaced : PlacedBlocksSC.placed(w, bx, by, bz));   // before the event forgets it
                    if (!p.capabilities.isCreativeMode && !canPay(p, drill, eu)) {
                        empty = true;                             // before its event: a block left standing was never "broken"
                        break;
                    }
                    if (!first && net.minecraftforge.common.ForgeHooks.onBlockBreakEvent(w, mode, p, bx, by, bz).isCanceled()) {
                        continue;                                 // claims, private fields
                    }
                    if (!payOrFree(p, drill, eu)) {
                        empty = true;
                        break;
                    }
                    w.setBlock(bx, by, bz, Blocks.air, 0, 2);     // no neighbour storm inside the zone
                    if (!liquid) {
                        removed++;
                    }
                    if (nat) {
                        dug++;                                    // level points: any natural block
                        if (b == Blocks.stone) {
                            stone++;                              // lava + water make it too: a fraction (STONE_DIV)
                        } else if (crumbBlock(b, meta)) {         // crumbs: natural ground / ore / other rock
                            natural++;
                            ores += isOre(norm(b), meta) ? 1 : 0;
                        }
                    }
                    if (DrillZoneSC.onShell(box, bx, by, bz)) {
                        shell.add(new int[]{bx, by, bz});
                    }
                }
            }
        }
        // the zone's outside neighbours learn it's gone (sand falls, torches drop) - liquids are left still: the zone stays dry
        for (int[] s : shell) {
            for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
                int nx = s[0] + d.offsetX, ny = s[1] + d.offsetY, nz = s[2] + d.offsetZ;
                if (!DrillZoneSC.inside(box, nx, ny, nz) && ny >= 0 && ny < 256 && w.blockExists(nx, ny, nz)
                        && !w.getBlock(nx, ny, nz).getMaterial().isLiquid()) {
                    w.notifyBlockOfNeighborChange(nx, ny, nz, Blocks.air);
                }
            }
        }
        if (on(p, drill, DrillFeature.DRAIN)) {
            drain(p, box, box);                                   // and the liquids right around it
        }
        if (empty) {
            p.addChatComponentMessage(new ChatComponentTranslation("sc.drill.empty"));
        }
        ToolGasSC.drainFraction(p, drill, f.gas(), DrillZoneSC.holeGas(removed, DrillFeature.HOLE_BLOCKS_PER_MB));
        heat(p, drill, DrillZoneSC.holeHeat(removed, DrillFeature.HOLE_HEAT_PER_BLOCK));
        int cd = holeCooldown(p, drill, size);
        if (cd > 0) {
            ToolLevelSC.setCooldown(drill, f.key(), w, ToolLevelSC.cooldownTicks(p, cd));
        }
        addDug(p, drill, dug);
        addCrumbs(p, drill, DrillZoneSC.crumbUnits(natural, ores, ItemSingularCrumbSC.ORE_MUL) + stoneUnits(drill, stone));
        holeEffects(w, box);
        p.inventoryContainer.detectAndSendChanges();
        return Boolean.TRUE;
    }

    /**
     * Two-block things (doors, beds, tall plants, pistons): the black hole leaves them - a half cut off by the zone's
     * edge would drop (a piston head's breakBlock takes its base along, even outside the zone).
     */
    public static boolean twoPart(Block b) {
        return b instanceof net.minecraft.block.BlockDoor || b instanceof net.minecraft.block.BlockBed
                || b instanceof net.minecraft.block.BlockDoublePlant || b instanceof net.minecraft.block.BlockPistonBase
                || b instanceof net.minecraft.block.BlockPistonExtension || b instanceof net.minecraft.block.BlockPistonMoving;
    }

    /**
     * Whether a natural (not player-placed) block gives the black hole's singularity crumbs: stone and ground only -
     * Material rock (not cobblestone / mossy cobblestone / stone bricks / obsidian / anything "cobblestone" in the ore dictionary:
     * lava + water makes them), ground, grass, sand, clay, netherrack, end stone, and every ore. Nothing that
     * grows back or forms by itself (logs, leaves, plants, gourds, cane, cactus, snow, ice) and nothing crafted (wool,
     * planks, glass). Server and self-test.
     */
    public static boolean crumbBlock(Block b, int meta) {
        if (b == null || b == Blocks.air) {
            return false;
        }
        if (isOre(norm(b), meta)) {
            return true;
        }
        if (b == Blocks.netherrack || b == Blocks.end_stone) {
            return true;
        }
        net.minecraft.block.material.Material m = b.getMaterial();
        if (m == net.minecraft.block.material.Material.rock) {
            return b != Blocks.cobblestone && b != Blocks.mossy_cobblestone && b != Blocks.stonebrick && b != Blocks.obsidian   // lava + water
                    && !oreNamed(b, meta, "cobblestone");
        }
        return m == net.minecraft.block.material.Material.ground || m == net.minecraft.block.material.Material.grass
                || m == net.minecraft.block.material.Material.sand || m == net.minecraft.block.material.Material.clay;
    }

    /** One of the block's ore dictionary names contains `part` (any case). */
    private static boolean oreNamed(Block b, int meta, String part) {
        Item item = Item.getItemFromBlock(b);
        if (item == null) {
            return false;
        }
        String low = part.toLowerCase(java.util.Locale.ROOT);
        for (int id : OreDictionary.getOreIDs(new ItemStack(item, 1, meta))) {
            if (OreDictionary.getOreName(id).toLowerCase(java.util.Locale.ROOT).contains(low)) {
                return true;
            }
        }
        return false;
    }

    /** One burst for the whole zone: sounds and particles at its centre (not one per block). */
    private static void holeEffects(World w, int[] box) {
        double cx = (box[0] + box[3] + 1) / 2.0, cy = (box[1] + box[4] + 1) / 2.0, cz = (box[2] + box[5] + 1) / 2.0;
        double rx = (box[3] - box[0] + 1) / 3.0, ry = (box[4] - box[1] + 1) / 3.0, rz = (box[5] - box[2] + 1) / 3.0;
        w.playSoundEffect(cx, cy, cz, "mob.endermen.portal", 1F, 0.5F);
        w.playSoundEffect(cx, cy, cz, "random.explode", 0.4F, 1.6F);
        if (w instanceof net.minecraft.world.WorldServer) {
            net.minecraft.world.WorldServer ws = (net.minecraft.world.WorldServer) w;
            ws.func_147487_a("portal", cx, cy, cz, 160, rx, ry, rz, 1.0);
            ws.func_147487_a("largesmoke", cx, cy, cz, 30, rx, ry, rz, 0.02);
            ws.func_147487_a("witchMagic", cx, cy, cz, 40, rx / 2, ry / 2, rz / 2, 0.1);
        }
    }

    /** Level points: one per BLOCKS_PER_POINT natural (not player-placed) blocks the Singular drill digs (any mode; the rest kept in its NBT). */
    static void addDug(EntityPlayer p, ItemStack drill, int blocks) {
        if (blocks <= 0 || !ToolLevelSC.isDrill(drill) || p.capabilities.isCreativeMode) {
            return;
        }
        int[] r = DrillZoneSC.accrue(ItemDrillSC.digCounter(drill), blocks, DrillFeature.BLOCKS_PER_POINT);
        ItemDrillSC.setDigCounter(drill, r[1]);
        if (r[0] > 0) {
            ToolLevelSC.addPoints(drill, r[0]);
        }
    }

    /** Natural stone into crumb units: STONE_DIV blocks make one, the rest kept in the drill. */
    static long stoneUnits(ItemStack drill, int stone) {
        if (stone <= 0 || !ToolLevelSC.isDrill(drill)) {
            return 0;
        }
        int[] r = DrillZoneSC.accrue(ItemDrillSC.stoneCounter(drill), stone, ItemSingularCrumbSC.STONE_DIV);
        ItemDrillSC.setStoneCounter(drill, r[1]);
        return r[0];
    }

    /** Singularity crumbs: one per CRUMB_BLOCKS units (the rest kept in the drill), into the inventory or dropped at the player. */
    static void addCrumbs(EntityPlayer p, ItemStack drill, long units) {
        if (units <= 0 || !ToolLevelSC.isDrill(drill) || p.capabilities.isCreativeMode || ModItems.singularCrumb == null) {
            return;
        }
        int[] r = DrillZoneSC.accrue(ItemDrillSC.crumbCounter(drill), units, ItemSingularCrumbSC.CRUMB_BLOCKS);
        ItemDrillSC.setCrumbCounter(drill, r[1]);
        for (int n = r[0]; n > 0; ) {
            ItemStack s = new ItemStack(ModItems.singularCrumb, Math.min(64, n));
            n -= s.stackSize;
            p.inventory.addItemStackToInventory(s);              // takes what fits, shrinking the stack
            if (s.stackSize > 0) {
                p.entityDropItem(s, 0.5F);
            }
        }
        if (r[0] > 0) {
            p.worldObj.playSoundAtEntity(p, "random.orb", 0.4F, 0.6F);
        }
    }

    /**
     * Phase dig (its key, level 3): the first ore along the look within phaseRange blocks is dug out through the
     * rock in front of it, the rock untouched. EU as one block, SM gasMb, a short cooldown; drops as a normal dig's.
     */
    public static void phaseDig(EntityPlayerMP p) {
        ItemStack drill = held(p);
        DrillFeature f = DrillFeature.PHASE_DIG;
        if (drill == null || !on(p, drill, f) || ItemDrillSC.overheated(drill)) {
            return;
        }
        World w = p.worldObj;
        if (ToolLevelSC.cooldownLeft(drill, f.key(), w) > 0) {
            return;
        }
        if (!ToolGasSC.has(p, f.gas(), f.gasMb())) {
            ToolGasSC.noGasMessage(p, f.gas(), f.gasMb());
            return;
        }
        Vec3 eyes = Vec3.createVectorHelper(p.posX, eyeY(p), p.posZ);
        Vec3 look = p.getLookVec();
        int range = phaseRange(p, drill);
        int[] target = null;
        double at = 0;
        long last = Long.MIN_VALUE;
        for (double d = 0.5; d <= range + 0.5 && target == null; d += 0.2) {
            int x = MathHelper.floor_double(eyes.xCoord + look.xCoord * d);
            int y = MathHelper.floor_double(eyes.yCoord + look.yCoord * d);
            int z = MathHelper.floor_double(eyes.zCoord + look.zCoord * d);
            long k = key(x, y, z);
            if (k == last || y < 0 || y > 255 || !w.blockExists(x, y, z)) {
                continue;
            }
            last = k;
            Block b = w.getBlock(x, y, z);
            if (isOre(norm(b), w.getBlockMetadata(x, y, z)) && areaTakes(p, x, y, z)) {
                target = new int[]{x, y, z};
                at = d;
            }
        }
        if (target == null) {
            ArmorLogicSC.warn(p, WARN_PHASE, 20);
            return;
        }
        Harvest h = new Harvest(p, drill);
        if (!h.breakOne(target[0], target[1], target[2], false, ItemDrillSC.typeOf(drill).euPerBlock)) {
            p.addChatComponentMessage(new ChatComponentTranslation("sc.drill.empty"));
            return;
        }
        if (h.broken == 0) {
            return;                                               // refused (a claim): nothing spent
        }
        ToolGasSC.drainExact(p, f.gas(), f.gasMb());
        ToolLevelSC.setCooldown(drill, f.key(), w, ToolLevelSC.cooldownTicks(p, f.cooldownTicks()));
        h.deliver(p.posX, p.posY + 0.5, p.posZ);
        w.playSoundAtEntity(p, "mob.endermen.portal", 0.5F, 1.8F);
        if (w instanceof net.minecraft.world.WorldServer) {
            for (double d = 1; d <= at; d += 0.5) {
                ((net.minecraft.world.WorldServer) w).func_147487_a("portal", eyes.xCoord + look.xCoord * d,
                        eyes.yCoord + look.yCoord * d - 0.2, eyes.zCoord + look.zCoord * d, 2, 0.05, 0.05, 0.05, 0.2);
            }
        }
    }

    // ------------------------------------------------------------------ keys and modes (from the client)

    /**
     * A drill key function (DRILL_ACTION): the laser, the phase dig (their switches are safety catches); the black
     * hole's key is a plain toggle (DRILL_TOGGLE). Functions the drill lacks or its level hasn't opened do nothing.
     */
    public static void action(EntityPlayerMP p, DrillFeature f) {
        ItemStack drill = held(p);
        if (f == null || drill == null || !unlocked(p, drill, f)) {
            return;
        }
        switch (f) {
            case LASER:
                laser(p);
                break;
            case PHASE_DIG:
                phaseDig(p);
                break;
            default:
                break;
        }
    }

    /**
     * Shift + wheel with the Singular drill / the K menu's mode button (DRILL_MODE), exactly one step per call:
     * off -> 5 -> 5 tunnel -> 9 -> 9 tunnel -> 12 -> 12 tunnel -> off, only the sizes its level opens.
     */
    public static void cycleMode(EntityPlayerMP p, int delta) {
        ItemStack drill = held(p);
        if (!ToolLevelSC.isDrill(drill) || delta == 0 || !BladeSingularSC.wheelReady(p)) {
            return;                                               // too fast: dropped (the client names the mode only once it changes)
        }
        int[] next = DrillZoneSC.nextMode(ItemDrillSC.blackHoleSize(drill), ItemDrillSC.tunnelDepth(drill), delta,
                ToolLevelSC.effectiveLevel(p, drill));
        ItemDrillSC.setBlackHoleSize(drill, next[0]);
        ItemDrillSC.setTunnelDepth(drill, next[1]);
        p.worldObj.playSoundAtEntity(p, "random.click", 0.3F, next[0] > 0 ? 0.6F + next[0] * 0.05F : 0.5F);
        p.inventoryContainer.detectAndSendChanges();
    }

    /** The black hole's depth: 1 <-> 3 (a tunnel). Shift + right-click in the air with the drill. */
    public static void toggleDepth(EntityPlayer p) {
        ItemStack drill = held(p);
        if (!ToolLevelSC.isDrill(drill) || p.worldObj.isRemote) {
            return;
        }
        int depth = ItemDrillSC.tunnelDepth(drill) == 1 ? DrillZoneSC.HOLE_TUNNEL : 1;
        ItemDrillSC.setTunnelDepth(drill, depth);
        p.addChatComponentMessage(new ChatComponentTranslation("sc.drill.hole.depth", String.valueOf(depth)));
        p.worldObj.playSoundAtEntity(p, "random.click", 0.3F, depth == 1 ? 0.7F : 0.9F);
        p.inventoryContainer.detectAndSendChanges();
    }

    // ------------------------------------------------------------------ torch, laser

    /** Right-click: a torch from the inventory goes where a torch in hand would. */
    public static boolean placeTorch(EntityPlayer p, World w, int x, int y, int z, int side, float hx, float hy, float hz) {
        Item torch = Item.getItemFromBlock(Blocks.torch);
        for (int i = 0; i < p.inventory.mainInventory.length; i++) {
            ItemStack s = p.inventory.mainInventory[i];
            if (s != null && s.getItem() == torch) {
                boolean placed = s.tryPlaceItemIntoWorld(p, w, x, y, z, side, hx, hy, hz);
                if (s.stackSize <= 0) {
                    p.inventory.mainInventory[i] = null;
                }
                return placed;
            }
        }
        return false;
    }

    /** Exo, on its key: a beam cuts LASER_RANGE blocks ahead (same rules and price as the area modes). */
    public static void laser(EntityPlayer player) {
        ItemStack drill = held(player);
        if (!(player instanceof EntityPlayerMP) || drill == null || !ItemDrillSC.isEnabled(drill, DrillFeature.LASER)
                || ItemDrillSC.overheated(drill)) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) player;
        NBTTagCompound data = p.getEntityData();
        long now = p.worldObj.getTotalWorldTime();
        if (now - data.getLong(LAST_LASER) < DrillFeature.LASER_COOLDOWN) {
            return;
        }
        Vec3 eyes = Vec3.createVectorHelper(p.posX, p.posY + p.getEyeHeight(), p.posZ);
        Vec3 look = p.getLookVec();
        Set<Long> seen = new LinkedHashSet<Long>();
        Harvest h = new Harvest(p, drill);
        for (double d = 1; d <= DrillFeature.LASER_RANGE; d += 0.25) {
            int x = MathHelper.floor_double(eyes.xCoord + look.xCoord * d);
            int y = MathHelper.floor_double(eyes.yCoord + look.yCoord * d);
            int z = MathHelper.floor_double(eyes.zCoord + look.zCoord * d);
            if (!seen.add(key(x, y, z)) || p.worldObj.isAirBlock(x, y, z)) {
                continue;
            }
            if (!areaTakes(p, x, y, z)) {
                break;                                            // stopped by something it can't take
            }
            if (!h.breakOne(x, y, z, false, extraCost(p, drill))) {
                p.addChatComponentMessage(new ChatComponentTranslation("sc.drill.empty"));
                break;
            }
        }
        h.deliver(p.posX, p.posY + 0.5, p.posZ);
        if (h.broken == 0) {
            return;                                               // nothing cut (no EU / air): no heat, no cooldown, no beam
        }
        data.setLong(LAST_LASER, now);
        heat(p, drill, DrillFeature.LASER_HEAT);
        p.worldObj.playSoundAtEntity(p, "mob.ghast.fireball", 0.6F, 2F);
        if (p.worldObj instanceof net.minecraft.world.WorldServer) {
            for (double d = 1; d <= DrillFeature.LASER_RANGE; d += 0.5) {
                ((net.minecraft.world.WorldServer) p.worldObj).func_147487_a("reddust", eyes.xCoord + look.xCoord * d,
                        eyes.yCoord + look.yCoord * d - 0.2, eyes.zCoord + look.zCoord * d, 2, 0.05, 0.05, 0.05, 0);
            }
        }
    }

    // ------------------------------------------------------------------ switches (from the client)

    /** A switch from the K screen: a function the drill lacks - or, switching on, one its level hasn't opened - is refused. */
    public static void toggle(EntityPlayer p, ItemStack drill, DrillFeature f, boolean on) {
        DrillType t = ItemDrillSC.typeOf(drill);
        if (t == null || !f.availableIn(t) || on && !unlocked(p, drill, f)) {
            return;
        }
        ItemDrillSC.setEnabled(drill, f, on);
        p.inventoryContainer.detectAndSendChanges();
    }

    /** For the set-bonus line of the armour screen. */
    public static boolean setBonus(EntityPlayer p, ItemStack drill) {
        DrillType t = ItemDrillSC.typeOf(drill);
        return t != null && fullSetOf(p, t);
    }

    public static ArmorSuit suitOf(ItemStack drill) {
        DrillType t = ItemDrillSC.typeOf(drill);
        return t == null ? null : t.suit;
    }
}
