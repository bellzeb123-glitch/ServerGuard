package pl.serverguard.listeners;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import pl.serverguard.ServerGuard;
import pl.serverguard.managers.LogEntry;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ContainerListener implements Listener {

    private final ServerGuard plugin;

    /**
     * Snapshot przy otwarciu skrzyni.
     * Zamiast klonować całą tablicę ItemStack[], liczymy tylko ilości
     * per Material - wielokrotnie lżejsze w pamięci.
     */
    private record Snapshot(Block block, Map<Material, Integer> counts) {}

    // UUID gracza → snapshot
    private final Map<UUID, Snapshot> snapshots = new HashMap<>();

    public ContainerListener(ServerGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!plugin.getConfig().getBoolean("containers.enabled", true)) return;
        if (!(event.getPlayer() instanceof Player p)) return;
        if (!(event.getInventory().getHolder() instanceof BlockInventoryHolder h)) return;

        Block block = h.getBlock();
        if (!isMonitored(block.getType().name())) return;

        // Lekki snapshot - tylko liczniki per Material
        snapshots.put(p.getUniqueId(), new Snapshot(block, countItems(event.getInventory())));

        // Loguj otwarcie tylko jeśli changes-only = false
        if (!plugin.getConfig().getBoolean("containers.changes-only", true)) {
            plugin.getDb().log(LogEntry.container(
                p.getUniqueId().toString(), p.getName(), "OTWARCIE",
                block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
                block.getType().name(), null, 0
            ));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player p)) return;
        Snapshot snap = snapshots.remove(p.getUniqueId());
        if (snap == null) return;
        if (!(event.getInventory().getHolder() instanceof BlockInventoryHolder h)) return;
        if (!h.getBlock().equals(snap.block())) return;

        Block block = snap.block();
        Map<Material, Integer> before = snap.counts();
        Map<Material, Integer> after = countItems(event.getInventory());

        // Diff - co zostało wyjęte
        for (Map.Entry<Material, Integer> e : before.entrySet()) {
            int diff = e.getValue() - after.getOrDefault(e.getKey(), 0);
            if (diff > 0) {
                plugin.getDb().log(LogEntry.container(
                    p.getUniqueId().toString(), p.getName(), "WYJĄŁ",
                    block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
                    block.getType().name(), e.getKey().name(), diff
                ));
            }
        }

        // Diff - co zostało włożone
        for (Map.Entry<Material, Integer> e : after.entrySet()) {
            int diff = e.getValue() - before.getOrDefault(e.getKey(), 0);
            if (diff > 0) {
                plugin.getDb().log(LogEntry.container(
                    p.getUniqueId().toString(), p.getName(), "WŁOŻYŁ",
                    block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
                    block.getType().name(), e.getKey().name(), diff
                ));
            }
        }
    }

    /** Liczy ilości przedmiotów per Material. Tanie i lekkie. */
    private Map<Material, Integer> countItems(Inventory inv) {
        Map<Material, Integer> map = new EnumMap<>(Material.class);
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                map.merge(item.getType(), item.getAmount(), Integer::sum);
            }
        }
        return map;
    }

    private boolean isMonitored(String typeName) {
        List<String> list = plugin.getConfig().getStringList("containers.monitored-types");
        return list.stream().anyMatch(t -> t.equalsIgnoreCase(typeName));
    }
}
