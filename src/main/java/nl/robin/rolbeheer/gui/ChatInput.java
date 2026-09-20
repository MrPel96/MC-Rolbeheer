package nl.robin.rolbeheer.gui;

import io.papermc.paper.event.player.AsyncChatEvent;
import nl.robin.rolbeheer.RolBeheer;
import nl.robin.rolbeheer.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Vraagt tekst via de chat (bijv. een nieuwe prefix) vanuit het menu. */
public final class ChatInput implements Listener {

    private final RolBeheer plugin;
    private final Map<UUID, Consumer<String>> pending = new ConcurrentHashMap<>();

    public ChatInput(RolBeheer plugin) {
        this.plugin = plugin;
    }

    public void ask(Player player, String question, Consumer<String> callback) {
        pending.put(player.getUniqueId(), callback);
        Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
        Text.send(player, question);
        Text.send(player, "<dark_gray>Typ <white>annuleer</white> om te stoppen.");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Consumer<String> callback = pending.remove(event.getPlayer().getUniqueId());
        if (callback == null) return;
        event.setCancelled(true);
        String text = Text.plain(event.message()).trim();
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (text.equalsIgnoreCase("annuleer")) {
                Text.send(player, "Geannuleerd.");
                return;
            }
            callback.accept(text);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}
