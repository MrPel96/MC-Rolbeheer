package nl.robin.rolbeheer.listener;

import nl.robin.rolbeheer.RolBeheer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;

/** Scant opnieuw zodra er een plugin bijkomt of verdwijnt. */
public final class ServerListener implements Listener {

    private final RolBeheer plugin;

    public ServerListener(RolBeheer plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEnable(PluginEnableEvent event) {
        if (event.getPlugin() != plugin) plugin.scanner().scheduleRescan();
    }

    @EventHandler
    public void onDisable(PluginDisableEvent event) {
        if (event.getPlugin() != plugin) plugin.scanner().scheduleRescan();
    }
}
