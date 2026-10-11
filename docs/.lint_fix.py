#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""迭代111：lint Warning 走查续——批量修正（XML 部分）。
每项 (文件, old, new, 描述)；old 需唯一；替换成功计数并打印。"""
import os, sys

BASE = "app/src/main/res"
ATTR = '    android:importantForAutofill="no"\n'

edits = []
def add(path, old, new, desc):
    edits.append((path, old, new, desc))

# ── A. themes.xml: 显式 windowBackground（为删除 activity 根背景兜底同色）──
add("values/themes.xml",
    '        <item name="colorOnPrimary">@color/md_theme_on_primary</item>',
    '        <item name="colorOnPrimary">@color/md_theme_on_primary</item>\n        <item name="android:windowBackground">@color/md_theme_background</item>',
    "themes: windowBackground")

# ── B. 7 个 activity 根背景删除（与 windowBackground 同色，消 Overdraw）──
for f, bgline in [
    ("layout/activity_connection.xml", '    android:background="@color/md_theme_background">'),
    ("layout/activity_csv_reader.xml", '    android:background="@color/md_theme_background">'),
    ("layout/activity_file_browser.xml", '    android:background="@color/md_theme_background">'),
    ("layout-w600dp/activity_file_browser.xml", '    android:background="@color/md_theme_background">'),
    ("layout/activity_image_viewer.xml", '    android:background="#121212">'),
    ("layout/activity_main.xml", '    android:background="@color/md_theme_background">'),
    ("layout/activity_saved_connections.xml", '    android:background="@color/md_theme_background">'),
]:
    add(f, '    android:background="' + ('#121212' if '#121212' in bgline else '@color/md_theme_background') + '">\n    ',
        '', f"root bg 删除 {f}")
    # 说明：上面的通用 old 会匹配多处？根背景唯一即可。改用精确行号方式下面的专用编辑。

# 上面通用写法有歧义风险，改用手工精确 old（含前一属性行确保唯一）——重建 edits
edits = []

# ── A ──
add("values/themes.xml",
    '        <item name="colorOnPrimary">@color/md_theme_on_primary</item>',
    '        <item name="colorOnPrimary">@color/md_theme_on_primary</item>\n        <item name="android:windowBackground">@color/md_theme_background</item>',
    "themes: windowBackground")

# ── B：各文件根元素（都读 line2 标签+line7 背景；用前一行 + 背景行组合）──
add("layout/activity_connection.xml",
    '    android:layout_height="match_parent"\n    android:background="@color/md_theme_background">',
    '    android:layout_height="match_parent">',
    "root bg")
add("layout/activity_csv_reader.xml",
    '    android:layout_height="match_parent"\n    android:background="@color/md_theme_background">',
    '    android:layout_height="match_parent">',
    "root bg")
add("layout/activity_file_browser.xml",
    '    android:layout_height="match_parent"\n    android:background="@color/md_theme_background">',
    '    android:layout_height="match_parent">',
    "root bg")
add("layout-w600dp/activity_file_browser.xml",
    '    android:layout_height="match_parent"\n    android:background="@color/md_theme_background">',
    '    android:layout_height="match_parent">',
    "root bg")
add("layout/activity_image_viewer.xml",
    '    android:layout_height="match_parent"\n    android:background="#121212">',
    '    android:layout_height="match_parent">',
    "root bg")
add("layout/activity_main.xml",
    '    android:layout_height="match_parent"\n    android:background="@color/md_theme_background">',
    '    android:layout_height="match_parent">',
    "root bg")
add("layout/activity_saved_connections.xml",
    '    android:layout_height="match_parent"\n    android:background="@color/md_theme_background">',
    '    android:layout_height="match_parent">',
    "root bg")

# ── C. Autofill ×14：EditText 后插 importantForAutofill="no"（缩进同级）──
def edittext(path, anchor, desc):
    add(path, anchor, anchor.replace(">", '\n' + '                        android:importantForAutofill="no">') if False else None, desc) if False else None

def ettb(path, tag_line, id_line, desc):
    """在 <EditText 行后（保持缩进）插入重要属性行。tag_line 是含缩进的 '<EditText' 文本。"""
    indent = tag_line[:len(tag_line) - len(tag_line.lstrip())]
    add(path, tag_line, tag_line + '\n' + indent + 'android:importantForAutofill="no"', desc)

ettb("layout/activity_file_browser.xml", '                    <EditText', None, "autofill et_filter")
ettb("layout-w600dp/activity_file_browser.xml", '                    <EditText', None, "autofill et_filter")
ettb("layout/activity_main.xml", '            <EditText', None, "autofill et_conn_search")
ettb("layout/activity_saved_connections.xml", '            <EditText', None, "autofill et_conn_search")
ettb("layout/find_bar_viewer.xml", '    <EditText', None, "autofill viewer_find")
ettb("layout/activity_markdown_reader.xml", '                        <EditText\n                        android:id="@+id/et_find_query"',
     None, "autofill et_find_query")
ettb("layout/activity_markdown_reader.xml", '                        <EditText\n                        android:id="@+id/et_replace_query"',
     None, "autofill et_replace_query")
ettb("layout/activity_markdown_reader.xml", '        <EditText\n            android:id="@+id/et_editor"',
     None, "autofill et_editor")
ettb("layout/dialog_input.xml", '    <EditText', None, "autofill dialog_input_et")
ettb("layout/dialog_port_forward.xml", '    <EditText\n        android:id="@+id/dialog_pf_name"', None, "autofill pf_name")
ettb("layout/dialog_port_forward.xml", '    <EditText\n        android:id="@+id/dialog_pf_local_port"', None, "autofill pf_local_port")
ettb("layout/dialog_port_forward.xml", '    <EditText\n        android:id="@+id/dialog_pf_remote_host"', None, "autofill pf_remote_host")
ettb("layout/dialog_port_forward.xml", '    <EditText\n        android:id="@+id/dialog_pf_remote_port"', None, "autofill pf_remote_port")
ettb("layout/dialog_port_forward.xml", '    <EditText\n        android:id="@+id/dialog_pf_bind"', None, "autofill pf_bind")

# ── D. dialog_input LabelFor：标题 TextView 加 labelFor ──
add("layout/dialog_input.xml",
    '        android:textStyle="bold"\n        android:layout_marginBottom="16dp" />',
    '        android:textStyle="bold"\n        android:labelFor="@+id/dialog_input_et"\n        android:layout_marginBottom="16dp" />',
    "labelFor title")

# ── E. UselessParent：item_home_header 合并外层 ──
add("layout/item_home_header.xml",
    '''<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingTop="48dp"
    android:paddingStart="20dp"
    android:paddingEnd="20dp"
    android:paddingBottom="12dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical">
''',
    '''<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:paddingTop="48dp"
    android:paddingStart="20dp"
    android:paddingEnd="20dp"
    android:paddingBottom="12dp">
''',
    "item_home_header 合并")

# ── E2. loading_overlay：FrameLayout 根 → LinearLayout 根（删中间层）──
add("layout/loading_overlay.xml",
    '''<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/loading_overlay"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#CC000000"
    android:clickable="true"
    android:focusable="true"
    android:visibility="gone"
    android:elevation="24dp">

    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:orientation="vertical"
        android:gravity="center_horizontal">
''',
    '''<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/loading_overlay"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#CC000000"
    android:clickable="true"
    android:focusable="true"
    android:visibility="gone"
    android:elevation="24dp"
    android:orientation="vertical"
    android:gravity="center">
''',
    "loading_overlay 根改 LinearLayout")

add("layout/loading_overlay.xml",
    '''    </LinearLayout>

</FrameLayout>''',
    '''</LinearLayout>''',
    "loading_overlay 收尾")
# 修正：上面的收尾 old 会匹配第一个 </LinearLayout> 之后的（中间层闭 + 根闭）；实际结构：... </LinearLayout>\n</FrameLayout> 唯一。OK。

# ── E3. w600dp：删 layout_preview_empty 中间 LinearLayout ──
add("layout-w600dp/activity_file_browser.xml",
    '''        <!-- 空状态 -->
        <LinearLayout
            android:id="@+id/layout_preview_empty"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:orientation="vertical"
            android:gravity="center">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/preview_empty_hint"
                android:textColor="#666666"
                android:textSize="16sp" />

        </LinearLayout>
''',
    '''        <!-- 空状态 -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="center"
            android:text="@string/preview_empty_hint"
            android:textColor="#666666"
            android:textSize="16sp" />
''',
    "w600dp 删空态中间层")

# ── F. MergeRootFrame：activity_text_viewer 根 FrameLayout → merge ──
add("layout/activity_text_viewer.xml",
    '''<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <LinearLayout''',
    '''<merge xmlns:android="http://schemas.android.com/apk/res/android">

    <LinearLayout''',
    "text_viewer merge 开")
add("layout/activity_text_viewer.xml",
    '''    <include layout="@layout/loading_overlay" />

</FrameLayout>''',
    '''    <include layout="@layout/loading_overlay" />

</merge>''',
    "text_viewer merge 闭")

# ── G. AlwaysShowAction：selection 菜单 always→ifRoom（与主菜单一致）──
add("menu/menu_file_browser_selection.xml",
    '        app:showAsAction="always" />\n\n    <item\n        android:id="@+id/action_select_all"\n        android:title="全选"\n        app:showAsAction="always" />',
    '        app:showAsAction="ifRoom" />\n\n    <item\n        android:id="@+id/action_select_all"\n        android:title="全选"\n        app:showAsAction="ifRoom" />',
    "selection menu ifRoom")

# ── H. TypographyDashes：端口范围用 en dash ──
add("values/strings.xml",
    '<string name="error_invalid_port">端口需在 1-65535 之间</string>',
    '<string name="error_invalid_port">端口需在 1\u201365535 之间</string>',
    "en dash 端口范围")

# ── I. SmallSp：角标 10sp→11sp ──
add("layout/item_annotation_list.xml",
    '                    android:textSize="10sp"',
    '                    android:textSize="11sp"',
    "角标 11sp")

# ── J. MonochromeLauncherIcon ×2 ──
add("mipmap-anydpi-v26/ic_launcher.xml",
    '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n</adaptive-icon>',
    '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n    <monochrome android:drawable="@drawable/ic_launcher_foreground" />\n</adaptive-icon>',
    "monochrome icon")
add("mipmap-anydpi-v26/ic_launcher_round.xml",
    '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n</adaptive-icon>',
    '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n    <monochrome android:drawable="@drawable/ic_launcher_foreground" />\n</adaptive-icon>',
    "monochrome icon round")

# ── K. InconsistentLayout：w600dp 根 tools:ignore（两栏仅宽屏有意）──
add("layout-w600dp/activity_file_browser.xml",
    '''<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"''',
    '''<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    tools:ignore="InconsistentLayout"
    android:layout_width="match_parent"''',
    "inconsistent ignore")

# ── L. UseCompoundDrawables：popup_annotation 根 tools:ignore（删除按钮有独立交互）──
add("layout/popup_annotation.xml",
    '''<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="wrap_content"''',
    '''<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    tools:ignore="UseCompoundDrawables"
    android:layout_width="wrap_content"''',
    "popup ignore compound")

# ── M. VectorPath：ic_settings 根 tools:ignore（官方材料图标）──
add("drawable/ic_settings.xml",
    '''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"''',
    '''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    tools:ignore="VectorPath"
    android:width="24dp"''',
    "settings ignore vector")

# ── 执行 ──
ok = fail = 0
for path, old, new, desc in edits:
    if old is None:
        print(f"  [SKIP-wrong] {path}: {desc}"); fail += 1; continue
    p = os.path.join(BASE, path)
    with open(p, encoding="utf-8") as f:
        text = f.read()
    if text.count(old) != 1:
        print(f"  [WARN count={text.count(old)}] {path}: {desc}")
        fail += 1
        continue
    text = text.replace(old, new)
    with open(p, "w", encoding="utf-8") as f:
        f.write(text)
    print(f"  [OK] {path}: {desc}")
    ok += 1
print(f"\n替换成功 {ok}/{ok+fail}")
