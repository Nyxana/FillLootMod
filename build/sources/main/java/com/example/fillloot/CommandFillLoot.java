package com.example.fillloot;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.NumberInvalidException;
import net.minecraft.entity.item.EntityMinecartChest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityLockableLoot;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

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
            sender.sendMessage(new TextComponentString("Centre coordinates and radius must be integers."));
            return;
        }

        World world = sender.getEntityWorld();

        if (!(sender instanceof EntityPlayerMP)) {
            sender.sendMessage(new TextComponentString("This command must be run by a player."));
            return;
        }

        TickHandler.startTask(new FillLootTask(world, centerX, centerZ, radius, (EntityPlayerMP) sender));
    }

    public static void tryFillLootAt(World world, BlockPos pos, ICommandSender sender) {
        TileEntity te = world.getTileEntity(pos);

        boolean lootGenerated = false;

        if (te instanceof TileEntityLockableLoot) {
            TileEntityLockableLoot lootTile = (TileEntityLockableLoot) te;

            if (hasLootTable(lootTile)) {
                lootTile.fillWithLoot(null);
                boolean changed = false;

                for (int i = 0; i < lootTile.getSizeInventory(); i++) {
                    ItemStack stack = lootTile.getStackInSlot(i);
                    if (!stack.isEmpty() && stack.getItem() == Items.GOLDEN_APPLE && stack.getMetadata() == 1) {
                        lootTile.setInventorySlotContents(i, new ItemStack(Items.GOLDEN_APPLE, stack.getCount(), 0));
                        changed = true;
                    }
                }
                lootTile.markDirty();
                if (changed) {
                    world.markBlockRangeForRenderUpdate(pos, pos);
                }
                lootGenerated = true;
            }
        }

        // Check for minecart chests at pos
        List<EntityMinecartChest> carts = world.getEntitiesWithinAABB(EntityMinecartChest.class, new AxisAlignedBB(pos));

        for (EntityMinecartChest cart : carts) {
            invokeFillWithLoot(cart);

            // Replace with static chest block
            world.setBlockState(pos, Blocks.CHEST.getDefaultState(), 3);
            TileEntity tile = world.getTileEntity(pos);

            if (tile instanceof TileEntityChest) {
                TileEntityChest chest = (TileEntityChest) tile;

                boolean hasItems = false;

                for (int i = 0; i < cart.getSizeInventory(); i++) {
                    ItemStack stack = cart.getStackInSlot(i);
                    if (!stack.isEmpty()) {
                        hasItems = true;
                        if (stack.getItem() == Items.GOLDEN_APPLE && stack.getMetadata() == 1) {
                            stack = new ItemStack(Items.GOLDEN_APPLE, stack.getCount(), 0);
                        }
                        chest.setInventorySlotContents(i, stack.copy());
                        cart.setInventorySlotContents(i, ItemStack.EMPTY); // clear to avoid drops
                    }
                }
                if (hasItems) {
                    chest.markDirty();
                    world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
                }
                lootGenerated = true;
            }

            cart.setDead();
        }

        // Only send a message if loot actually generated to reduce spam
        if (lootGenerated) {
            //sender.sendMessage(new TextComponentString("\u00a7aFilled loot at: " + pos));
        }
    }

     static boolean hasLootTable(TileEntityLockableLoot tile) {
        if (lootTableField == null) {
            return false;
        }
        try {
            ResourceLocation loot = (ResourceLocation) lootTableField.get(tile);
            return loot != null;
        } catch (IllegalAccessException e) {
            return false;
        }
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public List<String> getAliases() {
        return Collections.emptyList();
    }

    private static void invokeFillWithLoot(EntityMinecartChest cart) {
        try {
            Method fillMethod = EntityMinecartChest.class.getDeclaredMethod("func_184283_b", EntityPlayer.class);
            fillMethod.setAccessible(true);
            fillMethod.invoke(cart, (EntityPlayer) null);
        } catch (Exception e) {
            System.out.println("[FillLoot] Failed to reflectively call fillWithLoot: " + e.getMessage());
        }
    }
}
