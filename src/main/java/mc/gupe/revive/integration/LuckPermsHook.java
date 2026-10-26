package mc.gupe.revive.integration;

import mc.gupe.revive.util.Tasks;
import mc.gupe.revive.UltraRevive;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.track.Track;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Optional (softdepend) bridge to LuckPerms, to show a player's rank and next rank-up. */
public final class LuckPermsHook {

    private final UltraRevive plugin;
    private final boolean available;

    public LuckPermsHook(UltraRevive plugin) {
        this.plugin = plugin;
        this.available = Bukkit.getPluginManager().getPlugin("LuckPerms") != null;
    }

    public boolean available() {
        return available;
    }

    /** Preloads (async) the offline LuckPerms users, then runs {@code then} on the main thread. */
    public void preload(Collection<UUID> ids, Runnable then) {
        if (!available) {
            then.run();
            return;
        }
        try {
            LuckPerms lp = LuckPermsProvider.get();
            List<CompletableFuture<?>> futures = new ArrayList<>();
            for (UUID id : ids) {
                if (lp.getUserManager().getUser(id) == null) {
                    futures.add(lp.getUserManager().loadUser(id));
                }
            }
            if (futures.isEmpty()) {
                then.run();
                return;
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .whenComplete((v, e) -> Tasks.global(then));
        } catch (Throwable t) {
            then.run();
        }
    }

    /** The player's primary group (rank), or null. */
    public String rank(UUID id) {
        if (!available) {
            return null;
        }
        try {
            return rank0(id);
        } catch (Throwable t) {
            return null;
        }
    }

    /** The next group on the "rankup" track, or null if already at the top / no such track. */
    public String rankup(UUID id) {
        if (!available) {
            return null;
        }
        try {
            return rankup0(id);
        } catch (Throwable t) {
            return null;
        }
    }

    // These touch LuckPerms classes directly: only called when available == true.

    private String rank0(UUID id) {
        LuckPerms lp = LuckPermsProvider.get();
        User u = lp.getUserManager().getUser(id);
        return u == null ? null : pretty(u.getPrimaryGroup());
    }

    private String rankup0(UUID id) {
        LuckPerms lp = LuckPermsProvider.get();
        User u = lp.getUserManager().getUser(id);
        if (u == null) {
            return null;
        }
        Track track = lp.getTrackManager().getTrack("rankup");
        if (track == null) {
            return null;
        }
        List<String> groups = track.getGroups();
        int idx = groups.indexOf(u.getPrimaryGroup());
        if (idx < 0 || idx + 1 >= groups.size()) {
            return null;
        }
        return pretty(groups.get(idx + 1));
    }

    private String pretty(String g) {
        if (g == null || g.isEmpty()) {
            return null;
        }
        return g.substring(0, 1).toUpperCase(Locale.ROOT) + g.substring(1);
    }
}
