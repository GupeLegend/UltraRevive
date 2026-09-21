package mc.gupe.revive;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Cooldowns por categoria y por jugador (en memoria, no persistente). */
public final class Cooldowns {

    private final Map<String, Map<UUID, Long>> map = new HashMap<>();

    /** Intenta usar la accion. */
    public long use(String category, UUID id, long durationMs) {
        if (durationMs <= 0) return 0;
        Map<UUID, Long> m = map.computeIfAbsent(category, k -> new HashMap<>());
        long now = System.currentTimeMillis();
        Long until = m.get(id);
        if (until != null && now < until) return until - now;
        m.put(id, now + durationMs);
        return 0;
    }

    /** Arranca el cooldown sin chequear (para cuando la accion ya se confirmo, ej. tras teleport). */
    public void start(String category, UUID id, long durationMs) {
        if (durationMs <= 0) return;
        map.computeIfAbsent(category, k -> new HashMap<>()).put(id, System.currentTimeMillis() + durationMs);
    }

    /** MS que faltan para que se libere (0 si esta libre). */
    public long remaining(String category, UUID id) {
        Map<UUID, Long> m = map.get(category);
        if (m == null) return 0;
        Long until = m.get(id);
        if (until == null) return 0;
        return Math.max(0, until - System.currentTimeMillis());
    }

    public void clear(String category, UUID id) {
        Map<UUID, Long> m = map.get(category);
        if (m != null) m.remove(id);
    }

    /** Segundos redondeados hacia arriba (para mensajes). */
    public static long toSeconds(long ms) {
        return (ms + 999) / 1000;
    }
}
