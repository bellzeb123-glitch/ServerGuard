package pl.serverguard.listeners;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import pl.serverguard.ServerGuard;
import pl.serverguard.integration.BellLandsHook;
import pl.serverguard.managers.LogEntry;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public class EntityListener implements Listener {

    private static final Set<String> DESTROY_ACTION_TYPES = Set.of(
        "ARMOR_STAND", "ITEM_FRAME", "GLOW_ITEM_FRAME", "PAINTING"
    );

    private final ServerGuard plugin;
    private final BellLandsHook claims;

    public EntityListener(ServerGuard plugin, BellLandsHook claims) {
        this.plugin = plugin;
        this.claims = claims;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (!plugin.getConfig().getBoolean("entities.enabled", true)) return;

        LivingEntity entity = event.getEntity();
        Player actor = entity.getKiller();
        if (actor == null) return;

        handle(entity, actor);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (!plugin.getConfig().getBoolean("entities.enabled", true)) return;

        Player actor = resolveActor(event.getRemover());
        if (actor == null) return;

        handle(event.getEntity(), actor);
    }

    private void handle(Entity entity, Player actor) {
        if (actor.hasPermission("serverguard.bypass")) return;

        String typeName = entity.getType().name();
        if (!isMonitored(typeName)) return;

        BellLandsHook.NearestClaim nearest = null;
        if (plugin.getConfig().getBoolean("entities.near-claim.enabled", true) && claims.isActive()) {
            Location loc = entity.getLocation();
            int radius = plugin.getConfig().getInt("entities.near-claim.radius", 128);
            Optional<BellLandsHook.NearestClaim> found = claims.findNearestClaim(
                loc.getWorld().getName(), loc.getBlockX(), loc.getBlockZ(), radius, actor.getUniqueId());
            if (found.isEmpty()) return;
            nearest = found.get();
            boolean skipOwner = plugin.getConfig().getBoolean("entities.near-claim.skip-owner", true);
            if (!claims.shouldLogPlayer(actor.getUniqueId(), nearest, skipOwner)) return;
        }

        String action = DESTROY_ACTION_TYPES.contains(typeName)
            ? plugin.getLang().raw("db.action-destroy")
            : plugin.getLang().raw("db.action-kill");

        String role = nearest != null ? claims.playerRole(actor.getUniqueId(), nearest) : null;
        String claimOwner = nearest != null ? nearest.ownerUuid().toString() : null;
        Integer claimDist = nearest != null ? nearest.distanceBlocks() : null;
        Location loc = entity.getLocation();

        plugin.getDb().log(LogEntry.entity(
            actor.getUniqueId().toString(), actor.getName(), action,
            loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
            typeName.toLowerCase(Locale.ROOT),
            claimOwner, claimDist, role
        ));
    }

    private boolean isMonitored(String typeName) {
        List<String> monitored = plugin.getConfig().getStringList("entities.monitored-types");
        if (monitored.isEmpty()) return true;
        return monitored.stream().anyMatch(t -> t.equalsIgnoreCase(typeName));
    }

    private static Player resolveActor(Entity remover) {
        if (remover instanceof Player player) return player;
        if (remover instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
