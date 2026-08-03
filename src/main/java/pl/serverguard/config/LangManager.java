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
    private FileConfiguration jarDefaults;
    private File langFile;
    private String code = "pl";

    public LangManager(ServerGuard plugin) {
        this.plugin = plugin;
    }

    public void load() {
        try {
            code = plugin.getConfig().getString("language", "pl").toLowerCase(Locale.ROOT);
            if (!code.equals("pl") && !code.equals("en")) code = "pl";

            File folder = new File(plugin.getDataFolder(), "lang");
            folder.mkdirs();
            langFile = new File(folder, code + ".yml");
            if (!langFile.exists()) {
                plugin.saveResource("lang/" + code + ".yml", false);
            }

            try (InputStreamReader reader = new InputStreamReader(
                    new java.io.FileInputStream(langFile), StandardCharsets.UTF_8)) {
                lang = YamlConfiguration.loadConfiguration(reader);
            }

            jarDefaults = null;
            InputStream stream = plugin.getResource("lang/" + code + ".yml");
            if (stream != null) {
                try (InputStreamReader defReader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    jarDefaults = YamlConfiguration.loadConfiguration(defReader);
                }
                lang.setDefaults(jarDefaults);
                lang.options().copyDefaults(true);
                // Stary plik na dysku nie dostaje nowych kluczy sam z siebie — dopisz i zapisz.
                if (mergeMissingKeys(lang, jarDefaults)) {
                    lang.save(langFile);
                    plugin.getLogger().info("Language file updated with missing keys: lang/" + code + ".yml");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load language: " + e.getMessage());
            if (lang == null) lang = new YamlConfiguration();
        }
    }

    /** Kopiuje brakujące ścieżki z JAR do runtime-config. Zwraca true jeśli coś dopisano. */
    private static boolean mergeMissingKeys(FileConfiguration target, FileConfiguration defaults) {
        if (defaults == null) return false;
        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) continue;
            if (!target.isSet(key)) {
                target.set(key, defaults.get(key));
                changed = true;
            }
        }
        return changed;
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
        String value = lang.getString(key);
        if (value != null && !value.isEmpty()) return value;
        if (jarDefaults != null) {
            String fromJar = jarDefaults.getString(key);
            if (fromJar != null && !fromJar.isEmpty()) return fromJar;
        }
        return key;
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
        if (dbAction.equals(raw("db.action-place"))) return tr("db.action-place-label");
        if (dbAction.equals(raw("db.action-kill"))) return tr("db.action-kill-label");
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
            case "zwierzeta", "zwierzęta", "encje", "entities", "entity", "animals" -> raw("db.type-entities");
            default -> null;
        };
    }

    public String historyTypeLabel(String canonicalType) {
        if (canonicalType.equals(raw("db.type-commands"))) return tr("gui.logs.types.commands");
        if (canonicalType.equals(raw("db.type-containers"))) return tr("gui.logs.types.containers");
        if (canonicalType.equals(raw("db.type-blocks"))) return tr("gui.logs.types.blocks");
        if (canonicalType.equals(raw("db.type-entities"))) return tr("gui.logs.types.entities");
        return canonicalType;
    }
}
