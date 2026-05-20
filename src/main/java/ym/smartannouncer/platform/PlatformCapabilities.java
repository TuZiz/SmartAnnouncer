package ym.smartannouncer.platform;

public record PlatformCapabilities(
    boolean paperPresent,
    boolean foliaPresent,
    boolean globalSchedulerAvailable,
    boolean regionSchedulerAvailable,
    boolean entitySchedulerAvailable,
    boolean asyncSchedulerAvailable,
    String schedulerName
) {
}
