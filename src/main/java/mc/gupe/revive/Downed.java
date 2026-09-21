package mc.gupe.revive;

import org.bukkit.GameMode;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

/** Estado de un jugador DERRIBADO. */
public final class Downed {

    public final Player player;
    public final int totalTicks;  // duracion total (para la barra y la escalada de tension)
    public int ticksLeft;  // ticks restantes antes de desangrarse
    public int reviveTicks;  // progreso de revivir (ticks acumulados)
    public int noReviverTicks;  // ticks seguidos SIN reanimador (para la gracia de reanimacion)
    public BossBar bar;  // puede ser null si la bossbar esta desactivada
    public GameMode prevGamemode;  // modo de juego antes de ser derribado

    public Downed(Player player, int ticksLeft) {
        this.player = player;
        this.totalTicks = ticksLeft;
        this.ticksLeft = ticksLeft;
        this.reviveTicks = 0;
        this.noReviverTicks = 0;
        this.prevGamemode = player.getGameMode();
    }
}
