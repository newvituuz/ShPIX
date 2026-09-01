package dev.singlehope.free.shpix.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

final class FoliaBackend implements SchedulerBackend {

    @Override
    public void async(final Plugin plugin, final Runnable runnable) {
        Bukkit.getAsyncScheduler().runNow(plugin, task -> runnable.run());
    }

    @Override
    public Schedulers.Task asyncTimer(final Plugin plugin, final Runnable runnable,
                                      final long delayMillis, final long periodMillis) {
        final ScheduledTask task = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> runnable.run(),
                Math.max(1L, delayMillis), Math.max(1L, periodMillis), TimeUnit.MILLISECONDS);
        return task == null ? Schedulers.Task.NOOP : task::cancel;
    }

    @Override
    public void global(final Plugin plugin, final Runnable runnable) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, runnable);
    }

    @Override
    public void entity(final Plugin plugin, final Entity entity, final Runnable runnable, final Runnable retired) {
        final ScheduledTask task = entity.getScheduler().run(plugin, t -> runnable.run(), retired);
        if (task == null && retired != null) {
            retired.run();
        }
    }

    @Override
    public Schedulers.Task entityTimer(final Plugin plugin, final Entity entity, final Runnable runnable,
                                       final long delayTicks, final long periodTicks) {
        final ScheduledTask task = entity.getScheduler().runAtFixedRate(plugin, t -> runnable.run(), null,
                Math.max(1L, delayTicks), Math.max(1L, periodTicks));
        return task == null ? Schedulers.Task.NOOP : task::cancel;
    }
}
