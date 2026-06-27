package pl.serverguard.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.serverguard.ServerGuard;

public class SGCommand implements CommandExecutor {

    private final ServerGuard plugin;

    public SGCommand(ServerGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        var lang = plugin.getLang();
        if (!sender.hasPermission("serverguard.admin")) {
            sender.sendMessage(lang.tr("commands.no-permission"));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadAll();
            sender.sendMessage(lang.tr("commands.reload-ok"));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        if (sender instanceof Player player) {
            if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
                plugin.getAdminGui().openMain(player);
                return true;
            }
        }

        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        var lang = plugin.getLang();
        String ver = plugin.getDescription().getVersion();
        sender.sendMessage(lang.tr("commands.help-header-top"));
        sender.sendMessage(lang.tr("commands.help-header-title", "version", ver));
        sender.sendMessage(lang.tr("commands.help-header-mid"));
        sender.sendMessage(lang.tr("commands.help-line-gui"));
        sender.sendMessage(lang.tr("commands.help-line-reload"));
        sender.sendMessage(lang.tr("commands.help-line-history"));
        sender.sendMessage(lang.tr("commands.help-line-search"));
        sender.sendMessage(lang.tr("commands.help-footer"));
    }
}
