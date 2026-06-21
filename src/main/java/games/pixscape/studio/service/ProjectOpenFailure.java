package games.pixscape.studio.service;

import com.badlogic.gdx.files.FileHandle;

public final class ProjectOpenFailure {
    private final String flow;
    private final String projectPath;
    private final String message;
    private final Throwable cause;

    public ProjectOpenFailure(String flow, FileHandle projectFile, Throwable cause) {
        this.flow = (flow == null || flow.isBlank()) ? "project open" : flow;
        this.projectPath = projectFile == null ? "<unknown>" : projectFile.path();
        this.cause = cause;

        String reason = (cause == null || cause.getMessage() == null || cause.getMessage().isBlank())
                ? (cause == null ? "Unknown error" : cause.getClass().getSimpleName())
                : cause.getMessage();
        this.message = "Failed to " + this.flow + " for project '" + this.projectPath + "'.\n\nReason: " + reason;
    }

    public String flow() {
        return flow;
    }

    public String projectPath() {
        return projectPath;
    }

    public String message() {
        return message;
    }

    public Throwable cause() {
        return cause;
    }
}
