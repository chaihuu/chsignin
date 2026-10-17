# 基岩版 UI 实测失败问题处理文档

| 项目 | 内容 |
|---|---|
| 文档日期 | 2026-10-17 |
| 适用版本 | ChSignin 1.1.2（问题版本 1.1.0 / 1.1.1） |
| 问题域 | 基岩版（Geyser + Cumulus SimpleForm）签到 UI |
| 结论 | 实测失败包含两类原因：运行环境版本不匹配导致基岩客户端在登录阶段被拒绝；以及 1.1.1 现场出现的 Cumulus 类加载冲突（LinkageError）。1.1.2 已同时修复环境自检、表单可靠性与类引用隔离问题 |

---

## 一、失败场景梳理

### 1.1 复现环境

| 项 | 环境 A（失败） | 环境 B（UI 链路验证） |
|---|---|---|
| 操作系统 | Windows 11 10.0（amd64） | Windows 11 10.0（amd64） |
| Java 运行时 | Java 25.0.3（Oracle HotSpot） | Java 25.0.3 |
| Java 版服务端 | Paper 26.1.2-74（Minecraft 26.1.2） | Paper 26.2-134（Minecraft 26.2） |
| ViaVersion | 未安装 | ViaVersion 5.12.1 |
| Geyser | Geyser-Spigot 2.12.0-SNAPSHOT（b1262，git-master-f08128f） | 同左 |
| ChSignin | 1.1.0 | 1.1.0（修复前复测） |
| 基岩客户端 | Minecraft Bedrock 26.52.3（本机客户端） | 协议级基岩客户端 1.26.51（bedrock-protocol 3.59.0） |
| 网络入口 | `127.0.0.1:19132`（UDP/RakNet） | `127.0.0.1:19132`（UDP/RakNet） |

### 1.2 异常表现

**环境 A（失败）**

1. 基岩客户端可以完成服务器列表 Ping，显示 MOTD 与版本 `26.52`。
2. 点击进入服务器后，在“正在连接/加载地形”阶段被断开，无法进入世界。
3. 客户端收到的断开原因（原文）：

   ```text
   This server does not support Java Edition 26.3, which is required for Geyser to connect.
   The server needs to update or have the ViaVersion plugin installed.
   Original disconnect message: §rOutdated server! I'm still on 26.1.2
   ```

4. 服务端 Geyser 启动日志同步出现：

   ```text
   您的服务器软件不支持 Geyser 需要的 Java 版本 (26.3)。
   请安装 ViaVersion 或更新您的服务器软件！
   ```

5. 因为玩家从未成功进入 Java 服务器，`PlayerJoinEvent` 不会触发，**签到表单 UI 不会出现**。表现上等价于“基岩版 UI 实测失败”，但根因不在表单渲染代码。

**环境 B（UI 链路验证）**

在补齐 ViaVersion 后，基岩协议客户端可正常进入服务器，并观察到：

1. 入服后自动收到 `modal_form_request`（form_id=0），标题“每日签到”，内容含日期、农历、状态、连续/累计、余额、可补签数量，按钮 4 个。
2. 点击“立即签到”后，聊天栏输出完整签到结果，并收到刷新后的 form_id=1（状态变为“今日已签到”，连续 1 天，余额同步更新）。
3. 点击“本周状态”后，聊天栏输出本周格子、连续/累计、余额、可补签日期。
4. 点击“补签”后收到补签子表单（5 个候选日期 + “返回主菜单”）；点选日期后正确执行补签逻辑（测试账号余额不足时提示“金币不足”，并刷新主菜单）。

### 1.3 触发条件与影响范围

