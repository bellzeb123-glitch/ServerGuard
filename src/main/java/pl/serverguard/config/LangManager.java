package pl.serverguard.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.serverguard.ServerGuard;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class LangManager {

    private final ServerGuard plugin;
    private FileConfiguration lang;
    private String code = "pl";

    public LangManager(ServerGuard plugin) {
        this.plugin = plugin;
    }

    public void load() {
        code = plugin.getConfig().getString("language", "pl").toLowerCase(Locale.ROOT);
        if (!code.equals("pl") && !code.equals("en")) code = "pl";

        File folder = new File(plugin.getDataFolder(), "lang");
        folder.mkdirs();
        File file = new File(folder, code + ".yml");
        if (!file.exists()) {
            plugin.saveResource("lang/" + code + ".yml", false);
        }

        lang = YamlConfiguration.loadConfiguration(file);
        InputStream stream = plugin.getResource("lang/" + code + ".yml");
        if (stream != null) {
            lang.setDefaults(YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8)));
            lang.options().copyDefaults(true);
        }
    }

    public String code() {
        return code;
    }

    public void toggleLanguage() {
        setLanguage(code.equals("pl") ? "en" : "pl");
    }

    public void setLanguage(String newCode) {
        plugin.getConfig().set("language", newCode);
        plugin.saveConfig();
        load();
    }

    public String raw(String key) {
        return lang.getString(key, key);
    }

    public String tr(String key, Object... pairs) {
        String text = raw(key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            text = text.replace("{" + pairs[i] + "}", String.valueOf(pairs[i + 1]));
        }
        return color(text);
    }

    public String color(String text) {
        return text.replace('&', '§');
    }

    public boolean isCancel(String message) {
        String m = message.trim().toLowerCase(Locale.ROOT);
        return m.equals("anuluj") || m.equals("cancel");
    }

    /** Translates a DB-stored action code to the current language label. */
    public String actionLabel(String dbAction) {
        if (dbAction == null) return "";
        if (dbAction.equals(raw("db.action-take"))) return tr("db.action-take-label");
        if (dbAction.equals(raw("db.action-put"))) return tr("db.action-put-label");
        if (dbAction.equals(raw("db.action-destroy"))) return tr("db.action-destroy-label");
        return dbAction;
    }

    /** Resolves history query type from PL or EN aliases. Returns null if unknown. */
    public String resolveHistoryType(String input) {
        if (input == null) return raw("db.type-commands");
        String t = input.toLowerCase(Locale.ROOT);
        return switch (t) {
            case "komendy", "commands", "command" -> raw("db.type-commands");
            case "skrzynie", "containers", "container", "chests" -> raw("db.type-containers");
            case "bloki", "blocks", "block" -> raw("db.type-blocks");
            default -> null;
        };
    }

    public String historyTypeLabel(String canonicalType) {
        if (canonicalType.equals(raw("db.type-commands"))) return tr("gui.logs.types.commands");
        if (canonicalType.equals(raw("db.type-containers"))) return tr("gui.logs.types.containers");
        if (canonicalType.equals(raw("db.type-blocks"))) return tr("gui.logs.types.blocks");
        return canonicalType;
    }
}
