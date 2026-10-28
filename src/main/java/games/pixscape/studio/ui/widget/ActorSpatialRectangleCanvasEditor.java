package games.pixscape.studio.ui.widget;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent;
import games.pixscape.runtime.render.lighting.LightingCoordinates;
import games.pixscape.runtime.service.TextureRegistry;
import games.pixscape.runtime.spatial.ActorSpatialRectangleData;
import games.pixscape.runtime.system.GameObjectHierarchySystem;
import games.pixscape.studio.history.commands.EditActorSpatialRectangleCommand;
import games.pixscape.studio.scene.SceneEditorContext;
import games.pixscape.studio.ui.config.EditorOverlayPalette;
import games.pixscape.studio.ui.main.WorldCanvas;
import space.earlygrey.shapedrawer.ShapeDrawer;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Native Stage gesture ownership. The overlay never modifies the actor transform or physics. */
public final class ActorSpatialRectangleCanvasEditor extends Actor {
    private final WorldCanvas canvas;
    private final BooleanSupplier available;
    private final Vector2 scratch=new Vector2(),pivot=new Vector2();
    private final Vector2[] handles={new Vector2(),new Vector2(),new Vector2(),new Vector2()};
    private final float[] projected=new float[8];
    private final float[] worldAnchor=new float[3];
    private ShapeDrawer drawer;
    private SceneEditorContext gestureContext;
    private ActorSpatialRectangleData before,candidate,current;
    private int entity=-1,asset,handle=-1,pointer=-1;
    private String clip;
    private float anchorX,anchorY,anchorZ,pressX,pressY,factor;
    private Stage keyboardStage;
    private Actor previousFocus;

