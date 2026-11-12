package games.pixscape.studio.service;

import com.artemis.World;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessFiles;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.TileAnimationRegistry;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.asset.*;
import games.pixscape.studio.configuration.EditorSettings;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.document.EditorDocumentManager;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.io.TileAnimationsIO;
import games.pixscape.studio.scene.SceneEditorContext;
import games.pixscape.studio.service.asset.StudioAssetVisualResolver;
import games.pixscape.studio.service.atlas.SceneAtlasInputService;
import games.pixscape.studio.service.tiled.TiledAllocatorService;
import games.pixscape.studio.service.tiled.TiledPaintService;
import games.pixscape.studio.ui.docking.DockManager;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;
import games.pixscape.studio.ui.main.WorldCanvas;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class TileDeletionGuardTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    private final ProjectConfig cfg = new ProjectConfig();
    private final ProjectConfig previous = ProjectConfig.getInstance();
    private final EditorDocumentManager documents = new EditorDocumentManager();
    private final List<SceneEditorContext> contexts = new ArrayList<>();
    private final AssetMetaDatabase assets = new AssetMetaDatabase();
    private final TileAnimationsMetaDatabase animations = TileAnimationsIO.createEmpty();
    private final TileAnimationRegistry registry = new TileAnimationRegistry();
    private StudioGlTestScope globals;
    private FileHandle project, source, catalog, definitions;
    private TileAssetMeta grass;
    private TileAnimationProjectDefData wave;
    private SceneService service;
    private SceneEditorContext castle;
    private WorldCanvas canvas;

    @Before
    public void setup() throws Exception {
        globals = new StudioGlTestScope();
        Gdx.files = new HeadlessFiles();
        Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
                new Class<?>[]{Application.class}, (proxy, method, args) -> null);
        project = new FileHandle(temp.newFolder("studio"));
        cfg.projectTitle = "Deletion test";
        cfg.projectFileName = "fixture";
        cfg.projectDirectoryPath = project.path();
        cfg.exportRootPathDir = temp.newFolder("export").getAbsolutePath();
        cfg.createSceneMeta("castle");
        cfg.createSceneMeta("Test2");
        cfg.setCurrentSceneByName("castle");
        ProjectConfig.setInstance(cfg);
        scene("castle").writeString("{}", false);
        scene("Test2").writeString("{}", false);

        source = project.child(StudioFs.DIR_ORIG_TILES).child("terrain/grass.png");
        source.writeBytes(new byte[]{0, 1, 2}, false);
        AssetMeta terrain = assets.registerIfAbsent(AssetType.TILESET, "tiles/terrain", null, AssetMeta.AssetScope.USER);
        grass = (TileAssetMeta) assets.registerIfAbsent(AssetType.TILE, "tiles/terrain/grass",
                StudioFs.DIR_ORIG_TILES + "/terrain/grass.png", AssetMeta.AssetScope.USER);
        grass.tilesetId = terrain.id();
        catalog = project.child(StudioFs.FILE_ASSETS_JSON);
        wave = new TileAnimationProjectDefData();
        wave.id = assets.allocateNextId();
        wave.name = "wave";
        assets.save(catalog);
        animations.animations.add(wave);
        definitions = project.child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON);
        TileAnimationsIO.save(animations, definitions);

        // Only the graphical shells bypass construction. The service, documents and Worlds are real.
        canvas = shell(WorldCanvas.class);
        set(canvas, "tiledPaintService", new TiledPaintService());
        set(canvas, "tileAnimationRegistry", registry);
        set(canvas, "assetVisualResolver", new StudioAssetVisualResolver(new AtlasRuntimeService(),
                assets::findById, StudioAssetVisualResolver.projectStandaloneAccess()));
        StudioApplicationAdapter app = new StudioApplicationAdapter();
        set(app, "canvas", canvas);
        set(app, "editorDocumentManager", documents);
        DockManager dock = shell(DockManager.class);
        set(dock, "panels", new Array<>());
        set(app, "dockManager", dock);
        service = new SceneService(app, canvas);
        set(service, "assetMetaDatabase", assets);
        set(service, "tileAnimationsMetaDatabase", animations);
        castle = open("castle");
        castle.attached();
        set(canvas, "sceneEditorContext", castle);
    }

    @After
    public void tearDown() throws Exception {
        for (SceneEditorContext context : contexts) context.dispose();
        ProjectConfig.setInstance(previous);
        globals.close();
    }

    @Test
    public void closedSceneProtectsTopLevelBag() {
        painted("Test2", "tileAssetIds: {items: [" + grass.id() + "], size: 1}");
        assertTilesetProtected("terrain", "Test2");
    }

    @Test
    public void closedSceneProtectsNestedBag() {
        painted("Test2", "data: {tileAssetIds: {items: [" + grass.id() + "], size: 1}}");
        assertTilesetProtected("terrain", "Test2");
    }

    @Test
    public void savedCellsInMultipleScenesStillBlockWhenActiveWorldIsEmpty() {
        for (String name : List.of("castle", "Test2")) painted(name, "tileAssetIds: [" + grass.id() + "]");
        assertTilesetProtected("terrain", "castle", "Test2");
    }

    @Test
    public void closedSceneProtectsPaintedAnimation() {
        painted("Test2", "data: {tileAssetIds: {items: [" + wave.id + "], size: 1}}");
        assertAnimationProtected("Test2");
    }

    @Test
    public void unsavedCellsInInactiveTabProtectTileAndAnimation() {
        SceneEditorContext test2 = open("Test2");
        World world = test2.world();
        TiledLayerComponent layer = world.getMapper(TiledLayerComponent.class).create(world.create());
        layer.data = new TiledMapLayerData(2, 1, 8, 8, 2);
        layer.data.setTile(0, 0, grass.id());
        layer.data.setTile(1, 0, wave.id);
        world.process();
        test2.markExplicitSaveRequired();
        documents.openScene(castle.sceneIdentity(), "castle", castle);
        assertEquals("{}", scene("Test2").readString());
        assertTilesetProtected("terrain", "Test2");
        assertAnimationProtected("Test2");
    }

    @Test
    public void unreadableSceneBlocksBothDeletions() {
        scene("Test2").writeString("{invalid", false);
        assertTilesetProtected("Cannot read scene 'Test2'");
        assertAnimationProtected("Cannot read scene 'Test2'");
    }

    @Test
    public void tileAnimationFrameProtectsItsTileset() {
        wave.frameAssetIds = new int[]{grass.id()};
        wave.frameDurationsMs = new int[]{120};
        TileAnimationsIO.save(animations, definitions);
        assertTilesetProtected("terrain", "wave");
    }

    @Test
    public void animationBaseProtectsItsTilesetAfterAllFramesMoveElsewhere() {
        var other = assets.registerIfAbsent(AssetType.TILESET, "tiles/other", null, AssetMeta.AssetScope.USER);
        TileAssetMeta frame = (TileAssetMeta) assets.registerIfAbsent(AssetType.TILE, "tiles/other/7",
                "orig/tiles/other/7.png", AssetMeta.AssetScope.USER);
        frame.tilesetId = other.id();
        wave.baseTileAssetId = grass.id();
        wave.frameAssetIds = new int[]{frame.id()};
        wave.frameDurationsMs = new int[]{120};
        grass.properties.putInt("cost", 3);
        frame.properties.putInt("cost", 9);
        assets.save(catalog);
        TileAnimationsIO.save(animations, definitions);
        byte[] savedAnimations = definitions.readBytes();
        assertTilesetProtected("terrain", "wave", "base tile " + grass.id());
        assertArrayEquals(savedAnimations, definitions.readBytes());
        assertEquals(grass.id(), wave.baseTileAssetId);
        assertEquals(3, grass.properties.getInt("cost", -1));
        assertEquals(9, frame.properties.getInt("cost", -1));
    }

    @Test
    public void unusedTilesetWithIdOnlyAfterBagSizeCanBeDeleted() {
        painted("Test2", "tileAssetIds: {items: [0," + grass.id() + "], size: 1}");
        service.deleteTilesetDirectory("terrain");
        assertFalse(source.parent().exists());
        assertNull(assets.findById(grass.id()));
        assertNull(AssetMetaDatabase.load(catalog).findById(grass.tilesetId));
    }

    @Test
    public void availabilityAloneAllowsAnimationDeletionAndIsCleanedUp() throws Exception {
        cfg.getSceneMeta("Test2").runtimeAvailability.tiledAnimationIds.add(wave.id);
        registry.put(wave.id, new int[]{grass.id()}, new int[]{120});
        GdxNativesLoader.load();
        String home = System.getProperty("user.home");
        var previousRecents = new ArrayList<>(EditorSettings.get().recentProjectPaths);
        try {
            System.setProperty("user.home", temp.newFolder("home").getAbsolutePath());
            new SceneAtlasInputService().syncSceneAtlasInputForSave(cfg, castle.world(), assets, animations);
            service.deleteTileAnimation(wave.id);
        } finally {
            System.setProperty("user.home", home);
            EditorSettings.get().recentProjectPaths = previousRecents;
        }
        assertTrue(cfg.getSceneMeta("Test2").runtimeAvailability.tiledAnimationIds.isEmpty());
        assertEquals(0, TileAnimationsIO.load(definitions).animations.size);
        assertFalse(registry.contains(wave.id));
    }

    private void assertTilesetProtected(String... details) {
        String snapshot = assets.toSnapshotJson();
        byte[] saved = catalog.readBytes();
        byte[] tile = source.readBytes();
        cfg.getSceneMeta("Test2").runtimeAvailability.tiledTileAssetIds.add(grass.id());
        String message = assertThrows(IllegalStateException.class, () -> service.deleteTilesetDirectory("terrain")).getMessage();
        for (String detail : details) assertTrue(message, message.contains(detail));
        assertEquals(snapshot, assets.toSnapshotJson());
        assertArrayEquals(saved, catalog.readBytes());
        assertArrayEquals(tile, source.readBytes());
        assertTrue(cfg.getSceneMeta("Test2").runtimeAvailability.tiledTileAssetIds.contains(grass.id()));
    }

    private void assertAnimationProtected(String... details) {
        cfg.getSceneMeta("Test2").runtimeAvailability.tiledAnimationIds.add(wave.id);
        registry.put(wave.id, new int[]{grass.id()}, new int[]{120});
        var registered = registry.get(wave.id);
        byte[] saved = definitions.readBytes();
        String message = assertThrows(IllegalStateException.class, () -> service.deleteTileAnimation(wave.id)).getMessage();
        for (String detail : details) assertTrue(message, message.contains(detail));
        assertSame(wave, animations.animations.first());
        assertSame(registered, registry.get(wave.id));
        assertArrayEquals(saved, definitions.readBytes());
        assertTrue(cfg.getSceneMeta("Test2").runtimeAvailability.tiledAnimationIds.contains(wave.id));
    }

    private FileHandle scene(String name) {
        return project.child(StudioFs.DIR_SCENES).child(cfg.getSceneMeta(name).getFile());
    }

    private void painted(String name, String tiled) {
        scene(name).writeString("{entities: {0: {components: {TiledLayerComponent: {" + tiled + "}}}}}", false);
    }

    private SceneEditorContext open(String name) {
        var context = new SceneEditorContext(cfg.canonicalSceneTag(name), new StudioEditingModeService());
        context.initializeWorld(new World(), new TiledAllocatorService(), cfg.getSceneMeta(name));
        contexts.add(context);
        documents.openScene(context.sceneIdentity(), name, context);
        return context;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static <T> T shell(Class<T> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }
}
