package games.pixscape.studio.history.commands;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.Json;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.render.lighting.RoundedWallDescription;
import games.pixscape.runtime.render.lighting.TileSurfaceDescription;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.spatial.SpatialBlockLink;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.RenderTiledSyncSystem;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfiles;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.SceneService;
import games.pixscape.studio.service.tiled.TiledBrushSession;
import games.pixscape.studio.service.tiled.TiledMutationRejectedException;
import games.pixscape.studio.service.tiled.TiledSpatialMutationPlanner;
import org.junit.Test;

import java.nio.file.Files;

import static org.junit.Assert.*;

public class UnlitSpatialCommandsTest {
    @Test public void orthoShadowAndOccluderEditsUseOwningSceneDuringUndoRedo() {
        ProjectConfig previous = ProjectConfig.getInstance();
        try (Fixture f = new Fixture()) {
            ProjectConfig other = new ProjectConfig();
            other.createSceneMeta("Other");
            other.getCurrentSceneMeta().lightingEnabled = true;
            ProjectConfig.setInstance(other);
            SpatialBlockData edited = f.block().copy();
            edited.shadowCaster = true;
            edited.lightOccluder = true;
            edited.shadeUnlitFaces = true;
            int revision = f.blocks.geometryRevision();
            f.history.execute(f.edit(edited));
            assertTrue(f.block().shadowCaster);
            assertTrue(f.block().lightOccluder);
            assertTrue(f.block().shadeUnlitFaces);
            assertTrue(f.blocks.geometryRevision() > revision);
            for (int i = 0; i < 2; i++) {
                f.history.undo();
                assertFalse(f.block().shadowCaster);
                f.history.redo();
                assertTrue(f.block().shadowCaster);
                assertTrue(f.block().lightOccluder);
            }
            assertEquals(1, f.history.getCursor());
            assertEquals(0, f.state.lightingMaps.size);
            assertFalse(f.scene.lightingEnabled);
            assertTrue(other.getCurrentSceneMeta().lightingEnabled);
        } finally { ProjectConfig.setInstance(previous); }
    }

    @Test public void offStillRejectsInvalidGeometryAndLinksAtomically() {
        try (Fixture f = new Fixture()) {
            SpatialBlockData invalid = f.block().copy();
            invalid.width = -1;
            invalid.shadowCaster = true;
            f.history.execute(f.edit(invalid));
            assertFalse(f.history.canUndo());
            assertEquals(1, f.block().width, 0);
            assertFalse(f.block().shadowCaster);
            Array<SpatialBlockLink> links = new Array<>();
            links.add(new SpatialBlockLink(1, 1, 99, 1));
            assertEquals(CommandOutcome.REJECTED, SpatialBlockCommandSupport.validateBlocks(
                    f.world, f.entity, f.blocks.blocks, links));
            assertEquals(0, f.blocks.links.size);
        }
    }

    @Test public void onWithoutLightsStillRejectsAndKeepsHistoryAndModel() {
        try (Fixture f = new Fixture()) {
            // Deliberately leave the renderer flag OFF: the model is authoritative.
            f.scene.lightingEnabled = true;
            SpatialBlockData edited = f.block().copy();
            edited.shadowCaster = true;
            f.history.execute(f.edit(edited));
            assertFalse(f.history.canUndo());
            assertFalse(f.block().shadowCaster);
            EditTileLightingDescriptionsCommand command = f.descriptions();
            f.history.execute(command);
            assertTrue(command.rejection(), command.rejection().contains("ISO"));
            assertEquals(0, f.tiled.lightingDescriptions.size);
            assertFalse(f.history.canUndo());
        }
    }

    @Test public void descriptionsOffRemainAuthoredDirtyAndUndoableWithoutNativeResources() {
        try (Fixture f = new Fixture()) {
            f.history.execute(f.descriptions());
            assertEquals(1, f.tiled.lightingDescriptions.size);
            assertTrue((f.world.getSystem(DirtyTrackerSystem.class).coarseBits(f.entity) & DirtyBits.GEOMETRY) != 0);
            f.history.undo();
            assertEquals(0, f.tiled.lightingDescriptions.size);
            f.history.redo();
            assertEquals(1.25f, f.tiled.lightingDescriptions.first().rounded.centerGX, 0);
            assertEquals(0, f.state.lightingMaps.size);
        }
    }

    @Test public void brushOffCommitsAndUndoesButAnchorErasureAndOnAdmissionStillReject() {
        try (Fixture f = new Fixture()) {
            f.block().shadowCaster = true;
            TiledBrushSession stroke = new TiledBrushSession(f.entity);
            stroke.apply(f.tiled, 4, 4, 7);
            TiledBrushCommand command = f.brush(stroke);
            f.history.execute(command);
            assertEquals(7, f.tiled.data.getTile(4, 4));
            f.history.undo();
            assertEquals(0, f.tiled.data.getTile(4, 4));
            f.history.redo();
            assertEquals(7, f.tiled.data.getTile(4, 4));
            TiledBrushSession erase = new TiledBrushSession(f.entity);
            erase.apply(f.tiled, 1, 1, 0);
            int revision = f.tiled.data.contentRevision();
            assertThrows(TiledMutationRejectedException.class, () -> f.history.execute(f.brush(erase)));
            assertEquals(7, f.tiled.data.getTile(1, 1));
            assertEquals(revision, f.tiled.data.contentRevision());
            f.scene.lightingEnabled = true;
            TiledMutationRejectedException refusal = assertThrows(TiledMutationRejectedException.class, f.history::undo);
            assertTrue(refusal.getMessage(), refusal.getMessage().contains("ISO"));
            assertEquals(1, f.history.getCursor());
            assertEquals(7, f.tiled.data.getTile(4, 4));
            assertEquals(revision, f.tiled.data.contentRevision());
            f.scene.lightingEnabled = false;
            f.history.undo();
            assertEquals(0, f.tiled.data.getTile(4, 4));
        }
    }

