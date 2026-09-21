package mc.gupe.revive.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Se dispara cuando un jugador DEJA de estar derribado, sea como sea que haya terminado. */
public final class PlayerRevivedEvent extends Event {

    /** Cómo terminó el derribo. */
    public enum Cause {
        /** Otro jugador lo levantó agachándose al lado. */
        REVIVED,
        /** Se levantó solo con el Tótem de los Dioses / del End. */
        SELF_REVIVED,
        /** Se le acabó el tiempo y murió de verdad. */
        BLED_OUT,
        /** Apretó [Rendirse] y murió al instante. */
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

    /** El jugador que estaba derribado. */
    public Player getPlayer() { return player; }

    /** Quién lo levantó, o null si se levantó solo / se desangró / se rindió. */
    public Player getReviver() { return reviver; }

    /** Cómo terminó el derribo. */
    public Cause getCause() { return cause; }

    /** Atajo: ¿terminó vivo? (false = se desangró o se rindió). */
    public boolean survived() { return cause == Cause.REVIVED || cause == Cause.SELF_REVIVED; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
