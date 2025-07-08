package com.example.fillloot;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import java.util.Collections;
import java.util.List;

public class CommandFillLootCancel extends CommandBase {
    @Override
    public String getName() {
        return "filllootcancel";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/filllootcancel - Cancel the running FillLoot task";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        // Check if there is a running FillLoot task
        if (FillLootTask.currentTask != null) {
            // Request cancellation
            FillLootTask.currentTask.cancel();
            sender.sendMessage(new TextComponentString("\u00a7c[FillLoot] Task cancellation requested"));
        } else {
            sender.sendMessage(new TextComponentString("\u00a7c[FillLoot] No task is currently running"));
        }
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;  // Requires operator permission
    }

    @Override
    public List<String> getAliases() {
        return Collections.emptyList();
    }
}
