package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.utils.Json;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.spatial.SpatialBlockLinks;
import games.pixscape.runtime.spatial.SpatialWallGeometry;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.spatial.SpatialLinkProposal;
import games.pixscape.studio.service.spatial.SpatialStructureCompilation;
import org.junit.Test;

import static org.junit.Assert.*;

public class SpatialBlockLinkCommandTest {
    @Test public void parallelBorderWithoutSharedCellsAndAtomicUndoRedo() {
        try (Fixture f = new Fixture()) {
            f.add(wall(1,1,1,1,3,.3f,90)); f.add(wall(2,2,4,1,2,.15f,60)); f.add(wall(9,9,10,10,.4f,.5f,45));
            SpatialLinkProposal p = f.propose(2,1,false); assertTrue(p.diagnostic,p.valid);
            String untouched = new Json().toJson(f.block(1)), sentinel = new Json().toJson(f.block(9));
            f.execute(p); assertEquals(1,f.blocks.links.size); assertEquals(.3f,f.block(2).depth,0);
            assertEquals(90,f.tiled.defaultTileHeight,0); assertEquals(90,f.block(2).height,0);
            assertEquals(untouched,new Json().toJson(f.block(1))); assertEquals(sentinel,new Json().toJson(f.block(9)));
            assertEquals(SpatialWallGeometry.JunctionClassification.NONE, classify(f.block(1),f.block(2)));
            for (SpatialBlockData.LinkedTileRef a : f.block(1).linkedTileRefs)
                for (SpatialBlockData.LinkedTileRef b : f.block(2).linkedTileRefs) assertFalse(a.gx == b.gx && a.gy == b.gy);
            assertTrue(SpatialStructureCompilation.tryCompile(f.blocks,f.tiled.data).success());
            f.history.undo(); assertEquals(0,f.blocks.links.size); assertEquals(60,f.block(2).height,0); assertEquals(128,f.tiled.defaultTileHeight,0);
            f.history.redo(); assertEquals(.3f,f.block(2).depth,0); assertEquals(1,f.blocks.links.size);
        }
    }

    @Test public void perpendicularAndMixedChainOnlyShareHeight() {
        try (Fixture f = new Fixture()) {
            f.add(wall(1,1,1,1,3,.3f,90)); f.add(wall(2,2,3.8f,1.3f,.2f,2,60)); f.add(wall(3,2,3.8f,3.1f,2,.2f,60));
            f.execute(f.propose(2,1,false));
            assertEquals(.3f,f.block(1).depth,0); assertEquals(.2f,f.block(2).width,0); assertEquals(.2f,f.block(3).depth,0);
            assertEquals(90,f.block(2).height,0); assertEquals(90,f.block(3).height,0);
            SpatialBlockData changed = f.block(3).copy(); changed.height=110;
            f.history.execute(new EditSpatialBlockCommand(f.world,f.history.historyIds(),null,f.map,3,f.block(3),changed));
            for (SpatialBlockData wall : f.blocks.blocks) assertEquals(110,wall.height,0);
            changed=f.block(1).copy(); changed.depth=.4f;
            f.history.execute(new EditSpatialBlockCommand(f.world,f.history.historyIds(),null,f.map,1,f.block(1),changed));
            assertEquals(.4f,f.block(1).depth,0);
            assertEquals(.2f,f.block(2).width,0); assertEquals(.2f,f.block(3).depth,0);
            assertEquals(1.4f,f.block(2).y,.0001f); assertEquals(3.3f,f.block(2).y+f.block(2).depth,.0001f);
            f.history.undo(); assertEquals(1.3f,f.block(2).y,.0001f);
            f.history.redo(); assertEquals(1.4f,f.block(2).y,.0001f);
        }
    }

    @Test public void unlinkedTouchingBarrelStaysIndependent() {
        try (Fixture f = new Fixture()) {
            f.add(wall(1,1,1,1,3,.3f,90)); f.add(wall(2,2,4,1,.5f,.3f,25));
            SpatialBlockData changed = f.block(1).copy(); changed.height=100;
            f.history.execute(new EditSpatialBlockCommand(f.world,f.history.historyIds(),null,f.map,1,f.block(1),changed));
            assertEquals(25,f.block(2).height,0); assertEquals(2,f.block(2).structureId); assertEquals(0,f.blocks.links.size);
        }
    }

