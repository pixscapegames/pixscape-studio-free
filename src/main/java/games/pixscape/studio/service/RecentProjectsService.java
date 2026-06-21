package games.pixscape.studio.service;

import games.pixscape.studio.configuration.EditorSettings;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RecentProjectsService {
    public static final int MAX_RECENT_PROJECTS = 5;

    public List<String> getRecentProjects() {
        ensureList();
        return Collections.unmodifiableList(new ArrayList<>(EditorSettings.get().recentProjectPaths));
    }

    public void addRecentProject(String path) {
        String normalized = normalize(path);
        if (normalized.isBlank()) return;

        ArrayList<String> projects = ensureList();
        projects.remove(normalized);
        projects.add(0, normalized);
        while (projects.size() > MAX_RECENT_PROJECTS) {
            projects.remove(projects.size() - 1);
        }
        EditorSettings.save();
    }

    public void removeRecentProject(String path) {
        String normalized = normalize(path);
        if (normalized.isBlank()) return;

        ArrayList<String> projects = ensureList();
        if (projects.remove(normalized)) {
            EditorSettings.save();
        }
    }

    public void clearRecentProjects() {
        ArrayList<String> projects = ensureList();
        if (projects.isEmpty()) return;
        projects.clear();
        EditorSettings.save();
    }

    public static String normalize(String path) {
        if (path == null) return "";
        String trimmed = path.trim();
        if (trimmed.isEmpty()) return "";
        return Path.of(trimmed).toAbsolutePath().normalize().toString().replace('\\', '/');
    }

    private ArrayList<String> ensureList() {
        EditorSettings settings = EditorSettings.get();
        if (settings.recentProjectPaths == null) {
            settings.recentProjectPaths = new ArrayList<>();
        }
        return settings.recentProjectPaths;
    }
}
