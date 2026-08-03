package pl.serverguard.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pl.serverguard.ServerGuard;
import pl.serverguard.gui.InvseeHolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class SGCommand implements CommandExecutor, TabCompleter {

    private final ServerGuard plugin;

    public SGCommand(ServerGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        var lang = plugin.getLang();
        if (!sender.hasPermission("serverguard.admin")
                && !(args.length > 0 && args[0].equalsIgnoreCase("invsee")
                && sender.hasPermission("serverguard.invsee"))) {
            sender.sendMessage(lang.tr("commands.no-permission"));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("serverguard.admin")) {
                sender.sendMessage(lang.tr("commands.no-permission"));
                return true;
            }
            if (plugin.reloadAll()) {
                sender.sendMessage(lang.tr("commands.reload-ok"));
            } else {
                sender.sendMessage("§cServerGuard reload failed — see console.");
            }
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("invsee")) {
            return handleInvsee(sender, args);
        }

        if (sender instanceof Player player) {
            if (!sender.hasPermission("serverguard.admin")) {
                sender.sendMessage(lang.tr("commands.no-permission"));
                return true;
            }
            if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
                plugin.getAdminGui().openMain(player);
                return true;
            }
        }

        sendHelp(sender);
        return true;
    }

    private boolean handleInvsee(CommandSender sender, String[] args) {
        var lang = plugin.getLang();
        if (!(sender instanceof Player admin)) {
            sender.sendMessage(lang.tr("commands.invsee-players-only"));
            return true;
        }
        if (!admin.hasPermission("serverguard.invsee") && !admin.hasPermission("serverguard.admin")) {
            admin.sendMessage(lang.tr("commands.invsee-no-perm"));
            return true;
        }
        if (args.length < 2) {
            admin.sendMessage(lang.tr("commands.invsee-usage"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || !target.isOnline()) {
            admin.sendMessage(lang.tr("commands.invsee-offline", "player", args[1]));
            return true;
        }
        InvseeHolder.Mode mode = InvseeHolder.Mode.INVENTORY;
        if (args.length >= 3 && (args[2].equalsIgnoreCase("ender")
                || args[2].equalsIgnoreCase("ec")
                || args[2].equalsIgnoreCase("enderchest"))) {
            mode = InvseeHolder.Mode.ENDER;
        }
        plugin.getInvsee().open(admin, target, mode);
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
        sender.sendMessage(lang.tr("commands.help-line-invsee"));
        sender.sendMessage(lang.tr("commands.help-line-history"));
        sender.sendMessage(lang.tr("commands.help-line-search"));
        sender.sendMessage(lang.tr("commands.help-footer"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("serverguard.admin") && !sender.hasPermission("serverguard.invsee")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> base = new ArrayList<>(Arrays.asList("gui", "reload", "help", "invsee"));
            String p = args[0].toLowerCase(Locale.ROOT);
            return base.stream().filter(s -> s.startsWith(p)).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("invsee")) {
            String p = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(p))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("invsee")) {
            String p = args[2].toLowerCase(Locale.ROOT);
            return Arrays.asList("inventory", "ender").stream()
                    .filter(s -> s.startsWith(p))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
