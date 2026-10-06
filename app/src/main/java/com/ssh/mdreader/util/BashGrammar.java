package com.ssh.mdreader.util;

import io.noties.prism4j.Prism4j;

import java.util.regex.Pattern;

import static io.noties.prism4j.Prism4j.grammar;
import static io.noties.prism4j.Prism4j.pattern;
import static io.noties.prism4j.Prism4j.token;

/**
 * Custom Bash/Shell grammar（能力发现循环第 50 轮）。
 *
 * <p><b>背景（判据③/⑤）</b>：prism4j-bundler 2.0.0 模板清单.jar 内实证<b>无</b>
 * bash/shell 模板（{@code Prism_*.java} 清单无 bash）——{@code ```bash/```sh/```shell}
 * 是运维文档/README 代码块最高频标注，却查不到 grammar=原文直显（迭代93 高亮功能
 * 落地后唯一明显覆盖缺口）；本类=参照 {@link TypeScriptGrammar} 先例手写 grammar。
 *
 * <p><b>来源（官方 Prism JS 组件，逐 token 忠实移植）</b>：
 * {@code github.com/PrismJS/prism components/prism-bash.js}（master，2026-10-06 抓取，
 * 235 行）——token 名/别名/顺序（shebang/comment/function-name/for-or-select/assign-left/
 * parameter/string/environment/variable/function/keyword/builtin/boolean/file-descriptor/
 * operator/punctuation/number）与正则逐条一致；Prism 官方同文件末尾
 * {@code Prism.languages.sh = Prism.languages.shell = Prism.languages.bash}，
 * 别名（sh/shell/zsh→bash）由 {@link CustomGrammarLocator} 归一化承接。
 *
 * <p><b>适配说明（如实记录）</b>：a) PrismJS 的「command substitution 内嵌完整 bash
 * grammar」循环引用在 Prism4j 不可直接表达（Grammar 不可变、无自我引用）——inside
 * 按官方结构保留高频子集（字符串 inside=environment/variable/entity；命令替换
 * inside=variable 标记；算术/花括号展开=operator/punctuation/number），嵌套极端场景
 * 为近似，常见脚本着色不受影响；b) heredoc 用 Java 正则
 * 反向引用 {@code \2/\3} 表达结束标记，与 JS 一致；c) {@code \$\{...\}} 花括号展开的
 * inside 仅保留 operator/punctuation（environment 常量识别并入顶层 environment token）。
 */
public final class BashGrammar {

    private BashGrammar() {
    }

    /** bash 环境变量白名单（官方 envVars 全量复制，单词边界匹配）。 */
    private static final String ENVVARS =
            "\\b(?:BASH|BASHOPTS|BASH_ALIASES|BASH_ARGC|BASH_ARGV|BASH_CMDS|"
            + "BASH_COMPLETION_COMPAT_DIR|BASH_LINENO|BASH_REMATCH|BASH_SOURCE|"
            + "BASH_VERSINFO|BASH_VERSION|COLORTERM|COLUMNS|COMP_WORDBREAKS|"
            + "DBUS_SESSION_BUS_ADDRESS|DEFAULTS_PATH|DESKTOP_SESSION|DIRSTACK|"
            + "DISPLAY|EUID|GDMSESSION|GDM_LANG|GNOME_KEYRING_CONTROL|"
            + "GNOME_KEYRING_PID|GPG_AGENT_INFO|GROUPS|HISTCONTROL|HISTFILE|"
            + "HISTFILESIZE|HISTSIZE|HOME|HOSTNAME|HOSTTYPE|IFS|INSTANCE|JOB|"
            + "LANG|LANGUAGE|LC_ADDRESS|LC_ALL|LC_IDENTIFICATION|LC_MEASUREMENT|"
            + "LC_MONETARY|LC_NAME|LC_NUMERIC|LC_PAPER|LC_TELEPHONE|LC_TIME|"
            + "LESSCLOSE|LESSOPEN|LINES|LOGNAME|LS_COLORS|MACHTYPE|MAILCHECK|"
            + "MANDATORY_PATH|NO_AT_BRIDGE|OLDPWD|OPTERR|OPTIND|ORBIT_SOCKETDIR|"
            + "OSTYPE|PAPERSIZE|PATH|PIPESTATUS|PPID|PS1|PS2|PS3|PS4|PWD|RANDOM|"
            + "REPLY|SECONDS|SELINUX_INIT|SESSION|SESSIONTYPE|SESSION_MANAGER|"
            + "SHELL|SHELLOPTS|SHLVL|SSH_AUTH_SOCK|TERM|UID|UPSTART_EVENTS|"
            + "UPSTART_INSTANCE|UPSTART_JOB|UPSTART_SESSION|USER|WINDOWID|"
            + "XAUTHORITY|XDG_CONFIG_DIRS|XDG_CURRENT_DESKTOP|XDG_DATA_DIRS|"
            + "XDG_GREETER_DATA_DIR|XDG_MENU_PREFIX|XDG_RUNTIME_DIR|XDG_SEAT|"
            + "XDG_SEAT_PATH|XDG_SESSION_DESKTOP|XDG_SESSION_ID|XDG_SESSION_PATH|"
            + "XDG_SESSION_TYPE|XDG_VTNR|XMODIFIERS)\\b";

