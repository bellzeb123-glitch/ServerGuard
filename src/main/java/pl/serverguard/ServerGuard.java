package pl.serverguard;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import pl.serverguard.commands.SGCommand;
import pl.serverguard.commands.SGHistoryCommand;
import pl.serverguard.commands.SGSearchCommand;
import pl.serverguard.config.LangManager;
import pl.serverguard.gui.AdminGuiService;
import pl.serverguard.gui.InvseeService;
import pl.serverguard.integration.BellLandsHook;
import pl.serverguard.listeners.*;
import pl.serverguard.managers.AdminAuditManager;
import pl.serverguard.managers.AlertManager;
import pl.serverguard.managers.ConfigListManager;
import pl.serverguard.managers.DatabaseManager;
import pl.serverguard.managers.WatchManager;

public class ServerGuard extends JavaPlugin {

    private static ServerGuard instance;
    private DatabaseManager db;
    private AlertManager alertManager;
    private AdminAuditManager adminAudit;
    private WatchManager watchManager;
    private ConfigListManager configLists;
    private LangManager lang;
    private AdminGuiService adminGui;
    private InvseeService invsee;
    private PlayerListener playerListener;
    private BellLandsHook bellLandsHook;
    private BukkitTask retentionTask;

    @Override
    public void onEnable() {
        instance = this;
        getServer().getScheduler().runTaskLater(this, this::printBanner, 1L);
        saveDefaultConfig();
        saveResource("lang/pl.yml", false);
        saveResource("lang/en.yml", false);

        lang = new LangManager(this);
        lang.load();

        db = new DatabaseManager(this);
        db.initialize();

        alertManager = new AlertManager(this);
        adminAudit = new AdminAuditManager(this);
        watchManager = new WatchManager(this);
        configLists = new ConfigListManager(this);
        adminGui = new AdminGuiService(this);
        invsee = new InvseeService(this);

        bellLandsHook = new BellLandsHook(this);

        getServer().getPluginManager().registerEvents(new CommandListener(this), this);
        getServer().getPluginManager().registerEvents(new ContainerListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockListener(this, bellLandsHook), this);
        getServer().getPluginManager().registerEvents(new EntityListener(this, bellLandsHook), this);
        playerListener = new PlayerListener(this);
        getServer().getPluginManager().registerEvents(playerListener, this);
        getServer().getPluginManager().registerEvents(new ModerationListener(this), this);
        getServer().getPluginManager().registerEvents(new ConsoleCommandListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new AdminChatListener(this), this);
        getServer().getPluginManager().registerEvents(new InvseeListener(this), this);

        SGCommand sgCommand = new SGCommand(this);
        getCommand("sg").setExecutor(sgCommand);
        getCommand("sg").setTabCompleter(sgCommand);
        getCommand("sghistory").setExecutor(new SGHistoryCommand(this));
        getCommand("sgsearch").setExecutor(new SGSearchCommand(this));

        scheduleRetention();

        if (getServer().getPluginManager().getPlugin("BellHub") != null) {
            try {
                getServer().getServicesManager().register(
                        pl.bell.hub.api.BellModule.class,
                        new pl.serverguard.integration.BellHubModule(this), this,
                        org.bukkit.plugin.ServicePriority.Normal);
                getLogger().info("Zarejestrowano modul logów w panelu BellHub.");
            } catch (Throwable t) {
                getLogger().warning("Nie udalo sie zarejestrowac modulu BellHub: " + t.getMessage());
            }
        }

        getLogger().info("ServerGuard v2.1 aktywny. Panel: /sg gui");
    }

    @Override
    public void onDisable() {
        if (retentionTask != null) retentionTask.cancel();
        if (db != null) db.close();
    }

    public void scheduleRetention() {
        if (retentionTask != null) retentionTask.cancel();

        if (!getConfig().getBoolean("database.retention.enabled", true)) return;

        int days = getConfig().getInt("database.retention.days", 30);
        if (days <= 0) return;

        long intervalTicks = getConfig().getLong("database.retention.interval-hours", 24) * 60L * 60L * 20L;
        long delayTicks = getConfig().getLong("database.retention.initial-delay-minutes", 10) * 60L * 20L;

        retentionTask = getServer().getScheduler().runTaskTimerAsynchronously(
            this,
            () -> db.purgeOldEntries(days),
            delayTicks,
            intervalTicks
        );
    }

