package com.sc.item;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.sc.util.ArmorSuit;
import com.sc.util.DrillFeature;
import com.sc.util.DrillType;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

/**
 * What the drills do, on the server. The drill pays per block from its own small battery, then -
 * worn energy armour - from the chestplate (any suit). Set bonuses (the drill with the full suit
 * of its own tier):
 * - Nano: a quarter less EU per block,
 * - Quantum: fortune one level higher,
 * - Exo: the area / tunnel / vein / laser blocks beyond the first cost half.
 * The drill breaks blocks itself (onBlockStartBreak): area, vein, tunnel, silk touch / fortune up
 * to V, autosmelt, magnet, the linked chest. Blocks with a tile entity are left to vanilla (their
 * own drop rules) and never taken by the area modes; neither is anything unbreakable or liquid.
 */
public final class DrillLogicSC {

    private static final String LAST_LASER = "scDrillLaser", DIG_AT = "scDrillDigAt";

    private DrillLogicSC() {
    }

    public static ItemStack held(EntityPlayer p) {
        ItemStack s = p.getCurrentEquippedItem();
        return s != null && s.getItem() instanceof ItemDrillSC ? s : null;
    }

    private static boolean fullSetOf(EntityPlayer p, DrillType type) {
        return ArmorLogicSC.fullSet(p) == type.suit;
    }

