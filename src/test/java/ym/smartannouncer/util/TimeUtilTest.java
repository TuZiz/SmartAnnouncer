package ym.smartannouncer.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeUtilTest {
    @Test
    void parseClockTimeAcceptsSingleDigitHour() {
        assertEquals(LocalTime.of(8, 5), TimeUtil.parseClockTime("8:05"));
    }

    @Test
    void nextOccurrenceChoosesTodayOrTomorrow() {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime now = ZonedDateTime.of(LocalDate.of(2026, 5, 20), LocalTime.of(10, 0), zone);

        assertEquals(LocalTime.of(12, 0),
            TimeUtil.nextOccurrence(List.of(LocalTime.of(9, 0), LocalTime.of(12, 0)), zone, now).toLocalTime());
        assertEquals(LocalDate.of(2026, 5, 21),
            TimeUtil.nextOccurrence(List.of(LocalTime.of(9, 0)), zone, now).toLocalDate());
    }
}