    /** 常用命令名白名单（官方 function 清单全量复制）。 */
    private static final String COMMANDS =
            "(?:add|apropos|apt|apt-cache|apt-get|aptitude|aspell|automysqlbackup|"
            + "awk|basename|bash|bc|bconsole|bg|bzip2|cal|cargo|cat|cfdisk|chgrp|"
            + "chkconfig|chmod|chown|chroot|cksum|clear|cmp|column|comm|composer|"
            + "cp|cron|crontab|csplit|curl|cut|date|dc|dd|ddrescue|debootstrap|df|"
            + "diff|diff3|dig|dir|dircolors|dirname|dirs|dmesg|docker|docker-compose|"
            + "du|egrep|eject|env|ethtool|expand|expect|expr|fdformat|fdisk|fg|"
            + "fgrep|file|find|fmt|fold|format|free|fsck|ftp|fuser|gawk|git|"
            + "gparted|grep|groupadd|groupdel|groupmod|groups|grub-mkconfig|gzip|"
            + "halt|head|hg|history|host|hostname|htop|iconv|id|ifconfig|ifdown|"
            + "ifup|import|install|ip|java|jobs|join|kill|killall|less|link|ln|"
            + "locate|logname|logrotate|look|lpc|lpr|lprint|lprintd|lprintq|lprm|"
            + "ls|lsof|lynx|make|man|mc|mdadm|mkconfig|mkdir|mke2fs|mkfifo|mkfs|"
            + "mkisofs|mknod|mkswap|mmv|more|most|mount|mtools|mtr|mutt|mv|nano|"
            + "nc|netstat|nice|nl|node|nohup|notify-send|npm|nslookup|op|open|"
            + "parted|passwd|paste|pathchk|ping|pkill|pnpm|podman|podman-compose|"
            + "popd|pr|printcap|printenv|ps|pushd|pv|quota|quotacheck|quotactl|"
            + "ram|rar|rcp|reboot|remsync|rename|renice|rev|rm|rmdir|rpm|rsync|"
            + "scp|screen|sdiff|sed|sendmail|seq|service|sftp|sh|shellcheck|shuf|"
            + "shutdown|sleep|slocate|sort|split|ssh|stat|strace|su|sudo|sum|"
            + "suspend|swapon|sync|sysctl|tac|tail|tar|tee|time|timeout|top|touch|"
            + "tr|traceroute|tsort|tty|umount|uname|unexpand|uniq|units|unrar|"
            + "unshar|unzip|update-grub|uptime|useradd|userdel|usermod|users|"
            + "uudecode|uuencode|v|vcpkg|vdir|vi|vim|virsh|vmstat|wait|watch|wc|"
            + "wget|whereis|which|who|whoami|write|xargs|xdg-open|yarn|yes|zenity|"
            + "zip|zsh|zypper)(?=$|[)\\s;|&])";

