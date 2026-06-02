package pl.serverguard;

import org.bukkit.plugin.java.JavaPlugin;
import pl.serverguard.commands.SGCommand;
import pl.serverguard.commands.SGHistoryCommand;
import pl.serverguard.commands.SGSearchCommand;
import pl.serverguard.listeners.*;
import pl.serverguard.managers.AlertManager;
import pl.serverguard.managers.DatabaseManager;

public class ServerGuard extends JavaPlugin {

    private static ServerGuard instance;
    private DatabaseManager db;
    private AlertManager alertManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        db = new DatabaseManager(this);
        db.initialize();

        alertManager = new AlertManager(this);

        getServer().getPluginManager().registerEvents(new CommandListener(this), this);
        getServer().getPluginManager().registerEvents(new ContainerListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        getCommand("sg").setExecutor(new SGCommand(this));
        getCommand("sghistory").setExecutor(new SGHistoryCommand(this));
        getCommand("sgsearch").setExecutor(new SGSearchCommand(this));

        getLogger().info("ServerGuard v2 aktywny. Bufor: "
            + getConfig().getInt("max-buffer-size", 500)
            + " wpisów, flush co "
            + getConfig().getLong("flush-interval", 5) + "s.");
    }

    @Override
    public void onDisable() {
        if (db != null) db.close(); // poczeka na zapisanie bufora
    }

    public static ServerGuard getInstance() { return instance; }
    public DatabaseManager getDb()          { return db; }
    public AlertManager getAlertManager()   { return alertManager; }
}
