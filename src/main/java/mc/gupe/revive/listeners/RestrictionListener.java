package mc.gupe.revive.listeners;

import mc.gupe.revive.Downed;
import mc.gupe.revive.UltraRevive;
import mc.gupe.revive.ReviveManager;
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
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Mientras estas derribado no puedes hacer casi nada (romper, colocar, usar, soltar, comer). */
public final class RestrictionListener implements Listener {

    private final UltraRevive plugin;
    private final ReviveManager manager;

    public RestrictionListener(UltraRevive plugin) {
        this.plugin = plugin;
        this.manager = plugin.manager();
    }

    private boolean down(Player p) {
        return manager.isDowned(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (down(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (down(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!down(p)) return;
        // Por defecto, un derribado no puede usar NADA.
        e.setCancelled(true);

        // Excepcion: el Totem de los Dioses (auto-revivirse con click derecho).
        if (!plugin.settings().selfItemEnabled) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = e.getItem();
        if (item == null || !plugin.items().isSelf(item)) return;

        Downed d = manager.downedMap().get(p.getUniqueId());
        if (d == null) return;

        // Gasta 1 de la mano usada y se levanta.
        EquipmentSlot hand = e.getHand();
        ItemStack inHand = (hand == EquipmentSlot.OFF_HAND)
                ? p.getInventory().getItemInOffHand()
                : p.getInventory().getItemInMainHand();
        if (inHand != null) inHand.setAmount(inHand.getAmount() - 1);
        manager.selfRevive(d);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (down(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (down(e.getPlayer())) e.setCancelled(true);
    }
}
