package com.sc.item;

import java.util.ArrayList;
import java.util.List;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.machine.MachineRecipe;
import com.sc.machine.MachineType;
import com.sc.machine.RecipeRegistry;
import com.sc.manual.Lang;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

/** A single-icon, no-subtype item - for one-off materials that don't fit an existing metadata group (e.g. Coke, §17.2). */
public class ItemSimpleSC extends net.minecraft.item.Item {

    private final String textureName, name;
    private IIcon icon;
    /** Client: the machines whose recipes make this item (found once, on the first Ctrl tooltip). */
    private List<MachineType> madeIn;

    public ItemSimpleSC(String unlocalizedName, String textureName) {
        this.textureName = textureName;
        this.name = unlocalizedName;
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + "." + unlocalizedName);
    }

    @Override
    public void registerIcons(IIconRegister register) {
        icon = register.registerIcon(Reference.ASSETS + ":" + textureName);
    }

    @Override
    public IIcon getIconFromDamage(int damage) {
        return "nb3SnIngot".equals(textureName) ? IngotLookSC.icon() : icon;
    }

    /**
     * What it is for: "sc.item.<name>.tooltip" when the .lang has it (nothing otherwise - written as they come);
     * under Ctrl the machines that make it. Subclasses with their own tooltip override this.
     */
    @Override
    public void addInformation(net.minecraft.item.ItemStack stack, net.minecraft.entity.player.EntityPlayer player, List list, boolean advanced) {
        String what = Lang.trOr("sc.item." + name + ".tooltip", null);
        if (what != null) {
            com.sc.util.TooltipSC.wrap(list, what, "\u00a77");
        }
        if (madeIn == null) {
            madeIn = machinesMaking(this, -1);
        }
        madeInLines(list, madeIn);
    }

    /** Under Ctrl: "made in" with the machines' names; otherwise the Ctrl hint. Nothing for no machine. */
    public static void madeInLines(List list, List<MachineType> machines) {
        if (machines.isEmpty()) {
            return;
        }
        if (com.sc.util.TooltipSC.ctrl()) {
            StringBuilder sb = new StringBuilder();
            for (MachineType m : machines) {
                sb.append(sb.length() > 0 ? ", " : "").append(m.localizedName());
            }
            com.sc.util.TooltipSC.wrap(list, Lang.tr("sc.tooltip.madein", sb.toString()), "\u00a77");
        } else {
            list.add(Lang.tr("sc.tooltip.hold.madein"));
        }
    }

    /** The machines with a recipe putting out `item` (`meta` -1: any), each once. */
    public static List<MachineType> machinesMaking(net.minecraft.item.Item item, int meta) {
        List<MachineType> names = new ArrayList<MachineType>();
        for (MachineType t : MachineType.values()) {
            boolean found = false;
            for (MachineRecipe r : RecipeRegistry.recipesFor(t)) {
                for (int i = 0; !found && r.outputs != null && i < r.outputs.length; i++) {
                    net.minecraft.item.ItemStack o = r.outputs[i];
                    found = o != null && o.getItem() == item && (meta < 0 || o.getItemDamage() == meta);
                }
                if (found) {
                    break;
                }
            }
            if (found) {
                names.add(t);
            }
        }
        return names;
    }

    /** The Nb3Sn ingot shares the vanilla-ingot look of every other ingot (IngotLookSC). */
    @Override
    public int getColorFromItemStack(net.minecraft.item.ItemStack stack, int pass) {
        return "nb3SnIngot".equals(textureName) ? IngotLookSC.NB3SN_TINT : 0xFFFFFF;
    }
}
