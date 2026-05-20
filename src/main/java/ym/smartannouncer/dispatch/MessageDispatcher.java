package ym.smartannouncer.dispatch;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ym.smartannouncer.config.model.AnnouncementDefinition;
import ym.smartannouncer.config.model.AnnouncementMessage;
import ym.smartannouncer.config.model.IntervalAnnouncement;
import ym.smartannouncer.config.model.LocationAnnouncement;
import ym.smartannouncer.config.model.LocationTarget;
import ym.smartannouncer.config.model.MessageOrder;
import ym.smartannouncer.message.MessageRegistry;
import ym.smartannouncer.message.MessageTemplateResolver;
import ym.smartannouncer.platform.PlatformScheduler;
import ym.smartannouncer.service.OnlinePlayerTracker;
import ym.smartannouncer.util.ColorUtil;
import ym.smartannouncer.util.LocationPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

public final class MessageDispatcher {
    private final PlatformScheduler scheduler;
    private final OnlinePlayerTracker onlinePlayerTracker;
    private final AudienceSelector audienceSelector;
    private final MessageRegistry messageRegistry;
    private final Logger logger;

    public MessageDispatcher(
        PlatformScheduler scheduler,
        OnlinePlayerTracker onlinePlayerTracker,
        AudienceSelector audienceSelector,
        MessageRegistry messageRegistry,
        Logger logger
    ) {
        this.scheduler = scheduler;
        this.onlinePlayerTracker = onlinePlayerTracker;
        this.audienceSelector = audienceSelector;
        this.messageRegistry = messageRegistry;
        this.logger = logger;
    }

    public void dispatchToEligiblePlayers(AnnouncementDefinition announcement, List<AnnouncementMessage> messages) {
        for (Player player : onlinePlayerTracker.players()) {
            dispatchToPlayer(announcement, player, messages);
        }
    }

    public void dispatchToPlayer(AnnouncementDefinition announcement, Player player, List<AnnouncementMessage> messages) {
        scheduler.runEntity(player, () -> {
            if (audienceSelector.canReceive(player, announcement)) {
                sendLines(player, announcement, messages);
            }
        });
    }

    public void dispatchLocation(
        LocationAnnouncement announcement,
        Player triggerPlayer,
        Location triggerLocation,
        LocationPoint triggerPoint,
        List<AnnouncementMessage> messages
    ) {
        if (announcement.target() == LocationTarget.PLAYER) {
            scheduler.runEntity(triggerPlayer, () -> {
                if (audienceSelector.canReceive(triggerPlayer, announcement)) {
                    sendLines(triggerPlayer, announcement, messages);
                }
            });
            return;
        }

        /*
         * Region boundary: the location trigger is coordinate-owned, so Folia
         * enters RegionScheduler first. The region task only schedules entity
         * tasks; it does not read Player state or send messages directly.
         */
        scheduler.runRegion(triggerLocation, () -> {
            if (announcement.target() == LocationTarget.NEARBY) {
                dispatchNearbyEntityTasks(announcement, triggerPoint, messages);
            } else {
                dispatchToEligiblePlayers(announcement, messages);
            }
        });
    }

    public void sendSystemMessage(CommandSender sender, String rawMessage) {
        if (sender == null) {
            return;
        }
        String message = messageRegistry.snapshot().systemPrefix() + ColorUtil.color(rawMessage);
        if (sender instanceof Player player) {
            scheduler.runEntity(player, () -> player.sendMessage(message));
        } else {
            scheduler.runGlobal(() -> sender.sendMessage(message));
        }
    }

    public void sendConfiguredMessage(CommandSender sender, String key) {
        sendConfiguredMessage(sender, key, Map.of());
    }

    public void sendConfiguredMessage(CommandSender sender, String key, Map<String, String> placeholders) {
        String template = messageRegistry.snapshot().template(key);
        sendSystemMessage(sender, MessageTemplateResolver.resolve(template, placeholders));
    }

    public void preview(CommandSender sender, AnnouncementDefinition announcement) {
        if (announcement.messages().isEmpty()) {
            sendConfiguredMessage(sender, "preview.no-messages");
            return;
        }
        AnnouncementMessage message = previewMessage(announcement);
        if (sender instanceof Player player) {
            scheduler.runEntity(player, () -> sendLine(player, announcement, message));
        } else {
            scheduler.runGlobal(() -> {
                sender.sendMessage(ColorUtil.strip(announcement.prefix() + message.text()));
                logger.info("Previewed announcement " + announcement.id() + " for console.");
            });
        }
    }

    private void dispatchNearbyEntityTasks(LocationAnnouncement announcement, LocationPoint point, List<AnnouncementMessage> messages) {
        for (Player player : onlinePlayerTracker.players()) {
            scheduler.runEntity(player, () -> {
                if (audienceSelector.canReceive(player, announcement)
                    && audienceSelector.isNear(player, point, announcement.nearbyRadius())) {
                    sendLines(player, announcement, messages);
                }
            });
        }
    }

    private void sendLines(CommandSender sender, AnnouncementDefinition announcement, List<AnnouncementMessage> messages) {
        for (AnnouncementMessage message : messages) {
            sendLine(sender, announcement, message);
        }
    }

    /*
     * Entity-thread-only for Player send. Component construction itself is pure
     * memory work, but Player#spigot().sendMessage must only run after the caller
     * has entered PlatformScheduler#runEntity.
     */
    private void sendLine(CommandSender sender, AnnouncementDefinition announcement, AnnouncementMessage message) {
        if (sender instanceof Player player) {
            player.spigot().sendMessage(toComponents(announcement.prefix(), message));
        } else {
            sender.sendMessage(announcement.prefix() + message.text());
        }
    }

    private BaseComponent[] toComponents(String prefix, AnnouncementMessage message) {
        List<BaseComponent> components = new ArrayList<>();
        for (BaseComponent component : TextComponent.fromLegacyText(prefix)) {
            components.add(component);
        }

        BaseComponent[] messageComponents = TextComponent.fromLegacyText(message.text());
        ClickEvent clickEvent = null;
        if (message.clickable()) {
            try {
                clickEvent = new ClickEvent(ClickEvent.Action.valueOf(message.clickAction().name()), message.clickValue());
            } catch (IllegalArgumentException ignored) {
                logger.warning("Click action is not supported by this server API: " + message.clickAction());
            }
        }

        HoverEvent hoverEvent = null;
        if (message.hoverable()) {
            hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(message.hoverText()));
        }

        for (BaseComponent component : messageComponents) {
            if (clickEvent != null) {
                component.setClickEvent(clickEvent);
            }
            if (hoverEvent != null) {
                component.setHoverEvent(hoverEvent);
            }
            components.add(component);
        }
        return components.toArray(new BaseComponent[0]);
    }

    private AnnouncementMessage previewMessage(AnnouncementDefinition announcement) {
        if (announcement instanceof IntervalAnnouncement interval
            && interval.order() == MessageOrder.RANDOM
            && interval.messages().size() > 1) {
            return interval.messages().get(ThreadLocalRandom.current().nextInt(interval.messages().size()));
        }
        return announcement.messages().get(0);
    }
}
