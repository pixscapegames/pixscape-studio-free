package games.pixscape.studio.importer.tmx;

import com.artemis.*;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import games.pixscape.runtime.api.ClassProperty;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.helper.OrientedBoundsHelper;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.property.PropertyType;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.TagRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.UpdateWorldGeometrySystem;
import games.pixscape.runtime.tiled.TileTransformFlags;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.studio.asset.*;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.RuntimeExport;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.AddTiledMapCommand;
import games.pixscape.studio.history.commands.GizmoTransformCommand;
import games.pixscape.studio.history.commands.ToggleTiledMapSpatialDepthCommand;
import games.pixscape.studio.history.commands.TransformOp;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.io.TileAnimationsIO;
import games.pixscape.studio.model.EntityKind;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.StudioEditingMode;
import games.pixscape.studio.service.StudioEditingModeService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class TmxSceneImportServiceTest {

    @Test
    public void globalTilesetPropertiesSurviveImportReloadTsxReimportExportAndPublicRuntimeApi() throws Exception {
        for (boolean collection : new boolean[]{false, true}) {
            Harness h = harness("tileset-global-pipeline");
            FileHandle map = TmxPropertyImportTest.fixture(h.projectDir, true, collection);
            FileHandle tsx = h.projectDir.child("sets/terrain.tsx");
            String original = h.projectDir.child("terrain.tsx").readString()
                    .replace("source='terrain.png'", "source='../terrain.png'");
            int tileStart = original.indexOf("<tile id=");
            original = original.substring(0, tileStart)
                    + original.substring(tileStart).replace("value=\"42\"", "value=\"7\"");
            tsx.writeString(original, false);
            map.writeString(map.readString().replace("source='terrain.tsx'", "source='sets/terrain.tsx'"), false);
            var imported = h.importer().importScene(new TmxSceneImportRequest(map, "Imported", false));
            assertTrue(String.valueOf(imported.failure()), imported.imported());
            int tilesetId = requireTileset(h.db.findByLogicalPath("tiles/terrain")).id();
            FileHandle catalog = h.projectDir.child(StudioFs.FILE_ASSETS_JSON);
            AssetMetaDatabase reopened = AssetMetaDatabase.load(catalog);

            // Independent TSX import: this tileset stays outside scene reachability and export.
            var reimporter = new games.pixscape.studio.service.asset.TilesetReimportService(reopened);
            var unusedPlan = reimporter.prepareTsx(tsx, h.projectDir.child(StudioFs.DIR_ORIG_TILES),
                    -1, ids -> java.util.Map.of());
            var unused = reimporter.apply(unusedPlan, (candidate, colors) -> {}, () -> reopened.save(catalog));
            assertNotEquals(tilesetId, unused.tilesetAssetId());

            var engine = new games.pixscape.runtime.engine.PixscapeEngine();
            var api = engine.api().tiled();
            for (int revision = 0; revision < 3; revision++) {
                String expectedPath = revision == 0 ? "../scripts/missing.lua" : "../new folder/updated.lua";
                if (revision > 0) {
                    String updated = original.replace("../scripts/missing.lua", expectedPath)
                            .replace("value=\"42\"", "value=\"84\"");
                    if (revision == 2) {
                        int start = updated.indexOf("<properties>");
                        int end = updated.indexOf("</properties>") + "</properties>".length();
                        updated = updated.substring(0, start) + updated.substring(end);
                    }
                    tsx.writeString(updated, false);
                    var plan = reimporter.prepareTsx(tsx, h.projectDir.child(StudioFs.DIR_ORIG_TILES),
                            tilesetId, ids -> java.util.Map.of());
                    var result = reimporter.apply(plan, (candidate, colors) -> {}, () -> reopened.save(catalog));
                    assertEquals(tilesetId, result.tilesetAssetId());
                }
                AssetMetaDatabase saved = AssetMetaDatabase.load(catalog);
                var stored = ((TilesetAssetMeta) saved.findById(tilesetId)).properties;
                assertEquals(revision == 2, stored.isEmpty());
                if (revision < 2) {
                    assertEquals(expectedPath, stored.getString("relative", null));
                }
                var preview = games.pixscape.studio.service.tiled.StudioTilesetProfileResolver.buildRuntimeProfiles(saved);
                assertEquals(stored.size(), preview.tilesetProperties(tilesetId).size());
                FileHandle output = h.projectDir.child("export");
                RuntimeExport.exportRuntime(h.cfg, h.projectDir, output);
                var loaded = games.pixscape.runtime.loading.RuntimeProjectIO.loadTilesetProfiles(
                        output.child(RuntimeExport.RUNTIME_DIR_NAME));
                engine.getTilesetProfiles().clear();
                for (var profile : loaded.profiles()) {
                    engine.getTilesetProfiles().add(profile);
                }
                assertNull(api.tilesetProperties(unused.tilesetAssetId()));
                assertNull(api.tilesetProperties(Integer.MAX_VALUE));
                var global = api.tilesetProperties(tilesetId);
                assertNotNull(global);
                assertEquals(revision == 2, global.isEmpty());
                if (revision < 2) {
                    assertRuntimeFilePaths(global, expectedPath);
                    assertEquals(revision == 0 ? 42 : 84, global.getInt("kept", 0));
                }
                var tile = api.tile(tilesetId, 0);
                assertNotNull(tile);
                assertRuntimeFilePaths(tile.properties(), expectedPath);
                assertEquals(7, tile.properties().getInt("kept", 0));
            }
            assertFalse(h.projectDir.child("scripts/missing.lua").exists());
            assertFalse(h.projectDir.child("new folder/updated.lua").exists());
        }
    }

    private static void assertRuntimeFilePaths(games.pixscape.runtime.api.CustomProperties properties, String relative) {
        for (String key : List.of("relative", "spaces", "empty", "noValue", "text", "customFile")) {
            assertEquals(key, PropertyType.STRING, properties.typeOf(key));
        }
        assertEquals(relative, properties.getString("relative", null));
        assertEquals("folder with spaces/missing file.txt", properties.getString("spaces", null));
        assertEquals("", properties.getString("empty", null));
        assertEquals("", properties.getString("noValue", null));
        assertEquals("../text path.txt", properties.getString("text", null));
        assertEquals("missing.dat", properties.getString("customFile", null));
        assertFalse(properties.contains("unknown"));
    }

    @Test
    public void aSkippedCollectionStillFailsAndRollsBack() throws Exception {
        Harness h = harness("tmx-skipped-collection");
        FileHandle map = TmxPropertyImportTest.fixture(h.projectDir, true, true);
        h.projectDir.child("terrain.png").copyTo(h.projectDir.child("terrain_n.png"));
        FileHandle tsx = h.projectDir.child("terrain.tsx");
        tsx.writeString(tsx.readString().replace("terrain.png", "terrain_n.png"), false);
        String catalog = h.db.toSnapshotJson();
        var result = h.importer().importScene(new TmxSceneImportRequest(map, "Skipped", false));
        assertEquals(TmxSceneImportStatus.FAILED_ROLLED_BACK, result.status());
        assertTrue(result.rollbackSucceeded());
        assertEquals("Tileset import failed: terrain", result.failure().getMessage());
        assertNull(h.cfg.getSceneMeta("Skipped"));
        assertEquals(catalog, h.db.toSnapshotJson());
    }

    @Test
    public void reimportReusesCollectionTilesAfterTheImportedSceneWasDeleted() throws Exception {
        Harness h = harness("tmx-reimport-collection");
        FileHandle map = TmxPropertyImportTest.fixture(h.projectDir, true, true);
        var first = h.importer().importScene(new TmxSceneImportRequest(map, "Imported", false));
        assertTrue(String.valueOf(first.failure()), first.imported());
        AssetMetaDatabase saved = AssetMetaDatabase.load(h.projectDir.child(StudioFs.FILE_ASSETS_JSON));
        for (int i = 0; i < saved.size(); i++) {
            if (saved.assetAt(i) instanceof TilesetAssetMeta tileset) {
                TmxPropertyImportTest.assertProperties(tileset.properties);
            }
        }
        String catalog = h.db.toSnapshotJson();
        int nextAssetId = h.db.nextId();
        for (int cycle = 0; cycle < 3; cycle++) {
            games.pixscape.studio.service.ProjectFileCleanupService.deleteFileAndBackups(
                    h.projectDir.child(StudioFs.DIR_SCENES).child(first.sceneFileName()));
            games.pixscape.studio.service.ProjectFileCleanupService.deleteSceneAtlasFiles(h.projectDir, first.sceneTag());
            games.pixscape.studio.service.ProjectFileCleanupService.deleteSceneAtlasInput(h.projectDir, first.sceneTag());
            h.cfg.removeSceneMeta(first.sceneName());
            ProjectConfig.ProjectIO.saveProject(h.cfg, StudioFs.requireStudioProjectFile(h.cfg));

            var imported = h.importer().importScene(new TmxSceneImportRequest(map, "Imported", false));
            assertTrue(String.valueOf(imported.failure()), imported.imported());
            assertFalse(imported.rollbackAttempted());
            assertEquals(catalog, h.db.toSnapshotJson());
            assertEquals(nextAssetId, h.db.nextId());
            World world = loadImportedWorld(h, imported);
            try {
                var layers = world.getAspectSubscriptionManager().get(Aspect.all(TiledLayerComponent.class)).getEntities();
                assertEquals(1, layers.size());
                assertEquals(2, imported.importedCellCount());
                var owners = world.getAspectSubscriptionManager().get(Aspect.all(CustomPropertiesComponent.class)).getEntities();
                for (int i = 0; i < owners.size(); i++) {
                    TmxPropertyImportTest.assertProperties(ownerProperties(world, owners.get(i)));
                }
            } finally {
                world.dispose();
            }
            first = imported;
        }
    }

    @Test
    public void filePropertiesAndWarningsSurviveRealImportAndReachTheUserResult() throws Exception {
        Harness h = harness("file-properties");
        FileHandle map = TmxPropertyImportTest.fixture(h.projectDir, true, true);
        var result = h.importer().importScene(new TmxSceneImportRequest(map, "File properties", false));
        assertTrue(String.valueOf(result.failure()), result.imported());
        assertEquals(7, games.pixscape.studio.ui.importer.TmxImportUiSupport.warningCount(result.diagnostics()));
        assertTrue(games.pixscape.studio.ui.importer.TmxImportUiSupport.formatSuccessMessage(result).contains("7 warnings"));
        String shown = games.pixscape.studio.ui.importer.TmxImportUiSupport.formatDiagnostics(result.diagnostics());
        assertTrue(shown, shown.contains("unknown") && shown.contains("future-type") && shown.contains("tile 0"));
        World world = loadImportedWorld(h, result);
        try {
            var owners = world.getAspectSubscriptionManager().get(Aspect.all(CustomPropertiesComponent.class)).getEntities();
            assertEquals(6, owners.size());
            for (int i = 0; i < owners.size(); i++) {
                TmxPropertyImportTest.assertProperties(ownerProperties(world, owners.get(i)));
            }
        } finally {
            world.dispose();
        }
        assertFalse(h.projectDir.child("scripts/missing.lua").exists());
        assertFalse(h.projectDir.child("folder with spaces").exists());
    }

    @Test
    public void listEnumsSurviveRealImportEditDeleteCopyExportAndRuntimeApi() throws Exception {
        for (String projection : new String[]{"orthogonal", "isometric"}) {
            Harness h = harness("list-enums-" + projection);
            String fixture = "src/test/resources/tmx/list-enums/";
            var imported = h.importer().importScene(new TmxSceneImportRequest(new FileHandle(fixture + projection + ".tmx"),
                    "List enums", false, new FileHandle(fixture + "types.tiled-project")));
            assertTrue(String.valueOf(imported.failure()), imported.imported());
            World world = loadImportedWorld(h, imported);
            IdentityRegistry ids = new IdentityRegistry();
            ids.bind(world, h.cfg.getSceneMeta(imported.sceneName()));
            ids.rebuild();
            HistoryManager history = new HistoryManager(64);
            try {
                int map = world.getAspectSubscriptionManager().get(Aspect.all(TiledMapComponent.class)).getEntities().get(0);
                int actor = ids.firstByName("Actor");
                int target = stableId(world, ids.firstByName("Target"));
                int actorId = stableId(world, actor);
                int[] owners = {map, ids.firstByName("Ground"), ids.firstByName("Backdrop"), ids.firstByName("Objects"), actor};
                int[] stable = new int[owners.length];
                for (int i = 0; i < owners.length; i++) {
                    stable[i] = stableId(world, owners[i]);
                    TmxListEnumImportTest.assertValues(ownerProperties(world, owners[i]), target, actorId);
                }
                var before = ownerProperties(world, actor).copy();
                var after = before.copy().putList("items", new com.badlogic.gdx.utils.Array<>());
                history.execute(new games.pixscape.studio.history.commands.EditCustomPropertiesCommand(
                        world, history.historyIds(), history.historyIds().ensureForEntity(actor), true, before, after, 0, null));
                for (int i = 0; i < 3; i++) {
                    assertEquals(0, ownerProperties(world, actor).getListValue("items").size());
                    history.undo();
                    TmxListEnumImportTest.assertValues(ownerProperties(world, actor), target, actorId);
                    history.redo();
                }
                history.undo();
                var graph = new games.pixscape.studio.service.entitygraph.EntityGraphCaptureService(world)
                        .capture(com.badlogic.gdx.utils.IntArray.with(map));
                var copy = new games.pixscape.studio.service.entitygraph.EntityGraphInstantiationService(world, history, ids, null, () -> false)
                        .instantiate(graph, 0, 32, 0, "Copy list map");
                world.process();
                int copyActor = copiedEntityNamed(world, copy.createdIds(), "Actor");
                int copyTarget = copiedEntityNamed(world, copy.createdIds(), "Target");
                TmxListEnumImportTest.assertValues(ownerProperties(world, copyActor), stableId(world, copyTarget), stableId(world, copyActor));
                ownerProperties(world, copyActor).putList("items", new com.badlogic.gdx.utils.Array<>());
                TmxListEnumImportTest.assertValues(ownerProperties(world, actor), target, actorId);
                history.undo();
                world.process();
                history.execute(games.pixscape.studio.history.commands.DeleteEntitiesCommandFactory.create(
                        world, history.historyIds(), com.badlogic.gdx.utils.IntArray.with(map), null));
                for (int cycle = 0; cycle < 3; cycle++) {
                    world.process();
                    assertEquals(-1, ids.findByStableId(stable[0]));
                    history.undo();
                    world.process();
                    for (int id : stable) {
                        TmxListEnumImportTest.assertValues(ownerProperties(world, ids.findByStableId(id)), target, actorId);
                    }
                    history.redo();
                }
                history.undo();
                world.process();
                FileHandle scene = h.projectDir.child(StudioFs.DIR_SCENES).child(imported.sceneFileName());
                games.pixscape.studio.service.SceneService.saveScene(world, scene, false);
                World reopened = loadImportedWorld(h, imported);
                try {
                    IdentityRegistry reopenedIds = new IdentityRegistry();
                    reopenedIds.bind(reopened, h.cfg.getSceneMeta(imported.sceneName()));
                    reopenedIds.rebuild();
                    for (int id : stable) {
                        TmxListEnumImportTest.assertValues(ownerProperties(reopened, reopenedIds.findByStableId(id)), target, actorId);
                    }
                } finally { reopened.dispose(); }
                ProjectConfig.ProjectIO.saveProject(h.cfg, StudioFs.requireStudioProjectFile(h.cfg));
                FileHandle export = new FileHandle(h.root.resolve("runtime-export").toFile());
                var config = RuntimeExport.exportRuntime(h.cfg, h.projectDir, export);
                World runtime = new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
                var engine = new games.pixscape.runtime.engine.PixscapeEngine();
                var field = engine.getClass().getDeclaredField("world");
                field.setAccessible(true);
                field.set(engine, runtime);
                var meta = config.scenes.get(imported.sceneName());
                engine.getIdentityRegistry().bind(runtime, meta);
                try {
                    SceneLoader.loadScene(runtime, export.child(RuntimeExport.RUNTIME_DIR_NAME)
                            .child(RuntimeFs.DIR_SCENES).child(imported.sceneFileName()), false, meta);
                    runtime.process();
                    // Public typed API examples compiled and exercised after real export and SceneLoader.
                    var entities = engine.api().entities();
                    for (int id : stable) {
                        var properties = entities.requireStableId(id).properties();
                        assertEquals(16777217d, properties.getDouble("precise", 0), 0);
                        var list = properties.getListValue("items");
                        assertEquals(8, list.size());
                        assertEquals(PropertyType.STRING, list.typeAt(0));
                        assertEquals("7", list.getString(0));
                        assertEquals(7, list.getInt(1));
                        assertEquals(16777217d, list.getDouble(3), 0);
                        assertEquals("North", list.getListValue(6).getEnumValue(1).stringValue());
                        assertEquals(actorId, list.getClassValue(7).properties().getListValue("targets").getObjectStableId(0));
                        assertEquals(target, list.getObjectStableId(4));
                        assertNotNull(entities.requireStableId(list.getObjectStableId(4)));
                        assertEquals("West", properties.getEnumValue("direction").stringValue());
                        assertEquals(5, properties.getEnumValue("flags").intValue());
                    }
                } finally {
                    engine.getIdentityRegistry().bind(null, null);
                    runtime.dispose();
                }
                new FileHandle("build/list-enums-" + projection + "-fixture-path.txt").writeString(h.root.toString(), false);
            } finally {
                ids.bind(null, null);
                world.dispose();
            }
        }
    }

    @Test
    public void invalidListItemsAndEnumsLeaveProjectAndCatalogUntouched() throws Exception {
        Harness h = harness("tmx-invalid-list-enums");
        for (String item : new String[]{"<item type='object' value='999'/>", "<item propertytype='Missing' value='X'/>",
                "<item propertytype='Direction' value='Unknown'/>", "<item type='int' propertytype='Flags' value='8'/>"}) {
            FileHandle tmx = writeTmx(h.root.resolve("invalid.tmx"),
                    "<map orientation='orthogonal' width='1' height='1' tilewidth='16' tileheight='16'>"
                    + "<properties><property name='items' type='list'>" + item + "</property></properties></map>");
            var before = projectFileContents(h.root);
            var json = new com.badlogic.gdx.utils.Json();
            String config = json.toJson(h.cfg);
            String catalog = json.toJson(h.db);
            var result = h.importer().importScene(new TmxSceneImportRequest(tmx, "Rejected", false,
                    new FileHandle("src/test/resources/tmx/list-enums/types.tiled-project")));
            assertFalse(result.imported());
            assertFalse(result.rollbackAttempted());
            assertEquals(before, projectFileContents(h.root));
            assertEquals(config, json.toJson(h.cfg));
            assertEquals(catalog, json.toJson(h.db));
            assertTrue(result.planResult().preflightReport().diagnostics().stream().anyMatch(d ->
                    d.severity() == TmxDiagnosticSeverity.BLOCKING && d.location().contains("items")
                            && d.location().contains("[0]")));
        }
    }

    @Test
    public void invalidTiledOwnerPropertiesFailBeforeProjectPublication() throws Exception {
        Harness h = harness("tmx-invalid-owner-properties");
        String[][] invalid = {
                {"<property name=\"bad\" type=\"list\" value=\"1,2\"/>", "TMX_PROPERTY_VALUE_INVALID", "LIST"},
                {"<property name=\"bad\" propertytype=\"Direction\" value=\"North\"/>", "TMX_PROJECT_REQUIRED", "Direction"},
                {"<property name=\"bad\" type=\"object\" value=\"9999\"/>", "TMX_OBJECT_REFERENCE_UNRESOLVED", "9999"},
                {"<property name=\"bad\" type=\"object\" value=\"2002\"/>", "TMX_OBJECT_REFERENCE_TARGET_UNSUPPORTED", "2002"},
                {"<property name=\"nested\" type=\"class\" propertytype=\"Config\"><properties>"
                        + "<property name=\"bad\" type=\"object\" value=\"9999\"/>"
                        + "</properties></property>", "TMX_OBJECT_REFERENCE_UNRESOLVED", "9999"}
        };
        for (String owner : new String[]{"map", "tile", "image"}) {
            for (String[] error : invalid) {
                String properties = "<properties>" + error[0] + "</properties>";
                FileHandle tmx = writeTmx(h.root.resolve("invalid.tmx"), """
                        <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                          %s
                          <layer name="Ground" width="1" height="1">%s<data encoding="csv">0</data></layer>
                          <imagelayer name="Backdrop">%s<image source="terrain.png" width="32" height="32"/></imagelayer>
                          <objectgroup name="Objects"><object id="2002" width="8" height="8"><ellipse/></object></objectgroup>
                        </map>
                        """.formatted(owner.equals("map") ? properties : "",
                                owner.equals("tile") ? properties : "", owner.equals("image") ? properties : ""));
                java.util.Map<String, String> filesBefore = projectFileContents(h.root);
                String configBefore = new com.badlogic.gdx.utils.Json().toJson(h.cfg);
                TmxSceneImportResult result = h.importer().importScene(request(tmx, "Rejected"));
                assertFalse(result.imported());
                assertFalse(result.rollbackAttempted());
                String location = owner.equals("map") ? "map" : owner.equals("tile") ? "Ground" : "Backdrop";
                assertTrue(result.planResult().preflightReport().diagnostics().stream().anyMatch(d ->
                        d.code().equals(error[1]) && d.location().contains(location)
                                && d.location().contains("bad") && d.message().contains(error[2])));
                assertEquals(filesBefore, projectFileContents(h.root));
                assertEquals(configBefore, new com.badlogic.gdx.utils.Json().toJson(h.cfg));
            }
        }
    }

    private static java.util.Map<String, String> projectFileContents(Path root) throws Exception {
        java.util.Map<String, String> result = new java.util.TreeMap<>();
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                result.put(root.relativize(file).toString(), java.util.Base64.getEncoder().encodeToString(Files.readAllBytes(file)));
            }
        }
        return result;
    }

    @Test
    public void independentlyImportedMapsWithSameTiledObjectIdsRemainIndependentInOneScene() throws Exception {
        Harness h = harness("tmx-two-owner-domains");
        TmxSceneImportResult first = h.importer().importScene(request(new FileHandle(
                "src/test/resources/tmx/owner-properties/orthogonal.tmx"), "First"));
        TmxSceneImportResult second = h.importer().importScene(request(new FileHandle(
                "src/test/resources/tmx/owner-properties/isometric.tmx"), "Second"));
        assertTrue(first.imported());
        assertTrue(second.imported());
        World firstWorld = loadImportedWorld(h, first);
        World secondWorld = loadImportedWorld(h, second);
        IdentityRegistry firstIds = new IdentityRegistry();
        IdentityRegistry secondIds = new IdentityRegistry();
        firstIds.bind(firstWorld, h.cfg.getSceneMeta(first.sceneName()));
        secondIds.bind(secondWorld, h.cfg.getSceneMeta(second.sceneName()));
        firstIds.rebuild();
        secondIds.rebuild();
        try {
            int firstMap = firstWorld.getAspectSubscriptionManager().get(Aspect.all(TiledMapComponent.class)).getEntities().get(0);
            int secondMap = secondWorld.getAspectSubscriptionManager().get(Aspect.all(TiledMapComponent.class)).getEntities().get(0);
            int firstDoor = stableId(firstWorld, firstIds.firstByName("Door"));
            int secondDoor = stableId(secondWorld, secondIds.firstByName("Door"));
            assertEquals(firstDoor, ownerProperties(firstWorld, firstMap).getObjectStableId("target", -1));
            assertEquals(secondDoor, ownerProperties(secondWorld, secondMap).getObjectStableId("target", -1));
            var graph = new games.pixscape.studio.service.entitygraph.EntityGraphCaptureService(secondWorld)
                    .capture(com.badlogic.gdx.utils.IntArray.with(secondMap));
            var copy = new games.pixscape.studio.service.entitygraph.EntityGraphInstantiationService(
                    firstWorld, new HistoryManager(16), firstIds, null, () -> false)
                    .instantiate(graph, 0, 0, 0, "Paste second map");
            firstWorld.process();
            int pastedDoor = stableId(firstWorld, copiedEntityNamed(firstWorld, copy.createdIds(), "Door"));
            assertNotEquals(firstDoor, pastedDoor);
            assertEquals(firstDoor, ownerProperties(firstWorld, firstMap).getObjectStableId("target", -1));
            for (String owner : new String[]{"Ground", "Backdrop"}) {
                int pastedOwner = copiedEntityNamed(firstWorld, copy.createdIds(), owner);
                assertEquals(pastedDoor, ownerProperties(firstWorld, pastedOwner).getObjectStableId("target", -1));
            }
            assertEquals(pastedDoor, ownerProperties(firstWorld, copy.createdRootIds().first()).getObjectStableId("target", -1));
            assertEquals(secondDoor, ownerProperties(secondWorld, secondMap).getObjectStableId("target", -1));
        } finally {
            firstIds.bind(null, null);
            secondIds.bind(null, null);
            firstWorld.dispose();
            secondWorld.dispose();
        }
    }

    @Test
    public void tiledOwnerPropertiesSurviveHistoryCopySceneAndPublicRuntimeLoading() throws Exception {
        for (String orientation : new String[]{"orthogonal", "isometric"}) {
            Harness h = harness("tmx-owner-properties-" + orientation);
            FileHandle fixture = new FileHandle("src/test/resources/tmx/owner-properties/" + orientation + ".tmx");
            TmxSceneImportResult imported = h.importer().importScene(request(fixture, "Owner properties"));
            assertTrue(String.valueOf(imported.failure()), imported.imported());
            World world = loadImportedWorld(h, imported);
            IdentityRegistry identities = new IdentityRegistry();
            identities.bind(world, h.cfg.getSceneMeta(imported.sceneName()));
            identities.rebuild();
            HistoryManager history = new HistoryManager(64);
            try {
                int map = world.getAspectSubscriptionManager().get(Aspect.all(TiledMapComponent.class)).getEntities().get(0);
                int[] owners = {map, identities.firstByName("Ground"), identities.firstByName("Backdrop"),
                        identities.firstByName("Gameplay"), identities.firstByName("Switch"), identities.firstByName("Door")};
                String[] labels = {"map", "tile", "image", "objects", "switch", "door"};
                int[] stableIds = new int[owners.length];
                int door = stableId(world, owners[5]);
                int button = stableId(world, owners[4]);
                assertNotEquals(2002, door);
                assertNotEquals(1001, button);
                for (int i = 0; i < owners.length; i++) {
                    stableIds[i] = stableId(world, owners[i]);
                    assertOwnerProperties(ownerProperties(world, owners[i]), labels[i], door, button);
                    if (i < 4) {
                        assertFalse(world.getMapper(PhysicsBodyComponent.class).has(owners[i]));
                        assertFalse(world.getMapper(games.pixscape.runtime.component.spatial.SpatialFootprintComponent.class).has(owners[i]));
                    }
                }
                assertFalse(world.getMapper(CustomPropertiesComponent.class).has(identities.firstByName("Empty")));
                assertEquals(9, imported.planResult().plan().tilesets().get(0).tileDefinitions().get(0)
                        .properties().getInt("cost", -1));

                // The existing command edits these structural owners without adding actor capabilities.
                for (int i = 0; i < 3; i++) {
                    PropertySet before = ownerProperties(world, owners[i]).copy();
                    PropertySet after = before.copy().putString("label", "edited-" + labels[i]);
                    var command = new games.pixscape.studio.history.commands.EditCustomPropertiesCommand(
                            world, history.historyIds(), history.historyIds().ensureForEntity(owners[i]),
                            true, before, after, 0, null);
                    history.execute(command);
                    for (int cycle = 0; cycle < 3; cycle++) {
                        assertEquals("edited-" + labels[i], ownerProperties(world, owners[i]).getString("label", null));
                        history.undo();
                        assertOwnerProperties(ownerProperties(world, owners[i]), labels[i], door, button);
                        history.redo();
                    }
                    history.undo();
                }

                // Capture the complete map: internal OBJECT paths are remapped, including nested CLASS members.
                var graph = new games.pixscape.studio.service.entitygraph.EntityGraphCaptureService(world)
                        .capture(com.badlogic.gdx.utils.IntArray.with(map));
                var copy = new games.pixscape.studio.service.entitygraph.EntityGraphInstantiationService(
                        world, history, identities, null, () -> false).instantiate(graph, 0, 32, 0, "Copy map");
                world.process();
                int copiedDoor = copiedEntityNamed(world, copy.createdIds(), "Door");
                int copiedSwitch = copiedEntityNamed(world, copy.createdIds(), "Switch");
                int copiedMap = copy.createdRootIds().first();
                for (int i = 0; i < 3; i++) {
                    int entity = i == 0 ? copiedMap : copiedEntityNamed(world, copy.createdIds(), i == 1 ? "Ground" : "Backdrop");
                    PropertySet properties = ownerProperties(world, entity);
                    assertOwnerProperties(properties, labels[i], stableId(world, copiedDoor), stableId(world, copiedSwitch));
                    properties.putString("label", "copy only");
                    properties.putClass("settings", "Settings", new PropertySet().putInt("changed", 7));
                    assertOwnerProperties(ownerProperties(world, owners[i]), labels[i], door, button);
                }
                history.undo();
                world.process();

                // Actual Studio deletion factory, both whole-map closure and individual structural layers.
                for (int ownerIndex : new int[]{1, 2, 0}) {
                    int entity = identities.findByStableId(stableIds[ownerIndex]);
                    history.execute(games.pixscape.studio.history.commands.DeleteEntitiesCommandFactory.create(
                            world, history.historyIds(), com.badlogic.gdx.utils.IntArray.with(entity), null));
                    for (int cycle = 0; cycle < 3; cycle++) {
                        world.process();
                        assertEquals(-1, identities.findByStableId(stableIds[ownerIndex]));
                        history.undo();
                        world.process();
                        for (int i = 0; i < stableIds.length; i++) {
                            assertOwnerProperties(ownerProperties(world, identities.findByStableId(stableIds[i])),
                                    labels[i], door, button);
                        }
                        history.redo();
                    }
                    history.undo();
                    world.process();
                }
                FileHandle saved = h.projectDir.child(StudioFs.DIR_SCENES).child(imported.sceneFileName());
                games.pixscape.studio.service.SceneService.saveScene(world, saved, false);
                World reopened = loadImportedWorld(h, imported);
                try {
                    IdentityRegistry reopenedIds = new IdentityRegistry();
                    reopenedIds.bind(reopened, h.cfg.getSceneMeta(imported.sceneName()));
                    reopenedIds.rebuild();
                    for (int i = 0; i < stableIds.length; i++) {
                        assertOwnerProperties(ownerProperties(reopened, reopenedIds.findByStableId(stableIds[i])),
                                labels[i], door, button);
                    }
                } finally {
                    reopened.dispose();
                }
                ProjectConfig.ProjectIO.saveProject(h.cfg, StudioFs.requireStudioProjectFile(h.cfg));
                FileHandle export = new FileHandle(h.root.resolve("runtime-export").toFile());
                var runtimeConfig = RuntimeExport.exportRuntime(h.cfg, h.projectDir, export);
                World runtime = new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
                var engine = new games.pixscape.runtime.engine.PixscapeEngine();
                var worldField = engine.getClass().getDeclaredField("world");
                worldField.setAccessible(true);
                worldField.set(engine, runtime);
                var meta = runtimeConfig.scenes.get(imported.sceneName());
                engine.getIdentityRegistry().bind(runtime, meta);
                try {
                    SceneLoader.loadScene(runtime, export.child(RuntimeExport.RUNTIME_DIR_NAME)
                            .child(RuntimeFs.DIR_SCENES).child(imported.sceneFileName()), false, meta);
                    runtime.process();
                    // Compiled examples use public indexed handles, not direct ECS property access.
                    var entities = engine.api().entities();
                    var mapRef = entities.requireStableId(stableIds[0]);
                    var tileRef = entities.requireName("Ground");
                    var imageRef = entities.requireName("Backdrop");
                    assertEquals("map", mapRef.properties().getString("label", null));
                    assertEquals("tile", tileRef.properties().getString("label", null));
                    assertEquals("image", imageRef.properties().getString("label", null));
                    var target = entities.requireStableId(mapRef.properties().getObjectStableId("target", -1));
                    assertEquals("door", target.properties().getString("label", null));
                    assertEquals(button, imageRef.properties().getClassValue("settings").properties()
                            .getClassValue("link").properties().getObjectStableId("target", -1));
                    assertEquals(-1, tileRef.properties().getObjectStableId("none", 0));
                } finally {
                    engine.getIdentityRegistry().bind(null, null);
                    runtime.dispose();
                }
                new FileHandle("build/owner-properties-" + orientation + "-fixture-path.txt")
                        .writeString(h.root.toString(), false, "UTF-8");
            } finally {
                identities.bind(null, null);
                world.dispose();
            }
        }
    }

    private static int stableId(World world, int entity) {
        return world.getMapper(PixscapeIdentityComponent.class).get(entity).stableId;
    }

    private static PropertySet ownerProperties(World world, int entity) {
        assertTrue("Missing owner", entity >= 0);
        CustomPropertiesComponent component = world.getMapper(CustomPropertiesComponent.class).get(entity);
        assertNotNull("Missing custom properties on " + entity, component);
        return component.properties;
    }

    private static int copiedEntityNamed(World world, com.badlogic.gdx.utils.IntArray entities, String name) {
        for (int i = 0; i < entities.size; i++) {
            int entity = entities.get(i);
            if (name.equals(world.getMapper(PixscapeIdentityComponent.class).get(entity).name)) {
                return entity;
            }
        }
        throw new AssertionError("Missing copied entity " + name);
    }

    private static void assertOwnerProperties(PropertySet properties, String label, int door, int button) {
        assertEquals(8, properties.size());
        assertEquals(PropertyType.STRING, properties.typeOf("label"));
        assertEquals(label, properties.getString("label", null));
        assertEquals(PropertyType.BOOLEAN, properties.typeOf("enabled"));
        assertTrue(properties.getBoolean("enabled", false));
        assertEquals(PropertyType.INTEGER, properties.typeOf("count"));
        assertEquals(42, properties.getInt("count", 0));
        assertEquals(PropertyType.FLOAT, properties.typeOf("ratio"));
        assertEquals(1.25f, properties.getFloat("ratio", 0), 0f);
        assertEquals(PropertyType.COLOR, properties.typeOf("tint"));
        assertEquals(0x40201080, properties.getColorRgba8888("tint", 0));
        assertEquals(PropertyType.OBJECT, properties.typeOf("target"));
        assertEquals(door, properties.getObjectStableId("target", -1));
        assertEquals(-1, properties.getObjectStableId("none", 0));
        assertEquals(PropertyType.CLASS, properties.typeOf("settings"));
        ClassProperty settings = properties.getClassValue("settings");
        assertEquals("Settings", settings.typeName());
        assertEquals("nested", settings.properties().getString("label", null));
        assertEquals(button, settings.properties().getClassValue("link").properties().getObjectStableId("target", -1));
    }

    @Test
    public void importedContoursSurviveStudioDeleteUndoRedoAndSceneRoundTrip() throws Exception {
        for (String orientation : new String[]{"orthogonal", "isometric"}) {
            Harness h = harness("tmx-history-contours-" + orientation);
            FileHandle tmx = writeTmx(h.root.resolve("contours.tmx"), """
                    <map orientation="%s" width="10" height="10" tilewidth="32" tileheight="16">
                      <objectgroup name="Shapes" offsetx="3" offsety="4">
                        <object id="1" name="Convex" x="40" y="50" rotation="30"><polygon points="0,0 20,0 10,20"/></object>
                        <object id="2" name="Concave" x="60" y="50"><polygon points="0,0 20,0 20,10 10,5 0,10"/></object>
                        <object id="3" name="Crossed" x="80" y="50"><polygon points="0,0 20,20 0,20 20,0"/></object>
                        <object id="4" name="Open" x="100" y="50" rotation="90"><polyline points="-10,5 20,-7 40,12"/></object>
                        <object id="5" name="Rectangle" x="120" y="50" width="20" height="10">
                          <properties><property name="target" type="object" value="2"/></properties>
                        </object>
                      </objectgroup>
                    </map>
                    """.formatted(orientation));
            TmxSceneImportResult result = h.importer().importScene(request(tmx, "Contours"));
            assertTrue(String.valueOf(result.failure()), result.imported());
            World world = loadImportedWorldWithGeometry(h, result);
            IdentityRegistry identities = new IdentityRegistry();
            identities.bind(world, h.cfg.getSceneMeta(result.sceneName()));
            identities.rebuild();
            try {
                String[] names = {"Convex", "Concave", "Crossed", "Open", "Rectangle"};
                float[][] polygons = new float[names.length][];
                float[][] polylines = new float[names.length][];
                float[][] poses = new float[names.length][];
                int[] stableIds = new int[names.length];
                for (int i = 0; i < names.length; i++) {
                    int e = objectEntityByName(world, names[i]);
                    PolygonComponent polygon = world.getMapper(PolygonComponent.class).get(e);
                    PolylineComponent polyline = world.getMapper(PolylineComponent.class).get(e);
                    polygons[i] = polygon == null ? null : polygon.vertices.clone();
                    polylines[i] = polyline == null ? null : polyline.vertices.clone();
                    poses[i] = contourPose(world, e);
                    stableIds[i] = world.getMapper(PixscapeIdentityComponent.class).get(e).stableId;
                }
                assertEquals(orientation.equals("isometric") ? 8 : 0,
                        polygons[4] == null ? 0 : polygons[4].length);
                HistoryManager history = new HistoryManager(16);
                com.badlogic.gdx.utils.IntArray selection = new com.badlogic.gdx.utils.IntArray();
                for (String name : names) {
                    selection.add(objectEntityByName(world, name));
                }
                history.execute(new games.pixscape.studio.history.commands.DeleteGameObjectHierarchyCommand(
                        world, history.historyIds(), selection, null));
                world.process();
                for (int cycle = 0; cycle < 3; cycle++) {
                    for (int stableId : stableIds) {
                        assertEquals(-1, identities.findByStableId(stableId));
                    }
                    history.undo();
                    world.process();
                    assertContours(world, names, polygons, polylines, poses, stableIds);
                    if (cycle < 2) {
                        history.redo();
                        world.process();
                    }
                }
                FileHandle scene = h.projectDir.child(StudioFs.DIR_SCENES).child(result.sceneFileName());
                games.pixscape.studio.service.SceneService.saveScene(world, scene, false);
                World reloaded = loadImportedWorldWithGeometry(h, result);
                try {
                    assertContours(reloaded, names, polygons, polylines, poses, stableIds);
                } finally {
                    reloaded.dispose();
                }
                FileHandle exportDir = h.projectDir.child("runtime-export");
                var runtimeConfig = RuntimeExport.exportRuntime(h.cfg, h.projectDir, exportDir);
                World exported = new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
                try {
                    SceneLoader.loadScene(exported, exportDir.child(RuntimeExport.RUNTIME_DIR_NAME)
                            .child(RuntimeFs.DIR_SCENES).child(result.sceneFileName()), false,
                            runtimeConfig.scenes.get(result.sceneName()));
                    exported.process();
                    for (int i = 0; i < names.length; i++) {
                        int e = objectEntityByName(exported, names[i]);
                        assertContourGeometry(exported, e, polygons[i], polylines[i]);
                        assertArrayEquals(poses[i], contourPose(exported, e), 0f);
                    }
                } finally {
                    exported.dispose();
                }
            } finally {
                identities.bind(null, null);
                world.dispose();
            }
        }
    }

    @Test
    public void editedAndCreatedObjectsSurviveSaveExportAndRuntimeApi() throws Exception {
        for (String orientation : new String[]{"orthogonal", "isometric"}) {
            Harness h = harness("tmx-authoring-" + orientation);
            FileHandle tmx = writeTmx(h.root.resolve("authoring.tmx"), """
                    <map orientation="%s" width="10" height="10" tilewidth="32" tileheight="16">
                      <objectgroup name="Editable">
                        <object id="1" name="Imported polygon" x="50" y="30"><polygon points="0,0 50,0 50,40 20,10 0,40"/></object>
                        <object id="2" name="Imported rectangle" x="100" y="20" width="40" height="30"/>
                      </objectgroup>
                    </map>
                    """.formatted(orientation));
            TmxSceneImportResult result = h.importer().importScene(request(tmx, "Authoring"));
            assertTrue(String.valueOf(result.failure()), result.imported());
            World world = loadImportedWorldWithGeometry(h, result);
            IdentityRegistry identities = new IdentityRegistry();
            identities.bind(world, h.cfg.getSceneMeta(result.sceneName()));
            identities.rebuild();
            HistoryManager history = new HistoryManager(32);
            try {
                int polygon = objectEntityByName(world, "Imported polygon");
                int rectangle = objectEntityByName(world, "Imported rectangle");
                var before = games.pixscape.studio.service.tiled.TiledObjectGeometry.capture(world, polygon);
                float[] changed = before.vertices().clone();
                changed[2] = -80; changed[3] = 50; // Crossed data is retained without physical validation.
                history.execute(new games.pixscape.studio.history.commands.EditTiledObjectGeometryCommand(
                        world, history.historyIds(), polygon, before,
                        games.pixscape.studio.service.tiled.TiledObjectGeometry.withVertices(before, changed)));
                before = games.pixscape.studio.service.tiled.TiledObjectGeometry.capture(world, rectangle);
                history.execute(new games.pixscape.studio.history.commands.EditTiledObjectGeometryCommand(
                        world, history.historyIds(), rectangle, before,
                        games.pixscape.studio.service.tiled.TiledObjectGeometry.resize(before, 75, 35)));
                var authoring = new games.pixscape.studio.service.tiled.TiledObjectAuthoringService(world, history);
                int layer = authoring.objectLayer(polygon);
                int createdPolygon = authoring.createPolygon(layer,
                        new float[]{0,0,20,0,40,0,60,0,60,40,40,40,30,10,20,40,0,40});
                int createdRectangle = authoring.createRectangle(layer,
                        authoring.rectangleVertices(layer, -70, -40, 80, 90));
                world.process();
                int[] objects = {polygon, rectangle, createdPolygon, createdRectangle};
                String[] names = {"Imported polygon", "Imported rectangle", "Polygon", "Rectangle"};
                float[][] expected = new float[objects.length][];
                float[][] poses = new float[objects.length][];
                for (int i = 0; i < objects.length; i++) {
                    expected[i] = games.pixscape.studio.service.tiled.TiledObjectGeometry.vertices(
                            games.pixscape.studio.service.tiled.TiledObjectGeometry.capture(world, objects[i]));
                    poses[i] = contourPose(world, objects[i]);
                    assertFalse(world.getMapper(PhysicsBodyComponent.class).has(objects[i]));
                    assertFalse(world.getMapper(PhysicsShapesComponent.class).has(objects[i]));
                }
                FileHandle scene = h.projectDir.child(StudioFs.DIR_SCENES).child(result.sceneFileName());
                games.pixscape.studio.service.SceneService.saveScene(world, scene, false);
                ProjectConfig.ProjectIO.saveProject(h.cfg, StudioFs.requireStudioProjectFile(h.cfg));
                World reopened = loadImportedWorldWithGeometry(h, result);
                try {
                    for (int i = 0; i < names.length; i++) {
                        int e = objectEntityByName(reopened, names[i]);
                        assertArrayEquals(expected[i], games.pixscape.studio.service.tiled.TiledObjectGeometry.vertices(
                                games.pixscape.studio.service.tiled.TiledObjectGeometry.capture(reopened, e)), 0);
                        assertArrayEquals(poses[i], contourPose(reopened, e), 0);
                    }
                } finally { reopened.dispose(); }
                FileHandle exportDir = h.projectDir.child("authoring-export");
                var runtime = RuntimeExport.exportRuntime(h.cfg, h.projectDir, exportDir);
                World exported = new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
                var engine = new games.pixscape.runtime.engine.PixscapeEngine();
                var field = engine.getClass().getDeclaredField("world");
                field.setAccessible(true); field.set(engine, exported);
                engine.getIdentityRegistry().bind(exported, runtime.scenes.get(result.sceneName()));
                try {
                    SceneLoader.loadScene(exported, exportDir.child(RuntimeExport.RUNTIME_DIR_NAME)
                            .child(RuntimeFs.DIR_SCENES).child(result.sceneFileName()), false,
                            runtime.scenes.get(result.sceneName()));
                    exported.process();
                    var entities = engine.api().entities().findAllByTiledLayerName("Editable");
                    assertEquals(4, entities.length);
                    for (var ref : entities) {
                        int index = java.util.Arrays.asList(names).indexOf(
                                exported.getMapper(PixscapeIdentityComponent.class).get(ref.entityId()).name);
                        assertTrue(index >= 0);
                        var geometry = ref.geometry();
                        assertTrue(geometry.exists()); assertTrue(geometry.closed());
                        assertEquals(expected[index].length / 2, geometry.vertexCount());
                        for (int v = 0; v < geometry.vertexCount(); v++) {
                            assertEquals(expected[index][v * 2], geometry.localX(v), 0);
                            assertEquals(expected[index][v * 2 + 1], geometry.localY(v), 0);
                        }
                        assertArrayEquals(poses[index], contourPose(exported, ref.entityId()), 0);
                    }
                } finally { engine.getIdentityRegistry().bind(null, null); exported.dispose(); }
            } finally { identities.bind(null, null); world.dispose(); }
        }
    }

    private static float[] contourPose(World world, int e) {
        TransformComponent t = world.getMapper(TransformComponent.class).get(e);
        DimensionsComponent d = world.getMapper(DimensionsComponent.class).get(e);
        return new float[]{t.x, t.y, t.rotationRad, t.scaleX, t.scaleY, t.originX, t.originY,
                d.width, d.height};
    }

    private static void assertContourGeometry(World world, int e, float[] polygon, float[] polyline) {
        PolygonComponent p = world.getMapper(PolygonComponent.class).get(e);
        PolylineComponent l = world.getMapper(PolylineComponent.class).get(e);
        assertEquals("Polygon component presence", polygon != null, p != null);
        assertEquals("Polyline component presence", polyline != null, l != null);
        if (polygon != null) {
            assertArrayEquals(polygon, p.vertices, 0f);
        }
        if (polyline != null) {
            assertArrayEquals(polyline, l.vertices, 0f);
        }
    }

    private static void assertContours(World world, String[] names, float[][] polygons, float[][] polylines,
                                       float[][] poses, int[] stableIds) {
        for (int i = 0; i < names.length; i++) {
            int e = objectEntityByName(world, names[i]);
            assertContourGeometry(world, e, polygons[i], polylines[i]);
            assertArrayEquals(poses[i], contourPose(world, e), 0f);
            assertEquals(stableIds[i], world.getMapper(PixscapeIdentityComponent.class).get(e).stableId);
        }
        int rectangle = objectEntityByName(world, "Rectangle");
        assertEquals(EntityKind.TILED_RECTANGLE, world.getMapper(EntityMetaComponent.class).get(rectangle).kind);
        assertEquals(stableIds[1], world.getMapper(CustomPropertiesComponent.class).get(rectangle)
                .properties.getObjectStableId("target", -1));
    }

    @Test
    public void mixedLayerWitnessPaintsCopiesRestoresAndReloadsStableAssociations() throws Exception {
        Harness h = harness("tiled-mixed-witness");
        FileHandle tmx = new FileHandle("src/test/resources/tmx/mixed-layers/mixed-layers.tmx");
        TmxSceneImportResult imported = h.importer().importScene(new TmxSceneImportRequest(tmx, "Mixed Layers", true));
        assertTrue(String.valueOf(imported.failure()), imported.imported());
        World world = loadImportedWorld(h, imported);
        IdentityRegistry identities = new IdentityRegistry();
        identities.bind(world, h.cfg.getSceneMeta(imported.sceneName()));
        identities.rebuild();
        try {
            int map = world.getAspectSubscriptionManager().get(Aspect.all(TiledMapComponent.class)).getEntities().get(0);
            int mapStable = world.getMapper(PixscapeIdentityComponent.class).get(map).stableId;
            HistoryManager importHistory = new HistoryManager(16);
            importHistory.execute(new games.pixscape.studio.history.commands.ImportTiledMapCommand(
                    world, importHistory.historyIds(), map, null));
            importHistory.undo();
            world.process();
            assertEquals(0, world.getAspectSubscriptionManager().get(Aspect.all(TiledMapLayerComponent.class)).getEntities().size());
            importHistory.redo();
            world.process();
            map = identities.findByStableId(mapStable);
            assertTrue(map >= 0);
            assertEquals(4, world.getAspectSubscriptionManager().get(Aspect.all(TiledMapLayerComponent.class)).getEntities().size());
            int[] layerStable = new int[4];
            for (int i = 0; i < 4; i++) {
                int layer = layerEntity(world, i, false);
                layerStable[i] = world.getMapper(PixscapeIdentityComponent.class).get(layer).stableId;
                assertEquals(mapStable, world.getMapper(GameObjectMemberComponent.class).get(layer).parentStableId);
            }
            int bottom = layerEntity(world, 0, true), top = layerEntity(world, 3, true);
            TiledLayerComponent bottomTiles = world.getMapper(TiledLayerComponent.class).get(bottom);
            TiledLayerComponent topTiles = world.getMapper(TiledLayerComponent.class).get(top);
            assertEquals(bottomTiles.tileXs.get(0), topTiles.tileXs.get(0));
            assertEquals(bottomTiles.tileYs.get(0), topTiles.tileYs.get(0));
            int bottomAsset = bottomTiles.tileAssetIds.get(0);
            int topAsset = topTiles.tileAssetIds.get(0);
            topTiles.data = topTiles.createLayerData();
            int x = topTiles.tileXs.get(0), y = topTiles.tileYs.get(0);
            topTiles.data.setTile(x, y, topAsset);
            HistoryManager history = new HistoryManager(16);
            var brush = new games.pixscape.studio.service.tiled.TiledBrushSession(top);
            brush.apply(topTiles, x, y, bottomAsset);
            history.execute(new games.pixscape.studio.history.commands.TiledBrushCommand(world, null,
                    history.historyIds(), history.historyIds().ensureForEntity(top), brush.toPlan(),
                    new games.pixscape.studio.service.tiled.TiledSpatialMutationPlanner()));
            assertEquals(bottomAsset, topTiles.data.getTile(x, y));
            assertEquals(bottomAsset, bottomTiles.tileAssetIds.get(0));
            history.undo();
            assertEquals(topAsset, topTiles.data.getTile(x, y));
            history.redo();
            assertEquals(bottomAsset, topTiles.data.getTile(x, y));
            history.undo();

            int object = visualEntityByName(world, "Green diamond");
            world.getMapper(CustomPropertiesComponent.class).get(object).properties.putObjectStableId("map", mapStable);
            var graph = new games.pixscape.studio.service.entitygraph.EntityGraphCaptureService(world)
                    .capture(com.badlogic.gdx.utils.IntArray.with(map));
            var copy = new games.pixscape.studio.service.entitygraph.EntityGraphInstantiationService(
                    world, history, identities, null, () -> false)
                    .instantiate(graph, 0, 32, 0, "Duplicate map");
            assertEquals(6, copy.createdIds().size);
            int copyMap = copy.createdRootIds().first();
            assertNotEquals(mapStable, world.getMapper(PixscapeIdentityComponent.class).get(copyMap).stableId);
            int copiedMapStable = world.getMapper(PixscapeIdentityComponent.class).get(copyMap).stableId;
            int copiedObject = -1;
            for (int i = 0; i < copy.createdIds().size; i++) {
                int entity = copy.createdIds().get(i);
                if ("Green diamond".equals(world.getMapper(PixscapeIdentityComponent.class).get(entity).name)) {
                    copiedObject = entity;
                }
            }
            assertTrue(copiedObject >= 0);
            assertEquals(copiedMapStable, world.getMapper(CustomPropertiesComponent.class).get(copiedObject)
                    .properties.getObjectStableId("map", -1));
            // Delete before publication, undo after publication, and repeat with recycled handles.
            var deletion = new games.pixscape.studio.history.commands.DeleteGameObjectHierarchyCommand(
                    world, history.historyIds(), com.badlogic.gdx.utils.IntArray.with(copyMap), null);
            deletion.redo();
            world.process();
            deletion.undo();
            world.process();
            assertEquals(2, world.getAspectSubscriptionManager().get(Aspect.all(TiledMapComponent.class)).getEntities().size());
            deletion.redo();
            world.process();
            assertEquals(1, world.getAspectSubscriptionManager().get(Aspect.all(TiledMapComponent.class)).getEntities().size());

            world.getMapper(EntityIndexComponent.class).get(top).zIndex = -1;
            FileHandle saved = h.projectDir.child(StudioFs.DIR_SCENES).child(imported.sceneFileName());
            games.pixscape.studio.service.SceneService.saveScene(world, saved, false);
            World restored = loadImportedWorld(h, imported);
            try {
                for (int i = 0; i < 4; i++) {
                    int original = identities.findByStableId(layerStable[i]);
                    int restoredId = -1;
                    var all = restored.getAspectSubscriptionManager().get(Aspect.all(PixscapeIdentityComponent.class)).getEntities();
                    for (int j = 0; j < all.size(); j++) {
                        if (restored.getMapper(PixscapeIdentityComponent.class).get(all.get(j)).stableId == layerStable[i]) {
                            restoredId = all.get(j);
                        }
                    }
                    assertTrue(restoredId >= 0);
                    assertEquals(world.getMapper(EntityIndexComponent.class).get(original).zIndex,
                            restored.getMapper(EntityIndexComponent.class).get(restoredId).zIndex);
                    assertEquals(mapStable, restored.getMapper(GameObjectMemberComponent.class).get(restoredId).parentStableId);
                }
            } finally {
                restored.dispose();
            }
            // Restore the witness composition before producing the native/HTML export.
            world.getMapper(EntityIndexComponent.class).get(top).zIndex = 3;
            games.pixscape.studio.service.SceneService.saveScene(world, saved, false);
            ProjectConfig.ProjectIO.saveProject(h.cfg, StudioFs.requireStudioProjectFile(h.cfg));
            RuntimeExport.exportRuntime(h.cfg, h.projectDir, new FileHandle(h.root.resolve("export").toFile()));
            new FileHandle("build/tiled-map-layers-fixture-path.txt").writeString(h.root.toString(), false, "UTF-8");
        } finally {
            identities.bind(null, null);
            world.dispose();
        }
    }

    private static HeadlessApplication app;

    @BeforeClass
    public static void startGdx() {
        if (Gdx.app == null) {
            app = new HeadlessApplication(new ApplicationAdapter() {
            }, new HeadlessApplicationConfiguration());
        }
    }

    @AfterClass
    public static void stopGdx() {
        if (app != null) {
            app.exit();
            app = null;
        }
    }

    @Test
    public void importSceneReusesPlannerAndCreatesNewScene() throws Exception {
        Harness h = harness("tmx-import-new-scene");
        FileHandle tmx = simpleTmx(h, "map.tmx", "1,0,2,4");

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Imported Map"));

        assertTrue(result.imported());
        assertNotNull(result.planResult());
        assertTrue(result.planResult().hasPlan());
        assertEquals("Imported Map", result.sceneName());
        assertEquals("Imported Map", h.cfg.getCurrentSceneName());
        assertNotNull(h.cfg.getSceneMeta("Main"));
        assertNotNull(h.cfg.getSceneMeta("Imported Map"));
        assertFalse("import must create a new scene file", "scene1.json".equals(result.sceneFileName()));
    }

    @Test
    public void importScenePersistsSourceConfigurationOnMapWithoutMutatingSceneDefaults() throws Exception {
        Harness h = harness("tmx-import-scene-meta");
        writePng(h.projectDir.child("terrain.png"), 64, 32);
        FileHandle tmx = writeTmx(h.root.resolve("iso.tmx"), """
                <map orientation="isometric" width="2" height="2" tilewidth="32" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="32" tileheight="16" tilecount="4" columns="2">
                    <image source="terrain.png" width="64" height="32"/>
                  </tileset>
                  <layer name="Ground" width="2" height="2"><data encoding="csv">1,0,2,4</data></layer>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, null));

        assertTrue(result.imported());

        TiledLayerComponent tiled = firstTiled(loadImportedWorld(h, result));
        assertEquals(TiledProjection.ISO, tiled.projection);
        assertEquals(32, tiled.tileWidth);
        assertEquals(16, tiled.tileHeight);
        assertEquals(16, tiled.chunkSize);
        assertEquals(2, tiled.mapWidthCells);
        assertEquals(2, tiled.mapHeightCells);
    }

    @Test
    public void importSceneImportsTilesetsAndPersistsProjectAssetsAndScene() throws Exception {
        Harness h = harness("tmx-import-persistence");
        FileHandle tmx = simpleTmx(h, "map.tmx", "1,0,2,4");

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Imported"));

        assertTrue(h.projectDir.child(StudioFs.FILE_ASSETS_JSON).exists());
        assertTrue(StudioFs.requireStudioProjectFile(h.cfg).exists());
        assertTrue(h.projectDir.child(StudioFs.DIR_SCENES).child(result.sceneFileName()).exists());
        assertEquals(1, result.importedTilesetCount());
        assertEquals(4, result.importedTileCount());
        assertNotNull(h.db.findByLogicalPath("tiles/terrain"));
        assertTrue(h.cfg.getSceneMeta("Imported").runtimeAvailability.tiledTileAssetIds.size() >= 4);
    }

    @Test
    public void importSceneCreatesUniversalMapLayersInRenderOrder() throws Exception {
        Harness h = harness("tmx-import-layer-order");
        FileHandle tmx = writeTmx(h.root.resolve("layers.tmx"), """
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="terrain.png" width="16" height="16"/>
                  </tileset>
                  <layer name="Ground" width="1" height="1"><data encoding="csv">1</data></layer>
                  <layer name="Above" visible="0" parallaxx="2" parallaxy="0.5" offsetx="3" offsety="4" width="1" height="1"><data encoding="csv">1</data></layer>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Imported"));
        World world = loadImportedWorld(h, result);

        int ground = layerEntity(world, 0, true);
        int above = layerEntity(world, 1, true);
        assertEquals("Ground", world.getMapper(PixscapeIdentityComponent.class).get(ground).name);
        assertEquals("Above", world.getMapper(PixscapeIdentityComponent.class).get(above).name);
        assertFalse(world.getMapper(VisibilityComponent.class).get(above).visible);
        assertEquals(2f, world.getMapper(LayerParallaxComponent.class).get(above).factorX, 0.0001f);
        assertEquals(0.5f, world.getMapper(LayerParallaxComponent.class).get(above).factorY, 0.0001f);
        int aboveMap = tiledMapEntity(world, 1);
        assertEquals(3f, world.getMapper(TiledLayerComponent.class).get(aboveMap).originX, 0.0001f);
        assertEquals(4f, world.getMapper(TiledLayerComponent.class).get(aboveMap).originY, 0.0001f);
    }

    @Test
    public void importedTileLayerUsesUniversalOwnershipAndCurrentMapEditingPaths() throws Exception {
        Harness h = harness("tmx-import-universal-map-layer");
        writePng(h.projectDir.child("terrain.png"), 64, 32);
        FileHandle tmx = writeTmx(h.root.resolve("universal-map.tmx"), """
                <map orientation="isometric" width="2" height="2" tilewidth="32" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="32" tileheight="16" tilecount="4" columns="2">
                    <image source="terrain.png" width="64" height="32"/>
                  </tileset>
                  <layer name="Ground" visible="0" parallaxx="1.5" parallaxy="0.75"
                         offsetx="6" offsety="9" width="2" height="2">
                    <data encoding="csv">1,0,2,4</data>
                  </layer>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Universal Import"));
        assertTrue("import failed: " + result.failure() + " " + result.diagnostics(),
                result.imported());
        World world = loadImportedWorld(h, result);
        int layerEntity = world.getAspectSubscriptionManager().get(Aspect.all(LayerComponent.class)).getEntities().get(0);
        int mapEntity = tiledMapEntity(world, 0);
        LayerComponent layer = world.getMapper(LayerComponent.class).get(layerEntity);
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).get(mapEntity);
        EntityIndexComponent mapIndex = world.getMapper(EntityIndexComponent.class).get(mapEntity);

        assertFalse(world.getMapper(LayerComponent.class).has(mapEntity));
        assertEquals(0, mapIndex.layerIndex);
        assertEquals(0, mapIndex.zIndex);
        assertEquals(TiledProjection.ISO, tiled.projection);
        assertEquals(32, tiled.tileWidth);
        assertEquals(16, tiled.tileHeight);
        assertEquals(2, tiled.mapWidthCells);
        assertEquals(2, tiled.mapHeightCells);
        assertEquals(6f, tiled.originX, 0f);
        assertEquals(9f, tiled.originY, 0f);
        assertTrue(world.getMapper(VisibilityComponent.class).get(layerEntity).visible);
        assertFalse(world.getMapper(VisibilityComponent.class).get(mapEntity).visible);
        assertEquals(1.5f, world.getMapper(LayerParallaxComponent.class)
                .get(mapEntity).factorX, 0f);
        assertEquals(0.75f, world.getMapper(LayerParallaxComponent.class)
                .get(mapEntity).factorY, 0f);
        assertFalse(tiled.spatialEnabled);
        tiled.data = tiled.createLayerData();
        assertFalse(tiled.data.spatialEnabled);
        assertFalse(world.getMapper(PhysicsBodyComponent.class).has(layerEntity));
        assertFalse(world.getMapper(PhysicsShapesComponent.class).has(layerEntity));
        assertFalse(world.getMapper(PhysicsBodyComponent.class).has(mapEntity));
        assertFalse(world.getMapper(PhysicsShapesComponent.class).has(mapEntity));

        SceneMeta importedMeta = h.cfg.getSceneMeta(result.sceneName());
        IdentityRegistry identities = new IdentityRegistry();
        identities.bind(world, importedMeta);
        identities.rebuild();
        HistoryManager history = new HistoryManager(16);
        LayerService layers = new LayerService(world, null, history.historyIds(), identities);
        assertEquals(1, layers.count());
        assertTrue(layers.isLayerEntity(layerEntity));

        int sprite = world.create();
        world.getMapper(EntityIndexComponent.class).create(sprite).layerIndex = 0;
        world.getMapper(TextureRegionComponent.class).create(sprite);
        int light = world.create();
        world.getMapper(EntityIndexComponent.class).create(light).layerIndex = 0;
        world.getMapper(PointLightComponent.class).create(light);

        AtomicInteger selectedMap = new AtomicInteger(-1);
        history.execute(new AddTiledMapCommand(
                layers, layerEntity, 3, 3, TiledProjection.ORTHO,
                16, 16, 4, selectedMap::set));
        world.process();
        assertTrue(selectedMap.get() >= 0);
        assertEquals(2, world.getAspectSubscriptionManager()
                .get(Aspect.all(EntityIndexComponent.class, TiledLayerComponent.class)
                        .exclude(LayerComponent.class))
                .getEntities().size());
        assertEquals(0, world.getMapper(EntityIndexComponent.class)
                .get(selectedMap.get()).layerIndex);
        assertEquals(0, world.getMapper(EntityIndexComponent.class).get(sprite).layerIndex);
        assertEquals(0, world.getMapper(EntityIndexComponent.class).get(light).layerIndex);
        history.execute(new ToggleTiledMapSpatialDepthCommand(
                world, history.historyIds(), mapEntity,
                true, 3f, 12f));
        assertTrue(tiled.spatialEnabled);
        assertTrue(tiled.data.spatialEnabled);

        StudioEditingModeService editingModes = new StudioEditingModeService();
        editingModes.activateSceneDocument(StudioEditingMode.NORMAL, 0);
        SelectionService selection = new SelectionService(world, layers, editingModes);
        selection.setTiledMapEditingTarget(mapEntity, SelectionService.SelectionSource.TREE);
        assertTrue(selection.isTiledMapEditingTargetActive());
        assertEquals(mapEntity, selection.getTiledMapEditingTargetEntityId());
        assertEquals(layerEntity, selection.getActivelayerId());
    }

    @Test
    public void importSceneCreatesImageLayersAsClassicLayersWithSprite() throws Exception {
        Harness h = harness("tmx-import-image-layer");
        writePng(h.projectDir.child("background.png"), 64, 32);
        FileHandle tmx = writeTmx(h.root.resolve("image-layer.tmx"), """
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="terrain.png" width="16" height="16"/>
                  </tileset>
                  <layer name="Ground" width="1" height="1"><data encoding="csv">1</data></layer>
                  <imagelayer name="Backdrop" visible="0" opacity="0.5" offsetx="3" offsety="4" x="10" y="20" parallaxx="2" parallaxy="0.25">
                    <image source="background.png" width="64" height="32"/>
                  </imagelayer>
                  <layer name="Above" width="1" height="1"><data encoding="csv">1</data></layer>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Imported"));
        World world = loadImportedWorld(h, result);

        int ground = layerEntity(world, 0, true);
        int backdrop = layerEntity(world, 1, false);
        int above = layerEntity(world, 2, true);
        assertEquals("Ground", world.getMapper(PixscapeIdentityComponent.class).get(ground).name);
        assertEquals("Backdrop", world.getMapper(PixscapeIdentityComponent.class).get(backdrop).name);
        assertEquals("Above", world.getMapper(PixscapeIdentityComponent.class).get(above).name);
        assertFalse(world.getMapper(VisibilityComponent.class).get(backdrop).visible);
        assertEquals(2f, world.getMapper(LayerParallaxComponent.class).get(backdrop).factorX, 0.0001f);
        assertEquals(0.25f, world.getMapper(LayerParallaxComponent.class).get(backdrop).factorY, 0.0001f);

        int sprite = drawableInLayer(world, 1);
        TransformComponent transform = world.getMapper(TransformComponent.class).get(sprite);
        DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class).get(sprite);
        AssetRefComponent assetRef = world.getMapper(AssetRefComponent.class).get(sprite);
        TintComponent tint = world.getMapper(TintComponent.class).get(sprite);

        assertEquals(13f, transform.x, 0.0001f);
        assertEquals(-40f, transform.y, 0.0001f);
        assertEquals(0f, transform.originX, 0.0001f);
        assertEquals(0f, transform.originY, 0.0001f);
        assertEquals(64f, dimensions.width, 0.0001f);
        assertEquals(32f, dimensions.height, 0.0001f);
        assertTrue(assetRef.assetId > 0);
        assertEquals(result.sceneTag(), assetRef.atlasTag);
        assertEquals(0x80FFFFFF, tint.getRgba());

        AssetMeta imageMeta = h.db.findById(assetRef.assetId);
        assertNotNull(imageMeta);
        assertTrue(imageMeta.sourceRelPath().startsWith(StudioFs.DIR_ORIG_IMAGES + "/"));
        assertTrue(h.projectDir.child(imageMeta.sourceRelPath()).exists());
        assertTrue(h.cfg.getSceneMeta("Imported").runtimeAvailability.spriteAssetIds.contains(assetRef.assetId));
        assertTrue(h.projectDir.child(StudioFs.DIR_ATLASES)
                .child(StudioFs.DIR_INPUT)
                .child(result.sceneTag())
                .child(new FileHandle(imageMeta.sourceRelPath()).name())
                .exists());
    }

    @Test
    public void importSceneMaterializesEmptyObjectLayerInMixedGlobalOrder() throws Exception {
        Harness h = harness("tmx-import-empty-object-layer");
        FileHandle tmx = writeTmx(h.root.resolve("empty-object-layer.tmx"), """
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="terrain.png" width="16" height="16"/>
                  </tileset>
                  <layer name="Below" width="1" height="1"><data encoding="csv">1</data></layer>
                  <objectgroup name="Gameplay"/>
                  <layer name="Above" width="1" height="1"><data encoding="csv">0</data></layer>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Objects"));
        World world = loadImportedWorld(h, result);

        assertTrue(result.imported());
        assertEquals("Below", world.getMapper(PixscapeIdentityComponent.class).get(layerEntity(world, 0, true)).name);
        int objectLayer = layerEntity(world, 1, false);
        assertEquals("Gameplay", world.getMapper(PixscapeIdentityComponent.class).get(objectLayer).name);
        assertEquals("Above", world.getMapper(PixscapeIdentityComponent.class).get(layerEntity(world, 2, true)).name);
        assertEquals(0, objectCountInLayer(world, 1));
    }

    @Test
    public void importSceneRoundTripsObjectLayerRectanglesPointsPropertiesAndIdentity() throws Exception {
        Harness h = harness("tmx-import-object-roundtrip");
        FileHandle tmx = writeTmx(h.root.resolve("objects.tmx"), """
                <map orientation="orthogonal" width="20" height="10" tilewidth="16" tileheight="16">
                  <group name="World" visible="0" offsetx="3" offsety="4" parallaxx="2" parallaxy="0.5">
                    <objectgroup name="Gameplay" offsetx="5" offsety="6" parallaxx="0.5" parallaxy="2">
                      <properties>
                        <property name="role" value="logic"/>
                        <property name="enabled" type="bool" value="true"/>
                        <property name="count" type="int" value="7"/>
                        <property name="weight" type="float" value="1.5"/>
                        <property name="tint" type="color" value="#40010203"/>
                      </properties>
                      <object id="777" name="Duplicate" class="Trigger" type="LegacyTrigger"
                              x="10" y="20" width="30" height="40" visible="0">
                        <properties>
                          <property name="label" value="door"/>
                          <property name="armed" type="bool" value="true"/>
                          <property name="damage" type="int" value="20"/>
                          <property name="ratio" type="float" value="2.25"/>
                          <property name="tint" type="color" value="#800A0B0C"/>
                        </properties>
                      </object>
                      <object id="777" name="Duplicate" x="4" y="5"><point/></object>
                      <object name="Zero" x="1" y="2" width="0" height="0"/>
                      <object x="2" y="3"><point/></object>
                      <object id="9" name="Deferred"><ellipse/></object>
                    </objectgroup>
                  </group>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Object Round Trip"));
        World world = loadImportedWorld(h, result);

        assertTrue(result.imported());
        int layerEntity = layerEntity(world, 0, false);
        assertTrue(world.getMapper(TiledMapLayerComponent.class).has(layerEntity));
        assertEquals("World/Gameplay", world.getMapper(PixscapeIdentityComponent.class).get(layerEntity).name);
        assertFalse(world.getMapper(VisibilityComponent.class).get(layerEntity).visible);
        assertEquals(1f, world.getMapper(LayerParallaxComponent.class).get(layerEntity).factorX, 0.0001f);
        assertEquals(1f, world.getMapper(LayerParallaxComponent.class).get(layerEntity).factorY, 0.0001f);
        CustomPropertiesComponent layerProperties = world.getMapper(CustomPropertiesComponent.class).get(layerEntity);
        assertEquals("logic", layerProperties.properties.getString("role", null));
        assertTrue(layerProperties.properties.getBoolean("enabled", false));
        assertEquals(7, layerProperties.properties.getInt("count", 0));
        assertEquals(1.5f, layerProperties.properties.getFloat("weight", 0f), 0.0001f);
        assertEquals(0x01020340, layerProperties.properties.getColorRgba8888("tint", 0));
        TmxObjectLayerPlan plannedLayer = (TmxObjectLayerPlan) result.planResult().plan().layers().get(0);
        assertNotSame(plannedLayer.properties(), layerProperties.properties);
        assertTrue(world.getMapper(PixscapeIdentityComponent.class).get(layerEntity).stableId > 0);

        int[] duplicates = objectEntitiesByName(world, "Duplicate");
        assertEquals(2, duplicates.length);
        int rectangle = world.getMapper(DimensionsComponent.class).has(duplicates[0])
                ? duplicates[0] : duplicates[1];
        int point = rectangle == duplicates[0] ? duplicates[1] : duplicates[0];
        assertEquals(4, objectCountInLayer(world, 0));

        TransformComponent rectangleTransform = world.getMapper(TransformComponent.class).get(rectangle);
        DimensionsComponent rectangleDimensions = world.getMapper(DimensionsComponent.class).get(rectangle);
        assertEquals(33f, rectangleTransform.x, 0.0001f);
        assertEquals(110f, rectangleTransform.y, 0.0001f);
        assertEquals(15f, rectangleTransform.originX, 0f);
        assertEquals(20f, rectangleTransform.originY, 0f);
        assertEquals(30f, rectangleDimensions.width, 0f);
        assertEquals(40f, rectangleDimensions.height, 0f);
        assertFalse(world.getMapper(VisibilityComponent.class).get(rectangle).visible);
        assertEquals(3, world.getMapper(EntityIndexComponent.class).get(rectangle).zIndex);
        assertNotNull(world.getMapper(EntityMetaComponent.class).get(rectangle));
        assertEquals(EntityKind.TILED_RECTANGLE,
                world.getMapper(EntityMetaComponent.class).get(rectangle).kind);
        assertTrue(world.getMapper(AABBComponent.class).has(rectangle));
        assertTrue(world.getMapper(OrientedBoundsComponent.class).has(rectangle));
        PixscapeTagComponent rectangleTags = world.getMapper(PixscapeTagComponent.class).get(rectangle);
        assertEquals(1, rectangleTags.tags.size);
        assertEquals("Trigger", rectangleTags.tags.first());
        assertFalse(rectangleTags.tags.contains("LegacyTrigger", false));
        assertFalse(world.getMapper(AssetRefComponent.class).has(rectangle));
        assertFalse(world.getMapper(TextureRegionComponent.class).has(rectangle));
        assertFalse(world.getMapper(RenderMaterialComponent.class).has(rectangle));
        assertFalse(world.getMapper(TintComponent.class).has(rectangle));
        CustomPropertiesComponent objectProperties = world.getMapper(CustomPropertiesComponent.class).get(rectangle);
        assertEquals("door", objectProperties.properties.getString("label", null));
        assertTrue(objectProperties.properties.getBoolean("armed", false));
        assertEquals(20, objectProperties.properties.getInt("damage", 0));
        assertEquals(2.25f, objectProperties.properties.getFloat("ratio", 0f), 0.0001f);
        assertEquals(0x0A0B0C80, objectProperties.properties.getColorRgba8888("tint", 0));
        assertNotSame(layerProperties.properties, objectProperties.properties);
        assertNotSame(plannedLayer.objects().get(0).properties(), objectProperties.properties);

        TransformComponent pointTransform = world.getMapper(TransformComponent.class).get(point);
        assertEquals(12f, pointTransform.x, 0.0001f);
        assertEquals(145f, pointTransform.y, 0.0001f);
        assertFalse(world.getMapper(DimensionsComponent.class).has(point));
        assertFalse(world.getMapper(AABBComponent.class).has(point));
        assertFalse(world.getMapper(OrientedBoundsComponent.class).has(point));
        assertEquals(EntityKind.TILED_POINT,
                world.getMapper(EntityMetaComponent.class).get(point).kind);
        assertTrue(world.getMapper(VisibilityComponent.class).get(point).visible);
        assertEquals(2, world.getMapper(EntityIndexComponent.class).get(point).zIndex);

        int zero = objectEntityByName(world, "Zero");
        DimensionsComponent zeroDimensions = world.getMapper(DimensionsComponent.class).get(zero);
        assertEquals(0f, zeroDimensions.width, 0f);
        assertEquals(0f, zeroDimensions.height, 0f);
        assertEquals(0, world.getMapper(EntityIndexComponent.class).get(zero).zIndex);
        assertEquals(EntityKind.TILED_RECTANGLE,
                world.getMapper(EntityMetaComponent.class).get(zero).kind);

        int unnamed = objectEntityByName(world, "unnamed");
        assertFalse(world.getMapper(DimensionsComponent.class).has(unnamed));
        assertEquals(1, world.getMapper(EntityIndexComponent.class).get(unnamed).zIndex);

        PixscapeIdentityComponent rectangleIdentity = world.getMapper(PixscapeIdentityComponent.class).get(rectangle);
        PixscapeIdentityComponent pointIdentity = world.getMapper(PixscapeIdentityComponent.class).get(point);
        PixscapeIdentityComponent zeroIdentity = world.getMapper(PixscapeIdentityComponent.class).get(zero);
        PixscapeIdentityComponent unnamedIdentity = world.getMapper(PixscapeIdentityComponent.class).get(unnamed);
        assertTrue(rectangleIdentity.stableId > 0);
        assertTrue(pointIdentity.stableId > 0);
        assertTrue(zeroIdentity.stableId > 0);
        assertTrue(unnamedIdentity.stableId > 0);
        assertNotEquals(777, rectangleIdentity.stableId);
        assertNotEquals(rectangleIdentity.stableId, pointIdentity.stableId);
        assertNotEquals(pointIdentity.stableId, zeroIdentity.stableId);
        assertNotEquals(zeroIdentity.stableId, unnamedIdentity.stableId);
        assertEquals("Duplicate", rectangleIdentity.name);
        assertEquals("Duplicate", pointIdentity.name);
        assertEquals("Zero", zeroIdentity.name);
        assertEquals("unnamed", unnamedIdentity.name);

        TagRegistry registry = new TagRegistry();
        registry.bind(world);
        registry.rebuild();
        assertTrue(registry.hasTag(rectangle, "Trigger"));
        assertEquals(rectangle, registry.first("Trigger"));
        assertEquals(1, registry.get("Trigger").size);
        assertEquals(20, objectProperties.properties.getInt("damage", 0));
        assertFalse(objectProperties.properties.contains("class"));
        assertFalse(objectProperties.properties.contains("type"));
    }

    @Test
    public void importedRectangleUsesNormalBoundsAndGizmoScaleHistory() throws Exception {
        Harness h = harness("tmx-import-rectangle-geometry");
        FileHandle tmx = writeTmx(h.root.resolve("rectangle-geometry.tmx"), """
                <map orientation="orthogonal" width="10" height="10" tilewidth="16" tileheight="16">
                  <objectgroup name="Gameplay">
                    <object name="Rectangle" x="10" y="20" width="30" height="40" rotation="90"/>
                  </objectgroup>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Rectangle Geometry"));
        World world = loadImportedWorldWithGeometry(h, result);
        int rectangle = objectEntityByName(world, "Rectangle");
        TransformComponent transform = world.getMapper(TransformComponent.class).get(rectangle);
        DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class).get(rectangle);
        AABBComponent aabb = world.getMapper(AABBComponent.class).get(rectangle);
        OrientedBoundsComponent obb = world.getMapper(OrientedBoundsComponent.class).get(rectangle);

        assertEquals(EntityKind.TILED_RECTANGLE,
                world.getMapper(EntityMetaComponent.class).get(rectangle).kind);
        assertEquals(-10f, transform.x, 0.0001f);
        assertEquals(125f, transform.y, 0.0001f);
        assertEquals(15f, transform.originX, 0.0001f);
        assertEquals(20f, transform.originY, 0.0001f);
        float[] importedCorners = new float[8];
        OrientedBoundsHelper.toCorners(obb, importedCorners);
        assertSameCornerSet(tiledSourceCorners(10f, 140f, 30f, 40f, -MathUtils.PI / 2f),
                importedCorners);
        assertEquals(-30f, aabb.minX, 0.0001f);
        assertEquals(10f, aabb.maxX, 0.0001f);
        assertEquals(110f, aabb.minY, 0.0001f);
        assertEquals(140f, aabb.maxY, 0.0001f);
        assertTrue(OrientedBoundsHelper.contains(obb, -10f, 125f));
        assertFalse(OrientedBoundsHelper.contains(obb, 15f, 125f));

        HistoryManager history = new HistoryManager(8);
        long historyId = history.historyIds().ensureForEntity(rectangle);
        GizmoTransformCommand scale = new GizmoTransformCommand(
                world, history.historyIds(), TransformOp.SCALE);
        scale.addEntry(historyId, GizmoTransformCommand.Snapshot.of(transform),
                new GizmoTransformCommand.Snapshot(
                        transform.x, transform.y, transform.rotationRad,
                        1.5f, 0.5f, transform.originX, transform.originY));
        history.execute(scale);
        world.process();

        assertEquals(1.5f, transform.scaleX, 0f);
        assertEquals(0.5f, transform.scaleY, 0f);
        assertEquals(30f, dimensions.width, 0f);
        assertEquals(40f, dimensions.height, 0f);
        assertEquals(-20f, aabb.minX, 0.0001f);
        assertEquals(0f, aabb.maxX, 0.0001f);
        assertEquals(102.5f, aabb.minY, 0.0001f);
        assertEquals(147.5f, aabb.maxY, 0.0001f);

        history.undo();
        world.process();
        assertEquals(1f, transform.scaleX, 0f);
        assertEquals(1f, transform.scaleY, 0f);
        assertEquals(-30f, aabb.minX, 0.0001f);
        assertEquals(10f, aabb.maxX, 0.0001f);

        history.redo();
        world.process();
        assertEquals(1.5f, transform.scaleX, 0f);
        assertEquals(0.5f, transform.scaleY, 0f);
    }

    @Test
    public void centeredRectanglePivotPreservesTiledSourceGeometry() {
        assertCenteredRectangleGeometry(10f, 140f, 30f, 40f, 0f);
        assertCenteredRectangleGeometry(18f, 130f, 30f, 40f, 0.65f);
        assertCenteredRectangleGeometry(-3f, 90f, 17f, 9f, -1.1f);
        assertCenteredRectangleGeometry(18f, 130f, 0f, 40f, 0.65f);
        assertCenteredRectangleGeometry(18f, 130f, 30f, 0f, -1.1f);
        assertCenteredRectangleGeometry(18f, 130f, 0f, 0f, 0.65f);
    }

    @Test
    public void classPropertiesSurviveMaterializationSceneSaveReloadAndRuntimeReadback() throws Exception {
        Harness h = harness("tmx-import-class-round-trip");
        FileHandle tmx = writeTmx(h.root.resolve("class-properties.tmx"), """
                <map orientation="orthogonal" width="2" height="2" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="actors" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="terrain.png" width="32" height="32"/>
                    <tile id="0"><properties><property name="physics" type="class" propertytype="Physics"><properties>
                      <property name="mass" type="float" value="1"/>
                      <property name="sensor" type="bool" value="false"/>
                    </properties></property></properties></tile>
                  </tileset>
                  <objectgroup name="Gameplay"><properties>
                    <property name="settings" type="class" propertytype="LayerSettings"><properties>
                      <property name="enabled" type="bool" value="true"/>
                    </properties></property>
                  </properties>
                    <object name="Rectangle" width="4" height="5"><properties>
                      <property name="attack" type="class" propertytype="Attack"><properties>
                        <property name="damage" type="int" value="20"/>
                      </properties></property>
                    </properties></object>
                    <object name="Point"><point/><properties>
                      <property name="follow" type="class" propertytype="Follow"/>
                    </properties></object>
                    <object name="Tile" gid="1"><properties>
                      <property name="physics" type="class" propertytype="Physics"><properties>
                        <property name="mass" type="float" value="2"/>
                        <property name="sensor" type="bool" value="true"/>
                      </properties></property>
                    </properties></object>
                  </objectgroup>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Class Round Trip"));
        World world = loadImportedWorld(h, result);

        assertTrue(result.imported());
        ClassProperty settings = world.getMapper(CustomPropertiesComponent.class)
                .get(layerEntity(world, 0, false)).properties.getClassValue("settings");
        assertEquals("LayerSettings", settings.typeName());
        assertTrue(settings.properties().getBoolean("enabled", false));

        ClassProperty attack = world.getMapper(CustomPropertiesComponent.class)
                .get(objectEntityByName(world, "Rectangle")).properties.getClassValue("attack");
        assertEquals("Attack", attack.typeName());
        assertEquals(20, attack.properties().getInt("damage", 0));
        assertFalse(world.getMapper(PixscapeTagComponent.class)
                .has(objectEntityByName(world, "Rectangle")));

        ClassProperty follow = world.getMapper(CustomPropertiesComponent.class)
                .get(objectEntityByName(world, "Point")).properties.getClassValue("follow");
        assertEquals("Follow", follow.typeName());
        assertTrue(follow.properties().isEmpty());

        ClassProperty physics = world.getMapper(CustomPropertiesComponent.class)
                .get(visualEntityByName(world, "Tile")).properties.getClassValue("physics");
        assertEquals("Physics", physics.typeName());
        assertEquals(2f, physics.properties().getFloat("mass", 0f), 0.0001f);
        assertTrue(physics.properties().getBoolean("sensor", false));
    }

    @Test
    public void importSceneMapsModernAndLegacyClassificationToOneNormalizedTag() throws Exception {
        Harness h = harness("tmx-import-object-tags");
        FileHandle tmx = writeTmx(h.root.resolve("object-tags.tmx"), """
                <map orientation="orthogonal" width="4" height="4" tilewidth="16" tileheight="16">
                  <objectgroup name="Classified">
                    <object id="99" name="ClassOnly" class="Enemy" width="2" height="3"/>
                    <object id="99" name="TypeOnly" type="Legacy Enemy"><point/></object>
                    <object name="Equal" class="Same" type="Same"><point/></object>
                    <object name="Conflict" class="Modern" type="Legacy"><point/></object>
                    <object name="BlankClass" class="   " type="Fallback"><point/></object>
                    <object name="None"><point/></object>
                    <object name="InternalSpace" class="  Boss Enemy.v2  "><point/></object>
                    <object name="LowerCase" class="enemy"><point/></object>
                  </objectgroup>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Object Tags"));
        World world = loadImportedWorld(h, result);

        assertTrue(result.imported());
        assertSingleTag(world, "ClassOnly", "Enemy");
        assertSingleTag(world, "TypeOnly", "Legacy Enemy");
        assertSingleTag(world, "Equal", "Same");
        assertSingleTag(world, "Conflict", "Modern");
        assertSingleTag(world, "BlankClass", "Fallback");
        assertSingleTag(world, "InternalSpace", "Boss Enemy.v2");
        assertSingleTag(world, "LowerCase", "enemy");
        assertFalse(world.getMapper(PixscapeTagComponent.class).has(objectEntityByName(world, "None")));
        assertFalse(world.getMapper(PixscapeTagComponent.class).has(layerEntity(world, 0, false)));
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> "TMX_OBJECT_CLASS_TYPE_CONFLICT".equals(diagnostic.code())));

        TagRegistry registry = new TagRegistry();
        registry.bind(world);
        registry.rebuild();
        int classOnly = objectEntityByName(world, "ClassOnly");
        int typeOnly = objectEntityByName(world, "TypeOnly");
        int conflict = objectEntityByName(world, "Conflict");
        int internalSpace = objectEntityByName(world, "InternalSpace");
        int lowerCase = objectEntityByName(world, "LowerCase");
        assertTrue(registry.hasTag(classOnly, " Enemy "));
        assertTrue(registry.hasTag(typeOnly, "Legacy Enemy"));
        assertTrue(registry.hasTag(conflict, "Modern"));
        assertFalse(registry.hasTag(conflict, "Legacy"));
        assertTrue(registry.hasTag(internalSpace, "  Boss Enemy.v2  "));
        assertTrue(registry.hasTag(lowerCase, "enemy"));
        assertFalse(registry.hasTag(lowerCase, "Enemy"));
        assertEquals(1, registry.get("Enemy").size);
        assertEquals(1, registry.get("enemy").size);
    }

    @Test
    public void importScenePreservesTiledRectanglePivotAndRotatedCorners() throws Exception {
        Harness h = harness("tmx-import-object-rotation");
        FileHandle tmx = writeTmx(h.root.resolve("rotations.tmx"), """
                <map orientation="orthogonal" width="20" height="10" tilewidth="16" tileheight="16">
                  <objectgroup name="Geometry">
                    <object id="1" name="Zero" x="10" y="20" width="30" height="40" rotation="0"/>
                    <object id="2" name="Clockwise" x="10" y="20" width="30" height="40" rotation="90"/>
                    <object id="3" name="CounterClockwise" x="10" y="20" width="30" height="40" rotation="-90"/>
                  </objectgroup>
                </map>
                """);

        World world = loadImportedWorld(h,
                h.importer().importScene(request(tmx, "Rotated Objects")));

        assertCorners(world, objectEntityByName(world, "Zero"), new float[]{
                10f, 140f, 40f, 140f, 40f, 100f, 10f, 100f
        });
        assertCorners(world, objectEntityByName(world, "Clockwise"), new float[]{
                10f, 140f, 10f, 110f, -30f, 110f, -30f, 140f
        });
        assertCorners(world, objectEntityByName(world, "CounterClockwise"), new float[]{
                10f, 140f, 10f, 170f, 50f, 170f, 50f, 140f
        });
    }

    @Test
    public void importScenePlacesOrthogonalImageLayersUsingTiledTopLeftCoordinates() throws Exception {
        Harness h = harness("tmx-import-image-layer-placement");
        writePng(h.projectDir.child("graphics").child("sky.png"), 384, 240);
        FileHandle tmx = writeTmx(h.root.resolve("maps").resolve("image-layer-placement.tmx"), """
                <map orientation="orthogonal" width="75" height="20" tilewidth="16" tileheight="16">
                  <imagelayer name="Sky">
                    <image source="../graphics/sky.png" width="384" height="240"/>
                  </imagelayer>
                  <imagelayer name="SkyOffset" offsetx="10" offsety="5">
                    <image source="../graphics/sky.png" width="384" height="240"/>
                  </imagelayer>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Imported Placement"));
        World world = loadImportedWorld(h, result);

        TransformComponent sky = world.getMapper(TransformComponent.class).get(drawableInLayer(world, 0));
        TransformComponent skyOffset = world.getMapper(TransformComponent.class).get(drawableInLayer(world, 1));

        assertEquals(0f, sky.x, 0.0001f);
        assertEquals(80f, sky.y, 0.0001f);
        assertEquals(10f, skyOffset.x, 0.0001f);
        assertEquals(75f, skyOffset.y, 0.0001f);
    }

    @Test
    public void importScenePreservesMixedImageAndTileLayerStackOrder() throws Exception {
        Harness h = harness("tmx-import-mixed-layer-order");
        writePng(h.projectDir.child("sky.png"), 384, 240);
        writePng(h.projectDir.child("jungle.png"), 384, 240);
        FileHandle tmx = writeTmx(h.root.resolve("mixed-layers.tmx"), """
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="terrain.png" width="16" height="16"/>
                  </tileset>
                  <imagelayer id="1" name="sky">
                    <image source="sky.png" width="384" height="240"/>
                  </imagelayer>
                  <imagelayer id="2" name="jungle">
                    <image source="jungle.png" width="384" height="240"/>
                  </imagelayer>
                  <layer id="3" name="ground" width="1" height="1"><data encoding="csv">1</data></layer>
                  <layer id="4" name="props" width="1" height="1"><data encoding="csv">1</data></layer>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Imported Order"));
        World world = loadImportedWorld(h, result);

        assertEquals("sky", world.getMapper(PixscapeIdentityComponent.class).get(layerEntity(world, 0, false)).name);
        assertEquals("jungle", world.getMapper(PixscapeIdentityComponent.class).get(layerEntity(world, 1, false)).name);
        assertEquals("ground", world.getMapper(PixscapeIdentityComponent.class).get(layerEntity(world, 2, true)).name);
        assertEquals("props", world.getMapper(PixscapeIdentityComponent.class).get(layerEntity(world, 3, true)).name);
    }

    @Test
    public void importSceneAttachesImageLayerRepeatComponentWithoutDuplicatingSprites() throws Exception {
        Harness h = harness("tmx-import-image-layer-repeat");
        writePng(h.projectDir.child("sky.png"), 384, 240);
        FileHandle tmx = writeTmx(h.root.resolve("image-layer-repeat.tmx"), """
                <map orientation="orthogonal" width="75" height="20" tilewidth="16" tileheight="16">
                  <imagelayer name="SkyX" parallaxx="1.3" repeatx="1">
                    <image source="sky.png" width="384" height="240"/>
                  </imagelayer>
                  <imagelayer name="SkyY" repeaty="true">
                    <image source="sky.png" width="384" height="240"/>
                  </imagelayer>
                  <imagelayer name="SkyBoth" repeatx="true" repeaty="1">
                    <image source="sky.png" width="384" height="240"/>
                  </imagelayer>
                  <imagelayer name="SkyNone">
                    <image source="sky.png" width="384" height="240"/>
                  </imagelayer>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Imported Repeat"));
        World world = loadImportedWorld(h, result);

        assertTrue(result.imported());
        assertEquals(4, result.importedLayerCount());
        assertEquals(1, drawableCountInLayer(world, 0));
        assertEquals(1, drawableCountInLayer(world, 1));
        assertEquals(1, drawableCountInLayer(world, 2));
        assertEquals(1, drawableCountInLayer(world, 3));

        assertRepeat(world, drawableInLayer(world, 0), true, false);
        assertEquals(1.3f, world.getMapper(LayerParallaxComponent.class).get(layerEntity(world, 0, false)).factorX, 0.0001f);
        assertEquals(80f, world.getMapper(TransformComponent.class).get(drawableInLayer(world, 0)).y, 0.0001f);
        assertRepeat(world, drawableInLayer(world, 1), false, true);
        assertRepeat(world, drawableInLayer(world, 2), true, true);
        assertFalse(world.getMapper(RenderRepeatComponent.class).has(drawableInLayer(world, 3)));
    }

    @Test
    public void importSceneMapsTmxYDownCoordinatesToPixscapeYUpCells() throws Exception {
        Harness h = harness("tmx-import-coordinate-map");
        FileHandle tmx = simpleTmx(h, "map.tmx", "1,0,0,4");

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Imported"));
        TiledLayerComponent tiled = firstTiled(loadImportedWorld(h, result));

        assertEquals(2, tiled.tileXs.size);
        assertCell(tiled, 0, 0, 1);
        assertCell(tiled, 1, 1, 0);
    }

    @Test
    public void importScenePreservesTmxTransformFlags() throws Exception {
        Harness h = harness("tmx-import-transform-flags");
        long hFlip = TmxGidSupport.FLIPPED_HORIZONTALLY_FLAG | 1L;
        long vFlip = TmxGidSupport.FLIPPED_VERTICALLY_FLAG | 1L;
        long dFlip = TmxGidSupport.FLIPPED_DIAGONALLY_FLAG | 1L;
        FileHandle tmx = writeTmx(h.root.resolve("flags.tmx"), """
                <map orientation="orthogonal" width="3" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="terrain.png" width="16" height="16"/>
                  </tileset>
                  <layer name="Ground" width="3" height="1"><data encoding="csv">%d,%d,%d</data></layer>
                </map>
                """.formatted(hFlip, vFlip, dFlip));

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Imported"));
        TiledLayerComponent tiled = firstTiled(loadImportedWorld(h, result));

        assertEquals(TileTransformFlags.FLIP_H, tiled.tileTransformFlags.get(0));
        assertEquals(TileTransformFlags.FLIP_V, tiled.tileTransformFlags.get(1));
        assertEquals(TileTransformFlags.FLIP_D, tiled.tileTransformFlags.get(2));
    }

    @Test
    public void importSceneImportsExternalTsxImageCollectionTilesets() throws Exception {
        Harness h = harness("tmx-import-image-collection");
        FileHandle graphics = h.projectDir.child("graphics");
        FileHandle tilesets = h.projectDir.child("tilesets");
        writePng(graphics.child("tree.png"), 16, 32);
        writePng(graphics.child("rock.png"), 16, 16);
        writeString(tilesets.child("props.tsx"), """
                <tileset version="1.10" tiledversion="1.12.1" name="props" tilewidth="16" tileheight="16" tilecount="2" columns="0">
                  <tile id="0">
                    <image source="../graphics/tree.png" width="16" height="32"/>
                  </tile>
                  <tile id="1">
                    <image source="../graphics/rock.png" width="16" height="16"/>
                  </tile>
                </tileset>
                """);
        long hFlipTree = TmxGidSupport.FLIPPED_HORIZONTALLY_FLAG | 1L;
        FileHandle tmx = writeTmx(h.root.resolve("maps").resolve("props-map.tmx"), """
                <map orientation="orthogonal" width="2" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" source="../tilesets/props.tsx"/>
                  <layer name="ground" width="2" height="1">
                    <data encoding="csv">%d,2</data>
                  </layer>
                </map>
                """.formatted(hFlipTree));

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Props"));

        assertTrue(result.imported());
        assertEquals(1, result.importedTilesetCount());
        assertEquals(2, result.importedTileCount());

        TilesetAssetMeta tileset = requireTileset(h.db.findByLogicalPath("tiles/props"));
        assertEquals(16, tileset.tileWidth);
        assertEquals(16, tileset.tileHeight);
        assertEquals(16, tileset.referenceCellWidth);
        assertEquals(16, tileset.referenceCellHeight);
        assertEquals(TilesetAnchor.BOTTOM_CENTER, tileset.anchor);

        TileAssetMeta tree = requireTile(h.db.findByLogicalPath("tiles/props/0"));
        TileAssetMeta rock = requireTile(h.db.findByLogicalPath("tiles/props/1"));
        assertPngSize(h.projectDir.child(tree.sourceRelPath()), 16, 32);
        assertPngSize(h.projectDir.child(rock.sourceRelPath()), 16, 16);

        TiledLayerComponent tiled = firstTiled(loadImportedWorld(h, result));
        assertTileAsset(tiled, 0, 0, tree.id());
        assertTileAsset(tiled, 1, 0, rock.id());
        assertEquals(TileTransformFlags.FLIP_H, tiled.tileTransformFlags.get(0));
        assertEquals(TileTransformFlags.NONE, tiled.tileTransformFlags.get(1));
        assertTrue(h.cfg.getSceneMeta("Props").runtimeAvailability.tiledTileAssetIds.contains(tree.id()));
        assertTrue(h.cfg.getSceneMeta("Props").runtimeAvailability.tiledTileAssetIds.contains(rock.id()));
    }

    @Test
    public void importSceneCreatesTiledAnimationMetadataAndMapsAnimatedCells() throws Exception {
        Harness h = harness("tmx-import-tile-animation");
        FileHandle tmx = animatedTileTmx(h, "animated.tmx", "1,2,0,0");

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Animated"));

        assertTrue(result.imported());
        TileAnimationsMetaDatabase animations = TileAnimationsIO.load(
                h.projectDir.child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON)
        );
        assertEquals(1, animations.animations.size);
        TileAnimationProjectDefData def = animations.animations.get(0);
        assertTrue(def.id > 0);
        assertEquals(requireTile(h.db.findByLogicalPath("tiles/terrain/1")).id(), def.frameAssetIds[0]);
        assertEquals(requireTile(h.db.findByLogicalPath("tiles/terrain/2")).id(), def.frameAssetIds[1]);
        assertEquals(100, def.frameDurationsMs[0]);
        assertEquals(150, def.frameDurationsMs[1]);
        assertTrue(h.cfg.getSceneMeta("Animated").runtimeAvailability.tiledAnimationIds.contains(def.id));

        TiledLayerComponent tiled = firstTiled(loadImportedWorld(h, result));
        assertTileAsset(tiled, 0, 1, def.id);
        assertTileAsset(tiled, 1, 1, requireTile(h.db.findByLogicalPath("tiles/terrain/1")).id());
    }

    @Test
    public void importAssetsRetainsSeparateStaticCellAndAnimationMappings() throws Exception {
        Harness h = harness("tmx-import-animation-mappings");
        FileHandle tmx = animatedTileTmx(h, "animated-mappings.tmx", "1,2,0,0");
        TmxSceneImportService importer = h.importer();
        TmxImportPlanResult planned = importer.plan(request(tmx, "Mappings"));
        SceneMeta meta = new SceneMeta("Mappings", "mappings.json");
        importer.initializeSceneRuntimeAvailability(meta);

        TmxSceneImportService.ImportAssetsResult assets = importer.importAssets(planned.plan(), meta);
        int tilesetIndex = planned.plan().tilesets().get(0).planIndex();
        int staticBaseAssetId = requireTile(h.db.findByLogicalPath("tiles/terrain/0")).id();
        TileAnimationsMetaDatabase animations = TileAnimationsIO.load(
                h.projectDir.child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON));
        int logicalAnimationId = animations.animations.get(0).id;

        assertEquals(1, animations.animations.size);
        assertEquals(Integer.valueOf(logicalAnimationId),
                assets.animationIdsByTileset().get(tilesetIndex).get(0));
        assertEquals(Integer.valueOf(logicalAnimationId),
                assets.cellLogicalIdsByTileset().get(tilesetIndex).get(0));
        assertEquals(Integer.valueOf(staticBaseAssetId),
                assets.staticTileAssetIdsByTileset().get(tilesetIndex).get(0));
    }

    @Test
    public void importScenePreservesTransformFlagsOnAnimatedCells() throws Exception {
        Harness h = harness("tmx-import-animated-transform-flags");
        long hFlipAnimated = TmxGidSupport.FLIPPED_HORIZONTALLY_FLAG | 1L;
        FileHandle tmx = animatedTileTmx(h, "animated-flags.tmx", hFlipAnimated + ",0,0,0");

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Animated Flags"));

        TileAnimationsMetaDatabase animations = TileAnimationsIO.load(
                h.projectDir.child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON)
        );
        TiledLayerComponent tiled = firstTiled(loadImportedWorld(h, result));
        assertTileAsset(tiled, 0, 1, animations.animations.get(0).id);
        assertEquals(TileTransformFlags.FLIP_H, tiled.tileTransformFlags.get(0));
    }

    @Test
    public void importSceneReusesOneLogicalAnimationForTileLayerAndTileObject() throws Exception {
        Harness h = harness("tmx-import-shared-animation-consumers");
        FileHandle tmx = writeTmx(h.root.resolve("shared-animation.tmx"), """
                <map orientation="orthogonal" width="2" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="4" columns="2">
                    <image source="terrain.png" width="32" height="32"/>
                    <tile id="0"><animation>
                      <frame tileid="0" duration="90"/>
                      <frame tileid="1" duration="140"/>
                    </animation></tile>
                  </tileset>
                  <layer name="Ground" width="2" height="1"><data encoding="csv">1,0</data></layer>
                  <objectgroup name="Actors"><object name="SharedAnimated" gid="1" x="8" y="16"/></objectgroup>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Shared Animation"));
        World world = loadImportedWorldWithGeometry(h, result);
        TileAnimationsMetaDatabase animations = TileAnimationsIO.load(
                h.projectDir.child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON));

        assertTrue(result.imported());
        assertEquals(1, animations.animations.size);
        TileAnimationProjectDefData def = animations.animations.get(0);
        assertEquals(90, def.frameDurationsMs[0]);
        assertEquals(140, def.frameDurationsMs[1]);
        assertTileAsset(firstTiled(world), 0, 0, def.id);

        int object = visualEntityByName(world, "SharedAnimated");
        assertEquals(def.id,
                world.getMapper(TiledAnimationComponent.class).get(object).animationId);
        assertEquals(requireTile(h.db.findByLogicalPath("tiles/terrain/0")).id(),
                world.getMapper(AssetRefComponent.class).get(object).assetId);
        assertTrue(h.cfg.getSceneMeta(result.sceneName()).runtimeAvailability
                .tiledAnimationIds.contains(def.id));
    }

    @Test
    public void importSceneRuntimeExportWritesImportedTileAnimations() throws Exception {
        Harness h = harness("tmx-import-animation-runtime-export");
        FileHandle tmx = animatedTileTmx(h, "animated.tmx", "1,0,0,0");

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Animated"));
        assertTrue(result.imported());
        RuntimeExport.exportRuntime(h.cfg, h.projectDir, new FileHandle(h.root.resolve("export").toFile()));

        FileHandle runtimeAnimations = new FileHandle(h.root
                .resolve("export")
                .resolve(RuntimeExport.RUNTIME_DIR_NAME)
                .resolve(RuntimeFs.FILE_TILE_ANIMATIONS_JSON)
                .toFile());
        TileAnimationsMetaDatabase exported = TileAnimationsIO.load(runtimeAnimations);
        assertEquals(1, exported.animations.size);
        TileAnimationProjectDefData def = exported.animations.get(0);
        assertEquals(requireTile(h.db.findByLogicalPath("tiles/terrain/1")).id(), def.frameAssetIds[0]);
        assertEquals(requireTile(h.db.findByLogicalPath("tiles/terrain/2")).id(), def.frameAssetIds[1]);
        assertEquals(100, def.frameDurationsMs[0]);
        assertEquals(150, def.frameDurationsMs[1]);

        JsonValue profiles = new JsonReader().parse(new FileHandle(h.root
                .resolve("export")
                .resolve(RuntimeExport.RUNTIME_DIR_NAME)
                .resolve("tileset-profiles.json")
                .toFile()));
        JsonValue tileIds = profiles.get("tilesets").get(0).get("tileAssetIds");
        assertTrue(containsJsonInt(tileIds, def.frameAssetIds[0]));
        assertTrue(containsJsonInt(tileIds, def.frameAssetIds[1]));
    }

    @Test
    public void objectLayerNameAndPointRoleSurviveRuntimeExportAndSceneReplacement() throws Exception {
        Harness h = harness("tmx-layer-name-runtime-export");
        FileHandle tmx = writeTmx(h.root.resolve("points.tmx"), """
                <map orientation="orthogonal" width="4" height="4" tilewidth="16" tileheight="16">
                  <objectgroup name="spawn-points">
                    <object id="1" x="12" y="20"><point/>
                      <properties><property name="role" type="int" value="7"/></properties>
                    </object>
                  </objectgroup>
                  <objectgroup name="empty"/>
                </map>
                """);
        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Points"));
        assertTrue(result.imported());
        FileHandle exportDir = new FileHandle(h.root.resolve("export").toFile());
        var runtimeConfig = RuntimeExport.exportRuntime(h.cfg, h.projectDir, exportDir);
        FileHandle scene = exportDir.child(RuntimeExport.RUNTIME_DIR_NAME)
                .child(RuntimeFs.DIR_SCENES).child(result.sceneFileName());
        assertFalse(scene.readString().contains("LayerMetaComponent"));
        assertTrue(scene.readString().contains("LayerNameComponent"));

        World world = new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
        var engine = new games.pixscape.runtime.engine.PixscapeEngine();
        var worldField = engine.getClass().getDeclaredField("world");
        worldField.setAccessible(true);
        worldField.set(engine, world);
        var meta = runtimeConfig.scenes.get(result.sceneName());
        engine.getIdentityRegistry().bind(world, meta);
        try {
            SceneLoader.loadScene(world, scene, false, meta);
            world.process();
            var api = engine.api().entities();
            var points = api.findAllByTiledLayerName("spawn-points");
            assertEquals(1, points.length);
            assertEquals(7, points[0].properties().getInt("role", -1));
            assertEquals(12f, points[0].transform().x(), 0f);
            assertEquals(44f, points[0].transform().y(), 0f);
            assertFalse(world.getMapper(RenderMaterialComponent.class).has(points[0].entityId()));
            assertFalse(world.getMapper(PixscapeTagComponent.class).has(points[0].entityId()));
            assertEquals(0, api.findAllByLayerName("empty").length);

            // The existing loader can replace content in the same World.
            SceneLoader.loadScene(world, scene, true, meta);
            world.process();
            assertFalse(points[0].exists());
            assertEquals(1, api.findAllByTiledLayerName("spawn-points").length);
        } finally {
            engine.getIdentityRegistry().bind(null, null);
            world.dispose();
        }
    }

    @Test
    public void importSceneImportsTilesetsWithSpacingAndMargin() throws Exception {
        Harness h = harness("tmx-import-spacing-margin");
        int[] tileColors = {
                0xFF0000FF,
                0x00FF00FF,
                0x0000FFFF,
                0xFFFF00FF
        };
        writeSpacedAtlas(h.projectDir.child("terrain.png"), 8, 8, 2, 2, 1, 1, tileColors);
        FileHandle tmx = writeTmx(h.root.resolve("spacing.tmx"), """
                <map orientation="orthogonal" width="2" height="2" tilewidth="2" tileheight="2">
                  <tileset firstgid="1" name="terrain" tilewidth="2" tileheight="2" tilecount="4" columns="2" spacing="1" margin="1">
                    <image source="terrain.png" width="8" height="8"/>
                  </tileset>
                  <layer name="Ground" width="2" height="2"><data encoding="csv">1,2,3,4</data></layer>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Spacing"));

        assertTrue(result.imported());
        assertEquals(1, result.importedTilesetCount());
        assertEquals(4, result.importedTileCount());

        TilesetAssetMeta tileset = requireTileset(h.db.findByLogicalPath("tiles/terrain"));
        assertEquals(2, tileset.tileWidth);
        assertEquals(2, tileset.tileHeight);
        assertEquals(2, tileset.columns);
        assertEquals(2, tileset.rows);
        assertEquals(1, tileset.spacing);
        assertEquals(1, tileset.margin);

        int tile0 = requireTile(h.db.findByLogicalPath("tiles/terrain/0")).id();
        int tile1 = requireTile(h.db.findByLogicalPath("tiles/terrain/1")).id();
        int tile2 = requireTile(h.db.findByLogicalPath("tiles/terrain/2")).id();
        int tile3 = requireTile(h.db.findByLogicalPath("tiles/terrain/3")).id();

        TiledLayerComponent tiled = firstTiled(loadImportedWorld(h, result));
        assertTileAsset(tiled, 0, 1, tile0);
        assertTileAsset(tiled, 1, 1, tile1);
        assertTileAsset(tiled, 0, 0, tile2);
        assertTileAsset(tiled, 1, 0, tile3);
    }

    @Test
    public void importSceneReturnsPreflightFailureWithoutMutation() throws Exception {
        Harness h = harness("tmx-import-preflight-failure");
        FileHandle tmx = writeTmx(h.root.resolve("missing-image.tmx"), """
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="missing.png" width="16" height="16"/>
                  </tileset>
                  <layer name="Ground" width="1" height="1"><data encoding="csv">1</data></layer>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Missing"));

        assertEquals(TmxSceneImportStatus.PREFLIGHT_FAILED, result.status());
        assertFalse(h.cfg.getScenesMap().containsKey("Missing"));
        assertEquals(0, h.db.size());
    }

    @Test
    public void importSceneRollsBackSceneProjectAndAssetsWhenMutationFails() throws Exception {
        Harness h = harness("tmx-import-rollback");
        h.projectDir.child("broken.png").writeString("not a png", false, "UTF-8");
        FileHandle tmx = writeTmx(h.root.resolve("broken.tmx"), """
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="broken" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="broken.png" width="16" height="16"/>
                  </tileset>
                  <layer name="Ground" width="1" height="1"><data encoding="csv">1</data></layer>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Broken"));

        assertEquals(TmxSceneImportStatus.FAILED_ROLLED_BACK, result.status());
        assertTrue(result.rollbackAttempted());
        assertTrue(result.rollbackSucceeded());
        assertEquals("Main", h.cfg.getCurrentSceneName());
        assertFalse(h.cfg.getScenesMap().containsKey("Broken"));
        assertFalse(h.projectDir.child(StudioFs.DIR_SCENES).child("scene2.json").exists());
        assertEquals(0, h.db.size());
    }

    @Test
    public void sessionExecutesPhasesInOrderAndProducesEquivalentSuccess() throws Exception {
        Harness h = harness("tmx-import-session-success");
        FileHandle tmx = simpleTmx(h, "session.tmx", "1,2,3,4");
        TmxSceneImportSession session = h.importer().beginImport(request(tmx, "Session"));

        session.prepare();
        session.createScene();
        session.importAssets();
        session.materializeAndSaveScene();
        session.updateAtlas();
        TmxSceneImportResult result = session.persistAndFinish();

        assertTrue(result.imported());
        assertEquals("Session", result.sceneName());
        assertEquals(1, result.importedTilesetCount());
        assertEquals(4, result.importedTileCount());
        assertFalse(session.hasTemporaryWorld());
    }

    @Test
    public void sessionRejectsDuplicateAndOutOfOrderPhases() throws Exception {
        Harness h = harness("tmx-import-session-order");
        TmxSceneImportSession session = h.importer().beginImport(
                request(simpleTmx(h, "order.tmx", "1,0,0,0"), "Order")
        );

        assertIllegalState(session::importAssets);
        session.prepare();
        assertIllegalState(session::prepare);
        session.createScene();
        assertIllegalState(session::persistAndFinish);

        TmxSceneImportResult rollback = session.rollback(new RuntimeException("stop after order checks"));
        assertEquals(TmxSceneImportStatus.FAILED_ROLLED_BACK, rollback.status());
    }

    @Test
    public void sessionMaterializeFailureDisposesWorldAndRollsBackExactlyOnce() throws Exception {
        Harness h = harness("tmx-import-session-dispose");
        TmxSceneImportSession session = h.importer().beginImport(
                request(simpleTmx(h, "dispose.tmx", "1,0,0,0"), "Dispose")
        );
        session.prepare();
        session.createScene();
        session.importAssets();
        FileHandle scenesPath = h.projectDir.child(StudioFs.DIR_SCENES);
        scenesPath.deleteDirectory();
        scenesPath.writeString("not a directory", false, "UTF-8");

        RuntimeException materializeFailure = null;
        try {
            session.materializeAndSaveScene();
        } catch (RuntimeException failure) {
            materializeFailure = failure;
        }

        assertNotNull(materializeFailure);
        assertFalse(session.hasTemporaryWorld());
        TmxSceneImportResult firstRollback = session.rollback(materializeFailure);
        TmxSceneImportResult secondRollback = session.rollback(new RuntimeException("must be ignored"));
        assertSame(firstRollback, secondRollback);
        assertTrue(firstRollback.rollbackSucceeded());
        assertNull(h.cfg.getSceneMeta("Dispose"));
    }

    @Test
    public void tileObjectMaterializationFailureRollsBackPartialSceneAssetsAndRestoresCurrentScene() throws Exception {
        Harness h = harness("tmx-import-object-rollback");
        FileHandle tmx = writeTmx(h.root.resolve("object-failure.tmx"), """
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="objects" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="terrain.png" width="16" height="16"/>
                  </tileset>
                  <objectgroup name="Gameplay"><object id="1" name="Spawn" gid="1"/></objectgroup>
                </map>
                """);
        TmxSceneImportSession session = h.importer().beginImport(request(tmx, "Broken Objects"));
        session.prepare();
        session.createScene();
        session.importAssets();
        h.cfg.getSceneMeta("Broken Objects").nextEntityStableId = Integer.MAX_VALUE - 1;

        RuntimeException materializeFailure = null;
        try {
            session.materializeAndSaveScene();
        } catch (RuntimeException failure) {
            materializeFailure = failure;
        }

        assertNotNull(materializeFailure);
        assertFalse(session.hasTemporaryWorld());
        TmxSceneImportResult rollback = session.rollback(materializeFailure);
        assertEquals(TmxSceneImportStatus.FAILED_ROLLED_BACK, rollback.status());
        assertTrue(rollback.rollbackSucceeded());
        assertEquals("Main", h.cfg.getCurrentSceneName());
        assertNull(h.cfg.getSceneMeta("Broken Objects"));
        assertFalse(h.projectDir.child(StudioFs.DIR_SCENES).child("scene2.json").exists());
        assertEquals(0, h.db.size());
        assertEquals(0, h.projectDir.child(StudioFs.DIR_ORIG_TILES).list().length);
    }

    private static void assertIllegalState(Runnable action) {
        try {
            action.run();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // expected
        }
    }

    private static TmxSceneImportRequest request(FileHandle tmx, String sceneName) {
        return new TmxSceneImportRequest(tmx, sceneName, false);
    }

    @Test
    public void importSceneMaterializesStaticTileObjectsWithExactSpriteMetadataAndTransforms() throws Exception {
        Harness h = harness("tmx-import-static-tile-objects");
        writePng(h.projectDir.child("center.png"), 32, 16);
        writeString(h.projectDir.child("center.tsx"), """
                <tileset name="center" tilewidth="16" tileheight="16" tilecount="2" columns="2"
                         objectalignment="center">
                  <tileoffset x="2" y="3"/>
                  <image source="center.png" width="32" height="16"/>
                  <tile id="0" class="House"><properties>
                    <property name="tile_speed" type="float" value="0.6"/>
                  </properties></tile>
                </tileset>
                """);
        FileHandle tmx = writeTmx(h.root.resolve("tile-objects.tmx"), """
                <map orientation="orthogonal" width="10" height="10" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="inline" tilewidth="16" tileheight="16" tilecount="4" columns="2">
                    <image source="terrain.png" width="32" height="32"/>
                    <tile id="1" class="TileGem"><properties>
                      <property name="tile_label" value="inline"/>
                      <property name="collectible" type="bool" value="true"/>
                      <property name="damage" type="int" value="10"/>
                      <property name="animation_speed" type="float" value="0.5"/>
                    </properties></tile>
                  </tileset>
                  <tileset firstgid="10" source="center.tsx"/>
                  <layer name="Ground" width="10" height="10"><data encoding="csv">2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0</data></layer>
                  <group opacity="0.5" offsetx="1" offsety="2">
                    <objectgroup name="Actors" opacity="0.5" offsetx="2" offsety="2" draworder="topdown" visible="0">
                      <object id="7" name="Shared" class="gem" type="legacy" gid="2"
                              x="10" y="20" width="32" height="24" visible="0">
                        <properties>
                          <property name="animation_speed" type="float" value="0.6"/>
                          <property name="damage" type="int" value="25"/>
                          <property name="instance" value="yes"/>
                        </properties>
                      </object>
                      <object name="BottomNegative" gid="2" x="20" y="30" rotation="-90"/>
                      <object id="7" name="CenterRotated" gid="10" x="50" y="40" rotation="90"/>
                      <object name="HFlip" gid="2147483649" x="70" y="60"/>
                      <object name="VFlip" gid="1073741825" x="80" y="70"/>
                      <object name="HVFlip" gid="3221225473" x="90" y="80"/>
                      <object name="DiagonalRotated" gid="536870913" x="95" y="82"
                              width="34" height="18" rotation="30"/>
                      <object name="CleanHexPeer" gid="1" x="105" y="84"/>
                      <object name="IgnoredHex" gid="268435457" x="105" y="84"/>
                      <object name="DuplicateTile" gid="1" x="100" y="85"/>
                      <object name="DuplicateTile" gid="1" x="110" y="90"/>
                    </objectgroup>
                  </group>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Tile Objects"));
        World world = loadImportedWorld(h, result);

        assertTrue(result.imported());
        int layer = layerEntity(world, 1, false);
        assertFalse(world.getMapper(VisibilityComponent.class).get(layer).visible);
        assertEquals(11, drawableCountInLayer(world, 1));
        assertEquals(2, visualEntityCountByName(world, "DuplicateTile"));

        int shared = visualEntityByName(world, "Shared");
        TransformComponent sharedTransform = world.getMapper(TransformComponent.class).get(shared);
        DimensionsComponent sharedDimensions = world.getMapper(DimensionsComponent.class).get(shared);
        AssetRefComponent sharedAsset = world.getMapper(AssetRefComponent.class).get(shared);
        assertEquals(13f, sharedTransform.x, 0.0001f);
        assertEquals(136f, sharedTransform.y, 0.0001f);
        assertEquals(0f, sharedTransform.originX, 0.0001f);
        assertEquals(0f, sharedTransform.originY, 0.0001f);
        assertEquals(32f, sharedDimensions.width, 0.0001f);
        assertEquals(24f, sharedDimensions.height, 0.0001f);
        assertFalse(world.getMapper(VisibilityComponent.class).get(shared).visible);
        assertEquals(0x40FFFFFF, world.getMapper(TintComponent.class).get(shared).getRgba());
        assertEquals("gem", world.getMapper(PixscapeTagComponent.class).get(shared).tags.first());
        assertEquals(0.6f, world.getMapper(CustomPropertiesComponent.class).get(shared)
                .properties.getFloat("animation_speed", 0f), 0.0001f);
        CustomPropertiesComponent sharedProperties = world.getMapper(CustomPropertiesComponent.class).get(shared);
        assertEquals("inline", sharedProperties.properties.getString("tile_label", null));
        assertTrue(sharedProperties.properties.getBoolean("collectible", false));
        assertEquals(25, sharedProperties.properties.getInt("damage", 0));
        assertEquals("yes", sharedProperties.properties.getString("instance", null));
        assertTrue(world.getMapper(PixscapeIdentityComponent.class).get(shared).stableId > 0);
        assertTrue(world.getMapper(TextureRegionComponent.class).has(shared));
        assertTrue(world.getMapper(RenderMaterialComponent.class).has(shared));
        assertFalse(world.getMapper(AnimationComponent.class).has(shared));
        assertFalse(world.getMapper(TiledAnimationComponent.class).has(shared));
        assertEquals(EntityKind.SPRITE, world.getMapper(EntityMetaComponent.class).get(shared).kind);
        assertEquals(firstTiled(world).tileAssetIds.get(0), sharedAsset.assetId);
        assertTrue(h.cfg.getSceneMeta(result.sceneName()).runtimeAvailability.spriteAssetIds
                .contains(sharedAsset.assetId));

        int center = visualEntityByName(world, "CenterRotated");
        TransformComponent centerTransform = world.getMapper(TransformComponent.class).get(center);
        assertEquals(53f, centerTransform.x, 0.0001f);
        assertEquals(116f, centerTransform.y, 0.0001f);
        assertEquals(6f, centerTransform.originX, 0.0001f);
        assertEquals(11f, centerTransform.originY, 0.0001f);
        assertEquals(-MathUtils.PI / 2f, centerTransform.rotationRad, 0.0001f);
        assertTransformedCorners(world, center, new float[]{58, 122, 58, 106, 42, 106, 42, 122});
        assertEquals("House", world.getMapper(PixscapeTagComponent.class).get(center).tags.first());
        assertEquals(EntityKind.SPRITE, world.getMapper(EntityMetaComponent.class).get(center).kind);
        assertEquals(0.6f, world.getMapper(CustomPropertiesComponent.class).get(center)
                .properties.getFloat("tile_speed", 0f), 0.0001f);

        int bottomNegative = visualEntityByName(world, "BottomNegative");
        assertEquals(MathUtils.PI / 2f,
                world.getMapper(TransformComponent.class).get(bottomNegative).rotationRad, 0.0001f);
        assertTransformedCorners(world, bottomNegative,
                new float[]{7, 126, 7, 142, 23, 142, 23, 126});

        assertFlip(world, "HFlip", -1f, 1f, 16f, 0f);
        assertFlip(world, "VFlip", 1f, -1f, 0f, 16f);
        assertFlip(world, "HVFlip", -1f, -1f, 16f, 16f);
        assertTransformedBounds(world, visualEntityByName(world, "HFlip"), 73, 96, 89, 112);
        assertTransformedBounds(world, visualEntityByName(world, "VFlip"), 83, 86, 99, 102);
        assertTransformedBounds(world, visualEntityByName(world, "HVFlip"), 93, 76, 109, 92);
        int diagonal = visualEntityByName(world, "DiagonalRotated");
        assertEquals(98f, world.getMapper(TransformComponent.class).get(diagonal).x, 0.0001f);
        assertEquals(74f, world.getMapper(TransformComponent.class).get(diagonal).y, 0.0001f);
        assertEquals(34f, world.getMapper(DimensionsComponent.class).get(diagonal).width, 0.0001f);
        assertEquals(18f, world.getMapper(DimensionsComponent.class).get(diagonal).height, 0.0001f);
        assertEquivalentSpriteTransform(world,
                visualEntityByName(world, "CleanHexPeer"), visualEntityByName(world, "IgnoredHex"));
        assertEquals(0, world.getMapper(EntityIndexComponent.class).get(shared).zIndex);
        assertEquals(5, world.getMapper(EntityIndexComponent.class).get(visualEntityByName(world, "HVFlip")).zIndex);
    }

    @Test
    public void importSceneMaterializesSharedAnimatedTileObjectsWithoutTileLayerConsumer() throws Exception {
        Harness h = harness("tmx-import-isolated-animated-tile-objects");
        FileHandle tmx = animatedTileObjectTmx(h, "animated-objects.tmx");
        TmxSceneImportRequest request = new TmxSceneImportRequest(tmx, "Animated Objects", true);

        TmxSceneImportResult result = h.importer().importScene(request);
        World world = loadImportedWorldWithGeometry(h, result);
        TileAnimationsMetaDatabase animations = TileAnimationsIO.load(
                h.projectDir.child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON));
        int staticBaseAssetId = requireTile(h.db.findByLogicalPath("tiles/terrain/0")).id();

        assertTrue(result.imported());
        assertTrue(result.planResult().preflightReport().isImportableCandidate());
        assertEquals(1, animations.animations.size);
        TileAnimationProjectDefData def = animations.animations.get(0);
        assertEquals(100, def.frameDurationsMs[0]);
        assertEquals(150, def.frameDurationsMs[1]);

        int first = visualEntityByName(world, "AnimatedA");
        int second = visualEntityByName(world, "AnimatedB");
        int staticPeer = visualEntityByName(world, "StaticPeer");
        TiledAnimationComponent firstAnimation = world.getMapper(TiledAnimationComponent.class).get(first);
        TiledAnimationComponent secondAnimation = world.getMapper(TiledAnimationComponent.class).get(second);

        assertNotNull(firstAnimation);
        assertNotNull(secondAnimation);
        assertEquals(def.id, firstAnimation.animationId);
        assertEquals(def.id, secondAnimation.animationId);
        assertEquals(EntityKind.SPRITE, world.getMapper(EntityMetaComponent.class).get(first).kind);
        assertEquals(EntityKind.SPRITE, world.getMapper(EntityMetaComponent.class).get(second).kind);
        assertEquals(0, firstAnimation.frameIndex);
        assertEquals(0, firstAnimation.frameElapsedMs);
        assertEquals(-1, firstAnimation.appliedFrameAssetId);
        assertEquals(0, secondAnimation.frameIndex);
        assertEquals(0, secondAnimation.frameElapsedMs);
        assertEquals(-1, secondAnimation.appliedFrameAssetId);
        assertEquals(staticBaseAssetId, world.getMapper(AssetRefComponent.class).get(first).assetId);
        assertEquals(staticBaseAssetId, world.getMapper(AssetRefComponent.class).get(second).assetId);
        assertFalse(world.getMapper(TiledAnimationComponent.class).has(staticPeer));

        assertTrue(world.getMapper(TextureRegionComponent.class).has(first));
        assertTrue(world.getMapper(RenderMaterialComponent.class).has(first));
        assertTrue(world.getMapper(VisibilityComponent.class).has(first));
        assertTrue(world.getMapper(PixscapeIdentityComponent.class).has(first));
        assertTrue(world.getMapper(EntityMetaComponent.class).has(first));
        assertTrue(world.getMapper(EntityIndexComponent.class).has(first));
        assertEquals("enemy", world.getMapper(PixscapeTagComponent.class).get(first).tags.first());
        assertEquals(5, world.getMapper(CustomPropertiesComponent.class).get(first)
                .properties.getInt("speed", 0));
        assertEquivalentTransformAndDimensions(world, first, staticPeer);
        assertFlip(world, "AnimatedB", -1f, 1f, 16f, 0f);

        SceneMeta sceneMeta = h.cfg.getSceneMeta(result.sceneName());
        assertTrue(sceneMeta.runtimeAvailability.tiledAnimationIds.contains(def.id));
        for (int frameAssetId : def.frameAssetIds) {
            assertTrue(sceneMeta.runtimeAvailability.tiledTileAssetIds.contains(frameAssetId));
            AssetMeta frameAsset = h.db.findById(frameAssetId);
            assertNotNull(frameAsset);
            assertTrue(h.projectDir.child(StudioFs.DIR_ATLASES)
                    .child(StudioFs.DIR_INPUT)
                    .child(result.sceneTag())
                    .child(new FileHandle(frameAsset.sourceRelPath()).name())
                    .exists());
        }
        assertTrue(h.projectDir.child(StudioFs.DIR_ATLASES)
                .child(StudioFs.withExt(result.sceneTag(), StudioFs.EXT_ATLAS)).exists());

        FileHandle sceneFile = h.projectDir.child(StudioFs.DIR_SCENES).child(result.sceneFileName());
        String sceneJson = sceneFile.readString(StandardCharsets.UTF_8.name());
        assertTrue(sceneJson.contains("animationId"));
        assertFalse(sceneJson.contains("frameIndex"));
        assertFalse(sceneJson.contains("frameElapsedMs"));
        assertFalse(sceneJson.contains("appliedFrameAssetId"));

        FileHandle exportDir = new FileHandle(h.root.resolve("isolated-export").toFile());
        RuntimeExport.exportRuntime(h.cfg, h.projectDir, exportDir);
        JsonValue profiles = new JsonReader().parse(exportDir.child(RuntimeExport.RUNTIME_DIR_NAME)
                .child(RuntimeFs.FILE_TILESET_PROFILES_JSON));
        JsonValue tileIds = profiles.get("tilesets").get(0).get("tileAssetIds");
        for (int frameAssetId : def.frameAssetIds) {
            assertTrue(containsJsonInt(tileIds, frameAssetId));
        }
    }

    @Test
    public void importSceneSupportsAnimatedTileObjectFromExternalImageCollectionTileset() throws Exception {
        Harness h = harness("tmx-import-animated-collection-object");
        writePng(h.projectDir.child("collection-0.png"), 16, 16);
        writePng(h.projectDir.child("collection-1.png"), 16, 16);
        writeString(h.projectDir.child("animated-collection.tsx"), """
                <tileset name="collection" tilewidth="16" tileheight="16" tilecount="2" columns="0">
                  <tile id="0">
                    <image source="collection-0.png" width="16" height="16"/>
                    <animation>
                      <frame tileid="0" duration="80"/>
                      <frame tileid="1" duration="120"/>
                    </animation>
                  </tile>
                  <tile id="1"><image source="collection-1.png" width="16" height="16"/></tile>
                </tileset>
                """);
        FileHandle tmx = writeTmx(h.root.resolve("animated-collection-object.tmx"), """
                <map orientation="orthogonal" width="2" height="2" tilewidth="16" tileheight="16">
                  <tileset firstgid="5" source="animated-collection.tsx"/>
                  <objectgroup name="Actors"><object name="CollectionAnimated" gid="5" x="8" y="16"/></objectgroup>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Animated Collection"));
        World world = loadImportedWorld(h, result);
        int entity = visualEntityByName(world, "CollectionAnimated");
        TileAnimationsMetaDatabase animations = TileAnimationsIO.load(
                h.projectDir.child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON));

        assertTrue(result.imported());
        assertEquals(1, animations.animations.size);
        TileAnimationProjectDefData def = animations.animations.get(0);
        assertEquals(def.id, world.getMapper(TiledAnimationComponent.class).get(entity).animationId);
        assertEquals(def.frameAssetIds[0], world.getMapper(AssetRefComponent.class).get(entity).assetId);
        assertEquals(80, def.frameDurationsMs[0]);
        assertEquals(120, def.frameDurationsMs[1]);
    }

    @Test
    public void importSceneUsesActualImageCollectionTileAssetAndNativeSize() throws Exception {
        Harness h = harness("tmx-import-image-collection-object");
        writePng(h.projectDir.child("tree.png"), 23, 29);
        FileHandle tmx = writeTmx(h.root.resolve("collection-object.tmx"), """
                <map orientation="orthogonal" width="2" height="2" tilewidth="16" tileheight="16">
                  <tileset firstgid="5" name="collection" tilewidth="16" tileheight="16" tilecount="3" columns="0"
                           objectalignment="topleft">
                    <tile id="2" class="Tree"><image source="tree.png" width="23" height="29"/>
                      <properties><property name="solid" type="bool" value="true"/></properties>
                    </tile>
                  </tileset>
                  <objectgroup name="Props"><object name="Tree" gid="7" x="8" y="9" rotation="-90"/></objectgroup>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Collection Object"));
        World world = loadImportedWorld(h, result);
        int tree = visualEntityByName(world, "Tree");

        assertTrue(result.imported());
        DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class).get(tree);
        TransformComponent transform = world.getMapper(TransformComponent.class).get(tree);
        assertEquals(23f, dimensions.width, 0.0001f);
        assertEquals(29f, dimensions.height, 0.0001f);
        assertEquals(0f, transform.originX, 0.0001f);
        assertEquals(29f, transform.originY, 0.0001f);
        assertEquals(MathUtils.PI / 2f, transform.rotationRad, 0.0001f);
        assertTransformedCorners(world, tree, new float[]{8, 23, 8, 46, 37, 46, 37, 23});
        AssetMeta asset = h.db.findById(world.getMapper(AssetRefComponent.class).get(tree).assetId);
        assertNotNull(asset);
        assertTrue(h.projectDir.child(asset.sourceRelPath()).exists());
        assertEquals("Tree", world.getMapper(PixscapeTagComponent.class).get(tree).tags.first());
        assertTrue(world.getMapper(CustomPropertiesComponent.class).get(tree)
                .properties.getBoolean("solid", false));
    }

    private static Harness harness(String name) throws Exception {
        Path root = Files.createTempDirectory(name);
        FileHandle projectDir = new FileHandle(root.toFile());
        projectDir.child(StudioFs.DIR_SCENES).mkdirs();
        projectDir.child(StudioFs.DIR_ORIG_TILES).mkdirs();
        projectDir.child(StudioFs.DIR_ATLASES).mkdirs();

        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = name;
        cfg.projectFileName = name;
        cfg.projectDirectoryPath = root.toString();
        cfg.exportRootPathDir = root.resolve("export").toString();
        cfg.createSceneMeta("Main");
        ProjectConfig.setInstance(cfg);

        AssetMetaDatabase db = new AssetMetaDatabase();
        db.save(projectDir.child(StudioFs.FILE_ASSETS_JSON));
        ProjectConfig.ProjectIO.saveProject(cfg, StudioFs.requireStudioProjectFile(cfg));
        Files.writeString(root.resolve(StudioFs.DIR_SCENES).resolve("scene1.json"), "{}", StandardCharsets.UTF_8);
        writePng(projectDir.child("terrain.png"), 32, 32);
        return new Harness(root, projectDir, cfg, db);
    }

    private static FileHandle simpleTmx(Harness h, String name, String csv) throws Exception {
        return writeTmx(h.root.resolve(name), """
                <map orientation="orthogonal" width="2" height="2" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="4" columns="2">
                    <image source="terrain.png" width="32" height="32"/>
                  </tileset>
                  <layer name="Ground" width="2" height="2"><data encoding="csv">%s</data></layer>
                </map>
                """.formatted(csv));
    }

    private static FileHandle animatedTileTmx(Harness h, String name, String csv) throws Exception {
        return writeTmx(h.root.resolve(name), """
                <map orientation="orthogonal" width="2" height="2" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="4" columns="2">
                    <image source="terrain.png" width="32" height="32"/>
                    <tile id="0">
                      <animation>
                        <frame tileid="1" duration="100"/>
                        <frame tileid="2" duration="150"/>
                      </animation>
                    </tile>
                  </tileset>
                  <layer name="Ground" width="2" height="2"><data encoding="csv">%s</data></layer>
                </map>
                """.formatted(csv));
    }

    private static FileHandle animatedTileObjectTmx(Harness h, String name) throws Exception {
        long hFlipAnimated = TmxGidSupport.FLIPPED_HORIZONTALLY_FLAG | 1L;
        return writeTmx(h.root.resolve(name), """
                <map orientation="orthogonal" width="4" height="4" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="4" columns="2">
                    <image source="terrain.png" width="32" height="32"/>
                    <tile id="0" class="SourceEnemy">
                      <properties><property name="speed" type="int" value="3"/></properties>
                      <animation>
                        <frame tileid="1" duration="100"/>
                        <frame tileid="2" duration="150"/>
                      </animation>
                    </tile>
                  </tileset>
                  <objectgroup name="Actors">
                    <object id="1" name="AnimatedA" class="enemy" gid="1" x="10" y="20">
                      <properties><property name="speed" type="int" value="5"/></properties>
                    </object>
                    <object id="2" name="AnimatedB" gid="%d" x="30" y="20"/>
                    <object id="3" name="StaticPeer" gid="2" x="10" y="20"/>
                  </objectgroup>
                </map>
                """.formatted(hFlipAnimated));
    }

    private static FileHandle writeTmx(Path path, String text) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, text, StandardCharsets.UTF_8);
        return new FileHandle(path.toFile());
    }

    private static void writeString(FileHandle file, String text) {
        file.parent().mkdirs();
        file.writeString(text, false, StandardCharsets.UTF_8.name());
    }

    private static void writePng(FileHandle file, int width, int height) {
        file.parent().mkdirs();
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        try {
            pixmap.setColor(0.2f, 0.6f, 0.3f, 1f);
            pixmap.fill();
            PixmapIO.writePNG(file, pixmap);
        } finally {
            pixmap.dispose();
        }
    }

    private static void assertPngSize(FileHandle file, int width, int height) {
        Pixmap pixmap = new Pixmap(file);
        try {
            assertEquals(width, pixmap.getWidth());
            assertEquals(height, pixmap.getHeight());
        } finally {
            pixmap.dispose();
        }
    }

    private static void writeSpacedAtlas(FileHandle file,
                                         int width,
                                         int height,
                                         int tileWidth,
                                         int tileHeight,
                                         int spacing,
                                         int margin,
                                         int[] tileColors) {
        file.parent().mkdirs();
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        try {
            pixmap.setColor(0xFF00FFFF);
            pixmap.fill();
            int index = 0;
            for (int y = margin; y <= height - tileHeight; y += tileHeight + spacing) {
                for (int x = margin; x <= width - tileWidth; x += tileWidth + spacing) {
                    pixmap.setColor(tileColors[index++]);
                    pixmap.fillRectangle(x, y, tileWidth, tileHeight);
                }
            }
            PixmapIO.writePNG(file, pixmap);
        } finally {
            pixmap.dispose();
        }
    }

    private static World loadImportedWorld(Harness h, TmxSceneImportResult result) {
        World world = new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
        SceneLoader.loadScene(
                world,
                h.projectDir.child(StudioFs.DIR_SCENES).child(result.sceneFileName()),
                false,
                h.cfg.getSceneMeta(result.sceneName()));
        world.process();
        return world;
    }

    private static World loadImportedWorldWithGeometry(Harness h, TmxSceneImportResult result) {
        World world = new World(new WorldConfigurationBuilder()
                .with(new WorldSerializationManager(), new DirtyTrackerSystem(64),
                        new UpdateWorldGeometrySystem())
                .build());
        SceneLoader.loadScene(
                world,
                h.projectDir.child(StudioFs.DIR_SCENES).child(result.sceneFileName()),
                false,
                h.cfg.getSceneMeta(result.sceneName()));
        world.process();
        SceneLoader.forceFullRenderDirty(world);
        world.process();
        return world;
    }

    private static void assertCenteredRectangleGeometry(float sourceX,
                                                        float sourceY,
                                                        float width,
                                                        float height,
                                                        float rotationRad) {
        World world = new World(new WorldConfigurationBuilder()
                .with(new DirtyTrackerSystem(64), new UpdateWorldGeometrySystem())
                .build());
        try {
            int entity = world.create();
            TransformComponent transform = world.getMapper(TransformComponent.class).create(entity);
            transform.x = sourceX;
            transform.y = sourceY;
            transform.rotationRad = rotationRad;
            transform.scaleX = 1f;
            transform.scaleY = 1f;
            TmxSceneImportService.centerRectangleTransformFromTiledPivot(transform, width, height);
            transform.refreshCaches();
            DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class).create(entity);
            dimensions.width = width;
            dimensions.height = height;
            world.getMapper(AABBComponent.class).create(entity);
            OrientedBoundsComponent bounds = world.getMapper(OrientedBoundsComponent.class).create(entity);

            world.process();
            world.getSystem(DirtyTrackerSystem.class).geometry(entity, GeometryDirty.ALL);
            world.process();

            float[] expected = tiledSourceCorners(sourceX, sourceY, width, height, rotationRad);
            float[] actual = new float[8];
            OrientedBoundsHelper.toCorners(bounds, actual);
            assertSameCornerSet(expected, actual);

            AABBComponent aabb = world.getMapper(AABBComponent.class).get(entity);
            for (int i = 0; i < expected.length; i += 2) {
                assertTrue(expected[i] >= aabb.minX - 0.0001f);
                assertTrue(expected[i] <= aabb.maxX + 0.0001f);
                assertTrue(expected[i + 1] >= aabb.minY - 0.0001f);
                assertTrue(expected[i + 1] <= aabb.maxY + 0.0001f);
            }
        } finally {
            world.dispose();
        }
    }

    private static float[] tiledSourceCorners(float sourceX,
                                              float sourceY,
                                              float width,
                                              float height,
                                              float rotationRad) {
        float cos = MathUtils.cos(rotationRad);
        float sin = MathUtils.sin(rotationRad);
        float[] local = {0f, 0f, width, 0f, width, -height, 0f, -height};
        float[] corners = new float[8];
        for (int i = 0; i < local.length; i += 2) {
            corners[i] = sourceX + cos * local[i] - sin * local[i + 1];
            corners[i + 1] = sourceY + sin * local[i] + cos * local[i + 1];
        }
        return corners;
    }

    private static void assertSameCornerSet(float[] expected, float[] actual) {
        for (int i = 0; i < expected.length; i += 2) {
            boolean found = false;
            for (int j = 0; j < actual.length; j += 2) {
                if (Math.abs(expected[i] - actual[j]) <= 0.0001f
                        && Math.abs(expected[i + 1] - actual[j + 1]) <= 0.0001f) {
                    found = true;
                    break;
                }
            }
            assertTrue("Missing expected corner (" + expected[i] + ", " + expected[i + 1] + ")", found);
        }
    }

    private static TiledLayerComponent firstTiled(World world) {
        IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(TiledLayerComponent.class))
                .getEntities();
        assertEquals(1, entities.size());
        return world.getMapper(TiledLayerComponent.class).get(entities.get(0));
    }

    private static int layerEntity(World world, int index, boolean requireMap) {
        IntBag entities = world.getAspectSubscriptionManager().get(Aspect.all(TiledMapLayerComponent.class)).getEntities();
        for (int i = 0; i < entities.size(); i++) {
            int entity = entities.get(i);
            if (world.getMapper(EntityIndexComponent.class).get(entity).zIndex == index) {
                if (requireMap) {
                    assertTrue(world.getMapper(TiledLayerComponent.class).has(entity));
                }
                return entity;
            }
        }
        throw new AssertionError("Missing Tiled layer order " + index);
    }

    private static boolean belongsToTiledLayer(World world, int entity, int order) {
        int layer = layerEntity(world, order, false);
        if (entity == layer) {
            return true;
        }
        GameObjectMemberComponent member = world.getMapper(GameObjectMemberComponent.class).getSafe(entity, null);
        return member != null && member.parentStableId == world.getMapper(PixscapeIdentityComponent.class).get(layer).stableId;
    }

    private static int tiledMapEntity(World world, int layerIndex) {
        ComponentMapper<EntityIndexComponent> indexes = world.getMapper(EntityIndexComponent.class);
        IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(EntityIndexComponent.class, TiledLayerComponent.class)
                        .exclude(LayerComponent.class))
                .getEntities();
        for (int i = 0; i < entities.size(); i++) {
            int entity = entities.get(i);
            if (indexes.get(entity).zIndex == layerIndex) {
                return entity;
            }
        }
        throw new AssertionError("Missing Tiled map in layer index " + layerIndex);
    }

    private static int drawableInLayer(World world, int layerIndex) {
        ComponentMapper<EntityIndexComponent> indices = world.getMapper(EntityIndexComponent.class);
        IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(EntityIndexComponent.class, AssetRefComponent.class))
                .getEntities();
        for (int i = 0; i < entities.size(); i++) {
            int entity = entities.get(i);
            if (belongsToTiledLayer(world, entity, layerIndex)) {
                return entity;
            }
        }
        throw new AssertionError("Missing drawable in layer " + layerIndex);
    }

    private static int drawableCountInLayer(World world, int layerIndex) {
        int count = 0;
        ComponentMapper<EntityIndexComponent> indices = world.getMapper(EntityIndexComponent.class);
        IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(EntityIndexComponent.class, AssetRefComponent.class))
                .getEntities();
        for (int i = 0; i < entities.size(); i++) {
            int entity = entities.get(i);
            if (belongsToTiledLayer(world, entity, layerIndex)) {
                count++;
            }
        }
        return count;
    }

    private static int objectCountInLayer(World world, int layerIndex) {
        int count = 0;
        ComponentMapper<EntityIndexComponent> indices = world.getMapper(EntityIndexComponent.class);
        IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(EntityIndexComponent.class, TransformComponent.class,
                        PixscapeIdentityComponent.class))
                .getEntities();
        for (int i = 0; i < entities.size(); i++) {
            int entity = entities.get(i);
            if (belongsToTiledLayer(world, entity, layerIndex)
                    && !world.getMapper(TiledMapLayerComponent.class).has(entity)
                    && !world.getMapper(AssetRefComponent.class).has(entity)) {
                count++;
            }
        }
        return count;
    }

    private static int objectEntityByName(World world, String name) {
        int[] entities = objectEntitiesByName(world, name);
        if (entities.length != 1) {
            throw new AssertionError("Expected one object named " + name + " but found " + entities.length);
        }
        return entities[0];
    }

    private static int visualEntityByName(World world, String name) {
        ComponentMapper<PixscapeIdentityComponent> identities = world.getMapper(PixscapeIdentityComponent.class);
        IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(EntityIndexComponent.class, TransformComponent.class,
                        PixscapeIdentityComponent.class, AssetRefComponent.class))
                .getEntities();
        int found = -1;
        for (int i = 0; i < entities.size(); i++) {
            int entity = entities.get(i);
            if (!name.equals(identities.get(entity).name)) continue;
            if (found >= 0) throw new AssertionError("More than one visual entity named " + name);
            found = entity;
        }
        if (found < 0) throw new AssertionError("Missing visual entity named " + name);
        return found;
    }

    private static int visualEntityCountByName(World world, String name) {
        ComponentMapper<PixscapeIdentityComponent> identities = world.getMapper(PixscapeIdentityComponent.class);
        IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(EntityIndexComponent.class, PixscapeIdentityComponent.class,
                        AssetRefComponent.class))
                .getEntities();
        int count = 0;
        for (int i = 0; i < entities.size(); i++) {
            if (name.equals(identities.get(entities.get(i)).name)) count++;
        }
        return count;
    }

    private static void assertFlip(World world, String name,
                                   float scaleX, float scaleY, float originX, float originY) {
        TransformComponent transform = world.getMapper(TransformComponent.class)
                .get(visualEntityByName(world, name));
        assertEquals(scaleX, transform.scaleX, 0.0001f);
        assertEquals(scaleY, transform.scaleY, 0.0001f);
        assertEquals(originX, transform.originX, 0.0001f);
        assertEquals(originY, transform.originY, 0.0001f);
    }

    private static void assertTransformedCorners(World world, int entity, float[] expected) {
        TransformComponent transform = world.getMapper(TransformComponent.class).get(entity);
        DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class).get(entity);
        float[] local = {0f, dimensions.height, dimensions.width, dimensions.height,
                dimensions.width, 0f, 0f, 0f};
        for (int i = 0; i < local.length; i += 2) {
            float dx = (local[i] - transform.originX) * transform.scaleX;
            float dy = (local[i + 1] - transform.originY) * transform.scaleY;
            assertEquals(expected[i], transform.x + transform.cos * dx - transform.sin * dy, 0.0001f);
            assertEquals(expected[i + 1], transform.y + transform.sin * dx + transform.cos * dy, 0.0001f);
        }
    }

    private static void assertTransformedBounds(World world, int entity,
                                                float minX, float minY, float maxX, float maxY) {
        TransformComponent transform = world.getMapper(TransformComponent.class).get(entity);
        DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class).get(entity);
        float[] local = {0f, dimensions.height, dimensions.width, dimensions.height,
                dimensions.width, 0f, 0f, 0f};
        float actualMinX = Float.POSITIVE_INFINITY;
        float actualMinY = Float.POSITIVE_INFINITY;
        float actualMaxX = Float.NEGATIVE_INFINITY;
        float actualMaxY = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < local.length; i += 2) {
            float dx = (local[i] - transform.originX) * transform.scaleX;
            float dy = (local[i + 1] - transform.originY) * transform.scaleY;
            float x = transform.x + transform.cos * dx - transform.sin * dy;
            float y = transform.y + transform.sin * dx + transform.cos * dy;
            actualMinX = Math.min(actualMinX, x);
            actualMinY = Math.min(actualMinY, y);
            actualMaxX = Math.max(actualMaxX, x);
            actualMaxY = Math.max(actualMaxY, y);
        }
        assertEquals(minX, actualMinX, 0.0001f);
        assertEquals(minY, actualMinY, 0.0001f);
        assertEquals(maxX, actualMaxX, 0.0001f);
        assertEquals(maxY, actualMaxY, 0.0001f);
    }

    private static void assertEquivalentSpriteTransform(World world, int expectedEntity, int actualEntity) {
        assertEquivalentTransformAndDimensions(world, expectedEntity, actualEntity);
        assertEquals(world.getMapper(AssetRefComponent.class).get(expectedEntity).assetId,
                world.getMapper(AssetRefComponent.class).get(actualEntity).assetId);
    }

    private static void assertEquivalentTransformAndDimensions(World world,
                                                               int expectedEntity,
                                                               int actualEntity) {
        TransformComponent expected = world.getMapper(TransformComponent.class).get(expectedEntity);
        TransformComponent actual = world.getMapper(TransformComponent.class).get(actualEntity);
        assertEquals(expected.x, actual.x, 0.0001f);
        assertEquals(expected.y, actual.y, 0.0001f);
        assertEquals(expected.originX, actual.originX, 0.0001f);
        assertEquals(expected.originY, actual.originY, 0.0001f);
        assertEquals(expected.rotationRad, actual.rotationRad, 0.0001f);
        assertEquals(expected.scaleX, actual.scaleX, 0.0001f);
        assertEquals(expected.scaleY, actual.scaleY, 0.0001f);
        DimensionsComponent expectedDimensions = world.getMapper(DimensionsComponent.class).get(expectedEntity);
        DimensionsComponent actualDimensions = world.getMapper(DimensionsComponent.class).get(actualEntity);
        assertEquals(expectedDimensions.width, actualDimensions.width, 0.0001f);
        assertEquals(expectedDimensions.height, actualDimensions.height, 0.0001f);
    }

    private static int[] objectEntitiesByName(World world, String name) {
        ComponentMapper<PixscapeIdentityComponent> identities =
                world.getMapper(PixscapeIdentityComponent.class);
        IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(EntityIndexComponent.class, TransformComponent.class,
                        PixscapeIdentityComponent.class))
                .getEntities();
        int count = 0;
        for (int i = 0; i < entities.size(); i++) {
            if (name.equals(identities.get(entities.get(i)).name)
                    && !world.getMapper(AssetRefComponent.class).has(entities.get(i))) {
                count++;
            }
        }
        int[] matching = new int[count];
        int next = 0;
        for (int i = 0; i < entities.size(); i++) {
            int entity = entities.get(i);
            if (name.equals(identities.get(entity).name)
                    && !world.getMapper(AssetRefComponent.class).has(entity)) {
                matching[next++] = entity;
            }
        }
        return matching;
    }

    private static void assertCorners(World world, int entity, float[] expected) {
        TransformComponent transform = world.getMapper(TransformComponent.class).get(entity);
        DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class).get(entity);
        float[] local = {
                0f, dimensions.height,
                dimensions.width, dimensions.height,
                dimensions.width, 0f,
                0f, 0f
        };
        for (int i = 0; i < local.length; i += 2) {
            float dx = local[i] - transform.originX;
            float dy = local[i + 1] - transform.originY;
            float worldX = transform.x + transform.cos * dx - transform.sin * dy;
            float worldY = transform.y + transform.sin * dx + transform.cos * dy;
            assertEquals(expected[i], worldX, 0.0001f);
            assertEquals(expected[i + 1], worldY, 0.0001f);
        }
    }

    private static void assertSingleTag(World world, String entityName, String expectedTag) {
        int entity = objectEntityByName(world, entityName);
        PixscapeTagComponent tags = world.getMapper(PixscapeTagComponent.class).get(entity);
        assertNotNull(tags);
        assertEquals(1, tags.tags.size);
        assertEquals(expectedTag, tags.tags.first());
    }

    private static void assertRepeat(World world, int entity, boolean repeatX, boolean repeatY) {
        RenderRepeatComponent repeat = world.getMapper(RenderRepeatComponent.class).get(entity);
        assertNotNull(repeat);
        assertEquals(repeatX, repeat.repeatX);
        assertEquals(repeatY, repeat.repeatY);
    }

    private static void assertCell(TiledLayerComponent tiled, int sparseIndex, int expectedX, int expectedY) {
        assertEquals(expectedX, tiled.tileXs.get(sparseIndex));
        assertEquals(expectedY, tiled.tileYs.get(sparseIndex));
        assertTrue(tiled.tileAssetIds.get(sparseIndex) > 0);
    }

    private static void assertTileAsset(TiledLayerComponent tiled, int expectedX, int expectedY, int expectedAssetId) {
        for (int i = 0; i < tiled.tileXs.size; i++) {
            if (tiled.tileXs.get(i) == expectedX && tiled.tileYs.get(i) == expectedY) {
                assertEquals(expectedAssetId, tiled.tileAssetIds.get(i));
                return;
            }
        }
        throw new AssertionError("Missing tile cell " + expectedX + "," + expectedY);
    }

    @Test
    public void finalizesObjectReferencesWithPixscapeStableIdsNotTiledSourceIds() {
        PropertySet base = new PropertySet().putObjectStableId("target", 2002).putObjectStableId("none", -1)
                .putClass("behavior", "Behavior", new PropertySet().putObjectStableId("self", 1001));
        PropertySet finalized = TmxSceneImportService.finalizeObjectProperties(
                base, java.util.Map.of(1001, 3, 2002, 7));

        assertEquals(PropertyType.OBJECT, finalized.typeOf("target"));
        assertEquals(7, finalized.getObjectStableId("target", -1));
        assertNotEquals(2002, finalized.getObjectStableId("target", -1));
        assertEquals(-1, finalized.getObjectStableId("none", 0));
        assertEquals(3, finalized.getClassValue("behavior").properties()
                .getObjectStableId("self", -1));
        assertEquals(2002, base.getObjectStableId("target", -1));
    }

    @Test
    public void importSceneResolvesObjectPropertiesAcrossLayersAndPersistsStableIds()
            throws Exception {
        Harness h = harness("tmx-import-object-references-round-trip");
        FileHandle tmx = writeTmx(h.root.resolve("object-references.tmx"), """
                <map orientation="orthogonal" width="10" height="10" tilewidth="16" tileheight="16">
                  <objectgroup name="Triggers">
                    <properties>
                      <property name="layerTarget" type="object" value="2002"/>
                    </properties>
                    <object id="1001" name="Switch" x="10" y="20" width="8" height="6">
                      <properties>
                        <property name="target" type="object" value="2002"/>
                        <property name="none" type="object" value="0"/>
                        <property name="behavior" type="class" propertytype="Behavior">
                          <properties>
                            <property name="self" type="object" value="1001"/>
                          </properties>
                        </property>
                      </properties>
                    </object>
                  </objectgroup>
                  <objectgroup name="Targets">
                    <object id="2002" name="Door" x="40" y="50"><point/></object>
                  </objectgroup>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Object References"));
        World world = loadImportedWorld(h, result);

        assertTrue(result.imported());
        int switchEntity = objectEntityByName(world, "Switch");
        int doorEntity = objectEntityByName(world, "Door");
        ComponentMapper<PixscapeIdentityComponent> identities =
                world.getMapper(PixscapeIdentityComponent.class);
        int switchStableId = identities.get(switchEntity).stableId;
        int doorStableId = identities.get(doorEntity).stableId;
        assertTrue(switchStableId > 0);
        assertTrue(doorStableId > 0);
        assertNotEquals(1001, switchStableId);
        assertNotEquals(2002, doorStableId);

        PropertySet switchProperties = world.getMapper(CustomPropertiesComponent.class)
                .get(switchEntity).properties;
        assertEquals(doorStableId, switchProperties.getObjectStableId("target", -1));
        assertNotEquals(2002, switchProperties.getObjectStableId("target", -1));
        assertEquals(-1, switchProperties.getObjectStableId("none", 0));
        assertEquals(switchStableId, switchProperties.getClassValue("behavior").properties()
                .getObjectStableId("self", -1));

        PropertySet layerProperties = world.getMapper(CustomPropertiesComponent.class)
                .get(layerEntity(world, 0, false)).properties;
        assertEquals(doorStableId, layerProperties.getObjectStableId("layerTarget", -1));
    }

    @Test
    public void importSceneRoundTripsPolygonAndPolylineAsGenericAuthoredGeometry()
            throws Exception {
        Harness h = harness("tmx-import-authored-path-geometry");
        FileHandle tmx = writeTmx(h.root.resolve("paths.tmx"), """
                <map orientation="orthogonal" width="10" height="10" tilewidth="16" tileheight="16">
                  <objectgroup name="Shapes" offsetx="3" offsety="4">
                    <object id="1001" name="Polygon" class="Area" x="100" y="80" rotation="0">
                      <polygon points="0,0 40,10 20,50 -10,20"/>
                      <properties><property name="target" type="object" value="2002"/></properties>
                    </object>
                    <object id="2002" name="Polyline" type="Route" x="20" y="30" rotation="90">
                      <polyline points="-10,5 20,-7 40,12"/>
                      <properties><property name="label" value="exit"/></properties>
                    </object>
                  </objectgroup>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Authored Paths"));
        World world = loadImportedWorldWithGeometry(h, result);

        assertTrue(result.imported());
        int polygonEntity = objectEntityByName(world, "Polygon");
        int polylineEntity = objectEntityByName(world, "Polyline");
        PolygonComponent polygon = world.getMapper(PolygonComponent.class).get(polygonEntity);
        PolylineComponent polyline = world.getMapper(PolylineComponent.class).get(polylineEntity);
        assertNotNull(polygon);
        assertNotNull(polyline);
        assertArrayEquals(new float[]{10f, 50f, 50f, 40f, 30f, 0f, 0f, 30f},
                polygon.vertices, 0.0001f);
        assertArrayEquals(new float[]{0f, 7f, 30f, 19f, 50f, 0f}, polyline.vertices, 0.0001f);

        DimensionsComponent polygonDimensions = world.getMapper(DimensionsComponent.class).get(polygonEntity);
        DimensionsComponent polylineDimensions = world.getMapper(DimensionsComponent.class).get(polylineEntity);
        assertEquals(50f, polygonDimensions.width, 0.0001f);
        assertEquals(50f, polygonDimensions.height, 0.0001f);
        assertEquals(50f, polylineDimensions.width, 0.0001f);
        assertEquals(19f, polylineDimensions.height, 0.0001f);
        assertTrue(world.getMapper(AABBComponent.class).has(polygonEntity));
        assertTrue(world.getMapper(OrientedBoundsComponent.class).has(polygonEntity));
        assertTrue(world.getMapper(AABBComponent.class).has(polylineEntity));
        assertTrue(world.getMapper(OrientedBoundsComponent.class).has(polylineEntity));
        assertEquals(EntityKind.POLYGON, world.getMapper(EntityMetaComponent.class).get(polygonEntity).kind);
        assertEquals(EntityKind.POLYLINE, world.getMapper(EntityMetaComponent.class).get(polylineEntity).kind);

        assertWorldVertices(world.getMapper(TransformComponent.class).get(polygonEntity), polygon.vertices,
                new float[]{103f, 76f, 143f, 66f, 123f, 26f, 93f, 56f});
        assertWorldVertices(world.getMapper(TransformComponent.class).get(polylineEntity), polyline.vertices,
                new float[]{18f, 136f, 30f, 106f, 11f, 86f});

        int polygonStableId = world.getMapper(PixscapeIdentityComponent.class).get(polygonEntity).stableId;
        int polylineStableId = world.getMapper(PixscapeIdentityComponent.class).get(polylineEntity).stableId;
        assertTrue(polygonStableId > 0);
        assertTrue(polylineStableId > 0);
        assertNotEquals(2002, polylineStableId);
        assertEquals(polylineStableId, world.getMapper(CustomPropertiesComponent.class).get(polygonEntity)
                .properties.getObjectStableId("target", -1));
        assertEquals("exit", world.getMapper(CustomPropertiesComponent.class).get(polylineEntity)
                .properties.getString("label", null));
        assertSingleTag(world, "Polygon", "Area");
        assertSingleTag(world, "Polyline", "Route");
    }

    @Test
    public void importSceneRoundTripsIsometricObjectLayerGeometryAndDefaultTileObjectAlignment()
            throws Exception {
        Harness h = harness("tmx-import-isometric-objects");
        writePng(h.projectDir.child("terrain.png"), 64, 16);
        FileHandle tmx = writeTmx(h.root.resolve("isometric-objects.tmx"), """
                <map orientation="isometric" width="4" height="4" tilewidth="32" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="32" tileheight="16" tilecount="2" columns="2">
                    <image source="terrain.png" width="64" height="16"/>
                  </tileset>
                  <layer name="Below" width="4" height="4"><data encoding="csv">0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0</data></layer>
                  <group name="World" visible="0" offsetx="2" offsety="4" parallaxx="2" parallaxy="0.5">
                    <objectgroup name="Gameplay" offsetx="1" offsety="1" parallaxx="3" parallaxy="4" draworder="topdown">
                      <properties><property name="role" value="logic"/></properties>
                      <object id="101" name="Point" class="Spawn" x="24" y="24"><point/>
                        <properties><property name="target" type="object" value="102"/></properties>
                      </object>
                      <object id="102" name="Rectangle" class="Area" type="LegacyArea" x="24" y="24" width="16" height="16"/>
                      <object name="RotatedRectangle" x="24" y="24" width="16" height="16" rotation="90"/>
                      <object id="103" name="Polygon" class="Zone" x="24" y="24" rotation="90"><polygon points="0,0 16,0 16,16"/></object>
                      <object name="Polyline" x="24" y="24" rotation="90"><polyline points="0,0 16,0 16,16"/></object>
                      <object name="Tile" gid="1" x="24" y="24"/>
                    </objectgroup>
                  </group>
                  <layer name="Above" width="4" height="4"><data encoding="csv">0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0</data></layer>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Isometric Objects"));
        World world = loadImportedWorldWithGeometry(h, result);

        assertTrue(result.imported());
        assertTrue(result.planResult().hasPlan());
        assertEquals(List.of("Below", "World/Gameplay", "Above"), result.planResult().plan().layers()
                .stream().map(TmxLayerPlan::name).toList());
        TmxObjectLayerPlan layer = (TmxObjectLayerPlan) result.planResult().plan().layers().get(1);
        assertEquals(List.of(0, 1, 2, 3, 4, 5),
                layer.objects().stream().map(TmxObjectPlan::zIndex).toList());
        int objectLayer = layerEntity(world, 1, false);
        assertFalse(world.getMapper(VisibilityComponent.class).get(objectLayer).visible);
        assertEquals(6f, world.getMapper(LayerParallaxComponent.class).get(objectLayer).factorX, 0.0001f);
        assertEquals(2f, world.getMapper(LayerParallaxComponent.class).get(objectLayer).factorY, 0.0001f);
        assertEquals("logic", world.getMapper(CustomPropertiesComponent.class).get(objectLayer)
                .properties.getString("role", null));

        int point = objectEntityByName(world, "Point");
        TransformComponent pointTransform = world.getMapper(TransformComponent.class).get(point);
        assertEquals(3f, pointTransform.x, 0.0001f);
        assertEquals(27f, pointTransform.y, 0.0001f);
        assertEquals(EntityKind.TILED_POINT, world.getMapper(EntityMetaComponent.class).get(point).kind);
        assertEquals("Spawn", world.getMapper(PixscapeTagComponent.class).get(point).tags.first());

        int rectangle = objectEntityByName(world, "Rectangle");
        PolygonComponent rectanglePolygon = world.getMapper(PolygonComponent.class).get(rectangle);
        assertNotNull(rectanglePolygon);
        assertEquals(EntityKind.TILED_RECTANGLE,
                world.getMapper(EntityMetaComponent.class).get(rectangle).kind);
        assertFalse(world.getMapper(PolylineComponent.class).has(rectangle));
        assertArrayEquals(new float[]{0f, 8f, 16f, 16f, 32f, 8f, 16f, 0f},
                rectanglePolygon.vertices, 0.0001f);
        assertWorldVertices(world.getMapper(TransformComponent.class).get(rectangle), rectanglePolygon.vertices,
                new float[]{3f, 27f, 19f, 35f, 35f, 27f, 19f, 19f});
        assertEquals(32f, world.getMapper(DimensionsComponent.class).get(rectangle).width, 0.0001f);
        assertEquals(16f, world.getMapper(DimensionsComponent.class).get(rectangle).height, 0.0001f);
        assertEquals(0f, world.getMapper(TransformComponent.class).get(rectangle).rotationRad, 0f);
        assertEquals("Area", world.getMapper(PixscapeTagComponent.class).get(rectangle).tags.first());
        int rectangleStableId = world.getMapper(PixscapeIdentityComponent.class).get(rectangle).stableId;
        assertTrue(rectangleStableId > 0);
        assertEquals(rectangleStableId, world.getMapper(CustomPropertiesComponent.class).get(point)
                .properties.getObjectStableId("target", -1));

        int rotatedRectangle = objectEntityByName(world, "RotatedRectangle");
        PolygonComponent rotatedRectanglePolygon = world.getMapper(PolygonComponent.class).get(rotatedRectangle);
        assertNotNull(rotatedRectanglePolygon);
        assertArrayEquals(new float[]{32f, 8f, 64f, 4f, 32f, 0f, 0f, 4f},
                rotatedRectanglePolygon.vertices, 0.0001f);
        assertWorldVertices(world.getMapper(TransformComponent.class).get(rotatedRectangle),
                rotatedRectanglePolygon.vertices,
                new float[]{3f, 27f, 35f, 23f, 3f, 19f, -29f, 23f});
        assertEquals(0f, world.getMapper(TransformComponent.class).get(rotatedRectangle).rotationRad, 0f);

        int polygon = objectEntityByName(world, "Polygon");
        assertWorldVertices(world.getMapper(TransformComponent.class).get(polygon),
                world.getMapper(PolygonComponent.class).get(polygon).vertices,
                new float[]{3f, 27f, 35f, 23f, 3f, 19f});
        assertEquals(0f, world.getMapper(TransformComponent.class).get(polygon).rotationRad, 0f);
        int polyline = objectEntityByName(world, "Polyline");
        assertWorldVertices(world.getMapper(TransformComponent.class).get(polyline),
                world.getMapper(PolylineComponent.class).get(polyline).vertices,
                new float[]{3f, 27f, 35f, 23f, 3f, 19f});
        assertFalse(world.getMapper(PolygonComponent.class).has(polyline));
        assertEquals(0f, world.getMapper(TransformComponent.class).get(polyline).rotationRad, 0f);

        int tile = visualEntityByName(world, "Tile");
        TransformComponent tileTransform = world.getMapper(TransformComponent.class).get(tile);
        assertEquals(3f, tileTransform.x, 0.0001f);
        assertEquals(27f, tileTransform.y, 0.0001f);
        assertEquals(16f, tileTransform.originX, 0.0001f);
        assertEquals(0f, tileTransform.originY, 0.0001f);
        assertEquals(EntityKind.SPRITE, world.getMapper(EntityMetaComponent.class).get(tile).kind);
    }

    private static void assertWorldVertices(TransformComponent transform,
                                            float[] vertices,
                                            float[] expected) {
        assertEquals(expected.length, vertices.length);
        for (int i = 0; i < vertices.length; i += 2) {
            float x = (vertices[i] - transform.originX) * transform.scaleX;
            float y = (vertices[i + 1] - transform.originY) * transform.scaleY;
            float worldX = transform.x + transform.cos * x - transform.sin * y;
            float worldY = transform.y + transform.sin * x + transform.cos * y;
            assertEquals(expected[i], worldX, 0.0001f);
            assertEquals(expected[i + 1], worldY, 0.0001f);
        }
    }

    private static boolean containsJsonInt(JsonValue array, int value) {
        if (array == null || !array.isArray()) {
            return false;
        }
        for (JsonValue item = array.child; item != null; item = item.next) {
            if (item.asInt() == value) {
                return true;
            }
        }
        return false;
    }

    private static TilesetAssetMeta requireTileset(AssetMeta meta) {
        assertNotNull(meta);
        assertTrue(meta instanceof TilesetAssetMeta);
        return (TilesetAssetMeta) meta;
    }

    private static TileAssetMeta requireTile(AssetMeta meta) {
        assertNotNull(meta);
        assertTrue(meta instanceof TileAssetMeta);
        return (TileAssetMeta) meta;
    }

    private record Harness(Path root,
                           FileHandle projectDir,
                           ProjectConfig cfg,
                           AssetMetaDatabase db) {
        TmxSceneImportService importer() {
            return new TmxSceneImportService(cfg, projectDir, db);
        }
    }
}
