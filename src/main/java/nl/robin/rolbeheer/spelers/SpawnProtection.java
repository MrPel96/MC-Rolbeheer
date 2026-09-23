package nl.robin.rolbeheer.spelers;

import nl.robin.rolbeheer.RolBeheer;
import nl.robin.rolbeheer.util.Text;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Beschermt het gebied rond het spawnpunt van RolBeheer: niet bouwen, niet slopen,
 * geen explosies, en desgewenst geen PvP of mobschade.
 * Wie de permissie rolbeheer.spawnbescherming.bypass heeft, mag alles gewoon.
 */
public final class SpawnProtection implements Listener {

    public static final String BYPASS = "rolbeheer.spawnbescherming.bypass";

    private final RolBeheer plugin;
    private final Map<UUID, Long> lastWarning = new HashMap<>();

    private boolean enabled;
    private int radius;
    private boolean blockPvp;
    private boolean blockMobs;
    private boolean blockInteract;

    public SpawnProtection(RolBeheer plugin) {
        this.plugin = plugin;
        refresh();
    }

    /** Instellingen opnieuw uit config.yml lezen. */
    public void refresh() {
        enabled = plugin.getConfig().getBoolean("commands.spawn.bescherming.ingeschakeld", false);
        radius = plugin.getConfig().getInt("commands.spawn.bescherming.straal", 32);
        blockPvp = plugin.getConfig().getBoolean("commands.spawn.bescherming.geen-pvp", true);
        blockMobs = plugin.getConfig().getBoolean("commands.spawn.bescherming.geen-mobschade", true);
        blockInteract = plugin.getConfig().getBoolean("commands.spawn.bescherming.geen-interactie", false);
    }

    /** Ligt deze plek binnen het beschermde gebied? */
    public boolean isProtected(Location location) {
        if (!enabled || location == null || plugin.store() == null) return false;
        Location spawn = plugin.store().spawn();
        if (spawn == null || spawn.getWorld() == null || !spawn.getWorld().equals(location.getWorld())) return false;
        return Math.abs(spawn.getX() - location.getX()) <= radius
                && Math.abs(spawn.getZ() - location.getZ()) <= radius;
    }

    private boolean blocked(Player player, Location location) {
        return isProtected(location) && !player.hasPermission(BYPASS);
    }

    /** Waarschuwt hooguit één keer per twee seconden, anders spamt het de chat vol. */
    private void warn(Player player, String message) {
        long now = System.currentTimeMillis();
        Long last = lastWarning.get(player.getUniqueId());
        if (last != null && now - last < 2000) return;
        lastWarning.put(player.getUniqueId(), now);
        Text.send(player, message);
    }

    // ---------------------------------------------------------------- bouwen en slopen

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (blocked(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            warn(event.getPlayer(), "<red>Bij de spawn mag je niets slopen.");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (blocked(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            warn(event.getPlayer(), "<red>Bij de spawn mag je niets bouwen.");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (blocked(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            warn(event.getPlayer(), "<red>Bij de spawn mag je niets bouwen.");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (blocked(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            warn(event.getPlayer(), "<red>Bij de spawn mag je niets weghalen.");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHanging(HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player player && blocked(player, event.getEntity().getLocation())) {
            event.setCancelled(true);
            warn(player, "<red>Bij de spawn mag je niets weghalen.");
        } else if (!(event.getRemover() instanceof Player) && isProtected(event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!blockInteract || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Block block = event.getClickedBlock();
        if (!blocked(event.getPlayer(), block.getLocation())) return;
        BlockState state = block.getState();
        String type = block.getType().name().toUpperCase(Locale.ROOT);
        boolean interactive = state instanceof Container
                || type.contains("DOOR") || type.contains("BUTTON") || type.contains("LEVER")
                || type.contains("GATE") || type.contains("ANVIL") || type.contains("BED");
        if (interactive) {
            event.setUseInteractedBlock(Event.Result.DENY);
            warn(event.getPlayer(), "<red>Bij de spawn kun je hier niets mee.");
        }
    }

    // ---------------------------------------------------------------- explosies en schade

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> isProtected(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> isProtected(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !isProtected(victim.getLocation())) return;

        // Wie slaat er? Bij een pijl of drankje telt degene die hem afschoot.
        var damager = event.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            damager = shooter;
        }
        if (damager instanceof Player attacker) {
            if (blockPvp && !attacker.hasPermission(BYPASS)) {
                event.setCancelled(true);
                warn(attacker, "<red>Bij de spawn kun je niet vechten.");
            }
            return;
        }
        if (blockMobs) event.setCancelled(true);
    }
}
