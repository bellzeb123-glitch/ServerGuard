package pl.serverguard.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerCommandEvent;
import pl.serverguard.ServerGuard;
import pl.serverguard.managers.LogEntry;

public class ConsoleCommandListener implements Listener {

    private final ServerGuard plugin;

    public ConsoleCommandListener(ServerGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsoleCommand(ServerCommandEvent event) {
        if (!plugin.getConfig().getBoolean("moderation.log-console-commands", true)) return;

        String cmd = "/" + event.getCommand();
        boolean sensitive = isSensitive(cmd);

        plugin.getDb().log(LogEntry.command(
            "console", "CONSOLE",
            "console", 0, 0, 0,
            cmd, sensitive
        ));

        if (sensitive) {
            plugin.getAlertManager().alertModeration(
                "console:" + cmd.split(" ")[0].toLowerCase(),
                "&4KONSOLA &c" + cmd
            );
        }
    }

    private boolean isSensitive(String cmdLower) {
        String c = cmdLower.toLowerCase();
        for (String prefix : plugin.getConfig().getStringList("moderation.console-alert-prefixes")) {
            if (c.startsWith(prefix.toLowerCase())) return true;
        }
        return false;
    }
}
