package games.pixscape.studio.history.commands;

/** Internal bridge that keeps optional command outcomes out of the public command contract. */
public final class CommandOutcomeExecution {
    private CommandOutcomeExecution() {
    }

    public static boolean executeApplied(Command command) {
        if (command instanceof OutcomeAwareCommand outcomeAware) {
            return outcomeAware.executeOutcome() == CommandOutcome.APPLIED;
        }
        command.redo();
        return true;
    }

    public static boolean undoApplied(Command command) {
        if (command instanceof OutcomeAwareCommand outcomeAware) {
            return outcomeAware.undoOutcome() == CommandOutcome.APPLIED;
        }
        command.undo();
        return true;
    }

    public static boolean redoApplied(Command command) {
        if (command instanceof OutcomeAwareCommand outcomeAware) {
            return outcomeAware.redoOutcome() == CommandOutcome.APPLIED;
        }
        command.redo();
        return true;
    }
}

enum CommandOutcome {
    APPLIED,
    REJECTED,
    NO_CHANGE
}

interface OutcomeAwareCommand {
    CommandOutcome executeOutcome();

    CommandOutcome undoOutcome();

    CommandOutcome redoOutcome();
}
