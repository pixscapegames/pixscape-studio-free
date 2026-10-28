package games.pixscape.studio.ui.main;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.render.lighting.LightingAdmissionException;
import games.pixscape.runtime.render.lighting.LightingSurface;
import games.pixscape.runtime.render.lighting.RoundedWallDescription;
import games.pixscape.runtime.render.lighting.TileSurfaceDescription;
import games.pixscape.runtime.service.AtlasAssetBinding;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.LightingTestBindings;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.spatial.SpatialConstraintInvariantException;
import games.pixscape.runtime.spatial.SpatialTileOrderInvariantException;
import games.pixscape.runtime.spatial.SpatialTileSyncInvariantException;
import games.pixscape.runtime.system.RenderTiledSyncSystem;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.runtime.tiled.animation.TileAnimationDef;
import games.pixscape.runtime.tiled.animation.TileAnimationDefData;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetAnchor;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfile;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfiles;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.Command;
import games.pixscape.studio.history.commands.EditLightingCommand;
import games.pixscape.studio.service.LightValueGesture;
import org.junit.Assert;
import org.junit.Test;

public class SpatialPreviewInvariantBoundaryTest {
    @Test public void actualAnimationRefusalRecoversAfterExactCursorOneZeroOneAndUndoRedo(){
        com.badlogic.gdx.utils.GdxNativesLoader.load();
        RuntimeTilesetProfile profile=new RuntimeTilesetProfile();profile.tilesetId=1;
        profile.referenceCellWidth=256;profile.referenceCellHeight=128;profile.projection=TiledProjection.ISO;
        profile.anchor=RuntimeTilesetAnchor.BOTTOM_CENTER;profile.tileAssetIds=new int[]{1,2};
        RuntimeTilesetProfiles profiles=RuntimeTilesetProfiles.empty();profiles.add(profile);
        AtlasRuntimeService atlas=new AtlasRuntimeService(){
            @Override public AtlasAssetBinding resolveBinding(int id,String tag){return LightingTestBindings.tile(id,256,id==2?128:512);}
        };
        TiledMapRenderState state=new TiledMapRenderState();
        RenderTiledSyncSystem sync=new RenderTiledSyncSystem(new com.badlogic.gdx.graphics.OrthographicCamera(),state,atlas,0,null,profiles);
        World world=new World(new WorldConfiguration().setSystem(sync));
        try {
            int entity=world.create();world.getMapper(EntityIndexComponent.class).create(entity);
            TiledLayerComponent tiled=world.getMapper(TiledLayerComponent.class).create(entity);
            tiled.atlasTag="scene";tiled.data=new TiledMapLayerData(1,1,256,128,1,TiledProjection.ISO);tiled.data.setTile(0,0,1);
            SpatialBlocksComponent blocks=world.getMapper(SpatialBlocksComponent.class).create(entity);
            for(int i=0;i<2;i++){
                SpatialBlockData b=new SpatialBlockData();b.id=blocks.allocateNextSpatialBlockId();b.height=150;
                b.width=i==0?3:.25f;b.depth=i==0?.25f:3;b.shadowCaster=true;
                SpatialBlockData.LinkedTileRef r=new SpatialBlockData.LinkedTileRef();r.tileAssetId=1;b.linkedTileRefs.add(r);blocks.blocks.add(b);
            }
            TileSurfaceDescription d=new TileSurfaceDescription();d.assetId=1;d.rounded=new RoundedWallDescription();tiled.lightingDescriptions.add(d);
            TileAnimationDefData data=new TileAnimationDefData();data.id=1;data.frameAssetIds=new int[]{1,2};data.frameDurationsMs=new int[]{100,100};
            TileAnimationDef animation=new TileAnimationDef(data);
            HistoryManager history=new HistoryManager(16);
            SpatialPreviewInvariantBoundary boundary=new SpatialPreviewInvariantBoundary();RecordingListener listener=new RecordingListener();
            world.process();LightingSurface original=state.lightingSurfaces.get(tiled.data.getChunk(0,0).renderRefStartIndex);
            history.execute(new Command(){
                public String label(){return "Assign animation";}
                public void redo(){sync.setAnimatedTileLookup(id->id==1?animation:null);}
                public void undo(){sync.setAnimatedTileLookup(null);}
            });
            boundary.prepare("scene",history.getMutationRevision(),0);
            Assert.assertFalse(boundary.process("scene",world::process,listener));
            Assert.assertTrue(listener.failure instanceof LightingAdmissionException);
            Assert.assertTrue(listener.failure.getMessage().contains("frame 1"));
            Assert.assertSame(original,state.lightingSurfaces.get(tiled.data.getChunk(0,0).renderRefStartIndex));
            for(int i=0;i<4;i++){
                boundary.prepare("scene",history.getMutationRevision(),0);
                Assert.assertFalse(boundary.process("scene",()->Assert.fail("unchanged state retried"),listener));
            }
            Assert.assertEquals(1,listener.calls);Assert.assertEquals(1,history.getCursor());
            history.undo();Assert.assertEquals(0,history.getCursor());
            history.execute(new Command(){
                public String label(){return "Edit valid wall height";}
                private void height(float h){for(SpatialBlockData b:blocks.blocks)b.height=h;blocks.revision++;}
                public void redo(){height(160);}
                public void undo(){height(150);}
            });
            Assert.assertEquals(1,history.getCursor());Assert.assertEquals(160,blocks.blocks.first().height,0);
            boundary.prepare("scene",history.getMutationRevision(),0);
            Assert.assertTrue(boundary.process("scene",world::process,listener));
            Assert.assertFalse(boundary.isBlocked());
            history.undo();Assert.assertEquals(150,blocks.blocks.first().height,0);
            history.redo();Assert.assertEquals(160,blocks.blocks.first().height,0);
            // Reject again, then recover with a simple Undo as well.
            history.execute(new Command(){public String label(){return "Animation";}public void redo(){sync.setAnimatedTileLookup(id->animation);}public void undo(){sync.setAnimatedTileLookup(null);}});
            boundary.prepare("scene",history.getMutationRevision(),0);Assert.assertFalse(boundary.process("scene",world::process,listener));
            history.undo();boundary.prepare("scene",history.getMutationRevision(),0);Assert.assertTrue(boundary.process("scene",world::process,listener));
        } finally {world.dispose();}
    }

