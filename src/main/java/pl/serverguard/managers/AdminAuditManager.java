package pl.serverguard.managers;

import org.bukkit.entity.Player;
import pl.serverguard.ServerGuard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Wykrywa próby użycia komend admina (vanilla + Bell) bez wymaganego uprawnienia.
 */
public class AdminAuditManager {

    public record AuditRule(String pattern, String permission, boolean block) {}

    public record AuditResult(boolean violation, String permission, boolean block, String pattern) {
        public static AuditResult ok() {
            return new AuditResult(false, null, false, null);
        }
    }

    private final ServerGuard plugin;
    private boolean enabled;
    private String notifyPermission;
    private String bypassPermission;
    private boolean defaultBlock;
    private String playerMessage;
    private List<AuditRule> rules = List.of();

    public AdminAuditManager(ServerGuard plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        var cfg = plugin.getConfig();
        enabled = cfg.getBoolean("admin-audit.enabled", true);
        notifyPermission = cfg.getString("admin-audit.notify-permission", "serverguard.notify");
        bypassPermission = cfg.getString("admin-audit.bypass-permission", "serverguard.audit.bypass");
        defaultBlock = cfg.getBoolean("admin-audit.block-unauthorized", true);
        playerMessage = cfg.getString("admin-audit.player-message",
            "&c[ServerGuard] &eTa komenda jest zablokowana.");

        List<AuditRule> loaded = new ArrayList<>();
        try {
            for (var entry : cfg.getMapList("admin-audit.rules")) {
                if (entry == null) continue;
                String pattern = str(entry.get("pattern"));
                String permission = str(entry.get("permission"));
                if (pattern.isEmpty() || permission.isEmpty()) continue;

                boolean block = entry.containsKey("block")
                    ? Boolean.TRUE.equals(entry.get("block"))
                    : defaultBlock;
                loaded.add(new AuditRule(pattern.toLowerCase(), permission, block));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("admin-audit.rules invalid — keeping empty rules: " + e.getMessage());
        }

        loaded.sort(Comparator.comparingInt((AuditRule r) -> r.pattern().length()).reversed());
        rules = List.copyOf(loaded);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getNotifyPermission() {
        return notifyPermission;
    }

    public String getPlayerMessage() {
        return playerMessage;
    }

    public List<AuditRule> getRules() {
        return rules;
    }

    public void setEnabled(boolean value) {
        plugin.getConfig().set("admin-audit.enabled", value);
        plugin.saveConfig();
        reload();
    }

    public void addRule(String pattern, String permission, boolean block) {
        List<AuditRule> updated = new ArrayList<>(rules);
        updated.add(new AuditRule(pattern.toLowerCase(), permission, block));
        persistRules(updated);
        reload();
    }

    public void removeRule(int index) {
        if (index < 0 || index >= rules.size()) return;
        List<AuditRule> updated = new ArrayList<>(rules);
        updated.remove(index);
        persistRules(updated);
        reload();
    }

    private void persistRules(List<AuditRule> updated) {
        List<java.util.Map<String, Object>> maps = new ArrayList<>();
        for (AuditRule rule : updated) {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("pattern", rule.pattern());
            map.put("permission", rule.permission());
            map.put("block", rule.block());
            maps.add(map);
        }
        plugin.getConfig().set("admin-audit.rules", maps);
        plugin.saveConfig();
    }

    public AuditResult check(Player player, String fullCommand) {
        if (!enabled || player.hasPermission(bypassPermission)) {
            return AuditResult.ok();
        }

        String cmd = normalize(fullCommand);
        for (AuditRule rule : rules) {
            if (!matches(cmd, rule.pattern())) continue;
            if (player.hasPermission(rule.permission())) {
                return AuditResult.ok();
            }
            return new AuditResult(true, rule.permission(), rule.block(), rule.pattern());
        }
        return AuditResult.ok();
    }

    static String normalize(String fullCommand) {
        String cmd = fullCommand.startsWith("/") ? fullCommand.substring(1) : fullCommand;
        return cmd.toLowerCase().trim();
    }

    static boolean matches(String cmd, String pattern) {
        if (pattern.contains(" ")) {
            return cmd.equals(pattern) || cmd.startsWith(pattern + " ");
        }
        int space = cmd.indexOf(' ');
        String root = space == -1 ? cmd : cmd.substring(0, space);
        return root.equals(pattern);
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
