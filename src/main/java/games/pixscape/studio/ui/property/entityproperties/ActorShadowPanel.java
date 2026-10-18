package games.pixscape.studio.ui.property.entityproperties;

import com.badlogic.gdx.scenes.scene2d.*;
import com.kotcrab.vis.ui.widget.*;
import games.pixscape.studio.ui.widget.SimpleFloatSlider;
import games.pixscape.studio.ui.config.CommonLayout;
import java.util.Locale;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.render.lighting.ActorShadowProfile;
import games.pixscape.studio.history.commands.EditActorShadowCommand;
import games.pixscape.studio.service.ActorShadowEditingSupport;
import java.util.Objects;

/** Actor caster controls; the parent Cast shadow block owns visibility and indentation. */
final class ActorShadowPanel extends VisTable {
    private final EntityPropertiesContext ctx;
    private final ActorShadowProfile fallback=new ActorShadowProfile();
    private final SimpleFloatSlider thickness=new SimpleFloatSlider(0,.5f,.01f);
    private final VisLabel valueLabel=new VisLabel();
    private ActorShadowProfile beforePreview,preview;
    private boolean previewing,cancelled;
    private int entity=-1,assetId;
    private String clip;

    ActorShadowPanel(EntityPropertiesContext ctx){
        this.ctx=ctx;defaults().left().pad(5);
        thickness.setName("actor-shadow-thickness");
        thickness.bind(()->{ActorShadowProfile p=storedProfile();return p==null?0:p.thickness;},this::changeThickness);
        // Keep native Slider touch focus and release semantics, including cancellation.
        thickness.addCaptureListener(new InputListener(){
            @Override public boolean touchDown(InputEvent event,float x,float y,int pointer,int button){return !thickness.isDisabled();}
            @Override public void touchUp(InputEvent event,float x,float y,int pointer,int button){cancelled=event.isTouchFocusCancel();}
        });
        Tooltip tooltip=new Tooltip.Builder("Drag both foot handles on the selected actor in the canvas.\n"
                +"Esc cancels the foot gesture. Thickness is relative to the full sprite width.\n"
                +"Settings belong to the active clip. A missing profile starts at the receiver anchor.")
                .target(thickness).build();tooltip.setAppearDelayTime(0f);
        add(new VisLabel("Thickness:")).width(CommonLayout.LABEL_WIDTH);
        add(thickness).width(CommonLayout.FIELD_WIDTH);add(valueLabel).row();
    }
    private SurfaceLightingComponent surface(){return entity<0 || !ctx.world.getEntityManager().isActive(entity)?null:ctx.world.getMapper(SurfaceLightingComponent.class).getSafe(entity,null);}
    private ActorShadowProfile storedProfile(){SurfaceLightingComponent s=surface();return s==null?null:s.shadowProfile(assetId,clip);}
    private boolean applicable(){SurfaceLightingComponent s=surface();return assetId>0 && s!=null && s.shadowCaster && s.description==null;}
    private void changeThickness(float value){
        if(!applicable())return;
        if(cancelled){cancelled=false;cancelPreview();updateValue();return;}
        if(thickness.isDragging()){
            if(!previewing){ActorShadowProfile p=storedProfile();beforePreview=p==null?null:p.copy();
                preview=ActorShadowEditingSupport.resolve(surface(),assetId,clip,fallback).copy();previewing=true;}
            preview.thickness=value;EditActorShadowCommand.preview(ctx.world,entity,assetId,clip,preview);
        }else{
            cancelPreview();ActorShadowProfile p=storedProfile();
            if(p==null && value==0 || p!=null && p.thickness==value)return;
            ActorShadowProfile edited=ActorShadowEditingSupport.resolve(surface(),assetId,clip,fallback).copy();edited.thickness=value;
            ctx.history.execute(new EditActorShadowCommand(ctx.world,ctx.history.historyIds(),entity,assetId,clip,edited,ctx.markCurrentSceneSaveRequired));
        }
        updateValue();
    }
    private void cancelPreview(){
        if(!previewing)return;
        EditActorShadowCommand.preview(ctx.world,entity,assetId,clip,beforePreview);
        beforePreview=preview=null;previewing=false;
    }
    void setEntityId(int e){if(e!=entity){cancelPreview();entity=e;}if(!previewing)refresh();}
    void cancelThicknessPreview(){cancelPreview();thickness.refresh();updateValue();}
    @Override public void act(float delta){
        super.act(delta);if(entity<0 || !ctx.world.getEntityManager().isActive(entity))return;
        AssetRefComponent ref=ctx.mSpriteSource.getSafe(entity,null);AnimationComponent animation=ctx.mAnim.getSafe(entity,null);
        if(assetId!=(ref==null?0:ref.assetId) || !Objects.equals(clip,animation==null?null:animation.currentClip)){
            cancelPreview();refresh();
        }
    }
    private void updateValue(){ActorShadowProfile p=storedProfile();valueLabel.setText(String.format(Locale.ROOT,"%.3f",p==null?0:p.thickness));}
    private void refresh(){
        AssetRefComponent ref=entity<0?null:ctx.mSpriteSource.getSafe(entity,null);
        AnimationComponent animation=entity<0?null:ctx.mAnim.getSafe(entity,null);
        assetId=ref==null?0:ref.assetId;clip=animation==null?null:animation.currentClip;
        thickness.setDisabled(!applicable());thickness.refresh();updateValue();
    }
}
