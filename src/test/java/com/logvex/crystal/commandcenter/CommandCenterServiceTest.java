package com.logvex.crystal.commandcenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers the service orchestrator: provider aggregation, isolation and performance sampling. */
class CommandCenterServiceTest {

    private static StatusProvider provider(String id, StatusSection section) {
        return new StatusProvider() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String title() {
                return id;
            }

            @Override
            public StatusSection snapshot() {
                return section;
            }
        };
    }

    private static CommandCenterService service() {
        return new CommandCenterService(null, new PerformanceMonitor(30), new PerformanceIntelligence());
    }

    @Test
    void snapshotAggregatesProvidersPlusPerformanceSection() {
        CommandCenterService service = service();
        service.addProvider(provider("a", StatusSection.of("a", "Alpha", StatusEntry.ok("x", "y"))));
        service.addProvider(provider("b", StatusSection.of("b", "Beta", StatusEntry.attention("p", "q", ""))));

        ClientStatusModel model = service.snapshot();
        assertEquals(3, model.sections().size());
        assertNotNull(model.section("a"));
        assertNotNull(model.section("performance"));
        assertEquals(HealthStatus.ATTENTION, model.overall());
    }

    @Test
    void duplicateProviderIdsAreRejected() {
        CommandCenterService service = service();
        service.addProvider(provider("dup", StatusSection.of("dup", "D", StatusEntry.ok("x", "y"))));
        assertThrows(IllegalStateException.class,
                () -> service.addProvider(provider("DUP", StatusSection.of("dup", "D", StatusEntry.ok("x", "y")))));
    }

    @Test
    void aThrowingProviderIsIsolatedAsACriticalSection() {
        CommandCenterService service = service();
        service.addProvider(provider("ok", StatusSection.of("ok", "OK", StatusEntry.ok("x", "y"))));
        service.addProvider(new StatusProvider() {
            @Override
            public String id() {
                return "broken";
            }

            @Override
            public String title() {
                return "Broken";
            }

            @Override
            public StatusSection snapshot() {
                throw new RuntimeException("provider exploded");
            }
        });
        ClientStatusModel model = service.snapshot();
        assertEquals(HealthStatus.CRITICAL, model.section("broken").worst());
        // The healthy provider is still present; one failure did not blank the model.
        assertNotNull(model.section("ok"));
    }

    @Test
    void cachedStatusIsReusedUntilRefreshed() {
        CommandCenterService service = service();
        service.addProvider(provider("a", StatusSection.of("a", "Alpha", StatusEntry.ok("x", "y"))));
        ClientStatusModel first = service.statusModel();
        assertNotNull(first);
        // statusModel() must not rebuild when a snapshot already exists.
        assertEquals(first, service.statusModel());
        service.refreshStatus();
        assertNotNull(service.statusModel());
    }

    @Test
    void performanceStartsEmptyThenJudgesSampledFrames() {
        CommandCenterService service = service();
        assertFalse(service.performance().sample().hasFrameData());

        for (int i = 0; i < 20; i++) {
            service.recordFrame(8d);
        }
        PerformanceReport report = service.samplePerformance(1L, 3, "Heavy", 5.0, 8.0, 0, 0, 0, 0);
        assertTrue(report.sample().hasFrameData());
        assertTrue(report.findings().stream().anyMatch(f -> f.rule().equals("module-tick")));
        assertEquals(report.sample(), service.performance().sample());
    }

    @Test
    void sectionByIdReturnsProviderOrPerformanceSection() {
        CommandCenterService service = service();
        service.addProvider(provider("a", StatusSection.of("a", "Alpha", StatusEntry.ok("x", "y"))));
        assertNotNull(service.section("a"));
        assertNotNull(service.section("performance"));
        assertNull(service.section("missing"));
    }

    @Test
    void verdictForUnknownModuleIsNull() {
        CommandCenterService service = service();
        assertNull(service.verdictFor(null));
        assertNull(service.verdictFor("Nothing"));
    }
}
