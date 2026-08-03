package pl.serverguard.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import pl.serverguard.ServerGuard;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live inventory / enderchest viewer for moderators.
 * Layout (54): storage 0-26, hotbar 27-35, armor/offhand 36-40, controls 45-53.
 */
public class InvseeService {

    public static final int SLOT_STORAGE_START = 0;
    public static final int SLOT_HOTBAR_START = 27;
    public static final int SLOT_BOOTS = 36;
    public static final int SLOT_LEGGINGS = 37;
    public static final int SLOT_CHEST = 38;
    public static final int SLOT_HELMET = 39;
    public static final int SLOT_OFFHAND = 40;
    public static final int SLOT_REFRESH = 45;
    public static final int SLOT_TOGGLE = 46;
    public static final int SLOT_MODE = 49;
    public static final int SLOT_CLOSE = 53;

    private final ServerGuard plugin;
    private final Map<UUID, BukkitTask> syncTasks = new ConcurrentHashMap<>();

    public InvseeService(ServerGuard plugin) {
        this.plugin = plugin;
    }

    public boolean openInventory(Player admin, Player target) {
        return open(admin, target, InvseeHolder.Mode.INVENTORY);
    }

    public boolean openEnder(Player admin, Player target) {
        return open(admin, target, InvseeHolder.Mode.ENDER);
    }

