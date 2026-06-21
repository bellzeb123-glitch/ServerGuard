package pl.serverguard.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import pl.serverguard.ServerGuard;

import java.util.List;

public class SGHistoryCommand implements CommandExecutor {

    private final ServerGuard plugin;

    public SGHistoryCommand(ServerGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("serverguard.admin")) {
            sender.sendMessage("§cBrak uprawnień.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§eUżycie: /sghistory <nick> [komendy|skrzynie|bloki] [ilość]");
            return true;
        }

        String playerName = args[0];
        String type = args.length >= 2 ? args[1].toLowerCase() : "komendy";
        int limit = 20;
        if (args.length >= 3) {
            try { limit = Integer.parseInt(args[2]); } catch (NumberFormatException ignored) {}
        }
        limit = Math.min(limit, 100);

        switch (type) {
            case "komendy", "skrzynie", "bloki" -> {}
            default -> {
                sender.sendMessage("§cNieznany typ. Użyj: komendy, skrzynie, bloki");
                return true;
            }
        }

        sender.sendMessage("§6━━━━━━━━━━ §eHistoria §7" + playerName + " §e[" + type + "] §6━━━━━━━━━━");

        final int queryLimit = limit;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<String[]> rows = switch (type) {
                case "komendy"  -> plugin.getDb().getCommands(playerName, queryLimit);
                case "skrzynie" -> plugin.getDb().getContainers(playerName, queryLimit);
                case "bloki"    -> plugin.getDb().getBlocks(playerName, queryLimit);
                default         -> List.of();
            };

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (rows.isEmpty()) {
                    sender.sendMessage("§7Brak danych.");
                } else {
                    switch (type) {
                        case "komendy"  -> formatCommands(sender, rows);
                        case "skrzynie" -> formatContainers(sender, rows);
                        case "bloki"    -> formatBlocks(sender, rows);
                    }
                }
                sender.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            });
        });

        return true;
    }

    private void formatCommands(CommandSender sender, List<String[]> rows) {
        for (String[] r : rows) {
            String alert = "1".equals(r[7]) ? " §c[ALERT]" : "";
            sender.sendMessage("§7" + r[0] + " §e" + r[6] + alert);
            sender.sendMessage("  §8" + r[2] + " §7[" + r[3] + ", " + r[4] + ", " + r[5] + "]");
        }
    }

    private void formatContainers(CommandSender sender, List<String[]> rows) {
        for (String[] r : rows) {
            String color = "WYJĄŁ".equals(r[2]) ? "§c" : "WŁOŻYŁ".equals(r[2]) ? "§a" : "§e";
            String item = r[8] != null ? " §f" + r[8] + " x" + r[9] : "";
            sender.sendMessage("§7" + r[0] + " " + color + r[2] + item
                + " §8[" + r[7] + " @ " + r[3] + " " + r[4] + "," + r[5] + "," + r[6] + "]");
        }
    }

    private void formatBlocks(CommandSender sender, List<String[]> rows) {
        for (String[] r : rows) {
            String color = "ZNISZCZYŁ".equals(r[2]) ? "§c" : "§a";
            sender.sendMessage("§7" + r[0] + " " + color + r[2] + " §f" + r[7]
                + " §8@ " + r[3] + " [" + r[4] + "," + r[5] + "," + r[6] + "]");
        }
    }
}