| 触发条件 | 影响 |
|---|---|
| Geyser 2.12+ 搭配低于其要求的 Java 版服务端（本次为 Paper 26.1.2），且未安装 ViaVersion | 现代基岩客户端登录被拒，所有基岩 UI/命令均不可用 |
| Geyser API 推送异常（版本差异、表单冲突等） | 1.1.0 中异常被 `catch (Throwable ignored)` 静默吞掉，仅降级为聊天提示，服务端无堆栈可查 |
| 基岩客户端仍在加载地形 | 1.1.0 仅在入服 30 tick（1.5 秒）后推送表单，慢设备上存在表单被丢弃的风险 |
| 按钮使用 emoji（📊/🗓/❓/♻ 等） | 部分基岩设备字体不含对应字形，按钮显示为方块或空白 |
| 表单推送失败 | 1.1.0 的降级文案声称“可点击 [立即签到]/[查看帮助]”，但该消息没有 ClickEvent，基岩聊天栏也不支持 Java 点击事件，提示不可用 |

---

## 二、根因分析

### 2.1 直接根因：运行环境版本不匹配（已复现）

Geyser 2.12.0 对 Java 版服务端的最低要求为 **26.3**。环境 A 使用 Paper 26.1.2 且未安装 ViaVersion，Geyser 在登录阶段直接拒绝基岩客户端：

```text
Outdated server! I'm still on 26.1.2
```

该拒绝发生在 Java 服务器登录之前，`JoinNotifier`、`SignFormUI` 均不会执行。因此：

- 不是表单 JSON 构造错误；
- 不是按钮索引/回调绑定错误；
- 不是 Geyser API 方法缺失；
- 而是**测试服务端版本低于 Geyser 要求，UI 链路未被触达**。

### 2.2 插件层健壮性缺陷（放大故障、阻碍定位）

1. **异常静默**：`GeyserBridge.sendForm`/`showMain` 使用 `catch (Throwable ignored)`，Geyser API 版本差异或表单冲突时无任何堆栈，现场只能看到“表单未弹出”。
2. **降级提示误导**：失败提示使用普通字符串拼接出 `[立即签到]`、`[查看帮助]`，没有 `ClickEvent`，且基岩聊天栏不支持 Java 点击事件，玩家无法按提示操作。
3. **入服推送偏早**：固定 30 tick 延迟对手机/主机等慢加载设备不够，表单可能在加载界面被客户端丢弃。
4. **按钮 emoji 兼容性**：`📊`、`🗓`、`❓`、`♻` 等 emoji 不在基岩默认字体字形集中，存在显示为方块的风险；表单本身是纯文本渲染，不应依赖 emoji。
5. **无失败重试**：`sendForm` 返回 `false` 时只降级，不重试，偶发的表单冲突/时序问题会被直接放大为“UI 不可用”。

### 2.3 已排除的原因

- **Cumulus/Geyser API 方法缺失**：运行期 `GeyserApiBase#sendForm(UUID, Form)`、`SimpleForm$Builder`、`SimpleFormResponse#clickedButtonId()` 均存在且可正常调用。
- **按钮与动作索引错位**：主菜单、补签子菜单的 labels/actions 平行构建，按钮点击后命令执行与表单刷新均正常。
- **线程模型错误**：表单回调切回 Paper 主线程执行命令，实测签到/补签/状态均成功。
- **未安装 Geyser 时的类加载安全**：`GeyserBridge` 不会被触达，Java 版链路不受影响。

### 2.4 后续实测暴露的类加载冲突（LinkageError）

现场日志：

```text
java.lang.LinkageError: loader constraint violation: when resolving interface method
'boolean org.geysermc.geyser.api.GeyserApi.sendForm(java.util.UUID, org.geysermc.cumulus.form.Form)'
the class loader 'ChSignin-1.1.1.jar' of the current class, xyz/chaihu/chsignin/notify/GeyserBridge,
and the class loader 'Geyser-Spigot.jar' for the method's defining class, org/geysermc/geyser/api/GeyserApi,
have different Class objects for the type org/geysermc/cumulus/form/Form used in the signature
    at xyz.chaihu.chsignin.notify.GeyserBridge.sendForm(GeyserBridge.java:40)
```

