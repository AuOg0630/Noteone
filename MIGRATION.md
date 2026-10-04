# 整理记录 · remember_money → Noteone

> 执行日期：2026-10-03
> 源目录：`D:\Workspace\remember_money`（**只读，未做任何修改**）
> 目标目录：`D:\Workspace\Noteone`

本文记录此次「开发目录 → 可发布产物」的整理过程，供日后回溯。

---

## 1. 整理原则

### 目录划分
按**功能职责**分层，不按文件类型：

| 层 | 目录 | 准入标准 |
|---|---|---|
| 构建层 | `gradle/`、根 `*.kts`、`gradlew*` | Gradle 编译必需 |
| 源码层 | `app/` | 应用本体：清单、Kotlin、资源、Room schema |
| 设计层 | `design/` | 视觉规范的唯一依据文档 |

### 命名规范
- 全小写 + 连字符，禁用中文目录名与下划线
- 语义化，避免 `M0/M1/M3` 这类含义为空的编号
- 保留 Android / Gradle 生态的强制名称（`app`、`gradlew`、`gradle-wrapper.jar`）

### 剔除标准（五类）
| 类 | 判据 | 本次命中 |
|---|---|---|
| A 构建产物 | 可重新生成、体积大 | `app/build/`(496M)、`build/` |
| B 缓存 / IDE | 工具链中间态 | `.gradle/`(20M)、`.kotlin/` |
| C 本地配置 | 机器相关路径 | `local.properties` |
| D **私密信息** | 凭据、明文口令、密钥库 | `keystore.properties`、`keystore/`、`分发/项目指令.*` |
| E **开发过程产物** | 多人协作分工、交付过程记录、AI 记忆 | `.workbuddy/`、`分发/`、`docs/`、`tools/` |

**D 类说明**：`keystore.properties` 内含 `storePassword` / `keyPassword` 明文；`分发/项目指令.txt` 含内部分工与口令说明。二者入库等于公开泄露。

**E 类说明**：`docs/M0–M5_交付说明.md` 是「交付人 A/B/C/D/E」的协作过程记录，`分发/00` 讲「模块分工与文件归属」，对发布产品无价值且暴露开发过程。用户明确指示 `docs/` 与 `tools/` 均为开发中文件，不迁移。

---

## 2. 整理前后对照

### 新增（4）

| 文件 | 说明 |
|---|---|
| `README.md` | 项目简介、功能、技术栈、目录结构、构建与安装、隐私、设计规范 |
| `LICENSE` | MIT License，© 2026 AuOg |
| `.gitignore` | 在原有基础上补 `keystore/`、`.hprof`、OS 文件，并注明 `app/schemas/` 必须入库 |

> `design/preview/*.png`（18 张界面截图，从 `docs/验收截图/` 复制而来）曾一度加入，
> 后按用户要求**已删除**，README 改为纯文字说明。详见 §7。

### 移动

| 原 | 新 |
|---|---|
| `build.gradle.kts` | `build.gradle.kts` |
| `settings.gradle.kts` | `settings.gradle.kts` |
| `gradle.properties` | `gradle.properties` |
| `gradlew` / `gradlew.bat` | 同名 |
| `gradle/`（含 wrapper） | `gradle/` |
| `app/build.gradle.kts` | `app/build.gradle.kts` |
| `app/proguard-rules.pro` | `app/proguard-rules.pro` |
| `app/src/` | `app/src/` |
| `app/schemas/` | `app/schemas/` |
| `minimalist-ui/SKILL.md` | `design/minimal-ui-protocol.md` |

### 重命名

**品牌统一**（`remembermoney` → `noteone`）

| 项 | 原 | 新 |
|---|---|---|
| 包名 | `com.zja.remembermoney` | `com.noteone.app` |
| 源码目录 | `app/src/{main,test}/java/com/zja/remembermoney/` | `.../java/com/noteone/app/` |
| Room schema 目录 | `app/schemas/com.zja.remembermoney.core.data.db.AppDatabase/` | `app/schemas/com.noteone.app.core.data.db.AppDatabase/` |
| Application 类 | `RememberMoneyApp.kt` | `NoteoneApp.kt` |
| 主题 | `Theme.RememberMoney(.Transparent)` | `Theme.Noteone(.Transparent)` |
| 数据库文件名 | `remember_money.db` | `noteone.db` |
| Gradle 项目名 | `remember_money` | `noteone` |
| 自定义 action | `com.zja.remembermoney.action.*` | `com.noteone.app.action.*` |

