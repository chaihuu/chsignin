package xyz.chaihu.chsignin.calendar;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 纯本地农历算法(1900-2100), 基于公开的农历数据表实现, 无任何外部依赖。
 */
public final class LunarCalendar {

    /** 农历数据表: 每年一个 int, 低 4 位为闰月月份(0 无闰月), 位 16 为闰月大小, 位 4-15 为 1-12 月大小(1=30 天, 0=29 天)。 */
    private static final int[] LUNAR_INFO = {
            0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2, // 1900-1909
            0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977, // 1910-1919
            0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970, // 1920-1929
            0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950, // 1930-1939
            0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557, // 1940-1949
            0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0, // 1950-1959
            0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0, // 1960-1969
            0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6, // 1970-1979
            0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570, // 1980-1989
            0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x05ac0, 0x0ab60, 0x096d5, 0x092e0, // 1990-1999
            0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5, // 2000-2009
            0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930, // 2010-2019
            0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530, // 2020-2029
            0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45, // 2030-2039
            0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0, // 2040-2049
            0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0, // 2050-2059
            0x0a2e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4, // 2060-2069
            0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0, // 2070-2079
            0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160, // 2080-2089
            0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252, // 2090-2099
            0x0d520 // 2100
    };

    /** 公历 1900-01-31 = 农历 1900 年正月初一, 作为推算基准。 */
    private static final LocalDate BASE_DATE = LocalDate.of(1900, 1, 31);

    private LunarCalendar() {
    }

    /**
     * 农历日期。leapMonth 为 true 表示该日期处于闰月。
     */
    public record LunarDate(int year, int month, int day, boolean leapMonth) {

        /** 格式化为中文, 如 "乙巳年六月初十" 风格的简化版 "农历二〇二五年六月初十"。 */
        public String formatCn() {
            return "农历" + yearCn(year) + "年" + (leapMonth ? "闰" : "") + monthCn(month) + "月" + dayCn(day);
        }
    }

    /**
     * 公历转农历。超出 1900-01-31 ~ 2100-12-31 范围返回 null。
     */
    public static LunarDate toLunar(LocalDate date) {
        if (date == null || date.isBefore(BASE_DATE) || date.getYear() > 2100) {
            return null;
        }
        int offset = (int) ChronoUnit.DAYS.between(BASE_DATE, date);
        int i = 1900;
        int temp = 0;
        for (; i < 2101 && offset > 0; i++) {
            temp = yearDays(i);
            offset -= temp;
        }
        if (offset < 0) {
            offset += temp;
            i--;
        }
        int lYear = i;
        int leap = leapMonth(lYear);
        boolean isLeap = false;
        int month = 1;
        for (; month < 13 && offset > 0; month++) {
            if (leap > 0 && month == leap + 1 && !isLeap) {
                month--;
                isLeap = true;
                temp = leapDays(lYear);
            } else {
                temp = monthDays(lYear, month);
            }
            if (isLeap && month == leap + 1) {
                isLeap = false;
            }
            offset -= temp;
        }
        if (offset == 0 && leap > 0 && month == leap + 1) {
            if (isLeap) {
                isLeap = false;
            } else {
                isLeap = true;
                month--;
            }
        }
        if (offset < 0) {
            offset += temp;
            month--;
        }
        return new LunarDate(lYear, month, offset + 1, isLeap);
    }

    /** 该农历年的闰月月份(1-12), 0 表示无闰月。 */
    public static int leapMonth(int lunarYear) {
        return LUNAR_INFO[lunarYear - 1900] & 0xf;
    }

    /** 该农历年闰月的天数, 无闰月返回 0。 */
    public static int leapDays(int lunarYear) {
        if (leapMonth(lunarYear) == 0) {
            return 0;
        }
        return (LUNAR_INFO[lunarYear - 1900] & 0x10000) != 0 ? 30 : 29;
    }

    /** 农历年 m 月(非闰月)天数。 */
    public static int monthDays(int lunarYear, int month) {
        if (month < 1 || month > 12) {
            return -1;
        }
        return (LUNAR_INFO[lunarYear - 1900] & (0x10000 >> month)) != 0 ? 30 : 29;
    }

    /** 农历年全年天数(含闰月)。 */
    public static int yearDays(int lunarYear) {
        int sum = 348;
        for (int i = 0x8000; i > 0x8; i >>= 1) {
            if ((LUNAR_INFO[lunarYear - 1900] & i) != 0) {
                sum++;
            }
        }
        return sum + leapDays(lunarYear);
    }

    // ---------- 中文格式化 ----------

    private static final String[] CN_DIGITS = {"〇", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
    private static final String[] MONTH_NAMES = {"正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊"};

    static String yearCn(int year) {
        StringBuilder sb = new StringBuilder();
        for (char c : String.valueOf(year).toCharArray()) {
            sb.append(CN_DIGITS[c - '0']);
        }
        return sb.toString();
    }

    static String monthCn(int month) {
        return MONTH_NAMES[month - 1];
    }

    static String dayCn(int day) {
        if (day == 10) return "初十";
        if (day == 20) return "二十";
        if (day == 30) return "三十";
        int tens = day / 10;
        int ones = day % 10;
        String prefix = tens == 0 ? "初" : (tens == 1 ? "十" : tens == 2 ? "廿" : "三");
        return prefix + CN_DIGITS[ones];
    }
}
