package ym.smartannouncer.service;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import ym.smartannouncer.config.model.LocationAnnouncement;
import ym.smartannouncer.dispatch.MessageDispatcher;
import ym.smartannouncer.util.LocationPoint;
import ym.smartannouncer.util.RegionUtil;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class LocationAnnouncementService {
    private final AnnouncementRegistry registry;
    private final MessageDispatcher dispatcher;
    private final Logger logger;
    private final Map<UUID, Map<String, Boolean>> insideStates = new ConcurrentHashMap<>();
    private final Map<PlayerAnnouncementKey, Long> cooldownUntilMillis = new ConcurrentHashMap<>();
    private final Map<String, List<LocationAnnouncement>> announcementsByWorld = new ConcurrentHashMap<>();

    public LocationAnnouncementService(AnnouncementRegistry registry, MessageDispatcher dispatcher, Logger logger) {
        this.registry = registry;
        this.dispatcher = dispatcher;
        this.logger = logger;
    }

    public void handleJoin(Player player, Location location) {
        handleLocationChange(player, null, location);
    }

    public void handleMove(Player player, Location from, Location to) {
        if (!RegionUtil.changedBlock(from, to)) {
            return;
        }
        handleLocationChange(player, from, to);
    }

    /*
     * Event-thread boundary: called only from PlayerMove/Teleport/Join handlers.
     * It may read the event Player/Location state that belongs to that event
     * thread, but it never sends messages directly. Dispatching re-enters entity
     * or region schedulers through MessageDispatcher.
     */
    private void handleLocationChange(Player player, Location from, Location to) {
        if (to == null || to.getWorld() == null) {
            return;
        }
        List<LocationAnnouncement> worldAnnouncements = announcementsByWorld.get(to.getWorld().getName());
        if (worldAnnouncements == null || worldAnnouncements.isEmpty()) {
            return;
        }
        boolean debug = registry.snapshot().debug();
        UUID playerId = player.getUniqueId();
        Map<String, Boolean> playerStates = insideStates.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>());
        LocationPoint toPoint = LocationPoint.from(to);
        LocationPoint fromPoint = from != null && from.getWorld() != null ? LocationPoint.from(from) : null;
        int toBlockX = to.getBlockX();
        int toBlockY = to.getBlockY();
        int toBlockZ = to.getBlockZ();

        for (LocationAnnouncement announcement : worldAnnouncements) {
            if (!announcement.enabled()) {
                continue;
            }
            boolean wasInside = playerStates.getOrDefault(announcement.id(), false);
            if (!wasInside && !announcement.shape().intersectsBlock(toBlockX, toBlockY, toBlockZ)) {
                continue;
            }
            boolean isInside = RegionUtil.contains(announcement.shape(), toPoint);
            if (wasInside == isInside) {
                continue;
            }
            playerStates.put(announcement.id(), isInside);
            boolean entering = !wasInside;
            boolean shouldFire = entering
                ? announcement.trigger().firesOnEnter()
                : announcement.trigger().firesOnLeave();
            if (!shouldFire || !tryAcquireCooldown(playerId, announcement)) {
                continue;
            }

            Location triggerLocation = entering || fromPoint == null ? to : from;
            LocationPoint triggerPoint = entering || fromPoint == null ? toPoint : fromPoint;
            dispatcher.dispatchLocation(announcement, player, triggerLocation, triggerPoint, announcement.messages());
            if (debug) {
                logger.info("Location announcement fired: " + announcement.id() + " player=" + player.getName());
            }
        }
    }

    public void rebuildIndex() {
        Map<String, List<LocationAnnouncement>> rebuilt = registry.snapshot().locationAnnouncements().stream()
            .filter(LocationAnnouncement::enabled)
            .collect(Collectors.groupingBy(announcement -> announcement.shape().worldName()));
        announcementsByWorld.clear();
        announcementsByWorld.putAll(rebuilt);
    }

    public void handleQuit(UUID playerId) {
        insideStates.remove(playerId);
        cooldownUntilMillis.keySet().removeIf(key -> key.playerId().equals(playerId));
    }

    public void resetRuntimeState() {
        insideStates.clear();
        cooldownUntilMillis.clear();
    }

    private boolean tryAcquireCooldown(UUID playerId, LocationAnnouncement announcement) {
        long cooldownMillis = announcement.cooldownSeconds() * 1000L;
        if (cooldownMillis <= 0L) {
            return true;
        }
        long now = System.currentTimeMillis();
        PlayerAnnouncementKey key = new PlayerAnnouncementKey(playerId, announcement.id());
        long nextUntil = now + cooldownMillis;
        boolean[] acquired = new boolean[1];
        cooldownUntilMillis.compute(key, (ignored, currentUntil) -> {
            if (currentUntil != null && currentUntil > now) {
                acquired[0] = false;
                return currentUntil;
            }
            acquired[0] = true;
            return nextUntil;
        });
        return acquired[0];
    }

    private record PlayerAnnouncementKey(UUID playerId, String announcementId) {
    }
}
