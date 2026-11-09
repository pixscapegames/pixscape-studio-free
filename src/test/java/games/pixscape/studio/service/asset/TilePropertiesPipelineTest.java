package games.pixscape.studio.service.asset;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.api.TileDefinition;
import games.pixscape.runtime.loading.RuntimeProjectIO;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.property.PropertyType;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfiles;
import games.pixscape.studio.asset.*;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.RuntimeExport;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.EditTilePropertiesCommand;
import games.pixscape.studio.importer.tmx.*;
import games.pixscape.studio.io.StudioFs;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class TilePropertiesPipelineTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void importEditUndoSaveReopenExportAndRuntimePreserveTypesAndSparseIds() throws Exception {
        com.badlogic.gdx.utils.GdxNativesLoader.load();
        FileHandle source = new FileHandle(temp.newFolder("source"));
        FileHandle studio = new FileHandle(temp.newFolder("studio"));
        FileHandle destination = new FileHandle(temp.newFolder("export"));
        for (String name : List.of("one", "seven"))
            ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), "png", source.child(name + ".png").file());
        FileHandle tsx = source.child("terrain.tsx");
        tsx.writeString("""
                <tileset name="terrain" tilewidth="16" tileheight="16" tilecount="2" columns="0">
                  <tile id="1"><image source="one.png" width="16" height="16"/></tile>
                  <tile id="7">
                    <properties>
                      <property name="label" value="Water"/>
                      <property name="solid" type="bool" value="false"/>
                      <property name="cost" type="int" value="3"/>
                      <property name="speed" type="float" value="0.5"/>
                      <property name="tint" type="color" value="#80123456"/>
                      <property name="file" type="file" value="../scripts/water.lua"/>
                      <property name="object" type="object" value="12"/>
                      <property name="enum" type="string" propertytype="Terrain" value="water"/>
                      <property name="custom" type="class" propertytype="Stats"><properties>
                        <property name="health" type="int" value="20"/>
                      </properties></property>
                    </properties>
                    <image source="seven.png" width="16" height="16"/>
                    <animation><frame tileid="1" duration="100"/></animation>
                  </tile>
                </tileset>
                """, false);
        TsxTilesetDescriptor descriptor = new TsxTilesetImportParser().parse(tsx);
        assertImported(descriptor.tileProperties().get(7));
        FileHandle tmx = source.child("map.tmx");
        tmx.writeString("""
                <map orientation="orthogonal" width="2" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" source="terrain.tsx"/>
                  <layer name="Ground" width="2" height="1"><data encoding="csv">8,8</data></layer>
                </map>
                """, false);
        TmxPreflightReport report = new TmxPreflightService().analyze(new TmxPreflightRequest(tmx));
        assertFalse(report.diagnostics().toString(), report.hasBlockingDiagnostics());
        assertEquals(2, report.nonEmptyTileCount());
        assertImported(report.tilesets().get(0).tileDefinitions().get(1).properties());

        AssetMetaDatabase db = new AssetMetaDatabase();
        TilesetAssetImportService service = new TilesetAssetImportService(db);
        List<TilesetAssetImportService.ImageCollectionTileSource> images = List.of(
                new TilesetAssetImportService.ImageCollectionTileSource(1, source.child("one.png"), "one.png", 16, 16),
                new TilesetAssetImportService.ImageCollectionTileSource(7, source.child("seven.png"), "seven.png", 16, 16));
        var first = service.importImageCollection(new TilesetAssetImportService.TilesetImageCollectionImportRequest(
                "terrain", studio.child(StudioFs.DIR_ORIG_TILES), 16, 16, 2, images, null));
        var second = service.importImageCollection(new TilesetAssetImportService.TilesetImageCollectionImportRequest(
                "other", studio.child(StudioFs.DIR_ORIG_TILES), 16, 16, 2, images, null));
        TilePropertiesImportSupport.apply(db, first.localTileAssetIds(), descriptor.tileProperties());
        TilePropertiesImportSupport.apply(db, second.localTileAssetIds(), Map.of(7, new PropertySet().putString("label", "Other")));
        TileAnimationsMetaDatabase animations = games.pixscape.studio.io.TileAnimationsIO.createEmpty();
        int animationId = TiledAnimationImportSupport.importTileAnimations(db, animations, "terrain",
                descriptor.tileAnimations(), first.localTileAssetIds()).get(7);
        games.pixscape.studio.io.TileAnimationsIO.save(animations, studio.child(games.pixscape.runtime.helper.RuntimeFs.FILE_TILE_ANIMATIONS_JSON));
        int tileId = first.localTileAssetIds().get(7);
        TileAssetMeta tile = (TileAssetMeta) db.findById(tileId);
        assertEquals(7, tile.sheetIndex);
        PropertySet original = tile.properties.copy();
        FileHandle assets = studio.child(StudioFs.FILE_ASSETS_JSON);
        db.save(assets);
        HistoryManager history = new HistoryManager(20);
        AtomicInteger publications = new AtomicInteger();
        PropertySet added = original.copy().putString("added", "value");
        history.execute(new EditTilePropertiesCommand(db, tileId, added, () -> assets, ignored -> publications.incrementAndGet()));
        PropertySet modified = added.copy().putInt("cost", 9);
        history.execute(new EditTilePropertiesCommand(db, tileId, modified, () -> assets, ignored -> publications.incrementAndGet()));
        PropertySet removed = modified.copy(); removed.remove("label");
        history.execute(new EditTilePropertiesCommand(db, tileId, removed, () -> assets, ignored -> publications.incrementAndGet()));
        assertFalse(tile.properties.contains("label"));
        history.undo(); assertEquals("Water", tile.properties.getString("label", ""));
        history.undo(); assertEquals(3, tile.properties.getInt("cost", 0));
        history.undo(); assertFalse(tile.properties.contains("added"));
        assertImported(tile.properties);
        history.redo(); history.redo(); history.redo();
        assertEquals(9, publications.get());
        AssetMetaDatabase reopened = AssetMetaDatabase.load(assets);
        TileAssetMeta saved = (TileAssetMeta) reopened.findById(tileId);
        assertEquals(7, saved.sheetIndex);
        assertEquals(9, saved.properties.getInt("cost", 0));
        assertFalse(saved.properties.contains("label"));
        assertEquals(original.getImportedValue("file").sourceXml(), saved.properties.getImportedValue("file").sourceXml());
        assertEquals(20, saved.properties.getClassValue("custom").properties().getInt("health", 0));
        assertEquals("Other", ((TileAssetMeta) reopened.findById(second.localTileAssetIds().get(7))).properties.getString("label", ""));

        ProjectConfig config = new ProjectConfig();
        config.projectTitle = "Tile properties"; config.projectFileName = "properties";
        config.exportRootPathDir = destination.path(); config.createSceneMeta("Main");
        studio.child(StudioFs.DIR_SCENES).child(config.getCurrentSceneMeta().getFile()).writeString("{}", false);
        // Only the animation is explicitly reachable: its base definition must travel with it.
        config.getCurrentSceneMeta().runtimeAvailability.tiledAnimationIds.add(animationId);
        config.getCurrentSceneMeta().runtimeAvailability.tiledTileAssetIds.add(second.localTileAssetIds().get(7));
        RuntimeExport.exportRuntime(config, studio, destination);
        RuntimeTilesetProfiles profiles = RuntimeProjectIO.loadTilesetProfiles(destination.child(RuntimeExport.RUNTIME_DIR_NAME));
        var registry = new games.pixscape.runtime.service.TileAnimationRegistry();
        RuntimeProjectIO.loadTileAnimations(destination.child(RuntimeExport.RUNTIME_DIR_NAME), registry);
        assertEquals(tileId, registry.get(animationId).baseTileAssetId());
        TileDefinition loaded = profiles.tile(first.tilesetAssetId(), 7);
        assertNotNull(loaded);
        assertEquals(tileId, loaded.assetId());
        assertEquals(9, loaded.properties().getInt("cost", 0));
        assertEquals(0.5f, loaded.properties().getFloat("speed", 0), 0);
        assertFalse(loaded.properties().getBoolean("solid", true));
        assertEquals(0x12345680, loaded.properties().getColorRgba8888("tint", 0));
        assertEquals(original.getImportedValue("object").sourceXml(), loaded.properties().getImportedValue("object").sourceXml());
        assertEquals("Terrain", loaded.properties().getImportedValue("enum").customTypeName());
        assertEquals(20, loaded.properties().getClassValue("custom").properties().getInt("health", 0));
        assertEquals("Other", profiles.tile(second.tilesetAssetId(), 7).properties().getString("label", ""));
        assertNull(profiles.tile(first.tilesetAssetId(), 0));
    }

    private static void assertImported(PropertySet properties) {
        assertEquals("Water", properties.getString("label", ""));
        assertFalse(properties.getBoolean("solid", true));
        assertEquals(3, properties.getInt("cost", 0));
        assertEquals(0.5f, properties.getFloat("speed", 0), 0);
        assertEquals(PropertyType.IMPORTED, properties.typeOf("file"));
        assertEquals("file", properties.getImportedValue("file").typeName());
        assertEquals("object", properties.getImportedValue("object").typeName());
        assertEquals("Terrain", properties.getImportedValue("enum").customTypeName());
    }
}
