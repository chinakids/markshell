# -*- coding: utf-8 -*-
path = '/Users/chinakids/Desktop/markshell/docs/推进器日志.md'
entry = '''- 2026-10-04 （迭代62，feat 本卡提交已推送）：**能力发现循环第十八轮——扫 GitHub（0 issue/0 PR）+基线复核（assembleDebug+testDebugUnitTest：BUILD SUCCESSFUL、666/666 全绿零失败（40 测试类）REAL_EXIT=0）+主项#27 查看器「转到行号」实现+走查小修=Markdown 阅读器补「复制路径」，21 新单测 666→687/687 全绿**。先扫 GitHub：gh issue list/pr list 均空（0/0）；工作区干净（上轮 247af49 迭代61 feat，非并发）；基线复核 `./gradlew assembleDebug testDebugUnitTest --rerun-tasks` BUILD SUCCESSFUL（666/666 全绿零失败、REAL_EXIT=0、40 测试类、test-results XML 计数核对）。**① 全仓走查**：命中 a) Text/Code 查看器行号列（迭代59）无「跳转到行号」入口——报错栈「第 N 行」只能手动滚动，长行数文本无法绕过（#27，既有路线图落档 2.0 最高剩余）；b) MainActivity 连接列表无搜索/过滤（#26 待办）；c) 连接无「复制」动作（#28 待办）；d) 文件无「下载到本地」（#29 待办）；e) 新增候选=Markdown 阅读器阅读态菜单无「复制路径」（三查看器+FileBrowser 长按均有=同组能力不一致，走查小修直接修）；ImageViewer 无复制路径=观察项（图片场景低频且有替代）。**② 竞品调研**（#27）：markor showGoToLineDialog 实现源码实证（MarkorDialogFactory.java：输入框 searchInputType=TYPE_CLASS_NUMBER（仅数字）、lineNumber<1→1、callback=TextViewUtils.getIndexFromLineOffset(text, lineNumber-1, 0) 取行首→getLineStart clamp→setSelectionAndShow（requestFocus+showSelection+post setSelection）；getIndexFromLineOffset 越界=循环结束返回 length()=跳往文本末尾非 clamp 到末行——行为语义全部对拍）。**③ 实现 #27**：GoToLineHelper 纯函数层（parseLineNumber：null/空白/非法（含 int 溢出）→-1、<1→1=markor 同型；lineStartOffset：1-based 逻辑行首偏移、越界→text.length()=markor 同型、null/<=1→0；lineHighlightRange：[start,end) 行尾去尾换行、空行/越界=空区间；零 Android 依赖 21 单测）+Text/Code 查看器「转到行号…」overflow 菜单项（MENU_GOTO_LINE_ID=0xA2003，showAsAction=never 文字项=与高级动作样式一致零新 drawable）+showGoToLineDialog（DialogHelper.showInputDialog inputType=TYPE_CLASS_NUMBER+hint 动态「1-N」（LineNumberHelper.countLines 单一语义源）；文本空→toast「内容尚未加载完成」）+jumpToLine（lineHighlightRange→BackgroundColorSpan 0x66FFD600 全行高亮（SpannableStringBuilder 拷贝保留既有 Prism spans）+UiUtils.scrollToOffsetCenter 居中）+Code 查看器额外 horizontalScrollView.smoothScrollTo(0,0) 横向回行首列=整行可见。**④ 走查小修**：MarkdownReader 阅读态菜单补「复制路径」（MENU_COPY_PATH_ID=0xA1018、showAsAction=never、UiUtils.copyRemotePath 单封装=迭代55 单一语义源）。**⑤ 验证**：新单测首测 2 处失败=测试期望心算错（"line1\\nline2\\nline3" length=17 非 18）+1 处编译错（InputType 包=android.text 非 android.view.inputmethod）——按教训⑯a 先读 failure got 定位责任方，实现侧零改；全量 assembleDebug+testDebugUnitTest --rerun-tasks BUILD SUCCESSFUL、687/687 全绿零失败、REAL_EXIT=0、APK 产出。README 同步（代码查看器/纯文本查看器「转到行号…」+Markdown 阅读器复制路径）。第十六轮候选排序落档：#27 2.0 本轮实现>#28 连接复制 1.5>#29 远端文件下载 1.4>#26 连接列表搜索 1.33（#14 配置备份维持观察；#30 阅读器复制路径 2.0 本轮走查小修已实现）；真机项入待验㉟、教训㊳）。
'''
with open(path, encoding='utf-8') as f:
    s = f.read()
anchor = '- 2026-10-04 （迭代61，feat 本卡提交已推送）'
i = s.find(anchor)
assert i != -1, 'anchor not found'
entry = entry.replace('\\\\n', '\\n')  # 让"line1\\nline2"以字面 \n 显示——直接保留
s2 = s[:i] + entry + '\n' + s[i:]
with open(path, 'w', encoding='utf-8') as f:
    f.write(s2)
print('inserted, new len', len(s2))
# 验证
with open(path, encoding='utf-8') as f:
    s3 = f.read()
print('iter62 in log:', '迭代62' in s3)
print('㉟ count:', s3.count('㉟'), '㊳ count:', s3.count('㊳'))
