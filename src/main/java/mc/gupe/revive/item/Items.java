package mc.gupe.revive.item;

import mc.gupe.revive.UltraRevive;
import mc.gupe.revive.config.Settings;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** Builds and recognises the plugin's three custom totems. */
public final class Items {

    public static final String SELF = "self";
    public static final String BOOST = "boost";
    public static final String END = "end";

    private final UltraRevive plugin;
    private final NamespacedKey key;
    private static final NamespacedKey LEGACY_KEY = LegacyKeys.key("ur_item");

    public Items(UltraRevive plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "ur_item");
    }

    /**
     * Returns "self", "boost", "end" or null depending on the item's hidden tag.
     *
     * <p>Also checks the legacy tag ({@code ultrarevivir:ur_item}): totems players already own
     * were tagged before the plugin got its current name. See {@link LegacyKeys}.
     */
    public String tagOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return LegacyKeys.get(meta.getPersistentDataContainer(), key, LEGACY_KEY, PersistentDataType.STRING);
    }

    public boolean isSelf(ItemStack item) {
        return SELF.equals(tagOf(item));
    }

    public boolean isBoost(ItemStack item) {
        return BOOST.equals(tagOf(item));
    }

    public boolean isEnd(ItemStack item) {
        return END.equals(tagOf(item));
    }

    /** Builds the item as configured (material, name, lore, glow...). */
    public ItemStack build(String type, int amount) {
        Settings.ItemDef def = type.equals(BOOST) ? plugin.settings().boostItem
                : type.equals(END) ? plugin.settings().endItem
                : plugin.settings().selfItem;
        ItemStack item = new ItemStack(def.material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.lang().get(def.langKey + "-name"));
            List<String> lore = plugin.lang().getList(def.langKey + "-lore");
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            if (def.customModelData > 0) {
                meta.setCustomModelData(def.customModelData);
            }
            if (def.glow) {
                // Enchantment-less glint (Paper API); if it's missing, the item just won't glow.
                try {
                    meta.setEnchantmentGlintOverride(true);
                } catch (Throwable ignored) {
                }
            }
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, type);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** True if the player has that item anywhere in their inventory (including off-hand). */
    public boolean hasInInventory(Player p, String tag) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && tag.equals(tagOf(it))) {
                return true;
            }
        }
        return false;
    }

    /** Consumes one of that item from the inventory (the first one found). */
    public void consumeFromInventory(Player p, String tag) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it != null && tag.equals(tagOf(it))) {
                int amt = it.getAmount() - 1;
                if (amt <= 0) {
                    p.getInventory().setItem(i, null);
                } else {
                    it.setAmount(amt);
                }
                return;
            }
        }
    }
}
