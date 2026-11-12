package games.pixscape.studio.ui.property;

import com.badlogic.gdx.files.FileHandle;
import com.kotcrab.vis.ui.widget.VisLabel;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.studio.asset.*;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class TileAssetPropertiesTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @BeforeClass
    public static void load() {
        VisUiTestBootstrap.loadSkin();
    }

    @AfterClass
    public static void unload() {
        VisUiTestBootstrap.unloadSkin();
    }

    @Test
    public void inspectionIsReadOnlyAndAssetHistorySurvivesSelectionButNotProjectReplacement() throws Exception {
        AssetMetaDatabase db = new AssetMetaDatabase();
        TileAssetMeta first = tile(db, "first", 7);
        TileAssetMeta second = tile(db, "second", 7);
        AtomicReference<AssetMetaDatabase> project = new AtomicReference<>(db);
        FileHandle file = new FileHandle(temp.newFolder("assets")).child("assets.json");
        AtomicInteger published = new AtomicInteger();
        TileAssetProperties inspector = new TileAssetProperties(project::get, () -> file, ignored -> published.incrementAndGet());
        String snapshot = db.toSnapshotJson();
        inspector.select(first.id());
        assertEquals("7", ((VisLabel) inspector.findActor("tileLocalId")).getText().toString());
        assertTrue(((VisLabel) inspector.findActor("tileTileset")).getText().toString().contains("tiles/first"));
        assertEquals(snapshot, db.toSnapshotJson());
        assertFalse(file.exists());
        inspector.apply(first.id(), new PropertySet().putInt("cost", 4));
        inspector.select(second.id());
        assertTrue(inspector.canUndo());
        assertTrue(inspector.undo());
        assertFalse(first.properties.contains("cost"));
        assertTrue(inspector.redo());
        assertEquals(4, first.properties.getInt("cost", 0));
        assertTrue(second.properties.isEmpty());
        assertEquals(3, published.get());
        inspector.apply(first.id(), first.properties.copy());
        assertEquals(3, published.get()); // No-op does not write or publish.
        project.set(new AssetMetaDatabase());
        assertFalse(inspector.canUndo());
        inspector.select(first.id());
        assertFalse(inspector.canRedo());
    }

    @Test
    public void failedAssetSaveLeavesContentHistoryAndPublishedMetadataUnchanged() throws Exception {
        AssetMetaDatabase db = new AssetMetaDatabase();
        TileAssetMeta tile = tile(db, "first", 1);
        FileHandle regularFile = new FileHandle(temp.newFile("not-a-directory"));
        AtomicInteger published = new AtomicInteger();
        TileAssetProperties inspector = new TileAssetProperties(() -> db,
                () -> regularFile.child("assets.json"), ignored -> published.incrementAndGet());
        inspector.select(tile.id());
        assertThrows(RuntimeException.class, () -> inspector.apply(tile.id(), new PropertySet().putInt("cost", 4)));
        assertTrue(tile.properties.isEmpty());
        assertFalse(inspector.canUndo());
        assertEquals(0, published.get());
    }

    private static TileAssetMeta tile(AssetMetaDatabase db, String name, int localId) {
        var set = db.registerIfAbsent(AssetType.TILESET, "tiles/" + name, null, AssetMeta.AssetScope.USER);
        TileAssetMeta tile = (TileAssetMeta) db.registerIfAbsent(AssetType.TILE, "tiles/" + name + "/" + localId, null, AssetMeta.AssetScope.USER);
        tile.tilesetId = set.id();
        tile.sheetIndex = localId;
        return tile;
    }
}
