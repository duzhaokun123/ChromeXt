# ChromeXt 用户脚本 API 兼容性与标准差异

> **文档性质**：本文是对 ChromeXt 当前源码的**静态差异分析**，不是运行时测试报告，也不是安全审计。
>
> **AI 生成警告**：本文由 AI 模型 `deepseek-v4-flash` 基于仓库源码、官方文档抓取结果和少量 Node.js 片段复现生成。AI 可能误读代码、遗漏分支、错误推断浏览器/Android 运行时行为，或把“文档描述”误当成“实际实现”。文中所有结论都应回到对应 commit 的源码和官方文档复核；若本文与源码冲突，以源码为准。
>
> **可能过时的信息警告**：
> - 本文绑定到 commit `e2b6b62c30b6f6bfca70abce3113080832ae968c`（`v3.8.3-45-ge2b6b62`）。之后的重构、依赖升级和 API 改动不会自动反映到本文。
> - `GM_info.version` 在 `GM.js` 中是硬编码的 `3.8.3`，与 `app/build.gradle.kts` 的 `versionName = "3.8.3"` + `versionNameSuffix = "-o0kam1"` 并不完全一致。
> - Tampermonkey 文档是持续更新的；本文抓取时最新稳定版为 **5.5.1（2026-10-01）**，但文档中已经出现 `v5.6.6239+` 等更高版本的条目。
> - ChromeXt 只承诺兼容“最新稳定 Chrome”，且大量能力依赖 DevTools/CDP、WebView 实现和 Android 版本；不同设备上的实际行为可能不同。
> - 原 `README.bak.md` 中“almost all Tampermonkey APIs”是项目自述，不应直接当作兼容性结论。

---

## 1. 对比标准与判定口径

### 1.1 采用的标准

用户脚本 API 没有单一的 ISO/IEC 标准，实际是 Greasemonkey/Tampermonkey/Violentmonkey 等管理器形成的**事实标准**。本文按以下优先级对比：

| 优先级 | 标准 | 版本 / 快照 | 用途 | 来源 |
|---|---|---|---|---|
| 1（主标准） | Tampermonkey 官方文档 | 最新稳定版 5.5.1（2026-10-01）；文档抓取于 2026-10-17 | ChromeXt 自述主要兼容 Tampermonkey；GM_* 签名、@grant、@connect、@run-at 等以 TM 为准 | [TM 文档][TM]、[TM 变更日志][TM-changelog] |
| 2 | Violentmonkey API / Metadata Block | 抓取于 2026-10-17；页面未标注版本 | 对照 `GM.*` 异步别名、`@inject-into`、`GM_getResourceURL` 的 blob/data URL 语义 | [VM GM API][VM-gm]、[VM 元数据][VM-meta] |
| 3 | Greasemonkey Manual: API | 页面最后编辑 2026-08-09 | 对照 `GM.*` API 的来源和 `GM.getValue` 等异步语义 | [GM API][GM-api] |
| 4 | Chrome Match Patterns | 页面标注最后更新 2012-09-18（仍是 Chrome 官方规范） | 对照 `@match` 的 scheme/host/port/path 语义 | [Chrome Match Patterns][Chrome-match] |
| 辅助 | Chrome DevTools Protocol Network 域 | 抓取于 2026-10-17 | 解释 `GM_cookie` 内部使用 `Network.getCookies` / `setCookies` / `deleteCookies` 时的行为边界 | [CDP Network][CDP-network] |

### 1.2 判定规则

- **比较对象**：ChromeXt 暴露给用户脚本的 `GM_*`、`GM.*`、`unsafeWindow`、`GM_info`，以及元数据指令和 `@grant` 的解析/执行语义。
- **比较维度**：函数签名、参数默认值、返回值、回调参数、错误行为、执行时机、存储/跨标签页语义、权限范围、默认注入上下文。
- **标准冲突时**：以 Tampermonkey 为主；当 Tampermonkey 没有对应概念（例如 `@inject-into`）时，改用 Violentmonkey；当涉及 URL 匹配细节时，以 Chrome Match Patterns 为准。
- **非标准扩展**：`GM.ChromeXt`、`GM_cookie.store/export`、`GM_info.storage/uuid/valueListener`、`@grant frames` 等属于 ChromeXt 自有扩展，不计入“与标准不一致”，但在第 10 节单列，避免被误认为标准 API。
- **严重度**：
  - **高**：常见脚本会直接失败、数据丢失、回调签名错误导致异常，或权限/数据范围明显偏离标准。
  - **中**：特定参数、选项或边界场景不兼容，通常需要改写脚本才能正常工作。
  - **低**：日志格式、错误消息、默认值等非关键差异。
  - **信息**：仅记录实现方式差异，不判定为缺陷。

### 1.3 证据等级

本文的“依据”主要是源码行号（`文件:行号`）。涉及浏览器/CDP/Android 实际行为的部分属于**推断**，会明确写“需运行复核”。少量元数据解析结果用 Node.js 复现，见第 2.3 节。

---

## 2. 检查时信息

### 2.1 代码基线

| 项目 | 值 |
|---|---|
| 仓库 | `ChromeXt`（仓库根目录） |
| 分支 | `master` |
| HEAD | `e2b6b62c30b6f6bfca70abce3113080832ae968c` |
| `git describe` | `v3.8.3-45-ge2b6b62` |
| 最近提交时间 | 2026-10-14 16:58:35 +0800 |
| 最近提交 | `Update dependency org.lsposed.hiddenapibypass:hiddenapibypass to v6.2` |
| `versionName` | `3.8.3`（`versionNameSuffix = "-o0kam1"`） |
| `compileSdk` / `targetSdk` / `minSdk` | 37 / 37 / 23 |
| 检查时间 | 2026-10-17 18:00 +08:00 |
| 检查方式 | 源码静态阅读 + Node.js 片段复现 + 官方文档抓取；**未在 Android 设备/浏览器中运行** |

### 2.2 主要检查文件

| 文件 | 行数 | 作用 |
|---|---:|---|
| `app/src/main/assets/GM.js` | 1334 | GM_* API、GM_info、GM.bootstrap、元数据再解析、GM_xmlhttpRequest |
| `app/src/main/java/org/matrix/chromext/script/Local.kt` | 171 | `@grant` 到 GM.js 代码块的拼接、GM.* 别名、GM_info 构造 |
| `app/src/main/java/org/matrix/chromext/script/Parser.kt` | 107 | 安装/加载时的元数据解析、`@require` 下载、storage 初始化条件 |
| `app/src/main/java/org/matrix/chromext/script/Manager.kt` | 217 | 脚本注入、frame 注入、storage 回写 |
| `app/src/main/java/org/matrix/chromext/script/SQLite.kt` | 65 | 脚本与 storage 的 SQLite 结构 |
| `app/src/main/java/org/matrix/chromext/Listener.kt` | 520 | `GM_notification`、`GM_cookie`、`GM_xmlhttpRequest`、storage、菜单命令等原生侧处理 |
| `app/src/main/java/org/matrix/chromext/utils/XMLHttpRequest.kt` | 160 | `GM_xmlhttpRequest` 的 Kotlin HttpURLConnection 实现 |
| `app/src/main/java/org/matrix/chromext/utils/Url.kt` | 131 | `@match` / `@include` / `@exclude` 的自定义匹配器 |
| `app/src/main/java/org/matrix/chromext/utils/UserScriptMenuCommand.kt` | 33 | 菜单命令 ID 与页面菜单桥接 |
| `app/src/main/java/org/matrix/chromext/hook/PageMenu.kt` | 372 | 用户脚本菜单在 Chrome 页面菜单中的呈现 |
| `app/src/main/java/org/matrix/chromext/hook/UserScript.kt` | 91 | 注入触发、console 转发、storage 刷盘 |
| `app/src/main/java/org/matrix/chromext/Chrome.kt` | 356 | DevTools/CDP 求值、frame 注入、广播、cookie store |
| `app/src/main/assets/scripts/initChromeXt.js` | 330 | ChromeXt 与脚本之间的 dispatch/安全锁 |

### 2.3 复现与验证记录

1. **元数据归一化**：用 Node.js 复现 `GM.js:1043-1075` 的正则与循环，输入包含 `@include/@match/@exclude/@require/@resource/@grant/@connect/@run-at` 的元数据块，得到：
   - `GM_info.script.excludes` 为 `undefined`；
   - 原始 `exclude` 字段仍保留；
   - `@grant` 被归一化为 `grants`，`grant` 被删除；
   - `@resource` 先得到原始字符串数组，随后被映射为 `{name, url}`。
   这与第 4.2 节的结论一致，复现脚本未提交到仓库。
2. **URL 匹配**：用 Node.js 复现 `Url.kt:17-66` 的字符串替换与正则逻辑，确认：
   - `*://*/*` 会匹配 `ftp://example.com/a`；
   - `http://example.com/*` 不匹配 `http://example.com:8080/a`；
   - `@include` 的无 `*` 形式按子串匹配，`@exclude` 的无 `*` 形式按完整相等匹配。
   见第 9.1 节。
3. **文档抓取**：2026-10-17 09:54–10:00（页面页脚时间，未标注时区）抓取 Tampermonkey、Violentmonkey、Greasemonkey、Chrome 官方文档；具体 URL 与快照时间见第 14 节。

---

## 3. 总览

### 3.1 已实现的接口

ChromeXt 当前实现了以下用户脚本接口（共 19 个 `GM_*` 家族 + 2 个全局对象）：

- 全局/工具：`unsafeWindow`、`GM_info`、`GM_log`、`GM_addStyle`、`GM_addElement`、`GM_setClipboard`
- 存储：`GM_setValue`、`GM_getValue`、`GM_deleteValue`、`GM_listValues`、`GM_addValueChangeListener`、`GM_removeValueChangeListener`
- 资源/下载：`GM_getResourceText`、`GM_getResourceURL`、`GM_download`
- 网络/Cookie：`GM_xmlhttpRequest`、`GM_cookie.list/set/delete`
- 标签页/菜单/通知：`GM_openInTab`、`GM_registerMenuCommand`、`GM_unregisterMenuCommand`、`GM_notification`
- 非标准扩展：`GM.ChromeXt`（需 `@grant GM.ChromeXt`）

### 3.2 明确缺失的 Tampermonkey API

| 缺失 API | 标准用途 | ChromeXt 现状 |
|---|---|---|
| `GM_getTab(callback)` / `GM_saveTab(tab)` / `GM_getTabs(callback)` | 在多个标签页之间共享脚本级 tab 状态 | 未实现；`@grant GM_getTab` 会得到 console.error stub，`@grant GM.getTab` 甚至可能在启动时抛 `ReferenceError`/`TypeError`（见 9.3） |
| `GM_setValues(values)` / `GM_getValues(keysOrDefaults)` / `GM_deleteValues(keys)` | 批量读写/删除 storage（TM 5.3+） | 未实现；下划线形式会得到 console.error stub；点号形式（`GM.setValues` 等）可能在启动时抛 `ReferenceError`/`TypeError`（见 9.3） |
| `GM_webRequest(rules, listener)` | 声明式网络请求拦截 | 未实现 |
| `GM_audio.setMute/getState/addStateChangeListener/removeStateChangeListener` | 音频状态控制 | 未实现 |
| `window.onurlchange` | SPA URL 变化事件 | 未实现 |
| `window.focus` | 授权后聚焦标签页 | 未实现；`window.close` 已实现 |
| Subresource Integrity（`@require`/`@resource` 的 `#sha256=` 等） | 校验外部资源 | 未校验；`@require` 的 fragment 被忽略，`@resource` 的 fragment 被剥掉（见 6.3） |

### 3.3 最高风险的 10 个差异（先看这里）