    @Test public void mismatchedBasesRejectAtomicallyAndLinkedPhysicalEnvelopeMayCrossAnchorCells() {
        try (Fixture f = new Fixture()) {
            f.add(wall(1,1,1,1,3,.3f,90)); f.add(wall(2,2,4,1,2,.15f,60));
            f.block(2).altitude=10;
            String before = new Json().toJson(f.blocks);
            SpatialLinkProposal p = f.propose(2,1,false); assertFalse(p.valid); assertTrue(p.diagnostic.contains("altitude"));
            f.execute(p); assertEquals(before,new Json().toJson(f.blocks)); assertFalse(f.history.canUndo());
            f.block(2).altitude=0; f.block(1).depth=1.3f; f.block(1).addLinkedTileRef(1,2,1); f.block(1).addLinkedTileRef(2,2,1); f.block(1).addLinkedTileRef(3,2,1);
            p=f.propose(2,1,false); assertTrue(p.diagnostic,p.valid);
            f.execute(p); assertEquals(1,f.blocks.links.size); assertTrue(f.history.canUndo());
            assertTrue(SpatialStructureCompilation.tryCompile(f.blocks,f.tiled.data).success());
        }
    }

    @Test public void cornerEndpointFollowsChangedBandButPerpendicularThicknessStays() {
        try (Fixture f = new Fixture()) {
            f.add(wall(1,1,4.1f,1,.3f,3,90)); f.add(wall(2,2,4.1f,4,.2f,2,60)); f.add(wall(3,2,2,5,2.3f,.2f,60));
            SpatialLinkProposal p=f.propose(2,1,false); assertTrue(p.diagnostic,p.valid); f.execute(p);
            assertEquals(.3f,f.block(2).width,0); assertEquals(.2f,f.block(3).depth,0);
            assertEquals(f.block(2).x+f.block(2).width,f.block(3).x+f.block(3).width,.0001f);
            assertEquals(2.4f,f.block(3).width,.0001f); assertEquals(5,f.block(3).y,0);
        }
    }

    @Test public void detachSplitsWithoutChangingDimensionsAndDeleteRestoresLinks() {
        try (Fixture f = new Fixture()) {
            f.add(wall(1,1,1,1,3,.3f,90)); f.add(wall(2,2,4,1,2,.3f,90)); f.execute(f.propose(2,1,false));
            f.execute(f.propose(2,1,true)); assertEquals(0,f.blocks.links.size); assertNotEquals(f.block(1).structureId,f.block(2).structureId);
            assertEquals(.3f,f.block(2).depth,0); f.history.undo(); assertEquals(1,f.blocks.links.size);
            f.history.execute(new DeleteSpatialBlockCommand(f.world,f.history.historyIds(),null,f.map,2));
            assertEquals(0,f.blocks.links.size); f.history.undo(); assertEquals(1,f.blocks.links.size);
            assertTrue(SpatialStructureCompilation.tryCompile(f.blocks,f.tiled.data).success());
        }
    }

    @Test public void detachWithGeometricAlternativeLeavesOneEnsemble() {
        try (Fixture f = new Fixture()) {
            f.add(wall(1,1,1,1,3,.3f,90)); f.add(wall(2,1,3.8f,1,2,.3f,90));
            f.execute(f.propose(2,1,false)); assertEquals(1,f.blocks.links.size);
            f.execute(f.propose(2,1,true)); assertEquals(0,f.blocks.links.size); assertEquals(f.block(1).structureId,f.block(2).structureId);
        }
    }

    @Test public void parallelConnectedMembersInheritThicknessAsWellAsHeight() {
        try (Fixture f = new Fixture()) {
            f.add(wall(1,1,1,1,3,.3f,90)); f.add(wall(2,2,4,1,2,.15f,60)); f.add(wall(3,2,5.8f,1,2,.15f,60));
            f.execute(f.propose(2,1,false)); assertEquals(.3f,f.block(3).depth,0); assertEquals(90,f.block(3).height,0);
        }
    }

