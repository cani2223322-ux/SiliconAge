package com.sc.block;

import java.util.ArrayList;
import java.util.List;

import com.sc.Reference;
import com.sc.conduit.ConduitKind;
import com.sc.conduit.ConduitMode;
import com.sc.energy.CableType;
import com.sc.tileentity.TileEntityConduitBundleSC;
import com.sc.util.PipeType;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * The conduit bundle block (Ender IO style): a cable, a pipe and a pneumatic tube can share one
 * block, each in its own slot, each a thin core with an arm to every side it connects on. Every
 * part is its own box for collisions and clicks, so you can reach past a bundle, and what you
 * look at is what you act on:
 * - right-click with the cable / pipe / tube item adds it to the bundle (ItemBlockConduitSC),
 * - breaking takes out only the part you are looking at,
 * - right-click with an empty hand on an arm or connector opens that side's connector menu
 *   (GuiConduitSC: extract / insert / disabled, Ender IO style); on a core, the menu of the side
 *   of the core you clicked - which is how a switched-off side is switched back on,
 * - a wrench (or sneak + right-click with an empty hand) cycles that side's mode instead.
 * State lives in TileEntityConduitBundleSC; the model is client.ConduitRenderer.
 */
public class BlockConduitSC extends Block {

    /** Assigned by ClientProxy from RenderingRegistry; 0 (standard cube) until then / on a server. */
    public static int renderId;

    /**
     * Solid, tool-free, non-pushable material. Material.circuits (redstone-dust material) let
     * flowing water wash a line away, lava delete it and pistons break it.
     */
    public static final Material CONDUIT = new Material(net.minecraft.block.material.MapColor.ironColor);

    /** Part id in MovingObjectPosition.subHit: kind * 8 + (0..5 = arm on that side, CORE = the core). */
    public static final int CORE = 6;

    // icons for the renderer, filled in registerBlockIcons
    public static IIcon[] cableArm, cableCore, pipeArm, pipeCore;
    public static IIcon tubeArm, tubeCore, connector, connectorExtract, connectorInsert, connectorBoth;

    public BlockConduitSC() {
        super(CONDUIT);
        setBlockName(Reference.ASSETS + ".conduitBundle");
        setHardness(0.8F);
        setResistance(4.0F);
        setStepSound(soundTypeMetal);
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int meta) {
        return new TileEntityConduitBundleSC();
    }

    public static TileEntityConduitBundleSC bundle(IBlockAccess world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        return te instanceof TileEntityConduitBundleSC ? (TileEntityConduitBundleSC) te : null;
    }

    // ------------------------------------------------------------------ shape

    /** Core box of a part: {minX, minY, minZ, maxX, maxY, maxZ}. */
    public static float[] coreBox(ConduitKind kind) {
        float lo = kind.lo(), hi = kind.hi();
        return new float[]{lo, lo, lo, hi, hi, hi};
    }

    /** Arm of a part from its core out to the block edge on side `dir`. */
    public static float[] armBox(ConduitKind kind, ForgeDirection dir) {
        float lo = kind.lo(), hi = kind.hi();
        float[] b = {lo, lo, lo, hi, hi, hi};
        switch (dir) {
            case DOWN: b[1] = 0; b[4] = lo; break;
            case UP: b[1] = hi; b[4] = 1; break;
            case NORTH: b[2] = 0; b[5] = lo; break;
            case SOUTH: b[2] = hi; b[5] = 1; break;
            case WEST: b[0] = 0; b[3] = lo; break;
            default: b[0] = hi; b[3] = 1; break;    // EAST
        }
        return b;
    }

