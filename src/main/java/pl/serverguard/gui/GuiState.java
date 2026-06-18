package pl.serverguard.gui;

public record GuiState(
    Screen screen,
    int page,
    String targetPlayer,
    LogType logType
) {
    public enum Screen {
        MAIN, PLAYERS, PLAYER, LOGS, MONITORING, AUDIT_RULES, BLOCK_TYPES, CONTAINER_TYPES
    }

    public enum LogType {
        NONE, COMMANDS, CONTAINERS, BLOCKS, SESSIONS
    }

    public static GuiState main() {
        return new GuiState(Screen.MAIN, 0, null, LogType.NONE);
    }

    public GuiState withPage(int newPage) {
        return new GuiState(screen, newPage, targetPlayer, logType);
    }

    public GuiState withScreen(Screen newScreen) {
        return new GuiState(newScreen, 0, targetPlayer, logType);
    }

    public GuiState withPlayer(String player) {
        return new GuiState(Screen.PLAYER, 0, player, LogType.NONE);
    }

    public GuiState withLogs(LogType type) {
        return new GuiState(Screen.LOGS, 0, targetPlayer, type);
    }

    public GuiState withLogsPage(LogType type, int newPage) {
        return new GuiState(Screen.LOGS, newPage, targetPlayer, type);
    }
}