1. **`GM_addValueChangeListener` 回调参数错误**：实际传入 `(oldValue, newValue, remote)`，标准是 `(key, oldValue, newValue, remote)`；本地删除值时监听器不会被调用，跨标签页删除还会把 key 留成 `undefined`（4.2、5.2）。
2. **`GM_info.script` 字段名错误**：`excludes` 因 `exclude` 拼成 `exlcude` 而缺失；`grant` 被归一化成 `grants`（TM/VM 读取的是 `grant`）（4.2）。
3. **storage 只在特定 grant 下加载**：仅使用 `GM_setValue`/`GM_listValues`/`GM_deleteValue`/`GM_addValueChangeListener`（含 `GM.*` 形式）的脚本不会从 DB 加载 storage，写入也不会持久化（5.1）。
4. **`GM_cookie.list` 的 `url` 参数很可能没有传给 CDP**：JS 传的是数组，Kotlin 用 `optJSONObject` 读取得到 `null`；按 CDP 协议，`Network.getCookies` 未指定 `urls` 时不会返回 cookie。若目标 Chrome 版本遵循该语义，`list()` 会为空；若版本行为不同，则可能返回更大范围（7.2）。
5. **同源 `GM_xmlhttpRequest` 也必须 `@connect self`**：否则请求被拒绝；标准管理器通常对同源请求不要求 `@connect`（7.1）。
6. **`GM_xmlhttpRequest` 的 `finalUrl`/重定向/超时语义与标准不一致**：`redirect: "error"` 基本不生效；fetch 路径的 timeout 变成 abort；`finalUrl` 通常不是最终 URL（7.1）。
7. **`GM.*` 未实现别名可能让整个脚本启动失败**：`@grant GM.getTab` 等会生成 `{sync: GM_getTab}`，而 `GM_getTab` 未定义，抛 `ReferenceError`（9.3）。
8. **默认注入上下文不是 Tampermonkey 的 page/raw**：默认是 ChromeXt 自己的 Proxy sandbox；用户代码对 `window` 的写入不会落到真实页面（9.5）。
9. **iframe 默认不注入**：只有主页面存在 `@grant frames` 的脚本时才注入 frame；标准默认注入 frame，除非 `@noframes`（9.4）。
10. **`GM_addValueChangeListener`/`GM_notification`/`GM_registerMenuCommand` 等回调/返回值签名与标准不同**，常见脚本需要适配（5.2、8.2、8.3）。

---

## 4. 全局对象与工具 API

### 4.1 `unsafeWindow`

| 项目 | ChromeXt | 标准（Tampermonkey/VM） | 差异 |
|---|---|---|---|
| 暴露条件 | 仅 `@grant unsafeWindow` 时，`GM.js:38-39` 定义 `const unsafeWindow = window`，并在 sandbox handler 中挂到 `handler.window.unsafeWindow`（`GM.js:1131-1132`） | TM 需要 `@grant unsafeWindow`；VM 默认提供 | 与 TM 基本一致；与 VM 的“无 grant 也可用”不同 |
| 指向 | 真实页面 `window`（外层 IIFE 的全局 `window`） | 页面原始 `window` | 一致 |
| sandbox 下访问 | `unsafeWindow` 可绕过 Proxy sandbox 直接访问页面对象 | 同样可绕过 sandbox 直接访问页面对象 | 一致 |

**结论**：按 Tampermonkey 口径基本一致；但 VM 用户脚本若依赖“无需 grant 的 unsafeWindow”，会在 ChromeXt 中拿到 `ReferenceError`（未定义），需要在元数据中补 `@grant unsafeWindow`。[TM grant][TM-grant]、[VM GM API][VM-gm]

### 4.2 `GM_info`

ChromeXt 的 `GM_info` 由 Kotlin 先构造最小对象，再由 `GM.bootstrap()` 在 JS 侧解析 `scriptMetaStr` 补齐（`Local.kt:79-81`、`GM.js:1043-1079`、`GM.js:1136-1143`、`GM.js:1301-1303`）。

**标准结构**（Tampermonkey `GM_info` 类型）包含 `downloadMode`、`isIncognito`、`sandboxMode`、`scriptHandler`、`scriptMetaStr`、`scriptUpdateURL`、`scriptWillUpdate`、`userAgentData`、`version`、`script.{antifeatures, author, blockers, connects, copyright, description, downloadURL, excludes, fileURL, grant, header, homepage, icon, icon64, includes, lastModified, matches, name, namespace, position, resources, supportURL, 'run-at', 'run-in', unwrap, updateURL, version, webRequest, options}` 等（[TM GM_info][TM-info]）。Violentmonkey 还提供 `uuid`、`platform`、`userAgent` 等（[VM GM API][VM-gm]）。

| 字段/行为 | ChromeXt 实现 | 与标准差异 | 级别 |
|---|---|---|---|
| `scriptHandler` | `"ChromeXt"`（`GM.js:1301`） | 值不同是预期；脚本若硬编码 `"Tampermonkey"` 会走不同分支 | 低 |
| `version` | 硬编码 `"3.8.3"`（`GM.js:1302`） | 与 `versionName` 的 `-o0kam1` 后缀不一致；标准中是管理器版本 | 低 |
| `scriptMetaStr` | 原始元数据块（`Local.kt:79`） | 一致 | 信息 |
| `script.id` | `namespace:name`，且 Kotlin 会把 name 中的 `:` 全部删除（`Parser.kt:42`、`Parser.kt:80`） | 非标准字段；标准用 namespace+name 作为身份，但允许 name 含冒号 | 中 |
| `script.grant` | **不存在**；解析为 `grants`（`GM.js:1055-1068`） | TM 的字段是 `grant`，VM 也是 `grant`；读取 `GM_info.script.grant.includes(...)` 的脚本会抛 `TypeError` | 高 |
| `script.excludes` | **不存在/undefined**：列表里写成了 `"exlcude"`（`GM.js:1058`），`@exclude` 仍留在 `exclude` 字段 | `@exclude` 解析成功但 `GM_info.script.excludes` 不可用 | 高 |
| `script.includes` / `script.matches` | 存在（`GM.js:1055-1068`） | 与 TM 字段名一致 | 一致 |
| `script.connects` | 存在（`GM.js:1055-1068`） | 与 TM 字段名一致；VM 用 `connect` | 低 |
| `script.requires` | 存在（`GM.js:1055-1068`） | TM 类型未列该字段；VM 用 `require` | 低 |
| `script.resources` | `{name, url}`，运行时再补 `content`（`GM.js:1069-1075`、`GM.js:1272-1280`） | 标准 `Resource` 还包含 `content`/`error`/`meta`；ChromeXt 的 content 依赖 GM_xmlhttpRequest 且受 `@connect` 限制 | 中 |
| `script.noframes` | 有 `noframes: true`（`GM.js:1049-1054`） | TM 放在 `script.options.noframes`；VM 放在 `script.noframes` | 低 |
| `script['run-at']` | 默认 `"document-idle"`（`GM.js:1077-1079`） | 与 TM 字段名一致；但与实际执行时机不一致（见 9.2） | 中 |
| `script.version` / `description` / `author` / `icon` 等 | 只把原始 `@key` 塞进 `script`，多个同名 key 会变成数组 | 未做 `name_i18n`/`description_i18n` 归一化；`@description` 多次出现时类型可能变成数组 | 中 |
| `script.options` | 不存在 | 读取 `options.noframes`/`options.run_at` 等会失败 | 中 |
| `script.lastModified` / `position` / `header` / `blockers` | 不存在 | 依赖这些字段的管理器 UI/脚本会失败 | 低 |
| `downloadMode` / `isIncognito` / `sandboxMode` / `scriptUpdateURL` / `scriptWillUpdate` / `userAgentData` / `container` | 不存在 | 标准字段缺失 | 中 |
| `GM_info.storage` | **非标准**：暴露整个 storage 对象（`GM.js:1271`） | 标准 `GM_info` 不暴露 storage | 信息 |
| `GM_info.uuid` | **非标准**：随机数（`GM.js:1136`） | VM 有 `uuid` 字符串；TM 无 | 信息 |
| `GM_info.valueListener` | **非标准**：监听器数组（`GM.js:1212`） | 标准不暴露 | 信息 |
| 冻结 | `Object.freeze(GM_info.script)`、`Object.freeze(GM_info)`（`GM.js:15`、`GM.js:1303`） | 标准通常不保证冻结；严格模式下脚本改写会抛错 | 低 |

**结论**：`GM_info` 只实现了标准字段的一个子集，并且 `grant`/`excludes` 两个高频字段有实质性错误。依赖 `GM_info` 做能力探测的脚本很容易失败。

### 4.3 `GM_log`

| 项目 | ChromeXt | 标准 | 差异 |
|---|---|---|---|
| 签名 | `GM_log = console.info.bind(console, scriptId + ":")`（`GM.js:41-44`） | `GM_log(message)`，输出到 console | 输出级别固定为 `info`，并强制加 `脚本 id 末段:` 前缀；多参数会被一起打印 |
| 持久化/管理器日志 | 仅浏览器 console；`UserScriptHook` 会把 console 消息转发到 logcat（`hook/UserScript.kt:46-68`） | TM 的 console 日志；管理器可能有日志面板 | ChromeXt 没有独立的脚本日志面板；`README.bak.md` 的 TODO“Save and present script errors and GM_log logs”仍未完成 |

**结论**：功能可用，但日志格式与 TM 不同；依赖日志级别或原始消息内容的脚本/工具会受影响。级别：低。

### 4.4 `GM_addStyle`

| 项目 | ChromeXt | 标准 | 差异 |
|---|---|---|---|
| 签名/返回 | `GM_addStyle(css)` 创建 `<style>`，返回该元素（`GM.js:23-35`） | TM 返回注入的 style 元素（[TM GM_addStyle][TM-addstyle]） | 一致 |
| 注入位置 | 优先 `document.head \|\| document.documentElement`；失败则 `setTimeout` 再试 `document.head`（`GM.js:27-33`） | 注入到文档 | document-start 时可能先挂在 `<html>` 上，不会自动迁移到 `<head>`；极端情况下 `<head>` 仍未出现时二次尝试可能失败 |
| CSP | 普通 DOM 插入 | 扩展上下文注入，通常可绕 CSP | ChromeXt 在严格 `style-src` 页面可能被 CSP 拦截；这一点需按页面实际 CSP 运行验证 |

**结论**：返回值和基本行为一致；CSP 绕过能力不等同于 Tampermonkey。级别：低（CSP 场景为中）。

### 4.5 `GM_addElement`

| 项目 | ChromeXt | 标准 | 差异 |
|---|---|---|---|
| 签名 | 支持 `(parent, tag, attrs)` 和 `(tag, attrs)`（`GM.js:338-362`） | 同（[TM GM_addElement][TM-addelement]） | 基本一致 |
| 省略 parent | 固定挂到 `document.head`（`GM.js:340-342`） | 按标签选择 `document.head`（script/link/style/meta）、`document.body` 或其他、`documentElement` 兜底（[VM GM_addElement][VM-gm]） | 与 TM 的“head or body otherwise”不同；例如 `GM_addElement('div', ...)` 在 ChromeXt 中会挂到 head |
| 错误返回 | 参数个数不是 2/3 时返回 `undefined`；`createElement` 抛错时不捕获（`GM.js:343-345`、`GM.js:346`） | TM：失败返回 `null`，错误可被捕获 | 返回值/异常行为不同 |
| `textContent` | 特殊处理为 DOM 属性（`GM.js:347-353`） | 作为 DOM 属性 `textContent` 设置，而不是 HTML 属性 | 一致 |
| CSP | 普通 DOM 插入（`GM.js:354-360`） | TM/VM 主要用扩展上下文注入，常用于绕过严格 CSP | 严格 `script-src`/`style-src` 页面可能无法注入；需按页面 CSP 运行验证 | 中 |
| ShadowRoot | `appendChild` 可用 | 支持 ShadowRoot | 基本一致 |

