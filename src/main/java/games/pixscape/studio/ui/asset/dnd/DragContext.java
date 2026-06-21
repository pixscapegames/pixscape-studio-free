package games.pixscape.studio.ui.asset.dnd;

// imports

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class DragContext {
    private static final DragContext I = new DragContext();

    public static DragContext get() {
        return I;
    }

    private final AtomicReference<DragPayload> ref = new AtomicReference<>(null);
    private final AtomicBoolean releasedOnce = new AtomicBoolean(false);

    private DragContext() {
    }

    public void begin(DragPayload payload) {
        ref.set(payload);
        releasedOnce.set(false); // reset on each new drag
    }

    /**
     * Marks that the button was released (in the source window).
     */
    public void signalRelease() {
        releasedOnce.set(true);
    }

    /**
     * Actif tant qu’il y a un payload.
     */
    public boolean active() {
        return ref.get() != null;
    }

    /**
     * Non-destructive read (useful to create/apply the ghost).
     */
    public DragPayload peek() {
        return ref.get();
    }

    public boolean releasePending() {
        return releasedOnce.get();
    }

    public DragPayload consumeReleased() {
        if (releasedOnce.compareAndSet(true, false)) {
            return ref.getAndSet(null);
        }
        return null;
    }

    /**
     * Consumes the payload ONLY if a release was signaled AND the target is "inside".
     */
    public DragPayload consumeIfReleasedInside(boolean inside) {
        if (!inside) return null;
        if (releasedOnce.compareAndSet(true, false)) {
            return ref.getAndSet(null);
        }
        return null;
    }

    /**
     * Manual cancellation (for example, focus loss, escape).
     */
    public void cancel() {
        ref.set(null);
        releasedOnce.set(false);
    }
}

