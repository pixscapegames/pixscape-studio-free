package games.pixscape.studio.ui.property.entityproperties;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.*;
import games.pixscape.studio.history.commands.EditLightingCommand;
import games.pixscape.studio.history.commands.EditLightingCommand.Property;
import games.pixscape.studio.ui.widget.*;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.runtime.component.SurfaceLightingComponent;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import java.util.Locale;

/** Authored controls shared by point/cone and ordinary sprite inspectors. */
public final class LightingPanel extends VisTable {
    private final EntityPropertiesContext ctx;private int entity=-1;private boolean refresh;
    private final Array<FloatField> fields=new Array<>();private final Array<VisCheckBox> checks=new Array<>();
    private final Array<Property> checkProperties=new Array<>();
    private SimpleSelectBox<String> quality;private SimpleSelectBox<Integer> resolution;
    private CollapsibleVisTable casterFields;
    private SimpleFloatSlider threshold;
    private VisLabel thresholdValue;
    private boolean thresholdPreview,thresholdCancelled;
    private float thresholdBefore;
    public LightingPanel(EntityPropertiesContext ctx,boolean light){
        this.ctx=ctx;defaults().left().pad(5);
        if(light){
            field("Source height:",Property.HEIGHT);
            quality=new SimpleSelectBox<>();quality.setItems("None","Hard","PCF");
            quality.bind(()->new String[]{"None","Hard","PCF"}[(int)read(Property.QUALITY)],v->edit(Property.QUALITY,v.equals("Hard")?1:v.equals("PCF")?2:0));
            add(new VisLabel("Shadows:"));add(quality).left().row();
            resolution=new SimpleSelectBox<>();resolution.setItems(256,512,1024);
            resolution.bind(()->(int)read(Property.RESOLUTION),v->edit(Property.RESOLUTION,v));
            add(new VisLabel("Resolution:"));add(resolution).left().row();
        }else{
            check("Receive light",Property.RECEIVE);check("Cast shadow",Property.CAST);
            casterFields=new CollapsibleVisTable(false,true);casterFields.setName("actor-caster-fields");
            casterFields.content().defaults().left().pad(5);
            threshold=new SimpleFloatSlider(0,1,.01f);threshold.setName("lighting-alpha-threshold");
            thresholdValue=new VisLabel();threshold.bind(()->read(Property.ALPHA_THRESHOLD),this::changeThreshold);
            threshold.addCaptureListener(new com.badlogic.gdx.scenes.scene2d.InputListener(){
                @Override public boolean touchDown(com.badlogic.gdx.scenes.scene2d.InputEvent event,float x,float y,int pointer,int button){return !threshold.isDisabled();}
                @Override public void touchUp(com.badlogic.gdx.scenes.scene2d.InputEvent event,float x,float y,int pointer,int button){thresholdCancelled=event.isTouchFocusCancel();}
            });
            new Tooltip.Builder("Alpha threshold for casting shadows. Pixels below this opacity are ignored.").target(threshold).build();
            casterFields.content().add(new VisLabel("Threshold:")).width(CommonLayout.LABEL_WIDTH);
            casterFields.content().add(threshold).width(CommonLayout.FIELD_WIDTH);casterFields.content().add(thresholdValue).row();
            add(casterFields).colspan(2).pad(0).padLeft(games.pixscape.studio.ui.config.CommonLayout.PAD_LEFT_SUBMENU).growX().row();
            // Legacy receiver support values remain stored, but are not actor authoring controls.
        }
    }
    private float read(Property p){return entity<0?defaultValue(p):EditLightingCommand.read(ctx.world,entity,p);}
    private static float defaultValue(Property p){return p==Property.QUALITY?1:p==Property.RESOLUTION?512:p==Property.HEIGHT?128:0;}
    private void edit(Property p,float value){if(refresh || entity<0)return;
        if(p==Property.CAST)cancelThresholdPreview();
        EditLightingCommand command=new EditLightingCommand(ctx.world,ctx.history.historyIds(),entity,p,value);
        if(!command.isNoop())ctx.history.execute(command);
        if(p==Property.CAST)refreshCasterFields();
    }
    private void field(String label,Property p){field(this,label,p);}
    private void field(VisTable target,String label,Property p){
        FloatField f=new FloatField(ctx.world,e->EditLightingCommand.read(ctx.world,e,p),e->e>=0&&ctx.world.getEntityManager().isActive(e)).setDisplayDecimals(3);
        if(p==Property.HEIGHT)f.setName("light-source-height");
        f.setApplier((e,v)->edit(p,v));fields.add(f);target.add(new VisLabel(label));target.add(f).left().row();
    }
    private void check(String label,Property p){
        // Native checkbox has no ECS wrapper; command owns every authored mutation.
        VisCheckBox c=new VisCheckBox(label);if(p==Property.CAST)c.setName("lighting-cast-shadow");checks.add(c);checkProperties.add(p);add(c).colspan(2).left().row();
        c.addListener(new ChangeListener(){@Override public void changed(ChangeEvent event,Actor actor){edit(p,c.isChecked()?1:0);}});
    }
    private void refreshCasterFields(){
        if(casterFields!=null){casterFields.show(read(Property.CAST)!=0);if(!thresholdPreview){threshold.setDisabled(entity<0 || read(Property.CAST)==0);threshold.refresh();updateThresholdValue();}}
    }
    private void updateThresholdValue(){thresholdValue.setText(String.format(Locale.ROOT,"%.3f",read(Property.ALPHA_THRESHOLD)));}
    private void previewThreshold(float value){
        if(entity<0 || !ctx.world.getEntityManager().isActive(entity))return;
        SurfaceLightingComponent s=ctx.world.getMapper(SurfaceLightingComponent.class).getSafe(entity,null);if(s==null)return;
        s.alphaThreshold=value;DirtyTrackerSystem dirty=ctx.world.getSystem(DirtyTrackerSystem.class);if(dirty!=null)dirty.material(entity);
    }
    private void cancelThresholdPreview(){if(thresholdPreview){previewThreshold(thresholdBefore);thresholdPreview=false;}}
    private void changeThreshold(float value){
        if(refresh || entity<0 || !ctx.world.getEntityManager().isActive(entity))return;
        if(thresholdCancelled){thresholdCancelled=false;cancelThresholdPreview();updateThresholdValue();return;}
        if(threshold.isDragging()){
            if(!thresholdPreview){thresholdBefore=read(Property.ALPHA_THRESHOLD);thresholdPreview=true;}previewThreshold(value);
        }else{cancelThresholdPreview();edit(Property.ALPHA_THRESHOLD,value);}
        updateThresholdValue();
    }
    public void setEntityId(int e){if(e!=entity)cancelThresholdPreview();entity=e;refresh=true;try{
        for(FloatField f:fields)f.setEntityId(e);
        for(int i=0;i<checks.size;i++)checks.get(i).setChecked(read(checkProperties.get(i))!=0);
        if(quality!=null)quality.refresh();if(resolution!=null)resolution.refresh();
        refreshCasterFields();
    }finally{refresh=false;}}
}
