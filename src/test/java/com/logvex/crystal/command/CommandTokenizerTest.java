package com.logvex.crystal.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers the command tokenizer, which is deliberately Minecraft-free and unit testable. */
class CommandTokenizerTest {

    @Test
    void splitsOnWhitespace() {
        assertEquals(List.of("theme", "set", "preset"), CommandTokenizer.tokenize("theme set preset"));
        assertEquals(List.of("theme"), CommandTokenizer.tokenize("   theme   "));
    }

    @Test
    void keepsQuotedValuesTogether() {
        assertEquals(List.of("bind", "say hi there"),
                CommandTokenizer.tokenize("bind \"say hi there\""));
        // Quotes are stripped from the token.
        assertEquals(List.of("a b"), CommandTokenizer.tokenize("\"a b\""));
    }

    @Test
    void emptyAndNullInputsYieldNoTokens() {
        assertTrue(CommandTokenizer.tokenize(null).isEmpty());
        assertTrue(CommandTokenizer.tokenize("").isEmpty());
        assertTrue(CommandTokenizer.tokenize("   ").isEmpty());
    }

    @Test
    void unterminatedQuoteConsumesRestOfLine() {
        assertEquals(List.of("bind", "unfinished value"),
                CommandTokenizer.tokenize("bind \"unfinished value"));
    }

    @Test
    void consecutiveSpacesCollapse() {
        assertEquals(List.of("a", "b"), CommandTokenizer.tokenize("a    b"));
    }
}
