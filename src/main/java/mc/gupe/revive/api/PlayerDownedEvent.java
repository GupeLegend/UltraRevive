package mc.gupe.revive.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired right after a player goes down (not dead yet: bleeding out). */
public final class PlayerDownedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final int durationTicks;

    public PlayerDownedEvent(Player player, int durationTicks) {
        this.player = player;
        this.durationTicks = durationTicks;
    }

    /** The player who went down. */
    public Player getPlayer() {
        return player;
    }

    /** How long they can hold on before bleeding out, in ticks (fatigue already applied). */
    public int getDurationTicks() {
        return durationTicks;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
