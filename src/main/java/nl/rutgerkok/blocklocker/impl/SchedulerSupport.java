package nl.rutgerkok.blocklocker.impl;

import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

/**
 * Code to support both the standard schedulers of Bukkit, and Folia's scheduler.
 */
final class SchedulerSupport {

    private static boolean folia;

    static {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException e) {
            folia = false;
        }
    }

    private final Plugin plugin;

    SchedulerSupport(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public void runLater(Block block, Runnable runnable) {
        if (folia) {
            plugin.getServer().getRegionScheduler().run(plugin, block.getLocation(), task -> {
                runnable.run();
            });
        } else {
            plugin.getServer().getScheduler().runTask(plugin, runnable);
        }
    }

    public void runLater(Block block, Runnable runnable, int ticks) {
        if (folia) {
            plugin.getServer().getRegionScheduler().runDelayed(plugin, block.getLocation(), task -> {
                runnable.run();
            }, ticks);
        } else {
            plugin.getServer().getScheduler().runTaskLater(plugin, runnable, ticks);
        }
    }

    void runLaterGlobally(Runnable runnable, int ticks) {
        if (folia) {
            plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, task -> {
                runnable.run();
            }, ticks);
        } else {
            plugin.getServer().getScheduler().runTaskLater(plugin, runnable, ticks);
        }
    }

}
