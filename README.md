<div align="center">

# MarkShell

<p>
  <img src="docs/logo.svg" width="120" alt="MarkShell Logo" />
</p>

### Android SSH Markdown 阅读器

通过 SSH/SFTP 连接远程服务器，浏览目录并阅读 Markdown 文件，支持文本批注、代码语法高亮、图片查看等功能。

<p>
  <img src="https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white" alt="Platform" />
  <img src="https://img.shields.io/badge/minSdk-24-00B0FF?logo=android&logoColor=white" alt="minSdk" />
  <img src="https://img.shields.io/badge/targetSdk-34-00B0FF?logo=android&logoColor=white" alt="targetSdk" />
  <img src="https://img.shields.io/badge/Java-8-ED8B00?logo=openjdk&logoColor=white" alt="Java" />
  <img src="https://img.shields.io/badge/License-MIT-blue" alt="License" />
</p>

</div>

---

## 功能

### SSH 连接管理
保存多个服务器配置，快速重连，网络切换自动恢复连接；连接分组（折叠展示/分组管理）；连接搜索（首页列表右上搜索按钮/保存的连接工具栏「搜索连接」：按别名、主机、用户名、「主机:端口」、分组实时过滤，大小写不敏感，搜索时隐藏空组、匹配计数显示）；连接复制（连接行长按菜单「复制」：以现有配置预填新建连接表单，别名自动加「（副本）」可改，另存新条目不覆盖原连接）；连接凭据 AES-256-GCM + Android Keystore 加密存储；SSH 密钥认证（私钥+可选口令）；主机指纹校验（known_hosts TOFU/变更告警，SHA-256 指纹）；本地端口转发/隧道规则按服务器隔离保存（连接行长按菜单「端口转发…」，连接列表/保存的连接列表两处入口一致）；连接保活（首页右上「设置 → 连接保活 · 心跳间隔」：5/10/30/60 秒可选并持久化，下次连接生效）；连接表单校验（新建/编辑连接与首页快速连接对话框在保存/连接前预检：主机非空、端口必须为 1-65535 否则即时表单错误提示（对标 Termius「端口值不正确」预校验）、凭据按认证方式检查；模型契约与表单预检单一语义源，杜绝「保存后连接才报错」


### 外观与设置
首页右上齿轮进入「设置」：外观·主题（六态：跟随系统/自动 22:00-06:00 深色/自动 09:00-17:00 浅色/浅色/深色/深色·纯黑，AppCompat DayNight 持久化，切换即全局生效——对标 markor pref_arrkeys__app_themes system/auto/autocompat/light/dark/dark-black 全集；暗黑适配系统、自动按时间切换、时段模式 09:00-17:00 判浅色、深色·纯黑与深色行为等价（markor 亦无独立资源差异））、连接保活·心跳间隔（5/10/30/60 秒，与 SshManager 心跳语义一致，持久化下次连接生效）、查看器·打开后跳到底部（默认关闭；开启后文本/代码/CSV 查看器全新打开即跳到文件末尾，适合尾读日志/转储——对标 markor start_on_bottom 设置；关闭恢复「按阅读进度续读」既有语义；旋转恢复态不受影响）与关于·应用信息（应用名 + Version vX.X.X (版本号) + 包名，对标 markor MoreInfoFragment 版本展示）

