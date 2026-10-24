package games.pixscape.studio.ui.preview;

/** State machine for one child Preview process; callers still verify the session identity externally. */
final class PreviewSessionLifecycle {
    enum State { STARTING, LOADING, READY, CLOSING, FAILED }

    private State state = State.STARTING;

    State state() {
        return state;
    }

    boolean loading() {
        if (state != State.STARTING) return false;
        state = State.LOADING;
        return true;
    }

    boolean ready() {
        if (state == State.CLOSING || state == State.READY || state == State.FAILED) return false;
        state = State.READY;
        return true;
    }

    boolean failed() {
        if (state == State.CLOSING || state == State.FAILED) return false;
        state = State.FAILED;
        return true;
    }

    boolean closing() {
        if (state == State.CLOSING) return false;
        state = State.CLOSING;
        return true;
    }
}
