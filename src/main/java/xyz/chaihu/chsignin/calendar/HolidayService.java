package xyz.chaihu.chsignin.calendar;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置节日数据库: 公历固定节日 + 农历传统节日 + 节气(寿星公式推算)。
 * 每个节日带有专属颜色标识。
 */
public final class HolidayService {

    /**
     * 节日信息。
     *
     * @param name     节日名
     * @param color    § 颜色代码(不含 § 符号)
     * @param category 类别: 法定 / 传统 / 节气 / 其他
     */
    public record Holiday(String name, String color, String category) {

        /** 渲染为带颜色与斜体动态内容的文本, 如 "§c§o春节§r". */
        public String render() {
            return "§" + color + "§o" + name + "§r §8(" + category + ")";
        }
    }

    private static final Map<MonthDay, Holiday> SOLAR_HOLIDAYS = new HashMap<>();

    static {
        SOLAR_HOLIDAYS.put(MonthDay.of(1, 1), new Holiday("元旦", "b", "法定"));
        SOLAR_HOLIDAYS.put(MonthDay.of(2, 14), new Holiday("情人节", "d", "其他"));
        SOLAR_HOLIDAYS.put(MonthDay.of(3, 8), new Holiday("妇女节", "d", "法定"));
        SOLAR_HOLIDAYS.put(MonthDay.of(3, 12), new Holiday("植树节", "2", "法定"));
        SOLAR_HOLIDAYS.put(MonthDay.of(4, 1), new Holiday("愚人节", "3", "其他"));
        SOLAR_HOLIDAYS.put(MonthDay.of(5, 1), new Holiday("劳动节", "c", "法定"));
        SOLAR_HOLIDAYS.put(MonthDay.of(5, 4), new Holiday("青年节", "6", "法定"));
        SOLAR_HOLIDAYS.put(MonthDay.of(6, 1), new Holiday("儿童节", "e", "法定"));
        SOLAR_HOLIDAYS.put(MonthDay.of(7, 1), new Holiday("建党节", "c", "法定"));
        SOLAR_HOLIDAYS.put(MonthDay.of(8, 1), new Holiday("建军节", "c", "法定"));
        SOLAR_HOLIDAYS.put(MonthDay.of(9, 10), new Holiday("教师节", "b", "法定"));
        SOLAR_HOLIDAYS.put(MonthDay.of(10, 1), new Holiday("国庆节", "c", "法定"));
        SOLAR_HOLIDAYS.put(MonthDay.of(12, 24), new Holiday("平安夜", "3", "其他"));
        SOLAR_HOLIDAYS.put(MonthDay.of(12, 25), new Holiday("圣诞节", "2", "其他"));
    }

    /** 农历节日: 月份 -> (日 -> 节日)。 */
    private static final Map<Integer, Map<Integer, Holiday>> LUNAR_HOLIDAYS = new HashMap<>();

    static {
        putLunar(1, 1, new Holiday("春节", "c", "传统"));
        putLunar(1, 15, new Holiday("元宵节", "e", "传统"));
        putLunar(2, 2, new Holiday("龙抬头", "2", "传统"));
        putLunar(3, 3, new Holiday("上巳节", "a", "传统"));
        putLunar(5, 5, new Holiday("端午节", "3", "传统"));
        putLunar(7, 7, new Holiday("七夕节", "d", "传统"));
        putLunar(7, 15, new Holiday("中元节", "8", "传统"));
        putLunar(8, 15, new Holiday("中秋节", "6", "传统"));
        putLunar(9, 9, new Holiday("重阳节", "6", "传统"));
        putLunar(12, 8, new Holiday("腊八节", "5", "传统"));
        putLunar(12, 23, new Holiday("小年", "c", "传统"));
    }

    private static void putLunar(int month, int day, Holiday holiday) {
        LUNAR_HOLIDAYS.computeIfAbsent(month, m -> new HashMap<>()).put(day, holiday);
    }

    /**
     * 返回指定公历日期匹配的节日列表(可能为空)。
     */
    public List<Holiday> holidaysOf(LocalDate date) {
        List<Holiday> result = new ArrayList<>(3);

        Holiday solar = SOLAR_HOLIDAYS.get(MonthDay.from(date));
        if (solar != null) {
            result.add(solar);
        }

        Holiday term = solarTermOf(date);
        if (term != null) {
            result.add(term);
        }

        LunarCalendar.LunarDate lunar = LunarCalendar.toLunar(date);
        if (lunar != null && !lunar.leapMonth()) {
            Map<Integer, Holiday> byDay = LUNAR_HOLIDAYS.get(lunar.month());
            if (byDay != null) {
                Holiday h = byDay.get(lunar.day());
                if (h != null) {
                    result.add(h);
                }
            }
            // 除夕: 农历腊月最后一天
            if (lunar.month() == 12 && lunar.day() == LunarCalendar.monthDays(lunar.year(), 12)) {
                result.add(new Holiday("除夕", "c", "传统"));
            }
        }
        return result;
    }

    /**
     * 清明 / 冬至 节气推算(寿星公式, 20 与 21 世纪)。其余节气不展示。
     */
    private static Holiday solarTermOf(LocalDate date) {
        int year = date.getYear();
        if (year < 1901 || year > 2099) {
            return null;
        }
        int y = year % 100;
        int centuryOffset = y; // 21 世纪使用 y, 20 世纪使用 y (year<2000 时换 C 值)
        double d = 0.2422;
        int l = centuryOffset / 4;
        if (date.getMonthValue() == 4) {
            double c = year < 2000 ? 5.594 : 4.81;
            int day = (int) (centuryOffset * d + c) - l;
            if (day == date.getDayOfMonth()) {
                return new Holiday("清明节", "a", "法定");
            }
        } else if (date.getMonthValue() == 12) {
            double c = year < 2000 ? 22.60 : 21.94;
            int day = (int) (centuryOffset * d + c) - l;
            if (day == date.getDayOfMonth()) {
                return new Holiday("冬至", "b", "节气");
            }
        }
        return null;
    }

    /** 农历日期中文, 用于状态展示; 无法推算返回 null。 */
    public String lunarText(LocalDate date) {
        LunarCalendar.LunarDate lunar = LunarCalendar.toLunar(date);
        return lunar == null ? null : lunar.formatCn();
    }
}
