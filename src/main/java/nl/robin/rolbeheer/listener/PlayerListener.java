package nl.robin.rolbeheer.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import nl.robin.rolbeheer.RolBeheer;
import nl.robin.rolbeheer.sync.PlayerSync;
import nl.robin.rolbeheer.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;

public final class PlayerListener implements Listener {

    private final RolBeheer plugin;

    public PlayerListener(RolBeheer plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.roles().updateName(player.getUniqueId(), player.getName());
        plugin.sync().apply(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.sync().remove(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.chatEnabled()) return;
        PlayerSync.Display d = plugin.sync().display(event.getPlayer().getUniqueId());
        if (d == null) return;
        String format = plugin.chatFormat();
        event.renderer((source, displayName, message, viewer) -> {
            // Heeft de rol een naamkleur, dan wint die van kleuren van andere plugins.
            Component name = d.color() == null ? displayName : Component.text(source.getName(), d.color());
            return Text.mm(format,
                    Placeholder.component("prefix", d.prefix()),
                    Placeholder.component("naam", name),
                    Placeholder.component("suffix", d.suffix()),
                    Placeholder.component("bericht", message));
        });
    }

    /** Blokkeert commands zonder eigen permissie als een rol ze verbiedt. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage();
        if (msg.length() < 2 || msg.charAt(0) != '/') return;
        String label = msg.substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        String node = plugin.scanner().virtualNode(label);
        if (node != null && !event.getPlayer().hasPermission(node)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Text.mm(plugin.noAccessMessage()));
        }
    }

    /** Verbergt geblokkeerde commands ook uit tab-aanvulling. */
    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        event.getCommands().removeIf(label -> {
            String node = plugin.scanner().virtualNode(label);
            return node != null && !player.hasPermission(node);
        });
    }
}
