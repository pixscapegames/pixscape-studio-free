package games.pixscape.studio.service.spatial;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.utils.Json;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.spatial.SpatialBlockLink;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.EditSpatialBlockCommand;
import games.pixscape.studio.history.commands.SpatialVerticalJoinCommand;
import org.junit.Test;

import static org.junit.Assert.*;

public class SpatialVerticalJoinTest {
    @Test public void belowAdaptsMixedEnsembleAndOneUndoRestoresDimensionsAndDefault() {
        try(Fixture f=new Fixture()) {
            f.source.blocks.add(wall(2,1,1,2,.2f,2,0,60));f.source.nextSpatialBlockId=4;
            f.source.blocks.add(wall(3,3,9,5,2,.2f,0,35));
            String fixed=new Json().toJson(f.reference),disjoint=new Json().toJson(f.source.blocks.peek());
            SpatialVerticalJoinProposal p=f.proposal(f.lower,1,f.upper,1);assertTrue(p.diagnostic,p.valid);
            assertEquals(2,p.affected.size());assertEquals(80,p.defaultHeight,0);
            SpatialBlockData a=SpatialVerticalJoinProposal.find(p.after,1),corner=SpatialVerticalJoinProposal.find(p.after,2);
            assertEquals(2.05f,a.y,.0001f);assertEquals(.3f,a.depth,.0001f);
            assertEquals(.2f,corner.width,0);assertEquals(1,corner.x,0);
            assertEquals(a.y,corner.y,0);assertEquals(4,corner.y+corner.depth,.0001f);
            SpatialVerticalJoinCommand command=f.command(p);f.history.execute(command);assertTrue(command.rejectionReason(),command.wasApplied());
            assertEquals(1,f.history.getCursor());assertEquals(80,f.map(f.lower).defaultTileHeight,0);
            assertEquals(fixed,new Json().toJson(f.reference));assertEquals(disjoint,new Json().toJson(f.source.blocks.peek()));
            for(int loop=0;loop<3;loop++) {
                f.history.undo();assertEquals(60,f.source.blocks.first().height,0);assertEquals(128,f.map(f.lower).defaultTileHeight,0);
                assertEquals(2,SpatialVerticalJoinProposal.find(f.source,2).y,0);
                f.history.redo();assertEquals(80,f.source.blocks.first().height,0);assertEquals(80,f.map(f.lower).defaultTileHeight,0);
            }
            assertEquals(0,f.source.links.size);assertEquals(fixed,new Json().toJson(f.reference));
        }
    }
    @Test public void abovePreservesTopAndReferenceAndAtomicallyChangesBaseAndHeight() {
        try(Fixture f=new Fixture()) {
            String fixed=new Json().toJson(f.source);SpatialVerticalJoinProposal p=f.proposal(f.upper,1,f.lower,1);
            assertTrue(p.diagnostic,p.valid);SpatialBlockData a=p.after.blocks.first();
            assertEquals(60,a.altitude,0);assertEquals(110,a.height,0);assertEquals(170,a.altitude+a.height,0);
            assertEquals(2,a.y,.0001f);assertEquals(.2f,a.depth,.0001f);
            SpatialVerticalJoinCommand c=f.command(p);f.history.execute(c);assertTrue(c.rejectionReason(),c.wasApplied());
            assertEquals(110,f.map(f.upper).defaultTileHeight,0);assertEquals(fixed,new Json().toJson(f.source));
            f.history.undo();assertEquals(80,f.reference.blocks.first().altitude,0);assertEquals(90,f.reference.blocks.first().height,0);
            f.history.redo();assertEquals(60,f.reference.blocks.first().altitude,0);assertEquals(110,f.reference.blocks.first().height,0);
        }
    }
    @Test public void searchIgnoresLayerAndRenderIndicesAndDoesNotRequireSharedCells() {
        try(Fixture f=new Fixture()) {
            f.world.getMapper(EntityIndexComponent.class).get(f.lower).layerIndex=9;
            f.world.getMapper(EntityIndexComponent.class).get(f.lower).zIndex=100;
            f.world.getMapper(EntityIndexComponent.class).get(f.upper).layerIndex=2;
            f.world.getMapper(EntityIndexComponent.class).get(f.upper).zIndex=-10;
            SpatialVerticalJoinProposal.Search search=SpatialVerticalJoinProposal.search(f.world,f.lower,1);
            assertEquals(search.diagnostic,1,search.candidates.size);assertTrue(search.candidates.first().referenceAbove);
            assertTrue(f.proposal(f.lower,1,f.upper,1).valid);
        }
    }
    @Test public void multipleReferencesAreRetainedForChoiceAndAbsentOrUnsupportedAreExplained() {
        try(Fixture f=new Fixture()) {
            f.reference.blocks.add(wall(2,2,1,2.5f,3,.3f,80,90));f.reference.nextSpatialBlockId=3;
            assertEquals(2,SpatialVerticalJoinProposal.search(f.world,f.lower,1).candidates.size);
            f.reference.blocks.first().x=10;f.reference.blocks.get(1).x=10;
            SpatialVerticalJoinProposal.Search none=SpatialVerticalJoinProposal.search(f.world,f.lower,1);
            assertEquals(0,none.candidates.size);assertNotNull(none.diagnostic);
            f.map(f.upper).data=new TiledMapLayerData(16,8,32,16,4,TiledProjection.ORTHO);
            none=SpatialVerticalJoinProposal.search(f.world,f.lower,1);assertTrue(none.diagnostic.contains("unsupported"));
        }
    }
    @Test public void invalidHeightIsRefusedWithoutMutation() {
        try(Fixture f=new Fixture()) {
            f.reference.blocks.first().height=200;String before=new Json().toJson(f.source);
            SpatialVerticalJoinProposal p=f.proposal(f.lower,1,f.upper,1);assertTrue(p.valid);
            f.source.blocks.first().height=300;
            p=f.proposal(f.upper,1,f.lower,1);assertFalse(p.valid);assertTrue(p.diagnostic,p.diagnostic.contains("strictly positive"));
            f.source.blocks.first().height=60;assertEquals(before,new Json().toJson(f.source));
        }
        try(Fixture f=new Fixture()) {
            f.reference.blocks.first().y=2.8f;f.reference.blocks.first().depth=.3f;
            // Matching both faces is valid even though the selected image row ends at 3.
            f.reference.blocks.first().depth=.15f;
            f.reference.blocks.first().y=2.95f;
            f.reference.blocks.first().beginAuthoredLinkedTileRefs();
            for(int y=2;y<4;y++)for(int x=1;x<4;x++)f.reference.blocks.first().addLinkedTileRef(x,y,1);
            SpatialVerticalJoinProposal p=f.proposal(f.lower,1,f.upper,1);assertTrue(p.diagnostic,p.valid);
            assertEquals(2.95f,p.after.blocks.first().y,.0001f);
            assertEquals(.15f,p.after.blocks.first().depth,.0001f);
            assertEquals(.2f,f.source.blocks.first().depth,0);assertFalse(f.history.canUndo());
        }
    }
    @Test public void staleReferenceOrProjectionCannotPublishAndCancelIsDetached() {
        try(Fixture f=new Fixture()) {
            SpatialVerticalJoinProposal p=f.proposal(f.lower,1,f.upper,1);assertTrue(p.valid);
            assertEquals(60,f.source.blocks.first().height,0);assertFalse(f.history.canUndo());
            SpatialVerticalJoinCommand c=f.command(p);f.reference.blocks.first().height=95;f.history.execute(c);
            assertFalse(c.wasApplied());assertFalse(f.history.canUndo());assertEquals(60,f.source.blocks.first().height,0);
        }
        try(Fixture f=new Fixture()) {
            SpatialVerticalJoinProposal p=f.proposal(f.lower,1,f.upper,1);SpatialVerticalJoinCommand c=f.command(p);
            f.map(f.upper).data.originX=7;f.history.execute(c);assertFalse(c.wasApplied());assertFalse(f.history.canUndo());
        }
    }
    @Test public void explicitEdgeConnectedMembersAllFollowAndNewDisjointIntersectionIsRefused() {
        try(Fixture f=new Fixture()) {
            f.source.blocks.add(wall(2,1,4,2,2,.2f,0,60));f.source.nextSpatialBlockId=3;
            SpatialBlockLink edge=new SpatialBlockLink();edge.firstBlockId=1;edge.secondBlockId=2;edge.firstFace=2;edge.secondFace=1;f.source.links.add(edge);
            SpatialVerticalJoinProposal p=f.proposal(f.lower,1,f.upper,1);assertTrue(p.diagnostic,p.valid);
            assertEquals(80,SpatialVerticalJoinProposal.find(p.after,2).height,0);assertEquals(.3f,SpatialVerticalJoinProposal.find(p.after,2).depth,.0001f);
            assertEquals(1,p.after.links.size);assertEquals(4,SpatialVerticalJoinProposal.find(p.after,2).x,0);
        }
        try(Fixture f=new Fixture()) {
            f.source.blocks.add(wall(2,2,2,2.25f,1,.15f,0,60));f.source.nextSpatialBlockId=3;
            SpatialVerticalJoinProposal p=f.proposal(f.lower,1,f.upper,1);assertFalse(p.valid);
            assertEquals(.2f,f.source.blocks.first().depth,0);
        }
    }
    @Test public void ordinaryStructureHeightUpdatesDefaultWithoutChangingDisjoints() {
        try(Fixture f=new Fixture()) {
            SpatialBlockData before=f.source.blocks.first().copy(),edited=before.copy();edited.height=75;
            EditSpatialBlockCommand c=new EditSpatialBlockCommand(f.world,f.history.historyIds(),null,f.lower,1,before,edited);
            f.history.execute(c);assertEquals(75,f.map(f.lower).defaultTileHeight,0);
            f.history.undo();assertEquals(128,f.map(f.lower).defaultTileHeight,0);f.history.redo();assertEquals(75,f.map(f.lower).defaultTileHeight,0);
        }
    }
    @Test public void mapsInSameLayerAndDifferentCellSizesUseCommonMetric() {
        try(Fixture f=new Fixture()) {
            TiledLayerComponent upper=f.map(f.upper);upper.tileWidth=64;upper.tileHeight=32;upper.data=upper.createMapData();
            for(int y=0;y<8;y++)for(int x=0;x<16;x++)upper.data.setTile(x,y,1);
            f.reference.blocks.clear();f.reference.blocks.add(wall(1,1,.25f,1.275f,1.5f,.15f,80,90));
            SpatialVerticalJoinProposal p=f.proposal(f.lower,1,f.upper,1);assertTrue(p.diagnostic,p.valid);
            assertEquals(2.05f,p.after.blocks.first().y,.0001f);assertEquals(.3f,p.after.blocks.first().depth,.0001f);
            assertEquals(0,f.world.getMapper(EntityIndexComponent.class).get(f.lower).layerIndex);
            assertEquals(0,f.world.getMapper(EntityIndexComponent.class).get(f.upper).layerIndex);
        }
    }
    @Test public void staleOwnerTransformIsRefusedWithoutHistoryEntry() {
        try(Fixture f=new Fixture()) {
            TransformComponent transform=f.world.getMapper(TransformComponent.class).create(f.lower);transform.refreshCaches();f.world.process();
            SpatialVerticalJoinProposal p=f.proposal(f.lower,1,f.upper,1);assertTrue(p.diagnostic,p.valid);
            SpatialVerticalJoinCommand command=f.command(p);transform.x=12;transform.refreshCaches();f.history.execute(command);
            assertFalse(command.wasApplied());assertFalse(f.history.canUndo());assertEquals(60,f.source.blocks.first().height,0);
        }
    }
    @Test public void joinedFacesCanCrossImageAnchorCellsAndSurviveHistoryAndReload() {
        for(boolean transpose:new boolean[]{false,true}) try(Fixture f=new Fixture()) {
            TiledLayerComponent lower=f.map(f.lower),upper=f.map(f.upper);
            for(TiledLayerComponent t:new TiledLayerComponent[]{lower,upper}) {
                t.tileWidth=256;t.tileHeight=128;t.mapWidthCells=t.mapHeightCells=50;t.originY=0;
                t.defaultTileAltitude=t==lower?0:158;t.defaultTileHeight=t==lower?158:160;
                t.data=t.createMapData();
                for(int y=0;y<50;y++)for(int x=0;x<50;x++)t.data.setTile(x,y,1);
            }
            f.source.blocks.clear();f.reference.blocks.clear();
            SpatialBlockData fixed=wall(19,6,18,20.741352f,8,.25864792f,0,158);
            SpatialBlockData selected=wall(20,10,22,22,5,1,158,160);
            if(transpose)for(SpatialBlockData w:new SpatialBlockData[]{fixed,selected}) {
                float x=w.x,width=w.width;w.x=w.y;w.y=x;w.width=w.depth;w.depth=width;
                for(SpatialBlockData.LinkedTileRef ref:w.linkedTileRefs) {int gx=ref.gx;ref.gx=ref.gy;ref.gy=gx;}
            }
            f.source.blocks.add(fixed);f.source.nextSpatialBlockId=20;
            f.reference.blocks.add(selected);f.reference.nextSpatialBlockId=21;
            Json json=new Json();String fixedBefore=json.toJson(f.source),before=json.toJson(f.reference);
            String refs=json.toJson(selected.linkedTileRefs);
            SpatialVerticalJoinProposal p=f.proposal(f.upper,20,f.lower,19);
            assertTrue(p.diagnostic,p.valid);
            SpatialBlockData joined=p.after.blocks.first();
            assertEquals(21.975727f,transpose?joined.x:joined.y,.00001f);
            assertEquals(.25864792f,transpose?joined.width:joined.depth,.00001f);
            assertEquals(refs,json.toJson(joined.linkedTileRefs));
            assertEquals(158,joined.altitude,0);assertEquals(318,joined.altitude+joined.height,0);
            float[] expected=SpatialVerticalJoinProposal.rectangleIn(lower.data,fixed,upper.data);
            assertEquals(transpose?expected[0]:expected[1],transpose?joined.x:joined.y,0);
            assertEquals(transpose?expected[2]:expected[3],transpose?joined.width:joined.depth,0);
            SpatialVerticalJoinCommand c=f.command(p);f.history.execute(c);assertTrue(c.rejectionReason(),c.wasApplied());
            String after=json.toJson(f.reference);
            for(int i=0;i<3;i++) {
                f.history.undo();assertEquals(before,json.toJson(f.reference));
                f.history.redo();assertEquals(after,json.toJson(f.reference));
            }
            SpatialBlocksComponent reloaded=json.fromJson(SpatialBlocksComponent.class,after);
            assertTrue(SpatialStructureCompilation.tryCompile(reloaded,upper.data).diagnostic(),
                    SpatialStructureCompilation.tryCompile(reloaded,upper.data).success());
            assertEquals(after,json.toJson(reloaded));assertEquals(fixedBefore,json.toJson(f.source));
            assertEquals(1,f.history.getCursor());
        }
    }

