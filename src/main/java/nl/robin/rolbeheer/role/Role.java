package nl.robin.rolbeheer.role;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Role {

    private final String name;
    private String prefix = "";
    private String suffix = "";
    private int priority = 10;
    private String nameColor = "";
    /** Permissies; een "-" ervoor betekent verboden. Ondersteunt * en plugin.* */
    private final List<String> permissions = new ArrayList<>();

    public Role(String name) {
        this.name = name;
    }

    public String getName() { return name; }

    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix == null ? "" : prefix.trim(); }

    public String getSuffix() { return suffix; }
    public void setSuffix(String suffix) { this.suffix = suffix == null ? "" : suffix.trim(); }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = Math.max(0, Math.min(999, priority)); }

    public String getNameColorName() { return nameColor; }
    public void setNameColorName(String color) {
        this.nameColor = color == null ? "" : color.trim().toLowerCase(Locale.ROOT);
    }

    /** Naamkleur, of null als er geen is ingesteld. */
    public NamedTextColor getNameColor() {
        return nameColor.isEmpty() ? null : NamedTextColor.NAMES.value(nameColor);
    }

    public List<String> getPermissions() { return permissions; }

    public PermState state(String node) {
        String n = node.toLowerCase(Locale.ROOT);
        if (permissions.contains(n)) return PermState.ALLOW;
        if (permissions.contains("-" + n)) return PermState.DENY;
        return PermState.NONE;
    }

    public void setState(String node, PermState state) {
        String n = node.toLowerCase(Locale.ROOT);
        if (n.startsWith("-")) {
            n = n.substring(1);
        }
        permissions.remove(n);
        permissions.remove("-" + n);
        if (state == PermState.ALLOW) permissions.add(n);
        else if (state == PermState.DENY) permissions.add("-" + n);
    }
}
