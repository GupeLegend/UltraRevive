package mc.gupe.revive.listener;

import mc.gupe.revive.util.Tasks;
import mc.gupe.revive.UltraRevive;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/** Keeps the plugin's stats in sync with the server's own (vanilla) statistics. */
public final class StatsListener implements Listener {

    private final UltraRevive plugin;

    public StatsListener(UltraRevive plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.stats().syncFromVanilla(e.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller(); // captured before respawn resets it
        Tasks.of(victim, () -> plugin.stats().syncFromVanilla(victim));
        if (killer != null) {
            Tasks.of(killer, () -> plugin.stats().syncFromVanilla(killer));
        }
    }
}