    @Test public void stalePreviewIsRejectedAndCancellationLeavesSourceUnchanged() {
        try (Fixture f = new Fixture()) {
            f.add(wall(1,1,1,1,3,.3f,90)); f.add(wall(2,2,4,1,2,.15f,60));
            String before = new Json().toJson(f.blocks); SpatialLinkProposal p=f.propose(2,1,false);
            assertEquals(before,new Json().toJson(f.blocks)); f.block(2).height=70;
            f.execute(p); assertEquals(0,f.blocks.links.size); assertEquals(70,f.block(2).height,0); assertFalse(f.history.canUndo());
        }
    }

    @Test public void failedLinkedPhysicsPreparationPublishesNeitherLinkGeometryNorDefaultHeight() {
        try (Fixture f = new Fixture()) {
            f.add(wall(1,1,1,1,3,.3f,90)); f.add(wall(2,2,4,1,2,.15f,60));
            games.pixscape.runtime.component.physics.PhysicsShapesComponent shapes = f.world.getMapper(
                    games.pixscape.runtime.component.physics.PhysicsShapesComponent.class).create(f.map);
            games.pixscape.runtime.physics.PhysicsShapeData shape = new games.pixscape.runtime.physics.PhysicsShapeData();
            shape.physicsShapeId=1; shape.spatialBlockId=2; shapes.shapes.add(shape);
            com.badlogic.gdx.utils.Array<SpatialBlockData> original = f.blocks.blocks;
            f.execute(f.propose(2,1,false));
            assertSame(original,f.blocks.blocks); assertEquals(0,f.blocks.revision); assertEquals(0,f.blocks.links.size);
            assertEquals(128,f.tiled.defaultTileHeight,0); assertEquals(60,f.block(2).height,0); assertFalse(f.history.canUndo());
        }
    }

    @Test public void detachKeepsAlternateExplicitPathAndEveryFootprint() {
        try (Fixture f = new Fixture()) {
            f.add(wall(1,1,1,1,3,.3f,90)); f.add(wall(2,2,4,1,2,.3f,90));
            f.add(wall(3,3,1,1.3f,5,.3f,90));
            f.execute(f.propose(2,1,false)); f.execute(f.propose(3,1,false)); f.execute(f.propose(2,3,false));
            assertEquals(3,f.blocks.links.size);
            f.execute(f.propose(2,1,true)); assertEquals(2,f.blocks.links.size);
            assertEquals(f.block(1).structureId,f.block(2).structureId);
            assertEquals(4,f.block(2).x,0); assertEquals(2,f.block(2).width,0);
            assertEquals(1.3f,f.block(3).y,0); assertEquals(.3f,f.block(3).depth,0);
        }
    }

    @Test public void copyAndJsonPreserveOwnerLocalLinksAndOldDataLoadsEmpty() {
        try (Fixture f = new Fixture()) {
            f.add(wall(1,1,1,1,3,.3f,90)); f.add(wall(2,2,4,1,2,.3f,90)); f.execute(f.propose(2,1,false));
            Json json=new Json(); SpatialBlocksComponent loaded=json.fromJson(SpatialBlocksComponent.class,json.toJson(f.blocks));
            assertEquals(1,loaded.links.size); SpatialBlockLinks.validate(loaded.blocks,loaded.links);
            SpatialBlocksComponent copy=SpatialLinkProposal.copy(loaded); copy.links.first().firstBlockId=99;
            assertEquals(2,loaded.links.first().firstBlockId);
            assertEquals(0,json.fromJson(SpatialBlocksComponent.class,"{}").links.size);
            loaded.links.add(loaded.links.first().copy());
            assertThrows(IllegalArgumentException.class,()->SpatialBlockLinks.validate(loaded.blocks,loaded.links));
            loaded.links.pop(); loaded.links.first().firstBlockId=99;
            assertThrows(IllegalArgumentException.class,()->SpatialBlockLinks.validate(loaded.blocks,loaded.links));
        }
    }

