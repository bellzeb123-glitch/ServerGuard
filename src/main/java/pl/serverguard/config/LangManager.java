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
}
