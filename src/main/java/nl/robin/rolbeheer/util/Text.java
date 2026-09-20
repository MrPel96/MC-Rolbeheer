package nl.robin.rolbeheer.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public final class Text {

    public static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String PREFIX = "<dark_gray>[<gold>RolBeheer</gold>]</dark_gray> <gray>";

    private Text() {}

    /** Prefix/suffix-tekst omzetten. Ondersteunt &-kleurcodes (&c) én MiniMessage (<red>). */
    public static Component parse(String input) {
        if (input == null || input.isBlank()) return Component.empty();
        if (input.indexOf('&') >= 0 || input.indexOf('§') >= 0) {
            return LegacyComponentSerializer.legacyAmpersand().deserialize(input.replace('§', '&'));
        }
        return MM.deserialize(input);
    }

    public static Component mm(String mini, TagResolver... resolvers) {
        return MM.deserialize(mini, resolvers);
    }

    public static void send(CommandSender to, String mini, TagResolver... resolvers) {
        to.sendMessage(MM.deserialize(PREFIX + mini, resolvers));
    }

    /** Tekst voor itemnamen/lore (zonder de standaard cursieve opmaak). */
    public static Component item(String mini, TagResolver... resolvers) {
        return noItalic(MM.deserialize(mini, resolvers));
    }

    public static Component noItalic(Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }

    public static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    /** Lange tekst opknippen in lore-regels. */
    public static List<Component> wrap(String text, int width, NamedTextColor color) {
        List<Component> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (!line.isEmpty() && line.length() + word.length() + 1 > width) {
                out.add(noItalic(Component.text(line.toString(), color)));
                line.setLength(0);
            }
            if (!line.isEmpty()) line.append(' ');
            line.append(word);
        }
        if (!line.isEmpty()) out.add(noItalic(Component.text(line.toString(), color)));
        return out;
    }
}
