package xyz.chaihu.chsignin.calendar;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 农历算法锚点验证: 与真实历法公布日期比对。
 */
class LunarCalendarTest {

    private static void assertLunar(String solar, int month, int day) {
        LunarCalendar.LunarDate lunar = LunarCalendar.toLunar(LocalDate.parse(solar));
        assertNotNull(lunar, solar);
        assertEquals(month, lunar.month(), solar + " 月不符");
        assertEquals(day, lunar.day(), solar + " 日不符");
    }

    @Test
    void baseDateIsLunarNewYear1900() {
        LunarCalendar.LunarDate lunar = LunarCalendar.toLunar(LocalDate.of(1900, 1, 31));
        assertNotNull(lunar);
        assertEquals(1900, lunar.year());
        assertEquals(1, lunar.month());
        assertEquals(1, lunar.day());
    }

    @Test
    void springFestivalDates() {
        // 历年春节(正月初一)公历日期
        assertLunar("2024-02-10", 1, 1);
        assertLunar("2025-01-29", 1, 1);
        assertLunar("2026-02-17", 1, 1);
        assertLunar("2027-02-06", 1, 1);
        assertLunar("2028-01-26", 1, 1);
        assertLunar("2029-02-13", 1, 1);
        assertLunar("2030-02-03", 1, 1);
        assertLunar("2031-01-23", 1, 1);
        assertLunar("2032-02-11", 1, 1);
        assertLunar("2033-01-31", 1, 1);
    }

    @Test
    void midAutumnDates() {
        // 中秋节(八月十五)
        assertLunar("2024-09-17", 8, 15);
        assertLunar("2025-10-06", 8, 15);
        assertLunar("2026-09-25", 8, 15);
        assertLunar("2027-09-15", 8, 15);
    }

    @Test
    void dragonBoatDates() {
        // 端午节(五月初五)
        assertLunar("2024-06-10", 5, 5);
        assertLunar("2025-05-31", 5, 5);
        assertLunar("2026-06-19", 5, 5);
        assertLunar("2027-06-09", 5, 5);
    }

    @Test
    void otherTraditionalDates() {
        assertLunar("2026-08-19", 7, 7);   // 七夕
        assertLunar("2026-10-18", 9, 9);   // 重阳
        assertLunar("2026-03-03", 1, 15);  // 元宵
    }

    @Test
    void leapMonthsMatchRealCalendar() {
        assertEquals(2, LunarCalendar.leapMonth(2023)); // 2023 闰二月
        assertEquals(6, LunarCalendar.leapMonth(2025)); // 2025 闰六月
        assertEquals(0, LunarCalendar.leapMonth(2026)); // 2026 无闰月
    }

    @Test
    void chineseDayFormatting() {
        assertEquals("初十", LunarCalendar.dayCn(10));
        assertEquals("二十", LunarCalendar.dayCn(20));
        assertEquals("廿一", LunarCalendar.dayCn(21));
        assertEquals("三十", LunarCalendar.dayCn(30));
        assertEquals("腊", LunarCalendar.monthCn(12));
    }
}
