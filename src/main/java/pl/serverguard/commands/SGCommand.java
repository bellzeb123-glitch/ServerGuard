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
        if (!sender.hasPermission("serverguard.admin")) {
            sender.sendMessage("§cBrak uprawnień.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadAll();
            sender.sendMessage("§a[ServerGuard] Konfiguracja przeładowana.");
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
        sender.sendMessage("§6╔══════════════════════════════╗");
        sender.sendMessage("§6║   §eServerGuard §6v2.1.1         ║");
        sender.sendMessage("§6╠══════════════════════════════╣");
        sender.sendMessage("§6║ §a/sg §7lub §a/sg gui §6— panel GUI  ║");
        sender.sendMessage("§6║ §a/sg reload §6— przeładuj config ║");
        sender.sendMessage("§6║ §a/sghistory <nick> [typ] [n]  §6║");
        sender.sendMessage("§6║ §a/sgsearch <fraza>            §6║");
        sender.sendMessage("§6╚══════════════════════════════╝");
    }
}
