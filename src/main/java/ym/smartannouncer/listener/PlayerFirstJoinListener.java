package ym.smartannouncer.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ym.smartannouncer.service.FirstJoinAnnouncementService;

public final class PlayerFirstJoinListener implements Listener {
    private final FirstJoinAnnouncementService firstJoinAnnouncementService;

    public PlayerFirstJoinListener(FirstJoinAnnouncementService firstJoinAnnouncementService) {
        this.firstJoinAnnouncementService = firstJoinAnnouncementService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        firstJoinAnnouncementService.handleJoin(event.getPlayer(), event.getPlayer().hasPlayedBefore());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        firstJoinAnnouncementService.handleQuit(event.getPlayer().getUniqueId());
    }
}
