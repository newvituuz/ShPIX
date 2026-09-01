package dev.singlehope.free.shpix.user;

import dev.singlehope.free.shpix.ShPixPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class UserListener implements Listener {

    private final ShPixPlugin plugin;

    public UserListener(final ShPixPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(final AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        this.plugin.users().load(event.getUniqueId(), event.getName());
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        this.plugin.users().getOrEmpty(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        this.plugin.orders().deliverPendingFor(event.getPlayer());
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        this.plugin.users().unload(event.getPlayer().getUniqueId());
        this.plugin.couponInput().cancel(event.getPlayer().getUniqueId());
    }
}