    @Test public void actualMapMaterializerCopiesLocalGraphWithoutAliasing() {
        games.pixscape.studio.configuration.ProjectConfig previous = games.pixscape.studio.configuration.ProjectConfig.getInstance();
        games.pixscape.studio.configuration.ProjectConfig config = new games.pixscape.studio.configuration.ProjectConfig();
        config.createSceneMeta("Map copy"); games.pixscape.studio.configuration.ProjectConfig.setInstance(config);
        try (Fixture f = new Fixture()) {
            f.add(wall(1,1,1,1,3,.3f,90)); f.add(wall(2,2,4,1,2,.3f,90)); f.execute(f.propose(2,1,false));
            f.world.getMapper(games.pixscape.runtime.component.EntityIndexComponent.class).create(f.map);
            f.world.getMapper(games.pixscape.runtime.component.PixscapeIdentityComponent.class).create(f.map).stableId=1;
            f.tiled.projection=games.pixscape.runtime.tiled.TiledProjection.ISO;
            f.tiled.tileWidth=256; f.tiled.tileHeight=128; f.tiled.mapWidthCells=f.tiled.mapHeightCells=16; f.tiled.chunkSize=4;
            for (int y=0;y<16;y++) for (int x=0;x<16;x++) {
                f.tiled.tileXs.add(x); f.tiled.tileYs.add(y); f.tiled.tileAssetIds.add(1); f.tiled.tileTransformFlags.add((byte)0);
            }
            games.pixscape.studio.history.initializer.TiledMapInitializer initializer = new games.pixscape.studio.history.initializer.TiledMapInitializer(f.world,null);
            initializer.syncFrom(f.map); int other=f.world.create(); initializer.init(other); f.world.process();
            // The caller allocates the new map identity; block/link IDs are local to its owner.
            f.world.getMapper(games.pixscape.runtime.component.PixscapeIdentityComponent.class).get(other).stableId=2;
            SpatialBlocksComponent copied=f.world.getMapper(SpatialBlocksComponent.class).get(other);
            assertEquals(1,copied.links.size); SpatialBlockLinks.validate(copied.blocks,copied.links);
            assertTrue(SpatialStructureCompilation.tryCompile(copied,f.world.getMapper(TiledLayerComponent.class).get(other).data).success());
            copied.links.clear(); copied.blocks.first().height=200;
            assertEquals(1,f.blocks.links.size); assertEquals(90,f.block(1).height,0);
        } finally { games.pixscape.studio.configuration.ProjectConfig.setInstance(previous); }
    }

    private static SpatialWallGeometry.JunctionClassification classify(SpatialBlockData a,SpatialBlockData b) {
        return SpatialWallGeometry.classifyJunction(a,b,new SpatialWallGeometry.Bounds(),new SpatialWallGeometry.Bounds(),new SpatialWallGeometry.Junction());
    }
    static SpatialBlockData wall(int id,int structure,float x,float y,float width,float depth,float height) {
        SpatialBlockData wall=new SpatialBlockData(); wall.id=id; wall.structureId=structure; wall.x=x; wall.y=y; wall.width=width; wall.depth=depth; wall.height=height;
        wall.beginAuthoredLinkedTileRefs();
        for(int gy=(int)Math.floor(y);gy<(int)Math.ceil(y+depth);gy++) for(int gx=(int)Math.floor(x);gx<(int)Math.ceil(x+width);gx++) wall.addLinkedTileRef(gx,gy,1);
        return wall;
    }
    static final class Fixture implements AutoCloseable {
        final World world=new World(new WorldConfiguration()); final int map=world.create();
        final TiledLayerComponent tiled=world.getMapper(TiledLayerComponent.class).create(map);
        final SpatialBlocksComponent blocks=world.getMapper(SpatialBlocksComponent.class).create(map);
        final HistoryManager history=new HistoryManager(32);
        Fixture() { tiled.defaultTileHeight=128; tiled.data=new TiledMapLayerData(16,16,256,128,4); for(int y=0;y<16;y++)for(int x=0;x<16;x++)tiled.data.setTile(x,y,1); world.process(); history.historyIds().ensureForEntity(map); }
        void add(SpatialBlockData wall) { blocks.blocks.add(wall); while(blocks.nextSpatialBlockId<=wall.id)blocks.allocateNextSpatialBlockId(); }
        SpatialBlockData block(int id) { for(SpatialBlockData wall:blocks.blocks)if(wall.id==id)return wall; return null; }
        SpatialLinkProposal propose(int selected,int reference,boolean unlink) { return SpatialLinkProposal.create(blocks,tiled.data,selected,reference,unlink,tiled.defaultTileHeight); }
        void execute(SpatialLinkProposal p) { history.execute(new SpatialBlockLinkCommand(world,history.historyIds(),map,p)); }
        @Override public void close() { world.dispose(); }
    }
}
