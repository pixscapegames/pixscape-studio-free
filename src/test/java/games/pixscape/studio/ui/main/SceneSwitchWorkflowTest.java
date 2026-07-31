package games.pixscape.studio.ui.main;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class SceneSwitchWorkflowTest {

    @Test
    public void immediateDecisionLoadsAndConfirmsTarget() {
        Harness harness = new Harness((target, continuation, cancel, failure) -> continuation.run());

        harness.workflow.request("Other");

        assertEquals("Other", harness.loaded.get());
        assertEquals("Other", harness.confirmed.get());
        assertEquals(0, harness.restores.get());
        assertFalse(harness.workflow.isPending());
    }

    @Test
    public void savePathLoadsOnlyAfterSuccess() {
        AtomicReference<Runnable> saveSuccess = new AtomicReference<>();
        Harness harness = new Harness((target, continuation, cancel, failure) -> saveSuccess.set(continuation));

        harness.workflow.request("Other");

        assertNull(harness.loaded.get());
        assertTrue(harness.workflow.isPending());
        saveSuccess.get().run();
        assertEquals("Other", harness.loaded.get());
        assertFalse(harness.workflow.isPending());
    }

    @Test
    public void cancelRestoresSelectorWithoutLoading() {
        Harness harness = new Harness((target, continuation, cancel, failure) -> cancel.run());

        harness.workflow.request("Other");

        assertNull(harness.loaded.get());
        assertNull(harness.confirmed.get());
        assertEquals(1, harness.restores.get());
        assertFalse(harness.workflow.isPending());
        assertFalse(harness.selectorDisabled.get());
    }

    @Test
    public void saveFailureRestoresSelectorSurfacesFailureAndClearsPending() {
        RuntimeException failure = new RuntimeException("save failed");
        Harness harness = new Harness((target, continuation, cancel, onFailure) -> onFailure.accept(failure));

        harness.workflow.request("Other");

        assertNull(harness.loaded.get());
        assertEquals(1, harness.restores.get());
        assertSame(failure, harness.saveFailure.get());
        assertFalse(harness.workflow.isPending());
    }

    @Test
    public void sceneLoadFailureRestoresSelectorSurfacesFailureAndDoesNotConfirm() {
        RuntimeException failure = new RuntimeException("load failed");
        Harness harness = new Harness((target, continuation, cancel, onFailure) -> continuation.run());
        harness.sceneFailureToThrow = failure;

        harness.workflow.request("Other");

        assertEquals(1, harness.restores.get());
        assertNull(harness.confirmed.get());
        assertSame(failure, harness.sceneFailure.get());
        assertFalse(harness.workflow.isPending());
    }

    @Test
    public void repeatedRequestsWhilePendingStartOnlyOneDecision() {
        AtomicInteger decisions = new AtomicInteger();
        Harness harness = new Harness((target, continuation, cancel, failure) -> decisions.incrementAndGet());

        harness.workflow.request("Other");
        harness.workflow.request("Third");

        assertEquals(1, decisions.get());
        assertNull(harness.loaded.get());
        assertTrue(harness.workflow.isPending());
        assertTrue(harness.selectorDisabled.get());
    }

    private static final class Harness {
        private final AtomicReference<String> loaded = new AtomicReference<>();
        private final AtomicReference<String> confirmed = new AtomicReference<>();
        private final AtomicInteger restores = new AtomicInteger();
        private final AtomicReference<Throwable> saveFailure = new AtomicReference<>();
        private final AtomicReference<RuntimeException> sceneFailure = new AtomicReference<>();
        private final AtomicBoolean selectorDisabled = new AtomicBoolean();
        private RuntimeException sceneFailureToThrow;
        private final SceneSwitchWorkflow workflow;

        private Harness(SceneSwitchWorkflow.DecisionRequester requester) {
            workflow = new SceneSwitchWorkflow(
                    requester,
                    target -> {
                        if (sceneFailureToThrow != null) throw sceneFailureToThrow;
                        loaded.set(target);
                    },
                    restores::incrementAndGet,
                    confirmed::set,
                    saveFailure::set,
                    sceneFailure::set,
                    selectorDisabled::set
            );
        }
    }
}
