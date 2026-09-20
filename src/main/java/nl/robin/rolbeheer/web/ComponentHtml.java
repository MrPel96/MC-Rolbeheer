package nl.robin.rolbeheer.web;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Zet een Adventure-component om naar veilige HTML (voor voorbeelden in het webpaneel). */
public final class ComponentHtml {

    private record Fmt(TextColor color, boolean bold, boolean italic, boolean underlined, boolean strike) {}

    private ComponentHtml() {}

    public static String toHtml(Component component) {
        StringBuilder sb = new StringBuilder();
        render(component, new Fmt(null, false, false, false, false), sb);
        return sb.toString();
    }

    private static void render(Component c, Fmt parent, StringBuilder sb) {
        Fmt f = new Fmt(
                c.color() != null ? c.color() : parent.color(),
                state(c, TextDecoration.BOLD, parent.bold()),
                state(c, TextDecoration.ITALIC, parent.italic()),
                state(c, TextDecoration.UNDERLINED, parent.underlined()),
                state(c, TextDecoration.STRIKETHROUGH, parent.strike()));

        if (c instanceof TextComponent tc && !tc.content().isEmpty()) {
            StringBuilder style = new StringBuilder();
            if (f.color() != null) style.append("color:").append(f.color().asHexString()).append(';');
            if (f.bold()) style.append("font-weight:700;");
            if (f.italic()) style.append("font-style:italic;");
            if (f.underlined() || f.strike()) {
                style.append("text-decoration:")
                        .append(f.underlined() ? "underline " : "")
                        .append(f.strike() ? "line-through" : "")
                        .append(';');
            }
            sb.append("<span style=\"").append(style).append("\">").append(escape(tc.content())).append("</span>");
        }
        for (Component child : c.children()) render(child, f, sb);
    }

    private static boolean state(Component c, TextDecoration d, boolean inherited) {
        return switch (c.decoration(d)) {
            case TRUE -> true;
            case FALSE -> false;
            case NOT_SET -> inherited;
        };
    }

    public static String escape(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '&' -> out.append("&amp;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(ch);
            }
        }
        return out.toString();
    }
}