    @Test public void cancelledLightManipulationNotifiesRestorationWithoutAnUndoEntry(){
        World world=new World(new WorldConfiguration());HistoryManager history=new HistoryManager(8);
        SpatialPreviewInvariantBoundary boundary=new SpatialPreviewInvariantBoundary();RecordingListener listener=new RecordingListener();
        EventFlow.Listener<EventFlow.EntityChanged> changed=event->boundary.invalidate();
        EventFlow.i().flush();EventFlow.i().subscribe(EventFlow.EntityChanged.class,changed);
        try {
            int entity=world.create();games.pixscape.runtime.component.light.PointLightComponent light=world.getMapper(games.pixscape.runtime.component.light.PointLightComponent.class).create(entity);
            light.height=150;world.process();LightValueGesture gesture=new LightValueGesture(world,history);
            gesture.begin(entity,EditLightingCommand.Property.HEIGHT,0,0,false);gesture.preview(30);EventFlow.i().flush();
            Assert.assertEquals(180,light.height,0);
            boundary.process("scene",()->{throw new LightingAdmissionException("preview refused");},listener);
            gesture.cancel();EventFlow.i().flush();
            Assert.assertEquals(150,light.height,0);Assert.assertEquals(0,history.getCursor());Assert.assertEquals(0,history.getMutationRevision());
            Assert.assertTrue(boundary.process("scene",world::process,listener));
        } finally {EventFlow.i().unsubscribe(EventFlow.EntityChanged.class,changed);world.dispose();}
    }

