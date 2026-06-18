package pl.serverguard.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public record GuiHolder(GuiState state) implements InventoryHolder {

    @Override
    public @NotNull Inventory getInventory() {
        return null;
    }
}
