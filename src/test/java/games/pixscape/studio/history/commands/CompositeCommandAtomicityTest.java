package games.pixscape.studio.history.commands;

import games.pixscape.studio.history.HistoryManager;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CompositeCommandAtomicityTest {
    @Test
    public void secondCommandFailureRollsBackFirstAndDoesNotEnterHistory() {
        HistoryManager history = new HistoryManager(8);
        int[] state = {0};
        CompositeCommand composite = new CompositeCommand(
                "Atomic",
                Arrays.asList(increment(state, 1), failing("second")));

        try {
            history.execute(composite);
            Assert.fail("The composite must propagate the child failure.");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("child 1"));
        }

        Assert.assertEquals(0, state[0]);
        Assert.assertFalse(history.canUndo());
        Assert.assertFalse(history.canRedo());
        Assert.assertEquals(0, history.getCursor());
    }

    @Test
    public void lateFailureRollsBackAllCompletedChildrenInReverseOrder() {
        HistoryManager history = new HistoryManager(8);
        List<String> trace = new ArrayList<>();
        CompositeCommand composite = new CompositeCommand(
                "Late failure",
                Arrays.asList(
                        traced(trace, "a"),
                        traced(trace, "b"),
                        traced(trace, "c"),
                        failing("late")));

        try {
            history.execute(composite);
            Assert.fail("The composite must fail.");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("child 3"));
        }

        Assert.assertEquals(
                Arrays.asList("redo-a", "redo-b", "redo-c", "undo-c", "undo-b", "undo-a"),
                trace);
        Assert.assertFalse(history.canUndo());
    }

    @Test
    public void rollbackFailuresAreReportedWithoutMaskingExecutionFailure() {
        Command rollbackFailure = new Command() {
            @Override
            public String label() {
                return "rollback failure";
            }

            @Override
            public void redo() {
            }

            @Override
            public void undo() {
                throw new IllegalArgumentException("rollback");
            }
        };
        CompositeCommand composite = new CompositeCommand(
                "Aggregate",
                Arrays.asList(rollbackFailure, failing("execution")));

        try {
            composite.redo();
            Assert.fail("The composite must fail.");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("execution", expected.getCause().getMessage());
            Assert.assertEquals(1, expected.getSuppressed().length);
            Assert.assertTrue(expected.getSuppressed()[0].getMessage().contains("Rollback failed"));
        }
    }

    private static Command increment(int[] state, int amount) {
        return new Command() {
            @Override
            public String label() {
                return "increment";
            }

            @Override
            public void redo() {
                state[0] += amount;
            }

            @Override
            public void undo() {
                state[0] -= amount;
            }
        };
    }

    private static Command traced(List<String> trace, String name) {
        return new Command() {
            @Override
            public String label() {
                return name;
            }

            @Override
            public void redo() {
                trace.add("redo-" + name);
            }

            @Override
            public void undo() {
                trace.add("undo-" + name);
            }
        };
    }

    private static Command failing(String message) {
        return new Command() {
            @Override
            public String label() {
                return "failure";
            }

            @Override
            public void redo() {
                throw new IllegalStateException(message);
            }

            @Override
            public void undo() {
            }
        };
    }
}
