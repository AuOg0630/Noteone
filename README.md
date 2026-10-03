# Noteone · 记一笔

> **下拉通知栏，点一下磁贴，框一下金额，就记完了。**
> 不用开 App，不用切页面，全程没有系统弹窗。

一个极简风格的 Android 记账应用。它要解决的问题很具体：**记账这件事死在了"麻烦"上**。
绝大多数记账 App 的操作路径是「解锁 → 找到 App → 点开 → 点加号 → 输入 → 选分类 → 保存」，
六步起步，于是没人坚持得下来。

Noteone 把这条路砍到三步以内。并且首页本身就是记账——金额、分类、备注同屏完成，**不跳二级页面**。

---

## 核心优势：磁贴快捷记账

这是本应用最值得说的一件事，也是它和「又一个记账 App」的区别所在。

### 使用流程

```
下拉通知栏 → 点「记一笔」磁贴 → 按住拖一下框住金额 → 松手 → 改金额、补用途 → 完成
```

| 步骤 | 说明 |
|---|---|
| 1. 下拉通知栏 | 任何界面都能下拉，不需要退出当前 App、不需要回桌面 |
| 2. 点磁贴 | 磁贴位于快捷设置面板，可拖到第一排常驻 |
| 3. **一笔框选** | **手指按下处 = 一个顶点，抬起处 = 对角顶点**，两点撑出选区，松手立即识别 |
| 4. 确认入账 | 悬浮面板里改金额、选分类、补备注，点「完成」写入 |

### 为什么这样设计

**「一笔框选」是有意为之。** 常见做法是先弹一个固定大小的框、再让用户拖到目标位置——要在两个维度上对准，很别扭。Noteone 改成按住直接划出对角线，手感与系统级识屏一致。

**框选发生在截图之前。** 所以遮罩底下就是真实屏幕画面，选区天然透出，不做任何图像处理。用户看到的就是即将被识别的内容，所见即所得。

**磁贴会回话。** 记账成功后磁贴显示「已记 ¥14.00」，1.5 秒后自动复位；识别中显示「识别中…」。不用切回 App 就知道记成没有。

**还有两条不依赖权限的退路。**
- 桌面长按图标 → 「记一笔」/「从图片记一笔」两个快捷方式
- 在相册里对任意截图「分享」到本应用

### 识别能力

端侧 OCR，**离线可用**。ML Kit Text Recognition v2 中文 bundled 模型打包在 APK 内，
不联网、不上传，飞行模式下照常识别。

识别失败不会卡住流程——面板立即降级为手动输入，并说明原因（未识别到金额 / 截图失败 / 页面设了安全保护）。

---

## 其他特点

### 首页只做记账

首页在结构上只有「账本头部 + 记账卡片」两件东西，**没有滚动区**。卡片撑满屏幕，键盘贴在底栏正上方。

月度数字只保留右上角一个「本月结余」——支出、收入、趋势、分类排行全部归「汇总」页，最近记录归「账单」页。
首页不重复别处的信息，点进来就能立刻开始输入。

### 输入手感

- 金额数字**逐位滚动**，不是简单的文本刷新
- 方向切换（支出 / 收入）在输入区内完成，不用跳设置
- 记账成功后金额与备注一起清空，连着记第二笔不用手动擦

### 数据可带走

- **CSV 导出**：按当前筛选条件导出，或全量导出，UTF-8 with BOM（Excel 直接打开不乱码）
- **JSON 备份与恢复**：完整快照，恢复前二次确认，清空数据需输入确认词

### 多账本，软删除

支持多账本切换。所有删除都是**软删除且可撤销**——分类删除是归档而非物理删除，历史记录仍然显示原来的分类名。

### 隐私

这部分不是营销词，是可以在源码和 APK 里逐条验证的。

| 承诺 | 验证方式 |
|---|---|
| **不申请网络权限** | `aapt2 dump permissions` 读 APK，只有 `SYSTEM_ALERT_WINDOW` |
| 截图不落盘 | 截图仅在内存，OCR 完成后立即销毁；不写磁盘、不写 `cacheDir`、不进日志 |
| 无障碍服务不越权 | `canRetrieveWindowContent` 未开启；`packageNames` 限定为应用自身；不订阅全局事件、不模拟手势 |
| 不做系统备份 | `android:allowBackup="false"`，且显式声明不上云、不参与设备迁移 |

