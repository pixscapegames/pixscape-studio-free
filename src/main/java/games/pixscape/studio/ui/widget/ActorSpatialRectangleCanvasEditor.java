package games.pixscape.studio.ui.widget;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
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
import games.pixscape.studio.helper.RotationArc;
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
    private final RotationArc arc=new RotationArc();
    private final RotationArc.Gesture angleGesture=new RotationArc.Gesture();
    private final Vector2 scratch=new Vector2(),pivot=new Vector2();
    private final Vector2[] handles={new Vector2(),new Vector2(),new Vector2(),new Vector2()};
    private final float[] corners=new float[12],projected=new float[8];
    private final float[] worldAnchor=new float[3];
    private final RotationArc.Projection projection=this::arcPoint;
    private ShapeDrawer drawer;
    private SceneEditorContext gestureContext;
    private ActorSpatialRectangleData before,candidate,current;
    private int entity=-1,asset,handle=-1,pointer=-1;
    private String clip;
    private float anchorX,anchorY,anchorZ,radius,pressX,pressY,factor;
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
                angleGesture.begin(pointerAngle(x,y),factor<1);
                spatial(gestureContext,entity).rectangles.put(asset,clip,candidate);
                event.stop();return true;
            }
            @Override public void touchDragged(InputEvent event,float x,float y,int p){
                if(handle<0 || p!=pointer)return;
                if(!sameTarget() || !positions()){cancel();return;}
                if(handle==0)candidate.orientation=ActorSpatialRectangleData.normalize(before.orientation+angleGesture.update(pointerAngle(x,y)));
                else{
                    grid(x,y,scratch);float dx=(scratch.x-pressX)*factor,dy=(scratch.y-pressY)*factor;
                    float wx=LightingCoordinates.worldX(dx,dy,0),wy=LightingCoordinates.worldY(dx,dy,0);
                    float c=MathUtils.cosDeg(before.orientation),s=MathUtils.sinDeg(before.orientation);
                    if(handle==1){candidate.offsetX=before.offsetX+wx;candidate.offsetY=before.offsetY+wy;}
                    if(handle==2)candidate.width=Math.max(.01f,before.width+2*(c*wx+s*wy));
                    if(handle==3)candidate.height=Math.max(.01f,before.height+dy);
                }
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
        current.corners(anchorX,anchorY,anchorZ,corners);
        for(int i=0;i<4;i++){project(corners[3*i],corners[3*i+1],corners[3*i+2],scratch);projected[2*i]=scratch.x;projected[2*i+1]=scratch.y;}
        project(anchorX+current.offsetX,anchorY+current.offsetY,anchorZ+current.offsetZ,pivot);
        handles[1].set(pivot);
        localPoint(current.width*.5f,0,0,handles[2]);
        localPoint(0,0,current.height,handles[3]);
        // Keep the rotation control outside the base even for tiny rectangles, in logical pixels.
        grid(pivot.x,pivot.y,scratch);float baseX=scratch.x;
        grid(pivot.x+32,pivot.y,scratch);float minimum=Math.abs(scratch.x-baseX);
        radius=Math.max(minimum,current.width*.65f);
        arcPoint(current.orientation,handles[0]);return true;
    }
    private void localPoint(float x,float y,float z,Vector2 out){
        float c=MathUtils.cosDeg(current.orientation),s=MathUtils.sinDeg(current.orientation);
        project(anchorX+current.offsetX+c*x-s*y,anchorY+current.offsetY+s*x+c*y,anchorZ+current.offsetZ+z,out);
    }
    private void arcPoint(float degrees,Vector2 out){
        project(anchorX+current.offsetX+MathUtils.cosDeg(degrees)*radius,
                anchorY+current.offsetY+MathUtils.sinDeg(degrees)*radius,anchorZ+current.offsetZ,out);
    }
    private void project(float x,float y,float z,Vector2 out){
        out.set(LightingCoordinates.screenX(x,y),LightingCoordinates.screenY(x,y,z));
        canvas.getGridStage().stageToScreenCoordinates(out);getStage().screenToStageCoordinates(out);
    }
    private void grid(float x,float y,Vector2 out){
        out.set(x,y);getStage().stageToScreenCoordinates(out);canvas.getGridStage().screenToStageCoordinates(out);
    }
    private float pointerAngle(float x,float y){
        grid(pivot.x,pivot.y,scratch);float px=scratch.x,py=scratch.y;
        grid(x,y,scratch);float dx=scratch.x-px,dy=scratch.y-py;
        return MathUtils.atan2(LightingCoordinates.worldY(dx,dy,0),LightingCoordinates.worldX(dx,dy,0));
    }
    private int pick(float x,float y){
        int result=-1;float best=81;
        for(int i=0;i<handles.length;i++){float distance=handles[i].dst2(x,y);if(distance<best && RotationArc.hit(x,y,handles[i].x,handles[i].y,9)){best=distance;result=i;}}
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
            arc.draw(drawer,projection,0,360,64,1);
            scratch.set(Gdx.input.getX(),Gdx.input.getY());getStage().screenToStageCoordinates(scratch);
            int hover=pick(scratch.x,scratch.y);
            for(int i=0;i<handles.length;i++){
                drawer.setColor(handle==i?EditorOverlayPalette.WALL_SELECTED_COLOR:
                        hover==i?EditorOverlayPalette.WALL_HOVER_COLOR:EditorOverlayPalette.SPATIAL_NEUTRAL_COLOR);
                if(i==0)RotationArc.handle(drawer,pivot.x,pivot.y,handles[i].x,handles[i].y,1);
                else drawer.rectangle(handles[i].x-4,handles[i].y-4,8,8,1.5f);
            }
        }finally{batch.setPackedColor(color);batch.flush();clipEnd();}
    }
    private void edge(int a,int b){drawer.line(projected[2*a],projected[2*a+1],projected[2*b],projected[2*b+1],1.5f);}
}
