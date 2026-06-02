package pl.serverguard.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import pl.serverguard.ServerGuard;
import pl.serverguard.managers.LogEntry;

import java.util.List;

public class CommandListener implements Listener {

    private final ServerGuard plugin;

    public CommandListener(ServerGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        String cmd = event.getMessage();
        String cmdLower = cmd.toLowerCase();

        boolean isAdmin = p.hasPermission("serverguard.admin") || p.hasPermission("serverguard.bypass");

        if (!isAdmin) {
            // Sprawdź zablokowane
            for (String b : plugin.getConfig().getStringList("commands.blocked-list")) {
                if (cmdLower.startsWith(b.toLowerCase())) {
                    event.setCancelled(true);
                    p.sendMessage("§c[ServerGuard] §eTa komenda jest zablokowana.");
                    plugin.getAlertManager().alertBlocked(p.getName(), cmd);
                    plugin.getDb().log(LogEntry.command(
                        p.getUniqueId().toString(), p.getName(),
                        p.getWorld().getName(),
                        p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ(),
                        "[ZABLOKOWANA] " + cmd, true
                    ));
                    return;
                }
            }
        }

        // Sprawdź alerty
        boolean isAlert = false;
        if (!isAdmin) {
            List<String> alertList = plugin.getConfig().getStringList("commands.alert-list");
            for (String a : alertList) {
                if (cmdLower.startsWith(a.toLowerCase())) {
                    isAlert = true;
                    break;
                }
            }
        }

        if (isAlert) {
            plugin.getAlertManager().alertCommand(p.getName(), cmd,
                p.getWorld().getName(),
                p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ());
        }

        if (plugin.getConfig().getBoolean("commands.log-all", true) || isAlert) {
            plugin.getDb().log(LogEntry.command(
                p.getUniqueId().toString(), p.getName(),
                p.getWorld().getName(),
                p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ(),
                cmd, isAlert
            ));
        }
    }
}