清单里用 `tools:node="remove"` 显式剔除了三条权限：ML Kit 传递依赖注入的 `INTERNET` 与 `ACCESS_NETWORK_STATE`，
以及从未使用的 `POST_NOTIFICATIONS`。

屏幕采集走**无障碍服务 `takeScreenshot()`**，因此**不需要前台服务、不常驻通知、每次都要授权录屏**——这正是不用 MediaProjection 的原因。

### 关于耗电：开了无障碍也几乎不耗电

「开了无障碍服务会不会很费电」是这项功能最常被问到的问题。答案是**基本不会**，而且不是口头保证，是配置层面压到最低的结果。

无障碍服务确实必须保持开启（这是系统的要求，不是本应用的选择），但**保持开启 ≠ 持续工作**。本应用的无障碍服务只在「你点了磁贴之后的那几秒」真正干活，其余时间处于完全的睡眠状态：

| 省电措施 | 具体做法 | 效果 |
|---|---|---|
| **不消费任何事件** | `onAccessibilityEvent()` 是**空实现**——不读节点、不模拟手势，只维护一个服务存活标记 | 服务常开但不产生任何计算 |
| **只订阅一种事件** | `accessibilityEventTypes` 仅 `typeWindowStateChanged` | 不订阅文本变化、滚动等高频率事件 |
| **事件投递限定为本应用** | `packageNames="com.noteone.app"` | 别的 App 切换界面**根本不会唤醒本进程**——这是最关键的省电项 |
| **拉长事件合并窗口** | `notificationTimeout="1000"` | 同类型事件 1 秒内合并投递一次，减少昂贵的跨进程调用 |
| **无后台轮询** | 全项目无 `AlarmManager` / `WorkManager` / 定时器 / `WakeLock` | 不会定时把自己叫醒 |
| **无前台服务** | 不声明 `FOREGROUND_SERVICE`，不驻留通知栏 | 不占用常驻通知、不持续持有资源 |

需要注意的两点：

- `packageNames` 限定**不影响截图功能**。`takeScreenshot()` 是显示级能力，与事件订阅无关——这正是它可以被限定成仅本应用的原因。
- 你会注意到这里**没有「省电」而是「不需要电」**：服务在空闲时既不接收事件、也不做任何处理，其开销约等于一个未激活的静态对象。

> 唯一真正消耗资源的是每次识别时的 OCR 计算（端侧、约几百毫秒），且只在你自己点磁贴时发生。

反过来要提醒的是：部分国产 ROM 的**电池优化**会主动冻结后台进程，导致磁贴识别突然失效。这和耗电无关，但会表现为「功能坏了」。建议在「设置 → 应用 → 自启动 / 后台运行」中加白名单并关闭对本应用的电池优化（见下方「权限」一节）。

---

## 功能一览

| 模块 | 内容 |
|---|---|
| 记账 | 首页直接完成金额 / 分类 / 备注；数字逐位滚动；方向切换；记账后 Snackbar 撤销 |
| 账单 | 按日分组、时间范围与分类筛选、编辑、软删除与恢复 |
| 汇总 | 自定义时间范围 + 分类筛选；支出 / 收入 / 结余三卡、日均支出、分类排行、趋势图、本月预算进度 |
| **快捷记录** | **磁贴 → 框选 → OCR → 悬浮面板入账**；另支持桌面快捷方式、相册分享导入 |
| 设置 | 账本管理、分类管理、快捷按钮管理（长按拖拽排序）、CSV 导出、JSON 备份恢复、清空数据 |
| 引导 | 首次启动整屏列出「悬浮窗 + 无障碍 + 后台保活」三项，逐项可跳转授权，确认后不再出现 |

---

## 技术栈

