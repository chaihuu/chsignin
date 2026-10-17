package xyz.chaihu.chsignin.notify;

import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.geyser.api.GeyserApi;
import xyz.chaihu.chsignin.ChSigninPlugin;
import xyz.chaihu.chsignin.ui.SignFormUI;

import java.util.UUID;

/**
 * Geyser/Cumulus 类引用集中桥接层: 只有当 Geyser 确认存在、且本类被首次触达时才会加载,
 * 所有外部调用点均位于 bedrock() 为真的分支内, 从而在未安装 Geyser 的环境下绝不触发类解析。
 */
public final class GeyserBridge {

    private GeyserBridge() {
    }

    /** 是否为基岩版(Geyser)在线玩家。 */
    public static boolean bedrock(UUID uuid) {
        try {
            GeyserApi api = GeyserApi.api();
            return api != null && api.connectionByUuid(uuid) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 推送表单, 成功返回 true。 */
    public static boolean sendForm(UUID uuid, SimpleForm form) {
        try {
            GeyserApi api = GeyserApi.api();
            return api != null && api.sendForm(uuid, form);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 向玩家弹出签到主菜单表单。 */
    public static void showMain(ChSigninPlugin plugin, Player player) {
        try {
            new SignFormUI(plugin).showMain(player);
        } catch (Throwable t) {
            plugin.getLogger().warning("签到表单推送异常: " + t);
        }
    }
}
