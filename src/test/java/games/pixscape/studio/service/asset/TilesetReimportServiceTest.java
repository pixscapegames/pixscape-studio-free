package games.pixscape.studio.service.asset;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.studio.asset.*;
import games.pixscape.studio.configuration.*;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.atlas.AtlasStudioService;
import org.junit.Test;
import java.nio.file.Files;
import java.util.*;
import static games.pixscape.studio.service.asset.TilesetAssetImportService.*;
import static org.junit.Assert.*;

public class TilesetReimportServiceTest {
    static { GdxNativesLoader.load(); }
    private static final TilesetReimportService.PackingValidation PACK = AtlasStudioService::validateTilesetImportCandidate;
    private static final TilesetReimportService.UsageProof UNUSED = ids -> Map.of();
    private static final class Fixture {
        final AssetMetaDatabase db = new AssetMetaDatabase();
        final FileHandle project, source, tiles;
        final TilesetReimportService service = new TilesetReimportService(db);
        Fixture() throws Exception {
            project = new FileHandle(Files.createTempDirectory("tileset-update").toFile());
            source = project.child("external/terrain"); source.mkdirs();
            tiles = project.child(StudioFs.DIR_ORIG_TILES);
        }
        TilesetReimportService.Plan plan(int id, TilesetReimportService.UsageProof usage) {
            return service.prepareDirectory(new TilesetDirectoryImportRequest(source, tiles), id, usage);
        }
        TilesetImportResult apply(TilesetReimportService.Plan plan) {
            return service.apply(plan, PACK, () -> db.save(project.child("assets.json")));
        }
        TileAssetMeta tile(String key) {
            for (int i=0; i<db.size(); i++) if (db.assetAt(i) instanceof TileAssetMeta tile && key.equals(tile.importSourceKey)) return tile;
            throw new AssertionError(key);
        }
    }
    @Test public void stablePathsSurviveReorderCompletelyChangedPixelsAndNormalRemoval() throws Exception {
        Fixture f = new Fixture();
        png(f.source.child("b.png"), 8,8,0xff0000ff);
        png(f.source.child("b_n.png"),8,8,0x12345640);
        int set = f.apply(f.plan(-1,UNUSED)).tilesetAssetId();
        int id = f.tile("b.png").id();
        TilesetAssetMeta meta = (TilesetAssetMeta)f.db.findById(set);
        meta.offsetX=13; meta.anchor=TilesetAnchor.BOTTOM_CENTER;
        png(f.source.child("a.png"),8,8,0x112233ff);
        png(f.source.child("b.png"),8,8,0x00ff00ff);
        f.source.child("b_n.png").delete();
        var plan=f.plan(set,UNUSED);
        assertEquals(Integer.valueOf(id),plan.matches().get("b.png"));
        assertTrue(plan.notices().get(0).contains("removed"));
        f.apply(plan);
        assertEquals(id,f.tile("b.png").id());
        assertNull(f.tile("b.png").normalSourceRelPath);
        assertEquals(13,((TilesetAssetMeta)f.db.findById(set)).offsetX);
        assertEquals(TilesetAnchor.BOTTOM_CENTER,((TilesetAssetMeta)f.db.findById(set)).anchor);
        assertEquals(0x00ff00ff,pixel(f.project.child(f.tile("b.png").sourceRelPath())));
        assertEquals(2,f.db.size()-1);
    }
    @Test public void recursiveCompanionsAreByteExactIndependentOfColorAliasesAndReload() throws Exception {
        Fixture f=new Fixture();
        png(f.source.child("left/a.png"),8,8,0xaabbccff);
        png(f.source.child("left/a_n.png"),8,8,0x12345640);
        png(f.source.child("right/a.png"),8,8,0xaabbccff);
        png(f.source.child("right/a_n.png"),8,8,0x98765480);
        png(f.source.child("orphan_n.png"),8,8,0xffffffff);
        var plan=f.plan(-1,UNUSED);
        assertEquals(2,plan.sources().size()); assertTrue(plan.notices().get(0).contains("Orphan"));
        f.apply(plan);
        var restored=AssetMetaDatabase.load(f.project.child("assets.json"));
        for(String key:List.of("left/a.png","right/a.png")) {
            var tile=f.tile(key); var reloaded=(TileAssetMeta)restored.findById(tile.id());
            assertEquals(key,reloaded.importSourceKey);
            assertArrayEquals(f.source.child(key.replace(".png","_n.png")).readBytes(),f.project.child(reloaded.normalSourceRelPath).readBytes());
        }
        assertNotEquals(f.tile("left/a.png").normalSourceRelPath,f.tile("right/a.png").normalSourceRelPath);
    }
    @Test public void missingUnusedTileIsDeletedButReferencedTileKeepsItsNormal() throws Exception {
        Fixture f=new Fixture();
        for(String name:List.of("a","b","c")) png(f.source.child(name+".png"),8,8,0xff00ffff);
        png(f.source.child("b_n.png"),8,8,0x12345640);
        int set=f.apply(f.plan(-1,UNUSED)).tilesetAssetId();
        int b=f.tile("b.png").id(), c=f.tile("c.png").id();
        String normal=f.tile("b.png").normalSourceRelPath;
        f.source.child("b.png").delete(); f.source.child("b_n.png").delete(); f.source.child("c.png").delete();
        var plan=f.plan(set,ids->Map.of(b,"Referenced by closed scene"));
        assertEquals(Set.of(c),plan.removed());
        f.apply(plan);
        assertNull(f.db.findById(c)); assertEquals(normal,((TileAssetMeta)f.db.findById(b)).normalSourceRelPath);
        assertTrue(f.project.child(normal).exists());
    }
    @Test public void missingProvenanceRejectsReimportWithoutMutationOrNameRecovery() throws Exception {
        Fixture f=new Fixture(); png(f.source.child("barrel_E.png"),8,8,0xff00ffff);
        int set=f.apply(f.plan(-1,UNUSED)).tilesetAssetId();
        var tile=f.tile("barrel_E.png"); tile.importSourceKey=null;
        String before=f.db.toSnapshotJson(); byte[] catalog=f.project.child("assets.json").readBytes();
        byte[] image=f.project.child(tile.sourceRelPath()).readBytes();
        var failure=assertThrows(IllegalArgumentException.class,()->f.plan(set,UNUSED));
        assertTrue(failure.getMessage().contains("lacks required reimport provenance"));
        assertEquals(before,f.db.toSnapshotJson());
        assertArrayEquals(catalog,f.project.child("assets.json").readBytes());
        assertArrayEquals(image,f.project.child(tile.sourceRelPath()).readBytes());
        tile.importSourceKey="barrel_E.png"; ((TilesetAssetMeta)f.db.findById(set)).importKind=null;
        assertThrows(IllegalArgumentException.class,()->f.plan(set,UNUSED));
    }
    @Test public void ambiguousCurrentSourcesAreRefusedBeforeMutation() throws Exception {
        Fixture f=new Fixture(); png(f.source.child("a.png"),8,8,0xffffffff); png(f.source.child("b.png"),8,8,0xffffffff);
        int set=f.apply(f.plan(-1,UNUSED)).tilesetAssetId();
        f.tile("b.png").importSourceKey="A.png";
        String before=f.db.toSnapshotJson();
        var failure=assertThrows(IllegalArgumentException.class,()->f.plan(set,UNUSED));
        assertTrue(failure.getMessage().contains("Ambiguous current import")); assertEquals(before,f.db.toSnapshotJson());
    }
    @Test public void missingGridKeyIsNotRecoveredFromStoredCoordinates() throws Exception {
        Fixture f=new Fixture(); FileHandle sheet=f.project.child("sheet.png"); png(sheet,8,8,0xffffffff);
        var req=new TilesetAtlasImportRequest(sheet,f.tiles,8,8,0,0);
        var imported=f.apply(f.service.prepareAtlas(req,-1,UNUSED));
        ((TileAssetMeta)f.db.findById(imported.localTileAssetIds().get(0))).importSourceKey=null;
        String before=f.db.toSnapshotJson();
        assertThrows(IllegalArgumentException.class,()->f.service.prepareAtlas(req,imported.tilesetAssetId(),UNUSED));
        assertEquals(before,f.db.toSnapshotJson());
    }
    @Test public void normalsAreAddedAfterReloadAndUnusedMissingTilesAreProvedAcrossClosedScenes() throws Exception {
        Fixture f=new Fixture(); for(String key:List.of("a","b","c")) png(f.source.child(key+".png"),8,8,0xffffffff);
        int set=f.apply(f.plan(-1,UNUSED)).tilesetAssetId(); int b=f.tile("b.png").id(), c=f.tile("c.png").id();
        var reloaded=AssetMetaDatabase.load(f.project.child("assets.json"));
        assertEquals(((TilesetAssetMeta)f.db.findById(set)).importSourceUri,((TilesetAssetMeta)reloaded.findById(set)).importSourceUri);
        png(f.source.child("a_n.png"),8,8,0x12345640); f.source.child("b.png").delete(); f.source.child("c.png").delete();
        ProjectConfig cfg=new ProjectConfig(); cfg.projectDirectoryPath=f.project.path();
        cfg.getScenesMap().put("Closed",new SceneMeta("Closed","closed.json"));
        f.project.child("scenes/closed.json").writeString("{entities:{0:{components:{AssetRefComponent:{assetId:"+b+"}}}}}",false);
        var service=new TilesetReimportService(reloaded);
        var plan=service.prepareDirectory(new TilesetDirectoryImportRequest(f.source,f.tiles),set,
                ids->new AssetUsageScanner(null,cfg,reloaded).tileRetentionReasons(ids,List.of()));
        assertEquals(Set.of(c),plan.removed()); assertTrue(plan.retained().containsKey(b));
        service.apply(plan,PACK,()->reloaded.save(f.project.child("assets.json")));
        assertNull(reloaded.findById(c)); assertNotNull(((TileAssetMeta)reloaded.findById(f.tile("a.png").id())).normalSourceRelPath);
        assertNotNull(reloaded.findById(b));
    }
    @Test public void uppercaseNamesRecognizeCompanionsAndReplacementIsByteExact() throws Exception {
        Fixture f=new Fixture(); png(f.source.child("barrel_E.png"),8,8,0xffffffff);
        png(f.source.child("barrel_E_n.png"),8,8,0x12345640);
        var first=f.plan(-1,UNUSED); assertTrue(first.notices().isEmpty());
        int set=f.apply(first).tilesetAssetId(); int id=f.tile("barrel_E.png").id();
        png(f.source.child("barrel_E_n.png"),8,8,0x98765480);
        f.apply(f.plan(set,UNUSED));
        assertEquals(id,f.tile("barrel_E.png").id());
        assertArrayEquals(f.source.child("barrel_E_n.png").readBytes(),f.project.child(f.tile("barrel_E.png").normalSourceRelPath).readBytes());
    }
    @Test public void northColorsAreImportedWithTheirNormalsAndKeepIdsAfterReload() throws Exception {
        Fixture f=new Fixture(); String north="stoneWallRoundBroken_N.png", normal="stoneWallRoundBroken_N_n.png";
        png(f.source.child(north),8,8,0xaabbccff); png(f.source.child(normal),8,8,0x12345640);
        png(f.source.child("sub/stone_N_detail.png"),8,8,0xffffffff);
        png(f.source.child("sub/plank_n_detail.png"),8,8,0xffffffff);
        var first=f.plan(-1,UNUSED);
        assertEquals(Set.of(north,"sub/stone_N_detail.png","sub/plank_n_detail.png"),
                new HashSet<>(first.sources().stream().map(TilesetReimportService.Source::key).toList()));
        assertTrue(first.notices().isEmpty());
        int set=f.apply(first).tilesetAssetId(), id=f.tile(north).id();
        assertArrayEquals(f.source.child(normal).readBytes(),f.project.child(f.tile(north).normalSourceRelPath).readBytes());
        var reloaded=AssetMetaDatabase.load(f.project.child("assets.json"));
        var service=new TilesetReimportService(reloaded);
        png(f.source.child(north),8,8,0x00ff00ff); png(f.source.child(normal),8,8,0x98765480);
        var update=service.prepareDirectory(new TilesetDirectoryImportRequest(f.source,f.tiles),set,UNUSED);
        assertEquals(Integer.valueOf(id),update.matches().get(north)); assertTrue(update.notices().isEmpty());
        service.apply(update,PACK,()->reloaded.save(f.project.child("assets.json")));
        var tile=(TileAssetMeta)reloaded.findById(id);
        assertEquals(north,tile.importSourceKey);
        assertArrayEquals(f.source.child(north).readBytes(),f.project.child(tile.sourceRelPath()).readBytes());
        assertArrayEquals(f.source.child(normal).readBytes(),f.project.child(tile.normalSourceRelPath).readBytes());
    }
    @Test public void reimportAddsPreviouslyExcludedNorthColorsWithoutChangingExistingIds() throws Exception {
        Fixture f=new Fixture(); png(f.source.child("stone_E.png"),8,8,0xffffffff);
        png(f.source.child("stone_E_n.png"),8,8,0x12345640);
        int set=f.apply(f.plan(-1,UNUSED)).tilesetAssetId(), east=f.tile("stone_E.png").id();
        png(f.source.child("stone_N.png"),8,8,0xaabbccff); png(f.source.child("stone_N_n.png"),8,8,0x98765480);
        var update=f.plan(set,UNUSED);
        assertEquals(Map.of("stone_E.png",east),update.matches()); assertEquals(2,update.sources().size());
        assertTrue(update.removed().isEmpty()); assertTrue(update.notices().isEmpty());
        f.apply(update);
        assertEquals(east,f.tile("stone_E.png").id()); assertNotEquals(east,f.tile("stone_N.png").id());
        assertArrayEquals(f.source.child("stone_N_n.png").readBytes(),f.project.child(f.tile("stone_N.png").normalSourceRelPath).readBytes());
    }
    @Test public void northNamedGridIsColorAndItsLowercaseSuffixCompanionIsNormal() throws Exception {
        Fixture f=new Fixture(); FileHandle color=f.project.child("sheet_N.png"),normal=f.project.child("sheet_N_n.png");
        png(color,16,8,0xffffffff);png(normal,16,8,0x12345640);
        var request=new TilesetAtlasImportRequest(color,f.tiles,8,8,0,0);
        var first=f.apply(f.service.prepareAtlas(request,-1,UNUSED));
        assertEquals(2,first.localTileAssetIds().size());
        for(int id:first.localTileAssetIds().values())
            assertEquals(0x12345640,pixel(f.project.child(((TileAssetMeta)f.db.findById(id)).normalSourceRelPath)));
        assertEquals(2,f.service.prepareAtlas(request,first.tilesetAssetId(),UNUSED).matches().size());
        assertThrows(IllegalArgumentException.class,()->f.service.prepareAtlas(new TilesetAtlasImportRequest(normal,f.tiles,8,8,0,0),-1,UNUSED));
    }
    @Test public void tiledImageCollectionKeepsNorthLocalIdAndExcludesOnlyNormalCompanions() throws Exception {
        Fixture f=new Fixture();FileHandle color=f.source.child("stone_N.png"),normal=f.source.child("stone_N_n.png");
        png(color,8,8,0xffffffff);png(normal,8,8,0x12345640);
        var request=new TilesetImageCollectionImportRequest("north",f.tiles,8,8,2,List.of(
                new ImageCollectionTileSource(0,color,color.name(),8,8),
                new ImageCollectionTileSource(1,normal,normal.name(),8,8)),null);
        var service=new TilesetAssetImportService(f.db);
        var first=service.importImageCollection(request);
        assertEquals(Set.of(0),first.localTileAssetIds().keySet());assertEquals(2,f.db.size());
        int id=first.localTileAssetIds().get(0);var tile=(TileAssetMeta)f.db.findById(id);
        assertEquals("stone_N.png",tile.importSourceKey);
        assertArrayEquals(normal.readBytes(),f.project.child(tile.normalSourceRelPath).readBytes());
        assertEquals(first.localTileAssetIds(),service.importImageCollection(request).localTileAssetIds());
    }
    @Test public void incompatibleNormalOrColorIsRejectedBeforePublication() throws Exception {
        Fixture f=new Fixture(); png(f.source.child("a.png"),8,8,0xffffffff);
        int set=f.apply(f.plan(-1,UNUSED)).tilesetAssetId(); String before=f.db.toSnapshotJson();
        png(f.source.child("a_n.png"),9,8,0x12345640);
        assertThrows(IllegalArgumentException.class,()->f.plan(set,UNUSED));
        f.source.child("a_n.png").delete(); png(f.source.child("a.png"),9,8,0xffffffff);
        assertThrows(IllegalArgumentException.class,()->f.plan(set,UNUSED)); assertEquals(before,f.db.toSnapshotJson());
    }
    @Test public void packingAndCatalogFailureRollBackFilesMetadataAndKeepAllocatorHighWater() throws Exception {
        Fixture f=new Fixture(); png(f.source.child("a.png"),8,8,0xff0000ff);
        int set=f.apply(f.plan(-1,UNUSED)).tilesetAssetId(); int id=f.tile("a.png").id();
        String path=f.tile("a.png").sourceRelPath(); byte[] before=f.project.child(path).readBytes();
        byte[] catalog=f.project.child("assets.json").readBytes(); int next=f.db.nextId();
        png(f.source.child("a.png"),8,8,0x00ff00ff); png(f.source.child("b.png"),8,8,0xffffffff);
        assertThrows(IllegalStateException.class,()->f.service.apply(f.plan(set,UNUSED),
                (candidate,colors)->{throw new IllegalStateException("Injected packing failure");},()->{}));
        assertNull(f.db.findByLogicalPath("tiles/terrain/1")); assertTrue(f.db.nextId()>next);
        assertArrayEquals(before,f.project.child(path).readBytes());
        assertEquals(f.db.nextId(),AssetMetaDatabase.load(f.project.child("assets.json")).nextId());
        assertEquals(id,f.tile("a.png").id());
        assertThrows(IllegalStateException.class,()->f.service.apply(f.plan(set,UNUSED),PACK,()->{
            f.db.save(f.project.child("assets.json")); throw new IllegalStateException("Injected catalog acceptance failure"); }));
        assertArrayEquals(before,f.project.child(path).readBytes());
        var restored=AssetMetaDatabase.load(f.project.child("assets.json"));
        assertEquals(2,restored.size()); assertEquals(f.db.nextId(),restored.nextId());
        assertEquals(path,restored.findById(id).sourceRelPath());
    }
    @Test public void gridNormalSlicingPreservesChannelsAndRejectsRemapping() throws Exception {
        Fixture f=new Fixture(); FileHandle color=f.project.child("sheet.png"), normal=f.project.child("sheet_n.png");
        png(color,16,8,0xffffffff); png(normal,16,8,0x12345640);
        var request=new TilesetAtlasImportRequest(color,f.tiles,8,8,0,0);
        int set=f.apply(f.service.prepareAtlas(request,-1,UNUSED)).tilesetAssetId();
        for(int i=0;i<f.db.size();i++) if(f.db.assetAt(i) instanceof TileAssetMeta tile)
            assertEquals(0x12345640,pixel(f.project.child(tile.normalSourceRelPath)));
        assertThrows(IllegalArgumentException.class,()->f.service.prepareAtlas(new TilesetAtlasImportRequest(color,f.tiles,16,8,0,0),set,UNUSED));
    }
    @Test public void appendedGridRowsKeepExistingCellIdentities() throws Exception {
        Fixture f=new Fixture(); FileHandle color=f.project.child("sheet.png");
        png(color,16,8,0xffffffff);
        var request=new TilesetAtlasImportRequest(color,f.tiles,8,8,0,0);
        var first=f.apply(f.service.prepareAtlas(request,-1,UNUSED));
        png(color,16,16,0x112233ff);
        var plan=f.service.prepareAtlas(request,first.tilesetAssetId(),UNUSED);
        assertEquals(2,plan.matches().size()); assertEquals(4,plan.sources().size());
        var second=f.apply(plan);
        assertEquals(first.localTileAssetIds().get(0),second.localTileAssetIds().get(0));
        assertEquals(first.localTileAssetIds().get(1),second.localTileAssetIds().get(1));
    }
    @Test public void runtimeExportKeepsDistinctNormalResourcesForAliasedColors() throws Exception {
        Fixture f = new Fixture();
        for (String name : List.of("a", "b")) png(f.source.child(name + ".png"), 8, 8, 0xaabbccff);
        png(f.source.child("a_n.png"), 8, 8, 0x12345640);
        png(f.source.child("b_n.png"), 8, 8, 0x98765480);
        f.apply(f.plan(-1,UNUSED));
        ProjectConfig cfg = new ProjectConfig(); cfg.projectTitle="Normal export"; cfg.projectFileName="test";
        FileHandle exported = f.project.child("export"); cfg.exportRootPathDir=exported.path(); cfg.createSceneMeta("Main");
        f.project.child("scenes/scene1.json").writeString("{}",false);
        int a=f.tile("a.png").id(),b=f.tile("b.png").id();
        cfg.getCurrentSceneMeta().runtimeAvailability.tiledTileAssetIds.add(a);
        cfg.getCurrentSceneMeta().runtimeAvailability.tiledTileAssetIds.add(b);
        RuntimeExport.exportRuntime(cfg,f.project,exported);
        FileHandle runtime=exported.child(RuntimeExport.RUNTIME_DIR_NAME);
        var profiles=games.pixscape.runtime.loading.RuntimeProjectIO.loadTilesetProfiles(runtime);
        assertEquals("normals/"+a+".png",profiles.normalMapSourceForTileAsset(a));
        assertEquals("normals/"+b+".png",profiles.normalMapSourceForTileAsset(b));
        assertArrayEquals(f.source.child("a_n.png").readBytes(),runtime.child("normals/"+a+".png").readBytes());
        assertArrayEquals(f.source.child("b_n.png").readBytes(),runtime.child("normals/"+b+".png").readBytes());
        assertFalse(runtime.child("orig").exists());
    }
    @Test public void usageProofCoversClosedScenesGameObjectsAnimationsAndUnsavedWorlds() throws Exception {
        Fixture f=new Fixture(); ProjectConfig cfg=new ProjectConfig(); cfg.projectDirectoryPath=f.project.path();
        SceneMeta closed=new SceneMeta("Closed","closed.json"); cfg.getScenesMap().put("Closed",closed);
        f.project.child("scenes/closed.json").writeString("{entities:{0:{components:{TiledLayerComponent:{tileAssetIds:{items:[31,35],size:1}}}}}}",false);
        f.project.child(StudioFs.DIR_GAME_OBJECTS+"/object.json").writeString("{components:{AssetRefComponent:{assetId:32}}}",false);
        f.project.child("tiled-animations.json").writeString("{animations:[{id:999,frameAssetIds:[33]}]}",false);
        World world=new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
        try {
            var manager=world.getSystem(WorldSerializationManager.class); manager.setSerializer(new JsonArtemisSerializer(world));
            world.getMapper(AssetRefComponent.class).create(world.create()).assetId=34; world.process();
            var reasons=new AssetUsageScanner(world,cfg,f.db).tileRetentionReasons(Set.of(31,32,33,34,35),List.of(world));
            assertEquals(Set.of(31,32,33,34),reasons.keySet());
            f.project.child("scenes/closed.json").writeString("broken JSON {",false);
            assertTrue(new AssetUsageScanner(world,cfg,f.db).tileRetentionReasons(Set.of(35),List.of(world)).containsKey(35));
        } finally { world.dispose(); }
    }
    static void png(FileHandle file,int w,int h,int rgba) {
        file.parent().mkdirs(); Pixmap p=new Pixmap(w,h,Pixmap.Format.RGBA8888);
        try { p.setBlending(Pixmap.Blending.None); p.setColor(rgba); p.fill(); PixmapIO.writePNG(file,p); }
        finally { p.dispose(); }
    }
    static int pixel(FileHandle file) { Pixmap p=new Pixmap(file); try{return p.getPixel(0,0);}finally{p.dispose();} }
}