**根因**：服务端运行期存在两份可见的 Cumulus `Form` 类型（或插件类加载器与 Geyser 类加载器各自定义了同名类）。即使 ChSignin 的 Jar 内没有 `org/geysermc/cumulus/*`，只要生产代码的**方法签名直接引用 Cumulus 类型**，JVM 在解析 `GeyserApi#sendForm(UUID, Form)` 时仍会进行跨类加载器的类型一致性校验，从而抛出 `LinkageError`。这与“Jar 是否打包 Cumulus”是两个独立问题：前者是类引用隔离，后者是产物内容。

**规避策略**：生产代码不出现任何 Cumulus 类型引用，表单改为通过 Geyser 连接对象所属的类加载器反射构建与发送。这样 `Form`/`SimpleForm` 始终由 Geyser 一侧加载，方法签名两侧使用同一个 Class 对象。

---

## 三、修复方案

### 3.1 代码修改

| 文件 | 修改内容 |
|---|---|
| `src/main/java/xyz/chaihu/chsignin/ChSigninPlugin.java` | 新增启动期 Geyser/服务端版本自检：当 `GeyserApi#supportedJavaVersion()` 高于当前服务端版本时输出三条可操作告警（升级服务端或安装 ViaVersion）；已安装 ViaVersion 时改为记录兼容信息，避免误报 |
| `src/main/java/xyz/chaihu/chsignin/notify/GeyserBridge.java` | 移除所有 Cumulus 编译期类型；`sendForm` 改为 `sendSimpleForm`，通过连接对象所属类加载器反射加载 `Form`/`SimpleForm`/`Builder`/`SimpleFormResponse` 并调用 `sendForm`；异常输出完整堆栈；新增 `supportedJavaVersion()` 与 `fallback()` 桥接 |
| `src/main/java/xyz/chaihu/chsignin/notify/JoinNotifier.java` | 入服延迟由 30 tick 提升至 60 tick（3 秒）；表单推送失败后间隔 40 tick 重试 1 次；连续 2 次失败才降级，并记录玩家名与失败次数 |
| `src/main/java/xyz/chaihu/chsignin/ui/SignFormUI.java` | 移除 `SimpleForm` 依赖，改为传入标题/正文/按钮/回调给 `GeyserBridge.sendSimpleForm`；按钮文案去除 emoji；降级提示改为可直接输入的命令说明 |
| `src/main/java/xyz/chaihu/chsignin/command/QdCommand.java` | `/qd ui` 推送失败时调用统一降级提示 |
| `src/test/java/xyz/chaihu/chsignin/ui/SignFormUITest.java` | 同步按钮文案断言 |
| `pom.xml` | 版本号 1.1.0 → 1.1.2；从 Geyser API 中排除传递的 Cumulus，并显式声明 `org.geysermc.cumulus:cumulus:1.1.2` 为 `provided`（仅测试/编译校验，生产代码反射访问） |
| `src/main/resources/plugin.yml` | 版本号 1.1.0 → 1.1.2；`softdepend` 增加 `Geyser-Spigot`，与实际插件名对齐 |

### 3.2 版本要求（部署侧修复）

使用 Geyser 2.12+ 时，必须满足以下任一条件，否则现代基岩客户端无法登录：

- Java 版服务端升级到 **26.3+**；或
- 安装并启用 **ViaVersion**（本次验证使用 5.12.1）。

该要求已写入 README 与《功能说明文档》，插件启动时也会主动输出告警。

### 3.3 兼容性保障

- **Java 版玩家**：命令、消息、权限、经济逻辑均未改动。
- **未安装 Geyser**：`GeyserBridge` 仍不会被类加载，入服走原有可点击消息，无 `NoClassDefFoundError`。
- **表单行为**：按钮对应的 `performCommand` 命令语义未变，仅调整展示文案与失败处理。
- **无新增运行期依赖**：仍为 `paper-api`、`VaultAPI`、`geyser-api` 全部 `provided`。
- **配置项**：无新增必填配置，旧版 `config.yml` 可直接使用。

---

## 四、验证结果

### 4.1 环境 A：问题复现

