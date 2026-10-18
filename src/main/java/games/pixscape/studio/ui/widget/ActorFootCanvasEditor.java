package games.pixscape.studio.ui.widget;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.*;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.lighting.*;
import games.pixscape.runtime.service.TextureRegistry;
import games.pixscape.studio.history.commands.EditActorShadowCommand;
import games.pixscape.studio.scene.SceneEditorContext;
import games.pixscape.studio.service.ActorShadowEditingSupport;
import games.pixscape.studio.ui.main.WorldCanvas;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Canvas overlay in logical UI pixels. Native Stage touch focus exclusively owns each gesture. */
public final class ActorFootCanvasEditor extends Actor {
    private final WorldCanvas canvas;
    private final BooleanSupplier available;
    private final float[] quad=new float[8],uv=new float[2];
    private final Vector2 first=new Vector2(),second=new Vector2(),scratch=new Vector2();
    private SceneEditorContext gestureContext;
    private int entity=-1,assetId,endpoint=-1,pointer=-1;
    private String clip;
    private ActorShadowProfile before,candidate;
    private final ActorShadowProfile fallback=new ActorShadowProfile();
    private Stage keyboardStage;
    private Actor previousKeyboardFocus;

    public ActorFootCanvasEditor(WorldCanvas canvas,BooleanSupplier available) {
        this.canvas=canvas;this.available=available;setName("actor-foot-canvas-editor");
        addListener(new InputListener(){
            @Override public boolean touchDown(InputEvent event,float x,float y,int p,int button) {
                if(button!=Input.Buttons.LEFT || endpoint>=0 || !positions())return false;
                int handle=nearest(x,y);if(handle<0 || !insideCanvas(x,y))return false;
                // Commit any focused property before taking the gesture's snapshot.
                keyboardStage=getStage();previousKeyboardFocus=keyboardStage.getKeyboardFocus();
                keyboardStage.setKeyboardFocus(ActorFootCanvasEditor.this);
                SceneEditorContext ctx=canvas.getAttachedSceneContext();int selected=selected(ctx);
                ActorShadowProfile profile=profile(ctx,selected);if(profile==null){restoreFocus();return false;}
                gestureContext=ctx;entity=selected;assetId=asset(ctx,entity);clip=clip(ctx,entity);
                ActorShadowProfile authored=surface(ctx,selected).shadowProfile(assetId,clip);
                before=authored==null?null:authored.copy();candidate=profile.copy();endpoint=handle;pointer=p;
                EditActorShadowCommand.preview(ctx.world(),entity,assetId,clip,candidate);event.stop();return true;
            }
            @Override public void touchDragged(InputEvent event,float x,float y,int p) {
                if(endpoint<0 || p!=pointer)return;
                if(!sameTarget() || !readQuad(entity)){cancel();return;}
                scratch.set(x,y);getStage().stageToScreenCoordinates(scratch);
                canvas.getGridStage().screenToStageCoordinates(scratch);
                if(!ActorShadowImageMapping.inverse(quad,scratch.x,scratch.y,uv))return;
                DynamicEntityRenderState d=canvas.getDynamicEntityState();int s=d.entityIdToRenderSlot[entity];
                float u=d.u1[s]>d.u2[s]?1-uv[0]:uv[0],v=d.v1[s]>d.v2[s]?1-uv[1]:uv[1];
                float otherU=endpoint==0?candidate.rightU:candidate.leftU,otherV=endpoint==0?candidate.rightV:candidate.leftV;
                if(!ActorShadowProfile.distinct(u,v,otherU,otherV))return;
                if(endpoint==0){candidate.leftU=u;candidate.leftV=v;}else{candidate.rightU=u;candidate.rightV=v;}
                EditActorShadowCommand.preview(gestureContext.world(),entity,assetId,clip,candidate);event.stop();
            }
            @Override public void touchUp(InputEvent event,float x,float y,int p,int button) {
                if(endpoint>=0 && p==pointer){finish(event.isTouchFocusCancel());event.stop();}
            }
            @Override public boolean keyDown(InputEvent event,int key) {
                if(key!=Input.Keys.ESCAPE || endpoint<0)return false;
                cancel();event.stop();return true;
            }
        });
    }
    private static int selected(SceneEditorContext ctx) {
        return ctx!=null && ctx.selectionService().getSelectionSet().size==1?ctx.selectionService().getFirstSelectedEntityId():-1;
    }
    private static int asset(SceneEditorContext ctx,int e) {
        AssetRefComponent ref=ctx.world().getMapper(AssetRefComponent.class).getSafe(e,null);return ref==null?0:ref.assetId;
    }
    private static String clip(SceneEditorContext ctx,int e) {
        AnimationComponent a=ctx.world().getMapper(AnimationComponent.class).getSafe(e,null);return a==null?null:a.currentClip;
    }
    private static SurfaceLightingComponent surface(SceneEditorContext ctx,int e) {
        return ctx==null || e<0 || !ctx.world().getEntityManager().isActive(e)?null:ctx.world().getMapper(SurfaceLightingComponent.class).getSafe(e,null);
    }
    private ActorShadowProfile profile(SceneEditorContext ctx,int e) {
        if(ctx==null || e<0)return null;SurfaceLightingComponent s=surface(ctx,e);
        return ActorShadowEditingSupport.resolve(s,asset(ctx,e),clip(ctx,e),fallback);
    }
    private boolean sameTarget() {
        SceneEditorContext current=canvas.getAttachedSceneContext();
        SurfaceLightingComponent surface=surface(current,entity);
        return available.getAsBoolean() && current==gestureContext && selected(current)==entity
                && surface!=null && surface.shadowCaster
                && asset(current,entity)==assetId && Objects.equals(clip,clip(current,entity));
    }
    private boolean readQuad(int e) {
        DynamicEntityRenderState d=canvas.getDynamicEntityState();
        if(d==null || e<0 || e>=d.entityIdToRenderSlot.length)return false;
        int s=d.entityIdToRenderSlot[e];if(s<0 || !d.enabled[s] || !d.visible[s] || d.light[s]!=0)return false;
        // Final current-frame render corners: hierarchy, origin, scale, rotation, frame and offsets.
        // Studio intentionally renders without parallax; Preview consumes the same authored UVs.
        quad[0]=d.x1[s];quad[1]=d.y1[s];quad[2]=d.x2[s];quad[3]=d.y2[s];
        quad[4]=d.x3[s];quad[5]=d.y3[s];quad[6]=d.x4[s];quad[7]=d.y4[s];return true;
    }
    private boolean positions() {
        if(!available.getAsBoolean() || getStage()==null)return false;
        SceneEditorContext ctx=canvas.getAttachedSceneContext();int e=selected(ctx);ActorShadowProfile p=profile(ctx,e);
        if(p==null || !readQuad(e))return false;
        DynamicEntityRenderState d=canvas.getDynamicEntityState();int s=d.entityIdToRenderSlot[e];
        point(p.leftU,p.leftV,d.u1[s]>d.u2[s],d.v1[s]>d.v2[s],first);
        point(p.rightU,p.rightV,d.u1[s]>d.u2[s],d.v1[s]>d.v2[s],second);return true;
    }
    private void point(float u,float v,boolean flipX,boolean flipY,Vector2 out) {
        if(flipX)u=1-u;if(flipY)v=1-v;
        out.set(ActorShadowImageMapping.x(quad,u,v),ActorShadowImageMapping.y(quad,u,v));
        canvas.getGridStage().stageToScreenCoordinates(out);getStage().screenToStageCoordinates(out);
    }
    private int nearest(float x,float y) {
        float a=first.dst2(x,y),b=second.dst2(x,y);return Math.min(a,b)>144?-1:a<=b?0:1;
    }
    private boolean insideCanvas(float x,float y) {
        scratch.set(x,y);getStage().stageToScreenCoordinates(scratch);
        var viewport=canvas.getGridStage().getViewport();
        float top=Gdx.graphics.getHeight()-viewport.getScreenY()-viewport.getScreenHeight();
        return scratch.x>=viewport.getScreenX() && scratch.x<viewport.getScreenX()+viewport.getScreenWidth()
                && scratch.y>=top && scratch.y<top+viewport.getScreenHeight();
    }
    @Override public Actor hit(float x,float y,boolean touchable) {
        return isVisible() && positions() && insideCanvas(x,y) && nearest(x,y)>=0?this:null;
    }
    @Override public void act(float delta) {
        if(endpoint>=0 && (!sameTarget() || profile(gestureContext,entity)!=candidate))cancel();
    }
    public boolean isDragging(){return endpoint>=0;}
    public void cancel() {
        if(endpoint<0)return;finish(true);if(getStage()!=null)getStage().cancelTouchFocus(this);
    }
    private void finish(boolean cancelled) {
        endpoint=-1;pointer=-1;
        if(gestureContext!=null && !gestureContext.isDisposed() && gestureContext.world().getEntityManager().isActive(entity)) {
            SurfaceLightingComponent s=surface(gestureContext,entity);
            // Do not overwrite a concurrent undo, deletion or profile replacement.
            if(s!=null && s.shadowProfile(assetId,clip)==candidate) {
                EditActorShadowCommand.preview(gestureContext.world(),entity,assetId,clip,before);
                if(!cancelled)gestureContext.historyManager().execute(new EditActorShadowCommand(gestureContext.world(),
                        gestureContext.historyManager().historyIds(),entity,assetId,clip,candidate,gestureContext::markExplicitSaveRequired));
            }
        }
        gestureContext=null;before=candidate=null;restoreFocus();
    }
    private void restoreFocus() {
        if(keyboardStage!=null && keyboardStage.getKeyboardFocus()==this)
            keyboardStage.setKeyboardFocus(previousKeyboardFocus!=null && previousKeyboardFocus.getStage()==keyboardStage?previousKeyboardFocus:null);
        keyboardStage=null;previousKeyboardFocus=null;
    }
    @Override protected void setStage(Stage stage){if(stage!=getStage())cancel();super.setStage(stage);}
    @Override public void draw(Batch batch,float parentAlpha) {
        if(!positions())return;
        Texture white=TextureRegistry.getByHandle(TextureRegistry.WHITE_HANDLE);if(white==null)return;
        var viewport=canvas.getGridStage().getViewport();
        scratch.set(viewport.getScreenX(),Gdx.graphics.getHeight()-viewport.getScreenY());getStage().screenToStageCoordinates(scratch);
        float x=scratch.x,y=scratch.y;
        scratch.set(viewport.getScreenX()+viewport.getScreenWidth(),Gdx.graphics.getHeight()-viewport.getScreenY()-viewport.getScreenHeight());getStage().screenToStageCoordinates(scratch);
        batch.flush();if(!clipBegin(x,y,scratch.x-x,scratch.y-y))return;
        float old=batch.getPackedColor();
        try {
            float dx=second.x-first.x,dy=second.y-first.y;
            batch.setColor(1,.7f,.1f,parentAlpha);
            batch.draw(white,first.x,first.y,0,1,(float)Math.sqrt(dx*dx+dy*dy),2,1,1,MathUtils.atan2Deg(dy,dx),0,0,1,1,false,false);
            batch.setColor(1,.5f,.3f,parentAlpha);batch.draw(white,first.x-5,first.y-5,10,10);batch.draw(white,second.x-5,second.y-5,10,10);
        }finally{batch.setPackedColor(old);batch.flush();clipEnd();}
    }
}
