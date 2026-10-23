package games.pixscape.studio.service.spatial;

import com.artemis.*;
import com.badlogic.gdx.graphics.OrthographicCamera;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.render.lighting.*;
import games.pixscape.runtime.service.*;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.system.RenderTiledSyncSystem;
import games.pixscape.runtime.tiled.*;
import games.pixscape.runtime.tiled.profile.*;
import games.pixscape.studio.history.HistoryManager;
import org.junit.Test;
import static org.junit.Assert.*;

public class CornerProfileEditSessionTest {
    @Test public void removeProfileRestoresClassicWallsAndCanUndoRedo(){try(Fixture f=new Fixture(true)) {
        f.open();int cursor=f.history.getCursor();
        assertTrue(f.edit.removeProfile());assertFalse(f.edit.active());
        assertEquals(0,f.tiled.lightingDescriptions.size);assertEquals(2,f.blocks.blocks.size);
        assertEquals(cursor+1,f.history.getCursor());
        f.prepare();
        SpatialStructureGeometryCache structures=new SpatialStructureGeometryCache();structures.synchronize(f.entity,f.blocks,f.tiled.data);
        CornerProfileOverlay overview=new CornerProfileOverlay();
        overview.synchronize(f.entity,f.tiled,f.blocks,f.state.lightingMaps.get(f.entity),structures);
        assertEquals(0,overview.size());assertNull(overview.wire(1));assertFalse(overview.replacesWall(1));
        f.history.undo();f.prepare();assertNotNull(f.tiled.lightingDescriptions.first().rounded);
        f.history.redo();f.prepare();assertEquals(0,f.tiled.lightingDescriptions.size);
        assertNull(f.edit.inspect(f.world,f.state,f.history,new CornerProfileEditSession.Target(f.entity,1,1,7,1)));
        assertTrue(f.edit.open());assertEquals(1,f.tiled.lightingDescriptions.size);
    }}
    @Test public void cornerBodySelectionExcludesStraightWallContinuation(){try(Fixture f=new Fixture(true)) {
        SpatialStructureGeometryCache structures=new SpatialStructureGeometryCache();structures.synchronize(f.entity,f.blocks,f.tiled.data);
        CornerProfileOverlay overview=new CornerProfileOverlay();overview.synchronize(f.entity,f.tiled,f.blocks,f.state.lightingMaps.get(f.entity),structures);
        var c=overview.corner(0);float x=(c.x(2)+c.x(3))*.5f,y=(c.y(2)+c.y(3))*.5f+c.height()*.5f;
        assertTrue(c.contains(x,y));assertNotNull(overview.pickBody(f.entity,x,y,f.state));
        assertFalse(c.contains(x+2000,y));assertNull(overview.pickBody(f.entity,x+2000,y,f.state));
        assertNull(overview.pickBody(f.entity+1,x,y,f.state));
    }}
    @Test public void spatialOverviewShowsPublishedVerticesWithoutOpeningEditorOrChangingHistory(){try(Fixture f=new Fixture(true)) {
        f.open();assertTrue(f.edit.add(2));f.edit.close();f.prepare();
        SpatialStructureGeometryCache structures=new SpatialStructureGeometryCache();
        assertTrue(structures.synchronize(f.entity,f.blocks,f.tiled.data).success());
        CornerProfileOverlay overview=new CornerProfileOverlay();
        MapLightingGeometry geometry=f.state.lightingMaps.get(f.entity);
        int cursor=f.history.getCursor(),profiles=geometry.roundedProfileCompilationCount(),compiled=geometry.roundedGeometryCompilationCount();
        Object authored=f.tiled.lightingDescriptions;
        overview.synchronize(f.entity,f.tiled,f.blocks,geometry,structures);
        assertEquals(1,overview.size());assertEquals(7,overview.corner(0).support().segments);
        assertSame(geometry.roundedSupport(1,1),overview.corner(0).support());
        assertTrue(overview.replacesWall(1));assertTrue(overview.replacesWall(2));
        assertNotNull(overview.wire(1));assertFalse(f.edit.active());
        assertEquals(f.entity,overview.pickHandle(f.entity,overview.corner(0).x(2),overview.corner(0).y(2),3,f.state).mapId());
        assertNull(overview.pickHandle(f.entity+1,overview.corner(0).x(2),overview.corner(0).y(2),3,f.state));
        for(int i=0;i<30;i++)overview.synchronize(f.entity,f.tiled,f.blocks,geometry,structures);
        assertEquals(1,overview.rebuildCount());assertSame(authored,f.tiled.lightingDescriptions);
        assertEquals(cursor,f.history.getCursor());assertEquals(profiles,geometry.roundedProfileCompilationCount());
        assertEquals(compiled,geometry.roundedGeometryCompilationCount());
    }}
    @Test public void overviewRemovesSquareCornerButKeepsStraightContinuation(){try(Fixture f=new Fixture(true)) {
        f.open();f.edit.add(2);f.prepare();
        SpatialStructureGeometryCache structures=new SpatialStructureGeometryCache();structures.synchronize(f.entity,f.blocks,f.tiled.data);
        CornerProfileOverlay overview=new CornerProfileOverlay();overview.synchronize(f.entity,f.tiled,f.blocks,f.state.lightingMaps.get(f.entity),structures);
        com.badlogic.gdx.utils.FloatArray line=new com.badlogic.gdx.utils.FloatArray();
        CornerProfileOverlay.subtract(new float[]{1,1,4,1},0,overview.corner(0),line);
        assertArrayEquals(new float[]{2,1,4,1},line.toArray(),0);
        line.clear();CornerProfileOverlay.subtract(new float[]{1,1,1,4},0,overview.corner(0),line);
        assertArrayEquals(new float[]{1,2,1,4},line.toArray(),0);
        line.clear();CornerProfileOverlay.subtract(new float[]{1,1,1.25f,1},0,overview.corner(0),line);
        assertEquals(0,line.size);
    }}
    @Test public void overviewFollowsUndoRedoBetweenExplicitAndLegacyVertices(){try(Fixture f=new Fixture(true)) {
        f.open();f.edit.add(2);f.edit.close();f.prepare();
        SpatialStructureGeometryCache structures=new SpatialStructureGeometryCache();structures.synchronize(f.entity,f.blocks,f.tiled.data);
        CornerProfileOverlay overview=new CornerProfileOverlay();overview.synchronize(f.entity,f.tiled,f.blocks,f.state.lightingMaps.get(f.entity),structures);
        assertEquals(1,overview.size());
        f.history.undo();f.prepare();overview.synchronize(f.entity,f.tiled,f.blocks,f.state.lightingMaps.get(f.entity),structures);
        assertEquals(1,overview.size());assertEquals(6,overview.corner(0).support().segments);
        assertNotNull(overview.wire(1));assertTrue(overview.replacesWall(1));
        f.history.redo();f.prepare();overview.synchronize(f.entity,f.tiled,f.blocks,f.state.lightingMaps.get(f.entity),structures);
        assertEquals(1,overview.size());assertEquals(7,overview.corner(0).support().segments);
    }}
    @Test public void legacyRoundedOverviewAppearsImmediatelyWithoutConvertingAuthoredProfile(){try(Fixture f=new Fixture(true)) {
        SpatialStructureGeometryCache structures=new SpatialStructureGeometryCache();
        assertTrue(structures.synchronize(f.entity,f.blocks,f.tiled.data).success());
        MapLightingGeometry geometry=f.state.lightingMaps.get(f.entity);
        Object authored=f.tiled.lightingDescriptions;
        int compiled=geometry.roundedGeometryCompilationCount();
        CornerProfileOverlay overview=new CornerProfileOverlay();
        for(int i=0;i<30;i++)overview.synchronize(f.entity,f.tiled,f.blocks,geometry,structures);
        assertEquals(1,overview.size());assertEquals(6,overview.corner(0).support().segments);
        assertSame(geometry.roundedSupport(1,1),overview.corner(0).support());
        assertNotNull(overview.wire(1));assertEquals(1,overview.rebuildCount());
        assertSame(authored,f.tiled.lightingDescriptions);assertNull(f.tiled.lightingDescriptions.first().rounded.profileXY);
        assertEquals(compiled,geometry.roundedGeometryCompilationCount());assertFalse(f.history.canUndo());assertFalse(f.edit.active());
    }}
    @Test public void overviewHandleCannotCreateAnUnconfiguredProfile(){try(Fixture f=new Fixture(false)) {
        assertNull(f.edit.inspect(f.world,f.state,f.history,new CornerProfileEditSession.Target(f.entity,1,1,7,1)));
        assertFalse(f.edit.openExisting());assertFalse(f.edit.active());assertFalse(f.history.canUndo());
        assertEquals(0,f.tiled.lightingDescriptions.size);
        SpatialStructureGeometryCache structures=new SpatialStructureGeometryCache();structures.synchronize(f.entity,f.blocks,f.tiled.data);
        CornerProfileOverlay overview=new CornerProfileOverlay();
        overview.synchronize(f.entity,f.tiled,f.blocks,f.state.lightingMaps.get(f.entity),structures);
        assertEquals(0,overview.size());assertNull(overview.wire(1));
    }}
    @Test public void overviewDisplaysEveryOccurrenceOnceDespiteTwoLinkedWalls(){try(Fixture f=new Fixture(true)) {
        f.open();f.edit.add(2);f.edit.close();
        f.tiled.data.setTile(5,5,7);
        for(int i=0;i<2;i++) {
            SpatialBlockData wall=f.blocks.blocks.get(i).copy();wall.id=f.blocks.allocateNextSpatialBlockId();
            wall.structureId=2;wall.x+=4;wall.y+=4;
            wall.linkedTileRefs.first().gx=5;wall.linkedTileRefs.first().gy=5;f.blocks.blocks.add(wall);
        }
        f.blocks.revision++;f.prepare();
        MapLightingGeometry geometry=f.state.lightingMaps.get(f.entity);
        float[] secondQuad=f.quad.clone();for(int i=1;i<8;i+=2)secondQuad[i]+=512;
        geometry.receiver(f.tiled.data,5,5,7,(byte)0,secondQuad);
        SpatialStructureGeometryCache structures=new SpatialStructureGeometryCache();
        assertTrue(structures.synchronize(f.entity,f.blocks,f.tiled.data).success());
        CornerProfileOverlay overview=new CornerProfileOverlay();overview.synchronize(f.entity,f.tiled,f.blocks,geometry,structures);
        assertEquals(2,overview.size());assertNotNull(overview.wire(1));assertNotNull(overview.wire(2));
        assertEquals(1,overview.corner(0).target().gx());assertEquals(5,overview.corner(1).target().gx());
        assertSame(geometry.roundedSupport(5,5),overview.corner(1).support());
        float[] firstWire=overview.wire(1),secondWire=overview.wire(2);
        f.open();assertTrue(f.edit.begin(2));
        f.edit.move(f.edit.screenX(2)+1,f.edit.screenY(2)+.5f);
        overview.synchronize(f.entity,f.tiled,f.blocks,geometry,structures);
        assertEquals(2,overview.size());assertSame(firstWire,overview.wire(1));assertSame(secondWire,overview.wire(2));
        assertTrue(f.edit.isEditingOccurrence(overview.corner(0).target()));
        assertFalse(f.edit.isEditingOccurrence(overview.corner(1).target()));
        assertEquals(1,overview.rebuildCount());
        f.edit.cancelGesture();f.edit.close();
        assertFalse(f.edit.isEditingOccurrence(overview.corner(0).target()));
    }}
    private static final class Fixture implements AutoCloseable {
        final TiledMapRenderState state=new TiledMapRenderState(256);
        final World world;final HistoryManager history=new HistoryManager(16);
        final TiledLayerComponent tiled;final SpatialBlocksComponent blocks;final int entity;
        final CornerProfileEditSession edit=new CornerProfileEditSession();
        final float[] quad={0,128,0,640,256,640,256,128};
        Fixture(boolean enabled) {
            RuntimeTilesetProfile p=new RuntimeTilesetProfile();p.tilesetId=1;p.referenceCellWidth=256;p.referenceCellHeight=128;
            p.projection=TiledProjection.ISO;p.anchor=RuntimeTilesetAnchor.BOTTOM_CENTER;p.tileAssetIds=new int[]{7};
            RuntimeTilesetProfiles profiles=RuntimeTilesetProfiles.empty();profiles.add(p);
            AtlasRuntimeService atlas=new AtlasRuntimeService(){@Override public AtlasAssetBinding resolveBinding(int id,String tag){return LightingTestBindings.tile(id,256,512);}};
            world=new World(new WorldConfiguration().setSystem(new RenderTiledSyncSystem(new OrthographicCamera(),state,atlas,0,null,profiles)));
            entity=world.create();tiled=world.getMapper(TiledLayerComponent.class).create(entity);
            tiled.projection=TiledProjection.ISO;tiled.tileWidth=256;tiled.tileHeight=128;tiled.mapWidthCells=tiled.mapHeightCells=8;tiled.chunkSize=8;tiled.data=tiled.createMapData();
            tiled.data.setTile(1,1,7);blocks=world.getMapper(SpatialBlocksComponent.class).create(entity);
            for(int i=0;i<2;i++) {
                SpatialBlockData b=new SpatialBlockData();b.id=blocks.allocateNextSpatialBlockId();b.structureId=1;b.x=b.y=1;
                b.width=i==0?3:.25f;b.depth=i==0?.25f:3;b.height=150;b.shadowCaster=true;b.linkedTileRefsAuthored=true;
                SpatialBlockData.LinkedTileRef r=new SpatialBlockData.LinkedTileRef();r.gx=r.gy=1;r.tileAssetId=7;b.linkedTileRefs.add(r);blocks.blocks.add(b);
            }
            if(enabled){TileSurfaceDescription d=new TileSurfaceDescription();d.assetId=7;d.rounded=new RoundedWallDescription();tiled.lightingDescriptions.add(d);}
            world.process();
            TileChunk chunk=tiled.data.getChunk(0,0);chunk.renderRefStartIndex=state.registerRefs(chunk.cellCount());chunk.renderRefCount=chunk.cellCount();
            int ref=tiled.data.tiledRenderRefForTile(1,1);state.enabled[ref]=true;state.visible[ref]=true;state.textureHandle[ref]=1;
            state.x1[ref]=quad[0];state.y1[ref]=quad[1];state.x2[ref]=quad[2];state.y2[ref]=quad[3];
            state.x3[ref]=quad[4];state.y3[ref]=quad[5];state.x4[ref]=quad[6];state.y4[ref]=quad[7];
            prepare();
        }
        void prepare(){MapLightingGeometry g=new MapLightingGeometry();g.descriptions(tiled.lightingDescriptions);g.ensure(tiled.data,blocks);
            LightingSurface s=g.receiver(tiled.data,1,1,7,(byte)0,quad);state.lightingMaps.put(entity,g);state.lightingSurfaces.put(tiled.data.tiledRenderRefForTile(1,1),s);}
        void open(){assertNull(edit.inspect(world,state,history,new CornerProfileEditSession.Target(entity,1,1,7,1)));assertTrue(edit.reason(),edit.open());}
        public void close(){world.dispose();}
    }
    @Test public void openingLegacyDoesNotRewriteAndEscapeRestoresExactReceiver(){try(Fixture f=new Fixture(true)) {
        Object descriptions=f.tiled.lightingDescriptions;f.open();assertFalse(f.history.canUndo());
        LightingSurface initial=f.state.lightingSurfaces.get(f.tiled.data.tiledRenderRefForTile(1,1));
        assertTrue(f.edit.begin(2));float x=f.edit.screenX(2),y=f.edit.screenY(2);
        f.edit.move(x+1,y+.5f);assertTrue(f.edit.reason(),f.edit.valid());
        f.edit.cancelGesture();assertSame(initial,f.state.lightingSurfaces.get(f.tiled.data.tiledRenderRefForTile(1,1)));
        assertSame(descriptions,f.tiled.lightingDescriptions);assertNull(f.tiled.lightingDescriptions.first().rounded.profileXY);assertFalse(f.history.canUndo());
    }}
    @Test public void changedGestureIsOneCommandAndInvalidThenValidRemainsEditable(){try(Fixture f=new Fixture(true)) {
        f.open();assertTrue(f.edit.begin(2));float x=f.edit.screenX(2),y=f.edit.screenY(2);
        f.edit.move(x+500,y);assertFalse(f.edit.valid());f.edit.move(x+1,y+.5f);assertTrue(f.edit.reason(),f.edit.valid());
        assertFalse(f.history.canUndo());f.edit.release();assertEquals(1,f.history.getCursor());
        float[] saved=f.tiled.lightingDescriptions.first().rounded.profileXY.clone();
        f.history.undo();assertNull(f.tiled.lightingDescriptions.first().rounded.profileXY);f.history.redo();assertArrayEquals(saved,f.tiled.lightingDescriptions.first().rounded.profileXY,0);
    }}
    @Test public void activationAddRemoveResetAndUnlinkedTarget(){try(Fixture f=new Fixture(false)) {
        assertNotNull(f.edit.inspect(f.world,f.state,f.history,new CornerProfileEditSession.Target(f.entity,1,1,7,999)));
        f.open();assertEquals(1,f.history.getCursor());f.prepare();f.edit.synchronize();
        assertTrue(f.edit.reason(),f.edit.add(2));assertEquals(8,f.edit.vertexCount());
        assertTrue(f.edit.reason(),f.edit.remove(3));assertEquals(7,f.edit.vertexCount());
        assertTrue(f.edit.reset());assertNull(f.tiled.lightingDescriptions.first().rounded.profileXY);
        assertFalse(f.edit.reset());assertEquals(4,f.history.getCursor());
    }}
    @Test public void staleGestureCannotOverwriteAChangedMap(){try(Fixture f=new Fixture(true)) {
        f.open();f.edit.begin(2);f.edit.move(f.edit.screenX(2)+1,f.edit.screenY(2)+.5f);
        f.blocks.revision++;f.edit.release();assertFalse(f.history.canUndo());assertNull(f.tiled.lightingDescriptions.first().rounded.profileXY);
    }}
    @Test public void unchangedUpdatesAndCameraIndependentSynchronizationDoNotCompile(){try(Fixture f=new Fixture(true)) {
        f.open();float x=f.edit.screenX(2),y=f.edit.screenY(2);f.edit.press(2,x,y);
        MapLightingGeometry geometry=f.state.lightingMaps.get(f.entity);
        int globalProfiles=geometry.roundedProfileCompilationCount(),globalGeometry=geometry.roundedGeometryCompilationCount();
        for(int i=0;i<30;i++){f.edit.drag(x,y);f.edit.synchronize();}
        assertEquals(0,f.edit.profileCompilations);assertEquals(0,f.edit.geometryCompilations);
        f.edit.drag(x+1,y+.5f);int count=f.edit.geometryCompilations;
        for(int i=0;i<30;i++){f.edit.drag(x+1,y+.5f);f.edit.synchronize();}
        assertEquals(count,f.edit.geometryCompilations);
        assertEquals(globalProfiles,geometry.roundedProfileCompilationCount());
        assertEquals(globalGeometry,geometry.roundedGeometryCompilationCount());
        f.edit.drag(x,y);f.edit.release();assertFalse(f.history.canUndo());assertNull(f.tiled.lightingDescriptions.first().rounded.profileXY);
    }}
    @Test public void publicationValidatesOtherOccurrencesBeforeChangingHistory(){try(Fixture f=new Fixture(true)) {
        // An unrelated occurrence has no wall associations: local preview must not publish over it.
        f.tiled.data.setTile(5,5,7);f.open();f.edit.begin(2);f.edit.move(f.edit.screenX(2)+1,f.edit.screenY(2)+.5f);
        assertTrue(f.edit.valid());f.edit.release();assertFalse(f.history.canUndo());
        assertNotNull(f.edit.reason());assertNull(f.tiled.lightingDescriptions.first().rounded.profileXY);
    }}
    @Test public void animatedQuadChangeDiscardsPreviewWithoutDuplicatingItsBottom(){try(Fixture f=new Fixture(true)) {
        f.open();MapLightingGeometry geometry=f.state.lightingMaps.get(f.entity);
        int casterCount=geometry.casters.size;
        assertTrue(f.edit.begin(2));f.edit.move(f.edit.screenX(2)+1,f.edit.screenY(2)+.5f);
        float[] nextQuad=f.quad.clone();nextQuad[3]+=10;nextQuad[5]+=10;
        LightingSurface next=geometry.receiver(f.tiled.data,1,1,7,(byte)0,nextQuad);
        int ref=f.tiled.data.tiledRenderRefForTile(1,1);
        f.state.y2[ref]=nextQuad[3];f.state.y3[ref]=nextQuad[5];f.state.lightingSurfaces.put(ref,next);
        f.edit.synchronize();
        assertEquals(casterCount,geometry.casters.size);assertFalse(f.edit.dragging());
        assertSame(next,f.state.lightingSurfaces.get(ref));assertFalse(f.history.canUndo());
        assertTrue(f.edit.begin(2));f.edit.cancelGesture();
        assertSame(next,f.state.lightingSurfaces.get(ref));
    }}
    @Test public void geometryPublicationCancelsDetachedCandidate(){try(Fixture f=new Fixture(true)) {
        f.open();assertTrue(f.edit.begin(2));float x=f.edit.screenX(2),y=f.edit.screenY(2);
        f.edit.move(x+1,y+.5f);f.prepare();f.edit.synchronize();
        assertFalse(f.edit.dragging());assertEquals(x,f.edit.screenX(2),0);assertEquals(y,f.edit.screenY(2),0);
        assertFalse(f.history.canUndo());
    }}
    @Test public void nonzeroAltitudeAndMapOriginKeepGrabOffsetAndPublishedLocalCoordinates(){try(Fixture f=new Fixture(true)) {
        for(SpatialBlockData wall:f.blocks.blocks)wall.altitude=40;
        f.blocks.revision++;f.tiled.data.originX=25;f.tiled.data.originY=75;
        for(int i=0;i<8;i+=2){f.quad[i]+=25;f.quad[i+1]+=115;}
        int ref=f.tiled.data.tiledRenderRefForTile(1,1);
        f.state.x1[ref]=f.quad[0];f.state.y1[ref]=f.quad[1];f.state.x2[ref]=f.quad[2];f.state.y2[ref]=f.quad[3];
        f.state.x3[ref]=f.quad[4];f.state.y3[ref]=f.quad[5];f.state.x4[ref]=f.quad[6];f.state.y4[ref]=f.quad[7];
        f.prepare();f.open();assertEquals(40,f.edit.base(),0);
        float x=f.edit.screenX(2),y=f.edit.screenY(2);
        assertTrue(f.edit.press(2,x+2,y-3));f.edit.drag(x+3,y-2.5f);
        assertTrue(f.edit.reason(),f.edit.valid());assertEquals(x+1,f.edit.screenX(2),.001f);assertEquals(y+.5f,f.edit.screenY(2),.001f);
        f.edit.release();assertEquals(1,f.history.getCursor());assertNotNull(f.tiled.lightingDescriptions.first().rounded.profileXY);
    }}
}