### 远程文件浏览
树形目录导航，按文件类型显示图标（Markdown / 代码 / 图片 / CSV），下拉刷新；文件/目录重命名、移动、复制、删除、权限设置（chmod），单文件与多选批量操作（批量删除/权限/移动/复制——批量复制经目录选择器选目标目录，目标已有同名项时单次确认「全部覆盖 / 跳过同名 / 取消」，对标 Termius SFTP 多选复制；目录源校验「不能复制到自身内部」防递归）；文件/目录属性详情（长按菜单「属性」：类型/完整路径/大小/修改时间/权限符号串与八进制——信息只读视图，目录大小不统计显示「—」）；新建文件/文件夹（工具栏更多菜单「新建」，名称校验、同名不覆盖）；复制远程路径（文件/目录长按菜单、代码/文本/CSV 查看器工具栏「复制路径」、Markdown 阅读器菜单「复制路径」——完整远端路径写入剪贴板，便于运维引用）；下载到本地（文件长按菜单「下载到本地」，系统「另存为」选择保存位置——SAF 零存储权限，默认名为远端文件名可改，流式下载大文件不占内存，完成/失败 toast 提示）；上传文件（工具栏更多菜单「上传…」，系统文件选择器选本地文件——SAF 零存储权限，上传到当前目录，自动取本地显示名，目标同名时确认「覆盖/取消」（不静默覆盖），流式上传，完成/失败 toast 提示并刷新列表）；目录书签/快捷访问（工具栏「书签」：按服务器隔离、上限 30 条自动清理；点击跳转、长按取消收藏、底部「清空全部」——书签指向目录已删除时仍可长按清除，不再滞留）；导航（返回键逐级返回上级目录，到根目录时退出；工具栏更多菜单「主目录」一键回到配置主目录，未配置则为服务器 home；工具栏更多菜单「前往路径…」：输入远端绝对路径（或相对当前目录，支持 . / .. 段）直达目录或打开文件，目标不存在/网络异常友好提示）；启动恢复上次浏览目录（未配置主目录时自动回到最后浏览的位置，按服务器隔离、目录失效自动回退主目录）；最近打开（工具栏「历史」，按服务器隔离、上限 50 条自动清理；点击经存在性校验后重新打开，文件已删除自动从历史移除，长按移除单条、底部「清空全部」）；两栏预览（大屏/折叠屏，预览工具栏「打开」可直接进入完整查看器——预览为只读子集，批注/编辑/大纲/查找/复制路径/缩放保存等全量能力经「打开」直达）；不支持类型的点击出口（应用内不可查看的类型——zip/pdf 等二进制或无扩展名非说明文件，点击/历史重开/两栏预览提示后提供「下载到本地」出口（SAF 另存后用其他应用打开），不再只弹「暂不支持此文件类型」死路提示）；文件排序（按名称/大小/修改时间，列表项显示文件大小与最后修改时间，未知时间显示「—」）；文件名称筛选（工具栏「筛选」，大小写不敏感实时过滤当前已加载列表，切换目录自动清除）；全局递归搜索（工具栏更多菜单「全局搜索…」：从当前目录按名称递归查找（大小写不敏感），可选最大深度（3/5/10/无限制，默认 3），自动忽略 .git/node_modules/__pycache__ 等噪音目录、跳过符号链接目录防循环，结果按相对路径列出并显示文件大小（目录恒前、目录带「（目录）」标注、目录不显示大小），点目录结果直接跳转、点文件结果直接打开；不可读目录自动跳过不中断；搜索全程可取消——进行中常驻提示条带「取消」按钮即时中止（对标 markor），退出页面自动取消以防长搜占用队列，取消不展示已发现的部分结果）；大文件打开确认（>4MB 的文件经列表/历史/全局搜索/前往路径任一入口打开前确认「文件较大（N MB）——完全加载可能卡顿」，避免无提示全量加载造成卡顿/内存压力——四个入口均传导 stat/ls 真实大小判定，path-only 入口不再绕过护栏）

