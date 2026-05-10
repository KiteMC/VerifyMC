package team.kitemc.verifymc.util;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Small compatibility layer for Bukkit/Paper/Folia-style schedulers.
 */
public final class PluginScheduler {
    private static final boolean FOLIA_AVAILABLE = isClassPresent("io.papermc.paper.threadedregions.RegionizedServer");

    private PluginScheduler() {
    }

    public static ScheduledTask runGlobal(Plugin plugin, Runnable task) {
        if (FOLIA_AVAILABLE) {
            return runFoliaGlobal(plugin, task);
        }
        BukkitTask bukkitTask = Bukkit.getScheduler().runTask(plugin, task);
        return bukkitTask::cancel;
    }

    public static ScheduledTask runAsyncTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (FOLIA_AVAILABLE) {
            return runFoliaAsyncTimer(plugin, task, delayTicks, periodTicks);
        }
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
        return bukkitTask::cancel;
    }

    private static ScheduledTask runFoliaGlobal(Plugin plugin, Runnable task) {
        try {
            Object scheduler = Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(Bukkit.getServer());
            Method run = scheduler.getClass().getMethod("run", Plugin.class, java.util.function.Consumer.class);
            Object scheduledTask = run.invoke(scheduler, plugin, (java.util.function.Consumer<Object>) ignored -> task.run());
            return cancelHandle(scheduledTask);
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("[VerifyMC] Failed to schedule global task on Folia scheduler: " + e.getMessage());
            return () -> {
            };
        }
    }

    private static ScheduledTask runFoliaAsyncTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        try {
            Object scheduler = Bukkit.getServer().getClass().getMethod("getAsyncScheduler").invoke(Bukkit.getServer());
            Method runAtFixedRate = scheduler.getClass().getMethod(
                    "runAtFixedRate",
                    Plugin.class,
                    java.util.function.Consumer.class,
                    long.class,
                    long.class,
                    TimeUnit.class
            );
            Object scheduledTask = runAtFixedRate.invoke(
                    scheduler,
                    plugin,
                    (java.util.function.Consumer<Object>) ignored -> task.run(),
                    ticksToMillis(delayTicks),
                    ticksToMillis(periodTicks),
                    TimeUnit.MILLISECONDS
            );
            return cancelHandle(scheduledTask);
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("[VerifyMC] Failed to schedule async task on Folia scheduler: " + e.getMessage());
            return () -> {
            };
        }
    }

    private static ScheduledTask cancelHandle(Object scheduledTask) {
        return () -> {
            try {
                scheduledTask.getClass().getMethod("cancel").invoke(scheduledTask);
            } catch (ReflectiveOperationException ignored) {
            }
        };
    }

    private static long ticksToMillis(long ticks) {
        return Math.max(1L, ticks * 50L);
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public interface ScheduledTask {
        void cancel();
    }
}
