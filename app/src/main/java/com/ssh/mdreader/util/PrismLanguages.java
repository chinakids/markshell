package com.ssh.mdreader.util;

import io.noties.prism4j.annotations.PrismBundle;

/**
 * Registration point for the Prism4j annotation-processor bundler.
 * The bundler reads this annotation and generates:
 *   - Grammar classes for each included language
 *   - A GrammarLocator class ({@code com.ssh.mdreader.util.PrismGrammarLocator})
 *     that maps language names to their grammar factories.
 *
 * <p>第 49 轮起扩展（Markdown 代码块高亮，见 {@link MdCodeHighlightPlugin}）：
 * 在原有 7 种上追加 c/cpp/csharp/go/kotlin/sql/yaml（prism4j-bundler 2.0.0
 * 模板清单内、运维/笔记文档代码块高频；cpp/csharp/kotlin 以 clike 为基础）；
 * 未含 bash/shell（bundler 无模板，自定义 grammar 留待后续候选）；
 * markdown/makefile/git 等低频暂不加入（APK 体积与收益权衡，如实裁剪）。
 */
@PrismBundle(
    include = {
        "markup",       // HTML, XML, SVG
        "css",          // CSS
        "clike",        // C-like base (required by java)
        "java",         // Java
        "javascript",   // JavaScript (also used as fallback for JSX)
        "json",         // JSON
        "python",       // Python
        "c",            // C（基于 clike）
        "cpp",          // C++（基于 clike）
        "csharp",       // C#（基于 clike）
        "go",           // Go
        "kotlin",       // Kotlin（基于 clike）
        "sql",          // SQL
        "yaml"          // YAML
    },
    grammarLocatorClassName = ".PrismGrammarLocator"
)
public class PrismLanguages {}
