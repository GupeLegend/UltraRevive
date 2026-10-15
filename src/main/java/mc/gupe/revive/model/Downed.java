package mc.gupe.revive.model;

import org.bukkit.GameMode;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

/** State of a player who is currently downed. */
public final class Downed {

    public final Player player;
    public final int totalTicks;
    public int ticksLeft;
    public int reviveTicks;
    /** Consecutive ticks without anyone reviving (used for the revive grace period). */
    public int noReviverTicks;
    /** Null if the boss bar is disabled. */
    public BossBar bar;
    public GameMode prevGamemode;

    public Downed(Player player, int ticksLeft) {
        this.player = player;
        this.totalTicks = ticksLeft;
        this.ticksLeft = ticksLeft;
        this.reviveTicks = 0;
        this.noReviverTicks = 0;
        this.prevGamemode = player.getGameMode();
    }
}
