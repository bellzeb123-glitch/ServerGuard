package pl.serverguard.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import pl.serverguard.ServerGuard;
import pl.serverguard.managers.LogEntry;

public class PlayerListener implements Listener {

    private final ServerGuard plugin;

    public PlayerListener(ServerGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        var p = event.getPlayer();
        String ip = p.getAddress() != null ? p.getAddress().getAddress().getHostAddress() : "?";
        plugin.getDb().log(LogEntry.session(
            p.getUniqueId().toString(), p.getName(), "JOIN", ip,
            p.getWorld().getName(),
            p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ()
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        var p = event.getPlayer();
        plugin.getDb().log(LogEntry.session(
            p.getUniqueId().toString(), p.getName(), "QUIT", null,
            p.getWorld().getName(),
            p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ()
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) return;
        var cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.UNKNOWN) return;

        var p = event.getPlayer();
        plugin.getDb().log(LogEntry.command(
            p.getUniqueId().toString(), p.getName(),
            event.getTo().getWorld().getName(),
            event.getTo().getX(), event.getTo().getY(), event.getTo().getZ(),
            "[TP:" + cause.name() + "] z " + fmt(event.getFrom()) + " → " + fmt(event.getTo()),
            false
        ));
    }

    private String fmt(org.bukkit.Location loc) {
        return String.format("%.0f,%.0f,%.0f", loc.getX(), loc.getY(), loc.getZ());
    }
}
