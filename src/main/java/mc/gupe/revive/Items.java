package mc.gupe.revive;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Crea y reconoce los items custom del plugin: - SELF : "Totem de los Dioses" -> auto-revivirte
 * estando derribado.
 */
public final class Items {

    public static final String SELF = "self";
    public static final String BOOST = "boost";
    public static final String END = "end";  // Totem Desconocido del End (soporte/escape)

    private final UltraRevive plugin;
    private final NamespacedKey key;
    /** La misma marca como la escribía el plugin viejo. Ver {@link Legacy}. */
    private static final NamespacedKey KEY_VIEJA = Legacy.key("ur_item");

    public Items(UltraRevive plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "ur_item");
    }

    /**
     * Devuelve "self", "boost", "end", "key" o null segun el tag oculto del item.
     *
     * ⚠ Mira TAMBIEN la marca vieja (ultrarevivir:ur_item): los totems que la gente ya tiene se
     * marcaron cuando el plugin se llamaba UltraRevive. Sin esto quedan como un totem de undying
     * comun. Ver {@link Legacy}.
     */
    public String tagOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        return Legacy.get(meta.getPersistentDataContainer(), key, KEY_VIEJA, PersistentDataType.STRING);
    }

    public boolean isSelf(ItemStack item) { return SELF.equals(tagOf(item)); }
    public boolean isBoost(ItemStack item) { return BOOST.equals(tagOf(item)); }
    public boolean isEnd(ItemStack item) { return END.equals(tagOf(item)); }

    /** Construye el item segun la config (material, nombre, lore, brillo, etc.). */
    public ItemStack build(String type, int amount) {
        Settings.ItemDef def = type.equals(BOOST) ? plugin.settings().boostItem
                : type.equals(END) ? plugin.settings().endItem
                : plugin.settings().selfItem;
        ItemStack item = new ItemStack(def.material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.lang().get(def.langKey + "-name"));
            List<String> lore = plugin.lang().getList(def.langKey + "-lore");
            if (!lore.isEmpty()) meta.setLore(lore);
            if (def.customModelData > 0) meta.setCustomModelData(def.customModelData);
            if (def.glow) {
                // Brillo sin encantamiento (API de Paper). Si no existe, simplemente no brilla.
                try { meta.setEnchantmentGlintOverride(true); } catch (Throwable ignored) {}
            }
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, type);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** True si el jugador tiene ese item EN CUALQUIER PARTE del inventario (incl. off-hand). */
    public boolean hasInInventory(Player p, String tag) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && tag.equals(tagOf(it))) return true;
        }
        return false;
    }

    /** Gasta 1 de ese item del inventario (el primero que encuentre). */
    public void consumeFromInventory(Player p, String tag) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it != null && tag.equals(tagOf(it))) {
                int amt = it.getAmount() - 1;
                if (amt <= 0) p.getInventory().setItem(i, null);
                else it.setAmount(amt);
                return;
            }
        }
    }
}
