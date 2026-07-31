package games.pixscape.studio.ui.main;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class CurrentSceneSaveDecisionGuardTest {

    @Test
    public void cleanSceneRunsContinuationWithoutDialog() {
        AtomicInteger continuations = new AtomicInteger();
        AtomicInteger dialogs = new AtomicInteger();

        CurrentSceneSaveDecisionGuard.request(
                false, "title", "message", continuations::incrementAndGet, null, null,
                (title, message, save, dontSave, cancel) -> dialogs.incrementAndGet(),
                (success, failure) -> { }
        );

        assertEquals(1, continuations.get());
        assertEquals(0, dialogs.get());
    }

    @Test
    public void saveRunsContinuationOnlyAfterSaveSuccess() {
        AtomicInteger continuations = new AtomicInteger();
        AtomicInteger saves = new AtomicInteger();
        AtomicReference<Runnable> saveSuccess = new AtomicReference<>();

        CurrentSceneSaveDecisionGuard.request(
                true, "title", "message", continuations::incrementAndGet, null, null,
                (title, message, save, dontSave, cancel) -> save.run(),
                (success, failure) -> {
                    saves.incrementAndGet();
                    saveSuccess.set(success);
                }
        );

        assertEquals(1, saves.get());
        assertEquals(0, continuations.get());
        saveSuccess.get().run();
        assertEquals(1, continuations.get());
    }

    @Test
    public void dontSaveRunsContinuationWithoutSaving() {
        AtomicInteger continuations = new AtomicInteger();
        AtomicInteger saves = new AtomicInteger();

        CurrentSceneSaveDecisionGuard.request(
                true, "title", "message", continuations::incrementAndGet, null, null,
                (title, message, save, dontSave, cancel) -> dontSave.run(),
                (success, failure) -> saves.incrementAndGet()
        );

        assertEquals(1, continuations.get());
        assertEquals(0, saves.get());
    }

    @Test
    public void cancelRunsOnlyCancelCallback() {
        AtomicInteger continuations = new AtomicInteger();
        AtomicInteger cancels = new AtomicInteger();

        CurrentSceneSaveDecisionGuard.request(
                true, "title", "message", continuations::incrementAndGet, cancels::incrementAndGet, null,
                (title, message, save, dontSave, cancel) -> cancel.run(),
                (success, failure) -> { }
        );

        assertEquals(0, continuations.get());
        assertEquals(1, cancels.get());
    }

    @Test
    public void saveFailureDoesNotRunContinuation() {
        RuntimeException failure = new RuntimeException("save failed");
        AtomicInteger continuations = new AtomicInteger();
        AtomicReference<Throwable> surfaced = new AtomicReference<>();

        CurrentSceneSaveDecisionGuard.request(
                true, "title", "message", continuations::incrementAndGet, null, surfaced::set,
                (title, message, save, dontSave, cancel) -> save.run(),
                (success, onFailure) -> onFailure.accept(failure)
        );

        assertEquals(0, continuations.get());
        assertSame(failure, surfaced.get());
    }
}