    /** Every box of the bundle with its part id (kind * 8 + side, or + CORE). */
    public static List<int[]> parts(TileEntityConduitBundleSC te) {
        List<int[]> out = new ArrayList<int[]>();
        if (te == null) {
            return out;
        }
        for (ConduitKind kind : ConduitKind.values()) {
            if (!te.has(kind)) {
                continue;
            }
            out.add(new int[]{kind.ordinal() * 8 + CORE});
            for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
                if (te.connects(kind, dir)) {
                    out.add(new int[]{kind.ordinal() * 8 + dir.ordinal()});
                }
            }
        }
        return out;
    }

    public static float[] boxOf(int id) {
        ConduitKind kind = ConduitKind.values()[id / 8];
        int part = id % 8;
        return part == CORE ? coreBox(kind) : armBox(kind, ForgeDirection.getOrientation(part));
    }

    /** Set while collisionRayTrace tests one part (Block.collisionRayTrace calls setBlockBoundsBasedOnState first). Per thread. */
    private static final ThreadLocal<Boolean> TRACING_PART = new ThreadLocal<Boolean>();

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        if (Boolean.TRUE.equals(TRACING_PART.get())) {
            return;
        }
        List<int[]> parts = parts(bundle(world, x, y, z));
        if (parts.isEmpty()) {
            setBlockBounds(0.375F, 0.375F, 0.375F, 0.625F, 0.625F, 0.625F);
            return;
        }
        float[] u = {1, 1, 1, 0, 0, 0};
        for (int[] p : parts) {
            float[] b = boxOf(p[0]);
            for (int i = 0; i < 3; i++) {
                u[i] = Math.min(u[i], b[i]);
                u[i + 3] = Math.max(u[i + 3], b[i + 3]);
            }
        }
        setBlockBounds(u[0], u[1], u[2], u[3], u[4], u[5]);
    }

    @Override
    public void addCollisionBoxesToList(World world, int x, int y, int z, AxisAlignedBB mask, List list, Entity entity) {
        for (int[] p : parts(bundle(world, x, y, z))) {
            float[] b = boxOf(p[0]);
            setBlockBounds(b[0], b[1], b[2], b[3], b[4], b[5]);
            super.addCollisionBoxesToList(world, x, y, z, mask, list, entity);
        }
        setBlockBoundsBasedOnState(world, x, y, z);
    }

    /** Traces every part on its own; the hit carries the part id in subHit. */
    @Override
    public MovingObjectPosition collisionRayTrace(World world, int x, int y, int z, Vec3 start, Vec3 end) {
        MovingObjectPosition best = null;
        double bestDist = Double.MAX_VALUE;
        for (int[] p : parts(bundle(world, x, y, z))) {
            float[] b = boxOf(p[0]);
            setBlockBounds(b[0], b[1], b[2], b[3], b[4], b[5]);
            TRACING_PART.set(Boolean.TRUE);
            MovingObjectPosition hit;
            try {
                hit = super.collisionRayTrace(world, x, y, z, start, end);
            } finally {
                TRACING_PART.set(Boolean.FALSE);
            }
            if (hit != null) {
                double d = hit.hitVec.squareDistanceTo(start);
                if (d < bestDist) {
                    bestDist = d;
                    best = hit;
                    best.subHit = p[0];
                }
            }
        }
        setBlockBoundsBasedOnState(world, x, y, z);
        return best;
    }

    /** The part the player is looking at in this block, or null. */
    public MovingObjectPosition partUnderCursor(World world, int x, int y, int z, EntityPlayer player) {
        double reach = 5.0D;
        // eye height as vanilla's Item.getMovingObjectPositionFromPlayer: the client's posY is already at eye level
        double eyeY = player.posY + (world.isRemote ? player.getEyeHeight() - player.getDefaultEyeHeight() : player.getEyeHeight());
        Vec3 eye = Vec3.createVectorHelper(player.posX, eyeY, player.posZ);
        Vec3 look = player.getLook(1.0F);
        Vec3 end = eye.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
        return collisionRayTrace(world, x, y, z, eye, end);
    }

    // ------------------------------------------------------------------ interaction

    /** A wrench from any common mod: BuildCraft / CoFH / Ender IO tools, or anything named a wrench / hammer. */
    public static boolean isWrench(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        for (Class<?> c = stack.getItem().getClass(); c != null; c = c.getSuperclass()) {
            String simple = c.getSimpleName().toLowerCase(java.util.Locale.ROOT);
            if (simple.contains("wrench") || simple.contains("hammer")) {
                return true;
            }
            for (Class<?> i : c.getInterfaces()) {
                String n = i.getName();
                if (n.equals("buildcraft.api.tools.IToolWrench") || n.equals("cofh.api.item.IToolHammer")
                        || n.equals("crazypants.enderio.api.tool.ITool")) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        ItemStack held = player.getCurrentEquippedItem();
        boolean menu = held == null && !player.isSneaking();
        boolean wrench = isWrench(held) || (held == null && player.isSneaking());
        if (!wrench && !menu) {
            return false;
        }
        TileEntityConduitBundleSC te = bundle(world, x, y, z);
        MovingObjectPosition hit = partUnderCursor(world, x, y, z, player);
        if (te == null || hit == null) {
            return false;
        }
        ConduitKind kind = ConduitKind.values()[hit.subHit / 8];
        int part = hit.subHit % 8;
        ForgeDirection dir = ForgeDirection.getOrientation(part == CORE ? side : part);
        if (menu) {
            if (!world.isRemote) {
                player.openGui(com.sc.SCMod.instance, com.sc.handler.GuiHandlerSC.CONDUIT_GUI_BASE + dir.ordinal(), world, x, y, z);
            }
            return true;
        }
        if (!world.isRemote) {
            ConduitMode mode = te.cycleMode(kind, dir);
            player.addChatComponentMessage(new ChatComponentTranslation("sc.conduit.mode",
                    new ChatComponentTranslation("sc.conduit.kind." + kind.name().toLowerCase(java.util.Locale.ROOT)),
                    new ChatComponentTranslation("sc.side." + dir.name().toLowerCase(java.util.Locale.ROOT)),
                    new ChatComponentTranslation("sc.conduit.mode." + mode.menuKey(kind,
                            !(world.getTileEntity(x + dir.offsetX, y + dir.offsetY, z + dir.offsetZ) instanceof TileEntityConduitBundleSC)))));
        }
        return true;
    }

    /** The client still calls this older form (PlayerControllerMP) - same rules, or the whole bundle blinks out. */
    @Override
    @SuppressWarnings("deprecation")
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z) {
        return removedByPlayer(world, player, x, y, z, false);
    }

    /** Breaking takes out the part under the cursor; the block goes only with its last part. */
    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        TileEntityConduitBundleSC te = bundle(world, x, y, z);
        if (te == null) {
            return super.removedByPlayer(world, player, x, y, z, willHarvest);
        }
        MovingObjectPosition hit = partUnderCursor(world, x, y, z, player);
        ConduitKind kind = hit != null ? ConduitKind.values()[hit.subHit / 8] : firstPart(te);
        if (kind == null) {
            return super.removedByPlayer(world, player, x, y, z, willHarvest);
        }
        ItemStack drop = te.removePart(kind);
        if (drop != null && !world.isRemote && !player.capabilities.isCreativeMode) {
            EntityItem item = new EntityItem(world, x + 0.5, y + 0.5, z + 0.5, drop);
            item.delayBeforeCanPickup = 10;
            world.spawnEntityInWorld(item);
        }
        if (te.isEmpty()) {
            return world.setBlockToAir(x, y, z);
        }
        return false;
    }

    private static ConduitKind firstPart(TileEntityConduitBundleSC te) {
        for (ConduitKind kind : ConduitKind.values()) {
            if (te.has(kind)) {
                return kind;
            }
        }
        return null;
    }

    /** Everything that is still in the block when it goes some other way (an explosion...). */
    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        ArrayList<ItemStack> drops = new ArrayList<ItemStack>();
        TileEntityConduitBundleSC te = bundle(world, x, y, z);
        if (te != null) {
            drops.addAll(te.allPartStacks());
        }
        return drops;
    }

    /** However the block goes, the tube connectors' filters and upgrades come out (the real stacks, once). */
    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntityConduitBundleSC te = bundle(world, x, y, z);
        if (te != null) {
            te.spillConnectorItems();
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    @Override
    public ItemStack getPickBlock(MovingObjectPosition target, World world, int x, int y, int z) {
        TileEntityConduitBundleSC te = bundle(world, x, y, z);
        if (te == null) {
            return null;
        }
        ConduitKind kind = target != null && target.subHit >= 0 && target.subHit / 8 < ConduitKind.values().length
                ? ConduitKind.values()[target.subHit / 8] : firstPart(te);
        return kind == null ? null : te.partStack(kind);
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbour) {
        TileEntityConduitBundleSC te = bundle(world, x, y, z);
        if (te != null) {
            te.onNeighbourChanged();
        }
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void registerBlockIcons(IIconRegister register) {
        String p = Reference.ASSETS + ":conduit/";
        cableArm = new IIcon[CableType.values().length];
        cableCore = new IIcon[CableType.values().length];
        for (CableType t : CableType.values()) {
            cableArm[t.ordinal()] = register.registerIcon(p + t.textureName);
            cableCore[t.ordinal()] = register.registerIcon(p + t.textureName + "Core");
        }
        pipeArm = new IIcon[PipeType.values().length];
        pipeCore = new IIcon[PipeType.values().length];
        for (PipeType t : PipeType.values()) {
            pipeArm[t.ordinal()] = register.registerIcon(p + t.textureName);
            pipeCore[t.ordinal()] = register.registerIcon(p + t.textureName + "Core");
        }
        tubeArm = register.registerIcon(p + "tubeItemPneumatic");
        tubeCore = register.registerIcon(p + "tubeItemPneumaticCore");
        connector = register.registerIcon(p + "connector");
        connectorExtract = register.registerIcon(p + "connectorExtract");
        connectorInsert = register.registerIcon(p + "connectorInsert");
        connectorBoth = register.registerIcon(p + "connectorBoth");
        blockIcon = connector;
    }

    @Override
    public IIcon getIcon(int side, int meta) {
        return connector;
    }

    @Override
    public int getRenderType() {
        return renderId;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public boolean isNormalCube() {
        return false;
    }

    @Override
    public boolean isSideSolid(IBlockAccess world, int x, int y, int z, ForgeDirection side) {
        return false;
    }
}
