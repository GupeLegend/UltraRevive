package mc.gupe.revive;

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

/** Puente OPCIONAL con LuckPerms (softdepend) para mostrar Rango y Rankup en el menu de equipos. */
public final class LuckHook {

    private final UltraRevive plugin;
    private final boolean available;

    public LuckHook(UltraRevive plugin) {
        this.plugin = plugin;
        this.available = Bukkit.getPluginManager().getPlugin("LuckPerms") != null;
    }

    public boolean available() { return available; }

    /** Precarga (async) los usuarios OFFLINE en LuckPerms y luego ejecuta 'then' en el hilo principal. */
    public void preload(Collection<UUID> ids, Runnable then) {
        if (!available) { then.run(); return; }
        try {
            LuckPerms lp = LuckPermsProvider.get();
            List<CompletableFuture<?>> futures = new ArrayList<>();
            for (UUID id : ids)
                if (lp.getUserManager().getUser(id) == null)  // aun no cargado
                    futures.add(lp.getUserManager().loadUser(id));
            if (futures.isEmpty()) { then.run(); return; }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .whenComplete((v, e) -> Bukkit.getScheduler().runTask(plugin, then));
        } catch (Throwable t) {
            then.run();
        }
    }

    /** Grupo primario (rango) o null. */
    public String rank(UUID id) {
        if (!available) return null;
        try { return rank0(id); } catch (Throwable t) { return null; }
    }

    /** Siguiente grupo en el track "rankup" o null si ya esta al tope / sin track. */
    public String rankup(UUID id) {
        if (!available) return null;
        try { return rankup0(id); } catch (Throwable t) { return null; }
    }

    // ---- estos metodos tocan clases de LuckPerms: solo se llaman si available=true ----

    private String rank0(UUID id) {
        LuckPerms lp = LuckPermsProvider.get();
        User u = lp.getUserManager().getUser(id);
        if (u == null) return null;
        return pretty(u.getPrimaryGroup());
    }

    private String rankup0(UUID id) {
        LuckPerms lp = LuckPermsProvider.get();
        User u = lp.getUserManager().getUser(id);
        if (u == null) return null;
        Track track = lp.getTrackManager().getTrack("rankup");
        if (track == null) return null;
        List<String> groups = track.getGroups();
        int idx = groups.indexOf(u.getPrimaryGroup());
        if (idx < 0 || idx + 1 >= groups.size()) return null;  // tope o fuera del track
        return pretty(groups.get(idx + 1));
    }

    private String pretty(String g) {
        if (g == null || g.isEmpty()) return null;
        return g.substring(0, 1).toUpperCase(Locale.ROOT) + g.substring(1);
    }
}
