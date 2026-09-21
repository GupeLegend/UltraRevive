package mc.gupe.revive.listeners;

import mc.gupe.revive.Downed;
import mc.gupe.revive.UltraRevive;
import mc.gupe.revive.ReviveManager;
import mc.gupe.revive.Settings;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** - Si te desconectas estando derribado, mueres (anti combat-log). */
public final class ConnectionListener implements Listener {

    private final UltraRevive plugin;
    private final ReviveManager manager;
    private final Settings cfg;

    public ConnectionListener(UltraRevive plugin) {
        this.plugin = plugin;
        this.manager = plugin.manager();
        this.cfg = plugin.settings();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // re-aplicar el vanish al reloguear (persistencia) y OCULTAR el mensaje de entrada.
        // vanish: vive en el plugin de staff
        // mensajes de vanish: viven en el plugin de staff

        if (plugin.pack() == null) return;
        // Pequeno retraso: el cliente debe estar bien conectado para aceptar el pack.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (e.getPlayer().isOnline()) plugin.pack().sendTo(e.getPlayer());
        }, 40L);  // 2 s
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        // si está en vanish, ocultar el mensaje de salida (que NO "avise que se salió").
        // mensajes de vanish: viven en el plugin de staff

        if (!manager.isDowned(p.getUniqueId())) return;
        Downed d = manager.downedMap().get(p.getUniqueId());
        manager.cleanup(d);  // quita pose/glow/efectos y lo saca del mapa
        if (cfg.dieOnQuit) p.setHealth(0.0);
    }
}
