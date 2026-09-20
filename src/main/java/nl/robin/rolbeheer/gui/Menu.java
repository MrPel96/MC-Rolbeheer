package nl.robin.rolbeheer.gui;

import net.kyori.adventure.text.Component;
import nl.robin.rolbeheer.RolBeheer;
import nl.robin.rolbeheer.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public abstract class Menu implements InventoryHolder {

    protected final RolBeheer plugin;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();
    private Inventory inventory;

    protected Menu(RolBeheer plugin) {
        this.plugin = plugin;
    }

    protected abstract Component title();
    protected abstract int size();
    protected abstract void draw();

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void open(Player player) {
        inventory = Bukkit.createInventory(this, size(), title());
        redraw();
        player.openInventory(inventory);
    }

    public void redraw() {
        inventory.clear();
        actions.clear();
        draw();
    }

    protected void set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        inventory.setItem(slot, item);
        if (action != null) actions.put(slot, action);
    }

    protected void fillRow(int row) {
        for (int i = row * 9; i < row * 9 + 9; i++) {
            if (inventory.getItem(i) == null) inventory.setItem(i, Items.filler());
        }
    }

    /** Ander menu openen, één tick later (veilig binnen een klik-event). */
    protected void go(Player player, Menu next) {
        Bukkit.getScheduler().runTask(plugin, () -> next.open(player));
    }

    void handleClick(InventoryClickEvent event) {
        Consumer<InventoryClickEvent> action = actions.get(event.getRawSlot());
        if (action != null) action.accept(event);
    }
}