**结论**：成功路径基本兼容；默认父节点和错误返回值不兼容。级别：中。

### 4.6 `GM_setClipboard`

| 项目 | ChromeXt | 标准 | 差异 |
|---|---|---|---|
| 签名 | `GM_setClipboard(text, info = {type: "text"})`（`GM.js:316-324`） | `GM_setClipboard(data, info, cb)`；`info` 可为 `"text"`/`"html"` 或 `{type, mimetype}`（[TM GM_setClipboard][TM-clipboard]） | 第三个参数 `cb` 被忽略 |
| HTML | `info.type == "html"` 时设置 `htmlText`（`GM.js:322`） | 支持 `html` 类型 | `info` 为 `{mimetype: "text/html"}` 时，`info.type` 变成 `"text/html"`，不会走 HTML 分支，最终按纯文本处理 |
| 回调 | 无回调调用 | 可选 callback | 依赖 callback 的脚本会一直等不到回调（若它自己包了 Promise） |
| 原生侧 | `Listener.on("copy")` 用 `ClipData.newHtmlText`/`newPlainText`（`Listener.kt:137-151`） | 设置系统剪贴板 | 基本一致；`label` 被强制改为 `GM_setClipboard by <script id>` |

**结论**：纯文本场景可用；HTML mimetype 和 callback 不兼容。级别：中。

---

## 5. 存储与值变更

### 5.1 `GM_setValue` / `GM_getValue` / `GM_deleteValue` / `GM_listValues`

ChromeXt 的同步 storage 是一个 Proxy（`GM.js:1137-1189`、`GM.js:1269-1271`），写入通过 `ChromeXt.dispatch("scriptStorage", ...)` 送到 Kotlin，再由 `Listener` 更新内存中的 `Script.storage`（`Listener.kt:257-281`），最终在 Activity `onStop` 时批量写回 SQLite（`Manager.kt:174-188`、`hook/UserScript.kt:82-88`）。

| 项目 | ChromeXt | 标准 | 差异 | 级别 |
|---|---|---|---|---|
| 同步 `GM_setValue` | 立即更新本地 Proxy，异步 dispatch；无返回值（`GM.js:438-440`） | 同步写入脚本 storage；无返回值 | 基本一致 | 一致 |
| 同步 `GM_getValue` | 从 `GM_info.storage` 读取，缺省返回 `default_value`（`GM.js:448-450`） | 从 storage 读取 key；key 不存在时返回 `default_value` | 一致 | 一致 |
| `GM_deleteValue` | `delete GM_info.storage[key]`（`GM.js:443-445`） | 删除 key | 删除时不会通知 `GM_addValueChangeListener`（见 5.2） | 中 |
| `GM_listValues` | `Object.keys(GM_info.storage)`（`GM.js:453-455`） | 返回 key 数组 | 一致 | 一致 |
| 异步 `GM.getValue` | `storageHandler.async_get`：首次读本地值/默认值；后续读会向 Kotlin 请求 `scriptSyncValue`（`GM.js:1167-1188`、`GM.js:1191-1193`） | 从持久化 storage 读取最新值 | 第一次读可能读到页面加载时的快照；跨标签页更新依赖后续请求 | 中 |
| storage 初始化条件 | **只有** grant 含 `GM.getValue`/`GM_getValue`/`GM_cookie` 时，`Parser.kt:66-71` 才从 SQLite 读 storage | 任何 storage API grant 都应可读写同一 storage | 仅 grant `GM_setValue`/`GM_listValues`/`GM_deleteValue`/`GM_addValueChangeListener`（含对应的 `GM.*` 形式）时，`script.storage == null`，`Listener` 直接 `return`（`Listener.kt:261`），**写入不持久化** | 高 |
| 持久化时机 | 内存更新后，Activity `onStop` 批量写 DB（`Manager.kt:174-188`） | 通常立即持久化 | 进程被杀/崩溃时可能丢失最后一批写入 | 中 |
| 跨标签页 | 只有 grant 含 `GM_addValueChangeListener` 时 `broadcast` 为 true（`GM.js:1139`）；Kotlin 通过 `Chrome.broadcast` 推送（`Listener.kt:262-267`） | storage 变化按管理器策略同步到其他 tab | grant `GM.addValueChangeListener`（点号形式）时 `broadcast` 仍为 false；无监听器的脚本不广播是合理优化，但与标准“storage 全局一致”的语义不同 | 中 |
| `undefined`/复杂值 | 依赖 `JSON.stringify`；循环引用会抛错；`undefined` 在 JSON 中会被省略，Kotlin 侧可能当成删除 | TM 允许 `undefined`、嵌套对象等 | 边界值行为不同 | 低 |

**结论**：最常见的 `GM_getValue` + `GM_setValue` 组合可用；但 storage 初始化条件过窄，且持久化不是即时的。级别：高（初始化条件）、中（持久化时机）。

### 5.2 `GM_addValueChangeListener` / `GM_removeValueChangeListener`

**标准**（[TM values][TM-values]、[VM GM API][VM-gm]）：

```js
const id = GM_addValueChangeListener(key, (key, oldValue, newValue, remote) => {});
GM_removeValueChangeListener(id);
```

ChromeXt 实现（`GM.js:425-435`、`GM.js:1191-1213`）：

```js
function GM_addValueChangeListener(key, listener) {
  const index = GM_info.valueListener.findIndex(
    (e) => e.key == key && e.listener == listener
  );
  if (index != -1) {
    GM_info.valueListener[index].enabled = true;
    return index;
  }
  GM_info.valueListener.push({ key, listener, enabled: true });
  return GM_info.valueListener.length - 1;
}
```

触发处（`GM.js:1200-1207`）：

```js
GM_info.valueListener.forEach((v) => {
  if (v.enabled == true && v.key == data.key) {
    v.listener(
      GM_info.storage[data.key] || null,
      data.value,
      e.detail.uuid != GM_info.uuid
    );
  }
});
```

| 项目 | ChromeXt | 标准 | 差异 | 级别 |
|---|---|---|---|---|
| 回调参数 | 只传 3 个：旧值、新值、remote | 4 个：`key, oldValue, newValue, remote` | **缺少第一个 `key`**，后续参数整体左移；按标准写的回调会把 `oldValue` 当 key | 高 |
| `oldValue` | `GM_info.storage[data.key] \|\| null` | 创建时为 `undefined`；删除时为旧值 | `0`/`""`/`false` 会被 `\|\|` 变成 `null`；创建时标准是 `undefined`，ChromeXt 是 `null` | 中 |
| 删除通知 | `if ("key" in data && data.key in GM_info.storage)` 才进入；本地删除时 key 已不存在（`GM.js:1157-1161`） | 删除时以 `newValue === undefined` 通知 | **本地删除不会触发监听器**；跨标签页删除可能触发，但只是把 key 设为 `undefined`，`GM_listValues` 仍包含该 key | 高 |
| 返回值 | 数组索引（number） | listener id（TM 是 number/string；VM 是 string） | 类型可能不同；通常可作为 opaque id 使用 | 低 |
| 监听器初始化 | 只有 grant 含 `GM.getValue`/`GM_getValue` 时 `GM_info.valueListener = []`（`GM.js:1191-1213`） | 有监听器 API 就应可用 | 仅 `@grant GM_addValueChangeListener` 时 `GM_info.valueListener` 为 undefined，`findIndex` 抛 `TypeError` | 高 |
| 移除 | `GM_info.valueListener[index].enabled = false`（`GM.js:327-329`） | 删除监听器 | 只是禁用，数组项保留；再次添加同一 key+listener 会复用并重新启用（这是 ChromeXt 的额外行为，基本可接受） | 低 |
| 跨标签页 remote | `e.detail.uuid != GM_info.uuid` | 是否为其他脚本实例 | 逻辑基本一致 | 一致 |

**结论**：这是当前最严重的兼容性缺陷之一。任何按标准签名写的 `GM_addValueChangeListener` 回调都会拿到错误的参数；本地删除事件会丢失，跨标签页删除的语义也不正确。

### 5.3 `GM.*` 异步别名（存储部分）

`Local.kt:60-74` 会把 `@grant GM.getValue` 等映射到对应的 `GM_getValue`，然后在 `GM.bootstrap()` 中用 `{sync: GM_getValue}` 包装（`GM.js:1215-1239`）。对于返回 Promise 的函数直接透传，否则包成 `new Promise`：

```js
GM[name] = function () {
  const result = sync.apply(null, arguments);
  if (result instanceof Promise) return result;
  return new Promise(async (resolve) => {
    resolve(await result);
  });
};
```

| 项目 | ChromeXt | 标准 | 差异 |
|---|---|---|---|
| `GM.getValue/setValue/deleteValue/listValues` | 返回 Promise | GM4/VM 异步 API 返回 Promise | 基本一致 |
| `GM.addValueChangeListener` | 返回 Promise<number> | 返回 Promise<listener id> | 基本一致（底层 bug 仍存在） |
| `GM.removeValueChangeListener` | 返回 Promise | 返回 Promise | 基本一致 |
| `GM.setValues/getValues/deleteValues` | 未实现；见 9.3 的 ReferenceError 风险 | TM 5.3+ | 缺失 |

---

## 6. 资源与下载

### 6.1 `GM_getResourceText` / `GM_getResourceURL`

ChromeXt 的资源处理分两步：

1. `GM.bootstrap()` 解析元数据时把 `@resource` 归一化为 `{name, url}`（`GM.js:1069-1075`）。
2. 脚本运行前，如果 `GM_xmlhttpRequest` 可用且存在资源，则逐项下载并写入 `data.content`，然后才执行原脚本（`GM.js:1272-1280`）。

```js
meta.sync_code = meta.code;
meta.code = async () => {
  for (const data of meta.resources) {
    data.content = await GM_xmlhttpRequest({ url: data.url });
  }
  return meta.sync_code();
};
```

| 项目 | ChromeXt | 标准 | 差异 | 级别 |
|---|---|---|---|---|
| `GM_getResourceText(name)` | `resources.find(...).content \|\| "ChromeXt failed to find resource " + name`（`GM.js:458-463`） | 返回资源文本；资源在安装时预加载（[TM GM_getResourceText][TM-resource]、[VM GM API][VM-gm]） | 资源名不存在时 `.find` 返回 undefined，访问 `.content` 会抛 `TypeError`，而不是返回错误字符串；未预加载/下载失败时返回错误字符串 | 中 |
| `GM_getResourceURL(name)` | 返回 `resources.find(...).url`（`GM.js:466-471`） | 返回可加载的资源 URL；VM 默认 `blob:`，`false` 时 `data:`（[VM GM API][VM-gm]） | 返回**原始远程 URL**（或 `data:` URL），不是 blob/data 包装；CSP、离线、鉴权、相对路径场景都可能不同 | 高 |
| 预加载时机 | 运行时、脚本执行前逐项下载（`GM.js:1272-1280`） | 安装时预加载并缓存 | 每个页面/每次运行都可能重新下载；脚本启动被网络延迟阻塞；资源服务器不可用时脚本仍会执行（content 为 undefined） | 中 |
| `@connect` 限制 | 资源预加载在用户代码顶部的初始化块之前执行，此时 `GM_xmlhttpRequest.strict` 尚未定义，`@connect` 检查被跳过（`GM.js:1272-1280`、`GM.js:17-20`、`GM.js:485`） | 资源预加载通常不受 `@connect` 限制 | 当前方向与标准一致，但依赖执行顺序；若以后把 `strict` 初始化提前，就会变成受限 | 信息 |
| grant 自动补全 | `Parser.kt:59-64` 只在 grant 含 `GM_download`/`GM.xmlHttpRequest`/`GM_getResourceText` 时自动补 `GM_xmlhttpRequest`；`GM.getResourceText` 不在列表中 | 使用资源 API 时应能加载资源 | 只 grant `GM.getResourceText` 时资源不会被下载，函数返回错误字符串 | 高 |
| 相对 URL | 直接 `GM_xmlhttpRequest({url: data.url})`；strict 未设置时 fetch 会按当前页面解析相对 URL，回退 Kotlin 路径则 `URL()` 抛错 | 相对于脚本安装 URL 解析（VM） | 相对 `@resource` 可能解析到错误基址或失败 | 中 |
| SRI | 无校验；`#...` fragment 被剥掉（`GM.js:1072-1074`） | 支持 `#sha256=` 等（TM） | 完整性保护缺失 | 中 |

