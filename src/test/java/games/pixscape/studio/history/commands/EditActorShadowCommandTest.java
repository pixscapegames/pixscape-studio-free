package games.pixscape.studio.history.commands;

import com.artemis.*;
import com.badlogic.gdx.utils.Json;
import games.pixscape.runtime.component.SurfaceLightingComponent;
import games.pixscape.runtime.render.lighting.ActorShadowProfile;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import org.junit.*;

public class EditActorShadowCommandTest {
    @Test public void freeEndpointsArePartOfNoopDetectionHistoryAndGameObjectCopies(){
        World world=new World(new WorldConfiguration());HistoryManager history=new HistoryManager(16);
        try{
            int e=world.create();ActorShadowProfile p=new ActorShadowProfile(.2f,.1f,.8f,.15f,.35f);
            history.execute(new EditActorShadowCommand(world,history.historyIds(),e,42,"idle",p,null));
            SurfaceLightingComponent s=world.getMapper(SurfaceLightingComponent.class).get(e);
            ActorShadowProfile moved=p.copy();moved.leftU=.4f;
            EditActorShadowCommand command=new EditActorShadowCommand(world,history.historyIds(),e,42,"idle",moved,null);
            Assert.assertFalse(command.isNoop());history.execute(command);Assert.assertEquals(.4f,s.shadowProfile(42,"idle").leftU,0);
            history.undo();Assert.assertEquals(.2f,s.shadowProfile(42,"idle").leftU,0);history.redo();
            SurfaceLightingComponent copy=new SurfaceLightingComponent();GenericEntityInitializer.copySurface(s,copy);
            s.shadowProfile(42,"idle").rightU=.9f;Assert.assertEquals(.8f,copy.shadowProfile(42,"idle").rightU,0);
            Json json=new Json();SurfaceLightingComponent loaded=json.fromJson(SurfaceLightingComponent.class,json.toJson(copy));
            Assert.assertEquals(.4f,loaded.shadowProfile(42,"idle").leftU,0);Assert.assertEquals(.35f,loaded.shadowProfile(42,"idle").thickness,0);
        }finally{world.dispose();}
    }
    @Test public void oneGestureAndRepeatedUndoRedoPreserveOtherClipAndReceiverSettings(){
        World world=new World(new WorldConfiguration());HistoryManager history=new HistoryManager(16);
        try{
            int e=world.create();SurfaceLightingComponent s=world.getMapper(SurfaceLightingComponent.class).create(e);
            s.anchorU=.27f;s.anchorV=.18f;s.directionX=2;s.directionY=-3;s.shadowCaster=true;
            history.execute(new EditActorShadowCommand(world,history.historyIds(),e,42,"idle",new ActorShadowProfile(.1f,.2f,0),null));
            // Several previews are transient; completion restores the original before one command.
            ActorShadowProfile p=new ActorShadowProfile(.2f,.3f,.2f);
            for(int i=0;i<5;i++){p.rightV+=.01f;EditActorShadowCommand.preview(world,e,42,"walk",p);}
            EditActorShadowCommand.preview(world,e,42,"walk",null);
            history.execute(new EditActorShadowCommand(world,history.historyIds(),e,42,"walk",p,null));
            for(int i=0;i<4;i++){
                Assert.assertEquals(.35f,s.shadowProfile(42,"walk").rightV,.00001f);history.undo();
                Assert.assertNull(s.shadowProfile(42,"walk"));Assert.assertEquals(.2f,s.shadowProfile(42,"idle").rightV,0);history.redo();
            }
            Assert.assertEquals(.27f,s.anchorU,0);Assert.assertEquals(.18f,s.anchorV,0);Assert.assertTrue(s.shadowCaster);
            Json json=new Json();SurfaceLightingComponent loaded=json.fromJson(SurfaceLightingComponent.class,json.toJson(s));
            Assert.assertEquals(.35f,loaded.shadowProfile(42,"walk").rightV,.00001f);
            SurfaceLightingComponent restored=new SurfaceLightingComponent();GenericEntityInitializer.copySurface(s,restored);
            s.shadowProfile(42,"walk").leftV=.9f;Assert.assertEquals(.2f,restored.shadowProfile(42,"walk").leftV,0);
        }finally{world.dispose();}
    }
    @Test public void undoRemovesAnOriginallyAbsentComponentAndRejectsInvalidValuesBeforeMutation(){
        World world=new World(new WorldConfiguration());HistoryManager history=new HistoryManager(8);
        try{
            int e=world.create();history.execute(new EditActorShadowCommand(world,history.historyIds(),e,0,null,new ActorShadowProfile(),null));
            for(int i=0;i<3;i++){history.undo();Assert.assertFalse(world.getMapper(SurfaceLightingComponent.class).has(e));history.redo();}
            try{new ActorShadowProfile(-1,0,0);Assert.fail();}catch(IllegalArgumentException expected){}
            try{new ActorShadowProfile(0,0,Float.NaN);Assert.fail();}catch(IllegalArgumentException expected){}
            Assert.assertEquals(0,world.getMapper(SurfaceLightingComponent.class).get(e).actorShadow.leftV,0);
        }finally{world.dispose();}
    }
}
