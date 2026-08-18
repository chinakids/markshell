# MarkShell

Android SSH Markdown 阅读器，通过 SSH/SFTP 连接远程服务器，浏览目录并阅读 Markdown 文件，支持文本批注、代码语法高亮、图片查看等功能。

## 功能

- **SSH 连接管理** — 保存多个服务器配置，快速重连，网络切换自动恢复
- **远程文件浏览** — 树形目录导航，按类型显示图标，下拉刷新
- **Markdown 渲染** — 标题/列表/表格/任务列表/代码块/图片，双指缩放调节字体
- **文本批注** — 选中文本添加批注，黄色虚线下划线标记，右侧抽屉管理列表，点击定位
- **代码查看器** — Prism4j 语法高亮（JS/TS/Java/Python/JSON/CSS/HTML/XML 等），行号显示，双指缩放
- **图片查看器** — 双指缩放拖拽，弹性回弹，长按保存到相册
- **CSV 查看器** — 表格化渲染

## 技术栈

| 库 | 用途 |
|---|---|
| JSch 0.1.55 | SSH/SFTP 连接 |
| Markwon 4.6.2 | Markdown 渲染 |
| Prism4j 2.0.0 | 代码语法高亮 |
| Material Design | UI 组件 |
| AndroidX | 基础库 |

## 项目结构

```
app/src/main/java/com/ssh/mdreader/
├── ui/           # Activity
│   ├── MainActivity           # 首页（连接列表 + 快速连接）
│   ├── ConnectionActivity     # 新建/编辑连接
│   ├── FileBrowserActivity    # 远程文件浏览
│   ├── MarkdownReaderActivity  # Markdown 阅读 + 批注
│   ├── CodeViewerActivity     # 代码查看器
│   ├── ImageViewerActivity    # 图片查看器
│   ├── CsvReaderActivity      # CSV 阅读器
│   └── BaseActivity          # 基类
├── ssh/          # SSH 管理器
├── model/        # 数据模型
├── adapter/      # RecyclerView 适配器
├── util/         # 工具类
├── widget/       # 自定义 View
└── ui/span/      # 自定义 Span
```

## 批注系统

批注采用**渲染后文本搜索 + 出现序号**方案：

- Markdown 源文件零修改
- 批注元数据存储在同名 `_批注.csv` 文件中
- CSV 格式：`"id","批注内容","原文片段","出现序号"`
- `occurrenceIndex` 记录被批注文本在渲染后文本中是第几次出现（0-based），消除重复文本歧义
- 渲染流程：Markwon 正常渲染 → 在 Spanned 文本中按序号查找 → setSpan

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
