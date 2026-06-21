package games.pixscape.studio.helper;

import com.badlogic.gdx.Gdx;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class ThreadHelper {
    public static <T> T callOnGlThreadAndWait(java.util.concurrent.Callable<T> task) {
        if (Gdx.app == null) {
            throw new IllegalStateException("Gdx.app is null");
        }

        // If already on the GL thread, execute directly
        // (LibGDX does not always expose a clean check, but in this case consider:
        // - if in render()/UI listeners -> OK
        // - sinon -> worker)
        final AtomicReference<T> result = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);

        Gdx.app.postRunnable(() -> {
            try {
                result.set(task.call());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        if (error.get() != null) {
            Throwable t = error.get();
            if (t instanceof RuntimeException re) throw re;
            throw new RuntimeException(t);
        }
        return result.get();
    }

}
