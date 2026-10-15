package mc.gupe.revive.item;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Bridge for items handed out before the plugin was split out of the old monolith.
 *
 * <p>Do not delete this class even if it looks unused: as long as a single totem from the
 * monolith days is still in a player's inventory, it is still needed.
 */
public final class LegacyKeys {

    /** The plugin's name back when all of this was a single jar. */
    private static final String OLD_PLUGIN_NAME = "ultrarevivir";

    private LegacyKeys() {
    }

    /** The same key, but namespaced the way the old plugin wrote it. */
    @SuppressWarnings("deprecation")
    public static NamespacedKey key(String key) {
        try {
            return new NamespacedKey(OLD_PLUGIN_NAME, key);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Reads the current key, falling back to the legacy one if the item predates the split. */
    public static <P, C> C get(PersistentDataContainer c, NamespacedKey current, NamespacedKey legacy,
                                PersistentDataType<P, C> type) {
        if (c == null) {
            return null;
        }
        C v = c.get(current, type);
        if (v == null && legacy != null) {
            v = c.get(legacy, type);
        }
        return v;
    }

    /** Whether the tag is present under either namespace. */
    public static boolean has(PersistentDataContainer c, NamespacedKey current, NamespacedKey legacy,
                               PersistentDataType<?, ?> type) {
        if (c == null) {
            return false;
        }
        return c.has(current, type) || (legacy != null && c.has(legacy, type));
    }
}