    /**
     * Soft-reload config + managers. Never throws to callers (Paper "unexpected error").
     * @return true if fully OK
     */
    public boolean reloadAll() {
        try {
            // #region agent log
            agentDebugLog("B", "ServerGuard.reloadAll:start", "reload begin", "{}");
            // #endregion
            reloadConfig();
            // #region agent log
            agentDebugLog("A", "ServerGuard.reloadAll:afterConfig", "reloadConfig ok", "{}");
            // #endregion
            lang.load();
            // #region agent log
            agentDebugLog("A", "ServerGuard.reloadAll:afterLang", "lang load ok", "{}");
            // #endregion
            adminAudit.reload();
            alertManager.reload();
            watchManager.reload();
            if (playerListener != null) {
                playerListener.reloadCauses();
            }
            scheduleRetention();
            // #region agent log
            agentDebugLog("B", "ServerGuard.reloadAll:ok", "reload success", "{}");
            // #endregion
            return true;
        } catch (Exception e) {
            // #region agent log
            agentDebugLogEx("B", "ServerGuard.reloadAll:fail", "reload failed", e);
            // #endregion
            getLogger().severe("Reload failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // #region agent log
    private void agentDebugLog(String hypothesisId, String location, String message, String dataJson) {
        try {
            String json = "{\"sessionId\":\"1a385b\",\"hypothesisId\":\"" + hypothesisId
                + "\",\"location\":\"" + location + "\",\"message\":\"" + message
                + "\",\"data\":" + dataJson + ",\"timestamp\":" + System.currentTimeMillis() + "}\n";
            getLogger().warning("[DBG-1a385b] " + location + " | " + message + " | " + dataJson);
            java.util.List<java.nio.file.Path> targets = new java.util.ArrayList<>();
            try { targets.add(getDataFolder().toPath().resolve("debug-1a385b.log")); } catch (Throwable ignored) {}
            targets.add(java.nio.file.Path.of("debug-1a385b.log"));
            targets.add(java.nio.file.Path.of("logs/debug-1a385b.log"));
            targets.add(java.nio.file.Path.of("f:/Projekty/debug-1a385b.log"));
            for (java.nio.file.Path p : targets) {
                try {
                    java.nio.file.Path parent = p.getParent();
                    if (parent != null) java.nio.file.Files.createDirectories(parent);
                    java.nio.file.Files.writeString(p, json, java.nio.charset.StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                } catch (Throwable ignored) {}
            }
            try {
                var c = (java.net.HttpURLConnection) java.net.URI.create(
                    "http://127.0.0.1:7409/ingest/f03539fa-a7ee-4936-bf6b-029381ab42f4").toURL().openConnection();
                c.setRequestMethod("POST"); c.setDoOutput(true); c.setConnectTimeout(250); c.setReadTimeout(250);
                c.setRequestProperty("Content-Type", "application/json");
                c.setRequestProperty("X-Debug-Session-Id", "1a385b");
                c.getOutputStream().write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                c.getResponseCode();
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }
    private void agentDebugLogEx(String hypothesisId, String location, String message, Throwable t) {
        String stack = "";
        if (t != null) {
            var sb = new StringBuilder();
            for (int i = 0; i < Math.min(10, t.getStackTrace().length); i++) {
                if (i > 0) sb.append(" | ");
                sb.append(t.getStackTrace()[i].toString());
            }
            stack = sb.toString().replace("\\", "/").replace("\"", "'");
        }
        String ex = t == null ? "null" : t.getClass().getName();
        String msg = t == null || t.getMessage() == null ? "" : t.getMessage().replace("\\", "/").replace("\"", "'");
        agentDebugLog(hypothesisId, location, message,
            "{\"ex\":\"" + ex + "\",\"msg\":\"" + msg + "\",\"stack\":\"" + stack + "\"}");
    }
    // #endregion

    public static ServerGuard getInstance() { return instance; }
    public DatabaseManager getDb()          { return db; }
    public AlertManager getAlertManager()   { return alertManager; }
    public AdminAuditManager getAdminAudit() { return adminAudit; }
    public WatchManager getWatchManager()   { return watchManager; }
    public ConfigListManager getConfigLists() { return configLists; }
    public LangManager getLang()            { return lang; }
    public AdminGuiService getAdminGui()    { return adminGui; }
    public InvseeService getInvsee()        { return invsee; }

    private void printBanner() {
        if (org.bukkit.Bukkit.getPluginManager().getPlugin("ServerGuardPro") != null) return;
        var c = org.bukkit.Bukkit.getConsoleSender();
        c.sendMessage("§r");
        c.sendMessage("§6  ██████╗ ███████╗██╗     ██╗          ");
        c.sendMessage("§6  ██╔══██╗██╔════╝██║     ██║          ");
        c.sendMessage("§6  ██████╔╝█████╗  ██║     ██║          ");
        c.sendMessage("§6  ██╔══██╗██╔══╝  ██║     ██║          ");
        c.sendMessage("§6  ██████╔╝███████╗███████╗███████╗§r§f Guard");
        c.sendMessage("§6  ╚═════╝ ╚══════╝╚══════╝╚══════╝     ");
        c.sendMessage("§r");
        c.sendMessage("§7  Version §f" + getDescription().getVersion() + "  §7│  Author §bBellzeb");
        c.sendMessage("§7  Status  §aFree §7│ §7Server Monitoring & Logging");
        c.sendMessage("§r");
    }
}
