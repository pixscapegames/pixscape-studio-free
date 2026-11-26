package games.pixscape.studio.service.asset;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.property.PropertyType;
import games.pixscape.studio.asset.TileAssetMeta;
import games.pixscape.studio.asset.TilesetAssetMeta;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.Map;

import static org.junit.Assert.*;

public class TsxTilesetReimportTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    private final AssetMetaDatabase db = new AssetMetaDatabase();
    private final TilesetReimportService importer = new TilesetReimportService(db);
    private FileHandle source, root, catalog, first, second;

    @Before
    public void setup() throws Exception {
        GdxNativesLoader.load();
        source = new FileHandle(temp.newFolder("source"));
        root = new FileHandle(temp.newFolder("project"));
        catalog = root.child("assets.json");
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB), "png", source.child("shared.png").file());
        first = collection("first", 1);
        second = collection("second", 9);
    }

    @Test
    public void importAsNewSeparatesHomonymousCollectionsAndSparseLocalIds() {
        var one = apply(first, -1);
        var two = apply(second, -1);
        assertNotEquals(one.tilesetAssetId(), two.tilesetAssetId());
        assertNotEquals(one.localTileAssetIds().get(7), two.localTileAssetIds().get(7));
        AssetMetaDatabase saved = AssetMetaDatabase.load(catalog);
        assertTile(saved, one.localTileAssetIds().get(7), 1);
        assertTile(saved, two.localTileAssetIds().get(7), 9);
        assertEquals(two.tilesetAssetId(), importer.candidates(second, "same").get(0).id());
    }

    @Test
    public void updateUsesChosenTilesetAndKeepsItsTileIdentityAndEditedProfile() {
        var one = apply(first, -1);
        var two = apply(second, -1);
        ((TilesetAssetMeta) db.findById(one.tilesetAssetId())).offsetX = 17;
        var updated = apply(second, one.tilesetAssetId());
        assertEquals(one.tilesetAssetId(), updated.tilesetAssetId());
        assertEquals(one.localTileAssetIds(), updated.localTileAssetIds());
        assertTile(db, one.localTileAssetIds().get(7), 9);
        assertTile(db, two.localTileAssetIds().get(7), 9);
        assertEquals(17, ((TilesetAssetMeta) db.findById(one.tilesetAssetId())).offsetX);
        assertTile(AssetMetaDatabase.load(catalog), one.localTileAssetIds().get(7), 9);
    }

    @Test
    public void preparationAndCancellationDoNotChangeCatalogFilesOrAllocator() {
        var one = apply(first, -1);
        String snapshot = db.toSnapshotJson();
        byte[] saved = catalog.readBytes();
        var tile = (TileAssetMeta) db.findById(one.localTileAssetIds().get(7));
        byte[] pixels = root.child(tile.sourceRelPath()).readBytes();
        importer.prepareTsx(second, root.child("orig/tiles"), one.tilesetAssetId(), ids -> Map.of());
        importer.prepareTsx(second, root.child("orig/tiles"), -1, ids -> Map.of());
        assertEquals(snapshot, db.toSnapshotJson());
        assertArrayEquals(saved, catalog.readBytes());
        assertArrayEquals(pixels, root.child(tile.sourceRelPath()).readBytes());
        assertFalse(root.child("orig/tiles/same-2").exists());
    }

    @Test
    public void failedUpdateRestoresPropertiesAndPersistedCatalog() {
        var one = apply(first, -1);
        String snapshot = db.toSnapshotJson();
        byte[] saved = catalog.readBytes();
        var plan = importer.prepareTsx(second, root.child("orig/tiles"), one.tilesetAssetId(), ids -> Map.of());
        assertThrows(IllegalStateException.class, () -> importer.apply(plan, (candidate, colors) -> {
        }, () -> {
            assertTile(db, one.localTileAssetIds().get(7), 9);
            db.save(catalog);
            throw new IllegalStateException("Rejected publication");
        }));
        assertEquals(snapshot, db.toSnapshotJson());
        assertArrayEquals(saved, catalog.readBytes());
    }

    @Test
    public void updateKeepsDistinctDefinitionsSharingAnImage() {
        first.writeString(first.readString().replace("</tileset>",
                "<tile id=\"19\"><image source=\"shared.png\" width=\"8\" height=\"8\"/></tile></tileset>"), false);
        var one = apply(first, -1);
        var update = apply(first, one.tilesetAssetId());
        assertEquals(one.localTileAssetIds(), update.localTileAssetIds());
        assertNotEquals(update.localTileAssetIds().get(7), update.localTileAssetIds().get(19));
        assertEquals(19, ((TileAssetMeta) db.findById(update.localTileAssetIds().get(19))).sheetIndex);
        assertTile(db, update.localTileAssetIds().get(7), 1);
    }

    @Test
    public void standaloneGridAndCollectionPropertiesPersistAndAreReplacedOnReimport() {
        for (boolean collection : new boolean[]{false, true}) {
            FileHandle tsx = source.child(collection ? "paths-collection.tsx" : "paths-grid.tsx");
            String skeleton = "<tileset name='paths' tilewidth='8' tileheight='8' tilecount='1' columns='"
                    + (collection ? 0 : 1) + "'>%s"
                    + (collection ? "" : "<image source='shared.png' width='8' height='8'/>")
                    + "<tile id='0'>%s" + (collection ? "<image source='shared.png' width='8' height='8'/>" : "")
                    + "</tile></tileset>";
            tsx.writeString(skeleton.formatted(pathProperties("../missing folder/original.txt"),
                    pathProperties("../missing folder/original.txt")), false);
            var plan = importer.prepareTsx(tsx, root.child("orig/tiles"), -1, ids -> Map.of());
            assertEquals(2, plan.tsx().diagnostics().size());
            assertTrue(plan.summary(), plan.summary().contains("unknown") && plan.summary().contains("future-type")
                    && plan.summary().contains("tileset 'paths'") && plan.summary().contains("tile 0"));
            var imported = apply(tsx, -1);
            int id = imported.tilesetAssetId();
            int tileId = imported.localTileAssetIds().get(0);
            AssetMetaDatabase loaded = AssetMetaDatabase.load(catalog);
            assertPaths(((TilesetAssetMeta) loaded.findById(id)).properties, "../missing folder/original.txt");
            assertPaths(((TileAssetMeta) loaded.findById(tileId)).properties, "../missing folder/original.txt");
            AssetMetaDatabase copy = new AssetMetaDatabase();
            copy.replaceStateFrom(loaded);
            ((TilesetAssetMeta) copy.findById(id)).properties.putString("file", "edited copy");
            assertPaths(((TilesetAssetMeta) loaded.findById(id)).properties, "../missing folder/original.txt");

            tsx.writeString(skeleton.formatted(pathProperties("../elsewhere/new file.txt"),
                    pathProperties("../elsewhere/new file.txt")), false);
            var updated = apply(tsx, id);
            assertEquals(imported.localTileAssetIds(), updated.localTileAssetIds());
            loaded = AssetMetaDatabase.load(catalog);
            assertPaths(((TilesetAssetMeta) loaded.findById(id)).properties, "../elsewhere/new file.txt");
            assertPaths(((TileAssetMeta) loaded.findById(tileId)).properties, "../elsewhere/new file.txt");
            tsx.writeString(skeleton.formatted("", ""), false);
            apply(tsx, id);
            loaded = AssetMetaDatabase.load(catalog);
            assertTrue(((TilesetAssetMeta) loaded.findById(id)).properties.isEmpty());
            assertTrue(((TileAssetMeta) loaded.findById(tileId)).properties.isEmpty());
            assertFalse(source.child("missing folder").exists());
            assertFalse(root.child("missing folder").exists());
        }
    }

    private static String pathProperties(String path) {
        return "<properties><property name='file' type='file' propertytype='FileMetadata' value='" + path + "'/>"
                + "<property name='empty' type='file' value=''/><property name='noValue' type='file'/>"
                + "<property name='kept' type='int' value='12'/><property name='unknown' type='future-type'/>"
                + "<property name='nested' type='class' propertytype='Settings'><properties>"
                + "<property name='files' type='list'><item type='file' value='relative.txt'/></property>"
                + "</properties></property></properties>";
    }

    private static void assertPaths(PropertySet properties, String path) {
        assertEquals(PropertyType.STRING, properties.typeOf("file"));
        assertEquals(path, properties.getString("file", null));
        assertEquals("", properties.getString("empty", null));
        assertEquals("", properties.getString("noValue", null));
        assertEquals(12, properties.getInt("kept", 0));
        assertFalse(properties.contains("unknown"));
        assertEquals("relative.txt", properties.getClassValue("nested").properties().getListValue("files").getString(0));
    }

    private TilesetAssetImportService.TilesetImportResult apply(FileHandle tsx, int target) {
        var plan = importer.prepareTsx(tsx, root.child("orig/tiles"), target, ids -> Map.of());
        return importer.apply(plan, (candidate, colors) -> {
        }, () -> db.save(catalog));
    }

    private FileHandle collection(String file, int cost) {
        FileHandle tsx = source.child(file + ".tsx");
        tsx.writeString("<tileset name=\"same\" tilewidth=\"8\" tileheight=\"8\" tilecount=\"1\" columns=\"0\">"
                + "<properties><property name=\"globalCost\" type=\"int\" value=\"" + cost + "\"/></properties>"
                + "<tile id=\"7\"><properties><property name=\"cost\" type=\"int\" value=\"" + cost + "\"/></properties>"
                + "<image source=\"shared.png\" width=\"8\" height=\"8\"/></tile></tileset>", false);
        return tsx;
    }

    private static void assertTile(AssetMetaDatabase database, int id, int cost) {
        TileAssetMeta tile = (TileAssetMeta) database.findById(id);
        assertEquals(7, tile.sheetIndex);
        assertEquals(cost, tile.properties.getInt("cost", -1));
        assertEquals(cost, ((TilesetAssetMeta) database.findById(tile.tilesetId)).properties.getInt("globalCost", -1));
    }
}
