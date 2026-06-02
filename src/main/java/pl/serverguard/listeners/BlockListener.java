package pl.serverguard.listeners;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import pl.serverguard.ServerGuard;
import pl.serverguard.managers.LogEntry;

import java.util.List;

public class BlockListener implements Listener {

    private final ServerGuard plugin;

    public BlockListener(ServerGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("blocks.log-break", true)) return;
        handle(event.getPlayer(), event.getBlock(), "ZNISZCZYŁ");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!plugin.getConfig().getBoolean("blocks.log-place", true)) return;
        handle(event.getPlayer(), event.getBlock(), "POSTAWIŁ");
    }

    private void handle(Player p, Block b, String action) {
        if (!shouldLog(b.getType().name())) return;
        plugin.getDb().log(LogEntry.block(
            p.getUniqueId().toString(), p.getName(), action,
            b.getWorld().getName(), b.getX(), b.getY(), b.getZ(),
            b.getType().name()
        ));
    }

    private boolean shouldLog(String typeName) {
        if (plugin.getConfig().getBoolean("blocks.log-all-blocks", false)) return true;
        return plugin.getConfig().getStringList("blocks.always-log")
            .stream().anyMatch(t -> t.equalsIgnoreCase(typeName));
    }
}
