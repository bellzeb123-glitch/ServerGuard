package pl.serverguard.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import pl.serverguard.ServerGuard;
import pl.serverguard.managers.LogEntry;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PlayerListener implements Listener {

    private final ServerGuard plugin;
    private Set<PlayerTeleportEvent.TeleportCause> loggedCauses = Set.of();

    public PlayerListener(ServerGuard plugin) {
        this.plugin = plugin;
        reloadCauses();
    }

    public void reloadCauses() {
        List<String> names = plugin.getConfig().getStringList("teleports.log-causes");
        Set<PlayerTeleportEvent.TeleportCause> causes = new HashSet<>();
        for (String name : names) {
            try {
                causes.add(PlayerTeleportEvent.TeleportCause.valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("teleports.log-causes: nieznana przyczyna '" + name + "'");
            }
        }
        loggedCauses = Set.copyOf(causes);
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
        if (!plugin.getConfig().getBoolean("teleports.enabled", true)) return;
        if (event.getTo() == null) return;

        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (!loggedCauses.contains(cause)) return;

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
