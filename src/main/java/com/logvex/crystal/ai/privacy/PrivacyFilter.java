package com.logvex.crystal.ai.privacy;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Redacts everything privacy-sensitive out of text that may be shown or transmitted (stack
 * traces, log excerpts, environment details, AI input/output).
 *
 * <p>This is the single choke point for anything leaving the machine. It is deliberately
 * conservative: it would rather over-redact a harmless string than leak a token. Every rule
 * is a compiled {@link Pattern} applied in a fixed order, so it is deterministic and unit
 * testable without a game.
 *
 * <p>Nothing here is best-effort on failure: if a pattern were to misbehave the whole report
 * path must fail closed, so callers treat a {@link RuntimeException} as "do not send".
 */
public final class PrivacyFilter {

    /** Placeholder for a redacted token. */
    public static final String REDACTED = "[redacted]";

    /** A redaction rule: a pattern, a replacement and a human-readable label. */
    public record Rule(String label, Pattern pattern, String replacement) {
    }

    private final List<Rule> rules;

    /** The default rule set. Order matters (more specific rules first). */
    public PrivacyFilter() {
        this(defaultRules());
    }

    public PrivacyFilter(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    public List<Rule> rules() {
        return rules;
    }

    /** Applies every rule to {@code input}, returning the redacted text. */
    public String filter(String input) {
        if (input == null || input.isEmpty()) {
            return input == null ? "" : input;
        }
        String result = input;
        for (Rule rule : rules) {
            result = rule.pattern().matcher(result).replaceAll(rule.replacement());
        }
        return result;
    }

    /** Convenience: redacts a list of lines and drops any that became empty. */
    public List<String> filterLines(List<String> lines) {
        List<String> out = new ArrayList<>();
        if (lines == null) {
            return out;
        }
        for (String line : lines) {
            String filtered = filter(line);
            if (!filtered.isBlank()) {
                out.add(filtered);
            }
        }
        return out;
    }

    /** True when {@code text} still contains something the filter would change. */
    public boolean containsSensitive(String text) {
        return text != null && !filter(text).equals(text);
    }

    /**
     * The ordered default rules. Kept as one place so the privacy doc, the tests and the
     * implementation cannot drift apart.
     */
    public static List<Rule> defaultRules() {
        List<Rule> rules = new ArrayList<>();

        // --- credentials and tokens (before generic "key=value" rules) ---
        rules.add(rule("bearer", "(?i)\\bbearer\\s+[A-Za-z0-9._\\-]+", "Bearer " + REDACTED));
        rules.add(rule("basic-auth", "(?i)\\bbasic\\s+[A-Za-z0-9+/=]{8,}", "Basic " + REDACTED));
        rules.add(rule("jwt", "\\beyJ[A-Za-z0-9_\\-]{5,}\\.[A-Za-z0-9_\\-]{5,}\\.[A-Za-z0-9_\\-]{5,}", REDACTED));
        rules.add(rule("github-token", "\\bgh[pousr]_[A-Za-z0-9]{16,}", REDACTED));
        rules.add(rule("slack-token", "\\bxox[baprs]-[A-Za-z0-9-]{10,}", REDACTED));
        rules.add(rule("aws-key", "\\b(?:AKIA|ASIA)[0-9A-Z]{16}\\b", REDACTED));
        rules.add(rule("private-key", "-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----", REDACTED));
        rules.add(rule("session-cookie", "(?i)\\b(?:session|sessionid|access_token|refresh_token|auth|authorization|token|password|passwd|pwd|secret|api[_-]?key)\\s*[=:]\\s*\"?[^\\s\"'&,;]+", REDACTED));
        rules.add(rule("discord-webhook", "https?://(?:canary\\.|ptb\\.)?discord(?:app)?\\.com/api/webhooks/\\d+/[A-Za-z0-9._\\-]+", REDACTED));

        // --- network endpoints ---
        rules.add(rule("ipv4", "\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b", REDACTED));
        // The trailing (?![\w.]) stops the rule from eating Java package prefixes such as
        // "com.logvex" or "org.apache.commons.io" (the TLD must not be followed by a dot).
        rules.add(rule("mc-server",
                "(?i)\\b(?:[a-z0-9_\\-]+\\.)+(?:net|com|org|io|gg|me|xyz|dev|club|fun|online|site|shop|store|tv|uk|de|fr|nl|ru|cc|to)(?::\\d{2,5})?(?![\\w.])",
                REDACTED));

        // --- personal paths ---
        rules.add(rule("win-user-path", "(?i)[A-Z]:\\\\Users\\\\[^\\\\\\s\"']+", "C:\\\\Users\\\\" + REDACTED));
        rules.add(rule("win-home-path", "(?i)[A-Z]:\\\\Documents and Settings\\\\[^\\\\\\s\"']+", REDACTED));
        rules.add(rule("unix-home-path", "/(?:home|Users)/[A-Za-z0-9._\\-]+", "/" + REDACTED));
        rules.add(rule("mac-home-path", "/Users/[A-Za-z0-9._\\-]+", REDACTED));

        // --- coordinates (waypoints, positions) ---
        rules.add(rule("coords", "(?i)\\b(?:x|y|z)\\s*[=:]\\s*-?\\d+(?:\\.\\d+)?", "coord=" + REDACTED));
        rules.add(rule("position-triple", "\\(\\s*-?\\d+(?:\\.\\d+)?,\\s*-?\\d+(?:\\.\\d+)?,\\s*-?\\d+(?:\\.\\d+)?\\s*\\)", "(" + REDACTED + ")"));
        rules.add(rule("blockpos", "(?i)\\bblockpos\\{[^}]*}", "BlockPos{" + REDACTED + "}"));

        return List.copyOf(rules);
    }

    private static Rule rule(String label, String regex, String replacement) {
        return new Rule(label, Pattern.compile(regex), replacement);
    }

    /**
     * Redacts player/account names that the caller has explicitly supplied (from the local
     * friends list, the session, or a configured username). This is separate from the regex
     * rules because a name is only sensitive when we know what it is.
     */
    public String redactKnownNames(String text, Iterable<String> names) {
        if (text == null || text.isEmpty() || names == null) {
            return text == null ? "" : text;
        }
        String result = text;
        for (String name : names) {
            if (name == null || name.length() < 3) {
                continue;
            }
            result = result.replaceAll("(?<!\\w)" + Pattern.quote(name) + "(?!\\w)", REDACTED);
        }
        return result;
    }

    /** Applies the rules to one string, also masking any explicitly known names. */
    public String filter(String text, Iterable<String> knownNames) {
        return redactKnownNames(filter(text), knownNames);
    }

    /** Utility used by tests: the number of substitutions a rule made on {@code text}. */
    static int countMatches(Rule rule, String text) {
        Matcher matcher = rule.pattern().matcher(text == null ? "" : text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
