package nl.robin.rolbeheer.spelers;

import nl.robin.rolbeheer.RolBeheer;
import nl.robin.rolbeheer.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Voor commands die je in RolBeheer hebt uitgezet: het verzoek gaat door naar de andere
 * plugin die dit command levert. Zo blijft bijvoorbeeld /warp van je warp-plugin werken.
 */
public final class ForwardExecutor implements TabExecutor {

    private final RolBeheer plugin;
    private final String name;

    public ForwardExecutor(RolBeheer plugin, String name) {
        this.plugin = plugin;
        this.name = name;
    }

    /** Zoekt hetzelfde command bij een andere plugin, via zijn naam-met-dubbele-punt. */
    private Command other() {
        Command mine = plugin.getCommand(name);
        for (Map.Entry<String, Command> entry : Bukkit.getCommandMap().getKnownCommands().entrySet()) {
            if (entry.getValue() != mine && entry.getKey().endsWith(":" + name)) return entry.getValue();
        }
        return null;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        Command target = other();
        if (target == null) {
            Text.send(sender, "<red>Dit command staat uit in RolBeheer en geen andere plugin levert het.");
            return true;
        }
        return target.execute(sender, label, args);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        Command target = other();
        return target == null ? List.of() : target.tabComplete(sender, label, args);
    }
}
