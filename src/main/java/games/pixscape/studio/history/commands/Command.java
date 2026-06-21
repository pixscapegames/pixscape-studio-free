package games.pixscape.studio.history.commands;

public interface Command {
    String label();

    void redo();

    void undo();

    default boolean canMerge(Command next) {
        return false;
    }

    default Command merge(Command next) {
        return this;
    }
}
