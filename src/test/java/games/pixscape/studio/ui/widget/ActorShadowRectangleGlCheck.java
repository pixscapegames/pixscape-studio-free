package games.pixscape.studio.ui.widget;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.runtime.service.TextureRegistry;
import games.pixscape.runtime.spatial.ActorSpatialRectangleData;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.history.commands.ToggleSpatialActorCommand;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.scene.SceneEditorContext;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.*;

/** Explicit opt-in standalone GL check. Creates only an isolated fixture and never opens user scenes. */
public final class ActorShadowRectangleGlCheck {
    public static void main(String[] args) throws Exception {
        FileHandle root=new FileHandle(args[0]);root.mkdirs();
        System.setProperty("user.home",root.child("home").path());
        ProjectConfig cfg=new ProjectConfig();cfg.projectDirectoryPath=root.path();cfg.projectFileName="fixture.json";
        cfg.createSceneMeta("Volume fixture");cfg.getCurrentSceneMeta().physicsEnabled=true;
        ProjectConfig.setInstance(cfg);
        FileHandle image=root.child("orig/images/barrel.png");new FileHandle(args[1]).copyTo(image);
        AssetMetaDatabase db=new AssetMetaDatabase();
        var imageMeta=db.registerIfAbsent(AssetType.IMAGE,"images/barrel","orig/images/barrel.png",AssetMeta.AssetScope.USER);
        db.save(root.child("assets.json"));
        Throwable[] failed={null};
        Lwjgl3ApplicationConfiguration gl=new Lwjgl3ApplicationConfiguration();
        gl.setTitle("Actor spatial rectangle isolated UI check");gl.setWindowedMode(1200,850);gl.setInitialVisible(false);
        gl.useVsync(true);gl.setForegroundFPS(30);gl.disableAudio(true);
        gl.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30,3,2);
        new Lwjgl3Application(new ApplicationAdapter(){
            StudioApplicationAdapter studio;SceneEditorContext ctx;ActorSpatialRectangleCanvasEditor overlay;
            Texture texture;int e,frame;float[] angles={0,45,90,37.25f};
            long start=System.nanoTime();
            @Override public void create(){
                try{
                    studio=new StudioApplicationAdapter();studio.create();
                    var canvas=studio.getCanvas();
                    ctx=canvas.getAttachedSceneContext();
                    if(ctx==null){ctx=canvas.createSceneContext("scene1",cfg.getCurrentSceneMeta());canvas.attach(ctx);}
                    studio.getEditorDocumentManager().openScene("scene1","Volume fixture",ctx);
                    ctx.layerService().addLayerTop("Actors");
                    var world=ctx.world();world.process();
                    var layer=world.getMapper(LayerComponent.class).get(ctx.layerService().getLayerEntity(0));layer.spatialEnabled=true;
                    texture=new Texture(image);
                    e=world.create();
                    new GenericEntityInitializer(world).configureStandaloneSprite(imageMeta.id(),"scene1",texture.getWidth(),texture.getHeight(),
                            0,0,texture.getWidth()/2f,0,ShaderRegistry.indexOf(canvas.getDefaultShaderName()),
                            games.pixscape.runtime.render.BlendMode.ALPHA.id,TextureRegistry.handleOf(texture),"Volume witness",0).init(e);
                    ctx.identityRegistry().ensureStableId(e);world.process();
                    PhysicsShapeData foot=new PhysicsShapeData();foot.geometry=new PhysicsGeometryData();
                    foot.geometry.shapeType=PhysicsGeometryData.SHAPE_CIRCLE;foot.geometry.radius=.2f;foot.spatialFootprint=true;
                    ctx.historyManager().execute(new ToggleSpatialActorCommand(world,ctx.historyManager().historyIds(),
                            canvas.getPhysicsService(),e,true,true,foot));
                    volume().width=40;volume().height=75;
                    volume().offsetX=55;volume().offsetY=55;
                    canvas.focusCameraAt(70,35);
                    ((OrthographicCamera)canvas.getGridStage().getCamera()).zoom=.55f;
                    ctx.selectionService().selectOnly(e);world.process();
                    overlay=(ActorSpatialRectangleCanvasEditor)studio.getUiStage().getRoot().findActor("actor-spatial-rectangle-editor");
                    assertNotNull(overlay);
                    studio.resize(1200,850);
                }catch(Throwable failure){stop(failure);}
            }
            ActorSpatialRectangleData volume(){return ctx.world().getMapper(SpatialHeightComponent.class).get(e).rectangles.staticRectangle;}
            @Override public void render(){
                if(failed[0]!=null)return;
                try{
                    studio.render();
                    if(System.nanoTime()-start>25_000_000_000L)throw new AssertionError("UI deadline");
                    frame++;
                    if(frame==15)assertTrue("Volume handles must be available in the normal Studio canvas",positions());
                    for(int i=0;i<angles.length;i++)if(frame==20+i*6)volume().orientation=angles[i];
                    if(frame==48){
                        float x=ctx.world().getMapper(TransformComponent.class).get(e).x;
                        float rotation=ctx.world().getMapper(TransformComponent.class).get(e).rotationRad;
                        int cursor=ctx.historyManager().getCursor();
                        volume().orientation=359;positions();
                        Vector2 handle=handle(0),target=arc(1);
                        press(handle);drag(target);release(target);
                        assertEquals("One history entry per rotation",cursor+1,ctx.historyManager().getCursor());
                        assertEquals(1,volume().orientation,1.0f);
                        ctx.historyManager().undo();assertEquals(359,volume().orientation,.001);
                        ctx.historyManager().redo();assertEquals(1,volume().orientation,.001);
                        positions();float before=volume().orientation;handle=handle(0);target=arc(90);
                        press(handle);drag(target);studio.getUiStage().keyDown(Input.Keys.ESCAPE);
                        assertEquals(before,volume().orientation,.001);assertEquals(cursor+1,ctx.historyManager().getCursor());
                        assertEquals(x,ctx.world().getMapper(TransformComponent.class).get(e).x,0);
                        assertEquals(rotation,ctx.world().getMapper(TransformComponent.class).get(e).rotationRad,0);
                    }
                    if(frame==52){
                        ((OrthographicCamera)studio.getCanvas().getGridStage().getCamera()).zoom=1.2f;
                        studio.getCanvas().focusCameraAt(20,40);volume().orientation=45;
                    }
                    if(frame==49){
                        volume().orientation=0;
                        for(int index=1;index<=3;index++){
                            positions();ActorSpatialRectangleData before=volume().copy();
                            Vector2 h=handle(index),target=new Vector2(h).add(index==3?-12:12,index==4?18:9);
                            int cursor=ctx.historyManager().getCursor();
                            press(h);drag(target);release(target);
                            assertFalse("Volume handle "+index+" edits its parameter",before.same(volume()));
                            assertEquals(cursor+1,ctx.historyManager().getCursor());
                            ctx.historyManager().undo();assertTrue(before.same(volume()));
                        }
                    }
                    if(frame==60){root.child("DONE").writeString("PASS",false);Gdx.app.exit();}
                }catch(Throwable failure){stop(failure);}
            }
            boolean positions() throws Exception{Method m=ActorSpatialRectangleCanvasEditor.class.getDeclaredMethod("positions");m.setAccessible(true);return (Boolean)m.invoke(overlay);}
            Vector2 handle(int i)throws Exception{Field f=ActorSpatialRectangleCanvasEditor.class.getDeclaredField("handles");f.setAccessible(true);return new Vector2(((Vector2[])f.get(overlay))[i]);}
            Vector2 arc(float angle)throws Exception{Method m=ActorSpatialRectangleCanvasEditor.class.getDeclaredMethod("arcPoint",float.class,Vector2.class);m.setAccessible(true);Vector2 out=new Vector2();m.invoke(overlay,angle,out);return out;}
            Vector2 screen(Vector2 point){return studio.getUiStage().stageToScreenCoordinates(new Vector2(point));}
            void press(Vector2 p){Vector2 s=screen(p);assertTrue(studio.getUiStage().touchDown(Math.round(s.x),Math.round(s.y),0,Input.Buttons.LEFT));assertTrue("Native Stage picked rotation handle",overlay.isDragging());}
            void drag(Vector2 p){Vector2 s=screen(p);studio.getUiStage().touchDragged(Math.round(s.x),Math.round(s.y),0);}
            void release(Vector2 p){Vector2 s=screen(p);studio.getUiStage().touchUp(Math.round(s.x),Math.round(s.y),0,Input.Buttons.LEFT);}
            void stop(Throwable error){failed[0]=error;error.printStackTrace();Gdx.app.exit();}
            @Override public void resize(int w,int h){if(studio!=null)studio.resize(w,h);}
            @Override public void dispose(){if(studio!=null)studio.dispose();}
        },gl);
        if(failed[0]!=null)throw new AssertionError(failed[0]);
    }
}