- 基岩 26.52 客户端连接 Paper 26.1.2 + Geyser 2.12.0：被拒绝，断开原因为 `Outdated server! I'm still on 26.1.2`。
- 服务端 Geyser 日志给出“需要 Java 版本 26.3 / 安装 ViaVersion”告警。
- 结论：与 2.1 根因一致，UI 未进入执行链路。

### 4.2 环境 B：UI 链路回归（修复前基线）

使用 Paper 26.2 + ViaVersion 5.12.1 + Geyser 2.12.0，通过协议级基岩客户端验证：

| 用例 | 结果 |
|---|---|
| 未签到玩家入服自动弹出主菜单表单 | 通过 |
| 主表单内容（日期/农历/状态/连续/累计/余额/可补签） | 通过 |
| 点击“立即签到”执行签到并刷新为已签到状态 | 通过 |
| 点击“本周状态”输出本周签到格子与统计 | 通过 |
| 点击“补签”进入日期选择子表单 | 通过 |
| 点选补签日期执行补签（余额不足时正确提示）并返回主菜单 | 通过 |

说明：该轮验证证明表单构造、按钮索引、回调线程切换、命令复用均无逻辑缺陷；失败只发生在环境 A 的登录阶段。

### 4.3 修复后验证边界

- 本地构建与单元测试已执行（见第五章）。
- 按需求跳过剩余真机点选与管理员“重载配置”按钮复测；修复已覆盖推送失败重试、异常堆栈与命令降级路径。
- 建议上线前在目标设备上补做一次真机确认：入服 3 秒内弹出表单、四个按钮可点、签到后主菜单自动刷新。

---

## 五、构建与产物

```powershell
C:\Users\Ding\bukkitpluginsdev\.tools\apache-maven-3.9.9\bin\mvn.cmd clean package
```

- 编译目标：Java 21（`maven.compiler.release=21`），本机构建使用 JDK 25。
- 产物：`target/ChSignin-1.1.2.jar`（65,588 字节）
- 单元测试：42 项全部通过（0 失败 / 0 错误 / 0 跳过）
- SHA256：`BC32FE0B864019E4F2D50184421293428936A2EA7A17EBC7BA2429EB68975DC3`
- 构建时间：2026-10-17 23:50:30

### 5.1 Cumulus 依赖与产物隔离验证

`pom.xml` 中将 Cumulus 从 Geyser API 的传递依赖中排除，并显式声明为 `provided`：

```xml
<dependency>
    <groupId>org.geysermc.cumulus</groupId>
    <artifactId>cumulus</artifactId>
    <version>1.1.2</version>
    <scope>provided</scope>
</dependency>
```

依赖树确认：

```text
+- org.geysermc.geyser:api:jar:2.11.3-SNAPSHOT:provided
+- org.geysermc.cumulus:cumulus:jar:1.1.2:provided
```

产物检查（`jar tf target/ChSignin-1.1.2.jar`）：

| 检查项 | 结果 |
|---|---|
| `org/geysermc/cumulus/*` 条目数 | 0 |
| `org/geysermc/*` 条目数 | 0 |
| Jar 内 class 根路径 | 仅 `xyz/chaihu/chsignin` |

结论：Cumulus 类完全由运行期 Geyser 插件提供，ChSignin 产物中无重复副本。

### 5.2 LinkageError 修复验证（类引用隔离）

除 `jar tf` 外，进一步检查编译产物的常量池，确认生产类不再引用 Cumulus 类型（仅保留反射使用的字符串类名）：

```powershell
javap -classpath target/classes -v -p xyz.chaihu.chsignin.notify.GeyserBridge |
  Select-String '= Class .*cumulus'
```

| 检查项 | 结果 |
|---|---|
| `GeyserBridge.class` 中 `= Class org/geysermc/cumulus/*` 引用 | 0 |
| `SignFormUI.class` 中 `cumulus` 引用 | 0 |
| Cumulus 相关条目形式 | 仅为反射使用的 `String` 常量（如 `org.geysermc.cumulus.form.Form`） |

运行时调用链改为：

