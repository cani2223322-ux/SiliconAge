package com.sc.item;

import java.util.List;

import com.sc.Reference;
import com.sc.energy.Tier;
import com.sc.init.ModCreativeTab;
import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityWirelessSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

/**
 * The link card: right-click a transmitter (it remembers it), then a receiver - the two become a
 * pair. Sneak + right-click in the air forgets the chosen transmitter. Quantum translators link
 * by an entangled crystal instead.
 */
public class ItemLinkCardSC extends Item {

    public ItemLinkCardSC() {
        setMaxStackSize(1);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".linkCard");
        setTextureName(Reference.ASSETS + ":linkCard");
    }

    private static void say(EntityPlayer p, String key, Object... args) {
        p.addChatComponentMessage(new ChatComponentTranslation(key, args));
    }

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
                                  float hx, float hy, float hz) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityWirelessSC)) {
            return false;
        }
        if (world.isRemote) {
            return false;                      // true here would keep the click from reaching the server
        }
        TileEntityWirelessSC w = (TileEntityWirelessSC) te;
        if (!w.allowed(player)) {
            say(player, "sc.chat.wl.notmine", w.getOwner());
            return true;
        }
        if (w.getKind() == TileEntityWirelessSC.QUANTUM) {
            say(player, "sc.chat.wl.quantum");
            return true;
        }
        if (w.getKind() == TileEntityWirelessSC.TRANSMITTER) {
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setLong("TxId", w.getId());
            nbt.setInteger("TxX", x);
            nbt.setInteger("TxY", y);
            nbt.setInteger("TxZ", z);
            nbt.setInteger("TxTier", w.getTier().ordinal());
            stack.setTagCompound(nbt);
            say(player, "sc.chat.wl.txsel", w.getTier().name());
            return true;
        }
        if (!stack.hasTagCompound() || !stack.getTagCompound().hasKey("TxId")) {
            say(player, "sc.chat.wl.firsttx");
            return true;
        }
        TileEntityWirelessSC tx = TileEntityWirelessSC.loaded(stack.getTagCompound().getLong("TxId"));
        if (tx == null || tx.isInvalid()) {
            say(player, "sc.chat.wl.notx");
            return true;
        }
        if (!tx.allowed(player)) {
            say(player, "sc.chat.wl.notmine", tx.getOwner());
            return true;
        }
        if (tx.getWorldObj() != world) {
            say(player, "sc.chat.wl.otherdim");
            return true;
        }
        Tier t = tx.getTier().ordinal() <= w.getTier().ordinal() ? tx.getTier() : w.getTier();
        double dist = Math.sqrt(tx.getDistanceFrom(x + 0.5, y + 0.5, z + 0.5));
        if (dist > TileEntityWirelessSC.range(t)) {
            say(player, "sc.chat.wl.toofar", (int) dist, t.name(), TileEntityWirelessSC.range(t));
            return true;
        }
        TileEntityWirelessSC.link(tx, w);
        stack.setTagCompound(null);
        say(player, "sc.chat.wl.linked", (int) dist, TileEntityWirelessSC.lossPct(dist, t));
        return true;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (player.isSneaking() && stack.hasTagCompound()) {
            stack.setTagCompound(null);
            if (!world.isRemote) {
                say(player, "sc.chat.wl.cleared");
            }
        }
        return stack;
    }

    @Override
    public boolean hasEffect(ItemStack stack, int pass) {
        return stack.hasTagCompound() && stack.getTagCompound().hasKey("TxId");
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey("TxId")) {
            NBTTagCompound n = stack.getTagCompound();
            list.add("§b" + Lang.tr("sc.linkcard.tooltip.tx", Tier.values()[Math.max(0, Math.min(Tier.values().length - 1,
                    n.getInteger("TxTier")))].name(), n.getInteger("TxX"), n.getInteger("TxY"), n.getInteger("TxZ")));
            list.add("§7" + Lang.tr("sc.linkcard.tooltip.next"));
        } else {
            list.add("§7" + Lang.tr("sc.linkcard.tooltip.empty"));
        }
    }
}
