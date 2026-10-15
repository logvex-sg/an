package com.logvex.crystal.command;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a command line into tokens, honouring double quotes so values containing spaces
 * survive. Kept free of any Minecraft dependency so it can be unit tested.
 */
public final class CommandTokenizer {

    private CommandTokenizer() {
    }

    /**
     * Tokenizes {@code input}. Quotes are stripped; an unterminated quote consumes the
     * rest of the line. Empty tokens are dropped.
     */
    public static List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        if (input == null) {
            return tokens;
        }
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean hasToken = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                hasToken = true;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (hasToken) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    hasToken = false;
                }
            } else {
                current.append(c);
                hasToken = true;
            }
        }
        if (hasToken) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    /** Joins tokens back into a single string, re-quoting any token that contains spaces. */
    public static String join(List<String> tokens) {
        StringBuilder builder = new StringBuilder();
        for (String token : tokens) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            if (token.contains(" ")) {
                builder.append('"').append(token).append('"');
            } else {
                builder.append(token);
            }
        }
        return builder.toString();
    }
}
