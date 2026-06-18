package pl.serverguard.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import pl.serverguard.ServerGuard;

public class AdminChatListener implements Listener {

    private final ServerGuard plugin;

    public AdminChatListener(ServerGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getAdminGui().isAwaitingInput(player)) return;

        event.setCancelled(true);
        String message = event.getMessage();
        plugin.getServer().getScheduler().runTask(plugin,
            () -> plugin.getAdminGui().handleChatInput(player, message));
    }
}
