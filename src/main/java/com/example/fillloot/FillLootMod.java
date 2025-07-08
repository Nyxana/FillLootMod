package com.example.fillloot;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = FillLootMod.MODID, name = FillLootMod.NAME, version = FillLootMod.VERSION)
public class FillLootMod {
    public static final String MODID = "filllootmod";
    public static final String NAME = "Fill Loot Mod";
    public static final String VERSION = "2.0.0";

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        FillLootConfig.loadConfig(event);
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandFillLoot());
        event.registerServerCommand(new CommandFillLootCancel());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new TickHandler()); // if not already registered
    }
}
