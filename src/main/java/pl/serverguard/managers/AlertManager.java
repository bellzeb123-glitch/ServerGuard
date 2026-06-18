package pl.serverguard.managers;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import pl.serverguard.ServerGuard;

public class AlertManager {

    private final ServerGuard plugin;
    private final AlertCooldown cooldown = new AlertCooldown();
    private String prefix;
    private Sound alertSound;

    public AlertManager(ServerGuard plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        prefix = c(plugin.getConfig().getString("alerts.prefix", "&c[&4SG&c] &e"));
        cooldown.setCooldownSeconds(plugin.getConfig().getInt("alerts.cooldown-seconds", 30));
        try {
            alertSound = Sound.valueOf(plugin.getConfig().getString("alerts.sound", "ENTITY_EXPERIENCE_ORB_PICKUP"));
        } catch (IllegalArgumentException e) {
            alertSound = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        }
    }

    public void alert(String msg) {
        notifyThrottled("general", msg, "serverguard.admin");
    }

    public void notifyAudit(String msg) {
        String perm = plugin.getAdminAudit().getNotifyPermission();
        notifyThrottled("audit", msg, perm, "serverguard.admin");
    }

    public void alertModeration(String cooldownKey, String msg) {
        notifyThrottled(cooldownKey, msg, "serverguard.notify", "serverguard.admin");
    }

    private void notifyThrottled(String cooldownKey, String msg, String... permissions) {
        if (!plugin.getConfig().getBoolean("alerts.enabled", true)) return;
        if (!cooldown.shouldNotify(cooldownKey)) return;

        int suppressed = cooldown.markSent(cooldownKey);
        final String alertMsg = suppressed > 0
            ? msg + " &8(+" + suppressed + " podobnych)"
            : msg;

        final String full = prefix + alertMsg;
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (hasAnyPermission(p, permissions)) {
                    p.sendMessage(c(full));
                    p.playSound(p.getLocation(), alertSound, 0.8f, 1f);
                }
            }
            Bukkit.getConsoleSender().sendMessage("[SG ALERT] " + strip(alertMsg));
        });
    }

    private boolean hasAnyPermission(Player player, String... permissions) {
        for (String perm : permissions) {
            if (player.hasPermission(perm)) return true;
        }
        return false;
    }

    public void alertCommand(String name, String cmd, String world, double x, double y, double z) {
        alert("&c⚠ &4" + cmd + " &c→ &f" + name
            + " &8[" + world + " " + f(x) + "," + f(y) + "," + f(z) + "]");
    }

    public void alertBlocked(String name, String cmd) {
        alert("&4✗ BLOK &c" + cmd + " &7przez &4" + name);
    }

    public void alertUnauthorized(String name, String cmd, String requiredPerm,
                                  String world, double x, double y, double z,
                                  boolean blocked) {
        String action = blocked ? "&4✗ PRÓBA" : "&6⚠ PRÓBA";
        String key = "audit:" + name + ":" + cmd.split(" ")[0].toLowerCase();
        notifyThrottled(key,
            action + " &c" + cmd + " &7→ &f" + name
                + " &8(brak: &7" + requiredPerm + "&8)"
                + " &8[" + world + " " + f(x) + "," + f(y) + "," + f(z) + "]",
            plugin.getAdminAudit().getNotifyPermission(), "serverguard.admin");
    }

    private String f(double d) { return String.format("%.0f", d); }
    private String c(String s) { return s.replace("&", "§"); }
    private String strip(String s) { return s.replaceAll("&[0-9a-fk-or]", ""); }
}