**版本号**

| 项 | 原 | 新 |
|---|---|---|
| `versionCode` | 4 | 5 |
| `versionName` | 1.3 | 1.6 |

原代码版本号停留在 1.3，而设计规范已演进出 v1.4 / v1.5 / v1.6 三轮功能变更（框选改一笔框选、图标更换、首页精简、引导页、预算条迁移）。本次统一到 **1.6**，与规范版本对齐。

### 删除（未写入目标目录）

| 路径 | 体积 | 类别 |
|---|---|---|
| `app/build/` | 496 MB | A |
| `.gradle/` | 20 MB | B |
| `.kotlin/` | — | B |
| `build/` | 168 KB | A |
| `keystore/` | 4 KB | **D** |
| `keystore.properties` | 140 B | **D** |
| `.workbuddy/memory/` | — | E |
| `分发/`（8 文件） | 104 KB | **D + E** |
| `docs/`（7 md + 18 png） | 2.5 MB | E |
| `tools/`（2 py） | 12 KB | E |

---

## 3. 需要人工补充的信息

| # | 项 | 状态 |
|---|---|---|
| 1 | 仓库地址 | 已填 **https://github.com/AuOg0630/Noteone** |
| 2 | 许可证类型 | 已按 **GPL-3.0-or-later** 写入 `LICENSE` 与全部源文件 SPDX 头 |
| 3 | 签名密钥库 | 目标产物**不含**任何 `.jks`。原 `keystore/remember-money.jks` 保留在源目录，需自行备份；注意其 alias 为 `remembermoney`，应用内已改名为 `noteone`，正式发布前建议重新生成密钥库 |
| 4 | 包名 | 已用 `com.noteone.app`。若日后要上架，需确认域名反写是否可用 |
| 5 | 应用显示名 | 仍为「记一笔」，未改。如需改为「Noteone」请改 `res/values/strings.xml` 的 `common_app_name` |
| 6 | `design/minimal-ui-protocol.md` | 内容原样保留，仅改名。内部仍可能引用旧路径 |

---

## 4. 已知影响

- **包名变更 = 全新应用**。旧包名 `com.zja.remembermoney` 安装的数据不会自动迁移，两者可共存于同一设备。数据库文件名同时改为 `noteone.db`，新安装从零开始。
- **Room schema 未变**。表结构、`identityHash`（`a39842a8...`）与索引均未改动，仅目录名随包名变化。
- **签名配置已解耦**。`app/build.gradle.kts` 现在同时支持 `keystore.properties` 与环境变量（`NOTEONE_STORE_FILE` 等）两种凭据来源，便于 CI 构建；凭据缺失时 `assembleRelease` 仍能成功，但产出未签名 APK。
- **`app/src/test/` 已随包名迁移**，9 个测试类的 `package` 声明同步更新。

---

## 5. 目标产物规模

| 项 | 值 |
|---|---|
| 文件总数（不含构建产物） | 188 |
| 源码 | 106 个 Kotlin 文件 / 18,884 行 |
| 体积（不含构建产物） | 3.7 MB |

---

## 6. 验收结果

构建与质量检查全部通过，与源目录基线一致：

| 项 | 结果 |
|---|---|
| `clean assembleDebug` | ✅ BUILD SUCCESSFUL |
| `assembleRelease` | ✅ BUILD SUCCESSFUL（产出 `app-release-unsigned.apk`，符合预期） |
| 单元测试 | ✅ **9 个测试类 / 131 条用例，0 失败 0 错误 0 跳过** |
| `lintDebug` | ✅ `No issues found.` |
| APK 元数据 | ✅ `package=com.noteone.app` `versionCode=5` `versionName=1.6` `minSdk=30` `targetSdk=35` |
| **权限红线复验** | ✅ APK 内仅 `SYSTEM_ALERT_WINDOW`，**无 `INTERNET` / `ACCESS_NETWORK_STATE` / `POST_NOTIFICATIONS`** |
| 敏感信息扫描 | ✅ 无密钥库文件、无明文口令、无本机绝对路径 |

