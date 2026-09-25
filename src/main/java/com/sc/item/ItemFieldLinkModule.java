package com.sc.item;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.tileentity.TileEntityFieldGeneratorSC;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

/**
 * §7/§9: two-click linking tool for Field Generator clusters. First right-click on a Field
 * Generator stores its coordinate in this stack's NBT; the second right-click (on a different
 * Field Generator) completes the link via TileEntityFieldGeneratorSC.link() and clears the
 * pending coordinate. Shift-right-click on empty air (or any non-Field-Generator block)
 * cancels a pending selection.
 */
public class ItemFieldLinkModule extends net.minecraft.item.Item {

    private IIcon icon;

    public ItemFieldLinkModule() {
        setMaxStackSize(1);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".fieldLinkModule");
    }

    @Override
    public void registerIcons(IIconRegister register) {
        icon = register.registerIcon(Reference.ASSETS + ":fieldLinkModule");
    }

    @Override
    public IIcon getIconFromDamage(int damage) {
        return icon;
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityFieldGeneratorSC)) {
            return cancel(stack, player);
        }
        NBTTagCompound nbt = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        if (!nbt.hasKey("PendingX")) {
            nbt.setInteger("PendingX", x);
            nbt.setInteger("PendingY", y);
            nbt.setInteger("PendingZ", z);
            nbt.setInteger("PendingDim", world.provider.dimensionId);
            stack.setTagCompound(nbt);
            player.addChatComponentMessage(new ChatComponentTranslation("sc.chat.link.first"));
            return true;
        }

        int[] from = {nbt.getInteger("PendingX"), nbt.getInteger("PendingY"), nbt.getInteger("PendingZ")};
        int pendingDim = nbt.getInteger("PendingDim");
        int[] to = {x, y, z};
        clearPending(nbt);
        // The first point is only meaningful in the world it was picked in - otherwise the same
        // coordinates were looked up (and chunk-loaded) in whatever dimension the second click was.
        if (pendingDim != world.provider.dimensionId) {
            player.addChatComponentMessage(new ChatComponentTranslation("sc.chat.link.dimension"));
            return true;
        }

        TileEntityFieldGeneratorSC.LinkResult result = TileEntityFieldGeneratorSC.link(world, from, to, player);
        switch (result) {
            case LINKED:
                player.addChatComponentMessage(new ChatComponentTranslation("sc.chat.link.linked"));
                break;
            case TOO_FAR:
                player.addChatComponentMessage(new ChatComponentTranslation("sc.chat.link.toofar",
                        TileEntityFieldGeneratorSC.MAX_LINK_DISTANCE));
                break;
            case SAME_CLUSTER:
                player.addChatComponentMessage(new ChatComponentTranslation("sc.chat.link.same"));
                break;
            case NODE_CAP:
                player.addChatComponentMessage(new ChatComponentTranslation("sc.chat.link.cap"));
                break;
            case UNLOADED:
                player.addChatComponentMessage(new ChatComponentTranslation("sc.chat.link.unloaded"));
                break;
            case NO_ACCESS:
                player.addChatComponentMessage(new ChatComponentTranslation("sc.chat.link.noaccess"));
                break;
            default:
                player.addChatComponentMessage(new ChatComponentTranslation("sc.chat.link.invalid"));
        }
        return true;
    }

    /** Sneak + right-click on air or any other block drops a pending first selection (as the class doc promises). */
    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote && player.isSneaking()) {
            cancel(stack, player);
        }
        return stack;
    }

    private static boolean cancel(ItemStack stack, EntityPlayer player) {
        if (!player.isSneaking() || !stack.hasTagCompound() || !stack.getTagCompound().hasKey("PendingX")) {
            return false;
        }
        if (!player.worldObj.isRemote) {
            clearPending(stack.getTagCompound());
            player.addChatComponentMessage(new ChatComponentTranslation("sc.chat.link.cancelled"));
        }
        return true;
    }

    private static void clearPending(NBTTagCompound nbt) {
        nbt.removeTag("PendingX");
        nbt.removeTag("PendingY");
        nbt.removeTag("PendingZ");
        nbt.removeTag("PendingDim");
    }
}