    @Test public void groupedMutationsAdvanceGenerationButSavedStateDoesNot(){
        HistoryManager history=new HistoryManager(8);int[] state={0};
        history.beginGroup("group");
        for(int i=0;i<2;i++)history.execute(new Command(){public String label(){return "increment";}public void redo(){state[0]++;}public void undo(){state[0]--;}});
        Assert.assertEquals(2,history.getMutationRevision());Assert.assertEquals(0,history.getCursor());
        history.endGroup();Assert.assertEquals(1,history.getCursor());Assert.assertEquals(2,history.getMutationRevision());
        history.markSaved();Assert.assertEquals(2,history.getMutationRevision());Assert.assertFalse(history.isDirty());
        history.undo();Assert.assertEquals(0,state[0]);Assert.assertEquals(3,history.getMutationRevision());
        history.redo();Assert.assertEquals(2,state[0]);Assert.assertEquals(4,history.getMutationRevision());Assert.assertFalse(history.isDirty());
    }
    @Test public void lightingRefusalRetriesOnlyAfterEditOrResourcePublication(){
        SpatialPreviewInvariantBoundary boundary=new SpatialPreviewInvariantBoundary();RecordingListener listener=new RecordingListener();
        boundary.prepare("scene",4,2);
        Assert.assertFalse(boundary.process("scene",()->{throw new games.pixscape.runtime.render.lighting.LightingAdmissionException("second active light");},listener));
        boundary.prepare("scene",4,2);Assert.assertFalse(boundary.process("scene",()->Assert.fail("unchanged retry"),listener));
        boundary.prepare("scene",3,2);Assert.assertTrue(boundary.process("scene",()->{},listener));
        Assert.assertFalse(boundary.process("scene",()->{throw new games.pixscape.runtime.render.lighting.LightingAdmissionException("missing image");},listener));
        boundary.prepare("scene",3,3);Assert.assertTrue(boundary.process("scene",()->{},listener));
    }
    @Test(expected=AssertionError.class) public void fatalErrorsAreNotSwallowed(){
        new SpatialPreviewInvariantBoundary().process("scene",()->{throw new AssertionError("fatal");},new RecordingListener());
    }
    @Test
    public void invariantFailureBlocksSameSceneAndSurfacesExactDiagnosticOnce() {
        SpatialPreviewInvariantBoundary boundary = new SpatialPreviewInvariantBoundary();
        FailingFrame frame = new FailingFrame();
        RecordingListener listener = new RecordingListener();

        Assert.assertFalse(boundary.process("scene-a", frame, listener));
        Assert.assertFalse(boundary.process("scene-a", frame, listener));

        Assert.assertTrue(boundary.isBlocked());
        Assert.assertEquals(1, frame.calls);
        Assert.assertEquals(1, listener.calls);
        Assert.assertEquals(2, ((SpatialConstraintInvariantException) listener.failure).unresolvedConstraintCount());
        Assert.assertEquals("exact conflict", listener.failure.getMessage());
    }

    @Test
    public void activatingAnotherSceneClearsThePreviewBlock() {
        SpatialPreviewInvariantBoundary boundary = new SpatialPreviewInvariantBoundary();
        FailingFrame failing = new FailingFrame();
        RecordingListener listener = new RecordingListener();
        boundary.process("scene-a", failing, listener);

        CountingFrame valid = new CountingFrame();
        Assert.assertTrue(boundary.process("scene-b", valid, listener));

        Assert.assertFalse(boundary.isBlocked());
        Assert.assertEquals(1, valid.calls);
        Assert.assertEquals(1, listener.calls);
    }

    @Test
    public void staticTileOrderFailureBlocksPreviewWithExactDiagnostic() {
        SpatialPreviewInvariantBoundary boundary = new SpatialPreviewInvariantBoundary();
        RecordingListener listener = new RecordingListener();

        Assert.assertFalse(boundary.process("cycle", () -> {
            throw new SpatialTileOrderInvariantException("exact static cycle");
        }, listener));

        Assert.assertTrue(boundary.isBlocked());
        Assert.assertEquals("exact static cycle", listener.failure.getMessage());
    }

    @Test
    public void tiledSyncFailureIsRecoverableAtPreviewBoundary() {
        SpatialPreviewInvariantBoundary boundary = new SpatialPreviewInvariantBoundary();
        RecordingListener listener = new RecordingListener();

        Assert.assertFalse(boundary.process("edit", () -> {
            throw new SpatialTileSyncInvariantException("cell=(4,19), canonicalRankState=missing");
        }, listener));

        Assert.assertTrue(boundary.isBlocked());
        Assert.assertEquals(1, listener.calls);
        Assert.assertTrue(listener.failure.getMessage().contains("cell=(4,19)"));
    }

    private static final class FailingFrame implements SpatialPreviewInvariantBoundary.FrameProcessor {
        int calls;
        @Override public void processFrame() {
            calls++;
            throw new SpatialConstraintInvariantException(2, "exact conflict");
        }
    }

    private static final class CountingFrame implements SpatialPreviewInvariantBoundary.FrameProcessor {
        int calls;
        @Override public void processFrame() { calls++; }
    }

    private static final class RecordingListener implements SpatialPreviewInvariantBoundary.FailureListener {
        int calls;
        RuntimeException failure;
        @Override public void onSpatialInvariantFailure(RuntimeException failure) {
            calls++;
            this.failure = failure;
        }
    }
}
