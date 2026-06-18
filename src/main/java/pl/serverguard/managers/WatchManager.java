package pl.serverguard.managers;

import pl.serverguard.ServerGuard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WatchManager {

    private final ServerGuard plugin;
    private List<String> watched = List.of();

    public WatchManager(ServerGuard plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        watched = plugin.getConfig().getStringList("watch.players").stream()
            .map(s -> s.toLowerCase(Locale.ROOT))
            .toList();
    }

    public boolean isWatched(String playerName) {
        return watched.contains(playerName.toLowerCase(Locale.ROOT));
    }

    public List<String> getWatched() {
        return watched;
    }

    public void toggle(String playerName) {
        String key = playerName.toLowerCase(Locale.ROOT);
        List<String> updated = new ArrayList<>(plugin.getConfig().getStringList("watch.players"));
        boolean removed = updated.removeIf(s -> s.equalsIgnoreCase(playerName));
        if (!removed) {
            updated.add(playerName);
        }
        plugin.getConfig().set("watch.players", updated);
        plugin.saveConfig();
        reload();
    }
}