```text
GeyserApi.api()
  -> connectionByUuid(uuid)                     // Geyser 侧连接对象
  -> connection.getClass().getClassLoader()     // 与 Geyser 一致的类加载器
  -> Class.forName("org.geysermc.cumulus...")   // 由 Geyser 侧加载
  -> GeyserConnection#sendForm(Form)            // 两侧 Form 为同一 Class 对象
```

因此不再触发 `loader constraint violation`，即使服务端存在多份 Cumulus 也只会使用 Geyser 自身的那一份。

另使用 Geyser-Spigot 2.12.0 内置 Cumulus 做了反射冒烟测试：`SimpleForm.builder()`、`title`、`content`、`button`、`validResultHandler`、`build` 全部调用成功，输出 `SMOKE_OK`。

---

## 六、追溯信息

### 6.1 关键代码位置

- 启动版本自检：[ChSigninPlugin.java](../src/main/java/xyz/chaihu/chsignin/ChSigninPlugin.java)
- Geyser 桥接、反射表单与异常日志：[GeyserBridge.java](../src/main/java/xyz/chaihu/chsignin/notify/GeyserBridge.java)
- 入服提醒/重试/降级：[JoinNotifier.java](../src/main/java/xyz/chaihu/chsignin/notify/JoinNotifier.java)
- 表单构建与按钮：[SignFormUI.java](../src/main/java/xyz/chaihu/chsignin/ui/SignFormUI.java)
- `/qd ui` 入口：[QdCommand.java](../src/main/java/xyz/chaihu/chsignin/command/QdCommand.java)
- 表单渲染单测：[SignFormUITest.java](../src/test/java/xyz/chaihu/chsignin/ui/SignFormUITest.java)

### 6.2 现场排查顺序

1. 服务端启动日志搜索 `Geyser`：确认是否出现“需要 Java 版本 26.3 / ViaVersion”告警。
2. 基岩客户端断开原因是否包含 `Outdated server`：若是，先升级服务端或安装 ViaVersion，不要排查表单代码。
3. 搜索 `ChSignin` 告警：`签到表单发送失败(Geyser API 异常)`、`签到表单发送失败(Geyser/Cumulus 反射调用异常)`、`签到表单构建/推送异常`、`基岩签到表单推送失败`。
4. 若出现 `LinkageError: ... different Class objects for org.geysermc.cumulus.form.Form`，确认运行的是 1.1.2 修复版；该版本生产代码已不引用 Cumulus 类型，改用 Geyser 连接的类加载器反射构建表单。
5. 确认玩家确实为 Geyser 连接：`/qd ui` 对 Java 玩家只发送可点击消息，不会弹表单。
6. 若表单仍不显示，检查是否有其他插件同时向该玩家发送表单（Geyser 同时只维护一个打开中的表单）。

---

## 七、结论

本次“基岩版 UI 实测失败”包含两个独立问题：

1. **环境层**：Geyser 2.12 与 Paper 26.1.2 的 Java 版版本不匹配，基岩客户端在登录阶段被拒绝，UI 链路未被执行；
2. **类加载层**：1.1.1 现场出现 `LinkageError`，ChSignin 与 Geyser 的类加载器持有不同的 Cumulus `Form` Class 对象。即使 Jar 内没有 Cumulus class，只要生产代码签名直接引用 Cumulus 类型，跨加载器调用 `sendForm` 就会触发约束校验失败。

1.1.2 通过以下措施完成针对性修复与可追溯化：

1. 启动期主动检测并告警版本不匹配，避免再次把环境问题误判为 UI 缺陷；
2. 表单推送结果可观测、失败可重试、最终可降级；
3. 去除 emoji 按钮文案，降低真机字形缺失风险；
4. 入服推送延迟提升至 3 秒，适配慢加载设备；
5. 降级提示改为真实可执行的命令说明，不再误导玩家；
6. 生产代码移除全部 Cumulus 类型引用，表单改由 Geyser 连接对象的类加载器反射构建与发送，从根源规避 `LinkageError`；
7. Cumulus 依赖显式声明为 `provided`，并通过 `jar tf` 与 `javap` 双重验证产物无重复类、无 Cumulus 类型引用。
