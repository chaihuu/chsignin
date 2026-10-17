package xyz.chaihu.chsignin.fortune;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * 每日运势: 完全本地、确定性生成 —— 同一玩家在同一天结果恒定,
 * 不同玩家/不同日期结果不同。无需任何外部服务。
 */
public final class FortuneService {

    public record Fortune(int overall, int love, int wealth, int career, int health,
                          String luckyColorName, String luckyColorCode, int luckyNumber,
                          String luckyDirection, List<String> suitable, List<String> avoid) {

        public String stars(int value) {
            StringBuilder sb = new StringBuilder(5);
            for (int i = 1; i <= 5; i++) {
                sb.append(i <= value ? '★' : '☆');
            }
            return sb.toString();
        }
    }

    private static final String[][] LUCKY_COLORS = {
            {"§c", "赤红"}, {"§6", "橙金"}, {"§e", "明黄"}, {"§a", "青绿"}, {"§b", "天青"},
            {"§9", "靛蓝"}, {"§d", "绛紫"}, {"§f", "素白"}, {"§8", "玄黑"}, {"§7", "银灰"}
    };

    private static final String[] DIRECTIONS = {"东", "南", "西", "北", "东南", "东北", "西南", "西北"};

    private static final String[] SUITABLE_POOL = {
            "挖矿", "远行", "交易", "交友", "附魔", "建筑", "垂钓", "耕作",
            "探险", "狩猎", "冥想", "助人", "学习", "采集", "下界远征", "村庄互助",
            "红石研究", "驯养动物", "整理仓库", "登高望远"
    };

    private static final String[] AVOID_POOL = {
            "夜行", "独探矿洞", "与苦力怕对视", "红石实验", "末地远征", "争吵",
            "挥霍", "赖床", "贪食", "冒进", "暴雨出海", "深夜挖矿",
            "轻信路人", "乱挖方块", "追逐末影珍珠"
    };

    /**
     * 生成指定玩家在指定日期的运势。
     */
    public Fortune forPlayer(UUID player, LocalDate date) {
        // 使用 UUID 位值而非 hashCode, 避免退化样本(如对称 UUID 的 hashCode 为 0)
        long seed = mix(player.getMostSignificantBits() ^ player.getLeastSignificantBits(), date.toEpochDay());
        SplittableRandom r = new SplittableRandom(seed);

        int overall = r.nextInt(1, 6);
        int love = r.nextInt(1, 6);
        int wealth = r.nextInt(1, 6);
        int career = r.nextInt(1, 6);
        int health = r.nextInt(1, 6);

        String[] color = LUCKY_COLORS[r.nextInt(LUCKY_COLORS.length)];
        int number = r.nextInt(1, 10);
        String direction = DIRECTIONS[r.nextInt(DIRECTIONS.length)];

        List<String> suitable = pickTwo(r, SUITABLE_POOL);
        List<String> avoid = pickTwo(r, AVOID_POOL);

        return new Fortune(overall, love, wealth, career, health,
                color[1], color[0], number, direction, suitable, avoid);
    }

    private static List<String> pickTwo(SplittableRandom r, String[] pool) {
        List<String> out = new ArrayList<>(2);
        int a = r.nextInt(pool.length);
        int b = r.nextInt(pool.length);
        out.add(pool[a]);
        if (b != a) {
            out.add(pool[b]);
        }
        return out;
    }

    /** 简单混合函数, 保证种子分布均匀。 */
    private static long mix(long a, long b) {
        long x = a ^ (b * 0x9E3779B97F4A7C15L);
        x ^= x >>> 30;
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 27;
        x *= 0x94D049BB133111EBL;
        x ^= x >>> 31;
        return x;
    }
}
