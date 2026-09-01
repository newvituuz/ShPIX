package dev.singlehope.free.shpix.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class Schedulers {

    private static final boolean FOLIA = detectFolia();

    private Schedulers() {
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    public static void async(final Plugin plugin, final Runnable runnable) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(runnable, "runnable");
        if (!plugin.isEnabled()) {
            return;
        }
        try {
            if (FOLIA) {
                Bukkit.getAsyncScheduler().runNow(plugin, task -> runnable.run());
            } else {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
            }
        } catch (IllegalStateException | IllegalPluginAccessException ignored) {
            // plugin desabilitando
        }
    }

    public static Task asyncTimer(final Plugin plugin, final Runnable runnable,
                                  final long initialDelayMillis, final long periodMillis) {
        final long delay = Math.max(1L, initialDelayMillis);
        final long period = Math.max(1L, periodMillis);
        if (FOLIA) {
            final ScheduledTask task = Bukkit.getAsyncScheduler()
                    .runAtFixedRate(plugin, t -> runnable.run(), delay, period, TimeUnit.MILLISECONDS);
            return task == null ? Task.NOOP : task::cancel;
        }
        final BukkitTask task = Bukkit.getScheduler()
                .runTaskTimerAsynchronously(plugin, runnable, Math.max(1L, delay / 50L), Math.max(1L, period / 50L));
        return task::cancel;
    }

    public static void global(final Plugin plugin, final Runnable runnable) {
        if (!plugin.isEnabled()) {
            return;
        }
        try {
            if (FOLIA) {
                Bukkit.getGlobalRegionScheduler().execute(plugin, runnable);
            } else if (Bukkit.isPrimaryThread()) {
                runnable.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, runnable);
            }
        } catch (IllegalStateException | IllegalPluginAccessException ignored) {
            // plugin desabilitando
        }
    }

    public static void entity(final Plugin plugin, final Entity entity, final Runnable runnable) {
        entity(plugin, entity, runnable, null);
    }

    public static void entity(final Plugin plugin, final Entity entity, final Runnable runnable, final Runnable retired) {
        if (!plugin.isEnabled()) {
            runQuietly(retired);
            return;
        }
        try {
            if (FOLIA) {
                final ScheduledTask task = entity.getScheduler().run(plugin, t -> runnable.run(), retired);
                if (task == null) {
                    runQuietly(retired);
                }
            } else {
                Bukkit.getScheduler().runTask(plugin, runnable);
            }
        } catch (IllegalStateException | IllegalPluginAccessException ignored) {
            runQuietly(retired);
        }
    }

    public static Task entityTimer(final Plugin plugin, final Entity entity, final Runnable runnable,
                                   final long delayTicks, final long periodTicks) {
        final long delay = Math.max(1L, delayTicks);
        final long period = Math.max(1L, periodTicks);
        if (FOLIA) {
            final ScheduledTask task = entity.getScheduler().runAtFixedRate(plugin, t -> runnable.run(), null, delay, period);
            return task == null ? Task.NOOP : task::cancel;
        }
        final BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, runnable, delay, period);
        return task::cancel;
    }

    private static void runQuietly(final Runnable runnable) {
        if (runnable != null) {
            runnable.run();
        }
    }

    @FunctionalInterface
    public interface Task {

        Task NOOP = () -> {
        };

        void cancel();
    }
}
