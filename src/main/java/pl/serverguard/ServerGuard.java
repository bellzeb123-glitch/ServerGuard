package pl.serverguard;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import pl.serverguard.commands.SGCommand;
import pl.serverguard.commands.SGHistoryCommand;
import pl.serverguard.commands.SGSearchCommand;
import pl.serverguard.listeners.*;
import pl.serverguard.managers.AdminAuditManager;
import pl.serverguard.managers.AlertManager;
import pl.serverguard.managers.DatabaseManager;

public class ServerGuard extends JavaPlugin {

    private static ServerGuard instance;
    private DatabaseManager db;
    private AlertManager alertManager;
    private AdminAuditManager adminAudit;
    private PlayerListener playerListener;
    private BukkitTask retentionTask;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        db = new DatabaseManager(this);
        db.initialize();

        alertManager = new AlertManager(this);
        adminAudit = new AdminAuditManager(this);

        getServer().getPluginManager().registerEvents(new CommandListener(this), this);
        getServer().getPluginManager().registerEvents(new ContainerListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockListener(this), this);
        playerListener = new PlayerListener(this);
        getServer().getPluginManager().registerEvents(playerListener, this);
        getServer().getPluginManager().registerEvents(new ModerationListener(this), this);
        getServer().getPluginManager().registerEvents(new ConsoleCommandListener(this), this);

        getCommand("sg").setExecutor(new SGCommand(this));
        getCommand("sghistory").setExecutor(new SGHistoryCommand(this));
        getCommand("sgsearch").setExecutor(new SGSearchCommand(this));

        scheduleRetention();

        getLogger().info("ServerGuard v2 aktywny. Bufor: "
            + getConfig().getInt("max-buffer-size", 500)
            + " wpisów, flush co "
            + getConfig().getLong("flush-interval", 5) + "s.");
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
        adminAudit.reload();
        alertManager.reload();
        playerListener.reloadCauses();
        scheduleRetention();
    }

    public static ServerGuard getInstance() { return instance; }
    public DatabaseManager getDb()          { return db; }
    public AlertManager getAlertManager()   { return alertManager; }
    public AdminAuditManager getAdminAudit() { return adminAudit; }
}