| 项 | 取值 |
|---|---|
| 语言 / UI | Kotlin 2.4.20 + Jetpack Compose（BOM 2026.09.00） |
| 构建 | AGP 9.4.1 · Gradle 9.6.1 · KSP 2.3.12 |
| SDK | minSdk 30 · targetSdk 35 · compileSdk 37 |
| 存储 | Room 2.8.5（SQLite）+ DataStore Preferences |
| 依赖注入 | Hilt 2.60.1 |
| 屏幕采集 | 无障碍服务 `takeScreenshot()`（不使用 MediaProjection） |
| OCR | ML Kit Text Recognition v2（中文，端侧 bundled 模型） |
| 图标 | Phosphor Icons v2.1.1，转为 VectorDrawable 内置，统一 1.5dp 描边 |

金额一律以「**分**」为单位存 `Long`，不使用浮点数——避免任何浮点误差累积。

质量基线：131 条单元测试全绿，`lintDebug` 无告警。

---

## 设计约束

视觉规范的唯一依据是 [`design/minimal-ui-protocol.md`](design/minimal-ui-protocol.md)。几条硬约束：

- 暖色单色系，颜色稀缺；禁止渐变，禁止彩色大背景
- 字重只有 400 与 500；全站零阴影（唯一例外是 OCR 悬浮面板）
- 分割线统一 1dp `#EAEAEA`；圆角只用 12 / 16 / 6 / 4 / 9999 dp 五档
- 间距只用 4 / 8 / 12 / 16 / 24 / 32 dp；可点击区域不小于 48dp
- 禁止 emoji，禁止 loading 转圈；图标统一 1.5dp 描边
- 动效仅作用于 transform 与 opacity，时长 150–400ms
- 仅浅色模式

---

## 目录结构

```
Noteone/
├── app/
│   ├── build.gradle.kts          应用模块构建脚本（含签名配置）
│   ├── proguard-rules.pro        R8 规则（当前未开启混淆）
│   ├── schemas/                  Room schema JSON，数据库迁移的验收基准
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/noteone/app/
│       │   │   ├── MainActivity.kt / QuickEntryActivity.kt / NoteoneApp.kt
│       │   │   ├── core/
│       │   │   │   ├── common/   金额、日期等纯工具
│       │   │   │   ├── data/     Room 实体 / DAO / Repository / DI
│       │   │   │   ├── design/   设计系统：颜色、字体、间距、公共组件
│       │   │   │   └── domain/   金额输入规则、时间范围、软删除清理
│       │   │   ├── feature/      按页面划分：record / ledger / report / settings
│       │   │   │                 / book / category / manage / onboarding
│       │   │   ├── export/       CSV 序列化与导出服务
│       │   │   ├── navigation/   NavHost 与路由
│       │   │   └── quickentry/   磁贴 / 无障碍截图 / 框选悬浮层 / OCR / 结果面板
│       │   └── res/              字符串（按模块分文件）、drawable、主题
│       └── test/                 JUnit 单元测试
├── design/
│   └── minimal-ui-protocol.md    视觉规范的唯一依据
├── gradle/
│   ├── libs.versions.toml        所有依赖版本的唯一来源
│   └── wrapper/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── LICENSE
```

### 代码分层

`feature/` 下每个页面自成目录，页面之间不直接互相引用，跨页面跳转统一由 `navigation/AppNavHost.kt` 注入回调。

`feature/` 与 `navigation/` 一律通过 `Repository` 接口访问数据，只有 `core/data/repository/` 下的实现允许注入 DAO。

统计口径全部集中在 `feature/report/ReportAggregator` 的纯函数里，不读系统时间——因此可以完整单测。

---

## 构建

### 环境要求

- JDK 17
- Android SDK，需安装 **API 37 platform** 与 **API 35 build-tools**
- 国内网络下如需从镜像拉依赖，可在 `settings.gradle.kts` 中启用已注释的阿里云源

### 步骤

```bash
git clone <仓库地址>
cd Noteone

# 指定本机 SDK 路径（该文件已被 .gitignore 排除，不要提交）
echo "sdk.dir=/path/to/Android/SDK" > local.properties

./gradlew assembleDebug      # Debug 包
./gradlew assembleRelease    # Release 包
```

产物位于 `app/build/outputs/apk/`。

### 签名

Release 签名凭据有两个来源，按优先级：

