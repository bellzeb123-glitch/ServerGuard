package pl.serverguard.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import pl.serverguard.ServerGuard;
import pl.serverguard.managers.AdminAuditManager;
import pl.serverguard.managers.LogEntry;

public class CommandListener implements Listener {

    private final ServerGuard plugin;

    public CommandListener(ServerGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        String cmd = event.getMessage();

        AdminAuditManager.AuditResult audit = plugin.getAdminAudit().check(p, cmd);
        if (audit.violation()) {
            if (audit.block()) {
                event.setCancelled(true);
                p.sendMessage(c(plugin.getAdminAudit().getPlayerMessage()));
            }

            plugin.getAlertManager().alertUnauthorized(
                p.getName(), cmd, audit.permission(),
                p.getWorld().getName(),
                p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ(),
                audit.block()
            );

            String logPrefix = audit.block() ? "[ZABLOKOWANA] " : "[AUDYT] ";
            plugin.getDb().log(LogEntry.command(
                p.getUniqueId().toString(), p.getName(),
                p.getWorld().getName(),
                p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ(),
                logPrefix + cmd, true
            ));
            return;
        }

        boolean isAdmin = p.hasPermission("serverguard.admin") || p.hasPermission("serverguard.bypass");
        boolean isAlert = false;

        if (!isAdmin) {
            String cmdLower = cmd.toLowerCase();
            for (String a : plugin.getConfig().getStringList("commands.alert-list")) {
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

    private String c(String s) {
        return s.replace("&", "§");
    }
}
