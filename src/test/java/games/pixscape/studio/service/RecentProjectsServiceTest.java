package games.pixscape.studio.service;

import games.pixscape.studio.configuration.EditorSettings;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RecentProjectsServiceTest {
    private String originalUserHome;
    private Path tempHome;

    @Before
    public void setUp() throws Exception {
        originalUserHome = System.getProperty("user.home");
        tempHome = Files.createTempDirectory("pixscape-recent-projects");
        System.setProperty("user.home", tempHome.toString());
        EditorSettings.load();
    }

    @After
    public void tearDown() {
        System.setProperty("user.home", originalUserHome);
        EditorSettings.load();
    }

    @Test
    public void addRecentProject_addsProjectToEmptyList() {
        RecentProjectsService service = new RecentProjectsService();
        String project = projectPath("one");

        service.addRecentProject(project);

        assertEquals(List.of(normalizedProjectPath("one")), service.getRecentProjects());
    }

    @Test
    public void addRecentProject_keepsOnlyFiveMostRecentProjects() {
        RecentProjectsService service = new RecentProjectsService();

        for (int i = 1; i <= 6; i++) {
            service.addRecentProject(projectPath("project" + i));
        }

        assertEquals(List.of(
                normalizedProjectPath("project6"),
                normalizedProjectPath("project5"),
                normalizedProjectPath("project4"),
                normalizedProjectPath("project3"),
                normalizedProjectPath("project2")
        ), service.getRecentProjects());
    }

    @Test
    public void addRecentProject_movesExistingProjectToFrontWithoutDuplicate() {
        RecentProjectsService service = new RecentProjectsService();
        String projectOne = projectPath("one");
        String projectTwo = projectPath("two");

        service.addRecentProject(projectOne);
        service.addRecentProject(projectTwo);
        service.addRecentProject(projectOne);

        assertEquals(List.of(
                normalizedProjectPath("one"),
                normalizedProjectPath("two")
        ), service.getRecentProjects());
    }

    @Test
    public void recentProjects_persistAfterReloadingSettings() {
        RecentProjectsService service = new RecentProjectsService();
        String project = projectPath("persisted");

        service.addRecentProject(project);
        EditorSettings.load();

        assertEquals(List.of(normalizedProjectPath("persisted")), new RecentProjectsService().getRecentProjects());
    }

    @Test
    public void clearRecentProjects_emptiesList() {
        RecentProjectsService service = new RecentProjectsService();
        service.addRecentProject(projectPath("one"));

        service.clearRecentProjects();

        assertTrue(service.getRecentProjects().isEmpty());
    }

    @Test
    public void removeRecentProject_removesMissingProjectPath() {
        RecentProjectsService service = new RecentProjectsService();
        String project = projectPath("missing");

        service.addRecentProject(project);
        service.removeRecentProject(project);

        assertTrue(service.getRecentProjects().isEmpty());
    }

    private String projectPath(String name) {
        return tempHome.resolve("projects").resolve(name).resolve("project.json").toString();
    }

    private String normalizedProjectPath(String name) {
        return RecentProjectsService.normalize(projectPath(name));
    }
}
