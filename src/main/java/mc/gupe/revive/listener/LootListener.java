package mc.gupe.revive.listener;

import mc.gupe.revive.UltraRevive;
import mc.gupe.revive.config.Settings;
import mc.gupe.revive.item.Items;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.loot.LootTable;

import java.util.Locale;

/** Makes the custom totems appear in the world: in certain structures' chests, and fishing. */
public final class LootListener implements Listener {

    private final UltraRevive plugin;

    public LootListener(UltraRevive plugin) {
        this.plugin = plugin;
    }

    /** Structure chests: adds the item if its loot table matches, and rolls the chance. */
    @EventHandler
    public void onLoot(LootGenerateEvent e) {
        LootTable lt = e.getLootTable();
        if (lt == null || lt.getKey() == null) {
            return;
        }
        String key = lt.getKey().toString().toLowerCase(Locale.ROOT);
        tryChest(e, plugin.settings().selfLoot, Items.SELF, key);
        tryChest(e, plugin.settings().boostLoot, Items.BOOST, key);
        tryChest(e, plugin.settings().endLoot, Items.END, key);
    }

    private void tryChest(LootGenerateEvent e, Settings.LootDef def, String tag, String key) {
        if (def == null || !def.enabled) {
            return;
        }
        double chance = def.chanceFor(key); // each structure has its own chance
        if (chance <= 0.0) {
            return;
        }
        if (def.requireEndShip && !isInEndShip(e)) {
            return;
        }
        if (Math.random() < chance) {
            e.getLoot().add(plugin.items().build(tag, 1));
        }
    }

    /** Is this chest on an End ship, and not elsewhere in the city? */
    private boolean isInEndShip(LootGenerateEvent e) {
        try {
            org.bukkit.Location loc = e.getLootContext() == null ? null : e.getLootContext().getLocation();
            if (loc == null || loc.getWorld() == null) {
                return false;
            }
            for (org.bukkit.entity.Entity ent : loc.getWorld().getNearbyEntities(loc, 14, 14, 14)) {
                if (!(ent instanceof org.bukkit.entity.ItemFrame)) {
                    continue;
                }
                org.bukkit.inventory.ItemStack it = ((org.bukkit.entity.ItemFrame) ent).getItem();
                if (it != null && it.getType() == org.bukkit.Material.ELYTRA) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** Fishing: with a low chance, the catch turns into a totem instead. */
    @EventHandler
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        if (!(e.getCaught() instanceof Item)) {
            return;
        }
        if (tryFish(e, plugin.settings().selfLoot, Items.SELF)) {
            return;
        }
        tryFish(e, plugin.settings().boostLoot, Items.BOOST);
    }

    private boolean tryFish(PlayerFishEvent e, Settings.LootDef def, String tag) {
        if (def == null || !def.fishing || def.fishingChance <= 0.0) {
            return false;
        }
        if (Math.random() >= def.fishingChance) {
            return false;
        }
        ((Item) e.getCaught()).setItemStack(plugin.items().build(tag, 1));
        return true;
    }
}
