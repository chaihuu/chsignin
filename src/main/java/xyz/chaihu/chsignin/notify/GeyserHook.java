package xyz.chaihu.chsignin.notify;

import org.bukkit.plugin.PluginManager;

import java.util.UUID;

/**
 * Geyser 能力门面: 负责探测与路由, 本身不引用任何 Geyser/Cumulus 类,
 * 保证未安装 Geyser 的服务端也能安全加载插件。
 * 仅当 Geyser 确认存在时才触达 GeyserBridge(其类引用解析依赖 Geyser 提供的 Cumulus)。
 */
public final class GeyserHook {

    private final boolean present;

    public GeyserHook(PluginManager pluginManager) {
        boolean found = false;
        try {
            found = pluginManager.getPlugin("Geyser") != null
                    || pluginManager.getPlugin("Geyser-V") != null
                    || pluginManager.getPlugin("Geyser-Spigot") != null;
        } catch (Throwable ignored) {
        }
        this.present = found;
    }

    /** 服务端是否安装了 Geyser。 */
    public boolean present() {
        return present;
    }

    /** 该在线玩家是否通过 Geyser 接入的基岩版玩家。 */
    public boolean bedrock(UUID uuid) {
        return present && GeyserBridge.bedrock(uuid);
    }
}
