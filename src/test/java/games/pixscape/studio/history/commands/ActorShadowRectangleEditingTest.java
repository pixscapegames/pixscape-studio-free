package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.utils.Json;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.spatial.ActorSpatialRectangleData;
import games.pixscape.runtime.spatial.ActorSpatialRectangles;
import games.pixscape.studio.history.HistoryIdRegistry;
import org.junit.Test;

import static org.junit.Assert.*;

public class ActorShadowRectangleEditingTest {
    @Test public void clipSwitchSelectsItsAuthoredVolumeAndUndoRestoresSelection(){
        World world=new World(new WorldConfiguration());
        try{
            int e=world.create();
            var animation=world.getMapper(games.pixscape.runtime.component.AnimationComponent.class).create(e);
            animation.animationAssetIds.add(8);animation.currentClip="east";
            var asset=world.getMapper(games.pixscape.runtime.component.AssetRefComponent.class).create(e);asset.assetId=8;
            var spatial=world.getMapper(SpatialHeightComponent.class).create(e);spatial.rectangles=new ActorSpatialRectangles();
            ActorSpatialRectangleData east=new ActorSpatialRectangleData();east.pivotX=45;
            ActorSpatialRectangleData west=new ActorSpatialRectangleData();west.pivotX=90;
            spatial.rectangles.put(8,"east",east);spatial.rectangles.put(8,"west",west);
            var before=EditAnimationCommand.Snapshot.capture(animation,asset);
            var after=before.copy();after.currentClip="west";
            var command=new EditAnimationCommand(world,new HistoryIdRegistry(),e,before,after,null,null);
            command.redo();assertEquals(90,spatial.rectangles.get(asset.assetId,animation.currentClip).pivotX,0);
            command.undo();assertEquals(45,spatial.rectangles.get(asset.assetId,animation.currentClip).pivotX,0);
            command.redo();animation.frame=7;
            assertSame(spatial.rectangles.get(8,"west"),spatial.rectangles.get(asset.assetId,animation.currentClip));
        }finally{world.dispose();}
    }
    @Test public void editUndoRedoPreservesOtherClipsAndActorTransform(){
        World world=new World(new WorldConfiguration());
        try{
            int e=world.create();TransformComponent transform=world.getMapper(TransformComponent.class).create(e);
            transform.x=37;transform.y=49;transform.rotationRad=.5f;
            SpatialHeightComponent h=world.getMapper(SpatialHeightComponent.class).create(e);
            h.rectangles=new ActorSpatialRectangles();h.rectangles.put(8,"east",new ActorSpatialRectangleData());h.rectangles.put(8,"west",new ActorSpatialRectangleData());
            HistoryIdRegistry ids=new HistoryIdRegistry();ActorSpatialRectangleData edited=h.rectangles.get(8,"east").copy();
            edited.pivotX=45;edited.width=75;
            EditActorSpatialRectangleCommand command=new EditActorSpatialRectangleCommand(world,ids,e,8,"east",edited,null);
            for(int i=0;i<3;i++){
                command.redo();assertEquals(45,h.rectangles.get(8,"east").pivotX,0);assertEquals(75,h.rectangles.get(8,"east").width,0);
                assertEquals(0,h.rectangles.get(8,"west").pivotX,0);
                command.undo();assertEquals(0,h.rectangles.get(8,"east").pivotX,0);
            }
            assertEquals(37,transform.x,0);assertEquals(49,transform.y,0);assertEquals(.5f,transform.rotationRad,0);
            h.rectangles=new Json().fromJson(ActorSpatialRectangles.class,new Json().toJson(h.rectangles));
            assertEquals(32,h.rectangles.get(8,"east").width,0);
        }finally{world.dispose();}
    }
}
