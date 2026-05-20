package ym.smartannouncer;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ym.smartannouncer.command.SmartAnnouncerCommand;
import ym.smartannouncer.config.ConfigLoader;
import ym.smartannouncer.dispatch.AudienceSelector;
import ym.smartannouncer.dispatch.MessageDispatcher;
import ym.smartannouncer.listener.PlayerLocationListener;
import ym.smartannouncer.listener.PlayerFirstJoinListener;
import ym.smartannouncer.message.MessageConfigLoader;
import ym.smartannouncer.message.MessageRegistry;
import ym.smartannouncer.platform.PlatformDetector;
import ym.smartannouncer.platform.PlatformScheduler;
import ym.smartannouncer.service.AnnouncementRegistry;
import ym.smartannouncer.service.AnnouncementService;
import ym.smartannouncer.service.ClockScheduleService;
import ym.smartannouncer.service.FirstJoinAnnouncementService;
import ym.smartannouncer.service.IntervalScheduleService;
import ym.smartannouncer.service.LocationAnnouncementService;
import ym.smartannouncer.service.OnlinePlayerTracker;
import ym.smartannouncer.service.TimedAnnouncementDispatchQueue;
import ym.smartannouncer.storage.DispatchDedupeService;
import ym.smartannouncer.util.NamedThreadFactory;
import ym.smartannouncer.util.ThreadUtil;

import java.util.concurrent.ScheduledThreadPoolExecutor;

public final class SmartAnnouncerPlugin extends JavaPlugin {
    private ScheduledThreadPoolExecutor workerExecutor;
    private PlatformScheduler platformScheduler;
    private AnnouncementService announcementService;
    private OnlinePlayerTracker onlinePlayerTracker;

    @Override
    public void onEnable() {
        this.workerExecutor = new ScheduledThreadPoolExecutor(2, new NamedThreadFactory("SmartAnnouncer-Worker"));
        this.workerExecutor.setRemoveOnCancelPolicy(true);
        this.workerExecutor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        this.workerExecutor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);

        this.platformScheduler = PlatformDetector.create(this);
        getLogger().info("Using scheduler adapter: " + platformScheduler.capabilities().schedulerName());

        AnnouncementRegistry registry = new AnnouncementRegistry();
        ConfigLoader configLoader = new ConfigLoader(getDataFolder().toPath(), getClass().getClassLoader(), getLogger());
        MessageRegistry messageRegistry = new MessageRegistry();
        MessageConfigLoader messageConfigLoader = new MessageConfigLoader(getDataFolder().toPath(), getClass().getClassLoader(), getLogger());
        this.onlinePlayerTracker = new OnlinePlayerTracker();
        AudienceSelector audienceSelector = new AudienceSelector();
        MessageDispatcher dispatcher = new MessageDispatcher(platformScheduler, onlinePlayerTracker, audienceSelector, messageRegistry, getLogger());
        TimedAnnouncementDispatchQueue timedDispatchQueue = new TimedAnnouncementDispatchQueue(workerExecutor, 2500L);
        DispatchDedupeService dedupeService = new DispatchDedupeService(getLogger());

        IntervalScheduleService intervalScheduleService = new IntervalScheduleService(workerExecutor, registry, timedDispatchQueue, dispatcher, dedupeService, getLogger());
        ClockScheduleService clockScheduleService = new ClockScheduleService(workerExecutor, registry, timedDispatchQueue, dispatcher, dedupeService, getLogger());
        LocationAnnouncementService locationAnnouncementService = new LocationAnnouncementService(registry, dispatcher, getLogger());
        FirstJoinAnnouncementService firstJoinAnnouncementService = new FirstJoinAnnouncementService(workerExecutor, registry, dispatcher, dedupeService, getLogger());

        this.announcementService = new AnnouncementService(
            workerExecutor,
            registry,
            configLoader,
            messageRegistry,
            messageConfigLoader,
            intervalScheduleService,
            clockScheduleService,
            timedDispatchQueue,
            locationAnnouncementService,
            firstJoinAnnouncementService,
            dispatcher,
            dedupeService,
            getLogger()
        );

        getServer().getPluginManager().registerEvents(onlinePlayerTracker, this);
        getServer().getPluginManager().registerEvents(new PlayerLocationListener(locationAnnouncementService), this);
        getServer().getPluginManager().registerEvents(new PlayerFirstJoinListener(firstJoinAnnouncementService), this);

        PluginCommand command = getCommand("smartannouncer");
        if (command != null) {
            SmartAnnouncerCommand smartAnnouncerCommand = new SmartAnnouncerCommand(announcementService, registry, dispatcher);
            command.setExecutor(smartAnnouncerCommand);
            command.setTabCompleter(smartAnnouncerCommand);
        } else {
            getLogger().severe("Command smartannouncer is missing from plugin.yml");
        }

        // Global/main-safe section: collect current online player references, then
        // hand each player mutation to its entity scheduler. No Player state is
        // read from the wrong Folia region thread.
        platformScheduler.runGlobal(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                platformScheduler.runEntity(player, () -> onlinePlayerTracker.track(player));
            }
        });

        announcementService.reloadAsync(null, true);
    }

    @Override
    public void onDisable() {
        if (announcementService != null) {
            announcementService.shutdown();
        }
        if (platformScheduler != null) {
            platformScheduler.cancelAll();
        }
        if (workerExecutor != null) {
            ThreadUtil.shutdown(workerExecutor, getLogger(), "worker executor");
        }
    }
}
