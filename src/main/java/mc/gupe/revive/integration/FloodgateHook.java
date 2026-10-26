package mc.gupe.revive.integration;

import mc.gupe.revive.util.Tasks;
import mc.gupe.revive.UltraRevive;
import mc.gupe.revive.config.Lang;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;

/**
 * Optional (softdepend) bridge to Floodgate. Bedrock players (most of them on console) can't click
 * chat buttons, so they get a form with big buttons instead.
 */
public final class FloodgateHook {

    private final UltraRevive plugin;
    private final boolean available;

    public FloodgateHook(UltraRevive plugin) {
        this.plugin = plugin;
        this.available = Bukkit.getPluginManager().getPlugin("floodgate") != null;
    }

    public boolean available() {
        return available;
    }

    public boolean isBedrock(Player p) {
        if (!available) {
            return false;
        }
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(p.getUniqueId());
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * The downed menu: wait, give up and (if a totem is enabled) self-revive. The buttons run the
     * same subcommands as the chat buttons, so permissions and the surrender cooldown still apply.
     */
    public void sendDownedForm(Player p, boolean selfRevive) {
        Lang lang = plugin.lang();
        SimpleForm.Builder form = SimpleForm.builder()
                .title(lang.get("bedrock-form-title"))
                .content(lang.get("bedrock-form-content"))
                .button(lang.get("bedrock-form-wait"))
                .button(lang.get("bedrock-form-surrender"));
        if (selfRevive) {
            form.button(lang.get("bedrock-form-selfrevive"));
        }
        form.validResultHandler(response -> Tasks.of(p, () -> {
            if (!p.isOnline() || !plugin.manager().isDowned(p.getUniqueId())) {
                return;
            }
            switch (response.clickedButtonId()) {
                case 1 -> p.performCommand("ultrarevive surrender");
                case 2 -> p.performCommand("ultrarevive selfrevive");
                default -> p.sendMessage(lang.pre("bedrock-form-hint"));
            }
        }));
        try {
            FloodgateApi.getInstance().sendForm(p.getUniqueId(), form.build());
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not send the Bedrock form to " + p.getName() + ": " + t);
        }
    }
}
