package games.pixscape.studio.history.commands;

import games.pixscape.studio.history.HistoryManager;

import java.util.ArrayList;
import java.util.List;

public final class CompositeCommand implements Command, HistoryManager.SupportsNoop {

    private final String label;
    private final List<Command> commands;

    public CompositeCommand(String label, List<Command> commands) {
        this.label = label;
        this.commands = new ArrayList<>();
        if (commands != null) {
            for (Command cmd : commands) {
                if (cmd != null) {
                    this.commands.add(cmd);
                }
            }
        }
    }

    @Override
    public String label() {
        if (label == null || label.isBlank()) {
            return "Grouped Command";
        }
        return label;
    }

    @Override
    public boolean isNoop() {
        return commands.isEmpty();
    }

    @Override
    public void redo() {
        int completed = 0;
        try {
            for (; completed < commands.size(); completed++) {
                commands.get(completed).redo();
            }
        } catch (Throwable executionFailure) {
            IllegalStateException failure = new IllegalStateException(
                    "Composite command '" + label()
                            + "' failed while executing child " + completed
                            + "; completed children were rolled back.",
                    executionFailure);
            for (int i = completed - 1; i >= 0; i--) {
                try {
                    commands.get(i).undo();
                } catch (Throwable rollbackFailure) {
                    failure.addSuppressed(new IllegalStateException(
                            "Rollback failed for child " + i
                                    + " ('" + safeLabel(commands.get(i)) + "').",
                            rollbackFailure));
                }
            }
            throw failure;
        }
    }

    @Override
    public void undo() {
        for (int i = commands.size() - 1; i >= 0; i--) {
            commands.get(i).undo();
        }
    }

    private static String safeLabel(Command command) {
        try {
            String value = command != null ? command.label() : null;
            return value != null ? value : "unnamed";
        } catch (Throwable ignored) {
            return "unavailable";
        }
    }
}
