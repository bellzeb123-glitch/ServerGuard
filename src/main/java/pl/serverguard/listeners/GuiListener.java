package pl.serverguard.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import pl.serverguard.ServerGuard;
import pl.serverguard.gui.GuiHolder;

public class GuiListener implements Listener {

    private final ServerGuard plugin;

    public GuiListener(ServerGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof GuiHolder gui)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player admin)) return;
        if (!admin.hasPermission("serverguard.admin")) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        plugin.getAdminGui().handleClick(admin, gui.state(), event.getSlot(), event.isShiftClick());
    }
}
