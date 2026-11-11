package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.StudioGlTestScope;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class AssetsPanelProjectLifecycleTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private ProjectConfig previous;
    private StudioGlTestScope globals;

    @Before public void setUp() throws Exception {
        globals = new StudioGlTestScope();
        previous = ProjectConfig.getInstance();
        ProjectConfig.setInstance(new ProjectConfig());
        VisUiTestBootstrap.loadSkin();
    }

    @After public void tearDown() throws Exception {
        AssetPreviewCache.clear();
        VisUiTestBootstrap.unloadSkin();
        ProjectConfig.setInstance(previous);
        globals.close();
    }

    @Test public void gameObjectsCanCrossEmptyStateAndRefreshOnlyTheNextProject() throws Exception {
        AssetsPanel panel = new AssetsPanel(null);
        ProjectConfig first = project("first");
        gameObject(first, "First");
        ProjectConfig.setInstance(first);
        panel.reloadFromProject(first);
        FolderTreeView tree = (FolderTreeView) field(panel, "treeView");
        AssetsThumbsView thumbs = (AssetsThumbsView) field(panel, "thumbsView");
        tree.selectFolder(folder(AssetNode.Root.GAME_OBJECTS));
        assertEquals("First", assets(thumbs).first().name);
        thumbs.setTileSelectionListener(node -> {});
        thumbs.setSelectedNode(assets(thumbs).first());
        AtomicInteger cleared = new AtomicInteger();
        panel.addAssetSelectionListener(node -> { if (node == null) cleared.incrementAndGet(); });

        ProjectConfig.setInstance(new ProjectConfig());
        panel.reloadFromProject(ProjectConfig.getInstance());

        assertNull(tree.getSelectedFolder());
        assertNull(panel.getSelectedAssetNode());
        assertNull(field(thumbs, "currentFolder"));
        assertTrue(assets(thumbs).isEmpty());
        assertTrue(((Array<?>) field(thumbs, "selectedNodes")).isEmpty());
        assertTrue(cleared.get() > 0);
        assertThrows(IllegalStateException.class,
                () -> StudioFs.requireStudioProjectDir(ProjectConfig.getInstance()));

        ProjectConfig next = project("next");
        gameObject(next, "Next");
        ProjectConfig.setInstance(next);
        panel.reloadFromProject(next);
        tree.selectFolder(folder(AssetNode.Root.GAME_OBJECTS));
        assertEquals(1, assets(thumbs).size);
        assertEquals("Next", assets(thumbs).first().name);
        assertTrue(assets(thumbs).first().path.startsWith(next.projectDirectoryPath));
        gameObject(next, "Added");
        panel.reloadFromProject(next);
        assertEquals(2, assets(thumbs).size);
    }

    @Test public void everyCategoryCanBeClearedWithoutAProjectDirectory() throws Exception {
        AssetsPanel panel = new AssetsPanel(null);
        FolderTreeView tree = (FolderTreeView) field(panel, "treeView");
        AssetsThumbsView thumbs = (AssetsThumbsView) field(panel, "thumbsView");
        ProjectConfig cfg = project("categories");
        Array<AssetNode> categories = new Array<>();
        for (AssetNode.Root root : AssetNode.Root.values()) categories.add(folder(root));
        categories.add(new AssetNode(AssetNode.Kind.TILED_ANIMATIONS_FOLDER, AssetNode.Root.TILES,
                AssetNode.TILED_ANIMATIONS_NODE_PATH, "Tile animations", null));
        for (AssetNode category : categories) {
            ProjectConfig.setInstance(cfg);
            panel.reloadFromProject(cfg);
            tree.selectFolder(category);
            assertEquals(category.root, tree.getSelectedFolder().root);
            ProjectConfig empty = new ProjectConfig();
            empty.projectFileName = " ";
            ProjectConfig.setInstance(empty);
            panel.reloadFromProject(empty);
            EventFlow.i().flush();
            assertNull(tree.getSelectedFolder());
            assertNull(field(thumbs, "currentFolder"));
            assertTrue(assets(thumbs).isEmpty());
        }
    }

    private ProjectConfig project(String name) throws Exception {
        FileHandle dir = new FileHandle(temporary.newFolder(name));
        for (String path : new String[]{StudioFs.DIR_ORIG_IMAGES, StudioFs.DIR_ORIG_ANIMATIONS,
                StudioFs.DIR_ORIG_TILES, StudioFs.DIR_ORIG_EFFECTS}) dir.child(path).mkdirs();
        new AssetMetaDatabase().save(dir.child(StudioFs.FILE_ASSETS_JSON));
        ProjectConfig cfg = new ProjectConfig();
        cfg.projectFileName = name;
        cfg.projectDirectoryPath = dir.path();
        return cfg;
    }

    private static void gameObject(ProjectConfig cfg, String name) {
        new FileHandle(cfg.projectDirectoryPath).child(StudioFs.DIR_GAME_OBJECTS)
                .child(name + ".gameobject").writeString("{}", false);
    }

    private static AssetNode folder(AssetNode.Root root) {
        return new AssetNode(AssetNode.Kind.FOLDER, root, "", root.name(), null);
    }

    @SuppressWarnings("unchecked")
    private static Array<AssetNode> assets(AssetsThumbsView thumbs) throws Exception {
        return (Array<AssetNode>) field(thumbs, "currentAssets");
    }

    private static Object field(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
}
