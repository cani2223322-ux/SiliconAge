package com.sc.block;

import com.sc.Reference;
import com.sc.SCMod;
import com.sc.handler.GuiHandlerSC;
import com.sc.init.ModCreativeTab;
import com.sc.init.ModItems;
import com.sc.tileentity.TileEntityFieldGeneratorSC;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

/** Field Generator (§7/§9/§16) - single block, no metadata (fixed HV tier, see TileEntityFieldGeneratorSC). */
public class BlockFieldGeneratorSC extends Block {

    private IIcon icon;

    public BlockFieldGeneratorSC() {
        super(Material.iron);
        setBlockName(Reference.ASSETS + ".fieldGeneratorSC");
        setCreativeTab(ModCreativeTab.TAB);
        setHardness(4.0F);
        setResistance(10.0F);
        setStepSound(soundTypeMetal);
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int meta) {
        return new TileEntityFieldGeneratorSC();
    }

    @Override
    public void registerBlockIcons(IIconRegister register) {
        icon = register.registerIcon(Reference.ASSETS + ":machineFieldGeneratorFront");
    }

    @Override
    public IIcon getIcon(int side, int meta) {
        return icon;
    }

    /** Whoever places a generator owns it (private field, access list, warnings). */
    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, net.minecraft.entity.EntityLivingBase placer, ItemStack stack) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!world.isRemote && te instanceof TileEntityFieldGeneratorSC && placer instanceof EntityPlayer) {
            ((TileEntityFieldGeneratorSC) te).setOwner(placer.getCommandSenderName());
        }
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntityFieldGeneratorSC.unlink(world, x, y, z);
        super.breakBlock(world, x, y, z, block, meta);
    }

    /**
     * Plain right-click on the master opens the status GUI (energy bar + mode/node/active info,
     * see GuiFieldGeneratorSC). Sneak+right-click keeps the old quick-cycle-via-chat shortcut so
     * changing mode doesn't require opening a screen - with an EMPTY hand only: vanilla never calls
     * onBlockActivated for a sneaking player holding an item (sc.gui.field.cycle / the manual say
     * so). A linked (non-master) node has no state of
     * its own worth a screen for, so it just points the player at the master, same as before.
     */
    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        ItemStack held = player.getCurrentEquippedItem();
        if (held != null && held.getItem() == ModItems.fieldLinkModule) {
            return false; // let ItemFieldLinkModule.onItemUse handle it (§9)
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityFieldGeneratorSC)) {
            return true;
        }
        TileEntityFieldGeneratorSC field = (TileEntityFieldGeneratorSC) te;

        if (!field.isMaster()) {
            if (!world.isRemote) {
                player.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation("sc.chat.field.node"));
            }
            return true;
        }

        if (!world.isRemote && field.getOwner().isEmpty()) {
            field.setOwner(player.getCommandSenderName());       // a generator from before owners: the first to use it
            player.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation("sc.field.claimed"));
        }
        if (player.isSneaking()) {
            if (!field.allowed(player)) {
                if (!world.isRemote) {
                    player.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation("sc.field.noaccess", field.getOwner()));
                }
                return true;
            }
            if (!world.isRemote) {
                field.cycleMode();
                player.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation("sc.chat.field.mode",
                        new net.minecraft.util.ChatComponentTranslation("sc.field.mode." + field.getMode().name().toLowerCase(java.util.Locale.ROOT)),
                        field.getNodeCount(),
                        new net.minecraft.util.ChatComponentTranslation(field.isActive() ? "sc.gui.field.active" : "sc.gui.field.inactive")));
            }
            return true;
        }

        if (!world.isRemote) {
            player.openGui(SCMod.instance, GuiHandlerSC.FIELD_GENERATOR_GUI_ID, world, x, y, z);
        }
        return true;
    }
}
