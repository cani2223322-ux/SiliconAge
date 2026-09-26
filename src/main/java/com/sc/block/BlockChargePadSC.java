package com.sc.block;

import com.sc.Reference;
import com.sc.energy.Tier;
import com.sc.tileentity.TileEntityChargePadSC;
import com.sc.tileentity.TileEntityEnergyStorageSC;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Charge pad LV / MV / HV / EV (metadata = Tier ordinal): an energy storage with a pad on top
 * (TileEntityChargePadSC) - stand on it and the armour and weapons charge. Same casing, slot,
 * screen and output face as the storage; the output never faces up (the pad is there).
 */
public class BlockChargePadSC extends BlockEnergyStorageSC {

    private IIcon[] topIdle, topActive;

    public BlockChargePadSC() {
        setBlockName(Reference.ASSETS + ".chargePad");
    }

    @Override
    public TileEntity createTileEntity(World world, int meta) {
        TileEntityChargePadSC te = new TileEntityChargePadSC();
        te.setStorageTier(tierFor(meta));
        return te;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, x, y, z, placer, stack);
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityEnergyStorageSC && ((TileEntityEnergyStorageSC) te).getFacing() == ForgeDirection.UP) {
            // looking down at it: the side facing the placer instead
            int quarter = net.minecraft.util.MathHelper.floor_double(placer.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
            ForgeDirection[] sides = {ForgeDirection.NORTH, ForgeDirection.EAST, ForgeDirection.SOUTH, ForgeDirection.WEST};
            ((TileEntityEnergyStorageSC) te).setFacing(sides[quarter]);
            world.markBlockForUpdate(x, y, z);
        }
        com.sc.energy.CableWarningSC.sourcePlaced(world, x, y, z, placer);
    }

    @Override
    public void registerBlockIcons(IIconRegister register) {
        super.registerBlockIcons(register);
        Tier[] tiers = Tier.values();
        topIdle = new IIcon[tiers.length];
        topActive = new IIcon[tiers.length];
        for (Tier tier : tiers) {
            topIdle[tier.ordinal()] = register.registerIcon(Reference.ASSETS + ":chargePad" + tier.name() + "Top");
            topActive[tier.ordinal()] = register.registerIcon(Reference.ASSETS + ":chargePad" + tier.name() + "TopOn");
        }
    }

    @Override
    public IIcon getIcon(int side, int meta) {
        if (side == 1 && topIdle != null) {
            return topIdle[tierFor(meta).ordinal()];
        }
        return super.getIcon(side, meta);
    }

    @Override
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        if (side == 1 && topIdle != null) {
            TileEntity te = world.getTileEntity(x, y, z);
            int t = tierFor(world.getBlockMetadata(x, y, z)).ordinal();
            return te instanceof TileEntityChargePadSC && ((TileEntityChargePadSC) te).isActive() ? topActive[t] : topIdle[t];
        }
        return super.getIcon(world, x, y, z, side);
    }
}
