package com.logvex.crystal.integration.baritone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logvex.crystal.integration.baritone.BaritoneBridge.Availability;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Behavioural tests for {@link BaritoneService}, driven through a fake {@link BaritoneBridge}.
 *
 * <p>No Baritone jar and no Minecraft are involved: the bridge is the seam, so the service's
 * probe handling, goal issuing, completion detection and error reporting are all exercised
 * against real code paths. The only calls the service makes that need Minecraft
 * ({@code tick}, {@code goToCurrentPosition}) are the ones these tests deliberately avoid;
 * everything else runs as it does in the game.
 */
class BaritoneServiceTest {

    /** A configurable in-memory bridge that records what the service asked it to do. */
    private static final class FakeBridge implements BaritoneBridge {
        Availability probeResult = BaritoneBridge.Availability.ok("1.10");
        BaritoneStatus status = idleStatus();
        RuntimeException statusError;
        final List<String> goals = new ArrayList<>();
        final Map<String, String> settings = new java.util.LinkedHashMap<>();
        int cancelCount;
        int pauseCount;
        int resumeCount;
        boolean pauseAccepted = true;
        boolean resumeAccepted = true;
        String commandForResult;

        @Override
        public Availability probe() {
            return probeResult;
        }

        @Override
        public BaritoneStatus status() {
            if (statusError != null) {
                throw statusError;
            }
            return status;
        }

        @Override
        public void gotoBlock(int x, int y, int z) {
            goals.add("block " + x + " " + y + " " + z);
        }

        @Override
        public void gotoGoal(BaritoneGoal.Kind kind, int x, int y, int z) {
            goals.add(kind + " " + x + " " + y + " " + z);
        }

        @Override
        public void follow(BaritoneGoal.Kind kind, int x, int y, int z, int range) {
            goals.add(kind + " " + x + " " + y + " " + z + " r" + range);
        }

        @Override
        public void cancel() {
            cancelCount++;
        }

        @Override
        public boolean pause() {
            pauseCount++;
            return pauseAccepted;
        }

        @Override
        public boolean resume() {
            resumeCount++;
            return resumeAccepted;
        }

        @Override
        public boolean setSetting(String name, String value) {
            settings.put(name, value);
            return true;
        }

        @Override
        public String getSetting(String name) {
            return settings.get(name);
        }

        @Override
        public List<String> settingNames() {
            return List.copyOf(settings.keySet());
        }

        @Override
        public String commandFor(String action) {
            return commandForResult;
        }
    }

    private static BaritoneStatus idleStatus() {
        return new BaritoneStatus(true, "1.10", "", false, "none", "",
                BaritoneStatus.NO_COORD, BaritoneStatus.NO_COORD, BaritoneStatus.NO_COORD,
                false, false, false, BaritoneStatus.UNKNOWN, BaritoneStatus.UNKNOWN, false, false, "idle");
    }

    private static BaritoneStatus pathingStatus(String detail) {
        return new BaritoneStatus(true, "1.10", "", true, "GoalBlock", detail,
                10, 64, -20, true, false, true, 40, 200, false, false, "block " + detail);
    }

    @Test
    void unavailableBaritoneIsReportedNotThrown() {
        FakeBridge bridge = new FakeBridge();
        bridge.probeResult = BaritoneBridge.Availability.no("Baritone is not installed");
        BaritoneService service = new BaritoneService(bridge);
        service.probeBlocking();

        assertTrue(service.probed());
        assertFalse(service.available());
        assertEquals("Baritone is not installed", service.unavailableReason());
        assertFalse(service.status().available());

        // Issuing a goal while unavailable returns the reason and never reaches the bridge.
        String error = service.goTo(BaritoneGoal.block(1, 2, 3));
        assertNotNull(error);
        assertTrue(bridge.goals.isEmpty());
    }

    @Test
    void availableBaritoneExposesVersionAndStatus() {
        FakeBridge bridge = new FakeBridge();
        bridge.status = pathingStatus("10 64 -20");
        BaritoneService service = new BaritoneService(bridge);
        service.probeBlocking();
        service.poll(1_000);

        assertTrue(service.available());
        assertEquals("1.10", service.version());
        assertTrue(service.status().hasGoal());
        assertEquals("GoalBlock", service.status().goalType());
    }

    @Test
    void goToIssuesGoalAndMarksIt() {
        FakeBridge bridge = new FakeBridge();
        BaritoneService service = new BaritoneService(bridge);
        service.probeBlocking();

        assertNull(service.goTo(BaritoneGoal.block(1, 2, 3)));
        assertEquals(1, bridge.goals.size());
        assertTrue(bridge.goals.get(0).contains("1 2 3"));
    }

    @Test
    void followNearIssuesRadiusGoal() {
        FakeBridge bridge = new FakeBridge();
        BaritoneService service = new BaritoneService(bridge);
        service.probeBlocking();

        assertNull(service.followNear(4, 5, 6, 3));
        assertEquals(1, bridge.goals.size());
        assertTrue(bridge.goals.get(0).contains("r3"));
    }

    @Test
    void invalidFollowRadiusIsRejectedWithoutBridgeCall() {
        FakeBridge bridge = new FakeBridge();
        BaritoneService service = new BaritoneService(bridge);
        service.probeBlocking();

        assertNotNull(service.followNear(4, 5, 6, 0));
        assertTrue(bridge.goals.isEmpty());
    }

