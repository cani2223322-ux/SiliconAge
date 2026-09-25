package com.sc.block;

import java.util.List;

import com.sc.energy.GeneratorType;
import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityGeneratorSC;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

public class ItemBlockGeneratorSC extends ItemBlock {

    public ItemBlockGeneratorSC(Block block) {
        super(block);
        setHasSubtypes(true);
    }

    @Override
    public int getMetadata(int damage) {
        return damage;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        GeneratorType[] values = GeneratorType.values();
        int meta = stack.getItemDamage();
        GeneratorType type = values[meta >= 0 && meta < values.length ? meta : 0];
        return super.getUnlocalizedName() + "." + type.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** What a broken generator kept (BlockGeneratorSC.getDrops): its buffer, its fuel, the reactor's ignition. */
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt == null) {
            return;
        }
        GeneratorType[] values = GeneratorType.values();
        int meta = stack.getItemDamage();
        GeneratorType type = values[meta >= 0 && meta < values.length ? meta : 0];
        if (nbt.getInteger("EnergySC") > 0) {
            list.add(Lang.tr("sc.storage.tooltip.charge", String.valueOf(nbt.getInteger("EnergySC")),
                    String.valueOf(type.tier.getBuffer())));
        }
        if (nbt.hasKey("FuelTank")) {
            FluidStack fuel = FluidStack.loadFluidStackFromNBT(nbt.getCompoundTag("FuelTank"));
            if (fuel != null && fuel.amount > 0) {
                list.add(Lang.tr("sc.waila.fluid", fuel.getLocalizedName(), fuel.amount, TileEntityGeneratorSC.TANK_CAPACITY));
            }
        }
        if (nbt.getBoolean("Ignited")) {
            list.add(Lang.tr("sc.generator.tooltip.ignited",
                    (int) ((long) nbt.getInteger("ModuleLife") * 100 / TileEntityGeneratorSC.MODULE_LIFE_TICKS)));
        } else if (nbt.getLong("IgnitionEU") > 0) {
            list.add(Lang.tr("sc.generator.tooltip.ignition", String.valueOf(nbt.getLong("IgnitionEU")),
                    String.valueOf(TileEntityGeneratorSC.getIgnitionThreshold())));
        }
    }
}
