package games.pixscape.studio.ui.property.entityproperties;

import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisLabel;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.spatial.ActorVolumeData;
import games.pixscape.studio.history.commands.EditActorVolumeCommand;
import games.pixscape.studio.ui.widget.FloatField;
import games.pixscape.studio.ui.config.CommonLayout;
import java.util.Objects;

/** Fields edit the selected representation only; no projection or collision state is authored here. */
final class ActorVolumePanel extends VisTable {
    private final EntityPropertiesContext ctx;
    private final FloatField[] fields=new FloatField[7];
    private final float[] displayed=new float[7];
    private int entity=-1,asset;
    private String clip;
    ActorVolumePanel(EntityPropertiesContext ctx) {
        this.ctx=ctx;defaults().left().pad(5);
        add(new VisLabel("Volume (world pixels)")).colspan(2).row();
        String[] names={"Width:","Depth:","Volume height:","Offset X:","Offset Y:","Offset Z:","Orientation:"};
        for(int i=0;i<fields.length;i++){
            final int property=i;
            FloatField field=new FloatField(ctx.world,e->read(volume(e),property),e->volume(e)!=null).setDisplayDecimals(2);
            field.setName("actor-volume-"+i);
            field.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener(){
                @Override public boolean keyDown(com.badlogic.gdx.scenes.scene2d.InputEvent event,int key){
                    if(key!=com.badlogic.gdx.Input.Keys.ESCAPE)return false;
                    field.refreshFromModel();event.stop();return true;
                }
            });
            field.setApplier((e,value)->{
                ActorVolumeData current=volume(e);
                if(current==null || !Float.isFinite(value) || property<3 && value<=0)return;
                ActorVolumeData next=current.copy();write(next,property,value);
                ctx.history.execute(new EditActorVolumeCommand(ctx.world,ctx.history.historyIds(),e,asset,clip,next,ctx.markCurrentSceneSaveRequired));
            });
            fields[i]=field;
            add(new VisLabel(names[i])).width(CommonLayout.LABEL_WIDTH);add(field).width(CommonLayout.FIELD_WIDTH).row();
        }
    }
    private ActorVolumeData volume(int e){
        SpatialHeightComponent s=e<0?null:ctx.mSpatialHeight.getSafe(e,null);
        return s==null || s.volumes==null?null:s.volumes.get(asset,clip);
    }
    void refresh(int e){
        entity=e;
        AssetRefComponent a=e<0?null:ctx.mSpriteSource.getSafe(e,null);
        AnimationComponent c=e<0?null:ctx.mAnim.getSafe(e,null);
        asset=a==null?0:a.assetId;clip=c==null?null:c.currentClip;
        for(int i=0;i<fields.length;i++){fields[i].setEntityId(e);displayed[i]=read(volume(e),i);}
    }
    @Override public void act(float delta){
        super.act(delta);
        if(entity<0 || !ctx.world.getEntityManager().isActive(entity))return;
        AssetRefComponent a=ctx.mSpriteSource.getSafe(entity,null);
        AnimationComponent c=ctx.mAnim.getSafe(entity,null);
        if(asset!=(a==null?0:a.assetId) || !Objects.equals(clip,c==null?null:c.currentClip))refresh(entity);
        // Model changes during a canvas gesture are reflected without rewriting a focused field.
        ActorVolumeData v=volume(entity);
        for(int i=0;i<fields.length;i++)if(!fields[i].hasKeyboardFocus() && displayed[i]!=read(v,i)){
            fields[i].refreshFromModel();displayed[i]=read(v,i);
        }
    }
    static float read(ActorVolumeData v,int p){if(v==null)return 0;return switch(p){
        case 0->v.width;case 1->v.depth;case 2->v.height;case 3->v.offsetX;case 4->v.offsetY;case 5->v.offsetZ;default->v.orientation;};}
    static void write(ActorVolumeData v,int p,float n){switch(p){
        case 0->v.width=n;case 1->v.depth=n;case 2->v.height=n;case 3->v.offsetX=n;case 4->v.offsetY=n;case 5->v.offsetZ=n;default->v.orientation=ActorVolumeData.normalize(n);}}
}
