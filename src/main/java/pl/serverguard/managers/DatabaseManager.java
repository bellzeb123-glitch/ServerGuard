package pl.serverguard.managers;

import pl.serverguard.ServerGuard;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class DatabaseManager {

    private final ServerGuard plugin;
    private Connection connection;
    private final String dbType;

    private final BlockingQueue<LogEntry> queue;
    private volatile boolean running = true;
    private Thread writerThread;

    private PreparedStatement psCommand;
    private PreparedStatement psContainer;
    private PreparedStatement psBlock;
    private PreparedStatement psSession;

    public DatabaseManager(ServerGuard plugin) {
        this.plugin = plugin;
        this.dbType = plugin.getConfig().getString("database.type", "sqlite").toLowerCase();
        int maxBuffer = plugin.getConfig().getInt("max-buffer-size", 500);
        this.queue = new ArrayBlockingQueue<>(maxBuffer * 2);
    }

    public void initialize() {
        try {
            connect();
            createTables();
            prepareStatements();
            startWriterThread();
            plugin.getLogger().info("Baza danych gotowa (" + dbType + "). Bufor aktywny.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Blad inicjalizacji bazy danych: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void connect() throws SQLException {
        if (dbType.equals("mysql")) {
            String host = plugin.getConfig().getString("database.mysql.host", "localhost");
            int port    = plugin.getConfig().getInt("database.mysql.port", 3306);
            String db   = plugin.getConfig().getString("database.mysql.database", "serverguard");
            String user = plugin.getConfig().getString("database.mysql.username", "root");
            String pass = plugin.getConfig().getString("database.mysql.password", "");
            String url  = "jdbc:mysql://" + host + ":" + port + "/" + db
                    + "?useSSL=false&autoReconnect=true&characterEncoding=UTF-8"
                    + "&rewriteBatchedStatements=true";
            connection = DriverManager.getConnection(url, user, pass);
        } else {
            String fileName = plugin.getConfig().getString("database.sqlite-file", "serverguard.db");
            File dbFile = new File(plugin.getDataFolder(), fileName);
            plugin.getDataFolder().mkdirs();
            // SQLite - autoCommit zostaje TRUE podczas tworzenia tabel
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            // Optymalizacje SQLite
            try (Statement s = connection.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("PRAGMA synchronous=NORMAL");
                s.execute("PRAGMA cache_size=-32000");
                s.execute("PRAGMA temp_store=MEMORY");
                s.execute("PRAGMA mmap_size=268435456");
                s.execute("PRAGMA wal_autocheckpoint=1000");
            }
        }
        // autoCommit=true na etapie tworzenia tabel - wlaczymy false dopiero przed zapisem
    }

    private void createTables() throws SQLException {
        // SQLite: nie uzywamy AUTOINCREMENT (zbedne i wolniejsze), INTEGER PRIMARY KEY wystarczy
        // MySQL: AUTO_INCREMENT
        String ai = dbType.equals("mysql") ? " AUTO_INCREMENT" : "";

        try (Statement s = connection.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS sg_commands (" +
                "id INTEGER PRIMARY KEY" + ai + "," +
                "ts DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "uuid TEXT NOT NULL," +
                "name TEXT NOT NULL," +
                "world TEXT NOT NULL," +
                "x REAL, y REAL, z REAL," +
                "cmd TEXT NOT NULL," +
                "alert INTEGER DEFAULT 0" +
                ")");

            s.execute("CREATE TABLE IF NOT EXISTS sg_containers (" +
                "id INTEGER PRIMARY KEY" + ai + "," +
                "ts DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "uuid TEXT NOT NULL," +
                "name TEXT NOT NULL," +
                "action TEXT NOT NULL," +
                "world TEXT NOT NULL," +
                "x INTEGER, y INTEGER, z INTEGER," +
                "ctype TEXT NOT NULL," +
                "item TEXT," +
                "amount INTEGER DEFAULT 0" +
                ")");

            s.execute("CREATE TABLE IF NOT EXISTS sg_blocks (" +
                "id INTEGER PRIMARY KEY" + ai + "," +
                "ts DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "uuid TEXT NOT NULL," +
                "name TEXT NOT NULL," +
                "action TEXT NOT NULL," +
                "world TEXT NOT NULL," +
                "x INTEGER, y INTEGER, z INTEGER," +
                "btype TEXT NOT NULL" +
                ")");

            s.execute("CREATE TABLE IF NOT EXISTS sg_sessions (" +
                "id INTEGER PRIMARY KEY" + ai + "," +
                "ts DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "uuid TEXT NOT NULL," +
                "name TEXT NOT NULL," +
                "action TEXT NOT NULL," +
                "ip TEXT," +
                "world TEXT," +
                "x REAL, y REAL, z REAL" +
                ")");

            s.execute("CREATE INDEX IF NOT EXISTS idx_cmd_name  ON sg_commands(name, ts)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_cmd_alert ON sg_commands(alert, ts)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_con_name  ON sg_containers(name, ts)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_con_pos   ON sg_containers(world, x, y, z)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_blk_name  ON sg_blocks(name, ts)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_blk_pos   ON sg_blocks(world, x, y, z)");
        }
        plugin.getLogger().info("Tabele bazy danych gotowe.");
        // Dopiero teraz wlaczamy tryb manualnych transakcji dla szybkiego zapisu
        connection.setAutoCommit(false);
    }

    private void prepareStatements() throws SQLException {
        psCommand = connection.prepareStatement(
            "INSERT INTO sg_commands (uuid,name,world,x,y,z,cmd,alert) VALUES (?,?,?,?,?,?,?,?)");
        psContainer = connection.prepareStatement(
            "INSERT INTO sg_containers (uuid,name,action,world,x,y,z,ctype,item,amount) VALUES (?,?,?,?,?,?,?,?,?,?)");
        psBlock = connection.prepareStatement(
            "INSERT INTO sg_blocks (uuid,name,action,world,x,y,z,btype) VALUES (?,?,?,?,?,?,?,?)");
        psSession = connection.prepareStatement(
            "INSERT INTO sg_sessions (uuid,name,action,ip,world,x,y,z) VALUES (?,?,?,?,?,?,?,?)");
    }

    private void startWriterThread() {
        long intervalMs = plugin.getConfig().getLong("flush-interval", 5) * 1000L;
        int maxBuffer   = plugin.getConfig().getInt("max-buffer-size", 500);

        writerThread = new Thread(() -> {
            List<LogEntry> batch = new ArrayList<>(maxBuffer);
            while (running || !queue.isEmpty()) {
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                queue.drainTo(batch, maxBuffer);
                if (!batch.isEmpty()) {
                    flush(batch);
                    batch.clear();
                }
            }
        }, "ServerGuard-Writer");

        writerThread.setDaemon(true);
        writerThread.setPriority(Thread.MIN_PRIORITY);
        writerThread.start();
    }

    private void flush(List<LogEntry> batch) {
        try {
            for (LogEntry e : batch) {
                Object[] v = e.values();
                switch (e.table()) {
                    case COMMANDS -> {
                        psCommand.setString(1, (String) v[0]);
                        psCommand.setString(2, (String) v[1]);
                        psCommand.setString(3, (String) v[2]);
                        psCommand.setDouble(4, (double) v[3]);
                        psCommand.setDouble(5, (double) v[4]);
                        psCommand.setDouble(6, (double) v[5]);
                        psCommand.setString(7, (String) v[6]);
                        psCommand.setInt(8, (int) v[7]);
                        psCommand.addBatch();
                    }
                    case CONTAINERS -> {
                        psContainer.setString(1, (String) v[0]);
                        psContainer.setString(2, (String) v[1]);
                        psContainer.setString(3, (String) v[2]);
                        psContainer.setString(4, (String) v[3]);
                        psContainer.setInt(5, (int) v[4]);
                        psContainer.setInt(6, (int) v[5]);
                        psContainer.setInt(7, (int) v[6]);
                        psContainer.setString(8, (String) v[7]);
                        psContainer.setString(9, (String) v[8]);
                        psContainer.setInt(10, (int) v[9]);
                        psContainer.addBatch();
                    }
                    case BLOCKS -> {
                        psBlock.setString(1, (String) v[0]);
                        psBlock.setString(2, (String) v[1]);
                        psBlock.setString(3, (String) v[2]);
                        psBlock.setString(4, (String) v[3]);
                        psBlock.setInt(5, (int) v[4]);
                        psBlock.setInt(6, (int) v[5]);
                        psBlock.setInt(7, (int) v[6]);
                        psBlock.setString(8, (String) v[7]);
                        psBlock.addBatch();
                    }
                    case SESSIONS -> {
                        psSession.setString(1, (String) v[0]);
                        psSession.setString(2, (String) v[1]);
                        psSession.setString(3, (String) v[2]);
                        psSession.setString(4, (String) v[3]);
                        psSession.setString(5, (String) v[4]);
                        psSession.setDouble(6, (double) v[5]);
                        psSession.setDouble(7, (double) v[6]);
                        psSession.setDouble(8, (double) v[7]);
                        psSession.addBatch();
                    }
                }
            }
            psCommand.executeBatch();
            psContainer.executeBatch();
            psBlock.executeBatch();
            psSession.executeBatch();
            connection.commit();
        } catch (SQLException e) {
            plugin.getLogger().warning("Blad zapisu batch (" + batch.size() + " wpisow): " + e.getMessage());
            try { connection.rollback(); } catch (SQLException ignored) {}
        }
    }

    public void log(LogEntry entry) {
        int maxBuffer = plugin.getConfig().getInt("max-buffer-size", 500);
        if (!queue.offer(entry)) {
            plugin.getLogger().warning("Bufor pelny! Zwieksz max-buffer-size w config.yml.");
        }
        if (queue.size() >= maxBuffer * 0.8) {
            writerThread.interrupt();
        }
    }

    public List<String[]> getCommands(String name, int limit) {
        return query(
            "SELECT ts,name,world,ROUND(x,1),ROUND(y,1),ROUND(z,1),cmd,alert " +
            "FROM sg_commands WHERE name LIKE ? ORDER BY ts DESC LIMIT ?",
            name, limit);
    }

    public List<String[]> getContainers(String name, int limit) {
        return query(
            "SELECT ts,name,action,world,x,y,z,ctype,item,amount " +
            "FROM sg_containers WHERE name LIKE ? ORDER BY ts DESC LIMIT ?",
            name, limit);
    }

    public List<String[]> getContainersAtPos(String world, int x, int y, int z, int limit) {
        return query(
            "SELECT ts,name,action,ctype,item,amount " +
            "FROM sg_containers WHERE world=? AND x=? AND y=? AND z=? ORDER BY ts DESC LIMIT ?",
            world, x, y, z, limit);
    }

    public List<String[]> getBlocks(String name, int limit) {
        return query(
            "SELECT ts,name,action,world,x,y,z,btype " +
            "FROM sg_blocks WHERE name LIKE ? ORDER BY ts DESC LIMIT ?",
            name, limit);
    }

    public List<String[]> searchAll(String phrase, int limit) {
        List<String[]> out = new ArrayList<>();
        String p = "%" + phrase + "%";
        out.addAll(query(
            "SELECT ts,name,'KOMENDA',cmd FROM sg_commands WHERE name LIKE ? OR cmd LIKE ? ORDER BY ts DESC LIMIT ?",
            p, p, limit));
        out.addAll(query(
            "SELECT ts,name,'KONTENER_'||action, item||' x'||amount||' @ '||world||' '||x||','||y||','||z " +
            "FROM sg_containers WHERE name LIKE ? OR item LIKE ? ORDER BY ts DESC LIMIT ?",
            p, p, limit));
        out.addAll(query(
            "SELECT ts,name,'BLOK_'||action, btype||' @ '||world||' '||x||','||y||','||z " +
            "FROM sg_blocks WHERE name LIKE ? OR btype LIKE ? ORDER BY ts DESC LIMIT ?",
            p, p, limit));
        out.sort((a, b) -> b[0].compareTo(a[0]));
        return out.size() > limit ? out.subList(0, limit) : out;
    }

    private List<String[]> query(String sql, Object... params) {
        List<String[]> rows = new ArrayList<>();
        // Odczyt wymaga autoCommit=true lub osobnego polaczenia - uzywamy tymczasowego
        try (Connection readConn = openReadConnection();
             PreparedStatement ps = readConn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                if (params[i] instanceof String s) ps.setString(i + 1, s);
                else if (params[i] instanceof Integer n) ps.setInt(i + 1, n);
                else if (params[i] instanceof Double d) ps.setDouble(i + 1, d);
            }
            ResultSet rs = ps.executeQuery();
            ResultSetMetaData md = rs.getMetaData();
            int cols = md.getColumnCount();
            while (rs.next()) {
                String[] row = new String[cols];
                for (int i = 0; i < cols; i++) row[i] = rs.getString(i + 1);
                rows.add(row);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Blad zapytania: " + e.getMessage());
        }
        return rows;
    }

    private Connection openReadConnection() throws SQLException {
        if (dbType.equals("mysql")) {
            String host = plugin.getConfig().getString("database.mysql.host", "localhost");
            int port    = plugin.getConfig().getInt("database.mysql.port", 3306);
            String db   = plugin.getConfig().getString("database.mysql.database", "serverguard");
            String user = plugin.getConfig().getString("database.mysql.username", "root");
            String pass = plugin.getConfig().getString("database.mysql.password", "");
            return DriverManager.getConnection(
                "jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=false", user, pass);
        } else {
            String fileName = plugin.getConfig().getString("database.sqlite-file", "serverguard.db");
            File dbFile = new File(plugin.getDataFolder(), fileName);
            return DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        }
    }

    public void close() {
        running = false;
        writerThread.interrupt();
        try { writerThread.join(10_000); } catch (InterruptedException ignored) {}
        List<LogEntry> remaining = new ArrayList<>();
        queue.drainTo(remaining);
        if (!remaining.isEmpty()) flush(remaining);
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException ignored) {}
        plugin.getLogger().info("Baza danych zamknieta. Wszystkie dane zapisane.");
    }
}