    public boolean open(Player admin, Player target, InvseeHolder.Mode mode) {
        if (admin == null || target == null || !target.isOnline()) return false;
        if (!admin.hasPermission("serverguard.invsee") && !admin.hasPermission("serverguard.admin")) {
            admin.sendMessage(plugin.getLang().tr("commands.invsee-no-perm"));
            return false;
        }

        boolean editable = admin.hasPermission("serverguard.invsee.edit");
        InvseeHolder holder = new InvseeHolder(target.getUniqueId(), target.getName(), mode, editable);
        String titleKey = mode == InvseeHolder.Mode.ENDER ? "commands.invsee-ender-title" : "commands.invsee-title";
        String title = color(plugin.getLang().tr(titleKey, "player", target.getName()));
        if (title.length() > 32) title = title.substring(0, 32);

        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.bind(inv);
        paintControls(inv, holder);
        syncFromTarget(inv, target, mode);
        stopSync(admin.getUniqueId());
        admin.openInventory(inv);

        // Live mirror while read-only; editable sessions sync less often to avoid fighting clicks.
        long period = editable ? 40L : 10L;
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!admin.isOnline()) {
                stopSync(admin.getUniqueId());
                return;
            }
            if (!(admin.getOpenInventory().getTopInventory().getHolder() instanceof InvseeHolder open)
                    || !open.targetId().equals(target.getUniqueId())) {
                stopSync(admin.getUniqueId());
                return;
            }
            Player live = Bukkit.getPlayer(target.getUniqueId());
            if (live == null || !live.isOnline()) {
                admin.closeInventory();
                admin.sendMessage(plugin.getLang().tr("commands.invsee-target-left", "player", target.getName()));
                stopSync(admin.getUniqueId());
                return;
            }
            if (!editable) {
                syncFromTarget(admin.getOpenInventory().getTopInventory(), live, open.mode());
                paintControls(admin.getOpenInventory().getTopInventory(), open);
            }
        }, period, period);
        syncTasks.put(admin.getUniqueId(), task);

        admin.sendMessage(plugin.getLang().tr(
                editable ? "commands.invsee-opened-edit" : "commands.invsee-opened",
                "player", target.getName()));
        return true;
    }

    public void stopSync(UUID adminId) {
        BukkitTask t = syncTasks.remove(adminId);
        if (t != null) t.cancel();
    }

    public void onClose(Player admin) {
        stopSync(admin.getUniqueId());
    }

    public void refresh(Player admin, InvseeHolder holder) {
        Player target = Bukkit.getPlayer(holder.targetId());
        if (target == null || !target.isOnline()) {
            admin.closeInventory();
            admin.sendMessage(plugin.getLang().tr("commands.invsee-target-left", "player", holder.targetName()));
            return;
        }
        Inventory top = admin.getOpenInventory().getTopInventory();
        syncFromTarget(top, target, holder.mode());
        paintControls(top, holder);
    }

    public void toggleMode(Player admin, InvseeHolder holder) {
        Player target = Bukkit.getPlayer(holder.targetId());
        if (target == null || !target.isOnline()) {
            admin.closeInventory();
            return;
        }
        InvseeHolder.Mode next = holder.mode() == InvseeHolder.Mode.INVENTORY
                ? InvseeHolder.Mode.ENDER : InvseeHolder.Mode.INVENTORY;
        open(admin, target, next);
    }

    /** Map mirror GUI slot → player inventory slot, or -2 for armor/offhand handled separately, -1 = control/invalid. */
    public int mapGuiToPlayerSlot(int guiSlot) {
        if (guiSlot >= 0 && guiSlot <= 26) return guiSlot + 9;      // storage
        if (guiSlot >= 27 && guiSlot <= 35) return guiSlot - 27;     // hotbar
        return -1;
    }

    public boolean isArmorOrOffhand(int guiSlot) {
        return guiSlot == SLOT_BOOTS || guiSlot == SLOT_LEGGINGS
                || guiSlot == SLOT_CHEST || guiSlot == SLOT_HELMET
                || guiSlot == SLOT_OFFHAND;
    }

    public boolean isControlSlot(int guiSlot) {
        return guiSlot >= 45;
    }

    public void applyGuiSlotToTarget(Player target, InvseeHolder holder, int guiSlot, ItemStack stack) {
        if (holder.mode() == InvseeHolder.Mode.ENDER) {
            if (guiSlot < 0 || guiSlot >= 27) return;
            target.getEnderChest().setItem(guiSlot, cloneOrNull(stack));
            return;
        }
        PlayerInventory inv = target.getInventory();
        int mapped = mapGuiToPlayerSlot(guiSlot);
        if (mapped >= 0) {
            inv.setItem(mapped, cloneOrNull(stack));
            return;
        }
        ItemStack copy = cloneOrNull(stack);
        switch (guiSlot) {
            case SLOT_BOOTS -> inv.setBoots(copy);
            case SLOT_LEGGINGS -> inv.setLeggings(copy);
            case SLOT_CHEST -> inv.setChestplate(copy);
            case SLOT_HELMET -> inv.setHelmet(copy);
            case SLOT_OFFHAND -> inv.setItemInOffHand(copy);
            default -> { }
        }
    }

    public void writeBackAll(Player admin, InvseeHolder holder) {
        if (!holder.editable()) return;
        Player target = Bukkit.getPlayer(holder.targetId());
        if (target == null || !target.isOnline()) return;
        Inventory top = admin.getOpenInventory().getTopInventory();
        if (holder.mode() == InvseeHolder.Mode.ENDER) {
            for (int i = 0; i < 27; i++) {
                target.getEnderChest().setItem(i, cloneOrNull(top.getItem(i)));
            }
            return;
        }
        for (int gui = 0; gui <= 35; gui++) {
            int mapped = mapGuiToPlayerSlot(gui);
            if (mapped >= 0) target.getInventory().setItem(mapped, cloneOrNull(top.getItem(gui)));
        }
        target.getInventory().setBoots(cloneOrNull(top.getItem(SLOT_BOOTS)));
        target.getInventory().setLeggings(cloneOrNull(top.getItem(SLOT_LEGGINGS)));
        target.getInventory().setChestplate(cloneOrNull(top.getItem(SLOT_CHEST)));
        target.getInventory().setHelmet(cloneOrNull(top.getItem(SLOT_HELMET)));
        target.getInventory().setItemInOffHand(cloneOrNull(top.getItem(SLOT_OFFHAND)));
    }

    private void syncFromTarget(Inventory gui, Player target, InvseeHolder.Mode mode) {
        if (mode == InvseeHolder.Mode.ENDER) {
            for (int i = 0; i < 54; i++) {
                if (i < 27) gui.setItem(i, cloneOrNull(target.getEnderChest().getItem(i)));
                else if (i < 45) gui.setItem(i, pane());
            }
            return;
        }
        PlayerInventory inv = target.getInventory();
        for (int i = 0; i < 27; i++) {
            gui.setItem(i, cloneOrNull(inv.getItem(i + 9)));
        }
        for (int i = 0; i < 9; i++) {
            gui.setItem(SLOT_HOTBAR_START + i, cloneOrNull(inv.getItem(i)));
        }
        gui.setItem(SLOT_BOOTS, cloneOrNull(inv.getBoots()));
        gui.setItem(SLOT_LEGGINGS, cloneOrNull(inv.getLeggings()));
        gui.setItem(SLOT_CHEST, cloneOrNull(inv.getChestplate()));
        gui.setItem(SLOT_HELMET, cloneOrNull(inv.getHelmet()));
        gui.setItem(SLOT_OFFHAND, cloneOrNull(inv.getItemInOffHand()));
        for (int i = 41; i < 45; i++) gui.setItem(i, pane());
    }

    private void paintControls(Inventory gui, InvseeHolder holder) {
        for (int i = 45; i < 54; i++) gui.setItem(i, pane());

        gui.setItem(SLOT_REFRESH, button(Material.HOPPER,
                plugin.getLang().tr("commands.invsee-btn-refresh"),
                plugin.getLang().tr("commands.invsee-btn-refresh-lore")));

        boolean ender = holder.mode() == InvseeHolder.Mode.ENDER;
        gui.setItem(SLOT_TOGGLE, button(
                ender ? Material.CHEST : Material.ENDER_CHEST,
                plugin.getLang().tr(ender ? "commands.invsee-btn-inv" : "commands.invsee-btn-ender"),
                plugin.getLang().tr(ender ? "commands.invsee-btn-inv-lore" : "commands.invsee-btn-ender-lore")));

        gui.setItem(SLOT_MODE, button(
                holder.editable() ? Material.WRITABLE_BOOK : Material.BOOK,
                plugin.getLang().tr(holder.editable() ? "commands.invsee-mode-edit" : "commands.invsee-mode-view"),
                plugin.getLang().tr(holder.editable() ? "commands.invsee-mode-edit-lore" : "commands.invsee-mode-view-lore")));

        gui.setItem(SLOT_CLOSE, button(Material.BARRIER,
                plugin.getLang().tr("commands.invsee-btn-close"),
                plugin.getLang().tr("commands.invsee-btn-close-lore")));
    }

    private static ItemStack cloneOrNull(ItemStack stack) {
        return stack == null || stack.getType().isAir() ? null : stack.clone();
    }

    private ItemStack pane() {
        ItemStack stack = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack button(Material mat, String name, String lore) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            if (lore != null && !lore.isBlank()) meta.setLore(List.of(color(lore)));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static String color(String s) {
        return s == null ? "" : s.replace('&', '§');
    }
}
