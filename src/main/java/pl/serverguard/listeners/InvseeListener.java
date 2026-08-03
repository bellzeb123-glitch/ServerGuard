package pl.serverguard.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import pl.serverguard.ServerGuard;
import pl.serverguard.gui.InvseeHolder;
import pl.serverguard.gui.InvseeService;

public class InvseeListener implements Listener {

    private final ServerGuard plugin;

    public InvseeListener(ServerGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof InvseeHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player admin)) return;

        InvseeService svc = plugin.getInvsee();
        Inventory top = event.getView().getTopInventory();
        int raw = event.getRawSlot();
        boolean topClick = raw < top.getSize();

        // Control bar
        if (topClick && svc.isControlSlot(raw)) {
            event.setCancelled(true);
            if (raw == InvseeService.SLOT_REFRESH) {
                svc.refresh(admin, holder);
            } else if (raw == InvseeService.SLOT_TOGGLE) {
                svc.toggleMode(admin, holder);
            } else if (raw == InvseeService.SLOT_CLOSE) {
                admin.closeInventory();
            }
            return;
        }

        // Ender: only slots 0-26 are content; 27-44 are panes
        if (holder.mode() == InvseeHolder.Mode.ENDER && topClick && raw >= 27 && raw < 45) {
            event.setCancelled(true);
            return;
        }

        // Armor spacer panes
        if (holder.mode() == InvseeHolder.Mode.INVENTORY && topClick && raw >= 41 && raw < 45) {
            event.setCancelled(true);
            return;
        }

        if (!holder.editable()) {
            event.setCancelled(true);
            return;
        }

        // Editable: after Bukkit applies the click, mirror into target
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!admin.isOnline()) return;
            if (!(admin.getOpenInventory().getTopInventory().getHolder() instanceof InvseeHolder h)
                    || !h.targetId().equals(holder.targetId())) return;
            Player target = org.bukkit.Bukkit.getPlayer(holder.targetId());
            if (target == null || !target.isOnline()) return;

            if (holder.mode() == InvseeHolder.Mode.ENDER) {
                for (int i = 0; i < 27; i++) {
                    svc.applyGuiSlotToTarget(target, holder, i, top.getItem(i));
                }
            } else if (topClick) {
                if (raw <= 35 || svc.isArmorOrOffhand(raw)) {
                    svc.applyGuiSlotToTarget(target, holder, raw, top.getItem(raw));
                }
            } else {
                // Shift-click from admin inv into top — refresh whole mapped area
                for (int i = 0; i <= 40; i++) {
                    if (i <= 35 || svc.isArmorOrOffhand(i)) {
                        svc.applyGuiSlotToTarget(target, holder, i, top.getItem(i));
                    }
                }
            }
            // Collect number keys / hotbar swaps etc.
            if (event.getAction() == InventoryAction.HOTBAR_SWAP
                    || event.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD) {
                for (int i = 0; i <= 40; i++) {
                    if (holder.mode() == InvseeHolder.Mode.ENDER && i >= 27) continue;
                    if (i <= 35 || svc.isArmorOrOffhand(i) || holder.mode() == InvseeHolder.Mode.ENDER) {
                        if (holder.mode() == InvseeHolder.Mode.ENDER && i >= 27) continue;
                        svc.applyGuiSlotToTarget(target, holder, i, top.getItem(i));
                    }
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof InvseeHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player admin)) return;

        InvseeService svc = plugin.getInvsee();
        boolean touchesTop = event.getRawSlots().stream().anyMatch(s -> s < event.getView().getTopInventory().getSize());
        if (!touchesTop) return;

        if (!holder.editable()) {
            event.setCancelled(true);
            return;
        }

        // Block dragging onto control / pane slots
        for (int raw : event.getRawSlots()) {
            if (raw >= event.getView().getTopInventory().getSize()) continue;
            if (svc.isControlSlot(raw)) {
                event.setCancelled(true);
                return;
            }
            if (holder.mode() == InvseeHolder.Mode.ENDER && raw >= 27) {
                event.setCancelled(true);
                return;
            }
            if (holder.mode() == InvseeHolder.Mode.INVENTORY && raw >= 41 && raw < 45) {
                event.setCancelled(true);
                return;
            }
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!admin.isOnline()) return;
            Player target = org.bukkit.Bukkit.getPlayer(holder.targetId());
            if (target == null || !target.isOnline()) return;
            Inventory top = admin.getOpenInventory().getTopInventory();
            if (holder.mode() == InvseeHolder.Mode.ENDER) {
                for (int i = 0; i < 27; i++) {
                    svc.applyGuiSlotToTarget(target, holder, i, top.getItem(i));
                }
            } else {
                for (int i = 0; i <= 40; i++) {
                    if (i <= 35 || svc.isArmorOrOffhand(i)) {
                        svc.applyGuiSlotToTarget(target, holder, i, top.getItem(i));
                    }
                }
            }
        });
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof InvseeHolder holder)) return;
        if (!(event.getPlayer() instanceof Player admin)) return;
        InvseeService svc = plugin.getInvsee();
        if (holder.editable()) {
            // Final write-back using closed inventory contents
            Player target = org.bukkit.Bukkit.getPlayer(holder.targetId());
            if (target != null && target.isOnline()) {
                Inventory top = event.getInventory();
                if (holder.mode() == InvseeHolder.Mode.ENDER) {
                    for (int i = 0; i < 27; i++) {
                        ItemStack stack = top.getItem(i);
                        target.getEnderChest().setItem(i,
                                stack == null || stack.getType().isAir() ? null : stack.clone());
                    }
                } else {
                    for (int gui = 0; gui <= 35; gui++) {
                        int mapped = svc.mapGuiToPlayerSlot(gui);
                        if (mapped >= 0) {
                            ItemStack stack = top.getItem(gui);
                            target.getInventory().setItem(mapped,
                                    stack == null || stack.getType().isAir() ? null : stack.clone());
                        }
                    }
                    setArmor(target, top.getItem(InvseeService.SLOT_BOOTS), "boots");
                    setArmor(target, top.getItem(InvseeService.SLOT_LEGGINGS), "legs");
                    setArmor(target, top.getItem(InvseeService.SLOT_CHEST), "chest");
                    setArmor(target, top.getItem(InvseeService.SLOT_HELMET), "helm");
                    ItemStack off = top.getItem(InvseeService.SLOT_OFFHAND);
                    target.getInventory().setItemInOffHand(
                            off == null || off.getType().isAir() ? null : off.clone());
                }
            }
        }
        svc.onClose(admin);
    }

    private static void setArmor(Player target, ItemStack stack, String which) {
        ItemStack copy = stack == null || stack.getType().isAir() ? null : stack.clone();
        switch (which) {
            case "boots" -> target.getInventory().setBoots(copy);
            case "legs" -> target.getInventory().setLeggings(copy);
            case "chest" -> target.getInventory().setChestplate(copy);
            case "helm" -> target.getInventory().setHelmet(copy);
            default -> { }
        }
    }
}
