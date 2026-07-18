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
