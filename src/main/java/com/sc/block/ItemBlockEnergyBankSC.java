package com.sc.block;

import java.util.List;

import com.sc.energy.EnergyBankMathSC;
import com.sc.manual.Lang;
import com.sc.util.TooltipSC;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/**
 * ОМ-7 (docs/plan-ideas-2026-10-26.md): the Energy Bank's blocks as items, a name per meta (tile.siliconage.energyBank.<name>.name);
 * ОМ-7's tooltip: sc.tooltip.energyBank.<casing | controller | cell | port> (the glass - the casing's line), the controller's carried
 * charge (sc.tooltip.energyBank.stored), Shift .details, Ctrl .howto (the controller and the 4-billion cell: + .emptySv) (TooltipSC).
 */
public class ItemBlockEnergyBankSC extends ItemBlock {

    public ItemBlockEnergyBankSC(Block block) {
        super(block);
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    @Override
    public int getMetadata(int damage) {
        return damage >= 0 && damage < BlockEnergyBankSC.METAS ? damage : 0;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "." + BlockEnergyBankSC.nameOf(stack.getItemDamage());
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        int m = stack.getItemDamage();
        String line;
        if (m == BlockEnergyBankSC.CONTROLLER) {
            line = Lang.tr("sc.tooltip.energyBank.controller");
        } else if (m == BlockEnergyBankSC.CELL_4 || m == BlockEnergyBankSC.CELL_16) {
            line = Lang.tr("sc.tooltip.energyBank.cell", num(m == BlockEnergyBankSC.CELL_4      // as the controller counts it (storageCapacity)
                    ? EnergyBankMathSC.scaledCapacity(1, 0, com.sc.util.ConfigSC.storageCapacity)
                    : EnergyBankMathSC.scaledCapacity(0, 1, com.sc.util.ConfigSC.storageCapacity)));
        } else if (BlockEnergyBankSC.isPort(m)) {
            com.sc.energy.Tier t = com.sc.energy.Tier.byOrdinal(m - BlockEnergyBankSC.PORT_FIRST);
            line = Lang.tr("sc.tooltip.energyBank.port", t.name(),        // IC2 without Industrial Upgrade: one packet out
                    num((long) t.getVoltage() * com.sc.tileentity.TileEntityEnergyBankPortSC.outPackets()));
        } else {
            line = Lang.tr("sc.tooltip.energyBank.casing");
        }
        TooltipSC.wrap(list, line, "§7");
        if (m == BlockEnergyBankSC.CONTROLLER && stack.hasTagCompound() && stack.getTagCompound().hasKey(com.sc.tileentity.TileEntityEnergyBankSC.NBT)) {
            long eu = stack.getTagCompound().getCompoundTag(com.sc.tileentity.TileEntityEnergyBankSC.NBT).getLong("eu");
            TooltipSC.wrap(list, Lang.tr("sc.tooltip.energyBank.stored", num(eu)), "§b");
        }
        String howto = Lang.tr("sc.tooltip.energyBank.howto");
        if (m == BlockEnergyBankSC.CONTROLLER || m == BlockEnergyBankSC.CELL_4) {
            howto += "\n" + Lang.tr("sc.tooltip.energyBank.emptySv");     // ModRecipesCrafting.EmptyStorageRecipe
        }
        TooltipSC.more(list, Lang.tr("sc.tooltip.energyBank.details", EnergyBankMathSC.SIZE_MIN, EnergyBankMathSC.SIZE_MAX), howto);
    }

    /** "4 000 000 000" in Russian, "4,000,000,000" in English (the handbook's way). */
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    static String num(long v) {
        String s = com.sc.bridge.BridgeMathSC.group(v);
        return "ru".equals(Lang.trOr("sc.book.lang", "en")) ? s : s.replace(' ', ',');
    }
}
