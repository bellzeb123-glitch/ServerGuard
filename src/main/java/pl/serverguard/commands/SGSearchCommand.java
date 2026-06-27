package pl.serverguard.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import pl.serverguard.ServerGuard;

import java.util.List;

public class SGSearchCommand implements CommandExecutor {

    private final ServerGuard plugin;

    public SGSearchCommand(ServerGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        var lang = plugin.getLang();
        if (!sender.hasPermission("serverguard.admin")) {
            sender.sendMessage(lang.tr("commands.no-permission"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(lang.tr("commands.search-usage"));
            return true;
        }

        String query = String.join(" ", args);
        sender.sendMessage(lang.tr("commands.search-loading", "query", query));

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<String[]> results = plugin.getDb().searchAll(query, 50);

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (results.isEmpty()) {
                    sender.sendMessage(lang.tr("commands.search-empty", "query", query));
                    return;
                }

                sender.sendMessage(lang.tr("commands.search-header", "query", query, "count", results.size()));
                for (String[] r : results) {
                    String typeColor = getTypeColor(r[2]);
                    sender.sendMessage("§7" + r[0] + " §b" + r[1]
                            + " " + typeColor + "[" + r[2] + "] §f" + r[3]);
                }
                sender.sendMessage(lang.tr("commands.search-footer"));
            });
        });

        return true;
    }

    private String getTypeColor(String type) {
        if (type.startsWith("KOMENDA")) return "§c";
        if (type.startsWith("KONTENER")) return "§e";
        if (type.startsWith("BLOK")) return "§a";
        return "§7";
    }
}
