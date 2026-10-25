package games.pixscape.studio.history.commands;

import com.artemis.*;
import com.badlogic.gdx.utils.Json;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.spatial.*;
import games.pixscape.studio.history.*;
import games.pixscape.studio.helper.RotationArc;
import org.junit.Test;
import static org.junit.Assert.*;

public class ActorVolumeEditingTest {
    @Test public void clipSwitchSelectsItsAuthoredVolumeAndUndoRestoresSelection(){
        World world=new World(new WorldConfiguration());
        try{
            int e=world.create();
            var animation=world.getMapper(games.pixscape.runtime.component.AnimationComponent.class).create(e);
            animation.animationAssetIds.add(8);animation.currentClip="east";
            var asset=world.getMapper(games.pixscape.runtime.component.AssetRefComponent.class).create(e);asset.assetId=8;
            var spatial=world.getMapper(SpatialHeightComponent.class).create(e);spatial.volumes=new ActorVolumes();
            ActorVolumeData east=new ActorVolumeData();east.orientation=45;
            ActorVolumeData west=new ActorVolumeData();west.orientation=90;
            spatial.volumes.put(8,"east",east);spatial.volumes.put(8,"west",west);
            var before=EditAnimationCommand.Snapshot.capture(animation,asset);
            var after=before.copy();after.currentClip="west";
            var command=new EditAnimationCommand(world,new HistoryIdRegistry(),e,before,after,null,null);
            command.redo();assertEquals(90,spatial.volumes.get(asset.assetId,animation.currentClip).orientation,0);
            command.undo();assertEquals(45,spatial.volumes.get(asset.assetId,animation.currentClip).orientation,0);
            command.redo();animation.frame=7;
            assertSame(spatial.volumes.get(8,"west"),spatial.volumes.get(asset.assetId,animation.currentClip));
        }finally{world.dispose();}
    }
    @Test public void editUndoRedoPreservesOtherClipsAndActorTransform(){
        World world=new World(new WorldConfiguration());
        try{
            int e=world.create();TransformComponent transform=world.getMapper(TransformComponent.class).create(e);
            transform.x=37;transform.y=49;transform.rotationRad=.5f;
            SpatialHeightComponent h=world.getMapper(SpatialHeightComponent.class).create(e);
            h.volumes=new ActorVolumes();h.volumes.put(8,"east",new ActorVolumeData());h.volumes.put(8,"west",new ActorVolumeData());
            HistoryIdRegistry ids=new HistoryIdRegistry();ActorVolumeData edited=h.volumes.get(8,"east").copy();
            edited.orientation=405;edited.width=75;
            EditActorVolumeCommand command=new EditActorVolumeCommand(world,ids,e,8,"east",edited,null);
            for(int i=0;i<3;i++){
                command.redo();assertEquals(45,h.volumes.get(8,"east").orientation,0);assertEquals(75,h.volumes.get(8,"east").width,0);
                assertEquals(0,h.volumes.get(8,"west").orientation,0);
                command.undo();assertEquals(0,h.volumes.get(8,"east").orientation,0);
            }
            assertEquals(37,transform.x,0);assertEquals(49,transform.y,0);assertEquals(.5f,transform.rotationRad,0);
            h.volumes=new Json().fromJson(ActorVolumes.class,new Json().toJson(h.volumes));
            assertEquals(32,h.volumes.get(8,"east").width,0);
        }finally{world.dispose();}
    }
    @Test public void rotationGestureUnwrapsAcrossZeroAndSupportsFineAdjustment(){
        RotationArc.Gesture gesture=new RotationArc.Gesture();
        gesture.begin((float)Math.toRadians(359),false);
        assertEquals(2,gesture.update((float)Math.toRadians(1)),.03);
        assertEquals(91,gesture.update((float)Math.toRadians(90)),.03);
        gesture.begin((float)Math.toRadians(359),true);
        assertEquals(.2,gesture.update((float)Math.toRadians(1)),.003);
        assertTrue(RotationArc.hit(10,20,10,20,8));
        assertFalse(RotationArc.hit(30,20,10,20,8));
    }
}
