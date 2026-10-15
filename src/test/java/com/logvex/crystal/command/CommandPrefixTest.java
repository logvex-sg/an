package com.logvex.crystal.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Covers the chat prefix stripping that decides whether a line is a Crystal command. The
 * semicolon is the primary prefix; the legacy {@code .lvx} / {@code .crystal} spellings
 * stay accepted so older scripts keep working.
 */
class CommandPrefixTest {

    @Test
    void semicolonIsThePrimaryPrefix() {
        assertEquals("toggle killaura", CommandManager.stripPrefix(";toggle killaura"));
        assertEquals("help", CommandManager.stripPrefix("; help"));
        assertEquals("", CommandManager.stripPrefix(";"));
        assertEquals("", CommandManager.stripPrefix(";   "));
    }

    @Test
    void legacyPrefixesStillResolve() {
        assertEquals("help", CommandManager.stripPrefix(".lvx help"));
        assertEquals("help", CommandManager.stripPrefix(".crystal help"));
        assertEquals("", CommandManager.stripPrefix(".lvx"));
    }

    @Test
    void ordinaryChatIsNotACommand() {
        assertNull(CommandManager.stripPrefix("hello world"));
        assertNull(CommandManager.stripPrefix(""));
        // A longer word that merely starts with a legacy prefix is not a command.
        assertNull(CommandManager.stripPrefix(".crystals are nice"));
        assertNull(CommandManager.stripPrefix(".lvxfoo"));
    }
}