### 6.2 `GM_download`

| 项目 | ChromeXt | 标准 | 差异 | 级别 |
|---|---|---|---|---|
| 签名 | `GM_download(details)` 或 `GM_download(url, name)`（`GM.js:365-384`） | 同（[TM GM_download][TM-download]） | 基本一致 | 一致 |
| 实现 | 调用 `GM_xmlhttpRequest` 取 `blob`，再创建 `<a download>` 并 `dispatchEvent(new MouseEvent("click"))`（`GM.js:369-383`） | 浏览器下载 API 或 native 下载；支持保存位置/冲突策略 | 依赖页面/浏览器对 `download` 属性的支持；跨域文件名可能被忽略 | 中 |
| 成功判定 | `res.status !== 200` 直接 `console.error`（`GM.js:373-374`） | 任意成功下载（含 2xx） | 206/201 等会被当成失败 | 中 |
| 回调 | 用户传入的 `onload` 被覆盖；`onerror`/`onprogress`/`ontimeout` 可能透传给 GM_xmlhttpRequest，但下载层的错误不会走 `onerror` | `onload`/`onerror`/`onprogress`/`ontimeout` | 回调语义不完整；HTTP 非 200 只打 console | 高 |
| 选项 | `headers`/`anonymous` 可透传；`saveAs`/`conflictAction` 未实现；`Blob`/`File` URL 未处理 | `saveAs`、`conflictAction`、`anonymous`、`Blob`/`File`（TM 5.4+） | 依赖这些选项的脚本不兼容 | 中 |
| 返回值 | 返回 `GM_xmlhttpRequest` 的 thenable Proxy（含 `abort`）；`GM.download` 直接返回同一个 Proxy，因为 `instanceof Promise` 成立 | 返回 `{abort}`；`GM.download` 返回 Promise，resolve 下载详情且带 `abort` | `GM_download` 的 `abort` 可工作；`GM.download` 的 resolve 值是响应 blob，而不是标准下载详情（见 9.3） | 中 |

### 6.3 `@require` / `@resource` 的加载语义

| 项目 | ChromeXt | 标准 | 差异 | 级别 |
|---|---|---|---|---|
| `@require` 下载时机 | `parseScript()` 里 `Chrome.IO.submit { ... downloadLib ... }` 异步填充 `lib`，但 `Script` 立即构造（`Parser.kt:76-88`） | 安装/加载时下载完成后再执行脚本 | **竞态**：注入时 `script.lib` 可能还没下载完；没有 join/await/失败回滚 | 高 |
| `@require` 相对 URL | `URL(libUrl)` 直接解析（`Parser.kt:104`） | 相对于脚本安装 URL（VM） | 相对路径可能抛 `MalformedURLException`，且异常被 `runCatching` 吞掉 | 中 |
| `@require` SRI | 未校验；URL fragment 不参与 HTTP 请求，但也不会校验 | 支持 `#sha256=`/`#md5=`（TM） | 完整性保护缺失 | 中 |
| `@require` 执行位置 | 拼接到 `meta.code` 内、用户代码之前（`Local.kt:83`） | 在用户脚本之前、同一 sandbox 内 | 基本一致；但 `@require` 也能看到 `GM_info`/GM 内部变量，标准未保证 | 低 |
| `@resource` 解析 | JS 侧 `split(/\s+/)`，取前两段；`#` 后内容丢弃（`GM.js:1069-1075`） | 名称 + URL；支持 SRI | 不支持带空格的 URL（标准也不支持）；SRI 丢失 | 中 |
| `@resource` 预加载 | 运行时下载（见 6.1） | 安装时预加载 | 每个页面/每次运行都可能重新下载；脚本启动被网络阻塞；资源未加载时 `content` 为 `undefined` | 中 |

---

## 7. 网络与 Cookie

### 7.1 `GM_xmlhttpRequest` / `GM.xmlHttpRequest`

ChromeXt 有两条执行路径：

- **fetch 路径**（默认 `useJSFetch = true`）：用 `fetch()` 发请求，若遇到 CORS/禁止头等问题抛 `TypeError`，则回退到 Kotlin 路径（`GM.js:776-821`）。
- **Kotlin 路径**：通过 `chrome.dispatch("xmlhttpRequest")` 交给 `XMLHttpRequest.kt` 的 `HttpURLConnection` 发送（`Listener.kt:282-298`、`XMLHttpRequest.kt:58-142`）。

因此同一份 `details` 在不同站点/请求上可能走不同路径，行为也不同。

| 特性 | ChromeXt | 标准（TM） | 差异 | 级别 |
|---|---|---|---|---|
| 基本签名 | `GM_xmlhttpRequest(details)`；`GM.xmlHttpRequest` 为 Promise 版本（`GM.js:474-863`） | 同（[TM GM_xmlhttpRequest][TM-xhr]） | 基本一致 | 一致 |
| `@connect` 检查 | 始终开启（`GM.js:17-20` 设 `strict`；`GM.js:485-499`）。同源也要求 `connects.includes("self")` | 定义允许请求的域名；通常同源不要求 `@connect`，跨域可弹确认 | **同源请求也可能被拒绝**；只检查初始 URL，不检查重定向后的 final URL；`domain.endsWith(it)` 会误放行 `evil-example.com` 这类域名 | 高 |
| 相对 URL | `new URL(details.url)` 在 strict 检查中要求绝对 URL（`GM.js:486`） | 允许相对于当前页面（TM/VM） | 相对 URL 会抛错 | 中 |
| `method` 默认 | 有 `data` 时默认 POST，否则 GET（`GM.js:501-504`） | 通常默认 GET；`data` 不自动改方法 | 省略 method 且带 data 的脚本可能得到 POST | 中 |
| `GET/HEAD` 的 data | 删除 `data`/`body`（`GM.js:505-508`） | 标准未明确禁止（部分实现会附加到查询串） | 行为不同 | 低 |
| FormData | 非 binary FormData 手工拼成 `k=v&...`；GET 时对整个串 `encodeURIComponent`，POST 时直接作为 `x-www-form-urlencoded`（`GM.js:519-574`） | 浏览器 FormData 编码 | GET 查询参数被整体编码；POST 的特殊字符/重复键可能出错 | 中 |
| `url` 类型 | 只接受字符串/URL；Blob/File 会在 strict 检查或 `Request` 构造中失败 | TM 5.4+ 支持 `Blob`/`File` | 依赖 Blob/File 的请求不兼容 | 中 |
| `headers` | 转成 `Headers`；fetch 路径会检查禁止头，失败则回退（`GM.js:512`、`GM.js:762-768`） | 支持 Cookie/Host/Origin/Referer/User-Agent 等特殊头 | Kotlin 路径可能被 `HttpURLConnection` 限制；Cookie 头可能被 cookie store 覆盖 | 中 |
| `cookie` 选项 | 把 cookie 解析后加入全局 `Chrome.cookieStore`（`XMLHttpRequest.kt:43-52`），不是仅合并到本次请求 | 合并到本次请求的 Cookie，同名替换，不持久化（TM 5.6+） | **会污染全局 cookie store**；`cookiePartition` 不支持 | 高 |
| `anonymous` | fetch 路径 `credentials: "omit"`；Kotlin 路径不发送 cookie（`GM.js:759`、`XMLHttpRequest.kt:67`） | 不发送 cookie | 基本一致 | 一致 |
| `nocache` | Kotlin 路径 `setUseCaches(!nocache)`；fetch 路径固定 `cache: "force-cache"`（`GM.js:756`、`XMLHttpRequest.kt:64`） | 控制缓存 | fetch 路径忽略 `nocache`，反而强制缓存 | 中 |
| `revalidate` | 未实现 | 重新验证缓存 | 缺失 | 低 |
| `timeout` | Kotlin 路径只 `setConnectTimeout(timeout)`（`XMLHttpRequest.kt:65`）；fetch 路径转成 `AbortSignal.timeout`（`GM.js:745-751`），超时触发 `abort` 而非 `timeout` | 请求级超时，触发 `ontimeout` | 慢响应体可能不超时；fetch 路径 `ontimeout` 不触发 | 高 |
| `redirect` | fetch 路径把 `redirect` 传给 `Request`；Kotlin 路径只区分 `manual`，`error` 被当作 follow（`XMLHttpRequest.kt:62`）；`xhr.redirect` 没有从 details 暴露，`error` 分支基本不可达（`GM.js:690-693`、`GM.js:964-965`） | `follow`/`error`/`manual` | `redirect: "error"` 不可靠；`manual` 在 Kotlin 路径可用 | 高 |
| `finalUrl` | `headers.get("Location") \|\| xhr.url`（`GM.js:957-958`） | 重定向后的最终 URL | Kotlin 自动跟随重定向后没有最终 URL，`finalUrl` 通常仍是原 URL；fetch 路径也不保证 | 高 |
| `responseType` | `text`/`json`/`blob`/`arraybuffer`/`document`/`stream`（`GM.js:884-927`） | TM：`arraybuffer`/`blob`/`json`/`stream`；VM 还有 `document` | 额外支持 `document`；`stream` 在 `close()` 时才创建 `blob.stream()`，不是实时流 | 中 |
| `onreadystatechange` | `readyState` 赋值只 `dispatchEvent(new Event("readystatechange"))`（`GM.js:659-661`），没有调用 `xhr.onreadystatechange` 属性 | 属性回调在状态变化时调用 | 用 `onreadystatechange = fn` 很可能不触发；`addEventListener` 可用 | 高 |
| `onprogress` | Kotlin 路径按块上报；fetch 文本路径一次性读取后只发一次 progress（`GM.js:798-810`） | 持续上报已接收数据 | fetch 文本路径不是流式进度 | 中 |
| `context` | `details.context` 没有复制到响应对象（`ResponseSink.parse` 只复制 data 的字段，`GM.js:928-935`） | 响应对象包含 `context` | `response.context` 为 undefined | 中 |
| `user`/`password` | 仅 Kotlin 路径设置 Basic Authorization（`XMLHttpRequest.kt:70-75`）；fetch 路径忽略 | 支持 | 走 fetch 路径时鉴权不生效 | 中 |
| `fetch: true` | fetch 路径可返回 `Response` 对象（`GM.js:783-795`） | TM 的 `fetch` 模式行为不同 | 只部分模拟 | 低 |
| `proxy` / `upload` | 未实现 | TM `proxy`（Firefox）、VM `upload` | 缺失 | 低 |
| `GM.xmlHttpRequest` Promise | 返回 `new Promise`，但**没有 `abort` 属性**；async executor 在底层 reject 时不会 reject 外层 Promise（`GM.js:1223-1233`） | Promise 版本也带 `abort`（TM） | 依赖 `promise.abort()` 的脚本失败；网络错误可能让 Promise 永远 pending | 高 |
| 二进制/编码 | Kotlin 侧按 `Content-Type` 的 charset 决定是否 base64；无 charset 时可能按平台默认解码 | 按字节/编码处理 | 非 UTF-8 且无 charset 的响应可能乱码 | 低 |

