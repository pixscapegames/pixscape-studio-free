package games.pixscape.studio.ui.property.entityproperties;

import com.artemis.*;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.math.Vector2;
import games.pixscape.studio.history.commands.EditActorShadowCommand;
import games.pixscape.runtime.render.lighting.ActorShadowProfile;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.service.*;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.*;
import games.pixscape.studio.service.asset.AnimationAssetAuthoringService;
import games.pixscape.studio.service.atlas.AtlasStudioService;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import games.pixscape.studio.ui.widget.*;
import org.junit.*;

import java.lang.reflect.Proxy;

/** Regression through native slider touch, release, and cancellation events. */
public class ActorShadowPanelTest {
    @BeforeClass public static void skin(){VisUiTestBootstrap.loadSkin();}
    @AfterClass public static void disposeSkin(){VisUiTestBootstrap.unloadSkin();}
    @Test public void sliderPreviewsImmediatelyAndCommitsOneUndoableGesture()throws Exception {
        try(Harness h=new Harness()){
            int cursor=h.history.getCursor();h.begin(.1f);h.drag(.35f);
            Assert.assertTrue(h.input.isDragging());Assert.assertEquals(cursor,h.history.getCursor());h.value(.35f);
            h.end(.35f);Assert.assertEquals(cursor+1,h.history.getCursor());h.value(.35f);
            h.history.undo();h.panel.setEntityId(h.entity);h.value(0);
            h.history.redo();h.panel.setEntityId(h.entity);h.value(.35f);
            Assert.assertEquals(.35f,h.input.getValue(),.00001f);
        }
    }
    @Test public void nativeCancellationRestoresAnAbsentProfileWithoutHistory()throws Exception {
        try(Harness h=new Harness()){
            EditActorShadowCommand.preview(h.world,h.entity,42,"idle",null);h.panel.setEntityId(h.entity);
            int cursor=h.history.getCursor();h.begin(.2f);h.drag(.4f);h.value(.4f);
            h.stage.cancelTouchFocus();Assert.assertFalse(h.input.isDragging());
            Assert.assertNull(h.surface().shadowProfile(42,"idle"));Assert.assertEquals(cursor,h.history.getCursor());
            Assert.assertEquals(0,h.input.getValue(),0);
        }
    }
    @Test public void clipSelectionAndJsonKeepIndependentValuesAndNoRefreshAuthorship()throws Exception {
        try(Harness h=new Harness()){
            h.begin(.35f);h.end(.35f);h.value(.35f);
            h.animation.currentClip="walk";h.panel.act(0);
            Assert.assertNull(h.surface().shadowProfile(42,"walk"));Assert.assertEquals(0,h.input.getValue(),0);
            h.begin(.2f);h.end(.2f);Assert.assertEquals(.2f,h.surface().shadowProfile(42,"walk").thickness,.00001f);
            h.animation.currentClip="idle";h.panel.act(0);h.value(.35f);
            h.panel.setEntityId(-1);h.panel.setEntityId(h.entity);h.value(.35f);
            Json json=new Json();SurfaceLightingComponent loaded=json.fromJson(SurfaceLightingComponent.class,json.toJson(h.surface()));
            Assert.assertEquals(.35f,loaded.shadowProfile(42,"idle").thickness,.00001f);
            Assert.assertEquals(.2f,loaded.shadowProfile(42,"walk").thickness,.00001f);
            Assert.assertNull(loaded.shadowProfile(42,"unknown"));
        }
    }
    @Test public void thresholdUsesTheSameSliderAndCommitsOneGesture()throws Exception {
        try(Harness h=new Harness()){
            LightingPanel lighting=new LightingPanel(h.context,false);lighting.setEntityId(h.entity);lighting.pack();h.stage.addActor(lighting);
            SimpleFloatSlider threshold=lighting.findActor("lighting-alpha-threshold");
            Assert.assertEquals(0,threshold.getMinValue(),0);Assert.assertEquals(1,threshold.getMaxValue(),0);
            int cursor=h.history.getCursor();h.event(threshold,InputEvent.Type.touchDown,.2f);h.event(threshold,InputEvent.Type.touchDragged,.7f);
            Assert.assertEquals(.7f,h.surface().alphaThreshold,.00001f);Assert.assertEquals(cursor,h.history.getCursor());
            h.event(threshold,InputEvent.Type.touchUp,.7f);Assert.assertEquals(cursor+1,h.history.getCursor());
            h.history.undo();Assert.assertEquals(.5f,h.surface().alphaThreshold,0);h.history.redo();Assert.assertEquals(.7f,h.surface().alphaThreshold,.00001f);
            h.event(threshold,InputEvent.Type.touchDown,.3f);h.stage.cancelTouchFocus();Assert.assertEquals(.7f,h.surface().alphaThreshold,.00001f);
            Assert.assertEquals(cursor+1,h.history.getCursor());
        }
    }
    @Test public void castShadowOwnsVisibilityAndChildIndentation()throws Exception {
        try(Harness h=new Harness()) {
            LightingPanel lighting=new LightingPanel(h.context,false);lighting.setEntityId(h.entity);h.stage.addActor(lighting);
            CollapsibleVisTable block=lighting.findActor("actor-caster-fields");
            com.kotcrab.vis.ui.widget.VisCheckBox cast=lighting.findActor("lighting-cast-shadow");
            Assert.assertFalse(block.isCollapsed());
            Assert.assertEquals(games.pixscape.studio.ui.config.CommonLayout.PAD_LEFT_SUBMENU,lighting.getCell(block).getPadLeft(),0);
            int cursor=h.history.getCursor();cast.setChecked(false);Assert.assertTrue(block.isCollapsed());
            h.history.undo();lighting.setEntityId(h.entity);Assert.assertFalse(block.isCollapsed());
            h.history.redo();lighting.setEntityId(h.entity);Assert.assertTrue(block.isCollapsed());
            Assert.assertEquals(cursor+1,h.history.getCursor());
        }
    }
    @Test public void actorInspectorKeepsReceiverDataWithoutExposingLegacyFields()throws Exception {
        try(Harness h=new Harness()){
            h.surface().altitude=17;h.surface().anchorU=.2f;h.surface().anchorV=.3f;h.surface().directionX=2;h.surface().directionY=-3;
            LightingPanel lighting=new LightingPanel(h.context,false);lighting.setEntityId(h.entity);h.stage.addActor(lighting);
            Assert.assertEquals(17,h.surface().altitude,0);Assert.assertEquals(.2f,h.surface().anchorU,0);
            Assert.assertEquals(.3f,h.surface().anchorV,0);Assert.assertEquals(2,h.surface().directionX,0);Assert.assertEquals(-3,h.surface().directionY,0);
            Assert.assertFalse(hasLabel(lighting,"Altitude:"));Assert.assertFalse(hasLabel(lighting,"Receiver anchor U:"));
            Assert.assertFalse(hasLabel(lighting,"Receiver anchor V:"));Assert.assertFalse(hasLabel(lighting,"Direction X:"));Assert.assertFalse(hasLabel(lighting,"Direction Y:"));
            Assert.assertTrue(hasLabel(lighting,"Threshold:"));Assert.assertTrue(hasLabel(lighting,"Thickness:"));
        }
    }
    @Test public void repetitionIsAvailableForSpritesAndDisabledForAnimations()throws Exception {
        try(Harness h=new Harness()){
            h.world.getMapper(TextureRegionComponent.class).create(h.entity);h.world.getMapper(RenderMaterialComponent.class).create(h.entity);
            h.world.getMapper(RenderRepeatComponent.class).create(h.entity).repeatX=true;
            RepeatablePanel panel=new RepeatablePanel(h.context);panel.setEntityId(h.entity);
            Assert.assertFalse(panel.isApplicable());Assert.assertTrue(field(panel,"repeatXBox",com.kotcrab.vis.ui.widget.VisCheckBox.class).isDisabled());
            Assert.assertTrue(h.world.getMapper(RenderRepeatComponent.class).get(h.entity).repeatX);
            int sprite=h.world.create();h.world.getMapper(TextureRegionComponent.class).create(sprite);h.world.getMapper(RenderMaterialComponent.class).create(sprite);
            panel.setEntityId(sprite);Assert.assertTrue(panel.isApplicable());
            var repeat=field(panel,"repeatXBox",com.kotcrab.vis.ui.widget.VisCheckBox.class);Assert.assertFalse(repeat.isDisabled());
            int cursor=h.history.getCursor();repeat.setChecked(true);Assert.assertTrue(h.world.getMapper(RenderRepeatComponent.class).get(sprite).repeatX);
            Assert.assertEquals(cursor+1,h.history.getCursor());h.history.undo();Assert.assertFalse(h.world.getMapper(RenderRepeatComponent.class).has(sprite));
        }
    }
    private static boolean hasLabel(com.badlogic.gdx.scenes.scene2d.Group group,String text){
        for(var child:group.getChildren()){
            if(child instanceof com.kotcrab.vis.ui.widget.VisLabel label && text.contentEquals(label.getText()))return true;
            if(child instanceof com.badlogic.gdx.scenes.scene2d.Group nested && hasLabel(nested,text))return true;
        }
        return false;
    }
    private static <T>T field(Object owner,String name,Class<T> type)throws Exception{
        var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return type.cast(f.get(owner));
    }
    private static final class Harness implements AutoCloseable {
        final World world=new World(new WorldConfiguration());final HistoryManager history=new HistoryManager(16);
        final IdentityRegistry identities=new IdentityRegistry();final Stage stage;final ActorShadowPanel panel;final EntityPropertiesContext context;
        final int entity;final AnimationComponent animation;final SimpleFloatSlider input;
        Harness()throws Exception{
            SceneMeta meta=new SceneMeta();identities.bind(world,meta);LayerService layers=new LayerService(world,null,history.historyIds(),identities);
            AssetMetaDatabase assets=new AssetMetaDatabase();
            context=new EntityPropertiesContext(world,history,new PhysicsSelectionService(),new PhysicsService(world,null,meta),
                    layers,new AtlasStudioService(null),new SelectionService(world,layers),identities,new IconResolver(world),()->{},
                    assets::findById,ignored->{},Array::new,new AnimationAssetAuthoringService(()->assets,
                    ()->Gdx.files.local("build/tmp/actor-panel-unused.json"),ignored->{}),0,meta);
            entity=world.create();world.getMapper(AssetRefComponent.class).create(entity).assetId=42;
            animation=world.getMapper(AnimationComponent.class).create(entity);animation.currentClip="idle";
            world.getMapper(SurfaceLightingComponent.class).create(entity).shadowCaster=true;
            EditActorShadowCommand.preview(world,entity,42,"idle",new ActorShadowProfile());
            panel=new ActorShadowPanel(context);panel.setEntityId(entity);
            input=field(panel,"thickness",SimpleFloatSlider.class);
            Batch batch=(Batch)Proxy.newProxyInstance(Batch.class.getClassLoader(),new Class[]{Batch.class},(o,m,a)->{
                if(m.getReturnType()==boolean.class)return false;if(m.getReturnType()==int.class)return 0;return null;});
            stage=new Stage(new ScreenViewport(),batch);stage.addActor(panel);panel.pack();
        }
        void event(SimpleFloatSlider slider,InputEvent.Type type,float value){
            var style=slider.getStyle();float knob=style.knob.getMinWidth();float left=style.background.getLeftWidth(),right=style.background.getRightWidth();
            float x=left+knob*.5f+(slider.getWidth()-left-right-knob)*(value-slider.getMinValue())/(slider.getMaxValue()-slider.getMinValue());
            Vector2 p=slider.localToStageCoordinates(new Vector2(x,slider.getHeight()*.5f));
            InputEvent ev=new InputEvent();ev.setType(type);ev.setStage(stage);ev.setStageX(p.x);ev.setStageY(p.y);ev.setPointer(0);ev.setButton(Input.Buttons.LEFT);slider.fire(ev);
        }
        void begin(float v){event(input,InputEvent.Type.touchDown,v);}
        void drag(float v){event(input,InputEvent.Type.touchDragged,v);}
        void end(float v){event(input,InputEvent.Type.touchUp,v);}
        SurfaceLightingComponent surface(){return world.getMapper(SurfaceLightingComponent.class).get(entity);}
        void value(float t){Assert.assertEquals(t,surface().shadowProfile(42,"idle").thickness,.00001f);}
        @Override public void close(){stage.setKeyboardFocus(null);stage.dispose();identities.bind(null,null);world.dispose();}
    }
}
