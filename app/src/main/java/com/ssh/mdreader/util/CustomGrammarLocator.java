package com.ssh.mdreader.util;

import io.noties.prism4j.GrammarLocator;
import io.noties.prism4j.Prism4j;

import java.util.HashSet;
import java.util.Set;

/**
 * Wrapper GrammarLocator that delegates to the generated PrismGrammarLocator
 * for most languages, but provides custom TypeScript grammar.
 */
public class CustomGrammarLocator implements GrammarLocator {

    private final PrismGrammarLocator generatedLocator;

    public CustomGrammarLocator() {
        this.generatedLocator = new PrismGrammarLocator();
    }

    @Override
    public Prism4j.Grammar grammar(Prism4j prism4j, String language) {
        // Handle TypeScript with custom grammar
        if ("typescript".equals(language) || "ts".equals(language) || "tsx".equals(language)) {
            return TypeScriptGrammar.create(prism4j);
        }

        // Delegate to generated locator for all other languages
        return generatedLocator.grammar(prism4j, language);
    }

    @Override
    public Set<String> languages() {
        Set<String> langs = new HashSet<>(generatedLocator.languages());
        langs.add("typescript");
        langs.add("ts");
        langs.add("tsx");
        return langs;
    }
}
