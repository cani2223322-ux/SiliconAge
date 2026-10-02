package com.sc.block;

import java.util.Random;

import com.sc.Reference;
import com.sc.item.ArmorLogicSC;

import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.MaterialTransparent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/**
 * The krypton searchlight's light (ArmorLogicSC.searchlight): an invisible full-bright block in
 * plain air - no collision, can't be pointed at or broken, drops nothing, and goes out by itself
 * within ~10 ticks once no player's searchlight points at it any more (a scheduled tick - saved
 * with the chunk, so it also fires after a reload - and the random ticks as a backstop for one
 * left behind by a crash).
 */
public class BlockLightSC extends Block {

    /** Ticks between the checks whether someone still points at it. */
    public static final int CHECK_TICKS = 10;
    /** Its light level (a torch is 14). */
    public static final int LIGHT = 13;

    /**
     * Air-like, but not Material.air itself: WorldServer drops scheduled ticks of Material.air
     * blocks (scheduleBlockUpdate does nothing for them), so the light would only ever go out on a
     * random tick, minutes later. MaterialTransparent is already replaceable (its constructor).
     */
    public static final Material LIGHT_MATERIAL = new MaterialTransparent(MapColor.airColor) {
        {
            setReplaceable();
            setNoPushMobility();
        }
    };

    public BlockLightSC() {
        super(LIGHT_MATERIAL);
        lightValue = LIGHT;
        setBlockUnbreakable();
        setResistance(0F);
        setTickRandomly(true);
        setBlockName(Reference.ASSETS + ".light");
        setBlockTextureName("glass");
    }

    @Override
    public int getRenderType() {
        return -1;
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
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World w, int x, int y, int z) {
        return null;
    }

    @Override
    public boolean canCollideCheck(int meta, boolean hitIfLiquid) {
        return false;
    }

    @Override
    public MovingObjectPosition collisionRayTrace(World w, int x, int y, int z, Vec3 start, Vec3 end) {
        return null;
    }

    @Override
    public boolean isReplaceable(net.minecraft.world.IBlockAccess w, int x, int y, int z) {
        return true;
    }

    @Override
    public boolean isAir(net.minecraft.world.IBlockAccess w, int x, int y, int z) {
        return true;
    }

    @Override
    public Item getItemDropped(int meta, Random rand, int fortune) {
        return null;
    }

    @Override
    public int quantityDropped(Random rand) {
        return 0;
    }

    @Override
    public void dropBlockAsItemWithChance(World w, int x, int y, int z, int meta, float chance, int fortune) {
    }

    @Override
    public void onBlockAdded(World w, int x, int y, int z) {
        if (!w.isRemote) {
            w.scheduleBlockUpdate(x, y, z, this, CHECK_TICKS);
        }
    }

    @Override
    public void updateTick(World w, int x, int y, int z, Random rand) {
        if (w.isRemote) {
            return;
        }
        if (litBySomeone(w, x, y, z)) {
            w.scheduleBlockUpdate(x, y, z, this, CHECK_TICKS);
        } else {
            w.setBlock(x, y, z, net.minecraft.init.Blocks.air, 0, 2);
        }
    }

    /** A player in this world whose searchlight stands here and was pointed at it in the last second. */
    public static boolean litBySomeone(World w, int x, int y, int z) {
        long now = w.getTotalWorldTime();
        for (Object o : w.playerEntities) {
            NBTTagCompound d = ((EntityPlayer) o).getEntityData();
            if (d.getBoolean(ArmorLogicSC.LIGHT_ON) && d.getInteger(ArmorLogicSC.LIGHT_X) == x && d.getInteger(ArmorLogicSC.LIGHT_Y) == y
                    && d.getInteger(ArmorLogicSC.LIGHT_Z) == z && d.getInteger(ArmorLogicSC.LIGHT_DIM) == w.provider.dimensionId
                    && now - d.getLong(ArmorLogicSC.LIGHT_AT) <= 20 && now >= d.getLong(ArmorLogicSC.LIGHT_AT)) {
                return true;
            }
        }
        return false;
    }
}
