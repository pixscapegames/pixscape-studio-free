package games.pixscape.studio.history.initializer;


/**
 * Entity initialization API (capture -> (re)creation).
 */
public interface Initializer {
    /**
     * Captures current state from the source. Called BEFORE deletion.
     */
    void syncFrom(int sourceEid);

    /**
     * (Re)creates state on the target. Called during undo().
     */
    void init(int targetEid);

    default String label() {
        return getClass().getSimpleName();
    }
}
