package games.pixscape.studio.ui.property.entityproperties;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.*;
import games.pixscape.studio.history.commands.EditLightingCommand;
import games.pixscape.studio.history.commands.EditLightingCommand.Property;
import games.pixscape.studio.ui.widget.*;

/** Authored controls shared by point/cone and ordinary sprite inspectors. */
public final class LightingPanel extends VisTable {
    private final EntityPropertiesContext ctx;private int entity=-1;private boolean refresh;
    private final Array<FloatField> fields=new Array<>();private final Array<VisCheckBox> checks=new Array<>();
    private final Array<Property> checkProperties=new Array<>();
    private SimpleSelectBox<String> quality;private SimpleSelectBox<Integer> resolution;
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
            field("Altitude:",Property.ALTITUDE);field("Foot U:",Property.ANCHOR_U);field("Foot V:",Property.ANCHOR_V);
            field("Direction X:",Property.DIRECTION_X);field("Direction Y:",Property.DIRECTION_Y);field("Caster alpha:",Property.ALPHA_THRESHOLD);
        }
    }
    private float read(Property p){return entity<0?defaultValue(p):EditLightingCommand.read(ctx.world,entity,p);}
    private static float defaultValue(Property p){return p==Property.QUALITY?1:p==Property.RESOLUTION?512:p==Property.HEIGHT?128:0;}
    private void edit(Property p,float value){if(refresh || entity<0)return;
        EditLightingCommand command=new EditLightingCommand(ctx.world,ctx.history.historyIds(),entity,p,value);
        if(!command.isNoop())ctx.history.execute(command);
    }
    private void field(String label,Property p){
        FloatField f=new FloatField(ctx.world,e->EditLightingCommand.read(ctx.world,e,p),e->e>=0&&ctx.world.getEntityManager().isActive(e)).setDisplayDecimals(3);
        f.setApplier((e,v)->edit(p,v));fields.add(f);add(new VisLabel(label));add(f).left().row();
    }
    private void check(String label,Property p){
        // Native checkbox has no ECS wrapper; command owns every authored mutation.
        VisCheckBox c=new VisCheckBox(label);checks.add(c);checkProperties.add(p);add(c).colspan(2).left().row();
        c.addListener(new ChangeListener(){@Override public void changed(ChangeEvent event,Actor actor){edit(p,c.isChecked()?1:0);}});
    }
    public void setEntityId(int e){entity=e;refresh=true;try{
        for(FloatField f:fields)f.setEntityId(e);
        for(int i=0;i<checks.size;i++)checks.get(i).setChecked(read(checkProperties.get(i))!=0);
        if(quality!=null)quality.refresh();if(resolution!=null)resolution.refresh();
    }finally{refresh=false;}}
}
