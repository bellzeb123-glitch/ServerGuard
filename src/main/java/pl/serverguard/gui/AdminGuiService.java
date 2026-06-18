package pl.serverguard.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import pl.serverguard.ServerGuard;
import pl.serverguard.managers.AdminAuditManager;

import java.util.*;

public class AdminGuiService {

    public enum PendingType { ADD_RULE, FIND_PLAYER, ADD_BLOCK, ADD_CONTAINER }

    public record PendingInput(PendingType type, GuiState returnState) {}

    private static final String BLOCKS_PATH = "blocks.always-log";
    private static final String CONTAINERS_PATH = "containers.monitored-types";
    private static final int CONTENT = 45;
    private static final int LOGS_PER_PAGE = 45;
    private static final int LIST_PER_PAGE = 36;

    private final ServerGuard plugin;
    private final Map<UUID, PendingInput> pending = new HashMap<>();

    public AdminGuiService(ServerGuard plugin) {
        this.plugin = plugin;
    }

    public boolean isAwaitingInput(Player player) {
        return pending.containsKey(player.getUniqueId());
    }

    public void cancelInput(Player player) {
        pending.remove(player.getUniqueId());
    }

    private String L(String key, Object... pairs) {
        return plugin.getLang().tr(key, pairs);
    }

    // ── Open screens ────────────────────────────────────────────────────────

    public void openMain(Player admin) {
        Inventory inv = Bukkit.createInventory(new GuiHolder(GuiState.main()), 54,
            title(L("gui.main-title")));
        fillBorder(inv, Material.GRAY_STAINED_GLASS_PANE);

        String langLabel = plugin.getLang().code().toUpperCase();
        inv.setItem(4, item(Material.NAME_TAG, L("gui.main.language", "lang", langLabel),
            L("gui.main.language-lore1"), L("gui.main.language-lore2", "lang", langLabel), "",
            L("gui.nav.click")));

        inv.setItem(20, item(Material.PLAYER_HEAD, L("gui.main.players"),
            L("gui.main.players-lore1"), L("gui.main.players-lore2"), "", L("gui.nav.click")));

        inv.setItem(22, item(Material.BOOK, L("gui.main.monitoring"),
            L("gui.main.monitoring-lore1"), L("gui.main.monitoring-lore2"), "", L("gui.nav.click")));

        inv.setItem(24, item(Material.COMMAND_BLOCK, L("gui.main.audit"),
            L("gui.main.audit-lore1"),
            L("gui.main.audit-lore2", "count", plugin.getAdminAudit().getRules().size()),
            "", L("gui.nav.click")));

        inv.setItem(40, item(Material.COMPASS, L("gui.main.search"),
            L("gui.main.search-lore"), "", L("gui.nav.click")));

        inv.setItem(44, item(Material.REDSTONE, L("gui.main.reload"),
            L("gui.main.reload-lore"), "", L("gui.nav.click")));

        admin.openInventory(inv);
    }

    public void openPlayers(Player admin, int page) {
        GuiState state = new GuiState(GuiState.Screen.PLAYERS, page, null, GuiState.LogType.NONE);
        Inventory inv = Bukkit.createInventory(new GuiHolder(state), 54,
            title(L("gui.players-title")));

        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            names.add(online.getName());
        }
        int from = page * LIST_PER_PAGE;
        for (int i = from; i < Math.min(from + LIST_PER_PAGE, names.size()); i++) {
            String name = names.get(i);
            inv.setItem(i - from, playerHead(name, true, plugin.getWatchManager().isWatched(name)));
        }

