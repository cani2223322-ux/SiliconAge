package com.sc.debug;

import java.util.ArrayList;
import java.util.List;

import com.sc.Reference;
import com.sc.energy.TileEntityEnergyBase;

import cpw.mods.fml.common.Loader;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

/**
 * /scenergy - what the energy nets see around the player (or at x y z): every energy tile's
 * charge, demand and offer, its IC2 tiers and node stats, and which of its faces link to which
 * neighbour. Written to the log with an [SC-ENET] prefix (the chat gets a short summary), so a
 * network that doesn't move energy can be read back from latest.log.
 */
public class CommandEnergySC extends CommandBase {

    @Override
    public String getCommandName() {
        return "scenergy";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/scenergy [radius] | /scenergy <x> <y> <z> [radius]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;                                               // ops only: it loads chunks
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return sender.canCommandSenderUseCommand(getRequiredPermissionLevel(), getCommandName());
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        World world = sender.getEntityWorld();
        List<TileEntity> tiles = new ArrayList<TileEntity>();
        if (args.length == 3) {
            TileEntity te = world.getTileEntity(parseInt(sender, args[0]), parseInt(sender, args[1]), parseInt(sender, args[2]));
            if (te != null) {
                tiles.add(te);
            }
        } else {
            int r = args.length == 1 ? Math.max(1, Math.min(32, parseInt(sender, args[0])))
                    : args.length == 4 ? Math.max(1, Math.min(64, parseInt(sender, args[3]))) : 10;
            ChunkCoordinates c = args.length == 4
                    ? new ChunkCoordinates(parseInt(sender, args[0]), parseInt(sender, args[1]), parseInt(sender, args[2]))
                    : sender.getPlayerCoordinates();
            for (int x = c.posX - r; x <= c.posX + r + 15; x += 16) {           // from the console: load the area first
                for (int z = c.posZ - r; z <= c.posZ + r + 15; z += 16) {
                    world.getChunkFromBlockCoords(x, z);
                }
            }
            for (Object o : world.loadedTileEntityList) {
                TileEntity te = (TileEntity) o;
                if (Math.abs(te.xCoord - c.posX) <= r && Math.abs(te.yCoord - c.posY) <= r && Math.abs(te.zCoord - c.posZ) <= r
                        && (te instanceof TileEntityEnergyBase || ic2() && EnetProbeSC.isEnergyTile(te))) {
                    tiles.add(te);
                }
            }
        }
        System.out.println("[SC-ENET] ==== " + tiles.size() + " tiles, world time " + world.getTotalWorldTime()
                + (ic2() ? ", net " + EnetProbeSC.netName() : ", no IC2"));
        for (TileEntity te : tiles) {
            for (String line : describe(te)) {
                System.out.println("[SC-ENET] " + line);
                if (args.length == 3) {
                    sender.addChatMessage(new ChatComponentText(line));
                }
            }
        }
        if (args.length != 3) {
            sender.addChatMessage(new ChatComponentText("[SC] " + tiles.size() + " energy tiles written to the log ([SC-ENET])"));
        }
    }

    private static boolean ic2() {
        return Loader.isModLoaded(Reference.IC2_MODID);
    }

    private static List<String> describe(TileEntity te) {
        List<String> lines = new ArrayList<String>();
        lines.add("@ " + te.xCoord + " " + te.yCoord + " " + te.zCoord + " " + te.getClass().getSimpleName());
        if (te instanceof TileEntityEnergyBase) {
            TileEntityEnergyBase e = (TileEntityEnergyBase) te;
            lines.add("  SC: E=" + e.getEnergyStored() + "/" + e.getMaxEnergyStored() + " sink=" + e.isEnergySink() + " source=" + e.isEnergySource()
                    + " demand=" + e.demandedEnergy() + " offer=" + e.offerableEnergy() + " on=" + e.isPowerOn() + " rs=" + e.getRedstoneMode()
                    + " in=" + e.inputTier() + " out=" + e.outputTier() + " any=" + e.acceptsAnyVoltage());
        }
        if (ic2()) {
            lines.addAll(EnetProbeSC.describe(te));
        }
        return lines;
    }
}
