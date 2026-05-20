package ym.smartannouncer.util;

import ym.smartannouncer.config.ConfigLoadException;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;

public final class TimeUtil {
    private static final DateTimeFormatter CLOCK_FORMATTER = DateTimeFormatter.ofPattern("H:mm");

    private TimeUtil() {
    }

    public static LocalTime parseClockTime(String input) {
        try {
            return LocalTime.parse(input, CLOCK_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw new ConfigLoadException("Invalid clock time '" + input + "'. Expected HH:mm, for example 08:00.", ex);
        }
    }

    public static Duration delayUntilNext(Collection<LocalTime> times, ZoneId zoneId) {
        if (times.isEmpty()) {
            throw new IllegalArgumentException("times cannot be empty");
        }
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        ZonedDateTime nearest = null;
        for (LocalTime time : times) {
            ZonedDateTime candidate = now.toLocalDate().atTime(time).atZone(zoneId);
            if (!candidate.isAfter(now)) {
                candidate = candidate.plusDays(1L);
            }
            if (nearest == null || candidate.isBefore(nearest)) {
                nearest = candidate;
            }
        }
        return Duration.between(now, nearest);
    }
}
