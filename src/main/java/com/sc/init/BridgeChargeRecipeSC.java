package com.sc.init;

import com.sc.bridge.BridgeItemDataSC;
import com.sc.bridge.BridgeMathSC;
import com.sc.item.ItemBatterySC;
import com.sc.item.ItemBridgeRemoteSC;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.ShapedOreRecipe;

/**
 * The bridge's charged crafts: the Singularity Capacitor round a QV battery (its charge goes into the
 * capacitor item's "BridgeEU", what the block reads on placement), the Bridge Remote round an EV battery
 * and the Space Remote round a Bridge Remote and an XV core (the remote's "Charge"; the Bridge Remote's
 * NBT - its binding, coordinator, history - is copied first). Charges are summed and capped at the
 * result's capacity. Generic materials go by their ore names as in OreRecipes.shaped; batteries and
 * remotes stay exact stacks (any charge).
 */
public class BridgeChargeRecipeSC extends ShapedOreRecipe {

    public BridgeChargeRecipeSC(ItemStack result, Object... recipe) {
        super(result, convert(recipe));
    }

    private static Object[] convert(Object[] in) {
        Object[] out = new Object[in.length];
        for (int i = 0; i < in.length; i++) {
            boolean keep = !(in[i] instanceof ItemStack) || ItemBatterySC.isBattery((ItemStack) in[i]) || ItemBridgeRemoteSC.isRemote((ItemStack) in[i]);
            out[i] = keep ? in[i] : OreRecipes.oreName((ItemStack) in[i]);
        }
        return out;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting grid) {
        ItemStack out = super.getCraftingResult(grid);
        if (out == null) {
            return null;
        }
        long stored = 0;
        ItemStack remote = null;
        for (int i = 0; i < grid.getSizeInventory(); i++) {
            ItemStack s = grid.getStackInSlot(i);
            if (ItemBatterySC.isBattery(s)) {
                stored += ItemBatterySC.chargeOf(s);
            } else if (ItemBridgeRemoteSC.isRemote(s)) {
                stored += ItemBridgeRemoteSC.chargeOf(s);
                remote = s;
            }
        }
        if (ItemBridgeRemoteSC.isRemote(out)) {
            if (remote != null && remote.hasTagCompound()) {
                out.setTagCompound((NBTTagCompound) remote.getTagCompound().copy());
            }
            long eu = Math.max(0L, Math.min(ItemBridgeRemoteSC.capacityOf(out), stored));
            if (eu > 0 || out.hasTagCompound()) {
                BridgeItemDataSC.setCharge(out, eu);
            }
        } else if (stored > 0) {
            NBTTagCompound tag = out.hasTagCompound() ? out.getTagCompound() : new NBTTagCompound();
            tag.setLong("BridgeEU", Math.min(BridgeMathSC.CAPACITOR_EU, stored));
            out.setTagCompound(tag);
        }
        return out;
    }
}
