package com.logvex.crystal.ai.privacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Redaction rules: each category from the Phase 7 privacy spec is exercised directly. */
class PrivacyFilterTest {

    private final PrivacyFilter filter = new PrivacyFilter();

    @Test
    void redactsBearerTokens() {
        String out = filter.filter("Authorization: Bearer abcDEF12345._-xyz");
        assertTrue(out.contains(PrivacyFilter.REDACTED));
        assertFalse(out.contains("abcDEF12345"));
    }

    @Test
    void redactsKeyValueSecrets() {
        for (String secret : List.of("token=abc123", "password: hunter2", "api_key=deadbeef",
                "access_token=xyz", "secret=topsecret")) {
            String out = filter.filter("config line: " + secret);
            assertFalse(out.contains(secret.split("[=:]")[1].trim()),
                    "should redact value in: " + secret);
        }
    }

    @Test
    void redactsJwtAndKnownTokenShapes() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        assertFalse(filter.filter("jwt " + jwt).contains(jwt));
        assertFalse(filter.filter("token ghp_0123456789abcdefABCDEF").contains("ghp_0123456789abcdefABCDEF"));
        assertFalse(filter.filter("slack xoxb-1234567890-abcdef").contains("xoxb-1234567890-abcdef"));
    }

    @Test
    void redactsDiscordWebhookUrls() {
        String url = "https://discord.com/api/webhooks/123456789012345678/abcdefGHIJKLmnop";
        String out = filter.filter("send to " + url);
        assertFalse(out.contains("abcdefGHIJKLmnop"));
    }

    @Test
    void redactsServerAddressesAndIps() {
        assertFalse(filter.filter("connecting to mc.hypixel.net:25565").contains("hypixel"));
        assertFalse(filter.filter("server at 203.0.113.7").contains("203.0.113.7"));
    }

    @Test
    void doesNotEatJavaPackageNames() {
        // The domain rule must not treat a package prefix as a hostname.
        String out = filter.filter("at org.apache.commons.io.IOUtils.copy(IOUtils.java:1)");
        assertTrue(out.contains("org.apache.commons.io"), "package names must survive: " + out);
        String client = filter.filter("at com.logvex.crystal.module.Module.tick(Module.java:1)");
        assertTrue(client.contains("com.logvex.crystal"), "client package must survive: " + client);
    }

    @Test
    void redactsHomeDirectories() {
        assertFalse(filter.filter("C:\\Users\\matt\\.minecraft\\logs").contains("matt"));
        assertFalse(filter.filter("/home/matt/.minecraft/logs").contains("matt"));
        assertFalse(filter.filter("/Users/matt/Library").contains("matt"));
    }

    @Test
    void redactsCoordinatesAndBlockPositions() {
        assertFalse(filter.filter("waypoint x=1234 z=-567").contains("1234"));
        assertFalse(filter.filter("goal BlockPos{BlockPos{x=10, y=64, z=-20}}").contains("64"));
        assertFalse(filter.filter("at position (12.5, 64.0, -30.25)").contains("64.0"));
    }

    @Test
    void redactsKnownNamesOnlyWhenSupplied() {
        String text = "Player Steve joined, and Alex is near";
        String without = filter.filter(text);
        assertTrue(without.contains("Steve"), "without a name list nothing is assumed");
        String with = filter.filter(text, List.of("Steve", "Alex"));
        assertFalse(with.contains("Steve"));
        assertFalse(with.contains("Alex"));
    }

    @Test
    void shortNamesAreNotRedactedToAvoidSubstringDamage() {
        String out = filter.redactKnownNames("abc def ghi", List.of("ab"));
        assertEquals("abc def ghi", out);
    }

    @Test
    void filteringIsIdempotentOnAlreadyCleanText() {
        String clean = "at com.logvex.crystal.module.Module.tick(Module.java:42)";
        assertEquals(clean, filter.filter(clean));
    }
}
