package mc.gupe.revive.listener;

import mc.gupe.revive.UltraRevive;
import mc.gupe.revive.model.Downed;
import mc.gupe.revive.revive.ReviveManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** A downed player can barely do anything: no breaking, placing, using, dropping or eating. */
public final class RestrictionListener implements Listener {

    private final UltraRevive plugin;
    private final ReviveManager manager;
    /** Last time each downed Bedrock player started sneaking, to catch a double sneak. */
    private final Map<UUID, Long> lastSneak = new HashMap<>();

    public RestrictionListener(UltraRevive plugin) {
        this.plugin = plugin;
        this.manager = plugin.manager();
    }

    private boolean down(Player p) {
        return manager.isDowned(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (down(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (down(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    // Not ignoreCancelled: Bukkit fires every RIGHT_CLICK_AIR already cancelled (there's no block to use),
    // so with ignoreCancelled = true the self-revive by right click never ran.
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!down(p)) {
            return;
        }
        e.setCancelled(true);

        // Exception: the Aether and End totems (self-revive on right click, like the chat button).
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = e.getItem();
        boolean self = plugin.settings().selfItemEnabled && plugin.items().isSelf(item);
        boolean end = !self && plugin.settings().endItemEnabled && plugin.items().isEnd(item);
        if (!self && !end) {
            return;
        }

        Downed d = manager.downedMap().get(p.getUniqueId());
        if (d == null) {
            return;
        }

        EquipmentSlot hand = e.getHand();
        ItemStack inHand = (hand == EquipmentSlot.OFF_HAND)
                ? p.getInventory().getItemInOffHand()
                : p.getInventory().getItemInMainHand();
        if (inHand != null) {
            inHand.setAmount(inHand.getAmount() - 1);
        }
        if (self) {
            manager.selfRevive(d);
        } else {
            manager.endSelfRevive(d);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (down(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (down(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    /** Bedrock: sneaking twice in a row while downed reopens the downed menu. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (!e.isSneaking() || !down(p) || !plugin.settings().bedrockForms || !plugin.floodgate().isBedrock(p)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastSneak.put(p.getUniqueId(), now);
        if (last != null && now - last <= 700) {
            lastSneak.remove(p.getUniqueId());
            manager.openBedrockForm(p);
        }
    }
}
