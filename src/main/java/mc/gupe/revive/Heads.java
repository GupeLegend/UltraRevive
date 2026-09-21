package mc.gupe.revive;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cabezas de jugador para los menús, sin colgar el servidor.
 *
 * ⚠ Nunca llamar a setOwningPlayer con un OfflinePlayer desde un menú. Siempre {@link
 * #apply(SkullMeta, UUID)}.
 */
public final class Heads {

    private final UltraRevive plugin;
    private final Map<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();
    /** A quiénes ya les pedimos el perfil, para no encolar el mismo mil veces. */
    private final Set<UUID> pedidos = ConcurrentHashMap.newKeySet();

    public Heads(UltraRevive plugin) { this.plugin = plugin; }

    /** Precarga (async) los perfiles OFFLINE que falten y después corre then en el hilo principal. */
    public void preload(Collection<UUID> ids, Runnable then) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (UUID id : ids) fetch(id);
            Bukkit.getScheduler().runTask(plugin, then);
        });
    }

    /** Pega la skin a la cabeza. Nunca bloquea. */
    public void apply(SkullMeta sm, UUID id) {
        if (id == null) return;
        Player online = Bukkit.getPlayer(id);
        if (online != null) { sm.setOwningPlayer(online); return; }  // en vivo: gratis

        PlayerProfile prof = cache.get(id);
        if (prof != null && prof.hasTextures()) { sm.setPlayerProfile(prof); return; }

        // No hay nada cacheado: se deja genérica y se pide para la próxima. NO se llama a
        // setOwningPlayer(OfflinePlayer) — ese es justamente el que se va a Mojang y cuelga el tick.
        queue(id);
    }

    /** Igual que {@link #apply(SkullMeta, UUID)} pero para la cabeza PUESTA EN EL MUNDO (la tumba). */
    public void apply(org.bukkit.block.Skull skull, UUID id) {
        if (id == null) return;
        Player online = Bukkit.getPlayer(id);
        if (online != null) { skull.setOwningPlayer(online); return; }
        PlayerProfile prof = cache.get(id);
        if (prof != null && prof.hasTextures()) { skull.setPlayerProfile(prof); return; }
        queue(id);
    }

    /** Pide en segundo plano las caras que faltan, sin callback. */
    public void warm(Collection<UUID> ids) {
        java.util.List<UUID> faltan = new java.util.ArrayList<>();
        for (UUID id : ids) {
            if (id == null || Bukkit.getPlayer(id) != null) continue;
            PlayerProfile have = cache.get(id);
            if (have != null && have.hasTextures()) continue;
            if (pedidos.add(id)) faltan.add(id);
        }
        if (faltan.isEmpty()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> { for (UUID id : faltan) fetch(id); });
    }

    /** Encola el pedido en segundo plano, una sola vez por jugador. */
    private void queue(UUID id) {
        if (!pedidos.add(id)) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> fetch(id));
    }

    /** Pide y cachea el perfil. Corre SIEMPRE fuera del hilo principal. */
    private void fetch(UUID id) {
        if (Bukkit.getPlayer(id) != null) return;  // online: no hace falta
        PlayerProfile have = cache.get(id);
        if (have != null && have.hasTextures()) return;
        pedidos.add(id);
        try {
            PlayerProfile prof = Bukkit.createProfile(id);
            if (prof.complete(true) && prof.hasTextures()) cache.put(id, prof);
        } catch (Throwable ignored) {}
    }
}
