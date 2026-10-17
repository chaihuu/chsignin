package xyz.chaihu.chsignin.fortune;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 每日一言: 完全本地化的中文句子库(quotes.yml), 按日期确定性选取,
 * 全服同一天展示同一句。
 */
public final class QuoteService {

    private final File file;
    private volatile List<String> quotes = List.of();

    public QuoteService(File file) {
        this.file = file;
        reload();
    }

    /** 从 quotes.yml 重新加载句子库。 */
    public void reload() {
        if (!file.exists()) {
            quotes = List.of();
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        List<String> loaded = new ArrayList<>(cfg.getStringList("quotes"));
        loaded.removeIf(s -> s == null || s.isBlank());
        quotes = List.copyOf(loaded);
    }

    /** 返回指定日期的每日一言; 句子库为空时返回 null。 */
    public String of(LocalDate date) {
        List<String> current = quotes;
        if (current.isEmpty()) {
            return null;
        }
        int index = (int) Math.floorMod(date.toEpochDay() * 31L + 7L, current.size());
        return current.get(index);
    }

    public int size() {
        return quotes.size();
    }
}
