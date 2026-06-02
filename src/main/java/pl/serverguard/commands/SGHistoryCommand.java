package pl.serverguard.commands;

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
            sender.sendMessage("§eUżycie: /sghistory <nick> [komendy|pozycje|skrzynie|bloki] [ilość]");
            return true;
        }

        String playerName = args[0];
        String type = args.length >= 2 ? args[1].toLowerCase() : "komendy";
        int limit = 20;
        if (args.length >= 3) {
            try { limit = Integer.parseInt(args[2]); } catch (NumberFormatException ignored) {}
        }
        limit = Math.min(limit, 100);

        sender.sendMessage("§6━━━━━━━━━━ §eHistoria §7" + playerName + " §e[" + type + "] §6━━━━━━━━━━");

        switch (type) {
            case "komendy" -> showCommands(sender, playerName, limit);
            case "pozycje" -> showPositions(sender, playerName, limit);
            case "skrzynie" -> showContainers(sender, playerName, limit);
            case "bloki" -> showBlocks(sender, playerName, limit);
            default -> {
                sender.sendMessage("§cNieznany typ. Użyj: komendy, pozycje, skrzynie, bloki");
                return true;
            }
        }

        sender.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        return true;
    }

    private void showCommands(CommandSender sender, String name, int limit) {
        List<String[]> rows = plugin.getDb().getCommands(name, limit);
        if (rows.isEmpty()) {
            sender.sendMessage("§7Brak danych.");
            return;
        }
        for (String[] r : rows) {
            // r: timestamp, player, world, x, y, z, command, isAlert
            String alert = "1".equals(r[7]) ? " §c[ALERT]" : "";
            String loc = "§8" + r[2] + " §7[" + r[3] + ", " + r[4] + ", " + r[5] + "]";
            sender.sendMessage("§7" + r[0] + " §e" + r[6] + alert);
            sender.sendMessage("  " + loc);
        }
    }

    private void showPositions(CommandSender sender, String name, int limit) {
        List<String[]> rows = plugin.getDb().getPositions(name, limit);
        if (rows.isEmpty()) {
            sender.sendMessage("§7Brak danych.");
            return;
        }
        for (String[] r : rows) {
            // r: timestamp, player, world, x, y, z
            sender.sendMessage("§7" + r[0] + " §b" + r[2] + " §a[" + r[3] + ", " + r[4] + ", " + r[5] + "]");
        }
    }

    private void showContainers(CommandSender sender, String name, int limit) {
        List<String[]> rows = plugin.getDb().getContainerLogs(name, limit);
        if (rows.isEmpty()) {
            sender.sendMessage("§7Brak danych.");
            return;
        }
        for (String[] r : rows) {
            // r: timestamp, player, action, world, x, y, z, container_type, item, amount
            String action = r[2];
            String color = action.equals("WYJĄŁ") ? "§c" : action.equals("WŁOŻYŁ") ? "§a" : "§e";
            String item = r[8] != null ? " §f" + r[8] + " x" + r[9] : "";
            sender.sendMessage("§7" + r[0] + " " + color + action + item
                    + " §8[" + r[7] + " @ " + r[3] + " " + r[4] + "," + r[5] + "," + r[6] + "]");
        }
    }

    private void showBlocks(CommandSender sender, String name, int limit) {
        List<String[]> rows = plugin.getDb().getBlockLogs(name, limit);
        if (rows.isEmpty()) {
            sender.sendMessage("§7Brak danych.");
            return;
        }
        for (String[] r : rows) {
            // r: timestamp, player, action, world, x, y, z, block_type
            String color = r[2].equals("ZNISZCZYŁ") ? "§c" : "§a";
            sender.sendMessage("§7" + r[0] + " " + color + r[2] + " §f" + r[7]
                    + " §8@ " + r[3] + " [" + r[4] + "," + r[5] + "," + r[6] + "]");
        }
    }
}