**结论**：`GM_xmlhttpRequest` 的“能发请求”部分可用，但权限检查、重定向、超时、finalUrl、onreadystatechange、context、cookie 语义都与标准有显著差异。级别：高。

### 7.2 `GM_cookie.list/set/delete`

ChromeXt 通过 DevTools Protocol 的 `Network.getCookies` / `Network.setCookies` / `Network.deleteCookies` 实现（`GM.js:99-202`、`Listener.kt:299-325`）。

| 项目 | ChromeXt | 标准（TM） | 差异 | 级别 |
|---|---|---|---|---|
| `list(details, callback)` | 默认 `{url: window.origin}`；调用 `Network.getCookies` 后按 `domain/name/path` 本地过滤（`GM.js:145-169`） | 按 url/domain/name/path 过滤；检查脚本对 `details.url` 的 `@include/@match` 权限（[TM GM_cookie][TM-cookie]） | JS 传的 `params` 是数组，Kotlin `optJSONObject("params")` 读成 `null`（`Listener.kt:302`）；按 CDP 协议未指定 `urls` 时不会返回 cookie，若目标版本遵循该语义，`list()` 可能始终为空（需运行复核）。未做脚本权限检查 | 高 |
| 返回 cookie 形状 | `CookieParam` 直接 `Object.assign` CDP 字段：`expires`、`session`、`sourcePort`、`sourceScheme` 等（`GM.js:204-220`） | `expirationDate`（秒）、`hostOnly`、`httpOnly`、`sameSite`、`secure`、`session`、`value` 等（[TM GM_cookie][TM-cookie]） | 字段名 `expires` vs `expirationDate`；缺少 `hostOnly`/`firstPartyDomain`/`partitionKey`；多出 CDP 内部字段 | 高 |
| `set(details, callback)` | `expirationDate` 转 `expires`；缺省 `domain = location.hostname`；`Network.setCookies`（`GM.js:185-196`） | 缺省 `url` 为当前文档；支持 `url/domain/path/secure/httpOnly/expirationDate/partitionKey/firstPartyDomain` | 缺省作用域用 domain 而不是 url；`sameSite` 大小写未归一化（CDP 期待 `Lax`/`Strict`/`None`）；`partitionKey` 等未处理 | 中 |
| `delete(details, callback)` | 缺省 `domain = location.hostname`（`GM.js:197-201`） | 缺省 `url` 为当前文档 | 作用域语义不同 | 中 |
| 回调 | `set/delete` 调用 `callback(error?.message)` 后，如果 `error` 是 Error 还会抛出（`GM.js:170-184`）；`list` 调用 `callback(cookies, error?.message)` | 回调只接收 error/cookies，不应额外抛出 | 有回调时仍可能产生未处理的 Promise rejection | 中 |
| `GM.cookie` 别名 | `GM.cookie` 直接是 `GM_cookie` 对象（`Local.kt:60-74`、`GM.js:1215-1239`） | TM 也有 `GM.cookie` | 基本一致 | 一致 |
| httpOnly | CDP 可返回 httpOnly cookie | TM 文档标注 httpOnly 仅在 BETA 支持 | ChromeXt 可能比 TM 稳定版更宽松 | 信息 |
| 非标准扩展 | 暴露 `GM_cookie.store` getter 和 `GM_cookie.export()`（`GM.js:99-144`） | 无 | ChromeXt 自有扩展 | 信息 |

**结论**：`GM_cookie.list` 的参数传递问题是最可疑的实现缺陷；返回对象字段与 TM 不兼容。级别：高。

---

## 8. 标签页、菜单与通知

### 8.1 `GM_openInTab`

| 项目 | ChromeXt | 标准（TM） | 差异 | 级别 |
|---|---|---|---|---|
| 签名 | `GM_openInTab(url, options = true)`（`GM.js:387-402`） | `GM_openInTab(url, options)` 或 `(url, loadInBackground)`（[TM GM_openInTab][TM-openintab]） | 基本签名兼容 | 一致 |
| 返回值 | `window.open()` 返回的 window（被拦截时返回当前 `window`） | `{close, closed, onclose}` | 没有 `onclose`；`closed`/`close` 依赖 window 对象；不能可靠判断打开失败 | 高 |
| 新标签页目标 | 固定 `target = "GM_openInTab"`（`GM.js:388`） | 每次打开新标签页 | 同名 target 会复用已存在的命名窗口，连续打开可能只得到一个标签页 | 高 |
| `setParent` | 设为 `target = "_self"`（`GM.js:392`） | 设为当前标签页的子标签页 | 语义完全不同：ChromeXt 会**在当前标签页导航** | 高 |
| `active` | 布尔值时 `active: !options`；对象时直接使用（`GM.js:389-400`） | 默认 `active: false`；布尔参数是 `loadInBackground` 的反向语义 | 默认/布尔语义基本一致 | 低 |
| `insert`/`pinned`/`incognito` | 未实现 | TM/VM 支持 | 缺失 | 低 |
| 弹窗拦截 | 直接 `window.open` | 扩展 API 打开 | 非用户手势下可能被浏览器拦截 | 中 |

### 8.2 `GM_registerMenuCommand` / `GM_unregisterMenuCommand`

| 项目 | ChromeXt | 标准（TM） | 差异 | 级别 |
|---|---|---|---|---|
| 注册签名 | `GM_registerMenuCommand(title, listener, _accessKey = "Dummy")`（`GM.js:405-422`） | `(name, callback, options_or_accessKey)`；支持 `accessKey`、`id`、`autoClose`、`title`、`clickedFrame`（[TM GM_registerMenuCommand][TM-menu]） | `options`/`accessKey` 被忽略；不能按 `id` 原位更新；不能控制 `autoClose` | 中 |
| 返回值 | `ChromeXt.commands` 的**全局数组索引**（`GM.js:407-421`） | 菜单项 id（TM 5.0+ 可为 number/string） | 索引是跨脚本的全局数组下标；虽然当前 `unregister` 不删除元素，索引相对稳定，但不是标准 id | 中 |
| 回调参数 | PageMenu 调用 `listener()`，无参数（`PageMenu.kt:82-87`） | 传入 MouseEvent/KeyboardEvent（TM 4.14+），旧版传当前 tab | 依赖事件对象（`event.shiftKey` 等）的脚本会失败 | 中 |
| 去重/更新 | 同脚本 + 同 title 视为同一项，替换 listener（`GM.js:407-413`） | 按 id 或 name/title/accessKey 合并；支持原位更新 | 同 title 的不同命令无法共存；`accessKey` 不参与去重 | 中 |
| 注销 | `ChromeXt.commands[index].enabled = false`（`GM.js:332-335`） | 从菜单移除 | 只隐藏不删除；再次注册同 title 时**不会重新启用**，命令会一直不可见 | 高 |
| 菜单呈现 | Chrome 页面菜单 → “用户脚本”子菜单，按脚本名分组（`PageMenu.kt:289-371`、`scripts/updateMenuCommands.js`） | 管理器弹窗/页面菜单 | 呈现位置/分组不同；原 README 说的“eruda Resources 面板”已过时 | 信息 |
| frame 合并 | 只从主 frame 收集命令（`PageMenu.kt:296` 未传 frameId） | 多 frame 同名命令合并 | frame 内注册的命令可能不可见 | 中 |

### 8.3 `GM_notification`

| 项目 | ChromeXt | 标准（TM） | 差异 | 级别 |
|---|---|---|---|---|
| 签名 | `GM_notification(details, ondone)` 或位置参数（`GM.js:47-96`） | 同（[TM GM_notification][TM-notification]） | 基本一致 | 一致 |
| `title` | Kotlin `detail.getString("title")`（`Listener.kt:218`） | `title` 可选 | 没传 `title` 会抛 JSONException，通知不显示 | 高 |
| `text` | JS 检查 `!("text" in payload \|\| payload.highlight)`（`GM.js:66-67`）；但 Kotlin 仍 `getString("text")`（`Listener.kt:219`） | `text` 与 `highlight` 至少一个 | 只传 `highlight` 时 JS 放行、Kotlin 失败 | 高 |
| `timeout` | 非整数时设置 10 秒，但不注册 JS 侧 `ondone` 定时器（`GM.js:57-65`） | 默认/自定义超时；关闭时调用 `ondone` | 默认 10 秒自动关闭时 `ondone` 不会触发（只有调用方显式传整数 timeout 才会挂 JS 定时器） | 中 |
| `onclick` | Kotlin 设置 PendingIntent；点击后 `onclick()` 无参数（`GM.js:69-91`、`Listener.kt:235-238`） | 传 MouseEvent/KeyboardEvent；支持 `url` 和 `event.preventDefault()` | 事件对象缺失；`url` 未实现 | 中 |
| `tag` | 未实现 | TM 5.0+ 用 tag 替换已有通知 | 每次都是新通知 | 中 |
| `url` | 未实现 | TM 5.0+ 点击打开 URL | 缺失 | 中 |
| `highlight` | 请求 Android 窗口焦点（`Listener.kt:255`） | 高亮发送通知的标签页 | 行为近似但实现层不同 | 低 |
| `silent` | 选择低重要性 channel（`Listener.kt:222-223`） | 静音 | 基本一致 | 低 |
| `image` | 异步下载并设置 large icon（`Listener.kt:241-251`） | 支持图片 URL | 基本一致；失败只 log | 低 |
| 返回值 | 无 | 无（TM） | 一致 | 一致 |
| 权限/系统限制 | Android O 以下直接 console.error（`Listener.kt:211-214`）；Android 13+ 还受通知权限影响 | 浏览器通知权限 | 设备相关 | 信息 |

---

## 9. 元数据指令、匹配与执行上下文

### 9.1 `@match` / `@include` / `@exclude` / `@exclude-match`

ChromeXt 不使用 Chrome 的 match pattern 实现，而是 `Url.kt:17-66` 的自定义字符串替换 + Java 正则。

**匹配逻辑摘要**：

- 正则形式 `/.../`：去掉首尾 `/`，把 `\/` 还原为 `/`、`\://` 还原为 `://`，用 `Regex.containsMatchIn`（`Url.kt:21-24`、`Url.kt:41-47`）。
- 无 `*`：
  - `strict == true`（`@exclude`）→ 完整字符串相等；
  - `strict == false`（`@match`/`@include`）→ **子串包含**（`Url.kt:25-29`）。
- 有 `*` 且含 `://`（或 strict）：
  - 转义 `?`、`.`；
  - `*` → `[^:]*`；
  - `[^:]*\.` → `([^:]*\.)?`；
  - 用 `Regex.matches` 做整串匹配（`Url.kt:31-47`）。

| 输入模式 / URL | ChromeXt 结果 | Chrome Match Patterns / TM 预期 | 差异 | 级别 |
|---|---|---|---|---|
| `*://*/*` vs `ftp://example.com/a` | 匹配 | `*` scheme 只匹配 `http`/`https`（[Chrome Match Patterns][Chrome-match]） | ChromeXt 会匹配任意 scheme | 中 |
| `http://example.com/*` vs `http://example.com:8080/a` | 不匹配 | 未显式写端口时匹配所有端口 | 端口语义不同 | 中 |
| `https://*.example.com/foo*bar` vs `https://example.com/fooXbar` | 匹配 | `*.` 也匹配裸域（TM 文档示例） | 一致 | 一致 |
| `@include https://example.com/`（无 `*`）vs `https://example.com/path` | 匹配（子串） | TM 对含 `://` 的 include 按类 match 处理 | 可能过宽 | 中 |
| `@exclude https://example.com/private`（无 `*`）vs `https://example.com/private?x` | 不匹配 | 按 URL 模式/前缀处理 | 排除过窄 | 中 |
| `@include *` | 匹配任意 URL | 匹配任意 URL | 一致 | 一致 |
| 正则 `/^https:\/\/example\.com\/.*$/` | 匹配（`containsMatchIn`） | TM 支持正则 include；语义近似 | 部分匹配 vs 整串匹配可能有差异 | 低 |
| `<all_urls>` | 不特殊处理，作为普通字符串 | TM 文档称 `<all_urls>` 尚未支持 | 两边都不支持 | 信息 |
| `@exclude-match` | 未解析 | VM 支持 | 缺失 | 中 |

