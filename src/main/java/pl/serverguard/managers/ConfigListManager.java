package pl.serverguard.managers;

import pl.serverguard.ServerGuard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ConfigListManager {

    private final ServerGuard plugin;

    public ConfigListManager(ServerGuard plugin) {
        this.plugin = plugin;
    }

    public List<String> get(String path) {
        return new ArrayList<>(plugin.getConfig().getStringList(path));
    }

    public boolean add(String path, String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        List<String> list = get(path);
        if (list.stream().anyMatch(s -> s.equalsIgnoreCase(normalized))) {
            return false;
        }
        list.add(normalized);
        plugin.getConfig().set(path, list);
        plugin.saveConfig();
        return true;
    }

    public boolean removeAt(String path, int index) {
        List<String> list = get(path);
        if (index < 0 || index >= list.size()) return false;
        list.remove(index);
        plugin.getConfig().set(path, list);
        plugin.saveConfig();
        return true;
    }
}