        fillNav(inv, names.size() > from + LIST_PER_PAGE, page > 0, L("gui.nav.back-main"));
        inv.setItem(50, item(Material.OAK_SIGN, L("gui.players.search"),
            L("gui.players.search-lore"), "", L("gui.nav.click")));
        admin.openInventory(inv);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<String> merged = new ArrayList<>(names);
            for (String recent : plugin.getDb().getRecentPlayerNames(80)) {
                if (merged.stream().noneMatch(n -> n.equalsIgnoreCase(recent))) {
                    merged.add(recent);
                }
            }
            int to = Math.min(from + LIST_PER_PAGE, merged.size());
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!isOpen(admin, state)) return;
                Inventory open = admin.getOpenInventory().getTopInventory();
                for (int i = 0; i < LIST_PER_PAGE; i++) open.setItem(i, null);
                for (int i = from; i < to; i++) {
                    String name = merged.get(i);
                    boolean online = Bukkit.getPlayerExact(name) != null;
                    open.setItem(i - from, playerHead(name, online, plugin.getWatchManager().isWatched(name)));
                }
                fillNav(open, to < merged.size(), page > 0, L("gui.nav.back-main"));
            });
        });
    }

    public void openPlayer(Player admin, String target) {
        GuiState state = new GuiState(GuiState.Screen.PLAYER, 0, target, GuiState.LogType.NONE);
        Inventory inv = Bukkit.createInventory(new GuiHolder(state), 54,
            title(L("gui.player-title", "player", target)));

        boolean watched = plugin.getWatchManager().isWatched(target);
        boolean online = Bukkit.getPlayerExact(target) != null;

        inv.setItem(4, playerHead(target, online, watched));
        inv.setItem(19, item(Material.WRITABLE_BOOK, L("gui.player.commands"), "", L("gui.nav.click")));
        inv.setItem(21, item(Material.CHEST, L("gui.player.containers"), "", L("gui.nav.click")));
        inv.setItem(23, item(Material.GRASS_BLOCK, L("gui.player.blocks"), "", L("gui.nav.click")));
        inv.setItem(25, item(Material.CLOCK, L("gui.player.sessions"), "", L("gui.nav.click")));

        inv.setItem(40, item(watched ? Material.ENDER_EYE : Material.ENDER_PEARL,
            watched ? L("gui.player.watch-on") : L("gui.player.watch-off"),
            watched ? L("gui.player.watch-remove") : L("gui.player.watch-add")));

        inv.setItem(48, backItem(L("gui.nav.back-players")));
        inv.setItem(49, backItem(L("gui.nav.back-main")));

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            int[] c = plugin.getDb().getPlayerCounts(target);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!isOpen(admin, state)) return;
                Inventory open = admin.getOpenInventory().getTopInventory();
                open.setItem(19, item(Material.WRITABLE_BOOK, L("gui.player.commands"),
                    L("gui.player.db-count", "count", c[0]), "", L("gui.nav.click")));
                open.setItem(21, item(Material.CHEST, L("gui.player.containers"),
                    L("gui.player.db-count", "count", c[1]), "", L("gui.nav.click")));
                open.setItem(23, item(Material.GRASS_BLOCK, L("gui.player.blocks"),
                    L("gui.player.db-count", "count", c[2]), "", L("gui.nav.click")));
                open.setItem(25, item(Material.CLOCK, L("gui.player.sessions"),
                    L("gui.player.db-count", "count", c[3]), "", L("gui.nav.click")));
            });
        });

        admin.openInventory(inv);
    }

    public void openLogs(Player admin, GuiState state) {
        String typeName = logTypeName(state.logType());
        Inventory inv = Bukkit.createInventory(new GuiHolder(state), 54,
            title(L("gui.logs-title", "player", state.targetPlayer(), "type", typeName)));

        inv.setItem(22, item(Material.HOPPER, L("gui.logs.loading")));
        fillNav(inv, false, state.page() > 0, L("gui.nav.back-player", "player", state.targetPlayer()));
        admin.openInventory(inv);

        int offset = state.page() * LOGS_PER_PAGE;
        int limit = LOGS_PER_PAGE + 1;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<String[]> rows = fetchLogs(state, offset, limit);
            boolean hasNext = rows.size() > LOGS_PER_PAGE;
            if (hasNext) rows = rows.subList(0, LOGS_PER_PAGE);

            List<String[]> finalRows = rows;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!isOpen(admin, state)) return;
                Inventory open = admin.getOpenInventory().getTopInventory();
                for (int i = 0; i < CONTENT; i++) open.setItem(i, null);
                for (int i = 0; i < finalRows.size(); i++) {
                    open.setItem(i, logItem(state.logType(), finalRows.get(i)));
                }
                if (finalRows.isEmpty()) {
                    open.setItem(22, item(Material.BARRIER, L("gui.logs.empty")));
                }
                fillNav(open, hasNext, state.page() > 0,
                    L("gui.nav.back-player", "player", state.targetPlayer()));
            });
        });
    }

    public void openMonitoring(Player admin) {
        GuiState state = new GuiState(GuiState.Screen.MONITORING, 0, null, GuiState.LogType.NONE);
        Inventory inv = Bukkit.createInventory(new GuiHolder(state), 54,
            title(L("gui.monitoring-title")));
        var cfg = plugin.getConfig();

        inv.setItem(10, toggleItem(L("gui.monitoring.commands"), cfg.getBoolean("commands.log-all", true),
            "commands.log-all"));

        inv.setItem(12, item(Material.CHEST, L("gui.monitoring.containers"),
            L("gui.monitoring.containers-lore"),
            "§7" + plugin.getConfigLists().get(CONTAINERS_PATH).size() + " typów",
            "", L("gui.nav.click")));

        inv.setItem(14, item(Material.GRASS_BLOCK, L("gui.monitoring.blocks"),
            L("gui.monitoring.blocks-lore"),
            "§7" + plugin.getConfigLists().get(BLOCKS_PATH).size() + " bloków",
            "", L("gui.nav.click")));

        inv.setItem(16, toggleItem(L("gui.monitoring.teleports"), cfg.getBoolean("teleports.enabled", true),
            "teleports.enabled"));

        inv.setItem(28, toggleItem(L("gui.monitoring.moderation"),
            cfg.getBoolean("moderation.log-gamemode", true), "moderation.*"));

        inv.setItem(30, toggleItem(L("gui.monitoring.audit"),
            cfg.getBoolean("admin-audit.enabled", true), "admin-audit.enabled"));

        inv.setItem(32, toggleItem(L("gui.monitoring.alerts"), cfg.getBoolean("alerts.enabled", true),
            "alerts.enabled"));

        inv.setItem(34, item(Material.ANVIL, L("gui.monitoring.retention"),
            "§7" + cfg.getInt("database.retention.days", 30) + " dni",
            "§f" + cfg.getBoolean("database.retention.enabled", true)));

        inv.setItem(48, backItem(L("gui.nav.back-main")));
        admin.openInventory(inv);
    }

    public void openContainerTypes(Player admin, int page) {
        GuiState state = new GuiState(GuiState.Screen.CONTAINER_TYPES, page, null, GuiState.LogType.NONE);
        List<String> types = plugin.getConfigLists().get(CONTAINERS_PATH);
        Inventory inv = Bukkit.createInventory(new GuiHolder(state), 54,
            title(L("gui.containers-title", "count", types.size())));

        boolean enabled = plugin.getConfig().getBoolean("containers.enabled", true);
        inv.setItem(4, toggleItem(L("gui.monitoring.containers"), enabled, "containers.enabled"));

        fillTypePage(inv, types, page, true);
        fillNav(inv, (page + 1) * LIST_PER_PAGE < types.size(), page > 0, L("gui.nav.back-monitoring"));
        inv.setItem(50, item(Material.LIME_DYE, L("gui.list.add-container"),
            L("gui.list.add-container-lore"), "", L("gui.nav.click")));
        admin.openInventory(inv);
    }

    public void openBlockTypes(Player admin, int page) {
        GuiState state = new GuiState(GuiState.Screen.BLOCK_TYPES, page, null, GuiState.LogType.NONE);
        List<String> types = plugin.getConfigLists().get(BLOCKS_PATH);
        Inventory inv = Bukkit.createInventory(new GuiHolder(state), 54,
            title(L("gui.blocks-title", "count", types.size())));

        boolean blocksOn = plugin.getConfig().getBoolean("blocks.log-break", true)
            && plugin.getConfig().getBoolean("blocks.log-place", true);
        inv.setItem(4, toggleItem(L("gui.monitoring.blocks"), blocksOn, "blocks.log"));

        fillTypePage(inv, types, page, false);
        fillNav(inv, (page + 1) * LIST_PER_PAGE < types.size(), page > 0, L("gui.nav.back-monitoring"));
        inv.setItem(50, item(Material.LIME_DYE, L("gui.list.add-block"),
            L("gui.list.add-block-lore"), "", L("gui.nav.click")));
        admin.openInventory(inv);
    }

    public void openAuditRules(Player admin, int page) {
        GuiState state = new GuiState(GuiState.Screen.AUDIT_RULES, page, null, GuiState.LogType.NONE);
        List<AdminAuditManager.AuditRule> rules = plugin.getAdminAudit().getRules();
        Inventory inv = Bukkit.createInventory(new GuiHolder(state), 54,
            title(L("gui.rules-title", "count", rules.size())));

        int from = page * LIST_PER_PAGE;
        int to = Math.min(from + LIST_PER_PAGE, rules.size());
        for (int i = from; i < to; i++) {
            AdminAuditManager.AuditRule rule = rules.get(i);
            inv.setItem(i - from, item(Material.PAPER, "§e/" + rule.pattern(),
                L("gui.rules.permission", "perm", rule.permission()),
                rule.block() ? L("gui.rules.block-yes") : L("gui.rules.block-no"),
                "", L("gui.list.remove")));
        }

        fillNav(inv, to < rules.size(), page > 0, L("gui.nav.back-main"));
        inv.setItem(50, item(Material.LIME_DYE, L("gui.rules.add"),
            L("gui.rules.add-lore1"), "", L("gui.nav.click")));
        admin.openInventory(inv);
    }

    private void fillTypePage(Inventory inv, List<String> types, int page, boolean container) {
        int from = page * LIST_PER_PAGE;
        int to = Math.min(from + LIST_PER_PAGE, types.size());
        for (int i = from; i < to; i++) {
            String typeName = types.get(i);
            Material mat = resolveMaterial(typeName, container);
            inv.setItem(i - from, item(mat, "§f" + typeName, L("gui.list.remove")));
        }
        if (from >= types.size()) {
            inv.setItem(22, item(Material.BARRIER, L("gui.list.empty")));
        }
    }

    // ── Clicks ──────────────────────────────────────────────────────────────

    public void handleClick(Player admin, GuiState state, int slot, boolean shift) {
        switch (state.screen()) {
            case MAIN -> handleMainClick(admin, slot);
            case PLAYERS -> handlePlayersClick(admin, state, slot);
            case PLAYER -> handlePlayerClick(admin, state, slot);
            case LOGS -> handleLogsClick(admin, state, slot);
            case MONITORING -> handleMonitoringClick(admin, slot);
            case CONTAINER_TYPES -> handleContainerTypesClick(admin, state, slot);
            case BLOCK_TYPES -> handleBlockTypesClick(admin, state, slot);
            case AUDIT_RULES -> handleAuditClick(admin, state, slot);
        }
    }

    private void handleMainClick(Player admin, int slot) {
        switch (slot) {
            case 4 -> {
                plugin.getLang().toggleLanguage();
                admin.sendMessage(L("chat.language-changed", "lang", plugin.getLang().code().toUpperCase()));
                openMain(admin);
            }
            case 20 -> openPlayers(admin, 0);
            case 22 -> openMonitoring(admin);
            case 24 -> openAuditRules(admin, 0);
            case 40 -> prompt(admin, PendingType.FIND_PLAYER, GuiState.main(),
                L("chat.search-phrase"), L("chat.cancel-hint"));
            case 44 -> {
                plugin.reloadAll();
                admin.sendMessage(L("chat.reload-ok"));
                openMain(admin);
            }
        }
    }

    private void handlePlayersClick(Player admin, GuiState state, int slot) {
        if (slot == 45 && state.page() > 0) {
            openPlayers(admin, state.page() - 1);
            return;
        }
        if (slot == 53) {
            openPlayers(admin, state.page() + 1);
            return;
        }
        if (slot == 48) {
            openMain(admin);
            return;
        }
        if (slot == 50) {
            prompt(admin, PendingType.FIND_PLAYER, state,
                L("chat.search-player"), L("chat.cancel-hint"));
            return;
        }
        if (slot >= 0 && slot < LIST_PER_PAGE) {
            ItemStack stack = admin.getOpenInventory().getTopInventory().getItem(slot);
            if (stack == null || !stack.hasItemMeta()) return;
            String name = cleanPlayerName(stack.getItemMeta().getDisplayName());
            if (!name.isEmpty()) openPlayer(admin, name);
        }
    }

    private void handlePlayerClick(Player admin, GuiState state, int slot) {
        if (slot == 48) {
            openPlayers(admin, 0);
            return;
        }
        if (slot == 49) {
            openMain(admin);
            return;
        }
        if (slot == 40) {
            plugin.getWatchManager().toggle(state.targetPlayer());
            openPlayer(admin, state.targetPlayer());
            return;
        }
        GuiState.LogType type = switch (slot) {
            case 19 -> GuiState.LogType.COMMANDS;
            case 21 -> GuiState.LogType.CONTAINERS;
            case 23 -> GuiState.LogType.BLOCKS;
            case 25 -> GuiState.LogType.SESSIONS;
            default -> GuiState.LogType.NONE;
        };
        if (type != GuiState.LogType.NONE) {
            openLogs(admin, state.withLogs(type));
        }
    }

    private void handleLogsClick(Player admin, GuiState state, int slot) {
        if (slot == 45 && state.page() > 0) {
            openLogs(admin, state.withLogsPage(state.logType(), state.page() - 1));
            return;
        }
        if (slot == 53) {
            openLogs(admin, state.withLogsPage(state.logType(), state.page() + 1));
            return;
        }
        if (slot == 48) {
            openPlayer(admin, state.targetPlayer());
        }
    }

    private void handleMonitoringClick(Player admin, int slot) {
        if (slot == 48) {
            openMain(admin);
            return;
        }
        switch (slot) {
            case 10 -> { toggle("commands.log-all", true); plugin.reloadAll(); openMonitoring(admin); }
            case 12 -> openContainerTypes(admin, 0);
            case 14 -> openBlockTypes(admin, 0);
            case 16 -> { toggle("teleports.enabled", true); plugin.reloadAll(); openMonitoring(admin); }
            case 28 -> toggleModeration();
            case 30 -> plugin.getAdminAudit().setEnabled(!plugin.getAdminAudit().isEnabled());
            case 32 -> { toggle("alerts.enabled", true); plugin.reloadAll(); openMonitoring(admin); }
            default -> { return; }
        }
        if (slot == 28 || slot == 30) {
            plugin.reloadAll();
            openMonitoring(admin);
        }
    }

    private void handleContainerTypesClick(Player admin, GuiState state, int slot) {
        if (slot == 48) {
            openMonitoring(admin);
            return;
        }
        if (slot == 45 && state.page() > 0) {
            openContainerTypes(admin, state.page() - 1);
            return;
        }
        if (slot == 53) {
            openContainerTypes(admin, state.page() + 1);
            return;
        }
        if (slot == 4) {
            toggle("containers.enabled", true);
            plugin.reloadAll();
            openContainerTypes(admin, state.page());
            return;
        }
        if (slot == 50) {
            prompt(admin, PendingType.ADD_CONTAINER, state,
                L("chat.add-container"), L("chat.cancel-hint"));
            return;
        }
        if (slot >= 0 && slot < LIST_PER_PAGE) {
            int index = state.page() * LIST_PER_PAGE + slot;
            List<String> list = plugin.getConfigLists().get(CONTAINERS_PATH);
            if (index < list.size()) {
                String removed = list.get(index);
                plugin.getConfigLists().removeAt(CONTAINERS_PATH, index);
                admin.sendMessage(L("chat.list-removed", "value", removed));
                plugin.reloadAll();
                openContainerTypes(admin, state.page());
            }
        }
    }

    private void handleBlockTypesClick(Player admin, GuiState state, int slot) {
        if (slot == 48) {
            openMonitoring(admin);
            return;
        }
        if (slot == 45 && state.page() > 0) {
            openBlockTypes(admin, state.page() - 1);
            return;
        }
        if (slot == 53) {
            openBlockTypes(admin, state.page() + 1);
            return;
        }
        if (slot == 4) {
            boolean on = !plugin.getConfig().getBoolean("blocks.log-break", true);
            plugin.getConfig().set("blocks.log-break", on);
            plugin.getConfig().set("blocks.log-place", on);
            plugin.saveConfig();
            plugin.reloadAll();
            openBlockTypes(admin, state.page());
            return;
        }
        if (slot == 50) {
            prompt(admin, PendingType.ADD_BLOCK, state,
                L("chat.add-block"), L("chat.cancel-hint"));
            return;
        }
        if (slot >= 0 && slot < LIST_PER_PAGE) {
            int index = state.page() * LIST_PER_PAGE + slot;
            List<String> list = plugin.getConfigLists().get(BLOCKS_PATH);
            if (index < list.size()) {
                String removed = list.get(index);
                plugin.getConfigLists().removeAt(BLOCKS_PATH, index);
                admin.sendMessage(L("chat.list-removed", "value", removed));
                plugin.reloadAll();
                openBlockTypes(admin, state.page());
            }
        }
    }

    private void handleAuditClick(Player admin, GuiState state, int slot) {
        if (slot == 48) {
            openMain(admin);
            return;
        }
        if (slot == 45 && state.page() > 0) {
            openAuditRules(admin, state.page() - 1);
            return;
        }
        if (slot == 53) {
            openAuditRules(admin, state.page() + 1);
            return;
        }
        if (slot == 50) {
            prompt(admin, PendingType.ADD_RULE, state,
                L("chat.add-rule"), L("chat.cancel-hint"));
            return;
        }
        if (slot >= 0 && slot < LIST_PER_PAGE) {
            int index = state.page() * LIST_PER_PAGE + slot;
            plugin.getAdminAudit().removeRule(index);
            admin.sendMessage(L("chat.rule-removed", "index", index + 1));
            openAuditRules(admin, state.page());
        }
    }

    public void handleChatInput(Player admin, String message) {
        PendingInput input = pending.remove(admin.getUniqueId());
        if (input == null) return;

        if (plugin.getLang().isCancel(message)) {
            admin.sendMessage(L("chat.cancelled"));
            reopen(admin, input.returnState());
            return;
        }

        switch (input.type()) {
            case ADD_RULE -> {
                String[] parts = message.split("\\|");
                if (parts.length < 2) {
                    reopen(admin, input.returnState());
                    return;
                }
                boolean block = parts.length < 3 || parts[2].equalsIgnoreCase("tak")
                    || parts[2].equalsIgnoreCase("true") || parts[2].equalsIgnoreCase("yes");
                plugin.getAdminAudit().addRule(parts[0].trim(), parts[1].trim(), block);
                admin.sendMessage(L("chat.rule-added", "pattern", parts[0].trim()));
                openAuditRules(admin, 0);
            }
            case FIND_PLAYER -> {
                if (input.returnState().screen() == GuiState.Screen.PLAYERS) {
                    openPlayer(admin, message.trim());
                    return;
                }
                admin.sendMessage("§6" + message);
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    List<String[]> results = plugin.getDb().searchAll(message.trim(), 20);
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (results.isEmpty()) {
                            admin.sendMessage(L("gui.logs.empty"));
                        } else {
                            for (String[] r : results) {
                                admin.sendMessage("§7" + r[0] + " §b" + r[1] + " §f[" + r[2] + "] §7" + r[3]);
                            }
                        }
                        openMain(admin);
                    });
                });
            }
            case ADD_BLOCK -> addListEntry(admin, input, message, BLOCKS_PATH, false);
            case ADD_CONTAINER -> addListEntry(admin, input, message, CONTAINERS_PATH, true);
        }
    }

    private void addListEntry(Player admin, PendingInput input, String message,
                              String path, boolean container) {
        String value = message.trim().toUpperCase();
        if (Material.matchMaterial(value) == null && !container) {
            admin.sendMessage(L("chat.invalid-material", "value", value));
            reopen(admin, input.returnState());
            return;
        }
        if (container) {
            try {
                Material.valueOf(value);
            } catch (IllegalArgumentException e) {
                admin.sendMessage(L("chat.invalid-material", "value", value));
                reopen(admin, input.returnState());
                return;
            }
        }
        if (plugin.getConfigLists().add(path, value)) {
            admin.sendMessage(L("chat.list-added", "value", value));
            plugin.reloadAll();
        }
        reopen(admin, input.returnState());
    }

    private void prompt(Player admin, PendingType type, GuiState returnState, String... lines) {
        pending.put(admin.getUniqueId(), new PendingInput(type, returnState));
        admin.closeInventory();
        for (String line : lines) admin.sendMessage(line);
    }

    private void reopen(Player admin, GuiState state) {
        switch (state.screen()) {
            case MAIN -> openMain(admin);
            case PLAYERS -> openPlayers(admin, state.page());
            case AUDIT_RULES -> openAuditRules(admin, state.page());
            case CONTAINER_TYPES -> openContainerTypes(admin, state.page());
            case BLOCK_TYPES -> openBlockTypes(admin, state.page());
            case MONITORING -> openMonitoring(admin);
            default -> openMain(admin);
        }
    }

    private void toggleModeration() {
        boolean on = !plugin.getConfig().getBoolean("moderation.log-gamemode", true);
        plugin.getConfig().set("moderation.log-gamemode", on);
        plugin.getConfig().set("moderation.log-kicks", on);
        plugin.getConfig().set("moderation.log-console-commands", on);
        plugin.saveConfig();
    }

    private void toggle(String path, boolean defaultValue) {
        plugin.getConfig().set(path, !plugin.getConfig().getBoolean(path, defaultValue));
        plugin.saveConfig();
    }

    private String logTypeName(GuiState.LogType type) {
        return switch (type) {
            case COMMANDS -> L("gui.logs.types.commands");
            case CONTAINERS -> L("gui.logs.types.containers");
            case BLOCKS -> L("gui.logs.types.blocks");
            case SESSIONS -> L("gui.logs.types.sessions");
            default -> "?";
        };
    }

    private List<String[]> fetchLogs(GuiState state, int offset, int limit) {
        String name = state.targetPlayer();
        int fetch = offset + limit;
        return switch (state.logType()) {
            case COMMANDS -> slice(plugin.getDb().getCommands(name, fetch), offset);
            case CONTAINERS -> slice(plugin.getDb().getContainers(name, fetch), offset);
            case BLOCKS -> slice(plugin.getDb().getBlocks(name, fetch), offset);
            case SESSIONS -> slice(plugin.getDb().getSessions(name, fetch), offset);
            default -> List.of();
        };
    }

    private List<String[]> slice(List<String[]> all, int offset) {
        if (offset >= all.size()) return List.of();
        return all.subList(offset, Math.min(offset + LOGS_PER_PAGE + 1, all.size()));
    }

    private boolean isOpen(Player admin, GuiState expected) {
        if (!(admin.getOpenInventory().getTopInventory().getHolder() instanceof GuiHolder holder)) {
            return false;
        }
        GuiState current = holder.state();
        return current.screen() == expected.screen()
            && Objects.equals(current.targetPlayer(), expected.targetPlayer())
            && current.page() == expected.page()
            && current.logType() == expected.logType();
    }

    private Material resolveMaterial(String typeName, boolean container) {
        Material mat = Material.matchMaterial(typeName);
        if (mat != null) return mat;
        try {
            return Material.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            return container ? Material.CHEST : Material.STONE;
        }
    }

    private String cleanPlayerName(String displayName) {
        return stripColor(displayName).replace(" ★", "").trim();
    }

    // ── Items ───────────────────────────────────────────────────────────────

    private ItemStack logItem(GuiState.LogType type, String[] row) {
        return switch (type) {
            case COMMANDS -> {
                boolean alert = row.length > 7 && "1".equals(row[7]);
                yield item(alert ? Material.REDSTONE_TORCH : Material.PAPER,
                    "§7" + row[0] + (alert ? " §c[ALERT]" : ""),
                    "§f" + row[6],
                    "§8" + row[2] + " [" + row[3] + "," + row[4] + "," + row[5] + "]");
            }
            case CONTAINERS -> {
                Material mat = "WYJĄŁ".equals(row[2]) ? Material.HOPPER : Material.CHEST;
                String itemLine = row.length > 8 && row[8] != null ? " §f" + row[8] + " x" + row[9] : "";
                yield item(mat, "§7" + row[0] + " §e" + row[2] + itemLine,
                    "§8" + row[7] + " @ " + row[3] + " " + row[4] + "," + row[5] + "," + row[6]);
            }
            case BLOCKS -> item(Material.GRASS_BLOCK, "§7" + row[0] + " §f" + row[2] + " " + row[7],
                "§8" + row[3] + " [" + row[4] + "," + row[5] + "," + row[6] + "]");
            case SESSIONS -> item(Material.OAK_DOOR, "§7" + row[0] + " §b" + row[2],
                "§7IP: §f" + (row[3] != null ? row[3] : "?"),
                "§8" + row[4] + " [" + row[5] + "," + row[6] + "," + row[7] + "]");
            default -> item(Material.BARRIER, "?", "§7?");
        };
    }

    private ItemStack playerHead(String name, boolean online, boolean watched) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) meta.setOwningPlayer(p);
        else meta.setOwner(name);
        meta.setDisplayName((online ? "§a" : "§7") + name + (watched ? " §e★" : ""));
        List<String> lore = new ArrayList<>();
        lore.add(online ? L("gui.players.online") : L("gui.players.offline"));
        if (watched) lore.add(L("gui.players.watched"));
        lore.add("");
        lore.add(L("gui.players.open-profile"));
        meta.setLore(lore);
        skull.setItemMeta(meta);
        return skull;
    }

    private ItemStack toggleItem(String name, boolean on, String configPath) {
        Material mat = on ? Material.LIME_DYE : Material.GRAY_DYE;
        return item(mat, name,
            "§8" + configPath,
            "",
            on ? L("gui.monitoring.enabled") : L("gui.monitoring.disabled"),
            L("gui.monitoring.toggle"));
    }

    /** Nawigacja dolnego rzędu — slot 48 zawsze = Wróć */
    private void fillNav(Inventory inv, boolean hasNext, boolean hasPrev, String backLabel) {
        for (int slot = 45; slot <= 53; slot++) {
            if (slot == 45 || slot == 48 || slot == 53) continue;
            inv.setItem(slot, glassPane());
        }
        if (hasPrev) inv.setItem(45, item(Material.ARROW, L("gui.nav.prev")));
        inv.setItem(48, backItem(backLabel));
        if (hasNext) inv.setItem(53, item(Material.ARROW, L("gui.nav.next")));
    }

    private ItemStack glassPane() {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }

    private ItemStack backItem(String label) {
        return item(Material.BARRIER, L("gui.nav.back"), label);
    }

    private void fillBorder(Inventory inv, Material glass) {
        ItemStack pane = new ItemStack(glass);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        for (int i = 0; i < 9; i++) inv.setItem(i, pane);
        for (int i = 45; i < 54; i++) inv.setItem(i, pane);
        inv.setItem(9, pane);
        inv.setItem(17, pane);
        inv.setItem(36, pane);
    }

    private ItemStack item(Material mat, String name, String... lore) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(Arrays.asList(lore));
        stack.setItemMeta(meta);
        return stack;
    }

    private String title(String raw) {
        return raw.length() > 32 ? raw.substring(0, 32) : raw;
    }

    private String stripColor(String s) {
        return s.replaceAll("§[0-9a-fk-or]", "");
    }
}
