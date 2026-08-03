package pl.serverguard.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Holder for live inventory / enderchest mirrors — separate from {@link GuiHolder}
 * so {@link pl.serverguard.listeners.GuiListener} does not cancel all clicks.
 */
public final class InvseeHolder implements InventoryHolder {

    public enum Mode { INVENTORY, ENDER }

    private final UUID targetId;
    private final String targetName;
    private final Mode mode;
    private final boolean editable;
    private Inventory inventory;

    public InvseeHolder(UUID targetId, String targetName, Mode mode, boolean editable) {
        this.targetId = targetId;
        this.targetName = targetName;
        this.mode = mode;
        this.editable = editable;
    }

    public UUID targetId() { return targetId; }
    public String targetName() { return targetName; }
    public Mode mode() { return mode; }
    public boolean editable() { return editable; }

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
