package com.example.fillloot;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import java.io.File;

public class FillLootConfig {
    public static int MAX_BLOCKS_PER_TICK = 262144;
    public static int SCAN_CHUNKS_PER_TICK = 8;

    public static void loadConfig(FMLPreInitializationEvent event) {
        File configFile = new File(event.getModConfigurationDirectory(), "fillloot.cfg");
        Configuration config = new Configuration(configFile);

        config.load();

        MAX_BLOCKS_PER_TICK = config.getInt(
                "maxBlocksPerTick",
                Configuration.CATEGORY_GENERAL,
                MAX_BLOCKS_PER_TICK,
                10000,
                589824,
                "Maximum number of blocks to process per tick when running /fillloot"
        );

        // Optional: enforce fallback if config was corrupted or set to something invalid
        if (MAX_BLOCKS_PER_TICK <= 0) {
            MAX_BLOCKS_PER_TICK = 262144;
        }

        SCAN_CHUNKS_PER_TICK = config.getInt(
                "scanChunksPerTick",
                Configuration.CATEGORY_GENERAL,
                SCAN_CHUNKS_PER_TICK,
                1,
                24,
                "Maximum number of chunks to pre-compute per tick when running /fillloot"
        );

        // Optional: enforce fallback if config was corrupted or set to something invalid
        if (SCAN_CHUNKS_PER_TICK <= 0) {
            SCAN_CHUNKS_PER_TICK = 8;
        }

        if (config.hasChanged()) {
            config.save();
        }
    }
}