其他匹配差异：

- `Parser.kt:44-45` 把 `@include` 直接追加到 `match` 列表，**`@include` 与 `@match` 共用同一个匹配器**，但 `GM_info.script.includes` 与 `matches` 分开保存。
- 既不含 `://` 又含 `*` 的模式（例如 `*foo*`）会直接返回 false，而不是按通配符匹配（`Url.kt:31-38`）。
- `@match`/`@include` 缺失时 `parseScript()` 返回 `null`（`Parser.kt:73-75`），脚本无法安装。
- `@match` 的 URL 是完整 URL（含 query/hash）；Chrome match pattern 的 hash 处理与 ChromeXt 不同，SPA/hash 路由场景需实测。
- 无效正则被 `runCatching` 捕获后只记日志并返回 false（`Url.kt:40-50`）。

### 9.2 `@run-at`

| 值 | ChromeXt | 标准（TM） | 差异 | 级别 |
|---|---|---|---|---|
| `document-start` | 立即调用 `meta.code()`（`GM.js:1283-1285`） | 尽可能早注入 | 近似；ChromeXt 的注入时机取决于 `onUpdateUrl` 和 `javascript:`/DevTools 路径 | 中 |
| `document-end` | 若 `readyState != loading` 立即执行，否则监听 `DOMContentLoaded`（`GM.js:1286-1292`） | DOMContentLoaded 时或之后 | 基本一致 | 一致 |
| `document-idle`（默认） | 若 `readyState == complete` 立即执行，否则监听 `load`（`GM.js:1293-1298`） | DOMContentLoaded 之后异步执行 | **ChromeXt 等的是 `load`，比 TM 的 document-idle 更晚**；资源多的页面延迟明显 | 高 |
| `document-body` | 未实现，落到 default 分支 | TM/VM 支持 | 缺失 | 中 |
| `context-menu` | 未实现 | TM 5.5+ 支持 | 缺失 | 低 |

此外，`@require` 的异步下载会让实际执行时间进一步晚于 `@run-at` 目标（见 6.3）。

### 9.3 `@grant` 与 `GM.*` 解析

`Local.kt:47-76` 的处理规则：

- `none`、`frames`、`GM_info`、`GM.ChromeXt`、`window.close`：跳过，不拼接对应 GM.js 块。
- 其他 `GM_*`：
  - 若 `GM.js` 中存在同名块 → 拼接实现；
  - 否则 → 拼接一个 stub：
    ```js
    function GM_xxx(){ console.error('GM_xxx is not implemented in ChromeXt yet, called with', arguments) }
    ```
- `GM.*`：
  - `GM.xmlHttpRequest` → `GM_xmlhttpRequest`
  - `GM.getResourceUrl` → `GM_getResourceURL`
  - 其他 → `GM_` + 去掉 `GM.` 后的名字
  - 如果对应 `GM_*` 实现存在且未单独 grant，则先拼入实现；
  - 然后**无条件**拼接 `GM.xxx={sync: GM_xxx};`。

由此产生的问题：

| 场景 | 结果 | 级别 |
|---|---|---|
| `@grant GM.getTab` / `GM.setValues` / `GM.getValues` / `GM.deleteValues` / `GM.webRequest` / `GM.audio.*` 等未实现的 `GM.*` | 生成 `GM.getTab={sync: GM_getTab};`，而 `GM_getTab` 未定义 → 脚本 IIFE 抛 `ReferenceError`（`GM.audio.*` 还会因 `GM.audio` 为 undefined 抛 `TypeError`），整个脚本不执行 | 高 |
| `@grant GM.getTab` + `@grant GM_getTab` | 先拼接 stub `GM_getTab`，不会 ReferenceError；但调用时只打印 “not implemented” | 中 |
| `@grant GM.addValueChangeListener` 但没有 `GM.getValue`/`GM_getValue` | `GM_info.valueListener` 未初始化，底层 `GM_addValueChangeListener` 抛 `TypeError` | 高 |
| `@grant GM.addValueChangeListener` + `GM.getValue` | 监听器可用，但 `broadcast` 仍为 false（`GM.js:1139` 只认 `GM_addValueChangeListener`），跨标签页同步不完整 | 中 |
| `@grant GM.cookie` | `GM_xmlhttpRequest.addCookie` 只检查 `grants.includes("GM_cookie")`（`GM.js:18`）；点号形式不会启用自动 cookie 转发 | 中 |
| 未 grant 的 `GM_*` | 不拼接实现，脚本访问时得到 `ReferenceError` | 与标准一致 |
| `@grant none` | 不拼接任何 GM_*，但 `GM_info` 仍存在；`GM.globalThis` 指向真实 window（页面上下文） | 与 TM 的 grant none 语义基本一致 |
| `@grant window.close` | sandbox 下 `window.close` 被代理为 `ChromeXt.dispatch("close")`；page 模式下直接覆盖 `window.close`（`GM.js:1085-1088`、`GM.js:1115-1116`） | 基本一致 |
| `@grant window.focus` | 未特殊处理；不会获得额外权限 | 缺失 |
| `@grant GM_info` | 被跳过；`GM_info` 本来就存在。若写 `@grant GM.info`，会生成 `GM.info={sync: GM_info}`，bootstrap 再展开为 `GM.info = GM_info` | 非标准但可用 |
| `@grant GM.ChromeXt` | `GM.ChromeXt = ChromeXt`（`GM.js:1241-1243`），脚本可 dispatch 任意 action | 非标准扩展，见第 10 节 |

**`GM.*` 返回类型**：bootstrap 对非 Promise 结果统一包成 Promise（`GM.js:1215-1239`）。因此以下 API 的 `GM.*` 版本与标准/VM 的同步返回不同：

- `GM.addStyle` → Promise（VM 是同步返回元素）
- `GM.addElement` → Promise（VM 是同步返回元素）
- `GM.openInTab` → Promise（标准是同步返回控制对象）
- `GM.registerMenuCommand` → Promise（标准是同步返回 id）
- `GM.getResourceText` / `GM.getResourceUrl` → Promise（异步 API 合理）
- `GM.download` → 返回 `GM_xmlhttpRequest` 的 thenable Proxy（含 `abort`），resolve 值是响应 blob，而不是标准下载详情
- `GM.notification` → Promise，立即 resolve，不等待点击
- `GM.xmlHttpRequest` → Promise，响应对象是 `{...xhr}` 的浅拷贝，没有 `abort` 属性；底层请求 reject 时外层 Promise 不会 reject，可能一直 pending
- `GM.setClipboard` → Promise（合理）

### 9.4 `@noframes` / frame 注入

| 项目 | ChromeXt | 标准 | 差异 | 级别 |
|---|---|---|---|---|
| 默认 frame 行为 | 不注入 iframe；只有主页面某个脚本 grant 了 `frames`，才调用 `Chrome.injectFrames()`（`Manager.kt:159-171`） | TM/VM 默认注入所有匹配的 frame，除非 `@noframes` | **默认行为相反**：ChromeXt 默认不进 frame | 高 |
| `@noframes` | Kotlin 解析为 `noframes = true`（`Parser.kt:49-54`），frame 注入时跳过（`Manager.kt:161`） | 只在顶层文档运行 | 语义一致，但由于默认就不进 frame，实际影响被放大 | 中 |
| `@grant frames` | 非标准 grant；仅用于触发 frame 注入 | 无 | ChromeXt 自有约定 | 信息 |
| frame 注入方式 | DevTools `Page.frameScheduledNavigation` + `Page.frameNavigated`，再用 `Page.navigate("javascript: ...")`（`Chrome.kt:213-245`） | 浏览器原生 content script 注入 | 依赖 DevTools，WebView/部分浏览器可能不可用；时序和 CSP 行为不同 | 中 |

### 9.5 `@inject-into` / `@sandbox` / 默认上下文

ChromeXt 在 `GM.bootstrap()` 中的选择逻辑（`GM.js:1083-1134`）：

```js
if (meta["inject-into"] == "page" || grants.includes("none")) {
  GM.globalThis = window; // 真实页面 window
} else {
  const handler = { window: { GM }, ... };
  GM.globalThis = new Proxy(window, handler);
}
```

| 项目 | ChromeXt | 标准 | 差异 | 级别 |
|---|---|---|---|---|
| 默认（无 `@grant none`、无 `@inject-into page`） | `GM.globalThis` 是 Proxy；用户代码里的 `window` 指向 Proxy；普通属性写入落到 `handler.window`（sandbox 对象），不写真实页面 window（`GM.js:1102-1110`、`GM.js:1126-1128`） | TM 默认 `@sandbox raw` → MAIN_WORLD（页面上下文）；VM 默认 `@inject-into auto`，优先页面上下文 | **默认不是页面上下文**；依赖 `window.foo = ...` 修改页面的脚本默认失效 | 高 |
| `@grant none` | 切到真实 window（页面上下文） | 关闭 sandbox，页面上下文 | 基本一致 | 一致 |
| `@inject-into page` | 切到真实 window | VM：页面上下文 | 一致 | 一致 |
| `@inject-into content` | 仍使用 Proxy；只是 `Reflect.set` 在 libLoading 时被禁用（`GM.js:1107-1109`） | VM：真正的 content script 隔离环境，不能访问页面 JS 对象 | **不是真正隔离**：Proxy 以真实 window 为 target，可读取其内置/不可枚举属性，也能读到 sandbox 中已写入的属性；写入隔离方式与 VM 不同 | 高 |
| `@inject-into auto` | 未特殊处理，等同其他非 page 值 → Proxy | VM：尝试页面，CSP 阻止时回退 content | 缺少 CSP 回退逻辑 | 中 |
| `@sandbox DOM` | 仅当值严格等于 `"DOM"` 时禁止 `@require` 通过 `Reflect.set` 覆盖真实 window（`GM.js:1107-1109`） | TM：DOM 模式在隔离 world 执行，只给 DOM 访问 | 只实现了“@require 不覆盖全局”这一小部分语义；不改变注入 world | 高 |
| `@sandbox raw` / `JavaScript` | 未识别（不等于 `"DOM"` → 允许 @require 覆盖） | TM：raw/JavaScript 的不同模式语义 | 未实现 | 中 |
| `@require` 覆盖全局 | 默认允许 libLoading 时 `Reflect.set` 到真实 window（`GM.js:1107-1109`） | TM 的 sandbox 语义决定 | ChromeXt 自述行为见 `README.bak.md:55`；与 TM 默认 raw 有交叉，但不等价 | 中 |

**结论**：ChromeXt 的 `@inject-into`/`@sandbox` 是“部分实现 + 自定义 Proxy 模型”，不能按 TM/VM 的上下文语义理解。

### 9.6 元数据指令支持矩阵

