package mc.gupe.revive.api;

import mc.gupe.revive.UltraRevive;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * API PÚBLICA de UltraRevive.
 *
 * ⚠ Este contrato (nombre del metadata, firmas, eventos) es lo único estable de cara afuera. El
 * resto del núcleo puede cambiar entre versiones.
 */
public final class UltraReviveAPI {

    private UltraReviveAPI() {}

    /** Metadata que el núcleo pone en un jugador mientras está derribado (valor: true). */
    public static final String META_DOWNED = "ultrarevive_downed";

    /** Metadata que el núcleo ya usaba para el vanish de staff (convención compartida con TAB). */
    public static final String META_VANISHED = "vanished";

    /** El plugin del núcleo, o null si no está instalado / no está activo. */
    public static UltraRevive plugin() {
        Plugin p = Bukkit.getPluginManager().getPlugin("UltraRevive");
        if (p == null) p = Bukkit.getPluginManager().getPlugin("UltraRevivir");  // nombre anterior
        return (p instanceof UltraRevive && p.isEnabled()) ? (UltraRevive) p : null;
    }

    /** ¿Está el núcleo instalado y activo? */
    public static boolean available() { return plugin() != null; }

    /** Versión del núcleo (ej. "2.0.0"), o null si no está. */
    public static String version() {
        UltraRevive pl = plugin();
        return pl == null ? null : pl.getPluginMeta().getVersion();
    }

    // ESTADO

    /** ¿Ese jugador está DERRIBADO (desangrándose, esperando que lo levanten)? */
    public static boolean isDowned(Player p) {
        return p != null && isDowned(p.getUniqueId());
    }

    /** Igual que {@link #isDowned(Player)} pero por UUID (sirve para offline). */
    public static boolean isDowned(UUID id) {
        UltraRevive pl = plugin();
        return pl != null && id != null && pl.manager().isDowned(id);
    }

    /** ¿Ese jugador está AHORA levantando a alguien (agachado, llenando la barra)? */
    public static boolean isReviving(Player p) {
        UltraRevive pl = plugin();
        return pl != null && p != null && pl.manager().isActivelyReviving(p.getUniqueId());
    }

    /** ¿Ese jugador está en vanish de staff? (lo mismo que lee TAB por metadata). */
    public static boolean isVanished(Player p) {
        if (p == null) return false;
        try {
            for (MetadataValue m : p.getMetadata(META_VANISHED)) if (m.asBoolean()) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * ¿Es un objetivo "normal" para efectos de otros plugins? Falso si está derribado, en vanish,
     * en creativo/espectador o muerto.
     */
    public static boolean isNormalTarget(Player p) {
        if (p == null || !p.isValid() || p.isDead()) return false;
        if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR
                || p.getGameMode() == org.bukkit.GameMode.CREATIVE) return false;
        return !isVanished(p) && !isDowned(p);
    }

    // uso INTERNO del núcleo (mantiene el metadata en sincronía)

    /** Marca/desmarca a un jugador como derribado. Lo llama el propio núcleo, no las extensiones. */
    public static void markDowned(Plugin owner, Player p, boolean downed) {
        if (p == null) return;
        try {
            if (downed) p.setMetadata(META_DOWNED, new FixedMetadataValue(owner, true));
            else p.removeMetadata(META_DOWNED, owner);
        } catch (Throwable ignored) {}
    }
}
