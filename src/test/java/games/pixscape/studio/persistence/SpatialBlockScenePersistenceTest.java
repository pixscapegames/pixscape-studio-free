package games.pixscape.studio.persistence;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.spatial.CompiledSpatialStructure;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.spatial.SpatialStructureCompiler;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.SceneService;
import games.pixscape.studio.service.spatial.SpatialTileSelectionService;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

public class SpatialBlockScenePersistenceTest {
    @Test public void verticalJoinSurvivesStudioSaveRuntimeExportAndReload() throws Exception {
        World world=worldWithSerialization();
        for(int i=0;i<2;i++) {
            int map=world.create();world.getMapper(PixscapeIdentityComponent.class).create(map).stableId=i+1;
            games.pixscape.runtime.component.TiledLayerComponent tiled=world.getMapper(games.pixscape.runtime.component.TiledLayerComponent.class).create(map);
            tiled.projection=TiledProjection.ISO;tiled.tileWidth=32;tiled.tileHeight=16;
            tiled.mapWidthCells=16;tiled.mapHeightCells=8;tiled.chunkSize=4;tiled.defaultTileAltitude=i*80;
            tiled.originY=i*80;tiled.defaultTileHeight=90;tiled.data=tiled.createMapData();
            for(int y=0;y<8;y++)for(int x=0;x<16;x++)tiled.data.setTile(x,y,1);
            SpatialBlocksComponent blocks=world.getMapper(SpatialBlocksComponent.class).create(map);blocks.nextSpatialBlockId=2;
            SpatialBlockData wall=new SpatialBlockData();wall.id=wall.structureId=1;wall.x=1;wall.y=i==0?2:2.05f;
            wall.width=3;wall.depth=i==0?.2f:.3f;wall.altitude=i*80;wall.height=i==0?60:90;
            wall.beginAuthoredLinkedTileRefs();for(int x=1;x<4;x++)wall.addLinkedTileRef(x,2,1);blocks.blocks.add(wall);
        }
        world.process();var search=games.pixscape.studio.service.spatial.SpatialVerticalJoinProposal.search(world,0,1);
        Assert.assertEquals(1,search.candidates.size);
        var proposal=games.pixscape.studio.service.spatial.SpatialVerticalJoinProposal.create(world,search.selected,1,search.candidates.first());
        Assert.assertTrue(proposal.diagnostic,proposal.valid);
        var history=new games.pixscape.studio.history.HistoryManager(8);
        var command=new games.pixscape.studio.history.commands.SpatialVerticalJoinCommand(world,history.historyIds(),proposal);
        history.execute(command);Assert.assertTrue(command.rejectionReason(),command.wasApplied());
        java.nio.file.Path projectDir=java.nio.file.Files.createTempDirectory("vertical-join-studio");
        java.nio.file.Path exportDir=java.nio.file.Files.createTempDirectory("vertical-join-runtime");
        FileHandle project=new FileHandle(projectDir.toFile());project.child("scenes").mkdirs();
        SceneService.saveScene(world,project.child("scenes/scene1.json"),false);
        var config=new games.pixscape.studio.configuration.ProjectConfig();config.projectFileName="vertical-join";config.projectTitle="Vertical join";
        config.exportRootPathDir=exportDir.toString();config.createSceneMeta("Main");config.getCurrentSceneMeta().nextEntityStableId=3;
        new games.pixscape.studio.asset.AssetMetaDatabase().save(project.child(games.pixscape.studio.io.StudioFs.FILE_ASSETS_JSON));
        games.pixscape.studio.configuration.RuntimeExport.exportRuntime(config,project,new FileHandle(exportDir.toFile()));
        World loaded=worldWithSerialization();SceneMetaRuntime meta=sceneMeta();meta.nextEntityStableId=3;
        try {
            SceneLoader.loadScene(loaded,new FileHandle(exportDir.toFile()).child("pixscape-project/scenes/scene1.json"),false,meta);loaded.process();
            IntBag maps=loaded.getAspectSubscriptionManager().get(Aspect.all(PixscapeIdentityComponent.class,SpatialBlocksComponent.class)).getEntities();
            Assert.assertEquals(2,maps.size());
            for(int i=0;i<maps.size();i++) {
                int e=maps.get(i),stable=loaded.getMapper(PixscapeIdentityComponent.class).get(e).stableId;
                var blocks=loaded.getMapper(SpatialBlocksComponent.class).get(e);
                var tiled=loaded.getMapper(games.pixscape.runtime.component.TiledLayerComponent.class).get(e);
                Assert.assertEquals(0,blocks.links.size);
                if(stable==1){Assert.assertEquals(80,blocks.blocks.first().height,0);Assert.assertEquals(80,tiled.defaultTileHeight,0);
                    Assert.assertEquals(2.05f,blocks.blocks.first().y,.0001f);Assert.assertEquals(.3f,blocks.blocks.first().depth,.0001f);}
                else {Assert.assertEquals(80,blocks.blocks.first().altitude,0);Assert.assertEquals(90,blocks.blocks.first().height,0);Assert.assertEquals(80,tiled.defaultTileAltitude,0);}
            }
        } finally {loaded.dispose();world.dispose();}
    }
    @Test
    public void voluntaryBorderSurvivesRealStudioSaveExportAndRuntimeLoad() throws Exception {
        World world = worldWithSerialization();
        int map = world.create();
        world.getMapper(PixscapeIdentityComponent.class).create(map).stableId = 1;
        SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).create(map);
        blocks.nextSpatialBlockId = 3;
        for (int i = 0; i < 2; i++) {
            SpatialBlockData wall = new SpatialBlockData();
            wall.id = i + 1; wall.structureId = 1; wall.x = i == 0 ? 1 : 4;
            wall.y = 1; wall.width = i == 0 ? 3 : 2; wall.depth = .3f; wall.height = 90;
            wall.beginAuthoredLinkedTileRefs();
            for (int gx = (int)wall.x; gx < wall.x + wall.width; gx++) wall.addLinkedTileRef(gx, 1, 1);
            blocks.blocks.add(wall);
        }
        blocks.links.add(new games.pixscape.runtime.spatial.SpatialBlockLink(1, 2, 2, 1));
        world.process();
        FileHandle file = tempSceneFile("voluntary-border-roundtrip");
        SceneService.saveScene(world, file, false);
        java.nio.file.Path projectDir = java.nio.file.Files.createTempDirectory("voluntary-link-export-studio");
        java.nio.file.Path exportDir = java.nio.file.Files.createTempDirectory("voluntary-link-export-runtime");
        games.pixscape.studio.configuration.ProjectConfig cfg = new games.pixscape.studio.configuration.ProjectConfig();
        cfg.projectTitle = "Voluntary link export";
        cfg.projectFileName = "voluntary-link-export";
        cfg.exportRootPathDir = exportDir.toString();
        cfg.createSceneMeta("Main");
        FileHandle project = new FileHandle(projectDir.toFile());
        project.child(games.pixscape.studio.io.StudioFs.DIR_SCENES).mkdirs();
        FileHandle exportedScene = project.child(games.pixscape.studio.io.StudioFs.DIR_SCENES).child("scene1.json");
        file.copyTo(exportedScene);
        new games.pixscape.studio.asset.AssetMetaDatabase().save(project.child(games.pixscape.studio.io.StudioFs.FILE_ASSETS_JSON));
        games.pixscape.studio.configuration.RuntimeExport.exportRuntime(cfg, project, new FileHandle(exportDir.toFile()));
        file = new FileHandle(exportDir.toFile()).child(games.pixscape.studio.configuration.RuntimeExport.RUNTIME_DIR_NAME)
                .child(games.pixscape.studio.io.StudioFs.DIR_SCENES).child("scene1.json");
        Assert.assertTrue(file.exists());
        World loaded = worldWithSerialization();
        try {
            SceneLoader.loadScene(loaded, file, false, sceneMeta()); loaded.process();
            int id = loaded.getAspectSubscriptionManager().get(Aspect.all(SpatialBlocksComponent.class)).getEntities().get(0);
            SpatialBlocksComponent restored = loaded.getMapper(SpatialBlocksComponent.class).get(id);
            Assert.assertEquals(1, restored.links.size);
            Assert.assertEquals(1, restored.links.first().firstBlockId);
            Assert.assertEquals(2, restored.links.first().secondBlockId);
            Assert.assertEquals(4, SpatialStructureCompiler.compile(restored.blocks, restored.links, 1).segmentCount());
        } finally { loaded.dispose(); world.dispose(); }
    }

    @Test
    public void lightingPlacementAndLocalGeometrySurviveActualSceneSaveLoad() {
        World world = worldWithSerialization();
        int entity = world.create();
        world.getMapper(PixscapeIdentityComponent.class).create(entity).stableId = 1;
        games.pixscape.runtime.component.SurfaceLightingComponent surface = world.getMapper(
                games.pixscape.runtime.component.SurfaceLightingComponent.class).create(entity);
        surface.shadowCaster = true;
        surface.anchorV = .25f;
        surface.altitude = 42f;
        games.pixscape.runtime.render.lighting.LocalSurfaceDescription description =
                new games.pixscape.runtime.render.lighting.LocalSurfaceDescription();
        description.xyz = new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0};
        description.uv = new float[]{0, 0, 1, 0, 0, 1};
        description.normals = new float[]{0, 0, 1, 0, 0, 1, 0, 0, 1};
        description.triangles = new int[]{0, 1, 2};
        description.shadowCaster = true;
        surface.description = description;
        games.pixscape.runtime.component.TiledLayerComponent map = world.getMapper(
                games.pixscape.runtime.component.TiledLayerComponent.class).create(entity);
        map.projection = TiledProjection.ISO;
        map.tileWidth = 64;
        map.tileHeight = 32;
        map.mapWidthCells = map.mapHeightCells = 4;
        map.chunkSize = 4;
        map.defaultTileAltitude = 128;
        games.pixscape.runtime.render.lighting.TileSurfaceDescription tile =
                new games.pixscape.runtime.render.lighting.TileSurfaceDescription();
        tile.assetId = 17;
        tile.geometry = description.copy();
        map.lightingDescriptions.add(tile);
        games.pixscape.runtime.render.lighting.TileSurfaceDescription rounded=new games.pixscape.runtime.render.lighting.TileSurfaceDescription();
        rounded.assetId=18;rounded.rounded=new games.pixscape.runtime.render.lighting.RoundedWallDescription();
        rounded.rounded.centerGX=1.25f;map.lightingDescriptions.add(rounded);
        games.pixscape.runtime.component.light.PointLightComponent light = world.getMapper(
                games.pixscape.runtime.component.light.PointLightComponent.class).create(entity);
        light.height = 224;
        light.shadowQuality = 2;
        light.shadowResolution = 1024;
        world.process();
        FileHandle file = tempSceneFile("lighting-scene-roundtrip");
        SceneService.saveScene(world, file, false);
        World loaded = worldWithSerialization();
        try {
            SceneLoader.loadScene(loaded, file, false, sceneMeta());
            loaded.process();
            int restoredId = loaded.getAspectSubscriptionManager().get(Aspect.all(
                    games.pixscape.runtime.component.SurfaceLightingComponent.class)).getEntities().get(0);
            games.pixscape.runtime.component.SurfaceLightingComponent restored = loaded.getMapper(
                    games.pixscape.runtime.component.SurfaceLightingComponent.class).get(restoredId);
            Assert.assertTrue(restored.shadowCaster);
            Assert.assertEquals(.25f, restored.anchorV, 0);
            Assert.assertEquals(42f, restored.altitude, 0);
            restored.description.validate();
            games.pixscape.runtime.component.TiledLayerComponent restoredMap = loaded.getMapper(
                    games.pixscape.runtime.component.TiledLayerComponent.class).get(restoredId);
            Assert.assertEquals(map.defaultTileAltitude, restoredMap.defaultTileAltitude, 0);
            Assert.assertEquals(17, restoredMap.lightingDescriptions.first().assetId);
            restoredMap.lightingDescriptions.first().geometry.validate();
            Assert.assertEquals(2,restoredMap.lightingDescriptions.size);
            Assert.assertEquals(1.25f,restoredMap.lightingDescriptions.get(1).rounded.centerGX,0);
            restoredMap.lightingDescriptions.get(1).validate();
            games.pixscape.runtime.component.light.PointLightComponent restoredLight = loaded.getMapper(
                    games.pixscape.runtime.component.light.PointLightComponent.class).get(restoredId);
            Assert.assertEquals(224, restoredLight.height, 0);
            Assert.assertEquals(2, restoredLight.shadowQuality);
            Assert.assertEquals(1024, restoredLight.shadowResolution);
        } finally {
            loaded.dispose();
            world.dispose();
        }
    }

    @Test
    public void layerCreatedThroughLayerServiceWithSpatialBlockLoadsSuccessfully() {
        World world = worldWithSerialization();
        SceneMetaRuntime meta = sceneMeta();
        IdentityRegistry identities = new IdentityRegistry();
        identities.bind(world, meta);
        LayerService layers = new LayerService(world, null, new HistoryIdRegistry(), identities);
        int layerId = layers.getLayerEntity(layers.addLayerTop("Spatial"));
        SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        blocks.nextSpatialBlockId = 2;
        SpatialBlockData block = new SpatialBlockData();
        block.id = 1;
        block.structureId = 1;
        block.width = 1f;
        block.depth = 1f;
        blocks.blocks.add(block);
        world.process();
        FileHandle file = tempSceneFile("layer-service-spatial-roundtrip");
        SceneService.saveScene(world, file, false);

        World loaded = worldWithSerialization();
        try {
            SceneLoader.loadScene(loaded, file, false, meta);
        } finally {
            loaded.dispose();
            world.dispose();
        }
    }

    @Test
    public void spatialBlockFootprintSurvivesSceneSaveLoadRoundtripWithoutAnchors() {
        World world = worldWithSerialization();
        int layerId = world.create();
        world.getMapper(PixscapeIdentityComponent.class).create(layerId).stableId = 1;
        SpatialBlocksComponent spatialBlocks = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        spatialBlocks.nextSpatialBlockId = 2;
        SpatialBlockData block = new SpatialBlockData();
        block.id = 1;
        block.name = "Tall wall";
        block.x = 2f;
        block.y = 3f;
        block.width = 2f;
        block.depth = 2f;
        block.altitude = 155f;
        block.height = 16f;
        block.actorOccluder = true;
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(2, 3, 101);
        block.addLinkedTileRef(3, 3, 102);
        block.addLinkedTileRef(2, 4, 103);
        block.addLinkedTileRef(3, 4, 104);
        spatialBlocks.blocks.add(block);
        world.process();

        FileHandle file = tempSceneFile("spatial-block-footprint-roundtrip");
        SceneService.saveScene(world, file, false);

        String json = file.readString("UTF-8");
        Assert.assertFalse(json.contains("\"" + legacyAnchorField("Gx") + "\""));
        Assert.assertFalse(json.contains("\"" + legacyAnchorField("Gy") + "\""));
        Assert.assertFalse(json.contains("\"enabled\""));
        Assert.assertFalse(json.contains("\"orientation\""));

        World loaded = worldWithSerialization();
        SceneLoader.loadScene(loaded, file, false, sceneMeta());
        loaded.process();

        SpatialBlockData restored = restoredBlock(loaded);
        Assert.assertEquals(2f, restored.x, 0.0001f);
        Assert.assertEquals(3f, restored.y, 0.0001f);
        Assert.assertEquals(2f, restored.width, 0.0001f);
        Assert.assertEquals(2f, restored.depth, 0.0001f);
        Assert.assertEquals(155f, restored.altitude, 0.0001f);
        Assert.assertEquals(16f, restored.height, 0.0001f);
        Assert.assertTrue(restored.linkedTileRefsAuthored);
        Assert.assertEquals(4, restored.linkedTileRefs.size);
        Assert.assertEquals(2, restored.linkedTileRefs.get(0).gx);
        Assert.assertEquals(3, restored.linkedTileRefs.get(0).gy);
        Assert.assertEquals(101, restored.linkedTileRefs.get(0).tileAssetId);
        Assert.assertEquals(3, restored.linkedTileRefs.get(3).gx);
        Assert.assertEquals(4, restored.linkedTileRefs.get(3).gy);
        Assert.assertEquals(104, restored.linkedTileRefs.get(3).tileAssetId);
    }

    @Test
    public void normalizedSelectionRoundtripNeverRestoresRawEmptyBorders() {
        TiledMapLayerData map = new TiledMapLayerData(
                8, 8, 64, 32, 4, TiledProjection.ISO);
        map.setTile(2, 3, 201);
        map.setTile(3, 3, 202);
        map.setTile(4, 3, 203);
        SpatialTileSelectionService selection = new SpatialTileSelectionService();
        selection.beginDrag(7, 0, 1);
        selection.updateDrag(7, 5);
        selection.finishDrag();
        SpatialBlockData normalized = selection.toSpatialBlockData(map, 0f, 10f);
        Assert.assertNotNull(normalized);
        normalized.id = 1;
        normalized.structureId = 1;
        com.badlogic.gdx.utils.Array<SpatialBlockData> authored = new com.badlogic.gdx.utils.Array<>(SpatialBlockData[]::new);
        authored.add(normalized);
        String compiledBefore = compiledSignature(SpatialStructureCompiler.compile(authored, 1));

        World world = worldWithSerialization();
        int layerId = world.create();
        world.getMapper(PixscapeIdentityComponent.class).create(layerId).stableId = 1;
        SpatialBlocksComponent authoredComponent = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        authoredComponent.nextSpatialBlockId = 2;
        authoredComponent.blocks.add(normalized);
        authoredComponent.revision = 7;
        world.process();
        FileHandle file = tempSceneFile("normalized-spatial-selection-roundtrip");
        SceneService.saveScene(world, file, false);
        String json = file.readString("UTF-8");
        Assert.assertFalse(json.contains("CompiledSpatialStructure"));
        Assert.assertFalse(json.contains("segmentCount"));
        Assert.assertFalse(json.contains("revision"));

        World loaded = worldWithSerialization();
        SceneLoader.loadScene(loaded, file, false, sceneMeta());
        loaded.process();
        SpatialBlockData restored = restoredBlock(loaded);
        Assert.assertEquals(0, loaded.getMapper(SpatialBlocksComponent.class)
                .get(loaded.getAspectSubscriptionManager().get(Aspect.all(SpatialBlocksComponent.class))
                        .getEntities().get(0)).revision);

        Assert.assertEquals(2f, restored.x, 0f);
        Assert.assertEquals(3f, restored.y, 0f);
        Assert.assertEquals(3f, restored.width, 0f);
        Assert.assertEquals(1f, restored.depth, 0f);
        Assert.assertEquals(3, restored.linkedTileRefs.size);
        for (int i = 0; i < 3; i++) {
            Assert.assertEquals(2 + i, restored.linkedTileRefs.get(i).gx);
            Assert.assertEquals(3, restored.linkedTileRefs.get(i).gy);
            Assert.assertEquals(201 + i, restored.linkedTileRefs.get(i).tileAssetId);
        }
        com.badlogic.gdx.utils.Array<SpatialBlockData> restoredWalls =
                new com.badlogic.gdx.utils.Array<>(SpatialBlockData[]::new);
        restoredWalls.add(restored);
        Assert.assertEquals(compiledBefore,
                compiledSignature(SpatialStructureCompiler.compile(restoredWalls, restored.structureId)));
    }

    private static String compiledSignature(CompiledSpatialStructure structure) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < structure.segmentCount(); i++) {
            out.append(structure.startX(i)).append(',').append(structure.startY(i)).append('-')
                    .append(structure.endX(i)).append(',').append(structure.endY(i)).append('/')
                    .append(structure.normalX(i)).append(',').append(structure.normalY(i)).append(';');
        }
        return out.toString();
    }

    private static SpatialBlockData restoredBlock(World world) {
        IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(SpatialBlocksComponent.class))
                .getEntities();
        Assert.assertEquals(1, entities.size());
        SpatialBlocksComponent component =
                world.getMapper(SpatialBlocksComponent.class).get(entities.get(0));
        Assert.assertEquals(1, component.blocks.size);
        return component.blocks.first();
    }

    private static World worldWithSerialization() {
        return new World(new WorldConfiguration()
                .setSystem(new WorldSerializationManager()));
    }

    private static SceneMetaRuntime sceneMeta() {
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextEntityStableId = 2;
        return meta;
    }

    private static FileHandle tempSceneFile(String name) {
        File dir = new File(System.getProperty("java.io.tmpdir"), "pixscape-studio-tests");
        Assert.assertTrue(dir.exists() || dir.mkdirs());
        File file = new File(dir, name + ".json");
        if (file.exists()) {
            Assert.assertTrue(file.delete());
        }
        return new FileHandle(file);
    }

    private static String legacyAnchorField(String axis) {
        return "anchor" + axis;
    }
}
