package com.sc.block;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.tileentity.TileEntityRelaySC;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * ПД-7 (docs/plan-ideas-2026-10-26.md, «Отвоевать Мёртвый Узел: ретрансляционный пост», ★ A): the relay's block. Metas (fixed, append
 * only): RELAY - «Ретранслятор», bought from an Elder at «Свой» and set up by a player on a cleared Dead Node's plaza (its tile runs the
 * 5 minutes' defence - settle.RelayPostSC); MAST - «Мачта поста», what the Tuners build in its place once the defence held (unbreakable,
 * no drop, hidden in NEI: the post's heart under its dome; its tile keeps the post's settlement id). Both have TileEntityRelaySC.
 * <p>
 * Fixed (the coordinator's): the class, the registry name "relay" (IdeaBlocksSC), the metas, the tile. ПД-7's: placement rules
 * (RelayPostSC.refusal), hardness / unbreakable MAST, icons (tools/gen_relay_tex.py), the defence and the build.
 */
public class BlockRelaySC extends Block {

    public static final int RELAY = 0, MAST = 1;
    public static final String[] NAMES = {"relay", "mast"};

    public BlockRelaySC() {
        super(Material.iron);
        setBlockName(Reference.ASSETS + ".relay");
        setBlockTextureName(Reference.ASSETS + ":relay");
        setCreativeTab(ModCreativeTab.TAB);
        setHardness(5.0F);
        setResistance(30F);
        setStepSound(soundTypeMetal);
    }

    public static String nameOf(int meta) {
        return NAMES[meta >= 0 && meta < NAMES.length ? meta : 0];
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int meta) {
        return new TileEntityRelaySC();
    }

    @Override
    public int damageDropped(int meta) {
        return RELAY;
    }

    /** ПД-7: the MAST never breaks (the post's heart under its dome); the RELAY - a pickaxe's, hardness 5. */
    @Override
    public float getBlockHardness(World w, int x, int y, int z) {
        return w.getBlockMetadata(x, y, z) == MAST ? -1F : super.getBlockHardness(w, x, y, z);
    }

    @Override
    public float getExplosionResistance(net.minecraft.entity.Entity e, World w, int x, int y, int z, double ex, double ey, double ez) {
        return w.getBlockMetadata(x, y, z) == MAST ? 6000000F : super.getExplosionResistance(e, w, x, y, z, ex, ey, ez);
    }

    /** ПД-7: the MAST drops nothing. */
    @Override
    public java.util.ArrayList<net.minecraft.item.ItemStack> getDrops(World w, int x, int y, int z, int meta, int fortune) {
        java.util.ArrayList<net.minecraft.item.ItemStack> out = new java.util.ArrayList<net.minecraft.item.ItemStack>();
        if (meta == RELAY) {
            out.add(new net.minecraft.item.ItemStack(this, 1, RELAY));
        }
        return out;
    }

    /** ПД-7: a standing Relay with no defence (a restart paused it) - a right click goes on with it from the wave its tile kept. */
    @Override
    public boolean onBlockActivated(World w, int x, int y, int z, net.minecraft.entity.player.EntityPlayer p, int side, float hx, float hy, float hz) {
        if (w.getBlockMetadata(x, y, z) != RELAY) {
            return false;
        }
        if (w.isRemote) {
            return true;
        }
        return com.sc.settle.RelayPostSC.defending(x, y, z) || com.sc.settle.RelayPostSC.resume(w, p, x, y, z);
    }

    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public void registerBlockIcons(net.minecraft.client.renderer.texture.IIconRegister r) {
        icons = new net.minecraft.util.IIcon[NAMES.length * 2];
        for (int m = 0; m < NAMES.length; m++) {
            icons[m * 2] = r.registerIcon(Reference.ASSETS + ":relay_" + NAMES[m] + "_side");
            icons[m * 2 + 1] = r.registerIcon(Reference.ASSETS + ":relay_" + NAMES[m] + "_top");
        }
        blockIcon = icons[0];
    }

    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public net.minecraft.util.IIcon getIcon(int side, int meta) {
        int m = meta >= 0 && meta < NAMES.length ? meta : 0;
        return icons == null ? blockIcon : icons[m * 2 + (side <= 1 ? 1 : 0)];
    }

    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void getSubBlocks(net.minecraft.item.Item item, net.minecraft.creativetab.CreativeTabs tab, java.util.List list) {
        list.add(new net.minecraft.item.ItemStack(item, 1, RELAY));
    }

    @Override
    public int getLightValue(net.minecraft.world.IBlockAccess w, int x, int y, int z) {
        return 9;                                               // ПД-7: the turquoise fire of its antennae
    }

    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    private net.minecraft.util.IIcon[] icons;
}
