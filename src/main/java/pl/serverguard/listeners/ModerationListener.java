package pl.serverguard.listeners;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerKickEvent;
import pl.serverguard.ServerGuard;
import pl.serverguard.managers.LogEntry;

public class ModerationListener implements Listener {

    private final ServerGuard plugin;

    public ModerationListener(ServerGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (!plugin.getConfig().getBoolean("moderation.log-gamemode", true)) return;

        Player p = event.getPlayer();
        GameMode from = p.getGameMode();
        GameMode to = event.getNewGameMode();
        if (from == to) return;

        String cause = resolveGameModeCause(event);
        String msg = "[GM:" + cause + "] " + from.name() + " → " + to.name();

        plugin.getDb().log(LogEntry.command(
            p.getUniqueId().toString(), p.getName(),
            p.getWorld().getName(),
            p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ(),
            msg, isSensitiveGameMode(to)
        ));

        if (isSensitiveGameMode(to)) {
            plugin.getAlertManager().alertModeration(
                "gamemode:" + p.getUniqueId(),
                "&6GM &f" + p.getName() + " &7→ &e" + to.name()
                    + " &8(" + cause + ", było: " + from.name() + ")"
            );
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) {
        if (!plugin.getConfig().getBoolean("moderation.log-kicks", true)) return;

        Player p = event.getPlayer();
        String reason = event.getReason() != null ? event.getReason() : "brak powodu";
        String msg = "[KICK] " + reason;

        plugin.getDb().log(LogEntry.command(
            p.getUniqueId().toString(), p.getName(),
            p.getWorld().getName(),
            p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ(),
            msg, true
        ));

        plugin.getAlertManager().alertModeration(
            "kick:" + p.getUniqueId(),
            "&cKICK &f" + p.getName() + " &7— &f" + reason
        );
    }

    private static boolean isSensitiveGameMode(GameMode mode) {
        return mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
    }

    private static String resolveGameModeCause(PlayerGameModeChangeEvent event) {
        try {
            Object cause = event.getClass().getMethod("getCause").invoke(event);
            if (cause != null) return cause.toString();
        } catch (ReflectiveOperationException ignored) {
            // starsze API bez getCause()
        }
        return "UNKNOWN";
    }
}
