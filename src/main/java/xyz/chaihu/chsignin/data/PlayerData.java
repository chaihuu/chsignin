package xyz.chaihu.chsignin.data;

import java.time.LocalDate;
import java.util.TreeSet;
import java.util.UUID;

/**
 * 单个玩家的签到数据(内存模型), 由 DataManager 负责持久化为 YAML 文件。
 */
public final class PlayerData {

    private final UUID uuid;
    private String name;
    private final TreeSet<LocalDate> signedDates = new TreeSet<>();
    private final TreeSet<LocalDate> makeupDates = new TreeSet<>();
    private LocalDate lastMakeupDate;
    private long totalSigns;

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean hasSigned(LocalDate date) {
        return signedDates.contains(date);
    }

    /** 记录一次签到(补签时同时登记补签标记并刷新补签时间)。 */
    public void addSign(LocalDate date, boolean viaMakeup) {
        signedDates.add(date);
        totalSigns++;
        if (viaMakeup) {
            makeupDates.add(date);
            lastMakeupDate = LocalDate.now();
        }
    }

    /** 从存档恢复历史签到记录(不影响累计次数, 由 setTotalSigns 统一设置)。 */
    public void restore(LocalDate date, boolean viaMakeup) {
        signedDates.add(date);
        if (viaMakeup) {
            makeupDates.add(date);
        }
    }

    /** 以 end 为终点的连续签到天数(end 必须已签到, 否则返回 0)。 */
    public int streakEndingAt(LocalDate end) {
        if (!signedDates.contains(end)) {
            return 0;
        }
        int count = 0;
        LocalDate cursor = end;
        while (signedDates.contains(cursor)) {
            count++;
            cursor = cursor.minusDays(1);
        }
        return count;
    }

    /** 当前连续签到天数: 今天签了算到今天, 否则算到昨天。 */
    public int currentStreak(LocalDate today) {
        if (signedDates.contains(today)) {
            return streakEndingAt(today);
        }
        return streakEndingAt(today.minusDays(1));
    }

    public TreeSet<LocalDate> signedDates() {
        return new TreeSet<>(signedDates);
    }

    public TreeSet<LocalDate> makeupDates() {
        return new TreeSet<>(makeupDates);
    }

    public LocalDate lastMakeupDate() {
        return lastMakeupDate;
    }

    public void setLastMakeupDate(LocalDate lastMakeupDate) {
        this.lastMakeupDate = lastMakeupDate;
    }

    public long totalSigns() {
        return totalSigns;
    }

    public void setTotalSigns(long totalSigns) {
        this.totalSigns = totalSigns;
    }

    /** 本周(weekStart 起)已补签次数。 */
    public int makeupCountSince(LocalDate weekStart) {
        return makeupDates.tailSet(weekStart, true).size();
    }
}
