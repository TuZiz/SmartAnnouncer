package ym.smartannouncer.service;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class OnlinePlayerTracker implements Listener {
    private final ConcurrentMap<UUID, Player> onlinePlayers = new ConcurrentHashMap<>();

    public void track(Player player) {
        onlinePlayers.put(player.getUniqueId(), player);
    }

    public Collection<Player> players() {
        return List.copyOf(onlinePlayers.values());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        track(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        onlinePlayers.remove(event.getPlayer().getUniqueId());
    }
}
