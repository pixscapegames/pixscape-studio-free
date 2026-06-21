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

        service.addRecentProject("C:/projects/one/project.json");

        assertEquals(List.of("C:/projects/one/project.json"), service.getRecentProjects());
    }

    @Test
    public void addRecentProject_keepsOnlyFiveMostRecentProjects() {
        RecentProjectsService service = new RecentProjectsService();

        for (int i = 1; i <= 6; i++) {
            service.addRecentProject("C:/projects/project" + i + "/project.json");
        }

        assertEquals(List.of(
                "C:/projects/project6/project.json",
                "C:/projects/project5/project.json",
                "C:/projects/project4/project.json",
                "C:/projects/project3/project.json",
                "C:/projects/project2/project.json"
        ), service.getRecentProjects());
    }

    @Test
    public void addRecentProject_movesExistingProjectToFrontWithoutDuplicate() {
        RecentProjectsService service = new RecentProjectsService();

        service.addRecentProject("C:/projects/one/project.json");
        service.addRecentProject("C:/projects/two/project.json");
        service.addRecentProject("C:/projects/one/project.json");

        assertEquals(List.of(
                "C:/projects/one/project.json",
                "C:/projects/two/project.json"
        ), service.getRecentProjects());
    }

    @Test
    public void recentProjects_persistAfterReloadingSettings() {
        RecentProjectsService service = new RecentProjectsService();

        service.addRecentProject("C:/projects/persisted/project.json");
        EditorSettings.load();

        assertEquals(List.of("C:/projects/persisted/project.json"), new RecentProjectsService().getRecentProjects());
    }

    @Test
    public void clearRecentProjects_emptiesList() {
        RecentProjectsService service = new RecentProjectsService();
        service.addRecentProject("C:/projects/one/project.json");

        service.clearRecentProjects();

        assertTrue(service.getRecentProjects().isEmpty());
    }

    @Test
    public void removeRecentProject_removesMissingProjectPath() {
        RecentProjectsService service = new RecentProjectsService();
        service.addRecentProject("C:/projects/missing/project.json");

        service.removeRecentProject("C:/projects/missing/project.json");

        assertTrue(service.getRecentProjects().isEmpty());
    }
}
