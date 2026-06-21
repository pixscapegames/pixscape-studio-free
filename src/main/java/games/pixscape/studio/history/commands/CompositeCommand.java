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
        for (Command cmd : commands) {
            cmd.redo();
        }
    }

    @Override
    public void undo() {
        for (int i = commands.size() - 1; i >= 0; i--) {
            commands.get(i).undo();
        }
    }
}
