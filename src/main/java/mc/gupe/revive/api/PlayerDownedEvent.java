package mc.gupe.revive.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Se dispara justo DESPUÉS de que un jugador queda derribado (no muere: se desangra). */
public final class PlayerDownedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final int durationTicks;

    public PlayerDownedEvent(Player player, int durationTicks) {
        this.player = player;
        this.durationTicks = durationTicks;
    }

    /** El jugador que quedó derribado. */
    public Player getPlayer() { return player; }

    /** Cuánto aguanta antes de desangrarse, en ticks (ya con la fatiga por derribos aplicada). */
    public int getDurationTicks() { return durationTicks; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
