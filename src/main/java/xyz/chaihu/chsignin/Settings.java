package xyz.chaihu.chsignin;

import org.bukkit.configuration.file.FileConfiguration;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * config.yml 的类型安全包装, 支持热重载。
 */
public final class Settings {

    public enum RewardMode {
        RANDOM_RANGE, FIXED
    }

    /** 连续签到奖励规则: 连续 days 天时额外发放 bonus 金币。 */
    public record StreakRule(int days, int bonus) {
    }

    private FileConfiguration cfg;

    public Settings(FileConfiguration cfg) {
        this.cfg = cfg;
    }

    void update(FileConfiguration newCfg) {
        this.cfg = newCfg;
    }

    // ---------- general ----------

    public DayOfWeek weekStart() {
        String raw = cfg.getString("general.week-start", "MONDAY").toUpperCase(Locale.ROOT);
        try {
            return DayOfWeek.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return DayOfWeek.MONDAY;
        }
    }

    public LocalDate weekStartOf(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(weekStart()));
    }

    // ---------- reward ----------

    public RewardMode rewardMode() {
        String raw = cfg.getString("reward.mode", "random-range").toLowerCase(Locale.ROOT);
        return raw.startsWith("fixed") ? RewardMode.FIXED : RewardMode.RANDOM_RANGE;
    }

    public int randomMin() {
        return Math.max(0, cfg.getInt("reward.random-range.min", 10));
    }

    public int randomMax() {
        return Math.max(randomMin(), cfg.getInt("reward.random-range.max", 50));
    }

    public int fixedAmount() {
        return Math.max(0, cfg.getInt("reward.fixed-amount", 20));
    }

    // ---------- streak ----------

    public boolean streakEnabled() {
        return cfg.getBoolean("streak.enabled", true);
    }

    public List<StreakRule> streakRules() {
        List<StreakRule> rules = new ArrayList<>();
        for (Map<?, ?> map : cfg.getMapList("streak.rules")) {
            Object days = map.get("days");
            Object bonus = map.get("bonus");
            if (days instanceof Number d && bonus instanceof Number b) {
                rules.add(new StreakRule(d.intValue(), b.intValue()));
            }
        }
        rules.sort((a, b) -> Integer.compare(a.days(), b.days()));
        return rules;
    }

    public int streakBonus(int streak) {
        if (!streakEnabled()) {
            return 0;
        }
        for (StreakRule rule : streakRules()) {
            if (rule.days() == streak) {
                return rule.bonus();
            }
        }
        return 0;
    }

    // ---------- makeup ----------

    public boolean makeupEnabled() {
        return cfg.getBoolean("makeup.enabled", true);
    }

    public boolean makeupCurrentWeekOnly() {
        return cfg.getBoolean("makeup.current-week-only", true);
    }

    public int makeupMaxDaysBack() {
        return Math.max(1, cfg.getInt("makeup.max-days-back", 7));
    }

    public int makeupCooldownDays() {
        return Math.max(0, cfg.getInt("makeup.cooldown-days", 1));
    }

    public int makeupMaxPerWeek() {
        return Math.max(0, cfg.getInt("makeup.max-per-week", 2));
    }

    public int makeupCost() {
        return Math.max(0, cfg.getInt("makeup.cost", 10));
    }

    public boolean makeupGivesReward() {
        return cfg.getBoolean("makeup.gives-reward", true);
    }

    // ---------- fortune / quote ----------

    public boolean fortuneEnabled() {
        return cfg.getBoolean("fortune.enabled", true);
    }

    public boolean quoteEnabled() {
        return cfg.getBoolean("quote.enabled", true);
    }

    // ---------- messages ----------

    public String prefix() {
        return cfg.getString("messages.prefix", "§8[§b§l签到§8]§r ");
    }
}
