package com.example.fillloot;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityLockableLoot;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.command.NumberInvalidException;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

public class CommandFillLoot extends CommandBase {

    private static Field lootTableField;

    static {
        try {
            lootTableField = TileEntityLockableLoot.class.getDeclaredField("field_184284_m");
            lootTableField.setAccessible(true);
            System.out.println("[FillLoot] Successfully hooked loot table field: field_184284_m");
        } catch (Exception e) {
            lootTableField = null;
            System.out.println("[FillLoot] Final fallback — no loot table field found.");
        }
    }


    @Override
    public String getName() {
        return "fillloot";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/fillloot <x> <z> <radius>";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(new TextComponentString("Usage: /fillloot <x> <z> <radius>"));
            return;
        }

        int centerX, centerZ, radius;

        try {
            centerX = parseInt(args[0]);
            centerZ = parseInt(args[1]);
            radius = parseInt(args[2]);
        } catch (NumberInvalidException e) {
            sender.sendMessage(new TextComponentString("Coordinates and radius must be integers."));
            return;
        }

        World world = sender.getEntityWorld();
        int filledCount = 0;

        System.out.println("[FillLoot] Listing fields in TileEntityLockableLoot:");
        for (Field field : TileEntityLockableLoot.class.getDeclaredFields()) {
            field.setAccessible(true);
            System.out.println(" - " + field.getName() + " : " + field.getType().getSimpleName());
        }

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                for (int y = 0; y < world.getHeight(); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    TileEntity te = world.getTileEntity(pos);

                    if (te instanceof TileEntityLockableLoot) {
                        TileEntityLockableLoot lootTile = (TileEntityLockableLoot) te;

                        if (hasLootTable(lootTile)) {
                            lootTile.fillWithLoot(null);
                            lootTile.markDirty();
                            world.markBlockRangeForRenderUpdate(pos, pos);
                            filledCount++;

                            // Debug output to the player or console
                            sender.sendMessage(new TextComponentString("§eFilled loot at: " + pos));
                        }
                        else {
                            sender.sendMessage(new TextComponentString("§eNo loot table at: " + pos));
                        }

                    }
                }
            }
        }

        sender.sendMessage(new TextComponentString("§eFilled " + filledCount + " containers with loot."));
    }

    private boolean hasLootTable(TileEntityLockableLoot tile) {
        if (lootTableField == null) {
            System.out.println("[FillLoot] Reflection failed: lootTableField is null.");
            return false;
        }
        try {
            ResourceLocation loot = (ResourceLocation) lootTableField.get(tile);
            System.out.println("[FillLoot] LootTable at " + tile.getPos() + " = " + loot);
            return loot != null;
        } catch (IllegalAccessException e) {
            System.out.println("[FillLoot] Reflection access error: " + e.getMessage());
            return false;
        }
    }



    @Override
    public int getRequiredPermissionLevel() {
        return 2; // OP only
    }

    @Override
    public List<String> getAliases() {
        return Collections.emptyList();
    }
}
