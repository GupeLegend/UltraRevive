package mc.gupe.revive.listener;

import mc.gupe.revive.UltraRevive;
import mc.gupe.revive.config.Settings;
import mc.gupe.revive.model.Downed;
import mc.gupe.revive.revive.ReviveManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Sends the resource pack on join; dying while downed on quit (anti combat-log). */
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
        if (plugin.pack() == null) {
            return;
        }
        // Small delay: the client needs to be fully connected before it will accept the pack.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (e.getPlayer().isOnline()) {
                plugin.pack().sendTo(e.getPlayer());
            }
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (!manager.isDowned(p.getUniqueId())) {
            return;
        }
        Downed d = manager.downedMap().get(p.getUniqueId());
        manager.cleanup(d);
        if (cfg.dieOnQuit) {
            p.setHealth(0.0);
        }
    }
}
