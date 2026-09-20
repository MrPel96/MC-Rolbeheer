package nl.robin.rolbeheer.role;

public enum PermState {
    NONE, ALLOW, DENY;

    public PermState next() {
        return switch (this) {
            case NONE -> ALLOW;
            case ALLOW -> DENY;
            case DENY -> NONE;
        };
    }

    public String label() {
        return switch (this) {
            case NONE -> "<gray>Niet ingesteld";
            case ALLOW -> "<green>Toegestaan";
            case DENY -> "<red>Verboden";
        };
    }
}
