package com.logvex.crystal.commandcenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers the action registry: ids, categories, dispatch and failure handling. */
class CommandRegistryTest {

    private static CommandCenterAction action(String id, String category, ActionResult result) {
        return new CommandCenterAction() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String label() {
                return id;
            }

            @Override
            public String category() {
                return category;
            }

            @Override
            public String description() {
                return "does " + id;
            }

            @Override
            public ActionResult run() {
                return result;
            }
        };
    }

    @Test
    void registerAndLookupAreCaseInsensitive() {
        CommandRegistry registry = new CommandRegistry();
        registry.register(action("Diagnostics.Report", "diagnostics", ActionResult.success("ok")));
        assertTrue(registry.contains("diagnostics.report"));
        assertNotNull(registry.byId("DIAGNOSTICS.REPORT"));
        assertEquals(1, registry.size());
    }

    @Test
    void duplicateIdsAreRejected() {
        CommandRegistry registry = new CommandRegistry();
        registry.register(action("a.b", "x", ActionResult.success("ok")));
        assertThrows(IllegalStateException.class,
                () -> registry.register(action("A.B", "x", ActionResult.success("ok"))));
    }

    @Test
    void blankIdIsRejected() {
        CommandRegistry registry = new CommandRegistry();
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(action("  ", "x", ActionResult.success("ok"))));
    }

    @Test
    void categoriesPreserveFirstSeenOrder() {
        CommandRegistry registry = new CommandRegistry();
        registry.registerAll(
                action("a.1", "alpha", ActionResult.success("ok")),
                action("b.1", "beta", ActionResult.success("ok")),
                action("a.2", "alpha", ActionResult.success("ok")));
        assertEquals(List.of("alpha", "beta"), registry.categories());
        assertEquals(2, registry.byCategory("alpha").size());
    }

    @Test
    void runDispatchesAndReportsFailure() {
        CommandRegistry registry = new CommandRegistry();
        registry.register(action("good", "x", ActionResult.success("done")));
        registry.register(action("bad", "x", ActionResult.failure("nope")));
        assertTrue(registry.run("good").success());
        assertFalse(registry.run("bad").success());
    }

    @Test
    void runningUnknownActionIsAFailureNotAnException() {
        CommandRegistry registry = new CommandRegistry();
        ActionResult result = registry.run("missing");
        assertFalse(result.success());
        assertTrue(result.message().contains("missing"));
    }

    @Test
    void unavailableActionIsReportedWithoutRunning() {
        CommandRegistry registry = new CommandRegistry();
        registry.register(new CommandCenterAction() {
            @Override
            public String id() {
                return "needs.world";
            }

            @Override
            public String label() {
                return "Needs World";
            }

            @Override
            public String category() {
                return "x";
            }

            @Override
            public String description() {
                return "";
            }

            @Override
            public boolean available() {
                return false;
            }

            @Override
            public String unavailableReason() {
                return "no world loaded";
            }

            @Override
            public ActionResult run() {
                throw new AssertionError("must not run when unavailable");
            }
        });
        ActionResult result = registry.run("needs.world");
        assertFalse(result.success());
        assertTrue(result.message().contains("no world loaded"));
    }

    @Test
    void throwingActionBecomesAFailure() {
        CommandRegistry registry = new CommandRegistry();
        registry.register(new CommandCenterAction() {
            @Override
            public String id() {
                return "boom";
            }

            @Override
            public String label() {
                return "Boom";
            }

            @Override
            public String category() {
                return "x";
            }

            @Override
            public String description() {
                return "";
            }

            @Override
            public ActionResult run() {
                throw new IllegalStateException("kaboom");
            }
        });
        ActionResult result = registry.run("boom");
        assertFalse(result.success());
        assertTrue(result.message().contains("kaboom"));
    }
}
