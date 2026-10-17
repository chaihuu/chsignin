package xyz.chaihu.chsignin.ui;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import xyz.chaihu.chsignin.ChSigninPlugin;
import xyz.chaihu.chsignin.Settings;
import xyz.chaihu.chsignin.calendar.HolidayService;
import xyz.chaihu.chsignin.data.PlayerData;
import xyz.chaihu.chsignin.notify.GeyserBridge;
import xyz.chaihu.chsignin.sign.SignService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 基岩版(Geyser)交互式签到 UI: 基于 Cumulus SimpleForm 的按钮式表单,
 * 覆盖全部签到命令场景(签到/状态/补签/帮助/重载), 操作直接复用 /qd 命令逻辑, 保证行为一致无遗漏。
 * 表单内为纯文本(基岩表单不渲染 § 颜色码), 命令执行结果仍以聊天栏彩色消息呈现。
 */
public final class SignFormUI {

    private static final String[] WEEK_CN = {"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};

    private final ChSigninPlugin plugin;

    public SignFormUI(ChSigninPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------- 纯数据渲染(可单元测试) ----------

    /** 主菜单表单正文。 */
    public static String mainContent(LocalDate today, String lunar, String holidayNames, boolean signedToday,
                                     int streak, long totalSigns, String balanceText,
                                     int makeupAvailable, int makeupCost) {
        StringBuilder sb = new StringBuilder();
        sb.append(today).append(' ').append(weekCn(today));
        if (lunar != null && !lunar.isBlank()) {
            sb.append("  ·  ").append(lunar);
        }
        sb.append('\n');
        if (holidayNames != null && !holidayNames.isBlank()) {
            sb.append("今日节日: ").append(holidayNames).append('\n');
        }
        sb.append("─────────────\n");
        sb.append(signedToday ? "状态: 今日已签到" : "状态: 今日未签到").append('\n');
        sb.append("连续: ").append(streak).append(" 天  |  累计: ").append(totalSigns).append(" 次\n");
        sb.append("余额: ").append(balanceText).append('\n');
        if (makeupAvailable > 0) {
            sb.append("可补签: ").append(makeupAvailable).append(" 个日期");
            if (makeupCost > 0) {
                sb.append(" (每次 ").append(makeupCost).append(" 金币)");
            }
        } else {
            sb.append("可补签: 无");
        }
        return sb.toString();
    }

    /** 补签表单正文。 */
    public static String makeupContent(int makeupCost, int remainQuota, int cooldownDays) {
        StringBuilder sb = new StringBuilder("请选择要补签的日期:\n\n");
        sb.append(makeupCost > 0 ? "每次补签消耗 " + makeupCost + " 金币" : "补签免费");
        if (remainQuota >= 0) {
            sb.append("  |  本周剩余次数: ").append(remainQuota);
        }
        if (cooldownDays > 0) {
            sb.append("\n两次补签间隔至少 ").append(cooldownDays).append(" 天");
        }
        return sb.toString();
    }

    /** 补签日期按钮文本。 */
    public static String dateButton(LocalDate date) {
        return date + " " + weekCn(date);
    }

    static String weekCn(LocalDate date) {
        return WEEK_CN[date.getDayOfWeek().getValue() - 1];
    }

    // ---------- 表单构建与交互 ----------

    /** 推送主菜单签到表单, 推送成功返回 true。 */
    public boolean showMain(Player player) {
        LocalDate today = LocalDate.now();
        PlayerData data = plugin.dataManager().get(player.getUniqueId(), player.getName());
        Settings s = plugin.settings();

        List<HolidayService.Holiday> holidays = plugin.holidayService().holidaysOf(today);
        String holidayNames = holidays.stream()
                .map(HolidayService.Holiday::name)
                .collect(Collectors.joining(" · "));
        List<LocalDate> candidates = plugin.signService().makeupCandidates(data, today);
        boolean signed = data.hasSigned(today);

        String content = mainContent(today, plugin.holidayService().lunarText(today), holidayNames, signed,
                data.currentStreak(today), data.totalSigns(), plugin.economyHook().format(
                        plugin.economyHook().balance(player.getUniqueId())),
                candidates.size(), s.makeupCost());

        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        // 按钮文案只使用基岩版字体确定支持的纯文本, 避免 emoji 在部分设备上渲染为方块
        labels.add(signed ? "今日已签到" : "立即签到");
        actions.add(() -> {
            player.performCommand("qd");
            if (!signed) {
                reShowMain(player);
            }
        });
        labels.add("本周状态");
        actions.add(() -> player.performCommand("qd status"));
        if (s.makeupEnabled()) {
            labels.add("补签");
            actions.add(() -> {
                if (!showMakeup(player)) {
                    fallback(player);
                }
            });
        }
        labels.add("帮助");
        actions.add(() -> player.performCommand("qd help"));
        if (player.hasPermission("chsignin.admin")) {
            labels.add("重载配置");
            actions.add(() -> player.performCommand("qd reload"));
        }

        return GeyserBridge.sendSimpleForm(player.getUniqueId(), "每日签到", content, labels,
                id -> onButton(player, actions, id), plugin.getLogger());
    }

    /** 推送补签日期选择表单, 推送成功返回 true。 */
    public boolean showMakeup(Player player) {
        LocalDate today = LocalDate.now();
        PlayerData data = plugin.dataManager().get(player.getUniqueId(), player.getName());
        Settings s = plugin.settings();
        List<LocalDate> candidates = plugin.signService().makeupCandidates(data, today);
        int weekStartQuota = data.makeupCountSince(s.weekStartOf(today));
        int remainQuota = s.makeupMaxPerWeek() - weekStartQuota;

        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (LocalDate c : candidates) {
            labels.add(dateButton(c));
            actions.add(() -> {
                player.performCommand("qd bq " + c);
                reShowMain(player);
            });
        }
        labels.add("返回主菜单");
        actions.add(() -> {
            if (!showMain(player)) {
                fallback(player);
            }
        });

        return GeyserBridge.sendSimpleForm(player.getUniqueId(), "补签",
                makeupContent(s.makeupCost(), remainQuota, s.makeupCooldownDays()), labels,
                id -> onButton(player, actions, id), plugin.getLogger());
    }

    private void onButton(Player player, List<Runnable> actions, int buttonId) {
        // Geyser 回调来自其内部线程, 一切服务端操作切回主线程执行
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || buttonId < 0 || buttonId >= actions.size()) {
                return;
            }
            actions.get(buttonId).run();
        });
    }

    private void reShowMain(Player player) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !showMain(player)) {
                fallback(player);
            }
        }, 1L);
    }

    /** 表单推送失败时的降级提示: 基岩聊天栏不支持点击事件, 因此只给出可直接输入的命令。 */
    public void fallback(Player player) {
        plugin.getLogger().warning("基岩签到表单推送失败, 玩家 " + player.getName() + " 已降级为命令提示。");
        player.sendMessage(LegacyComponentSerializer.legacySection().deserialize(
                plugin.settings().prefix() + "§e签到界面暂时无法打开, 请直接输入 "
                        + "§a/qd §e签到, §a/qd status §e查看状态, §a/qd bq §e补签。"));
    }
}