| 指令 | ChromeXt 解析/行为 | 标准 | 差异 / 级别 |
|---|---|---|---|
| `@name` | Kotlin 取最后一次出现的值并删除所有 `:`（`Parser.kt:42`）；JS 侧 `meta.name` 保留原始值/数组 | 必填；可含冒号；支持 `@name:locale` | name 被改写；本地化未归一化（中） |
| `@namespace` | Kotlin 身份用缺省 `"ChromeXt"`；`GM_info.script.namespace` 来自原始元数据，缺省时可能为 `undefined`；多次出现取最后一次（`Parser.kt:25`、`Parser.kt:43`） | VM 缺省空字符串；TM 可空 | 默认值与 GM_info 可见性不同（低） |
| `@version` | 存入 `GM_info.script.version`，不参与更新比较 | 用于更新检查 | 无自动更新；仅展示（中） |
| `@description` | 原始字段；多次出现会变成数组 | 字符串；支持 i18n | 类型/本地化不同（中） |
| `@icon` / `@icon64` / `@iconURL` / `@defaulticon` | 原始字段 | 脚本图标 | 管理器 UI 未使用（低） |
| `@author` / `@copyright` / `@homepage` / `@homepageURL` / `@website` / `@source` / `@supportURL` | 原始字段 | 元数据 | 仅保存，无行为（低） |
| `@match` | 自定义匹配器（`Url.kt`） | Chrome match pattern | scheme/port 差异（中） |
| `@include` | 并入 `match` 列表，按子串/正则匹配 | glob/正则 | 子串匹配可能过宽（中） |
| `@exclude` | 参与 `matching()`；但 `GM_info.script.excludes` 缺失 | 排除规则 | 排除语义 + GM_info 字段错误（高） |
| `@exclude-match` | 未解析 | VM 支持 | 缺失（中） |
| `@run-at` | `document-start`/`document-end`/`document-idle`；idle 实际等 `load` | 四种（含 `document-body`/`context-menu`） | 无 body/context-menu；idle 偏晚（高） |
| `@grant` | `Local.kt` 按 grant 拼接 GM.js 代码块；下划线未实现项得到 console.error stub，点号未实现项可能在启动时抛 `ReferenceError`/`TypeError` | 逐 API 授权 | 部分实现/未知 `GM.*` 风险（高） |
| `@connect` | 运行时检查；同源也要 `self`；只查初始 URL | 域名白名单/确认 | 过严且可能过松（高） |
| `@require` | 异步下载；无 SRI；相对 URL 失败 | 预加载 + SRI | 竞态/完整性（高） |
| `@resource` | 运行时下载；受 `@connect` 初始化顺序影响；无 SRI；`GM_getResourceURL` 返回远程 URL | 预加载 + SRI + blob/data | 下载时机、完整性与 URL 形式均不同（高） |
| `@noframes` | 解析为 `noframes`；但 frame 默认就不注入 | 默认注入 frame，除非 `@noframes` | 默认行为相反（高） |
| `@inject-into` | `page` 或 Proxy；无真正的 `content`/`auto` | VM `page`/`content`/`auto` | 语义不同（高） |
| `@sandbox` | 仅 `DOM` 影响 `@require` 是否覆盖真实 window | TM `raw`/`JavaScript`/`DOM` | 部分实现（高） |
| `@unwrap` | 未实现 | VM 支持 | 缺失（中） |
| `@top-level-await` | 未实现；用户代码是非 async 函数体 | VM 支持 | 缺失（中） |
| `@tag` | 原始字段 | VM 支持 | 无 UI/options（低） |
| `@antifeature` | 原始字段 | TM 支持 | `GM_info.script.antifeatures` 缺失（低） |
| `@updateURL` / `@downloadURL` | 原始字段 | 自动更新 | 无更新机制（中） |
| `@webRequest` | 未实现 | TM 支持 | 缺失（中） |
| `@run-in` | 未实现 | TM 5.3+ | 缺失（低） |
| 本地化 `@name:xx` / `@description:xx` | 当作普通属性名 | i18n 归一化 | 未归一化（中） |

---

## 10. 非标准扩展（ChromeXt 自有）

以下内容不属于 Tampermonkey/Violentmonkey 标准 API，列出是为了避免混淆：

| 扩展 | 位置 | 说明 |
|---|---|---|
| `GM.ChromeXt` | `GM.js:1241-1243`、`Listener.kt:110-126` | 需 `@grant GM.ChromeXt`；暴露底层 `ChromeXt.dispatch(action, payload)`，可实现标准 API 之外的 action（notification、cookie、xmlhttpRequest、scriptStorage、installScript、syncData 等）。权限模型是“全有或全无”，与标准按 API 逐个 grant 不同。 |
| `GM_cookie.store` / `GM_cookie.export()` | `GM.js:99-144` | 暴露最近一次 `Network.getCookies` 的缓存，并可按 URL 导出 `Cookie` 头。标准没有这些方法。 |
| `GM_info.storage` | `GM.js:1271` | 直接暴露脚本 storage 对象。 |
| `GM_info.uuid` | `GM.js:1136` | 每个页面实例的随机 UUID，用于内部广播去重。 |
| `GM_info.valueListener` | `GM.js:1212` | 内部监听器数组。 |
| `@grant frames` | `Manager.kt:159-171` | 主页面脚本声明后，才为 iframe 注入脚本。标准没有此 grant。 |
| `@grant GM_info` | `Local.kt:49-52` | 被当作 no-op；`GM_info` 本来总是存在。 |
| `GM_info.script.id` | `Parser.kt:80` | `namespace:name`，并删除 name 中的冒号。标准 `GM_info` 不保证该字段。 |
| 菜单命令呈现 | `PageMenu.kt:289-371` | 用户脚本菜单被放进 Chrome 页面菜单的“用户脚本”子菜单，按脚本名分组；不是 TM 的扩展弹窗。 |
| `window.close` 实现 | `GM.js:1085-1088`、`Listener.kt:157-185` | 通过 ChromeXt dispatch 关闭当前 tab；标准是扩展 API。功能近似，但依赖当前 tab/Activity 类型。 |

> 这些扩展不应被用户脚本当作“跨管理器可移植 API”。如果脚本需要同时在 TM/VM/ChromeXt 运行，应只使用标准 API，并把 ChromeXt 扩展放在能力探测之后。

---

## 11. 高置信度缺陷与风险清单

下表只列**源码可直接定位**、且影响较大的问题。建议修复顺序按“影响面 × 修复成本”排序。

| # | 问题 | 证据 | 影响 | 建议 |
|---|---|---|---|---|
| 1 | `GM_addValueChangeListener` 回调缺少 `key`，本地删除不通知 | `GM.js:1200-1207`、`GM.js:1157-1161` | 标准监听器全部错位；本地删除事件丢失；跨标签页删除留下 `undefined` | 改为 `listener(data.key, oldValue, data.value, remote)`；删除时保留旧值并通知，同时真正删除 key |
| 2 | `GM_info.script.excludes` 因拼写错误永远为空 | `GM.js:1058` `"exlcude"` | 依赖 `excludes` 的脚本拿到 undefined | 改为 `"exclude"`；同时保留/删除原始字段的策略要明确 |
| 3 | storage 仅在 `GM.getValue`/`GM_getValue`/`GM_cookie` grant 下加载 | `Parser.kt:66-71`、`Listener.kt:261` | 只 grant `GM_setValue` 等的脚本写入不持久化 | 只要 grant 含任一 storage API（含 `GM.*` 形式）就加载 storage |
| 4 | `GM_cookie.list` 的 `params` 数组被 `optJSONObject` 丢弃 | `GM.js:149-151`、`Listener.kt:302` | `list()` 可能始终为空或作用域错误 | 把 params 作为 `{urls:[...]}` 对象传输；Kotlin 用 `optJSONObject`/`optJSONArray` 兼容两种形状 |
| 5 | 同源 `GM_xmlhttpRequest` 也要求 `@connect self` | `GM.js:485-499` | 未写 `@connect` 的常见脚本请求失败 | 同源默认允许；`@connect` 只限制跨域，或按 TM 的确认流程 |
| 6 | `redirect: "error"` / `finalUrl` / `timeout` 语义不完整 | `GM.js:690-693`、`GM.js:957-958`、`GM.js:745-751`、`XMLHttpRequest.kt:62-65` | 依赖重定向/超时的脚本行为错误 | 在 Kotlin 路径记录最终 URL；单独实现 read timeout；fetch 路径映射 timeout 为 timeout 事件 |
| 7 | 未实现的 `GM.*` grant 生成未定义引用 | `Local.kt:60-74` | `@grant GM.getTab` 等让整个脚本抛 `ReferenceError`/`TypeError` | 先检查 `localScript.containsKey(name)`，不存在时拼接 stub 或给出明确错误 |
| 8 | 默认注入上下文是 Proxy sandbox，不是页面上下文 | `GM.js:1083-1134` | 默认 `window.foo = ...` 不修改页面 | 明确默认策略；若要兼容 TM，默认使用页面上下文或提供 `@sandbox raw` 等价物 |
| 9 | iframe 默认不注入 | `Manager.kt:159-171` | 依赖 frame 的脚本默认失效 | 默认注入 frame（受 `@noframes` 控制），或至少在安装时提示需要 `@grant frames` |
| 10 | `GM_notification` 强制要求 `title` 和 `text` | `Listener.kt:218-219` | 只传 `text` 或只传 `highlight` 的标准调用失败 | Kotlin 侧对 title/text 做可选处理 |
| 11 | `GM_unregisterMenuCommand` 只禁用，重新注册同名不恢复 | `GM.js:332-335`、`GM.js:405-413` | 菜单命令注销后无法重新出现 | 注销时删除项或重新注册时置 `enabled = true` |
| 12 | `@require` 异步下载存在竞态 | `Parser.kt:76-88` | 注入时 lib 可能尚未就绪 | 在安装/加载阶段 join 下载；失败时明确报错 |
| 13 | `@resource` 预加载依赖 `strict` 初始化的顺序 | `GM.js:1272-1280`、`GM.js:17-20`、`GM.js:485-499` | 当前恰好绕过 `@connect`；若顺序变化，资源加载会被误拦 | 把资源预加载放到明确的“无 `@connect`”路径，避免依赖时序 |
| 14 | `GM_getResourceURL` 返回远程 URL | `GM.js:466-471` | 依赖 blob/data URL 的 CSP/离线场景失败 | 生成 blob/data URL 并缓存 |
| 15 | `GM_xmlhttpRequest` 的 `cookie` 污染全局 store | `XMLHttpRequest.kt:43-52` | 请求级 cookie 变成持久 cookie | 仅合并到本次请求头，不写入 `Chrome.cookieStore` |
| 16 | `GM_xmlhttpRequest` 的 `onreadystatechange` 属性不触发 | `GM.js:659-661` | 传统 XHR 风格脚本收不到状态变化 | 在 readyState setter 中调用 `onreadystatechange` |
| 17 | `@match` 的 scheme/port 语义与 Chrome 不同 | `Url.kt:31-38` | 匹配范围过宽/过窄 | 改用标准 match pattern 解析器，或至少限制 scheme 为 http/https/file 并支持任意端口 |
| 18 | `@run-at document-idle` 实际等 `load` | `GM.js:1293-1298` | 脚本启动更晚 | 改为 DOMContentLoaded 后异步调度 |
| 19 | `GM.*` 包装改变同步返回类型 | `GM.js:1215-1239` | `GM.openInTab`/`GM.registerMenuCommand`/`GM.addStyle` 等标准同步 API 变成 Promise | 对同步 API 不包 Promise；或按各管理器标准逐一实现 |
| 20 | `GM_openInTab` 固定 target 名、`setParent` 变 `_self` | `GM.js:387-402` | 连续打开复用窗口；setParent 导航当前页 | 使用唯一 target；setParent 用浏览器 API 或忽略并文档化 |
| 21 | `@grant GM.getResourceText` 不会自动补 `GM_xmlhttpRequest` | `Parser.kt:59-64` | 只 grant 点号形式的脚本拿不到 `@resource` 内容 | 自动补全检查同时覆盖 `GM.getResourceText`/`GM.getResourceUrl` |

---

## 12. 逐 API 兼容性速查表

