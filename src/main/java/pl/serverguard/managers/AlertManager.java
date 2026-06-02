package pl.serverguard.managers;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import pl.serverguard.ServerGuard;

public class AlertManager {

    private final ServerGuard plugin;
    private final String prefix;
    private Sound alertSound;

    public AlertManager(ServerGuard plugin) {
        this.plugin = plugin;
        this.prefix = c(plugin.getConfig().getString("alerts.prefix", "&c[&4SG&c] &e"));
        try {
            alertSound = Sound.valueOf(plugin.getConfig().getString("alerts.sound", "ENTITY_EXPERIENCE_ORB_PICKUP"));
        } catch (IllegalArgumentException e) {
            alertSound = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        }
    }

    public void alert(String msg) {
        if (!plugin.getConfig().getBoolean("alerts.enabled", true)) return;
        String full = prefix + msg;
        // Alerty zawsze na wątku głównym (operacje Bukkit)
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("serverguard.admin")) {
                    p.sendMessage(c(full));
                    p.playSound(p.getLocation(), alertSound, 0.8f, 1f);
                }
            }
            Bukkit.getConsoleSender().sendMessage("[SG ALERT] " + strip(msg));
        });
    }

    public void alertCommand(String name, String cmd, String world, double x, double y, double z) {
        alert("&c⚠ &4" + cmd + " &c→ &f" + name
            + " &8[" + world + " " + f(x) + "," + f(y) + "," + f(z) + "]");
    }

    public void alertBlocked(String name, String cmd) {
        alert("&4✗ BLOK &c" + cmd + " &7przez &4" + name);
    }

    private String f(double d) { return String.format("%.0f", d); }
    private String c(String s) { return s.replace("&", "§"); }
    private String strip(String s) { return s.replaceAll("&[0-9a-fk-or]", ""); }
}
