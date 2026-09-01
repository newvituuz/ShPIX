package dev.singlehope.free.shpix.scheduler;

import dev.singlehope.free.shpix.compat.ServerCompat;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

public final class Schedulers {

    private static final SchedulerBackend BACKEND = select();

    private Schedulers() {
    }

    private static SchedulerBackend select() {
        if (ServerCompat.isFolia()) {
            try {
                return new FoliaBackend();
            } catch (LinkageError ignored) {
                // servidor anunciou Folia mas as classes não estão presentes
            }
        }
        return new BukkitBackend();
    }

    public static boolean isFolia() {
        return ServerCompat.isFolia();
    }

    public static void async(final Plugin plugin, final Runnable runnable) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(runnable, "runnable");
        if (!plugin.isEnabled()) {
            return;
        }
        try {
            BACKEND.async(plugin, runnable);
        } catch (IllegalStateException | IllegalPluginAccessException ignored) {
            // plugin desabilitando
        }
    }

    public static Task asyncTimer(final Plugin plugin, final Runnable runnable,
                                  final long initialDelayMillis, final long periodMillis) {
        try {
            return BACKEND.asyncTimer(plugin, runnable, initialDelayMillis, periodMillis);
        } catch (IllegalStateException | IllegalPluginAccessException ignored) {
            return Task.NOOP;
        }
    }

    public static void global(final Plugin plugin, final Runnable runnable) {
        if (!plugin.isEnabled()) {
            return;
        }
        try {
            BACKEND.global(plugin, runnable);
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
            BACKEND.entity(plugin, entity, runnable, retired);
        } catch (IllegalStateException | IllegalPluginAccessException ignored) {
            runQuietly(retired);
        }
    }

    public static Task entityTimer(final Plugin plugin, final Entity entity, final Runnable runnable,
                                   final long delayTicks, final long periodTicks) {
        try {
            return BACKEND.entityTimer(plugin, entity, runnable, delayTicks, periodTicks);
        } catch (IllegalStateException | IllegalPluginAccessException ignored) {
            return Task.NOOP;
        }
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
