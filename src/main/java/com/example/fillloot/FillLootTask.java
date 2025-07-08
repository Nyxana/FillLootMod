package com.example.fillloot;

import net.minecraft.entity.item.EntityMinecartChest;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketTitle;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityLockableLoot;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class FillLootTask implements TickHandler.TickTask {

    public static FillLootTask currentTask = null;

    private enum Phase { PRECOMPUTE, FILLING }

    private static final ExecutorService SHARED_EXECUTOR =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    private final int maxBlocksPerTick = FillLootConfig.MAX_BLOCKS_PER_TICK;
    private final int chunksPerTick = FillLootConfig.SCAN_CHUNKS_PER_TICK;

    private final World world;
    private final EntityPlayerMP player;

    private final List<BlockPos> allPositions = new ArrayList<>(2048);
    private final List<Future<List<BlockPos>>> futures = new ArrayList<>(256);

    private final int chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ, height;
    private final int centreX, centreZ, radius;

    private final List<ChunkPos> chunkPositions;
    private final AtomicInteger chunksSubmitted = new AtomicInteger();
    private final int totalChunks;

    private Phase phase = Phase.PRECOMPUTE;
    private boolean started = false;
    private boolean cancelled = false;

    private int tickCounter = 0;
    private int lastPercentSent = -1;
    private int currentIndex = 0;

    public FillLootTask(World world, int centreX, int centreZ, int radius, EntityPlayerMP player) {
        this.world = world;
        this.player = player;
        this.centreX = centreX;
        this.centreZ = centreZ;
        this.radius = radius;
        this.height = world.getHeight();

        this.chunkMinX = (centreX - radius) >> 4;
        this.chunkMaxX = (centreX + radius) >> 4;
        this.chunkMinZ = (centreZ - radius) >> 4;
        this.chunkMaxZ = (centreZ + radius) >> 4;

        chunkPositions = new ArrayList<>((chunkMaxX - chunkMinX + 1) * (chunkMaxZ - chunkMinZ + 1));
        for (int x = chunkMinX; x <= chunkMaxX; x++) {
            for (int z = chunkMinZ; z <= chunkMaxZ; z++) {
                chunkPositions.add(new ChunkPos(x, z));
            }
        }

        totalChunks = chunkPositions.size();
        currentTask = this;
    }

    public void cancel() {
        cancelled = true;
        futures.clear();
    }

    @Override
    public boolean tick() {
        if (cancelled) {
            player.sendMessage(new TextComponentString("\u00a7c[FillLoot] Process cancelled"));
            currentTask = null;
            return true;
        }

        return (phase == Phase.PRECOMPUTE) ? tickPrecompute() : tickFilling();
    }

    private boolean tickPrecompute() {
        if (!started) {
            player.sendMessage(new TextComponentString(String.format(
                    "\u00a7e[FillLoot] Pre-compute starting for radius %d around x=%d, z=%d",
                    radius, centreX, centreZ
            )));
            started = true;
        }

        int submitted = 0;
        while (chunksSubmitted.get() < totalChunks && submitted < chunksPerTick && !cancelled) {
            int index = chunksSubmitted.getAndIncrement();
            if (index >= totalChunks) break;

            ChunkPos pos = chunkPositions.get(index);
            Chunk chunk = world.getChunkFromChunkCoords(pos.x, pos.z);
            Map<BlockPos, TileEntity> tileMap = new HashMap<>(chunk.getTileEntityMap());

            int x = pos.x << 4, z = pos.z << 4;
            AxisAlignedBB box = new AxisAlignedBB(x, 0, z, x + 16, height, z + 16);
            List<EntityMinecartChest> carts = world.getEntitiesWithinAABB(EntityMinecartChest.class, box);

            futures.add(SHARED_EXECUTOR.submit(() -> {
                List<BlockPos> result = new ArrayList<>();
                for (TileEntity te : tileMap.values()) {
                    if (te instanceof TileEntityLockableLoot &&
                            CommandFillLoot.hasLootTable((TileEntityLockableLoot) te)) {
                        result.add(te.getPos());
                    }
                }
                for (EntityMinecartChest cart : carts) {
                    result.add(cart.getPosition());
                }
                return result;
            }));

            submitted++;
        }

        List<BlockPos> batch = new ArrayList<>(64);
        futures.removeIf(future -> {
            if (future.isDone()) {
                try {
                    batch.addAll(future.get());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return true;
            }
            return false;
        });

        allPositions.addAll(batch);

        int completed = chunksSubmitted.get() - futures.size();
        int percent = (int) ((completed / (double) totalChunks) * 100);

        tickCounter++;
        if (tickCounter % 10 == 0 || percent != lastPercentSent) {
            sendActionBarProgress((double) completed / totalChunks, "Pre-compute");
            lastPercentSent = percent;
        }

        if (chunksSubmitted.get() >= totalChunks && futures.isEmpty()) {
            player.sendMessage(new TextComponentString(
                    "\u00a7a[FillLoot] Pre-compute complete - " + allPositions.size() + " lootable blocks found"
            ));
            phase = Phase.FILLING;
            started = false;
            lastPercentSent = -1;
            tickCounter = 0;
        }

        return false;
    }

    private boolean tickFilling() {
        int total = allPositions.size();
        int estimateSeconds = (int) Math.ceil(total / (double) maxBlocksPerTick / 20.0);

        if (!started) {
            player.sendMessage(new TextComponentString("\u00a7e[FillLoot] Starting - processing " + total + " blocks"));
            player.sendMessage(new TextComponentString("\u00a7e[FillLoot] Estimated duration: " + estimateSeconds + " seconds"));
            started = true;
        }

        int filled = 0;
        while (currentIndex < total && filled < maxBlocksPerTick) {
            CommandFillLoot.tryFillLootAt(world, allPositions.get(currentIndex), player);
            currentIndex++;
            filled++;
        }

        tickCounter++;
        int percent = (int) ((currentIndex / (double) total) * 100);
        if (tickCounter % 10 == 0 || percent != lastPercentSent) {
            sendActionBarProgress(currentIndex / (double) total, "Filling");
            lastPercentSent = percent;
        }

        if (currentIndex >= total) {
            player.sendMessage(new TextComponentString("\u00a7a[FillLoot] Complete - processed " + total + " blocks"));
            currentTask = null;
            return true;
        }

        return false;
    }

    private void sendActionBarProgress(double progress, String phaseName) {
        int totalBars = 20;
        int filledBars = (int) (progress * totalBars);
        StringBuilder bar = new StringBuilder(TextFormatting.GREEN.toString());

        for (int i = 0; i < totalBars; i++) {
            bar.append(i < filledBars ? "|" : TextFormatting.DARK_GRAY + "|");
        }

        String msg = TextFormatting.GOLD + "[FillLoot " + phaseName + "] " + bar + " " + (int) (progress * 100) + "%";
        player.connection.sendPacket(new SPacketTitle(SPacketTitle.Type.ACTIONBAR, new TextComponentString(msg)));
    }
}
