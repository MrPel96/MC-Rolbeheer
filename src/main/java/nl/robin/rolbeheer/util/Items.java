package nl.robin.rolbeheer.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

public final class Items {

    private Items() {}

    public static ItemStack of(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Text.noItalic(name));
            meta.lore(lore.stream().map(Text::noItalic).toList());
        });
        return item;
    }

    public static ItemStack of(Material material, Component name) {
        return of(material, name, List.of());
    }

    public static ItemStack head(OfflinePlayer owner, Component name, List<Component> lore) {
        ItemStack item = of(Material.PLAYER_HEAD, name, lore);
        item.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(owner));
        return item;
    }

    public static ItemStack filler() {
        return of(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
    }
}
