package mc.gupe.revive.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-category, per-player cooldowns. In memory only, not persisted. */
public final class Cooldowns {

    private final Map<String, Map<UUID, Long>> map = new HashMap<>();

    /** Tries to use the action; returns the remaining milliseconds, or 0 if it was free. */
    public long use(String category, UUID id, long durationMs) {
        if (durationMs <= 0) {
            return 0;
        }
        Map<UUID, Long> m = map.computeIfAbsent(category, k -> new HashMap<>());
        long now = System.currentTimeMillis();
        Long until = m.get(id);
        if (until != null && now < until) {
            return until - now;
        }
        m.put(id, now + durationMs);
        return 0;
    }

    /** Starts the cooldown without checking it first (the action was already confirmed elsewhere). */
    public void start(String category, UUID id, long durationMs) {
        if (durationMs <= 0) {
            return;
        }
        map.computeIfAbsent(category, k -> new HashMap<>()).put(id, System.currentTimeMillis() + durationMs);
    }

    /** Milliseconds left before it frees up (0 if it's already free). */
    public long remaining(String category, UUID id) {
        Map<UUID, Long> m = map.get(category);
        if (m == null) {
            return 0;
        }
        Long until = m.get(id);
        return until == null ? 0 : Math.max(0, until - System.currentTimeMillis());
    }

    public void clear(String category, UUID id) {
        Map<UUID, Long> m = map.get(category);
        if (m != null) {
            m.remove(id);
        }
    }

    /** Seconds rounded up, for messages. */
    public static long toSeconds(long ms) {
        return (ms + 999) / 1000;
    }
}
