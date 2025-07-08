package com.example.fillloot;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityLockableLoot;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.entity.item.EntityMinecartChest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.command.NumberInvalidException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
        int blocksProcessed = 0;

        System.out.println("[FillLoot] Listing fields in TileEntityLockableLoot:");
        for (Field field : TileEntityLockableLoot.class.getDeclaredFields()) {
            field.setAccessible(true);
            System.out.println(" - " + field.getName() + " : " + field.getType().getSimpleName());
        }

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                for (int y = 0; y < world.getHeight(); y++) {
                    blocksProcessed++;
                    BlockPos pos = new BlockPos(x, y, z);
                    TileEntity te = world.getTileEntity(pos);

                    if (te instanceof TileEntityLockableLoot) {
                        TileEntityLockableLoot lootTile = (TileEntityLockableLoot) te;

                        if (hasLootTable(lootTile)) {
                            lootTile.fillWithLoot(null);
                            for (int i = 0; i < lootTile.getSizeInventory(); i++) {
                                ItemStack stack = lootTile.getStackInSlot(i);
                                if (!stack.isEmpty()) {
                                    if (stack.getItem() == Items.GOLDEN_APPLE && stack.getMetadata() == 1) {
                                        lootTile.setInventorySlotContents(i, new ItemStack(Items.GOLDEN_APPLE, stack.getCount(), 0));
                                    }
                                }
                            }
                            lootTile.markDirty();
                            world.markBlockRangeForRenderUpdate(pos, pos);
                            filledCount++;

                            // Debug output to the player or console
                            sender.sendMessage(new TextComponentString("\u00a7eFilled loot at: " + pos));
                        }
                        else {
                            sender.sendMessage(new TextComponentString("\u00a7eNo loot table at: " + pos));
                        }

                    }
                    // Look for minecart chests at this location
                    List<EntityMinecartChest> carts = world.getEntitiesWithinAABB(
                            EntityMinecartChest.class,
                            new AxisAlignedBB(pos)
                    );

                    for (EntityMinecartChest cart : carts) {
                        // Generate loot
                        invokeFillWithLoot(cart);

                        // Replace with a static chest block
                        world.setBlockState(pos, Blocks.CHEST.getDefaultState(), 3); // Use flag 3 to notify neighbors and update

                        TileEntity tile = world.getTileEntity(pos);

                        if (tile instanceof TileEntityChest) {
                            TileEntityChest chest = (TileEntityChest) tile;

                            // Transfer items from minecart chest to static chest
                            for (int i = 0; i < cart.getSizeInventory(); i++) {
                                ItemStack stack = cart.getStackInSlot(i);
                                if (!stack.isEmpty()) {
                                    // Check if the item is an enchanted golden apple
                                    if (stack.getItem() == Items.GOLDEN_APPLE && stack.getMetadata() == 1) {
                                        // Replace with regular golden apple
                                        stack = new ItemStack(Items.GOLDEN_APPLE, stack.getCount(), 0);
                                    }
                                    chest.setInventorySlotContents(i, stack.copy());
                                    cart.setInventorySlotContents(i, ItemStack.EMPTY);  // Clear cart slot here
                                }
                            }
                            chest.markDirty();
                            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);

                            sender.sendMessage(new TextComponentString("\u00a7aConverted minecart chest at: " + pos));
                            filledCount++;
                        }

                        // Remove the minecart chest entity
                        cart.setDead();
                    }

                }
            }
        }

        sender.sendMessage(new TextComponentString("\u00a7Filled " + filledCount + " containers with loot."));
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

    private void invokeFillWithLoot(EntityMinecartChest cart) {
        try {
            Method fillMethod = EntityMinecartChest.class.getDeclaredMethod("func_184283_b", net.minecraft.entity.player.EntityPlayer.class);
            fillMethod.setAccessible(true);
            fillMethod.invoke(cart, new Object[] { null });
        } catch (Exception e) {
            System.out.println("[FillLoot] Failed to reflectively call fillWithLoot: " + e.getMessage());
        }
    }

}