    /** Worn energy armour feeding the drill: the chestplate, any suit. */
    private static ItemStack feeder(EntityPlayer p) {
        return ArmorLogicSC.piece(p, 1);
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
        return ItemDrillSC.chargeOf(drill) + (chest == null ? 0 : ItemArmorSC.chargeOf(chest)) >= need;
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
            return 0.5F;
        }
        if (ItemDrillSC.isEnabled(drill, DrillFeature.ECO)) {
            speed /= 2;
        }
        if (areaOn(drill)) {
            speed /= 2;
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

    private static boolean areaOn(ItemStack drill) {
        return ItemDrillSC.isEnabled(drill, DrillFeature.AREA_3X3) || ItemDrillSC.isEnabled(drill, DrillFeature.AREA_5X5)
                || ItemDrillSC.isEnabled(drill, DrillFeature.TUNNEL);
    }

    public static int fortune(EntityPlayer p, ItemStack drill) {
        DrillType t = ItemDrillSC.typeOf(drill);
        if (!ItemDrillSC.isEnabled(drill, DrillFeature.FORTUNE) || ItemDrillSC.isEnabled(drill, DrillFeature.SILK)) {
            return 0;
        }
        return t.fortune + (t == DrillType.QUANTUM && fullSetOf(p, t) ? 1 : 0);
    }

    // ------------------------------------------------------------------ digging

    /**
     * The block at x, y, z is being broken with the drill (server). @return true when the drill
     * broke it itself; false leaves it to vanilla (a tile-entity block, out of charge, overheated).
     */
    public static boolean dig(EntityPlayer player, ItemStack drill, int x, int y, int z) {
        if (!(player instanceof EntityPlayerMP) || player.capabilities.isCreativeMode) {
            return false;
        }
        EntityPlayerMP p = (EntityPlayerMP) player;
        World w = p.worldObj;
        DrillType t = ItemDrillSC.typeOf(drill);
        Block center = w.getBlock(x, y, z);
        int centerMeta = w.getBlockMetadata(x, y, z);
        if (ItemDrillSC.overheated(drill) || !canPay(p, drill, t.euPerBlock)) {
            return false;
        }
        List<int[]> targets = targets(p, drill, x, y, z, center, centerMeta);
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
        h.deliver(x + 0.5, y + 0.5, z + 0.5);
        return !centerByVanilla;
    }

    /** Area / tunnel / vein blocks after the first: the Exo set halves them. */
    private static int extraCost(EntityPlayer p, ItemStack drill) {
        DrillType t = ItemDrillSC.typeOf(drill);
        return t == DrillType.EXO && fullSetOf(p, t) ? (t.euPerBlock + 1) / 2 : t.euPerBlock;
    }

    /** The blocks one dig takes, the clicked one first: a vein (ore), a tunnel, a 5x5 or 3x3 face, or just the one. */
    private static List<int[]> targets(EntityPlayer p, ItemStack drill, int x, int y, int z, Block center, int meta) {
        List<int[]> out = new ArrayList<int[]>();
        out.add(new int[]{x, y, z});
        if (p.isSneaking() || !ItemDrillSC.suits(center, meta)) {
            return out;                                           // sneaking, or a torch / log / grass clicked: just that one
        }
        World w = p.worldObj;
        // an ore the drill can't harvest: just that one (the vein used to go too - broken with no drops)
        if (ItemDrillSC.isEnabled(drill, DrillFeature.VEIN) && isOre(norm(center), meta) && center.canHarvestBlock(p, meta)) {
            Set<Long> seen = new LinkedHashSet<Long>();
            List<int[]> queue = new ArrayList<int[]>();
            queue.add(new int[]{x, y, z});
            seen.add(key(x, y, z));
            for (int qi = 0; qi < queue.size() && out.size() < DrillFeature.VEIN_MAX; qi++) {
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
                            if (out.size() < DrillFeature.VEIN_MAX) {
                                out.add(b);
                            }
                        }
                    }
                }
            }
            return out;
        }
        int radius = ItemDrillSC.isEnabled(drill, DrillFeature.AREA_5X5) ? 2
                : ItemDrillSC.isEnabled(drill, DrillFeature.AREA_3X3) || ItemDrillSC.isEnabled(drill, DrillFeature.TUNNEL) ? 1 : 0;
        int depth = ItemDrillSC.isEnabled(drill, DrillFeature.TUNNEL) ? DrillFeature.TUNNEL_DEPTH : 1;
        if (radius == 0 && depth == 1) {
            return out;
        }
        int side = hitSide(p, x, y, z);
        int nx = 0, ny = 0, nz = 0;                             // the clicked face's outward normal
        switch (side) {
            case 0: ny = -1; break;
            case 1: ny = 1; break;
            case 2: nz = -1; break;
            case 3: nz = 1; break;
            case 4: nx = -1; break;
            default: nx = 1; break;
        }
        for (int d = 0; d < depth; d++) {
            int cx = x - nx * d, cy = y - ny * d, cz = z - nz * d;   // into the wall, away from the face
            for (int a = -radius; a <= radius; a++) {
                for (int b = -radius; b <= radius; b++) {
                    int bx = cx, by = cy, bz = cz;
                    if (ny != 0) {
                        bx += a;
                        bz += b;
                    } else if (nz != 0) {
                        bx += a;
                        by += b;
                    } else {
                        bz += a;
                        by += b;
                    }
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
        return ((long) x & 0x3FFFFFF) << 38 | ((long) y & 0xFFF) << 26 | ((long) z & 0x3FFFFFF);
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

    private static boolean isOre(Block b, int meta) {
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

    /** One dig's harvest: breaks blocks one by one, collects the drops and XP, hands them over at the end. */
    private static final class Harvest {
        final EntityPlayerMP p;
        final ItemStack drill;
        final World w;
        final int fortune;
        final boolean silk;
        final List<ItemStack> drops = new ArrayList<ItemStack>();
        int xp;

        Harvest(EntityPlayerMP p, ItemStack drill) {
            this.p = p;
            this.drill = drill;
            this.w = p.worldObj;
            this.fortune = fortune(p, drill);
            this.silk = ItemDrillSC.isEnabled(drill, DrillFeature.SILK);
        }

        /** @return false when it couldn't be paid for (the dig stops there) */
        boolean breakOne(int x, int y, int z, boolean first, int eu) {
            Block block = w.getBlock(x, y, z);
            int meta = w.getBlockMetadata(x, y, z);
            if (block.isAir(w, x, y, z) || block.getBlockHardness(w, x, y, z) < 0) {
                return true;
            }
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
                Item item = Item.getItemFromBlock(block);
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
            ItemDrillSC.addHeat(drill, DrillFeature.BLOCK_HEAT);
            return true;
        }

        /** Drops to the linked chest (link), the inventory (magnet) or the ground at the clicked block; XP to the player. */
        void deliver(double x, double y, double z) {
            IInventory chest = ItemDrillSC.isEnabled(drill, DrillFeature.LINK) ? linked(p, drill) : null;
            for (ItemStack s : drops) {
                ItemStack left = chest == null ? s : insert(chest, s);
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
            p.inventoryContainer.detectAndSendChanges();
        }
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

    /** The linked chest if it's in this dimension, loaded and still an inventory; else null (a message once in a while). */
    private static IInventory linked(EntityPlayer p, ItemStack drill) {
        int[] l = ItemDrillSC.link(drill);
        if (l == null || l[3] != p.worldObj.provider.dimensionId || !p.worldObj.blockExists(l[0], l[1], l[2])) {
            return null;
        }
        TileEntity te = p.worldObj.getTileEntity(l[0], l[1], l[2]);
        return te instanceof IInventory ? (IInventory) te : null;
    }

    /** Puts as much as fits into the inventory. @return what's left, or null */
    private static ItemStack insert(IInventory inv, ItemStack s) {
        ItemStack left = s.copy();
        for (int i = 0; i < inv.getSizeInventory() && left.stackSize > 0; i++) {
            if (!inv.isItemValidForSlot(i, left)) {
                continue;
            }
            ItemStack in = inv.getStackInSlot(i);
            int limit = Math.min(inv.getInventoryStackLimit(), left.getMaxStackSize());
            if (in == null) {
                ItemStack put = left.copy();
                put.stackSize = Math.min(limit, left.stackSize);
                inv.setInventorySlotContents(i, put);
                left.stackSize -= put.stackSize;
            } else if (in.isItemEqual(left) && ItemStack.areItemStackTagsEqual(in, left) && in.stackSize < limit) {
                int move = Math.min(limit - in.stackSize, left.stackSize);
                ItemStack grown = in.copy();
                grown.stackSize += move;
                inv.setInventorySlotContents(i, grown);
                left.stackSize -= move;
            }
        }
        return left.stackSize > 0 ? left : null;
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
        data.setLong(LAST_LASER, now);
        Vec3 eyes = Vec3.createVectorHelper(p.posX, p.posY + p.getEyeHeight(), p.posZ);
        Vec3 look = p.getLookVec();
        Set<Long> seen = new LinkedHashSet<Long>();
        Harvest h = new Harvest(p, drill);
        int cut = 0;
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
            cut++;
        }
        h.deliver(p.posX, p.posY + 0.5, p.posZ);
        ItemDrillSC.addHeat(drill, DrillFeature.LASER_HEAT);
        p.worldObj.playSoundAtEntity(p, "mob.ghast.fireball", 0.6F, 2F);
        if (p.worldObj instanceof net.minecraft.world.WorldServer) {
            for (double d = 1; d <= DrillFeature.LASER_RANGE; d += 0.5) {
                ((net.minecraft.world.WorldServer) p.worldObj).func_147487_a("reddust", eyes.xCoord + look.xCoord * d,
                        eyes.yCoord + look.yCoord * d - 0.2, eyes.zCoord + look.zCoord * d, 2, 0.05, 0.05, 0.05, 0);
            }
        }
    }

    // ------------------------------------------------------------------ switches (from the client)

    public static void toggle(EntityPlayer p, ItemStack drill, DrillFeature f, boolean on) {
        DrillType t = ItemDrillSC.typeOf(drill);
        if (t == null || !f.availableIn(t)) {
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
