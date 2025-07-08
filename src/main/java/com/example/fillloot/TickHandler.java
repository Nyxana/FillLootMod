package com.example.fillloot;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod.EventBusSubscriber
public class TickHandler {

    public interface TickTask {
        /**
         * Called every server tick.
         * @return true if task is done and can be cleared.
         */
        boolean tick();
    }

    private static TickTask currentTask = null;

    /** Start a new ticking task, replacing any running task */
    public static void startTask(TickTask task) {
        currentTask = task;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        // Run only at tick END to avoid conflicts
        if (event.phase == TickEvent.Phase.END && currentTask != null) {
            boolean done = currentTask.tick();
            if (done) {
                currentTask = null; // Clear finished task
            }
        }
    }
}
