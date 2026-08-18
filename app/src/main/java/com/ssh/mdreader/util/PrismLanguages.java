package com.ssh.mdreader.util;

import io.noties.prism4j.annotations.PrismBundle;

/**
 * Registration point for the Prism4j annotation-processor bundler.
 * The bundler reads this annotation and generates:
 *   - Grammar classes for each included language
 *   - A GrammarLocator class ({@code com.ssh.mdreader.util.PrismGrammarLocator})
 *     that maps language names to their grammar factories.
 */
@PrismBundle(
    include = {
        "markup",       // HTML, XML, SVG
        "css",          // CSS
        "clike",        // C-like base (required by java)
        "java",         // Java
        "javascript",   // JavaScript (also used as fallback for JSX)
        "json",         // JSON
        "python"        // Python
    },
    grammarLocatorClassName = ".PrismGrammarLocator"
)
public class PrismLanguages {}
