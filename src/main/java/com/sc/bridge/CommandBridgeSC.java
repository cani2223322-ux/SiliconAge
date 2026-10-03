package com.sc.bridge;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;

/**
 * /scbridge accept|decline <id> - what the chat's [Принять] / [Отклонить] of a bridge consent request run
 * (docs/plan-ground-bridge.md §10). Any player may use it; only the asked player's answer counts.
 */
public class CommandBridgeSC extends CommandBase {

    @Override
    public String getCommandName() {
        return "scbridge";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/scbridge accept|decline <id>";
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
        if (!(sender instanceof EntityPlayer) || args.length != 2 || !("accept".equals(args[0]) || "decline".equals(args[0]))) {
            sender.addChatMessage(new ChatComponentTranslation("sc.bridge.consent.usage"));
            return;
        }
        int id;
        try {
            id = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.addChatMessage(new ChatComponentTranslation("sc.bridge.consent.usage"));
            return;
        }
        BridgeMsgSC m = BridgeFarSC.answer((EntityPlayer) sender, id, "accept".equals(args[0]));
        if (m != null) {
            sender.addChatMessage(m.chat());
        }
    }
}
