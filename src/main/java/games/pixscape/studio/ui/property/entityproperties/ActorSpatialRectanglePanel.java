package games.pixscape.studio.ui.property.entityproperties;

import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisLabel;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.spatial.ActorSpatialRectangleData;
import games.pixscape.studio.history.commands.EditActorSpatialRectangleCommand;
import games.pixscape.studio.ui.widget.FloatField;
import games.pixscape.studio.ui.config.CommonLayout;
import java.util.Objects;

/** Rectangle controls share their parent property table columns; no nested margins or layout. */
final class ActorSpatialRectanglePanel {
    private final EntityPropertiesContext ctx;
    private final FloatField[] fields=new FloatField[6];
    private final float[] displayed=new float[6];
    private int entity=-1,asset;
    private String clip;
    ActorSpatialRectanglePanel(EntityPropertiesContext ctx, VisTable table) {
        this.ctx=ctx;
        table.add(new VisLabel("Spatial rectangle (world pixels)")).colspan(2).row();
        String[] names={"Width:","Height:","Offset X:","Offset Y:","Offset Z:","Orientation:"};
        for(int i=0;i<fields.length;i++){
            final int property=i;
            FloatField field=new FloatField(ctx.world,e->read(rectangle(e),property),e->rectangle(e)!=null).setDisplayDecimals(2);
            field.setName("actor-spatial-rectangle-"+i);
            field.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener(){
                @Override public boolean keyDown(com.badlogic.gdx.scenes.scene2d.InputEvent event,int key){
                    if(key!=com.badlogic.gdx.Input.Keys.ESCAPE)return false;
                    field.refreshFromModel();event.stop();return true;
                }
            });
            field.setApplier((e,value)->{
                ActorSpatialRectangleData current=rectangle(e);
                if(current==null || !Float.isFinite(value) || property<2 && value<=0)return;
                ActorSpatialRectangleData next=current.copy();write(next,property,value);
                ctx.history.execute(new EditActorSpatialRectangleCommand(ctx.world,ctx.history.historyIds(),e,asset,clip,next,ctx.markCurrentSceneSaveRequired));
            });
            fields[i]=field;
            table.add(new VisLabel(names[i])).width(CommonLayout.LABEL_WIDTH);
            table.add(field).width(CommonLayout.FIELD_WIDTH).row();
        }
    }
    private ActorSpatialRectangleData rectangle(int e){
        SpatialHeightComponent s=e<0?null:ctx.mSpatialHeight.getSafe(e,null);
        return s==null || s.rectangles==null?null:s.rectangles.get(asset,clip);
    }
    void refresh(int e){
        entity=e;
        AssetRefComponent a=e<0?null:ctx.mSpriteSource.getSafe(e,null);
        AnimationComponent c=e<0?null:ctx.mAnim.getSafe(e,null);
        asset=a==null?0:a.assetId;clip=c==null?null:c.currentClip;
        for(int i=0;i<fields.length;i++){fields[i].setEntityId(e);displayed[i]=read(rectangle(e),i);}
    }
    void refreshCurrentRepresentation(){
        if(entity<0 || !ctx.world.getEntityManager().isActive(entity))return;
        AssetRefComponent a=ctx.mSpriteSource.getSafe(entity,null);
        AnimationComponent c=ctx.mAnim.getSafe(entity,null);
        if(asset!=(a==null?0:a.assetId) || !Objects.equals(clip,c==null?null:c.currentClip))refresh(entity);
        // Model changes during a canvas gesture are reflected without rewriting a focused field.
        ActorSpatialRectangleData v=rectangle(entity);
        for(int i=0;i<fields.length;i++)if(!fields[i].hasKeyboardFocus() && displayed[i]!=read(v,i)){
            fields[i].refreshFromModel();displayed[i]=read(v,i);
        }
    }
    static float read(ActorSpatialRectangleData v,int p){if(v==null)return 0;return switch(p){
        case 0->v.width;case 1->v.height;case 2->v.offsetX;case 3->v.offsetY;case 4->v.offsetZ;default->v.orientation;};}
    static void write(ActorSpatialRectangleData v,int p,float n){switch(p){
        case 0->v.width=n;case 1->v.height=n;case 2->v.offsetX=n;case 3->v.offsetY=n;case 4->v.offsetZ=n;default->v.orientation=ActorSpatialRectangleData.normalize(n);}}
}
