package dev.singlehope.free.shpix.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

final class BukkitBackend implements SchedulerBackend {

    @Override
    public void async(final Plugin plugin, final Runnable runnable) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
    }

    @Override
    public Schedulers.Task asyncTimer(final Plugin plugin, final Runnable runnable,
                                      final long delayMillis, final long periodMillis) {
        final BukkitTask task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable,
                Math.max(1L, delayMillis / 50L), Math.max(1L, periodMillis / 50L));
        return task::cancel;
    }

    @Override
    public void global(final Plugin plugin, final Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    @Override
    public void entity(final Plugin plugin, final Entity entity, final Runnable runnable, final Runnable retired) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    @Override
    public Schedulers.Task entityTimer(final Plugin plugin, final Entity entity, final Runnable runnable,
                                       final long delayTicks, final long periodTicks) {
        final BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, runnable,
                Math.max(1L, delayTicks), Math.max(1L, periodTicks));
        return task::cancel;
    }
}
