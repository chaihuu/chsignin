package xyz.chaihu.chsignin.data;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 轻量本地文件存储: 每位玩家一个 YAML 文件(data/players/<uuid>.yml),
 * 无数据库依赖, 内存中缓存, 修改即落盘。
 */
public final class DataManager {

    private final File playersDir;
    private final Logger logger;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public DataManager(File dataFolder, Logger logger) {
        this.playersDir = new File(dataFolder, "players");
        if (!playersDir.exists() && !playersDir.mkdirs()) {
            logger.warning("无法创建玩家数据目录: " + playersDir.getAbsolutePath());
        }
        this.logger = logger;
    }

    /** 获取(或首次加载)玩家数据。 */
    public PlayerData get(UUID uuid, String name) {
        return cache.computeIfAbsent(uuid, id -> load(id, name));
    }

    private PlayerData load(UUID uuid, String name) {
        PlayerData data = new PlayerData(uuid, name);
        File file = fileOf(uuid);
        if (!file.exists()) {
            return data;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String raw : cfg.getStringList("signed")) {
            LocalDate d = parse(raw);
            if (d != null) {
                data.restore(d, false);
            }
        }
        for (String raw : cfg.getStringList("makeup")) {
            LocalDate d = parse(raw);
            if (d != null) {
                data.restore(d, true);
            }
        }
        LocalDate last = parse(cfg.getString("last-makeup"));
        if (last != null) {
            data.setLastMakeupDate(last);
        }
        data.setTotalSigns(cfg.getLong("total-signs", 0));
        return data;
    }

    /** 保存玩家数据。 */
    public void save(PlayerData data) {
        File file = fileOf(data.uuid());
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("name", data.name());
        cfg.set("signed", toStringList(data.signedDates()));
        cfg.set("makeup", toStringList(data.makeupDates()));
        if (data.lastMakeupDate() != null) {
            cfg.set("last-makeup", data.lastMakeupDate().toString());
        }
        cfg.set("total-signs", data.totalSigns());
        try {
            file.getParentFile().mkdirs();
            cfg.save(file);
        } catch (IOException e) {
            logger.severe("保存玩家数据失败 " + data.uuid() + ": " + e.getMessage());
        }
    }

    /** 关闭时全量落盘。 */
    public void saveAll() {
        cache.values().forEach(this::save);
    }

    private static List<String> toStringList(java.util.Collection<LocalDate> dates) {
        List<String> out = new ArrayList<>(dates.size());
        for (LocalDate d : dates) {
            out.add(d.toString());
        }
        return out;
    }

    private File fileOf(UUID uuid) {
        return new File(playersDir, uuid + ".yml");
    }

    private static LocalDate parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
