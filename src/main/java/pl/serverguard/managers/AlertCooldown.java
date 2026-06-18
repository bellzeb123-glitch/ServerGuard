package pl.serverguard.managers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ogranicza spam alertów — scala powtórzenia w jeden komunikat z licznikiem.
 */
public class AlertCooldown {

    private record Entry(long lastSentMs, int suppressed) {}

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private long cooldownMs = 30_000L;

    public void setCooldownSeconds(int seconds) {
        cooldownMs = Math.max(0, seconds) * 1000L;
    }

    public void clear() {
        entries.clear();
    }

    /**
     * @return true jeśli należy wysłać alert; false jeśli w oknie cooldownu (zliczone jako pominięte)
     */
    public boolean shouldNotify(String key) {
        if (cooldownMs <= 0) return true;

        long now = System.currentTimeMillis();
        Entry entry = entries.get(key);
        if (entry != null && now - entry.lastSentMs < cooldownMs) {
            entries.put(key, new Entry(entry.lastSentMs, entry.suppressed + 1));
            return false;
        }
        return true;
    }

    /** Wywołaj po wysłaniu alertu — zapisuje czas i zeruje licznik pominiętych. */
    public int markSent(String key) {
        long now = System.currentTimeMillis();
        Entry entry = entries.get(key);
        int suppressed = entry != null ? entry.suppressed : 0;
        entries.put(key, new Entry(now, 0));
        return suppressed;
    }
}
