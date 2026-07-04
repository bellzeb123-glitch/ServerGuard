package pl.serverguard.listeners;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import pl.serverguard.ServerGuard;
import pl.serverguard.integration.BellLandsHook;
import pl.serverguard.managers.LogEntry;

import java.util.Optional;

public class BlockListener implements Listener {

    private final ServerGuard plugin;
    private final BellLandsHook claims;

    public BlockListener(ServerGuard plugin, BellLandsHook claims) {
        this.plugin = plugin;
        this.claims = claims;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("blocks.log-break", true)) return;
        handle(event.getPlayer(), event.getBlock(), plugin.getLang().raw("db.action-destroy"));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!plugin.getConfig().getBoolean("blocks.log-place", true)) return;
        handle(event.getPlayer(), event.getBlock(), plugin.getLang().raw("db.action-place"));
    }

    private void handle(Player player, Block block, String action) {
        if (player.hasPermission("serverguard.bypass")) return;

        String typeName = block.getType().name();
        if (!shouldLog(player, block, typeName)) return;

        BellLandsHook.NearestClaim nearest = resolveNearestClaim(player, block);
        String role = nearest != null ? claims.playerRole(player.getUniqueId(), nearest) : null;
        String claimOwner = nearest != null ? nearest.ownerUuid().toString() : null;
        Integer claimDist = nearest != null ? nearest.distanceBlocks() : null;

        plugin.getDb().log(LogEntry.block(
            player.getUniqueId().toString(), player.getName(), action,
            block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
            typeName, claimOwner, claimDist, role
        ));
    }

    private boolean shouldLog(Player player, Block block, String typeName) {
        if (plugin.getConfig().getBoolean("blocks.log-all-blocks", false)) return true;
        if (isAlwaysLogged(typeName)) return true;

        if (!plugin.getConfig().getBoolean("blocks.near-claim.enabled", true) || !claims.isActive()) {
            return false;
        }

        int radius = plugin.getConfig().getInt("blocks.near-claim.radius", 128);
        Optional<BellLandsHook.NearestClaim> found = claims.findNearestClaim(
            block.getWorld().getName(), block.getX(), block.getZ(), radius, player.getUniqueId());
        if (found.isEmpty()) return false;

        boolean skipOwner = plugin.getConfig().getBoolean("blocks.near-claim.skip-owner", true);
        return claims.shouldLogPlayer(player.getUniqueId(), found.get(), skipOwner);
    }

    private BellLandsHook.NearestClaim resolveNearestClaim(Player player, Block block) {
        if (!claims.isActive()) return null;
        if (!plugin.getConfig().getBoolean("blocks.near-claim.enabled", true)) return null;

        int radius = plugin.getConfig().getInt("blocks.near-claim.radius", 128);
        return claims.findNearestClaim(
            block.getWorld().getName(), block.getX(), block.getZ(), radius, player.getUniqueId())
            .orElse(null);
    }

    private boolean isAlwaysLogged(String typeName) {
        return plugin.getConfig().getStringList("blocks.always-log")
            .stream().anyMatch(t -> t.equalsIgnoreCase(typeName));
    }
}