    public static Prism4j.Grammar create(Prism4j prism4j) {
        // ── 共享子 token（单例构建，供多层 inside 复用）────────────────────────

        final Prism4j.Pattern entity = pattern(Pattern.compile(
                "\\\\(?:[abceEfnrtv\\\\\"]|O?[0-7]{1,3}|U[0-9a-fA-F]{8}|"
                + "u[0-9a-fA-F]{4}|x[0-9a-fA-F]{1,2})"));

        final Prism4j.Token environment = token("environment", pattern(Pattern.compile(
                "\\$?" + ENVVARS), false, false, "constant"));

        final Prism4j.Token variable = token("variable",
                // [0] 算术环境 $(( ... ))
                pattern(Pattern.compile("\\$?\\(\\([\\s\\S]+?\\)\\)"), false, true, null,
                        grammar("inside",
                                token("variable",
                                        pattern(Pattern.compile("(^\\$\\(\\([\\s\\S]+)\\)\\)"), true),
                                        pattern(Pattern.compile("^\\$\\(\\("))),
                                token("number", pattern(Pattern.compile(
                                        "\\b0x[\\dA-Fa-f]+\\b|(?:\\b\\d+(?:\\.\\d*)?|\\B\\.\\d+)(?:[Ee]-?\\d+)?"))),
                                token("operator", pattern(Pattern.compile(
                                        "--|\\+\\+|\\*\\*=?|<<=?|>>=?|&&|\\|\\||[=!+\\-*/%<>^&|]=?|[?~:]"))),
                                token("punctuation", pattern(Pattern.compile("\\(\\(?|\\)\\)?|,|;")))
                        )),
                // [1] 命令替换 $( ... ) 与 ` ... `
                pattern(Pattern.compile("\\$\\((?:\\([^)]+\\)|[^()])+\\)|`[^`]+`"), false, true, null,
                        grammar("inside",
                                token("variable", pattern(Pattern.compile("^\\$\\(|^`|\\)$|`$"))))),
                // [2] 花括号展开 ${ ... }
                pattern(Pattern.compile("\\$\\{[^}]+\\}"), false, true, null,
                        grammar("inside",
                                token("operator", pattern(Pattern.compile(":[-=?+]?|[!\\/]|##?|%%?|\\^\\^?|,,?"))),
                                token("punctuation", pattern(Pattern.compile("[\\[\\]]"))))),
                // [3] 简单变量 $var / $# / $? 等
                pattern(Pattern.compile("\\$(?:\\w+|[#?*!@$])")));

        final Prism4j.Token comment = token("comment",
                pattern(Pattern.compile("(^|[^\"{\\\\$])#.*"), true));

        final Prism4j.Token functionName = token("function-name",
                pattern(Pattern.compile("(\\bfunction\\s+)[\\w-]+(?=(?:\\s*\\(?:\\s*\\))?\\s*\\{)"),
                        true, false, "function"),
                pattern(Pattern.compile("\\b[\\w-]+(?=\\s*\\(\\s*\\)\\s*\\{)"), false, false, "function"));

        final Prism4j.Token forOrSelect = token("for-or-select",
                pattern(Pattern.compile("(\\b(?:for|select)\\s+)\\w+(?=\\s+in\\s)"),
                        true, false, "variable"));

        final Prism4j.Token assignLeft = token("assign-left",
                pattern(Pattern.compile("(^|[\\s;|&]|[<>]\\()\\w+(?:\\.\\w+)*(?=\\+?=)"),
                        true, false, "variable",
                        grammar("inside",
                                token("environment", pattern(Pattern.compile("(^|[\\s;|&]|[<>]\\()" + ENVVARS),
                                        true, false, "constant")))));

        final Prism4j.Token parameter = token("parameter",
                pattern(Pattern.compile("(^|\\s)-{1,2}(?:\\w+:[+-]?)?\\w+(?:\\.\\w+)*(?=[=\\s]|$)"),
                        true, false, "variable"));

        final Prism4j.Token string = token("string",
                // heredoc（未加引号定界符：可展开 → 带 inside）
                pattern(Pattern.compile("((?:^|[^<])<<-?\\s*)(\\w+)\\s[\\s\\S]*?(?:\\r?\\n|\\r)\\2"),
                        true, true, null,
                        grammar("inside", environment, variable)),
                // heredoc（加引号定界符：不展开）
                pattern(Pattern.compile("((?:^|[^<])<<-?\\s*)([\"'])(\\w+)\\2\\s[\\s\\S]*?(?:\\r?\\n|\\r)\\3"),
                        true, true, null,
                        grammar("inside", environment, variable)),
                // 双引号字符串（可展开 → 带 inside）
                pattern(Pattern.compile("(^|[^\\\\](?:\\\\\\\\)*)\"(?:\\\\[\\s\\S]|\\$\\([^)]+\\)|\\$(?!\\()|`[^`]+`|[^\"\\\\`$])*\""),
                        true, true, null,
                        grammar("inside", environment, variable,
                                token("entity", entity))),
                // 单引号字符串（不展开）
                pattern(Pattern.compile("(^|[^$\\\\])'[^']*'"), true, true, null),
                // ANSI-C $'...'（仅实体转义）
                pattern(Pattern.compile("\\$'(?:[^'\\\\]|\\\\[\\s\\S])*'"), false, true, null,
                        grammar("inside", token("entity", entity))));

        final Prism4j.Token function = token("function",
                pattern(Pattern.compile("(^|[\\s;|&]|[<>]\\()" + COMMANDS), true));

        final Prism4j.Token keyword = token("keyword",
                pattern(Pattern.compile("(^|[\\s;|&]|[<>]\\()(?:case|do|done|elif|else|esac|fi|for|function|if|in|select|then|until|while)(?=$|[)\\s;|&])"),
                        true));

        final Prism4j.Token builtin = token("builtin",
                pattern(Pattern.compile(
                        "(^|[\\s;|&]|[<>]\\()(?:\\.|:|alias|bind|break|builtin|caller|cd|command|"
                        + "continue|declare|echo|enable|eval|exec|exit|export|getopts|hash|help|"
                        + "let|local|logout|mapfile|printf|pwd|read|readarray|readonly|return|set|"
                        + "shift|shopt|source|test|times|trap|type|typeset|ulimit|umask|unalias|"
                        + "unset)(?=$|[)\\s;|&])"),
                        true, false, "class-name"));

        final Prism4j.Token bool = token("boolean",
                pattern(Pattern.compile("(^|[\\s;|&]|[<>]\\()(?:false|true)(?=$|[)\\s;|&])"), true));

        final Prism4j.Token fileDescriptor = token("file-descriptor",
                pattern(Pattern.compile("\\B&\\d\\b"), false, false, "important"));

        final Prism4j.Token operator = token("operator",
                pattern(Pattern.compile("\\d?<>|>\\||\\+=|=[=~]?|!=?|<<[<-]?|[&\\d]?>>|\\d[<>]&?|[<>][&=]?|&[>&]?|\\|[&|]?"),
                        false, false, null,
                        grammar("inside",
                                token("file-descriptor", pattern(Pattern.compile("^\\d"), false, false, "important")))));

        final Prism4j.Token punctuation = token("punctuation",
                pattern(Pattern.compile("\\$?\\(\\(?|\\)\\)?|\\.\\.|[{}\\[\\];\\\\]")));

        final Prism4j.Token number = token("number",
                pattern(Pattern.compile("(^|\\s)(?:[1-9]\\d*|0)(?:[.,]\\d+)?\\b"), true));

        // 命令替换 $(...) / `...` 的 inside 仅保留官方 variable 标记 token
        // （官方另会把全量 bash 复制进去，Prism4j 不可变 Grammar 无法循环引用，
        // 已文档化为近似——嵌套命令内部的其他 token 不另行着色）。

        final Prism4j.Grammar bash = grammar("bash",
                token("shebang", pattern(Pattern.compile("^#!\\s*/.*"), false, false, "important")),
                comment,
                functionName,
                forOrSelect,
                assignLeft,
                parameter,
                string,
                environment,
                variable,
                function,
                keyword,
                builtin,
                bool,
                fileDescriptor,
                operator,
                punctuation,
                number);

        // localStorage: 官方在文件尾部把 bash 复制给 sh/shell；本项目别名归一化
        // （CustomGrammarLocator.normalizeLanguage）负责，此处仅返回 bash 本体。
        return bash;
    }
}
