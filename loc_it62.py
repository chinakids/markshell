with open('/Users/chinakids/Desktop/markshell/docs/推进器日志.md', encoding='utf-8') as f:
    lines = f.readlines()
for i in range(246, 256):
    print(i+1, repr(lines[i][:120]))
print('=== 249 head 400 ===')
print(repr(lines[248][:400]))
