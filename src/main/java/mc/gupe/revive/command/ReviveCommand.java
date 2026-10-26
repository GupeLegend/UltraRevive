package mc.gupe.revive.command;

import mc.gupe.revive.util.Tasks;
import mc.gupe.revive.UltraRevive;
import mc.gupe.revive.config.Lang;
import mc.gupe.revive.item.Items;
import mc.gupe.revive.model.Downed;
import mc.gupe.revive.stats.StatsManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class ReviveCommand implements CommandExecutor, TabCompleter {

    private final UltraRevive plugin;

    public ReviveCommand(UltraRevive plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        Lang lang = plugin.lang();

        // Give up: any downed player can do this (no admin permission needed). Triggered by the
        // [Give Up] chat button.
        if (args.length >= 1 && args[0].equalsIgnoreCase("surrender")) {
            if (s instanceof Player) {
                Player ps = (Player) s;
                Downed d = plugin.manager().downedMap().get(ps.getUniqueId());
                if (d != null) {
                    if (!ps.hasPermission("ultrarevive.surrender")) {
                        ps.sendMessage(lang.pre("surrender-no-permission"));
                    } else {
                        int cd = plugin.settings().surrenderCooldownSecs;
                        // Uses this down's REAL duration (d.totalTicks), not the global one: with
                        // fatigue applied, a fatigued player starts with fewer ticks and would
                        // otherwise skip the cooldown early.
                        int elapsed = (d.totalTicks - d.ticksLeft) / 20;
                        if (cd > 0 && elapsed < cd) {
                            ps.sendMessage(lang.pre("surrender-cooldown", "{secs}", String.valueOf(cd - elapsed)));
                        } else {
                            plugin.manager().surrender(d);
                        }
                    }
                }
            }
            return true;
        }

        // Self-revive: triggered by the self-revive button. Consumes the totem from the inventory.
        if (args.length >= 1 && args[0].equalsIgnoreCase("selfrevive")) {
            if (s instanceof Player) {
                Player ps = (Player) s;
                if (plugin.manager().isDowned(ps.getUniqueId())) {
                    Downed d = plugin.manager().downedMap().get(ps.getUniqueId());
                    if (plugin.settings().selfItemEnabled && plugin.items().hasInInventory(ps, Items.SELF)) {
                        plugin.items().consumeFromInventory(ps, Items.SELF);
                        plugin.manager().selfRevive(d);
                    } else if (plugin.settings().endItemEnabled && plugin.items().hasInInventory(ps, Items.END)) {
                        plugin.items().consumeFromInventory(ps, Items.END);
                        plugin.manager().endSelfRevive(d);
                    } else {
                        ps.sendMessage(lang.pre("selfrevive-no-item"));
                    }
                }
            }
            return true;
        }

        // /ur stats [player] (no player = your own; with player = admin only)
        if (args.length >= 1 && args[0].equalsIgnoreCase("stats")) {
            UUID target;
            String name;
            if (args.length >= 2) {
                if (!s.hasPermission("ultrarevive.admin")) {
                    s.sendMessage(lang.pre("cmd-no-permission"));
                    return true;
                }
                OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
                target = op.getUniqueId();
                name = args[1];
            } else if (s instanceof Player) {
                target = ((Player) s).getUniqueId();
                name = s.getName();
            } else {
                s.sendMessage(lang.pre("cmd-usage"));
                return true;
            }

            StatsManager st = plugin.stats();
            s.sendMessage(lang.pre("stats-header", "{player}", name));
            s.sendMessage(lang.get("stats-downed", "{n}", String.valueOf(st.get(target, "downed"))));
            s.sendMessage(lang.get("stats-revived", "{n}", String.valueOf(st.get(target, "revived"))));
            s.sendMessage(lang.get("stats-revives", "{n}", String.valueOf(st.get(target, "revives"))));
            s.sendMessage(lang.get("stats-self-revives", "{n}", String.valueOf(st.get(target, "self-revives"))));
            s.sendMessage(lang.get("stats-bled-out", "{n}", String.valueOf(st.get(target, "bled-out"))));
            return true;
        }

        if (args.length == 0) {
            sendHelp(s);
            return true;
        }
        if (args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("ayuda")) {
            sendHelp(s);
            return true;
        }

        if (!s.hasPermission("ultrarevive.admin")) {
            s.sendMessage(lang.pre("cmd-no-permission"));
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("reload")) {
            plugin.reload();
            s.sendMessage(lang.pre("cmd-reload"));
            return true;
        }

        // /ur give <self|boost|end> [player] [amount]
        if (sub.equals("give") || sub.equals("dar")) {
            if (args.length < 2) {
                s.sendMessage(lang.pre("cmd-usage"));
                return true;
            }
            String t = args[1].toLowerCase();
            String tag = t.startsWith("self") || t.startsWith("aether") || t.startsWith("jer") ? Items.SELF
                    : t.startsWith("boost") || t.startsWith("nether") || t.startsWith("kit") ? Items.BOOST
                    : t.startsWith("end") ? Items.END : null;
            if (tag == null) {
                s.sendMessage(lang.pre("cmd-usage"));
                return true;
            }

            Player who = (args.length >= 3) ? Bukkit.getPlayerExact(args[2])
                    : (s instanceof Player ? (Player) s : null);
            if (who == null) {
                s.sendMessage(lang.pre("cmd-player-not-found"));
                return true;
            }

            int amount = 1;
            if (args.length >= 4) {
                try {
                    amount = Math.max(1, Integer.parseInt(args[3]));
                } catch (NumberFormatException ignored) {
                }
            }

            ItemStack totems = plugin.items().build(tag, amount);
            Tasks.of(who, () -> who.getInventory().addItem(totems));
            s.sendMessage(lang.pre("cmd-item-given", "{amount}", String.valueOf(amount), "{target}", who.getName()));
            return true;
        }

        if (args.length < 2) {
            s.sendMessage(lang.pre("cmd-usage"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            s.sendMessage(lang.pre("cmd-player-not-found"));
            return true;
        }

        switch (sub) {
            case "revive":
                if (!plugin.manager().isDowned(target.getUniqueId())) {
                    s.sendMessage(lang.pre("cmd-not-downed"));
                    return true;
                }
                // On the target's own thread (on Folia they may be in another region).
                Tasks.of(target, () -> plugin.manager().revive(plugin.manager().downedMap().get(target.getUniqueId()), null));
                s.sendMessage(lang.pre("cmd-revived-by-admin", "{target}", target.getName()));
                return true;
            case "down":
                if (plugin.manager().isDowned(target.getUniqueId())) {
                    s.sendMessage(lang.pre("cmd-already-downed"));
                    return true;
                }
                Tasks.of(target, () -> plugin.manager().down(target));
                s.sendMessage(lang.pre("cmd-downed-by-admin", "{target}", target.getName()));
                return true;
            case "finish":
            case "kill":
                if (!plugin.manager().isDowned(target.getUniqueId())) {
                    s.sendMessage(lang.pre("cmd-not-downed"));
                    return true;
                }
                Tasks.of(target, () -> plugin.manager().bleedOut(plugin.manager().downedMap().get(target.getUniqueId())));
                s.sendMessage(lang.pre("cmd-finished-by-admin", "{target}", target.getName()));
                return true;
            default:
                s.sendMessage(lang.pre("cmd-usage"));
                return true;
        }
    }

    /**
     * Help for THIS plugin only.
     *
     * <p>It used to also list clans, backpacks, the staff panel and moderation, since all of
     * that used to live in the same jar. Those are separate plugins now, so advertising their
     * commands here would send people to an "unknown command" if they don't have them installed.
     */
    private void sendHelp(CommandSender s) {
        boolean admin = s.hasPermission("ultrarevive.admin");
        s.sendMessage(Lang.color("&8&m                                        "));
        s.sendMessage(Lang.color("        &6&lUltraRevive &7- &fCommands"));
        s.sendMessage(Lang.color("&8&m                                        "));

        s.sendMessage(Lang.color("&e▸ Player"));
        s.sendMessage(Lang.color("  &f/ur stats &7[player] &8- how many times you were revived and revived others"));
        s.sendMessage(Lang.color("  &f/ur surrender &8- give up (while downed)"));
        s.sendMessage(Lang.color("  &8While downed you get buttons in chat."));

        if (admin) {
            s.sendMessage(Lang.color("&c▸ Admin"));
            s.sendMessage(Lang.color("  &f/ur reload &8- reload config and languages"));
            s.sendMessage(Lang.color("  &f/ur revive|down|finish <player> &8- manage downed players"));
            s.sendMessage(Lang.color("  &f/ur give <self|boost|end> &7[player] [n] &8- hand out the totems"));
        }

        s.sendMessage(Lang.color("&8&m                                        "));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], Arrays.asList("help", "reload", "revive", "down", "finish", "give", "stats"), out);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            StringUtil.copyPartialMatches(args[1], Arrays.asList("self", "boost", "end"), out);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            StringUtil.copyPartialMatches(args[2], names, out);
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("reload")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            StringUtil.copyPartialMatches(args[1], names, out);
        }
        return out;
    }
}
