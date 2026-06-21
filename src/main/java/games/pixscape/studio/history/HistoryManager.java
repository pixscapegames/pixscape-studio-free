package games.pixscape.studio.history;

import com.badlogic.gdx.Gdx;
import games.pixscape.studio.history.commands.Command;
import games.pixscape.studio.history.commands.CompositeCommand;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

/**
 * HistoryManager : moteur d’historique unique (undo/redo + capture drag).
 */
public final class HistoryManager {

    private static final String DEBUG_HISTORY_PROP = "pixscape.debug.history";

    public interface Listener {
        void onChanged(int undoSize, int redoSize, String undoLabel, String redoLabel, boolean dirty);
    }

    /**
     * Optional hint for coalesced commands: avoids stacking no-ops.
     */
    public interface SupportsNoop {
        boolean isNoop();
    }

    private final Deque<Command> undo = new ArrayDeque<>();
    private final Deque<Command> redo = new ArrayDeque<>();
    private final int maxSize;
    private final HistoryIdRegistry historyIds = new HistoryIdRegistry();

    private Listener listener;
    private int opDepth = 0;
    private int cursor = 0;
    private int savedCursor = 0;

    // Commande en cours de capture (drag gizmo, etc.)
    private Command capturing = null;

    private static final class GroupFrame {
        private final String label;
        private final List<Command> commands = new ArrayList<>();

        private GroupFrame(String label) {
            this.label = label;
        }
    }

    private final Deque<GroupFrame> groupStack = new ArrayDeque<>();

