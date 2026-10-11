#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从 lint-results-debug.txt 提取各 Warning 的位置与首行，按类型分组输出。"""
import re, collections

path = "app/build/reports/lint-results-debug.txt"
with open(path, encoding="utf-8") as f:
    lines = f.readlines()

groups = collections.OrderedDict()
cur = None
for line in lines:
    m = re.match(r'^(/[^:]+):(\d+): Warning: (.*?) \[([A-Za-z]+)\]', line)
    if m:
        cur = m.group(4)
        groups.setdefault(cur, []).append((m.group(1), m.group(2), m.group(3)))
    elif line.strip() == "":
        cur = None

for typ, items in groups.items():
    print(f"\n===== [{typ}] x{len(items)} =====")
    for f, ln, msg in items:
        print(f"  {f.replace('/Users/chinakids/Desktop/markshell/', '')}:{ln} :: {msg[:110]}")
