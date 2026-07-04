package pl.serverguard.integration;

import pl.bell.hub.api.*;
import pl.serverguard.ServerGuard;
import pl.serverguard.managers.AdminAuditManager;
import pl.serverguard.managers.DatabaseManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BellHubModule implements BellModule {

    private final ServerGuard plugin;

    public BellHubModule(ServerGuard plugin) {
        this.plugin = plugin;
    }

    @Override public String id() { return "serverguard"; }
    @Override public String displayName() { return "ServerGuard"; }
    @Override public String icon() { return "shield-search"; }

    @Override
    public List<Stat> dashboard() {
        DatabaseManager db = plugin.getDb();
        int[] c = db.getPlayerCounts("%");
        String lang = plugin.getConfig().getString("language", "pl");
        return List.of(
            Stat.of("Komendy", c[0], "cyan"),
            Stat.of("Kontenery", c[1], "violet"),
            Stat.of("Bloki", c[2], "gold"),
            Stat.of("Sesje", c[3], "green"),
            Stat.of("Encje", c[4], "rose"),
            new Stat("Język", lang.toUpperCase(), "silver")
        );
    }

    @Override
    public List<ActionDef> actions() {
        return List.of(
            ActionDef.of("audit.toggle", "Włącz/Wyłącz audyt", "Audyt",
                ActionField.bool("enabled", "Włączony")),
            ActionDef.of("audit.addRule", "Dodaj regułę", "Audyt",
                ActionField.text("pattern", "Wzorzec komendy"),
                ActionField.text("permission", "Uprawnienie"),
                ActionField.bool("block", "Blokuj")),
            ActionDef.destructive("audit.removeRule", "Usuń regułę", "Audyt",
                ActionField.number("index", "Index reguły")),
            ActionDef.destructive("purge", "Wyczyść stare logi", "Dane",
                ActionField.number("days", "Starsze niż (dni)"))
        );
    }

    @Override
    public ActionResult invoke(HubAction action, Actor actor) {
        switch (action.name()) {
            case "audit.toggle" -> {
                boolean val = "true".equals(action.param("enabled"));
                plugin.getAdminAudit().setEnabled(val);
                return ActionResult.ok(val ? "Audyt włączony." : "Audyt wyłączony.");
            }
            case "audit.addRule" -> {
                String pattern = action.param("pattern", "");
                String permission = action.param("permission", "");
                if (pattern.isEmpty() || permission.isEmpty())
                    return ActionResult.error("Wzorzec i uprawnienie są wymagane.");
                boolean block = "true".equals(action.param("block"));
                plugin.getAdminAudit().addRule(pattern, permission, block);
                return ActionResult.ok("Reguła dodana: " + pattern);
            }
            case "audit.removeRule" -> {
                int idx;
                try { idx = Integer.parseInt(action.param("index", "-1")); }
                catch (NumberFormatException e) { return ActionResult.error("Nieprawidłowy index."); }
                plugin.getAdminAudit().removeRule(idx);
                return ActionResult.ok("Reguła usunięta.");
            }
            case "purge" -> {
                int days;
                try { days = Integer.parseInt(action.param("days", "0")); }
                catch (NumberFormatException e) { return ActionResult.error("Nieprawidłowa liczba dni."); }
                if (days <= 0) return ActionResult.error("Podaj liczbę dni > 0.");
                int removed = plugin.getDb().purgeOldEntries(days);
                return ActionResult.ok("Usunięto " + removed + " wpisów starszych niż " + days + " dni.");
            }
            default -> { return ActionResult.error("Nieznana akcja: " + action.name()); }
        }
    }

    @Override
    public String view(String viewId, Map<String, String> params) {
        return switch (viewId) {
            case "player" -> viewPlayer(params.getOrDefault("player", ""));
            case "search" -> viewSearch(params.getOrDefault("q", ""), intParam(params, "limit", 50));
            case "audit" -> viewAudit();
            case "recent" -> viewRecent();
            case "overview" -> viewOverview(intParam(params, "limit", 30));
            default -> "{}";
        };
    }

    private String viewPlayer(String name) {
        if (name.isEmpty()) return "{\"found\":false}";
        DatabaseManager db = plugin.getDb();
        int[] counts = db.getPlayerCounts(name);
        if (counts[0] + counts[1] + counts[2] + counts[3] + counts[4] == 0)
            return "{\"found\":false}";

        StringBuilder sb = new StringBuilder(2048);
        sb.append("{\"found\":true,\"player\":").append(q(name));
        sb.append(",\"counts\":{\"commands\":").append(counts[0])
          .append(",\"containers\":").append(counts[1])
          .append(",\"blocks\":").append(counts[2])
          .append(",\"sessions\":").append(counts[3])
          .append(",\"entities\":").append(counts[4]).append("}");

        sb.append(",\"commands\":").append(rowsToJson(db.getCommands(name, 50),
            new String[]{"ts","name","world","x","y","z","cmd","alert"}));
        sb.append(",\"containers\":").append(rowsToJson(db.getContainers(name, 50),
            new String[]{"ts","name","action","world","x","y","z","ctype","item","amount"}));
        sb.append(",\"blocks\":").append(rowsToJson(db.getBlocks(name, 50),
            new String[]{"ts","name","action","world","x","y","z","btype","claim_dist","player_role"}));
        sb.append(",\"entities\":").append(rowsToJson(db.getEntities(name, 50),
            new String[]{"ts","name","action","world","x","y","z","etype","claim_dist","player_role"}));
        sb.append(",\"sessions\":").append(rowsToJson(db.getSessions(name, 50),
            new String[]{"ts","name","action","ip","world","x","y","z"}));
        sb.append("}");
        return sb.toString();
    }

    private String viewSearch(String phrase, int limit) {
        if (phrase.isEmpty()) return "{\"results\":[]}";
        List<String[]> rows = plugin.getDb().searchAll(phrase, Math.min(limit, 200));
        StringBuilder sb = new StringBuilder(1024);
        sb.append("{\"results\":");
        sb.append(rowsToJson(rows, new String[]{"ts","name","type","detail"}));
        sb.append("}");
        return sb.toString();
    }

    private String viewAudit() {
        AdminAuditManager am = plugin.getAdminAudit();
        StringBuilder sb = new StringBuilder(512);
        sb.append("{\"enabled\":").append(am.isEnabled());
        sb.append(",\"rules\":[");
        List<AdminAuditManager.AuditRule> rules = am.getRules();
        for (int i = 0; i < rules.size(); i++) {
            if (i > 0) sb.append(",");
            AdminAuditManager.AuditRule r = rules.get(i);
            sb.append("{\"pattern\":").append(q(r.pattern()))
              .append(",\"permission\":").append(q(r.permission()))
              .append(",\"block\":").append(r.block()).append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String viewRecent() {
        List<String> names = plugin.getDb().getRecentPlayerNames(20);
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"players\":[");
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(q(names.get(i)));
        }
        sb.append("]}");
        return sb.toString();
    }

    private String viewOverview(int limit) {
        DatabaseManager db = plugin.getDb();
        int cap = Math.min(limit, 100);
        StringBuilder sb = new StringBuilder(4096);
        sb.append("{\"commands\":").append(rowsToJson(db.getCommands("%", cap),
                new String[]{"ts","name","world","x","y","z","cmd","alert"}));
        sb.append(",\"containers\":").append(rowsToJson(db.getContainers("%", cap),
                new String[]{"ts","name","action","world","x","y","z","ctype","item","amount"}));
        sb.append(",\"blocks\":").append(rowsToJson(db.getBlocks("%", cap),
                new String[]{"ts","name","action","world","x","y","z","btype","claim_dist","player_role"}));
        sb.append(",\"entities\":").append(rowsToJson(db.getEntities("%", cap),
                new String[]{"ts","name","action","world","x","y","z","etype","claim_dist","player_role"}));
        sb.append(",\"sessions\":").append(rowsToJson(db.getSessions("%", cap),
                new String[]{"ts","name","action","ip","world","x","y","z"}));
        sb.append("}");
        return sb.toString();
    }

    private String rowsToJson(List<String[]> rows, String[] keys) {
        StringBuilder sb = new StringBuilder(rows.size() * 128);
        sb.append("[");
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{");
            String[] r = rows.get(i);
            for (int j = 0; j < keys.length && j < r.length; j++) {
                if (j > 0) sb.append(",");
                sb.append("\"").append(keys[j]).append("\":").append(q(r[j]));
            }
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String q(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private static int intParam(Map<String, String> p, String key, int def) {
        String v = p.get(key);
        if (v == null) return def;
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return def; }
    }
}
