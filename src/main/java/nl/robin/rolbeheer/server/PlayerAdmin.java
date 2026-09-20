package nl.robin.rolbeheer.server;

import net.kyori.adventure.text.Component;
import nl.robin.rolbeheer.RolBeheer;
import nl.robin.rolbeheer.util.Text;
import nl.robin.rolbeheer.web.ApiException;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Witte lijst, OP's en verbanningen beheren vanuit het webpaneel. */
public final class PlayerAdmin {

    public record Entry(String name, String detail) {}

    private final RolBeheer plugin;

    public PlayerAdmin(RolBeheer plugin) {
        this.plugin = plugin;
    }

    public boolean whitelistEnabled() {
        return Bukkit.hasWhitelist();
    }

    public List<Entry> whitelist() {
        return sorted(Bukkit.getWhitelistedPlayers().stream()
                .map(p -> new Entry(name(p), "")).toList());
    }

    public List<Entry> ops() {
        return sorted(Bukkit.getOperators().stream()
                .map(p -> new Entry(name(p), p.isOnline() ? "online" : "")).toList());
    }

    public List<Entry> bans() {
        List<Entry> list = new ArrayList<>();
        for (OfflinePlayer player : Bukkit.getBannedPlayers()) {
            list.add(new Entry(name(player), reasonFor(name(player))));
        }
        return sorted(list);
    }

    /** Reden ophalen uit de verbanningslijst; leeg als die er niet is. */
    private static String reasonFor(String name) {
        for (BanEntry<?> entry : banEntries()) {
            if (name.equalsIgnoreCase(targetName(entry))) {
                return entry.getReason() == null ? "" : entry.getReason();
            }
        }
        return "";
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static java.util.Set<BanEntry> banEntries() {
        BanList list = Bukkit.getBanList(BanList.Type.PROFILE);
        return list.getEntries();
    }

    private static String targetName(BanEntry<?> entry) {
        Object target = entry.getBanTarget();
        if (target instanceof com.destroystokyo.paper.profile.PlayerProfile profile) return profile.getName();
        if (target instanceof org.bukkit.profile.PlayerProfile profile) return profile.getName();
        return String.valueOf(target);
    }

    public String apply(String action, String input, String reason) {
        String target = input == null ? "" : input.trim();
        if (target.isEmpty()) throw new ApiException("Vul een spelersnaam in.");

        switch (action) {
            case "whitelist-on", "whitelist-off" -> throw new ApiException("Onbekende actie.");
            case "whitelist-add" -> {
                OfflinePlayer p = lookup(target);
                p.setWhitelisted(true);
                return name(p) + " staat nu op de whitelist.";
            }
            case "whitelist-remove" -> {
                OfflinePlayer p = lookup(target);
                p.setWhitelisted(false);
                return name(p) + " staat niet meer op de whitelist.";
            }
            case "op" -> {
                OfflinePlayer p = lookup(target);
                p.setOp(true);
                return name(p) + " is nu operator. Let op: een operator mag standaard alles.";
            }
            case "deop" -> {
                OfflinePlayer p = lookup(target);
                p.setOp(false);
                return name(p) + " is geen operator meer.";
            }
            case "ban" -> {
                OfflinePlayer p = lookup(target);
                String why = reason == null || reason.isBlank() ? "Verbannen door een beheerder" : reason.trim();
                p.ban(why, (java.util.Date) null, "RolBeheer");
                Player online = p.getPlayer();
                if (online != null) online.kick(Text.mm("<red><reden>",
                        net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("reden", why)));
                return name(p) + " is verbannen.";
            }
            case "unban" -> {
                boolean found = false;
                for (BanEntry<?> entry : banEntries()) {
                    if (target.equalsIgnoreCase(targetName(entry))) {
                        entry.remove();
                        found = true;
                    }
                }
                if (!found) throw new ApiException(target + " staat niet in de verbanningslijst.");
                return target + " mag weer meespelen.";
            }
            case "kick" -> {
                Player online = Bukkit.getPlayerExact(target);
                if (online == null) throw new ApiException(target + " is niet online.");
                Component msg = reason == null || reason.isBlank()
                        ? Component.text("Je bent van de server gehaald.")
                        : Component.text(reason.trim());
                online.kick(msg);
                return target + " is van de server gehaald.";
            }
            default -> throw new ApiException("Onbekende actie.");
        }
    }

    public String setWhitelist(boolean on) {
        Bukkit.setWhitelist(on);
        if (on) Bukkit.reloadWhitelist();
        return on ? "De whitelist staat aan: alleen spelers op de lijst komen binnen."
                : "De whitelist staat uit: iedereen kan inloggen.";
    }

    private static OfflinePlayer lookup(String input) {
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) return online;
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(input);
        if (cached == null) {
            throw new ApiException(input + " is nog nooit op de server geweest. Laat de speler eerst een keer inloggen.");
        }
        return cached;
    }

    private static String name(OfflinePlayer player) {
        return player.getName() != null ? player.getName() : player.getUniqueId().toString();
    }

    private static List<Entry> sorted(List<Entry> list) {
        List<Entry> copy = new ArrayList<>(list);
        copy.sort(Comparator.comparing(e -> e.name().toLowerCase()));
        return copy;
    }
}
