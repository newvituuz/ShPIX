package dev.singlehope.free.shpix.scheduler;

import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

interface SchedulerBackend {

    void async(Plugin plugin, Runnable runnable);

    Schedulers.Task asyncTimer(Plugin plugin, Runnable runnable, long delayMillis, long periodMillis);

    void global(Plugin plugin, Runnable runnable);

    void entity(Plugin plugin, Entity entity, Runnable runnable, Runnable retired);

    Schedulers.Task entityTimer(Plugin plugin, Entity entity, Runnable runnable, long delayTicks, long periodTicks);
}
