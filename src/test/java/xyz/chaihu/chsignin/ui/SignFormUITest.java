package xyz.chaihu.chsignin.ui;

import org.geysermc.cumulus.form.SimpleForm;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 基岩版签到表单内容渲染测试(纯数据, 无需服务端)。
 */
class SignFormUITest {

    private static final LocalDate D = LocalDate.of(2026, 10, 17);

    @Test
    void 主菜单内容含日期农历与统计() {
        String content = SignFormUI.mainContent(D, "农历九月初八", "重阳节", false, 3, 12, "$120.0", 2, 10);
        assertTrue(content.contains("2026-10-17 星期六"));
        assertTrue(content.contains("农历九月初八"));
        assertTrue(content.contains("重阳节"));
        assertTrue(content.contains("今日未签到"));
        assertTrue(content.contains("连续: 3 天"));
        assertTrue(content.contains("累计: 12 次"));
        assertTrue(content.contains("$120.0"));
        assertTrue(content.contains("可补签: 2 个日期"));
        assertTrue(content.contains("每次 10 金币"));
    }

    @Test
    void 已签到状态正确渲染且无节日时不显示节日行() {
        String content = SignFormUI.mainContent(D, "农历九月初八", "", true, 7, 30, "$0.0", 0, 10);
        assertTrue(content.contains("今日已签到"));
        assertFalse(content.contains("今日节日"));
        assertTrue(content.contains("可补签: 无"));
    }

    @Test
    void 补签表单内容含消耗次数与冷却() {
        String content = SignFormUI.makeupContent(10, 1, 1);
        assertTrue(content.contains("消耗 10 金币"));
        assertTrue(content.contains("剩余次数: 1"));
        assertTrue(content.contains("间隔至少 1 天"));
    }

    @Test
    void 免费补签且无冷却时不显示对应文案() {
        String content = SignFormUI.makeupContent(0, -1, 0);
        assertTrue(content.contains("补签免费"));
        assertFalse(content.contains("剩余次数"));
        assertFalse(content.contains("间隔"));
    }

    @Test
    void 日期按钮文本含星期() {
        assertEquals("2026-10-14 星期三", SignFormUI.dateButton(LocalDate.of(2026, 10, 14)));
    }

    @Test
    void 主表单可正常构建且按钮齐全() {
        SimpleForm form = SimpleForm.builder()
                .title("每日签到")
                .content(SignFormUI.mainContent(D, "农历九月初八", "", false, 0, 0, "$0.0", 1, 10))
                .button("立即签到")
                .button("本周状态")
                .button("补签")
                .button("帮助")
                .build();
        assertEquals("每日签到", form.title());
        assertEquals(4, form.buttons().size());
        assertEquals("立即签到", form.buttons().get(0).text());
    }
}