    @Test
    void goalCompletionIsReportedOnceThenConsumed() {
        FakeBridge bridge = new FakeBridge();
        BaritoneService service = new BaritoneService(bridge);
        service.probeBlocking();

        service.goTo(BaritoneGoal.block(1, 2, 3));
        bridge.status = pathingStatus("1 2 3");
        service.poll(1_000);
        assertFalse(service.consumeGoalReached(), "still pathing, not arrived");

        bridge.status = idleStatus();
        service.poll(2_000);
        assertTrue(service.consumeGoalReached(), "arrival should be reported");
        assertFalse(service.consumeGoalReached(), "arrival must be reported only once");
    }

    @Test
    void cancelClearsIssuedGoalAndSuppressesArrival() {
        FakeBridge bridge = new FakeBridge();
        BaritoneService service = new BaritoneService(bridge);
        service.probeBlocking();

        service.goTo(BaritoneGoal.block(1, 2, 3));
        bridge.status = pathingStatus("1 2 3");
        service.poll(1_000);
        service.cancel();
        assertEquals(1, bridge.cancelCount);

        bridge.status = idleStatus();
        service.poll(2_000);
        assertFalse(service.consumeGoalReached(), "a cancelled goal must not fire an arrival");
    }

    @Test
    void pauseDelegatesToBridgeAndReportsSuccess() {
        FakeBridge bridge = new FakeBridge();
        BaritoneService service = new BaritoneService(bridge);
        service.probeBlocking();

        assertNull(service.pause());
        assertEquals(1, bridge.pauseCount);
        assertEquals(0, bridge.cancelCount, "a pause must not cancel");
    }

    @Test
    void resumeDelegatesToBridgeAndReportsSuccess() {
        FakeBridge bridge = new FakeBridge();
        BaritoneService service = new BaritoneService(bridge);
        service.probeBlocking();

        assertNull(service.resume());
        assertEquals(1, bridge.resumeCount);
    }

    @Test
    void rejectedPauseIsReportedAsError() {
        FakeBridge bridge = new FakeBridge();
        bridge.pauseAccepted = false;
        BaritoneService service = new BaritoneService(bridge);
        service.probeBlocking();

        assertNotNull(service.pause());
        assertEquals(1, bridge.pauseCount);
    }

    @Test
    void pauseAndResumeAreUnavailableWithoutBaritone() {
        FakeBridge bridge = new FakeBridge();
        bridge.probeResult = BaritoneBridge.Availability.no("Baritone is not installed");
        BaritoneService service = new BaritoneService(bridge);
        service.probeBlocking();

        assertNotNull(service.pause());
        assertNotNull(service.resume());
        assertEquals(0, bridge.pauseCount);
        assertEquals(0, bridge.resumeCount);
    }

    @Test
    void pausedReflectsLatestStatus() {
        FakeBridge bridge = new FakeBridge();
        BaritoneService service = new BaritoneService(bridge);
        service.probeBlocking();
        assertFalse(service.paused());

        bridge.status = new BaritoneStatus(true, "1.10", "", true, "GoalBlock", "1 2 3",
                1, 2, 3, false, false, true, 10, 40, true, true, "block 1 2 3 \u00B7 paused");
        service.poll(1_000);
        assertTrue(service.paused());
    }

    @Test
    void statusReadFailureDegradesToUnavailableAndLogsError() {
        FakeBridge bridge = new FakeBridge();
        BaritoneService service = new BaritoneService(bridge);
        service.probeBlocking();

        bridge.statusError = new BaritoneBridge.BaritoneException("kaboom");
        service.poll(1_000);
        assertFalse(service.status().available());
        // The history holds both the initial idle state and the error that replaced it.
        boolean sawError = service.history().recent(10).stream()
                .anyMatch(entry -> "error".equals(entry.kind()));
        assertTrue(sawError, "the read failure should be recorded as an error entry");
    }

    @Test
    void setSettingDelegatesToBridge() {
        FakeBridge bridge = new FakeBridge();
        BaritoneService service = new BaritoneService(bridge);
        service.probeBlocking();

        assertNull(service.setSetting("allowSprint", "false"));
        assertEquals("false", bridge.settings.get("allowSprint"));
        assertEquals("false", service.getSetting("allowSprint"));
    }

    @Test
    void setSettingWithoutValueIsRejected() {
        FakeBridge bridge = new FakeBridge();
        BaritoneService service = new BaritoneService(bridge);
        service.probeBlocking();

        assertNotNull(service.setSetting("allowSprint", null));
        assertTrue(bridge.settings.isEmpty());
    }

    @Test
    void settingNamesAreEmptyWhenUnavailable() {
        FakeBridge bridge = new FakeBridge();
        bridge.probeResult = BaritoneBridge.Availability.no("not installed");
        BaritoneService service = new BaritoneService(bridge);
        service.probeBlocking();

        assertTrue(service.settingNames().isEmpty());
    }

    @Test
    void historyRecordsTransitionsAcrossPolls() {
        FakeBridge bridge = new FakeBridge();
        BaritoneService service = new BaritoneService(bridge);
        service.probeBlocking();

        service.poll(1_000);
        bridge.status = pathingStatus("1 2 3");
        service.poll(2_000);
        bridge.status = idleStatus();
        service.poll(3_000);

        assertEquals(3, service.history().size());
    }
}
