package pl.serverguard;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import pl.serverguard.commands.SGCommand;
import pl.serverguard.commands.SGHistoryCommand;
import pl.serverguard.commands.SGSearchCommand;
import pl.serverguard.config.LangManager;
import pl.serverguard.gui.AdminGuiService;
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

        getCommand("sg").setExecutor(new SGCommand(this));
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

    public void reloadAll() {
        reloadConfig();
        lang.load();
        adminAudit.reload();
        alertManager.reload();
        watchManager.reload();
        playerListener.reloadCauses();
        scheduleRetention();
    }

    public static ServerGuard getInstance() { return instance; }
    public DatabaseManager getDb()          { return db; }
    public AlertManager getAlertManager()   { return alertManager; }
    public AdminAuditManager getAdminAudit() { return adminAudit; }
    public WatchManager getWatchManager()   { return watchManager; }
    public ConfigListManager getConfigLists() { return configLists; }
    public LangManager getLang()            { return lang; }
    public AdminGuiService getAdminGui()    { return adminGui; }

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
