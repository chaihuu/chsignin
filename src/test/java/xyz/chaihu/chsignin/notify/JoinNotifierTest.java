package xyz.chaihu.chsignin.notify;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.Test;
import xyz.chaihu.chsignin.data.PlayerData;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 入服提醒逻辑测试: 未签到触发判定 + Java 版可点击消息构建。
 */
class JoinNotifierTest {

    private static PlayerData freshData() {
        return new PlayerData(UUID.randomUUID(), "Steve");
    }

    @Test
    void 未签到玩家应当触发提醒() {
        LocalDate today = LocalDate.now();
        assertTrue(JoinNotifier.shouldRemind(freshData(), today));
    }

    @Test
    void 已签到玩家不再提醒() {
        LocalDate today = LocalDate.now();
        PlayerData data = freshData();
        data.addSign(today, false);
        assertFalse(JoinNotifier.shouldRemind(data, today));
    }

    @Test
    void 昨日签到但今日未签仍应提醒() {
        LocalDate today = LocalDate.now();
        PlayerData data = freshData();
        data.addSign(today.minusDays(1), false);
        assertTrue(JoinNotifier.shouldRemind(data, today));
    }

    @Test
    void java提醒消息携带签到命令点击事件() {
        Component msg = JoinNotifier.reminderMessage("§8[签到]§r ");
        List<ClickEvent> clicks = new ArrayList<>();
        collect(msg, clicks);
        assertTrue(clicks.stream().anyMatch(c -> c.action() == ClickEvent.Action.RUN_COMMAND
                && "/qd".equals(c.value())), "应包含 /qd 点击事件");
        assertTrue(clicks.stream().anyMatch(c -> "/qd status".equals(c.value())), "应包含 /qd status 点击事件");
        assertTrue(clicks.stream().anyMatch(c -> "/qd bq".equals(c.value())), "应包含 /qd bq 点击事件");
    }

    @Test
    void 提醒消息含前缀与正文文本() {
        Component msg = JoinNotifier.reminderMessage("§8[签到]§r ");
        StringBuilder sb = new StringBuilder();
        collectText(msg, sb);
        String plain = sb.toString();
        assertTrue(plain.contains("[签到]"));
        assertTrue(plain.contains("还没有签到"));
        assertTrue(plain.contains("立即签到"));
    }

    private static void collectText(Component c, StringBuilder sb) {
        if (c instanceof net.kyori.adventure.text.TextComponent t) {
            sb.append(t.content());
        }
        for (Component child : c.children()) {
            collectText(child, sb);
        }
    }

    private static void collect(Component c, List<ClickEvent> out) {
        if (c.clickEvent() != null) {
            out.add(c.clickEvent());
        }
        for (Component child : c.children()) {
            collect(child, out);
        }
    }
}
