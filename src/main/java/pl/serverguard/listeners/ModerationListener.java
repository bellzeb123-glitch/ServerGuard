package pl.serverguard.listeners;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerKickEvent;
import pl.serverguard.ServerGuard;
import pl.serverguard.managers.LogEntry;

import java.util.Locale;
import java.util.Map;

public class ModerationListener implements Listener {

    private final ServerGuard plugin;

    public ModerationListener(ServerGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * Vanilla Paper nie honoruje {@code minecraft.command.gamemode.creative=false}.
     * Tu egzekwujemy per-mode z configu (domyślnie CREATIVE).
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onGameModeRestrict(PlayerGameModeChangeEvent event) {
        if (!plugin.getConfig().getBoolean("moderation.enforce-gamemode-perms", true)) return;

        Player p = event.getPlayer();
        if (canBypassGamemodeRestrict(p)) return;

        GameMode to = event.getNewGameMode();
        String required = requiredPermFor(to);
        if (required == null) return;
        if (p.hasPermission(required)) return;

        event.setCancelled(true);
        String msg = plugin.getConfig().getString("moderation.gamemode-deny-message",
            "&c[ServerGuard] &eNie masz uprawnien do trybu &f{mode}&e.");
        p.sendMessage(c(msg.replace("{mode}", to.name().toLowerCase(Locale.ROOT))));
    }

    /** Wczesna blokada komendy — czytelniejszy komunikat niż sam event. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onGamemodeCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfig().getBoolean("moderation.enforce-gamemode-perms", true)) return;

        Player p = event.getPlayer();
        if (canBypassGamemodeRestrict(p)) return;

        GameMode target = parseGamemodeFromCommand(event.getMessage());
        if (target == null) return;

        String required = requiredPermFor(target);
        if (required == null) return;
        if (p.hasPermission(required)) return;

        event.setCancelled(true);
        String msg = plugin.getConfig().getString("moderation.gamemode-deny-message",
            "&c[ServerGuard] &eNie masz uprawnien do trybu &f{mode}&e.");
        p.sendMessage(c(msg.replace("{mode}", target.name().toLowerCase(Locale.ROOT))));
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

    private boolean canBypassGamemodeRestrict(Player player) {
        return player.isOp()
            || player.hasPermission("serverguard.gamemode.bypass");
    }

    private String requiredPermFor(GameMode mode) {
        var section = plugin.getConfig().getConfigurationSection("moderation.restricted-gamemodes");
        if (section == null) {
            // Domyślnie: creative wymaga osobnej permisji (LP false działa dopiero z tym).
            if (mode == GameMode.CREATIVE) {
                return "minecraft.command.gamemode.creative";
            }
            return null;
        }
        for (Map.Entry<String, Object> e : section.getValues(false).entrySet()) {
            if (e.getKey().equalsIgnoreCase(mode.name())) {
                Object val = e.getValue();
                return val != null ? val.toString() : null;
            }
        }
        return null;
    }

    /** Parsuje /gamemode|/gm creative|c|1|spectator|sp|3 … */
    static GameMode parseGamemodeFromCommand(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        if (s.startsWith("/")) s = s.substring(1);
        String[] parts = s.split("\\s+");
        if (parts.length < 2) return null;
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        if (cmd.contains(":")) cmd = cmd.substring(cmd.indexOf(':') + 1);
        if (!cmd.equals("gamemode") && !cmd.equals("gm")) return null;

        String arg = parts[1].toLowerCase(Locale.ROOT);
        return switch (arg) {
            case "creative", "c", "1" -> GameMode.CREATIVE;
            case "survival", "s", "0" -> GameMode.SURVIVAL;
            case "adventure", "a", "2" -> GameMode.ADVENTURE;
            case "spectator", "sp", "spec", "3" -> GameMode.SPECTATOR;
            default -> null;
        };
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

    private static String c(String s) {
        return s == null ? "" : s.replace('&', '§');
    }
}
