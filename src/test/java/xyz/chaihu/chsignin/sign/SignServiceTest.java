package xyz.chaihu.chsignin.sign;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.chaihu.chsignin.Settings;
import xyz.chaihu.chsignin.data.PlayerData;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignServiceTest {

    /** 测试用假钱包。 */
    static final class FakeWallet implements Wallet {
        final Map<UUID, Double> balances = new HashMap<>();

        @Override
        public double balance(UUID player) {
            return balances.getOrDefault(player, 0.0);
        }

        @Override
        public boolean withdraw(UUID player, double amount) {
            double b = balance(player);
            if (b < amount) {
                return false;
            }
            balances.put(player, b - amount);
            return true;
        }

        @Override
        public void deposit(UUID player, double amount) {
            balances.merge(player, amount, Double::sum);
        }
    }

    private static final String CONFIG = """
            general:
              week-start: MONDAY
            reward:
              mode: random-range
              random-range:
                min: 10
                max: 50
              fixed-amount: 20
            streak:
              enabled: true
              rules:
                - days: 3
                  bonus: 20
                - days: 7
                  bonus: 50
            makeup:
              enabled: true
              current-week-only: true
              max-days-back: 7
              cooldown-days: 1
              max-per-week: 2
              cost: 10
              gives-reward: true
            """;

    private Settings settings;
    private FakeWallet wallet;
    private SignService service;
    private final UUID player = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @BeforeEach
    void setUp() throws Exception {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.loadFromString(CONFIG);
        settings = new Settings(cfg);
        wallet = new FakeWallet();
        service = new SignService(settings, wallet);
    }

    @Test
    void randomRewardInRange() {
        PlayerData data = new PlayerData(player, "tester");
        for (int i = 0; i < 100; i++) {
            int r = service.rollReward();
            assertTrue(r >= 10 && r <= 50, "奖励越界: " + r);
        }
    }

    @Test
    void fixedRewardMode() throws Exception {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.loadFromString(CONFIG.replace("mode: random-range", "mode: fixed"));
        SignService fixed = new SignService(new Settings(cfg), wallet);
        for (int i = 0; i < 10; i++) {
            assertEquals(20, fixed.rollReward());
        }
    }

    @Test
    void signDepositsRewardAndCountsStreak() {
        PlayerData data = new PlayerData(player, "tester");
        LocalDate today = LocalDate.of(2026, 10, 17);
        SignService.SignResult r = service.sign(player, data, today);
        assertTrue(r.ok());
        assertEquals(1, r.streak());
        assertEquals(0, r.streakBonus());
        assertEquals(r.baseReward(), wallet.balance(player), 0.001);
        assertTrue(data.hasSigned(today));
    }

    @Test
    void doubleSignFails() {
        PlayerData data = new PlayerData(player, "tester");
        LocalDate today = LocalDate.of(2026, 10, 17);
        assertTrue(service.sign(player, data, today).ok());
        SignService.SignResult second = service.sign(player, data, today);
        assertFalse(second.ok());
        assertEquals("TODAY", second.failReason());
    }

    @Test
    void streakBonusAtThreeDays() {
        PlayerData data = new PlayerData(player, "tester");
        SignService.SignResult d1 = service.sign(player, data, LocalDate.of(2026, 10, 15));
        SignService.SignResult d2 = service.sign(player, data, LocalDate.of(2026, 10, 16));
        SignService.SignResult r = service.sign(player, data, LocalDate.of(2026, 10, 17));
        assertEquals(3, r.streak());
        assertEquals(20, r.streakBonus());
        double expected = d1.baseReward() + d2.baseReward() + r.baseReward() + 20;
        assertEquals(expected, wallet.balance(player), 0.001);
    }

    @Test
    void streakBreaksOnGap() {
        PlayerData data = new PlayerData(player, "tester");
        service.sign(player, data, LocalDate.of(2026, 10, 14));
        SignService.SignResult r = service.sign(player, data, LocalDate.of(2026, 10, 17));
        assertEquals(1, r.streak());
        assertEquals(0, r.streakBonus());
    }

    @Test
    void makeupCandidatesWithinWeek() {
        PlayerData data = new PlayerData(player, "tester");
        data.restore(LocalDate.of(2026, 10, 12), false);
        data.restore(LocalDate.of(2026, 10, 13), false);
        LocalDate today = LocalDate.of(2026, 10, 17); // 周六, 本周一为 10-12
        List<LocalDate> candidates = service.makeupCandidates(data, today);
        assertEquals(List.of(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 15),
                LocalDate.of(2026, 10, 16)), candidates);
    }

    @Test
    void makeupOutsideWeekRejected() {
        PlayerData data = new PlayerData(player, "tester");
        SignService.MakeupResult r = service.makeup(player, data, LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 17));
        assertFalse(r.ok());
        assertEquals("OUT_OF_RANGE", r.failReason());
    }

    @Test
    void makeupFutureRejected() {
        PlayerData data = new PlayerData(player, "tester");
        SignService.MakeupResult r = service.makeup(player, data, LocalDate.of(2026, 10, 17), LocalDate.of(2026, 10, 17));
        assertFalse(r.ok());
        assertEquals("FUTURE", r.failReason());
    }

    @Test
    void makeupCostAndReward() {
        PlayerData data = new PlayerData(player, "tester");
        wallet.balances.put(player, 100.0);
        SignService.MakeupResult r = service.makeup(player, data, LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 17));
        assertTrue(r.ok());
        assertEquals(10, r.cost());
        // 余额 = 100 - 10 + 奖励
        assertTrue(wallet.balance(player) >= 90.0 && wallet.balance(player) <= 140.0);
        assertTrue(data.hasSigned(LocalDate.of(2026, 10, 14)));
        assertTrue(data.makeupDates().contains(LocalDate.of(2026, 10, 14)));
    }

    @Test
    void makeupInsufficientFunds() {
        PlayerData data = new PlayerData(player, "tester");
        wallet.balances.put(player, 5.0);
        SignService.MakeupResult r = service.makeup(player, data, LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 17));
        assertFalse(r.ok());
        assertEquals("NO_MONEY", r.failReason());
        assertFalse(data.hasSigned(LocalDate.of(2026, 10, 14)));
    }

    @Test
    void makeupAlreadySignedRejected() {
        PlayerData data = new PlayerData(player, "tester");
        data.restore(LocalDate.of(2026, 10, 14), false);
        SignService.MakeupResult r = service.makeup(player, data, LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 17));
        assertFalse(r.ok());
        assertEquals("ALREADY", r.failReason());
    }

    @Test
    void makeupCooldownEnforced() {
        PlayerData data = new PlayerData(player, "tester");
        wallet.balances.put(player, 100.0);
        LocalDate today = LocalDate.now(); // 真实当前日期, 与 addSign 内部一致
        // 第一次补签(本周内某天)
        LocalDate target1 = settings.weekStartOf(today).plusDays(1);
        if (target1.isBefore(today) && !data.hasSigned(target1)) {
            assertTrue(service.makeup(player, data, target1, today).ok());
        }
        // 第二次补签应触发冷却 (cooldown-days=1)
        LocalDate target2 = settings.weekStartOf(today).plusDays(2);
        if (target2.isBefore(today) && !data.hasSigned(target2)) {
            SignService.MakeupResult r = service.makeup(player, data, target2, today);
            assertFalse(r.ok());
            assertEquals("COOLDOWN", r.failReason());
        }
    }

    @Test
    void makeupWeekLimit() throws Exception {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.loadFromString(CONFIG.replace("cooldown-days: 1", "cooldown-days: 0"));
        SignService noCd = new SignService(new Settings(cfg), wallet);
        PlayerData data = new PlayerData(player, "tester");
        wallet.balances.put(player, 1000.0);
        LocalDate today = LocalDate.of(2026, 10, 17);
        assertTrue(noCd.makeup(player, data, LocalDate.of(2026, 10, 14), today).ok());
        data.setLastMakeupDate(null);
        assertTrue(noCd.makeup(player, data, LocalDate.of(2026, 10, 15), today).ok());
        data.setLastMakeupDate(null);
        SignService.MakeupResult r = noCd.makeup(player, data, LocalDate.of(2026, 10, 16), today);
        assertFalse(r.ok());
        assertEquals("WEEK_LIMIT", r.failReason());
    }
}
