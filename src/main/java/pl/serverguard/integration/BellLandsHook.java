package pl.serverguard.integration;

import org.bukkit.plugin.Plugin;
import pl.serverguard.ServerGuard;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/**
 * Opcjonalna integracja z BellLands — szuka najbliższego claima w promieniu X bloków.
 */
public class BellLandsHook {

    public record NearestClaim(UUID ownerUuid, int distanceBlocks, boolean trusted) {}

    private final boolean active;
    private final Object landManager;
    private final Method getLandAt;
    private final Method getOwner;
    private final Method isTrusted;

    public BellLandsHook(ServerGuard plugin) {
        Plugin bellLands = plugin.getServer().getPluginManager().getPlugin("BellLands");
        Object manager = null;
        Method landAt = null;
        Method owner = null;
        Method trusted = null;
        boolean ok = false;

        if (bellLands != null) {
            try {
                manager = bellLands.getClass().getMethod("getLandManager").invoke(bellLands);
                landAt = manager.getClass().getMethod("getLandAt", String.class, int.class, int.class);
                Class<?> landClass = Class.forName("pl.bell.lands.model.Land");
                owner = landClass.getMethod("getOwner");
                trusted = landClass.getMethod("isTrusted", UUID.class);
                ok = true;
            } catch (ReflectiveOperationException e) {
                plugin.getLogger().warning("BellLands wykryty, ale integracja nieudana: " + e.getMessage());
            }
        }

        this.active = ok;
        this.landManager = manager;
        this.getLandAt = landAt;
        this.getOwner = owner;
        this.isTrusted = trusted;

        if (ok) {
            plugin.getLogger().info("Integracja BellLands: logowanie w pobliżu claimów aktywne.");
        }
    }

    public boolean isActive() {
        return active;
    }

    public Optional<NearestClaim> findNearestClaim(String world, int blockX, int blockZ,
                                                   int maxRadius, UUID playerUuid) {
        if (!active || maxRadius <= 0) return Optional.empty();

        int centerChunkX = blockX >> 4;
        int centerChunkZ = blockZ >> 4;
        int chunkRadius = (maxRadius + 15) / 16;

        NearestClaim nearest = null;
        int bestDist = Integer.MAX_VALUE;

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                int chunkX = centerChunkX + dx;
                int chunkZ = centerChunkZ + dz;
                Object land = invokeLandAt(world, chunkX, chunkZ);
                if (land == null) continue;

                int dist = horizontalDistanceToChunk(blockX, blockZ, chunkX, chunkZ);
                if (dist > maxRadius || dist >= bestDist) continue;

                try {
                    UUID ownerUuid = (UUID) getOwner.invoke(land);
                    boolean playerTrusted = (boolean) isTrusted.invoke(land, playerUuid);
                    nearest = new NearestClaim(ownerUuid, dist, playerTrusted);
                    bestDist = dist;
                } catch (ReflectiveOperationException e) {
                    return Optional.empty();
                }
            }
        }

        return Optional.ofNullable(nearest);
    }

    public boolean shouldLogPlayer(UUID playerUuid, NearestClaim claim, boolean skipOwner) {
        if (claim == null) return false;
        if (skipOwner && playerUuid.equals(claim.ownerUuid())) return false;
        return true;
    }

    public String playerRole(UUID playerUuid, NearestClaim claim) {
        if (claim == null) return null;
        if (playerUuid.equals(claim.ownerUuid())) return "OWNER";
        if (claim.trusted()) return "TRUSTED";
        return "STRANGER";
    }

    private Object invokeLandAt(String world, int chunkX, int chunkZ) {
        try {
            Object optional = getLandAt.invoke(landManager, world, chunkX, chunkZ);
            if (optional instanceof Optional<?> opt) {
                return opt.orElse(null);
            }
        } catch (ReflectiveOperationException ignored) {}
        return null;
    }

  private static int horizontalDistanceToChunk(int blockX, int blockZ, int chunkX, int chunkZ) {
        int minX = chunkX << 4;
        int maxX = minX + 15;
        int minZ = chunkZ << 4;
        int maxZ = minZ + 15;
        int nearestX = Math.max(minX, Math.min(blockX, maxX));
        int nearestZ = Math.max(minZ, Math.min(blockZ, maxZ));
        int dx = blockX - nearestX;
        int dz = blockZ - nearestZ;
        return (int) Math.ceil(Math.sqrt((double) dx * dx + (double) dz * dz));
    }
}
