package xyz.chaihu.chsignin;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.chaihu.chsignin.calendar.HolidayService;
import xyz.chaihu.chsignin.command.QdCommand;
import xyz.chaihu.chsignin.data.DataManager;
import xyz.chaihu.chsignin.economy.EconomyHook;
import xyz.chaihu.chsignin.fortune.FortuneService;
import xyz.chaihu.chsignin.fortune.QuoteService;
import xyz.chaihu.chsignin.notify.GeyserHook;
import xyz.chaihu.chsignin.notify.JoinNotifier;
import xyz.chaihu.chsignin.sign.SignService;

import java.io.File;

/**
 * ChSignin 主类: 轻量化签到插件。
 * 仅 /qd 命令驱动, 无 GUI, 全聊天栏交互, 兼容 Geyser 跨平台玩家。
 */
public final class ChSigninPlugin extends JavaPlugin {

    private Settings settings;
    private DataManager dataManager;
    private EconomyHook economyHook;
    private HolidayService holidayService;
    private FortuneService fortuneService;
    private QuoteService quoteService;
    private SignService signService;
    private GeyserHook geyserHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        File quotesFile = new File(getDataFolder(), "quotes.yml");
        if (!quotesFile.exists()) {
            saveResource("quotes.yml", false);
        }

        settings = new Settings(getConfig());
        holidayService = new HolidayService();
        fortuneService = new FortuneService();
        quoteService = new QuoteService(quotesFile);
        dataManager = new DataManager(getDataFolder(), getLogger());

        economyHook = new EconomyHook();
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().severe("未检测到 Vault 插件, ChSignin 已禁用。");
            getLogger().severe("请先安装 Vault 以及任一经济插件(如 EssentialsX), 再启动 ChSignin。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (!economyHook.setup()) {
            // Vault 已安装, 但经济提供者可能由更晚启用的经济插件注册, 延迟重试绑定
            getLogger().warning("检测到 Vault, 但经济提供者尚未就绪, 将在启动后自动重试绑定。");
            scheduleEconomyRetry(60);
        }

        signService = new SignService(settings, economyHook);

        QdCommand qdCommand = new QdCommand(this);
        PluginCommand qd = getCommand("qd");
        if (qd != null) {
            qd.setExecutor(qdCommand);
            qd.setTabCompleter(qdCommand);
        }

        geyserHook = new GeyserHook(getServer().getPluginManager());
        getServer().getPluginManager().registerEvents(new JoinNotifier(this), this);

        if (geyserHook.present()) {
            getLogger().info("检测到 Geyser — 基岩玩家入服将弹出签到 UI, Java 玩家发送可点击签到提示。");
        } else {
            getLogger().info("未检测到 Geyser — 所有玩家入服发送可点击签到提示(安装 Geyser 后基岩玩家自动改用签到 UI)。");
        }
        getLogger().info("ChSignin 已启用 — 玩家输入 /qd 即可签到。");
    }

    /** 经济提供者延迟重试绑定: 每 2 秒一次, 最多 attempts 次, 失败则禁用插件。 */
    private void scheduleEconomyRetry(int attempts) {
        getServer().getScheduler().runTaskLater(this, new Runnable() {
            private int remaining = attempts;

            @Override
            public void run() {
                if (economyHook.setup()) {
                    getLogger().info("经济提供者绑定成功, 经济功能可用。");
                    return;
                }
                if (--remaining <= 0) {
                    getLogger().severe("始终未能找到 Vault 经济提供者(请确认已安装 EssentialsX 等经济插件), ChSignin 已禁用。");
                    getServer().getPluginManager().disablePlugin(ChSigninPlugin.this);
                    return;
                }
                getServer().getScheduler().runTaskLater(ChSigninPlugin.this, this, 40L);
            }
        }, 40L);
    }

    @Override
    public void onDisable() {
        if (dataManager != null) {
            dataManager.saveAll();
        }
        getLogger().info("ChSignin 已停用, 数据已落盘。");
    }

    /** /qd reload 使用: 重载主配置与一言库。 */
    public void reloadAll() {
        reloadConfig();
        settings.update(getConfig());
        quoteService.reload();
    }

    public Settings settings() {
        return settings;
    }

    public DataManager dataManager() {
        return dataManager;
    }

    public EconomyHook economyHook() {
        return economyHook;
    }

    public HolidayService holidayService() {
        return holidayService;
    }

    public FortuneService fortuneService() {
        return fortuneService;
    }

    public QuoteService quoteService() {
        return quoteService;
    }

    public SignService signService() {
        return signService;
    }

    public GeyserHook geyserHook() {
        return geyserHook;
    }
}
