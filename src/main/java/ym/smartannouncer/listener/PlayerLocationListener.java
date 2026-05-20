package ym.smartannouncer.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import ym.smartannouncer.service.LocationAnnouncementService;

public final class PlayerLocationListener implements Listener {
    private final LocationAnnouncementService locationAnnouncementService;

    public PlayerLocationListener(LocationAnnouncementService locationAnnouncementService) {
        this.locationAnnouncementService = locationAnnouncementService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        locationAnnouncementService.handleJoin(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        locationAnnouncementService.handleMove(event.getPlayer(), event.getFrom(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        locationAnnouncementService.handleMove(event.getPlayer(), event.getFrom(), event.getTo());
    }
}
