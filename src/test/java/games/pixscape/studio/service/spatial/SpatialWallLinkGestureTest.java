package games.pixscape.studio.service.spatial;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.utils.Json;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.studio.history.HistoryManager;
import org.junit.Test;

import static org.junit.Assert.*;

public class SpatialWallLinkGestureTest {
    @Test public void contactReleaseIsIndependentAndWholeGestureUndoRestoresStart() {
        try(Fixture f=new Fixture()) {
            f.session.updateMove(1.75f,0);assertEquals(2.25f,f.session.candidate().x,0);
            assertNull(f.session.gestureProposal());assertEquals(0,f.blocks.links.size);
            assertTrue(f.session.commit(f.world,f.history,f.selection));assertEquals(0,f.blocks.links.size);
            assertEquals(1,f.history.getCursor());assertEquals(2.25f,f.wall(1).x,0);
            f.history.undo();assertEquals(.5f,f.wall(1).x,0);assertFalse(f.history.canUndo());
            f.history.redo();assertEquals(2.25f,f.wall(1).x,0);
        }
    }
    @Test public void overshootSharesMenuSolverAndCommitsOneSnapshot() {
        try(Fixture f=new Fixture()) {
            String reference=new Json().toJson(f.wall(2));
            f.session.updateMove(2.5f,0);SpatialLinkProposal p=f.session.gestureProposal();assertNotNull(p);assertTrue(p.diagnostic,p.valid);
            SpatialBlocksComponent contact=SpatialLinkProposal.copy(f.blocks);contact.blocks.set(0,f.session.candidate().copy());
            SpatialLinkProposal menu=SpatialLinkProposal.create(contact,f.tiled.data,1,2,false,128);
            assertEquals(new Json().toJson(menu.after),new Json().toJson(p.after));
            assertEquals(2.25f,f.session.candidate().x,0);assertEquals(0,f.blocks.links.size);
            assertTrue(f.session.commit(f.world,f.history,f.selection));assertEquals(1,f.history.getCursor());
            assertEquals(1,f.blocks.links.size);assertEquals(90,f.wall(1).height,0);assertEquals(90,f.tiled.defaultTileHeight,0);
            assertEquals(reference,new Json().toJson(f.wall(2)));
            f.history.undo();assertEquals(.5f,f.wall(1).x,0);assertEquals(60,f.wall(1).height,0);assertEquals(128,f.tiled.defaultTileHeight,0);
            f.history.redo();assertEquals(1,f.blocks.links.size);assertEquals(2.25f,f.wall(1).x,0);
        }
    }
    @Test public void hysteresisTangencyAndStationaryFramesDoNotRebuildProposals() {
        try(Fixture f=new Fixture()) {
            f.session.updateMove(1.75f,0);
            f.session.updateMove(1.75f,.1f);assertNull(f.session.gestureProposal());
            f.session.updateMove(2.35f,0);assertNotNull(f.session.gestureProposal());
            for(int i=0;i<1000;i++)f.session.updateMove(2.25f,0);
            assertNotNull(f.session.gestureProposal());assertEquals(1,f.session.proposalBuilds());
            f.session.updateMove(2.04f,0);assertNull(f.session.gestureProposal());
            f.session.updateMove(2.25f,0);assertNull(f.session.gestureProposal());
            f.session.updateMove(2.36f,0);assertNotNull(f.session.gestureProposal());assertEquals(1,f.session.proposalBuilds());
            f.session.cancel();assertEquals(.5f,f.wall(1).x,0);assertFalse(f.history.canUndo());
        }
    }
    @Test public void resizeAndLargeMouseJumpCannotCrossExternalVolume() {
        try(Fixture f=new Fixture()) {
            f.session.updateResize(SpatialBlockInteractiveEditSupport.ResizeHandle.MAX_X,15,2.1f);
            assertEquals(3,f.session.candidate().x+f.session.candidate().width,.0001f);
            assertNotNull(f.session.gestureProposal());assertTrue(f.session.gestureProposal().diagnostic,f.session.gestureProposal().valid);
            assertTrue(f.session.commit(f.world,f.history,f.selection));f.history.undo();assertEquals(.75f,f.wall(1).width,0);
        }
        try(Fixture f=new Fixture()) {
            f.session.updateMove(14,0);assertEquals(2.25f,f.session.candidate().x,.0001f);
        }
    }
    @Test public void invalidReferenceCommitsOnlySafeContactAndStaleGesturePublishesNothing() {
        try(Fixture f=new Fixture()) {
            f.wall(2).altitude=10;f.begin();f.session.updateMove(3,0);
            assertFalse(f.session.gestureProposal().valid);
            assertTrue(f.session.commit(f.world,f.history,f.selection));assertEquals(0,f.blocks.links.size);assertEquals(60,f.wall(1).height,0);
        }
        try(Fixture f=new Fixture()) {
            f.session.updateMove(3,0);f.wall(2).height=110;
            assertFalse(f.session.commit(f.world,f.history,f.selection));assertEquals(.5f,f.wall(1).x,0);assertFalse(f.history.canUndo());
        }
    }
    @Test public void ambiguousSimultaneousContactsNeverChooseAReference() {
        try(Fixture f=new Fixture()) {
            f.wall(1).depth=1.3f;
            f.blocks.blocks.add(wall(3,3,3,3,2,.3f,90));f.blocks.nextSpatialBlockId=4;
            for(int id=2;id<=3;id++) {SpatialBlockData b=f.wall(id);b.beginAuthoredLinkedTileRefs();
                for(int x=3;x<5;x++)b.addLinkedTileRef(x,id,1);}
            f.begin();f.session.updateMove(3,0);
            assertNull(f.session.gestureProposal());assertTrue(f.session.gestureMessage().contains("Ambiguous"));
            SpatialBlocksComponent safe=SpatialLinkProposal.copy(f.blocks);safe.blocks= f.session.candidateTopology().walls;
            SpatialStructureCompilation.Result compiled=SpatialStructureCompilation.tryCompile(safe,f.tiled.data);
            assertTrue(compiled.diagnostic(),compiled.success());
            assertTrue(f.session.commit(f.world,f.history,f.selection));assertEquals(0,f.blocks.links.size);
        }
    }
    @Test public void logicalThresholdsScaleWithZoomNotFramebufferDensity() {
        for(float zoom:new float[]{.5f,1,2})try(Fixture f=new Fixture()) {
            // These are logical viewport units; framebuffer density does not enter the basis.
            f.session.configureGesture(128,20/zoom,0,0,20/zoom);
            f.session.updateMove(1.75f+11*zoom/20,0);assertNull(f.session.gestureProposal());
            f.session.updateMove(1.75f+13*zoom/20,0);assertNotNull(f.session.gestureProposal());
        }
    }
    @Test public void connectedPerpendicularMemberSharesHeightButNotThickness() {
        try(Fixture f=new Fixture()) {
            f.wall(1).depth=.15f;
            SpatialBlockData corner=wall(3,1,.5f,2,.2f,1.2f,60);corner.beginAuthoredLinkedTileRefs();
            for(int y=2;y<4;y++)corner.addLinkedTileRef(0,y,1);
            f.blocks.blocks.add(corner);f.blocks.nextSpatialBlockId=4;f.begin();
            String fixed=new Json().toJson(f.wall(2));
            f.session.updateResize(SpatialBlockInteractiveEditSupport.ResizeHandle.MAX_X,8,2.1f);
            SpatialLinkProposal p=f.session.gestureProposal();assertNotNull(p);assertTrue(p.diagnostic,p.valid);
            assertTrue(p.affected.contains(3));assertTrue(f.session.commit(f.world,f.history,f.selection));
            assertEquals(90,f.wall(3).height,0);assertEquals(.2f,f.wall(3).width,0);
            assertEquals(.3f,f.wall(1).depth,0);assertEquals(fixed,new Json().toJson(f.wall(2)));
            assertTrue(SpatialStructureCompilation.tryCompile(f.blocks,f.tiled.data).success());
            f.history.undo();assertEquals(.15f,f.wall(1).depth,0);assertEquals(60,f.wall(3).height,0);
        }
    }
    @Test public void perpendicularReferenceUsesSameMenuProposalWithoutThicknessInheritance() {
        try(Fixture f=new Fixture()) {
            SpatialBlockData fixed=f.wall(2);fixed.width=.2f;fixed.depth=2;
            fixed.beginAuthoredLinkedTileRefs();for(int y=2;y<4;y++)fixed.addLinkedTileRef(3,y,1);
            f.begin();String reference=new Json().toJson(fixed);f.session.updateMove(3,0);
            SpatialLinkProposal p=f.session.gestureProposal();assertNotNull(p);assertTrue(p.diagnostic,p.valid);
            assertTrue(f.session.commit(f.world,f.history,f.selection));
            assertEquals(.3f,f.wall(1).depth,0);assertEquals(reference,new Json().toJson(f.wall(2)));
            f.history.undo();assertEquals(.5f,f.wall(1).x,0);
        }
    }
    @Test public void parallelConnectedBandFollowsAndReferenceEnsembleAndBarrelStayFixed() {
        try(Fixture f=new Fixture()) {
            f.blocks.blocks.add(wall(3,1,.8f,2,.75f,.3f,60));
            f.blocks.blocks.add(wall(4,2,4.8f,2,2,.3f,90));
            f.blocks.blocks.add(wall(5,5,7,2,.5f,.3f,25));f.blocks.nextSpatialBlockId=6;
            f.wall(2).x=.5f;f.wall(4).x=2.3f;f.wall(2).y=3;f.wall(4).y=3;f.wall(2).depth=.5f;f.wall(4).depth=.5f;
            f.wall(4).width=2.7f;f.wall(5).x=5;f.wall(5).y=3;
            for(int id:new int[]{1,3}) {SpatialBlockData b=f.wall(id);b.beginAuthoredLinkedTileRefs();
                for(int y=2;y<4;y++)for(int x=0;x<3;x++)b.addLinkedTileRef(x,y,1);}
            for(int id:new int[]{2,4,5}) {SpatialBlockData b=f.wall(id);b.beginAuthoredLinkedTileRefs();
                for(int x=(int)Math.floor(b.x);x<(int)Math.ceil(b.x+b.width);x++)b.addLinkedTileRef(x,3,1);}
            f.begin();String reference=new Json().toJson(f.wall(4)),barrel=new Json().toJson(f.wall(5));
            f.session.updateResize(SpatialBlockInteractiveEditSupport.ResizeHandle.MAX_Y,1,4);
            SpatialLinkProposal p=f.session.gestureProposal();assertNotNull(p);assertTrue(p.diagnostic,p.valid);
            assertTrue(f.session.commit(f.world,f.history,f.selection));
            assertEquals(.5f,f.wall(3).depth,0);assertEquals(90,f.wall(3).height,0);
            assertEquals(reference,new Json().toJson(f.wall(4)));assertEquals(barrel,new Json().toJson(f.wall(5)));
        }
    }
    @Test public void mapChangeAndPhysicalRejectionLeaveNoPartialTransaction() {
        try(Fixture f=new Fixture()) {
            f.session.updateMove(3,0);f.tiled.data.originX=10;
            assertFalse(f.session.commit(f.world,f.history,f.selection));assertEquals(.5f,f.wall(1).x,0);assertFalse(f.history.canUndo());
        }
        try(Fixture f=new Fixture()) {
            f.session.updateMove(3,0);
            games.pixscape.runtime.component.physics.PhysicsShapesComponent shapes=f.world.getMapper(
                    games.pixscape.runtime.component.physics.PhysicsShapesComponent.class).create(f.map);
            games.pixscape.runtime.physics.PhysicsShapeData shape=new games.pixscape.runtime.physics.PhysicsShapeData();
            shape.physicsShapeId=1;shape.spatialBlockId=1;shapes.shapes.add(shape);
            assertFalse(f.session.commit(f.world,f.history,f.selection));assertEquals(.5f,f.wall(1).x,0);
            assertEquals(0,f.blocks.links.size);assertEquals(128,f.tiled.defaultTileHeight,0);assertFalse(f.history.canUndo());
        }
    }
    @Test public void widthEditAlongPerpendicularNeighbourDoesNotProposeLink() {
        try(Fixture f=new Fixture()) {
            SpatialBlockData fixed=f.wall(2);f.wall(1).width=2.5f;fixed.x=3;fixed.width=.2f;fixed.depth=2;
            fixed.beginAuthoredLinkedTileRefs();for(int y=2;y<4;y++)fixed.addLinkedTileRef(3,y,1);
            SpatialStructureCompilation.Result start=SpatialStructureCompilation.tryCompile(f.blocks,f.tiled.data);
            assertTrue(start.diagnostic(),start.success());
            f.begin();String reference=new Json().toJson(fixed);
            assertTrue(f.session.updateResize(SpatialBlockInteractiveEditSupport.ResizeHandle.MAX_Y,1,3.6f));
            assertNull(f.session.gestureProposal());assertEquals(0,f.session.proposalBuilds());
            assertTrue(f.session.commit(f.world,f.history,f.selection));assertNull(f.session.commitMessage());
            assertEquals(0,f.blocks.links.size);assertEquals(1.6f,f.wall(1).depth,.0001f);
            assertEquals(reference,new Json().toJson(f.wall(2)));assertEquals(128,f.tiled.defaultTileHeight,0);
            f.history.undo();assertEquals(.3f,f.wall(1).depth,0);f.history.redo();assertEquals(1.6f,f.wall(1).depth,.0001f);
        }
    }
    @Test public void unrelatedFaceOrPropagatedMemberCannotArmResizeLink() {
        try(Fixture f=new Fixture()) {
            SpatialWallContactGuard guard=new SpatialWallContactGuard(f.blocks,f.wall(1));
            guard.face=2;guard.movingId=1;guard.referenceId=2;
            guard.intention(40,1,8);assertFalse(guard.armed); // MAX_Y handle cannot push MAX_X.
            guard.movingId=3;guard.intention(40,1,2);assertFalse(guard.armed); // Incidental propagated resize contact.
            guard.intention(40,0,15);assertTrue(guard.armed); // Whole-ensemble move can push a member.
            guard.intention(0,0,15);assertFalse(guard.armed);
            guard.movingId=1;guard.intention(40,1,2);assertTrue(guard.armed);
            guard.intention(40,1,8);assertFalse(guard.armed);
        }
    }
    @Test public void ordinaryResizeRejectionReportsPhysicsInsteadOfLinking() {
        try(Fixture f=new Fixture()) {
            assertTrue(f.session.updateResize(SpatialBlockInteractiveEditSupport.ResizeHandle.MIN_X,.2f,2));
            assertNull(f.session.gestureProposal());
            games.pixscape.runtime.component.physics.PhysicsShapesComponent shapes=f.world.getMapper(
                    games.pixscape.runtime.component.physics.PhysicsShapesComponent.class).create(f.map);
            games.pixscape.runtime.physics.PhysicsShapeData shape=new games.pixscape.runtime.physics.PhysicsShapeData();
            shape.physicsShapeId=1;shape.spatialBlockId=1;shapes.shapes.add(shape);
            assertFalse(f.session.commit(f.world,f.history,f.selection));
            assertEquals("Edit Spatial Wall",f.session.commitTitle());
            assertTrue(f.session.commitMessage(),f.session.commitMessage().contains("physics body"));
            assertFalse(f.session.commitMessage().contains("Stale gesture"));
            assertEquals(.5f,f.wall(1).x,0);assertEquals(0,f.blocks.links.size);assertFalse(f.history.canUndo());
        }
    }
    static SpatialBlockData wall(int id,int structure,float x,float y,float width,float depth,float height) {
        SpatialBlockData b=new SpatialBlockData();b.id=id;b.structureId=structure;b.x=x;b.y=y;b.width=width;b.depth=depth;b.height=height;
        b.beginAuthoredLinkedTileRefs();for(int gy=1;gy<4;gy++)for(int gx=0;gx<16;gx++)b.addLinkedTileRef(gx,gy,1);return b;
    }
    static final class Fixture implements AutoCloseable {
        final World world=new World(new WorldConfiguration());final int map=world.create();
        final TiledLayerComponent tiled=world.getMapper(TiledLayerComponent.class).create(map);
        final SpatialBlocksComponent blocks=world.getMapper(SpatialBlocksComponent.class).create(map);
        final HistoryManager history=new HistoryManager(32);final SpatialBlockSelectionService selection=new SpatialBlockSelectionService();
        final SpatialWallEditSession session=selection.wallEditSession();
        Fixture(){tiled.defaultTileHeight=128;tiled.data=new TiledMapLayerData(16,8,32,16,4,TiledProjection.ISO);
            for(int y=0;y<8;y++)for(int x=0;x<16;x++)tiled.data.setTile(x,y,1);
            blocks.blocks.add(SpatialWallLinkGestureTest.wall(1,1,.5f,2,.75f,.3f,60));blocks.blocks.add(SpatialWallLinkGestureTest.wall(2,2,3,2,2,.3f,90));blocks.nextSpatialBlockId=3;
            world.process();selection.selectBlock(map,1);history.historyIds().ensureForEntity(map);begin();}
        void begin(){assertTrue(session.begin(map,1,blocks,tiled.data));session.configureGesture(128,20,0,0,20);}
        SpatialBlockData wall(int id){for(SpatialBlockData b:blocks.blocks)if(b.id==id)return b;return null;}
        public void close(){selection.dispose();world.dispose();}
    }
}
