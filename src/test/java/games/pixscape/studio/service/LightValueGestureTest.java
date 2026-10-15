package games.pixscape.studio.service;

import com.artemis.*;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.light.*;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.EditLightingCommand;
import games.pixscape.studio.history.commands.EditLightingCommand.Property;
import org.junit.Test;
import static org.junit.Assert.*;

public class LightValueGestureTest {
    @Test public void removingTheConeComponentCancelsInclinationWithoutRewritingAnotherLight() {
        HistoryManager history=new HistoryManager(16);int e=world.create();
        world.getMapper(ConeLightComponent.class).create(e);world.process();
        LightValueGesture gesture=new LightValueGesture(world,history);
        gesture.begin(e,Property.INCLINATION,0,0,false);gesture.preview(-30);
        world.getMapper(ConeLightComponent.class).remove(e);
        PointLightComponent point=world.getMapper(PointLightComponent.class).create(e);
        gesture.preview(-40);gesture.cancel();
        assertFalse(gesture.active());assertEquals(128,point.height,0);assertEquals(1,point.intensity,0);
    }

    @Test public void inclinationIsContinuousBoundedAtomicAndIndependentOfOtherValues() {
        HistoryManager history=new HistoryManager(16);int e=world.create();
        ConeLightComponent light=world.getMapper(ConeLightComponent.class).create(e);
        light.height=160;light.radius=400;light.inclinationDeg=-12.25f;world.process();
        LightValueGesture gesture=new LightValueGesture(world,history);
        gesture.begin(e,Property.INCLINATION,10,20,false);gesture.preview(0);
        assertEquals(-12.25f,light.inclinationDeg,0);
        gesture.preview(-25.125f);assertEquals(-37.375f,light.inclinationDeg,0);gesture.commit();
        for(int i=0;i<3;i++){
            history.undo();assertEquals(-12.25f,light.inclinationDeg,0);
            history.redo();assertEquals(-37.375f,light.inclinationDeg,0);
        }
        gesture.begin(e,Property.INCLINATION,10,20,true);gesture.preview(5);
        assertEquals(-36.875f,light.inclinationDeg,.0001f);gesture.cancel();
        assertEquals(-37.375f,light.inclinationDeg,0);
        gesture.begin(e,Property.INCLINATION,10,20,false);gesture.preview(500);assertEquals(90,light.inclinationDeg,0);
        gesture.preview(-500);assertEquals(-90,light.inclinationDeg,0);gesture.cancel();
        assertEquals(-37.375f,light.inclinationDeg,0);
        assertEquals(160,light.height,0);assertEquals(400,light.radius,0);assertEquals(1,light.intensity,0);
    }

    private World world;
    @org.junit.Before public void setup() { world = new World(new WorldConfiguration()); }
    @org.junit.After public void cleanup() { world.dispose(); }
    @Test public void bothKindsPreviewOneUndoRedoAndPreserveOtherProperties() {
        for(boolean cone:new boolean[]{false,true}) {
            HistoryManager history=new HistoryManager(16);int e=world.create();
            TransformComponent t=world.getMapper(TransformComponent.class).create(e);t.x=10;t.y=20;
            if(cone)world.getMapper(ConeLightComponent.class).create(e).height=0;
            else world.getMapper(PointLightComponent.class).create(e).height=0;
            world.process();
            LightValueGesture gesture=new LightValueGesture(world,history);
            gesture.begin(e,Property.HEIGHT,120,240,false);
            gesture.preview(10);gesture.preview(33.125f);gesture.preview(-7.5f);
            assertEquals(-7.5f,EditLightingCommand.read(world,e,Property.HEIGHT),0f);
            gesture.commit();
            for(int i=0;i<3;i++) {
                history.undo();assertEquals(0f,EditLightingCommand.read(world,e,Property.HEIGHT),0f);
                history.redo();assertEquals(-7.5f,EditLightingCommand.read(world,e,Property.HEIGHT),0f);
            }
            history.undo();history.undo();assertEquals(0f,EditLightingCommand.read(world,e,Property.HEIGHT),0f);
            assertEquals(10f,t.x,0f);assertEquals(20f,t.y,0f);
        }
    }

    @Test public void cancellationRestoresExactlyWithoutHistoryAndNoOpAddsNothing() {
        {
            HistoryManager history=new HistoryManager(16);int e=world.create();
            PointLightComponent p=world.getMapper(PointLightComponent.class).create(e);p.height=17.25f;world.process();
            LightValueGesture gesture=new LightValueGesture(world,history);
            gesture.begin(e,Property.HEIGHT,10,20,false);gesture.preview(12);gesture.cancel();
            assertEquals(17.25f,p.height,0f);assertFalse(gesture.active());
            gesture.begin(e,Property.HEIGHT,10,20,false);gesture.commit();history.undo();
            assertEquals(17.25f,p.height,0f);
        }
    }

    @Test public void intensityHasZeroFloorNoMaximumFineControlAndIndependentHeight() {
        for(boolean cone:new boolean[]{false,true}) {
            HistoryManager history=new HistoryManager(16);int e=world.create();
            if(cone)world.getMapper(ConeLightComponent.class).create(e).intensity=0;
            else world.getMapper(PointLightComponent.class).create(e).intensity=0;
            world.process();float height=EditLightingCommand.read(world,e,Property.HEIGHT);
            LightValueGesture gesture=new LightValueGesture(world,history);
            gesture.begin(e,Property.INTENSITY,0,0,true);gesture.preview(-10);
            assertEquals(0f,EditLightingCommand.read(world,e,Property.INTENSITY),0f);
            gesture.preview(2);assertEquals(.2f,EditLightingCommand.read(world,e,Property.INTENSITY),.0001f);
            gesture.preview(2000);gesture.commit();
            assertEquals(200f,EditLightingCommand.read(world,e,Property.INTENSITY),.0001f);
            history.undo();assertEquals(0f,EditLightingCommand.read(world,e,Property.INTENSITY),0f);
            history.redo();assertEquals(200f,EditLightingCommand.read(world,e,Property.INTENSITY),0f);
            assertEquals(height,EditLightingCommand.read(world,e,Property.HEIGHT),0f);
        }
    }

    @Test public void disappearingEntityCannotBeRewrittenByTheOldGesture() {
        {
            HistoryManager history=new HistoryManager(16);int e=world.create();
            world.getMapper(PointLightComponent.class).create(e);world.process();
            LightValueGesture gesture=new LightValueGesture(world,history);
            gesture.begin(e,Property.HEIGHT,0,0,false);gesture.preview(20);
            history.historyIds().unbindEntity(e);world.delete(e);world.process();
            gesture.preview(30);gesture.commit();assertFalse(gesture.active());
        }
    }
}