### Markdown 渲染
基于 Markwon 引擎，支持标题、列表、表格、任务列表、代码块（**带语言标注的围栏代码块经 Prism4j 语法高亮——代码查看器同引擎，语言：HTML/CSS/JS/TS/Java/Python/JSON/C/C++/C#/Go/Kotlin/SQL/YAML/Bash/Shell 及常见别名（js/py/yml/c++/sh/zsh/shell 等），背景沿用 `markdown_code_bg`**）、图片（**内嵌图片按相对/绝对路径经 SFTP 加载 + LRU 缓存，EXIF 方向自动修正**，http(s)/data 图片走引擎内置加载），双指缩放调节字体大小；任务清单 checkbox 阅读态点击翻转并写回服务器；**GFM 补齐：`~~删除线~~` 渲染删除样式、裸 URL/邮箱自动成链（带 scheme 的 URL 与邮箱，`www.` 裸域不链=commonmark-ext-autolink 0.13.0 行为偏差实证落档；复用链接点击分发=外部 URL/邮箱系统打开）**；大纲（TOC）导航（抽屉「批注/大纲」双 Tab，点击跳转）；页内链接点击（外部 URL 打开浏览器/邮件，相对链接按当前文件目录解析打开对应远端文件，`#锚点` 跳转标题）；文档内文本查找（工具栏「查找」，大小写不敏感，上一处/下一处循环跳转 + 计数，跳转落点与批注/大纲导航共用高亮；**编辑模式同样提供「查找/替换」：编辑态工具栏「查找/替换」，替换当前处/全部替换，文件编辑流闭环**）；**内容分享（阅读态/编辑态菜单「分享」：分享 Markdown 原文——编辑态含未保存草稿，与 markor shareText=getTextString 对齐；超大自动降级文件流分享**）；阅读进度记忆（旋转之外新增跨会话「续读」：关闭再打开/换文件再回来自动恢复上次阅读位置，按服务器+文件路径隔离存储、上限 100 条自清理、文件变短自动截断兜底——与批次旋转恢复同一滚动口径；代码/文本/CSV 查看器同型支持）；**重新加载（工具栏更多菜单：重读服务器端最新内容并重新渲染——批注/大纲/任务清单随新内容协同重建（批注定位失败=抽屉「未找到原文」，与初次打开同语义），保持当前滚动位置；失败保留旧内容仅提示——与文本/代码/CSV/图片查看器同语义**）

### 文本批注
选中文本添加批注，黄色虚线下划线标记被批注文本，右侧抽屉管理批注列表，点击快速定位，条目内编辑/删除（**编辑批注内容：对话框预填+全选、保存写回 CSV——原文定位保持不变**）；批注连续导航（上一处/下一处）；批注导出/分享（纯文本/HTML/Markdown 三种格式）；批注与 Markdown 编辑模式协同（编辑后失效批注自动标记）；长文批注定位采用 Aho–Corasick 一次扫描（O(M+命中数)）

