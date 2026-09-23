package nl.robin.rolbeheer.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Text {

    public static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String DEFAULT_PREFIX = "<dark_gray>[<gold>Blueprint</gold>]</dark_gray>";

    /** Het naampje voor berichten van de plugin; in te stellen in config.yml. */
    private static volatile Component prefix = MM.deserialize(DEFAULT_PREFIX);

    public static void setPrefix(String raw) {
        prefix = raw == null || raw.isBlank() ? Component.empty() : parse(raw);
    }

    private Text() {}

    /**
     * Prefix/suffix-tekst omzetten. Ondersteunt &-kleurcodes (&c), §-codes en MiniMessage (<red>),
     * ook door elkaar heen: de codes worden eerst vertaald naar MiniMessage-tags.
     */
    public static Component parse(String input) {
        if (input == null || input.isBlank()) return Component.empty();
        return MM.deserialize(codesToTags(input));
    }

    private static final Map<Character, String> CODES = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"), Map.entry('2', "dark_green"),
            Map.entry('3', "dark_aqua"), Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"),
            Map.entry('6', "gold"), Map.entry('7', "gray"), Map.entry('8', "dark_gray"),
            Map.entry('9', "blue"), Map.entry('a', "green"), Map.entry('b', "aqua"),
            Map.entry('c', "red"), Map.entry('d', "light_purple"), Map.entry('e', "yellow"),
            Map.entry('f', "white"), Map.entry('k', "obfuscated"), Map.entry('l', "bold"),
            Map.entry('m', "strikethrough"), Map.entry('n', "underlined"), Map.entry('o', "italic"),
            Map.entry('r', "reset"));

    /** &c en §c worden <red>, zodat beide schrijfwijzen samen kunnen bestaan. */
    public static String codesToTags(String input) {
        StringBuilder out = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < input.length()) {
                String tag = CODES.get(Character.toLowerCase(input.charAt(i + 1)));
                if (tag != null) {
                    out.append('<').append(tag).append('>');
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    public static Component mm(String mini, TagResolver... resolvers) {
        return MM.deserialize(mini, resolvers);
    }

    public static void send(CommandSender to, String mini, TagResolver... resolvers) {
        Component body = MM.deserialize("<gray>" + mini, resolvers);
        to.sendMessage(prefix == Component.empty() || plain(prefix).isEmpty()
                ? body
                : Component.textOfChildren(prefix, Component.space(), body));
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
