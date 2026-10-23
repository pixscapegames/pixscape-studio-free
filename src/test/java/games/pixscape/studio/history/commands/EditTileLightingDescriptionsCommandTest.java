package games.pixscape.studio.history.commands;

import com.artemis.*;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.render.lighting.*;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.*;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.initializer.TiledMapInitializer;
import org.junit.Test;
import static org.junit.Assert.*;

public class EditTileLightingDescriptionsCommandTest {
    @Test public void editUndoRedoAndInitializerKeepIndependentPersistentProfiles(){
        World w=new World(new WorldConfiguration());HistoryManager history=new HistoryManager(16);ProjectConfig previous=ProjectConfig.getInstance();
        try{
            ProjectConfig config=new ProjectConfig();config.createSceneMeta("Main");ProjectConfig.setInstance(config);
            int e=w.create();TiledLayerComponent tiled=w.getMapper(TiledLayerComponent.class).create(e);
            tiled.projection=TiledProjection.ISO;tiled.tileWidth=256;tiled.tileHeight=128;tiled.mapWidthCells=tiled.mapHeightCells=8;tiled.chunkSize=8;tiled.data=tiled.createMapData();
            w.getMapper(EntityIndexComponent.class).create(e);w.getMapper(PixscapeIdentityComponent.class).create(e).stableId=1;
            // A shared asset declaration is valid before its first occurrence is placed.
            w.process();TileSurfaceDescription d=new TileSurfaceDescription();d.assetId=7;d.rounded=new RoundedWallDescription();
            Array<TileSurfaceDescription> values=new Array<>();values.add(d);
            history.execute(new EditTileLightingDescriptionsCommand(w,history.historyIds(),e,values));d.rounded.centerGX=99;
            for(int i=0;i<3;i++){assertEquals(1,tiled.lightingDescriptions.first().rounded.centerGX,0);history.undo();assertEquals(0,tiled.lightingDescriptions.size);history.redo();}
            TiledMapInitializer snapshot=new TiledMapInitializer(w,null);snapshot.syncFrom(e);
            tiled.lightingDescriptions.first().rounded.centerGX=5;int restored=w.create();snapshot.init(restored);w.process();
            assertEquals(1,w.getMapper(TiledLayerComponent.class).get(restored).lightingDescriptions.first().rounded.centerGX,0);
            assertNotSame(tiled.lightingDescriptions.first().rounded,w.getMapper(TiledLayerComponent.class).get(restored).lightingDescriptions.first().rounded);
        }finally{ProjectConfig.setInstance(previous);w.dispose();}
    }
    @Test public void incompatibleDeclarationIsRejectedWithoutChangingAuthoredStateOrHistory(){
        World w=new World(new WorldConfiguration());try{
            int e=w.create();TiledLayerComponent t=w.getMapper(TiledLayerComponent.class).create(e);t.projection=TiledProjection.ISO;t.tileWidth=256;t.tileHeight=128;t.mapWidthCells=t.mapHeightCells=8;t.chunkSize=8;t.data=t.createMapData();t.data.setTile(1,1,7);w.process();
            HistoryManager h=new HistoryManager(16);Array<TileSurfaceDescription> original=t.lightingDescriptions;
            TileSurfaceDescription d=new TileSurfaceDescription();d.assetId=7;d.rounded=new RoundedWallDescription();Array<TileSurfaceDescription> a=new Array<>();a.add(d);
            EditTileLightingDescriptionsCommand command=new EditTileLightingDescriptionsCommand(w,h.historyIds(),e,a);h.execute(command);
            assertNotNull(command.rejection());assertSame(original,t.lightingDescriptions);h.undo();assertSame(original,t.lightingDescriptions);
        }finally{w.dispose();}
    }
    @Test public void incompatibleWallMutationIsRejectedAtExistingAtomicCommandBoundary(){
        World w=new World(new WorldConfiguration());try{
            int e=w.create();TiledLayerComponent t=w.getMapper(TiledLayerComponent.class).create(e);t.projection=TiledProjection.ISO;t.tileWidth=256;t.tileHeight=128;t.mapWidthCells=t.mapHeightCells=8;t.chunkSize=8;t.data=t.createMapData();t.data.setTile(1,1,7);
            SpatialBlocksComponent blocks=w.getMapper(SpatialBlocksComponent.class).create(e);
            for(int i=0;i<2;i++){SpatialBlockData b=new SpatialBlockData();b.id=blocks.allocateNextSpatialBlockId();b.structureId=1;b.x=b.y=1;b.width=i==0?3:.25f;b.depth=i==0?.25f:3;b.height=150;b.shadowCaster=true;b.linkedTileRefsAuthored=true;
                SpatialBlockData.LinkedTileRef r=new SpatialBlockData.LinkedTileRef();r.gx=r.gy=1;r.tileAssetId=7;b.linkedTileRefs.add(r);blocks.blocks.add(b);}
            TileSurfaceDescription d=new TileSurfaceDescription();d.assetId=7;d.rounded=new RoundedWallDescription();t.lightingDescriptions.add(d);w.process();
            EditTileLightingDescriptionsCommand.validate(t,blocks,t.lightingDescriptions);
            Array<SpatialBlockData> candidate=new Array<>();for(SpatialBlockData b:blocks.blocks)candidate.add(b.copy());candidate.first().depth=.4f;
            assertEquals(CommandOutcome.REJECTED,SpatialBlockCommandSupport.validateBlocks(w,e,candidate));assertEquals(.25f,blocks.blocks.first().depth,0);
        }finally{w.dispose();}
    }
}
