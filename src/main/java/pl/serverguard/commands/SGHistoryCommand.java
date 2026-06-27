package pl.serverguard.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import pl.serverguard.ServerGuard;
import pl.serverguard.config.LangManager;

import java.util.List;

public class SGHistoryCommand implements CommandExecutor {

    private final ServerGuard plugin;

    public SGHistoryCommand(ServerGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        LangManager lang = plugin.getLang();
        if (!sender.hasPermission("serverguard.admin")) {
            sender.sendMessage(lang.tr("commands.no-permission"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(lang.tr("commands.history-usage"));
            return true;
        }

        String playerName = args[0];
        String typeArg = args.length >= 2 ? args[1] : lang.raw("db.type-commands");
        String type = lang.resolveHistoryType(typeArg);
        if (type == null) {
            sender.sendMessage(lang.tr("commands.history-unknown-type"));
            return true;
        }

        int limit = 20;
        if (args.length >= 3) {
            try { limit = Integer.parseInt(args[2]); } catch (NumberFormatException ignored) {}
        }
        limit = Math.min(limit, 100);

        String typeLabel = lang.historyTypeLabel(type);
        sender.sendMessage(lang.tr("commands.history-header", "player", playerName, "type", typeLabel));

        final String typeCommands = lang.raw("db.type-commands");
        final String typeContainers = lang.raw("db.type-containers");
        final String typeBlocks = lang.raw("db.type-blocks");
        final int queryLimit = limit;
        final String queryType = type;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<String[]> rows = switch (queryType) {
                case String t when t.equals(typeCommands) ->
                    plugin.getDb().getCommands(playerName, queryLimit);
                case String t when t.equals(typeContainers) ->
                    plugin.getDb().getContainers(playerName, queryLimit);
                case String t when t.equals(typeBlocks) ->
                    plugin.getDb().getBlocks(playerName, queryLimit);
                default -> List.of();
            };

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (rows.isEmpty()) {
                    sender.sendMessage(lang.tr("commands.history-empty"));
                } else {
                    if (queryType.equals(typeCommands)) formatCommands(sender, rows);
                    else if (queryType.equals(typeContainers)) formatContainers(sender, rows);
                    else formatBlocks(sender, rows);
                }
                sender.sendMessage(lang.tr("commands.history-footer"));
            });
        });

        return true;
    }

    private void formatCommands(CommandSender sender, List<String[]> rows) {
        LangManager lang = plugin.getLang();
        for (String[] r : rows) {
            String alert = "1".equals(r[7]) ? lang.tr("commands.history-alert") : "";
            sender.sendMessage("§7" + r[0] + " §e" + r[6] + alert);
            sender.sendMessage("  §8" + r[2] + " §7[" + r[3] + ", " + r[4] + ", " + r[5] + "]");
        }
    }

    private void formatContainers(CommandSender sender, List<String[]> rows) {
        LangManager lang = plugin.getLang();
        String take = lang.raw("db.action-take");
        String put = lang.raw("db.action-put");
        for (String[] r : rows) {
            String label = lang.actionLabel(r[2]);
            String color = take.equals(r[2]) ? "§c" : put.equals(r[2]) ? "§a" : "§e";
            String item = r[8] != null ? " §f" + r[8] + " x" + r[9] : "";
            sender.sendMessage("§7" + r[0] + " " + color + label + item
                + " §8[" + r[7] + " @ " + r[3] + " " + r[4] + "," + r[5] + "," + r[6] + "]");
        }
    }

    private void formatBlocks(CommandSender sender, List<String[]> rows) {
        LangManager lang = plugin.getLang();
        String destroy = lang.raw("db.action-destroy");
        for (String[] r : rows) {
            String label = lang.actionLabel(r[2]);
            String color = destroy.equals(r[2]) ? "§c" : "§a";
            sender.sendMessage("§7" + r[0] + " " + color + label + " §f" + r[7]
                + " §8@ " + r[3] + " [" + r[4] + "," + r[5] + "," + r[6] + "]");
        }
    }
}
