package com.sc.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.sc.Reference;
import com.sc.arena.ArenaBlocksSC;
import com.sc.tileentity.TileEntityBreakdownConsoleSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * PACKAGE A (docs/plan-substrate-tech-f5.md §4.2, §4.7): the Breakdown Epicentre's blocks (ArenaBlocksSC metas: the Breakdown
 * Console, a coil's pedestal, a column, the floor's and the wall's plating, a lamp) - all unbreakable (hardness -1, blast-proof), no
 * drop, no pick block, nothing spawns on them; the console has its TileEntityBreakdownConsoleSC, its click goes to it (both sides
 * answer true, the server decides). Looks (tools/gen_arena_tex.py): the Lithographers' dark plating scarred by the Breakdown
 * (violet-white cracks), the console a dark slab with a key slot (lit when a key is in - the tile's clientKey) and a pale reactor
 * glow, the pedestal a ring of copper with a blue core, the columns banded like the canyons' walls, the lamp a cold white strip.
 * Icons: arena_<name> (every face; the console's and the pedestal's sides), arena_breakdownConsole_top / _key and
 * arena_coilPedestal_top for their tops; a column's ends and the console's and pedestal's bottoms are the wall's plating.
 * <p>
 * Fixed (the coordinator's): the class, the material, unbreakable, the TE of the console meta, onBlockActivated's route. A's: the
 * icons, getLightValue (ArenaBlocksSC.LAMP_LIGHT / CONSOLE_LIGHT), randomDisplayTick (the console's sparks when a key is in).
 */
public class BlockArenaSC extends Block {

    @SideOnly(Side.CLIENT)
    private IIcon[] icons;
    @SideOnly(Side.CLIENT)
    private IIcon consoleTop, consoleKey, pedestalTop;

    public BlockArenaSC() {
        super(Material.iron);
        setBlockName(Reference.ASSETS + ".arena");
        setBlockTextureName(Reference.ASSETS + ":arena_arenaFloor");
        setBlockUnbreakable();
        setResistance(6000000F);
        setStepSound(soundTypeMetal);
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return meta == ArenaBlocksSC.CONSOLE;
    }

    @Override
    public TileEntity createTileEntity(World world, int meta) {
        return meta == ArenaBlocksSC.CONSOLE ? new TileEntityBreakdownConsoleSC() : null;
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hx, float hy, float hz) {
        if (world.getBlockMetadata(x, y, z) != ArenaBlocksSC.CONSOLE) {
            return false;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (!world.isRemote && te instanceof TileEntityBreakdownConsoleSC) {
            ((TileEntityBreakdownConsoleSC) te).click(player);
        }
        return true;
    }

    @Override
    public int getLightValue(IBlockAccess w, int x, int y, int z) {
        int m = w.getBlockMetadata(x, y, z);
        return m == ArenaBlocksSC.LAMP ? ArenaBlocksSC.LAMP_LIGHT : m == ArenaBlocksSC.CONSOLE ? ArenaBlocksSC.CONSOLE_LIGHT : 0;
    }

    @Override
    public int damageDropped(int meta) {
        return meta;
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        return new ArrayList<ItemStack>();
    }

    @Override
    public boolean canEntityDestroy(IBlockAccess world, int x, int y, int z, Entity entity) {
        return false;
    }

    @Override
    public ItemStack getPickBlock(net.minecraft.util.MovingObjectPosition target, World world, int x, int y, int z) {
        return null;
    }

    @Override
    public boolean canCreatureSpawn(EnumCreatureType type, IBlockAccess world, int x, int y, int z) {
        return false;
    }

    // ------------------------------------------------------------------ client

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister r) {
        icons = new IIcon[ArenaBlocksSC.NAMES.length];
        for (int m = 0; m < icons.length; m++) {
            icons[m] = r.registerIcon(Reference.ASSETS + ":arena_" + ArenaBlocksSC.NAMES[m]);
        }
        consoleTop = r.registerIcon(Reference.ASSETS + ":arena_" + ArenaBlocksSC.NAMES[ArenaBlocksSC.CONSOLE] + "_top");
        consoleKey = r.registerIcon(Reference.ASSETS + ":arena_" + ArenaBlocksSC.NAMES[ArenaBlocksSC.CONSOLE] + "_key");
        pedestalTop = r.registerIcon(Reference.ASSETS + ":arena_" + ArenaBlocksSC.NAMES[ArenaBlocksSC.PEDESTAL] + "_top");
        blockIcon = icons[ArenaBlocksSC.FLOOR];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        if (icons == null) {
            return blockIcon;
        }
        int m = meta >= 0 && meta < icons.length ? meta : ArenaBlocksSC.FLOOR;
        switch (m) {
            case ArenaBlocksSC.CONSOLE:
                return side == 1 ? consoleTop : side == 0 ? icons[ArenaBlocksSC.WALL] : icons[m];
            case ArenaBlocksSC.PEDESTAL:
                return side == 1 ? pedestalTop : side == 0 ? icons[ArenaBlocksSC.WALL] : icons[m];
            case ArenaBlocksSC.COLUMN:
                return side <= 1 ? icons[ArenaBlocksSC.WALL] : icons[m];
            default:
                return icons[m];
        }
    }

    /** The console's top: its key slot lit while a key lies in it (the tile's clientKey). */
    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess w, int x, int y, int z, int side) {
        int meta = w.getBlockMetadata(x, y, z);
        if (meta == ArenaBlocksSC.CONSOLE && side == 1 && consoleKey != null) {
            TileEntity te = w.getTileEntity(x, y, z);
            if (te instanceof TileEntityBreakdownConsoleSC && ((TileEntityBreakdownConsoleSC) te).clientKey) {
                return consoleKey;
            }
        }
        return getIcon(side, meta);
    }

    /** The console: violet-white sparks over its key slot while a key is in, a faint glow of its reactor now and then. */
    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(World world, int x, int y, int z, Random r) {
        if (world.getBlockMetadata(x, y, z) != ArenaBlocksSC.CONSOLE || !world.isAirBlock(x, y + 1, z)) {
            return;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        boolean key = te instanceof TileEntityBreakdownConsoleSC && ((TileEntityBreakdownConsoleSC) te).clientKey;
        double cx = x + 0.5D, top = y + 1.05D, cz = z + 0.5D;
        if (key) {
            for (int i = 0; i < 3; i++) {
                world.spawnParticle("reddust", cx + (r.nextDouble() - 0.5D) * 0.35D, top + r.nextDouble() * 0.2D,
                        cz + (r.nextDouble() - 0.5D) * 0.35D, 0.75D, 0.55D, 1.0D);
            }
            if (r.nextInt(3) == 0) {
                world.spawnParticle("portal", cx, top, cz, (r.nextDouble() - 0.5D) * 0.6D, 0.3D, (r.nextDouble() - 0.5D) * 0.6D);
            }
            if (r.nextInt(12) == 0) {
                world.playSound(cx, top, cz, "mob.endermen.portal", 0.15F, 1.6F + r.nextFloat() * 0.2F, false);
            }
        } else if (r.nextInt(4) == 0) {
            world.spawnParticle("reddust", x + 0.2D + r.nextDouble() * 0.6D, top, z + 0.2D + r.nextDouble() * 0.6D, 0.55D, 0.6D, 0.9D);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        for (int m = 0; m < ArenaBlocksSC.NAMES.length; m++) {
            list.add(new ItemStack(item, 1, m));
        }
    }
}
