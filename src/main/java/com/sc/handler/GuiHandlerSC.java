package com.sc.handler;

import com.sc.inventory.ContainerFieldGeneratorSC;
import com.sc.inventory.ContainerGeneratorSC;
import com.sc.inventory.ContainerMachineSC;
import com.sc.inventory.GuiFieldGeneratorSC;
import com.sc.inventory.GuiGeneratorSC;
import com.sc.inventory.GuiMachineSC;
import com.sc.tileentity.TileEntityFieldGeneratorSC;
import com.sc.tileentity.TileEntityGeneratorSC;
import com.sc.tileentity.TileEntityMachineSC;

import cpw.mods.fml.common.network.IGuiHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class GuiHandlerSC implements IGuiHandler {

    public static final int MACHINE_GUI_ID = 0;
    public static final int GENERATOR_GUI_ID = 1;
    public static final int FIELD_GENERATOR_GUI_ID = 2;
    public static final int ENERGY_STORAGE_GUI_ID = 3;
    public static final int QUARRY_GUI_ID = 4;
    /** Wireless energy: the transmitter, the receiver, the quantum translator. */
    public static final int WIRELESS_GUI_ID = 5;
    /** The decontamination shower. */
    public static final int SHOWER_GUI_ID = 6;
    /** The Armour Service Station. */
    public static final int ARMOR_STATION_GUI_ID = 7;
    /** The Singular Service Station. */
    public static final int SINGULAR_STATION_GUI_ID = 8;
    /** The Energy Converter. */
    public static final int ENERGY_CONVERTER_GUI_ID = 9;
    /** Conduit connector menu: this + the side (0..5). */
    public static final int CONDUIT_GUI_BASE = 10;
    /** Item filter set-up (the filter in the player's hand). */
    public static final int FILTER_GUI_ID = 20;

    /** The filter menu's "x" is the hotbar slot holding the filter. */
    private static boolean filterSlot(EntityPlayer player, int slot) {
        return slot >= 0 && slot < 9 && com.sc.conduit.ItemFilterSC.isFilter(player.inventory.getStackInSlot(slot));
    }

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == FILTER_GUI_ID) {           // x is a hotbar slot here, not a position - no block lookup
            return filterSlot(player, x) ? new com.sc.inventory.ContainerFilterSC(player, x) : null;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (id == QUARRY_GUI_ID && te instanceof com.sc.tileentity.TileEntityQuarrySC) {
            return new com.sc.inventory.ContainerQuarrySC(player.inventory, (com.sc.tileentity.TileEntityQuarrySC) te);
        }
        if (id == WIRELESS_GUI_ID && te instanceof com.sc.tileentity.TileEntityWirelessSC) {
            return new com.sc.inventory.ContainerWirelessSC(player.inventory, (com.sc.tileentity.TileEntityWirelessSC) te);
        }
        if (id == SHOWER_GUI_ID && te instanceof com.sc.tileentity.TileEntityShowerSC) {
            return new com.sc.inventory.ContainerShowerSC(player.inventory, (com.sc.tileentity.TileEntityShowerSC) te);
        }
        if (id == SINGULAR_STATION_GUI_ID && te instanceof com.sc.tileentity.TileEntitySingularStationSC) {
            return new com.sc.inventory.ContainerSingularStationSC(player.inventory, (com.sc.tileentity.TileEntitySingularStationSC) te);
        }
        if (id == ENERGY_CONVERTER_GUI_ID && te instanceof com.sc.tileentity.TileEntityEnergyConverterSC) {
            return new com.sc.inventory.ContainerEnergyConverterSC(player.inventory, (com.sc.tileentity.TileEntityEnergyConverterSC) te);
        }
        if (id == ARMOR_STATION_GUI_ID && te instanceof com.sc.tileentity.TileEntityArmorStationSC) {
            return new com.sc.inventory.ContainerArmorStationSC(player.inventory, (com.sc.tileentity.TileEntityArmorStationSC) te);
        }
        if (id == MACHINE_GUI_ID && te instanceof TileEntityMachineSC) {
            return new ContainerMachineSC(player.inventory, (TileEntityMachineSC) te);
        }
        if (id == GENERATOR_GUI_ID && te instanceof TileEntityGeneratorSC) {
            return new ContainerGeneratorSC(player.inventory, (TileEntityGeneratorSC) te);
        }
        if (id == FIELD_GENERATOR_GUI_ID && te instanceof TileEntityFieldGeneratorSC) {
            return new ContainerFieldGeneratorSC(player.inventory, (TileEntityFieldGeneratorSC) te);
        }
        if (id == ENERGY_STORAGE_GUI_ID && te instanceof com.sc.tileentity.TileEntityEnergyStorageSC) {
            return new com.sc.inventory.ContainerEnergyStorageSC(player.inventory, (com.sc.tileentity.TileEntityEnergyStorageSC) te);
        }
        if (id >= CONDUIT_GUI_BASE && id < CONDUIT_GUI_BASE + 6 && te instanceof com.sc.tileentity.TileEntityConduitBundleSC) {
            return new com.sc.inventory.ContainerConduitSC((com.sc.tileentity.TileEntityConduitBundleSC) te,
                    net.minecraftforge.common.util.ForgeDirection.getOrientation(id - CONDUIT_GUI_BASE), player.inventory);
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == FILTER_GUI_ID) {
            return filterSlot(player, x) ? new com.sc.inventory.GuiFilterSC(player, x) : null;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (id == QUARRY_GUI_ID && te instanceof com.sc.tileentity.TileEntityQuarrySC) {
            return new com.sc.inventory.GuiQuarrySC(player.inventory, (com.sc.tileentity.TileEntityQuarrySC) te);
        }
        if (id == WIRELESS_GUI_ID && te instanceof com.sc.tileentity.TileEntityWirelessSC) {
            return new com.sc.inventory.GuiWirelessSC(player.inventory, (com.sc.tileentity.TileEntityWirelessSC) te);
        }
        if (id == SHOWER_GUI_ID && te instanceof com.sc.tileentity.TileEntityShowerSC) {
            return new com.sc.inventory.GuiShowerSC(player.inventory, (com.sc.tileentity.TileEntityShowerSC) te);
        }
        if (id == SINGULAR_STATION_GUI_ID && te instanceof com.sc.tileentity.TileEntitySingularStationSC) {
            return new com.sc.inventory.GuiSingularStationSC(player.inventory, (com.sc.tileentity.TileEntitySingularStationSC) te);
        }
        if (id == ENERGY_CONVERTER_GUI_ID && te instanceof com.sc.tileentity.TileEntityEnergyConverterSC) {
            return new com.sc.inventory.GuiEnergyConverterSC(player.inventory, (com.sc.tileentity.TileEntityEnergyConverterSC) te);
        }
        if (id == ARMOR_STATION_GUI_ID && te instanceof com.sc.tileentity.TileEntityArmorStationSC) {
            return new com.sc.inventory.GuiArmorStationSC(player.inventory, (com.sc.tileentity.TileEntityArmorStationSC) te);
        }
        if (id == MACHINE_GUI_ID && te instanceof TileEntityMachineSC) {
            return new GuiMachineSC(player.inventory, (TileEntityMachineSC) te);
        }
        if (id == GENERATOR_GUI_ID && te instanceof TileEntityGeneratorSC) {
            if (((TileEntityGeneratorSC) te).getGeneratorType() == com.sc.energy.GeneratorType.TOKAMAK_XV) {
                return new com.sc.inventory.GuiTokamakXVSC(player.inventory, (TileEntityGeneratorSC) te);
            }
            if (((TileEntityGeneratorSC) te).getGeneratorType() == com.sc.energy.GeneratorType.SINGULAR_REACTOR) {
                return new com.sc.inventory.GuiSingularSC(player.inventory, (TileEntityGeneratorSC) te);
            }
            return new GuiGeneratorSC(player.inventory, (TileEntityGeneratorSC) te);
        }
        if (id == FIELD_GENERATOR_GUI_ID && te instanceof TileEntityFieldGeneratorSC) {
            return new GuiFieldGeneratorSC(player.inventory, (TileEntityFieldGeneratorSC) te);
        }
        if (id == ENERGY_STORAGE_GUI_ID && te instanceof com.sc.tileentity.TileEntityEnergyStorageSC) {
            return new com.sc.inventory.GuiEnergyStorageSC(player.inventory, (com.sc.tileentity.TileEntityEnergyStorageSC) te);
        }
        if (id >= CONDUIT_GUI_BASE && id < CONDUIT_GUI_BASE + 6 && te instanceof com.sc.tileentity.TileEntityConduitBundleSC) {
            return new com.sc.inventory.GuiConduitSC((com.sc.tileentity.TileEntityConduitBundleSC) te,
                    net.minecraftforge.common.util.ForgeDirection.getOrientation(id - CONDUIT_GUI_BASE), player.inventory);
        }
        return null;
    }
}
