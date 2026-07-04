package pl.serverguard.managers;

/**
 * Niezmienny rekord jednego zdarzenia w buforze.
 * Używa tablic Object[] zamiast osobnych pól żeby zminimalizować
 * liczbę obiektów na stercie (mniej GC pressure).
 */
public record LogEntry(Table table, Object[] values) {

    public enum Table {
        COMMANDS, CONTAINERS, BLOCKS, SESSIONS, ENTITIES
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
        return block(uuid, name, action, world, x, y, z, blockType, null, null, null);
    }

    public static LogEntry block(String uuid, String name, String action,
                                 String world, int x, int y, int z, String blockType,
                                 String claimOwner, Integer claimDist, String playerRole) {
        return new LogEntry(Table.BLOCKS,
            new Object[]{uuid, name, action, world, x, y, z, blockType, claimOwner, claimDist, playerRole});
    }

    public static LogEntry entity(String uuid, String name, String action,
                                  String world, int x, int y, int z, String entityType,
                                  String claimOwner, Integer claimDist, String playerRole) {
        return new LogEntry(Table.ENTITIES,
            new Object[]{uuid, name, action, world, x, y, z, entityType, claimOwner, claimDist, playerRole});
    }

    public static LogEntry session(String uuid, String name, String action,
                                   String ip, String world, double x, double y, double z) {
        return new LogEntry(Table.SESSIONS,
            new Object[]{uuid, name, action, ip, world, x, y, z});
    }
}
