package games.pixscape.studio.history.commands;

import games.pixscape.studio.history.HistoryManager;
import org.junit.Assert;
import org.junit.Test;

public class HistoryManagerOutcomeTest {
    @Test
    public void rejectedAndNoChangeExecutePreserveExistingRedo() {
        HistoryManager history = new HistoryManager(8);
        TestCommand legacy = new TestCommand("Legacy");
        history.execute(legacy);
        history.undo();
        int[] publications = {0};
        history.setListener((undoSize, redoSize, undoLabel, redoLabel, dirty) -> publications[0]++);

        OutcomeCommand rejected = new OutcomeCommand("Rejected", CommandOutcome.REJECTED);
        history.execute(rejected);
        assertRedoOnly(history, "Legacy");
        Assert.assertEquals(0, publications[0]);

        OutcomeCommand noChange = new OutcomeCommand("No change", CommandOutcome.NO_CHANGE);
        history.execute(noChange);
        assertRedoOnly(history, "Legacy");
        Assert.assertEquals(0, publications[0]);
    }

    @Test
    public void rejectedUndoKeepsCommandInUndo() {
        HistoryManager history = new HistoryManager(8);
        OutcomeCommand command = new OutcomeCommand("Outcome", CommandOutcome.APPLIED);
        history.execute(command);
        command.undoOutcome = CommandOutcome.REJECTED;

        history.undo();

        Assert.assertEquals(1, history.getCursor());
        Assert.assertTrue(history.canUndo());
        Assert.assertFalse(history.canRedo());
        Assert.assertEquals("Outcome", history.peekUndoLabel());
    }

    @Test
    public void rejectedRedoRemainsAvailableAndCanBeRetried() {
        HistoryManager history = new HistoryManager(8);
        OutcomeCommand command = new OutcomeCommand("Outcome", CommandOutcome.APPLIED);
        history.execute(command);
        history.undo();
        command.redoOutcome = CommandOutcome.REJECTED;

        history.redo();
        assertRedoOnly(history, "Outcome");

        command.redoOutcome = CommandOutcome.APPLIED;
        history.redo();
        Assert.assertEquals(1, history.getCursor());
        Assert.assertTrue(history.canUndo());
        Assert.assertFalse(history.canRedo());
    }

    @Test
    public void programmingExceptionsPropagateWithoutMovingStacks() {
        HistoryManager executeHistory = new HistoryManager(8);
        OutcomeCommand executeFailure = new OutcomeCommand("Execute failure", CommandOutcome.APPLIED);
        executeFailure.throwExecute = true;
        assertThrows(() -> executeHistory.execute(executeFailure));
        Assert.assertEquals(0, executeHistory.getCursor());
        Assert.assertFalse(executeHistory.canUndo());
        Assert.assertFalse(executeHistory.canRedo());

        HistoryManager undoHistory = new HistoryManager(8);
        TestCommand undoFailure = new TestCommand("Undo failure");
        undoHistory.execute(undoFailure);
        undoFailure.throwUndo = true;
        assertThrows(undoHistory::undo);
        Assert.assertEquals(1, undoHistory.getCursor());
        Assert.assertTrue(undoHistory.canUndo());
        Assert.assertFalse(undoHistory.canRedo());

        HistoryManager redoHistory = new HistoryManager(8);
        TestCommand redoFailure = new TestCommand("Redo failure");
        redoHistory.execute(redoFailure);
        redoHistory.undo();
        redoFailure.throwRedo = true;
        assertThrows(redoHistory::redo);
        assertRedoOnly(redoHistory, "Redo failure");
    }

    @Test
    public void legacyCommandRetainsExecuteUndoRedoBehavior() {
        HistoryManager history = new HistoryManager(8);
        TestCommand command = new TestCommand("Legacy");

        history.execute(command);
        history.undo();
        history.redo();

        Assert.assertEquals(2, command.redoCount);
        Assert.assertEquals(1, command.undoCount);
        Assert.assertEquals(1, history.getCursor());
        Assert.assertTrue(history.canUndo());
        Assert.assertFalse(history.canRedo());
    }

    @Test
    public void outcomeAwareCommandTakesPrecedenceOverPreExecutionNoopMarker() {
        HistoryManager history = new HistoryManager(8);
        MarkedOutcomeCommand command = new MarkedOutcomeCommand();

        history.execute(command);

        Assert.assertEquals(1, command.executeCount);
        Assert.assertEquals(0, command.noopCheckCount);
        Assert.assertEquals(0, command.redoCount);
        Assert.assertEquals(1, history.getCursor());
        Assert.assertTrue(history.canUndo());
    }

    @Test
    public void markedNoopSkipsRedoHistoryPublicationDirtyAndRevision() {
        HistoryManager history = new HistoryManager(8);
        history.execute(new TestCommand("Redo seed"));
        history.undo();
        int[] publications = {0};
        int[] revision = {7};
        history.setListener((undoSize, redoSize, undoLabel, redoLabel, dirty) -> publications[0]++);
        MarkedCommand command = new MarkedCommand("No change", true, revision);

        history.execute(command);

        Assert.assertEquals(0, command.redoCount);
        Assert.assertEquals(7, revision[0]);
        Assert.assertEquals(0, publications[0]);
        assertRedoOnly(history, "Redo seed");
    }