| API | 静态结论 | 主要差异 | 级别 |
|---|---|---|---|
| `unsafeWindow` | 基本兼容（TM 口径） | VM 无需 grant；ChromeXt 需要 | 低 |
| `GM_info` | 部分兼容 | `grant`/`excludes` 错误；大量字段缺失；version 硬编码 | 高 |
| `GM_log` | 基本可用 | 前缀 + `console.info` | 低 |
| `GM_addStyle` | 基本一致 | CSP 绕过能力不同 | 低 |
| `GM_addElement` | 部分兼容 | 默认父节点固定 head；错误返回 undefined/抛错 | 中 |
| `GM_setClipboard` | 部分兼容 | 无 callback；`text/html` mimetype 走纯文本 | 中 |
| `GM_setValue` | 部分兼容 | storage 初始化条件、持久化时机 | 高 |
| `GM_getValue` | 部分兼容 | storage 初始化条件、持久化时机；跨标签页更新依赖后续请求 | 高 |
| `GM_deleteValue` | 部分兼容 | 删除不通知监听器 | 中 |
| `GM_listValues` | 部分兼容 | storage 初始化条件 | 高 |
| `GM_addValueChangeListener` | 不兼容 | 回调缺 key、本地删除不通知、跨标签页删除留下 undefined、初始化条件 | 高 |
| `GM_removeValueChangeListener` | 部分兼容 | 只禁用不删除；依赖 valueListener 初始化 | 中 |
| `GM_getResourceText` | 部分兼容 | 运行时下载、@connect、缺失资源抛 TypeError | 中 |
| `GM_getResourceURL` | 不兼容 | 返回远程 URL 而非 blob/data | 高 |
| `GM_download` | 部分兼容 | 回调/选项/成功判定/返回控制 | 高 |
| `GM_xmlhttpRequest` | 部分兼容 | @connect、redirect、timeout、finalUrl、cookie、onreadystatechange、context | 高 |
| `GM_cookie.list` | 可疑/可能不兼容 | params 数组被丢弃；字段名 expires vs expirationDate | 高 |
| `GM_cookie.set` | 部分兼容 | 缺省 domain vs url；sameSite 大小写；回调抛出 | 中 |
| `GM_cookie.delete` | 部分兼容 | 缺省 domain vs url | 中 |
| `GM_openInTab` | 不兼容 | 返回 window 而非 control；固定 target；setParent | 高 |
| `GM_registerMenuCommand` | 部分兼容 | 无 options/event；返回索引；去重规则 | 中 |
| `GM_unregisterMenuCommand` | 部分兼容 | 只禁用；不能重新注册同名 | 高 |
| `GM_notification` | 部分兼容 | title/text 强制；ondone 默认超时不触发；tag/url 缺失 | 高 |
| `window.close` | 基本兼容 | 依赖 tab/Activity | 低 |
| `window.focus` | 未实现 | — | 中 |
| `window.onurlchange` | 未实现 | — | 中 |
| `GM.*` 别名 | 部分兼容 | 未实现项可能 ReferenceError；同步 API 被 Promise 化 | 高 |

---

## 13. 复现与再检查方法

```bash
# 在仓库根目录执行

# 1. 固定代码基线
git rev-parse HEAD
git describe --tags --always --dirty
git log -1 --format='%H%n%ci%n%s'

# 2. 查看关键实现
sed -n '1030,1310p' app/src/main/assets/GM.js
sed -n '40,90p'  app/src/main/java/org/matrix/chromext/script/Local.kt
sed -n '1,110p'  app/src/main/java/org/matrix/chromext/script/Parser.kt
sed -n '130,330p' app/src/main/java/org/matrix/chromext/Listener.kt
sed -n '1,170p'  app/src/main/java/org/matrix/chromext/utils/XMLHttpRequest.kt
sed -n '1,140p'  app/src/main/java/org/matrix/chromext/utils/Url.kt

# 3. 复现元数据解析（Node.js）
#    输入包含 @match/@include/@exclude/@grant/@connect/@resource
#    预期观察：excludes 缺失、grants 代替 grant、resources 被映射
```

设备侧验证建议（本文未执行）：

1. 安装一个只 grant `GM_setValue` + `GM_listValues` 的脚本，写入后重启浏览器，确认值是否丢失（验证 5.1）。
2. 注册 `GM_addValueChangeListener` 并打印 `arguments.length` 与各参数，确认是否只有 3 个参数、删除是否触发（验证 5.2）。
3. 调用 `GM_cookie.list({url: location.href}, cb)`，确认返回的 cookie 数量和 URL 范围（验证 7.2）。
4. 在无 `@connect` 的脚本中请求同源 URL，确认是否被拒绝（验证 7.1）。
5. 使用 `@run-at document-idle`，在 `DOMContentLoaded` 与 `load` 分别打时间戳，确认脚本实际执行时机（验证 9.2）。
6. 在 iframe 中运行普通 `@match` 脚本，确认默认是否注入（验证 9.4）。
7. 在默认 sandbox 中执行 `window.__test = 1`，从页面 console 检查是否可见（验证 9.5）。

---

## 14. 参考来源

以下 URL 均在 2026-10-17 抓取或核对；页面页脚时间为 09:54–10:00（未标注时区）。

| 引用 | 来源 | 用途 |
|---|---|---|
| [TM] | https://www.tampermonkey.net/documentation.php?locale=en | Tampermonkey 文档总入口 |
| [TM-changelog] | https://www.tampermonkey.net/changelog.php | 确认最新稳定版 5.5.1（2026-10-01） |
| [TM-info] | https://www.tampermonkey.net/documentation.php?q=GM_info | `GM_info` 类型与字段 |
| [TM-addstyle] | https://www.tampermonkey.net/documentation.php?q=GM_addStyle | `GM_addStyle` 返回值 |
| [TM-addelement] | https://www.tampermonkey.net/documentation.php?q=GM_addElement | `GM_addElement` 参数/错误返回 |
| [TM-download] | https://www.tampermonkey.net/documentation.php?q=GM_download | `GM_download` 选项/回调/返回值 |
| [TM-resource] | https://www.tampermonkey.net/documentation.php?q=GM_getResource | `GM_getResourceText/URL` |
| [TM-notification] | https://www.tampermonkey.net/documentation.php?q=GM_notification | `GM_notification` 选项 |
| [TM-openintab] | https://www.tampermonkey.net/documentation.php?q=GM_openInTab | `GM_openInTab` 返回控制对象 |
| [TM-menu] | https://www.tampermonkey.net/documentation.php?q=GM_registerMenuCommand | 菜单命令 options/id |
| [TM-clipboard] | https://www.tampermonkey.net/documentation.php?q=GM_setClipboard | `GM_setClipboard` 的 info/cb |
| [TM-values] | https://www.tampermonkey.net/documentation.php?q=GM_values | storage 与 value change listener |
| [TM-xhr] | https://www.tampermonkey.net/documentation.php?q=GM_xmlhttpRequest | `GM_xmlhttpRequest` 详情 |
| [TM-cookie] | https://www.tampermonkey.net/documentation.php?q=GM_cookie | `GM_cookie` 方法与字段 |
| [TM-grant] | https://www.tampermonkey.net/documentation.php?q=grant | `@grant` 语义 |
| [TM-include] | https://www.tampermonkey.net/documentation.php?q=include | `@include/@match/@exclude` |
| [TM-runat] | https://www.tampermonkey.net/documentation.php?q=run_at | `@run-at` |
| [TM-connect] | https://www.tampermonkey.net/documentation.php?q=connect | `@connect` |
| [TM-externals] | https://www.tampermonkey.net/documentation.php?q=externals | `@require/@resource` |
| [TM-sandbox] | https://www.tampermonkey.net/documentation.php?q=sandbox | `@sandbox` 三种模式 |
| [TM-noframes] | https://www.tampermonkey.net/documentation.php?q=noframes | `@noframes` |
| [VM-gm] | https://violentmonkey.github.io/api/gm/ | `GM.*` 异步别名、`GM_getResourceURL`、`unsafeWindow` |
| [VM-meta] | https://violentmonkey.github.io/api/metadata-block/ | `@inject-into`、`@run-at`、`@noframes`、`@unwrap` |
| [GM-api] | https://wiki.greasespot.net/Greasemonkey_Manual:API | GM.* API 来源 |
| [Chrome-match] | https://developer.chrome.com/docs/extensions/develop/concepts/match-patterns | `@match` 的 scheme/host/port/path 规范 |
| [CDP-network] | https://chromedevtools.github.io/devtools-protocol/tot/Network/ | `GM_cookie` 使用的 `Network.*` 命令 |

<!-- Markdown 引用定义：使上文的 [TM]、[VM-gm] 等引用可点击 -->
[TM]: https://www.tampermonkey.net/documentation.php?locale=en
[TM-changelog]: https://www.tampermonkey.net/changelog.php
[TM-info]: https://www.tampermonkey.net/documentation.php?q=GM_info
[TM-addstyle]: https://www.tampermonkey.net/documentation.php?q=GM_addStyle
[TM-addelement]: https://www.tampermonkey.net/documentation.php?q=GM_addElement
[TM-download]: https://www.tampermonkey.net/documentation.php?q=GM_download
[TM-resource]: https://www.tampermonkey.net/documentation.php?q=GM_getResource
[TM-notification]: https://www.tampermonkey.net/documentation.php?q=GM_notification
[TM-openintab]: https://www.tampermonkey.net/documentation.php?q=GM_openInTab
[TM-menu]: https://www.tampermonkey.net/documentation.php?q=GM_registerMenuCommand
[TM-clipboard]: https://www.tampermonkey.net/documentation.php?q=GM_setClipboard
[TM-values]: https://www.tampermonkey.net/documentation.php?q=GM_values
[TM-xhr]: https://www.tampermonkey.net/documentation.php?q=GM_xmlhttpRequest
[TM-cookie]: https://www.tampermonkey.net/documentation.php?q=GM_cookie
[TM-grant]: https://www.tampermonkey.net/documentation.php?q=grant
[TM-include]: https://www.tampermonkey.net/documentation.php?q=include
[TM-runat]: https://www.tampermonkey.net/documentation.php?q=run_at
[TM-connect]: https://www.tampermonkey.net/documentation.php?q=connect
[TM-externals]: https://www.tampermonkey.net/documentation.php?q=externals
[TM-sandbox]: https://www.tampermonkey.net/documentation.php?q=sandbox
[TM-noframes]: https://www.tampermonkey.net/documentation.php?q=noframes
[VM-gm]: https://violentmonkey.github.io/api/gm/
[VM-meta]: https://violentmonkey.github.io/api/metadata-block/
[GM-api]: https://wiki.greasespot.net/Greasemonkey_Manual:API
[Chrome-match]: https://developer.chrome.com/docs/extensions/develop/concepts/match-patterns
[CDP-network]: https://chromedevtools.github.io/devtools-protocol/tot/Network/

---

## 15. 结论

- ChromeXt 已经覆盖了用户脚本最常用的一批 API（样式、storage、XHR、通知、菜单、cookie），但**“能调用”不等于“按标准语义工作”**。
- 对兼容性影响最大的不是缺失 API，而是**已有 API 的细节偏差**：`GM_addValueChangeListener` 参数、`GM_info.script.grant/excludes`、storage 初始化条件、`@connect` 同源策略、`GM_xmlhttpRequest` 的重定向/超时/finalUrl、`GM_cookie.list` 的参数传递。
- `GM.*` 别名是“机械映射 + Promise 包装”，对未实现 API 可能直接让脚本启动失败；对同步 API 会改变返回类型。
- 默认注入上下文、iframe 行为、`@run-at document-idle` 的实现也与 Tampermonkey 默认值有实质差异。
- 如果目标是提高真实脚本的兼容率，建议优先修复第 11 节的 #1–#9，并用一组来自 Greasy Fork 的常见脚本做回归测试；本文的静态结论可以节省定位时间，但不能替代设备侧验证。

---

**署名**：本文由 AI 模型 `deepseek-v4-flash` 生成。
