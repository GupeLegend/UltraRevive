package mc.gupe.revive.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import mc.gupe.revive.UltraRevive;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player-head skins for menus, without ever blocking the main thread.
 *
 * <p>Never call {@code setOwningPlayer} with an {@code OfflinePlayer} from a menu; always go
 * through {@link #apply(SkullMeta, UUID)}.
 */
public final class PlayerHeads {

    private final UltraRevive plugin;
    private final Map<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();
    /** Who we already requested a profile for, so we don't queue the same one twice. */
    private final Set<UUID> requested = ConcurrentHashMap.newKeySet();

    public PlayerHeads(UltraRevive plugin) {
        this.plugin = plugin;
    }

    /** Preloads (async) the offline profiles that are missing, then runs {@code then} on the main thread. */
    public void preload(Collection<UUID> ids, Runnable then) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (UUID id : ids) {
                fetch(id);
            }
            Bukkit.getScheduler().runTask(plugin, then);
        });
    }

    /** Applies the skin to the skull. Never blocks. */
    public void apply(SkullMeta sm, UUID id) {
        if (id == null) {
            return;
        }
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            sm.setOwningPlayer(online);
            return;
        }

        PlayerProfile prof = cache.get(id);
        if (prof != null && prof.hasTextures()) {
            sm.setPlayerProfile(prof);
            return;
        }

        // Nothing cached yet: leave it generic and request it for next time. Never call
        // setOwningPlayer(OfflinePlayer) here; that's the one that reaches out to Mojang and
        // stalls the tick.
        queue(id);
    }

    /** Same as {@link #apply(SkullMeta, UUID)} but for a skull placed in the world (a grave). */
    public void apply(org.bukkit.block.Skull skull, UUID id) {
        if (id == null) {
            return;
        }
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            skull.setOwningPlayer(online);
            return;
        }
        PlayerProfile prof = cache.get(id);
        if (prof != null && prof.hasTextures()) {
            skull.setPlayerProfile(prof);
            return;
        }
        queue(id);
    }

    /** Requests the missing faces in the background, without a callback. */
    public void warm(Collection<UUID> ids) {
        java.util.List<UUID> missing = new java.util.ArrayList<>();
        for (UUID id : ids) {
            if (id == null || Bukkit.getPlayer(id) != null) {
                continue;
            }
            PlayerProfile have = cache.get(id);
            if (have != null && have.hasTextures()) {
                continue;
            }
            if (requested.add(id)) {
                missing.add(id);
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (UUID id : missing) {
                fetch(id);
            }
        });
    }

    /** Queues the request in the background, once per player. */
    private void queue(UUID id) {
        if (!requested.add(id)) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> fetch(id));
    }

    /** Fetches and caches the profile. Always runs off the main thread. */
    private void fetch(UUID id) {
        if (Bukkit.getPlayer(id) != null) {
            return;
        }
        PlayerProfile have = cache.get(id);
        if (have != null && have.hasTextures()) {
            return;
        }
        requested.add(id);
        try {
            PlayerProfile prof = Bukkit.createProfile(id);
            if (prof.complete(true) && prof.hasTextures()) {
                cache.put(id, prof);
            }
        } catch (Throwable ignored) {
        }
    }
}
