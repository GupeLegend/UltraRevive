package mc.gupe.revive.api;

import mc.gupe.revive.UltraRevive;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * UltraRevive's public API.
 *
 * <p>This contract (metadata name, method signatures, events) is the only thing guaranteed
 * stable to other plugins. Everything else in the core can change between versions.
 */
public final class UltraReviveAPI {

    private UltraReviveAPI() {
    }

    /** Metadata the core sets on a player while they're downed (value: true). */
    public static final String META_DOWNED = "ultrarevive_downed";

    /** Metadata the core reads for staff vanish (shared convention with TAB). */
    public static final String META_VANISHED = "vanished";

    /** The core plugin, or null if it's not installed / not enabled. */
    public static UltraRevive plugin() {
        Plugin p = Bukkit.getPluginManager().getPlugin("UltraRevive");
        if (p == null) {
            p = Bukkit.getPluginManager().getPlugin("UltraRevivir"); // previous name
        }
        return (p instanceof UltraRevive && p.isEnabled()) ? (UltraRevive) p : null;
    }

    /** Is the core installed and enabled? */
    public static boolean available() {
        return plugin() != null;
    }

    /** The core's version (e.g. "1.0"), or null if it's not installed. */
    public static String version() {
        UltraRevive pl = plugin();
        return pl == null ? null : pl.getPluginMeta().getVersion();
    }

    // STATE

    /** Is that player currently DOWNED (bleeding out, waiting to be picked up)? */
    public static boolean isDowned(Player p) {
        return p != null && isDowned(p.getUniqueId());
    }

    /** Same as {@link #isDowned(Player)} but by UUID (works offline). */
    public static boolean isDowned(UUID id) {
        UltraRevive pl = plugin();
        return pl != null && id != null && pl.manager().isDowned(id);
    }

    /** Is that player currently reviving someone (sneaking next to them, filling the bar)? */
    public static boolean isReviving(Player p) {
        UltraRevive pl = plugin();
        return pl != null && p != null && pl.manager().isActivelyReviving(p.getUniqueId());
    }

    /** Is that player in staff vanish? (the same metadata TAB reads). */
    public static boolean isVanished(Player p) {
        if (p == null) {
            return false;
        }
        try {
            for (MetadataValue m : p.getMetadata(META_VANISHED)) {
                if (m.asBoolean()) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * Is this a "normal" target for other plugins' effects? False if downed, vanished,
     * in creative/spectator, or dead.
     */
    public static boolean isNormalTarget(Player p) {
        if (p == null || !p.isValid() || p.isDead()) {
            return false;
        }
        if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR
                || p.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return false;
        }
        return !isVanished(p) && !isDowned(p);
    }

    // internal use (keeps the metadata in sync)

    /** Marks/unmarks a player as downed. Called by the core itself, not by extensions. */
    public static void markDowned(Plugin owner, Player p, boolean downed) {
        if (p == null) {
            return;
        }
        try {
            if (downed) {
                p.setMetadata(META_DOWNED, new FixedMetadataValue(owner, true));
            } else {
                p.removeMetadata(META_DOWNED, owner);
            }
        } catch (Throwable ignored) {
        }
    }
}
