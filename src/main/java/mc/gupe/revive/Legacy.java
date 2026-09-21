package mc.gupe.revive;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Puente con los ítems que la gente YA TIENE de antes de la separación.
 *
 * ⚠ No borrar esta clase aunque parezca que ya no hace falta. Mientras exista un solo tótem de la
 * época del monolito, se sigue necesitando.
 */
public final class Legacy {

    /** El nombre que tenía el plugin cuando todo esto era un solo jar. */
    private static final String VIEJO = "ultrarevivir";

    private Legacy() {}

    /** La misma clave, pero como la escribía el plugin viejo. */
    @SuppressWarnings("deprecation")
    public static NamespacedKey key(String clave) {
        try {
            return new NamespacedKey(VIEJO, clave);
        } catch (Throwable t) {
            return null;  // no debería: el namespace es una constante válida
        }
    }

    /** Lee la clave nueva y, si el ítem viene de antes de la separación, la vieja. */
    public static <P, C> C get(PersistentDataContainer c, NamespacedKey nueva, NamespacedKey vieja,
                               PersistentDataType<P, C> tipo) {
        if (c == null) return null;
        C v = c.get(nueva, tipo);
        if (v == null && vieja != null) v = c.get(vieja, tipo);
        return v;
    }

    /** ¿Tiene la marca, con cualquiera de los dos namespaces? */
    public static boolean has(PersistentDataContainer c, NamespacedKey nueva, NamespacedKey vieja,
                              PersistentDataType<?, ?> tipo) {
        if (c == null) return false;
        return c.has(nueva, tipo) || (vieja != null && c.has(vieja, tipo));
    }
}
