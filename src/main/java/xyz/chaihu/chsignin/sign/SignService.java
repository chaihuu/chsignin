package xyz.chaihu.chsignin.sign;

import xyz.chaihu.chsignin.Settings;
import xyz.chaihu.chsignin.data.PlayerData;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * 签到 / 补签核心业务逻辑(纯 Java, 不依赖 Bukkit, 便于测试)。
 */
public final class SignService {

    public record SignResult(boolean ok, LocalDate date, int baseReward, int streakBonus,
                             int streak, double balanceAfter, String failReason) {

        public static SignResult fail(String reason) {
            return new SignResult(false, null, 0, 0, 0, 0, reason);
        }
    }

    public record MakeupResult(boolean ok, LocalDate date, int cost, int reward,
                               int streak, double balanceAfter, String failReason) {

        public static MakeupResult fail(String reason) {
            return new MakeupResult(false, null, 0, 0, 0, 0, reason);
        }
    }

    private final Settings settings;
    private final Wallet wallet;
    private final Random random = new Random();

    public SignService(Settings settings, Wallet wallet) {
        this.settings = settings;
        this.wallet = wallet;
    }

    /** 掷出本次签到基础奖励。 */
    public int rollReward() {
        if (settings.rewardMode() == Settings.RewardMode.FIXED) {
            return settings.fixedAmount();
        }
        int min = settings.randomMin();
        int max = settings.randomMax();
        return min + random.nextInt(max - min + 1);
    }

    /**
     * 正常签到。调用成功后由调用方负责持久化 PlayerData。
     */
    public SignResult sign(UUID player, PlayerData data, LocalDate today) {
        if (data.hasSigned(today)) {
            return SignResult.fail("TODAY");
        }
        int base = rollReward();
        data.addSign(today, false);
        int streak = data.streakEndingAt(today);
        int bonus = settings.streakBonus(streak);
        double total = base + bonus;
        if (total > 0) {
            wallet.deposit(player, total);
        }
        return new SignResult(true, today, base, bonus, streak, wallet.balance(player), null);
    }

    /**
     * 可补签日期列表(未签到且在允许范围内)。
     */
    public List<LocalDate> makeupCandidates(PlayerData data, LocalDate today) {
        if (!settings.makeupEnabled()) {
            return List.of();
        }
        LocalDate from = windowStart(today);
        List<LocalDate> out = new ArrayList<>();
        for (LocalDate c = from; c.isBefore(today); c = c.plusDays(1)) {
            if (!data.hasSigned(c)) {
                out.add(c);
            }
        }
        return out;
    }

    private LocalDate windowStart(LocalDate today) {
        if (settings.makeupCurrentWeekOnly()) {
            return settings.weekStartOf(today);
        }
        return today.minusDays(settings.makeupMaxDaysBack());
    }

    /**
     * 补签指定日期。调用成功后由调用方负责持久化 PlayerData。
     */
    public MakeupResult makeup(UUID player, PlayerData data, LocalDate target, LocalDate today) {
        if (!settings.makeupEnabled()) {
            return MakeupResult.fail("DISABLED");
        }
        if (target == null || !target.isBefore(today)) {
            return MakeupResult.fail("FUTURE");
        }
        if (target.isBefore(windowStart(today))) {
            return MakeupResult.fail("OUT_OF_RANGE");
        }
        if (data.hasSigned(target)) {
            return MakeupResult.fail("ALREADY");
        }
        if (data.makeupDates().contains(target)) {
            return MakeupResult.fail("ALREADY_MADEUP");
        }
        LocalDate last = data.lastMakeupDate();
        if (last != null && today.isBefore(last.plusDays(settings.makeupCooldownDays()))) {
            return MakeupResult.fail("COOLDOWN");
        }
        LocalDate weekStart = settings.weekStartOf(today);
        if (data.makeupCountSince(weekStart) >= settings.makeupMaxPerWeek()) {
            return MakeupResult.fail("WEEK_LIMIT");
        }
        int cost = settings.makeupCost();
        if (cost > 0 && !wallet.withdraw(player, cost)) {
            return MakeupResult.fail("NO_MONEY");
        }
        data.addSign(target, true);
        data.setLastMakeupDate(today);
        int reward = settings.makeupGivesReward() ? rollReward() : 0;
        if (reward > 0) {
            wallet.deposit(player, reward);
        }
        int streak = data.currentStreak(today);
        return new MakeupResult(true, target, cost, reward, streak, wallet.balance(player), null);
    }
}