    public HistoryManager(int maxSize) {
        this.maxSize = Math.max(1, maxSize);
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    public HistoryIdRegistry historyIds() {
        return historyIds;
    }

    public void clear() {
        enterOp("clear");
        try {
            undo.clear();
            redo.clear();
            capturing = null;
            groupStack.clear();
            cursor = 0;
            savedCursor = 0;
        } finally {
            exitOp();
            fireChanged();
        }
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public boolean isDirty() {
        return cursor != savedCursor;
    }

    public int getCursor() {
        return cursor;
    }

    public int getSavedCursor() {
        return savedCursor;
    }

    public void markSaved() {
        ensureNotInOp("markSaved");
        savedCursor = cursor;
        fireChanged();
    }

    public String peekUndoLabel() {
        return undo.isEmpty() ? null : safeLabel(undo.peek());
    }

    public String peekRedoLabel() {
        return redo.isEmpty() ? null : safeLabel(redo.peek());
    }

    private static String safeLabel(Command c) {
        try {
            return c != null ? c.label() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    // --- executing a new command ---

    /**
     * Executes a new command and clears the redo stack.
     */
    public void execute(Command c) {
        if (c == null) return;
        debug("execute(" + safeLabel(c) + ") before undo=" + undo.size() + " redo=" + redo.size() + " cursor=" + cursor);
        enterOp("execute");
        try {
            c.redo();
            debug("redo executed label=" + safeLabel(c));
            if (!redo.isEmpty()) {
                debug("redo.clear reason=execute label=" + safeLabel(c) + " redoBefore=" + redo.size());
            }
            redo.clear();
            if (groupStack.isEmpty()) {
                undo.push(c);
                trim();
                cursor++;
            } else {
                groupStack.peek().commands.add(c);
            }
        } catch (Throwable t) {
            logError("History.execute failed on: " + safeLabel(c), t);
            throw t;
        } finally {
            exitOp();
            debug("execute(" + safeLabel(c) + ") after undo=" + undo.size() + " redo=" + redo.size() + " cursor=" + cursor);
            fireChanged();
        }
    }

    // --- groupes de commandes ---

    public void beginGroup(String label) {
        ensureNotInOp("beginGroup");
        groupStack.push(new GroupFrame(label));
    }

    public void endGroup() {
        ensureNotInOp("endGroup");
        if (groupStack.isEmpty()) return;

        GroupFrame frame = groupStack.pop();
        if (frame.commands.isEmpty()) {
            return;
        }

        CompositeCommand grouped = new CompositeCommand(frame.label, frame.commands);
        if (!groupStack.isEmpty()) {
            groupStack.peek().commands.add(grouped);
            return;
        }

        undo.push(grouped);
        redo.clear();
        trim();
        cursor++;
        fireChanged();
        debug("endGroup label=" + frame.label + " count=" + frame.commands.size());
    }

    public void withGroup(String label, Runnable action) {
        ensureNotInOp("withGroup");
        beginGroup(label);
        try {
            if (action != null) {
                action.run();
            }
        } finally {
            endGroup();
        }
    }

    // --- Undo/Redo classiques ---

    /**
     * Undo = pop only if the command actually undid.
     */
    public void undo() {
        debug("undo request undo=" + undo.size() + " redo=" + redo.size() + " cursor=" + cursor);
        enterOp("undo");
        boolean fire = false;
        Command c = null;
        try {
            if (undo.isEmpty()) {
                debug("undo boundary no-op");
                return;
            }
            c = undo.peek(); // do not pop immediately
            fire = true;
            c.undo();
            debug("undo executed label=" + safeLabel(c));
            undo.pop();
            redo.push(c);
            cursor--;
        } catch (Throwable t) {
            logError("History.undo failed on: " + safeLabel(c), t);
            throw t;
        } finally {
            exitOp();
            if (fire) {
                fireChanged();
            }
        }
    }

    /**
     * Redo = pop only if the command actually redid.
     */
    public void redo() {
        debug("redo request undo=" + undo.size() + " redo=" + redo.size() + " cursor=" + cursor);
        enterOp("redo");
        boolean fire = false;
        Command c = null;
        try {
            if (redo.isEmpty()) {
                debug("redo boundary no-op");
                return;
            }
            c = redo.peek(); // do not pop immediately
            fire = true;
            c.redo();
            debug("redo executed label=" + safeLabel(c));
            redo.pop();
            undo.push(c);
            cursor++;
        } catch (Throwable t) {
            logError("History.redo failed on: " + safeLabel(c), t);
            throw t;
        } finally {
            exitOp();
            if (fire) {
                fireChanged();
            }
        }
    }

    private void trim() {
        while (undo.size() > maxSize) undo.removeLast();
    }

    // --- capture for drags (coalescing) ---

    /**
     * Starts a capture, such as a gizmo drag. Does not call redo() here.
     */
    public void beginCapture(Command c) {
        ensureNotInOp("beginCapture");
        capturing = c;
    }

    /**
     * Allows mutating the captured command during drag (for example, updating "after" values).
     * Do not touch history until commitCapture has been called.
     */
    public void updateCapture(Consumer<Command> mutator) {
        if (capturing != null && mutator != null) {
            mutator.accept(capturing);
        }
    }

    /**
     * Ends capture: pushes the command if it is not a no-op.
     * On appelle execute(capturing), qui fait redo() + push + clean redo.
     */
    public void commitCapture() {
        ensureNotInOp("commitCapture");
        if (capturing == null) return;

        if (capturing instanceof SupportsNoop
                && ((SupportsNoop) capturing).isNoop()) {
            capturing = null;
            return;
        }

        execute(capturing);
        capturing = null;
    }

    public void cancelCapture() {
        ensureNotInOp("cancelCapture");
        capturing = null;
    }

    // --- internals ---

    private void fireChanged() {
        if (listener != null) {
            listener.onChanged(undo.size(), redo.size(), peekUndoLabel(), peekRedoLabel(), isDirty());
        }
    }

    private void enterOp(String opName) {
        if (opDepth > 0) {
            throw new IllegalStateException("History reentrance: " + opName + " while another history op is running");
        }
        opDepth = 1;
    }

    private void exitOp() {
        opDepth = 0;
    }

    private void ensureNotInOp(String opName) {
        if (opDepth > 0) {
            throw new IllegalStateException("History reentrance: " + opName + " while another history op is running");
        }
    }


    private static boolean debugEnabled() {
        return Boolean.getBoolean(DEBUG_HISTORY_PROP);
    }

    private static void debug(String msg) {
        if (!debugEnabled()) return;
        try {
            Gdx.app.log("History", msg);
        } catch (Throwable ignored) {
            System.out.println("[History] " + msg);
        }
    }

    private static void logError(String msg, Throwable t) {
        try {
            // If Gdx is available (desktop), this appears in the console
            Gdx.app.error("History", msg, t);
        } catch (Throwable ignored) {
            // Fallback if Gdx.app is not available in this context
            System.err.println("[History] " + msg);
            t.printStackTrace(System.err);
        }
    }
}
