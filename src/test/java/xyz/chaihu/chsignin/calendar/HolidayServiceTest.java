package xyz.chaihu.chsignin.calendar;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HolidayServiceTest {

    private final HolidayService service = new HolidayService();

    private boolean contains(String date, String name) {
        return service.holidaysOf(LocalDate.parse(date)).stream()
                .anyMatch(h -> h.name().equals(name));
    }

    @Test
    void solarHolidays() {
        assertTrue(contains("2026-10-01", "国庆节"));
        assertTrue(contains("2026-01-01", "元旦"));
        assertTrue(contains("2026-05-01", "劳动节"));
    }

    @Test
    void lunarHolidays() {
        assertTrue(contains("2026-02-17", "春节"));
        assertTrue(contains("2026-02-16", "除夕"));
        assertTrue(contains("2026-09-25", "中秋节"));
        assertTrue(contains("2026-06-19", "端午节"));
        assertTrue(contains("2026-03-03", "元宵节"));
    }

    @Test
    void solarTerms() {
        assertTrue(contains("2026-04-05", "清明节"));
        assertTrue(contains("2026-12-22", "冬至"));
        assertTrue(contains("2024-04-04", "清明节"));
    }

    @Test
    void ordinaryDayHasNoHoliday() {
        List<HolidayService.Holiday> holidays = service.holidaysOf(LocalDate.parse("2026-10-17"));
        assertTrue(holidays.isEmpty(), "2026-10-17 不应有节日, 实际: " + holidays);
    }

    @Test
    void holidayHasColorAndCategory() {
        HolidayService.Holiday h = service.holidaysOf(LocalDate.parse("2026-10-01")).get(0);
        assertEquals("国庆节", h.name());
        assertFalse(h.color().isEmpty());
        assertEquals("法定", h.category());
        assertTrue(h.render().contains("§"));
    }

    @Test
    void lunarTextAvailable() {
        String text = service.lunarText(LocalDate.parse("2026-10-17"));
        assertTrue(text.startsWith("农历二〇二六年"), text);
    }
}
