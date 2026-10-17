# ChSignin

轻量化的 Minecraft Bukkit/Paper 签到插件。无 GUI,所有交互仅通过 `/qd` 命令与聊天栏完成,兼容 Geyser 基岩端。

> MineBBS 内容页:[https://www.minebbs.com/resources/chsignin-minecraft-bukkit-paper.18614/](https://www.minebbs.com/resources/chsignin-minecraft-bukkit-paper.18614/)

## 功能

- **每日签到** `/qd` — 分行展示日期、星期、农历、今日节日、签到奖励、连续天数、每日运势、每日一言
- **签到状态** `/qd status` — 本周签到格子、连续天数、累计次数、当前余额、可补签日期
- **补签** `/qd bq [日期]` — 默认仅可补签当周漏签日期;冷却、时间范围、消耗、每周次数上限均可配置
- **帮助 / 重载** `/qd help`、`/qd reload`(管理员权限)
- **命令别名与补全** — 支持 `/qiandao`、`/sign`;Tab 补全含可补签日期联想
- **内置节日数据库** — 公历法定节日 + 农历传统节日(1900–2100 农历算法,含闰月)+ 清明/冬至节气推算,每个节日带专属颜色
- **每日运势** — 按玩家 UUID + 日期确定性生成(同玩家同天恒定),含五维星级、幸运色/幸运数/财神方位、宜忌
- **每日一言** — 本地 `quotes.yml`(默认 60 条真实中文名句),全服同日同句,可自由增删
- **Vault 经济集成** — 签到奖励自动入账、补签消耗自动扣除;未安装 Vault 时优雅禁用并给出明确提示
- **奖励配置** — 随机区间 / 固定数额两种模式;连续签到阶梯额外奖励
- **轻量存储** — 每位玩家一个 YAML 文件,内存缓存、修改即落盘,无数据库
- **零运行期依赖** — 除 Vault API(provided)外无任何第三方库,jar 仅约 20KB

## 许可

[GPL-3.0](LICENSE)
