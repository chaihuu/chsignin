package xyz.chaihu.chsignin.fortune;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FortuneServiceTest {

    private final FortuneService service = new FortuneService();
    // 注意: 形如 11111111-1111-... 的对称 UUID 的 hashCode 为 0, 不能用作测试样本
    private final UUID alice = UUID.fromString("064219f1-3a5e-4b8c-9d2e-1f6a7b8c9d01");
    private final UUID bob = UUID.fromString("7b3c2d1e-9f8a-4b7c-8d6e-5a4b3c2d1e0f");
    private final LocalDate day = LocalDate.parse("2026-10-17");

    @Test
    void deterministicPerPlayerAndDay() {
        assertEquals(service.forPlayer(alice, day), service.forPlayer(alice, day));
    }

    @Test
    void differsAcrossDays() {
        assertNotEquals(service.forPlayer(alice, day), service.forPlayer(alice, day.plusDays(1)));
    }

    @Test
    void differsAcrossPlayers() {
        assertNotEquals(service.forPlayer(alice, day), service.forPlayer(bob, day));
    }

    @Test
    void valuesInRange() {
        FortuneService.Fortune f = service.forPlayer(alice, day);
        assertEquals(5, f.stars(5).chars().filter(c -> c == '★').count());
        assertEquals(0, f.stars(0).chars().filter(c -> c == '★').count());
        for (int i = 0; i < 50; i++) {
            FortuneService.Fortune r = service.forPlayer(UUID.randomUUID(), day.plusDays(i));
            assertBetween(r.overall(), 1, 5);
            assertBetween(r.wealth(), 1, 5);
            assertBetween(r.luckyNumber(), 1, 9);
            if (r.suitable().size() < 1 || r.avoid().size() < 1) {
                assertEquals(1, r.suitable().size());
            }
        }
    }

    private static void assertBetween(int v, int min, int max) {
        if (v < min || v > max) {
            throw new AssertionError(v + " 不在 [" + min + "," + max + "]");
        }
    }
}
