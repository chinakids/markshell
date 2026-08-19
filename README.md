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
保存多个服务器配置，快速重连，网络切换自动恢复连接

### 远程文件浏览
树形目录导航，按文件类型显示图标（Markdown / 代码 / 图片 / CSV），下拉刷新

### Markdown 渲染
基于 Markwon 引擎，支持标题、列表、表格、任务列表、代码块、图片，双指缩放调节字体大小

### 文本批注
选中文本添加批注，黄色虚线下划线标记被批注文本，右侧抽屉管理批注列表，点击快速定位

### 代码查看器
基于 Prism4j 语法高亮引擎，支持 JS / TS / Java / Python / JSON / CSS / HTML / XML 等 10+ 种语言，行号显示，双指缩放

### 图片查看器
双指缩放拖拽，弹性回弹边界，长按保存到相册

### CSV 查看器
表格化渲染，支持表头识别

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
│   ├── FileBrowserActivity       # 远程文件浏览
│   ├── MarkdownReaderActivity    # Markdown 阅读 + 批注
│   ├── CodeViewerActivity        # 代码查看器
│   ├── ImageViewerActivity       # 图片查看器
│   ├── CsvReaderActivity         # CSV 阅读器
│   └── BaseActivity              # 基类
├── ssh/              # SSH 管理器（连接/断连/读写/重连）
├── model/            # 数据模型（SshConfig / RemoteFile / AnnotationEntry）
├── adapter/          # RecyclerView 适配器（TreeAdapter / AnnotationListAdapter）
├── util/             # 工具类（AnnotationHelper / CodeHighlighter / DialogHelper 等）
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
