package xyz.chaihu.chsignin.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import xyz.chaihu.chsignin.ChSigninPlugin;
import xyz.chaihu.chsignin.Settings;
import xyz.chaihu.chsignin.calendar.HolidayService;
import xyz.chaihu.chsignin.data.PlayerData;
import xyz.chaihu.chsignin.fortune.FortuneService;
import xyz.chaihu.chsignin.notify.GeyserBridge;
import xyz.chaihu.chsignin.notify.JoinNotifier;
import xyz.chaihu.chsignin.sign.SignService;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /qd 命令: 所有功能仅通过聊天栏分行输出, 动态数据统一斜体。
 */
public final class QdCommand implements CommandExecutor, TabCompleter {

    private static final String[] WEEK_CN = {"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};
    private static final String[] WEEK_SHORT = {"一", "二", "三", "四", "五", "六", "日"};

    private final ChSigninPlugin plugin;

    public QdCommand(ChSigninPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------- 格式化辅助 ----------

    /** 动态数据斜体渲染。 */
    private static String i(String value) {
        return "§o" + value + "§r";
    }

    private String prefix() {
        return plugin.settings().prefix();
    }

    private static String weekCn(LocalDate date) {
        return WEEK_CN[date.getDayOfWeek().getValue() - 1];
    }

    private void send(CommandSender sender, String... lines) {
        for (String line : lines) {
            sender.sendMessage(line);
        }
    }

    // ---------- 命令入口 ----------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefix() + "§c该命令只能由玩家执行。");
            return true;
        }
        if (!player.hasPermission("chsignin.use")) {
            player.sendMessage(prefix() + "§c你没有权限使用签到功能。");
            return true;
        }
        String sub = args.length == 0 ? "sign" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "sign", "qd" -> handleSign(player);
            case "help", "?" -> handleHelp(player);
            case "status", "info", "查询" -> handleStatus(player);
            case "bq", "makeup", "补签" -> handleMakeup(player, args);
            case "ui", "menu" -> handleUi(player);
            case "reload" -> handleReload(player);
            default -> handleHelp(player);
        }
        return true;
    }

    // ---------- /qd 签到 ----------

    private void handleSign(Player player) {
        LocalDate today = LocalDate.now();
        PlayerData data = plugin.dataManager().get(player.getUniqueId(), player.getName());
        SignService signService = plugin.signService();

        if (data.hasSigned(today)) {
            int streak = data.currentStreak(today);
            send(player,
                    prefix() + "§e你今天已经签过到啦",
                    " §7已连续签到 " + i(String.valueOf(streak)) + " §7天, 明天再来吧",
                    " §7漏签的日子可用 §o/qd bq §7补签");
            return;
        }

        SignService.SignResult result = signService.sign(player.getUniqueId(), data, today);
        plugin.dataManager().save(data);
        if (!result.ok()) {
            player.sendMessage(prefix() + "§c签到失败, 请稍后重试。");
            return;
        }

        Settings settings = plugin.settings();
        List<String> lines = new ArrayList<>();
        lines.add("§8━━━━━━━━ §b§l每日签到§r §8━━━━━━━━");
        lines.add("§7日期: " + i(today + " " + weekCn(today)) + "  §8" + i(plugin.holidayService().lunarText(today)));

        List<HolidayService.Holiday> holidays = plugin.holidayService().holidaysOf(today);
        if (!holidays.isEmpty()) {
            StringBuilder sb = new StringBuilder("§7今日节日: ");
            for (int idx = 0; idx < holidays.size(); idx++) {
                if (idx > 0) {
                    sb.append(" §8· ");
                }
                sb.append(holidays.get(idx).render());
            }
            lines.add(sb.toString());
        }

        lines.add("§a✔ 签到成功§7, 已连续签到 " + i(String.valueOf(result.streak())) + " §7天");
        lines.add("§6基础奖励: " + i("+" + result.baseReward()) + " §6金币");
        if (result.streakBonus() > 0) {
            lines.add("§d连续奖励: " + i("+" + result.streakBonus()) + " §7(达成 " + i(String.valueOf(result.streak())) + " §7天)");
        }
        lines.add("§7当前余额: " + i(plugin.economyHook().format(result.balanceAfter())));

        if (settings.fortuneEnabled()) {
            lines.addAll(fortuneLines(player));
        }
        if (settings.quoteEnabled()) {
            String quote = plugin.quoteService().of(today);
            if (quote != null) {
                lines.add("§e┈┈ §6每日一言 §e┈┈");
                lines.add("§7\"" + i(quote) + "§7\"");
            }
        }
        lines.add("§8━━━━━━━━━━━━━━━━━━━━━━");
        send(player, lines.toArray(new String[0]));
    }

    private List<String> fortuneLines(Player player) {
        FortuneService.Fortune f = plugin.fortuneService().forPlayer(player.getUniqueId(), LocalDate.now());
        List<String> lines = new ArrayList<>();
        lines.add("§d┈┈ §5每日运势 §d┈┈");
        lines.add("§7综合: " + i(f.stars(f.overall()))
                + "  §7爱情: " + i(f.stars(f.love()))
                + "  §7财富: " + i(f.stars(f.wealth())));
        lines.add("§7事业: " + i(f.stars(f.career()))
                + "  §7健康: " + i(f.stars(f.health())));
        lines.add("§7幸运色: " + f.luckyColorCode() + i(f.luckyColorName()) + "§r"
                + "  §7幸运数: " + i(String.valueOf(f.luckyNumber()))
                + "  §7财神位: " + i(f.luckyDirection()));
        lines.add("§a宜 " + i(String.join(" · ", f.suitable()))
                + "  §c忌 " + i(String.join(" · ", f.avoid())));
        return lines;
    }

    // ---------- /qd status ----------

    private void handleStatus(Player player) {
        LocalDate today = LocalDate.now();
        PlayerData data = plugin.dataManager().get(player.getUniqueId(), player.getName());
        Settings settings = plugin.settings();

        List<String> lines = new ArrayList<>();
        lines.add(prefix() + "§b§l签到状态");
        lines.add("§7日期: " + i(today + " " + weekCn(today)));

        // 本周签到格子
        LocalDate weekStart = settings.weekStartOf(today);
        StringBuilder header = new StringBuilder("§8");
        StringBuilder grid = new StringBuilder("§8");
        for (int d = 0; d < 7; d++) {
            LocalDate cur = weekStart.plusDays(d);
            String mark = data.hasSigned(cur) ? "§a✓" : "§8✗";
            String label = cur.isEqual(today) ? "§l" + WEEK_SHORT[d] + "§r" : "§7" + WEEK_SHORT[d];
            header.append(label).append("   ");
            grid.append(mark).append("   ");
        }
        lines.add("§7本周: " + header);
        lines.add("        " + grid);

        lines.add("§7连续签到: " + i(data.currentStreak(today) + " 天")
                + "  §7累计签到: " + i(String.valueOf(data.totalSigns()) + " 次"));
        lines.add("§7当前余额: " + i(plugin.economyHook().format(plugin.economyHook().balance(player.getUniqueId()))));

        if (settings.makeupEnabled()) {
            List<LocalDate> candidates = plugin.signService().makeupCandidates(data, today);
            if (!candidates.isEmpty()) {
                StringBuilder sb = new StringBuilder("§7可补签: ");
                for (int idx = 0; idx < candidates.size(); idx++) {
                    if (idx > 0) {
                        sb.append(" §8· ");
                    }
                    sb.append(i(candidates.get(idx).toString()));
                }
                sb.append(" §8(/qd bq 日期)");
                lines.add(sb.toString());
            }
        }
        send(player, lines.toArray(new String[0]));
    }

    // ---------- /qd bq ----------

    private void handleMakeup(Player player, String[] args) {
        Settings settings = plugin.settings();
        if (!settings.makeupEnabled()) {
            player.sendMessage(prefix() + "§c补签功能当前未开启。");
            return;
        }
        LocalDate today = LocalDate.now();
        PlayerData data = plugin.dataManager().get(player.getUniqueId(), player.getName());

        if (args.length < 2) {
            List<LocalDate> candidates = plugin.signService().makeupCandidates(data, today);
            if (candidates.isEmpty()) {
                player.sendMessage(prefix() + "§e本周没有可补签的日期, 保持全勤哦!");
                return;
            }
            send(player,
                    prefix() + "§7可补签日期: " + joinDates(candidates),
                    " §7使用方法: §o/qd bq " + candidates.get(0),
                    " §7每次补签消耗: " + i(String.valueOf(settings.makeupCost())) + " §7金币");
            return;
        }

        LocalDate target;
        try {
            target = LocalDate.parse(args[1].trim());
        } catch (DateTimeParseException e) {
            player.sendMessage(prefix() + "§c日期格式错误, 请使用 §oyyyy-MM-dd§c, 例如 §o" + today.minusDays(1));
            return;
        }

        SignService.MakeupResult result = plugin.signService()
                .makeup(player.getUniqueId(), data, target, today);
        if (result.ok()) {
            plugin.dataManager().save(data);
            List<String> lines = new ArrayList<>();
            lines.add(prefix() + "§a✔ 补签成功: " + i(target + " " + weekCn(target)));
            if (result.cost() > 0) {
                lines.add(" §7消耗: " + i("-" + result.cost()) + " §7金币");
            }
            if (result.reward() > 0) {
                lines.add(" §7奖励: " + i("+" + result.reward()) + " §7金币");
            }
            lines.add(" §7当前连续: " + i(result.streak() + " 天")
                    + "  §7余额: " + i(plugin.economyHook().format(result.balanceAfter())));
            send(player, lines.toArray(new String[0]));
            return;
        }
        player.sendMessage(prefix() + "§c补签失败: " + makeupFailText(result.failReason(), settings, today));
    }

    private static String makeupFailText(String reason, Settings settings, LocalDate today) {
        return switch (reason == null ? "" : reason) {
            case "DISABLED" -> "补签功能未开启。";
            case "FUTURE" -> "只能补签今天以前的日期。";
            case "OUT_OF_RANGE" -> settings.makeupCurrentWeekOnly()
                    ? "只能补签当周(自 §o" + settings.weekStartOf(today) + "§r§c)漏签的日期。"
                    : "只能补签最近 §o" + settings.makeupMaxDaysBack() + "§r§c 天内的日期。";
            case "ALREADY" -> "该日期已经签到过了。";
            case "ALREADY_MADEUP" -> "该日期已经补签过了。";
            case "COOLDOWN" -> "补签冷却中, 请 §o" + cooldownRemain(settings, today) + "§r§c 后再试。";
            case "WEEK_LIMIT" -> "本周补签次数已用完(每周上限 §o" + settings.makeupMaxPerWeek() + "§r§c 次)。";
            case "NO_MONEY" -> "金币不足, 补签需要 §o" + settings.makeupCost() + "§r§c 金币。";
            default -> "未知错误。";
        };
    }

    private static String cooldownRemain(Settings settings, LocalDate today) {
        return settings.makeupCooldownDays() + " 天";
    }

    private static String joinDates(List<LocalDate> dates) {
        StringBuilder sb = new StringBuilder();
        for (int idx = 0; idx < dates.size(); idx++) {
            if (idx > 0) {
                sb.append(" §8· ");
            }
            sb.append(i(dates.get(idx).toString()));
        }
        return sb.toString();
    }

    // ---------- /qd ui ----------

    private void handleUi(Player player) {
        if (plugin.geyserHook().bedrock(player.getUniqueId())) {
            GeyserBridge.showMain(plugin, player);
        } else {
            player.sendMessage(JoinNotifier.menuMessage(plugin.settings().prefix(), "§e签到快捷入口: "));
        }
    }

    // ---------- /qd reload ----------

    private void handleReload(Player player) {
        if (!player.hasPermission("chsignin.admin")) {
            player.sendMessage(prefix() + "§c你没有权限执行该操作。");
            return;
        }
        plugin.reloadAll();
        player.sendMessage(prefix() + "§a配置与一言库已重载。");
    }

    // ---------- /qd help ----------

    private void handleHelp(Player player) {
        send(player,
                "§8━━━━━━━━ §b§l签到帮助§r §8━━━━━━━━",
                " §a/qd §7— 每日签到, 领取奖励与运势",
                " §a/qd status §7— 查看本周签到与统计",
                " §a/qd bq §7— 查看可补签日期",
                " §a/qd bq <yyyy-MM-dd> §7— 补签指定日期",
                " §a/qd ui §7— 打开签到界面 §8(基岩版表单)",
                " §a/qd reload §7— 重载配置 §8(管理员)");
    }

    // ---------- Tab 补全 ----------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("help", "status", "bq", "ui", "reload"));
            return filter(subs, args[0]);
        }
        if (args.length == 2 && sender instanceof Player player
                && args[0].toLowerCase(Locale.ROOT).matches("bq|makeup|补签")) {
            LocalDate today = LocalDate.now();
            PlayerData data = plugin.dataManager().get(player.getUniqueId(), player.getName());
            List<String> dates = new ArrayList<>();
            for (LocalDate d : plugin.signService().makeupCandidates(data, today)) {
                dates.add(d.toString());
            }
            return filter(dates, args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String token) {
        String t = token.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(t)) {
                out.add(o);
            }
        }
        return out;
    }
}
