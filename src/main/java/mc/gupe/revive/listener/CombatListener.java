package mc.gupe.revive.listener;

import mc.gupe.revive.UltraRevive;
import mc.gupe.revive.config.Settings;
import mc.gupe.revive.item.Items;
import mc.gupe.revive.revive.ReviveManager;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

/** Damage rules: a lethal hit on a normal player downs them instead of killing them. */
public final class CombatListener implements Listener {

    private final UltraRevive plugin;
    private final ReviveManager manager;
    private final Settings cfg;

    public CombatListener(UltraRevive plugin) {
        this.plugin = plugin;
        this.manager = plugin.manager();
        this.cfg = plugin.settings();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        // A downed player cannot deal damage.
        if (e instanceof EntityDamageByEntityEvent) {
            Entity damager = ((EntityDamageByEntityEvent) e).getDamager();
            Player attacker = resolveAttacker(damager);
            if (attacker != null && manager.isDowned(attacker.getUniqueId())) {
                e.setCancelled(true);
                return;
            }
        }

        if (!(e.getEntity() instanceof Player)) {
            return;
        }
        Player p = (Player) e.getEntity();

        if (manager.isDowned(p.getUniqueId())) {
            e.setCancelled(true); // invulnerable by default
            Player attacker = null;
            if (e instanceof EntityDamageByEntityEvent) {
                attacker = resolveAttacker(((EntityDamageByEntityEvent) e).getDamager());
            }

            if (attacker != null) {
                if (cfg.finishByPlayers) {
                    manager.bleedOut(manager.downedMap().get(p.getUniqueId()));
                }
            } else if (!cfg.immuneToMobs) {
                manager.bleedOut(manager.downedMap().get(p.getUniqueId()));
            }
            return;
        }

        if (cfg.interruptOnDamageEnabled && e.getFinalDamage() > 0
                && manager.isActivelyReviving(p.getUniqueId())) {
            manager.interruptRevive(p);
        }

        // The void / out-of-world damage kills normally (otherwise they'd fall forever, invulnerable).
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
            return;
        }
        // Their death is already scheduled for next tick: let it happen, don't down them again.
        if (manager.isDying(p.getUniqueId())) {
            return;
        }
        if (hasBypass(p)) {
            return;
        }
        if (!cfg.worldEnabled(p.getWorld().getName())) {
            return;
        }
        // Guard against a second lethal hit landing in the same tick on an already-dead player.
        if (p.isDead() || p.getHealth() <= 0.0) {
            return;
        }

        double finalDamage = e.getFinalDamage();
        if (p.getHealth() - finalDamage <= 0.0) {
            // Carrying a totem in hand or off-hand: let vanilla's totem save them normally instead
            // of downing them.
            if (hasTotem(p)) {
                return;
            }

            // Cursed Totem of the Nether curse: carrying it without a real vanilla totem in hand
            // means an actual death, even with the Blessed Totem of the Aether on them too.
            if (cfg.boostItemEnabled && cfg.boostCurseInstaDeath
                    && plugin.items().hasInInventory(p, Items.BOOST)) {
                e.setCancelled(true);
                plugin.lang().chat(p, "nether-curse-death");
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (p.isOnline() && !p.isDead()) {
                        p.setHealth(0.0);
                    }
                });
                return;
            }

            e.setCancelled(true);
            manager.down(p);
        }
    }

    private boolean hasBypass(Player p) {
        if (!p.hasPermission("ultrarevive.bypass")) {
            return false;
        }
        return !(cfg.affectOps && p.isOp());
    }

    /** Only a vanilla totem (without our tag) auto-saves the player. */
    private boolean hasTotem(Player p) {
        return isVanillaTotem(p.getInventory().getItemInMainHand())
                || isVanillaTotem(p.getInventory().getItemInOffHand());
    }

    private boolean isVanillaTotem(ItemStack it) {
        return it != null
                && it.getType() == Material.TOTEM_OF_UNDYING
                && plugin.items().tagOf(it) == null;
    }

    /** Resolves the player who dealt the damage, directly or through a projectile. */
    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        }
        if (damager instanceof Projectile) {
            ProjectileSource src = ((Projectile) damager).getShooter();
            if (src instanceof Player) {
                return (Player) src;
            }
        }
        return null;
    }
}
