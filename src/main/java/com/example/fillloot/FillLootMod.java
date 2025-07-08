package com.example.fillloot;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

@Mod(modid = FillLootMod.MODID, name = FillLootMod.NAME, version = FillLootMod.VERSION)
public class FillLootMod {
    public static final String MODID = "filllootmod";
    public static final String NAME = "Fill Loot Mod";
    public static final String VERSION = "1.2";

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandFillLoot());
    }
}