验收后已删除 `app/build/`、`build/`、`.gradle/`、`.kotlin/`、`local.properties`，目标目录为纯源码形态。

---

## 7. 后续调整（2026-10-03 晚）

### 精简构建产物

删除了 `app/build/`（248 MB / 3,433 文件）、`build/`（128 KB）、`.gradle/`（3.6 MB）。
磁盘占用 **258 MB → 3.7 MB**，但**入库文件数不变（188）**——这些产物本就被 `.gitignore` 排除。

安全性经三步验证：

1. 清理前后各跑一次 `git add -A` 导出待提交清单并 `diff` → 完全一致，188 个文件
2. 清理后 `./gradlew assembleRelease` 成功，APK 正常生成且 `apksigner verify` 退出码 0
3. 复验产出再次删除，目录回到纯源码态

> 注意：同一份代码每次重编产生的 APK，**SHA-256 都不同**（构建时间戳与签名随机化）。
> 不要用它判断"是否同一个包"，应认版本号或签名证书指纹。

### 删除界面截图，重写 README

按用户要求删除 `design/preview/`（18 张 PNG，2.3 MB），README 改为纯文字说明。

重写后的 README 调整了重心：**从「功能清单」改为「特点与优点」**，并把
**磁贴快捷记账**提到首位单独成章——包含完整的四步使用流程、设计取舍的说明
（为什么用一笔框选、为什么框选发生在截图之前、磁贴如何回话）、以及两条无需权限的退路。
隐私一节改为「承诺 / 验证方式」对照表，每条都可复验。

### 当前状态

| 项 | 值 |
|---|---|
| 文件总数 | 173 |
| 入库文件数 | 172 |
| 体积 | 1.4 MB |

### 许可证改为 GPL-3.0

原为 MIT，按用户要求改为**传染性开源许可** —— 目标是让任何衍生的软件也保持开源。

- `LICENSE` 替换为 GNU GPL v3.0 官方全文（35,149 字节 / 674 行），已与 `gnu.org/licenses/gpl-3.0.txt` 逐字节比对一致
- 106 个 Kotlin 源文件（`app/src/main` 97 个 + `app/src/test` 9 个）全部添加 SPDX 许可证头：

  ```kotlin
  // SPDX-FileCopyrightText: 2026 AuOg
  // SPDX-License-Identifier: GPL-3.0-or-later
  ```

- README 的许可证章节改为「你可以做的 / 你必须做的」对照表，并说明与宽松许可的区别

改动后复验：`assembleRelease` + 131 条单测 + `lintDebug` 全绿，APK 正常产出 74,860,097 B。

---

## 8. Git 仓库初始化（2026-10-03 23:03）

- `git init -b main`，默认分支 **main**
- **提交身份仅配置在本仓库**（不动全局，因为本机原本未设置任何 git 身份）：
  `user.name = AuOg` / `user.email = AuOg@users.noreply.github.com`
  （noreply 地址不会泄露真实邮箱，可直接推送到 GitHub）
- 新增 `.gitattributes`：仓库内统一存 LF，避免跨平台产生整文件 diff。
  其中 **`gradlew` 强制 `eol=lf`** —— 它是 POSIX shell 脚本，若被转成 CRLF，
  在 Linux / macOS / CI 上会报 `bad interpreter: /bin/sh^M` 而无法执行。
- 首次提交：`5d98cbc`，171 个文件 / 23,112 行

### 泄漏检查

以下三项确实存在于工作目录，但**均被 `.gitignore` 正确排除**，未进入提交：

| 文件 | 内容 |
|---|---|
| `keystore.properties` | 签名口令（明文） |
| `keystore/noteone.jks` | 签名密钥库 |
| `local.properties` | 本机 SDK 绝对路径 |

首次提交前已逐项确认：暂存区里 `keystore.properties` / `*.jks` / `local.properties` / `app/build/` 命中数均为 **0**。

### 推送

```bash
git remote add origin https://github.com/AuOg0630/Noteone.git
git push -u origin main
```