    static SpatialBlockData wall(int id,int structure,float x,float y,float width,float depth,float altitude,float height) {
        SpatialBlockData b=new SpatialBlockData();b.id=id;b.structureId=structure;b.x=x;b.y=y;b.width=width;b.depth=depth;b.altitude=altitude;b.height=height;
        b.beginAuthoredLinkedTileRefs();for(int gy=(int)Math.floor(y);gy<(int)Math.ceil(y+depth);gy++)for(int gx=(int)Math.floor(x);gx<(int)Math.ceil(x+width);gx++)b.addLinkedTileRef(gx,gy,1);
        return b;
    }
    static final class Fixture implements AutoCloseable {
        final World world=new World(new WorldConfiguration());final HistoryManager history=new HistoryManager(16);
        final int lower=addMap(0,128),upper=addMap(80,90);
        final SpatialBlocksComponent source=world.getMapper(SpatialBlocksComponent.class).get(lower),reference=world.getMapper(SpatialBlocksComponent.class).get(upper);
        Fixture() {source.blocks.add(wall(1,1,1,2,3,.2f,0,60));reference.blocks.add(wall(1,1,1,2.05f,3,.3f,80,90));
            source.nextSpatialBlockId=reference.nextSpatialBlockId=2;world.process();}
        int addMap(float altitude,float height) {
            int e=world.create();TiledLayerComponent tiled=world.getMapper(TiledLayerComponent.class).create(e);
            tiled.projection=TiledProjection.ISO;tiled.tileWidth=32;tiled.tileHeight=16;tiled.mapWidthCells=16;tiled.mapHeightCells=8;tiled.chunkSize=4;
            tiled.defaultTileAltitude=altitude;tiled.defaultTileHeight=height;tiled.originY=altitude;tiled.data=tiled.createMapData();
            for(int y=0;y<8;y++)for(int x=0;x<16;x++)tiled.data.setTile(x,y,1);
            world.getMapper(SpatialBlocksComponent.class).create(e);world.getMapper(EntityIndexComponent.class).create(e);
            history.historyIds().ensureForEntity(e);return e;
        }
        TiledLayerComponent map(int entity) {return world.getMapper(TiledLayerComponent.class).get(entity);}
        SpatialVerticalJoinProposal proposal(int selected,int seed,int fixed,int ref) {
            SpatialVerticalJoinProposal.Search search=SpatialVerticalJoinProposal.search(world,selected,seed);
            for(SpatialVerticalJoinProposal.Candidate c:search.candidates)if(c.reference.entity==fixed && c.blockId==ref)
                return SpatialVerticalJoinProposal.create(world,search.selected,seed,c);
            throw new AssertionError("Reference not found: "+search.diagnostic);
        }
        SpatialVerticalJoinCommand command(SpatialVerticalJoinProposal p) {return new SpatialVerticalJoinCommand(world,history.historyIds(),p);}
        public void close() {world.dispose();}
    }
}
