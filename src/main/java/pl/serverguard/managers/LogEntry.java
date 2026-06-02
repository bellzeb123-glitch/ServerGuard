package pl.serverguard.managers;

/**
 * Niezmienny rekord jednego zdarzenia w buforze.
 * Używa tablic Object[] zamiast osobnych pól żeby zminimalizować
 * liczbę obiektów na stercie (mniej GC pressure).
 */
public record LogEntry(Table table, Object[] values) {

    public enum Table {
        COMMANDS, CONTAINERS, BLOCKS, SESSIONS
    }

    // Fabryki - czytelne tworzenie wpisów

    public static LogEntry command(String uuid, String name, String world,
                                   double x, double y, double z,
                                   String command, boolean isAlert) {
        return new LogEntry(Table.COMMANDS,
            new Object[]{uuid, name, world, x, y, z, command, isAlert ? 1 : 0});
    }

    public static LogEntry container(String uuid, String name, String action,
                                     String world, int x, int y, int z,
                                     String containerType, String item, int amount) {
        return new LogEntry(Table.CONTAINERS,
            new Object[]{uuid, name, action, world, x, y, z, containerType, item, amount});
    }

    public static LogEntry block(String uuid, String name, String action,
                                 String world, int x, int y, int z, String blockType) {
        return new LogEntry(Table.BLOCKS,
            new Object[]{uuid, name, action, world, x, y, z, blockType});
    }

    public static LogEntry session(String uuid, String name, String action,
                                   String ip, String world, double x, double y, double z) {
        return new LogEntry(Table.SESSIONS,
            new Object[]{uuid, name, action, ip, world, x, y, z});
    }
}