    @Test public void editedLightingPropertiesSurviveRealSceneSaveAndReload() throws Exception {
        try (Fixture f = new Fixture()) {
            SpatialBlockData edited = f.block().copy();
            edited.shadowCaster = true;
            edited.lightOccluder = true;
            edited.shadeUnlitFaces = true;
            f.history.execute(f.edit(edited));
            f.history.execute(f.descriptions());
            FileHandle file = new FileHandle(Files.createTempFile("unlit-spatial-", ".json").toFile());
            World loaded = new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
            try {
                SceneService.saveScene(f.world, file, false);
                SceneMetaRuntime meta = new Json().fromJson(SceneMetaRuntime.class, new Json().toJson(f.scene));
                assertFalse(meta.lightingEnabled);
                SceneLoader.loadScene(loaded, file, false, meta);
                loaded.process();
                int entity = loaded.getAspectSubscriptionManager().get(Aspect.all(SpatialBlocksComponent.class)).getEntities().get(0);
                SpatialBlockData block = loaded.getMapper(SpatialBlocksComponent.class).get(entity).blocks.first();
                assertTrue(block.shadowCaster);
                assertTrue(block.lightOccluder);
                assertTrue(block.shadeUnlitFaces);
                assertEquals(1.25f, loaded.getMapper(TiledLayerComponent.class).get(entity)
                        .lightingDescriptions.first().rounded.centerGX, 0);
            } finally { loaded.dispose(); file.delete(); }
        }
    }

    private static final class Fixture implements AutoCloseable {
        final SceneMetaRuntime scene = new SceneMetaRuntime();
        final TiledMapRenderState state = new TiledMapRenderState();
        final World world;
        final HistoryManager history = new HistoryManager(16);
        final int entity;
        final TiledLayerComponent tiled;
        final SpatialBlocksComponent blocks;

        Fixture() {
            GdxNativesLoader.load();
            scene.lightingEnabled = false;
            scene.nextEntityStableId = 2;
            state.lightingEnabled = false;
            world = new World(new WorldConfiguration().register(SceneMetaRuntime.class.getName(), scene)
                    .setSystem(new WorldSerializationManager()).setSystem(new DirtyTrackerSystem(16))
                    .setSystem(new RenderTiledSyncSystem(new OrthographicCamera(), state,
                            new AtlasRuntimeService(), 0, null, RuntimeTilesetProfiles.empty())));
            entity = world.create();
            world.getMapper(PixscapeIdentityComponent.class).create(entity).stableId = 1;
            tiled = world.getMapper(TiledLayerComponent.class).create(entity);
            tiled.projection = TiledProjection.ORTHO;
            tiled.tileWidth = tiled.tileHeight = 32;
            tiled.mapWidthCells = tiled.mapHeightCells = tiled.chunkSize = 8;
            tiled.data = tiled.createMapData();
            tiled.data.setTile(1, 1, 7);
            blocks = world.getMapper(SpatialBlocksComponent.class).create(entity);
            SpatialBlockData b = new SpatialBlockData();
            b.id = b.structureId = 1;
            b.x = b.y = b.width = b.depth = 1;
            b.height = 48;
            b.beginAuthoredLinkedTileRefs();
            b.addLinkedTileRef(1, 1, 7);
            blocks.blocks.add(b);
            blocks.nextSpatialBlockId = 2;
            world.process();
        }

        SpatialBlockData block() { return blocks.blocks.first(); }
        EditSpatialBlockCommand edit(SpatialBlockData value) {
            return new EditSpatialBlockCommand(world, history.historyIds(), null, entity, 1, block(), value);
        }
        EditTileLightingDescriptionsCommand descriptions() {
            TileSurfaceDescription value = new TileSurfaceDescription();
            value.assetId = 7;
            value.rounded = new RoundedWallDescription();
            value.rounded.centerGX = 1.25f;
            Array<TileSurfaceDescription> values = new Array<>();
            values.add(value);
            return new EditTileLightingDescriptionsCommand(world, history.historyIds(), entity, values);
        }
        TiledBrushCommand brush(TiledBrushSession stroke) {
            return new TiledBrushCommand(world, null, history.historyIds(), history.historyIds().ensureForEntity(entity),
                    stroke.toPlan(), new TiledSpatialMutationPlanner());
        }
        @Override public void close() { world.dispose(); }
    }
}
