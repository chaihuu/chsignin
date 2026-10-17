package xyz.chaihu.chsignin.notify;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import xyz.chaihu.chsignin.ChSigninPlugin;
import xyz.chaihu.chsignin.data.PlayerData;

import java.time.LocalDate;

/**
 * 入服签到提醒: 仅针对当日尚未签到的玩家, 按接入端差异化触发 —
 * 基岩版(Geyser)弹出交互式签到 UI, Java 版发送携带 ClickEvent 的可点击提示。
 */
public final class JoinNotifier implements Listener {

    /** 入服提醒延迟(刻), 等待客户端完成地形加载, 避免表单在加载界面被丢弃。 */
    private static final long DELAY_TICKS = 60L;
    /** 表单推送失败后的重试间隔(刻)。 */
    private static final long RETRY_TICKS = 40L;
    /** 基岩表单最多推送次数(首次 + 1 次重试)。 */
    private static final int MAX_FORM_ATTEMPTS = 2;

    private final ChSigninPlugin plugin;

    public JoinNotifier(ChSigninPlugin plugin) {
        this.plugin = plugin;
    }

    /** 是否需要提醒(纯逻辑, 可单元测试): 当日未签到才提醒。 */
    public static boolean shouldRemind(PlayerData data, LocalDate today) {
        return !data.hasSigned(today);
    }

    /** Java 版可点击签到提示消息(纯构建, 可单元测试)。 */
    public static Component reminderMessage(String prefix) {
        return menuMessage(prefix, "§e你今天还没有签到哦! ");
    }

    /** Java 版签到快捷入口消息(/qd ui 使用)。 */
    public static Component menuMessage(String prefix, String intro) {
        return LegacyComponentSerializer.legacySection().deserialize(prefix)
                .append(legacy(intro))
                .append(click("§a[ 立即签到 ]", "/qd", "§a点击完成每日签到\n§7领取奖励与今日运势"))
                .append(legacy("§7 | "))
                .append(click("§b[ 查看状态 ]", "/qd status", "§b点击查看本周签到情况"))
                .append(legacy("§7 | "))
                .append(click("§d[ 去补签 ]", "/qd bq", "§d补签漏掉的日子"));
    }

    private static Component legacy(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(text);
    }

    private static Component click(String text, String command, String hover) {
        return legacy(text)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(
                        LegacyComponentSerializer.legacySection().deserialize(hover)));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        LocalDate today = LocalDate.now();
        PlayerData data = plugin.dataManager().get(player.getUniqueId(), player.getName());
        if (!shouldRemind(data, today)) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            // 延迟期间可能已通过其他途径签到, 再次确认
            if (!shouldRemind(plugin.dataManager().get(player.getUniqueId(), player.getName()), LocalDate.now())) {
                return;
            }
            if (plugin.geyserHook().bedrock(player.getUniqueId())) {
                showBedrockUi(player, 1);
            } else {
                player.sendMessage(reminderMessage(plugin.settings().prefix()));
            }
        }, DELAY_TICKS);
    }

    /** 推送基岩签到表单; 失败时延迟重试一次, 仍失败则降级为命令提示。 */
    private void showBedrockUi(Player player, int attempt) {
        if (!player.isOnline()) {
            return;
        }
        if (!shouldRemind(plugin.dataManager().get(player.getUniqueId(), player.getName()), LocalDate.now())) {
            return;
        }
        if (GeyserBridge.showMain(plugin, player)) {
            return;
        }
        if (attempt < MAX_FORM_ATTEMPTS) {
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> showBedrockUi(player, attempt + 1), RETRY_TICKS);
            return;
        }
        plugin.getLogger().warning("玩家 " + player.getName() + " 的签到表单连续 "
                + MAX_FORM_ATTEMPTS + " 次推送失败, 已降级为命令提示。");
        GeyserBridge.fallback(plugin, player);
    }
}
