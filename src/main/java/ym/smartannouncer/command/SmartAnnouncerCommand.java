package ym.smartannouncer.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import ym.smartannouncer.config.ConfigSnapshot;
import ym.smartannouncer.config.model.AnnouncementDefinition;
import ym.smartannouncer.dispatch.MessageDispatcher;
import ym.smartannouncer.service.AnnouncementRegistry;
import ym.smartannouncer.service.AnnouncementService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SmartAnnouncerCommand implements CommandExecutor, TabCompleter {
    private final AnnouncementService announcementService;
    private final AnnouncementRegistry registry;
    private final MessageDispatcher dispatcher;

    public SmartAnnouncerCommand(
        AnnouncementService announcementService,
        AnnouncementRegistry registry,
        MessageDispatcher dispatcher
    ) {
        this.announcementService = announcementService;
        this.registry = registry;
        this.dispatcher = dispatcher;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            dispatcher.sendConfiguredMessage(sender, "command.usage", Map.of("label", label));
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        switch (subCommand) {
            case "reload" -> {
                if (!has(sender, "smartannouncer.reload")) {
                    dispatcher.sendConfiguredMessage(sender, "command.no-permission");
                    return true;
                }
                dispatcher.sendConfiguredMessage(sender, "command.reload-start");
                announcementService.reloadAsync(sender, false);
                return true;
            }
            case "test" -> {
                if (!has(sender, "smartannouncer.test")) {
                    dispatcher.sendConfiguredMessage(sender, "command.no-permission");
                    return true;
                }
                if (args.length < 2) {
                    dispatcher.sendConfiguredMessage(sender, "command.test-usage", Map.of("label", label));
                    return true;
                }
                AnnouncementDefinition announcement = registry.find(args[1]).orElse(null);
                if (announcement == null) {
                    dispatcher.sendConfiguredMessage(sender, "command.test-not-found", Map.of("id", args[1]));
                    return true;
                }
                dispatcher.preview(sender, announcement);
                return true;
            }
            case "list" -> {
                if (!has(sender, "smartannouncer.admin")) {
                    dispatcher.sendConfiguredMessage(sender, "command.no-permission");
                    return true;
                }
                ConfigSnapshot snapshot = registry.snapshot();
                dispatcher.sendConfiguredMessage(sender, "command.list-header", Map.of(
                    "count", String.valueOf(snapshot.announcementsById().size())
                ));
                for (AnnouncementDefinition announcement : snapshot.announcementsById().values()) {
                    dispatcher.sendConfiguredMessage(sender, "command.list-entry", Map.of(
                        "id", announcement.id(),
                        "type", announcement.type().name(),
                        "enabled", String.valueOf(announcement.enabled())
                    ));
                }
                return true;
            }
            default -> {
                dispatcher.sendConfiguredMessage(sender, "command.unknown-subcommand");
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("reload", "test", "list"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("test")) {
            return filter(new ArrayList<>(registry.snapshot().announcementsById().keySet()), args[1]);
        }
        return List.of();
    }

    private boolean has(CommandSender sender, String permission) {
        return sender.hasPermission("smartannouncer.admin") || sender.hasPermission(permission);
    }

    private List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
            .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
            .sorted()
            .toList();
    }
}