1. 项目根目录的 `keystore.properties`
2. 环境变量 `NOTEONE_STORE_FILE` / `NOTEONE_STORE_PASSWORD` / `NOTEONE_KEY_ALIAS` / `NOTEONE_KEY_PASSWORD`

```properties
storeFile=/absolute/path/to/noteone.jks
storePassword=...
keyAlias=noteone
keyPassword=...
```

两者都缺失时 `assembleRelease` 依然能构建成功，但产出的是 `app-release-unsigned.apk`，**装不上设备**。

> **本仓库不包含任何密钥库。** 请自行生成：
>
> ```bash
> keytool -genkeypair -v -keystore noteone.jks -keyalg RSA -keysize 2048 \
>         -validity 10000 -alias noteone
> ```
>
> **务必离线备份**——密钥库丢了，就再也签不出能覆盖安装的同名包。

### 测试

```bash
./gradlew testDebugUnitTest   # 9 个测试类 / 131 条用例
./gradlew lintDebug
```

---

## 安装

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

也可将 APK 拷贝到手机点击安装（需允许「安装未知来源应用」）。

### 启用磁贴

1. 下拉通知栏，展开快捷设置面板
2. 点「编辑」或铅笔图标，把 **记一笔** 从可用磁贴拖到面板中
3. 建议拖到第一排，便于单手下拉后直接点

### 权限

首次启动会进入引导页。**磁贴快捷记账**需要两项授权：

| 权限 | 用途 |
|---|---|
| **悬浮窗**（`SYSTEM_ALERT_WINDOW`） | 框选遮罩与记账面板都以悬浮窗显示 |
| **无障碍服务** | 调用 `takeScreenshot()` 读取屏幕画面 |

其余功能（记账、账单、汇总、设置、从图片记一笔）**无需任何授权**即可使用。

> **关于无障碍权限的顾虑**：本应用的无障碍服务**不读取任何窗口内容、不模拟点击、不监听你的操作**，
> 空闲时不消耗资源（详见上文「关于耗电」）。它只在你点了磁贴之后调用一次截图接口。
> 如果仍有顾虑，可以完全不授权——用桌面快捷方式或「从图片记一笔」同样能完成识别记账。

> 部分国产 ROM 会在后台杀掉无障碍服务，导致识别突然失效。设置页会显示服务状态并提供「去开启」；
> 建议同时在「设置 → 应用 → 自启动 / 后台运行」里加白名单，并关闭电池优化。

---

## 版本

当前 `versionName = 1.6` / `versionCode = 5`。

依赖版本统一在 `gradle/libs.versions.toml` 中管理——改版本改这一个文件。

## 许可证

[GNU General Public License v3.0](LICENSE) © 2026 AuOg

这是一个 **copyleft（著佐权）** 许可证，与宽松许可（MIT / Apache）的关键区别：

| | 你可以做的 | 你必须做的 |
|---|---|---|
| 使用 | 自由使用、修改、商用、再分发 | — |
| **分发衍生作品** | 可以闭源分发吗？**不可以** | 衍生作品**整体**必须以 GPL-3.0 开源，并附上完整源码 |
| 保留声明 | — | 必须保留版权声明与许可证文本，并标明修改过 |
| 专利 | 获得贡献者的专利授权 | 不得对下游追加专利限制 |

### 简单说

- 你**可以**基于本项目做一个新软件，甚至拿去卖钱
- 但只要你**对外分发**（发布、上架、装机、给他人使用），那个新软件就**必须同样以 GPL-3.0 开源**
- 如果只是**自己内部使用、不对外发布**，则不受分发条款约束，无需开源

### 为什么选它

本项目希望任何由它衍生的软件都保持开源。宽松许可证（如 MIT）允许他人把代码拿去做成闭源产品，
而 GPL-3.0 正是为了阻止这种情况而设计 —— 这是刻意的选择，不是默认选项。

> 注：GPL 只约束「分发」行为，不约束「使用」。且**它不阻止商用**，只要求商用产品同样开源。
> 若还希望「通过网络提供服务」也触发开源义务（如 SaaS），应改用 AGPL-3.0。
