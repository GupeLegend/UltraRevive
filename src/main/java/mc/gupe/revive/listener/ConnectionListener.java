package mc.gupe.revive.listener;

import mc.gupe.revive.UltraRevive;
import mc.gupe.revive.config.Settings;
import mc.gupe.revive.model.Downed;
import mc.gupe.revive.revive.ReviveManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** Dying while downed on quit (anti combat-log). */
public final class ConnectionListener implements Listener {

    private final ReviveManager manager;
    private final Settings cfg;

    public ConnectionListener(UltraRevive plugin) {
        this.manager = plugin.manager();
        this.cfg = plugin.settings();
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