    @Test
    public void markedNonNoopExecutesNormally() {
        HistoryManager history = new HistoryManager(8);
        int[] revision = {3};
        MarkedCommand command = new MarkedCommand("Applied", false, revision);

        history.execute(command);

        Assert.assertEquals(1, command.redoCount);
        Assert.assertEquals(4, revision[0]);
        Assert.assertEquals(1, history.getCursor());
        Assert.assertTrue(history.canUndo());
        Assert.assertTrue(history.isDirty());
    }

    @Test
    public void unmarkedSupportsNoopCommandRetainsLegacyDirectExecution() {
        HistoryManager history = new HistoryManager(8);
        LegacyNoopCommand command = new LegacyNoopCommand();

        history.execute(command);

        Assert.assertEquals(1, command.redoCount);
        Assert.assertEquals(1, history.getCursor());
        Assert.assertTrue(history.canUndo());
    }

    @Test
    public void gizmoTransformCommandRemainsOutsidePreExecutionMarker() {
        Assert.assertTrue(HistoryManager.SupportsNoop.class.isAssignableFrom(
                GizmoTransformCommand.class));
        Assert.assertFalse(PreExecutionNoopCommand.class.isAssignableFrom(
                GizmoTransformCommand.class));
    }

    @Test
    public void irreversibleResetClearsStacksAndRemainsDirtyUntilSaved() {
        HistoryManager history = new HistoryManager(8);
        history.execute(new TestCommand("Before purge"));
        history.undo();
        Assert.assertTrue(history.canRedo());

        history.resetAfterIrreversibleChange();

        Assert.assertFalse(history.canUndo());
        Assert.assertFalse(history.canRedo());
        Assert.assertTrue(history.isDirty());

        history.execute(new TestCommand("After purge"));
        history.undo();
        Assert.assertTrue(history.isDirty());

        history.markSaved();
        Assert.assertFalse(history.isDirty());

        history.execute(new TestCommand("Clear"));
        history.clear();
        Assert.assertFalse(history.isDirty());
    }

    private static void assertRedoOnly(HistoryManager history, String label) {
        Assert.assertEquals(0, history.getCursor());
        Assert.assertFalse(history.canUndo());
        Assert.assertTrue(history.canRedo());
        Assert.assertNull(history.peekUndoLabel());
        Assert.assertEquals(label, history.peekRedoLabel());
        Assert.assertFalse(history.isDirty());
    }

    private static void assertThrows(Runnable action) {
        try {
            action.run();
            Assert.fail("Expected programming exception");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("programming failure", expected.getMessage());
        }
    }

    private static final class OutcomeCommand implements Command, OutcomeAwareCommand {
        private final String label;
        private CommandOutcome executeOutcome;
        private CommandOutcome undoOutcome = CommandOutcome.APPLIED;
        private CommandOutcome redoOutcome = CommandOutcome.APPLIED;
        private boolean throwExecute;

        private OutcomeCommand(String label, CommandOutcome executeOutcome) {
            this.label = label;
            this.executeOutcome = executeOutcome;
        }

        @Override public String label() { return label; }
        @Override public void redo() { }
        @Override public void undo() { }
        @Override
        public CommandOutcome executeOutcome() {
            if (throwExecute) throw new IllegalStateException("programming failure");
            return executeOutcome;
        }
        @Override public CommandOutcome undoOutcome() { return undoOutcome; }
        @Override public CommandOutcome redoOutcome() { return redoOutcome; }
    }

    private static final class MarkedOutcomeCommand
            implements Command, OutcomeAwareCommand, PreExecutionNoopCommand {
        private int executeCount;
        private int noopCheckCount;
        private int redoCount;

        @Override public String label() { return "Marked outcome"; }
        @Override public boolean isNoop() { noopCheckCount++; return true; }
        @Override public void redo() { redoCount++; }
        @Override public void undo() { }
        @Override public CommandOutcome executeOutcome() {
            executeCount++;
            return CommandOutcome.APPLIED;
        }
        @Override public CommandOutcome undoOutcome() { return CommandOutcome.APPLIED; }
        @Override public CommandOutcome redoOutcome() { return CommandOutcome.APPLIED; }
    }

    private static final class MarkedCommand implements Command, PreExecutionNoopCommand {
        private final String label;
        private final boolean noop;
        private final int[] revision;
        private int redoCount;

        private MarkedCommand(String label, boolean noop, int[] revision) {
            this.label = label;
            this.noop = noop;
            this.revision = revision;
        }

        @Override public String label() { return label; }
        @Override public boolean isNoop() { return noop; }
        @Override public void redo() { redoCount++; revision[0]++; }
        @Override public void undo() { revision[0]--; }
    }

    private static final class LegacyNoopCommand implements Command, HistoryManager.SupportsNoop {
        private int redoCount;

        @Override public String label() { return "Legacy no-op"; }
        @Override public boolean isNoop() { return true; }
        @Override public void redo() { redoCount++; }
        @Override public void undo() { }
    }

    private static final class TestCommand implements Command {
        private final String label;
        private int redoCount;
        private int undoCount;
        private boolean throwRedo;
        private boolean throwUndo;

        private TestCommand(String label) {
            this.label = label;
        }

        @Override public String label() { return label; }

        @Override
        public void redo() {
            if (throwRedo) throw new IllegalStateException("programming failure");
            redoCount++;
        }

        @Override
        public void undo() {
            if (throwUndo) throw new IllegalStateException("programming failure");
            undoCount++;
        }
    }
}