    public ActorSpatialRectangleCanvasEditor(WorldCanvas canvas,BooleanSupplier available){
        this.canvas=canvas;this.available=available;setName("actor-spatial-rectangle-editor");
        addListener(new InputListener(){
            @Override public boolean touchDown(InputEvent event,float x,float y,int p,int button){
                if(button!=Input.Buttons.LEFT || handle>=0 || !positions())return false;
                int picked=pick(x,y);if(picked<0 || !inside(x,y))return false;
                keyboardStage=getStage();previousFocus=keyboardStage.getKeyboardFocus();
                keyboardStage.setKeyboardFocus(ActorSpatialRectangleCanvasEditor.this);
                if(!positions()){restoreFocus();return false;}
                gestureContext=canvas.getAttachedSceneContext();entity=selected(gestureContext);
                asset=asset(gestureContext,entity);clip=clip(gestureContext,entity);
                before=current.copy();candidate=current.copy();handle=picked;pointer=p;
                factor=Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)||Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)?.1f:1f;
                grid(x,y,scratch);pressX=scratch.x;pressY=scratch.y;
                spatial(gestureContext,entity).rectangles.put(asset,clip,candidate);
                event.stop();return true;
            }
            @Override public void touchDragged(InputEvent event,float x,float y,int p){
                if(handle<0 || p!=pointer)return;
                if(!sameTarget() || !positions()){cancel();return;}
                grid(x,y,scratch);float dx=(scratch.x-pressX)*factor,dy=(scratch.y-pressY)*factor;
                if(handle==0){candidate.pivotX=before.pivotX+dx;candidate.pivotY=before.pivotY+dy;}
                if(handle==1){candidate.x=before.x+dx;candidate.y=before.y+dy;}
                if(handle==2)candidate.width=Math.max(.01f,before.width+dx);
                if(handle==3)candidate.height=Math.max(.01f,before.height+dy);
                event.stop();
            }
            @Override public void touchUp(InputEvent event,float x,float y,int p,int button){
                if(handle>=0 && p==pointer){finish(event.isTouchFocusCancel());event.stop();}
            }
            @Override public boolean keyDown(InputEvent event,int key){
                if(key==Input.Keys.ESCAPE && handle>=0){cancel();event.stop();return true;}return false;
            }
        });
    }
    private static int selected(SceneEditorContext c){return c!=null && c.selectionService().getSelectionSet().size==1?c.selectionService().getFirstSelectedEntityId():-1;}
    private static int asset(SceneEditorContext c,int e){AssetRefComponent a=c.world().getMapper(AssetRefComponent.class).getSafe(e,null);return a==null?0:a.assetId;}
    private static String clip(SceneEditorContext c,int e){AnimationComponent a=c.world().getMapper(AnimationComponent.class).getSafe(e,null);return a==null?null:a.currentClip;}
    private static SpatialHeightComponent spatial(SceneEditorContext c,int e){
        return c==null || c.isDisposed() || e<0 || !c.world().getEntityManager().isActive(e)?null:c.world().getMapper(SpatialHeightComponent.class).getSafe(e,null);
    }
    private boolean positions(){
        if(!available.getAsBoolean() || getStage()==null)return false;
        SceneEditorContext ctx=canvas.getAttachedSceneContext();int e=selected(ctx);
        SpatialHeightComponent h=spatial(ctx,e);
        if(h==null || h.rectangles==null)return false;
        current=h.rectangles.get(asset(ctx,e),clip(ctx,e));if(current==null)return false;
        var d=canvas.getDynamicEntityState();
        if(d==null || e>=d.entityIdToRenderSlot.length)return false;
        int slot=d.entityIdToRenderSlot[e];if(slot<0 || !d.visible[slot] || !d.enabled[slot])return false;
        TransformComponent t=ctx.world().getMapper(TransformComponent.class).getSafe(e,null);if(t==null)return false;
        var hierarchy=ctx.world().getSystem(GameObjectHierarchySystem.class);
        var state=hierarchy==null?null:hierarchy.worldTransforms();
        boolean resolved=state!=null && state.isResolved(e);
        float sx=resolved?state.x[e]:t.x,sy=resolved?state.y[e]:t.y,angle=resolved?state.rotationRad[e]:t.rotationRad;
        var foot=ctx.world().getMapper(SpatialPhysicsFootprintComponent.class).getSafe(e,null);
        if(foot==null || !foot.valid)return false;
        games.pixscape.runtime.spatial.ActorSpatialRectanglePlacement.anchor(sx,sy,angle,foot.localOffsetXPx,foot.localOffsetYPx,h.altitude,worldAnchor);
        anchorX=worldAnchor[0];anchorY=worldAnchor[1];anchorZ=worldAnchor[2];
        for(int i=0;i<4;i++){
            localPoint(current.x+(i>=2?current.width:0),current.y+(i==1||i==2?current.height:0),scratch);
            projected[2*i]=scratch.x;projected[2*i+1]=scratch.y;
        }
        localPoint(current.pivotX,current.pivotY,pivot);handles[0].set(pivot);
        localPoint(current.x+current.width*.5f,current.y+current.height*.5f,handles[1]);
        localPoint(current.x+current.width,current.y+current.height*.5f,handles[2]);
        localPoint(current.x+current.width*.5f,current.y+current.height,handles[3]);
        return true;
    }
    private void localPoint(float x,float y,Vector2 out){
        out.set(LightingCoordinates.screenX(anchorX,anchorY)+x,LightingCoordinates.screenY(anchorX,anchorY,anchorZ)+y);
        canvas.getGridStage().stageToScreenCoordinates(out);getStage().screenToStageCoordinates(out);
    }
    private void grid(float x,float y,Vector2 out){
        out.set(x,y);getStage().stageToScreenCoordinates(out);canvas.getGridStage().screenToStageCoordinates(out);
    }
    private int pick(float x,float y){
        int result=-1;float best=81;
        for(int i=0;i<handles.length;i++){float distance=handles[i].dst2(x,y);if(distance<best){best=distance;result=i;}}
        return result;
    }
    private boolean inside(float x,float y){
        scratch.set(x,y);getStage().stageToScreenCoordinates(scratch);var v=canvas.getGridStage().getViewport();
        float top=Gdx.graphics.getHeight()-v.getScreenY()-v.getScreenHeight();
        return scratch.x>=v.getScreenX() && scratch.x<v.getScreenX()+v.getScreenWidth() && scratch.y>=top && scratch.y<top+v.getScreenHeight();
    }
    @Override public Actor hit(float x,float y,boolean touchable){return isVisible() && positions() && inside(x,y) && pick(x,y)>=0?this:null;}
    private boolean sameTarget(){
        var ctx=canvas.getAttachedSceneContext();var h=spatial(ctx,entity);
        return available.getAsBoolean() && ctx==gestureContext && selected(ctx)==entity && h!=null && h.rectangles!=null
                && asset(ctx,entity)==asset && Objects.equals(clip,clip(ctx,entity)) && h.rectangles.get(asset,clip)==candidate;
    }
    @Override public void act(float delta){if(handle>=0 && !sameTarget())cancel();}
    public boolean isDragging(){return handle>=0;}
    public void cancel(){if(handle<0)return;finish(true);if(getStage()!=null)getStage().cancelTouchFocus(this);}
    private void finish(boolean cancelled){
        var h=spatial(gestureContext,entity);
        if(h!=null && h.rectangles!=null && h.rectangles.get(asset,clip)==candidate){
            h.rectangles.put(asset,clip,before);
            if(!cancelled)gestureContext.historyManager().execute(new EditActorSpatialRectangleCommand(gestureContext.world(),
                    gestureContext.historyManager().historyIds(),entity,asset,clip,candidate,gestureContext::markExplicitSaveRequired));
        }
        handle=-1;pointer=-1;gestureContext=null;before=candidate=null;restoreFocus();
    }
    private void restoreFocus(){
        if(keyboardStage!=null && keyboardStage.getKeyboardFocus()==this)
            keyboardStage.setKeyboardFocus(previousFocus!=null && previousFocus.getStage()==keyboardStage?previousFocus:null);
        keyboardStage=null;previousFocus=null;
    }
    @Override protected void setStage(Stage stage){if(stage!=getStage())cancel();super.setStage(stage);}
    @Override public void draw(Batch batch,float parentAlpha){
        if(!positions())return;
        Texture white=TextureRegistry.getByHandle(TextureRegistry.WHITE_HANDLE);if(white==null)return;
        if(drawer==null)drawer=new ShapeDrawer(batch,new TextureRegion(white,0,0,1,1));
        var v=canvas.getGridStage().getViewport();
        scratch.set(v.getScreenX(),Gdx.graphics.getHeight()-v.getScreenY());getStage().screenToStageCoordinates(scratch);
        float x=scratch.x,y=scratch.y;
        scratch.set(v.getScreenX()+v.getScreenWidth(),Gdx.graphics.getHeight()-v.getScreenY()-v.getScreenHeight());getStage().screenToStageCoordinates(scratch);
        batch.flush();if(!clipBegin(x,y,scratch.x-x,scratch.y-y))return;
        float color=batch.getPackedColor();
        try{
            drawer.setColor(EditorOverlayPalette.SPATIAL_NEUTRAL_COLOR);
            for(int i=0;i<4;i++){
                edge(i,(i+1)%4);
            }
            scratch.set(Gdx.input.getX(),Gdx.input.getY());getStage().screenToStageCoordinates(scratch);
            int hover=pick(scratch.x,scratch.y);
            for(int i=0;i<handles.length;i++){
                drawer.setColor(handle==i?EditorOverlayPalette.WALL_SELECTED_COLOR:
                        hover==i?EditorOverlayPalette.WALL_HOVER_COLOR:EditorOverlayPalette.SPATIAL_NEUTRAL_COLOR);
                if(i==0){
                    drawer.circle(pivot.x,pivot.y,5,1.5f);
                    drawer.line(pivot.x-8,pivot.y,pivot.x+8,pivot.y,1.5f);
                    drawer.line(pivot.x,pivot.y-8,pivot.x,pivot.y+8,1.5f);
                }
                else drawer.rectangle(handles[i].x-4,handles[i].y-4,8,8,1.5f);
            }
        }finally{batch.setPackedColor(color);batch.flush();clipEnd();}
    }
    private void edge(int a,int b){drawer.line(projected[2*a],projected[2*a+1],projected[2*b],projected[2*b+1],1.5f);}
}
