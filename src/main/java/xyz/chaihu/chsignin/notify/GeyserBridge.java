package xyz.chaihu.chsignin.notify;

import org.bukkit.entity.Player;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.util.MinecraftVersion;
import xyz.chaihu.chsignin.ChSigninPlugin;
import xyz.chaihu.chsignin.ui.SignFormUI;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Geyser/Cumulus 类引用集中桥接层: 只有当 Geyser 确认存在、且本类被首次触达时才会加载,
 * 所有外部调用点均位于 bedrock() 为真的分支内, 从而在未安装 Geyser 的环境下绝不触发类解析。
 *
 * <p>Cumulus 表单类型不参与本插件的编译期签名, 而是通过 Geyser 连接对象所属的类加载器反射加载。
 * 这样即使服务端存在多份 Cumulus(插件隔离、库目录或其他插件附带), 也不会出现
 * "loader constraint violation: different Class objects for org.geysermc.cumulus.form.Form"。</p>
 */
public final class GeyserBridge {

    private static final String CUMULUS_FORM = "org.geysermc.cumulus.form.Form";
    private static final String CUMULUS_SIMPLE_FORM = "org.geysermc.cumulus.form.SimpleForm";
    private static final String CUMULUS_SIMPLE_FORM_BUILDER = "org.geysermc.cumulus.form.SimpleForm$Builder";
    private static final String CUMULUS_SIMPLE_RESPONSE = "org.geysermc.cumulus.response.SimpleFormResponse";
    private static final String GEYSER_CONNECTION = "org.geysermc.geyser.api.connection.GeyserConnection";

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

    /**
     * 通过 Geyser 连接所属类加载器反射构建并推送 SimpleForm。
     * 表单类型不进入本插件 Jar 的类引用, 从根源规避 Cumulus 重复导致的 LinkageError。
     */
    public static boolean sendSimpleForm(UUID uuid, String title, String content, List<String> buttons,
                                         IntConsumer onClick, Logger logger) {
        try {
            GeyserApi api = GeyserApi.api();
            if (api == null) {
                return false;
            }
            Object connection = api.connectionByUuid(uuid);
            if (connection == null) {
                return false;
            }

            ClassLoader loader = connection.getClass().getClassLoader();
            if (loader == null) {
                return false;
            }

            Class<?> formClass = Class.forName(CUMULUS_FORM, true, loader);
            Class<?> simpleFormClass = Class.forName(CUMULUS_SIMPLE_FORM, true, loader);
            Class<?> builderClass = Class.forName(CUMULUS_SIMPLE_FORM_BUILDER, true, loader);
            Class<?> responseClass = Class.forName(CUMULUS_SIMPLE_RESPONSE, true, loader);
            Class<?> connectionClass = Class.forName(GEYSER_CONNECTION, true, loader);

            Object builder = simpleFormClass.getMethod("builder").invoke(null);
            builderClass.getMethod("title", String.class).invoke(builder, title);
            builderClass.getMethod("content", String.class).invoke(builder, content);

            Method button = builderClass.getMethod("button", String.class);
            for (String label : buttons) {
                button.invoke(builder, label);
            }

            Method clickedButtonId = responseClass.getMethod("clickedButtonId");
            builderClass.getMethod("validResultHandler", Consumer.class)
                    .invoke(builder, (Consumer<Object>) response -> {
                        try {
                            int id = (Integer) clickedButtonId.invoke(response);
                            onClick.accept(id);
                        } catch (Throwable t) {
                            logger.log(Level.WARNING, "签到表单按钮回调解析失败", t);
                        }
                    });

            Object form = builderClass.getMethod("build").invoke(builder);
            Object result = connectionClass.getMethod("sendForm", formClass).invoke(connection, form);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable t) {
            logger.log(Level.WARNING, "签到表单发送失败(Geyser/Cumulus 反射调用异常)", t);
            return false;
        }
    }

    /** 向玩家弹出签到主菜单表单, 成功返回 true。 */
    public static boolean showMain(ChSigninPlugin plugin, Player player) {
        try {
            return new SignFormUI(plugin).showMain(player);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "签到表单构建/推送异常", t);
            return false;
        }
    }

    /** 表单链路不可用时的命令降级提示。 */
    public static void fallback(ChSigninPlugin plugin, Player player) {
        try {
            new SignFormUI(plugin).fallback(player);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "签到降级提示发送异常", t);
        }
    }

    /** 当前 Geyser 要求的 Java 版服务端版本(如 26.3); 无法获取时返回 null。 */
    public static String supportedJavaVersion() {
        try {
            GeyserApi api = GeyserApi.api();
            if (api == null) {
                return null;
            }
            MinecraftVersion version = api.supportedJavaVersion();
            return version == null ? null : version.versionString();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
