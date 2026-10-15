package mc.gupe.revive.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired when a player stops being downed, however that ended. */
public final class PlayerRevivedEvent extends Event {

    /** How the downed state ended. */
    public enum Cause {
        /** Another player picked them up by sneaking next to them. */
        REVIVED,
        /** They got back up on their own with the Aether or End totem. */
        SELF_REVIVED,
        /** Time ran out and they died for real. */
        BLED_OUT,
        /** They clicked [Give Up] and died instantly. */
        SURRENDERED
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Player reviver;
    private final Cause cause;

    public PlayerRevivedEvent(Player player, Player reviver, Cause cause) {
        this.player = player;
        this.reviver = reviver;
        this.cause = cause;
    }

    /** The player who was downed. */
    public Player getPlayer() {
        return player;
    }

    /** Who revived them, or null if they self-revived, bled out, or surrendered. */
    public Player getReviver() {
        return reviver;
    }

    /** How the downed state ended. */
    public Cause getCause() {
        return cause;
    }

    /** Shortcut: did they end up alive? (false = bled out or surrendered). */
    public boolean survived() {
        return cause == Cause.REVIVED || cause == Cause.SELF_REVIVED;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
