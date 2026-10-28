package games.pixscape.studio.history.commands;

public interface Command {
    String label();

    /** Cold preflight: net additional authored active lights in this new command. */
    default int additionalActiveLights() { return 0; }

    default boolean selectsLegacyBeforeLights() { return false; }

    void redo();

    void undo();

    default boolean canMerge(Command next) {
        return false;
    }

    default Command merge(Command next) {
        return this;
    }
}
