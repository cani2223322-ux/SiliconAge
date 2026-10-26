package com.sc.settle;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.util.FakePlayer;

/**
 * ПД-2 (docs/plan-ideas-2026-10-26.md): «/scsquad accept|decline <name>» - the [Принять] / [Отклонить] of a squad invitation (the chat line
 * SquadSC sends after a Credit Chip's click), «/scsquad leave», «/scsquad list». Any player (level 0), never the console. Registered by
 * SCMod.serverStarting (the coordinator's); the body is ПД-2's (SquadSC does the work).
 */
public class CommandSquadSC extends CommandBase {

    private static final String[] SUBS = {"accept", "decline", "leave", "list"};

    @Override
    public String getCommandName() {
        return "scsquad";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/scsquad accept|decline <name> | leave | list";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return sender instanceof EntityPlayer;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP) || sender instanceof FakePlayer || args == null || args.length == 0) {
            usage(sender);
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) sender;
        String sub = args[0];
        String name = args.length > 1 ? args[1] : "";
        if ("accept".equalsIgnoreCase(sub) && args.length > 1) {
            SquadSC.accept(p, name);
        } else if ("decline".equalsIgnoreCase(sub) && args.length > 1) {
            SquadSC.decline(p, name);
        } else if ("leave".equalsIgnoreCase(sub)) {
            SettlementRegistrySC reg = SettlementRegistrySC.get(p.worldObj);
            if (reg == null || !SquadSC.leave(reg, p.getUniqueID(), p.getCommandSenderName())) {
                SquadSC.tell(p, EnumChatFormatting.GRAY, "sc.squad.none");
            }
        } else if ("list".equalsIgnoreCase(sub)) {
            SquadSC.list(p);
        } else {
            usage(sender);
        }
    }

    private static void usage(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentTranslation("sc.cmd.squad.usage").setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GRAY)));
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, SUBS);
        }
        if (args.length == 2 && ("accept".equalsIgnoreCase(args[0]) || "decline".equalsIgnoreCase(args[0]))) {
            return getListOfStringsMatchingLastWord(args, net.minecraft.server.MinecraftServer.getServer().getAllUsernames());
        }
        return new ArrayList<String>();
    }
}
