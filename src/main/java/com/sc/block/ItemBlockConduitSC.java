package com.sc.block;

import com.sc.conduit.ConduitKind;
import com.sc.init.ModBlocks;
import com.sc.tileentity.TileEntityConduitBundleSC;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Cable / pipe / tube items (Ender IO style placement): used on a bundle that has no conduit of
 * this kind yet, the conduit goes into that bundle - also when you click a machine through the
 * gap next to a bundle standing in front of it. Otherwise a new bundle is placed.
 */
public abstract class ItemBlockConduitSC extends ItemBlock {

    protected ItemBlockConduitSC(Block block) {
        super(block);
    }

    protected abstract ConduitKind kind();

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
                             float hitX, float hitY, float hitZ) {
        if (stack.stackSize <= 0) {
            return false;
        }
        if (addTo(stack, player, world, x, y, z, side)) {
            return true;
        }
        Block clicked = world.getBlock(x, y, z);
        if (clicked == Blocks.snow_layer && (world.getBlockMetadata(x, y, z) & 7) < 1) {
            side = 1;
        } else if (!clicked.isReplaceable(world, x, y, z)) {
            ForgeDirection d = ForgeDirection.getOrientation(side);
            x += d.offsetX;
            y += d.offsetY;
            z += d.offsetZ;
        }
        if (addTo(stack, player, world, x, y, z, side)) {
            return true;
        }
        if (y < 0 || y >= 255 || !player.canPlayerEdit(x, y, z, side, stack)
                || !world.getBlock(x, y, z).isReplaceable(world, x, y, z)) {
            return false;
        }
        float lo = kind().lo(), hi = kind().hi();
        if (!world.checkNoEntityCollision(AxisAlignedBB.getBoundingBox(x + lo, y + lo, z + lo, x + hi, y + hi, z + hi))) {
            return false;
        }
        if (!world.setBlock(x, y, z, ModBlocks.conduitBundle, 0, 3)) {
            return false;
        }
        TileEntityConduitBundleSC te = BlockConduitSC.bundle(world, x, y, z);
        if (te != null) {
            te.addPart(kind(), stack.getItemDamage());
        }
        placed(stack, world, x, y, z);
        if (kind() == ConduitKind.CABLE) {
            com.sc.energy.CableWarningSC.cablePlaced(world, x, y, z, player);
        }
        return true;
    }

    /** Puts the conduit into the bundle at (x, y, z) if there is one without this kind. */
    private boolean addTo(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side) {
        TileEntityConduitBundleSC te = BlockConduitSC.bundle(world, x, y, z);
        if (te == null || te.has(kind()) || !player.canPlayerEdit(x, y, z, side, stack)
                || com.sc.ShieldEventHandler.privateFor(world, player, x, y, z)) {
            return false;
        }
        if (world.isRemote) {
            return true;    // the server decides (claims, private fields); its sync shows the result
        }
        // no block is placed, so Forge fires no PlaceEvent: claim mods (FTB Utilities, GriefPrevention) get one here
        if (net.minecraftforge.event.ForgeEventFactory.onPlayerBlockPlace(player,
                net.minecraftforge.common.util.BlockSnapshot.getBlockSnapshot(world, x, y, z),
                ForgeDirection.getOrientation(side)).isCanceled()) {
            return false;
        }
        te.addPart(kind(), stack.getItemDamage());
        placed(stack, world, x, y, z);
        if (kind() == ConduitKind.CABLE) {
            com.sc.energy.CableWarningSC.cablePlaced(world, x, y, z, player);
        }
        return true;
    }

    private void placed(ItemStack stack, World world, int x, int y, int z) {
        Block block = ModBlocks.conduitBundle;
        world.playSoundEffect(x + 0.5, y + 0.5, z + 0.5, block.stepSound.func_150496_b(),
                (block.stepSound.getVolume() + 1.0F) / 2.0F, block.stepSound.getPitch() * 0.8F);
        stack.stackSize--;
    }

    /** The client asks before sending a placement; the real checks are in onItemUse. */
    @Override
    public boolean func_150936_a(World world, int x, int y, int z, int side, EntityPlayer player, ItemStack stack) {
        return true;
    }
}
