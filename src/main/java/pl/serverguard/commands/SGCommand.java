package pl.serverguard.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
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

        sender.sendMessage("§6╔══════════════════════════════╗");
        sender.sendMessage("§6║   §eServerGuard §6v1.0.0         ║");
        sender.sendMessage("§6╠══════════════════════════════╣");
        sender.sendMessage("§6║ §a/sghistory <nick> [typ] [n]  §6║");
        sender.sendMessage("§6║   §7typy: komendy, pozycje,     §6║");
        sender.sendMessage("§6║   §7       skrzynie, bloki       §6║");
        sender.sendMessage("§6║ §a/sgsearch <fraza>            §6║");
        sender.sendMessage("§6║ §a/sg reload                   §6║");
        sender.sendMessage("§6╠══════════════════════════════╣");
        sender.sendMessage("§6║ §7Monitorowane: komendy, pozycje,§6║");
        sender.sendMessage("§6║ §7skrzynie, bloki, sesje         §6║");
        sender.sendMessage("§6╚══════════════════════════════╝");

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            sender.sendMessage("§a[ServerGuard] Konfiguracja przeładowana.");
        }

        return true;
    }
}