### Markdown 远程在线编辑
阅读/编辑单 Activity 切换，编辑保存写回 SFTP；草稿随界面恢复；无变更保存直接退出；编辑模式内置「查找/替换」（大小写不敏感，替换当前处或全部，跳转选区定位）+ 编辑格式工具栏（加粗/斜体/引用/标题 H1-H3/无序列表/有序列表/任务清单/删除线/行内代码：选中文字包裹切换（删除线 ~~、行内代码 ` 与加粗斜体同款），标题/列表/引用/任务按行级前缀切换（任务清单仅翻转未勾选↔已勾选态），与 markor 行为对齐；有序列表切换后自动重编号 1..N（含嵌套子列表独立编号，markor AutoTextFormatter.renumberOrderedList 对齐）+ 编辑撤销/重做（格式栏左端撤销/重做按钮：连续打字合并为一步、误操作保存前可逐步回退，对标 markor 编辑历史栈）；回车自动续行（列表/缩进行按回车自动续前缀与缩进：有序列表编号递增、任务清单新项未勾选、无序列表保留原符号、代码块缩进保留——markor AutoTextFormatter 对齐；引用/标题不续）

### 代码查看器
基于 Prism4j 语法高亮引擎，支持 JS / TS / Java / Python / JSON / CSS / HTML / XML / SVG / C / C++ / Bash / Shell / SQL / Go / Kotlin / YAML 等语言（**Shell 脚本 .sh/.bash/.zsh 与代码族 .sql/.go/.kt/.yaml/.yml 经此处打开获语法高亮+行号+查找**），行号显示，双指缩放（**字号持久化——每次进入/换文件恢复上次缩放字号，与 Markdown 阅读器调节字体一致**）；**自动换行开关（工具栏更多菜单「自动换行」勾选项：开启=长行自动换行、行号槽自绘按逻辑行对齐（长行换行不错位），关闭=横向滚动（默认，代码查看惯例）；设置全局记忆——对标 markor Wrap words 菜单勾选；切换时保持当前纵向阅读位置**）；阅读进度记忆（关闭重开自动恢复上次阅读位置，与 Markdown 阅读器同型）；文档内查找（工具栏「查找」，大小写不敏感、上一处/下一处循环跳转 + 计数，命中词黄色高亮并平滑滚动定位到所在行）；「转到行号…」（工具栏更多菜单：输入行号（1-总行数）按逻辑行跳转，目标整行黄色高亮、纵向居中定位，越界输入跳至文件末尾）；**重新加载（工具栏更多菜单：重读服务器端最新内容并重渲染，保持当前滚动位置；失败保留旧内容仅提示**）；**内容分享（工具栏「分享」：全文经系统分享面板直发文本（markor shareText 对齐）；超大文本（>512KB）自动降级为文件流分享避免 Binder 崩溃**）

### 纯文本查看器
等宽字体渲染与长行自动换行；行号显示（行号槽按内容版式基线绘制，长行换行时行号仍与逻辑行行首对齐；双指缩放行号同步刷新，**字号持久化——每次进入/换文件恢复上次缩放字号，与 Markdown 阅读器调节字体一致**）；阅读进度记忆（关闭重开自动恢复上次阅读位置，与 Markdown 阅读器同型）；文档内查找（工具栏「查找」，大小写不敏感、上一处/下一处循环跳转 + 计数，命中词黄色高亮并平滑滚动定位到所在行）；「转到行号…」（工具栏更多菜单：输入行号（1-总行数）按逻辑行跳转，目标整行黄色高亮、纵向居中定位，越界输入跳至文件末尾）；**重新加载（工具栏更多菜单：重读服务器端最新内容并重渲染，保持当前滚动位置；失败保留旧内容仅提示——与文件浏览下拉刷新同语义**）；**内容分享（工具栏「分享」：全文经系统分享面板直发，超大文本自动降级文件流分享**）；支持类型扩充（**运维高频文本/配置/说明文件点击即开：.conf/.properties/.ini/.toml/.env 等配置类扩展名 + 无扩展名 README/Dockerfile/Makefile/LICENSE 特判——对标 markor 纯文本 fallback，不再「暂不支持此文件类型」；无对应高亮语法者按纯文本打开（自动换行），C/C++ 类有 clike 高亮走代码查看器**；**Shell 脚本（.sh/.bash/.zsh）与代码族（.sql/.go/.kt/.yaml/.yml）已升级走代码查看器获语法高亮**；**dot 前缀文件特判：.bashrc/.zshrc/.profile 等 shell 启动文件走代码查看器获 bash 语法高亮（与 .sh 同判据），.gitignore/.gitconfig/.npmrc/.editorconfig/.env.local 等配置/清单文件按纯文本打开**）

### 图片查看器
双指缩放拖拽，弹性回弹边界，长按或工具栏「保存图片」保存到相册（**原图直存：保原分辨率/EXIF 元数据/原格式与体积、零重编码，文件名/内容/MIME 三方一致**——显示走降采样解码、保存写原始字节）；工具栏「复制路径」（复制当前图片完整远端路径）；**内容分享（工具栏「分享图片」：原字节经 FileProvider 直发，零重编码零重采样**）；**EXIF 方向自动修正（竖拍照片按要求方向显示，与 Markdown 内嵌图/两栏预览同一处理，失败静默回退）**；**重新加载（工具栏更多菜单：重读服务器端最新内容并重解码，视图重置为适应大小；失败保留旧图仅提示**）

### CSV 查看器
表格化渲染，支持表头识别；单元格内容可长按选中复制（与文本/代码/阅读器查看器一致性补全，同平台文本选择机制）；**双指缩放调节字号（与文本/代码查看器共用持久化字号——每次进入/换文件恢复上次缩放值，默认 13sp 起，标注/表格小字可放大可读**）；阅读进度记忆（关闭重开自动恢复上次阅读位置，与 Markdown 阅读器同型）；文档内查找（工具栏「查找」，按单元格内容匹配、大小写不敏感、上一处/下一处循环 + 计数，命中单元格高亮并平滑滚动定位到所在行——文本/纯文本查看器同样提供文档内查找）；**「转到行号…」（工具栏更多菜单：输入行号（1-总行数，含表头行）按显示行跳转，目标行首单元格黄色高亮、纵向居中定位，越界输入跳至末行——文本/代码查看器同型）**；**内容分享（工具栏「分享」：原始 CSV 文本直发，超大自动降级文件流分享**）；**重新加载（工具栏更多菜单：重读服务器端最新内容并重建表格，保持当前滚动位置；失败保留旧表格仅提示**）

## 界面预览

<div align="center">
<table>
<tr>
<td align="center"><img src="docs/screen_main.svg" width="200" /><br/>首页 · 连接列表</td>
<td align="center"><img src="docs/screen_file_browser.svg" width="200" /><br/>文件浏览 · 树形目录</td>
</tr>
<tr>
<td align="center"><img src="docs/screen_markdown_reader.svg" width="200" /><br/>Markdown 阅读器 · 批注高亮</td>
<td align="center"><img src="docs/screen_code_viewer.svg" width="200" /><br/>代码查看器 · 语法高亮</td>
</tr>
</table>
</div>

## 技术栈

| 库 | 版本 | 用途 |
|---|---|---|
| [JSch](http://www.jcraft.com/jsch/) | 0.1.55 | SSH/SFTP 连接 |
| [Markwon](https://github.com/noties/markwon) | 4.6.2 | Markdown 渲染 |
| [Prism4j](https://github.com/noties/prism4j) | 2.0.0 | 代码语法高亮 |
| [Material Design](https://material.io/develop/android) | 1.11.0 | UI 组件 |
| [AndroidX](https://developer.android.com/jetpack/androidx) | — | 基础库 |

## 项目结构

```
app/src/main/java/com/ssh/mdreader/
├── ui/               # Activity
│   ├── MainActivity              # 首页（连接列表 + 快速连接）
│   ├── ConnectionActivity        # 新建/编辑连接
│   ├── SavedConnectionsActivity  # 保存的连接（分组管理/端口转发入口）
│   ├── FileBrowserActivity       # 远程文件浏览（书签/多选/两栏预览）
│   ├── MarkdownReaderActivity    # Markdown 阅读 + 批注 + 编辑 + 大纲 + 链接
│   ├── CodeViewerActivity        # 代码查看器
│   ├── ImageViewerActivity       # 图片查看器
│   ├── CsvReaderActivity         # CSV 阅读器
│   ├── TextViewerActivity        # 纯文本查看器
│   └── BaseActivity              # 基类
├── ssh/              # SSH 管理器（连接/断连/读写/重连/密钥/指纹/转发）
├── model/            # 数据模型（SshConfig / RemoteFile / AnnotationEntry / PortForwardRule）
├── adapter/          # RecyclerView 适配器（TreeAdapter / AnnotationListAdapter / TocAdapter）
├── util/             # 工具层（AnnotationHelper / Overlay / TocHelper / TaskCheckboxHelper /
│                     #   LinkTargetHelper / FindHelper / ReplaceHelper / CodeHighlighter / PreferenceManager 等，多数纯函数可 JVM 测）
├── widget/           # 自定义 View（SwipeRevealLayout）
└── ui/span/          # 自定义 Span（AnnotationSpan）
```

## 批注系统

批注采用**渲染后文本搜索 + 出现序号**方案：

```
用户选中文本 → 记录 occurrenceIndex → 写入 CSV → 渲染后按序号 setSpan
```

| 特性 | 说明 |
|---|---|
| Markdown 源文件零修改 | 批注只存 CSV，不向 Markdown 插入任何标签 |
| 重复文本消歧 | `occurrenceIndex` 记录被批注文本是第几次出现（0-based） |
| CSV 格式 | `"id","批注内容","原文片段","出现序号"` |
| 渲染流程 | Markwon 正常渲染 → `findNthOccurrence()` 定位 → `setSpan()` |

## 构建

```bash
# 需要 JDK 17 和 Android SDK
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export ANDROID_HOME=~/Library/Android/sdk
cd ssh-md-reader && gradle assembleDebug
```

## 系统要求

- Android 7.0 (API 24) 及以上
- targetSdk 34

## License

[MIT](LICENSE)
